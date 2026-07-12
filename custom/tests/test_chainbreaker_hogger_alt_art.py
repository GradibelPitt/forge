from pathlib import Path
import unittest

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
PICTURES = ROOT / "cards" / "pictures" / "PH01"


class ChainbreakerHoggerAlternateArtTest(unittest.TestCase):
    def test_ph01_registers_two_consecutive_hogger_arts(self):
        rows = [
            line.strip()
            for line in EDITION.read_text(encoding="utf-8").splitlines()
            if "破链灾星霍格" in line
        ]
        self.assertEqual(
            ["8 M 破链灾星霍格 @Custom", "8a M 破链灾星霍格"],
            rows,
        )

    def test_first_art_is_landscape_crop_and_second_is_generated_full_art_card(self):
        crop = PICTURES / "破链灾星霍格1.artcrop.jpg"
        extended = PICTURES / "破链灾星霍格2.full.jpg"
        original = PICTURES / "破链灾星霍格.full.jpg"

        self.assertTrue(crop.is_file())
        self.assertTrue(extended.is_file())
        self.assertNotEqual(original.read_bytes(), extended.read_bytes())

        with Image.open(crop) as image:
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.1)
        with Image.open(extended) as image:
            self.assertEqual((2010, 2814), image.size)


if __name__ == "__main__":
    unittest.main()
