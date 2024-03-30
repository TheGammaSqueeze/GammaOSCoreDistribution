/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tv.settings.library.system;

import static com.android.tv.settings.library.ManagerUtil.STATE_DEVELOPMENT;
import static com.android.tv.settings.library.overlay.FlavorUtils.X_EXPERIENCE_FLAVORS_MASK;

import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.app.backup.IBackupManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.ServiceManager;
import android.os.UserManager;
import android.provider.Settings;
import android.sysprop.AdbProperties;
import android.view.IWindowManager;
import android.widget.Toast;

import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceControllerState;
import com.android.tv.settings.library.overlay.FlavorUtils;
import com.android.tv.settings.library.system.development.audio.AudioDebug;
import com.android.tv.settings.library.system.development.audio.AudioMetrics;
import com.android.tv.settings.library.util.AbstractPreferenceController;
import com.android.tv.settings.library.util.ResourcesUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

public class DevelopmentState extends PreferenceControllerState {
    private static final String TAG = "DevelopmentSettings";

    private static final String ENABLE_DEVELOPER = "development_settings_enable";
    private static final String ENABLE_ADB = "enable_adb";
    private static final String CLEAR_ADB_KEYS = "clear_adb_keys";
    private static final String ENABLE_TERMINAL = "enable_terminal";
    private static final String KEEP_SCREEN_ON = "keep_screen_on";
    private static final String BT_HCI_SNOOP_LOG = "bt_hci_snoop_log";
    private static final String BTSNOOP_LOG_MODE_PROPERTY = "persist.bluetooth.btsnooplogmode";
    private static final String ENABLE_OEM_UNLOCK = "oem_unlock_enable";
    private static final String HDCP_CHECKING_KEY = "hdcp_checking";
    private static final String HDCP_CHECKING_PROPERTY = "persist.sys.hdcp_checking";
    private static final String LOCAL_BACKUP_PASSWORD = "local_backup_password";
    private static final String BUGREPORT = "bugreport";
    private static final String BUGREPORT_IN_POWER_KEY = "bugreport_in_power";

    private static final String DEBUG_APP_KEY = "debug_app";
    private static final String WAIT_FOR_DEBUGGER_KEY = "wait_for_debugger";
    private static final String MOCK_LOCATION_APP_KEY = "mock_location_app";
    private static final String VERIFY_APPS_OVER_USB_KEY = "verify_apps_over_usb";
    private static final String DEBUG_VIEW_ATTRIBUTES = "debug_view_attributes";
    private static final String FORCE_ALLOW_ON_EXTERNAL_KEY = "force_allow_on_external";
    private static final String STRICT_MODE_KEY = "strict_mode";
    private static final String POINTER_LOCATION_KEY = "pointer_location";
    private static final String SHOW_TOUCHES_KEY = "show_touches";
    private static final String SHOW_SCREEN_UPDATES_KEY = "show_screen_updates";
    private static final String DISABLE_OVERLAYS_KEY = "disable_overlays";
    private static final String SIMULATE_COLOR_SPACE = "simulate_color_space";
    private static final String USB_AUDIO_KEY = "usb_audio";
    private static final String RECORD_AUDIO_KEY = "record_audio";
    private static final String PLAY_RECORDED_AUDIO_KEY = "play_recorded_audio";
    private static final String SAVE_RECORDED_AUDIO_KEY = "save_recorded_audio";
    private static final String TIME_TO_START_READ_KEY = "time_to_start_read";
    private static final String TIME_TO_VALID_AUDIO_KEY = "time_to_valid_audio";
    private static final String EMPTY_AUDIO_DURATION_KEY = "empty_audio_duration";
    private static final String FORCE_MSAA_KEY = "force_msaa";
    private static final String TRACK_FRAME_TIME_KEY = "track_frame_time";
    private static final String SHOW_NON_RECTANGULAR_CLIP_KEY = "show_non_rect_clip";
    private static final String SHOW_HW_SCREEN_UPDATES_KEY = "show_hw_screen_updates";
    private static final String SHOW_HW_LAYERS_UPDATES_KEY = "show_hw_layers_updates";
    private static final String DEBUG_HW_OVERDRAW_KEY = "debug_hw_overdraw";
    private static final String DEBUG_LAYOUT_KEY = "debug_layout";
    private static final String FORCE_RTL_LAYOUT_KEY = "force_rtl_layout_all_locales";
    private static final String WINDOW_BLURS_KEY = "window_blurs";
    private static final String WINDOW_ANIMATION_SCALE_KEY = "window_animation_scale";
    private static final String TRANSITION_ANIMATION_SCALE_KEY = "transition_animation_scale";
    private static final String ANIMATOR_DURATION_SCALE_KEY = "animator_duration_scale";
    private static final String OVERLAY_DISPLAY_DEVICES_KEY = "overlay_display_devices";
    private static final String DEBUG_DEBUGGING_CATEGORY_KEY = "debug_debugging_category";

