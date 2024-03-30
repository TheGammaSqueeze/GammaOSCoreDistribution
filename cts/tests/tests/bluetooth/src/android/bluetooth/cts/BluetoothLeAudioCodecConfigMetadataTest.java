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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothLeAudioCodecConfigMetadata;
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
public class BluetoothLeAudioCodecConfigMetadataTest {
    private static final long TEST_AUDIO_LOCATION_FRONT_LEFT = 0x01;
    // See Page 5 of Generic Audio assigned number specification
    private static final byte[] TEST_METADATA_BYTES = {
            // length = 0x05, type = 0x03, value = 0x00000001 (front left)
            0x05, 0x03, 0x01, 0x00, 0x00, 0x00
    };

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
    public void testCreateCodecConfigMetadataFromBuilder() {
        if (shouldSkipTest()) {
            return;
        }
        BluetoothLeAudioCodecConfigMetadata codecMetadata =
                new BluetoothLeAudioCodecConfigMetadata.Builder()
                        .setAudioLocation(TEST_AUDIO_LOCATION_FRONT_LEFT).build();
        assertEquals(TEST_AUDIO_LOCATION_FRONT_LEFT, codecMetadata.getAudioLocation());
        // TODO: Implement implicit LTV byte conversion in the API class
        // assertArrayEquals(TEST_METADATA_BYTES, codecMetadata.getRawMetadata());
    }

    @Test
    public void testCreateCodecConfigMetadataFromCopy() {
        if (shouldSkipTest()) {
            return;
        }
        BluetoothLeAudioCodecConfigMetadata codecMetadata =
                new BluetoothLeAudioCodecConfigMetadata.Builder()
                        .setAudioLocation(TEST_AUDIO_LOCATION_FRONT_LEFT).build();
        BluetoothLeAudioCodecConfigMetadata codecMetadataCopy =
                new BluetoothLeAudioCodecConfigMetadata.Builder(codecMetadata).build();
        assertEquals(codecMetadata, codecMetadataCopy);
        assertEquals(TEST_AUDIO_LOCATION_FRONT_LEFT, codecMetadataCopy.getAudioLocation());
        assertArrayEquals(codecMetadata.getRawMetadata(), codecMetadataCopy.getRawMetadata());
    }

    @Test
    public void testCreateCodecConfigMetadataFromBytes() {
        if (shouldSkipTest()) {
            return;
        }
        BluetoothLeAudioCodecConfigMetadata codecMetadata =
                BluetoothLeAudioCodecConfigMetadata.fromRawBytes(TEST_METADATA_BYTES);
        byte[] metadataBytes = codecMetadata.getRawMetadata();
        assertNotNull(metadataBytes);
        assertArrayEquals(TEST_METADATA_BYTES, metadataBytes);
        assertEquals(TEST_AUDIO_LOCATION_FRONT_LEFT, codecMetadata.getAudioLocation());
    }

    private boolean shouldSkipTest() {
        return !mHasBluetooth || (!mIsBroadcastSourceSupported && !mIsBroadcastAssistantSupported);
    }
}
