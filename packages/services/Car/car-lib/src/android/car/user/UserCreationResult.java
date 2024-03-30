/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.car.user;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.car.annotation.AddedInOrBefore;
import android.os.Parcelable;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.DataClass;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * User creation results.
 *
 * @hide
 */
@DataClass(
        genToString = true,
        genHiddenConstructor = true,
        genHiddenConstDefs = true)
public final class UserCreationResult implements Parcelable, OperationResult {

    /**
     * {@link Status} called when user creation is successful for both HAL and Android.
     *
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int STATUS_SUCCESSFUL = CommonResults.STATUS_SUCCESSFUL;

    /**
     * {@link Status} called when user creation failed on Android - HAL is not even called in this
     * case.
     *
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int STATUS_ANDROID_FAILURE = CommonResults.STATUS_ANDROID_FAILURE;

    /**
     * {@link Status} called when user was created on Android but HAL returned a failure - the
     * Android user is automatically removed.
     *
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int STATUS_HAL_FAILURE = CommonResults.STATUS_HAL_FAILURE;

    /**
     * {@link Status} called when user creation is failed for HAL for some internal error - the
     * Android user is not automatically removed.
     *
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int STATUS_HAL_INTERNAL_FAILURE = CommonResults.STATUS_HAL_INTERNAL_FAILURE;

    /**
     * {@link Status} called when given parameters or environment states are invalid for creating
     * user - HAL or Android user creation is not requested.
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int STATUS_INVALID_REQUEST = CommonResults.STATUS_INVALID_REQUEST;

    /**
     * Gets the user creation result status.
     *
     * @return either {@link UserCreationResult#STATUS_SUCCESSFUL},
     *         {@link UserCreationResult#STATUS_ANDROID_FAILURE},
     *         {@link UserCreationResult#STATUS_HAL_FAILURE},
     *         {@link UserCreationResult#STATUS_HAL_INTERNAL_FAILURE}, or
     *         {@link UserCreationResult#STATUS_INVALID_REQUEST}.
     */
    private final @Status int mStatus;

    // TODO(b/214443810): codegen generates call to writeInteger() / readInteger(), we need to
    // manually change to writeInt() / readInt()
    /**
     * Gets the failure status returned by {@link UserManager} when the {@link #getStatus() status}
     * is {@link #STATUS_ANDROID_FAILURE}.
     *
     * @return {@code USER_OPERATION_ERROR_} constants defined by {@link UserManager}, or
     * {@code null} when the {@link #getStatus() status} is not {@link #STATUS_ANDROID_FAILURE}.
     */
    @Nullable
    private final Integer mAndroidFailureStatus;

    /**
     * Gets the created user.
     */
    @Nullable
    private final UserHandle mUser;

    /**
     * Gets the error message sent by HAL, if any.
     */
    @Nullable
    private final String mErrorMessage;

    /**
     * Gets the internal error message , if any.
     *
     * @hide
     */
    @Nullable
    private final String mInternalErrorMessage;

    @Override
    @AddedInOrBefore(majorVersion = 33)
    public boolean isSuccess() {
        return mStatus == STATUS_SUCCESSFUL;
    }

    /** @hide */
    public UserCreationResult(@Status int status) {
        this(status, /* user= */ null);
    }

    /** @hide */
    public UserCreationResult(@Status int status, UserHandle user) {
        this(status, /* androidFailureStatus= */ null, user, /* errorMessage= */ null,
                /* internalErrorMessage= */ null);
    }

    // NOTE: codegen generates this method, but without @ExcludeFromCodeCoverageGeneratedReport
    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    @AddedInOrBefore(majorVersion = 33)
    public int describeContents() {
        return 0;
    }

    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/services/Car/car-lib/src/android/car/user/UserCreationResult.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    /** @hide */
    @IntDef(prefix = "STATUS_", value = {
        STATUS_SUCCESSFUL,
        STATUS_ANDROID_FAILURE,
        STATUS_HAL_FAILURE,
        STATUS_HAL_INTERNAL_FAILURE,
        STATUS_INVALID_REQUEST
    })
    @Retention(RetentionPolicy.SOURCE)
    @DataClass.Generated.Member
    public @interface Status {}

    /** @hide */
    @DataClass.Generated.Member
    @AddedInOrBefore(majorVersion = 33)
    public static String statusToString(@Status int value) {
        switch (value) {
            case STATUS_SUCCESSFUL:
                    return "STATUS_SUCCESSFUL";
            case STATUS_ANDROID_FAILURE:
                    return "STATUS_ANDROID_FAILURE";
            case STATUS_HAL_FAILURE:
                    return "STATUS_HAL_FAILURE";
            case STATUS_HAL_INTERNAL_FAILURE:
                    return "STATUS_HAL_INTERNAL_FAILURE";
            case STATUS_INVALID_REQUEST:
                    return "STATUS_INVALID_REQUEST";
            default: return Integer.toHexString(value);
        }
    }

