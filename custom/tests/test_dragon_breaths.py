from pathlib import Path
import unittest


CUSTOM = Path(__file__).resolve().parents[1]
ROOT = CUSTOM.parent
PRIEST = CUSTOM / "cards" / "white" / "龙鳞祭司.txt"
ARCANE = CUSTOM / "cards" / "multicolor" / "奥术吐息.txt"
DREAM = CUSTOM / "cards" / "multicolor" / "梦境吐息.txt"
EDITION = CUSTOM / "editions" / "Placeholder_Set.txt"
LOCALIZATION = ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class DragonBreathsContractTest(unittest.TestCase):
    def test_dragon_scale_priest_characteristics_and_behold_return(self):
        text = PRIEST.read_text(encoding="utf-8")
        self.assertIn("ManaCost:W", text)
        self.assertIn("Types:Creature Human Cleric", text)
        self.assertIn("PT:1/2", text)
        self.assertIn("T:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | ValidCard$ Card.Self | Execute$ TrigReturn", text)
        self.assertIn("SVar:TrigReturn:AB$ ChangeZone | Cost$ Behold<1/Dragon>", text)
        self.assertIn("Origin$ Graveyard | Destination$ Hand", text)
        self.assertIn("ValidTgts$ Instant.YouOwn,Sorcery.YouOwn", text)

    def test_arcane_breath_optional_behold_damage_and_draw(self):
        text = ARCANE.read_text(encoding="utf-8")
        self.assertIn("ManaCost:U R", text)
        self.assertIn("Types:Instant", text)
        self.assertIn("Cost$ Behold<1/Dragon>", text)
        self.assertIn("A:SP$ DealDamage | ValidTgts$ Creature", text)
        self.assertIn("NumDmg$ 2", text)
        self.assertIn("SubAbility$ DBDraw", text)
        self.assertIn("SVar:DBDraw:DB$ Draw | Condition$ OptionalCost | ConditionOptionalPaid$ True | Defined$ You | NumCards$ 1", text)

    def test_dream_breath_optional_behold_draw_and_tapped_basic(self):
        text = DREAM.read_text(encoding="utf-8")
        self.assertIn("ManaCost:G U", text)
        self.assertIn("Types:Sorcery", text)
        self.assertIn("Cost$ Behold<1/Dragon>", text)
        self.assertIn("A:SP$ Draw | Defined$ You | NumCards$ 1 | SubAbility$ DBRamp", text)
        self.assertIn("SVar:DBRamp:DB$ ChangeZone | Condition$ OptionalCost | ConditionOptionalPaid$ True", text)
        self.assertIn("Origin$ Library | Destination$ Battlefield | Tapped$ True", text)
        self.assertIn("ChangeType$ Land.Basic", text)
        self.assertIn("ShuffleNonMandatory$ True", text)

    def test_registration_and_localization(self):
        edition = EDITION.read_text(encoding="utf-8")
        self.assertIn("140 U 龙鳞祭司 @Custom", edition)
        self.assertIn("141 C 奥术吐息 @Custom", edition)
        self.assertIn("142 C 梦境吐息 @Custom", edition)
        localization = LOCALIZATION.read_text(encoding="utf-8")
        self.assertIn("龙鳞祭司|龙鳞祭司|生物～人类／祭师|", localization)
        self.assertIn("奥术吐息|奥术吐息|瞬间|", localization)
        self.assertIn("梦境吐息|梦境吐息|法术|", localization)


if __name__ == "__main__":
    unittest.main()
