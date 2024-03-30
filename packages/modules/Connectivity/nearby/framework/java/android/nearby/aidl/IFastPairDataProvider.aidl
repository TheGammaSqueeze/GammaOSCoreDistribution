// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package android.nearby.aidl;

import android.nearby.aidl.FastPairAntispoofKeyDeviceMetadataRequestParcel;
import android.nearby.aidl.IFastPairAntispoofKeyDeviceMetadataCallback;
import android.nearby.aidl.FastPairAccountDevicesMetadataRequestParcel;
import android.nearby.aidl.IFastPairAccountDevicesMetadataCallback;
import android.nearby.aidl.FastPairEligibleAccountsRequestParcel;
import android.nearby.aidl.IFastPairEligibleAccountsCallback;
import android.nearby.aidl.FastPairManageAccountRequestParcel;
import android.nearby.aidl.IFastPairManageAccountCallback;
import android.nearby.aidl.FastPairManageAccountDeviceRequestParcel;
import android.nearby.aidl.IFastPairManageAccountDeviceCallback;

/**
 * Interface for communicating with the fast pair providers.
 *
 * {@hide}
 */
oneway interface IFastPairDataProvider {
    void loadFastPairAntispoofKeyDeviceMetadata(in FastPairAntispoofKeyDeviceMetadataRequestParcel request,
        in IFastPairAntispoofKeyDeviceMetadataCallback callback);
    void loadFastPairAccountDevicesMetadata(in FastPairAccountDevicesMetadataRequestParcel request,
        in IFastPairAccountDevicesMetadataCallback callback);
    void loadFastPairEligibleAccounts(in FastPairEligibleAccountsRequestParcel request,
        in IFastPairEligibleAccountsCallback callback);
    void manageFastPairAccount(in FastPairManageAccountRequestParcel request,
        in IFastPairManageAccountCallback callback);
    void manageFastPairAccountDevice(in FastPairManageAccountDeviceRequestParcel request,
        in IFastPairManageAccountDeviceCallback callback);
}
