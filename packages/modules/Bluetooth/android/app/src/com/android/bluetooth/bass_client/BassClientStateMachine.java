/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.bass_client;

import static android.Manifest.permission.BLUETOOTH_CONNECT;

import android.annotation.Nullable;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothLeAudioCodecConfigMetadata;
import android.bluetooth.BluetoothLeAudioContentMetadata;
import android.bluetooth.BluetoothLeBroadcastAssistant;
import android.bluetooth.BluetoothLeBroadcastChannel;
import android.bluetooth.BluetoothLeBroadcastMetadata;
import android.bluetooth.BluetoothLeBroadcastReceiveState;
import android.bluetooth.BluetoothLeBroadcastSubgroup;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.le.PeriodicAdvertisingCallback;
import android.bluetooth.le.PeriodicAdvertisingManager;
import android.bluetooth.le.PeriodicAdvertisingReport;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.os.Binder;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.provider.DeviceConfig;
import android.util.Log;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.btservice.ServiceFactory;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import java.util.stream.IntStream;

@VisibleForTesting
public class BassClientStateMachine extends StateMachine {
    private static final String TAG = "BassClientStateMachine";
    @VisibleForTesting
    static final byte[] REMOTE_SCAN_STOP = {00};
    @VisibleForTesting
    static final byte[] REMOTE_SCAN_START = {01};
    private static final byte OPCODE_ADD_SOURCE = 0x02;
    private static final byte OPCODE_UPDATE_SOURCE = 0x03;
    private static final byte OPCODE_SET_BCAST_PIN = 0x04;
    private static final byte OPCODE_REMOVE_SOURCE = 0x05;
    private static final int ADD_SOURCE_FIXED_LENGTH = 16;
    private static final int UPDATE_SOURCE_FIXED_LENGTH = 6;

    static final int CONNECT = 1;
    static final int DISCONNECT = 2;
    static final int CONNECTION_STATE_CHANGED = 3;
    static final int GATT_TXN_PROCESSED = 4;
    static final int READ_BASS_CHARACTERISTICS = 5;
    static final int START_SCAN_OFFLOAD = 6;
    static final int STOP_SCAN_OFFLOAD = 7;
    static final int SELECT_BCAST_SOURCE = 8;
    static final int ADD_BCAST_SOURCE = 9;
    static final int UPDATE_BCAST_SOURCE = 10;
    static final int SET_BCAST_CODE = 11;
    static final int REMOVE_BCAST_SOURCE = 12;
    static final int GATT_TXN_TIMEOUT = 13;
    static final int PSYNC_ACTIVE_TIMEOUT = 14;
    static final int CONNECT_TIMEOUT = 15;

    // NOTE: the value is not "final" - it is modified in the unit tests
    @VisibleForTesting
    private int mConnectTimeoutMs;

    /*key is combination of sourceId, Address and advSid for this hashmap*/
    private final Map<Integer, BluetoothLeBroadcastReceiveState>
            mBluetoothLeBroadcastReceiveStates =
            new HashMap<Integer, BluetoothLeBroadcastReceiveState>();
    private final Map<Integer, BluetoothLeBroadcastMetadata> mCurrentMetadata = new HashMap();
    private final Disconnected mDisconnected = new Disconnected();
    private final Connected mConnected = new Connected();
    private final Connecting mConnecting = new Connecting();
    private final ConnectedProcessing mConnectedProcessing = new ConnectedProcessing();
    @VisibleForTesting
    final List<BluetoothGattCharacteristic> mBroadcastCharacteristics =
            new ArrayList<BluetoothGattCharacteristic>();
    @VisibleForTesting
    BluetoothDevice mDevice;

    private boolean mIsAllowedList = false;
    private int mLastConnectionState = -1;
    @VisibleForTesting
    boolean mMTUChangeRequested = false;
    @VisibleForTesting
    boolean mDiscoveryInitiated = false;
    @VisibleForTesting
    BassClientService mService;
    @VisibleForTesting
    BluetoothGattCharacteristic mBroadcastScanControlPoint;
    private boolean mFirstTimeBisDiscovery = false;
    private int mPASyncRetryCounter = 0;
    private ScanResult mScanRes = null;
    @VisibleForTesting
    int mNumOfBroadcastReceiverStates = 0;
    private BluetoothAdapter mBluetoothAdapter =
            BluetoothAdapter.getDefaultAdapter();
    private ServiceFactory mFactory = new ServiceFactory();
    @VisibleForTesting
    int mPendingOperation = -1;
    @VisibleForTesting
    byte mPendingSourceId = -1;
    @VisibleForTesting
    BluetoothLeBroadcastMetadata mPendingMetadata = null;
    private BluetoothLeBroadcastReceiveState mSetBroadcastPINRcvState = null;
    @VisibleForTesting
    boolean mSetBroadcastCodePending = false;
    private final Map<Integer, Boolean> mPendingRemove = new HashMap();
    // Psync and PAST interfaces
    private PeriodicAdvertisingManager mPeriodicAdvManager;
    private boolean mAutoAssist = false;
    @VisibleForTesting
    boolean mAutoTriggered = false;
    @VisibleForTesting
    boolean mNoStopScanOffload = false;
    private boolean mDefNoPAS = false;
    private boolean mForceSB = false;
    private int mBroadcastSourceIdLength = 3;
    @VisibleForTesting
    byte mNextSourceId = 0;
    private boolean mAllowReconnect = false;
    @VisibleForTesting
    BluetoothGattTestableWrapper mBluetoothGatt = null;
    BluetoothGattCallback mGattCallback = null;

