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

package com.android.bedstead.testapp;

import com.android.eventlib.events.activities.ActivityCreatedEvent;
import com.android.eventlib.events.activities.ActivityDestroyedEvent;
import com.android.eventlib.events.activities.ActivityEvents;
import com.android.eventlib.events.activities.ActivityPausedEvent;
import com.android.eventlib.events.activities.ActivityRestartedEvent;
import com.android.eventlib.events.activities.ActivityResumedEvent;
import com.android.eventlib.events.activities.ActivityStartedEvent;
import com.android.eventlib.events.activities.ActivityStoppedEvent;
import com.android.eventlib.events.broadcastreceivers.BroadcastReceivedEvent;
import com.android.eventlib.events.broadcastreceivers.BroadcastReceiverEvents;
import com.android.eventlib.events.delegatedadminreceivers.DelegatedAdminChoosePrivateKeyAliasEvent;
import com.android.eventlib.events.delegatedadminreceivers.DelegatedAdminChoosePrivateKeyAliasEvent.DelegatedAdminChoosePrivateKeyAliasEventQuery;
import com.android.eventlib.events.delegatedadminreceivers.DelegatedAdminReceiverEvents;
import com.android.eventlib.events.delegatedadminreceivers.DelegatedAdminSecurityLogsAvailableEvent;
import com.android.eventlib.events.delegatedadminreceivers.DelegatedAdminSecurityLogsAvailableEvent.DelegatedAdminSecurityLogsAvailableEventQuery;
import com.android.eventlib.events.deviceadminreceivers.DelegatedAdminNetworkLogsAvailableEvent;
import com.android.eventlib.events.deviceadminreceivers.DelegatedAdminNetworkLogsAvailableEvent.DelegatedAdminNetworkLogsAvailableEventQuery;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminBugreportFailedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminBugreportSharedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminBugreportSharingDeclinedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminBugreportSharingDeclinedEvent.DeviceAdminBugreportSharingDeclinedEventQuery;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminChoosePrivateKeyAliasEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminChoosePrivateKeyAliasEvent.DeviceAdminChoosePrivateKeyAliasEventQuery;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminDisableRequestedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminDisableRequestedEvent.DeviceAdminDisableRequestedEventQuery;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminDisabledEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminEnabledEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminLockTaskModeEnteringEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminLockTaskModeEnteringEvent.DeviceAdminLockTaskModeEnteringEventQuery;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminLockTaskModeExitingEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminLockTaskModeExitingEvent.DeviceAdminLockTaskModeExitingEventQuery;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminNetworkLogsAvailableEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminNetworkLogsAvailableEvent.DeviceAdminNetworkLogsAvailableEventQuery;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminOperationSafetyStateChangedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminOperationSafetyStateChangedEvent.DeviceAdminOperationSafetyStateChangedEventQuery;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminPasswordChangedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminPasswordExpiringEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminPasswordExpiringEvent.DeviceAdminPasswordExpiringEventQuery;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminPasswordFailedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminPasswordSucceededEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminPasswordSucceededEvent.DeviceAdminPasswordSucceededEventQuery;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminProfileProvisioningCompleteEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminProfileProvisioningCompleteEvent.DeviceAdminProfileProvisioningCompleteEventQuery;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminReadyForUserInitializationEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminReadyForUserInitializationEvent.DeviceAdminReadyForUserInitializationEventQuery;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminReceiverEvents;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminSecurityLogsAvailableEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminSecurityLogsAvailableEvent.DeviceAdminSecurityLogsAvailableEventQuery;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminSystemUpdatePendingEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminSystemUpdatePendingEvent.DeviceAdminSystemUpdatePendingEventQuery;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminTransferAffiliatedProfileOwnershipCompleteEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminTransferAffiliatedProfileOwnershipCompleteEvent.DeviceAdminTransferAffiliatedProfileOwnershipCompleteEventQuery;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminTransferOwnershipCompleteEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminTransferOwnershipCompleteEvent.DeviceAdminTransferOwnershipCompleteEventQuery;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserAddedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserRemovedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserStartedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserStoppedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserSwitchedEvent;
import com.android.eventlib.events.services.ServiceBoundEvent;
import com.android.eventlib.events.services.ServiceConfigurationChangedEvent;
import com.android.eventlib.events.services.ServiceCreatedEvent;
import com.android.eventlib.events.services.ServiceDestroyedEvent;
import com.android.eventlib.events.services.ServiceEvents;
import com.android.eventlib.events.services.ServiceLowMemoryEvent;
import com.android.eventlib.events.services.ServiceMemoryTrimmedEvent;
import com.android.eventlib.events.services.ServiceReboundEvent;
import com.android.eventlib.events.services.ServiceStartedEvent;
import com.android.eventlib.events.services.ServiceTaskRemovedEvent;
import com.android.eventlib.events.services.ServiceUnboundEvent;

