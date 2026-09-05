from __future__ import annotations

from pathlib import Path
import sys


if len(sys.argv) != 2:
    raise SystemExit("usage: dragon_breaths_builder.py <forge-root>")

ROOT = Path(sys.argv[1]).resolve()
CUSTOM = ROOT / "custom"


def write_text(rel: str, text: str) -> None:
    path = ROOT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text.rstrip("\n") + "\n", encoding="utf-8", newline="\n")


CARD_FILES = {
    "custom/cards/white/龙鳞祭司.txt": """Name:龙鳞祭司
ManaCost:W
Types:Creature Human Cleric
PT:1/2
T:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | ValidCard$ Card.Self | Execute$ TrigReturn | TriggerDescription$ 当龙鳞祭司进场时，你可以请援龙。若你如此作，将目标瞬间或法术牌从你的坟墓场移回你手上。
SVar:TrigReturn:AB$ ChangeZone | Cost$ Behold<1/Dragon> | Origin$ Graveyard | Destination$ Hand | ValidTgts$ Instant.YouOwn,Sorcery.YouOwn | TgtPrompt$ 选择目标你坟墓场中的瞬间或法术牌
DeckHas:Ability$Graveyard
Oracle:当龙鳞祭司进场时，你可以请援龙。若你如此作，将目标瞬间或法术牌从你的坟墓场移回你手上。
""",
    "custom/cards/multicolor/奥术吐息.txt": """Name:奥术吐息
ManaCost:U R
Types:Instant
S:Mode$ OptionalCost | EffectZone$ All | ValidCard$ Card.Self | ValidSA$ Spell | Cost$ Behold<1/Dragon> | Description$ 你可以请援龙，以作为施放此咒语的额外费用。
A:SP$ DealDamage | ValidTgts$ Creature | TgtPrompt$ 选择目标生物 | NumDmg$ 2 | SubAbility$ DBDraw | SpellDescription$ 奥术吐息对目标生物造成2点伤害。如果已请援龙，则你抓一张牌。
SVar:DBDraw:DB$ Draw | Condition$ OptionalCost | ConditionOptionalPaid$ True | Defined$ You | NumCards$ 1
Oracle:你可以请援龙，以作为施放此咒语的额外费用。\\n奥术吐息对目标生物造成2点伤害。如果已请援龙，则你抓一张牌。
""",
    "custom/cards/multicolor/梦境吐息.txt": """Name:梦境吐息
ManaCost:G U
Types:Sorcery
S:Mode$ OptionalCost | EffectZone$ All | ValidCard$ Card.Self | ValidSA$ Spell | Cost$ Behold<1/Dragon> | Description$ 你可以请援龙，以作为施放此咒语的额外费用。
A:SP$ Draw | Defined$ You | NumCards$ 1 | SubAbility$ DBRamp | SpellDescription$ 抓一张牌。如果已请援龙，则从你牌库中搜寻一张基本地牌，将该牌横置放进战场，然后洗牌。
SVar:DBRamp:DB$ ChangeZone | Condition$ OptionalCost | ConditionOptionalPaid$ True | Origin$ Library | Destination$ Battlefield | Tapped$ True | ChangeType$ Land.Basic | ChangeTypeDesc$ 基本地牌 | ShuffleNonMandatory$ True
Oracle:你可以请援龙，以作为施放此咒语的额外费用。\\n抓一张牌。如果已请援龙，则从你牌库中搜寻一张基本地牌，将该牌横置放进战场，然后洗牌。
""",
}

for rel, text in CARD_FILES.items():
    write_text(rel, text)

# PH01 registration. These are the next unused collector numbers after #139.
edition_path = CUSTOM / "editions" / "Placeholder_Set.txt"
edition = edition_path.read_text(encoding="utf-8")
registrations = [
    "140 U 龙鳞祭司 @Custom",
    "141 C 奥术吐息 @Custom",
    "142 C 梦境吐息 @Custom",
]
for number, name in ((140, "龙鳞祭司"), (141, "奥术吐息"), (142, "梦境吐息")):
    for line in edition.splitlines():
        if line.startswith(f"{number} ") and name not in line:
            raise RuntimeError(f"PH01 collector number {number} is already occupied: {line}")
for line in registrations:
    if line not in edition.splitlines():
        edition = edition.rstrip("\n") + "\n" + line + "\n"
edition_path.write_text(edition, encoding="utf-8", newline="\n")

# Cards index. Insert the trio directly after Rommath, which is the prior PH01 entry.
cards_doc = CUSTOM / "CARDS.md"
doc = cards_doc.read_text(encoding="utf-8")
new_rows = [
    "| 龙鳞祭司 | `{W}`，1/2 生物～人类／祭师 | `cards/white/龙鳞祭司.txt` | 140 | 进场时可请援龙；若如此作，将目标自己坟墓场中的瞬间或法术牌移回手上。 |",
    "| 奥术吐息 | `{U}{R}` 瞬间 | `cards/multicolor/奥术吐息.txt` | 141 | 可将请援龙作为额外费用；对目标生物造成 2 点伤害，若已请援龙则抓一张牌。 |",
    "| 梦境吐息 | `{G}{U}` 法术 | `cards/multicolor/梦境吐息.txt` | 142 | 可将请援龙作为额外费用；抓一张牌，若已请援龙则检索一张基本地牌横置进场并洗牌。 |",
]
if "| 龙鳞祭司 |" not in doc:
    lines = doc.splitlines()
    insert_at = next((i + 1 for i, line in enumerate(lines) if line.startswith("| 大法师罗曼斯 |")), None)
    if insert_at is None:
        raise RuntimeError("Could not locate Grand Magister Rommath row in CARDS.md")
    lines[insert_at:insert_at] = new_rows
    doc = "\n".join(lines) + "\n"
