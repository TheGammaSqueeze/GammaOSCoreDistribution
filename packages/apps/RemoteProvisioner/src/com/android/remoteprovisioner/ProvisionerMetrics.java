/**
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

package com.android.remoteprovisioner;

import android.content.Context;
import android.hardware.security.keymint.SecurityLevel;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Log;

import java.time.Duration;

/**
 * Contains the metrics values that are recorded for every attempt to remotely provision keys.
 * This class will automatically push the atoms on close, and is intended to be used with a
 * try-with-resources block to ensure metrics are automatically logged on completion of an attempt.
 */
public final class ProvisionerMetrics implements AutoCloseable {
    // The state of remote provisioning enablement
    public enum Enablement {
        UNKNOWN,
        ENABLED_WITH_FALLBACK,
        ENABLED_RKP_ONLY,
        DISABLED
    }

    public enum Status {
        UNKNOWN,
        KEYS_SUCCESSFULLY_PROVISIONED,
        NO_PROVISIONING_NEEDED,
        PROVISIONING_DISABLED,
        INTERNAL_ERROR,
        NO_NETWORK_CONNECTIVITY,
        OUT_OF_ERROR_BUDGET,
        INTERRUPTED,
        GENERATE_KEYPAIR_FAILED,
        GENERATE_CSR_FAILED,
        GET_POOL_STATUS_FAILED,
        INSERT_CHAIN_INTO_POOL_FAILED,
        FETCH_GEEK_TIMED_OUT,
        FETCH_GEEK_IO_EXCEPTION,
        FETCH_GEEK_HTTP_ERROR,
        SIGN_CERTS_TIMED_OUT,
        SIGN_CERTS_IO_EXCEPTION,
        SIGN_CERTS_HTTP_ERROR,
        SIGN_CERTS_DEVICE_NOT_REGISTERED
    }

    /**
     * Restartable stopwatch class that can be used to measure multiple start->stop time
     * intervals. All measured time intervals are summed and returned by getElapsedMillis.
     */
    public static class StopWatch implements AutoCloseable {
        private long mStartTime = 0;
        private long mElapsedTime = 0;

        /** Start or resume a timer. */
        public void start() {
            if (isRunning()) {
                Log.w(TAG, "Starting a timer that's already been running for "
                        + getElapsedMillis() + "ms");
            } else {
                mStartTime = SystemClock.elapsedRealtime();
            }
        }

        /** Stop recording time. */
        public void stop() {
            if (!isRunning()) {
                Log.w(TAG, "Attempting to stop a timer that hasn't been started.");
            } else {
                mElapsedTime += SystemClock.elapsedRealtime() - mStartTime;
                mStartTime = 0;
            }
        }

        /** Stops the timer if it's running. */
        @Override
        public void close() {
            if (isRunning()) {
                stop();
            }
        }

        /** Get how long the timer has been recording. */
        public int getElapsedMillis() {
            if (isRunning()) {
                return (int) (mElapsedTime + SystemClock.elapsedRealtime() - mStartTime);
            } else {
                return (int) mElapsedTime;
            }
        }

        /** Is the timer currently recording time? */
        public boolean isRunning() {
            return mStartTime != 0;
        }
    }

    private static final String TAG = Provisioner.TAG;

    private final Context mContext;
    private final int mCause;
    private final StopWatch mServerWaitTimer = new StopWatch();
    private final StopWatch mBinderWaitTimer = new StopWatch();
    private final StopWatch mLockWaitTimer = new StopWatch();
    private final StopWatch mTotalTimer = new StopWatch();
    private final String mRemotelyProvisionedComponent;
    private Enablement mEnablement;
    private boolean mIsKeyPoolEmpty = false;
    private Status mStatus = Status.UNKNOWN;
    private int mHttpStatusError = 0;

    private ProvisionerMetrics(Context context, int cause,
            String remotelyProvisionedComponent, Enablement enablement) {
        mContext = context;
        mCause = cause;
        mRemotelyProvisionedComponent = remotelyProvisionedComponent;
        mEnablement = enablement;
        mTotalTimer.start();
    }

