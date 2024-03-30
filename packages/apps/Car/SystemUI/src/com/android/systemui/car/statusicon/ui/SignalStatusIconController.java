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

package com.android.systemui.car.statusicon.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import androidx.annotation.VisibleForTesting;

import com.android.settingslib.graph.SignalDrawable;
import com.android.systemui.R;
import com.android.systemui.car.statusicon.StatusIconController;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.statusbar.connectivity.MobileDataIndicators;
import com.android.systemui.statusbar.connectivity.NetworkController;
import com.android.systemui.statusbar.connectivity.SignalCallback;
import com.android.systemui.statusbar.connectivity.WifiIndicators;
import com.android.systemui.statusbar.policy.HotspotController;

import javax.inject.Inject;

/**
 * A controller for status icon about mobile data, Wi-Fi, and hotspot.
 */
public class SignalStatusIconController extends StatusIconController implements
        SignalCallback, HotspotController.Callback {

    private final Context mContext;
    private final Resources mResources;
    private final HotspotController mHotspotController;
    private final NetworkController mNetworkController;

    private SignalDrawable mMobileSignalIconDrawable;
    private Drawable mWifiSignalIconDrawable;
    private Drawable mHotSpotIconDrawable;
    private boolean mIsWifiEnabledAndConnected;
    private boolean mIsHotspotEnabled;
    private String mMobileSignalContentDescription;
    private String mWifiConnectedContentDescription;
    private String mHotspotOnContentDescription;

    @Inject
    SignalStatusIconController(
            Context context,
            @Main Resources resources,
            NetworkController networkController,
            HotspotController hotspotController) {
        mContext = context;
        mResources = resources;
        mHotspotController = hotspotController;
        mNetworkController = networkController;

        mMobileSignalIconDrawable = new SignalDrawable(mContext);
        mHotSpotIconDrawable = mResources.getDrawable(R.drawable.ic_hotspot, mContext.getTheme());

        mMobileSignalContentDescription = resources.getString(R.string.status_icon_signal_mobile);
        mWifiConnectedContentDescription = resources.getString(R.string.status_icon_signal_wifi);
        mHotspotOnContentDescription = resources.getString(R.string.status_icon_signal_hotspot);

        mNetworkController.addCallback(this);
        mHotspotController.addCallback(this);
    }

    @Override
    protected void updateStatus() {
        if (mIsHotspotEnabled) {
            setIconDrawableToDisplay(mHotSpotIconDrawable);
            setIconContentDescription(mHotspotOnContentDescription);
        } else if (mIsWifiEnabledAndConnected) {
            setIconDrawableToDisplay(mWifiSignalIconDrawable);
            setIconContentDescription(mWifiConnectedContentDescription);
        } else {
            setIconDrawableToDisplay(mMobileSignalIconDrawable);
            setIconContentDescription(mMobileSignalContentDescription);
        }
        onStatusUpdated();
    }

    @Override
    public void setMobileDataIndicators(MobileDataIndicators mobileDataIndicators) {
        mMobileSignalIconDrawable.setLevel(mobileDataIndicators.statusIcon.icon);
        updateStatus();
    }

    @Override
    public void setWifiIndicators(WifiIndicators indicators) {
        mIsWifiEnabledAndConnected = indicators.enabled && indicators.statusIcon.visible;
        mWifiSignalIconDrawable = mResources.getDrawable(indicators.statusIcon.icon,
                mContext.getTheme());
        updateStatus();
    }

    @Override
    public void onHotspotChanged(boolean enabled, int numDevices) {
        mIsHotspotEnabled = enabled;
        updateStatus();
    }

    @Override
    protected int getPanelContentLayout() {
        return R.layout.qc_connectivity_panel;
    }

    @VisibleForTesting
    SignalDrawable getMobileSignalIconDrawable() {
        return mMobileSignalIconDrawable;
    }

    @VisibleForTesting
    Drawable getWifiSignalIconDrawable() {
        return mWifiSignalIconDrawable;
    }

    @VisibleForTesting
    Drawable getHotSpotIconDrawable() {
        return mHotSpotIconDrawable;
    }

    @Override
    protected int getId() {
        return R.id.qc_signal_status_icon;
    }
}
