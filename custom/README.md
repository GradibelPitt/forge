# Forge DIY Custom Project

This directory contains the local custom-card project that was formerly stored in a separate workspace.

- Start with [AGENTS.md](AGENTS.md) for required task-specific reading and workflow rules.
- See [PROJECT.md](PROJECT.md) for current scope and status.
- See [ARCHITECTURE.md](ARCHITECTURE.md) for source, build, deployment, and runtime paths.
- See [VERIFICATION.md](VERIFICATION.md) for tested, built, deployed, and client-verified states.
- Files under `docs/archive/legacy/`, including the old `memory.md`, are historical only; the current workflow lives in the authoritative documents above.
- After every completed card change, immediately commit and push both the source and runtime repositories after proportionate validation. For Java changes, prefer the affected module overlay as a precise injection patch; rebuild the desktop aggregate only when a packaging boundary requires it.

Run Python validation and deployment commands from this directory. Run Maven builds from the repository root, `D:\Forge\forge-latest`.
