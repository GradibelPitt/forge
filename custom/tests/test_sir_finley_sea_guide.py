import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "blue" / "海中向导芬利爵士.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-4039e035-d06b-40e0-93d5-ed54cec97886.png"
)
ART = ROOT / "cards" / "pictures" / "PH01" / "海中向导芬利爵士.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = (
    "当芬利进战场时，放逐你的手牌，每以此法放逐一张牌，便将牌库底的一张牌置入手中，"
    "然后将所有被芬利放逐的牌以任意顺序放回牌库底。"
)
ENGLISH_ORACLE = (
    "When CARDNAME enters, exile your hand. For each card exiled this way, put a card "
    "from the bottom of your library into your hand, then "
    "put all cards exiled with CARDNAME on the bottom of your library in any order."
)
SOURCE_ART_SHA256 = "01A9A1455A110A872CDEC5F1A461A64A8367A4992D898521CC50AF760F532C57"


class SirFinleySeaGuideContractTest(unittest.TestCase):
    def test_etb_exchanges_entire_hand_with_library_bottom(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:海中向导芬利爵士", lines)
        self.assertIn("ManaCost:U", lines)
        self.assertIn("Types:Legendary Creature Murloc Explorer", lines)
        self.assertIn("PT:1/3", lines)

        trigger = next(
            line for line in lines if line.startswith("T:Mode$ ChangesZone")
        )
        self.assertIn("Origin$ Any", trigger)
        self.assertIn("Destination$ Battlefield", trigger)
        self.assertIn("ValidCard$ Card.Self", trigger)
        self.assertIn("TriggerZones$ Battlefield", trigger)
        self.assertIn("Execute$ TrigFinley", trigger)
        self.assertIn(f"TriggerDescription$ {ENGLISH_ORACLE}", trigger)

        exile = next(line for line in lines if line.startswith("SVar:TrigFinley:"))
        self.assertIn("DB$ ChangeZoneAll", exile)
        self.assertIn("Origin$ Hand", exile)
        self.assertIn("Destination$ Exile", exile)
        self.assertIn("ChangeType$ Card.YouOwn", exile)
        self.assertNotIn("ChangeNum$", exile)
        self.assertIn("RememberChanged$ True", exile)
        self.assertIn("SubAbility$ DBDig", exile)

        dig = next(line for line in lines if line.startswith("SVar:DBDig:"))
        self.assertIn("DB$ Dig", dig)
        self.assertIn("DigNum$ X", dig)
        self.assertIn("ChangeNum$ All", dig)
        self.assertIn("ChangeValid$ Card", dig)
        self.assertIn("DestinationZone$ Hand", dig)
        self.assertIn("FromBottom$ True", dig)
        self.assertIn("SubAbility$ DBReplace", dig)

        replace = next(line for line in lines if line.startswith("SVar:DBReplace:"))
        self.assertIn("DB$ ChangeZoneAll", replace)
        self.assertIn("Origin$ Exile", replace)
        self.assertIn("Destination$ Library", replace)
        self.assertIn("LibraryPosition$ -1", replace)
        self.assertIn("ChangeType$ Card.IsRemembered", replace)
        self.assertNotIn("RandomOrder$ True", replace)
        self.assertIn("SubAbility$ DBCleanup", replace)

        self.assertFalse(any(line.startswith("SVar:XFetch:") for line in lines))
        self.assertIn("SVar:X:Remembered$Amount", lines)
        self.assertIn("SVar:DBCleanup:DB$ Cleanup | ClearRemembered$ True", lines)
        self.assertIn(f"Oracle:{ORACLE}", lines)

    def test_registration_localization_art_and_documentation(self):
        self.assertIn(
            "108 M 海中向导芬利爵士 @Custom",
            EDITION.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            f"海中向导芬利爵士|海中向导芬利爵士|传奇生物～鱼人／探险家|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 海中向导芬利爵士 | `{U}`，1/3 传奇生物～鱼人／探险家 | "
            "`cards/blue/海中向导芬利爵士.txt` | 108 |",
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
            self.assertEqual((1024, 1429), image.size)

        self.assertTrue(ART.is_file())
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((1024, 747), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
