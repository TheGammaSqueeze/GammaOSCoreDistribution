/*
 * Copyright (C) 2020 Google Inc.
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

package com.android.car.carlauncher.homescreen.audio;

import android.Manifest;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.IBinder;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.android.car.carlauncher.R;
import com.android.car.carlauncher.homescreen.HomeCardInterface;
import com.android.car.carlauncher.homescreen.audio.telecom.InCallServiceImpl;
import com.android.car.carlauncher.homescreen.ui.CardContent;
import com.android.car.carlauncher.homescreen.ui.CardHeader;
import com.android.car.carlauncher.homescreen.ui.DescriptiveTextWithControlsView;
import com.android.car.telephony.common.CallDetail;
import com.android.car.telephony.common.TelecomUtils;
import com.android.car.telephony.selfmanaged.SelfManagedCallUtil;
import com.android.car.ui.utils.CarUxRestrictionsUtil;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.ArrayUtils;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.time.Clock;
import java.util.concurrent.CompletableFuture;

/**
 * The {@link HomeCardInterface.Model} for ongoing phone calls.
 */
public class InCallModel implements HomeCardInterface.Model, InCallServiceImpl.InCallListener {

    private static final String TAG = "InCallModel";
    private static final boolean DEBUG = false;

    private Context mContext;
    private TelecomManager mTelecomManager;
    private SelfManagedCallUtil mSelfManagedCallUtil;

    private PackageManager mPackageManager;
    private final Clock mElapsedTimeClock;

    private Call mCurrentCall;
    private CompletableFuture<Void> mPhoneNumberInfoFuture;

    private InCallServiceImpl mInCallService;
    private HomeCardInterface.Presenter mPresenter;

    private CardHeader mDefaultDialerCardHeader;
    private CardHeader mCardHeader;
    private CardContent mCardContent;
    private CharSequence mOngoingCallSubtitle;
    private CharSequence mDialingCallSubtitle;
    private DescriptiveTextWithControlsView.Control mMuteButton;
    private DescriptiveTextWithControlsView.Control mEndCallButton;
    private DescriptiveTextWithControlsView.Control mDialpadButton;

