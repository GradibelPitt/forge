# Deathstalker Rexxar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `死亡猎手雷克萨` as a fully registered, localized, illustrated PH01 planeswalker whose mutate reduction, ETB debuff, temporary mutate grant, token, and two-Beast discovery abilities use existing Forge DSL.

**Architecture:** Keep all gameplay in a new custom card script and a dedicated token script. Reuse `Spell.Mutate` cost filtering, `Mutate:CardManaCost`, two chained `CardDiscover` resolutions, and a permanent remembered-card permission effect; no Java changes are required. Add contract coverage for script semantics, registration, localization, and artwork, then deploy through the existing installer.

**Tech Stack:** Forge card DSL, Python `unittest`, Pillow, PowerShell deployment tooling.

---

### Task 1: Add the failing contract

**Files:**
- Create: `custom/tests/test_deathstalker_rexxar.py`

- [ ] **Step 1: Write the failing test**

Create a contract test that requires:

```python
CARD = ROOT / "cards" / "multicolor" / "死亡猎手雷克萨.txt"
TOKEN = ROOT / "tokens" / "bg_1_1_zombie_beast_mutate.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "死亡猎手雷克萨.artcrop.jpg"
TOKEN_ART = ROOT / "tokens" / "pictures" / "bg_1_1_zombie_beast_mutate.jpg"
BACKUP = ROOT / "tools" / "card-artwork" / "codex-clipboard-d33f4f1a-59f2-4cff-82c3-d2001279aa2e.png"
```

Assert the card has `{3}{B}{G}`, types `Legendary Planeswalker Rexxar`, loyalty 5, a `ReduceCost` static ability restricted by `ValidSpell$ Spell.Mutate`, the Massacre Wurm ETB `PumpAll`, a `+1` Effect that remembers `ValidExile Card.ExiledWithSource+Creature`, grants `Mutate:CardManaCost` until end of turn, and creates the custom token, plus two chained Beast `CardDiscover` resolutions into exile followed by a permanent `MayPlayIgnoreColor$ True` permission Effect. Assert PH01 collector number 58, the Chinese localization row, RGB landscape JPEG assets, and the original backup.

- [ ] **Step 2: Run test to verify it fails**

Run: `python -m unittest tests.test_deathstalker_rexxar -v`

Expected: FAIL because the new card, token, registration, localization, and artwork do not exist.

### Task 2: Implement scripts, registration, localization, and artwork

**Files:**
- Create: `custom/cards/multicolor/死亡猎手雷克萨.txt`
- Create: `custom/tokens/bg_1_1_zombie_beast_mutate.txt`
- Create: `custom/cards/pictures/PH01/死亡猎手雷克萨.artcrop.jpg`
- Create: `custom/tokens/pictures/bg_1_1_zombie_beast_mutate.jpg`
- Create: `custom/tools/card-artwork/codex-clipboard-d33f4f1a-59f2-4cff-82c3-d2001279aa2e.png`
- Modify: `custom/editions/Placeholder_Set.txt`
- Modify: `forge-gui/res/languages/cardnames-zh-CN.txt`

- [ ] **Step 1: Add the minimal card and token DSL**

Use these verified DSL shapes:

```text
S:Mode$ ReduceCost | ValidCard$ Creature | ValidSpell$ Spell.Mutate | Type$ Spell | Activator$ You | Amount$ 2
SVar:TrigMassacre:DB$ PumpAll | NumAtt$ -2 | NumDef$ -2 | ValidCards$ Creature.OppCtrl | IsCurse$ True
SVar:GrantMutate:Mode$ Continuous | Affected$ Creature.IsRemembered | AffectedZone$ Exile | AddKeyword$ Mutate:CardManaCost
SVar:MayPlay:Mode$ Continuous | MayPlay$ True | MayPlayIgnoreColor$ True | Affected$ Card.IsRemembered | AffectedZone$ Exile
```

Chain two `CardDiscover` abilities with `ValidCards$ Creature.Beast`, `OptionCount$ 3`, `Destination$ Exile`, and `RememberChosen$ True`; then create a permanent Effect with `ForgetOnMoved$ Exile` and clear the planeswalker's remembered list.

The token is black-green, 1/1, `Creature Zombie Beast`, and uses the official `Mutates` trigger with `TriggeredCardLKICopy` to add one +1/+1 counter.

- [ ] **Step 2: Register and localize**

Add `58 M 死亡猎手雷克萨 @Custom` to PH01. Add one exact `zh-CN` row with `传奇鹏洛客～雷克萨` and the normalized user wording, correcting only the duplicated `牌牌` and using standard loyalty prefixes.

- [ ] **Step 3: Process the supplied art without generation**

Copy the source PNG unchanged to `tools/card-artwork`. Crop the full composition to a landscape 816x600 RGB JPEG for the planeswalker. Crop the left-side undead beast to a landscape 400x292 RGB JPEG for the custom token.

- [ ] **Step 4: Run the focused test**

Run: `python -m unittest tests.test_deathstalker_rexxar -v`

Expected: PASS.

### Task 3: Update authoritative documentation and verify

**Files:**
- Modify: `custom/CARDS.md`
- Modify: `custom/VERIFICATION.md`

- [ ] **Step 1: Document the implemented card**

Add PH01 58 to `CARDS.md`, summarizing all four rules components and the custom token. Add a dated verification entry only after collecting fresh command results.

- [ ] **Step 2: Run focused lint and tests**

Run:

```powershell
python tools\lint_card.py cards\multicolor\死亡猎手雷克萨.txt
python -m unittest tests.test_deathstalker_rexxar -v
```

Expected: card lint passes and all Rexxar contract tests pass.

- [ ] **Step 3: Run the full DIY suite**

Run: `python -m unittest discover -s tests -p "test_*.py"`

Expected: all tests pass with zero failures.

- [ ] **Step 4: Deploy and compare hashes**

Run: `powershell -ExecutionPolicy Bypass -File .\tools\install_to_forge.ps1`

Compare SHA-256 for the card script and card art between source and the Forge profile/cache, and verify the deployed token script/art and PH01 registration exist.

- [ ] **Step 5: Review scope and commit only this card batch**

Inspect `git diff --check`, `git status --short`, and the scoped diff. Stage only Rexxar-related scripts, tests, assets, PH01/localization/docs entries, and this plan; create a local commit without pushing.
