// SPDX-License-Identifier: GPL-3.0-only

package app.sicktv

import android.content.Context
import app.sicktv.data.AndroidLauncherPick
import app.sicktv.data.LauncherPick
import app.sicktv.data.Updates

// The Play build's side of app/src/github/…/Store.kt: no update check and no downloads. Play updates the
// app, and on Fire OS the user downloads the launcher in the browser and picks the file.

@Suppress("UNUSED_PARAMETER")
internal fun updates(context: Context, version: String): Updates? = null

/** Checks a launcher file the user picked; downloads nothing. */
internal fun launcherPick(context: Context): LauncherPick = AndroidLauncherPick(context)
