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

package com.android.server.uwb;

import static android.Manifest.permission.UWB_PRIVILEGED;
import static android.uwb.UwbManager.AdapterStateCallback.STATE_ENABLED_ACTIVE;
import static android.uwb.UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE;

import static com.android.server.uwb.UwbSettingsStore.SETTINGS_TOGGLE_STATE;

import static com.google.common.truth.Truth.assertThat;
import static com.google.uwb.support.fira.FiraParams.RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.AttributionSource;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.Process;
import android.platform.test.annotations.Presubmit;
import android.provider.Settings;
import android.test.suitebuilder.annotation.SmallTest;
import android.uwb.IUwbAdapterStateCallbacks;
import android.uwb.IUwbAdfProvisionStateCallbacks;
import android.uwb.IUwbRangingCallbacks;
import android.uwb.IUwbVendorUciCallback;
import android.uwb.SessionHandle;
import android.uwb.UwbAddress;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.jni.NativeUwbManager;
import com.android.server.uwb.multchip.UwbMultichipData;

import com.google.uwb.support.fira.FiraRangingReconfigureParams;
import com.google.uwb.support.multichip.ChipInfoParams;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

/**
 * Tests for {@link UwbServiceImpl}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class UwbServiceImplTest {
    private static final int UID = 343453;
    private static final String PACKAGE_NAME = "com.uwb.test";
    private static final String DEFAULT_CHIP_ID = "defaultChipId";
    private static final ChipInfoParams DEFAULT_CHIP_INFO_PARAMS =
            ChipInfoParams.createBuilder().setChipId(DEFAULT_CHIP_ID).build();
    private static final AttributionSource ATTRIBUTION_SOURCE =
            new AttributionSource.Builder(UID).setPackageName(PACKAGE_NAME).build();

    @Mock private UwbServiceCore mUwbServiceCore;
    @Mock private Context mContext;
    @Mock private UwbInjector mUwbInjector;
    @Mock private UwbSettingsStore mUwbSettingsStore;
    @Mock private NativeUwbManager mNativeUwbManager;
    @Mock private UwbMultichipData mUwbMultichipData;
    @Captor private ArgumentCaptor<IUwbRangingCallbacks> mRangingCbCaptor;
    @Captor private ArgumentCaptor<IUwbRangingCallbacks> mRangingCbCaptor2;
    @Captor private ArgumentCaptor<IBinder.DeathRecipient> mClientDeathCaptor;
    @Captor private ArgumentCaptor<IBinder.DeathRecipient> mUwbServiceCoreDeathCaptor;
    @Captor private ArgumentCaptor<BroadcastReceiver> mApmModeBroadcastReceiver;

    private UwbServiceImpl mUwbServiceImpl;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mUwbInjector.getUwbSettingsStore()).thenReturn(mUwbSettingsStore);
        when(mUwbSettingsStore.get(SETTINGS_TOGGLE_STATE)).thenReturn(true);
        when(mUwbMultichipData.getChipInfos()).thenReturn(List.of(DEFAULT_CHIP_INFO_PARAMS));
        when(mUwbMultichipData.getDefaultChipId()).thenReturn(DEFAULT_CHIP_ID);
        when(mUwbInjector.getUwbServiceCore()).thenReturn(mUwbServiceCore);
        when(mUwbInjector.getMultichipData()).thenReturn(mUwbMultichipData);
        when(mUwbInjector.getSettingsInt(Settings.Global.AIRPLANE_MODE_ON, 0)).thenReturn(0);
        when(mUwbInjector.getNativeUwbManager()).thenReturn(mNativeUwbManager);

        mUwbServiceImpl = new UwbServiceImpl(mContext, mUwbInjector);

        verify(mContext).registerReceiver(
                mApmModeBroadcastReceiver.capture(),
                argThat(i -> i.getAction(0).equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)));
    }

    @Test
    public void testRegisterAdapterStateCallbacks() throws Exception {
        final IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        mUwbServiceImpl.registerAdapterStateCallbacks(cb);

        verify(mUwbServiceCore).registerAdapterStateCallbacks(cb);
    }

    @Test
    public void testUnregisterAdapterStateCallbacks() throws Exception {
        final IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        mUwbServiceImpl.unregisterAdapterStateCallbacks(cb);

        verify(mUwbServiceCore).unregisterAdapterStateCallbacks(cb);
    }

    @Test
    public void testGetTimestampResolutionNanos() throws Exception {
        final long timestamp = 34L;
        when(mUwbServiceCore.getTimestampResolutionNanos()).thenReturn(timestamp);
        assertThat(mUwbServiceImpl.getTimestampResolutionNanos(/* chipId= */ null))
                .isEqualTo(timestamp);

        verify(mUwbServiceCore).getTimestampResolutionNanos();
    }

    @Test
    public void testGetTimestampResolutionNanos_validChipId() throws Exception {
        final long timestamp = 34L;
        when(mUwbServiceCore.getTimestampResolutionNanos()).thenReturn(timestamp);
        assertThat(mUwbServiceImpl.getTimestampResolutionNanos(DEFAULT_CHIP_ID))
                .isEqualTo(timestamp);

        verify(mUwbServiceCore).getTimestampResolutionNanos();
    }

    @Test
    public void testGetTimestampResolutionNanos_invalidChipId() {
        assertThrows(IllegalArgumentException.class,
                () -> mUwbServiceImpl.getTimestampResolutionNanos("invalidChipId"));
    }

    @Test
    public void testGetSpecificationInfo() throws Exception {
        final PersistableBundle specification = new PersistableBundle();
        when(mUwbServiceCore.getSpecificationInfo()).thenReturn(specification);
        assertThat(mUwbServiceImpl.getSpecificationInfo(/* chipId= */ null))
                .isEqualTo(specification);

        verify(mUwbServiceCore).getSpecificationInfo();
    }

    @Test
    public void testGetSpecificationInfo_validChipId() throws Exception {
        final PersistableBundle specification = new PersistableBundle();
        when(mUwbServiceCore.getSpecificationInfo()).thenReturn(specification);
        assertThat(mUwbServiceImpl.getSpecificationInfo(DEFAULT_CHIP_ID))
                .isEqualTo(specification);

        verify(mUwbServiceCore).getSpecificationInfo();
    }

    @Test
    public void testGetSpecificationInfo_invalidChipId() {
        assertThrows(IllegalArgumentException.class,
                () -> mUwbServiceImpl.getSpecificationInfo("invalidChipId"));
    }

    @Test
    public void testOpenRanging() throws Exception {
        final SessionHandle sessionHandle = new SessionHandle(5);
        final IUwbRangingCallbacks cb = mock(IUwbRangingCallbacks.class);
        final PersistableBundle parameters = new PersistableBundle();
        final IBinder cbBinder = mock(IBinder.class);
        when(cb.asBinder()).thenReturn(cbBinder);

        mUwbServiceImpl.openRanging(
                ATTRIBUTION_SOURCE, sessionHandle, cb, parameters, /* chipId= */ null);

        verify(mUwbServiceCore).openRanging(
                eq(ATTRIBUTION_SOURCE), eq(sessionHandle), mRangingCbCaptor.capture(),
                eq(parameters));
        assertThat(mRangingCbCaptor.getValue()).isNotNull();
    }

    @Test
    public void testStartRanging() throws Exception {
        final SessionHandle sessionHandle = new SessionHandle(5);
        final PersistableBundle parameters = new PersistableBundle();

        mUwbServiceImpl.startRanging(sessionHandle, parameters);

        verify(mUwbServiceCore).startRanging(sessionHandle, parameters);
    }

    @Test
    public void testReconfigureRanging() throws Exception {
        final SessionHandle sessionHandle = new SessionHandle(5);
        final FiraRangingReconfigureParams parameters =
                new FiraRangingReconfigureParams.Builder()
                        .setBlockStrideLength(6)
                        .setRangeDataNtfConfig(RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY)
                        .setRangeDataProximityFar(6)
                        .setRangeDataProximityNear(4)
                        .build();
        mUwbServiceImpl.reconfigureRanging(sessionHandle, parameters.toBundle());
        verify(mUwbServiceCore).reconfigureRanging(eq(sessionHandle),
                argThat((x) -> x.getInt("update_block_stride_length") == 6));
    }

    @Test
    public void testStopRanging() throws Exception {
        final SessionHandle sessionHandle = new SessionHandle(5);

        mUwbServiceImpl.stopRanging(sessionHandle);

        verify(mUwbServiceCore).stopRanging(sessionHandle);
    }

    @Test
    public void testCloseRanging() throws Exception {
        final SessionHandle sessionHandle = new SessionHandle(5);

        mUwbServiceImpl.closeRanging(sessionHandle);

        verify(mUwbServiceCore).closeRanging(sessionHandle);
    }

    @Test
    public void testThrowSecurityExceptionWhenCalledWithoutUwbPrivilegedPermission()
            throws Exception {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(UWB_PRIVILEGED), any());
        final IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        try {
            mUwbServiceImpl.registerAdapterStateCallbacks(cb);
            fail();
        } catch (SecurityException e) { /* pass */ }
    }

    @Test
    public void testThrowSecurityExceptionWhenSetUwbEnabledCalledWithoutUwbPrivilegedPermission()
            throws Exception {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(UWB_PRIVILEGED), any());
        try {
            mUwbServiceImpl.setEnabled(true);
            fail();
        } catch (SecurityException e) { /* pass */ }
    }

    @Test
    public void testThrowSecurityExceptionWhenOpenRangingCalledWithoutUwbRangingPermission()
            throws Exception {
        doThrow(new SecurityException()).when(mUwbInjector).enforceUwbRangingPermissionForPreflight(
                any());

        final SessionHandle sessionHandle = new SessionHandle(5);
        final IUwbRangingCallbacks cb = mock(IUwbRangingCallbacks.class);
        final PersistableBundle parameters = new PersistableBundle();
        final IBinder cbBinder = mock(IBinder.class);
        when(cb.asBinder()).thenReturn(cbBinder);
        try {
            mUwbServiceImpl.openRanging(
                    ATTRIBUTION_SOURCE, sessionHandle, cb, parameters, /* chipId= */ null);
            fail();
        } catch (SecurityException e) { /* pass */ }
    }

    @Test
    public void testToggleStatePersistenceToSharedPrefs() throws Exception {
        mUwbServiceImpl.setEnabled(true);
        verify(mUwbSettingsStore).put(SETTINGS_TOGGLE_STATE, true);
        verify(mUwbServiceCore).setEnabled(true);

        when(mUwbSettingsStore.get(SETTINGS_TOGGLE_STATE)).thenReturn(false);
        mUwbServiceImpl.setEnabled(false);
        verify(mUwbSettingsStore).put(SETTINGS_TOGGLE_STATE, false);
        verify(mUwbServiceCore).setEnabled(false);
    }

    @Test
    public void testToggleStatePersistenceToSharedPrefsWhenApmModeOn() throws Exception {
        when(mUwbInjector.getSettingsInt(Settings.Global.AIRPLANE_MODE_ON, 0)).thenReturn(1);

        mUwbServiceImpl.setEnabled(true);
        verify(mUwbSettingsStore).put(SETTINGS_TOGGLE_STATE, true);
        verify(mUwbServiceCore).setEnabled(false);

        when(mUwbSettingsStore.get(SETTINGS_TOGGLE_STATE)).thenReturn(false);
        mUwbServiceImpl.setEnabled(false);
        verify(mUwbSettingsStore).put(SETTINGS_TOGGLE_STATE, false);
        verify(mUwbServiceCore, times(2)).setEnabled(false);
    }

    @Test
    public void testToggleStateReadFromSharedPrefsOnInitialization() throws Exception {
        when(mUwbServiceCore.getAdapterState()).thenReturn(STATE_ENABLED_ACTIVE);
        assertThat(mUwbServiceImpl.getAdapterState()).isEqualTo(STATE_ENABLED_ACTIVE);
        verify(mUwbServiceCore).getAdapterState();

        when(mUwbServiceCore.getAdapterState()).thenReturn(STATE_ENABLED_INACTIVE);
        assertThat(mUwbServiceImpl.getAdapterState()).isEqualTo(STATE_ENABLED_INACTIVE);
        verify(mUwbServiceCore, times(2)).getAdapterState();
    }

    @Test
    public void testApmModeToggle() throws Exception {
        mUwbServiceImpl.setEnabled(true);
        verify(mUwbSettingsStore).put(SETTINGS_TOGGLE_STATE, true);
        verify(mUwbServiceCore).setEnabled(true);

        // Toggle on
        when(mUwbInjector.getSettingsInt(Settings.Global.AIRPLANE_MODE_ON, 0)).thenReturn(1);
        mApmModeBroadcastReceiver.getValue().onReceive(
                mContext, new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED));
        verify(mUwbServiceCore).setEnabled(false);

        // Toggle off
        when(mUwbInjector.getSettingsInt(Settings.Global.AIRPLANE_MODE_ON, 0)).thenReturn(0);
        mApmModeBroadcastReceiver.getValue().onReceive(
                mContext, new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED));
        verify(mUwbServiceCore, times(2)).setEnabled(true);
    }

    @Test
    public void testToggleFromRootedShellWhenApmModeOn() throws Exception {
        BinderUtil.setUid(Process.ROOT_UID);
        when(mUwbInjector.getSettingsInt(Settings.Global.AIRPLANE_MODE_ON, 0)).thenReturn(1);

        mUwbServiceImpl.setEnabled(true);
        verify(mUwbSettingsStore).put(SETTINGS_TOGGLE_STATE, true);
        verify(mUwbServiceCore).setEnabled(true);

        when(mUwbSettingsStore.get(SETTINGS_TOGGLE_STATE)).thenReturn(false);
        mUwbServiceImpl.setEnabled(false);
        verify(mUwbSettingsStore).put(SETTINGS_TOGGLE_STATE, false);
        verify(mUwbServiceCore).setEnabled(false);
    }

    @Test
    public void testGetDefaultChipId() {
        assertEquals(DEFAULT_CHIP_ID, mUwbServiceImpl.getDefaultChipId());
    }

    @Test
    public void testThrowSecurityExceptionWhenGetDefaultChipIdWithoutUwbPrivilegedPermission()
            throws Exception {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(UWB_PRIVILEGED), any());
        try {
            mUwbServiceImpl.getDefaultChipId();
            fail();
        } catch (SecurityException e) { /* pass */ }
    }

    @Test
    public void testGetChipIds() {
        List<String> chipIds = mUwbServiceImpl.getChipIds();
        assertThat(chipIds).containsExactly(DEFAULT_CHIP_ID);
    }

    @Test
    public void testThrowSecurityExceptionWhenGetChipIdsWithoutUwbPrivilegedPermission()
            throws Exception {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(UWB_PRIVILEGED), any());
        try {
            mUwbServiceImpl.getChipIds();
            fail();
        } catch (SecurityException e) { /* pass */ }
    }

    @Test
    public void testGetChipInfos() {
        List<PersistableBundle> chipInfos = mUwbServiceImpl.getChipInfos();
        assertThat(chipInfos).hasSize(1);
        ChipInfoParams chipInfoParams = ChipInfoParams.fromBundle(chipInfos.get(0));
        assertThat(chipInfoParams.getChipId()).isEqualTo(DEFAULT_CHIP_ID);
        assertThat(chipInfoParams.getPositionX()).isEqualTo(0.);
        assertThat(chipInfoParams.getPositionY()).isEqualTo(0.);
        assertThat(chipInfoParams.getPositionZ()).isEqualTo(0.);
    }

    @Test
    public void testThrowSecurityExceptionWhenGetChipInfosWithoutUwbPrivilegedPermission()
            throws Exception {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(UWB_PRIVILEGED), any());
        try {
            mUwbServiceImpl.getChipInfos();
            fail();
        } catch (SecurityException e) { /* pass */ }
    }

    @Test
    public void testAddControlee() throws Exception {
        final SessionHandle sessionHandle = new SessionHandle(5);
        final PersistableBundle parameters = new PersistableBundle();

        mUwbServiceImpl.addControlee(sessionHandle, parameters);
        verify(mUwbServiceCore).addControlee(sessionHandle, parameters);
    }

    @Test
    public void testRemoveControlee() throws Exception {
        final SessionHandle sessionHandle = new SessionHandle(5);
        final PersistableBundle parameters = new PersistableBundle();

        mUwbServiceImpl.removeControlee(sessionHandle, parameters);
        verify(mUwbServiceCore).removeControlee(sessionHandle, parameters);
    }

    @Test
    public void testAddServiceProfile() throws Exception {
        final PersistableBundle parameters = new PersistableBundle();

        try {
            mUwbServiceImpl.addServiceProfile(parameters);
            fail();
        } catch (IllegalStateException e) { /* pass */ }
    }

    @Test
    public void testGetAdfCertificateAndInfo() throws Exception {
        final PersistableBundle parameters = new PersistableBundle();

        try {
            mUwbServiceImpl.getAdfCertificateAndInfo(parameters);
            fail();
        } catch (IllegalStateException e) { /* pass */ }
    }

    @Test
    public void testGetAdfProvisioningAuthorities() throws Exception {
        final PersistableBundle parameters = new PersistableBundle();

        try {
            mUwbServiceImpl.getAdfProvisioningAuthorities(parameters);
            fail();
        } catch (IllegalStateException e) { /* pass */ }
    }

    @Test
    public void testGetAllServiceProfiles() throws Exception {
        try {
            mUwbServiceImpl.getAllServiceProfiles();
            fail();
        } catch (IllegalStateException e) { /* pass */ }
    }

    @Test
    public void testProvisionProfileAdfByScript() throws Exception {
        final PersistableBundle parameters = new PersistableBundle();
        final IUwbAdfProvisionStateCallbacks cb = mock(IUwbAdfProvisionStateCallbacks.class);

        try {
            mUwbServiceImpl.provisionProfileAdfByScript(parameters, cb);
            fail();
        } catch (IllegalStateException e) { /* pass */ }
    }

    @Test
    public void testRegisterVendorExtensionCallback() throws Exception {
        final IUwbVendorUciCallback cb = mock(IUwbVendorUciCallback.class);
        mUwbServiceImpl.registerVendorExtensionCallback(cb);
        verify(mUwbServiceCore).registerVendorExtensionCallback(cb);
    }

    @Test
    public void testUnregisterVendorExtensionCallback() throws Exception {
        final IUwbVendorUciCallback cb = mock(IUwbVendorUciCallback.class);
        mUwbServiceImpl.unregisterVendorExtensionCallback(cb);
        verify(mUwbServiceCore).unregisterVendorExtensionCallback(cb);
    }

    @Test
    public void testRemoveProfileAdf() throws Exception {
        final PersistableBundle parameters = new PersistableBundle();

        try {
            mUwbServiceImpl.removeProfileAdf(parameters);
            fail();
        } catch (IllegalStateException e) { /* pass */ }
    }

    @Test
    public void testRemoveServiceProfile() throws Exception {
        final PersistableBundle parameters = new PersistableBundle();

        try {
            mUwbServiceImpl.removeServiceProfile(parameters);
            fail();
        } catch (IllegalStateException e) { /* pass */ }
    }

    @Test
    public void testResume() throws Exception {
        final SessionHandle sessionHandle = new SessionHandle(5);
        final PersistableBundle parameters = new PersistableBundle();

        try {
            mUwbServiceImpl.resume(sessionHandle, parameters);
            fail();
        } catch (IllegalStateException e) { /* pass */ }
    }

    @Test
    public void testPause() throws Exception {
        final SessionHandle sessionHandle = new SessionHandle(5);
        final PersistableBundle parameters = new PersistableBundle();

        try {
            mUwbServiceImpl.pause(sessionHandle, parameters);
            fail();
        } catch (IllegalStateException e) { /* pass */ }
    }

    @Test
    public void testSendData() throws Exception {
        final SessionHandle sessionHandle = new SessionHandle(5);
        final UwbAddress mUwbAddress = mock(UwbAddress.class);
        final PersistableBundle parameters = new PersistableBundle();

        try {
            mUwbServiceImpl.sendData(sessionHandle, mUwbAddress, parameters, null);
            fail();
        } catch (IllegalStateException e) { /* pass */ }
    }

    @Test
    public void testSendVendorUciMessage() throws Exception {
        final int gid = 0;
        final int oid = 0;
        mUwbServiceImpl.sendVendorUciMessage(gid, oid, null);
        verify(mUwbServiceCore).sendVendorUciMessage(gid, oid, null);
    }
}
