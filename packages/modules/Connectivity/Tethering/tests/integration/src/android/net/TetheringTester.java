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

package android.net;

import static com.android.net.module.util.NetworkStackConstants.ARP_REPLY;
import static com.android.net.module.util.NetworkStackConstants.ARP_REQUEST;
import static com.android.net.module.util.NetworkStackConstants.ETHER_ADDR_LEN;
import static com.android.net.module.util.NetworkStackConstants.ETHER_BROADCAST;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.net.dhcp.DhcpAckPacket;
import android.net.dhcp.DhcpOfferPacket;
import android.net.dhcp.DhcpPacket;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.networkstack.arp.ArpPacket;
import com.android.testutils.TapPacketReader;

import java.net.Inet4Address;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

/**
 * A class simulate tethered client. When caller create TetheringTester, it would connect to
 * tethering module that do the dhcp and slaac to obtain ipv4 and ipv6 address. Then caller can
 * send/receive packets by this class.
 */
public final class TetheringTester {
    private static final String TAG = TetheringTester.class.getSimpleName();
    private static final int PACKET_READ_TIMEOUT_MS = 100;
    private static final int DHCP_DISCOVER_ATTEMPTS = 10;
    private static final byte[] DHCP_REQUESTED_PARAMS = new byte[] {
            DhcpPacket.DHCP_SUBNET_MASK,
            DhcpPacket.DHCP_ROUTER,
            DhcpPacket.DHCP_DNS_SERVER,
            DhcpPacket.DHCP_LEASE_TIME,
    };

    public static final String DHCP_HOSTNAME = "testhostname";

    private final ArrayMap<MacAddress, TetheredDevice> mTetheredDevices;
    private final TapPacketReader mDownstreamReader;

    public TetheringTester(TapPacketReader downstream) {
        if (downstream == null) fail("Downstream reader could not be NULL");

        mDownstreamReader = downstream;
        mTetheredDevices = new ArrayMap<>();
    }

    public TetheredDevice createTetheredDevice(MacAddress macAddr) throws Exception {
        if (mTetheredDevices.get(macAddr) != null) {
            fail("Tethered device already created");
        }

        TetheredDevice tethered = new TetheredDevice(macAddr);
        mTetheredDevices.put(macAddr, tethered);

        return tethered;
    }

    public class TetheredDevice {
        public final MacAddress macAddr;
        public final MacAddress routerMacAddr;
        public final Inet4Address ipv4Addr;

        private TetheredDevice(MacAddress mac) throws Exception {
            macAddr = mac;

            DhcpResults dhcpResults = runDhcp(macAddr.toByteArray());
            ipv4Addr = (Inet4Address) dhcpResults.ipAddress.getAddress();
            routerMacAddr = getRouterMacAddressFromArp(ipv4Addr, macAddr,
                    dhcpResults.serverAddress);
        }
    }

    /** Simulate dhcp client to obtain ipv4 address. */
    public DhcpResults runDhcp(byte[] clientMacAddr)
            throws Exception {
        // We have to retransmit DHCP requests because IpServer declares itself to be ready before
        // its DhcpServer is actually started. TODO: fix this race and remove this loop.
        DhcpPacket offerPacket = null;
        for (int i = 0; i < DHCP_DISCOVER_ATTEMPTS; i++) {
            Log.d(TAG, "Sending DHCP discover");
            sendDhcpDiscover(clientMacAddr);
            offerPacket = getNextDhcpPacket();
            if (offerPacket instanceof DhcpOfferPacket) break;
        }
        if (!(offerPacket instanceof DhcpOfferPacket)) {
            throw new TimeoutException("No DHCPOFFER received on interface within timeout");
        }

        sendDhcpRequest(offerPacket, clientMacAddr);
        DhcpPacket ackPacket = getNextDhcpPacket();
        if (!(ackPacket instanceof DhcpAckPacket)) {
            throw new TimeoutException("No DHCPACK received on interface within timeout");
        }

        return ackPacket.toDhcpResults();
    }

    private void sendDhcpDiscover(byte[] macAddress) throws Exception {
        ByteBuffer packet = DhcpPacket.buildDiscoverPacket(DhcpPacket.ENCAP_L2,
                new Random().nextInt() /* transactionId */, (short) 0 /* secs */,
                macAddress,  false /* unicast */, DHCP_REQUESTED_PARAMS,
                false /* rapid commit */,  DHCP_HOSTNAME);
        mDownstreamReader.sendResponse(packet);
    }

    private void sendDhcpRequest(DhcpPacket offerPacket, byte[] macAddress)
            throws Exception {
        DhcpResults results = offerPacket.toDhcpResults();
        Inet4Address clientIp = (Inet4Address) results.ipAddress.getAddress();
        Inet4Address serverIdentifier = results.serverAddress;
        ByteBuffer packet = DhcpPacket.buildRequestPacket(DhcpPacket.ENCAP_L2,
                0 /* transactionId */, (short) 0 /* secs */, DhcpPacket.INADDR_ANY /* clientIp */,
                false /* broadcast */, macAddress, clientIp /* requestedIpAddress */,
                serverIdentifier, DHCP_REQUESTED_PARAMS, DHCP_HOSTNAME);
        mDownstreamReader.sendResponse(packet);
    }

