FROM docker.m.daocloud.io/library/python:3.12.12-slim-bookworm@sha256:593bd06efe90efa80dc4eee3948be7c0fde4134606dd40d8dd8dbcade98e669c

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

# opencv-python is used by RapidOCR; the slim base omits the runtime GL
# library required when importing cv2. Install only the runtime package and
# remove apt metadata so the worker image remains small and deterministic.
RUN apt-get update \
    && apt-get install --no-install-recommends -y libgl1 libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --system --uid 10001 --create-home mediaworker
WORKDIR /app

COPY requirements-runtime.txt ./
RUN pip install --require-hashes -r requirements-runtime.txt

COPY redaction ./redaction
COPY models ./models
COPY worker.py ./

USER 10001
ENTRYPOINT ["python", "worker.py"]
