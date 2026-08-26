#!/usr/bin/env bash

set -Eeuo pipefail

readonly RUNTIME_URL="https://github.com/GradibelPitt/forge-diy-runtime.git"
readonly RUNTIME_REPO="${FORGE_DIY_RUNTIME_DIR:-${HOME}/Library/Application Support/ForgeDIY/repo}"
readonly APP_DIR="${RUNTIME_REPO}/app"
readonly FORGE_PROFILE="${HOME}/Library/Application Support/Forge"
readonly FORGE_CACHE="${HOME}/Library/Caches/Forge"
readonly FORGE_PREFERENCES="${FORGE_PROFILE}/preferences/forge.preferences"

update_runtime=true
install_only=false
forge_args=()

usage() {
    cat <<'EOF'
Usage: ./run.sh [options] [-- Forge arguments]

Update, install, and run the current Forge DIY runtime on macOS.

Options:
  --no-update     Use the installed runtime without pulling GitHub updates.
  --install-only  Update, verify, and sync the DIY payload without starting Forge.
  -h, --help      Show this help.

Examples:
  ./run.sh
  ./run.sh --no-update
  ./run.sh --install-only
  ./run.sh -- sim -d deck1 deck2 -n 3
EOF
}

while (($# > 0)); do
    case "$1" in
        --no-update)
            update_runtime=false
            shift
            ;;
        --install-only)
            install_only=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        --)
            shift
            forge_args+=("$@")
            break
            ;;
        *)
            forge_args+=("$1")
            shift
            ;;
    esac
done

if ! command -v git >/dev/null 2>&1; then
    echo "Error: Git is required to install the Forge DIY runtime." >&2
    exit 1
fi

if ! command -v java >/dev/null 2>&1; then
    echo "Error: Java 17 or newer is required." >&2
    exit 1
fi

