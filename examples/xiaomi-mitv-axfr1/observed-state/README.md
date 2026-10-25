# Personalized MiTV-AXFR1 setup

This is the home-screen configuration built for one Xiaomi MiTV-AXFR1 (rango), Android TV 11.
It is separate from the community example at [`../debloat.txt`](../debloat.txt):
[`debloat.txt`](debloat.txt) records the packages observed disabled on this TV, while
[`not-installed-user-0.txt`](not-installed-user-0.txt) records packages absent for user 0.

## Set up another TV

Enable ADB debugging on the TV and connect your computer to its current IP. Install Python 3,
JDK 17, Android SDK platform 34, and Android build tools. From the repository root:

```sh
python3 -m venv tools/venv
tools/venv/bin/pip install -r tools/requirements.txt
TV_HOST=<TV_IP> launcher/build-and-install.sh
TV_HOST=<TV_IP> tools/disable.sh examples/xiaomi-mitv-axfr1/observed-state/debloat.txt
```

On Windows, use the PowerShell versions:

```powershell
python -m venv tools\venv
tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt
$env:TV_HOST = '<TV_IP>'
powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost $env:TV_HOST
powershell -ExecutionPolicy Bypass -File tools\disable.ps1 -TvHost $env:TV_HOST examples\xiaomi-mitv-axfr1\observed-state\debloat.txt
```

Build and start the custom launcher before applying the package list. The installer safely switches
Home and disables the stock launcher after the custom launcher starts. The package script uses
`pm disable-user`; it does not uninstall apps. It saves a restore record before changing package
states. To restore the debloat changes, run `tools/restore.sh` or `tools/restore.ps1` with the same
list and keep the generated record.

The package list reflects one live TV, not every AXFR1 firmware. It includes Live TV/input and
remote-related packages; read [the state notes](package-state.md) before applying it to another set.

## Home screen features

- Five dock slots fill from apps already installed. Long-press an app and choose **Move left**,
  **Move right**, or **App info** to organize them.
- Seven bundled Unsplash landscape wallpapers rotate in a daily cycle. See
  [`../../../docs/wallpaper-sources.md`](../../../docs/wallpaper-sources.md) for photo credits.
- The greeting and clock sit above a Celsius weather card. Weather uses the TV's approximate
  location with Open-Meteo; Gayeshpur is the fallback when Android cannot provide a location.
- The media bar below the clock has previous, play/pause, and next controls. If prompted, enable
  CleanYourTV's notification access in TV Settings so it can read active media sessions.
- A single translucent pill immediately left of **Inputs** shows CPU and used-memory percentages.
- The Wi-Fi pill shows the connected network name when Android grants the required location access.
- The first app row has a transparent background. Press Up from a dock app to enter the media row.
- The low-power Clock screensaver uses a black background and slowly changes position to reduce
  burn-in. Select **CleanYourTV Clock** in Android TV's screensaver settings, or run:

  ```sh
  TV_HOST=<TV_IP> tools/tv.py 'settings put secure screensaver_components com.gathin.tvhome.custom/com.gathin.tvhome.ClockDream; settings put secure screensaver_enabled 1'
  ```

Weather requests go to Open-Meteo over the internet. No API key is needed. The application ID is
`com.gathin.tvhome.custom`, so the original CleanYourTV install can remain available as a rollback
option.
