import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "blue" / "冰霜女巫吉安娜.txt"
TOKEN = ROOT / "tokens" / "u_3_6_elemental.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "冰霜女巫吉安娜.artcrop.jpg"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Art_ICC_833.png"
EMBLEM_ART = ROOT / "tokens" / "pictures" / "emblem_frost_lich_jaina.png"
SOURCE_ART = Path(r"C:\Users\Marsh\Desktop\Art_ICC_833.png")
TOKEN_ART = ROOT / "tokens" / "pictures" / "u_3_6_elemental.jpg"
TOKEN_ART_BACKUP = ROOT / "tools" / "card-artwork" / "images.jpg"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class FrostLichJainaContractTest(unittest.TestCase):
    def read_card(self):
        self.assertTrue(CARD.is_file(), CARD)
        return CARD.read_text(encoding="utf-8")

    def test_card_matches_the_requested_planeswalker_characteristics(self):
        text = self.read_card()

        self.assertIn("Name:冰霜女巫吉安娜", text)
        self.assertIn("ManaCost:2 U U", text)
        self.assertIn("Types:Legendary Planeswalker Jaina", text)
        self.assertIn("Loyalty:3", text)

    def test_elementals_controlled_by_jainas_controller_have_lifelink(self):
        text = self.read_card()

        self.assertIn(
            "S:Mode$ Continuous | Affected$ Creature.Elemental+YouCtrl | "
            "AddKeyword$ Lifelink",
            text,
        )

    def test_plus_one_taps_a_permanent_and_adds_a_stun_counter(self):
        text = self.read_card()

        self.assertIn(
            "A:AB$ Tap | Cost$ AddCounter<1/LOYALTY> | Planeswalker$ True | "
            "ValidTgts$ Permanent | SubAbility$ DBStun",
            text,
        )
        self.assertIn(
            "SVar:DBStun:DB$ PutCounter | Defined$ Targeted | "
            "CounterType$ Stun | CounterNum$ 1",
            text,
        )

    def test_minus_one_creates_an_elemental_if_the_damaged_creature_dies(self):
        text = self.read_card()

        self.assertIn(
            "A:AB$ DealDamage | Cost$ SubCounter<1/LOYALTY> | Planeswalker$ True | "
            "ValidTgts$ Any | NumDmg$ 1 | RememberDamaged$ True | "
            "SubAbility$ DBDelayedTrigger",
            text,
        )
        self.assertIn(
            "SVar:DBDelayedTrigger:DB$ DelayedTrigger | Mode$ ChangesZone | "
            "RememberObjects$ Remembered | ValidCard$ Card.IsTriggerRemembered | "
            "Origin$ Battlefield | Destination$ Graveyard | ThisTurn$ True | "
            "Execute$ TrigToken | SubAbility$ DBCleanup",
            text,
        )
        self.assertIn(
            "SVar:TrigToken:DB$ Token | TokenScript$ u_3_6_elemental",
            text,
        )
        self.assertIn("SVar:DBCleanup:DB$ Cleanup | ClearRemembered$ True", text)

    def test_ultimate_creates_a_lifelink_emblem_that_damages_tapped_creatures(self):
        text = self.read_card()

        self.assertIn(
            "A:AB$ Effect | Cost$ SubCounter<6/LOYALTY> | Planeswalker$ True | "
            "Ultimate$ True | Name$ Emblem — 冰霜女巫吉安娜 | "
            "Image$ emblem_frost_lich_jaina | StaticAbilities$ EmblemLifelink | "
            "Triggers$ EmblemTapTrigger | Duration$ Permanent",
            text,
        )
        self.assertIn(
            "SVar:EmblemLifelink:Mode$ Continuous | Affected$ Card.Self | "
            "AffectedZone$ Command | AddKeyword$ Lifelink",
            text,
        )
        self.assertIn(
            "SVar:EmblemTapTrigger:Mode$ Taps | ValidCard$ Creature.OppCtrl | "
            "Execute$ EmblemDamage",
            text,
        )
        self.assertIn(
            "SVar:EmblemDamage:DB$ DealDamage | Defined$ TriggeredCardLKICopy | NumDmg$ 3",
            text,
        )

    def test_blue_three_six_elemental_token_is_vanilla(self):
        self.assertTrue(TOKEN.is_file(), TOKEN)
        self.assertEqual(
            "Name:Elemental Token\n"
            "ManaCost:no cost\n"
            "Colors:blue\n"
            "Types:Creature Elemental\n"
            "PT:3/6\n"
            "Oracle:\n",
            TOKEN.read_text(encoding="utf-8"),
        )

    def test_elemental_token_uses_the_supplied_landscape_art(self):
        from PIL import Image

        self.assertTrue(TOKEN_ART_BACKUP.is_file(), TOKEN_ART_BACKUP)
        self.assertTrue(TOKEN_ART.is_file(), TOKEN_ART)
        with Image.open(TOKEN_ART) as image:
            self.assertEqual((498, 363), image.size)
            self.assertEqual("RGB", image.mode)
            self.assertAlmostEqual(1.37, image.width / image.height, places=2)

    def test_card_is_registered_with_standard_crop_art(self):
        self.assertIn("53 M 冰霜女巫吉安娜 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertTrue(ART_BACKUP.is_file(), ART_BACKUP)
        self.assertTrue(ART.is_file(), ART)

    def test_standard_art_crop_is_landscape_rgb_jpeg(self):
        from PIL import Image

        self.assertTrue(ART.is_file(), ART)
        with Image.open(ART) as image:
            self.assertEqual((512, 374), image.size)
            self.assertEqual("RGB", image.mode)
            self.assertAlmostEqual(1.37, image.width / image.height, places=2)

    def test_emblem_reuses_the_supplied_jaina_artwork(self):
        self.assertTrue(SOURCE_ART.is_file(), SOURCE_ART)
        self.assertTrue(EMBLEM_ART.is_file(), EMBLEM_ART)
        self.assertEqual(SOURCE_ART.read_bytes(), EMBLEM_ART.read_bytes())

    def test_zh_cn_display_text_matches_the_requested_oracle(self):
        expected = (
            "冰霜女巫吉安娜|冰霜女巫吉安娜|传奇鹏洛客～吉安娜|"
            "由你操控的元素具有系命异能。\\n"
            "+1：横置目标永久物。在其上放置一个晕眩指示物。\\n"
            "-1：吉安娜对任意一个目标造成1点伤害。当一个本回合中曾以此法受到伤害的生物死去时，"
            "派出一个3/6蓝色元素衍生生物。\\n"
            "-6：你获得具有以下异能的徽记～「系命。每当一个由对手操控的生物成为横置时，"
            "此徽记对该生物造成3点伤害。」"
        )
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())


if __name__ == "__main__":
    unittest.main()
