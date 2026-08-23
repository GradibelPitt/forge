# Forge Keyword Abilities Reference (`K`)

This document lists valid keyword abilities supported in Forge. These are declared in card scripts using the `K:` prefix (one per line).

---

## 🏃 Simple Keywords
These keywords do not take parameters. Write them exactly as shown:

- `K:Flying`
- `K:Haste`
- `K:Vigilance`
- `K:Trample`
- `K:Lifelink`
- `K:Deathtouch`
- `K:Defender`
- `K:Indestructible`
- `K:First Strike`
- `K:Double Strike`
- `K:Reach`
- `K:Superreach` (can block despite restrictions created by the attacking creature)
- `K:Ignore Superreach` (the attacker's restrictions still apply against Superreach blockers)
- `K:IgnoreDeckLimits` (in a non-Commander main deck, sets the minimum size to one and ignores all same-name copy limits)
- `K:Menace` (cannot be blocked except by two or more creatures)
- `K:Flash`
- `K:Shroud` (cannot be the target of spells or abilities)
- `K:Wither` (deals damage to creatures in the form of -1/-1 counters)
- `K:Infect` (deals damage to creatures as -1/-1 counters and players as poison counters)
- `K:Cascade`
- `K:Changeling` (is every creature type)
- `K:Ascend`
- `K:Ward`
- `K:Phasing`
- `K:Vigilance`

---

## 💰 Keywords with Cost
These keywords require a cost value (mana and/or other resources) separated by a colon (`:`):

- `K:Cycling:<Mana>`
  - *Example*: `K:Cycling:2`
- `K:Flashback:<Mana>`
  - *Example*: `K:Flashback:1 R`
- `K:Kicker:<Mana>`
  - *Example*: `K:Kicker:1 G`
- `K:Equip:<Mana>`
  - *Example*: `K:Equip:2`
- `K:Level up:<Mana>`
  - *Example*: `K:Level up:1 U`
- `K:Madness:<Mana>`
  - *Example*: `K:Madness:B`
- `K:Morph:<Mana>` (cast face down for {3}, turn face up for morph cost)
  - *Example*: `K:Morph:G`
- `K:Disguise:<Mana>` (cast face down for {3} with Ward {2}, turn face up for disguise cost)
  - *Example*: `K:Disguise:1 W`
- `K:Evoke:<Mana>` (cast for evoke cost, sacrificed when it enters)
  - *Example*: `K:Evoke:U`
- `K:Plot:<Mana>` (exile face up, cast as a sorcery on a later turn for free)
  - *Example*: `K:Plot:1 R`

---

## 🤝 Grouped Partner

- `K:Partner:<Group>`
  - *Example*: `K:Partner:探险者协会`
  - Two legendary cards with the exact same non-empty `<Group>` value can be designated as a player's two commanders. Typed Partner does not pair with ordinary `K:Partner` or a different group.

---

## 🔢 Keywords with Amount or Type
These keywords take a numeric amount or card type constraint:

- `K:Annihilator:<Amount>`
  - *Example*: `K:Annihilator:2` (defending player sacrifices two permanents)
- `K:Toxic:<Amount>`
  - *Example*: `K:Toxic:1`
- `K:Afflict:<Amount>`
  - *Example*: `K:Afflict:3`
- `K:Crew:<Amount>` (tap creatures with total power equal to or greater than amount to animate vehicle)
  - *Example*: `K:Crew:2`
- `K:Saddle:<Amount>`
  - *Example*: `K:Saddle:3`
- `K:Devour:<Amount>`
  - *Example*: `K:Devour:2`
- `K:Boarding:<Amount>`
  - *Example*: `K:Boarding:3`
  - Tracks different friendly damageable entities by ID for the current turn. Once the threshold is met, cards with Boarding move directly from their owner's hand or library to the battlefield without using the stack.
- `K:Enchant:<Type>` (defines target for Auras)
  - *Example*: `K:Enchant:Creature`
- `K:Landwalk:<Type>`
  - *Example*: `K:Landwalk:Forest`
- `K:Champion:<Type>`
  - *Example*: `K:Champion:Elf`
- `K:Ward:<Cost>`
  - *Example*: `K:Ward:2` or `K:Ward:PayLife<2>`

---

## ⚠️ Notes on Keyword Syntax
1. **Case Sensitivity**: Forge's keyword parser is case-insensitive, but writing them exactly as declared in `Keyword.java` (e.g. `First Strike` or `Double Strike`) is best practice.
2. **Spacing**: Watch for colons and parameters. For example: `K:Flying` has no colon after the keyword, but `K:Cycling:2` does.
