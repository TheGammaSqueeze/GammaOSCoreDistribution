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

package com.android.net.module.util.netlink;

import static android.system.OsConstants.NETLINK_ROUTE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.net.InetAddresses;
import android.net.IpPrefix;
import android.system.OsConstants;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.net.module.util.HexDump;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.Inet6Address;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class RtNetlinkRouteMessageTest {
    private static final IpPrefix TEST_IPV6_GLOBAL_PREFIX = new IpPrefix("2001:db8:1::/64");
    private static final Inet6Address TEST_IPV6_LINK_LOCAL_GATEWAY =
            (Inet6Address) InetAddresses.parseNumericAddress("fe80::1");

    // An example of the full RTM_NEWROUTE message.
    private static final String RTM_NEWROUTE_HEX =
            "88000000180000060000000000000000"            // struct nlmsghr
            + "0A400000FC02000100000000"                  // struct rtmsg
            + "08000F00C7060000"                          // RTA_TABLE
            + "1400010020010DB8000100000000000000000000"  // RTA_DST
            + "08000400DF020000"                          // RTA_OIF
            + "0800060000010000"                          // RTA_PRIORITY
            + "24000C0000000000000000005EEA000000000000"  // RTA_CACHEINFO
            + "00000000000000000000000000000000"
            + "14000500FE800000000000000000000000000001"  // RTA_GATEWAY
            + "0500140000000000";                         // RTA_PREF

    private ByteBuffer toByteBuffer(final String hexString) {
        return ByteBuffer.wrap(HexDump.hexStringToByteArray(hexString));
    }

    private void assertRtmRouteMessage(final RtNetlinkRouteMessage routeMsg) {
        final StructNlMsgHdr hdr = routeMsg.getHeader();
        assertNotNull(hdr);
        assertEquals(136, hdr.nlmsg_len);
        assertEquals(NetlinkConstants.RTM_NEWROUTE, hdr.nlmsg_type);
        assertEquals(0x600, hdr.nlmsg_flags);
        assertEquals(0, hdr.nlmsg_seq);
        assertEquals(0, hdr.nlmsg_pid);

        final StructRtMsg rtmsg = routeMsg.getRtMsgHeader();
        assertNotNull(rtmsg);
        assertEquals((byte) OsConstants.AF_INET6, rtmsg.family);
        assertEquals(64, rtmsg.dstLen);
        assertEquals(0, rtmsg.srcLen);
        assertEquals(0, rtmsg.tos);
        assertEquals(0xFC, rtmsg.table);
        assertEquals(NetlinkConstants.RTPROT_KERNEL, rtmsg.protocol);
        assertEquals(NetlinkConstants.RT_SCOPE_UNIVERSE, rtmsg.scope);
        assertEquals(NetlinkConstants.RTN_UNICAST, rtmsg.type);
        assertEquals(0, rtmsg.flags);

        assertEquals(routeMsg.getDestination(), TEST_IPV6_GLOBAL_PREFIX);
        assertEquals(735, routeMsg.getInterfaceIndex());
        assertEquals((Inet6Address) routeMsg.getGateway(), TEST_IPV6_LINK_LOCAL_GATEWAY);
    }

    @Test
    public void testParseRtmRouteMessage() {
        final ByteBuffer byteBuffer = toByteBuffer(RTM_NEWROUTE_HEX);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);  // For testing.

        final NetlinkMessage msg = NetlinkMessage.parse(byteBuffer, NETLINK_ROUTE);
        assertNotNull(msg);
        assertTrue(msg instanceof RtNetlinkRouteMessage);
        final RtNetlinkRouteMessage routeMsg = (RtNetlinkRouteMessage) msg;
        assertRtmRouteMessage(routeMsg);
    }

    private static final String RTM_NEWROUTE_PACK_HEX =
            "4C000000180000060000000000000000"             // struct nlmsghr
            + "0A400000FC02000100000000"                   // struct rtmsg
            + "1400010020010DB8000100000000000000000000"   // RTA_DST
            + "14000500FE800000000000000000000000000001"   // RTA_GATEWAY
            + "08000400DF020000";                          // RTA_OIF

    @Test
    public void testPackRtmNewRoute() {
        final ByteBuffer byteBuffer = toByteBuffer(RTM_NEWROUTE_PACK_HEX);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);  // For testing.
        final NetlinkMessage msg = NetlinkMessage.parse(byteBuffer, NETLINK_ROUTE);
        assertNotNull(msg);
        assertTrue(msg instanceof RtNetlinkRouteMessage);
        final RtNetlinkRouteMessage routeMsg = (RtNetlinkRouteMessage) msg;

        final ByteBuffer packBuffer = ByteBuffer.allocate(76);
        packBuffer.order(ByteOrder.LITTLE_ENDIAN);  // For testing.
        routeMsg.pack(packBuffer);
        assertEquals(RTM_NEWROUTE_PACK_HEX, HexDump.toHexString(packBuffer.array()));
    }

    private static final String RTM_NEWROUTE_TRUNCATED_HEX =
            "48000000180000060000000000000000"             // struct nlmsghr
            + "0A400000FC02000100000000"                   // struct rtmsg
            + "1400010020010DB8000100000000000000000000"   // RTA_DST
            + "10000500FE8000000000000000000000"           // RTA_GATEWAY(truncated)
            + "08000400DF020000";                          // RTA_OIF

    @Test
    public void testTruncatedRtmNewRoute() {
        final ByteBuffer byteBuffer = toByteBuffer(RTM_NEWROUTE_TRUNCATED_HEX);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);  // For testing.
        final NetlinkMessage msg = NetlinkMessage.parse(byteBuffer, NETLINK_ROUTE);
        // Parsing RTM_NEWROUTE with truncated RTA_GATEWAY attribute returns null.
        assertNull(msg);
    }

    private static final String RTM_NEWROUTE_IPV4_MAPPED_IPV6_GATEWAY_HEX =
            "4C000000180000060000000000000000"             // struct nlmsghr
            + "0A400000FC02000100000000"                   // struct rtmsg
            + "1400010020010DB8000100000000000000000000"   // RTA_DST(2001:db8:1::/64)
            + "1400050000000000000000000000FFFF0A010203"   // RTA_GATEWAY(::ffff:10.1.2.3)
            + "08000400DF020000";                          // RTA_OIF

    @Test
    public void testParseRtmRouteMessage_IPv4MappedIPv6Gateway() {
        final ByteBuffer byteBuffer = toByteBuffer(RTM_NEWROUTE_IPV4_MAPPED_IPV6_GATEWAY_HEX);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);  // For testing.
        final NetlinkMessage msg = NetlinkMessage.parse(byteBuffer, NETLINK_ROUTE);
        // Parsing RTM_NEWROUTE with IPv4-mapped IPv6 gateway address, which doesn't match
        // rtm_family after address parsing.
        assertNull(msg);
    }

    private static final String RTM_NEWROUTE_IPV4_MAPPED_IPV6_DST_HEX =
            "4C000000180000060000000000000000"             // struct nlmsghr
            + "0A780000FC02000100000000"                   // struct rtmsg
            + "1400010000000000000000000000FFFF0A000000"   // RTA_DST(::ffff:10.0.0.0/120)
            + "14000500FE800000000000000000000000000001"   // RTA_GATEWAY(fe80::1)
            + "08000400DF020000";                          // RTA_OIF

    @Test
    public void testParseRtmRouteMessage_IPv4MappedIPv6Destination() {
        final ByteBuffer byteBuffer = toByteBuffer(RTM_NEWROUTE_IPV4_MAPPED_IPV6_DST_HEX);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);  // For testing.
        final NetlinkMessage msg = NetlinkMessage.parse(byteBuffer, NETLINK_ROUTE);
        // Parsing RTM_NEWROUTE with IPv4-mapped IPv6 destination prefix, which doesn't match
        // rtm_family after address parsing.
        assertNull(msg);
    }

    // An example of the full RTM_NEWADDR message.
    private static final String RTM_NEWADDR_HEX =
            "48000000140000000000000000000000"            // struct nlmsghr
            + "0A4080FD1E000000"                          // struct ifaddrmsg
            + "14000100FE800000000000002C415CFFFE096665"  // IFA_ADDRESS
            + "14000600100E0000201C00002A70000045700000"  // IFA_CACHEINFO
            + "0800080080000000";                         // IFA_FLAGS

    @Test
    public void testParseMultipleRtmMessagesInOneByteBuffer() {
        final ByteBuffer byteBuffer = toByteBuffer(RTM_NEWROUTE_HEX + RTM_NEWADDR_HEX);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);  // For testing.

        // Try to parse the RTM_NEWROUTE message.
        NetlinkMessage msg = NetlinkMessage.parse(byteBuffer, NETLINK_ROUTE);
        assertNotNull(msg);
        assertTrue(msg instanceof RtNetlinkRouteMessage);
        final RtNetlinkRouteMessage routeMsg = (RtNetlinkRouteMessage) msg;
        assertRtmRouteMessage(routeMsg);

        // Try to parse the RTM_NEWADDR message.
        msg = NetlinkMessage.parse(byteBuffer, NETLINK_ROUTE);
        assertNotNull(msg);
        assertTrue(msg instanceof RtNetlinkAddressMessage);
    }

    @Test
    public void testToString() {
        final ByteBuffer byteBuffer = toByteBuffer(RTM_NEWROUTE_HEX);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);  // For testing.
        final NetlinkMessage msg = NetlinkMessage.parse(byteBuffer, NETLINK_ROUTE);
        assertNotNull(msg);
        assertTrue(msg instanceof RtNetlinkRouteMessage);
        final RtNetlinkRouteMessage routeMsg = (RtNetlinkRouteMessage) msg;
        final String expected = "RtNetlinkRouteMessage{ "
                + "nlmsghdr{"
                + "StructNlMsgHdr{ nlmsg_len{136}, nlmsg_type{24(RTM_NEWROUTE)}, "
                + "nlmsg_flags{1536(NLM_F_MATCH)}, nlmsg_seq{0}, nlmsg_pid{0} }}, "
                + "Rtmsg{"
                + "family: 10, dstLen: 64, srcLen: 0, tos: 0, table: 252, protocol: 2, "
                + "scope: 0, type: 1, flags: 0}, "
                + "destination{2001:db8:1::}, "
                + "gateway{fe80::1}, "
                + "ifindex{735} "
                + "}";
        assertEquals(expected, routeMsg.toString());
    }
}
