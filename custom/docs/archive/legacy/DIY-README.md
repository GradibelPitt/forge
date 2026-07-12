# Forge MTG DIY Card Workspace

Welcome to the Forge DIY Card development workspace.

This workspace is designed for creating, organizing, testing, and maintaining custom Magic: The Gathering cards for the Forge engine. Custom cards are kept separate from Forge’s main installation folder, making the project:

* Git-trackable
* Safe from Forge updates
* Easy to share or back up
* Suitable for AI coding agents
* Easier to lint, test, and maintain

The Magic Comprehensive Rules define **what a card should do**. Forge source code, scripting documentation, and existing card scripts define **how Forge can implement it**.

Both layers must be checked before a custom card is considered complete.

---

## Directory Structure

```text
Forge-DIY-Workspace/
├─ cards/
│  ├─ white/
│  ├─ blue/
│  ├─ black/
│  ├─ red/
│  ├─ green/
│  ├─ multicolor/
│  ├─ colorless/
│  └─ lands/
│
├─ editions/
│  └─ Placeholder_Set.txt
│
├─ tokens/
│
├─ reference/
│  ├─ mtg_rules/
│  ├─ forge_engine/
│  ├─ forge_docs/
│  └─ card_examples/
│
├─ tools/
│  ├─ install_to_forge.ps1
│  ├─ new_card.py
│  ├─ find_similar_cards.py
│  └─ lint_card_scripts.py
│
├─ tests/
│  └─ game_states/
│
├─ AGENTS.md
└─ README.md
```

### `cards/`

Contains all custom Forge card scripts.

Cards may be organized by color, card type, custom set, or another project-specific structure. Forge supports recursively loading `.txt` card scripts from subdirectories, although Forge’s built-in Workshop may not locate deeply nested files correctly.

Each card should normally use a lowercase filename with underscores:

```text
goblin_card_guide.txt
ancient_clockwork_engine.txt
```

### `editions/`

Contains custom set and edition definition files.

Each playable custom card should be listed in an edition file with a unique collector number, rarity, card name, and artist where applicable.

Example:

```text
[metadata]
Code=DIY
Code2=DY
Name=DIY Test Set
Type=Custom
Date=2026-01-01

[cards]
1 C Goblin Card Guide @Custom Artist
2 R Ancient Clockwork Engine @Custom Artist
```

### `tokens/`

Contains Forge token scripts required by custom cards.

Custom tokens should be stored separately from normal card scripts and included in the installation process.

### `reference/`

Contains documentation and source material used by humans and AI coding agents.

Recommended structure:

```text
reference/
├─ mtg_rules/
│  ├─ MagicCompRules.txt
│  ├─ keyword_actions_701.txt
│  ├─ keyword_abilities_702.txt
│  └─ glossary.txt
│
├─ forge_engine/
│  ├─ Keyword.java
│  ├─ CardScriptParser.java
│  └─ NonStackingKWList.txt
│
├─ forge_docs/
│  ├─ Card-scripting-API.md
│  ├─ AbilityFactory.md
│  ├─ Triggers.md
│  ├─ Replacements.md
│  ├─ Statics.md
│  └─ Targeting.md
│
└─ card_examples/
   └─ cardsfolder/
```

### `tools/`

Contains scripts used to automate repetitive development tasks.

Typical tools include:

* Installing or synchronizing the workspace to Forge
* Creating new card templates
* Searching official Forge cards for similar implementations
* Validating card script structure
* Detecting unresolved `SVar` references
* Checking edition entries
* Preparing test files

### `tests/game_states/`

Contains saved Developer Mode game states and other reproducible test scenarios.

Use these files to test:

* Enter-the-battlefield triggers
* Death triggers
* Replacement effects
* Activated abilities
* Combat interactions
* Zone changes
* Multiplayer behavior
* Edge cases involving targets and remembered objects

### `AGENTS.md`

