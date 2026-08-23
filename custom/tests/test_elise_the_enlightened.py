import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "multicolor" / "启迪者伊利斯.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Elise_the_Enlightened_full.jpg"
ART = ROOT / "cards" / "pictures" / "PH01" / "启迪者伊利斯.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

SOURCE_ART_SHA256 = "8A81523772A8A506026D5AF19AB0EED6EB2BCC390773058E5B51F0AC0B4D6A04"
ZH_ORACLE = (
    "当你施放启迪者伊利斯时，如果你起始套牌中每张非地牌的名称均不相同，"
    "化生你手牌中每一张牌的复制并置入你手中。\\n"
    "探险者协会（你可以将两个来自探险者协会的角色共同用作指挥官）"
)


class EliseTheEnlightenedContractTest(unittest.TestCase):
    def test_characteristics_and_cast_trigger(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:启迪者伊利斯", text)
        self.assertIn("ManaCost:3 U G", text)
        self.assertIn("Types:Legendary Creature Elf Druid", text)
        self.assertIn("PT:5/5", text)
        self.assertIn(
            "T:Mode$ SpellCast | ValidCard$ Card.Self | TriggerZones$ Stack | "
            "Execute$ TrigConjure",
            text,
        )

    def test_highlander_condition_uses_the_registered_starting_deck(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "SVar:TrigConjure:DB$ MakeCard | Defined$ You | Conjure$ True | "
            "DefinedName$ ValidHand Card.YouCtrl | Zone$ Hand | "
            "ConditionCheckSVar$ StartingDeckDuplicateNonlandNames | "
            "ConditionSVarCompare$ EQ0",
            text,
        )
        self.assertIn(
            "SVar:StartingDeckDuplicateNonlandNames:Count$StartingDeckDuplicateNonlandNames",
            text,
        )
        self.assertNotIn("ValidLibrary", text)
        self.assertNotIn("ValidGraveyard", text)

    def test_registration_localization_and_hswiki_art_crop(self):
        self.assertIn("104 M 启迪者伊利斯 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertIn(
            f"启迪者伊利斯|启迪者伊利斯|传奇生物～精灵／德鲁伊|{ZH_ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )

        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())
        self.assertEqual(
            SOURCE_ART_SHA256,
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((1024, 747), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
