import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "black" / "末日预言者.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Doomsayer_照片-1.jpg"
ART = ROOT / "cards" / "pictures" / "PH01" / "末日预言者.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = "在你的维持开始时，消灭所有生物。它们不能重生。"
ENGLISH_ORACLE = (
    "At the beginning of your upkeep, destroy all creatures. "
    "They can't be regenerated."
)
SOURCE_ART_SHA256 = "12045115F7E345A069F9511CA809773065C9BF8666D563272174148C2181B22F"


class DoomsayerContractTest(unittest.TestCase):
    def test_upkeep_trigger_destroys_every_creature_without_regeneration(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:末日预言者", lines)
        self.assertIn("ManaCost:B B", lines)
        self.assertIn("Types:Creature Warlock", lines)
        self.assertIn("PT:0/5", lines)

        trigger = next(line for line in lines if line.startswith("T:Mode$ Phase"))
        self.assertIn("Phase$ Upkeep", trigger)
        self.assertIn("ValidPlayer$ You", trigger)
        self.assertIn("TriggerZones$ Battlefield", trigger)
        self.assertIn("Execute$ TrigDestroyAll", trigger)
        self.assertIn(f"TriggerDescription$ {ENGLISH_ORACLE}", trigger)

        destroy_all = next(
            line for line in lines if line.startswith("SVar:TrigDestroyAll:")
        )
        self.assertIn("DB$ DestroyAll", destroy_all)
        self.assertIn("ValidCards$ Creature", destroy_all)
        self.assertIn("NoRegen$ True", destroy_all)
        self.assertNotIn("YouCtrl", destroy_all)
        self.assertNotIn("ValidTgts$", destroy_all)
        self.assertIn(f"Oracle:{ORACLE}", lines)

    def test_registration_localization_art_and_documentation(self):
        self.assertIn(
            "107 R 末日预言者 @Custom",
            EDITION.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            f"末日预言者|末日预言者|生物～术士|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 末日预言者 | `{B}{B}`，0/5 生物～术士 | "
            "`cards/black/末日预言者.txt` | 107 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        self.assertTrue(ART_BACKUP.is_file())
        self.assertEqual(
            SOURCE_ART_SHA256,
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        with Image.open(ART_BACKUP) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((890, 800), image.size)

        self.assertTrue(ART.is_file())
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((890, 650), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
