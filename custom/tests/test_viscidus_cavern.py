import unittest
import hashlib
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "colorless" / "维希度斯的窟穴.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-88a9bfc0-7f0b-4382-8d91-14336dee6cdb.png"
)
ART = ROOT / "cards" / "pictures" / "PH01" / "维希度斯的窟穴.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

SOURCE_ORACLE = (
    "Durability 2 (This permanent enters with two durability counters on it. "
    "When the last durability counter is removed from it, sacrifice it.)\\n"
    "Whenever you draw a card, put a mithril counter on CARDNAME.\\n"
    "{T}, Remove four mithril counters from CARDNAME: Spells in your hand perpetually cost {1} less to cast. "
    "Remove a durability counter from CARDNAME."
)
ZH_ORACLE = (
    "耐久2（此永久物进战场时上面有两个耐久指示物。当最后一个耐久指示物从其上移去时，将它牺牲。）\\n"
    "每当你抓一张牌时，在维希度斯的窟穴上放置一个秘银指示物。\\n"
    "{T}，从维希度斯的窟穴上移去四个秘银指示物：你手中的咒语牌永久地减少{1}来施放。"
    "从维希度斯的窟穴上移去一个耐久指示物。"
)


class ViscidusCavernContractTest(unittest.TestCase):
    def test_artifact_has_durability_two_and_gains_mithril_when_you_draw(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:维希度斯的窟穴", text)
        self.assertIn("ManaCost:B R", text)
        self.assertIn("Types:Artifact", text)
        self.assertNotIn("Types:Land", text)
        self.assertIn("K:Durability:2", text)
        self.assertIn(
            "T:Mode$ Drawn | ValidCard$ Card.YouCtrl | TriggerZones$ Battlefield | "
            "Execute$ TrigMithril | TriggerDescription$ Whenever you draw a card, "
            "put a mithril counter on CARDNAME.",
            text,
        )
        self.assertIn(
            "SVar:TrigMithril:DB$ PutCounter | Defined$ Self | "
            "CounterType$ MITHRIL | CounterNum$ 1",
            text,
        )
        self.assertNotIn("DEPLETION", text)

    def test_activated_ability_perpetually_reduces_hand_spells_then_removes_durability(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "A:AB$ AnimateAll | Cost$ T SubCounter<4/MITHRIL> | Zone$ Hand | "
            "Duration$ Perpetual | ValidCards$ Card.YouOwn+nonLand | "
            "staticAbilities$ ReduceCost | SubAbility$ DBRemoveDurability",
            text,
        )
        self.assertIn(
            "SVar:ReduceCost:Mode$ ReduceCost | ValidCard$ Card.Self | Type$ Spell | "
            "Amount$ 1 | EffectZone$ All | Description$ This spell costs {1} less to cast.",
            text,
        )
        self.assertIn(
            "SVar:DBRemoveDurability:DB$ RemoveCounter | Defined$ Self | "
            "CounterType$ DURABILITY | CounterNum$ 1",
            text,
        )
        self.assertIn(
            "SpellDescription$ Spells in your hand perpetually cost {1} less to cast. "
            "Remove a durability counter from CARDNAME.",
            text,
        )
        self.assertIn(f"Oracle:{SOURCE_ORACLE}", text)
        self.assertNotIn("AB$ Discard", text)
        self.assertNotIn("DB$ Draw", text)

    def test_registration_art_and_localization(self):
        self.assertIn("37 R 维希度斯的窟穴 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())
        self.assertEqual(
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
            "5C76B96F543C9144E28A2AEA8E417DF9E9F379EB60BB92D33465A5E378738B5C",
        )
        with Image.open(ART) as image:
            self.assertEqual(image.mode, "RGB")
            self.assertEqual(image.size, (490, 358))
            self.assertAlmostEqual(image.width / image.height, 1.37, places=2)
        self.assertIn(
            f"维希度斯的窟穴|维希度斯的窟穴|神器|{ZH_ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertEqual(SOURCE_ORACLE.count("\\n"), ZH_ORACLE.count("\\n"))


if __name__ == "__main__":
    unittest.main()
