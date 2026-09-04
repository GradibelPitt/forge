import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "blue" / "法术反制.txt"
BLUE_MYSTERY_CARD = ROOT / "cards" / "blue" / "蓝色奥秘.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
TOKEN_HS_EDITION = ROOT / "editions" / "Token_HS.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Counterspell_full_original.jpg"
ART = ROOT / "cards" / "pictures" / "PH01" / "法术反制.artcrop.jpg"
MYSTERY_BACK = ROOT / "cards" / "pictures" / "TOKEN_HS" / "蓝色奥秘.artcrop.jpg"
LEGACY_MYSTERY_BACK = ROOT / "tokens" / "pictures" / "mystery.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = "奥秘\\n反击目标法术或瞬间咒语。"
SOURCE_ART_SHA256 = "A5F4BFABB5623C7F6040328CDF0A091ED426A6A81684852F20C27C2684527825"


class CounterspellMysteryContractTest(unittest.TestCase):
    def test_card_uses_mystery_and_targets_only_instant_or_sorcery_spells(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        for line in (
            "Name:法术反制",
            "ManaCost:1 U U",
            "Types:Enchantment Mystery",
            "K:Mystery",
            "SVar:MysteryEffect:DB$ Counter | TargetType$ Spell | "
            "ValidTgts$ Instant,Sorcery | TgtPrompt$ 选择目标法术或瞬间咒语 | "
            "SpellDescription$ 反击目标法术或瞬间咒语。",
            f"Oracle:{ORACLE}",
        ):
            self.assertIn(line, lines)

    def test_registration_localization_and_art(self):
        self.assertIn(
            "138 R 法术反制 @Jason Chan",
            EDITION.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            f"法术反制|法术反制|结界～奥秘|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )

        self.assertEqual(
            SOURCE_ART_SHA256,
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        with Image.open(ART_BACKUP) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((1548, 1200), image.size)

        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((1548, 1130), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)

    def test_blue_mystery_back_is_an_actual_token_hs_card(self):
        self.assertEqual(
            [
                "Name:蓝色奥秘",
                "ManaCost:1 U U",
                "Types:Enchantment Mystery",
                "Oracle:你的对手隐藏了一些秘密。",
            ],
            BLUE_MYSTERY_CARD.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "8 C 蓝色奥秘 @Custom",
            TOKEN_HS_EDITION.read_text(encoding="utf-8").splitlines(),
        )
        self.assertTrue(MYSTERY_BACK.is_file())
        self.assertFalse(LEGACY_MYSTERY_BACK.exists())
        with Image.open(MYSTERY_BACK) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((960, 700), image.size)


if __name__ == "__main__":
    unittest.main()
