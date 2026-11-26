# darkOS Launcher

A dark, Linux-terminal-style home screen for Android, made for a Samsung Galaxy A55.

- **Tabs**: `1:apps`, `2:settings` and `3:system`. Tap a tab or swipe left or right to switch.
- **Swipe down** for a terminal-style quick panel with brightness, volume, flashlight, sound mode, rotation, Wi-Fi, Bluetooth and notifications.
- **Apps keep their places**: new apps go to the bottom, and coming back from an app leaves the screen as you left it. Hold an app to move it up, down, to the top or to the bottom, or to uninstall it.
- **Settings tab**: terminal controls that change the phone directly. Extra Samsung pages are listed under *MORE*.
- **Typing**: an app name (`chrome`), a number, or commands such as `torch`, `wifi`, `panel`, `neofetch` and `help`.

Samsung's own Settings app and its pull-down panel can't be restyled by other apps, so they keep their normal look.

## Install

1. On your phone, open the [latest release](../../releases/latest) and download `DarkLauncher.apk`.
2. Open the file and install it. Updates install over the old version.
3. Choose **sethome** in the terminal, or go to *Settings → Apps → Choose default apps → Home app*.

The first time you change brightness, rotation or the screen timeout, Android asks you to allow "Modify system settings" for darkOS.

To go back to Samsung's launcher, open *Settings → Apps → Choose default apps → Home app* and pick **One UI Home**.

## Build

Every push to `main` builds the APK on GitHub Actions and publishes a new release.
`keystore/darklauncher.jks` is a signing key used only for this app.
