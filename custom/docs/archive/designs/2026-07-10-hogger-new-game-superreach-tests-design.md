# Hogger New-Game Trigger and Superreach Test Cards

## Scope

This change updates Chainbreaker Hogger, makes all `NewGame` card triggers visible on the normal Forge stack, and adds two reusable custom cards for testing Superreach against attacker-side blocking restrictions.

## New-Game Trigger Timing and Visibility

Forge currently registers `TriggerType.NewGame` after all players finish mulligans and opening-hand actions. The trigger is held while the first turn begins. Because the first untap step gives no priority, the first opportunity to process the waiting trigger is the beginning of the starting player's first upkeep.

Keep this scheduling unchanged. Change only the trigger execution path so a `NewGame` trigger is always added as a simultaneous stack entry, even when its card script contains `Static$ True`. This gives both players normal stack visibility, APNAP ordering, priority, and resolution. Static triggers of every other type must retain their existing immediate-resolution behavior.

Acceptance criteria:

- A static `NewGame` trigger is queued on the normal stack instead of resolving immediately.
- A non-`NewGame` static trigger still resolves immediately.
- `NewGame` registration remains after mulligans and opening-hand actions.
- The trigger first appears at the beginning of the starting player's first upkeep.

## Chainbreaker Hogger

Both the starting hand and starting library searches must copy legendary permanent cards owned by Hogger's owner except cards named `破链灾星霍格`. Exclusion is name-based, so no copy of Hogger can copy another Hogger.

Use Forge's verified `notnamed破链灾星霍格` card-validity property in both `DefinedName` expressions. Preserve the current library-first, hand-second execution order so newly created cards are not copied again. Preserve library shuffling.

The trigger and Oracle text must say that it duplicates every other legendary permanent card in the starting deck. The trigger remains `Mode$ NewGame`; its stack visibility is supplied by the engine rule above.

## Superreach Test Cards

Add both cards to the custom `PH01` edition.

### Test Superreach 1

- Mana cost: `0`
- Type: `Creature`
- Power/toughness: `10/10`
- Keyword: `Superreach`

This is the defending test creature.

### Test Superreach 2

- Mana cost: `0`
- Type: `Creature`
- Power/toughness: `20/20`
- Keywords: Flying, Fear, Menace, Shadow, `Landwalk:Land`, Horsemanship, and Skulk
- Attacker-side static restrictions:
  - It cannot be blocked.
  - It cannot be blocked except by artifact creatures.
  - It cannot be blocked by creatures with power less than 20.
  - It cannot be blocked by creatures with power greater than 1.

The artifact-only rule is represented by rejecting nonartifact creature blockers. The contradictory power limits deliberately make ordinary blocking impossible. The card does not have `Ignore Superreach`, so Test Superreach 1 must be allowed to block it.

## Tests

Follow test-driven development:

1. Add a focused Java regression test proving static `NewGame` triggers use the stack while unrelated static triggers do not.
2. Add or extend combat tests proving a Superreach creature can block an attacker carrying the complete Test Superreach 2 restriction set, while a normal creature cannot.
3. Add DIY contract tests for Hogger's name exclusion, updated text, both test-card scripts, and their edition rows.
4. Run the full `forge-game` test suite, all DIY tests, and the card linter for all changed and new scripts.
5. Install the custom cards, patch the desktop runtime JAR with the verified classes, and confirm the deployed files and bytecode.

## Non-Goals

- Do not add modal dialogs or separate card-reveal prompts.
- Do not change the timing of opening-hand actions or mulligans.
- Do not change static-trigger handling outside `TriggerType.NewGame`.
- Do not weaken blocker-side restrictions or global blocking restrictions in Superreach.
