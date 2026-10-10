#!/usr/bin/env python3
"""Check canonical ETA documentation and build its human/agent projections."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import fnmatch
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tomllib
from urllib.parse import quote, unquote, urlsplit

ROOT = Path(__file__).resolve().parent.parent
STATUSES = {"current", "proposed", "accepted", "historical", "superseded"}
FRONT_MATTER = re.compile(r"\A---\n(.*?)\n---(?:\n|$)", re.DOTALL)
INLINE_LINK = re.compile(r"(!?\[[^\]\n]*\])\((<[^>\n]+>|[^)\n]+)\)")
REFERENCE_LINK = re.compile(r"^(\s*\[[^\]\n]+\]:\s*)(\S+)(.*)$", re.MULTILINE)
WIKI_LINK = re.compile(r"(!?)\[\[([^\]\n]+)\]\]")
GENERATED_PAGES = {Path("reference/project.md")}


@dataclass(frozen=True)
class Page:
    source: Path
    output: Path
    kind: str
    status: str
    title: str


def inside(root: Path, path: Path) -> Path:
    resolved = path.resolve()
    if not resolved.is_relative_to(root.resolve()):
        raise ValueError(f"Path escapes the repository: {path}")
    return resolved


def read_metadata(text: str) -> tuple[dict[str, str], str]:
    match = FRONT_MATTER.match(text)
    if not match:
        return {}, text
    metadata = {}
    for line in match[1].splitlines():
        field = re.fullmatch(r"([a-z_]+):\s*(.+)", line)
        if field:
            metadata[field[1]] = field[2].strip().strip('\"').strip("'")
    return metadata, text[match.end():]


def map_prose(text: str, transform) -> str:
    """Preserve fenced and inline code while resolving documentation links."""
    result = []
    fence = None
    for line in text.splitlines(keepends=True):
        marker = re.match(r"^\s*(`{3,}|~{3,})", line)
        if marker:
            if fence is None:
                fence = marker[1]
            elif marker[1][0] == fence[0] and len(marker[1]) >= len(fence):
                fence = None
            result.append(line)
        elif fence:
            result.append(line)
        else:
            # Match complete links before code spans: labels may contain code.
            pieces = re.split(r"(!?\[[^\]\n]*\]\([^)\n]+\)|!?\[\[[^\]\n]+\]\]|`+[^`\n]*`+)", line)
            result.append("".join(part if part.startswith("`") else transform(part) for part in pieces))
    return "".join(result)


def load_pages(root: Path) -> tuple[dict, list[Page]]:
    manifest = json.loads((root / "docs/documentation-map.json").read_text(encoding="utf-8"))
    if manifest.get("schema_version") != 1:
        raise ValueError("Unsupported documentation-map schema version.")
    pages = []
    for source in sorted((root / "docs").rglob("*.md")):
        inside(root, source)
        metadata, _ = read_metadata(source.read_text(encoding="utf-8"))
        for field in ("doc_type", "status", "owner", "summary"):
            if not metadata.get(field):
                raise ValueError(f"{source.relative_to(root)}: missing metadata field {field}")
        if metadata["status"] not in STATUSES:
            raise ValueError(f"{source.relative_to(root)}: invalid documentation status")
        if metadata["status"] == "superseded" and not metadata.get("superseded_by"):
            raise ValueError(f"{source.relative_to(root)}: superseded document needs superseded_by")
        output = source.relative_to(root / "docs")
        if output == Path("README.md"):
            output = Path("index.md")
        pages.append(Page(source.relative_to(root), output, metadata["doc_type"], metadata["status"], metadata.get("title", source.stem)))
    for collection in manifest["collections"]:
        directory = inside(root, root / collection["source"])
        if not directory.is_dir() or collection["status"] not in STATUSES:
            raise ValueError(f"Invalid source collection: {collection['source']}")
        output = Path(collection["output"])
        if output.is_absolute() or ".." in output.parts:
            raise ValueError(f"Invalid collection output: {output}")
        for source in sorted(directory.glob("*.md")):
            if not any(fnmatch.fnmatchcase(source.name, pattern) for pattern in collection["include"]):
                continue
            if any(fnmatch.fnmatchcase(source.name, pattern) for pattern in collection["exclude"]):
                continue
            inside(root, source)
            pages.append(Page(source.relative_to(root), output / source.name, collection["kind"], collection["status"], source.stem))
    sources = [page.source for page in pages]
    outputs = [page.output for page in pages]
    reserved = GENERATED_PAGES | {Path(collection["output"]) / "index.md" for collection in manifest["collections"]}
    if len(set(sources)) != len(sources) or len(set(outputs)) != len(outputs) or set(outputs) & reserved:
        raise ValueError("Duplicate or reserved documentation source/output path.")
    if Path("index.md") not in outputs:
        raise ValueError("docs/README.md is required as the documentation entry point.")
    topic_ids = set()
    for topic in manifest["topics"]:
        if topic["id"] in topic_ids or not topic["paths"] or not topic["documents"]:
            raise ValueError(f"Invalid documentation topic: {topic['id']}")
        topic_ids.add(topic["id"])
        for document in topic["documents"]:
            if Path(document) not in sources:
                raise ValueError(f"{topic['id']}: unmapped or missing canonical document {document}")
    return manifest, pages


def rewrite_links(root: Path, page: Page, pages: list[Page], repository_url: str, revision: str) -> str:
    """Resolve canonical links to site pages, assets, or versioned source links."""
    _, body = read_metadata((root / page.source).read_text(encoding="utf-8"))
    by_source = {inside(root, root / entry.source): entry for entry in pages}
    generated = GENERATED_PAGES | {Path(entry.output.parent) / "index.md" for entry in pages if entry.kind in {"specification", "subject-note", "development-history", "release-history"}}

    def target_url(target: str, wiki: bool = False) -> str:
        parsed = urlsplit(target)
        if parsed.scheme or parsed.netloc or not parsed.path:
            return target
        target_path = unquote(parsed.path)
        candidate = root / page.source.parent / target_path
        if wiki and not candidate.suffix:
            candidate = candidate.with_suffix(".md")
        candidate = inside(root, candidate)
        if wiki and not candidate.exists():
            matches = [root / entry.source for entry in pages if entry.source.stem == Path(target_path).stem]
            if len(matches) == 1:
                candidate = inside(root, matches[0])
        suffix = ("?" + parsed.query if parsed.query else "") + ("#" + parsed.fragment if parsed.fragment else "")
        if candidate in by_source:
            destination = by_source[candidate].output
            return quote(os.path.relpath(destination, page.output.parent), safe="/") + suffix
        virtual = Path(os.path.normpath(str(page.output.parent / target_path)))
        if not candidate.exists() and virtual in generated:
            return quote(target_path, safe="/") + suffix
        if not candidate.exists():
            raise ValueError(f"{page.source}: missing local link target {target}")
        relative = candidate.relative_to(root)
        if relative == Path("docs/documentation-map.json"):
            return quote(os.path.relpath("documentation-map.json", page.output.parent), safe="/") + suffix
        if candidate.is_file() and relative.parts[0] == "Eta_doc" and candidate.suffix.lower() in {".png", ".jpg", ".jpeg", ".svg"}:
            destination = Path("assets/specifications") / candidate.name
            return quote(os.path.relpath(destination, page.output.parent), safe="/") + suffix
        kind = "tree" if candidate.is_dir() else "blob"
        return f"{repository_url}/{kind}/{revision}/{quote(relative.as_posix(), safe='/')}" + suffix

    def transform(text: str) -> str:
        def wiki(match):
            name, separator, alias = match[2].partition("|")
            label = alias if separator else name
            return f"{match[1]}[{label}]({target_url(name, wiki=True)})"

        def inline(match):
            raw = match[2]
            if raw.startswith("<"):
                target, title = raw[1:-1], ""
            else:
                split = re.split(r'\s+(?=[\"\'])', raw, maxsplit=1)
                target, title = split[0], " " + split[1] if len(split) == 2 else ""
            return f"{match[1]}({target_url(target)}{title})"

        text = INLINE_LINK.sub(inline, text)
        text = REFERENCE_LINK.sub(lambda match: match[1] + target_url(match[2]) + match[3], text)
        return WIKI_LINK.sub(wiki, text)

    return map_prose(body, transform)


def project_reference(root: Path) -> str:
    versions = tomllib.loads((root / "gradle/libs.versions.toml").read_text(encoding="utf-8"))["versions"]
    build = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
    database = (root / "app/src/main/java/com/example/eta/data/local/EtaDatabase.kt").read_text(encoding="utf-8")
    wrapper = (root / "gradle/wrapper/gradle-wrapper.properties").read_text(encoding="utf-8")

    def extract(pattern: str, text: str) -> str:
        match = re.search(pattern, text)
        if not match:
            raise ValueError(f"Cannot derive project reference field: {pattern}")
        return match[1]

    version = int(extract(r"\bversion\s*=\s*(\d+)", database))
    schema = root / f"app/schemas/com.example.eta.data.local.EtaDatabase/{version}.json"
    if not schema.is_file() or json.loads(schema.read_text(encoding="utf-8"))["database"]["version"] != version:
        raise ValueError("Database version and exported Room schema do not match.")
    rows = [
        ("Application ID", extract(r'applicationId\s*=\s*"([^"]+)"', build)),
        ("Namespace", extract(r'namespace\s*=\s*"([^"]+)"', build)),
        ("Datenbankdatei", extract(r'const val NAME\s*=\s*"([^"]+)"', database)),
        ("Room-Schemaversion", str(version)),
        ("compileSdk", extract(r"version\s*=\s*release\((\d+)\)", build)),
        ("minSdk", extract(r"minSdk\s*=\s*(\d+)", build)),
        ("targetSdk", extract(r"targetSdk\s*=\s*(\d+)", build)),
        ("Gradle", extract(r"gradle-([\d.]+)-bin", wrapper)),
    ]
    rows.extend((name, value) for name, value in sorted(versions.items()))
    lines = ["# Generierte Projektreferenz", "", "Diese Werte stammen aus dem Build-Arbeitsverzeichnis, nicht aus einer laufenden Android-Installation. Sie werden bei jedem Dokumentationsbuild neu abgeleitet; diese Seite ist kein Test- oder Betriebsnachweis.", "", "| Eigenschaft | Wert |", "| --- | --- |"]
    lines.extend(f"| {name} | `{value}` |" for name, value in rows)
    return "\n".join(lines) + "\n"


def review_topics(paths: list[str], manifest: dict) -> dict[str, list[str]]:
    result = {}
    for path in paths:
        matches = [topic for topic in manifest["topics"] if not topic.get("fallback") and any(fnmatch.fnmatchcase(path, pattern) for pattern in topic["paths"])]
        if not matches:
            matches = [topic for topic in manifest["topics"] if topic.get("fallback") and any(fnmatch.fnmatchcase(path, pattern) for pattern in topic["paths"])]
        if not matches:
            raise ValueError(f"Changed path has no documentation review mapping: {path}")
        for topic in matches:
            result[topic["id"]] = topic["documents"]
    return result


def check(root: Path, base: str | None = None, head: str = "HEAD") -> tuple[dict, list[Page]]:
    manifest, pages = load_pages(root)
    for page in pages:
        rewrite_links(root, page, pages, manifest["repository_url"], "HEAD")
    project_reference(root)
    if base:
        changed = subprocess.run(["git", "diff", "--name-only", "-z", f"{base}...{head}", "--"], cwd=root, capture_output=True, text=True, check=True)
        topics = review_topics([path for path in changed.stdout.split("\0") if path], manifest)
        print("Documentation review targets (content review remains required):")
        for topic, documents in sorted(topics.items()):
            print(f"- {topic}: {', '.join(documents)}")
    print(f"Documentation checks passed: {len(pages)} canonical pages.", flush=True)
    return manifest, pages


def build_site(root: Path, manifest: dict, pages: list[Page]) -> Path:
    source = inside(root, root / "build/documentation/source")
    site = inside(root, root / "build/documentation/site")
    if source.exists():
        shutil.rmtree(source)
    if site.exists():
        shutil.rmtree(site)
    source.mkdir(parents=True)
    revision = subprocess.run(["git", "rev-parse", "HEAD"], cwd=root, capture_output=True, text=True, check=True).stdout.strip()
    for page in pages:
        body = rewrite_links(root, page, pages, manifest["repository_url"], revision)
        heading = "" if re.search(r"^# ", body, re.MULTILINE) else f"# {page.title}\n\n"
        if page.kind == "development-history":
            notice = "Historische Entwicklungsnotiz: frühere Entscheidungen und Beobachtungen. Versionsangaben und damalige Abläufe nicht als aktuellen Ist-Zustand verwenden; mit Fachnotizen und Code abgleichen."
        elif page.kind == "release-history":
            notice = "Historische Änderungsnotiz einer konkreten Lieferung; kein vollständiger Nachweis des heutigen App-Verhaltens."
        elif page.kind == "specification":
            notice = "Produktspezifikation: beschreibt Anforderungen, nicht automatisch das implementierte Verhalten. Abweichungen mit den Fachnotizen und dem aktuellen Code abgleichen."
        else:
            notice = "Dokumentation des Projekts; konkrete Implementierungs- und Laufzeitaussagen anhand der angegebenen Quellen prüfen."
        header = f"---\ntitle: {json.dumps(page.title, ensure_ascii=False)}\ndoc_type: {page.kind}\nstatus: {page.status}\n"
        if page.status in {"historical", "superseded"}:
            header += "search:\n  exclude: true\n"
        header += "---\n\n"
        canonical = quote(page.source.as_posix(), safe="/")
        provenance = f"\n\n---\n\nKanonische Quelle: `{page.source.as_posix()}` · [Quelldatei im Repository]({manifest['repository_url']}/blob/{revision}/{canonical}). Git-Basis: `{revision[:12]}`. Lokale, noch nicht commitete Änderungen können davon abweichen.\n"
        destination = source / page.output
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(header + heading + f"> **Status: {page.status}.** {notice}\n\n" + body + provenance, encoding="utf-8")
    for collection in manifest["collections"]:
        folder = Path(collection["output"])
        entries = [page for page in pages if page.output.parent == folder]
        entries.sort(key=lambda page: page.output.name, reverse=collection.get("descending", False))
        lines = [f"# {collection['title']}", "", "Die folgenden Seiten sind automatisch erzeugte Ansichten der bestehenden Quelldateien. Änderungen erfolgen ausschließlich an der kanonischen Quelle.", ""]
        if collection["status"] == "historical":
            lines.extend(["> Historisches Archiv; nicht als aktuelle Betriebs- oder Implementierungsanweisung verwenden. Aus der Standardsuche und dem kuratierten KI-Index ausgeschlossen.", ""])
        lines.extend(f"- [{page.title}]({quote(page.output.name)}) — `{page.source.as_posix()}`" for page in entries)
        index = source / folder / "index.md"
        index.parent.mkdir(parents=True, exist_ok=True)
        index.write_text(("---\nsearch:\n  exclude: true\n---\n\n" if collection["status"] == "historical" else "") + "\n".join(lines) + "\n", encoding="utf-8")
    reference = source / "reference/project.md"
    reference.parent.mkdir(parents=True, exist_ok=True)
    reference.write_text(project_reference(root), encoding="utf-8")
    shutil.copyfile(root / "docs/documentation-map.json", source / "documentation-map.json")
    for asset in (root / "Eta_doc").iterdir():
        if not asset.is_file() or asset.suffix.lower() not in {".png", ".jpg", ".jpeg", ".svg"}:
            continue
        inside(root, asset)
        destination = source / "assets/specifications" / asset.name
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(asset, destination)
    subprocess.run([sys.executable, "-m", "zensical", "build", "--strict", "--clean", "--config-file", "zensical.toml"], cwd=root, check=True)
    markdown = site / "markdown"
    shutil.copytree(source, markdown, dirs_exist_ok=True)
    index = ["# ETA", "", "> Gemeinsame, Git-versionierte Dokumentation für Menschen und KI-Agenten. Fachnotizen beschreiben die Implementierung; Produktspezifikationen die Anforderungen. Historische Notizen sind keine aktuellen Anweisungen.", "", f"Git-Basis: `{revision}`. Inhalte stammen aus dem Build-Arbeitsverzeichnis, nicht aus einer geprüften Android-Installation.", "", "## Aktuelle Leitfäden und Fachnotizen", ""]
    for page in pages:
        if page.kind != "specification" and page.status in {"current", "accepted"}:
            index.append(f"- [{page.title}](markdown/{quote(page.output.as_posix(), safe='/')}): `{page.source.as_posix()}`")
    index.extend(["", "## Produktspezifikationen", "", "Anforderungen; nicht als Nachweis des implementierten Verhaltens lesen.", ""])
    index.extend(f"- [{page.title}](markdown/{quote(page.output.as_posix(), safe='/')}): `{page.source.as_posix()}`" for page in pages if page.kind == "specification")
    index.extend(["", "## Generierte Referenz", "", "- [Projektkonfiguration](markdown/reference/project.md): aus Konfiguration und exportiertem Room-Schema abgeleitet."])
    (site / "llms.txt").write_text("\n".join(index) + "\n", encoding="utf-8")
    print(f"Documentation site built: {site.relative_to(root)}", flush=True)
    return site


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("check", "build", "serve"))
    parser.add_argument("--base", help="Git base revision for documentation review mapping")
    parser.add_argument("--head", default="HEAD")
    parser.add_argument("--port", type=int, default=8000)
    args = parser.parse_args()
    if not 1 <= args.port <= 65535:
        parser.error("--port must be between 1 and 65535")
    try:
        manifest, pages = check(ROOT, args.base, args.head)
        if args.command == "check":
            return 0
        site = build_site(ROOT, manifest, pages)
        if args.command == "serve":
            handler = partial(SimpleHTTPRequestHandler, directory=str(site))
            with ThreadingHTTPServer(("127.0.0.1", args.port), handler) as server:
                print(f"Serving ETA documentation at http://127.0.0.1:{args.port}/ (restart after source changes)", flush=True)
                server.serve_forever()
        return 0
    except KeyboardInterrupt:
        return 0
    except (ValueError, OSError, KeyError, subprocess.CalledProcessError) as error:
        print(f"Documentation error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
