import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "multicolor" / "污染者玛法里奥.txt"
INSECT = ROOT / "tokens" / "g_0_3_insect_reach.txt"
SPIDER = ROOT / "tokens" / "b_1_1_spider_deathtouch.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "污染者玛法里奥.artcrop.jpg"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-bd3453b3-1e08-4c68-a214-0207dd02fa75.png"
)
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class MalfurionTheCorruptorContractTest(unittest.TestCase):
    def read_card(self):
        self.assertTrue(CARD.is_file(), CARD)
        return CARD.read_text(encoding="utf-8")

    def test_card_matches_the_requested_planeswalker_characteristics(self):
        text = self.read_card()

        self.assertIn("Name:污染者玛法里奥", text)
        self.assertIn("ManaCost:1 B G", text)
        self.assertIn("Types:Legendary Planeswalker Malfurion", text)
        self.assertIn("Loyalty:3", text)

    def test_malfurion_becomes_a_three_three_trampling_beast_during_your_turn(self):
        text = self.read_card()

        self.assertIn(
            "S:Mode$ Continuous | Affected$ Permanent.Self+counters_GE1_LOYALTY | "
            "Condition$ PlayerTurn | AddType$ Creature & Beast | RemoveCardTypes$ True | "
            "SetPower$ 3 | SetToughness$ 3 | AddKeyword$ Trample",
            text,
        )

    def test_plus_one_puts_a_counter_on_up_to_one_target_beast(self):
        text = self.read_card()

        self.assertIn(
            "A:AB$ PutCounter | Cost$ AddCounter<1/LOYALTY> | Planeswalker$ True | "
            "CounterType$ P1P1 | CounterNum$ 1 | TargetMin$ 0 | TargetMax$ 1 | "
            "ValidTgts$ Beast",
            text,
        )

    def test_minus_two_chooses_between_the_requested_insect_and_spider(self):
        text = self.read_card()

        self.assertIn(
            "A:AB$ Charm | Cost$ SubCounter<2/LOYALTY> | Planeswalker$ True | "
            "Choices$ DBInsect,DBSpider",
            text,
        )
        self.assertIn(
            "SVar:DBInsect:DB$ Token | TokenScript$ g_0_3_insect_reach | "
            "TokenOwner$ You",
            text,
        )
        self.assertIn(
            "SVar:DBSpider:DB$ Token | TokenScript$ b_1_1_spider_deathtouch | "
            "TokenOwner$ You",
            text,
        )

    def test_tokens_have_the_requested_characteristics(self):
        self.assertTrue(INSECT.is_file(), INSECT)
        self.assertTrue(SPIDER.is_file(), SPIDER)
        insect = INSECT.read_text(encoding="utf-8")
        spider = SPIDER.read_text(encoding="utf-8")

        self.assertIn("Colors:green", insect)
        self.assertIn("Types:Creature Insect", insect)
        self.assertIn("PT:0/3", insect)
        self.assertIn("K:Reach", insect)
        self.assertIn("Colors:black", spider)
        self.assertIn("Types:Creature Spider", spider)
        self.assertIn("PT:1/1", spider)
        self.assertIn("K:Deathtouch", spider)

    def test_card_and_source_art_are_registered(self):
        edition = EDITION.read_text(encoding="utf-8")
        self.assertIn("59 M 污染者玛法里奥 @Custom", edition)
        card_lines = edition.split("[cards]", 1)[1].splitlines()
        collector_numbers = [line.split(maxsplit=1)[0] for line in card_lines if line.strip()]
        self.assertEqual(len(collector_numbers), len(set(collector_numbers)))
        self.assertTrue(ART_BACKUP.is_file(), ART_BACKUP)
        self.assertTrue(ART.is_file(), ART)

    def test_standard_art_crop_is_landscape_rgb_jpeg(self):
        from PIL import Image

        self.assertTrue(ART.is_file(), ART)
        with Image.open(ART) as image:
            self.assertEqual((810, 591), image.size)
            self.assertEqual("RGB", image.mode)
            self.assertAlmostEqual(1.37, image.width / image.height, places=2)

    def test_zh_cn_display_text_matches_the_requested_oracle(self):
        expected = (
            "污染者玛法里奥|污染者玛法里奥|传奇鹏洛客～玛法里奥|"
            "于你的回合中，只要玛法里奥上有一个或数个忠诚指示物，他便是3/3野兽生物，且具有践踏异能。\\n"
            "+1：在至多一个目标野兽上放置一个+1/+1指示物。\\n"
            "-2：选择一项～\\n"
            "• 派出一个0/3绿色，具有延势异能的昆虫衍生生物。\\n"
            "• 派出一个1/1黑色，具有死触异能的蜘蛛衍生生物。"
        )
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())


if __name__ == "__main__":
    unittest.main()
