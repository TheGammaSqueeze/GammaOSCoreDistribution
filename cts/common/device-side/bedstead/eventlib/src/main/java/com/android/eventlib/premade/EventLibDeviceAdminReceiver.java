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

package com.android.eventlib.premade;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PersistableBundle;
import android.os.UserHandle;

import com.android.eventlib.events.broadcastreceivers.BroadcastReceivedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminBugreportFailedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminBugreportSharedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminBugreportSharingDeclinedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminChoosePrivateKeyAliasEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminDisableRequestedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminDisabledEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminEnabledEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminLockTaskModeEnteringEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminLockTaskModeExitingEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminNetworkLogsAvailableEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminOperationSafetyStateChangedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminPasswordChangedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminPasswordExpiringEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminPasswordFailedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminPasswordSucceededEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminProfileProvisioningCompleteEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminReadyForUserInitializationEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminSecurityLogsAvailableEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminSystemUpdatePendingEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminTransferAffiliatedProfileOwnershipCompleteEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminTransferOwnershipCompleteEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserAddedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserRemovedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserStartedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserStoppedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserSwitchedEvent;

/** Implementation of {@link DeviceAdminReceiver} which logs events in response to callbacks. */
public class EventLibDeviceAdminReceiver extends DeviceAdminReceiver {

    private String mOverrideDeviceAdminReceiverClassName;

    public void setOverrideDeviceAdminReceiverClassName(
            String overrideDeviceAdminReceiverClassName) {
        mOverrideDeviceAdminReceiverClassName = overrideDeviceAdminReceiverClassName;
    }

    /**
     * Get the class name for this {@link DeviceAdminReceiver}.
     *
     * <p>This will account for the name being overridden.
     */
    public String className() {
        if (mOverrideDeviceAdminReceiverClassName != null) {
            return mOverrideDeviceAdminReceiverClassName;
        } else {
            return EventLibDeviceAdminReceiver.class.getName();
        }
    }

    @Override
    public void onEnabled(Context context, Intent intent) {
        DeviceAdminEnabledEvent.DeviceAdminEnabledEventLogger logger =
                DeviceAdminEnabledEvent.logger(this, context, intent);

        if (mOverrideDeviceAdminReceiverClassName != null) {
            logger.setDeviceAdminReceiver(mOverrideDeviceAdminReceiverClassName);
        }

        logger.log();

        super.onEnabled(context, intent);
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        DeviceAdminDisableRequestedEvent.DeviceAdminDisableRequestedEventLogger logger =
                DeviceAdminDisableRequestedEvent.logger(this, context, intent);

        if (mOverrideDeviceAdminReceiverClassName != null) {
            logger.setDeviceAdminReceiver(mOverrideDeviceAdminReceiverClassName);
        }

        logger.log();

        return super.onDisableRequested(context, intent);
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        DeviceAdminDisabledEvent.DeviceAdminDisabledEventLogger logger =
                DeviceAdminDisabledEvent.logger(this, context, intent);

        if (mOverrideDeviceAdminReceiverClassName != null) {
            logger.setDeviceAdminReceiver(mOverrideDeviceAdminReceiverClassName);
        }

        logger.log();

        super.onDisabled(context, intent);
    }

    @Override
    public void onPasswordChanged(Context context, Intent intent) {
        DeviceAdminPasswordChangedEvent.DeviceAdminPasswordChangedEventLogger logger =
                DeviceAdminPasswordChangedEvent.logger(this, context, intent);

        if (mOverrideDeviceAdminReceiverClassName != null) {
            logger.setDeviceAdminReceiver(mOverrideDeviceAdminReceiverClassName);
        }

        logger.log();

        super.onPasswordChanged(context, intent);
    }

    @Override
    public void onPasswordChanged(Context context, Intent intent, UserHandle user) {
        DeviceAdminPasswordChangedEvent.DeviceAdminPasswordChangedEventLogger logger =
                DeviceAdminPasswordChangedEvent.logger(this, context, intent);
        logger.setUserHandle(user);

        if (mOverrideDeviceAdminReceiverClassName != null) {
            logger.setDeviceAdminReceiver(mOverrideDeviceAdminReceiverClassName);
        }

        logger.log();
    }

    @Override
    public void onPasswordFailed(Context context, Intent intent) {
        DeviceAdminPasswordFailedEvent.DeviceAdminPasswordFailedEventLogger logger =
                DeviceAdminPasswordFailedEvent.logger(this, context, intent);

        if (mOverrideDeviceAdminReceiverClassName != null) {
            logger.setDeviceAdminReceiver(mOverrideDeviceAdminReceiverClassName);
        }

        logger.log();

        super.onPasswordFailed(context, intent);
    }

