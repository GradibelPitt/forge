import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "blue" / "造物协议.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-de58621b-3ceb-4e44-9401-a351cbbb3d6a.png"
)
ART = ROOT / "cards" / "pictures" / "PH01" / "造物协议.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = (
    "预示 {U}（在你的回合中，你可以支付{2}并从你手上牌面朝下地放逐此牌。"
    "过了该回合后，便可利用其预示费用来施放之。）\\n"
    "发现你牌库中的一张生物牌。如果此咒语已预示，则额外化生一张该生物牌的复制置入你手中"
)


class CreationProtocolContractTest(unittest.TestCase):
    def test_foretell_and_library_discover_pipeline(self):
        text = CARD.read_text(encoding="utf-8")
        self.assertIn("Name:造物协议", text)
        self.assertIn("ManaCost:1 U", text)
        self.assertIn("Types:Sorcery", text)
        self.assertIn("K:Foretell:U", text)
        self.assertNotIn("蓄势增幅", text)
        self.assertNotIn("A:AB$ Animate", text)
        self.assertIn(
            "A:SP$ CardDiscover | Defined$ You | Source$ Library | "
            "SourceController$ You | ValidCards$ Creature | OptionCount$ 1 | "
            "Destination$ Hand | RememberChosen$ True | SubAbility$ DBConjure",
            text,
        )
        self.assertIn(
            "SVar:DBConjure:DB$ MakeCard | Defined$ You | DefinedName$ Remembered | "
            "Conjure$ True | Zone$ Hand | Condition$ Foretold",
            text,
        )
        self.assertIn(f"Oracle:{ORACLE}", text)

    def test_registration_localization_and_art(self):
        self.assertIn("72 U 造物协议 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())
        self.assertEqual(
            "4211EA50EED3984616889092FE8F094DE907F430C4325F77D3F987BC2A7ACEF0",
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((1920, 1401), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)
        expected = f"造物协议|造物协议|法术|{ORACLE}"
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())


if __name__ == "__main__":
    unittest.main()
