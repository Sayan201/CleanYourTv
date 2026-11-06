# Personalized MiTV-AXFR1 setup

This is the home-screen configuration built for one Xiaomi MiTV-AXFR1 (rango), Android TV 11.
It is separate from the community example at [`../debloat.txt`](../debloat.txt):
[`debloat.txt`](debloat.txt) records the packages observed disabled on this TV, while
[`not-installed-user-0.txt`](not-installed-user-0.txt) records packages absent for user 0.

## Set up another TV with SickTv

Use the **SickTv** phone companion app (`app.sicktv`) to connect to your TV and apply this debloat profile and custom launcher directly over wireless ADB without needing a computer:

1. Connect your phone to the same Wi-Fi network as the TV.
2. Enable **Network Debugging** on the TV.
3. Open **SickTv** on your phone and select your TV from the scan list.
4. Apply the `xiaomi-mitv-axfr1` debloat profile to safely disable the listed bloatware packages.
5. Install the **CleanYourTV** launcher (`com.sayan.tvhome`) as your default home screen.

The package list reflects one live TV, not every AXFR1 firmware. It includes Live TV/input and
remote-related packages; read [the state notes](package-state.md) before applying it to another set.

## Home screen features

- Five dock slots fill from apps already installed. Long-press an app and choose **Move left**,
  **Move right**, or **App info** to organize them.
- Bing Daily Wallpaper Engine automatically fetches the daily photo, falling back to seven bundled
  Unsplash landscape wallpapers ([`../../../docs/wallpaper-sources.md`](../../../docs/wallpaper-sources.md)) if offline.
- The greeting and clock sit above a Celsius weather card. Weather uses the TV's approximate
  location with Open-Meteo, with automatic IP geolocation fallback when GPS coordinates are unavailable.
- The media bar below the clock has previous, play/pause, and next controls. If prompted, enable
  CleanYourTV's notification access in TV Settings so it can read active media sessions.
- A single translucent pill immediately left of **Inputs** shows CPU and used-memory percentages.
- The Wi-Fi pill shows the connected network name when Android grants the required location access.
- The first app row has a transparent background. Press Up from a dock app to enter the media row.
- The Standby mode (screensaver) features Bing's daily wallpaper (offset by 1 day to guarantee it never
  duplicates the Home screen), with a fixed bottom-left clock, date, and contrast scrim. Select
  **CleanYourTV Clock** in Android TV's screensaver settings, or run:

  ```sh
  TV_HOST=<TV_IP> tools/tv.py 'settings put secure screensaver_components com.sayan.tvhome/.ClockDream; settings put secure screensaver_enabled 1'
  ```

Weather requests go to Open-Meteo over the internet. No API key is needed. The application ID is
`com.sayan.tvhome`.
