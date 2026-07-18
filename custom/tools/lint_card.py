import os
import sys
import re
import argparse
import unicodedata

# Custom card names may contain non-ASCII characters. Keep diagnostics usable
# when the invoking Windows console defaults to a legacy code page.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

# List of all valid ApiTypes from forge-game/src/main/java/forge/game/ability/ApiType.java
VALID_API_TYPES = {
    "abandon", "activateability", "addorremovecounter", "addphase", "addturn", "advancecrank", "alterattribute", 
    "amass", "animate", "animateall", "attach", "ascend", "assemblecontraption", "assigngroup", "balance", 
    "becomemonarch", "becomesblocked", "bidlife", "block", "bond", "branch", "camouflage", "changecombatants", 
    "changespeed", "changetargets", "changetext", "changex", "changezone", "changezoneall", "carddiscover", "chaosensues",
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
    "regeneration", "removecounter", "removecounterall", "removefromcombat", "replacecards", "removefromgame", "removefrommatch",
    "reorderzone", "repeat", "repeateach", "replacecounter", "replaceeffect", "replacemana", "replacedamage", 
    "replacetoken", "replacesplitdamage", "restartgame", "reveal", "revealhand", "reverseturnorder", 
    "ringtemptsyou", "rolldice", "rollplanardice", "runchaos", "sacrifice", "sacrificeall", "scry", 
    "seek", "setinmotion", "setlife", "setstate", "shuffle", "skipphase", "skipturn", "stealsamename", "storesvar", 
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
    "drawn", "drawnall", "dungeoncompleted", "evolved", "excessdamage", "excessdamageall", "enlisted", "exerted", "exiled",
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

DECK_RULE_MODES = {"ADD_FIXED", "CHOOSE_ONE", "ALLOW"}
DECK_RULE_CONSTRAINTS = {
    "FORMAT_CARD_POOL",
    "COMMANDER_COLOR_IDENTITY",
    "COPY_LIMIT",
    "SECTION",
    "BANNED_OR_RESTRICTED",
}
DECK_RULE_SECTIONS = {
    "MAIN", "SIDEBOARD", "COMMANDER", "AVATAR", "PLANES", "SCHEMES",
    "CONSPIRACY", "DUNGEON", "ATTRACTIONS", "CONTRAPTIONS",
}
DECK_RULE_FIELD_DISPLAY = {
    "ID": "Id",
    "MODE": "Mode",
    "TARGET": "Target",
    "CARD": "Card",
    "AMOUNT": "Amount",
    "CANDIDATES": "Candidates",
    "CONSTRAINT": "Constraint",
    "CARDINALITY": "Cardinality",
}
MAX_DECK_RULES_PER_CARD = 100
MAX_DECK_RULE_LINE_LENGTH = 16384
MAX_DECK_RULE_FIELD_LENGTH = 8192
MAX_DECK_RULE_FIELDS = 16
MAX_RULE_ID_LENGTH = 1024
MAX_CARD_NAME_LENGTH = 4096
MAX_DECK_RULE_AMOUNT = 1000
MAX_DECK_RULE_CANDIDATES = 1000
FACE_SETTER_KEYS = {
    "A", "Colors", "Defense", "Draft", "FlavorName", "K", "Loyalty",
    "Lights", "ManaCost", "Oracle", "PT", "R", "S", "SVar", "T",
    "Text", "Types", "Variant",
}


def _java_trim(value):
    start = 0
    end = len(value)
    while start < end and ord(value[start]) <= 0x20:
        start += 1
    while end > start and ord(value[end - 1]) <= 0x20:
        end -= 1
    return value[start:end]


def _java_is_whitespace(character):
    codepoint = ord(character)
    if 0x09 <= codepoint <= 0x0d or 0x1c <= codepoint <= 0x1f:
        return True
    if codepoint in (0x00a0, 0x2007, 0x202f):
        return False
    return unicodedata.category(character) in ("Zs", "Zl", "Zp")


def _java_strip(value):
    start = 0
    end = len(value)
    while start < end and _java_is_whitespace(value[start]):
        start += 1
    while end > start and _java_is_whitespace(value[end - 1]):
        end -= 1
    return value[start:end]


def _contains_forbidden_deck_rule_control(value):
    return any(ord(character) <= 0x1f or ord(character) == 0x7f for character in value)


def _exceeds_utf8_limit(value, maximum_bytes):
    return len(value) > maximum_bytes or len(value.encode("utf-8")) > maximum_bytes


def _resolve_deck_rule_face_name(actual_name, placeholder_name):
    selected_name = actual_name if actual_name is not None else placeholder_name
    if selected_name is None:
        return None
    stripped_name = _java_strip(selected_name)
    return stripped_name if stripped_name else None

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


def _parse_deck_rule_params(rule_text, line_num, errors):
    params = {}
    raw_segments = rule_text.split("|")
    if len(raw_segments) > MAX_DECK_RULE_FIELDS:
        errors.append(
            f"Line {line_num}: DeckRule may contain at most "
            f"{MAX_DECK_RULE_FIELDS} fields."
        )
        return params

    for raw_segment in raw_segments:
        segment = _java_strip(raw_segment)
        if _exceeds_utf8_limit(raw_segment, MAX_DECK_RULE_FIELD_LENGTH):
            errors.append(
                f"Line {line_num}: DeckRule field exceeds "
                f"{MAX_DECK_RULE_FIELD_LENGTH} UTF-8 bytes."
            )
            continue
        if not segment or "$" not in segment:
            errors.append(
                f"Line {line_num}: DeckRule parameter must use 'Name$ value' syntax."
            )
            continue

        raw_name, raw_value = raw_segment.split("$", 1)
        name = _java_strip(raw_name)
        value = _java_strip(raw_value)
        if not name:
            errors.append(
                f"Line {line_num}: DeckRule parameter must use 'Name$ value' syntax."
            )
            continue
        canonical_name = name.upper()
        if canonical_name == "ID" and _contains_forbidden_deck_rule_control(raw_value):
            errors.append(
                f"Line {line_num}: DeckRule Id contains a forbidden control character."
            )
        if canonical_name in params:
            errors.append(f"Line {line_num}: DeckRule has duplicate parameter '{name}'.")
            continue
        if not value:
            errors.append(f"Line {line_num}: DeckRule parameter '{name}' must have a value.")
            continue
        params[canonical_name] = value
    return params


def _require_deck_rule_params(params, required, line_num, errors):
    for name in required:
        if name not in params:
            display_name = DECK_RULE_FIELD_DISPLAY.get(name, name)
            errors.append(
                f"Line {line_num}: DeckRule missing required parameter '{display_name}'."
            )


def _display_deck_rule_field(name):
    return DECK_RULE_FIELD_DISPLAY.get(name, name.title())


def _normalize_deck_rule_display(value):
    return unicodedata.normalize("NFC", _java_strip(value))


def _canonical_deck_rule_card_name(card_name):
    normalized = unicodedata.normalize("NFC", card_name)
    return unicodedata.normalize("NFC", normalized.upper())


def _deck_rule_card_name_exceeds_limit(card_name):
    display_name = _normalize_deck_rule_display(card_name)
    if _exceeds_utf8_limit(display_name, MAX_CARD_NAME_LENGTH):
        return True
    canonical_name = _canonical_deck_rule_card_name(display_name)
    return _exceeds_utf8_limit(canonical_name, MAX_CARD_NAME_LENGTH)


def _lint_deck_rule(rule_text, line_num, seen_ids):
    errors = []
    if _exceeds_utf8_limit(rule_text, MAX_DECK_RULE_LINE_LENGTH):
        errors.append(
            f"Line {line_num}: DeckRule line exceeds "
            f"{MAX_DECK_RULE_LINE_LENGTH} UTF-8 bytes."
        )
        return errors

    params = _parse_deck_rule_params(rule_text, line_num, errors)
    _require_deck_rule_params(params, ("ID", "MODE"), line_num, errors)

    rule_id = params.get("ID")
    if rule_id:
        normalized_rule_id = _normalize_deck_rule_display(rule_id)
        if _exceeds_utf8_limit(normalized_rule_id, MAX_RULE_ID_LENGTH):
            errors.append(
                f"Line {line_num}: DeckRule Id exceeds "
                f"{MAX_RULE_ID_LENGTH} UTF-8 bytes."
            )
        if normalized_rule_id in seen_ids:
            errors.append(f"Line {line_num}: duplicate DeckRule Id '{rule_id}'.")
        else:
            seen_ids.add(normalized_rule_id)

    cardinality = params.get("CARDINALITY", "ONCE_PER_DECK")
    if cardinality.upper() != "ONCE_PER_DECK":
        errors.append(
            f"Line {line_num}: unsupported Cardinality '{cardinality}'; "
            "only ONCE_PER_DECK is supported."
        )

    mode_value = params.get("MODE")
    if not mode_value:
        return errors
    mode = mode_value.upper()
    if mode not in DECK_RULE_MODES:
        errors.append(f"Line {line_num}: unknown Mode '{mode_value}' in DeckRule.")
        safe_names = {
            "ID", "MODE", "CARDINALITY", "TARGET", "CARD", "AMOUNT",
            "CANDIDATES", "CONSTRAINT",
        }
        for name in params:
            if name not in safe_names:
                errors.append(
                    f"Line {line_num}: unexpected DeckRule parameter "
                    f"'{_display_deck_rule_field(name)}'."
                )
        return errors

    required = {"ID", "MODE"}
    allowed = {"ID", "MODE", "CARDINALITY"}
    constraint = None
    if mode == "ADD_FIXED":
        required.update(("TARGET", "CARD", "AMOUNT"))
        allowed.update(("TARGET", "CARD", "AMOUNT"))
    elif mode == "CHOOSE_ONE":
        required.update(("TARGET", "CANDIDATES", "AMOUNT"))
        allowed.update(("TARGET", "CANDIDATES", "AMOUNT"))
    else:
        required.update(("CONSTRAINT", "CARD"))
        allowed.update(("CONSTRAINT", "CARD"))
        constraint_value = params.get("CONSTRAINT")
        if constraint_value:
            constraint = constraint_value.upper()
            if constraint not in DECK_RULE_CONSTRAINTS:
                errors.append(
                    f"Line {line_num}: unknown Constraint '{constraint_value}' in DeckRule."
                )
            elif constraint == "SECTION":
                required.add("TARGET")
                allowed.add("TARGET")

    _require_deck_rule_params(params, required, line_num, errors)

    for name in params:
        if name not in allowed:
            if mode == "ALLOW" and name == "TARGET" and constraint:
                errors.append(
                    f"Line {line_num}: unexpected parameter 'Target' for Mode ALLOW "
                    f"with Constraint {constraint}."
                )
            else:
                errors.append(
                    f"Line {line_num}: unexpected parameter "
                    f"'{_display_deck_rule_field(name)}' for Mode {mode}."
                )

    target = params.get("TARGET")
    if "TARGET" in allowed and target and target.upper() not in DECK_RULE_SECTIONS:
        errors.append(f"Line {line_num}: unknown Target deck section '{target}'.")

    if mode in ("ADD_FIXED", "CHOOSE_ONE"):
        amount = params.get("AMOUNT")
        valid_amount = False
        if amount and re.fullmatch(r"[+-]?[0-9]+", amount):
            unsigned = amount.lstrip("+")
            if not unsigned.startswith("-"):
                significant = unsigned.lstrip("0") or "0"
                if len(significant) <= 4:
                    valid_amount = 1 <= int(significant) <= MAX_DECK_RULE_AMOUNT
        if amount and not valid_amount:
            errors.append(
                f"Line {line_num}: DeckRule Amount must be an integer from 1 through "
                f"{MAX_DECK_RULE_AMOUNT}."
            )

    if mode in ("ADD_FIXED", "ALLOW") and "CARD" in params:
        if _deck_rule_card_name_exceeds_limit(params["CARD"]):
            errors.append(
                f"Line {line_num}: DeckRule Card exceeds "
                f"{MAX_CARD_NAME_LENGTH} UTF-8 bytes."
            )

    if mode == "CHOOSE_ONE" and "CANDIDATES" in params:
        candidates = [_java_strip(candidate) for candidate in params["CANDIDATES"].split(";")]
        canonical_candidates = {
            _canonical_deck_rule_card_name(candidate) for candidate in candidates if candidate
        }
        oversized_candidate = any(
            candidate and _deck_rule_card_name_exceeds_limit(candidate)
            for candidate in candidates
        )
        if oversized_candidate:
            errors.append(
                f"Line {line_num}: DeckRule Candidate name exceeds "
                f"{MAX_CARD_NAME_LENGTH} UTF-8 bytes."
            )
        if (
            any(not candidate for candidate in candidates)
            or not 1 <= len(canonical_candidates) <= MAX_DECK_RULE_CANDIDATES
        ):
            errors.append(
                f"Line {line_num}: DeckRule Candidates must contain from 1 through "
                f"{MAX_DECK_RULE_CANDIDATES} non-empty card names; empty entries are not allowed."
            )

    return errors

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
    deck_rule_ids = set()
    deck_rule_count = 0
    face_names = [None] * 7
    placeholder_face_names = [None] * 7
    face_mana_costs = [None] * 7
    face_types = [None] * 7
    current_face = 0
    alternate_mode = "None"
    alternate_mode_error = None
    known_alternate_modes = {
        "None", "Transform", "Meld", "Split", "Flip", "Adventure",
        "Omen", "Modal", "Prepare", "Specialize", "DoubleFaced",
    }

    with open(file_path, "r", encoding="utf-8") as f:
        lines = f.readlines()

    for idx, line in enumerate(lines):
        line_num = idx + 1
        source_line = line.rstrip("\r\n")
        raw_line = _java_trim(source_line)
        
        # Skip empty lines or comments
        if not raw_line or raw_line.startswith("#"):
            continue

        if raw_line == "ALTERNATE":
            current_face = 1
            continue

        if ":" not in raw_line:
            errors.append(f"Line {line_num}: Missing colon separator: '{raw_line}'")
            continue

        key, raw_value = raw_line.split(":", 1)
        val = _java_trim(raw_value)

        needs_face = key in FACE_SETTER_KEYS and not (key == "Text" and not _java_strip(val))
        if needs_face and face_names[current_face] is None:
            errors.append(
                f"Line {line_num}: face field '{key}' requires an initialized face; "
                "Name must precede it and CopyFaceFrom placeholders cannot receive face fields."
            )
            continue

        if key == "Name":
            face_names[current_face] = val
            face_mana_costs[current_face] = None
            face_types[current_face] = None
        elif key == "AlternateMode":
            if val not in known_alternate_modes and alternate_mode_error is None:
                alternate_mode_error = (line_num, val)
            alternate_mode = "Transform" if val == "DoubleFaced" else val
        elif key == "ALTERNATE":
            current_face = 1
        elif key == "CopyFaceFrom":
            placeholder_face_names[current_face] = val
        elif key.startswith("SPECIALIZE"):
            specialize_faces = {
                "WHITE": 2,
                "BLUE": 3,
                "BLACK": 4,
                "RED": 5,
                "GREEN": 6,
            }
            if val in specialize_faces:
                current_face = specialize_faces[val]
        elif key == "ManaCost":
            if face_names[current_face] is not None:
                face_mana_costs[current_face] = val
        elif key == "Types":
            if face_names[current_face] is not None:
                face_types[current_face] = val
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

        elif key == "DeckRule":
            deck_rule_count += 1
            if deck_rule_count <= MAX_DECK_RULES_PER_CARD:
                errors.extend(_lint_deck_rule(val, line_num, deck_rule_ids))
            elif deck_rule_count == MAX_DECK_RULES_PER_CARD + 1:
                errors.append(
                    f"Line {line_num}: DeckRule supports at most "
                    f"{MAX_DECK_RULES_PER_CARD} DeckRule lines per card; "
                    "remaining DeckRule lines were not linted."
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

    filename_main_face_name = (
        face_names[0] if face_names[0] is not None else placeholder_face_names[0]
    )
    filename_other_face_name = (
        face_names[1] if face_names[1] is not None else placeholder_face_names[1]
    )
    if (
        alternate_mode == "Split"
        and filename_main_face_name is not None
        and filename_other_face_name is not None
    ):
        card_name = f"{filename_main_face_name} // {filename_other_face_name}"
    else:
        card_name = filename_main_face_name

    construction_main_face_name = _resolve_deck_rule_face_name(
        face_names[0], placeholder_face_names[0]
    )
    construction_other_face_name = _resolve_deck_rule_face_name(
        face_names[1], placeholder_face_names[1]
    )
    construction_source_name = None
    source_name_resolved = False
    if alternate_mode_error is not None:
        if deck_rule_count:
            error_line, invalid_mode = alternate_mode_error
            errors.append(
                f"Line {error_line}: cannot determine DeckRule source name for "
                f"unsupported AlternateMode '{invalid_mode}'."
            )
    elif construction_main_face_name is None:
        if deck_rule_count:
            errors.append(
                "DeckRule cannot determine DeckRule source name: primary face name is missing."
            )
    elif alternate_mode == "Split":
        if construction_other_face_name is None:
            if deck_rule_count:
                errors.append(
                    "DeckRule cannot determine DeckRule source name: Split requires both face names."
                )
        else:
            construction_source_name = (
                f"{construction_main_face_name} // {construction_other_face_name}"
            )
            source_name_resolved = True
    else:
        construction_source_name = construction_main_face_name
        source_name_resolved = True

    if deck_rule_count and source_name_resolved:
        if _contains_forbidden_deck_rule_control(construction_source_name):
            errors.append(
                "DeckRule source card name contains a forbidden control character."
            )
        if _deck_rule_card_name_exceeds_limit(construction_source_name):
            errors.append(
                f"DeckRule source card name exceeds "
                f"{MAX_CARD_NAME_LENGTH} UTF-8 bytes."
            )

    main_face_is_placeholder = (
        face_names[0] is None and placeholder_face_names[0] is not None
    )
    mana_cost = face_mana_costs[0]
    types = face_types[0]

    # Validation Checks
    if not card_name:
        errors.append("Missing required field: 'Name'")
    if not mana_cost and not main_face_is_placeholder:
        errors.append("Missing required field: 'ManaCost'")
    if not types and not main_face_is_placeholder:
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