    /** Start collecting metrics for scheduled provisioning. */
    public static ProvisionerMetrics createScheduledAttemptMetrics(Context context) {
        // Scheduled jobs (PeriodicProvisioner) intermix a lot of operations for multiple
        // components, which makes it difficult to tease apart what is happening for which
        // remotely provisioned component. Thus, on these calls, the component and
        // component-specific enablement are not logged.
        return new ProvisionerMetrics(
                context,
                RemoteProvisionerStatsLog.REMOTE_KEY_PROVISIONING_ATTEMPT__CAUSE__SCHEDULED,
                "",
                Enablement.UNKNOWN);
    }

    /** Start collecting metrics when an attestation key has been consumed from the pool. */
    public static ProvisionerMetrics createKeyConsumedAttemptMetrics(Context context,
            int securityLevel) {
        return new ProvisionerMetrics(
                context,
                RemoteProvisionerStatsLog.REMOTE_KEY_PROVISIONING_ATTEMPT__CAUSE__KEY_CONSUMED,
                getRemotelyProvisionedComponentName(securityLevel),
                getEnablementForSecurityLevel(securityLevel));
    }

    /** Start collecting metrics when the spare attestation key pool is empty. */
    public static ProvisionerMetrics createOutOfKeysAttemptMetrics(Context context,
            int securityLevel) {
        return new ProvisionerMetrics(
                context,
                RemoteProvisionerStatsLog.REMOTE_KEY_PROVISIONING_ATTEMPT__CAUSE__OUT_OF_KEYS,
                getRemotelyProvisionedComponentName(securityLevel),
                getEnablementForSecurityLevel(securityLevel));
    }

    /** Record the state of RKP configuration. */
    public void setEnablement(Enablement enablement) {
        mEnablement = enablement;
    }

    /** Set to true if the provisioning encountered an empty key pool. */
    public void setIsKeyPoolEmpty(boolean isEmpty) {
        mIsKeyPoolEmpty = isEmpty;
    }

    /** Set the status for this provisioning attempt. */
    public void setStatus(Status status) {
        mStatus = status;
    }

    /** Set the last HTTP status encountered. */
    public void setHttpStatusError(int httpStatusError) {
        mHttpStatusError = httpStatusError;
    }

    /**
     * Starts the server wait timer, returning a reference to an object to be closed when the
     * wait is over.
     */
    public StopWatch startServerWait() {
        mServerWaitTimer.start();
        return mServerWaitTimer;
    }

    /**
     * Starts the binder wait timer, returning a reference to an object to be closed when the
     * wait is over.
     */
    public StopWatch startBinderWait() {
        mBinderWaitTimer.start();
        return mBinderWaitTimer;
    }

    /**
     * Starts the lock wait timer, returning a reference to an object to be closed when the
     * wait is over.
     */
    public StopWatch startLockWait() {
        mLockWaitTimer.start();
        return mLockWaitTimer;
    }

    /** Record the atoms for this metrics object. */
    @Override
    public void close() {
        mTotalTimer.stop();

        int transportType = getTransportTypeForActiveNetwork();
        RemoteProvisionerStatsLog.write(RemoteProvisionerStatsLog.REMOTE_KEY_PROVISIONING_ATTEMPT,
                mCause, mRemotelyProvisionedComponent, getUpTimeBucket(), getIntEnablement(),
                mIsKeyPoolEmpty, getIntStatus());
        RemoteProvisionerStatsLog.write(
                RemoteProvisionerStatsLog.REMOTE_KEY_PROVISIONING_NETWORK_INFO,
                transportType, getIntStatus(), mHttpStatusError);
        RemoteProvisionerStatsLog.write(RemoteProvisionerStatsLog.REMOTE_KEY_PROVISIONING_TIMING,
                mServerWaitTimer.getElapsedMillis(), mBinderWaitTimer.getElapsedMillis(),
                mLockWaitTimer.getElapsedMillis(),
                mTotalTimer.getElapsedMillis(), transportType, mRemotelyProvisionedComponent);
    }

    // TODO: Fix this in U when the provisioner uses the remotely provisioned component names.
    private static String getRemotelyProvisionedComponentName(int securityLevel) {
        switch (securityLevel) {
            case SecurityLevel.SOFTWARE:
                return "SOFTWARE_KEYMINT";
            case SecurityLevel.TRUSTED_ENVIRONMENT:
                return "TEE_KEYMINT";
            case SecurityLevel.STRONGBOX:
                return "STRONGBOX";
            default:
                return "UNKNOWN";
        }
    }

