/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.connectivity;

import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static android.telephony.TelephonyManager.ACTION_MULTI_SIM_CONFIG_CHANGED;

import static com.android.testutils.DevSdkIgnoreRuleKt.SC_V2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.annotation.NonNull;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.NetworkRequest;
import android.net.NetworkSpecifier;
import android.net.TelephonyNetworkSpecifier;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.networkstack.apishim.TelephonyManagerShimImpl;
import com.android.networkstack.apishim.common.TelephonyManagerShim;
import com.android.networkstack.apishim.common.UnsupportedApiLevelException;
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;

/**
 * Tests for CarrierPrivilegeAuthenticatorTest.
 *
 * Build, install and run with:
 *  runtest frameworks-net -c com.android.server.connectivity.CarrierPrivilegeAuthenticatorTest
 */
@RunWith(DevSdkIgnoreRunner.class)
@IgnoreUpTo(SC_V2) // TODO: Use to Build.VERSION_CODES.SC_V2 when available
public class CarrierPrivilegeAuthenticatorTest {
    private static final int SUBSCRIPTION_COUNT = 2;
    private static final int TEST_SUBSCRIPTION_ID = 1;

    @NonNull private final Context mContext;
    @NonNull private final TelephonyManager mTelephonyManager;
    @NonNull private final TelephonyManagerShimImpl mTelephonyManagerShim;
    @NonNull private final PackageManager mPackageManager;
    @NonNull private TestCarrierPrivilegeAuthenticator mCarrierPrivilegeAuthenticator;
    private final int mCarrierConfigPkgUid = 12345;
    private final String mTestPkg = "com.android.server.connectivity.test";

    public class TestCarrierPrivilegeAuthenticator extends CarrierPrivilegeAuthenticator {
        TestCarrierPrivilegeAuthenticator(@NonNull final Context c,
                @NonNull final TelephonyManager t) {
            super(c, t, mTelephonyManagerShim);
        }
        @Override
        protected int getSlotIndex(int subId) {
            if (SubscriptionManager.DEFAULT_SUBSCRIPTION_ID == subId) return TEST_SUBSCRIPTION_ID;
            return subId;
        }
    }

    public CarrierPrivilegeAuthenticatorTest() {
        mContext = mock(Context.class);
        mTelephonyManager = mock(TelephonyManager.class);
        mTelephonyManagerShim = mock(TelephonyManagerShimImpl.class);
        mPackageManager = mock(PackageManager.class);
    }

    @Before
    public void setUp() throws Exception {
        doReturn(SUBSCRIPTION_COUNT).when(mTelephonyManager).getActiveModemCount();
        doReturn(mTestPkg).when(mTelephonyManagerShim)
                .getCarrierServicePackageNameForLogicalSlot(anyInt());
        doReturn(mPackageManager).when(mContext).getPackageManager();
        final ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.uid = mCarrierConfigPkgUid;
        doReturn(applicationInfo).when(mPackageManager)
                .getApplicationInfo(eq(mTestPkg), anyInt());
        mCarrierPrivilegeAuthenticator =
                new TestCarrierPrivilegeAuthenticator(mContext, mTelephonyManager);
    }

    private IntentFilter getIntentFilter() {
        final ArgumentCaptor<IntentFilter> captor = ArgumentCaptor.forClass(IntentFilter.class);
        verify(mContext).registerReceiver(any(), captor.capture(), any(), any());
        return captor.getValue();
    }

    private List<TelephonyManagerShim.CarrierPrivilegesListenerShim>
            getCarrierPrivilegesListeners() {
        final ArgumentCaptor<TelephonyManagerShim.CarrierPrivilegesListenerShim> captor =
                ArgumentCaptor.forClass(TelephonyManagerShim.CarrierPrivilegesListenerShim.class);
        try {
            verify(mTelephonyManagerShim, atLeastOnce())
                    .addCarrierPrivilegesListener(anyInt(), any(), captor.capture());
        } catch (UnsupportedApiLevelException e) {
        }
        return captor.getAllValues();
    }

    private Intent buildTestMultiSimConfigBroadcastIntent() {
        final Intent intent = new Intent(ACTION_MULTI_SIM_CONFIG_CHANGED);
        return intent;
    }
    @Test
    public void testConstructor() throws Exception {
        verify(mContext).registerReceiver(
                        eq(mCarrierPrivilegeAuthenticator),
                        any(IntentFilter.class),
                        any(),
                        any());
        final IntentFilter filter = getIntentFilter();
        assertEquals(1, filter.countActions());
        assertTrue(filter.hasAction(ACTION_MULTI_SIM_CONFIG_CHANGED));

        verify(mTelephonyManagerShim, times(2))
                .addCarrierPrivilegesListener(anyInt(), any(), any());
        verify(mTelephonyManagerShim)
                .addCarrierPrivilegesListener(eq(0), any(), any());
        verify(mTelephonyManagerShim)
                .addCarrierPrivilegesListener(eq(1), any(), any());
        assertEquals(2, getCarrierPrivilegesListeners().size());

        final TelephonyNetworkSpecifier telephonyNetworkSpecifier =
                new TelephonyNetworkSpecifier(0);
        final NetworkRequest.Builder networkRequestBuilder = new NetworkRequest.Builder();
        networkRequestBuilder.addTransportType(TRANSPORT_CELLULAR);
        networkRequestBuilder.setNetworkSpecifier(telephonyNetworkSpecifier);

        assertTrue(mCarrierPrivilegeAuthenticator.hasCarrierPrivilegeForNetworkCapabilities(
                mCarrierConfigPkgUid, networkRequestBuilder.build().networkCapabilities));
        assertFalse(mCarrierPrivilegeAuthenticator.hasCarrierPrivilegeForNetworkCapabilities(
                mCarrierConfigPkgUid + 1, networkRequestBuilder.build().networkCapabilities));
    }