    /**
     * Creates a new UserCreationResult.
     *
     * @param status
     *   Gets the user creation result status.
     *
     *   @return either {@link UserCreationResult#STATUS_SUCCESSFUL},
     *           {@link UserCreationResult#STATUS_ANDROID_FAILURE},
     *           {@link UserCreationResult#STATUS_HAL_FAILURE},
     *           {@link UserCreationResult#STATUS_HAL_INTERNAL_FAILURE}, or
     *           {@link UserCreationResult#STATUS_INVALID_REQUEST}.
     * @param androidFailureStatus
     *   Gets the failure status returned by {@link UserManager} when the {@link #getStatus() status}
     *   is {@link #STATUS_ANDROID_FAILURE}.
     *
     *   @return {@code USER_OPERATION_ERROR_} constants defined by {@link UserManager}, or
     *   {@code null} when the {@link #getStatus() status} is not {@link #STATUS_ANDROID_FAILURE}.
     * @param user
     *   Gets the created user.
     * @param errorMessage
     *   Gets the error message sent by HAL, if any.
     * @param internalErrorMessage
     *   Gets the internal error message, if any.
     * @hide
     */
    @DataClass.Generated.Member
    public UserCreationResult(
            @Status int status,
            @Nullable Integer androidFailureStatus,
            @Nullable UserHandle user,
            @Nullable String errorMessage,
            @Nullable String internalErrorMessage) {
        this.mStatus = status;

        if (!(mStatus == STATUS_SUCCESSFUL)
                && !(mStatus == STATUS_ANDROID_FAILURE)
                && !(mStatus == STATUS_HAL_FAILURE)
                && !(mStatus == STATUS_HAL_INTERNAL_FAILURE)
                && !(mStatus == STATUS_INVALID_REQUEST)) {
            throw new java.lang.IllegalArgumentException(
                    "status was " + mStatus + " but must be one of: "
                            + "STATUS_SUCCESSFUL(" + STATUS_SUCCESSFUL + "), "
                            + "STATUS_ANDROID_FAILURE(" + STATUS_ANDROID_FAILURE + "), "
                            + "STATUS_HAL_FAILURE(" + STATUS_HAL_FAILURE + "), "
                            + "STATUS_HAL_INTERNAL_FAILURE(" + STATUS_HAL_INTERNAL_FAILURE + "), "
                            + "STATUS_INVALID_REQUEST(" + STATUS_INVALID_REQUEST + ")");
        }

        this.mAndroidFailureStatus = androidFailureStatus;
        this.mUser = user;
        this.mErrorMessage = errorMessage;
        this.mInternalErrorMessage = internalErrorMessage;

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * Gets the user creation result status.
     *
     * @return either {@link UserCreationResult#STATUS_SUCCESSFUL},
     *         {@link UserCreationResult#STATUS_ANDROID_FAILURE},
     *         {@link UserCreationResult#STATUS_HAL_FAILURE},
     *         {@link UserCreationResult#STATUS_HAL_INTERNAL_FAILURE}, or
     *         {@link UserCreationResult#STATUS_INVALID_REQUEST}.
     */
    @DataClass.Generated.Member
    @AddedInOrBefore(majorVersion = 33)
    public @Status int getStatus() {
        return mStatus;
    }

    /**
     * Gets the failure status returned by {@link UserManager} when the {@link #getStatus() status}
     * is {@link #STATUS_ANDROID_FAILURE}.
     *
     * @return {@code USER_OPERATION_ERROR_} constants defined by {@link UserManager}, or
     * {@code null} when the {@link #getStatus() status} is not {@link #STATUS_ANDROID_FAILURE}.
     */
    @DataClass.Generated.Member
    @AddedInOrBefore(majorVersion = 33)
    public @Nullable Integer getAndroidFailureStatus() {
        return mAndroidFailureStatus;
    }

    /**
     * Gets the created user.
     */
    @DataClass.Generated.Member
    @AddedInOrBefore(majorVersion = 33)
    public @Nullable UserHandle getUser() {
        return mUser;
    }

    /**
     * Gets the error message sent by HAL, if any.
     */
    @DataClass.Generated.Member
    @AddedInOrBefore(majorVersion = 33)
    public @Nullable String getErrorMessage() {
        return mErrorMessage;
    }

    /**
     * Gets the internal error message, if any.
     *
     * @hide
     */
    @DataClass.Generated.Member
    @AddedInOrBefore(majorVersion = 33)
    public @Nullable String getInternalErrorMessage() {
        return mInternalErrorMessage;
    }

    @Override
    @DataClass.Generated.Member
    @AddedInOrBefore(majorVersion = 33)
    public String toString() {
        // You can override field toString logic by defining methods like:
        // String fieldNameToString() { ... }

        return "UserCreationResult { " +
                "status = " + statusToString(mStatus) + ", " +
                "androidFailureStatus = " + mAndroidFailureStatus + ", " +
                "user = " + mUser + ", " +
                "errorMessage = " + mErrorMessage + ", " +
                "internalErrorMessage = " + mInternalErrorMessage +
        " }";
    }

    @Override
    @DataClass.Generated.Member
    @AddedInOrBefore(majorVersion = 33)
    public void writeToParcel(@android.annotation.NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        byte flg = 0;
        if (mAndroidFailureStatus != null) flg |= 0x2;
        if (mUser != null) flg |= 0x4;
        if (mErrorMessage != null) flg |= 0x8;
        if (mInternalErrorMessage != null) flg |= 0x10;
        dest.writeByte(flg);
        dest.writeInt(mStatus);
        if (mAndroidFailureStatus != null) dest.writeInt(mAndroidFailureStatus);
        if (mUser != null) dest.writeTypedObject(mUser, flags);
        if (mErrorMessage != null) dest.writeString(mErrorMessage);
        if (mInternalErrorMessage != null) dest.writeString(mInternalErrorMessage);
    }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ UserCreationResult(@android.annotation.NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        byte flg = in.readByte();
        int status = in.readInt();
        Integer androidFailureStatus = (flg & 0x2) == 0 ? null : (Integer) in.readInt();
        UserHandle user = (flg & 0x4) == 0 ? null : (UserHandle) in.readTypedObject(UserHandle.CREATOR);
        String errorMessage = (flg & 0x8) == 0 ? null : in.readString();
        String internalErrorMessage = (flg & 0x10) == 0 ? null : in.readString();

        this.mStatus = status;

        if (!(mStatus == STATUS_SUCCESSFUL)
                && !(mStatus == STATUS_ANDROID_FAILURE)
                && !(mStatus == STATUS_HAL_FAILURE)
                && !(mStatus == STATUS_HAL_INTERNAL_FAILURE)
                && !(mStatus == STATUS_INVALID_REQUEST)) {
            throw new java.lang.IllegalArgumentException(
                    "status was " + mStatus + " but must be one of: "
                            + "STATUS_SUCCESSFUL(" + STATUS_SUCCESSFUL + "), "
                            + "STATUS_ANDROID_FAILURE(" + STATUS_ANDROID_FAILURE + "), "
                            + "STATUS_HAL_FAILURE(" + STATUS_HAL_FAILURE + "), "
                            + "STATUS_HAL_INTERNAL_FAILURE(" + STATUS_HAL_INTERNAL_FAILURE + "), "
                            + "STATUS_INVALID_REQUEST(" + STATUS_INVALID_REQUEST + ")");
        }

        this.mAndroidFailureStatus = androidFailureStatus;
        this.mUser = user;
        this.mErrorMessage = errorMessage;
        this.mInternalErrorMessage = internalErrorMessage;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    @AddedInOrBefore(majorVersion = 33)
    public static final @android.annotation.NonNull Parcelable.Creator<UserCreationResult> CREATOR
            = new Parcelable.Creator<UserCreationResult>() {
        @Override
        public UserCreationResult[] newArray(int size) {
            return new UserCreationResult[size];
        }

        @Override
        public UserCreationResult createFromParcel(@android.annotation.NonNull android.os.Parcel in) {
            return new UserCreationResult(in);
        }
    };

    @DataClass.Generated(
            time = 1642117564775L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/services/Car/car-lib/src/android/car/user/UserCreationResult.java",
            inputSignatures = "public static final  int STATUS_SUCCESSFUL\npublic static final  int STATUS_ANDROID_FAILURE\npublic static final  int STATUS_HAL_FAILURE\npublic static final  int STATUS_HAL_INTERNAL_FAILURE\npublic static final  int STATUS_INVALID_REQUEST\nprivate final @android.car.user.UserCreationResult.Status int mStatus\nprivate final @android.annotation.Nullable java.lang.Integer mAndroidFailureStatus\nprivate final @android.annotation.Nullable android.os.UserHandle mUser\nprivate final @android.annotation.Nullable java.lang.String mErrorMessage\nprivate final @android.annotation.Nullable java.lang.String mInternalErrorMessage\npublic @java.lang.Override boolean isSuccess()\npublic @java.lang.Override @com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport int describeContents()\nclass UserCreationResult extends java.lang.Object implements [android.os.Parcelable, android.car.user.OperationResult]\n@com.android.car.internal.util.DataClass(genToString=true, genHiddenConstructor=true, genHiddenConstDefs=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
