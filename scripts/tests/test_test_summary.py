"""Consumer-visible boundaries of the JUnit summary written to the CI step summary."""

import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "test_summary.py"
SPEC = importlib.util.spec_from_file_location("eta_test_summary", SCRIPT)
test_summary = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = test_summary
SPEC.loader.exec_module(test_summary)

PASSING_SUITE = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.example.eta.domain.YieldTest" tests="2" skipped="0" failures="0" errors="0">
  <testcase name="Fokus yields one point per hour" classname="com.example.eta.domain.YieldTest"/>
  <testcase name="Pause yields nothing" classname="com.example.eta.domain.YieldTest"/>
</testsuite>
"""

FAILING_SUITE = """<?xml version="1.0" encoding="UTF-8"?>
<testsuites>
  <testsuite name="com.example.eta.domain.PlanTest" tests="3" skipped="1" failures="1" errors="0">
    <testcase name="block | overlaps" classname="com.example.eta.domain.PlanTest">
      <failure message="expected:&lt;2&gt; but was:&lt;3&gt;" type="AssertionError">trace</failure>
    </testcase>
    <testcase name="skipped one" classname="com.example.eta.domain.PlanTest"><skipped/></testcase>
    <testcase name="fine" classname="com.example.eta.domain.PlanTest"/>
  </testsuite>
</testsuites>
"""


class TestSummaryTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.results = Path(self.directory.name)

    def write(self, name, content):
        (self.results / name).write_text(content, encoding="utf-8")

    def test_missing_results_directory_is_reported_without_failing(self):
        summary = test_summary.summarize(self.results / "absent", "Unit tests")
        self.assertIn("## Unit tests", summary)
        self.assertIn("No test results found", summary)

    def test_counts_are_reported_per_suite_and_in_total(self):
        self.write("TEST-yield.xml", PASSING_SUITE)
        self.write("TEST-plan.xml", FAILING_SUITE)
        summary = test_summary.summarize(self.results, "Unit tests")
        self.assertIn("| `com.example.eta.domain.YieldTest` | 2 | 2 | 0 | 0 | 0 |", summary)
        self.assertIn("| `com.example.eta.domain.PlanTest` | 3 | 1 | 1 | 0 | 1 |", summary)
        self.assertIn("**Total:** 5 tests, 3 passed, 1 failed, 0 errors, 1 skipped.", summary)

    def test_failed_test_is_listed_with_its_message_and_escaped_name(self):
        self.write("TEST-plan.xml", FAILING_SUITE)
        summary = test_summary.summarize(self.results, "Unit tests")
        self.assertIn("### Failed tests", summary)
        self.assertIn("- `com.example.eta.domain.PlanTest.block \\| overlaps`: expected:<2> but was:<3>", summary)

    def test_all_passing_run_has_no_failure_section(self):
        self.write("TEST-yield.xml", PASSING_SUITE)
        self.assertNotIn("Failed tests", test_summary.summarize(self.results, "Unit tests"))

    def test_unreadable_file_is_listed_and_other_files_still_count(self):
        self.write("TEST-broken.xml", "<testsuite")
        self.write("TEST-yield.xml", PASSING_SUITE)
        summary = test_summary.summarize(self.results, "Unit tests")
        self.assertIn("unreadable XML", summary)
        self.assertIn("**Total:** 2 tests, 2 passed", summary)

    def test_long_failure_message_is_truncated_to_one_line(self):
        long_message = "x" * 1000
        self.write("TEST-long.xml",
                   f'<testsuite name="S" tests="1" failures="1"><testcase name="t" classname="C">'
                   f'<failure message="{long_message}"/></testcase></testsuite>')
        summary = test_summary.summarize(self.results, "Unit tests")
        failure_line = next(line for line in summary.splitlines() if line.startswith("- `C.t`"))
        self.assertLessEqual(len(failure_line), test_summary.MESSAGE_LIMIT + 20)
        self.assertTrue(failure_line.endswith("…"))


if __name__ == "__main__":
    unittest.main()
