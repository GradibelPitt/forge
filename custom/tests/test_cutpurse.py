import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "black" / "窃贼.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "cutpurse_horsley.png"
ART = ROOT / "cards" / "pictures" / "PH01" / "窃贼.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class CutpurseContractTest(unittest.TestCase):
    def test_characteristics_and_attack_treasure_trigger(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:窃贼", text)
        self.assertIn("ManaCost:1 B", text)
        self.assertIn("Types:Creature Zombie Rogue", text)
        self.assertIn("PT:2/2", text)

        trigger = next(line for line in text.splitlines() if line.startswith("T:Mode$ Attacks"))
        self.assertIn("ValidCard$ Card.Self", trigger)
        self.assertIn("Execute$ TrigToken", trigger)
        self.assertIn(
            "SVar:TrigToken:DB$ Token | TokenScript$ c_a_treasure_sac | TokenOwner$ You",
            text,
        )

    def test_registration_localization_and_art(self):
        self.assertIn("120 R 窃贼 @Ralph Horsley", EDITION.read_text(encoding="utf-8"))

        localization = ZH_CN.read_text(encoding="utf-8").splitlines()
        self.assertIn(
            "窃贼|窃贼|生物～灵俑／浪客|每当窃贼攻击时，派出一个珍宝衍生物。",
            localization,
        )

        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertGreater(image.width, image.height)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
