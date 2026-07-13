# Chaos Tentacle Real Card Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert 混乱触须 from a token into a `{1}` PH01 real artifact card and make 脱困古神尤格萨隆 conjure one or six copies onto the battlefield.

**Architecture:** The card database becomes the sole definition of 混乱触须. Yogg uses Forge's existing `MakeCard`/`Conjure` DSL to create database-backed card objects, so sacrificed copies move to the graveyard and satisfy the existing `Count$ValidGraveyard` expression without an engine exception.

**Tech Stack:** Forge card-script DSL, Python `unittest` contract tests, PowerShell deployment, Git.

---

## File map

- Create `cards/colorless/混乱触须.txt`: authoritative real-card definition.
- Delete `tokens/c_chaos_tentacle.txt`: remove the conflicting token definition.
- Move `tokens/pictures/c_chaos_tentacle.jpg` to `cards/pictures/PH01/混乱触须.artcrop.jpg`: use PH01 real-card image lookup.
- Modify `cards/colorless/脱困古神尤格萨隆.txt`: replace both token-producing effects with `MakeCard` conjure effects.
- Modify `editions/Placeholder_Set.txt`: register PH01 #39.
- Modify `tests/test_chaos_tentacle.py`: enforce the real-card contract and absence of old token assets.
- Modify `tests/test_yogg_saron_unbound.py`: enforce database-backed conjuring.
- Modify `forge-gui/res/languages/cardnames-zh-CN.txt`: add the card translation and update Yogg wording.
- Modify `CARDS.md`: document the new card and corrected Yogg behavior.
- Modify `VERIFICATION.md`: record only verification actually run in this implementation.

### Task 1: Write contract tests for a real card

**Files:**
- Modify: `tests/test_chaos_tentacle.py`
- Modify: `tests/test_yogg_saron_unbound.py`

- [ ] **Step 1: Point the Chaos Tentacle test at the real-card assets**

Replace its path constants with:

```python
ROOT = Path(__file__).resolve().parents[1]
CARD = ROOT / "cards" / "colorless" / "混乱触须.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "混乱触须.artcrop.jpg"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Chaos_Tentacle_original.jpg"
OLD_TOKEN = ROOT / "tokens" / "c_chaos_tentacle.txt"
OLD_TOKEN_ART = ROOT / "tokens" / "pictures" / "c_chaos_tentacle.jpg"
ZH_CN = ROOT.parent / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"
```

Add a helper so a missing new card produces an assertion failure rather than a file-read error during RED:

```python
def read_card(self):
    self.assertTrue(CARD.is_file(), f"missing real-card script: {CARD}")
    return CARD.read_text(encoding="utf-8")
```

- [ ] **Step 2: Replace the token identity test with the real-card contract**

```python
def test_card_is_a_one_mana_ph01_artifact(self):
    text = self.read_card()

    self.assertIn("Name:混乱触须", text)
    self.assertIn("ManaCost:1", text)
    self.assertIn("Types:Artifact", text)
    self.assertIn("39 C 混乱触须 @Custom", EDITION.read_text(encoding="utf-8"))

def test_real_card_uses_the_requested_tap_and_sacrifice_cost(self):
    text = self.read_card()

    self.assertIn(
        "A:AB$ CardDiscover | Cost$ T Sac<1/CARDNAME> | Defined$ You | Source$ CardDatabase | ValidCards$ Sorcery.cmcEQY | OptionCount$ 3 | Destination$ Exile | RememberChosen$ True | SubAbility$ CastDiscoveredSpell",
        text,
    )

def test_real_card_assets_replace_the_old_token_assets(self):
    self.assertTrue(ART_BACKUP.is_file())
    self.assertTrue(ART.is_file())
    self.assertFalse(OLD_TOKEN.exists())
    self.assertFalse(OLD_TOKEN_ART.exists())

def test_card_has_simplified_chinese_display_text(self):
    expected = (
        "混乱触须|混乱触须|神器|"
        "{T}，牺牲混乱触须：发现一张法术力值为X加1的法术牌，然后不支付其法术力费用且以随机目标施放之，"
        "X为你坟墓场中混乱触须牌的数量。"
    )
    self.assertIn(expected, ZH_CN.read_text(encoding="utf-8").splitlines())
```

Keep the existing discovery, free-cast, random-target, and graveyard-count assertions, but read the script through `self.read_card()` instead of `TOKEN`.

- [ ] **Step 3: Update the Yogg test paths and expected DSL**

