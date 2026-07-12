import os
import sys
import re
import argparse

# Custom card names may contain non-ASCII characters. Keep diagnostics usable
# when the invoking Windows console defaults to a legacy code page.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

# List of all valid ApiTypes from forge-game/src/main/java/forge/game/ability/ApiType.java
VALID_API_TYPES = {
    "abandon", "activateability", "addorremovecounter", "addphase", "addturn", "advancecrank", "alterattribute", 
    "amass", "animate", "animateall", "attach", "ascend", "assemblecontraption", "assigngroup", "balance", 
    "becomemonarch", "becomesblocked", "bidlife", "block", "bond", "branch", "camouflage", "changecombatants", 
    "changespeed", "changetargets", "changetext", "changex", "changezone", "changezoneall", "chaosensues", 
    "charm", "choosecard", "choosecolor", "choosedirection", "chooseevenodd", "choosenumber", "chooseplayer", 
    "choosesector", "choosesource", "choosetype", "claimtheprize", "clash", "classlevelup", "cleanup", 
    "cloak", "clone", "companionchoose", "connive", "copypermanent", "copyspellability", "controlspell", 
    "controlplayer", "counter", "damageall", "dealdamage", "daytime", "debuff", "delayedtrigger", 
    "destroy", "destroyall", "dig", "digmultiple", "diguntil", "discard", "discover", "drainmana", 
    "draft", "draw", "eachdamage", "effect", "encode", "endcombatphase", "endturn", "exchangelife", 
    "exchangelifevariant", "exchangecontrol", "exchangecontrolvariant", "exchangepower", "exchangezone", 
    "explore", "fight", "flipacoin", "flipontobattlefield", "fog", "gaincontrol", "gaincontrolvariant", 
    "gainlife", "gainownership", "gamedrawn", "genericchoice", "goad", "haunt", "heist", "investigate", 
    "intensify", "immediatetrigger", "incubate", "learn", "lookat", "loselife", "loseperpetual", 
    "losesgame", "makecard", "mana", "manareflected", "manifest", "manifestdread", "meld", "mill", 
    "movecounter", "multiplepiles", "multiplycounter", "mustblock", "mutate", "namecard", "openattraction", 
    "peekandreveal", "permanentcreature", "permanentnoncreature", "phases", "planeswalk", "play", 
    "playlandvariant", "poison", "preventdamage", "proliferate", "protection", "protectionall", 
    "pump", "pumpall", "putcounter", "putcounterall", "radiation", "rearrangetopoflibrary", "regenerate", 
    "regeneration", "removecounter", "removecounterall", "removefromcombat", "removefromgame", "removefrommatch", 
    "reorderzone", "repeat", "repeateach", "replacecounter", "replaceeffect", "replacemana", "replacedamage", 
    "replacetoken", "replacesplitdamage", "restartgame", "reveal", "revealhand", "reverseturnorder", 
    "ringtemptsyou", "rolldice", "rollplanardice", "runchaos", "sacrifice", "sacrificeall", "scry", 
    "seek", "setinmotion", "setlife", "setstate", "shuffle", "skipphase", "skipturn", "storesvar", 
    "subgame", "surveil", "switchblock", "takeinitiative", "tap", "tapall", "taporuntap", "taporuntapall", 
    "timetravel", "token", "twopiles", "unattach", "unattachall", "unlockdoor", "untap", "untapall", 
    "venture", "villainouschoice", "vote", "winsgame", "blankline", "damageresolve", "changezoneresolve", 
    "internallegendaryrule", "internalignoreeffect", "internalradiation"
}

