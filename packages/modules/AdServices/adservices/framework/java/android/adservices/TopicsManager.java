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
package android.adservices;

import android.adservices.exceptions.AdServicesException;
import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.content.Context;
import android.os.OutcomeReceiver;
import android.os.RemoteException;

import com.android.adservices.AdServicesCommon;
import com.android.adservices.LogUtil;
import com.android.adservices.ServiceBinder;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Topics Manager.
 *
 * @hide
 */
public class TopicsManager {
    public static final String TOPICS_SERVICE = "topics_service";

    private final Context mContext;
    private final ServiceBinder<ITopicsService> mServiceBinder;

    /**
     * Create TopicsManager
     *
     * @hide
     */
    public TopicsManager(Context context) {
        mContext = context;
        mServiceBinder =
                ServiceBinder.getServiceBinder(
                        context,
                        AdServicesCommon.ACTION_TOPICS_SERVICE,
                        ITopicsService.Stub::asInterface);
    }

    @NonNull
    private ITopicsService getService() {
        ITopicsService service = mServiceBinder.getService();
        if (service == null) {
            throw new IllegalStateException("Unable to find the service");
        }
        return service;
    }

    /** Return the topics. */
    @NonNull
    public void getTopics(
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<GetTopicsResponse, AdServicesException> callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);
        final ITopicsService service = getService();

        try {
            service.getTopics(
                    new GetTopicsRequest.Builder()
                        .setAttributionSource(mContext.getAttributionSource())
                        .build(),
                    new IGetTopicsCallback.Stub() {
                        @Override
                        public void onResult(GetTopicsResponse resultParcel) {
                            executor.execute(
                                    () -> {
                                        callback.onResult(resultParcel);
                                    });
                        }
                    });
        } catch (RemoteException e) {
            LogUtil.e("RemoteException", e);
            callback.onError(new AdServicesException("Internal Error!"));
        }
    }

    /**
     * If the service is in an APK (as opposed to the system service), unbind it from the service to
     * allow the APK process to die.
     *
     * @hide Not sure if we'll need this functionality in the final API. For now, we need it for
     *     performance testing to simulate "cold-start" situations.
     */
    // TODO: change to @VisibleForTesting
    public void unbindFromService() {
        mServiceBinder.unbindFromService();
    }
}
