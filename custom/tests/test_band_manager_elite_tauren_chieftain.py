import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "colorless" / "乐队经理牛头人酋长.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-138709a5-f06a-4566-8a92-38f10a335f24.png"
)
ART = ROOT / "cards" / "pictures" / "PH01" / "乐队经理牛头人酋长.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

SOURCE_ORACLE = (
    "When CARDNAME enters, discover a card in your sideboard that hasn't been chosen "
    "this way, then put it into your hand."
)
ZH_ORACLE = "当乐队经理牛头人酋长进战场时，发现一张在你备牌中且未以此法选择过的牌，将其置入你手中。"


class BandManagerEliteTaurenChieftainContractTest(unittest.TestCase):
    def test_characteristics_and_sideboard_discover(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:乐队经理牛头人酋长", text)
        self.assertIn("ManaCost:4", text)
        self.assertIn("Types:Legendary Creature Minotaur", text)
        self.assertIn("PT:4/4", text)

        trigger = next(line for line in text.splitlines() if line.startswith("T:Mode$ ChangesZone"))
        self.assertIn("Origin$ Any", trigger)
        self.assertIn("Destination$ Battlefield", trigger)
        self.assertIn("ValidCard$ Card.Self", trigger)
        self.assertIn("Execute$ TrigDiscover", trigger)

        discover = next(line for line in text.splitlines() if line.startswith("SVar:TrigDiscover:"))
        self.assertIn("DB$ CardDiscover", discover)
        self.assertIn("Defined$ You", discover)
        self.assertIn("Source$ Sideboard", discover)
        self.assertIn("SourceController$ You", discover)
        self.assertIn("ValidCards$ Card.YouOwn+doesNotShareNameWith Remembered", discover)
        self.assertIn("OptionCount$ 3", discover)
        self.assertIn("Destination$ Hand", discover)
        self.assertIn("RememberChosen$ True", discover)
        self.assertIn(f"Oracle:{SOURCE_ORACLE}", text)

    def test_registration_localization_and_art(self):
        self.assertIn("76 M 乐队经理牛头人酋长 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertIn(
            f"乐队经理牛头人酋长|乐队经理牛头人酋长|传奇生物～牛头人|{ZH_ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())

        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertGreater(image.width, image.height)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
