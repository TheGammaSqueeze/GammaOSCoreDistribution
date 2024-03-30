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

package android.nearby.fastpair.provider;

import static com.google.common.io.BaseEncoding.base16;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.google.protobuf.ByteString;

import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

/** Stores fast pair related information for each paired device */
public class FastPairSimulatorDatabase {

    private static final String SHARED_PREF_NAME =
            "android.nearby.fastpair.provider.fastpairsimulator";
    private static final String KEY_DEVICE_NAME = "DEVICE_NAME";
    private static final String KEY_ACCOUNT_KEYS = "ACCOUNT_KEYS";
    private static final int MAX_NUMBER_OF_ACCOUNT_KEYS = 8;

    // [for SASS]
    private static final String KEY_FAST_PAIR_SEEKER_DEVICE = "FAST_PAIR_SEEKER_DEVICE";

    private final SharedPreferences mSharedPreferences;

    public FastPairSimulatorDatabase(Context context) {
        mSharedPreferences = context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
    }

    /** Adds single account key. */
    public void addAccountKey(byte[] accountKey) {
        if (mSharedPreferences == null) {
            return;
        }

        Set<ByteString> accountKeys = new HashSet<>(getAccountKeys());
        if (accountKeys.size() >= MAX_NUMBER_OF_ACCOUNT_KEYS) {
            Set<ByteString> removedKeys = new HashSet<>();
            int removedCount = accountKeys.size() - MAX_NUMBER_OF_ACCOUNT_KEYS + 1;
            for (ByteString key : accountKeys) {
                if (removedKeys.size() == removedCount) {
                    break;
                }
                removedKeys.add(key);
            }

            accountKeys.removeAll(removedKeys);
        }

        // Just make sure the newest key will not be removed.
        accountKeys.add(ByteString.copyFrom(accountKey));
        setAccountKeys(accountKeys);
    }

    /** Sets account keys, overrides all. */
    public void setAccountKeys(Set<ByteString> accountKeys) {
        if (mSharedPreferences == null) {
            return;
        }

        Set<String> keys = new HashSet<>();
        for (ByteString item : accountKeys) {
            keys.add(base16().encode(item.toByteArray()));
        }

        mSharedPreferences.edit().putStringSet(KEY_ACCOUNT_KEYS, keys).apply();
    }

    /** Gets all account keys. */
    public Set<ByteString> getAccountKeys() {
        if (mSharedPreferences == null) {
            return new HashSet<>();
        }

        Set<String> keys = mSharedPreferences.getStringSet(KEY_ACCOUNT_KEYS, new HashSet<>());
        Set<ByteString> accountKeys = new HashSet<>();
        // Add new account keys one by one.
        for (String key : keys) {
            accountKeys.add(ByteString.copyFrom(base16().decode(key)));
        }

        return accountKeys;
    }

    /** Sets local device name. */
    public void setLocalDeviceName(byte[] deviceName) {
        if (mSharedPreferences == null) {
            return;
        }

        String humanReadableName = deviceName != null ? new String(deviceName, UTF_8) : null;
        if (humanReadableName == null) {
            mSharedPreferences.edit().remove(KEY_DEVICE_NAME).apply();
        } else {
            mSharedPreferences.edit().putString(KEY_DEVICE_NAME, humanReadableName).apply();
        }
    }

    /** Gets local device name. */
    @Nullable
    public byte[] getLocalDeviceName() {
        if (mSharedPreferences == null) {
            return null;
        }

        String deviceName = mSharedPreferences.getString(KEY_DEVICE_NAME, null);
        return deviceName != null ? deviceName.getBytes(UTF_8) : null;
    }

