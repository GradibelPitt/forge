# Mind Vision Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the PH01 custom card 心灵视界, which inspects a target opponent's hand, lets its controller choose any card there, conjures a fresh copy by printed name, and permanently reduces that conjured card's spell cost by {1}.

**Architecture:** Implement the rules entirely in Forge's existing card DSL using the proven `RevealHand` → `ChooseCard` → `MakeCard` → perpetual `Animate` chain. Keep rules, registration, localization, artwork, contract tests, deployment, and verification evidence in their existing project-owned files; do not change Java or package a new desktop JAR.

**Tech Stack:** Forge card-script DSL, Python `unittest`, Pillow, PowerShell deployment tooling, PH01 edition data, Forge zh-CN language resources.

---

## File map

- Create `custom/tests/test_mind_vision.py`: focused contract tests for characteristics, unrestricted hand choice, name-based conjuring, perpetual cost reduction, cleanup, localization, registration, and art.
- Create `custom/cards/blue/心灵视界.txt`: the complete Forge card script.
- Modify `custom/editions/Placeholder_Set.txt`: register PH01 collector number 39 at common rarity with `@Custom` artwork.
- Modify `forge-gui/res/languages/cardnames-zh-CN.txt`: add the exact Simplified Chinese display entry.
- Create `custom/tools/card-artwork/Art_CS2_003.png`: preserve the supplied original artwork.
- Create `custom/cards/pictures/PH01/心灵视界.artcrop.jpg`: deterministic 512×374 RGB art crop for Forge's dynamic frame.
- Modify `custom/VERIFICATION.md`: append the actual test, deployment, hash, and remaining manual-client evidence without disturbing unrelated Soulfire work already present in that file.

### Task 1: Add the failing card contract

**Files:**
- Create: `custom/tests/test_mind_vision.py`

- [ ] **Step 1: Write the focused contract test**

