# Forge Edition/Set Definition Format

Custom editions (sets) are defined in `.txt` files inside the `editions/` folder. They use an INI-style sections layout parsed by [CardEdition.java](file:///d:/Forge/forge-latest/forge-core/src/main/java/forge/card/CardEdition.java).

---

## 📋 Structure of an Edition File

An edition file is split into three main sections: `[metadata]`, `[cards]`, and `[tokens]`.

### 1. `[metadata]` Section (Required)
Defines properties of the set.
- **`Code`** (Required): A unique 3-to-4 character alphanumeric set code (e.g. `DIY1`, `MS01`).
- **`Name`** (Required): The full name of the custom set.
- **`Date`** (Required): Release date formatted as `YYYY-MM-DD`.
- **`Type`** (Optional): For custom sets, this is automatically forced to `CUSTOM_SET` by the game engine, but you can write `Type=Custom_Set` for clarity.

Example:
```ini
[metadata]
Code=DIY1
Name=My First DIY Set
Date=2026-07-10
Type=Custom_Set
```

### 2. `[cards]` Section (Required)
Lists the cards that belong to this set. Each card has its own line.
- **Format**: `<collector_number> <rarity> <card_name> [@<artist_name>]`
- **Rarities**: 
  - `C` = Common
  - `U` = Uncommon
  - `R` = Rare
  - `M` = Mythic
  - `L` = Basic Land
  - `S` = Special

Example:
```ini
[cards]
1 M Forge Test Goblin @Gemini AI
2 C Llanowar Elves
3 L Plains
```

*Note: The artist name `@Artist` is optional but helpful if you want to assign specific card art.*

### 3. `[tokens]` Section (Optional)
Lists any custom tokens used by cards in this set. Refers to token script filenames in the `tokens/` folder without the `.txt` extension.
- **Example**:
  ```ini
  [tokens]
  r_1_1_goblin
  w_1_1_soldier
  c_a_treasure_sac
  ```

---

## ⚠️ Important Rules
1. **Filename**: The filename of the edition file must be the full name of the set (matching the `Name` property in metadata) or a simplified version.
2. **Duplicate Names**: Do not list a card that has the same name as an official card unless you intend to print a custom reprint/override. If you want a custom version of an existing card, append a suffix like `(Custom)` or `(DIY)` to the name.
3. **Collector Numbers**: Collector numbers must be unique integers within the set.
