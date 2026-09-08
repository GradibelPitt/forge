from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from tools.card_translations import load_translations


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "translations" / "cardnames-zh-CN-custom.txt"
SYNC = ROOT / "tools" / "sync_translations.ps1"
POWERSHELL = shutil.which("pwsh") or shutil.which("powershell.exe")


class TranslationSourceTest(unittest.TestCase):
    def test_authoring_tools_read_base_then_overlay_with_engine_fallbacks(self):
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp) / "base.txt"
            overlay = Path(temp) / "custom.txt"
            base.write_text("Old|旧卡|类别|旧规则\nSame|原名|类别|原规则\n", encoding="utf-8")
            overlay.write_text("Same|新名|新类别|\nNew|新卡|法术|一VERT二\\n三\n"
                               "Variant$C|变体|法术|//Level_2//\\n四\n", encoding="utf-8")
            data = load_translations(base, overlay)
            self.assertEqual("旧规则", data["Old"]["Oracle"])
            self.assertEqual({"Name": "新名", "Types": "新类别", "Oracle": "原规则"}, data["Same"])
            self.assertEqual("一|二\n三", data["New"]["Oracle"])
            self.assertEqual("变体", data["Variant"]["Name"])
            self.assertEqual("四", data["Variant $C"]["Oracle"])

    def test_overlay_records_are_complete_and_unique(self):
        seen = set()
        for line in SOURCE.read_text(encoding="utf-8-sig").splitlines():
            if not line.strip() or line.startswith("#"):
                continue
            fields = line.split("|")
            self.assertEqual(4, len(fields), line)
            self.assertTrue(all(field.strip() for field in fields[:3]), line)
            self.assertNotIn(fields[0], seen, line)
            seen.add(fields[0])


@unittest.skipUnless(POWERSHELL, "PowerShell required for sync integration tests")
class TranslationSyncTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.languages = self.root / "languages"
        self.languages.mkdir()
        self.base = self.languages / "cardnames-zh-CN.txt"
        self.base.write_bytes("Old|旧卡|法术|保留\\n规则\r\n".encode("utf-8"))
        self.original = self.base.read_bytes()
        self.source = self.root / "custom.txt"
        self.source.write_bytes("New|新卡|法术|第一段\\n第二段VERT第三段\n".encode("utf-8"))
        self.overlay = self.languages / SOURCE.name

    def run_sync(self, *arguments):
        result = subprocess.run(
            [POWERSHELL, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(SYNC),
             "-TranslationSource", str(self.source), "-LanguagesDirectory", str(self.languages),
             *arguments], capture_output=True,
        )
        self.assertEqual(self.original, self.base.read_bytes(), "Base must remain byte-identical")
        return result

    def test_copy_and_repeat_are_byte_exact(self):
        for _ in range(2):
            result = self.run_sync()
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(self.source.read_bytes(), self.overlay.read_bytes())

    def test_check_only_does_not_write(self):
        self.assertEqual(0, self.run_sync("-CheckOnly").returncode)
        self.assertFalse(self.overlay.exists())

    def test_invalid_or_duplicate_rows_preserve_deployed_overlay(self):
        self.overlay.write_bytes(b"previous deployment\n")
        for text in ("Missing|fields\n", "Same|A|T|O\nSame|B|T|O\n",
                     "Variant$C|A|T|O\nVariant $C|B|T|O\n"):
            self.source.write_text(text, encoding="utf-8")
            self.assertNotEqual(0, self.run_sync().returncode)
            self.assertEqual(b"previous deployment\n", self.overlay.read_bytes())

    def test_empty_overlay_replaces_old_overrides(self):
        self.overlay.write_bytes(b"obsolete override\n")
        self.source.write_text("# No overrides\n", encoding="utf-8")
        self.assertEqual(0, self.run_sync().returncode)
        self.assertEqual(self.source.read_bytes(), self.overlay.read_bytes())

    def test_uninstall_only_removes_overlay(self):
        self.overlay.write_bytes(b"override\n")
        self.assertEqual(0, self.run_sync("-Uninstall").returncode)
        self.assertFalse(self.overlay.exists())

    def test_translation_only_install_avoids_profile_work(self):
        result = subprocess.run(
            [POWERSHELL, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
             str(ROOT / "tools" / "install_to_forge.ps1"), "-TranslationsOnly",
             "-LanguagesDirectory", str(self.languages)], capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(SOURCE.read_bytes(), self.overlay.read_bytes())
        self.assertEqual(self.original, self.base.read_bytes())


if __name__ == "__main__":
    unittest.main()
