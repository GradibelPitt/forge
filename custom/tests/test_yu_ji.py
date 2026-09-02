import unittest
from pathlib import Path
import struct


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "multicolor" / "于吉.txt"
EDITION = ROOT / "editions" / "BoTu_Three_Kingdoms_New_Chapter.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "yu_ji_source_20260826.png"
ART = ROOT / "cards" / "pictures" / "BT3K" / "于吉.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ZH_ORACLE = (
    "每回合中，当你施放第一个名称不是于吉的咒语时，你可以选择一个牌名。"
    "每位对手可以选择质疑你。若有牌手如此作，展示你的手牌。"
    "若其中没有具该名称的牌，每位质疑你的牌手各从你手上选择一张牌并将其放逐；"
    "若其中有具该名称的牌，每位质疑你的牌手各获得一个具有「你不能质疑」的徽记。\n"
    "若无人质疑你，或质疑失败，你可以视同该咒语为具所选牌名的牌来施放，"
    "并支付该牌的法术力费用而非该咒语的法术力费用。"
)


def image_info(path: Path):
    data = path.read_bytes()
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        width, height = struct.unpack(">II", data[16:24])
        return "PNG", width, height
    if data.startswith(b"\xff\xd8"):
        offset = 2
        while offset < len(data):
            if data[offset] != 0xFF:
                offset += 1
                continue
            marker = data[offset + 1]
            offset += 2
            if marker in (0xD8, 0xD9) or 0xD0 <= marker <= 0xD7:
                continue
            length = struct.unpack(">H", data[offset : offset + 2])[0]
            if marker in range(0xC0, 0xC4):
                height, width = struct.unpack(">HH", data[offset + 3 : offset + 7])
                return "JPEG", width, height
            offset += length
    raise AssertionError(f"Unsupported image: {path}")


class YuJiContractTest(unittest.TestCase):
    def test_characteristics_and_first_non_yu_ji_spell_trigger(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:于吉", lines)
        self.assertIn("ManaCost:2 U B R", lines)
        self.assertIn("Types:Legendary Creature Human Shaman", lines)
        self.assertIn("PT:3/4", lines)

        trigger = next(line for line in lines if line.startswith("T:Mode$ SpellCast"))
        self.assertIn("ValidCard$ Card.!named于吉", trigger)
        self.assertIn("ValidActivatingPlayer$ You", trigger)
        self.assertIn("ActivatorThisTurnCast$ EQ1", trigger)
        self.assertIn("OptionalDecider$ You", trigger)
        self.assertIn("TriggerZones$ Battlefield", trigger)

    def test_challenge_flow_uses_only_native_script_primitives(self):
        text = CARD.read_text(encoding="utf-8")

        for api in (
            "DB$ NameCard",
            "DB$ GenericChoice",
            "DB$ RevealHand",
            "DB$ RepeatEach",
            "DB$ ChooseCard",
            "DB$ ChangeZoneAll",
            "DB$ Effect",
            "DB$ Play",
            "CopyFromChosenName$ True",
            "RememberPlayed$ True",
            "DB$ Branch",
            "DB$ Cleanup",
        ):
            self.assertIn(api, text)

        self.assertIn("Player.NotedForYuJiChallenge", text)
        self.assertIn("Card.NamedCard", text)
        self.assertIn("Name$ Emblem — 于吉的缠怨", text)
        self.assertIn("Description$ 你不能质疑。", text)
        self.assertIn(
            "Opponent.!HasCardsInCommand_Effect.namedEmblem — 于吉的缠怨_GE1", text
        )
        self.assertNotIn("WithoutManaCost$ True", text)

    def test_registration_localization_documentation_and_art(self):
        self.assertIn(
            "4 M 于吉 @Custom", EDITION.read_text(encoding="utf-8").splitlines()
        )
        self.assertIn(
            f"于吉|于吉|传奇生物～人类／祭师|{ZH_ORACLE.replace(chr(10), r'\n')}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 于吉 | `{2}{U}{B}{R}` 3/4 传奇生物～人类／祭师 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        self.assertTrue(ART_BACKUP.is_file())
        self.assertEqual(("PNG", 528, 663), image_info(ART_BACKUP))

        self.assertTrue(ART.is_file())
        image_format, width, height = image_info(ART)
        self.assertEqual("JPEG", image_format)
        self.assertEqual((528, 385), (width, height))
        self.assertAlmostEqual(1.37, width / height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
