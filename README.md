# QuietFeed: Shorts Blocker

QuietFeed: Shorts Blocker is an Android app that uses an Accessibility Service to detect Instagram, Facebook, and TikTok screens and enforce the short-video controls selected in the app. Its minimum supported version is Android 6.0 (API 23).

## Project layout and version control

This folder is the single Git checkout for [darren236/quiet-feed](https://github.com/darren236/quiet-feed). The authoritative source files are `src/`, `res/`, and `AndroidManifest.xml` at the project root. `gradle-app/` contains the build configuration and points to those same files; it is not a second app copy.

Use `dist/QuietFeed-<version>.apk` for installation. Gradle also produces an identical intermediate APK under `gradle-app/app/build/outputs/apk/release/`. Build outputs and local signing files are ignored by Git. Do not commit signing keys or passwords.

Commit source changes to Git and use a new `versionName` and higher `versionCode` for each APK release. Published releases use Git tags such as `v1.7` and store APKs in GitHub Releases. Keep published tags and assets unchanged; publish a new version for later app changes. Use Git history and release tags to recover earlier versions instead of keeping numbered project copies.

## Build the APK

The app is built with Android Gradle Plugin 8.12, Gradle 9.3.1, JDK 17 or newer, Android SDK Platform 36, and OpenSSL for first-time signing setup. The Gradle wrapper downloads Gradle and its dependencies if they are not already installed. On macOS with Homebrew, the script finds the JDK and SDK automatically. On another machine, set `JAVA_HOME` and `ANDROID_HOME` first.

```sh
./build.sh
```

The signed APK is named with its version, such as `dist/QuietFeed-1.10.apk`. The app targets Android 16 and remains installable on Android 6 or newer. On a fresh checkout, the script generates a random signing password in `build/signing.properties` and creates a local signing key at `build/quietfeed.keystore`. Both files are ignored by Git. Back up both files securely: future APKs need the same key and password to install as updates without uninstalling the app.

## Development and checks

Open `gradle-app/` in Android Studio. Debug builds and tests do not require private release signing files:

```sh
./gradle-app/gradlew -p gradle-app :app:check :app:assembleDebug
```

The `screenRulesTest` task runs the plain Java regression suite. It is included in `check`, the unit-test tasks, and every release build. GitHub Actions runs the checks and a debug build on pushes and pull requests without release secrets. A release built directly with Gradle is unsigned when local signing files are absent; use `./build.sh` to prepare signing and produce an installable release APK.

The tests use synthetic accessibility-node fixtures. They cover screen classification and shared-Reel eligibility, but do not substitute for testing event timing, overlays, and navigation on a phone. Before releasing, check each supported app's feed previews, opened videos, inbox, chat previews, shared videos, comments, comment scrolling, and the first swipe after closing comments.

## Install and enable

Copy the versioned APK to your Android phone, open it, and allow installation from that source when Android asks. The app shows its installed version under the QuietFeed: Shorts Blocker name. Open QuietFeed: Shorts Blocker and follow its link to **Settings → Accessibility → Installed apps → QuietFeed: Shorts Blocker screen filter** to enable the service. Android requires you to enable this access manually.

If Android shows **Restricted setting** instead of allowing the service, open **Settings → Apps → QuietFeed: Shorts Blocker → More (⋮) → Allow restricted settings**, then return to Accessibility. This step can be required for sideloaded accessibility apps on Android 13 and newer ([Android Help](https://support.google.com/android/answer/12623953)).

For a phone connected by USB with debugging enabled, you can also install the APK using:

```sh
"${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}/platform-tools/adb" install -r dist/QuietFeed-1.10.apk
```

## How it works

The service inspects the on-screen interface of Instagram, Facebook, and TikTok on the device. Opening a detected blocked Instagram or Facebook Reel shows a brief centered notice, then sends the user to the phone's Home screen. Reel previews in those feeds remain available. Instagram's DM mode keeps the inbox and chats available and permits a Reel opened directly from a chat until the user scrolls, at which point it returns Home. Other Instagram areas display an Open messages button that selects a detected top Messages icon or bottom DM tab.

TikTok's **Block videos** option shows a centered notice and returns Home when a video feed or opened video is detected, including chat videos. Inbox and chats remain available.

TikTok's **Chats + shared videos** mode is off by default. When enabled, it covers video feeds with a gate that opens the inbox, keeps chats available, and permits a video opened directly from a chat until the user swipes to another video. That swipe shows the centered notice and returns Home. The TikTok Friends feed is separate from chats and is blocked in this mode. QuietFeed: Shorts Blocker does not check who sent a message or access your TikTok account; it uses the on-screen path from a chat to decide whether a video is allowed.

The app does not request internet access or social-media passwords. Social apps can change their interfaces, so a blocked video may appear briefly before detection, and an app update may require new detection rules.

Detected comment panels are allowed in all three apps, including reading, writing, and scrolling comments. Closing comments returns to the underlying screen and its normal blocking rules.

## Limitations and privacy

Detection currently relies on English accessibility labels, view identifiers, and screen geometry. Other languages, interface experiments, and app updates can change the results. Shared-video modes identify a media click from a chat; they do not verify the sender's identity or friendship status. If Instagram does not expose that click, the shared Reel is blocked rather than granting access based only on the previous chat screen.

Accessibility access lets the service inspect window contents. QuietFeed processes supported apps' visible UI locally and does not store message contents, send analytics, or request internet permission. Some banking apps reject enabled accessibility services; disable QuietFeed's service in Android settings when required.

## License

QuietFeed is available under the [MIT License](LICENSE).
