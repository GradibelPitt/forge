import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "green" / "青玉护符.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Jade_Idol_full_hswiki.jpg"
ART = ROOT / "cards" / "pictures" / "PH01" / "青玉护符.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = (
    "选择一项：\\n"
    "• 化生三张青玉护符到你的牌库中，然后将你的牌库洗牌。\\n"
    "• 化生一个青玉魔像并置入战场。"
)


class JadeIdolContractTest(unittest.TestCase):
    def test_choose_one_modes_follow_the_requested_order(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:青玉护符", lines)
        self.assertIn("ManaCost:G", lines)
        self.assertIn("Types:Sorcery", lines)

        spell = next(line for line in lines if line.startswith("A:SP$ Charm"))
        self.assertIn("Choices$ DBShuffleIdols,DBJadeGolem", spell)
        self.assertIn(f"SpellDescription$ {ORACLE}", spell)

        shuffle_mode = next(
            line for line in lines if line.startswith("SVar:DBShuffleIdols:")
        )
        self.assertIn("DB$ MakeCard", shuffle_mode)
        self.assertIn("Defined$ You", shuffle_mode)
        self.assertIn("Conjure$ True", shuffle_mode)
        self.assertIn("Name$ 青玉护符", shuffle_mode)
        self.assertIn("Amount$ 3", shuffle_mode)
        self.assertIn("Zone$ Library", shuffle_mode)
        self.assertNotIn("LibraryPosition$", shuffle_mode)

        golem_mode = next(
            line for line in lines if line.startswith("SVar:DBJadeGolem:")
        )
        self.assertIn("DB$ MakeCard", golem_mode)
        self.assertIn("Defined$ You", golem_mode)
        self.assertIn("Conjure$ True", golem_mode)
        self.assertIn("Name$ 青玉魔像", golem_mode)
        self.assertIn("Amount$ 1", golem_mode)
        self.assertIn("Zone$ Battlefield", golem_mode)

        self.assertIn(f"Oracle:{ORACLE}", lines)

    def test_registration_localization_art_and_documentation(self):
        self.assertIn(
            "115 R 青玉护符 @Custom",
            EDITION.read_text(encoding="utf-8"),
        )
        self.assertIn(
            f"青玉护符|青玉护符|法术|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 青玉护符 | `{G}` 法术 | `cards/green/青玉护符.txt` | 115 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        self.assertTrue(ART_BACKUP.is_file(), ART_BACKUP)
        self.assertTrue(ART.is_file(), ART)
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((1024, 746), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)

        self.assertEqual(
            "D6BB67896CBCE18717D1CA169C8ABC22A370EBD9ECF9F0EE61BDABDB5049F749",
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )


if __name__ == "__main__":
    unittest.main()
