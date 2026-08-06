import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "black" / "液力压裂.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-f9df70b0-b9bd-458b-b371-de472a9427c4.png"
)
ART = ROOT / "cards" / "pictures" / "PH01" / "液力压裂.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = "检视你牌堆底的三张牌，选择其中一张放在你手上，然后将其余两张牌放入坟墓场"


class FrackingContractTest(unittest.TestCase):
    def test_characteristics_and_bottom_three_selection(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:液力压裂", lines)
        self.assertIn("ManaCost:B", lines)
        self.assertIn("Types:Sorcery", lines)
        ability = next(line for line in lines if line.startswith("A:SP$ Dig"))
        self.assertIn("DigNum$ 3", ability)
        self.assertIn("ChangeNum$ 1", ability)
        self.assertIn("FromBottom$ True", ability)
        self.assertIn("ChangeValid$ Card", ability)
        self.assertIn("DestinationZone$ Hand", ability)
        self.assertIn("DestinationZone2$ Graveyard", ability)
        self.assertIn("StackDescription$ SpellDescription", ability)
        self.assertIn(f"SpellDescription$ {ORACLE}", ability)
        self.assertIn(f"Oracle:{ORACLE}", lines)

    def test_registration_localization_art_and_documentation(self):
        self.assertIn("90 R 液力压裂 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertIn(
            f"液力压裂|液力压裂|法术|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 液力压裂 | `{B}` 法术 | `cards/black/液力压裂.txt` | 90 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        self.assertTrue(ART_BACKUP.is_file())
        self.assertEqual(
            "D85EF02DD1A20E33F8AB9A47769349514D6EDF88B08BCEC8AD3C65C8C717627B",
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        self.assertTrue(ART.is_file())
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((510, 372), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
