/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.net.cts

import android.Manifest.permission.NETWORK_SETTINGS
import android.app.Instrumentation
import android.content.Context
import android.net.ConnectivityManager
import android.net.EthernetNetworkSpecifier
import android.net.INetworkAgent
import android.net.INetworkAgentRegistry
import android.net.InetAddresses
import android.net.IpPrefix
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.NattKeepalivePacketData
import android.net.Network
import android.net.NetworkAgent
import android.net.NetworkAgentConfig
import android.net.NetworkAgent.INVALID_NETWORK
import android.net.NetworkAgent.VALID_NETWORK
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN
import android.net.NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED
import android.net.NetworkCapabilities.NET_CAPABILITY_TRUSTED
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_TEST
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.NetworkCapabilities.TRANSPORT_VPN
import android.net.NetworkInfo
import android.net.NetworkProvider
import android.net.NetworkReleasedException
import android.net.NetworkRequest
import android.net.NetworkScore
import android.net.RouteInfo
import android.net.QosCallback
import android.net.QosCallbackException
import android.net.QosCallback.QosCallbackRegistrationException
import android.net.QosSession
import android.net.QosSessionAttributes
import android.net.QosSocketInfo
import android.net.SocketKeepalive
import android.net.Uri
import android.net.VpnManager
import android.net.VpnTransportInfo
import android.net.cts.NetworkAgentTest.TestableQosCallback.CallbackEntry.OnError
import android.net.cts.NetworkAgentTest.TestableQosCallback.CallbackEntry.OnQosSessionAvailable
import android.net.cts.NetworkAgentTest.TestableQosCallback.CallbackEntry.OnQosSessionLost
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.os.SystemClock
import android.platform.test.annotations.AppModeFull
import android.telephony.TelephonyManager
import android.telephony.data.EpsBearerQosSessionAttributes
import android.util.DebugUtils.valueToString
import androidx.test.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.ThrowingSupplier
import com.android.modules.utils.build.SdkLevel
import com.android.net.module.util.ArrayTrackRecord
import com.android.testutils.CompatUtil
import com.android.testutils.ConnectivityModuleTest
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo
import com.android.testutils.DevSdkIgnoreRunner
import com.android.testutils.RecorderCallback.CallbackEntry.Available
import com.android.testutils.RecorderCallback.CallbackEntry.BlockedStatus
import com.android.testutils.RecorderCallback.CallbackEntry.LinkPropertiesChanged
import com.android.testutils.RecorderCallback.CallbackEntry.Losing
import com.android.testutils.RecorderCallback.CallbackEntry.Lost
import com.android.testutils.TestableNetworkAgent
import com.android.testutils.TestableNetworkAgent.CallbackEntry.OnAddKeepalivePacketFilter
import com.android.testutils.TestableNetworkAgent.CallbackEntry.OnAutomaticReconnectDisabled
import com.android.testutils.TestableNetworkAgent.CallbackEntry.OnBandwidthUpdateRequested
import com.android.testutils.TestableNetworkAgent.CallbackEntry.OnNetworkCreated
import com.android.testutils.TestableNetworkAgent.CallbackEntry.OnNetworkDestroyed
import com.android.testutils.TestableNetworkAgent.CallbackEntry.OnNetworkUnwanted
import com.android.testutils.TestableNetworkAgent.CallbackEntry.OnRegisterQosCallback
import com.android.testutils.TestableNetworkAgent.CallbackEntry.OnRemoveKeepalivePacketFilter
import com.android.testutils.TestableNetworkAgent.CallbackEntry.OnSaveAcceptUnvalidated
import com.android.testutils.TestableNetworkAgent.CallbackEntry.OnStartSocketKeepalive
import com.android.testutils.TestableNetworkAgent.CallbackEntry.OnStopSocketKeepalive
import com.android.testutils.TestableNetworkAgent.CallbackEntry.OnUnregisterQosCallback
import com.android.testutils.TestableNetworkAgent.CallbackEntry.OnValidationStatus
import com.android.testutils.TestableNetworkCallback
import com.android.testutils.assertThrows
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.argThat
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import java.io.IOException
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.time.Duration
import java.util.Arrays
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

// This test doesn't really have a constraint on how fast the methods should return. If it's
// going to fail, it will simply wait forever, so setting a high timeout lowers the flake ratio
// without affecting the run time of successful runs. Thus, set a very high timeout.
private const val DEFAULT_TIMEOUT_MS = 5000L
// When waiting for a NetworkCallback to determine there was no timeout, waiting is the
// only possible thing (the relevant handler is the one in the real ConnectivityService,
// and then there is the Binder call), so have a short timeout for this as it will be
// exhausted every time.
private const val NO_CALLBACK_TIMEOUT = 200L
private const val WORSE_NETWORK_SCORE = 65
private const val BETTER_NETWORK_SCORE = 75
private const val FAKE_NET_ID = 1098
private val instrumentation: Instrumentation
    get() = InstrumentationRegistry.getInstrumentation()
private val realContext: Context
    get() = InstrumentationRegistry.getContext()
private fun Message(what: Int, arg1: Int, arg2: Int, obj: Any?) = Message.obtain().also {
    it.what = what
    it.arg1 = arg1
    it.arg2 = arg2
    it.obj = obj
}

@RunWith(DevSdkIgnoreRunner::class)
// NetworkAgent is not updatable in R-, so this test does not need to be compatible with older
// versions. NetworkAgent was also based on AsyncChannel before S so cannot be tested the same way.
@IgnoreUpTo(Build.VERSION_CODES.R)
// NetworkAgent is updated as part of the connectivity module, and running NetworkAgent tests in MTS
// for modules other than Connectivity does not provide much value. Only run them in connectivity
// module MTS, so the tests only need to cover the case of an updated NetworkAgent.
@ConnectivityModuleTest
class NetworkAgentTest {
    private val LOCAL_IPV4_ADDRESS = InetAddresses.parseNumericAddress("192.0.2.1")
    private val REMOTE_IPV4_ADDRESS = InetAddresses.parseNumericAddress("192.0.2.2")

    private val mCM = realContext.getSystemService(ConnectivityManager::class.java)!!
    private val mHandlerThread = HandlerThread("${javaClass.simpleName} handler thread")
    private val mFakeConnectivityService = FakeConnectivityService()
    private val agentsToCleanUp = mutableListOf<NetworkAgent>()
    private val callbacksToCleanUp = mutableListOf<TestableNetworkCallback>()
    private var qosTestSocket: Socket? = null

    @Before
    fun setUp() {
        instrumentation.getUiAutomation().adoptShellPermissionIdentity()
        mHandlerThread.start()
    }

    @After
    fun tearDown() {
        agentsToCleanUp.forEach { it.unregister() }
        callbacksToCleanUp.forEach { mCM.unregisterNetworkCallback(it) }
        qosTestSocket?.close()
        mHandlerThread.quitSafely()
        instrumentation.getUiAutomation().dropShellPermissionIdentity()
    }

