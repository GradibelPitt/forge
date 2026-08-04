import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "multicolor" / "虚空之影瓦莉拉.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "虚空之影瓦莉拉.artcrop.jpg"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-1669f305-5b63-4917-8a96-7b89cc538c49.png"
)
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class ShadowOfTheVoidValeeraContractTest(unittest.TestCase):
    def test_card_matches_the_requested_planeswalker_characteristics(self):
        self.assertTrue(CARD.is_file(), CARD)
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:虚空之影瓦莉拉", text)
        self.assertIn("ManaCost:3 U B", text)
        self.assertIn("Types:Legendary Planeswalker Valeera", text)
        self.assertIn("Loyalty:4", text)

    def test_enters_with_protection_for_valeera_and_her_controller(self):
        self.assertTrue(CARD.is_file(), CARD)
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "T:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | "
            "ValidCard$ Card.Self | Execute$ TrigProtect",
            text,
        )
        self.assertIn(
            "SVar:TrigProtect:DB$ Pump | Defined$ You | Duration$ UntilYourNextTurn | "
            "KW$ Protection from everything | SubAbility$ DBProtectValeera",
            text,
        )
        self.assertIn(
            "SVar:DBProtectValeera:DB$ Pump | Defined$ Self | "
            "Duration$ UntilYourNextTurn | KW$ Protection from everything",
            text,
        )

    def test_plus_one_loots_then_makes_up_to_one_creature_unblockable(self):
        self.assertTrue(CARD.is_file(), CARD)
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "A:AB$ Draw | Cost$ AddCounter<1/LOYALTY> | Planeswalker$ True | "
            "Defined$ You | NumCards$ 1 | SubAbility$ DBDiscard",
            text,
        )
        self.assertIn(
            "SVar:DBDiscard:DB$ Discard | Defined$ You | NumCards$ 1 | "
            "Mode$ TgtChoose | SubAbility$ DBUnblockable",
            text,
        )
        self.assertIn(
            "SVar:DBUnblockable:DB$ Effect | TargetMin$ 0 | TargetMax$ 1 | "
            "ValidTgts$ Creature | RememberObjects$ Targeted | "
            "ExileOnMoved$ Battlefield | StaticAbilities$ Unblockable",
            text,
        )
        self.assertIn(
            "SVar:Unblockable:Mode$ CantBlockBy | ValidAttacker$ Card.IsRemembered",
            text,
        )

    def test_minus_two_copies_only_the_next_spell_cast_this_turn(self):
        self.assertTrue(CARD.is_file(), CARD)
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "A:AB$ DelayedTrigger | Cost$ SubCounter<2/LOYALTY> | "
            "Planeswalker$ True | AILogic$ SpellCopy | Mode$ SpellCast | "
            "ValidCard$ Card | ValidActivatingPlayer$ You | ThisTurn$ True | "
            "Execute$ TrigCopy",
            text,
        )
        self.assertIn(
            "SVar:TrigCopy:DB$ CopySpellAbility | Defined$ TriggeredSpellAbility | "
            "AILogic$ Always | Amount$ 1 | MayChooseTarget$ True",
            text,
        )

    def test_card_is_registered_with_standard_crop_art(self):
        self.assertIn("55 M 虚空之影瓦莉拉 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertTrue(ART_BACKUP.is_file(), ART_BACKUP)
        self.assertTrue(ART.is_file(), ART)

    def test_standard_art_crop_is_landscape_rgb_jpeg(self):
        from PIL import Image

        self.assertTrue(ART.is_file(), ART)
        with Image.open(ART) as image:
            self.assertEqual((495, 361), image.size)
            self.assertEqual("RGB", image.mode)
            self.assertAlmostEqual(1.37, image.width / image.height, places=2)

    def test_zh_cn_display_text_matches_the_card(self):
        expected = (
            "虚空之影瓦莉拉|虚空之影瓦莉拉|传奇鹏洛客～瓦莉拉|"
            "当瓦莉拉进场时，直到你的下一个回合，你和瓦莉拉获得反一切保护异能。\\n"
            "+1：抓一张牌，然后弃一张牌。至多一个目标生物本回合不能被阻挡。\\n"
            "-2：本回合中，当你施放你的下一个咒语时，将它复制。你可以为该复制品选择新的目标。\\n"
            "本牌可用作你的指挥官。"
        )
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())


if __name__ == "__main__":
    unittest.main()
