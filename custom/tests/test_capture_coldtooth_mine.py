import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "multicolor" / "占领冷齿矿洞.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Capture_Coldtooth_Mine_full.jpg"
ART = ROOT / "cards" / "pictures" / "PH01" / "占领冷齿矿洞.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ZH_ORACLE = (
    "选择一项—\\n"
    "• 将你牌库中一张具有最高法术力值的牌置于你手上。\\n"
    "• 将你牌库中一张具有最低法术力值的牌置于你手上。\\n"
    "打包{2}(若你支付打包费用，则两项都选择。)"
)


class CaptureColdtoothMineContractTest(unittest.TestCase):
    def test_choose_one_seeks_an_automatic_highest_or_lowest_card(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:占领冷齿矿洞", text)
        self.assertIn("ManaCost:U G", text)
        self.assertIn("Types:Sorcery", text)
        self.assertIn("K:Entwine:2", text)
        self.assertNotIn("K:Entwine:2 U G", text)
        self.assertIn("A:SP$ Charm | Choices$ DBHighest,DBLowest", text)
        self.assertIn(
            "SVar:DBHighest:DB$ Seek | Type$ Card.cmcEQX | "
            "SpellDescription$ Put a card with the highest mana value in your "
            "library into your hand.",
            text,
        )
        self.assertIn(
            "SVar:DBLowest:DB$ Seek | Type$ Card.cmcEQY | "
            "SpellDescription$ Put a card with the lowest mana value in your "
            "library into your hand.",
            text,
        )
        self.assertIn(
            "SVar:X:Count$ValidLibrary Card.YouOwn$GreatestCardManaCost",
            text,
        )
        self.assertIn(
            "SVar:Y:Count$ValidLibrary Card.YouOwn$LeastCardManaCost",
            text,
        )
        self.assertNotIn("ChooseCard", text)
        self.assertNotIn("CardDiscover", text)
        self.assertNotIn("ChangeZone", text)
        self.assertIn(
            "Entwine {2} (Choose both if you pay the entwine cost.)",
            text,
        )

    def test_registration_localization_and_original_art_crop(self):
        self.assertIn("81 R 占领冷齿矿洞 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertEqual(
            "9D2A11F5DD2C85693828A2CAE69D27ACAA8BECF075DFC3F83ABE1C0B1410CF3A",
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        self.assertTrue(ART.is_file())
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((4367, 3188), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)

        expected = f"占领冷齿矿洞|占领冷齿矿洞|法术|{ZH_ORACLE}"
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())


if __name__ == "__main__":
    unittest.main()
