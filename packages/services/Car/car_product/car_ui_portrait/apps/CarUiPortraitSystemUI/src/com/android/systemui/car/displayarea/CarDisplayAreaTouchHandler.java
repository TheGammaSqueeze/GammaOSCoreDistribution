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

package com.android.systemui.car.displayarea;

import static android.view.Display.DEFAULT_DISPLAY;

import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.os.Looper;
import android.view.InputChannel;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.InputMonitor;
import android.view.MotionEvent;

import com.android.wm.shell.common.ShellExecutor;

/**
 * Manages all the touch handling for display area, including user tap outside region
 * to exit.
 */
public class CarDisplayAreaTouchHandler {
    private static final int CLICK_ACTION_THRESHOLD = 10;

    private final ShellExecutor mMainExecutor;

    private InputMonitor mInputMonitor;
    private InputEventReceiver mInputEventReceiver;
    private OnClickDisplayAreaListener mOnClickDisplayAreaListener;
    private OnDragDisplayAreaListener mOnTouchTitleBarListener;
    private boolean mIsEnabled;
    private float mStartX;
    private float mStartY;
    private Rect mTitleBarBounds;
    private boolean mIsTitleBarVisible;
    private boolean mIsTitleBarDragged;

    public CarDisplayAreaTouchHandler(ShellExecutor mainExecutor) {
        mMainExecutor = mainExecutor;
    }

    /**
     * Notified by {@link CarDisplayAreaController}, to update settings of Enabled or Disabled.
     */
    public void enable(boolean isEnabled) {
        mIsEnabled = isEnabled;
        updateIsEnabled();
    }

    /**
     * Register {@link OnClickDisplayAreaListener} to receive onClick() callback
     */
    public void registerOnClickListener(OnClickDisplayAreaListener listener) {
        mOnClickDisplayAreaListener = listener;
    }

    /**
     * Register {@link OnDragDisplayAreaListener} to receive onTouch() callbacks
     */
    public void registerTouchEventListener(OnDragDisplayAreaListener listener) {
        mOnTouchTitleBarListener = listener;
    }

    /**
     * Updated whether the titleBar is visible or not.
     */
    public void updateTitleBarVisibility(boolean isTitleBarVisible) {
        mIsTitleBarVisible = isTitleBarVisible;
    }

    public void setTitleBarBounds(Rect titleBarBounds) {
        mTitleBarBounds = titleBarBounds;
    }

    private void onMotionEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mStartX = event.getX();
                mStartY = event.getY();
                if (mOnTouchTitleBarListener != null && mIsTitleBarVisible) {
                    mOnTouchTitleBarListener.onStart(mStartX, mStartY);
                }
                mIsTitleBarDragged = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (mIsTitleBarVisible && isTitleBarGrabbed() && mOnTouchTitleBarListener != null) {
                    mOnTouchTitleBarListener.onMove(event.getX(), event.getY());
                    mIsTitleBarDragged = true;
                }

                break;
            case MotionEvent.ACTION_UP:
                float endX = event.getX();
                float endY = event.getY();
                // TODO: use utility functions from gesture class.
                if (isAClick(mStartX, endX, mStartY, endY)) {
                    mOnClickDisplayAreaListener.onClick(endX, endY);
                }
                if (mIsTitleBarDragged && mOnTouchTitleBarListener != null) {
                    mOnTouchTitleBarListener.onFinish(event.getX(), event.getY());
                }
                mIsTitleBarDragged = false;
                break;
            default:
                mIsTitleBarDragged = false;
        }
    }

    private static boolean isAClick(float startX, float endX, float startY, float endY) {
        float differenceX = Math.abs(startX - endX);
        float differenceY = Math.abs(startY - endY);
        return !(differenceX > CLICK_ACTION_THRESHOLD || differenceY > CLICK_ACTION_THRESHOLD);
    }

    private boolean isTitleBarGrabbed() {
        return mStartX >= mTitleBarBounds.left && mStartX <= mTitleBarBounds.right
                && mStartY >= mTitleBarBounds.top && mStartY <= mTitleBarBounds.bottom;
    }

    private void disposeInputChannel() {
        if (mInputEventReceiver != null) {
            mInputEventReceiver.dispose();
            mInputEventReceiver = null;
        }
        if (mInputMonitor != null) {
            mInputMonitor.dispose();
            mInputMonitor = null;
        }
    }

    private void onInputEvent(InputEvent ev) {
        if (ev instanceof MotionEvent) {
            onMotionEvent((MotionEvent) ev);
        }
    }

    private void updateIsEnabled() {
        disposeInputChannel();
        if (mIsEnabled) {
            mInputMonitor = InputManager.getInstance().monitorGestureInput(
                    "car-display-area-touch", DEFAULT_DISPLAY);
            try {
                mMainExecutor.executeBlocking(() -> {
                    mInputEventReceiver = new EventReceiver(
                            mInputMonitor.getInputChannel(), Looper.myLooper());
                });
            } catch (InterruptedException e) {
                throw new RuntimeException("Failed to create input event receiver", e);
            }
        }
    }

    private class EventReceiver extends InputEventReceiver {
        EventReceiver(InputChannel channel, Looper looper) {
            super(channel, looper);
        }

        public void onInputEvent(InputEvent event) {
            CarDisplayAreaTouchHandler.this.onInputEvent(event);
            finishInputEvent(event, true);
        }
    }

    /**
     * Callback invoked when a user clicks anywhere on the display area.
     */
    interface OnClickDisplayAreaListener {

        /**
         * Called when a user clicks on the display area. Returns the co-ordinate of the click.
         */
        void onClick(float x, float y);
    }

    /**
     * Callback invoked when a user touches anywhere on the display area.
     */
    interface OnDragDisplayAreaListener {

        /**
         * Called for ACTION_MOVE touch events on the title bar. Returns the co-ordinate of the
         * touch. This is only called when the title bar is visible.
         */
        void onMove(float x, float y);

        /**
         * Called for ACTION_UP touch events on the title bar. Returns the co-ordinate of the
         * touch. This is only called when the title bar is visible.
         */
        void onFinish(float x, float y);

        /**
         * Called for ACTION_DOWN touch events on the title bar. Returns the co-ordinate of the
         * touch. This is only called when the title bar is visible.
         */
        void onStart(float x, float y);
    }
}
