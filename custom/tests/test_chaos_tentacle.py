import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
TOKEN = ROOT / "tokens" / "c_chaos_tentacle.txt"
ART = ROOT / "tokens" / "pictures" / "c_chaos_tentacle.jpg"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Chaos_Tentacle_original.jpg"


class ChaosTentacleContractTest(unittest.TestCase):
    def test_token_uses_the_requested_tap_and_sacrifice_cost(self):
        text = TOKEN.read_text(encoding="utf-8")

        self.assertIn("Name:混乱触须", text)
        self.assertIn("Types:Artifact", text)
        self.assertIn(
            "A:AB$ CardDiscover | Cost$ T Sac<1/CARDNAME> | Defined$ You | Source$ CardDatabase | ValidCards$ Sorcery.cmcEQY | OptionCount$ 3 | Destination$ Exile | RememberChosen$ True | SubAbility$ CastDiscoveredSpell",
            text,
        )

    def test_discovery_uses_graveyard_tentacles_plus_one_for_mana_value(self):
        text = TOKEN.read_text(encoding="utf-8")

        self.assertIn("SVar:X:Count$ValidGraveyard Card.named混乱触须+YouOwn", text)
        self.assertIn("SVar:Y:SVar$X/Plus.1", text)

    def test_discovered_sorcery_is_cast_for_free_then_its_targets_are_randomized(self):
        text = TOKEN.read_text(encoding="utf-8")

        self.assertIn(
            "SVar:CastDiscoveredSpell:DB$ Play | Defined$ Remembered | ValidSA$ Spell | ValidZone$ Exile | ZoneRegardless$ True | Controller$ You | WithoutManaCost$ True | Optional$ False | RememberPlayed$ True | SubAbility$ RandomizeTargets",
            text,
        )
        self.assertIn(
            "SVar:RandomizeTargets:DB$ ChangeTargets | Defined$ Remembered | RandomTarget$ True | SubAbility$ Cleanup",
            text,
        )
        self.assertIn("SVar:Cleanup:DB$ Cleanup | ClearRemembered$ True", text)

    def test_artwork_is_backed_up_and_saved_under_the_token_script_name(self):
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())


if __name__ == "__main__":
    unittest.main()
