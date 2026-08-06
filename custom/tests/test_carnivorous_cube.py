import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "black" / "食肉格块.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-4ca1e2bc-4d81-45de-936f-6ab81e1ae6fa.png"
)
OUTPAINT = (
    ROOT
    / "tools"
    / "card-artwork"
    / "食肉格块-imagegen-outpaint-20260806.png"
)
ART = ROOT / "cards" / "pictures" / "PH01" / "食肉格块.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = (
    "当食肉格块进战场时，消灭一个由你控制的生物。"
    "食肉格块具有“在你的结束阶段，化生一个以此法消灭的生物并在你的操控下放入战场”。"
)


class CarnivorousCubeContractTest(unittest.TestCase):
    def test_characteristics_and_etb_controlled_creature_destruction(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:食肉格块", lines)
        self.assertIn("ManaCost:3 B B", lines)
        self.assertIn("Types:Creature Ooze", lines)
        self.assertIn("PT:4/6", lines)
        trigger = next(line for line in lines if line.startswith("T:Mode$ ChangesZone"))
        self.assertIn("Origin$ Any", trigger)
        self.assertIn("Destination$ Battlefield", trigger)
        self.assertIn("ValidCard$ Card.Self", trigger)
        self.assertIn("Execute$ TrigChooseCreature", trigger)
        choose = next(line for line in lines if line.startswith("SVar:TrigChooseCreature:"))
        self.assertIn("DB$ ChooseCard", choose)
        self.assertIn("Defined$ You", choose)
        self.assertIn("Choices$ Creature.YouCtrl", choose)
        self.assertIn("Mandatory$ True", choose)
        self.assertIn("ImprintChosen$ True", choose)
        self.assertIn("SubAbility$ DBDestroy", choose)
        destroy = next(line for line in lines if line.startswith("SVar:DBDestroy:"))
        self.assertIn("DB$ Destroy", destroy)
        self.assertIn("Defined$ Imprinted", destroy)
        self.assertIn("RememberDestroyed$ True", destroy)
        self.assertIn("SubAbility$ DBCleanupImprinted", destroy)
        cleanup = next(line for line in lines if line.startswith("SVar:DBCleanupImprinted:"))
        self.assertIn("DB$ Cleanup", cleanup)
        self.assertIn("ClearImprinted$ True", cleanup)

    def test_each_end_step_conjures_the_remembered_creature(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        trigger = next(line for line in lines if line.startswith("T:Mode$ Phase"))
        self.assertIn("Phase$ End of Turn", trigger)
        self.assertIn("ValidPlayer$ You", trigger)
        self.assertIn("TriggerZones$ Battlefield", trigger)
        self.assertIn("Execute$ TrigConjure", trigger)
        conjure = next(line for line in lines if line.startswith("SVar:TrigConjure:"))
        self.assertIn("DB$ MakeCard", conjure)
        self.assertIn("Conjure$ True", conjure)
        self.assertIn("DefinedName$ Remembered", conjure)
        self.assertIn("Zone$ Battlefield", conjure)
        self.assertIn(f"Oracle:{ORACLE}", lines)

    def test_registration_localization_art_and_documentation(self):
        self.assertIn("92 R 食肉格块 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertIn(
            f"食肉格块|食肉格块|生物～流浆|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 食肉格块 | `{3}{B}{B}`，4/6 生物～流浆 | "
            "`cards/black/食肉格块.txt` | 92 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        self.assertTrue(ART_BACKUP.is_file())
        self.assertEqual(
            "B66BF516C667B98B8952B4ED3AD96B31DB6951AF346AB9251FE02D8D37F1AE07",
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        self.assertTrue(OUTPAINT.is_file())
        self.assertEqual(
            "027CBBBB7E84A5B60F55EED81641B7EF1335C3E8F0F6A60D6895BF8935A2DFA1",
            hashlib.sha256(OUTPAINT.read_bytes()).hexdigest().upper(),
        )
        with Image.open(OUTPAINT) as image:
            self.assertEqual("PNG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((1537, 1023), image.size)

        self.assertTrue(ART.is_file())
        self.assertEqual(
            "C0CE8C807A52391F9905063E0D899D02407FF322A624D126EF788DCF54AB18B9",
            hashlib.sha256(ART.read_bytes()).hexdigest().upper(),
        )
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((1024, 748), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
