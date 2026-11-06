#!/bin/bash
# Creates the release signing key OUTSIDE the repository. The certificate name comes from app.properties:
#   CN=<appSlug>-dev, O=<appNameEn>   (anyone with the APK can read it, so never a real name)
# Usage: scripts/create-release-key.sh            (keytool asks for the passwords)
#        KEY_DIR=/other/dir scripts/create-release-key.sh
set -euo pipefail
cd "$(dirname "$0")/.."

prop() { grep -E "^$1=" app.properties | cut -d= -f2- | tr -d '[:space:]'; }
slug=$(prop appSlug)
org=$(prop appNameEn)
[ -n "$slug" ] || { echo "app.properties: appSlug is empty" >&2; exit 1; }
[ -n "$org" ] || { echo "app.properties: appNameEn is empty" >&2; exit 1; }

dir="${KEY_DIR:-$HOME/Keys/$slug}"
store="$dir/$slug-release.jks"
[ -e "$store" ] && { echo "Already exists, not overwritten: $store" >&2; exit 1; }
case "$(cd "$(dirname "$dir")" 2>/dev/null && pwd)/" in
  "$(pwd)/"*) echo "KEY_DIR must be outside the repository" >&2; exit 1 ;;
esac

mkdir -p "$dir"
"${JAVA_HOME:+$JAVA_HOME/bin/}keytool" -genkeypair -v -keystore "$store" -alias release \
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=$slug-dev, O=$org" "$@"

cat <<EOT

Created: $store
Add these lines to local.properties (git-ignored) with the passwords you just typed:
release.storeFile=$store
release.storePassword=
release.keyAlias=release
release.keyPassword=
Keep one backup copy of the .jks somewhere other than this computer (a lost key cannot be recreated).
EOT
