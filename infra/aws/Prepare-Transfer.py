"""Prepare a per-VM tar and SSM command JSON locally. Executes NO AWS commands."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import secrets
import tarfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--deployment", required=True)
parser.add_argument("--zone", choices=["auth", "data", "ingest", "core", "app"], required=True)
parser.add_argument("--instance-id", required=True)
args = parser.parse_args()
if not args.instance_id.startswith("i-") or not all(c in "0123456789abcdef" for c in args.instance_id[2:]):
    raise SystemExit("Expected an actual EC2 instance ID after approved deployment")
repo = Path(__file__).resolve().parents[2]
deployment = Path(args.deployment).resolve()
if not deployment.is_relative_to(repo / ".tools") or not (deployment / "COMPLETE").is_file():
    raise SystemExit("Use a completed, ignored local deployment directory")
zone = deployment / args.zone
if not (zone / "images" / (args.zone + "-images.tar")).is_file():
    raise SystemExit("Export and review this zone's images before preparing delivery")
upload = deployment / "uploads" / args.zone
upload.mkdir(parents=True, mode=0o700, exist_ok=False)
archive = upload / "zone.tar"
with tarfile.open(archive, "x") as bundle:
    for item in sorted(zone.rglob("*")):
        if item.is_symlink():
            raise ValueError("Refusing symlink in deployment")
        bundle.add(item, arcname=str(item.relative_to(zone)), recursive=False)
digest = hashlib.sha256()
with archive.open("rb") as stream:
    for block in iter(lambda: stream.read(1024**2), b""):
        digest.update(block)
token = secrets.token_hex(32)
(upload / "token.txt").write_text(token)
encoded = base64.b64encode((repo / "infra/aws/receive-package.py").read_bytes()).decode()
token_digest = hashlib.sha256(token.encode()).hexdigest()
commands = [
    "set -eu",
    "umask 077",
    "install -d -m 0700 /opt/smarttraffic/incoming",
    f"printf '%s' '{encoded}' | base64 -d > /opt/smarttraffic/receive-package.py",
    f"nohup python3 /opt/smarttraffic/receive-package.py --zone {args.zone} --token-sha256 {token_digest} --bundle-sha256 {digest.hexdigest()} > /opt/smarttraffic/receive-package.log 2>&1 < /dev/null &",
]
(upload / "ssm-receiver.json").write_text(json.dumps({"DocumentName": "AWS-RunShellScript", "InstanceIds": [args.instance_id], "Parameters": {"commands": commands}}, indent=2))
(upload / "bundle-sha256.txt").write_text(digest.hexdigest() + "\n")
if os.name != "nt":
    for item in upload.iterdir():
        item.chmod(0o600)
print(f"Prepared {args.zone} upload. SSM command is NOT executed; approval is required.")
