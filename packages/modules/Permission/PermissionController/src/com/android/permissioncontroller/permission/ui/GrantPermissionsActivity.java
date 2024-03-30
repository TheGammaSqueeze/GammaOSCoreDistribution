/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.view.WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS;

import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.CANCELED;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.DENIED;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.DENIED_DO_NOT_ASK_AGAIN;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_ALWAYS;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_FOREGROUND_ONLY;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_ONE_TIME;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.LINKED_TO_SETTINGS;
import static com.android.permissioncontroller.permission.utils.Utils.getRequestMessage;

import android.app.KeyguardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Process;
import android.text.Annotation;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import com.android.modules.utils.build.SdkLevel;
import com.android.permissioncontroller.DeviceUtils;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.ui.auto.GrantPermissionsAutoViewHandler;
import com.android.permissioncontroller.permission.ui.model.v31.GrantPermissionsViewModel;
import com.android.permissioncontroller.permission.ui.model.v31.GrantPermissionsViewModel.RequestInfo;
import com.android.permissioncontroller.permission.ui.model.v31.GrantPermissionsViewModelFactory;
import com.android.permissioncontroller.permission.ui.wear.GrantPermissionsWearViewHandler;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.permission.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * An activity which displays runtime permission prompts on behalf of an app.
 */
