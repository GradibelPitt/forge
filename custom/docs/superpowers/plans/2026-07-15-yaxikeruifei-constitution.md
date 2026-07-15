# 亚西克瑞非体质 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the zero-mana colorless enchantment 亚西克瑞非体质, its supplied art, exact Chinese display text, one-per-game colorless-commander library deployment, tests, documentation, and local Forge deployment.

**Architecture:** Keep the gameplay implementation in existing Forge DSL. The battlefield protection uses the same `GameLoss` / `GameWin` replacement pattern as Platinum Angel, gated by `Count$YourLifeTotal GE1`; a silent `ResolveBeforeFirstTurn` trigger creates one unique command-zone effect only when the player has a commander, and that effect grants a zero-cost, instant-speed `ChangeZone` ability restricted to commander color identity count zero and one activation per game.

**Tech Stack:** Forge card DSL, Python `unittest`, Pillow, PowerShell deployment tooling.

---

### Task 1: Lock the card contract with a failing test

**Files:**
- Create: `custom/tests/test_yaxikeruifei_constitution.py`

- [x] **Step 1: Write the failing contract test**

```python
import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "colorless" / "亚西克瑞非体质.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "亚西克瑞非体质.artcrop.jpg"
BACKUP = (
    ROOT
    / "tools"
    / "card-artwork"
    / "codex-clipboard-cb06b767-f050-407f-9938-bb8851b8004c.png"
)
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"


class YaxikeruifeiConstitutionContractTest(unittest.TestCase):
    def test_card_characteristics_and_life_gated_platinum_angel_effect(self):
        text = CARD.read_text(encoding="utf-8")
        self.assertIn("Name:亚西克瑞非体质", text)
        self.assertIn("ManaCost:0", text)
        self.assertIn("Types:Enchantment", text)
        self.assertEqual(2, text.count("CheckSVar$ Count$YourLifeTotal"))
        self.assertIn("R:Event$ GameLoss", text)
        self.assertIn("ValidPlayer$ You", text)
        self.assertIn("R:Event$ GameWin", text)
        self.assertIn("ValidPlayer$ Opponent", text)
        self.assertEqual(2, text.count("SVarCompare$ GE1"))

    def test_colorless_commander_library_entry_is_unique_and_once_per_game(self):
        text = CARD.read_text(encoding="utf-8")
        self.assertIn(
            "T:Mode$ NewGame | TriggerZones$ Hand,Library | "
            "ResolveBeforeFirstTurn$ True | Execute$ CreateLibraryAccess | Static$ True",
            text,
        )
        self.assertIn("ConditionPresent$ Card.IsCommander+YouOwn", text)
        self.assertIn("PresentZone$ Command", text)
        self.assertIn("Abilities$ EnterFromLibrary", text)
        self.assertIn("Unique$ True", text)
        self.assertIn("Cost$ 0", text)
        self.assertIn("ActivationZone$ Command", text)
        self.assertIn("CheckSVar$ Count$ColorsColorIdentity", text)
        self.assertIn("SVarCompare$ EQ0", text)
        self.assertIn("GameActivationLimit$ 1", text)
        self.assertIn("Origin$ Library", text)
        self.assertIn("Destination$ Battlefield", text)
        self.assertIn("ChangeType$ Card.named亚西克瑞非体质+YouOwn", text)
        self.assertIn("Reveal$ True", text)
        self.assertIn("Shuffle$ True", text)

    def test_edition_art_backup_and_chinese_text(self):
        self.assertIn("63 M 亚西克瑞非体质 @Custom", EDITION.read_text(encoding="utf-8"))
        self.assertTrue(BACKUP.is_file(), BACKUP)
        self.assertTrue(ART.is_file(), ART)
        with Image.open(ART) as image:
            self.assertEqual("RGB", image.mode)
            self.assertEqual("JPEG", image.format)
            self.assertAlmostEqual(1.37, image.width / image.height, places=2)
        translation = (
            "亚西克瑞非体质|亚西克瑞非体质|结界|"
            "只要你的总生命为1或更多，你便不会输掉游戏，且你的对手不会赢得游戏。\\n"
            "只要你的每位指挥官的颜色标识均为无色，你可以于你能施放瞬间的时机，从你的牌库中展示此牌并将它放进战场。若你如此作，洗牌。每盘游戏只能如此作一次。"
        )
        self.assertIn(translation, ZH_CN.read_text(encoding="utf-8").splitlines())


if __name__ == "__main__":
    unittest.main()
```

- [x] **Step 2: Run the target test and verify RED**

Run: `python -m unittest tests.test_yaxikeruifei_constitution -v`

Expected: three errors/failures because the script, edition row, artwork, backup, and translation do not exist yet.

### Task 2: Implement the card, registration, localization, and art

**Files:**
- Create: `custom/cards/colorless/亚西克瑞非体质.txt`
- Modify: `custom/editions/Placeholder_Set.txt`
- Modify: `forge-gui/res/languages/cardnames-zh-CN.txt`
- Create: `custom/tools/card-artwork/codex-clipboard-cb06b767-f050-407f-9938-bb8851b8004c.png`
- Create: `custom/cards/pictures/PH01/亚西克瑞非体质.artcrop.jpg`

