import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "black" / "萨瓦丝女王.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-f06c4a7b-a154-450e-a660-de139d6eab66.png"
)
ART = ROOT / "cards" / "pictures" / "PH01" / "萨瓦丝女王.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

SOURCE_ORACLE = (
    "Flying\\n"
    "If a spell or ability causes you to discard CARDNAME, you may instead cast "
    "it by paying {B} rather than paying its mana cost.\\n"
    "When CARDNAME enters, unless you pay {1}{B}, return it to your hand and it "
    "perpetually gets +2/+2."
)
ZH_ORACLE = (
    "飞行\\n"
    "如果一个咒语或异能使你弃掉萨瓦丝女王，你可以改为支付{B}而非支付其法术力费用来施放它。\\n"
    "当萨瓦丝女王进战场时，除非你支付{1}{B}，否则将此牌移回你手上且它永久得+2/+2。"
)


class QueenSavathContractTest(unittest.TestCase):
    def test_characteristics_and_effect_only_discard_replacement(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:萨瓦丝女王", text)
        self.assertIn("ManaCost:B B", text)
        self.assertIn("Types:Legendary Creature Insect", text)
        self.assertIn("PT:2/2", text)
        self.assertIn("K:Flying", text)
        self.assertNotIn("K:Madness", text)
        self.assertIn(
            "R:Event$ Moved | ActiveZones$ Hand | ValidCard$ Card.Self | "
            "ValidCause$ SpellAbility | Discard$ True | EffectOnly$ True | "
            "Optional$ True | ReplaceWith$ CastSelf",
            text,
        )
        self.assertIn(
            "SVar:CastSelf:DB$ Play | Defined$ ReplacedCard | "
            "ValidSA$ Spell | Controller$ ReplacedCardController | "
            "PlayCost$ B",
            text,
        )
        self.assertNotIn("WithoutManaCost$ True", text)
        cast_line = next(
            line for line in text.splitlines() if line.startswith("SVar:CastSelf:")
        )
        self.assertNotIn("Optional$", cast_line)

    def test_unpaid_enters_trigger_returns_immediately_and_stacks_perpetual_buff(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "T:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | "
            "ValidCard$ Card.Self | Execute$ TrigReturn",
            text,
        )
        self.assertIn(
            "SVar:TrigReturn:DB$ ChangeZone | Defined$ Self | "
            "Origin$ Battlefield | Destination$ Hand | RememberChanged$ True | "
            "UnlessCost$ 1 B | UnlessPayer$ You | "
            "UnlessResolveSubs$ WhenNotPaid | SubAbility$ DBPerpetualBuff",
            text,
        )
        self.assertNotIn("DB$ DelayedTrigger", text)
        self.assertNotIn("DelayTriggerRememberedLKI", text)
        self.assertIn(
            "SVar:DBPerpetualBuff:DB$ Pump | Defined$ Remembered | "
            "PumpZone$ Hand | NumAtt$ +2 | NumDef$ +2 | "
            "Duration$ Perpetual | SubAbility$ DBCleanup",
            text,
        )
        self.assertIn(
            "SVar:DBCleanup:DB$ Cleanup | ClearRemembered$ True",
            text,
        )
        self.assertIn(f"Oracle:{SOURCE_ORACLE}", text)

    def test_registration_localization_and_original_art_crop(self):
        self.assertIn(
            "85 M 萨瓦丝女王 @Custom",
            EDITION.read_text(encoding="utf-8"),
        )
        self.assertIn(
            f"萨瓦丝女王|萨瓦丝女王|传奇生物～昆虫|{ZH_ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )

        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())
        self.assertEqual(
            "5E39EA56A00BF59410ED3C46EF321DCD6E4A03F26D4B364669CCCAA181F06226",
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        with Image.open(ART_BACKUP) as image:
            self.assertEqual((450, 600), image.size)
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((450, 328), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
