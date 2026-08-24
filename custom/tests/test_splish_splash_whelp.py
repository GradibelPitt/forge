import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "green" / "戏水雏龙.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
SOURCE_ART = ROOT / "tools" / "card-artwork" / "WW_819_Splish_Splash_Whelp.png"
ART = ROOT / "cards" / "pictures" / "PH01" / "戏水雏龙.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ADDITIONAL_COST = (
    "作为施放此咒语的额外费用，你可以请援龙。"
    "（你可以选择一个由你操控的龙，或从你手上展示一张龙牌。）"
)
TRIGGER_TEXT = (
    "当戏水雏龙进战场时，若你施放此咒语时请援了龙，"
    "则从你的牌库中搜寻一张基本地牌，将该牌横置放进战场，然后洗牌。"
)
ORACLE = f"{ADDITIONAL_COST}\\n{TRIGGER_TEXT}"
SOURCE_ART_SHA256 = "9167E35B0F13D1648294D57880E0BCCC36106807B3615E2E8883B2693F263B40"


class SplishSplashWhelpContractTest(unittest.TestCase):
    def test_beholds_a_dragon_and_searches_a_basic_land_tapped(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:戏水雏龙", lines)
        self.assertIn("ManaCost:1 G", lines)
        self.assertIn("Types:Creature Dragon", lines)
        self.assertIn("PT:2/1", lines)

        optional_cost = next(line for line in lines if line.startswith("S:Mode$ OptionalCost"))
        self.assertIn("EffectZone$ All", optional_cost)
        self.assertIn("ValidCard$ Card.Self", optional_cost)
        self.assertIn("ValidSA$ Spell", optional_cost)
        self.assertIn("Cost$ Behold<1/Dragon>", optional_cost)
        self.assertNotIn("Reveal<", optional_cost)
        self.assertIn(f"Description$ {ADDITIONAL_COST}", optional_cost)

        trigger = next(line for line in lines if line.startswith("T:Mode$ ChangesZone"))
        self.assertIn("Origin$ Any", trigger)
        self.assertIn("Destination$ Battlefield", trigger)
        self.assertIn("ValidCard$ Card.Self", trigger)
        self.assertIn("CheckSVar$ CastSA>Count$OptionalGenericCostPaid.1.0", trigger)
        self.assertIn("NoResolvingCheck$ True", trigger)
        self.assertIn("Execute$ TrigLand", trigger)
        self.assertIn(f"TriggerDescription$ {TRIGGER_TEXT}", trigger)

        search = next(line for line in lines if line.startswith("SVar:TrigLand:DB$ ChangeZone"))
        self.assertIn("Origin$ Library", search)
        self.assertIn("Destination$ Battlefield", search)
        self.assertIn("ChangeType$ Land.Basic", search)
        self.assertIn("ChangeTypeDesc$ basic land", search)
        self.assertIn("ChangeNum$ 1", search)
        self.assertIn("Tapped$ True", search)
        self.assertNotIn("Optional$ True", search)
        self.assertIn(f"Oracle:{ORACLE}", lines)

    def test_registration_localization_art_and_documentation(self):
        self.assertIn(
            "122 R 戏水雏龙 @Caroline Gariba",
            EDITION.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            f"戏水雏龙|戏水雏龙|生物～龙|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 戏水雏龙 | `{1}{G}`，2/1 生物～龙 | `cards/green/戏水雏龙.txt` | 122 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        self.assertTrue(SOURCE_ART.is_file())
        self.assertEqual(
            SOURCE_ART_SHA256,
            hashlib.sha256(SOURCE_ART.read_bytes()).hexdigest().upper(),
        )
        with Image.open(SOURCE_ART) as image:
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
