#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: tools/sign-release.sh <unsigned.apk> <keystore> <key-alias> [output.apk]"
  echo "passwords: typed when asked, or taken from TACIT_KS_PASS (and TACIT_KEY_PASS if the key has its own)"
  exit 2
}

[ "$#" -ge 3 ] || usage
input="$1"
keystore="$2"
alias_name="$3"

[ -f "$input" ] || { echo "no such file: $input" >&2; exit 1; }
[ -f "$keystore" ] || { echo "no such keystore: $keystore" >&2; exit 1; }

if [ "$#" -ge 4 ]; then
  output="$4"
elif [[ "$input" == *-unsigned.apk ]]; then
  output="${input%-unsigned.apk}.apk"
else
  output="${input%.apk}-signed.apk"
fi
[ "$output" != "$input" ] || { echo "output would overwrite the input" >&2; exit 1; }

tool() {
  local name="$1"
  if command -v "$name" > /dev/null 2>&1; then command -v "$name"; return; fi
  local home="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  [ -n "$home" ] && [ -d "$home/build-tools" ] || return 1
  local found
  found="$(ls -d "$home"/build-tools/*/ 2>/dev/null | sort -V | tail -1)"
  [ -n "$found" ] || return 1
  for candidate in "${found}${name}" "${found}${name}.bat" "${found}${name}.exe"; do
    [ -x "$candidate" ] && { echo "$candidate"; return; }
  done
  return 1
}

zipalign="$(tool zipalign)" || { echo "zipalign not found: install Android build-tools or set ANDROID_HOME" >&2; exit 1; }
apksigner="$(tool apksigner)" || { echo "apksigner not found: install Android build-tools or set ANDROID_HOME" >&2; exit 1; }

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
aligned="$work/aligned.apk"

"$zipalign" -f -p 4 "$input" "$aligned"
"$zipalign" -c -p 4 "$aligned"

args=(sign --ks "$keystore" --ks-key-alias "$alias_name" --v4-signing-enabled false --out "$output")
[ -z "${TACIT_KS_PASS:-}" ] || args+=(--ks-pass env:TACIT_KS_PASS)
[ -z "${TACIT_KEY_PASS:-}" ] || args+=(--key-pass env:TACIT_KEY_PASS)
"$apksigner" "${args[@]}" "$aligned"

"$apksigner" verify --verbose --print-certs "$output"
echo
echo "signed: $output"
sha256sum "$output" | tee "${output}.sha256"
echo "Check that the certificate SHA-256 above is the fingerprint of your own key before uploading."
