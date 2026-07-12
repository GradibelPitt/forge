# Forge DIY Documentation Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the scattered agent-facing Markdown files with six focused daily-use documents, one consolidated design document, and a searchable lossless historical archive.

**Architecture:** Build new authoritative documents from verified repository and runtime paths first, validate their coverage and links, and only then move superseded files into the archive. Preserve every historical design, plan, and legacy document under its original filename and provide an archive index mapping historical topics to current documents.

**Tech Stack:** Markdown, PowerShell, ripgrep, Forge Java/Maven project layout, Forge DIY Python tooling.

---

## File map

- Rewrite: `AGENTS.md` — short agent entry point, non-negotiable rules, task-based reading map.
- Create: `PROJECT.md` — project purpose, authority hierarchy, current state, workflow, open work.
- Create: `ARCHITECTURE.md` — verified source, asset, localization, build, runtime, and deployment paths.
- Create: `KEYWORDS.md` — custom keyword/API semantics, DSL, Java entry points, tests, implementation status.
- Create: `CARDS.md` — stable inventory of DIY and test cards.
- Create: `VERIFICATION.md` — test, lint, build, deployment, and client-verification status.
- Create: `docs/DESIGN.md` — consolidated current design decisions.
- Create: `docs/archive/README.md` — archive index and authority warning.
- Move: `docs/superpowers/specs/*.md` to `docs/archive/designs/` without renaming.
- Move: `docs/superpowers/plans/*.md` to `docs/archive/plans/` without renaming.
- Move: `memory.md`, `instruction.md`, `test_cards.md`, `update.md`, `DIY-README.md` to `docs/archive/legacy/` without renaming.

### Task 1: Capture the verified project map

**Files:**
- Read: `AGENTS.md`
- Read: `memory.md`
- Read: `instruction.md`
- Read: `DIY-README.md`
- Read: `test_cards.md`
- Read: `update.md`
- Read: `docs/superpowers/specs/*.md`
- Read: `docs/superpowers/plans/*.md`
- Inspect: `D:/Forge/forge-master/forge-master/`

- [ ] **Step 1: Inventory all agent-facing Markdown before moving anything**

Run:

```powershell
rg --files -g '*.md' -g '!reference/**' | Sort-Object
```

Expected: every root document, current spec, and current plan is listed and can later be accounted for in either the active set or archive.

- [ ] **Step 2: Verify DIY source and test paths**

Run:

```powershell
Get-ChildItem cards,editions,tokens,tests,tools -Force -ErrorAction SilentlyContinue
```

Expected: existing paths are recorded exactly; absent optional directories are described as optional rather than asserted to exist.

- [ ] **Step 3: Verify Java implementation and test paths for custom mechanics**

Run:

```powershell
rg -n 'BOARDING|Boarding|SUPERREACH|Superreach|IGNORE_DECK_LIMITS|IgnoreDeckLimits' D:\Forge\forge-master\forge-master -g '*.java'
```

Expected: keyword registration, implementation, integration, and focused test paths are identified from current source.

- [ ] **Step 4: Verify image, localization, build, and runtime deployment paths**

Run:

```powershell
rg --files D:\Forge\forge-master\forge-master | rg 'cardnames-zh-CN\.txt$|forge-gui-desktop.*\.jar$|pom\.xml$'
Get-ChildItem cards\pictures -Recurse -File -ErrorAction SilentlyContinue
Get-ChildItem "$env:APPDATA\Forge" -Recurse -Depth 4 -ErrorAction SilentlyContinue | Where-Object { $_.FullName -match 'custom|cache|pics|cards' } | Select-Object -First 200 FullName
```

Expected: documentation distinguishes repository resources, DIY source images, generated build artifacts, and live user-data destinations. If a live path is absent, label it as generated or installation-dependent.

### Task 2: Create the authoritative daily-use documents

**Files:**
- Create: `PROJECT.md`
- Create: `ARCHITECTURE.md`
- Create: `KEYWORDS.md`
- Create: `CARDS.md`
- Create: `VERIFICATION.md`

- [ ] **Step 1: Create `PROJECT.md` from current source material**

Use these exact top-level sections:

```markdown
# Forge DIY Project
## Document map
## Purpose and source of truth
## Authority hierarchy
## Current implemented state
## Approved but not implemented
## Development workflow
## Constraints and open work
```

