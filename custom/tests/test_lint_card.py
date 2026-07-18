from pathlib import Path
import os
import runpy
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


class LintCardDeckRuleTest(unittest.TestCase):
    @staticmethod
    def _run(deck_rules, card_name="Construction Card"):
        script = (
            f"Name:{card_name}\n"
            "ManaCost:0\n"
            "Types:Artifact\n"
            + "".join(f"DeckRule:{rule}\n" for rule in deck_rules)
        )
        return LintCardDeckRuleTest._run_script(script)

    @staticmethod
    def _run_script(script):
        temp_dir = tempfile.TemporaryDirectory()
        card = Path(temp_dir.name) / "construction_card.txt"
        card.write_text(script, encoding="utf-8")
        result = subprocess.run(
            [sys.executable, str(LINTER), str(card)],
            cwd=ROOT,
            capture_output=True,
            text=True,
            encoding="utf-8",
        )
        temp_dir.cleanup()
        return result

    def assert_lint_accepts(self, *deck_rules):
        result = self._run(deck_rules)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def assert_lint_rejects(self, expected, *deck_rules):
        result = self._run(deck_rules)
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn(expected, result.stdout)

    def test_accepts_all_three_deck_rule_modes(self):
        self.assert_lint_accepts(
            "Id$ add-companions | Mode$ ADD_FIXED | Target$ Main | Card$ Fire, Ice | Amount$ 2",
            "Id$ choose-spell | Mode$ CHOOSE_ONE | Target$ Sideboard | Candidates$ Alpha;Beta;Gamma | Amount$ 1",
            "Id$ allow-color | Mode$ ALLOW | Constraint$ COMMANDER_COLOR_IDENTITY | Card$ Off-color, Example",
        )

    def test_accepts_two_add_fixed_rules_for_two_automatic_cards(self):
        self.assert_lint_accepts(
            "Id$ oddity-left | Mode$ ADD_FIXED | Target$ Main | Card$ Left Curiosity | Amount$ 1",
            "Id$ oddity-right | Mode$ ADD_FIXED | Target$ Main | Card$ Right Curiosity | Amount$ 1",
        )

    def test_accepts_each_allow_constraint(self):
        for constraint in (
            "FORMAT_CARD_POOL",
            "COMMANDER_COLOR_IDENTITY",
            "COPY_LIMIT",
            "BANNED_OR_RESTRICTED",
        ):
            with self.subTest(constraint=constraint):
                self.assert_lint_accepts(
                    f"Id$ allow-{constraint.lower()} | Mode$ ALLOW | Constraint$ {constraint} | Card$ Example Card"
                )
        self.assert_lint_accepts(
            "Id$ allow-section | Mode$ ALLOW | Constraint$ SECTION | Target$ Sideboard | Card$ Example Card"
        )

    def test_rejects_duplicate_rule_id(self):
        self.assert_lint_rejects(
            "duplicate DeckRule Id 'same-id'",
            "Id$ same-id | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1",
            "Id$ same-id | Mode$ ADD_FIXED | Target$ Main | Card$ Beta | Amount$ 1",
        )

    def test_rejects_duplicate_parameter(self):
        self.assert_lint_rejects(
            "duplicate parameter 'Amount'",
            "Id$ duplicate-param | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1 | Amount$ 2",
        )
        self.assert_lint_rejects(
            "duplicate parameter 'ID'",
            "Id$ first | ID$ second | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1",
        )

    def test_parameter_names_are_case_insensitive(self):
        self.assert_lint_accepts(
            "id$ lowercase | mode$ add_fixed | target$ main | card$ Alpha | amount$ 1"
        )

    def test_rule_ids_are_nfc_normalized_but_case_sensitive(self):
        self.assert_lint_rejects(
            "duplicate DeckRule Id",
            "Id$ caf\u00e9 | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1",
            "Id$ cafe\u0301 | Mode$ ADD_FIXED | Target$ Main | Card$ Beta | Amount$ 1",
        )
        self.assert_lint_accepts(
            "Id$ CaseSensitive | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1",
            "Id$ casesensitive | Mode$ ADD_FIXED | Target$ Main | Card$ Beta | Amount$ 1",
        )

    def test_rejects_unknown_mode_and_constraint(self):
        self.assert_lint_rejects(
            "unknown Mode 'SUMMON'",
            "Id$ unknown-mode | Mode$ SUMMON | Target$ Main | Card$ Alpha | Amount$ 1",
        )
        self.assert_lint_rejects(
            "unknown Constraint 'EVERYTHING'",
            "Id$ unknown-constraint | Mode$ ALLOW | Constraint$ EVERYTHING | Card$ Alpha",
        )

    def test_rejects_missing_required_fields(self):
        cases = (
            ("missing required parameter 'Id'", "Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1"),
            ("missing required parameter 'Mode'", "Id$ no-mode | Target$ Main | Card$ Alpha | Amount$ 1"),
            ("missing required parameter 'Target'", "Id$ no-target | Mode$ ADD_FIXED | Card$ Alpha | Amount$ 1"),
            ("missing required parameter 'Card'", "Id$ no-card | Mode$ ADD_FIXED | Target$ Main | Amount$ 1"),
            ("missing required parameter 'Amount'", "Id$ no-amount | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha"),
            ("missing required parameter 'Candidates'", "Id$ no-candidates | Mode$ CHOOSE_ONE | Target$ Main | Amount$ 1"),
            ("missing required parameter 'Constraint'", "Id$ no-constraint | Mode$ ALLOW | Card$ Alpha"),
        )
        for expected, rule in cases:
            with self.subTest(expected=expected):
                self.assert_lint_rejects(expected, rule)

    def test_rejects_bad_amount(self):
        for amount in ("zero", "0", "-1", "1001"):
            with self.subTest(amount=amount):
                self.assert_lint_rejects(
                    "Amount must be an integer from 1 through 1000",
                    f"Id$ bad-amount-{amount} | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ {amount}",
                )

    def test_rejects_bad_section(self):
        self.assert_lint_rejects(
            "unknown Target deck section 'MaybeBoard'",
            "Id$ bad-section | Mode$ ADD_FIXED | Target$ MaybeBoard | Card$ Alpha | Amount$ 1",
        )

    def test_section_allow_requires_target_and_other_allows_reject_it(self):
        self.assert_lint_rejects(
            "missing required parameter 'Target'",
            "Id$ no-section-target | Mode$ ALLOW | Constraint$ SECTION | Card$ Alpha",
        )
        self.assert_lint_rejects(
            "unexpected parameter 'Target' for Mode ALLOW with Constraint COPY_LIMIT",
            "Id$ extra-target | Mode$ ALLOW | Constraint$ COPY_LIMIT | Target$ Main | Card$ Alpha",
        )

    def test_rejects_empty_and_oversized_candidates(self):
        for candidates in ("; ;", "Alpha;;Beta", ";Alpha", "Alpha;"):
            with self.subTest(candidates=candidates):
                self.assert_lint_rejects(
                    "Candidates must contain from 1 through 1000 non-empty card names",
                    f"Id$ empty-candidates | Mode$ CHOOSE_ONE | Target$ Main | Candidates$ {candidates} | Amount$ 1",
                )
        repeated = ";".join("A" for _ in range(1001))
        self.assert_lint_accepts(
            f"Id$ repeated-candidates | Mode$ CHOOSE_ONE | Target$ Main | Candidates$ {repeated} | Amount$ 1"
        )

        too_many = ";".join(f"C{index:x}" for index in range(1001))
        self.assert_lint_rejects(
            "Candidates must contain from 1 through 1000 non-empty card names",
            f"Id$ too-many-candidates | Mode$ CHOOSE_ONE | Target$ Main | Candidates$ {too_many} | Amount$ 1",
        )

    def test_accepts_unicode_canonical_duplicate_candidates(self):
        canonical_spellings = ("\u00e9", "e\u0301", "\u00c9", "\u00df", "ss")
        candidates = ";".join(
            canonical_spellings[index % len(canonical_spellings)] for index in range(1000)
        )
        self.assert_lint_accepts(
            "Id$ canonical-candidates | Mode$ CHOOSE_ONE | Target$ Main "
            f"| Candidates$ {candidates} | Amount$ 1"
        )

        lint_namespace = runpy.run_path(str(LINTER))
        canonical = lint_namespace["_canonical_deck_rule_card_name"]
        self.assertEqual(canonical("\u00e9"), canonical("e\u0301"))
        self.assertEqual(canonical("\u00e9"), canonical("\u00c9"))
        self.assertEqual(canonical("\u00df"), canonical("ss"))

    def test_rejects_unexpected_or_malformed_parameters(self):
        self.assert_lint_rejects(
            "unexpected parameter 'Execute' for Mode ADD_FIXED",
            "Id$ dangerous | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1 | Execute$ ArbitrarySVar",
        )
        self.assert_lint_rejects(
            "parameter must use 'Name$ value' syntax",
            "Id$ malformed | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1 | Execute",
        )

    def test_rejects_per_copy_and_unknown_cardinality(self):
        for cardinality in ("PER_COPY", "SOMETHING_NEW"):
            with self.subTest(cardinality=cardinality):
                self.assert_lint_rejects(
                    f"unsupported Cardinality '{cardinality}'; only ONCE_PER_DECK is supported",
                    "Id$ cardinality | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1 "
                    f"| Cardinality$ {cardinality}",
                )

    def test_accepts_explicit_once_per_deck_cardinality(self):
        self.assert_lint_accepts(
            "Id$ cardinality | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1 "
            "| Cardinality$ ONCE_PER_DECK"
        )

    def test_rejects_too_many_rules_and_overlong_rule_line(self):
        rules = tuple(
            f"Id$ rule-{index} | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1"
            for index in range(101)
        )
        self.assert_lint_rejects("at most 100 DeckRule lines", *rules)

        overlong_card = "A" * 16400
        self.assert_lint_rejects(
            "DeckRule line exceeds 16384 UTF-8 bytes",
            f"Id$ overlong | Mode$ ADD_FIXED | Target$ Main | Card$ {overlong_card} | Amount$ 1",
        )

    def test_exact_rule_definition_limit_does_not_trigger_line_error(self):
        template = "Id$ {rule_id} | Mode$ ADD_FIXED | Target$ Main | Card$ {card} | Amount$ 1"
        fixed_length = len(template.format(rule_id="", card=""))
        payload_length = 16384 - fixed_length
        rule_id_length = payload_length // 2
        card_length = payload_length - rule_id_length
        rule = template.format(rule_id="I" * rule_id_length, card="C" * card_length)

        self.assertEqual(len(rule), 16384)
        result = self._run((rule,))
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("DeckRule line exceeds 16384 UTF-8 bytes", result.stdout)

    def test_rejects_overlong_field(self):
        overlong_id = "I" * 8193
        self.assert_lint_rejects(
            "DeckRule field exceeds 8192 UTF-8 bytes",
            f"Id$ {overlong_id} | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1",
        )

    def test_rejects_more_than_sixteen_fields(self):
        extra_fields = " | ".join(f"Extra{index}$ value" for index in range(12))
        self.assert_lint_rejects(
            "DeckRule may contain at most 16 fields",
            "Id$ too-many-fields | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1 | "
            + extra_fields,
        )

    def test_rejects_an_8192_digit_amount_without_crashing(self):
        result = self._run((
            "Id$ huge-amount | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ "
            + "9" * 8192,
        ))
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("DeckRule field exceeds 8192 UTF-8 bytes", result.stdout)
        self.assertNotIn("Traceback", result.stdout + result.stderr)

    def test_rule_id_length_uses_nfc_and_utf8_boundaries(self):
        self.assert_lint_accepts(
            f"Id$ {'I' * 1024} | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1"
        )
        self.assert_lint_rejects(
            "DeckRule Id exceeds 1024 UTF-8 bytes",
            f"Id$ {'I' * 1025} | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1",
        )
        self.assert_lint_accepts(
            f"Id$ {'e\u0301' * 512} | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1"
        )
        self.assert_lint_rejects(
            "DeckRule Id exceeds 1024 UTF-8 bytes",
            f"Id$ {'e\u0301' * 513} | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1",
        )
        self.assert_lint_accepts(
            f"Id$ {'\U0001f600' * 256} | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1"
        )
        self.assert_lint_rejects(
            "DeckRule Id exceeds 1024 UTF-8 bytes",
            f"Id$ {'\U0001f600' * 257} | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1",
        )

    def test_source_card_name_limit_only_applies_to_cards_with_deck_rules(self):
        self.assertEqual(
            self._run((
                "Id$ source-boundary | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1",
            ), card_name="S" * 4096).returncode,
            0,
        )
        self.assert_lint_rejects_with_name(
            "DeckRule source card name exceeds 4096 UTF-8 bytes",
            "S" * 4097,
            "Id$ source-over | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1",
        )
        self.assertEqual(self._run_without_deck_rule("S" * 4097).returncode, 0)

    def test_source_name_rejects_uppercase_expansion_and_uses_utf8_bytes(self):
        self.assertEqual(
            self._run((
                "Id$ source-sharp-s | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1",
            ), card_name="\u00df" * 2048).returncode,
            0,
        )
        self.assert_lint_rejects_with_name(
            "DeckRule source card name exceeds 4096 UTF-8 bytes",
            "\u00df" * 2049,
            "Id$ source-sharp-s-over | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1",
        )
        self.assertEqual(
            self._run((
                "Id$ source-astral | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1",
            ), card_name="\U0001f600" * 1024).returncode,
            0,
        )
        self.assert_lint_rejects_with_name(
            "DeckRule source card name exceeds 4096 UTF-8 bytes",
            "\U0001f600" * 1025,
            "Id$ source-astral-over | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1",
        )
        self.assertEqual(
            self._run((
                "Id$ source-uppercase | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1",
            ), card_name="\u0390" * 1024).returncode,
            0,
        )
        self.assert_lint_rejects_with_name(
            "DeckRule source card name exceeds 4096 UTF-8 bytes",
            "\u0390" * 1025,
            "Id$ source-uppercase-over | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1",
        )

    def test_card_name_display_and_canonical_lengths_are_bounded(self):
        self.assert_lint_accepts(
            f"Id$ card-boundary | Mode$ ADD_FIXED | Target$ Main | Card$ {'C' * 4096} | Amount$ 1"
        )
        self.assert_lint_rejects(
            "Card exceeds 4096 UTF-8 bytes",
            f"Id$ card-over | Mode$ ADD_FIXED | Target$ Main | Card$ {'C' * 4097} | Amount$ 1",
        )
        self.assert_lint_accepts(
            f"Id$ card-sharp-s | Mode$ ADD_FIXED | Target$ Main | Card$ {'\u00df' * 2048} | Amount$ 1"
        )
        self.assert_lint_rejects(
            "Card exceeds 4096 UTF-8 bytes",
            f"Id$ card-sharp-s-over | Mode$ ADD_FIXED | Target$ Main | Card$ {'\u00df' * 2049} | Amount$ 1",
        )
        self.assert_lint_accepts(
            f"Id$ card-uppercase | Mode$ ADD_FIXED | Target$ Main | Card$ {'\u0390' * 1024} | Amount$ 1"
        )
        self.assert_lint_rejects(
            "Card exceeds 4096 UTF-8 bytes",
            f"Id$ card-uppercase-over | Mode$ ADD_FIXED | Target$ Main | Card$ {'\u0390' * 1025} | Amount$ 1",
        )

    def test_candidate_display_and_canonical_lengths_are_bounded(self):
        self.assert_lint_accepts(
            f"Id$ candidate-boundary | Mode$ CHOOSE_ONE | Target$ Main | Candidates$ {'C' * 4096} | Amount$ 1"
        )
        self.assert_lint_rejects(
            "Candidate name exceeds 4096 UTF-8 bytes",
            f"Id$ candidate-over | Mode$ CHOOSE_ONE | Target$ Main | Candidates$ {'C' * 4097} | Amount$ 1",
        )
        self.assert_lint_accepts(
            f"Id$ candidate-sharp-s | Mode$ CHOOSE_ONE | Target$ Main | Candidates$ {'\u00df' * 2048} | Amount$ 1"
        )
        self.assert_lint_rejects(
            "Candidate name exceeds 4096 UTF-8 bytes",
            f"Id$ candidate-sharp-s-over | Mode$ CHOOSE_ONE | Target$ Main | Candidates$ {'\u00df' * 2049} | Amount$ 1",
        )
        self.assert_lint_accepts(
            f"Id$ candidate-uppercase | Mode$ CHOOSE_ONE | Target$ Main | Candidates$ {'\u0390' * 1024} | Amount$ 1"
        )
        self.assert_lint_rejects(
            "Candidate name exceeds 4096 UTF-8 bytes",
            f"Id$ candidate-uppercase-over | Mode$ CHOOSE_ONE | Target$ Main | Candidates$ {'\u0390' * 1025} | Amount$ 1",
        )

    def test_raw_field_limit_remains_independent_of_rule_id_limit(self):
        exact_field_id = "I" * 8187
        at_limit = self._run((
            f"Id$ {exact_field_id} | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1",
        ))
        self.assertNotEqual(at_limit.returncode, 0)
        self.assertIn("DeckRule Id exceeds 1024 UTF-8 bytes", at_limit.stdout)
        self.assertNotIn("DeckRule field exceeds 8192 UTF-8 bytes", at_limit.stdout)

        over_limit = self._run((
            f"Id$ {exact_field_id}I | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1",
        ))
        self.assertNotEqual(over_limit.returncode, 0)
        self.assertIn("DeckRule field exceeds 8192 UTF-8 bytes", over_limit.stdout)

        astral_exact_id = "\U0001f600" * 2046 + "III"
        astral_at_limit = self._run((
            f"Id$ {astral_exact_id} | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1",
        ))
        self.assertNotEqual(astral_at_limit.returncode, 0)
        self.assertIn("DeckRule Id exceeds 1024 UTF-8 bytes", astral_at_limit.stdout)
        self.assertNotIn("DeckRule field exceeds 8192 UTF-8 bytes", astral_at_limit.stdout)

        astral_over_limit = self._run((
            f"Id$ {astral_exact_id}I | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1",
        ))
        self.assertNotEqual(astral_over_limit.returncode, 0)
        self.assertIn("DeckRule field exceeds 8192 UTF-8 bytes", astral_over_limit.stdout)

    def test_rule_line_limit_uses_utf8_bytes(self):
        astral_payload = "\U0001f600" * 2041
        rule = (
            f"Id$ {astral_payload} | Mode$ ADD_FIXED | Target$ Main "
            f"| Card$ {astral_payload} | Amount$ 1"
        )
        self.assertLess(len(rule), 16384)
        self.assertGreater(len(rule.encode("utf-8")), 16384)
        self.assert_lint_rejects("DeckRule line exceeds 16384 UTF-8 bytes", rule)

    def test_source_name_rejects_c0_and_del_controls(self):
        valid_rule = "Id$ source-control | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1"
        for control in ("\x00", "\x1f", "\x7f"):
            with self.subTest(control=ord(control)):
                self.assert_lint_rejects_with_name(
                    "DeckRule source card name contains a forbidden control character",
                    f"Source{control}Name",
                    valid_rule,
                )

    def test_rule_id_rejects_c0_and_del_controls(self):
        for control in ("\x00", "\x1f", "\x7f"):
            with self.subTest(control=ord(control)):
                self.assert_lint_rejects(
                    "DeckRule Id contains a forbidden control character",
                    f"Id$ before{control}after | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1",
                )

    def test_control_character_helper_matches_rule_key_contract(self):
        lint_namespace = runpy.run_path(str(LINTER))
        contains_control = lint_namespace["_contains_forbidden_deck_rule_control"]
        for control in ("\x00", "\x1f", "\x7f"):
            self.assertTrue(contains_control(f"A{control}B"))
        self.assertTrue(contains_control("A\tB"))
        self.assertFalse(contains_control("A B"))
        self.assertFalse(contains_control("A\u0080B"))

    def test_card_and_candidate_fields_keep_current_control_character_behavior(self):
        self.assert_lint_accepts(
            "Id$ card-controls | Mode$ ADD_FIXED | Target$ Main | Card$ A\x00B\x1fC\x7fD | Amount$ 1",
            "Id$ candidate-controls | Mode$ CHOOSE_ONE | Target$ Main "
            "| Candidates$ A\x00B;C\x1fD;E\x7fF | Amount$ 1",
        )

    def test_more_than_one_hundred_rules_produces_one_bounded_error(self):
        first_hundred = tuple(
            f"Id$ valid-{index} | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1"
            for index in range(100)
        )
        ignored_tail = tuple(
            "Id$ repeated | Id$ duplicate | Mode$ DANGEROUS | Execute$ Arbitrary | Amount$ 999999999999999999"
            for _ in range(3000)
        )
        result = self._run(first_hundred + ignored_tail)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("[ERROR] Found 1 error(s)", result.stdout)
        self.assertEqual(result.stdout.count("at most 100 DeckRule lines"), 1)
        self.assertNotIn("duplicate parameter", result.stdout)
        self.assertNotIn("Execute", result.stdout)
        self.assertNotIn("DANGEROUS", result.stdout)

    def test_nbsp_in_a_field_key_is_not_stripped_like_python_whitespace(self):
        self.assert_lint_rejects(
            "unexpected parameter 'Id\u00a0'",
            "Id\u00a0$ nbsp-key | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1",
        )

    def test_nbsp_values_follow_java_trim_and_strip_semantics(self):
        self.assert_lint_accepts(
            "Id$ nbsp | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1",
            "Id$ \u00a0nbsp\u00a0 | Mode$ ADD_FIXED | Target$ Main | Card$ Beta | Amount$ 1",
        )
        self.assert_lint_rejects(
            "Amount must be an integer from 1 through 1000",
            "Id$ nbsp-amount | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1\u00a0",
        )

    def test_java_trim_and_strip_helpers_exclude_nbsp(self):
        lint_namespace = runpy.run_path(str(LINTER))
        java_trim = lint_namespace["_java_trim"]
        java_strip = lint_namespace["_java_strip"]

        self.assertEqual(java_trim(" \tAlpha\r\n"), "Alpha")
        self.assertEqual(java_strip("\x1fAlpha\x1f"), "Alpha")
        self.assertEqual(java_trim("\u00a0Alpha\u00a0"), "\u00a0Alpha\u00a0")
        self.assertEqual(java_strip("\u00a0Alpha\u00a0"), "\u00a0Alpha\u00a0")

    def test_utf8_limit_short_circuits_before_encoding_obviously_long_values(self):
        lint_namespace = runpy.run_path(str(LINTER))
        exceeds_limit = lint_namespace["_exceeds_utf8_limit"]

        class ObviouslyLongValue:
            def __len__(self):
                return 11

            def encode(self, _encoding):
                raise AssertionError("encode must not run after the character-count guard")

        self.assertTrue(exceeds_limit(ObviouslyLongValue(), 10))

    def test_card_name_limit_short_circuits_before_canonical_expansion(self):
        lint_namespace = runpy.run_path(str(LINTER))
        exceeds_name_limit = lint_namespace["_deck_rule_card_name_exceeds_limit"]
        exceeds_name_limit.__globals__["_canonical_deck_rule_card_name"] = (
            lambda _value: (_ for _ in ()).throw(
                AssertionError("canonicalization must not run after display overflow")
            )
        )
        self.assertTrue(exceeds_name_limit("A" * 4097))

    def test_split_source_name_rejects_controls_on_the_front_face(self):
        result = self._run_script(
            "AlternateMode:Split\n"
            "Name:Front\x00Face\n"
            "ManaCost:0\n"
            "Types:Instant\n"
            "ALTERNATE\n"
            "Name:Back Face\n"
            "DeckRule:Id$ split-control | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1\n"
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("DeckRule source card name contains a forbidden control character", result.stdout)

    def test_split_source_name_limit_includes_both_faces_and_separator(self):
        boundary = self._run_script(
            "AlternateMode:Split\n"
            f"Name:{'A' * 2046}\n"
            "ManaCost:0\n"
            "Types:Instant\n"
            "ALTERNATE\n"
            f"Name:{'B' * 2046}\n"
            "DeckRule:Id$ split-boundary | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1\n"
        )
        self.assertEqual(boundary.returncode, 0, boundary.stdout + boundary.stderr)

        over_limit = self._run_script(
            "AlternateMode:Split\n"
            f"Name:{'A' * 2047}\n"
            "ManaCost:0\n"
            "Types:Instant\n"
            "ALTERNATE\n"
            f"Name:{'B' * 2046}\n"
            "DeckRule:Id$ split-over | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1\n"
        )
        self.assertNotEqual(over_limit.returncode, 0)
        self.assertIn("DeckRule source card name exceeds 4096 UTF-8 bytes", over_limit.stdout)

    def test_transform_and_modal_use_only_the_front_face_as_source(self):
        for alternate_mode in ("Transform", "Modal", "DoubleFaced"):
            with self.subTest(alternate_mode=alternate_mode):
                accepted = self._run_script(
                    f"AlternateMode:{alternate_mode}\n"
                    f"Name:{'F' * 4096}\n"
                    "ManaCost:0\n"
                    "Types:Creature\n"
                    "ALTERNATE\n"
                    f"Name:{'B' * 5000}\x00Back\n"
                    "DeckRule:Id$ front-only | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1\n"
                )
                self.assertEqual(accepted.returncode, 0, accepted.stdout + accepted.stderr)

                front_control = self._run_script(
                    f"AlternateMode:{alternate_mode}\n"
                    "Name:Front\x7fFace\n"
                    "ManaCost:0\n"
                    "Types:Creature\n"
                    "ALTERNATE\n"
                    "Name:Clean Back\n"
                    "DeckRule:Id$ front-control | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1\n"
                )
                self.assertNotEqual(front_control.returncode, 0)
                self.assertIn(
                    "DeckRule source card name contains a forbidden control character",
                    front_control.stdout,
                )

        over_limit = self._run_script(
            "AlternateMode:Transform\n"
            f"Name:{'F' * 4097}\n"
            "ManaCost:0\n"
            "Types:Creature\n"
            "ALTERNATE\n"
            "Name:Back\n"
            "DeckRule:Id$ front-over | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1\n"
        )
        self.assertNotEqual(over_limit.returncode, 0)
        self.assertIn("DeckRule source card name exceeds 4096 UTF-8 bytes", over_limit.stdout)

    def test_unknown_or_incomplete_alternate_mode_fails_closed(self):
        unknown = self._run_script(
            "AlternateMode:FutureMode\n"
            "Name:Front\n"
            "ManaCost:0\n"
            "Types:Creature\n"
            "ALTERNATE\n"
            "Name:Back\n"
            "DeckRule:Id$ unknown-mode | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1\n"
        )
        self.assertNotEqual(unknown.returncode, 0)
        self.assertIn("cannot determine DeckRule source name", unknown.stdout)

        missing_split_face = self._run_script(
            "AlternateMode:Split\n"
            "Name:Front\n"
            "ManaCost:0\n"
            "Types:Instant\n"
            "DeckRule:Id$ missing-face | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1\n"
        )
        self.assertNotEqual(missing_split_face.returncode, 0)
        self.assertIn("cannot determine DeckRule source name", missing_split_face.stdout)

    def test_copy_face_from_with_face_fields_fails_fast(self):
        result = self._run_script(
            "AlternateMode:Split\n"
            "CopyFaceFrom:Front Proxy\n"
            "ManaCost:0\n"
            "Types:Instant\n"
            "ALTERNATE\n"
            "CopyFaceFrom:Back Proxy\n"
            "DeckRule:Id$ copied-faces | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1\n"
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("face field 'ManaCost' requires an initialized face", result.stdout)
        self.assertIn("face field 'Types' requires an initialized face", result.stdout)

    def test_dual_placeholder_split_does_not_require_face_mana_cost_or_types(self):
        result = self._run_script(
            "CopyFaceFrom:Bind\n"
            "AlternateMode:Split\n"
            "ALTERNATE\n"
            "CopyFaceFrom:Liberate\n"
            "DeckRule:Id$ placeholder-split | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1\n"
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_null_alternate_face_fields_do_not_satisfy_primary_requirements(self):
        result = self._run_script(
            "AlternateMode:Split\n"
            "ALTERNATE\n"
            "CopyFaceFrom:Back Proxy\n"
            "ManaCost:0\n"
            "Types:Instant\n"
            "DeckRule:Id$ null-face-fields | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1\n"
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("primary face name is missing", result.stdout)
        self.assertIn("face field 'ManaCost' requires an initialized face", result.stdout)
        self.assertIn("face field 'Types' requires an initialized face", result.stdout)
        self.assertIn("Missing required field: 'ManaCost'", result.stdout)
        self.assertIn("Missing required field: 'Types'", result.stdout)

    def test_explicit_empty_name_takes_priority_over_copy_face_placeholder(self):
        result = self._run_script(
            "CopyFaceFrom:Fallback Name\n"
            "Name:\n"
            "ManaCost:0\n"
            "Types:Artifact\n"
            "DeckRule:Id$ empty-name | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1\n"
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("primary face name is missing", result.stdout)

    def test_java_blank_actual_name_does_not_fall_back_to_placeholder(self):
        result = self._run_script(
            "CopyFaceFrom:Fallback Name\n"
            "Name:\u2003\n"
            "ManaCost:0\n"
            "Types:Artifact\n"
            "DeckRule:Id$ blank-name | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1\n"
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("primary face name is missing", result.stdout)

    def test_split_strips_each_face_before_combining_source_name(self):
        result = self._run_script(
            "AlternateMode:Split\n"
            f"Name:{'A' * 2046}\u2003\n"
            "ManaCost:0\n"
            "Types:Instant\n"
            "ALTERNATE\n"
            f"Name:\u2003{'B' * 2046}\n"
            "DeckRule:Id$ stripped-boundary | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1\n"
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_placeholder_face_rejects_direct_face_setter_fields(self):
        result = self._run_script(
            "CopyFaceFrom:Proxy\n"
            "ManaCost:0\n"
            "Types:Artifact\n"
            "Oracle:Text\n"
            "A:SP$ Draw | NumCards$ 1\n"
            "SVar:X:Number$ 1\n"
            "DeckRule:Id$ placeholder-setters | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1\n"
        )
        self.assertNotEqual(result.returncode, 0)
        for key in ("ManaCost", "Types", "Oracle", "A", "SVar"):
            self.assertIn(f"face field '{key}' requires an initialized face", result.stdout)

    def test_ordinary_multiface_filename_uses_cardrules_aggregation(self):
        split = self._run_script(
            "Name:Front\n"
            "ManaCost:0\n"
            "Types:Instant\n"
            "AlternateMode:Split\n"
            "ALTERNATE\n"
            "Name:Back\n"
        )
        self.assertEqual(split.returncode, 0, split.stdout + split.stderr)
        self.assertIn("script should be named 'front_back.txt'", split.stdout)

        transform = self._run_script(
            "Name:Front\n"
            "ManaCost:0\n"
            "Types:Creature\n"
            "AlternateMode:Transform\n"
            "ALTERNATE\n"
            "Name:Back\n"
        )
        self.assertEqual(transform.returncode, 0, transform.stdout + transform.stderr)
        self.assertIn("script should be named 'front.txt'", transform.stdout)
        self.assertNotIn("front_back.txt", transform.stdout)

    def test_top_level_key_is_exact_after_java_trim(self):
        for disguised_key in ("DeckRule ", "DeckRule\u00a0", "\u00a0DeckRule"):
            with self.subTest(disguised_key=disguised_key):
                result = self._run_script(
                    "Name:Ordinary Card\n"
                    "ManaCost:0\n"
                    "Types:Artifact\n"
                    f"{disguised_key}:Id$ ignored | Mode$ DANGEROUS | Execute$ Arbitrary\n"
                )
                self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

        recognized = self._run_script(
            "Name:Ordinary Card\n"
            "ManaCost:0\n"
            "Types:Artifact\n"
            "  DeckRule:Id$ recognized | Mode$ DANGEROUS\n"
        )
        self.assertNotEqual(recognized.returncode, 0)
        self.assertIn("unknown Mode 'DANGEROUS'", recognized.stdout)

    def test_amount_accepts_only_ascii_digits(self):
        for amount in ("\u0661", "\uff11", "+\u0661", "-\uff11"):
            with self.subTest(amount=amount):
                self.assert_lint_rejects(
                    "Amount must be an integer from 1 through 1000",
                    f"Id$ non-ascii-amount | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ {amount}",
                )

    def assert_lint_rejects_with_name(self, expected, card_name, *deck_rules):
        result = self._run(deck_rules, card_name=card_name)
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn(expected, result.stdout)

    @staticmethod
    def _run_without_deck_rule(card_name):
        script = f"Name:{card_name}\nManaCost:0\nTypes:Artifact\n"
        with tempfile.TemporaryDirectory() as temp_dir:
            card = Path(temp_dir) / "ordinary_card.txt"
            card.write_text(script, encoding="utf-8")
            return subprocess.run(
                [sys.executable, str(LINTER), str(card)],
                cwd=ROOT,
                capture_output=True,
                text=True,
                encoding="utf-8",
            )

    def test_does_not_treat_unknown_ordinary_script_lines_as_deck_rules(self):
        result = self._run((
            "Id$ normal | Mode$ ADD_FIXED | Target$ Main | Card$ Alpha | Amount$ 1",
        ))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

        script = "Name:Ordinary Card\nManaCost:0\nTypes:Artifact\nCustomMetadata:Mode$ NOT_A_DECK_RULE\n"
        with tempfile.TemporaryDirectory() as temp_dir:
            card = Path(temp_dir) / "ordinary_card.txt"
            card.write_text(script, encoding="utf-8")
            ordinary_result = subprocess.run(
                [sys.executable, str(LINTER), str(card)],
                cwd=ROOT,
                capture_output=True,
                text=True,
                encoding="utf-8",
            )
        self.assertEqual(ordinary_result.returncode, 0, ordinary_result.stdout + ordinary_result.stderr)


if __name__ == "__main__":
    unittest.main()
