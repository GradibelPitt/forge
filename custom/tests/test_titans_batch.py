from hashlib import sha256
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
TRANSLATIONS = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"
CARDS_DOC = ROOT / "CARDS.md"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "titans-1.31"
V07_DISPLAY_NAME = "终极V-07-TR-0N"
V07_INTERNAL_NAME = "V07TRON Prime"

TITANS = {
    "阿曼苏尔": "amanthul.jpg",
    "灭世者萨格拉斯": "sargeras.jpg",
    "兵主": "the_primus.jpg",
    "生命的缚誓者艾欧娜尔": "eonar.jpg",
    "维和者阿米图斯": "amitus.jpg",
    "诺甘农": "norgannon.jpg",
    "雷霆之神高戈奈斯": "golganneth.jpg",
    "卡兹格罗斯": "khazgoroth.jpg",
    "翠绿之星阿古斯": "argus.jpg",
    "复仇者阿格拉玛": "aggramar.jpg",
    V07_DISPLAY_NAME: "v07tron_prime.jpg",
}

TOKENS = {
    "br_3_2_burning_legion": "burning_legion_3_2.jpg",
    "br_6_6_burning_legion": "burning_legion_6_6.jpg",
    "g_3_3_zombie_reach_persist": "zombie_3_3.jpg",
    "g_5_5_treefolk_reach": "treefolk_5_5.jpg",
    "g_2_2_elemental_reach": "elemental_2_2.jpg",
    "g_3_3_dwarf_double_strike": "dwarf_3_3.jpg",
    "c_taeshalach_fading3": "taeshalach.jpg",
}


def digest(path: Path) -> str:
    return sha256(path.read_bytes()).hexdigest()


def titan_script(name: str) -> Path:
    filename = "v07tron_prime" if name == V07_DISPLAY_NAME else name
    return ROOT / "cards" / "multicolor" / f"{filename}.txt"


def titan_internal_name(name: str) -> str:
    return V07_INTERNAL_NAME if name == V07_DISPLAY_NAME else name


