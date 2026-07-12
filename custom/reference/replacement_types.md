# Forge Replacement Types Reference (`ReplacementType`)

This document lists all valid `ReplacementType` values defined in [ReplacementType.java](file:///d:/Forge/forge-latest/forge-game/src/main/java/forge/game/replacement/ReplacementType.java). Replacement effects intercept game events (e.g. drawing a card, dealing damage) and redirect them. They are declared with the `R:` prefix on a line.

---

## 🔑 Common Replacement Effects & Usage Examples

### 1. `Draw`
Intercepts a single card draw.
- **Parameters**: `Event$`, `ActiveZones$`, `ReplaceWith$`, `Description$`
- **Example**:
  ```text
  R:Event$ Draw | ActiveZones$ Battlefield | ReplaceWith$ SVarReplaceDraw | Description$ If you would draw a card, instead ...
  ```

### 2. `DamageDone` / `DealtDamage`
Intercepts damage before it is dealt.
- **Parameters**: `Event$`, `ActiveZones$`, `ReplaceWith$`, `Description$`
- **Example**:
  ```text
  R:Event$ DamageDone | ActiveZones$ Battlefield | ValidSource$ Card.Self | ReplaceWith$ SVarDoubleDamage | Description$ If CARDNAME would deal damage, it deals double that damage instead.
  ```

### 3. `Moved`
Intercepts moving cards between zones (e.g. going to graveyard, entering battlefield).
- **Parameters**: `Event$`, `ActiveZones$`, `Destination$`, `ReplaceWith$`, `Description$`
- **Example**:
  ```text
  R:Event$ Moved | ActiveZones$ Battlefield | Destination$ Graveyard | ValidCard$ Card.Self | ReplaceWith$ SVarExileInstead | Description$ If CARDNAME would be put into a graveyard from anywhere, exile it instead.
  ```

---

## 📜 Full List of Valid `ReplacementType` Values

These must be written exactly as spelled:

- `AddCounter`
- `AssembleContraption`
- `AssignDealDamage`
- `Attached`
- `BeginPhase`
- `BeginTurn`
- `Cascade`
- `Counter`
- `CopySpell`
- `CreateToken`
- `DamageDone`
- `DealtDamage`
- `DeclareBlocker`
- `Destroy`
- `Draw`
- `DrawCards`
- `Explore`
- `GainLife`
- `GameLoss`
- `GameWin`
- `Learn`
- `LifeReduced`
- `LoseMana`
- `Mill`
- `Moved`
- `PayLife`
- `PlanarDiceResult`
- `Planeswalk`
- `ProduceMana`
- `Proliferate`
- `RemoveCounter`
- `RollDice`
- `RollPlanarDice`
- `Scry`
- `SetInMotion`
- `Tap`
- `Transform`
- `TurnFaceUp`
- `Untap`
