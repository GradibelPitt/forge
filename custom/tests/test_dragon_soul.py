import hashlib
import unittest
from pathlib import Path

from PIL import Image


CUSTOM = Path(__file__).resolve().parents[1]
FORGE_ROOT = CUSTOM.parent
CARD = CUSTOM / "cards" / "white" / "巨龙之魂.txt"
TOKEN = CUSTOM / "tokens" / "c_5_5_dragon.txt"
EDITION = CUSTOM / "editions" / "Placeholder_Set.txt"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"
CATALOG = CUSTOM / "CARDS.md"
ORACLE_CATALOG = CUSTOM / "DIY卡牌_游戏内Oracle_非测试卡.txt"
ART_BACKUP = (
    CUSTOM / "tools" / "card-artwork" / "Dragon_Soul_GamesPress_3000.jpg"
)
ART_CROP = CUSTOM / "cards" / "pictures" / "PH01" / "巨龙之魂.artcrop.jpg"
TOKEN_ART_BACKUP = (
    CUSTOM
    / "tools"
    / "card-artwork"
    / "Dragon_Spirit_full_official_3000x4000.jpg"
)
TOKEN_ART = CUSTOM / "tokens" / "pictures" / "c_5_5_dragon.jpg"

ORACLE = (
    "每当你从手上施放一个龙咒语时，在巨龙之魂上放置一个龙魂指示物。\\n"
    "从巨龙之魂上移去三个龙魂指示物：派出一个5/5的龙衍生生物。"
)
ORACLE_RENDERED = ORACLE.replace("\\n", "\n")


class DragonSoulContractTest(unittest.TestCase):
    def test_characteristics_match_the_approved_design(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:巨龙之魂", lines)
        self.assertIn("ManaCost:1 W W", lines)
        self.assertIn("Types:Legendary Kindred Artifact Dragon", lines)
        self.assertIn(f"Oracle:{ORACLE}", lines)

    def test_only_dragons_cast_from_your_hand_add_a_dragon_soul_counter(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()
        trigger = next(line for line in lines if line.startswith("T:Mode$ SpellCast"))

        for field in (
            "ValidCard$ Card.Dragon+wasCastFromYourHandByYou",
            "ValidActivatingPlayer$ You",
            "TriggerZones$ Battlefield",
            "Execute$ TrigPutCounter",
        ):
            self.assertIn(field, trigger)

        put_counter = next(
            line for line in lines if line.startswith("SVar:TrigPutCounter:")
        )
        for field in (
            "DB$ PutCounter",
            "Defined$ Self",
            "CounterType$ Dragon Soul",
            "CounterNum$ 1",
        ):
            self.assertIn(field, put_counter)

    def test_three_matching_counters_create_exactly_one_plain_five_five_dragon(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()
        ability = next(line for line in lines if line.startswith("A:AB$ Token"))

        for field in (
            "Cost$ SubCounter<3/Dragon Soul>",
            "TokenScript$ c_5_5_dragon",
            "TokenAmount$ 1",
            "TokenOwner$ You",
        ):
            self.assertIn(field, ability)

        token_lines = TOKEN.read_text(encoding="utf-8").splitlines()
        self.assertEqual(
            [
                "Name:Dragon Token",
                "ManaCost:no cost",
                "Types:Creature Dragon",
                "PT:5/5",
                "Oracle:",
            ],
            token_lines,
        )
        self.assertFalse(any(line.startswith("Colors:") for line in token_lines))
        self.assertFalse(any(line.startswith("K:") for line in token_lines))

    def test_registration_localization_and_catalogs_are_complete(self):
        edition_lines = EDITION.read_text(encoding="utf-8").splitlines()
        self.assertEqual(1, edition_lines.count("190 M 巨龙之魂 @Tyler Walpole"))
        self.assertEqual(1, edition_lines.count("c_5_5_dragon"))

        localization = ZH_CN.read_text(encoding="utf-8").splitlines()
        self.assertEqual(
            1,
            localization.count(
                f"巨龙之魂|巨龙之魂|传奇亲缘神器～龙|{ORACLE}"
            ),
        )

        catalog = CATALOG.read_text(encoding="utf-8")
        self.assertIn(
            "| 巨龙之魂 | `{1}{W}{W}` 传奇亲缘神器～龙 | "
            "`cards/white/巨龙之魂.txt` | 190 |",
            catalog,
        )
        self.assertIn(
            "| 龙（衍生物） | 无色 5/5 生物～龙 | "
            "`tokens/c_5_5_dragon.txt` | 衍生物 |",
            catalog,
        )

        oracle_catalog = ORACLE_CATALOG.read_text(encoding="utf-8")
        self.assertIn("巨龙之魂\n版本：PH01 / 190", oracle_catalog)
        self.assertIn(f"Oracle：{ORACLE_RENDERED}", oracle_catalog)

    def test_official_original_and_crop_compatible_art_are_preserved(self):
        images = (
            (
                ART_BACKUP,
                (3000, 3000),
                "729164A3DC77D0DF94ECA7BB118A285A603A54E2D41F2C41118C67CB13FBD67B",
            ),
            (
                ART_CROP,
                (3000, 2190),
                "FD68E2F4E348519C1BBADDC82CE40F5B2FB0059822E29FA4F30B3873A1AF62AE",
            ),
            (
                TOKEN_ART_BACKUP,
                (3000, 4000),
                "2B6A7CF80D8F94A756AE6C610DB5D41310363BC8160D236D82C216272917C2CC",
            ),
            (
                TOKEN_ART,
                (3000, 2100),
                "06EC364A7B8281644FE2987A522F889F5C9FD1656902D4395712C9A2AFB44F43",
            ),
        )
        for path, expected_size, expected_hash in images:
            self.assertTrue(path.is_file(), path)
            self.assertEqual(
                expected_hash,
                hashlib.sha256(path.read_bytes()).hexdigest().upper(),
                path,
            )
            with Image.open(path) as image:
                self.assertEqual("JPEG", image.format, path)
                self.assertEqual("RGB", image.mode, path)
                self.assertEqual(expected_size, image.size, path)

        with Image.open(ART_CROP) as image:
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
