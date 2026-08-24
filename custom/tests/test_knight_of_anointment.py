import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "white" / "圣礼骑士.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-763fb895-9e1e-4244-8ec4-1cac3a776f4c.png"
)
ART = ROOT / "cards" / "pictures" / "PH01" / "圣礼骑士.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = "当圣礼骑士进战场时，随机将一张白色瞬间或法术牌从你的牌库置于你手上。"


class KnightOfAnointmentContractTest(unittest.TestCase):
    def test_characteristics_and_enters_seek_trigger(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:圣礼骑士", text)
        self.assertIn("ManaCost:W", text)
        self.assertIn("Types:Creature Human Paladin", text)
        self.assertIn("PT:1/1", text)
        self.assertIn(
            "T:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | "
            "ValidCard$ Card.Self | Execute$ TrigSeek",
            text,
        )
        self.assertIn(
            "SVar:TrigSeek:DB$ Seek | Type$ Instant.White,Sorcery.White | Num$ 1",
            text,
        )
        self.assertNotIn("MonoColor", text)
        self.assertIn(f"Oracle:{ORACLE}", text)

    def test_registration_localization_and_original_art_crop(self):
        self.assertIn(
            "88 C 圣礼骑士 @Custom",
            EDITION.read_text(encoding="utf-8"),
        )
        self.assertIn(
            f"圣礼骑士|圣礼骑士|生物～人类／圣骑士|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )

        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())
        self.assertEqual(
            "EA5EA9FB72C57C607C13E6D250C19EEF5BF6C0D6CC346F4C53472C92323FC30F",
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        with Image.open(ART_BACKUP) as image:
            self.assertEqual((512, 512), image.size)
            self.assertEqual("RGB", image.mode)
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((380, 277), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
