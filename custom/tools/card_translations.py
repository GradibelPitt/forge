"""Read base + optional custom translations for DIY authoring tools."""

from pathlib import Path
import re


def load_translations(base: Path, custom: Path | None = None) -> dict[str, dict[str, str]]:
    result = {}
    for path in (base, custom):
        if path is None or not path.is_file():
            continue
        for line in path.read_text(encoding="utf-8-sig").splitlines():
            if line.startswith("#"):
                continue
            fields = line.split("|")
            # Java String.split drops trailing empty fields. Preserve the same
            # field-by-field fallback when a later record omits its Oracle.
            while fields and fields[-1] == "":
                fields.pop()
            if len(fields) < 2:
                continue
            key = fields[0]
            if key.find("$") > 0:
                name, variant = re.split(r"\s*\$", key, maxsplit=1)
                result.setdefault(name, {})["Name"] = fields[1]
                key = name + " $" + variant
            entry = result.setdefault(key, {})
            entry["Name"] = fields[1]
            if len(fields) >= 3:
                entry["Types"] = fields[2]
            if len(fields) >= 4:
                entry["Oracle"] = (fields[3].replace("//Level_2//\\n", "")
                                   .replace("//Level_3//\\n", "")
                                   .replace("\\n", "\n").replace("VERT", "|"))
    return result
