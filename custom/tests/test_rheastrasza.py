import hashlib
import unittest
from pathlib import Path

from PIL import Image


CUSTOM = Path(__file__).resolve().parents[1]
FORGE_ROOT = CUSTOM.parent
CARD = CUSTOM / "cards" / "green" / "瑞亚斯塔萨.txt"
EDITION = CUSTOM / "editions" / "Placeholder_Set.txt"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"
CATALOG = CUSTOM / "CARDS.md"
TOKEN_HS_EDITION = CUSTOM / "editions" / "Token_HS.txt"
EMBLEM = CUSTOM / "cards" / "colorless" / "emblem_purified_dragon_nest.txt"
RHEASTRASZA_ART_BACKUP = (
    CUSTOM / "tools" / "card-artwork" / "WW_824_Rheastrasza_full_hswiki.jpg"
)
RHEASTRASZA_ART = (
    CUSTOM / "cards" / "pictures" / "PH01" / "瑞亚斯塔萨.artcrop.jpg"
)
NEST_ART_BACKUP = (
    CUSTOM
    / "tools"
    / "card-artwork"
    / "WW_824t_Purified_Dragon_Nest_full_hswiki.jpg"
)
NEST_ART = (
    CUSTOM
    / "cards"
    / "pictures"
    / "TOKEN_HS"
    / "Emblem — 纯净龙巢.artcrop.jpg"
)

ORACLE = (
    "飞行\\n"
    "作为施放此咒语的额外费用，你可以请援龙。（你可以选择一个由你操控的龙，或从你手上展示一张龙牌。）\\n"
    "当你施放瑞亚斯塔萨时，如果你的起始套牌中每张非地牌的名称均不相同且你施放此咒语时请援了龙，"
    "则你获得名为纯净龙巢的徽记，其具有「在你的战斗前行动阶段开始时，加三点任意颜色组合的法术力。"
    "此法术力只能用于施放龙咒语。发现一张龙牌。你可以用任意颜色的法术力支付以此法发现之牌的法术力费用。」"
)
NEST_ORACLE = (
    "在你的战斗前行动阶段开始时，加三点任意颜色组合的法术力。"
    "此法术力只能用于施放龙咒语。发现一张龙牌。"
    "你可以用任意颜色的法术力支付以此法发现之牌的法术力费用。"
)


