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
public class RtNetlinkAddressMessageTest {
    private static final Inet6Address TEST_LINK_LOCAL =
            (Inet6Address) InetAddresses.parseNumericAddress("fe80::2C41:5CFF:FE09:6665");

    // An example of the full RTM_NEWADDR message.
    private static final String RTM_NEWADDR_HEX =
            "48000000140000000000000000000000"            // struct nlmsghr
            + "0A4080FD1E000000"                          // struct ifaddrmsg
            + "14000100FE800000000000002C415CFFFE096665"  // IFA_ADDRESS
            + "14000600100E0000201C00002A70000045700000"  // IFA_CACHEINFO
            + "0800080080000000";                         // IFA_FLAGS

    private ByteBuffer toByteBuffer(final String hexString) {
        return ByteBuffer.wrap(HexDump.hexStringToByteArray(hexString));
    }

    @Test
    public void testParseRtmNewAddress() {
        final ByteBuffer byteBuffer = toByteBuffer(RTM_NEWADDR_HEX);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);  // For testing.
        final NetlinkMessage msg = NetlinkMessage.parse(byteBuffer, NETLINK_ROUTE);
        assertNotNull(msg);
        assertTrue(msg instanceof RtNetlinkAddressMessage);
        final RtNetlinkAddressMessage addrMsg = (RtNetlinkAddressMessage) msg;

        final StructNlMsgHdr hdr = addrMsg.getHeader();
        assertNotNull(hdr);
        assertEquals(72, hdr.nlmsg_len);
        assertEquals(NetlinkConstants.RTM_NEWADDR, hdr.nlmsg_type);
        assertEquals(0, hdr.nlmsg_flags);
        assertEquals(0, hdr.nlmsg_seq);
        assertEquals(0, hdr.nlmsg_pid);

        final StructIfaddrMsg ifaddrMsgHdr = addrMsg.getIfaddrHeader();
        assertNotNull(ifaddrMsgHdr);
        assertEquals((byte) OsConstants.AF_INET6, ifaddrMsgHdr.family);
        assertEquals(64, ifaddrMsgHdr.prefixLen);
        assertEquals(0x80, ifaddrMsgHdr.flags);
        assertEquals(0xFD, ifaddrMsgHdr.scope);
        assertEquals(30, ifaddrMsgHdr.index);

        assertEquals((Inet6Address) addrMsg.getIpAddress(), TEST_LINK_LOCAL);
        assertEquals(3600L, addrMsg.getIfacacheInfo().preferred);
        assertEquals(7200L, addrMsg.getIfacacheInfo().valid);
        assertEquals(28714, addrMsg.getIfacacheInfo().cstamp);
        assertEquals(28741, addrMsg.getIfacacheInfo().tstamp);
        assertEquals(0x80, addrMsg.getFlags());
    }

    private static final String RTM_NEWADDR_PACK_HEX =
            "48000000140000000000000000000000"             // struct nlmsghr
            + "0A4080FD1E000000"                           // struct ifaddrmsg
            + "14000100FE800000000000002C415CFFFE096665"   // IFA_ADDRESS
            + "14000600FFFFFFFFFFFFFFFF2A7000002A700000"   // IFA_CACHEINFO
            + "0800080081000000";                          // IFA_FLAGS(override ifa_flags)

    @Test
    public void testPackRtmNewAddr() {
        final ByteBuffer byteBuffer = toByteBuffer(RTM_NEWADDR_PACK_HEX);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);  // For testing.
        final NetlinkMessage msg = NetlinkMessage.parse(byteBuffer, NETLINK_ROUTE);
        assertNotNull(msg);
        assertTrue(msg instanceof RtNetlinkAddressMessage);
        final RtNetlinkAddressMessage addrMsg = (RtNetlinkAddressMessage) msg;

        final ByteBuffer packBuffer = ByteBuffer.allocate(72);
        packBuffer.order(ByteOrder.LITTLE_ENDIAN);  // For testing.
        addrMsg.pack(packBuffer);
        assertEquals(RTM_NEWADDR_PACK_HEX, HexDump.toHexString(packBuffer.array()));
    }

    private static final String RTM_NEWADDR_TRUNCATED_HEX =
            "44000000140000000000000000000000"            // struct nlmsghr
            + "0A4080FD1E000000"                          // struct ifaddrmsg
            + "10000100FE800000000000002C415CFF"          // IFA_ADDRESS(truncated)
            + "14000600FFFFFFFFFFFFFFFF2A7000002A700000"  // IFA_CACHEINFO
            + "0800080080000000";                         // IFA_FLAGS

    @Test
    public void testTruncatedRtmNewAddr() {
        final ByteBuffer byteBuffer = toByteBuffer(RTM_NEWADDR_TRUNCATED_HEX);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);  // For testing.
        final NetlinkMessage msg = NetlinkMessage.parse(byteBuffer, NETLINK_ROUTE);
        // Parsing RTM_NEWADDR with truncated IFA_ADDRESS attribute returns null.
        assertNull(msg);
    }

    @Test
    public void testToString() {
        final ByteBuffer byteBuffer = toByteBuffer(RTM_NEWADDR_HEX);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);  // For testing.
        final NetlinkMessage msg = NetlinkMessage.parse(byteBuffer, NETLINK_ROUTE);
        assertNotNull(msg);
        assertTrue(msg instanceof RtNetlinkAddressMessage);
        final RtNetlinkAddressMessage addrMsg = (RtNetlinkAddressMessage) msg;
        final String expected = "RtNetlinkAddressMessage{ "
                + "nlmsghdr{"
                + "StructNlMsgHdr{ nlmsg_len{72}, nlmsg_type{20(RTM_NEWADDR)}, nlmsg_flags{0()}, "
                + "nlmsg_seq{0}, nlmsg_pid{0} }}, "
                + "Ifaddrmsg{"
                + "family: 10, prefixLen: 64, flags: 128, scope: 253, index: 30}, "
                + "IP Address{fe80::2c41:5cff:fe09:6665}, "
                + "IfacacheInfo{"
                + "preferred: 3600, valid: 7200, cstamp: 28714, tstamp: 28741}, "
                + "Address Flags{00000080} "
                + "}";
        assertEquals(expected, addrMsg.toString());
    }
}