/**
 * Quick access to events on this test app.
 *
 * <p>Additional filters can be added to the returned object.
 *
 * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
 */
public class TestAppEvents implements ActivityEvents, BroadcastReceiverEvents,
        DeviceAdminReceiverEvents, DelegatedAdminReceiverEvents, ServiceEvents {

    private final TestAppInstance mTestApp;

    TestAppEvents(TestAppInstance testApp) {
        mTestApp = testApp;
    }

    @Override
    public ActivityCreatedEvent.ActivityCreatedEventQuery activityCreated() {
        return ActivityCreatedEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public ActivityDestroyedEvent.ActivityDestroyedEventQuery activityDestroyed() {
        return ActivityDestroyedEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public ActivityPausedEvent.ActivityPausedEventQuery activityPaused() {
        return ActivityPausedEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public ActivityRestartedEvent.ActivityRestartedEventQuery activityRestarted() {
        return ActivityRestartedEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public ActivityResumedEvent.ActivityResumedEventQuery activityResumed() {
        return ActivityResumedEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public ActivityStartedEvent.ActivityStartedEventQuery activityStarted() {
        return ActivityStartedEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public ActivityStoppedEvent.ActivityStoppedEventQuery activityStopped() {
        return ActivityStoppedEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public BroadcastReceivedEvent.BroadcastReceivedEventQuery broadcastReceived() {
        return BroadcastReceivedEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public DeviceAdminBugreportFailedEvent.DeviceAdminBugreportFailedEventQuery bugReportFailed() {
        return DeviceAdminBugreportFailedEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public DeviceAdminBugreportSharedEvent.DeviceAdminBugreportSharedEventQuery bugReportShared() {
        return DeviceAdminBugreportSharedEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public DeviceAdminBugreportSharingDeclinedEventQuery bugReportSharingDeclined() {
        return DeviceAdminBugreportSharingDeclinedEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public DeviceAdminChoosePrivateKeyAliasEventQuery choosePrivateKeyAlias() {
        return DeviceAdminChoosePrivateKeyAliasEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public DeviceAdminDisabledEvent.DeviceAdminDisabledEventQuery deviceAdminDisabled() {
        return DeviceAdminDisabledEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public DeviceAdminDisableRequestedEventQuery deviceAdminDisableRequested() {
        return DeviceAdminDisableRequestedEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public DeviceAdminEnabledEvent.DeviceAdminEnabledEventQuery deviceAdminEnabled() {
        return DeviceAdminEnabledEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public DeviceAdminLockTaskModeEnteringEventQuery lockTaskModeEntering() {
        return DeviceAdminLockTaskModeEnteringEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public DeviceAdminLockTaskModeExitingEventQuery lockTaskModeExiting() {
        return DeviceAdminLockTaskModeExitingEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public DeviceAdminNetworkLogsAvailableEventQuery networkLogsAvailable() {
        return DeviceAdminNetworkLogsAvailableEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public DeviceAdminOperationSafetyStateChangedEventQuery operationSafetyStateChanged() {
        return DeviceAdminOperationSafetyStateChangedEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public DeviceAdminPasswordChangedEvent.DeviceAdminPasswordChangedEventQuery passwordChanged() {
        return DeviceAdminPasswordChangedEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public DeviceAdminPasswordExpiringEventQuery passwordExpiring() {
        return DeviceAdminPasswordExpiringEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public DeviceAdminPasswordFailedEvent.DeviceAdminPasswordFailedEventQuery passwordFailed() {
        return DeviceAdminPasswordFailedEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public DeviceAdminPasswordSucceededEventQuery passwordSucceeded() {
        return DeviceAdminPasswordSucceededEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public DeviceAdminProfileProvisioningCompleteEventQuery profileProvisioningComplete() {
        return DeviceAdminProfileProvisioningCompleteEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public DeviceAdminReadyForUserInitializationEventQuery readyForUserInitialization() {
        return DeviceAdminReadyForUserInitializationEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public DeviceAdminSecurityLogsAvailableEventQuery securityLogsAvailable() {
        return DeviceAdminSecurityLogsAvailableEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public DeviceAdminSystemUpdatePendingEventQuery systemUpdatePending() {
        return DeviceAdminSystemUpdatePendingEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public DeviceAdminTransferAffiliatedProfileOwnershipCompleteEventQuery transferAffiliatedProfileOwnershipComplete() {
        return DeviceAdminTransferAffiliatedProfileOwnershipCompleteEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public DeviceAdminTransferOwnershipCompleteEventQuery transferOwnershipComplete() {
        return DeviceAdminTransferOwnershipCompleteEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public DeviceAdminUserAddedEvent.DeviceAdminUserAddedEventQuery userAdded() {
        return DeviceAdminUserAddedEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public DeviceAdminUserRemovedEvent.DeviceAdminUserRemovedEventQuery userRemoved() {
        return DeviceAdminUserRemovedEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public DeviceAdminUserStartedEvent.DeviceAdminUserStartedEventQuery userStarted() {
        return DeviceAdminUserStartedEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public DeviceAdminUserStoppedEvent.DeviceAdminUserStoppedEventQuery userStopped() {
        return DeviceAdminUserStoppedEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public DeviceAdminUserSwitchedEvent.DeviceAdminUserSwitchedEventQuery userSwitched() {
        return DeviceAdminUserSwitchedEvent.queryPackage(
                mTestApp.packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public ServiceCreatedEvent.ServiceCreatedEventQuery serviceCreated() {
        return ServiceCreatedEvent.queryPackage(
                mTestApp.testApp().packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public ServiceStartedEvent.ServiceStartedEventQuery serviceStarted() {
        return ServiceStartedEvent.queryPackage(
                mTestApp.testApp().packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public ServiceDestroyedEvent.ServiceDestroyedEventQuery serviceDestroyed() {
        return ServiceDestroyedEvent.queryPackage(
                mTestApp.testApp().packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public ServiceConfigurationChangedEvent.ServiceConfigurationChangedEventQuery
            serviceConfigurationChanged() {
        return ServiceConfigurationChangedEvent.queryPackage(
                mTestApp.testApp().packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public ServiceLowMemoryEvent.ServiceLowMemoryEventQuery serviceLowMemory() {
        return ServiceLowMemoryEvent.queryPackage(
                mTestApp.testApp().packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public ServiceMemoryTrimmedEvent.ServiceMemoryTrimmedEventQuery serviceMemoryTrimmed() {
        return ServiceMemoryTrimmedEvent.queryPackage(
                mTestApp.testApp().packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public ServiceBoundEvent.ServiceBoundEventQuery serviceBound() {
        return ServiceBoundEvent.queryPackage(
                mTestApp.testApp().packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public ServiceUnboundEvent.ServiceUnboundEventQuery serviceUnbound() {
        return ServiceUnboundEvent.queryPackage(
                mTestApp.testApp().packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public ServiceReboundEvent.ServiceReboundEventQuery serviceRebound() {
        return ServiceReboundEvent.queryPackage(
                mTestApp.testApp().packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public ServiceTaskRemovedEvent.ServiceTaskRemovedEventQuery serviceTaskRemoved() {
        return ServiceTaskRemovedEvent.queryPackage(
                mTestApp.testApp().packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public DelegatedAdminChoosePrivateKeyAliasEventQuery delegateChoosePrivateKeyAlias() {
        return DelegatedAdminChoosePrivateKeyAliasEvent.queryPackage(
                mTestApp.testApp().packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public DelegatedAdminNetworkLogsAvailableEventQuery delegateNetworkLogsAvailable() {
        return DelegatedAdminNetworkLogsAvailableEvent.queryPackage(
                        mTestApp.testApp().packageName())
                .onUser(mTestApp.user());
    }

    @Override
    public DelegatedAdminSecurityLogsAvailableEventQuery delegateSecurityLogsAvailable() {
        return DelegatedAdminSecurityLogsAvailableEvent.queryPackage(
                        mTestApp.testApp().packageName())
                .onUser(mTestApp.user());
    }
}
