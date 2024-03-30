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

package android.nearby;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.internal.util.Preconditions;


/**
 * Represents a data element in Nearby Presence.
 *
 * @hide
 */
@SystemApi
public final class DataElement implements Parcelable {

    private final int mKey;
    private final byte[] mValue;

    /**
     * Constructs a {@link DataElement}.
     */
    public DataElement(int key, @NonNull byte[] value) {
        Preconditions.checkState(value != null, "value cannot be null");
        mKey = key;
        mValue = value;
    }

    @NonNull
    public static final Creator<DataElement> CREATOR = new Creator<DataElement>() {
        @Override
        public DataElement createFromParcel(Parcel in) {
            int key = in.readInt();
            byte[] value = new byte[in.readInt()];
            in.readByteArray(value);
            return new DataElement(key, value);
        }

        @Override
        public DataElement[] newArray(int size) {
            return new DataElement[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mKey);
        dest.writeInt(mValue.length);
        dest.writeByteArray(mValue);
    }

    /**
     * Returns the key of the data element, as defined in the nearby presence specification.
     */
    public int getKey() {
        return mKey;
    }

    /**
     * Returns the value of the data element.
     */
    @NonNull
    public byte[] getValue() {
        return mValue;
    }
}
