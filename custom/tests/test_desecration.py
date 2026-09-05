import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "black" / "亵渎.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-5451d607-d808-4214-96e5-0a5222334646.png"
)
ART = ROOT / "cards" / "pictures" / "PH01" / "亵渎.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = (
    "你失去1点生命。所有生物得-0/-1直到回合结束。若有生物因亵渎的效果死去，则直到你的回合结束，"
    "你可以从你的坟墓场施放亵渎，且不需支付其法术力费用。"
)


class DesecrationContractTest(unittest.TestCase):
    def test_life_loss_and_toughness_reduction_with_causal_death_watcher(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:亵渎", lines)
        self.assertIn("ManaCost:B B", lines)
        self.assertIn("Types:Sorcery", lines)
        spell = next(line for line in lines if line.startswith("A:SP$ LoseLife"))
        self.assertIn("Defined$ You", spell)
        self.assertIn("LifeAmount$ 1", spell)
        self.assertIn("SubAbility$ DBWeaken", spell)
        weaken = next(line for line in lines if line.startswith("SVar:DBWeaken:"))
        for field in ("DB$ PumpAll", "ValidCards$ Creature", "NumAtt$ 0", "NumDef$ -1",
                      "Duration$ UntilEndOfTurn", "RememberPumped$ True", "SubAbility$ DBWatchDeaths"):
            self.assertIn(field, weaken)

        watcher = next(line for line in lines if line.startswith("SVar:DBWatchDeaths:"))
        self.assertIn("DB$ Effect", watcher)
        self.assertIn("Triggers$ TrigDeath", watcher)
        self.assertIn("RememberObjects$ Self", watcher)
        self.assertIn("ImprintCards$ Remembered", watcher)
        self.assertIn("Duration$ UntilStateBasedActionChecked", watcher)
        self.assertIn("SubAbility$ DBCleanup", watcher)
        self.assertIn("SVar:DBCleanup:DB$ Cleanup | ClearRemembered$ True", lines)
        death = next(line for line in lines if line.startswith("SVar:TrigDeath:"))
        self.assertIn("Mode$ ChangesZone", death)
        self.assertIn("Origin$ Battlefield", death)
        self.assertIn("Destination$ Graveyard", death)
        self.assertIn("ValidCard$ Creature.IsImprinted", death)
        self.assertIn("ActivationLimit$ 1", death)
        self.assertIn("Execute$ DBGrantRecast", death)

    def test_free_graveyard_recast_permission_expires_this_turn(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        grant = next(line for line in lines if line.startswith("SVar:DBGrantRecast:"))
        self.assertIn("DB$ Effect", grant)
        self.assertIn("RememberObjects$ Remembered", grant)
        self.assertIn("StaticAbilities$ FreeCastFromGrave", grant)
        self.assertIn("ForgetOnMoved$ Graveyard", grant)
        self.assertIn("Duration$ UntilEndOfTurn", grant)
        permission = next(
            line for line in lines if line.startswith("SVar:FreeCastFromGrave:")
        )
        self.assertIn("Affected$ Sorcery.IsRemembered", permission)
        self.assertIn("MayPlay$ True", permission)
        self.assertIn("MayPlayWithoutManaCost$ True", permission)
        self.assertIn("AffectedZone$ Graveyard", permission)
        self.assertIn(f"Oracle:{ORACLE}", lines)

    def test_registration_localization_art_and_documentation(self):
        self.assertIn("94 R 亵渎 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertIn(
            f"亵渎|亵渎|法术|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            f"| 亵渎 | `{{B}}{{B}}` 法术 | `cards/black/亵渎.txt` | 94 | {ORACLE} |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        self.assertTrue(ART_BACKUP.is_file())
        self.assertEqual(
            "4779296F8830188B81978146BF43BCA4EE7D464AD6B540A88DA75A65F5D2ED73",
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        self.assertTrue(ART.is_file())
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((775, 566), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
