/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.Manifest.permission.MANAGE_TEST_NETWORKS
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkAgentConfig
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED
import android.net.NetworkCapabilities.NET_CAPABILITY_TRUSTED
import android.net.NetworkCapabilities.TRANSPORT_TEST
import android.net.NetworkRequest
import android.net.TestNetworkInterface
import android.net.TestNetworkManager
import android.net.TestNetworkSpecifier
import android.net.cts.NsdManagerTest.NsdDiscoveryRecord.DiscoveryEvent.DiscoveryStarted
import android.net.cts.NsdManagerTest.NsdDiscoveryRecord.DiscoveryEvent.DiscoveryStopped
import android.net.cts.NsdManagerTest.NsdDiscoveryRecord.DiscoveryEvent.ServiceFound
import android.net.cts.NsdManagerTest.NsdDiscoveryRecord.DiscoveryEvent.ServiceLost
import android.net.cts.NsdManagerTest.NsdDiscoveryRecord.DiscoveryEvent.StartDiscoveryFailed
import android.net.cts.NsdManagerTest.NsdDiscoveryRecord.DiscoveryEvent.StopDiscoveryFailed
import android.net.cts.NsdManagerTest.NsdRegistrationRecord.RegistrationEvent.RegistrationFailed
import android.net.cts.NsdManagerTest.NsdRegistrationRecord.RegistrationEvent.ServiceRegistered
import android.net.cts.NsdManagerTest.NsdRegistrationRecord.RegistrationEvent.ServiceUnregistered
import android.net.cts.NsdManagerTest.NsdRegistrationRecord.RegistrationEvent.UnregistrationFailed
import android.net.cts.NsdManagerTest.NsdResolveRecord.ResolveEvent.ResolveFailed
import android.net.cts.NsdManagerTest.NsdResolveRecord.ResolveEvent.ServiceResolved
import android.net.nsd.NsdManager
import android.net.nsd.NsdManager.DiscoveryListener
import android.net.nsd.NsdManager.RegistrationListener
import android.net.nsd.NsdManager.ResolveListener
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.HandlerThread
import android.os.Process.myTid
import android.platform.test.annotations.AppModeFull
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.android.net.module.util.ArrayTrackRecord
import com.android.net.module.util.TrackRecord
import com.android.networkstack.apishim.NsdShimImpl
import com.android.testutils.TestableNetworkAgent
import com.android.testutils.TestableNetworkCallback
import com.android.testutils.runAsShell
import com.android.testutils.tryTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.Random
import java.util.concurrent.Executor
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

private const val TAG = "NsdManagerTest"
private const val SERVICE_TYPE = "_nmt._tcp"
private const val TIMEOUT_MS = 2000L
private const val NO_CALLBACK_TIMEOUT_MS = 200L
private const val DBG = false

private val nsdShim = NsdShimImpl.newInstance()

@AppModeFull(reason = "Socket cannot bind in instant app mode")
@RunWith(AndroidJUnit4::class)
class NsdManagerTest {
    private val context by lazy { InstrumentationRegistry.getInstrumentation().context }
    private val nsdManager by lazy { context.getSystemService(NsdManager::class.java) }

    private val cm by lazy { context.getSystemService(ConnectivityManager::class.java) }
    private val serviceName = "NsdTest%09d".format(Random().nextInt(1_000_000_000))
    private val handlerThread = HandlerThread(NsdManagerTest::class.java.simpleName)

    private lateinit var testNetwork1: TestTapNetwork
    private lateinit var testNetwork2: TestTapNetwork

    private class TestTapNetwork(
        val iface: TestNetworkInterface,
        val requestCb: NetworkCallback,
        val agent: TestableNetworkAgent,
        val network: Network
    ) {
        fun close(cm: ConnectivityManager) {
            cm.unregisterNetworkCallback(requestCb)
            agent.unregister()
            iface.fileDescriptor.close()
        }
    }

