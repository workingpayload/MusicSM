#!/bin/bash
# MusicSM Desktop for macOS, installed with one command:
#
#   curl -fsSL https://music-sm.vercel.app/install.sh | bash
#
# Downloads the newest release, puts MusicSM Desktop in Applications and opens it. The app isn't
# signed by Apple yet, so a DMG downloaded in a browser gets the "can't be opened" prompt; files
# fetched with curl aren't marked as downloaded from the internet, so this way it just opens.
#
# Everything runs inside main(), called on the last line, so a half-downloaded script does nothing.

set -euo pipefail

main() {
  local app_name="MusicSM Desktop"
  local site="https://music-sm.vercel.app"

  if [ "$(uname -s)" != "Darwin" ]; then
    fail "This installer is for macOS. For Windows, download the installer from $site."
  fi
  # hw.optional.arm64 is 1 on Apple silicon, even when this shell runs under Rosetta.
  if [ "$(sysctl -n hw.optional.arm64 2>/dev/null || echo 0)" != "1" ]; then
    fail "MusicSM Desktop needs a Mac with Apple silicon (M1 or newer)."
  fi

  local tmp
  tmp="$(mktemp -d "${TMPDIR:-/tmp}/musicsm.XXXXXX")"
  local dmg="$tmp/MusicSM-Desktop.dmg"
  local mnt="$tmp/mount"
  # Unmount and delete the download however the script ends.
  trap 'hdiutil detach "'"$mnt"'" -quiet >/dev/null 2>&1 || true; rm -rf "'"$tmp"'"' EXIT

  say "Downloading $app_name..."
  # The same link as the site's "Download for Mac" button: the newest DMG, counted like a click.
  curl -fL --progress-bar "$site/api/download?desktop=mac" -o "$dmg" \
    || fail "The download failed. Check your connection and try again."

  mkdir -p "$mnt"
  hdiutil attach -nobrowse -readonly -noautoopen -mountpoint "$mnt" "$dmg" >/dev/null 2>&1 \
    || fail "That wasn't a usable disk image. Try again later, or download it from $site."

  local src=""
  for candidate in "$mnt"/*.app; do
    if [ -d "$candidate" ]; then
      src="$candidate"
      break
    fi
  done
  [ -n "$src" ] || fail "The disk image had no app in it."

  # /Applications is writable for admin accounts; a standard account gets its own Applications.
  local dest_dir="/Applications"
  if [ ! -w "$dest_dir" ]; then
    dest_dir="$HOME/Applications"
    mkdir -p "$dest_dir"
  fi
  local dest="$dest_dir/$app_name.app"
  local staging="$dest_dir/.$app_name.app.installing"

  if pgrep -xq "$app_name"; then
    say "Closing $app_name..."
    osascript -e "quit app \"$app_name\"" >/dev/null 2>&1 || true
    sleep 2
  fi

  say "Installing to $dest_dir..."
  # Copied next to the old version first, so a failed copy leaves the installed app alone.
  rm -rf "$staging"
  ditto "$src" "$staging" || { rm -rf "$staging"; fail "Couldn't copy the app into $dest_dir."; }
  rm -rf "$dest"
  mv "$staging" "$dest"
  xattr -dr com.apple.quarantine "$dest" 2>/dev/null || true

  say "Done. Opening $app_name."
  open "$dest"
}

say() {
  printf '%s\n' "$*"
}

fail() {
  printf 'Error: %s\n' "$*" >&2
  exit 1
}

main
