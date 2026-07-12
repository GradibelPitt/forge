# SVG Full-Art Frame Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render full-art card title and text frames from the provided transparent SVG assets and document the complete picture-import workflow.

**Architecture:** CairoSVG rasterizes the named SVG assets to RGBA Pillow layers. The existing generator places those layers over art and then draws localized metadata. `图片导入流程.md` is the durable operating guide for all image import variants.

**Tech Stack:** Python 3, Pillow, CairoSVG, unittest, Forge image cache.

---

### Task 1: SVG layer contract

**Files:**
- Modify: `tests/test_generate_full_art_card.py`
- Modify: `tools/generate_full_art_card.py`

- [ ] Add a failing test for `load_svg_layer(path, size)` that asserts the provided name-frame SVG rasterizes to RGBA with nonzero alpha.
- [ ] Run `python -m unittest tests.test_generate_full_art_card -v` and confirm failure before implementation.

### Task 2: Generator SVG integration

- [ ] Install CairoSVG into the active Python environment.
- [ ] Implement `load_svg_layer`, default SVG asset paths, and use the name/text SVG layers in `render_card`.
- [ ] Remove the hand-drawn name and rules frame geometry while preserving text, mana, outer border, and P/T rendering.
- [ ] Run targeted generator tests and inspect the regenerated Hogger output.

### Task 3: Workflow documentation and deployment

**Files:**
- Create: `图片导入流程.md`
- Modify: `cards/pictures/PH01/破链灾星霍格2.full.jpg`

- [ ] Write the complete image import, deployment, naming, alternate-art, Chinese localization, conflict, verification, and rollback procedure.
- [ ] Run all DIY tests, sync to Forge, verify source/cache hashes, restart Forge, and confirm it remains running.

## Repository constraint

The workspace is not a Git repository, so no commit step is available.
