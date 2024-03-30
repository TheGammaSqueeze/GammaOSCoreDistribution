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

package android.nearby.fastpair.provider.bluetooth;

import static android.nearby.fastpair.provider.bluetooth.RfcommServer.State.ACCEPTING;
import static android.nearby.fastpair.provider.bluetooth.RfcommServer.State.CONNECTED;
import static android.nearby.fastpair.provider.bluetooth.RfcommServer.State.RESTARTING;
import static android.nearby.fastpair.provider.bluetooth.RfcommServer.State.STARTING;
import static android.nearby.fastpair.provider.bluetooth.RfcommServer.State.STOPPED;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.nearby.fastpair.provider.EventStreamProtocol;
import android.nearby.fastpair.provider.utils.Logger;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Listens for a rfcomm client to connect and supports both sending messages to the client and
 * receiving messages from the client.
 */
public class RfcommServer {
    private static final String TAG = "RfcommServer";
    private final Logger mLogger = new Logger(TAG);

    private static final String FAST_PAIR_RFCOMM_SERVICE_NAME = "FastPairServer";
    public static final UUID FAST_PAIR_RFCOMM_UUID =
            UUID.fromString("df21fe2c-2515-4fdb-8886-f12c4d67927c");

    /** A single thread executor where all state checks are performed. */
    private final ExecutorService mControllerExecutor = Executors.newSingleThreadExecutor();

    private final ExecutorService mSendMessageExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService mReceiveMessageExecutor = Executors.newSingleThreadExecutor();

    @Nullable
    private BluetoothServerSocket mServerSocket;
    @Nullable
    private BluetoothSocket mSocket;

    private State mState = STOPPED;
    private boolean mIsStopRequested = false;

    @Nullable
    private RequestHandler mRequestHandler;

    @Nullable
    private CountDownLatch mCountDownLatch;
    @Nullable
    private StateMonitor mStateMonitor;

    /**
     * Manages RfcommServer status.
     *
     * <pre>{@code
     *      +------------------------------------------------+
     *      +-------------------------------+                |
     *      v                               |                |
     * +---------+    +----------+    +-----+-----+    +-----+-----+
     * | STOPPED +--> | STARTING +--> | ACCEPTING +--> | CONNECTED |
     * +---------+    +-----+----+    +-------+---+    +-----+-----+
     *      ^               |             ^   v              |
     *      +---------------+         +---+--------+         |
     *                                | RESTARTING | <-------+
     *                                +------------+
     * }</pre>
     *
     * If Stop action is not requested, the server will restart forever. Otherwise, go stopped.
     */
    public enum State {
        STOPPED,
        STARTING,
        RESTARTING,
        ACCEPTING,
        CONNECTED,
    }

    /** Starts the rfcomm server. */
    public void start() {
        runInControllerExecutor(this::startServer);
    }

    private void startServer() {
        log("Start RfcommServer");

        if (!mState.equals(STOPPED)) {
            log("Server is not stopped, skip start request.");
            return;
        }
        updateState(STARTING);
        mIsStopRequested = false;

        startAccept();
    }

    private void restartServer() {
        log("Restart RfcommServer");
        updateState(RESTARTING);
        startAccept();
    }

    private void startAccept() {
        try {
            // Gets server socket in controller thread for stop() API.
            mServerSocket =
                    BluetoothAdapter.getDefaultAdapter()
                            .listenUsingRfcommWithServiceRecord(
                                    FAST_PAIR_RFCOMM_SERVICE_NAME, FAST_PAIR_RFCOMM_UUID);
        } catch (IOException e) {
            log("Create service record failed, stop server");
            stopServer();
            return;
        }

        updateState(ACCEPTING);
        new Thread(() -> accept(mServerSocket)).start();
    }

    private void accept(BluetoothServerSocket serverSocket) {
        triggerCountdownLatch();

        try {
            BluetoothSocket socket = serverSocket.accept();
            serverSocket.close();

            runInControllerExecutor(() -> startListen(socket));
        } catch (IOException e) {
            log("IOException when accepting new connection");
            runInControllerExecutor(() -> handleAcceptException(serverSocket));
        }
    }

