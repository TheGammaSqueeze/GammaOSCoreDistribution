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

package android.adservices;

import android.annotation.NonNull;
import android.content.AttributionSource;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Represent input params to the getTopics API.
 *
 * @hide
 */
public class GetTopicsRequest implements Parcelable {
    AttributionSource mAttributionSource;

    private GetTopicsRequest(AttributionSource attributionSource) {
        mAttributionSource = attributionSource;
    }

    private GetTopicsRequest(@NonNull Parcel in) {
        mAttributionSource = AttributionSource.CREATOR.createFromParcel(in);
    }

    public static final @NonNull Creator<GetTopicsRequest> CREATOR =
            new Parcelable.Creator<GetTopicsRequest>() {
                @Override
                public GetTopicsRequest createFromParcel(Parcel in) {
                    return new GetTopicsRequest(in);
                }

                @Override
                public GetTopicsRequest[] newArray(int size) {
                    return new GetTopicsRequest[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        mAttributionSource.writeToParcel(out, flags);
    }

    /** Get the AttributionSource.
     * The AttributionSource is used to obtain the calling chain
     */
    @NonNull
    public AttributionSource getAttributionSource() {
        return mAttributionSource;
    }

    /** Builder for {@link GetTopicsRequest} objects. */
    public static final class Builder {
        private AttributionSource mAttributionSource;

        public Builder() {}

        /** Set the AttributionSource. */
        public @NonNull Builder setAttributionSource(@NonNull AttributionSource attributionSource) {
            mAttributionSource = attributionSource;
            return this;
        }

        /** Builds a {@link GetTopicsRequest} instance. */
        public @NonNull GetTopicsRequest build() {
            if (mAttributionSource == null) {
                throw new IllegalArgumentException("AttributionSource unset");
            }

            return new GetTopicsRequest(mAttributionSource);
        }
    }
}