    private final ServiceConnection mInCallServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (DEBUG) Log.d(TAG, "onServiceConnected: " + name + ", service: " + service);
            mInCallService = ((InCallServiceImpl.LocalBinder) service).getService();
            mInCallService.addListener(InCallModel.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (DEBUG) Log.d(TAG, "onServiceDisconnected: " + name);
            mInCallService = null;
        }
    };

    private Call.Callback mCallback = new Call.Callback() {
        @Override
        public void onStateChanged(Call call, int state) {
            super.onStateChanged(call, state);
            handleActiveCall(call);
        }
    };

    public InCallModel(Clock elapsedTimeClock) {
        mElapsedTimeClock = elapsedTimeClock;
    }

    @Override
    public void onCreate(Context context) {
        mContext = context;
        mTelecomManager = context.getSystemService(TelecomManager.class);
        CarUxRestrictionsUtil carUxRestrictionsUtil = CarUxRestrictionsUtil.getInstance(context);
        mSelfManagedCallUtil = new SelfManagedCallUtil(mContext, carUxRestrictionsUtil);

        mOngoingCallSubtitle = context.getResources().getString(R.string.ongoing_call_text);
        mDialingCallSubtitle = context.getResources().getString(R.string.dialing_call_text);
        initializeAudioControls();

        mPackageManager = context.getPackageManager();
        mDefaultDialerCardHeader = createCardHeader(mTelecomManager.getDefaultDialerPackage());
        mCardHeader = mDefaultDialerCardHeader;

        Intent intent = new Intent(context, InCallServiceImpl.class);
        intent.setAction(InCallServiceImpl.ACTION_LOCAL_BIND);
        context.getApplicationContext().bindService(intent, mInCallServiceConnection,
                Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy(Context context) {
        if (mInCallService != null) {
            context.getApplicationContext().unbindService(mInCallServiceConnection);
            mInCallService = null;
        }
        if (mPhoneNumberInfoFuture != null) {
            mPhoneNumberInfoFuture.cancel(/* mayInterruptIfRunning= */true);
        }
    }

    @Override
    public void setPresenter(HomeCardInterface.Presenter presenter) {
        mPresenter = presenter;
    }

    @Override
    public CardHeader getCardHeader() {
        return mCardContent == null ? null : mCardHeader;
    }

    @Override
    public CardContent getCardContent() {
        return mCardContent;
    }

    /**
     * Clicking the card opens the default dialer application that fills the role of {@link
     * android.app.role.RoleManager#ROLE_DIALER}. This application will have an appropriate UI to
     * display as one of the requirements to fill this role is to provide an ongoing call UI.
     */
    @Override
    public void onClick(View view) {
        Intent intent = null;
        if (isSelfManagedCall() && mSelfManagedCallUtil.canShowCalInCallView()) {
            Bundle extras = mCurrentCall.getDetails().getExtras();
            ComponentName componentName = extras == null ? null : extras.getParcelable(
                    Intent.EXTRA_COMPONENT_NAME, ComponentName.class);
            if (componentName != null) {
                intent = new Intent();
                intent.setComponent(componentName);
            } else {
                String callingAppPackageName = getCallingAppPackageName();
                if (!TextUtils.isEmpty(callingAppPackageName)) {
                    intent = mPackageManager.getLaunchIntentForPackage(callingAppPackageName);
                }
            }
        } else {
            intent = mPackageManager.getLaunchIntentForPackage(
                    mTelecomManager.getDefaultDialerPackage());
        }

        if (intent != null) {
            // Launch activity in the default app task container: the display area where
            // applications are launched by default.
            // If not set, activity launches in the calling TDA.
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchDisplayId(Display.DEFAULT_DISPLAY);
            mContext.startActivity(intent, options.toBundle());
        } else {
            if (DEBUG) {
                Log.d(TAG, "No launch intent found to show in call ui for call : " + mCurrentCall);
            }
        }
    }

    /**
     * When a {@link Call} is added, notify the {@link HomeCardInterface.Presenter} to update the
     * card to display content on the ongoing phone call.
     */
    @Override
    public void onCallAdded(Call call) {
        if (call != null) {
            handleActiveCall(call);
            call.registerCallback(mCallback);
        }
    }

    /**
     * When a {@link Call} is removed, notify the {@link HomeCardInterface.Presenter} to update the
     * card to remove the content on the no longer ongoing phone call.
     */
    @Override
    public void onCallRemoved(Call call) {
        mCurrentCall = null;
        mCardHeader = null;
        mCardContent = null;
        mPresenter.onModelUpdated(this);
        if (call != null) {
            call.unregisterCallback(mCallback);
        }
    }

    /**
     * When a {@link CallAudioState} is changed, update the model and notify the
     * {@link HomeCardInterface.Presenter} to update the view.
     */
    @Override
    public void onCallAudioStateChanged(CallAudioState audioState) {
        // This is implemented to listen to changes to audio from other sources and update the
        // content accordingly.
        if (updateMuteButtonIconState(audioState)) {
            mPresenter.onModelUpdated(this);
        }
    }

    /**
     * Updates the mute button according to the CallAudioState supplied.
     * returns true if the model was updated and needs to refresh the view
     */
    @VisibleForTesting
    boolean updateMuteButtonIconState(CallAudioState audioState) {
        int[] iconState = mMuteButton.getIcon().getState();
        boolean selectedStateExists = ArrayUtils.contains(iconState,
                android.R.attr.state_selected);

        if (selectedStateExists == audioState.isMuted()) {
            // no need to update since the drawable was already muted
            return false;
        }

        if (audioState.isMuted()) {
            iconState = ArrayUtils.appendInt(iconState,
                    android.R.attr.state_selected);
        } else {
            iconState = ArrayUtils.removeInt(iconState,
                    android.R.attr.state_selected);
        }
        mMuteButton
                .getIcon()
                .setState(iconState);
        return true;
    }

    /**
     * Updates the model's content using the given phone number.
     */
    @VisibleForTesting
    void updateModelWithPhoneNumber(String number, @Call.CallState int callState) {
        String formattedNumber = TelecomUtils.getFormattedNumber(mContext, number);
        mCardContent = createPhoneCardContent(null, formattedNumber, callState);
        mPresenter.onModelUpdated(this);
    }

    /**
     * Updates the model's content using the given {@link TelecomUtils.PhoneNumberInfo}. If there is
     * a corresponding contact, use the contact's name and avatar. If the contact doesn't have an
     * avatar, use an icon with their first initial.
     */
    @VisibleForTesting
    void updateModelWithContact(TelecomUtils.PhoneNumberInfo phoneNumberInfo,
            @Call.CallState int callState) {
        // If call has been removed, return.
        if (mCurrentCall == null) {
            return;
        }

        // Use the caller display name or contact display name from call details first.
        String contactName = mCurrentCall.getDetails().getCallerDisplayName();
        if (TextUtils.isEmpty(contactName)) {
            contactName = mCurrentCall.getDetails().getContactDisplayName();
        }
        String initials = null;
        if (TextUtils.isEmpty(contactName)) {
            contactName = phoneNumberInfo.getDisplayName();
            initials = phoneNumberInfo.getInitials();
        } else {
            initials = TelecomUtils.getInitials(contactName);
        }
        Drawable contactImage = null;
        if (phoneNumberInfo.getAvatarUri() != null) {
            try {
                InputStream inputStream = mContext.getContentResolver().openInputStream(
                        phoneNumberInfo.getAvatarUri());
                contactImage = Drawable.createFromStream(inputStream,
                        phoneNumberInfo.getAvatarUri().toString());
            } catch (FileNotFoundException e) {
                // If no file is found for the contact's avatar URI, the icon will be set to a
                // LetterTile below.
                if (DEBUG) {
                    Log.d(TAG, "Unable to find contact avatar from Uri: "
                            + phoneNumberInfo.getAvatarUri(), e);
                }
            }
        }
        if (contactImage == null) {
            contactImage = TelecomUtils.createLetterTile(mContext, initials, contactName);
        }

        mCardContent = createPhoneCardContent(contactImage, contactName, callState);
        mPresenter.onModelUpdated(this);
    }

    private void handleActiveCall(@NonNull Call call) {
        @Call.CallState int callState = call.getDetails().getState();
        if (callState != Call.STATE_ACTIVE && callState != Call.STATE_DIALING) {
            return;
        }
        mCurrentCall = call;

        CallDetail callDetails = CallDetail.fromTelecomCallDetail(call.getDetails());
        if (callDetails.isSelfManaged()) {
            String packageName = getCallingAppPackageName();
            mCardHeader = createCardHeader(packageName);
        }
        if (mCardHeader == null) {
            // Default to show the default dialer app info
            mCardHeader = mDefaultDialerCardHeader;
        }

        // If the home app does not have permission to read contacts, just display the
        // phone number
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            updateModelWithPhoneNumber(callDetails.getNumber(), callState);
            return;
        }

        if (mPhoneNumberInfoFuture != null) {
            mPhoneNumberInfoFuture.cancel(/* mayInterruptIfRunning= */ true);
        }
        mPhoneNumberInfoFuture = TelecomUtils.getPhoneNumberInfo(mContext,
                        callDetails.getNumber())
                .thenAcceptAsync(x -> updateModelWithContact(x, callState),
                        mContext.getMainExecutor());
    }

    private CardContent createPhoneCardContent(Drawable image, CharSequence title,
            @Call.CallState int callState) {
        switch (callState) {
            case Call.STATE_DIALING:
                return new DescriptiveTextWithControlsView(image, title, mDialingCallSubtitle,
                        mMuteButton, mEndCallButton, mDialpadButton);
            case Call.STATE_ACTIVE:
                return new DescriptiveTextWithControlsView(image, title, mOngoingCallSubtitle,
                        mElapsedTimeClock.millis(), mMuteButton, mEndCallButton, mDialpadButton);
            default:
                if (DEBUG) {
                    Log.d(TAG, "Call State " + callState
                            + " is not currently supported by this model");
                }
                return null;
        }
    }

    private void initializeAudioControls() {
        mMuteButton = new DescriptiveTextWithControlsView.Control(
                mContext.getDrawable(R.drawable.ic_mute_activatable),
                v -> {
                    boolean toggledValue = !v.isSelected();
                    mInCallService.setMuted(toggledValue);
                    v.setSelected(toggledValue);
                });
        mEndCallButton = new DescriptiveTextWithControlsView.Control(
                mContext.getDrawable(R.drawable.ic_call_end_button),
                v -> mCurrentCall.disconnect());
        mDialpadButton = new DescriptiveTextWithControlsView.Control(
                mContext.getDrawable(R.drawable.ic_dialpad), this::onClick);
    }

    @VisibleForTesting
    void updateMuteButtonDrawableState(int[] state) {
        mMuteButton.getIcon().setState(state);
    }

    @VisibleForTesting
    int[] getMuteButtonDrawableState() {
        return mMuteButton.getIcon().getState();
    }

    @Nullable
    private String getCallingAppPackageName() {
        Call.Details callDetails = mCurrentCall == null ? null : mCurrentCall.getDetails();
        PhoneAccountHandle phoneAccountHandle =
                callDetails == null ? null : callDetails.getAccountHandle();
        return phoneAccountHandle == null ? null
                : phoneAccountHandle.getComponentName().getPackageName();
    }

    private boolean isSelfManagedCall() {
        return mCurrentCall != null
                && mCurrentCall.getDetails().hasProperty(Call.Details.PROPERTY_SELF_MANAGED);
    }

    private CardHeader createCardHeader(String packageName) {
        if (!TextUtils.isEmpty(packageName)) {
            try {
                ApplicationInfo applicationInfo = mPackageManager.getApplicationInfo(
                        packageName, PackageManager.ApplicationInfoFlags.of(0));
                Drawable appIcon = mPackageManager.getApplicationIcon(applicationInfo);
                CharSequence appName = mPackageManager.getApplicationLabel(applicationInfo);
                return new CardHeader(appName, appIcon);
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "No such package found " + packageName, e);
            }
        }
        return null;
    }
}
