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
- `Subgame`
- `Surveil`
- `SwitchBlock`
- `TakeInitiative`
- `Tap`
- `TapAll`
- `TapOrUntap`
- `TapOrUntapAll`
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
