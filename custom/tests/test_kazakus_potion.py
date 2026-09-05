import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "colorless" / "卡扎库斯药水.txt"
EDITION = ROOT / "editions" / "Token_HS.txt"
NATIVE_TOKEN = (
    FORGE_ROOT / "forge-gui" / "res" / "tokenscripts" / "b_3_3_demon.txt"
)
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "karzakusspotion.jpg"
ART = ROOT / "cards" / "pictures" / "TOKEN_HS" / "卡扎库斯药水.artcrop.jpg"

SOURCE_ART_SHA256 = "0517009A778FF3842212DDB5166E90505EFCE53AF8F2FA1AEB7EC6700B3140F0"
FINAL_ART_SHA256 = "58A2F6B790ADF434F3B3533C90590728A7707B2EF4E138C53398F2BD44A6ECA8"
ORACLE = (
    "选择两项：\\n• 派出一个5/5的恶魔衍生生物。\\n• 发现两张恶魔牌。"
    "\\n• 抓两张牌。\\n• 卡扎库斯药水对任一目标造成5点伤害。"
    "\\n• 横置至多两个目标生物。它们在各自操控者的下个重置步骤中不能重置。"
    "\\n• 你的每个生物永久得+0/+4。\\n• 所有生物得-0/-4直到回合结束。"
    "\\n• 随机将两个生物从你的坟墓场移回战场。\\n• 获得7点生命。"
    "\\n• 目标生物失去所有异能且成为1/1的绿色羊生物。"
)


class KazakusPotionContractTest(unittest.TestCase):
    def test_choose_two_from_ten_scaled_effects(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()
        card_text = "\n".join(lines)

        self.assertEqual("Name:卡扎库斯药水", lines[0])
        self.assertIn("ManaCost:3", lines)
        self.assertIn("Types:Sorcery", lines)
        self.assertIn(
            "A:SP$ Charm | Choices$ DBToken,DBDiscover,DBDraw,DBDamage,"
            "DBFreeze,DBBuff,DBDebuff,DBReanimate,DBLife,DBSheep | CharmNum$ 2",
            lines,
        )
        required = (
            "SVar:DBToken:DB$ Token | TokenScript$ b_3_3_demon | "
            "TokenPower$ 5 | TokenToughness$ 5",
            "SVar:DBDiscover:DB$ CardDiscover",
            "SubAbility$ DBDiscoverSecond",
            "SVar:DBDiscoverSecond:DB$ CardDiscover",
            "SVar:DBDraw:DB$ Draw | Defined$ You | NumCards$ 2",
            "SVar:DBDamage:DB$ DealDamage | ValidTgts$ Any | NumDmg$ 5",
            "TargetMin$ 0 | TargetMax$ 2",
            "SVar:DBBuff:DB$ PumpAll | ValidCards$ Creature.YouCtrl",
            "NumDef$ +4 | Duration$ Perpetual",
            "SVar:DBDebuff:DB$ PumpAll | ValidCards$ Creature | NumDef$ -4",
            "ChangeType$ Creature.YouOwn | ChangeNum$ 2",
            "SVar:DBLife:DB$ GainLife | Defined$ You | LifeAmount$ 7",
            "SVar:DBSheep:DB$ Animate | ValidTgts$ Creature",
            "Power$ 1 | Toughness$ 1 | RemoveAllAbilities$ True",
            "Colors$ Green | OverwriteColors$ True | Types$ Sheep",
            "RemoveCreatureTypes$ True | Duration$ Permanent | IsCurse$ True",
        )
        for fragment in required:
            self.assertIn(fragment, card_text)
        self.assertNotIn("RemoveCardTypes$ True", card_text)
        self.assertNotIn("c_5_5_demon", card_text)
        self.assertEqual(f"Oracle:{ORACLE}", lines[-1])

    def test_registration_native_token_localization_and_art(self):
        edition_lines = EDITION.read_text(encoding="utf-8").splitlines()
        self.assertIn("3 C 卡扎库斯药水 @Konstantin Turovec", edition_lines)

        token_lines = NATIVE_TOKEN.read_text(encoding="utf-8").splitlines()
        self.assertIn("Types:Creature Demon", token_lines)
        self.assertIn("PT:3/3", token_lines)
        self.assertFalse(any(line.startswith("K:") for line in token_lines))

        self.assertIn(
            f"卡扎库斯药水|卡扎库斯药水|法术|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertEqual(
            SOURCE_ART_SHA256,
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        with Image.open(ART_BACKUP) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((1200, 974), image.size)

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
