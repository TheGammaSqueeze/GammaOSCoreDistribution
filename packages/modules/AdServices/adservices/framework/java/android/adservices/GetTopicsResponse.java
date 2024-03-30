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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represent the result from the getTopics API.
 *
 * @hide
 */
public final class GetTopicsResponse implements Parcelable {
    /**
     * Result codes from {@link getTopics} methods.
     *
     * @hide
     */
    @IntDef(
            value = {
                RESULT_OK,
                RESULT_INTERNAL_ERROR,
                RESULT_INVALID_ARGUMENT,
                RESULT_IO_ERROR,
                RESULT_RATE_LIMIT_REACHED,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ResultCode {}

    /** The call was successful. */
    public static final int RESULT_OK = 0;

    /**
     * An internal error occurred within Topics API, which the caller cannot address.
     *
     * <p>This error may be considered similar to {@link IllegalStateException}
     */
    public static final int RESULT_INTERNAL_ERROR = 1;

    /**
     * The caller supplied invalid arguments to the call.
     *
     * <p>This error may be considered similar to {@link IllegalArgumentException}.
     */
    public static final int RESULT_INVALID_ARGUMENT = 2;

    /**
     * An issue occurred reading or writing to storage. The call might succeed if repeated.
     *
     * <p>This error may be considered similar to {@link java.io.IOException}.
     */
    public static final int RESULT_IO_ERROR = 3;

    /**
     * The caller has reached the API call limit.
     *
     * <p>The caller should back off and try later.
     */
    public static final int RESULT_RATE_LIMIT_REACHED = 4;

    private final @ResultCode int mResultCode;
    @Nullable private final String mErrorMessage;
    private final List<Long> mTaxonomyVersions;
    private final List<Long> mModelVersions;
    private final List<String> mTopics;

    private GetTopicsResponse(
            @ResultCode int resultCode,
            @Nullable String errorMessage,
            @NonNull List<Long> taxonomyVersions,
            @NonNull List<Long> modelVersions,
            @NonNull List<String> topics) {
        mResultCode = resultCode;
        mErrorMessage = errorMessage;
        mTaxonomyVersions = taxonomyVersions;
        mModelVersions = modelVersions;
        mTopics = topics;
    }

    private GetTopicsResponse(@NonNull Parcel in) {
        mResultCode = in.readInt();
        mErrorMessage = in.readString();

        mTaxonomyVersions = Collections.unmodifiableList(readLongList(in));
        mModelVersions = Collections.unmodifiableList(readLongList(in));

        List<String> topicsMutable = new ArrayList<>();
        in.readStringList(topicsMutable);
        mTopics = Collections.unmodifiableList(topicsMutable);
    }

    public static final @NonNull Creator<GetTopicsResponse> CREATOR =
            new Parcelable.Creator<GetTopicsResponse>() {
                @Override
                public GetTopicsResponse createFromParcel(Parcel in) {
                    return new GetTopicsResponse(in);
                }

                @Override
                public GetTopicsResponse[] newArray(int size) {
                    return new GetTopicsResponse[size];
                }
            };

    /** @hide */
    @Override
    public int describeContents() {
        return 0;
    }

    /** @hide */
    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        out.writeInt(mResultCode);
        out.writeString(mErrorMessage);
        writeLongList(out, mTaxonomyVersions);
        writeLongList(out, mModelVersions);
        out.writeStringList(mTopics);
    }

    /**
     * Returns {@code true} if {@link #getResultCode} equals {@link GetTopicsResponse#RESULT_OK}.
     */
    public boolean isSuccess() {
        return getResultCode() == RESULT_OK;
    }

    /** Returns one of the {@code RESULT} constants defined in {@link GetTopicsResponse}. */
    public int getResultCode() {
        return mResultCode;
    }

    /**
     * Returns the error message associated with this result.
     *
     * <p>If {@link #isSuccess} is {@code true}, the error message is always {@code null}. The error
     * message may be {@code null} even if {@link #isSuccess} is {@code false}.
     */
    @Nullable
    public String getErrorMessage() {
        return mErrorMessage;
    }

    /** Get the Taxonomy Versions. */
    public List<Long> getTaxonomyVersions() {
        return mTaxonomyVersions;
    }

    /** Get the Model Versions. */
    public List<Long> getModelVersions() {
        return mModelVersions;
    }

    @NonNull
    public List<String> getTopics() {
        return mTopics;
    }

    // Read the list of long from parcel.
    private static List<Long> readLongList(@NonNull Parcel in) {
        List<Long> list = new ArrayList<>();

        int toReadCount = in.readInt();
        // Negative toReadCount is handled implicitly
        for (int i = 0; i < toReadCount; i++) {
            list.add(in.readLong());
        }

        return list;
    }

    // Write a List of Long to parcel.
    private static void writeLongList(@NonNull Parcel out, @Nullable List<Long> val) {
        if (val == null) {
            out.writeInt(-1);
            return;
        }
        out.writeInt(val.size());
        for (Long l : val) {
            out.writeLong(l);
        }
    }

    /**
     * Builder for {@link GetTopicsResponse} objects.
     *
     * @hide
     */
    public static final class Builder {
        private @ResultCode int mResultCode;
        @Nullable private String mErrorMessage;
        private List<Long> mTaxonomyVersions = new ArrayList<>();
        private List<Long> mModelVersions = new ArrayList<>();
        private List<String> mTopics = new ArrayList<>();

        public Builder() {}

        /** Set the Result Code. */
        public @NonNull Builder setResultCode(@ResultCode int resultCode) {
            mResultCode = resultCode;
            return this;
        }

        /** Set the Error Message. */
        public @NonNull Builder setErrorMessage(@Nullable String errorMessage) {
            mErrorMessage = errorMessage;
            return this;
        }

        /** Set the Taxonomy Version. */
        public @NonNull Builder setTaxonomyVersions(@NonNull List<Long> taxonomyVersions) {
            mTaxonomyVersions = taxonomyVersions;
            return this;
        }

        /** Set the Model Version. */
        public @NonNull Builder setModelVersions(@NonNull List<Long> modelVersions) {
            mModelVersions = modelVersions;
            return this;
        }

        /** Set the list of the returned Topics */
        public @NonNull Builder setTopics(@NonNull List<String> topics) {
            mTopics = topics;
            return this;
        }

        /**
         * Builds a {@link GetTopicsResponse} instance.
         *
         * <p>throws IllegalArgumentException if any of the params are null or there is any mismatch
         * in the size of ModelVersions and TaxonomyVersions.
         */
        public @NonNull GetTopicsResponse build() {
            if (mTopics == null || mTaxonomyVersions == null || mModelVersions == null) {
                throw new IllegalArgumentException(
                        "Topics or TaxonomyVersion or ModelVersion is null");
            }

            if (mTopics.size() != mTaxonomyVersions.size()
                    || mTopics.size() != mModelVersions.size()) {
                throw new IllegalArgumentException("Size mismatch in Topics");
            }

            return new GetTopicsResponse(
                    mResultCode, mErrorMessage, mTaxonomyVersions, mModelVersions, mTopics);
        }
    }
}
