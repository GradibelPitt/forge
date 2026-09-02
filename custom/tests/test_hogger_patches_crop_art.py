import hashlib
from pathlib import Path
import unittest

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
PICTURES = ROOT / "cards" / "pictures" / "PH01"
ART_BACKUPS = ROOT / "tools" / "card-artwork"
INSTALLER = ROOT / "tools" / "install_to_forge.ps1"

HOGGER_BACKUP = ART_BACKUPS / "Chainbreaker_Hogger_full.jpg"
HOGGER_CROP = PICTURES / "破链灾星霍格.artcrop.jpg"
PATCHES_BACKUP = ART_BACKUPS / "Patches_the_Pirate_full.jpg"
PATCHES_CROP = PICTURES / "海盗帕奇斯.artcrop.jpg"


class HoggerPatchesCropArtTest(unittest.TestCase):
    def test_ph01_registers_one_custom_crop_for_each_card(self):
        rows = EDITION.read_text(encoding="utf-8").splitlines()

        self.assertEqual(
            ["8 M 破链灾星霍格 @Custom"],
            [row.strip() for row in rows if "破链灾星霍格" in row],
        )
        self.assertEqual(
            ["10 M 海盗帕奇斯 @Custom"],
            [row.strip() for row in rows if "海盗帕奇斯" in row],
        )

    def test_hogger_uses_hswiki_original_and_default_frame_crop(self):
        self.assertEqual(
            "E24C17D29404772BA010015937A965A1451D87EEA80B8F75575886A5ECAC4C31",
            hashlib.sha256(HOGGER_BACKUP.read_bytes()).hexdigest().upper(),
        )
        with Image.open(HOGGER_BACKUP) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((3000, 4000), image.size)

        self.assertEqual(
            "D9E6FF1860AE565216149B5F98FC24C3823AB895BD66950ED0425B5AAEEF3817",
            hashlib.sha256(HOGGER_CROP.read_bytes()).hexdigest().upper(),
        )
        with Image.open(HOGGER_CROP) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((3000, 2190), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)

    def test_patches_uses_hswiki_original_and_default_frame_crop(self):
        self.assertEqual(
            "86FC67DBBC44D50D3BAE7ED9D9B8F1098BD4E927C33B97153D3CC6CB5597642E",
            hashlib.sha256(PATCHES_BACKUP.read_bytes()).hexdigest().upper(),
        )
        with Image.open(PATCHES_BACKUP) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((750, 1000), image.size)

        self.assertEqual(
            "A4DCF49CA3DC0E0D76C00A03D93D37FD7D352AFAEBD458F1C3F2210BFD5522C4",
            hashlib.sha256(PATCHES_CROP.read_bytes()).hexdigest().upper(),
        )
        with Image.open(PATCHES_CROP) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((750, 548), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)

    def test_no_obsolete_full_art_or_numbered_hogger_variants_remain(self):
        obsolete = (
            "破链灾星霍格.full.jpg",
            "破链灾星霍格1.artcrop.jpg",
            "破链灾星霍格2.full.jpg",
            "破链灾星霍格2.artcrop.jpg",
            "海盗帕奇斯.full.jpg",
        )
        for filename in obsolete:
            with self.subTest(filename=filename):
                self.assertFalse((PICTURES / filename).exists())

    def test_installer_removes_obsolete_deployed_full_art_variants(self):
        installer = INSTALLER.read_text(encoding="utf-8-sig")

        for path in (
            '"PH01\\$ChainbreakerHoggerName.full.jpg"',
            '"PH01\\$($ChainbreakerHoggerName)1.artcrop.jpg"',
            '"PH01\\$($ChainbreakerHoggerName)2.full.jpg"',
            '"PH01\\$($ChainbreakerHoggerName)2.artcrop.jpg"',
            '"PH01\\$PatchesPirateName.full.jpg"',
            '"PH01\\$($PatchesPirateName)1.artcrop.jpg"',
            '"PH01\\$($PatchesPirateName)2.full.jpg"',
            '"PH01\\$($PatchesPirateName)2.artcrop.jpg"',
        ):
            with self.subTest(path=path):
                self.assertIn(path, installer)


if __name__ == "__main__":
    unittest.main()
