import os
import sys
import argparse

def transform_name(card_name):
    # Match Forge's CardStorageReader.transformName Java method
    chars = []
    card_name = card_name.lower()
    for i, char in enumerate(card_name):
        if char == "'":
            continue
        if ('a' <= char <= 'z') or ('0' <= char <= '9'):
            chars.append(char)
        else:
            if chars and chars[-1] == '_':
                continue
            if char == ',' and chars and ('0' <= chars[-1] <= '9'):
                continue
            chars.append('_')
    if chars and chars[-1] == '_':
        chars.pop()
    return "".join(chars)

def main():
    parser = argparse.ArgumentParser(description="Scaffold a new custom card script.")
    parser.add_argument("name", help="Name of the card (e.g. 'Goblin Card Guide').")
    parser.add_argument("--type", required=True, help="Types line (e.g. 'Creature Goblin Warrior').")
    parser.add_argument("--cost", default="no cost", help="Mana cost (e.g. '1 R'). Defaults to 'no cost'.")
    parser.add_argument("--pt", help="Power/Toughness (e.g. '2/2'). Required if it is a Creature.")
    parser.add_argument("--color", help="Force folder sorting color (white, blue, black, red, green, multicolor, colorless, lands).")
    args = parser.parse_args()

    # Determine sorting folder based on cost/type
    color_folder = "colorless"
    types_lower = args.type.lower()
    
    if args.color:
        color_folder = args.color.lower()
    elif "land" in types_lower:
        color_folder = "lands"
    elif args.cost != "no cost":
        cost_clean = args.cost.upper().replace(" ", "")
        colors = []
        for c in ("W", "U", "B", "R", "G"):
            if c in cost_clean:
                colors.append(c)
        if len(colors) > 1:
            color_folder = "multicolor"
        elif len(colors) == 1:
            mapping = {"W": "white", "U": "blue", "B": "black", "R": "red", "G": "green"}
            color_folder = mapping[colors[0]]
        else:
            color_folder = "colorless"

    # Ensure color folder is valid
    valid_folders = ("white", "blue", "black", "red", "green", "multicolor", "colorless", "lands")
    if color_folder not in valid_folders:
        print(f"Warning: Folder '{color_folder}' is not standard. Defaulting to 'colorless'.")
        color_folder = "colorless"

    # Build target directory
    workspace_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    target_dir = os.path.join(workspace_root, "cards", color_folder)
    os.makedirs(target_dir, exist_ok=True)

    # Build target file
    filename = transform_name(args.name) + ".txt"
    target_file = os.path.join(target_dir, filename)

    if os.path.exists(target_file):
        print(f"Error: Card file already exists at '{target_file}'")
        sys.exit(1)

    # Creature check
    is_creature = "creature" in types_lower
    if is_creature and not args.pt:
        print("Warning: Creature card specified but power/toughness (--pt) is missing. Using placeholder '1/1'.")
        args.pt = "1/1"

    # Assemble template
    lines = []
    lines.append(f"Name:{args.name}")
    lines.append(f"ManaCost:{args.cost}")
    lines.append(f"Types:{args.type}")
    if args.pt:
        lines.append(f"PT:{args.pt}")
    
    lines.append("# Add abilities here (e.g. K:Flying or A:SP$ DealDamage)")
    
    # Generic Oracle description
    oracle_text = args.name
    if is_creature:
        oracle_text += f" ({args.pt})"
    lines.append(f"Oracle:{oracle_text}.")
    
    content = "\n".join(lines) + "\n"

    with open(target_file, "w", encoding="utf-8") as f:
        f.write(content)

    print(f"Created new card scaffold at: {os.path.relpath(target_file, workspace_root)}")
    print(f"Name: {args.name}")
    print(f"ManaCost: {args.cost}")
    print(f"Types: {args.type}")
    if args.pt:
        print(f"PT: {args.pt}")

if __name__ == "__main__":
    main()
