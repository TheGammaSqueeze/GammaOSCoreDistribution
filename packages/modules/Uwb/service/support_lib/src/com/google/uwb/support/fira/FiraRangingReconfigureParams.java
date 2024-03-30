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

import androidx.annotation.Nullable;

/**
 * UWB parameters used to reconfigure a FiRa session. Supports peer adding/removing.
 *
 * <p>This is passed as a bundle to the service API {@link RangingSession#reconfigure}.
 */
public class FiraRangingReconfigureParams extends FiraParams {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    @Nullable @MulticastListUpdateAction private final Integer mAction;
    @Nullable private final UwbAddress[] mAddressList;
    @Nullable private final int[] mSubSessionIdList;

    @Nullable private final Integer mBlockStrideLength;

    @Nullable @RangeDataNtfConfig private final Integer mRangeDataNtfConfig;
    @Nullable private final Integer mRangeDataProximityNear;
    @Nullable private final Integer mRangeDataProximityFar;

    private static final String KEY_ACTION = "action";
    private static final String KEY_MAC_ADDRESS_MODE = "mac_address_mode";
    private static final String KEY_ADDRESS_LIST = "address_list";
    private static final String KEY_SUB_SESSION_ID_LIST = "sub_session_id_list";
    private static final String KEY_UPDATE_BLOCK_STRIDE_LENGTH = "update_block_stride_length";
    private static final String KEY_UPDATE_RANGE_DATA_NTF_CONFIG = "update_range_data_ntf_config";
    private static final String KEY_UPDATE_RANGE_DATA_NTF_PROXIMITY_NEAR =
            "update_range_data_proximity_near";
    private static final String KEY_UPDATE_RANGE_DATA_NTF_PROXIMITY_FAR =
            "update_range_data_proximity_far";

    private FiraRangingReconfigureParams(
            @Nullable @MulticastListUpdateAction Integer action,
            @Nullable UwbAddress[] addressList,
            @Nullable int[] subSessionIdList,
            @Nullable Integer blockStrideLength,
            @Nullable Integer rangeDataNtfConfig,
            @Nullable Integer rangeDataProximityNear,
            @Nullable Integer rangeDataProximityFar) {
        mAction = action;
        mAddressList = addressList;
        mSubSessionIdList = subSessionIdList;
        mBlockStrideLength = blockStrideLength;
        mRangeDataNtfConfig = rangeDataNtfConfig;
        mRangeDataProximityNear = rangeDataProximityNear;
        mRangeDataProximityFar = rangeDataProximityFar;
    }

    @Override
    protected int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    @Nullable
    @MulticastListUpdateAction
    public Integer getAction() {
        return mAction;
    }

    @Nullable
    public UwbAddress[] getAddressList() {
        return mAddressList;
    }

    @Nullable
    public int[] getSubSessionIdList() {
        return mSubSessionIdList;
    }

    @Nullable
    public Integer getBlockStrideLength() {
        return mBlockStrideLength;
    }

    @Nullable
    public Integer getRangeDataNtfConfig() {
        return mRangeDataNtfConfig;
    }

    @Nullable
    public Integer getRangeDataProximityNear() {
        return mRangeDataProximityNear;
    }

    @Nullable
    public Integer getRangeDataProximityFar() {
        return mRangeDataProximityFar;
    }

    @Override
    public PersistableBundle toBundle() {
        PersistableBundle bundle = super.toBundle();
        if (mAction != null) {
            requireNonNull(mAddressList);
            bundle.putInt(KEY_ACTION, mAction);

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
        }

        if (mBlockStrideLength != null) {
            bundle.putInt(KEY_UPDATE_BLOCK_STRIDE_LENGTH, mBlockStrideLength);
        }

        if (mRangeDataNtfConfig != null) {
            bundle.putInt(KEY_UPDATE_RANGE_DATA_NTF_CONFIG, mRangeDataNtfConfig);
        }

        if (mRangeDataProximityNear != null) {
            bundle.putInt(KEY_UPDATE_RANGE_DATA_NTF_PROXIMITY_NEAR, mRangeDataProximityNear);
        }

        if (mRangeDataProximityFar != null) {
            bundle.putInt(KEY_UPDATE_RANGE_DATA_NTF_PROXIMITY_FAR, mRangeDataProximityFar);
        }

        return bundle;
    }

