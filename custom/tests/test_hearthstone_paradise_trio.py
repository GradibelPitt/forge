import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

VOLJIN = ROOT / "cards" / "white" / "惬意的沃金.txt"
MAESTRA = ROOT / "cards" / "blue" / "面具变装大师.txt"
COOKIE = ROOT / "cards" / "black" / "悠闲的曲奇.txt"

ART_CASES = (
    (
        ROOT / "tools" / "card-artwork" / "Chillin_Voljin_full.jpg",
        ROOT / "cards" / "pictures" / "PH01" / "惬意的沃金.artcrop.jpg",
    ),
    (
        ROOT / "tools" / "card-artwork" / "Maestra_Mask_Merchant_full.jpg",
        ROOT / "cards" / "pictures" / "PH01" / "面具变装大师.artcrop.jpg",
    ),
    (
        ROOT / "tools" / "card-artwork" / "Carefree_Cookie_full.jpg",
        ROOT / "cards" / "pictures" / "PH01" / "悠闲的曲奇.artcrop.jpg",
    ),
)


class HearthstoneParadiseTrioContractTest(unittest.TestCase):
    def test_voljin_characteristics_and_cross_target_counters(self):
        text = VOLJIN.read_text(encoding="utf-8")

        self.assertIn("Name:惬意的沃金", text)
        self.assertIn("ManaCost:1 W W", text)
        self.assertIn("Types:Legendary Creature Troll Monk", text)
        self.assertIn("PT:3/3", text)
        self.assertIn("K:Lifelink", text)
        self.assertIn(
            "SVar:TrigCounters:DB$ PutCounter | Defined$ Targeted | "
            "CounterType$ P1P1 | CounterNumPerDefined$ X | "
            "ValidTgts$ Creature.Other | TargetMin$ 2 | TargetMax$ 2 | "
            "TargetUnique$ True",
            text,
        )
        self.assertIn("SVar:X:Count$Valid Targeted.Other$CardPower", text)
        self.assertIn(
            "A:AB$ PutCounter | Cost$ G Sac<1/Creature.powerEQ1+toughnessEQ1> | "
            "ValidTgts$ Creature | CounterType$ P1P1 | CounterNum$ 2",
            text,
        )

    def test_maestra_discover_and_once_each_turn_copy(self):
        text = MAESTRA.read_text(encoding="utf-8")

        self.assertIn("Name:面具变装大师", text)
        self.assertIn("ManaCost:1 U U", text)
        self.assertIn("Types:Legendary Creature Shapeshifter", text)
        self.assertIn("PT:3/2", text)
        self.assertIn("K:Changeling", text)
        self.assertIn(
            "SVar:TrigDiscover:DB$ CardDiscover | Defined$ You | "
            "Source$ CardDatabase | ValidCards$ Planeswalker.nonBlue | "
            "OptionCount$ 3 | Destination$ Hand",
            text,
        )
        copy_line = next(
            line for line in text.splitlines() if line.startswith("A:AB$ CopySpellAbility")
        )
        self.assertIn("Cost$ B PayLife<2>", copy_line)
        self.assertIn("ActivationLimit$ 1", copy_line)
        self.assertIn("TargetType$ Activated.YouCtrl", copy_line)
        self.assertIn("ValidTgts$ Card.nonCreature", copy_line)
        self.assertIn("MayChooseTarget$ True", copy_line)
        self.assertNotIn("PlayerTurn$ True", copy_line)

    def test_cookie_flash_and_random_conjure_chain(self):
        text = COOKIE.read_text(encoding="utf-8")

        self.assertIn("Name:悠闲的曲奇", text)
        self.assertIn("ManaCost:1 B B", text)
        self.assertIn("Types:Legendary Creature Frog Pirate Shaman", text)
        self.assertIn("PT:2/2", text)
        self.assertIn("K:Flash", text)
        self.assertIn(
            "T:Mode$ ChangesZone | Origin$ Battlefield | Destination$ Graveyard | "
            "ValidCard$ Creature.!token+YouCtrl+Other | TriggerZones$ Battlefield | "
            "Execute$ TrigNameCard",
            text,
        )
        self.assertIn(
            "SVar:TrigNameCard:DB$ NameCard | AtRandom$ True | "
            "ValidCards$ Creature.cmcEQX | SubAbility$ DBConjure",
            text,
        )
        self.assertIn(
            "SVar:DBConjure:DB$ MakeCard | Name$ ChosenName | Conjure$ True | "
            "Zone$ Battlefield | SubAbility$ DBCleanup",
            text,
        )
        self.assertIn("SVar:DBCleanup:DB$ Cleanup | ClearNamedCard$ True", text)
        self.assertIn("SVar:X:TriggeredCard$CardManaCost/Plus.1", text)
        self.assertNotIn("???", text)

    def test_registration_localization_and_art(self):
        edition = EDITION.read_text(encoding="utf-8")
        self.assertIn("78 M 惬意的沃金 @Custom", edition)
        self.assertIn("79 M 面具变装大师 @Custom", edition)
        self.assertIn("80 M 悠闲的曲奇 @Custom", edition)

        localization = ZH_CN.read_text(encoding="utf-8").splitlines()
        self.assertTrue(any(line.startswith("惬意的沃金|惬意的沃金|") for line in localization))
        self.assertTrue(any(line.startswith("面具变装大师|面具变装大师|") for line in localization))
        cookie_line = next(line for line in localization if line.startswith("悠闲的曲奇|悠闲的曲奇|"))
        self.assertNotIn("???", cookie_line)
        self.assertNotIn("{G}", cookie_line)

        for backup, crop in ART_CASES:
            self.assertTrue(backup.is_file())
            self.assertTrue(crop.is_file())
            with Image.open(crop) as image:
                self.assertEqual("JPEG", image.format)
                self.assertEqual("RGB", image.mode)
                self.assertGreater(image.width, image.height)
                self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
