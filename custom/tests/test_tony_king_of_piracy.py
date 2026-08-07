import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "blue" / "盗版之王托尼.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-4c41a21d-b624-4e89-a9b2-d4123432a87f.png"
)
ART = ROOT / "cards" / "pictures" / "PH01" / "盗版之王托尼.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

EN_ORACLE = (
    "Players may spend mana as though it were mana of any color to cast spells.\\n"
    "If you would draw a card, instead choose an opponent and draw a card from that "
    "player's library.\\n"
    "If an opponent would draw a card, instead that player draws a card from your "
    "library.\\n"
    "When CARDNAME enters, if you control no untapped lands, draw a card."
)
ZH_ORACLE = (
    "所有牌手均可以将法术力视同任意颜色的法术力来施放咒语。\\n"
    "如果你将抓一张牌，改为选择一位对手并从该牌手的牌库顶抓一张牌。\\n"
    "如果任一对手将抓一张牌，改为该牌手从你的牌库顶抓一张牌。\\n"
    "当盗版之王托尼进战场时，若你未操控未横置的地，抓一张牌。"
)
SOURCE_ART_SHA256 = (
    "CAEC8C8E68D7C5F7B31DB4FE4E27954CE6C33FF9176FBA2AF2C50C4855920D5B"
)


class TonyKingOfPiracyContractTest(unittest.TestCase):
    def test_characteristics_and_all_player_mana_conversion(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:盗版之王托尼", lines)
        self.assertIn("ManaCost:3 U U", lines)
        self.assertIn("Types:Legendary Creature Troll", lines)
        self.assertIn("PT:4/4", lines)

        mana = next(line for line in lines if line.startswith("S:Mode$ ManaConvert"))
        self.assertIn("ValidSA$ Spell", mana)
        self.assertIn("ManaConversion$ AnyType->AnyColor", mana)
        self.assertNotIn("ValidPlayer$ You", mana)
        self.assertIn(
            "Description$ Players may spend mana as though it were mana of any color "
            "to cast spells.",
            mana,
        )

    def test_both_draw_replacements_use_the_correct_library(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()
        replacements = [line for line in lines if line.startswith("R:Event$ Draw")]
        self.assertEqual(2, len(replacements))

        your_draw = next(line for line in replacements if "ValidPlayer$ You" in line)
        self.assertIn("ActiveZones$ Battlefield", your_draw)
        self.assertIn("ReplaceWith$ ChooseOpponentLibrary", your_draw)

        choose = next(
            line for line in lines if line.startswith("SVar:ChooseOpponentLibrary:")
        )
        self.assertIn("DB$ ChoosePlayer", choose)
        self.assertIn("Defined$ ReplacedPlayer", choose)
        self.assertIn("Choices$ Player.OpponentOf ReplacedPlayer", choose)
        self.assertIn("SubAbility$ DrawFromChosenLibrary", choose)

        chosen_draw = next(
            line for line in lines if line.startswith("SVar:DrawFromChosenLibrary:")
        )
        self.assertIn("DB$ Draw", chosen_draw)
        self.assertIn("Defined$ ReplacedPlayer", chosen_draw)
        self.assertIn("NumCards$ 1", chosen_draw)
        self.assertIn("FromLibrary$ Player.Chosen", chosen_draw)

        opponent_draw = next(
            line for line in replacements if "ValidPlayer$ Opponent" in line
        )
        self.assertIn("ActiveZones$ Battlefield", opponent_draw)
        self.assertIn("ReplaceWith$ DrawFromTonyLibrary", opponent_draw)

        from_tony = next(
            line for line in lines if line.startswith("SVar:DrawFromTonyLibrary:")
        )
        self.assertIn("DB$ Draw", from_tony)
        self.assertIn("Defined$ ReplacedPlayer", from_tony)
        self.assertIn("NumCards$ 1", from_tony)
        self.assertIn("FromLibrary$ You", from_tony)

    def test_etb_draw_requires_no_untapped_lands(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()
        trigger = next(line for line in lines if line.startswith("T:Mode$ ChangesZone"))

        self.assertIn("Origin$ Any", trigger)
        self.assertIn("Destination$ Battlefield", trigger)
        self.assertIn("ValidCard$ Card.Self", trigger)
        self.assertIn("TriggerZones$ Battlefield", trigger)
        self.assertIn("IsPresent$ Land.untapped+YouCtrl", trigger)
        self.assertIn("PresentCompare$ EQ0", trigger)
        self.assertIn("Execute$ TrigDraw", trigger)
        self.assertIn(
            "TriggerDescription$ When CARDNAME enters, if you control no untapped "
            "lands, draw a card.",
            trigger,
        )

        draw = next(line for line in lines if line.startswith("SVar:TrigDraw:"))
        self.assertIn("DB$ Draw", draw)
        self.assertIn("Defined$ You", draw)
        self.assertIn("NumCards$ 1", draw)
        self.assertIn(f"Oracle:{EN_ORACLE}", lines)

    def test_registration_localization_art_and_documentation(self):
        self.assertIn(
            "111 M 盗版之王托尼 @Custom",
            EDITION.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            f"盗版之王托尼|盗版之王托尼|传奇生物～巨魔|{ZH_ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 盗版之王托尼 | `{3}{U}{U}`，4/4 传奇生物～巨魔 | "
            "`cards/blue/盗版之王托尼.txt` | 111 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        self.assertTrue(ART_BACKUP.is_file())
        self.assertEqual(
            SOURCE_ART_SHA256,
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        with Image.open(ART_BACKUP) as image:
            self.assertEqual("PNG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((1024, 1364), image.size)

        self.assertTrue(ART.is_file())
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((1024, 747), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
