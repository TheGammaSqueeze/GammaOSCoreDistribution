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
import android.os.Handler;
import android.os.Looper;

/**
 * Handles executing runnables on a background thread.
 *
 * <p>Nearby services follow an event loop model where events can be queued and delivered in the
 * future. All code that is run in this EventLoop is guaranteed to be run on this thread. The main
 * advantage of this model is that all modules don't have to deal with synchronization and race
 * conditions, while making it easy to handle the several asynchronous tasks that are expected to be
 * needed for this type of provider (such as starting a WiFi scan and waiting for the result,
 * starting BLE scans, doing a server request and waiting for the response etc.).
 *
 * <p>Code that needs to wait for an event should not spawn a new thread nor sleep. It should simply
 * deliver a new message to the event queue when the reply of the event happens.
 */
// TODO(b/177675274): Resolve nullness suppression.
@SuppressWarnings("nullness")
public class EventLoop {

    private final Interface mImpl;

    private EventLoop(Interface impl) {
        this.mImpl = impl;
    }

    protected EventLoop(String name) {
        this(new HandlerEventLoopImpl(name));
    }

    /** Creates an EventLoop. */
    public static EventLoop newInstance(String name) {
        return new EventLoop(name);
    }

    /** Creates an EventLoop. */
    public static EventLoop newInstance(String name, Looper looper) {
        return new EventLoop(new HandlerEventLoopImpl(name, looper));
    }

    /** Marks the EventLoop as destroyed. Any further messages received will be ignored. */
    public void destroy() {
        mImpl.destroy();
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
    public void postAndWait(final NamedRunnable runnable) throws InterruptedException {
        mImpl.postAndWait(runnable);
    }

    /**
     * Posts a runnable to this to the front of the event loop, blocking until the runnable has been
     * executed. This should be used rarely, as it can starve the event loop.
     *
     * @param runnable a Runnable to post. This method will not return until the run() method of the
     *                 given runnable has executed on the background thread.
     */
    public void postToFrontAndWait(final NamedRunnable runnable) throws InterruptedException {
        mImpl.postToFrontAndWait(runnable);
    }

    /** Checks if there are any pending posts of the Runnable in the queue. */
    public boolean isPosted(NamedRunnable runnable) {
        return mImpl.isPosted(runnable);
    }

    /**
     * Run code on the event loop thread.
     *
     * @param runnable the runnable to execute.
     */
    public void postRunnable(NamedRunnable runnable) {
        mImpl.postRunnable(runnable);
    }

    /**
     * Run code to be executed when there is no runnable scheduled.
     *
     * @param runnable last runnable to execute.
     */
    public void postEmptyQueueRunnable(final NamedRunnable runnable) {
        mImpl.postEmptyQueueRunnable(runnable);
    }

    /**
     * Run code on the event loop thread after delayedMillis.
     *
     * @param runnable      the runnable to execute.
     * @param delayedMillis the number of milliseconds before executing the runnable.
     */
    public void postRunnableDelayed(NamedRunnable runnable, long delayedMillis) {
        mImpl.postRunnableDelayed(runnable, delayedMillis);
    }

    /**
     * Removes and cancels the specified {@code runnable} if it had not posted/started yet. Calling
     * with null does nothing.
     */
    public void removeRunnable(@Nullable NamedRunnable runnable) {
        mImpl.removeRunnable(runnable);
    }

    /** Asserts that the current operation is being executed in the Event Loop's thread. */
    public void checkThread() {
        mImpl.checkThread();
    }

    public Handler getHandler() {
        return mImpl.getHandler();
    }

    interface Interface {
        void destroy();

        void postAndWait(NamedRunnable runnable) throws InterruptedException;

        void postToFrontAndWait(NamedRunnable runnable) throws InterruptedException;

        boolean isPosted(NamedRunnable runnable);

        void postRunnable(NamedRunnable runnable);

        void postEmptyQueueRunnable(NamedRunnable runnable);

        void postRunnableDelayed(NamedRunnable runnable, long delayedMillis);

        void removeRunnable(NamedRunnable runnable);

        void checkThread();

        Handler getHandler();
    }
}
