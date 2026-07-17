import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "multicolor" / "转生.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "转生.artcrop.jpg"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "codex-clipboard-f4c61216-eb2d-4496-81a4-7997c818548f.png"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class ReincarnationContractTest(unittest.TestCase):
    def test_characteristics_registration_and_chinese_wording(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:转生", text)
        self.assertIn("ManaCost:W/B", text)
        self.assertNotIn("ManaCost:W B", text)
        self.assertIn("Types:Instant", text)
        self.assertIn("66 U 转生 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertIn(
            "转生|转生|瞬间|消灭目标生物。若一个生物牌以此法置入坟墓场，则将它在其拥有者的操控下移回战场。",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )

    def test_destroys_then_returns_only_a_destroyed_nontoken_creature(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "A:SP$ Destroy | ValidTgts$ Creature | TgtPrompt$ Select target creature | RememberDestroyed$ True | SubAbility$ DBReturn",
            text,
        )
        self.assertIn(
            "SVar:DBReturn:DB$ ChangeZone | Origin$ Graveyard | Destination$ Battlefield | Defined$ Remembered | ConditionDefined$ Remembered | ConditionPresent$ Card.Creature | SubAbility$ DBCleanup",
            text,
        )
        self.assertNotIn("GainControl$ True", text)
        self.assertIn("SVar:DBCleanup:DB$ Cleanup | ClearRemembered$ True", text)

    def test_original_and_dynamic_art_are_preserved(self):
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((512, 374), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.02)


if __name__ == "__main__":
    unittest.main()
