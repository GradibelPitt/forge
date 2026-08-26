from hashlib import sha256
from pathlib import Path
import struct
import unittest


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "red" / "开进码头.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
CARD_ART_SOURCE = ROOT / "tools" / "card-artwork" / "Raid_the_Docks_full_hswiki.jpg"
EMBLEM_ART_SOURCE = ROOT / "tools" / "card-artwork" / "The_Juggernaut_full_hswiki.jpg"
CARD_ART = ROOT / "cards" / "pictures" / "PH01" / "开进码头.artcrop.jpg"
EMBLEM_ART = ROOT / "tokens" / "pictures" / "emblem_destroyer_warship.png"
TRANSLATIONS = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"
RENDERER = FORGE_ROOT / "forge-gui-desktop" / "src" / "main" / "java" / "forge" / "toolbox" / "imaging" / "FCardImageRenderer.java"
HUMAN_CONTROLLER = FORGE_ROOT / "forge-gui" / "src" / "main" / "java" / "forge" / "player" / "PlayerControllerHuman.java"


def jpeg_size_and_components(path: Path):
    data = path.read_bytes()
    index = 2
    while index < len(data):
        if data[index] != 0xFF:
            index += 1
            continue
        marker = data[index + 1]
        index += 2
        if marker in (0xD8, 0xD9):
            continue
        length = int.from_bytes(data[index : index + 2], "big")
        if marker in range(0xC0, 0xC4):
            height = int.from_bytes(data[index + 3 : index + 5], "big")
            width = int.from_bytes(data[index + 5 : index + 7], "big")
            components = data[index + 7]
            return width, height, components
        index += length
    raise AssertionError(f"No JPEG frame header found in {path}")


def png_size_and_color_type(path: Path):
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        raise AssertionError(f"Not a PNG with an IHDR chunk: {path}")
    width, height = struct.unpack(">II", data[16:24])
    return width, height, data[25]


