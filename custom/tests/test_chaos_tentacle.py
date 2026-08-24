import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "colorless" / "混乱触须.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "混乱触须.artcrop.jpg"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Chaos_Tentacle_original.jpg"
OLD_TOKEN = ROOT / "tokens" / "c_chaos_tentacle.txt"
OLD_TOKEN_ART = ROOT / "tokens" / "pictures" / "c_chaos_tentacle.jpg"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class ChaosTentacleContractTest(unittest.TestCase):
    def read_card(self):
        self.assertTrue(CARD.is_file(), f"missing real-card script: {CARD}")
        return CARD.read_text(encoding="utf-8")

    def test_card_is_a_one_mana_ph01_artifact(self):
        text = self.read_card()

        self.assertIn("Name:混乱触须", text)
        self.assertIn("ManaCost:1", text)
        self.assertIn("Types:Artifact", text)
        self.assertIn("40 C 混乱触须 @Custom", EDITION.read_text(encoding="utf-8"))

    def test_real_card_uses_the_requested_tap_and_sacrifice_cost(self):
        text = self.read_card()

        self.assertIn(
            "A:AB$ CardDiscover | Cost$ T Sac<1/CARDNAME> | Defined$ You | Source$ CardDatabase | ValidCards$ Sorcery.cmcEQX | OptionCount$ 3 | Destination$ Exile | RememberChosen$ True | SubAbility$ CastDiscoveredSpell",
            text,
        )

    def test_discovery_caps_graveyard_tentacle_count_at_ten_for_mana_value(self):
        text = self.read_card()

        self.assertIn(
            "SVar:X:Count$ValidGraveyard Card.named混乱触须+YouOwn/LimitMax.10",
            text,
        )
        self.assertNotIn("SVar:Y:", text)
        self.assertIn("Discover a sorcery card with mana value X, then cast it", text)
        self.assertIn("X is the number of Chaos Tentacle cards in your graveyard, up to a maximum of 10", text)
        self.assertNotIn("mana value X plus 1", text)

    def test_discovered_sorcery_is_cast_for_free_then_its_targets_are_randomized(self):
        text = self.read_card()

        self.assertIn(
            "SVar:CastDiscoveredSpell:DB$ Play | Defined$ Remembered | ValidSA$ Spell | ValidZone$ Exile | ZoneRegardless$ True | Controller$ You | WithoutManaCost$ True | Optional$ False | RememberPlayed$ True | SubAbility$ RandomizeTargets",
            text,
        )
        self.assertIn(
            "SVar:RandomizeTargets:DB$ ChangeTargets | Defined$ Remembered | RandomTarget$ True | SubAbility$ Cleanup",
            text,
        )
        self.assertIn("SVar:Cleanup:DB$ Cleanup | ClearRemembered$ True", text)

    def test_real_card_assets_replace_the_old_token_assets(self):
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())
        self.assertFalse(OLD_TOKEN.exists())
        self.assertFalse(OLD_TOKEN_ART.exists())

    def test_card_has_simplified_chinese_display_text(self):
        expected = (
            "混乱触须|混乱触须|神器|"
            "{T}，牺牲混乱触须：发现一张法术力值为X的法术牌，然后不支付其法术力费用并为其随机选择目标来施放之。"
            "X为你坟墓场中名为「混乱触须」的牌数量，且最高为10。"
        )
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())


if __name__ == "__main__":
    unittest.main()
