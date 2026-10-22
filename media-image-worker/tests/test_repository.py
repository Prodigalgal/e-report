from __future__ import annotations

from contextlib import contextmanager
import unittest
from unittest.mock import patch
from uuid import UUID

from redaction.repository import RedactionJobRepository


class FakeCursor:
    def __init__(self, rows: list[dict[str, object]]) -> None:
        self._rows = rows

    def fetchall(self) -> list[dict[str, object]]:
        return self._rows


class FakeConnection:
    def __init__(self, rows: list[dict[str, object]]) -> None:
        self._rows = rows

    def execute(self, _statement: str) -> FakeCursor:
        return FakeCursor(self._rows)


class RedactionJobRepositoryTests(unittest.TestCase):
    def test_claim_next_reads_tenant_id_from_dict_row(self) -> None:
        tenant_id = UUID("019d0000-0000-7000-8000-000000000001")
        repository = RedactionJobRepository("postgresql://unused", "worker-1", 30)

        @contextmanager
        def fake_connection():
            yield FakeConnection([{"id": tenant_id}])

        with patch.object(repository, "_connection", fake_connection), patch.object(
            repository,
            "_claim_for_tenant",
            return_value=None,
        ) as claim_for_tenant:
            self.assertIsNone(repository.claim_next())

        claim_for_tenant.assert_called_once_with(tenant_id)


if __name__ == "__main__":
    unittest.main()
