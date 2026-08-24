import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "multicolor" / "强能雷象.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Augmented_Elekk_original.jpg"
ART = ROOT / "cards" / "pictures" / "PH01" / "强能雷象.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = (
    "每当一张牌因强能雷象以外的效应进入牌库时，化生一张该牌的复制并置入该牌库，"
    "然后该牌库的拥有者将它洗牌。"
)


class AugmentedElekkContractTest(unittest.TestCase):
    def test_zone_entry_conjures_the_entering_card_for_its_owner(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:强能雷象", lines)
        self.assertIn("ManaCost:1 G U", lines)
        self.assertIn("Types:Creature Elephant", lines)
        self.assertIn("PT:3/4", lines)

        trigger = next(line for line in lines if line.startswith("T:Mode$ ChangesZone"))
        self.assertIn("Origin$ Any", trigger)
        self.assertIn("Destination$ Library", trigger)
        self.assertIn("ValidCard$ Card.!token", trigger)
        self.assertIn("TriggerZones$ Battlefield", trigger)
        self.assertIn("NotThisAbility$ True", trigger)
        self.assertIn("ValidCause$ SpellAbility.named强能雷象", trigger)
        self.assertIn("InvertValidCause$ True", trigger)
        self.assertIn("Execute$ TrigConjure", trigger)
        self.assertIn(f"TriggerDescription$ {ORACLE}", trigger)

        conjure = next(line for line in lines if line.startswith("SVar:TrigConjure:"))
        self.assertIn("DB$ MakeCard", conjure)
        self.assertIn("Defined$ TriggeredCardOwner", conjure)
        self.assertIn("DefinedName$ TriggeredCard", conjure)
        self.assertIn("Conjure$ True", conjure)
        self.assertIn("Zone$ Library", conjure)
        self.assertNotIn("Name$ 强能雷象", conjure)
        self.assertNotIn("LibraryPosition$", conjure)
        self.assertIn(f"Oracle:{ORACLE}", lines)

    def test_registration_localization_art_and_documentation(self):
        self.assertIn("101 U 强能雷象 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertIn(
            f"强能雷象|强能雷象|生物～象|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 强能雷象 | `{1}{G}{U}`，3/4 生物～象 | "
            "`cards/multicolor/强能雷象.txt` | 101 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        self.assertTrue(ART_BACKUP.is_file())
        self.assertEqual(
            "360E1E78B98CC11F47AC7904670AABC66DD07DC905B64D38EB1C2BC53424A222",
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
