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

package android.app.appsearch.testutil;

import com.android.server.appsearch.AppSearchConfig;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An instance of {@link AppSearchConfig} which does not read from any flag system, but simply
 * returns the defaults for each key.
 *
 * <p>This class is thread safe.
 *
 * @hide
 */
public final class FakeAppSearchConfig implements AppSearchConfig {
    private final AtomicBoolean mIsClosed = new AtomicBoolean();

    @Override
    public void close() {
        mIsClosed.set(true);
    }

    @Override
    public long getCachedMinTimeIntervalBetweenSamplesMillis() {
        throwIfClosed();
        return DEFAULT_MIN_TIME_INTERVAL_BETWEEN_SAMPLES_MILLIS;
    }

    @Override
    public int getCachedSamplingIntervalDefault() {
        throwIfClosed();
        return DEFAULT_SAMPLING_INTERVAL;
    }

    @Override
    public int getCachedSamplingIntervalForBatchCallStats() {
        throwIfClosed();
        return getCachedSamplingIntervalDefault();
    }

    @Override
    public int getCachedSamplingIntervalForPutDocumentStats() {
        throwIfClosed();
        return getCachedSamplingIntervalDefault();
    }

    @Override
    public int getCachedSamplingIntervalForInitializeStats() {
            throwIfClosed();
            return getCachedSamplingIntervalDefault();
    }

    @Override
    public int getCachedSamplingIntervalForSearchStats() {
            throwIfClosed();
            return getCachedSamplingIntervalDefault();
    }

    @Override
    public int getCachedSamplingIntervalForGlobalSearchStats() {
        throwIfClosed();
        return getCachedSamplingIntervalDefault();
    }

    @Override
    public int getCachedSamplingIntervalForOptimizeStats() {
        throwIfClosed();
        return getCachedSamplingIntervalDefault();
    }

    @Override
    public int getCachedLimitConfigMaxDocumentSizeBytes() {
        throwIfClosed();
        return DEFAULT_LIMIT_CONFIG_MAX_DOCUMENT_SIZE_BYTES;
    }

    @Override
    public int getCachedLimitConfigMaxDocumentCount() {
        throwIfClosed();
        return DEFAULT_LIMIT_CONFIG_MAX_DOCUMENT_COUNT;
    }

    @Override
    public int getCachedBytesOptimizeThreshold() {
        throwIfClosed();
        return DEFAULT_BYTES_OPTIMIZE_THRESHOLD;
    }

    @Override
    public int getCachedTimeOptimizeThresholdMs() {
        throwIfClosed();
        return DEFAULT_TIME_OPTIMIZE_THRESHOLD_MILLIS;
    }

    @Override
    public int getCachedDocCountOptimizeThreshold() {
        throwIfClosed();
        return DEFAULT_DOC_COUNT_OPTIMIZE_THRESHOLD;
    }

    private void throwIfClosed() {
        if (mIsClosed.get()) {
            throw new IllegalStateException("Trying to use a closed AppSearchConfig instance.");
        }
    }
}
