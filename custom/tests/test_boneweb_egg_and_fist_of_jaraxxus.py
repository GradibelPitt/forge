import hashlib
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BONEWEB_EGG = ROOT / "cards" / "black" / "骨网之卵.txt"
FIST_OF_JARAXXUS = ROOT / "cards" / "multicolor" / "加拉克苏斯之拳.txt"
SPIDER = ROOT / "tokens" / "bg_2_1_spider.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
BONEWEB_ART = ROOT / "cards" / "pictures" / "PH01" / "骨网之卵.artcrop.jpg"
FIST_ART = ROOT / "cards" / "pictures" / "PH01" / "加拉克苏斯之拳.artcrop.jpg"
BONEWEB_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-5356a624-4923-40f4-8145-b51d13417541.png"
)
FIST_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-27354cae-47e7-4370-90fa-e3d59ebb1a70.png"
)
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


class BonewebEggAndFistOfJaraxxusContractTest(unittest.TestCase):
    def test_boneweb_egg_has_the_requested_characteristics_and_madness(self):
        self.assertTrue(BONEWEB_EGG.is_file(), BONEWEB_EGG)
        text = BONEWEB_EGG.read_text(encoding="utf-8")

        self.assertIn("Name:骨网之卵", text)
        self.assertIn("ManaCost:1 B", text)
        self.assertIn("Types:Creature Spider", text)
        self.assertIn("PT:0/2", text)
        self.assertIn("K:Reach", text)
        self.assertIn("K:Madness:0", text)
        self.assertIn(
            "T:Mode$ ChangesZone | Origin$ Battlefield | Destination$ Graveyard | "
            "ValidCard$ Card.Self | Execute$ TrigToken",
            text,
        )
        self.assertIn(
            "SVar:TrigToken:DB$ Token | TokenAmount$ 2 | "
            "TokenScript$ bg_2_1_spider | TokenOwner$ You",
            text,
        )
        self.assertIn(
            "Oracle:延势\\n当此生物死去时，派出两个2/1黑绿双色蜘蛛衍生生物。\\n疯魔{0}",
            text,
        )

    def test_spider_token_is_two_one_black_green_and_has_no_extra_abilities(self):
        self.assertTrue(SPIDER.is_file(), SPIDER)
        text = SPIDER.read_text(encoding="utf-8")

        self.assertIn("Name:蜘蛛衍生物", text)
        self.assertIn("ManaCost:no cost", text)
        self.assertIn("Colors:black,green", text)
        self.assertIn("Types:Creature Spider", text)
        self.assertIn("PT:2/1", text)
        self.assertNotIn("K:", text)

    def test_fist_of_jaraxxus_uses_the_native_random_target_pattern(self):
        self.assertTrue(FIST_OF_JARAXXUS.is_file(), FIST_OF_JARAXXUS)
        text = FIST_OF_JARAXXUS.read_text(encoding="utf-8")

        self.assertIn("Name:加拉克苏斯之拳", text)
        self.assertIn("ManaCost:1 B R", text)
        self.assertIn("Types:Instant", text)
        self.assertIn("K:Madness:0", text)
        self.assertIn("A:SP$ DealDamage", text)
        self.assertIn("NumDmg$ 4", text)
        self.assertIn("ValidTgts$ Creature.OppCtrl", text)
        self.assertIn("TargetsAtRandom$ True", text)
        self.assertNotIn("TgtPrompt$", text)
        self.assertIn(
            "Oracle:加拉克苏斯之拳对一个随机选择的由对手操控的生物造成4点伤害。\\n疯魔{0}",
            text,
        )

    def test_cards_are_registered_with_unique_numbers_and_standard_crop_art(self):
        edition = EDITION.read_text(encoding="utf-8")
        self.assertIn("61 C 骨网之卵 @Custom", edition)
        self.assertIn("62 C 加拉克苏斯之拳 @Custom", edition)
        card_lines = edition.split("[cards]", 1)[1].splitlines()
        collector_numbers = [line.split(maxsplit=1)[0] for line in card_lines if line.strip()]
        self.assertEqual(len(collector_numbers), len(set(collector_numbers)))

        for path in (BONEWEB_ART, FIST_ART, BONEWEB_BACKUP, FIST_BACKUP):
            self.assertTrue(path.is_file(), path)

    def test_original_art_is_backed_up_and_crops_are_landscape_rgb_jpegs(self):
        from PIL import Image

        for path in (BONEWEB_ART, FIST_ART, BONEWEB_BACKUP, FIST_BACKUP):
            self.assertTrue(path.is_file(), path)

        self.assertEqual(
            "B9186A70498A4EC69514AFB20EBF6799342DD7AA75B4F4A1A8B1A635AF347246",
            sha256(BONEWEB_BACKUP),
        )
        self.assertEqual(
            "279C0BB5E65B052CB2CDBA50C96A8100A2B64FECF709B4C0725EB53C311364FD",
            sha256(FIST_BACKUP),
        )

        with Image.open(BONEWEB_ART) as image:
            self.assertEqual((395, 288), image.size)
            self.assertEqual("RGB", image.mode)
            self.assertAlmostEqual(1.37, image.width / image.height, places=2)
        with Image.open(FIST_ART) as image:
            self.assertEqual((998, 728), image.size)
            self.assertEqual("RGB", image.mode)
            self.assertAlmostEqual(1.37, image.width / image.height, places=2)

    def test_zh_cn_display_text_matches_the_requested_oracle(self):
        lines = ZH_CN.read_text(encoding="utf-8").splitlines()
        self.assertIn(
            "骨网之卵|骨网之卵|生物～蜘蛛|"
            "延势\\n当此生物死去时，派出两个2/1黑绿双色蜘蛛衍生生物。\\n疯魔{0}",
            lines,
        )
        self.assertIn(
            "加拉克苏斯之拳|加拉克苏斯之拳|瞬间|"
            "加拉克苏斯之拳对一个随机选择的由对手操控的生物造成4点伤害。\\n疯魔{0}",
            lines,
        )


if __name__ == "__main__":
    unittest.main()