# List of all valid TriggerTypes from forge-game/src/main/java/forge/game/trigger/TriggerType.java
VALID_TRIGGER_TYPES = {
    "abandoned", "abilitycast", "abilityresolves", "abilitytriggered", "adapt", "always", "attached", 
    "attackerblocked", "attackerblockedonce", "attackerblockedbycreature", "attackersdeclared", 
    "attackersdeclaredonetarget", "attackerunblocked", "attackerunblockedonce", "attacks", "becomemonarch", 
    "becomemonstrous", "becomerenowned", "becomescrewed", "becomesplotted", "becomessaddled", "becomestarget", 
    "becomestargetonce", "blockersdeclared", "blocks", "casesolved", "championed", "changescontroller", 
    "changeszone", "changeszoneall", "chaosensues", "claimprize", "clashed", "classlevelgained", "commitcrime", 
    "conjureall", "collectevidence", "counteradded", "counteraddedonce", "counterplayeraddedall", 
    "counteraddedall", "countered", "counterremoved", "counterremovedonce", "crankcontraption", "crewed", 
    "cycled", "damageall", "damagedealtonce", "damagedone", "damagedoneonce", "damagedoneoncebycontroller", 
    "damagepreventedonce", "daytimechanges", "destroyed", "devoured", "discarded", "discardedall", "discover", 
    "drawn", "dungeoncompleted", "evolved", "excessdamage", "excessdamageall", "enlisted", "exerted", "exiled", 
    "exploited", "explores", "fight", "fightonce", "flippedcoin", "forage", "foretell", "fullyunlock", "givegift", 
    "immediate", "investigated", "landplayed", "lifechanged", "lifegained", "lifelost", "lifelostall", 
    "losesgame", "manaadded", "manaexpend", "manifestdread", "mentored", "milled", "milledonce", "milledall", 
    "mutates", "newgame", "paycumulativeupkeep", "payecho", "paylife", "phase", "phasein", "phaseout", 
    "phaseoutall", "planardice", "planeswalkedfrom", "planeswalkedto", "proliferate", "ringtemptsyou", 
    "rolleddie", "rolleddieonce", "roomentered", "saddled", "sacrificed", "sacrificedonce", "scry", 
    "searchedlibrary", "seekall", "setinmotion", "shuffled", "specializes", "spellabilitycast", 
    "spellabilitycopy", "spellcast", "spellcastorcopy", "spellcopy", "surveil", "takesinitiative", 
    "tapall", "taps", "tapsformana", "tokencreated", "tokencreatedonce", "trains", "transformed", 
    "turnbegin", "turnfaceup", "unattach", "unlockdoor", "untapall", "untaps", "visitattraction", "vote"
}

# List of all valid ReplacementTypes from forge-game/src/main/java/forge/game/replacement/ReplacementType.java
VALID_REPLACEMENT_TYPES = {
    "addcounter", "assemblecontraption", "assigndealdamage", "attached", "beginphase", "beginturn", "cascade", 
    "counter", "copyspell", "createtoken", "damagedone", "dealtdamage", "declareblocker", "destroy", "draw", 
    "drawcards", "explore", "gainlife", "gameloss", "gamewin", "learn", "lifereduced", "losemana", "mill", 
    "moved", "paylife", "planardiceresult", "planeswalk", "producemana", "proliferate", "removecounter", 
    "rolldice", "rollplanardice", "scry", "setinmotion", "tap", "transform", "turnfaceup", "untap"
}

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

def parse_pip_params(pip_str):
    # Splits parameters by '|' and cleans whitespace, preserving structure
    parts = pip_str.split("|")
    params = {}
    for part in parts:
        part = part.strip()
        if not part:
            continue
        if "$" in part:
            k, v = part.split("$", 1)
            params[k.strip()] = v.strip()
        else:
            params[part] = True
    return params

