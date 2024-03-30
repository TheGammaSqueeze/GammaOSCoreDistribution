/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.net.cts.util.CtsNetUtils.TestNetworkCallback

import android.app.Instrumentation
import android.Manifest.permission.MANAGE_TEST_NETWORKS
import android.content.Context
import android.net.ConnectivityManager
import android.net.DscpPolicy
import android.net.InetAddresses
import android.net.IpPrefix
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.NetworkAgent
import android.net.NetworkAgent.DSCP_POLICY_STATUS_DELETED
import android.net.NetworkAgent.DSCP_POLICY_STATUS_SUCCESS
import android.net.NetworkAgentConfig
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN
import android.net.NetworkCapabilities.NET_CAPABILITY_TRUSTED
import android.net.NetworkCapabilities.TRANSPORT_TEST
import android.net.NetworkRequest
import android.net.TestNetworkInterface
import android.net.TestNetworkManager
import android.net.RouteInfo
import android.os.HandlerThread
import android.platform.test.annotations.AppModeFull
import android.system.Os
import android.system.OsConstants.AF_INET
import android.system.OsConstants.AF_INET6
import android.system.OsConstants.IPPROTO_UDP
import android.system.OsConstants.SOCK_DGRAM
import android.system.OsConstants.SOCK_NONBLOCK
import android.util.Log
import android.util.Range
import androidx.test.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.android.testutils.CompatUtil
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.assertParcelingIsLossless
import com.android.testutils.runAsShell
import com.android.testutils.SC_V2
import com.android.testutils.TapPacketReader
import com.android.testutils.TestableNetworkAgent
import com.android.testutils.TestableNetworkAgent.CallbackEntry.OnNetworkCreated
import com.android.testutils.TestableNetworkAgent.CallbackEntry.OnDscpPolicyStatusUpdated
import com.android.testutils.TestableNetworkCallback
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.regex.Pattern
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

private const val MAX_PACKET_LENGTH = 1500

private const val IP4_PREFIX_LEN = 32
private const val IP6_PREFIX_LEN = 128

private val instrumentation: Instrumentation
    get() = InstrumentationRegistry.getInstrumentation()

private const val TAG = "DscpPolicyTest"
private const val PACKET_TIMEOUT_MS = 2_000L

@AppModeFull(reason = "Instant apps cannot create test networks")
@RunWith(AndroidJUnit4::class)
class DscpPolicyTest {
    @JvmField
    @Rule
    val ignoreRule = DevSdkIgnoreRule(ignoreClassUpTo = SC_V2)

    private val LOCAL_IPV4_ADDRESS = InetAddresses.parseNumericAddress("192.0.2.1")
    private val TEST_TARGET_IPV4_ADDR =
            InetAddresses.parseNumericAddress("8.8.8.8") as Inet4Address
    private val LOCAL_IPV6_ADDRESS = InetAddresses.parseNumericAddress("2001:db8::1")
    private val TEST_TARGET_IPV6_ADDR =
            InetAddresses.parseNumericAddress("2001:4860:4860::8888") as Inet6Address

    private val realContext = InstrumentationRegistry.getContext()
    private val cm = realContext.getSystemService(ConnectivityManager::class.java)

    private val agentsToCleanUp = mutableListOf<NetworkAgent>()
    private val callbacksToCleanUp = mutableListOf<TestableNetworkCallback>()

    private val handlerThread = HandlerThread(DscpPolicyTest::class.java.simpleName)

    private lateinit var iface: TestNetworkInterface
    private lateinit var tunNetworkCallback: TestNetworkCallback
    private lateinit var reader: TapPacketReader

