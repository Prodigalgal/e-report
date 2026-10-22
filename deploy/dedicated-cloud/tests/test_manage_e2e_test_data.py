import importlib.util
from pathlib import Path
import tempfile
import unittest


MODULE_PATH = Path(__file__).parents[1] / "manage-e2e-test-data.py"
SPEC = importlib.util.spec_from_file_location("manage_e2e_test_data", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ManageE2eTestDataTests(unittest.TestCase):
    def test_resolve_under_accepts_uuid_file_name(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            resolved = MODULE.resolve_under(root, "123e4567-e89b-12d3-a456-426614174000.png")
            self.assertEqual(root.resolve() / "123e4567-e89b-12d3-a456-426614174000.png", resolved)

    def test_resolve_under_rejects_path_traversal_and_non_uuid_names(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            with self.assertRaises(RuntimeError):
                MODULE.resolve_under(root, "../business-file.png")
            with self.assertRaises(RuntimeError):
                MODULE.resolve_under(root, "business-file.png")

    def test_safety_gate_rejects_active_or_cross_linked_test_data(self):
        with self.assertRaises(RuntimeError):
            MODULE.assert_safe_inventory({
                "active_test_users": 1,
                "nonterminal_test_work_orders": 0,
                "business_orders_with_test_participants": 0,
            }, enforce_baseline=False)

    def test_safety_gate_allows_business_growth_and_locks_reserved_test_set(self):
        inventory = {
            "total_users": 150,
            "test_users": 70,
            "active_test_users": 0,
            "total_work_orders": 104,
            "test_work_orders": 52,
            "nonterminal_test_work_orders": 0,
            "total_attachments": 170,
            "test_attachments": 84,
            "total_upload_sessions": 205,
            "test_upload_sessions": 88,
            "business_orders_with_test_participants": 0,
        }
        MODULE.assert_safe_inventory(inventory, enforce_baseline=True)

        inventory["test_work_orders"] = 53
        with self.assertRaises(RuntimeError):
            MODULE.assert_safe_inventory(inventory, enforce_baseline=True)
        with self.assertRaises(RuntimeError):
            MODULE.assert_safe_inventory({
                "active_test_users": 0,
                "nonterminal_test_work_orders": 0,
                "business_orders_with_test_participants": 1,
            }, enforce_baseline=False)


if __name__ == "__main__":
    unittest.main()
