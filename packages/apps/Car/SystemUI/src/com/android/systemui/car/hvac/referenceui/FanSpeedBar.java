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

package com.android.systemui.car.hvac.referenceui;

import static android.car.VehiclePropertyIds.HVAC_AUTO_ON;
import static android.car.VehiclePropertyIds.HVAC_FAN_SPEED;
import static android.car.VehiclePropertyIds.HVAC_POWER_ON;

import android.car.hardware.CarPropertyValue;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.car.hvac.HvacController;
import com.android.systemui.car.hvac.HvacPropertySetter;
import com.android.systemui.car.hvac.HvacView;

import java.util.ArrayList;
import java.util.List;

public class FanSpeedBar extends RelativeLayout implements HvacView {

    private static final int BAR_SEGMENT_ANIMATION_DELAY_MS = 50;
    private static final int BAR_SEGMENT_ANIMATION_PERIOD_MS = 100;

    private HvacPropertySetter mHvacPropertySetter;

    private int mHvacGlobalAreaId;

    private int mButtonActiveTextColor;
    private int mButtonInactiveTextColor;

    private int mFanOffActiveBgColor;
    private int mFanMaxActiveBgColor;

    private float mCornerRadius;

    private TextView mMaxButton;
    private TextView mOffButton;

    private FanSpeedBarSegment mFanSpeed1;
    private FanSpeedBarSegment mFanSpeed2;
    private FanSpeedBarSegment mFanSpeed3;
    private FanSpeedBarSegment mFanSpeed4;

    private boolean mPowerOn;
    private boolean mAutoOn;

    private float mOnAlpha;
    private float mOffAlpha;

    private final List<FanSpeedBarSegment> mFanSpeedButtons = new ArrayList<>();

    public FanSpeedBar(Context context) {
        super(context);
        init();
    }

