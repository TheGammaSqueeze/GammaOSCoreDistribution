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

package android.nearby;

import android.annotation.NonNull;
import android.annotation.SystemApi;

/**
 * Reports newly discovered devices.
 * Note: The frequency of the callback is dependent on whether the caller
 * is in the foreground or background. Foreground callbacks will occur
 * as fast as the underlying medium supports, whereas background
 * use cases will be rate limited to improve performance (ie, only on
 * found/lost/significant changes).
 *
 * @hide
 */
@SystemApi
public interface ScanCallback {
    /**
     * Reports a {@link NearbyDevice} being discovered.
     *
     * @param device {@link NearbyDevice} that is found.
     */
    void onDiscovered(@NonNull NearbyDevice device);

    /**
     * Reports a {@link NearbyDevice} information(distance, packet, and etc) changed.
     *
     * @param device {@link NearbyDevice} that has updates.
     */
    void onUpdated(@NonNull NearbyDevice device);

    /**
     * Reports a {@link NearbyDevice} is no longer within range.
     *
     * @param device {@link NearbyDevice} that is lost.
     */
    void onLost(@NonNull NearbyDevice device);
}
