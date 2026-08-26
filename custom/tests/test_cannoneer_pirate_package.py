import unittest
from pathlib import Path

from PIL import Image


CUSTOM_ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = CUSTOM_ROOT.parent
EDITION = CUSTOM_ROOT / "editions" / "Placeholder_Set.txt"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

CARDS = {
    "宝藏经销商": CUSTOM_ROOT / "cards" / "red" / "宝藏经销商.txt",
    "钩手拖曳": CUSTOM_ROOT / "cards" / "multicolor" / "钩手拖曳.txt",
    "火炮长": CUSTOM_ROOT / "cards" / "red" / "火炮长.txt",
    "船载火炮": CUSTOM_ROOT / "cards" / "red" / "船载火炮.txt",
    "炸药工程师": CUSTOM_ROOT / "cards" / "red" / "炸药工程师.txt",
}
TOKEN = CUSTOM_ROOT / "tokens" / "r_1_1_pirate_cannoneer.txt"

ART_SOURCES = {
    "Treasure_Distributor_full.jpg",
    "Cannoneer_full.jpg",
    "Hook_n_Heave_full.jpg",
    "Cannonmaster_full.jpg",
    "Ships_Cannon_full.jpg",
    "Blastpowder_Engineer_full.jpg",
}


def read_lines(path: Path) -> list[str]:
    return path.read_text(encoding="utf-8").splitlines()


