from pathlib import Path
import unittest


CUSTOM_ROOT = Path(__file__).resolve().parents[1]
CARDS_ROOT = CUSTOM_ROOT / "cards"
LOCALIZATION_FILES = (
    CUSTOM_ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt",
    CUSTOM_ROOT / "translations" / "cardnames-zh-CN-custom.txt",
)


def custom_card_names():
    names = set()
    for script in CARDS_ROOT.rglob("*.txt"):
        names.add(script.stem)
        for line in script.read_text(encoding="utf-8-sig").splitlines():
            if line.startswith("Name:"):
                names.add(line.removeprefix("Name:").strip())
    return names


class ConjureZhCnTerminologyTest(unittest.TestCase):
    def test_card_scripts_use_huan_bian_instead_of_hua_sheng(self):
        stale = []
        updated = []
        for script in CARDS_ROOT.rglob("*.txt"):
            text = script.read_text(encoding="utf-8-sig")
            if "化生" in text:
                stale.append(script.relative_to(CUSTOM_ROOT).as_posix())
            if "幻变" in text:
                updated.append(script.relative_to(CUSTOM_ROOT).as_posix())

        self.assertEqual([], stale, f"card scripts still use the old Conjure translation: {stale}")
        self.assertTrue(updated, "no card script uses the corrected Conjure translation")

    def test_diy_zh_cn_rows_use_huan_bian_instead_of_hua_sheng(self):
        names = custom_card_names()
        stale = []
        updated = []
        for localization in LOCALIZATION_FILES:
            for line_number, line in enumerate(
                localization.read_text(encoding="utf-8-sig").splitlines(), start=1
            ):
                if not line or line.startswith("#"):
                    continue
                key = line.split("|", 1)[0]
                if key not in names:
                    continue
                location = f"{localization.name}:{line_number}:{key}"
                if "化生" in line:
                    stale.append(location)
                if "幻变" in line:
                    updated.append(location)

        self.assertEqual([], stale, f"DIY zh-CN rows still use the old Conjure translation: {stale}")
        self.assertTrue(updated, "no DIY zh-CN row uses the corrected Conjure translation")


if __name__ == "__main__":
    unittest.main()
