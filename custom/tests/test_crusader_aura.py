import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "multicolor" / "十字军光环.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "十字军光环.artcrop.jpg"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "十字军光环.jpg"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class CrusaderAuraContractTest(unittest.TestCase):
    def test_card_grants_each_controlled_creature_a_permanent_attack_buff(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:十字军光环", text)
        self.assertIn("ManaCost:2 W R", text)
        self.assertIn("Types:Enchantment", text)
        self.assertIn(
            "S:Mode$ Continuous | Affected$ Creature.YouCtrl | AddTrigger$ CrusaderAttack | AddSVar$ AE | "
            "Description$ Creatures you control have \"Whenever this creature attacks, it gets +3/+2 permanently.\"",
            text,
        )
        self.assertIn(
            "SVar:CrusaderAttack:Mode$ Attacks | ValidCard$ Card.Self | Execute$ TrigPump | "
            "TriggerDescription$ Whenever this creature attacks, it gets +3/+2 permanently.",
            text,
        )
        self.assertIn(
            "SVar:TrigPump:DB$ Pump | Defined$ TriggeredAttackerLKICopy | NumAtt$ +3 | NumDef$ +2 | "
            "Duration$ Permanent",
            text,
        )

    def test_card_uses_the_requested_chinese_wording(self):
        oracle = "Oracle:你操控的生物具有「每当该生物攻击时，它永久获得+3/+2。」"
        self.assertIn(oracle, CARD.read_text(encoding="utf-8"))

        expected = "十字军光环|十字军光环|结界|你操控的生物具有「每当该生物攻击时，它永久获得+3/+2。」"
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())

    def test_card_is_registered_with_standard_crop_art(self):
        self.assertIn("23 R 十字军光环 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())

    def test_art_crop_is_rgb_and_landscape(self):
        from PIL import Image

        with Image.open(ART) as image:
            self.assertEqual("RGB", image.mode)
            self.assertGreater(image.width, image.height)
            self.assertAlmostEqual(image.width / image.height, 1.37, delta=0.02)


if __name__ == "__main__":
    unittest.main()