Record `CardDiscover` and “空中悍匪” under “Approved but not implemented” until their code and tests exist.

- [ ] **Step 2: Create `ARCHITECTURE.md` using only paths verified in Task 1**

Use these exact top-level sections:

```markdown
# Forge DIY Architecture
## Repository layout
## DIY card and edition sources
## Forge Java engine
## Card images
## Chinese localization
## Tests and validation tools
## Build and runtime artifacts
## Source-to-client data flow
```

Include a compact table with columns `Concern`, `Authoritative source`, `Generated or deployed destination`, and `Notes`.

- [ ] **Step 3: Create `KEYWORDS.md` with a consistent mechanism template**

Use one section each for `Superreach`, `Ignore Superreach`, `IgnoreDeckLimits`, `Boarding`, and `CardDiscover`. Each section must contain `Status`, `Player-facing behavior`, `DSL`, `Java implementation`, `Tests`, and `Edge cases`. Mark `CardDiscover` as approved and unimplemented.

- [ ] **Step 4: Create `CARDS.md` from card scripts and edition data**

Run before writing:

```powershell
Get-Content cards\*\*.txt -Encoding UTF8
Get-Content editions\Placeholder_Set.txt -Encoding UTF8
```

Use sections `Test cards`, `Gameplay cards`, and `Approved cards awaiting implementation`. Record exact script paths and edition numbers; do not claim “空中悍匪” has a script or collector number yet.

- [ ] **Step 5: Create `VERIFICATION.md` with explicit state levels**

Use these status values consistently: `Design approved`, `Implemented`, `Automated tests passed`, `Deployed`, and `Client verified`. Preserve historical commands/results from `update.md`, memory, plans, and test reports, while labeling anything not re-run during this migration as historical evidence rather than fresh verification.

### Task 3: Consolidate designs and rewrite the agent entry point

**Files:**
- Create: `docs/DESIGN.md`
- Rewrite: `AGENTS.md`

- [ ] **Step 1: Create `docs/DESIGN.md` from the approved conclusions in all design specs**

Use these exact top-level sections:

```markdown
# Forge DIY Current Design Decisions
## Document governance
## Card scripting boundaries
## Deck construction overrides
## New-game and legend-rule behavior
## Superreach
## Boarding
## Chinese localization
## CardDiscover
## Card-specific decisions
```

Resolve conflicts in favor of current source and current approved specifications. Link to archived originals for detailed history instead of copying implementation task lists.

- [ ] **Step 2: Rewrite `AGENTS.md` as the minimal mandatory entry point**

Use these exact top-level sections:

```markdown
# Forge DIY Agent Guide
## Required reading by task
## Non-negotiable rules
## Change workflow
## Validation before handoff
## Historical research
```

The reading map must route engine work to `ARCHITECTURE.md`, `KEYWORDS.md`, and `docs/DESIGN.md`; card work to `CARDS.md`; and status claims to `VERIFICATION.md`.

- [ ] **Step 3: Check current documents for contradictory implementation claims**

Run:

```powershell
rg -n 'CardDiscover|空中悍匪|已实现|待实现|客户端' AGENTS.md PROJECT.md ARCHITECTURE.md KEYWORDS.md CARDS.md VERIFICATION.md docs\DESIGN.md
```

Expected: discovery is consistently described as approved but unimplemented; existing mechanics retain their actual verified status.

### Task 4: Archive without data loss

**Files:**
- Create: `docs/archive/designs/`
- Create: `docs/archive/plans/`
- Create: `docs/archive/legacy/`
- Create: `docs/archive/README.md`
- Move: all superseded documents listed in the file map.

- [ ] **Step 1: Record source file hashes before moving**

Run:

```powershell
$sources = @(
  Get-ChildItem docs\superpowers\specs\*.md -File
  Get-ChildItem docs\superpowers\plans\*.md -File
  Get-Item memory.md,instruction.md,test_cards.md,update.md,DIY-README.md
)
$sources | Get-FileHash -Algorithm SHA256 | Sort-Object Path | Format-Table -AutoSize
```

Save the displayed mapping temporarily in the terminal output for comparison after the move; do not create another permanent status file.

- [ ] **Step 2: Create archive directories and move files with native PowerShell**

Run from `D:\Forge\forge-diy` only after checking `$PWD.Path` equals that path:

