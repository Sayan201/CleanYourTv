// SPDX-License-Identifier: GPL-3.0-only

package app.sicktv

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.viewModelScope
import app.sicktv.data.Device
import app.sicktv.data.DeviceSource
import app.sicktv.data.FakeDeviceSource
import app.sicktv.data.ScanEvent
import app.sicktv.data.ScanHit
import app.sicktv.data.ScanKind
import app.sicktv.ui.RowState
import app.sicktv.ui.ScanScreen
import app.sicktv.ui.ScanUi
import app.sicktv.ui.SickTvViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Off Wi-Fi (or under a VPN) on the first scan, on it from the second; [lan] stands in for the network callback. */
private class JoinsWifi(private val first: ScanEvent = ScanEvent.NoLan) : DeviceSource by FakeDeviceSource(FakeDeviceSource.Scenario.Empty, clock = { 0L }) {
    var scans = 0
    val lan = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override suspend fun known() = emptyList<Device>()

    override fun scan() = flow {
        if (++scans == 1) emit(first)
        emit(ScanEvent.Done)
    }

    override fun lanUp() = lan
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OffWifiTest {
    @get:Rule val compose = createComposeRule()

    private val tv = ScanHit("192.0.2.7", 37211, ScanKind.Wireless, "chromecast", "Google")

    private fun show(scan: ScanUi, onOpenWifi: () -> Unit = {}) = compose.setContent {
        ScanScreen(scan, 0L, {}, {}, { _, _ -> }, {}, { null }, onOpenWifi = onOpenWifi)
    }

    @Test fun noLanShowsTheWifiPrompt() {
        var opened = false
        show(ScanUi(noLan = true)) { opened = true }
        compose.onNodeWithText("This phone isn't on Wi-Fi").assertIsDisplayed()
        compose.onNodeWithText("Add by IP address").assertIsDisplayed()
        compose.onNodeWithText("Open Wi-Fi settings").performClick()
        assertTrue(opened)
    }

    @Test fun aTvThatAnnouncedItselfStillShows() {
        show(ScanUi(noLan = true, hits = listOf(tv), rows = mapOf(tv.ip to RowState.Found)))
        compose.onNodeWithText("chromecast").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("This phone isn't on Wi-Fi").assertDoesNotExist()
    }

    @Test fun onWifiShowsTheScan() {
        show(ScanUi())
        compose.onNodeWithText("Add your TV").assertIsDisplayed()
        compose.onNodeWithText("This phone isn't on Wi-Fi").assertDoesNotExist()
    }

    @Test fun aVpnKeepsTheScanAndSaysWhy() {
        show(ScanUi(vpn = true))
        compose.onNodeWithText("Add your TV").assertIsDisplayed()
        compose.onNodeWithText("A VPN is on", substring = true).assertIsDisplayed()
        compose.onNodeWithText("This phone isn't on Wi-Fi").assertDoesNotExist()
    }

    @Test fun aVpnScansAgainOnceThereIsALan() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val source = JoinsWifi(ScanEvent.Vpn)
        val vm = SickTvViewModel(source, clock = { 0L })
        try {
            with(TestScope(dispatcher)) {
                runCurrent()
                assertTrue(vm.state.value.scan.vpn)
                assertFalse(vm.state.value.scan.offWifi)

                source.lan.tryEmit(Unit)
                runCurrent()
                assertEquals(2, source.scans)
                assertFalse(vm.state.value.scan.vpn)
            }
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test fun joiningWifiScansAgain() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val source = JoinsWifi()
        val vm = SickTvViewModel(source, clock = { 0L })
        try {
            with(TestScope(dispatcher)) {
                runCurrent()
                assertTrue(vm.state.value.scan.noLan)

                source.lan.tryEmit(Unit)
                runCurrent()
                assertEquals(2, source.scans)
                assertFalse(vm.state.value.scan.noLan)

                // Already on the LAN: a network change doesn't restart the scan.
                source.lan.tryEmit(Unit)
                runCurrent()
                assertEquals(2, source.scans)
            }
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test fun anEmptyScanTriesAgainEvery10Seconds() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val source = JoinsWifi()
        val vm = SickTvViewModel(source, clock = { 0L })
        try {
            with(TestScope(dispatcher)) {
                runCurrent()
                assertEquals(1, source.scans)
                advanceTimeBy(10_001)
                assertEquals(2, source.scans)
                advanceTimeBy(10_001)
                assertEquals(3, source.scans)
            }
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}
