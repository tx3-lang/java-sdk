#!/usr/bin/env bash
#
# Renders tx3c's built-in `java-client` template against the canonical transfer
# and complex fixtures, smoke-checks the generated surface, and compiles each
# result against the SDK in this checkout.
#
# The template ships inside tx3c and pins the published `land.tx3:tx3-sdk`
# version. Building against this checkout installed as a snapshot means an SDK
# change that would break the client the current tx3c generates fails here,
# before the SDK is released.
#
# Requires `tx3c` (0.25.0 or later, which ships the `java-client` template) on
# PATH and a JDK 21; Maven comes from this checkout's wrapper.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
m2="${CODEGEN_M2_REPO:-$work/m2}"
snapshot="0.15.0-SNAPSHOT"
mvnw="$repo_root/mvnw"

"$mvnw" -B -ntp -f "$repo_root/pom.xml" -Drevision="$snapshot" \
  -Dmaven.repo.local="$m2" -DskipTests install

for fixture in transfer complex; do
  gen="$work/$fixture"
  tx3c codegen \
    --tii "$repo_root/src/test/resources/fixtures/$fixture.tii" \
    --template java-client \
    --output "$gen"

  test -f "$gen/pom.xml" || { echo "missing generated pom.xml ($fixture)"; exit 1; }
  client="$(find "$gen/src/main/java/land/tx3/generated" -name '*Client.java' | head -n1)"
  [[ -n "$client" ]] || { echo "missing generated *Client.java ($fixture)"; exit 1; }

  # Public surface every generated client carries: protocol identity, the
  # profile selector, and per-transaction TIR constants and Params records.
  for pattern in \
    'public static final String PROTOCOL_NAME' \
    'public static final String PROTOCOL_VERSION' \
    'public static final String TARGET_TII_VERSION = "v1beta0"' \
    'public enum Profile' \
    'Tx3ClientBuilder.fromParts('; do
    grep -qF "$pattern" "$client" || { echo "generated $fixture client missing: $pattern"; exit 1; }
  done
  grep -qE 'public static final land\.tx3\.sdk\.TirEnvelope [A-Z0-9_]+_TIR' "$client" \
    || { echo "generated $fixture client has no TIR constant"; exit 1; }
  grep -qE 'public record [A-Za-z0-9]+Params\(' "$client" \
    || { echo "generated $fixture client has no Params record"; exit 1; }

  # Typed wrappers only: no generic party setter and no builder-only settings.
  if grep -qE 'public [A-Za-z0-9]+Client with(Party|Profile|EnvValue|Header)\(' "$client"; then
    echo "generated $fixture client exposes an untyped or builder-only setter"
    exit 1
  fi

  echo "--- java-client/$fixture"
  "$mvnw" -B -ntp -f "$gen/pom.xml" -Dmaven.repo.local="$m2" \
    -Dtx3.sdk.version="$snapshot" verify
done

# The transfer fixture's typed surface, exactly.
transfer_client="$(find "$work/transfer/src/main/java/land/tx3/generated" -name '*Client.java' | head -n1)"
for pattern in \
  'public record TransferParams(java.math.BigInteger quantity)' \
  'PREPROD("preprod")' \
  'TRANSFER_TIR' \
  'Client withSender(land.tx3.sdk.Party party)' \
  'Client withReceiver(land.tx3.sdk.Party party)' \
  'Client withMiddleman(land.tx3.sdk.Party party)' \
  'public land.tx3.sdk.TxBuilder transfer(TransferParams args)'; do
  grep -qF "$pattern" "$transfer_client" || { echo "generated transfer client missing: $pattern"; exit 1; }
done
