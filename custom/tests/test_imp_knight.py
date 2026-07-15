import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "multicolor" / "小鬼骑士.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "小鬼骑士.artcrop.jpg"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Imp_Knight_original.png"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class ImpKnightContractTest(unittest.TestCase):
    def test_card_has_requested_cost_types_stats_and_batch_discard_trigger(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:小鬼骑士", text)
        self.assertIn("ManaCost:B R", text)
        self.assertIn("Types:Creature Demon Knight", text)
        self.assertIn("PT:3/2", text)
        self.assertIn(
            "T:Mode$ DiscardedAll | ValidPlayer$ You | TriggerZones$ Battlefield | "
            "Execute$ TrigPutCounter",
            text,
        )
        self.assertIn(
            "SVar:TrigPutCounter:DB$ PutCounter | Defined$ Self | CounterType$ P1P1 | "
            "CounterNum$ X | SubAbility$ DBTrample",
            text,
        )
        self.assertIn("SVar:X:TriggerCount$Amount", text)
        self.assertIn("SVar:DBTrample:DB$ Pump | Defined$ Self | KW$ Trample", text)
        self.assertIn(
            "Oracle:每当你弃一张或数张牌时，在此生物上放置等量的+1/+1指示物且直到回合结束获得践踏异能。",
            text,
        )

    def test_card_is_registered_with_original_and_standard_crop_art(self):
        self.assertIn("60 R 小鬼骑士 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())

        from PIL import Image

        with Image.open(ART_BACKUP) as image:
            self.assertEqual((714, 1024), image.size)
        with Image.open(ART) as image:
            self.assertEqual((714, 521), image.size)
            self.assertEqual("RGB", image.mode)

    def test_zh_cn_display_text_matches_the_requested_oracle(self):
        expected = (
            "小鬼骑士|小鬼骑士|生物～恶魔／骑士|"
            "每当你弃一张或数张牌时，在此生物上放置等量的+1/+1指示物且直到回合结束获得践踏异能。"
        )
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())


if __name__ == "__main__":
    unittest.main()
