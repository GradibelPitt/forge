import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "black" / "尼鲁巴蛛网领主.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "尼鲁巴蛛网领主.full.jpg"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "282017.jpg"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class NerubianWeblordContractTest(unittest.TestCase):
    def test_card_matches_the_complete_card_image_effect(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:尼鲁巴蛛网领主", text)
        self.assertIn("ManaCost:1 B", text)
        self.assertIn("Types:Creature Spider Undead", text)
        self.assertIn("PT:1/4", text)
        self.assertIn(
            "S:Mode$ RaiseCost | ValidCard$ Creature | Activator$ Opponent | Type$ Spell | "
            "Amount$ 2 | Description$ Creature spells your opponents cast cost {2} more to cast.",
            text,
        )
        self.assertIn("Oracle:对手的生物咒语施放费用增加{2}。", text)

    def test_card_is_registered_with_the_complete_card_image(self):
        edition = EDITION.read_text(encoding="utf-8")

        self.assertIn("19 C 尼鲁巴蛛网领主", edition)
        self.assertNotIn("19 C 尼鲁巴蛛网领主 @Custom", edition)
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())

    def test_zh_cn_display_text_is_complete(self):
        expected = "尼鲁巴蛛网领主|尼鲁巴蛛网领主|生物～蜘蛛／亡灵|对手的生物咒语施放费用增加{2}。"

        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())


if __name__ == "__main__":
    unittest.main()
