import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "blue" / "冰霜新星.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-d0a083bc-05ae-4399-aa45-da7ed9642c18.png"
)
ART = ROOT / "cards" / "pictures" / "PH01" / "冰霜新星.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = "横置所有不由你操控的生物。它们于其操控者的下一个重置步骤中不能重置。"
SOURCE_ART_SHA256 = "95216C283A558CF3B0CB57871621EA1180915BF00CEB49ACA960B7F503D5D529"


class FrostNovaContractTest(unittest.TestCase):
    def test_taps_each_creature_not_controlled_by_you_and_locks_next_untap(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:冰霜新星", lines)
        self.assertIn("ManaCost:1 U U", lines)
        self.assertIn("Types:Instant", lines)

        spell = next(line for line in lines if line.startswith("A:SP$ TapAll"))
        self.assertIn("ValidCards$ Creature.OppCtrl", spell)
        self.assertIn("SubAbility$ DBNoUntap", spell)
        self.assertIn(f"SpellDescription$ {ORACLE}", spell)
        self.assertNotIn("ValidTgts$", spell)

        no_untap = next(
            line for line in lines if line.startswith("SVar:DBNoUntap:")
        )
        self.assertIn("DB$ PumpAll", no_untap)
        self.assertIn("ValidCards$ Creature.OppCtrl", no_untap)
        self.assertIn(
            "KW$ HIDDEN This card doesn't untap during your next untap step.",
            no_untap,
        )
        self.assertIn("IsCurse$ True", no_untap)
        self.assertIn("Duration$ Permanent", no_untap)
        self.assertIn(f"Oracle:{ORACLE}", lines)

    def test_registration_localization_art_and_documentation(self):
        self.assertIn(
            "112 R 冰霜新星 @Custom",
            EDITION.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            f"冰霜新星|冰霜新星|瞬间|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 冰霜新星 | `{1}{U}{U}` 瞬间 | `cards/blue/冰霜新星.txt` | 112 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        self.assertTrue(ART_BACKUP.is_file())
        self.assertEqual(
            SOURCE_ART_SHA256,
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        with Image.open(ART_BACKUP) as image:
            self.assertEqual("PNG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((512, 512), image.size)

        self.assertTrue(ART.is_file())
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((462, 337), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
