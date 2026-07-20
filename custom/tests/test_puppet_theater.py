import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "colorless" / "木偶剧场.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-6ec4253b-7f09-4b1d-bb5c-5dfc14ac8dc5.png"
)
ART = ROOT / "cards" / "pictures" / "PH01" / "木偶剧场.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

SOURCE_ORACLE = (
    "Durability 2 (This permanent enters with two durability counters on it. "
    "When the last durability counter is removed from it, sacrifice it.)\\n"
    "{T}, Remove a durability counter from CARDNAME: Conjure a duplicate of target "
    "nontoken creature you don't control into your hand. Its mana cost perpetually "
    "becomes {1}, and it perpetually has base power and toughness 1/1."
)
ZH_ORACLE = (
    "耐久2（此永久物进战场时上面有两个耐久指示物。当最后一个耐久指示物从其上移去时，将它牺牲。）\\n"
    "{T}，从木偶剧场上移去一个耐久指示物：选择目标不由你操控的非衍生生物。"
    "化生一张它的复制品置入你手中。该复制品的法术力费用永久变为{1}，"
    "且其基础力量和基础防御力永久变为1/1。"
)


class PuppetTheaterContractTest(unittest.TestCase):
    def test_characteristics_and_durability_cost(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:木偶剧场", text)
        self.assertIn("ManaCost:2 U U", text)
        self.assertIn("Types:Artifact", text)
        self.assertIn("K:Durability:2", text)

        ability = next(line for line in text.splitlines() if line.startswith("A:AB$ MakeCard"))
        self.assertIn("Cost$ T SubCounter<1/DURABILITY>", ability)
        self.assertIn("ValidTgts$ Creature.YouDontCtrl+!token", ability)
        self.assertIn("DefinedName$ Targeted", ability)
        self.assertIn("Conjure$ True", ability)
        self.assertIn("Zone$ Hand", ability)
        self.assertIn("RememberMade$ True", ability)
        self.assertIn("SubAbility$ DBAnimate", ability)

    def test_duplicate_perpetually_becomes_one_mana_and_one_one(self):
        text = CARD.read_text(encoding="utf-8")
        animate = next(line for line in text.splitlines() if line.startswith("SVar:DBAnimate:"))
        cleanup = next(line for line in text.splitlines() if line.startswith("SVar:DBCleanup:"))

        self.assertIn("DB$ Animate", animate)
        self.assertIn("Defined$ Remembered", animate)
        self.assertIn("ManaCost$ 1", animate)
        self.assertIn("Power$ 1", animate)
        self.assertIn("Toughness$ 1", animate)
        self.assertIn("Duration$ Perpetual", animate)
        self.assertIn("ClearRemembered$ True", cleanup)
        self.assertIn(f"Oracle:{SOURCE_ORACLE}", text)

    def test_registration_localization_and_art(self):
        self.assertIn("75 R 木偶剧场 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertIn(
            f"木偶剧场|木偶剧场|神器|{ZH_ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())

        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertGreater(image.width, image.height)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