Set:

```python
CHAOS_TENTACLE = ROOT / "cards" / "colorless" / "混乱触须.txt"
```

Replace the token-production test with:

```python
def test_each_of_yoggs_exhaust_abilities_conjures_a_chaos_tentacle_card(self):
    text = CARD.read_text(encoding="utf-8")

    self.assertIn(
        "T:Mode$ AbilityCast | ValidCard$ Card.Self | ValidActivatingPlayer$ You | ValidSA$ Activated.Exhaust | TriggerZones$ Battlefield | Execute$ ConjureChaosTentacle",
        text,
    )
    self.assertIn(
        "SVar:ConjureChaosTentacle:DB$ MakeCard | Conjure$ True | Name$ 混乱触须 | Zone$ Battlefield",
        text,
    )
    self.assertNotIn("TokenScript$ c_chaos_tentacle", text)

    tentacle = CHAOS_TENTACLE.read_text(encoding="utf-8")
    self.assertIn("Name:混乱触须", tentacle)
    self.assertIn("Types:Artifact", tentacle)

def test_third_exhaust_conjures_six_chaos_tentacle_cards(self):
    text = CARD.read_text(encoding="utf-8")

    self.assertIn(
        "A:AB$ MakeCard | Cost$ T | Conjure$ True | Name$ 混乱触须 | Amount$ 6 | Zone$ Battlefield | Exhaust$ True",
        text,
    )
    self.assertNotIn("A:AB$ Token", text)
```

Update the expected Chinese Yogg text to contain:

```python
"当你启动脱困古神尤格萨隆的竭绝异能时，化生一张混乱触须牌到战场。\n"
"竭绝—{T}：化生六张混乱触须牌到战场。"
```

- [ ] **Step 4: Run the focused tests and verify RED**

Run:

```powershell
python -m unittest tests.test_chaos_tentacle tests.test_yogg_saron_unbound
```

Expected: assertion failures because `cards/colorless/混乱触须.txt`, PH01 #39, the PH01 image, and the `MakeCard` Yogg effects do not exist yet. There must be no import or file-read errors. The pre-change baseline is 11 passing tests, so these failures must be attributable only to the new contract.

### Task 2: Migrate the card and update Yogg

**Files:**
- Create: `cards/colorless/混乱触须.txt`
- Delete: `tokens/c_chaos_tentacle.txt`
- Move: `tokens/pictures/c_chaos_tentacle.jpg` to `cards/pictures/PH01/混乱触须.artcrop.jpg`
- Modify: `cards/colorless/脱困古神尤格萨隆.txt`
- Modify: `editions/Placeholder_Set.txt`
- Modify: `forge-gui/res/languages/cardnames-zh-CN.txt`

- [ ] **Step 1: Create the real-card script and remove the token script**

Create `cards/colorless/混乱触须.txt` with:

```text
Name:混乱触须
ManaCost:1
Colors:colorless
Types:Artifact
A:AB$ CardDiscover | Cost$ T Sac<1/CARDNAME> | Defined$ You | Source$ CardDatabase | ValidCards$ Sorcery.cmcEQY | OptionCount$ 3 | Destination$ Exile | RememberChosen$ True | SubAbility$ CastDiscoveredSpell | SpellDescription$ Discover a sorcery card with mana value X plus 1, then cast it at random targets without paying its mana cost, where X is the number of Chaos Tentacle cards in your graveyard.
SVar:CastDiscoveredSpell:DB$ Play | Defined$ Remembered | ValidSA$ Spell | ValidZone$ Exile | ZoneRegardless$ True | Controller$ You | WithoutManaCost$ True | Optional$ False | RememberPlayed$ True | SubAbility$ RandomizeTargets
SVar:RandomizeTargets:DB$ ChangeTargets | Defined$ Remembered | RandomTarget$ True | SubAbility$ Cleanup
SVar:Cleanup:DB$ Cleanup | ClearRemembered$ True
SVar:X:Count$ValidGraveyard Card.named混乱触须+YouOwn
SVar:Y:SVar$X/Plus.1
Oracle:{T}, Sacrifice Chaos Tentacle: Discover a sorcery card with mana value X plus 1, then cast it at random targets without paying its mana cost, where X is the number of Chaos Tentacle cards in your graveyard.
```

Delete `tokens/c_chaos_tentacle.txt` after the real-card script exists.

- [ ] **Step 2: Move the artwork into PH01 card lookup**

