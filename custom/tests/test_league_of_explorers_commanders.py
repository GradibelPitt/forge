import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
FINLEY = ROOT / "cards" / "blue" / "海中向导芬利爵士.txt"
ELISE = ROOT / "cards" / "multicolor" / "启迪者伊利斯.txt"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

KEYWORD = "K:Partner:探险者协会"
REMINDER = "探险者协会（你可以将两个来自探险者协会的角色共同用作指挥官）"


class LeagueOfExplorersCommanderContractTest(unittest.TestCase):
    def test_both_characters_share_the_same_typed_partner_ability(self):
        for card in (FINLEY, ELISE):
            with self.subTest(card=card.name):
                lines = card.read_text(encoding="utf-8").splitlines()
                self.assertEqual(1, lines.count(KEYWORD))
                oracle = next(line for line in lines if line.startswith("Oracle:"))
                self.assertTrue(oracle.endswith(rf"\n{REMINDER}"))

    def test_localized_oracle_text_ends_with_the_exact_reminder(self):
        localized_lines = ZH_CN.read_text(encoding="utf-8").splitlines()
        for name in ("海中向导芬利爵士", "启迪者伊利斯"):
            with self.subTest(name=name):
                matches = [line for line in localized_lines if line.startswith(f"{name}|")]
                self.assertEqual(1, len(matches))
                self.assertTrue(matches[0].endswith(rf"\n{REMINDER}"))

    def test_card_catalog_documents_the_shared_commander_group(self):
        catalog = (ROOT / "CARDS.md").read_text(encoding="utf-8")
        for name in ("海中向导芬利爵士", "启迪者伊利斯"):
            with self.subTest(name=name):
                row = next(line for line in catalog.splitlines() if line.startswith(f"| {name} |"))
                self.assertIn("探险者协会", row)
                self.assertIn("共同用作指挥官", row)


if __name__ == "__main__":
    unittest.main()
