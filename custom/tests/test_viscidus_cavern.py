import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "colorless" / "维希度斯的窟穴.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Viscidus_Cavern_original.jpg"
ART = ROOT / "cards" / "pictures" / "PH01" / "维希度斯的窟穴.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class ViscidusCavernContractTest(unittest.TestCase):
    def test_land_enters_tapped_with_two_depletion_counters(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:维希度斯的窟穴", text)
        self.assertIn("Types:Land", text)
        self.assertIn(
            "R:Event$ Moved | ValidCard$ Card.Self | Destination$ Battlefield | "
            "ReplaceWith$ LandTapped | ReplacementResult$ Updated",
            text,
        )
        self.assertIn(
            "SVar:LandTapped:DB$ Tap | Defined$ Self | ETB$ True | SubAbility$ DBAddCounter",
            text,
        )
        self.assertIn(
            "SVar:DBAddCounter:DB$ PutCounter | Defined$ Self | ETB$ True | "
            "CounterType$ DEPLETION | CounterNum$ 2",
            text,
        )

    def test_activated_ability_removes_a_counter_discards_draws_and_sacrifices_when_empty(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "A:AB$ Draw | Cost$ T SubCounter<1/DEPLETION> Discard<1/Card> | "
            "NumCards$ 2 | Defined$ You | SubAbility$ DBSac",
            text,
        )
        self.assertIn(
            "SVar:DBSac:DB$ Sacrifice | SacValid$ Self | "
            "ConditionPresent$ Card.Self+counters_EQ0_DEPLETION",
            text,
        )

    def test_registration_art_and_localization(self):
        self.assertIn("37 R 维希度斯的窟穴 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())
        self.assertIn(
            "维希度斯的窟穴|维希度斯的窟穴|地|"
            "此地须横置进场，且上面有两个消耗指示物。\\n"
            "{T}，从此地上移去一个消耗指示物：弃一张牌：抽两张牌。"
            "如果此地上没有消耗指示物，则将它牺牲。",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )


if __name__ == "__main__":
    unittest.main()
