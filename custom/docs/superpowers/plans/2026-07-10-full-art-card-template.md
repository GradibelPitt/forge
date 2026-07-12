# Full-Art Card Template Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a reusable local generator for Cardsmith-style full-art Forge card images and use Hogger as the first verified output.

**Architecture:** A focused Pillow-based CLI parses stable Forge card metadata, composes deterministic overlay geometry over source art, and writes a full-card JPEG. Contract tests validate parsing and output structure; the existing installer deploys the result.

**Tech Stack:** Python 3, Pillow, unittest, Forge card scripts.

---

### Task 1: Parser and rendering contract

- [ ] Add failing tests for Forge field parsing and a 1500×2092 JPEG output.
- [ ] Verify the tests fail because the generator module is absent.

### Task 2: Implement reusable generator

- [ ] Add `tools/generate_full_art_card.py` with Forge field parsing, cover-crop background placement, gold overlay geometry, Chinese text wrapping, mana text, type line, rules text, footer, and P/T.
- [ ] Run targeted tests and confirm they pass.

### Task 3: Generate and deploy Hogger art 2

- [ ] Generate `cards/pictures/PH01/破链灾星霍格2.full.jpg` directly from the preserved original art and Hogger script.
- [ ] Run the alternate-art and full regression suites.
- [ ] Sync with `tools/install_to_forge.ps1`, compare hashes, restart Forge, and confirm the process remains running.

## Repository constraint

The workspace is not a Git repository, so no commit step is available.