    private static final String WIFI_DISPLAY_CERTIFICATION_KEY = "wifi_display_certification";
    private static final String WIFI_VERBOSE_LOGGING_KEY = "wifi_verbose_logging";
    private static final String USB_CONFIGURATION_KEY = "select_usb_configuration";
    private static final String MOBILE_DATA_ALWAYS_ON = "mobile_data_always_on";
    private static final String KEY_COLOR_MODE = "color_mode";
    private static final String FORCE_RESIZABLE_KEY = "force_resizable_activities";

    private static final String INACTIVE_APPS_KEY = "inactive_apps";

    private static final String OPENGL_TRACES_KEY = "enable_opengl_traces";

    private static final String IMMEDIATELY_DESTROY_ACTIVITIES_KEY =
            "immediately_destroy_activities";
    private static final String APP_PROCESS_LIMIT_KEY = "app_process_limit";

    private static final String SHOW_ALL_ANRS_KEY = "show_all_anrs";

    private static final String PACKAGE_MIME_TYPE = "application/vnd.android.package-archive";

    private static final String TERMINAL_APP_PACKAGE = "com.android.terminal";

    private static final String KEY_CONVERT_FBE = "convert_to_file_encryption";

    private static final int RESULT_DEBUG_APP = 1000;
    private static final int RESULT_MOCK_LOCATION_APP = 1001;

    private static final String PERSISTENT_DATA_BLOCK_PROP = "ro.frp.pst";

    private static final String DEFAULT_LOG_RING_BUFFER_SIZE_IN_BYTES = "262144"; // 256K

    private static final int[] MOCK_LOCATION_APP_OPS = new int[]{AppOpsManager.OP_MOCK_LOCATION};

    private static final String STATE_SHOWING_DIALOG_KEY = "showing_dialog_key";

    private String mPendingDialogKey;

    private IWindowManager mWindowManager;
    private IBackupManager mBackupManager;
    private DevicePolicyManager mDpm;
    private UserManager mUm;
    private WifiManager mWifiManager;
    private ContentResolver mContentResolver;

    private boolean mLastEnabledState;
    private boolean mHaveDebugSettings;

    private PreferenceCompat mEnableDeveloper;
    private PreferenceCompat mEnableAdb;
    private PreferenceCompat mClearAdbKeys;
    private PreferenceCompat mEnableTerminal;
    private PreferenceCompat mBugreport;
    private PreferenceCompat mKeepScreenOn;
    private PreferenceCompat mDebugDebuggingCategory;
    private PreferenceCompat mBtHciSnoopLog;
    private PreferenceCompat mDebugViewAttributes;
    private PreferenceCompat mForceAllowOnExternal;

    private PreferenceCompat mPassword;
    private String mDebugApp;
    private PreferenceCompat mDebugAppPref;

    private String mMockLocationApp;
    private PreferenceCompat mMockLocationAppPref;

    private PreferenceCompat mWaitForDebugger;
    private PreferenceCompat mVerifyAppsOverUsb;
    private PreferenceCompat mWifiDisplayCertification;
    private PreferenceCompat mWifiVerboseLogging;
    private PreferenceCompat mMobileDataAlwaysOn;

