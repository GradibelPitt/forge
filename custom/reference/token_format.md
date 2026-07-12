# Forge Token Script Format

Tokens in Forge are defined in text files placed in the `tokens/` directory. They use the same script format as cards but have specific filename conventions.

---

## 🏷️ Filename Convention
Forge automatically maps token generation effects to script filenames using a structured naming convention:

### Format
```text
<color>_<power>_<toughness>_<subtype>[_keywords].txt
```

### Components
1. **`<color>`**: The color(s) of the token:
   - `w` = White
   - `u` = Blue
   - `b` = Black
   - `r` = Red
   - `g` = Green
   - `c` = Colorless
   - Multiple colors are joined without separators (e.g. `wu` for White-Blue, `br` for Black-Red).
2. **`<power>`** and **`<toughness>`**: Integers representing stats (e.g., `1_1` or `2_2`). Use `0_0` for non-creature tokens.
3. **`<subtype>`**: The main creature/artifact subtype (e.g., `soldier`, `goblin`, `treasure`).
4. **`[_keywords]`** (Optional): Appended if the token has keyword abilities (e.g., `_flying`, `_haste`).

### Examples
- **1/1 White Soldier with Flying**: `w_1_1_soldier_flying.txt`
- **2/2 Red Goblin with Haste**: `r_2_2_goblin_haste.txt`
- **Colorless Artifact Treasure**: `c_a_treasure_sac.txt` (a special name)

---

## 📝 Scripting a Token
Token scripts require the following properties:

- `Name`: The displayed name of the token (e.g. `Soldier Token`).
- `ManaCost`: Always `no cost`.
- `Colors`: Space-separated list of colors (e.g. `white`, `red colorless`, `green blue`).
- `Types`: Supertypes, types, and subtypes (e.g. `Creature Soldier`, `Artifact Treasure`).
- `PT` (Optional): Power/Toughness (e.g. `1/1`).
- `K` (Optional): Keyword abilities (one per line).
- `A` / `T` / `S` / `R` (Optional): Abilities/triggers/static effects.

### Example: White 1/1 Soldier Token (`w_1_1_soldier.txt`)
```text
Name:Soldier Token
ManaCost:no cost
Colors:white
Types:Creature Soldier
PT:1/1
Oracle:
```

### Example: Colorless Treasure Token (`c_a_treasure_sac.txt`)
```text
Name:Treasure Token
ManaCost:no cost
Types:Artifact Treasure
A:AB$ Mana | Cost$ T Sac<1/CARDNAME/this artifact> | Produced$ Any | Amount$ 1 | SpellDescription$ Add one mana of any color.
Oracle:{T}, Sacrifice this artifact: Add one mana of any color.
```
