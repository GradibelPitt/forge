import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "colorless" / "小型卡扎库斯药水.txt"
EDITION = ROOT / "editions" / "Token_HS.txt"
NATIVE_TOKEN = (
    FORGE_ROOT / "forge-gui" / "res" / "tokenscripts" / "b_3_3_demon.txt"
)
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"
ART_BACKUP = (
    ROOT / "tools" / "card-artwork" / "Kazakus_Potion_1_mana_full_hswiki.jpg"
)
ART = ROOT / "cards" / "pictures" / "TOKEN_HS" / "小型卡扎库斯药水.artcrop.jpg"

SOURCE_ART_SHA256 = "BBCC967B56132D2E84C7E8B9DC966AD6FA911EDFBA70D11CB6FC6EAB8519DD79"
FINAL_ART_SHA256 = "D729605F55C98133A79CB5FBC9B4F11A2437FEA439E781F7A9E07A04FE08F9E5"
ORACLE = (
    "选择两项：\\n• 派出一个2/2的恶魔衍生生物。\\n• 发现一张恶魔牌。"
    "\\n• 抓一张牌。\\n• 小型卡扎库斯药水对任一目标造成3点伤害。"
    "\\n• 横置目标生物。它在其操控者的下个重置步骤中不能重置。"
    "\\n• 你的每个生物永久得+0/+2。\\n• 所有生物得-0/-2直到回合结束。"
    "\\n• 随机将一个生物从你的坟墓场移回战场。\\n• 获得4点生命。"
)


class SmallKazakusPotionContractTest(unittest.TestCase):
    def test_choose_two_from_nine_effects(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertEqual("Name:小型卡扎库斯药水", lines[0])
        self.assertIn("ManaCost:1", lines)
        self.assertIn("Types:Sorcery", lines)
        self.assertIn(
            "A:SP$ Charm | Choices$ DBToken,DBDiscover,DBDraw,DBDamage,"
            "DBFreeze,DBBuff,DBDebuff,DBReanimate,DBLife | CharmNum$ 2",
            lines,
        )

        expected_fragments = (
            "SVar:DBToken:DB$ Token | TokenScript$ b_3_3_demon | "
            "TokenPower$ 2 | TokenToughness$ 2",
            "SVar:DBDiscover:DB$ CardDiscover",
            "SVar:DBDraw:DB$ Draw",
            "SVar:DBDamage:DB$ DealDamage | ValidTgts$ Any | NumDmg$ 3",
            "SVar:DBFreeze:DB$ Tap | ValidTgts$ Creature",
            "SVar:DBBuff:DB$ PumpAll | ValidCards$ Creature.YouCtrl",
            "SVar:DBDebuff:DB$ PumpAll | ValidCards$ Creature",
            "SVar:DBReanimate:DB$ ChangeZone | Origin$ Graveyard",
            "SVar:DBLife:DB$ GainLife | Defined$ You | LifeAmount$ 4",
        )
        for fragment in expected_fragments:
            self.assertTrue(any(line.startswith(fragment) for line in lines), fragment)

    def test_registration_native_token_and_art_contract(self):
        edition_lines = EDITION.read_text(encoding="utf-8").splitlines()
        self.assertIn("Code=TOKEN_HS", edition_lines)
        self.assertIn("Name=衍生牌", edition_lines)
        self.assertIn(
            "1 C 小型卡扎库斯药水 @Konstantin Turovec", edition_lines
        )
        self.assertIn(
            f"小型卡扎库斯药水|小型卡扎库斯药水|法术|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )

        token_lines = NATIVE_TOKEN.read_text(encoding="utf-8").splitlines()
        self.assertIn("Types:Creature Demon", token_lines)
        self.assertIn("PT:3/3", token_lines)
        self.assertFalse(any(line.startswith("K:") for line in token_lines))

        self.assertEqual(
            SOURCE_ART_SHA256,
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        with Image.open(ART_BACKUP) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((1920, 1561), image.size)

        self.assertEqual(
            FINAL_ART_SHA256,
            hashlib.sha256(ART.read_bytes()).hexdigest().upper(),
        )
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((960, 700), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
