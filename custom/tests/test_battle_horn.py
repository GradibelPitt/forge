import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "white" / "战斗号角.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "战斗号角.artcrop.jpg"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Call_to_Arms_full.jpg"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class BattleHornContractTest(unittest.TestCase):
    def test_card_uses_the_exact_requested_search_effect(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:战斗号角", text)
        self.assertIn("ManaCost:1 W U G", text)
        self.assertIn("Types:Sorcery", text)
        self.assertIn(
            "A:SP$ ChangeZone | Origin$ Library | Destination$ Battlefield | "
            "ChangeType$ Creature.cmcLE2 | ChangeNum$ 3 | ShuffleNonMandatory$ True | "
            "SpellDescription$ 从你的牌堆中搜寻至多三个法术力消耗小于等于2的生物，将它们以任意顺序放进战场，然后洗牌",
            text,
        )
        self.assertIn(
            "Oracle:从你的牌堆中搜寻至多三个法术力消耗小于等于2的生物，将它们以任意顺序放进战场，然后洗牌",
            text,
        )

    def test_card_is_registered_with_standard_crop_art(self):
        edition = EDITION.read_text(encoding="utf-8")

        self.assertIn("18 R 战斗号角 @Custom", edition)
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())

    def test_standard_art_crop_preserves_the_requested_landscape_format(self):
        from PIL import Image

        with Image.open(ART) as image:
            self.assertEqual((1920, 1401), image.size)
            self.assertEqual("RGB", image.mode)

    def test_zh_cn_display_text_matches_the_user_provided_description(self):
        expected = (
            "战斗号角|战斗号角|法术|"
            "从你的牌堆中搜寻至多三个法术力消耗小于等于2的生物，将它们以任意顺序放进战场，然后洗牌"
        )

        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())


if __name__ == "__main__":
    unittest.main()
