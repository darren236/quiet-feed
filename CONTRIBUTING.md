# Contributing

Thanks for helping improve QuietFeed. Small changes with a reproducible example are easiest to review.

## Development setup

Follow the [build requirements](README.md#requirements): JDK 17, Android SDK Platform 36, Build Tools 36.0.0, and Platform Tools. Open `gradle-app/` in Android Studio. Private release signing files are not needed for development.

Run the same checks as CI before submitting a change:

```sh
./gradle-app/gradlew -p gradle-app --no-daemon :app:check :app:assembleDebug
```

For a focused rules check:

```sh
./gradle-app/gradlew -p gradle-app --no-daemon :app:screenRulesTest
```

## Detection changes

- Describe the screen that was allowed or blocked incorrectly, the selected QuietFeed mode, and the steps to reach it.
- Add a minimal synthetic accessibility-node fixture to `tests/com/darren/quietfeed/ScreenRulesTest.java` when the change concerns classification or permission state. Include a nearby screen that must still behave differently, such as a feed preview versus an opened viewer or comment scrolling versus video paging.
- Keep screen rules separate from Android window and event handling where practical.
- State which cases were tested on a device and which were tested only with fixtures. Do not claim support for a live app layout based on a synthetic fixture alone.

## Device checklist

Use test accounts and record the phone model, Android version, social-app version, interface language, and QuietFeed mode. Before a release or a change to event handling, check:

- [ ] **Instagram Block Reels:** Home previews remain visible; the Reels tab and an opened Reel show the notice and return Home.
- [ ] **Instagram DM mode:** inbox and individual chats stay available; Open messages reaches DMs from other screens.
- [ ] **Instagram shared Reel:** opening a video from a chat allows it; opening, scrolling, and closing comments keeps that same Reel available; the first video swipe returns Home.
- [ ] **Instagram origin:** an unrelated Reel reached through feed navigation does not inherit a chat allowance.
- [ ] **Facebook:** feed previews remain visible; opened Reels return Home; detected comments and comment scrolling remain available.
- [ ] **TikTok Block videos:** feeds and opened videos, including chat videos, return Home; inbox, chats, and detected comments remain available.
- [ ] **TikTok chat mode:** inbox and chats remain available; a chat video is allowed until the first video swipe; comment scrolling does not count as that swipe; the Friends feed shows the gate.
- [ ] **Transitions:** Back, keyboards, comment-sheet animations, switching apps, and turning a mode Off leave no stale gate or delayed Home action.
- [ ] **Service:** disabling accessibility removes protection; enabling it restores the selected modes. On a supported Samsung phone, check the documented hardware shortcut separately from emulator tests.

## Bug reports and privacy

Use the [bug report form](https://github.com/darren236/quiet-feed/issues/new?template=bug_report.yml). Include steps, expected and actual behavior, and version information. Screenshots are optional. Remove names, usernames, messages, notifications, account details, and unrelated content before sharing a screenshot or UI dump. Prefer a minimal invented fixture to a raw accessibility dump.

For vulnerabilities or accidental exposure of private data, follow [SECURITY.md](SECURITY.md) instead of posting details publicly. Never commit signing keys, passwords, APKs, tokens, or local SDK configuration.

## Pull requests

Keep the change focused, explain its effect, and list the checks performed and any remaining device-testing gaps. App changes need a new version name and a higher version code when released; maintainers handle release signing and publishing. Contributions are distributed under the repository's [MIT License](LICENSE).
