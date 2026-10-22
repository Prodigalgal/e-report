from __future__ import annotations

import hashlib
import json
import argparse
from pathlib import Path
import urllib.request
import urllib.error
import time


ROOT = Path(__file__).resolve().parent
MANIFEST_PATH = ROOT / "source_manifest.json"
OUTPUT_DIRECTORY = ROOT / "sources"
LOCK_PATH = ROOT / "source-lock.json"


def main() -> None:
    parser = argparse.ArgumentParser(description="Download and lock public training sources.")
    parser.add_argument(
        "--skip-existing",
        action="store_true",
        help="Reuse an existing non-empty source file and avoid an unnecessary network request.",
    )
    arguments = parser.parse_args()
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    OUTPUT_DIRECTORY.mkdir(parents=True, exist_ok=True)
    lock_entries = []
    for source in manifest["sources"]:
        target = OUTPUT_DIRECTORY / source["fileName"]
        if arguments.skip_existing and target.is_file() and target.stat().st_size >= 1024:
            content = target.read_bytes()
            print(f"reused {source['id']} -> {target.name} ({len(content)} bytes)")
        else:
            content = download_with_retry(source["url"])
            target.write_bytes(content)
            print(f"downloaded {source['id']} -> {target.name} ({len(content)} bytes)")
        if len(content) < 1024:
            raise RuntimeError(f"Downloaded source is unexpectedly small: {source['id']}")
        lock_entries.append(
            {
                "id": source["id"],
                "fileName": source["fileName"],
                "sha256": hashlib.sha256(content).hexdigest(),
                "bytes": len(content),
                "sourcePage": source["sourcePage"],
                "license": source["license"],
            }
        )
    LOCK_PATH.write_text(
        json.dumps({"schemaVersion": 1, "sources": lock_entries}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def download_with_retry(url: str) -> bytes:
    last_error: Exception | None = None
    for attempt in range(1, 5):
        request = urllib.request.Request(
            url,
            headers={"User-Agent": "e-report-image-redaction-training/5.5.0"},
        )
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return response.read()
        except urllib.error.HTTPError as exception:
            last_error = exception
            if attempt == 4:
                break
            retry_after = exception.headers.get("Retry-After")
            try:
                delay = max(5, min(120, int(retry_after))) if retry_after else min(120, 5 * (2 ** (attempt - 1)))
            except ValueError:
                delay = min(120, 5 * (2 ** (attempt - 1)))
            time.sleep(delay)
        except Exception as exception:
            last_error = exception
            if attempt == 4:
                break
            time.sleep(min(120, 5 * (2 ** (attempt - 1))))
    raise RuntimeError(f"Unable to download source after retries: {url}") from last_error


if __name__ == "__main__":
    main()
