# Forge DIY Workspace Verification Log

This document records the verification status and updates for custom cards developed in this workspace.

---

## 📅 2026-07-10: Minimal Loading & Rule Breaking Verification

### 1. Custom Card Loading Verification
- **Card Name**: `test1`
- **Script Path**: [cards/colorless/test1.txt](file:///d:/Forge/forge-diy/cards/colorless/test1.txt)
- **Edition Registration**: Added to [editions/Placeholder_Set.txt](file:///d:/Forge/forge-diy/editions/Placeholder_Set.txt) as card #2.
- **Verification Method**: Launched Forge game client, opened the Deck Editor, and verified that `test1` appears in the card database under "Placeholder Set" (Set Code: `PH01`).
- **Result**: **`PASSED`** (The custom card is successfully read and loaded by the game engine).

### 2. Rule Breaking Verification (Arbitrary Deck Count Limit Bypass)
- **Objective**: Test if custom cards can implement game rule-breaking keywords.
- **Implementation**: Added the keyword `K:A deck can have any number of cards named CARDNAME.` to the card script.
- **Linter Status**: Checked via `tools/lint_card.py` and returned 0 syntax errors.
- **Gameplay Verification**: Synchronized files to Forge and verified in the Deck Editor that more than 4 copies of `test1` can be added to a single deck.
- **Result**: **`PASSED`** (The keyword is successfully parsed and correctly bypasses the standard 4-card rule limit).

### 3. Cascade-style Summoning Verification
- **Card Name**: `test2`
- **Script Path**: [cards/colorless/test2.txt](file:///d:/Forge/forge-diy/cards/colorless/test2.txt)
- **Features**:
  - Mana Cost: `0`
  - Types: `Artifact`
  - Limit Bypass: `K:A deck can have any number of cards named CARDNAME.`
  - Trigger Logic: Uses `ChangesZone` trigger combined with `Card.wasCastFromYourHandByYou+Self` restriction to prevent infinite recursion, chaining two `ChangeZoneAll` sub-abilities to put all `test2` cards from Hand and Library onto the battlefield (and shuffles the library).
- **Linter Status**: Checked via `tools/lint_card.py` and returned 0 syntax errors.
- **Verification Method**: Synchronized files to Forge, added 10 copies of `test2` to a deck, launched Developer Mode, and cast one `test2` from hand.
- **Result**: **`PENDING_GAMEPLAY`** (Successfully coded and linted; ready to test in-game).

### 4. Global Passive Static Effect Verification
- **Card Name**: `test3`
- **Script Path**: [cards/colorless/test3.txt](file:///d:/Forge/forge-diy/cards/colorless/test3.txt)
- **Features**:
  - Mana Cost: `0`
  - Types: `Legendary Artifact`
  - Limit Bypass: `K:A deck can have any number of cards named CARDNAME.`
  - Continuous Effect: Uses `IgnoreLegendRule` mode combined with `EffectZone$ All` and `ValidCard$ Permanent.YouCtrl` to bypass the legend rule for all of your permanents, regardless of which zone `test3` is in (e.g. even if it remains in your library).
- **Linter Status**: Checked via `tools/lint_card.py` and returned 0 syntax errors.
- **Verification Method**: Synchronized files to Forge, added `test3` and multiple copies of another legendary permanent (e.g., `Mox Amber` or `Black Lotus` if modified to be legendary, or any standard legendary creature) to a deck. Start a match, keep `test3` in library, and cast two copies of the same legendary permanent to verify that they both remain on the battlefield.
- **Result**: **`PENDING_GAMEPLAY`** (Successfully coded and linted; ready to test in-game).

### 5. Automated Library Draw & Game Win Verification
- **Card Name**: `test4`
- **Script Path**: [cards/colorless/test4.txt](file:///d:/Forge/forge-diy/cards/colorless/test4.txt)
- **Features**:
  - Mana Cost: `0`
  - Types: `Artifact`
  - Limit Bypass: `K:A deck can have any number of cards named CARDNAME.`
  - Auto-Draw Effect: Uses a `ChangesZone` trigger starting from `Origin$ Library` and ending in `Destination$ Any`, checking if the card is in the `Library` zone (`TriggerZones$ Library`). It evaluates the count of non-`test4` cards remaining in the library (`Count$ValidLibrary Card.notnamedtest4`). If the count of other cards is `0`, it triggers `ChangeZone` to put `test4` (`Defined$ Self`) into the hand. If it is already in the hand, the trigger automatically becomes inactive because the zone restriction is violated.
  - Win Condition Effect: Uses a standard ETB `ChangesZone` trigger. It evaluates the sum of non-`test4` cards in hand and library (`Count$ValidHand Card.notnamedtest4/Plus.Count$ValidLibrary Card.notnamedtest4`). If this sum is `0`, it executes `WinsGame` to immediately award victory.
- **Linter Status**: Checked via `tools/lint_card.py` and returned 0 syntax errors.
- **Verification Method**: Synchronized files to Forge, built a deck containing `test4` and a few other cards. Draw or mill other cards until only `test4` cards are left in the library. Verify that `test4` automatically moves to the hand. Play `test4` onto the battlefield and verify that when you control `test4` and have no other cards in hand or library, you win the game.
- **Result**: **`PENDING_GAMEPLAY`** (Successfully coded and linted; ready to test in-game).

### 6. Starting Hand Guarantee & High Mana Output Verification
- **Card Name**: `test5`
- **Script Path**: [cards/colorless/test5.txt](file:///d:/Forge/forge-diy/cards/colorless/test5.txt)
- **Features**:
  - Mana Cost: `0`
  - Types: `Artifact`
  - Mana Ability: Uses `Combo Any` with `Amount$ 100` to add 100 mana in any combination of colors upon tapping.
  - Starting Hand Trigger: Uses `NewGame` trigger with `TriggerZones$ Library` to search the library and put `test5` (`Defined$ Self`) into the hand before the first turn begins. This ensures a 100% guarantee of starting with `test5` in hand (if it is already in the opening hand, the trigger remains inactive).
- **Linter Status**: Checked via `tools/lint_card.py` and returned 0 syntax errors.
- **Verification Method**: Synchronized files to Forge, built a deck containing `test5` and other cards. Verify in the Deck Editor and during a game that `test5` is always in your opening hand. Verify that tapping `test5` adds 100 mana and allows you to choose any color combination to pay for spells.
- **Result**: **`PENDING_GAMEPLAY`** (Successfully coded and linted; ready to test in-game).

---

## 🛠️ Verification Environment
- **Forge Path**: `d:\Forge\forge-master\forge-master\`
- **Workspace Path**: `d:\Forge\forge-diy\`
- **Installer Tool**: `tools/install_to_forge.ps1` (Copies workspace directories to `%APPDATA%\Forge\custom\`)
