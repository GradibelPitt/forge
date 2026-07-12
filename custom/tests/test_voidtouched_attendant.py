import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "red" / "虚触侍从.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "虚触侍从.artcrop.jpg"
BACKUP = ROOT / "tools" / "card-artwork" / "Voidtouched_Attendant_full.jpg"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class VoidtouchedAttendantContractTest(unittest.TestCase):
    def test_characteristics_and_global_damage_increase(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:虚触侍从", text)
        self.assertIn("ManaCost:R", text)
        self.assertIn("Types:Creature Human Warlock", text)
        self.assertIn("PT:1/3", text)
        self.assertIn(
            "R:Event$ DamageDone | ActiveZones$ Battlefield | ValidTarget$ Player | "
            "ReplaceWith$ DmgPlus1",
            text,
        )
        self.assertIn(
            "SVar:DmgPlus1:DB$ ReplaceEffect | VarName$ DamageAmount | VarValue$ X",
            text,
        )
        self.assertIn("SVar:X:ReplaceCount$DamageAmount/Plus.1", text)

    def test_registration_art_and_chinese_display_text(self):
        self.assertIn("28 R 虚触侍从 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertTrue(BACKUP.is_file())
        self.assertTrue(ART.is_file())
        expected = "虚触侍从|虚触侍从|生物～人类／术士|每位牌手受到的所有伤害增加1点。"
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())

    def test_art_is_an_rgb_landscape_crop(self):
        from PIL import Image

        with Image.open(ART) as image:
            width, height = image.size
            self.assertEqual("RGB", image.mode)
            self.assertGreater(width, height)
            self.assertAlmostEqual(1.37, width / height, delta=0.02)


if __name__ == "__main__":
    unittest.main()