    BassClientStateMachine(BluetoothDevice device, BassClientService svc, Looper looper,
            int connectTimeoutMs) {
        super(TAG + "(" + device.toString() + ")", looper);
        mDevice = device;
        mService = svc;
        mConnectTimeoutMs = connectTimeoutMs;
        addState(mDisconnected);
        addState(mConnected);
        addState(mConnecting);
        addState(mConnectedProcessing);
        setInitialState(mDisconnected);
        // PSYNC and PAST instances
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter != null) {
            mPeriodicAdvManager = mBluetoothAdapter.getPeriodicAdvertisingManager();
        }
        long token = Binder.clearCallingIdentity();
        mIsAllowedList = DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_BLUETOOTH,
                "persist.vendor.service.bt.wl", true);
        mDefNoPAS = DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_BLUETOOTH,
                "persist.vendor.service.bt.defNoPAS", false);
        mForceSB = DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_BLUETOOTH,
                "persist.vendor.service.bt.forceSB", false);
        Binder.restoreCallingIdentity(token);
    }

    static BassClientStateMachine make(BluetoothDevice device,
            BassClientService svc, Looper looper) {
        Log.d(TAG, "make for device " + device);
        BassClientStateMachine BassclientSm = new BassClientStateMachine(device, svc, looper,
                BassConstants.CONNECT_TIMEOUT_MS);
        BassclientSm.start();
        return BassclientSm;
    }

    static void destroy(BassClientStateMachine stateMachine) {
        Log.i(TAG, "destroy");
        if (stateMachine == null) {
            Log.w(TAG, "destroy(), stateMachine is null");
            return;
        }
        stateMachine.doQuit();
        stateMachine.cleanup();
    }

    public void doQuit() {
        log("doQuit for device " + mDevice);
        quitNow();
    }

    public void cleanup() {
        log("cleanup for device " + mDevice);
        clearCharsCache();

        if (mBluetoothGatt != null) {
            log("disconnect gatt");
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
            mBluetoothGatt = null;
            mGattCallback = null;
        }
        mPendingOperation = -1;
        mPendingSourceId = -1;
        mPendingMetadata = null;
        mCurrentMetadata.clear();
        mPendingRemove.clear();
    }

    Boolean hasPendingSourceOperation() {
        return mPendingMetadata != null;
    }

    BluetoothLeBroadcastMetadata getCurrentBroadcastMetadata(Integer sourceId) {
        return mCurrentMetadata.getOrDefault(sourceId, null);
    }

    private void setCurrentBroadcastMetadata(Integer sourceId,
            BluetoothLeBroadcastMetadata metadata) {
        if (metadata != null) {
            mCurrentMetadata.put(sourceId, metadata);
        } else {
            mCurrentMetadata.remove(sourceId);
        }
    }

    boolean isPendingRemove(Integer sourceId) {
        return mPendingRemove.getOrDefault(sourceId, false);
    }

    private void setPendingRemove(Integer sourceId, boolean remove) {
        if (remove) {
            mPendingRemove.put(sourceId, remove);
        } else {
            mPendingRemove.remove(sourceId);
        }
    }

    BluetoothLeBroadcastReceiveState getBroadcastReceiveStateForSourceDevice(
            BluetoothDevice srcDevice) {
        List<BluetoothLeBroadcastReceiveState> currentSources = getAllSources();
        BluetoothLeBroadcastReceiveState state = null;
        for (int i = 0; i < currentSources.size(); i++) {
            BluetoothDevice device = currentSources.get(i).getSourceDevice();
            if (device != null && device.equals(srcDevice)) {
                state = currentSources.get(i);
                Log.e(TAG,
                        "getBroadcastReceiveStateForSourceDevice: returns for: "
                                + srcDevice + "&srcInfo" + state);
                return state;
            }
        }
        return null;
    }

    BluetoothLeBroadcastReceiveState getBroadcastReceiveStateForSourceId(int sourceId) {
        List<BluetoothLeBroadcastReceiveState> currentSources = getAllSources();
        for (int i = 0; i < currentSources.size(); i++) {
            if (sourceId == currentSources.get(i).getSourceId()) {
                return currentSources.get(i);
            }
        }
        return null;
    }

    void parseBaseData(BluetoothDevice device, int syncHandle, byte[] serviceData) {
        log("parseBaseData" + Arrays.toString(serviceData));
        BaseData base = BaseData.parseBaseData(serviceData);
        if (base != null) {
            mService.updateBase(syncHandle, base);
            base.print();
            if (mAutoTriggered) {
                // successful auto periodic synchrnization with source
                log("auto triggered assist");
                mAutoTriggered = false;
                // perform PAST with this device
                BluetoothDevice srcDevice = mService.getDeviceForSyncHandle(syncHandle);
                if (srcDevice != null) {
                    BluetoothLeBroadcastReceiveState recvState =
                            getBroadcastReceiveStateForSourceDevice(srcDevice);
                    processPASyncState(recvState);
                } else {
                    Log.w(TAG, "Autoassist: no matching device");
                }
            }
        } else {
            Log.e(TAG, "Seems BASE is not in parsable format");
            if (!mAutoTriggered) {
                BluetoothDevice srcDevice = mService.getDeviceForSyncHandle(syncHandle);
                cancelActiveSync(srcDevice);
            } else {
                mAutoTriggered = false;
            }
        }
    }

    void parseScanRecord(int syncHandle, ScanRecord record) {
        log("parseScanRecord" + record);
        BluetoothDevice srcDevice = mService.getDeviceForSyncHandle(syncHandle);
        Map<ParcelUuid, byte[]> bmsAdvDataMap = record.getServiceData();
        if (bmsAdvDataMap != null) {
            for (Map.Entry<ParcelUuid, byte[]> entry : bmsAdvDataMap.entrySet()) {
                log("ParcelUUid = " + entry.getKey() + ", Value = "
                        + Arrays.toString(entry.getValue()));
            }
        }
        byte[] advData = record.getServiceData(BassConstants.BASIC_AUDIO_UUID);
        if (advData != null) {
            parseBaseData(mDevice, syncHandle, advData);
        } else {
            Log.e(TAG, "No service data in Scan record");
            if (!mAutoTriggered) {
                cancelActiveSync(srcDevice);
            } else {
                mAutoTriggered = false;
            }
        }
    }

    private boolean selectSource(
            ScanResult scanRes, boolean autoTriggered) {
        log("selectSource: ScanResult " + scanRes);
        mAutoTriggered = autoTriggered;
        mFirstTimeBisDiscovery = true;
        mPASyncRetryCounter = 1;
        // Cache Scan res for Retrys
        mScanRes = scanRes;
        /*This is an override case if Previous sync is still active, cancel It, but don't stop the
         * Scan offload as we still trying to assist remote
         */
        mNoStopScanOffload = true;
        cancelActiveSync(null);
        try {
            BluetoothMethodProxy.getInstance().periodicAdvertisingManagerRegisterSync(
                    mPeriodicAdvManager, scanRes, 0, BassConstants.PSYNC_TIMEOUT,
                    mPeriodicAdvCallback, null);
        } catch (IllegalArgumentException ex) {
            Log.w(TAG, "registerSync:IllegalArgumentException");
            Message message = obtainMessage(STOP_SCAN_OFFLOAD);
            sendMessage(message);
            return false;
        }
        // updating mainly for Address type and PA Interval here
        // extract BroadcastId from ScanResult
        ScanRecord scanRecord = scanRes.getScanRecord();
        if (scanRecord != null) {
            Map<ParcelUuid, byte[]> listOfUuids = scanRecord.getServiceData();
            int broadcastId = BassConstants.INVALID_BROADCAST_ID;
            if (listOfUuids != null) {
                if (listOfUuids.containsKey(BassConstants.BAAS_UUID)) {
                    byte[] bId = listOfUuids.get(BassConstants.BAAS_UUID);
                    broadcastId = BassUtils.parseBroadcastId(bId);
                }
            }
            mService.updatePeriodicAdvertisementResultMap(
                    scanRes.getDevice(),
                    scanRes.getDevice().getAddressType(),
                    BassConstants.INVALID_SYNC_HANDLE,
                    BassConstants.INVALID_ADV_SID,
                    scanRes.getPeriodicAdvertisingInterval(),
                    broadcastId);
        }
        return true;
    }

    private void cancelActiveSync(BluetoothDevice sourceDev) {
        log("cancelActiveSync");
        BluetoothDevice activeSyncedSrc = mService.getActiveSyncedSource(mDevice);

        /* Stop sync if there is some running */
        if (activeSyncedSrc != null && (sourceDev == null || activeSyncedSrc.equals(sourceDev))) {
            removeMessages(PSYNC_ACTIVE_TIMEOUT);
            try {
                log("calling unregisterSync");
                mPeriodicAdvManager.unregisterSync(mPeriodicAdvCallback);
            } catch (IllegalArgumentException ex) {
                Log.w(TAG, "unregisterSync:IllegalArgumentException");
            }
            mService.clearPeriodicAdvertisementResult(activeSyncedSrc);
            mService.setActiveSyncedSource(mDevice, null);
            if (!mNoStopScanOffload) {
                // trigger scan stop here
                Message message = obtainMessage(STOP_SCAN_OFFLOAD);
                sendMessage(message);
            }
        }
        mNoStopScanOffload = false;
    }

    private BluetoothLeBroadcastMetadata getBroadcastMetadataFromBaseData(BaseData baseData,
            BluetoothDevice device) {
        BluetoothLeBroadcastMetadata.Builder metaData =
                new BluetoothLeBroadcastMetadata.Builder();
        int index = 0;
        for (BaseData.BaseInformation baseLevel2 : baseData.getLevelTwo()) {
            BluetoothLeBroadcastSubgroup.Builder subGroup =
                    new BluetoothLeBroadcastSubgroup.Builder();
            for (int j = 0; j < baseLevel2.numSubGroups; j ++) {
                BaseData.BaseInformation baseLevel3 =
                        baseData.getLevelThree().get(index++);
                BluetoothLeBroadcastChannel.Builder channel =
                        new BluetoothLeBroadcastChannel.Builder();
                channel.setChannelIndex(baseLevel3.index);
                channel.setCodecMetadata(BluetoothLeAudioCodecConfigMetadata.
                        fromRawBytes(baseLevel3.codecConfigInfo));
                channel.setSelected(false);
                subGroup.addChannel(channel.build());
            }
            byte[] arrayCodecId = baseLevel2.codecId;
            long codeId = (long) ((arrayCodecId[4] & 0xff) << 32
                    | (arrayCodecId[3] & 0xff) << 24
                    | (arrayCodecId[2] & 0xff) << 16
                    | (arrayCodecId[1] & 0xff) << 8
                    | (arrayCodecId[0] & 0xff));
            subGroup.setCodecId(codeId);
            subGroup.setCodecSpecificConfig(BluetoothLeAudioCodecConfigMetadata.
                    fromRawBytes(baseLevel2.codecConfigInfo));
            subGroup.setContentMetadata(BluetoothLeAudioContentMetadata.
                    fromRawBytes(baseLevel2.metaData));
            metaData.addSubgroup(subGroup.build());
        }
        metaData.setSourceDevice(device, device.getAddressType());
        byte[] arrayPresentationDelay = baseData.getLevelOne().presentationDelay;
        int presentationDelay = (int) ((arrayPresentationDelay[2] & 0xff) << 16
                | (arrayPresentationDelay[1] & 0xff)
                | (arrayPresentationDelay[0] & 0xff));
        metaData.setPresentationDelayMicros(presentationDelay);
        PeriodicAdvertisementResult result =
                mService.getPeriodicAdvertisementResult(device);
        if (result != null) {
            int broadcastId = result.getBroadcastId();
            log("broadcast ID: " + broadcastId);
            metaData.setBroadcastId(broadcastId);
            metaData.setSourceAdvertisingSid(result.getAdvSid());
        }
        return metaData.build();
    }

    /** Internal periodc Advertising manager callback */
    private PeriodicAdvertisingCallback mPeriodicAdvCallback =
            new PeriodicAdvertisingCallback() {
                @Override
                public void onSyncEstablished(
                        int syncHandle,
                        BluetoothDevice device,
                        int advertisingSid,
                        int skip,
                        int timeout,
                        int status) {
                    log("onSyncEstablished syncHandle: " + syncHandle
                            + ", device: " + device
                            + ", advertisingSid: " + advertisingSid
                            + ", skip: " + skip
                            + ", timeout: " + timeout
                            + ", status: " + status);
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        // updates syncHandle, advSid
                        mService.updatePeriodicAdvertisementResultMap(
                                device,
                                BassConstants.INVALID_ADV_ADDRESS_TYPE,
                                syncHandle,
                                advertisingSid,
                                BassConstants.INVALID_ADV_INTERVAL,
                                BassConstants.INVALID_BROADCAST_ID);
                        sendMessageDelayed(PSYNC_ACTIVE_TIMEOUT,
                                BassConstants.PSYNC_ACTIVE_TIMEOUT_MS);
                        mService.setActiveSyncedSource(mDevice, device);
                    } else {
                        log("failed to sync to PA: " + mPASyncRetryCounter);
                        mScanRes = null;
                        if (!mAutoTriggered) {
                            Message message = obtainMessage(STOP_SCAN_OFFLOAD);
                            sendMessage(message);
                        }
                        mAutoTriggered = false;
                    }
                }

                @Override
                public void onPeriodicAdvertisingReport(PeriodicAdvertisingReport report) {
                    log("onPeriodicAdvertisingReport");
                    // Parse the BIS indices from report's service data
                    if (mFirstTimeBisDiscovery) {
                        parseScanRecord(report.getSyncHandle(), report.getData());
                        BaseData baseData = mService.getBase(report.getSyncHandle());
                        if (baseData != null) {
                            BluetoothLeBroadcastMetadata metaData =
                                    getBroadcastMetadataFromBaseData(baseData,
                                            mService.getDeviceForSyncHandle(report.getSyncHandle()));
                            mService.getCallbacks().notifySourceFound(metaData);
                        }
                        mFirstTimeBisDiscovery = false;
                    }
                }

                @Override
                public void onSyncLost(int syncHandle) {
                    log("OnSyncLost" + syncHandle);
                    BluetoothDevice srcDevice = mService.getDeviceForSyncHandle(syncHandle);
                    cancelActiveSync(srcDevice);
                }
            };

    private void broadcastReceiverState(
            BluetoothLeBroadcastReceiveState state, int sourceId) {
        log("broadcastReceiverState: " + mDevice);
        mService.getCallbacks().notifyReceiveStateChanged(mDevice, sourceId, state);
    }

    @VisibleForTesting
    static boolean isEmpty(final byte[] data) {
        return IntStream.range(0, data.length).parallel().allMatch(i -> data[i] == 0);
    }

    private void processPASyncState(BluetoothLeBroadcastReceiveState recvState) {
        log("processPASyncState " + recvState);
        int serviceData = 0;
        if (recvState == null) {
            Log.e(TAG, "processPASyncState: recvState is null");
            return;
        }
        int state = recvState.getPaSyncState();
        if (state == BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_SYNCINFO_REQUEST) {
            log("Initiate PAST procedure");
            PeriodicAdvertisementResult result =
                    mService.getPeriodicAdvertisementResult(
                    recvState.getSourceDevice());
            if (result != null) {
                int syncHandle = result.getSyncHandle();
                log("processPASyncState: syncHandle " + result.getSyncHandle());
                if (syncHandle != BassConstants.INVALID_SYNC_HANDLE) {
                    serviceData = 0x000000FF & recvState.getSourceId();
                    serviceData = serviceData << 8;
                    //advA matches EXT_ADV_ADDRESS
                    //also matches source address (as we would have written)
                    serviceData = serviceData
                            & (~BassConstants.ADV_ADDRESS_DONT_MATCHES_EXT_ADV_ADDRESS);
                    serviceData = serviceData
                            & (~BassConstants.ADV_ADDRESS_DONT_MATCHES_SOURCE_ADV_ADDRESS);
                    log("Initiate PAST for: " + mDevice + ", syncHandle: " +  syncHandle
                            + "serviceData" + serviceData);
                    BluetoothMethodProxy.getInstance().periodicAdvertisingManagerTransferSync(
                            mPeriodicAdvManager, mDevice, serviceData, syncHandle);
                }
            } else {
                if (mService.isLocalBroadcast(mPendingMetadata)) {
                    int advHandle = mPendingMetadata.getSourceAdvertisingSid();
                    serviceData = 0x000000FF & recvState.getSourceId();
                    serviceData = serviceData << 8;
                    // Address we set in the Source Address can differ from the address in the air
                    serviceData = serviceData
                            | BassConstants.ADV_ADDRESS_DONT_MATCHES_SOURCE_ADV_ADDRESS;
                    log("Initiate local broadcast PAST for: " + mDevice
                            + ", advSID/Handle: " +  advHandle
                            + ", serviceData: " + serviceData);
                    BluetoothMethodProxy.getInstance().periodicAdvertisingManagerTransferSetInfo(
                            mPeriodicAdvManager, mDevice, serviceData, advHandle,
                            mPeriodicAdvCallback);
                } else {
                    Log.e(TAG, "There is no valid sync handle for this Source");
                    if (mAutoAssist) {
                        // Initiate Auto Assist procedure for this device
                        mService.getBassUtils().triggerAutoAssist(recvState);
                    }
                }
            }
        } else if (state == BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_SYNCHRONIZED
                || state == BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_NO_PAST) {
            Message message = obtainMessage(STOP_SCAN_OFFLOAD);
            sendMessage(message);
        }
    }

    private void checkAndUpdateBroadcastCode(BluetoothLeBroadcastReceiveState recvState) {
        log("checkAndUpdateBroadcastCode");
        // non colocated case, Broadcast PIN should have been updated from lyaer
        // If there is pending one process it Now
        if (recvState.getBigEncryptionState()
                == BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_CODE_REQUIRED
                && mSetBroadcastCodePending) {
            log("Update the Broadcast now");
            Message m = obtainMessage(BassClientStateMachine.SET_BCAST_CODE);

            /* Use cached receiver state if previousely didn't finished setting broadcast code or
             * use current receiver state if this is a first check and update
             */
            if (mSetBroadcastPINRcvState != null) {
                m.obj = mSetBroadcastPINRcvState;
            } else {
                m.obj = recvState;
            }

            sendMessage(m);
        }
    }

    private BluetoothLeBroadcastReceiveState parseBroadcastReceiverState(
            byte[] receiverState) {
        byte sourceId = 0;
        if (receiverState.length > 0) {
            sourceId = receiverState[BassConstants.BCAST_RCVR_STATE_SRC_ID_IDX];
        }
        log("processBroadcastReceiverState: receiverState length: " + receiverState.length);

        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothLeBroadcastReceiveState recvState = null;
        if (receiverState.length == 0
                || isEmpty(Arrays.copyOfRange(receiverState, 1, receiverState.length - 1))) {
            String emptyBluetoothDevice = "00:00:00:00:00:00";
            if (mPendingOperation == REMOVE_BCAST_SOURCE) {
                recvState = new BluetoothLeBroadcastReceiveState(mPendingSourceId,
                        BluetoothDevice.ADDRESS_TYPE_PUBLIC, // sourceAddressType
                        btAdapter.getRemoteDevice(emptyBluetoothDevice),  // sourceDevice
                        0,  // sourceAdvertisingSid
                        0,  // broadcastId
                        BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE, // paSyncState
                        // bigEncryptionState
                        BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                        null,   // badCode
                        0,  // numSubgroups
                        Arrays.asList(new Long[0]),   // bisSyncState
                        Arrays.asList(new BluetoothLeAudioContentMetadata[0])    // subgroupMetadata
                );
            } else if (receiverState.length == 0) {
                if (mBluetoothLeBroadcastReceiveStates != null) {
                    mNextSourceId = (byte) mBluetoothLeBroadcastReceiveStates.size();
                }
                if (mNextSourceId >= mNumOfBroadcastReceiverStates) {
                    Log.e(TAG, "reached the remote supported max SourceInfos");
                    return null;
                }
                mNextSourceId++;
                recvState = new BluetoothLeBroadcastReceiveState(mNextSourceId,
                        BluetoothDevice.ADDRESS_TYPE_PUBLIC, // sourceAddressType
                        btAdapter.getRemoteDevice(emptyBluetoothDevice),   // sourceDevice
                        0,  // sourceAdvertisingSid
                        0,  // broadcastId
                        BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE, // paSyncState
                        // bigEncryptionState
                        BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                        null,   // badCode
                        0,  // numSubgroups
                        Arrays.asList(new Long[0]),   // bisSyncState
                        Arrays.asList(new BluetoothLeAudioContentMetadata[0])    // subgroupMetadata
                );
            }
        } else {
            byte metaDataSyncState = receiverState[BassConstants.BCAST_RCVR_STATE_PA_SYNC_IDX];
            byte encryptionStatus = receiverState[BassConstants.BCAST_RCVR_STATE_ENC_STATUS_IDX];
            byte[] badBroadcastCode = null;
            int badBroadcastCodeLen = 0;
            if (encryptionStatus
                    == BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_BAD_CODE) {
                badBroadcastCode = new byte[BassConstants.BCAST_RCVR_STATE_BADCODE_SIZE];
                System.arraycopy(
                        receiverState,
                        BassConstants.BCAST_RCVR_STATE_BADCODE_START_IDX,
                        badBroadcastCode,
                        0,
                        BassConstants.BCAST_RCVR_STATE_BADCODE_SIZE);
                badBroadcastCodeLen = BassConstants.BCAST_RCVR_STATE_BADCODE_SIZE;
            }
            byte numSubGroups = receiverState[BassConstants.BCAST_RCVR_STATE_BADCODE_START_IDX
                    + badBroadcastCodeLen];
            int offset = BassConstants.BCAST_RCVR_STATE_BADCODE_START_IDX
                    + badBroadcastCodeLen + 1;
            ArrayList<BluetoothLeAudioContentMetadata> metadataList =
                    new ArrayList<BluetoothLeAudioContentMetadata>();
            ArrayList<Long> audioSyncState = new ArrayList<Long>();
            for (int i = 0; i < numSubGroups; i++) {
                byte[] audioSyncIndex = new byte[BassConstants.BCAST_RCVR_STATE_BIS_SYNC_SIZE];
                System.arraycopy(receiverState, offset, audioSyncIndex, 0,
                        BassConstants.BCAST_RCVR_STATE_BIS_SYNC_SIZE);
                offset += BassConstants.BCAST_RCVR_STATE_BIS_SYNC_SIZE;
                audioSyncState.add((long) Utils.byteArrayToInt(audioSyncIndex));

                byte metaDataLength = receiverState[offset++];
                if (metaDataLength > 0) {
                    log("metadata of length: " + metaDataLength + "is available");
                    byte[] metaData = new byte[metaDataLength];
                    System.arraycopy(receiverState, offset, metaData, 0, metaDataLength);
                    offset += metaDataLength;
                    metadataList.add(BluetoothLeAudioContentMetadata.fromRawBytes(metaData));
                } else {
                    metadataList.add(BluetoothLeAudioContentMetadata.fromRawBytes(new byte[0]));
                }
            }
            byte[] broadcastIdBytes = new byte[mBroadcastSourceIdLength];
            System.arraycopy(
                    receiverState,
                    BassConstants.BCAST_RCVR_STATE_SRC_BCAST_ID_START_IDX,
                    broadcastIdBytes,
                    0,
                    mBroadcastSourceIdLength);
            int broadcastId = BassUtils.parseBroadcastId(broadcastIdBytes);
            byte[] sourceAddress = new byte[BassConstants.BCAST_RCVR_STATE_SRC_ADDR_SIZE];
            System.arraycopy(
                    receiverState,
                    BassConstants.BCAST_RCVR_STATE_SRC_ADDR_START_IDX,
                    sourceAddress,
                    0,
                    BassConstants.BCAST_RCVR_STATE_SRC_ADDR_SIZE);
            byte sourceAddressType = receiverState[BassConstants
                    .BCAST_RCVR_STATE_SRC_ADDR_TYPE_IDX];
            BassUtils.reverse(sourceAddress);
            String address = Utils.getAddressStringFromByte(sourceAddress);
            BluetoothDevice device = btAdapter.getRemoteLeDevice(
                    address, sourceAddressType);
            byte sourceAdvSid = receiverState[BassConstants.BCAST_RCVR_STATE_SRC_ADV_SID_IDX];
            recvState = new BluetoothLeBroadcastReceiveState(
                    sourceId,
                    (int) sourceAddressType,
                    device,
                    sourceAdvSid,
                    broadcastId,
                    (int) metaDataSyncState,
                    (int) encryptionStatus,
                    badBroadcastCode,
                    numSubGroups,
                    audioSyncState,
                    metadataList);
            log("Receiver state: "
                    + "\n\tSource ID: " + sourceId
                    + "\n\tSource Address Type: " + (int) sourceAddressType
                    + "\n\tDevice: " + device
                    + "\n\tSource Adv SID: " + sourceAdvSid
                    + "\n\tBroadcast ID: " + broadcastId
                    + "\n\tMetadata Sync State: " + (int) metaDataSyncState
                    + "\n\tEncryption Status: " + (int) encryptionStatus
                    + "\n\tBad Broadcast Code: " + Arrays.toString(badBroadcastCode)
                    + "\n\tNumber Of Subgroups: " + numSubGroups
                    + "\n\tAudio Sync State: " + audioSyncState
                    + "\n\tMetadata: " + metadataList);
        }
        return recvState;
    }

    private void processBroadcastReceiverState(
            byte[] receiverState, BluetoothGattCharacteristic characteristic) {
        log("processBroadcastReceiverState: characteristic:" + characteristic);
        BluetoothLeBroadcastReceiveState recvState = parseBroadcastReceiverState(
                receiverState);
        if (recvState == null) {
            log("processBroadcastReceiverState: Null recvState");
            return;
        } else if (recvState.getSourceId() == -1) {
            log("processBroadcastReceiverState: invalid index: " + recvState.getSourceId());
            return;
        }
        BluetoothLeBroadcastReceiveState oldRecvState =
                mBluetoothLeBroadcastReceiveStates.get(characteristic.getInstanceId());
        if (oldRecvState == null) {
            log("Initial Read and Populating values");
            if (mBluetoothLeBroadcastReceiveStates.size() == mNumOfBroadcastReceiverStates) {
                Log.e(TAG, "reached the Max SourceInfos");
                return;
            }
            mBluetoothLeBroadcastReceiveStates.put(characteristic.getInstanceId(), recvState);
            checkAndUpdateBroadcastCode(recvState);
            processPASyncState(recvState);
        } else {
            log("old sourceInfo: " + oldRecvState);
            log("new sourceInfo: " + recvState);
            mBluetoothLeBroadcastReceiveStates.replace(characteristic.getInstanceId(), recvState);
            String emptyBluetoothDevice = "00:00:00:00:00:00";
            if (oldRecvState.getSourceDevice() == null
                    || oldRecvState.getSourceDevice().getAddress().equals(emptyBluetoothDevice)) {
                log("New Source Addition");
                mService.getCallbacks().notifySourceAdded(mDevice, recvState,
                        BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST);
                if (mPendingMetadata != null) {
                    setCurrentBroadcastMetadata(recvState.getSourceId(), mPendingMetadata);
                }
                checkAndUpdateBroadcastCode(recvState);
                processPASyncState(recvState);
            } else {
                if (recvState.getSourceDevice() == null
                        || recvState.getSourceDevice().getAddress().equals(emptyBluetoothDevice)) {
                    BluetoothDevice removedDevice = oldRecvState.getSourceDevice();
                    log("sourceInfo removal" + removedDevice);
                    cancelActiveSync(removedDevice);
                    setCurrentBroadcastMetadata(oldRecvState.getSourceId(), null);
                    mService.getCallbacks().notifySourceRemoved(mDevice,
                            oldRecvState.getSourceId(),
                            BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST);
                } else {
                    log("update to an existing recvState");
                    setCurrentBroadcastMetadata(recvState.getSourceId(), mPendingMetadata);
                    mService.getCallbacks().notifySourceModified(mDevice,
                            recvState.getSourceId(), BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST);
                    checkAndUpdateBroadcastCode(recvState);
                    processPASyncState(recvState);

                    if (isPendingRemove(recvState.getSourceId())) {
                        Message message = obtainMessage(REMOVE_BCAST_SOURCE);
                        message.arg1 = recvState.getSourceId();
                        sendMessage(message);
                    }
                }
            }
        }
        broadcastReceiverState(recvState, recvState.getSourceId());
    }

    // Implements callback methods for GATT events that the app cares about.
    // For example, connection change and services discovered.
    final class GattCallback extends BluetoothGattCallback {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            boolean isStateChanged = false;
            log("onConnectionStateChange : Status=" + status + "newState" + newState);
            if (newState == BluetoothProfile.STATE_CONNECTED
                    && getConnectionState() != BluetoothProfile.STATE_CONNECTED) {
                isStateChanged = true;
                Log.w(TAG, "Bassclient Connected from Disconnected state: " + mDevice);
                if (mService.okToConnect(mDevice)) {
                    log("Bassclient Connected to: " + mDevice);
                    if (mBluetoothGatt != null) {
                        log("Attempting to start service discovery:"
                                + mBluetoothGatt.discoverServices());
                        mDiscoveryInitiated = true;
                    }
                } else if (mBluetoothGatt != null) {
                    // Reject the connection
                    Log.w(TAG, "Bassclient Connect request rejected: " + mDevice);
                    mBluetoothGatt.disconnect();
                    mBluetoothGatt.close();
                    mBluetoothGatt = null;
                    // force move to disconnected
                    newState = BluetoothProfile.STATE_DISCONNECTED;
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED
                    && getConnectionState() != BluetoothProfile.STATE_DISCONNECTED) {
                isStateChanged = true;
                log("Disconnected from Bass GATT server.");
            }
            if (isStateChanged) {
                Message m = obtainMessage(CONNECTION_STATE_CHANGED);
                m.obj = newState;
                sendMessage(m);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            log("onServicesDiscovered:" + status);
            if (mDiscoveryInitiated) {
                mDiscoveryInitiated = false;
                if (status == BluetoothGatt.GATT_SUCCESS && mBluetoothGatt != null) {
                    mBluetoothGatt.requestMtu(BassConstants.BASS_MAX_BYTES);
                    mMTUChangeRequested = true;
                } else {
                    Log.w(TAG, "onServicesDiscovered received: "
                            + status + "mBluetoothGatt" + mBluetoothGatt);
                }
            } else {
                log("remote initiated callback");
            }
        }

        @Override
        public void onCharacteristicRead(
                BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic,
                int status) {
            log("onCharacteristicRead:: status: " + status + "char:" + characteristic);
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.getUuid()
                    .equals(BassConstants.BASS_BCAST_RECEIVER_STATE)) {
                log("onCharacteristicRead: BASS_BCAST_RECEIVER_STATE: status" + status);
                if (characteristic.getValue() == null) {
                    Log.e(TAG, "Remote receiver state is NULL");
                    return;
                }
                logByteArray("Received ", characteristic.getValue(), 0,
                        characteristic.getValue().length);
                processBroadcastReceiverState(characteristic.getValue(), characteristic);
            }
            // switch to receiving notifications after initial characteristic read
            BluetoothGattDescriptor desc = characteristic
                    .getDescriptor(BassConstants.CLIENT_CHARACTERISTIC_CONFIG);
            if (mBluetoothGatt != null && desc != null) {
                log("Setting the value for Desc");
                mBluetoothGatt.setCharacteristicNotification(characteristic, true);
                desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                mBluetoothGatt.writeDescriptor(desc);
            } else {
                Log.w(TAG, "CCC for " + characteristic + "seem to be not present");
                // at least move the SM to stable state
                Message m = obtainMessage(GATT_TXN_PROCESSED);
                m.arg1 = status;
                sendMessage(m);
            }
        }

        @Override
        public void onDescriptorWrite(
                BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            log("onDescriptorWrite");
            if (status == BluetoothGatt.GATT_SUCCESS
                    && descriptor.getUuid()
                    .equals(BassConstants.CLIENT_CHARACTERISTIC_CONFIG)) {
                log("CCC write resp");
            }

            // Move the SM to connected so further reads happens
            Message m = obtainMessage(GATT_TXN_PROCESSED);
            m.arg1 = status;
            sendMessage(m);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            log("onMtuChanged: mtu:" + mtu);
            if (mMTUChangeRequested && mBluetoothGatt != null) {
                acquireAllBassChars();
                mMTUChangeRequested = false;
            } else {
                log("onMtuChanged is remote initiated trigger, mBluetoothGatt:"
                        + mBluetoothGatt);
            }
        }

        @Override
        public void onCharacteristicChanged(
                BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            log("onCharacteristicChanged :: " + characteristic.getUuid().toString());
            if (characteristic.getUuid().equals(BassConstants.BASS_BCAST_RECEIVER_STATE)) {
                log("onCharacteristicChanged is rcvr State :: "
                        + characteristic.getUuid().toString());
                if (characteristic.getValue() == null) {
                    Log.e(TAG, "Remote receiver state is NULL");
                    return;
                }
                logByteArray("onCharacteristicChanged: Received ",
                        characteristic.getValue(),
                        0,
                        characteristic.getValue().length);
                processBroadcastReceiverState(characteristic.getValue(), characteristic);
            }
        }

        @Override
        public void onCharacteristicWrite(
                BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic,
                int status) {
            log("onCharacteristicWrite: " + characteristic.getUuid().toString()
                    + "status:" + status);
            if (status == 0
                    && characteristic.getUuid()
                    .equals(BassConstants.BASS_BCAST_AUDIO_SCAN_CTRL_POINT)) {
                log("BASS_BCAST_AUDIO_SCAN_CTRL_POINT is written successfully");
            }
            Message m = obtainMessage(GATT_TXN_PROCESSED);
            m.arg1 = status;
            sendMessage(m);
        }
    }

    /**
     * Connects to the GATT server of the device.
     *
     * @return {@code true} if it successfully connects to the GATT server.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public boolean connectGatt(Boolean autoConnect) {
        if (mGattCallback == null) {
            mGattCallback = new GattCallback();
        }

        BluetoothGatt gatt = mDevice.connectGatt(mService, autoConnect,
                mGattCallback, BluetoothDevice.TRANSPORT_LE,
                (BluetoothDevice.PHY_LE_1M_MASK
                        | BluetoothDevice.PHY_LE_2M_MASK
                        | BluetoothDevice.PHY_LE_CODED_MASK), null);

        if (gatt != null) {
            mBluetoothGatt = new BluetoothGattTestableWrapper(gatt);
        }

        return mBluetoothGatt != null;
    }

    /**
     * getAllSources
     */
    public List<BluetoothLeBroadcastReceiveState> getAllSources() {
        log("getAllSources");
        List list = new ArrayList(mBluetoothLeBroadcastReceiveStates.values());
        return list;
    }

    void acquireAllBassChars() {
        clearCharsCache();
        BluetoothGattService service = null;
        if (mBluetoothGatt != null) {
            log("getting Bass Service handle");
            service = mBluetoothGatt.getService(BassConstants.BASS_UUID);
        }
        if (service == null) {
            log("acquireAllBassChars: BASS service not found");
            return;
        }
        log("found BASS_SERVICE");
        List<BluetoothGattCharacteristic> allChars = service.getCharacteristics();
        int numOfChars = allChars.size();
        mNumOfBroadcastReceiverStates = numOfChars - 1;
        log("Total number of chars" + numOfChars);
        for (int i = 0; i < allChars.size(); i++) {
            if (allChars.get(i).getUuid().equals(BassConstants.BASS_BCAST_AUDIO_SCAN_CTRL_POINT)) {
                mBroadcastScanControlPoint = allChars.get(i);
                log("Index of ScanCtrlPoint:" + i);
            } else {
                log("Reading " + i + "th ReceiverState");
                mBroadcastCharacteristics.add(allChars.get(i));
                Message m = obtainMessage(READ_BASS_CHARACTERISTICS);
                m.obj = allChars.get(i);
                sendMessage(m);
            }
        }
    }

    void clearCharsCache() {
        if (mBroadcastCharacteristics != null) {
            mBroadcastCharacteristics.clear();
        }
        if (mBroadcastScanControlPoint != null) {
            mBroadcastScanControlPoint = null;
        }
        mNumOfBroadcastReceiverStates = 0;
        if (mBluetoothLeBroadcastReceiveStates != null) {
            mBluetoothLeBroadcastReceiveStates.clear();
        }
        mPendingOperation = -1;
        mPendingMetadata = null;
        mCurrentMetadata.clear();
        mPendingRemove.clear();
    }

    @VisibleForTesting
    class Disconnected extends State {
        @Override
        public void enter() {
            log("Enter Disconnected(" + mDevice + "): "
                    + messageWhatToString(getCurrentMessage().what));
            clearCharsCache();
            mNextSourceId = 0;
            removeDeferredMessages(DISCONNECT);
            if (mLastConnectionState == -1) {
                log("no Broadcast of initial profile state ");
            } else {
                broadcastConnectionState(
                        mDevice, mLastConnectionState, BluetoothProfile.STATE_DISCONNECTED);
                if (mLastConnectionState != BluetoothProfile.STATE_DISCONNECTED) {
                    // Reconnect in background if not disallowed by the service
                    if (mService.okToConnect(mDevice) && mAllowReconnect) {
                        connectGatt(false);
                    }
                }
            }
        }

        @Override
        public void exit() {
            log("Exit Disconnected(" + mDevice + "): "
                    + messageWhatToString(getCurrentMessage().what));
            mLastConnectionState = BluetoothProfile.STATE_DISCONNECTED;
        }

        @Override
        public boolean processMessage(Message message) {
            log("Disconnected process message(" + mDevice
                    + "): " + messageWhatToString(message.what));
            switch (message.what) {
                case CONNECT:
                    log("Connecting to " + mDevice);
                    if (mBluetoothGatt != null) {
                        Log.d(TAG, "clear off, pending wl connection");
                        mBluetoothGatt.disconnect();
                        mBluetoothGatt.close();
                        mBluetoothGatt = null;
                    }
                    mAllowReconnect = true;
                    if (connectGatt(mIsAllowedList)) {
                        transitionTo(mConnecting);
                    } else {
                        Log.e(TAG, "Disconnected: error connecting to " + mDevice);
                    }
                    break;
                case DISCONNECT:
                    // Disconnect if there's an ongoing background connection
                    mAllowReconnect = false;
                    if (mBluetoothGatt != null) {
                        log("Cancelling the background connection to " + mDevice);
                        mBluetoothGatt.disconnect();
                        mBluetoothGatt.close();
                        mBluetoothGatt = null;
                    } else {
                        Log.d(TAG, "Disconnected: DISCONNECT ignored: " + mDevice);
                    }
                    break;
                case CONNECTION_STATE_CHANGED:
                    int state = (int) message.obj;
                    Log.w(TAG, "connection state changed:" + state);
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        log("remote/wl connection");
                        transitionTo(mConnected);
                    } else {
                        Log.w(TAG, "Disconnected: Connection failed to " + mDevice);
                    }
                    break;
                case PSYNC_ACTIVE_TIMEOUT:
                    cancelActiveSync(null);
                    break;
                default:
                    log("DISCONNECTED: not handled message:" + message.what);
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    @VisibleForTesting
    class Connecting extends State {
        @Override
        public void enter() {
            log("Enter Connecting(" + mDevice + "): "
                    + messageWhatToString(getCurrentMessage().what));
            sendMessageDelayed(CONNECT_TIMEOUT, mDevice, mConnectTimeoutMs);
            broadcastConnectionState(
                    mDevice, mLastConnectionState, BluetoothProfile.STATE_CONNECTING);
        }

        @Override
        public void exit() {
            log("Exit Connecting(" + mDevice + "): "
                    + messageWhatToString(getCurrentMessage().what));
            mLastConnectionState = BluetoothProfile.STATE_CONNECTING;
            removeMessages(CONNECT_TIMEOUT);
        }

        @Override
        public boolean processMessage(Message message) {
            log("Connecting process message(" + mDevice + "): "
                    + messageWhatToString(message.what));
            switch (message.what) {
                case CONNECT:
                    log("Already Connecting to " + mDevice);
                    log("Ignore this connection request " + mDevice);
                    break;
                case DISCONNECT:
                    Log.w(TAG, "Connecting: DISCONNECT deferred: " + mDevice);
                    deferMessage(message);
                    break;
                case READ_BASS_CHARACTERISTICS:
                    Log.w(TAG, "defer READ_BASS_CHARACTERISTICS requested!: " + mDevice);
                    deferMessage(message);
                    break;
                case CONNECTION_STATE_CHANGED:
                    int state = (int) message.obj;
                    Log.w(TAG, "Connecting: connection state changed:" + state);
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        transitionTo(mConnected);
                    } else {
                        Log.w(TAG, "Connection failed to " + mDevice);
                        transitionTo(mDisconnected);
                    }
                    break;
                case CONNECT_TIMEOUT:
                    Log.w(TAG, "CONNECT_TIMEOUT");
                    BluetoothDevice device = (BluetoothDevice) message.obj;
                    if (!mDevice.equals(device)) {
                        Log.e(TAG, "Unknown device timeout " + device);
                        break;
                    }
                    transitionTo(mDisconnected);
                    break;
                case PSYNC_ACTIVE_TIMEOUT:
                    deferMessage(message);
                    break;
                default:
                    log("CONNECTING: not handled message:" + message.what);
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    private static int getBisSyncFromChannelPreference(
                List<BluetoothLeBroadcastChannel> channels) {
        int bisSync = 0;
        for (BluetoothLeBroadcastChannel channel : channels) {
            if (channel.isSelected()) {
                if (channel.getChannelIndex() == 0) {
                    Log.e(TAG, "getBisSyncFromChannelPreference: invalid channel index=0");
                    continue;
                }
                bisSync |= 1 << (channel.getChannelIndex() - 1);
            }
        }

        return bisSync;
    }

    private byte[] convertMetadataToAddSourceByteArray(BluetoothLeBroadcastMetadata metaData) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        BluetoothDevice advSource = metaData.getSourceDevice();

        // Opcode
        stream.write(OPCODE_ADD_SOURCE);

        // Advertiser_Address_Type
        stream.write(metaData.getSourceAddressType());

        // Advertiser_Address
        byte[] bcastSourceAddr = Utils.getBytesFromAddress(advSource.getAddress());
        BassUtils.reverse(bcastSourceAddr);
        stream.write(bcastSourceAddr, 0, 6);
        log("Address bytes: " + advSource.getAddress());

        // Advertising_SID
        stream.write(metaData.getSourceAdvertisingSid());

        // Broadcast_ID
        stream.write(metaData.getBroadcastId() & 0x00000000000000FF);
        stream.write((metaData.getBroadcastId() & 0x000000000000FF00) >>> 8);
        stream.write((metaData.getBroadcastId() & 0x0000000000FF0000) >>> 16);
        log("mBroadcastId: " + metaData.getBroadcastId());

        // PA_Sync
        if (!mDefNoPAS) {
            stream.write(0x01);
        } else {
            log("setting PA sync to ZERO");
            stream.write(0x00);
        }

        // PA_Interval
        stream.write((metaData.getPaSyncInterval() & 0x00000000000000FF));
        stream.write((metaData.getPaSyncInterval() & 0x000000000000FF00) >>> 8);

        // Num_Subgroups
        List<BluetoothLeBroadcastSubgroup> subGroups = metaData.getSubgroups();
        stream.write(metaData.getSubgroups().size());

        for (BluetoothLeBroadcastSubgroup subGroup : subGroups) {
            // BIS_Sync
            int bisSync = getBisSyncFromChannelPreference(subGroup.getChannels());
            if (bisSync == 0) {
                bisSync = 0xFFFFFFFF;
            }
            stream.write(bisSync & 0x00000000000000FF);
            stream.write((bisSync & 0x000000000000FF00) >>> 8);
            stream.write((bisSync & 0x0000000000FF0000) >>> 16);
            stream.write((bisSync & 0x00000000FF000000) >>> 24);

            // Metadata_Length
            BluetoothLeAudioContentMetadata metadata = subGroup.getContentMetadata();
            stream.write(metadata.getRawMetadata().length);

            // Metadata
            stream.write(metadata.getRawMetadata(), 0, metadata.getRawMetadata().length);
        }

        byte[] res = stream.toByteArray();
        log("ADD_BCAST_SOURCE in Bytes");
        BassUtils.printByteArray(res);
        return res;
    }

    private byte[] convertBroadcastMetadataToUpdateSourceByteArray(int sourceId,
            BluetoothLeBroadcastMetadata metaData, int paSync) {
        BluetoothLeBroadcastReceiveState existingState =
                getBroadcastReceiveStateForSourceId(sourceId);
        if (existingState == null) {
            log("no existing SI for update source op");
            return null;
        }
        BluetoothDevice broadcastSource = metaData.getSourceDevice();
        PeriodicAdvertisementResult paRes =
                mService.getPeriodicAdvertisementResult(broadcastSource);
        if (paRes == null) {
            Log.e(TAG, "No matching psync, scan res for update");
            mService.getCallbacks().notifySourceRemoveFailed(
                    mDevice, sourceId, BluetoothStatusCodes.ERROR_UNKNOWN);
            return null;
        }
        // populate metadata from BASE levelOne
        BaseData base = mService.getBase(paRes.getSyncHandle());
        if (base == null) {
            Log.e(TAG, "No valid base data populated for this device");
            mService.getCallbacks().notifySourceRemoveFailed(
                    mDevice, sourceId, BluetoothStatusCodes.ERROR_UNKNOWN);
            return null;
        }
        byte numSubGroups = base.getNumberOfSubgroupsofBIG();
        byte[] res = new byte[UPDATE_SOURCE_FIXED_LENGTH + numSubGroups * 5];
        int offset = 0;
        // Opcode
        res[offset++] = OPCODE_UPDATE_SOURCE;
        // Source_ID
        res[offset++] = (byte) sourceId;
        // PA_Sync
        if (paSync != BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_INVALID) {
            res[offset++] = (byte) paSync;
        } else if (existingState.getPaSyncState()
                == BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_SYNCHRONIZED) {
            res[offset++] = (byte) (0x01);
        } else {
            res[offset++] = (byte) 0x00;
        }
        // PA_Interval
        res[offset++] = (byte) 0xFF;
        res[offset++] = (byte) 0xFF;
        // Num_Subgroups
        res[offset++] = numSubGroups;
        for (int i = 0; i < numSubGroups; i++) {
            int bisIndexValue;
            if (paSync != BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_INVALID) {
                bisIndexValue = 0;
            } else {
                bisIndexValue = existingState.getBisSyncState().get(i).intValue();
            }
            log("UPDATE_BCAST_SOURCE: bisIndexValue : " + bisIndexValue);
            // BIS_Sync
            res[offset++] = (byte) (bisIndexValue & 0x00000000000000FF);
            res[offset++] = (byte) ((bisIndexValue & 0x000000000000FF00) >>> 8);
            res[offset++] = (byte) ((bisIndexValue & 0x0000000000FF0000) >>> 16);
            res[offset++] = (byte) ((bisIndexValue & 0x00000000FF000000) >>> 24);
            // Metadata_Length; On Modify source, don't update any Metadata
            res[offset++] = 0;
        }
        log("UPDATE_BCAST_SOURCE in Bytes");
        BassUtils.printByteArray(res);
        return res;
    }

    private byte[] convertRecvStateToSetBroadcastCodeByteArray(
            BluetoothLeBroadcastReceiveState recvState) {
        byte[] res = new byte[BassConstants.PIN_CODE_CMD_LEN];
        // Opcode
        res[0] = OPCODE_SET_BCAST_PIN;
        // Source_ID
        res[1] = (byte) recvState.getSourceId();
        log("convertRecvStateToSetBroadcastCodeByteArray: Source device : "
                + recvState.getSourceDevice());
        BluetoothLeBroadcastMetadata metaData =
                getCurrentBroadcastMetadata(recvState.getSourceId());
        if (metaData == null) {
            Log.e(TAG, "Fail to find broadcast source, sourceId = "
                    + recvState.getSourceId());
            return null;
        }
        // Broadcast Code
        byte[] actualPIN = metaData.getBroadcastCode();
        if (actualPIN == null) {
            Log.e(TAG, "actual PIN is null");
            return null;
        } else {
            log("byte array broadcast Code:" + Arrays.toString(actualPIN));
            log("pinLength:" + actualPIN.length);
            // Broadcast_Code, Fill the PIN code in the Last Position
            // This effectively adds padding zeros to LSB positions when the broadcast code
            // is shorter than 16 octets
            System.arraycopy(
                    actualPIN, 0, res,
                    (BassConstants.PIN_CODE_CMD_LEN - actualPIN.length), actualPIN.length);
            log("SET_BCAST_PIN in Bytes");
            BassUtils.printByteArray(res);
        }
        return res;
    }

    private boolean isItRightTimeToUpdateBroadcastPin(byte sourceId) {
        Collection<BluetoothLeBroadcastReceiveState> recvStates =
                mBluetoothLeBroadcastReceiveStates.values();
        Iterator<BluetoothLeBroadcastReceiveState> iterator = recvStates.iterator();
        boolean retval = false;
        if (mForceSB) {
            log("force SB is set");
            return true;
        }
        while (iterator.hasNext()) {
            BluetoothLeBroadcastReceiveState state = iterator.next();
            if (state == null) {
                log("Source state is null");
                continue;
            }
            if (sourceId == state.getSourceId() && state.getBigEncryptionState()
                    == BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_CODE_REQUIRED) {
                retval = true;
                break;
            }
        }
        log("IsItRightTimeToUpdateBroadcastPIN returning:" + retval);
        return retval;
    }

    @VisibleForTesting
    class Connected extends State {
        @Override
        public void enter() {
            log("Enter Connected(" + mDevice + "): "
                    + messageWhatToString(getCurrentMessage().what));
            removeDeferredMessages(CONNECT);
            if (mLastConnectionState == BluetoothProfile.STATE_CONNECTED) {
                log("CONNECTED->CONNECTED: Ignore");
                // Broadcast for testing purpose only
                if (Utils.isInstrumentationTestMode()) {
                    Intent intent = new Intent("android.bluetooth.bass_client.NOTIFY_TEST");
                    mService.sendBroadcast(intent, BLUETOOTH_CONNECT,
                            Utils.getTempAllowlistBroadcastOptions());
                }
            } else {
                broadcastConnectionState(mDevice, mLastConnectionState,
                        BluetoothProfile.STATE_CONNECTED);
            }
        }

        @Override
        public void exit() {
            log("Exit Connected(" + mDevice + "): "
                    + messageWhatToString(getCurrentMessage().what));
            mLastConnectionState = BluetoothProfile.STATE_CONNECTED;
        }

        @Override
        public boolean processMessage(Message message) {
            log("Connected process message(" + mDevice + "): " + messageWhatToString(message.what));
            BluetoothLeBroadcastMetadata metaData;
            switch (message.what) {
                case CONNECT:
                    Log.w(TAG, "Connected: CONNECT ignored: " + mDevice);
                    break;
                case DISCONNECT:
                    log("Disconnecting from " + mDevice);
                    mAllowReconnect = false;
                    if (mBluetoothGatt != null) {
                        mBluetoothGatt.disconnect();
                        mBluetoothGatt.close();
                        mBluetoothGatt = null;
                        cancelActiveSync(null);
                        transitionTo(mDisconnected);
                    } else {
                        log("mBluetoothGatt is null");
                    }
                    break;
                case CONNECTION_STATE_CHANGED:
                    int state = (int) message.obj;
                    Log.w(TAG, "Connected:connection state changed:" + state);
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        Log.w(TAG, "device is already connected to Bass" + mDevice);
                    } else {
                        Log.w(TAG, "unexpected disconnected from " + mDevice);
                        cancelActiveSync(null);
                        transitionTo(mDisconnected);
                    }
                    break;
                case READ_BASS_CHARACTERISTICS:
                    BluetoothGattCharacteristic characteristic =
                            (BluetoothGattCharacteristic) message.obj;
                    if (mBluetoothGatt != null) {
                        mBluetoothGatt.readCharacteristic(characteristic);
                        transitionTo(mConnectedProcessing);
                    } else {
                        Log.e(TAG, "READ_BASS_CHARACTERISTICS is ignored, Gatt handle is null");
                    }
                    break;
                case START_SCAN_OFFLOAD:
                    if (mBluetoothGatt != null && mBroadcastScanControlPoint != null) {
                        mBroadcastScanControlPoint.setValue(REMOTE_SCAN_START);
                        mBluetoothGatt.writeCharacteristic(mBroadcastScanControlPoint);
                        mPendingOperation = message.what;
                        transitionTo(mConnectedProcessing);
                    } else {
                        log("no Bluetooth Gatt handle, may need to fetch write");
                    }
                    break;
                case STOP_SCAN_OFFLOAD:
                    if (mBluetoothGatt != null && mBroadcastScanControlPoint != null) {
                        mBroadcastScanControlPoint.setValue(REMOTE_SCAN_STOP);
                        mBluetoothGatt.writeCharacteristic(mBroadcastScanControlPoint);
                        mPendingOperation = message.what;
                        transitionTo(mConnectedProcessing);
                    } else {
                        log("no Bluetooth Gatt handle, may need to fetch write");
                    }
                    break;
                case SELECT_BCAST_SOURCE:
                    ScanResult scanRes = (ScanResult) message.obj;
                    boolean auto = ((int) message.arg1) == BassConstants.AUTO;
                    selectSource(scanRes, auto);
                    break;
                case ADD_BCAST_SOURCE:
                    metaData = (BluetoothLeBroadcastMetadata) message.obj;
                    log("Adding Broadcast source" + metaData);
                    byte[] addSourceInfo = convertMetadataToAddSourceByteArray(metaData);
                    if (addSourceInfo == null) {
                        Log.e(TAG, "add source: source Info is NULL");
                        break;
                    }
                    if (mBluetoothGatt != null && mBroadcastScanControlPoint != null) {
                        mBroadcastScanControlPoint.setValue(addSourceInfo);
                        mBluetoothGatt.writeCharacteristic(mBroadcastScanControlPoint);
                        mPendingOperation = message.what;
                        mPendingMetadata = metaData;
                        if (metaData.isEncrypted() && (metaData.getBroadcastCode() != null)) {
                            mSetBroadcastCodePending = true;
                        }
                        transitionTo(mConnectedProcessing);
                        sendMessageDelayed(GATT_TXN_TIMEOUT, BassConstants.GATT_TXN_TIMEOUT_MS);
                    } else {
                        Log.e(TAG, "ADD_BCAST_SOURCE: no Bluetooth Gatt handle, Fatal");
                        mService.getCallbacks().notifySourceAddFailed(mDevice,
                                metaData, BluetoothStatusCodes.ERROR_UNKNOWN);
                    }
                    break;
                case UPDATE_BCAST_SOURCE:
                    metaData = (BluetoothLeBroadcastMetadata) message.obj;
                    int sourceId = message.arg1;
                    int paSync = message.arg2;
                    log("Updating Broadcast source" + metaData);
                    byte[] updateSourceInfo = convertBroadcastMetadataToUpdateSourceByteArray(
                            sourceId, metaData, paSync);
                    if (updateSourceInfo == null) {
                        Log.e(TAG, "update source: source Info is NULL");
                        break;
                    }
                    if (mBluetoothGatt != null && mBroadcastScanControlPoint != null) {
                        mBroadcastScanControlPoint.setValue(updateSourceInfo);
                        mBluetoothGatt.writeCharacteristic(mBroadcastScanControlPoint);
                        mPendingOperation = message.what;
                        mPendingSourceId = (byte) sourceId;
                        if (paSync == BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE) {
                            setPendingRemove(sourceId, true);
                        }
                        if (metaData.isEncrypted() && (metaData.getBroadcastCode() != null)) {
                            mSetBroadcastCodePending = true;
                        }
                        mPendingMetadata = metaData;
                        transitionTo(mConnectedProcessing);
                        sendMessageDelayed(GATT_TXN_TIMEOUT, BassConstants.GATT_TXN_TIMEOUT_MS);
                    } else {
                        Log.e(TAG, "UPDATE_BCAST_SOURCE: no Bluetooth Gatt handle, Fatal");
                        mService.getCallbacks().notifySourceModifyFailed(
                                mDevice, sourceId, BluetoothStatusCodes.ERROR_UNKNOWN);
                    }
                    break;
                case SET_BCAST_CODE:
                    BluetoothLeBroadcastReceiveState recvState =
                            (BluetoothLeBroadcastReceiveState) message.obj;
                    log("SET_BCAST_CODE metaData: " + recvState);
                    if (!isItRightTimeToUpdateBroadcastPin((byte) recvState.getSourceId())) {
                        mSetBroadcastCodePending = true;
                        mSetBroadcastPINRcvState = recvState;
                        log("Ignore SET_BCAST now, but store it for later");
                    } else {
                        byte[] setBroadcastPINcmd =
                                convertRecvStateToSetBroadcastCodeByteArray(recvState);
                        if (setBroadcastPINcmd == null) {
                            Log.e(TAG, "SET_BCAST_CODE: Broadcast code is NULL");
                            break;
                        }
                        if (mBluetoothGatt != null && mBroadcastScanControlPoint != null) {
                            mBroadcastScanControlPoint.setValue(setBroadcastPINcmd);
                            mBluetoothGatt.writeCharacteristic(mBroadcastScanControlPoint);
                            mPendingOperation = message.what;
                            mPendingSourceId = (byte) recvState.getSourceId();
                            transitionTo(mConnectedProcessing);
                            sendMessageDelayed(GATT_TXN_TIMEOUT, BassConstants.GATT_TXN_TIMEOUT_MS);
                            mSetBroadcastCodePending = false;
                            mSetBroadcastPINRcvState = null;
                        }
                    }
                    break;
                case REMOVE_BCAST_SOURCE:
                    byte sid = (byte) message.arg1;
                    log("Removing Broadcast source, sourceId: " + sid);
                    byte[] removeSourceInfo = new byte[2];
                    removeSourceInfo[0] = OPCODE_REMOVE_SOURCE;
                    removeSourceInfo[1] = sid;
                    if (mBluetoothGatt != null && mBroadcastScanControlPoint != null) {
                        if (isPendingRemove((int) sid)) {
                            setPendingRemove((int) sid, false);
                        }

                        mBroadcastScanControlPoint.setValue(removeSourceInfo);
                        mBluetoothGatt.writeCharacteristic(mBroadcastScanControlPoint);
                        mPendingOperation = message.what;
                        mPendingSourceId = sid;
                        transitionTo(mConnectedProcessing);
                        sendMessageDelayed(GATT_TXN_TIMEOUT, BassConstants.GATT_TXN_TIMEOUT_MS);
                    } else {
                        Log.e(TAG, "REMOVE_BCAST_SOURCE: no Bluetooth Gatt handle, Fatal");
                        mService.getCallbacks().notifySourceRemoveFailed(mDevice,
                                sid, BluetoothStatusCodes.ERROR_UNKNOWN);
                    }
                    break;
                case PSYNC_ACTIVE_TIMEOUT:
                    cancelActiveSync(null);
                    break;
                default:
                    log("CONNECTED: not handled message:" + message.what);
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    private boolean isSuccess(int status) {
        boolean ret = false;
        switch (status) {
            case BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST:
            case BluetoothStatusCodes.REASON_LOCAL_STACK_REQUEST:
            case BluetoothStatusCodes.REASON_REMOTE_REQUEST:
            case BluetoothStatusCodes.REASON_SYSTEM_POLICY:
                ret = true;
                break;
            default:
                break;
        }
        return ret;
    }

    void sendPendingCallbacks(int pendingOp, int status) {
        switch (pendingOp) {
            case START_SCAN_OFFLOAD:
                if (!isSuccess(status)) {
                    if (!mAutoTriggered) {
                        cancelActiveSync(null);
                    } else {
                        mAutoTriggered = false;
                    }
                }
                break;
            case ADD_BCAST_SOURCE:
                if (!isSuccess(status)) {
                    cancelActiveSync(null);
                    Message message = obtainMessage(STOP_SCAN_OFFLOAD);
                    sendMessage(message);
                    mService.getCallbacks().notifySourceAddFailed(mDevice,
                            mPendingMetadata, status);
                    mPendingMetadata = null;
                }
                break;
            case UPDATE_BCAST_SOURCE:
                if (!mAutoTriggered) {
                    if (!isSuccess(status)) {
                        mService.getCallbacks().notifySourceModifyFailed(mDevice,
                                mPendingSourceId, status);
                        mPendingMetadata = null;
                    }
                } else {
                    mAutoTriggered = false;
                }
                break;
            case REMOVE_BCAST_SOURCE:
                if (!isSuccess(status)) {
                    mService.getCallbacks().notifySourceRemoveFailed(mDevice,
                            mPendingSourceId, status);
                }
                break;
            case SET_BCAST_CODE:
                log("sendPendingCallbacks: SET_BCAST_CODE");
                break;
            default:
                log("sendPendingCallbacks: unhandled case");
                break;
        }
    }

    // public for testing, but private for non-testing
    @VisibleForTesting
    class ConnectedProcessing extends State {
        @Override
        public void enter() {
            log("Enter ConnectedProcessing(" + mDevice + "): "
                    + messageWhatToString(getCurrentMessage().what));

            // Broadcast for testing purpose only
            if (Utils.isInstrumentationTestMode()) {
                Intent intent = new Intent("android.bluetooth.bass_client.NOTIFY_TEST");
                mService.sendBroadcast(intent, BLUETOOTH_CONNECT,
                        Utils.getTempAllowlistBroadcastOptions());
            }
        }
        @Override
        public void exit() {
            /* Pending Metadata will be used to bond with source ID in receiver state notify */
            if (mPendingOperation == REMOVE_BCAST_SOURCE) {
                    mPendingMetadata = null;
            }

            log("Exit ConnectedProcessing(" + mDevice + "): "
                    + messageWhatToString(getCurrentMessage().what));
        }
        @Override
        public boolean processMessage(Message message) {
            log("ConnectedProcessing process message(" + mDevice + "): "
                    + messageWhatToString(message.what));
            switch (message.what) {
                case CONNECT:
                    Log.w(TAG, "CONNECT request is ignored" + mDevice);
                    break;
                case DISCONNECT:
                    Log.w(TAG, "DISCONNECT requested!: " + mDevice);
                    mAllowReconnect = false;
                    if (mBluetoothGatt != null) {
                        mBluetoothGatt.disconnect();
                        mBluetoothGatt.close();
                        mBluetoothGatt = null;
                        cancelActiveSync(null);
                        transitionTo(mDisconnected);
                    } else {
                        log("mBluetoothGatt is null");
                    }
                    break;
                case READ_BASS_CHARACTERISTICS:
                    Log.w(TAG, "defer READ_BASS_CHARACTERISTICS requested!: " + mDevice);
                    deferMessage(message);
                    break;
                case CONNECTION_STATE_CHANGED:
                    int state = (int) message.obj;
                    Log.w(TAG, "ConnectedProcessing: connection state changed:" + state);
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        Log.w(TAG, "should never happen from this state");
                    } else {
                        Log.w(TAG, "Unexpected disconnection " + mDevice);
                        transitionTo(mDisconnected);
                    }
                    break;
                case GATT_TXN_PROCESSED:
                    removeMessages(GATT_TXN_TIMEOUT);
                    int status = (int) message.arg1;
                    log("GATT transaction processed for" + mDevice);
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        sendPendingCallbacks(
                                mPendingOperation,
                                BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST);
                    } else {
                        sendPendingCallbacks(
                                mPendingOperation,
                                BluetoothStatusCodes.ERROR_UNKNOWN);
                    }
                    transitionTo(mConnected);
                    break;
                case GATT_TXN_TIMEOUT:
                    log("GATT transaction timeout for" + mDevice);
                    sendPendingCallbacks(
                            mPendingOperation,
                            BluetoothStatusCodes.ERROR_UNKNOWN);
                    mPendingOperation = -1;
                    mPendingSourceId = -1;
                    transitionTo(mConnected);
                    break;
                case START_SCAN_OFFLOAD:
                case STOP_SCAN_OFFLOAD:
                case SELECT_BCAST_SOURCE:
                case ADD_BCAST_SOURCE:
                case SET_BCAST_CODE:
                case REMOVE_BCAST_SOURCE:
                case PSYNC_ACTIVE_TIMEOUT:
                    log("defer the message:" + message.what + "so that it will be processed later");
                    deferMessage(message);
                    break;
                default:
                    log("CONNECTEDPROCESSING: not handled message:" + message.what);
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    void broadcastConnectionState(BluetoothDevice device, int fromState, int toState) {
        log("broadcastConnectionState " + device + ": " + fromState + "->" + toState);
        if (fromState == BluetoothProfile.STATE_CONNECTED
                && toState == BluetoothProfile.STATE_CONNECTED) {
            log("CONNECTED->CONNECTED: Ignore");
            return;
        }

        Intent intent = new Intent(BluetoothLeBroadcastAssistant.ACTION_CONNECTION_STATE_CHANGED);
        intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, fromState);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, toState);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mDevice);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
                    | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
        mService.sendBroadcast(intent, BLUETOOTH_CONNECT, Utils.getTempAllowlistBroadcastOptions());
    }

    int getConnectionState() {
        String currentState = "Unknown";
        if (getCurrentState() != null) {
            currentState = getCurrentState().getName();
        }
        switch (currentState) {
            case "Disconnected":
                log("Disconnected");
                return BluetoothProfile.STATE_DISCONNECTED;
            case "Connecting":
                log("Connecting");
                return BluetoothProfile.STATE_CONNECTING;
            case "Connected":
            case "ConnectedProcessing":
                log("connected");
                return BluetoothProfile.STATE_CONNECTED;
            default:
                Log.e(TAG, "Bad currentState: " + currentState);
                return BluetoothProfile.STATE_DISCONNECTED;
        }
    }

    int getMaximumSourceCapacity() {
        return mNumOfBroadcastReceiverStates;
    }

    BluetoothDevice getDevice() {
        return mDevice;
    }

    synchronized boolean isConnected() {
        return getCurrentState() == mConnected;
    }

    public static String messageWhatToString(int what) {
        switch (what) {
            case CONNECT:
                return "CONNECT";
            case DISCONNECT:
                return "DISCONNECT";
            case CONNECTION_STATE_CHANGED:
                return "CONNECTION_STATE_CHANGED";
            case GATT_TXN_PROCESSED:
                return "GATT_TXN_PROCESSED";
            case READ_BASS_CHARACTERISTICS:
                return "READ_BASS_CHARACTERISTICS";
            case START_SCAN_OFFLOAD:
                return "START_SCAN_OFFLOAD";
            case STOP_SCAN_OFFLOAD:
                return "STOP_SCAN_OFFLOAD";
            case ADD_BCAST_SOURCE:
                return "ADD_BCAST_SOURCE";
            case SELECT_BCAST_SOURCE:
                return "SELECT_BCAST_SOURCE";
            case UPDATE_BCAST_SOURCE:
                return "UPDATE_BCAST_SOURCE";
            case SET_BCAST_CODE:
                return "SET_BCAST_CODE";
            case REMOVE_BCAST_SOURCE:
                return "REMOVE_BCAST_SOURCE";
            case PSYNC_ACTIVE_TIMEOUT:
                return "PSYNC_ACTIVE_TIMEOUT";
            case CONNECT_TIMEOUT:
                return "CONNECT_TIMEOUT";
            default:
                break;
        }
        return Integer.toString(what);
    }

    /**
     * Dump info
     */
    public void dump(StringBuilder sb) {
        ProfileService.println(sb, "mDevice: " + mDevice);
        ProfileService.println(sb, "  StateMachine: " + this);
        // Dump the state machine logs
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        super.dump(new FileDescriptor(), printWriter, new String[] {});
        printWriter.flush();
        stringWriter.flush();
        ProfileService.println(sb, "  StateMachineLog:");
        Scanner scanner = new Scanner(stringWriter.toString());
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            ProfileService.println(sb, "    " + line);
        }
        scanner.close();
    }

    @Override
    protected void log(String msg) {
        if (BassConstants.BASS_DBG) {
            super.log(msg);
        }
    }

    private static void logByteArray(String prefix, byte[] value, int offset, int count) {
        StringBuilder builder = new StringBuilder(prefix);
        for (int i = offset; i < count; i++) {
            builder.append(String.format("0x%02X", value[i]));
            if (i != value.length - 1) {
                builder.append(", ");
            }
        }
        Log.d(TAG, builder.toString());
    }

    /** Mockable wrapper of {@link BluetoothGatt}. */
    @VisibleForTesting
    public static class BluetoothGattTestableWrapper {
        public final BluetoothGatt mWrappedBluetoothGatt;

        BluetoothGattTestableWrapper(BluetoothGatt bluetoothGatt) {
            mWrappedBluetoothGatt = bluetoothGatt;
        }

        /** See {@link BluetoothGatt#getServices()}. */
        public List<BluetoothGattService> getServices() {
            return mWrappedBluetoothGatt.getServices();
        }

        /** See {@link BluetoothGatt#getService(UUID)}. */
        @Nullable
        public BluetoothGattService getService(UUID uuid) {
            return mWrappedBluetoothGatt.getService(uuid);
        }

        /** See {@link BluetoothGatt#discoverServices()}. */
        public boolean discoverServices() {
            return mWrappedBluetoothGatt.discoverServices();
        }

        /**
         * See {@link BluetoothGatt#readCharacteristic(
         * BluetoothGattCharacteristic)}.
         */
        public boolean readCharacteristic(BluetoothGattCharacteristic characteristic) {
            return mWrappedBluetoothGatt.readCharacteristic(characteristic);
        }

        /**
         * See {@link BluetoothGatt#writeCharacteristic(
         * BluetoothGattCharacteristic, byte[], int)} .
         */
        public boolean writeCharacteristic(BluetoothGattCharacteristic characteristic) {
            return mWrappedBluetoothGatt.writeCharacteristic(characteristic);
        }

        /** See {@link BluetoothGatt#readDescriptor(BluetoothGattDescriptor)}. */
        public boolean readDescriptor(BluetoothGattDescriptor descriptor) {
            return mWrappedBluetoothGatt.readDescriptor(descriptor);
        }

        /**
         * See {@link BluetoothGatt#writeDescriptor(BluetoothGattDescriptor,
         * byte[])}.
         */
        public boolean writeDescriptor(BluetoothGattDescriptor descriptor) {
            return mWrappedBluetoothGatt.writeDescriptor(descriptor);
        }

        /** See {@link BluetoothGatt#requestMtu(int)}. */
        public boolean requestMtu(int mtu) {
            return mWrappedBluetoothGatt.requestMtu(mtu);
        }

        /** See {@link BluetoothGatt#setCharacteristicNotification}. */
        public boolean setCharacteristicNotification(
                BluetoothGattCharacteristic characteristic, boolean enable) {
            return mWrappedBluetoothGatt.setCharacteristicNotification(characteristic, enable);
        }

        /** See {@link BluetoothGatt#disconnect()}. */
        public void disconnect() {
            mWrappedBluetoothGatt.disconnect();
        }

        /** See {@link BluetoothGatt#close()}. */
        public void close() {
            mWrappedBluetoothGatt.close();
        }
    }

}
