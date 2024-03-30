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
package android.car;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.car.annotation.ApiRequirements;
import android.car.annotation.ApiRequirements.CarVersion;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Represents the API version of the standard Android SDK.
 */
@ApiRequirements(minCarVersion = CarVersion.TIRAMISU_1,
        minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
public final class PlatformVersion extends ApiVersion<PlatformVersion> implements Parcelable {

    /**
     * Contains pre-defined versions matching Car releases.
     */
    @ApiRequirements(minCarVersion = CarVersion.TIRAMISU_1,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public static class VERSION_CODES {

        /**
         * Helper object for main version of Android 13.
         */
        @ApiRequirements(minCarVersion = CarVersion.TIRAMISU_1,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        @NonNull
        public static final PlatformVersion TIRAMISU_0 =
                new PlatformVersion("TIRAMISU_0", Build.VERSION_CODES.TIRAMISU, 0);

        /**
         * Helper object for first minor upgrade of Android 13.
         */
        @ApiRequirements(minCarVersion = CarVersion.TIRAMISU_1,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        @NonNull
        public static final PlatformVersion TIRAMISU_1 =
                new PlatformVersion("TIRAMISU_1", Build.VERSION_CODES.TIRAMISU, 1);

        /**
         * Helper object for second minor upgrade of Android 13.
         */
        @ApiRequirements(minCarVersion = CarVersion.TIRAMISU_2,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        @NonNull
        public static final PlatformVersion TIRAMISU_2 =
                new PlatformVersion("TIRAMISU_2", Build.VERSION_CODES.TIRAMISU, 2);

        /**
         * Helper object for third minor upgrade of Android 13.
         *
         * @hide
         */
        @ApiRequirements(minCarVersion = CarVersion.TIRAMISU_3,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        @SystemApi
        @NonNull
        public static final PlatformVersion TIRAMISU_3 =
                new PlatformVersion("TIRAMISU_3", Build.VERSION_CODES.TIRAMISU, 3);

        private VERSION_CODES() {
            throw new UnsupportedOperationException("Only provide constants");
        }
    }

    /**
     * Creates a named instance with the given major and minor versions.
     */
    // TODO(b/243429779): should not need @ApiRequirements as it's package-protected
    @ApiRequirements(minCarVersion = CarVersion.TIRAMISU_1,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    static PlatformVersion newInstance(String versionName, int majorVersion, int minorVersion) {
        return new PlatformVersion(versionName, majorVersion, minorVersion);
    }

    /**
     * Creates a new instance with the given major and minor versions.
     */
    @ApiRequirements(minCarVersion = CarVersion.TIRAMISU_1,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    @NonNull
    public static PlatformVersion forMajorAndMinorVersions(int majorVersion, int minorVersion) {
        return new PlatformVersion(majorVersion, minorVersion);
    }

    /**
     * Creates a new instance for a major version (i.e., the minor version will be {@code 0}.
     */
    @ApiRequirements(minCarVersion = CarVersion.TIRAMISU_1,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    @NonNull
    public static PlatformVersion forMajorVersion(int majorVersion) {
        return new PlatformVersion(majorVersion, /* minorVersion= */ 0);
    }

    private PlatformVersion(String name, int majorVersion, int minorVersion) {
        super(name, majorVersion, minorVersion);
    }

    private PlatformVersion(int majorVersion, int minorVersion) {
        super(majorVersion, minorVersion);
    }

    @ApiRequirements(minCarVersion = CarVersion.TIRAMISU_1,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    @Override
    public int describeContents() {
        return 0;
    }

    @ApiRequirements(minCarVersion = CarVersion.TIRAMISU_1,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        writeToParcel(dest);
    }

    @ApiRequirements(minCarVersion = CarVersion.TIRAMISU_1,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    @NonNull
    public static final Parcelable.Creator<PlatformVersion> CREATOR =
            new Parcelable.Creator<PlatformVersion>() {

        @Override
        public PlatformVersion createFromParcel(Parcel source) {
            return ApiVersion.readFromParcel(source,
                    (name, major, minor) -> new PlatformVersion(name, major, minor));
        }

        @Override
        public PlatformVersion[] newArray(int size) {
            return new PlatformVersion[size];
        }
    };
}
