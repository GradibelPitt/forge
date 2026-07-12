# Forge Custom Development Guide

This checkout contains both the upstream Forge engine and the local custom-card project.

- For custom cards, mechanics, images, localization, deployment, or validation, read [custom/AGENTS.md](custom/AGENTS.md) first.
- `custom/` is the authoritative source for DIY cards, editions, images, tools, tests, and project documentation.
- Engine changes belong in the existing Forge modules and must retain targeted tests.
- Do not edit `%APPDATA%\Forge\custom` or built JARs as source files; they are deployment artifacts.
