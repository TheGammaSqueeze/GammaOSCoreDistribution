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
package com.android.nearby.halfsheet.fragment;

import static android.text.TextUtils.isEmpty;

import static com.android.nearby.halfsheet.HalfSheetActivity.ARG_FRAGMENT_STATE;
import static com.android.nearby.halfsheet.HalfSheetActivity.EXTRA_DESCRIPTION;
import static com.android.nearby.halfsheet.HalfSheetActivity.EXTRA_HALF_SHEET_ACCOUNT_NAME;
import static com.android.nearby.halfsheet.HalfSheetActivity.EXTRA_HALF_SHEET_CONTENT;
import static com.android.nearby.halfsheet.HalfSheetActivity.EXTRA_HALF_SHEET_ID;
import static com.android.nearby.halfsheet.HalfSheetActivity.EXTRA_TITLE;
import static com.android.nearby.halfsheet.HalfSheetActivity.TAG;
import static com.android.nearby.halfsheet.fragment.HalfSheetModuleFragment.HalfSheetFragmentState.FAILED;
import static com.android.nearby.halfsheet.fragment.HalfSheetModuleFragment.HalfSheetFragmentState.FOUND_DEVICE;
import static com.android.nearby.halfsheet.fragment.HalfSheetModuleFragment.HalfSheetFragmentState.NOT_STARTED;
import static com.android.nearby.halfsheet.fragment.HalfSheetModuleFragment.HalfSheetFragmentState.PAIRED_LAUNCHABLE;
import static com.android.nearby.halfsheet.fragment.HalfSheetModuleFragment.HalfSheetFragmentState.PAIRED_UNLAUNCHABLE;
import static com.android.nearby.halfsheet.fragment.HalfSheetModuleFragment.HalfSheetFragmentState.PAIRING;
import static com.android.server.nearby.fastpair.Constant.EXTRA_BINDER;
import static com.android.server.nearby.fastpair.Constant.EXTRA_BUNDLE;
import static com.android.server.nearby.fastpair.Constant.EXTRA_HALF_SHEET_INFO;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.nearby.FastPairDevice;
import android.nearby.FastPairStatusCallback;
import android.nearby.NearbyDevice;
import android.nearby.PairStatusMetadata;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.nearby.halfsheet.FastPairUiServiceClient;
import com.android.nearby.halfsheet.HalfSheetActivity;
import com.android.nearby.halfsheet.R;
import com.android.nearby.halfsheet.utils.FastPairUtils;
import com.android.nearby.halfsheet.utils.IconUtils;

import com.google.protobuf.InvalidProtocolBufferException;

import java.util.Objects;

import service.proto.Cache.ScanFastPairStoreItem;

/**
 * Modularize half sheet for fast pair this fragment will show when half sheet does device pairing.
 *
 * <p>This fragment will handle initial pairing subsequent pairing and retroactive pairing.
 */
