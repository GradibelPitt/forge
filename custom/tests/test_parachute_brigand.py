import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "red" / "空降歹徒.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "空降歹徒.artcrop.jpg"


class ParachuteBrigandContractTest(unittest.TestCase):
    def test_card_can_be_cast_free_from_hand_when_a_pirate_enters(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:空降歹徒", text)
        self.assertIn("ManaCost:2", text)
        self.assertIn("Types:Creature Pirate", text)
        self.assertIn("PT:2/2", text)
        self.assertIn("ValidCard$ Pirate.YouCtrl", text)
        self.assertIn("TriggerZones$ Hand", text)
        self.assertIn("DB$ Play", text)
        self.assertIn("Defined$ Self", text)
        self.assertIn("ValidZone$ Hand", text)
        self.assertIn("WithoutManaCost$ True", text)
        self.assertIn("Optional$ True", text)

    def test_card_is_registered_as_the_standard_crop_art(self):
        edition = EDITION.read_text(encoding="utf-8")

        self.assertIn("16 C 空降歹徒 @Custom", edition)

    def test_standard_art_crop_is_landscape(self):
        from PIL import Image

        with Image.open(ART) as image:
            self.assertEqual((1000, 730), image.size)
            self.assertEqual("RGB", image.mode)


if __name__ == "__main__":
    unittest.main()
