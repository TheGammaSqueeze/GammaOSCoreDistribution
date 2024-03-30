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
import com.android.queryable.info.BroadcastReceiverInfo;

/** Implementation of {@link BroadcastReceiverQuery}. */
public final class BroadcastReceiverQueryHelper<E extends Queryable>
        implements BroadcastReceiverQuery<E> {

    private final transient E mQuery;
    private final ClassQueryHelper<E> mReceiverClassQueryHelper;

    BroadcastReceiverQueryHelper() {
        mQuery = (E) this;
        mReceiverClassQueryHelper = new ClassQueryHelper<>(mQuery);
    }

    public BroadcastReceiverQueryHelper(E query) {
        mQuery = query;
        mReceiverClassQueryHelper = new ClassQueryHelper<>(query);
    }

    private BroadcastReceiverQueryHelper(Parcel in) {
        mQuery = null;
        mReceiverClassQueryHelper = in.readParcelable(
                BroadcastReceiverQueryHelper.class.getClassLoader());
    }

    @Override
    public ClassQuery<E> receiverClass() {
        return mReceiverClassQueryHelper;
    }

    /** {@code true} if all filters are met by {@code value}. */
    @Override
    public boolean matches(BroadcastReceiverInfo value) {
        return mReceiverClassQueryHelper.matches(value);
    }

    @Override
    public String describeQuery(String fieldName) {
        return Queryable.joinQueryStrings(
                mReceiverClassQueryHelper.describeQuery(fieldName + ".receiverClass")
        );
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeParcelable(mReceiverClassQueryHelper, flags);
    }

    public static final Parcelable.Creator<BroadcastReceiverQueryHelper> CREATOR =
            new Parcelable.Creator<BroadcastReceiverQueryHelper>() {
                public BroadcastReceiverQueryHelper createFromParcel(Parcel in) {
                    return new BroadcastReceiverQueryHelper(in);
                }

                public BroadcastReceiverQueryHelper[] newArray(int size) {
                    return new BroadcastReceiverQueryHelper[size];
                }
    };
}
