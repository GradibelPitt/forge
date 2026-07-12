import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "black" / "咒怨之墓.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "咒怨之墓.artcrop.jpg"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "1024px-Cursed_Catacombs_full.jpg"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class CursedCatacombsContractTest(unittest.TestCase):
    def test_card_discovers_from_its_controllers_library_and_marks_the_choice(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:咒怨之墓", text)
        self.assertIn("ManaCost:0", text)
        self.assertIn("Colors:black", text)
        self.assertIn("Types:Sorcery", text)
        self.assertIn(
            "A:SP$ CardDiscover | Defined$ You | Source$ Library | SourceController$ You | "
            "ValidCards$ Card | OptionCount$ 3 | Destination$ Hand | RememberChosen$ True | "
            "SubAbility$ DelayedDiscard",
            text,
        )

    def test_end_step_discards_only_the_still_held_discovered_card(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "SVar:DelayedDiscard:DB$ DelayedTrigger | Mode$ Phase | Phase$ End of Turn | "
            "ValidPlayer$ You | RememberObjects$ Remembered | Execute$ DiscardRemembered | "
            "SubAbility$ Cleanup",
            text,
        )
        self.assertIn(
            "SVar:DiscardRemembered:DB$ Discard | Defined$ You | Mode$ Defined | "
            "DefinedCards$ ValidHand Card.IsTriggerRemembered",
            text,
        )
        self.assertIn("SVar:Cleanup:DB$ Cleanup | ClearRemembered$ True", text)

    def test_card_is_registered_with_standard_crop_art(self):
        self.assertIn("30 R 咒怨之墓 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())

    def test_standard_art_crop_is_landscape_rgb_jpeg(self):
        from PIL import Image

        with Image.open(ART) as image:
            self.assertEqual((1024, 748), image.size)
            self.assertEqual("RGB", image.mode)

    def test_zh_cn_display_text_matches_the_requested_description(self):
        expected = "咒怨之墓|咒怨之墓|法术|咒怨之墓是黑色。\\n发现你牌堆中的一张牌，在你的回合结束时，弃掉以此法获得的且仍然在你手牌中的牌。"
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())


if __name__ == "__main__":
    unittest.main()
