import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "colorless" / "脱困古神尤格萨隆.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "脱困古神尤格萨隆.artcrop.jpg"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "1-照片-1.jpg"
CHAOS_TENTACLE = ROOT / "cards" / "colorless" / "混乱触须.txt"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class YoggSaronUnboundContractTest(unittest.TestCase):
    def test_card_identity_and_dynamic_cost_reduction(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn("Name:脱困古神尤格萨隆", text)
        self.assertIn("ManaCost:15", text)
        self.assertIn("Types:Legendary Creature God", text)
        self.assertIn("PT:7/5", text)
        self.assertIn("K:Fear", text)
        self.assertIn("K:Haste", text)
        self.assertIn(
            "T:Mode$ NewGame | TriggerZones$ Hand,Library,Command | Execute$ CreateSpellCounterEmblem | Static$ True",
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

    def test_the_first_exhaust_ability_gains_control_of_a_nonartifact_creature(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "A:AB$ GainControl | Cost$ T | ValidTgts$ Creature.nonArtifact | TgtPrompt$ Select target nonartifact creature | Exhaust$ True",
            text,
        )
    def test_each_of_yoggs_exhaust_abilities_conjures_a_chaos_tentacle_card(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "T:Mode$ AbilityCast | ValidCard$ Card.Self | ValidActivatingPlayer$ You | ValidSA$ Activated.Exhaust | TriggerZones$ Battlefield | Execute$ ConjureChaosTentacle",
            text,
        )
        self.assertIn(
            "SVar:ConjureChaosTentacle:DB$ MakeCard | Conjure$ True | Name$ 混乱触须 | Zone$ Battlefield",
            text,
        )
        self.assertNotIn("TokenScript$ c_chaos_tentacle", text)
        tentacle = CHAOS_TENTACLE.read_text(encoding="utf-8")
        self.assertIn("Name:混乱触须", tentacle)
        self.assertIn("Types:Artifact", tentacle)

    def test_second_exhaust_goads_each_opponents_nonartifact_creatures(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "A:AB$ Goad | Cost$ T | Defined$ Valid Creature.OppCtrl+nonArtifact | Exhaust$ True",
            text,
        )
        self.assertNotIn("RepeatCards$ Creature.OppCtrl", text)

    def test_third_exhaust_conjures_six_chaos_tentacle_cards(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "A:AB$ MakeCard | Cost$ T | Conjure$ True | Name$ 混乱触须 | Amount$ 6 | Zone$ Battlefield | Exhaust$ True",
            text,
        )
        self.assertNotIn("A:AB$ Token", text)

    def test_previous_random_spell_chain_is_kept_as_an_unwired_reusable_template(self):
        text = CARD.read_text(encoding="utf-8")

        self.assertIn(
            "SVar:ArchivedChaosSpellstorm:DB$ Repeat | RepeatSubAbility$ ArchivedRandomSpell | MaxRepeat$ Z",
            text,
        )
        self.assertIn("SVar:Z:Count$ValidGraveyard Instant.YouOwn,Sorcery.YouOwn", text)
        self.assertIn(
            "SVar:ArchivedRandomSpell:DB$ NameCard | AtRandom$ True | ValidCards$ Instant,Sorcery | SubAbility$ ArchivedConjureRandomSpell",
            text,
        )
        self.assertIn(
            "SVar:ArchivedConjureRandomSpell:DB$ MakeCard | Name$ ChosenName | Conjure$ True | Zone$ None | RememberMade$ True | SubAbility$ ArchivedCastConjuredSpell",
            text,
        )
        self.assertIn(
            "SVar:ArchivedCastConjuredSpell:DB$ Play | Defined$ Remembered | ValidSA$ Spell | ZoneRegardless$ True | Controller$ You | WithoutManaCost$ True | Optional$ False | SubAbility$ ArchivedClearRandomSpell",
            text,
        )
        self.assertIn(
            "SVar:ArchivedClearRandomSpell:DB$ Cleanup | ClearRemembered$ True | ClearNamedCard$ True",
            text,
        )

    def test_card_is_registered_with_art_and_requested_chinese_text(self):
        self.assertIn("36 M 脱困古神尤格萨隆 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())
        self.assertIn("Oracle:Fear\\nHaste", CARD.read_text(encoding="utf-8"))
        expected = (
            "脱困古神尤格萨隆|脱困古神尤格萨隆|传奇生物～古神|"
            "恐惧\\n敏捷\\n"
            "本局游戏中，你每释放过一个非生物咒语，脱困古神尤格萨隆便减少{1}来施放。\\n"
            "当你启动脱困古神尤格萨隆的竭绝异能时，派出一个混乱触须。\\n"
            "竭绝—{T}：获得目标非神器生物的操控权。\\n"
            "竭绝—{T}：煽惑所有由对手操控的非神器生物。\\n"
            "竭绝—{T}：化生六个混乱触须并放进战场。"
        )
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())


if __name__ == "__main__":
    unittest.main()
