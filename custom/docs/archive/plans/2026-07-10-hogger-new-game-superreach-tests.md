# Hogger New-Game Trigger and Superreach Test Cards Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every `NewGame` trigger visible on the normal stack at the first upkeep, prevent Hogger from copying any Hogger, and add two deployable Superreach test creatures.

**Architecture:** Preserve Forge's existing post-mulligan held `NewGame` event and first-upkeep processing. Add one narrow trigger-routing predicate so only `NewGame` overrides `Static$ True` and enters the stack; keep all unrelated static triggers unchanged. Keep card behavior in Forge DSL and verify it with contract tests plus focused Java tests.

**Tech Stack:** Java 8+, TestNG, Maven 3.9.16, Forge card-script DSL, Python `unittest`, PowerShell deployment tooling.

---

## File Map

- Modify `D:\Forge\forge-master\forge-master\forge-game\src\main\java\forge\game\trigger\TriggerHandler.java`: route `NewGame` triggers to the stack.
- Create `D:\Forge\forge-master\forge-master\forge-game\src\test\java\forge\game\trigger\TriggerHandlerTest.java`: lock the new routing rule and unrelated-static regression behavior.
- Modify `D:\Forge\forge-master\forge-master\forge-game\src\test\java\forge\game\combat\CombatUtilTest.java`: exercise Superreach against the test attacker's evasion keywords.
- Modify `D:\Forge\forge-diy\cards\red\chainbreaker_hogger.txt`: exclude all Hogger cards and update visible text.
- Modify `D:\Forge\forge-diy\tests\test_chainbreaker_hogger.py`: enforce the exclusion and text contract.
- Create `D:\Forge\forge-diy\cards\colorless\test_superreach_1.txt`: defending Superreach test creature.
- Create `D:\Forge\forge-diy\cards\colorless\test_superreach_2.txt`: attacker with stacked blocking restrictions.
- Create `D:\Forge\forge-diy\tests\test_superreach_cards.py`: validate both scripts and edition registration.
- Modify `D:\Forge\forge-diy\editions\Placeholder_Set.txt`: add collector numbers 9 and 10.
- Modify `D:\Forge\forge-diy\memory.md`: record the completed behavior after verification.
- Patch `D:\Forge\forge-master\forge-master\forge-gui-desktop\forge-gui-desktop-2.0.02-SNAPSHOT-jar-with-dependencies.jar`: deploy verified engine classes.

### Task 1: NewGame Stack Routing

- [ ] **Step 1: Write the failing TestNG test**

Create `TriggerHandlerTest.java` with two tests. Construct triggers through `TriggerType.createTrigger` so their modes match production:

```java
package forge.game.trigger;

import java.util.HashMap;
import java.util.Map;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.game.card.Card;

public class TriggerHandlerTest {
    private static Trigger staticTrigger(final TriggerType type) {
        final Map<String, String> params = new HashMap<>();
        params.put("Static", "True");
        return type.createTrigger(params, new Card(1, null), true);
    }

    @Test
    public void staticNewGameTriggerUsesTheStack() {
        AssertJUnit.assertTrue(TriggerHandler.shouldUseStack(staticTrigger(TriggerType.NewGame)));
    }

    @Test
    public void unrelatedStaticTriggerStillResolvesImmediately() {
        AssertJUnit.assertFalse(TriggerHandler.shouldUseStack(staticTrigger(TriggerType.Phase)));
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
& 'C:\Users\Marsh\AppData\Local\Programs\Apache\Maven\apache-maven-3.9.16\bin\mvn.cmd' -f forge-game\pom.xml -Dtest=forge.game.trigger.TriggerHandlerTest test
```

Expected: compilation fails because `TriggerHandler.shouldUseStack(Trigger)` does not exist.

- [ ] **Step 3: Add the minimal routing predicate and use it**

In `TriggerHandler.java`, add a package-private helper:

```java
static boolean shouldUseStack(final Trigger trigger) {
    return !trigger.isStatic() || trigger.getMode() == TriggerType.NewGame;
}
```

