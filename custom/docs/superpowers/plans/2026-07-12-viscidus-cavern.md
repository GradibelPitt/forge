# Viscidus Cavern Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the PH01 land `维希度斯的窟穴` with “{T}, discard a card: draw two cards,” using the supplied artwork and deploying it to the local Forge runtime.

**Architecture:** Implement the effect entirely with Forge's existing `Draw` ability and `Discard<1/Card>` cost syntax. Keep card logic, PH01 registration, localization, art, contract tests, documentation, and deployment evidence synchronized.

**Tech Stack:** Forge card-script DSL, Python `unittest`, PowerShell/System.Drawing artwork pipeline, Forge custom installer.

---

### Task 1: Add the failing card contract

**Files:**
- Create: `custom/tests/test_viscidus_cavern.py`

- [ ] **Step 1: Write the failing test**

```python
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "colorless" / "维希度斯的窟穴.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Viscidus_Cavern_original.jpg"
ART = ROOT / "cards" / "pictures" / "PH01" / "维希度斯的窟穴.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class ViscidusCavernContractTest(unittest.TestCase):
    def test_land_and_activated_ability(self):
        text = CARD.read_text(encoding="utf-8")
        self.assertIn("Name:维希度斯的窟穴", text)
        self.assertIn("Types:Land", text)
        self.assertIn(
            "A:AB$ Draw | Cost$ T Discard<1/Card> | NumCards$ 2 | Defined$ You",
            text,
        )

    def test_registration_art_and_localization(self):
        self.assertIn("37 R 维希度斯的窟穴 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART.is_file())
        self.assertIn(
            "维希度斯的窟穴|维希度斯的窟穴|地|{T}，弃一张牌：抽两张牌。",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run the test and verify RED**

Run: `python -m unittest tests.test_viscidus_cavern -v`

Expected: failure because `cards/colorless/维希度斯的窟穴.txt` does not exist.

### Task 2: Implement the card, registration, localization, and artwork

**Files:**
- Create: `custom/cards/colorless/维希度斯的窟穴.txt`
- Modify: `custom/editions/Placeholder_Set.txt`
- Modify: `forge-gui/res/languages/cardnames-zh-CN.txt`
- Create: `custom/tools/card-artwork/Viscidus_Cavern_original.jpg`
- Create: `custom/cards/pictures/PH01/维希度斯的窟穴.artcrop.jpg`

- [ ] **Step 1: Add the minimal card script**

```text
Name:维希度斯的窟穴
ManaCost:no cost
Types:Land
A:AB$ Draw | Cost$ T Discard<1/Card> | NumCards$ 2 | Defined$ You | SpellDescription$ Draw two cards.
Oracle:{T}, Discard a card: Draw two cards.
```

- [ ] **Step 2: Register PH01 #37 and Chinese text**

Append to `Placeholder_Set.txt`:

```text
37 R 维希度斯的窟穴 @Custom
```

Append to `cardnames-zh-CN.txt`:

```text
维希度斯的窟穴|维希度斯的窟穴|地|{T}，弃一张牌：抽两张牌。
```

- [ ] **Step 3: Preserve and crop the supplied artwork**

Copy the exact attachment to `custom/tools/card-artwork/Viscidus_Cavern_original.jpg`. Use System.Drawing to center-crop it to a 1.37:1 landscape RGB JPEG while preserving the central cavern, orange pod, tendrils, and treasure, then save it as `custom/cards/pictures/PH01/维希度斯的窟穴.artcrop.jpg`.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run: `python -m unittest tests.test_viscidus_cavern -v`

Expected: 2 tests pass.

### Task 3: Validate, document, deploy, and commit

**Files:**
- Modify: `custom/CARDS.md`
- Modify: `custom/VERIFICATION.md`

- [ ] **Step 1: Update authoritative documentation**

Add PH01 #37 to `CARDS.md`, describing the land and its discard-as-cost draw ability. Add a fresh top entry to `VERIFICATION.md` with actual test counts, crop dimensions, deployment paths, and hashes.

- [ ] **Step 2: Run validation**

Run:

```powershell
python tools\lint_card.py 'cards\colorless\维希度斯的窟穴.txt'
python -m unittest tests.test_viscidus_cavern -v
python -m unittest discover -s tests -p 'test_*.py'
```

Expected: lint succeeds, 2 focused tests pass, and the full DIY suite passes.

- [ ] **Step 3: Deploy and compare hashes**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\install_to_forge.ps1
Get-FileHash -Algorithm SHA256 <source-card>, <AppData-card>, <source-art>, <cache-art>
```

Expected: source/deployed card hashes match and source/cache art hashes match.

- [ ] **Step 4: Inspect the final diff and commit**

Stage only the card, artwork, backup, edition registration, localization, test, `CARDS.md`, and `VERIFICATION.md`. Run `git diff --cached --check`, then commit with:

```text
Add Viscidus Cavern custom land
```