    @Override
    public void onPasswordFailed(Context context, Intent intent, UserHandle user) {
        DeviceAdminPasswordFailedEvent.DeviceAdminPasswordFailedEventLogger logger =
                DeviceAdminPasswordFailedEvent.logger(this, context, intent);
        logger.setUserHandle(user);

        if (mOverrideDeviceAdminReceiverClassName != null) {
            logger.setDeviceAdminReceiver(mOverrideDeviceAdminReceiverClassName);
        }

        logger.log();
    }

    @Override
    public void onPasswordSucceeded(Context context, Intent intent) {
        DeviceAdminPasswordSucceededEvent.DeviceAdminPasswordSucceededEventLogger logger =
                DeviceAdminPasswordSucceededEvent.logger(this, context, intent);

        if (mOverrideDeviceAdminReceiverClassName != null) {
            logger.setDeviceAdminReceiver(mOverrideDeviceAdminReceiverClassName);
        }

        logger.log();

        super.onPasswordSucceeded(context, intent);
    }

    @Override
    public void onPasswordSucceeded(Context context, Intent intent, UserHandle user) {
        DeviceAdminPasswordSucceededEvent.DeviceAdminPasswordSucceededEventLogger logger =
                DeviceAdminPasswordSucceededEvent.logger(this, context, intent);
        logger.setUserHandle(user);

        if (mOverrideDeviceAdminReceiverClassName != null) {
            logger.setDeviceAdminReceiver(mOverrideDeviceAdminReceiverClassName);
        }

        logger.log();
    }

    @Override
    public void onPasswordExpiring(Context context, Intent intent) {
        DeviceAdminPasswordExpiringEvent.DeviceAdminPasswordExpiringEventLogger logger =
                DeviceAdminPasswordExpiringEvent.logger(this, context, intent);

        if (mOverrideDeviceAdminReceiverClassName != null) {
            logger.setDeviceAdminReceiver(mOverrideDeviceAdminReceiverClassName);
        }

        logger.log();

        super.onPasswordExpiring(context, intent);
    }

    @Override
    public void onPasswordExpiring(Context context, Intent intent, UserHandle user) {
        DeviceAdminPasswordExpiringEvent.DeviceAdminPasswordExpiringEventLogger logger =
                DeviceAdminPasswordExpiringEvent.logger(this, context, intent);
        logger.setUserHandle(user);

        if (mOverrideDeviceAdminReceiverClassName != null) {
            logger.setDeviceAdminReceiver(mOverrideDeviceAdminReceiverClassName);
        }

        logger.log();
    }

    @Override
    public void onProfileProvisioningComplete(Context context, Intent intent) {
        DeviceAdminProfileProvisioningCompleteEvent.DeviceAdminProfileProvisioningCompleteEventLogger logger =
                DeviceAdminProfileProvisioningCompleteEvent.logger(this, context, intent);

        if (mOverrideDeviceAdminReceiverClassName != null) {
            logger.setDeviceAdminReceiver(mOverrideDeviceAdminReceiverClassName);
        }

        logger.log();

        super.onProfileProvisioningComplete(context, intent);
    }

    @Override
    public void onReadyForUserInitialization(Context context, Intent intent) {
        DeviceAdminReadyForUserInitializationEvent.DeviceAdminReadyForUserInitializationEventLogger logger =
                DeviceAdminReadyForUserInitializationEvent.logger(this, context, intent);

        if (mOverrideDeviceAdminReceiverClassName != null) {
            logger.setDeviceAdminReceiver(mOverrideDeviceAdminReceiverClassName);
        }

        logger.log();

        super.onReadyForUserInitialization(context, intent);
    }

    @Override
    public void onLockTaskModeEntering(Context context, Intent intent, String pkg) {
        DeviceAdminLockTaskModeEnteringEvent.DeviceAdminLockTaskModeEnteringEventLogger logger =
                DeviceAdminLockTaskModeEnteringEvent.logger(this, context, intent, pkg);

        if (mOverrideDeviceAdminReceiverClassName != null) {
            logger.setDeviceAdminReceiver(mOverrideDeviceAdminReceiverClassName);
        }

        logger.log();

        super.onLockTaskModeEntering(context, intent, pkg);
    }

    @Override
    public void onLockTaskModeExiting(Context context, Intent intent) {
        DeviceAdminLockTaskModeExitingEvent.DeviceAdminLockTaskModeExitingEventLogger logger =
                DeviceAdminLockTaskModeExitingEvent.logger(this, context, intent);

        if (mOverrideDeviceAdminReceiverClassName != null) {
            logger.setDeviceAdminReceiver(mOverrideDeviceAdminReceiverClassName);
        }

        logger.log();

        super.onLockTaskModeExiting(context, intent);
    }

