import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "colorless" / "镀银魔像.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "镀银魔像.artcrop.jpg"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Silverware_Golem_full.jpg"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class SilverwareGolemContractTest(unittest.TestCase):
    def test_card_has_the_requested_artifact_creature_stats_and_madness(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:镀银魔像", text)
        self.assertIn("ManaCost:2 B", text)
        self.assertIn("Types:Artifact Creature Golem", text)
        self.assertIn("PT:3/3", text)
        self.assertIn("K:Madness:B", text)

    def test_card_is_registered_with_backup_and_dynamic_art(self):
        self.assertIn("33 R 镀银魔像 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())

    def test_zh_cn_display_text_matches_the_requested_description(self):
        expected = "镀银魔像|镀银魔像|神器生物～魔像|疯魔{B}"
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())


if __name__ == "__main__":
    unittest.main()
