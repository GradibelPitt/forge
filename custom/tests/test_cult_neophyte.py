import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "multicolor" / "异教低阶牧师.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "异教低阶牧师.artcrop.jpg"
BACKUP = ROOT / "tools" / "card-artwork" / "Cult_Neophyte_full.jpg"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class CultNeophyteContractTest(unittest.TestCase):
    def test_characteristics_and_etb_cost_increase_until_next_turn(self):
        text = CARD.read_text(encoding="utf-8")
        self.assertIn("Name:异教低阶牧师", text)
        self.assertIn("ManaCost:U B", text)
        self.assertNotIn("ManaCost:UB", text)
        self.assertIn("Types:Creature Human Cleric", text)
        self.assertIn("PT:3/2", text)
        self.assertIn(
            "T:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | ValidCard$ Card.Self | "
            "Execute$ TrigTax",
            text,
        )
        self.assertIn("SVar:TrigTax:DB$ Effect | Duration$ UntilYourNextTurn | StaticAbilities$ RaiseCost", text)
        self.assertIn(
            "SVar:RaiseCost:Mode$ RaiseCost | ValidCard$ Card.nonCreature | Activator$ Opponent | "
            "Type$ Spell | Amount$ 1",
            text,
        )

    def test_registration_and_chinese_wording(self):
        self.assertIn("27 R 异教低阶牧师 @Custom", EDITION.read_text(encoding="utf-8"))
        expected = "异教低阶牧师|异教低阶牧师|生物～人类／牧师|当此生物进场时，直到你的下一个回合，所有对手施放的非生物咒语增加{1}来施放。"
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())

    def test_art_is_preserved_and_cropped_for_dynamic_frame(self):
        from PIL import Image

        self.assertTrue(BACKUP.is_file())
        self.assertTrue(ART.is_file())
        with Image.open(ART) as image:
            width, height = image.size
            self.assertEqual("RGB", image.mode)
            self.assertGreater(width, height)
            self.assertAlmostEqual(1.37, width / height, delta=0.02)


if __name__ == "__main__":
    unittest.main()