    private PreferenceCompat mStrictMode;
    private PreferenceCompat mPointerLocation;
    private PreferenceCompat mShowTouches;
    private PreferenceCompat mShowScreenUpdates;
    private PreferenceCompat mDisableOverlays;
    private PreferenceCompat mForceMsaa;
    private PreferenceCompat mShowHwScreenUpdates;
    private PreferenceCompat mShowHwLayersUpdates;
    private PreferenceCompat mDebugLayout;
    private PreferenceCompat mForceRtlLayout;
    private PreferenceCompat mWindowBlurs;
    private PreferenceCompat mDebugHwOverdraw;
    private PreferenceCompat mTrackFrameTime;
    private PreferenceCompat mShowNonRectClip;
    private PreferenceCompat mWindowAnimationScale;
    private PreferenceCompat mTransitionAnimationScale;
    private PreferenceCompat mAnimatorDurationScale;
    private PreferenceCompat mOverlayDisplayDevices;
    private PreferenceCompat mOpenGLTraces;
    private PreferenceCompat mSimulateColorSpace;
    private PreferenceCompat mUSBAudio;
    private PreferenceCompat mRecordAudio;
    private PreferenceCompat mPlayRecordedAudio;
    private PreferenceCompat mSaveAudio;
    private PreferenceCompat mTimeToStartRead;
    private PreferenceCompat mTimeToValidAudio;
    private PreferenceCompat mEmptyAudioDuration;
    private PreferenceCompat mImmediatelyDestroyActivities;
    private PreferenceCompat mAppProcessLimit;
    private PreferenceCompat mShowAllANRs;
    private PreferenceCompat mForceResizable;
    private PreferenceCompat mHdcpChecking;

    private boolean mUnavailable;

    private AudioDebug mAudioDebug;
    private final ArrayList<PreferenceCompat> mAllPrefCompats = new ArrayList<>();
    private final ArrayList<PreferenceCompat> mResetSwitchPrefCompats = new ArrayList<>();
    private final HashSet<PreferenceCompat> mDisabledPrefCompats = new HashSet<>();

    public DevelopmentState(Context context,
            UIUpdateCallback callback) {
        super(context, callback);
    }

