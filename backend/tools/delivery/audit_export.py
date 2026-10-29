"""Verify a source manifest and check known private baseline values without printing them."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

from project_bundle import require_d


def audit(delivery: Path, baseline: Path):
    delivery, baseline = require_d(delivery), require_d(baseline)
    manifest = json.loads((delivery / "manifest.json").read_text(encoding="utf-8"))
    if manifest["kind"] != "SOURCE_DELIVERY_NOT_YET_PRODUCTION_READY":
        raise ValueError("Not a source delivery manifest")
    root = ET.parse(baseline / "backend/pom.xml").getroot()
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    sensitive = []
    for name in ["dockerregistry.username", "dockerregistry.password"]:
        value = root.find("m:properties/m:" + name, ns).text
        if value and not value.startswith("${"):
            sensitive.append(value.encode("utf-8"))
    source = (baseline / "backend/flashcode-common/flashcode-common-domain/src/main/java/com/gejia/flashcode/flashcodecommondomain/constants/TokenConstants.java").read_text(encoding="utf-8")
    old_key = re.search(r'String\s+SECRET\s*=\s*"([^"\r\n]+)"', source)
    if old_key:
        sensitive.append(old_key.group(1).encode("utf-8"))
    for path in (baseline / "backend").glob("**/src/main/resources/bootstrap.yml"):
        text = path.read_text(encoding="utf-8")
        for value in re.findall(r"(?m)^\s*password:\s*([^\r\n]+)", text):
            if not value.startswith("${"):
                sensitive.append(value.strip("'\"").encode("utf-8"))
    sensitive = list(set(sensitive))
    for entry in manifest["files"]:
        path = (delivery / entry["path"]).resolve()
        if not path.is_relative_to(delivery):
            raise ValueError("Manifest escapes delivery")
        if path.suffix.lower() in {".pem", ".key", ".p12", ".pfx", ".jks"} or any(p in {".git", "node_modules", "target", ".local"} for p in Path(entry["path"]).parts):
            raise ValueError("Forbidden private/generated path: " + entry["path"])
        content = path.read_bytes()
        if hashlib.sha256(content).hexdigest() != entry["sha256"]:
            raise ValueError("Manifest hash mismatch: " + entry["path"])
        if any(value in content for value in sensitive):
            raise ValueError("Known private baseline value found in: " + entry["path"])
        if re.search(rb"sk-[A-Za-z0-9_-]{20,}", content):
            raise ValueError("Possible API key found in: " + entry["path"])
    return {"result": "PASS", "verifiedFiles": len(manifest["files"]), "knownPrivateValuesChecked": len(sensitive), "note": "Known-value and path audit, not a guarantee against every possible secret"}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--delivery", type=Path, required=True)
    parser.add_argument("--baseline", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(audit(args.delivery, args.baseline)))
