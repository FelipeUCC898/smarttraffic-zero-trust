"""Prepare fresh, local-only deployment material. Never calls AWS or reuses fixtures."""
import argparse
import csv
import io
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess


def run(args, env=None):
    result = subprocess.run(args, env=env, capture_output=True, text=True)
    if result.returncode:
        # Do not include command arguments, secret-bearing environment or output.
        raise RuntimeError(f"Tool failed: {Path(args[0]).name}; inspect local setup")
    return result.stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True)
    parser.add_argument("--openssl", default="openssl")
    parser.add_argument("--keytool", default="keytool")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    target = Path(args.output).resolve()
    if not target.is_relative_to(root / ".tools") or target.exists():
        raise ValueError("Use a NEW directory under this worktree's ignored .tools")
    target.mkdir(parents=True, mode=0o700)
    if os.name == "nt":
        sid = next(csv.reader(io.StringIO(run(["whoami", "/user", "/fo", "csv", "/nh"]))))[1]
        owner_env = os.environ.copy()
        owner_env["SMARTTRAFFIC_PREPARE_REPO"] = str(root)
        owner_sid = run(["powershell", "-NoProfile", "-Command",
            "[Security.Principal.NTAccount]::new((Get-Acl -LiteralPath $env:SMARTTRAFFIC_PREPARE_REPO).Owner).Translate([Security.Principal.SecurityIdentifier]).Value"], owner_env).strip()
        if not re.fullmatch(r"S-1-(?:[0-9]+-)*[0-9]+", owner_sid):
            raise ValueError("Cannot identify repository owner's SID")
        run(["icacls", str(target), "/inheritance:r", "/grant:r", f"*{sid}:(OI)(CI)F", f"*{owner_sid}:(OI)(CI)F"])
    offline = target / "OFFLINE-DO-NOT-UPLOAD"
    offline.mkdir(mode=0o700)
    values = {}
    for line in (root / "infra/aws/zones/runtime.env.example").read_text(encoding="utf-8-sig").splitlines():
        if line and not line.startswith("#"):
            name, value = line.split("=", 1)
            values[name] = secrets.token_hex(32) if value == "REPLACE_WITH_GENERATED_VALUE" else value
    values["MIGRATOR_DB_PASSWORD"] = secrets.token_hex(32)
    values["ARCHIVER_DB_PASSWORD"] = secrets.token_hex(32)
    tool_env = os.environ.copy()
    tool_env["SMARTTRAFFIC_STORE_PASSWORD"] = values["TRUSTSTORE_PASSWORD"]
    openssl = args.openssl
    def cert(*parts):
        return str(offline.joinpath(*parts))
    run([openssl, "req", "-x509", "-newkey", "rsa:3072", "-noenc", "-days", "30",
         "-subj", "/CN=SmartTraffic-deployment-CA", "-addext", "basicConstraints=critical,CA:TRUE",
         "-keyout", cert("ca.key"), "-out", cert("ca.pem")])
    sans = {
        "auth-server": "DNS:vm-auth,IP:10.42.10.10",
        "kafka": "DNS:vm-data,IP:10.42.10.11,IP:127.0.0.1",
        "rabbitmq": "DNS:vm-data,IP:10.42.10.11",
        "postgres": "DNS:vm-data,IP:10.42.10.11",
    }
    for name, san in sans.items():
        run([openssl, "req", "-new", "-newkey", "rsa:3072", "-noenc", "-subj", f"/CN={name}",
             "-keyout", cert(name + ".key"), "-out", cert(name + ".csr")])
        ext = offline / (name + ".ext")
        eku = "serverAuth,clientAuth" if name == "kafka" else "serverAuth"
        ext.write_text(f"subjectAltName={san}\nextendedKeyUsage={eku}\nbasicConstraints=critical,CA:FALSE\n")
        run([openssl, "x509", "-req", "-in", cert(name + ".csr"), "-CA", cert("ca.pem"),
             "-CAkey", cert("ca.key"), "-CAcreateserial", "-days", "14", "-sha256", "-extfile", str(ext),
             "-out", cert(name + ".pem")])
        run([openssl, "verify", "-CAfile", cert("ca.pem"), cert(name + ".pem")])
    run([openssl, "pkcs12", "-export", "-name", "auth-server", "-in", cert("auth-server.pem"),
         "-inkey", cert("auth-server.key"), "-certfile", cert("ca.pem"), "-out", cert("auth-server.p12"),
         "-passout", "env:SMARTTRAFFIC_STORE_PASSWORD"], tool_env)
    run([args.keytool, "-importcert", "-noprompt", "-alias", "smarttraffic-ca", "-file", cert("ca.pem"),
         "-keystore", cert("truststore.p12"), "-storetype", "PKCS12", "-storepass:env", "SMARTTRAFFIC_STORE_PASSWORD"], tool_env)
    run([openssl, "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:3072", "-out", cert("jwt-private.pem")])
    run([openssl, "pkey", "-in", cert("jwt-private.pem"), "-pubout", "-out", cert("jwt-public.pem")])
    (offline / "kafka-combined.pem").write_bytes((offline / "kafka.key").read_bytes() + (offline / "kafka.pem").read_bytes())
    # Password files are exact bytes: no platform newline becomes part of a password.
    (offline / "migrator-password").write_bytes(values["MIGRATOR_DB_PASSWORD"].encode("ascii"))
    (offline / "archiver-password").write_bytes(values["ARCHIVER_DB_PASSWORD"].encode("ascii"))
    distribution = {}
    for zone in ("auth", "data", "ingest", "core", "app"):
        destination = target / zone
        secret_dir = destination / "secrets"
        secret_dir.mkdir(parents=True, mode=0o700)
        definition = (root / f"infra/aws/zones/{zone}.yml").read_text(encoding="utf-8-sig")
        overlay = root / f"infra/aws/zones/{zone}-roles.yml"
        if overlay.exists():
            definition += overlay.read_text(encoding="utf-8-sig")
        required = set(re.findall(r"\$\{([A-Z0-9_]+)", definition))
        zone_values = {name: values[name] for name in sorted(required)}
        if zone == "core":
            # Compose interpolates the base before merging the roles overlay.
            # Never deliver the bootstrap password to the application VM.
            zone_values["POSTGRES_PASSWORD"] = "unused-overridden-by-core-roles"
        (destination / "runtime.env").write_bytes("".join(f"{name}={value}\n" for name, value in zone_values.items()).encode("utf-8"))
        filenames = sorted(set(re.findall(r"\$\{SECRETS_DIR[^}]*\}/([a-zA-Z0-9.-]+)", definition)))
        for name in filenames:
            shutil.copyfile(offline / name, secret_dir / name)
        config_root = destination / "repo/infra"
        shutil.copytree(root / "infra/runtime", config_root / "runtime")
        zone_config = config_root / "aws/zones"
        zone_config.mkdir(parents=True)
        shutil.copyfile(root / f"infra/aws/zones/{zone}.yml", zone_config / f"{zone}.yml")
        if overlay.exists():
            shutil.copyfile(overlay, zone_config / overlay.name)
        shutil.copyfile(root / "infra/aws/Install-Zone.sh", destination / "Install-Zone.sh")
        shutil.copyfile(root / "infra/aws/Import-Images.sh", destination / "Import-Images.sh")
        distribution[zone] = {"secretFiles": filenames, "environmentNames": sorted(zone_values)}
    for path in target.rglob("*"):
        if os.name != "nt":
            path.chmod(0o700 if path.is_dir() else 0o600)
    # This manifest contains names only, never secret values.
    (target / "distribution.json").write_text(json.dumps(distribution, indent=2) + "\n")
    (target / "COMPLETE").write_text("Fresh deployment material; offline CA MUST NEVER be uploaded.\n")
    print(f"Prepared five filtered zones in {target}; retain OFFLINE-DO-NOT-UPLOAD locally.")


if __name__ == "__main__":
    main()
