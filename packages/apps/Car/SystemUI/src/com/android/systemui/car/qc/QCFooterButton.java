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

package com.android.systemui.car.qc;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Button;

import com.android.car.ui.utils.CarUxRestrictionsUtil;
import com.android.systemui.R;

import java.net.URISyntaxException;

/**
 * Footer button for quick control panels.
 *
 * Allows for an intent action to be specified via the {@link R.styleable.QCFooterButton_intent}
 * attribute and for enabled state to be set according to driving mode via the
 * {@link R.styleable.QCFooterButton_disableWhileDriving} attribute.
 */
public class QCFooterButton extends Button {
    private static final String TAG = "QCFooterButton";
    private final boolean mDisableWhileDriving;
    private final CarUxRestrictionsUtil.OnUxRestrictionsChangedListener mListener =
            carUxRestrictions -> setEnabled(!carUxRestrictions.isRequiresDistractionOptimization());

    public QCFooterButton(Context context) {
        this(context, /* attrs= */ null);
    }

    public QCFooterButton(Context context, AttributeSet attrs) {
        this(context, attrs, /* defStyleAttr= */ 0);
    }

    public QCFooterButton(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, /* defStyleRes= */ 0);
    }

    public QCFooterButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        if (attrs == null) {
            mDisableWhileDriving = false;
            return;
        }

        TypedArray typedArray = context.obtainStyledAttributes(attrs,
                R.styleable.QCFooterButton);
        String intentString = typedArray.getString(R.styleable.QCFooterButton_intent);
        if (intentString != null) {
            Intent intent;
            try {
                intent = Intent.parseUri(intentString, Intent.URI_INTENT_SCHEME);
            } catch (URISyntaxException e) {
                throw new RuntimeException("Failed to attach intent", e);
            }
            Intent finalIntent = intent;
            setOnClickListener(v -> {
                mContext.sendBroadcastAsUser(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS),
                        UserHandle.CURRENT);
                try {
                    ActivityOptions options = ActivityOptions.makeBasic();
                    options.setLaunchDisplayId(mContext.getDisplayId());
                    mContext.startActivityAsUser(finalIntent, options.toBundle(),
                            UserHandle.CURRENT);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to launch intent", e);
                }
            });
        }

        mDisableWhileDriving = typedArray.getBoolean(
                R.styleable.QCFooterButton_disableWhileDriving, /* defValue= */ false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mDisableWhileDriving) {
            CarUxRestrictionsUtil.getInstance(mContext).register(mListener);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mDisableWhileDriving) {
            CarUxRestrictionsUtil.getInstance(mContext).unregister(mListener);
        }
    }
}