    private static Enablement getEnablementForSecurityLevel(int securityLevel) {
        switch (securityLevel) {
            case SecurityLevel.SOFTWARE:
                return Enablement.ENABLED_WITH_FALLBACK;
            case SecurityLevel.TRUSTED_ENVIRONMENT:
                return readRkpOnlyProperty("remote_provisioning.tee.rkp_only");
            case SecurityLevel.STRONGBOX:
                return readRkpOnlyProperty("remote_provisioning.strongbox.rkp_only");
            default:
                return Enablement.UNKNOWN;
        }
    }

    private static Enablement readRkpOnlyProperty(String property) {
        if (SystemProperties.getBoolean(property, false)) {
            return Enablement.ENABLED_RKP_ONLY;
        }
        return Enablement.ENABLED_WITH_FALLBACK;
    }

    private int getTransportTypeForActiveNetwork() {
        ConnectivityManager cm = mContext.getSystemService(ConnectivityManager.class);
        if (cm == null) {
            Log.w(TAG, "Unable to get ConnectivityManager instance");
            return RemoteProvisionerStatsLog
                    .REMOTE_KEY_PROVISIONING_NETWORK_INFO__TRANSPORT_TYPE__TT_UNKNOWN;
        }

        NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());
        if (capabilities == null) {
            return RemoteProvisionerStatsLog
                    .REMOTE_KEY_PROVISIONING_NETWORK_INFO__TRANSPORT_TYPE__TT_UNKNOWN;
        }

        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return RemoteProvisionerStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__TRANSPORT_TYPE__TT_WIFI_CELLULAR_VPN;
            }
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return RemoteProvisionerStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__TRANSPORT_TYPE__TT_CELLULAR_VPN;
            }
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return RemoteProvisionerStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__TRANSPORT_TYPE__TT_WIFI_VPN;
            }
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
                return RemoteProvisionerStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__TRANSPORT_TYPE__TT_BLUETOOTH_VPN;
            }
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                return RemoteProvisionerStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__TRANSPORT_TYPE__TT_ETHERNET_VPN;
            }
            return RemoteProvisionerStatsLog
                    .REMOTE_KEY_PROVISIONING_NETWORK_INFO__TRANSPORT_TYPE__TT_UNKNOWN;
        }

        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return RemoteProvisionerStatsLog
                    .REMOTE_KEY_PROVISIONING_NETWORK_INFO__TRANSPORT_TYPE__TT_CELLULAR;
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return RemoteProvisionerStatsLog
                    .REMOTE_KEY_PROVISIONING_NETWORK_INFO__TRANSPORT_TYPE__TT_WIFI;
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
            return RemoteProvisionerStatsLog
                    .REMOTE_KEY_PROVISIONING_NETWORK_INFO__TRANSPORT_TYPE__TT_BLUETOOTH;
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return RemoteProvisionerStatsLog
                    .REMOTE_KEY_PROVISIONING_NETWORK_INFO__TRANSPORT_TYPE__TT_ETHERNET;
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE)) {
            return RemoteProvisionerStatsLog
                    .REMOTE_KEY_PROVISIONING_NETWORK_INFO__TRANSPORT_TYPE__TT_WIFI_AWARE;
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_LOWPAN)) {
            return RemoteProvisionerStatsLog
                    .REMOTE_KEY_PROVISIONING_NETWORK_INFO__TRANSPORT_TYPE__TT_LOWPAN;
        }

        return RemoteProvisionerStatsLog
                .REMOTE_KEY_PROVISIONING_NETWORK_INFO__TRANSPORT_TYPE__TT_UNKNOWN;
    }

    private int getUpTimeBucket() {
        final long uptimeMillis = SystemClock.uptimeMillis();
        if (uptimeMillis < Duration.ofMinutes(5).toMillis()) {
            return RemoteProvisionerStatsLog
                    .REMOTE_KEY_PROVISIONING_ATTEMPT__UPTIME__LESS_THAN_5_MINUTES;
        } else if (uptimeMillis < Duration.ofMinutes(60).toMillis()) {
            return RemoteProvisionerStatsLog
                    .REMOTE_KEY_PROVISIONING_ATTEMPT__UPTIME__BETWEEN_5_AND_60_MINUTES;
        } else {
            return RemoteProvisionerStatsLog
                    .REMOTE_KEY_PROVISIONING_ATTEMPT__UPTIME__MORE_THAN_60_MINUTES;
        }
    }

    private int getIntStatus() {
        switch (mStatus) {
            // A whole bunch of generated types here just don't fit in our line length limit.
            // CHECKSTYLE:OFF Generated code
            case UNKNOWN:
                return RemoteProvisionerStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__REMOTE_KEY_PROVISIONING_STATUS_UNKNOWN;
            case KEYS_SUCCESSFULLY_PROVISIONED:
                return RemoteProvisionerStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__KEYS_SUCCESSFULLY_PROVISIONED;
            case NO_PROVISIONING_NEEDED:
                return RemoteProvisionerStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__NO_PROVISIONING_NEEDED;
            case PROVISIONING_DISABLED:
                return RemoteProvisionerStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__PROVISIONING_DISABLED;
            case INTERNAL_ERROR:
                return RemoteProvisionerStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__INTERNAL_ERROR;
            case NO_NETWORK_CONNECTIVITY:
                return RemoteProvisionerStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__NO_NETWORK_CONNECTIVITY;
            case OUT_OF_ERROR_BUDGET:
                return RemoteProvisionerStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__OUT_OF_ERROR_BUDGET;
            case INTERRUPTED:
                return RemoteProvisionerStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__INTERRUPTED;
            case GENERATE_KEYPAIR_FAILED:
                return RemoteProvisionerStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__GENERATE_KEYPAIR_FAILED;
            case GENERATE_CSR_FAILED:
                return RemoteProvisionerStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__GENERATE_CSR_FAILED;
            case GET_POOL_STATUS_FAILED:
                return RemoteProvisionerStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__GET_POOL_STATUS_FAILED;
            case INSERT_CHAIN_INTO_POOL_FAILED:
                return RemoteProvisionerStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__INSERT_CHAIN_INTO_POOL_FAILED;
            case FETCH_GEEK_TIMED_OUT:
                return RemoteProvisionerStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__FETCH_GEEK_TIMED_OUT;
            case FETCH_GEEK_IO_EXCEPTION:
                return RemoteProvisionerStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__FETCH_GEEK_IO_EXCEPTION;
            case FETCH_GEEK_HTTP_ERROR:
                return RemoteProvisionerStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__FETCH_GEEK_HTTP_ERROR;
            case SIGN_CERTS_TIMED_OUT:
                return RemoteProvisionerStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__SIGN_CERTS_TIMED_OUT;
            case SIGN_CERTS_IO_EXCEPTION:
                return RemoteProvisionerStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__SIGN_CERTS_IO_EXCEPTION;
            case SIGN_CERTS_HTTP_ERROR:
                return RemoteProvisionerStatsLog
                        .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__SIGN_CERTS_HTTP_ERROR;
            case SIGN_CERTS_DEVICE_NOT_REGISTERED:
                return RemoteProvisionerStatsLog
                  .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__SIGN_CERTS_DEVICE_NOT_REGISTERED;
        }
        return RemoteProvisionerStatsLog
                .REMOTE_KEY_PROVISIONING_NETWORK_INFO__STATUS__REMOTE_KEY_PROVISIONING_STATUS_UNKNOWN;
        // CHECKSTYLE:ON Generated code
    }

    private int getIntEnablement() {
        switch (mEnablement) {
            case UNKNOWN:
                return RemoteProvisionerStatsLog
                        .REMOTE_KEY_PROVISIONING_ATTEMPT__ENABLEMENT__ENABLEMENT_UNKNOWN;
            case ENABLED_WITH_FALLBACK:
                return RemoteProvisionerStatsLog
                        .REMOTE_KEY_PROVISIONING_ATTEMPT__ENABLEMENT__ENABLED_WITH_FALLBACK;
            case ENABLED_RKP_ONLY:
                return RemoteProvisionerStatsLog
                        .REMOTE_KEY_PROVISIONING_ATTEMPT__ENABLEMENT__ENABLED_RKP_ONLY;
            case DISABLED:
                return RemoteProvisionerStatsLog
                        .REMOTE_KEY_PROVISIONING_ATTEMPT__ENABLEMENT__DISABLED;
        }
        return RemoteProvisionerStatsLog
                .REMOTE_KEY_PROVISIONING_ATTEMPT__ENABLEMENT__ENABLEMENT_UNKNOWN;
    }
}
