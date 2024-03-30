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
package com.android.adservices.service.topics;

import android.adservices.GetTopicsRequest;
import android.adservices.GetTopicsResponse;
import android.adservices.IGetTopicsCallback;
import android.adservices.ITopicsService;
import android.annotation.NonNull;
import android.content.Context;
import android.os.RemoteException;

import com.android.adservices.LogUtil;

import java.util.Arrays;

/**
 * Implementation of {@link ITopicsService}.
 *
 * @hide
 */
public class TopicsServiceImpl extends ITopicsService.Stub {
    private final Context mContext;

    public TopicsServiceImpl(Context context) {
        mContext = context;
    }

    @Override
    public void getTopics(@NonNull GetTopicsRequest topicsParams,
            @NonNull IGetTopicsCallback callback) {
        // TODO: Implement!
        try {
            callback.onResult(
                    new GetTopicsResponse.Builder()
                            .setTaxonomyVersions(Arrays.asList(1L, 2L))
                            .setModelVersions(Arrays.asList(3L, 4L))
                            .setTopics(Arrays.asList("topic1", "topic2"))
                            .build());
        } catch (RemoteException e) {
            LogUtil.e("Unable to send result to the callback", e);
        }
    }
}