Contains the full operating instructions for AI coding agents.

AI agents should read `AGENTS.md` before creating or modifying card scripts.

---

## Setup and Workflow

### 1. Add Reference Material

Place the current Magic Comprehensive Rules and relevant Forge documentation under `reference/`.

At minimum, the project should provide access to:

```text
MagicCompRules.txt
Keyword.java
Card-scripting-API.md
AbilityFactory.md
Official Forge card scripts
```

Additional documentation for triggers, replacements, statics, targeting, and parser behavior is strongly recommended.

### 2. Write a Card

Place card scripts under `cards/`, or generate a template using:

```powershell
python .\tools\new_card.py
```

A basic Forge card script may look like:

```text
Name:Goblin Card Guide
ManaCost:1 R
Types:Creature Goblin
PT:2/2
K:Haste
Oracle:Haste
```

More complex cards may use:

```text
K:
A:
T:
S:
R:
SVar:
Oracle:
```

### 3. Add the Card to an Edition

Make sure the exact card name is listed in an edition file under `editions/`.

The name in the edition file must match the card script’s `Name:` field.

Collector numbers must be unique within the edition.

### 4. Lint the Scripts

Run the available linting tools before installation:

```powershell
python .\tools\lint_card_scripts.py
```

A linter should check as much as possible, including:

* Required card fields
* File naming
* Duplicate card names
* Missing edition entries
* Invalid script prefixes
* Missing `SVar` references
* Unresolved `Execute`
* Unresolved `SubAbility`
* Unresolved `ReplaceWith`
* Suspicious Oracle-only behavior
* Duplicate collector numbers

Passing the linter does not prove that the card follows Magic rules correctly.

### 5. Install to Forge

