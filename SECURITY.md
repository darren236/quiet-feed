# Security policy

## Supported versions

Security fixes are made for the latest published version. Install updates from this repository's [GitHub Releases](https://github.com/darren236/quiet-feed/releases/latest). There is no automatic updater.

## Report a vulnerability

Use GitHub's **Security → Advisories → Report a vulnerability** for a private report:

[Report privately](https://github.com/darren236/quiet-feed/security/advisories/new)

Include the affected version, steps to reproduce, and the expected impact. Share only the minimum information needed to reproduce the problem. Do not include personal messages, account credentials, banking details, or signing keys. If private reporting is unavailable, open an issue asking the maintainer to enable a private reporting channel without including vulnerability details.

Ordinary false blocks, missed blocks, and unsupported app layouts belong in the [bug report form](https://github.com/darren236/quiet-feed/issues/new?template=bug_report.yml).

## Permission and data scope

QuietFeed uses Android accessibility access to recognize supported social-app screens and perform navigation. Android grants broad window access; the app receives events for foreground tracking and collects supported apps' visible UI locally. It does not request internet permission, capture screenshots, save message contents, or send analytics. App-mode preferences are stored on the device.

Accessibility access must be enabled manually and can be disabled in Android settings. Changing an app mode to Off leaves the service enabled. Some banking apps require the service to be disabled; QuietFeed does not bypass that requirement. See the [Samsung shortcut instructions](README.md#samsung-shortcut-for-banking-apps).

Release signing keys and passwords are kept outside tracked source. Local builds generate their own key; they cannot update the official APK without first removing it.
