/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.net.shared;

import static android.net.InetAddresses.parseNumericAddress;
import static android.net.ip.IIpClient.PROV_IPV4_DHCP;
import static android.net.ip.IIpClient.PROV_IPV4_DISABLED;
import static android.net.ip.IIpClient.PROV_IPV4_STATIC;
import static android.net.ip.IIpClient.PROV_IPV6_DISABLED;
import static android.net.ip.IIpClient.PROV_IPV6_LINKLOCAL;
import static android.net.ip.IIpClient.PROV_IPV6_SLAAC;
import static android.net.shared.ProvisioningConfiguration.fromStableParcelable;
import static android.net.shared.ProvisioningConfiguration.ipv4ProvisioningModeToString;
import static android.net.shared.ProvisioningConfiguration.ipv6ProvisioningModeToString;

import static com.android.testutils.MiscAsserts.assertFieldCountEquals;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.net.InformationElementParcelable;
import android.net.InitialConfigurationParcelable;
import android.net.Layer2InformationParcelable;
import android.net.LinkAddress;
import android.net.MacAddress;
import android.net.Network;
import android.net.ProvisioningConfigurationParcelable;
import android.net.ScanResultInfoParcelable;
import android.net.StaticIpConfiguration;
import android.net.apf.ApfCapabilities;
import android.net.networkstack.aidl.dhcp.DhcpOption;
import android.net.shared.ProvisioningConfiguration.ScanResultInfo;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Tests for {@link ProvisioningConfiguration}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class ProvisioningConfigurationTest {
    private ProvisioningConfiguration mConfig;

    private ScanResultInfo makeScanResultInfo(final String ssid) {
        final byte[] payload = new byte[] {
            (byte) 0x00, (byte) 0x17, (byte) 0xF2, (byte) 0x06, (byte) 0x01,
            (byte) 0x01, (byte) 0x03, (byte) 0x01, (byte) 0x00, (byte) 0x00,
        };
        final ScanResultInfo.InformationElement ie =
                new ScanResultInfo.InformationElement(0xdd /* vendor specific IE id */,
                        ByteBuffer.wrap(payload));
        return new ScanResultInfo(ssid, "01:02:03:04:05:06" /* bssid string */,
                Collections.singletonList(ie));
    }

    private List<DhcpOption> makeCustomizedDhcpOptions(byte type, final byte[] value) {
        final DhcpOption option = new DhcpOption();
        option.type = type;
        option.value = value;

        final List<DhcpOption> options = new ArrayList<DhcpOption>();
        options.add(option);
        return options;
    }

    private ProvisioningConfiguration makeTestProvisioningConfiguration() {
        final ProvisioningConfiguration config = new ProvisioningConfiguration();
        config.mUsingMultinetworkPolicyTracker = true;
        config.mUsingIpReachabilityMonitor = true;
        config.mRequestedPreDhcpActionMs = 42;
        config.mInitialConfig = new InitialConfiguration();
        config.mInitialConfig.ipAddresses.add(
                new LinkAddress(parseNumericAddress("192.168.42.42"), 24));
        config.mStaticIpConfig = new StaticIpConfiguration();
        config.mStaticIpConfig.ipAddress =
                new LinkAddress(parseNumericAddress("2001:db8::42"), 90);
        // Not testing other InitialConfig or StaticIpConfig members: they have their own unit tests
        config.mApfCapabilities = new ApfCapabilities(1, 2, 3);
        config.mProvisioningTimeoutMs = 4200;
        config.mIPv6AddrGenMode = 123;
        config.mNetwork = new Network(321);
        config.mDisplayName = "test_config";
        config.mEnablePreconnection = false;
        config.mScanResultInfo = makeScanResultInfo("ssid");
        config.mLayer2Info = new Layer2Information("some l2key", "some cluster",
                MacAddress.fromString("00:01:02:03:04:05"));
        config.mDhcpOptions = makeCustomizedDhcpOptions((byte) 60,
                new String("android-dhcp-11").getBytes());
        config.mIPv4ProvisioningMode = PROV_IPV4_DHCP;
        config.mIPv6ProvisioningMode = PROV_IPV6_SLAAC;
        return config;
    }

    private ProvisioningConfigurationParcelable makeTestProvisioningConfigurationParcelable() {
        final ProvisioningConfigurationParcelable p = new ProvisioningConfigurationParcelable();
        p.enableIPv4 = true;
        p.enableIPv6 = true;
        p.usingMultinetworkPolicyTracker = true;
        p.usingIpReachabilityMonitor = true;
        p.requestedPreDhcpActionMs = 42;
        final InitialConfiguration initialConfig = new InitialConfiguration();
        initialConfig.ipAddresses.add(
                new LinkAddress(parseNumericAddress("192.168.42.42"), 24));
        p.initialConfig = initialConfig.toStableParcelable();
        p.staticIpConfig = new StaticIpConfiguration();
        p.staticIpConfig.ipAddress =
                new LinkAddress(parseNumericAddress("2001:db8::42"), 90);
        p.apfCapabilities = new ApfCapabilities(1, 2, 3);
        p.provisioningTimeoutMs = 4200;
        p.ipv6AddrGenMode = 123;
        p.network = new Network(321);
        p.displayName = "test_config";
        p.enablePreconnection = false;
        final ScanResultInfo scanResultInfo = makeScanResultInfo("ssid");
        p.scanResultInfo = scanResultInfo.toStableParcelable();
        final Layer2Information layer2Info = new Layer2Information("some l2key", "some cluster",
                MacAddress.fromString("00:01:02:03:04:05"));
        p.layer2Info = layer2Info.toStableParcelable();
        p.options = makeCustomizedDhcpOptions((byte) 60, new String("android-dhcp-11").getBytes());
        return p;
    }

    @Before
    public void setUp() {
        mConfig = makeTestProvisioningConfiguration();
        // Any added field must be included in equals() to be tested properly
        assertFieldCountEquals(16, ProvisioningConfiguration.class);
    }

    @Test
    public void testParcelUnparcel() {
        doParcelUnparcelTest();
    }

    @Test
    public void testParcelUnparcel_NullInitialConfiguration() {
        mConfig.mInitialConfig = null;
        doParcelUnparcelTest();
    }

    @Test
    public void testParcelUnparcel_NullStaticConfiguration() {
        mConfig.mStaticIpConfig = null;
        doParcelUnparcelTest();
    }

    @Test
    public void testParcelUnparcel_NullApfCapabilities() {
        mConfig.mApfCapabilities = null;
        doParcelUnparcelTest();
    }

    @Test
    public void testParcelUnparcel_NullNetwork() {
        mConfig.mNetwork = null;
        doParcelUnparcelTest();
    }

    @Test
    public void testParcelUnparcel_NullScanResultInfo() {
        mConfig.mScanResultInfo = null;
        doParcelUnparcelTest();
    }

    @Test
    public void testParcelUnparcel_NullCustomizedDhcpOptions() {
        mConfig.mDhcpOptions = null;
        doParcelUnparcelTest();
    }

    @Test
    public void testParcelUnparcel_WithPreDhcpConnection() {
        mConfig.mEnablePreconnection = true;
        doParcelUnparcelTest();
    }

    @Test
    public void testParcelUnparcel_DisabledIpProvisioningMode() {
        mConfig.mIPv4ProvisioningMode = PROV_IPV4_DISABLED;
        mConfig.mIPv6ProvisioningMode = PROV_IPV6_DISABLED;
        doParcelUnparcelTest();

        assertFalse(mConfig.toStableParcelable().enableIPv4);
        assertFalse(mConfig.toStableParcelable().enableIPv6);
    }

    @Test
    public void testParcelUnparcel_enabledIpProvisioningMode() {
        mConfig.mIPv4ProvisioningMode = PROV_IPV4_DHCP;
        mConfig.mIPv6ProvisioningMode = PROV_IPV6_SLAAC;
        doParcelUnparcelTest();

        assertTrue(mConfig.toStableParcelable().enableIPv4);
        assertTrue(mConfig.toStableParcelable().enableIPv6);
    }

    @Test
    public void testParcelUnparcel_IpProvisioningModefromOldStableParcelable() {
        final ProvisioningConfigurationParcelable p = makeTestProvisioningConfigurationParcelable();
        final ProvisioningConfiguration unparceled = fromStableParcelable(p,
                11 /* interface version */);
        assertEquals(mConfig, unparceled);
    }

    @Test
    public void testParcelUnparcel_WithIpv6LinkLocalOnly() {
        mConfig.mIPv6ProvisioningMode = PROV_IPV6_LINKLOCAL;
        doParcelUnparcelTest();
    }

    private void doParcelUnparcelTest() {
        final ProvisioningConfiguration unparceled =
                fromStableParcelable(mConfig.toStableParcelable(), 12 /* interface version */);
        assertEquals(mConfig, unparceled);
    }

    @Test
    public void testEquals() {
        assertEquals(mConfig, new ProvisioningConfiguration(mConfig));

        assertNotEqualsAfterChange(c -> c.mUsingMultinetworkPolicyTracker = false);
        assertNotEqualsAfterChange(c -> c.mUsingIpReachabilityMonitor = false);
        assertNotEqualsAfterChange(c -> c.mRequestedPreDhcpActionMs++);
        assertNotEqualsAfterChange(c -> c.mInitialConfig.ipAddresses.add(
                new LinkAddress(parseNumericAddress("192.168.47.47"), 16)));
        assertNotEqualsAfterChange(c -> c.mInitialConfig = null);
        assertNotEqualsAfterChange(c -> c.mStaticIpConfig.ipAddress =
                new LinkAddress(parseNumericAddress("2001:db8::47"), 64));
        assertNotEqualsAfterChange(c -> c.mStaticIpConfig = null);
        assertNotEqualsAfterChange(c -> c.mApfCapabilities = new ApfCapabilities(4, 5, 6));
        assertNotEqualsAfterChange(c -> c.mApfCapabilities = null);
        assertNotEqualsAfterChange(c -> c.mProvisioningTimeoutMs++);
        assertNotEqualsAfterChange(c -> c.mIPv6AddrGenMode++);
        assertNotEqualsAfterChange(c -> c.mNetwork = new Network(123));
        assertNotEqualsAfterChange(c -> c.mNetwork = null);
        assertNotEqualsAfterChange(c -> c.mDisplayName = "other_test");
        assertNotEqualsAfterChange(c -> c.mDisplayName = null);
        assertNotEqualsAfterChange(c -> c.mEnablePreconnection = true);
        assertNotEqualsAfterChange(c -> c.mScanResultInfo = null);
        assertNotEqualsAfterChange(c -> c.mScanResultInfo = makeScanResultInfo("another ssid"));
        assertNotEqualsAfterChange(c -> c.mLayer2Info = new Layer2Information("another l2key",
                "some cluster", MacAddress.fromString("00:01:02:03:04:05")));
        assertNotEqualsAfterChange(c -> c.mLayer2Info = new Layer2Information("some l2key",
                "another cluster", MacAddress.fromString("00:01:02:03:04:05")));
        assertNotEqualsAfterChange(c -> c.mLayer2Info = new Layer2Information("some l2key",
                "some cluster", MacAddress.fromString("01:02:03:04:05:06")));
        assertNotEqualsAfterChange(c -> c.mLayer2Info = null);
        assertNotEqualsAfterChange(c -> c.mDhcpOptions = new ArrayList<DhcpOption>());
        assertNotEqualsAfterChange(c -> c.mDhcpOptions = null);
        assertNotEqualsAfterChange(c -> c.mDhcpOptions = makeCustomizedDhcpOptions((byte) 60,
                  new String("vendor-class-identifier").getBytes()));
        assertNotEqualsAfterChange(c -> c.mDhcpOptions = makeCustomizedDhcpOptions((byte) 77,
                  new String("vendor-class-identifier").getBytes()));
        assertNotEqualsAfterChange(c -> c.mIPv4ProvisioningMode = PROV_IPV4_DISABLED);
        assertNotEqualsAfterChange(c -> c.mIPv4ProvisioningMode = PROV_IPV4_STATIC);
        assertNotEqualsAfterChange(c -> c.mIPv6ProvisioningMode = PROV_IPV6_DISABLED);
        assertNotEqualsAfterChange(c -> c.mIPv6ProvisioningMode = PROV_IPV6_LINKLOCAL);
        assertFieldCountEquals(16, ProvisioningConfiguration.class);
    }

    private void assertNotEqualsAfterChange(Consumer<ProvisioningConfiguration> mutator) {
        final ProvisioningConfiguration newConfig = new ProvisioningConfiguration(mConfig);
        mutator.accept(newConfig);
        assertNotEquals(mConfig, newConfig);
    }

    private static final String TEMPLATE = ""
            + ProvisioningConfigurationParcelable.class.getName()
            + "{enableIPv4: true, enableIPv6: true,"
            + " usingMultinetworkPolicyTracker: true,"
            + " usingIpReachabilityMonitor: true, requestedPreDhcpActionMs: 42,"
            + " initialConfig: "
            + InitialConfigurationParcelable.class.getName()
            + "{ipAddresses: [192.168.42.42/24],"
            + " directlyConnectedRoutes: [], dnsServers: [], gateway: null},"
            + " staticIpConfig: IP address 2001:db8::42/90 Gateway  DNS servers: [ ] Domains ,"
            + " apfCapabilities: ApfCapabilities{version: 1, maxSize: 2, format: 3},"
            + " provisioningTimeoutMs: 4200, ipv6AddrGenMode: 123, network: 321,"
            + " displayName: test_config, enablePreconnection: false, scanResultInfo: "
            + ScanResultInfoParcelable.class.getName()
            + "{ssid: ssid, bssid: 01:02:03:04:05:06,"
            + " informationElements: ["
            + InformationElementParcelable.class.getName()
            + "{id: 221, payload: [0, 23, -14, 6, 1, 1, 3, 1, 0, 0]}]}, layer2Info: "
            + Layer2InformationParcelable.class.getName()
            + "{l2Key: some l2key, cluster: some cluster, bssid: %s},"
            + " options: ["
            + DhcpOption.class.getName()
            + "{type: 60,"
            + " value: [97, 110, 100, 114, 111, 105, 100, 45, 100, 104, 99, 112, 45, 49, 49]}],"
            + " ipv4ProvisioningMode: 2, ipv6ProvisioningMode: 1}";

    @Test
    public void testParcelableToString() {
        String expected = String.format(TEMPLATE, "00:01:02:03:04:05");
        assertEquals(expected, mConfig.toStableParcelable().toString());

        final ProvisioningConfigurationParcelable parcelWithNull = mConfig.toStableParcelable();
        parcelWithNull.layer2Info.bssid = null;
        expected = String.format(TEMPLATE, "null");
        assertEquals(expected, parcelWithNull.toString());
    }

    @Test
    public void testIpProvisioningModeToString() {
        assertEquals("disabled", ipv4ProvisioningModeToString(PROV_IPV4_DISABLED));
        assertEquals("static", ipv4ProvisioningModeToString(PROV_IPV4_STATIC));
        assertEquals("dhcp", ipv4ProvisioningModeToString(PROV_IPV4_DHCP));
        assertEquals("unknown", ipv4ProvisioningModeToString(0x03 /* unknown mode */));

        assertEquals("disabled", ipv6ProvisioningModeToString(PROV_IPV6_DISABLED));
        assertEquals("slaac", ipv6ProvisioningModeToString(PROV_IPV6_SLAAC));
        assertEquals("link-local", ipv6ProvisioningModeToString(PROV_IPV6_LINKLOCAL));
        assertEquals("unknown", ipv6ProvisioningModeToString(0x03 /* unknown mode */));
    }
}
