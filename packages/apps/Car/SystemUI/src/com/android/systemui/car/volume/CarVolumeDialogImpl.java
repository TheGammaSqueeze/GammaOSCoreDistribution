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

package com.android.systemui.car.volume;

import static android.car.media.CarAudioManager.AUDIO_FEATURE_VOLUME_GROUP_MUTING;
import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.annotation.DrawableRes;
import android.annotation.Nullable;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.car.Car;
import android.car.media.CarAudioManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.util.Xml;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.systemui.R;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.plugins.VolumeDialog;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.volume.Events;
import com.android.systemui.volume.SystemUIInterpolators;
import com.android.systemui.volume.VolumeDialogImpl;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Car version of the volume dialog.
 *
 * Methods ending in "H" must be called on the (ui) handler.
 */
public class CarVolumeDialogImpl
        implements VolumeDialog, ConfigurationController.ConfigurationListener {

    private static final String TAG = "CarVolumeDialog";
    private static final boolean DEBUG = Build.IS_USERDEBUG || Build.IS_ENG;

    private static final String XML_TAG_VOLUME_ITEMS = "carVolumeItems";
    private static final String XML_TAG_VOLUME_ITEM = "item";
    private static final int LISTVIEW_ANIMATION_DURATION_IN_MILLIS = 250;
    private static final int DISMISS_DELAY_IN_MILLIS = 50;
    private static final int ARROW_FADE_IN_START_DELAY_IN_MILLIS = 100;

    private final Context mContext;
    private final H mHandler = new H();
    // All the volume items.
    private final SparseArray<VolumeItem> mVolumeItems = new SparseArray<>();
    // Available volume items in car audio manager.
    private final List<VolumeItem> mAvailableVolumeItems = new ArrayList<>();
    // Volume items in the RecyclerView.
    private final List<CarVolumeItem> mCarVolumeLineItems = new ArrayList<>();
    private final KeyguardManager mKeyguard;
    private final int mNormalTimeout;
    private final int mHoveringTimeout;
    private final int mExpNormalTimeout;
    private final int mExpHoveringTimeout;
    private final CarServiceProvider mCarServiceProvider;
    private final ConfigurationController mConfigurationController;

    private Window mWindow;
    private CustomDialog mDialog;
    private RecyclerView mListView;
    private CarVolumeItemAdapter mVolumeItemsAdapter;
    private CarAudioManager mCarAudioManager;
    private boolean mHovering;
    private int mCurrentlyDisplayingGroupId;
    private int mPreviouslyDisplayingGroupId;
    private boolean mDismissing;
    private boolean mExpanded;
    private View mExpandIcon;

    private final CarAudioManager.CarVolumeCallback mVolumeChangeCallback =
            new CarAudioManager.CarVolumeCallback() {
                @Override
                public void onGroupVolumeChanged(int zoneId, int groupId, int flags) {
                    updateVolumeAndMute(zoneId, groupId, flags);
                }

                @Override
                public void onMasterMuteChanged(int zoneId, int flags) {
                    // ignored
                }

                @Override
                public void onGroupMuteChanged(int zoneId, int groupId, int flags) {
                    updateVolumeAndMute(zoneId, groupId, flags);
                }

                private void updateVolumeAndMute(int zoneId, int groupId, int flags) {
                    // TODO: Include zoneId into consideration.
                    VolumeItem volumeItem = mAvailableVolumeItems.get(groupId);
                    boolean muted = isGroupMuted(mCarAudioManager, groupId);
                    int value = getSeekbarValue(mCarAudioManager, groupId);

                    boolean isShowing = mCarVolumeLineItems.stream().anyMatch(
                            item -> item.getGroupId() == groupId);

                    if ((value != volumeItem.mProgress || muted != volumeItem.mIsMuted)
                            && isShowing) {
                        volumeItem.mCarVolumeItem.setProgress(value);
                        volumeItem.mProgress = value;
                        volumeItem.mCarVolumeItem.setIsMuted(muted);
                        volumeItem.mIsMuted = muted;
                    }
                    if ((flags & AudioManager.FLAG_SHOW_UI) != 0) {
                        mPreviouslyDisplayingGroupId = mCurrentlyDisplayingGroupId;
                        mCurrentlyDisplayingGroupId = groupId;
                        mHandler.obtainMessage(H.SHOW,
                                Events.SHOW_REASON_VOLUME_CHANGED).sendToTarget();
                    }
                }
            };

    private final CarServiceProvider.CarServiceOnConnectedListener mCarServiceOnConnectedListener =
            car -> {
                mExpanded = false;
                mCarAudioManager = (CarAudioManager) car.getCarManager(Car.AUDIO_SERVICE);
                int volumeGroupCount = mCarAudioManager.getVolumeGroupCount();
                // Populates volume slider items from volume groups to UI.
                for (int groupId = 0; groupId < volumeGroupCount; groupId++) {
                    VolumeItem volumeItem = getVolumeItemForUsages(
                            mCarAudioManager.getUsagesForVolumeGroupId(groupId));
                    mAvailableVolumeItems.add(volumeItem);
                    // The first one is the default item.
                    if (groupId == 0) {
                        clearAllAndSetupDefaultCarVolumeLineItem(0);
                    }
                }

                // If list is already initiated, update its content.
                if (mVolumeItemsAdapter != null) {
                    mVolumeItemsAdapter.notifyDataSetChanged();
                }
                mCarAudioManager.registerCarVolumeCallback(mVolumeChangeCallback);
            };

    private final BroadcastReceiver mHomeButtonPressedBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!intent.getAction().equals(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) {
                return;
            }

            dismissH(Events.DISMISS_REASON_VOLUME_CONTROLLER);
        }
    };

    public CarVolumeDialogImpl(
            Context context,
            CarServiceProvider carServiceProvider,
            ConfigurationController configurationController) {
        mContext = context;
        mCarServiceProvider = carServiceProvider;
        mKeyguard = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        mNormalTimeout = mContext.getResources().getInteger(
                R.integer.car_volume_dialog_display_normal_timeout);
        mHoveringTimeout = mContext.getResources().getInteger(
                R.integer.car_volume_dialog_display_hovering_timeout);
        mExpNormalTimeout = mContext.getResources().getInteger(
                R.integer.car_volume_dialog_display_expanded_normal_timeout);
        mExpHoveringTimeout = mContext.getResources().getInteger(
                R.integer.car_volume_dialog_display_expanded_hovering_timeout);
        mConfigurationController = configurationController;
    }

    private static int getSeekbarValue(CarAudioManager carAudioManager, int volumeGroupId) {
        return carAudioManager.getGroupVolume(volumeGroupId);
    }

    private static boolean isGroupMuted(CarAudioManager carAudioManager, int volumeGroupId) {
        if (!carAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_VOLUME_GROUP_MUTING)) {
            return false;
        }
        return carAudioManager.isVolumeGroupMuted(PRIMARY_AUDIO_ZONE, volumeGroupId);
    }

    private static int getMaxSeekbarValue(CarAudioManager carAudioManager, int volumeGroupId) {
        return carAudioManager.getGroupMaxVolume(volumeGroupId);
    }

    /**
     * Build the volume window and connect to the CarService which registers with car audio
     * manager.
     */
    @Override
    public void init(int windowType, Callback callback) {
        initDialog();
        mCarServiceProvider.addListener(mCarServiceOnConnectedListener);

        mContext.registerReceiverAsUser(mHomeButtonPressedBroadcastReceiver, UserHandle.CURRENT,
                new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS), /* broadcastPermission= */
                null, /* scheduler= */ null, Context.RECEIVER_EXPORTED);
        mConfigurationController.addCallback(this);
    }

    @Override
    public void destroy() {
        mHandler.removeCallbacksAndMessages(/* token= */ null);

        mContext.unregisterReceiver(mHomeButtonPressedBroadcastReceiver);

        cleanupAudioManager();
        mConfigurationController.removeCallback(this);
    }

    @Override
    public void onLayoutDirectionChanged(boolean isLayoutRtl) {
        if (mListView != null) {
            mListView.setLayoutDirection(
                    isLayoutRtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        }
    }

    /**
     * Reveals volume dialog.
     */
    public void show(int reason) {
        mHandler.obtainMessage(H.SHOW, reason).sendToTarget();
    }

    /**
     * Hides volume dialog.
     */
    public void dismiss(int reason) {
        mHandler.obtainMessage(H.DISMISS, reason).sendToTarget();
    }

    private void initDialog() {
        loadAudioUsageItems();
        mCarVolumeLineItems.clear();
        mDialog = new CustomDialog(mContext);

        mHovering = false;
        mDismissing = false;
        mExpanded = false;
        mWindow = mDialog.getWindow();
        mWindow.requestFeature(Window.FEATURE_NO_TITLE);
        mWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        mWindow.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND
                | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR);
        mWindow.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        mWindow.setType(WindowManager.LayoutParams.TYPE_VOLUME_OVERLAY);
        mWindow.setWindowAnimations(com.android.internal.R.style.Animation_Toast);
        final WindowManager.LayoutParams lp = mWindow.getAttributes();
        lp.format = PixelFormat.TRANSLUCENT;
        lp.setTitle(VolumeDialogImpl.class.getSimpleName());
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.windowAnimations = -1;
        mWindow.setAttributes(lp);

        mDialog.setContentView(R.layout.car_volume_dialog);
        mWindow.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        mDialog.setCanceledOnTouchOutside(true);
        mDialog.setOnShowListener(dialog -> {
            mListView.setTranslationY(-mListView.getHeight());
            mListView.setAlpha(0);
            PropertyValuesHolder pvhAlpha = PropertyValuesHolder.ofFloat(View.ALPHA, 1f);
            PropertyValuesHolder pvhY = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f);
            ObjectAnimator showAnimator = ObjectAnimator.ofPropertyValuesHolder(mListView, pvhAlpha,
                    pvhY);
            showAnimator.setDuration(LISTVIEW_ANIMATION_DURATION_IN_MILLIS);
            showAnimator.setInterpolator(new SystemUIInterpolators.LogDecelerateInterpolator());
            showAnimator.start();
        });
        mListView = mWindow.findViewById(R.id.volume_list);
        mListView.setOnHoverListener((v, event) -> {
            int action = event.getActionMasked();
            mHovering = (action == MotionEvent.ACTION_HOVER_ENTER)
                    || (action == MotionEvent.ACTION_HOVER_MOVE);
            rescheduleTimeoutH();
            return true;
        });

        mVolumeItemsAdapter = new CarVolumeItemAdapter(mContext, mCarVolumeLineItems);
        mListView.setAdapter(mVolumeItemsAdapter);
        mListView.setLayoutManager(new LinearLayoutManager(mContext));
    }


    private void showH(int reason) {
        if (DEBUG) {
            Log.d(TAG, "showH r=" + Events.DISMISS_REASONS[reason]);
        }

        mHandler.removeMessages(H.SHOW);
        mHandler.removeMessages(H.DISMISS);

        rescheduleTimeoutH();

        // Refresh the data set before showing.
        mVolumeItemsAdapter.notifyDataSetChanged();

        if (mDialog.isShowing()) {
            if (mPreviouslyDisplayingGroupId == mCurrentlyDisplayingGroupId || mExpanded) {
                return;
            }

            clearAllAndSetupDefaultCarVolumeLineItem(mCurrentlyDisplayingGroupId);
            return;
        }

        clearAllAndSetupDefaultCarVolumeLineItem(mCurrentlyDisplayingGroupId);
        mDismissing = false;
        mDialog.show();
        Events.writeEvent(Events.EVENT_SHOW_DIALOG, reason, mKeyguard.isKeyguardLocked());
    }

    private void clearAllAndSetupDefaultCarVolumeLineItem(int groupId) {
        mCarVolumeLineItems.clear();
        VolumeItem volumeItem = mAvailableVolumeItems.get(groupId);
        volumeItem.mDefaultItem = true;
        addCarVolumeListItem(volumeItem, /* volumeGroupId = */ groupId,
                R.drawable.car_ic_keyboard_arrow_down, new ExpandIconListener());
    }

    protected void rescheduleTimeoutH() {
        mHandler.removeMessages(H.DISMISS);
        final int timeout = computeTimeoutH();
        mHandler.sendMessageDelayed(mHandler
                .obtainMessage(H.DISMISS, Events.DISMISS_REASON_TIMEOUT), timeout);

        if (DEBUG) {
            Log.d(TAG, "rescheduleTimeout " + timeout + " " + Debug.getCaller());
        }
    }

    private int computeTimeoutH() {
        if (mExpanded) {
            return mHovering ? mExpHoveringTimeout : mExpNormalTimeout;
        } else {
            return mHovering ? mHoveringTimeout : mNormalTimeout;
        }
    }

    private void dismissH(int reason) {
        if (DEBUG) {
            Log.d(TAG, "dismissH r=" + Events.DISMISS_REASONS[reason]);
        }

        mHandler.removeMessages(H.DISMISS);
        mHandler.removeMessages(H.SHOW);
        if (!mDialog.isShowing() || mDismissing) {
            return;
        }

        PropertyValuesHolder pvhAlpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0f);
        PropertyValuesHolder pvhY = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y,
                (float) -mListView.getHeight());
        ObjectAnimator dismissAnimator = ObjectAnimator.ofPropertyValuesHolder(mListView, pvhAlpha,
                pvhY);
        dismissAnimator.setDuration(LISTVIEW_ANIMATION_DURATION_IN_MILLIS);
        dismissAnimator.setInterpolator(new SystemUIInterpolators.LogAccelerateInterpolator());
        dismissAnimator.addListener(new DismissAnimationListener());
        dismissAnimator.start();

        Events.writeEvent(Events.EVENT_DISMISS_DIALOG, reason);
    }

    private void loadAudioUsageItems() {
        if (DEBUG) {
            Log.i(TAG, "loadAudioUsageItems start");
        }

        try (XmlResourceParser parser = mContext.getResources().getXml(R.xml.car_volume_items)) {
            AttributeSet attrs = Xml.asAttributeSet(parser);
            int type;
            // Traverse to the first start tag
            while ((type = parser.next()) != XmlResourceParser.END_DOCUMENT
                    && type != XmlResourceParser.START_TAG) {
                // Do Nothing (moving parser to start element)
            }

            if (!XML_TAG_VOLUME_ITEMS.equals(parser.getName())) {
                throw new RuntimeException("Meta-data does not start with carVolumeItems tag");
            }
            int outerDepth = parser.getDepth();
            int rank = 0;
            while ((type = parser.next()) != XmlResourceParser.END_DOCUMENT
                    && (type != XmlResourceParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlResourceParser.END_TAG) {
                    continue;
                }
                if (XML_TAG_VOLUME_ITEM.equals(parser.getName())) {
                    TypedArray item = mContext.getResources().obtainAttributes(
                            attrs, R.styleable.carVolumeItems_item);
                    int usage = item.getInt(R.styleable.carVolumeItems_item_usage,
                            /* defValue= */ -1);
                    if (usage >= 0) {
                        VolumeItem volumeItem = new VolumeItem();
                        volumeItem.mRank = rank;
                        volumeItem.mIcon = item.getResourceId(
                                R.styleable.carVolumeItems_item_icon, /* defValue= */ 0);
                        volumeItem.mMuteIcon = item.getResourceId(
                                R.styleable.carVolumeItems_item_mute_icon, /* defValue= */ 0);
                        mVolumeItems.put(usage, volumeItem);
                        rank++;
                    }
                    item.recycle();
                }
            }
        } catch (XmlPullParserException | IOException e) {
            Log.e(TAG, "Error parsing volume groups configuration", e);
        }

        if (DEBUG) {
            Log.i(TAG,
                    "loadAudioUsageItems finished. Number of volume items: " + mVolumeItems.size());
        }
    }

    private VolumeItem getVolumeItemForUsages(int[] usages) {
        int rank = Integer.MAX_VALUE;
        VolumeItem result = null;
        for (int usage : usages) {
            VolumeItem volumeItem = mVolumeItems.get(usage);
            if (DEBUG) {
                Log.i(TAG, "getVolumeItemForUsage: " + usage + ": " + volumeItem);
            }
            if (volumeItem.mRank < rank) {
                rank = volumeItem.mRank;
                result = volumeItem;
            }
        }
        return result;
    }

    private CarVolumeItem createCarVolumeListItem(VolumeItem volumeItem, int volumeGroupId,
            Drawable supplementalIcon, int seekbarProgressValue,
            boolean isMuted, @Nullable View.OnClickListener supplementalIconOnClickListener) {
        CarVolumeItem carVolumeItem = new CarVolumeItem();
        carVolumeItem.setMax(getMaxSeekbarValue(mCarAudioManager, volumeGroupId));
        carVolumeItem.setProgress(seekbarProgressValue);
        carVolumeItem.setIsMuted(isMuted);
        carVolumeItem.setOnSeekBarChangeListener(
                new CarVolumeDialogImpl.VolumeSeekBarChangeListener(volumeGroupId,
                        mCarAudioManager));
        carVolumeItem.setGroupId(volumeGroupId);

        int color = mContext.getColor(R.color.car_volume_dialog_tint);
        Drawable primaryIcon = mContext.getDrawable(volumeItem.mIcon);
        primaryIcon.mutate().setTint(color);
        carVolumeItem.setPrimaryIcon(primaryIcon);

        Drawable primaryMuteIcon = mContext.getDrawable(volumeItem.mMuteIcon);
        primaryMuteIcon.mutate().setTint(color);
        carVolumeItem.setPrimaryMuteIcon(primaryMuteIcon);

        if (supplementalIcon != null) {
            supplementalIcon.mutate().setTint(color);
            carVolumeItem.setSupplementalIcon(supplementalIcon,
                    /* showSupplementalIconDivider= */ true);
            carVolumeItem.setSupplementalIconListener(supplementalIconOnClickListener);
        } else {
            carVolumeItem.setSupplementalIcon(/* drawable= */ null,
                    /* showSupplementalIconDivider= */ false);
        }

        volumeItem.mCarVolumeItem = carVolumeItem;
        volumeItem.mProgress = seekbarProgressValue;

        return carVolumeItem;
    }

    private CarVolumeItem addCarVolumeListItem(VolumeItem volumeItem, int volumeGroupId,
            int supplementalIconId,
            @Nullable View.OnClickListener supplementalIconOnClickListener) {
        int seekbarProgressValue = getSeekbarValue(mCarAudioManager, volumeGroupId);
        boolean isMuted = isGroupMuted(mCarAudioManager, volumeGroupId);
        Drawable supplementalIcon = supplementalIconId == 0 ? null : mContext.getDrawable(
                supplementalIconId);
        CarVolumeItem carVolumeItem = createCarVolumeListItem(volumeItem, volumeGroupId,
                supplementalIcon, seekbarProgressValue, isMuted, supplementalIconOnClickListener);
        mCarVolumeLineItems.add(carVolumeItem);
        return carVolumeItem;
    }

    private void cleanupAudioManager() {
        mCarAudioManager.unregisterCarVolumeCallback(mVolumeChangeCallback);
        mCarVolumeLineItems.clear();
        mCarAudioManager = null;
    }

    /**
     * Wrapper class which contains information of each volume group.
     */
    private static class VolumeItem {
        private int mRank;
        private boolean mDefaultItem = false;
        @DrawableRes
        private int mIcon;
        @DrawableRes
        private int mMuteIcon;
        private CarVolumeItem mCarVolumeItem;
        private int mProgress;
        private boolean mIsMuted;
    }

    private final class H extends Handler {

        private static final int SHOW = 1;
        private static final int DISMISS = 2;

        private H() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SHOW:
                    showH(msg.arg1);
                    break;
                case DISMISS:
                    dismissH(msg.arg1);
                    break;
                default:
            }
        }
    }

    private final class CustomDialog extends Dialog implements DialogInterface {

        private CustomDialog(Context context) {
            super(context, com.android.systemui.R.style.Theme_SystemUI);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            rescheduleTimeoutH();
            return super.dispatchTouchEvent(ev);
        }

        @Override
        protected void onStart() {
            super.setCanceledOnTouchOutside(true);
            super.onStart();
        }

        @Override
        protected void onStop() {
            super.onStop();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (isShowing()) {
                if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                    mHandler.obtainMessage(
                            H.DISMISS, Events.DISMISS_REASON_TOUCH_OUTSIDE).sendToTarget();
                    return true;
                }
            }
            return false;
        }
    }

    private final class DismissAnimationListener implements Animator.AnimatorListener {
        @Override
        public void onAnimationStart(Animator animation) {
            mDismissing = true;
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            mHandler.postDelayed(() -> {
                if (DEBUG) {
                    Log.d(TAG, "mDialog.dismiss()");
                }
                mDialog.dismiss();
                mDismissing = false;
                // if mExpandIcon is null that means user never clicked on the expanded arrow
                // which implies that the dialog is still not expanded. In that case we do
                // not want to reset the state
                if (mExpandIcon != null && mExpanded) {
                    toggleDialogExpansion(/* isClicked = */ false);
                }
            }, DISMISS_DELAY_IN_MILLIS);
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            // A canceled animation will also call onAnimationEnd so any necessary cleanup will
            // already happen there
            if (DEBUG) {
                Log.d(TAG, "dismiss animation canceled");
            }
        }

        @Override
        public void onAnimationRepeat(Animator animation) {
            // no-op
        }
    }

    private final class ExpandIconListener implements View.OnClickListener {
        @Override
        public void onClick(final View v) {
            mExpandIcon = v;
            toggleDialogExpansion(true);
            rescheduleTimeoutH();
        }
    }

    private void toggleDialogExpansion(boolean isClicked) {
        mExpanded = !mExpanded;
        Animator inAnimator;
        if (mExpanded) {
            for (int groupId = 0; groupId < mAvailableVolumeItems.size(); ++groupId) {
                if (groupId != mCurrentlyDisplayingGroupId) {
                    VolumeItem volumeItem = mAvailableVolumeItems.get(groupId);
                    addCarVolumeListItem(volumeItem, groupId, /* supplementalIconId= */ 0,
                            /* supplementalIconOnClickListener= */ null);
                }
            }
            inAnimator = AnimatorInflater.loadAnimator(
                    mContext, R.anim.car_arrow_fade_in_rotate_up);

        } else {
            clearAllAndSetupDefaultCarVolumeLineItem(mCurrentlyDisplayingGroupId);
            inAnimator = AnimatorInflater.loadAnimator(
                    mContext, R.anim.car_arrow_fade_in_rotate_down);
        }

        Animator outAnimator = AnimatorInflater.loadAnimator(
                mContext, R.anim.car_arrow_fade_out);
        inAnimator.setStartDelay(ARROW_FADE_IN_START_DELAY_IN_MILLIS);
        AnimatorSet animators = new AnimatorSet();
        animators.playTogether(outAnimator, inAnimator);
        if (!isClicked) {
            // Do not animate when the state is called to reset the dialogs view and not clicked
            // by user.
            animators.setDuration(0);
        }
        animators.setTarget(mExpandIcon);
        animators.start();
        mVolumeItemsAdapter.notifyDataSetChanged();
    }

    private final class VolumeSeekBarChangeListener implements OnSeekBarChangeListener {

        private final int mVolumeGroupId;
        private final CarAudioManager mCarAudioManager;

        private VolumeSeekBarChangeListener(int volumeGroupId, CarAudioManager carAudioManager) {
            mVolumeGroupId = volumeGroupId;
            mCarAudioManager = carAudioManager;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (!fromUser) {
                // For instance, if this event is originated from AudioService,
                // we can ignore it as it has already been handled and doesn't need to be
                // sent back down again.
                return;
            }
            if (mCarAudioManager == null) {
                Log.w(TAG, "Ignoring volume change event because the car isn't connected");
                return;
            }
            mAvailableVolumeItems.get(mVolumeGroupId).mProgress = progress;
            mAvailableVolumeItems.get(
                    mVolumeGroupId).mCarVolumeItem.setProgress(progress);
            mCarAudioManager.setGroupVolume(mVolumeGroupId, progress, 0);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
