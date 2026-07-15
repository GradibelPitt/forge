# Titans 1.31 Batch Implementation Plan

> **For Codex:** Execute continuously. The user explicitly approved immediate execution and requested parallel subagents.

**Goal:** Implement the ten custom Titan cards shown on slides 3–12 of `假卡速递-泰坦1.31.pptx`, skip Yogg-Saron on slide 13, and install every required token with its complete replacement image. Addendum: after the user expanded scope, also implement V-07-TR-0N Prime from slide 14.

**Architecture:** Each Titan is a normal Forge card script using native `Exhaust$ True` abilities and existing DSL patterns. Full main-card images are stored as `PH01/<Chinese name>.full.jpg`; token scripts and complete token-card images use matching basenames under `custom/tokens` and `custom/tokens/pictures`. Shared edition, translation, documentation, and deployment files are integrated only by the primary agent.

**Tech Stack:** Forge card DSL, Python/pytest validation, `lint_card_scripts.py`, PowerShell install workflow.

---

### Task 1: Establish the batch contract

**Files:**
- Create: `custom/tests/test_titans_batch.py`
- Create: `custom/docs/superpowers/plans/2026-07-13-titans-batch.md`

1. Add tests enumerating exactly eleven Titans and seven token scripts after the slide 14 addendum.
2. Assert the three exhaust abilities, key rules-text mechanics, PH01 records 41–51, Chinese translations, full-image files, and token image/script pairs.
3. Run the test and confirm it fails because implementation files do not yet exist.

### Task 2: Implement Titans and token scripts in parallel

**Files:**
- Create eleven scripts in `custom/cards/multicolor/` after the slide 14 addendum.
- Create seven scripts in `custom/tokens/`.

1. Implement Aman'Thul, Sargeras, The Primus, and their required Burning Legion/Zombie tokens.
2. Implement Eonar, Amitus, Norgannon, and Eonar's Treefolk token.
3. Implement Golganneth, Khaz'goroth, Argus, Aggramar, and their Elemental/Dwarf/Taeshalach tokens.
4. Each implementer runs the card-script linter on only its files and self-reviews against the supplied English Oracle.

### Task 3: Install complete replacement images and shared metadata

**Files:**
- Create eleven images under `custom/cards/pictures/PH01/*.full.jpg` after the slide 14 addendum.
- Create seven images under `custom/tokens/pictures/*.jpg`.
- Modify `custom/editions/Placeholder_Set.txt`.
- Modify `forge-gui/res/languages/cardnames-zh-CN.txt`.
- Modify `custom/CARDS.md`.

1. Copy the embedded PPT JPEGs byte-for-byte; do not crop, render a frame, or alter dimensions.
2. Add PH01 collector records 41–51 without `@Custom`.
3. Add normalized Chinese card names and Oracle translations derived from the English images/text boxes.
4. Add the cards and token assets to the custom-card inventory documentation.

### Task 4: Verify scripts and behavior contracts

1. Run `pytest -q tests/test_titans_batch.py`.
2. Run the linter on all eleven card scripts and seven token scripts.
3. Run `pytest -q` for the complete custom suite.
4. Fix every failure and rerun until green.

### Task 5: Deploy and prove exact installation

**Files:**
- Modify `custom/VERIFICATION.md`.

1. Run `powershell -ExecutionPolicy Bypass -File tools/install_to_forge.ps1`.
2. Compare SHA-256 hashes for each source/deployed card script, main-card full image, token script, and token image.
3. Record commands and results in `VERIFICATION.md`.
4. Confirm no Yogg-Saron file or registration was added by this batch.
