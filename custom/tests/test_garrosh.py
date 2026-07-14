import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "multicolor" / "天灾领主加尔鲁什.txt"
LEGACY_CARD = ROOT / "cards" / "multicolor" / "加尔鲁什.txt"
TOKEN = ROOT / "tokens" / "shadowmourne.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
CARD_ART = ROOT / "cards" / "pictures" / "PH01" / "天灾领主加尔鲁什.artcrop.jpg"
LEGACY_CARD_ART = ROOT / "cards" / "pictures" / "PH01" / "加尔鲁什.artcrop.jpg"
TOKEN_ART = ROOT / "tokens" / "pictures" / "shadowmourne.jpg"
CARD_ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-ce7a6eb3-06de-49dc-88b4-38729c7a5dd0.png"
)
TOKEN_ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-c13c17c0-981d-453a-94fa-ee7817d9e667.png"
)
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class GarroshContractTest(unittest.TestCase):
    def read_card(self):
        self.assertTrue(CARD.is_file(), CARD)
        return CARD.read_text(encoding="utf-8")

    def test_planeswalker_characteristics(self):
        text = self.read_card()

        self.assertIn("Name:天灾领主加尔鲁什", text)
        self.assertIn("ManaCost:2 B R", text)
        self.assertIn("Types:Legendary Planeswalker Garrosh", text)
        self.assertIn("Loyalty:5", text)

    def test_enters_and_creates_shadowmourne(self):
        text = self.read_card()

        self.assertIn(
            "T:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | "
            "ValidCard$ Card.Self | Execute$ TrigShadowmourne",
            text,
        )
        self.assertIn(
            "SVar:TrigShadowmourne:DB$ Token | TokenScript$ shadowmourne",
            text,
        )
        self.assertIn("当天灾领主加尔鲁什进场时", text)

    def test_plus_one_damages_each_creature(self):
        text = self.read_card()

        self.assertIn(
            "A:AB$ DamageAll | Cost$ AddCounter<1/LOYALTY> | Planeswalker$ True | "
            "ValidCards$ Creature | NumDmg$ 1",
            text,
        )
        self.assertIn("天灾领主加尔鲁什对每个生物各造成1点伤害。", text)

    def test_minus_one_creates_a_decayed_zombie(self):
        text = self.read_card()

        self.assertIn(
            "A:AB$ Token | Cost$ SubCounter<1/LOYALTY> | Planeswalker$ True | "
            "TokenScript$ b_2_2_zombie_decayed | TokenAmount$ 1 | TokenOwner$ You",
            text,
        )

    def test_shadowmourne_has_the_requested_equipment_rules(self):
        self.assertTrue(TOKEN.is_file(), TOKEN)
        text = TOKEN.read_text(encoding="utf-8")

        self.assertIn("Name:影之哀伤", text)
        self.assertIn("ManaCost:no cost", text)
        self.assertIn("Types:Legendary Artifact Equipment", text)
        self.assertIn("K:Equip:4", text)
        self.assertIn(
            "S:Mode$ Continuous | Affected$ Creature.EquippedBy | AddPower$ 4 | "
            "AddToughness$ 3 | AddKeyword$ Double Strike & Menace",
            text,
        )

    def test_registration_art_and_backups_exist(self):
        edition = EDITION.read_text(encoding="utf-8")

        self.assertIn("57 M 天灾领主加尔鲁什 @Custom", edition)
        card_lines = edition.split("[cards]", 1)[1].splitlines()
        collector_numbers = [
            line.split(maxsplit=1)[0] for line in card_lines if line.strip()
        ]
        self.assertEqual(len(collector_numbers), len(set(collector_numbers)))
        for path in (CARD_ART, TOKEN_ART, CARD_ART_BACKUP, TOKEN_ART_BACKUP):
            self.assertTrue(path.is_file(), path)
        for path in (LEGACY_CARD, LEGACY_CARD_ART):
            self.assertFalse(path.exists(), path)

    def test_art_is_landscape_rgb_jpeg(self):
        from PIL import Image

        self.assertTrue(CARD_ART.is_file(), CARD_ART)
        self.assertTrue(TOKEN_ART.is_file(), TOKEN_ART)
        with Image.open(CARD_ART) as image:
            self.assertEqual((816, 600), image.size)
            self.assertEqual("RGB", image.mode)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.02)
        with Image.open(TOKEN_ART) as image:
            self.assertEqual((400, 292), image.size)
            self.assertEqual("RGB", image.mode)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.02)

    def test_zh_cn_text_matches_the_requested_oracle(self):
        expected = (
            "天灾领主加尔鲁什|天灾领主加尔鲁什|传奇鹏洛客～加尔鲁什|"
            "当天灾领主加尔鲁什进场时，派出传奇衍生神器影之哀伤，其为无色武具，且具有"
            "「佩带此武具的生物得+4/+3且具有连击与威慑异能」与佩带{4}。\\n"
            "+1：天灾领主加尔鲁什对每个生物各造成1点伤害。\\n"
            "-1：派出一个2/2黑色，具败朽异能的灵俑衍生生物。"
        )
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())


if __name__ == "__main__":
    unittest.main()