Run from `D:\Forge\forge-latest\custom`:

```powershell
Move-Item -LiteralPath 'tokens\pictures\c_chaos_tentacle.jpg' -Destination 'cards\pictures\PH01\混乱触须.artcrop.jpg'
```

Expected: the PH01 image exists and the old token image does not.

- [ ] **Step 3: Register the card in PH01**

Append under `[cards]` in `editions/Placeholder_Set.txt`:

```text
39 C 混乱触须 @Custom
```

- [ ] **Step 4: Replace Yogg's token DSL with real-card conjuring**

Use these exact definitions in `cards/colorless/脱困古神尤格萨隆.txt`:

```text
T:Mode$ AbilityCast | ValidCard$ Card.Self | ValidActivatingPlayer$ You | ValidSA$ Activated.Exhaust | TriggerZones$ Battlefield | Execute$ ConjureChaosTentacle | TriggerDescription$ Whenever you activate an exhaust ability of CARDNAME, conjure a Chaos Tentacle card onto the battlefield.
SVar:ConjureChaosTentacle:DB$ MakeCard | Conjure$ True | Name$ 混乱触须 | Zone$ Battlefield
A:AB$ MakeCard | Cost$ T | Conjure$ True | Name$ 混乱触须 | Amount$ 6 | Zone$ Battlefield | Exhaust$ True | SpellDescription$ Conjure six Chaos Tentacle cards onto the battlefield. (Activate each exhaust ability only once.)
```

Replace the corresponding two Oracle sentences with:

```text
Whenever you activate an exhaust ability of CARDNAME, conjure a Chaos Tentacle card onto the battlefield.
Exhaust — {T}: Conjure six Chaos Tentacle cards onto the battlefield.
```

- [ ] **Step 5: Add Chinese card text and update Yogg's Chinese wording**

Add one line to `forge-gui/res/languages/cardnames-zh-CN.txt`:

```text
混乱触须|混乱触须|神器|{T}，牺牲混乱触须：发现一张法术力值为X加1的法术牌，然后不支付其法术力费用且以随机目标施放之，X为你坟墓场中混乱触须牌的数量。
```

In Yogg's existing line, replace the two token sentences with:

```text
当你启动脱困古神尤格萨隆的竭绝异能时，化生一张混乱触须牌到战场。
竭绝—{T}：化生六张混乱触须牌到战场。
```

- [ ] **Step 6: Run focused tests and verify GREEN**

Run:

```powershell
python -m unittest tests.test_chaos_tentacle tests.test_yogg_saron_unbound
```

Expected: all focused tests pass.

### Task 3: Validate scripts and update authoritative documentation

**Files:**
- Modify: `CARDS.md`
- Modify: `VERIFICATION.md`

- [ ] **Step 1: Lint both affected cards**

Run:

```powershell
python tools\lint_card.py 'cards\colorless\混乱触须.txt'
python tools\lint_card.py 'cards\colorless\脱困古神尤格萨隆.txt'
```

Expected: no lint errors for either script.

- [ ] **Step 2: Run the complete DIY test suite**

Run:

```powershell
python -m unittest discover -s tests -p 'test_*.py'
```

Expected: all tests pass. If an unrelated pre-existing failure appears, record its exact test name and prove it is unchanged rather than editing unrelated files.

- [ ] **Step 3: Update the card catalog**

Change Yogg's `CARDS.md` row to say it conjures real 混乱触须 cards, and append:

```markdown
| 混乱触须 | `{1}` 无色神器 | `cards/colorless/混乱触须.txt` | 39 | `{T}`并牺牲：发现一张法术力值为坟墓场中混乱触须牌数量加 1 的法术牌，免费施放并随机化其目标；牺牲的本体会进入坟墓场并计入数量。 |
```

- [ ] **Step 4: Record current verification evidence**

Prepend a dated entry to `VERIFICATION.md` stating the exact focused-test count, lint results, full-suite count, deployment outcome, and hashes observed during this run. Do not reuse historical counts or claim client gameplay verification.

### Task 4: Deploy, remove stale token artifacts, and preserve the change

**Files:**
- Deploy from: `custom/cards`, `custom/editions`, and card images
- Copy language resource from: `forge-gui/res/languages/cardnames-zh-CN.txt`
- Copy language resource to: `D:/Forge/forge-diy-runtime/app/res/languages/cardnames-zh-CN.txt`
- Remove stale deployed files: `%APPDATA%\Forge\custom\tokens\c_chaos_tentacle.txt` and `%LOCALAPPDATA%\Forge\Cache\pics\tokens\c_chaos_tentacle.jpg`

