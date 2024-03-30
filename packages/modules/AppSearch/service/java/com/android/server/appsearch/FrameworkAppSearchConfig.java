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

package com.android.server.appsearch;

import android.annotation.NonNull;
import android.os.Bundle;
import android.provider.DeviceConfig;
import android.provider.DeviceConfig.OnPropertiesChangedListener;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Implementation of {@link AppSearchConfig} using {@link DeviceConfig}.
 *
 * <p>Though the latest flag values can always be retrieved by calling {@link
 * DeviceConfig#getProperty}, we want to cache some of those values. For example, the sampling
 * intervals for logging, they are needed for each api call and it would be a little expensive to
 * call {@link DeviceConfig#getProperty} every time.
 *
 * <p>Listener is registered to DeviceConfig keep the cached value up to date.
 *
 * <p>This class is thread-safe.
 *
 * @hide
 */
public final class FrameworkAppSearchConfig implements AppSearchConfig {
    private static volatile FrameworkAppSearchConfig sConfig;

    /*
     * Keys for ALL the flags stored in DeviceConfig.
     */
    public static final String KEY_MIN_TIME_INTERVAL_BETWEEN_SAMPLES_MILLIS =
            "min_time_interval_between_samples_millis";
    public static final String KEY_SAMPLING_INTERVAL_DEFAULT = "sampling_interval_default";
    public static final String KEY_SAMPLING_INTERVAL_FOR_BATCH_CALL_STATS =
            "sampling_interval_for_batch_call_stats";
    public static final String KEY_SAMPLING_INTERVAL_FOR_PUT_DOCUMENT_STATS =
            "sampling_interval_for_put_document_stats";
    public static final String KEY_SAMPLING_INTERVAL_FOR_INITIALIZE_STATS =
            "sampling_interval_for_initialize_stats";
    public static final String KEY_SAMPLING_INTERVAL_FOR_SEARCH_STATS =
            "sampling_interval_for_search_stats";
    public static final String KEY_SAMPLING_INTERVAL_FOR_GLOBAL_SEARCH_STATS =
            "sampling_interval_for_global_search_stats";
    public static final String KEY_SAMPLING_INTERVAL_FOR_OPTIMIZE_STATS =
            "sampling_interval_for_optimize_stats";
    public static final String KEY_LIMIT_CONFIG_MAX_DOCUMENT_SIZE_BYTES =
            "limit_config_max_document_size_bytes";
    public static final String KEY_LIMIT_CONFIG_MAX_DOCUMENT_COUNT =
            "limit_config_max_document_docunt";
    public static final String KEY_BYTES_OPTIMIZE_THRESHOLD = "bytes_optimize_threshold";
    public static final String KEY_TIME_OPTIMIZE_THRESHOLD_MILLIS = "time_optimize_threshold";
    public static final String KEY_DOC_COUNT_OPTIMIZE_THRESHOLD = "doc_count_optimize_threshold";

    // Array contains all the corresponding keys for the cached values.
    private static final String[] KEYS_TO_ALL_CACHED_VALUES = {
            KEY_MIN_TIME_INTERVAL_BETWEEN_SAMPLES_MILLIS,
            KEY_SAMPLING_INTERVAL_DEFAULT,
            KEY_SAMPLING_INTERVAL_FOR_BATCH_CALL_STATS,
            KEY_SAMPLING_INTERVAL_FOR_PUT_DOCUMENT_STATS,
            KEY_SAMPLING_INTERVAL_FOR_INITIALIZE_STATS,
            KEY_SAMPLING_INTERVAL_FOR_SEARCH_STATS,
            KEY_SAMPLING_INTERVAL_FOR_GLOBAL_SEARCH_STATS,
            KEY_SAMPLING_INTERVAL_FOR_OPTIMIZE_STATS,
            KEY_LIMIT_CONFIG_MAX_DOCUMENT_SIZE_BYTES,
            KEY_LIMIT_CONFIG_MAX_DOCUMENT_COUNT,
            KEY_BYTES_OPTIMIZE_THRESHOLD,
            KEY_TIME_OPTIMIZE_THRESHOLD_MILLIS,
            KEY_DOC_COUNT_OPTIMIZE_THRESHOLD
    };

    // Lock needed for all the operations in this class.
    private final Object mLock = new Object();

    /**
     * Bundle to hold all the cached flag values corresponding to
     * {@link FrameworkAppSearchConfig#KEYS_TO_ALL_CACHED_VALUES}.
     */
    @GuardedBy("mLock")
    private final Bundle mBundleLocked = new Bundle();


    @GuardedBy("mLock")
    private boolean mIsClosedLocked = false;

    /** Listener to update cached flag values from DeviceConfig. */
    private final OnPropertiesChangedListener mOnDeviceConfigChangedListener =
            properties -> {
                if (!properties.getNamespace().equals(DeviceConfig.NAMESPACE_APPSEARCH)) {
                    return;
                }

                updateCachedValues(properties);
            };

    private FrameworkAppSearchConfig() {
    }

    /**
     * Creates an instance of {@link FrameworkAppSearchConfig}.
     *
     * @param executor used to fetch and cache the flag values from DeviceConfig during creation or
     *                 config change.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    @NonNull
    public static FrameworkAppSearchConfig create(@NonNull Executor executor) {
        Objects.requireNonNull(executor);
        FrameworkAppSearchConfig configManager = new FrameworkAppSearchConfig();
        configManager.initialize(executor);
        return configManager;
    }

    /**
     * Gets an instance of {@link FrameworkAppSearchConfig} to be used.
     *
     * <p>If no instance has been initialized yet, a new one will be created. Otherwise, the
     * existing instance will be returned.
     */
    @NonNull
    public static FrameworkAppSearchConfig getInstance(@NonNull Executor executor) {
        Objects.requireNonNull(executor);
        if (sConfig == null) {
            synchronized (FrameworkAppSearchConfig.class) {
                if (sConfig == null) {
                    sConfig = create(executor);
                }
            }
        }
        return sConfig;
    }

    /**
     * Initializes the {@link FrameworkAppSearchConfig}
     *
     * <p>It fetches the custom properties from DeviceConfig if available.
     *
     * @param executor listener would be run on to handle P/H flag change.
     */
    private void initialize(@NonNull Executor executor) {
        executor.execute(() -> {
            // Attach the callback to get updates on those properties.
            DeviceConfig.addOnPropertiesChangedListener(DeviceConfig.NAMESPACE_APPSEARCH,
                    executor,
                    mOnDeviceConfigChangedListener);

            DeviceConfig.Properties properties = DeviceConfig.getProperties(
                    DeviceConfig.NAMESPACE_APPSEARCH, KEYS_TO_ALL_CACHED_VALUES);
            updateCachedValues(properties);
        });
    }

    // TODO(b/173532925) check this will be called. If we have a singleton instance for this
    //  class, probably we don't need it.
    @Override
    public void close() {
        synchronized (mLock) {
            if (mIsClosedLocked) {
                return;
            }

            DeviceConfig.removeOnPropertiesChangedListener(mOnDeviceConfigChangedListener);
            mIsClosedLocked = true;
        }
    }

    @Override
    public long getCachedMinTimeIntervalBetweenSamplesMillis() {
        synchronized (mLock) {
            throwIfClosedLocked();
            return mBundleLocked.getLong(KEY_MIN_TIME_INTERVAL_BETWEEN_SAMPLES_MILLIS,
                    DEFAULT_MIN_TIME_INTERVAL_BETWEEN_SAMPLES_MILLIS);
        }
    }

    @Override
    public int getCachedSamplingIntervalDefault() {
        synchronized (mLock) {
            throwIfClosedLocked();
            return mBundleLocked.getInt(KEY_SAMPLING_INTERVAL_DEFAULT, DEFAULT_SAMPLING_INTERVAL);
        }
    }

    @Override
    public int getCachedSamplingIntervalForBatchCallStats() {
        synchronized (mLock) {
            throwIfClosedLocked();
            return mBundleLocked.getInt(KEY_SAMPLING_INTERVAL_FOR_BATCH_CALL_STATS,
                    getCachedSamplingIntervalDefault());
        }
    }

    @Override
    public int getCachedSamplingIntervalForPutDocumentStats() {
        synchronized (mLock) {
            throwIfClosedLocked();
            return mBundleLocked.getInt(KEY_SAMPLING_INTERVAL_FOR_PUT_DOCUMENT_STATS,
                    getCachedSamplingIntervalDefault());
        }
    }

    @Override
    public int getCachedSamplingIntervalForInitializeStats() {
        synchronized (mLock) {
            throwIfClosedLocked();
            return mBundleLocked.getInt(KEY_SAMPLING_INTERVAL_FOR_INITIALIZE_STATS,
                    getCachedSamplingIntervalDefault());
        }
    }

    @Override
    public int getCachedSamplingIntervalForSearchStats() {
        synchronized (mLock) {
            throwIfClosedLocked();
            return mBundleLocked.getInt(KEY_SAMPLING_INTERVAL_FOR_SEARCH_STATS,
                    getCachedSamplingIntervalDefault());
        }
    }

    @Override
    public int getCachedSamplingIntervalForGlobalSearchStats() {
        synchronized (mLock) {
            throwIfClosedLocked();
            return mBundleLocked.getInt(KEY_SAMPLING_INTERVAL_FOR_GLOBAL_SEARCH_STATS,
                    getCachedSamplingIntervalDefault());
        }
    }

    @Override
    public int getCachedSamplingIntervalForOptimizeStats() {
        synchronized (mLock) {
            throwIfClosedLocked();
            return mBundleLocked.getInt(KEY_SAMPLING_INTERVAL_FOR_OPTIMIZE_STATS,
                    getCachedSamplingIntervalDefault());
        }
    }

    @Override
    public int getCachedLimitConfigMaxDocumentSizeBytes() {
        synchronized (mLock) {
            throwIfClosedLocked();
            return mBundleLocked.getInt(KEY_LIMIT_CONFIG_MAX_DOCUMENT_SIZE_BYTES,
                    DEFAULT_LIMIT_CONFIG_MAX_DOCUMENT_SIZE_BYTES);
        }
    }

    @Override
    public int getCachedLimitConfigMaxDocumentCount() {
        synchronized (mLock) {
            throwIfClosedLocked();
            return mBundleLocked.getInt(KEY_LIMIT_CONFIG_MAX_DOCUMENT_COUNT,
                    DEFAULT_LIMIT_CONFIG_MAX_DOCUMENT_COUNT);
        }
    }

    @Override
    public int getCachedBytesOptimizeThreshold() {
        synchronized (mLock) {
            throwIfClosedLocked();
            return mBundleLocked.getInt(KEY_BYTES_OPTIMIZE_THRESHOLD,
                    DEFAULT_BYTES_OPTIMIZE_THRESHOLD);
        }
    }

    @Override
    public int getCachedTimeOptimizeThresholdMs() {
        synchronized (mLock) {
            throwIfClosedLocked();
            return mBundleLocked.getInt(KEY_TIME_OPTIMIZE_THRESHOLD_MILLIS,
                    DEFAULT_TIME_OPTIMIZE_THRESHOLD_MILLIS);
        }
    }

    @Override
    public int getCachedDocCountOptimizeThreshold() {
        synchronized (mLock) {
            throwIfClosedLocked();
            return mBundleLocked.getInt(KEY_DOC_COUNT_OPTIMIZE_THRESHOLD,
                    DEFAULT_DOC_COUNT_OPTIMIZE_THRESHOLD);
        }
    }

    @GuardedBy("mLock")
    private void throwIfClosedLocked() {
        if (mIsClosedLocked) {
            throw new IllegalStateException("Trying to use a closed AppSearchConfig instance.");
        }
    }

    private void updateCachedValues(@NonNull DeviceConfig.Properties properties) {
        for (String key : properties.getKeyset()) {
            updateCachedValue(key, properties);
        }
    }

    private void updateCachedValue(@NonNull String key,
            @NonNull DeviceConfig.Properties properties) {
        if (properties.getString(key, /*defaultValue=*/ null) == null) {
            // Key is missing or value is just null. That is not expected if the key is
            // defined in the configuration.
            //
            // We choose NOT to put the default value in the bundle.
            // Instead, we let the getters handle what default value should be returned.
            //
            // Also we keep the old value in the bundle. So getters can still
            // return last valid value.
            return;
        }

        switch (key) {
            case KEY_MIN_TIME_INTERVAL_BETWEEN_SAMPLES_MILLIS:
                synchronized (mLock) {
                    mBundleLocked.putLong(key,
                            properties.getLong(key,
                                    DEFAULT_MIN_TIME_INTERVAL_BETWEEN_SAMPLES_MILLIS));
                }
                break;
            case KEY_SAMPLING_INTERVAL_DEFAULT:
            case KEY_SAMPLING_INTERVAL_FOR_BATCH_CALL_STATS:
            case KEY_SAMPLING_INTERVAL_FOR_PUT_DOCUMENT_STATS:
            case KEY_SAMPLING_INTERVAL_FOR_INITIALIZE_STATS:
            case KEY_SAMPLING_INTERVAL_FOR_SEARCH_STATS:
            case KEY_SAMPLING_INTERVAL_FOR_GLOBAL_SEARCH_STATS:
            case KEY_SAMPLING_INTERVAL_FOR_OPTIMIZE_STATS:
                synchronized (mLock) {
                    mBundleLocked.putInt(key, properties.getInt(key, DEFAULT_SAMPLING_INTERVAL));
                }
                break;
            case KEY_LIMIT_CONFIG_MAX_DOCUMENT_SIZE_BYTES:
                synchronized (mLock) {
                    mBundleLocked.putInt(
                            key,
                            properties.getInt(key, DEFAULT_LIMIT_CONFIG_MAX_DOCUMENT_SIZE_BYTES));
                }
                break;
            case KEY_LIMIT_CONFIG_MAX_DOCUMENT_COUNT:
                synchronized (mLock) {
                    mBundleLocked.putInt(
                            key,
                            properties.getInt(key, DEFAULT_LIMIT_CONFIG_MAX_DOCUMENT_COUNT));
                }
                break;
            case KEY_BYTES_OPTIMIZE_THRESHOLD:
                synchronized (mLock) {
                    mBundleLocked.putInt(key, properties.getInt(key,
                            DEFAULT_BYTES_OPTIMIZE_THRESHOLD));
                }
                break;
            case KEY_TIME_OPTIMIZE_THRESHOLD_MILLIS:
                synchronized (mLock) {
                    mBundleLocked.putInt(key, properties.getInt(key,
                            DEFAULT_TIME_OPTIMIZE_THRESHOLD_MILLIS));
                }
                break;
            case KEY_DOC_COUNT_OPTIMIZE_THRESHOLD:
                synchronized (mLock) {
                    mBundleLocked.putInt(key, properties.getInt(key,
                            DEFAULT_DOC_COUNT_OPTIMIZE_THRESHOLD));
                }
                break;
            default:
                break;
        }
    }
}
