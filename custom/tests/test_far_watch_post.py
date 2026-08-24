import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "colorless" / "前沿哨所.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "前沿哨所.artcrop.jpg"
BACKUP = ROOT / "tools" / "card-artwork" / "800px-Far_Watch_Post_full.jpg"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class FarWatchPostContractTest(unittest.TestCase):
    def test_characteristics_and_battlefield_trigger(self):
        text = CARD.read_text(encoding="utf-8")
        self.assertIn("Name:前沿哨所", text)
        self.assertIn("ManaCost:2", text)
        self.assertIn("Types:Creature Wall", text)
        self.assertIn("PT:2/3", text)
        self.assertIn("K:Defender", text)
        trigger = next(line for line in text.splitlines() if line.startswith("T:Mode$ ChangesZone"))
        self.assertIn("Destination$ Hand", trigger)
        self.assertIn("ValidCard$ Card.nonLand+OpponentOwn", trigger)
        self.assertIn("TriggerZones$ Battlefield", trigger)
        self.assertNotIn("Origin$", trigger)

    def test_each_trigger_adds_an_independent_perpetual_cost_increase(self):
        text = CARD.read_text(encoding="utf-8")
        self.assertIn("Defined$ TriggeredCard", text)
        self.assertIn("staticAbilities$ RaiseCost", text)
        self.assertIn("Duration$ Perpetual", text)
        self.assertIn("Mode$ RaiseCost | ValidCard$ Card.Self | Type$ Spell | Amount$ 1 | EffectZone$ All", text)
        self.assertNotIn("Unique$", text)

    def test_registration_art_and_chinese_wording(self):
        self.assertIn("26 R 前沿哨所 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertTrue(ART.is_file())
        self.assertTrue(BACKUP.is_file())
        expected = "前沿哨所|前沿哨所|生物～墙|守军\\n每当一张非地牌从任何区域置入对手的手牌时，该牌永久获得「施放此牌的费用增加{1}。」"
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())

    def test_art_is_rgb_landscape_crop(self):
        from PIL import Image
        with Image.open(ART) as image:
            self.assertEqual((800, 584), image.size)
            self.assertEqual("RGB", image.mode)


if __name__ == "__main__":
    unittest.main()
