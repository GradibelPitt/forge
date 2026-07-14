import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "black" / "尼鲁巴蛛网领主.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "尼鲁巴蛛网领主.artcrop.jpg"
OLD_FULL_ART = ROOT / "cards" / "pictures" / "PH01" / "尼鲁巴蛛网领主.full.jpg"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "282017.jpg"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class NerubianWeblordContractTest(unittest.TestCase):
    def test_card_taxes_opposing_creature_etb_triggers(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:尼鲁巴蛛网领主", text)
        self.assertIn("ManaCost:1 B", text)
        self.assertIn("Types:Creature Spider Undead", text)
        self.assertIn("PT:1/4", text)
        self.assertIn(
            "T:Mode$ AbilityTriggered | ValidDestination$ Battlefield | ValidMode$ ChangesZone,ChangesZoneAll | "
            "ValidSource$ Creature.OppCtrl | TriggerZones$ Battlefield | Execute$ TrigCounter",
            text,
        )
        self.assertIn(
            "SVar:TrigCounter:DB$ Counter | Defined$ TriggeredSpellAbility | UnlessCost$ 2 | "
            "UnlessPayer$ TriggeredSpellAbilityController",
            text,
        )
        self.assertIn("Oracle:每当一个由对手操控的生物的触发式异能因永久物进场而触发时，除非其操控者支付{2}，否则反击之。", text)

    def test_card_is_registered_with_the_standard_crop_art(self):
        edition = EDITION.read_text(encoding="utf-8")

        self.assertIn("19 C 尼鲁巴蛛网领主 @Custom", edition)
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())
        self.assertFalse(OLD_FULL_ART.exists())

    def test_standard_art_crop_is_landscape_rgb_jpeg(self):
        from PIL import Image

        self.assertTrue(ART.is_file(), f"missing art crop: {ART}")
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertGreater(image.width, image.height)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.02)

    def test_zh_cn_display_text_is_complete(self):
        expected = "尼鲁巴蛛网领主|尼鲁巴蛛网领主|生物～蜘蛛／亡灵|每当一个由对手操控的生物的触发式异能因永久物进场而触发时，除非其操控者支付{2}，否则反击之。"

        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())


if __name__ == "__main__":
    unittest.main()
