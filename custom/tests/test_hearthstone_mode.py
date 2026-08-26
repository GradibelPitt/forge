from pathlib import Path
import unittest


PROJECT_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = PROJECT_ROOT.parent


class HearthstoneModeContractTest(unittest.TestCase):
    def test_desktop_variant_replaces_archenemy_entries_at_requested_position(self):
        lobby = (
            REPO_ROOT
            / "forge-gui-desktop"
            / "src"
            / "main"
            / "java"
            / "forge"
            / "screens"
            / "home"
            / "VLobby.java"
        ).read_text(encoding="utf-8")

        self.assertIn("new VariantCheckBox(GameType.Hearthstone)", lobby)
        self.assertIn("vntTinyLeaders, vntPlanechase, vntHearthstone", lobby)
        self.assertNotIn("new VariantCheckBox(GameType.Archenemy)", lobby)
        self.assertNotIn("new VariantCheckBox(GameType.ArchenemyRumble)", lobby)

    def test_mode_owns_rules_instead_of_a_custom_artifact(self):
        self.assertFalse(
            (PROJECT_ROOT / "cards" / "colorless" / "炉石传说.txt").exists()
        )
        edition = (PROJECT_ROOT / "editions" / "Placeholder_Set.txt").read_text(
            encoding="utf-8"
        )
        self.assertNotIn("14 M 炉石传说 @Custom", edition.splitlines())

        game_type = (
            REPO_ROOT
            / "forge-game"
            / "src"
            / "main"
            / "java"
            / "forge"
            / "game"
            / "GameType.java"
        ).read_text(encoding="utf-8")
        self.assertIn(
            'Hearthstone         (DeckFormat.Hearthstone', game_type
        )

    def test_chinese_label_and_mode_description_exist(self):
        chinese = (
            REPO_ROOT / "forge-gui" / "res" / "languages" / "zh-CN.properties"
        ).read_text(encoding="utf-8")
        self.assertIn("lblHearthstone=炉石传说", chinese)
        self.assertIn("lblHearthstoneDesc=", chinese)

    def test_every_creature_is_attackable_instead_of_forcing_blocks(self):
        combat_util = (
            REPO_ROOT
            / "forge-game"
            / "src"
            / "main"
            / "java"
            / "forge"
            / "game"
            / "combat"
            / "CombatUtil.java"
        ).read_text(encoding="utf-8")
        mode = (
            REPO_ROOT
            / "forge-game"
            / "src"
            / "main"
            / "java"
            / "forge"
            / "game"
            / "HearthstoneMode.java"
        ).read_text(encoding="utf-8")
        can_be_attacked = (
            REPO_ROOT
            / "forge-game"
            / "src"
            / "main"
            / "java"
            / "forge"
            / "game"
            / "staticability"
            / "StaticAbilityCanBeAttacked.java"
        ).read_text(encoding="utf-8")
        phase_handler = (
            REPO_ROOT
            / "forge-game"
            / "src"
            / "main"
            / "java"
            / "forge"
            / "game"
            / "phase"
            / "PhaseHandler.java"
        ).read_text(encoding="utf-8")

        self.assertIn(
            "StaticAbilityCanBeAttacked::canBeAttacked", combat_util
        )
        self.assertIn(
            "HearthstoneMode.isActive(defender.getGame()) && defender.isCreature()",
            can_be_attacked,
        )
        self.assertIn(
            "attacker.getController().isOpponentOf(defender.getController())",
            can_be_attacked,
        )
        for retired_name in (
            "canHearthstoneForceBlock",
            "chooseForcedBlockers",
            "applyForcedBlockers",
            "chooseHearthstoneBlocker",
        ):
            self.assertNotIn(retired_name, combat_util)
            self.assertNotIn(retired_name, mode)
            self.assertNotIn(retired_name, phase_handler)

    def test_profile_installer_removes_the_retired_rule_card(self):
        installer = (PROJECT_ROOT / "tools" / "install_to_forge.ps1").read_text(
            encoding="utf-8-sig"
        )
        self.assertIn('"colorless\\炉石传说.txt"', installer)
        self.assertIn("pre-hearthstone-mode.bak", installer)
        self.assertIn("Remove-RetiredHearthstoneCardFromDecks", installer)


if __name__ == "__main__":
    unittest.main()