def lint_file(file_path):
    print(f"Linting custom card script: {file_path}")
    if not os.path.exists(file_path):
        print(f"Error: File '{file_path}' does not exist.")
        return False

    errors = []
    warnings = []

    # State tracking
    card_name = None
    mana_cost = None
    types = None
    svars = set()
    svar_refs = []  # Tuples of (svar_name, line_num, source)

    with open(file_path, "r", encoding="utf-8") as f:
        lines = f.readlines()

    for idx, line in enumerate(lines):
        line_num = idx + 1
        raw_line = line.strip()
        
        # Skip empty lines or comments
        if not raw_line or raw_line.startswith("#"):
            continue

        if ":" not in raw_line:
            errors.append(f"Line {line_num}: Missing colon separator: '{raw_line}'")
            continue

        key, val = raw_line.split(":", 1)
        key = key.strip()
        val = val.strip()

        if key == "Name":
            card_name = val
        elif key == "ManaCost":
            mana_cost = val
        elif key == "Types":
            types = val
        elif key == "SVar":
            if ":" not in val:
                errors.append(f"Line {line_num}: SVar must contain a colon separator: 'SVar:{val}'")
                continue
            svar_name, svar_val = val.split(":", 1)
            svars.add(svar_name.strip())
            # Check if this SVar value contains references to other SVars (subabilities)
            if "Execute$" in svar_val:
                match = re.search(r'Execute\$\s*([^|]*)', svar_val)
                if match:
                    svar_refs.append((match.group(1).strip(), line_num, f"SVar:{svar_name}"))
            if "SubAbility$" in svar_val:
                match = re.search(r'SubAbility\$\s*([^|]*)', svar_val)
                if match:
                    svar_refs.append((match.group(1).strip(), line_num, f"SVar:{svar_name}"))
            if "ReplaceWith$" in svar_val:
                match = re.search(r'ReplaceWith\$\s*([^|]*)', svar_val)
                if match:
                    svar_refs.append((match.group(1).strip(), line_num, f"SVar:{svar_name}"))

        elif key == "K":
            if val.startswith("DeckLimit:"):
                parts = val.split(":", 2)
                if len(parts) < 3 or not parts[1].isdigit() or not parts[2].strip():
                    errors.append(
                        f"Line {line_num}: DeckLimit must include limit and display text "
                        "(for example, 'K:DeckLimit:1:Your deck can have only one copy of CARDNAME.')."
                    )
            elif val == "DeckMinimum" or val.startswith("DeckMinimum:"):
                parts = val.split(":", 1)
                if len(parts) != 2 or not parts[1].isdigit() or int(parts[1]) <= 0:
                    errors.append(
                        f"Line {line_num}: DeckMinimum must specify a positive integer "
                        "(for example, 'K:DeckMinimum:31')."
                    )

        elif key in ("A", "T", "S", "R"):
            # Check formatting (pipes should have spaces around them usually, or at least be clean)
            if "|" in val:
                params = parse_pip_params(val)
                
                # Check for Execute, SubAbility, or ReplaceWith SVar references
                for ref_key in ("Execute", "SubAbility", "ReplaceWith"):
                    if ref_key + "$" in val:
                        svar_ref = params.get(ref_key)
                        if svar_ref:
                            # SVar can have parameters sometimes, get the base name
                            base_ref = svar_ref.split()[0]
                            svar_refs.append((base_ref, line_num, f"{key} Ability"))

                # Validate specific API/Trigger/Replacement types
                if key == "A":
                    # Check ApiType
                    api_type = None
                    for ab_prefix in ("AB", "SP", "DB"):
                        if ab_prefix in params:
                            api_type = params.get(ab_prefix)
                            break
                    if api_type:
                        # Clean type name (e.g. might have arguments)
                        clean_api = api_type.split()[0].lower()
                        if clean_api not in VALID_API_TYPES:
                            errors.append(f"Line {line_num}: Invalid ApiType '{api_type}' in ability script.")
                    else:
                        warnings.append(f"Line {line_num}: Ability line missing AB$, SP$, or DB$ prefix definition.")

                elif key == "T":
                    # Check TriggerType
                    trigger_type = params.get("Mode")
                    if trigger_type:
                        clean_trigger = trigger_type.lower()
                        if clean_trigger not in VALID_TRIGGER_TYPES:
                            errors.append(f"Line {line_num}: Invalid Trigger Mode '{trigger_type}' in trigger script.")
                    else:
                        errors.append(f"Line {line_num}: Trigger line missing Mode$ trigger type.")

                elif key == "R":
                    # Check ReplacementType
                    replace_type = params.get("Event")
                    if replace_type:
                        clean_replace = replace_type.lower()
                        if clean_replace not in VALID_REPLACEMENT_TYPES:
                            errors.append(f"Line {line_num}: Invalid Replacement Event '{replace_type}' in replacement script.")
                    else:
                        errors.append(f"Line {line_num}: Replacement line missing Event$ replacement type.")

    # Validation Checks
    if not card_name:
        errors.append("Missing required field: 'Name'")
    if not mana_cost:
        errors.append("Missing required field: 'ManaCost'")
    if not types:
        errors.append("Missing required field: 'Types'")

    # Validate filename matching convention
    if card_name:
        transformed_name = transform_name(card_name)
        if transformed_name:
            expected_filename = transformed_name + ".txt"
            actual_filename = os.path.basename(file_path)
            if expected_filename != actual_filename.lower():
                warnings.append(f"Filename mismatch. Based on Name '{card_name}', the script should be named '{expected_filename}' (current: '{actual_filename}').")

    # Validate SVar references
    for ref, line_num, source in svar_refs:
        # Ignore numeric expressions or card references (like self, CARDNAME, etc.)
        if ref.isdigit() or ref in ("Self", "CARDNAME", "this", "Opponent", "You"):
            continue
        # Forge allows subabilities to pass parameters like SVarName$Param
        base_ref = ref.split("$")[0]
        if base_ref not in svars:
            errors.append(f"Line {line_num}: Referenced SVar '{base_ref}' (in {source}) is not defined in this script.")

    # Print Results
    print(f"\n--- Validation Summary for {os.path.basename(file_path)} ---")
    if errors:
        print(f"[ERROR] Found {len(errors)} error(s):")
        for err in errors:
            print(f"  - {err}")
    else:
        print("[SUCCESS] No errors found!")

    if warnings:
        print(f"[WARNING] Found {len(warnings)} warning(s):")
        for warn in warnings:
            print(f"  - {warn}")

    print("-------------------------------------------\n")
    return len(errors) == 0

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Lint a custom Forge card script.")
    parser.add_argument("file", help="Path to the card script txt file.")
    args = parser.parse_args()
    
    success = lint_file(args.file)
    sys.exit(0 if success else 1)
