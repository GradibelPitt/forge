import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "black" / "鲜血掠夺者古尔丹.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "鲜血掠夺者古尔丹.artcrop.jpg"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Bloodreaver_Gul'dan_full.jpg"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class BloodreaverGuldanContractTest(unittest.TestCase):
    def test_card_matches_the_requested_planeswalker_characteristics(self):
        self.assertTrue(CARD.is_file(), CARD)
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:鲜血掠夺者古尔丹", text)
        self.assertIn("ManaCost:2 B B", text)
        self.assertIn("Types:Legendary Planeswalker Guldan", text)
        self.assertIn("Loyalty:4", text)

    def test_loyalty_abilities_match_the_requested_effects(self):
        self.assertTrue(CARD.is_file(), CARD)
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "A:AB$ DealDamage | Cost$ AddCounter<1/LOYALTY> | Planeswalker$ True | "
            "ValidTgts$ Any | NumDmg$ 3 | SubAbility$ DBGainLife",
            text,
        )
        self.assertIn("SVar:DBGainLife:DB$ GainLife | Defined$ You | LifeAmount$ 3", text)
        self.assertIn(
            "A:AB$ ChangeZone | Cost$ SubCounter<2/LOYALTY> | Planeswalker$ True | "
            "Origin$ Graveyard | Destination$ Battlefield | ValidTgts$ Demon.YouCtrl",
            text,
        )
        self.assertIn(
            "A:AB$ ChangeZone | Cost$ SubCounter<8/LOYALTY> | Planeswalker$ True | "
            "Ultimate$ True | Origin$ Library | Destination$ Battlefield | "
            "ChangeType$ Demon | ChangeNum$ XFetch | Shuffle$ True",
            text,
        )
        self.assertIn("SVar:XFetch:Count$ValidLibrary Demon.YouCtrl", text)

    def test_card_is_registered_with_standard_crop_art(self):
        self.assertIn("52 M 鲜血掠夺者古尔丹 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())

    def test_standard_art_crop_is_landscape_rgb_jpeg(self):
        from PIL import Image

        self.assertTrue(ART.is_file(), ART)
        with Image.open(ART) as image:
            self.assertEqual((1820, 1328), image.size)
            self.assertEqual("RGB", image.mode)
            self.assertAlmostEqual(1.37, image.width / image.height, places=2)

    def test_zh_cn_display_text_matches_the_card(self):
        expected = (
            "鲜血掠夺者古尔丹|鲜血掠夺者古尔丹|传奇鹏洛客～古尔丹|"
            "+1：古尔丹对任意一个目标造成3点伤害。你获得3点生命。\\n"
            "-2：将目标恶魔牌从你的坟墓场移回战场。\\n"
            "-8：从你的牌库中搜寻任意数量的恶魔牌，将它们放进战场，然后洗牌。\\n"
            "本牌可用作你的指挥官。"
        )
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())


if __name__ == "__main__":
    unittest.main()
