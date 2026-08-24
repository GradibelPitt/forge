import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PACKAGING = ROOT / "packaging"
BUILD = PACKAGING / "build_windows_installer.ps1"
LAUNCHER = PACKAGING / "launch_forge_diy.ps1"
INNO = PACKAGING / "ForgeDIY.iss"


class WindowsInstallerPackageContractTest(unittest.TestCase):
    def test_packaging_entrypoints_exist(self):
        self.assertTrue(BUILD.is_file())
        self.assertTrue(LAUNCHER.is_file())
        self.assertTrue(INNO.is_file())

    def test_builder_stages_diy_content_and_never_official_art_cache(self):
        text = BUILD.read_text(encoding="utf-8")
        for required in (
            "custom\\cards",
            "custom\\tokens",
            "custom\\editions",
            "cardnames-zh-CN.txt",
            "manifest.sha256",
            "manifest-critical.sha256",
            "BUILD-ID.txt",
            "jlink",
            "ISCC.exe",
        ):
            self.assertIn(required, text)
        self.assertNotIn("Cache\\pics\\cards", text)
        self.assertNotIn("Cache/pics/cards", text)

    def test_launcher_checks_java_17_and_has_private_runtime_fallback(self):
        text = LAUNCHER.read_text(encoding="utf-8")
        self.assertIn("manifest-critical.sha256", text)
        self.assertIn("runtime\\bin\\javaw.exe", text)
        self.assertIn("forge.view.Main", text)
        self.assertRegex(text, r"(?i)java.*17")
        self.assertIn("[switch]$VerifyOnly", text)
        self.assertIn("[switch]$IgnoreSystemJava", text)

    def test_launcher_prepends_optional_module_overlays(self):
        text = LAUNCHER.read_text(encoding="utf-8")
        self.assertIn('Join-Path $AppRoot "overlays"', text)
        self.assertIn("[IO.Path]::PathSeparator", text)
        self.assertIn("$classPathEntries", text)
        self.assertIn("'-cp', $classPath", text)

    def test_inno_is_per_user_chinese_and_creates_shortcuts(self):
        text = INNO.read_text(encoding="utf-8")
        self.assertIn("PrivilegesRequired=lowest", text)
        self.assertIn("ChineseSimplified", text)
        self.assertIn("{userdesktop}", text)
        self.assertIn("{userprograms}", text)
        self.assertNotIn("{localappdata}\\Forge\\Cache\\pics\\cards", text)


if __name__ == "__main__":
    unittest.main()
