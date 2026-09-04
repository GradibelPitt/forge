import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "blue" / "大法师罗曼斯.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "RLK_803_art.jpg"
ART_SOURCE = ROOT / "tools" / "card-artwork" / "RLK_803_art.source.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "大法师罗曼斯.artcrop.jpg"


class GrandMagisterRommathContractTest(unittest.TestCase):
    def test_characteristics(self):
        text = CARD.read_text(encoding="utf-8")
        self.assertIn("Name:大法师罗曼斯", text)
        self.assertIn("ManaCost:5 U U U U", text)
        self.assertIn("Types:Legendary Creature Human Wizard", text)
        self.assertIn("PT:5/7", text)

    def test_cast_trigger_returns_only_your_spells_and_grants_free_cast(self):
        text = CARD.read_text(encoding="utf-8")
        self.assertIn(
            "T:Mode$ SpellCast | ValidCard$ Card.Self | TriggerZones$ Stack | "
            "Execute$ TrigReturnSpells",
            text,
        )
        self.assertIn(
            "SVar:TrigReturnSpells:DB$ ChangeZoneAll | Defined$ You | "
            "Origin$ Graveyard | Destination$ Hand | "
            "ChangeType$ Instant.YouOwn,Sorcery.YouOwn | RememberChanged$ True | "
            "SubAbility$ DBFreeCastEffect",
            text,
        )
        self.assertIn(
            "SVar:DBFreeCastEffect:DB$ Effect | RememberObjects$ Remembered | "
            "StaticAbilities$ FreeCast | ForgetOnMoved$ Hand | "
            "Duration$ UntilEndOfTurn | SubAbility$ DBCleanupReturned",
            text,
        )
        self.assertIn(
            "SVar:FreeCast:Mode$ Continuous | "
            "Affected$ Instant.IsRemembered,Sorcery.IsRemembered | "
            "MayPlay$ True | MayPlayWithoutManaCost$ True | AffectedZone$ Hand",
            text,
        )
        self.assertIn(
            "SVar:DBCleanupReturned:DB$ Cleanup | ClearRemembered$ True",
            text,
        )

    def test_registration_localization_and_hswiki_art_crop(self):
        edition = EDITION.read_text(encoding="utf-8")
        self.assertIn("139 M 大法师罗曼斯 @Custom", edition)

        localization = ZH_CN.read_text(encoding="utf-8").splitlines()
        line = next(item for item in localization if item.startswith("大法师罗曼斯|大法师罗曼斯|"))
        self.assertIn("传奇生物～人类／法术师", line)
        self.assertIn("将你坟墓场中的每张瞬间牌和法术牌移回你手上", line)
        self.assertIn("不支付这些牌的法术力费用来施放它们", line)

        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART_SOURCE.is_file())
        source_text = ART_SOURCE.read_text(encoding="utf-8")
        self.assertIn("hearthstone.wiki.gg", source_text)
        self.assertIn("Grand_Magister_Rommath_full.jpg", source_text)
        self.assertTrue(ART.is_file())
        with Image.open(ART_BACKUP) as image:
            self.assertGreaterEqual(image.width, 2000)
            self.assertGreaterEqual(image.height, 2000)
            self.assertGreater(image.height, image.width)
            self.assertGreaterEqual(image.width / image.height, 0.60)
            self.assertLessEqual(image.width / image.height, 0.85)
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
