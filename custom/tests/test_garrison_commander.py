import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "colorless" / "要塞指挥官.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-0f1b2ba3-9672-4522-8cff-14074c54e0ff.png"
)
ART = ROOT / "cards" / "pictures" / "PH01" / "要塞指挥官.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ABILITY_TEXT = (
    "选择目标由你操控的鹏洛客。本回合中，你可以起动该鹏洛客的忠诚异能两次，"
    "而不是只能起动一次。"
)
ORACLE = f"{{2}}，{{T}}：{ABILITY_TEXT}"


class GarrisonCommanderContractTest(unittest.TestCase):
    def test_only_the_targeted_planeswalker_gets_a_second_activation(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:要塞指挥官", lines)
        self.assertIn("ManaCost:2", lines)
        self.assertIn("Types:Creature Human Soldier", lines)
        self.assertIn("PT:2/3", lines)

        ability = next(line for line in lines if line.startswith("A:AB$ Effect"))
        self.assertIn("Cost$ 2 T", ability)
        self.assertIn("ValidTgts$ Planeswalker.YouCtrl", ability)
        self.assertIn("RememberObjects$ Targeted", ability)
        self.assertIn("StaticAbilities$ PWTwice", ability)
        self.assertIn(f"SpellDescription$ {ABILITY_TEXT}", ability)

        loyalty_limit = next(
            line for line in lines if line.startswith("SVar:PWTwice:")
        )
        self.assertIn("Mode$ NumLoyaltyAct", loyalty_limit)
        self.assertIn("ValidCard$ Card.IsRemembered", loyalty_limit)
        self.assertIn("Twice$ True", loyalty_limit)
        self.assertNotIn("ValidCard$ Planeswalker.YouCtrl", loyalty_limit)
        self.assertNotIn("Additional$", loyalty_limit)
        self.assertIn(f"Oracle:{ORACLE}", lines)

    def test_registration_localization_art_and_documentation(self):
        self.assertIn("102 R 要塞指挥官 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertIn(
            f"要塞指挥官|要塞指挥官|生物～人类／士兵|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 要塞指挥官 | `{2}`，2/3 生物～人类／士兵 | "
            "`cards/colorless/要塞指挥官.txt` | 102 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        self.assertTrue(ART_BACKUP.is_file())
        self.assertEqual(
            "EFC7483B36AD57F1423C45A8AC0144A574930383E6FA3C8C31AAF5A1BD0EEFBB",
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        self.assertTrue(ART.is_file())
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((666, 486), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
