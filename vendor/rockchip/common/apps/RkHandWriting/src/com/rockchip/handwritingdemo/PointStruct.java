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

import java.io.Serializable;

public class PointStruct implements Serializable {
    //Eraser Mode
    public static final int PEN_ERASER_DISABLE = 0;
    public static final int PEN_ERASER_ENABLE = 1;
    //Strokes Mode
    public static final int PEN_STROKES_DISABLE = 0;
    public static final int PEN_STROKES_ENABLE = 1;
    //Action Code
    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int ACTION_MOVE = 2;
    public static final int ACTION_TOOL_UP = 3;
    public static final int ACTION_OUT = 4;

    //Pen Color
    public static final int PEN_ALPHA_COLOR = 0;
    public static final int PEN_BLACK_COLOR = 1;
    public static final int PEN_WHITE_COLOR = 2;
    public static final int PEN_BLUE_COLOR = 3;
    public static final int PEN_GREEN_COLOR = 4;
    public static final int PEN_RED_COLOR = 5;
    public static final int PEN_WIDTH_DEFAULT = 4;
    public static final int ERASER_WIDTH_DEFAULT = 40;

    public int x;
    public int y;
    public int lastX;
    public int lastY;
    public float lastEndX;
    public float lastEndY;
    public float endX;
    public float endY;
    public int pressedValue;//压力
    public int penColor;//颜色
    public int penWidth;//宽度
    public int action;//类型
    public boolean eraserEnable;//是否使用橡皮擦
    public boolean strokesEnable;//是否使用笔锋
    public boolean smoothPenEnable;//是否使用平滑曲线（贝塞尔曲线）

    public PointStruct(int x, int y, int pressedValue, int action) {
        this.x = x;
        this.y = y;
        this.pressedValue = pressedValue;
        this.action = action;
    }

    public PointStruct(int lastX, int lastY, int x, int y, int pressedValue, int penColor,
                       int penWidth, int action, boolean eraserEnable, boolean strokesEnable) {
        this.lastX = lastX;
        this.lastY = lastY;
        this.x = x;
        this.y = y;
        this.pressedValue = pressedValue;
        this.penColor = penColor;
        this.penWidth = penWidth;
        this.action = action;
        this.eraserEnable = eraserEnable;
        this.strokesEnable = strokesEnable;
    }

    public PointStruct(int x, int y, int lastX, int lastY, int pressedValue, int penColor, int penWidth, int action) {
        this.x = x;
        this.y = y;
        this.lastX = lastX;
        this.lastY = lastY;
        this.pressedValue = pressedValue;
        this.penColor = penColor;
        this.penWidth = penWidth;
        this.action = action;
    }

    public PointStruct(int lastX, int lastY, int x, int y, float lastEndX, float lastEndY, float endX, float endY,
                       int pressedValue, int penColor, int penWidth, int action, boolean eraserEnable, boolean strokesEnable,
                       boolean smoothPenEnable) {
        this.lastX = lastX;
        this.lastY = lastY;
        this.x = x;
        this.y = y;
        this.lastEndX = lastEndX;
        this.lastEndY = lastEndY;
        this.endX = endX;
        this.endY = endY;
        this.pressedValue = pressedValue;
        this.penColor = penColor;
        this.penWidth = penWidth;
        this.action = action;
        this.eraserEnable = eraserEnable;
        this.strokesEnable = strokesEnable;
        this.smoothPenEnable = smoothPenEnable;
    }

    public PointStruct convertToAndroidPointStruct(PointStruct pointStruct) {
        PointStruct pointStructAndroid = pointStruct;
        //E人E本
        /*pointStructAndroid.lastY = MainActivity.mScreenH - pointStruct.lastY - MainActivity.mUIHeight;
        pointStructAndroid.y = MainActivity.mScreenH - pointStruct.y - MainActivity.mUIHeight;
        pointStructAndroid.lastEndY = MainActivity.mScreenH - pointStruct.lastEndY - MainActivity.mUIHeight;
        pointStructAndroid.endY = MainActivity.mScreenH - pointStruct.endY - MainActivity.mUIHeight;
        pointStructAndroid.lastX = MainActivity.mScreenW - pointStruct.lastX;
        pointStructAndroid.x = MainActivity.mScreenW - pointStruct.x;
        pointStructAndroid.lastEndX = MainActivity.mScreenW - pointStruct.lastEndX;
        pointStructAndroid.endX = MainActivity.mScreenW - pointStruct.endX;*/
        //3588平板
        pointStructAndroid.lastY = pointStruct.lastY - MainActivity2.mUIHeight;
        pointStructAndroid.y = pointStruct.y - MainActivity2.mUIHeight;
        pointStructAndroid.lastEndY = pointStruct.lastEndY - MainActivity2.mUIHeight;
        pointStructAndroid.endY = pointStruct.endY - MainActivity2.mUIHeight;
        return pointStructAndroid;
    }
}