    private DhcpPacket getNextDhcpPacket() throws Exception {
        final byte[] packet = getNextMatchedPacket((p) -> {
            // Test whether this is DHCP packet.
            try {
                DhcpPacket.decodeFullPacket(p, p.length, DhcpPacket.ENCAP_L2);
            } catch (DhcpPacket.ParseException e) {
                // Not a DHCP packet.
                return false;
            }

            return true;
        });

        return packet == null ? null :
                DhcpPacket.decodeFullPacket(packet, packet.length, DhcpPacket.ENCAP_L2);
    }

    @Nullable
    private ArpPacket parseArpPacket(final byte[] packet) {
        try {
            return ArpPacket.parseArpPacket(packet, packet.length);
        } catch (ArpPacket.ParseException e) {
            return null;
        }
    }

    private void maybeReplyArp(byte[] packet) {
        ByteBuffer buf = ByteBuffer.wrap(packet);

        final ArpPacket arpPacket = parseArpPacket(packet);
        if (arpPacket == null || arpPacket.opCode != ARP_REQUEST) return;

        for (int i = 0; i < mTetheredDevices.size(); i++) {
            TetheredDevice tethered = mTetheredDevices.valueAt(i);
            if (!arpPacket.targetIp.equals(tethered.ipv4Addr)) continue;

            final ByteBuffer arpReply = ArpPacket.buildArpPacket(
                    arpPacket.senderHwAddress.toByteArray() /* dst */,
                    tethered.macAddr.toByteArray() /* srcMac */,
                    arpPacket.senderIp.getAddress() /* target IP */,
                    arpPacket.senderHwAddress.toByteArray() /* target HW address */,
                    tethered.ipv4Addr.getAddress() /* sender IP */,
                    (short) ARP_REPLY);
            try {
                sendPacket(arpReply);
            } catch (Exception e) {
                fail("Failed to reply ARP for " + tethered.ipv4Addr);
            }
            return;
        }
    }

    private MacAddress getRouterMacAddressFromArp(final Inet4Address tetherIp,
            final MacAddress tetherMac, final Inet4Address routerIp) throws Exception {
        final ByteBuffer arpProbe = ArpPacket.buildArpPacket(ETHER_BROADCAST /* dst */,
                tetherMac.toByteArray() /* srcMac */, routerIp.getAddress() /* target IP */,
                new byte[ETHER_ADDR_LEN] /* target HW address */,
                tetherIp.getAddress() /* sender IP */, (short) ARP_REQUEST);
        sendPacket(arpProbe);

        final byte[] packet = getNextMatchedPacket((p) -> {
            final ArpPacket arpPacket = parseArpPacket(p);
            if (arpPacket == null || arpPacket.opCode != ARP_REPLY) return false;
            return arpPacket.targetIp.equals(tetherIp);
        });

        if (packet != null) {
            Log.d(TAG, "Get Mac address from ARP");
            final ArpPacket arpReply = ArpPacket.parseArpPacket(packet, packet.length);
            return arpReply.senderHwAddress;
        }

        fail("Could not get ARP packet");
        return null;
    }

    public void sendPacket(ByteBuffer packet) throws Exception {
        mDownstreamReader.sendResponse(packet);
    }

    public byte[] getNextMatchedPacket(Predicate<byte[]> filter) {
        byte[] packet;
        while ((packet = mDownstreamReader.poll(PACKET_READ_TIMEOUT_MS)) != null) {
            if (filter.test(packet)) return packet;

            maybeReplyArp(packet);
        }

        return null;
    }

    public void verifyUpload(final RemoteResponder dst, final ByteBuffer packet,
            final Predicate<byte[]> filter) throws Exception {
        sendPacket(packet);
        assertNotNull("Upload fail", dst.getNextMatchedPacket(filter));
    }

    public static class RemoteResponder {
        final TapPacketReader mUpstreamReader;
        public RemoteResponder(TapPacketReader reader) {
            mUpstreamReader = reader;
        }

        public void sendPacket(ByteBuffer packet) throws Exception {
            mUpstreamReader.sendResponse(packet);
        }

        public byte[] getNextMatchedPacket(Predicate<byte[]> filter) throws Exception {
            return mUpstreamReader.poll(PACKET_READ_TIMEOUT_MS, filter);
        }

        public void verifyDownload(final TetheringTester dst, final ByteBuffer packet,
                final Predicate<byte[]> filter) throws Exception {
            sendPacket(packet);
            assertNotNull("Download fail", dst.getNextMatchedPacket(filter));
        }
    }
}
