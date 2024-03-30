/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.car.hardware.property;

import android.annotation.SystemApi;
import android.car.annotation.AddedInOrBefore;

/**
 * VehicleVendorPermission list all vendor permissions for vehicle. Vendors can map the vendor
 * properties with any vendor permission.
 * @hide
 */
@SystemApi
public final class VehicleVendorPermission {

    // permissions for the property related with window
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_GET_CAR_VENDOR_CATEGORY_WINDOW =
            "android.car.permission.GET_CAR_VENDOR_CATEGORY_WINDOW";
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_SET_CAR_VENDOR_CATEGORY_WINDOW =
            "android.car.permission.SET_CAR_VENDOR_CATEGORY_WINDOW";

    // permissions for the property related with door
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_GET_CAR_VENDOR_CATEGORY_DOOR =
            "android.car.permission.GET_CAR_VENDOR_CATEGORY_DOOR";
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_SET_CAR_VENDOR_CATEGORY_DOOR =
            "android.car.permission.SET_CAR_VENDOR_CATEGORY_DOOR";

    // permissions for the property related with seat
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_GET_CAR_VENDOR_CATEGORY_SEAT =
            "android.car.permission.GET_CAR_VENDOR_CATEGORY_SEAT";
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_SET_CAR_VENDOR_CATEGORY_SEAT =
            "android.car.permission.SET_CAR_VENDOR_CATEGORY_SEAT";

    // permissions for the property related with mirror
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_GET_CAR_VENDOR_CATEGORY_MIRROR =
            "android.car.permission.GET_CAR_VENDOR_CATEGORY_MIRROR";
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_SET_CAR_VENDOR_CATEGORY_MIRROR =
            "android.car.permission.SET_CAR_VENDOR_CATEGORY_MIRROR";

    // permissions for the property related with car's information
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_GET_CAR_VENDOR_CATEGORY_INFO =
            "android.car.permission.GET_CAR_VENDOR_CATEGORY_INFO";
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_SET_CAR_VENDOR_CATEGORY_INFO =
            "android.car.permission.SET_CAR_VENDOR_CATEGORY_INFO";

    // permissions for the property related with car's engine
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_GET_CAR_VENDOR_CATEGORY_ENGINE =
            "android.car.permission.GET_CAR_VENDOR_CATEGORY_ENGINE";
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_SET_CAR_VENDOR_CATEGORY_ENGINE =
            "android.car.permission.SET_CAR_VENDOR_CATEGORY_ENGINE";

    // permissions for the property related with car's HVAC
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_GET_CAR_VENDOR_CATEGORY_HVAC =
            "android.car.permission.GET_CAR_VENDOR_CATEGORY_HVAC";
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_SET_CAR_VENDOR_CATEGORY_HVAC =
            "android.car.permission.SET_CAR_VENDOR_CATEGORY_HVAC";

    // permissions for the property related with car's light
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_GET_CAR_VENDOR_CATEGORY_LIGHT =
            "android.car.permission.GET_CAR_VENDOR_CATEGORY_LIGHT";
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_SET_CAR_VENDOR_CATEGORY_LIGHT =
            "android.car.permission.SET_CAR_VENDOR_CATEGORY_LIGHT";


    // permissions reserved for other vendor permission
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_GET_CAR_VENDOR_CATEGORY_1 =
            "android.car.permission.GET_CAR_VENDOR_CATEGORY_1";
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_SET_CAR_VENDOR_CATEGORY_1 =
            "android.car.permission.SET_CAR_VENDOR_CATEGORY_1";
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_GET_CAR_VENDOR_CATEGORY_2 =
            "android.car.permission.GET_CAR_VENDOR_CATEGORY_2";
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_SET_CAR_VENDOR_CATEGORY_2 =
            "android.car.permission.SET_CAR_VENDOR_CATEGORY_2";
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_GET_CAR_VENDOR_CATEGORY_3 =
            "android.car.permission.GET_CAR_VENDOR_CATEGORY_3";
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_SET_CAR_VENDOR_CATEGORY_3 =
            "android.car.permission.SET_CAR_VENDOR_CATEGORY_3";
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_GET_CAR_VENDOR_CATEGORY_4 =
            "android.car.permission.GET_CAR_VENDOR_CATEGORY_4";
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_SET_CAR_VENDOR_CATEGORY_4 =
            "android.car.permission.SET_CAR_VENDOR_CATEGORY_4";
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_GET_CAR_VENDOR_CATEGORY_5 =
            "android.car.permission.GET_CAR_VENDOR_CATEGORY_5";
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_SET_CAR_VENDOR_CATEGORY_5 =
            "android.car.permission.SET_CAR_VENDOR_CATEGORY_5";
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_GET_CAR_VENDOR_CATEGORY_6 =
            "android.car.permission.GET_CAR_VENDOR_CATEGORY_6";
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_SET_CAR_VENDOR_CATEGORY_6 =
            "android.car.permission.SET_CAR_VENDOR_CATEGORY_6";
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_GET_CAR_VENDOR_CATEGORY_7 =
            "android.car.permission.GET_CAR_VENDOR_CATEGORY_7";
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_SET_CAR_VENDOR_CATEGORY_7 =
            "android.car.permission.SET_CAR_VENDOR_CATEGORY_7";
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_GET_CAR_VENDOR_CATEGORY_8 =
            "android.car.permission.GET_CAR_VENDOR_CATEGORY_8";
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_SET_CAR_VENDOR_CATEGORY_8 =
            "android.car.permission.SET_CAR_VENDOR_CATEGORY_8";
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_GET_CAR_VENDOR_CATEGORY_9 =
            "android.car.permission.GET_CAR_VENDOR_CATEGORY_9";
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_SET_CAR_VENDOR_CATEGORY_9 =
            "android.car.permission.SET_CAR_VENDOR_CATEGORY_9";
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_GET_CAR_VENDOR_CATEGORY_10 =
            "android.car.permission.GET_CAR_VENDOR_CATEGORY_10";
    @AddedInOrBefore(majorVersion = 33)
    public static final String PERMISSION_SET_CAR_VENDOR_CATEGORY_10 =
            "android.car.permission.SET_CAR_VENDOR_CATEGORY_10";

    private VehicleVendorPermission() {}

}
