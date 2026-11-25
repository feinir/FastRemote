#!/bin/zsh
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <jpackage-input-directory> <mac-application-signing-identity>" >&2
  exit 64
fi

input_directory=$1
signing_identity=$2

[[ -d "$input_directory" ]] || {
  echo "jpackage input directory does not exist: $input_directory" >&2
  exit 65
}
case "$signing_identity" in
  "Developer ID Application:"*) store=false ;;
  "3rd Party Mac Developer Application:"*) store=true ;;
  *) echo "A macOS application signing identity is required" >&2; exit 66 ;;
esac

for archive in "$input_directory"/*.jar(N); do
  case "${archive:t}" in
    jna-[0-9]*.jar|pty4j-[0-9]*.jar|sqlite-jdbc-[0-9]*.jar) ;;
    *) continue ;;
  esac
  archive_entries=$(unzip -Z1 "$archive")
  if ! grep -Eq '\.(dylib|jnilib)$|/darwin/[^/]*(helper|spawn)[^/]*$' <<<"$archive_entries"; then
    continue
  fi

  work_directory=$(mktemp -d /private/tmp/fastremote-jar-signing.XXXXXX)
  replacement_archive="$work_directory/replacement.jar"
  cleanup() {
    rm -rf "$work_directory"
  }
  trap cleanup EXIT

  if $store; then
    cat > "$work_directory/helper.entitlements" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "https://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
<key>com.apple.security.app-sandbox</key><true/>
<key>com.apple.security.inherit</key><true/>
</dict></plist>
PLIST
  fi

  unzip -qq "$archive" -d "$work_directory/contents"
  signed_count=0
  while IFS= read -r -d '' candidate; do
    binary_type=$(file -b "$candidate")
    if [[ "$binary_type" == *Mach-O* ]]; then
      chmod u+w "$candidate"
      if $store; then
        sign_options=(--timestamp=none)
        if [[ "$binary_type" == *executable* ]]; then
          sign_options+=(--entitlements "$work_directory/helper.entitlements")
        fi
      else
        sign_options=(--options runtime --timestamp)
      fi
      codesign --force --sign "$signing_identity" "${sign_options[@]}" "$candidate"
      codesign --verify --strict --verbose=2 "$candidate"
      if ! $store; then
        signature_details=$(codesign --display --verbose=4 "$candidate" 2>&1)
        grep -q 'flags=.*runtime' <<<"$signature_details"
        grep -q '^Timestamp=' <<<"$signature_details"
      fi
      signed_count=$((signed_count + 1))
    fi
  done < <(find "$work_directory/contents" -type f -print0)

  if [[ $signed_count -gt 0 ]]; then
    (
      cd "$work_directory/contents"
      zip -X -q -r "$replacement_archive" .
    )
    unzip -tq "$replacement_archive" >/dev/null
    cp -f "$replacement_archive" "$archive"
    echo "Signed $signed_count embedded macOS binaries in ${archive:t}"
  fi

  cleanup
  trap - EXIT
done
