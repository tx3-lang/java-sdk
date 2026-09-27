#!/usr/bin/env bash
set -euo pipefail

tag="${1:-}"
if [[ ! "$tag" =~ ^v([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
  echo "Release tag must match vMAJOR.MINOR.PATCH: $tag" >&2
  exit 1
fi

tag_version="${tag#v}"
project_version="$(./mvnw -B -ntp help:evaluate -Dexpression=project.version -DforceStdout -q)"
if [[ "$tag_version" != "$project_version" ]]; then
  echo "Tag version ($tag_version) does not match pom.xml version ($project_version)." >&2
  exit 1
fi

train="$(tr -d '[:space:]' < .github/release-train)"
tag_train="${BASH_REMATCH[1]}.${BASH_REMATCH[2]}"
if [[ "$tag_train" != "$train" ]]; then
  echo "Tag MAJOR.MINOR ($tag_train) does not match the fleet release train ($train)." >&2
  exit 1
fi

echo "Validated Java SDK release $tag_version on fleet train $train."