    /**
     * [for SASS] Adds seeker device info. <a
     * href="http://go/smart-audio-source-switching-design">Sass design doc</a>
     */
    public void addFastPairSeekerDevice(@Nullable BluetoothDevice device, byte[] accountKey) {
        if (mSharedPreferences == null) {
            return;
        }

        if (device == null) {
            return;
        }

        // When hitting size limitation, choose the existing items to delete.
        Set<FastPairSeekerDevice> fastPairSeekerDevices = getFastPairSeekerDevices();
        if (fastPairSeekerDevices.size() > MAX_NUMBER_OF_ACCOUNT_KEYS) {
            int removedCount = fastPairSeekerDevices.size() - MAX_NUMBER_OF_ACCOUNT_KEYS + 1;
            Set<FastPairSeekerDevice> removedFastPairDevices = new HashSet<>();
            for (FastPairSeekerDevice fastPairDevice : fastPairSeekerDevices) {
                if (removedFastPairDevices.size() == removedCount) {
                    break;
                }
                removedFastPairDevices.add(fastPairDevice);
            }
            fastPairSeekerDevices.removeAll(removedFastPairDevices);
        }

        fastPairSeekerDevices.add(new FastPairSeekerDevice(device, accountKey));
        setFastPairSeekerDevices(fastPairSeekerDevices);
    }

    /** [for SASS] Sets all seeker device info, overrides all. */
    public void setFastPairSeekerDevices(Set<FastPairSeekerDevice> fastPairSeekerDeviceSet) {
        if (mSharedPreferences == null) {
            return;
        }

        Set<String> rawStringSet = new HashSet<>();
        for (FastPairSeekerDevice item : fastPairSeekerDeviceSet) {
            rawStringSet.add(item.toRawString());
        }

        mSharedPreferences.edit().putStringSet(KEY_FAST_PAIR_SEEKER_DEVICE, rawStringSet).apply();
    }

    /** [for SASS] Gets all seeker device info. */
    public Set<FastPairSeekerDevice> getFastPairSeekerDevices() {
        if (mSharedPreferences == null) {
            return new HashSet<>();
        }

        Set<FastPairSeekerDevice> fastPairSeekerDevices = new HashSet<>();
        Set<String> rawStringSet =
                mSharedPreferences.getStringSet(KEY_FAST_PAIR_SEEKER_DEVICE, new HashSet<>());
        for (String rawString : rawStringSet) {
            FastPairSeekerDevice fastPairDevice = FastPairSeekerDevice.fromRawString(rawString);
            if (fastPairDevice == null) {
                continue;
            }
            fastPairSeekerDevices.add(fastPairDevice);
        }

        return fastPairSeekerDevices;
    }

    /** Defines data structure for the paired Fast Pair device. */
    public static class FastPairSeekerDevice {
        private static final int INDEX_DEVICE = 0;
        private static final int INDEX_ACCOUNT_KEY = 1;

        private final BluetoothDevice mDevice;
        private final byte[] mAccountKey;

        private FastPairSeekerDevice(BluetoothDevice device, byte[] accountKey) {
            this.mDevice = device;
            this.mAccountKey = accountKey;
        }

        public BluetoothDevice getBluetoothDevice() {
            return mDevice;
        }

        public byte[] getAccountKey() {
            return mAccountKey;
        }

        public String toRawString() {
            return String.format("%s,%s", mDevice, base16().encode(mAccountKey));
        }

        /** Decodes the raw string if possible. */
        @Nullable
        public static FastPairSeekerDevice fromRawString(String rawString) {
            BluetoothDevice device = null;
            byte[] accountKey = null;
            int step = INDEX_DEVICE;

            StringTokenizer tokenizer = new StringTokenizer(rawString, ",");
            while (tokenizer.hasMoreElements()) {
                boolean shouldStop = false;
                String token = tokenizer.nextToken();
                switch (step) {
                    case INDEX_DEVICE:
                        try {
                            device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(token);
                        } catch (IllegalArgumentException e) {
                            device = null;
                        }
                        break;
                    case INDEX_ACCOUNT_KEY:
                        accountKey = base16().decode(token);
                        if (accountKey.length != 16) {
                            accountKey = null;
                        }
                        break;
                    default:
                        shouldStop = true;
                }

                if (shouldStop) {
                    break;
                }
                step++;
            }
            if (device != null && accountKey != null) {
                return new FastPairSeekerDevice(device, accountKey);
            }
            return null;
        }
    }
}
