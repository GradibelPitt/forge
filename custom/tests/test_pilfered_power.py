import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "multicolor" / "妙手空空.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Pilfered_Power_CFM_616.png"
ART = ROOT / "cards" / "pictures" / "PH01" / "妙手空空.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class PilferedPowerContractTest(unittest.TestCase):
    def test_modal_spell_uses_one_shared_land_search_and_a_delayed_treasure_trigger(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:妙手空空", lines)
        self.assertIn("ManaCost:1 B G", lines)
        self.assertNotIn("ManaCost:1 B R", lines)
        self.assertIn("Types:Sorcery", lines)

        charm = next(line for line in lines if line.startswith("A:SP$ Charm"))
        self.assertIn("Choices$ SearchLands,DelayTreasures", charm)

        search = next(line for line in lines if line.startswith("SVar:SearchLands:"))
        self.assertIn("DB$ ChangeZone", search)
        self.assertIn("Origin$ Library", search)
        self.assertIn("Destination$ Battlefield", search)
        self.assertIn("ChangeType$ Land.hasABasicLandType", search)
        self.assertNotIn("ChangeType$ Swamp,Mountain", search)
        self.assertIn("ChangeNum$ X", search)
        self.assertIn("Tapped$ True", search)
        self.assertNotIn("Destination$ Hand", search)
        self.assertNotIn("Reveal$ True", search)
        self.assertEqual(
            1,
            sum("Origin$ Library" in line and "ChangeNum$ X" in line for line in lines),
            "lands with basic land types must share one aggregate X-card search limit",
        )

        delayed = next(line for line in lines if line.startswith("SVar:DelayTreasures:"))
        self.assertIn("DB$ DelayedTrigger", delayed)
        self.assertIn("Mode$ Phase", delayed)
        self.assertIn("Phase$ Upkeep", delayed)
        self.assertIn("ValidPlayer$ You", delayed)
        self.assertIn("Execute$ CreateTreasures", delayed)

        treasures = next(line for line in lines if line.startswith("SVar:CreateTreasures:"))
        self.assertIn("DB$ Token", treasures)
        self.assertIn("TokenAmount$ X", treasures)
        self.assertIn("TokenScript$ c_a_treasure_sac", treasures)
        self.assertIn("TokenOwner$ You", treasures)
        self.assertIn("SVar:X:Count$Valid Creature.YouCtrl", lines)

    def test_registration_localization_art_and_documentation(self):
        self.assertIn(
            "121 R 妙手空空 @Zoltan Boros",
            EDITION.read_text(encoding="utf-8"),
        )
        self.assertIn(
            "妙手空空|妙手空空|法术|选择一项 —\\n"
            "• 从你的牌库中搜寻至多X张各具有基本地类别的地牌，将它们横置放进战场，然后洗牌。X为由你操控的生物数量。\\n"
            "• 在你的下一个维持开始时，派出X个珍宝衍生物。X为由你操控的生物数量。",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 妙手空空 | `{1}{B}{G}` 法术 | `cards/multicolor/妙手空空.txt` | 121 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
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
