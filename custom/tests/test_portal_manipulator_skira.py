import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "blue" / "传送门操控师斯奇拉.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Portal_Manipulator_Skira.jpg"
ART = ROOT / "cards" / "pictures" / "PH01" / "传送门操控师斯奇拉.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = (
    "灵技\\n"
    "每当你弃一张或数张瞬间或法术牌时，你施放瞬间或法术咒语时可以支付{X}，"
    "而不支付其法术力费用，X等同于这些牌中最小的法术力值。每回合只能如此作一次。\\n"
    "{R}，弃一张牌：占卜1，然后抓一张牌。"
)


class PortalManipulatorSkiraContractTest(unittest.TestCase):
    def test_characteristics_and_discard_batch_alternative_cost(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:传送门操控师斯奇拉", text)
        self.assertIn("ManaCost:2 U U", text)
        self.assertIn("Types:Legendary Creature Werewolf Wizard", text)
        self.assertIn("PT:4/5", text)
        self.assertIn("K:Prowess", text)
        self.assertIn(
            "T:Mode$ DiscardedAll | ValidPlayer$ You | "
            "ValidCard$ Card.Instant,Card.Sorcery | TriggerZones$ Battlefield | "
            "ResolvedLimit$ 1 | Execute$ TrigAlternativeCost",
            text,
        )
        self.assertIn(
            "SVar:TrigAlternativeCost:DB$ Effect | SetChosenNumber$ X | "
            "StaticAbilities$ MayPlay | Duration$ UntilEndOfTurn",
            text,
        )
        self.assertIn(
            "SVar:MayPlay:Mode$ Continuous | MayPlay$ True | "
            "MayPlayAltManaCost$ ChosenNumber | MayPlayLimit$ 1 | "
            "MayPlayDontGrantZonePermissions$ True | Affected$ Instant,Sorcery",
            text,
        )
        self.assertIn("SVar:X:TriggerObjectsCards$LeastCardManaCost", text)

    def test_loot_ability_and_display_text(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "A:AB$ Scry | Cost$ R Discard<1/Card> | ScryNum$ 1 | "
            "SubAbility$ DBDraw",
            text,
        )
        self.assertIn("SVar:DBDraw:DB$ Draw | Defined$ You | NumCards$ 1", text)
        self.assertIn(f"Oracle:{ORACLE}", text)
        self.assertIn("73 M 传送门操控师斯奇拉 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertIn(
            f"传送门操控师斯奇拉|传送门操控师斯奇拉|传奇生物～狼人／法术师|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )

    def test_art_is_an_rgb_landscape_crop(self):
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertGreater(image.width, image.height)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.02)


if __name__ == "__main__":
    unittest.main()
