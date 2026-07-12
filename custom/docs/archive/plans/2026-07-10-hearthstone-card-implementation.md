# 炉石传说 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the reusable `DeckMinimum:N` construction keyword and the custom card “炉石传说”, whose start-of-game sequence establishes the approved Hearthstone rules.

**Architecture:** Forge Core determines the effective main-deck lower bound from custom card keywords, with `IgnoreDeckLimits` taking absolute precedence. The new card stays declarative: its NewGame trigger reveals the card, sets every player's life, creates one command-zone emblem per player, and then exiles itself. The emblem uses its own counter and Forge's existing persistent-mana support.

**Tech Stack:** Java 21 / Maven / JUnit 5 in Forge Core and Game; Forge card-script DSL; Python unittest custom-card contracts.

---

## File map

- `D:\Forge\forge-master\forge-master\forge-core\src\main\java\forge\deck\DeckFormat.java` — derive the non-Commander effective main-deck lower bound from main-deck card keywords.
- `D:\Forge\forge-master\forge-master\forge-core\src\test\java\forge\deck\DeckFormatIgnoreDeckLimitsTest.java` — regression tests for default, custom minimum, max-of-many, and override precedence.
- `D:\Forge\forge-master\forge-master\forge-game\src\main\java\forge\game\keyword\Keyword.java` — register `DeckMinimum` as a numeric keyword for script parsing and display.
- `D:\Forge\forge-diy\cards\colorless\炉石传说.txt` — custom card script, its start-of-game chain, and emblem definition.
- `D:\Forge\forge-diy\editions\Placeholder_Set.txt` — register card number 14.
- `D:\Forge\forge-diy\tools\lint_card.py` — reject malformed/non-positive `DeckMinimum` declarations before deployment.
- `D:\Forge\forge-diy\tests\test_hearthstone_card.py` — text-level contract for the card, edition registration, and exact sequence constraints.
- `%APPDATA%\Forge\custom\cards\colorless\炉石传说.txt` and `%APPDATA%\Forge\custom\editions\Placeholder_Set.txt` — deployed copies after source tests pass.

### Task 1: Add construction-minimum behavior in Forge Core

**Files:**
- Modify: `D:\Forge\forge-master\forge-master\forge-core\src\test\java\forge\deck\DeckFormatIgnoreDeckLimitsTest.java`
- Modify: `D:\Forge\forge-master\forge-master\forge-core\src\main\java\forge\deck\DeckFormat.java`

- [ ] **Step 1: Write failing Core tests**

Extend the test helper's existing `card(name, keywords...)` usage and add these tests:

```java
@Test
void deckMinimumUsesTheLargestPositiveMainDeckKeyword() {
    final CardPool main = new CardPool();
    main.add(card("Thirty one", "DeckMinimum:31"), 1);
    main.add(card("Forty", "DeckMinimum:40"), 1);

    assertEquals(40, DeckFormat.getMainDeckMinimum(60, main, false));
}

@Test
void ignoreDeckLimitsWinsOverDeckMinimum() {
    final CardPool main = new CardPool();
    main.add(card("Hearthstone", "DeckMinimum:31"), 1);
    main.add(card("Override", "IgnoreDeckLimits"), 1);

    assertEquals(1, DeckFormat.getMainDeckMinimum(60, main, true));
}

@Test
void deckMinimumDoesNotAffectCommanderFormats() {
    final Deck deck = new Deck("Commander test");
    deck.getMain().add(card("Hearthstone", "DeckMinimum:31"), 31);
    deck.setCommander(card("Commander", ""));

    assertEquals("should have at least 99 cards", DeckFormat.Commander.getDeckConformanceProblem(deck));
}
```

Replace the old two-argument `effectiveMainDeckMinimum` expectation with an assertion that no custom keyword leaves the original 60-card lower bound unchanged. Keep the existing copy-limit assertions.

- [ ] **Step 2: Run the focused Core test and confirm failure**

Run:

```powershell
mvn -pl forge-core -Dtest=DeckFormatIgnoreDeckLimitsTest test
```

Expected: compilation failure because `getMainDeckMinimum` does not exist.

- [ ] **Step 3: Implement safe keyword scanning and precedence**

In `DeckFormat.java`, add `private static final String DECK_MINIMUM = "DeckMinimum";`. Replace the direct minimum calculation in `getDeckConformanceProblem` with:

```java
final CardPool mainDeck = deck.getMain();
final boolean ignoreDeckLimits = !hasCommander() && hasDeckLimitOverride(mainDeck);
int min = hasCommander()
        ? getMainRange().getMinimum()
        : getMainDeckMinimum(getMainRange().getMinimum(), mainDeck, ignoreDeckLimits);
```