    private void handleAcceptException(BluetoothServerSocket serverSocket) {
        if (mIsStopRequested) {
            stopServer();
        } else {
            closeServerSocket(serverSocket);
            restartServer();
        }
    }

    private void startListen(BluetoothSocket bluetoothSocket) {
        if (mIsStopRequested) {
            closeSocket(bluetoothSocket);
            stopServer();
            return;
        }

        updateState(CONNECTED);
        // Sets method parameter to global socket for stop() API.
        this.mSocket = bluetoothSocket;
        new Thread(() -> listen(bluetoothSocket)).start();
    }

    private void listen(BluetoothSocket bluetoothSocket) {
        triggerCountdownLatch();

        try {
            DataInputStream dataInputStream = new DataInputStream(bluetoothSocket.getInputStream());
            while (true) {
                int eventGroup = dataInputStream.readUnsignedByte();
                int eventCode = dataInputStream.readUnsignedByte();
                int additionalLength = dataInputStream.readUnsignedShort();

                byte[] data = new byte[additionalLength];
                if (additionalLength > 0) {
                    int count = 0;
                    do {
                        count += dataInputStream.read(data, count, additionalLength - count);
                    } while (count < additionalLength);
                }

                if (mRequestHandler != null) {
                    // In order not to block listening thread, use different thread to dispatch
                    // message.
                    mReceiveMessageExecutor.execute(
                            () -> {
                                mRequestHandler.handleRequest(eventGroup, eventCode, data);
                                triggerCountdownLatch();
                            });
                }
            }
        } catch (IOException e) {
            log(
                    String.format(
                            "IOException when listening to %s",
                            bluetoothSocket.getRemoteDevice().getAddress()));
            runInControllerExecutor(() -> handleListenException(bluetoothSocket));
        }
    }

    private void handleListenException(BluetoothSocket bluetoothSocket) {
        if (mIsStopRequested) {
            stopServer();
        } else {
            closeSocket(bluetoothSocket);
            restartServer();
        }
    }

    public void sendFakeEventStreamMessage(EventStreamProtocol.EventGroup eventGroup) {
        switch (eventGroup) {
            case BLUETOOTH:
                send(EventStreamProtocol.EventGroup.BLUETOOTH_VALUE,
                        EventStreamProtocol.BluetoothEventCode.BLUETOOTH_ENABLE_SILENCE_MODE_VALUE,
                        new byte[0]);
                break;
            case LOGGING:
                send(EventStreamProtocol.EventGroup.LOGGING_VALUE,
                        EventStreamProtocol.LoggingEventCode.LOG_FULL_VALUE,
                        new byte[0]);
                break;
            case DEVICE:
                send(EventStreamProtocol.EventGroup.DEVICE_VALUE,
                        EventStreamProtocol.DeviceEventCode.DEVICE_BATTERY_INFO_VALUE,
                        new byte[]{0x11, 0x12, 0x13});
                break;
            default: // fall out
        }
    }

    public void sendFakeEventStreamLoggingMessage(@Nullable String logContent) {
        send(EventStreamProtocol.EventGroup.LOGGING_VALUE,
                EventStreamProtocol.LoggingEventCode.LOG_SAVE_TO_BUFFER_VALUE,
                logContent != null ? logContent.getBytes(UTF_8) : new byte[0]);
    }

    public void send(int eventGroup, int eventCode, byte[] data) {
        runInControllerExecutor(
                () -> {
                    if (!CONNECTED.equals(mState)) {
                        log("Server is not in CONNECTED state, skip send request");
                        return;
                    }
                    BluetoothSocket bluetoothSocket = this.mSocket;
                    mSendMessageExecutor.execute(() -> {
                        String address = bluetoothSocket.getRemoteDevice().getAddress();
                        try {
                            DataOutputStream dataOutputStream =
                                    new DataOutputStream(bluetoothSocket.getOutputStream());
                            dataOutputStream.writeByte(eventGroup);
                            dataOutputStream.writeByte(eventCode);
                            dataOutputStream.writeShort(data.length);
                            if (data.length > 0) {
                                dataOutputStream.write(data);
                            }
                            dataOutputStream.flush();
                            log(
                                    String.format(
                                            "Send message to %s: %s, %s, %s.",
                                            address, eventGroup, eventCode, data.length));
                        } catch (IOException e) {
                            log(
                                    String.format(
                                            "Failed to send message to %s: %s, %s, %s.",
                                            address, eventGroup, eventCode, data.length),
                                    e);
                        }
                    });
                });
    }

