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
    def test_land_and_activated_ability(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:维希度斯的窟穴", text)
        self.assertIn("Types:Land", text)
        self.assertIn(
            "A:AB$ Draw | Cost$ T Discard<1/Card> | NumCards$ 2 | Defined$ You",
            text,
        )

    def test_registration_art_and_localization(self):
        self.assertIn("37 R 维希度斯的窟穴 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())
        self.assertIn(
            "维希度斯的窟穴|维希度斯的窟穴|地|{T}，弃一张牌：抽两张牌。",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )


if __name__ == "__main__":
    unittest.main()