cards_doc.write_text(doc, encoding="utf-8", newline="\n")

# User confirmed these custom localization records may be appended at EOF.
loc_path = ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"
loc = loc_path.read_text(encoding="utf-8")
loc_entries = [
    "龙鳞祭司|龙鳞祭司|生物～人类／祭师|当龙鳞祭司进场时，你可以请援龙。若你如此作，将目标瞬间或法术牌从你的坟墓场移回你手上。",
    "奥术吐息|奥术吐息|瞬间|你可以请援龙，以作为施放此咒语的额外费用。\\n奥术吐息对目标生物造成2点伤害。如果已请援龙，则你抓一张牌。",
    "梦境吐息|梦境吐息|法术|你可以请援龙，以作为施放此咒语的额外费用。\\n抓一张牌。如果已请援龙，则从你牌库中搜寻一张基本地牌，将该牌横置放进战场，然后洗牌。",
]
existing_keys = {line.split("|", 1)[0] for line in loc.splitlines() if "|" in line}
for entry in loc_entries:
    key = entry.split("|", 1)[0]
    if key not in existing_keys:
        loc = loc.rstrip("\n") + "\n" + entry + "\n"
        existing_keys.add(key)
loc_path.write_text(loc, encoding="utf-8", newline="\n")

# Targeted contract tests remain in the authoritative source tree.
test_text = r'''from pathlib import Path
import unittest


CUSTOM = Path(__file__).resolve().parents[1]
ROOT = CUSTOM.parent
PRIEST = CUSTOM / "cards" / "white" / "龙鳞祭司.txt"
ARCANE = CUSTOM / "cards" / "multicolor" / "奥术吐息.txt"
DREAM = CUSTOM / "cards" / "multicolor" / "梦境吐息.txt"
EDITION = CUSTOM / "editions" / "Placeholder_Set.txt"
LOCALIZATION = ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class DragonBreathsContractTest(unittest.TestCase):
    def test_dragon_scale_priest_characteristics_and_behold_return(self):
        text = PRIEST.read_text(encoding="utf-8")
        self.assertIn("ManaCost:W", text)
        self.assertIn("Types:Creature Human Cleric", text)
        self.assertIn("PT:1/2", text)
        self.assertIn("T:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | ValidCard$ Card.Self | Execute$ TrigReturn", text)
        self.assertIn("SVar:TrigReturn:AB$ ChangeZone | Cost$ Behold<1/Dragon>", text)
        self.assertIn("Origin$ Graveyard | Destination$ Hand", text)
        self.assertIn("ValidTgts$ Instant.YouOwn,Sorcery.YouOwn", text)

    def test_arcane_breath_optional_behold_damage_and_draw(self):
        text = ARCANE.read_text(encoding="utf-8")
        self.assertIn("ManaCost:U R", text)
        self.assertIn("Types:Instant", text)
        self.assertIn("Cost$ Behold<1/Dragon>", text)
        self.assertIn("A:SP$ DealDamage | ValidTgts$ Creature", text)
        self.assertIn("NumDmg$ 2", text)
        self.assertIn("SubAbility$ DBDraw", text)
        self.assertIn("SVar:DBDraw:DB$ Draw | Condition$ OptionalCost | ConditionOptionalPaid$ True | Defined$ You | NumCards$ 1", text)

    def test_dream_breath_optional_behold_draw_and_tapped_basic(self):
        text = DREAM.read_text(encoding="utf-8")
        self.assertIn("ManaCost:G U", text)
        self.assertIn("Types:Sorcery", text)
        self.assertIn("Cost$ Behold<1/Dragon>", text)
        self.assertIn("A:SP$ Draw | Defined$ You | NumCards$ 1 | SubAbility$ DBRamp", text)
        self.assertIn("SVar:DBRamp:DB$ ChangeZone | Condition$ OptionalCost | ConditionOptionalPaid$ True", text)
        self.assertIn("Origin$ Library | Destination$ Battlefield | Tapped$ True", text)
        self.assertIn("ChangeType$ Land.Basic", text)
        self.assertIn("ShuffleNonMandatory$ True", text)

    def test_registration_and_localization(self):
        edition = EDITION.read_text(encoding="utf-8")
        self.assertIn("140 U 龙鳞祭司 @Custom", edition)
        self.assertIn("141 C 奥术吐息 @Custom", edition)
        self.assertIn("142 C 梦境吐息 @Custom", edition)
        localization = LOCALIZATION.read_text(encoding="utf-8")
        self.assertIn("龙鳞祭司|龙鳞祭司|生物～人类／祭师|", localization)
        self.assertIn("奥术吐息|奥术吐息|瞬间|", localization)
        self.assertIn("梦境吐息|梦境吐息|法术|", localization)


if __name__ == "__main__":
    unittest.main()
'''
write_text("custom/tests/test_dragon_breaths.py", test_text)

print("DRAGON_BREATHS_SOURCE_GENERATED=OK")
