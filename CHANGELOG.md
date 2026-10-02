# Changelog

Published APKs and source tags are available in [GitHub Releases](https://github.com/darren236/quiet-feed/releases).

## 1.14

- Added real Android emulator screenshots and a clearer project overview.
- Added contributor guidance, a device regression checklist, a bug report form, and a security reporting policy.
- Made Android Build Tools 36.0.0 requirements explicit in the build setup.
- Fixed shared-video swipe detection for identified TikTok video pagers implemented as lists while preserving comment scrolling.
- Refreshed and pinned CI actions for the current action runtime.
- Removed an unused accessibility gesture capability.

## 1.13

- Added Samsung Side/Power + Volume up shortcut instructions in the app and README for turning accessibility off before banking and back on afterward.

## 1.12

- Preserved Instagram's shared-Reel allowance while opening, reading, scrolling, and closing comments, including incomplete accessibility trees.

## 1.11

- Improved Instagram DM inbox recognition and shared-Reel transitions when click information is missing.
- Improved Facebook and TikTok comment detection.
- Cancelled pending exits when comments open or the user returns to a chat.

## 1.10

- Improved counted comment headings and Instagram viewer controls.
- Tightened Instagram shared-video eligibility.
- Connected regression fixtures to Gradle checks and release builds, and added CI.
- Allowed debug development without release signing files.
- Added the MIT license.

Earlier changes are recorded in [Git history](https://github.com/darren236/quiet-feed/commits/) and the release notes.
