import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "blue" / "云端翔龙.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
SOURCE_ART = ROOT / "tools" / "card-artwork" / "Cloud_Serpent_TLC_888_original.png"
ART = ROOT / "cards" / "pictures" / "PH01" / "云端翔龙.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ABILITY_TEXT = (
    "{U}，请援龙：云端翔龙成为以此法展示的牌或选择的永久物的复制品。"
    "只能于云端翔龙在你手上时起动。"
)
ORACLE = ABILITY_TEXT
ORACLE_EN = (
    "{U}, Behold a Dragon: CARDNAME becomes a copy of the card revealed or "
    "permanent chosen this way. Activate only while CARDNAME is in your hand."
)


class CloudSerpentContractTest(unittest.TestCase):
    def test_hand_ability_pays_u_and_beholds_before_cloning(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:云端翔龙", lines)
        self.assertIn("ManaCost:1 U U", lines)
        self.assertIn("Types:Creature Dragon", lines)
        self.assertIn("PT:3/3", lines)

        transform = next(line for line in lines if line.startswith("A:ST$ Clone"))
        self.assertIn("Cost$ U Behold<1/Dragon.Other>", transform)
        self.assertIn("CostDesc$ {U}, behold a Dragon", transform)
        self.assertIn("ActivationZone$ Hand", transform)
        self.assertIn("Defined$ Revealed", transform)
        self.assertIn("CloneZone$ Hand", transform)
        self.assertIn("KeepCloneOnStack$ True", transform)
        self.assertNotIn("GainThisAbility$ True", transform)
        self.assertNotIn("A:AB$ Clone", "\n".join(lines))
        self.assertNotIn("AlternativeCost", "\n".join(lines))
        self.assertNotIn("SetManaCost$", transform)
        self.assertIn(f"Oracle:{ORACLE_EN}", lines)

    def test_registration_localization_art_and_documentation(self):
        self.assertIn(
            "193 R 云端翔龙 @James Ryman",
            EDITION.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            f"云端翔龙|云端翔龙|生物～龙|{ORACLE}".encode("utf-8"),
            ZH_CN.read_bytes().splitlines(),
        )
        self.assertIn(
            "| 云端翔龙 | `{1}{U}{U}`，3/3 生物～龙 | "
            "`cards/blue/云端翔龙.txt` | 193 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        self.assertTrue(SOURCE_ART.is_file())
        with Image.open(SOURCE_ART) as image:
            self.assertEqual("PNG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((512, 512), image.size)

        self.assertTrue(ART.is_file())
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((512, 374), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
