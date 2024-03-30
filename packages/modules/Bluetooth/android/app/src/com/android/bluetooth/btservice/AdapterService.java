/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.bluetooth.btservice;

import static android.bluetooth.BluetoothDevice.TRANSPORT_AUTO;
import static android.text.format.DateUtils.MINUTE_IN_MILLIS;
import static android.text.format.DateUtils.SECOND_IN_MILLIS;

import static com.android.bluetooth.Utils.callerIsSystemOrActiveOrManagedUser;
import static com.android.bluetooth.Utils.enforceBluetoothPrivilegedPermission;
import static com.android.bluetooth.Utils.enforceCdmAssociation;
import static com.android.bluetooth.Utils.enforceDumpPermission;
import static com.android.bluetooth.Utils.enforceLocalMacAddressPermission;
import static com.android.bluetooth.Utils.getBytesFromAddress;
import static com.android.bluetooth.Utils.hasBluetoothPrivilegedPermission;
import static com.android.bluetooth.Utils.isPackageNameAccurate;

import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothActivityEnergyInfo;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.ActiveDeviceProfile;
import android.bluetooth.BluetoothAdapter.ActiveDeviceUse;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothFrameworkInitializer;
import android.bluetooth.BluetoothMap;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProtoEnums;
import android.bluetooth.BluetoothSap;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSinkAudioPolicy;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.BufferConstraints;
import android.bluetooth.IBluetooth;
import android.bluetooth.IBluetoothActivityEnergyInfoListener;
import android.bluetooth.IBluetoothCallback;
import android.bluetooth.IBluetoothConnectionCallback;
import android.bluetooth.IBluetoothMetadataListener;
import android.bluetooth.IBluetoothOobDataCallback;
import android.bluetooth.IBluetoothSocketManager;
import android.bluetooth.IncomingRfcommSocketInfo;
import android.bluetooth.OobData;
import android.bluetooth.UidTraffic;
import android.companion.CompanionDeviceManager;
import android.content.AttributionSource;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.BatteryStatsManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.PowerManager;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.sysprop.BluetoothProperties;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.util.SparseArray;

import com.android.bluetooth.BluetoothMetricsProto;
import com.android.bluetooth.BluetoothStatsLog;
import com.android.bluetooth.R;
import com.android.bluetooth.Utils;
import com.android.bluetooth.a2dp.A2dpService;
import com.android.bluetooth.a2dpsink.A2dpSinkService;
import com.android.bluetooth.bas.BatteryService;
import com.android.bluetooth.bass_client.BassClientService;
import com.android.bluetooth.btservice.RemoteDevices.DeviceProperties;
import com.android.bluetooth.btservice.activityattribution.ActivityAttributionService;
import com.android.bluetooth.btservice.bluetoothkeystore.BluetoothKeystoreService;
import com.android.bluetooth.btservice.storage.DatabaseManager;
import com.android.bluetooth.btservice.storage.MetadataDatabase;
import com.android.bluetooth.csip.CsipSetCoordinatorService;
import com.android.bluetooth.gatt.GattService;
import com.android.bluetooth.gatt.ScanManager;
import com.android.bluetooth.hap.HapClientService;
import com.android.bluetooth.hearingaid.HearingAidService;
import com.android.bluetooth.hfp.HeadsetService;
import com.android.bluetooth.hfpclient.HeadsetClientService;
import com.android.bluetooth.hid.HidDeviceService;
import com.android.bluetooth.hid.HidHostService;
import com.android.bluetooth.le_audio.LeAudioService;
import com.android.bluetooth.map.BluetoothMapService;
import com.android.bluetooth.mapclient.MapClientService;
import com.android.bluetooth.pan.PanService;
import com.android.bluetooth.pbap.BluetoothPbapService;
import com.android.bluetooth.pbapclient.PbapClientService;
import com.android.bluetooth.sap.SapService;
import com.android.bluetooth.sdp.SdpManager;
import com.android.bluetooth.telephony.BluetoothInCallService;
import com.android.bluetooth.vc.VolumeControlService;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.BackgroundThread;
import com.android.modules.utils.BytesMatcher;
import com.android.modules.utils.SynchronousResultReceiver;

import com.google.protobuf.InvalidProtocolBufferException;

