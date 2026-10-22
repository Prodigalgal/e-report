from __future__ import annotations

from contextlib import contextmanager
from datetime import datetime, timezone
import logging
from typing import Iterator
from uuid import UUID

import psycopg
from psycopg.rows import dict_row

from redaction.processor import ImageRejectedError, ProcessResult


LOGGER = logging.getLogger("media-image-worker.repository")


class RedactionJobRepository:
    def __init__(self, database_url: str, worker_id: str, lease_seconds: int) -> None:
        self._database_url = database_url
        self._worker_id = worker_id
        self._lease_seconds = lease_seconds

    def claim_next(self) -> dict[str, object] | None:
        with self._connection() as connection:
            tenant_ids = [row["id"] for row in connection.execute(
                "SELECT id FROM tenants WHERE status = 'ACTIVE' ORDER BY id"
            ).fetchall()]
        for tenant_id in tenant_ids:
            job = self._claim_for_tenant(tenant_id)
            if job is not None:
                return job
        return None

    def mark_ready(self, job: dict[str, object], result: ProcessResult, model_version: str) -> None:
        with self._tenant_connection(job["tenant_id"]) as connection:
            updated = connection.execute(
                """
                UPDATE image_redaction_jobs
                   SET status = 'READY',
                       output_stored_file_name = %s,
                       output_original_file_name = %s,
                       output_content_type = %s,
                       output_file_size = %s,
                       output_sha256 = %s,
                       model_version = %s,
                       detected_regions = %s,
                       processing_duration_ms = %s,
                       error_code = NULL,
                       error_message = NULL,
                       lease_owner = NULL,
                       lease_until = NULL,
                       completed_at = CURRENT_TIMESTAMP,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE id = %s AND status = 'PROCESSING' AND lease_owner = %s
                """,
                (
                    result.output_stored_file_name,
                    result.output_original_file_name,
                    result.output_content_type,
                    result.output_file_size,
                    result.output_sha256,
                    model_version,
                    result.detected_regions,
                    result.duration_ms,
                    job["id"],
                    self._worker_id,
                ),
            ).rowcount
            if updated != 1:
                raise RuntimeError(f"Lost image-redaction lease before completion: {job['id']}")

    def mark_failed(self, job: dict[str, object], exception: Exception) -> bool:
        attempt_count = int(job["attempt_count"])
        max_attempts = int(job["max_attempts"])
        terminal = isinstance(exception, ImageRejectedError) or attempt_count >= max_attempts
        status = "FAILED" if terminal else "RETRY_PENDING"
        error_code = exception.error_code if isinstance(exception, ImageRejectedError) else "PROCESSING_FAILED"
        public_message = (
            exception.public_message
            if isinstance(exception, ImageRejectedError)
            else "图片安全处理暂时失败，系统将自动重试。"
        )
        retry_seconds = min(300, 10 * (2 ** max(0, attempt_count - 1)))
        with self._tenant_connection(job["tenant_id"]) as connection:
            connection.execute(
                """
                UPDATE image_redaction_jobs
                   SET status = %s,
                       available_at = CASE WHEN %s = 'RETRY_PENDING'
                                           THEN CURRENT_TIMESTAMP + (%s * INTERVAL '1 second')
                                           ELSE available_at END,
                       error_code = %s,
                       error_message = %s,
                       lease_owner = NULL,
                       lease_until = NULL,
                       completed_at = CASE WHEN %s = 'FAILED' THEN CURRENT_TIMESTAMP ELSE NULL END,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE id = %s AND status = 'PROCESSING' AND lease_owner = %s
                """,
                (
                    status,
                    status,
                    retry_seconds,
                    error_code,
                    public_message[:500],
                    status,
                    job["id"],
                    self._worker_id,
                ),
            )
        LOGGER.warning(
            "Image redaction failed job_id=%s tenant_id=%s code=%s terminal=%s",
            job["id"],
            job["tenant_id"],
            error_code,
            terminal,
        )
        return terminal

    def _claim_for_tenant(self, tenant_id: UUID) -> dict[str, object] | None:
        with self._tenant_connection(tenant_id) as connection:
            return connection.execute(
                """
                WITH candidate AS (
                    SELECT id
                      FROM image_redaction_jobs
                     WHERE (
                            status IN ('QUEUED', 'RETRY_PENDING') AND available_at <= CURRENT_TIMESTAMP
                           ) OR (
                            status = 'PROCESSING' AND lease_until < CURRENT_TIMESTAMP
                           )
                     ORDER BY available_at, created_at, id
                     FOR UPDATE SKIP LOCKED
                     LIMIT 1
                )
                UPDATE image_redaction_jobs job
                   SET status = 'PROCESSING',
                       attempt_count = attempt_count + 1,
                       lease_owner = %s,
                       lease_until = CURRENT_TIMESTAMP + (%s * INTERVAL '1 second'),
                       error_code = NULL,
                       error_message = NULL,
                       updated_at = CURRENT_TIMESTAMP
                  FROM candidate
                 WHERE job.id = candidate.id
                RETURNING job.*,
                          (SELECT original_file_name FROM file_transfer_upload_sessions WHERE id = job.upload_session_id)
                            AS original_file_name
                """,
                (self._worker_id, self._lease_seconds),
            ).fetchone()

    @contextmanager
    def _connection(self) -> Iterator[psycopg.Connection]:
        with psycopg.connect(self._database_url, row_factory=dict_row) as connection:
            with connection.transaction():
                yield connection

    @contextmanager
    def _tenant_connection(self, tenant_id: object) -> Iterator[psycopg.Connection]:
        with psycopg.connect(self._database_url, row_factory=dict_row) as connection:
            with connection.transaction():
                connection.execute("SELECT set_config('app.tenant_id', %s, true)", (str(tenant_id),))
                yield connection
