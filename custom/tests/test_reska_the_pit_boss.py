import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "multicolor" / "矿坑老板雷斯卡.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "矿坑老板雷斯卡.artcrop.jpg"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "800px-Reska,_the_Pit_Boss_full.jpg"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class ReskaThePitBossContractTest(unittest.TestCase):
    def test_card_has_the_requested_legendary_undead_profile_and_haste(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:矿坑老板雷斯卡", text)
        self.assertIn("ManaCost:6 B B U U", text)
        self.assertIn("Types:Legendary Creature Zombie", text)
        self.assertIn("PT:6/3", text)
        self.assertIn("K:Haste", text)

    def test_card_reduces_one_chosen_blue_or_black_cost_for_each_creature_card_in_its_controllers_graveyard(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "S:Mode$ ReduceCost | ValidCard$ Card.Self | Type$ Spell | Amount$ X | "
            "ColorChoice$ U B | EffectZone$ All",
            text,
        )
        self.assertIn("SVar:X:Count$ValidGraveyard Creature.YouOwn", text)

    def test_entering_a_graveyard_from_any_zone_gains_control_of_a_target_nonland_permanent(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "T:Mode$ ChangesZone | Origin$ Any | Destination$ Graveyard | ValidCard$ Card.Self | "
            "Execute$ TrigGainControl",
            text,
        )
        self.assertIn(
            "SVar:TrigGainControl:DB$ GainControl | ValidTgts$ Permanent.nonLand",
            text,
        )

    def test_card_is_registered_with_backup_and_dynamic_art(self):
        self.assertIn("34 M 矿坑老板雷斯卡 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())

    def test_zh_cn_display_text_matches_the_requested_description(self):
        expected = "矿坑老板雷斯卡|矿坑老板雷斯卡|传奇生物～僵尸|敏捷\\n你坟墓场中的每张生物牌可以为此咒语支付{U}或{B}。\\n当矿坑老板雷斯卡从任何区域进入坟墓场时，获得目标非地永久物的操控权。"
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())


if __name__ == "__main__":
    unittest.main()
