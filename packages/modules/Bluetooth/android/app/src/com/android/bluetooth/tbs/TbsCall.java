/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

package com.android.bluetooth.tbs;

import android.bluetooth.BluetoothLeCall;

import java.util.UUID;

public class TbsCall {

    public static final int INDEX_UNASSIGNED = 0x00;
    public static final int INDEX_MIN = 0x01;
    public static final int INDEX_MAX = 0xFF;

    private int mState;
    private String mUri;
    private int mFlags;
    private String mFriendlyName;

    private TbsCall(int state, String uri, int flags, String friendlyName) {
        mState = state;
        mUri = uri;
        mFlags = flags;
        mFriendlyName = friendlyName;
    }

    public static TbsCall create(BluetoothLeCall call) {
        return new TbsCall(call.getState(), call.getUri(), call.getCallFlags(),
                call.getFriendlyName());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TbsCall that = (TbsCall) o;
        // check the state only
        return mState == that.mState;
    }

    public int getState() {
        return mState;
    }

    public void setState(int state) {
        mState = state;
    }

    public String getUri() {
        return mUri;
    }

    public int getFlags() {
        return mFlags;
    }

    public boolean isIncoming() {
        return (mFlags & BluetoothLeCall.FLAG_OUTGOING_CALL) == 0;
    }

    public String getFriendlyName() {
        return mFriendlyName;
    }
}