class CannoneerPiratePackageTest(unittest.TestCase):
    def test_treasure_distributor_perpetually_buffs_entering_pirate_attack(self):
        lines = read_lines(CARDS["宝藏经销商"])
        self.assertIn("Name:宝藏经销商", lines)
        self.assertIn("ManaCost:R", lines)
        self.assertIn("Types:Creature Pirate", lines)
        self.assertIn("PT:1/1", lines)
        trigger = next(line for line in lines if line.startswith("T:Mode$ ChangesZone"))
        self.assertIn("Destination$ Battlefield", trigger)
        self.assertIn("Creature.Pirate+Other+YouCtrl", trigger)
        pump = next(line for line in lines if line.startswith("SVar:TrigPump:DB$ Pump"))
        self.assertIn("Defined$ TriggeredCardLKICopy", pump)
        self.assertIn("NumAtt$ +1", pump)
        self.assertIn("Duration$ Perpetual", pump)

    def test_hook_n_heave_draws_one_then_creates_two_cannoneers(self):
        lines = read_lines(CARDS["钩手拖曳"])
        self.assertIn("ManaCost:U R", lines)
        self.assertIn("Types:Sorcery", lines)
        draw = next(line for line in lines if line.startswith("A:SP$ Draw"))
        self.assertIn("NumCards$ 1", draw)
        self.assertIn("SubAbility$ DBToken", draw)
        token = next(line for line in lines if line.startswith("SVar:DBToken:DB$ Token"))
        self.assertIn("TokenScript$ r_1_1_pirate_cannoneer", token)
        self.assertIn("TokenAmount$ 2", token)

    def test_cannonmaster_etb_may_pay_red_to_create_one_cannoneer(self):
        lines = read_lines(CARDS["火炮长"])
        self.assertIn("ManaCost:R", lines)
        self.assertIn("Types:Creature Pirate", lines)
        self.assertIn("PT:3/1", lines)
        trigger = next(line for line in lines if line.startswith("T:Mode$ ChangesZone"))
        self.assertIn("ValidCard$ Card.Self", trigger)
        self.assertIn("Execute$ TrigToken", trigger)
        token = next(line for line in lines if line.startswith("SVar:TrigToken:AB$ Token"))
        self.assertIn("Cost$ R", token)
        self.assertIn("TokenScript$ r_1_1_pirate_cannoneer", token)
        self.assertIn("TokenAmount$ 1", token)

    def test_ships_cannon_is_artifact_construct_and_not_a_pirate(self):
        lines = read_lines(CARDS["船载火炮"])
        self.assertIn("ManaCost:R R", lines)
        self.assertIn("Types:Artifact Creature Construct", lines)
        self.assertNotIn("Types:Artifact Creature Pirate", lines)
        self.assertIn("PT:2/3", lines)
        self.assertIn("K:Defender", lines)
        self.assertTrue(any(line.startswith("S:Mode$ MustBlock") for line in lines))
        trigger = next(line for line in lines if line.startswith("T:Mode$ ChangesZone"))
        self.assertIn("Creature.Pirate+YouCtrl", trigger)
        damage = next(line for line in lines if line.startswith("SVar:TrigDamage:DB$ DealDamage"))
        self.assertIn("ValidTgts$ Any", damage)
        self.assertIn("NumDmg$ 2", damage)

    def test_blastpowder_engineer_adds_one_to_pirate_damage_on_your_turn(self):
        lines = read_lines(CARDS["炸药工程师"])
        self.assertIn("ManaCost:R R", lines)
        self.assertIn("Types:Creature Pirate", lines)
        self.assertIn("PT:2/3", lines)
        replacement = next(line for line in lines if line.startswith("R:Event$ DamageDone"))
        self.assertIn("ValidSource$ Creature.Pirate+YouCtrl", replacement)
        self.assertIn("PlayerTurn$ True", replacement)
        self.assertIn("ReplaceWith$ DmgPlusOne", replacement)
        self.assertIn(
            "SVar:DmgPlusOne:DB$ ReplaceEffect | VarName$ DamageAmount | VarValue$ X",
            lines,
        )
        self.assertIn("SVar:X:ReplaceCount$DamageAmount/Plus.1", lines)

    def test_cannoneer_token_contract(self):
        lines = read_lines(TOKEN)
        self.assertIn("Name:火炮手", lines)
        self.assertIn("ManaCost:no cost", lines)
        self.assertIn("Colors:red", lines)
        self.assertIn("Types:Creature Pirate", lines)
        self.assertIn("PT:1/1", lines)
        self.assertTrue(any(line.startswith("S:Mode$ MustBlock") for line in lines))
        trigger = next(line for line in lines if line.startswith("T:Mode$ Phase"))
        self.assertIn("Phase$ End of Turn", trigger)
        self.assertIn("ValidPlayer$ You", trigger)
        self.assertIn("Creature.Pirate+Other+YouCtrl", trigger)
        damage = next(line for line in lines if line.startswith("SVar:TrigDamage:DB$ DealDamage"))
        self.assertIn("ValidTgts$ Any", damage)
        self.assertIn("NumDmg$ 1", damage)

    def test_registration_localization_documentation_and_hswiki_art(self):
        edition_lines = read_lines(EDITION)
        for row in (
            "129 C 宝藏经销商 @Jason Kang",
            "130 C 钩手拖曳 @Timur Shevtsov",
            "131 C 火炮长 @Luca Zontini",
            "132 C 船载火炮 @Warren Mahy",
            "133 R 炸药工程师 @Kati Sarin",
        ):
            self.assertIn(row, edition_lines)

        localization = ZH_CN.read_text(encoding="utf-8")
        for name in CARDS:
            self.assertIn(f"\n{name}|{name}|", f"\n{localization}")

        catalog = (CUSTOM_ROOT / "CARDS.md").read_text(encoding="utf-8")
        for name in CARDS:
            self.assertIn(f"| {name} |", catalog)
        self.assertIn("`tokens/r_1_1_pirate_cannoneer.txt`", catalog)

        backup_dir = CUSTOM_ROOT / "tools" / "card-artwork"
        for source_name in ART_SOURCES:
            source = backup_dir / source_name
            self.assertTrue(source.is_file(), source)
            with Image.open(source) as image:
                self.assertIn(image.format, {"JPEG", "PNG", "WEBP"})
                self.assertGreater(image.width, 500)
                self.assertGreater(image.height, 500)

        crops = [
            CUSTOM_ROOT / "cards" / "pictures" / "PH01" / f"{name}.artcrop.jpg"
            for name in CARDS
        ]
        crops.append(CUSTOM_ROOT / "tokens" / "pictures" / "r_1_1_pirate_cannoneer.jpg")
        for crop in crops:
            self.assertTrue(crop.is_file(), crop)
            with Image.open(crop) as image:
                self.assertEqual("JPEG", image.format)
                self.assertEqual("RGB", image.mode)
                self.assertEqual((960, 700), image.size)
                self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