    /**
     * A fake that helps simulating ConnectivityService talking to a harnessed agent.
     * This fake only supports speaking to one harnessed agent at a time because it
     * only keeps track of one async channel.
     */
    private class FakeConnectivityService {
        val mockRegistry = mock(INetworkAgentRegistry::class.java)
        private var agentField: INetworkAgent? = null
        private val registry = object : INetworkAgentRegistry.Stub(),
                INetworkAgentRegistry by mockRegistry {
            // asBinder has implementations in both INetworkAgentRegistry.Stub and mockRegistry, so
            // it needs to be disambiguated. Just fail the test as it should be unused here.
            // asBinder is used when sending the registry in binder transactions, so not in this
            // test (the test just uses in-process direct calls). If it were used across processes,
            // using the Stub super.asBinder() implementation would allow sending the registry in
            // binder transactions, while recording incoming calls on the other mockito-generated
            // methods.
            override fun asBinder() = fail("asBinder should be unused in this test")
        }

        val agent: INetworkAgent
            get() = agentField ?: fail("No INetworkAgent")

        fun connect(agent: INetworkAgent) {
            this.agentField = agent
            agent.onRegistered(registry)
        }

        fun disconnect() = agent.onDisconnected()
    }

    private fun requestNetwork(request: NetworkRequest, callback: TestableNetworkCallback) {
        mCM.requestNetwork(request, callback)
        callbacksToCleanUp.add(callback)
    }

    private fun registerNetworkCallback(
        request: NetworkRequest,
        callback: TestableNetworkCallback
    ) {
        mCM.registerNetworkCallback(request, callback)
        callbacksToCleanUp.add(callback)
    }

    private fun registerBestMatchingNetworkCallback(
        request: NetworkRequest,
        callback: TestableNetworkCallback,
        handler: Handler
    ) {
        mCM!!.registerBestMatchingNetworkCallback(request, callback, handler)
        callbacksToCleanUp.add(callback)
    }

    private fun makeTestNetworkRequest(specifier: String? = null): NetworkRequest {
        return NetworkRequest.Builder()
                .clearCapabilities()
                .addTransportType(TRANSPORT_TEST)
                .also {
                    if (specifier != null) {
                        it.setNetworkSpecifier(CompatUtil.makeEthernetNetworkSpecifier(specifier))
                    }
                }
                .build()
    }

    private fun makeTestNetworkCapabilities(
        specifier: String? = null,
        transports: IntArray = intArrayOf()
    ) = NetworkCapabilities().apply {
        addTransportType(TRANSPORT_TEST)
        removeCapability(NET_CAPABILITY_TRUSTED)
        removeCapability(NET_CAPABILITY_INTERNET)
        addCapability(NET_CAPABILITY_NOT_SUSPENDED)
        addCapability(NET_CAPABILITY_NOT_ROAMING)
        addCapability(NET_CAPABILITY_NOT_VPN)
        if (SdkLevel.isAtLeastS()) {
            addCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
        }
        if (null != specifier) {
            setNetworkSpecifier(CompatUtil.makeEthernetNetworkSpecifier(specifier))
        }
        for (t in transports) { addTransportType(t) }
        // Most transports are not allowed on test networks unless the network is marked restricted.
        // This test does not need
        if (transports.size > 0) removeCapability(NET_CAPABILITY_NOT_RESTRICTED)
    }

    private fun createNetworkAgent(
        context: Context = realContext,
        specifier: String? = null,
        initialNc: NetworkCapabilities? = null,
        initialLp: LinkProperties? = null,
        initialConfig: NetworkAgentConfig? = null
    ): TestableNetworkAgent {
        val nc = initialNc ?: makeTestNetworkCapabilities(specifier)
        val lp = initialLp ?: LinkProperties().apply {
            addLinkAddress(LinkAddress(LOCAL_IPV4_ADDRESS, 32))
            addRoute(RouteInfo(IpPrefix("0.0.0.0/0"), null, null))
        }
        val config = initialConfig ?: NetworkAgentConfig.Builder().build()
        return TestableNetworkAgent(context, mHandlerThread.looper, nc, lp, config).also {
            agentsToCleanUp.add(it)
        }
    }

    private fun createConnectedNetworkAgent(
        context: Context = realContext,
        specifier: String? = UUID.randomUUID().toString(),
        initialConfig: NetworkAgentConfig? = null,
        expectedInitSignalStrengthThresholds: IntArray? = intArrayOf(),
        transports: IntArray = intArrayOf()
    ): Pair<TestableNetworkAgent, TestableNetworkCallback> {
        val callback = TestableNetworkCallback()
        // Ensure this NetworkAgent is never unneeded by filing a request with its specifier.
        requestNetwork(makeTestNetworkRequest(specifier = specifier), callback)
        val nc = makeTestNetworkCapabilities(specifier, transports)
        val agent = createNetworkAgent(context, initialConfig = initialConfig, initialNc = nc)
        agent.setTeardownDelayMillis(0)
        // Connect the agent and verify initial status callbacks.
        agent.register()
        agent.markConnected()
        agent.expectCallback<OnNetworkCreated>()
        agent.expectSignalStrengths(expectedInitSignalStrengthThresholds)
        agent.expectValidationBypassedStatus()
        callback.expectAvailableThenValidatedCallbacks(agent.network!!)
        return agent to callback
    }

    private fun connectNetwork(vararg transports: Int): Pair<TestableNetworkAgent, Network> {
        val (agent, callback) = createConnectedNetworkAgent(transports = transports)
        val network = agent.network!!
        // createConnectedNetworkAgent internally files a request; release it so that the network
        // will be torn down if unneeded.
        mCM.unregisterNetworkCallback(callback)
        return agent to network
    }

    private fun createNetworkAgentWithFakeCS() = createNetworkAgent().also {
        mFakeConnectivityService.connect(it.registerForTest(Network(FAKE_NET_ID)))
    }

    @Test
    fun testSetSubtypeNameAndExtraInfoByAgentConfig() {
        val subtypeLTE = TelephonyManager.NETWORK_TYPE_LTE
        val subtypeNameLTE = "LTE"
        val legacyExtraInfo = "mylegacyExtraInfo"
        val config = NetworkAgentConfig.Builder()
                .setLegacySubType(subtypeLTE)
                .setLegacySubTypeName(subtypeNameLTE)
                .setLegacyExtraInfo(legacyExtraInfo).build()
        val (agent, callback) = createConnectedNetworkAgent(initialConfig = config)
        val networkInfo = mCM.getNetworkInfo(agent.network)
        assertEquals(subtypeLTE, networkInfo.getSubtype())
        assertEquals(subtypeNameLTE, networkInfo.getSubtypeName())
        assertEquals(legacyExtraInfo, config.getLegacyExtraInfo())
    }

