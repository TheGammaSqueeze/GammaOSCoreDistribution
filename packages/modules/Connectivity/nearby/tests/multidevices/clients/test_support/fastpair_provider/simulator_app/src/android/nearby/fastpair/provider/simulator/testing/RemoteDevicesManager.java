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

package android.nearby.fastpair.provider.simulator.testing;

import static com.google.common.base.Preconditions.checkState;

import android.util.Log;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

/**
 * Manages the IO streams with remote devices.
 *
 * <p>The caller must invoke {@link #registerRemoteDevice} before starting to communicate with the
 * remote device, and invoke {@link #unregisterRemoteDevice} after finishing tasks. If this instance
 * is not used anymore, the caller need to invoke {@link #destroy} to release all resources.
 *
 * <p>All of the methods are thread-safe.
 */
public class RemoteDevicesManager {
    private static final String TAG = "RemoteDevicesManager";

    private final Map<String, RemoteDevice> mRemoteDeviceMap = new HashMap<>();
    private final ListeningExecutorService mBackgroundExecutor =
            MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
    private final ListeningExecutorService mListenInputStreamExecutors =
            MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
    private final Map<String, ListenableFuture<Void>> mListeningTaskMap = new HashMap<>();

    /**
     * Opens input and output data streams for {@code remoteDevice} in the background and notifies
     * the
     * open result via {@code callback}, and assigns a dedicated executor to listen the input data
     * stream if data streams are opened successfully. The dedicated executor will invoke the
     * {@code
     * remoteDevice.inputStreamListener().onInputData()} directly if the new data exists in the
     * input
     * stream and invoke the {@code remoteDevice.inputStreamListener().onClose()} if the input
     * stream
     * is closed.
     */
    public synchronized void registerRemoteDevice(String id, RemoteDevice remoteDevice) {
        checkState(mRemoteDeviceMap.put(id, remoteDevice) == null,
                "The %s is already registered", id);
        startListeningInputStreamTask(remoteDevice);
    }

    /**
     * Closes the data streams for specific remote device {@code id} in the background and notifies
     * the result via {@code callback}.
     */
    public synchronized void unregisterRemoteDevice(String id) {
        RemoteDevice remoteDevice = mRemoteDeviceMap.remove(id);
        checkState(remoteDevice != null, "The %s is not registered", id);
        if (mListeningTaskMap.containsKey(id)) {
            mListeningTaskMap.remove(id).cancel(/* mayInterruptIfRunning= */ true);
        }
    }

    /** Closes all data streams of registered remote devices and stop all background tasks. */
    public synchronized void destroy() {
        mRemoteDeviceMap.clear();
        mListeningTaskMap.clear();
        mListenInputStreamExecutors.shutdownNow();
    }

    /**
     * Writes {@code data} into the output data stream of specific remote device {@code id} in the
     * background and notifies the result via {@code callback}.
     */
    public synchronized void writeDataToRemoteDevice(
            String id, ByteString data, FutureCallback<Void> callback) {
        RemoteDevice remoteDevice = mRemoteDeviceMap.get(id);
        checkState(remoteDevice != null, "The %s is not registered", id);

        runInBackground(() -> {
            remoteDevice.getStreamIOHandler().write(data);
            return null;
        }, callback);
    }

    private void runInBackground(Callable<Void> callable, FutureCallback<Void> callback) {
        Futures.addCallback(
                mBackgroundExecutor.submit(callable), callback, MoreExecutors.directExecutor());
    }

    private void startListeningInputStreamTask(RemoteDevice remoteDevice) {
        ListenableFuture<Void> listenFuture = mListenInputStreamExecutors.submit(() -> {
            Log.i(TAG, "Start listening " + remoteDevice.getId());
            while (true) {
                ByteString data;
                try {
                    data = remoteDevice.getStreamIOHandler().read();
                } catch (IOException | IllegalStateException e) {
                    break;
                }
                remoteDevice.getInputStreamListener().onInputData(data);
            }
        }, /* result= */ null);
        Futures.addCallback(listenFuture, new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Log.i(TAG, "Stop listening " + remoteDevice.getId());
                remoteDevice.getInputStreamListener().onClose();
            }

            @Override
            public void onFailure(Throwable t) {
                Log.w(TAG, "Stop listening " + remoteDevice.getId() + ", cause: " + t);
                remoteDevice.getInputStreamListener().onClose();
            }
        }, MoreExecutors.directExecutor());
        mListeningTaskMap.put(remoteDevice.getId(), listenFuture);
    }
}
