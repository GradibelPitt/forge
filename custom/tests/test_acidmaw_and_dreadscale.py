import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
ACIDMAW = ROOT / "cards" / "black" / "酸喉.txt"
DREADSCALE = ROOT / "cards" / "red" / "恐鳞.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ACIDMAW_ART_BACKUP = ROOT / "tools" / "card-artwork" / "AT_063.png"
DREADSCALE_ART_BACKUP = ROOT / "tools" / "card-artwork" / "AT_063t.png"
ACIDMAW_ART = ROOT / "cards" / "pictures" / "PH01" / "酸喉.artcrop.jpg"
DREADSCALE_ART = ROOT / "cards" / "pictures" / "PH01" / "恐鳞.artcrop.jpg"

ACIDMAW_ZH_ORACLE = (
    "和恐鳞拍档。\\n"
    "每当一个除酸喉和恐鳞外的生物受到伤害时，将其消灭。"
)
DREADSCALE_ZH_ORACLE = (
    "和酸喉拍档。\\n"
    "在你的回合结束时，恐鳞对每位对手以及其操控的每个生物各造成1点伤害。"
)


class AcidmawAndDreadscaleContractTest(unittest.TestCase):
    def test_acidmaw_characteristics_partner_and_damage_trigger(self):
        lines = ACIDMAW.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:酸喉", lines)
        self.assertIn("ManaCost:1 B B", lines)
        self.assertIn("Types:Legendary Creature Beast Wurm", lines)
        self.assertIn("PT:4/2", lines)
        self.assertEqual(1, lines.count("K:Partner with:恐鳞"))

        trigger = next(line for line in lines if line.startswith("T:Mode$ DamageDoneOnce"))
        self.assertIn("ValidTarget$ Creature.!named酸喉+!named恐鳞", trigger)
        self.assertIn("TriggerZones$ Battlefield", trigger)
        self.assertIn("Execute$ TrigDestroy", trigger)

        destroy = next(line for line in lines if line.startswith("SVar:TrigDestroy:"))
        self.assertIn("DB$ Destroy", destroy)
        self.assertIn("Defined$ TriggeredTargetLKICopy", destroy)
        self.assertNotIn("NoRegen$ True", destroy)

    def test_dreadscale_characteristics_partner_and_end_step_damage(self):
        lines = DREADSCALE.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:恐鳞", lines)
        self.assertIn("ManaCost:1 R R", lines)
        self.assertIn("Types:Legendary Creature Beast Wurm", lines)
        self.assertIn("PT:4/2", lines)
        self.assertEqual(1, lines.count("K:Partner with:酸喉"))

        trigger = next(line for line in lines if line.startswith("T:Mode$ Phase"))
        self.assertIn("Phase$ End of Turn", trigger)
        self.assertIn("ValidPlayer$ You", trigger)
        self.assertIn("TriggerZones$ Battlefield", trigger)
        self.assertIn("Execute$ TrigDamageAll", trigger)

        damage = next(line for line in lines if line.startswith("SVar:TrigDamageAll:"))
        self.assertIn("DB$ DamageAll", damage)
        self.assertIn("ValidPlayers$ Player.Opponent", damage)
        self.assertIn("ValidCards$ Creature.OppCtrl", damage)
        self.assertIn("NumDmg$ 1", damage)

    def test_registration_localization_documentation_and_art(self):
        edition_lines = EDITION.read_text(encoding="utf-8").splitlines()
        self.assertIn("123 M 酸喉 @Andrew Hou", edition_lines)
        self.assertIn("124 M 恐鳞 @Zoltan Boros", edition_lines)

        localized_lines = ZH_CN.read_text(encoding="utf-8").splitlines()
        self.assertIn(
            f"酸喉|酸喉|传奇生物～野兽／亚龙|{ACIDMAW_ZH_ORACLE}",
            localized_lines,
        )
        self.assertIn(
            f"恐鳞|恐鳞|传奇生物～野兽／亚龙|{DREADSCALE_ZH_ORACLE}",
            localized_lines,
        )

        catalog = (ROOT / "CARDS.md").read_text(encoding="utf-8")
        self.assertIn(
            "| 酸喉 | `{1}{B}{B}`，4/2 传奇生物～野兽／亚龙 | "
            "`cards/black/酸喉.txt` | 123 |",
            catalog,
        )
        self.assertIn(
            "| 恐鳞 | `{1}{R}{R}`，4/2 传奇生物～野兽／亚龙 | "
            "`cards/red/恐鳞.txt` | 124 |",
            catalog,
        )

        for source, crop in (
            (ACIDMAW_ART_BACKUP, ACIDMAW_ART),
            (DREADSCALE_ART_BACKUP, DREADSCALE_ART),
        ):
            with self.subTest(source=source.name):
                self.assertTrue(source.is_file())
                with Image.open(source) as image:
                    self.assertEqual("PNG", image.format)
                    self.assertEqual("RGB", image.mode)
                    self.assertEqual((512, 512), image.size)

                self.assertTrue(crop.is_file())
                with Image.open(crop) as image:
                    self.assertEqual("JPEG", image.format)
                    self.assertEqual("RGB", image.mode)
                    self.assertEqual((512, 374), image.size)
                    self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
