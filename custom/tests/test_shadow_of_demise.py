import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "multicolor" / "殒命暗影.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-2de77c75-572e-45e0-8e61-a1f7bf2aca8e.png"
)
ART_OUTPAINT = (
    ROOT
    / "tools"
    / "card-artwork"
    / "殒命暗影-imagegen-outpaint-20260806.png"
)
ART = ROOT / "cards" / "pictures" / "PH01" / "殒命暗影.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = (
    "殒命暗影是蓝黑双色。当你施放殒命暗影时，你改为施放你上一个施放且不为殒命暗影的瞬间或法术咒语"
    "（你仍然需要支付其法术力费用）。"
)


class ShadowOfDemiseContractTest(unittest.TestCase):
    def test_card_tracks_the_last_instant_or_sorcery_in_hand(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:殒命暗影", lines)
        self.assertIn("ManaCost:0", lines)
        self.assertIn("Colors:blue,black", lines)
        self.assertIn("Types:Instant", lines)
        self.assertIn(
            "Oracle:CARDNAME is blue and black. As you cast CARDNAME, instead cast "
            "the last instant or sorcery spell you cast that wasn't named CARDNAME. "
            "(You still pay its mana cost.)",
            lines,
        )

        cleanup = next(line for line in lines if line.startswith("A:SP$ Cleanup"))
        self.assertEqual(
            "A:SP$ Cleanup | SpellDescription$ Cast the last instant or sorcery spell "
            "you cast that wasn't named CARDNAME instead. You still pay its mana cost.",
            cleanup,
        )

        trigger = next(line for line in lines if line.startswith("T:Mode$ SpellCast"))
        self.assertIn(
            "ValidCard$ Instant.!printedNamed殒命暗影,"
            "Sorcery.!printedNamed殒命暗影",
            trigger,
        )
        self.assertIn("ValidActivatingPlayer$ You", trigger)
        self.assertIn("TriggerZones$ Hand,Library,Graveyard,Exile", trigger)
        self.assertIn("Execute$ BecomeLastSpell", trigger)
        self.assertIn("Static$ True", trigger)

        clone = next(line for line in lines if line.startswith("SVar:BecomeLastSpell:"))
        self.assertIn("DB$ Clone", clone)
        self.assertIn("Defined$ TriggeredCardLKICopy", clone)
        self.assertNotIn("CloneZone$", clone)
        self.assertIn("AddTypes$ Instant", clone)
        self.assertIn("GainThisAbility$ True", clone)

        self.assertFalse(any(line.startswith("S:Mode$ CantBeCast") for line in lines))
        self.assertNotIn("SVar:Uncopied:1", lines)

    def test_registration_localization_art_and_documentation(self):
        self.assertIn("97 M 殒命暗影 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertIn(
            f"殒命暗影|殒命暗影|瞬间|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 殒命暗影 | `{0}` 蓝黑双色瞬间 | "
            "`cards/multicolor/殒命暗影.txt` | 97 | "
            "在手牌、牌库、坟墓场或放逐区持续记录你上一个施放且不为殒命暗影的"
            "瞬间或法术；成为该咒语的复制品时额外保留瞬间类别，因此即使复制法术"
            "也能在瞬间时机施放，并照常支付其法术力费用。 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        self.assertTrue(ART_BACKUP.is_file())
        self.assertEqual(
            "F081AAA438CB4D0C0052E770DBCF5E354245D0FDDC9AAF18CCC99CD953DFB413",
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        self.assertTrue(ART_OUTPAINT.is_file())
        self.assertEqual(
            "51E0352300934B8558832D31FC4ADEBDB10AD458EEDD02417D784B519A3C393B",
            hashlib.sha256(ART_OUTPAINT.read_bytes()).hexdigest().upper(),
        )
        with Image.open(ART_OUTPAINT) as image:
            self.assertEqual("PNG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((1586, 992), image.size)

        self.assertTrue(ART.is_file())
        self.assertEqual(
            "19576192BACE06F4EE14A336BAC8701AEB929D3F567A2DCF00D667B53CA501EA",
            hashlib.sha256(ART.read_bytes()).hexdigest().upper(),
        )
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((1024, 748), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
