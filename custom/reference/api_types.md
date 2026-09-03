# Forge API Types Reference (`ApiType`)

This document lists all valid `ApiType` enums defined in [ApiType.java](file:///d:/Forge/forge-latest/forge-game/src/main/java/forge/game/ability/ApiType.java). The API Type is used as the `AB$`, `SP$`, or `DB$` parameter to define spell and ability effects.

---

## 🔑 Common ApiTypes & Usage Examples

### 1. `DealDamage`
Deals damage to a target or player.
- **Parameters**: `ValidTgts$`, `NumDmg$`, `SpellDescription$`
- **Example**:
  ```text
  A:SP$ DealDamage | ValidTgts$ Creature,Player | NumDmg$ 3 | SpellDescription$ CARDNAME deals 3 damage to target creature or player.
  ```

### 2. `Draw`
Causes a player to draw cards.
- **Parameters**: `Defined$`, `NumCards$`
- **Example**:
  ```text
  A:SP$ Draw | Defined$ You | NumCards$ 2 | SpellDescription$ Draw two cards.
  ```

### 3. `Mana`
Produces mana.
- **Parameters**: `Produced$`, `Amount$`
- **Example**:
  ```text
  A:AB$ Mana | Cost$ T | Produced$ G | Amount$ 1 | SpellDescription$ Add {G}.
  ```

### 4. `PutCounter`
Places counters (+1/+1, charge, poison, etc.) on permanents or players.
- **Parameters**: `ValidTgts$`, `CounterType$`, `CounterNum$`
- **Example**:
  ```text
  A:AB$ PutCounter | Cost$ T | ValidTgts$ Creature.YouCtrl | CounterType$ P1P1 | CounterNum$ 1 | SpellDescription$ Put a +1/+1 counter on target creature you control.
  ```

### 5. `Pump`
Temporarily boosts power/toughness and/or adds keyword abilities until end of turn.
- **Parameters**: `ValidTgts$`, `NumPower$`, `NumToughness$`, `AddKeyword$`
- **Example**:
  ```text
  A:AB$ Pump | Cost$ R | ValidTgts$ Card.Self | NumPower$ 1 | NumToughness$ 0 | SpellDescription$ CARDNAME gets +1/+0 until end of turn.
  ```

### 6. `Sacrifice`
Forces a player to sacrifice a permanent.
- **Parameters**: `Defined$`, `SacValid$`
- **Example**:
  ```text
  A:SP$ Sacrifice | Defined$ Opponent | SacValid$ Creature | SpellDescription$ Target opponent sacrifices a creature.
  ```

### 7. `Token`
Creates creature or artifact tokens.
- **Parameters**: `TokenAmount$`, `TokenName$`, `TokenTypes$`, `TokenColors$`, `TokenPower$`, `TokenToughness$`, `TokenKeywords$`
- **Example**:
  ```text
  A:SP$ Token | TokenAmount$ 1 | TokenName$ Goblin | TokenTypes$ Creature Goblin | TokenColors$ Red | TokenPower$ 1 | TokenToughness$ 1 | TokenKeywords$ Haste | SpellDescription$ Create a 1/1 red Goblin creature token with haste.
  ```

### 8. `GrantSpellRule`
Registers a source-independent spell cost or mana-payment rule on a player for the current game.
- **Parameters**: `Defined$`, `RuleKey$`, `ValidCards$`, `ValidSA$`, `ReduceGeneric$`, `ManaConversion$`, `Harmony$`, `HarmonyReduction$`, `Duration$`, optional `Stacking$`, optional `NameSnapshot$ OpponentCards`
- **Constraints**: at least one of `ReduceGeneric$`, `ManaConversion$`, or `Harmony$ True` must have an effect; every whitespace-separated mana-conversion pair must select at least one source mana type and at least one destination mana type. `Harmony$ True` automatically supplies `AnyType->AnyColor` and therefore rejects both explicit `ManaConversion$` and `ReduceGeneric$`; `HarmonyReduction$` requires Harmony. It removes fixed colored-option symbols first, then passes any remainder through Forge's existing generic-reduction semantics, and never changes legacy `ReduceGeneric$` itself. An unresolved X/COLORED_X is not a removable symbol, while a chosen X already expanded into payable amounts can be reduced. Harmony label, reduction, and conversion follow the card owner's registry even when another player casts it; legacy non-Harmony rules continue to follow the activating player. `NameSnapshot$ OpponentCards` snapshots all current opponent card names for each recipient and limits that permanent rule to matching names; an empty snapshot matches nothing. With `Stacking$ True`, every registration retains its own snapshot, overlapping names accumulate reductions, and names introduced only in later snapshots receive only later layers. `Duration$ Permanent` is explicit and required (missing or other values fail before registration); card and spell restrictions use the source-independent safe subset documented in `KEYWORDS.md`. Multi-player grants preflight every deduplicated target before committing any registry mutation. Visible keyword views update differentially; hidden libraries and sideboards invalidate lazily by owner Harmony epoch and are never bulk-rebuilt.
- **Example**:
  ```text
  SVar:GrantRule:DB$ GrantSpellRule | Defined$ You | RuleKey$ Example.ColoredSpells | ValidCards$ Card.nonColorless | ValidSA$ Spell | Harmony$ True | HarmonyReduction$ 2 | Duration$ Permanent
  ```

### 9. `StealSameName`
Takes one card from a targeted opponent whose name matches the `Card` object of the enclosing trigger. It checks zones in the fixed order Battlefield, Hand, Library, Graveyard and never falls through to a lower-priority zone when a match exists in a higher one. Within that zone it takes the first matching object without opening a player-choice UI, so repeated triggers cannot create a sequence of native selection windows. A battlefield match changes control in place; a hand, library, or graveyard match moves that existing card object to the activating player's hand and transfers ownership. No match is a silent no-op.
- **Parameters**: `ValidTgts$ Opponent`
- **Trigger input**: the enclosing trigger must supply `AbilityKey.Card`, as `SpellCast` does.
- **Example**:
  ```text
  SVar:TakeSameName:DB$ StealSameName | ValidTgts$ Opponent
  ```

### Entity-backed emblems with `MakeCard`

`MakeCard` accepts `AsEmblem$ True` only together with `Zone$ Command`. It loads the named `PaperCard` normally, retaining that independent script's triggers, abilities, Oracle text, and image, then marks the materialized game object as both an emblem and `GamePieceType.EFFECT`. This avoids leaving an ordinary `CARD` in the command zone while allowing an emblem's rules to live in its own registered card script.

```text
SVar:GainEmblem:DB$ MakeCard | Defined$ You | Name$ Emblem — Example | Zone$ Command | AsEmblem$ True
```

The named entity must be present in the card database. Do not combine `AsEmblem$ True` with another zone or `AttachedTo$`.

For a resolution-time three-way choice among entity-backed emblems, use the existing `GenericChoice` API and put an `IsPresent$ Emblem.YouCtrl+named... | PresentZone$ Command | PresentCompare$ EQ0` restriction on each fixed `MakeCard` choice. `GenericChoice` removes choices whose restrictions fail before opening its UI. This makes “has not been chosen” a direct check for the corresponding emblem object in that player's command zone, not a history of selected description text.

## Deck-construction rules (`DeckRule:`)

`DeckRule:` is top-level card-script metadata, not an `ApiType`. A rule becomes active when its source card is included in a deck. The top-level key is exact after Java-style trimming: `DeckRule :`, `DeckRule :` (NBSP), and ` DeckRule:` are different unknown keys, not construction rules. Parameter field names and enum values are case-insensitive. Rule IDs are trimmed and NFC-normalized but remain case-sensitive, and must be unique within one card script. The first schema supports only `ONCE_PER_DECK`: one or several copies of the source card activate a rule once.

The construction source name follows `CardRules.Reader`, not the last `Name:` line. Normal cards and Transform/Modal/Adventure/other non-combining modes use the primary face. `Split` strips each selected face independently, then uses `Primary // Other`, including the four-byte separator in name limits. Construction-source normalization is separate from ordinary filename aggregation, which retains the Reader face spelling and still uses both faces only for Split. The face switch uses the cardsfolder-standard standalone `ALTERNATE` line without a colon.

`CopyFaceFrom:` supplies a null face name. A real `Name:` entry takes priority over its placeholder whenever it exists; after Java `strip()`, an empty real name leaves the construction source unresolved and never falls back. A pure primary placeholder omits local `ManaCost:` and `Types:` and is exempt from those two required-field checks. Conversely, a null/placeholder face must not receive any direct face-setter field: `A`, `Colors`, `Defense`, `Draft`, `FlavorName`, `K`, `Loyalty`, `Lights`, `ManaCost`, `Oracle`, `PT`, `R`, `S`, `SVar`, `T`, nonblank `Text`, `Types`, or `Variant`. Strict lint rejects these combinations before they can become a Reader null-face crash.

Both strict lint and the production construction parser fail closed with diagnostics for unsupported `AlternateMode:` values or missing required faces, because a stable source/`RuleKey` cannot be derived. Production card loading must keep these as inactive construction rules rather than infer the last `Name:` line.

```text
DeckRule:Id$ add-signature | Mode$ ADD_FIXED | Target$ Main | Card$ Card Name | Amount$ 2
DeckRule:Id$ choose-signature | Mode$ CHOOSE_ONE | Target$ Main | Candidates$ First Card;Second Card;Third Card | Amount$ 1
DeckRule:Id$ allow-signature | Mode$ ALLOW | Constraint$ COPY_LIMIT | Card$ Card Name
```

The optional `Cardinality$ ONCE_PER_DECK` spelling is accepted. `Cardinality$ PER_COPY` and every other value are intentionally unsupported and fail strict lint instead of silently changing deck contents.

### Modes

- `ADD_FIXED` requires `Target$`, `Card$`, and `Amount$`. It adds the exact managed contribution to the target section.
- `CHOOSE_ONE` requires `Target$`, semicolon-delimited `Candidates$`, and `Amount$`. Candidate names cannot contain empty entries. Runtime canonical keys apply Unicode NFC, locale-invariant uppercase, then NFC again; canonical duplicates are removed while preserving the first spelling and order.
- `ALLOW` requires `Constraint$` and `Card$`. Its constraints are `FORMAT_CARD_POOL`, `COMMANDER_COLOR_IDENTITY`, `COPY_LIMIT`, `SECTION`, and `BANNED_OR_RESTRICTED`. `COPY_LIMIT` means unlimited copies of that target card in the first schema. `SECTION` additionally requires `Target$`; the other constraints reject `Target$` as an unexpected field.

Valid `Target$` values are `Main`, `Sideboard`, `Commander`, `Avatar`, `Planes`, `Schemes`, `Conspiracy`, `Dungeon`, `Attractions`, and `Contraptions` (case-insensitive). `Amount$` is an ASCII integer matching `[+-]?[0-9]+` whose value is from 1 through 1000; Arabic-Indic, fullwidth, and other Unicode digits are rejected. A comma is ordinary card-name text; only `|` separates parameters and only `;` separates `CHOOSE_ONE` candidates.

For a Hearthstone-style card that always contributes two different derived cards, use two stable rules rather than combining names:

```text
DeckRule:Id$ oddity-left | Mode$ ADD_FIXED | Target$ Main | Card$ Left Curiosity | Amount$ 1
DeckRule:Id$ oddity-right | Mode$ ADD_FIXED | Target$ Main | Card$ Right Curiosity | Amount$ 1
```

### Validation, persistence, and limits

The strict development lint rejects duplicate IDs or parameters, missing or unexpected fields, unknown modes/constraints/sections, malformed amounts/candidates, and unsupported cardinality. It does not query the card database for `Card$` or `Candidates$`: cards may come from a different pool or load later. Production resolves those names at runtime and records a structured inactive-rule diagnostic instead of crashing card/database startup.

The source card name and rule `Id$` reject every C0 control (`U+0000` through `U+001F`) and `DEL` (`U+007F`) so a persistent `RuleKey` cannot contain invisible separators. This schema does not add that control-character restriction to `Card$` or candidate values. Cardscript values follow Java parsing exactly: the value after the top-level colon first receives `String.trim()` semantics, while each `Key$ Value` piece uses `String.strip()` semantics. In particular, non-breaking space (`U+00A0`) is not silently removed from a field key or value.

Each runtime rule carries a schema version, stable `RuleKey` (canonical source name plus case-sensitive NFC rule ID), and normalized fingerprint. Changing the amount, section, card, candidates, or other semantics for the same source card plus `Id$` requires a managed-ledger migration; the engine must not guess which physical printing was automatically contributed. Dependency expansion uses a bounded fixed-point graph. Cycles are reported as `CYCLIC_DEPENDENCY`/`UNRESOLVED`, never expanded indefinitely.

Only the first 100 `DeckRule:` lines are parsed in detail. The 101st records one overflow error, and all later rule lines skip field/ID diagnostics so malformed input cannot grow the lint error list or ID set without bound.

The first-schema lint limits use strict UTF-8 byte counts. Raw definitions and fields are measured as written after the documented trimming; IDs are measured after NFC normalization; source, card, and candidate names must fit both their NFC display spelling and their NFC-uppercase-NFC canonical key.

- at most 100 `DeckRule:` lines per card;
- at most 16,384 UTF-8 bytes in one trimmed rule definition after `DeckRule:`;
- at most 8,192 UTF-8 bytes in one complete `Key$ Value` field;
- at most 16 fields in one rule definition;
- at most 1,024 UTF-8 bytes in an NFC-normalized rule `Id$`;
- at most 4,096 UTF-8 bytes in both the display and canonical forms of the source card name, each `Card$`, and each candidate;
- `Amount$` from 1 through 1,000;
- 1 through 1,000 canonically distinct `CHOOSE_ONE` candidates; raw empty entries are rejected and canonical duplicates are collapsed before counting.

---

## 📜 Full List of Valid `ApiType` Values

These must be written exactly as spelled (case-insensitive in Forge but capitalized as shown is best practice):

- `Abandon`
- `ActivateAbility`
- `AddOrRemoveCounter`
- `AddPhase`
- `AddTurn`
- `AdvanceCrank`
- `AlterAttribute`
- `Amass`
- `Animate`
- `AnimateAll`
- `Attach`
- `Ascend`
- `AssembleContraption`
- `AssignGroup`
- `Balance`
- `BecomeMonarch`
- `BecomesBlocked`
- `BidLife`
- `Block`
- `Bond`
- `Branch`
- `CardDiscover`
- `Camouflage`
- `ChangeCombatants`
- `ChangeSpeed`
- `ChangeTargets`
- `ChangeText`
- `ChangeX`
- `ChangeZone`
- `ChangeZoneAll`
- `ChaosEnsues`
- `Charm`
- `ChooseCard`
- `ChooseColor`
- `ChooseDirection`
- `ChooseEvenOdd`
- `ChooseNumber`
- `ChoosePlayer`
- `ChooseSector`
- `ChooseSource`
- `ChooseType`
- `ClaimThePrize`
- `Clash`
- `ClassLevelUp`
- `Cleanup`
- `Cloak`
- `Clone`
- `CompanionChoose`
- `Connive`
- `CopyPermanent`
- `CopySpellAbility`
- `ControlSpell`
- `ControlPlayer`
- `Counter`
- `DamageAll`
- `DealDamage`
- `DayTime`
- `Debuff`
- `DelayedTrigger`
- `Destroy`
- `DestroyAll`
- `Dig`
- `DigMultiple`
- `DigUntil`
- `Discard`
- `Discover`
- `DrainMana`
- `Draft`
- `Draw`
- `EachDamage`
- `Effect`
- `Encode`
- `EndCombatPhase`
- `EndTurn`
- `ExchangeLife`
- `ExchangeLifeVariant`
- `ExchangeControl`
- `ExchangeControlVariant`
- `ExchangePower`
- `ExchangeZone`
- `Explore`
- `Fight`
- `FlipACoin`
- `FlipOntoBattlefield`
- `Fog`
- `GainControl`
- `GainControlVariant`
- `GainLife`
- `GainOwnership`
- `GameDrawn`
- `GenericChoice`
- `Goad`
- `GrantSpellRule`
- `Haunt`
- `Heist`
- `Investigate`
- `Intensify`
- `ImmediateTrigger`
- `Incubate`
- `Learn`
- `LookAt`
- `LoseLife`
- `LosePerpetual`
- `LosesGame`
- `MakeCard`
- `Mana`
- `ManaReflected`
- `Manifest`
- `ManifestDread`
- `Meld`
- `Mill`
- `MoveCounter`
- `MultiplePiles`
- `MultiplyCounter`
- `MustBlock`
- `Mutate`
- `NameCard`
- `OpenAttraction`
- `PeekAndReveal`
- `PermanentCreature`
- `PermanentNoncreature`
- `Phases`
- `Planeswalk`
- `Play`
- `PlayLandVariant`
- `Poison`
- `PreventDamage`
- `Proliferate`
- `Protection`
- `ProtectionAll`
- `Pump`
- `PumpAll`
- `PutCounter`
- `PutCounterAll`
- `Radiation`
- `RearrangeTopOfLibrary`
- `Regenerate`
- `Regeneration`
- `RemoveCounter`
- `RemoveCounterAll`
- `RemoveFromCombat`
- `ReplaceCards`
- `RemoveFromGame`
- `RemoveFromMatch`
- `ReorderZone`
- `Repeat`
- `RepeatEach`
- `ReplaceCounter`
- `ReplaceEffect`
- `ReplaceMana`
- `ReplaceDamage`
- `ReplaceToken`
- `ReplaceSplitDamage`
- `RestartGame`
- `Reveal`
- `RevealHand`
- `ReverseTurnOrder`
- `RingTemptsYou`
- `RollDice`
- `RollPlanarDice`
- `RunChaos`
- `Sacrifice`
- `SacrificeAll`
- `Scry`
- `Seek`
- `SetInMotion`
- `SetLife`
- `SetState`
- `Shuffle`
- `SkipPhase`
- `SkipTurn`
- `StoreSVar`
- `StealSameName`
- `Subgame`
- `Surveil`
- `SwitchBlock`
- `TakeInitiative`
- `Tap`
- `TapAll`
- `TapOrUntap`
- `TapOrUntapAll`
- `TakeFatigue`
- `TimeTravel`
- `Token`
- `TwoPiles`
- `Unattach`
- `UnattachAll`
- `UnlockDoor`
- `Untap`
- `UntapAll`
- `Venture`
- `VillainousChoice`
- `Vote`
- `WinsGame`
- `BlankLine`
- `DamageResolve`
- `ChangeZoneResolve`
- `InternalLegendaryRule`
- `InternalIgnoreEffect`
- `InternalRadiation`
