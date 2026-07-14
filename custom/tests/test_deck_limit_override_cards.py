from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
OVERRIDE = ROOT / "cards" / "colorless" / "test_解除构筑限制.txt"
PATCHES = ROOT / "cards" / "red" / "海盗帕奇斯.txt"
TUSK = ROOT / "cards" / "red" / "突牙.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
TEST_EDITION = ROOT / "editions" / "Test_Set.txt"


class DeckLimitOverrideCardsTest(unittest.TestCase):
    def test_override_card_contract(self):
        script = OVERRIDE.read_text(encoding="utf-8")

        self.assertIn("Name:test_解除构筑限制", script)
        self.assertIn("ManaCost:0", script)
        self.assertIn("Types:Artifact", script)
        self.assertIn("K:IgnoreDeckLimits", script)

    def test_patches_contract(self):
        script = PATCHES.read_text(encoding="utf-8")

        for value in (
            "Name:海盗帕奇斯",
            "ManaCost:R",
            "Types:Legendary Creature Pirate Demon",
            "PT:1/1",
            "K:Haste",
            "K:DeckLimit:1:Your deck can have no more than one card named CARDNAME.",
            "TriggerZones$ Hand,Library",
            "ValidCard$ Pirate.YouCtrl",
            "DB$ Branch",
            "BranchConditionSVarCompare$ GE1",
            "DB$ ChangeZoneAll",
            "Count$ValidHand,Library Card.named海盗帕奇斯+YouOwn",
        ):
            self.assertIn(value, script)
        self.assertNotIn("Shuffle$ True", script)
        self.assertNotIn(
            "Count$ValidHand Card.named海盗帕奇斯+YouOwn/Plus.Count$ValidLibrary Card.named海盗帕奇斯+YouOwn",
            script,
        )
        self.assertNotIn("/Plus.Count$ValidLibrary", script)

    def test_tusk_contract(self):
        script = TUSK.read_text(encoding="utf-8")

        for value in (
            "Name:突牙",
            "ManaCost:2 R R",
            "Types:Legendary Creature Beast",
            "PT:3/3",
            "K:Haste",
            "K:DeckLimit:1:Your deck can have no more than one card named CARDNAME.",
            "K:Boarding:3",
        ):
            self.assertIn(value, script)
        self.assertNotIn("Mode$ DamageDoneOnce", script)
        self.assertNotIn("SVar:FriendlyCharactersDamaged", script)
        self.assertNotIn("Shuffle$ True", script)

    def test_cards_are_listed_in_the_custom_edition(self):
        edition = EDITION.read_text(encoding="utf-8")
        test_edition = TEST_EDITION.read_text(encoding="utf-8")

        self.assertIn("7 C test_解除构筑限制", test_edition)
        self.assertNotIn("test_解除构筑限制", edition)
        self.assertIn("10 M 海盗帕奇斯", edition)
        self.assertIn("11 M 突牙", edition)


if __name__ == "__main__":
    unittest.main()
