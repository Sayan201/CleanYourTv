// SPDX-License-Identifier: GPL-3.0-only

package app.sicktv

import android.net.Uri
import androidx.lifecycle.viewModelScope
import app.sicktv.adb.ApkInfo
import app.sicktv.adb.InstalledVersion
import app.sicktv.data.Device
import app.sicktv.data.DeviceSource
import app.sicktv.data.DeviceState
import app.sicktv.data.FakeDeviceSource
import app.sicktv.data.PhoneStorage
import app.sicktv.data.PickedApp
import app.sicktv.data.ScanHit
import app.sicktv.data.ScanKind
import app.sicktv.data.Snapshot
import app.sicktv.ui.Copy
import app.sicktv.ui.SideloadUi
import app.sicktv.ui.SickTvViewModel
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** One TV, online until [state] says otherwise, whose version check fails when [unreachable]; [asked] counts the checks. */
private class VersionCheckTv : DeviceSource by FakeDeviceSource(FakeDeviceSource.Scenario.Empty, clock = { 0L }) {
    var unreachable = false
    var asked = 0
    var state = DeviceState.Online

    override suspend fun known() = listOf(Device("tv", ScanHit("tv", 5555, ScanKind.Classic, "tv", null), state, null, null, null, 0L))

    override fun live(deviceId: String) = emptyFlow<Snapshot>()

    override suspend fun installed(deviceId: String, packageName: String): InstalledVersion? {
        asked++
        if (unreachable) throw IOException("link gone")
        return null
    }
}

/** A phone whose picked file reads when [readable], and fails like a revoked or vanished file otherwise. */
private class Picked(private val readable: Boolean) : PhoneStorage {
    override suspend fun readApp(uris: List<Uri>) =
        if (readable) PickedApp(listOf(File("app.apk")), ApkInfo("com.example.app", 1, "1.0"), "Example") else throw IOException("permission revoked")
    override fun open(uri: Uri) = error("unused")
    override fun create(uri: Uri) = Buffer()
    override fun delete(uri: Uri) = Unit
    override fun clearApp() = Unit
}

/** A failed pick names what failed: the phone's file or the TV, never "lost partway" before anything was sent. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SideloadPickTest {
    private val dispatcher = StandardTestDispatcher()
    private val test = TestScope(dispatcher)
    private val tv = VersionCheckTv()
    private lateinit var vm: SickTvViewModel

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() {
        vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    private fun TestScope.pick(phone: PhoneStorage): SideloadUi? {
        vm = SickTvViewModel(tv, phone, clock = { 0L })
        runCurrent()
        vm.pickedApp(listOf(Uri.parse("content://x/app.apk")))
        runCurrent()
        return vm.state.value.device.sideload
    }

    @Test fun aFileThePhoneCantReadSaysSoAndNeverAsksTheTv() = with(test) {
        val ui = pick(Picked(readable = false))
        assertEquals(SideloadUi.Step.Failed, ui?.step)
        assertEquals(Copy.CANT_READ, ui?.error)
        assertEquals(0, tv.asked)
    }

    @Test fun aTvThatDoesntAnswerTheVersionCheckIsUnreachableNotLost() = with(test) {
        tv.unreachable = true
        val ui = pick(Picked(readable = true))
        assertEquals(SideloadUi.Step.Failed, ui?.step)
        assertEquals(Copy.UNREACHABLE, ui?.error)
        assertEquals(1, tv.asked)
    }

    @Test fun aReadableFileOnAnAnsweringTvIsReady() = with(test) {
        val ui = pick(Picked(readable = true))
        assertEquals(SideloadUi.Step.Ready, ui?.step)
    }

    /** Back from the picker the link drops and returns, which reselects the TV: the dialog stays (#290). */
    @Test fun theDialogOutlivesTheLinkDroppingAndComingBack() = with(test) {
        pick(Picked(readable = true))
        tv.state = DeviceState.Offline
        advanceTimeBy(3_100)
        tv.state = DeviceState.Online
        advanceTimeBy(3_100)
        assertEquals(DeviceState.Online, vm.state.value.selected?.state)
        assertEquals(SideloadUi.Step.Ready, vm.state.value.device.sideload?.step)
    }
}
