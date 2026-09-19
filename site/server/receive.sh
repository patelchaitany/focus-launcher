#!/bin/bash
# Runs on the web server as the FORCED COMMAND of the CI upload key (site/setup-ci-publishing.sh
# installs it). Whatever the holder of that key asks ssh to do, this is what runs instead: it
# reads one tar archive from stdin and installs the files in it into the Focus web directory.
# It gives no shell, reads no arguments, and touches nothing outside that directory.
#
# Accepted: plain files with plain names, directly in the archive's root, with one of the
# extensions a static site and an APK download need. Anything else (a path with a slash, a
# symlink, a device, an unknown extension, an oversized upload) refuses the whole upload.
set -euo pipefail
umask 022
export LC_ALL=C

DEST="${FOCUS_WEB_DIR:-/var/www/focusapp}"
MAX_BYTES=$((40 * 1024 * 1024))
ALLOWED='^[A-Za-z0-9][A-Za-z0-9._-]*\.(html|css|svg|png|txt|xml|apk|sha256)$'

refuse() { echo "refused: $*" >&2; exit 1; }

[ -d "$DEST" ] && [ -w "$DEST" ] || refuse "the web directory is missing or not writable"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# One byte more than the limit, so "too big" can be told from "exactly the limit".
head -c $((MAX_BYTES + 1)) > "$work/upload.tar"
size=$(stat -c %s "$work/upload.tar")
[ "$size" -gt 0 ] || refuse "empty upload"
[ "$size" -le "$MAX_BYTES" ] || refuse "upload larger than $MAX_BYTES bytes"

listing=$(tar -tvf "$work/upload.tar" 2>/dev/null) || refuse "not a tar archive"
names=()
while read -r mode _owner _size _date _time name rest; do
  [ -n "$mode" ] || continue
  [ -z "$rest" ] || refuse "an entry is not a plain file ($name ...)"
  case "$mode" in
    d*) [ "$name" = "./" ] || refuse "directories are not accepted ($name)"; continue ;;
    -*) ;;
    *)  refuse "only plain files are accepted ($name is '${mode:0:1}')" ;;
  esac
  name=${name#./}
  [[ "$name" =~ $ALLOWED ]] || refuse "file name not accepted ($name)"
  names+=("$name")
done <<< "$listing"

[ "${#names[@]}" -gt 0 ] || refuse "no files in the upload"
[ "${#names[@]}" -le 64 ] || refuse "too many files"
printf '%s\n' "${names[@]}" | grep -qx 'index.html' || refuse "index.html is missing"
printf '%s\n' "${names[@]}" | grep -q '\.apk$' || refuse "no APK in the upload"
[ "$(printf '%s\n' "${names[@]}" | sort | uniq -d | wc -l)" -eq 0 ] || refuse "a file name appears twice"

mkdir "$work/x"
tar -xf "$work/upload.tar" -C "$work/x" --no-same-owner --no-same-permissions
for name in "${names[@]}"; do
  [ -f "$work/x/$name" ] && [ ! -L "$work/x/$name" ] || refuse "$name did not unpack as a plain file"
done
for apk in "$work"/x/*.apk; do
  [ "$(stat -c %s "$apk")" -ge 300000 ] || refuse "$(basename "$apk") is too small to be the app"
  # An APK is a zip: it starts with "PK".
  [ "$(head -c 2 "$apk")" = "PK" ] || refuse "$(basename "$apk") is not an APK"
done

# Each file is moved into place in one step. The page goes last, so it never links to an APK
# that is not there yet. Older APKs stay where they are: links to them keep working.
install_one() { install -m 644 "$work/x/$1" "$DEST/.$1.incoming" && mv -f "$DEST/.$1.incoming" "$DEST/$1"; }
for name in "${names[@]}"; do [ "$name" = "index.html" ] || install_one "$name"; done
install_one index.html

echo "installed ${#names[@]} files: $(printf '%s ' "${names[@]}")"
