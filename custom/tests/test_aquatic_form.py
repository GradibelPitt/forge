import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "blue" / "水栖形态.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-60431522-ba36-4823-bc7c-9acaea564d6f.png"
)
ART = ROOT / "cards" / "pictures" / "PH01" / "水栖形态.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = (
    "检视你牌库底的三张牌，选择其中一张置于你的牌库顶。"
    "如果你有足够的法术力可以施放该咒语，则你可以改为展示该牌，然后将其置于你手上。"
)
SOURCE_ART_SHA256 = "F76C13762A5D1F3D73EA0232866B1492DC8D668F720DD99E4862D757BB52C26C"


class AquaticFormContractTest(unittest.TestCase):
    def test_bottom_three_top_or_affordable_hand_contract(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertEqual("Name:水栖形态", lines[0])
        self.assertIn("ManaCost:0", lines)
        self.assertIn("Colors:blue", lines)
        self.assertIn("Types:Sorcery", lines)

        self.assertIn(
            "A:SP$ Dig | DigNum$ 3 | ChangeNum$ 1 | FromBottom$ True | "
            "ChangeValid$ Card | DestinationZone$ Library | LibraryPosition$ 0 | "
            "DestinationZone2$ Library | LibraryPosition2$ -1 | SkipReorder$ True | "
            "NoReveal$ True | RememberChanged$ True | SubAbility$ CheckSpell | "
            "StackDescription$ SpellDescription | SpellDescription$ " + ORACLE,
            lines,
        )
        self.assertIn(
            "SVar:CheckSpell:DB$ Branch | BranchConditionSVar$ RememberedNonland | "
            "BranchConditionSVarCompare$ GE1 | TrueSubAbility$ CheckMana | "
            "FalseSubAbility$ Cleanup",
            lines,
        )
        self.assertIn(
            "SVar:CheckMana:DB$ Branch | BranchConditionSVar$ AvailableMana | "
            "BranchConditionSVarCompare$ GERememberedManaValue | "
            "TrueSubAbility$ MoveToHand | FalseSubAbility$ Cleanup",
            lines,
        )
        self.assertIn(
            "SVar:MoveToHand:DB$ ChangeZone | Defined$ Remembered | Origin$ Library | "
            "Destination$ Hand | Optional$ True | OptionalPrompt$ "
            "展示该牌并将其置于你手上？ | Reveal$ True | SubAbility$ Cleanup",
            lines,
        )
        self.assertIn(
            "SVar:AvailableMana:Count$Valid Land.YouCtrl+untapped+hasManaAbility/"
            "Plus.FloatingMana",
            lines,
        )
        self.assertIn("SVar:FloatingMana:Count$ManaPool:All", lines)
        self.assertIn(
            "SVar:RememberedManaValue:Remembered$CardManaCost", lines
        )
        self.assertIn(
            "SVar:RememberedNonland:Count$ValidLibrary Card.IsRemembered+nonLand",
            lines,
        )
        self.assertIn(
            "SVar:Cleanup:DB$ Cleanup | ClearRemembered$ True", lines
        )
        self.assertIn(f"Oracle:{ORACLE}", lines)

    def test_registration_localization_art_and_documentation(self):
        self.assertIn(
            "114 R 水栖形态 @Custom",
            EDITION.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            f"水栖形态|水栖形态|法术|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 水栖形态 | `{0}` 蓝色法术 | `cards/blue/水栖形态.txt` | 114 |",
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
            self.assertEqual((1920, 1559), image.size)

        self.assertTrue(ART.is_file())
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((1024, 747), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
