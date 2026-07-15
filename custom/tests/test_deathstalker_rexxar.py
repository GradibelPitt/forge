import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "multicolor" / "死亡猎手雷克萨.txt"
TOKEN = ROOT / "tokens" / "bg_1_1_zombie_beast_mutate.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
CARD_ART = ROOT / "cards" / "pictures" / "PH01" / "死亡猎手雷克萨.artcrop.jpg"
TOKEN_ART = ROOT / "tokens" / "pictures" / "bg_1_1_zombie_beast_mutate.jpg"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-d33f4f1a-59f2-4cff-82c3-d2001279aa2e.png"
)
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class DeathstalkerRexxarContractTest(unittest.TestCase):
    def read_card(self):
        self.assertTrue(CARD.is_file(), CARD)
        return CARD.read_text(encoding="utf-8")

    def test_planeswalker_characteristics_and_mutate_reduction(self):
        text = self.read_card()

        self.assertIn("Name:死亡猎手雷克萨", text)
        self.assertIn("ManaCost:3 B G", text)
        self.assertIn("Types:Legendary Planeswalker Rexxar", text)
        self.assertIn("Loyalty:5", text)
        self.assertIn(
            "S:Mode$ ReduceCost | ValidCard$ Creature | ValidSpell$ Spell.Mutate | "
            "Type$ Spell | Activator$ You | Amount$ 2",
            text,
        )

    def test_enters_with_the_massacre_wurm_debuff(self):
        text = self.read_card()

        self.assertIn(
            "T:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | "
            "ValidCard$ Card.Self | Execute$ TrigMassacre",
            text,
        )
        self.assertIn(
            "SVar:TrigMassacre:DB$ PumpAll | NumAtt$ -2 | NumDef$ -2 | "
            "ValidCards$ Creature.OppCtrl | IsCurse$ True",
            text,
        )

    def test_plus_one_grants_mana_cost_mutate_and_creates_the_token(self):
        text = self.read_card()

        self.assertIn(
            "A:AB$ Effect | Cost$ AddCounter<1/LOYALTY> | Planeswalker$ True",
            text,
        )
        self.assertIn("Duration$ UntilEndOfTurn", text)
        self.assertIn(
            "RememberObjects$ ValidExile Creature.ExiledWithSource", text
        )
        self.assertIn("StaticAbilities$ GrantMutate", text)
        self.assertIn(
            "SVar:GrantMutate:Mode$ Continuous | Affected$ Creature.IsRemembered | "
            "AffectedZone$ Exile | AddKeyword$ Mutate:CardManaCost",
            text,
        )
        self.assertIn(
            "SVar:CreateMutant:DB$ Token | TokenScript$ bg_1_1_zombie_beast_mutate | "
            "TokenAmount$ 1 | TokenOwner$ You",
            text,
        )

    def test_minus_two_discovers_two_beasts_and_grants_persistent_play_permission(self):
        text = self.read_card()

        self.assertEqual(2, text.count("ValidCards$ Creature.Beast"))
        self.assertEqual(2, text.count("Source$ CardDatabase"))
        self.assertEqual(2, text.count("Destination$ Exile"))
        self.assertEqual(2, text.count("RememberChosen$ True"))
        self.assertIn(
            "A:AB$ CardDiscover | Cost$ SubCounter<2/LOYALTY> | Planeswalker$ True",
            text,
        )
        self.assertIn(
            "SVar:GrantPlay:DB$ Effect | Duration$ Permanent | "
            "RememberObjects$ Remembered | StaticAbilities$ MayPlay | "
            "ForgetOnMoved$ Exile | SubAbility$ Cleanup",
            text,
        )
        self.assertIn(
            "SVar:MayPlay:Mode$ Continuous | MayPlay$ True | "
            "MayPlayIgnoreColor$ True | Affected$ Card.IsRemembered | "
            "AffectedZone$ Exile",
            text,
        )
        self.assertIn("SVar:Cleanup:DB$ Cleanup | ClearRemembered$ True", text)

    def test_token_has_the_requested_characteristics_and_mutate_trigger(self):
        self.assertTrue(TOKEN.is_file(), TOKEN)
        text = TOKEN.read_text(encoding="utf-8")

        self.assertIn("Name:灵俑野兽", text)
        self.assertIn("ManaCost:no cost", text)
        self.assertIn("Colors:black,green", text)
        self.assertIn("Types:Creature Zombie Beast", text)
        self.assertIn("PT:1/1", text)
        self.assertIn(
            "T:Mode$ Mutates | ValidCard$ Card.Self | TriggerZones$ Battlefield | "
            "Execute$ TrigPutCounter",
            text,
        )
        self.assertIn(
            "SVar:TrigPutCounter:DB$ PutCounter | Defined$ TriggeredCardLKICopy | "
            "CounterType$ P1P1 | CounterNum$ 1",
            text,
        )

    def test_registration_localization_and_artwork(self):
        edition = EDITION.read_text(encoding="utf-8")
        self.assertIn("58 M 死亡猎手雷克萨 @Custom", edition)

        expected = (
            "死亡猎手雷克萨|死亡猎手雷克萨|传奇鹏洛客～雷克萨|"
            "利用合变异能来施放的生物咒语减少{2}来施放。\\n"
            "当雷克萨进场时，由对手操控的生物得-2/-2直到回合结束。\\n"
            "+1：所有以雷克萨放逐的生物牌获得合变异能直到回合结束。其合变费用"
            "等同于其法术力费用。派出一个1/1黑绿双色的灵俑／野兽衍生生物，且具有"
            "「每当此生物合变时，在其上放置一个+1/+1指示物。」\\n"
            "-2：发现两个野兽并将它们放逐。于这些牌持续放逐的时段内，你可以使用它们，"
            "且你可以将法术力视同任意颜色的法术力来施放它们。"
        )
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())

        from PIL import Image

        for path, size in ((CARD_ART, (816, 600)), (TOKEN_ART, (400, 292))):
            self.assertTrue(path.is_file(), path)
            with Image.open(path) as image:
                self.assertEqual(size, image.size)
                self.assertEqual("RGB", image.mode)
                self.assertAlmostEqual(1.37, image.width / image.height, delta=0.02)
        self.assertTrue(ART_BACKUP.is_file(), ART_BACKUP)


if __name__ == "__main__":
    unittest.main()
