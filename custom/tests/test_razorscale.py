import hashlib
import unittest
from pathlib import Path

from PIL import Image


CUSTOM = Path(__file__).resolve().parents[1]
FORGE_ROOT = CUSTOM.parent
CARD = CUSTOM / "cards" / "blue" / "锋鳞.txt"
EDITION = CUSTOM / "editions" / "Placeholder_Set.txt"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"
CATALOG = CUSTOM / "CARDS.md"
ORACLE_CATALOG = CUSTOM / "DIY卡牌_游戏内Oracle_非测试卡.txt"
ART_BACKUP = (
    CUSTOM / "tools" / "card-artwork" / "TTN_924_Razorscale_full_hswiki.jpg"
)
ART_CROP = CUSTOM / "cards" / "pictures" / "PH01" / "锋鳞.artcrop.jpg"

ORACLE = (
    "作为施放此咒语的额外费用，你可以请援龙。（你可以选择一个由你操控的龙，或从你手上展示一张龙牌。）\\n"
    "如果你在施放锋鳞时支付了请援龙此费用，则锋鳞不能被反击。\\n"
    "每当一个对手施放瞬间咒语时，除非其操控者支付{2}，否则反击之。\\n"
    "每个施放时要支付的法术力少于两点的咒语均需支付两点法术力来施放。"
)
ORACLE_RENDERED = ORACLE.replace("\\n", "\n")


class RazorscaleContractTest(unittest.TestCase):
    def test_characteristics_match_the_approved_design(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:锋鳞", lines)
        self.assertIn("ManaCost:2 U", lines)
        self.assertIn("Types:Creature Dragon", lines)
        self.assertIn("PT:2/4", lines)
        self.assertIn(f"Oracle:{ORACLE}", lines)

    def test_cast_exposes_a_real_optional_behold_cost(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()
        optional = next(line for line in lines if line.startswith("S:Mode$ OptionalCost"))

        for field in (
            "EffectZone$ All",
            "ValidCard$ Card.Self",
            "ValidSA$ Spell",
            "Cost$ Behold<1/Dragon>",
        ):
            self.assertIn(field, optional)

    def test_only_the_paid_behold_cast_is_uncounterable(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()
        replacement = next(line for line in lines if line.startswith("R:Event$ Counter"))

        for field in (
            "ValidCard$ Card.Self",
            "ValidSA$ Spell",
            "Layer$ CantHappen",
            "CheckSVar$ CastSA>Count$OptionalGenericCostPaid.1.0",
        ):
            self.assertIn(field, replacement)
        self.assertNotIn("Count$Presence_Dragon", replacement)

    def test_opponents_instants_are_countered_unless_their_caster_pays_two(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()
        trigger = next(line for line in lines if line.startswith("T:Mode$ SpellCast"))

        for field in (
            "ValidCard$ Instant",
            "ValidActivatingPlayer$ Opponent",
            "TriggerZones$ Battlefield",
            "Execute$ TrigCounter",
        ):
            self.assertIn(field, trigger)

        counter = next(line for line in lines if line.startswith("SVar:TrigCounter:"))
        for field in (
            "DB$ Counter",
            "Defined$ TriggeredSpellAbility",
            "UnlessCost$ 2",
            "UnlessPayer$ TriggeredActivator",
        ):
            self.assertIn(field, counter)

    def test_spells_with_mana_payment_below_two_are_raised_to_two(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()
        cost_floor = next(
            line
            for line in lines
            if line.startswith("S:Mode$ SetCost") and "Type$ Spell" in line
        )

        for field in (
            "ValidCard$ Card",
            "Type$ Spell",
            "Amount$ 2",
            "RaiseTo$ True",
        ):
            self.assertIn(field, cost_floor)
        self.assertIn("SVar:NonStackingEffect:True", lines)

    def test_registration_localization_and_catalogs_are_complete(self):
        self.assertEqual(
            1,
            EDITION.read_text(encoding="utf-8").splitlines().count(
                "191 R 锋鳞 @L. Lullabi & K. Turovec"
            ),
        )
        self.assertEqual(
            1,
            ZH_CN.read_text(encoding="utf-8").splitlines().count(
                f"锋鳞|锋鳞|生物～龙|{ORACLE}"
            ),
        )
        self.assertIn(
            "| 锋鳞 | `{2}{U}`，2/4 生物～龙 | `cards/blue/锋鳞.txt` | 191 |",
            CATALOG.read_text(encoding="utf-8"),
        )

        oracle_catalog = ORACLE_CATALOG.read_text(encoding="utf-8")
        self.assertIn("锋鳞\n版本：PH01 / 191", oracle_catalog)
        self.assertIn(f"Oracle：{ORACLE_RENDERED}", oracle_catalog)

    def test_hswiki_original_and_crop_compatible_art_are_preserved(self):
        images = (
            (
                ART_BACKUP,
                (3000, 4000),
                "C437F2761908C054DF33D7FAB55BDCDABE32CD9F92BBA9C69BBACBC8D7401C22",
            ),
            (
                ART_CROP,
                (3000, 2190),
                "5181BC40C5D32C88C571013A24A00E3CB8549347837A13E4986C4DFCC0E9008C",
            ),
        )
        for path, expected_size, expected_hash in images:
            self.assertTrue(path.is_file(), path)
            self.assertEqual(
                expected_hash,
                hashlib.sha256(path.read_bytes()).hexdigest().upper(),
                path,
            )
            with Image.open(path) as image:
                self.assertEqual("JPEG", image.format, path)
                self.assertEqual("RGB", image.mode, path)
                self.assertEqual(expected_size, image.size, path)

        with Image.open(ART_CROP) as image:
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
