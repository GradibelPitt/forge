from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "white" / "大地坠饰.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "大地坠饰.artcrop.jpg"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-ab7a84b7-fec5-4124-b1ac-782c408277a3.png"
)
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class EarthPendantContractTest(unittest.TestCase):
    def test_discovers_a_creature_from_your_library_and_remembers_it(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:大地坠饰", text)
        self.assertIn("ManaCost:W W", text.splitlines())
        self.assertIn("Types:Instant", text)
        self.assertIn(
            "A:SP$ CardDiscover | Defined$ You | Source$ Library | "
            "SourceController$ You | ValidCards$ Creature | OptionCount$ 3 | "
            "Destination$ Hand | RememberChosen$ True | SubAbility$ GainLife",
            text,
        )

    def test_gains_life_equal_to_the_chosen_creatures_mana_value(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "SVar:GainLife:DB$ GainLife | Defined$ You | LifeAmount$ X | "
            "SubAbility$ Cleanup",
            text,
        )
        self.assertIn("SVar:X:Remembered$CardManaCost", text)
        self.assertIn("SVar:Cleanup:DB$ Cleanup | ClearRemembered$ True", text)

    def test_card_is_registered_with_standard_crop_art(self):
        self.assertIn("71 R 大地坠饰 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())

    def test_standard_art_crop_is_landscape_rgb_jpeg(self):
        from PIL import Image

        with Image.open(ART) as image:
            self.assertEqual((1024, 748), image.size)
            self.assertEqual("RGB", image.mode)

    def test_zh_cn_display_text_matches_the_requested_description(self):
        expected = (
            "大地坠饰|大地坠饰|瞬间|"
            "发现你牌库中的一张生物牌，你获得X点生命，"
            "X等同于所选择生物牌的总法术力费用。"
        )
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())


if __name__ == "__main__":
    unittest.main()
