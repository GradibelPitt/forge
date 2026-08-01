import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "white" / "阳光汲取者莱妮莎.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Sunsapper_Lynessa_full.jpg"
ART = ROOT / "cards" / "pictures" / "PH01" / "阳光汲取者莱妮莎.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

SOURCE_ORACLE = (
    "Flying\\n"
    "Whenever you cast an instant or sorcery spell with mana value 2 or less, "
    "copy that spell. You may choose new targets for the copy.\\n"
    "{U}, Exile three cards from your graveyard: This turn, you may cast sorcery "
    "spells as though they had flash."
)
ZH_ORACLE = (
    "飞行\\n"
    "每当你施放法术力值等于或小于2的瞬间或法术咒语时，复制该咒语。"
    "你可以为该复制品选择新的目标。\\n"
    "{U}，从你的坟墓场放逐三张牌：本回合中，你可以将法术咒语视同具有闪现异能地来施放。"
)


class SunsapperLynessaContractTest(unittest.TestCase):
    def test_characteristics_and_low_mana_value_spell_copy(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:阳光汲取者莱妮莎", text)
        self.assertIn("ManaCost:2 W W", text)
        self.assertIn("Types:Legendary Creature Troll Monk", text)
        self.assertIn("PT:2/6", text)
        self.assertIn("K:Flying", text)
        self.assertIn(
            "T:Mode$ SpellCast | ValidCard$ Instant.cmcLE2,Sorcery.cmcLE2 | "
            "ValidActivatingPlayer$ You | TriggerZones$ Battlefield | "
            "Execute$ TrigCopy",
            text,
        )
        self.assertIn(
            "SVar:TrigCopy:DB$ CopySpellAbility | "
            "Defined$ TriggeredSpellAbility | MayChooseTarget$ True",
            text,
        )

    def test_graveyard_cost_grants_sorcery_flash_this_turn(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "A:AB$ Effect | Cost$ U ExileFromGrave<3/Card/cards> | "
            "StaticAbilities$ STPlay | Duration$ EndOfTurn",
            text,
        )
        self.assertIn(
            "SVar:STPlay:Mode$ CastWithFlash | ValidCard$ Sorcery | "
            "ValidSA$ Spell | Caster$ You",
            text,
        )
        self.assertIn(f"Oracle:{SOURCE_ORACLE}", text)

    def test_registration_localization_and_original_art_crop(self):
        self.assertIn("84 M 阳光汲取者莱妮莎 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertIn(
            f"阳光汲取者莱妮莎|阳光汲取者莱妮莎|传奇生物～巨魔／修行僧|{ZH_ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )

        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())
        self.assertEqual(
            "21B09D4EC408AF067923E6B2AB3935B832BED6F30555AB650F3A26700271F2F0",
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((3000, 2190), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
