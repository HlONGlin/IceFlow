package com.iceflow.core

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iceflow.IceFlowApp
import com.iceflow.data.model.*
import com.iceflow.service.IceFlowVpnService
import com.iceflow.service.VpnController
import com.iceflow.service.VpnState
import com.iceflow.service.VpnErrorType
import com.iceflow.service.VpnErrorNotifier
import java.io.Closeable
import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VpnLifecycleTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val container get() = (context.applicationContext as IceFlowApp).container
    private lateinit var originalStore: ServerStore
    private lateinit var originalSettings: AppSettings
    private lateinit var upstream: SocksFixture

    @Before fun prepare() {
        shell("appops set ${context.packageName} ACTIVATE_VPN allow")
        assertNull("Grant ACTIVATE_VPN to the test app before running lifecycle tests", VpnService.prepare(context))
        originalStore = container.servers.store.value
        originalSettings = container.settings.settings.value
        stopAndWait()
        upstream = SocksFixture()
        val profile = ServerProfile(name = "Lifecycle fixture", protocol = Protocol.SOCKS, address = "127.0.0.1", port = upstream.port)
        container.servers.replace(ServerStore(servers = listOf(profile), selectedId = profile.id))
        container.settings.update { AppSettings(
            socksEnabled = false,
            appRoutingEnabled = true,
            bypassApps = listOf("com.android.shell"),
            routingRules = listOf(
                RoutingRule(name = "Fixture domain", action = RouteAction.PROXY, domains = listOf("full:probe.invalid")),
                // Keep the emulator's TCP ADB control connection on the physical LAN.
                RoutingRule(name = "Control network", ips = listOf("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16")),
            ),
            testUrl = "http://probe.invalid/generate_204",
        ) }
    }

    @After fun restore() {
        try {
            stopAndWait()
        } finally {
            if (::upstream.isInitialized) upstream.close()
            if (::originalStore.isInitialized) container.servers.replace(originalStore)
            if (::originalSettings.isInitialized) container.settings.update { originalSettings }
        }
    }

    @Test fun disabledPublicSocksStillForwardsRealTunTrafficAndCurrentConnectionProbe() {
        val session = connect()
        assertTrue(ConnectionProbe.measure(session.socksPort, username = session.tunnelUser, password = session.tunnelPass, url = "http://probe.invalid/generate_204") > 0)
        val output = probeTun("198.51.100.1")
        assertTrue("Traffic sent by another UID did not traverse the TUN: $output", output.contains("iceflow-tun-ok"))
        assertTrue(upstream.destinations.any { it == "198.51.100.1:80" })
        val ipv6 = probeTun("2001:db8::1")
        assertTrue("IPv6 did not traverse the VPN: $ipv6; destinations=${upstream.destinations}; errors=${upstream.errors}", ipv6.contains("iceflow-tun-ok"))
        await { VpnController.traffic.value.upTotal > 0 && VpnController.traffic.value.downTotal > 0 }
        assertTrue("Published traffic must include sampled history", VpnController.traffic.value.history.isNotEmpty())
    }

    @Test fun appAllowlistRoutesTheSelectedPackageThroughTun() {
        container.settings.update { it.copy(appProxyMode = true, bypassApps = listOf(instrumentation.context.packageName)) }
        connect()
        val output = probeTun("198.51.100.1")
        assertTrue("The allowlisted test UID did not traverse TUN: $output", output.contains("iceflow-tun-ok"))
        assertTrue(upstream.destinations.any { it == "198.51.100.1:80" })
    }

    @Test fun connectedNotificationDisplaysNodeAndItsStopActionReleasesVpn() {
        connect()
        val manager = context.getSystemService(NotificationManager::class.java)
        await { manager.activeNotifications.any { it.id == 1 } }
        val notification = manager.activeNotifications.single { it.id == 1 }.notification
        assertEquals(context.getString(com.iceflow.R.string.app_name), notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertTrue(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("Lifecycle fixture"))
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        val stop = notification.actions.single { it.title.toString() == context.getString(com.iceflow.R.string.notification_disconnect) }
        stop.actionIntent.send()
        await { VpnController.state.value == VpnState.Idle && !CoreVariantBridge.isCoreRunning() }
        await { manager.activeNotifications.none { it.id == 1 } }
        assertEquals(0L, VpnController.traffic.value.upTotal)
        assertEquals(0L, VpnController.traffic.value.downTotal)
    }

    @Test fun missingNodePublishesActionableErrorNotificationWithoutConnectedNotification() {
        container.servers.replace(ServerStore())
        assertTrue(VpnController.start(context))
        await { VpnController.state.value is VpnState.Error }
        val error = (VpnController.state.value as VpnState.Error).presentation
        assertEquals(VpnErrorType.CONFIGURATION_OR_SUBSCRIPTION, error.type)
        val manager = context.getSystemService(NotificationManager::class.java)
        await { manager.activeNotifications.any { it.id == 2 } }
        val notification = manager.activeNotifications.single { it.id == 2 }.notification
        assertEquals(error.message, notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        assertTrue(notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains(error.nextAction))
        assertEquals(error.actionLabel, notification.actions.single().title.toString())
        assertNotNull(notification.actions.single().actionIntent)
        await { manager.activeNotifications.none { it.id == 1 } }
        assertFalse(CoreVariantBridge.isCoreRunning())
        VpnErrorNotifier.dismiss(context)
    }

    @Test fun quickSettingsTileStopsTheRunningVpn() {
        val component = "${context.packageName}/com.iceflow.service.IceFlowTileService"
        val wasAdded = shell("settings get secure sysui_qs_tiles").contains(component)
        connect()
        try {
            shell("cmd statusbar add-tile $component")
            shell("cmd statusbar expand-settings")
            // TileService must be bound by SystemUI before click-tile can dispatch its callback.
            await { shell("dumpsys activity services $component").contains("IceFlowTileService") }
            shell("cmd statusbar click-tile $component")
            await { VpnController.state.value == VpnState.Idle && !CoreVariantBridge.isCoreRunning() }
        } finally {
            shell("cmd statusbar collapse")
            if (!wasAdded) shell("cmd statusbar remove-tile $component")
        }
    }

    @Test fun physicalNetworkLossKeepsTunAndReconnectsWhenNetworkReturns() {
        assumeTrue("Network toggles require a local ADB transport", InstrumentationRegistry.getArguments().getString("networkToggle") == "true")
        val wifi = shell("settings get global wifi_on").trim() == "1"
        val mobile = shell("settings get global mobile_data").trim() == "1"
        assumeTrue("This device has no toggleable physical network", wifi || mobile)
        val first = connect()
        try {
            if (mobile) shell("svc data disable")
            if (wifi) shell("svc wifi disable")
            await { VpnController.state.value == VpnState.WaitingNetwork && !CoreVariantBridge.isHealthy() }
            assertTrue("TUN must remain established while offline", shell("ip addr").contains("172.19.0.1"))
            if (wifi) shell("svc wifi enable")
            if (mobile) shell("svc data enable")
            await(30_000) { val state = VpnController.state.value; state is VpnState.Connected && state != first }
            val restored = VpnController.state.value as VpnState.Connected
            assertTrue(ConnectionProbe.measure(restored.socksPort, username = restored.tunnelUser, password = restored.tunnelPass, url = "http://probe.invalid/generate_204") > 0)
        } finally {
            if (wifi) shell("svc wifi enable")
            if (mobile) shell("svc data enable")
        }
    }

    @Test fun publicSocksAuthenticationAndUdpSettingsDoNotBreakVpnAndApplyAutomatically() {
        val first = connect()
        val publicPort = ServerSocket(0).use { it.localPort }
        container.settings.update { it.copy(socksEnabled = true, socksPort = publicPort, socksUsername = "user", socksPassword = "secret", socksUdp = false) }
        val second = awaitConnectionAfter(first)
        assertNotEquals(publicPort, second.socksPort)
        assertTrue(ConnectionProbe.measure(second.socksPort, username = second.tunnelUser, password = second.tunnelPass, url = "http://probe.invalid/generate_204") > 0)
        Socket("127.0.0.1", publicPort).use { socket ->
            socket.soTimeout = 3000
            socket.getOutputStream().write(byteArrayOf(5, 1, 0))
            val input = DataInputStream(socket.getInputStream())
            assertEquals(5, input.readUnsignedByte())
            val method = input.readUnsignedByte()
            // Xray rejects the offered no-auth method; mihomo requests credentials instead.
            assertTrue("Public listener must never select unauthenticated access", method == 255 || method == 2)
            if (method == 2) {
                socket.getOutputStream().write(byteArrayOf(1, 4, *"fail".toByteArray(), 4, *"nope".toByteArray()))
                assertEquals(1, input.readUnsignedByte())
                assertNotEquals("Public listener accepted incorrect credentials", 0, input.readUnsignedByte())
            }
        }
        assertTrue("Authenticated public SOCKS requests must reach the selected outbound",
            ConnectionProbe.measure(publicPort, username = "user", password = "secret", url = "http://probe.invalid/generate_204") > 0)
        container.settings.update { it.copy(dnsProxyEnabled = false, ipv6Enabled = false) }
        val third = awaitConnectionAfter(second)
        assertTrue(ConnectionProbe.measure(third.socksPort, username = third.tunnelUser, password = third.tunnelPass, url = "http://probe.invalid/generate_204") > 0)
        val stable = VpnController.state.value
        container.settings.update { it.copy(theme = AppTheme.MINT_GREEN) }
        SystemClock.sleep(800)
        assertEquals("Appearance changes must not restart VPN", stable, VpnController.state.value)
    }

    @Test fun editingConnectedNodeEndpointReplacesTheCoreConfiguration() {
        val first = connect()
        SocksFixture().use { replacement ->
            val selected = requireNotNull(container.servers.selected)
            container.servers.updateServer(selected.copy(port = replacement.port))
            val second = awaitConnectionAfter(first)
            val previousRequests = upstream.destinations.size
            assertTrue(ConnectionProbe.measure(second.socksPort, username = second.tunnelUser, password = second.tunnelPass,
                url = "http://probe.invalid/generate_204") > 0)
            assertTrue("Requests still use the old endpoint after the selected node was edited",
                replacement.destinations.contains("probe.invalid:80"))
            assertEquals("Old endpoint received a new request after the edit", previousRequests, upstream.destinations.size)
            stopAndWait()
        }
    }

    @Test fun exitedCoreIsDetectedAndRecoveredAndUserStopCancelsRecovery() {
        val first = connect()
        CoreVariantBridge.stopTun()
        val recovered = awaitConnectionAfter(first)
        assertTrue(CoreVariantBridge.isHealthy())
        assertTrue(ConnectionProbe.measure(recovered.socksPort, username = recovered.tunnelUser, password = recovered.tunnelPass, url = "http://probe.invalid/generate_204") > 0)
        CoreVariantBridge.stop()
        await { VpnController.state.value == VpnState.Connecting }
        stopAndWait()
        SystemClock.sleep(2500)
        assertEquals(VpnState.Idle, VpnController.state.value)
        assertFalse(CoreVariantBridge.isHealthy())
        assertFalse(CoreVariantBridge.isCoreRunning())
    }

    @Test fun queuedSwitchFollowedByStopCannotRestartTheVpn() {
        connect()
        repeat(3) { VpnController.switch(context) }
        stopAndWait()
        SystemClock.sleep(1800)
        assertEquals(VpnState.Idle, VpnController.state.value)
        assertFalse(CoreVariantBridge.isHealthy())
    }

    private fun connect(): VpnState.Connected {
        VpnController.start(context)
        await { VpnController.state.value is VpnState.Connected }
        // Let initial physical-network notifications settle before taking a session identity.
        SystemClock.sleep(900)
        await { VpnController.state.value is VpnState.Connected }
        return VpnController.state.value as VpnState.Connected
    }

    private fun awaitConnectionAfter(previous: VpnState.Connected): VpnState.Connected {
        await { val state = VpnController.state.value; state is VpnState.Connected && state != previous }
        return VpnController.state.value as VpnState.Connected
    }

    @Suppress("DEPRECATION")
    private fun stopAndWait() {
        VpnController.stop(context)
        await {
            VpnController.state.value == VpnState.Idle && !CoreVariantBridge.isHealthy() && !CoreVariantBridge.isCoreRunning() &&
                context.getSystemService(ActivityManager::class.java).getRunningServices(Int.MAX_VALUE)
                    .none { it.service.className == IceFlowVpnService::class.java.name }
        }
    }

    private fun await(timeout: Long = 15_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (!condition()) {
            check(SystemClock.elapsedRealtime() < deadline) { "Timed out; VPN state=${VpnController.state.value}" }
            SystemClock.sleep(50)
        }
    }

    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use {
        ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().use { reader -> reader.readText() }
    }

    private fun probeTun(host: String): String {
        val done = CountDownLatch(1)
        var response = ""
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                response = resultData?.getString("response").orEmpty()
                done.countDown()
            }
        }
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) = Unit
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        check(context.bindService(Intent().setAction("probe:$host:${System.nanoTime()}")
            .setClassName(instrumentation.context.packageName, TunProbeService::class.java.name)
            .putExtra("host", host).putExtra("receiver", receiver), connection, Context.BIND_AUTO_CREATE))
        return try {
            check(done.await(10, TimeUnit.SECONDS)) { "TUN probe service did not respond" }
            response
        } finally { context.unbindService(connection) }
    }

    private class SocksFixture : Closeable {
        private val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        private val closed = AtomicBoolean(false)
        private val clients = CopyOnWriteArrayList<Socket>()
        val destinations = CopyOnWriteArrayList<String>()
        val errors = CopyOnWriteArrayList<String>()
        val port get() = server.localPort
        private val worker = thread(isDaemon = true, name = "socks-fixture") {
            while (!closed.get()) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                clients += socket
                thread(isDaemon = true) {
                    try {
                        socket.use {
                            socket.soTimeout = 5000
                            val input = DataInputStream(socket.getInputStream())
                            if (input.readUnsignedByte() != 5) return@thread
                            input.readFully(ByteArray(input.readUnsignedByte()))
                            val output = socket.getOutputStream()
                            output.write(byteArrayOf(5, 0))
                            val version = input.readUnsignedByte()
                            val command = input.readUnsignedByte()
                            check(version == 5 && command == 1) { "SOCKS version=$version command=$command" }
                            input.readUnsignedByte()
                            val host = when (input.readUnsignedByte()) {
                                1 -> ByteArray(4).also { input.readFully(it) }.joinToString(".") { (it.toInt() and 255).toString() }
                                3 -> ByteArray(input.readUnsignedByte()).also { input.readFully(it) }.toString(Charsets.US_ASCII)
                                4 -> InetAddress.getByAddress(ByteArray(16).also { input.readFully(it) }).hostAddress
                                else -> error("Unexpected address type")
                            }
                            destinations += "$host:${input.readUnsignedShort()}"
                            output.write(byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, 0, 0))
                            val reader = input.bufferedReader(Charsets.US_ASCII)
                            val request = reader.readLine().orEmpty()
                            while (!reader.readLine().isNullOrEmpty()) { }
                            val response = if (request.contains("/generate_204")) "HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n"
                            else "HTTP/1.1 200 OK\r\nContent-Length: 14\r\nConnection: close\r\n\r\niceflow-tun-ok"
                            output.write(response.toByteArray(Charsets.US_ASCII))
                            output.flush()
                        }
                    } catch (error: Exception) { if (!closed.get()) errors += error.toString() }
                    finally { clients -= socket }
                }
            }
        }

        override fun close() {
            closed.set(true)
            server.close()
            clients.forEach { runCatching { it.close() } }
            worker.join(2000)
        }
    }
}
