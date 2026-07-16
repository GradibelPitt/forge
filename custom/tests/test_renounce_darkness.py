import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "black" / "弃暗投明.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-818ec03d-a8fc-4437-a278-152abd303d8d.png"
)
ART = ROOT / "cards" / "pictures" / "PH01" / "弃暗投明.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = (
    "将你手牌和牌库中的每张黑色牌替换为一张随机选择的、总法术力值相同、"
    "非黑色且非无色的牌，这些牌以及与其同名的牌永久获得调和"
    "（你可以用任意颜色的法术力支付此咒语的法术力费用）和"
    "“此咒语的施放费用减少{2}”"
)


class RenounceDarknessContractTest(unittest.TestCase):
    def test_card_characteristics_and_exact_description(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:弃暗投明", text)
        self.assertIn("ManaCost:B", text)
        self.assertIn("Types:Sorcery", text)
        self.assertIn(f"Oracle:{ORACLE}", text)
        self.assertIn(f"SpellDescription$ {ORACLE}", text)

    def test_batch_replacement_uses_cached_same_mana_value_pool(self):
        text = CARD.read_text(encoding="utf-8")
        ability = next(line for line in text.splitlines() if line.startswith("A:SP$ ReplaceCards"))

        self.assertIn("Defined$ You", ability)
        self.assertIn("Zones$ Hand,Library", ability)
        self.assertIn("ValidCards$ Card.Black", ability)
        self.assertIn("ReplacementValid$ Card.nonBlack+nonColorless", ability)
        self.assertIn("MatchManaValue$ True", ability)
        self.assertIn("RememberNames$ True", ability)
        self.assertIn("SubAbility$ CreateHarmonyEmblem", ability)
        self.assertNotIn("CardDiscover", text)
        self.assertNotIn("RepeatEach", text)

    def test_permanent_emblem_checks_deduplicated_names_globally(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "SVar:CreateHarmonyEmblem:DB$ Effect | Name$ Emblem — 弃暗投明 | "
            "EffectOwner$ You | StaticAbilities$ HarmonyByName,ReduceByName | "
            "Duration$ Permanent",
            text,
        )
        self.assertIn(
            "SVar:HarmonyByName:Mode$ ManaConvert | ValidPlayer$ You | "
            "ValidCard$ Card.sharesNameWith NamedCards | ValidSA$ Spell | "
            "ManaConversion$ AnyType->AnyColor",
            text,
        )
        self.assertIn(
            "SVar:ReduceByName:Mode$ ReduceCost | Type$ Spell | "
            "ValidCard$ Card.sharesNameWith NamedCards | Activator$ You | Amount$ 2",
            text,
        )

    def test_registration_art_and_localization(self):
        self.assertIn("65 R 弃暗投明 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())
        self.assertEqual(
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
            "B46243F8393809E0209BDFAB88517ED6B12978EA6F1658B68B516A884431D56E",
        )
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((396, 289), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.02)
        self.assertIn(
            f"弃暗投明|弃暗投明|法术|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )


if __name__ == "__main__":
    unittest.main()
