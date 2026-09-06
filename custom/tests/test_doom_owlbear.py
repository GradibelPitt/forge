import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "multicolor" / "末日枭兽.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Doomkin_JAM_029.png"
ART = ROOT / "cards" / "pictures" / "PH01" / "末日枭兽.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = "飞行\\n当末日枭兽进战场时，获得至多一个目标地的操控权。"
SOURCE_ART_SHA256 = "9B6FBFF18E8E73318FEE559D9ABA68956120D50668BBB3483BF5E7337A281BBF"


class DoomOwlbearContractTest(unittest.TestCase):
    def test_card_profile_and_flying_match_the_requested_design(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:末日枭兽", lines)
        self.assertIn("ManaCost:3 U G", lines)
        self.assertIn("Types:Creature Bird Bear", lines)
        self.assertIn("PT:3/4", lines)
        self.assertIn("K:Flying", lines)

    def test_etb_may_permanently_gain_control_of_up_to_one_target_land(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        trigger = next(line for line in lines if line.startswith("T:Mode$ ChangesZone"))
        self.assertIn("Origin$ Any", trigger)
        self.assertIn("Destination$ Battlefield", trigger)
        self.assertIn("ValidCard$ Card.Self", trigger)
        self.assertIn("Execute$ TrigGainControl", trigger)

        gain_control = next(
            line for line in lines if line.startswith("SVar:TrigGainControl:")
        )
        self.assertIn("DB$ GainControl", gain_control)
        self.assertIn("ValidTgts$ Land", gain_control)
        self.assertIn("TargetMin$ 0", gain_control)
        self.assertIn("TargetMax$ 1", gain_control)
        self.assertNotIn("LoseControl$", gain_control)
        self.assertIn(f"Oracle:{ORACLE}", lines)

    def test_registration_localization_art_and_documentation(self):
        self.assertIn(
            "184 U 末日枭兽 @Mooncolony",
            EDITION.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            f"末日枭兽|末日枭兽|生物～鸟／熊|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 末日枭兽 | `{3}{U}{G}`，3/4 生物～鸟／熊 | "
            "`cards/multicolor/末日枭兽.txt` | 184 |",
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
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
