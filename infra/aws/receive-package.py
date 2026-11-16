"""One upload over an authenticated SSM tunnel; bind to loopback only. VM-side."""
import argparse
import hashlib
import hmac
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
import tarfile
import time

parser = argparse.ArgumentParser()
parser.add_argument("--zone", choices=["auth", "data", "ingest", "core", "app"], required=True)
parser.add_argument("--token-sha256", required=True)
parser.add_argument("--bundle-sha256", required=True)
parser.add_argument("--destination", default="/opt/smarttraffic/incoming")
parser.add_argument("--port", type=int, default=18800)
args = parser.parse_args()
root = Path(args.destination).resolve() / args.zone
if root.exists():
    raise SystemExit("Destination already exists; inspect it rather than overwriting")
root.mkdir(parents=True, mode=0o700)
root.chmod(0o700)
accepted = False


class Receiver(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass  # Never log credentials/headers.

    def do_POST(self):
        global accepted
        self.connection.settimeout(30)
        token = self.headers.get("Authorization", "")
        if self.path != "/bundle" or not token.startswith("Bearer ") or not hmac.compare_digest(
            hashlib.sha256(token[7:].encode()).hexdigest(), args.token_sha256
        ):
            self.send_error(403)
            return
        length = int(self.headers.get("Content-Length", "0"))
        if not 0 < length <= 8 * 1024**3:
            self.send_error(413)
            return
        archive = root / "bundle.tar"
        digest = hashlib.sha256()
        with archive.open("xb") as output:
            archive.chmod(0o600)
            remaining = length
            while remaining:
                block = self.rfile.read(min(1024**2, remaining))
                if not block:
                    raise ConnectionError("Incomplete upload; preserved for review")
                output.write(block)
                digest.update(block)
                remaining -= len(block)
        if not hmac.compare_digest(digest.hexdigest(), args.bundle_sha256):
            self.send_error(422, "Checksum mismatch; preserved for review")
            return
        with tarfile.open(archive) as bundle:
            members = bundle.getmembers()
            for member in members:
                destination = (root / member.name).resolve()
                if (not destination.is_relative_to(root.resolve()) or
                    not (member.isdir() or member.isfile()) or destination == archive.resolve()):
                    raise ValueError("Unsafe archive member")
            # All members are regular files/directories within a root-only directory.
            bundle.extractall(root, members=members)
        for item in root.rglob("*"):
            item.chmod(0o700 if item.is_dir() else 0o600)
        accepted = True
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"Verified and staged; applications not started.\n")


server = HTTPServer(("127.0.0.1", args.port), Receiver)
server.timeout = 10
deadline = time.monotonic() + 1800
while not accepted and time.monotonic() < deadline:
    server.handle_request()
server.server_close()
