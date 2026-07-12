import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "colorless" / "脱困古神尤格萨隆.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "脱困古神尤格萨隆.artcrop.jpg"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "1-照片-1.jpg"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class YoggSaronUnboundContractTest(unittest.TestCase):
    def test_card_identity_and_dynamic_cost_reduction(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:脱困古神尤格萨隆", text)
        self.assertIn("ManaCost:15", text)
        self.assertIn("Types:Legendary Creature God", text)
        self.assertIn("PT:7/5", text)
        self.assertIn(
            "T:Mode$ NewGame | TriggerZones$ Hand,Library | Execute$ CreateSpellCounterEmblem | Static$ True",
            text,
        )
        self.assertIn(
            "SVar:CreateSpellCounterEmblem:DB$ Effect | Name$ Emblem — 脱困古神尤格萨隆 | Triggers$ CountNonCreatureSpell | StaticAbilities$ ReduceYoggCost | Duration$ Permanent | Unique$ True",
            text,
        )
        self.assertIn(
            "SVar:CountNonCreatureSpell:Mode$ SpellCast | ValidCard$ Card.nonCreature | ValidActivatingPlayer$ You | TriggerZones$ Command | Execute$ AddSpellCounter",
            text,
        )
        self.assertIn(
            "SVar:AddSpellCounter:DB$ PutCounter | Defined$ Self | CounterType$ SPELL | CounterNum$ 1",
            text,
        )
        self.assertIn(
            "SVar:ReduceYoggCost:Mode$ ReduceCost | ValidCard$ Card.named脱困古神尤格萨隆+YouOwn | Type$ Spell | Activator$ You | Amount$ X",
            text,
        )
        self.assertIn("SVar:X:Count$CardCounters.SPELL", text)

    def test_the_three_exhaust_abilities_match_the_requested_effects(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "A:AB$ GainControl | Cost$ T | ValidTgts$ Creature.nonArtifact | TgtPrompt$ Select target nonartifact creature | Exhaust$ True",
            text,
        )
        self.assertIn(
            "A:AB$ RepeatEach | Cost$ T | RepeatCards$ Creature.OppCtrl | RepeatSubAbility$ ChooseOtherOpponentCreature | Exhaust$ True | DamageMap$ True",
            text,
        )
        self.assertIn(
            "SVar:ChooseOtherOpponentCreature:DB$ ChooseCard | AtRandom$ True | Choices$ Creature.OppCtrl+!IsRemembered | SubAbility$ DealRememberedPowerDamage",
            text,
        )
        self.assertIn(
            "SVar:DealRememberedPowerDamage:DB$ DealDamage | DamageSource$ Remembered | NumDmg$ Y | Defined$ ChosenCard",
            text,
        )
        self.assertIn("SVar:Y:Remembered$CardPower", text)

    def test_third_exhaust_conjures_and_casts_x_random_spells(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "A:AB$ Repeat | Cost$ T | RepeatSubAbility$ RandomSpell | MaxRepeat$ Z | Exhaust$ True",
            text,
        )
        self.assertIn("SVar:Z:Count$ValidGraveyard Instant.YouOwn,Sorcery.YouOwn", text)
        self.assertIn(
            "SVar:RandomSpell:DB$ NameCard | AtRandom$ True | ValidCards$ Instant,Sorcery | SubAbility$ ConjureRandomSpell",
            text,
        )
        self.assertIn(
            "SVar:ConjureRandomSpell:DB$ MakeCard | Name$ ChosenName | Conjure$ True | Zone$ None | RememberMade$ True | SubAbility$ CastConjuredSpell",
            text,
        )
        self.assertIn(
            "SVar:CastConjuredSpell:DB$ Play | Defined$ Remembered | ValidSA$ Spell | ZoneRegardless$ True | Controller$ You | WithoutManaCost$ True | Optional$ False | SubAbility$ ClearRandomSpell",
            text,
        )

    def test_card_is_registered_with_art_and_requested_chinese_text(self):
        self.assertIn("36 M 脱困古神尤格萨隆 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())
        expected = (
            "脱困古神尤格萨隆|脱困古神尤格萨隆|传奇生物～古神|"
            "本局游戏中，你每释放过一个非生物咒语，脱困古神尤格萨隆便减少{1}来施放。\\n"
            "竭绝—{T}：获得目标非神器生物的操控权。\\n"
            "竭绝—{T}：每个由对手操控的生物随机对另一个由对手操控的生物造成等同于其自身力量的伤害。\\n"
            "竭绝—{T}：随机释放X个法术，X等同于你坟墓场中瞬间和法术牌的数量。"
        )
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())


if __name__ == "__main__":
    unittest.main()
