import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "black" / "demonfire_custom.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-7d427378-b26c-4de4-8cb7-3ef9030989d6.png"
)
ART = ROOT / "cards" / "pictures" / "PH01" / "Demonfire (Custom).full.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = (
    "Demonfire deals 2 damage to target creature. If it's a Demon you control, "
    "put two +1/+1 counters on it instead."
)
ZH_ORACLE = (
    "恶魔之火对目标生物造成2点伤害。若它是由你操控的恶魔，则改为在其上放置两个+1/+1指示物。"
)


class DemonfireCustomContractTest(unittest.TestCase):
    def test_spell_branches_between_damage_and_counters(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:Demonfire (Custom)", lines)
        self.assertNotIn("Name:Demonfire", lines)
        self.assertIn("ManaCost:1 B", lines)
        self.assertIn("Types:Instant", lines)

        spell = next(line for line in lines if line.startswith("A:SP$ Branch"))
        self.assertIn("ValidTgts$ Creature", spell)
        self.assertIn("BranchConditionSVar$ IsControlledDemon", spell)
        self.assertIn("BranchConditionSVarCompare$ EQ1", spell)
        self.assertIn("TrueSubAbility$ PutCounters", spell)
        self.assertIn("FalseSubAbility$ DealDamage", spell)
        self.assertIn(f"SpellDescription$ {ORACLE}", spell)

        self.assertIn(
            "SVar:IsControlledDemon:Targeted$Valid Creature.Demon+YouCtrl",
            lines,
        )
        self.assertIn(
            "SVar:PutCounters:DB$ PutCounter | Defined$ Targeted | "
            "CounterTypes$ P1P1 | CounterNum$ 2",
            lines,
        )
        self.assertIn(
            "SVar:DealDamage:DB$ DealDamage | Defined$ Targeted | NumDmg$ 2",
            lines,
        )
        self.assertIn(f"Oracle:{ORACLE}", lines)
        self.assertIn(
            "Text:Demonfire is like regular fire except for IT NEVER STOPS "
            "BURNING HELLPPP",
            lines,
        )

    def test_registration_localization_full_card_art_and_documentation(self):
        edition_lines = EDITION.read_text(encoding="utf-8").splitlines()
        self.assertIn("98 C Demonfire (Custom)", edition_lines)
        self.assertNotIn("98 C Demonfire (Custom) @Custom", edition_lines)
        self.assertIn(
            f"Demonfire (Custom)|恶魔之火（自制）|瞬间|{ZH_ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| Demonfire (Custom) | `{1}{B}` 瞬间 | "
            "`cards/black/demonfire_custom.txt` | 98 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        self.assertTrue(ART_BACKUP.is_file())
        self.assertEqual(
            "31B68404CDD9E2A9B2E4A6F43C1EC5A6BEBA1E7443E62881180123448ACF926E",
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        self.assertTrue(ART.is_file())
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((375, 523), image.size)


if __name__ == "__main__":
    unittest.main()
