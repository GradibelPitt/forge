import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
PILOT = ROOT / "cards" / "colorless" / "飞行员帕奇斯.txt"
PARACHUTE = ROOT / "cards" / "colorless" / "帕奇斯的降落伞.txt"
PILOT_ART = ROOT / "cards" / "pictures" / "PH01" / "飞行员帕奇斯.artcrop.jpg"
PARACHUTE_ART = (
    ROOT / "cards" / "pictures" / "TOKEN_HS" / "帕奇斯的降落伞.artcrop.jpg"
)
PH01 = ROOT / "editions" / "Placeholder_Set.txt"
TOKEN_HS = ROOT / "editions" / "Token_HS.txt"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class PilotPatchesParachuteContractTest(unittest.TestCase):
    def test_pilot_conjures_six_parachutes(self):
        lines = PILOT.read_text(encoding="utf-8").splitlines()

        for required in (
            "Name:飞行员帕奇斯",
            "ManaCost:U",
            "Types:Creature Pirate",
            "PT:1/1",
            "K:DeckLimit:1:你的套牌中只能包含一张名为CARDNAME的牌。",
            "K:Flying",
        ):
            self.assertIn(required, lines)

        trigger = next(line for line in lines if line.startswith("T:Mode$ ChangesZone"))
        self.assertIn("Destination$ Battlefield", trigger)
        self.assertIn("ValidCard$ Card.Self", trigger)
        self.assertIn("Execute$ TrigConjure", trigger)

        conjure = next(line for line in lines if line.startswith("SVar:TrigConjure:"))
        self.assertIn("DB$ MakeCard", conjure)
        self.assertIn("Conjure$ True", conjure)
        self.assertIn("Name$ 帕奇斯的降落伞", conjure)
        self.assertIn("Amount$ 6", conjure)
        self.assertIn("Zone$ Library", conjure)
        self.assertIn("SubAbility$ ShuffleLibrary", conjure)

    def test_parachute_creates_a_hasty_pirate_and_draws(self):
        lines = PARACHUTE.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:帕奇斯的降落伞", lines)
        self.assertIn("ManaCost:0", lines)
        self.assertIn("Types:Instant", lines)
        ability = next(line for line in lines if line.startswith("A:SP$ Token"))
        self.assertIn("TokenScript$ c_1_1_pirate_haste", ability)
        self.assertIn("TokenAmount$ 1", ability)
        self.assertIn("SubAbility$ DBDraw", ability)
        self.assertIn(
            "SVar:DBDraw:DB$ Draw | Defined$ You | NumCards$ 1 | "
            "SpellDescription$ 抓一张牌。",
            lines,
        )

    def test_registration_localization_and_catalog_are_complete(self):
        self.assertEqual(
            ["134 M 飞行员帕奇斯 @Custom"],
            [line for line in PH01.read_text(encoding="utf-8").splitlines()
             if "飞行员帕奇斯" in line],
        )
        self.assertEqual(
            ["4 C 帕奇斯的降落伞 @Custom"],
            [line for line in TOKEN_HS.read_text(encoding="utf-8").splitlines()
             if "帕奇斯的降落伞" in line],
        )

        localization = ZH_CN.read_text(encoding="utf-8").splitlines()
        for name in ("飞行员帕奇斯", "帕奇斯的降落伞"):
            rows = [line for line in localization if line.startswith(f"{name}|")]
            self.assertEqual(1, len(rows), name)
            self.assertEqual(4, len(rows[0].split("|")), name)

        catalog = (ROOT / "CARDS.md").read_text(encoding="utf-8")
        self.assertIn("| 飞行员帕奇斯 | `{U}`，1/1 生物～海盗 |", catalog)
        self.assertIn("| 帕奇斯的降落伞 | `{0}` 瞬间 |", catalog)

    def test_recovered_art_is_preserved(self):
        expected = {
            PILOT_ART: "D1BFCA8DA304A2E6145BB8B52AACF0E917570AFE081F5FFA860230B5CE477B93",
            PARACHUTE_ART: "D76DCB17B91F87140A847829A989C140B3E75EF9369A26F711E1C382E0A8A183",
        }
        for path, digest in expected.items():
            with self.subTest(path=path):
                self.assertEqual(digest, hashlib.sha256(path.read_bytes()).hexdigest().upper())
                with Image.open(path) as image:
                    self.assertEqual("JPEG", image.format)
                    self.assertEqual("RGB", image.mode)
                    self.assertEqual((960, 700), image.size)
                    self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
