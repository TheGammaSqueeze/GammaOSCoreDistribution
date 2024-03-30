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

package com.google.uwb.support.fira;

import static com.android.internal.util.Preconditions.checkArgument;

import static java.util.Objects.requireNonNull;

import android.os.PersistableBundle;
import android.uwb.RangingSession;
import android.uwb.UwbAddress;
import android.uwb.UwbManager;

import androidx.annotation.Nullable;

/**
 * UWB parameters used to add/remove controlees for a FiRa session
 *
 * <p>This is passed as a bundle to the service API {@link RangingSession#addControlee} and
 * {@link RangingSession#removeControlee}.
 */
public class FiraControleeParams extends FiraParams {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    @Nullable private final UwbAddress[] mAddressList;
    @Nullable private final int[] mSubSessionIdList;

    private static final String KEY_MAC_ADDRESS_MODE = "mac_address_mode";
    private static final String KEY_ADDRESS_LIST = "address_list";
    private static final String KEY_SUB_SESSION_ID_LIST = "sub_session_id_list";

    private FiraControleeParams(
            @Nullable UwbAddress[] addressList,
            @Nullable int[] subSessionIdList) {
        mAddressList = addressList;
        mSubSessionIdList = subSessionIdList;
    }

    @Override
    protected int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    @Nullable
    public UwbAddress[] getAddressList() {
        return mAddressList;
    }

    @Nullable
    public int[] getSubSessionIdList() {
        return mSubSessionIdList;
    }
    @Override
    public PersistableBundle toBundle() {
        PersistableBundle bundle = super.toBundle();
        requireNonNull(mAddressList);

        long[] addressList = new long[mAddressList.length];
        int i = 0;
        for (UwbAddress address : mAddressList) {
            addressList[i++] = uwbAddressToLong(address);
        }
        int macAddressMode = MAC_ADDRESS_MODE_2_BYTES;
        if (mAddressList[0].size() == UwbAddress.EXTENDED_ADDRESS_BYTE_LENGTH) {
            macAddressMode = MAC_ADDRESS_MODE_8_BYTES;
        }
        bundle.putInt(KEY_MAC_ADDRESS_MODE, macAddressMode);
        bundle.putLongArray(KEY_ADDRESS_LIST, addressList);
        bundle.putIntArray(KEY_SUB_SESSION_ID_LIST, mSubSessionIdList);
        return bundle;
    }

    public static FiraControleeParams fromBundle(PersistableBundle bundle) {
        if (!isCorrectProtocol(bundle)) {
            throw new IllegalArgumentException("Invalid protocol");
        }

        switch (getBundleVersion(bundle)) {
            case BUNDLE_VERSION_1:
                return parseVersion1(bundle);

            default:
                throw new IllegalArgumentException("Invalid bundle version");
        }
    }

    private static FiraControleeParams parseVersion1(PersistableBundle bundle) {
        FiraControleeParams.Builder builder = new FiraControleeParams.Builder();
        int macAddressMode = bundle.getInt(KEY_MAC_ADDRESS_MODE);
        int addressByteLength = UwbAddress.SHORT_ADDRESS_BYTE_LENGTH;
        if (macAddressMode == MAC_ADDRESS_MODE_8_BYTES) {
            addressByteLength = UwbAddress.EXTENDED_ADDRESS_BYTE_LENGTH;
        }
        long[] addresses = bundle.getLongArray(KEY_ADDRESS_LIST);
        UwbAddress[] addressList = new UwbAddress[addresses.length];
        for (int i = 0; i < addresses.length; i++) {
            addressList[i] = longToUwbAddress(addresses[i], addressByteLength);
        }
        builder.setAddressList(addressList);
        builder.setSubSessionIdList(bundle.getIntArray(KEY_SUB_SESSION_ID_LIST));
        return builder.build();
    }

    /** Builder */
    public static class Builder {
        @Nullable private UwbAddress[] mAddressList = null;
        @Nullable private int[] mSubSessionIdList = null;

        public FiraControleeParams.Builder setAddressList(UwbAddress[] addressList) {
            mAddressList = addressList;
            return this;
        }

        public FiraControleeParams.Builder setSubSessionIdList(int[] subSessionIdList) {
            mSubSessionIdList = subSessionIdList;
            return this;
        }

        private void checkAddressList() {
            checkArgument(mAddressList != null && mAddressList.length > 0);
            for (UwbAddress uwbAddress : mAddressList) {
                requireNonNull(uwbAddress);
                checkArgument(uwbAddress.size() == UwbAddress.SHORT_ADDRESS_BYTE_LENGTH);
            }

            checkArgument(
                    mSubSessionIdList == null || mSubSessionIdList.length == mAddressList.length);
        }

        public FiraControleeParams build() {
            checkAddressList();
            return new FiraControleeParams(
                    mAddressList,
                    mSubSessionIdList);
        }
    }
}
