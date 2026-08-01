import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "white" / "扣子.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Buttons_full.jpg"
ART = ROOT / "cards" / "pictures" / "PH01" / "扣子.artcrop.jpg"


class ButtonsContractTest(unittest.TestCase):
    def test_characteristics_and_extort(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:扣子", text)
        self.assertIn("ManaCost:2 W W", text)
        self.assertIn("Types:Legendary Creature Zombie Shaman", text)
        self.assertIn("PT:4/4", text)
        self.assertIn("K:Extort", text)

    def test_enters_mills_and_returns_up_to_one_spell(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "T:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | "
            "ValidCard$ Card.Self | Execute$ TrigMill",
            text,
        )
        self.assertIn(
            "SVar:TrigMill:DB$ Mill | Defined$ You | NumCards$ 5 | "
            "RememberMilled$ True | SubAbility$ DBReturnMilled",
            text,
        )
        self.assertIn(
            "SVar:DBReturnMilled:DB$ ChangeZone | Hidden$ True | "
            "Origin$ Graveyard,Exile | Destination$ Hand | ChangeNum$ 1 | "
            "ChangeType$ Instant.IsRemembered,Sorcery.IsRemembered",
            text,
        )
        self.assertIn("SubAbility$ DBCleanupMilled", text)
        self.assertIn(
            "SVar:DBCleanupMilled:DB$ Cleanup | ClearRemembered$ True",
            text,
        )

    def test_sacrifice_color_match_and_free_cast_this_turn(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "A:AB$ ImmediateTrigger | Cost$ B Sac<1/Creature> | "
            "RememberObjects$ Sacrificed | Execute$ TrigReturnSpell",
            text,
        )
        self.assertIn(
            "SVar:TrigReturnSpell:DB$ ChangeZone | Origin$ Graveyard | "
            "Destination$ Hand | ValidTgts$ "
            "Instant.YouOwn+SharesColorWith TriggerRemembered,"
            "Sorcery.YouOwn+SharesColorWith TriggerRemembered",
            text,
        )
        self.assertIn(
            "RememberChanged$ True | SubAbility$ DBFreeCastEffect",
            text,
        )
        self.assertIn(
            "SVar:DBFreeCastEffect:DB$ Effect | RememberObjects$ Remembered | "
            "StaticAbilities$ FreeCast | ForgetOnMoved$ Hand | "
            "Duration$ UntilEndOfTurn | "
            "SubAbility$ DBCleanupReturned",
            text,
        )
        self.assertIn(
            "SVar:FreeCast:Mode$ Continuous | "
            "Affected$ Instant.IsRemembered,Sorcery.IsRemembered | "
            "MayPlay$ True | MayPlayWithoutManaCost$ True | "
            "AffectedZone$ Hand",
            text,
        )
        self.assertIn(
            "SVar:DBCleanupReturned:DB$ Cleanup | ClearRemembered$ True",
            text,
        )

    def test_registration_localization_and_original_art_crop(self):
        edition = EDITION.read_text(encoding="utf-8")
        self.assertIn("83 M 扣子 @Custom", edition)

        localization = ZH_CN.read_text(encoding="utf-8").splitlines()
        line = next(item for item in localization if item.startswith("扣子|扣子|"))
        self.assertIn("传奇生物～灵俑／祭师", line)
        self.assertIn("敲诈", line)
        self.assertIn("磨五张牌", line)
        self.assertIn("以此法所牺牲生物之颜色相同", line)
        self.assertIn("于本回合中，你可以免费施放该牌", line)

        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())
        with Image.open(ART_BACKUP) as image:
            self.assertEqual((3000, 4250), image.size)
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((3000, 2190), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
