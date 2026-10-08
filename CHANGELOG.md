# Changelog

Published APKs and source tags are available in [GitHub Releases](https://github.com/darren236/quiet-feed/releases).

## 1.16

- Preserved TikTok chat origin through delayed media taps, idle chats, and slower shared-video loading.
- Stopped stationary video-pager and opening events from counting as a swipe; real movement or a changed page still blocks the next video.
- Kept shared-video permission through brief partial viewer trees and sparse comment sheets, including closing comments back to the same video.
- Cleared TikTok permission on feed/profile navigation, app changes, and mode changes; For You, Following, and Friends feeds remain blocked in chat mode.
- Added regression sequences for chat opening, loading, comments, and paging. Real-device verification is still required for TikTok layouts and event timing.

## 1.15

- Preserved Instagram chat origin when a Reel tap arrives after the viewer starts opening, including an idle chat.
- Extended confirmed chat-media loading grace and kept permission through brief incomplete viewer snapshots.
- Allowed a confirmed chat tap to open a dedicated Reel viewer when its Back control is absent from the accessibility tree.
- Replaced size-only Instagram scroll detection with viewer movement and page-index evidence so opening/layout events do not count as a new Reel.
- Reset paging state around comments and when leaving the shared viewer.
- Added regression coverage for delayed taps, loading gaps, comment transitions, and actual paging.

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
