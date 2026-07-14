import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARDS = ROOT / "cards"
GAMEPLAY_EDITION = ROOT / "editions" / "Placeholder_Set.txt"
TEST_EDITION = ROOT / "editions" / "Test_Set.txt"


def card_names_containing_test():
    names = []
    for script in CARDS.rglob("*.txt"):
        first_line = script.read_text(encoding="utf-8").splitlines()[0]
        if first_line.startswith("Name:"):
            name = first_line.removeprefix("Name:")
            if "test" in name.casefold():
                names.append(name)
    return sorted(names, key=str.casefold)


def edition_card_names(path):
    lines = path.read_text(encoding="utf-8").splitlines()
    cards_index = lines.index("[cards]")
    names = []
    for line in lines[cards_index + 1 :]:
        if not line.strip():
            continue
        fields = line.split(maxsplit=3)
        names.append(fields[2] if len(fields) == 3 else fields[2] + " " + fields[3].removesuffix(" @Custom"))
    return names


class TestCardSetContract(unittest.TestCase):
    def test_gameplay_set_uses_hearthstone_display_name(self):
        metadata = GAMEPLAY_EDITION.read_text(encoding="utf-8")
        self.assertIn("Code=PH01", metadata)
        self.assertIn("Name=炉石传说", metadata)
        self.assertNotIn("Name=Placeholder Set", metadata)

    def test_names_containing_test_live_only_in_the_test_set(self):
        expected = card_names_containing_test()
        self.assertEqual(9, len(expected))
        self.assertTrue(TEST_EDITION.exists(), "Test_Set.txt must define the dedicated TEST set")

        metadata = TEST_EDITION.read_text(encoding="utf-8")
        self.assertIn("Code=TEST", metadata)
        self.assertIn("Name=Test Cards", metadata)

        test_set_names = edition_card_names(TEST_EDITION)
        gameplay_names = edition_card_names(GAMEPLAY_EDITION)
        self.assertCountEqual(expected, test_set_names)
        self.assertTrue(set(expected).isdisjoint(gameplay_names))


if __name__ == "__main__":
    unittest.main()
