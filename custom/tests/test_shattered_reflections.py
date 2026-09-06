import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "multicolor" / "破碎映像.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "DEEP_025.png"
ART = ROOT / "cards" / "pictures" / "PH01" / "破碎映像.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = (
    "选择至多一个目标非衍生生物。分别在你的战场、手上和牌库中各化生一张该生物的复制品，"
    "然后将你的牌库洗牌。\\n以此法化生的牌不是传奇。"
)


class ShatteredReflectionsContractTest(unittest.TestCase):
    def test_card_profile_matches_the_requested_design(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:破碎映像", lines)
        self.assertIn("ManaCost:2 U W G", lines)
        self.assertIn("Types:Sorcery", lines)
        self.assertIn(f"Oracle:{ORACLE}", lines)

    def test_conjures_one_copy_to_each_zone_and_then_shuffles(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        spell = next(line for line in lines if line.startswith("A:SP$ MakeCard"))
        for field in (
            "Defined$ You",
            "Conjure$ True",
            "ValidTgts$ Creature.!token",
            "TargetMin$ 0",
            "TargetMax$ 1",
            "DefinedName$ Targeted",
            "Zone$ None",
            "RememberMade$ True",
            "SubAbility$ RemoveLegendaryBattlefield",
        ):
            self.assertIn(field, spell)

        make_hand = next(line for line in lines if line.startswith("SVar:MakeHand:"))
        for field in (
            "DB$ MakeCard",
            "Defined$ You",
            "Conjure$ True",
            "DefinedName$ Targeted",
            "Zone$ None",
            "RememberMade$ True",
            "SubAbility$ RemoveLegendaryHand",
        ):
            self.assertIn(field, make_hand)

        make_library = next(
            line for line in lines if line.startswith("SVar:MakeLibrary:")
        )
        for field in (
            "DB$ MakeCard",
            "Defined$ You",
            "Conjure$ True",
            "DefinedName$ Targeted",
            "Zone$ None",
            "RememberMade$ True",
            "SubAbility$ RemoveLegendaryLibrary",
        ):
            self.assertIn(field, make_library)

        moves = {
            prefix: next(line for line in lines if line.startswith(prefix))
            for prefix in (
                "SVar:MoveBattlefield:",
                "SVar:MoveHand:",
                "SVar:MoveLibrary:",
            )
        }
        for move in moves.values():
            self.assertIn("DB$ ChangeZone", move)
            self.assertIn("Defined$ Remembered", move)
            self.assertIn("Origin$ None", move)
        self.assertIn("Destination$ Battlefield", moves["SVar:MoveBattlefield:"])
        self.assertIn("Destination$ Hand", moves["SVar:MoveHand:"])
        self.assertIn("Destination$ Library", moves["SVar:MoveLibrary:"])
        self.assertIn("LibraryPosition$ 0", moves["SVar:MoveLibrary:"])

        shuffle = next(
            line for line in lines if line.startswith("SVar:ShuffleLibrary:")
        )
        self.assertIn("DB$ Shuffle", shuffle)
        self.assertIn("Defined$ You", shuffle)

    def test_every_conjured_copy_perpetually_loses_only_legendary(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        for suffix, next_step in (
            ("Battlefield", "MoveBattlefield"),
            ("Hand", "MoveHand"),
            ("Library", "MoveLibrary"),
        ):
            remove_legendary = next(
                line
                for line in lines
                if line.startswith(f"SVar:RemoveLegendary{suffix}:")
            )
            self.assertIn("DB$ Animate", remove_legendary)
            self.assertIn("Defined$ Remembered", remove_legendary)
            self.assertIn("RemoveTypes$ Legendary", remove_legendary)
            self.assertIn("Duration$ Perpetual", remove_legendary)
            self.assertNotIn("RemoveSuperTypes$", remove_legendary)
            self.assertIn(f"SubAbility$ {next_step}", remove_legendary)

        cleanup = next(line for line in lines if line.startswith("SVar:Cleanup:"))
        self.assertIn("DB$ Cleanup", cleanup)
        self.assertIn("ClearRemembered$ True", cleanup)

    def test_registration_localization_art_and_documentation(self):
        self.assertIn(
            "185 R 破碎映像 @Vladimir Kafanov",
            EDITION.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            f"破碎映像|破碎映像|法术|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 破碎映像 | `{2}{U}{W}{G}` 法术 | "
            "`cards/multicolor/破碎映像.txt` | 185 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        self.assertTrue(ART_BACKUP.is_file())
        with Image.open(ART_BACKUP) as image:
            self.assertEqual("PNG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((512, 512), image.size)

        self.assertTrue(ART.is_file())
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
