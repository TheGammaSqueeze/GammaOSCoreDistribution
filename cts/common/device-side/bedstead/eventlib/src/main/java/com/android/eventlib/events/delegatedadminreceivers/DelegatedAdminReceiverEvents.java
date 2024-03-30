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

package com.android.eventlib.events.delegatedadminreceivers;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.android.eventlib.events.delegatedadminreceivers.DelegatedAdminChoosePrivateKeyAliasEvent.DelegatedAdminChoosePrivateKeyAliasEventQuery;
import com.android.eventlib.events.delegatedadminreceivers.DelegatedAdminSecurityLogsAvailableEvent.DelegatedAdminSecurityLogsAvailableEventQuery;
import com.android.eventlib.events.deviceadminreceivers.DelegatedAdminNetworkLogsAvailableEvent.DelegatedAdminNetworkLogsAvailableEventQuery;

/**
 * Quick access to event queries about device admin receivers.
 */
public interface DelegatedAdminReceiverEvents {

    /**
     * Query for when {@link DeviceAdminReceiver#onChoosePrivateKeyAlias(Context, Intent, int, Uri, String)}
     * is called on a device admin receiver.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    DelegatedAdminChoosePrivateKeyAliasEventQuery delegateChoosePrivateKeyAlias();

    /**
     * Query for when {@link DeviceAdminReceiver#onNetworkLogsAvailable(Context, Intent, long, int)}
     * is called on a device admin receiver.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    DelegatedAdminNetworkLogsAvailableEventQuery delegateNetworkLogsAvailable();

    /**
     * Query for when {@link DeviceAdminReceiver#onSecurityLogsAvailable(Context, Intent)} is called
     * on a device admin receiver.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    DelegatedAdminSecurityLogsAvailableEventQuery delegateSecurityLogsAvailable();
}
