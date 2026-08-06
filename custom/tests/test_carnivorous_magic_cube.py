import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "black" / "食肉魔块.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-3abb60f5-62f1-4f63-84f9-99e10b4634ce.png"
)
ART = ROOT / "cards" / "pictures" / "PH01" / "食肉魔块.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = (
    "当食肉魔块进战场，消灭目标由你控制的生物。"
    "当食肉魔块死去时，化生两个以此法被消灭的生物并放进战场。"
)


class CarnivorousMagicCubeContractTest(unittest.TestCase):
    def test_characteristics_and_targeted_etb_destruction(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:食肉魔块", lines)
        self.assertNotIn("Name:食肉格块", lines)
        self.assertIn("ManaCost:3 B B", lines)
        self.assertIn("Types:Creature Ooze", lines)
        self.assertIn("PT:4/6", lines)
        trigger = next(line for line in lines if line.startswith("T:Mode$ ChangesZone"))
        self.assertIn("Origin$ Any", trigger)
        self.assertIn("Destination$ Battlefield", trigger)
        self.assertIn("ValidCard$ Card.Self", trigger)
        self.assertIn("Execute$ TrigDestroy", trigger)
        destroy = next(line for line in lines if line.startswith("SVar:TrigDestroy:"))
        self.assertIn("DB$ Destroy", destroy)
        self.assertIn("ValidTgts$ Creature.YouCtrl", destroy)
        self.assertIn("RememberDestroyed$ True", destroy)

    def test_death_conjures_two_duplicates_of_the_destroyed_creature(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        triggers = [line for line in lines if line.startswith("T:Mode$ ChangesZone")]
        death = next(line for line in triggers if "Origin$ Battlefield" in line)
        self.assertIn("Destination$ Graveyard", death)
        self.assertIn("ValidCard$ Card.Self", death)
        self.assertIn("Execute$ TrigConjure", death)
        conjure = next(line for line in lines if line.startswith("SVar:TrigConjure:"))
        self.assertIn("DB$ MakeCard", conjure)
        self.assertIn("Conjure$ True", conjure)
        self.assertIn("DefinedName$ Remembered", conjure)
        self.assertIn("Amount$ 2", conjure)
        self.assertIn("Zone$ Battlefield", conjure)
        self.assertIn(f"Oracle:{ORACLE}", lines)

    def test_registration_localization_art_and_documentation(self):
        self.assertIn("93 R 食肉魔块 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertIn(
            f"食肉魔块|食肉魔块|生物～流浆|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 食肉魔块 | `{3}{B}{B}`，4/6 生物～流浆 | "
            "`cards/black/食肉魔块.txt` | 93 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        self.assertTrue(ART_BACKUP.is_file())
        self.assertEqual(
            "29942BB10BD7D7F65625BFD3A5582EC37FF5424C4D95F9A9CF309FE5C5598DF2",
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        self.assertTrue(ART.is_file())
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((400, 292), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
