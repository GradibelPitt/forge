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
    def test_artifact_enters_with_two_depletion_counters(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:维希度斯的窟穴", text)
        self.assertIn("ManaCost:B R", text)
        self.assertIn("Types:Artifact", text)
        self.assertNotIn("Types:Land", text)
        self.assertIn(
            "R:Event$ Moved | ValidCard$ Card.Self | Destination$ Battlefield | "
            "ReplaceWith$ DBAddCounter | ReplacementResult$ Updated",
            text,
        )
        self.assertIn(
            "SVar:DBAddCounter:DB$ PutCounter | Defined$ Self | ETB$ True | "
            "CounterType$ DEPLETION | CounterNum$ 2",
            text,
        )
        self.assertNotIn("DB$ Tap", text)

    def test_activated_ability_removes_a_counter_discards_draws_and_sacrifices_when_empty(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "A:AB$ Discard | Cost$ T SubCounter<1/DEPLETION> | Defined$ You | "
            "NumCards$ 1 | Mode$ TgtChoose | SubAbility$ DBDraw | "
            "PlayerTurn$ True | SorcerySpeed$ True",
            text,
        )
        self.assertIn(
            "SVar:DBDraw:DB$ Draw | Defined$ You | NumCards$ 2 | SubAbility$ DBSac",
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
            "维希度斯的窟穴|维希度斯的窟穴|神器|"
            "此神器进场时上面有两个消耗指示物。\\n"
            "{T}，从此神器上移去一个消耗指示物：弃一张牌，抽两张牌。"
            "如果此地上没有消耗指示物，则将它牺牲。只能于你的回合中起动且只能于法术时机起动。",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )


if __name__ == "__main__":
    unittest.main()