    @Override
    public String onChoosePrivateKeyAlias(Context context, Intent intent, int uid, Uri uri,
            String alias) {
        DeviceAdminChoosePrivateKeyAliasEvent.DeviceAdminChoosePrivateKeyAliasEventLogger logger =
                DeviceAdminChoosePrivateKeyAliasEvent
                        .logger(this, context, intent, uid, uri, alias);

        if (mOverrideDeviceAdminReceiverClassName != null) {
            logger.setDeviceAdminReceiver(mOverrideDeviceAdminReceiverClassName);
        }

        logger.log();

        // TODO(b/198280332) Allow TestApp to return values for methods.
        super.onChoosePrivateKeyAlias(context, intent, uid, uri, alias);
        if (uri == null) {
            return null;
        }
        return uri.getQueryParameter("alias");
    }

    @Override
    public void onSystemUpdatePending(Context context, Intent intent, long receivedTime) {
        DeviceAdminSystemUpdatePendingEvent.DeviceAdminSystemUpdatePendingEventLogger logger =
                DeviceAdminSystemUpdatePendingEvent.logger(this, context, intent, receivedTime);

        if (mOverrideDeviceAdminReceiverClassName != null) {
            logger.setDeviceAdminReceiver(mOverrideDeviceAdminReceiverClassName);
        }

        logger.log();

        super.onSystemUpdatePending(context, intent, receivedTime);
    }

    @Override
    public void onBugreportSharingDeclined(Context context, Intent intent) {
        DeviceAdminBugreportSharingDeclinedEvent.DeviceAdminBugreportSharingDeclinedEventLogger logger =
                DeviceAdminBugreportSharingDeclinedEvent.logger(this, context, intent);

        if (mOverrideDeviceAdminReceiverClassName != null) {
            logger.setDeviceAdminReceiver(mOverrideDeviceAdminReceiverClassName);
        }

        logger.log();

        super.onBugreportSharingDeclined(context, intent);
    }

    @Override
    public void onBugreportShared(Context context, Intent intent, String bugreportHash) {
        DeviceAdminBugreportSharedEvent.DeviceAdminBugreportSharedEventLogger logger =
                DeviceAdminBugreportSharedEvent.logger(this, context, intent, bugreportHash);

        if (mOverrideDeviceAdminReceiverClassName != null) {
            logger.setDeviceAdminReceiver(mOverrideDeviceAdminReceiverClassName);
        }

        logger.log();

        super.onBugreportSharingDeclined(context, intent);
    }

    @Override
    public void onBugreportFailed(Context context, Intent intent, int failureCode) {
        DeviceAdminBugreportFailedEvent.DeviceAdminBugreportFailedEventLogger logger =
                DeviceAdminBugreportFailedEvent.logger(this, context, intent, failureCode);

        if (mOverrideDeviceAdminReceiverClassName != null) {
            logger.setDeviceAdminReceiver(mOverrideDeviceAdminReceiverClassName);
        }

        logger.log();

        super.onBugreportFailed(context, intent, failureCode);
    }

    @Override
    public void onSecurityLogsAvailable(Context context, Intent intent) {
        DeviceAdminSecurityLogsAvailableEvent.DeviceAdminSecurityLogsAvailableEventLogger logger =
                DeviceAdminSecurityLogsAvailableEvent.logger(this, context, intent);

        if (mOverrideDeviceAdminReceiverClassName != null) {
            logger.setDeviceAdminReceiver(mOverrideDeviceAdminReceiverClassName);
        }

        logger.log();

        super.onSecurityLogsAvailable(context, intent);
    }

    @Override
    public void onNetworkLogsAvailable(Context context, Intent intent, long batchToken,
            int networkLogsCount) {
        DeviceAdminNetworkLogsAvailableEvent.DeviceAdminNetworkLogsAvailableEventLogger logger =
                DeviceAdminNetworkLogsAvailableEvent
                        .logger(this, context, intent, batchToken, networkLogsCount);

        if (mOverrideDeviceAdminReceiverClassName != null) {
            logger.setDeviceAdminReceiver(mOverrideDeviceAdminReceiverClassName);
        }

        logger.log();

        super.onNetworkLogsAvailable(context, intent, batchToken, networkLogsCount);
    }

