# Forge DIY Project Instructions

- Treat `D:\Forge\forge-diy` as the source of truth for custom Forge cards.
- Read `AGENTS.md`, `DIY-README.md`, and `test_cards.md` before changing card scripts.
- Keep card implementation in `cards/` and edition entries in `editions/`; do not edit Forge Java unless the user explicitly authorizes it.
- Reuse verified Forge card scripts and engine behavior. Run `python tools/lint_card.py <card-file>` before handoff.

