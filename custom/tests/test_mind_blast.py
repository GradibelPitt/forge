import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "multicolor" / "心灵震爆.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "心灵震爆.artcrop.jpg"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "心灵震爆_1-照片-1.jpg"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class MindBlastContractTest(unittest.TestCase):
    def test_card_is_a_red_black_shadow_sorcery(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:心灵震爆", text)
        self.assertIn("ManaCost:B R", text)
        self.assertIn("Types:Sorcery Shadow", text)

    def test_card_deals_five_damage_to_target_opponent(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "A:SP$ DealDamage | ValidTgts$ Opponent | NumDmg$ 5",
            text,
        )

    def test_card_is_registered_with_backup_and_dynamic_art(self):
        self.assertIn("38 C 心灵震爆 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())

        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((512, 374), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.02)

    def test_zh_cn_type_line_exposes_the_shadow_spell_category(self):
        expected = r"心灵震爆|心灵震爆|法术～暗影|对目标对手造成5点伤害。"
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())


if __name__ == "__main__":
    unittest.main()
