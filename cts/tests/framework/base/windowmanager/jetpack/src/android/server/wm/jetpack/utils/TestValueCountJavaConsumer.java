/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.server.wm.jetpack.utils;

import androidx.annotation.Nullable;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Consumer that provides a simple way to wait for a specific count of values to be received within
 * a timeout and then return the last value.
 * <p>
 * Note that this class should only be used if {@link TestValueCountConsumer} can not be used prior
 * to {@link ExtensionUtil#EXTENSION_VERSION_2}.
 */
public class TestValueCountJavaConsumer<T> implements Consumer<T> {

    private static final long TIMEOUT_MS = 3000;
    private static final int DEFAULT_COUNT = 1;
    private int mCount = DEFAULT_COUNT;
    private LinkedBlockingQueue<T> mLinkedBlockingQueue;
    private T mLastReportedValue;

    public TestValueCountJavaConsumer() {
        mLinkedBlockingQueue = new LinkedBlockingQueue<>();
    }

    @Override
    public void accept(T value) {
        // Asynchronously offer value to queue
        mLinkedBlockingQueue.offer(value);
    }

    public void setCount(int count) {
        mCount = count;
    }

    @Nullable
    public T waitAndGet() throws InterruptedException {
        T value = null;
        for (int i = 0; i < mCount; i++) {
            value = mLinkedBlockingQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
        mLastReportedValue = value;
        return value;
    }

    // Doesn't change the count.
    public void clearQueue() {
        mLinkedBlockingQueue.clear();
    }

    @Nullable
    public T getLastReportedValue() {
        return mLastReportedValue;
    }
}
