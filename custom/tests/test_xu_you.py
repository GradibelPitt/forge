import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "multicolor" / "许攸.txt"
EDITION = ROOT / "editions" / "BoTu_Three_Kingdoms_New_Chapter.txt"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-cf98aba8-4adc-46a6-aa00-fcee9dafaf32.png"
)
ART = ROOT / "cards" / "pictures" / "BT3K" / "许攸.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = (
    "你改为从你的牌库底抓牌。"
    "你的对手可以略过其抓牌步骤，然后从你的牌库顶抓至多两张牌。\\n"
    "每当你从手上施放一个瞬间或法术咒语时，于该咒语结算时，"
    "你可以改为将该牌置于你的牌库顶而非坟墓场。若你如此做，抓一张牌。"
    "每种类别至多触发一次。\\n"
    "{T}：抓一张牌，然后弃两张牌并在许攸上放置一个转换指示物。\\n"
    "{T}，从许攸上移去一个转换指示物：抓两张牌，然后弃一张牌。"
)


class XuYouContractTest(unittest.TestCase):
    def test_characteristics_and_draw_replacement(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:许攸", lines)
        self.assertIn("ManaCost:1 B U", lines)
        self.assertIn("Types:Legendary Creature Human Advisor", lines)
        self.assertIn("PT:2/3", lines)

        bottom_draw = next(line for line in lines if line.startswith("S:Mode$ Continuous"))
        self.assertIn("Affected$ You", bottom_draw)
        self.assertIn(
            "AddKeyword$ You draw cards from the bottom of your library instead of the top of your library.",
            bottom_draw,
        )

        replacement = next(
            line for line in lines if line.startswith("R:Event$ BeginPhase")
        )
        self.assertIn("ValidPlayer$ Opponent", replacement)
        self.assertIn("Phase$ Draw", replacement)
        self.assertIn("Layer$ Other", replacement)
        self.assertIn("Optional$ True", replacement)
        self.assertIn("ReplaceWith$ DrawFromXuYou", replacement)
        self.assertNotIn("FirstCardInDrawStep$", replacement)

        replacement_draw = next(
            line for line in lines if line.startswith("SVar:DrawFromXuYou:")
        )
        self.assertIn("DB$ Draw", replacement_draw)
        self.assertIn("Defined$ ReplacedPlayer", replacement_draw)
        self.assertIn("NumCards$ 2", replacement_draw)
        self.assertIn("Upto$ True", replacement_draw)
        self.assertIn("FromLibrary$ You", replacement_draw)

    def test_instant_sorcery_trigger_and_feather_timing(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()
        triggers = [line for line in lines if line.startswith("T:Mode$ SpellCast")]
        self.assertEqual(1, len(triggers))
        self.assertFalse(any("ValidCard$ Permanent" in line for line in triggers))
        self.assertFalse(any(line.startswith("SVar:PermanentDraw:") for line in lines))

        instant_sorcery = triggers[0]
        self.assertIn(
            "ValidCard$ Instant.wasCastFromYourHandByYou,Sorcery.wasCastFromYourHandByYou",
            instant_sorcery,
        )
        self.assertIn("ActivatorThisTurnCastSharedCardType$ EQ0", instant_sorcery)
        self.assertIn("ActivatorThisTurnCastSharedCardTypeValid$ Card", instant_sorcery)
        self.assertIn("每种类别至多触发一次。", instant_sorcery)
        self.assertNotIn("若其类别与", instant_sorcery)
        self.assertNotIn("OptionalDecider$", instant_sorcery)
        self.assertIn("Execute$ DelayedTop", instant_sorcery)
        self.assertFalse(any(line.startswith("SVar:SpellDraw:") for line in lines))

        delayed = next(line for line in lines if line.startswith("SVar:DelayedTop:"))
        self.assertIn("RememberObjects$ TriggeredCard", delayed)
        self.assertIn("ConditionDefined$ TriggeredCard", delayed)
        self.assertIn("ConditionPresent$ Card", delayed)
        self.assertIn("ConditionZone$ Stack", delayed)
        self.assertIn("ExileOnMoved$ Stack", delayed)

        replacement = next(
            line for line in lines if line.startswith("SVar:MoveToYourTopReplace:")
        )
        self.assertIn("Origin$ Stack", replacement)
        self.assertIn("Destination$ Graveyard", replacement)
        self.assertIn("Fizzle$ False", replacement)
        self.assertIn("Optional$ True", replacement)

        move = next(line for line in lines if line.startswith("SVar:ReplaceYourTop:"))
        self.assertIn("Defined$ ReplacedCard", move)
        self.assertIn("Destination$ Library", move)
        self.assertIn("DestinationPlayer$ You", move)
        self.assertIn("LibraryPosition$ 0", move)
        self.assertIn("SubAbility$ DrawAfterTop", move)

        draw_after_top = next(
            line for line in lines if line.startswith("SVar:DrawAfterTop:")
        )
        self.assertIn("DB$ Draw", draw_after_top)
        self.assertIn("Defined$ You", draw_after_top)
        self.assertIn("NumCards$ 1", draw_after_top)

    def test_conversion_abilities(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()
        abilities = [line for line in lines if line.startswith("A:AB$ Draw")]
        self.assertEqual(2, len(abilities))

        create = next(line for line in abilities if "Cost$ T |" in line)
        self.assertIn("NumCards$ 1", create)
        self.assertIn("SubAbility$ DiscardTwo", create)
        discard_two = next(line for line in lines if line.startswith("SVar:DiscardTwo:"))
        self.assertIn("NumCards$ 2", discard_two)
        self.assertIn("SubAbility$ AddConversion", discard_two)
        add_counter = next(
            line for line in lines if line.startswith("SVar:AddConversion:")
        )
        self.assertIn("CounterType$ CONVERSION", add_counter)

        convert = next(line for line in abilities if "SubCounter<1/CONVERSION>" in line)
        self.assertIn("Cost$ T SubCounter<1/CONVERSION>", convert)
        self.assertIn("NumCards$ 2", convert)
        self.assertIn("SubAbility$ DiscardOne", convert)
        discard_one = next(line for line in lines if line.startswith("SVar:DiscardOne:"))
        self.assertIn("NumCards$ 1", discard_one)

    def test_registration_localization_art_and_documentation(self):
        self.assertIn("Code=BT3K", EDITION.read_text(encoding="utf-8"))
        self.assertIn("Name=博图三国新篇", EDITION.read_text(encoding="utf-8"))
        self.assertIn("1 M 许攸 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertIn(
            f"许攸|许攸|传奇生物～人类／参谋|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 许攸 | `{1}{B}{U}` 2/3 传奇生物～人类／参谋 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        self.assertTrue(ART_BACKUP.is_file())
        self.assertEqual(
            "E0BA92D9BAF87529F5799273E54D517F9369E65C7E4B47BACD14E878F21ED91A",
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        self.assertTrue(ART.is_file())
        self.assertEqual(
            "31DABF401E312D2BA890478ADD679D424DF7A5196520103C9C8635761E845933",
            hashlib.sha256(ART.read_bytes()).hexdigest().upper(),
        )
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((574, 419), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
