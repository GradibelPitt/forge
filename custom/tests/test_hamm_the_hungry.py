import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "green" / "饥饿食客哈姆.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Hamm_the_Hungry_full.jpg"
ART = ROOT / "cards" / "pictures" / "PH01" / "饥饿食客哈姆.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

SOURCE_ORACLE = (
    "Trample\\n"
    "At the beginning of your end step, each opponent exiles cards from the top "
    "of their library until they exile a creature card. Put X +1/+1 counters on "
    "Hamm, where X is the number of cards exiled this way.\\n"
    "{R}, Remove a +1/+1 counter from Hamm: Hamm deals 1 damage to any target."
)
ZH_ORACLE = (
    "践踏\\n"
    "在你的结束步骤开始时，每位对手各从其牌库顶开始放逐牌，直到放逐一张生物牌为止。"
    "在哈姆上放置X个+1/+1指示物，X为以此法放逐之牌的数量。\\n"
    "{R}，从哈姆上移去一个+1/+1指示物：哈姆对任意一个目标造成1点伤害。"
)


class HammTheHungryContractTest(unittest.TestCase):
    def test_characteristics_and_end_step_exile_count(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:饥饿食客哈姆", text)
        self.assertIn("ManaCost:3 G G", text)
        self.assertIn("Types:Legendary Creature Minotaur Warrior", text)
        self.assertIn("PT:3/3", text)
        self.assertIn("K:Trample", text)
        self.assertIn(
            "T:Mode$ Phase | Phase$ End of Turn | ValidPlayer$ You | "
            "TriggerZones$ Battlefield | Execute$ TrigDigUntil",
            text,
        )
        self.assertIn(
            "SVar:TrigDigUntil:DB$ DigUntil | Defined$ Player.Opponent | "
            "Valid$ Creature | ValidDescription$ creature | "
            "FoundDestination$ Exile | RevealedDestination$ Exile | "
            "RememberFound$ True | RememberRevealed$ True | "
            "SubAbility$ DBPutCounter",
            text,
        )
        self.assertIn(
            "SVar:DBPutCounter:DB$ PutCounter | Defined$ Self | "
            "CounterType$ P1P1 | CounterNum$ X | SubAbility$ DBCleanup",
            text,
        )
        self.assertIn("SVar:X:Count$ValidExile Card.IsRemembered", text)
        self.assertIn("SVar:DBCleanup:DB$ Cleanup | ClearRemembered$ True", text)

    def test_counter_fueled_damage_activation(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "A:AB$ DealDamage | Cost$ R SubCounter<1/P1P1> | "
            "ValidTgts$ Any | NumDmg$ 1",
            text,
        )
        self.assertIn(f"Oracle:{SOURCE_ORACLE}", text)

    def test_registration_localization_and_original_art_crop(self):
        self.assertIn("82 M 饥饿食客哈姆 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertIn(
            f"饥饿食客哈姆|饥饿食客哈姆|传奇生物～牛头怪／战士|{ZH_ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )

        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())
        self.assertEqual(
            "7A562055D079040378DF5F0258F63752E2E24CB75D721227ECCEC4E00DE938D1",
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((5249, 3831), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
