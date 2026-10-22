from __future__ import annotations

from dataclasses import dataclass
import os
from pathlib import Path
from urllib.parse import quote_plus


@dataclass(frozen=True)
class WorkerConfig:
    database_url: str
    storage_root: Path
    model_path: Path
    model_sha256: str
    model_version: str
    worker_id: str
    poll_seconds: float
    lease_seconds: int
    confidence_threshold: float
    nms_threshold: float
    grayscale_inference: bool
    dense_scan: bool
    ocr_enabled: bool
    ocr_max_side: int
    ocr_min_confidence: float
    standalone_model_confidence: float
    blur_padding_ratio: float
    max_padding_pixels: int
    max_pixels: int
    max_side: int
    max_file_bytes: int

    @classmethod
    def from_environment(cls) -> "WorkerConfig":
        database_url = database_connection_url()
        storage_root = Path(os.getenv("IMAGE_REDACTION_STORAGE_ROOT", "/data/uploads")).resolve()
        model_path = Path(os.getenv("IMAGE_REDACTION_MODEL_PATH", "/app/models/cmcc-brand-v1.onnx")).resolve()
        return cls(
            database_url=database_url,
            storage_root=storage_root,
            model_path=model_path,
            model_sha256=required("IMAGE_REDACTION_MODEL_SHA256").lower(),
            model_version=os.getenv("IMAGE_REDACTION_MODEL_VERSION", "cmcc-brand-v1"),
            worker_id=os.getenv("IMAGE_REDACTION_WORKER_ID", os.getenv("HOSTNAME", "media-image-worker")),
            poll_seconds=bounded_float("IMAGE_REDACTION_POLL_SECONDS", 2.0, 0.2, 30.0),
            lease_seconds=bounded_int("IMAGE_REDACTION_LEASE_SECONDS", 120, 30, 900),
            confidence_threshold=bounded_float("IMAGE_REDACTION_CONFIDENCE", 0.65, 0.05, 0.99),
            nms_threshold=bounded_float("IMAGE_REDACTION_NMS", 0.35, 0.05, 0.95),
            grayscale_inference=boolean_env("IMAGE_REDACTION_GRAYSCALE_INFERENCE", False),
            dense_scan=boolean_env("IMAGE_REDACTION_DENSE_SCAN", False),
            ocr_enabled=boolean_env("IMAGE_REDACTION_OCR_ENABLED", False),
            ocr_max_side=bounded_int("IMAGE_REDACTION_OCR_MAX_SIDE", 2000, 256, 4000),
            ocr_min_confidence=bounded_float("IMAGE_REDACTION_OCR_MIN_CONFIDENCE", 0.65, 0.1, 0.99),
            standalone_model_confidence=bounded_float(
                "IMAGE_REDACTION_STANDALONE_MODEL_CONFIDENCE", 0.90, 0.5, 0.99
            ),
            blur_padding_ratio=bounded_float("IMAGE_REDACTION_PADDING_RATIO", 0.05, 0.0, 0.5),
            max_padding_pixels=bounded_int("IMAGE_REDACTION_MAX_PADDING_PIXELS", 16, 2, 64),
            max_pixels=bounded_int("IMAGE_REDACTION_MAX_PIXELS", 40_000_000, 1_000_000, 100_000_000),
            max_side=bounded_int("IMAGE_REDACTION_MAX_SIDE", 12_000, 1_000, 30_000),
            max_file_bytes=bounded_int("IMAGE_REDACTION_MAX_FILE_BYTES", 100 * 1024 * 1024, 1024, 100 * 1024 * 1024),
        )


def required(name: str) -> str:
    value = os.getenv(name, "").strip()
    if not value:
        raise RuntimeError(f"Missing required environment variable: {name}")
    return value


def database_connection_url() -> str:
    explicit = os.getenv("DATABASE_URL", "").strip()
    if explicit:
        return explicit
    host = os.getenv("PGHOST", "e-postgresql-v5").strip()
    port = os.getenv("PGPORT", "5432").strip()
    database = os.getenv("PGDATABASE", "fault_reporter_db").strip()
    username = os.getenv("PGUSER", "e_report_app").strip()
    password = required("PGPASSWORD")
    return (
        f"postgresql://{quote_plus(username)}:{quote_plus(password)}@"
        f"{host}:{port}/{quote_plus(database)}"
    )


def bounded_int(name: str, default: int, minimum: int, maximum: int) -> int:
    value = int(os.getenv(name, str(default)))
    if not minimum <= value <= maximum:
        raise RuntimeError(f"{name} must be between {minimum} and {maximum}")
    return value


def bounded_float(name: str, default: float, minimum: float, maximum: float) -> float:
    value = float(os.getenv(name, str(default)))
    if not minimum <= value <= maximum:
        raise RuntimeError(f"{name} must be between {minimum} and {maximum}")
    return value


def boolean_env(name: str, default: bool) -> bool:
    value = os.getenv(name, str(default).lower()).strip().lower()
    if value in {"1", "true", "yes", "on"}:
        return True
    if value in {"0", "false", "no", "off"}:
        return False
    raise RuntimeError(f"{name} must be a boolean")
