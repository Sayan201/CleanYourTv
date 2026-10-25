# CleanYourTV for Xiaomi MiTV-AXFR1

A personalized Android TV launcher and cleanup setup for my Xiaomi MiTV-AXFR1 (rango), running Android TV 11.

This repository documents the home screen, package state, install steps, and recovery workflow we prepared for this TV. The device-specific files are under [examples/xiaomi-mitv-axfr1/observed-state](examples/xiaomi-mitv-axfr1/observed-state/).

## The home screen

- **Five app slots in the dock.** It fills from apps installed on the TV. Long-press an app to move it left or right, or open its App info.
- **Seven scenic wallpapers.** Bundled Unsplash photos rotate daily; the image credits are in [docs/wallpaper-sources.md](docs/wallpaper-sources.md).
- **Greeting, clock, and weather.** Weather is shown in Celsius using approximate device location and Open-Meteo, with Gayeshpur, West Bengal as the fallback.
- **Media controls below the clock.** Previous, play/pause, and next controls follow the active media session. Android notification access is needed for session information.
- **A combined CPU and memory tile.** One translucent button-style tile shows both usage percentages immediately to the left of Inputs.
- **Inputs, Wi-Fi, and Settings tiles.** The Wi-Fi tile shows the connected network name when Android grants location access.
- **A transparent first app row** matching the rows below it.
- **A lightweight clock screensaver.** It uses a black background and slowly moves the clock to reduce burn-in.
- **Remote navigation fixes.** Up from the dock can reach the media controls, and focus stays within the correct home-screen row.

The original customized launcher is the native Android app in the `launcher` folder, with application ID `com.gathin.tvhome.custom`.

## Standalone CleanYourTV application

The `launcher/standalone` Gradle module builds a second, independently installable application with application ID `com.sayan.cleanyourtv`. It reuses the maintained launcher implementation and resources, so the separate package can offer the same home-screen features without replacing the existing customized app. The manifest displays it as **CleanYourTV Standalone**.

Build the standalone APK from the repository root:

```sh
cd launcher
./gradlew --no-daemon :standalone:assembleRelease
```

The APK is written to `launcher/standalone/build/outputs/apk/release/standalone-release.apk`. GitHub Actions builds and uploads it as the `CleanYourTV-Standalone-APK` workflow artifact on pull requests and pushes covered by CI. The artifact is retained for 14 days. The release build is signed with Gradle's debug signing configuration for sideloading, consistent with the existing customized launcher; it is not a Play Store release.

The standalone module shares the existing source and resource directories rather than maintaining a forked copy of the launcher code. Feature fixes made to the shared implementation therefore apply to both packages. The original installation and its package identity remain unchanged.

## MiTV-AXFR1 package record

The observed state for this TV is documented in [the device profile](examples/xiaomi-mitv-axfr1/observed-state/README.md):

- debloat.txt lists 22 packages observed disabled for user 0. The stock Android TV launcher is handled separately by the launcher installer.
- not-installed-user-0.txt lists six packages present in Package Manager's all-packages query but absent from the normal installed list. It is an inventory, not an uninstall script.
- package-state.md explains the capture, what the list affects, and how to restore the recorded state.

The debloat workflow uses Android's pm disable-user command; it does not uninstall packages. Review the package notes before applying this list to another TV or firmware.

## Install on the TV

### Requirements

- A computer on the same network as the TV
- Android TV debugging enabled, with the computer authorized on the TV
- Python 3
- Android Studio, which supplies JDK 17 and the Android SDK. The launcher build uses Android SDK platform 34 and build tools 34.0.0.

### Windows PowerShell

Run these commands from the repository folder. Replace the TV_IP placeholder with the TV's current address; the address is intentionally not stored in this public repository.

```powershell
python -m venv tools\venv
tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt

$env:TV_HOST = '<TV_IP>'
powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost $env:TV_HOST
```

Once the launcher is installed and starts on Home, apply the device-specific package list only if you want the recorded debloat state:

```powershell
powershell -ExecutionPolicy Bypass -File tools\disable.ps1 -TvHost $env:TV_HOST examples\xiaomi-mitv-axfr1\observed-state\debloat.txt
```

To restore the packages and Home state recorded by the disable workflow:

```powershell
powershell -ExecutionPolicy Bypass -File tools\restore.ps1 -TvHost $env:TV_HOST examples\xiaomi-mitv-axfr1\observed-state\debloat.txt
```

Keep the recovery record created by the disable script. The installer also includes rollback handling for switching the default Home app.

### macOS or Linux

```sh
python3 -m venv tools/venv
tools/venv/bin/pip install -r tools/requirements.txt

export TV_HOST='<TV_IP>'
launcher/build-and-install.sh
tools/disable.sh examples/xiaomi-mitv-axfr1/observed-state/debloat.txt
# Restore later with:
tools/restore.sh examples/xiaomi-mitv-axfr1/observed-state/debloat.txt
```

## Useful files

| Path | Purpose |
|---|---|
| [launcher/](launcher/) | Android TV launcher source and build scripts |
| [launcher/standalone/](launcher/standalone/) | Separate app ID and manifest for the standalone build |
| [tools/](tools/) | ADB connection, package disable/restore, and theme utilities |
| [MiTV-AXFR1 package record](examples/xiaomi-mitv-axfr1/observed-state/) | This TV's observed package inventory and notes |
| [Wallpaper credits](docs/wallpaper-sources.md) | Credits for the bundled wallpaper photos |
| [config.xml](launcher/app/src/main/res/values/config.xml) | Launcher defaults and settings shared by both apps |

## Privacy and safety

This public repository uses TV_IP placeholders and contains no Wi-Fi name, account data, or signing key. The package record describes one TV and may not match another MiTV-AXFR1 firmware. Review the device profile before using its package list elsewhere.

When setup is finished, turn off Network/ADB debugging in the TV's Developer options and revoke debugging authorizations you no longer need. The launcher itself continues to work without ADB.

## Project origin

This personalized project is based on [CleanYourTV by Gathin23](https://github.com/Gathin23/cleanyourtv), which is licensed under MIT. The launcher foundation and its license remain here; this README and the MiTV-AXFR1 profile describe the customization for this TV. See [LICENSE](LICENSE).
