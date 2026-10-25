# MiTV-AXFR1 live package snapshot

Captured from the connected Xiaomi MiTV-AXFR1 (`rango`) running Android TV 11 on 2026-10-25.
The snapshot records Package Manager state for user 0; it does not include the TV's IP address,
Wi-Fi name, account data, or signing key.

## Disabled

Twenty-three packages were disabled for user 0. Twenty-two are in [`debloat.txt`](debloat.txt).
The stock Android TV launcher, `com.google.android.tvlauncher`, is handled separately by the
launcher installer after the custom Home app is installed and started.

Apply the 22-package list with the repository's backed-up disable workflow:

```sh
TV_HOST=<TV_IP> tools/disable.sh examples/xiaomi-mitv-axfr1/observed-state/debloat.txt
TV_HOST=<TV_IP> tools/restore.sh examples/xiaomi-mitv-axfr1/observed-state/debloat.txt
```

The restore tool remembers the pre-change state and re-enables only packages that were enabled
before the list was applied. Keep its recovery record. The launcher install script manages the
stock launcher and returns Home to it during recovery.

This list includes `com.android.tv` (the built-in Live TV/input picker on the documented firmware),
`com.droidlogic.droidlivetvsettings`, and Xiaomi's `com.xiaomi.android.tvsetup.partnercustomizer`.
Disabling them can affect Live TV, input settings, remote pairing, or remote button behavior. The
list represents this TV's observed configuration; review those functions before using it on another
set or firmware.

## Not installed for user 0

Six packages are recorded in [`not-installed-user-0.txt`](not-installed-user-0.txt). They appear
when Package Manager is queried with `-u` but not in the ordinary installed-package list. This
does not prove that a person explicitly uninstalled them, or that they exist on every AXFR1 image.
The debloat script does not uninstall or install these packages. If you need one, try reinstalling
it from the Play Store or Xiaomi's app store; `pm install-existing --user 0 <package>` can restore a
system app only when its APK is still present on that firmware.

## Capture commands

```sh
TV_HOST=<TV_IP> tools/tv.py 'pm list packages -d --user 0'
TV_HOST=<TV_IP> tools/tv.py 'pm list packages --user 0'
TV_HOST=<TV_IP> tools/tv.py 'pm list packages -u --user 0'
```
