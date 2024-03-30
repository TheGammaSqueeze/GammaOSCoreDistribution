/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.car;

import android.os.UserHandle;
import android.car.user.UserCreationResult;
import android.car.user.UserLifecycleEventFilter;
import android.car.user.UserRemovalResult;
import android.car.user.UserIdentificationAssociationResponse;
import android.car.user.UserSwitchResult;

import android.car.ICarResultReceiver;
import android.car.util.concurrent.AndroidFuture;

/** @hide */
interface ICarUserService {
    void switchUser(int targetUserId, int timeoutMs, in AndroidFuture<UserSwitchResult> receiver);
    void logoutUser(int timeoutMs, in AndroidFuture<UserSwitchResult> receiver);
    void setUserSwitchUiCallback(in ICarResultReceiver callback);
    void createUser(@nullable String name, String userType, int flags, int timeoutMs,
      in AndroidFuture<UserCreationResult> receiver);
    void updatePreCreatedUsers();
    void removeUser(int userId, in AndroidFuture<UserRemovalResult> receiver);
    void setLifecycleListenerForApp(String pkgName, in UserLifecycleEventFilter filter,
      in ICarResultReceiver listener);
    void resetLifecycleListenerForApp(in ICarResultReceiver listener);
    UserIdentificationAssociationResponse getUserIdentificationAssociation(in int[] types);
    void setUserIdentificationAssociation(int timeoutMs, in int[] types, in int[] values,
      in AndroidFuture<UserIdentificationAssociationResponse> result);
    boolean isUserHalUserAssociationSupported();
}
