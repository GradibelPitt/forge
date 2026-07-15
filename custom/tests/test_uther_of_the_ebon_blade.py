import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "white" / "黑锋骑士乌瑟尔.txt"
TOKEN = ROOT / "tokens" / "wb_2_2_apocalypse_knight.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
CARD_ART = ROOT / "cards" / "pictures" / "PH01" / "黑锋骑士乌瑟尔.artcrop.jpg"
TOKEN_ART = ROOT / "tokens" / "pictures" / "wb_2_2_apocalypse_knight.jpg"
CARD_ART_BACKUP = ROOT / "tools" / "card-artwork" / "codex-clipboard-4749bcba-6b7c-485b-b406-4b6d4ae51cdd.png"
TOKEN_ART_BACKUP = ROOT / "tools" / "card-artwork" / "0.jpg"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class UtherOfTheEbonBladeContractTest(unittest.TestCase):
    def test_card_matches_the_requested_planeswalker_characteristics(self):
        self.assertTrue(CARD.is_file(), CARD)
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:黑锋骑士乌瑟尔", text)
        self.assertIn("ManaCost:1 W W W", text)
        self.assertIn("Types:Legendary Planeswalker Uther", text)
        self.assertIn("Loyalty:4", text)

    def test_four_apocalypse_knights_use_a_repeatable_state_trigger_to_win(self):
        self.assertTrue(CARD.is_file(), CARD)
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "T:Mode$ Always | TriggerZones$ Battlefield | "
            "IsPresent$ Creature.YouCtrl+named天启骑士 | PresentCompare$ GE4 | "
            "Execute$ TrigWinGame",
            text,
        )
        self.assertIn("SVar:TrigWinGame:DB$ WinsGame | Defined$ You", text)
        self.assertNotIn("Phase$", text)

    def test_loyalty_abilities_create_one_or_four_apocalypse_knights(self):
        self.assertTrue(CARD.is_file(), CARD)
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "A:AB$ Token | Cost$ AddCounter<1/LOYALTY> | Planeswalker$ True | "
            "TokenScript$ wb_2_2_apocalypse_knight | TokenAmount$ 1 | TokenOwner$ You",
            text,
        )
        self.assertIn(
            "A:AB$ Token | Cost$ SubCounter<7/LOYALTY> | Planeswalker$ True | "
            "Ultimate$ True | TokenScript$ wb_2_2_apocalypse_knight | "
            "TokenAmount$ 4 | TokenOwner$ You",
            text,
        )

    def test_apocalypse_knight_token_has_the_requested_characteristics(self):
        self.assertTrue(TOKEN.is_file(), TOKEN)
        text = TOKEN.read_text(encoding="utf-8")

        self.assertIn("Name:天启骑士", text)
        self.assertIn("ManaCost:no cost", text)
        self.assertIn("Colors:white,black", text)
        self.assertIn("Types:Creature Knight", text)
        self.assertIn("PT:2/2", text)

    def test_card_token_and_source_art_are_registered(self):
        edition = EDITION.read_text(encoding="utf-8")
        self.assertIn("54 M 黑锋骑士乌瑟尔 @Custom", edition)
        card_lines = edition.split("[cards]", 1)[1].splitlines()
        collector_numbers = [line.split(maxsplit=1)[0] for line in card_lines if line.strip()]
        self.assertEqual(len(collector_numbers), len(set(collector_numbers)))
        for path in (CARD_ART, TOKEN_ART, CARD_ART_BACKUP, TOKEN_ART_BACKUP):
            self.assertTrue(path.is_file(), path)

    def test_card_and_token_art_are_landscape_rgb_jpegs(self):
        from PIL import Image

        self.assertTrue(CARD_ART.is_file(), CARD_ART)
        self.assertTrue(TOKEN_ART.is_file(), TOKEN_ART)
        with Image.open(CARD_ART) as image:
            self.assertEqual((993, 725), image.size)
            self.assertEqual("RGB", image.mode)
            self.assertAlmostEqual(1.37, image.width / image.height, places=2)
        with Image.open(TOKEN_ART) as image:
            self.assertEqual((580, 423), image.size)
            self.assertEqual("RGB", image.mode)
            self.assertAlmostEqual(1.37, image.width / image.height, places=2)

    def test_zh_cn_display_text_matches_the_card(self):
        expected = (
            "黑锋骑士乌瑟尔|黑锋骑士乌瑟尔|传奇鹏洛客～乌瑟尔|"
            "当你操控四个或更多天启骑士时，你赢得这盘游戏。\\n"
            "+1：派出一个名为“天启骑士”的2/2白黑双色骑士衍生生物。\\n"
            "-7：派出四个名为“天启骑士”的2/2白黑双色骑士衍生生物。"
        )
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())


if __name__ == "__main__":
    unittest.main()
