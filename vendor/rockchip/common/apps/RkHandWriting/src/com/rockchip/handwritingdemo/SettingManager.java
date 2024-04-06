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

import android.content.Context;

import java.lang.reflect.Method;

public class SettingManager {
    private Context mContext;

    public SettingManager() {

    }

    public SettingManager(Context context) {
        mContext = context;
    }

    public static String getProperty(String key) {
        String value = "";
        try {
            Class c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class);
            value = (String) (get.invoke(c, key));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            return value;
        }
    }

    public void setProperty(String key, String property) {
        try {
            Class c = Class.forName("android.os.SystemProperties");
            Method set = c.getMethod("set", String.class, String.class);
            set.invoke(c, key, property);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
