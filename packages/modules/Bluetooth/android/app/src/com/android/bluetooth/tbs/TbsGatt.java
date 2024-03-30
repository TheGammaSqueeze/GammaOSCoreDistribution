/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

package com.android.bluetooth.tbs;

import static android.bluetooth.BluetoothDevice.METADATA_GTBS_CCCD;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.IBluetoothManager;
import android.bluetooth.IBluetoothStateChangeCallback;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.util.Log;

import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.Utils;
import com.android.internal.annotations.VisibleForTesting;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class TbsGatt {

    private static final String TAG = "TbsGatt";
    private static final boolean DBG = true;

    private static final String UUID_PREFIX = "0000";
    private static final String UUID_SUFFIX = "-0000-1000-8000-00805f9b34fb";

    /* TBS assigned uuid's */
    @VisibleForTesting
    static final UUID UUID_TBS = makeUuid("184B");
    @VisibleForTesting
    public static final UUID UUID_GTBS = makeUuid("184C");
    @VisibleForTesting
    static final UUID UUID_BEARER_PROVIDER_NAME = makeUuid("2BB3");
    @VisibleForTesting
    static final UUID UUID_BEARER_UCI = makeUuid("2BB4");
    @VisibleForTesting
    static final UUID UUID_BEARER_TECHNOLOGY = makeUuid("2BB5");
    @VisibleForTesting
    static final UUID UUID_BEARER_URI_SCHEMES_SUPPORTED_LIST = makeUuid("2BB6");
    @VisibleForTesting
    static final UUID UUID_BEARER_LIST_CURRENT_CALLS = makeUuid("2BB9");
    @VisibleForTesting
    static final UUID UUID_CONTENT_CONTROL_ID = makeUuid("2BBA");
    @VisibleForTesting
    static final UUID UUID_STATUS_FLAGS = makeUuid("2BBB");
    @VisibleForTesting
    static final UUID UUID_CALL_STATE = makeUuid("2BBD");
    @VisibleForTesting
    static final UUID UUID_CALL_CONTROL_POINT = makeUuid("2BBE");
    @VisibleForTesting
    static final UUID UUID_CALL_CONTROL_POINT_OPTIONAL_OPCODES = makeUuid("2BBF");
    @VisibleForTesting
    static final UUID UUID_TERMINATION_REASON = makeUuid("2BC0");
    @VisibleForTesting
    static final UUID UUID_INCOMING_CALL = makeUuid("2BC1");
    @VisibleForTesting
    static final UUID UUID_CALL_FRIENDLY_NAME = makeUuid("2BC2");
    @VisibleForTesting
    static final UUID UUID_CLIENT_CHARACTERISTIC_CONFIGURATION = makeUuid("2902");

    @VisibleForTesting
    static final int STATUS_FLAG_INBAND_RINGTONE_ENABLED = 0x0001;
    @VisibleForTesting
    static final int STATUS_FLAG_SILENT_MODE_ENABLED = 0x0002;

    @VisibleForTesting
    static final int CALL_CONTROL_POINT_OPTIONAL_OPCODE_LOCAL_HOLD = 0x0001;
    @VisibleForTesting
    static final int CALL_CONTROL_POINT_OPTIONAL_OPCODE_JOIN = 0x0002;

    @VisibleForTesting
    public static final int CALL_CONTROL_POINT_OPCODE_ACCEPT = 0x00;
    @VisibleForTesting
    public static final int CALL_CONTROL_POINT_OPCODE_TERMINATE = 0x01;
    @VisibleForTesting
    public static final int CALL_CONTROL_POINT_OPCODE_LOCAL_HOLD = 0x02;
    @VisibleForTesting
    public static final int CALL_CONTROL_POINT_OPCODE_LOCAL_RETRIEVE = 0x03;
    @VisibleForTesting
    public static final int CALL_CONTROL_POINT_OPCODE_ORIGINATE = 0x04;
    @VisibleForTesting
    public static final int CALL_CONTROL_POINT_OPCODE_JOIN = 0x05;

    @VisibleForTesting
    public static final int CALL_CONTROL_POINT_RESULT_SUCCESS = 0x00;
    @VisibleForTesting
    public static final int CALL_CONTROL_POINT_RESULT_OPCODE_NOT_SUPPORTED = 0x01;
    @VisibleForTesting
    public static final int CALL_CONTROL_POINT_RESULT_OPERATION_NOT_POSSIBLE = 0x02;
    @VisibleForTesting
    public static final int CALL_CONTROL_POINT_RESULT_INVALID_CALL_INDEX = 0x03;
    @VisibleForTesting
    public static final int CALL_CONTROL_POINT_RESULT_STATE_MISMATCH = 0x04;
    @VisibleForTesting
    public static final int CALL_CONTROL_POINT_RESULT_LACK_OF_RESOURCES = 0x05;
    @VisibleForTesting
    public static final int CALL_CONTROL_POINT_RESULT_INVALID_OUTGOING_URI = 0x06;

    private final Context mContext;
    private final GattCharacteristic mBearerProviderNameCharacteristic;
    private final GattCharacteristic mBearerUciCharacteristic;
    private final GattCharacteristic mBearerTechnologyCharacteristic;
    private final GattCharacteristic mBearerUriSchemesSupportedListCharacteristic;
    private final GattCharacteristic mBearerListCurrentCallsCharacteristic;
    private final GattCharacteristic mContentControlIdCharacteristic;
    private final GattCharacteristic mStatusFlagsCharacteristic;
    private final GattCharacteristic mCallStateCharacteristic;
    private final CallControlPointCharacteristic mCallControlPointCharacteristic;
    private final GattCharacteristic mCallControlPointOptionalOpcodesCharacteristic;
    private final GattCharacteristic mTerminationReasonCharacteristic;
    private final GattCharacteristic mIncomingCallCharacteristic;
    private final GattCharacteristic mCallFriendlyNameCharacteristic;
    private List<BluetoothDevice> mSubscribers = new ArrayList<>();
    private BluetoothGattServerProxy mBluetoothGattServer;
    private Handler mHandler;
    private Callback mCallback;
    private AdapterService mAdapterService;

    public static abstract class Callback {

        public abstract void onServiceAdded(boolean success);

        public abstract void onCallControlPointRequest(BluetoothDevice device, int opcode,
                byte[] args);
    }

    TbsGatt(Context context) {
        mAdapterService =  Objects.requireNonNull(AdapterService.getAdapterService(),
                "AdapterService shouldn't be null when creating MediaControlCattService");
        IBluetoothManager mgr = BluetoothAdapter.getDefaultAdapter().getBluetoothManager();
        if (mgr != null) {
            try {
                mgr.registerStateChangeCallback(mBluetoothStateChangeCallback);
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }

        mContext = context;
        mBearerProviderNameCharacteristic = new GattCharacteristic(UUID_BEARER_PROVIDER_NAME,
                BluetoothGattCharacteristic.PROPERTY_READ
                        | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED);
        mBearerUciCharacteristic =
                new GattCharacteristic(UUID_BEARER_UCI, BluetoothGattCharacteristic.PROPERTY_READ,
                        BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED);
        mBearerTechnologyCharacteristic = new GattCharacteristic(UUID_BEARER_TECHNOLOGY,
                BluetoothGattCharacteristic.PROPERTY_READ
                        | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED);
        mBearerUriSchemesSupportedListCharacteristic =
                new GattCharacteristic(UUID_BEARER_URI_SCHEMES_SUPPORTED_LIST,
                        BluetoothGattCharacteristic.PROPERTY_READ
                                | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                        BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED);
        mBearerListCurrentCallsCharacteristic =
                new GattCharacteristic(UUID_BEARER_LIST_CURRENT_CALLS,
                        BluetoothGattCharacteristic.PROPERTY_READ
                                | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                        BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED);
        mContentControlIdCharacteristic = new GattCharacteristic(UUID_CONTENT_CONTROL_ID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED);
        mStatusFlagsCharacteristic = new GattCharacteristic(UUID_STATUS_FLAGS,
                BluetoothGattCharacteristic.PROPERTY_READ
                        | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED);
        mCallStateCharacteristic = new GattCharacteristic(UUID_CALL_STATE,
                BluetoothGattCharacteristic.PROPERTY_READ
                        | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED);
        mCallControlPointCharacteristic = new CallControlPointCharacteristic();
        mCallControlPointOptionalOpcodesCharacteristic = new GattCharacteristic(
                UUID_CALL_CONTROL_POINT_OPTIONAL_OPCODES, BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED);
        mTerminationReasonCharacteristic = new GattCharacteristic(UUID_TERMINATION_REASON,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0);
        mIncomingCallCharacteristic = new GattCharacteristic(UUID_INCOMING_CALL,
                BluetoothGattCharacteristic.PROPERTY_READ
                        | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED);
        mCallFriendlyNameCharacteristic = new GattCharacteristic(UUID_CALL_FRIENDLY_NAME,
                BluetoothGattCharacteristic.PROPERTY_READ
                        | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED);
        mBluetoothGattServer = null;
    }

    @VisibleForTesting
    void setBluetoothGattServerForTesting(BluetoothGattServerProxy proxy) {
        mBluetoothGattServer = proxy;
    }

    public boolean init(int ccid, String uci, List<String> uriSchemes,
            boolean isLocalHoldOpcodeSupported, boolean isJoinOpcodeSupported, String providerName,
            int technology, Callback callback) {
        mBearerProviderNameCharacteristic.setValue(providerName);
        mBearerTechnologyCharacteristic.setValue(new byte[] {(byte) (technology & 0xFF)});
        mBearerUciCharacteristic.setValue(uci);
        setBearerUriSchemesSupportedList(uriSchemes);
        mContentControlIdCharacteristic.setValue(ccid, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        setCallControlPointOptionalOpcodes(isLocalHoldOpcodeSupported, isJoinOpcodeSupported);
        mStatusFlagsCharacteristic.setValue(0, BluetoothGattCharacteristic.FORMAT_UINT16, 0);
        mCallback = callback;
        mHandler = new Handler(Looper.getMainLooper());

        if (mBluetoothGattServer == null) {
            mBluetoothGattServer = new BluetoothGattServerProxy(mContext);
        }

        if (!mBluetoothGattServer.open(mGattServerCallback)) {
            Log.e(TAG, " Could not open Gatt server");
            return false;
        }

        BluetoothGattService gattService =
                new BluetoothGattService(UUID_GTBS, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        gattService.addCharacteristic(mBearerProviderNameCharacteristic);
        gattService.addCharacteristic(mBearerUciCharacteristic);
        gattService.addCharacteristic(mBearerTechnologyCharacteristic);
        gattService.addCharacteristic(mBearerUriSchemesSupportedListCharacteristic);
        gattService.addCharacteristic(mBearerListCurrentCallsCharacteristic);
        gattService.addCharacteristic(mContentControlIdCharacteristic);
        gattService.addCharacteristic(mStatusFlagsCharacteristic);
        gattService.addCharacteristic(mCallStateCharacteristic);
        gattService.addCharacteristic(mCallControlPointCharacteristic);
        gattService.addCharacteristic(mCallControlPointOptionalOpcodesCharacteristic);
        gattService.addCharacteristic(mTerminationReasonCharacteristic);
        gattService.addCharacteristic(mIncomingCallCharacteristic);
        gattService.addCharacteristic(mCallFriendlyNameCharacteristic);

        return mBluetoothGattServer.addService(gattService);
    }

    public void cleanup() {
        if (mBluetoothGattServer == null) {
            return;
        }
        mBluetoothGattServer.close();
        mBluetoothGattServer = null;
    }

    public Context getContext() {
        return mContext;
    }

    private void removeUuidFromMetadata(ParcelUuid charUuid, BluetoothDevice device) {
        List<ParcelUuid> uuidList;
        byte[] gtbs_cccd = device.getMetadata(METADATA_GTBS_CCCD);

        if ((gtbs_cccd == null) || (gtbs_cccd.length == 0)) {
            uuidList = new ArrayList<ParcelUuid>();
        } else {
            uuidList = new ArrayList<>(Arrays.asList(Utils.byteArrayToUuid(gtbs_cccd)));

            if (!uuidList.contains(charUuid)) {
                Log.d(TAG, "Characteristic CCCD can't be removed (not cached): "
                        + charUuid.toString());
                return;
            }
        }

        uuidList.remove(charUuid);

        if (!device.setMetadata(METADATA_GTBS_CCCD,
                Utils.uuidsToByteArray(uuidList.toArray(new ParcelUuid[0])))) {
            Log.e(TAG, "Can't set CCCD for GTBS characteristic UUID: " + charUuid + ", (remove)");
        }
    }

    private void addUuidToMetadata(ParcelUuid charUuid, BluetoothDevice device) {
        List<ParcelUuid> uuidList;
        byte[] gtbs_cccd = device.getMetadata(METADATA_GTBS_CCCD);

        if ((gtbs_cccd == null) || (gtbs_cccd.length == 0)) {
            uuidList = new ArrayList<ParcelUuid>();
        } else {
            uuidList = new ArrayList<>(Arrays.asList(Utils.byteArrayToUuid(gtbs_cccd)));

            if (uuidList.contains(charUuid)) {
                Log.d(TAG, "Characteristic CCCD already add: " + charUuid.toString());
                return;
            }
        }

        uuidList.add(charUuid);

        if (!device.setMetadata(METADATA_GTBS_CCCD,
                Utils.uuidsToByteArray(uuidList.toArray(new ParcelUuid[0])))) {
            Log.e(TAG, "Can't set CCCD for GTBS characteristic UUID: " + charUuid + ", (add)");
        }
    }

    /** Class that handles GATT characteristic notifications */
    private class BluetoothGattCharacteristicNotifier {
        public int setSubscriptionConfiguration(BluetoothDevice device, byte[] configuration) {
            if (Arrays.equals(configuration, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                mSubscribers.remove(device);
            } else if (!isSubscribed(device)) {
                mSubscribers.add(device);
            }

            return BluetoothGatt.GATT_SUCCESS;
        }

        public byte[] getSubscriptionConfiguration(BluetoothDevice device) {
            if (isSubscribed(device)) {
                return BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
            }

            return BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
        }

        public boolean isSubscribed(BluetoothDevice device) {
            return mSubscribers.contains(device);
        }

        private void notifyCharacteristicChanged(BluetoothDevice device,
                BluetoothGattCharacteristic characteristic) {
            if (mBluetoothGattServer != null) {
                mBluetoothGattServer.notifyCharacteristicChanged(device, characteristic, false);
            }
        }

        public void notify(BluetoothDevice device, BluetoothGattCharacteristic characteristic) {
            if (isSubscribed(device)) {
                notifyCharacteristicChanged(device, characteristic);
            }
        }

        public void notifyAll(BluetoothGattCharacteristic characteristic) {
            for (BluetoothDevice device : mSubscribers) {
                notifyCharacteristicChanged(device, characteristic);
            }
        }
    }

    /** Wrapper class for BluetoothGattCharacteristic */
    private class GattCharacteristic extends BluetoothGattCharacteristic {

        protected BluetoothGattCharacteristicNotifier mNotifier;

        public GattCharacteristic(UUID uuid, int properties, int permissions) {
            super(uuid, properties, permissions);
            if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                mNotifier = new BluetoothGattCharacteristicNotifier();
                addDescriptor(new ClientCharacteristicConfigurationDescriptor());
            } else {
                mNotifier = null;
            }
        }

        public byte[] getSubscriptionConfiguration(BluetoothDevice device) {
            return mNotifier.getSubscriptionConfiguration(device);
        }

        public int setSubscriptionConfiguration(BluetoothDevice device, byte[] configuration) {
            return mNotifier.setSubscriptionConfiguration(device, configuration);
        }

        private boolean isNotifiable() {
            return mNotifier != null;
        }

        @Override
        public boolean setValue(byte[] value) {
            boolean success = super.setValue(value);
            if (success && isNotifiable()) {
                mNotifier.notifyAll(this);
            }

            return success;
        }

        @Override
        public boolean setValue(int value, int formatType, int offset) {
            boolean success = super.setValue(value, formatType, offset);
            if (success && isNotifiable()) {
                mNotifier.notifyAll(this);
            }

            return success;
        }

        @Override
        public boolean setValue(String value) {
            boolean success = super.setValue(value);
            if (success && isNotifiable()) {
                mNotifier.notifyAll(this);
            }

            return success;
        }

        public boolean setValueNoNotify(byte[] value) {
            return super.setValue(value);
        }

        public boolean clearValue(boolean notify) {
            boolean success = super.setValue(new byte[0]);
            if (success && notify && isNotifiable()) {
                mNotifier.notifyAll(this);
            }

            return success;
        }

        public void handleWriteRequest(BluetoothDevice device, int requestId,
                boolean responseNeeded, byte[] value) {
            if (responseNeeded) {
                mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0,
                        value);
            }
        }
    }

    private class CallControlPointCharacteristic extends GattCharacteristic {

        public CallControlPointCharacteristic() {
            super(UUID_CALL_CONTROL_POINT,
                    PROPERTY_WRITE | PROPERTY_WRITE_NO_RESPONSE | PROPERTY_NOTIFY,
                    PERMISSION_WRITE_ENCRYPTED);
        }

        @Override
        public void handleWriteRequest(BluetoothDevice device, int requestId,
                boolean responseNeeded, byte[] value) {
            int status;
            if (value.length == 0) {
                // at least opcode is required
                status = BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH;
            } else {
                status = BluetoothGatt.GATT_SUCCESS;
            }

            if (responseNeeded) {
                mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0,
                        value);
            }

            int opcode = (int) value[0];
            mCallback.onCallControlPointRequest(device, opcode,
                    Arrays.copyOfRange(value, 1, value.length));
        }

        public void setResult(BluetoothDevice device, int requestedOpcode, int callIndex,
                int requestResult) {
            byte[] value = new byte[3];
            value[0] = (byte) (requestedOpcode);
            value[1] = (byte) (callIndex);
            value[2] = (byte) (requestResult);

            super.setValueNoNotify(value);

            // to avoid sending control point notification before write response
            mHandler.post(() -> mNotifier.notify(device, this));
        }
    }

    private class ClientCharacteristicConfigurationDescriptor extends BluetoothGattDescriptor {

        ClientCharacteristicConfigurationDescriptor() {
            super(UUID_CLIENT_CHARACTERISTIC_CONFIGURATION,
                    PERMISSION_READ | PERMISSION_WRITE_ENCRYPTED);
        }

        public byte[] getValue(BluetoothDevice device) {
            GattCharacteristic characteristic = (GattCharacteristic) getCharacteristic();
            byte value[] = characteristic.getSubscriptionConfiguration(device);
            if (value == null) {
                return BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
            }

            return value;
        }

        public int setValue(BluetoothDevice device, byte[] value) {
            GattCharacteristic characteristic = (GattCharacteristic) getCharacteristic();
            int properties = characteristic.getProperties();

            if (value.length != 2) {
                return BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH;

            } else if ((!Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    && !Arrays.equals(value, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                    && !Arrays.equals(value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE))
                    || ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0 && Arrays
                            .equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE))
                    || ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) == 0 && Arrays
                            .equals(value, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE))) {
                return BluetoothGatt.GATT_FAILURE;
            }

            if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                addUuidToMetadata(new ParcelUuid(characteristic.getUuid()), device);
            } else if (Arrays.equals(value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                removeUuidFromMetadata(new ParcelUuid(characteristic.getUuid()), device);
            } else {
                Log.e(TAG, "Not handled CCC value: " + Arrays.toString(value));
            }

            return characteristic.setSubscriptionConfiguration(device, value);
        }
    }

    public boolean setBearerProviderName(String providerName) {
        return mBearerProviderNameCharacteristic.setValue(providerName);
    }

    public boolean setBearerTechnology(int technology) {
        return mBearerTechnologyCharacteristic.setValue(technology,
                BluetoothGattCharacteristic.FORMAT_UINT8, 0);
    }

    public boolean setBearerUriSchemesSupportedList(List<String> bearerUriSchemesSupportedList) {
        return mBearerUriSchemesSupportedListCharacteristic
                .setValue(String.join(",", bearerUriSchemesSupportedList));
    }

    public boolean setCallState(Map<Integer, TbsCall> callsList) {
        if (DBG) {
            Log.d(TAG, "setCallState: callsList=" + callsList);
        }
        int i = 0;
        byte[] value = new byte[callsList.size() * 3];
        for (Map.Entry<Integer, TbsCall> entry : callsList.entrySet()) {
            TbsCall call = entry.getValue();
            value[i++] = (byte) (entry.getKey() & 0xff);
            value[i++] = (byte) (call.getState() & 0xff);
            value[i++] = (byte) (call.getFlags() & 0xff);
        }

        return mCallStateCharacteristic.setValue(value);
    }

    public boolean setBearerListCurrentCalls(Map<Integer, TbsCall> callsList) {
        if (DBG) {
            Log.d(TAG, "setBearerListCurrentCalls: callsList=" + callsList);
        }
        final int listItemLengthMax = Byte.MAX_VALUE;

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        for (Map.Entry<Integer, TbsCall> entry : callsList.entrySet()) {
            TbsCall call = entry.getValue();
            if (call == null) {
                Log.w(TAG, "setBearerListCurrentCalls: call is null");
                continue;
            }

            int uri_len = 0;
            if (call.getUri() != null) {
                uri_len =  call.getUri().getBytes().length;
            }

            int listItemLength = Math.min(listItemLengthMax, 3 + uri_len);
            stream.write((byte) (listItemLength & 0xff));
            stream.write((byte) (entry.getKey() & 0xff));
            stream.write((byte) (call.getState() & 0xff));
            stream.write((byte) (call.getFlags() & 0xff));
            if (uri_len > 0) {
                stream.write(call.getUri().getBytes(), 0, listItemLength - 3);
            }
        }

        return mBearerListCurrentCallsCharacteristic.setValue(stream.toByteArray());
    }

    private boolean updateStatusFlags(int flag, boolean set) {
        Integer valueInt = mStatusFlagsCharacteristic
                .getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);

        if (((valueInt & flag) != 0) == set) {
            return false;
        }

        valueInt ^= flag;

        return mStatusFlagsCharacteristic.setValue(valueInt,
                BluetoothGattCharacteristic.FORMAT_UINT16, 0);
    }

    public boolean setInbandRingtoneFlag() {
        return updateStatusFlags(STATUS_FLAG_INBAND_RINGTONE_ENABLED, true);
    }

    public boolean clearInbandRingtoneFlag() {
        return updateStatusFlags(STATUS_FLAG_INBAND_RINGTONE_ENABLED, false);
    }

    public boolean setSilentModeFlag() {
        return updateStatusFlags(STATUS_FLAG_SILENT_MODE_ENABLED, true);
    }

    public boolean clearSilentModeFlag() {
        return updateStatusFlags(STATUS_FLAG_SILENT_MODE_ENABLED, false);
    }

    private void setCallControlPointOptionalOpcodes(boolean isLocalHoldOpcodeSupported,
            boolean isJoinOpcodeSupported) {
        int valueInt = 0;
        if (isLocalHoldOpcodeSupported) {
            valueInt |= CALL_CONTROL_POINT_OPTIONAL_OPCODE_LOCAL_HOLD;
        }
        if (isJoinOpcodeSupported) {
            valueInt |= CALL_CONTROL_POINT_OPTIONAL_OPCODE_JOIN;
        }

        byte[] value = new byte[2];
        value[0] = (byte) (valueInt & 0xff);
        value[1] = (byte) ((valueInt >> 8) & 0xff);

        mCallControlPointOptionalOpcodesCharacteristic.setValue(value);
    }

    public boolean setTerminationReason(int callIndex, int terminationReason) {
        if (DBG) {
            Log.d(TAG, "setTerminationReason: callIndex=" + callIndex + " terminationReason="
                    + terminationReason);
        }
        byte[] value = new byte[2];
        value[0] = (byte) (callIndex & 0xff);
        value[1] = (byte) (terminationReason & 0xff);

        return mTerminationReasonCharacteristic.setValue(value);
    }

    public Integer getIncomingCallIndex() {
        byte[] value = mIncomingCallCharacteristic.getValue();
        if (value == null || value.length == 0) {
            return null;
        }

        return (int) value[0];
    }

    public boolean setIncomingCall(int callIndex, String uri) {
        if (DBG) {
            Log.d(TAG, "setIncomingCall: callIndex=" + callIndex + " uri=" + uri);
        }
        int uri_len = 0;
        if (uri != null) {
            uri_len = uri.length();
        }

        byte[] value = new byte[uri_len + 1];
        value[0] = (byte) (callIndex & 0xff);

        if (uri_len > 0) {
            System.arraycopy(uri.getBytes(), 0, value, 1, uri_len);
        }

        return mIncomingCallCharacteristic.setValue(value);
    }

    public boolean clearIncomingCall() {
        if (DBG) {
            Log.d(TAG, "clearIncomingCall");
        }
        return mIncomingCallCharacteristic.clearValue(false);
    }

    public boolean setCallFriendlyName(int callIndex, String callFriendlyName) {
        if (DBG) {
            Log.d(TAG, "setCallFriendlyName: callIndex=" + callIndex + "callFriendlyName="
                    + callFriendlyName);
        }
        byte[] value = new byte[callFriendlyName.length() + 1];
        value[0] = (byte) (callIndex & 0xff);
        System.arraycopy(callFriendlyName.getBytes(), 0, value, 1, callFriendlyName.length());

        return mCallFriendlyNameCharacteristic.setValue(value);
    }

    public Integer getCallFriendlyNameIndex() {
        byte[] value = mCallFriendlyNameCharacteristic.getValue();
        if (value == null || value.length == 0) {
            return null;
        }

        return (int) value[0];
    }

    public boolean clearFriendlyName() {
        if (DBG) {
            Log.d(TAG, "clearFriendlyName");
        }
        return mCallFriendlyNameCharacteristic.clearValue(false);
    }

    public void setCallControlPointResult(BluetoothDevice device, int requestedOpcode,
            int callIndex, int requestResult) {
        if (DBG) {
            Log.d(TAG,
                    "setCallControlPointResult: device=" + device + " requestedOpcode="
                            + requestedOpcode + " callIndex=" + callIndex + " requesuResult="
                            + requestResult);
        }
        mCallControlPointCharacteristic.setResult(device, requestedOpcode, callIndex,
                requestResult);
    }

    private static UUID makeUuid(String uuid16) {
        return UUID.fromString(UUID_PREFIX + uuid16 + UUID_SUFFIX);
    }

    private void restoreCccValuesForStoredDevices() {
        BluetoothGattService gattService = mBluetoothGattServer.getService(UUID_GTBS);

        for (BluetoothDevice device : mAdapterService.getBondedDevices()) {
            byte[] gtbs_cccd = device.getMetadata(METADATA_GTBS_CCCD);

            if ((gtbs_cccd == null) || (gtbs_cccd.length == 0)) {
                return;
            }

            List<ParcelUuid> uuidList = Arrays.asList(Utils.byteArrayToUuid(gtbs_cccd));

            /* Restore CCCD values for device */
            for (ParcelUuid uuid : uuidList) {
                BluetoothGattCharacteristic characteristic =
                        gattService.getCharacteristic(uuid.getUuid());
                if (characteristic == null) {
                    Log.e(TAG, "Invalid UUID stored in metadata: " + uuid.toString());
                    continue;
                }

                BluetoothGattDescriptor descriptor =
                        characteristic.getDescriptor(UUID_CLIENT_CHARACTERISTIC_CONFIGURATION);
                if (descriptor == null) {
                    Log.e(TAG, "Invalid characteristic, does not include CCCD");
                    continue;
                }

                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                mSubscribers.add(device);
            }
        }
    }

    private final IBluetoothStateChangeCallback mBluetoothStateChangeCallback =
            new IBluetoothStateChangeCallback.Stub() {
                public void onBluetoothStateChange(boolean up) {
                    if (DBG) Log.d(TAG, "onBluetoothStateChange: up=" + up);
                    if (up) {
                        restoreCccValuesForStoredDevices();
                    }
                }
            };

    /**
     * Callback to handle incoming requests to the GATT server. All read/write requests for
     * characteristics and descriptors are handled here.
     */
    @VisibleForTesting
    final BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            if (DBG) {
                Log.d(TAG, "onServiceAdded: status=" + status);
            }
            if (mCallback != null) {
                mCallback.onServiceAdded(status == BluetoothGatt.GATT_SUCCESS);
            }

            restoreCccValuesForStoredDevices();
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                BluetoothGattCharacteristic characteristic) {
            if (DBG) {
                Log.d(TAG, "onCharacteristicReadRequest: device=" + device);
            }
            GattCharacteristic gattCharacteristic = (GattCharacteristic) characteristic;
            byte[] value = gattCharacteristic.getValue();
            if (value == null) {
                value = new byte[0];
            }

            int status;
            if (value.length < offset) {
                status = BluetoothGatt.GATT_INVALID_OFFSET;
            } else {
                value = Arrays.copyOfRange(value, offset, value.length);
                status = BluetoothGatt.GATT_SUCCESS;
            }

            mBluetoothGattServer.sendResponse(device, requestId, status, offset, value);
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                BluetoothGattCharacteristic characteristic, boolean preparedWrite,
                boolean responseNeeded, int offset, byte[] value) {
            if (DBG) {
                Log.d(TAG, "onCharacteristicWriteRequest: device=" + device);
            }
            GattCharacteristic gattCharacteristic = (GattCharacteristic) characteristic;
            int status;
            if (preparedWrite) {
                status = BluetoothGatt.GATT_FAILURE;
            } else if (offset > 0) {
                status = BluetoothGatt.GATT_INVALID_OFFSET;
            } else {
                gattCharacteristic.handleWriteRequest(device, requestId, responseNeeded, value);
                return;
            }

            if (responseNeeded) {
                mBluetoothGattServer.sendResponse(device, requestId, status, offset, value);
            }
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset,
                BluetoothGattDescriptor descriptor) {
            if (DBG) {
                Log.d(TAG, "onDescriptorReadRequest: device=" + device);
            }
            ClientCharacteristicConfigurationDescriptor cccd =
                    (ClientCharacteristicConfigurationDescriptor) descriptor;
            byte[] value = cccd.getValue(device);
            int status;
            if (value.length < offset) {
                status = BluetoothGatt.GATT_INVALID_OFFSET;
            } else {
                value = Arrays.copyOfRange(value, offset, value.length);
                status = BluetoothGatt.GATT_SUCCESS;
            }

            mBluetoothGattServer.sendResponse(device, requestId, status, offset, value);
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded,
                int offset, byte[] value) {
            if (DBG) {
                Log.d(TAG, "onDescriptorWriteRequest: device=" + device);
            }
            ClientCharacteristicConfigurationDescriptor cccd =
                    (ClientCharacteristicConfigurationDescriptor) descriptor;
            int status;
            if (preparedWrite) {
                // TODO: handle prepareWrite
                status = BluetoothGatt.GATT_FAILURE;
            } else if (offset > 0) {
                status = BluetoothGatt.GATT_INVALID_OFFSET;
            } else if (value.length != 2) {
                status = BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH;
            } else {
                status = cccd.setValue(device, value);
            }

            if (responseNeeded) {
                mBluetoothGattServer.sendResponse(device, requestId, status, offset, value);
            }
        }
    };
}
