/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.odpclient;

import android.app.Activity;
import android.content.Context;
import android.ondevicepersonalization.OnDevicePersonalizationManager;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;

public class MainActivity extends Activity {
    private OnDevicePersonalizationManager mOdpManager = null;

    private Button mBindButton;
    private SurfaceView mRenderedView;

    private Context mContext;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = getApplicationContext();
        if (mOdpManager == null) {
            mOdpManager = new OnDevicePersonalizationManager(mContext);
        }

        mRenderedView = findViewById(R.id.rendered_view);
        mRenderedView.setZOrderOnTop(true);
        mRenderedView.setVisibility(View.INVISIBLE);
        mBindButton = findViewById(R.id.bind_service_button);
        registerBindServiceButton();
    }

    private void registerBindServiceButton() {
        mBindButton.setOnClickListener(v -> {
            if (mOdpManager == null) {
                mBindButton.setText("OnDevicePersonalizationManager is null");
            } else {
                mBindButton.setText(mOdpManager.getVersion());
            }
        });
    }
}
