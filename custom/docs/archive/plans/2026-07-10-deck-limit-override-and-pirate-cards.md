# Deck Limit Override and Pirate Cards Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a reusable Forge deck-limit override keyword and implement `test_解除构筑限制`, `海盗帕奇斯`, and `突牙` with verified DIY scripts.

**Architecture:** Forge Core recognizes a main-deck card carrying `IgnoreDeckLimits` and changes only main-deck size and duplicate-count validation. The three card definitions remain in the DIY DSL; Python contract tests verify their exact script structure, while focused Java tests verify keyword parsing and override policy before the production validator changes.

**Tech Stack:** Java 17, Maven, Forge card DSL, Python `unittest`, PowerShell.

---

## File map

- Modify `D:/Forge/forge-master/forge-master/forge-game/src/main/java/forge/game/keyword/Keyword.java`: register the reusable keyword.
- Modify `D:/Forge/forge-master/forge-master/forge-core/src/main/java/forge/deck/DeckFormat.java`: detect the keyword in the main deck and apply size/copy override policy.
- Create `D:/Forge/forge-master/forge-master/forge-core/src/test/java/forge/deck/DeckFormatIgnoreDeckLimitsTest.java`: focused Java policy tests.
- Modify `D:/Forge/forge-master/forge-master/forge-core/pom.xml`: add test-scoped JUnit only if inherited test dependencies are unavailable.
- Create three scripts under `D:/Forge/forge-diy/cards/colorless/` and `D:/Forge/forge-diy/cards/red/`.
- Modify `D:/Forge/forge-diy/editions/Placeholder_Set.txt`: add collector numbers 9–11.
- Create `D:/Forge/forge-diy/tests/test_deck_limit_override_cards.py`: card and edition contract tests.
- Modify `D:/Forge/forge-diy/reference/keyword_abilities.md`, `test_cards.md`, and `memory.md`: document verified behavior after implementation.

### Task 1: Forge keyword and deck-policy tests

- [ ] **Step 1: Write failing Java tests**

Create `forge-core/src/test/java/forge/deck/DeckFormatIgnoreDeckLimitsTest.java`. Build `CardRules` with `CardRules.fromScript`, wrap them in `PaperCard`, and assert the wished-for helpers:

```java
package forge.deck;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.item.PaperCard;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeckFormatIgnoreDeckLimitsTest {
    private static PaperCard card(String name, String... keywords) {
        var script = new java.util.ArrayList<>(List.of(
                "Name:" + name, "ManaCost:0", "Types:Artifact"));
        for (String keyword : keywords) script.add("K:" + keyword);
        return new PaperCard(CardRules.fromScript(script), "TST", CardRarity.Common);
    }

    @Test
    void recognizesOverrideOnlyFromMainDeck() {
        CardPool main = new CardPool();
        main.add(card("Override", "IgnoreDeckLimits"), 1);
        assertTrue(DeckFormat.hasDeckLimitOverride(main));
        assertFalse(DeckFormat.hasDeckLimitOverride(new CardPool()));
    }

    @Test
    void overridePolicySetsMinimumToOneAndSkipsCopyLimits() {
        assertEquals(1, DeckFormat.effectiveMainDeckMinimum(60, true));
        assertEquals(60, DeckFormat.effectiveMainDeckMinimum(60, false));
        assertFalse(DeckFormat.shouldEnforceCardCopyLimits(true));
        assertTrue(DeckFormat.shouldEnforceCardCopyLimits(false));
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run from Forge root:

```powershell
./mvnw -pl forge-core -Dtest=DeckFormatIgnoreDeckLimitsTest test
```

Expected: compilation failure because the three `DeckFormat` helper methods do not exist.

- [ ] **Step 3: Register the keyword and implement the policy helpers**

Add a `SimpleKeyword` entry to `Keyword.java`:

```java
IGNORE_DECK_LIMITS("IgnoreDeckLimits", SimpleKeyword.class, true,
        "Your main deck may contain any number of cards and any number of cards with the same name."),
```

Add package-visible static helpers to `DeckFormat.java` which inspect only `deck.getMain()` and use exact keyword membership. Compute `ignoreDeckLimits` once in `getDeckConformanceProblem`, set `min` to one when true, skip the maximum-size rejection when true, and wrap the duplicate-count loop in `shouldEnforceCardCopyLimits(ignoreDeckLimits)`. Preserve all legality checks between those sections.

- [ ] **Step 4: Run the focused test and Forge Core tests to verify GREEN**

```powershell
./mvnw -pl forge-core -Dtest=DeckFormatIgnoreDeckLimitsTest test
./mvnw -pl forge-core test
```

Expected: PASS with no compilation errors.

### Task 2: DIY card contract tests

- [ ] **Step 1: Write failing Python tests**

Create `tests/test_deck_limit_override_cards.py` with separate tests asserting:

```python
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
OVERRIDE = ROOT / "cards" / "colorless" / "test_解除构筑限制.txt"
PATCHES = ROOT / "cards" / "red" / "海盗帕奇斯.txt"
TUSK = ROOT / "cards" / "red" / "突牙.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"

