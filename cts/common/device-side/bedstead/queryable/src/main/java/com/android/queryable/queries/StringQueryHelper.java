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

import static com.android.queryable.util.ParcelableUtils.writeStringSet;

import android.os.Parcel;
import android.os.Parcelable;

import com.android.queryable.Queryable;
import com.android.queryable.util.ParcelableUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Implementation of {@link StringQuery}. */
public final class StringQueryHelper<E extends Queryable>
        implements StringQuery<E>, Serializable{

    private static final long serialVersionUID = 1;

    private final transient E mQuery;
    private String mEqualsValue;
    private Set<String> mNotEqualsValues = new HashSet<>();

    StringQueryHelper() {
        mQuery = (E) this;
    }

    public StringQueryHelper(E query) {
        mQuery = query;
    }

    private StringQueryHelper(Parcel in) {
        mQuery = null;
        mEqualsValue = in.readString();
        mNotEqualsValues = ParcelableUtils.readStringSet(in);
    }

    @Override
    public E isEqualTo(String string) {
        mEqualsValue = string;
        return mQuery;
    }

    @Override
    public E isNotEqualTo(String string) {
        mNotEqualsValues.add(string);
        return mQuery;
    }

    @Override
    public boolean matches(String value) {
        if (mEqualsValue != null && !mEqualsValue.equals(value)) {
            return false;
        }
        if (mNotEqualsValues.contains(value)) {
            return false;
        }

        return true;
    }

    public static boolean matches(StringQueryHelper<?> stringQueryHelper, String value) {
        return stringQueryHelper.matches(value);
    }

    /**
     * True if this query has not been configured.
     */
    public boolean isEmpty() {
        return mEqualsValue == null && mNotEqualsValues.isEmpty();
    }

    /**
     * True if this query is for an exact string match.
     */
    public boolean isQueryingForExactMatch() {
        return mEqualsValue != null;
    }

    @Override
    public String describeQuery(String fieldName) {
        List<String> queryStrings = new ArrayList<>();
        if (mEqualsValue != null) {
            queryStrings.add(fieldName + "=\"" + mEqualsValue + "\"");
        }

        for (String notEquals : mNotEqualsValues) {
            queryStrings.add(fieldName + "!=\"" + notEquals + "\"");
        }

        return Queryable.joinQueryStrings(queryStrings);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mEqualsValue);
        writeStringSet(out, mNotEqualsValues);
    }

    public static final Parcelable.Creator<StringQueryHelper> CREATOR =
            new Parcelable.Creator<StringQueryHelper>() {
                public StringQueryHelper createFromParcel(Parcel in) {
                    return new StringQueryHelper(in);
                }

                public StringQueryHelper[] newArray(int size) {
                    return new StringQueryHelper[size];
                }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StringQueryHelper)) return false;
        StringQueryHelper<?> that = (StringQueryHelper<?>) o;
        return Objects.equals(mEqualsValue, that.mEqualsValue) && Objects.equals(
                mNotEqualsValues, that.mNotEqualsValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mEqualsValue, mNotEqualsValues);
    }
}