    private interface NsdEvent
    private open class NsdRecord<T : NsdEvent> private constructor(
        private val history: ArrayTrackRecord<T>,
        private val expectedThreadId: Int? = null
    ) : TrackRecord<T> by history {
        constructor(expectedThreadId: Int? = null) : this(ArrayTrackRecord(), expectedThreadId)

        val nextEvents = history.newReadHead()

        override fun add(e: T): Boolean {
            if (expectedThreadId != null) {
                assertEquals(expectedThreadId, myTid(), "Callback is running on the wrong thread")
            }
            return history.add(e)
        }

        inline fun <reified V : NsdEvent> expectCallbackEventually(
            crossinline predicate: (V) -> Boolean = { true }
        ): V = nextEvents.poll(TIMEOUT_MS) { e -> e is V && predicate(e) } as V?
                ?: fail("Callback for ${V::class.java.simpleName} not seen after $TIMEOUT_MS ms")

        inline fun <reified V : NsdEvent> expectCallback(): V {
            val nextEvent = nextEvents.poll(TIMEOUT_MS)
            assertNotNull(nextEvent, "No callback received after $TIMEOUT_MS ms")
            assertTrue(nextEvent is V, "Expected ${V::class.java.simpleName} but got " +
                    nextEvent.javaClass.simpleName)
            return nextEvent
        }

        inline fun assertNoCallback(timeoutMs: Long = NO_CALLBACK_TIMEOUT_MS) {
            val cb = nextEvents.poll(timeoutMs)
            assertNull(cb, "Expected no callback but got $cb")
        }
    }

    private class NsdRegistrationRecord(expectedThreadId: Int? = null) : RegistrationListener,
            NsdRecord<NsdRegistrationRecord.RegistrationEvent>(expectedThreadId) {
        sealed class RegistrationEvent : NsdEvent {
            abstract val serviceInfo: NsdServiceInfo

            data class RegistrationFailed(
                override val serviceInfo: NsdServiceInfo,
                val errorCode: Int
            ) : RegistrationEvent()

            data class UnregistrationFailed(
                override val serviceInfo: NsdServiceInfo,
                val errorCode: Int
            ) : RegistrationEvent()

            data class ServiceRegistered(override val serviceInfo: NsdServiceInfo)
                : RegistrationEvent()
            data class ServiceUnregistered(override val serviceInfo: NsdServiceInfo)
                : RegistrationEvent()
        }

        override fun onRegistrationFailed(si: NsdServiceInfo, err: Int) {
            add(RegistrationFailed(si, err))
        }

        override fun onUnregistrationFailed(si: NsdServiceInfo, err: Int) {
            add(UnregistrationFailed(si, err))
        }

        override fun onServiceRegistered(si: NsdServiceInfo) {
            add(ServiceRegistered(si))
        }

        override fun onServiceUnregistered(si: NsdServiceInfo) {
            add(ServiceUnregistered(si))
        }
    }

    private class NsdDiscoveryRecord(expectedThreadId: Int? = null) :
            DiscoveryListener, NsdRecord<NsdDiscoveryRecord.DiscoveryEvent>(expectedThreadId) {
        sealed class DiscoveryEvent : NsdEvent {
            data class StartDiscoveryFailed(val serviceType: String, val errorCode: Int)
                : DiscoveryEvent()

            data class StopDiscoveryFailed(val serviceType: String, val errorCode: Int)
                : DiscoveryEvent()

            data class DiscoveryStarted(val serviceType: String) : DiscoveryEvent()
            data class DiscoveryStopped(val serviceType: String) : DiscoveryEvent()
            data class ServiceFound(val serviceInfo: NsdServiceInfo) : DiscoveryEvent()
            data class ServiceLost(val serviceInfo: NsdServiceInfo) : DiscoveryEvent()
        }

        override fun onStartDiscoveryFailed(serviceType: String, err: Int) {
            add(StartDiscoveryFailed(serviceType, err))
        }

        override fun onStopDiscoveryFailed(serviceType: String, err: Int) {
            add(StopDiscoveryFailed(serviceType, err))
        }

        override fun onDiscoveryStarted(serviceType: String) {
            add(DiscoveryStarted(serviceType))
        }

        override fun onDiscoveryStopped(serviceType: String) {
            add(DiscoveryStopped(serviceType))
        }

        override fun onServiceFound(si: NsdServiceInfo) {
            add(ServiceFound(si))
        }

        override fun onServiceLost(si: NsdServiceInfo) {
            add(ServiceLost(si))
        }

        fun waitForServiceDiscovered(
            serviceName: String,
            expectedNetwork: Network? = null
        ): NsdServiceInfo {
            return expectCallbackEventually<ServiceFound> {
                it.serviceInfo.serviceName == serviceName &&
                        (expectedNetwork == null ||
                                expectedNetwork == nsdShim.getNetwork(it.serviceInfo))
            }.serviceInfo
        }
    }

