// SPDX-License-Identifier: GPL-3.0-only

package app.sicktv.adb

import org.junit.Test

class KadbClasspathTest {
    // Kadb ships Java 25 bytecode; this fails fast if the test JVM is older.
    @Test
    fun kadbLoadsOnTestJvm() {
        Class.forName("com.flyfishxu.kadb.Kadb")
    }
}
