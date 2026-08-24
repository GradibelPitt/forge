import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "blue" / "神秘访客.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "codex-clipboard-388f27aa-5c99-4597-a30b-e1b922f5fbf2.png"
ART = ROOT / "cards" / "pictures" / "PH01" / "神秘访客.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = (
    "当神秘访客进战场时，你手牌和牌库中与任一对手的牌同名的牌永久获得调和"
    "（你可以用任意颜色的法术力支付此咒语的法术力费用）和「此咒语的施放费用减少{3}」。"
)


class MysteriousVisitorContractTest(unittest.TestCase):
    def test_card_and_permanent_opponent_name_harmony_rule(self):
        text = CARD.read_text(encoding="utf-8")
        self.assertIn("Name:神秘访客", text)
        self.assertIn("ManaCost:U U", text)
        self.assertIn("Types:Creature Human Cleric Warlock", text)
        self.assertIn("PT:2/3", text)
        self.assertIn(f"Oracle:{ORACLE}", text)
        self.assertIn(
            "T:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | "
            "ValidCard$ Card.Self | Execute$ GrantOpponentNameHarmony",
            text,
        )
        self.assertIn(
            "SVar:GrantOpponentNameHarmony:DB$ GrantSpellRule | Defined$ You | "
            "RuleKey$ MysteriousVisitor.OpponentNames | ValidCards$ Card | "
            "ValidSA$ Spell | NameSnapshot$ OpponentCards | Harmony$ True | "
            "HarmonyReduction$ 3 | Stacking$ True | Duration$ Permanent",
            text,
        )
        self.assertNotIn("ReduceGeneric$", text)
        self.assertNotIn("ManaConversion$", text)

    def test_registration_art_and_localization(self):
        self.assertIn("67 U 神秘访客 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())
        self.assertEqual(
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
            "3032148DEC51A7CF1BBB6AED09FD3C61860149E77B43D1345B448E99827D38F7",
        )
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.02)
        self.assertIn(
            f"神秘访客|神秘访客|生物～人类／牧师／邪术师|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )


if __name__ == "__main__":
    unittest.main()
