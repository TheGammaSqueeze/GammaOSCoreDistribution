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

package com.android.server.nearby.common.eventloop;

import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


/**
 * Handles executing runnables on a background thread.
 *
 * <p>Nearby services follow an event loop model where events can be queued and delivered in the
 * future. All code that is run in this package is guaranteed to be run on this thread. The main
 * advantage of this model is that all modules don't have to deal with synchronization and race
 * conditions, while making it easy to handle the several asynchronous tasks that are expected to be
 * needed for this type of provider (such as starting a WiFi scan and waiting for the result,
 * starting BLE scans, doing a server request and waiting for the response etc.).
 *
 * <p>Code that needs to wait for an event should not spawn a new thread nor sleep. It should simply
 * deliver a new message to the event queue when the reply of the event happens.
 *
 * <p>
 */
// TODO(b/203471261) use executor instead of handler
// TODO(b/177675274): Resolve nullness suppression.
@SuppressWarnings("nullness")
final class HandlerEventLoopImpl implements EventLoop.Interface {
    /** The {@link Message#what} code for all messages that we post to the EventLoop. */
    private static final int WHAT = 0;

    private static final long ELAPSED_THRESHOLD_MS = TimeUnit.SECONDS.toMillis(5);
    private static final long RUNNABLE_DELAY_THRESHOLD_MS = TimeUnit.SECONDS.toMillis(2);
    private static final String TAG = HandlerEventLoopImpl.class.getSimpleName();
    private final MyHandler mHandler;

    private volatile boolean mIsDestroyed = false;

    /** Constructs an EventLoop. */
    HandlerEventLoopImpl(String name) {
        this(name, createHandlerThread(name));
    }

    HandlerEventLoopImpl(String name, Looper looper) {

        mHandler = new MyHandler(looper);
        Log.d(TAG,
                "Created EventLoop for thread '" + looper.getThread().getName()
                        + "(id: " + looper.getThread().getId() + ")'");
    }

    private static Looper createHandlerThread(String name) {
        HandlerThread handlerThread = new HandlerThread(name, Process.THREAD_PRIORITY_BACKGROUND);
        handlerThread.start();

        return handlerThread.getLooper();
    }

    /**
     * Wrapper to satisfy Android Lint. {@link Looper#getQueue()} is public and available since ICS,
     * but was marked @hide until Marshmallow. Tested that this code doesn't crash pre-Marshmallow.
     * /aosp-ics/frameworks/base/core/java/android/os/Looper.java?l=218
     */
    @SuppressLint("NewApi")
    private static MessageQueue getQueue(Handler handler) {
        return handler.getLooper().getQueue();
    }

    /** Marks the EventLoop as destroyed. Any further messages received will be ignored. */
    @Override
    public void destroy() {
        Looper looper = mHandler.getLooper();
        Log.d(TAG,
                "Destroying EventLoop for thread " + looper.getThread().getName()
                        + " (id: " + looper.getThread().getId() + ")");
        looper.quit();
        mIsDestroyed = true;
    }

    /**
     * Posts a runnable to this event loop, blocking until the runnable has been executed. This
     * should
     * be used rarely. It could be useful, for example, for a runnable that initializes the system
     * and
     * must block the posting of all other runnables.
     *
     * @param runnable a Runnable to post. This method will not return until the run() method of the
     *                 given runnable has executed on the background thread.
     */
    @Override
    public void postAndWait(final NamedRunnable runnable) throws InterruptedException {
        internalPostAndWait(runnable, false);
    }

    @Override
    public void postToFrontAndWait(final NamedRunnable runnable) throws InterruptedException {
        internalPostAndWait(runnable, true);
    }

    /** Checks if there are any pending posts of the Runnable in the queue. */
    @Override
    public boolean isPosted(NamedRunnable runnable) {
        return mHandler.hasMessages(WHAT, runnable);
    }

    /**
     * Run code on the event loop thread.
     *
     * @param runnable the runnable to execute.
     */
    @Override
    public void postRunnable(NamedRunnable runnable) {
        Log.d(TAG, "Posting " + runnable);
        mHandler.post(runnable, 0L, false);
    }

    /**
     * Run code to be executed when there is no runnable scheduled.
     *
     * @param runnable last runnable to execute.
     */
    @Override
    public void postEmptyQueueRunnable(final NamedRunnable runnable) {
        mHandler.post(
                () ->
                        getQueue(mHandler)
                                .addIdleHandler(
                                        () -> {
                                            if (mHandler.hasMessages(WHAT)) {
                                                return true;
                                            } else {
                                                // Only stop if start has not been called since
                                                // this was queued
                                                runnable.run();
                                                return false;
                                            }
                                        }));
    }

