import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "colorless" / "山岭巨人.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-0c7715cf-d19b-4b33-bebc-77dc71dd6aee.png"
)
ART = ROOT / "cards" / "pictures" / "PH01" / "山岭巨人.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = "你每有一张其他手牌，此咒语便减少{1}来施放。"


class MountainGiantContractTest(unittest.TestCase):
    def test_card_fields_and_dynamic_hand_cost_reduction(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:山岭巨人", lines)
        self.assertIn("ManaCost:12", lines)
        self.assertIn("Types:Creature Giant", lines)
        self.assertIn("PT:8/8", lines)

        reduce_cost = next(line for line in lines if line.startswith("S:Mode$ ReduceCost"))
        self.assertIn("ValidCard$ Card.Self", reduce_cost)
        self.assertIn("Type$ Spell", reduce_cost)
        self.assertIn("Amount$ X", reduce_cost)
        self.assertIn("EffectZone$ All", reduce_cost)
        self.assertIn(
            "Description$ This spell costs {1} less to cast for each other card in your hand.",
            reduce_cost,
        )
        self.assertIn(
            "SVar:X:Count$ValidHand Card.Other+YouOwn",
            lines,
        )
        self.assertIn(
            "Oracle:This spell costs {1} less to cast for each other card in your hand.",
            lines,
        )

    def test_registration_localization_art_and_documentation(self):
        self.assertIn("96 R 山岭巨人 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertIn(
            f"山岭巨人|山岭巨人|生物～巨人|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 山岭巨人 | `{12}`，8/8 生物～巨人 | "
            "`cards/colorless/山岭巨人.txt` | 96 | "
            "你每有一张其他手牌，本牌便减少1来施放。 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        self.assertTrue(ART_BACKUP.is_file())
        self.assertEqual(
            "063A7C5937EB5AB0FFE54229C8856E1157D54F30C291D0EEA4C6FEF7B1ECD4B6",
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        self.assertTrue(ART.is_file())
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((512, 374), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
