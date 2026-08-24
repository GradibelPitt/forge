import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "colorless" / "伴唱机.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-3bfc4869-1385-4d7b-8c71-13531638b333.png"
)
ART = ROOT / "cards" / "pictures" / "PH01" / "伴唱机.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ABILITY_TEXT = (
    "选择目标由你操控的鹏洛客。本回合中，每当你起动其忠诚异能时，"
    "复制该异能。你可以为该复制品选择新的目标。"
)
ORACLE = f"{{T}}：{ABILITY_TEXT}"


class AccompanimentMachineContractTest(unittest.TestCase):
    def test_copies_each_loyalty_ability_of_only_the_targeted_planeswalker(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:伴唱机", lines)
        self.assertIn("ManaCost:3", lines)
        self.assertIn("Types:Artifact Creature", lines)
        self.assertIn("PT:2/4", lines)

        ability = next(line for line in lines if line.startswith("A:AB$ Effect"))
        self.assertIn("Cost$ T", ability)
        self.assertIn("ValidTgts$ Planeswalker.YouCtrl", ability)
        self.assertIn("RememberObjects$ Targeted", ability)
        self.assertIn("Triggers$ CopyTargetedLoyalty", ability)
        self.assertIn(f"SpellDescription$ {ABILITY_TEXT}", ability)
        self.assertNotIn("DelayedTrigger", ability)

        trigger = next(
            line for line in lines if line.startswith("SVar:CopyTargetedLoyalty:")
        )
        self.assertIn("Mode$ AbilityCast", trigger)
        self.assertIn("ValidCard$ Planeswalker.IsRemembered", trigger)
        self.assertIn("ValidSA$ Activated.Loyalty", trigger)
        self.assertIn("ValidActivatingPlayer$ You", trigger)
        self.assertIn("TriggerZones$ Command", trigger)
        self.assertIn("Execute$ CopyLoyaltyAbility", trigger)
        self.assertNotIn("OneOff$ True", trigger)

        copy = next(
            line for line in lines if line.startswith("SVar:CopyLoyaltyAbility:")
        )
        self.assertIn("DB$ CopySpellAbility", copy)
        self.assertIn("Defined$ TriggeredSpellAbility", copy)
        self.assertIn("MayChooseTarget$ True", copy)
        self.assertIn(f"Oracle:{ORACLE}", lines)

    def test_registration_localization_art_and_documentation(self):
        self.assertIn("103 R 伴唱机 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertIn(
            f"伴唱机|伴唱机|神器生物|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 伴唱机 | `{3}`，2/4 神器生物 | "
            "`cards/colorless/伴唱机.txt` | 103 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        self.assertTrue(ART_BACKUP.is_file())
        self.assertEqual(
            "7692A17A4931F762BBB6B57E4FA831D5083E0262D27DA270BC83F5282F78D451",
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        self.assertTrue(ART.is_file())
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((1024, 748), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
