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
package com.android.server.uwb.data;
import java.util.Arrays;

public class UwbMulticastListUpdateStatus {
    private long mSessionId;
    private int mRemainingSize;
    private int mNumOfControlees;
    private int [] mContolleeMacAddress;
    private long[] mSubSessionId;
    private int[] mStatus;

    public UwbMulticastListUpdateStatus(long sessionID, int remainingSize, int numOfControlees,
            int[] contolleeMacAddress, long[] subSessionId, int[] status) {
        this.mSessionId = sessionID;
        this.mRemainingSize = remainingSize;
        this.mNumOfControlees = numOfControlees;
        this.mContolleeMacAddress = contolleeMacAddress;
        this.mSubSessionId = subSessionId;
        this.mStatus = status;
    }

    public long getSessionId() {
        return mSessionId;
    }

    public int getRemainingSize() {
        return mRemainingSize;
    }

    public int getNumOfControlee() {
        return mNumOfControlees;
    }

    public int[] getContolleeMacAddress() {
        return mContolleeMacAddress;
    }

    public long[] getSubSessionId() {
        return mSubSessionId;
    }

    public int[] getStatus() {
        return mStatus;
    }

    @Override
    public String toString() {
        return "UwbMulticastListUpdateEvent { "
                + " SessionID =" + mSessionId
                + ", RemainingSize =" + mRemainingSize
                + ", NumOfControlee =" + mNumOfControlees
                + ", MacAddress =" + Arrays.toString(mContolleeMacAddress)
                + ", SubSessionId =" + Arrays.toString(mSubSessionId)
                + ", Status =" + Arrays.toString(mStatus)
                + '}';
    }
}