    /**
     * Run code on the event loop thread after delayedMillis.
     *
     * @param runnable      the runnable to execute.
     * @param delayedMillis the number of milliseconds before executing the runnable.
     */
    @Override
    public void postRunnableDelayed(NamedRunnable runnable, long delayedMillis) {
        Log.d(TAG, "Posting " + runnable + " [delay " + delayedMillis + "]");
        mHandler.post(runnable, delayedMillis, false);
    }

    /**
     * Removes and cancels the specified {@code runnable} if it had not posted/started yet. Calling
     * with null does nothing.
     */
    @Override
    public void removeRunnable(@Nullable NamedRunnable runnable) {
        if (runnable != null) {
            // Removes any pending sent messages where what=WHAT and obj=runnable. We can't use
            // removeCallbacks(runnable) because we're not posting the runnable directly, we're
            // sending a Message with the runnable as its obj.
            mHandler.removeMessages(WHAT, runnable);
        }
    }

    /** Asserts that the current operation is being executed in the Event Loop's thread. */
    @Override
    public void checkThread() {

        Thread currentThread = Looper.myLooper().getThread();
        Thread expectedThread = mHandler.getLooper().getThread();
        if (currentThread.getId() != expectedThread.getId()) {
            throw new IllegalStateException(
                    String.format(
                            "This method must run in the EventLoop thread '%s (id: %s)'. "
                                    + "Was called from thread '%s (id: %s)'.",
                            expectedThread.getName(),
                            expectedThread.getId(),
                            currentThread.getName(),
                            currentThread.getId()));
        }

    }

    @Override
    public Handler getHandler() {
        return mHandler;
    }

    private void internalPostAndWait(final NamedRunnable runnable, boolean postToFront)
            throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        NamedRunnable delegate =
                new NamedRunnable(runnable.name) {
                    @Override
                    public void run() {
                        try {
                            runnable.run();
                        } finally {
                            latch.countDown();
                        }
                    }
                };

        Log.d(TAG, "Posting " + delegate + " and wait");
        if (!mHandler.post(delegate, 0L, postToFront)) {
            // Do not wait if delegate is not posted.
            Log.d(TAG, delegate + " not posted");
            latch.countDown();
        }
        latch.await();
    }

    /** Handler that executes code on a private event loop thread. */
    private class MyHandler extends Handler {

        MyHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            NamedRunnable runnable = (NamedRunnable) msg.obj;

            if (mIsDestroyed) {
                Log.w(TAG, "Runnable " + runnable
                        + " attempted to run after the EventLoop was destroyed. Ignoring");
                return;
            }
            Log.i(TAG, "Executing " + runnable);

            // Did this runnable start much later than we expected it to? If so, then log.
            long expectedStartTime = (long) msg.arg1 << 32 | (msg.arg2 & 0xFFFFFFFFL);
            logIfExceedsThreshold(
                    RUNNABLE_DELAY_THRESHOLD_MS, expectedStartTime, runnable, "was delayed for");

            long startTimeMillis = SystemClock.elapsedRealtime();
            try {
                runnable.run();
            } catch (Exception t) {
                Log.e(TAG, runnable + "crashed.");
                throw t;
            } finally {
                logIfExceedsThreshold(ELAPSED_THRESHOLD_MS, startTimeMillis, runnable, "ran for");
            }
        }

        private boolean post(NamedRunnable runnable, long delayedMillis, boolean postToFront) {
            if (mIsDestroyed) {
                Log.w(TAG, runnable + " not posted since EventLoop is destroyed");
                return false;
            }
            long expectedStartTime = SystemClock.elapsedRealtime() + delayedMillis;
            int arg1 = (int) (expectedStartTime >> 32);
            int arg2 = (int) expectedStartTime;
            Message message = obtainMessage(WHAT, arg1, arg2, runnable /* obj */);
            boolean sent =
                    postToFront
                            ? sendMessageAtFrontOfQueue(message)
                            : sendMessageDelayed(message, delayedMillis);
            if (!sent) {
                Log.w(TAG, runnable + "not posted since looper is exiting");
            }
            return sent;
        }

        private void logIfExceedsThreshold(
                long thresholdMillis, long startTimeMillis, NamedRunnable runnable,
                String message) {
            long elapsedMillis = SystemClock.elapsedRealtime() - startTimeMillis;
            if (elapsedMillis > thresholdMillis) {
                String elapsedFormatted =
                        new SimpleDateFormat("mm:ss.SSS", Locale.US).format(elapsedMillis);
                Log.w(TAG, runnable + " " + message + " " + elapsedFormatted);
            }
        }
    }
}
