from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "black" / "markzul_imp.txt"
PICTURE = ROOT / "cards" / "pictures" / "PH01" / "马克扎尔的小鬼.full.jpg"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
INSTALLER = ROOT / "tools" / "install_to_forge.ps1"


class MarkzulImpContractTest(unittest.TestCase):
    def test_card_script_has_the_requested_discard_trigger(self):
        script = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:马克扎尔的小鬼", script)
        self.assertIn("ManaCost:B B", script)
        self.assertNotIn("ManaCost:1 B B B", script)
        self.assertIn("Types:Creature Demon", script)
        self.assertIn("PT:1/3", script)
        self.assertIn(
            "T:Mode$ Discarded | ValidCard$ Card.YouCtrl | TriggerZones$ Battlefield | Execute$ TrigDraw",
            script,
        )
        self.assertIn("SVar:TrigDraw:DB$ Draw | Defined$ You | NumCards$ 1", script)
        self.assertIn("Oracle:Whenever you discard a card, draw a card.", script)

    def test_edition_and_picture_installation_are_declared(self):
        self.assertIn("7 C 马克扎尔的小鬼", EDITION.read_text(encoding="utf-8"))
        self.assertTrue(PICTURE.is_file())
        self.assertIn("$WorkspacePictures", INSTALLER.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
