import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "blue" / "水晶学.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "水晶学.artcrop.jpg"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Crystology.jpg"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class CrystologyContractTest(unittest.TestCase):
    def test_card_searches_for_two_power_one_creature_cards(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:水晶学", text)
        self.assertIn("ManaCost:W U", text)
        self.assertIn("Types:Sorcery", text)
        self.assertIn(
            "A:SP$ ChangeZone | Origin$ Library | Destination$ Hand | ChangeType$ Creature.powerEQ1 | "
            "ChangeTypeDesc$ creature cards with power 1 | ChangeNum$ 2 | AtRandom$ True | ShuffleNonMandatory$ True | "
            "SpellDescription$ Choose up to two creature cards with power 1 at random from your library, put them into your hand, then shuffle.",
            text,
        )

    def test_card_uses_the_requested_chinese_wording(self):
        oracle = "Oracle:从你的牌库中随机选择至多两张力量为1的生物牌，将它们置于你手上，然后洗牌。"
        self.assertIn(oracle, CARD.read_text(encoding="utf-8"))

        expected = "水晶学|水晶学|法术|从你的牌库中随机选择至多两张力量为1的生物牌，将它们置于你手上，然后洗牌。"
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())

    def test_card_is_registered_with_standard_crop_art(self):
        self.assertIn("24 C 水晶学 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())

    def test_art_crop_is_rgb_and_landscape(self):
        from PIL import Image

        with Image.open(ART) as image:
            self.assertEqual("RGB", image.mode)
            self.assertGreater(image.width, image.height)
            self.assertAlmostEqual(image.width / image.height, 1.37, delta=0.02)


if __name__ == "__main__":
    unittest.main()
