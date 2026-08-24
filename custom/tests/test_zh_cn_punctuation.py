import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARDS = ROOT / "cards"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"
FORBIDDEN_QUOTES = ("“", "”")


def card_scripts():
    return sorted(CARDS.rglob("*.txt"))


def custom_names():
    names = set()
    for path in card_scripts():
        for line in path.read_text(encoding="utf-8").splitlines():
            if line.startswith("Name:"):
                names.add(line.removeprefix("Name:"))
                break
    return names


def localized_oracles():
    names = custom_names()
    for line in ZH_CN.read_text(encoding="utf-8").splitlines():
        fields = line.split("|", 3)
        if len(fields) == 4 and fields[0] in names:
            yield fields[0], fields[3]


class ZhCnPunctuationContractTest(unittest.TestCase):
    def test_custom_card_scripts_use_official_corner_brackets(self):
        offenders = []
        for path in card_scripts():
            text = path.read_text(encoding="utf-8")
            if any(mark in text for mark in FORBIDDEN_QUOTES):
                offenders.append(str(path.relative_to(ROOT)))
            self.assertEqual(text.count("「"), text.count("」"), path)
        self.assertEqual([], offenders)

    def test_custom_localized_oracles_use_official_corner_brackets(self):
        offenders = []
        for name, oracle in localized_oracles():
            if any(mark in oracle for mark in FORBIDDEN_QUOTES):
                offenders.append(name)
            self.assertEqual(oracle.count("「"), oracle.count("」"), name)
        self.assertEqual([], offenders)


if __name__ == "__main__":
    unittest.main()
