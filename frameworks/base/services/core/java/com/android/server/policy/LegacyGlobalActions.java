/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.server.policy;

import static android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.content.pm.ApplicationInfo;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.Vibrator;
import android.provider.Settings;
import android.service.dreams.DreamService;
import android.service.dreams.IDreamManager;
import android.sysprop.TelephonyProperties;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.ArraySet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.widget.AdapterView;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.graphics.Paint;

import com.android.internal.R;
import com.android.internal.app.AlertController;
import com.android.internal.globalactions.Action;
import com.android.internal.globalactions.ActionsAdapter;
import com.android.internal.globalactions.ActionsDialog;
import com.android.internal.globalactions.LongPressAction;
import com.android.internal.globalactions.SinglePressAction;
import com.android.internal.globalactions.ToggleAction;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.util.EmergencyAffordanceManager;
import com.android.internal.widget.LockPatternUtils;
import com.android.server.policy.WindowManagerPolicy.WindowManagerFuncs;

import java.util.ArrayList;
import java.util.List;
import android.app.AlertDialog;
import android.os.Looper;
import android.app.Activity;
import android.widget.Toast;
import android.os.BatteryManager;
import android.widget.TextView;
import android.view.LayoutInflater;
import android.view.View;
import android.os.Build;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;


/**
 * Helper to show the global actions dialog.  Each item is an {@link Action} that
 * may show depending on whether the keyguard is showing, and whether the device
 * is provisioned.
 */
class LegacyGlobalActions implements DialogInterface.OnDismissListener, DialogInterface.OnClickListener  {

    private static final String TAG = "LegacyGlobalActions";

    private static final boolean SHOW_SILENT_TOGGLE = true;

    /* Valid settings for global actions keys.
     * see config.xml config_globalActionList */
    private static final String GLOBAL_ACTION_KEY_POWER = "power";
    private static final String GLOBAL_ACTION_KEY_AIRPLANE = "airplane";
    private static final String GLOBAL_ACTION_KEY_BUGREPORT = "bugreport";
    private static final String GLOBAL_ACTION_KEY_SILENT = "silent";
    private static final String GLOBAL_ACTION_KEY_USERS = "users";
    private static final String GLOBAL_ACTION_KEY_SETTINGS = "settings";
    private static final String GLOBAL_ACTION_KEY_LOCKDOWN = "lockdown";
    private static final String GLOBAL_ACTION_KEY_VOICEASSIST = "voiceassist";
    private static final String GLOBAL_ACTION_KEY_ASSIST = "assist";
    private static final String GLOBAL_ACTION_KEY_RESTART = "restart";

    private final Context mContext;
    private final WindowManagerFuncs mWindowManagerFuncs;
    private final AudioManager mAudioManager;
    private final IDreamManager mDreamManager;
    private final Runnable mOnDismiss;

    private ArrayList<Action> mItems;
    private ActionsDialog mDialog;

    private Action mSilentModeAction;
    private ToggleAction mAirplaneModeOn;

    private ActionsAdapter mAdapter;

    private boolean mKeyguardShowing = false;
    private boolean mDeviceProvisioned = false;
    private ToggleAction.State mAirplaneState = ToggleAction.State.Off;
    private boolean mIsWaitingForEcmExit = false;
    private final boolean mHasTelephony;
    private boolean mHasVibrator;
    private final boolean mShowSilentToggle;
    private final EmergencyAffordanceManager mEmergencyAffordanceManager;

    private View headerView; // Member variable to hold the header view

    /**
     * @param context everything needs a context :(
     */
    public LegacyGlobalActions(Context context, WindowManagerFuncs windowManagerFuncs,
            Runnable onDismiss) {
        mContext = context;
        mWindowManagerFuncs = windowManagerFuncs;
        mOnDismiss = onDismiss;
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mDreamManager = IDreamManager.Stub.asInterface(
                ServiceManager.getService(DreamService.DREAM_SERVICE));

        // receive broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(TelephonyManager.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED);
        // By default CLOSE_SYSTEM_DIALOGS broadcast is sent only for current user, which is user
        // 10 on devices with headless system user enabled.
        // In order to receive the broadcast, register the broadcast receiver with UserHandle.ALL.
        context.registerReceiverAsUser(mBroadcastReceiver, UserHandle.ALL, filter, null, null,
                Context.RECEIVER_EXPORTED);

        mHasTelephony =
                context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);