```python
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "blue" / "心灵视界.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "心灵视界.artcrop.jpg"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Art_CS2_003.png"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class MindVisionContractTest(unittest.TestCase):
    def test_characteristics_registration_and_chinese_wording(self):
        text = CARD.read_text(encoding="utf-8")
        self.assertIn("Name:心灵视界", text)
        self.assertIn("ManaCost:U", text)
        self.assertIn("Types:Sorcery", text)
        self.assertIn("39 C 心灵视界 @Custom", EDITION.read_text(encoding="utf-8"))
        expected = (
            "心灵视界|心灵视界|法术|检视目标对手的手牌，从中选择一张牌。"
            "化生一张以此法选择的牌名的复制并置于你的手上。它减少{1}来施放。"
        )
        self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())

    def test_inspects_target_opponent_and_allows_any_card_in_hand(self):
        text = CARD.read_text(encoding="utf-8")
        reveal = next(line for line in text.splitlines() if line.startswith("A:SP$ RevealHand"))
        choose = next(line for line in text.splitlines() if line.startswith("SVar:DBChooseCard:"))
        self.assertIn("ValidTgts$ Opponent", reveal)
        self.assertIn("Look$ True", reveal)
        self.assertIn("RememberRevealed$ True", reveal)
        self.assertIn("SubAbility$ DBChooseCard", reveal)
        self.assertIn("ChoiceZone$ Hand", choose)
        self.assertIn("Choices$ Card.IsRemembered", choose)
        self.assertIn("Mandatory$ True", choose)
        self.assertNotIn("nonLand", choose)
        self.assertNotIn("Creature", choose)

    def test_conjures_fresh_printed_copy_with_perpetual_cost_reduction(self):
        text = CARD.read_text(encoding="utf-8")
        conjure = next(line for line in text.splitlines() if line.startswith("SVar:DBConjure:"))
        animate = next(line for line in text.splitlines() if line.startswith("SVar:DBAnimate:"))
        reduce_cost = next(line for line in text.splitlines() if line.startswith("SVar:ReduceCost:"))
        cleanup = next(line for line in text.splitlines() if line.startswith("SVar:DBCleanup:"))
        self.assertIn("DB$ MakeCard", conjure)
        self.assertIn("Conjure$ True", conjure)
        self.assertIn("DefinedName$ ChosenCard", conjure)
        self.assertIn("Zone$ Hand", conjure)
        self.assertIn("RememberMade$ True", conjure)
        self.assertIn("Defined$ Remembered", animate)
        self.assertIn("staticAbilities$ ReduceCost", animate)
        self.assertIn("Duration$ Perpetual", animate)
        self.assertIn("ValidCard$ Card.Self", reduce_cost)
        self.assertIn("Type$ Spell", reduce_cost)
        self.assertIn("Amount$ 1", reduce_cost)
        self.assertIn("EffectZone$ All", reduce_cost)
        self.assertIn("ClearRemembered$ True", cleanup)
        self.assertIn("ClearChosenCard$ True", cleanup)

    def test_original_and_dynamic_art_are_preserved(self):
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertEqual((512, 374), image.size)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.02)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run the new test to verify the red state**

Run from `D:\Forge\forge-latest\custom`:

```powershell
python -m unittest tests.test_mind_vision -v
```

Expected: `ERROR` because `cards/blue/心灵视界.txt` does not exist yet. The failure must be missing implementation, not a syntax or import error.

### Task 2: Implement rules, registration, and localization

**Files:**
- Create: `custom/cards/blue/心灵视界.txt`
- Modify: `custom/editions/Placeholder_Set.txt`
- Modify: `forge-gui/res/languages/cardnames-zh-CN.txt`
- Test: `custom/tests/test_mind_vision.py`

- [ ] **Step 1: Add the card script**

```text
Name:心灵视界
ManaCost:U
Types:Sorcery
A:SP$ RevealHand | ValidTgts$ Opponent | Look$ True | RememberRevealed$ True | SubAbility$ DBChooseCard | SpellDescription$ Look at target opponent's hand, then choose a card from it. Conjure a duplicate of the card with the chosen name into your hand. It perpetually gains "This spell costs {1} less to cast."
SVar:DBChooseCard:DB$ ChooseCard | ChoiceZone$ Hand | Choices$ Card.IsRemembered | ChoiceTitle$ Choose a card from that opponent's hand | Mandatory$ True | SubAbility$ DBConjure
SVar:DBConjure:DB$ MakeCard | Conjure$ True | DefinedName$ ChosenCard | Zone$ Hand | RememberMade$ True | SubAbility$ DBAnimate
SVar:DBAnimate:DB$ Animate | Defined$ Remembered | staticAbilities$ ReduceCost | Duration$ Perpetual | SubAbility$ DBCleanup | StackDescription$ None
SVar:ReduceCost:Mode$ ReduceCost | ValidCard$ Card.Self | Type$ Spell | Amount$ 1 | EffectZone$ All | Description$ This spell costs {1} less to cast.
SVar:DBCleanup:DB$ Cleanup | ClearRemembered$ True | ClearChosenCard$ True
AI:RemoveDeck:Random
Oracle:Look at target opponent's hand, then choose a card from it. Conjure a duplicate of the card with the chosen name into your hand. It perpetually gains "This spell costs {1} less to cast."
```

- [ ] **Step 2: Register the card in PH01**

Append this exact unique entry to `custom/editions/Placeholder_Set.txt`:

```text
39 C 心灵视界 @Custom
```

- [ ] **Step 3: Add the Simplified Chinese display line**

Append this exact line to `forge-gui/res/languages/cardnames-zh-CN.txt`:

```text
心灵视界|心灵视界|法术|检视目标对手的手牌，从中选择一张牌。化生一张以此法选择的牌名的复制并置于你的手上。它减少{1}来施放。
```

- [ ] **Step 4: Run the rules-focused tests**

```powershell
python -m unittest `
  tests.test_mind_vision.MindVisionContractTest.test_characteristics_registration_and_chinese_wording `
  tests.test_mind_vision.MindVisionContractTest.test_inspects_target_opponent_and_allows_any_card_in_hand `
  tests.test_mind_vision.MindVisionContractTest.test_conjures_fresh_printed_copy_with_perpetual_cost_reduction -v
```

Expected: all 3 tests report `ok`.

- [ ] **Step 5: Lint the new card**

```powershell
python tools\lint_card.py cards\blue\心灵视界.txt
```

Expected: exit code 0 with no lint errors.

- [ ] **Step 6: Commit the rules layer**

```powershell
git add -- custom/tests/test_mind_vision.py custom/cards/blue/心灵视界.txt custom/editions/Placeholder_Set.txt forge-gui/res/languages/cardnames-zh-CN.txt
git commit -m "Add Mind Vision card rules"
```

Expected: the commit contains only the four listed paths and does not stage existing Soulfire or temporary-file changes.

### Task 3: Preserve and crop the artwork

**Files:**
- Create: `custom/tools/card-artwork/Art_CS2_003.png`
- Create: `custom/cards/pictures/PH01/心灵视界.artcrop.jpg`
- Test: `custom/tests/test_mind_vision.py`

- [ ] **Step 1: Inspect the source image before transforming it**

Open `C:\Users\Marsh\Desktop\Art_CS2_003.png` and confirm that it is pure artwork with no final card name, mana cost, type line, rules text, or complete card frame. Confirm the visible focal points are the glowing eyes and central face.

- [ ] **Step 2: Preserve the original without modifying the desktop copy**

```powershell
Copy-Item -LiteralPath 'C:\Users\Marsh\Desktop\Art_CS2_003.png' -Destination 'D:\Forge\forge-latest\custom\tools\card-artwork\Art_CS2_003.png'
```

Expected: source and backup SHA-256 hashes match.

- [ ] **Step 3: Produce the deterministic dynamic-frame crop**

