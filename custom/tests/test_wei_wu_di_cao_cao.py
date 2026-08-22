import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "multicolor" / "魏武帝曹操.txt"
EDITION = ROOT / "editions" / "BoTu_Three_Kingdoms_New_Chapter.txt"
ART_ORIGINAL = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-d0e73c6c-f272-4912-a125-24d7908755cd.png"
)
ART = ROOT / "cards" / "pictures" / "BT3K" / "魏武帝曹操.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

RULES_TEXT = (
    "守护～弃两张牌。\\n"
    "奸雄 — 如果一个不由你操控且本回合曾对你造成过伤害的瞬间或法术咒语将因结算而置入坟墓场，"
    "改为将它放逐。\\n"
    "于这些牌持续被放逐期间，你可以不支付其法术力费用来施放它们。"
)


class WeiWuDiCaoCaoContractTest(unittest.TestCase):
    def test_characteristics_and_ward(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:魏武帝曹操", lines)
        self.assertIn("ManaCost:B R", lines)
        self.assertIn("Types:Legendary Creature Human Noble", lines)
        self.assertIn("PT:1/2", lines)
        self.assertIn("K:Ward:Discard<2/Card>", lines)

    def test_jianxiong_replaces_only_damaging_spells_resolving(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()
        replacement = next(line for line in lines if line.startswith("R:Event$ Moved"))

        self.assertIn("ActiveZones$ Battlefield", replacement)
        self.assertIn("Origin$ Stack", replacement)
        self.assertIn("Destination$ Graveyard", replacement)
        self.assertIn("Fizzle$ False", replacement)
        self.assertIn(
            "ValidCard$ Instant.YouDontCtrl+dealtDamageToYouThisTurn,"
            "Sorcery.YouDontCtrl+dealtDamageToYouThisTurn",
            replacement,
        )
        self.assertIn("ReplaceWith$ ExileDamagingSpell", replacement)

        exile = next(
            line for line in lines if line.startswith("SVar:ExileDamagingSpell:")
        )
        self.assertIn("DB$ ChangeZone", exile)
        self.assertIn("Defined$ ReplacedCard", exile)
        self.assertIn("Origin$ Stack", exile)
        self.assertIn("Destination$ Exile", exile)
        self.assertIn("ExiledWithEffectSource$ True", exile)

    def test_exiled_cards_can_be_cast_without_mana_cost(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()
        permission = next(line for line in lines if line.startswith("S:Mode$ Continuous"))

        self.assertIn("Affected$ Card.ExiledWithSource", permission)
        self.assertIn("AffectedZone$ Exile", permission)
        self.assertIn("MayPlay$ True", permission)
        self.assertIn("MayPlayWithoutManaCost$ True", permission)
        self.assertIn(f"Oracle:{RULES_TEXT}", lines)

    def test_registration_localization_documentation_and_art(self):
        edition = EDITION.read_text(encoding="utf-8")
        self.assertIn("Code=BT3K", edition)
        self.assertIn("Name=博图三国新篇", edition)
        self.assertIn("3 M 魏武帝曹操 @Custom", edition)

        self.assertIn(
            f"魏武帝曹操|魏武帝曹操|传奇生物～人类／贵族|{RULES_TEXT}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 魏武帝曹操 | `{B}{R}` 1/2 传奇生物～人类／贵族 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        self.assertEqual(
            "07A47022CCBD29934B3A5C4EE0A8A6BF8B61807A8BE8C19CB95CEB226A40C8F4",
            hashlib.sha256(ART_ORIGINAL.read_bytes()).hexdigest().upper(),
        )
        self.assertEqual(
            "242E0C5F105BC8DED5BF54EB331E710DE07369BB816C2D3833DC3609061DA594",
            hashlib.sha256(ART.read_bytes()).hexdigest().upper(),
        )
        self.assertTrue(ART.is_file())
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((750, 547), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
