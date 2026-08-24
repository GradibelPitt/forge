import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "multicolor" / "野性之心古夫.txt"
OLD_CARD = ROOT / "cards" / "green" / "野性之心古夫.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-91355578-c781-4710-8539-8e07cb2b3ed5.png"
)
ART = ROOT / "cards" / "pictures" / "PH01" / "野性之心古夫.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"
INSTALLER = ROOT / "tools" / "install_to_forge.ps1"

ETB = (
    "当古夫进战场时，占卜3，然后抓一张牌。你可以从你的牌库中搜寻一张基本地牌，将它放进战场，"
    "然后将你的牌库洗牌。"
)
ULTIMATE = (
    "你获得具有「你可以使用任意数量的地。在你的维持开始时，你可以从你的牌库中搜寻一张基本地牌，"
    "然后将你的牌库洗牌。」的徽记。"
)
ORACLE = (
    f"{ETB}\\n+1：抓一张牌。\\n+1：从你的牌库中搜寻一张基本地牌，将它横置放进战场，"
    f"然后将你的牌库洗牌。\\n-5：{ULTIMATE}\\n野性之心古夫可以作为你的指挥官。"
)
SOURCE_ART_SHA256 = "3017D7405852A087901DC20AE20D01B90566694D75818849704929CB317E2190"


class WildheartGuffContractTest(unittest.TestCase):
    def test_characteristics_etb_and_commander_permission(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:野性之心古夫", lines)
        self.assertIn("ManaCost:3 G U", lines)
        self.assertIn("Types:Legendary Planeswalker Guff", lines)
        self.assertIn("Loyalty:4", lines)
        self.assertIn("K:CARDNAME can be your commander.", lines)

        trigger = next(line for line in lines if line.startswith("T:Mode$ ChangesZone"))
        self.assertIn("Origin$ Any", trigger)
        self.assertIn("Destination$ Battlefield", trigger)
        self.assertIn("ValidCard$ Card.Self", trigger)
        self.assertIn("Execute$ TrigScry", trigger)
        self.assertIn(f"TriggerDescription$ {ETB}", trigger)

        scry = next(line for line in lines if line.startswith("SVar:TrigScry:"))
        self.assertIn("DB$ Scry", scry)
        self.assertIn("ScryNum$ 3", scry)
        self.assertIn("SubAbility$ DBDraw", scry)
        self.assertFalse(any("DB$ GainLife" in line for line in lines))

        draw = next(line for line in lines if line.startswith("SVar:DBDraw:"))
        self.assertIn("DB$ Draw", draw)
        self.assertIn("Defined$ You", draw)
        self.assertIn("NumCards$ 1", draw)
        self.assertIn("SubAbility$ DBLand", draw)

        land = next(line for line in lines if line.startswith("SVar:DBLand:"))
        self.assertIn("DB$ ChangeZone", land)
        self.assertIn("Origin$ Library", land)
        self.assertIn("Destination$ Battlefield", land)
        self.assertIn("ChangeType$ Land.Basic", land)
        self.assertIn("ChangeNum$ 1", land)
        self.assertIn("Shuffle$ True", land)
        self.assertNotIn("Tapped$ True", land)

    def test_loyalty_abilities_and_emblem_are_exact(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        plus_one = next(
            line
            for line in lines
            if line.startswith("A:AB$ Draw | Cost$ AddCounter<1/LOYALTY>")
        )
        self.assertIn("Planeswalker$ True", plus_one)
        self.assertIn("Defined$ You", plus_one)
        self.assertIn("NumCards$ 1", plus_one)
        self.assertIn("SpellDescription$ 抓一张牌", plus_one)

        land_plus_one = next(
            line
            for line in lines
            if line.startswith("A:AB$ ChangeZone | Cost$ AddCounter<1/LOYALTY>")
        )
        self.assertIn("Origin$ Library", land_plus_one)
        self.assertIn("Destination$ Battlefield", land_plus_one)
        self.assertIn("ChangeType$ Land.Basic", land_plus_one)
        self.assertIn("ChangeNum$ 1", land_plus_one)
        self.assertIn("Tapped$ True", land_plus_one)
        self.assertIn("Shuffle$ True", land_plus_one)

        ultimate = next(
            line
            for line in lines
            if line.startswith("A:AB$ Effect | Cost$ SubCounter<5/LOYALTY>")
        )
        self.assertIn("Planeswalker$ True", ultimate)
        self.assertIn("Ultimate$ True", ultimate)
        self.assertIn("StaticAbilities$ UnlimitedLands", ultimate)
        self.assertIn("Triggers$ EmblemUpkeep", ultimate)
        self.assertIn("Duration$ Permanent", ultimate)
        self.assertIn(f"SpellDescription$ {ULTIMATE}", ultimate)

        unlimited = next(line for line in lines if line.startswith("SVar:UnlimitedLands:"))
        self.assertIn("Mode$ Continuous", unlimited)
        self.assertIn("Affected$ You", unlimited)
        self.assertIn("EffectZone$ Command", unlimited)
        self.assertIn("AdjustLandPlays$ Unlimited", unlimited)

        upkeep = next(line for line in lines if line.startswith("SVar:EmblemUpkeep:"))
        self.assertIn("Mode$ Phase", upkeep)
        self.assertIn("Phase$ Upkeep", upkeep)
        self.assertIn("ValidPlayer$ You", upkeep)
        self.assertIn("TriggerZones$ Command", upkeep)
        self.assertIn("OptionalDecider$ You", upkeep)
        self.assertIn("Execute$ EmblemSearch", upkeep)

        search = next(line for line in lines if line.startswith("SVar:EmblemSearch:"))
        self.assertIn("DB$ ChangeZone", search)
        self.assertIn("Origin$ Library", search)
        self.assertIn("Destination$ Library", search)
        self.assertIn("ChangeType$ Land.Basic", search)
        self.assertIn("ChangeNum$ 1", search)
        self.assertIn("Shuffle$ True", search)
        self.assertNotIn("Destination$ Hand", search)
        self.assertNotIn("Destination$ Battlefield", search)

        self.assertIn(f"Oracle:{ORACLE}", lines)

    def test_registration_localization_art_and_documentation(self):
        self.assertIn(
            "110 M 野性之心古夫 @Custom",
            EDITION.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            f"野性之心古夫|野性之心古夫|传奇鹏洛客～古夫|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 野性之心古夫 | `{3}{G}{U}`，初始忠诚 4 的传奇鹏洛客～古夫 | "
            "`cards/multicolor/野性之心古夫.txt` | 110 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )
        self.assertFalse(OLD_CARD.exists())
        installer = INSTALLER.read_text(encoding="utf-8-sig")
        self.assertIn(
            "$WildheartGuffName = -join ([char[]](0x91CE, 0x6027, 0x4E4B, 0x5FC3, 0x53E4, 0x592B))",
            installer,
        )
        self.assertIn('"green\\$WildheartGuffName.txt"', installer)

        self.assertTrue(ART_BACKUP.is_file())
        self.assertEqual(
            SOURCE_ART_SHA256,
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        with Image.open(ART_BACKUP) as image:
            self.assertEqual("PNG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((450, 450), image.size)

        self.assertTrue(ART.is_file())
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((450, 328), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
