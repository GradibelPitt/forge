import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "green" / "主厨奈瑟雷克.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-ebac2d1a-c4ba-47c5-afc6-3da94a46c8c7.png"
)
ART = ROOT / "cards" / "pictures" / "PH01" / "主厨奈瑟雷克.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ZH_ORACLE = (
    "行侣～你起始套牌中每张牌的总法术力费用均为3或更少。\\n"
    "延势\\n"
    "除非你已经进行过五个或更多回合，否则你不能施放主厨奈瑟雷克。\\n"
    "当你施放主厨奈瑟雷克时，如果此牌为你的行侣，从你的牌堆中搜寻至多十张地牌，并将他们放进战场。"
)


class ChefNethrazekContractTest(unittest.TestCase):
    def test_characteristics_companion_and_turn_gate(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:主厨奈瑟雷克", lines)
        self.assertIn("ManaCost:1 G G", lines)
        self.assertIn("Types:Legendary Creature Spider", lines)
        self.assertIn("PT:3/3", lines)
        self.assertIn(
            "K:Companion:Card.cmcLE3:Each card in your starting deck has mana value 3 or less.",
            lines,
        )
        self.assertIn("K:Reach", lines)

        turn_gate = next(line for line in lines if line.startswith("S:Mode$ CantBeCast"))
        self.assertIn("ValidCard$ Card.Self", turn_gate)
        self.assertIn("EffectZone$ All", turn_gate)
        self.assertIn("CheckSVar$ Count$YourTurns", turn_gate)
        self.assertIn("SVarCompare$ LT5", turn_gate)
        self.assertIn(
            "Description$ You can't cast CARDNAME unless you have taken five or more turns.",
            turn_gate,
        )

    def test_cast_trigger_only_fires_for_the_chosen_companion(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        trigger = next(line for line in lines if line.startswith("T:Mode$ SpellCast"))
        self.assertIn("ValidCard$ Card.Self+IsCompanion", trigger)
        self.assertIn("TriggerZones$ Stack", trigger)
        self.assertIn("Execute$ TrigLands", trigger)

        search = next(line for line in lines if line.startswith("SVar:TrigLands:"))
        self.assertIn("DB$ ChangeZone", search)
        self.assertIn("Origin$ Library", search)
        self.assertIn("Destination$ Battlefield", search)
        self.assertIn("ChangeType$ Land", search)
        self.assertIn("ChangeNum$ 10", search)
        self.assertIn("NoShuffle$ True", search)
        self.assertNotIn("Tapped$ True", search)

    def test_registration_localization_documentation_and_art(self):
        self.assertIn(
            "99 M 主厨奈瑟雷克 @Custom",
            EDITION.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            f"主厨奈瑟雷克|主厨奈瑟雷克|传奇生物～蜘蛛|{ZH_ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 主厨奈瑟雷克 | `{1}{G}{G}`，3/3 传奇生物～蜘蛛 | "
            "`cards/green/主厨奈瑟雷克.txt` | 99 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        self.assertTrue(ART_BACKUP.is_file())
        self.assertEqual(
            "A882BA5FC13CBD736B6EBDFA4BE664C4E58E2421ED35E6C3A2CB72F839D9F4BA",
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        with Image.open(ART_BACKUP) as image:
            self.assertEqual("PNG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((1024, 1455), image.size)

        self.assertTrue(ART.is_file())
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((1024, 748), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
