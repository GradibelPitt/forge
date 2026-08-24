import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "green" / "青玉飞镖.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Jade_Shuriken_full_hswiki.jpg"
ART = ROOT / "cards" / "pictures" / "PH01" / "青玉飞镖.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = (
    "青玉飞镖对任一目标造成3点伤害，如果此咒语不是你本回合施放的第一个咒语，"
    "派出一个青玉魔像"
)
SOURCE_ART_SHA256 = "9466AE7CB4E5FF9A81A95BD18AD981C2D02B98967C025CC58E20C195D5DC6A41"


class JadeShurikenContractTest(unittest.TestCase):
    def test_damage_then_conditionally_conjures_a_jade_golem(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:青玉飞镖", lines)
        self.assertIn("ManaCost:1 B", lines)
        self.assertIn("Types:Sorcery", lines)

        spell = next(line for line in lines if line.startswith("A:SP$ DealDamage"))
        self.assertIn("ValidTgts$ Any", spell)
        self.assertIn("NumDmg$ 3", spell)
        self.assertIn("SubAbility$ DBJadeGolem", spell)
        self.assertIn(f"SpellDescription$ {ORACLE}", spell)

        golem = next(
            line for line in lines if line.startswith("SVar:DBJadeGolem:")
        )
        self.assertIn("DB$ MakeCard", golem)
        self.assertIn("Defined$ You", golem)
        self.assertIn("Conjure$ True", golem)
        self.assertIn("Name$ 青玉魔像", golem)
        self.assertIn("Amount$ 1", golem)
        self.assertIn("Zone$ Battlefield", golem)
        self.assertIn("ConditionCheckSVar$ SpellsCastThisTurn", golem)
        self.assertIn("ConditionSVarCompare$ GE2", golem)
        self.assertIn(
            "SVar:SpellsCastThisTurn:Count$ThisTurnCast_Card.YouCtrl",
            lines,
        )
        self.assertIn(f"Oracle:{ORACLE}", lines)

    def test_registration_localization_art_and_documentation(self):
        self.assertIn(
            "116 C 青玉飞镖 @Izzy Hoover",
            EDITION.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            f"青玉飞镖|青玉飞镖|法术|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 青玉飞镖 | `{1}{B}` 法术 | `cards/green/青玉飞镖.txt` | 116 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        self.assertTrue(ART_BACKUP.is_file(), ART_BACKUP)
        self.assertTrue(ART.is_file(), ART)
        self.assertEqual(
            SOURCE_ART_SHA256,
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        with Image.open(ART_BACKUP) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((1200, 964), image.size)
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((1200, 876), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
