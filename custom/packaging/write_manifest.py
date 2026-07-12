import argparse
import hashlib
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest().upper()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("root", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("paths", nargs="*")
    parser.add_argument("--all", action="store_true")
    args = parser.parse_args()

    root = args.root.resolve()
    output = args.output.resolve()
    if args.all:
        files = [path for path in root.rglob("*") if path.is_file() and path.resolve() != output]
    else:
        files = [(root / relative).resolve() for relative in args.paths]

    lines = []
    for path in sorted(files, key=lambda item: item.relative_to(root).as_posix().casefold()):
        relative = path.relative_to(root).as_posix()
        lines.append(f"{sha256(path)} *{relative}")
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()

