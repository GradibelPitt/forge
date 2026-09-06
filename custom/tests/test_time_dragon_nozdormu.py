import hashlib
import unittest
from pathlib import Path

from PIL import Image


CUSTOM = Path(__file__).resolve().parents[1]
FORGE_ROOT = CUSTOM.parent
CARD = CUSTOM / "cards" / "multicolor" / "nozdormu_time_dragon.txt"
EMBLEM = CUSTOM / "cards" / "colorless" / "emblem_time_conflux.txt"
EDITION = CUSTOM / "editions" / "Placeholder_Set.txt"
TOKEN_EDITION = CUSTOM / "editions" / "Token_HS.txt"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"
CATALOG = CUSTOM / "CARDS.md"
ORACLE_CATALOG = CUSTOM / "DIY卡牌_游戏内Oracle_非测试卡.txt"
ART_BACKUP = (
    CUSTOM
    / "tools"
    / "card-artwork"
    / "DRG_309_Nozdormu_the_Timeless_original.png"
)
ART_CROP = (
    CUSTOM
    / "cards"
    / "pictures"
    / "PH01"
    / "时光巨龙诺兹多姆.artcrop.jpg"
)
EMBLEM_ART = (
    CUSTOM
    / "cards"
    / "pictures"
    / "TOKEN_HS"
    / "Emblem — 时光流汇.artcrop.jpg"
)

EMBLEM_TEXT = (
    "在你的维持开始时，加十点任意颜色的法术力，你可以随意分配。"
    "以此法获得的法术力在本回合中不会被清除。"
)
ORACLE = (
    "当你施放时光巨龙诺兹多姆时，结束你的回合，放逐所有地，每位牌手各获得一个"
    f"名为时光流汇且具有「{EMBLEM_TEXT}」的徽记。"
)


class TimeDragonNozdormuContractTest(unittest.TestCase):
    def test_characteristics_and_oracle_match_the_requested_card(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:时光巨龙诺兹多姆", lines)
        self.assertIn("ManaCost:W W G G", lines)
        self.assertIn("Types:Legendary Creature Dragon", lines)
        self.assertIn("PT:8/8", lines)
        self.assertIn(f"Oracle:{ORACLE}", lines)

    def test_cast_trigger_ends_the_turn_then_exiles_every_land(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()
        trigger = next(line for line in lines if line.startswith("T:Mode$ SpellCast"))
        for field in (
            "ValidCard$ Card.Self",
            "TriggerZones$ Stack",
            "Execute$ EndYourTurn",
        ):
            self.assertIn(field, trigger)

        end_turn = next(line for line in lines if line.startswith("SVar:EndYourTurn:"))
        self.assertIn("DB$ EndTurn", end_turn)
        self.assertIn("SubAbility$ ExileAllLands", end_turn)

        exile = next(line for line in lines if line.startswith("SVar:ExileAllLands:"))
        for field in (
            "DB$ ChangeZoneAll",
            "ChangeType$ Land",
            "Origin$ Battlefield",
            "Destination$ Exile",
            "SubAbility$ GrantTimeConfluxToEachPlayer",
        ):
            self.assertIn(field, exile)

    def test_every_player_gets_a_registered_stackable_emblem(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()
        grant = next(
            line
            for line in lines
            if line.startswith("SVar:GrantTimeConfluxToEachPlayer:")
        )
        for field in (
            "DB$ MakeCard",
            "Defined$ Player",
            "Name$ Emblem — 时光流汇",
            "Zone$ Command",
            "AsEmblem$ True",
        ):
            self.assertIn(field, grant)
        self.assertNotIn("DB$ RepeatEach", grant)

    def test_emblem_upkeep_adds_ten_freely_allocated_persistent_mana(self):
        lines = EMBLEM.read_text(encoding="utf-8").splitlines()
        self.assertIn("Name:Emblem — 时光流汇", lines)
        self.assertIn("ManaCost:no cost", lines)
        self.assertIn("Types:Emblem", lines)
        self.assertIn("AI:RemoveDeck:All", lines)
        self.assertIn(f"Oracle:{EMBLEM_TEXT}", lines)

        upkeep = next(line for line in lines if line.startswith("T:Mode$ Phase"))
        for field in (
            "Phase$ Upkeep",
            "ValidPlayer$ You",
            "TriggerZones$ Command",
            "Execute$ AddTimeConfluxMana",
        ):
            self.assertIn(field, upkeep)

        mana = next(
            line for line in lines if line.startswith("SVar:AddTimeConfluxMana:")
        )
        for field in (
            "DB$ Mana",
            "Defined$ TriggeredPlayer",
            "Produced$ Combo Any",
            "Amount$ 10",
            "PersistentMana$ True",
        ):
            self.assertIn(field, mana)

    def test_registration_localization_and_catalogs_are_complete(self):
        edition_lines = EDITION.read_text(encoding="utf-8").splitlines()
        self.assertEqual(
            1, edition_lines.count("192 M 时光巨龙诺兹多姆 @Ludo Lullabi")
        )
        self.assertNotIn("191 M 时光巨龙诺兹多姆 @Ludo Lullabi", edition_lines)

        token_lines = TOKEN_EDITION.read_text(encoding="utf-8").splitlines()
        self.assertEqual(
            1, token_lines.count("10 C Emblem — 时光流汇 @Ludo Lullabi")
        )

        localization = ZH_CN.read_text(
            encoding="utf-8", errors="surrogateescape"
        ).splitlines()
        self.assertEqual(
            1,
            localization.count(
                f"时光巨龙诺兹多姆|时光巨龙诺兹多姆|传奇生物～龙|{ORACLE}"
            ),
        )
        self.assertEqual(
            1,
            localization.count(
                f"Emblem — 时光流汇|Emblem — 时光流汇|徽记|{EMBLEM_TEXT}"
            ),
        )

        catalog = CATALOG.read_text(encoding="utf-8")
        self.assertIn("nozdormu_time_dragon.txt", catalog)
        self.assertIn("| 192 | 施放触发依次结束本回合、放逐所有地", catalog)
        self.assertIn("emblem_time_conflux.txt", catalog)
        self.assertIn("| TOKEN_HS / 10 | 由时光巨龙诺兹多姆", catalog)

        oracle_catalog = ORACLE_CATALOG.read_text(encoding="utf-8")
        self.assertIn("时光巨龙诺兹多姆\n版本：PH01 / 192", oracle_catalog)
        self.assertIn(f"Oracle：{ORACLE}", oracle_catalog)
        self.assertIn("Emblem — 时光流汇\n版本：TOKEN_HS / 10", oracle_catalog)
        self.assertIn(f"Oracle：{EMBLEM_TEXT}", oracle_catalog)

    def test_original_and_both_dynamic_frame_crops_are_complete(self):
        self.assertTrue(ART_BACKUP.is_file(), ART_BACKUP)
        with Image.open(ART_BACKUP) as image:
            self.assertEqual("PNG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((512, 512), image.size)

        for art in (ART_CROP, EMBLEM_ART):
            self.assertTrue(art.is_file(), art)
            with Image.open(art) as image:
                self.assertEqual("JPEG", image.format)
                self.assertEqual("RGB", image.mode)
                self.assertEqual((512, 374), image.size)
                self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)

        self.assertEqual(
            hashlib.sha256(ART_CROP.read_bytes()).digest(),
            hashlib.sha256(EMBLEM_ART.read_bytes()).digest(),
        )


if __name__ == "__main__":
    unittest.main()