```powershell
if ($PWD.Path -ne 'D:\Forge\forge-diy') { throw 'Wrong working directory' }
New-Item -ItemType Directory -Force docs\archive\designs,docs\archive\plans,docs\archive\legacy | Out-Null
Move-Item -LiteralPath (Get-ChildItem docs\superpowers\specs\*.md -File).FullName -Destination docs\archive\designs
Move-Item -LiteralPath (Get-ChildItem docs\superpowers\plans\*.md -File).FullName -Destination docs\archive\plans
Move-Item -LiteralPath memory.md,instruction.md,test_cards.md,update.md,DIY-README.md -Destination docs\archive\legacy
```

Expected: no source document is deleted; every filename remains unchanged under its archive category.

- [ ] **Step 3: Recompute hashes and compare by filename**

Run:

```powershell
Get-ChildItem docs\archive\designs\*.md,docs\archive\plans\*.md,docs\archive\legacy\*.md | Get-FileHash -Algorithm SHA256 | Sort-Object Path | Format-Table -AutoSize
```

Expected: each archived file hash matches its pre-move hash.

- [ ] **Step 4: Create `docs/archive/README.md`**

Index every archived file with date, topic, historical/current status, and a link to the authoritative current document. State prominently that archived documents may be stale and are not current instructions.

### Task 5: Validate structure, links, coverage, and repository state

**Files:**
- Validate: all active and archived Markdown files.

- [ ] **Step 1: Verify the active root document set**

Run:

```powershell
Get-ChildItem *.md -File | Select-Object -ExpandProperty Name | Sort-Object
```

Expected exactly:

```text
AGENTS.md
ARCHITECTURE.md
CARDS.md
KEYWORDS.md
PROJECT.md
VERIFICATION.md
```

- [ ] **Step 2: Verify every former document exists in the archive**

Run:

```powershell
Get-ChildItem docs\archive -Recurse -File -Filter *.md | Select-Object -ExpandProperty FullName | Sort-Object
```

Expected: every original spec, plan, and legacy document plus `docs/archive/README.md` is present.

- [ ] **Step 3: Validate local Markdown links**

Run a PowerShell link checker that parses Markdown link targets, ignores `http`, `https`, and anchors, resolves relative targets against each document directory, and throws for missing paths:

```powershell
$errors = @()
Get-ChildItem -Recurse -File -Filter *.md | ForEach-Object {
  $file = $_
  $text = Get-Content -Raw -Encoding UTF8 $file.FullName
  foreach ($m in [regex]::Matches($text, '\[[^\]]+\]\(([^)]+)\)')) {
    $target = $m.Groups[1].Value.Split('#')[0]
    if (-not $target -or $target -match '^(https?:|mailto:)') { continue }
    $resolved = [IO.Path]::GetFullPath((Join-Path $file.DirectoryName $target))
    if (-not (Test-Path -LiteralPath $resolved)) { $errors += "$($file.FullName) -> $target" }
  }
}
if ($errors.Count) { $errors; throw 'Broken Markdown links found' }
```

Expected: command exits successfully with no broken-link list.

- [ ] **Step 4: Check for placeholders and duplicate ownership**

Run:

```powershell
rg -n 'TBD|TODO|待补充|稍后填写' AGENTS.md PROJECT.md ARCHITECTURE.md KEYWORDS.md CARDS.md VERIFICATION.md docs\DESIGN.md
rg -n '^## (Card images|Chinese localization|Superreach|Boarding|Current implemented state|Client verification)' AGENTS.md PROJECT.md ARCHITECTURE.md KEYWORDS.md CARDS.md VERIFICATION.md docs\DESIGN.md
```

Expected: no placeholders; detailed ownership sections appear only in their designated authoritative document, with other documents linking to them.

- [ ] **Step 5: Inspect final diff/status when version control is available**

Run:

```powershell
git status --short
git diff --stat
```

Expected in this workspace: if it remains outside a Git repository, record that commits are unavailable. Do not initialize a repository merely to satisfy the plan.

## Plan self-review

- Every document in the approved target structure has a creation or rewrite task.
- Every existing design, plan, and superseded root document has an explicit archive destination.
- Hash comparison protects archive integrity.
- Current source and filesystem checks precede architecture claims.
- `CardDiscover` and “空中悍匪” cannot accidentally be presented as implemented.
- Final checks cover root structure, archive coverage, Markdown links, placeholders, and status consistency.