Add package-visible helpers:

```java
static int getMainDeckMinimum(final int normalMinimum, final CardPool mainDeck,
                              final boolean ignoreDeckLimits) {
    if (ignoreDeckLimits) {
        return 1;
    }
    int minimum = normalMinimum;
    if (mainDeck == null) {
        return minimum;
    }
    for (final Entry<PaperCard, Integer> entry : mainDeck) {
        for (final String keyword : entry.getKey().getRules().getMainPart().getKeywords()) {
            final String prefix = DECK_MINIMUM + ":";
            if (!keyword.startsWith(prefix)) {
                continue;
            }
            try {
                final int requested = Integer.parseInt(keyword.substring(prefix.length()));
                if (requested > 0) {
                    minimum = Math.max(minimum == normalMinimum ? requested : minimum, requested);
                }
            } catch (NumberFormatException ignored) {
                // Malformed custom data is rejected by the DIY linter; do not crash deck validation.
            }
        }
    }
    return minimum;
}
```

Correct the initialization so a first valid `DeckMinimum` replaces rather than merely raises the format default: track `Integer requestedMinimum = null`, set it to `Math.max(requestedMinimum == null ? requested : requestedMinimum, requested)`, and return `requestedMinimum == null ? normalMinimum : requestedMinimum`. Retain `effectiveMainDeckMinimum` only if existing callers require it, delegating to the new policy for the override case.

- [ ] **Step 4: Run focused Core tests**

Run:

```powershell
mvn -pl forge-core -Dtest=DeckFormatIgnoreDeckLimitsTest test
```

Expected: all tests pass.

### Task 2: Register and validate `DeckMinimum`

**Files:**
- Modify: `D:\Forge\forge-master\forge-master\forge-game\src\main\java\forge\game\keyword\Keyword.java`
- Modify: `D:\Forge\forge-diy\tools\lint_card.py`
- Modify: `D:\Forge\forge-diy\tests\test_lint_card.py`

- [ ] **Step 1: Write failing DIY linter tests**

Add one positive temporary script containing `K:DeckMinimum:31`, plus negative scripts containing `K:DeckMinimum`, `K:DeckMinimum:zero`, and `K:DeckMinimum:0`. Assert the positive script lints and each negative script returns failure with `DeckMinimum` in its diagnostic.

- [ ] **Step 2: Run the linter tests and confirm failure**

Run:

```powershell
python -m unittest tests.test_lint_card -v
```

Expected: malformed `DeckMinimum` scripts are currently accepted.

- [ ] **Step 3: Add parser registration and lint rule**

Add this enum member near `IGNORE_DECK_LIMITS`:

```java
DECK_MINIMUM("DeckMinimum", KeywordWithAmount.class, true,
        "Your main deck must contain at least %d cards."),
```

In `lint_card.py`'s `key == "K"` branch, use this exact additional validation:

```python
if val.startswith("DeckMinimum:") or val == "DeckMinimum":
    parts = val.split(":", 1)
    if len(parts) != 2 or not parts[1].isdigit() or int(parts[1]) <= 0:
        errors.append(
            f"Line {line_num}: DeckMinimum must be a positive integer "
            "(for example, 'K:DeckMinimum:31')."
        )
```

- [ ] **Step 4: Run the linter tests**

Run:

```powershell
python -m unittest tests.test_lint_card -v
```

Expected: all linter tests pass.

### Task 3: Add the “炉石传说” DSL card and source contracts

**Files:**
- Create: `D:\Forge\forge-diy\cards\colorless\炉石传说.txt`
- Create: `D:\Forge\forge-diy\tests\test_hearthstone_card.py`
- Modify: `D:\Forge\forge-diy\editions\Placeholder_Set.txt`

- [ ] **Step 1: Write the failing card contract**

Create `test_hearthstone_card.py` with assertions for the metadata (`Name:炉石传说`, `ManaCost:0`, `Types:Artifact`, `K:DeckMinimum:31`, and the fully formed `DeckLimit:1` text), the PH01 entry `14 M 炉石传说`, and the required strings below. Assert their offsets are strictly ordered: `MoveToLibrary`, `RevealSelf`, `SetAllLife`, `MakeEmblems`, `ExileSelf`.

