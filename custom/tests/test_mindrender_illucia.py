import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "multicolor" / "裂心者伊露希亚.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "codex-clipboard-f1e3391d-4a9f-4a18-9a87-21e8f9394afa.png"
ART = ROOT / "cards" / "pictures" / "PH01" / "裂心者伊露希亚.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = (
    "当裂心者伊露希亚进战场时，将你对手手中每张牌的复制品各一张化生到你的手中。"
    "在你的下一个结束步骤开始时，放逐以此法化生且仍在手中的牌。"
)


class MindrenderIlluciaContractTest(unittest.TestCase):
    def test_card_characteristics_and_conjure_cleanup_chain(self):
        text = CARD.read_text(encoding="utf-8")
        self.assertIn("Name:裂心者伊露希亚", text)
        self.assertIn("ManaCost:1 U B", text)
        self.assertIn("Types:Legendary Creature Human Cleric", text)
        self.assertIn("PT:1/3", text)
        self.assertIn(f"Oracle:{ORACLE}", text)
        self.assertIn(
            "T:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | "
            "ValidCard$ Card.Self | Execute$ TrigConjure",
            text,
        )
        self.assertIn(
            "SVar:TrigConjure:DB$ MakeCard | Conjure$ True | "
            "DefinedName$ ValidHand Card.OppCtrl | Zone$ Hand | "
            "RememberMade$ True | SubAbility$ DBDelay",
            text,
        )
        self.assertIn(
            "SVar:DBDelay:DB$ DelayedTrigger | DelayedTriggerDefinedPlayer$ You | "
            "Mode$ Phase | Phase$ End of Turn | Execute$ TrigExile | "
            "RememberObjects$ Remembered | SubAbility$ DBCleanup",
            text,
        )
        self.assertIn(
            "SVar:TrigExile:DB$ ChangeZone | Defined$ DelayTriggerRemembered | "
            "Origin$ Hand | Destination$ Exile",
            text,
        )
        self.assertIn("SVar:DBCleanup:DB$ Cleanup | ClearRemembered$ True", text)
        self.assertNotIn("Token", text)

    def test_registration_art_and_localization(self):
        self.assertIn("70 M 裂心者伊露希亚 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())
        self.assertEqual(
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
            "51A9B0D5025610DB64FEBE8AD9ADF60D6ADE67C861A82BF447F6A5BCB6D84129",
        )
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((512, 374), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.02)
        self.assertIn(
            f"裂心者伊露希亚|裂心者伊露希亚|传奇生物～人类／牧师|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )


if __name__ == "__main__":
    unittest.main()
