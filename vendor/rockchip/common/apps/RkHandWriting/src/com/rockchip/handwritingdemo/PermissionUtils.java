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
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * 关于申请授权
 * 只需要在主界面申请一次即可
 * 在其他子activity，自动授权
 */
public class PermissionUtils {
    //这是要申请的权限
/*    private static String[] PERMISSIONS_CAMERA = {Manifest.permission.CAMERA};
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,};*/

    /**
     * 解决安卓6.0以上版本不能读取外部存储权限的问题
     *
     * @param context
     * @param permissions
     * @return
     */
    public static boolean isGrantPermission(Context context, String[] permissions) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String permission : permissions) {
                int permissionFlag = context.checkSelfPermission(permission);
                if (permissionFlag != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        //说明已经授权
        return true;
    }
}
