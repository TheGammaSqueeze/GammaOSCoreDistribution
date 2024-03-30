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

package com.android.queryable.queries;

import android.os.Parcel;
import android.os.Parcelable;

import com.android.queryable.Queryable;
import com.android.queryable.info.DelegatedAdminReceiverInfo;

/** Implementation of {@link DelegatedAdminReceiverQuery}. */
public final class DelegatedAdminReceiverQueryHelper<E extends Queryable>
        implements DelegatedAdminReceiverQuery<E> {

    private final transient E mQuery;
    private final BroadcastReceiverQueryHelper<E> mBroadcastReceiverQueryHelper;

    DelegatedAdminReceiverQueryHelper() {
        mQuery = (E) this;
        mBroadcastReceiverQueryHelper = new BroadcastReceiverQueryHelper<>(mQuery);
    }

    public DelegatedAdminReceiverQueryHelper(E query) {
        mQuery = query;
        mBroadcastReceiverQueryHelper = new BroadcastReceiverQueryHelper<>(query);
    }

    private DelegatedAdminReceiverQueryHelper(Parcel in) {
        mQuery = null;
        mBroadcastReceiverQueryHelper = in.readParcelable(
                DelegatedAdminReceiverQueryHelper.class.getClassLoader());
    }

    @Override
    public BroadcastReceiverQuery<E> broadcastReceiver() {
        return mBroadcastReceiverQueryHelper;
    }

    /** {@code true} if all filters are met by {@code value}. */
    @Override
    public boolean matches(DelegatedAdminReceiverInfo value) {
        return mBroadcastReceiverQueryHelper.matches(value);
    }

    @Override
    public String describeQuery(String fieldName) {
        return Queryable.joinQueryStrings(
                mBroadcastReceiverQueryHelper.describeQuery(fieldName + ".broadcastReceiver")
        );
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeParcelable(mBroadcastReceiverQueryHelper, flags);
    }

    public static final Parcelable.Creator<DelegatedAdminReceiverQueryHelper> CREATOR =
            new Parcelable.Creator<DelegatedAdminReceiverQueryHelper>() {
                public DelegatedAdminReceiverQueryHelper createFromParcel(Parcel in) {
                    return new DelegatedAdminReceiverQueryHelper(in);
                }

                public DelegatedAdminReceiverQueryHelper[] newArray(int size) {
                    return new DelegatedAdminReceiverQueryHelper[size];
                }
    };
}
