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

package android.server.wm.scvh;

import static android.view.Display.DEFAULT_DISPLAY;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.server.wm.shared.ICrossProcessSurfaceControlViewHostTestService;
import android.util.ArrayMap;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.SurfaceControlViewHost;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.Map;


public class CrossProcessSurfaceControlViewHostTestService extends Service {
    private final ICrossProcessSurfaceControlViewHostTestService mBinder = new ServiceImpl();
    private Handler mHandler;

    class MotionRecordingView extends View {
        boolean mGotEvent = false;
        boolean mGotObscuredEvent = false;

        MotionRecordingView(Context c) {
            super(c);
        }

        public boolean onTouchEvent(MotionEvent e) {
            super.onTouchEvent(e);
            synchronized (this) {
                mGotEvent = true;
                if ((e.getFlags() & MotionEvent.FLAG_WINDOW_IS_OBSCURED) != 0) {
                    mGotObscuredEvent = true;
                }
            }
            return true;
        }

        boolean gotEvent() {
            synchronized (this) {
                return mGotEvent;
            }
        }

        boolean gotObscuredTouch() {
            synchronized (this) {
                return mGotObscuredEvent;
            }
        }

        void reset() {
            synchronized (this) {
                mGotEvent = false;
                mGotObscuredEvent = false;
            }
        }
    }
    @Override
    public void onCreate() {
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder.asBinder();
    }

    Display getDefaultDisplay() {
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        return wm.getDefaultDisplay();
    }

    SurfaceControlViewHost mSurfaceControlViewHost;
    MotionRecordingView mView;

    SurfaceControlViewHost.SurfacePackage createSurfacePackage(IBinder hostInputToken) {
        mView = new MotionRecordingView(this);
        mSurfaceControlViewHost = new SurfaceControlViewHost(this, getDefaultDisplay(), hostInputToken);
        mSurfaceControlViewHost.setView(mView, 100, 100);
        return mSurfaceControlViewHost.getSurfacePackage();
    }

    private class ServiceImpl extends ICrossProcessSurfaceControlViewHostTestService.Stub {
        private final CrossProcessSurfaceControlViewHostTestService mService =
            CrossProcessSurfaceControlViewHostTestService.this;

        private void drainHandler() {
            final CountDownLatch latch = new CountDownLatch(1);
            mHandler.post(() -> {
                latch.countDown();
            });
            try {
                latch.await(1, TimeUnit.SECONDS);
            } catch (Exception e) {
            }
        }

        @Override
        public SurfaceControlViewHost.SurfacePackage getSurfacePackage(IBinder hostInputToken) {
            final CountDownLatch latch = new CountDownLatch(1);
            mHandler.post(() -> {
                createSurfacePackage(hostInputToken);
                mView.getViewTreeObserver().registerFrameCommitCallback(latch::countDown);
                mView.invalidate();
            });
            try {
                latch.await(1, TimeUnit.SECONDS);
            } catch (Exception e) {
                return null;
            }
            return mSurfaceControlViewHost.getSurfacePackage();
        }

        @Override
        public boolean getViewIsTouched() {
            drainHandler();
            return mView.gotEvent();
        }

        @Override
        public boolean getViewIsTouchedAndObscured() {
            return getViewIsTouched() && mView.gotObscuredTouch();
        }

        @Override
        public void resetViewIsTouched() {
            drainHandler();
            mView.reset();
        }
    }
}
