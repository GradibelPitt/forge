import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "colorless" / "魔能机甲.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-266b4ed0-3066-44f5-82f5-28ac477dca89.png"
)
ART = ROOT / "cards" / "pictures" / "PH01" / "魔能机甲.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = "威慑，践踏。\\n每当你的对手施放一个咒语时，你磨三张牌"


class FelReaverContractTest(unittest.TestCase):
    def test_card_fields_and_opponent_spell_trigger(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:魔能机甲", lines)
        self.assertIn("ManaCost:3 B B", lines)
        self.assertIn("Types:Artifact Creature", lines)
        self.assertIn("PT:8/8", lines)
        self.assertIn("K:Menace", lines)
        self.assertIn("K:Trample", lines)

        trigger = next(line for line in lines if line.startswith("T:Mode$ SpellCast"))
        self.assertIn("ValidCard$ Card", trigger)
        self.assertIn("ValidActivatingPlayer$ Opponent", trigger)
        self.assertIn("TriggerZones$ Battlefield", trigger)
        self.assertIn("Execute$ TrigMill", trigger)

        mill = next(line for line in lines if line.startswith("SVar:TrigMill:"))
        self.assertIn("DB$ Mill", mill)
        self.assertIn("Defined$ You", mill)
        self.assertIn("NumCards$ 3", mill)
        self.assertIn(f"Oracle:{ORACLE}", lines)

    def test_registration_localization_art_and_documentation(self):
        self.assertIn("95 R 魔能机甲 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertIn(
            f"魔能机甲|魔能机甲|神器生物|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 魔能机甲 | `{3}{B}{B}`，8/8 神器生物 | "
            "`cards/colorless/魔能机甲.txt` | 95 | "
            "威慑、践踏；每当你的对手施放一个咒语时，你磨三张牌。 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        self.assertTrue(ART_BACKUP.is_file())
        self.assertEqual(
            "0C81EA6B91C4772EDF1DD0A24A4A7658489A2799B1641FFA9FABBE3DEE146F7E",
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        self.assertTrue(ART.is_file())
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((1024, 748), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