java_version="$(java -version 2>&1 | sed -n '1s/.*version "\([^"]*\)".*/\1/p')"
java_major="${java_version%%.*}"
if [[ "$java_major" == "1" ]]; then
    java_major="$(printf '%s' "$java_version" | cut -d. -f2)"
fi
if [[ ! "$java_major" =~ ^[0-9]+$ ]] || ((java_major < 17)); then
    echo "Error: Java 17 or newer is required (found ${java_version:-unknown})." >&2
    exit 1
fi

install_or_update_runtime() {
    if [[ ! -d "${RUNTIME_REPO}/.git" ]]; then
        mkdir -p "$(dirname "$RUNTIME_REPO")"
        echo "Installing the Forge DIY runtime..."
        git clone --depth 1 "$RUNTIME_URL" "$RUNTIME_REPO"
        return
    fi

    if [[ "$update_runtime" == true ]]; then
        echo "Updating the Forge DIY runtime..."
        if ! git -C "$RUNTIME_REPO" pull --ff-only; then
            echo "Warning: runtime update failed; using the installed verified runtime." >&2
        fi
    fi
}

verify_runtime() {
    local manifest="${APP_DIR}/manifest-critical.sha256"
    if [[ ! -f "$manifest" ]]; then
        echo "Error: runtime manifest is missing: $manifest" >&2
        return 1
    fi

    local line expected relative actual
    local checked=0
    while IFS= read -r line || [[ -n "$line" ]]; do
        line="${line%$'\r'}"
        [[ -z "$line" ]] && continue

        expected="${line%% *}"
        expected="$(printf '%s' "$expected" | tr '[:lower:]' '[:upper:]')"
        relative="${line#* }"
        relative="${relative#\*}"
        if [[ ! "$expected" =~ ^[0-9A-Fa-f]{64}$ ]] || [[ -z "$relative" ]]; then
            echo "Error: invalid runtime manifest entry: $line" >&2
            return 1
        fi
        if [[ ! -f "${APP_DIR}/${relative}" ]]; then
            echo "Error: runtime file is missing: $relative" >&2
            return 1
        fi

        actual="$(LC_ALL=C shasum -a 256 "${APP_DIR}/${relative}" | awk '{print toupper($1)}')"
        if [[ "$expected" != "$actual" ]]; then
            echo "Error: runtime checksum mismatch: $relative" >&2
            return 1
        fi
        ((checked += 1))
    done < "$manifest"

    echo "Verified ${checked} runtime files."
}

disable_builtin_music() {
    local custom_menu="${APP_DIR}/managed/custom/music/Pull Up a Chair/menus/Pull Up a Chair.mp3"
    local custom_match="${APP_DIR}/managed/custom/music/Pull Up a Chair/match/Bad Down to the Molten Core.mp3"
    local removed=0
    local track
    local builtin_tracks=(
        "${APP_DIR}/res/music/menus/Heroic Age.mp3"
        "${APP_DIR}/res/music/menus/Lord of the Land.mp3"
        "${APP_DIR}/res/music/menus/Evil March.mp3"
        "${APP_DIR}/res/music/menus/The Pyre.mp3"
        "${APP_DIR}/res/music/match/Dangerous.mp3"
        "${APP_DIR}/res/music/match/Failing Defense.mp3"
        "${APP_DIR}/res/music/match/Prelude and Action.mp3"
        "${APP_DIR}/res/music/match/Hitman.mp3"
    )

    if [[ ! -f "$custom_menu" || ! -f "$custom_match" ]]; then
        echo "Error: refusing to disable built-in music before both custom tracks are present." >&2
        return 1
    fi

    for track in "${builtin_tracks[@]}"; do
        if [[ -f "$track" ]]; then
            rm -f -- "$track"
            ((removed += 1))
        fi
    done

    echo "Disabled Forge built-in lobby and match music: removed=${removed}."
}

sync_tree() {
    local source="$1"
    local destination="$2"
    local pattern="$3"
    local count=0

    [[ -d "$source" ]] || {
        printf '0\n'
        return
    }

    mkdir -p "$destination"
    if [[ "$pattern" == "*.txt" ]]; then
        rsync -a --include='*/' --include='*.txt' --exclude='*' \
            "${source}/" "${destination}/"
    else
        rsync -a "${source}/" "${destination}/"
    fi

    local source_file relative target
    while IFS= read -r -d '' source_file; do
        relative="${source_file#${source}/}"
        target="${destination}/${relative}"
        if [[ ! -f "$target" ]] || ! cmp -s "$source_file" "$target"; then
            echo "Error: synced file differs: $target" >&2
            return 1
        fi
        ((count += 1))
    done < <(find "$source" -type f -name "$pattern" -print0)

    printf '%s\n' "$count"
}

set_managed_preference() {
    local key="$1"
    local value="$2"
    local preferences="$3"
    local temporary

    mkdir -p "$(dirname "$preferences")"
    touch "$preferences"
    temporary="$(mktemp "${preferences}.tmp.XXXXXX")"
    awk -F= -v key="$key" -v value="$value" '
        $1 == key {
            if (!found) {
                print key "=" value
                found = 1
            }
            next
        }
        { print }
        END {
            if (!found) {
                print key "=" value
            }
        }
    ' "$preferences" > "$temporary"
    mv "$temporary" "$preferences"
}

sync_diy_payload() {
    local managed="${APP_DIR}/managed/custom"
    local custom="${FORGE_PROFILE}/custom"
    local card_cache="${FORGE_CACHE}/pics/cards"
    local token_cache="${FORGE_CACHE}/pics/tokens"

    local cards editions tokens music card_images token_images
    cards="$(sync_tree "${managed}/cards" "${custom}/cards" '*.txt')"
    editions="$(sync_tree "${managed}/editions" "${custom}/editions" '*.txt')"
    tokens="$(sync_tree "${managed}/tokens" "${custom}/tokens" '*.txt')"
    music="$(sync_tree "${managed}/music" "${custom}/music" '*')"
    card_images="$(sync_tree "${managed}/cards/pictures" "$card_cache" '*')"
    token_images="$(sync_tree "${managed}/tokens/pictures" "$token_cache" '*')"

    set_managed_preference UI_ENABLE_MUSIC true "$FORGE_PREFERENCES"
    set_managed_preference UI_VOL_MUSIC 100 "$FORGE_PREFERENCES"
    set_managed_preference UI_CURRENT_MUSIC_SET 'Pull Up a Chair' "$FORGE_PREFERENCES"

    echo "Synced DIY payload: cards=${cards}, editions=${editions}, tokens=${tokens}, music=${music}, card images=${card_images}, token images=${token_images}."
    echo "Selected custom music set: Pull Up a Chair."
}

install_or_update_runtime

if [[ ! -d "$APP_DIR" ]]; then
    echo "Error: Forge DIY app directory is missing: $APP_DIR" >&2
    exit 1
fi

verify_runtime
disable_builtin_music
sync_diy_payload

build_id="$(tr -d '\r\n' < "${APP_DIR}/BUILD-ID.txt")"
if [[ "$install_only" == true ]]; then
    echo "Forge DIY runtime is installed: ${build_id}"
    exit 0
fi

shopt -s nullglob
jars=("${APP_DIR}"/*-jar-with-dependencies.jar)
if ((${#jars[@]} != 1)); then
    echo "Error: expected one Forge desktop JAR in $APP_DIR; found ${#jars[@]}." >&2
    exit 1
fi

classpath=""
overlay_count=0
overlays=("${APP_DIR}/overlays"/*.jar)
for entry in "${overlays[@]}" "${jars[0]}"; do
    if [[ -z "$classpath" ]]; then
        classpath="$entry"
    else
        classpath="${classpath}:$entry"
    fi
done
overlay_count="${#overlays[@]}"

java_args=(
    -Xmx4096m
    -Dio.netty.tryReflectionSetAccessible=true
    -Dfile.encoding=UTF-8
    -Dapple.awt.application.name=ForgeDIY
    -Dcom.apple.macos.use-file-dialog-packages=true
    -Dcom.apple.macos.useScreenMenuBar=true
)

echo "Starting Forge DIY ${build_id} with ${overlay_count} overlay JAR(s)..."
cd "$APP_DIR"
if ((${#forge_args[@]} > 0)); then
    exec java "${java_args[@]}" -cp "$classpath" forge.view.Main "${forge_args[@]}"
else
    exec java "${java_args[@]}" -cp "$classpath" forge.view.Main
fi