class DeckLimitOverrideCardsTest(unittest.TestCase):
    def test_override_card_contract(self):
        script = OVERRIDE.read_text(encoding="utf-8")
        self.assertIn("Name:test_解除构筑限制", script)
        self.assertIn("ManaCost:0", script)
        self.assertIn("Types:Artifact", script)
        self.assertIn("K:IgnoreDeckLimits", script)

    def test_patches_contract(self):
        script = PATCHES.read_text(encoding="utf-8")
        for value in ("ManaCost:R", "Types:Legendary Creature Pirate Demon", "PT:1/1", "K:Haste", "K:DeckLimit:1", "TriggerZones$ Hand,Library", "ValidCard$ Pirate.YouCtrl", "DB$ ChangeZoneAll"):
            self.assertIn(value, script)
        self.assertNotIn("Shuffle$ True", script)

    def test_tusk_contract(self):
        script = TUSK.read_text(encoding="utf-8")
        for value in ("ManaCost:2 R R", "Types:Legendary Creature Beast", "PT:3/3", "K:Haste", "K:DeckLimit:1", "Mode$ DamageDoneOnce", "wasDealtDamageThisTurn", "SVarCompare$ GE3", "DB$ ChangeZoneAll"):
            self.assertIn(value, script)
        self.assertNotIn("Shuffle$ True", script)

    def test_edition_entries(self):
        edition = EDITION.read_text(encoding="utf-8")
        self.assertIn("9 C test_解除构筑限制", edition)
        self.assertIn("10 M 海盗帕奇斯", edition)
        self.assertIn("11 M 突牙", edition)
```

- [ ] **Step 2: Run the contract tests and verify RED**

```powershell
python -m unittest tests.test_deck_limit_override_cards -v
```

Expected: FAIL because all three card files are absent.

### Task 3: Implement the three card scripts

- [ ] **Step 1: Implement `test_解除构筑限制` minimally**

Create `cards/colorless/test_解除构筑限制.txt` with the approved name, zero cost, Artifact type, `K:IgnoreDeckLimits`, and matching Oracle text.

- [ ] **Step 2: Implement `海盗帕奇斯` from verified zone-change patterns**

Create `cards/red/海盗帕奇斯.txt` with the approved characteristics. Use a `ChangesZone` trigger active in `Hand,Library`, `ValidCard$ Pirate.YouCtrl`, and a hand-to-battlefield `ChangeZoneAll` chained to a library-to-battlefield `ChangeZoneAll`. Gate each movement with an `IsPresent`/`PresentZone` condition supported by current Forge scripts. Do not add `Shuffle$` or player choices.

- [ ] **Step 3: Implement `突牙` using existing damage history**

Create `cards/red/突牙.txt` with the approved characteristics. Use `DamageDoneOnce`, `ValidTarget$ You,Permanent.YouCtrl`, and `TriggerZones$ Hand,Library`. Define a count expression equal to controlled battlefield permanents with `wasDealtDamageThisTurn` plus the controller when that player was dealt damage this turn; require `GE3`. Chain guarded hand and library `ChangeZoneAll` effects without shuffle.

- [ ] **Step 4: Add edition entries and verify GREEN**

Append collector numbers 9–11 to `editions/Placeholder_Set.txt`, then run:

```powershell
python -m unittest tests.test_deck_limit_override_cards -v
```

Expected: PASS.

### Task 4: Lint, regression tests, and documentation

- [ ] **Step 1: Run all three linters**

```powershell
python tools/lint_card.py cards/colorless/test_解除构筑限制.txt
python tools/lint_card.py cards/red/海盗帕奇斯.txt
python tools/lint_card.py cards/red/突牙.txt
```

Expected: each reports `[SUCCESS] No errors found!`; warnings must be reviewed rather than ignored.

- [ ] **Step 2: Run the full DIY regression suite**

```powershell
python -m unittest discover -s tests -v
```

Expected: all tests PASS.

- [ ] **Step 3: Update project documentation**

Add `IgnoreDeckLimits` to `reference/keyword_abilities.md`; add concise entries for test6/解除构筑限制, 海盗帕奇斯, and 突牙 to `test_cards.md`; update `memory.md` with implemented and verified state only.

- [ ] **Step 4: Re-run tests after documentation changes**

Repeat the three lint commands and the full Python suite. Expected: all PASS.

### Task 5: Build and runtime packaging

- [ ] **Step 1: Compile the affected Forge modules**

From Forge root:

```powershell
./mvnw -pl forge-core,forge-game,forge-gui-desktop -am -DskipTests package
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Identify the installed desktop JAR before replacement**

Use the existing Forge installation path and compare artifact name, timestamp, and hash. Back up the active JAR before replacement because this is direct runtime file surgery.

- [ ] **Step 3: Install the tested runtime and DIY files**

Copy the built desktop artifact into the verified Forge installation, then run:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\install_to_forge.ps1
```

Expected: the three card scripts and edition file synchronize successfully.

- [ ] **Step 4: Perform final verification**

Re-run focused Java tests, full Python tests, and linters; then launch Forge and verify deck construction with one解除牌, multiple帕奇斯/突牙, and a control deck without the解除牌. Confirm both boarding abilities in Developer Mode, including repeated damage to one object not increasing the distinct count.
