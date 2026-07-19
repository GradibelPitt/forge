import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "red" / "召唤师达克玛洛.txt"
TOKEN = ROOT / "tokens" / "c_a_dakmaro_munitions.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "照片-2.jpg"
ART = ROOT / "cards" / "pictures" / "PH01" / "召唤师达克玛洛.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class SummonerDakmaroContractTest(unittest.TestCase):
    def test_characteristics_and_leaves_battlefield_trigger_doubling(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:召唤师达克玛洛", text)
        self.assertIn("ManaCost:2 R R", text)
        self.assertIn("Types:Legendary Creature Zombie Warlock", text)
        self.assertIn("PT:4/4", text)
        self.assertIn("K:Deathtouch", text)
        self.assertIn(
            "S:Mode$ Panharmonicon | ValidMode$ ChangesZone,ChangesZoneAll | "
            "ValidCard$ Permanent.YouCtrl | ValidCause$ Permanent | "
            "Origin$ Battlefield",
            text,
        )

    def test_activation_sacrifices_any_nontoken_permanent_and_creates_munitions(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "A:AB$ PutCounter | Cost$ B Sac<1/Permanent.!token/nontoken permanent> | "
            "Defined$ Self | CounterType$ P1P1 | CounterNum$ 1 | "
            "SubAbility$ CreateMunitions",
            text,
        )
        self.assertNotIn("Permanent.!token+Other", text)
        self.assertIn(
            "SVar:CreateMunitions:DB$ Token | "
            "TokenScript$ c_a_dakmaro_munitions | TokenOwner$ You",
            text,
        )

    def test_munitions_token_has_the_requested_leaves_battlefield_trigger(self):
        text = TOKEN.read_text(encoding="utf-8")

        self.assertIn("Name:军械", text)
        self.assertIn("ManaCost:no cost", text)
        self.assertIn("Types:Artifact", text)
        self.assertIn(
            "T:Mode$ ChangesZone | Origin$ Battlefield | Destination$ Any | "
            "ValidCard$ Card.Self | Execute$ TrigDamage",
            text,
        )
        self.assertIn(
            "SVar:TrigDamage:DB$ DealDamage | ValidTgts$ Any | NumDmg$ 2",
            text,
        )

    def test_registration_localization_and_art(self):
        self.assertIn(
            "74 M 召唤师达克玛洛 @Custom",
            EDITION.read_text(encoding="utf-8"),
        )
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())
        expected = (
            "召唤师达克玛洛|召唤师达克玛洛|传奇生物～灵俑／邪术师|"
            "死触\\n"
            "如果由你操控之永久物的触发式异能因永久物离开战场而触发，"
            "则该异能额外触发一次。\\n"
            "{B}，牺牲一个非衍生物的永久物：在达克玛洛上放置一个+1/+1指示物。"
            "派出一个名称为军械的无色衍生神器，且具有「当此衍生物离开战场时，"
            "它对任意一个目标造成2点伤害。」"
        )
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())

        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertGreater(image.width, image.height)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
