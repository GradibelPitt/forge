import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "colorless" / "gigantic_spright.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "Gigantic Spright.artcrop.jpg"
DEPLOYED_CARDS = Path.home() / "AppData" / "Roaming" / "Forge" / "custom" / "cards" / "colorless"


class GiganticSprightImplementationTest(unittest.TestCase):
    def test_card_tracks_kicker_materials_and_the_etb_bonus(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:Gigantic Spright", text)
        self.assertIn("ManaCost:U R", text)
        self.assertIn("Types:Legendary Artifact Creature Elemental Construct", text)
        self.assertIn("PT:2/2", text)
        self.assertIn("K:Kicker:tapXType<2/Creature.cmcEQ2/creatures with mana value 2>", text)
        self.assertIn("TriggerZones$ Stack", text)
        self.assertIn(
            "K:ETBReplacement:Other:DBMaterialBranch:Mandatory::Card.Self+kicked",
            text,
        )
        self.assertIn(
            "SVar:DBMaterialBranch:DB$ Branch | BranchConditionSVar$ SpecialMaterialCount | "
            "BranchConditionSVarCompare$ GE1 | TrueSubAbility$ DBPutBonusMaterial | "
            "FalseSubAbility$ DBPutBaseMaterial",
            text,
        )
        self.assertIn(
            "SVar:DBPutBaseMaterial:DB$ PutCounter | ETB$ True | Defined$ Self | "
            "CounterType$ COMPONENT | CounterNum$ 1",
            text,
        )
        self.assertIn(
            "SVar:DBPutBonusMaterial:DB$ PutCounter | ETB$ True | Defined$ Self | "
            "CounterType$ COMPONENT | CounterNum$ 2 | SubAbility$ DBGainHaste",
            text,
        )
        self.assertIn(
            "SVar:DBGainHaste:DB$ Animate | Defined$ Self | Keywords$ Haste | "
            "Duration$ EndOfTurn",
            text,
        )
        self.assertIn("Creature.Legendary+IsRemembered", text)
        self.assertNotIn(
            "T:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | "
            "ValidCard$ Card.Self+kicked",
            text,
        )

    def test_card_tutors_a_2_2_without_creating_a_turn_lock(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("A:AB$ ChangeZone | Cost$ T SubCounter<1/COMPONENT>", text)
        self.assertIn("ChangeType$ Creature.basePowerEQ2+baseToughnessEQ2", text)
        self.assertNotIn("DBCreateLock", text)
        self.assertNotIn("RepSprightLock", text)
        self.assertNotIn("!wasCast", text)

    def test_placeholder_is_registered_for_standard_art_crop(self):
        edition = EDITION.read_text(encoding="utf-8")
        self.assertIn("17 M Gigantic Spright @Custom", edition)
        self.assertTrue(ART.is_file())

    def test_deployment_has_only_the_forge_readable_script_name(self):
        self.assertFalse((DEPLOYED_CARDS / "Gigantic Spright.txt").exists())
        self.assertTrue((DEPLOYED_CARDS / "gigantic_spright.txt").is_file())


if __name__ == "__main__":
    unittest.main()
