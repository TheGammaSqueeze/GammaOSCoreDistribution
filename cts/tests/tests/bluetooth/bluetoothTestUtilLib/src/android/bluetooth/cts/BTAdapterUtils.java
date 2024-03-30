/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.bluetooth.cts;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.util.SparseIntArray;

import androidx.test.platform.app.InstrumentationRegistry;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Utility for controlling the Bluetooth adapter from CTS test.
 */
public class BTAdapterUtils {
    private static final String TAG = "BTAdapterUtils";
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    // ADAPTER_ENABLE_TIMEOUT_MS = AdapterState.BLE_START_TIMEOUT_DELAY +
    //                              AdapterState.BREDR_START_TIMEOUT_DELAY
    private static final int ADAPTER_ENABLE_TIMEOUT_MS = 8000;
    // ADAPTER_DISABLE_TIMEOUT_MS = AdapterState.BLE_STOP_TIMEOUT_DELAY +
    //                                  AdapterState.BREDR_STOP_TIMEOUT_DELAY
    private static final int ADAPTER_DISABLE_TIMEOUT_MS = 5000;

    public static final int STATE_BLE_TURNING_ON = 14;
    public static final int STATE_BLE_ON = 15;
    public static final int STATE_BLE_TURNING_OFF = 16;

    private static final SparseIntArray sStateTimeouts = new SparseIntArray();
    static {
        sStateTimeouts.put(BluetoothAdapter.STATE_OFF, ADAPTER_DISABLE_TIMEOUT_MS);
        sStateTimeouts.put(BluetoothAdapter.STATE_TURNING_ON, ADAPTER_ENABLE_TIMEOUT_MS);
        sStateTimeouts.put(BluetoothAdapter.STATE_ON, ADAPTER_ENABLE_TIMEOUT_MS);
        sStateTimeouts.put(BluetoothAdapter.STATE_TURNING_OFF, ADAPTER_DISABLE_TIMEOUT_MS);
        sStateTimeouts.put(STATE_BLE_TURNING_ON, ADAPTER_ENABLE_TIMEOUT_MS);
        sStateTimeouts.put(STATE_BLE_ON, ADAPTER_ENABLE_TIMEOUT_MS);
        sStateTimeouts.put(STATE_BLE_TURNING_OFF, ADAPTER_DISABLE_TIMEOUT_MS);
    }

    private static BluetoothAdapterReceiver sAdapterReceiver;

    private static boolean sAdapterVarsInitialized;
    private static ReentrantLock sBluetoothAdapterLock;
    private static Condition sConditionAdapterStateReached;
    private static int sDesiredState;
    private static int sAdapterState;