    @Override
    public void onUserAdded(Context context, Intent intent, UserHandle addedUser) {
        DeviceAdminUserAddedEvent.DeviceAdminUserAddedEventLogger logger =
                DeviceAdminUserAddedEvent.logger(this, context, intent, addedUser);

        if (mOverrideDeviceAdminReceiverClassName != null) {
            logger.setDeviceAdminReceiver(mOverrideDeviceAdminReceiverClassName);
        }

        logger.log();

        super.onUserAdded(context, intent, addedUser);
    }

    @Override
    public void onUserRemoved(Context context, Intent intent, UserHandle removedUser) {
        DeviceAdminUserRemovedEvent.DeviceAdminUserRemovedEventLogger logger =
                DeviceAdminUserRemovedEvent.logger(this, context, intent, removedUser);

        if (mOverrideDeviceAdminReceiverClassName != null) {
            logger.setDeviceAdminReceiver(mOverrideDeviceAdminReceiverClassName);
        }

        logger.log();

        super.onUserRemoved(context, intent, removedUser);
    }

    @Override
    public void onUserStarted(Context context, Intent intent, UserHandle startedUser) {
        DeviceAdminUserStartedEvent.DeviceAdminUserStartedEventLogger logger =
                DeviceAdminUserStartedEvent.logger(this, context, intent, startedUser);

        if (mOverrideDeviceAdminReceiverClassName != null) {
            logger.setDeviceAdminReceiver(mOverrideDeviceAdminReceiverClassName);
        }

        logger.log();

        super.onUserStarted(context, intent, startedUser);
    }

    @Override
    public void onUserStopped(Context context, Intent intent, UserHandle stoppedUser) {
        DeviceAdminUserStoppedEvent.DeviceAdminUserStoppedEventLogger logger =
                DeviceAdminUserStoppedEvent.logger(this, context, intent, stoppedUser);

        if (mOverrideDeviceAdminReceiverClassName != null) {
            logger.setDeviceAdminReceiver(mOverrideDeviceAdminReceiverClassName);
        }

        logger.log();

        super.onUserStopped(context, intent, stoppedUser);
    }

    @Override
    public void onUserSwitched(Context context, Intent intent, UserHandle switchedUser) {
        DeviceAdminUserSwitchedEvent.DeviceAdminUserSwitchedEventLogger logger =
                DeviceAdminUserSwitchedEvent.logger(this, context, intent, switchedUser);

        if (mOverrideDeviceAdminReceiverClassName != null) {
            logger.setDeviceAdminReceiver(mOverrideDeviceAdminReceiverClassName);
        }

        logger.log();

        super.onUserRemoved(context, intent, switchedUser);
    }

    @Override
    public void onTransferOwnershipComplete(Context context, PersistableBundle bundle) {
        DeviceAdminTransferOwnershipCompleteEvent.DeviceAdminTransferOwnershipCompleteEventLogger logger =
                DeviceAdminTransferOwnershipCompleteEvent.logger(this, context, bundle);

        if (mOverrideDeviceAdminReceiverClassName != null) {
            logger.setDeviceAdminReceiver(mOverrideDeviceAdminReceiverClassName);
        }

        logger.log();

        super.onTransferOwnershipComplete(context, bundle);
    }

    @Override
    public void onTransferAffiliatedProfileOwnershipComplete(Context context, UserHandle user) {
        DeviceAdminTransferAffiliatedProfileOwnershipCompleteEvent.DeviceAdminTransferAffiliatedProfileOwnershipCompleteEventLogger logger =
                DeviceAdminTransferAffiliatedProfileOwnershipCompleteEvent
                        .logger(this, context, user);

        if (mOverrideDeviceAdminReceiverClassName != null) {
            logger.setDeviceAdminReceiver(mOverrideDeviceAdminReceiverClassName);
        }

        logger.log();

        super.onTransferAffiliatedProfileOwnershipComplete(context, user);
    }

    @Override
    public void onOperationSafetyStateChanged(Context context, int reason, boolean isSafe) {
        DeviceAdminOperationSafetyStateChangedEvent.DeviceAdminOperationSafetyStateChangedEventLogger logger =
                DeviceAdminOperationSafetyStateChangedEvent
                        .logger(this, context, reason, isSafe);

        if (mOverrideDeviceAdminReceiverClassName != null) {
            logger.setDeviceAdminReceiver(mOverrideDeviceAdminReceiverClassName);
        }

        logger.log();

        super.onOperationSafetyStateChanged(context, reason, isSafe);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        BroadcastReceivedEvent.BroadcastReceivedEventLogger logger =
                BroadcastReceivedEvent.logger(this, context, intent);

        if (mOverrideDeviceAdminReceiverClassName != null) {
            logger.setBroadcastReceiver(mOverrideDeviceAdminReceiverClassName);
        }

        logger.log();

        super.onReceive(context, intent);
    }
}
