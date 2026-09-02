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
ORACLE_CATALOG = ROOT / "DIY卡牌_游戏内Oracle_非测试卡.txt"

ZH_ORACLE = (
    "行侣～你起始套牌中每张牌的法术力值均等于或小于3。\\n"
    "延势\\n"
    "除非你已经进行过五个或更多回合，否则你不能施放主厨奈瑟雷克。\\n"
    "当你施放主厨奈瑟雷克时，如果此牌为你的行侣，则从你的牌库顶开始展示牌，直到展示出十张各具有基本地类别的地牌为止。将这些地牌放进战场，其余则以随机顺序置于你的牌库底。"
)

EN_ORACLE = (
    "Companion — Each card in your starting deck has mana value 3 or less.\\n"
    "Reach\\n"
    "You can't cast CARDNAME unless you have taken five or more turns.\\n"
    "When you cast CARDNAME, if it is your companion, reveal cards from the top of your "
    "library until you reveal ten land cards that each have a basic land type. Put those "
    "land cards onto the battlefield and the rest on the bottom of your library in a "
    "random order."
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
        self.assertIn(
            "TriggerDescription$ When you cast CARDNAME, if it is your companion, reveal "
            "cards from the top of your library until you reveal ten land cards that each "
            "have a basic land type. Put those land cards onto the battlefield and the rest "
            "on the bottom of your library in a random order.",
            trigger,
        )

        reveal = next(line for line in lines if line.startswith("SVar:TrigLands:"))
        self.assertEqual(
            "SVar:TrigLands:DB$ DigUntil | Amount$ 10 | Valid$ Land.hasABasicLandType | "
            "ValidDescription$ land | FoundDestination$ Battlefield | RevealedDestination$ "
            "Library | RevealedLibraryPosition$ -1 | RevealRandomOrder$ True",
            reveal,
        )
        self.assertNotIn("ChangeZone", reveal)
        self.assertIn(f"Oracle:{EN_ORACLE}", lines)

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
        self.assertIn(
            "从牌库顶开始展示牌，直到展示出十张各具有基本地类别的地牌为止",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )
        self.assertIn(
            "当你施放主厨奈瑟雷克时，如果此牌为你的行侣，则"
            "从你的牌库顶开始展示牌，直到展示出十张各具有基本地类别的地牌为止。"
            "将这些地牌放进战场，其余则以随机顺序置于你的牌库底。",
            ORACLE_CATALOG.read_text(encoding="utf-8"),
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
