# 冰霜女巫吉安娜 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 冰霜女巫吉安娜 as PH01 #53 with all four requested abilities, a vanilla 3/6 blue Elemental token with supplied art, Chinese display text, the supplied Jaina artwork in Forge's dynamic crop format, and the same Jaina artwork on her emblem.

**Architecture:** Keep the implementation entirely in existing Forge DSL. Reuse the official `Skeletonize` delayed-trigger pattern for the -1 death check, a Command-zone effect with `Mode$ Taps` for the emblem, and a Command-zone continuous ability to give the emblem itself lifelink.

**Tech Stack:** Forge card DSL, Python `unittest`, Pillow image conversion, PowerShell deployment.

---

### Task 1: Add the failing contract test

**Files:**
- Create: `tests/test_frost_lich_jaina.py`

- [x] **Step 1: Write the failing test**

Create assertions for `Name:冰霜女巫吉安娜`, `ManaCost:2 U U`, `Types:Legendary Planeswalker Jaina`, `Loyalty:3`, the Elemental lifelink static ability, all three loyalty abilities, `u_3_6_elemental`, PH01 #53, the RGB 1.37:1 art crop, and the exact zh-CN row.

- [x] **Step 2: Run test to verify it fails**

Run: `python -m unittest tests.test_frost_lich_jaina -v`

Expected: FAIL because `cards/blue/冰霜女巫吉安娜.txt` and its assets do not exist.

### Task 2: Implement the card and token

**Files:**
- Create: `cards/blue/冰霜女巫吉安娜.txt`
- Create: `tokens/u_3_6_elemental.txt`

- [x] **Step 1: Write the minimal card script**

Use these verified native patterns:

```text
S:Mode$ Continuous | Affected$ Creature.Elemental+YouCtrl | AddKeyword$ Lifelink
A:AB$ Tap | Cost$ AddCounter<1/LOYALTY> | Planeswalker$ True | ValidTgts$ Permanent | SubAbility$ DBStun
A:AB$ DealDamage | Cost$ SubCounter<1/LOYALTY> | Planeswalker$ True | ValidTgts$ Any | NumDmg$ 1 | RememberDamaged$ True | SubAbility$ DBDelayedTrigger
A:AB$ Effect | Cost$ SubCounter<6/LOYALTY> | Planeswalker$ True | Ultimate$ True | Name$ Emblem — 冰霜女巫吉安娜 | Image$ emblem_frost_lich_jaina | StaticAbilities$ EmblemLifelink | Triggers$ EmblemTapTrigger | Duration$ Permanent
```

Implement `DBDelayedTrigger` with `RememberObjects$ Remembered`, `Card.IsTriggerRemembered`, `ThisTurn$ True`, and cleanup exactly as used by the official `Skeletonize` script. Implement the emblem's damage as `Defined$ TriggeredCardLKICopy`.

- [x] **Step 2: Add the vanilla token**

```text
Name:Elemental Token
ManaCost:no cost
Colors:blue
Types:Creature Elemental
PT:3/6
Oracle:
```

- [x] **Step 3: Run the focused test**

Run: `python -m unittest tests.test_frost_lich_jaina -v`

Expected: remaining failures only for registration, art, and translation.

### Task 3: Register display and artwork resources

**Files:**
- Modify: `editions/Placeholder_Set.txt`
- Modify: `../forge-gui/res/languages/cardnames-zh-CN.txt`
- Modify: `CARDS.md`
- Create: `tools/card-artwork/Art_ICC_833.png`
- Create: `cards/pictures/PH01/冰霜女巫吉安娜.artcrop.jpg`
- Create: `tokens/pictures/emblem_frost_lich_jaina.png`
- Create: `tools/card-artwork/images.jpg`
- Create: `tokens/pictures/u_3_6_elemental.jpg`

- [x] **Step 1: Register the card**

Append `53 M 冰霜女巫吉安娜 @Custom` to PH01 and add the card to the gameplay table in `CARDS.md`.

- [x] **Step 2: Add exact Chinese display text**

Add one row whose display type is `传奇鹏洛客～吉安娜`; normalize the -1 sentence to the rules-template form “当一个本回合中曾以此法受到伤害的生物死去时，派出一个3/6蓝色元素衍生生物。” while preserving the requested mechanics.

- [x] **Step 3: Back up and crop the supplied art**

Copy `C:\Users\Marsh\Desktop\Art_ICC_833.png` unchanged to `tools/card-artwork/Art_ICC_833.png`. Center-crop the square source slightly upward to 512×374 and save a high-quality RGB JPEG at `cards/pictures/PH01/冰霜女巫吉安娜.artcrop.jpg`.

Copy the original PNG unchanged to `tokens/pictures/emblem_frost_lich_jaina.png`; `Image$ emblem_frost_lich_jaina` resolves it from Forge's token-image cache, so the emblem reuses the exact supplied Jaina art.

Copy `C:\Users\Marsh\Desktop\images.jpg` unchanged to `tools/card-artwork/images.jpg`. Crop only 10 vertical pixels to produce a 498×363 RGB JPEG at `tokens/pictures/u_3_6_elemental.jpg`, matching the `TokenScript$ u_3_6_elemental` lookup key.

- [x] **Step 4: Run focused validation**

Run:

```powershell
python -m unittest tests.test_frost_lich_jaina -v
python tools\lint_card.py cards\blue\冰霜女巫吉安娜.txt
```

Expected: PASS.

### Task 4: Regress, deploy, verify, and save

**Files:**
- Modify: `VERIFICATION.md`

- [x] **Step 1: Run the full DIY suite**

Run: `python -m unittest discover -s tests -p "test_*.py"`

Expected: all tests pass.

- [x] **Step 2: Deploy custom resources**

Run: `powershell -ExecutionPolicy Bypass -File .\tools\install_to_forge.ps1`

Expected: script, PH01 edition, token, and art synchronize successfully.

- [x] **Step 3: Verify deployed hashes**

Compare SHA-256 for the card script, token, edition, and art between the source tree and `%APPDATA%\Forge\custom` / `%LOCALAPPDATA%\Forge\Cache\pics\cards\PH01`.

- [ ] **Step 4: Record evidence and commit only this card's files**

Append current-run evidence to `VERIFICATION.md`, stage only the new Jaina files and their clean shared-file hunks, review `git diff --cached`, and create a local commit. Do not push this card-only change.