    @Override
    public void onCreate(Bundle extras) {
        super.onCreate(extras);
        if (extras != null) {
            // Don't show this in onCreate since we might be on the back stack
            mPendingDialogKey = extras.getString(STATE_SHOWING_DIALOG_KEY);
        }

        mWindowManager = IWindowManager.Stub.asInterface(ServiceManager.getService("window"));
        mBackupManager = IBackupManager.Stub.asInterface(
                ServiceManager.getService(Context.BACKUP_SERVICE));
        mDpm = mContext.getSystemService(DevicePolicyManager.class);
        mUm = mContext.getSystemService(UserManager.class);

        mWifiManager = mContext.getSystemService(WifiManager.class);

        mContentResolver = mContext.getContentResolver();

        mAudioDebug = new AudioDebug(mContext,
                (boolean successful) -> onAudioRecorded(successful),
                (AudioMetrics.Data data) -> updateAudioRecordingMetrics(data));
        mEnableDeveloper = mPreferenceCompatManager.getOrCreatePrefCompat(ENABLE_DEVELOPER);
        mEnableAdb = findAndInitSwitchPrefCompat(ENABLE_DEVELOPER);
        mDebugDebuggingCategory = mPreferenceCompatManager.getOrCreatePrefCompat(
                DEBUG_DEBUGGING_CATEGORY_KEY);
        mClearAdbKeys = mPreferenceCompatManager.getOrCreatePrefCompat(CLEAR_ADB_KEYS);
        mKeepScreenOn = mPreferenceCompatManager.getOrCreatePrefCompat(KEEP_SCREEN_ON);
        if (!AdbProperties.secure().orElse(false)) {
            mClearAdbKeys.setVisible(false);
        }
        mAllPrefCompats.add(mClearAdbKeys);
        mEnableTerminal = findAndInitSwitchPrefCompat(ENABLE_TERMINAL);
        if (!isPackageInstalled(mContext, TERMINAL_APP_PACKAGE)) {
            mEnableTerminal.setVisible(false);
        }
        mBugreport = mPreferenceCompatManager.getOrCreatePrefCompat(BUGREPORT);
        if (!showBugReportPreference()) {
            mBugreport.setVisible(false);
        }

        mKeepScreenOn = findAndInitSwitchPrefCompat(KEEP_SCREEN_ON);
        mBtHciSnoopLog = addListPrefCompat(BT_HCI_SNOOP_LOG);
        mDebugViewAttributes = findAndInitSwitchPrefCompat(DEBUG_VIEW_ATTRIBUTES);
        mForceAllowOnExternal = findAndInitSwitchPrefCompat(FORCE_ALLOW_ON_EXTERNAL_KEY);
        if (!mUm.isAdminUser()) {
            disableForUser(mEnableAdb);
            disableForUser(mClearAdbKeys);
            disableForUser(mEnableTerminal);
            disableForUser(mPassword);
        }
        mDebugAppPref = mPreferenceCompatManager.getOrCreatePrefCompat(DEBUG_APP_KEY);
        mAllPrefCompats.add(mDebugAppPref);
        mWaitForDebugger = findAndInitSwitchPrefCompat(WAIT_FOR_DEBUGGER_KEY);
        mMockLocationAppPref = mPreferenceCompatManager.getOrCreatePrefCompat(
                MOCK_LOCATION_APP_KEY);
        mAllPrefCompats.add(mMockLocationAppPref);
        mVerifyAppsOverUsb = findAndInitSwitchPrefCompat(VERIFY_APPS_OVER_USB_KEY);
        if (!showVerifierSetting()) {
            mVerifyAppsOverUsb.setVisible(false);
            mVerifyAppsOverUsb.setEnabled(false);
        }
        mStrictMode = findAndInitSwitchPrefCompat(STRICT_MODE_KEY);
        mPointerLocation = findAndInitSwitchPrefCompat(POINTER_LOCATION_KEY);
        mShowTouches = findAndInitSwitchPrefCompat(SHOW_TOUCHES_KEY);
        mShowScreenUpdates = findAndInitSwitchPrefCompat(SHOW_SCREEN_UPDATES_KEY);
        mDisableOverlays = findAndInitSwitchPrefCompat(DISABLE_OVERLAYS_KEY);
        mForceMsaa = findAndInitSwitchPrefCompat(FORCE_MSAA_KEY);
        mTrackFrameTime = addListPrefCompat(TRACK_FRAME_TIME_KEY);
        mShowNonRectClip = addListPrefCompat(SHOW_NON_RECTANGULAR_CLIP_KEY);
        mShowHwScreenUpdates = findAndInitSwitchPrefCompat(SHOW_HW_SCREEN_UPDATES_KEY);
        mShowHwLayersUpdates = findAndInitSwitchPrefCompat(SHOW_HW_LAYERS_UPDATES_KEY);
        mDebugLayout = findAndInitSwitchPrefCompat(DEBUG_LAYOUT_KEY);
        mForceRtlLayout = findAndInitSwitchPrefCompat(FORCE_RTL_LAYOUT_KEY);
        mWindowBlurs = findAndInitSwitchPrefCompat(WINDOW_BLURS_KEY);
        mDebugHwOverdraw = addListPrefCompat(DEBUG_HW_OVERDRAW_KEY);
        mWifiDisplayCertification = findAndInitSwitchPrefCompat(WIFI_DISPLAY_CERTIFICATION_KEY);
        mWifiVerboseLogging = findAndInitSwitchPrefCompat(WIFI_VERBOSE_LOGGING_KEY);
        mMobileDataAlwaysOn = findAndInitSwitchPrefCompat(MOBILE_DATA_ALWAYS_ON);

        mWindowAnimationScale = addListPrefCompat(WINDOW_ANIMATION_SCALE_KEY);
        mTransitionAnimationScale = addListPrefCompat(TRANSITION_ANIMATION_SCALE_KEY);
        mAnimatorDurationScale = addListPrefCompat(ANIMATOR_DURATION_SCALE_KEY);
        mOverlayDisplayDevices = addListPrefCompat(OVERLAY_DISPLAY_DEVICES_KEY);
        mOpenGLTraces = addListPrefCompat(OPENGL_TRACES_KEY);
        mSimulateColorSpace = addListPrefCompat(SIMULATE_COLOR_SPACE);
        mUSBAudio = findAndInitSwitchPrefCompat(USB_AUDIO_KEY);
        mRecordAudio = findAndInitSwitchPrefCompat(RECORD_AUDIO_KEY);
        mPlayRecordedAudio = mPreferenceCompatManager.getOrCreatePrefCompat(
                PLAY_RECORDED_AUDIO_KEY);
        mPlayRecordedAudio.setVisible(false);
        mSaveAudio = mPreferenceCompatManager.getOrCreatePrefCompat(SAVE_RECORDED_AUDIO_KEY);
        mSaveAudio.setVisible(false);
        mTimeToStartRead = mPreferenceCompatManager.getOrCreatePrefCompat(TIME_TO_START_READ_KEY);
        mTimeToStartRead.setVisible(false);
        mTimeToValidAudio = mPreferenceCompatManager.getOrCreatePrefCompat(TIME_TO_VALID_AUDIO_KEY);
        mTimeToValidAudio.setVisible(false);
        mEmptyAudioDuration = mPreferenceCompatManager.getOrCreatePrefCompat(
                EMPTY_AUDIO_DURATION_KEY);
        mEmptyAudioDuration.setVisible(false);
        mForceResizable = findAndInitSwitchPrefCompat(FORCE_RESIZABLE_KEY);

        mImmediatelyDestroyActivities = findAndInitSwitchPrefCompat(
                IMMEDIATELY_DESTROY_ACTIVITIES_KEY);

        mAppProcessLimit = addListPrefCompat(APP_PROCESS_LIMIT_KEY);

        mShowAllANRs = findAndInitSwitchPrefCompat(SHOW_ALL_ANRS_KEY);

        mHdcpChecking = mPreferenceCompatManager.getOrCreatePrefCompat(HDCP_CHECKING_KEY);
        mAllPrefCompats.add(mHdcpChecking);
        removePreferenceForProduction(mHdcpChecking);
    }