    private class NsdResolveRecord : ResolveListener,
            NsdRecord<NsdResolveRecord.ResolveEvent>() {
        sealed class ResolveEvent : NsdEvent {
            data class ResolveFailed(val serviceInfo: NsdServiceInfo, val errorCode: Int)
                : ResolveEvent()

            data class ServiceResolved(val serviceInfo: NsdServiceInfo) : ResolveEvent()
        }

        override fun onResolveFailed(si: NsdServiceInfo, err: Int) {
            add(ResolveFailed(si, err))
        }

        override fun onServiceResolved(si: NsdServiceInfo) {
            add(ServiceResolved(si))
        }
    }

    @Before
    fun setUp() {
        handlerThread.start()

        if (TestUtils.shouldTestTApis()) {
            runAsShell(MANAGE_TEST_NETWORKS) {
                testNetwork1 = createTestNetwork()
                testNetwork2 = createTestNetwork()
            }
        }
    }

    private fun createTestNetwork(): TestTapNetwork {
        val tnm = context.getSystemService(TestNetworkManager::class.java)
        val iface = tnm.createTapInterface()
        val cb = TestableNetworkCallback()
        val testNetworkSpecifier = TestNetworkSpecifier(iface.interfaceName)
        cm.requestNetwork(NetworkRequest.Builder()
                .removeCapability(NET_CAPABILITY_TRUSTED)
                .addTransportType(TRANSPORT_TEST)
                .setNetworkSpecifier(testNetworkSpecifier)
                .build(), cb)
        val agent = registerTestNetworkAgent(iface.interfaceName)
        val network = agent.network ?: fail("Registered agent should have a network")
        // The network has no INTERNET capability, so will be marked validated immediately
        cb.expectAvailableThenValidatedCallbacks(network)
        return TestTapNetwork(iface, cb, agent, network)
    }

    private fun registerTestNetworkAgent(ifaceName: String): TestableNetworkAgent {
        val agent = TestableNetworkAgent(context, handlerThread.looper,
                NetworkCapabilities().apply {
                    removeCapability(NET_CAPABILITY_TRUSTED)
                    addTransportType(TRANSPORT_TEST)
                    setNetworkSpecifier(TestNetworkSpecifier(ifaceName))
                },
                LinkProperties().apply {
                    interfaceName = ifaceName
                },
                NetworkAgentConfig.Builder().build())
        agent.register()
        agent.markConnected()
        return agent
    }

    @After
    fun tearDown() {
        if (TestUtils.shouldTestTApis()) {
            runAsShell(MANAGE_TEST_NETWORKS) {
                testNetwork1.close(cm)
                testNetwork2.close(cm)
            }
        }
        handlerThread.quitSafely()
    }

    @Test
    fun testNsdManager() {
        val si = NsdServiceInfo()
        si.serviceType = SERVICE_TYPE
        si.serviceName = serviceName
        // Test binary data with various bytes
        val testByteArray = byteArrayOf(-128, 127, 2, 1, 0, 1, 2)
        // Test string data with 256 characters (25 blocks of 10 characters + 6)
        val string256 = "1_________2_________3_________4_________5_________6_________" +
                "7_________8_________9_________10________11________12________13________" +
                "14________15________16________17________18________19________20________" +
                "21________22________23________24________25________123456"

        // Illegal attributes
        listOf(
                Triple(null, null, "null key"),
                Triple("", null, "empty key"),
                Triple(string256, null, "key with 256 characters"),
                Triple("key", string256.substring(3),
                        "key+value combination with more than 255 characters"),
                Triple("key", string256.substring(4), "key+value combination with 255 characters"),
                Triple("\u0019", null, "key with invalid character"),
                Triple("=", null, "key with invalid character"),
                Triple("\u007f", null, "key with invalid character")
        ).forEach {
            assertFailsWith<IllegalArgumentException>(
                    "Setting invalid ${it.third} unexpectedly succeeded") {
                si.setAttribute(it.first, it.second)
            }
        }

        // Allowed attributes
        si.setAttribute("booleanAttr", null as String?)
        si.setAttribute("keyValueAttr", "value")
        si.setAttribute("keyEqualsAttr", "=")
        si.setAttribute(" whiteSpaceKeyValueAttr ", " value ")
        si.setAttribute("binaryDataAttr", testByteArray)
        si.setAttribute("nullBinaryDataAttr", null as ByteArray?)
        si.setAttribute("emptyBinaryDataAttr", byteArrayOf())
        si.setAttribute("longkey", string256.substring(9))
        val socket = ServerSocket(0)
        val localPort = socket.localPort
        si.port = localPort
        if (DBG) Log.d(TAG, "Port = $localPort")

        val registrationRecord = NsdRegistrationRecord()
        // Test registering without an Executor
        nsdManager.registerService(si, NsdManager.PROTOCOL_DNS_SD, registrationRecord)
        val registeredInfo = registrationRecord.expectCallback<ServiceRegistered>().serviceInfo

        val discoveryRecord = NsdDiscoveryRecord()
        // Test discovering without an Executor
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryRecord)

