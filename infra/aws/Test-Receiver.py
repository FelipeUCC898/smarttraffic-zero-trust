"""Exercise the actual receiver locally, including hostile archives; no AWS calls."""
import hashlib
import http.client
import io
from pathlib import Path
import socket
import subprocess
import sys
import tarfile
import tempfile
import time
import unittest


class ReceiverTest(unittest.TestCase):
    def exercise(self, member="payload.txt", token="local-test-only", expected_digest=None):
        root = Path(__file__).resolve().parents[2]
        with tempfile.TemporaryDirectory(prefix="receiver-", dir=root / ".tools") as temporary:
            payload = io.BytesIO()
            with tarfile.open(fileobj=payload, mode="w") as bundle:
                info = tarfile.TarInfo(member); info.size = 4
                bundle.addfile(info, io.BytesIO(b"safe"))
            data = payload.getvalue()
            with socket.socket() as port_socket:
                port_socket.bind(("127.0.0.1", 0)); port = port_socket.getsockname()[1]
            process = subprocess.Popen([sys.executable, str(root / "infra/aws/receive-package.py"),
                "--zone", "auth", "--destination", temporary, "--port", str(port),
                "--token-sha256", hashlib.sha256(b"local-test-only").hexdigest(),
                "--bundle-sha256", expected_digest or hashlib.sha256(data).hexdigest()],
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            try:
                for _ in range(100):
                    try:
                        with socket.create_connection(("127.0.0.1", port), timeout=.1): break
                    except OSError: time.sleep(.05)
                connection = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
                try:
                    connection.request("POST", "/bundle", data, {"Authorization": "Bearer " + token})
                    response = connection.getresponse(); status = response.status; response.read()
                except http.client.RemoteDisconnected:
                    status = 0
                finally: connection.close()
                extracted = (Path(temporary) / "auth/payload.txt").exists()
                escaped = (Path(temporary) / "escape.txt").exists()
                self.assertFalse(escaped)
                return status, extracted
            finally:
                if process.poll() is None: process.terminate()
                process.wait(timeout=5)

    def test_accepts_exact_authenticated_bundle(self):
        self.assertEqual((200, True), self.exercise())

    def test_rejects_wrong_token(self):
        self.assertEqual((403, False), self.exercise(token="wrong"))

    def test_rejects_wrong_digest(self):
        self.assertEqual((422, False), self.exercise(expected_digest="0" * 64))

    def test_blocks_archive_path_escape(self):
        status, extracted = self.exercise(member="../escape.txt")
        self.assertNotEqual(200, status); self.assertFalse(extracted)


if __name__ == "__main__":
    unittest.main()
