import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARDS = ROOT / "cards"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

EXCLUDED_NAMES = {
    "阿曼苏尔",
    "灭世者萨格拉斯",
    "兵主",
    "生命的缚誓者艾欧娜尔",
    "维和者阿米图斯",
    "诺甘农",
    "雷霆之神高戈奈斯",
    "卡兹格罗斯",
    "翠绿之星阿古斯",
    "复仇者阿格拉玛",
}


def gameplay_card_names():
    names = []
    for path in CARDS.rglob("*.txt"):
        if path.name.lower().startswith("test") or path.name == "forge_test_goblin.txt":
            continue
        for line in path.read_text(encoding="utf-8").splitlines():
            if line.startswith("Name:"):
                name = line.removeprefix("Name:")
                if name not in EXCLUDED_NAMES:
                    names.append(name)
                break
    return sorted(names)


def custom_localizations():
    entries = {}
    for line in ZH_CN.read_text(encoding="utf-8").splitlines():
        parts = line.split("|", 3)
        if len(parts) == 4:
            entries[parts[0]] = parts[3]
    return entries


class DiyWordingConsistencyTest(unittest.TestCase):
    def test_every_in_scope_card_has_reviewable_chinese_oracle(self):
        names = gameplay_card_names()

        localized = custom_localizations()
        self.assertEqual([], [name for name in names if name not in localized])
        self.assertEqual([], [name for name in names if not localized[name]])

    def test_chinese_oracle_uses_consistent_magic_terms(self):
        localized = custom_localizations()
        forbidden = {
            "牌堆": re.compile("牌堆"),
            "启动（应为起动）": re.compile("启动"),
            "由你控制（应为由你操控）": re.compile(r"由你控制|你控制的|控制下"),
            "操纵（应为操控）": re.compile("操纵"),
            "阶段（应为步骤）": re.compile(r"重置阶段|结束阶段"),
            "法术或瞬间（应为瞬间或法术）": re.compile("法术或瞬间"),
            "放入战场（应为放进战场）": re.compile("放入战场"),
            "抽牌（应为抓牌）": re.compile(r"抽[一二三四五六七八九十X若]"),
        }

        failures = []
        for name in gameplay_card_names():
            oracle = localized[name]
            for label, pattern in forbidden.items():
                if pattern.search(oracle):
                    failures.append(f"{name}: {label}")
        self.assertEqual([], failures)

    def test_fixed_object_counts_use_chinese_numerals(self):
        localized = custom_localizations()
        fixed_count = re.compile(r"(?<![0-9+/])(?:[1-9]|1[0-9]|20)(?:张|个|位|项|枚|种|次)")

        failures = [
            name
            for name in gameplay_card_names()
            if fixed_count.search(localized[name])
        ]
        self.assertEqual([], failures)


if __name__ == "__main__":
    unittest.main()
