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

import android.util.Log;

import java.lang.reflect.Method;

public class Utils {

    public static Object invokeMethodNoParameter(Object object, String methodName) {
        try {
            Method method = object.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            return method.invoke(object);
        } catch (Exception e) {
            Log.e("RkHandWriting", "invokeMethod->methodName:" + methodName);
        }
        return null;
    }

    public static int ALIGN(int x, int a) {
        return (((x) + ((a) - 1)) & (~((a) - 1)));
    }
}
