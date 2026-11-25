#!/bin/zsh
set -euo pipefail

if [[ $# -ne 6 ]]; then
  echo "usage: $0 <app> <output-dmg> <jdk-jpackage-jmod> <finder-script-template> <volume-icon> <volume-name>" >&2
  exit 64
fi

app_path=$1
output_dmg=$2
jpackage_jmod=$3
finder_template=$4
volume_icon=$5
volume_name=$6
mount_point="/Volumes/$volume_name"
work_dir=$(mktemp -d /private/tmp/fastremote-signed-dmg.XXXXXX)
read_write_dmg="$work_dir/$volume_name-rw.dmg"
finder_script="$work_dir/dmg-setup.scpt"
attached_device=""

cleanup() {
  if [[ -n "$attached_device" ]]; then
    hdiutil detach "$attached_device" >/dev/null 2>&1 || hdiutil detach -force "$attached_device" >/dev/null 2>&1 || true
  fi
  rm -rf "$work_dir"
}
trap cleanup EXIT

[[ -d "$app_path" ]] || { echo "App image does not exist: $app_path" >&2; exit 65; }
[[ -f "$jpackage_jmod" ]] || { echo "jdk.jpackage.jmod does not exist: $jpackage_jmod" >&2; exit 66; }
[[ -f "$finder_template" ]] || { echo "Finder layout template does not exist: $finder_template" >&2; exit 67; }
[[ -f "$volume_icon" ]] || { echo "Volume icon does not exist: $volume_icon" >&2; exit 68; }
[[ ! -e "$mount_point" ]] || { echo "Unmount the existing $mount_point volume before packaging" >&2; exit 69; }

app_kib=$(du -sk "$app_path" | awk '{print $1}')
image_mib=$(( (app_kib + 1023) / 1024 + 64 ))
mkdir -p "${output_dmg:h}"
rm -f "$output_dmg"

hdiutil create -ov -size "${image_mib}m" -fs HFS+ -volname "$volume_name" "$read_write_dmg" >/dev/null
attach_output=$(hdiutil attach -readwrite -noverify -nobrowse -mountpoint "$mount_point" "$read_write_dmg")
attached_device=$(printf '%s\n' "$attach_output" | awk '/Apple_HFS/ {print $1; exit}')
[[ -n "$attached_device" ]] || { echo "Unable to identify mounted DMG device" >&2; exit 70; }

ditto "$app_path" "$mount_point/${app_path:t}"
mkdir -p "$mount_point/.background"
unzip -p "$jpackage_jmod" classes/jdk/jpackage/internal/resources/background_dmg.tiff \
  > "$mount_point/.background/background.tiff" || [[ $? -eq 1 ]]
[[ -s "$mount_point/.background/background.tiff" ]] || {
  echo "Unable to extract the jpackage DMG background" >&2
  exit 71
}
cp "$volume_icon" "$mount_point/.VolumeIcon.icns"
SetFile -a C "$mount_point"

sed \
  -e "s|DEPLOY_VOLUME_URL|file://$mount_point/|g" \
  -e "s|DEPLOY_VOLUME_PATH|$mount_point|g" \
  -e "s|DEPLOY_INSTALL_LOCATION_DISPLAY_NAME|Applications|g" \
  -e "s|DEPLOY_INSTALL_LOCATION|/Applications|g" \
  -e "s|DEPLOY_BG_FILE|$mount_point/.background/background.tiff|g" \
  -e "s|DEPLOY_TARGET|${app_path:t}|g" \
  "$finder_template" > "$finder_script"
osascript "$finder_script"
sync

hdiutil detach "$attached_device" >/dev/null
attached_device=""
hdiutil convert "$read_write_dmg" -format UDZO -imagekey zlib-level=9 -o "$output_dmg" >/dev/null
