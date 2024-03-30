/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.libraries.testing.deviceshadower.internal.common;

import com.android.internal.annotations.VisibleForTesting;
import com.android.libraries.testing.deviceshadower.internal.DeviceShadowEnvironmentImpl;
import com.android.libraries.testing.deviceshadower.internal.utils.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.GuardedBy;

/**
 * Scheduler to post runnables to a single thread.
 */
public class Scheduler {

    private static final Logger LOGGER = Logger.create("Scheduler");

    @GuardedBy("Scheduler.class")
    private static int sTotalRunnables = 0;

    private static CountDownLatch sCompleteLatch;

    public Scheduler() {
        this(null);
    }

    public Scheduler(String name) {
        mExecutor =
                Executors.newSingleThreadExecutor(
                        r -> {
                            Thread thread = Executors.defaultThreadFactory().newThread(r);
                            if (name != null) {
                                thread.setName(name);
                            }
                            return thread;
                        });
    }

    public static boolean await(long timeoutMillis) throws InterruptedException {

        synchronized (Scheduler.class) {
            if (isComplete()) {
                return true;
            }
            if (sCompleteLatch == null) {
                sCompleteLatch = new CountDownLatch(1);
            }
        }

        // TODO(b/200231384): solve potential NPE caused by race condition.
        boolean result = sCompleteLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        synchronized (Scheduler.class) {
            sCompleteLatch = null;
        }
        return result;
    }

    private final ExecutorService mExecutor;

    @GuardedBy("this")
    private final List<ScheduledRunnable> mRunnables = new ArrayList<>();

    @GuardedBy("this")
    private long mCurrentTimeMillis = 0;

    @GuardedBy("this")
    private List<ScheduledRunnable> mRunningRunnables = new ArrayList<>();

    /**
     * Post a {@link NamedRunnable} to scheduler.
     *
     * <p>Return value can be ignored because exception will be handled by {@link
     * DeviceShadowEnvironmentImpl#catchInternalException}.
     */
    // @CanIgnoreReturnValue
    public synchronized Future<?> post(NamedRunnable r) {
        synchronized (Scheduler.class) {
            sTotalRunnables++;
        }
        advance(0);
        return mExecutor.submit(new ScheduledRunnable(r, mCurrentTimeMillis).mRunnable);
    }

    public synchronized void post(NamedRunnable r, long delayMillis) {
        synchronized (Scheduler.class) {
            sTotalRunnables++;
        }
        addRunnables(new ScheduledRunnable(r, mCurrentTimeMillis + delayMillis));
        advance(0);
    }

    public synchronized void shutdown() {
        mExecutor.shutdown();
    }

    @VisibleForTesting
    synchronized void advance(long durationMillis) {
        mCurrentTimeMillis += durationMillis;
        while (mRunnables.size() > 0) {
            ScheduledRunnable r = mRunnables.get(0);
            if (r.mTimeMillis <= mCurrentTimeMillis) {
                mRunnables.remove(0);
                mExecutor.execute(r.mRunnable);
            } else {
                break;
            }
        }
    }

    private synchronized void addRunnables(ScheduledRunnable r) {
        int index = 0;
        while (index < mRunnables.size() && mRunnables.get(index).mTimeMillis <= r.mTimeMillis) {
            index++;
        }
        mRunnables.add(index, r);
    }

    @VisibleForTesting
    static synchronized boolean isComplete() {
        return sTotalRunnables == 0;
    }

    // Can only be called by DeviceShadowEnvironmentImpl when reset.
    public static synchronized void clear() {
        sTotalRunnables = 0;
    }

    class ScheduledRunnable {

        final NamedRunnable mRunnable;
        final long mTimeMillis;

        ScheduledRunnable(final NamedRunnable r, long timeMillis) {
            this.mTimeMillis = timeMillis;
            this.mRunnable =
                    NamedRunnable.create(
                            r.toString(),
                            () -> {
                                synchronized (Scheduler.this) {
                                    Scheduler.this.mRunningRunnables.add(ScheduledRunnable.this);
                                }

                                try {
                                    r.run();
                                } catch (Exception e) {
                                    LOGGER.e("Error in scheduler runnable " + r, e);
                                    DeviceShadowEnvironmentImpl.catchInternalException(e);
                                }

                                synchronized (Scheduler.this) {
                                    // Remove the last one.
                                    Scheduler.this.mRunningRunnables.remove(
                                            Scheduler.this.mRunningRunnables.size() - 1);
                                }

                                // If this is last runnable,
                                // When this section runs before await:
                                //   totalRunnable will be 0, await will return directly.
                                // When this section runs after await:
                                //   latch will not be null, count down will terminate await.

                                // TODO(b/200231384): when there are two threads running at same
                                // time, there will be a case when totalRunnable is 0, but another
                                // thread pending to acquire Scheduler.class lock to post a
                                // runnable. Hence, await here might not be correct in this case.
                                synchronized (Scheduler.class) {
                                    sTotalRunnables--;
                                    if (isComplete()) {
                                        if (sCompleteLatch != null) {
                                            sCompleteLatch.countDown();
                                        }
                                    }
                                }
                            });
        }

        @Override
        public String toString() {
            return mRunnable.toString();
        }
    }

    @Override
    public synchronized String toString() {
        return String.format(
                "\t%d scheduled runnables %s\n\t%d still running or aborted %s",
                mRunnables.size(), mRunnables, mRunningRunnables.size(), mRunningRunnables);
    }
}