    private fun getKernelVersion(): IntArray {
        // Example:
        // 4.9.29-g958411d --> 4.9
        val release = Os.uname().release
        val m = Pattern.compile("^(\\d+)\\.(\\d+)").matcher(release)
        assertTrue(m.find(), "No pattern in release string: " + release)
        return intArrayOf(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)))
    }

    private fun kernelIsAtLeast(major: Int, minor: Int): Boolean {
        val version = getKernelVersion()
        return (version.get(0) > major || (version.get(0) == major && version.get(1) >= minor))
    }

    @Before
    fun setUp() {
        // For BPF support kernel needs to be at least 5.15.
        assumeTrue(kernelIsAtLeast(5, 15))

        runAsShell(MANAGE_TEST_NETWORKS) {
            val tnm = realContext.getSystemService(TestNetworkManager::class.java)

            iface = tnm.createTunInterface(arrayOf(
                    LinkAddress(LOCAL_IPV4_ADDRESS, IP4_PREFIX_LEN),
                    LinkAddress(LOCAL_IPV6_ADDRESS, IP6_PREFIX_LEN)))
            assertNotNull(iface)
        }

        handlerThread.start()
        reader = TapPacketReader(
                handlerThread.threadHandler,
                iface.fileDescriptor.fileDescriptor,
                MAX_PACKET_LENGTH)
        reader.startAsyncForTest()
    }

    @After
    fun tearDown() {
        if (!kernelIsAtLeast(5, 15)) {
            return;
        }
        agentsToCleanUp.forEach { it.unregister() }
        callbacksToCleanUp.forEach { cm.unregisterNetworkCallback(it) }

        // reader.stop() cleans up tun fd
        reader.handler.post { reader.stop() }
        if (iface.fileDescriptor.fileDescriptor != null)
            Os.close(iface.fileDescriptor.fileDescriptor)
        handlerThread.quitSafely()
    }

    private fun requestNetwork(request: NetworkRequest, callback: TestableNetworkCallback) {
        cm.requestNetwork(request, callback)
        callbacksToCleanUp.add(callback)
    }

    private fun makeTestNetworkRequest(specifier: String? = null): NetworkRequest {
        return NetworkRequest.Builder()
                .clearCapabilities()
                .addCapability(NET_CAPABILITY_NOT_RESTRICTED)
                .addTransportType(TRANSPORT_TEST)
                .also {
                    if (specifier != null) {
                        it.setNetworkSpecifier(CompatUtil.makeTestNetworkSpecifier(specifier))
                    }
                }
                .build()
    }

    private fun createConnectedNetworkAgent(
        context: Context = realContext,
        specifier: String? = iface.getInterfaceName()
    ): Pair<TestableNetworkAgent, TestableNetworkCallback> {
        val callback = TestableNetworkCallback()
        // Ensure this NetworkAgent is never unneeded by filing a request with its specifier.
        requestNetwork(makeTestNetworkRequest(specifier = specifier), callback)

        val nc = NetworkCapabilities().apply {
            addTransportType(TRANSPORT_TEST)
            removeCapability(NET_CAPABILITY_TRUSTED)
            removeCapability(NET_CAPABILITY_INTERNET)
            addCapability(NET_CAPABILITY_NOT_SUSPENDED)
            addCapability(NET_CAPABILITY_NOT_ROAMING)
            addCapability(NET_CAPABILITY_NOT_VPN)
            addCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
            if (null != specifier) {
                setNetworkSpecifier(CompatUtil.makeTestNetworkSpecifier(specifier))
            }
        }
        val lp = LinkProperties().apply {
            addLinkAddress(LinkAddress(LOCAL_IPV4_ADDRESS, IP4_PREFIX_LEN))
            addLinkAddress(LinkAddress(LOCAL_IPV6_ADDRESS, IP6_PREFIX_LEN))
            addRoute(RouteInfo(IpPrefix("0.0.0.0/0"), null, null))
            addRoute(RouteInfo(InetAddress.getByName("fe80::1234")))
            setInterfaceName(specifier)
        }
        val config = NetworkAgentConfig.Builder().build()
        val agent = TestableNetworkAgent(context, handlerThread.looper, nc, lp, config)
        agentsToCleanUp.add(agent)

        // Connect the agent and verify initial status callbacks.
        runAsShell(MANAGE_TEST_NETWORKS) { agent.register() }
        agent.markConnected()
        agent.expectCallback<OnNetworkCreated>()
        agent.expectSignalStrengths(intArrayOf())
        agent.expectValidationBypassedStatus()
        val network = agent.network ?: fail("Expected a non-null network")
        return agent to callback
    }

    fun ByteArray.toHex(): String = joinToString(separator = "") {
        eachByte -> "%02x".format(eachByte)
    }

    fun sendPacket(
        agent: TestableNetworkAgent,
        sendV6: Boolean,
        dstPort: Int = 0,
    ) {
        val testString = "test string"
        val testPacket = ByteBuffer.wrap(testString.toByteArray(Charsets.UTF_8))
        var packetFound = false

        val socket = Os.socket(if (sendV6) AF_INET6 else AF_INET, SOCK_DGRAM or SOCK_NONBLOCK,
                IPPROTO_UDP)
        agent.network.bindSocket(socket)

        val originalPacket = testPacket.readAsArray()
        Os.sendto(socket, originalPacket, 0 /* bytesOffset */, originalPacket.size, 0 /* flags */,
                if(sendV6) TEST_TARGET_IPV6_ADDR else TEST_TARGET_IPV4_ADDR, dstPort)
        Os.close(socket)
    }

    fun parseV4PacketDscp(buffer : ByteBuffer) : Int {
        val ip_ver = buffer.get()
        val tos = buffer.get()
        val length = buffer.getShort()
        val id = buffer.getShort()
        val offset = buffer.getShort()
        val ttl = buffer.get()
        val ipType = buffer.get()
        val checksum = buffer.getShort()
        return tos.toInt().shr(2)
    }

    fun parseV6PacketDscp(buffer : ByteBuffer) : Int {
        val ip_ver = buffer.get()
        val tc = buffer.get()
        val fl = buffer.getShort()
        val length = buffer.getShort()
        val proto = buffer.get()
        val hop = buffer.get()
        // DSCP is bottom 4 bits of ip_ver and top 2 of tc.
        val ip_ver_bottom = ip_ver.toInt().and(0xf)
        val tc_dscp = tc.toInt().shr(6)
        return ip_ver_bottom.toInt().shl(2) + tc_dscp
    }

    fun parsePacketIp(
        buffer : ByteBuffer,
        sendV6 : Boolean,
    ) : Boolean {
        val ipAddr = if (sendV6) ByteArray(16) else ByteArray(4)
        buffer.get(ipAddr)
        val srcIp = if (sendV6) Inet6Address.getByAddress(ipAddr)
                else Inet4Address.getByAddress(ipAddr)
        buffer.get(ipAddr)
        val dstIp = if (sendV6) Inet6Address.getByAddress(ipAddr)
                else Inet4Address.getByAddress(ipAddr)

        Log.e(TAG, "IP Src:" + srcIp + " dst: " + dstIp)

        if ((sendV6 && srcIp == LOCAL_IPV6_ADDRESS && dstIp == TEST_TARGET_IPV6_ADDR) ||
                (!sendV6 && srcIp == LOCAL_IPV4_ADDRESS && dstIp == TEST_TARGET_IPV4_ADDR)) {
            Log.e(TAG, "IP return true");
            return true
        }
        Log.e(TAG, "IP return false");
        return false
    }

    fun parsePacketPort(
        buffer : ByteBuffer,
        srcPort : Int,
        dstPort : Int
    ) : Boolean {
        if (srcPort == 0 && dstPort == 0) return true

        val packetSrcPort = buffer.getShort().toInt()
        val packetDstPort = buffer.getShort().toInt()

        Log.e(TAG, "Port Src:" + packetSrcPort + " dst: " + packetDstPort)

        if ((srcPort == 0 || (srcPort != 0 && srcPort == packetSrcPort)) &&
                (dstPort == 0 || (dstPort != 0 && dstPort == packetDstPort))) {
            Log.e(TAG, "Port return true");
            return true
        }
        Log.e(TAG, "Port return false");
        return false
    }

    fun validatePacket(
        agent : TestableNetworkAgent,
        sendV6 : Boolean = false,
        dscpValue : Int = 0,
        dstPort : Int = 0,
    ) {
        var packetFound = false;
        sendPacket(agent, sendV6, dstPort)
        // TODO: grab source port from socket in sendPacket

        Log.e(TAG, "find DSCP value:" + dscpValue)
        generateSequence { reader.poll(PACKET_TIMEOUT_MS) }.forEach { packet ->
            val buffer = ByteBuffer.wrap(packet, 0, packet.size).order(ByteOrder.BIG_ENDIAN)
            val dscp = if (sendV6) parseV6PacketDscp(buffer) else parseV4PacketDscp(buffer)
            Log.e(TAG, "DSCP value:" + dscp)

            // TODO: Add source port comparison. Use 0 for now.
            if (parsePacketIp(buffer, sendV6) && parsePacketPort(buffer, 0, dstPort)) {
                Log.e(TAG, "DSCP value found")
                assertEquals(dscpValue, dscp)
                packetFound = true
            }
        }
        assertTrue(packetFound)
    }

    fun doRemovePolicyTest(
        agent: TestableNetworkAgent,
        callback: TestableNetworkCallback,
        policyId: Int
    ) {
        val portNumber = 1111 * policyId
        agent.sendRemoveDscpPolicy(policyId)
        agent.expectCallback<OnDscpPolicyStatusUpdated>().let {
            assertEquals(policyId, it.policyId)
            assertEquals(DSCP_POLICY_STATUS_DELETED, it.status)
        }
    }

    @Test
    fun testDscpPolicyAddPolicies(): Unit = createConnectedNetworkAgent().let {
                (agent, callback) ->
        val policy = DscpPolicy.Builder(1, 1)
                .setDestinationPortRange(Range(4444, 4444)).build()
        agent.sendAddDscpPolicy(policy)
        agent.expectCallback<OnDscpPolicyStatusUpdated>().let {
            assertEquals(1, it.policyId)
            assertEquals(DSCP_POLICY_STATUS_SUCCESS, it.status)
        }
        validatePacket(agent, dscpValue = 1, dstPort = 4444)

        agent.sendRemoveDscpPolicy(1)
        agent.expectCallback<OnDscpPolicyStatusUpdated>().let {
            assertEquals(1, it.policyId)
            assertEquals(DSCP_POLICY_STATUS_DELETED, it.status)
        }

        val policy2 = DscpPolicy.Builder(1, 4)
                .setDestinationPortRange(Range(5555, 5555))
                .setDestinationAddress(TEST_TARGET_IPV4_ADDR)
                .setSourceAddress(LOCAL_IPV4_ADDRESS)
                .setProtocol(IPPROTO_UDP).build()
        agent.sendAddDscpPolicy(policy2)
        agent.expectCallback<OnDscpPolicyStatusUpdated>().let {
            assertEquals(1, it.policyId)
            assertEquals(DSCP_POLICY_STATUS_SUCCESS, it.status)
        }

        validatePacket(agent, dscpValue = 4, dstPort = 5555)

        agent.sendRemoveDscpPolicy(1)
        agent.expectCallback<OnDscpPolicyStatusUpdated>().let {
            assertEquals(1, it.policyId)
            assertEquals(DSCP_POLICY_STATUS_DELETED, it.status)
        }
    }

    @Test
    fun testDscpPolicyAddV6Policies(): Unit = createConnectedNetworkAgent().let {
                (agent, callback) ->
        val policy = DscpPolicy.Builder(1, 1)
                .setDestinationPortRange(Range(4444, 4444)).build()
        agent.sendAddDscpPolicy(policy)
        agent.expectCallback<OnDscpPolicyStatusUpdated>().let {
            assertEquals(1, it.policyId)
            assertEquals(DSCP_POLICY_STATUS_SUCCESS, it.status)
        }
        validatePacket(agent, true, dscpValue = 1, dstPort = 4444)

        agent.sendRemoveDscpPolicy(1)
        agent.expectCallback<OnDscpPolicyStatusUpdated>().let {
            assertEquals(1, it.policyId)
            assertEquals(DSCP_POLICY_STATUS_DELETED, it.status)
        }

        val policy2 = DscpPolicy.Builder(1, 4)
                .setDestinationPortRange(Range(5555, 5555))
                .setDestinationAddress(TEST_TARGET_IPV6_ADDR)
                .setSourceAddress(LOCAL_IPV6_ADDRESS)
                .setProtocol(IPPROTO_UDP).build()
        agent.sendAddDscpPolicy(policy2)
        agent.expectCallback<OnDscpPolicyStatusUpdated>().let {
            assertEquals(1, it.policyId)
            assertEquals(DSCP_POLICY_STATUS_SUCCESS, it.status)
        }
        validatePacket(agent, true, dscpValue = 4, dstPort = 5555)

        agent.sendRemoveDscpPolicy(1)
        agent.expectCallback<OnDscpPolicyStatusUpdated>().let {
            assertEquals(1, it.policyId)
            assertEquals(DSCP_POLICY_STATUS_DELETED, it.status)
        }
    }

    @Test
    // Remove policies in the same order as addition.
    fun testRemoveDscpPolicy_RemoveSameOrderAsAdd(): Unit = createConnectedNetworkAgent().let {
                (agent, callback) ->
        val policy = DscpPolicy.Builder(1, 1).setDestinationPortRange(Range(1111, 1111)).build()
        agent.sendAddDscpPolicy(policy)
        agent.expectCallback<OnDscpPolicyStatusUpdated>().let {
            assertEquals(1, it.policyId)
            assertEquals(DSCP_POLICY_STATUS_SUCCESS, it.status)
            validatePacket(agent, dscpValue = 1, dstPort = 1111)
        }

        val policy2 = DscpPolicy.Builder(2, 1).setDestinationPortRange(Range(2222, 2222)).build()
        agent.sendAddDscpPolicy(policy2)
        agent.expectCallback<OnDscpPolicyStatusUpdated>().let {
            assertEquals(2, it.policyId)
            assertEquals(DSCP_POLICY_STATUS_SUCCESS, it.status)
            validatePacket(agent, dscpValue = 1, dstPort = 2222)
        }

        val policy3 = DscpPolicy.Builder(3, 1).setDestinationPortRange(Range(3333, 3333)).build()
        agent.sendAddDscpPolicy(policy3)
        agent.expectCallback<OnDscpPolicyStatusUpdated>().let {
            assertEquals(3, it.policyId)
            assertEquals(DSCP_POLICY_STATUS_SUCCESS, it.status)
            validatePacket(agent, dscpValue = 1, dstPort = 3333)
        }

        /* Remove Policies and check CE is no longer set */
        doRemovePolicyTest(agent, callback, 1)
        validatePacket(agent, dscpValue = 0, dstPort = 1111)
        doRemovePolicyTest(agent, callback, 2)
        validatePacket(agent, dscpValue = 0, dstPort = 2222)
        doRemovePolicyTest(agent, callback, 3)
        validatePacket(agent, dscpValue = 0, dstPort = 3333)
    }

    @Test
    fun testRemoveDscpPolicy_RemoveImmediatelyAfterAdd(): Unit =
            createConnectedNetworkAgent().let { (agent, callback) ->
        val policy = DscpPolicy.Builder(1, 1).setDestinationPortRange(Range(1111, 1111)).build()
        agent.sendAddDscpPolicy(policy)
        agent.expectCallback<OnDscpPolicyStatusUpdated>().let {
            assertEquals(1, it.policyId)
            assertEquals(DSCP_POLICY_STATUS_SUCCESS, it.status)
            validatePacket(agent, dscpValue = 1, dstPort = 1111)
        }
        doRemovePolicyTest(agent, callback, 1)

        val policy2 = DscpPolicy.Builder(2, 1).setDestinationPortRange(Range(2222, 2222)).build()
        agent.sendAddDscpPolicy(policy2)
        agent.expectCallback<OnDscpPolicyStatusUpdated>().let {
            assertEquals(2, it.policyId)
            assertEquals(DSCP_POLICY_STATUS_SUCCESS, it.status)
            validatePacket(agent, dscpValue = 1, dstPort = 2222)
        }
        doRemovePolicyTest(agent, callback, 2)

        val policy3 = DscpPolicy.Builder(3, 1).setDestinationPortRange(Range(3333, 3333)).build()
        agent.sendAddDscpPolicy(policy3)
        agent.expectCallback<OnDscpPolicyStatusUpdated>().let {
            assertEquals(3, it.policyId)
            assertEquals(DSCP_POLICY_STATUS_SUCCESS, it.status)
            validatePacket(agent, dscpValue = 1, dstPort = 3333)
        }
        doRemovePolicyTest(agent, callback, 3)
    }

    @Test
    // Remove policies in reverse order from addition.
    fun testRemoveDscpPolicy_RemoveReverseOrder(): Unit =
            createConnectedNetworkAgent().let { (agent, callback) ->
        val policy = DscpPolicy.Builder(1, 1).setDestinationPortRange(Range(1111, 1111)).build()
        agent.sendAddDscpPolicy(policy)
        agent.expectCallback<OnDscpPolicyStatusUpdated>().let {
            assertEquals(1, it.policyId)
            assertEquals(DSCP_POLICY_STATUS_SUCCESS, it.status)
            validatePacket(agent, dscpValue = 1, dstPort = 1111)
        }

        val policy2 = DscpPolicy.Builder(2, 1).setDestinationPortRange(Range(2222, 2222)).build()
        agent.sendAddDscpPolicy(policy2)
        agent.expectCallback<OnDscpPolicyStatusUpdated>().let {
            assertEquals(2, it.policyId)
            assertEquals(DSCP_POLICY_STATUS_SUCCESS, it.status)
            validatePacket(agent, dscpValue = 1, dstPort = 2222)
        }

        val policy3 = DscpPolicy.Builder(3, 1).setDestinationPortRange(Range(3333, 3333)).build()
        agent.sendAddDscpPolicy(policy3)
        agent.expectCallback<OnDscpPolicyStatusUpdated>().let {
            assertEquals(3, it.policyId)
            assertEquals(DSCP_POLICY_STATUS_SUCCESS, it.status)
            validatePacket(agent, dscpValue = 1, dstPort = 3333)
        }

        /* Remove Policies and check CE is no longer set */
        doRemovePolicyTest(agent, callback, 3)
        doRemovePolicyTest(agent, callback, 2)
        doRemovePolicyTest(agent, callback, 1)
    }

    @Test
    fun testRemoveDscpPolicy_InvalidPolicy(): Unit = createConnectedNetworkAgent().let {
                (agent, callback) ->
        agent.sendRemoveDscpPolicy(3)
        // Is there something to add in TestableNetworkCallback to NOT expect a callback?
        // Or should we send DSCP_POLICY_STATUS_DELETED in any case or a different STATUS?
    }

    @Test
    fun testRemoveAllDscpPolicies(): Unit = createConnectedNetworkAgent().let {
                (agent, callback) ->
        val policy = DscpPolicy.Builder(1, 1)
                .setDestinationPortRange(Range(1111, 1111)).build()
        agent.sendAddDscpPolicy(policy)
        agent.expectCallback<OnDscpPolicyStatusUpdated>().let {
            assertEquals(1, it.policyId)
            assertEquals(DSCP_POLICY_STATUS_SUCCESS, it.status)
            validatePacket(agent, dscpValue = 1, dstPort = 1111)
        }

        val policy2 = DscpPolicy.Builder(2, 1)
                .setDestinationPortRange(Range(2222, 2222)).build()
        agent.sendAddDscpPolicy(policy2)
        agent.expectCallback<OnDscpPolicyStatusUpdated>().let {
            assertEquals(2, it.policyId)
            assertEquals(DSCP_POLICY_STATUS_SUCCESS, it.status)
            validatePacket(agent, dscpValue = 1, dstPort = 2222)
        }

        val policy3 = DscpPolicy.Builder(3, 1)
                .setDestinationPortRange(Range(3333, 3333)).build()
        agent.sendAddDscpPolicy(policy3)
        agent.expectCallback<OnDscpPolicyStatusUpdated>().let {
            assertEquals(3, it.policyId)
            assertEquals(DSCP_POLICY_STATUS_SUCCESS, it.status)
            validatePacket(agent, dscpValue = 1, dstPort = 3333)
        }

        agent.sendRemoveAllDscpPolicies()
        agent.expectCallback<OnDscpPolicyStatusUpdated>().let {
            assertEquals(1, it.policyId)
            assertEquals(DSCP_POLICY_STATUS_DELETED, it.status)
            validatePacket(agent, false, dstPort = 1111)
        }
        agent.expectCallback<OnDscpPolicyStatusUpdated>().let {
            assertEquals(2, it.policyId)
            assertEquals(DSCP_POLICY_STATUS_DELETED, it.status)
            validatePacket(agent, false, dstPort = 2222)
        }
        agent.expectCallback<OnDscpPolicyStatusUpdated>().let {
            assertEquals(3, it.policyId)
            assertEquals(DSCP_POLICY_STATUS_DELETED, it.status)
            validatePacket(agent, false, dstPort = 3333)
        }
    }

    @Test
    fun testAddDuplicateDscpPolicy(): Unit = createConnectedNetworkAgent().let {
                (agent, callback) ->
        val policy = DscpPolicy.Builder(1, 1).setDestinationPortRange(Range(4444, 4444)).build()
        agent.sendAddDscpPolicy(policy)
        agent.expectCallback<OnDscpPolicyStatusUpdated>().let {
            assertEquals(1, it.policyId)
            assertEquals(DSCP_POLICY_STATUS_SUCCESS, it.status)
            validatePacket(agent, dscpValue = 1, dstPort = 4444)
        }

        val policy2 = DscpPolicy.Builder(1, 1).setDestinationPortRange(Range(5555, 5555)).build()
        agent.sendAddDscpPolicy(policy2)
        agent.expectCallback<OnDscpPolicyStatusUpdated>().let {
            assertEquals(1, it.policyId)
            assertEquals(DSCP_POLICY_STATUS_SUCCESS, it.status)

            // Sending packet with old policy should fail
            validatePacket(agent, dscpValue = 0, dstPort = 4444)
            validatePacket(agent, dscpValue = 1, dstPort = 5555)
        }

        agent.sendRemoveDscpPolicy(1)
        agent.expectCallback<OnDscpPolicyStatusUpdated>().let {
            assertEquals(1, it.policyId)
            assertEquals(DSCP_POLICY_STATUS_DELETED, it.status)
        }
    }

    @Test
    fun testParcelingDscpPolicyIsLossless(): Unit = createConnectedNetworkAgent().let {
                (agent, callback) ->
        val policyId = 1
        val dscpValue = 1
        val range = Range(4444, 4444)
        val srcPort = 555

        // Check that policy with partial parameters is lossless.
        val policy = DscpPolicy.Builder(policyId, dscpValue).setDestinationPortRange(range).build()
        assertEquals(policyId, policy.policyId)
        assertEquals(dscpValue, policy.dscpValue)
        assertEquals(range, policy.destinationPortRange)
        assertParcelingIsLossless(policy)

        // Check that policy with all parameters is lossless.
        val policy2 = DscpPolicy.Builder(policyId, dscpValue).setDestinationPortRange(range)
                .setSourceAddress(LOCAL_IPV4_ADDRESS)
                .setDestinationAddress(TEST_TARGET_IPV4_ADDR)
                .setSourcePort(srcPort)
                .setProtocol(IPPROTO_UDP).build()
        assertEquals(policyId, policy2.policyId)
        assertEquals(dscpValue, policy2.dscpValue)
        assertEquals(range, policy2.destinationPortRange)
        assertEquals(TEST_TARGET_IPV4_ADDR, policy2.destinationAddress)
        assertEquals(LOCAL_IPV4_ADDRESS, policy2.sourceAddress)
        assertEquals(srcPort, policy2.sourcePort)
        assertEquals(IPPROTO_UDP, policy2.protocol)
        assertParcelingIsLossless(policy2)
    }
}

private fun ByteBuffer.readAsArray(): ByteArray {
    val out = ByteArray(remaining())
    get(out)
    return out
}

private fun <T> Context.assertHasService(manager: Class<T>): T {
    return getSystemService(manager) ?: fail("Service $manager not found")
}
