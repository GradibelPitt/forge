# Forge Trigger Types Reference (`TriggerType`)

This document lists all valid `TriggerType` values defined in [TriggerType.java](file:///d:/Forge/forge-latest/forge-game/src/main/java/forge/game/trigger/TriggerType.java). Triggers are declared using the prefix `T:` on a line, followed by parameters.

---

## 🔑 Common Trigger modes & Usage Examples

### 1. `ChangesZone` (ETB / Death / Exile Triggers)
Triggered when a card moves from one zone to another.
- **Parameters**: `Origin$`, `Destination$`, `ValidCard$`, `Execute$`, `TriggerDescription$`
- **Examples**:
  - **Enters the Battlefield (ETB)**:
    ```text
    T:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | ValidCard$ Card.Self | Execute$ TrigDraw | TriggerDescription$ When CARDNAME enters, draw a card.
    ```
  - **Dies (Battlefield to Graveyard)**:
    ```text
    T:Mode$ ChangesZone | Origin$ Battlefield | Destination$ Graveyard | ValidCard$ Card.Self | Execute$ TrigDamage | TriggerDescription$ When CARDNAME dies, deal 2 damage...
    ```

### 2. `Phase` (Upkeep / End Step Triggers)
Triggered at a specific turn phase or step.
- **Parameters**: `Phase$`, `ValidPlayer$`, `Execute$`, `TriggerDescription$`
- **Examples**:
  - **At the beginning of your upkeep**:
    ```text
    T:Mode$ Phase | Phase$ Upkeep | ValidPlayer$ You | Execute$ TrigGainLife | TriggerDescription$ At the beginning of your upkeep, you gain 1 life.
    ```
  - **At the beginning of each opponent's end step**:
    ```text
    T:Mode$ Phase | Phase$ End | ValidPlayer$ Opponent | Execute$ TrigLoseLife | TriggerDescription$ At the beginning of each opponent's end step, ...
    ```

### 3. `Attacks` (Attack Triggers)
Triggered when a creature attacks.
- **Parameters**: `ValidCard$`, `Execute$`, `TriggerDescription$`
- **Example**:
  ```text
  T:Mode$ Attacks | ValidCard$ Card.Self | Execute$ TrigPump | TriggerDescription$ Whenever CARDNAME attacks, it gets +1/+1 until end of turn.
  ```

### 4. `SpellCast` (Cast Triggers)
Triggered when a player casts a spell.
- **Parameters**: `ValidCard$`, `ValidPlayer$`, `Execute$`, `TriggerDescription$`
- **Example**:
  ```text
  T:Mode$ SpellCast | ValidCard$ Spell.Instant,Spell.Sorcery | ValidPlayer$ You | Execute$ TrigDraw | TriggerDescription$ Whenever you cast an instant or sorcery spell, draw a card.
  ```

---

## 📜 Full List of Valid `TriggerType` Values

These must be written exactly as spelled:

- `Abandoned`
- `AbilityCast`
- `AbilityResolves`
- `AbilityTriggered`
- `Adapt`
- `Always`
- `Attached`
- `AttackerBlocked`
- `AttackerBlockedOnce`
- `AttackerBlockedByCreature`
- `AttackersDeclared`
- `AttackersDeclaredOneTarget`
- `AttackerUnblocked`
- `AttackerUnblockedOnce`
- `Attacks`
- `BecomeMonarch`
- `BecomeMonstrous`
- `BecomeRenowned`
- `BecomesCrewed`
- `BecomesPlotted`
- `BecomesSaddled`
- `BecomesTarget`
- `BecomesTargetOnce`
- `BlockersDeclared`
- `Blocks`
- `CaseSolved`
- `Championed`
- `ChangesController`
- `ChangesZone`
- `ChangesZoneAll`
- `ChaosEnsues`
- `ClaimPrize`
- `Clashed`
- `ClassLevelGained`
- `CommitCrime`
- `ConjureAll`
- `CollectEvidence`
- `CounterAdded`
- `CounterAddedOnce`
- `CounterPlayerAddedAll`
- `CounterAddedAll`
- `Countered`
- `CounterRemoved`
- `CounterRemovedOnce`
- `CrankContraption`
- `Crewed`
- `Cycled`
- `DamageAll`
- `DamageDealtOnce`
- `DamageDone`
- `DamageDoneOnce`
- `DamageDoneOnceByController`
- `DamagePreventedOnce`
- `DayTimeChanges`
- `Destroyed`
- `Devoured`
- `Discarded`
- `DiscardedAll`
- `Discover`
- `Drawn`
- `DungeonCompleted`
- `Evolved`
- `ExcessDamage`
- `ExcessDamageAll`
- `Enlisted`
- `Exerted`
- `Exiled`
- `Exploited`
- `Explores`
- `Fight`
- `FightOnce`
- `FlippedCoin`
- `Forage`
- `Foretell`
- `FullyUnlock`
- `GiveGift`
- `Immediate`
- `Investigated`
- `LandPlayed`
- `LifeChanged`
- `LifeGained`
- `LifeLost`
- `LifeLostAll`
- `LosesGame`
- `ManaAdded`
- `ManaExpend`
- `ManifestDread`
- `Mentored`
- `Milled`
- `MilledOnce`
- `MilledAll`
- `Mutates`
- `NewGame`
- `PayCumulativeUpkeep`
- `PayEcho`
- `PayLife`
- `Phase`
- `PhaseIn`
- `PhaseOut`
- `PhaseOutAll`
- `PlanarDice`
- `PlaneswalkedFrom`
- `PlaneswalkedTo`
- `Proliferate`
- `RingTemptsYou`
- `RolledDie`
- `RolledDieOnce`
- `RoomEntered`
- `Saddled`
- `Sacrificed`
- `SacrificedOnce`
- `Scry`
- `SearchedLibrary`
- `SeekAll`
- `SetInMotion`
- `Shuffled`
- `Specializes`
- `SpellAbilityCast`
- `SpellAbilityCopy`
- `SpellCast`
- `SpellCastOrCopy`
- `SpellCopy`
- `Surveil`
- `TakesInitiative`
- `TapAll`
- `Taps`
- `TapsForMana`
- `TokenCreated`
- `TokenCreatedOnce`
- `Trains`
- `Transformed`
- `TurnBegin`
- `TurnFaceUp`
- `Unattach`
- `UnlockDoor`
- `UntapAll`
- `Untaps`
- `VisitAttraction`
- `Vote`
