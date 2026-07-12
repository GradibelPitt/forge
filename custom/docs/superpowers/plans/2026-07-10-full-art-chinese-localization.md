# Full-Art Chinese Localization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate Cardsmith-style full-art cards using the desktop Forge zh-CN translations by default.

**Architecture:** Add a small translation reader to the existing deterministic Pillow generator. It overlays matched translation fields before rendering and preserves the original Forge script fields as a fallback.

**Tech Stack:** Python 3, Pillow, unittest, Forge zh-CN resource format.

---

### Task 1: Translation behavior contract

**Files:**
- Modify: `tests/test_generate_full_art_card.py`
- Modify: `tools/generate_full_art_card.py`

- [ ] Add a test that writes a four-column zh-CN fixture and asserts a matching card receives Chinese `Name`, `Types`, and `Oracle`, including `\\n` conversion.
- [ ] Add a test that a missing translation keeps the English fields.
- [ ] Run `python -m unittest tests.test_generate_full_art_card -v` and confirm the new import/function expectation fails before implementation.

### Task 2: Minimal translation integration

- [ ] Implement `load_translations(path)` and `localized_fields(fields, translations)` in `tools/generate_full_art_card.py`.
- [ ] Add `--translations` with the desktop language resource as its default value.
- [ ] Run the targeted test command and confirm all generator tests pass.

### Task 3: Regenerate and deploy Hogger

- [ ] Regenerate `cards/pictures/PH01/破链灾星霍格2.full.jpg` with the default desktop translations.
- [ ] Run the entire DIY test suite and `tools/install_to_forge.ps1`.
- [ ] Verify cache/source hashes, restart Forge, and confirm the process remains active.

## Repository constraint

The workspace is not a Git repository, so no commit step is available.