    @Test
    fun testSetLegacySubtypeInNetworkAgent() {
        val subtypeLTE = TelephonyManager.NETWORK_TYPE_LTE
        val subtypeUMTS = TelephonyManager.NETWORK_TYPE_UMTS
        val subtypeNameLTE = "LTE"
        val subtypeNameUMTS = "UMTS"
        val config = NetworkAgentConfig.Builder()
                .setLegacySubType(subtypeLTE)
                .setLegacySubTypeName(subtypeNameLTE).build()
        val (agent, callback) = createConnectedNetworkAgent(initialConfig = config)
            agent.setLegacySubtype(subtypeUMTS, subtypeNameUMTS)

            // There is no callback when networkInfo changes,
            // so use the NetworkCapabilities callback to ensure
            // that networkInfo is ready for verification.
            val nc = NetworkCapabilities(agent.nc)
            nc.addCapability(NET_CAPABILITY_NOT_METERED)
            agent.sendNetworkCapabilities(nc)
            callback.expectCapabilitiesThat(agent.network) {
                it.hasCapability(NET_CAPABILITY_NOT_METERED)
            }
            val networkInfo = mCM.getNetworkInfo(agent.network)
            assertEquals(subtypeUMTS, networkInfo.getSubtype())
            assertEquals(subtypeNameUMTS, networkInfo.getSubtypeName())
    }

    @Test
    fun testConnectAndUnregister() {
        val (agent, callback) = createConnectedNetworkAgent()
        unregister(agent)
        callback.expectCallback<Lost>(agent.network!!)
        assertFailsWith<IllegalStateException>("Must not be able to register an agent twice") {
            agent.register()
        }
    }

    @Test
    fun testOnBandwidthUpdateRequested() {
        val (agent, _) = createConnectedNetworkAgent()
        mCM.requestBandwidthUpdate(agent.network!!)
        agent.expectCallback<OnBandwidthUpdateRequested>()
        unregister(agent)
    }

    @Test
    fun testSignalStrengthThresholds() {
        val thresholds = intArrayOf(30, 50, 65)
        val callbacks = thresholds.map { strength ->
            val request = NetworkRequest.Builder()
                    .clearCapabilities()
                    .addTransportType(TRANSPORT_TEST)
                    .setSignalStrength(strength)
                    .build()
            TestableNetworkCallback(DEFAULT_TIMEOUT_MS).also {
                registerNetworkCallback(request, it)
            }
        }
        createConnectedNetworkAgent(expectedInitSignalStrengthThresholds = thresholds).let {
            (agent, callback) ->
            // Send signal strength and check that the callbacks are called appropriately.
            val nc = NetworkCapabilities(agent.nc)
            nc.setSignalStrength(20)
            agent.sendNetworkCapabilities(nc)
            callbacks.forEach { it.assertNoCallback(NO_CALLBACK_TIMEOUT) }

            nc.setSignalStrength(40)
            agent.sendNetworkCapabilities(nc)
            callbacks[0].expectAvailableCallbacks(agent.network!!)
            callbacks[1].assertNoCallback(NO_CALLBACK_TIMEOUT)
            callbacks[2].assertNoCallback(NO_CALLBACK_TIMEOUT)

            nc.setSignalStrength(80)
            agent.sendNetworkCapabilities(nc)
            callbacks[0].expectCapabilitiesThat(agent.network!!) { it.signalStrength == 80 }
            callbacks[1].expectAvailableCallbacks(agent.network!!)
            callbacks[2].expectAvailableCallbacks(agent.network!!)

            nc.setSignalStrength(55)
            agent.sendNetworkCapabilities(nc)
            callbacks[0].expectCapabilitiesThat(agent.network!!) { it.signalStrength == 55 }
            callbacks[1].expectCapabilitiesThat(agent.network!!) { it.signalStrength == 55 }
            callbacks[2].expectCallback<Lost>(agent.network!!)
        }
        callbacks.forEach {
            mCM.unregisterNetworkCallback(it)
        }
    }

    @Test
    fun testSocketKeepalive(): Unit = createNetworkAgentWithFakeCS().let { agent ->
        val packet = NattKeepalivePacketData(
                LOCAL_IPV4_ADDRESS /* srcAddress */, 1234 /* srcPort */,
                REMOTE_IPV4_ADDRESS /* dstAddress */, 4567 /* dstPort */,
                ByteArray(100 /* size */))
        val slot = 4
        val interval = 37

        mFakeConnectivityService.agent.onAddNattKeepalivePacketFilter(slot, packet)
        mFakeConnectivityService.agent.onStartNattSocketKeepalive(slot, interval, packet)

        agent.expectCallback<OnAddKeepalivePacketFilter>().let {
            assertEquals(it.slot, slot)
            assertEquals(it.packet, packet)
        }
        agent.expectCallback<OnStartSocketKeepalive>().let {
            assertEquals(it.slot, slot)
            assertEquals(it.interval, interval)
            assertEquals(it.packet, packet)
        }

        agent.assertNoCallback()

        // Check that when the agent sends a keepalive event, ConnectivityService receives the
        // expected message.
        agent.sendSocketKeepaliveEvent(slot, SocketKeepalive.ERROR_UNSUPPORTED)
        verify(mFakeConnectivityService.mockRegistry, timeout(DEFAULT_TIMEOUT_MS))
                .sendSocketKeepaliveEvent(slot, SocketKeepalive.ERROR_UNSUPPORTED)

        mFakeConnectivityService.agent.onStopSocketKeepalive(slot)
        mFakeConnectivityService.agent.onRemoveKeepalivePacketFilter(slot)
        agent.expectCallback<OnStopSocketKeepalive>().let {
            assertEquals(it.slot, slot)
        }
        agent.expectCallback<OnRemoveKeepalivePacketFilter>().let {
            assertEquals(it.slot, slot)
        }
    }

    @Test
    fun testSendUpdates(): Unit = createConnectedNetworkAgent().let { (agent, callback) ->
        val ifaceName = "adhocIface"
        val lp = LinkProperties(agent.lp)
        lp.setInterfaceName(ifaceName)
        agent.sendLinkProperties(lp)
        callback.expectLinkPropertiesThat(agent.network!!) {
            it.getInterfaceName() == ifaceName
        }
        val nc = NetworkCapabilities(agent.nc)
        nc.addCapability(NET_CAPABILITY_NOT_METERED)
        agent.sendNetworkCapabilities(nc)
        callback.expectCapabilitiesThat(agent.network!!) {
            it.hasCapability(NET_CAPABILITY_NOT_METERED)
        }
    }

    private fun ncWithAllowedUids(vararg uids: Int) = NetworkCapabilities.Builder()
                .addTransportType(TRANSPORT_TEST)
                .setAllowedUids(uids.toSet()).build()

    @Test
    fun testRejectedUpdates() {
        val callback = TestableNetworkCallback(DEFAULT_TIMEOUT_MS)
        // will be cleaned up in tearDown
        registerNetworkCallback(makeTestNetworkRequest(), callback)
        val agent = createNetworkAgent(initialNc = ncWithAllowedUids(200))
        agent.register()
        agent.markConnected()

        // Make sure the UIDs have been ignored.
        callback.expectCallback<Available>(agent.network!!)
        callback.expectCapabilitiesThat(agent.network!!) {
            it.allowedUids.isEmpty() && !it.hasCapability(NET_CAPABILITY_VALIDATED)
        }
        callback.expectCallback<LinkPropertiesChanged>(agent.network!!)
        callback.expectCallback<BlockedStatus>(agent.network!!)
        callback.expectCapabilitiesThat(agent.network!!) {
            it.allowedUids.isEmpty() && it.hasCapability(NET_CAPABILITY_VALIDATED)
        }
        callback.assertNoCallback(NO_CALLBACK_TIMEOUT)

        // Make sure that the UIDs are also ignored upon update
        agent.sendNetworkCapabilities(ncWithAllowedUids(200, 300))
        callback.assertNoCallback(NO_CALLBACK_TIMEOUT)
    }