    /**
     * Handles BluetoothAdapter state changes and signals when we have reached a desired state
     */
    private static class BluetoothAdapterReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_BLE_STATE_CHANGED.equals(action)) {
                int newState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                if (DBG) {
                    Log.d(TAG, "Bluetooth adapter state changed: " + newState);
                }

                // Signal if the state is set to the one we are waiting on
                sBluetoothAdapterLock.lock();
                sAdapterState = newState;
                try {
                    if (sDesiredState == newState) {
                        if (DBG) {
                            Log.d(TAG, "Adapter has reached desired state: " + sDesiredState);
                        }
                        sConditionAdapterStateReached.signal();
                    }
                } finally {
                    sBluetoothAdapterLock.unlock();
                }
            }
        }
    }

    /**
     * Initialize all static state variables
     */
    private static void initAdapterStateVariables(Context context) {
        if (DBG) {
            Log.d(TAG, "Initializing adapter state variables");
        }
        sAdapterReceiver = new BluetoothAdapterReceiver();
        sBluetoothAdapterLock = new ReentrantLock();
        sConditionAdapterStateReached = sBluetoothAdapterLock.newCondition();
        sDesiredState = -1;
        sAdapterState = -1;
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_BLE_STATE_CHANGED);
        context.registerReceiver(sAdapterReceiver, filter);
        sAdapterVarsInitialized = true;
    }

    /**
     * Wait for the bluetooth adapter to be in a given state
     *
     * Assumes all state variables are initialized. Assumes it's being run with
     * sBluetoothAdapterLock in the locked state.
     */
    private static boolean waitForAdapterStateLocked(int desiredState, BluetoothAdapter adapter)
            throws InterruptedException {
        int timeout = sStateTimeouts.get(desiredState, ADAPTER_ENABLE_TIMEOUT_MS);

        if (DBG) {
            Log.d(TAG, "Waiting for adapter state " + desiredState);
        }
        sDesiredState = desiredState;

        // Wait until we have reached the desired state
        while (desiredState != sAdapterState) {
            if (!sConditionAdapterStateReached.await(timeout, TimeUnit.MILLISECONDS)) {
                // Handle situation where state change occurs, but we don't receive the broadcast
                if (desiredState >= BluetoothAdapter.STATE_OFF
                        && desiredState <= BluetoothAdapter.STATE_TURNING_OFF) {
                    return adapter.getState() == desiredState;
                } else if (desiredState == STATE_BLE_ON) {
                    Log.d(TAG, "adapter isLeEnabled: " + adapter.isLeEnabled());
                    return adapter.isLeEnabled();
                }
                Log.e(TAG, "Timeout while waiting for Bluetooth adapter state " + desiredState
                        + " while current state is " + sAdapterState);
                break;
            }
        }

        if (DBG) {
            Log.d(TAG, "Final state while waiting: " + sAdapterState);
        }

        return sAdapterState == desiredState;
    }

    /**
     * Utility method to wait on any specific adapter state
     */
    public static boolean waitForAdapterState(int desiredState, BluetoothAdapter adapter) {
        sBluetoothAdapterLock.lock();
        try {
            return waitForAdapterStateLocked(desiredState, adapter);
        } catch (InterruptedException e) {
            Log.w(TAG, "waitForAdapterState(): interrupted", e);
        } finally {
            sBluetoothAdapterLock.unlock();
        }
        return false;
    }

    /**
     * Enables Bluetooth to a Low Energy only mode
     */
    public static boolean enableBLE(BluetoothAdapter bluetoothAdapter, Context context) {
        if (!sAdapterVarsInitialized) {
            initAdapterStateVariables(context);
        }

        if (bluetoothAdapter.isLeEnabled()) {
            return true;
        }

        sBluetoothAdapterLock.lock();
        try {
            if (DBG) {
                Log.d(TAG, "Enabling Bluetooth low energy only mode");
            }
            if (!bluetoothAdapter.enableBLE()) {
                Log.e(TAG, "Unable to enable Bluetooth low energy only mode");
                return false;
            }
            return waitForAdapterStateLocked(STATE_BLE_ON, bluetoothAdapter);
        } catch (InterruptedException e) {
            Log.w(TAG, "enableBLE(): interrupted", e);
        } finally {
            sBluetoothAdapterLock.unlock();
        }
        return false;
    }

    /**
     * Disable Bluetooth Low Energy mode
     */
    public static boolean disableBLE(BluetoothAdapter bluetoothAdapter, Context context) {
        if (!sAdapterVarsInitialized) {
            initAdapterStateVariables(context);
        }

        if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_OFF) {
            return true;
        }

        sBluetoothAdapterLock.lock();
        try {
            if (DBG) {
                Log.d(TAG, "Disabling Bluetooth low energy");
            }
            bluetoothAdapter.disableBLE();
            return waitForAdapterStateLocked(BluetoothAdapter.STATE_OFF, bluetoothAdapter);
        } catch (InterruptedException e) {
            Log.w(TAG, "disableBLE(): interrupted", e);
        } finally {
            sBluetoothAdapterLock.unlock();
        }
        return false;
    }

    /**
     * Enables the Bluetooth Adapter. Return true if it is already enabled or is enabled.
     */
    public static boolean enableAdapter(BluetoothAdapter bluetoothAdapter, Context context) {
        if (!sAdapterVarsInitialized) {
            initAdapterStateVariables(context);
        }

        if (bluetoothAdapter.isEnabled()) {
            return true;
        }

        Set<String> permissionsAdopted = getPermissionsAdoptedAsShellUid();
        sBluetoothAdapterLock.lock();
        try {
            if (DBG) {
                Log.d(TAG, "Enabling Bluetooth adapter");
            }
            adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
            bluetoothAdapter.enable();
            return waitForAdapterStateLocked(BluetoothAdapter.STATE_ON, bluetoothAdapter);
        } catch (InterruptedException e) {
            Log.w(TAG, "enableAdapter(): interrupted", e);
        } finally {
            adoptPermissionAsShellUid(permissionsAdopted.toArray(new String[0]));
            sBluetoothAdapterLock.unlock();
        }
        return false;
    }

    /**
     * Disable the Bluetooth Adapter. Return true if it is already disabled or is disabled.
     */
    public static boolean disableAdapter(BluetoothAdapter bluetoothAdapter, Context context) {
        if (!sAdapterVarsInitialized) {
            initAdapterStateVariables(context);
        }

        if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_OFF) {
            return true;
        }

        if (DBG) {
            Log.d(TAG, "Disabling Bluetooth adapter");
        }

        Set<String> permissionsAdopted = getPermissionsAdoptedAsShellUid();
        sBluetoothAdapterLock.lock();
        try {
            adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
            bluetoothAdapter.disable();
            if (waitForAdapterStateLocked(BluetoothAdapter.STATE_OFF, bluetoothAdapter)) {
                //TODO b/234892968
                Thread.sleep(2000);
                return true;
            }
            return false;
        } catch (InterruptedException e) {
            Log.w(TAG, "disableAdapter(): interrupted", e);
        } finally {
            adoptPermissionAsShellUid(permissionsAdopted.toArray(new String[0]));
            sBluetoothAdapterLock.unlock();
        }
        return false;
    }

    /**
     * Disable the Bluetooth Adapter with then option to persist the off state or not.
     *
     * Returns true if the adapter is already disabled or was disabled.
     */
    public static boolean disableAdapter(BluetoothAdapter bluetoothAdapter, boolean persist,
            Context context) {
        if (!sAdapterVarsInitialized) {
            initAdapterStateVariables(context);
        }

        if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_OFF) {
            return true;
        }

        Set<String> permissionsAdopted = getPermissionsAdoptedAsShellUid();
        sBluetoothAdapterLock.lock();
        try {
            if (DBG) {
                Log.d(TAG, "Disabling Bluetooth adapter, persist=" + persist);
            }
            adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
            bluetoothAdapter.disable(persist);
            return waitForAdapterStateLocked(BluetoothAdapter.STATE_OFF, bluetoothAdapter);
        } catch (InterruptedException e) {
            Log.w(TAG, "disableAdapter(persist=" + persist + "): interrupted", e);
        } finally {
            adoptPermissionAsShellUid(permissionsAdopted.toArray(new String[0]));
            sBluetoothAdapterLock.unlock();
        }
        return false;
    }

    /**
     * Adopt shell UID's permission via {@link android.app.UiAutomation}
     * @param permission permission to adopt
     */
    private static void adoptPermissionAsShellUid(String... permission) {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(permission);
    }

    /**
     * Gets all the permissions adopted as the shell UID
     *
     * @return a {@link java.util.Set} of the adopted shell permissions
     */
    private static Set<String> getPermissionsAdoptedAsShellUid() {
        return InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .getAdoptedShellPermissions();
    }
}