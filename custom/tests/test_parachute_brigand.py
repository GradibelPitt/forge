import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "red" / "空降歹徒.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "空降歹徒.artcrop.jpg"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class ParachuteBrigandContractTest(unittest.TestCase):
    def test_card_can_be_cast_free_from_hand_after_a_pirate_entered_this_turn(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:空降歹徒", text)
        self.assertIn("ManaCost:2", text)
        self.assertIn("Types:Creature Pirate", text)
        self.assertIn("PT:2/2", text)
        self.assertIn(
            "S:Mode$ Continuous | MayPlay$ True | MayPlayWithoutManaCost$ True | "
            "MayPlayDontGrantZonePermissions$ True | Affected$ Card.Self | "
            "AffectedZone$ Hand | EffectZone$ All | CheckSVar$ PirateEntered | "
            "SVarCompare$ GE1",
            text,
        )
        self.assertIn(
            "SVar:PirateEntered:Count$ThisTurnEntered_Battlefield_Pirate.YouCtrl",
            text,
        )
        self.assertNotIn("TriggerZones$ Hand", text)

    def test_zh_cn_display_text_matches_the_latest_document(self):
        expected = (
            "空降歹徒|空降歹徒|生物～海盗|"
            "如果本回合一个海盗在你的操控下进战场时，你可以从手牌中免费施放这张牌"
        )
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())

    def test_card_is_registered_as_the_standard_crop_art(self):
        edition = EDITION.read_text(encoding="utf-8")

        self.assertIn("16 C 空降歹徒 @Custom", edition)

    def test_standard_art_crop_is_landscape(self):
        from PIL import Image

        with Image.open(ART) as image:
            self.assertEqual((1000, 730), image.size)
            self.assertEqual("RGB", image.mode)


if __name__ == "__main__":
    unittest.main()
