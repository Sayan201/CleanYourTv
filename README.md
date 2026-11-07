# CleanYourTV for Android TV & Google TV

A clean, customizable, and lightweight home screen launcher and debloating setup for universal Android TV and Google TV devices.

This repository provides the launcher app, mobile companion installer, package management profiles, and setup guides for universal TV devices. Device-specific debloat profiles and package lists are available under [examples/](examples/).

## The home screen

- **Customizable app slots in the dock (3 to 6 apps per row).** Configurable directly from Launcher Settings, with intelligent focus navigation and wrapping. Long-press an app to open a modern liquid-glass flyout menu to move it left/right or open its App Info.
- **Bing Daily Wallpaper Engine with rotation fallback.** Automatically fetches Bing's daily picture with adaptive luminance contrast detection for navigation pills, falling back to bundled Unsplash photos ([docs/wallpaper-sources.md](docs/wallpaper-sources.md)) if offline.
- **Greeting, clock, and weather.** Weather is shown in Celsius using approximate device location and Open-Meteo, with automatic IP-based geolocation fallback.
- **A wide weather bar below the clock.** Displays live temperature and condition on the left, with local city name and source on the right.
- **A combined CPU and memory tile.** One translucent button-style tile shows both usage percentages immediately to the left of Inputs.
- **Liquid-glass Inputs, Wi-Fi, and Settings pills.**
  - **Inputs flyout:** Smooth glass panel showing all active TV input sources.
  - **Settings flyout:** 2-option menu offering quick access to **System Settings** and **Launcher Settings**.
  - **GitHub Update Checker:** Checks GitHub Releases for new updates and highlights the Settings pill and Launcher Settings with a notification dot badge.
- **Launcher Settings:**
  - Choose dock row column count (3, 4, 5, or 6 apps per row).
  - Configurable screensaver idle timer (3 min, 5 min, 10 min, 15 min, or Never).
  - 1-tap screensaver preview and shortcut to TV Screensaver settings.
  - In-app GitHub release update check and download.
- **Standby mode & Ambient Screensaver.** Fixed direct-to-sleep issues with official Android TV `dream_info` metadata, `FLAG_KEEP_SCREEN_ON`, and an in-app idle timeout fallback that displays yesterday's Bing daily photo with bottom-left clock and soft contrast scrim.
- **Remote navigation.** Seamless directional navigation across dock and app rows with focus preservation.

The original customized launcher is the native Android app in the `launcher` folder, with application ID `com.sayan.tvhome`.

## Standalone CleanYourTV application

The `launcher/standalone` Gradle module builds a second, independently installable application with application ID `com.sayan.cleanyourtv`. It reuses the maintained launcher implementation and resources, so the separate package can offer the same home-screen features without replacing the existing customized app. The manifest displays it as **CleanYourTV Standalone**.

Build the standalone APK from the repository root:

```sh
cd launcher
./gradlew --no-daemon :standalone:assembleRelease
```

The APK is written to `launcher/standalone/build/outputs/apk/release/standalone-release.apk`. GitHub Actions builds and uploads it as the `CleanYourTV-Standalone-APK` workflow artifact on pull requests and pushes covered by CI. The artifact is retained for 14 days. The release build is signed with Gradle's debug signing configuration for sideloading, consistent with the existing customized launcher; it is not a Play Store release.

The standalone module shares the existing source and resource directories rather than maintaining a forked copy of the launcher code. Feature fixes made to the shared implementation therefore apply to both packages. The original installation and its package identity remain unchanged.

## Device debloat and package profiles

Device-specific debloat lists and package records are documented in [examples/](examples/):

- `debloat.txt` lists bloatware packages safe to disable for user 0 across 90+ TV models and streaming devices.
- The debloat workflow uses Android's `pm disable-user` command; it does not uninstall packages, making changes safe and reversible.
- The **SickTv** companion app reads these curated lists and enables 1-tap debloating directly from your mobile phone.

## Install on the TV with SickTv Companion

The companion mobile app **SickTv** (`app.sicktv`) runs directly on your Android phone to discover your TV, debloat it, and install the CleanYourTV launcher over wireless ADB without needing a computer or command-line scripts.

### How to use SickTv:
1. Ensure your phone and Android TV are connected to the same Wi-Fi network.
2. Enable **Network Debugging** (or **Wireless Debugging** on Android 11+) in your TV's Developer options.
3. Open **SickTv** on your phone. It automatically scans your local network and discovers your TV.
4. Select your TV, review the debloat list for your model, and tap to disable bloatware.
5. Select **CleanYourTV** from the launcher list to automatically download and install `com.sayan.tvhome` as your default home screen.

## Downloads & GitHub Releases

Every push to `main` automatically builds, signs, and publishes the 2 official APK packages to the repository's [GitHub Releases](https://github.com/Sayan201/CleanYourTv/releases):

- **`launcher.apk`**: CleanYourTv Launcher for Android TV (`com.sayan.tvhome`).
- **`sicktv-companion.apk`**: SickTv Companion app for Android phones (`app.sicktv`).

## Useful files

| Path | Purpose |
|---|---|
| [launcher/](launcher/) | Android TV launcher source and build scripts |
| [launcher/companion/](launcher/companion/) | SickTv mobile companion source and device catalog |
| [launcher/standalone/](launcher/standalone/) | Separate app ID and manifest for the standalone build |
| [tools/](tools/) | ADB connection, package disable/restore, and theme utilities |
| [Device profiles](examples/) | Curated package debloat inventories and device notes |
| [Wallpaper credits](docs/wallpaper-sources.md) | Credits for the bundled wallpaper photos |
| [config.xml](launcher/app/src/main/res/values/config.xml) | Launcher defaults and settings shared by both apps |

## Privacy and safety

This repository contains no private Wi-Fi credentials, personal account data, or private signing keys. Review the device profile in [examples/](examples/) before disabling packages on your TV.

When setup is finished, turn off Network/ADB debugging in your TV's Developer options and revoke debugging authorizations you no longer need. The launcher itself continues to work normally without ADB.

## Project origin

This project is based on [CleanYourTV by Gathin23](https://github.com/Gathin23/cleanyourtv), which is licensed under MIT. The launcher foundation and its license remain here; this repository expands it into a universal Android TV & Google TV home screen with the SickTv companion installer. See [LICENSE](LICENSE).
