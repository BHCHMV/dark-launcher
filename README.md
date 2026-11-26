# darkOS Launcher

A dark home-screen launcher for Android (made for a Samsung Galaxy A55) with three looks you can switch between:

- **iPhone style**: clock, search bar, rounded icons and a frosted dock.
- **Windows style**: big clock, "Start" and colored live tiles, plus a live battery tile.
- **Linux terminal**: green-on-black shell. Tap the numbered options (it types the command for you) or type commands such as `apps`, `open chrome`, `settings`, `wifi`, `sysinfo`, `theme`, `help`.

## Install

1. On your phone, open the [latest release](../../releases/latest) and download `DarkLauncher.apk`.
2. Open the file. If Android asks, allow installs from your browser or file app.
3. Open **darkOS Launcher** and tap **Set as home** (or go to *Settings → Apps → Choose default apps → Home app*).

To switch looks: open the **darkOS Settings** app tile, long-press the clock (iPhone and Windows), or type `theme` in terminal mode.

To go back to Samsung's launcher, open *Settings → Apps → Choose default apps → Home app* and pick **One UI Home**.

## Build

Every push to `main` builds the APK on GitHub Actions and publishes a new release.

`keystore/darklauncher.jks` is a signing key used only for this app, so each new build installs over the old one.
