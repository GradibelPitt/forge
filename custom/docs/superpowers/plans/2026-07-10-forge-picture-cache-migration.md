# Forge Picture Cache Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move Forge's complete picture cache to `D:\Forge\Cache\pics` while preserving `%LOCALAPPDATA%\Forge\Cache\pics` as a transparent compatible entry point.

**Architecture:** Copy and verify the current directory before deleting any source data, then replace the source directory with an NTFS junction. Validate both filesystem views and perform a Forge startup/log check.

**Tech Stack:** Windows PowerShell, Robocopy, NTFS junctions, Forge desktop Java runtime.

---

### Task 1: Preflight and process safety

- [ ] Confirm the source is a normal directory and the destination does not already contain conflicting data.
- [ ] Record source file count, total bytes, and a SHA-256 hash sample.
- [ ] Detect and stop only processes whose command line identifies the Forge desktop JAR.

### Task 2: Copy and verify data

- [ ] Create `D:\Forge\Cache\pics` and copy with `robocopy /E /COPY:DAT /DCOPY:DAT /R:2 /W:1`.
- [ ] Accept Robocopy exit codes 0 through 7 and treat 8 or higher as failure.
- [ ] Compare source and destination file counts and total bytes; stop without modifying the source if they differ.
- [ ] Verify the recorded hash sample at the destination.

### Task 3: Switch to the junction

- [ ] Rename the source to a temporary sibling backup.
- [ ] Create `%LOCALAPPDATA%\Forge\Cache\pics` as a Junction targeting `D:\Forge\Cache\pics`.
- [ ] Verify `LinkType`, target path, file count, total bytes, and sample hashes through the junction.
- [ ] Remove the temporary source backup only after all junction verification passes; restore it if any check fails.

### Task 4: Runtime verification

- [ ] Locate the current Forge desktop aggregate JAR.
- [ ] Start it as a hidden process, wait for initialization, and inspect `%APPDATA%\Forge\forge.log` for new cache/path/image errors.
- [ ] Stop only the process started by this verification.
- [ ] Report the final junction path, physical target, migrated size, file count, and verification result.

## Repository constraint

The custom source now lives under `D:\Forge\forge-latest\custom` in the main Git working tree. The cache migration itself still changes machine state and must be verified separately from source changes.