public class GrantPermissionsActivity extends SettingsActivity
        implements GrantPermissionsViewHandler.ResultListener {

    private static final String LOG_TAG = "GrantPermissionsActivit";

    private static final String KEY_SESSION_ID = GrantPermissionsActivity.class.getName()
            + "_REQUEST_ID";
    public static final String ANNOTATION_ID = "link";

    public static final int NEXT_BUTTON = 11;
    public static final int ALLOW_BUTTON = 0;
    public static final int ALLOW_ALWAYS_BUTTON = 1; // Used in auto
    public static final int ALLOW_FOREGROUND_BUTTON = 2;
    public static final int DENY_BUTTON = 3;
    public static final int DENY_AND_DONT_ASK_AGAIN_BUTTON = 4;
    public static final int ALLOW_ONE_TIME_BUTTON = 5;
    public static final int NO_UPGRADE_BUTTON = 6;
    public static final int NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON = 7;
    public static final int NO_UPGRADE_OT_BUTTON = 8; // one-time
    public static final int NO_UPGRADE_OT_AND_DONT_ASK_AGAIN_BUTTON = 9; // one-time
    public static final int LINK_TO_SETTINGS = 10;

    public static final int NEXT_LOCATION_DIALOG = 6;
    public static final int LOCATION_ACCURACY_LAYOUT = 0;
    public static final int FINE_RADIO_BUTTON = 1;
    public static final int COARSE_RADIO_BUTTON = 2;
    public static final int DIALOG_WITH_BOTH_LOCATIONS = 3;
    public static final int DIALOG_WITH_FINE_LOCATION_ONLY = 4;
    public static final int DIALOG_WITH_COARSE_LOCATION_ONLY = 5;

    public static final Map<String, Integer> PERMISSION_TO_BIT_SHIFT =
            new HashMap<String, Integer>() {{
                put(ACCESS_COARSE_LOCATION, 0);
                put(ACCESS_FINE_LOCATION, 1);
            }};

    private static final int APP_PERMISSION_REQUEST_CODE = 1;

    /**
     * A map of the currently shown GrantPermissionsActivity for this user, per package and task ID
     */
    @GuardedBy("sCurrentGrantRequests")
    private static final Map<Pair<String, Integer>, GrantPermissionsActivity>
            sCurrentGrantRequests = new HashMap<>();

    /** Unique Id of a request */
    private long mSessionId;

    /**
     * The permission group that was showing, before a new permission request came in on top of an
     * existing request
     */
    private String mPreMergeShownGroupName;

    /** The current list of permissions requested, across all current requests for this app */
    private String[] mRequestedPermissions;
    /** The list of permissions requested in the intent to this activity*/
    private String[] mOriginalRequestedPermissions;

    private boolean[] mButtonVisibilities;
    private boolean[] mLocationVisibilities;
    private List<RequestInfo> mRequestInfos = new ArrayList<>();
    private GrantPermissionsViewHandler mViewHandler;
    private GrantPermissionsViewModel mViewModel;
    /**
     * A list of other GrantPermissionActivities for the same package which passed their list of
     * permissions to this one. They need to be informed when this activity finishes.
     */
    private List<GrantPermissionsActivity> mFollowerActivities = new ArrayList<>();
    /** Whether this activity has asked another GrantPermissionsActivity to show on its behalf */
    private boolean mDelegated;
    /** The set result code, or MAX_VALUE if it hasn't been set yet */
    private int mResultCode = Integer.MAX_VALUE;
    /** Package that shall have permissions granted */
    private String mTargetPackage;
    /** A key representing this activity, defined by the target package and task ID */
    private Pair<String, Integer> mKey;
    private int mTotalRequests = 0;
    private int mCurrentRequestIdx = 0;
    private float mOriginalDimAmount;
    private View mRootView;

    @Override
    public void onCreate(Bundle icicle) {
        if (DeviceUtils.isAuto(this)) {
            setTheme(R.style.GrantPermissions_Car_FilterTouches);
        }
        super.onCreate(icicle);

        if (icicle == null) {
            mSessionId = new Random().nextLong();
        } else {
            mSessionId = icicle.getLong(KEY_SESSION_ID);
        }

        getWindow().addSystemFlags(SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);

        mRequestedPermissions = getIntent()
                .getStringArrayExtra(PackageManager.EXTRA_REQUEST_PERMISSIONS_NAMES);

        if (PackageManager.ACTION_REQUEST_PERMISSIONS_FOR_OTHER.equals(getIntent().getAction())) {
            mTargetPackage = getIntent().getStringExtra(Intent.EXTRA_PACKAGE_NAME);
            if (mTargetPackage == null) {
                Log.e(LOG_TAG, "null EXTRA_PACKAGE_NAME. Must be set for "
                        + "REQUEST_PERMISSIONS_FOR_OTHER activity");
                finishAfterTransition();
                return;
            }
        } else if (getCallingPackage() == null) {
            Log.e(LOG_TAG, "null callingPackageName. Please use \"RequestPermission\" to "
                    + "request permissions");
            finishAfterTransition();
            return;
        } else {
            // Cache this as this can only read on onCreate, not later.
            mTargetPackage = getCallingPackage();

            // If this app is below the android T targetSdk, filter out the POST_NOTIFICATIONS
            // permission, if present
            mRequestedPermissions = GrantPermissionsViewModel.Companion
                    .filterNotificationPermissionIfNeededSync(
                            mTargetPackage, mRequestedPermissions);
        }


        if (mRequestedPermissions == null || mRequestedPermissions.length == 0) {
            setResultAndFinish();
            return;
        }
        mOriginalRequestedPermissions = mRequestedPermissions;

        synchronized (sCurrentGrantRequests) {
            mKey = new Pair<>(mTargetPackage, getTaskId());
            if (!sCurrentGrantRequests.containsKey(mKey)) {
                sCurrentGrantRequests.put(mKey, this);
                finishSystemStartedDialogsOnOtherTasksLocked();
            } else if (getCallingPackage() == null) {
                // The trampoline doesn't require results. Delegate, and finish.
                sCurrentGrantRequests.get(mKey).onNewFollowerActivity(null,
                        mRequestedPermissions);
                finishAfterTransition();
                return;
            } else {
                mDelegated = true;
                sCurrentGrantRequests.get(mKey).onNewFollowerActivity(this,
                        mRequestedPermissions);
            }
        }

        setFinishOnTouchOutside(false);

        setTitle(R.string.permission_request_title);

        if (DeviceUtils.isTelevision(this)) {
            mViewHandler = new com.android.permissioncontroller.permission.ui.television
                    .GrantPermissionsViewHandlerImpl(this,
                    mTargetPackage).setResultListener(this);
        } else if (DeviceUtils.isWear(this)) {
            mViewHandler = new GrantPermissionsWearViewHandler(this).setResultListener(this);
        } else if (DeviceUtils.isAuto(this)) {
            mViewHandler = new GrantPermissionsAutoViewHandler(this, mTargetPackage)
                    .setResultListener(this);
        } else {
            mViewHandler = new com.android.permissioncontroller.permission.ui.handheld
                    .GrantPermissionsViewHandlerImpl(this, mTargetPackage,
                    Process.myUserHandle()).setResultListener(this);
        }

        GrantPermissionsViewModelFactory factory = new GrantPermissionsViewModelFactory(
                getApplication(), mTargetPackage, mRequestedPermissions, mSessionId, icicle);
        mViewModel = factory.create(GrantPermissionsViewModel.class);
        mViewModel.getRequestInfosLiveData().observe(this, this::onRequestInfoLoad);

        mRootView = mViewHandler.createView();
        mRootView.setVisibility(View.GONE);
        setContentView(mRootView);
        Window window = getWindow();
        WindowManager.LayoutParams layoutParams = window.getAttributes();
        mOriginalDimAmount = layoutParams.dimAmount;
        mViewHandler.updateWindowAttributes(layoutParams);
        window.setAttributes(layoutParams);

        if (SdkLevel.isAtLeastS() && getResources().getBoolean(R.bool.config_useWindowBlur)) {
            java.util.function.Consumer<Boolean> blurEnabledListener = enabled -> {
                mViewHandler.onBlurEnabledChanged(window, enabled);
            };
            mRootView.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    window.getWindowManager().addCrossWindowBlurEnabledListener(
                            blurEnabledListener);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    window.getWindowManager().removeCrossWindowBlurEnabledListener(
                            blurEnabledListener);
                }
            });
        }
        // Restore UI state after lifecycle events. This has to be before we show the first request,
        // as the UI behaves differently for updates and initial creations.
        if (icicle != null) {
            mViewHandler.loadInstanceState(icicle);
        } else {
            // Do not show screen dim until data is loaded
            window.setDimAmount(0f);
        }
    }

    /**
     * A new GrantPermissionsActivity has opened for this same package. Merge its requested
     * permissions with the original ones set in the intent, and recalculate the grant states.
     * @param follower The activity requesting permissions, which needs to be informed upon this
     *                 activity finishing
     * @param newPermissions The new permissions requested in the activity
     */
    private void onNewFollowerActivity(GrantPermissionsActivity follower, String[] newPermissions) {
        if (follower != null) {
            // Ensure the list of follower activities is a stack
            mFollowerActivities.add(0, follower);
        }

        boolean isShowingGroup = mRootView != null && mRootView.getVisibility() == View.VISIBLE;
        List<RequestInfo> currentGroups = mViewModel.getRequestInfosLiveData().getValue();
        if (mPreMergeShownGroupName == null && isShowingGroup
                && currentGroups != null && !currentGroups.isEmpty()) {
            mPreMergeShownGroupName = currentGroups.get(0).getGroupName();
        }
        boolean newPermission = false;
        ArrayList<String> currentPermissions =
                new ArrayList<>(Arrays.asList(mRequestedPermissions));
        for (String newPerm: newPermissions) {
            if (!currentPermissions.contains(newPerm)) {
                currentPermissions.add(newPerm);
                newPermission = true;
            }
        }

        if (!newPermission) {
            return;
        }

        mRequestedPermissions = currentPermissions.toArray(new String[0]);
        Bundle oldState = new Bundle();
        mViewModel.getRequestInfosLiveData().removeObservers(this);
        mViewModel.saveInstanceState(oldState);
        GrantPermissionsViewModelFactory factory = new GrantPermissionsViewModelFactory(
                getApplication(), mTargetPackage, mRequestedPermissions,
                mSessionId, oldState);
        mViewModel = factory.create(GrantPermissionsViewModel.class);
        mViewModel.getRequestInfosLiveData().observe(this, this::onRequestInfoLoad);
    }

    /**
     * When the leader activity this activity delegated to finishes, finish this activity
     * @param resultCode the result of the leader
     */
    private void onLeaderActivityFinished(int resultCode) {
        setResultIfNeeded(resultCode);
        finishAfterTransition();
    }

    private void onRequestInfoLoad(List<RequestInfo> requests) {
        if (!mViewModel.getRequestInfosLiveData().isInitialized() || isResultSet() || mDelegated) {
            return;
        } else if (requests == null) {
            finishAfterTransition();
            return;
        } else if (requests.isEmpty()) {
            setResultAndFinish();
            return;
        }

        if (mRequestInfos == null) {
            mTotalRequests = requests.size();
        }
        mRequestInfos = requests;

        // If we were already showing a group, and then another request came in with more groups,
        // keep the current group showing until the user makes a decision
        if (mPreMergeShownGroupName != null) {
            return;
        }

        showNextRequest();
    }

    private void showNextRequest() {
        if (mRequestInfos == null || mRequestInfos.isEmpty()) {
            return;
        }

        RequestInfo info = mRequestInfos.get(0);

        if (info.getSendToSettingsImmediately()) {
            mViewModel.sendDirectlyToSettings(this, info.getGroupName());
            return;
        }

        CharSequence appLabel = KotlinUtils.INSTANCE.getPackageLabel(getApplication(),
                mTargetPackage, Process.myUserHandle());

        Icon icon = null;
        int messageId = 0;
        switch(info.getMessage()) {
            case FG_MESSAGE:
                messageId = Utils.getRequest(info.getGroupName());
                break;
            case FG_FINE_LOCATION_MESSAGE:
                messageId = R.string.permgrouprequest_fineupgrade;
                break;
            case FG_COARSE_LOCATION_MESSAGE:
                messageId = R.string.permgrouprequest_coarselocation;
                break;
            case BG_MESSAGE:
                messageId = Utils.getBackgroundRequest(info.getGroupName());
                break;
            case UPGRADE_MESSAGE:
                messageId = Utils.getUpgradeRequest(info.getGroupName());
                break;
            case STORAGE_SUPERGROUP_MESSAGE_Q_TO_S:
                icon = Icon.createWithResource(getPackageName(), R.drawable.perm_group_storage);
                messageId = R.string.permgrouprequest_storage_q_to_s;
                break;
            case STORAGE_SUPERGROUP_MESSAGE_PRE_Q:
                icon = Icon.createWithResource(getPackageName(), R.drawable.perm_group_storage);
                messageId = R.string.permgrouprequest_storage_pre_q;
                break;
        }

        CharSequence message = getRequestMessage(appLabel, mTargetPackage,
                info.getGroupName(), this, messageId);

        int detailMessageId = 0;
        switch(info.getDetailMessage()) {
            case FG_MESSAGE:
                detailMessageId = Utils.getRequestDetail(info.getGroupName());
                break;
            case BG_MESSAGE:
                detailMessageId = Utils.getBackgroundRequestDetail(info.getGroupName());
                break;
            case UPGRADE_MESSAGE:
                detailMessageId = Utils.getUpgradeRequestDetail(info.getGroupName());
        }

        Spanned detailMessage = null;
        if (detailMessageId != 0) {
            detailMessage =
                    new SpannableString(getText(detailMessageId));
            Annotation[] annotations = detailMessage.getSpans(
                    0, detailMessage.length(), Annotation.class);
            int numAnnotations = annotations.length;
            for (int i = 0; i < numAnnotations; i++) {
                Annotation annotation = annotations[i];
                if (annotation.getValue().equals(ANNOTATION_ID)) {
                    int start = detailMessage.getSpanStart(annotation);
                    int end = detailMessage.getSpanEnd(annotation);
                    ClickableSpan clickableSpan = getLinkToAppPermissions(info);
                    SpannableString spannableString =
                            new SpannableString(detailMessage);
                    spannableString.setSpan(clickableSpan, start, end, 0);
                    detailMessage = spannableString;
                    break;
                }
            }
        }

        try {
            icon = icon != null ? icon : Icon.createWithResource(
                    info.getGroupInfo().getPackageName(),
                    info.getGroupInfo().getIcon());
        } catch (Resources.NotFoundException e) {
            Log.e(LOG_TAG, "Cannot load icon for group" + info.getGroupName(), e);
        }

        boolean showingNewGroup = message == null || !message.equals(getTitle());

        // Set the permission message as the title so it can be announced. Skip on Wear
        // because the dialog title is already announced, as is the default selection which
        // is a text view containing the title.
        if (!DeviceUtils.isWear(this)) {
            setTitle(message);
        }

        ArrayList<Integer> idxs = new ArrayList<>();
        mButtonVisibilities = new boolean[info.getButtonVisibilities().size()];
        for (int i = 0; i < info.getButtonVisibilities().size(); i++) {
            mButtonVisibilities[i] = info.getButtonVisibilities().get(i);
            if (mButtonVisibilities[i]) {
                idxs.add(i);
            }
        }

        mLocationVisibilities = new boolean[info.getLocationVisibilities().size()];
        for (int i = 0; i < info.getLocationVisibilities().size(); i++) {
            mLocationVisibilities[i] = info.getLocationVisibilities().get(i);
        }

        mViewHandler.updateUi(info.getGroupName(), mTotalRequests, mCurrentRequestIdx, icon,
                message, detailMessage, mButtonVisibilities, mLocationVisibilities);
        if (showingNewGroup) {
            mCurrentRequestIdx++;
        }

        getWindow().setDimAmount(mOriginalDimAmount);
        if (mRootView.getVisibility() == View.GONE) {
            InputMethodManager manager = getSystemService(InputMethodManager.class);
            manager.hideSoftInputFromWindow(mRootView.getWindowToken(), 0);
            mRootView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        View rootView = getWindow().getDecorView();
        if (rootView.getTop() != 0) {
            // We are animating the top view, need to compensate for that in motion events.
            ev.setLocation(ev.getX(), ev.getY() - rootView.getTop());
        }
        final int x = (int) ev.getX();
        final int y = (int) ev.getY();
        if ((x < 0) || (y < 0) || (x > (rootView.getWidth())) || (y > (rootView.getHeight()))) {
            finishAfterTransition();
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mViewHandler == null || mViewModel == null) {
            return;
        }

        mViewHandler.saveInstanceState(outState);
        mViewModel.saveInstanceState(outState);

        outState.putLong(KEY_SESSION_ID, mSessionId);
    }

    private ClickableSpan getLinkToAppPermissions(RequestInfo info) {
        return new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                logGrantPermissionActivityButtons(info.getGroupName(), null, LINKED_TO_SETTINGS);
                mViewModel.sendToSettingsFromLink(GrantPermissionsActivity.this,
                        info.getGroupName());
            }
        };
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Consumer<Intent> callback = mViewModel.getActivityResultCallback();

        if (requestCode == APP_PERMISSION_REQUEST_CODE && callback != null) {
            callback.accept(data);
            mViewModel.setActivityResultCallback(null);
        }
    }

    @Override
    public void onPermissionGrantResult(String name,
            @GrantPermissionsViewHandler.Result int result) {
        if (checkKgm(name, null, result)) {
            return;
        }

        if (name.equals(mPreMergeShownGroupName)) {
            mPreMergeShownGroupName = null;
        }

        logGrantPermissionActivityButtons(name, null, result);
        mViewModel.onPermissionGrantResult(name, null, result);
        showNextRequest();
        if (result == CANCELED) {
            setResultAndFinish();
        }
    }

    @Override
    public void onPermissionGrantResult(String name, List<String> affectedForegroundPermissions,
            @GrantPermissionsViewHandler.Result int result) {
        if (checkKgm(name, affectedForegroundPermissions, result)) {
            return;
        }

        if (name.equals(mPreMergeShownGroupName)) {
            mPreMergeShownGroupName = null;
        }

        logGrantPermissionActivityButtons(name, affectedForegroundPermissions, result);
        mViewModel.onPermissionGrantResult(name, affectedForegroundPermissions, result);
        showNextRequest();
        if (result == CANCELED) {
            setResultAndFinish();
        }
    }

    @Override
    public void onBackPressed() {
        mViewHandler.onBackPressed();
    }

    @Override
    public void finishAfterTransition() {
        if (!setResultIfNeeded(RESULT_CANCELED)) {
            return;
        }
        if (mViewModel != null) {
            mViewModel.autoGrantNotify();
        }
        super.finishAfterTransition();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (!isResultSet()) {
            removeActivityFromMap();
        }
    }

    /**
     * Remove this activity from the map of activities
     */
    private void removeActivityFromMap() {
        synchronized (sCurrentGrantRequests) {
            GrantPermissionsActivity top = sCurrentGrantRequests.get(mKey);
            if (this.equals(top)) {
                sCurrentGrantRequests.remove(mKey);
            } else if (top != null) {
                top.mFollowerActivities.remove(this);
            }
        }
        for (GrantPermissionsActivity activity: mFollowerActivities) {
            activity.onLeaderActivityFinished(mResultCode);
        }
        mFollowerActivities.clear();
    }

    private boolean checkKgm(String name, List<String> affectedForegroundPermissions,
            @GrantPermissionsViewHandler.Result int result) {
        if (result == GRANTED_ALWAYS || result == GRANTED_FOREGROUND_ONLY
                || result == DENIED_DO_NOT_ASK_AGAIN) {
            KeyguardManager kgm = getSystemService(KeyguardManager.class);

            if (kgm != null && kgm.isDeviceLocked()) {
                kgm.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                    @Override
                    public void onDismissError() {
                        Log.e(LOG_TAG, "Cannot dismiss keyguard perm=" + name
                                + " result=" + result);
                    }

                    @Override
                    public void onDismissCancelled() {
                        // do nothing (i.e. stay at the current permission group)
                    }

                    @Override
                    public void onDismissSucceeded() {
                        // Now the keyguard is dismissed, hence the device is not locked
                        // anymore
                        onPermissionGrantResult(name, affectedForegroundPermissions, result);
                    }
                });
                return true;
            }
        }
        return false;
    }

    private boolean setResultIfNeeded(int resultCode) {
        if (!isResultSet()) {
            String[] oldRequestedPermissions = mRequestedPermissions;
            removeActivityFromMap();
            // If a new merge request came in before we managed to remove this activity from the
            // map, then cancel the result set for now.
            if (!Arrays.equals(oldRequestedPermissions, mRequestedPermissions)) {
                return false;
            }
            mResultCode = resultCode;
            if (mViewModel != null) {
                mViewModel.logRequestedPermissionGroups();
            }
            // Only include the originally requested permissions in the result
            Intent result = new Intent(PackageManager.ACTION_REQUEST_PERMISSIONS);
            String[] resultPermissions = mOriginalRequestedPermissions != null
                    ? mOriginalRequestedPermissions : new String[0];
            int[] grantResults = new int[resultPermissions.length];

            if ((mDelegated || (mViewModel != null && mViewModel.shouldReturnPermissionState()))
                    && mTargetPackage != null) {
                PackageManager pm = getPackageManager();
                for (int i = 0; i < resultPermissions.length; i++) {
                    grantResults[i] = pm.checkPermission(resultPermissions[i], mTargetPackage);
                }
            } else {
                grantResults = new int[0];
                resultPermissions = new String[0];
            }

            result.putExtra(PackageManager.EXTRA_REQUEST_PERMISSIONS_NAMES, resultPermissions);
            result.putExtra(PackageManager.EXTRA_REQUEST_PERMISSIONS_RESULTS, grantResults);
            result.putExtra(Intent.EXTRA_PACKAGE_NAME, mTargetPackage);
            setResult(resultCode, result);
        }
        return true;
    }

    private void setResultAndFinish() {
        if (setResultIfNeeded(RESULT_OK)) {
            finishAfterTransition();
        }
    }

    private void logGrantPermissionActivityButtons(String permissionGroupName,
            List<String> affectedForegroundPermissions, int grantResult) {
        int clickedButton = 0;
        int presentedButtons = getButtonState();
        switch (grantResult) {
            case GRANTED_ALWAYS:
                clickedButton = 1 << ALLOW_BUTTON;
                break;
            case GRANTED_FOREGROUND_ONLY:
                clickedButton = 1 << ALLOW_FOREGROUND_BUTTON;
                break;
            case DENIED:
                if (mButtonVisibilities != null) {
                    if (mButtonVisibilities[NO_UPGRADE_BUTTON]) {
                        clickedButton = 1 << NO_UPGRADE_BUTTON;
                    } else if (mButtonVisibilities[NO_UPGRADE_OT_BUTTON]) {
                        clickedButton = 1 << NO_UPGRADE_OT_BUTTON;
                    } else if (mButtonVisibilities[DENY_BUTTON]) {
                        clickedButton = 1 << DENY_BUTTON;
                    }
                }
                break;
            case DENIED_DO_NOT_ASK_AGAIN:
                if (mButtonVisibilities != null) {
                    if (mButtonVisibilities[NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON]) {
                        clickedButton = 1 << NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON;
                    } else if (mButtonVisibilities[NO_UPGRADE_OT_AND_DONT_ASK_AGAIN_BUTTON]) {
                        clickedButton = 1 << NO_UPGRADE_OT_AND_DONT_ASK_AGAIN_BUTTON;
                    } else if (mButtonVisibilities[DENY_AND_DONT_ASK_AGAIN_BUTTON]) {
                        clickedButton = 1 << DENY_AND_DONT_ASK_AGAIN_BUTTON;
                    }
                }
                break;
            case GRANTED_ONE_TIME:
                clickedButton = 1 << ALLOW_ONE_TIME_BUTTON;
                break;
            case LINKED_TO_SETTINGS:
                clickedButton = 1 << LINK_TO_SETTINGS;
            case CANCELED:
                // fall through
            default:
                break;
        }

        int selectedPrecision = 0;
        if (affectedForegroundPermissions != null) {
            for (Map.Entry<String, Integer> entry : PERMISSION_TO_BIT_SHIFT.entrySet()) {
                if (affectedForegroundPermissions.contains(entry.getKey())) {
                    selectedPrecision |= 1 << entry.getValue();
                }
            }
        }

        mViewModel.logClickedButtons(permissionGroupName, selectedPrecision, clickedButton,
                presentedButtons);
    }

    private int getButtonState() {
        if (mButtonVisibilities == null) {
            return 0;
        }
        int buttonState = 0;
        for (int i = NEXT_BUTTON - 1; i >= 0; i--) {
            buttonState *= 2;
            if (mButtonVisibilities[i]) {
                buttonState++;
            }
        }
        return buttonState;
    }

    private boolean isResultSet() {
        return mResultCode != Integer.MAX_VALUE;
    }

    /**
     * If there is another system-shown dialog on another task, that is not being relied upon by an
     * app-defined dialogs, these other dialogs should be finished.
     */
    @GuardedBy("sCurrentGrantRequests")
    private void finishSystemStartedDialogsOnOtherTasksLocked() {
        for (Pair<String, Integer> key : sCurrentGrantRequests.keySet()) {
            if (key.first.equals(mTargetPackage) && key.second != getTaskId()) {
                GrantPermissionsActivity other = sCurrentGrantRequests.get(key);
                if (other.getIntent().getAction()
                        .equals(PackageManager.ACTION_REQUEST_PERMISSIONS_FOR_OTHER)
                        && other.mFollowerActivities.isEmpty()) {
                    other.finish();
                }
            }
        }
    }
}
