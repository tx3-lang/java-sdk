#!/bin/sh

set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
properties="$project_dir/.mvn/wrapper/maven-wrapper.properties"
wrapper_jar="$project_dir/.mvn/wrapper/maven-wrapper.jar"
wrapper_url=$(sed -n 's/^wrapperUrl=//p' "$properties")
wrapper_sha=$(sed -n 's/^wrapperSha256Sum=//p' "$properties")

if [ -z "${JAVA_HOME:-}" ]; then
  if [ -x /usr/libexec/java_home ]; then
    JAVA_HOME=$(/usr/libexec/java_home)
    export JAVA_HOME
  elif command -v javac >/dev/null 2>&1; then
    JAVA_HOME=$(dirname "$(dirname "$(command -v javac)")")
    export JAVA_HOME
  fi
fi

if [ ! -x "${JAVA_HOME:-}/bin/java" ]; then
  echo "Error: JAVA_HOME must point to a JDK." >&2
  exit 1
fi

if [ ! -f "$wrapper_jar" ]; then
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$wrapper_url" -o "$wrapper_jar"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$wrapper_url" -O "$wrapper_jar"
  else
    echo "Error: curl or wget is required to download Maven Wrapper." >&2
    exit 1
  fi
fi

if command -v sha256sum >/dev/null 2>&1; then
  actual_sha=$(sha256sum "$wrapper_jar" | awk '{print $1}')
else
  actual_sha=$(shasum -a 256 "$wrapper_jar" | awk '{print $1}')
fi
if [ "$actual_sha" != "$wrapper_sha" ]; then
  echo "Error: Maven Wrapper checksum mismatch." >&2
  exit 1
fi

exec "$JAVA_HOME/bin/java" ${MAVEN_OPTS:-} \
  -classpath "$wrapper_jar" \
  "-Dmaven.multiModuleProjectDirectory=$project_dir" \
  org.apache.maven.wrapper.MavenWrapperMain "$@"
