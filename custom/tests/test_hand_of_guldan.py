import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "blue" / "古尔丹之手.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "古尔丹之手.artcrop.jpg"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "1920px-Hand_of_Gul'dan_full.jpg"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class HandOfGuldanContractTest(unittest.TestCase):
    def test_card_matches_the_requested_cost_type_draw_and_madness(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:古尔丹之手", text)
        self.assertIn("ManaCost:2 U B", text)
        self.assertIn("Types:Sorcery", text)
        self.assertIn(
            "A:SP$ Draw | Defined$ You | NumCards$ 2 | SpellDescription$ 抓两张牌。",
            text,
        )
        self.assertIn("K:Madness:U B", text)
        self.assertIn("Oracle:抓两张牌。\\n疯魔{U}{B}", text)

    def test_card_is_registered_with_standard_crop_art(self):
        self.assertIn("29 R 古尔丹之手 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())

    def test_standard_art_crop_is_landscape_rgb_jpeg(self):
        from PIL import Image

        with Image.open(ART) as image:
            self.assertEqual((1920, 1401), image.size)
            self.assertEqual("RGB", image.mode)

    def test_zh_cn_display_text_matches_the_card(self):
        expected = "古尔丹之手|古尔丹之手|法术|抓两张牌。\\n疯魔{U}{B}"
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())


if __name__ == "__main__":
    unittest.main()
