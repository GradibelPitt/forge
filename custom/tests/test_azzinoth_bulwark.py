import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "colorless" / "埃辛诺斯壁垒.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-2e2d56f8-511d-452d-886e-23b7e846eaba.png"
)
ART = ROOT / "cards" / "pictures" / "PH01" / "埃辛诺斯壁垒.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

SOURCE_ORACLE = (
    "Hexproof, indestructible\\n"
    "Durability 7 (This permanent enters with seven durability counters on it. "
    "When the last durability counter is removed from it, sacrifice it.)\\n"
    "Remove a durability counter from CARDNAME: Choose a source. Prevent the next "
    "damage that source would deal to target player or the next effect from that "
    "source that would cause that player to lose life this turn."
)
ZH_ORACLE = (
    "辟邪，不灭。\\n"
    "耐久7（此永久物进战场时上面有七个耐久指示物。当最后一个耐久指示物从其上移去时，将它牺牲。）\\n"
    "从埃辛诺斯壁垒上移去一个耐久指示物：选择一个来源，于本回合中，"
    "防止该来源下一次将对目标牌手造成的伤害或失去生命的效应。"
)


class AzzinothBulwarkContractTest(unittest.TestCase):
    def test_characteristics_durability_and_activated_cost(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:埃辛诺斯壁垒", lines)
        self.assertIn("ManaCost:1 W W", lines)
        self.assertIn("Types:Legendary Artifact", lines)
        self.assertIn("K:Durability:7", lines)
        self.assertIn("K:Hexproof", lines)
        self.assertIn("K:Indestructible", lines)

        ability = next(line for line in lines if line.startswith("A:AB$ ChooseSource"))
        self.assertIn("Cost$ SubCounter<1/DURABILITY>", ability)
        self.assertIn("Choices$ Card,Emblem", ability)
        self.assertIn("SubAbility$ DBEffect", ability)

    def test_one_shield_covers_chosen_source_damage_or_life_loss(self):
        text = CARD.read_text(encoding="utf-8")
        lines = text.splitlines()
        effect = next(line for line in lines if line.startswith("SVar:DBEffect:"))
        damage = next(line for line in lines if line.startswith("SVar:RepDamage:"))
        life_loss = next(line for line in lines if line.startswith("SVar:RepLifeLoss:"))

        self.assertIn("ValidTgts$ Player", effect)
        self.assertIn("RememberObjects$ Targeted", effect)
        self.assertIn("ReplacementEffects$ RepDamage,RepLifeLoss", effect)
        self.assertIn("Event$ DamageDone", damage)
        self.assertIn("ValidTarget$ Player.IsRemembered", damage)
        self.assertIn("ValidSource$ Card.ChosenCardStrict,Emblem.ChosenCard", damage)
        self.assertIn("ReplaceWith$ ExileEffect", damage)
        self.assertIn("Event$ LifeReduced", life_loss)
        self.assertIn("ValidPlayer$ Player.IsRemembered", life_loss)
        self.assertIn("ValidSource$ Card.ChosenCardStrict,Emblem.ChosenCard", life_loss)
        self.assertIn("IsDamage$ False", life_loss)
        self.assertIn("ReplaceWith$ ExileEffect", life_loss)
        self.assertIn(f"Oracle:{SOURCE_ORACLE}", text)

    def test_registration_localization_art_and_documentation(self):
        self.assertIn("89 M 埃辛诺斯壁垒 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertIn(
            f"埃辛诺斯壁垒|埃辛诺斯壁垒|传奇神器|{ZH_ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 埃辛诺斯壁垒 | `{1}{W}{W}` 传奇神器 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        self.assertTrue(ART_BACKUP.is_file())
        self.assertEqual(
            "059B7C8F243C32F3AA037F90ED5A5E18DA58253584D6B863327771889B0CA1A5",
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        self.assertTrue(ART.is_file())
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((512, 374), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
