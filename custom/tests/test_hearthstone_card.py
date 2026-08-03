from pathlib import Path
import unittest


PROJECT_ROOT = Path(__file__).resolve().parents[1]
CARD_PATH = PROJECT_ROOT / "cards" / "colorless" / "炉石传说.txt"
EDITION_PATH = PROJECT_ROOT / "editions" / "Placeholder_Set.txt"
ART_PATH = PROJECT_ROOT / "cards" / "pictures" / "PH01" / "炉石传说.artcrop.jpg"
ART_BACKUP_PATH = PROJECT_ROOT / "tools" / "card-artwork" / "炉石传说_original.png"


class HearthstoneCardContractTest(unittest.TestCase):
    def read_card(self) -> str:
        return CARD_PATH.read_text(encoding="utf-8")

    def test_identity_and_deck_constraints(self):
        card = self.read_card()

        self.assertIn("Name:炉石传说", card)
        self.assertIn("ManaCost:0", card)
        self.assertIn("Types:Artifact", card)
        self.assertIn("K:GameRule", card)
        self.assertIn("K:DeckMinimum:31", card)
        self.assertIn("K:DeckLimit:1:Your deck can have no more than one card named CARDNAME.", card)
        self.assertIn("Oracle:将对战规则改变为炉石传说！\\n对战开始时：展示并放逐炉石传说。双方最大手牌数量为10。牌手从空牌库抓牌时不会输掉游戏，改为受到疲劳伤害；每位牌手的疲劳伤害从1开始并在每次空抽后递增。", card)

    def test_start_of_game_effects_are_ordered_and_global(self):
        card = self.read_card()

        self.assertIn(
            "T:Mode$ NewGame | TriggerZones$ Exile | ResolveBeforeFirstTurn$ True | Execute$ TrigReveal",
            card,
        )
        self.assertIn("SVar:TrigReveal:DB$ Reveal | RevealDefined$ Self | SubAbility$ TrigSetLife", card)
        self.assertIn("SVar:TrigSetLife:DB$ SetLife | Defined$ Player | LifeAmount$ 30 | SubAbility$ TrigCreateEmblems", card)
        self.assertIn("SVar:TrigCreateEmblems:DB$ RepeatEach | RepeatPlayers$ Player | RepeatSubAbility$ TrigCreateEmblem", card)
        self.assertIn("SVar:TrigCreateEmblem:DB$ Effect | Name$ Emblem — 炉石传说 | EffectOwner$ Player.IsRemembered | Triggers$ HearthstoneUpkeep | StaticAbilities$ HearthstoneRules | Duration$ Permanent | Unique$ True", card)
        self.assertNotIn("TrigMoveToLibrary", card)
        self.assertNotIn("TrigExile", card)

    def test_emblem_is_unique_for_each_owner_when_multiple_startup_cards_resolve(self):
        card = self.read_card()

        emblem_line = next(
            line for line in card.splitlines()
            if line.startswith("SVar:TrigCreateEmblem:")
        )
        self.assertIn("EffectOwner$ Player.IsRemembered", emblem_line)
        self.assertIn("Unique$ True", emblem_line)

    def test_emblem_conjures_a_chosen_basic_land_each_owner_upkeep(self):
        card = self.read_card()

        self.assertIn("SVar:HearthstoneUpkeep:Mode$ Phase | Phase$ Upkeep | ValidPlayer$ You | TriggerZones$ Command | Execute$ HearthstoneChooseBasic", card)
        self.assertIn(
            "SVar:HearthstoneChooseBasic:DB$ MakeCard | Conjure$ True | Spellbook$ Plains,Island,Swamp,Mountain,Forest | Zone$ Hand",
            card,
        )
        self.assertNotIn("DB$ PutCounter", card)
        self.assertNotIn("DB$ Mana", card)
        self.assertNotIn("PersistentMana$", card)
        self.assertNotIn("Count$CardCounters.MANA", card)

    def test_emblem_sets_global_hand_size_and_enables_empty_library_fatigue(self):
        card = self.read_card()

        self.assertIn("StaticAbilities$ HearthstoneRules", card)
        self.assertIn(
            "SVar:HearthstoneRules:Mode$ Continuous | EffectZone$ Command | Affected$ Player | SetMaxHandSize$ 10 | AddKeyword$ FatigueOnEmptyDraw",
            card,
        )

    def test_has_exact_placeholder_set_entry(self):
        edition = EDITION_PATH.read_text(encoding="utf-8")

        self.assertIn("14 M 炉石传说 @Custom", edition.splitlines())

    def test_has_standard_crop_art(self):
        from PIL import Image

        self.assertTrue(ART_BACKUP_PATH.is_file())
        self.assertTrue(ART_PATH.is_file())
        with Image.open(ART_PATH) as image:
            self.assertEqual("RGB", image.mode)
            self.assertGreater(image.width, image.height)
            self.assertAlmostEqual(image.width / image.height, 1.37, delta=0.02)


if __name__ == "__main__":
    unittest.main()
