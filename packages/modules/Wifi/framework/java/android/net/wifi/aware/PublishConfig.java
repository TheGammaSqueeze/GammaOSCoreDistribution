/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.net.wifi.aware;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.wifi.WifiScanner;
import android.net.wifi.util.HexEncoding;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.RequiresApi;

import com.android.modules.utils.build.SdkLevel;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Defines the configuration of a Aware publish session. Built using
 * {@link PublishConfig.Builder}. A publish session is created using
 * {@link WifiAwareSession#publish(PublishConfig, DiscoverySessionCallback,
 * android.os.Handler)} or updated using
 * {@link PublishDiscoverySession#updatePublish(PublishConfig)}.
 */
public final class PublishConfig implements Parcelable {
    /** @hide */
    @IntDef({
            PUBLISH_TYPE_UNSOLICITED, PUBLISH_TYPE_SOLICITED })
    @Retention(RetentionPolicy.SOURCE)
    public @interface PublishTypes {
    }

    /**
     * Defines an unsolicited publish session - a publish session where the publisher is
     * advertising itself by broadcasting on-the-air. An unsolicited publish session is paired
     * with an passive subscribe session {@link SubscribeConfig#SUBSCRIBE_TYPE_PASSIVE}.
     * Configuration is done using {@link PublishConfig.Builder#setPublishType(int)}.
     */
    public static final int PUBLISH_TYPE_UNSOLICITED = 0;

    /**
     * Defines a solicited publish session - a publish session which is silent, waiting for a
     * matching active subscribe session - and responding to it in unicast. A
     * solicited publish session is paired with an active subscribe session
     * {@link SubscribeConfig#SUBSCRIBE_TYPE_ACTIVE}. Configuration is done using
     * {@link PublishConfig.Builder#setPublishType(int)}.
     */
    public static final int PUBLISH_TYPE_SOLICITED = 1;

    /** @hide */
    public final byte[] mServiceName;

    /** @hide */
    public final byte[] mServiceSpecificInfo;

    /** @hide */
    public final byte[] mMatchFilter;

    /** @hide */
    public final int mPublishType;

    /** @hide */
    public final int mTtlSec;

    /** @hide */
    public final boolean mEnableTerminateNotification;

    /** @hide */
    public final boolean mEnableRanging;

    private final boolean mEnableInstantMode;

    private final int mBand;

    private final WifiAwareDataPathSecurityConfig mSecurityConfig;

    /** @hide */
    public PublishConfig(byte[] serviceName, byte[] serviceSpecificInfo, byte[] matchFilter,
            int publishType, int ttlSec, boolean enableTerminateNotification,
            boolean enableRanging, boolean enableInstantMode, @WifiScanner.WifiBand int
            band, WifiAwareDataPathSecurityConfig securityConfig) {
        mServiceName = serviceName;
        mServiceSpecificInfo = serviceSpecificInfo;
        mMatchFilter = matchFilter;
        mPublishType = publishType;
        mTtlSec = ttlSec;
        mEnableTerminateNotification = enableTerminateNotification;
        mEnableRanging = enableRanging;
        mEnableInstantMode = enableInstantMode;
        mBand = band;
        mSecurityConfig = securityConfig;
    }

    @Override
    public String toString() {
        return "PublishConfig [mServiceName='" + (mServiceName == null ? "<null>" : String.valueOf(
                HexEncoding.encode(mServiceName))) + ", mServiceName.length=" + (
                mServiceName == null ? 0 : mServiceName.length) + ", mServiceSpecificInfo='" + (
                (mServiceSpecificInfo == null) ? "<null>" : String.valueOf(
                        HexEncoding.encode(mServiceSpecificInfo)))
                + ", mServiceSpecificInfo.length=" + (mServiceSpecificInfo == null ? 0
                : mServiceSpecificInfo.length) + ", mMatchFilter="
                + (new TlvBufferUtils.TlvIterable(0, 1, mMatchFilter)).toString()
                + ", mMatchFilter.length=" + (mMatchFilter == null ? 0 : mMatchFilter.length)
                + ", mPublishType=" + mPublishType + ", mTtlSec=" + mTtlSec
                + ", mEnableTerminateNotification=" + mEnableTerminateNotification
                + ", mEnableRanging=" + mEnableRanging + "]"
                + ", mEnableInstantMode=" + mEnableInstantMode
                + ", mBand=" + mBand
                + ", mSecurityConfig" + mSecurityConfig;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByteArray(mServiceName);
        dest.writeByteArray(mServiceSpecificInfo);
        dest.writeByteArray(mMatchFilter);
        dest.writeInt(mPublishType);
        dest.writeInt(mTtlSec);
        dest.writeInt(mEnableTerminateNotification ? 1 : 0);
        dest.writeInt(mEnableRanging ? 1 : 0);
        dest.writeBoolean(mEnableInstantMode);
        dest.writeInt(mBand);
        dest.writeParcelable(mSecurityConfig, flags);
    }

    public static final @android.annotation.NonNull Creator<PublishConfig> CREATOR = new Creator<PublishConfig>() {
        @Override
        public PublishConfig[] newArray(int size) {
            return new PublishConfig[size];
        }

        @Override
        public PublishConfig createFromParcel(Parcel in) {
            byte[] serviceName = in.createByteArray();
            byte[] ssi = in.createByteArray();
            byte[] matchFilter = in.createByteArray();
            int publishType = in.readInt();
            int ttlSec = in.readInt();
            boolean enableTerminateNotification = in.readInt() != 0;
            boolean enableRanging = in.readInt() != 0;
            boolean enableInstantMode = in.readBoolean();
            int band = in.readInt();
            WifiAwareDataPathSecurityConfig securityConfig = in
                    .readParcelable(WifiAwareDataPathSecurityConfig.class.getClassLoader());

            return new PublishConfig(serviceName, ssi, matchFilter, publishType, ttlSec,
                    enableTerminateNotification, enableRanging, enableInstantMode,
                    band, securityConfig);
        }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof PublishConfig)) {
            return false;
        }

        PublishConfig lhs = (PublishConfig) o;

        return Arrays.equals(mServiceName, lhs.mServiceName) && Arrays.equals(mServiceSpecificInfo,
                lhs.mServiceSpecificInfo) && Arrays.equals(mMatchFilter, lhs.mMatchFilter)
                && mPublishType == lhs.mPublishType
                && mTtlSec == lhs.mTtlSec
                && mEnableTerminateNotification == lhs.mEnableTerminateNotification
                && mEnableRanging == lhs.mEnableRanging
                && mEnableInstantMode == lhs.mEnableInstantMode
                && mBand == lhs.mBand
                && Objects.equals(mSecurityConfig, lhs.mSecurityConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(mServiceName), Arrays.hashCode(mServiceSpecificInfo),
                Arrays.hashCode(mMatchFilter), mPublishType, mTtlSec, mEnableTerminateNotification,
                mEnableRanging, mEnableInstantMode, mBand, mSecurityConfig);
    }

    /**
     * Verifies that the contents of the PublishConfig are valid. Otherwise
     * throws an IllegalArgumentException.
     *
     * @hide
     */
    public void assertValid(Characteristics characteristics, boolean rttSupported)
            throws IllegalArgumentException {
        WifiAwareUtils.validateServiceName(mServiceName);

        if (!TlvBufferUtils.isValid(mMatchFilter, 0, 1)) {
            throw new IllegalArgumentException(
                    "Invalid txFilter configuration - LV fields do not match up to length");
        }
        if (mPublishType < PUBLISH_TYPE_UNSOLICITED || mPublishType > PUBLISH_TYPE_SOLICITED) {
            throw new IllegalArgumentException("Invalid publishType - " + mPublishType);
        }
        if (mTtlSec < 0) {
            throw new IllegalArgumentException("Invalid ttlSec - must be non-negative");
        }
        if (mSecurityConfig != null && !mSecurityConfig.isValid()) {
            throw new IllegalArgumentException("WifiAwareDataPathSecurityConfig is invalid");
        }

        if (characteristics != null) {
            int maxServiceNameLength = characteristics.getMaxServiceNameLength();
            if (maxServiceNameLength != 0 && mServiceName.length > maxServiceNameLength) {
                throw new IllegalArgumentException(
                        "Service name longer than supported by device characteristics");
            }
            int maxServiceSpecificInfoLength = characteristics.getMaxServiceSpecificInfoLength();
            if (maxServiceSpecificInfoLength != 0 && mServiceSpecificInfo != null
                    && mServiceSpecificInfo.length > maxServiceSpecificInfoLength) {
                throw new IllegalArgumentException(
                        "Service specific info longer than supported by device characteristics");
            }
            int maxMatchFilterLength = characteristics.getMaxMatchFilterLength();
            if (maxMatchFilterLength != 0 && mMatchFilter != null
                    && mMatchFilter.length > maxMatchFilterLength) {
                throw new IllegalArgumentException(
                        "Match filter longer than supported by device characteristics");
            }
            if (mEnableInstantMode) {
                if (SdkLevel.isAtLeastT()
                        && characteristics.isInstantCommunicationModeSupported()) {
                    // Valid to use instant communication mode
                } else {
                    throw new IllegalArgumentException("instant mode is not supported");
                }
            }
            if (mSecurityConfig != null && (characteristics.getSupportedCipherSuites()
                    & mSecurityConfig.getCipherSuite()) == 0) {
                throw new IllegalArgumentException("Unsupported cipher suite");
            }
        }

        if (!rttSupported && mEnableRanging) {
            throw new IllegalArgumentException("Ranging is not supported");
        }
    }

    /**
     * Check if instant communication mode is enabled for this publish session.
     * @see Builder#setInstantCommunicationModeEnabled(boolean, int)
     * @return true for enabled, false otherwise.
     */
    public boolean isInstantCommunicationModeEnabled() {
        return mEnableInstantMode;
    }

    /**
     * Get the Wi-FI band for instant communication mode for this publish session
     * @see Builder#setInstantCommunicationModeEnabled(boolean, int)
     * @return The Wi-Fi band, one of the {@link WifiScanner#WIFI_BAND_24_GHZ}
     * or {@link WifiScanner#WIFI_BAND_5_GHZ}. If instant communication mode is not enabled will
     * return {@link WifiScanner#WIFI_BAND_24_GHZ} as default.
     */
    public @WifiScanner.WifiBand int getInstantCommunicationBand() {
        return mBand;
    }

    /**
     * Get the data-path security config for this publish session
     * @see Builder#setDataPathSecurityConfig(WifiAwareDataPathSecurityConfig)
     * @return A {@link WifiAwareDataPathSecurityConfig} specified in this config.
     */
    @Nullable
    public WifiAwareDataPathSecurityConfig getSecurityConfig() {
        return mSecurityConfig;
    }

    /**
     * Builder used to build {@link PublishConfig} objects.
     */
    public static final class Builder {
        private byte[] mServiceName;
        private byte[] mServiceSpecificInfo;
        private byte[] mMatchFilter;
        private int mPublishType = PUBLISH_TYPE_UNSOLICITED;
        private int mTtlSec = 0;
        private boolean mEnableTerminateNotification = true;
        private boolean mEnableRanging = false;
        private boolean mEnableInstantMode = false;
        private int mBand = WifiScanner.WIFI_BAND_24_GHZ;
        private WifiAwareDataPathSecurityConfig mSecurityConfig = null;

        /**
         * Specify the service name of the publish session. The actual on-air
         * value is a 6 byte hashed representation of this string.
         * <p>
         * The Service Name is a UTF-8 encoded string from 1 to 255 bytes in length.
         * The only acceptable single-byte UTF-8 symbols for a Service Name are alphanumeric
         * values (A-Z, a-z, 0-9), the hyphen ('-'), the period ('.') and the underscore ('_'). All
         * valid multi-byte UTF-8 characters are acceptable in a Service Name.
         * <p>
         * Note: for compatibility with devices running Android 11 or older, avoid using
         * underscore ('_') symbol as a single-byte UTF-8 service name.
         * <p>
         * Must be called - an empty ServiceName is not valid.
         *
         * @param serviceName The service name for the publish session.
         *
         * @return The builder to facilitate chaining
         *         {@code builder.setXXX(..).setXXX(..)}.
         */
        public Builder setServiceName(@NonNull String serviceName) {
            if (serviceName == null) {
                throw new IllegalArgumentException("Invalid service name - must be non-null");
            }
            mServiceName = serviceName.getBytes(StandardCharsets.UTF_8);
            return this;
        }

        /**
         * Specify service specific information for the publish session. This is
         * a free-form byte array available to the application to send
         * additional information as part of the discovery operation - it
         * will not be used to determine whether a publish/subscribe match
         * occurs.
         * <p>
         *     Optional. Empty by default.
         *
         * @param serviceSpecificInfo A byte-array for the service-specific
         *            information field.
         *
         * @return The builder to facilitate chaining
         *         {@code builder.setXXX(..).setXXX(..)}.
         */
        public Builder setServiceSpecificInfo(@Nullable byte[] serviceSpecificInfo) {
            mServiceSpecificInfo = serviceSpecificInfo;
            return this;
        }

        /**
         * The match filter for a publish session. Used to determine whether a service
         * discovery occurred - in addition to relying on the service name.
         * <p>
         *     Optional. Empty by default.
         *
         * @param matchFilter A list of match filter entries (each of which is an arbitrary byte
         *                    array).
         *
         * @return The builder to facilitate chaining
         *         {@code builder.setXXX(..).setXXX(..)}.
         */
        public Builder setMatchFilter(@Nullable List<byte[]> matchFilter) {
            mMatchFilter = new TlvBufferUtils.TlvConstructor(0, 1).allocateAndPut(
                    matchFilter).getArray();
            return this;
        }

        /**
         * Specify the type of the publish session: solicited (aka active - publish
         * packets are transmitted over-the-air), or unsolicited (aka passive -
         * no publish packets are transmitted, a match is made against an active
         * subscribe session whose packets are transmitted over-the-air).
         *
         * @param publishType Publish session type:
         *            {@link PublishConfig#PUBLISH_TYPE_SOLICITED} or
         *            {@link PublishConfig#PUBLISH_TYPE_UNSOLICITED} (the default).
         *
         * @return The builder to facilitate chaining
         *         {@code builder.setXXX(..).setXXX(..)}.
         */
        public Builder setPublishType(@PublishTypes int publishType) {
            if (publishType < PUBLISH_TYPE_UNSOLICITED || publishType > PUBLISH_TYPE_SOLICITED) {
                throw new IllegalArgumentException("Invalid publishType - " + publishType);
            }
            mPublishType = publishType;
            return this;
        }

        /**
         * Sets the time interval (in seconds) an unsolicited (
         * {@link PublishConfig.Builder#setPublishType(int)}) publish session
         * will be alive - broadcasting a packet. When the TTL is reached
         * an event will be generated for
         * {@link DiscoverySessionCallback#onSessionTerminated()} [unless
         * {@link #setTerminateNotificationEnabled(boolean)} disables the callback].
         * <p>
         *     Optional. 0 by default - indicating the session doesn't terminate on its own.
         *     Session will be terminated when {@link DiscoverySession#close()} is
         *     called.
         *
         * @param ttlSec Lifetime of a publish session in seconds.
         *
         * @return The builder to facilitate chaining
         *         {@code builder.setXXX(..).setXXX(..)}.
         */
        public Builder setTtlSec(int ttlSec) {
            if (ttlSec < 0) {
                throw new IllegalArgumentException("Invalid ttlSec - must be non-negative");
            }
            mTtlSec = ttlSec;
            return this;
        }

        /**
         * Configure whether a publish terminate notification
         * {@link DiscoverySessionCallback#onSessionTerminated()} is reported
         * back to the callback.
         *
         * @param enable If true the terminate callback will be called when the
         *            publish is terminated. Otherwise it will not be called.
         *
         * @return The builder to facilitate chaining
         *         {@code builder.setXXX(..).setXXX(..)}.
         */
        public Builder setTerminateNotificationEnabled(boolean enable) {
            mEnableTerminateNotification = enable;
            return this;
        }

        /**
         * Configure whether the publish discovery session supports ranging and allows peers to
         * measure distance to it. This API is used in conjunction with
         * {@link SubscribeConfig.Builder#setMinDistanceMm(int)} and
         * {@link SubscribeConfig.Builder#setMaxDistanceMm(int)} to specify a minimum and/or
         * maximum distance at which discovery will be triggered.
         * <p>
         * Optional. Disabled by default - i.e. any peer attempt to measure distance to this device
         * will be refused and discovery will proceed without ranging constraints.
         * <p>
         * The device must support Wi-Fi RTT for this feature to be used. Feature support is checked
         * as described in {@link android.net.wifi.rtt}.
         *
         * @param enable If true, ranging is supported on request of the peer.
         *
         * @return The builder to facilitate chaining
         *         {@code builder.setXXX(..).setXXX(..)}.
         */
        public Builder setRangingEnabled(boolean enable) {
            mEnableRanging = enable;
            return this;
        }

        /**
         * Configure whether to enable and use instant communication for this publish session.
         * Instant communication will speed up service discovery and any data-path set up as part of
         * this session. Use {@link Characteristics#isInstantCommunicationModeSupported()} to check
         * if the device supports this feature.
         *
         * Note: due to increased power requirements of this mode - it will only remain enabled for
         * 30 seconds from the time the discovery session is started.
         *
         * @param enabled true for enable instant communication mode, default is false.
         * @param band Either {@link WifiScanner#WIFI_BAND_24_GHZ}
         *             or {@link WifiScanner#WIFI_BAND_5_GHZ}. When setting to
         *             {@link WifiScanner#WIFI_BAND_5_GHZ}, device will try to enable instant
         *             communication mode on 5Ghz, but may fall back to 2.4Ghz due to regulatory
         *             requirements.
         * @return the current {@link Builder} builder, enabling chaining of builder methods.
         */
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        public @NonNull Builder setInstantCommunicationModeEnabled(boolean enabled,
                @WifiScanner.WifiBand int band) {
            if (!SdkLevel.isAtLeastT()) {
                throw new UnsupportedOperationException();
            }
            if (band != WifiScanner.WIFI_BAND_24_GHZ && band != WifiScanner.WIFI_BAND_5_GHZ) {
                throw new IllegalArgumentException();
            }
            mBand = band;
            mEnableInstantMode = enabled;
            return this;
        }

        /**
         * Configure security config for the Wi-Fi Aware publish session. The security config set
         * here must be the same as the one used to request Wi-Fi Aware data-path connection using
         * {@link WifiAwareNetworkSpecifier.Builder#setDataPathSecurityConfig(WifiAwareDataPathSecurityConfig)}.
         * This security config will create a security identifier (SCID) which contains a PMKID and
         * transmitted in the publish message. The device which subscribe this session can get this
         * info by {@link ServiceDiscoveryInfo#getScid()}
         * This method is optional - if not called, then no security context identifier will be
         * passed in the publish message, then no security context identifier will be provided in
         * the {@link ServiceDiscoveryInfo} on the subscriber. Security can still be negotiated
         * using out-of-band (OOB) mechanisms.
         *
         * @param securityConfig The (optional) security config to be used to create security
         *                       context Identifier
         * @return the current {@link Builder} builder, enabling chaining of builder methods.
         */
        public @NonNull Builder setDataPathSecurityConfig(
                @NonNull WifiAwareDataPathSecurityConfig securityConfig) {
            if (securityConfig == null) {
                throw new IllegalArgumentException("The WifiAwareDataPathSecurityConfig "
                        + "should be non-null");
            }
            if (!securityConfig.isValid()) {
                throw new IllegalArgumentException("The WifiAwareDataPathSecurityConfig "
                        + "is invalid");
            }
            mSecurityConfig = securityConfig;
            return this;
        }

        /**
         * Build {@link PublishConfig} given the current requests made on the
         * builder.
         */
        public PublishConfig build() {
            return new PublishConfig(mServiceName, mServiceSpecificInfo, mMatchFilter, mPublishType,
                    mTtlSec, mEnableTerminateNotification, mEnableRanging, mEnableInstantMode,
                    mBand, mSecurityConfig);
        }
    }
}
