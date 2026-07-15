# Card Image Refresh Performance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop unchanged battlefield card images from being rescaled during full-field refreshes without preventing missing or newly downloaded images from appearing.

**Architecture:** Keep the existing full-field refresh and image-fetch callback behavior. Add a narrow request-signature guard in `CardPanel`, configure each `ResampleOp` for one worker, and cache the shared resized default image without caching it under a card-specific key.

**Tech Stack:** Java 17, Swing/AWT, Guava `LoadingCache`, java-image-scaling `ResampleOp`, TestNG, Maven.

---

### Task 1: Card image refresh guard

**Files:**
- Modify: `forge-gui-desktop/src/main/java/forge/view/arcane/CardPanel.java`
- Create: `forge-gui-desktop/src/main/java/forge/view/arcane/CardImageRefreshPolicy.java`
- Test: `forge-gui-desktop/src/test/java/forge/view/arcane/CardPanelImageRefreshTest.java`

- [ ] Add failing tests proving that an identical resolved image request is skipped, while a changed key, changed size, or unresolved/default image is refreshed.
- [ ] Run the targeted test and confirm it fails because the guard API does not exist.
- [ ] Add the smallest key-and-size guard to `CardPanel`; keep unresolved/default images eligible for refresh and keep `CachedCardImage.onImageFetched()` active.
- [ ] Run the targeted test and confirm it passes.

### Task 2: Single-thread scaling and shared default caching

**Files:**
- Modify: `forge-gui-desktop/src/main/java/forge/ImageCache.java`
- Create: `forge-gui-desktop/src/main/java/forge/ImageScalingPolicy.java`
- Test: `forge-gui-desktop/src/test/java/forge/ImageCachePerformanceTest.java`

- [ ] Add failing tests proving resamplers use one worker and the shared default image is cacheable even though it is a placeholder.
- [ ] Run the targeted test and confirm it fails because the policy helpers do not exist.
- [ ] Configure `ResampleOp` with one worker and cache only the shared `__DEFAULT__` resized entry for the default placeholder.
- [ ] Run the targeted tests and confirm they pass.

### Task 3: Build and runtime verification

**Files:**
- Verify: `forge-gui-desktop/target/forge-gui-desktop-2.0.14-SNAPSHOT-jar-with-dependencies.jar`

- [ ] Run both targeted TestNG tests through the desktop module POM and local Maven settings.
- [ ] Build the desktop fat JAR.
- [ ] Confirm with `javap` that the packaged JAR contains the refresh guard and single-thread resampler factory.
- [ ] Relaunch Forge with the rebuilt JAR, reproduce the multi-Titan battlefield, and capture a fresh 20-second JFR sample.
- [ ] Verify image keys still refresh from placeholder/missing images to real downloaded images and compare short-lived thread creation against the prior 2,923-per-20-second baseline.

### Task 4: Hand-card preview hot path

**Files:**
- Modify: `forge-gui-desktop/src/main/java/forge/ImageScalingPolicy.java`
- Modify: `forge-gui-desktop/src/main/java/forge/gui/CardPicturePanel.java`
- Modify: `forge-gui-desktop/src/main/java/forge/toolbox/imaging/FImagePanel.java`
- Create: `forge-gui-desktop/src/main/java/forge/gui/CardPictureRefreshPolicy.java`
- Test: `forge-gui-desktop/src/test/java/forge/gui/CardPictureRefreshPolicyTest.java`

- [ ] Add failing tests proving panel resampling uses one worker and identical resolved preview requests are skipped.
- [ ] Route `FImagePanel` through the tested single-worker resampler factory.
- [ ] Short-circuit only identical resolved preview requests; preserve refreshes for hidden/alternate/unresolved/cache-cleared images.
- [ ] Rebuild and repeat the same 20-second hand-hover JFR sample.
