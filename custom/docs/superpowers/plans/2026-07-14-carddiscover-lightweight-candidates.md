# CardDiscover Lightweight Candidate Pipeline

**Goal:** Prevent `CardDiscoverEffect` from materializing the entire Forge card database while preserving the existing `ValidCards` implementation as the final semantic authority.

**Architecture:** Database discovery uses three bounded layers: conservative static prefiltering on immutable `PaperCard`/`CardRules`, randomized sampling with Forge's existing RNG, and exact `Card.isValid` validation after materializing at most a fixed number of candidates. Library discovery continues to operate on the player's existing `Card` objects. Static-only filters materialize only the final option count; filters containing dynamic or unsupported clauses use the exact validator but can never exceed the explicit candidate budget.

**Compatibility constraints:**

- `Card.isValid` remains the semantic source of truth; the lightweight matcher may only reject candidates when a clause is provably static and equivalent.
- An unknown or dynamic clause disables static completeness and must never be guessed or reimplemented approximately.
- The dynamic path has a hard materialization budget and may return fewer than three results when that budget is exhausted.
- Production randomness comes from `MyRandom.getRandom()`; tests may inject a seeded `Random` into package-private helpers.
- No cache may retain a `Card`, `SpellAbility`, `Player`, or `Game` reference. This fix scans Forge's existing lightweight unique `PaperCard` definitions and does not introduce a game-bound candidate cache.

## Task 1: Add failing candidate-pipeline tests

**Files:**

- Modify: `forge-game/src/test/java/forge/game/ability/effects/CardDiscoverEffectTest.java`

1. Add fixtures that create lightweight `PaperCard` definitions with controlled names, types, subtypes, and mana values.
2. Add a static-filter test for the real database clauses used by custom cards: `Creature.Pirate` and `Sorcery.cmcEQX`.
3. Add a materialization counter and assert that a static-only query requesting three options creates exactly three `Card` candidates.
4. Add a mixed/dynamic query whose exact predicate never succeeds; assert the number of created candidates equals the configured budget and never the database size.
5. Add fixed-seed reproducibility and duplicate-name tests.
6. Add a test proving rejected temporary candidates are neither returned nor placed in a player zone/remembered collection.
7. Run only `CardDiscoverEffectTest` and record the expected RED result before production implementation.

## Task 2: Implement conservative lightweight prefiltering

**Files:**

- Modify: `forge-game/src/main/java/forge/game/ability/effects/CardDiscoverEffect.java`
- Add if separation improves clarity: `forge-game/src/main/java/forge/game/ability/effects/CardDiscoverCandidateFilter.java`

1. Parse only a documented, conservative subset of existing `ValidCards` clauses against `CardRules`: base card/type selectors, static card types/subtypes, colors, and mana-value comparisons whose right-hand value can already be evaluated by `AbilityUtils`.
2. Return both a lightweight predicate and a `complete` flag. Any unknown, player-, zone-, game-, or source-dependent clause remains unfiltered and sets `complete=false`.
3. Keep the original `Card.isValid(validCards, sourceController, sourceCard, sourceAbility)` call for every materialized database candidate.
4. Unit-test that unknown clauses pass the lightweight stage instead of causing false negatives.

## Task 3: Add randomized bounded materialization

**Files:**

- Modify: `forge-game/src/main/java/forge/game/ability/effects/CardDiscoverEffect.java`
- Modify: `forge-game/src/test/java/forge/game/ability/effects/CardDiscoverEffectTest.java`

1. Reservoir-sample lightweight `PaperCard` references without replacement from the statically prefiltered stream.
2. For complete static filters, sample only the requested option count and create at most that many `Card` instances.
3. For incomplete/dynamic filters, sample at most `MAX_DYNAMIC_CANDIDATES`, create and exact-check only those candidates, stop after collecting the requested number of distinct names, and never fall back to scanning/materializing the rest of the database.
4. Deduplicate final choices by normalized card name.
5. Obtain production RNG once through `MyRandom.getRandom()` and pass it through the helper pipeline.
6. Emit a bounded-path warning when an incomplete filter exhausts its budget before filling the requested option count, without retaining rejected candidates.
7. Run `CardDiscoverEffectTest` until GREEN.

## Task 4: Exercise the real effect paths

**Files:**

- Modify: `forge-game/src/test/java/forge/game/ability/effects/CardDiscoverEffectTest.java`

1. Resolve a `Source$ CardDatabase` static discovery and assert `createdCandidateCardCount == 3` through the package-private instrumentation seam.
2. Resolve or directly exercise a mixed dynamic discovery with a card pool larger than the budget and assert the hard upper bound.
3. Run 1,000 seeded static selections and assert total candidate materialization is at most `executions * 3`, all results are distinct by name, and both runs produce the same sequence.
4. Re-run all `CardDiscoverEffectTest` tests.

## Task 5: Verify custom cards and Forge modules

**Files:**

- Modify: `custom/VERIFICATION.md`

1. Run targeted custom-card tests for `混乱触须` and `空中悍匪`.
2. Run custom lint and the full custom test suite.
3. Run the complete `forge-game` module test suite.
4. Record commands and concrete results in `custom/VERIFICATION.md`.

## Task 6: Build and validate the desktop runtime

1. Install the updated `forge-game` module with `.mvn/local-settings.xml`.
2. Build the desktop fat JAR.
3. Inspect the packaged `CardDiscoverEffect` bytecode with `javap` to confirm the bounded lightweight path is present and the old unconditional full-database `Card.fromPaperCard` loop is absent.
4. Re-check that Forge is not running before replacing any live runtime artifact; if it is running, stop and ask the user to close it.
5. Update the local DIY runtime only after all source and packaged-runtime checks pass.

## Task 7: Review, publish, and hand off

1. Review the diff for accidental changes and verify no game-bound object is stored in a static or long-lived cache.
2. Run final targeted tests and inspect `git status`.
3. Commit only the intended source, tests, plan, and verification documentation.
4. Push the Forge source branch required by the repository instructions.
5. Update, verify, commit, and push `D:\Forge\forge-diy-runtime` according to its local instructions.
6. Report the root cause, the exact static and dynamic materialization bounds, test/build evidence, and deployed artifact hashes.
