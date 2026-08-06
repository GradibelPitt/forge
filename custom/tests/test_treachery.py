import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "black" / "变节.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Treachery_original.jpg"
ART = ROOT / "cards" / "pictures" / "PH01" / "变节.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = "选择目标由你控制的生物，目标对手获得该生物的操控权。"
SOURCE_ART_SHA256 = "657B54F35736ED33AEDD5712670B7371351567DB883BB88343034BF077E54C49"


class TreacheryContractTest(unittest.TestCase):
    def test_target_opponent_permanently_gains_the_target_creature(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:变节", lines)
        self.assertIn("ManaCost:B", lines)
        self.assertIn("Types:Instant", lines)

        spell = next(line for line in lines if line.startswith("A:SP$ Pump"))
        self.assertIn("ValidTgts$ Opponent", spell)
        self.assertIn("SubAbility$ DBGainControl", spell)
        self.assertIn(f"SpellDescription$ {ORACLE}", spell)

        gain_control = next(
            line for line in lines if line.startswith("SVar:DBGainControl:")
        )
        self.assertIn("DB$ GainControl", gain_control)
        self.assertIn("ValidTgts$ Creature.YouCtrl", gain_control)
        self.assertIn("NewController$ ParentTarget", gain_control)
        self.assertNotIn("LoseControl$", gain_control)
        self.assertNotIn("ChangeZone", "\n".join(lines))
        self.assertIn(f"Oracle:{ORACLE}", lines)

    def test_registration_localization_art_and_documentation(self):
        self.assertIn("106 R 变节 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertIn(
            f"变节|变节|瞬间|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 变节 | `{B}` 瞬间 | `cards/black/变节.txt` | 106 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        self.assertTrue(ART_BACKUP.is_file())
        self.assertEqual(
            SOURCE_ART_SHA256,
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        with Image.open(ART_BACKUP) as image:
            self.assertEqual("JPEG", image.format)
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
