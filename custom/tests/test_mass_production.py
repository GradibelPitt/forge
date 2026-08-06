import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "black" / "批量生产.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Mass_Production_original.jpg"
ART = ROOT / "cards" / "pictures" / "PH01" / "批量生产.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = "批量生产对你造成3点伤害，抓两张牌，化生两张批量生产并置入牌库，然后将你的牌库洗牌"


class MassProductionContractTest(unittest.TestCase):
    def test_damage_draw_and_conjure_chain_matches_the_requested_order(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:批量生产", lines)
        self.assertIn("ManaCost:B", lines)
        self.assertIn("Types:Sorcery", lines)

        spell = next(line for line in lines if line.startswith("A:SP$ DealDamage"))
        self.assertIn("Defined$ You", spell)
        self.assertIn("NumDmg$ 3", spell)
        self.assertIn("SubAbility$ DBDraw", spell)
        self.assertIn(f"SpellDescription$ {ORACLE}", spell)

        draw = next(line for line in lines if line.startswith("SVar:DBDraw:"))
        self.assertIn("DB$ Draw", draw)
        self.assertIn("Defined$ You", draw)
        self.assertIn("NumCards$ 2", draw)
        self.assertIn("SubAbility$ DBConjure", draw)

        conjure = next(line for line in lines if line.startswith("SVar:DBConjure:"))
        self.assertIn("DB$ MakeCard", conjure)
        self.assertIn("Defined$ You", conjure)
        self.assertIn("Conjure$ True", conjure)
        self.assertIn("Name$ 批量生产", conjure)
        self.assertIn("Amount$ 2", conjure)
        self.assertIn("Zone$ Library", conjure)
        self.assertNotIn("LibraryPosition$", conjure)
        self.assertIn(f"Oracle:{ORACLE}", lines)

    def test_registration_localization_art_and_documentation(self):
        self.assertIn("100 C 批量生产 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertIn(
            f"批量生产|批量生产|法术|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 批量生产 | `{B}` 法术 | `cards/black/批量生产.txt` | 100 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        self.assertTrue(ART_BACKUP.is_file())
        self.assertEqual(
            "517CBEEB7213E4C2E358C9DE6B7916110698E34B08A19D152AD0C8AF2C5A4ACE",
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        self.assertTrue(ART.is_file())
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((1248, 911), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