```python
required = (
    "T:Mode$ NewGame | TriggerZones$ Hand,Library | Execute$ MoveToLibrary",
    "SVar:MoveToLibrary:DB$ ChangeZone | Defined$ Self | Origin$ Hand | Destination$ Library | SubAbility$ RevealSelf",
    "SVar:RevealSelf:DB$ Reveal | RevealDefined$ Self | SubAbility$ SetAllLife",
    "SVar:SetAllLife:DB$ SetLife | Defined$ Player | LifeAmount$ 30 | SubAbility$ MakeEmblems",
    "SVar:MakeEmblems:DB$ RepeatEach | RepeatPlayers$ Player | RepeatSubAbility$ MakeEmblem | SubAbility$ ExileSelf",
    "SVar:MakeEmblem:DB$ Effect | EffectOwner$ Player.IsRemembered | Name$ Emblem — 炉石传说",
    "SVar:ManaTrigger:Mode$ Phase | Phase$ Upkeep | ValidPlayer$ You | TriggerZones$ Command | Execute$ AddManaCounter",
    "SVar:AddMana:DB$ Mana | Produced$ Combo Any | Amount$ X | PersistentMana$ True",
    "SVar:X:Count$Counters_MANA",
    "SVar:ExileSelf:DB$ ChangeZone | Defined$ Self | Origin$ Library | Destination$ Exile",
)
```

- [ ] **Step 2: Run the contract and confirm failure**

Run:

```powershell
python -m unittest tests.test_hearthstone_card -v
```

Expected: failure because the card file and edition entry do not yet exist.

- [ ] **Step 3: Create the card and edition entry**

Write the card using the exact SVar names and sequence asserted above. Give `MakeEmblem` the fields `Image$ emblem`, `Triggers$ ManaTrigger`, `Duration$ Permanent`, and `SubAbility$ DBCleanup`; make `AddManaCounter` put one `MANA` counter on `Self` before `AddMana`. Append exactly `14 M 炉石传说` to the PH01 `[cards]` section. Keep the Oracle text exactly:

```text
将对战规则改变为炉石传说！\n对战开始时：展示并放逐炉石传说
```

- [ ] **Step 4: Run card contract and DSL linter**

Run:

```powershell
python -m unittest tests.test_hearthstone_card -v
python tools/lint_card.py cards/colorless/炉石传说.txt
```

Expected: contract passes and the linter reports no errors.

### Task 4: Build, deploy, and verify the actual runtime payload

**Files:**
- Modify by build output only: `D:\Forge\forge-master\forge-master\forge-gui-desktop\forge-gui-desktop-2.0.02-SNAPSHOT-jar-with-dependencies.jar`
- Copy: `%APPDATA%\Forge\custom\cards\colorless\炉石传说.txt`
- Copy: `%APPDATA%\Forge\custom\editions\Placeholder_Set.txt`

- [ ] **Step 1: Build affected modules**

Run:

```powershell
mvn -pl forge-core -Dtest=DeckFormatIgnoreDeckLimitsTest test
mvn -pl forge-game test
mvn -pl forge-gui-desktop -am package -DskipTests
```

Expected: all commands exit 0. Do not modify the fat JAR until its build succeeds.

- [ ] **Step 2: Patch the desktop fat JAR with the freshly built classes**

Back up the current JAR once using a timestamped sibling filename, then inject the rebuilt Core/Game class files with `jar uf`; confirm the JAR lists `forge/deck/DeckFormat.class` and `forge/game/keyword/Keyword.class` after patching. Record both SHA-256 values in the implementation handoff.

- [ ] **Step 3: Sync and test deployed custom resources**

Copy the new card and updated edition file to `%APPDATA%\Forge\custom`, preserving UTF-8. Run:

```powershell
python -m unittest discover -s tests -v
python tools/lint_card.py "$env:APPDATA\Forge\custom\cards\colorless\炉石传说.txt"
```

Expected: all DIY tests pass and the deployed card lints cleanly.

- [ ] **Step 4: Manual desktop verification**

Restart Forge. Verify the card can be searched by `炉石传说`, is listed in PH01, and a Constructed deck with it validates at 31 cards but rejects 30; verify a deck also containing `test_解除构筑限制` validates at 1 card. Start a two-player match and confirm the reveal, both life totals at 30, one emblem per player, first upkeep mana = 1, second own upkeep mana = 2, mana survives phase changes, and it disappears at end turn cleanup.

## Plan self-review

Spec coverage is mapped: construction precedence (Task 1), parser/linter safety (Task 2), card sequence and emblem behavior (Task 3), deployment and runtime verification (Task 4). There are no placeholders or deferred implementation steps. The plan intentionally leaves existing `IgnoreDeckLimits`, Pirate Patches, Tusk, and localization overlay untouched except for the documented precedence test.

Because both workspaces are not Git repositories, omit the plan template's commit steps; do not initialize a repository merely for this work.
