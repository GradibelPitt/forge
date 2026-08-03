import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "multicolor" / "援军光环.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "援军光环.artcrop.jpg"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "1920px-Reinforcement_Aura_full.jpg"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class ReinforcementAuraContractTest(unittest.TestCase):
    def test_card_has_vanishing_and_the_end_step_top_ten_selection(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:援军光环", text)
        self.assertIn("ManaCost:1 W W", text)
        self.assertIn("Types:Enchantment", text)
        self.assertIn("K:Vanishing:3", text)
        self.assertIn(
            "T:Mode$ Phase | Phase$ End of Turn | ValidPlayer$ You | TriggerZones$ Battlefield | "
            "Execute$ TrigDig | TriggerDescription$ At the beginning of your end step, look at the top ten cards of your "
            "library. You may put a creature card with mana value 2 or less from among them onto the battlefield. "
            "Put the rest on the bottom of your library in any order.",
            text,
        )
        self.assertIn(
            "SVar:TrigDig:DB$ Dig | DigNum$ 10 | ChangeNum$ 1 | Optional$ True | "
            "ChangeValid$ Creature.cmcLE2 | DestinationZone$ Battlefield",
            text,
        )

    def test_card_uses_the_standard_chinese_wording(self):
        oracle = (
            "Oracle:消逝3\\n在你的结束步骤开始时，检视你牌库顶的十张牌。你可以将其中一张法术力值等于或小于2的生物牌放进战场。"
            "将其余的牌以任意顺序置于你牌库底。"
        )
        self.assertIn(oracle, CARD.read_text(encoding="utf-8"))

        expected = (
            "援军光环|援军光环|结界|消逝3\\n在你的结束步骤开始时，检视你牌库顶的十张牌。你可以将其中一张"
            "法术力值等于或小于2的生物牌放进战场。将其余的牌以任意顺序置于你牌库底。"
        )
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())

    def test_card_is_registered_with_standard_crop_art(self):
        edition = EDITION.read_text(encoding="utf-8")

        self.assertIn("22 R 援军光环 @Custom", edition)
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())

    def test_art_crop_preserves_the_full_scene(self):
        from PIL import Image

        with Image.open(ART) as image:
            self.assertEqual((1920, 1401), image.size)
            self.assertEqual("RGB", image.mode)


if __name__ == "__main__":
    unittest.main()