- [x] **Step 1: Add the minimal DSL implementation**

```text
Name:亚西克瑞非体质
ManaCost:0
Types:Enchantment
R:Event$ GameLoss | ActiveZones$ Battlefield | ValidPlayer$ You | Layer$ CantHappen | CheckSVar$ Count$YourLifeTotal | SVarCompare$ GE1 | Description$ As long as your life total is 1 or more, you can't lose the game and your opponents can't win the game.
R:Event$ GameWin | ActiveZones$ Battlefield | ValidPlayer$ Opponent | Layer$ CantHappen | CheckSVar$ Count$YourLifeTotal | SVarCompare$ GE1 | Secondary$ True | Description$ As long as your life total is 1 or more, you can't lose the game and your opponents can't win the game.
T:Mode$ NewGame | TriggerZones$ Hand,Library | ResolveBeforeFirstTurn$ True | Execute$ CreateLibraryAccess | Static$ True | TriggerDescription$ At the beginning of the game, prepare CARDNAME's library ability.
SVar:CreateLibraryAccess:DB$ Effect | Name$ 亚西克瑞非体质 Library Access | EffectOwner$ You | Abilities$ EnterFromLibrary | Duration$ Permanent | Unique$ True | ConditionPresent$ Card.IsCommander+YouOwn | PresentZone$ Command | ConditionCompare$ GE1
SVar:EnterFromLibrary:AB$ ChangeZone | Cost$ 0 | ActivationZone$ Command | CheckSVar$ Count$ColorsColorIdentity | SVarCompare$ EQ0 | IsPresent$ Card.named亚西克瑞非体质+YouOwn | PresentZone$ Library | PresentCompare$ GE1 | GameActivationLimit$ 1 | Origin$ Library | Destination$ Battlefield | ChangeType$ Card.named亚西克瑞非体质+YouOwn | ChangeNum$ 1 | Reveal$ True | Shuffle$ True | SpellDescription$ Reveal a card named 亚西克瑞非体质 from your library and put it onto the battlefield, then shuffle. Activate only if each of your commanders has a colorless color identity and only once each game.
Oracle:只要你的总生命为1或更多，你便不会输掉游戏，且你的对手不会赢得游戏。\n只要你的每位指挥官的颜色标识均为无色，你可以于你能施放瞬间的时机，从你的牌库中展示此牌并将它放进战场。若你如此作，洗牌。每盘游戏只能如此作一次。
```

- [x] **Step 2: Register PH01 collector number 63 and add the exact zh-CN row**

Append `63 M 亚西克瑞非体质 @Custom` to the `[cards]` section and add the exact user wording as the card's `cardnames-zh-CN.txt` row.

- [x] **Step 3: Back up and crop the supplied pure artwork**

Copy the source PNG byte-for-byte into `tools/card-artwork/`, then center-crop it to approximately 1.37:1 without removing the face and hands, convert to RGB, and save as a high-quality JPEG at `cards/pictures/PH01/亚西克瑞非体质.artcrop.jpg`.

- [x] **Step 4: Run the target test and card lint for GREEN**

Run:

```powershell
python -m unittest tests.test_yaxikeruifei_constitution -v
python tools/lint_card.py cards/colorless/亚西克瑞非体质.txt
```

Expected: all three target tests pass and lint reports no errors.

### Task 3: Document, verify, deploy, and save locally

**Files:**
- Modify: `custom/CARDS.md`
- Modify: `custom/VERIFICATION.md`

- [x] **Step 1: Add the card to the gameplay table**

Add PH01 #63 with `{0}` enchantment characteristics, life-gated loss/win prevention, and the once-per-game colorless-commander library deployment summary.

- [x] **Step 2: Run fresh full verification**

Run:

```powershell
python -m unittest discover -s tests -p "test_*.py"
python tools/lint_card.py cards/colorless/亚西克瑞非体质.txt
powershell -ExecutionPolicy Bypass -File .\tools\install_to_forge.ps1
```

Expected: the full DIY suite passes, lint reports no errors, and the installer syncs card, edition, and artwork.

- [x] **Step 3: Verify deployed bytes and record evidence**

Compare SHA-256 for the source/deployed script, edition, and art; record test counts, dimensions, hashes, deployment status, and remaining GUI checks in `VERIFICATION.md`.

- [x] **Step 4: Review the diff and create a local commit**

Run:

```powershell
git diff --check
git status --short
git add custom/cards/colorless/亚西克瑞非体质.txt custom/tests/test_yaxikeruifei_constitution.py custom/editions/Placeholder_Set.txt custom/cards/pictures/PH01/亚西克瑞非体质.artcrop.jpg custom/tools/card-artwork/codex-clipboard-cb06b767-f050-407f-9938-bb8851b8004c.png custom/CARDS.md custom/VERIFICATION.md custom/docs/superpowers/plans/2026-07-15-yaxikeruifei-constitution.md forge-gui/res/languages/cardnames-zh-CN.txt
git commit -m "Add 亚西克瑞非体质"
```

Expected: a local commit is created; no push is performed because this is a pure DSL/card-content change.
