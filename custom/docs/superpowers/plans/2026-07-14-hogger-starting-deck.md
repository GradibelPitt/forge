# Hogger Starting-Deck Copy Implementation Plan

**Goal:** Make 破链灾星霍格 duplicate every other legendary permanent from the registered main-deck construction state, even if a card such as 海盗帕奇斯 has already left the opening hand or library before the NewGame ability resolves.

**Architecture:** Add one deliberately narrow `StartingDeckLegendaryPermanents` source to `MakeCardEffect`. It iterates only the registered player's main-deck `CardPool` entries and checks `PaperCard` rule metadata. It never queries the global card database and never materializes temporary `Card` candidates, so work is bounded by distinct main-deck entries plus the copies that must actually be created.

**Tech Stack:** Java 17, Forge AbilityFactory DSL, TestNG, Python unittest, Maven, PowerShell.

## Task 1: Lock the bug and bounded behavior with failing tests

- [x] Add `MakeCardEffectTest` with Patches present only in a synthetic starting `CardPool`.
- [x] Assert two constructed copies produce exactly two names, Hogger excludes itself, and a nonpermanent is ignored.
- [x] Update the Python contract to reject `ValidHand`, `ValidLibrary`, and generic `ValidStartingDeck` scans.
- [x] Run both tests before implementation and confirm the expected compile/contract failures.

## Task 2: Implement the specialized starting-deck source

- [x] Route `DefinedName$ StartingDeckLegendaryPermanents` through the registered player's current main deck.
- [x] Iterate `CardPool` counted entries directly; do not call `toFlatList()`.
- [x] Filter with `PaperCard.getRules().getType()` and the explicit excluded card name.
- [x] Preserve constructed multiplicity while allocating output only for cards that will actually be copied.
- [x] Run the targeted Java and Python regressions and confirm green.

## Task 3: Update Hogger and design documentation

- [x] Replace the live hand/library chain with one `CopyStartingDeck` MakeCard step.
- [x] Document that starting deck means the registered current main-deck construction state.
- [x] Document the separate Boarding late-entry boundary accurately.
- [x] Run card lint and the full custom suite.

## Task 4: Verify engine integration and packaged bytecode

- [x] Run `BoardingTest`, `MakeCardEffectTest`, and `TriggerHandlerTest` together.
- [x] Run the full `forge-game` module test suite.
- [ ] Build the desktop aggregate JAR.
- [ ] Inspect packaged `MakeCardEffect` bytecode for the registered-deck path and absence of global database/card-materialization calls in the helper.

## Task 5: Deploy and publish

- [ ] Install the updated Hogger script to the local Forge profile and compare hashes.
- [ ] Commit and push only the related source, test, plan, and documentation files to `diy-fork/diy`.
- [ ] Publish the aggregate JAR and managed Hogger script to `forge-diy-runtime`.
- [ ] Regenerate runtime manifests, run runtime tests, commit, and push `origin/main`.
- [ ] Record exact verification and artifact hashes in `custom/VERIFICATION.md`.
