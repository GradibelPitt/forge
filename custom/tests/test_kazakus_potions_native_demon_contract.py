import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
SMALL_CARD = ROOT / "cards" / "colorless" / "小型卡扎库斯药水.txt"
LARGE_CARD = ROOT / "cards" / "colorless" / "卡扎库斯药水.txt"
EDITION = ROOT / "editions" / "Token_HS.txt"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"
CARDS_INDEX = ROOT / "CARDS.md"
NATIVE_DEMON = FORGE_ROOT / "forge-gui" / "res" / "tokenscripts" / "b_3_3_demon.txt"
TOKEN_INFO = (
    FORGE_ROOT
    / "forge-game"
    / "src"
    / "main"
    / "java"
    / "forge"
    / "game"
    / "card"
    / "token"
    / "TokenInfo.java"
)
SMALL_ART = ROOT / "cards" / "pictures" / "TOKEN_HS" / "小型卡扎库斯药水.artcrop.jpg"
LARGE_ART = ROOT / "cards" / "pictures" / "TOKEN_HS" / "卡扎库斯药水.artcrop.jpg"


class KazakusPotionsNativeDemonContractTest(unittest.TestCase):
    def _read_card(self, path: Path) -> str:
        self.assertTrue(path.is_file(), f"Missing recovered card script: {path}")
        return path.read_text(encoding="utf-8")

    def _assert_four_field_localization(self, name: str) -> None:
        lines = ZH_CN.read_text(encoding="utf-8").splitlines()
        matches = [line for line in lines if line.startswith(f"{name}|")]
        self.assertEqual(1, len(matches), f"Expected exactly one zh-CN record for {name}")
        fields = matches[0].split("|", 3)
        self.assertEqual(4, len(fields), f"{name} must use the four-field localization format")
        self.assertEqual(name, fields[0])
        self.assertEqual(name, fields[1])
        self.assertTrue(fields[2].strip(), f"{name} is missing localized type text")
        self.assertTrue(fields[3].strip(), f"{name} is missing localized rules text")

    def test_native_demon_prototype_and_engine_override_support(self):
        native = NATIVE_DEMON.read_text(encoding="utf-8")
        self.assertIn("Name:Demon Token", native)
        self.assertIn("Colors:black", native)
        self.assertIn("Types:Creature Demon", native)
        self.assertIn("PT:3/3", native)
        self.assertNotIn("K:Flying", native)

        token_info = TOKEN_INFO.read_text(encoding="utf-8")
        self.assertIn('sa.hasParam("TokenPower")', token_info)
        self.assertIn('sa.getParam("TokenPower")', token_info)
        self.assertIn("result.setBasePower", token_info)
        self.assertIn('sa.hasParam("TokenToughness")', token_info)
        self.assertIn('sa.getParam("TokenToughness")', token_info)
        self.assertIn("result.setBaseToughness", token_info)

    def test_small_potion_uses_native_demon_with_two_two_override(self):
        text = self._read_card(SMALL_CARD)
        self.assertIn("TokenScript$ b_3_3_demon", text)
        self.assertIn("TokenPower$ 2", text)
        self.assertIn("TokenToughness$ 2", text)
        self.assertNotIn("c_2_2_demon", text)
        self.assertNotIn("c_5_5_demon", text)
        self.assertNotIn("b_5_5_demon_flying", text)

    def test_large_potion_uses_native_demon_with_five_five_override(self):
        text = self._read_card(LARGE_CARD)
        self.assertIn("TokenScript$ b_3_3_demon", text)
        self.assertIn("TokenPower$ 5", text)
        self.assertIn("TokenToughness$ 5", text)
        self.assertNotIn("c_2_2_demon", text)
        self.assertNotIn("c_5_5_demon", text)
        self.assertNotIn("b_5_5_demon_flying", text)

    def test_no_custom_demon_token_dependency_is_restored(self):
        self.assertFalse((ROOT / "tokens" / "c_2_2_demon.txt").exists())
        self.assertFalse((ROOT / "tokens" / "c_5_5_demon.txt").exists())

    def test_registration_localization_index_and_art_contract(self):
        edition = EDITION.read_text(encoding="utf-8")
        self.assertIn("1 C 小型卡扎库斯药水 @Konstantin Turovec", edition)
        self.assertIn("3 C 卡扎库斯药水 @Konstantin Turovec", edition)

        self._assert_four_field_localization("小型卡扎库斯药水")
        self._assert_four_field_localization("卡扎库斯药水")

        cards_index = CARDS_INDEX.read_text(encoding="utf-8")
        self.assertIn("cards/colorless/小型卡扎库斯药水.txt", cards_index)
        self.assertIn("cards/colorless/卡扎库斯药水.txt", cards_index)

        self.assertTrue(SMALL_ART.is_file(), f"Missing TOKEN_HS crop: {SMALL_ART}")
        self.assertTrue(LARGE_ART.is_file(), f"Missing TOKEN_HS crop: {LARGE_ART}")


if __name__ == "__main__":
    unittest.main()
