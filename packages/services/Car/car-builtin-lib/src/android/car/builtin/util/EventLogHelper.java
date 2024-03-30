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

package android.car.builtin.util;

import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.car.builtin.annotation.AddedIn;
import android.car.builtin.annotation.PlatformVersion;
import android.util.EventLog;

/**
 * Helper for {@link EventLog}
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class EventLogHelper {
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarHelperStart() {
        EventLog.writeEvent(EventLogTags.CAR_HELPER_START);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarHelperBootPhase(int phase) {
        EventLog.writeEvent(EventLogTags.CAR_HELPER_BOOT_PHASE, phase);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarHelperUserStarting(int userId) {
        EventLog.writeEvent(EventLogTags.CAR_HELPER_USER_STARTING, userId);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarHelperUserSwitching(int fromUserId, int toUserId) {
        EventLog.writeEvent(EventLogTags.CAR_HELPER_USER_SWITCHING, fromUserId, toUserId);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarHelperUserUnlocking(int userId) {
        EventLog.writeEvent(EventLogTags.CAR_HELPER_USER_UNLOCKING, userId);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarHelperUserUnlocked(int userId) {
        EventLog.writeEvent(EventLogTags.CAR_HELPER_USER_UNLOCKED, userId);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarHelperUserStopping(int userId) {
        EventLog.writeEvent(EventLogTags.CAR_HELPER_USER_STOPPING, userId);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarHelperUserStopped(int userId) {
        EventLog.writeEvent(EventLogTags.CAR_HELPER_USER_STOPPED, userId);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarHelperServiceConnected() {
        EventLog.writeEvent(EventLogTags.CAR_HELPER_SVC_CONNECTED);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarServiceInit(int numberServices) {
        EventLog.writeEvent(EventLogTags.CAR_SERVICE_INIT, numberServices);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarServiceVhalReconnected(int numberServices) {
        EventLog.writeEvent(EventLogTags.CAR_SERVICE_VHAL_RECONNECTED, numberServices);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarServiceSetCarServiceHelper(int pid) {
        EventLog.writeEvent(EventLogTags.CAR_SERVICE_SET_CAR_SERVICE_HELPER, pid);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarServiceOnUserLifecycle(int type, int fromUserId, int toUserId) {
        EventLog.writeEvent(EventLogTags.CAR_SERVICE_ON_USER_LIFECYCLE, type, fromUserId, toUserId);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarServiceCreate(boolean hasVhal) {
        EventLog.writeEvent(EventLogTags.CAR_SERVICE_CREATE, hasVhal ? 1 : 0);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarServiceConnected(@Nullable String interfaceName) {
        EventLog.writeEvent(EventLogTags.CAR_SERVICE_CONNECTED, interfaceName);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarServiceDestroy(boolean hasVhal) {
        EventLog.writeEvent(EventLogTags.CAR_SERVICE_DESTROY, hasVhal ? 1 : 0);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarServiceVhalDied(long cookie) {
        EventLog.writeEvent(EventLogTags.CAR_SERVICE_VHAL_DIED, cookie);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarServiceInitBootUser() {
        EventLog.writeEvent(EventLogTags.CAR_SERVICE_INIT_BOOT_USER);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarServiceOnUserRemoved(int userId) {
        EventLog.writeEvent(EventLogTags.CAR_SERVICE_ON_USER_REMOVED, userId);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserServiceInitialUserInfoReq(int requestType, int timeout,
            int currentUserId, int currentUserFlags, int numberExistingUsers) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_INITIAL_USER_INFO_REQ, requestType, timeout,
                currentUserId, currentUserFlags, numberExistingUsers);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserServiceInitialUserInfoResp(int status, int action, int userId,
            int flags, @Nullable String safeName, @Nullable String userLocales) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_INITIAL_USER_INFO_RESP, status, action,
                userId, flags, safeName, userLocales);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserServiceSetInitialUser(int userId) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_SET_INITIAL_USER, userId);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserServiceSetLifecycleListener(int uid,
            @Nullable String packageName) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_SET_LIFECYCLE_LISTENER, uid, packageName);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserServiceResetLifecycleListener(int uid,
            @Nullable String packageName) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_RESET_LIFECYCLE_LISTENER, uid, packageName);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserServiceSwitchUserReq(int userId, int timeout) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_SWITCH_USER_REQ, userId, timeout);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserServiceSwitchUserResp(int halCallbackStatus,
            int userSwitchStatus, @Nullable String errorMessage) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_SWITCH_USER_RESP, halCallbackStatus,
                userSwitchStatus, errorMessage);
    }

    /**
     * Logs a {@code EventLogTags.CAR_USER_SVC_LOGOUT_USER_REQ} event.
     */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserServiceLogoutUserReq(int userId, int timeout) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_LOGOUT_USER_REQ, userId, timeout);
    }

    /**
     * Logs a {@code EventLogTags.CAR_USER_SVC_LOGOUT_USER_RESP} event.
     */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserServiceLogoutUserResp(int halCallbackStatus,
            int userSwitchStatus, @Nullable String errorMessage) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_LOGOUT_USER_RESP, halCallbackStatus,
                userSwitchStatus, errorMessage);
    }
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserServicePostSwitchUserReq(int targetUserId, int currentUserId) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_POST_SWITCH_USER_REQ, targetUserId,
                currentUserId);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserServiceGetUserAuthReq(int uid, int userId, int numberTypes) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_GET_USER_AUTH_REQ, uid, userId, numberTypes);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserServiceGetUserAuthResp(int numberValues) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_GET_USER_AUTH_RESP, numberValues);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserServiceSwitchUserUiReq(int userId) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_SWITCH_USER_UI_REQ, userId);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserServiceSwitchUserFromHalReq(int requestId, int uid) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_SWITCH_USER_FROM_HAL_REQ, requestId, uid);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserServiceSetUserAuthReq(int uid, int userId,
            int numberAssociations) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_SET_USER_AUTH_REQ, uid, userId,
                numberAssociations);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserServiceSetUserAuthResp(int numberValues,
            @Nullable String errorMessage) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_SET_USER_AUTH_RESP, numberValues,
                errorMessage);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserServiceCreateUserReq(@Nullable String safeName,
            @Nullable String userType, int flags, int timeout, int hasCallerRestrictions) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_CREATE_USER_REQ, safeName, userType, flags,
                timeout, hasCallerRestrictions);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserServiceCreateUserResp(int status, int result,
            @Nullable String errorMessage) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_CREATE_USER_RESP, status, result,
                errorMessage);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserServiceCreateUserUserCreated(int userId,
            @Nullable String safeName, @Nullable String userType, int flags) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_CREATE_USER_USER_CREATED, userId, safeName,
                userType, flags);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserServiceCreateUserUserRemoved(int userId,
            @Nullable String reason) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_CREATE_USER_USER_REMOVED, userId, reason);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserServiceRemoveUserReq(int userId, int hascallerrestrictions) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_REMOVE_USER_REQ, userId,
                hascallerrestrictions);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserServiceRemoveUserResp(int userId, int result) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_REMOVE_USER_RESP, userId, result);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserServiceNotifyAppLifecycleListener(int uid,
            @Nullable String packageName, int eventType, int fromUserId, int toUserId) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_NOTIFY_APP_LIFECYCLE_LISTENER, uid,
                packageName, eventType, fromUserId, toUserId);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserServiceNotifyInternalLifecycleListener(
            @Nullable String listenerName, int eventType, int fromUserId, int toUserId) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_NOTIFY_INTERNAL_LIFECYCLE_LISTENER,
                listenerName, eventType, fromUserId, toUserId);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserServicePreCreationRequested(int numberUsers, int numberGuests) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_PRE_CREATION_REQUESTED, numberUsers,
                numberGuests);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserServicePreCreationStatus(int numberExistingUsers,
            int numberUsersToAdd, int numberUsersToRemove, int numberExistingGuests,
            int numberGuestsToAdd, int numberGuestsToRemove, int numberInvalidUsersToRemove) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_PRE_CREATION_STATUS, numberExistingUsers,
                numberUsersToAdd, numberUsersToRemove, numberExistingGuests, numberGuestsToAdd,
                numberGuestsToRemove, numberInvalidUsersToRemove);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserServiceStartUserInBackgroundReq(int userId) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_START_USER_IN_BACKGROUND_REQ, userId);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserServiceStartUserInBackgroundResp(int userId, int result) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_START_USER_IN_BACKGROUND_RESP, userId,
                result);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserServiceStopUserReq(int userId) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_STOP_USER_REQ, userId);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserServiceStopUserResp(int userId, int result) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_STOP_USER_RESP, userId, result);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserServiceInitialUserInfoReqComplete(int requestType) {
        EventLog.writeEvent(EventLogTags.CAR_USER_SVC_INITIAL_USER_INFO_REQ_COMPLETE, requestType);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserHalInitialUserInfoReq(int requestId, int requestType,
            int timeout) {
        EventLog.writeEvent(EventLogTags.CAR_USER_HAL_INITIAL_USER_INFO_REQ, requestId, requestType,
                timeout);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserHalInitialUserInfoResp(int requestId, int status, int action,
            int userId, int flags, @Nullable String safeName, @Nullable String userLocales) {
        EventLog.writeEvent(EventLogTags.CAR_USER_HAL_INITIAL_USER_INFO_RESP, requestId, status,
                action, userId, flags, safeName, userLocales);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserHalSwitchUserReq(int requestId, int userId, int userFlags,
            int timeout) {
        EventLog.writeEvent(EventLogTags.CAR_USER_HAL_SWITCH_USER_REQ, requestId, userId, userFlags,
                timeout);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserHalSwitchUserResp(int requestId, int status, int result,
            @Nullable String errorMessage) {
        EventLog.writeEvent(EventLogTags.CAR_USER_HAL_SWITCH_USER_RESP, requestId, status, result,
                errorMessage);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserHalPostSwitchUserReq(int requestId, int targetUserId,
            int currentUserId) {
        EventLog.writeEvent(EventLogTags.CAR_USER_HAL_POST_SWITCH_USER_REQ, requestId, targetUserId,
                currentUserId);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserHalGetUserAuthReq(@Nullable Object[] int32Values) {
        EventLog.writeEvent(EventLogTags.CAR_USER_HAL_GET_USER_AUTH_REQ, int32Values);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserHalGetUserAuthResp(@Nullable Object[] valuesAndError) {
        EventLog.writeEvent(EventLogTags.CAR_USER_HAL_GET_USER_AUTH_RESP, valuesAndError);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserHalLegacySwitchUserReq(int requestId, int targetUserId,
            int currentUserId) {
        EventLog.writeEvent(EventLogTags.CAR_USER_HAL_LEGACY_SWITCH_USER_REQ, requestId,
                targetUserId, currentUserId);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserHalSetUserAuthReq(@Nullable Object[] int32Values) {
        EventLog.writeEvent(EventLogTags.CAR_USER_HAL_SET_USER_AUTH_REQ, int32Values);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserHalSetUserAuthResp(@Nullable Object[] valuesAndError) {
        EventLog.writeEvent(EventLogTags.CAR_USER_HAL_SET_USER_AUTH_RESP, valuesAndError);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserHalOemSwitchUserReq(int requestId, int targetUserId) {
        EventLog.writeEvent(EventLogTags.CAR_USER_HAL_OEM_SWITCH_USER_REQ, requestId, targetUserId);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserHalCreateUserReq(int requestId, @Nullable String safeName,
            int flags, int timeout) {
        EventLog.writeEvent(EventLogTags.CAR_USER_HAL_CREATE_USER_REQ, requestId, safeName, flags,
                timeout);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserHalCreateUserResp(int requestId, int status, int result,
            @Nullable String errorMessage) {
        EventLog.writeEvent(EventLogTags.CAR_USER_HAL_CREATE_USER_RESP, requestId, status, result,
                errorMessage);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserHalRemoveUserReq(int targetUserId, int currentUserId) {
        EventLog.writeEvent(EventLogTags.CAR_USER_HAL_REMOVE_USER_REQ, targetUserId, currentUserId);
    }

    /** Logs a {@code EventLogTags.CAR_USER_MGR_ADD_LISTENER} event. */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserManagerAddListener(int uid, @Nullable String packageName,
            boolean hasFilter) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_ADD_LISTENER, uid, packageName,
                hasFilter ? 1 : 0);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserManagerRemoveListener(int uid, @Nullable String packageName) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_REMOVE_LISTENER, uid, packageName);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserManagerDisconnected(int uid) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_DISCONNECTED, uid);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserManagerSwitchUserReq(int uid, int userId) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_SWITCH_USER_REQ, uid, userId);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserManagerSwitchUserResp(int uid, int status,
            @Nullable String errorMessage) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_SWITCH_USER_RESP, uid, status, errorMessage);
    }

    /**
     * Logs a {@code EventLogTags.CAR_USER_MGR_LOGOUT_USER_REQ} event.
     */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserManagerLogoutUserReq(int uid) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_LOGOUT_USER_REQ, uid);
    }

    /**
     * Logs a {@code EventLogTags.CAR_USER_MGR_LOGOUT_USER_RESP} event.
     */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserManagerLogoutUserResp(int uid, int status,
            @Nullable String errorMessage) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_LOGOUT_USER_RESP, uid, status, errorMessage);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserManagerGetUserAuthReq(@Nullable Object[] types) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_GET_USER_AUTH_REQ, types);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserManagerGetUserAuthResp(@Nullable Object[] values) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_GET_USER_AUTH_RESP, values);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserManagerSetUserAuthReq(@Nullable Object[] typesAndValuesPairs) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_SET_USER_AUTH_REQ, typesAndValuesPairs);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserManagerSetUserAuthResp(@Nullable Object[] values) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_SET_USER_AUTH_RESP, values);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserManagerCreateUserReq(int uid, @Nullable String safeName,
            @Nullable String userType, int flags) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_CREATE_USER_REQ, uid, safeName, userType,
                flags);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserManagerCreateUserResp(int uid, int status,
            @Nullable String errorMessage) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_CREATE_USER_RESP, uid, status, errorMessage);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserManagerRemoveUserReq(int uid, int userId) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_REMOVE_USER_REQ, uid, userId);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserManagerRemoveUserResp(int uid, int status) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_REMOVE_USER_RESP, uid, status);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserManagerNotifyLifecycleListener(int numberListeners,
            int eventType, int fromUserId, int toUserId) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_NOTIFY_LIFECYCLE_LISTENER, numberListeners,
                eventType, fromUserId, toUserId);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarUserManagerPreCreateUserReq(int uid) {
        EventLog.writeEvent(EventLogTags.CAR_USER_MGR_PRE_CREATE_USER_REQ, uid);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarDevicePolicyManagerRemoveUserReq(int uid, int userId) {
        EventLog.writeEvent(EventLogTags.CAR_DP_MGR_REMOVE_USER_REQ, uid, userId);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarDevicePolicyManagerRemoveUserResp(int uid, int status) {
        EventLog.writeEvent(EventLogTags.CAR_DP_MGR_REMOVE_USER_RESP, uid, status);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarDevicePolicyManagerCreateUserReq(int uid, @Nullable String safeName,
            int flags) {
        EventLog.writeEvent(EventLogTags.CAR_DP_MGR_CREATE_USER_REQ, uid, safeName, flags);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarDevicePolicyManagerCreateUserResp(int uid, int status) {
        EventLog.writeEvent(EventLogTags.CAR_DP_MGR_CREATE_USER_RESP, uid, status);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarDevicePolicyManagerStartUserInBackgroundReq(int uid, int userId) {
        EventLog.writeEvent(EventLogTags.CAR_DP_MGR_START_USER_IN_BACKGROUND_REQ, uid, userId);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarDevicePolicyManagerStartUserInBackgroundResp(int uid, int status) {
        EventLog.writeEvent(EventLogTags.CAR_DP_MGR_START_USER_IN_BACKGROUND_RESP, uid, status);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarDevicePolicyManagerStopUserReq(int uid, int userId) {
        EventLog.writeEvent(EventLogTags.CAR_DP_MGR_STOP_USER_REQ, uid, userId);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarDevicePolicyManagerStopUserResp(int uid, int status) {
        EventLog.writeEvent(EventLogTags.CAR_DP_MGR_STOP_USER_RESP, uid, status);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writePowerPolicyChange(String policy) {
        EventLog.writeEvent(EventLogTags.CAR_PWR_MGR_PWR_POLICY_CHANGE, policy);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarPowerManagerStateChange(int state) {
        EventLog.writeEvent(EventLogTags.CAR_PWR_MGR_STATE_CHANGE, state);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeCarPowerManagerStateRequest(int state, int param) {
        EventLog.writeEvent(EventLogTags.CAR_PWR_MGR_STATE_REQ, state, param);
    }

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void writeGarageModeEvent(int status) {
        EventLog.writeEvent(EventLogTags.CAR_PWR_MGR_GARAGE_MODE, status);
    }

    private EventLogHelper() {
        throw new UnsupportedOperationException();
    }
}
