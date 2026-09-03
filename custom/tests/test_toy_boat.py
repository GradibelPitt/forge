import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "blue" / "玩具船.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "玩具船.artcrop.jpg"
ORIGINAL_ART = ROOT / "tools" / "card-artwork" / "TOY_505_original.png"
LANDSCAPE_ART = ROOT / "tools" / "card-artwork" / "TOY_505_landscape.png"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = (
    "守军\\n"
    "只要玩具船可以阻挡，则它必须阻挡。\\n"
    "每当你召唤一个海盗时，抓一张牌。"
)


class ToyBoatContractTest(unittest.TestCase):
    def test_card_matches_the_requested_characteristics_and_rules(self):
        text = CARD.read_text(encoding="utf-8")

        for line in (
            "Name:玩具船",
            "ManaCost:U U",
            "Types:Artifact Creature Ship",
            "PT:2/3",
            "K:Defender",
            "S:Mode$ MustBlock | ValidCreature$ Card.Self | "
            "Description$ CARDNAME blocks each combat if able.",
            "T:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | "
            "ValidCard$ Creature.Pirate+YouCtrl | TriggerZones$ Battlefield | "
            "Execute$ TrigDraw | TriggerDescription$ Whenever a Pirate enters "
            "the battlefield under your control, draw a card.",
            "SVar:TrigDraw:DB$ Draw | Defined$ You | NumCards$ 1",
            f"Oracle:{ORACLE}",
        ):
            self.assertIn(line, text)

    def test_registration_and_zh_cn_display_text(self):
        self.assertIn(
            "137 C 玩具船 @BOSi Studio",
            EDITION.read_text(encoding="utf-8"),
        )
        self.assertIn(
            f"玩具船|玩具船|神器生物～船|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )

    def test_original_and_landscape_art_are_preserved(self):
        self.assertTrue(ORIGINAL_ART.is_file())
        self.assertTrue(LANDSCAPE_ART.is_file())

        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
