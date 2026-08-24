import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "colorless" / "无界空宇.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-f592d38c-064e-4922-8c99-5636687dac1c.png"
)
ART = ROOT / "cards" / "pictures" / "PH01" / "无界空宇.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ZH_ORACLE = (
    "无界空宇不能被反击。\\n"
    "飞行，不灭，反一切保护。\\n"
    "每当一张牌进入手中、战场、坟墓场或放逐区时，无界空宇永久减少{1}来施放。\\n"
    "当你从手上施放无界空宇且它是你本回合中施放的第一个咒语时，"
    "放逐所有非地永久物。"
)


class BoundlessEmptyUniverseContractTest(unittest.TestCase):
    def test_characteristics_and_protection(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:无界空宇", text)
        self.assertIn("ManaCost:100", text)
        self.assertIn("Types:Legendary Enchantment Creature God", text)
        self.assertIn("PT:15/15", text)
        self.assertIn("K:Flying", text)
        self.assertIn("K:Indestructible", text)
        self.assertIn("K:Protection from everything", text)
        self.assertIn(
            "R:Event$ Counter | ValidCard$ Card.Self | ValidSA$ Spell | "
            "Layer$ CantHappen",
            text,
        )

    def test_zone_entries_permanently_reduce_the_cost(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "T:Mode$ NewGame | TriggerZones$ Hand,Library,Command | "
            "Execute$ CreateReductionEmblem | Static$ True",
            text,
        )
        self.assertIn(
            "SVar:CreateReductionEmblem:DB$ Effect | Name$ Emblem — 无界空宇 | "
            "Triggers$ TrackZoneEntry | StaticAbilities$ ReduceBoundlessCost | "
            "Duration$ Permanent | Unique$ True",
            text,
        )
        self.assertIn(
            "SVar:TrackZoneEntry:Mode$ ChangesZone | Origin$ Any | "
            "Destination$ Hand,Battlefield,Graveyard,Exile | "
            "ValidCard$ Card.!token | TriggerZones$ Command | "
            "Static$ True | Execute$ AddReductionCounter",
            text,
        )
        self.assertIn(
            "SVar:AddReductionCounter:DB$ PutCounter | Defined$ Self | "
            "CounterType$ STORAGE | CounterNum$ 1",
            text,
        )
        self.assertIn(
            "SVar:ReduceBoundlessCost:Mode$ ReduceCost | "
            "ValidCard$ Card.named无界空宇+YouOwn | Type$ Spell | Activator$ You | "
            "Amount$ Count$CardCounters.STORAGE/Plus.14",
            text,
        )
        self.assertNotIn("OpeningHandCount", text)

    def test_first_spell_from_hand_exiles_all_nonland_permanents(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "T:Mode$ SpellCast | ValidCard$ Card.Self+wasCastFromYourHandByYou | "
            "ValidActivatingPlayer$ You | TriggerZones$ Stack | "
            "CheckSVar$ FirstSpellThisTurn | SVarCompare$ EQ1 | "
            "Execute$ ExileNonlandPermanents",
            text,
        )
        self.assertIn(
            "SVar:FirstSpellThisTurn:Count$ThisTurnCast_Card.YouCtrl",
            text,
        )
        self.assertIn(
            "SVar:ExileNonlandPermanents:DB$ ChangeZoneAll | "
            "ChangeType$ Permanent.nonLand | Origin$ Battlefield | "
            "Destination$ Exile",
            text,
        )

    def test_registration_localization_and_art(self):
        self.assertIn(
            "87 M 无界空宇 @Custom",
            EDITION.read_text(encoding="utf-8"),
        )
        self.assertIn(
            f"无界空宇|无界空宇|传奇结界生物～神|{ZH_ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )

        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())
        self.assertEqual(
            "E64D969CEA45DE027EFAA6A392AABB025E6E2882ECAB6A7BFDF1A3022AA13D94",
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        with Image.open(ART_BACKUP) as image:
            self.assertEqual((450, 450), image.size)
            self.assertEqual("RGB", image.mode)
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((960, 700), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