    public static FiraRangingReconfigureParams fromBundle(PersistableBundle bundle) {
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

    private static FiraRangingReconfigureParams parseVersion1(PersistableBundle bundle) {
        FiraRangingReconfigureParams.Builder builder = new FiraRangingReconfigureParams.Builder();
        if (bundle.containsKey(KEY_ACTION)) {
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
            builder.setAction(bundle.getInt(KEY_ACTION))
                    .setAddressList(addressList)
                    .setSubSessionIdList(bundle.getIntArray(KEY_SUB_SESSION_ID_LIST));
        }

        if (bundle.containsKey(KEY_UPDATE_BLOCK_STRIDE_LENGTH)) {
            builder.setBlockStrideLength(bundle.getInt(KEY_UPDATE_BLOCK_STRIDE_LENGTH));
        }

        if (bundle.containsKey(KEY_UPDATE_RANGE_DATA_NTF_CONFIG)) {
            builder.setRangeDataNtfConfig(bundle.getInt(KEY_UPDATE_RANGE_DATA_NTF_CONFIG));
        }

        if (bundle.containsKey(KEY_UPDATE_RANGE_DATA_NTF_PROXIMITY_NEAR)) {
            builder.setRangeDataProximityNear(
                    bundle.getInt(KEY_UPDATE_RANGE_DATA_NTF_PROXIMITY_NEAR));
        }

        if (bundle.containsKey(KEY_UPDATE_RANGE_DATA_NTF_PROXIMITY_FAR)) {
            builder.setRangeDataProximityFar(
                    bundle.getInt(KEY_UPDATE_RANGE_DATA_NTF_PROXIMITY_FAR));
        }

        return builder.build();
    }

    /** Builder */
    public static class Builder {
        @Nullable private Integer mAction = null;
        @Nullable private UwbAddress[] mAddressList = null;
        @Nullable private int[] mSubSessionIdList = null;

        @Nullable private Integer mBlockStrideLength = null;

        @Nullable private Integer mRangeDataNtfConfig = null;
        @Nullable private Integer mRangeDataProximityNear = null;
        @Nullable private Integer mRangeDataProximityFar = null;

        public FiraRangingReconfigureParams.Builder setAction(
                @MulticastListUpdateAction int action) {
            mAction = action;
            return this;
        }

        public FiraRangingReconfigureParams.Builder setAddressList(UwbAddress[] addressList) {
            mAddressList = addressList;
            return this;
        }

        public FiraRangingReconfigureParams.Builder setSubSessionIdList(int[] subSessionIdList) {
            mSubSessionIdList = subSessionIdList;
            return this;
        }

        public FiraRangingReconfigureParams.Builder setBlockStrideLength(int blockStrideLength) {
            mBlockStrideLength = blockStrideLength;
            return this;
        }

        public FiraRangingReconfigureParams.Builder setRangeDataNtfConfig(int rangeDataNtfConfig) {
            mRangeDataNtfConfig = rangeDataNtfConfig;
            return this;
        }

        public FiraRangingReconfigureParams.Builder setRangeDataProximityNear(
                int rangeDataProximityNear) {
            mRangeDataProximityNear = rangeDataProximityNear;
            return this;
        }

        public FiraRangingReconfigureParams.Builder setRangeDataProximityFar(
                int rangeDataProximityFar) {
            mRangeDataProximityFar = rangeDataProximityFar;
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

        public FiraRangingReconfigureParams build() {
            if (mAction != null) {
                checkAddressList();
                // Either update the address list or update ranging parameters. Not both.
                checkArgument(
                        mBlockStrideLength == null
                                && mRangeDataNtfConfig == null
                                && mRangeDataProximityNear == null
                                && mRangeDataProximityFar == null);
            } else {
                checkArgument(
                        mBlockStrideLength != null
                                || mRangeDataNtfConfig != null
                                || mRangeDataProximityNear != null
                                || mRangeDataProximityFar != null);
            }

            return new FiraRangingReconfigureParams(
                    mAction,
                    mAddressList,
                    mSubSessionIdList,
                    mBlockStrideLength,
                    mRangeDataNtfConfig,
                    mRangeDataProximityNear,
                    mRangeDataProximityFar);
        }
    }
}