- [ ] **Step 1: Install current custom content**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File tools\install_to_forge.ps1
```

Expected: the new card, PH01 edition entry, and PH01 image are copied successfully.

- [ ] **Step 2: Synchronize the Chinese resource to the desktop runtime**

The custom installer does not copy `forge-gui` language resources. Run:

```powershell
$languageSource = 'D:\Forge\forge-latest\forge-gui\res\languages\cardnames-zh-CN.txt'
$languageTarget = 'D:\Forge\forge-diy-runtime\app\res\languages\cardnames-zh-CN.txt'
Copy-Item -LiteralPath $languageSource -Destination $languageTarget -Force
```

Expected: source and runtime language files have identical SHA-256 hashes.

- [ ] **Step 3: Remove the two stale deployed token artifacts explicitly**

The installer only copies current sources and cannot discover a token deleted from the workspace. Resolve and verify these exact paths, then remove them if present:

```powershell
$oldToken = Join-Path $env:APPDATA 'Forge\custom\tokens\c_chaos_tentacle.txt'
$oldTokenArt = Join-Path $env:LOCALAPPDATA 'Forge\Cache\pics\tokens\c_chaos_tentacle.jpg'
if (Test-Path -LiteralPath $oldToken) { Remove-Item -LiteralPath $oldToken -Force }
if (Test-Path -LiteralPath $oldTokenArt) { Remove-Item -LiteralPath $oldTokenArt -Force }
```

Expected: both old paths return `False` from `Test-Path`.

- [ ] **Step 4: Compare source and deployed hashes**

Run:

```powershell
$pairs = @(
    @('cards\colorless\混乱触须.txt', (Join-Path $env:APPDATA 'Forge\custom\cards\colorless\混乱触须.txt')),
    @('cards\colorless\脱困古神尤格萨隆.txt', (Join-Path $env:APPDATA 'Forge\custom\cards\colorless\脱困古神尤格萨隆.txt')),
    @('editions\Placeholder_Set.txt', (Join-Path $env:APPDATA 'Forge\custom\editions\Placeholder_Set.txt')),
    @('cards\pictures\PH01\混乱触须.artcrop.jpg', (Join-Path $env:LOCALAPPDATA 'Forge\Cache\pics\cards\PH01\混乱触须.artcrop.jpg')),
    @('D:\Forge\forge-latest\forge-gui\res\languages\cardnames-zh-CN.txt', 'D:\Forge\forge-diy-runtime\app\res\languages\cardnames-zh-CN.txt')
)
$pairs | ForEach-Object {
    $source = (Get-FileHash -Algorithm SHA256 -LiteralPath $_[0]).Hash
    $deployed = (Get-FileHash -Algorithm SHA256 -LiteralPath $_[1]).Hash
    [pscustomobject]@{ Source = $_[0]; Match = ($source -eq $deployed); SHA256 = $source }
}
```

Expected: every `Match` value is `True`.

- [ ] **Step 5: Review only the intended diff**

Run from `D:\Forge\forge-latest`:

```powershell
git diff -- custom/cards/colorless/混乱触须.txt custom/tokens/c_chaos_tentacle.txt custom/tokens/pictures/c_chaos_tentacle.jpg custom/cards/pictures/PH01/混乱触须.artcrop.jpg custom/cards/colorless/脱困古神尤格萨隆.txt custom/editions/Placeholder_Set.txt custom/tests/test_chaos_tentacle.py custom/tests/test_yogg_saron_unbound.py custom/CARDS.md custom/VERIFICATION.md forge-gui/res/languages/cardnames-zh-CN.txt
git status --short
```

Expected: the feature files contain only this migration; unrelated existing Soulfire and temporary-file changes remain unmodified and unstaged.

- [ ] **Step 6: Commit the completed card migration locally**

Stage only the files listed in this plan, including the image rename, then run:

```powershell
git commit -m "Convert Chaos Tentacle into a real card"
```

Expected: one local commit for the verified DSL/data change. Do not push because this plan contains no Java engine change and the user did not request publication.

- [ ] **Step 7: Report the manual verification boundary**

State that Forge must be restarted or custom resources reloaded. Do not claim that the client has displayed PH01 #39, that Yogg conjured real cards, or that sacrificed cards accumulated in the graveyard until those actions are observed in a game.