Run the synchronization tool:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\install_to_forge.ps1
```

The installer should copy or synchronize:

```text
cards/    → Forge/custom/cards/
editions/ → Forge/custom/editions/
tokens/   → Forge/custom/tokens/
```

The source workspace should remain the authoritative project copy. Avoid editing installed Forge files directly.

### 6. Test in Forge

Open Forge and enable Developer Mode.

Verify that:

* The card appears in the card database
* The card appears under the intended set
* The card can be added to a deck
* Costs and targets are correct
* Trigger conditions occur at the correct time
* Effects use the correct zones
* Replacement effects replace the correct event
* Oracle text matches actual behavior
* The AI does not make obviously broken decisions

Restart Forge after adding new files if the card database is not automatically refreshed.

---

## Source-of-Truth Hierarchy

When implementing or reviewing a card, use sources in the following order.

### 1. Magic Comprehensive Rules

The Comprehensive Rules are the authority for actual Magic behavior.

Use them to determine:

* Timing
* Priority
* Targets
* Costs
* Zones
* State-based actions
* Layers
* Trigger conditions
* Replacement effects
* Keyword actions
* Keyword abilities
* Multiplayer interactions

Important sections include:

* Rule 601: Casting spells
* Rule 603: Handling triggered abilities
* Rule 614: Replacement effects
* Rule 616: Interaction of replacement effects
* Rule 701: Keyword actions
* Rule 702: Keyword abilities
* Rule 704: State-based actions
* Rule 613: Continuous-effect layers
* Comprehensive Rules Glossary

The rules do not prove that Forge supports a mechanic.

### 2. Forge Engine Source Code

Forge source code is the authority for what the installed Forge version can actually execute.

Important files include:

```text
Keyword.java
CardScriptParser.java
NonStackingKWList.txt
ApiType.java
TriggerType.java
ReplacementType.java
```

Use `Keyword.java` to determine:

* Whether Forge recognizes a keyword
* The exact keyword spelling
* Whether a keyword requires a cost
* Whether it requires an amount
* Whether it requires a card type
* Whether it uses a specialized implementation class

Common keyword implementation classes include:

* `SimpleKeyword`
* `KeywordWithAmount`
* `KeywordWithCost`
* `KeywordWithType`
* Specialized mechanic-specific classes

Do not infer valid Forge `K:` syntax from the Magic rules alone.

### 3. Forge Scripting Documentation

Use Forge scripting documentation to understand card script structure and supported parameters.

Relevant documents include:

```text
Card-scripting-API.md
AbilityFactory.md
Triggers.md
Replacements.md
Statics.md
Targeting.md
```

Documentation may be incomplete or outdated, so verify uncertain syntax against current source code and existing cards.

### 4. Existing Forge Card Scripts

Existing official Forge card scripts are usually the most reliable practical implementation examples.

For every complex custom effect:

1. Find an official card with similar behavior.
2. Inspect its current Forge script.
3. Identify the relevant trigger, API, target, and `SVar` structure.
4. Adapt the smallest possible portion.
5. Avoid rewriting proven behavior from scratch.

Search by mechanical fragments, not only card names.

Examples:

```text
Mode$ ChangesZone
Destination$ Battlefield
Mode$ DamageDone
DB$ Draw
DB$ PutCounter
CounterType$
ValidTgts$
Defined$
SubAbility$
ReplaceWith$
```

---

## Forge Script Prefixes

Common script prefixes include:

```text
K:       Keyword ability
A:       Activated or spell ability
T:       Triggered ability
S:       Static ability
R:       Replacement effect
SVar:    Reusable value, calculation, or referenced sub-ability
Oracle:  Displayed rules text
```

Oracle text does not implement card behavior.

A card is not functional merely because its Oracle wording is correct.

---

## Card Authoring Process

For each requested card, follow this process.

### Step 1: Interpret the Card as Magic Rules

Determine:

* What objects are affected
* Whether the effect targets
* When the effect occurs
* Which zone each object is in
* Whether the ability is optional
* Whether a cost is required
* Whether a replacement effect is involved
* Whether multiple instructions happen sequentially
* Whether intervening-if conditions apply
* Whether the effect creates a delayed trigger

### Step 2: Classify Each Ability

Identify whether each component is:

* A keyword ability
* An activated ability
* A triggered ability
* A static ability
* A replacement effect
* A spell ability
* A keyword action
* A linked ability
* A characteristic-defining ability

One card may require several different script systems.

### Step 3: Check Forge Support

Determine whether Forge already supports the required behavior through:

* `K:`
* AbilityFactory APIs
* Trigger types
* Replacement types
* Static ability modes
* Existing `SVar` calculations
* Remembered or imprinted objects
* Chosen values
* Existing AI logic

### Step 4: Find Similar Cards

Search official Forge scripts for the closest implementation.

Use more than one example when the card combines multiple unrelated mechanics.

### Step 5: Implement the Smallest Valid Script

Reuse verified patterns and change only:

* Costs
* Numeric values
* Targets
* Validity restrictions
* Zones
* Descriptions
* Optionality
* Timing restrictions

### Step 6: Validate References

Every referenced identifier must exist.

Check:

```text
Execute$
SubAbility$
ReplaceWith$
SVar:
AdditionalAbility$
OptionalDecider$
```

Also confirm that referenced values use the expected data type.

### Step 7: Test Rules and Gameplay

Test both normal behavior and edge cases.

---

## Mandatory Agent Rules

AI coding agents must never:

* Invent Forge parameter names
* Invent undocumented `K:` syntax
* Assume an official Magic keyword is supported by Forge
* Treat Oracle text as executable logic
* Assume a script is correct only because Forge loads it
* Replace targeting with `Defined` unless the effect is intentionally non-targeting
* Add Java engine changes before checking existing script support
* Copy an outdated implementation without checking current Forge files
* Silently approximate unsupported rules behavior
* Modify official Forge card scripts as the primary implementation method

When uncertain, the agent must search current Forge source code and existing card scripts.

---

## Targeting vs. Defined Objects

Forge distinguishes between targeted and non-targeted effects.

Use targeting fields such as:

```text
ValidTgts$
TargetMin$
TargetMax$
TgtPrompt$
```

when the Magic effect uses the word “target.”

Use fields such as:

```text
Defined$
ValidCards$
Affected$
```

for non-targeting object selection or previously established objects.

This distinction affects:

* Hexproof
* Shroud
* Ward
* Protection
* Retargeting
* Fizzling
* Legality checks
* Trigger resolution

Do not change a targeted effect into a non-targeted effect merely because it is easier to script.

---

## Unsupported Mechanics

Some custom mechanics cannot be fully represented with existing Forge scripting APIs.

Java engine changes may be required when a mechanic introduces:

* A new game zone
* A new game object type
* A new turn step or phase
* A trigger event Forge does not track
* A replacement event Forge cannot represent
* A new combat rule
* Persistent information Forge cannot store
* A new casting procedure
* A new cost type
* A fundamentally new card state
* AI behavior that existing hints cannot express

When a requested effect is unsupported, report:

1. Which part cannot be implemented.
2. Which Forge APIs and existing cards were checked.
3. Whether a partial approximation is possible.
4. How the approximation differs from the intended rules.
5. Which Java subsystem would likely require extension.

Never silently substitute different behavior.

---

## Validation Levels

Each custom card should be validated at three levels.

### 1. Syntax Validation

Confirm that:

* Forge loads the card
* Required fields are present
* Parameters are recognized
* Referenced `SVar` entries exist
* Card and edition names match
* The card can be found in the database

### 2. Rules Validation

Confirm that:

* Targets are correct
* Costs are correct
* Timing is correct
* Zones are correct
* Trigger events are correct
* Replacement effects modify the intended event
* Continuous effects apply in the correct layer
* “May” effects are optional
* “If you do” dependencies are preserved
* Oracle wording matches implementation

### 3. Gameplay Validation

Confirm through Forge Developer Mode that:

* The effect resolves correctly
* Edge cases behave correctly
* Multiplayer behavior is acceptable
* Repeated triggers do not occur accidentally
* Temporary effects expire correctly
* Objects are remembered and forgotten correctly
* The AI can use the card without obvious failures

Successful loading does not guarantee correct gameplay behavior.

---

## Similar-Card Search Strategy

Search official card scripts using implementation fragments.

### Zone Changes

```text
Mode$ ChangesZone
Origin$
Destination$
ValidCard$
```

### Damage

```text
Mode$ DamageDone
ValidSource$
ValidTarget$
CombatDamage$
```

### Card Draw

```text
DB$ Draw
NumCards$
Defined$
```

### Counters

```text
DB$ PutCounter
CounterType$
CounterNum$
```

### Tokens

```text
DB$ Token
TokenScript$
TokenAmount$
```

### Continuous Effects

```text
S:Mode$
Affected$
AddPower$
AddToughness$
AddKeyword$
```

### Replacement Effects

```text
R:Event$
ReplaceWith$
Description$
```

### Linked Sub-Abilities

```text
SubAbility$
Execute$
SVar:
```

---

## Git Workflow

The workspace directory should be treated as the source of truth.

Recommended practice:

```text
Edit in workspace
→ lint
→ review diff
→ install/sync to Forge
→ test
→ commit
```

Do not treat files copied into Forge’s `custom` directory as the authoritative source.

Suggested `.gitignore` entries:

```gitignore
__pycache__/
*.pyc
*.log
.cache/
tmp/
build/
dist/
local_config.json
forge_path.local
```

Local Forge installation paths and machine-specific configuration should not be committed.

---

## Core Principle

The Magic rules define:

> What the card must do.

Forge engine code, documentation, and official scripts define:

> How Forge can implement it.

A custom card is complete only when its script is syntactically valid, its behavior matches the intended Magic rules, and it has been tested in Forge.
