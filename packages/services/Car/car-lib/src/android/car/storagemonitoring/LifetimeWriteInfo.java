/*
 * Copyright (C) 2018 The Android Open Source Project
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
package android.car.storagemonitoring;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.annotation.SystemApi;
import android.car.annotation.AddedInOrBefore;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.JsonWriter;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Objects;

/**
 * Information about how many bytes were written to a filesystem during its lifetime.
 *
 * @hide
 */
@SystemApi
public final class LifetimeWriteInfo implements Parcelable {
    @AddedInOrBefore(majorVersion = 33)
    public static final Creator<IoStats> CREATOR = new Creator<IoStats>() {
        @Override
        public IoStats createFromParcel(Parcel in) {
            return new IoStats(in);
        }

        @Override
        public IoStats[] newArray(int size) {
            return new IoStats[size];
        }
    };

    @AddedInOrBefore(majorVersion = 33)
    public final String partition;
    @AddedInOrBefore(majorVersion = 33)
    public final String fstype;
    @AddedInOrBefore(majorVersion = 33)
    public final long writtenBytes;

    public LifetimeWriteInfo(String partition, String fstype, long writtenBytes) {
        this.partition = Objects.requireNonNull(partition);
        this.fstype = Objects.requireNonNull(fstype);
        if (writtenBytes < 0) {
            throw new IllegalArgumentException("writtenBytes must be non-negative");
        }
        this.writtenBytes = writtenBytes;
    }

    public LifetimeWriteInfo(Parcel in) {
        this.partition = in.readString();
        this.fstype = in.readString();
        this.writtenBytes = in.readLong();
    }

    /**
     * @hide
     */
    public LifetimeWriteInfo(JSONObject in) throws JSONException {
        partition = in.getString("partition");
        fstype = in.getString("fstype");
        writtenBytes = in.getLong("writtenBytes");
    }


    @Override
    @AddedInOrBefore(majorVersion = 33)
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(partition);
        dest.writeString(fstype);
        dest.writeLong(writtenBytes);
    }

    /**
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public void writeToJson(JsonWriter jsonWriter) throws IOException {
        jsonWriter.beginObject();
        jsonWriter.name("partition").value(partition);
        jsonWriter.name("fstype").value(fstype);
        jsonWriter.name("writtenBytes").value(writtenBytes);
        jsonWriter.endObject();
    }


    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    @AddedInOrBefore(majorVersion = 33)
    public int describeContents() {
        return 0;
    }

    @Override
    @AddedInOrBefore(majorVersion = 33)
    public boolean equals(Object other) {
        if (other instanceof LifetimeWriteInfo) {
            LifetimeWriteInfo lifetime = (LifetimeWriteInfo) other;
            return partition.equals(lifetime.partition)
                    && fstype.equals(lifetime.fstype)
                    && writtenBytes == lifetime.writtenBytes;
        }

        return false;
    }

    @Override
    @AddedInOrBefore(majorVersion = 33)
    public String toString() {
        return String.format("for partition %s of type %s, %d bytes were written",
                partition, fstype, writtenBytes);
    }
}
