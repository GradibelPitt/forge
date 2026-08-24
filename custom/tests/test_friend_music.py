import hashlib
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MUSIC_ROOT = ROOT / "music" / "Pull Up a Chair"


class FriendMusicContractTest(unittest.TestCase):
    def test_verified_menu_and_match_tracks_are_source_controlled(self):
        expected = {
            MUSIC_ROOT / "menus" / "Pull Up a Chair.mp3":
                "5761979E0E71C1C5AC2CFAE664DCA0069FB39DFDB900834B7B61A2BA73D1CAFB",
            MUSIC_ROOT / "match" / "Bad Down to the Molten Core.mp3":
                "378F65639E84BF246FDE8220C5C65D502288CC30B37A242398026165A2E6EDB6",
        }
        for path, expected_hash in expected.items():
            self.assertTrue(path.is_file(), path)
            self.assertEqual(expected_hash, hashlib.sha256(path.read_bytes()).hexdigest().upper())

    def test_local_installer_syncs_the_music_tree(self):
        installer = (ROOT / "tools" / "install_to_forge.ps1").read_text(encoding="utf-8-sig")
        self.assertIn('$WorkspaceMusic = Join-Path $WorkspaceRoot "music"', installer)
        self.assertIn('$ForgeMusic = Join-Path $ForgeCustomDir "music"', installer)
        self.assertIn('Get-ChildItem -Path $WorkspaceMusic -Recurse -File', installer)
        self.assertIn('Write-Host "Synced Music: $relPath"', installer)

    def test_local_installer_reapplies_managed_ui_and_music_preferences(self):
        installer = (ROOT / "tools" / "install_to_forge.ps1").read_text(encoding="utf-8-sig")
        self.assertIn('$ForgePreferences = Join-Path $AppData "Forge\\preferences\\forge.preferences"', installer)
        self.assertIn('function Set-ManagedPreferences', installer)
        for required in (
            "UI_SKIN = 'Warmwood'",
            "UI_ENABLE_MUSIC = 'true'",
            "UI_VOL_MUSIC = '100'",
            "UI_CURRENT_MUSIC_SET = 'Pull Up a Chair'",
        ):
            self.assertIn(required, installer)
        self.assertIn('Set-ManagedPreferences $ForgePreferences', installer)


if __name__ == "__main__":
    unittest.main()
