import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "red" / "尘魔.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "EX1_243_Dust_Devil.png"
ART = ROOT / "cards" / "pictures" / "PH01" / "尘魔.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = (
    "作为施放此咒语的额外费用，选择一个由你操控的地，其在下个重置步骤不能被重置。\\n"
    "风怒（在战斗阶段结束后，具有风怒的生物共同执行一个额外的战斗阶段）"
)


class DustDevilContractTest(unittest.TestCase):
    def test_characteristics_additional_cost_and_windfury(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:尘魔", text)
        self.assertIn("ManaCost:R", text)
        self.assertIn("Types:Creature Elemental", text)
        self.assertIn("PT:3/1", text)
        self.assertIn("K:Windfury", text)
        self.assertIn(
            "S:Mode$ RaiseCost | ValidCard$ Card.Self | Type$ Spell | "
            "Cost$ Exert<1/Land/land> | EffectZone$ All",
            text,
        )
        self.assertIn(f"Oracle:{ORACLE}", text)

    def test_registration_localization_and_original_art_crop(self):
        self.assertIn(
            "126 R 尘魔 @Raymond Swanland",
            EDITION.read_text(encoding="utf-8"),
        )
        self.assertIn(
            f"尘魔|尘魔|生物～元素|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )

        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())
        with Image.open(ART_BACKUP) as image:
            self.assertEqual((512, 512), image.size)
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
