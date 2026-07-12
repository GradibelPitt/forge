import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "multicolor" / "灵魂弹幕.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "灵魂弹幕.artcrop.jpg"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Soul_Barrage_full.jpg"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class SoulBarrageContractTest(unittest.TestCase):
    def test_card_is_a_red_black_sorcery_with_madness_zero(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:灵魂弹幕", text)
        self.assertIn("ManaCost:3 B R", text)
        self.assertIn("Types:Sorcery", text)
        self.assertIn("K:Madness:0", text)

    def test_card_deals_one_damage_to_the_same_any_target_six_times(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "A:SP$ Repeat | ValidTgts$ Any | RepeatSubAbility$ DBDamage | MaxRepeat$ 6",
            text,
        )
        self.assertIn("SVar:DBDamage:DB$ DealDamage | Defined$ Targeted | NumDmg$ 1", text)

    def test_card_is_registered_with_backup_and_dynamic_art(self):
        self.assertIn("35 R 灵魂弹幕 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())

    def test_zh_cn_display_text_matches_the_requested_description(self):
        expected = r"灵魂弹幕|灵魂弹幕|法术|灵魂弹幕对任意目标造成1点伤害，重复6次。\n疯魔{0}"
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())


if __name__ == "__main__":
    unittest.main()
