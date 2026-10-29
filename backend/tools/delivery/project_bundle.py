"""Create verified private backups or source deliveries without touching C:."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET
import zipfile

SKIP_DIRS = {"target", "node_modules", ".idea", ".nacos-backup", "user-code", "dist", ".local", "__pycache__"}
MODULES = {"flashcode-admin", "flashcode-common", "flashcode-file", "flashcode-gateway", "flashcode-mstemplate", "flashcode-portal", "flashcode-gitee-mcp-server"}
ROOT_FILES = {"pom.xml", ".gitignore", "AGENTS.md", "README.md", "NOTICE.upstream.md", "个人代码说明.md"}
FRONT_FILES = {"package.json", "package-lock.json", "index.html", "jsconfig.json", "vite.config.js", ".env.example", ".gitignore", "AGENTS.md", "README.md", "NOTICE.upstream.md"}


def require_d(path: Path) -> Path:
    if path.drive and path.drive.upper() != "D:":
        raise ValueError("Use D:; another drive is not authorized")
    resolved = path.resolve()
    if resolved.drive.upper() != "D:" or len(resolved.parts) < 3:
        raise ValueError("Use a specific directory on D:, not a drive root")
    return resolved


def walk_files(root: Path, *, private: bool = False):
    for current, dirs, names in os.walk(root, followlinks=False):
        dirs[:] = sorted(d for d in dirs if d not in SKIP_DIRS and (private or d != ".git"))
        for directory in dirs:
            candidate = Path(current) / directory
            if candidate.is_symlink() or candidate.is_junction():
                raise ValueError("Refusing linked directory: " + str(candidate))
        for name in sorted(names):
            path = Path(current) / name
            if path.is_symlink():
                raise ValueError("Refusing linked file: " + str(path))
            yield path


def include_backend(relative: Path) -> bool:
    parts = relative.parts
    if len(parts) == 1:
        return parts[0] in ROOT_FILES
    if parts[0] in MODULES:
        return ("src" in parts or parts[-1] in {"pom.xml", "Dockerfile"}) and path_is_public(relative)
    if parts[0] in {"docs", "tools"}:
        return path_is_public(relative) and relative.suffix in {".md", ".py", ".ps1", ".mjs", ".js", ".xml"}
    return relative.as_posix() in {"deploy/dev/res/sql/flashcode.sql", "deploy/manual/teacher-alignment.sql"}


def path_is_public(relative: Path) -> bool:
    return relative.suffix.lower() not in {".pem", ".key", ".p12", ".pfx", ".jks", ".log", ".pyc"} and not any(part.startswith(".env") and part != ".env.example" for part in relative.parts)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def copy_verified(source: Path, destination: Path, root: Path, entries: list):
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)
    digest = sha256(source)
    if digest != sha256(destination):
        raise RuntimeError("Copy verification failed: " + str(destination))
    entries.append({"path": destination.relative_to(root).as_posix(), "bytes": destination.stat().st_size, "sha256": digest})


def validate_maven(root: Path) -> int:
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    count = 0
    pending = [root / "pom.xml"]
    visited = set()
    while pending:
        pom = pending.pop()
        if pom in visited:
            raise ValueError("Repeated or cyclic reactor module: " + str(pom))
        visited.add(pom)
        if not pom.is_file():
            raise ValueError("Missing reactor POM: " + str(pom))
        document = ET.parse(pom)
        count += 1
        for module in document.findall("m:modules/m:module", ns):
            child = (pom.parent / module.text / "pom.xml").resolve()
            if not child.is_relative_to(root.resolve()):
                raise ValueError("Module escapes project root")
            pending.append(child)
    return count


def create_bundle(backend: Path, frontend: Path, output: Path, *, private: bool, runtime: Path | None = None):
    backend, frontend, output = map(require_d, (backend, frontend, output))
    if output.is_relative_to(backend) or output.is_relative_to(frontend):
        raise ValueError("Output must be outside both source trees")
    output.mkdir(parents=True, exist_ok=False)
    entries = []
    for label, source in (("backend", backend), ("frontend", frontend)):
        for path in walk_files(source, private=private):
            relative = path.relative_to(source)
            allowed = include_backend(relative) if label == "backend" else path_is_public(relative) and (relative.parts[0] in {"src", "public"} or relative.as_posix() in FRONT_FILES)
            if private or allowed:
                copy_verified(path, output / label / relative, output, entries)
    if private and runtime:
        for path in require_d(runtime).iterdir():
            if path.is_file() and path.suffix in {".yaml", ".yml", ".ps1", ".mjs", ".py"}:
                copy_verified(path, output / "private-runtime" / path.name, output, entries)
    count = validate_maven(output / "backend")
    manifest = {"kind": "PRIVATE_BACKUP_DO_NOT_SHARE" if private else "SOURCE_DELIVERY_NOT_YET_PRODUCTION_READY", "reactorPomCount": count, "fileCount": len(entries), "files": entries}
    (output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    return {"output": str(output), "kind": manifest["kind"], "fileCount": len(entries), "reactorPomCount": count}


def archive_source(delivery: Path, destination: Path):
    delivery, destination = require_d(delivery), require_d(destination)
    manifest_path = delivery / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest["kind"] != "SOURCE_DELIVERY_NOT_YET_PRODUCTION_READY":
        raise ValueError("Never archive a private baseline for delivery")
    files = []
    for entry in manifest["files"]:
        path = (delivery / entry["path"]).resolve()
        if not path.is_relative_to(delivery) or not path_is_public(Path(entry["path"])):
            raise ValueError("Unsafe manifest path")
        if sha256(path) != entry["sha256"]:
            raise ValueError("Changed source; export a new delivery before archiving")
        files.append((path, entry["path"]))
    # Archive only manifest-listed source: a later test build may have created private state.
    with zipfile.ZipFile(destination, "x", compression=zipfile.ZIP_DEFLATED) as archive:
        for path, relative in files:
            archive.write(path, relative)
        archive.write(manifest_path, "manifest.json")
    return {"archive": str(destination), "sha256": sha256(destination), "entries": len(files) + 1}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=["backup", "export"])
    parser.add_argument("--backend", type=Path, required=True)
    parser.add_argument("--frontend", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--runtime", type=Path)
    parser.add_argument("--archive", type=Path)
    args = parser.parse_args()
    print(json.dumps(create_bundle(args.backend, args.frontend, args.output, private=args.mode == "backup", runtime=args.runtime), ensure_ascii=False))
    if args.archive:
        print(json.dumps(archive_source(args.output, args.archive), ensure_ascii=False))


if __name__ == "__main__":
    main()
