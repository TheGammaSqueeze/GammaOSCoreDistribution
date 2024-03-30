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

package com.android.server.nearby.presence;

import android.nearby.NearbyDevice;
import android.nearby.NearbyDeviceParcelable;
import android.nearby.PresenceDevice;
import android.nearby.PresenceScanFilter;
import android.nearby.PublicCredential;

import java.util.ArrayList;
import java.util.List;

/** Represents a Presence discovery result. */
public class PresenceDiscoveryResult {

    /** Creates a {@link PresenceDiscoveryResult} from the scan data. */
    public static PresenceDiscoveryResult fromDevice(NearbyDeviceParcelable device) {
        byte[] salt = device.getSalt();
        if (salt == null) {
            salt = new byte[0];
        }
        return new PresenceDiscoveryResult.Builder()
                .setTxPower(device.getTxPower())
                .setRssi(device.getRssi())
                .setSalt(salt)
                .addPresenceAction(device.getAction())
                .setPublicCredential(device.getPublicCredential())
                .build();
    }

    private final int mTxPower;
    private final int mRssi;
    private final byte[] mSalt;
    private final List<Integer> mPresenceActions;
    private final PublicCredential mPublicCredential;

    private PresenceDiscoveryResult(
            int txPower,
            int rssi,
            byte[] salt,
            List<Integer> presenceActions,
            PublicCredential publicCredential) {
        mTxPower = txPower;
        mRssi = rssi;
        mSalt = salt;
        mPresenceActions = presenceActions;
        mPublicCredential = publicCredential;
    }

    /** Returns whether the discovery result matches the scan filter. */
    public boolean matches(PresenceScanFilter scanFilter) {
        return pathLossMatches(scanFilter.getMaxPathLoss())
                && actionMatches(scanFilter.getPresenceActions())
                && credentialMatches(scanFilter.getCredentials());
    }

    private boolean pathLossMatches(int maxPathLoss) {
        return (mTxPower - mRssi) <= maxPathLoss;
    }

    private boolean actionMatches(List<Integer> filterActions) {
        if (filterActions.isEmpty()) {
            return true;
        }
        return filterActions.stream().anyMatch(mPresenceActions::contains);
    }

    private boolean credentialMatches(List<PublicCredential> credentials) {
        return credentials.contains(mPublicCredential);
    }

    /** Converts a presence device from the discovery result. */
    public PresenceDevice toPresenceDevice() {
        return new PresenceDevice.Builder(
                // Use the public credential hash as the device Id.
                String.valueOf(mPublicCredential.hashCode()),
                mSalt,
                mPublicCredential.getSecretId(),
                mPublicCredential.getEncryptedMetadata())
                .setRssi(mRssi)
                .addMedium(NearbyDevice.Medium.BLE)
                .build();
    }

    /** Builder for {@link PresenceDiscoveryResult}. */
    public static class Builder {
        private int mTxPower;
        private int mRssi;
        private byte[] mSalt;

        private PublicCredential mPublicCredential;
        private final List<Integer> mPresenceActions;

        public Builder() {
            mPresenceActions = new ArrayList<>();
        }

        /** Sets the calibrated tx power for the discovery result. */
        public Builder setTxPower(int txPower) {
            mTxPower = txPower;
            return this;
        }

        /** Sets the rssi for the discovery result. */
        public Builder setRssi(int rssi) {
            mRssi = rssi;
            return this;
        }

        /** Sets the salt for the discovery result. */
        public Builder setSalt(byte[] salt) {
            mSalt = salt;
            return this;
        }

        /** Sets the public credential for the discovery result. */
        public Builder setPublicCredential(PublicCredential publicCredential) {
            mPublicCredential = publicCredential;
            return this;
        }

        /** Adds presence action of the discovery result. */
        public Builder addPresenceAction(int presenceAction) {
            mPresenceActions.add(presenceAction);
            return this;
        }

        /** Builds a {@link PresenceDiscoveryResult}. */
        public PresenceDiscoveryResult build() {
            return new PresenceDiscoveryResult(
                    mTxPower, mRssi, mSalt, mPresenceActions, mPublicCredential);
        }
    }
}