Replace the static/immediate branch condition in `runSingleTriggerInternal`:

```java
if (!shouldUseStack(regtrig)) {
    if (wrapperAbility.getActivatingPlayer().getController().playTrigger(host, wrapperAbility, isMandatory)) {
        final Map<AbilityKey, Object> staticParams = AbilityKey.mapFromCard(host);
        staticParams.put(AbilityKey.SpellAbility, sa);
        game.getTriggerHandler().runTrigger(TriggerType.AbilityResolves, staticParams, false);
    }
} else {
    game.getStack().addSimultaneousStackEntry(wrapperAbility);
    game.getTriggerHandler().runTrigger(TriggerType.AbilityTriggered,
            TriggerAbilityTriggered.getRunParams(regtrig, wrapperAbility, runParams), false);
}
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the Step 2 command. Expected: 2 tests, 0 failures, build success.

### Task 2: Superreach Keyword Regression

- [ ] **Step 1: Extend the combat test before changing production code**

Add a test to `CombatUtilTest.java` that gives the attacker the keyword restrictions used by Test Superreach 2 and verifies Superreach bypasses them:

```java
@Test
public void superreachBlocksAnAttackerWithStackedEvasionKeywords() {
    final Card attacker = new Card(1, null);
    final Card blocker = new Card(2, null);
    attacker.addIntrinsicKeyword("Flying");
    attacker.addIntrinsicKeyword("Fear");
    attacker.addIntrinsicKeyword("Menace");
    attacker.addIntrinsicKeyword("Shadow");
    attacker.addIntrinsicKeyword("Landwalk:Land");
    attacker.addIntrinsicKeyword("Horsemanship");
    attacker.addIntrinsicKeyword("Skulk");
    blocker.addIntrinsicKeyword("Superreach");

    AssertJUnit.assertTrue(CombatUtil.canBlock(attacker, blocker, false));
}
```

- [ ] **Step 2: Run the focused combat test**

Run:

```powershell
& 'C:\Users\Marsh\AppData\Local\Programs\Apache\Maven\apache-maven-3.9.16\bin\mvn.cmd' -f forge-game\pom.xml -Dtest=forge.game.combat.CombatUtilTest test
```

Expected: the test passes with the existing Superreach implementation. If it fails, record the exact failing restriction, add one restriction at a time to isolate it, then make the smallest correction in `CombatUtil.java` without bypassing blocker-side or global restrictions.

### Task 3: Hogger Name Exclusion

- [ ] **Step 1: Change the Python contract test first**

Replace the old `DefinedName` assertions in `test_chainbreaker_hogger.py` with:

```python
self.assertIn(
    "DefinedName$ ValidLibrary Permanent.Legendary+YouOwn+notnamed破链灾星霍格 | Zone$ Library",
    script,
)
self.assertIn(
    "DefinedName$ ValidHand Permanent.Legendary+YouOwn+notnamed破链灾星霍格 | Zone$ Library",
    script,
)
self.assertIn("duplicate each other legendary permanent card", script)
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
python -m unittest tests.test_chainbreaker_hogger -v
```

Expected: failure because both filters and the text still include Hogger.

- [ ] **Step 3: Update Hogger's script**

Change both `DefinedName` filters to append `+notnamed破链灾星霍格`. Change `TriggerDescription` and `Oracle` from `each legendary permanent card` to `each other legendary permanent card`. Preserve `Mode$ NewGame`, `Static$ True`, and the library-first subability chain.

- [ ] **Step 4: Run the focused test and linter**

```powershell
python -m unittest tests.test_chainbreaker_hogger -v
python tools\lint_card.py cards\red\chainbreaker_hogger.txt
```

Expected: all Hogger tests pass and lint reports no errors.

### Task 4: Superreach Test Cards

- [ ] **Step 1: Write failing card contract tests**

Create `tests\test_superreach_cards.py` asserting both files exist, Test Superreach 1 is a 0-cost 10/10 with `K:Superreach`, Test Superreach 2 is a 0-cost 20/20 with all seven keywords and four `CantBlockBy` restrictions, and PH01 contains `9 C Test Superreach 1` and `10 C Test Superreach 2`.

- [ ] **Step 2: Run the focused test and verify RED**

```powershell
python -m unittest tests.test_superreach_cards -v
```

Expected: failure because the two scripts do not exist.

- [ ] **Step 3: Create Test Superreach 1**

Create `cards\colorless\test_superreach_1.txt`:

```text
Name:Test Superreach 1
ManaCost:0
Types:Creature
PT:10/10
K:Superreach
Oracle:Superreach
```

- [ ] **Step 4: Create Test Superreach 2**

Create `cards\colorless\test_superreach_2.txt`:

```text
Name:Test Superreach 2
ManaCost:0
Types:Creature
PT:20/20
K:Flying
K:Fear
K:Menace
K:Shadow
K:Landwalk:Land
K:Horsemanship
K:Skulk
S:Mode$ CantBlockBy | ValidAttacker$ Creature.Self | Description$ CARDNAME can't be blocked.
S:Mode$ CantBlockBy | ValidAttacker$ Creature.Self | ValidBlocker$ Creature.nonArtifact | Description$ CARDNAME can't be blocked except by artifact creatures.
S:Mode$ CantBlockBy | ValidAttacker$ Creature.Self | ValidBlocker$ Creature.powerLT20 | Description$ Creatures with power less than 20 can't block CARDNAME.
S:Mode$ CantBlockBy | ValidAttacker$ Creature.Self | ValidBlocker$ Creature.powerGT1 | Description$ Creatures with power greater than 1 can't block CARDNAME.
Oracle:Flying, fear, menace, shadow, landwalk, horsemanship, skulk\nCARDNAME can't be blocked.\nCARDNAME can't be blocked except by artifact creatures.\nCreatures with power less than 20 or greater than 1 can't block CARDNAME.
```

- [ ] **Step 5: Register collector numbers and verify GREEN**

Append these rows to PH01:

```text
9 C Test Superreach 1
10 C Test Superreach 2
```

Run:

```powershell
python -m unittest tests.test_superreach_cards -v
python tools\lint_card.py cards\colorless\test_superreach_1.txt
python tools\lint_card.py cards\colorless\test_superreach_2.txt
```

Expected: contract tests pass and both scripts lint without errors.

### Task 5: Full Verification and Deployment

- [ ] **Step 1: Run all source tests**

```powershell
& 'C:\Users\Marsh\AppData\Local\Programs\Apache\Maven\apache-maven-3.9.16\bin\mvn.cmd' -f forge-game\pom.xml test
python -m unittest discover -s tests -p 'test_*.py' -v
```

Expected: Maven build success with 0 failures and all DIY tests passing.

- [ ] **Step 2: Install all DIY assets**

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\install_to_forge.ps1
```

Verify SHA-256 equality between all three source scripts and their `%APPDATA%\Forge\custom\cards` copies.

- [ ] **Step 3: Patch the desktop runtime JAR**

Back up the current fat JAR to a new `.pre-newgame-stack.bak` sibling if that backup does not exist. Update the JAR with compiled `TriggerHandler.class` and any changed nested classes from `forge-game\target\classes`, preserving the existing Superreach classes.

- [ ] **Step 4: Verify deployed bytecode and startup**

Use `javap -private` on the fat JAR to confirm `TriggerHandler.shouldUseStack` and `CombatUtil.superreachApplies`. Start the desktop JAR with `javaw`, wait for Forge initialization, inspect `%APPDATA%\Forge\forge.log` for custom-card parser errors, then stop only the process started by this verification.

- [ ] **Step 5: Update project memory**

Replace the current Hogger memory entry with the verified exclusion, first-upkeep stack behavior, and both test-card names. Do not claim runtime success unless Step 4 evidence is clean.

## Repository Constraint

Neither `D:\Forge\forge-diy` nor `D:\Forge\forge-master\forge-master` is a Git repository. No commit steps can be executed; preserve explicit runtime JAR backups instead.
