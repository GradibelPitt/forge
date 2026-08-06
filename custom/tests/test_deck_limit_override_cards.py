import hashlib
from pathlib import Path
import unittest

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
OVERRIDE = ROOT / "cards" / "colorless" / "test_解除构筑限制.txt"
PATCHES = ROOT / "cards" / "red" / "海盗帕奇斯.txt"
TUSK = ROOT / "cards" / "red" / "突牙.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
TEST_EDITION = ROOT / "editions" / "Test_Set.txt"
TUSK_ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-c5710624-095a-4f7a-aade-073501fa90b5.png"
)
TUSK_ART = ROOT / "cards" / "pictures" / "PH01" / "突牙.artcrop.jpg"


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
        self.assertIn("11 M 突牙 @Custom", edition)

    def test_tusk_original_art_is_preserved_and_cropped_for_dynamic_frame(self):
        self.assertTrue(TUSK_ART_BACKUP.is_file())
        self.assertEqual(
            "3ACCAF9531BC69A12409E3C63B7FA553AB5BF81701E44C27C3A056DC0C51BC75",
            hashlib.sha256(TUSK_ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        with Image.open(TUSK_ART_BACKUP) as image:
            self.assertEqual("PNG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((1024, 1365), image.size)

        self.assertTrue(TUSK_ART.is_file())
        self.assertEqual(
            "E9D6CBB170BA47C66C8F8EF2A6E6CC4F9FD9B832C37B0FEA355D0751C8E62AEB",
            hashlib.sha256(TUSK_ART.read_bytes()).hexdigest().upper(),
        )
        with Image.open(TUSK_ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((1024, 748), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
