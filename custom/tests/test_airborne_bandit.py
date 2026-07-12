import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "red" / "空中悍匪.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"


class AirborneBanditContractTest(unittest.TestCase):
    def test_card_uses_card_discover_for_pirate_creatures(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:空中悍匪", text)
        self.assertIn("ManaCost:R", text)
        self.assertIn("Types:Creature Pirate", text)
        self.assertIn("PT:1/2", text)
        self.assertIn("Mode$ ChangesZone", text)
        self.assertIn("Destination$ Battlefield", text)
        self.assertIn("ValidCard$ Card.Self", text)
        self.assertIn("DB$ CardDiscover", text)
        self.assertIn("Source$ CardDatabase", text)
        self.assertIn("ValidCards$ Creature.Pirate", text)
        self.assertIn("OptionCount$ 3", text)
        self.assertIn("Destination$ Hand", text)

    def test_card_is_registered_in_ph01(self):
        edition = EDITION.read_text(encoding="utf-8")
        self.assertIn("15 C 空中悍匪", edition)


if __name__ == "__main__":
    unittest.main()
