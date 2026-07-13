import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "multicolor" / "灵魂之火.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "灵魂之火.artcrop.jpg"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "1024px-Soulfire_full.jpg"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class SoulfireContractTest(unittest.TestCase):
    def test_card_uses_a_single_black_red_phyrexian_mana_symbol(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:灵魂之火", text)
        self.assertIn("ManaCost:BRP", text)
        self.assertIn("Colors:red black", text)
        self.assertIn("Types:Sorcery", text)

    def test_card_deals_three_damage_to_any_target_then_discards_one_card(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "A:SP$ DealDamage | ValidTgts$ Any | NumDmg$ 3 | SubAbility$ DBDiscard",
            text,
        )
        self.assertIn(
            "SVar:DBDiscard:DB$ Discard | Defined$ You | NumCards$ 1",
            text,
        )

    def test_card_is_registered_with_backup_and_dynamic_art(self):
        self.assertIn("32 R 灵魂之火 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())

    def test_zh_cn_display_text_matches_the_requested_description(self):
        expected = "灵魂之火|灵魂之火|法术|灵魂之火对任一目标造成3点伤害，弃一张牌。"
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())


if __name__ == "__main__":
    unittest.main()