    private PreferenceCompat addListPrefCompat(String prefKey) {
        PreferenceCompat pref = mPreferenceCompatManager.getOrCreatePrefCompat(prefKey);
        pref.setType(PreferenceCompat.TYPE_LIST);
        mAllPrefCompats.add(pref);
        return pref;
    }

    /** Called when audio recording is finished. Updates UI component states. */
    private void onAudioRecorded(boolean successful) {
        mPlayRecordedAudio.setVisible(successful);
        mSaveAudio.setVisible(successful);
        mRecordAudio.setChecked(false);

        if (!successful) {
            Toast errorToast = Toast.makeText(mContext,
                    ResourcesUtil.getString(mContext, "show_audio_recording_failed"),
                    Toast.LENGTH_SHORT);
            errorToast.show();
        }
    }

    /** Updates displayed audio recording metrics */
    private void updateAudioRecordingMetrics(AudioMetrics.Data data) {
        updateAudioRecordingMetric(mTimeToStartRead, data.timeToStartReadMs);
        updateAudioRecordingMetric(mTimeToValidAudio, data.timeToValidAudioMs);
        updateAudioRecordingMetric(mEmptyAudioDuration, data.emptyAudioDurationMs);
    }

    private static void updateAudioRecordingMetric(PreferenceCompat preference, Optional<Long> ts) {
        ts.ifPresent(x -> preference.setVisible(true));
        if (preference.getVisible() == PreferenceCompat.STATUS_ON) {
            preference.setSummary(AudioMetrics.msTimestampToString(ts));
        }
    }

    private boolean removePreferenceForProduction(PreferenceCompat preference) {
        if ("user".equals(Build.TYPE)) {
            preference.setVisible(false);
            return true;
        }
        return false;
    }

    private boolean showVerifierSetting() {
        return Settings.Global.getInt(mContentResolver,
                Settings.Global.PACKAGE_VERIFIER_SETTING_VISIBLE, 1) > 0;
    }

    private PreferenceCompat findAndInitSwitchPrefCompat(String key) {
        PreferenceCompat pref = mPreferenceCompatManager.getOrCreatePrefCompat(key);
        pref.setType(PreferenceCompat.TYPE_SWITCH);
        if (pref == null) {
            throw new IllegalArgumentException("Cannot find preference with key = " + key);
        }
        mAllPrefCompats.add(pref);
        mResetSwitchPrefCompats.add(pref);
        return pref;
    }

    @Override
    public int getStateIdentifier() {
        return STATE_DEVELOPMENT;
    }

    private static boolean isPackageInstalled(Context context, String packageName) {
        try {
            return context.getPackageManager().getPackageInfo(packageName, 0) != null;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void disableForUser(PreferenceCompat pref) {
        if (pref != null) {
            pref.setEnabled(false);
            mDisabledPrefCompats.add(pref);
        }
    }

    private boolean showBugReportPreference() {
        return (FlavorUtils.getFlavor(mContext) & X_EXPERIENCE_FLAVORS_MASK) == 0;
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        return null;
    }
}
