import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "black" / "咆哮魔.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-f2c78131-2933-4b64-9183-88f5ae2673c6.png"
)
ART = ROOT / "cards" / "pictures" / "PH01" / "咆哮魔.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = (
    "不忠（此生物在一位由你选择的牌手操控下进战场。）\\n"
    "每当咆哮魔受到伤害时，你随机弃一张牌。\\n"
    "如果咆哮魔能阻挡，则它必须阻挡。"
)


class HowlfiendContractTest(unittest.TestCase):
    def test_characteristics_and_disloyal_player_control_replacement(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:咆哮魔", text)
        self.assertIn("ManaCost:1 B B", text)
        self.assertIn("Types:Creature Demon", text)
        self.assertIn("PT:3/6", text)
        self.assertIn(
            "R:Event$ Moved | ValidCard$ Card.Self | Destination$ Battlefield | "
            "ReplaceWith$ DBChoosePlayer | Layer$ Control",
            text,
        )
        self.assertIn(
            "SVar:DBChoosePlayer:DB$ ChoosePlayer | Defined$ You | "
            "Choices$ Player | ChoiceTitle$ 选择一位接收CARDNAME的牌手 | "
            "AILogic$ Curse | SubAbility$ MoveToPlay",
            text,
        )
        self.assertIn(
            "SVar:MoveToPlay:DB$ ChangeZone | Hidden$ True | Origin$ All | "
            "Destination$ Battlefield | Defined$ ReplacedCard | "
            "GainControl$ ChosenPlayer | SubAbility$ DBCleanup",
            text,
        )
        self.assertIn(
            "SVar:DBCleanup:DB$ Cleanup | ClearChosenPlayer$ True",
            text,
        )

    def test_damage_makes_its_controller_discard_at_random(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "T:Mode$ DamageDoneOnce | ValidTarget$ Card.Self | "
            "TriggerZones$ Battlefield | Execute$ TrigDiscard",
            text,
        )
        self.assertIn(
            "SVar:TrigDiscard:DB$ Discard | Defined$ You | "
            "NumCards$ 1 | Mode$ Random",
            text,
        )
        self.assertIn(f"Oracle:{ORACLE}", text)

    def test_must_block_if_able(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "S:Mode$ MustBlock | ValidCreature$ Card.Self | "
            "Description$ 如果CARDNAME能阻挡，则它必须阻挡。",
            text,
        )

    def test_registration_localization_and_original_art_crop(self):
        self.assertIn(
            "86 R 咆哮魔 @Custom",
            EDITION.read_text(encoding="utf-8"),
        )
        self.assertIn(
            f"咆哮魔|咆哮魔|生物～恶魔|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )

        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())
        self.assertEqual(
            "0CEE722995C70AD1DA71F835E68AF9961CFAA322E897AEFA04F1E5FAC486A311",
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        with Image.open(ART_BACKUP) as image:
            self.assertEqual((450, 600), image.size)
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((450, 328), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
