// SPDX-License-Identifier: GPL-3.0-only

package app.sicktv

import androidx.lifecycle.viewModelScope
import app.sicktv.adb.RemoteKey
import app.sicktv.data.Action
import app.sicktv.data.AppInfo
import app.sicktv.data.Device
import app.sicktv.data.DeviceList
import app.sicktv.data.DeviceSource
import app.sicktv.data.DeviceState
import app.sicktv.data.FakeDeviceSource
import app.sicktv.data.HistoryEntry
import app.sicktv.data.ScanHit
import app.sicktv.data.ScanKind
import app.sicktv.data.Snapshot
import app.sicktv.data.Suggestions
import app.sicktv.ui.Copy
import app.sicktv.ui.SickTvViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Two online TVs; everything else comes from the empty fake. */
private class TwoTvs : DeviceSource by FakeDeviceSource(FakeDeviceSource.Scenario.Empty, clock = { 0L }) {
    var appsCalls = 0
    var appsDelayMs = 0L
    var actGate: CompletableDeferred<Unit>? = null
    val acted = mutableListOf<Pair<String, String>>()
    var pressFails = false

    override suspend fun known() = listOf("tv-a", "tv-b").map {
        Device(it, ScanHit(it, 5555, ScanKind.Classic, it, null), DeviceState.Online, null, null, null, 0L)
    }

    override fun live(deviceId: String): Flow<Snapshot> = emptyFlow()

    /** Set: a list found for every TV, keeping these packages. */
    var listKeep: Set<String>? = null

    override suspend fun suggestions(deviceId: String, apps: suspend () -> List<AppInfo>): Suggestions {
        val keep = listKeep ?: return Suggestions.Unavailable
        apps()
        return Suggestions.Ready(null, DeviceList(deviceId, deviceId, null, null, keep, emptyList()), null, emptyList(), emptyList())
    }

    override suspend fun apps(deviceId: String): List<AppInfo> {
        appsCalls++
        delay(appsDelayMs)
        return listOf(AppInfo("app-on-$deviceId", "App", null, false, true, null, ""))
    }

    override suspend fun act(deviceId: String, action: Action, packageName: String, undoing: Long?): HistoryEntry {
        acted += deviceId to packageName
        actGate?.await()
        return HistoryEntry(acted.size.toLong(), deviceId, 0L, action, packageName, "App", Action.Enable, null)
    }

    override suspend fun history(deviceId: String) = emptyList<HistoryEntry>()

    override suspend fun press(deviceId: String, key: RemoteKey): Boolean {
        if (pressFails) throw IllegalStateException("$deviceId is offline")
        return true
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ResultsLandOnTheirTvTest {
    private val dispatcher = StandardTestDispatcher()
    private val test = TestScope(dispatcher)
    private val source = TwoTvs()
    private lateinit var vm: SickTvViewModel

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() {
        vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test fun slowAppListIsReadOnce() = with(test) {
        // The state changes every second; a read inside update {} was retried on every change.
        source.appsDelayMs = 3_000
        vm = SickTvViewModel(source, clock = { 0L })
        advanceTimeBy(20_000)
        assertEquals(1, source.appsCalls)
        assertEquals(listOf("app-on-tv-a"), vm.state.value.device.apps?.map { it.packageName })
    }

    @Test fun theTvsListUsesTheSameAppRead() = with(test) {
        source.appsDelayMs = 3_000
        source.listKeep = setOf("app-on-tv-a")
        vm = SickTvViewModel(source, clock = { 0L })
        advanceTimeBy(20_000)
        assertEquals(1, source.appsCalls)
        assertTrue(vm.state.value.device.lists is Suggestions.Ready)
        // The keep mark comes from the list, not a second read of the TV.
        assertEquals(listOf(true), vm.state.value.device.apps?.map { it.listKeep })
    }

    @Test fun resultForAnotherTvLeavesTheShownPageAlone() = with(test) {
        vm = SickTvViewModel(source, clock = { 0L })
        runCurrent()
        assertEquals("tv-a", vm.state.value.selectedId)

        source.actGate = CompletableDeferred()
        vm.propose("com.example", Action.Disable)
        vm.confirm()
        runCurrent()
        vm.select("tv-b")
        runCurrent()
        source.actGate!!.complete(Unit)
        runCurrent()

        val page = vm.state.value.device
        assertEquals("tv-b", vm.state.value.selectedId)
        assertEquals(listOf("app-on-tv-b"), page.apps?.map { it.packageName })
        assertNull(page.busy)
        assertEquals("tv-a", vm.done.value?.deviceId)
    }

    @Test fun switchingBackShowsTheLastListsUntilFreshOnesReplaceThem() = with(test) {
        source.listKeep = setOf("kept")
        vm = SickTvViewModel(source, clock = { 0L })
        runCurrent()
        vm.select("tv-b")
        runCurrent()

        source.appsDelayMs = 5_000
        vm.select("tv-a")
        runCurrent()
        val page = vm.state.value.device
        assertEquals(listOf("app-on-tv-a"), page.apps.map { it.packageName })
        assertFalse(page.appsLoading)
        assertEquals("tv-a", ((page.lists as Suggestions.Ready).list).id)

        // The fresh read was still out; once it lands it replaces the shown list.
        advanceTimeBy(5_100)
        assertEquals(listOf("app-on-tv-a"), vm.state.value.device.apps.map { it.packageName })
    }

    @Test fun snackbarUndoGoesToTheTvItCameFrom() = with(test) {
        vm = SickTvViewModel(source, clock = { 0L })
        runCurrent()
        vm.propose("com.example", Action.Disable)
        vm.confirm()
        runCurrent()
        val done = vm.done.value!!
        vm.select("tv-b")
        runCurrent()

        vm.undo(done.entry, done.deviceId)
        runCurrent()
        assertEquals("tv-a" to "com.example", source.acted.last())
        assertNull(vm.state.value.device.busy)
    }

    @Test fun lostTvStopsTheKey() = with(test) {
        vm = SickTvViewModel(source, clock = { 0L })
        runCurrent()
        source.pressFails = true
        var ok = true
        launchAndRun { ok = vm.press(RemoteKey.UP) }
        assertFalse(ok)
        assertEquals(Copy.REMOTE_LOST, vm.notice.value)
    }

    private fun TestScope.launchAndRun(block: suspend () -> Unit) {
        backgroundScope.launch { block() }
        runCurrent()
    }
}