    @Test
    fun testSendScore() {
        // This test will create two networks and check that the one with the stronger
        // score wins out for a request that matches them both.

        // File the interesting request
        val callback = TestableNetworkCallback(timeoutMs = DEFAULT_TIMEOUT_MS)
        requestNetwork(makeTestNetworkRequest(), callback)

        // Connect the first Network, with an unused callback that kept the network up.
        val (agent1, _) = createConnectedNetworkAgent()
        callback.expectAvailableThenValidatedCallbacks(agent1.network!!)
        // If using the int ranking, agent1 must be upgraded to a better score so that there is
        // no ambiguity when agent2 connects that agent1 is still better. If using policy
        // ranking, this is not necessary.
        agent1.sendNetworkScore(NetworkScore.Builder().setLegacyInt(BETTER_NETWORK_SCORE)
                .build())

        // Connect the second agent.
        val (agent2, _) = createConnectedNetworkAgent()
        // The callback should not see anything yet. With int ranking, agent1 was upgraded
        // to a stronger score beforehand. With policy ranking, agent1 is preferred by
        // virtue of already satisfying the request.
        callback.assertNoCallback(NO_CALLBACK_TIMEOUT)
        // Now downgrade the score and expect the callback now prefers agent2
        agent1.sendNetworkScore(NetworkScore.Builder()
                .setLegacyInt(WORSE_NETWORK_SCORE)
                .setExiting(true)
                .build())
        callback.expectCallback<Available>(agent2.network!!)

        // tearDown() will unregister the requests and agents
    }

