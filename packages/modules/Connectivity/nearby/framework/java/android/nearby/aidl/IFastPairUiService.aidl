/*
 * Copyright (C) 2022, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.nearby.aidl;

import android.nearby.aidl.IFastPairStatusCallback;
import android.nearby.FastPairDevice;

/**
 * 0p API for controlling Fast Pair. Used to talk between foreground activities
 * and background services.
 *
 * {@hide}
 */
interface IFastPairUiService {

    void registerCallback(in IFastPairStatusCallback fastPairStatusCallback);

    void unregisterCallback(in IFastPairStatusCallback fastPairStatusCallback);

    void connect(in FastPairDevice fastPairDevice);

    void cancel(in FastPairDevice fastPairDevice);
}