class RaidTheDocksContractTest(unittest.TestCase):
    def read_card(self):
        self.assertTrue(CARD.is_file(), CARD)
        return CARD.read_text(encoding="utf-8")

    def test_card_is_a_one_mana_red_quest(self):
        text = self.read_card()
        self.assertIn("Name:开进码头", text)
        self.assertIn("ManaCost:R", text)
        self.assertIn("Types:Enchantment Quest", text)
        self.assertIn(
            "K:Quest:Pirate;Equipment;Card.Historic:Your starting deck contains "
            "a Pirate card, an Equipment card, and a historic card.",
            text,
        )

    def test_two_pirates_advance_each_step_and_progress_caps_at_three(self):
        text = self.read_card()
        self.assertIn("Mode$ SpellCast | ValidCard$ Pirate", text)
        self.assertIn("TriggerZones$ Command", text)
        self.assertIn("Card.Self+counters_LT6_QUEST", text)
        self.assertIn("CounterType$ QUEST | CounterNum$ 1", text)

    def test_all_three_step_abilities_are_exhaust_and_sorcery_speed(self):
        text = self.read_card()
        self.assertEqual(3, text.count("Exhaust$ True"))
        self.assertEqual(3, text.count("SorcerySpeed$ True"))
        self.assertIn("counters_GE2_QUEST", text)
        self.assertIn("counters_GE4_QUEST", text)
        self.assertIn("counters_GE6_QUEST", text)

    def test_first_step_puts_a_small_equipment_from_the_library_onto_the_battlefield(self):
        text = self.read_card()
        self.assertIn("Origin$ Library | Destination$ Battlefield", text)
        self.assertIn("ChangeType$ Equipment.cmcLE2", text)
        self.assertIn("Reveal$ True | Shuffle$ True", text)

    def test_second_step_deals_two_damage_to_up_to_two_targets(self):
        text = self.read_card()
        self.assertIn(
            "ValidTgts$ Any | TargetMin$ 0 | TargetMax$ 2 | "
            "TgtPrompt$ Select up to two targets | NumDmg$ 2",
            text,
        )

    def test_third_step_creates_the_destroyer_warship_emblem(self):
        text = self.read_card()
        self.assertIn(
            "A:AB$ Effect | Cost$ 5 | ActivationZone$ Command | "
            "SorcerySpeed$ True",
            text,
        )
        self.assertIn("Name$ Emblem — 毁灭战舰", text)
        self.assertIn("Image$ emblem_destroyer_warship", text)
        self.assertNotIn("Types:Legendary Artifact", text)
        self.assertNotIn("TokenScript$", text)

    def test_emblem_conjures_a_random_pirate_and_equipment_then_deals_damage(self):
        text = self.read_card()
        self.assertIn("Phase$ Upkeep | ValidPlayer$ You | TriggerZones$ Command", text)
        self.assertIn("AtRandom$ True | ValidCards$ Pirate", text)
        self.assertIn("AtRandom$ True | ValidCards$ Equipment", text)
        self.assertEqual(2, text.count("Name$ ChosenName | Conjure$ True | Zone$ Battlefield"))
        self.assertIn("SVar:WarshipDamage:DB$ DealDamage", text)

    def test_card_and_emblem_use_the_preserved_hswiki_art(self):
        self.assertEqual(
            "f39210073efb26d8b0a2493b0cbae70d74cacfc611e4aa34b6f90f0ad4eab154",
            sha256(CARD_ART_SOURCE.read_bytes()).hexdigest(),
        )
        self.assertEqual(
            "c7dd3c7737f791657dc2285018fbd30ed670d2e08fb7d785a8349c45635b5780",
            sha256(EMBLEM_ART_SOURCE.read_bytes()).hexdigest(),
        )
        card_width, card_height, card_components = jpeg_size_and_components(CARD_ART)
        self.assertEqual(3, card_components)
        self.assertAlmostEqual(1.37, card_width / card_height, places=2)
        emblem_width, emblem_height, emblem_color_type = png_size_and_color_type(EMBLEM_ART)
        self.assertEqual(6, emblem_color_type, "PNG color type 6 is RGBA")
        self.assertEqual((1024, 1365), (emblem_width, emblem_height))

    def test_edition_and_four_field_chinese_localization_are_registered(self):
        self.assertIn("128 M 开进码头 @Arthur Bozonnet", EDITION.read_text(encoding="utf-8"))
        rows = [
            line
            for line in TRANSLATIONS.read_text(encoding="utf-8").splitlines()
            if line.startswith("开进码头|")
        ]
        self.assertEqual(1, len(rows))
        self.assertGreaterEqual(rows[0].count("|"), 3)
        self.assertIn("结界～任务", rows[0])
        self.assertIn("任务～你的起始套牌中包含海盗牌、武具牌和史迹牌。", rows[0])
        self.assertIn("初始阶段为0，使用两张海盗牌后达到下一阶段，只能于法术时机如此做。", rows[0])
        self.assertNotIn("任务指示物", rows[0])
        self.assertIn("毁灭战舰", rows[0])

    def test_quest_uses_the_saga_visual_layout_without_saga_rules(self):
        text = self.read_card()
        renderer = RENDERER.read_text(encoding="utf-8")
        self.assertIn("Types:Enchantment Quest", text)
        self.assertNotIn("Types:Enchantment Saga", text)
        self.assertNotIn("Types:Enchantment Class", text)
        self.assertIn('boolean isQuest = state.getType().hasSubtype("Quest");', renderer)
        self.assertIn("if (isSaga || isQuest || isClass || isDungeon)", renderer)
        self.assertIn("(isSaga || isQuest ? artWidth : 0)", renderer)

    def test_gui_trigger_bridge_uses_the_current_no_stack_signature(self):
        controller = HUMAN_CONTROLLER.read_text(encoding="utf-8")
        self.assertIn(
            "return PlaySpellAbility.playSpellAbilityNoStack(this, player, wrapperAbility, false);",
            controller,
        )


if __name__ == "__main__":
    unittest.main()
