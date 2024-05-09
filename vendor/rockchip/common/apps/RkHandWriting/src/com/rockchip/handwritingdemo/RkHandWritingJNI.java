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

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;

public class RkHandWritingJNI {
    private static Handler mPointHandler = null;
    public static final int MSG_FLAG_DRAW = 0;

    //JNI
    public native int native_init(Rect rect,
                                  int viewWidth, int viewHeight, int screenWidth, int screenHeight,
                                  int layerStack);

    public native int native_clear();

    public native int native_exit();

    public native int native_draw_bitmap(Bitmap bitmap);

    public RkHandWritingJNI() {
        System.loadLibrary("rkhandwriting");
    }

    public static void receiveWritingDataEvent(int lastX, int lastY, int x, int y, int pressedValue, int penColor,
                                               int penWidth, int action, boolean isEraserEnable,
                                               boolean isStrokesEnable) {
        //Log.d(TAG, "receiveWritingDataEvent lastX:" + lastX + ",lastY:" + lastY +
        //",x:" + x + ",y:" + y + ",action:" + action + ",penColor:" + penColor);
        PointStruct pointStruct = new PointStruct(lastX, lastY, x, y, pressedValue, penColor,
                penWidth, action, isEraserEnable, isStrokesEnable);
        PointStruct pointStructAndroid = pointStruct.convertToAndroidPointStruct(pointStruct);
        Message msg = mPointHandler.obtainMessage();
        msg.what = MSG_FLAG_DRAW;
        msg.obj = (Object) pointStructAndroid;
        msg.sendToTarget();
    }

    public void setPointHandler(Handler handler) {
        mPointHandler = handler;
    }

    public int init(Rect rect,
                    int viewWidth, int viewHeight, int screenWidth, int screenHeight, int layerStack) {
        return native_init(rect, viewWidth, viewHeight, screenWidth, screenHeight, layerStack);
    }

    public void clear() {
        native_clear();
    }

    public void exit() {
        native_exit();
    }

    public void drawBitmap(Bitmap bitmap) {
        native_draw_bitmap(bitmap);
    }

}
