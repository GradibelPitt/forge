import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "multicolor" / "矿车难题.txt"
TOKEN = ROOT / "tokens" / "c_3_3_a_minecart_haste.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "hswiki-trolley-problem-full.jpg"
ART = ROOT / "cards" / "pictures" / "PH01" / "矿车难题.artcrop.jpg"
TOKEN_ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "forge-token-vehicle-tdft-12.artcrop.jpg"
)
TOKEN_ART = ROOT / "tokens" / "pictures" / "c_3_3_a_minecart_haste.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = (
    "除非你支付了矿车难题的奇迹费用，否则弃一张瞬间或法术牌，以作为施放此咒语的额外费用。\\n"
    "派出两个3/3、具敏捷异能的无色矿车衍生神器生物。\\n"
    "奇迹{B}{R}{R}"
)
SOURCE_ART_SHA256 = "A4DEFE69471D6679A2E5D47684463F08E36B302476F97E72BC6C7E8AB0C2C4CD"
TOKEN_SOURCE_ART_SHA256 = (
    "34C2727728B2D802855A408112CFB33235CD768DCCF7D7BFD178C41551EE1811"
)


class TrolleyProblemContractTest(unittest.TestCase):
    def test_normal_cast_discards_but_miracle_cast_does_not(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:矿车难题", lines)
        self.assertIn("ManaCost:B R R", lines)
        self.assertIn("Types:Sorcery", lines)
        self.assertIn("K:Miracle:B R R", lines)

        additional_cost = next(
            line for line in lines if line.startswith("S:Mode$ RaiseCost")
        )
        self.assertIn("ValidCard$ Card.Self", additional_cost)
        self.assertIn("ValidSpell$ Spell.!Miracle", additional_cost)
        self.assertIn(
            "Cost$ Discard<1/Instant;Sorcery/instant or sorcery>",
            additional_cost,
        )
        self.assertIn("EffectZone$ All", additional_cost)

    def test_spell_creates_two_hasty_minecarts(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()
        spell = next(line for line in lines if line.startswith("A:SP$ Token"))

        self.assertIn("TokenScript$ c_3_3_a_minecart_haste", spell)
        self.assertIn("TokenAmount$ 2", spell)
        self.assertIn("TokenOwner$ You", spell)
        self.assertIn(
            "SpellDescription$ 派出两个3/3、具敏捷异能的无色矿车衍生神器生物。",
            spell,
        )
        self.assertIn(f"Oracle:{ORACLE}", lines)

        token_lines = TOKEN.read_text(encoding="utf-8").splitlines()
        self.assertIn("Name:矿车", token_lines)
        self.assertIn("ManaCost:no cost", token_lines)
        self.assertIn("Types:Artifact Creature Vehicle", token_lines)
        self.assertIn("PT:3/3", token_lines)
        self.assertIn("K:Haste", token_lines)

    def test_registration_localization_documentation_and_art(self):
        self.assertIn(
            "113 R 矿车难题 @L. Lullabi & K. Turovec",
            EDITION.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            f"矿车难题|矿车难题|法术|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 矿车难题 | `{B}{R}{R}` 法术 | `cards/multicolor/矿车难题.txt` | 113 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        self.assertEqual(
            SOURCE_ART_SHA256,
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        with Image.open(ART_BACKUP) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((4500, 3652), image.size)

        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((4500, 3284), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)

        self.assertEqual(
            TOKEN_SOURCE_ART_SHA256,
            hashlib.sha256(TOKEN_ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        with Image.open(TOKEN_ART_BACKUP) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)

        with Image.open(TOKEN_ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((488, 680), image.size)


if __name__ == "__main__":
    unittest.main()
