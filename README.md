# Quiet Feed

Quiet Feed is an Android app that uses an Accessibility Service to detect Instagram and Facebook screens and enforce the short-video controls selected in the app. Its minimum supported version is Android 6.0 (API 23).

## Build the APK

The app is built with Android Gradle Plugin 8.12, Gradle 9.3.1, JDK 17 or newer, Android SDK Platform 36, and OpenSSL for first-time signing setup. The Gradle wrapper downloads Gradle and its dependencies if they are not already installed. On macOS with Homebrew, the script finds the JDK and SDK automatically. On another machine, set `JAVA_HOME` and `ANDROID_HOME` first.

```sh
./build.sh
```

The signed APK is named with its version, such as `dist/QuietFeed-1.5.apk`. The app targets Android 16 and remains installable on Android 6 or newer. On a fresh checkout, the script generates a random signing password in `build/signing.properties` and creates a local signing key at `build/quietfeed.keystore`. Both files are ignored by Git. Back up both files securely: future APKs need the same key and password to install as updates without uninstalling the app.

## Install and enable

Copy the versioned APK to your Android phone, open it, and allow installation from that source when Android asks. The app shows its installed version under the Quiet Feed name. Open Quiet Feed and follow its link to **Settings → Accessibility → Installed apps → Quiet Feed screen filter** to enable the service. Android requires you to enable this access manually.

If Android shows **Restricted setting** instead of allowing the service, open **Settings → Apps → Quiet Feed → More (⋮) → Allow restricted settings**, then return to Accessibility. This step can be required for sideloaded accessibility apps on Android 13 and newer ([Android Help](https://support.google.com/android/answer/12623953)).

For a phone connected by USB with debugging enabled, you can also install the APK using:

```sh
"${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}/platform-tools/adb" install -r dist/QuietFeed-1.5.apk
```

## How it works

The service inspects the on-screen interface of Instagram and Facebook on the device. Opening a detected Reel or Short shows a brief centered notice, then sends the user to the phone's Home screen; Reel previews in the main feeds remain available. Instagram's DM mode keeps the inbox and chats available and permits a Reel opened directly from a chat until the user scrolls, at which point it returns Home. Other Instagram areas display a way to open messages. The app does not request internet access or social-media passwords. Instagram and Facebook can change their interfaces, so a Reel may appear briefly before detection, and an app update may require new detection rules.