import libcore.util.SneakyThrow;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class AdapterService extends Service {
    private static final String TAG = "BluetoothAdapterService";
    private static final boolean DBG = true;
    private static final boolean VERBOSE = false;
    private static final int MIN_ADVT_INSTANCES_FOR_MA = 5;
    private static final int MIN_OFFLOADED_FILTERS = 10;
    private static final int MIN_OFFLOADED_SCAN_STORAGE_BYTES = 1024;
    private static final Duration PENDING_SOCKET_HANDOFF_TIMEOUT = Duration.ofMinutes(1);
    private static final Duration GENERATE_LOCAL_OOB_DATA_TIMEOUT = Duration.ofSeconds(2);

    private final Object mEnergyInfoLock = new Object();
    private int mStackReportedState;
    private long mTxTimeTotalMs;
    private long mRxTimeTotalMs;
    private long mIdleTimeTotalMs;
    private long mEnergyUsedTotalVoltAmpSecMicro;
    private final SparseArray<UidTraffic> mUidTraffic = new SparseArray<>();

    private final ArrayList<String> mStartedProfiles = new ArrayList<>();
    private final ArrayList<ProfileService> mRegisteredProfiles = new ArrayList<>();
    private final ArrayList<ProfileService> mRunningProfiles = new ArrayList<>();

    public static final String ACTION_LOAD_ADAPTER_PROPERTIES =
            "com.android.bluetooth.btservice.action.LOAD_ADAPTER_PROPERTIES";
    public static final String ACTION_SERVICE_STATE_CHANGED =
            "com.android.bluetooth.btservice.action.STATE_CHANGED";
    public static final String EXTRA_ACTION = "action";
    public static final int PROFILE_CONN_REJECTED = 2;

    private static final String ACTION_ALARM_WAKEUP =
            "com.android.bluetooth.btservice.action.ALARM_WAKEUP";

    static final String BLUETOOTH_BTSNOOP_LOG_MODE_PROPERTY = "persist.bluetooth.btsnooplogmode";
    static final String BLUETOOTH_BTSNOOP_DEFAULT_MODE_PROPERTY =
            "persist.bluetooth.btsnoopdefaultmode";
    private String mSnoopLogSettingAtEnable = "empty";
    private String mDefaultSnoopLogSettingAtEnable = "empty";

    public static final String BLUETOOTH_PRIVILEGED =
            android.Manifest.permission.BLUETOOTH_PRIVILEGED;
    static final String BLUETOOTH_PERM = android.Manifest.permission.BLUETOOTH;
    static final String LOCAL_MAC_ADDRESS_PERM = android.Manifest.permission.LOCAL_MAC_ADDRESS;
    static final String RECEIVE_MAP_PERM = android.Manifest.permission.RECEIVE_BLUETOOTH_MAP;

    static final String PHONEBOOK_ACCESS_PERMISSION_PREFERENCE_FILE =
            "phonebook_access_permission";
    static final String MESSAGE_ACCESS_PERMISSION_PREFERENCE_FILE =
            "message_access_permission";
    static final String SIM_ACCESS_PERMISSION_PREFERENCE_FILE = "sim_access_permission";

    private static final int CONTROLLER_ENERGY_UPDATE_TIMEOUT_MILLIS = 30;

    public static final String ACTIVITY_ATTRIBUTION_NO_ACTIVE_DEVICE_ADDRESS =
            "no_active_device_address";

    // Report ID definition
    public enum BqrQualityReportId {
        QUALITY_REPORT_ID_MONITOR_MODE(0x01),
        QUALITY_REPORT_ID_APPROACH_LSTO(0x02),
        QUALITY_REPORT_ID_A2DP_AUDIO_CHOPPY(0x03),
        QUALITY_REPORT_ID_SCO_VOICE_CHOPPY(0x04),
        QUALITY_REPORT_ID_ROOT_INFLAMMATION(0x05),
        QUALITY_REPORT_ID_LMP_LL_MESSAGE_TRACE(0x11),
        QUALITY_REPORT_ID_BT_SCHEDULING_TRACE(0x12),
        QUALITY_REPORT_ID_CONTROLLER_DBG_INFO(0x13);

        private final int value;
        private BqrQualityReportId(int value) {
            this.value = value;
        }
        public int getValue() {
            return value;
        }
    };

    private final ArrayList<DiscoveringPackage> mDiscoveringPackages = new ArrayList<>();

    static {
        classInitNative();
    }

    private static AdapterService sAdapterService;

    public static synchronized AdapterService getAdapterService() {
        return sAdapterService;
    }

    private static synchronized void setAdapterService(AdapterService instance) {
        Log.d(TAG, "setAdapterService() - trying to set service to " + instance);
        if (instance == null) {
            return;
        }
        sAdapterService = instance;
    }

    private static synchronized void clearAdapterService(AdapterService current) {
        if (sAdapterService == current) {
            sAdapterService = null;
        }
    }

    private BluetoothAdapter mAdapter;
    @VisibleForTesting
    AdapterProperties mAdapterProperties;
    private AdapterState mAdapterStateMachine;
    private BondStateMachine mBondStateMachine;
    private JniCallbacks mJniCallbacks;
    private RemoteDevices mRemoteDevices;

    /* TODO: Consider to remove the search API from this class, if changed to use call-back */
    private SdpManager mSdpManager = null;

    private boolean mNativeAvailable;
    private boolean mCleaningUp;
    private final HashMap<BluetoothDevice, ArrayList<IBluetoothMetadataListener>>
            mMetadataListeners = new HashMap<>();
    private final HashMap<String, Integer> mProfileServicesState = new HashMap<String, Integer>();
    private Set<IBluetoothConnectionCallback> mBluetoothConnectionCallbacks = new HashSet<>();
    //Only BluetoothManagerService should be registered
    private RemoteCallbackList<IBluetoothCallback> mCallbacks;
    private int mCurrentRequestId;
    private boolean mQuietmode = false;
    private HashMap<String, CallerInfo> mBondAttemptCallerInfo = new HashMap<>();

    private final Map<UUID, RfcommListenerData> mBluetoothServerSockets = new ConcurrentHashMap<>();
    private final Executor mSocketServersExecutor = r -> new Thread(r).start();

    private AlarmManager mAlarmManager;
    private PendingIntent mPendingAlarm;
    private BatteryStatsManager mBatteryStatsManager;
    private PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;
    private String mWakeLockName;
    private UserManager mUserManager;
    private CompanionDeviceManager mCompanionDeviceManager;

    private PhonePolicy mPhonePolicy;
    private ActiveDeviceManager mActiveDeviceManager;
    private DatabaseManager mDatabaseManager;
    private SilenceDeviceManager mSilenceDeviceManager;
    private CompanionManager mBtCompanionManager;
    private AppOpsManager mAppOps;

    private BluetoothSocketManagerBinder mBluetoothSocketManagerBinder;

    private BluetoothKeystoreService mBluetoothKeystoreService;
    private A2dpService mA2dpService;
    private A2dpSinkService mA2dpSinkService;
    private ActivityAttributionService mActivityAttributionService;
    private HeadsetService mHeadsetService;
    private HeadsetClientService mHeadsetClientService;
    private BluetoothMapService mMapService;
    private MapClientService mMapClientService;
    private HidDeviceService mHidDeviceService;
    private HidHostService mHidHostService;
    private PanService mPanService;
    private BluetoothPbapService mPbapService;
    private PbapClientService mPbapClientService;
    private HearingAidService mHearingAidService;
    private HapClientService mHapClientService;
    private SapService mSapService;
    private VolumeControlService mVolumeControlService;
    private CsipSetCoordinatorService mCsipSetCoordinatorService;
    private LeAudioService mLeAudioService;
    private BassClientService mBassClientService;
    private BatteryService mBatteryService;

    private volatile boolean mTestModeEnabled = false;

    private MetricsLogger mMetricsLogger;

    /**
     * Register a {@link ProfileService} with AdapterService.
     *
     * @param profile the service being added.
     */
    public void addProfile(ProfileService profile) {
        mHandler.obtainMessage(MESSAGE_PROFILE_SERVICE_REGISTERED, profile).sendToTarget();
    }

    /**
     * Unregister a ProfileService with AdapterService.
     *
     * @param profile the service being removed.
     */
    public void removeProfile(ProfileService profile) {
        mHandler.obtainMessage(MESSAGE_PROFILE_SERVICE_UNREGISTERED, profile).sendToTarget();
    }

    /**
     * Notify AdapterService that a ProfileService has started or stopped.
     *
     * @param profile the service being removed.
     * @param state {@link BluetoothAdapter#STATE_ON} or {@link BluetoothAdapter#STATE_OFF}
     */
    public void onProfileServiceStateChanged(ProfileService profile, int state) {
        if (state != BluetoothAdapter.STATE_ON && state != BluetoothAdapter.STATE_OFF) {
            throw new IllegalArgumentException(BluetoothAdapter.nameForState(state));
        }
        Message m = mHandler.obtainMessage(MESSAGE_PROFILE_SERVICE_STATE_CHANGED);
        m.obj = profile;
        m.arg1 = state;
        mHandler.sendMessage(m);
    }

    /**
     * Confirm whether the ProfileService is started expectedly.
     *
     * @param serviceSampleName the service simple name.
     * @return true if the service is started expectedly, false otherwise.
     */
    public boolean isStartedProfile(String serviceSampleName) {
        return mStartedProfiles.contains(serviceSampleName);
    }

    private static final int MESSAGE_PROFILE_SERVICE_STATE_CHANGED = 1;
    private static final int MESSAGE_PROFILE_SERVICE_REGISTERED = 2;
    private static final int MESSAGE_PROFILE_SERVICE_UNREGISTERED = 3;

    class AdapterServiceHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            verboseLog("handleMessage() - Message: " + msg.what);

            switch (msg.what) {
                case MESSAGE_PROFILE_SERVICE_STATE_CHANGED:
                    verboseLog("handleMessage() - MESSAGE_PROFILE_SERVICE_STATE_CHANGED");
                    processProfileServiceStateChanged((ProfileService) msg.obj, msg.arg1);
                    break;
                case MESSAGE_PROFILE_SERVICE_REGISTERED:
                    verboseLog("handleMessage() - MESSAGE_PROFILE_SERVICE_REGISTERED");
                    registerProfileService((ProfileService) msg.obj);
                    break;
                case MESSAGE_PROFILE_SERVICE_UNREGISTERED:
                    verboseLog("handleMessage() - MESSAGE_PROFILE_SERVICE_UNREGISTERED");
                    unregisterProfileService((ProfileService) msg.obj);
                    break;
            }
        }

        private void registerProfileService(ProfileService profile) {
            if (mRegisteredProfiles.contains(profile)) {
                Log.e(TAG, profile.getName() + " already registered.");
                return;
            }
            mRegisteredProfiles.add(profile);
        }

        private void unregisterProfileService(ProfileService profile) {
            if (!mRegisteredProfiles.contains(profile)) {
                Log.e(TAG, profile.getName() + " not registered (UNREGISTER).");
                return;
            }
            mRegisteredProfiles.remove(profile);
        }

        private void processProfileServiceStateChanged(ProfileService profile, int state) {
            switch (state) {
                case BluetoothAdapter.STATE_ON:
                    if (!mRegisteredProfiles.contains(profile)) {
                        Log.e(TAG, profile.getName() + " not registered (STATE_ON).");
                        return;
                    }
                    if (mRunningProfiles.contains(profile)) {
                        Log.e(TAG, profile.getName() + " already running.");
                        return;
                    }
                    mRunningProfiles.add(profile);
                    // TODO(b/228875190): GATT is assumed supported. GATT starting triggers hardware
                    // initializtion. Configuring a device without GATT causes start up failures.
                    if (GattService.class.getSimpleName().equals(profile.getName())) {
                        enableNative();
                    } else if (mRegisteredProfiles.size() == Config.getSupportedProfiles().length
                            && mRegisteredProfiles.size() == mRunningProfiles.size()) {
                        mAdapterProperties.onBluetoothReady();
                        updateUuids();
                        setBluetoothClassFromConfig();
                        initProfileServices();
                        getAdapterPropertyNative(AbstractionLayer.BT_PROPERTY_LOCAL_IO_CAPS);
                        getAdapterPropertyNative(AbstractionLayer.BT_PROPERTY_LOCAL_IO_CAPS_BLE);
                        getAdapterPropertyNative(AbstractionLayer.BT_PROPERTY_DYNAMIC_AUDIO_BUFFER);
                        mAdapterStateMachine.sendMessage(AdapterState.BREDR_STARTED);
                        mBtCompanionManager.loadCompanionInfo();
                    }
                    break;
                case BluetoothAdapter.STATE_OFF:
                    if (!mRegisteredProfiles.contains(profile)) {
                        Log.e(TAG, profile.getName() + " not registered (STATE_OFF).");
                        return;
                    }
                    if (!mRunningProfiles.contains(profile)) {
                        Log.e(TAG, profile.getName() + " not running.");
                        return;
                    }
                    mRunningProfiles.remove(profile);
                    // TODO(b/228875190): GATT is assumed supported. GATT is expected to be the only
                    // profile available in the "BLE ON" state. If only GATT is left, send
                    // BREDR_STOPPED. If GATT is stopped, deinitialize the hardware.
                    if ((mRunningProfiles.size() == 1 && (GattService.class.getSimpleName()
                            .equals(mRunningProfiles.get(0).getName())))) {
                        mAdapterStateMachine.sendMessage(AdapterState.BREDR_STOPPED);
                    } else if (mRunningProfiles.size() == 0) {
                        disableNative();
                    }
                    break;
                default:
                    Log.e(TAG, "Unhandled profile state: " + state);
            }
        }
    }

    private final AdapterServiceHandler mHandler = new AdapterServiceHandler();

    @Override
    public void onCreate() {
        super.onCreate();
        initMetricsLogger();
        debugLog("onCreate()");
        mDeviceConfigListener.start();
        mRemoteDevices = new RemoteDevices(this, Looper.getMainLooper());
        mRemoteDevices.init();
        clearDiscoveringPackages();
        mBinder = new AdapterServiceBinder(this);
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mAdapterProperties = new AdapterProperties(this);
        mAdapterStateMachine = AdapterState.make(this);
        mJniCallbacks = new JniCallbacks(this, mAdapterProperties);
        mBluetoothKeystoreService = new BluetoothKeystoreService(isCommonCriteriaMode());
        mBluetoothKeystoreService.start();
        int configCompareResult = mBluetoothKeystoreService.getCompareResult();

        // Start tracking Binder latency for the bluetooth process.
        BluetoothFrameworkInitializer.initializeBinderCallsStats(getApplicationContext());

        // Android TV doesn't show consent dialogs for just works and encryption only le pairing
        boolean isAtvDevice = getApplicationContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_LEANBACK_ONLY);
        mUserManager = getSystemService(UserManager.class);
        initNative(mUserManager.isGuestUser(), isCommonCriteriaMode(), configCompareResult,
                getInitFlags(), isAtvDevice, getApplicationInfo().dataDir);
        mNativeAvailable = true;
        mCallbacks = new RemoteCallbackList<IBluetoothCallback>();
        mAppOps = getSystemService(AppOpsManager.class);
        //Load the name and address
        getAdapterPropertyNative(AbstractionLayer.BT_PROPERTY_BDADDR);
        getAdapterPropertyNative(AbstractionLayer.BT_PROPERTY_BDNAME);
        getAdapterPropertyNative(AbstractionLayer.BT_PROPERTY_CLASS_OF_DEVICE);
        mAlarmManager = getSystemService(AlarmManager.class);
        mPowerManager = getSystemService(PowerManager.class);
        mBatteryStatsManager = getSystemService(BatteryStatsManager.class);
        mCompanionDeviceManager = getSystemService(CompanionDeviceManager.class);

        mBluetoothKeystoreService.initJni();

        mSdpManager = SdpManager.init(this);
        registerReceiver(mAlarmBroadcastReceiver, new IntentFilter(ACTION_ALARM_WAKEUP));

        mDatabaseManager = new DatabaseManager(this);
        mDatabaseManager.start(MetadataDatabase.createDatabase(this));

        boolean isAutomotiveDevice = getApplicationContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE);

        /*
         * Phone policy is specific to phone implementations and hence if a device wants to exclude
         * it out then it can be disabled by using the flag below. Phone policy is never used on
         * Android Automotive OS builds, in favor of a policy currently located in
         * CarBluetoothService.
         */
        if (!isAutomotiveDevice && getResources().getBoolean(R.bool.enable_phone_policy)) {
            Log.i(TAG, "Phone policy enabled");
            mPhonePolicy = new PhonePolicy(this, new ServiceFactory());
            mPhonePolicy.start();
        } else {
            Log.i(TAG, "Phone policy disabled");
        }

        mActiveDeviceManager = new ActiveDeviceManager(this, new ServiceFactory());
        mActiveDeviceManager.start();

        mSilenceDeviceManager = new SilenceDeviceManager(this, new ServiceFactory(),
                Looper.getMainLooper());
        mSilenceDeviceManager.start();

        mBtCompanionManager = new CompanionManager(this, new ServiceFactory());

        mBluetoothSocketManagerBinder = new BluetoothSocketManagerBinder(this);

        mActivityAttributionService = new ActivityAttributionService();
        mActivityAttributionService.start();

        setAdapterService(this);

        invalidateBluetoothCaches();

        // First call to getSharedPreferences will result in a file read into
        // memory cache. Call it here asynchronously to avoid potential ANR
        // in the future
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                getSharedPreferences(PHONEBOOK_ACCESS_PERMISSION_PREFERENCE_FILE,
                        Context.MODE_PRIVATE);
                getSharedPreferences(MESSAGE_ACCESS_PERMISSION_PREFERENCE_FILE,
                        Context.MODE_PRIVATE);
                getSharedPreferences(SIM_ACCESS_PERMISSION_PREFERENCE_FILE, Context.MODE_PRIVATE);
                return null;
            }
        }.execute();

        try {
            int systemUiUid = getApplicationContext()
                    .createContextAsUser(UserHandle.SYSTEM, /* flags= */ 0)
                    .getPackageManager()
                    .getPackageUid("com.android.systemui", PackageManager.MATCH_SYSTEM_ONLY);

            Utils.setSystemUiUid(systemUiUid);
        } catch (PackageManager.NameNotFoundException e) {
            // Some platforms, such as wearables do not have a system ui.
            Log.w(TAG, "Unable to resolve SystemUI's UID.", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        debugLog("onBind()");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        debugLog("onUnbind() - calling cleanup");
        cleanup();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        debugLog("onDestroy()");
        if (!isMock()) {
            // TODO(b/27859763)
            Log.i(TAG, "Force exit to cleanup internal state in Bluetooth stack");
            System.exit(0);
        }
    }

    private boolean initMetricsLogger() {
        if (mMetricsLogger != null) {
            return false;
        }
        mMetricsLogger = MetricsLogger.getInstance();
        return mMetricsLogger.init(this);
    }

    private boolean closeMetricsLogger() {
        if (mMetricsLogger == null) {
            return false;
        }
        boolean result = mMetricsLogger.close();
        mMetricsLogger = null;
        return result;
    }

    public void setMetricsLogger(MetricsLogger metricsLogger) {
        mMetricsLogger = metricsLogger;
    }

    void bringUpBle() {
        debugLog("bleOnProcessStart()");

        if (getResources().getBoolean(
                R.bool.config_bluetooth_reload_supported_profiles_when_enabled)) {
            Config.init(getApplicationContext());
        }

        // Reset |mRemoteDevices| whenever BLE is turned off then on
        // This is to replace the fact that |mRemoteDevices| was
        // reinitialized in previous code.
        //
        // TODO(apanicke): The reason is unclear but
        // I believe it is to clear the variable every time BLE was
        // turned off then on. The same effect can be achieved by
        // calling cleanup but this may not be necessary at all
        // We should figure out why this is needed later
        mRemoteDevices.reset();
        mAdapterProperties.init(mRemoteDevices);

        debugLog("bleOnProcessStart() - Make Bond State Machine");
        mBondStateMachine = BondStateMachine.make(this, mAdapterProperties, mRemoteDevices);

        mJniCallbacks.init(mBondStateMachine, mRemoteDevices);

        mBatteryStatsManager.reportBleScanReset();
        BluetoothStatsLog.write_non_chained(BluetoothStatsLog.BLE_SCAN_STATE_CHANGED, -1, null,
                BluetoothStatsLog.BLE_SCAN_STATE_CHANGED__STATE__RESET, false, false, false);

        // TODO(b/228875190): GATT is assumed supported. As a result, we don't respect the
        // configuration sysprop. Configuring a device without GATT, although rare, will cause stack
        // start up errors yielding init loops.
        if (!GattService.isEnabled()) {
            Log.w(TAG,
                    "GATT is configured off but the stack assumes it to be enabled. Start anyway.");
        }
        setProfileServiceState(GattService.class, BluetoothAdapter.STATE_ON);
    }

    void bringDownBle() {
        stopGattProfileService();
    }

    void stateChangeCallback(int status) {
        if (status == AbstractionLayer.BT_STATE_OFF) {
            debugLog("stateChangeCallback: disableNative() completed");
            mAdapterStateMachine.sendMessage(AdapterState.BLE_STOPPED);
        } else if (status == AbstractionLayer.BT_STATE_ON) {
            mAdapterStateMachine.sendMessage(AdapterState.BLE_STARTED);
        } else {
            Log.e(TAG, "Incorrect status " + status + " in stateChangeCallback");
        }
    }

    /**
     * Sets the Bluetooth CoD value of the local adapter if there exists a config value for it.
     */
    void setBluetoothClassFromConfig() {
        int bluetoothClassConfig = retrieveBluetoothClassConfig();
        if (bluetoothClassConfig != 0) {
            mAdapterProperties.setBluetoothClass(new BluetoothClass(bluetoothClassConfig));
        }
    }

    private int retrieveBluetoothClassConfig() {
        return Settings.Global.getInt(
                getContentResolver(), Settings.Global.BLUETOOTH_CLASS_OF_DEVICE, 0);
    }

    void startProfileServices() {
        debugLog("startCoreServices()");
        Class[] supportedProfileServices = Config.getSupportedProfiles();
        // TODO(b/228875190): GATT is assumed supported. If we support no other profiles then just
        // move on to BREDR_STARTED. Note that configuring GATT to NOT supported will cause adapter
        // initialization failures
        if (supportedProfileServices.length == 1 && GattService.class.getSimpleName()
                .equals(supportedProfileServices[0].getSimpleName())) {
            mAdapterProperties.onBluetoothReady();
            updateUuids();
            setBluetoothClassFromConfig();
            mAdapterStateMachine.sendMessage(AdapterState.BREDR_STARTED);
        } else {
            setAllProfileServiceStates(supportedProfileServices, BluetoothAdapter.STATE_ON);
        }
    }

    void stopProfileServices() {
        // Make sure to stop classic background tasks now
        cancelDiscoveryNative();
        mAdapterProperties.setScanMode(BluetoothAdapter.SCAN_MODE_NONE);

        Class[] supportedProfileServices = Config.getSupportedProfiles();
        // TODO(b/228875190): GATT is assumed supported. If we support no profiles then just move on
        // to BREDR_STOPPED
        if (supportedProfileServices.length == 1 && (mRunningProfiles.size() == 1
                && GattService.class.getSimpleName().equals(mRunningProfiles.get(0).getName()))) {
            debugLog("stopProfileServices() - No profiles services to stop or already stopped.");
            mAdapterStateMachine.sendMessage(AdapterState.BREDR_STOPPED);
        } else {
            setAllProfileServiceStates(supportedProfileServices, BluetoothAdapter.STATE_OFF);
        }
    }

    private void stopGattProfileService() {
        mAdapterProperties.onBleDisable();
        if (mRunningProfiles.size() == 0) {
            debugLog("stopGattProfileService() - No profiles services to stop.");
            mAdapterStateMachine.sendMessage(AdapterState.BLE_STOPPED);
        }
        setProfileServiceState(GattService.class, BluetoothAdapter.STATE_OFF);
    }

    private void invalidateBluetoothGetStateCache() {
        BluetoothAdapter.invalidateBluetoothGetStateCache();
    }

    void updateLeAudioProfileServiceState() {
        HashSet<Class> nonSupportedProfiles = new HashSet<>();

        if (!isLeConnectedIsochronousStreamCentralSupported()) {
            nonSupportedProfiles.addAll(Config.geLeAudioUnicastProfiles());
        }

        if (!isLeAudioBroadcastAssistantSupported()) {
            nonSupportedProfiles.add(BassClientService.class);
        }

        if (!isLeAudioBroadcastSourceSupported()) {
            Config.updateSupportedProfileMask(
                    false, LeAudioService.class, BluetoothProfile.LE_AUDIO_BROADCAST);
        }

        if (!nonSupportedProfiles.isEmpty()) {
            // Remove non-supported profiles from the supported list
            // since the controller doesn't support
            Config.removeProfileFromSupportedList(nonSupportedProfiles);

            // Disable the non-supported profiles service
            for (Class profileService : nonSupportedProfiles) {
                if (isStartedProfile(profileService.getSimpleName())) {
                    setProfileServiceState(profileService, BluetoothAdapter.STATE_OFF);
                }
            }
        }
    }

    void updateAdapterState(int prevState, int newState) {
        mAdapterProperties.setState(newState);
        invalidateBluetoothGetStateCache();
        if (mCallbacks != null) {
            int n = mCallbacks.beginBroadcast();
            debugLog("updateAdapterState() - Broadcasting state " + BluetoothAdapter.nameForState(
                    newState) + " to " + n + " receivers.");
            for (int i = 0; i < n; i++) {
                try {
                    mCallbacks.getBroadcastItem(i).onBluetoothStateChange(prevState, newState);
                } catch (RemoteException e) {
                    debugLog("updateAdapterState() - Callback #" + i + " failed (" + e + ")");
                }
            }
            mCallbacks.finishBroadcast();
        }

        // Turn the Adapter all the way off if we are disabling and the snoop log setting changed.
        if (newState == BluetoothAdapter.STATE_BLE_TURNING_ON) {
            mSnoopLogSettingAtEnable =
                    SystemProperties.get(BLUETOOTH_BTSNOOP_LOG_MODE_PROPERTY, "empty");
            mDefaultSnoopLogSettingAtEnable =
                    Settings.Global.getString(getContentResolver(),
                            Settings.Global.BLUETOOTH_BTSNOOP_DEFAULT_MODE);
            BluetoothProperties.snoop_default_mode(
                    BluetoothProperties.snoop_default_mode_values.DISABLED);
            for (BluetoothProperties.snoop_default_mode_values value :
                    BluetoothProperties.snoop_default_mode_values.values()) {
                if (value.getPropValue().equals(mDefaultSnoopLogSettingAtEnable)) {
                    BluetoothProperties.snoop_default_mode(value);
                }
            }
        } else if (newState == BluetoothAdapter.STATE_BLE_ON
                   && prevState != BluetoothAdapter.STATE_OFF) {
            String snoopLogSetting =
                    SystemProperties.get(BLUETOOTH_BTSNOOP_LOG_MODE_PROPERTY, "empty");
            String snoopDefaultModeSetting =
                    Settings.Global.getString(getContentResolver(),
                            Settings.Global.BLUETOOTH_BTSNOOP_DEFAULT_MODE);

            if (!TextUtils.equals(mSnoopLogSettingAtEnable, snoopLogSetting)
                    || !TextUtils.equals(mDefaultSnoopLogSettingAtEnable,
                            snoopDefaultModeSetting)) {
                mAdapterStateMachine.sendMessage(AdapterState.BLE_TURN_OFF);
            }
        }
    }

    void linkQualityReportCallback(
            long timestamp,
            int reportId,
            int rssi,
            int snr,
            int retransmissionCount,
            int packetsNotReceiveCount,
            int negativeAcknowledgementCount) {
        BluetoothInCallService bluetoothInCallService = BluetoothInCallService.getInstance();

        if (reportId == BqrQualityReportId.QUALITY_REPORT_ID_SCO_VOICE_CHOPPY.getValue()) {
            if (bluetoothInCallService == null) {
                Log.w(TAG, "No BluetoothInCallService while trying to send BQR."
                        + " timestamp: " + timestamp + " reportId: " + reportId
                        + " rssi: " + rssi + " snr: " + snr
                        + " retransmissionCount: " + retransmissionCount
                        + " packetsNotReceiveCount: " + packetsNotReceiveCount
                        + " negativeAcknowledgementCount: " + negativeAcknowledgementCount);
                return;
            }
            bluetoothInCallService.sendBluetoothCallQualityReport(
                    timestamp, rssi, snr, retransmissionCount,
                    packetsNotReceiveCount, negativeAcknowledgementCount);
        }
    }

    void switchBufferSizeCallback(boolean isLowLatencyBufferSize) {
        List<BluetoothDevice> activeDevices = getActiveDevices(BluetoothProfile.A2DP);
        if (activeDevices.size() != 1) {
            errorLog(
                    "Cannot switch buffer size. The number of A2DP active devices is "
                            + activeDevices.size());
        }

        // Send intent to fastpair
        Intent switchBufferSizeIntent = new Intent(BluetoothDevice.ACTION_SWITCH_BUFFER_SIZE);
        switchBufferSizeIntent.setClassName(
                getString(com.android.bluetooth.R.string.peripheral_link_package),
                getString(com.android.bluetooth.R.string.peripheral_link_package)
                        + getString(com.android.bluetooth.R.string.peripheral_link_service));
        switchBufferSizeIntent.putExtra(BluetoothDevice.EXTRA_DEVICE, activeDevices.get(0));
        switchBufferSizeIntent.putExtra(
                BluetoothDevice.EXTRA_LOW_LATENCY_BUFFER_SIZE, isLowLatencyBufferSize);
        sendBroadcast(switchBufferSizeIntent);
    }

    void switchCodecCallback(boolean isLowLatencyBufferSize) {
        List<BluetoothDevice> activeDevices = getActiveDevices(BluetoothProfile.A2DP);
        if (activeDevices.size() != 1) {
            errorLog(
                    "Cannot switch buffer size. The number of A2DP active devices is "
                            + activeDevices.size());
            return;
        }
        mA2dpService.switchCodecByBufferSize(activeDevices.get(0), isLowLatencyBufferSize);
    }

    void cleanup() {
        debugLog("cleanup()");
        if (mCleaningUp) {
            errorLog("cleanup() - Service already starting to cleanup, ignoring request...");
            return;
        }

        closeMetricsLogger();

        clearAdapterService(this);

        mCleaningUp = true;
        invalidateBluetoothCaches();

        unregisterReceiver(mAlarmBroadcastReceiver);

        stopRfcommServerSockets();

        if (mPendingAlarm != null) {
            mAlarmManager.cancel(mPendingAlarm);
            mPendingAlarm = null;
        }

        // This wake lock release may also be called concurrently by
        // {@link #releaseWakeLock(String lockName)}, so a synchronization is needed here.
        synchronized (this) {
            if (mWakeLock != null) {
                if (mWakeLock.isHeld()) {
                    mWakeLock.release();
                }
                mWakeLock = null;
            }
        }

        if (mDatabaseManager != null) {
            mDatabaseManager.cleanup();
        }

        if (mAdapterStateMachine != null) {
            mAdapterStateMachine.doQuit();
        }

        if (mBondStateMachine != null) {
            mBondStateMachine.doQuit();
        }

        if (mRemoteDevices != null) {
            mRemoteDevices.cleanup();
        }

        if (mSdpManager != null) {
            mSdpManager.cleanup();
            mSdpManager = null;
        }

        if (mActivityAttributionService != null) {
            mActivityAttributionService.cleanup();
        }

        if (mNativeAvailable) {
            debugLog("cleanup() - Cleaning up adapter native");
            cleanupNative();
            mNativeAvailable = false;
        }

        if (mAdapterProperties != null) {
            mAdapterProperties.cleanup();
        }

        if (mJniCallbacks != null) {
            mJniCallbacks.cleanup();
        }

        if (mBluetoothKeystoreService != null) {
            debugLog("cleanup(): mBluetoothKeystoreService.cleanup()");
            mBluetoothKeystoreService.cleanup();
        }

        if (mPhonePolicy != null) {
            mPhonePolicy.cleanup();
        }

        if (mSilenceDeviceManager != null) {
            mSilenceDeviceManager.cleanup();
        }

        if (mActiveDeviceManager != null) {
            mActiveDeviceManager.cleanup();
        }

        if (mProfileServicesState != null) {
            mProfileServicesState.clear();
        }

        if (mBluetoothSocketManagerBinder != null) {
            mBluetoothSocketManagerBinder.cleanUp();
            mBluetoothSocketManagerBinder = null;
        }

        if (mBinder != null) {
            mBinder.cleanup();
            mBinder = null;  //Do not remove. Otherwise Binder leak!
        }

        if (mCallbacks != null) {
            mCallbacks.kill();
        }
    }

    private void invalidateBluetoothCaches() {
        BluetoothAdapter.invalidateGetProfileConnectionStateCache();
        BluetoothAdapter.invalidateIsOffloadedFilteringSupportedCache();
        BluetoothDevice.invalidateBluetoothGetBondStateCache();
        BluetoothAdapter.invalidateBluetoothGetStateCache();
        BluetoothAdapter.invalidateGetAdapterConnectionStateCache();
        BluetoothMap.invalidateBluetoothGetConnectionStateCache();
        BluetoothSap.invalidateBluetoothGetConnectionStateCache();
    }

    private void setProfileServiceState(Class service, int state) {
        if (state == BluetoothAdapter.STATE_ON) {
            mStartedProfiles.add(service.getSimpleName());
        } else if (state == BluetoothAdapter.STATE_OFF) {
            mStartedProfiles.remove(service.getSimpleName());
        }
        Intent intent = new Intent(this, service);
        intent.putExtra(EXTRA_ACTION, ACTION_SERVICE_STATE_CHANGED);
        intent.putExtra(BluetoothAdapter.EXTRA_STATE, state);
        startService(intent);
    }

    private void setAllProfileServiceStates(Class[] services, int state) {
        for (Class service : services) {
            // TODO(b/228875190): GATT is assumed supported and treated differently as part of the
            // "BLE ON" state, despite GATT not being BLE specific.
            if (GattService.class.getSimpleName().equals(service.getSimpleName())) {
                continue;
            }
            setProfileServiceState(service, state);
        }
    }

    /**
     * Verifies whether the profile is supported by the local bluetooth adapter by checking a
     * bitmask of its supported profiles
     *
     * @param remoteDeviceUuids is an array of all supported profiles by the remote device
     * @param localDeviceUuids  is an array of all supported profiles by the local device
     * @param profile           is the profile we are checking for support
     * @param device            is the remote device we wish to connect to
     * @return true if the profile is supported by both the local and remote device, false otherwise
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    private boolean isSupported(ParcelUuid[] localDeviceUuids, ParcelUuid[] remoteDeviceUuids,
            int profile, BluetoothDevice device) {
        if (remoteDeviceUuids == null || remoteDeviceUuids.length == 0) {
            Log.e(TAG, "isSupported: Remote Device Uuids Empty");
        }

        if (profile == BluetoothProfile.HEADSET) {
            return (Utils.arrayContains(localDeviceUuids, BluetoothUuid.HSP_AG)
                    && Utils.arrayContains(remoteDeviceUuids, BluetoothUuid.HSP))
                    || (Utils.arrayContains(localDeviceUuids, BluetoothUuid.HFP_AG)
                    && Utils.arrayContains(remoteDeviceUuids, BluetoothUuid.HFP));
        }
        if (profile == BluetoothProfile.HEADSET_CLIENT) {
            return Utils.arrayContains(remoteDeviceUuids, BluetoothUuid.HFP_AG)
                    && Utils.arrayContains(localDeviceUuids, BluetoothUuid.HFP);
        }
        if (profile == BluetoothProfile.A2DP) {
            return Utils.arrayContains(remoteDeviceUuids, BluetoothUuid.ADV_AUDIO_DIST)
                    || Utils.arrayContains(remoteDeviceUuids, BluetoothUuid.A2DP_SINK);
        }
        if (profile == BluetoothProfile.A2DP_SINK) {
            return Utils.arrayContains(remoteDeviceUuids, BluetoothUuid.ADV_AUDIO_DIST)
                    || Utils.arrayContains(remoteDeviceUuids, BluetoothUuid.A2DP_SOURCE);
        }
        if (profile == BluetoothProfile.OPP) {
            return Utils.arrayContains(remoteDeviceUuids, BluetoothUuid.OBEX_OBJECT_PUSH);
        }
        if (profile == BluetoothProfile.HID_HOST) {
            return Utils.arrayContains(remoteDeviceUuids, BluetoothUuid.HID)
                    || Utils.arrayContains(remoteDeviceUuids, BluetoothUuid.HOGP);
        }
        if (profile == BluetoothProfile.HID_DEVICE) {
            return mHidDeviceService.getConnectionState(device)
                    == BluetoothProfile.STATE_DISCONNECTED;
        }
        if (profile == BluetoothProfile.PAN) {
            return Utils.arrayContains(remoteDeviceUuids, BluetoothUuid.NAP);
        }
        if (profile == BluetoothProfile.MAP) {
            return mMapService.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED;
        }
        if (profile == BluetoothProfile.PBAP) {
            return mPbapService.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED;
        }
        if (profile == BluetoothProfile.MAP_CLIENT) {
            return true;
        }
        if (profile == BluetoothProfile.PBAP_CLIENT) {
            return Utils.arrayContains(localDeviceUuids, BluetoothUuid.PBAP_PCE)
                    && Utils.arrayContains(remoteDeviceUuids, BluetoothUuid.PBAP_PSE);
        }
        if (profile == BluetoothProfile.HEARING_AID) {
            return Utils.arrayContains(remoteDeviceUuids, BluetoothUuid.HEARING_AID);
        }
        if (profile == BluetoothProfile.SAP) {
            return Utils.arrayContains(remoteDeviceUuids, BluetoothUuid.SAP);
        }
        if (profile == BluetoothProfile.VOLUME_CONTROL) {
            return Utils.arrayContains(remoteDeviceUuids, BluetoothUuid.VOLUME_CONTROL);
        }
        if (profile == BluetoothProfile.CSIP_SET_COORDINATOR) {
            return Utils.arrayContains(remoteDeviceUuids, BluetoothUuid.COORDINATED_SET);
        }
        if (profile == BluetoothProfile.LE_AUDIO) {
            return Utils.arrayContains(remoteDeviceUuids, BluetoothUuid.LE_AUDIO);
        }
        if (profile == BluetoothProfile.HAP_CLIENT) {
            return Utils.arrayContains(remoteDeviceUuids, BluetoothUuid.HAS);
        }
        if (profile == BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT) {
            return Utils.arrayContains(remoteDeviceUuids, BluetoothUuid.BASS);
        }
        if (profile == BluetoothProfile.BATTERY) {
            return Utils.arrayContains(remoteDeviceUuids, BluetoothUuid.BATTERY);
        }

        Log.e(TAG, "isSupported: Unexpected profile passed in to function: " + profile);
        return false;
    }

    /**
     * Checks if any profile is enabled for the given device
     *
     * @param device is the device for which we are checking if any profiles are enabled
     * @return true if any profile is enabled, false otherwise
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    boolean isAnyProfileEnabled(BluetoothDevice device) {
        if (mA2dpService != null && mA2dpService.getConnectionPolicy(device)
                > BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            return true;
        }
        if (mA2dpSinkService != null && mA2dpSinkService.getConnectionPolicy(device)
                > BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            return true;
        }
        if (mHeadsetService != null && mHeadsetService.getConnectionPolicy(device)
                > BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            return true;
        }
        if (mHeadsetClientService != null && mHeadsetClientService.getConnectionPolicy(device)
                > BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            return true;
        }
        if (mMapClientService != null && mMapClientService.getConnectionPolicy(device)
                > BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            return true;
        }
        if (mHidHostService != null && mHidHostService.getConnectionPolicy(device)
                > BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            return true;
        }
        if (mPanService != null && mPanService.getConnectionPolicy(device)
                > BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            return true;
        }
        if (mPbapClientService != null && mPbapClientService.getConnectionPolicy(device)
                > BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            return true;
        }
        if (mHearingAidService != null && mHearingAidService.getConnectionPolicy(device)
                > BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            return true;
        }
        if (mHapClientService != null && mHapClientService.getConnectionPolicy(device)
                > BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            return true;
        }
        if (mVolumeControlService != null && mVolumeControlService.getConnectionPolicy(device)
                > BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            return true;
        }
        if (mCsipSetCoordinatorService != null
                && mCsipSetCoordinatorService.getConnectionPolicy(device)
                        > BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            return true;
        }
        if (mLeAudioService != null && mLeAudioService.getConnectionPolicy(device)
                > BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            return true;
        }
        if (mBassClientService != null && mBassClientService.getConnectionPolicy(device)
                 > BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            return true;
        }
        if (mBatteryService != null && mBatteryService.getConnectionPolicy(device)
                > BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            return true;
        }
        return false;
    }

    /**
     * Connects only available profiles
     * (those with {@link BluetoothProfile#CONNECTION_POLICY_ALLOWED})
     *
     * @param device is the device with which we are connecting the profiles
     * @return {@link BluetoothStatusCodes#SUCCESS}
     */
    @RequiresPermission(allOf = {
            android.Manifest.permission.BLUETOOTH_PRIVILEGED,
            android.Manifest.permission.MODIFY_PHONE_STATE,
    })
    private int connectEnabledProfiles(BluetoothDevice device) {
        ParcelUuid[] remoteDeviceUuids = getRemoteUuids(device);
        ParcelUuid[] localDeviceUuids = mAdapterProperties.getUuids();

        if (mA2dpService != null && isSupported(localDeviceUuids, remoteDeviceUuids,
                BluetoothProfile.A2DP, device) && mA2dpService.getConnectionPolicy(device)
                > BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            Log.i(TAG, "connectEnabledProfiles: Connecting A2dp");
            mA2dpService.connect(device);
        }
        if (mA2dpSinkService != null && isSupported(localDeviceUuids, remoteDeviceUuids,
                BluetoothProfile.A2DP_SINK, device) && mA2dpSinkService.getConnectionPolicy(device)
                > BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            Log.i(TAG, "connectEnabledProfiles: Connecting A2dp Sink");
            mA2dpSinkService.connect(device);
        }
        if (mHeadsetService != null && isSupported(localDeviceUuids, remoteDeviceUuids,
                BluetoothProfile.HEADSET, device) && mHeadsetService.getConnectionPolicy(device)
                > BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            Log.i(TAG, "connectEnabledProfiles: Connecting Headset Profile");
            mHeadsetService.connect(device);
        }
        if (mHeadsetClientService != null && isSupported(localDeviceUuids, remoteDeviceUuids,
                BluetoothProfile.HEADSET_CLIENT, device)
                && mHeadsetClientService.getConnectionPolicy(device)
                > BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            Log.i(TAG, "connectEnabledProfiles: Connecting HFP");
            mHeadsetClientService.connect(device);
        }
        if (mMapClientService != null && isSupported(localDeviceUuids, remoteDeviceUuids,
                BluetoothProfile.MAP_CLIENT, device)
                && mMapClientService.getConnectionPolicy(device)
                > BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            Log.i(TAG, "connectEnabledProfiles: Connecting MAP");
            mMapClientService.connect(device);
        }
        if (mHidHostService != null && isSupported(localDeviceUuids, remoteDeviceUuids,
                BluetoothProfile.HID_HOST, device) && mHidHostService.getConnectionPolicy(device)
                > BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            Log.i(TAG, "connectEnabledProfiles: Connecting Hid Host Profile");
            mHidHostService.connect(device);
        }
        if (mPanService != null && isSupported(localDeviceUuids, remoteDeviceUuids,
                BluetoothProfile.PAN, device) && mPanService.getConnectionPolicy(device)
                > BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            Log.i(TAG, "connectEnabledProfiles: Connecting Pan Profile");
            mPanService.connect(device);
        }
        if (mPbapClientService != null && isSupported(localDeviceUuids, remoteDeviceUuids,
                BluetoothProfile.PBAP_CLIENT, device)
                && mPbapClientService.getConnectionPolicy(device)
                > BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            Log.i(TAG, "connectEnabledProfiles: Connecting Pbap");
            mPbapClientService.connect(device);
        }
        if (mHearingAidService != null && isSupported(localDeviceUuids, remoteDeviceUuids,
                BluetoothProfile.HEARING_AID, device)
                && mHearingAidService.getConnectionPolicy(device)
                > BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            Log.i(TAG, "connectEnabledProfiles: Connecting Hearing Aid Profile");
            mHearingAidService.connect(device);
        }
        if (mHapClientService != null && isSupported(localDeviceUuids, remoteDeviceUuids,
                BluetoothProfile.HAP_CLIENT, device)
                && mHapClientService.getConnectionPolicy(device)
                > BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            Log.i(TAG, "connectEnabledProfiles: Connecting HAS Profile");
            mHapClientService.connect(device);
        }
        if (mVolumeControlService != null && isSupported(localDeviceUuids, remoteDeviceUuids,
                BluetoothProfile.VOLUME_CONTROL, device)
                && mVolumeControlService.getConnectionPolicy(device)
                > BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            Log.i(TAG, "connectEnabledProfiles: Connecting Volume Control Profile");
            mVolumeControlService.connect(device);
        }
        if (mCsipSetCoordinatorService != null
                && isSupported(localDeviceUuids, remoteDeviceUuids,
                        BluetoothProfile.CSIP_SET_COORDINATOR, device)
                && mCsipSetCoordinatorService.getConnectionPolicy(device)
                        > BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            Log.i(TAG, "connectEnabledProfiles: Connecting Coordinated Set Profile");
            mCsipSetCoordinatorService.connect(device);
        }
        if (mLeAudioService != null && isSupported(localDeviceUuids, remoteDeviceUuids,
                BluetoothProfile.LE_AUDIO, device)
                && mLeAudioService.getConnectionPolicy(device)
                        > BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            Log.i(TAG, "connectEnabledProfiles: Connecting LeAudio profile (BAP)");
            mLeAudioService.connect(device);
        }
        if (mBassClientService != null && isSupported(localDeviceUuids, remoteDeviceUuids,
                BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT, device)
                && mBassClientService.getConnectionPolicy(device)
                        > BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            Log.i(TAG, "connectEnabledProfiles: Connecting LE Broadcast Assistant Profile");
            mBassClientService.connect(device);
        }
        if (mBatteryService != null
                && isSupported(
                        localDeviceUuids, remoteDeviceUuids, BluetoothProfile.BATTERY, device)
                && mBatteryService.getConnectionPolicy(device)
                        > BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            Log.i(TAG, "connectEnabledProfiles: Connecting Battery Service");
            mBatteryService.connect(device);
        }
        return BluetoothStatusCodes.SUCCESS;
    }

    /**
     * Verifies that all bluetooth profile services are running
     *
     * @return true if all bluetooth profile services running, false otherwise
     */
    private boolean profileServicesRunning() {
        if (mRegisteredProfiles.size() == Config.getSupportedProfiles().length
                && mRegisteredProfiles.size() == mRunningProfiles.size()) {
            return true;
        }

        Log.e(TAG, "profileServicesRunning: One or more supported services not running");
        return false;
    }

    /**
     * Initializes all the profile services fields
     */
    private void initProfileServices() {
        Log.i(TAG, "initProfileServices: Initializing all bluetooth profile services");
        mA2dpService = A2dpService.getA2dpService();
        mA2dpSinkService = A2dpSinkService.getA2dpSinkService();
        mHeadsetService = HeadsetService.getHeadsetService();
        mHeadsetClientService = HeadsetClientService.getHeadsetClientService();
        mMapService = BluetoothMapService.getBluetoothMapService();
        mMapClientService = MapClientService.getMapClientService();
        mHidDeviceService = HidDeviceService.getHidDeviceService();
        mHidHostService = HidHostService.getHidHostService();
        mPanService = PanService.getPanService();
        mPbapService = BluetoothPbapService.getBluetoothPbapService();
        mPbapClientService = PbapClientService.getPbapClientService();
        mHearingAidService = HearingAidService.getHearingAidService();
        mHapClientService = HapClientService.getHapClientService();
        mSapService = SapService.getSapService();
        mVolumeControlService = VolumeControlService.getVolumeControlService();
        mCsipSetCoordinatorService = CsipSetCoordinatorService.getCsipSetCoordinatorService();
        mLeAudioService = LeAudioService.getLeAudioService();
        mBassClientService = BassClientService.getBassClientService();
        mBatteryService = BatteryService.getBatteryService();
    }

    @BluetoothAdapter.RfcommListenerResult
    private int startRfcommListener(
            String name,
            ParcelUuid uuid,
            PendingIntent pendingIntent,
            AttributionSource attributionSource) {
        if (mBluetoothServerSockets.containsKey(uuid.getUuid())) {
            Log.d(TAG, String.format(
                        "Cannot start RFCOMM listener: UUID %s already in use.", uuid.getUuid()));
            return BluetoothStatusCodes.RFCOMM_LISTENER_START_FAILED_UUID_IN_USE;
        }

        try {
            startRfcommListenerInternal(name, uuid.getUuid(), pendingIntent, attributionSource);
        } catch (IOException e) {
            return BluetoothStatusCodes.RFCOMM_LISTENER_FAILED_TO_CREATE_SERVER_SOCKET;
        }

        return BluetoothStatusCodes.SUCCESS;
    }

    @BluetoothAdapter.RfcommListenerResult
    @VisibleForTesting
    int stopRfcommListener(ParcelUuid uuid, AttributionSource attributionSource) {
        RfcommListenerData listenerData = mBluetoothServerSockets.get(uuid.getUuid());

        if (listenerData == null) {
            Log.d(TAG, String.format(
                        "Cannot stop RFCOMM listener: UUID %s is not registered.", uuid.getUuid()));
            return BluetoothStatusCodes.RFCOMM_LISTENER_OPERATION_FAILED_NO_MATCHING_SERVICE_RECORD;
        }

        if (attributionSource.getUid() != listenerData.mAttributionSource.getUid()) {
            return BluetoothStatusCodes.RFCOMM_LISTENER_OPERATION_FAILED_DIFFERENT_APP;
        }

        // Remove the entry so that it does not try and restart the server socket.
        mBluetoothServerSockets.remove(uuid.getUuid());

        return listenerData.closeServerAndPendingSockets(mHandler);
    }

    @VisibleForTesting
    IncomingRfcommSocketInfo retrievePendingSocketForServiceRecord(
            ParcelUuid uuid, AttributionSource attributionSource) {
        IncomingRfcommSocketInfo socketInfo = new IncomingRfcommSocketInfo();

        RfcommListenerData listenerData = mBluetoothServerSockets.get(uuid.getUuid());

        if (listenerData == null) {
            socketInfo.status =
                    BluetoothStatusCodes
                            .RFCOMM_LISTENER_OPERATION_FAILED_NO_MATCHING_SERVICE_RECORD;
            return socketInfo;
        }

        if (attributionSource.getUid() != listenerData.mAttributionSource.getUid()) {
            socketInfo.status = BluetoothStatusCodes.RFCOMM_LISTENER_OPERATION_FAILED_DIFFERENT_APP;
            return socketInfo;
        }

        BluetoothSocket socket = listenerData.mPendingSockets.poll();

        if (socket == null) {
            socketInfo.status = BluetoothStatusCodes.RFCOMM_LISTENER_NO_SOCKET_AVAILABLE;
            return socketInfo;
        }

        mHandler.removeCallbacksAndMessages(socket);

        socketInfo.bluetoothDevice = socket.getRemoteDevice();
        socketInfo.pfd = socket.getParcelFileDescriptor();
        socketInfo.status = BluetoothStatusCodes.SUCCESS;

        return socketInfo;
    }

    private void handleIncomingRfcommConnections(UUID uuid) {
        RfcommListenerData listenerData = mBluetoothServerSockets.get(uuid);
        for (;;) {
            BluetoothSocket socket;
            try {
                socket = listenerData.mServerSocket.accept();
            } catch (IOException e) {
                if (mBluetoothServerSockets.containsKey(uuid)) {
                    // The uuid still being in the map indicates that the accept failure is
                    // unexpected. Try and restart the listener.
                    Log.e(TAG, "Failed to accept socket on " + listenerData.mServerSocket, e);
                    restartRfcommListener(listenerData, uuid);
                }
                return;
            }

            listenerData.mPendingSockets.add(socket);
            try {
                listenerData.mPendingIntent.send();
            } catch (PendingIntent.CanceledException e) {
                Log.e(TAG, "PendingIntent for RFCOMM socket notifications cancelled.", e);
                // The pending intent was cancelled, close the server as there is no longer any way
                // to notify the app that registered the listener.
                listenerData.closeServerAndPendingSockets(mHandler);
                mBluetoothServerSockets.remove(uuid);
                return;
            }
            mHandler.postDelayed(
                    () -> pendingSocketTimeoutRunnable(listenerData, socket),
                    socket,
                    PENDING_SOCKET_HANDOFF_TIMEOUT.toMillis());
        }
    }

    // Tries to restart the rfcomm listener for the given UUID
    private void restartRfcommListener(RfcommListenerData listenerData, UUID uuid) {
        listenerData.closeServerAndPendingSockets(mHandler);
        try {
            startRfcommListenerInternal(
                    listenerData.mName,
                    uuid,
                    listenerData.mPendingIntent,
                    listenerData.mAttributionSource);
        } catch (IOException e) {
            Log.e(TAG, "Failed to recreate rfcomm server socket", e);

            mBluetoothServerSockets.remove(uuid);
        }
    }

    private void pendingSocketTimeoutRunnable(
            RfcommListenerData listenerData, BluetoothSocket socket) {
        boolean socketFound = listenerData.mPendingSockets.remove(socket);
        if (socketFound) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close bt socket", e);
                // We don't care if closing the socket failed, just continue on.
            }
        }
    }

    private void startRfcommListenerInternal(
            String name, UUID uuid, PendingIntent intent, AttributionSource attributionSource)
            throws IOException {
        BluetoothServerSocket bluetoothServerSocket =
                mAdapter.listenUsingRfcommWithServiceRecord(name, uuid);

        RfcommListenerData listenerData =
                new RfcommListenerData(bluetoothServerSocket, name, intent, attributionSource);

        mBluetoothServerSockets.put(uuid, listenerData);

        mSocketServersExecutor.execute(() -> handleIncomingRfcommConnections(uuid));
    }

    private void stopRfcommServerSockets() {
        Iterator<Map.Entry<UUID, RfcommListenerData>> socketsIterator =
                mBluetoothServerSockets.entrySet().iterator();
        while (socketsIterator.hasNext()) {
            socketsIterator.next().getValue().closeServerAndPendingSockets(mHandler);
            socketsIterator.remove();
        }
    }

    private static class RfcommListenerData {
        final BluetoothServerSocket mServerSocket;
        // Service record name
        final String mName;
        // The Intent which contains the Service info to which the incoming socket connections are
        // handed off to.
        final PendingIntent mPendingIntent;
        // AttributionSource for the requester of the RFCOMM listener
        final AttributionSource mAttributionSource;
        // Contains the connected sockets which are pending transfer to the app which requested the
        // listener.
        final ConcurrentLinkedQueue<BluetoothSocket> mPendingSockets =
                new ConcurrentLinkedQueue<>();

        RfcommListenerData(
                BluetoothServerSocket serverSocket,
                String name,
                PendingIntent pendingIntent,
                AttributionSource attributionSource) {
            mServerSocket = serverSocket;
            mName = name;
            mPendingIntent = pendingIntent;
            mAttributionSource = attributionSource;
        }

        int closeServerAndPendingSockets(Handler handler) {
            int result = BluetoothStatusCodes.SUCCESS;
            try {
                mServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to call close on rfcomm server socket", e);
                result = BluetoothStatusCodes.RFCOMM_LISTENER_FAILED_TO_CLOSE_SERVER_SOCKET;
            }
            mPendingSockets.forEach(
                    pendingSocket -> {
                        handler.removeCallbacksAndMessages(pendingSocket);
                        try {
                            pendingSocket.close();
                        } catch (IOException e) {
                            Log.e(TAG, "Failed to close socket", e);
                        }
                    });
            mPendingSockets.clear();

            return result;
        }
    }

    @VisibleForTesting
    boolean isAvailable() {
        return !mCleaningUp;
    }

    /**
     *  Get an metadata of given device and key
     *
     *  @param device Bluetooth device
     *  @param key Metadata key
     *  @param value Metadata value
     *  @return if metadata is set successfully
     */
    public boolean setMetadata(BluetoothDevice device, int key, byte[] value) {
        if (value == null || value.length > BluetoothDevice.METADATA_MAX_LENGTH) {
            return false;
        }
        return mDatabaseManager.setCustomMeta(device, key, value);
    }

    /**
     *  Get an metadata of given device and key
     *
     *  @param device Bluetooth device
     *  @param key Metadata key
     *  @return value of given device and key combination
     */
    public byte[] getMetadata(BluetoothDevice device, int key) {
        return mDatabaseManager.getCustomMeta(device, key);
    }

    /**
     * Handlers for incoming service calls
     */
    private AdapterServiceBinder mBinder;

    /**
     * The Binder implementation must be declared to be a static class, with
     * the AdapterService instance passed in the constructor. Furthermore,
     * when the AdapterService shuts down, the reference to the AdapterService
     * must be explicitly removed.
     *
     * Otherwise, a memory leak can occur from repeated starting/stopping the
     * service...Please refer to android.os.Binder for further details on
     * why an inner instance class should be avoided.
     *
     */
    @VisibleForTesting
    public static class AdapterServiceBinder extends IBluetooth.Stub {
        private AdapterService mService;

        AdapterServiceBinder(AdapterService svc) {
            mService = svc;
            mService.invalidateBluetoothGetStateCache();
            BluetoothAdapter.getDefaultAdapter().disableBluetoothGetStateCache();
        }

        public void cleanup() {
            mService = null;
        }

        public AdapterService getService() {
            if (mService != null && mService.isAvailable()) {
                return mService;
            }
            return null;
        }

        @Override
        public void getState(SynchronousResultReceiver receiver) {
            try {
                receiver.send(getState());
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private int getState() {
            // don't check caller, may be called from system UI
            AdapterService service = getService();
            if (service == null) {
                return BluetoothAdapter.STATE_OFF;
            }

            return service.getState();
        }

        @Override
        public void enable(boolean quietMode, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(enable(quietMode, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean enable(boolean quietMode, AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null || !callerIsSystemOrActiveOrManagedUser(service, TAG, "enable")
                    || !Utils.checkConnectPermissionForDataDelivery(
                            service, attributionSource, "AdapterService enable")) {
                return false;
            }

            return service.enable(quietMode);
        }

        @Override
        public void disable(AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                receiver.send(disable(source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean disable(AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null || !callerIsSystemOrActiveOrManagedUser(service, TAG, "disable")
                    || !Utils.checkConnectPermissionForDataDelivery(
                            service, attributionSource, "AdapterService disable")) {
                return false;
            }

            return service.disable();
        }

        @Override
        public String getAddress() {
            if (mService == null) {
                return null;
            }
            return getAddressWithAttribution(Utils.getCallingAttributionSource(mService));
        }

        @Override
        public void getAddressWithAttribution(AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(getAddressWithAttribution(source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private String getAddressWithAttribution(AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null || !callerIsSystemOrActiveOrManagedUser(service, TAG, "getAddress")
                    || !Utils.checkConnectPermissionForDataDelivery(
                            service, attributionSource, "AdapterService getAddress")) {
                return null;
            }

            enforceLocalMacAddressPermission(service);

            return Utils.getAddressStringFromByte(service.mAdapterProperties.getAddress());
        }

        @Override
        public void getUuids(AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                receiver.send(getUuids(source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private List<ParcelUuid> getUuids(AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null || !callerIsSystemOrActiveOrManagedUser(service, TAG, "getUuids")
                    || !Utils.checkConnectPermissionForDataDelivery(
                            service, attributionSource, "AdapterService getUuids")) {
                return new ArrayList<>();
            }

            ParcelUuid[] parcels = service.mAdapterProperties.getUuids();
            if (parcels == null) {
                parcels = new ParcelUuid[0];
            }
            return Arrays.asList(parcels);
        }

        @Override
        public void getIdentityAddress(String address, SynchronousResultReceiver receiver) {
            try {
                receiver.send(getIdentityAddress(address));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        public String getIdentityAddress(String address) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "getIdentityAddress")
                    || !Utils.checkConnectPermissionForDataDelivery(
                            service, Utils.getCallingAttributionSource(mService),
                                "AdapterService getIdentityAddress")) {
                return null;
            }
            enforceBluetoothPrivilegedPermission(service);
            return service.getIdentityAddress(address);
        }

        @Override
        public void getName(AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                receiver.send(getName(source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private String getName(AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null || !callerIsSystemOrActiveOrManagedUser(service, TAG, "getName")
                    || !Utils.checkConnectPermissionForDataDelivery(
                            service, attributionSource, "AdapterService getName")) {
                return null;
            }

            return service.getName();
        }

        @Override
        public void getNameLengthForAdvertise(AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(getNameLengthForAdvertise(source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private int getNameLengthForAdvertise(AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service,
                            TAG, "getNameLengthForAdvertise")
                    || !Utils.checkAdvertisePermissionForDataDelivery(
                            service, attributionSource, TAG)) {
                return -1;
            }

            return service.getNameLengthForAdvertise();
        }

        @Override
        public void setName(String name, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(setName(name, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean setName(String name, AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null || !callerIsSystemOrActiveOrManagedUser(service, TAG, "setName")
                    || !Utils.checkConnectPermissionForDataDelivery(
                            service, attributionSource, "AdapterService setName")) {
                return false;
            }

            return service.mAdapterProperties.setName(name);
        }

        @Override
        public void getBluetoothClass(AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(getBluetoothClass(source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private BluetoothClass getBluetoothClass(AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "getBluetoothClass")
                    || !Utils.checkConnectPermissionForDataDelivery(
                            service, attributionSource, "AdapterSource getBluetoothClass")) {
                return null;
            }

            return service.mAdapterProperties.getBluetoothClass();
        }

        @Override
        public void setBluetoothClass(BluetoothClass bluetoothClass, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(setBluetoothClass(bluetoothClass, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean setBluetoothClass(BluetoothClass bluetoothClass, AttributionSource source) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "setBluetoothClass")
                    || !Utils.checkConnectPermissionForDataDelivery(service, source, TAG)) {
                return false;
            }

            enforceBluetoothPrivilegedPermission(service);

            if (!service.mAdapterProperties.setBluetoothClass(bluetoothClass)) {
              return false;
            }

            return Settings.Global.putInt(
                    service.getContentResolver(),
                    Settings.Global.BLUETOOTH_CLASS_OF_DEVICE,
                    bluetoothClass.getClassOfDevice());
        }

        @Override
        public void getIoCapability(AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                receiver.send(getIoCapability(source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private int getIoCapability(AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "getIoCapability")
                    || !Utils.checkConnectPermissionForDataDelivery(
                            service, attributionSource, "AdapterService getIoCapability")) {
                return BluetoothAdapter.IO_CAPABILITY_UNKNOWN;
            }

            return service.mAdapterProperties.getIoCapability();
        }

        @Override
        public void setIoCapability(int capability, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(setIoCapability(capability, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean setIoCapability(int capability, AttributionSource source) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "setIoCapability")
                    || !Utils.checkConnectPermissionForDataDelivery(service, source, TAG)) {
                return false;
            }

            enforceBluetoothPrivilegedPermission(service);

            if (!isValidIoCapability(capability)) {
              return false;
            }

            return service.mAdapterProperties.setIoCapability(capability);
        }

        @Override
        public void getLeIoCapability(AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(getLeIoCapability(source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private int getLeIoCapability(AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "getLeIoCapability")
                    || !Utils.checkConnectPermissionForDataDelivery(
                            service, attributionSource, "AdapterService getLeIoCapability")) {
                return BluetoothAdapter.IO_CAPABILITY_UNKNOWN;
            }

            return service.mAdapterProperties.getLeIoCapability();
        }

        @Override
        public void setLeIoCapability(int capability, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(setLeIoCapability(capability, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean setLeIoCapability(int capability, AttributionSource source) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "setLeIoCapability")
                    || !Utils.checkConnectPermissionForDataDelivery(service, source, TAG)) {
                return false;
            }

            enforceBluetoothPrivilegedPermission(service);

            if (!isValidIoCapability(capability)) {
              return false;
            }

            return service.mAdapterProperties.setLeIoCapability(capability);
        }

        @Override
        public void getScanMode(AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                receiver.send(getScanMode(source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        @VisibleForTesting
        int getScanMode(AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null || !callerIsSystemOrActiveOrManagedUser(service, TAG, "getScanMode")
                    || !Utils.checkScanPermissionForDataDelivery(
                            service, attributionSource, "AdapterService getScanMode")) {
                return BluetoothAdapter.SCAN_MODE_NONE;
            }

            return service.mAdapterProperties.getScanMode();
        }

        @Override
        public void setScanMode(int mode, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(setScanMode(mode, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private int setScanMode(int mode, AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "setScanMode")
                    || !Utils.checkScanPermissionForDataDelivery(
                            service, attributionSource, "AdapterService setScanMode")) {
                return BluetoothStatusCodes.ERROR_MISSING_BLUETOOTH_SCAN_PERMISSION;
            }
            enforceBluetoothPrivilegedPermission(service);

            return service.mAdapterProperties.setScanMode(mode)
                    ? BluetoothStatusCodes.SUCCESS : BluetoothStatusCodes.ERROR_UNKNOWN;
        }

        @Override
        public void getDiscoverableTimeout(AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(getDiscoverableTimeout(source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private long getDiscoverableTimeout(AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "getDiscoverableTimeout")
                    || !Utils.checkScanPermissionForDataDelivery(
                            service, attributionSource, "AdapterService getDiscoverableTimeout")) {
                return -1;
            }

            return service.mAdapterProperties.getDiscoverableTimeout();
        }

        @Override
        public void setDiscoverableTimeout(long timeout, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(setDiscoverableTimeout(timeout, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private int setDiscoverableTimeout(long timeout, AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "setDiscoverableTimeout")
                    || !Utils.checkScanPermissionForDataDelivery(
                            service, attributionSource, "AdapterService setDiscoverableTimeout")) {
                return BluetoothStatusCodes.ERROR_MISSING_BLUETOOTH_SCAN_PERMISSION;
            }
            enforceBluetoothPrivilegedPermission(service);

            return service.mAdapterProperties.setDiscoverableTimeout((int) timeout)
                    ? BluetoothStatusCodes.SUCCESS : BluetoothStatusCodes.ERROR_UNKNOWN;
        }

        @Override
        public void startDiscovery(AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                receiver.send(startDiscovery(source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean startDiscovery(AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "startDiscovery")) {
                return false;
            }

            if (!Utils.checkScanPermissionForDataDelivery(
                    service, attributionSource, "Starting discovery.")) {
                return false;
            }

            return service.startDiscovery(attributionSource);
        }

        @Override
        public void cancelDiscovery(AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                receiver.send(cancelDiscovery(source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean cancelDiscovery(AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "cancelDiscovery")
                    || !Utils.checkScanPermissionForDataDelivery(
                            service, attributionSource, "AdapterService cancelDiscovery")) {
                return false;
            }

            service.debugLog("cancelDiscovery");
            return service.cancelDiscoveryNative();
        }

        @Override
        public void isDiscovering(AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                receiver.send(isDiscovering(source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean isDiscovering(AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "isDiscovering")
                    || !Utils.checkScanPermissionForDataDelivery(
                            service, attributionSource, "AdapterService isDiscovering")) {
                return false;
            }

            return service.mAdapterProperties.isDiscovering();
        }

        @Override
        public void getDiscoveryEndMillis(AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(getDiscoveryEndMillis(source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private long getDiscoveryEndMillis(AttributionSource source) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "getDiscoveryEndMillis")
                    || !Utils.checkConnectPermissionForDataDelivery(service, source, TAG)) {
                return -1;
            }

            enforceBluetoothPrivilegedPermission(service);

            return service.mAdapterProperties.discoveryEndMillis();
        }

        @Override
        public void getMostRecentlyConnectedDevices(AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(getMostRecentlyConnectedDevices(source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private List<BluetoothDevice> getMostRecentlyConnectedDevices(
                AttributionSource attributionSource) {
            // don't check caller, may be called from system UI
            AdapterService service = getService();
            if (service == null || !Utils.checkConnectPermissionForDataDelivery(
                    service, attributionSource, "AdapterService getMostRecentlyConnectedDevices")) {
                return new ArrayList<>();
            }

            enforceBluetoothPrivilegedPermission(service);

            return service.mDatabaseManager.getMostRecentlyConnectedDevices();
        }

        @Override
        public void getBondedDevices(AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                receiver.send(getBondedDevices(source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private List<BluetoothDevice> getBondedDevices(AttributionSource attributionSource) {
            // don't check caller, may be called from system UI
            AdapterService service = getService();
            if (service == null || !Utils.checkConnectPermissionForDataDelivery(
                    service, attributionSource, "AdapterService getBondedDevices")) {
                return new ArrayList<>();
            }

            return Arrays.asList(service.getBondedDevices());
        }

        @Override
        public void getAdapterConnectionState(SynchronousResultReceiver receiver) {
            try {
                receiver.send(getAdapterConnectionState());
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private int getAdapterConnectionState() {
            // don't check caller, may be called from system UI
            AdapterService service = getService();
            if (service == null) {
                return BluetoothAdapter.STATE_DISCONNECTED;
            }

            return service.mAdapterProperties.getConnectionState();
        }

        /**
         * This method has an associated binder cache.  The invalidation
         * methods must be changed if the logic behind this method changes.
         */
        @Override
        public void getProfileConnectionState(int profile, SynchronousResultReceiver receiver) {
            try {
                receiver.send(getProfileConnectionState(profile));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private int getProfileConnectionState(int profile) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(
                            service, TAG, "getProfileConnectionState")) {
                return BluetoothProfile.STATE_DISCONNECTED;
            }

            return service.mAdapterProperties.getProfileConnectionState(profile);
        }

        @Override
        public void createBond(BluetoothDevice device, int transport, OobData remoteP192Data,
                OobData remoteP256Data, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(createBond(device, transport, remoteP192Data, remoteP256Data,
                            source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean createBond(BluetoothDevice device, int transport, OobData remoteP192Data,
                OobData remoteP256Data, AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null || !callerIsSystemOrActiveOrManagedUser(service, TAG, "createBond")
                    || !Utils.checkConnectPermissionForDataDelivery(
                            service, attributionSource, "AdapterService createBond")) {
                return false;
            }

            // This conditional is required to satisfy permission dependencies
            // since createBond calls createBondOutOfBand with null value passed as data.
            // BluetoothDevice#createBond requires BLUETOOTH_ADMIN only.
            service.enforceBluetoothPrivilegedPermissionIfNeeded(remoteP192Data, remoteP256Data);

            return service.createBond(device, transport, remoteP192Data, remoteP256Data,
                    attributionSource.getPackageName());
        }

        @Override
        public void cancelBondProcess(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(cancelBondProcess(device, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean cancelBondProcess(
                BluetoothDevice device, AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "cancelBondProcess")
                    || !Utils.checkConnectPermissionForDataDelivery(
                            service, attributionSource, "AdapterService cancelBondProcess")) {
                return false;
            }

            enforceBluetoothPrivilegedPermission(service);

            DeviceProperties deviceProp = service.mRemoteDevices.getDeviceProperties(device);
            if (deviceProp != null) {
                deviceProp.setBondingInitiatedLocally(false);
            }

            return service.cancelBondNative(getBytesFromAddress(device.getAddress()));
        }

        @Override
        public void removeBond(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(removeBond(device, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean removeBond(BluetoothDevice device, AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "removeBond")
                    || !Utils.checkConnectPermissionForDataDelivery(
                            service, attributionSource, "AdapterService removeBond")) {
                return false;
            }

            DeviceProperties deviceProp = service.mRemoteDevices.getDeviceProperties(device);
            if (deviceProp == null || deviceProp.getBondState() != BluetoothDevice.BOND_BONDED) {
                return false;
            }
            service.mBondAttemptCallerInfo.remove(device.getAddress());
            deviceProp.setBondingInitiatedLocally(false);

            Message msg = service.mBondStateMachine.obtainMessage(BondStateMachine.REMOVE_BOND);
            msg.obj = device;
            service.mBondStateMachine.sendMessage(msg);
            return true;
        }

        @Override
        public void getBondState(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(getBondState(device, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private int getBondState(BluetoothDevice device, AttributionSource attributionSource) {
            // don't check caller, may be called from system UI
            AdapterService service = getService();
            if (service == null || !Utils.checkConnectPermissionForDataDelivery(
                    service, attributionSource, "AdapterService getBondState")) {
                return BluetoothDevice.BOND_NONE;
            }

            return service.getBondState(device);
        }

        @Override
        public void isBondingInitiatedLocally(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(isBondingInitiatedLocally(device, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean isBondingInitiatedLocally(
                BluetoothDevice device, AttributionSource attributionSource) {
            // don't check caller, may be called from system UI
            AdapterService service = getService();
            if (service == null || !Utils.checkConnectPermissionForDataDelivery(
                    service, attributionSource, "AdapterService isBondingInitiatedLocally")) {
                return false;
            }

            DeviceProperties deviceProp = service.mRemoteDevices.getDeviceProperties(device);
            return deviceProp != null && deviceProp.isBondingInitiatedLocally();
        }

        @Override
        public void generateLocalOobData(int transport, IBluetoothOobDataCallback callback,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                generateLocalOobData(transport, callback, source);
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private void generateLocalOobData(int transport, IBluetoothOobDataCallback callback,
                AttributionSource source) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "generateLocalOobData")
                    || !Utils.checkConnectPermissionForDataDelivery(service, source, TAG)) {
                return;
            }
            enforceBluetoothPrivilegedPermission(service);
            service.generateLocalOobData(transport, callback);
        }

        @Override
        public void getSupportedProfiles(AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(getSupportedProfiles(source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private long getSupportedProfiles(AttributionSource source) {
            AdapterService service = getService();
            if (service == null
                    || !Utils.checkConnectPermissionForDataDelivery(service, source, TAG)) {
                return 0;
            }
            enforceBluetoothPrivilegedPermission(service);

            return Config.getSupportedProfilesBitMask();
        }

        @Override
        public int getConnectionState(BluetoothDevice device) {
            if (mService == null) {
                return BluetoothProfile.STATE_DISCONNECTED;
            }
            return getConnectionStateWithAttribution(device,
                        Utils.getCallingAttributionSource(mService));
        }

        @Override
        public void getConnectionStateWithAttribution(BluetoothDevice device,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                receiver.send(getConnectionStateWithAttribution(device, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        public int getConnectionStateWithAttribution(
                BluetoothDevice device, AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null || !Utils.checkConnectPermissionForDataDelivery(
                    service, attributionSource, "AdapterService getConnectionState")) {
                return BluetoothProfile.STATE_DISCONNECTED;
            }

            return service.getConnectionState(device);
        }

        @Override
        public void canBondWithoutDialog(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(canBondWithoutDialog(device, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean canBondWithoutDialog(BluetoothDevice device, AttributionSource source) {
            AdapterService service = getService();
            if (service == null
                    || !Utils.checkConnectPermissionForDataDelivery(service, source, TAG)) {
                return false;
            }

            enforceBluetoothPrivilegedPermission(service);

            return service.canBondWithoutDialog(device);
        }

        @Override
        public void removeActiveDevice(@ActiveDeviceUse int profiles,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                receiver.send(removeActiveDevice(profiles, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean removeActiveDevice(@ActiveDeviceUse int profiles,
                AttributionSource source) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "removeActiveDevice")
                    || !Utils.checkConnectPermissionForDataDelivery(service, source, TAG)) {
                return false;
            }
            return service.setActiveDevice(null, profiles);
        }

        @Override
        public void setActiveDevice(BluetoothDevice device, @ActiveDeviceUse int profiles,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                receiver.send(setActiveDevice(device, profiles, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean setActiveDevice(BluetoothDevice device, @ActiveDeviceUse int profiles,
                AttributionSource source) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "setActiveDevice")
                    || !Utils.checkConnectPermissionForDataDelivery(service, source, TAG)) {
                return false;
            }

            enforceBluetoothPrivilegedPermission(service);

            return service.setActiveDevice(device, profiles);
        }

        @Override
        public void getActiveDevices(@ActiveDeviceProfile int profile,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                receiver.send(getActiveDevices(profile, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private List<BluetoothDevice> getActiveDevices(@ActiveDeviceProfile int profile,
                AttributionSource source) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "getActiveDevices")
                    || !Utils.checkConnectPermissionForDataDelivery(service, source, TAG)) {
                return new ArrayList<>();
            }

            enforceBluetoothPrivilegedPermission(service);

            return service.getActiveDevices(profile);
        }

        @Override
        public void connectAllEnabledProfiles(BluetoothDevice device,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                receiver.send(connectAllEnabledProfiles(device, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private int connectAllEnabledProfiles(BluetoothDevice device,
                AttributionSource source) {
            AdapterService service = getService();
            if (service == null) {
                return BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED;
            }
            if (!callerIsSystemOrActiveOrManagedUser(service, TAG, "connectAllEnabledProfiles")) {
                return BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ALLOWED;
            }
            if (device == null) {
                throw new IllegalArgumentException("device cannot be null");
            }
            if (!BluetoothAdapter.checkBluetoothAddress(device.getAddress())) {
                throw new IllegalArgumentException("device cannot have an invalid address");
            }
            if (!Utils.checkConnectPermissionForDataDelivery(service, source, TAG)) {
                return BluetoothStatusCodes.ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION;
            }

            enforceBluetoothPrivilegedPermission(service);

            try {
                return service.connectAllEnabledProfiles(device);
            } catch (Exception e) {
                Log.v(TAG, "connectAllEnabledProfiles() failed", e);
                SneakyThrow.sneakyThrow(e);
                throw new RuntimeException(e);
            }
        }

        @Override
        public void disconnectAllEnabledProfiles(BluetoothDevice device,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                receiver.send(disconnectAllEnabledProfiles(device, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private int disconnectAllEnabledProfiles(BluetoothDevice device,
                AttributionSource source) {
            AdapterService service = getService();
            if (service == null) {
                return BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED;
            }
            if (!callerIsSystemOrActiveOrManagedUser(service,
                    TAG, "disconnectAllEnabledProfiles")) {
                return BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ALLOWED;
            }
            if (device == null) {
                throw new IllegalArgumentException("device cannot be null");
            }
            if (!BluetoothAdapter.checkBluetoothAddress(device.getAddress())) {
                throw new IllegalArgumentException("device cannot have an invalid address");
            }
            if (!Utils.checkConnectPermissionForDataDelivery(service, source, TAG)) {
                return BluetoothStatusCodes.ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION;
            }

            enforceBluetoothPrivilegedPermission(service);

            try {
                return service.disconnectAllEnabledProfiles(device);
            } catch (Exception e) {
                Log.v(TAG, "disconnectAllEnabledProfiles() failed", e);
                SneakyThrow.sneakyThrow(e);
                throw new RuntimeException(e);
            }
        }

        @Override
        public void getRemoteName(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(getRemoteName(device, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private String getRemoteName(BluetoothDevice device, AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "getRemoteName")
                    || !Utils.checkConnectPermissionForDataDelivery(
                            service, attributionSource, "AdapterService getRemoteName")) {
                return null;
            }

            return service.getRemoteName(device);
        }

        @Override
        public void getRemoteType(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(getRemoteType(device, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private int getRemoteType(BluetoothDevice device, AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "getRemoteType")
                    || !Utils.checkConnectPermissionForDataDelivery(
                            service, attributionSource, "AdapterService getRemoteType")) {
                return BluetoothDevice.DEVICE_TYPE_UNKNOWN;
            }

            DeviceProperties deviceProp = service.mRemoteDevices.getDeviceProperties(device);
            return deviceProp != null
                    ? deviceProp.getDeviceType() : BluetoothDevice.DEVICE_TYPE_UNKNOWN;
        }

        @Override
        public String getRemoteAlias(BluetoothDevice device) {
            if (mService == null) {
                return null;
            }
            return getRemoteAliasWithAttribution(device,
                    Utils.getCallingAttributionSource(mService));
        }

        @Override
        public void getRemoteAliasWithAttribution(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(getRemoteAliasWithAttribution(device, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private String getRemoteAliasWithAttribution(
                BluetoothDevice device, AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "getRemoteAlias")
                    || !Utils.checkConnectPermissionForDataDelivery(
                            service, attributionSource, "AdapterService getRemoteAlias")) {
                return null;
            }

            DeviceProperties deviceProp = service.mRemoteDevices.getDeviceProperties(device);
            return deviceProp != null ? deviceProp.getAlias() : null;
        }

        @Override
        public void setRemoteAlias(BluetoothDevice device, String name, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(setRemoteAlias(device, name, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private int setRemoteAlias(BluetoothDevice device, String name,
                AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null) {
                return BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED;
            }
            if (!callerIsSystemOrActiveOrManagedUser(service, TAG, "setRemoteAlias")) {
                return BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ALLOWED;
            }
            if (name != null && name.isEmpty()) {
                throw new IllegalArgumentException("alias cannot be the empty string");
            }

            if (!hasBluetoothPrivilegedPermission(service)) {
                if (!Utils.checkConnectPermissionForDataDelivery(
                        service, attributionSource, "AdapterService setRemoteAlias")) {
                    return BluetoothStatusCodes.ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION;
                }
                enforceCdmAssociation(service.mCompanionDeviceManager, service,
                        attributionSource.getPackageName(), Binder.getCallingUid(), device);
            }

            DeviceProperties deviceProp = service.mRemoteDevices.getDeviceProperties(device);
            if (deviceProp == null) {
                return BluetoothStatusCodes.ERROR_DEVICE_NOT_BONDED;
            }
            deviceProp.setAlias(device, name);
            return BluetoothStatusCodes.SUCCESS;
        }

        @Override
        public void getRemoteClass(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(getRemoteClass(device, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private int getRemoteClass(BluetoothDevice device, AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "getRemoteClass")
                    || !Utils.checkConnectPermissionForDataDelivery(
                            service, attributionSource, "AdapterService getRemoteClass")) {
                return 0;
            }

            DeviceProperties deviceProp = service.mRemoteDevices.getDeviceProperties(device);
            return deviceProp != null ? deviceProp.getBluetoothClass() : 0;
        }

        @Override
        public void getRemoteUuids(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(getRemoteUuids(device, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private List<ParcelUuid> getRemoteUuids(
                BluetoothDevice device, AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "getRemoteUuids")
                    || !Utils.checkConnectPermissionForDataDelivery(
                            service, attributionSource, "AdapterService getRemoteUuids")) {
                return new ArrayList<>();
            }

            ParcelUuid[] parcels = service.getRemoteUuids(device);
            if (parcels == null) {
                return null;
            }
            return Arrays.asList(parcels);
        }

        @Override
        public boolean fetchRemoteUuids(BluetoothDevice device) {
            if (mService == null) {
                return false;
            }
            return fetchRemoteUuidsWithAttribution(device, TRANSPORT_AUTO,
                    Utils.getCallingAttributionSource(mService));
        }

        @Override
        public void fetchRemoteUuidsWithAttribution(BluetoothDevice device, int transport,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                receiver.send(fetchRemoteUuidsWithAttribution(device, transport, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean fetchRemoteUuidsWithAttribution(
                BluetoothDevice device, int transport, AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "fetchRemoteUuids")
                    || !Utils.checkConnectPermissionForDataDelivery(
                            service, attributionSource, "AdapterService fetchRemoteUuids")) {
                return false;
            }
            if (transport != TRANSPORT_AUTO) {
                enforceBluetoothPrivilegedPermission(service);
            }

            service.mRemoteDevices.fetchUuids(device, transport);
            return true;
        }

        @Override
        public void setPin(BluetoothDevice device, boolean accept, int len, byte[] pinCode,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                receiver.send(setPin(device, accept, len, pinCode, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean setPin(BluetoothDevice device, boolean accept, int len, byte[] pinCode,
                AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "setPin")
                    || !Utils.checkConnectPermissionForDataDelivery(
                            service, attributionSource, "AdapterService setPin")) {
                return false;
            }

            DeviceProperties deviceProp = service.mRemoteDevices.getDeviceProperties(device);
            // Only allow setting a pin in bonding state, or bonded state in case of security
            // upgrade.
            if (deviceProp == null || !deviceProp.isBondingOrBonded()) {
                return false;
            }
            if (pinCode.length != len) {
                android.util.EventLog.writeEvent(0x534e4554, "139287605", -1,
                        "PIN code length mismatch");
                return false;
            }
            service.logUserBondResponse(device, accept,
                    BluetoothProtoEnums.BOND_SUB_STATE_LOCAL_PIN_REPLIED);
            return service.pinReplyNative(
                    getBytesFromAddress(device.getAddress()), accept, len, pinCode);
        }

        @Override
        public void setPasskey(BluetoothDevice device, boolean accept, int len, byte[] passkey,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                receiver.send(setPasskey(device, accept, len, passkey, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean setPasskey(BluetoothDevice device, boolean accept, int len, byte[] passkey,
                AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "setPasskey")
                    || !Utils.checkConnectPermissionForDataDelivery(
                            service, attributionSource, "AdapterService setPasskey")) {
                return false;
            }

            DeviceProperties deviceProp = service.mRemoteDevices.getDeviceProperties(device);
            if (deviceProp == null || !deviceProp.isBonding()) {
                return false;
            }
            if (passkey.length != len) {
                android.util.EventLog.writeEvent(0x534e4554, "139287605", -1,
                        "Passkey length mismatch");
                return false;
            }
            service.logUserBondResponse(device, accept, BluetoothProtoEnums.BOND_SUB_STATE_LOCAL_SSP_REPLIED);
            return service.sspReplyNative(
                    getBytesFromAddress(device.getAddress()),
                    AbstractionLayer.BT_SSP_VARIANT_PASSKEY_ENTRY,
                    accept,
                    Utils.byteArrayToInt(passkey));
        }

        @Override
        public void setPairingConfirmation(BluetoothDevice device, boolean accept,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                receiver.send(setPairingConfirmation(device, accept, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean setPairingConfirmation(BluetoothDevice device, boolean accept,
                AttributionSource source) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "setPairingConfirmation")
                    || !Utils.checkConnectPermissionForDataDelivery(service, source, TAG)) {
                return false;
            }

            enforceBluetoothPrivilegedPermission(service);

            DeviceProperties deviceProp = service.mRemoteDevices.getDeviceProperties(device);
            if (deviceProp == null || !deviceProp.isBonding()) {
                return false;
            }
            service.logUserBondResponse(device, accept, BluetoothProtoEnums.BOND_SUB_STATE_LOCAL_SSP_REPLIED);
            return service.sspReplyNative(
                    getBytesFromAddress(device.getAddress()),
                    AbstractionLayer.BT_SSP_VARIANT_PASSKEY_CONFIRMATION,
                    accept,
                    0);
        }

        @Override
        public void getSilenceMode(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(getSilenceMode(device, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean getSilenceMode(BluetoothDevice device, AttributionSource source) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "getSilenceMode")
                    || !Utils.checkConnectPermissionForDataDelivery(service, source, TAG)) {
                return false;
            }

            enforceBluetoothPrivilegedPermission(service);

            return service.mSilenceDeviceManager.getSilenceMode(device);
        }

        @Override
        public void setSilenceMode(BluetoothDevice device, boolean silence,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                receiver.send(setSilenceMode(device, silence, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean setSilenceMode(BluetoothDevice device, boolean silence,
                AttributionSource source) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "setSilenceMode")
                    || !Utils.checkConnectPermissionForDataDelivery(service, source, TAG)) {
                return false;
            }

            enforceBluetoothPrivilegedPermission(service);

            service.mSilenceDeviceManager.setSilenceMode(device, silence);
            return true;
        }

        @Override
        public void getPhonebookAccessPermission(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(getPhonebookAccessPermission(device, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private int getPhonebookAccessPermission(
                BluetoothDevice device, AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(
                            service, TAG, "getPhonebookAccessPermission")
                    || !Utils.checkConnectPermissionForDataDelivery(
                    service, attributionSource, "AdapterService getPhonebookAccessPermission")) {
                return BluetoothDevice.ACCESS_UNKNOWN;
            }

            return service.getDeviceAccessFromPrefs(device, PHONEBOOK_ACCESS_PERMISSION_PREFERENCE_FILE);
        }

        @Override
        public void setPhonebookAccessPermission(BluetoothDevice device, int value,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                receiver.send(setPhonebookAccessPermission(device, value, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean setPhonebookAccessPermission(BluetoothDevice device, int value,
                AttributionSource source) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service,
                            TAG, "setPhonebookAccessPermission")
                    || !Utils.checkConnectPermissionForDataDelivery(service, source, TAG)) {
                return false;
            }

            enforceBluetoothPrivilegedPermission(service);

            service.setPhonebookAccessPermission(device, value);
            return true;
        }

        @Override
        public void getMessageAccessPermission(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(getMessageAccessPermission(device, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private int getMessageAccessPermission(
                BluetoothDevice device, AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service,
                            TAG, "getMessageAccessPermission")
                    || !Utils.checkConnectPermissionForDataDelivery(
                    service, attributionSource, "AdapterService getMessageAccessPermission")) {
                return BluetoothDevice.ACCESS_UNKNOWN;
            }

            return service.getDeviceAccessFromPrefs(device, MESSAGE_ACCESS_PERMISSION_PREFERENCE_FILE);
        }

        @Override
        public void setMessageAccessPermission(BluetoothDevice device, int value,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                receiver.send(setMessageAccessPermission(device, value, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean setMessageAccessPermission(BluetoothDevice device, int value,
                AttributionSource source) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service,
                            TAG, "setMessageAccessPermission")
                    || !Utils.checkConnectPermissionForDataDelivery(service, source, TAG)) {
                return false;
            }

            enforceBluetoothPrivilegedPermission(service);

            service.setMessageAccessPermission(device, value);
            return true;
        }

        @Override
        public void getSimAccessPermission(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(getSimAccessPermission(device, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private int getSimAccessPermission(
                BluetoothDevice device, AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service,
                            TAG, "getSimAccessPermission")
                    || !Utils.checkConnectPermissionForDataDelivery(
                            service, attributionSource, "AdapterService getSimAccessPermission")) {
                return BluetoothDevice.ACCESS_UNKNOWN;
            }

            return service.getDeviceAccessFromPrefs(device, SIM_ACCESS_PERMISSION_PREFERENCE_FILE);
        }

        @Override
        public void setSimAccessPermission(BluetoothDevice device, int value,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                receiver.send(setSimAccessPermission(device, value, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean setSimAccessPermission(BluetoothDevice device, int value,
                AttributionSource source) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "setSimAccessPermission")
                    || !Utils.checkConnectPermissionForDataDelivery(service, source, TAG)) {
                return false;
            }

            enforceBluetoothPrivilegedPermission(service);

            service.setSimAccessPermission(device, value);
            return true;
        }

        @Override
        public IBluetoothSocketManager getSocketManager() {
            AdapterService service = getService();
            if (service == null) {
                return null;
            }

            return IBluetoothSocketManager.Stub.asInterface(service.mBluetoothSocketManagerBinder);
        }

        @Override
        public void sdpSearch(BluetoothDevice device, ParcelUuid uuid, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(sdpSearch(device, uuid, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean sdpSearch(
                BluetoothDevice device, ParcelUuid uuid, AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "sdpSearch")
                    || !Utils.checkConnectPermissionForDataDelivery(
                            service, attributionSource, "AdapterService sdpSearch")) {
                return false;
            }

            if (service.mSdpManager == null) {
                return false;
            }
            service.mSdpManager.sdpSearch(device, uuid);
            return true;
        }

        @Override
        public void getBatteryLevel(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(getBatteryLevel(device, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private int getBatteryLevel(BluetoothDevice device, AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "getBatteryLevel")
                    || !Utils.checkConnectPermissionForDataDelivery(
                            service, attributionSource, "AdapterService getBatteryLevel")) {
                return BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
            }

            DeviceProperties deviceProp = service.mRemoteDevices.getDeviceProperties(device);
            if (deviceProp == null) {
                return BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
            }
            return deviceProp.getBatteryLevel();
        }

        @Override
        public void getMaxConnectedAudioDevices(AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(getMaxConnectedAudioDevices(source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private int getMaxConnectedAudioDevices(AttributionSource attributionSource) {
            // don't check caller, may be called from system UI
            AdapterService service = getService();
            if (service == null || !Utils.checkConnectPermissionForDataDelivery(
                    service, attributionSource, "AdapterService getMaxConnectedAudioDevices")) {
                return -1;
            }

            return service.getMaxConnectedAudioDevices();
        }

        //@Override
        @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        public void isA2dpOffloadEnabled(AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(isA2dpOffloadEnabled(source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean isA2dpOffloadEnabled(AttributionSource attributionSource) {
            // don't check caller, may be called from system UI
            AdapterService service = getService();
            if (service == null || !Utils.checkConnectPermissionForDataDelivery(
                    service, attributionSource, "AdapterService isA2dpOffloadEnabled")) {
                return false;
            }

            return service.isA2dpOffloadEnabled();
        }

        @Override
        public void factoryReset(AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                receiver.send(factoryReset(source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        @VisibleForTesting
        boolean factoryReset(AttributionSource source) {
            AdapterService service = getService();
            if (service == null
                    || !Utils.checkConnectPermissionForDataDelivery(service, source, TAG)) {
                return false;
            }

            enforceBluetoothPrivilegedPermission(service);

            if (service.mDatabaseManager != null) {
                service.mDatabaseManager.factoryReset();
            }

            if (service.mBluetoothKeystoreService != null) {
                service.mBluetoothKeystoreService.factoryReset();
            }

            if (service.mBtCompanionManager != null) {
                service.mBtCompanionManager.factoryReset();
            }

            return service.factoryResetNative();
        }

        @Override
        public void registerBluetoothConnectionCallback(IBluetoothConnectionCallback callback,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                receiver.send(registerBluetoothConnectionCallback(callback, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean registerBluetoothConnectionCallback(IBluetoothConnectionCallback callback,
                AttributionSource source) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service,
                            TAG, "registerBluetoothConnectionCallback")
                    || !Utils.checkConnectPermissionForDataDelivery(service, source, TAG)) {
                return false;
            }
            enforceBluetoothPrivilegedPermission(service);
            service.mBluetoothConnectionCallbacks.add(callback);
            return true;
        }

        @Override
        public void unregisterBluetoothConnectionCallback(IBluetoothConnectionCallback callback,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                receiver.send(unregisterBluetoothConnectionCallback(callback, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean unregisterBluetoothConnectionCallback(
                IBluetoothConnectionCallback callback, AttributionSource source) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service,
                            TAG, "unregisterBluetoothConnectionCallback")
                    || !Utils.checkConnectPermissionForDataDelivery(service, source, TAG)) {
                return false;
            }
            enforceBluetoothPrivilegedPermission(service);
            return service.mBluetoothConnectionCallbacks.remove(callback);
        }

        @Override
        public void registerCallback(IBluetoothCallback callback, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                registerCallback(callback, source);
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        @VisibleForTesting
        void registerCallback(IBluetoothCallback callback, AttributionSource source) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "registerCallback")
                    || !Utils.checkConnectPermissionForDataDelivery(service, source, TAG)) {
                return;
            }

            enforceBluetoothPrivilegedPermission(service);

            service.mCallbacks.register(callback);
        }

        @Override
        public void unregisterCallback(IBluetoothCallback callback, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                unregisterCallback(callback, source);
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        @VisibleForTesting
        void unregisterCallback(IBluetoothCallback callback, AttributionSource source) {
            AdapterService service = getService();
            if (service == null || service.mCallbacks == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "unregisterCallback")
                    || !Utils.checkConnectPermissionForDataDelivery(service, source, TAG)) {
                return;
            }

            enforceBluetoothPrivilegedPermission(service);

            service.mCallbacks.unregister(callback);
        }

        @Override
        public void isMultiAdvertisementSupported(SynchronousResultReceiver receiver) {
            try {
                receiver.send(isMultiAdvertisementSupported());
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean isMultiAdvertisementSupported() {
            AdapterService service = getService();
            if (service == null) {
                return false;
            }

            int val = service.mAdapterProperties.getNumOfAdvertisementInstancesSupported();
            return val >= MIN_ADVT_INSTANCES_FOR_MA;
        }

        /**
         * This method has an associated binder cache.  The invalidation
         * methods must be changed if the logic behind this method changes.
         */
        @Override
        public void isOffloadedFilteringSupported(SynchronousResultReceiver receiver) {
            try {
                receiver.send(isOffloadedFilteringSupported());
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean isOffloadedFilteringSupported() {
            AdapterService service = getService();
            if (service == null) {
                return false;
            }

            int val = service.getNumOfOffloadedScanFilterSupported();
            return val >= MIN_OFFLOADED_FILTERS;
        }

        @Override
        public void isOffloadedScanBatchingSupported(SynchronousResultReceiver receiver) {
            try {
                receiver.send(isOffloadedScanBatchingSupported());
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean isOffloadedScanBatchingSupported() {
            AdapterService service = getService();
            if (service == null) {
                return false;
            }

            int val = service.getOffloadedScanResultStorage();
            return val >= MIN_OFFLOADED_SCAN_STORAGE_BYTES;
        }

        @Override
        public void isLe2MPhySupported(SynchronousResultReceiver receiver) {
            try {
                receiver.send(isLe2MPhySupported());
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean isLe2MPhySupported() {
            AdapterService service = getService();
            if (service == null) {
                return false;
            }

            return service.isLe2MPhySupported();
        }

        @Override
        public void isLeCodedPhySupported(SynchronousResultReceiver receiver) {
            try {
                receiver.send(isLeCodedPhySupported());
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean isLeCodedPhySupported() {
            AdapterService service = getService();
            if (service == null) {
                return false;
            }

            return service.isLeCodedPhySupported();
        }

        @Override
        public void isLeExtendedAdvertisingSupported(SynchronousResultReceiver receiver) {
            try {
                receiver.send(isLeExtendedAdvertisingSupported());
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean isLeExtendedAdvertisingSupported() {
            AdapterService service = getService();
            if (service == null) {
                return false;
            }

            return service.isLeExtendedAdvertisingSupported();
        }

        @Override
        public void isLePeriodicAdvertisingSupported(SynchronousResultReceiver receiver) {
            try {
                receiver.send(isLePeriodicAdvertisingSupported());
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean isLePeriodicAdvertisingSupported() {
            AdapterService service = getService();
            if (service == null) {
                return false;
            }

            return service.isLePeriodicAdvertisingSupported();
        }

        @Override
        public void isLeAudioSupported(SynchronousResultReceiver receiver) {
            try {
                receiver.send(isLeAudioSupported());
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private int isLeAudioSupported() {
            AdapterService service = getService();
            if (service == null) {
                return BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED;
            }

            HashSet<Class> supportedProfileServices =
                    new HashSet<Class>(Arrays.asList(Config.getSupportedProfiles()));
            HashSet<Class> leAudioUnicastProfiles = Config.geLeAudioUnicastProfiles();

            if (supportedProfileServices.containsAll(leAudioUnicastProfiles)) {
                return BluetoothStatusCodes.FEATURE_SUPPORTED;
            }

            return BluetoothStatusCodes.FEATURE_NOT_SUPPORTED;
        }

        @Override
        public void isLeAudioBroadcastSourceSupported(SynchronousResultReceiver receiver) {
            try {
                receiver.send(isLeAudioBroadcastSourceSupported());
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private int isLeAudioBroadcastSourceSupported() {
            AdapterService service = getService();
            if (service == null) {
                return BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED;
            }

            long supportBitMask = Config.getSupportedProfilesBitMask();
            if ((supportBitMask & (1 << BluetoothProfile.LE_AUDIO_BROADCAST)) != 0) {
                return BluetoothStatusCodes.FEATURE_SUPPORTED;
            }

            return BluetoothStatusCodes.FEATURE_NOT_SUPPORTED;
        }

        @Override
        public void isLeAudioBroadcastAssistantSupported(SynchronousResultReceiver receiver) {
            try {
                receiver.send(isLeAudioBroadcastAssistantSupported());
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        public int isLeAudioBroadcastAssistantSupported() {
            AdapterService service = getService();
            if (service == null) {
                return BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED;
            }

            HashSet<Class> supportedProfileServices =
                    new HashSet<Class>(Arrays.asList(Config.getSupportedProfiles()));

            if (supportedProfileServices.contains(BassClientService.class)) {
                return BluetoothStatusCodes.FEATURE_SUPPORTED;
            }

            return BluetoothStatusCodes.FEATURE_NOT_SUPPORTED;
        }

        @Override
        public void getLeMaximumAdvertisingDataLength(SynchronousResultReceiver receiver) {
            try {
                receiver.send(getLeMaximumAdvertisingDataLength());
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private int getLeMaximumAdvertisingDataLength() {
            AdapterService service = getService();
            if (service == null) {
                return 0;
            }

            return service.getLeMaximumAdvertisingDataLength();
        }

        @Override
        public void isActivityAndEnergyReportingSupported(SynchronousResultReceiver receiver) {
            try {
                receiver.send(isActivityAndEnergyReportingSupported());
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean isActivityAndEnergyReportingSupported() {
            AdapterService service = getService();
            if (service == null) {
                return false;
            }

            return service.mAdapterProperties.isActivityAndEnergyReportingSupported();
        }

        @Override
        public void reportActivityInfo(AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(reportActivityInfo(source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private BluetoothActivityEnergyInfo reportActivityInfo(AttributionSource source) {
            AdapterService service = getService();
            if (service == null
                    || !Utils.checkConnectPermissionForDataDelivery(service, source, TAG)) {
                return null;
            }

            enforceBluetoothPrivilegedPermission(service);

            return service.reportActivityInfo();
        }

        @Override
        public void registerMetadataListener(IBluetoothMetadataListener listener,
                BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(registerMetadataListener(listener, device, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean registerMetadataListener(IBluetoothMetadataListener listener,
                BluetoothDevice device, AttributionSource source) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service,
                            TAG, "registerMetadataListener")
                    || !Utils.checkConnectPermissionForDataDelivery(service, source, TAG)) {
                return false;
            }

            enforceBluetoothPrivilegedPermission(service);

            if (service.mMetadataListeners == null) {
                return false;
            }
            ArrayList<IBluetoothMetadataListener> list = service.mMetadataListeners.get(device);
            if (list == null) {
                list = new ArrayList<>();
            } else if (list.contains(listener)) {
                // The device is already registered with this listener
                return true;
            }
            list.add(listener);
            service.mMetadataListeners.put(device, list);
            return true;
        }

        @Override
        public void unregisterMetadataListener(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(unregisterMetadataListener(device, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean unregisterMetadataListener(BluetoothDevice device,
                AttributionSource source) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service,
                            TAG, "unregisterMetadataListener")
                    || !Utils.checkConnectPermissionForDataDelivery(service, source, TAG)) {
                return false;
            }

            enforceBluetoothPrivilegedPermission(service);

            if (service.mMetadataListeners == null) {
                return false;
            }
            if (service.mMetadataListeners.containsKey(device)) {
                service.mMetadataListeners.remove(device);
            }
            return true;
        }

        @Override
        public void setMetadata(BluetoothDevice device, int key, byte[] value,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                receiver.send(setMetadata(device, key, value, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private boolean setMetadata(BluetoothDevice device, int key, byte[] value,
                AttributionSource source) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "setMetadata")
                    || !Utils.checkConnectPermissionForDataDelivery(service, source, TAG)) {
                return false;
            }

            enforceBluetoothPrivilegedPermission(service);

            if (value.length > BluetoothDevice.METADATA_MAX_LENGTH) {
                return false;
            }
            return service.mDatabaseManager.setCustomMeta(device, key, value);
        }

        @Override
        public void getMetadata(BluetoothDevice device, int key, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(getMetadata(device, key, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private byte[] getMetadata(BluetoothDevice device, int key,
                AttributionSource source) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "getMetadata")
                    || !Utils.checkConnectPermissionForDataDelivery(service, source, TAG)) {
                return null;
            }

            enforceBluetoothPrivilegedPermission(service);

            return service.mDatabaseManager.getCustomMeta(device, key);
        }

        @Override
        public void isRequestAudioPolicyAsSinkSupported(BluetoothDevice device,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                receiver.send(isRequestAudioPolicyAsSinkSupported(device, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private int isRequestAudioPolicyAsSinkSupported(BluetoothDevice device,
                AttributionSource source) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG,
                        "isRequestAudioPolicyAsSinkSupported")
                    || !Utils.checkConnectPermissionForDataDelivery(service, source, TAG)) {
                return BluetoothStatusCodes.FEATURE_NOT_CONFIGURED;
            }
            enforceBluetoothPrivilegedPermission(service);
            return service.isRequestAudioPolicyAsSinkSupported(device);
        }

        @Override
        public void requestAudioPolicyAsSink(BluetoothDevice device,
                BluetoothSinkAudioPolicy policies, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(requestAudioPolicyAsSink(device, policies, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private int requestAudioPolicyAsSink(BluetoothDevice device,
                BluetoothSinkAudioPolicy policies, AttributionSource source) {
            AdapterService service = getService();
            if (service == null) {
                return BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED;
            } else if (!callerIsSystemOrActiveOrManagedUser(service,
                    TAG, "requestAudioPolicyAsSink")) {
                return BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ALLOWED;
            } else if (!Utils.checkConnectPermissionForDataDelivery(
                    service, source, TAG)) {
                return BluetoothStatusCodes.ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION;
            }
            enforceBluetoothPrivilegedPermission(service);
            return service.requestAudioPolicyAsSink(device, policies);
        }

        @Override
        public void getRequestedAudioPolicyAsSink(BluetoothDevice device,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                receiver.send(getRequestedAudioPolicyAsSink(device, source));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        private BluetoothSinkAudioPolicy getRequestedAudioPolicyAsSink(BluetoothDevice device,
                AttributionSource source) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service,
                            TAG, "getRequestedAudioPolicyAsSink")
                    || !Utils.checkConnectPermissionForDataDelivery(service, source, TAG)) {
                return null;
            }
            enforceBluetoothPrivilegedPermission(service);
            return service.getRequestedAudioPolicyAsSink(device);
        }

        @Override
        public void requestActivityInfo(IBluetoothActivityEnergyInfoListener listener,
                    AttributionSource source) {
            BluetoothActivityEnergyInfo info = reportActivityInfo(source);
            try {
                listener.onBluetoothActivityEnergyInfoAvailable(info);
            } catch (RemoteException e) {
                Log.e(TAG, "onBluetoothActivityEnergyInfo: RemoteException", e);
            }
        }

        @Override
        public void onLeServiceUp(AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                onLeServiceUp(source);
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        @VisibleForTesting
        void onLeServiceUp(AttributionSource source) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "onLeServiceUp")
                    || !Utils.checkConnectPermissionForDataDelivery(service, source, TAG)) {
                return;
            }

            enforceBluetoothPrivilegedPermission(service);

            service.mAdapterStateMachine.sendMessage(AdapterState.USER_TURN_ON);
        }

        @Override
        public void onBrEdrDown(AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                onBrEdrDown(source);
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        @VisibleForTesting
        void onBrEdrDown(AttributionSource source) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "onBrEdrDown")
                    || !Utils.checkConnectPermissionForDataDelivery(service, source, TAG)) {
                return;
            }

            enforceBluetoothPrivilegedPermission(service);

            service.mAdapterStateMachine.sendMessage(AdapterState.BLE_TURN_OFF);
        }

        @Override
        public void dump(FileDescriptor fd, String[] args) {
            PrintWriter writer = new PrintWriter(new FileOutputStream(fd));
            AdapterService service = getService();
            if (service == null) {
                return;
            }

            enforceDumpPermission(service);

            service.dump(fd, writer, args);
            writer.close();
        }

        @Override
        public void allowLowLatencyAudio(boolean allowed, BluetoothDevice device,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(allowLowLatencyAudio(allowed, device));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        @RequiresPermission(allOf = {
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_PRIVILEGED,
        })
        private boolean allowLowLatencyAudio(boolean allowed, BluetoothDevice device) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "allowLowLatencyAudio")
                    || !Utils.checkConnectPermissionForDataDelivery(
                            service, Utils.getCallingAttributionSource(service),
                                "AdapterService allowLowLatencyAudio")) {
                return false;
            }
            enforceBluetoothPrivilegedPermission(service);
            return service.allowLowLatencyAudio(allowed, device);
        }

        @Override
        public void startRfcommListener(String name, ParcelUuid uuid, PendingIntent pendingIntent,
                AttributionSource attributionSource, SynchronousResultReceiver receiver) {
            try {
                receiver.send(startRfcommListener(name, uuid, pendingIntent, attributionSource));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        @RequiresPermission(allOf = {
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_PRIVILEGED,
        })
        private int startRfcommListener(
                String name,
                ParcelUuid uuid,
                PendingIntent pendingIntent,
                AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "startRfcommListener")
                    || !Utils.checkConnectPermissionForDataDelivery(
                            service, attributionSource, "AdapterService startRfcommListener")) {
                return BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ALLOWED;
            }
            enforceBluetoothPrivilegedPermission(service);
            return service.startRfcommListener(name, uuid, pendingIntent, attributionSource);
        }

        @Override
        public void stopRfcommListener(ParcelUuid uuid, AttributionSource attributionSource,
                SynchronousResultReceiver receiver) {
            try {
                receiver.send(stopRfcommListener(uuid, attributionSource));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        @RequiresPermission(allOf = {
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_PRIVILEGED,
        })
        private int stopRfcommListener(ParcelUuid uuid, AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service, TAG, "stopRfcommListener")
                    || !Utils.checkConnectPermissionForDataDelivery(
                            service, attributionSource, "AdapterService stopRfcommListener")) {
                return BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ALLOWED;
            }
            enforceBluetoothPrivilegedPermission(service);
            return service.stopRfcommListener(uuid, attributionSource);
        }

        @Override
        public void retrievePendingSocketForServiceRecord(ParcelUuid uuid,
                AttributionSource attributionSource, SynchronousResultReceiver receiver) {
            try {
                receiver.send(retrievePendingSocketForServiceRecord(uuid, attributionSource));
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
        @RequiresPermission(allOf = {
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_PRIVILEGED,
        })
        private IncomingRfcommSocketInfo retrievePendingSocketForServiceRecord(
                ParcelUuid uuid, AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null
                    || !callerIsSystemOrActiveOrManagedUser(service,
                            TAG, "retrievePendingSocketForServiceRecord")
                    || !Utils.checkConnectPermissionForDataDelivery(
                            service, attributionSource,
                            "AdapterService retrievePendingSocketForServiceRecord")) {
                return null;
            }
            enforceBluetoothPrivilegedPermission(service);
            return service.retrievePendingSocketForServiceRecord(uuid, attributionSource);
        }

        @Override
        public void setForegroundUserId(int userId, AttributionSource attributionSource) {
            AdapterService service = getService();
            if (service == null || !Utils.checkConnectPermissionForDataDelivery(
                    service, Utils.getCallingAttributionSource(mService),
                    "AdapterService setForegroundUserId")) {
                return;
            }
            enforceBluetoothPrivilegedPermission(service);
            Utils.setForegroundUserId(userId);
        }
    }

    // ----API Methods--------

    public boolean isEnabled() {
        return getState() == BluetoothAdapter.STATE_ON;
    }

    public int getState() {
        if (mAdapterProperties != null) {
            return mAdapterProperties.getState();
        }
        return BluetoothAdapter.STATE_OFF;
    }

    public synchronized boolean enable(boolean quietMode) {
        // Enforce the user restriction for disallowing Bluetooth if it was set.
        if (mUserManager.hasUserRestrictionForUser(UserManager.DISALLOW_BLUETOOTH,
                    UserHandle.SYSTEM)) {
            debugLog("enable() called when Bluetooth was disallowed");
            return false;
        }

        debugLog("enable() - Enable called with quiet mode status =  " + quietMode);
        mQuietmode = quietMode;
        mAdapterStateMachine.sendMessage(AdapterState.BLE_TURN_ON);
        return true;
    }

    boolean disable() {
        debugLog("disable() called with mRunningProfiles.size() = " + mRunningProfiles.size());
        mAdapterStateMachine.sendMessage(AdapterState.USER_TURN_OFF);
        return true;
    }

    public String getName() {
        return mAdapterProperties.getName();
    }

    public int getNameLengthForAdvertise() {
        return mAdapterProperties.getName().length();
    }

    @VisibleForTesting
    static boolean isValidIoCapability(int capability) {
        if (capability < 0 || capability >= BluetoothAdapter.IO_CAPABILITY_MAX) {
            Log.e(TAG, "Invalid IO capability value - " + capability);
            return false;
        }

        return true;
    }

    ArrayList<DiscoveringPackage> getDiscoveringPackages() {
        return mDiscoveringPackages;
    }

    void clearDiscoveringPackages() {
        synchronized (mDiscoveringPackages) {
            mDiscoveringPackages.clear();
        }
    }

    boolean startDiscovery(AttributionSource attributionSource) {
        UserHandle callingUser = Binder.getCallingUserHandle();
        debugLog("startDiscovery");
        String callingPackage = attributionSource.getPackageName();
        mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);
        boolean isQApp = Utils.checkCallerTargetSdk(this, callingPackage, Build.VERSION_CODES.Q);
        boolean hasDisavowedLocation =
                Utils.hasDisavowedLocationForScan(this, attributionSource, mTestModeEnabled);
        String permission = null;
        if (Utils.checkCallerHasNetworkSettingsPermission(this)) {
            permission = android.Manifest.permission.NETWORK_SETTINGS;
        } else if (Utils.checkCallerHasNetworkSetupWizardPermission(this)) {
            permission = android.Manifest.permission.NETWORK_SETUP_WIZARD;
        } else if (!hasDisavowedLocation) {
            if (isQApp) {
                if (!Utils.checkCallerHasFineLocation(this, attributionSource, callingUser)) {
                    return false;
                }
                permission = android.Manifest.permission.ACCESS_FINE_LOCATION;
            } else {
                if (!Utils.checkCallerHasCoarseLocation(this, attributionSource, callingUser)) {
                    return false;
                }
                permission = android.Manifest.permission.ACCESS_COARSE_LOCATION;
            }
        }

        synchronized (mDiscoveringPackages) {
            mDiscoveringPackages.add(
                    new DiscoveringPackage(callingPackage, permission, hasDisavowedLocation));
        }
        return startDiscoveryNative();
    }

    /**
     * Same as API method {@link BluetoothAdapter#getBondedDevices()}
     *
     * @return array of bonded {@link BluetoothDevice} or null on error
     */
    public BluetoothDevice[] getBondedDevices() {
        return mAdapterProperties.getBondedDevices();
    }

    /**
     * Get the database manager to access Bluetooth storage
     *
     * @return {@link DatabaseManager} or null on error
     */
    @VisibleForTesting
    public DatabaseManager getDatabase() {
        return mDatabaseManager;
    }

    public byte[] getByteIdentityAddress(BluetoothDevice device) {
        DeviceProperties deviceProp = mRemoteDevices.getDeviceProperties(device);
        if (deviceProp != null && deviceProp.getIdentityAddress() != null) {
            return Utils.getBytesFromAddress(deviceProp.getIdentityAddress());
        } else {
            return Utils.getByteAddress(device);
        }
    }

    public BluetoothDevice getDeviceFromByte(byte[] address) {
        BluetoothDevice device = mRemoteDevices.getDevice(address);
        if (device == null) {
            device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        }
        return device;
    }

    public String getIdentityAddress(String address) {
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address.toUpperCase());
        DeviceProperties deviceProp = mRemoteDevices.getDeviceProperties(device);
        if (deviceProp != null && deviceProp.getIdentityAddress() != null) {
            return deviceProp.getIdentityAddress();
        } else {
            return address;
        }
    }

    private class CallerInfo {
        public String callerPackageName;
        public UserHandle user;
    }

    boolean createBond(BluetoothDevice device, int transport, OobData remoteP192Data,
            OobData remoteP256Data, String callingPackage) {
        DeviceProperties deviceProp = mRemoteDevices.getDeviceProperties(device);
        if (deviceProp != null && deviceProp.getBondState() != BluetoothDevice.BOND_NONE) {
            return false;
        }

        if (!isPackageNameAccurate(this, callingPackage, Binder.getCallingUid())) {
            return false;
        }

        CallerInfo createBondCaller = new CallerInfo();
        createBondCaller.callerPackageName = callingPackage;
        createBondCaller.user = Binder.getCallingUserHandle();
        mBondAttemptCallerInfo.put(device.getAddress(), createBondCaller);

        mRemoteDevices.setBondingInitiatedLocally(Utils.getByteAddress(device));

        // Pairing is unreliable while scanning, so cancel discovery
        // Note, remove this when native stack improves
        cancelDiscoveryNative();

        Message msg = mBondStateMachine.obtainMessage(BondStateMachine.CREATE_BOND);
        msg.obj = device;
        msg.arg1 = transport;

        Bundle remoteOobDatasBundle = new Bundle();
        boolean setData = false;
        if (remoteP192Data != null) {
            remoteOobDatasBundle.putParcelable(BondStateMachine.OOBDATAP192, remoteP192Data);
            setData = true;
        }
        if (remoteP256Data != null) {
            remoteOobDatasBundle.putParcelable(BondStateMachine.OOBDATAP256, remoteP256Data);
            setData = true;
        }
        if (setData) {
            msg.setData(remoteOobDatasBundle);
        }
        mBondStateMachine.sendMessage(msg);
        return true;
    }

    private final ArrayDeque<IBluetoothOobDataCallback> mOobDataCallbackQueue =
            new ArrayDeque<>();

    /**
     * Fetches the local OOB data to give out to remote.
     *
     * @param transport - specify data transport.
     * @param callback - callback used to receive the requested {@link OobData}; null will be
     * ignored silently.
     *
     * @hide
     */
    public synchronized void generateLocalOobData(int transport,
            IBluetoothOobDataCallback callback) {
        if (callback == null) {
            Log.e(TAG, "'callback' argument must not be null!");
            return;
        }
        if (mOobDataCallbackQueue.peek() != null) {
            try {
                callback.onError(BluetoothStatusCodes.ERROR_ANOTHER_ACTIVE_OOB_REQUEST);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to make callback", e);
            }
            return;
        }
        mOobDataCallbackQueue.offer(callback);
        mHandler.postDelayed(() -> removeFromOobDataCallbackQueue(callback),
                GENERATE_LOCAL_OOB_DATA_TIMEOUT.toMillis());
        generateLocalOobDataNative(transport);
    }

    private synchronized void removeFromOobDataCallbackQueue(IBluetoothOobDataCallback callback) {
        if (callback == null) {
            return;
        }

        if (mOobDataCallbackQueue.peek() == callback) {
            try {
                mOobDataCallbackQueue.poll().onError(BluetoothStatusCodes.ERROR_UNKNOWN);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to make OobDataCallback to remove callback from queue", e);
            }
        }
    }

    /* package */ synchronized void notifyOobDataCallback(int transport, OobData oobData) {
        if (mOobDataCallbackQueue.peek() == null) {
            Log.e(TAG, "Failed to make callback, no callback exists");
            return;
        }
        if (oobData == null) {
            try {
                mOobDataCallbackQueue.poll().onError(BluetoothStatusCodes.ERROR_UNKNOWN);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to make callback", e);
            }
        } else {
            try {
                mOobDataCallbackQueue.poll().onOobData(transport, oobData);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to make callback", e);
            }
        }
    }

    public boolean isQuietModeEnabled() {
        debugLog("isQuietModeEnabled() - Enabled = " + mQuietmode);
        return mQuietmode;
    }

    public void updateUuids() {
        debugLog("updateUuids() - Updating UUIDs for bonded devices");
        BluetoothDevice[] bondedDevices = getBondedDevices();
        if (bondedDevices == null) {
            return;
        }

        for (BluetoothDevice device : bondedDevices) {
            mRemoteDevices.updateUuids(device);
        }
    }

    /**
     * Update device UUID changed to {@link BondStateMachine}
     *
     * @param device remote device of interest
     */
    public void deviceUuidUpdated(BluetoothDevice device) {
        // Notify BondStateMachine for SDP complete / UUID changed.
        Message msg = mBondStateMachine.obtainMessage(BondStateMachine.UUID_UPDATE);
        msg.obj = device;
        mBondStateMachine.sendMessage(msg);
    }

    /**
     * Get the bond state of a particular {@link BluetoothDevice}
     *
     * @param device remote device of interest
     * @return bond state <p>Possible values are
     * {@link BluetoothDevice#BOND_NONE},
     * {@link BluetoothDevice#BOND_BONDING},
     * {@link BluetoothDevice#BOND_BONDED}.
     */
    @VisibleForTesting
    public int getBondState(BluetoothDevice device) {
        DeviceProperties deviceProp = mRemoteDevices.getDeviceProperties(device);
        if (deviceProp == null) {
            return BluetoothDevice.BOND_NONE;
        }
        return deviceProp.getBondState();
    }

    int getConnectionState(BluetoothDevice device) {
        return getConnectionStateNative(getBytesFromAddress(device.getAddress()));
    }

    /**
     * Checks whether the device was recently associated with the comapnion app that called
     * {@link BluetoothDevice#createBond}. This allows these devices to skip the pairing dialog if
     * their pairing variant is {@link BluetoothDevice#PAIRING_VARIANT_CONSENT}.
     *
     * @param device the bluetooth device that is being bonded
     * @return true if it was recently associated and we can bypass the dialog, false otherwise
     */
    public boolean canBondWithoutDialog(BluetoothDevice device) {
        if (mBondAttemptCallerInfo.containsKey(device.getAddress())) {
            CallerInfo bondCallerInfo = mBondAttemptCallerInfo.get(device.getAddress());

            return mCompanionDeviceManager.canPairWithoutPrompt(bondCallerInfo.callerPackageName,
                    device.getAddress(), bondCallerInfo.user);
        }
        return false;
    }

    /**
     * Sets device as the active devices for the profiles passed into the function
     *
     * @param device is the remote bluetooth device
     * @param profiles is a constant that references for which profiles we'll be setting the remote
     *                 device as our active device. One of the following:
     *                 {@link BluetoothAdapter#ACTIVE_DEVICE_AUDIO},
     *                 {@link BluetoothAdapter#ACTIVE_DEVICE_PHONE_CALL}
     *                 {@link BluetoothAdapter#ACTIVE_DEVICE_ALL}
     * @return false if profiles value is not one of the constants we accept, true otherwise
     */
    @RequiresPermission(allOf = {
            android.Manifest.permission.BLUETOOTH_PRIVILEGED,
            android.Manifest.permission.MODIFY_PHONE_STATE,
    })
    public boolean setActiveDevice(BluetoothDevice device, @ActiveDeviceUse int profiles) {
        boolean setA2dp = false;
        boolean setHeadset = false;

        // Determine for which profiles we want to set device as our active device
        switch(profiles) {
            case BluetoothAdapter.ACTIVE_DEVICE_AUDIO:
                setA2dp = true;
                break;
            case BluetoothAdapter.ACTIVE_DEVICE_PHONE_CALL:
                setHeadset = true;
                break;
            case BluetoothAdapter.ACTIVE_DEVICE_ALL:
                setA2dp = true;
                setHeadset = true;
                break;
            default:
                return false;
        }

        if (mLeAudioService != null && (device == null
                || mLeAudioService.getConnectionPolicy(device)
                == BluetoothProfile.CONNECTION_POLICY_ALLOWED)) {
            Log.i(TAG, "setActiveDevice: Setting active Le Audio device " + device);
            mLeAudioService.setActiveDevice(device);
        }

        if (setA2dp && mA2dpService != null && (device == null
                || mA2dpService.getConnectionPolicy(device)
                == BluetoothProfile.CONNECTION_POLICY_ALLOWED)) {
            Log.i(TAG, "setActiveDevice: Setting active A2dp device " + device);
            mA2dpService.setActiveDevice(device);
        }

        if (mHearingAidService != null && (device == null
                || mHearingAidService.getConnectionPolicy(device)
                == BluetoothProfile.CONNECTION_POLICY_ALLOWED)) {
            Log.i(TAG, "setActiveDevice: Setting active Hearing Aid " + device);
            mHearingAidService.setActiveDevice(device);
        }

        if (setHeadset && mHeadsetService != null && (device == null
                || mHeadsetService.getConnectionPolicy(device)
                == BluetoothProfile.CONNECTION_POLICY_ALLOWED)) {
            Log.i(TAG, "setActiveDevice: Setting active Headset " + device);
            mHeadsetService.setActiveDevice(device);
        }

        return true;
    }

    /**
     * Get the active devices for the BluetoothProfile specified
     *
     * @param profile is the profile from which we want the active devices.
     *                Possible values are:
     *                {@link BluetoothProfile#HEADSET},
     *                {@link BluetoothProfile#A2DP},
     *                {@link BluetoothProfile#HEARING_AID}
     *                {@link BluetoothProfile#LE_AUDIO}
     * @return A list of active bluetooth devices
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    public List<BluetoothDevice> getActiveDevices(@ActiveDeviceProfile int profile) {
        List<BluetoothDevice> activeDevices = new ArrayList<>();

        switch (profile) {
            case BluetoothProfile.HEADSET:
                if (mHeadsetService == null) {
                    Log.e(TAG, "getActiveDevices: HeadsetService is null");
                } else {
                    BluetoothDevice device = mHeadsetService.getActiveDevice();
                    if (device != null) {
                        activeDevices.add(device);
                    }
                    Log.i(TAG, "getActiveDevices: Headset device: " + device);
                }
                break;
            case BluetoothProfile.A2DP:
                if (mA2dpService == null) {
                    Log.e(TAG, "getActiveDevices: A2dpService is null");
                } else {
                    BluetoothDevice device = mA2dpService.getActiveDevice();
                    if (device != null) {
                        activeDevices.add(device);
                    }
                    Log.i(TAG, "getActiveDevices: A2dp device: " + device);
                }
                break;
            case BluetoothProfile.HEARING_AID:
                if (mHearingAidService == null) {
                    Log.e(TAG, "getActiveDevices: HearingAidService is null");
                } else {
                    activeDevices = mHearingAidService.getActiveDevices();
                    Log.i(TAG, "getActiveDevices: Hearing Aid devices: Left["
                            + activeDevices.get(0) + "] - Right[" + activeDevices.get(1) + "]");
                }
                break;
            case BluetoothProfile.LE_AUDIO:
                if (mLeAudioService == null) {
                Log.e(TAG, "getActiveDevices: LeAudioService is null");
                } else {
                    activeDevices = mLeAudioService.getActiveDevices();
                    Log.i(TAG, "getActiveDevices: LeAudio devices: Lead["
                            + activeDevices.get(0) + "] - member_1[" + activeDevices.get(1) + "]");
                }
                break;
            default:
                Log.e(TAG, "getActiveDevices: profile value is not valid");
        }
        return activeDevices;
    }

    /**
     * Attempts connection to all enabled and supported bluetooth profiles between the local and
     * remote device
     *
     * @param device is the remote device with which to connect these profiles
     * @return {@link BluetoothStatusCodes#SUCCESS} if all profiles connections are attempted, false
     *         if an error occurred
     */
    @RequiresPermission(allOf = {
            android.Manifest.permission.BLUETOOTH_PRIVILEGED,
            android.Manifest.permission.MODIFY_PHONE_STATE,
    })
    public int connectAllEnabledProfiles(BluetoothDevice device) {
        if (!profileServicesRunning()) {
            Log.e(TAG, "connectAllEnabledProfiles: Not all profile services running");
            return BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED;
        }

        // Checks if any profiles are enabled and if so, only connect enabled profiles
        if (isAnyProfileEnabled(device)) {
            return connectEnabledProfiles(device);
        }

        int numProfilesConnected = 0;
        ParcelUuid[] remoteDeviceUuids = getRemoteUuids(device);
        ParcelUuid[] localDeviceUuids = mAdapterProperties.getUuids();

        // All profile toggles disabled, so connects all supported profiles
        if (mA2dpService != null && isSupported(localDeviceUuids, remoteDeviceUuids,
                BluetoothProfile.A2DP, device)) {
            Log.i(TAG, "connectAllEnabledProfiles: Connecting A2dp");
            // Set connection policy also connects the profile with CONNECTION_POLICY_ALLOWED
            mA2dpService.setConnectionPolicy(device, BluetoothProfile.CONNECTION_POLICY_ALLOWED);
            numProfilesConnected++;
        }
        if (mA2dpSinkService != null && isSupported(localDeviceUuids, remoteDeviceUuids,
                BluetoothProfile.A2DP_SINK, device)) {
            Log.i(TAG, "connectAllEnabledProfiles: Connecting A2dp Sink");
            mA2dpSinkService.setConnectionPolicy(device,
                    BluetoothProfile.CONNECTION_POLICY_ALLOWED);
            numProfilesConnected++;
        }
        if (mHeadsetService != null && isSupported(localDeviceUuids, remoteDeviceUuids,
                BluetoothProfile.HEADSET, device)) {
            Log.i(TAG, "connectAllEnabledProfiles: Connecting Headset Profile");
            mHeadsetService.setConnectionPolicy(device, BluetoothProfile.CONNECTION_POLICY_ALLOWED);
            numProfilesConnected++;
        }
        if (mHeadsetClientService != null && isSupported(localDeviceUuids, remoteDeviceUuids,
                BluetoothProfile.HEADSET_CLIENT, device)) {
            Log.i(TAG, "connectAllEnabledProfiles: Connecting HFP");
            mHeadsetClientService.setConnectionPolicy(device,
                    BluetoothProfile.CONNECTION_POLICY_ALLOWED);
            numProfilesConnected++;
        }
        if (mMapClientService != null && isSupported(localDeviceUuids, remoteDeviceUuids,
                BluetoothProfile.MAP_CLIENT, device)) {
            Log.i(TAG, "connectAllEnabledProfiles: Connecting MAP");
            mMapClientService.setConnectionPolicy(device,
                    BluetoothProfile.CONNECTION_POLICY_ALLOWED);
            numProfilesConnected++;
        }
        if (mHidHostService != null && isSupported(localDeviceUuids, remoteDeviceUuids,
                BluetoothProfile.HID_HOST, device)) {
            Log.i(TAG, "connectAllEnabledProfiles: Connecting Hid Host Profile");
            mHidHostService.setConnectionPolicy(device, BluetoothProfile.CONNECTION_POLICY_ALLOWED);
            numProfilesConnected++;
        }
        if (mPanService != null && isSupported(localDeviceUuids, remoteDeviceUuids,
                BluetoothProfile.PAN, device)) {
            Log.i(TAG, "connectAllEnabledProfiles: Connecting Pan Profile");
            mPanService.setConnectionPolicy(device, BluetoothProfile.CONNECTION_POLICY_ALLOWED);
            numProfilesConnected++;
        }
        if (mPbapClientService != null && isSupported(localDeviceUuids, remoteDeviceUuids,
                BluetoothProfile.PBAP_CLIENT, device)) {
            Log.i(TAG, "connectAllEnabledProfiles: Connecting Pbap");
            mPbapClientService.setConnectionPolicy(device,
                    BluetoothProfile.CONNECTION_POLICY_ALLOWED);
            numProfilesConnected++;
        }
        if (mHearingAidService != null && isSupported(localDeviceUuids, remoteDeviceUuids,
                BluetoothProfile.HEARING_AID, device)) {
            Log.i(TAG, "connectAllEnabledProfiles: Connecting Hearing Aid Profile");
            mHearingAidService.setConnectionPolicy(device,
                    BluetoothProfile.CONNECTION_POLICY_ALLOWED);
            numProfilesConnected++;
        }
        if (mHapClientService != null && isSupported(localDeviceUuids, remoteDeviceUuids,
                BluetoothProfile.HAP_CLIENT, device)) {
            Log.i(TAG, "connectAllEnabledProfiles: Connecting Hearing Access Client Profile");
            mHapClientService.setConnectionPolicy(device,
                    BluetoothProfile.CONNECTION_POLICY_ALLOWED);
            numProfilesConnected++;
        }
        if (mVolumeControlService != null && isSupported(localDeviceUuids, remoteDeviceUuids,
                BluetoothProfile.VOLUME_CONTROL, device)) {
            Log.i(TAG, "connectAllEnabledProfiles: Connecting Volume Control Profile");
            mVolumeControlService.setConnectionPolicy(device,
                    BluetoothProfile.CONNECTION_POLICY_ALLOWED);
            numProfilesConnected++;
        }
        if (mCsipSetCoordinatorService != null
                && isSupported(localDeviceUuids, remoteDeviceUuids,
                        BluetoothProfile.CSIP_SET_COORDINATOR, device)) {
            Log.i(TAG, "connectAllEnabledProfiles: Connecting Coordinated Set Profile");
            mCsipSetCoordinatorService.setConnectionPolicy(
                    device, BluetoothProfile.CONNECTION_POLICY_ALLOWED);
            numProfilesConnected++;
        }
        if (mLeAudioService != null && isSupported(localDeviceUuids, remoteDeviceUuids,
                BluetoothProfile.LE_AUDIO, device)) {
            Log.i(TAG, "connectAllEnabledProfiles: Connecting LeAudio profile (BAP)");
            mLeAudioService.setConnectionPolicy(device,
                    BluetoothProfile.CONNECTION_POLICY_ALLOWED);
            numProfilesConnected++;
        }
        if (mBassClientService != null && isSupported(localDeviceUuids, remoteDeviceUuids,
                BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT, device)) {
            Log.i(TAG, "connectAllEnabledProfiles: Connecting LE Broadcast Assistant Profile");
            mBassClientService.setConnectionPolicy(device,
                    BluetoothProfile.CONNECTION_POLICY_ALLOWED);
            numProfilesConnected++;
        }

        Log.i(TAG, "connectAllEnabledProfiles: Number of Profiles Connected: "
                + numProfilesConnected);

        return BluetoothStatusCodes.SUCCESS;
    }

    /**
     * Disconnects all enabled and supported bluetooth profiles between the local and remote device
     *
     * @param device is the remote device with which to disconnect these profiles
     * @return true if all profiles successfully disconnected, false if an error occurred
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    public int disconnectAllEnabledProfiles(BluetoothDevice device) {
        if (!profileServicesRunning()) {
            Log.e(TAG, "disconnectAllEnabledProfiles: Not all profile services bound");
            return BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED;
        }

        if (mA2dpService != null && (mA2dpService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTED
                || mA2dpService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTING)) {
            Log.i(TAG, "disconnectAllEnabledProfiles: Disconnecting A2dp");
            mA2dpService.disconnect(device);
        }
        if (mA2dpSinkService != null && (mA2dpSinkService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTED
                || mA2dpSinkService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTING)) {
            Log.i(TAG, "disconnectAllEnabledProfiles: Disconnecting A2dp Sink");
            mA2dpSinkService.disconnect(device);
        }
        if (mHeadsetService != null && (mHeadsetService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTED
                ||  mHeadsetService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTING)) {
            Log.i(TAG,
                    "disconnectAllEnabledProfiles: Disconnecting Headset Profile");
            mHeadsetService.disconnect(device);
        }
        if (mHeadsetClientService != null && (mHeadsetClientService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTED
                || mHeadsetClientService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTING)) {
            Log.i(TAG, "disconnectAllEnabledProfiles: Disconnecting HFP");
            mHeadsetClientService.disconnect(device);
        }
        if (mMapClientService != null && (mMapClientService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTED
                || mMapClientService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTING)) {
            Log.i(TAG, "disconnectAllEnabledProfiles: Disconnecting MAP Client");
            mMapClientService.disconnect(device);
        }
        if (mMapService != null && (mMapService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTED
                || mMapService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTING)) {
            Log.i(TAG, "disconnectAllEnabledProfiles: Disconnecting MAP");
            mMapService.disconnect(device);
        }
        if (mHidDeviceService != null && (mHidDeviceService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTED
                || mHidDeviceService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTING)) {
            Log.i(TAG, "disconnectAllEnabledProfiles: Disconnecting Hid Device Profile");
            mHidDeviceService.disconnect(device);
        }
        if (mHidHostService != null && (mHidHostService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTED
                || mHidHostService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTING)) {
            Log.i(TAG, "disconnectAllEnabledProfiles: Disconnecting Hid Host Profile");
            mHidHostService.disconnect(device);
        }
        if (mPanService != null && (mPanService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTED
                || mPanService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTING)) {
            Log.i(TAG, "disconnectAllEnabledProfiles: Disconnecting Pan Profile");
            mPanService.disconnect(device);
        }
        if (mPbapClientService != null && (mPbapClientService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTED
                || mPbapClientService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTING)) {
            Log.i(TAG, "disconnectAllEnabledProfiles: Disconnecting Pbap Client");
            mPbapClientService.disconnect(device);
        }
        if (mPbapService != null && (mPbapService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTED
                || mPbapService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTING)) {
            Log.i(TAG, "disconnectAllEnabledProfiles: Disconnecting Pbap Server");
            mPbapService.disconnect(device);
        }
        if (mHearingAidService != null && (mHearingAidService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTED
                || mHearingAidService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTING)) {
            Log.i(TAG, "disconnectAllEnabledProfiles: Disconnecting Hearing Aid Profile");
            mHearingAidService.disconnect(device);
        }
        if (mHapClientService != null && (mHapClientService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTED
                || mHapClientService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTING)) {
            Log.i(TAG, "disconnectAllEnabledProfiles: Disconnecting Hearing Access Profile Client");
            mHapClientService.disconnect(device);
        }
        if (mVolumeControlService != null && (mVolumeControlService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTED
                || mVolumeControlService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTING)) {
            Log.i(TAG, "disconnectAllEnabledProfiles: Disconnecting Volume Control Profile");
            mVolumeControlService.disconnect(device);
        }
        if (mSapService != null && (mSapService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTED
                || mSapService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTING)) {
            Log.i(TAG, "disconnectAllEnabledProfiles: Disconnecting Sap Profile");
            mSapService.disconnect(device);
        }
        if (mCsipSetCoordinatorService != null
                && (mCsipSetCoordinatorService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTED
                || mCsipSetCoordinatorService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTING)) {
            Log.i(TAG, "disconnectAllEnabledProfiles: Disconnecting Coordinater Set Profile");
            mCsipSetCoordinatorService.disconnect(device);
        }
        if (mLeAudioService != null && (mLeAudioService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTED
                || mLeAudioService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTING)) {
            Log.i(TAG, "disconnectAllEnabledProfiles: Disconnecting LeAudio profile (BAP)");
            mLeAudioService.disconnect(device);
        }
        if (mBassClientService != null && (mBassClientService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTED
                || mBassClientService.getConnectionState(device)
                == BluetoothProfile.STATE_CONNECTING)) {
            Log.i(TAG, "disconnectAllEnabledProfiles: Disconnecting "
                            + "LE Broadcast Assistant Profile");
            mBassClientService.disconnect(device);
        }

        return BluetoothStatusCodes.SUCCESS;
    }

    /**
     * Same as API method {@link BluetoothDevice#getName()}
     *
     * @param device remote device of interest
     * @return remote device name
     */
    public String getRemoteName(BluetoothDevice device) {
        if (mRemoteDevices == null) {
            return null;
        }
        DeviceProperties deviceProp = mRemoteDevices.getDeviceProperties(device);
        if (deviceProp == null) {
            return null;
        }
        return deviceProp.getName();
    }

    /**
     * Get UUIDs for service supported by a remote device
     *
     * @param device the remote device that we want to get UUIDs from
     * @return
     */
    @VisibleForTesting
    public ParcelUuid[] getRemoteUuids(BluetoothDevice device) {
        DeviceProperties deviceProp = mRemoteDevices.getDeviceProperties(device);
        if (deviceProp == null) {
            return null;
        }
        return deviceProp.getUuids();
    }

    public Set<IBluetoothConnectionCallback> getBluetoothConnectionCallbacks() {
        return mBluetoothConnectionCallbacks;
    }

    /**
     * Converts HCI disconnect reasons to Android disconnect reasons.
     * <p>
     * The HCI Error Codes used for ACL disconnect reasons propagated up from native code were
     * copied from: {@link system/bt/stack/include/hci_error_code.h}.
     * <p>
     * These error codes are specified and described in Bluetooth Core Spec v5.1, Vol 2, Part D.
     *
     * @param hciReason is the raw HCI disconnect reason from native.
     * @return the Android disconnect reason for apps.
     */
    static @BluetoothAdapter.BluetoothConnectionCallback.DisconnectReason int
            hciToAndroidDisconnectReason(int hciReason) {
        switch(hciReason) {
            case /*HCI_SUCCESS*/ 0x00:
            case /*HCI_ERR_UNSPECIFIED*/ 0x1F:
            case /*HCI_ERR_UNDEFINED*/ 0xff:
                return BluetoothStatusCodes.ERROR_UNKNOWN;
            case /*HCI_ERR_ILLEGAL_COMMAND*/ 0x01:
            case /*HCI_ERR_NO_CONNECTION*/ 0x02:
            case /*HCI_ERR_HW_FAILURE*/ 0x03:
            case /*HCI_ERR_DIFF_TRANSACTION_COLLISION*/ 0x2A:
            case /*HCI_ERR_ROLE_SWITCH_PENDING*/ 0x32:
            case /*HCI_ERR_ROLE_SWITCH_FAILED*/ 0x35:
                return BluetoothStatusCodes.ERROR_DISCONNECT_REASON_LOCAL;
            case /*HCI_ERR_PAGE_TIMEOUT*/ 0x04:
            case /*HCI_ERR_CONNECTION_TOUT*/ 0x08:
            case /*HCI_ERR_HOST_TIMEOUT*/ 0x10:
            case /*HCI_ERR_LMP_RESPONSE_TIMEOUT*/ 0x22:
            case /*HCI_ERR_ADVERTISING_TIMEOUT*/ 0x3C:
            case /*HCI_ERR_CONN_FAILED_ESTABLISHMENT*/ 0x3E:
                return BluetoothStatusCodes.ERROR_DISCONNECT_REASON_TIMEOUT;
            case /*HCI_ERR_AUTH_FAILURE*/ 0x05:
            case /*HCI_ERR_KEY_MISSING*/ 0x06:
            case /*HCI_ERR_HOST_REJECT_SECURITY*/ 0x0E:
            case /*HCI_ERR_REPEATED_ATTEMPTS*/ 0x17:
            case /*HCI_ERR_PAIRING_NOT_ALLOWED*/ 0x18:
            case /*HCI_ERR_ENCRY_MODE_NOT_ACCEPTABLE*/ 0x25:
            case /*HCI_ERR_UNIT_KEY_USED*/ 0x26:
            case /*HCI_ERR_PAIRING_WITH_UNIT_KEY_NOT_SUPPORTED*/ 0x29:
            case /*HCI_ERR_INSUFFCIENT_SECURITY*/ 0x2F:
            case /*HCI_ERR_HOST_BUSY_PAIRING*/ 0x38:
                return BluetoothStatusCodes.ERROR_DISCONNECT_REASON_SECURITY;
            case /*HCI_ERR_MEMORY_FULL*/ 0x07:
            case /*HCI_ERR_MAX_NUM_OF_CONNECTIONS*/ 0x09:
            case /*HCI_ERR_MAX_NUM_OF_SCOS*/ 0x0A:
            case /*HCI_ERR_COMMAND_DISALLOWED*/ 0x0C:
            case /*HCI_ERR_HOST_REJECT_RESOURCES*/ 0x0D:
            case /*HCI_ERR_LIMIT_REACHED*/ 0x43:
                return BluetoothStatusCodes.ERROR_DISCONNECT_REASON_RESOURCE_LIMIT_REACHED;
            case /*HCI_ERR_CONNECTION_EXISTS*/ 0x0B:
                return BluetoothStatusCodes.ERROR_DISCONNECT_REASON_CONNECTION_ALREADY_EXISTS;
            case /*HCI_ERR_HOST_REJECT_DEVICE*/ 0x0F:
                return BluetoothStatusCodes.ERROR_DISCONNECT_REASON_SYSTEM_POLICY;
            case /*HCI_ERR_ILLEGAL_PARAMETER_FMT*/ 0x12:
                return BluetoothStatusCodes.ERROR_DISCONNECT_REASON_BAD_PARAMETERS;
            case /*HCI_ERR_PEER_USER*/ 0x13:
                return BluetoothStatusCodes.ERROR_DISCONNECT_REASON_REMOTE_REQUEST;
            case /*HCI_ERR_REMOTE_POWER_OFF*/ 0x15:
                return BluetoothStatusCodes.ERROR_DISCONNECT_REASON_REMOTE_REQUEST;
            case /*HCI_ERR_CONN_CAUSE_LOCAL_HOST*/ 0x16:
                return BluetoothStatusCodes.ERROR_DISCONNECT_REASON_LOCAL_REQUEST;
            case /*HCI_ERR_UNSUPPORTED_REM_FEATURE*/ 0x1A:
                return BluetoothStatusCodes.ERROR_DISCONNECT_REASON_REMOTE;
            case /*HCI_ERR_UNACCEPT_CONN_INTERVAL*/ 0x3B:
                return BluetoothStatusCodes.ERROR_DISCONNECT_REASON_BAD_PARAMETERS;
            default:
                Log.e(TAG, "Invalid HCI disconnect reason: " + hciReason);
                return BluetoothStatusCodes.ERROR_UNKNOWN;
        }
    }

    void logUserBondResponse(BluetoothDevice device, boolean accepted, int event) {
        final long token = Binder.clearCallingIdentity();
        try {
            BluetoothStatsLog.write(BluetoothStatsLog.BLUETOOTH_BOND_STATE_CHANGED,
                    obfuscateAddress(device), 0, device.getType(),
                    BluetoothDevice.BOND_BONDING,
                    event,
                    accepted ? 0 : BluetoothDevice.UNBOND_REASON_AUTH_REJECTED);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    int getDeviceAccessFromPrefs(BluetoothDevice device, String prefFile) {
        SharedPreferences prefs = getSharedPreferences(prefFile, Context.MODE_PRIVATE);
        if (!prefs.contains(device.getAddress())) {
            return BluetoothDevice.ACCESS_UNKNOWN;
        }
        return prefs.getBoolean(device.getAddress(), false)
                ? BluetoothDevice.ACCESS_ALLOWED
                : BluetoothDevice.ACCESS_REJECTED;
    }

    void setDeviceAccessFromPrefs(BluetoothDevice device, int value, String prefFile) {
        SharedPreferences pref = getSharedPreferences(prefFile, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        if (value == BluetoothDevice.ACCESS_UNKNOWN) {
            editor.remove(device.getAddress());
        } else {
            editor.putBoolean(device.getAddress(), value == BluetoothDevice.ACCESS_ALLOWED);
        }
        editor.apply();
    }

    public void setPhonebookAccessPermission(BluetoothDevice device, int value) {
        setDeviceAccessFromPrefs(device, value, PHONEBOOK_ACCESS_PERMISSION_PREFERENCE_FILE);
    }

    public void setMessageAccessPermission(BluetoothDevice device, int value) {
        setDeviceAccessFromPrefs(device, value, MESSAGE_ACCESS_PERMISSION_PREFERENCE_FILE);
    }

    public void setSimAccessPermission(BluetoothDevice device, int value) {
        setDeviceAccessFromPrefs(device, value, SIM_ACCESS_PERMISSION_PREFERENCE_FILE);
    }

    public boolean isRpaOffloadSupported() {
        return mAdapterProperties.isRpaOffloadSupported();
    }

    public int getNumOfOffloadedIrkSupported() {
        return mAdapterProperties.getNumOfOffloadedIrkSupported();
    }

    public int getNumOfOffloadedScanFilterSupported() {
        return mAdapterProperties.getNumOfOffloadedScanFilterSupported();
    }

    public int getOffloadedScanResultStorage() {
        return mAdapterProperties.getOffloadedScanResultStorage();
    }

    public boolean isLe2MPhySupported() {
        return mAdapterProperties.isLe2MPhySupported();
    }

    public boolean isLeCodedPhySupported() {
        return mAdapterProperties.isLeCodedPhySupported();
    }

    public boolean isLeExtendedAdvertisingSupported() {
        return mAdapterProperties.isLeExtendedAdvertisingSupported();
    }

    public boolean isLePeriodicAdvertisingSupported() {
        return mAdapterProperties.isLePeriodicAdvertisingSupported();
    }

    /**
     * Check if the LE audio broadcast source feature is supported.
     *
     * @return true, if the LE audio broadcast source is supported
     */
    public boolean isLeAudioBroadcastSourceSupported() {
        return  mAdapterProperties.isLePeriodicAdvertisingSupported()
                && mAdapterProperties.isLeExtendedAdvertisingSupported()
                && mAdapterProperties.isLeIsochronousBroadcasterSupported();
    }

    /**
     * Check if the LE audio broadcast assistant feature is supported.
     *
     * @return true, if the LE audio broadcast assistant is supported
     */
    public boolean isLeAudioBroadcastAssistantSupported() {
        return mAdapterProperties.isLePeriodicAdvertisingSupported()
            && mAdapterProperties.isLeExtendedAdvertisingSupported()
            && (mAdapterProperties.isLePeriodicAdvertisingSyncTransferSenderSupported()
                || mAdapterProperties.isLePeriodicAdvertisingSyncTransferRecipientSupported());
    }

    public long getSupportedProfilesBitMask() {
        return Config.getSupportedProfilesBitMask();
    }

    /**
     * Check if the LE audio CIS central feature is supported.
     *
     * @return true, if the LE audio CIS central is supported
     */
    public boolean isLeConnectedIsochronousStreamCentralSupported() {
        return mAdapterProperties.isLeConnectedIsochronousStreamCentralSupported();
    }

    public int getLeMaximumAdvertisingDataLength() {
        return mAdapterProperties.getLeMaximumAdvertisingDataLength();
    }

    /**
     * Get the maximum number of connected audio devices.
     *
     * @return the maximum number of connected audio devices
     */
    public int getMaxConnectedAudioDevices() {
        return mAdapterProperties.getMaxConnectedAudioDevices();
    }

    /**
     * Check whether A2DP offload is enabled.
     *
     * @return true if A2DP offload is enabled
     */
    public boolean isA2dpOffloadEnabled() {
        return mAdapterProperties.isA2dpOffloadEnabled();
    }

    @VisibleForTesting
    BluetoothActivityEnergyInfo reportActivityInfo() {
        if (mAdapterProperties.getState() != BluetoothAdapter.STATE_ON
                || !mAdapterProperties.isActivityAndEnergyReportingSupported()) {
            return null;
        }

        // Pull the data. The callback will notify mEnergyInfoLock.
        readEnergyInfo();

        synchronized (mEnergyInfoLock) {
            try {
                mEnergyInfoLock.wait(CONTROLLER_ENERGY_UPDATE_TIMEOUT_MILLIS);
            } catch (InterruptedException e) {
                // Just continue, the energy data may be stale but we won't miss anything next time
                // we query.
            }

            final BluetoothActivityEnergyInfo info =
                    new BluetoothActivityEnergyInfo(SystemClock.elapsedRealtime(),
                            mStackReportedState, mTxTimeTotalMs, mRxTimeTotalMs, mIdleTimeTotalMs,
                            mEnergyUsedTotalVoltAmpSecMicro);

            // Count the number of entries that have byte counts > 0
            int arrayLen = 0;
            for (int i = 0; i < mUidTraffic.size(); i++) {
                final UidTraffic traffic = mUidTraffic.valueAt(i);
                if (traffic.getTxBytes() != 0 || traffic.getRxBytes() != 0) {
                    arrayLen++;
                }
            }

            // Copy the traffic objects whose byte counts are > 0
            final List<UidTraffic> result = new ArrayList<>();
            int putIdx = 0;
            for (int i = 0; i < mUidTraffic.size(); i++) {
                final UidTraffic traffic = mUidTraffic.valueAt(i);
                if (traffic.getTxBytes() != 0 || traffic.getRxBytes() != 0) {
                    result.add(traffic.clone());
                }
            }

            info.setUidTraffic(result);

            return info;
        }
    }

    public int getTotalNumOfTrackableAdvertisements() {
        return mAdapterProperties.getTotalNumOfTrackableAdvertisements();
    }

    /**
     * Notify the UID and package name of the app, and the address of associated active device
     *
     * @param source The attribution source that starts the activity
     * @param deviceAddress The address of the active device associated with the app
     */
    public void notifyActivityAttributionInfo(AttributionSource source, String deviceAddress) {
        mActivityAttributionService.notifyActivityAttributionInfo(
                source.getUid(), source.getPackageName(), deviceAddress);
    }

    static int convertScanModeToHal(int mode) {
        switch (mode) {
            case BluetoothAdapter.SCAN_MODE_NONE:
                return AbstractionLayer.BT_SCAN_MODE_NONE;
            case BluetoothAdapter.SCAN_MODE_CONNECTABLE:
                return AbstractionLayer.BT_SCAN_MODE_CONNECTABLE;
            case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE:
                return AbstractionLayer.BT_SCAN_MODE_CONNECTABLE_DISCOVERABLE;
        }
        // errorLog("Incorrect scan mode in convertScanModeToHal");
        return -1;
    }

    static int convertScanModeFromHal(int mode) {
        switch (mode) {
            case AbstractionLayer.BT_SCAN_MODE_NONE:
                return BluetoothAdapter.SCAN_MODE_NONE;
            case AbstractionLayer.BT_SCAN_MODE_CONNECTABLE:
                return BluetoothAdapter.SCAN_MODE_CONNECTABLE;
            case AbstractionLayer.BT_SCAN_MODE_CONNECTABLE_DISCOVERABLE:
                return BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
        }
        //errorLog("Incorrect scan mode in convertScanModeFromHal");
        return -1;
    }

    // This function is called from JNI. It allows native code to set a single wake
    // alarm. If an alarm is already pending and a new request comes in, the alarm
    // will be rescheduled (i.e. the previously set alarm will be cancelled).
    private boolean setWakeAlarm(long delayMillis, boolean shouldWake) {
        synchronized (this) {
            if (mPendingAlarm != null) {
                mAlarmManager.cancel(mPendingAlarm);
            }

            long wakeupTime = SystemClock.elapsedRealtime() + delayMillis;
            int type = shouldWake ? AlarmManager.ELAPSED_REALTIME_WAKEUP
                    : AlarmManager.ELAPSED_REALTIME;

            Intent intent = new Intent(ACTION_ALARM_WAKEUP);
            mPendingAlarm =
                    PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_ONE_SHOT
                            | PendingIntent.FLAG_IMMUTABLE);
            mAlarmManager.setExact(type, wakeupTime, mPendingAlarm);
            return true;
        }
    }

    // This function is called from JNI. It allows native code to acquire a single wake lock.
    // If the wake lock is already held, this function returns success. Although this function
    // only supports acquiring a single wake lock at a time right now, it will eventually be
    // extended to allow acquiring an arbitrary number of wake locks. The current interface
    // takes |lockName| as a parameter in anticipation of that implementation.
    private boolean acquireWakeLock(String lockName) {
        synchronized (this) {
            if (mWakeLock == null) {
                mWakeLockName = lockName;
                mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, lockName);
            }

            if (!mWakeLock.isHeld()) {
                mWakeLock.acquire();
            }
        }
        return true;
    }

    // This function is called from JNI. It allows native code to release a wake lock acquired
    // by |acquireWakeLock|. If the wake lock is not held, this function returns failure.
    // Note that the release() call is also invoked by {@link #cleanup()} so a synchronization is
    // needed here. See the comment for |acquireWakeLock| for an explanation of the interface.
    private boolean releaseWakeLock(String lockName) {
        synchronized (this) {
            if (mWakeLock == null) {
                errorLog("Repeated wake lock release; aborting release: " + lockName);
                return false;
            }

            if (mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        }
        return true;
    }

    private void energyInfoCallback(int status, int ctrlState, long txTime, long rxTime,
            long idleTime, long energyUsed, UidTraffic[] data) throws RemoteException {
        if (ctrlState >= BluetoothActivityEnergyInfo.BT_STACK_STATE_INVALID
                && ctrlState <= BluetoothActivityEnergyInfo.BT_STACK_STATE_STATE_IDLE) {
            // Energy is product of mA, V and ms. If the chipset doesn't
            // report it, we have to compute it from time
            if (energyUsed == 0) {
                try {
                    final long txMah = Math.multiplyExact(txTime, getTxCurrentMa());
                    final long rxMah = Math.multiplyExact(rxTime, getRxCurrentMa());
                    final long idleMah = Math.multiplyExact(idleTime, getIdleCurrentMa());
                    energyUsed = (long) (Math.addExact(Math.addExact(txMah, rxMah), idleMah)
                            * getOperatingVolt());
                } catch (ArithmeticException e) {
                    Log.wtf(TAG, "overflow in bluetooth energy callback", e);
                    // Energy is already 0 if the exception was thrown.
                }
            }

            synchronized (mEnergyInfoLock) {
                mStackReportedState = ctrlState;
                long totalTxTimeMs;
                long totalRxTimeMs;
                long totalIdleTimeMs;
                long totalEnergy;
                try {
                    totalTxTimeMs = Math.addExact(mTxTimeTotalMs, txTime);
                    totalRxTimeMs = Math.addExact(mRxTimeTotalMs, rxTime);
                    totalIdleTimeMs = Math.addExact(mIdleTimeTotalMs, idleTime);
                    totalEnergy = Math.addExact(mEnergyUsedTotalVoltAmpSecMicro, energyUsed);
                } catch (ArithmeticException e) {
                    // This could be because we accumulated a lot of time, or we got a very strange
                    // value from the controller (more likely). Discard this data.
                    Log.wtf(TAG, "overflow in bluetooth energy callback", e);
                    totalTxTimeMs = mTxTimeTotalMs;
                    totalRxTimeMs = mRxTimeTotalMs;
                    totalIdleTimeMs = mIdleTimeTotalMs;
                    totalEnergy = mEnergyUsedTotalVoltAmpSecMicro;
                }

                mTxTimeTotalMs = totalTxTimeMs;
                mRxTimeTotalMs = totalRxTimeMs;
                mIdleTimeTotalMs = totalIdleTimeMs;
                mEnergyUsedTotalVoltAmpSecMicro = totalEnergy;

                for (UidTraffic traffic : data) {
                    UidTraffic existingTraffic = mUidTraffic.get(traffic.getUid());
                    if (existingTraffic == null) {
                        mUidTraffic.put(traffic.getUid(), traffic);
                    } else {
                        existingTraffic.addRxBytes(traffic.getRxBytes());
                        existingTraffic.addTxBytes(traffic.getTxBytes());
                    }
                }
                mEnergyInfoLock.notifyAll();
            }
        }

        verboseLog("energyInfoCallback() status = " + status + "txTime = " + txTime + "rxTime = "
                + rxTime + "idleTime = " + idleTime + "energyUsed = " + energyUsed + "ctrlState = "
                + ctrlState + "traffic = " + Arrays.toString(data));
    }

    /**
     * Update metadata change to registered listeners
     */
    @VisibleForTesting
    public void metadataChanged(String address, int key, byte[] value) {
        BluetoothDevice device = mRemoteDevices.getDevice(Utils.getBytesFromAddress(address));

        // pass just interesting metadata to native, to reduce spam
        if (key == BluetoothDevice.METADATA_LE_AUDIO) {
            metadataChangedNative(Utils.getBytesFromAddress(address), key, value);
        }

        if (mMetadataListeners.containsKey(device)) {
            ArrayList<IBluetoothMetadataListener> list = mMetadataListeners.get(device);
            for (IBluetoothMetadataListener listener : list) {
                try {
                    listener.onMetadataChanged(device, key, value);
                } catch (RemoteException e) {
                    Log.w(TAG, "RemoteException when onMetadataChanged");
                }
            }
        }
    }

    private int getIdleCurrentMa() {
        return BluetoothProperties.getHardwareIdleCurrentMa().orElse(0);
    }

    private int getTxCurrentMa() {
        return BluetoothProperties.getHardwareTxCurrentMa().orElse(0);
    }

    private int getRxCurrentMa() {
        return BluetoothProperties.getHardwareRxCurrentMa().orElse(0);
    }

    private double getOperatingVolt() {
        return BluetoothProperties.getHardwareOperatingVoltageMv().orElse(0) / 1000.0;
    }

    @VisibleForTesting
    protected RemoteDevices getRemoteDevices() {
        return mRemoteDevices;
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        if (args.length == 0) {
            writer.println("Skipping dump in APP SERVICES, see bluetooth_manager section.");
            writer.println("Use --print argument for dumpsys direct from AdapterService.");
            return;
        }

        if ("set-test-mode".equals(args[0])) {
            final boolean testModeEnabled = "enabled".equalsIgnoreCase(args[1]);
            for (ProfileService profile : mRunningProfiles) {
                profile.setTestModeEnabled(testModeEnabled);
            }
            mTestModeEnabled = testModeEnabled;
            return;
        }

        verboseLog("dumpsys arguments, check for protobuf output: " + TextUtils.join(" ", args));
        if (args[0].equals("--proto-bin")) {
            dumpMetrics(fd);
            return;
        }

        writer.println();
        mAdapterProperties.dump(fd, writer, args);
        writer.println("mSnoopLogSettingAtEnable = " + mSnoopLogSettingAtEnable);
        writer.println("mDefaultSnoopLogSettingAtEnable = " + mDefaultSnoopLogSettingAtEnable);

        writer.println();
        writer.println("Enabled Profile Services:");
        for (Class profile : Config.getSupportedProfiles()) {
            writer.println("  " + profile.getSimpleName());
        }
        writer.println();

        mAdapterStateMachine.dump(fd, writer, args);

        StringBuilder sb = new StringBuilder();
        for (ProfileService profile : mRegisteredProfiles) {
            profile.dump(sb);
        }
        mSilenceDeviceManager.dump(fd, writer, args);
        mDatabaseManager.dump(writer);

        writer.write(sb.toString());
        writer.flush();

        dumpNative(fd, args);
    }

    private void dumpMetrics(FileDescriptor fd) {
        BluetoothMetricsProto.BluetoothLog.Builder metricsBuilder =
                BluetoothMetricsProto.BluetoothLog.newBuilder();
        byte[] nativeMetricsBytes = dumpMetricsNative();
        debugLog("dumpMetrics: native metrics size is " + nativeMetricsBytes.length);
        if (nativeMetricsBytes.length > 0) {
            try {
                metricsBuilder.mergeFrom(nativeMetricsBytes);
            } catch (InvalidProtocolBufferException ex) {
                Log.w(TAG, "dumpMetrics: problem parsing metrics protobuf, " + ex.getMessage());
                return;
            }
        }
        metricsBuilder.setNumBondedDevices(getBondedDevices().length);
        MetricsLogger.dumpProto(metricsBuilder);
        for (ProfileService profile : mRegisteredProfiles) {
            profile.dumpProto(metricsBuilder);
        }
        byte[] metricsBytes = Base64.encode(metricsBuilder.build().toByteArray(), Base64.DEFAULT);
        debugLog("dumpMetrics: combined metrics size is " + metricsBytes.length);
        try (FileOutputStream protoOut = new FileOutputStream(fd)) {
            protoOut.write(metricsBytes);
        } catch (IOException e) {
            errorLog("dumpMetrics: error writing combined protobuf to fd, " + e.getMessage());
        }
    }

    private void debugLog(String msg) {
        if (DBG) {
            Log.d(TAG, msg);
        }
    }

    private void verboseLog(String msg) {
        if (VERBOSE) {
            Log.v(TAG, msg);
        }
    }

    private void errorLog(String msg) {
        Log.e(TAG, msg);
    }

    private final BroadcastReceiver mAlarmBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (AdapterService.this) {
                mPendingAlarm = null;
                alarmFiredNative();
            }
        }
    };

    private boolean isCommonCriteriaMode() {
        return getSystemService(DevicePolicyManager.class).isCommonCriteriaModeEnabled(null);
    }

    @SuppressLint("AndroidFrameworkRequiresPermission")
    private void enforceBluetoothPrivilegedPermissionIfNeeded(OobData remoteP192Data,
            OobData remoteP256Data) {
        if (remoteP192Data != null || remoteP256Data != null) {
            enforceBluetoothPrivilegedPermission(this);
        }
    }

    @RequiresPermission(android.Manifest.permission.READ_DEVICE_CONFIG)
    private String[] getInitFlags() {
        final DeviceConfig.Properties properties =
                DeviceConfig.getProperties(DeviceConfig.NAMESPACE_BLUETOOTH);
        ArrayList<String> initFlags = new ArrayList<>();
        for (String property: properties.getKeyset()) {
            if (property.startsWith("INIT_")) {
                initFlags.add(String.format("%s=%s", property,
                            properties.getString(property, null)));
            }
        }
        return initFlags.toArray(new String[0]);
    }

    private final Object mDeviceConfigLock = new Object();

    /**
     * Predicate that can be applied to names to determine if a device is
     * well-known to be used for physical location.
     */
    @GuardedBy("mDeviceConfigLock")
    private Predicate<String> mLocationDenylistName = (v) -> false;

    /**
     * Predicate that can be applied to MAC addresses to determine if a device
     * is well-known to be used for physical location.
     */
    @GuardedBy("mDeviceConfigLock")
    private Predicate<byte[]> mLocationDenylistMac = (v) -> false;

    /**
     * Predicate that can be applied to Advertising Data payloads to determine
     * if a device is well-known to be used for physical location.
     */
    @GuardedBy("mDeviceConfigLock")
    private Predicate<byte[]> mLocationDenylistAdvertisingData = (v) -> false;

    @GuardedBy("mDeviceConfigLock")
    private int mScanQuotaCount = DeviceConfigListener.DEFAULT_SCAN_QUOTA_COUNT;
    @GuardedBy("mDeviceConfigLock")
    private long mScanQuotaWindowMillis = DeviceConfigListener.DEFAULT_SCAN_QUOTA_WINDOW_MILLIS;
    @GuardedBy("mDeviceConfigLock")
    private long mScanTimeoutMillis = DeviceConfigListener.DEFAULT_SCAN_TIMEOUT_MILLIS;
    @GuardedBy("mDeviceConfigLock")
    private int mScanUpgradeDurationMillis =
            DeviceConfigListener.DEFAULT_SCAN_UPGRADE_DURATION_MILLIS;
    @GuardedBy("mDeviceConfigLock")
    private int mScreenOffLowPowerWindowMillis =
            ScanManager.SCAN_MODE_SCREEN_OFF_LOW_POWER_WINDOW_MS;
    @GuardedBy("mDeviceConfigLock")
    private int mScreenOffLowPowerIntervalMillis =
            ScanManager.SCAN_MODE_SCREEN_OFF_LOW_POWER_INTERVAL_MS;
    @GuardedBy("mDeviceConfigLock")
    private int mScreenOffBalancedWindowMillis =
            ScanManager.SCAN_MODE_SCREEN_OFF_BALANCED_WINDOW_MS;
    @GuardedBy("mDeviceConfigLock")
    private int mScreenOffBalancedIntervalMillis =
            ScanManager.SCAN_MODE_SCREEN_OFF_BALANCED_INTERVAL_MS;

    public @NonNull Predicate<String> getLocationDenylistName() {
        synchronized (mDeviceConfigLock) {
            return mLocationDenylistName;
        }
    }

    public @NonNull Predicate<byte[]> getLocationDenylistMac() {
        synchronized (mDeviceConfigLock) {
            return mLocationDenylistMac;
        }
    }

    public @NonNull Predicate<byte[]> getLocationDenylistAdvertisingData() {
        synchronized (mDeviceConfigLock) {
            return mLocationDenylistAdvertisingData;
        }
    }

    /**
     * Returns scan quota count.
     */
    public int getScanQuotaCount() {
        synchronized (mDeviceConfigLock) {
            return mScanQuotaCount;
        }
    }

    /**
     * Returns scan quota window in millis.
     */
    public long getScanQuotaWindowMillis() {
        synchronized (mDeviceConfigLock) {
            return mScanQuotaWindowMillis;
        }
    }

    /**
     * Returns scan timeout in millis.
     */
    public long getScanTimeoutMillis() {
        synchronized (mDeviceConfigLock) {
            return mScanTimeoutMillis;
        }
    }

    /**
     * Returns scan upgrade duration in millis.
     */
    public long getScanUpgradeDurationMillis() {
        synchronized (mDeviceConfigLock) {
            return mScanUpgradeDurationMillis;
        }
    }

    /**
     * Returns SCREEN_OFF_BALANCED scan window in millis.
     */
    public int getScreenOffBalancedWindowMillis() {
        synchronized (mDeviceConfigLock) {
            return mScreenOffBalancedWindowMillis;
        }
    }

    /**
     * Returns SCREEN_OFF_BALANCED scan interval in millis.
     */
    public int getScreenOffBalancedIntervalMillis() {
        synchronized (mDeviceConfigLock) {
            return mScreenOffBalancedIntervalMillis;
        }
    }

    /**
     * Returns SCREEN_OFF low power scan window in millis.
     */
    public int getScreenOffLowPowerWindowMillis() {
        synchronized (mDeviceConfigLock) {
            return mScreenOffLowPowerWindowMillis;
        }
    }

    /**
     * Returns SCREEN_OFF low power scan interval in millis.
     */
    public int getScreenOffLowPowerIntervalMillis() {
        synchronized (mDeviceConfigLock) {
            return mScreenOffLowPowerIntervalMillis;
        }
    }

    private final DeviceConfigListener mDeviceConfigListener = new DeviceConfigListener();

    private class DeviceConfigListener implements DeviceConfig.OnPropertiesChangedListener {
        private static final String LOCATION_DENYLIST_NAME =
                "location_denylist_name";
        private static final String LOCATION_DENYLIST_MAC =
                "location_denylist_mac";
        private static final String LOCATION_DENYLIST_ADVERTISING_DATA =
                "location_denylist_advertising_data";
        private static final String SCAN_QUOTA_COUNT =
                "scan_quota_count";
        private static final String SCAN_QUOTA_WINDOW_MILLIS =
                "scan_quota_window_millis";
        private static final String SCAN_TIMEOUT_MILLIS =
                "scan_timeout_millis";
        private static final String SCAN_UPGRADE_DURATION_MILLIS =
                "scan_upgrade_duration_millis";
        private static final String SCREEN_OFF_LOW_POWER_WINDOW_MILLIS =
                "screen_off_low_power_window_millis";
        private static final String SCREEN_OFF_LOW_POWER_INTERVAL_MILLIS =
                "screen_off_low_power_interval_millis";
        private static final String SCREEN_OFF_BALANCED_WINDOW_MILLIS =
                "screen_off_balanced_window_millis";
        private static final String SCREEN_OFF_BALANCED_INTERVAL_MILLIS =
                "screen_off_balanced_interval_millis";

        /**
         * Default denylist which matches Eddystone and iBeacon payloads.
         */
        private static final String DEFAULT_LOCATION_DENYLIST_ADVERTISING_DATA =
                "⊆0016AAFE/00FFFFFF,⊆00FF4C0002/00FFFFFFFF";

        private static final int DEFAULT_SCAN_QUOTA_COUNT = 5;
        private static final long DEFAULT_SCAN_QUOTA_WINDOW_MILLIS = 30 * SECOND_IN_MILLIS;
        private static final long DEFAULT_SCAN_TIMEOUT_MILLIS = 30 * MINUTE_IN_MILLIS;
        private static final int DEFAULT_SCAN_UPGRADE_DURATION_MILLIS = (int) SECOND_IN_MILLIS * 6;

        public void start() {
            DeviceConfig.addOnPropertiesChangedListener(DeviceConfig.NAMESPACE_BLUETOOTH,
                    BackgroundThread.getExecutor(), this);
            onPropertiesChanged(DeviceConfig.getProperties(DeviceConfig.NAMESPACE_BLUETOOTH));
        }

        @Override
        public void onPropertiesChanged(DeviceConfig.Properties properties) {
            synchronized (mDeviceConfigLock) {
                final String name = properties.getString(LOCATION_DENYLIST_NAME, null);
                mLocationDenylistName = !TextUtils.isEmpty(name)
                        ? Pattern.compile(name).asPredicate()
                        : (v) -> false;
                mLocationDenylistMac = BytesMatcher
                        .decode(properties.getString(LOCATION_DENYLIST_MAC, null));
                mLocationDenylistAdvertisingData = BytesMatcher
                        .decode(properties.getString(LOCATION_DENYLIST_ADVERTISING_DATA,
                                DEFAULT_LOCATION_DENYLIST_ADVERTISING_DATA));
                mScanQuotaCount = properties.getInt(SCAN_QUOTA_COUNT,
                        DEFAULT_SCAN_QUOTA_COUNT);
                mScanQuotaWindowMillis = properties.getLong(SCAN_QUOTA_WINDOW_MILLIS,
                        DEFAULT_SCAN_QUOTA_WINDOW_MILLIS);
                mScanTimeoutMillis = properties.getLong(SCAN_TIMEOUT_MILLIS,
                        DEFAULT_SCAN_TIMEOUT_MILLIS);
                mScanUpgradeDurationMillis = properties.getInt(SCAN_UPGRADE_DURATION_MILLIS,
                        DEFAULT_SCAN_UPGRADE_DURATION_MILLIS);
                mScreenOffLowPowerWindowMillis = properties.getInt(
                        SCREEN_OFF_LOW_POWER_WINDOW_MILLIS,
                        ScanManager.SCAN_MODE_SCREEN_OFF_LOW_POWER_WINDOW_MS);
                mScreenOffLowPowerIntervalMillis = properties.getInt(
                        SCREEN_OFF_LOW_POWER_INTERVAL_MILLIS,
                        ScanManager.SCAN_MODE_SCREEN_OFF_LOW_POWER_INTERVAL_MS);
                mScreenOffBalancedWindowMillis = properties.getInt(
                        SCREEN_OFF_BALANCED_WINDOW_MILLIS,
                        ScanManager.SCAN_MODE_SCREEN_OFF_BALANCED_WINDOW_MS);
                mScreenOffBalancedIntervalMillis = properties.getInt(
                        SCREEN_OFF_BALANCED_INTERVAL_MILLIS,
                        ScanManager.SCAN_MODE_SCREEN_OFF_BALANCED_INTERVAL_MS);
            }
        }
    }

    /**
     *  Obfuscate Bluetooth MAC address into a PII free ID string
     *
     *  @param device Bluetooth device whose MAC address will be obfuscated
     *  @return a byte array that is unique to this MAC address on this device,
     *          or empty byte array when either device is null or obfuscateAddressNative fails
     */
    public byte[] obfuscateAddress(BluetoothDevice device) {
        if (device == null) {
            return new byte[0];
        }
        return obfuscateAddressNative(Utils.getByteAddress(device));
    }

    /**
     * Get dynamic audio buffer size supported type
     *
     * @return support <p>Possible values are
     * {@link BluetoothA2dp#DYNAMIC_BUFFER_SUPPORT_NONE},
     * {@link BluetoothA2dp#DYNAMIC_BUFFER_SUPPORT_A2DP_OFFLOAD},
     * {@link BluetoothA2dp#DYNAMIC_BUFFER_SUPPORT_A2DP_SOFTWARE_ENCODING}.
     */
    public int getDynamicBufferSupport() {
        return mAdapterProperties.getDynamicBufferSupport();
    }

    /**
     * Get dynamic audio buffer size
     *
     * @return BufferConstraints
     */
    public BufferConstraints getBufferConstraints() {
        return mAdapterProperties.getBufferConstraints();
    }

    /**
     * Set dynamic audio buffer size
     *
     * @param codec Audio codec
     * @param value buffer millis
     * @return true if the settings is successful, false otherwise
     */
    public boolean setBufferLengthMillis(int codec, int value) {
        return mAdapterProperties.setBufferLengthMillis(codec, value);
    }

    /**
     *  Get an incremental id of Bluetooth metrics and log
     *
     *  @param device Bluetooth device
     *  @return int of id for Bluetooth metrics and logging, 0 if the device is invalid
     */
    public int getMetricId(BluetoothDevice device) {
        if (device == null) {
            return 0;
        }
        return getMetricIdNative(Utils.getByteAddress(device));
    }

    public CompanionManager getCompanionManager() {
        return mBtCompanionManager;
    }

    /**
     *  Call for the AdapterService receives bond state change
     *
     *  @param device Bluetooth device
     *  @param state bond state
     */
    public void onBondStateChanged(BluetoothDevice device, int state) {
        if (mBtCompanionManager != null) {
            mBtCompanionManager.onBondStateChanged(device, state);
        }
    }

    /**
     * Get audio policy feature support status
     *
     * @param device Bluetooth device to be checked for audio policy support
     * @return int status of the remote support for audio policy feature
     */
    public int isRequestAudioPolicyAsSinkSupported(BluetoothDevice device) {
        if (mHeadsetClientService != null) {
            return mHeadsetClientService.getAudioPolicyRemoteSupported(device);
        } else {
            Log.e(TAG, "No audio transport connected");
            return BluetoothStatusCodes.FEATURE_NOT_CONFIGURED;
        }
    }

    /**
     * Set audio policy for remote device
     *
     * @param device Bluetooth device to be set policy for
     * @return int result status for requestAudioPolicyAsSink API
     */
    public int requestAudioPolicyAsSink(BluetoothDevice device, BluetoothSinkAudioPolicy policies) {
        DeviceProperties deviceProp = mRemoteDevices.getDeviceProperties(device);
        if (deviceProp == null) {
            return BluetoothStatusCodes.ERROR_DEVICE_NOT_BONDED;
        }

        if (mHeadsetClientService != null) {
            if (isRequestAudioPolicyAsSinkSupported(device)
                    != BluetoothStatusCodes.FEATURE_SUPPORTED) {
                throw new UnsupportedOperationException(
                        "Request Audio Policy As Sink not supported");
            }
            deviceProp.setHfAudioPolicyForRemoteAg(policies);
            mHeadsetClientService.setAudioPolicy(device, policies);
            return BluetoothStatusCodes.SUCCESS;
        } else {
            Log.e(TAG, "HeadsetClient not connected");
            return BluetoothStatusCodes.ERROR_PROFILE_NOT_CONNECTED;
        }
    }

    /**
     * Get audio policy for remote device
     *
     * @param device Bluetooth device to be set policy for
     * @return {@link BluetoothSinkAudioPolicy} policy stored for the device
     */
    public BluetoothSinkAudioPolicy getRequestedAudioPolicyAsSink(BluetoothDevice device) {
        DeviceProperties deviceProp = mRemoteDevices.getDeviceProperties(device);
        if (deviceProp == null) {
            return null;
        }

        if (mHeadsetClientService != null) {
            return deviceProp.getHfAudioPolicyForRemoteAg();
        } else {
            Log.e(TAG, "HeadsetClient not connected");
            return null;
        }
    }

    /**
     *  Allow audio low latency
     *
     *  @param allowed true if audio low latency is being allowed
     *  @param device device whose audio low latency will be allowed or disallowed
     *  @return boolean true if audio low latency is successfully allowed or disallowed
     */
    public boolean allowLowLatencyAudio(boolean allowed, BluetoothDevice device) {
        return allowLowLatencyAudioNative(allowed, Utils.getByteAddress(device));
    }

    /**
     * Sets the battery level of the remote device
     */
    public void setBatteryLevel(BluetoothDevice device, int batteryLevel) {
        mRemoteDevices.updateBatteryLevel(device, batteryLevel);
    }

    static native void classInitNative();

    native boolean initNative(boolean startRestricted, boolean isCommonCriteriaMode,
            int configCompareResult, String[] initFlags, boolean isAtvDevice,
            String userDataDirectory);

    native void cleanupNative();

    /*package*/
    native boolean enableNative();

    /*package*/
    native boolean disableNative();

    /*package*/
    native boolean setAdapterPropertyNative(int type, byte[] val);

    /*package*/
    native boolean getAdapterPropertiesNative();

    /*package*/
    native boolean getAdapterPropertyNative(int type);

    /*package*/
    native boolean setAdapterPropertyNative(int type);

    /*package*/
    native boolean setDevicePropertyNative(byte[] address, int type, byte[] val);

    /*package*/
    native boolean getDevicePropertyNative(byte[] address, int type);

    /*package*/
    public native boolean createBondNative(byte[] address, int transport);

    /*package*/
    native boolean createBondOutOfBandNative(byte[] address, int transport,
            OobData p192Data, OobData p256Data);

    /*package*/
    public native boolean removeBondNative(byte[] address);

    /*package*/
    native boolean cancelBondNative(byte[] address);

    /*package*/
    native void generateLocalOobDataNative(int transport);

    /*package*/
    native boolean sdpSearchNative(byte[] address, byte[] uuid);

    /*package*/
    native int getConnectionStateNative(byte[] address);

    private native boolean startDiscoveryNative();

    private native boolean cancelDiscoveryNative();

    private native boolean pinReplyNative(byte[] address, boolean accept, int len, byte[] pin);

    private native boolean sspReplyNative(byte[] address, int type, boolean accept, int passkey);

    /*package*/
    native boolean getRemoteServicesNative(byte[] address, int transport);

    /*package*/
    native boolean getRemoteMasInstancesNative(byte[] address);

    private native int readEnergyInfo();

    /*package*/
    native boolean factoryResetNative();

    private native void alarmFiredNative();

    private native void dumpNative(FileDescriptor fd, String[] arguments);

    private native byte[] dumpMetricsNative();

    private native byte[] obfuscateAddressNative(byte[] address);

    native boolean setBufferLengthMillisNative(int codec, int value);

    private native int getMetricIdNative(byte[] address);

    /*package*/ native int connectSocketNative(
            byte[] address, int type, byte[] uuid, int port, int flag, int callingUid);

    /*package*/ native int createSocketChannelNative(
            int type, String serviceName, byte[] uuid, int port, int flag, int callingUid);

    /*package*/ native void requestMaximumTxDataLengthNative(byte[] address);

    private native boolean allowLowLatencyAudioNative(boolean allowed, byte[] address);

    private native void metadataChangedNative(byte[] address, int key, byte[] value);

    // Returns if this is a mock object. This is currently used in testing so that we may not call
    // System.exit() while finalizing the object. Otherwise GC of mock objects unfortunately ends up
    // calling finalize() which in turn calls System.exit() and the process crashes.
    //
    // Mock this in your testing framework to return true to avoid the mentioned behavior. In
    // production this has no effect.
    public boolean isMock() {
        return false;
    }
}
