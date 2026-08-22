import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "multicolor" / "超世之杰曹操.txt"
EDITION = ROOT / "editions" / "BoTu_Three_Kingdoms_New_Chapter.txt"
ART_ORIGINAL = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-8f1b2484-b5ee-492a-95f5-a87417c3994b.png"
)
ART_OUTPAINT = (
    ROOT / "tools" / "card-artwork" / "超世之杰曹操-imagegen-outpaint-20260807.png"
)
ART = ROOT / "cards" / "pictures" / "BT3K" / "超世之杰曹操.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = (
    "如果一个由对手操控的来源将对你或超世之杰曹操造成伤害，"
    "除非该来源的操控者支付{1}，否则防止该伤害。\\n"
    "每当一个由对手操控的来源对你造成伤害时，该来源的操控者选择一项——\\n"
    "• 选择一个由其操控的永久物。你获得该永久物的操控权。\\n"
    "• 从其手牌放逐一张牌。"
)


class CaoCaoContractTest(unittest.TestCase):
    def test_characteristics_and_damage_tax(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:超世之杰曹操", lines)
        self.assertIn("ManaCost:W U B", lines)
        self.assertIn("Types:Legendary Creature Human Noble", lines)
        self.assertIn("PT:3/4", lines)

        replacement = next(line for line in lines if line.startswith("R:Event$ DamageDone"))
        self.assertIn("ValidSource$ Card.OppCtrl,Emblem.OppCtrl", replacement)
        self.assertIn("ValidTarget$ You,Card.Self", replacement)
        self.assertIn("ReplaceWith$ TaxedPrevention", replacement)
        self.assertIn("PreventionEffect$ True", replacement)

        prevention = next(
            line for line in lines if line.startswith("SVar:TaxedPrevention:")
        )
        self.assertIn("DB$ ReplaceDamage", prevention)
        self.assertIn("Amount$ ShieldAmount", prevention)
        self.assertIn("UnlessCost$ 1", prevention)
        self.assertIn("UnlessPayer$ ReplacedSourceController", prevention)

    def test_damage_trigger_keeps_approved_choice_order(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()
        trigger = next(line for line in lines if line.startswith("T:Mode$ DamageDone"))
        self.assertIn("ValidSource$ Card.OppCtrl,Emblem.OppCtrl", trigger)
        self.assertIn("ValidTarget$ You", trigger)
        self.assertIn("Execute$ TrigChoice", trigger)

        choice = next(line for line in lines if line.startswith("SVar:TrigChoice:"))
        self.assertIn("Defined$ TriggeredSourceController", choice)
        self.assertIn("Choices$ GivePermanent,ExileHand", choice)
        self.assertIn("TempRemember$ Chooser", choice)

        give = next(line for line in lines if line.startswith("SVar:GivePermanent:"))
        self.assertIn("Defined$ Player.IsRemembered", give)
        self.assertIn("Choices$ Permanent.RememberedPlayerCtrl", give)
        self.assertIn("Mandatory$ True", give)
        self.assertIn("SubAbility$ GainChosenPermanent", give)

        gain = next(
            line for line in lines if line.startswith("SVar:GainChosenPermanent:")
        )
        self.assertIn("DB$ GainControl", gain)
        self.assertIn("Defined$ ChosenCard", gain)
        self.assertIn("NewController$ You", gain)

        exile = next(line for line in lines if line.startswith("SVar:ExileHand:"))
        self.assertIn("ChoiceZone$ Hand", exile)
        self.assertIn("Choices$ Card.RememberedPlayerCtrl", exile)
        self.assertIn("SubAbility$ ExileChosenCard", exile)

    def test_registration_localization_documentation_and_art(self):
        edition = EDITION.read_text(encoding="utf-8")
        self.assertIn("Code=BT3K", edition)
        self.assertIn("Name=博图三国新篇", edition)
        self.assertIn("2 M 超世之杰曹操 @Custom", edition)

        self.assertIn(
            f"超世之杰曹操|超世之杰曹操|传奇生物～人类／贵族|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 超世之杰曹操 | `{W}{U}{B}` 3/4 传奇生物～人类／贵族 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        expected_hashes = {
            ART_ORIGINAL: "5A75BF71B4E39A8FF895AFC98061BF3C44884CFD1ECD58CA9766DB8A5A1F687A",
            ART_OUTPAINT: "14ED0512702EBFAB617C0BA4CB581C13D80B706AB34A90DE725C8D0A396FB1B3",
            ART: "D1C4FECEBAA6FEADE719D22633A755809CCE7EB0E02E13408000D2722D285C68",
        }
        for path, expected_hash in expected_hashes.items():
            self.assertTrue(path.is_file())
            self.assertEqual(
                expected_hash,
                hashlib.sha256(path.read_bytes()).hexdigest().upper(),
            )

        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((574, 419), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
