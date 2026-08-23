import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "colorless" / "绞肉车.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Meat_Wagon_ICC_812.png"
ART = ROOT / "cards" / "pictures" / "PH01" / "绞肉车.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class MeatWagonContractTest(unittest.TestCase):
    def test_characteristics_death_trigger_and_self_sacrifice(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:绞肉车", lines)
        self.assertIn("ManaCost:3", lines)
        self.assertIn("Types:Artifact Creature Juggernaut", lines)
        self.assertIn("PT:1/4", lines)

        trigger = next(line for line in lines if line.startswith("T:Mode$ ChangesZone"))
        self.assertIn("Origin$ Battlefield", trigger)
        self.assertIn("Destination$ Graveyard", trigger)
        self.assertIn("ValidCard$ Card.Self", trigger)
        self.assertIn("OptionalDecider$ TriggeredCardController", trigger)
        self.assertIn("Execute$ TrigRecruit", trigger)

        recruit = next(line for line in lines if line.startswith("SVar:TrigRecruit:"))
        self.assertIn("DB$ ChangeZone", recruit)
        self.assertIn("Origin$ Library", recruit)
        self.assertIn("Destination$ Battlefield", recruit)
        self.assertIn("ChangeType$ Creature.powerLEX", recruit)
        self.assertIn("ChangeNum$ 1", recruit)
        self.assertIn("SVar:X:TriggeredCard$CardPower", lines)

        self.assertIn(
            "A:AB$ Sacrifice | Cost$ 1 T | SpellDescription$ Sacrifice CARDNAME.",
            lines,
        )

    def test_registration_localization_art_and_documentation(self):
        self.assertIn(
            "119 R 绞肉车 @Rafael Zanchetin",
            EDITION.read_text(encoding="utf-8"),
        )
        self.assertIn(
            "绞肉车|绞肉车|神器生物～攻城巨车|"
            "当绞肉车死去时，你可以从你的牌库中搜寻一张力量不大于绞肉车力量的生物牌，"
            "将其放进战场，然后将你的牌库洗牌。\\n{1}，{T}：牺牲绞肉车。",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 绞肉车 | `{3}`，1/4 神器生物～攻城巨车 | "
            "`cards/colorless/绞肉车.txt` | 119 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
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
