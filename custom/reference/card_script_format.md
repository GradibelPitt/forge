# Forge Card Script Field Reference

This document explains the standard keys parsed by `CardRules.Reader` in [CardRules.java](file:///d:/Forge/forge-latest/forge-core/src/main/java/forge/card/CardRules.java). Each card script is a collection of `Key:Value` pairs.

---

## 📋 Standard Fields

### 1. `Name` (Required)
The exact name of the card. Used for card identification.
- **Example**: `Name:Dark Confidant`

### 2. `ManaCost` (Required)
The mana cost. Mana symbols are space-separated. Use `no cost` for cards that cannot be cast normally (e.g. Ancestral Vision or tokens).
- **Format**: `no cost`, `W`, `U`, `B`, `R`, `G`, `C` (Colorless), `X`, numbers (e.g. `2`, `3`). Hybrid and Phyrexian mana symbols are written as `{W/U}` or `{G/P}`.
- **Example**: `ManaCost:1 W B`

### 3. `Types` (Required)
Space-separated card supertypes, types, and subtypes.
- **Example**: `Types:Legendary Creature Elf Scout`
- **Example**: `Types:Artifact Equipment`

### 4. `PT` (Optional)
Power and Toughness for creatures and vehicles.
- **Example**: `PT:2/1`
- **Example**: `PT:X/X`

### 5. `Loyalty` (Optional)
Starting loyalty counters for Planeswalkers.
- **Example**: `Loyalty:4`

### 6. `Defense` (Optional)
Starting defense counters for Battles.
- **Example**: `Defense:5`

### 7. `Colors` (Optional)
Overrides the card's color identity. Helpful for cards like Ancestral Vision or Spheres of Protection.
- **Example**: `Colors:blue`

### 8. `Oracle` (Optional)
The official rules text shown to the player in the UI. Use `\n` to represent line breaks.
- **Example**: `Oracle:Flying, vigilance\nWhen CARDNAME enters, draw a card.`

### 9. `Text` (Optional)
Flavor text or non-game-rule text.
- **Example**: `Text:Deep in the forest, the elves listen.`

---

## ⚡ Ability & Effect Fields

### `K` (Keywords)
One per line. Declares built-in keyword abilities (e.g., `Flying`, `Haste`, `Trample`, `Defender`, `Lifelink`, `Deathtouch`).
- **Example**:
  ```text
  K:Flying
  K:Vigilance
  ```
- Refer to `reference/keyword_abilities.md` for a full list of valid keywords.

### `A` (Activated or Spell Abilities)
One per line. Defines activated abilities (`AB$`) or spells (`SP$`).
- **Format**: `A:<AB|SP>$ <ApiType> | <Params>`
- **Example**:
  ```text
  A:SP$ DealDamage | ValidTgts$ Creature,Player | NumDmg$ 3 | SpellDescription$ CARDNAME deals 3 damage to target creature or player.
  ```

### `T` (Triggered Abilities)
One per line. Defines triggers that react to game events.
- **Format**: `T:Mode$ <TriggerType> | <Params>`
- **Example**:
  ```text
  T:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | ValidCard$ Card.Self | Execute$ TrigDraw | TriggerDescription$ When CARDNAME enters, draw a card.
  ```

### `S` (Static Abilities)
One per line. Defines continuous effects or global state changes.
- **Format**: `S:Mode$ Continuous | <Params>`
- **Example**:
  ```text
  S:Mode$ Continuous | Affected$ Creature.YouCtrl | AddKeyword$ Vigilance | Description$ Creatures you control have vigilance.
  ```

### `R` (Replacement Effects)
One per line. Defines replacement modifications.
- **Format**: `R:Event$ <ReplacementType> | <Params>`
- **Example**:
  ```text
  R:Event$ Draw | ActiveZones$ Battlefield | ReplaceWith$ SVarReplaceDraw | Description$ If you would draw a card...
  ```

---

## ⚙️ Logic and Variables

### `SVar` (Script Variables)
Defines variables referenced in the card's abilities. SVars can store numbers, formulas, targeting conditions, or chained sub-abilities (`DB$`).
- **Format**: `SVar:<Name>:<Value>`
- **Examples**:
  - Sub-ability chain:
    ```text
    SVar:TrigDraw:DB$ Draw | Defined$ You | NumCards$ 1
    ```
  - Math formula (X value calculation):
    ```text
    SVar:X:Count$CardColor_Green_Battlefield
    ```

For a command-zone effect created with `DB$ Effect | SetChosenNumber$ X`, a
continuous play permission may use `MayPlayAltManaCost$ ChosenNumber`. Forge
snapshots that chosen number into the alternative generic mana cost when the
permission is applied.

---

## 🤖 AI and Deckbuilder Hints

### `DeckHints`
Tells the AI what deck archetypes this card belongs to.
- **Example**: `DeckHints:Ability$LifeGain`

### `DeckHas`
Tells the deckbuilder what resources this card utilizes or produces.
- **Example**: `DeckHas:Ability$Counters|Token`

---

## 🔄 Dual and Double-Faced Cards

### `AlternateMode`
Declares split cards, adventure cards, transform cards, or meld cards.
- **Values**: `Split`, `Adventure`, `Transform`, `Meld`, `DoubleFaced`

### `ALTERNATE`
Separates the front face script from the back face (or second mode) script. Everything below `ALTERNATE` defines the second half of the card.
- **Example**:
  ```text
  # Front Face
  Name:Delver of Secrets
  ManaCost:U
  Types:Creature Human Wizard
  ...
  AlternateMode:Transform
  
  ALTERNATE
  
  # Back Face
  Name:Insectile Aberration
  ManaCost:no cost
  Colors:blue
  Types:Creature Human Insect
  ...
  ```
