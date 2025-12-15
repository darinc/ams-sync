#!/bin/bash
#
# Merge old config values onto new config file
# Preserves new file structure and comments, overlays old values
#
# Usage: ./merge-config.sh <old-config> <new-config> [output-file]
#        If output-file omitted, prints to stdout

set -e

OLD_CONFIG="${1:?Usage: $0 <old-config> <new-config> [output-file]}"
NEW_CONFIG="${2:?Usage: $0 <old-config> <new-config> [output-file]}"
OUTPUT="${3:-/dev/stdout}"

if [[ ! -f "$OLD_CONFIG" ]]; then
    echo "Error: Old config not found: $OLD_CONFIG" >&2
    exit 1
fi

if [[ ! -f "$NEW_CONFIG" ]]; then
    echo "Error: New config not found: $NEW_CONFIG" >&2
    exit 1
fi

# Create temp file for processing
TEMP_FILE=$(mktemp)
cp "$NEW_CONFIG" "$TEMP_FILE"

# Extract key-value pairs from old config (handles nested YAML)
# Outputs: "full.path.to.key|value"
extract_values() {
    local file="$1"
    local prefix=""
    local indent_stack=()
    local prefix_stack=()

    while IFS= read -r line || [[ -n "$line" ]]; do
        # Skip comments and empty lines
        [[ "$line" =~ ^[[:space:]]*# ]] && continue
        [[ "$line" =~ ^[[:space:]]*$ ]] && continue

        # Count leading spaces
        local stripped="${line#"${line%%[![:space:]]*}"}"
        local indent=$(( ${#line} - ${#stripped} ))

        # Extract key and value
        if [[ "$stripped" =~ ^([a-zA-Z0-9_-]+):[[:space:]]*(.*) ]]; then
            local key="${BASH_REMATCH[1]}"
            local value="${BASH_REMATCH[2]}"

            # Adjust prefix based on indentation
            while [[ ${#indent_stack[@]} -gt 0 && ${indent_stack[-1]} -ge $indent ]]; do
                unset 'indent_stack[-1]'
                unset 'prefix_stack[-1]'
            done

            # Build full path
            local full_path=""
            for p in "${prefix_stack[@]}"; do
                full_path="${full_path}${p}."
            done
            full_path="${full_path}${key}"

            # If value is not empty and not a nested object marker
            if [[ -n "$value" && ! "$value" =~ ^\{.*\}$ && "$value" != "{}" ]]; then
                echo "${full_path}|${value}"
            fi

            # Push to stack for nested keys
            indent_stack+=("$indent")
            prefix_stack+=("$key")
        fi
    done < "$file"
}

# Apply a single value to the config file
apply_value() {
    local file="$1"
    local path="$2"
    local value="$3"

    # Convert path to regex pattern (e.g., "discord.token" -> find "token:" under "discord:")
    local key="${path##*.}"

    # Escape special characters in value for sed
    local escaped_value=$(printf '%s\n' "$value" | sed 's/[&/\]/\\&/g')

    # Use sed to replace the value (matches "key: anything" and replaces with "key: new_value")
    # This preserves the indentation
    sed -i -E "s/^([[:space:]]*)${key}:[[:space:]]*.*/\1${key}: ${escaped_value}/" "$file"
}

echo "Merging configs..." >&2
echo "  Old: $OLD_CONFIG" >&2
echo "  New: $NEW_CONFIG" >&2

# Extract and apply each value
count=0
while IFS='|' read -r path value; do
    [[ -z "$path" ]] && continue

    key="${path##*.}"

    # Check if key exists in new config before applying
    if grep -qE "^[[:space:]]*${key}:" "$TEMP_FILE"; then
        apply_value "$TEMP_FILE" "$path" "$value"
        ((count++)) || true
    fi
done < <(extract_values "$OLD_CONFIG")

echo "Applied $count values from old config" >&2

# Handle user-mappings specially (it's a map, not simple key-value)
if grep -q "^user-mappings:" "$OLD_CONFIG"; then
    echo "Copying user-mappings section..." >&2

    # Extract user-mappings from old config
    USER_MAPPINGS=$(sed -n '/^user-mappings:/,/^[a-z]/p' "$OLD_CONFIG" | head -n -1)

    # Replace in new config
    sed -i '/^user-mappings:/,/^[a-z]/{/^user-mappings:/!{/^[a-z]/!d}}' "$TEMP_FILE"
    sed -i "s/^user-mappings:.*/${USER_MAPPINGS//$'\n'/\\n}/" "$TEMP_FILE" 2>/dev/null || true
fi

# Output result
if [[ "$OUTPUT" == "/dev/stdout" ]]; then
    cat "$TEMP_FILE"
else
    cp "$TEMP_FILE" "$OUTPUT"
    echo "Output written to: $OUTPUT" >&2
fi

rm -f "$TEMP_FILE"
echo "Done!" >&2