class TitansBatchContractTest(unittest.TestCase):
    def test_exactly_the_requested_titan_scripts_exist(self):
        for name in TITANS:
            path = titan_script(name)
            with self.subTest(name=name):
                self.assertTrue(path.is_file(), path)
                text = path.read_text(encoding="utf-8")
                self.assertIn(f"Name:{titan_internal_name(name)}", text)
                self.assertIn("Types:Legendary", text)
                self.assertIn("Creature", text)
                self.assertEqual(3, text.count("Exhaust$ True"))
                self.assertIn("Oracle:", text)
                self.assertIn("竭绝", text.split("Oracle:", 1)[1])

    def test_yogg_saron_slide_is_not_part_of_this_batch(self):
        self.assertNotIn("尤格萨隆", TITANS)
        self.assertEqual(11, len(TITANS))

    def test_key_mechanics_are_present(self):
        expected = {
            "阿曼苏尔": ("DB$ Discover | Num$ 5", "NonLegendary$ True", "FINALITY"),
            "灭世者萨格拉斯": ("燃烧军团", "Creature.nonDemon", "Reach"),
            "兵主": ("g_3_3_zombie_reach_persist", "ForgetOnCast", "P0P1"),
            "生命的缚誓者艾欧娜尔": ("UntapAll", "StartingLife", "Hand"),
            "维和者阿米图斯": ("ReplaceDamage", "Animate", "P1P1"),
            "诺甘农": ("Foretold$ True", "CopySpellAbility", "UntilYourNextTurn"),
            "雷霆之神高戈奈斯": ("OnlyFirstSpell$ True", "ChangeNum$ 3", "hasKeywordStorm"),
            "卡兹格罗斯": ("Prevent all combat damage", "Fight", "SHIELD"),
            "翠绿之星阿古斯": ("Riot", "CounterType$ Lifelink", "NumCards$ 2"),
            "复仇者阿格拉玛": ("泰沙拉克", "Triggers$", "FADE"),
            V07_DISPLAY_NAME: (
                "ManaCost:C/W C/U C/B C/R C/G",
                "IsPresent$ Creature.Other+YouCtrl",
                "CopySpellAbility",
                "NumDmg$ 4",
                "TokenScript$ c_a_treasure_sac",
                "CounterType$ Hexproof",
            ),
        }
        for name, needles in expected.items():
            text = titan_script(name).read_text(encoding="utf-8")
            for needle in needles:
                with self.subTest(name=name, needle=needle):
                    self.assertIn(needle, text)

    def test_amanthul_calls_native_discover_and_uses_qingtan_terminology(self):
        script = titan_script("阿曼苏尔").read_text(encoding="utf-8")
        oracle = script.split("Oracle:", 1)[1]
        translation = next(
            line
            for line in TRANSLATIONS.read_text(encoding="utf-8").splitlines()
            if line.startswith("阿曼苏尔|")
        )
        cards_doc = CARDS_DOC.read_text(encoding="utf-8")

        self.assertIn("DB$ Discover | Num$ 5", script)
        for text in (oracle, translation, cards_doc):
            self.assertIn("倾探5", text)
            self.assertNotIn("发现5", text)

    def test_edition_uses_full_image_records_41_through_51(self):
        rows = EDITION.read_text(encoding="utf-8").splitlines()
        for number, name in enumerate(TITANS, start=41):
            expected = f"{number} M {titan_internal_name(name)}"
            with self.subTest(name=name):
                self.assertIn(expected, rows)
                self.assertNotIn(expected + " @Custom", rows)

    def test_main_images_are_byte_for_byte_replacements(self):
        for name, backup_name in TITANS.items():
            source = ART_BACKUP / backup_name
            installed_source = ROOT / "cards" / "pictures" / "PH01" / f"{titan_internal_name(name)}.full.jpg"
            with self.subTest(name=name):
                self.assertTrue(source.is_file(), source)
                self.assertTrue(installed_source.is_file(), installed_source)
                self.assertEqual(digest(source), digest(installed_source))

    def test_token_scripts_and_full_images_are_installed_as_pairs(self):
        for basename, backup_name in TOKENS.items():
            script = ROOT / "tokens" / f"{basename}.txt"
            image = ROOT / "tokens" / "pictures" / f"{basename}.jpg"
            backup = ART_BACKUP / backup_name
            with self.subTest(token=basename):
                self.assertTrue(script.is_file(), script)
                self.assertTrue(image.is_file(), image)
                self.assertTrue(backup.is_file(), backup)
                self.assertEqual(digest(backup), digest(image))

    def test_translation_rows_cover_every_titan(self):
        text = TRANSLATIONS.read_text(encoding="utf-8")
        for name in TITANS:
            with self.subTest(name=name):
                internal_name = titan_internal_name(name)
                rows = [line for line in text.splitlines() if line.startswith(internal_name + "|")]
                self.assertEqual(1, len(rows))
                self.assertGreaterEqual(rows[0].count("|"), 3)
                self.assertEqual(name, rows[0].split("|", 2)[1])
                self.assertIn("长老", rows[0])
                self.assertNotIn("古老", rows[0])

    def test_v07tron_uses_a_searchable_hyphenless_internal_key(self):
        self.assertIn("v07tron", V07_INTERNAL_NAME.lower())
        self.assertFalse((ROOT / "cards" / "multicolor" / "_v_07_tr_0n.txt").exists())
        self.assertFalse((ROOT / "cards" / "pictures" / "PH01" / f"{V07_DISPLAY_NAME}.full.jpg").exists())
        self.assertNotIn(f"51 M {V07_DISPLAY_NAME}", EDITION.read_text(encoding="utf-8").splitlines())


if __name__ == "__main__":
    unittest.main()