class RheastraszaContractTest(unittest.TestCase):
    def test_characteristics_match_the_revised_design(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:瑞亚斯塔萨", lines)
        self.assertIn("ManaCost:3 G G G", lines)
        self.assertIn("Types:Legendary Creature Dragon", lines)
        self.assertIn("PT:6/6", lines)
        self.assertIn("K:Flying", lines)
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

    def test_cast_trigger_requires_both_behold_and_a_highlander_starting_deck(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()
        trigger = next(line for line in lines if line.startswith("T:Mode$ SpellCast"))

        for field in (
            "ValidCard$ Card.Self",
            "TriggerZones$ Stack",
            "CheckSVar$ CastSA>Count$OptionalGenericCostPaid.1.0",
            "Execute$ CreatePureNest",
        ):
            self.assertIn(field, trigger)

        create = next(line for line in lines if line.startswith("SVar:CreatePureNest:"))
        for field in (
            "DB$ MakeCard",
            "Defined$ You",
            "Name$ Emblem — 纯净龙巢",
            "Zone$ Command",
            "AsEmblem$ True",
            "ConditionCheckSVar$ StartingDeckDuplicateNonlandNames",
            "ConditionSVarCompare$ EQ0",
        ):
            self.assertIn(field, create)
        self.assertFalse(any("DB$ Effect" in line for line in lines))
        self.assertFalse(any(line.startswith("SVar:PureNestMain:") for line in lines))
        self.assertIn(
            "SVar:StartingDeckDuplicateNonlandNames:Count$StartingDeckDuplicateNonlandNames",
            lines,
        )

    def test_pure_nest_is_a_real_emblem_with_its_own_ability_chain(self):
        lines = EMBLEM.read_text(encoding="utf-8").splitlines()
        self.assertIn("Name:Emblem — 纯净龙巢", lines)
        self.assertIn("ManaCost:no cost", lines)
        self.assertIn("Types:Emblem", lines)

        phase = next(line for line in lines if line.startswith("T:Mode$ Phase"))
        for field in (
            "Mode$ Phase",
            "Phase$ Main1",
            "ValidPlayer$ You",
            "TriggerZones$ Command",
            "Execute$ AddDragonMana",
        ):
            self.assertIn(field, phase)

        mana = next(line for line in lines if line.startswith("SVar:AddDragonMana:"))
        for field in (
            "DB$ Mana",
            "Produced$ Combo Any",
            "Amount$ 3",
            "RestrictValid$ Spell.Dragon",
            "SubAbility$ DiscoverDragon",
        ):
            self.assertIn(field, mana)

        discover = next(line for line in lines if line.startswith("SVar:DiscoverDragon:"))
        for field in (
            "DB$ CardDiscover",
            "Defined$ You",
            "Source$ CardDatabase",
            "ValidCards$ Dragon",
            "OptionCount$ 3",
            "Destination$ Hand",
            "RememberChosen$ True",
            "SubAbility$ GrantHarmony",
        ):
            self.assertIn(field, discover)

    def test_discovered_card_perpetually_gains_harmony(self):
        lines = EMBLEM.read_text(encoding="utf-8").splitlines()
        harmony = next(line for line in lines if line.startswith("SVar:GrantHarmony:"))

        for field in (
            "DB$ Pump",
            "Defined$ Remembered",
            "PumpZone$ Hand",
            "KW$ Harmony",
            "Duration$ Perpetual",
            "SubAbility$ Cleanup",
        ):
            self.assertIn(field, harmony)
        self.assertIn("SVar:Cleanup:DB$ Cleanup | ClearRemembered$ True", lines)

    def test_registration_localization_and_catalog_are_complete(self):
        self.assertEqual(
            1,
            EDITION.read_text(encoding="utf-8").splitlines().count(
                "186 M 瑞亚斯塔萨 @Custom"
            ),
        )
        self.assertEqual(
            1,
            ZH_CN.read_text(encoding="utf-8").splitlines().count(
                f"瑞亚斯塔萨|瑞亚斯塔萨|传奇生物～龙|{ORACLE}"
            ),
        )
        self.assertEqual(
            1,
            TOKEN_HS_EDITION.read_text(encoding="utf-8").splitlines().count(
                "9 C Emblem — 纯净龙巢 @Custom"
            ),
        )
        self.assertEqual(
            1,
            ZH_CN.read_text(encoding="utf-8").splitlines().count(
                f"Emblem — 纯净龙巢|Emblem — 纯净龙巢|徽记|{NEST_ORACLE}"
            ),
        )
        self.assertIn(
            "| 瑞亚斯塔萨 | `{3}{G}{G}{G}`，6/6 传奇生物～龙 | "
            "`cards/green/瑞亚斯塔萨.txt` | 186 |",
            CATALOG.read_text(encoding="utf-8"),
        )
        self.assertIn(
            "| Emblem — 纯净龙巢 | 无法术力费用的实体徽记定义 | "
            "`cards/colorless/emblem_purified_dragon_nest.txt` | TOKEN_HS / 9 |",
            CATALOG.read_text(encoding="utf-8"),
        )

    def test_wiki_originals_and_crop_compatible_art_are_preserved(self):
        images = (
            (
                RHEASTRASZA_ART_BACKUP,
                (3000, 4000),
                "BD4BCC9239184DFF718B454BEB7E7755B80635080489A30705BE41B7C3F0DB04",
            ),
            (
                RHEASTRASZA_ART,
                (3000, 2190),
                "43884AAA390038ABDDF956C6E43C88D49AE066BE77F6E583D1EDB9A191F18353",
            ),
            (
                NEST_ART_BACKUP,
                (900, 1200),
                "2EE8435F52F207BEDAB720132E591D22865EF4A99B88BD1D7DAB6A0B4F4DFF9E",
            ),
            (
                NEST_ART,
                (900, 657),
                "6C4E5D5A7F3B3BB1CE320EEB334030F71E087FAC2405A3DC16E51507A7473FCA",
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

        for path in (RHEASTRASZA_ART, NEST_ART):
            with Image.open(path) as image:
                self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
