import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "black" / "狗头人图书管理员.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-2057b15d-f4c9-47af-90ac-986261f8aca0.png"
)
ART = ROOT / "cards" / "pictures" / "PH01" / "狗头人图书管理员.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = "当狗头人图书管理员进战场时，你可以支付两点生命并抓一张牌"


class KoboldLibrarianContractTest(unittest.TestCase):
    def test_characteristics_and_optional_life_payment_draw(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:狗头人图书管理员", lines)
        self.assertIn("ManaCost:B", lines)
        self.assertIn("Types:Creature Kobold", lines)
        self.assertIn("PT:2/1", lines)
        trigger = next(line for line in lines if line.startswith("T:Mode$ ChangesZone"))
        self.assertIn("Origin$ Any", trigger)
        self.assertIn("Destination$ Battlefield", trigger)
        self.assertIn("ValidCard$ Card.Self", trigger)
        self.assertIn("Execute$ TrigDraw", trigger)
        draw = next(line for line in lines if line.startswith("SVar:TrigDraw:"))
        self.assertIn("AB$ Draw", draw)
        self.assertIn("Cost$ PayLife<2>", draw)
        self.assertIn("Defined$ You", draw)
        self.assertIn("NumCards$ 1", draw)
        self.assertIn(f"Oracle:{ORACLE}", lines)

    def test_registration_localization_art_and_documentation(self):
        self.assertIn("91 C 狗头人图书管理员 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertIn(
            f"狗头人图书管理员|狗头人图书管理员|生物～狗头人|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 狗头人图书管理员 | `{B}`，2/1 生物～狗头人 | "
            "`cards/black/狗头人图书管理员.txt` | 91 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        self.assertTrue(ART_BACKUP.is_file())
        self.assertEqual(
            "BC966A19F9C76045D4962C9CB9889B2B97274C3B8E55C305B312A572986504AA",
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        self.assertTrue(ART.is_file())
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((362, 264), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
