import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "multicolor" / "艾雅，玉莲帮主.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Aya_Lotus_Kingpin_full_hswiki.jpg"
ART = ROOT / "cards" / "pictures" / "PH01" / "艾雅,玉莲帮主.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class AyaLotusKingpinContractTest(unittest.TestCase):
    def test_characteristics_and_etb_order(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:艾雅，玉莲帮主", lines)
        self.assertIn("ManaCost:3 B R", lines)
        self.assertIn("Types:Creature Rogue", lines)
        self.assertNotIn("Types:Legendary Creature Rogue", lines)
        self.assertIn("PT:5/3", lines)

        trigger = next(line for line in lines if line.startswith("T:Mode$ ChangesZone"))
        self.assertIn("Origin$ Any", trigger)
        self.assertIn("Destination$ Battlefield", trigger)
        self.assertIn("ValidCard$ Card.Self", trigger)
        self.assertIn("Execute$ CreateTreasures", trigger)

        treasures = next(
            line for line in lines if line.startswith("SVar:CreateTreasures:")
        )
        self.assertIn("DB$ Token", treasures)
        self.assertIn("TokenAmount$ 3", treasures)
        self.assertIn("TokenScript$ c_a_treasure_sac", treasures)
        self.assertIn("TokenOwner$ You", treasures)
        self.assertIn("SubAbility$ ChooseEmblem", treasures)

    def test_three_game_restricted_choices_create_the_requested_emblems(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        choice = next(
            line for line in lines if line.startswith("SVar:ChooseEmblem:")
        )
        self.assertIn("DB$ Charm", choice)
        self.assertIn(
            "Choices$ CreateJadeEmblem,CreateDamageEmblem,CreateDrawEmblem",
            choice,
        )
        self.assertIn("ChoiceRestriction$ ThisGame", choice)
        self.assertIn("CharmNum$ 1", choice)

        for name, trigger_name in (
            ("CreateJadeEmblem", "JadeTreasureTrigger"),
            ("CreateDamageEmblem", "DamageTreasureTrigger"),
            ("CreateDrawEmblem", "DrawTreasureTrigger"),
        ):
            emblem = next(
                line for line in lines if line.startswith(f"SVar:{name}:")
            )
            self.assertIn("DB$ Effect", emblem)
            self.assertIn("EffectOwner$ You", emblem)
            self.assertIn(f"Triggers$ {trigger_name}", emblem)
            self.assertIn("Duration$ Permanent", emblem)

        for trigger_name, execute in (
            ("JadeTreasureTrigger", "CreateJadeGolem"),
            ("DamageTreasureTrigger", "DealRandomDamage"),
            ("DrawTreasureTrigger", "DrawCard"),
        ):
            emblem_trigger = next(
                line
                for line in lines
                if line.startswith(f"SVar:{trigger_name}:")
            )
            self.assertIn("Mode$ Sacrificed", emblem_trigger)
            self.assertIn("ValidCard$ Treasure.token+YouCtrl", emblem_trigger)
            self.assertIn("TriggerZones$ Command", emblem_trigger)
            self.assertIn(f"Execute$ {execute}", emblem_trigger)

        jade = next(
            line for line in lines if line.startswith("SVar:CreateJadeGolem:")
        )
        self.assertIn("DB$ MakeCard", jade)
        self.assertIn("Defined$ You", jade)
        self.assertIn("Conjure$ True", jade)
        self.assertIn("Name$ 青玉魔像", jade)
        self.assertIn("Zone$ Battlefield", jade)

        damage = next(
            line for line in lines if line.startswith("SVar:DealRandomDamage:")
        )
        self.assertIn("DB$ DealDamage", damage)
        self.assertIn("ValidTgts$ Player.Other,Permanent.YouDontCtrl", damage)
        self.assertIn("TargetsAtRandom$ True", damage)
        self.assertIn("NumDmg$ 2", damage)

        draw = next(line for line in lines if line.startswith("SVar:DrawCard:"))
        self.assertIn("AB$ Draw", draw)
        self.assertIn("Cost$ 1", draw)
        self.assertIn("Defined$ You", draw)
        self.assertIn("NumCards$ 1", draw)

    def test_registration_localization_documentation_and_art(self):
        self.assertIn(
            "118 R 艾雅，玉莲帮主 @James Ryman",
            EDITION.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 艾雅，玉莲帮主 | `{3}{B}{R}`，5/3 生物～熊猫人／浪客 | "
            "`cards/multicolor/艾雅，玉莲帮主.txt` | 118 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        localization = ZH_CN.read_text(encoding="utf-8").splitlines()
        self.assertTrue(
            any(
                line.startswith(
                    "艾雅，玉莲帮主|艾雅，玉莲帮主|生物～熊猫人／浪客|"
                )
                and "派出三个珍宝衍生物" in line
                and "此前未选择过的选项中选择一项" in line
                and "派出一个青玉魔像" in line
                and "随机选择" in line
                and "你可以支付{1}。若如此作，抓一张牌" in line
                for line in localization
            )
        )

        self.assertTrue(ART_BACKUP.is_file(), ART_BACKUP)
        self.assertTrue(ART.is_file(), ART)
        with Image.open(ART_BACKUP) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((3000, 4000), image.size)
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((3000, 2190), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
