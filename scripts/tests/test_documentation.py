"""Consumer-visible boundaries for ETA documentation projections."""

import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "documentation.py"
SPEC = importlib.util.spec_from_file_location("eta_documentation", SCRIPT)
documentation = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = documentation
SPEC.loader.exec_module(documentation)


class DocumentationTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)

    def source(self, path, content):
        destination = self.root / path
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(content, encoding="utf-8")
        return destination

    def project(self, source, output, text, extra_pages=()):
        self.source(source, text)
        page = documentation.Page(Path(source), Path(output), "specification", "proposed", "Example")
        return documentation.rewrite_links(self.root, page, [page, *extra_pages], "https://example.invalid/repository", "revision")

    def test_embedded_wiki_image_with_spaces_resolves_to_published_asset(self):
        self.source("Eta_doc/Paint Draft Dayplanner.png", "image fixture")
        result = self.project("Eta_doc/Planungsphase.md", "specifications/Planungsphase.md", "![[Paint Draft Dayplanner.png]]\n")
        self.assertEqual("![Paint Draft Dayplanner.png](../assets/specifications/Paint%20Draft%20Dayplanner.png)\n", result)

    def test_code_formatted_link_label_still_resolves_to_projected_note(self):
        self.source(".claude/rules/database.md", "# Database\n")
        note = documentation.Page(Path(".claude/rules/database.md"), Path("notes/database.md"), "subject-note", "current", "Database")
        result = self.project("docs/architecture.md", "architecture.md", "[`Schema`](../.claude/rules/database.md#database)\n", [note])
        self.assertEqual("[`Schema`](notes/database.md#database)\n", result)

    def test_link_like_examples_inside_code_are_not_resolved(self):
        text = "`[missing](missing.md)`\n\n```markdown\n[[Missing]]\n[missing](missing.md)\n```\n"
        result = self.project("docs/example.md", "example.md", text)
        self.assertEqual(text, result)

    def test_broken_link_is_rejected_instead_of_published(self):
        with self.assertRaisesRegex(ValueError, "missing local link target"):
            self.project("docs/example.md", "example.md", "[Missing](missing.md)\n")

    def test_link_cannot_escape_repository(self):
        with self.assertRaisesRegex(ValueError, "escapes the repository"):
            self.project("docs/example.md", "example.md", "[Outside](../../outside.md)\n")

    def test_specific_review_mapping_wins_over_broad_application_fallback(self):
        manifest = {"topics": [
            {"id": "database", "paths": ["app/src/**/data/local/**"], "documents": ["database.md"]},
            {"id": "app", "fallback": True, "paths": ["app/**"], "documents": ["architecture.md"]},
        ]}
        self.assertEqual({"database": ["database.md"]}, documentation.review_topics(["app/src/main/java/example/data/local/Database.kt"], manifest))
        self.assertEqual({"app": ["architecture.md"]}, documentation.review_topics(["app/src/main/java/example/NewArea.kt"], manifest))
        with self.assertRaisesRegex(ValueError, "no documentation review mapping"):
            documentation.review_topics(["unmapped/config.txt"], manifest)

    def test_exported_schema_must_match_declared_database_version(self):
        self.source("gradle/libs.versions.toml", '[versions]\nroom = "3.0.1"\n')
        self.source("gradle/wrapper/gradle-wrapper.properties", "distributionUrl=gradle-9.5.0-bin.zip\n")
        self.source("app/build.gradle.kts", 'namespace = "example.eta"\napplicationId = "example.original"\nversion = release(37)\nminSdk = 24\ntargetSdk = 37\n')
        self.source("app/src/main/java/com/example/eta/data/local/EtaDatabase.kt", 'version = 28\nconst val NAME = "erik.db"\n')
        self.source("app/schemas/com.example.eta.data.local.EtaDatabase/28.json", json.dumps({"database": {"version": 27}}))
        with self.assertRaisesRegex(ValueError, "do not match"):
            documentation.project_reference(self.root)


if __name__ == "__main__":
    unittest.main()