@SuppressWarnings("nullness")
public class DevicePairingFragment extends HalfSheetModuleFragment implements
        FastPairStatusCallback {
    private TextView mTitleView;
    private TextView mSubTitleView;
    private ImageView mImage;

    private Button mConnectButton;
    private Button mSetupButton;
    private Button mCancelButton;
    // Opens Bluetooth Settings.
    private Button mSettingsButton;
    private ImageView mInfoIconButton;
    private ProgressBar mConnectProgressBar;

    private Bundle mBundle;

    private ScanFastPairStoreItem mScanFastPairStoreItem;
    private FastPairUiServiceClient mFastPairUiServiceClient;

    private @PairStatusMetadata.Status int mPairStatus = PairStatusMetadata.Status.UNKNOWN;
    // True when there is a companion app to open.
    private boolean mIsLaunchable;
    private boolean mIsConnecting;
    // Indicates that the setup button is clicked before.
    private boolean mSetupButtonClicked = false;

    // Holds the new text while we transition between the two.
    private static final int TAG_PENDING_TEXT = R.id.toolbar_title;
    public static final String APP_LAUNCH_FRAGMENT_TYPE = "APP_LAUNCH";

    private static final String ARG_SETUP_BUTTON_CLICKED = "SETUP_BUTTON_CLICKED";
    private static final String ARG_PAIRING_RESULT = "PAIRING_RESULT";

    /**
     * Create certain fragment according to the intent.
     */
    @Nullable
    public static HalfSheetModuleFragment newInstance(
            Intent intent, @Nullable Bundle saveInstanceStates) {
        Bundle args = new Bundle();
        byte[] infoArray = intent.getByteArrayExtra(EXTRA_HALF_SHEET_INFO);

        Bundle bundle = intent.getBundleExtra(EXTRA_BUNDLE);
        String title = intent.getStringExtra(EXTRA_TITLE);
        String description = intent.getStringExtra(EXTRA_DESCRIPTION);
        String accountName = intent.getStringExtra(EXTRA_HALF_SHEET_ACCOUNT_NAME);
        String result = intent.getStringExtra(EXTRA_HALF_SHEET_CONTENT);
        int halfSheetId = intent.getIntExtra(EXTRA_HALF_SHEET_ID, 0);

        args.putByteArray(EXTRA_HALF_SHEET_INFO, infoArray);
        args.putString(EXTRA_HALF_SHEET_ACCOUNT_NAME, accountName);
        args.putString(EXTRA_TITLE, title);
        args.putString(EXTRA_DESCRIPTION, description);
        args.putInt(EXTRA_HALF_SHEET_ID, halfSheetId);
        args.putString(EXTRA_HALF_SHEET_CONTENT, result == null ? "" : result);
        args.putBundle(EXTRA_BUNDLE, bundle);
        if (saveInstanceStates != null) {
            if (saveInstanceStates.containsKey(ARG_FRAGMENT_STATE)) {
                args.putSerializable(
                        ARG_FRAGMENT_STATE, saveInstanceStates.getSerializable(ARG_FRAGMENT_STATE));
            }
            if (saveInstanceStates.containsKey(BluetoothDevice.EXTRA_DEVICE)) {
                args.putParcelable(
                        BluetoothDevice.EXTRA_DEVICE,
                        saveInstanceStates.getParcelable(BluetoothDevice.EXTRA_DEVICE));
            }
            if (saveInstanceStates.containsKey(BluetoothDevice.EXTRA_PAIRING_KEY)) {
                args.putInt(
                        BluetoothDevice.EXTRA_PAIRING_KEY,
                        saveInstanceStates.getInt(BluetoothDevice.EXTRA_PAIRING_KEY));
            }
            if (saveInstanceStates.containsKey(ARG_SETUP_BUTTON_CLICKED)) {
                args.putBoolean(
                        ARG_SETUP_BUTTON_CLICKED,
                        saveInstanceStates.getBoolean(ARG_SETUP_BUTTON_CLICKED));
            }
            if (saveInstanceStates.containsKey(ARG_PAIRING_RESULT)) {
                args.putBoolean(ARG_PAIRING_RESULT,
                        saveInstanceStates.getBoolean(ARG_PAIRING_RESULT));
            }
        }
        DevicePairingFragment fragment = new DevicePairingFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(
            LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        /* attachToRoot= */
        View rootView = inflater.inflate(
                R.layout.fast_pair_device_pairing_fragment, container, /* attachToRoot= */
                false);
        if (getContext() == null) {
            Log.d(TAG, "can't find the attached activity");
            return rootView;
        }

        Bundle args = getArguments();
        byte[] storeFastPairItemBytesArray = args.getByteArray(EXTRA_HALF_SHEET_INFO);
        mBundle = args.getBundle(EXTRA_BUNDLE);
        if (mBundle != null) {
            mFastPairUiServiceClient =
                    new FastPairUiServiceClient(getContext(), mBundle.getBinder(EXTRA_BINDER));
            mFastPairUiServiceClient.registerHalfSheetStateCallBack(this);
        }
        if (args.containsKey(ARG_FRAGMENT_STATE)) {
            mFragmentState = (HalfSheetFragmentState) args.getSerializable(ARG_FRAGMENT_STATE);
        }
        if (args.containsKey(ARG_SETUP_BUTTON_CLICKED)) {
            mSetupButtonClicked = args.getBoolean(ARG_SETUP_BUTTON_CLICKED);
        }
        if (args.containsKey(ARG_PAIRING_RESULT)) {
            mPairStatus = args.getInt(ARG_PAIRING_RESULT);
        }

        // Initiate views.
        mTitleView = Objects.requireNonNull(getActivity()).findViewById(R.id.toolbar_title);
        mSubTitleView = rootView.findViewById(R.id.header_subtitle);
        mImage = rootView.findViewById(R.id.pairing_pic);
        mConnectProgressBar = rootView.findViewById(R.id.connect_progressbar);
        mConnectButton = rootView.findViewById(R.id.connect_btn);
        mCancelButton = rootView.findViewById(R.id.cancel_btn);
        mSettingsButton = rootView.findViewById(R.id.settings_btn);
        mSetupButton = rootView.findViewById(R.id.setup_btn);
        mInfoIconButton = rootView.findViewById(R.id.info_icon);
        mInfoIconButton.setImageResource(R.drawable.fast_pair_ic_info);

        try {
            setScanFastPairStoreItem(ScanFastPairStoreItem.parseFrom(storeFastPairItemBytesArray));
        } catch (InvalidProtocolBufferException e) {
            Log.w(TAG,
                    "DevicePairingFragment: error happens when pass info to half sheet");
            return rootView;
        }

        // Config for landscape mode
        DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            rootView.getLayoutParams().height = displayMetrics.heightPixels * 4 / 5;
            rootView.getLayoutParams().width = displayMetrics.heightPixels * 4 / 5;
            mImage.getLayoutParams().height = displayMetrics.heightPixels / 2;
            mImage.getLayoutParams().width = displayMetrics.heightPixels / 2;
            mConnectProgressBar.getLayoutParams().width = displayMetrics.heightPixels / 2;
            mConnectButton.getLayoutParams().width = displayMetrics.heightPixels / 2;
            //TODO(b/213373051): Add cancel button
        }

        Bitmap icon = IconUtils.getIcon(mScanFastPairStoreItem.getIconPng().toByteArray(),
                mScanFastPairStoreItem.getIconPng().size());
        if (icon != null) {
            mImage.setImageBitmap(icon);
        }
        mConnectButton.setOnClickListener(v -> onConnectClick());
        mCancelButton.setOnClickListener(v ->
                ((HalfSheetActivity) getActivity()).onCancelClicked());
        mSettingsButton.setOnClickListener(v -> onSettingsClicked());
        mSetupButton.setOnClickListener(v -> onSetupClick());

        return rootView;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Get access to the activity's menu
        setHasOptionsMenu(true);
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.v(TAG, "onStart: invalidate states");
        invalidateState();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);

        savedInstanceState.putSerializable(ARG_FRAGMENT_STATE, mFragmentState);
        savedInstanceState.putBoolean(ARG_SETUP_BUTTON_CLICKED, mSetupButtonClicked);
        savedInstanceState.putInt(ARG_PAIRING_RESULT, mPairStatus);
    }

    private void onSettingsClicked() {
        startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
    }

    private void onSetupClick() {
        String companionApp =
                FastPairUtils.getCompanionAppFromActionUrl(mScanFastPairStoreItem.getActionUrl());
        Intent intent =
                FastPairUtils.createCompanionAppIntent(
                        Objects.requireNonNull(getContext()),
                        companionApp,
                        mScanFastPairStoreItem.getAddress());
        mSetupButtonClicked = true;
        if (mFragmentState == PAIRED_LAUNCHABLE) {
            if (intent != null) {
                startActivity(intent);
            }
        } else {
            Log.d(TAG, "onSetupClick: State is " + mFragmentState);
        }
    }

    private void onConnectClick() {
        if (mScanFastPairStoreItem == null) {
            Log.w(TAG, "No pairing related information in half sheet");
            return;
        }
        if (getFragmentState() == PAIRING) {
            return;
        }
        mIsConnecting = true;
        invalidateState();
        mFastPairUiServiceClient.connect(
                new FastPairDevice.Builder()
                        .addMedium(NearbyDevice.Medium.BLE)
                        .setBluetoothAddress(mScanFastPairStoreItem.getAddress())
                        .setData(FastPairUtils.convertFrom(mScanFastPairStoreItem)
                                .toByteArray())
                        .build());
    }

    // Receives callback from service.
    @Override
    public void onPairUpdate(FastPairDevice fastPairDevice, PairStatusMetadata pairStatusMetadata) {
        @PairStatusMetadata.Status int status = pairStatusMetadata.getStatus();
        if (status == PairStatusMetadata.Status.DISMISS && getActivity() != null) {
            getActivity().finish();
        }
        mIsConnecting = false;
        mPairStatus = status;
        invalidateState();
    }

    @Override
    public void invalidateState() {
        HalfSheetFragmentState newState = NOT_STARTED;
        if (mIsConnecting) {
            newState = PAIRING;
        } else {
            switch (mPairStatus) {
                case PairStatusMetadata.Status.SUCCESS:
                    newState = mIsLaunchable ? PAIRED_LAUNCHABLE : PAIRED_UNLAUNCHABLE;
                    break;
                case PairStatusMetadata.Status.FAIL:
                    newState = FAILED;
                    break;
                default:
                    if (mScanFastPairStoreItem != null) {
                        newState = FOUND_DEVICE;
                    }
            }
        }
        if (newState == mFragmentState) {
            return;
        }
        setState(newState);
    }

    @Override
    public void setState(HalfSheetFragmentState state) {
        super.setState(state);
        invalidateTitles();
        invalidateButtons();
    }

    private void setScanFastPairStoreItem(ScanFastPairStoreItem item) {
        mScanFastPairStoreItem = item;
        invalidateLaunchable();
    }

    private void invalidateLaunchable() {
        String companionApp =
                FastPairUtils.getCompanionAppFromActionUrl(mScanFastPairStoreItem.getActionUrl());
        if (isEmpty(companionApp)) {
            mIsLaunchable = false;
            return;
        }
        mIsLaunchable =
                FastPairUtils.isLaunchable(Objects.requireNonNull(getContext()), companionApp);
    }

    private void invalidateButtons() {
        mConnectProgressBar.setVisibility(View.INVISIBLE);
        mConnectButton.setVisibility(View.INVISIBLE);
        mCancelButton.setVisibility(View.INVISIBLE);
        mSetupButton.setVisibility(View.INVISIBLE);
        mSettingsButton.setVisibility(View.INVISIBLE);
        mInfoIconButton.setVisibility(View.INVISIBLE);

        switch (mFragmentState) {
            case FOUND_DEVICE:
                mInfoIconButton.setVisibility(View.VISIBLE);
                mConnectButton.setVisibility(View.VISIBLE);
                break;
            case PAIRING:
                mConnectProgressBar.setVisibility(View.VISIBLE);
                mCancelButton.setVisibility(View.VISIBLE);
                setBackgroundClickable(false);
                break;
            case PAIRED_LAUNCHABLE:
                mCancelButton.setVisibility(View.VISIBLE);
                mSetupButton.setVisibility(View.VISIBLE);
                setBackgroundClickable(true);
                break;
            case FAILED:
                mSettingsButton.setVisibility(View.VISIBLE);
                setBackgroundClickable(true);
                break;
            case NOT_STARTED:
            case PAIRED_UNLAUNCHABLE:
            default:
                mCancelButton.setVisibility(View.VISIBLE);
                setBackgroundClickable(true);
        }
    }

    private void setBackgroundClickable(boolean isClickable) {
        HalfSheetActivity activity = (HalfSheetActivity) getActivity();
        if (activity == null) {
            Log.w(TAG, "setBackgroundClickable: failed to set clickable to " + isClickable
                    + " because cannot get HalfSheetActivity.");
            return;
        }
        View background = activity.findViewById(R.id.background);
        if (background == null) {
            Log.w(TAG, "setBackgroundClickable: failed to set clickable to " + isClickable
                    + " cannot find background at HalfSheetActivity.");
            return;
        }
        Log.d(TAG, "setBackgroundClickable to " + isClickable);
        background.setClickable(isClickable);
    }

    private void invalidateTitles() {
        String newTitle = getTitle();
        invalidateTextView(mTitleView, newTitle);
        String newSubTitle = getSubTitle();
        invalidateTextView(mSubTitleView, newSubTitle);
    }

    private void invalidateTextView(TextView textView, String newText) {
        CharSequence oldText =
                textView.getTag(TAG_PENDING_TEXT) != null
                        ? (CharSequence) textView.getTag(TAG_PENDING_TEXT)
                        : textView.getText();
        if (TextUtils.equals(oldText, newText)) {
            return;
        }
        if (TextUtils.isEmpty(oldText)) {
            // First time run. Don't animate since there's nothing to animate from.
            textView.setText(newText);
        } else {
            textView.setTag(TAG_PENDING_TEXT, newText);
            textView
                    .animate()
                    .alpha(0f)
                    .setDuration(TEXT_ANIMATION_DURATION_MILLISECONDS)
                    .withEndAction(
                            () -> {
                                textView.setText(newText);
                                textView
                                        .animate()
                                        .alpha(1f)
                                        .setDuration(TEXT_ANIMATION_DURATION_MILLISECONDS);
                            });
        }
    }

    private String getTitle() {
        switch (mFragmentState) {
            case PAIRED_LAUNCHABLE:
                return getString(R.string.fast_pair_title_setup);
            case FAILED:
                return getString(R.string.fast_pair_title_fail);
            case FOUND_DEVICE:
            case NOT_STARTED:
            case PAIRED_UNLAUNCHABLE:
            default:
                return mScanFastPairStoreItem.getDeviceName();
        }
    }

    private String getSubTitle() {
        switch (mFragmentState) {
            case PAIRED_LAUNCHABLE:
                return String.format(
                        mScanFastPairStoreItem
                                .getFastPairStrings()
                                .getPairingFinishedCompanionAppInstalled(),
                        mScanFastPairStoreItem.getDeviceName());
            case FAILED:
                return mScanFastPairStoreItem.getFastPairStrings().getPairingFailDescription();
            case PAIRED_UNLAUNCHABLE:
                getString(R.string.fast_pair_device_ready);
            // fall through
            case FOUND_DEVICE:
            case NOT_STARTED:
                return mScanFastPairStoreItem.getFastPairStrings().getInitialPairingDescription();
            default:
                return "";
        }
    }
}