    public FanSpeedBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FanSpeedBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.fan_speed, this);

        Resources res = getContext().getResources();
        // The fanspeed bar is set as height 72dp to match min tap target size. However it is
        // inset by fan speed inset to make it appear thinner.
        int barHeight = res.getDimensionPixelSize(R.dimen.hvac_fan_speed_bar_height);
        int insetHeight = res.getDimensionPixelSize(R.dimen.hvac_fan_speed_bar_vertical_inset);
        mCornerRadius = (barHeight - 2 * insetHeight) / 2;

        mFanOffActiveBgColor = res.getColor(R.color.hvac_fanspeed_off_active_bg);

        mButtonActiveTextColor = res.getColor(R.color.hvac_fanspeed_off_active_text_color);
        mButtonInactiveTextColor = res.getColor(R.color.hvac_fanspeed_off_inactive_text_color);
        mFanMaxActiveBgColor = res.getColor(R.color.hvac_fanspeed_segment_color);
        mHvacGlobalAreaId = res.getInteger(R.integer.hvac_global_area_id);
    }

    @Override
    public void setHvacPropertySetter(HvacPropertySetter hvacPropertySetter) {
        mHvacPropertySetter = hvacPropertySetter;
    }

    @Override
    public void onPropertyChanged(CarPropertyValue value) {
        if (value.getPropertyId() == HVAC_FAN_SPEED) {
            int level = (Integer) value.getValue();

            setOffAndMaxButtonsActiveState(level);

            int fanSpeedCount = mFanSpeedButtons.size();
            int fanSpeedIndex = Math.min(level - 1, 4);

            int delay = 0;
            // Animate segments turning on when the fan speed is increased.
            for (int i = 0; i < fanSpeedIndex; i++) {
                FanSpeedBarSegment fanSpeedButton = mFanSpeedButtons.get(i);
                if (!fanSpeedButton.isTurnedOn()) {
                    fanSpeedButton.playTurnOnAnimation(delay, BAR_SEGMENT_ANIMATION_PERIOD_MS);
                    delay += BAR_SEGMENT_ANIMATION_DELAY_MS;
                }
            }

            delay = 0;
            // Animate segments turning off when the fan speed is decreased.
            for (int i = fanSpeedCount - 1; i >= fanSpeedIndex; i--) {
                FanSpeedBarSegment fanSpeedButton = mFanSpeedButtons.get(i);
                if (fanSpeedButton.isTurnedOn()) {
                    fanSpeedButton.playTurnOffAnimation(delay, BAR_SEGMENT_ANIMATION_PERIOD_MS);
                    delay += BAR_SEGMENT_ANIMATION_DELAY_MS;
                }
            }

            return;
        }

        if (value.getPropertyId() == HVAC_POWER_ON) {
            mPowerOn = (Boolean) value.getValue();
        }

        if (value.getPropertyId() == HVAC_AUTO_ON) {
            mAutoOn = (Boolean) value.getValue();
        }

        updateViewPerAvailability();
    }

    @Override
    public @HvacController.HvacProperty Integer getHvacPropertyToView() {
        return HVAC_FAN_SPEED;
    }

    @Override
    public @HvacController.AreaId Integer getAreaId() {
        return mHvacGlobalAreaId;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mOnAlpha = mContext.getResources().getFloat(R.dimen.hvac_turned_on_alpha);
        mOffAlpha = mContext.getResources().getFloat(R.dimen.hvac_turned_off_alpha);

        mOffButton = (TextView) findViewById(R.id.fan_off);
        mFanSpeed1 = (FanSpeedBarSegment) findViewById(R.id.fan_speed_1);
        mFanSpeed2 = (FanSpeedBarSegment) findViewById(R.id.fan_speed_2);
        mFanSpeed3 = (FanSpeedBarSegment) findViewById(R.id.fan_speed_3);
        mFanSpeed4 = (FanSpeedBarSegment) findViewById(R.id.fan_speed_4);
        mMaxButton = (TextView) findViewById(R.id.fan_max);

        mFanSpeedButtons.add(mFanSpeed1);
        mFanSpeedButtons.add(mFanSpeed2);
        mFanSpeedButtons.add(mFanSpeed3);
        mFanSpeedButtons.add(mFanSpeed4);

        for (int i = 0; i < mFanSpeedButtons.size(); i++) {
            mFanSpeedButtons.get(i).setOnClickListener(
                    getOnClickListener(/* fanSpeedLevel =*/ i + 2));
        }

        mMaxButton.setOnClickListener(getOnClickListener(6));
        mOffButton.setOnClickListener(getOnClickListener(1));

        // Set the corner radius of the off/max button based on the height of the bar to get a
        // pill-shaped border.
        GradientDrawable offButtonBg = new GradientDrawable();
        offButtonBg.setCornerRadii(new float[]{mCornerRadius, mCornerRadius, 0, 0,
                0, 0, mCornerRadius, mCornerRadius});
        mOffButton.setBackground(offButtonBg);
        mOffButton.setTextColor(mButtonInactiveTextColor);

        GradientDrawable maxButtonBg = new GradientDrawable();
        maxButtonBg.setCornerRadii(new float[]{0, 0, mCornerRadius, mCornerRadius,
                mCornerRadius, mCornerRadius, 0, 0});
        mMaxButton.setBackground(maxButtonBg);
        mMaxButton.setTextColor(mButtonInactiveTextColor);
    }

    @Override
    public void onHvacTemperatureUnitChanged(boolean usesFahrenheit) {
        // no-op.
    }

    protected void setOffAndMaxButtonsActiveState(int level) {
        setOffButtonActive(level == 1);
        setMaxButtonActive(level == 6);
    }

    private void setMaxButtonActive(boolean active) {
        GradientDrawable background = (GradientDrawable) mMaxButton.getBackground();
        if (active) {
            background.setColor(mFanMaxActiveBgColor);
            mMaxButton.setTextColor(mButtonActiveTextColor);
        } else {
            background.setColor(Color.TRANSPARENT);
            mMaxButton.setTextColor(mButtonInactiveTextColor);
        }
    }

    private void setOffButtonActive(boolean active) {
        GradientDrawable background = (GradientDrawable) mOffButton.getBackground();
        if (active) {
            background.setColor(mFanOffActiveBgColor);
            mOffButton.setTextColor(mButtonActiveTextColor);
        } else {
            background.setColor(Color.TRANSPARENT);
            mOffButton.setTextColor(mButtonInactiveTextColor);
        }
    }

    private OnClickListener getOnClickListener(int fanSpeedLevel) {
        return v -> {
            if (shouldAllowControl()) {
                mHvacPropertySetter.setHvacProperty(HVAC_FAN_SPEED, getAreaId(), fanSpeedLevel);
            }
        };
    }

    private void updateViewPerAvailability() {
        setAlpha(shouldAllowControl() ? mOnAlpha : mOffAlpha);
    }

    private boolean shouldAllowControl() {
        return mPowerOn && !mAutoOn;
    }
}