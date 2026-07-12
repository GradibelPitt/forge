import os
import sys
import argparse

def main():
    parser = argparse.ArgumentParser(description="Find similar official cards in Forge by searching for patterns.")
    parser.add_argument("terms", nargs="+", help="Strings to search for in card scripts (case-insensitive).")
    parser.add_argument("--limit", type=int, default=10, help="Maximum number of results to display.")
    parser.add_argument("--repo-path", default=r"D:\Forge\forge-latest\forge-gui\res\cardsfolder",
                        help="Path to the official cards folder.")
    args = parser.parse_args()

    if not os.path.exists(args.repo_path):
        print(f"Error: Cards folder not found at '{args.repo_path}'", file=sys.stderr)
        sys.exit(1)

    print(f"Searching for cards containing: {', '.join(f'\"{t}\"' for t in args.terms)}")
    print(f"Scanning directory: {args.repo_path}...\n")

    results = []
    terms_lower = [t.lower() for t in args.terms]

    # Walk through folders recursively
    for root, dirs, files in os.walk(args.repo_path):
        for file in files:
            if file.endswith(".txt"):
                file_path = os.path.join(root, file)
                try:
                    with open(file_path, "r", encoding="utf-8") as f:
                        content = f.read()
                    
                    content_lower = content.lower()
                    if all(term in content_lower for term in terms_lower):
                        results.append((file, file_path, content))
                except Exception as e:
                    # Ignore encoding errors or permission issues
                    continue

    if not results:
        print("No matching cards found.")
        return

    print(f"Found {len(results)} matching card(s). Displaying top {min(args.limit, len(results))}:\n")
    for i, (name, path, content) in enumerate(results[:args.limit]):
        print(f"=== {name} ({os.path.relpath(path, args.repo_path)}) ===")
        # Print a snippet of the script (or all of it if small)
        lines = content.strip().split("\n")
        # Find lines that match the search terms to show context
        for idx, line in enumerate(lines):
            line_lower = line.lower()
            is_match = any(term in line_lower for term in terms_lower)
            prefix = "--> " if is_match else "    "
            print(f"{prefix}{idx+1}: {line}")
        print("\n")

if __name__ == "__main__":
    main()
