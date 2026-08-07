import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "green" / "生物计划.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-8260981a-a5c2-453e-9467-41b4a771d9cb.png"
)
ART = ROOT / "cards" / "pictures" / "PH01" / "生物计划.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = "每位牌手从牌库中搜寻两张基本地牌，并将它们横置放入战场"
SOURCE_ART_SHA256 = "6CCC603FC091B1A765709EB085060BB613F182E600F41E917B0C87281F5165CE"


class BiologyProjectContractTest(unittest.TestCase):
    def test_each_player_searches_two_basic_lands_onto_the_battlefield_tapped(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:生物计划", lines)
        self.assertIn("ManaCost:G", lines)
        self.assertIn("Types:Sorcery", lines)

        spell = next(line for line in lines if line.startswith("A:SP$ ChangeZone"))
        self.assertIn("Origin$ Library", spell)
        self.assertIn("Destination$ Battlefield", spell)
        self.assertIn("ChangeType$ Land.Basic", spell)
        self.assertIn("ChangeTypeDesc$ basic land", spell)
        self.assertIn("DefinedPlayer$ Player", spell)
        self.assertIn("ChangeNum$ 2", spell)
        self.assertIn("Tapped$ True", spell)
        self.assertIn("NoShuffle$ True", spell)
        self.assertNotIn("Optional$ True", spell)
        self.assertNotIn("ValidTgts$", spell)
        self.assertIn(f"SpellDescription$ {ORACLE}", spell)
        self.assertIn(f"Oracle:{ORACLE}", lines)

    def test_registration_localization_art_and_documentation(self):
        self.assertIn(
            "109 C 生物计划 @Custom",
            EDITION.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            f"生物计划|生物计划|法术|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 生物计划 | `{G}` 法术 | `cards/green/生物计划.txt` | 109 |",
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
            self.assertEqual((512, 374), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
