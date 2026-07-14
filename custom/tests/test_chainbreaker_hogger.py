from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "red" / "chainbreaker_hogger.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"


class ChainbreakerHoggerContractTest(unittest.TestCase):
    def test_card_has_superreach_and_the_requested_stats(self):
        script = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:破链灾星霍格", script)
        self.assertIn("ManaCost:4 R R R R", script)
        self.assertIn("Types:Legendary Creature Gnoll", script)
        self.assertIn("PT:10/10", script)
        self.assertIn("K:Superreach", script)
        self.assertIn("CanBlockAny$ True", script)

    def test_new_game_effect_copies_legendary_permanents_from_the_starting_deck(self):
        script = CARD.read_text(encoding="utf-8")
        lines = script.splitlines()
        trigger_line = next(line for line in lines if line.startswith("T:Mode$ NewGame |"))
        copy_starting_deck_line = next(
            (line for line in lines if line.startswith("SVar:CopyStartingDeck:")),
            None,
        )

        self.assertIn("TriggerZones$ Hand,Library", script)
        self.assertIn("Execute$ CopyStartingDeck", trigger_line)
        self.assertIsNotNone(copy_starting_deck_line)
        self.assertIn(
            "DefinedName$ StartingDeckLegendaryPermanents | "
            "ExcludeName$ 破链灾星霍格 | Zone$ Library",
            copy_starting_deck_line,
        )
        self.assertIn("SubAbility$ GrantLegendEmblem", copy_starting_deck_line)
        self.assertNotIn("DefinedName$ ValidStartingDeck", script)
        self.assertNotIn("DefinedName$ ValidHand", script)
        self.assertNotIn("DefinedName$ ValidLibrary", script)

    def test_new_game_chain_creates_permanent_player_scoped_legend_emblem(self):
        script = CARD.read_text(encoding="utf-8")

        self.assertNotIn("S:Mode$ IgnoreLegendRule", script)
        self.assertIn("SubAbility$ GrantLegendEmblem", script)
        self.assertIn(
            "SVar:GrantLegendEmblem:DB$ Effect | Name$ Emblem — 破链灾星霍格 | "
            "StaticAbilities$ IgnoreLegendRule | Duration$ Permanent | Unique$ True",
            script,
        )
        self.assertIn(
            "SVar:IgnoreLegendRule:Mode$ IgnoreLegendRule | ValidCard$ Permanent.YouCtrl | "
            "Description$ The \"legend rule\" doesn't apply to permanents you control.",
            script,
        )

    def test_new_game_wording_excludes_hogger_itself(self):
        script = CARD.read_text(encoding="utf-8")
        lines = script.splitlines()
        trigger_line = next(line for line in lines if line.startswith("T:Mode$ NewGame |"))
        oracle_line = next(line for line in lines if line.startswith("Oracle:"))

        self.assertIn("duplicate each other legendary permanent card", script)
        self.assertIn("duplicate each other legendary permanent card", trigger_line)
        self.assertIn("duplicate each other legendary permanent card", oracle_line)

    def test_card_is_listed_in_the_custom_edition(self):
        self.assertIn("8 M 破链灾星霍格", EDITION.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
