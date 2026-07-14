from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SUPERREACH_1 = ROOT / "cards" / "colorless" / "test_superreach_1.txt"
SUPERREACH_2 = ROOT / "cards" / "colorless" / "test_superreach_2.txt"
EDITION = ROOT / "editions" / "Test_Set.txt"


def edition_card_rows():
    lines = EDITION.read_text(encoding="utf-8").splitlines()
    cards_start = lines.index("[cards]") + 1
    rows = []
    for line in lines[cards_start:]:
        line = line.strip()
        if line.startswith("[") and line.endswith("]"):
            break
        if line:
            rows.append(line)
    return rows


class SuperreachCardsContractTest(unittest.TestCase):
    def test_superreach_1_contract(self):
        lines = SUPERREACH_1.read_text(encoding="utf-8").splitlines()
        self.assertEqual(
            [
                "Name:Test Superreach 1",
                "ManaCost:0",
                "Types:Creature",
                "PT:10/10",
                "K:Superreach",
            ],
            [line for line in lines if line.strip()],
        )

    def test_superreach_2_characteristics_and_keywords(self):
        lines = SUPERREACH_2.read_text(encoding="utf-8").splitlines()

        for expected in (
            "Name:Test Superreach 2",
            "ManaCost:0",
            "Types:Creature",
            "PT:20/20",
        ):
            with self.subTest(expected=expected):
                self.assertIn(expected, lines)
        self.assertEqual(
            [
                "K:Flying",
                "K:Fear",
                "K:Menace",
                "K:Shadow",
                "K:Landwalk:Land",
                "K:Horsemanship",
                "K:Skulk",
            ],
            [line for line in lines if line.startswith("K:")],
        )

    def test_superreach_2_oracle_displays_every_ability(self):
        lines = SUPERREACH_2.read_text(encoding="utf-8").splitlines()
        oracle_lines = [line for line in lines if line.startswith("Oracle:")]

        self.assertEqual(1, len(oracle_lines))
        oracle = oracle_lines[0].removeprefix("Oracle:")
        for keyword in (
            "Flying",
            "fear",
            "menace",
            "shadow",
            "landwalk",
            "horsemanship",
            "skulk",
        ):
            with self.subTest(keyword=keyword):
                self.assertIn(keyword, oracle)
        for restriction in (
            "CARDNAME can't be blocked.",
            "CARDNAME can't be blocked except by artifact creatures.",
            "Creatures with power less than 20 can't block CARDNAME.",
            "Creatures with power greater than 1 can't block CARDNAME.",
        ):
            with self.subTest(restriction=restriction):
                self.assertIn(restriction, oracle)

    def test_superreach_2_has_exact_attacker_hosted_restrictions(self):
        lines = SUPERREACH_2.read_text(encoding="utf-8").splitlines()
        restrictions = [line for line in lines if line.startswith("S:Mode$ CantBlockBy |")]

        self.assertEqual(
            [
                "S:Mode$ CantBlockBy | ValidAttacker$ Creature.Self | Description$ CARDNAME can't be blocked.",
                "S:Mode$ CantBlockBy | ValidAttacker$ Creature.Self | ValidBlocker$ Creature.nonArtifact | Description$ CARDNAME can't be blocked except by artifact creatures.",
                "S:Mode$ CantBlockBy | ValidAttacker$ Creature.Self | ValidBlocker$ Creature.powerLT20 | Description$ Creatures with power less than 20 can't block CARDNAME.",
                "S:Mode$ CantBlockBy | ValidAttacker$ Creature.Self | ValidBlocker$ Creature.powerGT1 | Description$ Creatures with power greater than 1 can't block CARDNAME.",
            ],
            restrictions,
        )
        self.assertNotIn("ValidBlocker$", restrictions[0])

    def test_cards_are_listed_in_the_custom_edition(self):
        rows = edition_card_rows()

        self.assertIn("8 C Test Superreach 1", rows)
        self.assertIn("9 C Test Superreach 2", rows)
        self.assertEqual(1, rows.count("8 C Test Superreach 1"))
        self.assertEqual(1, rows.count("9 C Test Superreach 2"))

    def test_custom_edition_card_rows_have_unique_numbers_and_names(self):
        rows = edition_card_rows()
        collector_numbers = []
        card_names = []

        for row in rows:
            with self.subTest(row=row):
                fields = row.split(maxsplit=2)
                self.assertEqual(3, len(fields), f"Malformed edition card row: {row}")
                collector_number, rarity, card_name = fields
                self.assertRegex(collector_number, r"^\d+[a-z]?$" )
                self.assertTrue(rarity)
                self.assertTrue(card_name)
                collector_numbers.append(collector_number)
                card_names.append(card_name)

        self.assertEqual(len(collector_numbers), len(set(collector_numbers)))
        self.assertEqual(len(card_names), len(set(card_names)))


if __name__ == "__main__":
    unittest.main()
