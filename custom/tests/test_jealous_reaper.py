import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "multicolor" / "妒意收割者.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-e72571e6-fd9e-4995-9f4e-1aff32011434.png"
)
ART = ROOT / "cards" / "pictures" / "PH01" / "妒意收割者.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = (
    "在你施放一个咒语后，从你对手的战场上，手中，牌库或坟墓场"
    "如顺手牵羊般地拿走一张与该咒语同名的牌或永久物。"
)


class JealousReaperContractTest(unittest.TestCase):
    def test_card_uses_the_exact_requested_text_and_dedicated_api(self):
        text = CARD.read_text(encoding="utf-8")
        self.assertIn("Name:妒意收割者", text)
        self.assertIn("ManaCost:1 B U", text)
        self.assertIn("Types:Legendary Creature Vampire Venthyr", text)
        self.assertIn("PT:4/3", text)
        self.assertIn(f"TriggerDescription$ {ORACLE}", text)
        self.assertIn(f"Oracle:{ORACLE}", text)
        self.assertIn(
            "SVar:TrigSteal:DB$ StealSameName | ValidTgts$ Opponent",
            text,
        )

    def test_registration_art_and_localization(self):
        self.assertIn("69 M 妒意收割者 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertEqual(
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
            "244ADEC3B6F21DB993AB8E73AB6CF7604ABD0A3C0D50A6258AC3FCA1C9E7DD81",
        )
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((1024, 747), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)
        self.assertIn(
            f"妒意收割者|妒意收割者|传奇生物～吸血鬼／温西尔|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )


if __name__ == "__main__":
    unittest.main()
