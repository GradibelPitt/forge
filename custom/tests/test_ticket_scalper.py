import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "multicolor" / "黑心票贩.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Ticket_Scalper_full.webp"
ART = ROOT / "cards" / "pictures" / "PH01" / "黑心票贩.artcrop.jpg"

ORACLE = (
    "只要你操控另一个海盗，黑心票贩便具有敏捷和挑拨。\\n"
    "每当黑心票贩因攻击造成过量伤害时，抓两张牌。"
)


class TicketScalperContractTest(unittest.TestCase):
    def test_characteristics_pirate_condition_and_excess_damage_draw(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:黑心票贩", lines)
        self.assertIn("ManaCost:2 U R", lines)
        self.assertIn("Types:Creature Pirate", lines)
        self.assertIn("PT:5/3", lines)

        static = next(line for line in lines if line.startswith("S:Mode$ Continuous"))
        self.assertIn("Affected$ Card.Self", static)
        self.assertIn("AddKeyword$ Haste & Provoke", static)
        self.assertIn("IsPresent$ Pirate.Other+YouCtrl", static)

        trigger = next(line for line in lines if line.startswith("T:Mode$ DamageDone"))
        self.assertIn("ValidSource$ Card.Self", trigger)
        self.assertIn("ValidTarget$ Creature.wasDealtExcessDamageThisTurn", trigger)
        self.assertIn("Execute$ TrigDraw", trigger)
        self.assertIn("CombatDamage$ True", trigger)
        self.assertIn("SVar:TrigDraw:DB$ Draw | Defined$ You | NumCards$ 2", lines)
        self.assertIn(f"Oracle:{ORACLE}", lines)

    def test_registration_localization_documentation_and_art(self):
        self.assertIn(
            "127 C 黑心票贩 @David Kegg",
            EDITION.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            f"黑心票贩|黑心票贩|生物～海盗|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 黑心票贩 | `{2}{U}{R}`，5/3 生物～海盗 | "
            "`cards/multicolor/黑心票贩.txt` | 127 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        self.assertTrue(ART_BACKUP.is_file())
        with Image.open(ART_BACKUP) as image:
            self.assertEqual("WEBP", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((960, 1280), image.size)

        self.assertTrue(ART.is_file())
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((960, 700), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