    private fun hasAllTransports(nc: NetworkCapabilities?, transports: IntArray) =
            nc != null && transports.all { nc.hasTransport(it) }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.R)
    fun testSetUnderlyingNetworksAndVpnSpecifier() {
        val mySessionId = "MySession12345"
        val request = NetworkRequest.Builder()
                .addTransportType(TRANSPORT_TEST)
                .addTransportType(TRANSPORT_VPN)
                .removeCapability(NET_CAPABILITY_NOT_VPN)
                .removeCapability(NET_CAPABILITY_TRUSTED) // TODO: add to VPN!
                .build()
        val callback = TestableNetworkCallback(timeoutMs = DEFAULT_TIMEOUT_MS)
        registerNetworkCallback(request, callback)

        val nc = NetworkCapabilities().apply {
            addTransportType(TRANSPORT_TEST)
            addTransportType(TRANSPORT_VPN)
            removeCapability(NET_CAPABILITY_NOT_VPN)
            setTransportInfo(VpnTransportInfo(VpnManager.TYPE_VPN_SERVICE, mySessionId))
            if (SdkLevel.isAtLeastS()) {
                addCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
            }
        }
        val defaultNetwork = mCM.activeNetwork
        assertNotNull(defaultNetwork)
        val defaultNetworkCapabilities = mCM.getNetworkCapabilities(defaultNetwork)
        val defaultNetworkTransports = defaultNetworkCapabilities.transportTypes

        val agent = createNetworkAgent(initialNc = nc)
        agent.register()
        agent.markConnected()
        callback.expectAvailableThenValidatedCallbacks(agent.network!!)

        // Check that the default network's transport is propagated to the VPN.
        var vpnNc = mCM.getNetworkCapabilities(agent.network!!)
        assertNotNull(vpnNc)
        assertEquals(VpnManager.TYPE_VPN_SERVICE,
                (vpnNc.transportInfo as VpnTransportInfo).type)
        assertEquals(mySessionId, (vpnNc.transportInfo as VpnTransportInfo).sessionId)

        val testAndVpn = intArrayOf(TRANSPORT_TEST, TRANSPORT_VPN)
        assertTrue(hasAllTransports(vpnNc, testAndVpn))
        assertFalse(vpnNc.hasCapability(NET_CAPABILITY_NOT_VPN))
        assertTrue(hasAllTransports(vpnNc, defaultNetworkTransports),
                "VPN transports ${Arrays.toString(vpnNc.transportTypes)}" +
                        " lacking transports from ${Arrays.toString(defaultNetworkTransports)}")

        // Check that when no underlying networks are announced the underlying transport disappears.
        agent.setUnderlyingNetworks(listOf<Network>())
        callback.expectCapabilitiesThat(agent.network!!) {
            it.transportTypes.size == 2 && hasAllTransports(it, testAndVpn)
        }

        // Put the underlying network back and check that the underlying transport reappears.
        val expectedTransports = (defaultNetworkTransports.toSet() + TRANSPORT_TEST + TRANSPORT_VPN)
                .toIntArray()
        agent.setUnderlyingNetworks(null)
        callback.expectCapabilitiesThat(agent.network!!) {
            it.transportTypes.size == expectedTransports.size &&
                    hasAllTransports(it, expectedTransports)
        }

        // Check that some underlying capabilities are propagated.
        // This is not very accurate because the test does not control the capabilities of the
        // underlying networks, and because not congested, not roaming, and not suspended are the
        // default anyway. It's still useful as an extra check though.
        vpnNc = mCM.getNetworkCapabilities(agent.network!!)
        for (cap in listOf(NET_CAPABILITY_NOT_CONGESTED,
                NET_CAPABILITY_NOT_ROAMING,
                NET_CAPABILITY_NOT_SUSPENDED)) {
            val capStr = valueToString(NetworkCapabilities::class.java, "NET_CAPABILITY_", cap)
            if (defaultNetworkCapabilities.hasCapability(cap) && !vpnNc.hasCapability(cap)) {
                fail("$capStr not propagated from underlying: $defaultNetworkCapabilities")
            }
        }

        unregister(agent)
        callback.expectCallback<Lost>(agent.network!!)
    }

    private fun unregister(agent: TestableNetworkAgent) {
        agent.unregister()
        agent.eventuallyExpect<OnNetworkUnwanted>()
        agent.eventuallyExpect<OnNetworkDestroyed>()
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.R)
    fun testAgentStartsInConnecting() {
        val mockContext = mock(Context::class.java)
        val mockCm = mock(ConnectivityManager::class.java)
        doReturn(mockCm).`when`(mockContext).getSystemService(Context.CONNECTIVITY_SERVICE)
        val agent = createNetworkAgent(mockContext)
        agent.register()
        verify(mockCm).registerNetworkAgent(any(),
                argThat<NetworkInfo> { it.detailedState == NetworkInfo.DetailedState.CONNECTING },
                any(LinkProperties::class.java),
                any(NetworkCapabilities::class.java),
                any(NetworkScore::class.java),
                any(NetworkAgentConfig::class.java),
                eq(NetworkProvider.ID_NONE))
    }

    @Test
    fun testSetAcceptUnvalidated() {
        createNetworkAgentWithFakeCS().let { agent ->
            mFakeConnectivityService.agent.onSaveAcceptUnvalidated(true)
            agent.expectCallback<OnSaveAcceptUnvalidated>().let {
                assertTrue(it.accept)
            }
            agent.assertNoCallback()
        }
    }

    @Test
    fun testSetAcceptUnvalidatedPreventAutomaticReconnect() {
        createNetworkAgentWithFakeCS().let { agent ->
            mFakeConnectivityService.agent.onSaveAcceptUnvalidated(false)
            mFakeConnectivityService.agent.onPreventAutomaticReconnect()
            agent.expectCallback<OnSaveAcceptUnvalidated>().let {
                assertFalse(it.accept)
            }
            agent.expectCallback<OnAutomaticReconnectDisabled>()
            agent.assertNoCallback()
            // When automatic reconnect is turned off, the network is torn down and
            // ConnectivityService disconnects. As part of the disconnect, ConnectivityService will
            // also send itself a message to unregister the NetworkAgent from its internal
            // structure.
            mFakeConnectivityService.disconnect()
            agent.expectCallback<OnNetworkUnwanted>()
        }
    }

    @Test
    fun testPreventAutomaticReconnect() {
        createNetworkAgentWithFakeCS().let { agent ->
            mFakeConnectivityService.agent.onPreventAutomaticReconnect()
            agent.expectCallback<OnAutomaticReconnectDisabled>()
            agent.assertNoCallback()
            mFakeConnectivityService.disconnect()
            agent.expectCallback<OnNetworkUnwanted>()
        }
    }

    @Test
    fun testValidationStatus() = createNetworkAgentWithFakeCS().let { agent ->
        val uri = Uri.parse("http://www.google.com")
        mFakeConnectivityService.agent.onValidationStatusChanged(VALID_NETWORK,
                uri.toString())
        agent.expectCallback<OnValidationStatus>().let {
            assertEquals(it.status, VALID_NETWORK)
            assertEquals(it.uri, uri)
        }

        mFakeConnectivityService.agent.onValidationStatusChanged(INVALID_NETWORK, null)
        agent.expectCallback<OnValidationStatus>().let {
            assertEquals(it.status, INVALID_NETWORK)
            assertNull(it.uri)
        }
    }

    @Test
    fun testTemporarilyUnmeteredCapability() {
        // This test will create a networks with/without NET_CAPABILITY_TEMPORARILY_NOT_METERED
        // and check that the callback reflects the capability changes.

        // Connect the network
        val (agent, callback) = createConnectedNetworkAgent()

        // Send TEMP_NOT_METERED and check that the callback is called appropriately.
        val nc1 = NetworkCapabilities(agent.nc)
                .addCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED)
        agent.sendNetworkCapabilities(nc1)
        callback.expectCapabilitiesThat(agent.network!!) {
            it.hasCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED)
        }

        // Remove TEMP_NOT_METERED and check that the callback is called appropriately.
        val nc2 = NetworkCapabilities(agent.nc)
                .removeCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED)
        agent.sendNetworkCapabilities(nc2)
        callback.expectCapabilitiesThat(agent.network!!) {
            !it.hasCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED)
        }

        // tearDown() will unregister the requests and agents
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.R)
    fun testSetLingerDuration() {
        // This test will create two networks and check that the one with the stronger
        // score wins out for a request that matches them both. And the weaker agent will
        // be disconnected after customized linger duration.

        // Request the first Network, with a request that could moved to agentStronger in order to
        // make agentWeaker linger later.
        val specifierWeaker = UUID.randomUUID().toString()
        val specifierStronger = UUID.randomUUID().toString()
        val commonCallback = TestableNetworkCallback(timeoutMs = DEFAULT_TIMEOUT_MS)
        requestNetwork(makeTestNetworkRequest(), commonCallback)
        val agentWeaker = createNetworkAgent(specifier = specifierWeaker)
        agentWeaker.register()
        agentWeaker.markConnected()
        commonCallback.expectAvailableThenValidatedCallbacks(agentWeaker.network!!)
        // Downgrade agentWeaker to a worse score so that there is no ambiguity when
        // agentStronger connects.
        agentWeaker.sendNetworkScore(NetworkScore.Builder().setLegacyInt(WORSE_NETWORK_SCORE)
                .setExiting(true).build())

        // Verify invalid linger duration cannot be set.
        assertFailsWith<IllegalArgumentException> {
            agentWeaker.setLingerDuration(Duration.ofMillis(-1))
        }
        assertFailsWith<IllegalArgumentException> { agentWeaker.setLingerDuration(Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> {
            agentWeaker.setLingerDuration(Duration.ofMillis(Integer.MIN_VALUE.toLong()))
        }
        assertFailsWith<IllegalArgumentException> {
            agentWeaker.setLingerDuration(Duration.ofMillis(Integer.MAX_VALUE.toLong() + 1))
        }
        assertFailsWith<IllegalArgumentException> {
            agentWeaker.setLingerDuration(Duration.ofMillis(
                    NetworkAgent.MIN_LINGER_TIMER_MS.toLong() - 1))
        }
        // Verify valid linger timer can be set, but it should not take effect since the network
        // is still needed.
        agentWeaker.setLingerDuration(Duration.ofMillis(Integer.MAX_VALUE.toLong()))
        commonCallback.assertNoCallback(NO_CALLBACK_TIMEOUT)
        // Set to the value we want to verify the functionality.
        agentWeaker.setLingerDuration(Duration.ofMillis(NetworkAgent.MIN_LINGER_TIMER_MS.toLong()))
        // Make a listener which can observe agentWeaker lost later.
        val callbackWeaker = TestableNetworkCallback(timeoutMs = DEFAULT_TIMEOUT_MS)
        registerNetworkCallback(NetworkRequest.Builder()
                .clearCapabilities()
                .addTransportType(TRANSPORT_TEST)
                .setNetworkSpecifier(CompatUtil.makeEthernetNetworkSpecifier(specifierWeaker))
                .build(), callbackWeaker)
        callbackWeaker.expectAvailableCallbacks(agentWeaker.network!!)

        // Connect the agentStronger with a score better than agentWeaker. Verify the callback for
        // agentWeaker sees the linger expiry while the callback for both sees the winner.
        // Record linger start timestamp prior to send score to prevent possible race, the actual
        // timestamp should be slightly late than this since the service handles update
        // network score asynchronously.
        val lingerStart = SystemClock.elapsedRealtime()
        val agentStronger = createNetworkAgent(specifier = specifierStronger)
        agentStronger.register()
        agentStronger.markConnected()
        commonCallback.expectAvailableCallbacks(agentStronger.network!!)
        callbackWeaker.expectCallback<Losing>(agentWeaker.network!!)
        val expectedRemainingLingerDuration = lingerStart +
                NetworkAgent.MIN_LINGER_TIMER_MS.toLong() - SystemClock.elapsedRealtime()
        // If the available callback is too late. The remaining duration will be reduced.
        assertTrue(expectedRemainingLingerDuration > 0,
                "expected remaining linger duration is $expectedRemainingLingerDuration")
        callbackWeaker.assertNoCallback(expectedRemainingLingerDuration)
        callbackWeaker.expectCallback<Lost>(agentWeaker.network!!)
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.R)
    fun testSetSubscriberId() {
        val imsi = UUID.randomUUID().toString()
        val config = NetworkAgentConfig.Builder().setSubscriberId(imsi).build()

        val (agent, _) = createConnectedNetworkAgent(initialConfig = config)
        val snapshots = runWithShellPermissionIdentity(ThrowingSupplier {
                mCM!!.allNetworkStateSnapshots }, NETWORK_SETTINGS)
        val testNetworkSnapshot = snapshots.findLast { it.network == agent.network }
        assertEquals(imsi, testNetworkSnapshot!!.subscriberId)
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.R)
    // TODO: Refactor helper functions to util class and move this test case to
    //  {@link android.net.cts.ConnectivityManagerTest}.
    fun testRegisterBestMatchingNetworkCallback() {
        // Register best matching network callback with additional condition that will be
        // exercised later. This assumes the test network agent has NOT_VCN_MANAGED in it and
        // does not have NET_CAPABILITY_TEMPORARILY_NOT_METERED.
        val bestMatchingCb = TestableNetworkCallback(timeoutMs = DEFAULT_TIMEOUT_MS)
        registerBestMatchingNetworkCallback(NetworkRequest.Builder()
                .clearCapabilities()
                .addTransportType(TRANSPORT_TEST)
                .addCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
                .build(), bestMatchingCb, mHandlerThread.threadHandler)

        val (agent1, _) = createConnectedNetworkAgent(specifier = "AGENT-1")
        bestMatchingCb.expectAvailableThenValidatedCallbacks(agent1.network!!)
        // Make agent1 worse so when agent2 shows up, the callback will see that.
        agent1.sendNetworkScore(NetworkScore.Builder().setExiting(true).build())
        bestMatchingCb.assertNoCallback(NO_CALLBACK_TIMEOUT)

        val (agent2, _) = createConnectedNetworkAgent(specifier = "AGENT-2")
        bestMatchingCb.expectAvailableDoubleValidatedCallbacks(agent2.network!!)

        // Change something on agent1 to trigger capabilities changed, since the callback
        // only cares about the best network, verify it received nothing from agent1.
        val ncAgent1 = agent1.nc
        ncAgent1.addCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED)
        agent1.sendNetworkCapabilities(ncAgent1)
        bestMatchingCb.assertNoCallback(NO_CALLBACK_TIMEOUT)

        // Make agent1 the best network again, verify the callback now tracks agent1.
        agent1.sendNetworkScore(NetworkScore.Builder()
                .setExiting(false).setTransportPrimary(true).build())
        bestMatchingCb.expectAvailableCallbacks(agent1.network!!)

        // Make agent1 temporary vcn managed, which will not satisfying the request.
        // Verify the callback switch from/to the other network accordingly.
        ncAgent1.removeCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
        agent1.sendNetworkCapabilities(ncAgent1)
        bestMatchingCb.expectAvailableCallbacks(agent2.network!!)
        ncAgent1.addCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
        agent1.sendNetworkCapabilities(ncAgent1)
        bestMatchingCb.expectAvailableDoubleValidatedCallbacks(agent1.network!!)

        // Verify the callback doesn't care about agent2 disconnect.
        agent2.unregister()
        agentsToCleanUp.remove(agent2)
        bestMatchingCb.assertNoCallback()
        agent1.unregister()
        agentsToCleanUp.remove(agent1)
        bestMatchingCb.expectCallback<Lost>(agent1.network!!)

        // tearDown() will unregister the requests and agents
    }

    private class TestableQosCallback : QosCallback() {
        val history = ArrayTrackRecord<CallbackEntry>().newReadHead()

        sealed class CallbackEntry {
            data class OnQosSessionAvailable(val sess: QosSession, val attr: QosSessionAttributes)
                : CallbackEntry()
            data class OnQosSessionLost(val sess: QosSession)
                : CallbackEntry()
            data class OnError(val ex: QosCallbackException)
                : CallbackEntry()
        }

        override fun onQosSessionAvailable(sess: QosSession, attr: QosSessionAttributes) {
            history.add(OnQosSessionAvailable(sess, attr))
        }

        override fun onQosSessionLost(sess: QosSession) {
            history.add(OnQosSessionLost(sess))
        }

        override fun onError(ex: QosCallbackException) {
            history.add(OnError(ex))
        }

        inline fun <reified T : CallbackEntry> expectCallback(): T {
            val foundCallback = history.poll(DEFAULT_TIMEOUT_MS)
            assertTrue(foundCallback is T, "Expected ${T::class} but found $foundCallback")
            return foundCallback
        }

        inline fun <reified T : CallbackEntry> expectCallback(valid: (T) -> Boolean) {
            val foundCallback = history.poll(DEFAULT_TIMEOUT_MS)
            assertTrue(foundCallback is T, "Expected ${T::class} but found $foundCallback")
            assertTrue(valid(foundCallback), "Unexpected callback : $foundCallback")
        }

        fun assertNoCallback() {
            assertNull(history.poll(NO_CALLBACK_TIMEOUT),
                    "Callback received")
        }
    }

    private fun setupForQosCallbackTesting(): Pair<TestableNetworkAgent, Socket> {
        val request = NetworkRequest.Builder()
                .clearCapabilities()
                .addTransportType(TRANSPORT_TEST)
                .build()

        val callback = TestableNetworkCallback(timeoutMs = DEFAULT_TIMEOUT_MS)
        requestNetwork(request, callback)
        val (agent, _) = createConnectedNetworkAgent()

        qosTestSocket = assertNotNull(agent.network?.socketFactory?.createSocket()).also {
            it.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
        }
        return Pair(agent, qosTestSocket!!)
    }

    @AppModeFull(reason = "Instant apps don't have permission to bind sockets.")
    @Test
    fun testQosCallbackRegisterWithUnregister() {
        val (agent, socket) = setupForQosCallbackTesting()

        val qosCallback = TestableQosCallback()
        var callbackId = -1
        Executors.newSingleThreadExecutor().let { executor ->
            try {
                val info = QosSocketInfo(agent.network!!, socket)
                mCM.registerQosCallback(info, executor, qosCallback)
                callbackId = agent.expectCallback<OnRegisterQosCallback>().callbackId

                assertFailsWith<QosCallbackRegistrationException>(
                        "The same callback cannot be " +
                        "registered more than once without first being unregistered") {
                    mCM.registerQosCallback(info, executor, qosCallback)
                }
            } finally {
                socket.close()
                mCM.unregisterQosCallback(qosCallback)
                agent.expectCallback<OnUnregisterQosCallback> { it.callbackId == callbackId }
                executor.shutdown()
            }
        }
    }

    @AppModeFull(reason = "Instant apps don't have permission to bind sockets.")
    @Test
    fun testQosCallbackOnQosSession() {
        val (agent, socket) = setupForQosCallbackTesting()
        val qosCallback = TestableQosCallback()
        Executors.newSingleThreadExecutor().let { executor ->
            try {
                val info = QosSocketInfo(agent.network!!, socket)
                assertEquals(agent.network, info.getNetwork())
                mCM.registerQosCallback(info, executor, qosCallback)
                val callbackId = agent.expectCallback<OnRegisterQosCallback>().callbackId

                val uniqueSessionId = 4294967397
                val sessId = 101

                val attributes = createEpsAttributes(5)
                assertEquals(attributes.qosIdentifier, 5)
                agent.sendQosSessionAvailable(callbackId, sessId, attributes)
                qosCallback.expectCallback<OnQosSessionAvailable> {
                            it.sess.sessionId == sessId && it.sess.uniqueId == uniqueSessionId &&
                                it.sess.sessionType == QosSession.TYPE_EPS_BEARER
                        }

                agent.sendQosSessionLost(callbackId, sessId, QosSession.TYPE_EPS_BEARER)
                qosCallback.expectCallback<OnQosSessionLost> {
                            it.sess.sessionId == sessId && it.sess.uniqueId == uniqueSessionId &&
                                it.sess.sessionType == QosSession.TYPE_EPS_BEARER
                        }

                // Make sure that we don't get more qos callbacks
                mCM.unregisterQosCallback(qosCallback)
                agent.expectCallback<OnUnregisterQosCallback>()

                agent.sendQosSessionLost(callbackId, sessId, QosSession.TYPE_EPS_BEARER)
                qosCallback.assertNoCallback()
            } finally {
                socket.close()

                // safety precaution
                mCM.unregisterQosCallback(qosCallback)

                executor.shutdown()
            }
        }
    }

    @AppModeFull(reason = "Instant apps don't have permission to bind sockets.")
    @Test
    fun testQosCallbackOnError() {
        val (agent, socket) = setupForQosCallbackTesting()
        val qosCallback = TestableQosCallback()
        Executors.newSingleThreadExecutor().let { executor ->
            try {
                val info = QosSocketInfo(agent.network!!, socket)
                mCM.registerQosCallback(info, executor, qosCallback)
                val callbackId = agent.expectCallback<OnRegisterQosCallback>().callbackId

                val sessId = 101
                val attributes = createEpsAttributes()

                // Double check that this is wired up and ready to go
                agent.sendQosSessionAvailable(callbackId, sessId, attributes)
                qosCallback.expectCallback<OnQosSessionAvailable>()

                // Check that onError is coming through correctly
                agent.sendQosCallbackError(callbackId,
                        QosCallbackException.EX_TYPE_FILTER_NOT_SUPPORTED)
                qosCallback.expectCallback<OnError> {
                    it.ex.cause is UnsupportedOperationException
                }

                // Ensure that when an error occurs the callback was also unregistered
                agent.sendQosSessionLost(callbackId, sessId, QosSession.TYPE_EPS_BEARER)
                qosCallback.assertNoCallback()
            } finally {
                socket.close()

                // Make sure that the callback is fully unregistered
                mCM.unregisterQosCallback(qosCallback)

                executor.shutdown()
            }
        }
    }

    @AppModeFull(reason = "Instant apps don't have permission to bind sockets.")
    @Test
    fun testQosCallbackIdsAreMappedCorrectly() {
        val (agent, socket) = setupForQosCallbackTesting()
        val qosCallback1 = TestableQosCallback()
        val qosCallback2 = TestableQosCallback()
        Executors.newSingleThreadExecutor().let { executor ->
            try {
                val info = QosSocketInfo(agent.network!!, socket)
                mCM.registerQosCallback(info, executor, qosCallback1)
                val callbackId1 = agent.expectCallback<OnRegisterQosCallback>().callbackId

                mCM.registerQosCallback(info, executor, qosCallback2)
                val callbackId2 = agent.expectCallback<OnRegisterQosCallback>().callbackId

                val sessId1 = 101
                val attributes1 = createEpsAttributes(1)

                // Check #1
                agent.sendQosSessionAvailable(callbackId1, sessId1, attributes1)
                qosCallback1.expectCallback<OnQosSessionAvailable>()
                qosCallback2.assertNoCallback()

                // Check #2
                val sessId2 = 102
                val attributes2 = createEpsAttributes(2)
                agent.sendQosSessionAvailable(callbackId2, sessId2, attributes2)
                qosCallback1.assertNoCallback()
                qosCallback2.expectCallback<OnQosSessionAvailable> { sessId2 == it.sess.sessionId }
            } finally {
                socket.close()

                // Make sure that the callback is fully unregistered
                mCM.unregisterQosCallback(qosCallback1)
                mCM.unregisterQosCallback(qosCallback2)

                executor.shutdown()
            }
        }
    }

    @AppModeFull(reason = "Instant apps don't have permission to bind sockets.")
    @Test
    fun testQosCallbackWhenNetworkReleased() {
        val (agent, socket) = setupForQosCallbackTesting()
        Executors.newSingleThreadExecutor().let { executor ->
            try {
                val qosCallback1 = TestableQosCallback()
                val qosCallback2 = TestableQosCallback()
                try {
                    val info = QosSocketInfo(agent.network!!, socket)
                    mCM.registerQosCallback(info, executor, qosCallback1)
                    mCM.registerQosCallback(info, executor, qosCallback2)
                    agent.unregister()

                    qosCallback1.expectCallback<OnError> {
                        it.ex.cause is NetworkReleasedException
                    }

                    qosCallback2.expectCallback<OnError> {
                        it.ex.cause is NetworkReleasedException
                    }
                } finally {
                    socket.close()
                    mCM.unregisterQosCallback(qosCallback1)
                    mCM.unregisterQosCallback(qosCallback2)
                }
            } finally {
                socket.close()
                executor.shutdown()
            }
        }
    }

    private fun createEpsAttributes(qci: Int = 1): EpsBearerQosSessionAttributes {
        val remoteAddresses = ArrayList<InetSocketAddress>()
        remoteAddresses.add(InetSocketAddress("2001:db8::123", 80))
        return EpsBearerQosSessionAttributes(
                qci, 2, 3, 4, 5,
                remoteAddresses
        )
    }

    @AppModeFull(reason = "Instant apps don't have permission to bind sockets.")
    @Test
    fun testUnregisterAfterReplacement() {
        // Keeps an eye on all test networks.
        val matchAllCallback = TestableNetworkCallback(timeoutMs = DEFAULT_TIMEOUT_MS)
        registerNetworkCallback(makeTestNetworkRequest(), matchAllCallback)

        // File a request that matches and keeps up the best-scoring test network.
        val testCallback = TestableNetworkCallback(timeoutMs = DEFAULT_TIMEOUT_MS)
        requestNetwork(makeTestNetworkRequest(), testCallback)

        // Connect the first network. This should satisfy the request.
        val (agent1, network1) = connectNetwork()
        matchAllCallback.expectAvailableThenValidatedCallbacks(network1)
        testCallback.expectAvailableThenValidatedCallbacks(network1)
        // Check that network1 exists by binding a socket to it and getting no exceptions.
        network1.bindSocket(DatagramSocket())

        // Connect a second agent. network1 is preferred because it was already registered, so
        // testCallback will not see any events. agent2 is be torn down because it has no requests.
        val (agent2, network2) = connectNetwork()
        matchAllCallback.expectAvailableThenValidatedCallbacks(network2)
        matchAllCallback.expectCallback<Lost>(network2)
        agent2.expectCallback<OnNetworkUnwanted>()
        agent2.expectCallback<OnNetworkDestroyed>()
        assertNull(mCM.getLinkProperties(network2))

        // Mark the first network as awaiting replacement. This should destroy the underlying
        // native network and send onNetworkDestroyed, but will not send any NetworkCallbacks,
        // because for callback and scoring purposes network1 is still connected.
        agent1.unregisterAfterReplacement(5_000 /* timeoutMillis */)
        agent1.expectCallback<OnNetworkDestroyed>()
        assertThrows(IOException::class.java) { network1.bindSocket(DatagramSocket()) }
        assertNotNull(mCM.getLinkProperties(network1))

        // Calling unregisterAfterReplacement more than once has no effect.
        // If it did, this test would fail because the 1ms timeout means that the network would be
        // torn down before the replacement arrives.
        agent1.unregisterAfterReplacement(1 /* timeoutMillis */)

        // Connect a third network. Because network1 is awaiting replacement, network3 is preferred
        // as soon as it validates (until then, it is outscored by network1).
        // The fact that the first events seen by matchAllCallback is the connection of network3
        // implicitly ensures that no callbacks are sent since network1 was lost.
        val (agent3, network3) = connectNetwork()
        matchAllCallback.expectAvailableThenValidatedCallbacks(network3)
        testCallback.expectAvailableDoubleValidatedCallbacks(network3)

        // As soon as the replacement arrives, network1 is disconnected.
        // Check that this happens before the replacement timeout (5 seconds) fires.
        matchAllCallback.expectCallback<Lost>(network1, 2_000 /* timeoutMs */)
        agent1.expectCallback<OnNetworkUnwanted>()

        // Test lingering:
        // - Connect a higher-scoring network and check that network3 starts lingering.
        // - Mark network3 awaiting replacement.
        // - Check that network3 is torn down immediately without waiting for the linger timer or
        //   the replacement timer to fire. This is a regular teardown, so it results in
        //   onNetworkUnwanted before onNetworkDestroyed.
        val (agent4, agent4callback) = createConnectedNetworkAgent()
        val network4 = agent4.network!!
        matchAllCallback.expectAvailableThenValidatedCallbacks(network4)
        agent4.sendNetworkScore(NetworkScore.Builder().setTransportPrimary(true).build())
        matchAllCallback.expectCallback<Losing>(network3)
        testCallback.expectAvailableCallbacks(network4, validated = true)
        mCM.unregisterNetworkCallback(agent4callback)
        agent3.unregisterAfterReplacement(5_000)
        agent3.expectCallback<OnNetworkUnwanted>()
        matchAllCallback.expectCallback<Lost>(network3, 1000L)
        agent3.expectCallback<OnNetworkDestroyed>()

        // Now mark network4 awaiting replacement with a low timeout, and check that if no
        // replacement arrives, it is torn down.
        agent4.unregisterAfterReplacement(100 /* timeoutMillis */)
        matchAllCallback.expectCallback<Lost>(network4, 1000L /* timeoutMs */)
        testCallback.expectCallback<Lost>(network4, 1000L /* timeoutMs */)
        agent4.expectCallback<OnNetworkDestroyed>()
        agent4.expectCallback<OnNetworkUnwanted>()

        // If a network that is awaiting replacement is unregistered, it disconnects immediately,
        // before the replacement timeout fires.
        val (agent5, network5) = connectNetwork()
        matchAllCallback.expectAvailableThenValidatedCallbacks(network5)
        testCallback.expectAvailableThenValidatedCallbacks(network5)
        agent5.unregisterAfterReplacement(5_000 /* timeoutMillis */)
        agent5.unregister()
        matchAllCallback.expectCallback<Lost>(network5, 1000L /* timeoutMs */)
        testCallback.expectCallback<Lost>(network5, 1000L /* timeoutMs */)
        agent5.expectCallback<OnNetworkDestroyed>()
        agent5.expectCallback<OnNetworkUnwanted>()

        // If wifi is replaced within the timeout, the device does not switch to cellular.
        val (cellAgent, cellNetwork) = connectNetwork(TRANSPORT_CELLULAR)
        testCallback.expectAvailableThenValidatedCallbacks(cellNetwork)
        matchAllCallback.expectAvailableThenValidatedCallbacks(cellNetwork)

        val (wifiAgent, wifiNetwork) = connectNetwork(TRANSPORT_WIFI)
        testCallback.expectAvailableCallbacks(wifiNetwork, validated = true)
        testCallback.expectCapabilitiesThat(wifiNetwork) {
            it.hasCapability(NET_CAPABILITY_VALIDATED)
        }
        matchAllCallback.expectAvailableCallbacks(wifiNetwork, validated = false)
        matchAllCallback.expectCallback<Losing>(cellNetwork)
        matchAllCallback.expectCapabilitiesThat(wifiNetwork) {
            it.hasCapability(NET_CAPABILITY_VALIDATED)
        }

        wifiAgent.unregisterAfterReplacement(5_000 /* timeoutMillis */)
        wifiAgent.expectCallback<OnNetworkDestroyed>()

        // Once the network is awaiting replacement, changing LinkProperties, NetworkCapabilities or
        // score, or calling reportNetworkConnectivity, have no effect.
        val wifiSpecifier = mCM.getNetworkCapabilities(wifiNetwork)!!.networkSpecifier
        assertNotNull(wifiSpecifier)
        assertTrue(wifiSpecifier is EthernetNetworkSpecifier)

        val wifiNc = makeTestNetworkCapabilities(wifiSpecifier.interfaceName,
                intArrayOf(TRANSPORT_WIFI))
        wifiAgent.sendNetworkCapabilities(wifiNc)
        val wifiLp = mCM.getLinkProperties(wifiNetwork)!!
        val newRoute = RouteInfo(IpPrefix("192.0.2.42/24"))
        assertFalse(wifiLp.getRoutes().contains(newRoute))
        wifiLp.addRoute(newRoute)
        wifiAgent.sendLinkProperties(wifiLp)
        mCM.reportNetworkConnectivity(wifiNetwork, false)
        // The test implicitly checks that no callbacks are sent here, because the next events seen
        // by the callbacks are for the new network connecting.

        val (newWifiAgent, newWifiNetwork) = connectNetwork(TRANSPORT_WIFI)
        testCallback.expectAvailableCallbacks(newWifiNetwork, validated = true)
        matchAllCallback.expectAvailableThenValidatedCallbacks(newWifiNetwork)
        matchAllCallback.expectCallback<Lost>(wifiNetwork)
        wifiAgent.expectCallback<OnNetworkUnwanted>()
    }

    @Test
    fun testUnregisterAgentBeforeAgentFullyConnected() {
        val specifier = UUID.randomUUID().toString()
        val callback = TestableNetworkCallback()
        val transports = intArrayOf(TRANSPORT_CELLULAR)
        // Ensure this NetworkAgent is never unneeded by filing a request with its specifier.
        requestNetwork(makeTestNetworkRequest(specifier = specifier), callback)
        val nc = makeTestNetworkCapabilities(specifier, transports)
        val agent = createNetworkAgent(realContext, initialNc = nc)
        // Connect the agent
        agent.register()
        // Mark agent connected then unregister agent immediately. Verify that both available and
        // lost callback should be sent still.
        agent.markConnected()
        agent.unregister()
        callback.expectCallback<Available>(agent.network!!)
        callback.eventuallyExpect<Lost> { it.network == agent.network }
    }
}
