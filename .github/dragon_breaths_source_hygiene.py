from __future__ import annotations

from pathlib import Path
import sys


if len(sys.argv) != 2:
    raise SystemExit("usage: dragon_breaths_source_hygiene.py <forge-root>")

root = Path(sys.argv[1]).resolve()

# Existing source contract failure: normalize the customary Magic wording order.
card_path = root / "custom" / "cards" / "blue" / "法术反制.txt"
card_text = card_path.read_text(encoding="utf-8")
if "法术或瞬间" not in card_text:
    raise RuntimeError("Expected pre-existing 法术反制 wording was not found.")
card_path.write_text(
    card_text.replace("法术或瞬间", "瞬间或法术"),
    encoding="utf-8",
    newline="\n",
)

# Existing localization contract failures: fix only the two named rows in place.
loc_path = root / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"
lines = loc_path.read_text(encoding="utf-8").splitlines()
seen = {"法术反制": False, "开进码头": False}
for index, line in enumerate(lines):
    if line.startswith("法术反制|"):
        seen["法术反制"] = True
        lines[index] = line.replace("法术或瞬间", "瞬间或法术")
    elif line.startswith("开进码头|"):
        seen["开进码头"] = True
        lines[index] = line.replace("“毁灭战舰”", "「毁灭战舰」")

missing = [name for name, found in seen.items() if not found]
if missing:
    raise RuntimeError(f"Expected localization rows were not found: {missing}")

loc_path.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
print("DRAGON_BREATHS_SOURCE_HYGIENE=OK")
