#!/usr/bin/env python3
"""Render JUnit XML results as a Markdown summary for GITHUB_STEP_SUMMARY.

Usage: python3 scripts/test_summary.py RESULTS_DIR [--title TITLE]

Reads every *.xml below RESULTS_DIR, prints counts per suite and the failed
test cases with their messages. Always exits 0: the summary must never fail a
job whose Gradle step already decided the outcome.
"""

import argparse
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

MESSAGE_LIMIT = 300


def suites_in(root: ET.Element) -> list[ET.Element]:
    if root.tag == "testsuite":
        return [root]
    return root.findall("testsuite")


def failure_of(case: ET.Element) -> ET.Element | None:
    for child in case:
        if child.tag in ("failure", "error"):
            return child
    return None


def first_line(text: str | None) -> str:
    return next((line.strip() for line in (text or "").splitlines() if line.strip()), "")


def clean(text: str) -> str:
    collapsed = " ".join(text.split())
    if len(collapsed) > MESSAGE_LIMIT:
        collapsed = collapsed[: MESSAGE_LIMIT - 1] + "…"
    return collapsed.replace("|", "\\|").replace("`", "'")


def summarize(results_dir: Path, title: str) -> str:
    files = sorted(results_dir.rglob("*.xml")) if results_dir.is_dir() else []
    lines = [f"## {title}", ""]
    if not files:
        lines.append(f"No test results found in `{results_dir}`.")
        return "\n".join(lines) + "\n"

    rows: list[tuple[str, int, int, int, int, int]] = []
    failed: list[str] = []
    for path in files:
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError as error:
            failed.append(f"- `{clean(path.name)}`: unreadable XML ({clean(str(error))})")
            continue
        for suite in suites_in(root):
            name = suite.get("name", path.stem)
            tests = int(suite.get("tests", "0"))
            failures = int(suite.get("failures", "0"))
            errors = int(suite.get("errors", "0"))
            skipped = int(suite.get("skipped", "0"))
            rows.append((name, tests, tests - failures - errors - skipped, failures, errors, skipped))
            for case in suite.findall("testcase"):
                failure = failure_of(case)
                if failure is None:
                    continue
                label = f"{case.get('classname', name)}.{case.get('name', '?')}"
                message = failure.get("message") or first_line(failure.text) or "no message"
                failed.append(f"- `{clean(label)}`: {clean(message)}")

    totals = [sum(row[index] for row in rows) for index in range(1, 6)]
    lines += [
        "| Suite | Tests | Passed | Failed | Errors | Skipped |",
        "| --- | ---: | ---: | ---: | ---: | ---: |",
    ]
    lines += [f"| `{clean(name)}` | {tests} | {passed} | {failures} | {errors} | {skipped} |"
              for name, tests, passed, failures, errors, skipped in rows]
    lines += [
        "",
        f"**Total:** {totals[0]} tests, {totals[1]} passed, {totals[2]} failed, "
        f"{totals[3]} errors, {totals[4]} skipped.",
        "",
    ]
    if failed:
        lines += ["### Failed tests", "", *failed, ""]
    return "\n".join(lines) + "\n"


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("results_dir", type=Path)
    parser.add_argument("--title", default="Unit test results")
    arguments = parser.parse_args(argv)
    sys.stdout.write(summarize(arguments.results_dir, arguments.title))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
