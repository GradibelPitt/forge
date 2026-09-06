import hashlib
import os
import unittest
from pathlib import Path

from PIL import Image


CUSTOM = Path(__file__).resolve().parents[1]
APP_ROOT = Path(os.environ["FORGE_APP_ROOT"]) if os.environ.get("FORGE_APP_ROOT") else None
PAYLOAD = APP_ROOT / "managed" / "custom" if APP_ROOT else CUSTOM
CARD = PAYLOAD / "cards" / "green" / "先觉蜿变幼龙.txt"
EDITION = PAYLOAD / "editions" / "Placeholder_Set.txt"
ART = PAYLOAD / "cards" / "pictures" / "PH01" / "先觉蜿变幼龙.artcrop.jpg"
ZH_CN = (APP_ROOT / "res" if APP_ROOT else CUSTOM.parent / "forge-gui" / "res") / "languages" / "cardnames-zh-CN.txt"
MANIFEST = APP_ROOT / "manifest-critical.sha256" if APP_ROOT else None

ORACLE = "请援龙或支付{3}，以作为施放此咒语的额外费用。\\n飞行，辟邪"


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


class PrescientSlitherdrakeContractTest(unittest.TestCase):
    def test_card_contract(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()
        self.assertEqual(
            [
                "Name:先觉蜿变幼龙",
                "ManaCost:1 G G G",
                "Types:Creature Dragon",
                "PT:6/8",
                "K:AlternateAdditionalCost:Behold<1/Dragon>:3",
                "K:Flying",
                "K:Hexproof",
                "DeckHints:Type$Dragon",
                f"Oracle:{ORACLE}",
            ],
            lines,
        )

    def test_registration_and_localization(self):
        self.assertIn(
            "187 C 先觉蜿变幼龙 @Edgar Hidalgo",
            EDITION.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            f"先觉蜿变幼龙|先觉蜿变幼龙|生物～龙|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )

    def test_art_contract(self):
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((1024, 748), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)

    @unittest.skipUnless(APP_ROOT, "Set FORGE_APP_ROOT to validate the runtime manifest")
    def test_manifest_hashes(self):
        entries = MANIFEST.read_text(encoding="utf-8").splitlines()
        expected = {
            "managed/custom/cards/green/先觉蜿变幼龙.txt": CARD,
            "managed/custom/cards/pictures/PH01/先觉蜿变幼龙.artcrop.jpg": ART,
            "managed/custom/editions/Placeholder_Set.txt": EDITION,
            "res/languages/cardnames-zh-CN.txt": ZH_CN,
        }
        for relative_path, path in expected.items():
            self.assertIn(f"{sha256(path)} *{relative_path}", entries)


if __name__ == "__main__":
    unittest.main()
