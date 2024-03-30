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

package android.bluetooth.cts;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.bluetooth.BluetoothStatusCodes.FEATURE_SUPPORTED;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothLeAudioCodecConfigMetadata;
import android.bluetooth.BluetoothLeAudioContentMetadata;
import android.bluetooth.BluetoothLeBroadcastChannel;
import android.bluetooth.BluetoothLeBroadcastMetadata;
import android.bluetooth.BluetoothLeBroadcastSubgroup;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class BluetoothLeBroadcastMetadataTest {
    private static final String TEST_MAC_ADDRESS = "00:11:22:33:44:55";
    private static final int TEST_BROADCAST_ID = 42;
    private static final int TEST_ADVERTISER_SID = 1234;
    private static final int TEST_PA_SYNC_INTERVAL = 100;
    private static final int TEST_PRESENTATION_DELAY_MS = 345;

    private static final int TEST_CODEC_ID = 42;
    private static final BluetoothLeBroadcastChannel[] TEST_CHANNELS = {
        new BluetoothLeBroadcastChannel.Builder().setChannelIndex(42).setSelected(true)
                .setCodecMetadata(new BluetoothLeAudioCodecConfigMetadata.Builder().build())
                .build()
    };

    // For BluetoothLeAudioCodecConfigMetadata
    private static final long TEST_AUDIO_LOCATION_FRONT_LEFT = 0x01;

    // For BluetoothLeAudioContentMetadata
    private static final String TEST_PROGRAM_INFO = "Test";
    // German language code in ISO 639-3
    private static final String TEST_LANGUAGE = "deu";

    private Context mContext;
    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;
    private boolean mIsBroadcastSourceSupported;
    private boolean mIsBroadcastAssistantSupported;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        if (!ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
            return;
        }
        mHasBluetooth = TestUtils.hasBluetooth();
        if (!mHasBluetooth) {
            return;
        }
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);
        mAdapter = TestUtils.getBluetoothAdapterOrDie();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        mIsBroadcastAssistantSupported =
                mAdapter.isLeAudioBroadcastAssistantSupported() == FEATURE_SUPPORTED;
        if (mIsBroadcastAssistantSupported) {
            boolean isBroadcastAssistantEnabledInConfig =
                    TestUtils.isProfileEnabled(BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT);
            assertTrue("Config must be true when profile is supported",
                    isBroadcastAssistantEnabledInConfig);
        }

        mIsBroadcastSourceSupported =
                mAdapter.isLeAudioBroadcastSourceSupported() == FEATURE_SUPPORTED;
        if (mIsBroadcastSourceSupported) {
            boolean isBroadcastSourceEnabledInConfig =
                    TestUtils.isProfileEnabled(BluetoothProfile.LE_AUDIO_BROADCAST);
            assertTrue("Config must be true when profile is supported",
                    isBroadcastSourceEnabledInConfig);
        }
    }

    @After
    public void tearDown() {
        if (mHasBluetooth) {
            mAdapter = null;
            TestUtils.dropPermissionAsShellUid();
        }
    }

    @Test
    public void testCreateMetadataFromBuilder() {
        if (shouldSkipTest()) {
            return;
        }
        BluetoothDevice testDevice =
                mAdapter.getRemoteLeDevice(TEST_MAC_ADDRESS, BluetoothDevice.ADDRESS_TYPE_RANDOM);
        BluetoothLeBroadcastMetadata.Builder builder = new BluetoothLeBroadcastMetadata.Builder()
                        .setEncrypted(false)
                        .setSourceDevice(testDevice, BluetoothDevice.ADDRESS_TYPE_RANDOM)
                        .setSourceAdvertisingSid(TEST_ADVERTISER_SID)
                        .setBroadcastId(TEST_BROADCAST_ID)
                        .setBroadcastCode(null)
                        .setPaSyncInterval(TEST_PA_SYNC_INTERVAL)
                        .setPresentationDelayMicros(TEST_PRESENTATION_DELAY_MS);
        // builder expect at least one subgroup
        assertThrows(IllegalArgumentException.class, builder::build);
        BluetoothLeBroadcastSubgroup[] subgroups = new BluetoothLeBroadcastSubgroup[] {
                createBroadcastSubgroup()
        };
        for (BluetoothLeBroadcastSubgroup subgroup : subgroups) {
            builder.addSubgroup(subgroup);
        }
        BluetoothLeBroadcastMetadata metadata = builder.build();
        assertFalse(metadata.isEncrypted());
        assertEquals(testDevice, metadata.getSourceDevice());
        assertEquals(BluetoothDevice.ADDRESS_TYPE_RANDOM, metadata.getSourceAddressType());
        assertEquals(TEST_ADVERTISER_SID, metadata.getSourceAdvertisingSid());
        assertEquals(TEST_BROADCAST_ID, metadata.getBroadcastId());
        assertNull(metadata.getBroadcastCode());
        assertEquals(TEST_PA_SYNC_INTERVAL, metadata.getPaSyncInterval());
        assertEquals(TEST_PRESENTATION_DELAY_MS, metadata.getPresentationDelayMicros());
        assertArrayEquals(subgroups,
                metadata.getSubgroups().toArray(new BluetoothLeBroadcastSubgroup[0]));
        builder.clearSubgroup();
        // builder expect at least one subgroup
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    public void testCreateMetadataFromCopy() {
        if (shouldSkipTest()) {
            return;
        }
        BluetoothDevice testDevice =
                mAdapter.getRemoteLeDevice(TEST_MAC_ADDRESS, BluetoothDevice.ADDRESS_TYPE_RANDOM);
        BluetoothLeBroadcastMetadata.Builder builder = new BluetoothLeBroadcastMetadata.Builder()
                .setEncrypted(false)
                .setSourceDevice(testDevice, BluetoothDevice.ADDRESS_TYPE_RANDOM)
                .setSourceAdvertisingSid(TEST_ADVERTISER_SID)
                .setBroadcastId(TEST_BROADCAST_ID)
                .setBroadcastCode(null)
                .setPaSyncInterval(TEST_PA_SYNC_INTERVAL)
                .setPresentationDelayMicros(TEST_PRESENTATION_DELAY_MS);
        // builder expect at least one subgroup
        assertThrows(IllegalArgumentException.class, builder::build);
        BluetoothLeBroadcastSubgroup[] subgroups = new BluetoothLeBroadcastSubgroup[] {
                createBroadcastSubgroup()
        };
        for (BluetoothLeBroadcastSubgroup subgroup : subgroups) {
            builder.addSubgroup(subgroup);
        }
        BluetoothLeBroadcastMetadata metadata = builder.build();
        BluetoothLeBroadcastMetadata metadataCopy =
                new BluetoothLeBroadcastMetadata.Builder(metadata).build();
        assertFalse(metadataCopy.isEncrypted());
        assertEquals(testDevice, metadataCopy.getSourceDevice());
        assertEquals(BluetoothDevice.ADDRESS_TYPE_RANDOM, metadataCopy.getSourceAddressType());
        assertEquals(TEST_ADVERTISER_SID, metadataCopy.getSourceAdvertisingSid());
        assertEquals(TEST_BROADCAST_ID, metadataCopy.getBroadcastId());
        assertNull(metadataCopy.getBroadcastCode());
        assertEquals(TEST_PA_SYNC_INTERVAL, metadataCopy.getPaSyncInterval());
        assertEquals(TEST_PRESENTATION_DELAY_MS, metadataCopy.getPresentationDelayMicros());
        assertArrayEquals(subgroups,
                metadataCopy.getSubgroups().toArray(new BluetoothLeBroadcastSubgroup[0]));
        builder.clearSubgroup();
        // builder expect at least one subgroup
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    private boolean shouldSkipTest() {
        return !mHasBluetooth || (!mIsBroadcastSourceSupported && !mIsBroadcastAssistantSupported);
    }

    static BluetoothLeBroadcastSubgroup createBroadcastSubgroup() {
        BluetoothLeAudioCodecConfigMetadata codecMetadata =
                new BluetoothLeAudioCodecConfigMetadata.Builder()
                        .setAudioLocation(TEST_AUDIO_LOCATION_FRONT_LEFT).build();
        BluetoothLeAudioContentMetadata contentMetadata =
                new BluetoothLeAudioContentMetadata.Builder()
                        .setProgramInfo(TEST_PROGRAM_INFO).setLanguage(TEST_LANGUAGE).build();
        BluetoothLeBroadcastSubgroup.Builder builder = new BluetoothLeBroadcastSubgroup.Builder()
                .setCodecId(TEST_CODEC_ID)
                .setCodecSpecificConfig(codecMetadata)
                .setContentMetadata(contentMetadata);
        for (BluetoothLeBroadcastChannel channel : TEST_CHANNELS) {
            builder.addChannel(channel);
        }
        return builder.build();
    }

}
