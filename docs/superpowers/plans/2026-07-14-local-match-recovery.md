# Local Match Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make cancelled pre-commit spell/ability payments return control normally, guarantee that abandoned inputs replace stale payment UI, and turn any genuinely unrecoverable game-loop failure into a visible, clean match shutdown instead of an unresponsive board.

**Architecture:** Do not use `GameSnapshot` as a generic transaction mechanism: it does not preserve the complete stack, phase, card, player, or GUI state and is the source of the observed crash. Use the existing manual ability rollback for cancellations, repair input teardown so it always releases waiters and refreshes the proxy, and add one outer `HostedMatch` failure boundary that reports the original exception and schedules deterministic UI cleanup. Recoverable pre-commit cancellation remains in the existing game loop; unknown failures are not swallowed or resumed with corrupted state.

**Tech Stack:** Java 17, Swing, TestNG/JUnit 5 as already configured per module, Maven.

---

### Task 1: Make empty input queues actively clear stale prompts

**Files:**
- Modify: `forge-gui/src/main/java/forge/gamemodes/match/input/InputLockUI.java`
- Modify: `forge-gui/src/main/java/forge/gamemodes/match/input/InputQueue.java`
- Modify: `forge-gui/src/main/java/forge/gamemodes/match/input/InputSyncronizedBase.java`
- Test: `forge-gui/src/test/java/forge/gamemodes/match/input/InputLockUIRecoveryTest.java`
- Test: `forge-gui/src/test/java/forge/gamemodes/match/input/InputQueueRecoveryTest.java`

- [ ] Write `InputLockUIRecoveryTest.emptyQueueReplacementIsActive` and `newProxyInputInvalidatesOldReplacement`; construct the smallest real queue/proxy/controller fixture available and assert the synthetic lock is current by proxy identity, not by presence on the synchronized-input stack.
- [ ] Run `mvn -s .mvn/local-settings.xml -pl forge-gui -am "-Dtest=InputLockUIRecoveryTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` and record the expected failure caused by `InputLockUI.isActive()` comparing against `InputQueue.getInput()`.
- [ ] Change `InputLockUI.isActive()` to return `controller.getInputProxy().getInput() == this`, so the synthetic lock created for an empty stack remains active until the proxy replaces it.
- [ ] Write `InputQueueRecoveryTest.clearInputsStopsEveryInputAndLeavesQueueEmpty`, `clearEmptyQueueStillNotifiesObservers`, and a stop-failure test proving the latch/removal cleanup runs even if `onStop()` throws.
- [ ] Run the focused queue tests and record the expected failure caused by `clearInputs()` popping before `stop()` and `stop()` lacking `finally` cleanup.
- [ ] Change `InputQueue.clearInputs()` to repeatedly stop the current top input without pre-popping it; change `InputSyncronizedBase.stop()` so finished-state update, conditional removal, and latch release happen in a `finally` block. Preserve the original `onStop()` failure after cleanup.
- [ ] Re-run both focused test classes and commit the passing implementation.

### Task 2: Remove unsafe snapshot restore from normal ability cancellation

**Files:**
- Modify: `forge-game/src/main/java/forge/game/GameActionUtil.java`
- Test: `forge-game/src/test/java/forge/game/GameActionUtilRollbackTest.java`

- [ ] Build a minimal real `Game`, two `Player` objects, a battlefield card with an activated `{W},{T}`-style ability, and a `CostPayment`; enable `game.EXPERIMENTAL_RESTORE_SNAPSHOT`, then install a snapshot whose post-checkpoint state contains an extra battlefield object reproducing the logged `GameSnapshot.copyGameState()` null lookup.
- [ ] Write `cancelledAbilityUsesManualRollbackWhenExperimentalSnapshotsAreEnabled`: invoke `GameActionUtil.rollbackAbility(...)` and assert it does not throw, clears targets/frozen stack, refunds paid costs, and returns the host card/tapped state to its pre-activation condition.
- [ ] Run `mvn -s .mvn/local-settings.xml -pl forge-game -am "-Dtest=GameActionUtilRollbackTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` and record the expected failure through `Game.restoreGameState()`.
- [ ] Remove the `game.restoreGameState()` branch from `GameActionUtil.rollbackAbility()`. Keep the existing explicit rollback as the single cancellation path; do not catch and resume arbitrary committed-action exceptions.
- [ ] Re-run the regression test plus relevant existing game tests and commit the passing implementation.

### Task 3: Add a deterministic outer game-loop failure boundary

**Files:**
- Create: `forge-gui/src/main/java/forge/gamemodes/match/MatchGameFailureHandler.java`
- Modify: `forge-gui/src/main/java/forge/gamemodes/match/HostedMatch.java`
- Test: `forge-gui/src/test/java/forge/gamemodes/match/MatchGameFailureHandlerTest.java`

- [ ] Write tests against injected cleanup, reporter, and UI-shutdown callbacks: `nonFatalFailureClearsInputsReportsAndEndsMatchInOrder`, `cleanupFailureStillReportsAndEndsMatch`, `reporterFailureStillEndsMatch`, and `fatalVmFailureIsRethrownWithoutPretendingRecovery`.
- [ ] Run `mvn -s .mvn/local-settings.xml -pl forge-gui -am "-Dtest=MatchGameFailureHandlerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` and record the expected compile/test failure because the handler is absent.
- [ ] Implement a small package-private `MatchGameFailureHandler` that rejects `VirtualMachineError`, `ThreadDeath`, and `LinkageError`; for other failures it runs input cleanup, reports the original failure (with suppressed cleanup/report failures), and always schedules match shutdown exactly once.
- [ ] Wrap the long-lived `match.startGame(currentGame, startGameHook)` body in `HostedMatch` with this handler. The cleanup callback must call `clearInputs()` for every human controller on the game thread. The shutdown callback must run on the EDT, verify `game == currentGame`, call `endCurrentGame()`, mark the match over, and invoke `onMatchOver` once. Do not restart `PhaseHandler` or restore a `GameSnapshot`.
- [ ] Re-run the focused tests and a `forge-gui` compile/test slice; commit the passing implementation.

### Task 4: Verify, integrate, publish, and reproduce

**Files:**
- Modify only if required by build findings: affected test/resource files above.
- Verify artifact: `forge-gui-desktop/target/forge-gui-desktop-2.0.14-SNAPSHOT-jar-with-dependencies.jar`

- [ ] Run the combined regression suite for `InputLockUIRecoveryTest`, `InputQueueRecoveryTest`, `GameActionUtilRollbackTest`, and `MatchGameFailureHandlerTest`, with `-Dsurefire.failIfNoSpecifiedTests=false` where reactor modules do not own a selected test.
- [ ] Run module-level Maven verification for `forge-game`, `forge-gui`, and the desktop shaded JAR. Check Checkstyle and test summaries, not just the process exit code.
- [ ] Inspect the branch diff for unrelated files, debug output, broad exception swallowing, and any attempt to resume an incomplete snapshot.
- [ ] Merge/cherry-pick the isolated commits into the user's dirty main worktree without overwriting the existing card-image performance edits; rebuild the desktop shaded JAR there.
- [ ] Confirm bytecode/JAR timestamps and hashes changed, stop only the old Forge process if still running, start the rebuilt desktop client, and reproduce: activate Aman'Thul Exhaust with insufficient white mana, cancel, confirm the prompt/buttons clear and priority returns; also verify a normal payable activation still succeeds.
- [ ] Commit and push the engine changes as required by the repository instructions, then report exact test/build/runtime evidence and any remaining limitation.
