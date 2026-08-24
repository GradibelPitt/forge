import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "blue" / "潮池学徒.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "潮池学徒.artcrop.jpg"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "潮池学徒_original.jpg"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class TidepoolApprenticeContractTest(unittest.TestCase):
    def test_characteristics_registration_and_chinese_wording(self):
        text = CARD.read_text(encoding="utf-8")
        self.assertIn("Name:潮池学徒", text)
        self.assertIn("ManaCost:U", text)
        self.assertIn("Types:Creature Naga", text)
        self.assertIn("PT:2/1", text)
        self.assertIn("63 C 潮池学徒 @Custom", EDITION.read_text(encoding="utf-8"))

        expected = (
            "潮池学徒|潮池学徒|生物～娜迦|"
            "每当你施放一个瞬间或法术咒语时，将一张与该咒语同名之牌的复制品化生到放逐区。"
            "\\n{T}，牺牲潮池学徒：将目标由潮池学徒放逐的牌移回你手上。"
        )
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())

    def test_cast_trigger_conjures_the_cast_cards_duplicate_into_exile(self):
        text = CARD.read_text(encoding="utf-8")
        trigger = next(line for line in text.splitlines() if line.startswith("T:Mode$ SpellCast"))
        conjure = next(line for line in text.splitlines() if line.startswith("SVar:TrigConjure:"))

        self.assertIn("ValidCard$ Instant,Sorcery", trigger)
        self.assertIn("ValidActivatingPlayer$ You", trigger)
        self.assertIn("TriggerZones$ Battlefield", trigger)
        self.assertIn("Execute$ TrigConjure", trigger)
        self.assertIn("DB$ MakeCard", conjure)
        self.assertIn("Conjure$ True", conjure)
        self.assertIn("DefinedName$ TriggeredCardLKICopy", conjure)
        self.assertIn("Zone$ Exile", conjure)
        self.assertIn("ImprintMade$ True", conjure)

    def test_tap_and_sacrifice_returns_only_a_tracked_exiled_spell_card(self):
        text = CARD.read_text(encoding="utf-8")
        ability = next(line for line in text.splitlines() if line.startswith("A:AB$ ChangeZone"))

        self.assertIn("Cost$ T Sac<1/CARDNAME>", ability)
        self.assertIn("ValidTgts$ Instant.IsImprinted,Sorcery.IsImprinted", ability)
        self.assertIn("TgtZone$ Exile", ability)
        self.assertIn("Origin$ Exile", ability)
        self.assertIn("Destination$ Hand", ability)

    def test_original_and_dynamic_art_are_preserved(self):
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((512, 374), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.02)


if __name__ == "__main__":
    unittest.main()
