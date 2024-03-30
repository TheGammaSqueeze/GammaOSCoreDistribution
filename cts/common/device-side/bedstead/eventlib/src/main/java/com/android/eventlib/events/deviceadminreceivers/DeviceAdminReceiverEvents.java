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

package com.android.eventlib.events.deviceadminreceivers;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PersistableBundle;
import android.os.UserHandle;

/**
 * Quick access to event queries about device admin receivers.
 */
public interface DeviceAdminReceiverEvents {

    /**
     * Query for when {@link DeviceAdminReceiver#onBugreportFailed(Context, Intent, int)} is called
     * on a device admin receiver.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    DeviceAdminBugreportFailedEvent.DeviceAdminBugreportFailedEventQuery bugReportFailed();

    /**
     * Query for when {@link DeviceAdminReceiver#onBugreportShared(Context, Intent, String)}  is
     * called on a device admin receiver.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    DeviceAdminBugreportSharedEvent.DeviceAdminBugreportSharedEventQuery bugReportShared();

    /**
     * Query for when {@link DeviceAdminReceiver#onBugreportSharingDeclined(Context, Intent)}  is
     * called on a device admin receiver.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    DeviceAdminBugreportSharingDeclinedEvent.DeviceAdminBugreportSharingDeclinedEventQuery bugReportSharingDeclined();

    /**
     * Query for when {@link DeviceAdminReceiver#onChoosePrivateKeyAlias(Context, Intent, int, Uri, String)}
     * is called on a device admin receiver.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    DeviceAdminChoosePrivateKeyAliasEvent.DeviceAdminChoosePrivateKeyAliasEventQuery choosePrivateKeyAlias();

    /**
     * Query for when {@link DeviceAdminReceiver#onDisabled(Context, Intent)} is called on a
     * device admin receiver.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    DeviceAdminDisabledEvent.DeviceAdminDisabledEventQuery deviceAdminDisabled();

    /**
     * Query for when {@link DeviceAdminReceiver#onDisableRequested(Context, Intent)} is called on
     * a device admin receiver.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    DeviceAdminDisableRequestedEvent.DeviceAdminDisableRequestedEventQuery deviceAdminDisableRequested();

    /**
     * Query for when {@link DeviceAdminReceiver#onEnabled(Context, Intent)} is called on a device
     * admin receiver.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    DeviceAdminEnabledEvent.DeviceAdminEnabledEventQuery deviceAdminEnabled();

    /**
     * Query for when {@link DeviceAdminReceiver#onLockTaskModeEntering(Context, Intent, String)}
     * is called on a device admin receiver.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    DeviceAdminLockTaskModeEnteringEvent.DeviceAdminLockTaskModeEnteringEventQuery lockTaskModeEntering();

    /**
     * Query for when {@link DeviceAdminReceiver#onLockTaskModeExiting(Context, Intent)} is called
     * on a device admin receiver.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    DeviceAdminLockTaskModeExitingEvent.DeviceAdminLockTaskModeExitingEventQuery lockTaskModeExiting();

    /**
     * Query for when {@link DeviceAdminReceiver#onNetworkLogsAvailable(Context, Intent, long, int)}
     * is called on a device admin receiver.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    DeviceAdminNetworkLogsAvailableEvent.DeviceAdminNetworkLogsAvailableEventQuery networkLogsAvailable();

    /**
     * Query for when {@link DeviceAdminReceiver#onOperationSafetyStateChanged(Context, Intent)} is
     * called on a device admin receiver.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    DeviceAdminOperationSafetyStateChangedEvent.DeviceAdminOperationSafetyStateChangedEventQuery operationSafetyStateChanged();

    /**
     * Query for when {@link DeviceAdminReceiver#onPasswordChanged(Context, Intent, UserHandle)} is
     * called on a device admin receiver.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    DeviceAdminPasswordChangedEvent.DeviceAdminPasswordChangedEventQuery passwordChanged();

    /**
     * Query for when {@link DeviceAdminReceiver#onPasswordExpiring(Context, Intent, UserHandle)}
     * is called on a device admin receiver.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    DeviceAdminPasswordExpiringEvent.DeviceAdminPasswordExpiringEventQuery passwordExpiring();

    /**
     * Query for when {@link DeviceAdminReceiver#onPasswordFailed(Context, Intent, UserHandle)} is
     * called on a device admin receiver.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    DeviceAdminPasswordFailedEvent.DeviceAdminPasswordFailedEventQuery passwordFailed();

    /**
     * Query for when {@link DeviceAdminReceiver#onPasswordSucceeded(Context, Intent, UserHandle)}
     * is called on a device admin receiver.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    DeviceAdminPasswordSucceededEvent.DeviceAdminPasswordSucceededEventQuery passwordSucceeded();

    /**
     * Query for when {@link DeviceAdminReceiver#onProfileProvisioningComplete(Context, Intent)} is
     * called on a device admin receiver.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    DeviceAdminProfileProvisioningCompleteEvent.DeviceAdminProfileProvisioningCompleteEventQuery profileProvisioningComplete();

    /**
     * Query for when {@link DeviceAdminReceiver#onReadyForUserInitialization(Context, Intent)} is
     * called on a device admin receiver.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    DeviceAdminReadyForUserInitializationEvent.DeviceAdminReadyForUserInitializationEventQuery readyForUserInitialization();

    /**
     * Query for when {@link DeviceAdminReceiver#onSecurityLogsAvailable(Context, Intent)} is called
     * on a device admin receiver.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    DeviceAdminSecurityLogsAvailableEvent.DeviceAdminSecurityLogsAvailableEventQuery securityLogsAvailable();

    /**
     * Query for when {@link DeviceAdminReceiver#onSystemUpdatePending(Context, Intent, long)} is
     * called on a device admin receiver.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    DeviceAdminSystemUpdatePendingEvent.DeviceAdminSystemUpdatePendingEventQuery systemUpdatePending();

    /**
     * Query for when {@link DeviceAdminReceiver#onTransferAffiliatedProfileOwnershipComplete(Context, UserHandle)}
     * is called on a device admin receiver.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    DeviceAdminTransferAffiliatedProfileOwnershipCompleteEvent.DeviceAdminTransferAffiliatedProfileOwnershipCompleteEventQuery transferAffiliatedProfileOwnershipComplete();

    /**
     * Query for when {@link DeviceAdminReceiver#onTransferOwnershipComplete(Context, PersistableBundle)}
     * is called on a device admin receiver.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    DeviceAdminTransferOwnershipCompleteEvent.DeviceAdminTransferOwnershipCompleteEventQuery transferOwnershipComplete();

    /**
     * Query for when {@link DeviceAdminReceiver#onUserAdded(Context, Intent, UserHandle)} is called
     * on a device admin receiver.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    DeviceAdminUserAddedEvent.DeviceAdminUserAddedEventQuery userAdded();

    /**
     * Query for when {@link DeviceAdminReceiver#onUserRemoved(Context, Intent, UserHandle)} is
     * called on a device admin receiver.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    DeviceAdminUserRemovedEvent.DeviceAdminUserRemovedEventQuery userRemoved();

    /**
     * Query for when {@link DeviceAdminReceiver#onUserStarted(Context, Intent, UserHandle)} is
     * called on a device admin receiver.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    DeviceAdminUserStartedEvent.DeviceAdminUserStartedEventQuery userStarted();

    /**
     * Query for when {@link DeviceAdminReceiver#onUserStopped(Context, Intent, UserHandle)} is
     * called on a device admin receiver.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    DeviceAdminUserStoppedEvent.DeviceAdminUserStoppedEventQuery userStopped();

    /**
     * Query for when {@link DeviceAdminReceiver#onUserSwitched(Context, Intent, UserHandle)} is
     * called on a device admin receiver.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    DeviceAdminUserSwitchedEvent.DeviceAdminUserSwitchedEventQuery userSwitched();
}
