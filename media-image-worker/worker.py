from __future__ import annotations

import logging
import signal
import threading
import time

from redaction.config import WorkerConfig
from redaction.detector import CmccBrandDetector
from redaction.processor import ImageRedactionProcessor
from redaction.repository import RedactionJobRepository


logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)
LOGGER = logging.getLogger("media-image-worker")


def main() -> None:
    config = WorkerConfig.from_environment()
    detector = CmccBrandDetector(
        config.model_path,
        config.model_sha256,
        config.confidence_threshold,
        config.nms_threshold,
        config.grayscale_inference,
        config.dense_scan,
        config.ocr_enabled,
        config.ocr_max_side,
        config.ocr_min_confidence,
        config.standalone_model_confidence,
    )
    processor = ImageRedactionProcessor(
        detector=detector,
        storage_root=config.storage_root,
        max_file_bytes=config.max_file_bytes,
        max_pixels=config.max_pixels,
        max_side=config.max_side,
        padding_ratio=config.blur_padding_ratio,
        max_padding_pixels=config.max_padding_pixels,
    )
    repository = RedactionJobRepository(config.database_url, config.worker_id, config.lease_seconds)
    stop_event = threading.Event()
    signal.signal(signal.SIGTERM, lambda *_: stop_event.set())
    signal.signal(signal.SIGINT, lambda *_: stop_event.set())
    LOGGER.info(
        "Image redaction worker started worker_id=%s model_version=%s concurrency=1",
        config.worker_id,
        config.model_version,
    )
    while not stop_event.is_set():
        try:
            job = repository.claim_next()
            if job is None:
                stop_event.wait(config.poll_seconds)
                continue
            process_job(repository, processor, job, config.model_version)
        except Exception:
            LOGGER.exception("Worker loop failed; retrying after bounded delay")
            stop_event.wait(min(10.0, config.poll_seconds * 2.0))
    LOGGER.info("Image redaction worker stopped")


def process_job(
    repository: RedactionJobRepository,
    processor: ImageRedactionProcessor,
    job: dict[str, object],
    model_version: str,
) -> None:
    LOGGER.info("Processing image redaction job_id=%s tenant_id=%s", job["id"], job["tenant_id"])
    try:
        result = processor.process(job)
        repository.mark_ready(job, result, model_version)
        LOGGER.info(
            "Image redaction ready job_id=%s regions=%s duration_ms=%s output_bytes=%s",
            job["id"],
            result.detected_regions,
            result.duration_ms,
            result.output_file_size,
        )
    except Exception as exception:
        LOGGER.exception("Image redaction job failed job_id=%s", job["id"])
        repository.mark_failed(job, exception)


if __name__ == "__main__":
    main()
