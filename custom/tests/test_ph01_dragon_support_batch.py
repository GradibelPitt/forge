from pathlib import Path
import unittest

from PIL import Image


CUSTOM = Path(__file__).resolve().parents[1]
ROOT = CUSTOM.parent
ETERNAL = CUSTOM / "cards" / "multicolor" / "永恒吐息.txt"
STUDY = CUSTOM / "cards" / "white" / "龙族研习.txt"
LANCER = CUSTOM / "cards" / "red" / "燃棘枪兵.txt"
WHELP = CUSTOM / "cards" / "multicolor" / "琥珀雏龙.txt"
PICTURES = CUSTOM / "cards" / "pictures" / "PH01"
EDITION = CUSTOM / "editions" / "Placeholder_Set.txt"
LOCALIZATION = ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"
CARDS = CUSTOM / "CARDS.md"


class Ph01DragonSupportBatchContractTest(unittest.TestCase):
    def test_eternal_breath_behold_branch_and_opponent_graveyards(self):
        text = ETERNAL.read_text(encoding="utf-8")
        self.assertIn("ManaCost:1 W B", text)
        self.assertIn("Types:Sorcery", text)
        self.assertIn("Cost$ Behold<1/Dragon>", text)
        self.assertIn(
            "A:SP$ PumpAll | ValidCards$ Creature | NumAtt$ -3 | NumDef$ -3",
            text,
        )
        self.assertIn("SubAbility$ DBExileOppGy", text)
        self.assertIn(
            "SVar:DBExileOppGy:DB$ ChangeZoneAll | ChangeType$ Card.OppCtrl "
            "| Origin$ Graveyard | Destination$ Exile | SubAbility$ DBRestoreYours",
            text,
        )
        restore = next(
            line for line in text.splitlines() if line.startswith("SVar:DBRestoreYours:")
        )
        self.assertIn("ValidCards$ Creature.YouCtrl", restore)
        self.assertIn("NumAtt$ +3 | NumDef$ +3", restore)
        self.assertIn("Condition$ OptionalCost | ConditionOptionalPaid$ True", restore)
        self.assertNotIn("Defined$ Opponent | Origin$ Graveyard", text)

    def test_dragon_study_alternate_cost_dig_and_restricted_mana(self):
        text = STUDY.read_text(encoding="utf-8")
        self.assertIn("ManaCost:W", text)
        self.assertIn("K:AlternateAdditionalCost:Behold<1/Dragon>:1", text)
        self.assertIn("A:SP$ Dig | DigNum$ 5 | ChangeNum$ 1 | Optional$ True", text)
        self.assertIn("ChangeValid$ Dragon", text)
        self.assertIn("SubAbility$ DBDragonMana", text)
        self.assertIn(
            "SVar:DBDragonMana:DB$ Mana | Produced$ Any | Amount$ 1 "
            "| RestrictValid$ Spell.Creature+Dragon",
            text,
        )
        self.assertNotIn("Produced$ Combo Any", text)

    def test_burnthorn_lancer_keywords_and_optional_etb_destroy(self):
        text = LANCER.read_text(encoding="utf-8")
        lines = text.splitlines()
        self.assertIn("ManaCost:2 R", lines)
        self.assertIn("Types:Creature Orc Warrior", lines)
        self.assertIn("PT:3/2", lines)
        self.assertIn("K:First Strike", lines)
        self.assertIn("K:Menace", lines)
        trigger = next(line for line in lines if line.startswith("T:Mode$ ChangesZone"))
        self.assertIn("Origin$ Any | Destination$ Battlefield", trigger)
        self.assertIn("Execute$ TrigDestroy", trigger)
        destroy = next(line for line in lines if line.startswith("SVar:TrigDestroy:"))
        self.assertIn("AB$ Destroy | Cost$ Behold<1/Dragon>", destroy)
        self.assertIn("ValidTgts$ Creature.tapped", destroy)

    def test_amber_whelp_reads_cast_optional_cost_on_etb(self):
        text = WHELP.read_text(encoding="utf-8")
        lines = text.splitlines()
        self.assertIn("ManaCost:1 R W", lines)
        self.assertIn("Types:Creature Dragon", lines)
        self.assertIn("PT:3/3", lines)
        self.assertIn("Cost$ Behold<1/Dragon>", text)
        trigger = next(line for line in lines if line.startswith("T:Mode$ ChangesZone"))
        self.assertIn("CheckSVar$ CastSA>Count$OptionalGenericCostPaid.1.0", trigger)
        self.assertIn("NoResolvingCheck$ True", trigger)
        damage = next(line for line in lines if line.startswith("SVar:TrigDamage:"))
        self.assertIn("DB$ DealDamage | ValidTgts$ Any", damage)
        self.assertIn("NumDmg$ 3", damage)
        self.assertNotIn("ConditionOptionalPaid", damage)

    def test_registration_localization_and_catalog_are_complete(self):
        edition_lines = EDITION.read_text(encoding="utf-8").splitlines()
        expected_registration = {
            "143 U 永恒吐息 @Custom",
            "144 C 龙族研习 @Custom",
            "145 U 燃棘枪兵 @Custom",
            "146 U 琥珀雏龙 @Custom",
        }
        for line in expected_registration:
            self.assertEqual(1, edition_lines.count(line))

        localization = LOCALIZATION.read_text(encoding="utf-8").splitlines()
        for name, card_type in (
            ("永恒吐息", "法术"),
            ("龙族研习", "法术"),
            ("燃棘枪兵", "生物～半兽人／战士"),
            ("琥珀雏龙", "生物～龙"),
        ):
            prefix = f"{name}|{name}|{card_type}|"
            self.assertEqual(1, sum(line.startswith(prefix) for line in localization))

        catalog = CARDS.read_text(encoding="utf-8")
        for name, number in (
            ("永恒吐息", 143),
            ("龙族研习", 144),
            ("燃棘枪兵", 145),
            ("琥珀雏龙", 146),
        ):
            self.assertIn(f"| {name} |", catalog)
            self.assertIn(f"| {number} |", catalog)

    def test_official_full_art_crops_are_present_and_loadable(self):
        expected = (
            "永恒吐息.artcrop.jpg",
            "龙族研习.artcrop.jpg",
            "燃棘枪兵.artcrop.jpg",
            "琥珀雏龙.artcrop.jpg",
        )
        for filename in expected:
            with self.subTest(filename=filename):
                path = PICTURES / filename
                self.assertTrue(path.is_file(), path)
                with Image.open(path) as image:
                    self.assertEqual("JPEG", image.format)
                    self.assertEqual("RGB", image.mode)
                    self.assertEqual((1024, 748), image.size)


if __name__ == "__main__":
    unittest.main()
