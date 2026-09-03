import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "multicolor" / "艾雅，玉莲帮主.txt"
EMBLEMS = {
    "GainJadeEmblem": (
        ROOT / "cards" / "colorless" / "emblem_ayas_jade_treasure.txt",
        "Emblem — Aya's Jade Treasure",
        "CreateJadeGolem",
    ),
    "GainDamageEmblem": (
        ROOT / "cards" / "colorless" / "emblem_ayas_burst_treasure.txt",
        "Emblem — Aya's Burst Treasure",
        "DealRandomDamage",
    ),
    "GainCunningEmblem": (
        ROOT / "cards" / "colorless" / "emblem_ayas_cunning_treasure.txt",
        "Emblem — Aya's Cunning Treasure",
        "ConjureForgedPotion",
    ),
}
MAIN_EDITION = ROOT / "editions" / "Placeholder_Set.txt"
EMBLEM_EDITION = ROOT / "editions" / "Token_HS.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Aya_Lotus_Kingpin_full_hswiki.jpg"
ART = ROOT / "cards" / "pictures" / "PH01" / "艾雅,玉莲帮主.artcrop.jpg"
EMBLEM_ART = {
    "Emblem — Aya's Jade Treasure": (
        ROOT / "tools" / "card-artwork" / "Aya_Jade_Treasure_source.jpg",
        ROOT / "cards" / "pictures" / "TOKEN_HS" / "Emblem — Aya's Jade Treasure.artcrop.jpg",
        "59D4AD2423C7119074163C08F0D0CC08F1FA1871851772374CAA36878929C049",
        "EF54B75BC1EBC1E242A209C3019E3EA2E881CD0E062F2F8170F871A549D3AD14",
    ),
    "Emblem — Aya's Burst Treasure": (
        ROOT / "tools" / "card-artwork" / "Aya_Burst_Treasure_source.jpg",
        ROOT / "cards" / "pictures" / "TOKEN_HS" / "Emblem — Aya's Burst Treasure.artcrop.jpg",
        "8BF4AA5F0B97B541DC350D86229D3DB0742F3F62BD9BC9105F37DC7727BD2693",
        "9CA0CC9DCC7ABD349B841C0F34EB86D7313D7AEB20C9CB42C13556F394580314",
    ),
    "Emblem — Aya's Cunning Treasure": (
        ROOT / "tools" / "card-artwork" / "Aya_Cunning_Treasure_source.jpg",
        ROOT / "cards" / "pictures" / "TOKEN_HS" / "Emblem — Aya's Cunning Treasure.artcrop.jpg",
        "C5726D5D2A8FDBAF0062C93FF8493B8439A5217029124E594E22FCAAE321EAB4",
        "2A44F38B236A809AB28C9F3203B77ADAD9AA9934D8EDF96BD7CB352F71A89B92",
    ),
}
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class AyaLotusKingpinContractTest(unittest.TestCase):
    def test_characteristics_and_etb_order(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:艾雅，玉莲帮主", lines)
        self.assertIn("ManaCost:2 B G U", lines)
        self.assertNotIn("ManaCost:3 B R", lines)
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

    def test_choice_availability_is_based_on_existing_entity_emblems(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        choice = next(
            line for line in lines if line.startswith("SVar:ChooseEmblem:")
        )
        self.assertIn("DB$ GenericChoice", choice)
        self.assertIn("Defined$ You", choice)
        self.assertIn(
            "Choices$ GainJadeEmblem,GainDamageEmblem,GainCunningEmblem",
            choice,
        )
        self.assertIn("ChoiceAmount$ 1", choice)
        self.assertNotIn("ChoiceRestriction$", choice)
        self.assertNotIn("FilterChoiceConditions$", choice)

        self.assertFalse(any("DB$ Effect" in line for line in lines))
        self.assertFalse(any("TreasureTrigger:" in line for line in lines))

        for gain_name, (path, card_name, execute) in EMBLEMS.items():
            gain = next(
                line for line in lines if line.startswith(f"SVar:{gain_name}:")
            )
            self.assertIn("DB$ MakeCard", gain)
            self.assertIn("Defined$ You", gain)
            self.assertIn(f"Name$ {card_name}", gain)
            self.assertIn("Zone$ Command", gain)
            self.assertIn("AsEmblem$ True", gain)
            self.assertIn(f"IsPresent$ Emblem.YouCtrl+named{card_name}", gain)
            self.assertIn("PresentZone$ Command", gain)
            self.assertIn("PresentCompare$ EQ0", gain)

            self.assertTrue(path.is_file(), path)
            emblem_lines = path.read_text(encoding="utf-8").splitlines()
            self.assertIn(f"Name:{card_name}", emblem_lines)
            self.assertIn("ManaCost:no cost", emblem_lines)
            self.assertIn("Types:Emblem", emblem_lines)
            emblem_trigger = next(
                line
                for line in emblem_lines
                if line.startswith("T:Mode$ Sacrificed")
            )
            self.assertIn("Mode$ Sacrificed", emblem_trigger)
            self.assertIn("ValidCard$ Treasure.token+YouCtrl", emblem_trigger)
            self.assertIn("TriggerZones$ Command", emblem_trigger)
            self.assertIn(f"Execute$ {execute}", emblem_trigger)

        jade_lines = EMBLEMS["GainJadeEmblem"][0].read_text(
            encoding="utf-8"
        ).splitlines()
        jade = next(
            line for line in jade_lines if line.startswith("SVar:CreateJadeGolem:")
        )
        self.assertIn("DB$ MakeCard", jade)
        self.assertIn("Defined$ You", jade)
        self.assertIn("Conjure$ True", jade)
        self.assertIn("Name$ 青玉魔像", jade)
        self.assertIn("Zone$ Battlefield", jade)

        damage_lines = EMBLEMS["GainDamageEmblem"][0].read_text(
            encoding="utf-8"
        ).splitlines()
        damage = next(line for line in damage_lines if line.startswith("SVar:DealRandomDamage:"))
        self.assertIn("DB$ DealDamage", damage)
        self.assertIn("ValidTgts$ Player.Other,Permanent.YouDontCtrl", damage)
        self.assertIn("TargetsAtRandom$ True", damage)
        self.assertIn("NumDmg$ 2", damage)

        cunning_lines = EMBLEMS["GainCunningEmblem"][0].read_text(
            encoding="utf-8"
        ).splitlines()
        potion = next(
            line
            for line in cunning_lines
            if line.startswith("SVar:ConjureForgedPotion:")
        )
        self.assertIn("DB$ MakeCard", potion)
        self.assertIn("Defined$ You", potion)
        self.assertIn("Conjure$ True", potion)
        self.assertIn("Name$ 伪造的药水", potion)
        self.assertIn("Amount$ 1", potion)
        self.assertIn("Zone$ Hand", potion)
        self.assertFalse(any(line.startswith("SVar:DrawCard:") for line in cunning_lines))

    def test_registration_localization_documentation_and_art(self):
        self.assertIn(
            "118 R 艾雅，玉莲帮主 @James Ryman",
            MAIN_EDITION.read_text(encoding="utf-8").splitlines(),
        )
        main_edition_lines = MAIN_EDITION.read_text(encoding="utf-8").splitlines()
        emblem_edition_lines = EMBLEM_EDITION.read_text(encoding="utf-8").splitlines()
        for collector_number, (_, card_name, _) in zip(
            (5, 6, 7), EMBLEMS.values()
        ):
            self.assertIn(
                f"{collector_number} C {card_name} @Custom",
                emblem_edition_lines,
            )
            self.assertFalse(
                any(card_name in line for line in main_edition_lines),
                card_name,
            )
        self.assertIn(
            "| 艾雅，玉莲帮主 | `{2}{B}{G}{U}`，5/3 生物～熊猫人／浪客 | "
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
                and "化生一张伪造的药水并置入你的手牌" in line
                and "你可以支付{1}" not in line
                for line in localization
            )
        )
        for _, card_name, _ in EMBLEMS.values():
            rows = [line for line in localization if line.startswith(f"{card_name}|")]
            self.assertEqual(1, len(rows), card_name)
            self.assertEqual(4, len(rows[0].split("|")), card_name)
            self.assertIn("|徽记|", rows[0])
        cunning_row = next(
            line
            for line in localization
            if line.startswith("Emblem — Aya's Cunning Treasure|")
        )
        self.assertIn("化生一张伪造的药水并置入你的手牌", cunning_row)
        self.assertNotIn("支付{1}", cunning_row)

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

        for card_name, (source, art, source_hash, art_hash) in EMBLEM_ART.items():
            self.assertTrue(source.is_file(), source)
            self.assertTrue(art.is_file(), art)
            self.assertEqual(source_hash, hashlib.sha256(source.read_bytes()).hexdigest().upper())
            self.assertEqual(art_hash, hashlib.sha256(art.read_bytes()).hexdigest().upper())
            with Image.open(source) as image:
                self.assertEqual("JPEG", image.format, card_name)
                self.assertEqual("RGB", image.mode, card_name)
            with Image.open(art) as image:
                self.assertEqual("JPEG", image.format, card_name)
                self.assertEqual("RGB", image.mode, card_name)
                self.assertEqual((960, 700), image.size, card_name)
                self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
