import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "blue" / "心灵视界.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "心灵视界.artcrop.jpg"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Art_CS2_003.png"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class MindVisionContractTest(unittest.TestCase):
    def test_characteristics_registration_and_chinese_wording(self):
        text = CARD.read_text(encoding="utf-8")
        self.assertIn("Name:心灵视界", text)
        self.assertIn("ManaCost:U", text)
        self.assertIn("Types:Sorcery", text)
        self.assertIn("39 C 心灵视界 @Custom", EDITION.read_text(encoding="utf-8"))
        expected = (
            "心灵视界|心灵视界|法术|检视目标对手的手牌，从中选择一张牌。"
            "化生一张以此法选择的牌名的复制并置于你的手上。它减少{1}来施放。"
        )
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())

    def test_inspects_target_opponent_and_allows_any_card_in_hand(self):
        text = CARD.read_text(encoding="utf-8")
        reveal = next(line for line in text.splitlines() if line.startswith("A:SP$ RevealHand"))
        choose = next(line for line in text.splitlines() if line.startswith("SVar:DBChooseCard:"))
        self.assertIn("ValidTgts$ Opponent", reveal)
        self.assertIn("Look$ True", reveal)
        self.assertIn("RememberRevealed$ True", reveal)
        self.assertIn("SubAbility$ DBChooseCard", reveal)
        self.assertIn("ChoiceZone$ Hand", choose)
        self.assertIn("Choices$ Card.IsRemembered", choose)
        self.assertIn("Mandatory$ True", choose)
        self.assertNotIn("nonLand", choose)
        self.assertNotIn("Creature", choose)

    def test_conjures_fresh_printed_copy_with_perpetual_cost_reduction(self):
        text = CARD.read_text(encoding="utf-8")
        conjure = next(line for line in text.splitlines() if line.startswith("SVar:DBConjure:"))
        animate = next(line for line in text.splitlines() if line.startswith("SVar:DBAnimate:"))
        reduce_cost = next(line for line in text.splitlines() if line.startswith("SVar:ReduceCost:"))
        cleanup = next(line for line in text.splitlines() if line.startswith("SVar:DBCleanup:"))
        self.assertIn("DB$ MakeCard", conjure)
        self.assertIn("Conjure$ True", conjure)
        self.assertIn("DefinedName$ ChosenCard", conjure)
        self.assertIn("Zone$ Hand", conjure)
        self.assertIn("RememberMade$ True", conjure)
        self.assertIn("Defined$ Remembered", animate)
        self.assertIn("staticAbilities$ ReduceCost", animate)
        self.assertIn("Duration$ Perpetual", animate)
        self.assertIn("ValidCard$ Card.Self", reduce_cost)
        self.assertIn("Type$ Spell", reduce_cost)
        self.assertIn("Amount$ 1", reduce_cost)
        self.assertIn("EffectZone$ All", reduce_cost)
        self.assertIn("ClearRemembered$ True", cleanup)
        self.assertIn("ClearChosenCard$ True", cleanup)

    def test_original_and_dynamic_art_are_preserved(self):
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((512, 374), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.02)


if __name__ == "__main__":
    unittest.main()
