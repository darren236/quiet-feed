#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/homebrew/share/android-commandlinetools}}"
JDK="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
AAPT="$SDK/build-tools/36.0.0/aapt"
KEYSTORE="$ROOT/build/quietfeed.keystore"
SIGNING_FILE="$ROOT/build/signing.properties"

if [[ ! -f "$SDK/platforms/android-36/android.jar" ]]; then
    echo 'Android SDK Platform 36 is required. Set ANDROID_HOME to its SDK directory.' >&2
    exit 1
fi
if [[ ! -x "$AAPT" ]]; then
    echo 'Android SDK Build Tools 36.0.0 is required. Install it with sdkmanager "build-tools;36.0.0" in the SDK set by ANDROID_HOME.' >&2
    exit 1
fi
if [[ ! -x "$JDK/bin/keytool" ]]; then
    echo 'JDK 17 or newer is required. Set JAVA_HOME to its directory.' >&2
    exit 1
fi

mkdir -p "$ROOT/build" "$ROOT/dist"
if [[ ! -f "$SIGNING_FILE" ]]; then
    if [[ -f "$KEYSTORE" ]]; then
        echo 'Existing signing key found, but build/signing.properties is missing. Restore the matching signing password before building.' >&2
        exit 1
    fi
    if ! command -v openssl >/dev/null 2>&1; then
        echo 'OpenSSL is required to generate a signing password.' >&2
        exit 1
    fi
    GENERATED_PASSWORD="$(openssl rand -hex 32)"
    if [[ ! "$GENERATED_PASSWORD" =~ ^[0-9a-f]{64}$ ]]; then
        echo 'Could not generate a signing password.' >&2
        exit 1
    fi
    (umask 077; printf 'password=%s\n' "$GENERATED_PASSWORD" > "$SIGNING_FILE")
fi
SIGNING_PASSWORD="$(sed -n 's/^password=//p' "$SIGNING_FILE")"
if [[ ! "$SIGNING_PASSWORD" =~ ^[A-Za-z0-9]+$ ]]; then
    echo 'build/signing.properties must contain one password= line with an alphanumeric value.' >&2
    exit 1
fi
chmod 600 "$SIGNING_FILE"
export QUIETFEED_SIGNING_PASSWORD="$SIGNING_PASSWORD"
if [[ ! -f "$KEYSTORE" ]]; then
    "$JDK/bin/keytool" -genkeypair -noprompt \
        -keystore "$KEYSTORE" -alias quietfeed \
        -storepass:env QUIETFEED_SIGNING_PASSWORD -keypass:env QUIETFEED_SIGNING_PASSWORD \
        -dname 'CN=Quiet Feed Local Build,O=Quiet Feed,C=SG' \
        -keyalg RSA -keysize 2048 -validity 10000
fi
chmod 600 "$KEYSTORE"

export ANDROID_HOME="$SDK"
export JAVA_HOME="$JDK"
"$ROOT/gradle-app/gradlew" -p "$ROOT/gradle-app" --no-daemon :app:assembleRelease

BUILT_APK="$ROOT/gradle-app/app/build/outputs/apk/release/app-release.apk"
VERSION_NAME="$("$AAPT" dump badging "$BUILT_APK" \
    | sed -n "1s/.*versionName='\([^']*\)'.*/\1/p")"
if [[ -z "$VERSION_NAME" ]]; then
    echo 'Could not read the built APK version.' >&2
    exit 1
fi
OUTPUT_APK="$ROOT/dist/QuietFeed-$VERSION_NAME.apk"
cp "$BUILT_APK" "$OUTPUT_APK"
rm -f "$ROOT/dist/QuietFeed.apk"
for previous in "$ROOT"/dist/QuietFeed-*.apk; do
    if [[ "$previous" != "$OUTPUT_APK" ]]; then rm -f "$previous"; fi
done

echo "APK ready: $OUTPUT_APK"
