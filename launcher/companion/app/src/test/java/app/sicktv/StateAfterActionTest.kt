// SPDX-License-Identifier: GPL-3.0-only

package app.sicktv

import androidx.lifecycle.viewModelScope
import app.sicktv.adb.ConnectionRequest
import app.sicktv.data.AccessKey
import app.sicktv.data.Action
import app.sicktv.data.AppInfo
import app.sicktv.data.Device
import app.sicktv.data.DeviceSource
import app.sicktv.data.DeviceState
import app.sicktv.data.FakeDeviceSource
import app.sicktv.data.HistoryEntry
import app.sicktv.data.ScanHit
import app.sicktv.data.ScanKind
import app.sicktv.data.Snapshot
import app.sicktv.data.TvAccess
import app.sicktv.ui.SickTvViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** One online TV whose app is turned off by a change that then fails, and that asks to allow a device. */
private class HalfwayTv : DeviceSource by FakeDeviceSource(FakeDeviceSource.Scenario.Empty, clock = { 0L }) {
    var appOn = true
    val history = mutableListOf<HistoryEntry>()
    var request: ConnectionRequest? = null
    var keys = emptyList<AccessKey>()

    override suspend fun known() = listOf(Device("tv", ScanHit("tv", 5555, ScanKind.Classic, "tv", null), DeviceState.Online, null, null, null, 0L))

    override fun live(deviceId: String): Flow<Snapshot> = emptyFlow()

    override suspend fun apps(deviceId: String) = listOf(AppInfo("com.example", "App", null, false, appOn, null, ""))

    override suspend fun act(deviceId: String, action: Action, packageName: String, undoing: Long?): HistoryEntry {
        // Recorded and done on the TV, then a later step fails, as a Home switch's way back can.
        appOn = false
        history += HistoryEntry(1, deviceId, 0L, action, packageName, "App", Action.Enable, null)
        throw IllegalStateException("the old launcher didn't come back")
    }

    override suspend fun history(deviceId: String) = history.toList()

    override suspend fun connectionRequest(deviceId: String) = request

    override suspend fun answerRequest(deviceId: String, request: ConnectionRequest, allow: Boolean, always: Boolean): Boolean {
        this.request = null
        keys = listOf(AccessKey(request.fingerprint, "laptop", null))
        return true
    }

    override suspend fun access(deviceId: String) = TvAccess(allowed = keys)

    var alertOks = 0

    /** Fire OS's alert closes, and the prompt under it shows. */
    override suspend fun closeAlert(deviceId: String): Boolean {
        alertOks++
        request = ConnectionRequest("AA:BB")
        return true
    }
}

private val behindAlert = ConnectionRequest("", canAlwaysAllow = false, behindAlert = true)

@OptIn(ExperimentalCoroutinesApi::class)
class StateAfterActionTest {
    private val dispatcher = StandardTestDispatcher()
    private val test = TestScope(dispatcher)
    private val source = HalfwayTv()
    private lateinit var vm: SickTvViewModel

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() {
        vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test fun aChangeThatFailsHalfwayShowsWhatTheTvHasNow() = with(test) {
        vm = SickTvViewModel(source, clock = { 0L })
        runCurrent()
        vm.propose("com.example", Action.Disable)
        vm.confirm()
        runCurrent()

        val page = vm.state.value.device
        assertEquals(listOf(false), page.apps.map { it.enabled })
        assertEquals(1, page.history.size)
        assertNull(page.busy)
    }

    @Test fun anAllowedDeviceShowsInAccessAtOnce() = with(test) {
        source.request = ConnectionRequest("AA:BB")
        vm = SickTvViewModel(source, clock = { 0L })
        runCurrent()
        vm.answerRequest(allow = true, always = true)
        runCurrent()
        // Well before the next 10 s read of who has access.
        assertEquals(listOf("AA:BB"), vm.state.value.device.access?.allowed?.map { it.fingerprint })
    }

    @Test fun pressOkOnTheTvThenThePromptItselfTakesOver() = with(test) {
        source.request = behindAlert
        vm = SickTvViewModel(source, clock = { 0L })
        runCurrent()
        assertEquals(true, vm.state.value.device.request?.behindAlert)
        vm.closeAlert()
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(1, source.alertOks)
        // Before the next 5 s check.
        assertEquals("AA:BB", vm.state.value.device.request?.fingerprint)
    }

    @Test fun closeHidesTheAlertStateOnlyWhileTheAlertIsUp() = with(test) {
        source.request = behindAlert
        vm = SickTvViewModel(source, clock = { 0L })
        runCurrent()
        vm.dismissRequest()
        advanceTimeBy(5_001)
        runCurrent()
        assertNull(vm.state.value.device.request)
        source.request = null
        advanceTimeBy(5_000)
        runCurrent()
        source.request = behindAlert
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(true, vm.state.value.device.request?.behindAlert)
        assertEquals(0, source.alertOks)
    }
}
