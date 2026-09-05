from __future__ import annotations

import json
import os
from pathlib import Path
import sys
import unittest


EXPECTED_BASELINE_FAILURES = {
    "test_diy_wording_consistency.DiyWordingConsistencyTest.test_chinese_oracle_uses_consistent_magic_terms",
    "test_zh_cn_punctuation.ZhCnPunctuationContractTest.test_custom_localized_oracles_use_official_corner_brackets",
}


def run_suite(custom: Path) -> tuple[list[str], list[str], int]:
    os.chdir(custom)
    sys.path.insert(0, str(custom))
    suite = unittest.defaultTestLoader.discover("tests", pattern="test_*.py")
    result = unittest.TextTestRunner(verbosity=1).run(suite)
    failures = sorted(test.id() for test, _ in result.failures)
    errors = sorted(test.id() for test, _ in result.errors)
    return failures, errors, result.testsRun


def main() -> int:
    if len(sys.argv) != 4 or sys.argv[1] not in {"baseline", "compare"}:
        print("usage: dragon_breaths_regression_gate.py baseline|compare <forge-root> <baseline-json>")
        return 2

    mode = sys.argv[1]
    root = Path(sys.argv[2]).resolve()
    baseline_path = Path(sys.argv[3]).resolve()
    failures, errors, tests_run = run_suite(root / "custom")
    print(f"DIY_REGRESSION_TESTS_RUN={tests_run}")
    print("DIY_REGRESSION_FAILURES=" + json.dumps(failures, ensure_ascii=False))
    print("DIY_REGRESSION_ERRORS=" + json.dumps(errors, ensure_ascii=False))

    if mode == "baseline":
        if errors:
            print("Baseline has unexpected test errors; refusing to classify them away.")
            return 1
        if set(failures) != EXPECTED_BASELINE_FAILURES:
            print("Baseline failure set differs from the two verified pre-existing failures; refusing to publish.")
            return 1
        baseline_path.write_text(
            json.dumps({"failures": failures, "errors": errors, "tests_run": tests_run}, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        print("DIY_BASELINE_CLASSIFIED=OK")
        return 0

    baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
    if errors != baseline["errors"]:
        print("Post-change error set differs from baseline.")
        return 1
    if failures != baseline["failures"]:
        print("Post-change failure set differs from baseline.")
        return 1
    if tests_run < baseline["tests_run"]:
        print("Post-change suite ran fewer tests than baseline.")
        return 1
    print("DIY_REGRESSION_DELTA=ZERO")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