        // get notified of phone state changes
        TelephonyManager telephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_SERVICE_STATE);
        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON), true,
                mAirplaneModeObserver);
        Vibrator vibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mHasVibrator = vibrator != null && vibrator.hasVibrator();

        mShowSilentToggle = SHOW_SILENT_TOGGLE && !mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_useFixedVolume);

        mEmergencyAffordanceManager = new EmergencyAffordanceManager(context);
    }

    /**
     * Show the global actions dialog (creating if necessary)
     * @param keyguardShowing True if keyguard is showing
     */
    public void showDialog(boolean keyguardShowing, boolean isDeviceProvisioned) {
        mKeyguardShowing = keyguardShowing;
        mDeviceProvisioned = isDeviceProvisioned;
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
            // Show delayed, so that the dismiss of the previous dialog completes
            mHandler.sendEmptyMessage(MESSAGE_SHOW);
        } else {
            handleShow();
        }
    }

    private void awakenIfNecessary() {
        if (mDreamManager != null) {
            try {
                if (mDreamManager.isDreaming()) {
                    mDreamManager.awaken();
                }
            } catch (RemoteException e) {
                // we tried
            }
        }
    }

    private void handleShow() {
        awakenIfNecessary();
        mDialog = createDialog();
        prepareDialog();

        // If we only have 1 item and it's a simple press action, just do this action.
        if (mAdapter.getCount() == 1
                && mAdapter.getItem(0) instanceof SinglePressAction
                && !(mAdapter.getItem(0) instanceof LongPressAction)) {
            ((SinglePressAction) mAdapter.getItem(0)).onPress();
        } else {
            if (mDialog != null) {
                WindowManager.LayoutParams attrs = mDialog.getWindow().getAttributes();
                attrs.setTitle("LegacyGlobalActions");
                mDialog.getWindow().setAttributes(attrs);
                mDialog.show();
                mDialog.getWindow().getDecorView().setSystemUiVisibility(
                        View.STATUS_BAR_DISABLE_EXPAND);
            }
        }
    }

    /**
     * Create the global actions dialog.
     * @return A new dialog.
     */