    /** Stops the rfcomm server. */
    public void stop() {
        runInControllerExecutor(() -> {
            log("Stop RfcommServer");

            if (STOPPED.equals(mState)) {
                log("Server is stopped, skip stop request.");
                return;
            }

            if (mIsStopRequested) {
                log("Stop is already requested, skip stop request.");
                return;
            }
            mIsStopRequested = true;

            if (ACCEPTING.equals(mState)) {
                closeServerSocket(mServerSocket);
            }

            if (CONNECTED.equals(mState)) {
                closeSocket(mSocket);
            }
        });
    }

    private void stopServer() {
        updateState(STOPPED);
        triggerCountdownLatch();
    }

    private void updateState(State newState) {
        log(String.format("Change state from %s to %s", mState, newState));
        if (mStateMonitor != null) {
            mStateMonitor.onStateChanged(newState);
        }
        mState = newState;
    }

    private void closeServerSocket(BluetoothServerSocket serverSocket) {
        try {
            if (serverSocket != null) {
                log(String.format("Close server socket: %s", serverSocket));
                serverSocket.close();
            }
        } catch (IOException | NullPointerException e) {
            // NullPointerException is used to skip robolectric test failure.
            // In unit test, different virtual devices are set up in different threads, calling
            // ServerSocket.close() in wrong thread will result in NullPointerException since there
            // is no corresponding service record.
            // TODO(hylo): Remove NullPointerException when the solution is submitted to test cases.
            log("Failed to stop server", e);
        }
    }

    private void closeSocket(BluetoothSocket socket) {
        try {
            if (socket != null && socket.isConnected()) {
                log(String.format("Close socket: %s", socket.getRemoteDevice().getAddress()));
                socket.close();
            }
        } catch (IOException e) {
            log(String.format("IOException when close socket %s",
                    socket.getRemoteDevice().getAddress()));
        }
    }

    private void runInControllerExecutor(Runnable runnable) {
        mControllerExecutor.execute(runnable);
    }

    private void log(String message) {
        mLogger.log("Server=%s, %s", FAST_PAIR_RFCOMM_SERVICE_NAME, message);
    }

    private void log(String message, Throwable e) {
        mLogger.log(e, "Server=%s, %s", FAST_PAIR_RFCOMM_SERVICE_NAME, message);
    }

    private void triggerCountdownLatch() {
        if (mCountDownLatch != null) {
            mCountDownLatch.countDown();
        }
    }

    /** Interface to handle incoming request from clients. */
    public interface RequestHandler {
        void handleRequest(int eventGroup, int eventCode, byte[] data);
    }

    public void setRequestHandler(@Nullable RequestHandler requestHandler) {
        this.mRequestHandler = requestHandler;
    }

    /** A state monitor to send signal when state is changed. */
    public interface StateMonitor {
        void onStateChanged(State state);
    }

    public void setStateMonitor(@Nullable StateMonitor stateMonitor) {
        this.mStateMonitor = stateMonitor;
    }

    @VisibleForTesting
    void setCountDownLatch(@Nullable CountDownLatch countDownLatch) {
        this.mCountDownLatch = countDownLatch;
    }

    @VisibleForTesting
    void setIsStopRequested(boolean isStopRequested) {
        this.mIsStopRequested = isStopRequested;
    }

    @VisibleForTesting
    void simulateAcceptIOException() {
        runInControllerExecutor(() -> {
            if (ACCEPTING.equals(mState)) {
                closeServerSocket(mServerSocket);
            }
        });
    }

    @VisibleForTesting
    void simulateListenIOException() {
        runInControllerExecutor(() -> {
            if (CONNECTED.equals(mState)) {
                closeSocket(mSocket);
            }
        });
    }
}
