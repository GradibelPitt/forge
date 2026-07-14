import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "blue" / "农夫.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "农夫.artcrop.jpg"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "800px-Peasant_full.jpg"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class PeasantContractTest(unittest.TestCase):
    def test_card_matches_the_requested_upkeep_draw(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:农夫", text)
        self.assertIn("ManaCost:2", text)
        self.assertIn("Types:Creature Human Citizen", text)
        self.assertIn("PT:2/1", text)
        self.assertIn(
            "T:Mode$ Phase | Phase$ Upkeep | ValidPlayer$ You | TriggerZones$ Battlefield | Execute$ TrigDraw | "
            "TriggerDescription$ At the beginning of your upkeep, draw a card.",
            text,
        )
        self.assertIn("SVar:TrigDraw:DB$ Draw | Defined$ You | NumCards$ 1", text)
        self.assertIn("Oracle:在你的维持开始时，抓一张牌。", text)

    def test_upkeep_trigger_only_functions_on_the_battlefield(self):
        text = CARD.read_text(encoding="utf-8")

        trigger = next(line for line in text.splitlines() if line.startswith("T:Mode$ Phase"))
        self.assertIn("TriggerZones$ Battlefield", trigger)

    def test_card_is_registered_with_standard_crop_art(self):
        edition = EDITION.read_text(encoding="utf-8")

        self.assertIn("20 C 农夫 @Custom", edition)
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())

    def test_standard_art_crop_uses_the_landscape_card_frame_ratio(self):
        from PIL import Image

        with Image.open(ART) as image:
            self.assertEqual((800, 584), image.size)
            self.assertEqual("RGB", image.mode)

    def test_zh_cn_display_text_matches_the_requested_description(self):
        expected = "农夫|农夫|生物～人类／平民|在你的维持开始时，抓一张牌。"

        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())


if __name__ == "__main__":
    unittest.main()
