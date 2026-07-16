# Durability Keyword and Viscidus Cavern Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add an engine-level `Durability:N` keyword and update 维希度斯的窟穴 to use durability and mithril counters with the supplied artwork.

**Architecture:** Register `Durability` as a numeric Forge keyword and expand it in `CardFactoryUtil` into an enters-with-durability-counters replacement effect plus a last-counter-removed sacrifice trigger. Keep the cavern-specific draw trigger and perpetual hand cost reduction in the card DSL, using the existing `Drawn`, `AnimateAll`, `ReduceCost`, and `Duration$ Perpetual` paths.

**Tech Stack:** Java 17, Forge Game/TestNG, Forge card-script DSL, Python `unittest`, Pillow image conversion, PowerShell deployment scripts, Maven.

---

### Task 1: Lock the durability engine contract with a failing test

**Files:**
- Create: `forge-game/src/test/java/forge/game/keyword/DurabilityTest.java`

- [x] **Step 1: Write the failing test**

```java
package forge.game.keyword;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.card.CounterEnumType;
import forge.game.replacement.ReplacementType;
import forge.game.trigger.TriggerType;
import forge.item.PaperCard;
import forge.util.Lang;
import forge.util.Localizer;
import forge.game.player.Player;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;

public class DurabilityTest {
    @BeforeClass
    public void initializeLocalization() {
        Lang.createInstance("en-US");
        Localizer.getInstance().initialize("en-US", "../forge-gui/res/languages");
    }

    @Test
    public void parsesDurabilityAmountAndCreatesItsRules() {
        final KeywordInterface keyword = Keyword.getInstance("Durability:2");
        Assert.assertEquals(keyword.getKeyword(), Keyword.DURABILITY);
        Assert.assertEquals(keyword.getAmount(), 2);

        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(), "Durability test"));
        final Player controller = new Player("Controller", game, 1);
        game.getPlayers().add(controller);
        final CardRules cardRules = new CardRules.Reader().readCard(List.of(
                "Name:Durability Fixture",
                "ManaCost:1",
                "Types:Artifact",
                "K:Durability:2"
        ), "Durability Fixture");
        final Card card = CardFactory.getCard(
                new PaperCard(cardRules, "TST", CardRarity.Common), controller, game);

        Assert.assertEquals(CounterEnumType.getType("DURABILITY"), CounterEnumType.DURABILITY);
        Assert.assertTrue(card.getReplacementEffects().stream()
                .anyMatch(replacement -> replacement.getMode() == ReplacementType.Moved
                        && "DURABILITY".equals(replacement.getOverridingAbility()
                                .getParam("CounterType"))
                        && "2".equals(replacement.getOverridingAbility()
                                .getParam("CounterNum"))));
        Assert.assertTrue(card.getTriggers().stream()
                .anyMatch(trigger -> trigger.getMode() == TriggerType.CounterRemoved
                        && "DURABILITY".equals(trigger.getParam("CounterType"))
                        && "0".equals(trigger.getParam("NewCounterAmount"))));
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run:

```powershell
mvn -s D:\Forge\forge-latest\.mvn\local-settings.xml -f D:\Forge\forge-latest\forge-game\pom.xml -Dtest=forge.game.keyword.DurabilityTest test
```

Expected: compilation fails because `Keyword.DURABILITY` and `CounterEnumType.DURABILITY` do not exist.

### Task 2: Implement the numeric keyword and counter types

**Files:**
- Modify: `forge-game/src/main/java/forge/game/keyword/Keyword.java`
- Modify: `forge-game/src/main/java/forge/game/card/CounterEnumType.java`
- Modify: `forge-game/src/main/java/forge/game/card/CardFactoryUtil.java`
- Modify: `forge-game/src/main/java/forge/game/card/Card.java`
- Modify: `custom/KEYWORDS.md`

- [x] **Step 1: Register the keyword and counters**

Add `DURABILITY("Durability", KeywordWithAmount.class, false, "This permanent enters with {%d:durability counter} on it. When the last durability counter is removed from it, sacrifice it.")` to `Keyword`, and add silver-toned `DURABILITY` and `MITHRIL` entries to `CounterEnumType`.

- [x] **Step 2: Expand the keyword into intrinsic rules**

In `CardFactoryUtil`, add a `Durability` trigger branch that creates:

```java
Mode$ CounterRemoved | TriggerZones$ Battlefield | ValidCard$ Card.Self | NewCounterAmount$ 0 | CounterType$ DURABILITY | Secondary$ True
DB$ Sacrifice | SacValid$ Self
```

Add a replacement branch for `Durability:<amount>` using `makeEtbCounter("etbCounter:DURABILITY:<amount>:no Condition:...")`.

- [x] **Step 3: Document the DSL**

Add a `Durability` section to `custom/KEYWORDS.md` stating the `K:Durability:N` syntax, enters-with counters behavior, and sacrifice-on-last-counter-removal behavior.

- [x] **Step 4: Run the focused Java test**

Run the Task 1 Maven command. Expected: `DurabilityTest` passes.

### Task 3: Update 维希度斯的窟穴 and its artwork test-first

**Files:**
- Modify: `custom/tests/test_viscidus_cavern.py`
- Modify: `custom/cards/colorless/维希度斯的窟穴.txt`
- Modify: `custom/CARDS.md`
- Modify: `forge-gui/res/languages/cardnames-zh-CN.txt`
- Create: `custom/tools/card-artwork/codex-clipboard-88a9bfc0-7f0b-4382-8d91-14336dee6cdb.png`
- Modify: `custom/cards/pictures/PH01/维希度斯的窟穴.artcrop.jpg`

- [x] **Step 1: Replace the old card contract with the new one**

Require `K:Durability:2`, a `Drawn` trigger that puts one `MITHRIL` counter on the cavern, and an activated `AnimateAll` ability with `Cost$ T SubCounter<4/MITHRIL>` that permanently grants hand nonland cards a `ReduceCost` static ability and then removes one `DURABILITY` counter. Require the exact new Chinese Oracle and the new source-art backup.

- [x] **Step 2: Run the Python contract and verify red**

```powershell
python -m unittest tests.test_viscidus_cavern -v
```

Expected: failures identify the old depletion/discard/draw implementation and missing new artwork backup.

- [x] **Step 3: Implement the card DSL and text**

Use these core lines:

```text
K:Durability:2
T:Mode$ Drawn | ValidCard$ Card.YouCtrl | TriggerZones$ Battlefield | Execute$ TrigMithril | TriggerDescription$ Whenever you draw a card, put a mithril counter on CARDNAME.
SVar:TrigMithril:DB$ PutCounter | Defined$ Self | CounterType$ MITHRIL | CounterNum$ 1
A:AB$ AnimateAll | Cost$ T SubCounter<4/MITHRIL> | Zone$ Hand | Duration$ Perpetual | ValidCards$ Card.YouOwn+nonLand | staticAbilities$ ReduceCost | SubAbility$ DBRemoveDurability
SVar:ReduceCost:Mode$ ReduceCost | ValidCard$ Card.Self | Type$ Spell | Amount$ 1 | EffectZone$ All | Description$ This spell costs {1} less to cast.
SVar:DBRemoveDurability:DB$ RemoveCounter | Defined$ Self | CounterType$ DURABILITY | CounterNum$ 1
```

- [x] **Step 4: Import and crop the supplied art**

Copy the untouched PNG to `custom/tools/card-artwork/`, then remove its white border and center-crop it to a 490×358 (about 1.37:1) RGB JPEG at `custom/cards/pictures/PH01/维希度斯的窟穴.artcrop.jpg` without generating or overlaying a card frame.

- [x] **Step 5: Run the card contract and lint**

```powershell
python -m unittest tests.test_viscidus_cavern -v
python tools/lint_card.py cards/colorless/维希度斯的窟穴.txt
```

Expected: all cavern tests pass and lint reports no errors.

### Task 4: Verify, package, deploy, and publish both repositories

**Files:**
- Modify: `custom/VERIFICATION.md`
- Update: `forge-gui-desktop/target/forge-gui-desktop-2.0.14-SNAPSHOT-jar-with-dependencies.jar`
- Update: matching managed files, resources, manifest, and `BUILD-ID` under `D:/Forge/forge-diy-runtime/app`

- [x] **Step 1: Run source regressions**

Run the focused Java test, complete `forge-game` tests, the cavern contract, single-card lint, and the complete custom Python suite.

- [x] **Step 2: Build the desktop aggregate JAR**

Install the updated `forge-game` artifact with the local settings file, then package `forge-gui-desktop`; verify the final JAR contains the updated `Keyword`, `CounterEnumType`, and `CardFactoryUtil` bytecode.

- [x] **Step 3: Deploy custom content and sync the runtime repository**

Run `custom/tools/install_to_forge.ps1`, copy the aggregate JAR and managed card/art/language files into `forge-diy-runtime`, update its manifest and `BUILD-ID`, and compare source/deployed/runtime hashes.

- [x] **Step 4: Record fresh evidence**

Prepend a dated entry to `custom/VERIFICATION.md` with exact test counts, build result, hashes, deployment state, and whether a Forge process was running.

- [x] **Step 5: Commit and publish**

Commit the engine/custom changes and push them to `diy-fork/diy`; commit runtime payload changes and push `forge-diy-runtime` to `origin/main`. Confirm both remote heads with `git ls-remote`.
