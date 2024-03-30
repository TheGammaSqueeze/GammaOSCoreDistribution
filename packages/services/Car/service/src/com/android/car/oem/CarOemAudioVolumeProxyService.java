/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.car.oem;

import android.annotation.NonNull;
import android.car.builtin.util.Slogf;
import android.car.oem.IOemCarAudioVolumeService;
import android.car.oem.OemCarAudioVolumeRequest;
import android.car.oem.OemCarVolumeChangeInfo;
import android.os.RemoteException;

import com.android.car.CarLog;
import com.android.internal.util.Preconditions;

/**
 * Provides functionality of the OEM Audio Volume Service.
 */
public final class CarOemAudioVolumeProxyService {

    private static final String TAG = CarLog.tagFor(CarOemAudioVolumeProxyService.class);
    private static final String CALLER_TAG = CarLog.tagFor(CarOemAudioVolumeProxyService.class);

    private final CarOemProxyServiceHelper mHelper;
    private final IOemCarAudioVolumeService mOemCarAudioVolumeService;

    public CarOemAudioVolumeProxyService(CarOemProxyServiceHelper helper,
            IOemCarAudioVolumeService oemAudioVolumeService) {
        mHelper = helper;
        mOemCarAudioVolumeService = oemAudioVolumeService;
    }

    /**
     * Call to evaluate a volume change.
     */
    @NonNull
    public OemCarVolumeChangeInfo getSuggestedGroupForVolumeChange(
            @NonNull OemCarAudioVolumeRequest requestInfo, int volumeAdjustment) {
        Preconditions.checkArgument(requestInfo != null,
                "Audio volume evaluation request can not be null");
        return mHelper.doBinderTimedCallWithDefaultValue(CALLER_TAG,
                () -> {
                    try {
                        return mOemCarAudioVolumeService
                                .getSuggestedGroupForVolumeChange(requestInfo, volumeAdjustment);
                    } catch (RemoteException e) {
                        Slogf.e(TAG, e, "Suggested group for volume Change with request "
                                + requestInfo);
                    }
                    return OemCarVolumeChangeInfo.EMPTY_OEM_VOLUME_CHANGE;
                }, OemCarVolumeChangeInfo.EMPTY_OEM_VOLUME_CHANGE);
    }
}
