import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "colorless" / "许愿井.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Wishing_Well_full_hswiki.jpg"
ART = ROOT / "cards" / "pictures" / "PH01" / "许愿井.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class WishingWellContractTest(unittest.TestCase):
    def test_characteristics_and_treasure_token_trigger(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:许愿井", text)
        self.assertIn("ManaCost:5", text)
        self.assertIn("Types:Artifact Creature Wall", text)
        self.assertIn("PT:0/7", text)

        trigger = next(line for line in text.splitlines() if line.startswith("T:Mode$ Sacrificed"))
        self.assertIn("ValidCard$ Treasure.token+YouCtrl", trigger)
        self.assertIn("TriggerZones$ Battlefield", trigger)
        self.assertIn("Execute$ TrigDiscover", trigger)
        self.assertNotIn("K:Defender", text)

    def test_discovers_a_legendary_creature_and_perpetually_sets_cost_to_one(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "SVar:TrigDiscover:DB$ CardDiscover | Defined$ You | "
            "Source$ CardDatabase | ValidCards$ Creature.Legendary | "
            "OptionCount$ 3 | Destination$ Hand | RememberChosen$ True | "
            "SubAbility$ SetCost",
            text,
        )
        self.assertIn(
            "SVar:SetCost:DB$ Animate | Defined$ Remembered | ManaCost$ 1 | "
            "Duration$ Perpetual | SubAbility$ Cleanup | StackDescription$ None",
            text,
        )
        self.assertIn("SVar:Cleanup:DB$ Cleanup | ClearRemembered$ True", text)
        self.assertNotIn("ManaCost$ C", text)
        self.assertIn("perpetually becomes {1}", text)

    def test_registration_localization_and_art(self):
        self.assertIn("117 R 许愿井 @Custom", EDITION.read_text(encoding="utf-8"))

        localization = ZH_CN.read_text(encoding="utf-8").splitlines()
        self.assertIn(
            "许愿井|许愿井|神器生物～墙|"
            "每当你牺牲一个珍宝衍生物时，发现一张具有传奇类别的生物牌并置入你手中，"
            "且其法术力费用永久变为{1}。",
            localization,
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
