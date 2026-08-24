import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "colorless" / "gigantic_spright.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "Gigantic Spright.artcrop.jpg"
HIDDEN = ROOT / "hidden" / "gigantic_spright"
HIDDEN_CARD = HIDDEN / "gigantic_spright.txt"
HIDDEN_ART = HIDDEN / "Gigantic Spright.artcrop.jpg"
INSTALLER = ROOT / "tools" / "install_to_forge.ps1"
DEPLOYED_CARDS = Path.home() / "AppData" / "Roaming" / "Forge" / "custom" / "cards" / "colorless"


class GiganticSprightHiddenCardTest(unittest.TestCase):
    def test_card_script_and_art_are_preserved_outside_scanned_directories(self):
        self.assertFalse(CARD.exists())
        self.assertFalse(ART.exists())
        self.assertTrue(HIDDEN_CARD.is_file())
        self.assertTrue(HIDDEN_ART.is_file())
        self.assertIn("Name:Gigantic Spright", HIDDEN_CARD.read_text(encoding="utf-8"))

    def test_hidden_card_is_not_registered_or_left_in_the_local_profile(self):
        edition = EDITION.read_text(encoding="utf-8")
        self.assertNotIn("Gigantic Spright", edition)
        self.assertIn(
            '"colorless\\gigantic_spright.txt"',
            INSTALLER.read_text(encoding="utf-8"),
        )
        self.assertFalse((DEPLOYED_CARDS / "Gigantic Spright.txt").exists())
        self.assertFalse((DEPLOYED_CARDS / "gigantic_spright.txt").exists())


if __name__ == "__main__":
    unittest.main()
