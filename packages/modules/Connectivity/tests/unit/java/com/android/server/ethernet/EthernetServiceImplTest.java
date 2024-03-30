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

package com.android.server.ethernet;

import static android.net.NetworkCapabilities.TRANSPORT_TEST;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.INetworkInterfaceOutcomeReceiver;
import android.net.EthernetNetworkUpdateRequest;
import android.net.IpConfiguration;
import android.net.NetworkCapabilities;
import android.os.Handler;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class EthernetServiceImplTest {
    private static final String TEST_IFACE = "test123";
    private static final EthernetNetworkUpdateRequest UPDATE_REQUEST =
            new EthernetNetworkUpdateRequest.Builder()
                    .setIpConfiguration(new IpConfiguration())
                    .setNetworkCapabilities(new NetworkCapabilities.Builder().build())
                    .build();
    private static final EthernetNetworkUpdateRequest UPDATE_REQUEST_WITHOUT_CAPABILITIES =
            new EthernetNetworkUpdateRequest.Builder()
                    .setIpConfiguration(new IpConfiguration())
                    .build();
    private static final EthernetNetworkUpdateRequest UPDATE_REQUEST_WITHOUT_IP_CONFIG =
            new EthernetNetworkUpdateRequest.Builder()
                    .setNetworkCapabilities(new NetworkCapabilities.Builder().build())
                    .build();
    private static final INetworkInterfaceOutcomeReceiver NULL_LISTENER = null;
    private EthernetServiceImpl mEthernetServiceImpl;
    @Mock private Context mContext;
    @Mock private Handler mHandler;
    @Mock private EthernetTracker mEthernetTracker;
    @Mock private PackageManager mPackageManager;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        doReturn(mPackageManager).when(mContext).getPackageManager();
        mEthernetServiceImpl = new EthernetServiceImpl(mContext, mHandler, mEthernetTracker);
        mEthernetServiceImpl.mStarted.set(true);
        toggleAutomotiveFeature(true);
        shouldTrackIface(TEST_IFACE, true);
    }

    private void toggleAutomotiveFeature(final boolean isEnabled) {
        doReturn(isEnabled)
                .when(mPackageManager).hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    private void shouldTrackIface(@NonNull final String iface, final boolean shouldTrack) {
        doReturn(shouldTrack).when(mEthernetTracker).isTrackingInterface(iface);
    }

    @Test
    public void testSetConfigurationRejectsWhenEthNotStarted() {
        mEthernetServiceImpl.mStarted.set(false);
        assertThrows(IllegalStateException.class, () -> {
            mEthernetServiceImpl.setConfiguration("" /* iface */, new IpConfiguration());
        });
    }

    @Test
    public void testUpdateConfigurationRejectsWhenEthNotStarted() {
        mEthernetServiceImpl.mStarted.set(false);
        assertThrows(IllegalStateException.class, () -> {
            mEthernetServiceImpl.updateConfiguration(
                    "" /* iface */, UPDATE_REQUEST, null /* listener */);
        });
    }

    @Test
    public void testConnectNetworkRejectsWhenEthNotStarted() {
        mEthernetServiceImpl.mStarted.set(false);
        assertThrows(IllegalStateException.class, () -> {
            mEthernetServiceImpl.connectNetwork("" /* iface */, null /* listener */);
        });
    }

    @Test
    public void testDisconnectNetworkRejectsWhenEthNotStarted() {
        mEthernetServiceImpl.mStarted.set(false);
        assertThrows(IllegalStateException.class, () -> {
            mEthernetServiceImpl.disconnectNetwork("" /* iface */, null /* listener */);
        });
    }

    @Test
    public void testUpdateConfigurationRejectsNullIface() {
        assertThrows(NullPointerException.class, () -> {
            mEthernetServiceImpl.updateConfiguration(null, UPDATE_REQUEST, NULL_LISTENER);
        });
    }

    @Test
    public void testConnectNetworkRejectsNullIface() {
        assertThrows(NullPointerException.class, () -> {
            mEthernetServiceImpl.connectNetwork(null /* iface */, NULL_LISTENER);
        });
    }

    @Test
    public void testDisconnectNetworkRejectsNullIface() {
        assertThrows(NullPointerException.class, () -> {
            mEthernetServiceImpl.disconnectNetwork(null /* iface */, NULL_LISTENER);
        });
    }

    @Test
    public void testUpdateConfigurationWithCapabilitiesRejectsWithoutAutomotiveFeature() {
        toggleAutomotiveFeature(false);
        assertThrows(UnsupportedOperationException.class, () -> {
            mEthernetServiceImpl.updateConfiguration(TEST_IFACE, UPDATE_REQUEST, NULL_LISTENER);
        });
    }

    @Test
    public void testUpdateConfigurationWithCapabilitiesWithAutomotiveFeature() {
        toggleAutomotiveFeature(false);
        mEthernetServiceImpl.updateConfiguration(TEST_IFACE, UPDATE_REQUEST_WITHOUT_CAPABILITIES,
                NULL_LISTENER);
        verify(mEthernetTracker).updateConfiguration(eq(TEST_IFACE),
                eq(UPDATE_REQUEST_WITHOUT_CAPABILITIES.getIpConfiguration()),
                eq(UPDATE_REQUEST_WITHOUT_CAPABILITIES.getNetworkCapabilities()), isNull());
    }

    @Test
    public void testConnectNetworkRejectsWithoutAutomotiveFeature() {
        toggleAutomotiveFeature(false);
        assertThrows(UnsupportedOperationException.class, () -> {
            mEthernetServiceImpl.connectNetwork("" /* iface */, NULL_LISTENER);
        });
    }

    @Test
    public void testDisconnectNetworkRejectsWithoutAutomotiveFeature() {
        toggleAutomotiveFeature(false);
        assertThrows(UnsupportedOperationException.class, () -> {
            mEthernetServiceImpl.disconnectNetwork("" /* iface */, NULL_LISTENER);
        });
    }

    private void denyManageEthPermission() {
        doThrow(new SecurityException("")).when(mContext)
                .enforceCallingOrSelfPermission(
                        eq(Manifest.permission.MANAGE_ETHERNET_NETWORKS), anyString());
    }

    private void denyManageTestNetworksPermission() {
        doThrow(new SecurityException("")).when(mContext)
                .enforceCallingOrSelfPermission(
                        eq(Manifest.permission.MANAGE_TEST_NETWORKS), anyString());
    }

    @Test
    public void testUpdateConfigurationRejectsWithoutManageEthPermission() {
        denyManageEthPermission();
        assertThrows(SecurityException.class, () -> {
            mEthernetServiceImpl.updateConfiguration(TEST_IFACE, UPDATE_REQUEST, NULL_LISTENER);
        });
    }

    @Test
    public void testConnectNetworkRejectsWithoutManageEthPermission() {
        denyManageEthPermission();
        assertThrows(SecurityException.class, () -> {
            mEthernetServiceImpl.connectNetwork(TEST_IFACE, NULL_LISTENER);
        });
    }

    @Test
    public void testDisconnectNetworkRejectsWithoutManageEthPermission() {
        denyManageEthPermission();
        assertThrows(SecurityException.class, () -> {
            mEthernetServiceImpl.disconnectNetwork(TEST_IFACE, NULL_LISTENER);
        });
    }

    private void enableTestInterface() {
        when(mEthernetTracker.isValidTestInterface(eq(TEST_IFACE))).thenReturn(true);
    }

    @Test
    public void testUpdateConfigurationRejectsTestRequestWithoutTestPermission() {
        enableTestInterface();
        denyManageTestNetworksPermission();
        assertThrows(SecurityException.class, () -> {
            mEthernetServiceImpl.updateConfiguration(TEST_IFACE, UPDATE_REQUEST, NULL_LISTENER);
        });
    }

    @Test
    public void testConnectNetworkRejectsTestRequestWithoutTestPermission() {
        enableTestInterface();
        denyManageTestNetworksPermission();
        assertThrows(SecurityException.class, () -> {
            mEthernetServiceImpl.connectNetwork(TEST_IFACE, NULL_LISTENER);
        });
    }

    @Test
    public void testDisconnectNetworkRejectsTestRequestWithoutTestPermission() {
        enableTestInterface();
        denyManageTestNetworksPermission();
        assertThrows(SecurityException.class, () -> {
            mEthernetServiceImpl.disconnectNetwork(TEST_IFACE, NULL_LISTENER);
        });
    }

    @Test
    public void testUpdateConfiguration() {
        mEthernetServiceImpl.updateConfiguration(TEST_IFACE, UPDATE_REQUEST, NULL_LISTENER);
        verify(mEthernetTracker).updateConfiguration(
                eq(TEST_IFACE),
                eq(UPDATE_REQUEST.getIpConfiguration()),
                eq(UPDATE_REQUEST.getNetworkCapabilities()), eq(NULL_LISTENER));
    }

    @Test
    public void testConnectNetwork() {
        mEthernetServiceImpl.connectNetwork(TEST_IFACE, NULL_LISTENER);
        verify(mEthernetTracker).connectNetwork(eq(TEST_IFACE), eq(NULL_LISTENER));
    }

    @Test
    public void testDisconnectNetwork() {
        mEthernetServiceImpl.disconnectNetwork(TEST_IFACE, NULL_LISTENER);
        verify(mEthernetTracker).disconnectNetwork(eq(TEST_IFACE), eq(NULL_LISTENER));
    }

    @Test
    public void testUpdateConfigurationAcceptsTestRequestWithNullCapabilities() {
        enableTestInterface();
        final EthernetNetworkUpdateRequest request =
                new EthernetNetworkUpdateRequest
                        .Builder()
                        .setIpConfiguration(new IpConfiguration()).build();
        mEthernetServiceImpl.updateConfiguration(TEST_IFACE, request, NULL_LISTENER);
        verify(mEthernetTracker).updateConfiguration(eq(TEST_IFACE),
                eq(request.getIpConfiguration()),
                eq(request.getNetworkCapabilities()), isNull());
    }

    @Test
    public void testUpdateConfigurationAcceptsRequestWithNullIpConfiguration() {
        mEthernetServiceImpl.updateConfiguration(TEST_IFACE, UPDATE_REQUEST_WITHOUT_IP_CONFIG,
                NULL_LISTENER);
        verify(mEthernetTracker).updateConfiguration(eq(TEST_IFACE),
                eq(UPDATE_REQUEST_WITHOUT_IP_CONFIG.getIpConfiguration()),
                eq(UPDATE_REQUEST_WITHOUT_IP_CONFIG.getNetworkCapabilities()), isNull());
    }

    @Test
    public void testUpdateConfigurationRejectsInvalidTestRequest() {
        enableTestInterface();
        assertThrows(IllegalArgumentException.class, () -> {
            mEthernetServiceImpl.updateConfiguration(TEST_IFACE, UPDATE_REQUEST, NULL_LISTENER);
        });
    }

    private EthernetNetworkUpdateRequest createTestNetworkUpdateRequest() {
        final NetworkCapabilities nc =  new NetworkCapabilities
                .Builder(UPDATE_REQUEST.getNetworkCapabilities())
                .addTransportType(TRANSPORT_TEST).build();

        return new EthernetNetworkUpdateRequest
                .Builder(UPDATE_REQUEST)
                .setNetworkCapabilities(nc).build();
    }

    @Test
    public void testUpdateConfigurationForTestRequestDoesNotRequireAutoOrEthernetPermission() {
        enableTestInterface();
        toggleAutomotiveFeature(false);
        denyManageEthPermission();
        final EthernetNetworkUpdateRequest request = createTestNetworkUpdateRequest();

        mEthernetServiceImpl.updateConfiguration(TEST_IFACE, request, NULL_LISTENER);
        verify(mEthernetTracker).updateConfiguration(
                eq(TEST_IFACE),
                eq(request.getIpConfiguration()),
                eq(request.getNetworkCapabilities()), eq(NULL_LISTENER));
    }

    @Test
    public void testConnectNetworkForTestRequestDoesNotRequireAutoOrNetPermission() {
        enableTestInterface();
        toggleAutomotiveFeature(false);
        denyManageEthPermission();

        mEthernetServiceImpl.connectNetwork(TEST_IFACE, NULL_LISTENER);
        verify(mEthernetTracker).connectNetwork(eq(TEST_IFACE), eq(NULL_LISTENER));
    }

    @Test
    public void testDisconnectNetworkForTestRequestDoesNotRequireAutoOrNetPermission() {
        enableTestInterface();
        toggleAutomotiveFeature(false);
        denyManageEthPermission();

        mEthernetServiceImpl.disconnectNetwork(TEST_IFACE, NULL_LISTENER);
        verify(mEthernetTracker).disconnectNetwork(eq(TEST_IFACE), eq(NULL_LISTENER));
    }

    private void denyPermissions(String... permissions) {
        for (String permission: permissions) {
            doReturn(PackageManager.PERMISSION_DENIED).when(mContext)
                    .checkCallingOrSelfPermission(eq(permission));
        }
    }

    @Test
    public void testSetEthernetEnabled() {
        denyPermissions(android.net.NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK);
        mEthernetServiceImpl.setEthernetEnabled(true);
        verify(mEthernetTracker).setEthernetEnabled(true);
        reset(mEthernetTracker);

        denyPermissions(Manifest.permission.NETWORK_STACK);
        mEthernetServiceImpl.setEthernetEnabled(false);
        verify(mEthernetTracker).setEthernetEnabled(false);
        reset(mEthernetTracker);

        denyPermissions(Manifest.permission.NETWORK_SETTINGS);
        try {
            mEthernetServiceImpl.setEthernetEnabled(true);
            fail("Should get SecurityException");
        } catch (SecurityException e) { }
        verify(mEthernetTracker, never()).setEthernetEnabled(false);
    }
}
