import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "multicolor" / "暗影收割者安度因.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "暗影收割者安度因.artcrop.jpg"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-0a9b317b-89f9-4e02-a2ad-fd23b1585f00.png"
)
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class ShadowreaperAnduinContractTest(unittest.TestCase):
    def read_card(self):
        self.assertTrue(CARD.is_file(), CARD)
        return CARD.read_text(encoding="utf-8")

    def test_card_matches_the_requested_planeswalker_characteristics(self):
        text = self.read_card()

        self.assertIn("Name:暗影收割者安度因", text)
        self.assertIn("ManaCost:3 W B", text)
        self.assertIn("Types:Legendary Planeswalker Anduin", text)
        self.assertIn("Loyalty:4", text)

    def test_each_spell_cast_grants_one_additional_anduin_loyalty_activation(self):
        text = self.read_card()

        self.assertIn(
            "T:Mode$ SpellCast | ValidCard$ Card | ValidActivatingPlayer$ You | "
            "TriggerZones$ Battlefield | Execute$ TrigLoyalty",
            text,
        )
        self.assertIn(
            "SVar:TrigLoyalty:DB$ Effect | StaticAbilities$ LoyaltyAbs | "
            "RememberObjects$ Self | ExileOnMoved$ Battlefield",
            text,
        )
        self.assertIn(
            "SVar:LoyaltyAbs:Mode$ NumLoyaltyAct | ValidCard$ Card.EffectSource | "
            "Additional$ 1 | Description$",
            text,
        )
        self.assertNotIn("OnlySourceAbs$ True", text)

    def test_zero_loyalty_ability_deals_two_damage_to_any_target(self):
        text = self.read_card()

        self.assertIn(
            "A:AB$ DealDamage | Cost$ AddCounter<0/LOYALTY> | "
            "Planeswalker$ True | ValidTgts$ Any | NumDmg$ 2",
            text,
        )

    def test_minus_one_destroys_a_creature_with_power_four_or_greater(self):
        text = self.read_card()

        self.assertIn(
            "A:AB$ Destroy | Cost$ SubCounter<1/LOYALTY> | Planeswalker$ True | "
            "ValidTgts$ Creature.powerGE4",
            text,
        )

    def test_card_is_registered_with_standard_crop_art(self):
        self.assertIn("56 M 暗影收割者安度因 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertTrue(ART_BACKUP.is_file(), ART_BACKUP)
        self.assertTrue(ART.is_file(), ART)

    def test_standard_art_crop_is_landscape_rgb_jpeg(self):
        from PIL import Image

        self.assertTrue(ART.is_file(), ART)
        with Image.open(ART) as image:
            self.assertEqual((1366, 998), image.size)
            self.assertEqual("RGB", image.mode)
            self.assertAlmostEqual(1.37, image.width / image.height, places=2)

    def test_zh_cn_display_text_matches_the_card(self):
        expected = (
            "暗影收割者安度因|暗影收割者安度因|传奇鹏洛客～安度因|"
            "每当你施放咒语时，于本回合中，你可以起动安度因的一个忠诚异能一次，"
            "且能视同本回合中未起动过其忠诚异能地来起动之。\\n"
            "0：安度因对任意一个目标造成2点伤害。\\n"
            "-1：消灭目标力量等于或大于4的生物。\\n"
            "本牌可用作你的指挥官。"
        )
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())


if __name__ == "__main__":
    unittest.main()