Crop the 512×512 source to box `(0, 69, 512, 443)`, which yields 512×374 and removes excess top and bottom space while retaining both glowing eyes, the face, and the lower muzzle. Save as high-quality RGB JPEG:

```powershell
@'
from pathlib import Path
from PIL import Image

source = Path(r"D:\Forge\forge-latest\custom\tools\card-artwork\Art_CS2_003.png")
target = Path(r"D:\Forge\forge-latest\custom\cards\pictures\PH01\心灵视界.artcrop.jpg")
with Image.open(source) as image:
    crop = image.convert("RGB").crop((0, 69, 512, 443))
    crop.save(target, format="JPEG", quality=95, subsampling=0, optimize=True)
'@ | python -
```

Expected: `心灵视界.artcrop.jpg` is a 512×374 RGB JPEG with an aspect ratio of approximately 1.37.

- [ ] **Step 4: Run the complete focused contract**

```powershell
python -m unittest tests.test_mind_vision -v
```

Expected: 4 tests pass.

- [ ] **Step 5: Commit the artwork**

```powershell
git add -- custom/tools/card-artwork/Art_CS2_003.png custom/cards/pictures/PH01/心灵视界.artcrop.jpg
git commit -m "Add Mind Vision artwork"
```

Expected: the commit contains only the original backup and generated crop.

### Task 4: Regress, deploy, and record evidence

**Files:**
- Modify: `custom/VERIFICATION.md`
- Verify: all Mind Vision source and deployed files

- [ ] **Step 1: Run the focused test and lint together**

```powershell
python -m unittest tests.test_mind_vision -v
python tools\lint_card.py cards\blue\心灵视界.txt
```

Expected: 4 tests pass and lint exits 0.

- [ ] **Step 2: Run the complete DIY regression suite**

```powershell
python -m unittest discover -s tests -p "test_*.py"
```

Expected: all tests pass. If an unrelated pre-existing failure remains, record its exact test name and prove `tests.test_mind_vision` still passes; do not modify unrelated code.

- [ ] **Step 3: Install the card, edition data, localization, and art**

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\install_to_forge.ps1
```

Expected: the installer completes without error and synchronizes the card script, PH01 edition, localization, and picture cache.

- [ ] **Step 4: Compare source and deployed hashes**

```powershell
$sourceCard = 'D:\Forge\forge-latest\custom\cards\blue\心灵视界.txt'
$deployedCard = Join-Path $env:APPDATA 'Forge\custom\cards\blue\心灵视界.txt'
$sourceArt = 'D:\Forge\forge-latest\custom\cards\pictures\PH01\心灵视界.artcrop.jpg'
$deployedArt = Join-Path $env:LOCALAPPDATA 'Forge\Cache\pics\cards\PH01\心灵视界.artcrop.jpg'
Get-FileHash -Algorithm SHA256 $sourceCard, $deployedCard, $sourceArt, $deployedArt
```

Expected: source/deployed card hashes match each other, and source/deployed art hashes match each other.

- [ ] **Step 5: Append precise verification evidence**

Append one dated `2026-07-13` entry to `custom/VERIFICATION.md` containing:

```text
- 2026-07-13 新增 `心灵视界`：{U} 法术，检视目标对手的手牌并允许选择包括地牌在内的任意牌；按所选印刷牌名化生全新复制品到你的手牌，该牌永久获得“此咒语减少{1}来施放”。实现复用 `RevealHand`、`ChooseCard`、`MakeCard` 与永久 `Animate`/`ReduceCost`，未修改 Java。目标契约测试、单卡 lint 与 DIY 全量测试均通过；安装脚本已同步脚本、PH01 #39、本地化与卡图，源/部署脚本及卡图 SHA-256 分别一致。仍需重启或重载 Forge 后手工确认隐藏手牌仅向施法者显示、地牌可选及非地牌费用减少。
```

Replace “均通过” with the exact observed full-suite count, and include the two actual SHA-256 values rather than leaving them implicit. Preserve all pre-existing uncommitted Soulfire text in this file.

- [ ] **Step 6: Commit only this task's verification hunk**

Stage only the newly appended Mind Vision paragraph from `custom/VERIFICATION.md`; do not stage its pre-existing Soulfire edits. Verify the cached diff before committing:

```powershell
git diff --cached -- custom/VERIFICATION.md
git commit -m "Record Mind Vision verification"
```

Expected: the cached diff contains only the Mind Vision evidence paragraph. If the hunk cannot be isolated safely because it overlaps existing edits, leave the evidence unstaged and report that explicitly instead of staging another task's work.

- [ ] **Step 7: Inspect final repository state**

```powershell
git status --short
git log -3 --oneline
```

Expected: Mind Vision implementation commits are present; any remaining dirty paths are the pre-existing Soulfire, document, temporary, or desktop-argument changes and are reported without modification.
