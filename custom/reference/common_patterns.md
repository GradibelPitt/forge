# Forge Scripting Common Patterns

This document provides copy-paste templates for common Magic: The Gathering mechanics in Forge card scripting. 

---

## 🃏 Enters the Battlefield (ETB) Triggers

### 1. Enters the Battlefield, draw a card
```text
T:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | ValidCard$ Card.Self | Execute$ TrigDraw | TriggerDescription$ When CARDNAME enters the battlefield, draw a card.
SVar:TrigDraw:DB$ Draw | Defined$ You | NumCards$ 1
```

### 2. Enters the Battlefield, deal damage to any target
```text
T:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | ValidCard$ Card.Self | Execute$ TrigDamage | TriggerDescription$ When CARDNAME enters the battlefield, it deals 2 damage to any target.
SVar:TrigDamage:DB$ DealDamage | ValidTgts$ Creature,Player,Planeswalker | NumDmg$ 2
```

### 3. Enters the Battlefield, put a +1/+1 counter on target creature
```text
T:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | ValidCard$ Card.Self | Execute$ TrigCounter | TriggerDescription$ When CARDNAME enters the battlefield, put a +1/+1 counter on target creature.
SVar:TrigCounter:DB$ PutCounter | ValidTgts$ Creature | CounterType$ P1P1 | CounterNum$ 1
```

---

## 💀 Death / Leaves Battlefield Triggers

### 1. When this creature dies, draw a card
```text
T:Mode$ ChangesZone | Origin$ Battlefield | Destination$ Graveyard | ValidCard$ Card.Self | Execute$ TrigDraw | TriggerDescription$ When CARDNAME dies, draw a card.
SVar:TrigDraw:DB$ Draw | Defined$ You | NumCards$ 1
```

---

## ⚡ Activated Abilities

### 1. Tap: Add Green Mana (`Llanowar Elves`)
```text
A:AB$ Mana | Cost$ T | Produced$ G | Amount$ 1 | SpellDescription$ Add {G}.
```

### 2. Pay {1}{R}, Tap: Deals 1 damage to any target
```text
A:AB$ DealDamage | Cost$ 1 R T | ValidTgts$ Creature,Player,Planeswalker | NumDmg$ 1 | SpellDescription$ CARDNAME deals 1 damage to any target.
```

### 3. Pay {G}: Pump self +1/+1 until end of turn
```text
A:AB$ Pump | Cost$ G | ValidTgts$ Card.Self | NumPower$ 1 | NumToughness$ 1 | SpellDescription$ CARDNAME gets +1/+1 until end of turn.
```

---

## 🕒 Phase / Turn Triggers

### 1. At the beginning of your upkeep, gain 1 life
```text
T:Mode$ Phase | Phase$ Upkeep | ValidPlayer$ You | Execute$ TrigGainLife | TriggerDescription$ At the beginning of your upkeep, you gain 1 life.
SVar:TrigGainLife:DB$ GainLife | Defined$ You | LifeAmount$ 1
```

### 2. At the beginning of your end step, each opponent loses 1 life
```text
T:Mode$ Phase | Phase$ End | ValidPlayer$ You | Execute$ TrigDrain | TriggerDescription$ At the beginning of your end step, each opponent loses 1 life.
SVar:TrigDrain:DB$ LoseLife | Defined$ Opponent | LifeAmount$ 1
```

---

## 🪄 Instant & Sorcery Spells

### 1. Destroy target creature
```text
A:SP$ Destroy | ValidTgts$ Creature | SpellDescription$ Destroy target creature.
```

### 2. Counter target spell
```text
A:SP$ Counter | ValidTgts$ Spell | SpellDescription$ Counter target spell.
```

### 3. Create a 1/1 red Goblin creature token
```text
A:SP$ Token | TokenAmount$ 1 | TokenName$ Goblin | TokenTypes$ Creature Goblin | TokenColors$ Red | TokenPower$ 1 | TokenToughness$ 1 | SpellDescription$ Create a 1/1 red Goblin creature token.
```
