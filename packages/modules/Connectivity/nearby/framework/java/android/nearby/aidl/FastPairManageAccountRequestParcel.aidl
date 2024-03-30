/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.nearby.aidl;

import android.accounts.Account;

/**
 * Request details for managing a Fast Pair account.
 *
 * {@hide}
 */
parcelable FastPairManageAccountRequestParcel {
    Account account;
    // MANAGE_ACCOUNT_OPT_IN: opt account into Fast Pair.
    // MANAGE_ACCOUNT_OPT_OUT: opt account out of Fast Pair.
    int requestType;
}