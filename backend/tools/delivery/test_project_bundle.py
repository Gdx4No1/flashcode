import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

from project_bundle import archive_source, create_bundle, include_backend, path_is_public, require_d, validate_maven
from audit_export import audit

NS = 'xmlns="http://maven.apache.org/POM/4.0.0"'


class BundleTest(unittest.TestCase):
    def setUp(self):
        parent = Path("D:/tmp/flashcode-delivery/tool-tests")
        parent.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=parent)
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.backend = self.root / "original-backend"
        self.frontend = self.root / "original-frontend"
        self.backend.mkdir()
        self.frontend.mkdir()
        (self.backend / "pom.xml").write_text(f"<project {NS}><modules><module>flashcode-common</module></modules></project>", encoding="utf-8")
        self.put(self.backend, "flashcode-common/pom.xml", f"<project {NS}/>")
        self.put(self.backend, "flashcode-common/src/main/java/Example.java", "class Example {}")
        self.put(self.frontend, "package.json", '{"private":true}')
        self.put(self.frontend, "src/main.js", "console.log('source')")

    def put(self, root, name, text="fixture"):
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")

    def export(self):
        return create_bundle(self.backend, self.frontend, self.root / "export", private=False)

    def test_reject_c_without_accessing_it(self):
        with self.assertRaises(ValueError):
            require_d(Path("C:/forbidden-test-output"))

    def test_reject_drive_root(self):
        with self.assertRaises(ValueError):
            require_d(Path("D:/"))

    def test_reject_output_inside_source(self):
        with self.assertRaises(ValueError):
            create_bundle(self.backend, self.frontend, self.backend / "export", private=False)

    def test_existing_destination_is_never_overwritten(self):
        self.export()
        sentinel = self.root / "export/sentinel.txt"
        sentinel.write_text("keep", encoding="utf-8")
        with self.assertRaises(FileExistsError):
            self.export()
        self.assertEqual(sentinel.read_text(), "keep")

    def test_manifest_hashes_and_reactor(self):
        result = self.export()
        self.assertEqual(result["reactorPomCount"], 2)
        manifest = json.loads((self.root / "export/manifest.json").read_text())
        for entry in manifest["files"]:
            payload = (self.root / "export" / entry["path"]).read_bytes()
            self.assertEqual(hashlib.sha256(payload).hexdigest(), entry["sha256"])

    def test_missing_module_fails(self):
        self.put(self.backend, "pom.xml", f"<project {NS}><modules><module>missing</module></modules></project>")
        with self.assertRaisesRegex(ValueError, "Missing reactor"):
            validate_maven(self.backend)

    def test_module_escape_fails(self):
        self.put(self.backend, "pom.xml", f"<project {NS}><modules><module>../other</module></modules></project>")
        with self.assertRaisesRegex(ValueError, "escapes"):
            validate_maven(self.backend)

    def test_module_cycle_fails(self):
        self.put(self.backend, "flashcode-common/pom.xml", f"<project {NS}><modules><module>..</module></modules></project>")
        with self.assertRaisesRegex(ValueError, "cyclic"):
            validate_maven(self.backend)

    def test_private_and_generated_files_excluded_from_delivery(self):
        for name in [".git/config", "target/app.jar", "user-code/1/index.html", "deploy/dev/res/sql/db.sql", "deploy/dev/res/sql/nacosdata.sql", "flashcode-common/src/main/resources/key.pem", ".env.local"]:
            self.put(self.backend, name, "private")
        for name in [".env.local", "node_modules/example.js", "dist/index.html", "src/cert.pem"]:
            self.put(self.frontend, name, "private")
        self.put(self.frontend, ".env.example", "VITE_API_TARGET=http://127.0.0.1:18080")
        self.export()
        paths = [p.relative_to(self.root / "export").as_posix() for p in (self.root / "export").rglob("*") if p.is_file()]
        self.assertIn("frontend/.env.example", paths)
        self.assertFalse(any(".git/" in p or p.endswith(".pem") or "node_modules/" in p or p.endswith("db.sql") or p.endswith(".env.local") for p in paths))

    def test_private_backup_keeps_configs_and_git(self):
        self.put(self.backend, ".git/config", "private metadata")
        self.put(self.frontend, ".env.local", "private config")
        result = create_bundle(self.backend, self.frontend, self.root / "private", private=True)
        self.assertEqual(result["kind"], "PRIVATE_BACKUP_DO_NOT_SHARE")
        self.assertTrue((self.root / "private/backend/.git/config").is_file())
        self.assertTrue((self.root / "private/frontend/.env.local").is_file())

    def test_untracked_public_module_source_is_included(self):
        self.assertTrue(include_backend(Path("flashcode-common/flashcode-common-security/src/main/java/NewFix.java")))
        self.assertTrue(include_backend(Path("deploy/manual/teacher-alignment.sql")))
        self.assertFalse(path_is_public(Path("docs/key.pfx")))

    def test_source_is_unchanged_by_export(self):
        source = self.backend / "pom.xml"
        before = source.read_bytes()
        self.export()
        self.assertEqual(source.read_bytes(), before)

    def baseline(self):
        baseline = self.root / 'baseline'
        self.put(baseline, 'backend/pom.xml', f'<project {NS}><properties><dockerregistry.username>old-private-user-123</dockerregistry.username><dockerregistry.password>old-private-password-123</dockerregistry.password></properties></project>')
        self.put(baseline, 'backend/flashcode-common/flashcode-common-domain/src/main/java/com/gejia/flashcode/flashcodecommondomain/constants/TokenConstants.java', 'String SECRET = "old-private-signing-key-123";')
        return baseline

    def test_audit_accepts_verified_source(self):
        self.export()
        self.assertEqual(audit(self.root / 'export', self.baseline())['result'], 'PASS')

    def test_audit_detects_known_private_value_without_echoing_it(self):
        self.put(self.frontend, 'src/main.js', 'old-private-password-123')
        self.export()
        with self.assertRaisesRegex(ValueError, 'Known private baseline') as error:
            audit(self.root / 'export', self.baseline())
        self.assertNotIn('old-private-password-123', str(error.exception))

    def test_audit_rejects_modified_payload(self):
        self.export()
        self.put(self.root / 'export', 'frontend/src/main.js', 'tampered')
        with self.assertRaisesRegex(ValueError, 'hash mismatch'):
            audit(self.root / 'export', self.baseline())

    def test_audit_rejects_manifest_path_escape(self):
        self.export()
        path = self.root / 'export/manifest.json'
        manifest = json.loads(path.read_text())
        manifest['files'][0]['path'] = '../outside.txt'
        path.write_text(json.dumps(manifest), encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'escapes'):
            audit(self.root / 'export', self.baseline())

    def test_audit_rejects_private_backup(self):
        create_bundle(self.backend, self.frontend, self.root / 'private', private=True)
        with self.assertRaisesRegex(ValueError, 'Not a source delivery'):
            audit(self.root / 'private', self.baseline())

    def test_archive_ignores_later_private_build_outputs(self):
        self.export()
        self.put(self.root / 'export', 'backend/.local/jwt-secret.local', 'do-not-export')
        archive_source(self.root / 'export', self.root / 'source.zip')
        with zipfile.ZipFile(self.root / 'source.zip') as archive:
            self.assertIn('manifest.json', archive.namelist())
            self.assertNotIn('backend/.local/jwt-secret.local', archive.namelist())

    def test_archive_refuses_private_backup(self):
        create_bundle(self.backend, self.frontend, self.root / 'private', private=True)
        with self.assertRaisesRegex(ValueError, 'Never archive a private'):
            archive_source(self.root / 'private', self.root / 'source.zip')


if __name__ == "__main__":
    unittest.main()
