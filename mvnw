#!/bin/sh
# Minimal Maven bootstrap for Linux/macOS (Ubuntu 24.04): Maven from PATH, otherwise downloads Apache Maven once into .mvn/
set -e
if command -v mvn >/dev/null 2>&1; then exec mvn "$@"; fi
DIR="$(cd "$(dirname "$0")" && pwd)"
VER=3.9.9
HOME_DIR="$DIR/.mvn/apache-maven-$VER"
if [ ! -x "$HOME_DIR/bin/mvn" ]; then
  echo "Downloading Apache Maven $VER ..."
  mkdir -p "$DIR/.mvn"
  curl -fsSL "https://archive.apache.org/dist/maven/maven-3/$VER/binaries/apache-maven-$VER-bin.tar.gz" | tar -xz -C "$DIR/.mvn"
fi
exec "$HOME_DIR/bin/mvn" "$@"
