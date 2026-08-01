import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "multicolor" / "米斯塔·维斯塔.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Mistah_Vistah_full.jpg"
ART = ROOT / "cards" / "pictures" / "PH01" / "米斯塔·维斯塔.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

SOURCE_ORACLE = (
    "Vigilance\\n"
    "When CARDNAME enters, until the end of your next turn, instant and sorcery spells "
    "you control have rebound.\\n"
    "{U}, Return a land you control to its owner's hand: Tap target creature. "
    "Put a stun counter on it."
)
ZH_ORACLE = (
    "警戒\\n"
    "当维斯塔进场时，直到你下一个回合的回合结束，由你操控的瞬间与法术咒语具有弹回异能。\\n"
    "{U}，将一个由你操控的地移回其拥有者手上：横置目标生物。在其上放置一个晕眩指示物。"
)


class MistahVistahContractTest(unittest.TestCase):
    def test_characteristics_and_temporary_rebound_grant(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:米斯塔·维斯塔", text)
        self.assertIn("ManaCost:4 G G", text)
        self.assertIn("Types:Legendary Creature Troll Monk", text)
        self.assertIn("PT:5/5", text)
        self.assertIn("K:Vigilance", text)
        self.assertIn(
            "T:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | "
            "ValidCard$ Card.Self | Execute$ TrigRebound",
            text,
        )
        self.assertIn(
            "SVar:TrigRebound:DB$ Effect | StaticAbilities$ GrantRebound | "
            "Duration$ UntilTheEndOfYourNextTurn",
            text,
        )
        self.assertIn(
            "SVar:GrantRebound:Mode$ Continuous | AddKeyword$ Rebound | "
            "Affected$ Instant.YouCtrl,Sorcery.YouCtrl | AffectedZone$ Stack",
            text,
        )

    def test_land_return_activation_taps_and_adds_a_stun_counter(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "A:AB$ Tap | Cost$ U Return<1/Land/land> | ValidTgts$ Creature | "
            "SubAbility$ DBStun",
            text,
        )
        self.assertIn(
            "SVar:DBStun:DB$ PutCounter | Defined$ Targeted | "
            "CounterType$ Stun | CounterNum$ 1",
            text,
        )
        self.assertIn(f"Oracle:{SOURCE_ORACLE}", text)

    def test_registration_localization_and_art(self):
        self.assertIn("77 M 米斯塔·维斯塔 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertIn(
            f"米斯塔·维斯塔|米斯塔·维斯塔|传奇生物～巨魔／修行僧|{ZH_ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
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