private ActionsDialog createDialog() {

    LayoutInflater inflater = LayoutInflater.from(mContext);
    headerView = inflater.inflate(R.layout.header_battery_status, null, false);
    updateBatteryStatus(); // Initial update

    // Simple toggle style if there's no vibrator, otherwise use a tri-state
    if (!mHasVibrator) {
        mSilentModeAction = new SilentModeToggleAction();
    } else {
        mSilentModeAction = new SilentModeTriStateAction(mContext, mAudioManager, mHandler);
    }
    mAirplaneModeOn = new ToggleAction(
            R.drawable.ic_lock_airplane_mode,
            R.drawable.ic_lock_airplane_mode_off,
            R.string.global_actions_toggle_airplane_mode,
            R.string.global_actions_airplane_mode_on_status,
            R.string.global_actions_airplane_mode_off_status) {

        @Override
        public void onToggle(boolean on) {
            if (mHasTelephony && TelephonyProperties.in_ecm_mode().orElse(false)) {
                mIsWaitingForEcmExit = true;
                // Launch ECM exit dialog
                Intent ecmDialogIntent =
                        new Intent(TelephonyManager.ACTION_SHOW_NOTICE_ECM_BLOCK_OTHERS, null);
                ecmDialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(ecmDialogIntent);
            } else {
                changeAirplaneModeSystemSetting(on);
            }
        }

        @Override
        protected void changeStateFromPress(boolean buttonOn) {
            if (!mHasTelephony) return;

            // In ECM mode airplane state cannot be changed
            if (!TelephonyProperties.in_ecm_mode().orElse(false)) {
                mState = buttonOn ? State.TurningOn : State.TurningOff;
                mAirplaneState = mState;
            }
        }

        @Override
        public boolean showDuringKeyguard() {
            return true;
        }

        @Override
        public boolean showBeforeProvisioning() {
            return false;
        }
    };
    onAirplaneModeChanged();

    mItems = new ArrayList<Action>();
    String[] defaultActions = mContext.getResources().getStringArray(
            com.android.internal.R.array.config_globalActionsList);

    ArraySet<String> addedKeys = new ArraySet<String>();
    for (int i = 0; i < defaultActions.length; i++) {
        String actionKey = defaultActions[i];
        if (addedKeys.contains(actionKey)) {
            // If we already have added this, don't add it again.
            continue;
        }
        if (GLOBAL_ACTION_KEY_POWER.equals(actionKey)) {
            mItems.add(new PowerAction(mContext, mWindowManagerFuncs));
        } else if (GLOBAL_ACTION_KEY_AIRPLANE.equals(actionKey)) {
            mItems.add(mAirplaneModeOn);
        } else if (GLOBAL_ACTION_KEY_BUGREPORT.equals(actionKey)) {
            if (Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.BUGREPORT_IN_POWER_MENU, 0) != 0 && isCurrentUserOwner()) {
                mItems.add(new BugReportAction());
            }
        } else if (GLOBAL_ACTION_KEY_SILENT.equals(actionKey)) {
            if (mShowSilentToggle) {
                mItems.add(mSilentModeAction);
            }
        } else if (GLOBAL_ACTION_KEY_USERS.equals(actionKey)) {
            if (SystemProperties.getBoolean("fw.power_user_switcher", false)) {
                addUsersToMenu(mItems);
            }
        } else if (GLOBAL_ACTION_KEY_VOICEASSIST.equals(actionKey)) {
            mItems.add(getVoiceAssistAction());
        } else if (GLOBAL_ACTION_KEY_ASSIST.equals(actionKey)) {
            mItems.add(getAssistAction());
        } else if (GLOBAL_ACTION_KEY_RESTART.equals(actionKey)) {
            mItems.add(new RestartAction(mContext, mWindowManagerFuncs));
        } else {
            Log.e(TAG, "Invalid global action key " + actionKey);
        }
        // Add here so we don't add more than one.
        addedKeys.add(actionKey);
    }

    if (mEmergencyAffordanceManager.needsEmergencyAffordance()) {
        mItems.add(getEmergencyAction());
    }

    // GammaOS - Add our own shortcuts
    mItems.add(getSettingsAction());
    mItems.add(getBrightnessOptionsAction());
    mItems.add(getHomeAction());
    mItems.add(getControllerOptionsAction());
    mItems.add(getUSBOptionsAction());
    mItems.add(getPerformanceOptionsAction());
    mItems.add(getKillForegroundAppAction());
    mItems.add(getKillBackgroundAppsAction());
    mItems.add(getKillAllAppsAction());

    // Override ActionsAdapter's getView method to set text color to white
    mAdapter = new ActionsAdapter(mContext, mItems,
            () -> mDeviceProvisioned, () -> mKeyguardShowing) {

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // Get the default view for the item
            View view = super.getView(position, convertView, parent);

            // Traverse view hierarchy to find the TextView
            if (view instanceof ViewGroup) {
                findAndSetTextColorWhite((ViewGroup) view);
            }

            return view;
        }

        private void findAndSetTextColorWhite(ViewGroup viewGroup) {
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                if (child instanceof TextView) {
                    ((TextView) child).setTextColor(Color.WHITE); // Set text color to white
                } else if (child instanceof ViewGroup) {
                    findAndSetTextColorWhite((ViewGroup) child); // Recursively search for TextView
                }
            }
        }
    };

    AlertController.AlertParams params = new AlertController.AlertParams(mContext);
    params.mAdapter = mAdapter;
    params.mOnClickListener = this;
    params.mForceInverseBackground = true;
    params.mCustomTitleView = headerView; // Set custom header

    ActionsDialog dialog = new ActionsDialog(mContext, params);
    dialog.setCanceledOnTouchOutside(false); // Handled by the custom class.

    dialog.getListView().setItemsCanFocus(true);
    dialog.getListView().setLongClickable(true);
    dialog.getListView().setOnItemLongClickListener(
            new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> parent, View view, int position,
                        long id) {
                    final Action action = mAdapter.getItem(position);
                    if (action instanceof LongPressAction) {
                        return ((LongPressAction) action).onLongPress();
                    }
                    return false;
                }
    });
    dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
    // Don't acquire soft keyboard focus, to avoid destroying state when capturing bug reports
    dialog.getWindow().setFlags(FLAG_ALT_FOCUSABLE_IM, FLAG_ALT_FOCUSABLE_IM);

    // Define rounded corners
    float[] outerRadii = new float[] {16, 16, 16, 16, 16, 16, 16, 16}; // Set corner radius
    RoundRectShape roundedRect = new RoundRectShape(outerRadii, null, null);
    ShapeDrawable shapeDrawable = new ShapeDrawable(roundedRect);
    shapeDrawable.getPaint().setColor(Color.parseColor("#FA333333")); // Transparent black
    shapeDrawable.getPaint().setStyle(Paint.Style.FILL);

    // Apply the rounded background
    dialog.getWindow().setBackgroundDrawable(shapeDrawable);

    dialog.setOnDismissListener(this);

    return dialog;
}

    private class BugReportAction extends SinglePressAction implements LongPressAction {

        public BugReportAction() {
            super(com.android.internal.R.drawable.ic_lock_bugreport, R.string.bugreport_title);
        }

        @Override
        public void onPress() {
            // don't actually trigger the bugreport if we are running stability
            // tests via monkey
            if (ActivityManager.isUserAMonkey()) {
                return;
            }
            // Add a little delay before executing, to give the
            // dialog a chance to go away before it takes a
            // screenshot.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Take an "interactive" bugreport.
                        MetricsLogger.action(mContext,
                                MetricsEvent.ACTION_BUGREPORT_FROM_POWER_MENU_INTERACTIVE);
                        ActivityManager.getService().requestInteractiveBugReport();
                    } catch (RemoteException e) {
                    }
                }
            }, 500);
        }

        @Override
        public boolean onLongPress() {
            // don't actually trigger the bugreport if we are running stability
            // tests via monkey
            if (ActivityManager.isUserAMonkey()) {
                return false;
            }
            try {
                // Take a "full" bugreport.
                MetricsLogger.action(mContext, MetricsEvent.ACTION_BUGREPORT_FROM_POWER_MENU_FULL);
                ActivityManager.getService().requestFullBugReport();
            } catch (RemoteException e) {
            }
            return false;
        }

        @Override
        public boolean showDuringKeyguard() {
            return true;
        }

        @Override
        public boolean showBeforeProvisioning() {
            return false;
        }

        @Override
        public String getStatus() {
            return mContext.getString(
                    com.android.internal.R.string.bugreport_status,
                    Build.VERSION.RELEASE_OR_CODENAME,
                    Build.ID);
        }
    }

    private Action getSettingsAction() {
        return new SinglePressAction(com.android.internal.R.drawable.ic_settings,
                R.string.global_action_settings) {

            @Override
            public void onPress() {
                Intent intent = new Intent(Settings.ACTION_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                mContext.startActivity(intent);
            }

            @Override
            public boolean showDuringKeyguard() {
                return true;
            }

            @Override
            public boolean showBeforeProvisioning() {
                return true;
            }
        };
    }

    private Action getHomeAction() {
        return new SinglePressAction(com.android.internal.R.drawable.ic_menu,
                R.string.accessibility_system_action_home_label) {

            @Override
            public void onPress() {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_HOME);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                mContext.startActivity(intent);
            }

            @Override
            public boolean showDuringKeyguard() {
                return true;
            }

            @Override
            public boolean showBeforeProvisioning() {
                return true;
            }
        };
    }


    private Action getPerformanceOptionsAction() {
        return new SinglePressAction(R.drawable.ic_menu,
                R.string.gammaos_performance_mode) {

            public void onPress() {
                Intent intent = new Intent(mContext, PerformanceOptionsActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

                // Dismiss the dialog completely before launching the new activity
                if (mDialog != null && mDialog.isShowing()) {
                    mDialog.dismiss();
                    mDialog = null; // Clear the reference to help garbage collection
                }

                mContext.startActivity(intent);

                // Check if mContext is an instance of Activity and then call finish()
                if (mContext instanceof Activity) {
                    ((Activity) mContext).finish();
                }
            }

            public boolean onLongPress() {
                return false;
            }

            @Override
            public boolean showDuringKeyguard() {
                return true;
            }

            @Override
            public boolean showBeforeProvisioning() {
                return true;
            }

        };
    }

    private Action getControllerOptionsAction() {
        return new SinglePressAction(R.drawable.ic_menu,
                R.string.gammaos_controller_options) {

            public void onPress() {
                Intent intent = new Intent(mContext, ControllerOptionsActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

                // Dismiss the dialog completely before launching the new activity
                if (mDialog != null && mDialog.isShowing()) {
                    mDialog.dismiss();
                    mDialog = null; // Clear the reference to help garbage collection
                }

                mContext.startActivity(intent);

                // Check if mContext is an instance of Activity and then call finish()
                if (mContext instanceof Activity) {
                    ((Activity) mContext).finish();
                }
            }

            public boolean onLongPress() {
                return false;
            }

            @Override
            public boolean showDuringKeyguard() {
                return true;
            }

            @Override
            public boolean showBeforeProvisioning() {
                return true;
            }

        };
    }

    private Action getUSBOptionsAction() {
        return new SinglePressAction(R.drawable.ic_usb_48dp,
                R.string.gammaos_usb_options) {

            public void onPress() {
                Intent intent = new Intent(mContext, USBOptionsActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

                // Dismiss the dialog completely before launching the new activity
                if (mDialog != null && mDialog.isShowing()) {
                    mDialog.dismiss();
                    mDialog = null; // Clear the reference to help garbage collection
                }

                mContext.startActivity(intent);

                // Check if mContext is an instance of Activity and then call finish()
                if (mContext instanceof Activity) {
                    ((Activity) mContext).finish();
                }
            }

            public boolean onLongPress() {
                return false;
            }

            @Override
            public boolean showDuringKeyguard() {
                return true;
            }

            @Override
            public boolean showBeforeProvisioning() {
                return true;
            }

        };
    }

    private Action getBrightnessOptionsAction() {
        return new SinglePressAction(com.android.internal.R.drawable.ic_menu, // Use an appropriate icon for brightness
                R.string.gammaos_brightness_settings) { // Define this string in your resources

            public void onPress() {
                Intent intent = new Intent(mContext, BrightnessControlActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

                // Dismiss the dialog completely before launching the new activity
                if (mDialog != null && mDialog.isShowing()) {
                    mDialog.dismiss();
                    mDialog = null; // Clear the reference to help garbage collection
                }

                mContext.startActivity(intent);

                // Check if mContext is an instance of Activity and then call finish()
                if (mContext instanceof Activity) {
                    ((Activity) mContext).finish();
                }
            }

            public boolean onLongPress() {
                return false;
            }

            @Override
            public boolean showDuringKeyguard() {
                return true;
            }

            @Override
            public boolean showBeforeProvisioning() {
                return true;
            }
        };
    }

    private Action getKillForegroundAppAction() {
        return new SinglePressAction(R.drawable.ic_menu, R.string.gammaos_kill_app) {

            @Override
            public void onPress() {
                ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
                List<ActivityManager.RunningTaskInfo> taskInfo = am.getRunningTasks(1); // Get the top (foreground) task

                if (taskInfo != null && !taskInfo.isEmpty()) {
                    String foregroundProcess = taskInfo.get(0).topActivity.getPackageName(); // Get the package name of the top activity
                    try {
                        am.forceStopPackage(foregroundProcess);
                        Toast.makeText(mContext, "Foreground app killed: " + foregroundProcess, Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(mContext, "Failed to kill app due to: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(mContext, "No foreground app found", Toast.LENGTH_SHORT).show();
                }
            }

            public boolean onLongPress() {
                return false;
            }

            @Override
            public boolean showDuringKeyguard() {
                return true;
            }

            @Override
            public boolean showBeforeProvisioning() {
                return true;
            }
        };
    }

    private Action getKillBackgroundAppsAction() {
        return new SinglePressAction(R.drawable.ic_menu, R.string.gammaos_kill_all_background_apps) {

            @Override
            public void onPress() {
                ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
                PackageManager pm = mContext.getPackageManager();

                // Get the foreground app
                List<ActivityManager.RunningTaskInfo> taskInfo = am.getRunningTasks(1);
                String foregroundProcess = null;
                if (taskInfo != null && !taskInfo.isEmpty()) {
                    foregroundProcess = taskInfo.get(0).topActivity.getPackageName(); // Get foreground app package name
                }

                List<ActivityManager.RunningAppProcessInfo> runningAppProcesses = am.getRunningAppProcesses();

                if (runningAppProcesses != null && !runningAppProcesses.isEmpty()) {
                    for (ActivityManager.RunningAppProcessInfo processInfo : runningAppProcesses) {
                        String packageName = processInfo.processName;

                        // Skip the foreground app and system apps
                        try {
                            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);

                            if (!packageName.equals(foregroundProcess) && (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                                // Force stop the background app
                                am.forceStopPackage(packageName);
                            }
                        } catch (PackageManager.NameNotFoundException e) {
                            // Ignore any packages that cannot be found
                        } catch (Exception e) {
                            Toast.makeText(mContext, "Failed to kill app: " + packageName + " due to: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                    Toast.makeText(mContext, "All background apps have been killed", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(mContext, "No running background apps found", Toast.LENGTH_SHORT).show();
                }
            }

            public boolean onLongPress() {
                return false;
            }

            @Override
            public boolean showDuringKeyguard() {
                return true;
            }

            @Override
            public boolean showBeforeProvisioning() {
                return true;
            }
        };
    }

    private Action getKillAllAppsAction() {
        return new SinglePressAction(R.drawable.ic_menu, R.string.gammaos_kill_all_apps) {

            @Override
            public void onPress() {
                ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
                PackageManager pm = mContext.getPackageManager();
                List<ActivityManager.RunningAppProcessInfo> runningAppProcesses = am.getRunningAppProcesses();

                if (runningAppProcesses != null && !runningAppProcesses.isEmpty()) {
                    for (ActivityManager.RunningAppProcessInfo processInfo : runningAppProcesses) {
                        String packageName = processInfo.processName;

                        try {
                            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);

                            // Skip system apps and the current package
                            if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                                // Force stop the package
                                am.forceStopPackage(packageName);
                            }
                        } catch (PackageManager.NameNotFoundException e) {
                            // Ignore any packages that cannot be found
                        } catch (Exception e) {
                            Toast.makeText(mContext, "Failed to kill app: " + packageName + " due to: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                    Toast.makeText(mContext, "All apps have been killed", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(mContext, "No running apps found", Toast.LENGTH_SHORT).show();
                }
            }

            public boolean onLongPress() {
                return false;
            }

            @Override
            public boolean showDuringKeyguard() {
                return true;
            }

            @Override
            public boolean showBeforeProvisioning() {
                return true;
            }
        };
    }

    private Action getEmergencyAction() {
        return new SinglePressAction(com.android.internal.R.drawable.emergency_icon,
                R.string.global_action_emergency) {
            @Override
            public void onPress() {
                mEmergencyAffordanceManager.performEmergencyCall();
            }

            @Override
            public boolean showDuringKeyguard() {
                return true;
            }

            @Override
            public boolean showBeforeProvisioning() {
                return true;
            }
        };
    }

    private Action getAssistAction() {
        return new SinglePressAction(com.android.internal.R.drawable.ic_action_assist_focused,
                R.string.global_action_assist) {
            @Override
            public void onPress() {
                Intent intent = new Intent(Intent.ACTION_ASSIST);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                mContext.startActivity(intent);
            }

            @Override
            public boolean showDuringKeyguard() {
                return true;
            }

            @Override
            public boolean showBeforeProvisioning() {
                return true;
            }
        };
    }

    private Action getVoiceAssistAction() {
        return new SinglePressAction(com.android.internal.R.drawable.ic_voice_search,
                R.string.global_action_voice_assist) {
            @Override
            public void onPress() {
                Intent intent = new Intent(Intent.ACTION_VOICE_ASSIST);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                mContext.startActivity(intent);
            }

            @Override
            public boolean showDuringKeyguard() {
                return true;
            }

            @Override
            public boolean showBeforeProvisioning() {
                return true;
            }
        };
    }

    private Action getLockdownAction() {
        return new SinglePressAction(com.android.internal.R.drawable.ic_lock_lock,
                R.string.global_action_lockdown) {

            @Override
            public void onPress() {
                new LockPatternUtils(mContext).requireCredentialEntry(UserHandle.USER_ALL);
                try {
                    WindowManagerGlobal.getWindowManagerService().lockNow(null);
                } catch (RemoteException e) {
                    Log.e(TAG, "Error while trying to lock device.", e);
                }
            }

            @Override
            public boolean showDuringKeyguard() {
                return true;
            }

            @Override
            public boolean showBeforeProvisioning() {
                return false;
            }
        };
    }

    private UserInfo getCurrentUser() {
        try {
            return ActivityManager.getService().getCurrentUser();
        } catch (RemoteException re) {
            return null;
        }
    }

    private boolean isCurrentUserOwner() {
        UserInfo currentUser = getCurrentUser();
        return currentUser == null || currentUser.isPrimary();
    }

    private void addUsersToMenu(ArrayList<Action> items) {
        UserManager um = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
        if (um.isUserSwitcherEnabled()) {
            List<UserInfo> users = um.getUsers();
            UserInfo currentUser = getCurrentUser();
            for (final UserInfo user : users) {
                if (user.supportsSwitchToByUser()) {
                    boolean isCurrentUser = currentUser == null
                            ? user.id == 0 : (currentUser.id == user.id);
                    Drawable icon = user.iconPath != null ? Drawable.createFromPath(user.iconPath)
                            : null;
                    SinglePressAction switchToUser = new SinglePressAction(
                            com.android.internal.R.drawable.ic_menu_cc, icon,
                            (user.name != null ? user.name : "Primary")
                            + (isCurrentUser ? " \u2714" : "")) {
                        @Override
                        public void onPress() {
                            try {
                                ActivityManager.getService().switchUser(user.id);
                            } catch (RemoteException re) {
                                Log.e(TAG, "Couldn't switch user " + re);
                            }
                        }

                        @Override
                        public boolean showDuringKeyguard() {
                            return true;
                        }

                        @Override
                        public boolean showBeforeProvisioning() {
                            return false;
                        }
                    };
                    items.add(switchToUser);
                }
            }
        }
    }

    private void prepareDialog() {
        refreshSilentMode();
        mAirplaneModeOn.updateState(mAirplaneState);
        mAdapter.notifyDataSetChanged();
        mDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        if (mShowSilentToggle) {
            IntentFilter filter = new IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION);
            mContext.registerReceiver(mRingerModeReceiver, filter);
        }
    }

    private void refreshSilentMode() {
        if (!mHasVibrator) {
            final boolean silentModeOn =
                    mAudioManager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL;
            ((ToggleAction)mSilentModeAction).updateState(
                    silentModeOn ? ToggleAction.State.On : ToggleAction.State.Off);
        }
    }

/** {@inheritDoc} */
@Override
public void onDismiss(DialogInterface dialog) {
    if (mOnDismiss != null) {
        mOnDismiss.run();
    }
    if (mShowSilentToggle) {
        try {
            mContext.unregisterReceiver(mRingerModeReceiver);
        } catch (IllegalArgumentException ie) {
            // This will catch the exception if the receiver was already unregistered or not registered.
            Log.w(TAG, "Attempted to unregister the ringer mode receiver that was not registered", ie);
        }
    }
    try {
        mContext.unregisterReceiver(batteryInfoReceiver); // Unregister the battery info receiver
    } catch (IllegalArgumentException ie) {
        // This will catch the exception if the receiver was already unregistered or not registered.
        Log.w(TAG, "Attempted to unregister the battery info receiver that was not registered", ie);
    }
}

    /** {@inheritDoc} */
    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (!(mAdapter.getItem(which) instanceof SilentModeTriStateAction)) {
            dialog.dismiss();
        }
        mAdapter.getItem(which).onPress();
    }

    // note: the scheme below made more sense when we were planning on having
    // 8 different things in the global actions dialog.  seems overkill with
    // only 3 items now, but may as well keep this flexible approach so it will
    // be easy should someone decide at the last minute to include something
    // else, such as 'enable wifi', or 'enable bluetooth'

    private class SilentModeToggleAction extends ToggleAction {
        public SilentModeToggleAction() {
            super(R.drawable.ic_audio_vol_mute,
                    R.drawable.ic_audio_vol,
                    R.string.global_action_toggle_silent_mode,
                    R.string.global_action_silent_mode_on_status,
                    R.string.global_action_silent_mode_off_status);
        }

        @Override
        public void onToggle(boolean on) {
            if (on) {
                mAudioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            } else {
                mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            }
        }

        @Override
        public boolean showDuringKeyguard() {
            return true;
        }

        @Override
        public boolean showBeforeProvisioning() {
            return false;
        }
    }

    private static class SilentModeTriStateAction implements Action, View.OnClickListener {

        private final int[] ITEM_IDS = { R.id.option1, R.id.option2, R.id.option3 };

        private final AudioManager mAudioManager;
        private final Handler mHandler;
        private final Context mContext;

        SilentModeTriStateAction(Context context, AudioManager audioManager, Handler handler) {
            mAudioManager = audioManager;
            mHandler = handler;
            mContext = context;
        }

        private int ringerModeToIndex(int ringerMode) {
            // They just happen to coincide
            return ringerMode;
        }

        private int indexToRingerMode(int index) {
            // They just happen to coincide
            return index;
        }

        @Override
        public CharSequence getLabelForAccessibility(Context context) {
            return null;
        }

        @Override
        public View create(Context context, View convertView, ViewGroup parent,
                LayoutInflater inflater) {
            View v = inflater.inflate(R.layout.global_actions_silent_mode, parent, false);

            int selectedIndex = ringerModeToIndex(mAudioManager.getRingerMode());
            for (int i = 0; i < 3; i++) {
                View itemView = v.findViewById(ITEM_IDS[i]);
                itemView.setSelected(selectedIndex == i);
                // Set up click handler
                itemView.setTag(i);
                itemView.setOnClickListener(this);
            }
            return v;
        }

        @Override
        public void onPress() {
        }

        @Override
        public boolean showDuringKeyguard() {
            return true;
        }

        @Override
        public boolean showBeforeProvisioning() {
            return false;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        void willCreate() {
        }

        @Override
        public void onClick(View v) {
            if (!(v.getTag() instanceof Integer)) return;

            int index = (Integer) v.getTag();
            mAudioManager.setRingerMode(indexToRingerMode(index));
            mHandler.sendEmptyMessageDelayed(MESSAGE_DISMISS, DIALOG_DISMISS_DELAY);
        }
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)
                    || Intent.ACTION_SCREEN_OFF.equals(action)) {
                String reason = intent.getStringExtra(PhoneWindowManager.SYSTEM_DIALOG_REASON_KEY);
                if (!PhoneWindowManager.SYSTEM_DIALOG_REASON_GLOBAL_ACTIONS.equals(reason)) {
                    mHandler.sendEmptyMessage(MESSAGE_DISMISS);
                }
            } else if (TelephonyManager.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED.equals(action)) {
                // Airplane mode can be changed after ECM exits if airplane toggle button
                // is pressed during ECM mode
                if (!(intent.getBooleanExtra(TelephonyManager.EXTRA_PHONE_IN_ECM_STATE, false))
                        && mIsWaitingForEcmExit) {
                    mIsWaitingForEcmExit = false;
                    changeAirplaneModeSystemSetting(true);
                }
            }
        }
    };

    PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            if (!mHasTelephony) return;
            final boolean inAirplaneMode = serviceState.getState() == ServiceState.STATE_POWER_OFF;
            mAirplaneState = inAirplaneMode ? ToggleAction.State.On : ToggleAction.State.Off;
            mAirplaneModeOn.updateState(mAirplaneState);
            mAdapter.notifyDataSetChanged();
        }
    };

    private BroadcastReceiver mRingerModeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(AudioManager.RINGER_MODE_CHANGED_ACTION)) {
                mHandler.sendEmptyMessage(MESSAGE_REFRESH);
            }
        }
    };

    private ContentObserver mAirplaneModeObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            onAirplaneModeChanged();
        }
    };

    private static final int MESSAGE_DISMISS = 0;
    private static final int MESSAGE_REFRESH = 1;
    private static final int MESSAGE_SHOW = 2;
    private static final int DIALOG_DISMISS_DELAY = 300; // ms

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_DISMISS:
                if (mDialog != null) {
                    mDialog.dismiss();
                    mDialog = null;
                }
                break;
            case MESSAGE_REFRESH:
                refreshSilentMode();
                mAdapter.notifyDataSetChanged();
                break;
            case MESSAGE_SHOW:
                handleShow();
                break;
            }
        }
    };

    private void onAirplaneModeChanged() {
        // Let the service state callbacks handle the state.
        if (mHasTelephony) return;

        boolean airplaneModeOn = Settings.Global.getInt(
                mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON,
                0) == 1;
        mAirplaneState = airplaneModeOn ? ToggleAction.State.On : ToggleAction.State.Off;
        mAirplaneModeOn.updateState(mAirplaneState);
    }

    /**
     * Change the airplane mode system setting
     */
    private void changeAirplaneModeSystemSetting(boolean on) {
        Settings.Global.putInt(
                mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON,
                on ? 1 : 0);
        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra("state", on);
        mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
        if (!mHasTelephony) {
            mAirplaneState = on ? ToggleAction.State.On : ToggleAction.State.Off;
        }
    }


    private void updateBatteryStatus() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        // Log to confirm registration is happening
        Log.d(TAG, "Registering battery status receiver");
        mContext.registerReceiver(batteryInfoReceiver, filter);
    }


    private BroadcastReceiver batteryInfoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            final int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            final int batteryPct = (level >= 0 && scale > 0) ? (int) ((level / (float) scale) * 100) : -1;

            if (batteryPct >= 0) {
                // Post task to Handler to ensure it runs on the main thread
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        TextView batteryText = headerView.findViewById(R.id.battery_percentage);
                        if (batteryText != null) {
                            batteryText.setText(batteryPct + "%");
                            batteryText.setTextColor(Color.WHITE);
                        } else {
                            Log.e(TAG, "Battery TextView not found");
                        }
                    }
                });
            } else {
                Log.e(TAG, "Invalid battery level or scale");
            }
        }
    };


}
