from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "blue" / "裂魂者阿扎莉娜.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"


class SoulrenderAzzalinaContractTest(unittest.TestCase):
    def test_card_metadata_and_deck_minimum(self):
        script = CARD.read_text(encoding="utf-8")
        self.assertIn("Name:裂魂者阿扎莉娜", script)
        self.assertIn("ManaCost:4 U U U", script)
        self.assertIn("Types:Legendary Creature Human Cleric Warlock", script)
        self.assertIn("PT:7/7", script)
        self.assertIn("K:DeckMinimum:20", script)

    def test_new_game_copy_is_automatic_and_uses_opponents_starting_deck(self):
        script = CARD.read_text(encoding="utf-8")
        trigger = next(line for line in script.splitlines() if line.startswith("T:Mode$ NewGame |"))
        copy = next(line for line in script.splitlines() if line.startswith("SVar:CopyOpponentDeck:"))
        self.assertIn("TriggerZones$ Hand,Library", trigger)
        self.assertIn("Static$ True", trigger)
        self.assertIn("DefinedName$ RandomOpponentStartingDeckNonlands", copy)
        self.assertIn("Zone$ Library", copy)
        self.assertIn("Conjure$ True", copy)
        self.assertNotIn("Choose", copy)
        self.assertNotIn("Optional$", copy)

    def test_etb_draws_only_the_difference_to_seven(self):
        script = CARD.read_text(encoding="utf-8")
        self.assertIn("Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield", script)
        self.assertIn("NumCards$ CardsToSeven", script)
        self.assertIn("SVar:HandCount:Count$ValidHand Card.YouOwn", script)
        self.assertIn("SVar:CardsToSeven:Number$7/Minus.HandCount", script)

    def test_card_is_registered_as_custom_art(self):
        self.assertIn("68 M 裂魂者阿扎莉娜 @Custom", EDITION.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
