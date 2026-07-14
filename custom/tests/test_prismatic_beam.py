import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "colorless" / "棱彩光束.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "棱彩光束.artcrop.jpg"
BACKUP = ROOT / "tools" / "card-artwork" / "1920px-Prismatic_Beam_full.jpg"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class PrismaticBeamContractTest(unittest.TestCase):
    def test_card_implements_targeted_damage_and_cost_reduction(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:棱彩光束", text)
        self.assertIn("ManaCost:4 U W R", text)
        self.assertIn("Types:Sorcery", text)
        self.assertIn("A:SP$ DamageAll", text)
        self.assertIn("ValidTgts$ Opponent", text)
        self.assertIn("ValidPlayers$ Targeted", text)
        self.assertIn("ValidCards$ Creature.TargetedPlayerCtrl,Planeswalker.TargetedPlayerCtrl", text)
        self.assertIn("NumDmg$ 3", text)
        self.assertIn("ReduceCost$ X", text)
        self.assertIn("SVar:X:Count$Valid Creature.TargetedPlayerCtrl,Planeswalker.TargetedPlayerCtrl", text)

    def test_registration_art_and_chinese_text(self):
        self.assertIn("25 R 棱彩光束 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertTrue(ART.is_file())
        self.assertTrue(BACKUP.is_file())
        expected = "棱彩光束|棱彩光束|法术|对目标对手以及他操控的每个生物和鹏洛客各造成3点伤害。目标对手每操控一个生物或鹏洛客，本牌的法术力费用减少1"
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())

    def test_art_is_rgb_landscape_crop(self):
        from PIL import Image

        with Image.open(ART) as image:
            self.assertEqual((1920, 1401), image.size)
            self.assertEqual("RGB", image.mode)


if __name__ == "__main__":
    unittest.main()
