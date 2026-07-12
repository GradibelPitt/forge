from pathlib import Path
import os
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
LINTER = ROOT / "tools" / "lint_card.py"


class LintCardEncodingTest(unittest.TestCase):
    def test_linter_handles_a_unicode_card_name_with_a_legacy_console_encoding(self):
        script = "Name:马克扎尔的小鬼\nManaCost:B B\nTypes:Creature Demon\n"

        with tempfile.TemporaryDirectory() as temp_dir:
            card = Path(temp_dir) / "markzul_imp.txt"
            card.write_text(script, encoding="utf-8")
            environment = os.environ | {"PYTHONIOENCODING": "cp1252"}
            result = subprocess.run(
                [sys.executable, str(LINTER), str(card)],
                cwd=ROOT,
                env=environment,
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
            )

        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertNotIn("Filename mismatch", result.stdout)

    def test_linter_rejects_deck_limit_without_display_text(self):
        script = "Name:Limited Card\nManaCost:0\nTypes:Artifact\nK:DeckLimit:1\n"

        with tempfile.TemporaryDirectory() as temp_dir:
            card = Path(temp_dir) / "limited_card.txt"
            card.write_text(script, encoding="utf-8")
            result = subprocess.run(
                [sys.executable, str(LINTER), str(card)],
                cwd=ROOT,
                capture_output=True,
                text=True,
                encoding="utf-8",
            )

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("DeckLimit must include limit and display text", result.stdout)

    def test_linter_accepts_a_positive_deck_minimum(self):
        script = "Name:Minimum Card\nManaCost:0\nTypes:Artifact\nK:DeckMinimum:31\n"

        with tempfile.TemporaryDirectory() as temp_dir:
            card = Path(temp_dir) / "minimum_card.txt"
            card.write_text(script, encoding="utf-8")
            result = subprocess.run(
                [sys.executable, str(LINTER), str(card)],
                cwd=ROOT,
                capture_output=True,
                text=True,
                encoding="utf-8",
            )

        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_linter_rejects_malformed_deck_minimum(self):
        invalid_keywords = (
            "K:DeckMinimum",
            "K:DeckMinimum:zero",
            "K:DeckMinimum:0",
            "K:DeckMinimum:-1",
        )

        for keyword in invalid_keywords:
            with self.subTest(keyword=keyword), tempfile.TemporaryDirectory() as temp_dir:
                card = Path(temp_dir) / "minimum_card.txt"
                card.write_text(
                    f"Name:Minimum Card\nManaCost:0\nTypes:Artifact\n{keyword}\n",
                    encoding="utf-8",
                )
                result = subprocess.run(
                    [sys.executable, str(LINTER), str(card)],
                    cwd=ROOT,
                    capture_output=True,
                    text=True,
                    encoding="utf-8",
                )

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("DeckMinimum", result.stdout)


if __name__ == "__main__":
    unittest.main()
