import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "colorless" / "亚西克瑞非体质.txt"
PH01_EDITION = ROOT / "editions" / "Placeholder_Set.txt"
JF99_EDITION = ROOT / "editions" / "JiFei99_Set.txt"
ART = ROOT / "cards" / "pictures" / "JF99" / "亚西克瑞非体质.artcrop.jpg"
OLD_ART = ROOT / "cards" / "pictures" / "PH01" / "亚西克瑞非体质.artcrop.jpg"
BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-cb06b767-f050-407f-9938-bb8851b8004c.png"
)
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"
BACKUP_SHA256 = "d58a8d0ba989921704d5ce35f29038c9ed366bf6d59e83e3018007ee18f9ee61"


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


class YaxikeruifeiConstitutionContractTest(unittest.TestCase):
    def test_card_characteristics_and_life_gated_platinum_angel_effect(self):
        text = CARD.read_text(encoding="utf-8")
        self.assertIn("Name:亚西克瑞非体质", text)
        self.assertIn("ManaCost:0", text)
        self.assertIn("Types:Enchantment", text)
        self.assertEqual(2, text.count("CheckSVar$ Count$YourLifeTotal"))
        self.assertIn("R:Event$ GameLoss", text)
        self.assertIn("ValidPlayer$ You", text)
        self.assertIn("R:Event$ GameWin", text)
        self.assertIn("ValidPlayer$ Opponent", text)
        self.assertEqual(2, text.count("SVarCompare$ GE1"))

    def test_colorless_commander_library_entry_is_unique_and_once_per_game(self):
        text = CARD.read_text(encoding="utf-8")
        self.assertIn(
            "T:Mode$ NewGame | TriggerZones$ Hand,Library | "
            "ResolveBeforeFirstTurn$ True | Execute$ CreateLibraryAccess | Static$ True",
            text,
        )
        self.assertIn("ConditionPresent$ Card.IsCommander+YouOwn", text)
        self.assertIn("PresentZone$ Command", text)
        self.assertIn("Abilities$ EnterFromLibrary", text)
        self.assertIn("Unique$ True", text)
        self.assertIn("Cost$ 0", text)
        self.assertIn("ActivationZone$ Command", text)
        self.assertIn("CheckSVar$ Count$ColorsColorIdentity", text)
        self.assertIn("SVarCompare$ EQ0", text)
        self.assertIn("GameActivationLimit$ 1", text)
        self.assertIn("Origin$ Library", text)
        self.assertIn("Destination$ Battlefield", text)
        self.assertIn("ChangeType$ Card.named亚西克瑞非体质+YouOwn", text)
        self.assertIn("Reveal$ True", text)
        self.assertIn("Shuffle$ True", text)
        self.assertNotIn("SorcerySpeed$ True", text)

    def test_jifei99_edition_art_backup_and_chinese_text(self):
        edition = JF99_EDITION.read_text(encoding="utf-8")
        self.assertIn("Code=JF99", edition)
        self.assertIn("Name=鸡飞99", edition)
        self.assertIn("Type=Custom_Set", edition)
        self.assertIn("1 M 亚西克瑞非体质 @Custom", edition)
        self.assertNotIn(
            "亚西克瑞非体质",
            PH01_EDITION.read_text(encoding="utf-8"),
        )
        self.assertTrue(BACKUP.is_file(), BACKUP)
        self.assertEqual(BACKUP_SHA256, sha256(BACKUP))
        self.assertTrue(ART.is_file(), ART)
        self.assertFalse(OLD_ART.exists(), OLD_ART)
        with Image.open(ART) as image:
            self.assertEqual("RGB", image.mode)
            self.assertEqual("JPEG", image.format)
            self.assertAlmostEqual(1.37, image.width / image.height, places=2)
        translation = (
            "亚西克瑞非体质|亚西克瑞非体质|结界|"
            "只要你的总生命为1或更多，你便不会输掉游戏，且你的对手不会赢得游戏。\\n"
            "只要你的每位指挥官的颜色标识均为无色，你可以于你能施放瞬间的时机，从你的牌库中展示此牌并将它放进战场。"
            "若你如此作，洗牌。每盘游戏只能如此作一次。"
        )
        self.assertIn(translation, ZH_CN.read_text(encoding="utf-8").splitlines())


if __name__ == "__main__":
    unittest.main()
