/*
 * Copyright 2008 The Android Open Source Project
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

package android.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.content.AttributionSource;

import com.android.modules.utils.SynchronousResultReceiver;

/**
 * API for Bluetooth Headset service
 *
 * Note before adding anything new:
 *   Internal interactions within com.android.bluetooth should be handled through
 *   HeadsetService directly instead of going through binder
 *
 * {@hide}
 */
interface IBluetoothHeadset {
    // Public API
    @UnsupportedAppUsage
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    List<BluetoothDevice> getConnectedDevices();
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void getConnectedDevicesWithAttribution(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void getDevicesMatchingConnectionStates(in int[] states, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @UnsupportedAppUsage
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    int getConnectionState(in BluetoothDevice device);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void getConnectionStateWithAttribution(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.MODIFY_PHONE_STATE})")
    oneway void startVoiceRecognition(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void stopVoiceRecognition(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void isAudioConnected(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void sendVendorSpecificResultCode(in BluetoothDevice device, in String command, in String arg, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);

    // Hidden API
    @UnsupportedAppUsage
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.MODIFY_PHONE_STATE})")
    boolean connect(in BluetoothDevice device);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.MODIFY_PHONE_STATE})")
    oneway void connectWithAttribution(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @UnsupportedAppUsage
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    boolean disconnect(in BluetoothDevice device);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void disconnectWithAttribution(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED,android.Manifest.permission.MODIFY_PHONE_STATE})")
    oneway void setConnectionPolicy(in BluetoothDevice device, int connectionPolicy, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void getConnectionPolicy(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void getAudioState(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void isAudioOn(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void connectAudio(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void disconnectAudio(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void setAudioRouteAllowed(boolean allowed, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void getAudioRouteAllowed(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void setForceScoAudio(boolean forced, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.MODIFY_PHONE_STATE})")
    oneway void startScoUsingVirtualVoiceCall(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.MODIFY_PHONE_STATE})")
    oneway void stopScoUsingVirtualVoiceCall(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.MODIFY_PHONE_STATE})")
    oneway void phoneStateChanged(int numActive, int numHeld, int callState, String number, int type, String name, in AttributionSource attributionSource);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.MODIFY_PHONE_STATE})")
    oneway void clccResponse(int index, int direction, int status, int mode, boolean mpty, String number, int type, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.MODIFY_PHONE_STATE})")
    oneway void setActiveDevice(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void getActiveDevice(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void isInbandRingingEnabled(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void isNoiseReductionSupported(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void isVoiceRecognitionSupported(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
}
