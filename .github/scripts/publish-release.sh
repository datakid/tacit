#!/usr/bin/env bash
set -euo pipefail

notes="$(ls docs/release-notes/*.md | sort -V | tail -1)"
tag="$(basename "$notes" .md)"
repo="${GITHUB_REPOSITORY:?}"
sha="${GITHUB_SHA:?}"

ids="$(gh api "repos/${repo}/releases" --paginate --jq ".[] | select(.tag_name == \"${tag}\") | .id")"
id="${ids%%$'\n'*}"
if [ -n "$id" ]; then
  echo "release ${tag} exists (id ${id}), refreshing its notes only"
  gh api -X PATCH "repos/${repo}/releases/${id}" -F "body=@${notes}" > /dev/null
  exit 0
fi

case "$tag" in
  *-*)
    apk="tacit-${tag}-debug.apk"
    cp "$(ls app/build/outputs/apk/debug/*.apk | head -1)" "$apk"
    gh release create "$tag" "$apk" --target "$sha" --prerelease --title "Tacit ${tag}" --notes-file "$notes"
    ;;
  *)
    dir="release-assets"
    unsigned="tacit-${tag}-unsigned.apk"
    rm -rf "$dir"
    mkdir -p "$dir"
    cp "$(ls app/build/outputs/apk/release/*.apk | head -1)" "${dir}/${unsigned}"
    (cd "$dir" && sha256sum "$unsigned" > SHA256SUMS)
    gh release create "$tag" "${dir}/${unsigned}" "${dir}/SHA256SUMS" --draft --target "$sha" --title "Tacit ${tag}" --notes-file "$notes"
    echo "draft ${tag} created with the unsigned APK; sign it outside GitHub and upload the signed APK (docs/SIGNING.md)"
    ;;
esac
