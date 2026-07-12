# Upstream Forge AI Merge and Gigantic Spright Fix Implementation Plan

> **For agentic workers:** Execute inline in this session. The checkout has no Git metadata, so verification uses backups, hashes, module builds, and focused tests instead of commits.

**Goal:** Replace only the local `forge-ai` module with upstream `master` commit `ebf900109c882d7027b0651ddcff65a57519237a`, preserve a rollback copy, verify compatibility with the current local engine, then remove Gigantic Spright's crashing nested ETB-counter branch.

**Architecture:** The upstream repository remains isolated at `D:\Forge\tmp\upstream-forge-ai`; only its `forge-ai` tree is synchronized into the local module. No other upstream module is downloaded or copied. The card fix stays in the DIY script layer unless a focused failing test proves the current DSL cannot express it.

**Tech Stack:** Java 17, Maven 3.9.12, Forge card DSL, Python `unittest`, PowerShell.

---

### Task 1: Establish the pre-merge baseline and rollback point

**Files:**
- Read: `D:\Forge\forge-master\forge-master\forge-ai\pom.xml`
- Create: `D:\Forge\tmp\forge-ai-pre-ebf9001\`

- [ ] Run the local `forge-ai` module tests/package command and record whether the existing module builds.
- [ ] Copy the complete current `forge-ai` directory to the rollback directory.
- [ ] Verify the rollback file count and representative hashes match the source module.

### Task 2: Synchronize only upstream `forge-ai`

**Files:**
- Source: `D:\Forge\tmp\upstream-forge-ai\forge-ai\`
- Modify: `D:\Forge\forge-master\forge-master\forge-ai\`

- [ ] Mirror the upstream module while excluding generated `target` files.
- [ ] Verify only the local `forge-ai` tree changed and its source file list matches upstream.
- [ ] Run the focused Maven build. If it fails, classify every failure as an AI-local issue or a missing current-engine API; do not copy another upstream module.
- [ ] Add only minimal AI-local compatibility edits needed for the current local interfaces, with a failing compile/test observed before each edit.

### Task 3: Reproduce Gigantic Spright's unsafe ETB branch in a test

**Files:**
- Modify: `D:\Forge\forge-diy\tests\test_gigantic_spright_placeholder.py`
- Test: `D:\Forge\forge-diy\tests\test_gigantic_spright_placeholder.py`

- [ ] Replace the assertion that requires `DB$ Branch` around `PutCounter | ETB$ True` with a contract rejecting that nesting.
- [ ] Require a direct ETB counter ability whose `CounterNum$` is calculated as one or two from the remembered tapped material.
- [ ] Run the focused Python test and confirm it fails against the current crashing script.

### Task 4: Implement the smallest script-level card fix

**Files:**
- Modify: `D:\Forge\forge-diy\cards\colorless\gigantic_spright.txt`

- [ ] Replace the nested `Branch -> PutCounter ETB$ True` chain with one direct ETB `PutCounter` using a verified native amount expression.
- [ ] Keep the special-material haste behavior linked without nesting the ETB counter operation under `Branch`.
- [ ] Run the focused test and single-card lint until both pass.
- [ ] Run the full DIY suite and report unrelated pre-existing failures separately.

### Task 5: Deploy and verify artifacts

**Files:**
- Modify through installer: `%APPDATA%\Forge\custom\cards\colorless\gigantic_spright.txt`
- Update: `D:\Forge\forge-diy\VERIFICATION.md`

- [ ] Build the desktop JAR only if the AI module integration requires a new aggregate artifact.
- [ ] Stop before replacing a JAR locked by the running Forge process; preserve the built artifact until the client can be restarted.
- [ ] Deploy the card script and compare source/deployed SHA-256 hashes.
- [ ] Record exact test, build, deployment, and pending client-verification status.
