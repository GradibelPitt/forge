import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "multicolor" / "栉龙.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "栉龙.artcrop.jpg"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "163557.png"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class CompsognathusContractTest(unittest.TestCase):
    def test_card_has_the_requested_hybrid_cost_stats_and_madness(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:栉龙", text)
        self.assertIn("ManaCost:UB", text)
        self.assertIn("Types:Creature Dinosaur", text)
        self.assertIn("PT:1/2", text)
        self.assertIn("K:Madness:0", text)

    def test_enter_the_battlefield_draw_is_remembered_and_death_discards_only_that_card(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "T:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | ValidCard$ Card.Self | "
            "Execute$ TrigDraw",
            text,
        )
        self.assertIn("SVar:TrigDraw:DB$ Draw | Defined$ You | NumCards$ 1 | RememberDrawn$ True", text)
        self.assertIn(
            "T:Mode$ ChangesZone | Origin$ Battlefield | Destination$ Graveyard | ValidCard$ Card.Self | "
            "Execute$ TrigDiscard",
            text,
        )
        self.assertIn(
            "SVar:TrigDiscard:DB$ Discard | Defined$ You | Mode$ Defined | "
            "DefinedCards$ ValidHand Card.IsRemembered",
            text,
        )

    def test_card_is_registered_with_standard_crop_art(self):
        self.assertIn("31 R 栉龙 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())

    def test_zh_cn_display_text_matches_the_requested_description(self):
        expected = "栉龙|栉龙|生物～恐龙|当栉龙进战场时，抓一张牌。\\n当栉龙死去时，若你手上仍然有以此法抓的牌，将其弃掉。\\n疯魔{0}"
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())


if __name__ == "__main__":
    unittest.main()
