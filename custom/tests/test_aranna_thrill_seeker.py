import hashlib
import unittest
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "white" / "极限追逐者阿兰娜.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Aranna_Thrill_Seeker_full.jpg"
ART = ROOT / "cards" / "pictures" / "PH01" / "极限追逐者阿兰娜.artcrop.jpg"

ZH_ORACLE = (
    "如果你将要在你的回合承受伤害，则防止该伤害。每以此法防止一点伤害，便在极限追逐者阿兰娜上放置一个追逐指示物。\\n"
    "{R}，{T}：极限追逐者阿兰娜对任意数量的目标造成共X点伤害，由你决定分配方式，X为其上的追逐指示物数量。\\n"
    "在每个结束步骤开始时，移去极限追逐者阿兰娜上的所有追逐指示物。"
)


class ArannaThrillSeekerContractTest(unittest.TestCase):
    def test_characteristics_prevention_damage_and_cleanup(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:极限追逐者阿兰娜", lines)
        self.assertIn("ManaCost:3 W W", lines)
        self.assertIn("Types:Legendary Creature Druid Scout", lines)
        self.assertIn("PT:5/6", lines)

        replacement = next(line for line in lines if line.startswith("R:Event$ DamageDone"))
        self.assertIn("ActiveZones$ Battlefield", replacement)
        self.assertIn("ValidTarget$ You", replacement)
        self.assertIn("PlayerTurn$ True", replacement)
        self.assertIn("ReplaceWith$ PreventDamage", replacement)
        self.assertIn("PreventionEffect$ True", replacement)
        self.assertIn("AlwaysReplace$ True", replacement)
        self.assertIn("SVar:PreventDamage:DB$ ReplaceDamage | Amount$ DamageToPrevent | SubAbility$ AddPursuit", lines)
        self.assertIn("SVar:DamageToPrevent:ReplaceCount$DamageAmount", lines)
        self.assertIn("SVar:AddPursuit:DB$ PutCounter | Defined$ Self | CounterType$ PURSUIT | CounterNum$ PreventedDamage", lines)

        damage = next(line for line in lines if line.startswith("A:AB$ DealDamage"))
        self.assertIn("Cost$ R T", damage)
        self.assertIn("ValidTgts$ Any", damage)
        self.assertIn("NumDmg$ X", damage)
        self.assertIn("TargetMin$ 0", damage)
        self.assertIn("TargetMax$ MaxTgts", damage)
        self.assertIn("DividedAsYouChoose$ X", damage)
        self.assertIn("SVar:X:Count$CardCounters.PURSUIT", lines)

        trigger = next(line for line in lines if line.startswith("T:Mode$ Phase"))
        self.assertIn("Phase$ End of Turn", trigger)
        self.assertIn("TriggerZones$ Battlefield", trigger)
        self.assertNotIn("ValidPlayer$", trigger)
        self.assertIn("Execute$ TrigClearPursuit", trigger)
        self.assertIn("SVar:TrigClearPursuit:DB$ RemoveCounter | Defined$ Self | CounterType$ PURSUIT | CounterNum$ All", lines)
        self.assertIn(f"Oracle:{ZH_ORACLE}", lines)

    def test_registration_localization_documentation_and_art(self):
        self.assertIn("125 M 极限追逐者阿兰娜 @Zoltan Boros", EDITION.read_text(encoding="utf-8").splitlines())
        self.assertIn(
            f"极限追逐者阿兰娜|极限追逐者阿兰娜|传奇生物～德鲁伊／斥候|{ZH_ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 极限追逐者阿兰娜 | `{3}{W}{W}`，5/6 传奇生物～德鲁伊／斥候 | "
            "`cards/white/极限追逐者阿兰娜.txt` | 125 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        self.assertTrue(ART_BACKUP.is_file())
        self.assertEqual(
            "49e60fd514c84b7fdbbf3a5690d8bd4c40a1e3f25ece9d190a626bc9cce4b882",
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest(),
        )
        with Image.open(ART_BACKUP) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((3008, 4000), image.size)

        self.assertTrue(ART.is_file())
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((3008, 2196), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