    @Test
    public void testMultiSimConfigChanged() throws Exception {
        doReturn(1).when(mTelephonyManager).getActiveModemCount();
        final List<TelephonyManagerShim.CarrierPrivilegesListenerShim> carrierPrivilegesListeners =
                getCarrierPrivilegesListeners();

        mCarrierPrivilegeAuthenticator.onReceive(
                mContext, buildTestMultiSimConfigBroadcastIntent());
        for (TelephonyManagerShim.CarrierPrivilegesListenerShim carrierPrivilegesListener
                : carrierPrivilegesListeners) {
            verify(mTelephonyManagerShim)
                    .removeCarrierPrivilegesListener(eq(carrierPrivilegesListener));
        }

        // Expect a new CarrierPrivilegesListener to have been registered for slot 0, and none other
        // (2 previously registered during startup, for slots 0 & 1)
        verify(mTelephonyManagerShim, times(3))
                .addCarrierPrivilegesListener(anyInt(), any(), any());
        verify(mTelephonyManagerShim, times(2))
                .addCarrierPrivilegesListener(eq(0), any(), any());

        final TelephonyNetworkSpecifier telephonyNetworkSpecifier =
                new TelephonyNetworkSpecifier(0);
        final NetworkRequest.Builder networkRequestBuilder = new NetworkRequest.Builder();
        networkRequestBuilder.addTransportType(TRANSPORT_CELLULAR);
        networkRequestBuilder.setNetworkSpecifier(telephonyNetworkSpecifier);
        assertTrue(mCarrierPrivilegeAuthenticator.hasCarrierPrivilegeForNetworkCapabilities(
                mCarrierConfigPkgUid, networkRequestBuilder.build().networkCapabilities));
        assertFalse(mCarrierPrivilegeAuthenticator.hasCarrierPrivilegeForNetworkCapabilities(
                mCarrierConfigPkgUid + 1, networkRequestBuilder.build().networkCapabilities));
    }

    @Test
    public void testOnCarrierPrivilegesChanged() throws Exception {
        final TelephonyManagerShim.CarrierPrivilegesListenerShim listener =
                getCarrierPrivilegesListeners().get(0);

        final TelephonyNetworkSpecifier telephonyNetworkSpecifier =
                new TelephonyNetworkSpecifier(0);
        final NetworkRequest.Builder networkRequestBuilder = new NetworkRequest.Builder();
        networkRequestBuilder.addTransportType(TRANSPORT_CELLULAR);
        networkRequestBuilder.setNetworkSpecifier(telephonyNetworkSpecifier);

        final ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.uid = mCarrierConfigPkgUid + 1;
        doReturn(applicationInfo).when(mPackageManager)
                .getApplicationInfo(eq(mTestPkg), anyInt());
        listener.onCarrierPrivilegesChanged(Collections.emptyList(), new int[] {});

        assertFalse(mCarrierPrivilegeAuthenticator.hasCarrierPrivilegeForNetworkCapabilities(
                mCarrierConfigPkgUid, networkRequestBuilder.build().networkCapabilities));
        assertTrue(mCarrierPrivilegeAuthenticator.hasCarrierPrivilegeForNetworkCapabilities(
                mCarrierConfigPkgUid + 1, networkRequestBuilder.build().networkCapabilities));
    }

    @Test
    public void testDefaultSubscription() throws Exception {
        final NetworkRequest.Builder networkRequestBuilder = new NetworkRequest.Builder();
        networkRequestBuilder.addTransportType(TRANSPORT_CELLULAR);
        assertFalse(mCarrierPrivilegeAuthenticator.hasCarrierPrivilegeForNetworkCapabilities(
                mCarrierConfigPkgUid, networkRequestBuilder.build().networkCapabilities));

        networkRequestBuilder.setNetworkSpecifier(new TelephonyNetworkSpecifier(0));
        assertTrue(mCarrierPrivilegeAuthenticator.hasCarrierPrivilegeForNetworkCapabilities(
                mCarrierConfigPkgUid, networkRequestBuilder.build().networkCapabilities));

        // The builder for NetworkRequest doesn't allow removing the transport as long as a
        // specifier is set, so unset it first. TODO : fix the builder
        networkRequestBuilder.setNetworkSpecifier((NetworkSpecifier) null);
        networkRequestBuilder.removeTransportType(TRANSPORT_CELLULAR);
        networkRequestBuilder.addTransportType(TRANSPORT_WIFI);
        networkRequestBuilder.setNetworkSpecifier(new TelephonyNetworkSpecifier(0));
        assertFalse(mCarrierPrivilegeAuthenticator.hasCarrierPrivilegeForNetworkCapabilities(
                mCarrierConfigPkgUid, networkRequestBuilder.build().networkCapabilities));
    }
}
