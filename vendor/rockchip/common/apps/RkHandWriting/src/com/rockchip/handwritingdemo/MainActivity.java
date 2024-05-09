/*
 * Copyright 2023 Rockchip Electronics S.LSI Co. LTD
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

package com.rockchip.handwritingdemo;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity2";

    private View top_view;
    private HandWritingView mView;

    private RkHandWritingJNI mNativeJNI;
    private int mScreenHWithoutBar;
    private int mScreenH;
    private int mScreenW;
    private int mLeft;
    private int mTop;
    private int mRight;
    private int mBottom;
    private int mLayerStack = -1;
    private boolean mInitFlag;
    private boolean mResume;
    private Object mLock = new Object();
    private WindowManager mWm;
    private Handler mHandler = new Handler();

    private Runnable mRunnable = new Runnable() {
        public void run() {
            int count = 0;
            while (mView.getHeight() <= 0) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                if (count++ > 40) {
                    Log.d(TAG, "Flash test : ++++++++ removeCallbacks");
                    mHandler.removeCallbacks(mRunnable);
                    System.exit(0);
                }
                Log.d(TAG, "Flash test : ++++++++ mView.getHeight() = " + mView.getHeight() + ", count = " + count);
            }
            mLeft = 0;
            mTop = mScreenHWithoutBar - mView.getHeight();
            mRight = mScreenW;
            mBottom = mScreenHWithoutBar;
            Log.d(TAG, "mViewWidth:" + mView.getWidth() + "mViewHeight:" + mView.getHeight());
            synchronized (mLock) {
                if (mResume) {
                    mInitFlag = true;
                    /*DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
                    Display display = displayManager.getDisplays()[1];
                    mLayerStack = (int) Utils.invokeMethodNoParameter(display, "getLayerStack");
                    Log.w(TAG, "mLayerStack=" + mLayerStack);*/
                    mView.init(new Rect(mLeft, mTop, mRight, mBottom), mScreenW, mScreenH, mTop, mLayerStack);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        top_view = findViewById(R.id.top_view);
        top_view.setVisibility(View.GONE);
        mView = (HandWritingView) findViewById(R.id.handwriting_view);

        mWm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        mNativeJNI = new RkHandWritingJNI();
        getScreenWH();
    }

    @Override
    protected void onResume() {
        super.onResume();
        synchronized (mLock) {
            Log.d(TAG, "onResume()");
            mResume = true;
        }
        mHandler.postDelayed(mRunnable, 100);
    }

    @Override
    public void onPause() {
        super.onPause();
        synchronized (mLock) {
            mResume = false;
            Log.d(TAG, "onPause(), mInitFlag=" + mInitFlag);
            if (mInitFlag) {
                mView.clear();
                mNativeJNI.clear();
                mNativeJNI.exit();
            }
        }

    }

    private void getScreenWH() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        mScreenW = metrics.widthPixels;
        mScreenHWithoutBar = metrics.heightPixels;
        Display display = mWm.getDefaultDisplay();
        DisplayMetrics dm = new DisplayMetrics();
        display.getRealMetrics(dm);
        mScreenH = dm.heightPixels;
        Log.w(TAG, "screenW=" + mScreenW + ", screenH=" + mScreenH
                + ", screenHWithoutBar" + mScreenHWithoutBar);
    }
}
