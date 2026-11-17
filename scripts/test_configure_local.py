import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("configure-local.py")

class ConfigureLocalTest(unittest.TestCase):
    def test_preserves_credentials_and_only_fills_missing_values(self):
        with tempfile.TemporaryDirectory() as directory:
            env = Path(directory) / ".env"
            env.write_text("ORACLE_ADMIN_PASSWORD='preserve-existing-value'\nREPORT_DB_PASSWORD=\nENTRA_TENANT_ID=keep-id\n", encoding="utf-8")
            result = subprocess.run([sys.executable, str(SCRIPT), "--output", str(env)],
                                    capture_output=True, text=True, check=True)
            text = env.read_text(encoding="utf-8")
            self.assertIn("ORACLE_ADMIN_PASSWORD='preserve-existing-value'", text)
            self.assertIn("ENTRA_TENANT_ID=keep-id", text)
            values = dict(line.split("=", 1) for line in text.splitlines() if "=" in line)
            self.assertRegex(values["REPORT_DB_PASSWORD"], r"^[a-f0-9]{48}$")
            self.assertRegex(values["AUDIT_DB_PASSWORD"], r"^[a-f0-9]{48}$")
            self.assertRegex(values["KAFKA_UI_PASSWORD"], r"^[a-f0-9]{48}$")
            self.assertNotIn(values["KAFKA_UI_PASSWORD"], result.stdout)
            subprocess.run([sys.executable, str(SCRIPT), "--output", str(env)], capture_output=True, check=True)
            self.assertEqual(text, env.read_text(encoding="utf-8"))

if __name__ == "__main__":
    unittest.main()