        // Expect discovery started
        discoveryRecord.expectCallback<DiscoveryStarted>()

        // Expect a service record to be discovered
        val foundInfo = discoveryRecord.waitForServiceDiscovered(registeredInfo.serviceName)

        // Test resolving without an Executor
        val resolveRecord = NsdResolveRecord()
        nsdManager.resolveService(foundInfo, resolveRecord)
        val resolvedService = resolveRecord.expectCallback<ServiceResolved>().serviceInfo

        // Check Txt attributes
        assertEquals(8, resolvedService.attributes.size)
        assertTrue(resolvedService.attributes.containsKey("booleanAttr"))
        assertNull(resolvedService.attributes["booleanAttr"])
        assertEquals("value", resolvedService.attributes["keyValueAttr"].utf8ToString())
        assertEquals("=", resolvedService.attributes["keyEqualsAttr"].utf8ToString())
        assertEquals(" value ",
                resolvedService.attributes[" whiteSpaceKeyValueAttr "].utf8ToString())
        assertEquals(string256.substring(9), resolvedService.attributes["longkey"].utf8ToString())
        assertArrayEquals(testByteArray, resolvedService.attributes["binaryDataAttr"])
        assertTrue(resolvedService.attributes.containsKey("nullBinaryDataAttr"))
        assertNull(resolvedService.attributes["nullBinaryDataAttr"])
        assertTrue(resolvedService.attributes.containsKey("emptyBinaryDataAttr"))
        assertNull(resolvedService.attributes["emptyBinaryDataAttr"])
        assertEquals(localPort, resolvedService.port)

        // Unregister the service
        nsdManager.unregisterService(registrationRecord)
        registrationRecord.expectCallback<ServiceUnregistered>()

        // Expect a callback for service lost
        discoveryRecord.expectCallbackEventually<ServiceLost> {
            it.serviceInfo.serviceName == serviceName
        }

        // Register service again to see if NsdManager can discover it
        val si2 = NsdServiceInfo()
        si2.serviceType = SERVICE_TYPE
        si2.serviceName = serviceName
        si2.port = localPort
        val registrationRecord2 = NsdRegistrationRecord()
        nsdManager.registerService(si2, NsdManager.PROTOCOL_DNS_SD, registrationRecord2)
        val registeredInfo2 = registrationRecord2.expectCallback<ServiceRegistered>().serviceInfo

        // Expect a service record to be discovered (and filter the ones
        // that are unrelated to this test)
        val foundInfo2 = discoveryRecord.waitForServiceDiscovered(registeredInfo2.serviceName)

        // Resolve the service
        val resolveRecord2 = NsdResolveRecord()
        nsdManager.resolveService(foundInfo2, resolveRecord2)
        val resolvedService2 = resolveRecord2.expectCallback<ServiceResolved>().serviceInfo

        // Check that the resolved service doesn't have any TXT records
        assertEquals(0, resolvedService2.attributes.size)

        nsdManager.stopServiceDiscovery(discoveryRecord)

        discoveryRecord.expectCallbackEventually<DiscoveryStopped>()

