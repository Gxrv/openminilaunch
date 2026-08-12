# MinkLauncher Open

A fully open-source, focused Android home-screen launcher built with Kotlin and Jetpack Compose.

[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

Current version: **0.5.0**. Feature releases show existing users a one-time in-app update notice covering new behavior, privacy impact, and any optional permissions; the onboarding tutorial is updated alongside each release.

## Included

- Date header and settings access
- First-run onboarding for the launcher, Magic Box, to-dos, and permissions
- Replayable onboarding from Settings, including permission setup
- Two-at-a-time, horizontally snapping to-do preview
- Eight home shortcuts: Note, Event, Weather, To-do, Call, Message, Files, and Drawer
- Weather opens only an installed app explicitly selected by the user
- Configurable default apps for the six external shortcuts
- One-tap reset back to each shortcut's system intent
- A compact drawer containing up to five selected apps
- Real installed-app icons and an alphabetical jump rail in both app pickers
- A searchable Magic Box:
  - Physical-keyboard instant typing — press any printable key from the home screen to reveal the already-focused Magic Box with the first character preserved
  - Plain text — search locally accessible file names, then delegate web queries to Android's selected search handler
  - `@name message` — choose a contact, then open the configured/default SMS or RCS composer with recipient and text filled
  - `#name` — choose a contact, confirm in MinkLauncher, then place the call directly
  - `-task` — save an internal to-do
  - `$text` — enter a multiline note using Android's standard create-note or text-sharing contracts
  - `+text` — create a calendar event with the text as its description
  - `?app` — search and launch any installed app
- The five most recent plain-text searches are stored locally, with controls to reuse, delete, or clear them; hot-key actions are never added
- Direct calling uses Android's Call permission; emergency numbers and failed direct-call attempts fall back to the system dialer
- Full to-do management: add, check, edit, delete, and reorder
- Delete confirmation to protect against accidental taps and back-swipe gestures
- Animated Magic Box to-do delivery into the newest widget page
- System, light, and dark appearance modes
- Swipe down anywhere on the home screen to expand notifications
- Optional double-tap on empty Home space locks through Android's accessibility `GLOBAL_ACTION_LOCK_SCREEN`, preserving normal fingerprint and face unlock eligibility
- Local persistence via SharedPreferences; no account or network is required
- Privacy-first file search through Android's MediaStore and user-selected document folders; filenames never leave the device
- Document search uses only folders selected through Android's Storage Access Framework, with an in-search reminder until one is selected
- File results are grouped as Photos, Videos, Documents, and Audio, with locally generated thumbnails where Android provides them

## Run

Open the folder in Android Studio or build from the terminal:

```shell
./gradlew assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

For an unsigned release APK:

```shell
./gradlew --no-daemon clean lintDebug assembleRelease
```

The release artifact is generated at `app/build/outputs/apk/release/app-release-unsigned.apk`. The build requires JDK 17 or newer and Android SDK 35; Android Studio is not required.

After installing, press the device Home button and select **MinkLauncher Open** as the home app. Contact permission is requested only when `@` or `#` search is used. Optional controls, including **MinkLauncher Open - Double Tap to Lock Screen** in Android Accessibility settings, are linked from Settings.

On first launch, MinkLauncher Open explicitly opens Android's default Home-app prompt. If it is dismissed, it can be reopened from **Settings → Default home app**.

## Privacy

MinkLauncher Open has no accounts, ads, analytics, trackers, or application server. Search, to-dos, settings, and file indexing stay local. See [PRIVACY.md](PRIVACY.md) for permission details and the boundaries of Android intent handoffs.

## License

Copyright 2026 Katoa Apps. Source code and bundled artwork are released under the [Apache License 2.0](LICENSE). The license does not grant unrestricted use of the MinkLauncher name or branding.
