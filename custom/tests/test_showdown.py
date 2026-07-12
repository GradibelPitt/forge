import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "multicolor" / "决战.txt"
TOKEN = ROOT / "tokens" / "c_3_3_outlaw_haste.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
CARD_ART = ROOT / "cards" / "pictures" / "PH01" / "决战.artcrop.jpg"
TOKEN_ART = ROOT / "tokens" / "pictures" / "c_3_3_outlaw_haste.jpg"
TOKEN_ART_SET_FALLBACK = ROOT / "tokens" / "pictures" / "c_3_3_outlaw_haste_PH01.jpg"
CARD_ART_BACKUP = ROOT / "tools" / "card-artwork" / "Showdown!.jpg"
TOKEN_ART_BACKUP = ROOT / "tools" / "card-artwork" / "Outlaw Token.jpg"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"
INSTALLER = ROOT / "tools" / "install_to_forge.ps1"


class ShowdownContractTest(unittest.TestCase):
    def test_card_creates_three_outlaws_for_each_player(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:决战", text)
        self.assertIn("ManaCost:W R", text)
        self.assertIn("Types:Instant", text)
        self.assertIn(
            "A:SP$ RepeatEach | RepeatPlayers$ Player | RepeatSubAbility$ DBToken | "
            "SpellDescription$ Each player creates three 3/3 colorless creature tokens named Outlaw with haste.",
            text,
        )
        self.assertIn(
            "SVar:DBToken:DB$ Token | TokenScript$ c_3_3_outlaw_haste | "
            "TokenAmount$ 3 | TokenOwner$ Player.IsRemembered",
            text,
        )
        self.assertIn(
            "Oracle:每位牌手各派出三个名为“歹徒”的3/3无色生物衍生物，且它们具有敏捷异能。",
            text,
        )

    def test_outlaw_token_has_the_requested_characteristics(self):
        text = TOKEN.read_text(encoding="utf-8")

        self.assertIn("Name:歹徒", text)
        self.assertIn("ManaCost:no cost", text)
        self.assertIn("Colors:colorless", text)
        self.assertIn("Types:Creature", text)
        self.assertIn("PT:3/3", text)
        self.assertIn("K:Haste", text)
        self.assertIn("Oracle:敏捷", text)

    def test_card_and_art_are_registered(self):
        edition = EDITION.read_text(encoding="utf-8")

        self.assertIn("21 R 决战 @Custom", edition)
        for path in (
            CARD_ART,
            TOKEN_ART,
            TOKEN_ART_SET_FALLBACK,
            CARD_ART_BACKUP,
            TOKEN_ART_BACKUP,
        ):
            self.assertTrue(path.is_file(), path)

    def test_art_uses_landscape_card_frame_ratio(self):
        from PIL import Image

        with Image.open(CARD_ART) as image:
            self.assertEqual((1024, 747), image.size)
            self.assertEqual("RGB", image.mode)
        with Image.open(TOKEN_ART) as image:
            self.assertEqual((488, 680), image.size)
            self.assertEqual("RGB", image.mode)

    def test_zh_cn_uses_standard_wording(self):
        expected = (
            "决战|决战！|瞬间|"
            "每位牌手各派出三个名为“歹徒”的3/3无色生物衍生物，且它们具有敏捷异能。"
        )
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())

    def test_installer_syncs_custom_token_pictures(self):
        text = INSTALLER.read_text(encoding="utf-8")

        self.assertIn('$WorkspaceTokenPictures = Join-Path $WorkspaceTokens "pictures"', text)
        self.assertIn('$ForgeTokenPictures = Join-Path $env:LOCALAPPDATA "Forge\\Cache\\pics\\tokens"', text)
        self.assertIn("Synced Token Picture:", text)


if __name__ == "__main__":
    unittest.main()