        nsdManager.unregisterService(registrationRecord2)
        registrationRecord2.expectCallback<ServiceUnregistered>()
    }

    @Test
    fun testNsdManager_DiscoverOnNetwork() {
        // This test requires shims supporting T+ APIs (discovering on specific network)
        assumeTrue(TestUtils.shouldTestTApis())

        val si = NsdServiceInfo()
        si.serviceType = SERVICE_TYPE
        si.serviceName = this.serviceName
        si.port = 12345 // Test won't try to connect so port does not matter

        val registrationRecord = NsdRegistrationRecord()
        val registeredInfo = registerService(registrationRecord, si)

        tryTest {
            val discoveryRecord = NsdDiscoveryRecord()
            nsdShim.discoverServices(nsdManager, SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD,
                    testNetwork1.network, Executor { it.run() }, discoveryRecord)

            val foundInfo = discoveryRecord.waitForServiceDiscovered(
                    serviceName, testNetwork1.network)
            assertEquals(testNetwork1.network, nsdShim.getNetwork(foundInfo))

            // Rewind to ensure the service is not found on the other interface
            discoveryRecord.nextEvents.rewind(0)
            assertNull(discoveryRecord.nextEvents.poll(timeoutMs = 100L) {
                it is ServiceFound &&
                        it.serviceInfo.serviceName == registeredInfo.serviceName &&
                        nsdShim.getNetwork(it.serviceInfo) != testNetwork1.network
            }, "The service should not be found on this network")
        } cleanup {
            nsdManager.unregisterService(registrationRecord)
        }
    }

    @Test
    fun testNsdManager_DiscoverWithNetworkRequest() {
        // This test requires shims supporting T+ APIs (discovering on network request)
        assumeTrue(TestUtils.shouldTestTApis())

        val si = NsdServiceInfo()
        si.serviceType = SERVICE_TYPE
        si.serviceName = this.serviceName
        si.port = 12345 // Test won't try to connect so port does not matter

        val handler = Handler(handlerThread.looper)
        val executor = Executor { handler.post(it) }

        val registrationRecord = NsdRegistrationRecord(expectedThreadId = handlerThread.threadId)
        val registeredInfo1 = registerService(registrationRecord, si, executor)
        val discoveryRecord = NsdDiscoveryRecord(expectedThreadId = handlerThread.threadId)

        tryTest {
            val specifier = TestNetworkSpecifier(testNetwork1.iface.interfaceName)
            nsdShim.discoverServices(nsdManager, SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD,
                    NetworkRequest.Builder()
                            .removeCapability(NET_CAPABILITY_TRUSTED)
                            .addTransportType(TRANSPORT_TEST)
                            .setNetworkSpecifier(specifier)
                            .build(),
                    executor, discoveryRecord)

            val discoveryStarted = discoveryRecord.expectCallback<DiscoveryStarted>()
            assertEquals(SERVICE_TYPE, discoveryStarted.serviceType)

            val serviceDiscovered = discoveryRecord.expectCallback<ServiceFound>()
            assertEquals(registeredInfo1.serviceName, serviceDiscovered.serviceInfo.serviceName)
            assertEquals(testNetwork1.network, nsdShim.getNetwork(serviceDiscovered.serviceInfo))

            // Unregister, then register the service back: it should be lost and found again
            nsdManager.unregisterService(registrationRecord)
            val serviceLost1 = discoveryRecord.expectCallback<ServiceLost>()
            assertEquals(registeredInfo1.serviceName, serviceLost1.serviceInfo.serviceName)
            assertEquals(testNetwork1.network, nsdShim.getNetwork(serviceLost1.serviceInfo))

            registrationRecord.expectCallback<ServiceUnregistered>()
            val registeredInfo2 = registerService(registrationRecord, si, executor)
            val serviceDiscovered2 = discoveryRecord.expectCallback<ServiceFound>()
            assertEquals(registeredInfo2.serviceName, serviceDiscovered2.serviceInfo.serviceName)
            assertEquals(testNetwork1.network, nsdShim.getNetwork(serviceDiscovered2.serviceInfo))

            // Teardown, then bring back up a network on the test interface: the service should
            // go away, then come back
            testNetwork1.agent.unregister()
            val serviceLost = discoveryRecord.expectCallback<ServiceLost>()
            assertEquals(registeredInfo2.serviceName, serviceLost.serviceInfo.serviceName)
            assertEquals(testNetwork1.network, nsdShim.getNetwork(serviceLost.serviceInfo))

            val newAgent = runAsShell(MANAGE_TEST_NETWORKS) {
                registerTestNetworkAgent(testNetwork1.iface.interfaceName)
            }
            val newNetwork = newAgent.network ?: fail("Registered agent should have a network")
            val serviceDiscovered3 = discoveryRecord.expectCallback<ServiceFound>()
            assertEquals(registeredInfo2.serviceName, serviceDiscovered3.serviceInfo.serviceName)
            assertEquals(newNetwork, nsdShim.getNetwork(serviceDiscovered3.serviceInfo))
        } cleanupStep {
            nsdManager.stopServiceDiscovery(discoveryRecord)
            discoveryRecord.expectCallback<DiscoveryStopped>()
        } cleanup {
            nsdManager.unregisterService(registrationRecord)
        }
    }

    @Test
    fun testNsdManager_DiscoverWithNetworkRequest_NoMatchingNetwork() {
        // This test requires shims supporting T+ APIs (discovering on network request)
        assumeTrue(TestUtils.shouldTestTApis())

        val si = NsdServiceInfo()
        si.serviceType = SERVICE_TYPE
        si.serviceName = this.serviceName
        si.port = 12345 // Test won't try to connect so port does not matter

        val handler = Handler(handlerThread.looper)
        val executor = Executor { handler.post(it) }

        val discoveryRecord = NsdDiscoveryRecord(expectedThreadId = handlerThread.threadId)
        val specifier = TestNetworkSpecifier(testNetwork1.iface.interfaceName)

        tryTest {
            nsdShim.discoverServices(nsdManager, SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD,
                    NetworkRequest.Builder()
                            .removeCapability(NET_CAPABILITY_TRUSTED)
                            .addTransportType(TRANSPORT_TEST)
                            // Specified network does not have this capability
                            .addCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED)
                            .setNetworkSpecifier(specifier)
                            .build(),
                    executor, discoveryRecord)
            discoveryRecord.expectCallback<DiscoveryStarted>()
        } cleanup {
            nsdManager.stopServiceDiscovery(discoveryRecord)
            discoveryRecord.expectCallback<DiscoveryStopped>()
        }
    }

    @Test
    fun testNsdManager_ResolveOnNetwork() {
        // This test requires shims supporting T+ APIs (NsdServiceInfo.network)
        assumeTrue(TestUtils.shouldTestTApis())

        val si = NsdServiceInfo()
        si.serviceType = SERVICE_TYPE
        si.serviceName = this.serviceName
        si.port = 12345 // Test won't try to connect so port does not matter

        val registrationRecord = NsdRegistrationRecord()
        val registeredInfo = registerService(registrationRecord, si)
        tryTest {
            val resolveRecord = NsdResolveRecord()

            val discoveryRecord = NsdDiscoveryRecord()
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryRecord)

            val foundInfo1 = discoveryRecord.waitForServiceDiscovered(
                    serviceName, testNetwork1.network)
            assertEquals(testNetwork1.network, nsdShim.getNetwork(foundInfo1))
            // Rewind as the service could be found on each interface in any order
            discoveryRecord.nextEvents.rewind(0)
            val foundInfo2 = discoveryRecord.waitForServiceDiscovered(
                    serviceName, testNetwork2.network)
            assertEquals(testNetwork2.network, nsdShim.getNetwork(foundInfo2))

            nsdShim.resolveService(nsdManager, foundInfo1, Executor { it.run() }, resolveRecord)
            val cb = resolveRecord.expectCallback<ServiceResolved>()
            cb.serviceInfo.let {
                // Resolved service type has leading dot
                assertEquals(".$SERVICE_TYPE", it.serviceType)
                assertEquals(registeredInfo.serviceName, it.serviceName)
                assertEquals(si.port, it.port)
                assertEquals(testNetwork1.network, nsdShim.getNetwork(it))
            }
            // TODO: check that MDNS packets are sent only on testNetwork1.
        } cleanupStep {
            nsdManager.unregisterService(registrationRecord)
        } cleanup {
            registrationRecord.expectCallback<ServiceUnregistered>()
        }
    }

    @Test
    fun testNsdManager_RegisterOnNetwork() {
        // This test requires shims supporting T+ APIs (NsdServiceInfo.network)
        assumeTrue(TestUtils.shouldTestTApis())

        val si = NsdServiceInfo()
        si.serviceType = SERVICE_TYPE
        si.serviceName = this.serviceName
        si.network = testNetwork1.network
        si.port = 12345 // Test won't try to connect so port does not matter

        // Register service on testNetwork1
        val registrationRecord = NsdRegistrationRecord()
        registerService(registrationRecord, si)
        val discoveryRecord = NsdDiscoveryRecord()
        val discoveryRecord2 = NsdDiscoveryRecord()
        val discoveryRecord3 = NsdDiscoveryRecord()

        tryTest {
            // Discover service on testNetwork1.
            nsdShim.discoverServices(nsdManager, SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD,
                testNetwork1.network, Executor { it.run() }, discoveryRecord)
            // Expect that service is found on testNetwork1
            val foundInfo = discoveryRecord.waitForServiceDiscovered(
                serviceName, testNetwork1.network)
            assertEquals(testNetwork1.network, nsdShim.getNetwork(foundInfo))

            // Discover service on testNetwork2.
            nsdShim.discoverServices(nsdManager, SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD,
                testNetwork2.network, Executor { it.run() }, discoveryRecord2)
            // Expect that discovery is started then no other callbacks.
            discoveryRecord2.expectCallback<DiscoveryStarted>()
            discoveryRecord2.assertNoCallback()

            // Discover service on all networks (not specify any network).
            nsdShim.discoverServices(nsdManager, SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD,
                null as Network? /* network */, Executor { it.run() }, discoveryRecord3)
            // Expect that service is found on testNetwork1
            val foundInfo3 = discoveryRecord3.waitForServiceDiscovered(
                    serviceName, testNetwork1.network)
            assertEquals(testNetwork1.network, nsdShim.getNetwork(foundInfo3))
        } cleanupStep {
            nsdManager.stopServiceDiscovery(discoveryRecord2)
            discoveryRecord2.expectCallback<DiscoveryStopped>()
        } cleanup {
            nsdManager.unregisterService(registrationRecord)
        }
    }

    @Test
    fun testNsdManager_RegisterServiceNameWithNonStandardCharacters() {
        val serviceNames = "^Nsd.Test|Non-#AsCiI\\Characters&\\ufffe テスト 測試"
        val si = NsdServiceInfo().apply {
            serviceType = SERVICE_TYPE
            serviceName = serviceNames
            port = 12345 // Test won't try to connect so port does not matter
        }

        // Register the service name which contains non-standard characters.
        val registrationRecord = NsdRegistrationRecord()
        nsdManager.registerService(si, NsdManager.PROTOCOL_DNS_SD, registrationRecord)
        registrationRecord.expectCallback<ServiceRegistered>()

        tryTest {
            // Discover that service name.
            val discoveryRecord = NsdDiscoveryRecord()
            nsdManager.discoverServices(
                SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryRecord
            )
            val foundInfo = discoveryRecord.waitForServiceDiscovered(serviceNames)

            // Expect that resolving the service name works properly even service name contains
            // non-standard characters.
            val resolveRecord = NsdResolveRecord()
            nsdManager.resolveService(foundInfo, resolveRecord)
            val resolvedCb = resolveRecord.expectCallback<ServiceResolved>()
            assertEquals(foundInfo.serviceName, resolvedCb.serviceInfo.serviceName)
        } cleanupStep {
            nsdManager.unregisterService(registrationRecord)
        } cleanup {
            registrationRecord.expectCallback<ServiceUnregistered>()
        }
    }

    /**
     * Register a service and return its registration record.
     */
    private fun registerService(
        record: NsdRegistrationRecord,
        si: NsdServiceInfo,
        executor: Executor = Executor { it.run() }
    ): NsdServiceInfo {
        nsdShim.registerService(nsdManager, si, NsdManager.PROTOCOL_DNS_SD, executor, record)
        // We may not always get the name that we tried to register;
        // This events tells us the name that was registered.
        val cb = record.expectCallback<ServiceRegistered>()
        return cb.serviceInfo
    }

    private fun resolveService(discoveredInfo: NsdServiceInfo): NsdServiceInfo {
        val record = NsdResolveRecord()
        nsdShim.resolveService(nsdManager, discoveredInfo, Executor { it.run() }, record)
        val resolvedCb = record.expectCallback<ServiceResolved>()
        assertEquals(discoveredInfo.serviceName, resolvedCb.serviceInfo.serviceName)

        return resolvedCb.serviceInfo
    }
}

private fun ByteArray?.utf8ToString(): String {
    if (this == null) return ""
    return String(this, StandardCharsets.UTF_8)
}
