/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.car.admin;

import static android.os.Process.myUid;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.annotation.TestApi;
import android.car.Car;
import android.car.CarManagerBase;
import android.car.annotation.AddedInOrBefore;
import android.car.builtin.util.EventLogHelper;
import android.car.user.UserCreationResult;
import android.car.user.UserRemovalResult;
import android.car.user.UserStartResult;
import android.car.user.UserStopResult;
import android.car.util.concurrent.AndroidFuture;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;

import com.android.car.internal.common.UserHelperLite;
import com.android.car.internal.os.CarSystemProperties;
import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Public interface for managing policies enforced on a device.
 *
 * <p>This is a sub-set of {@link android.app.admin.DevicePolicyManager}, but with the following
 * differences:
 *
 * <ol>
 *   <li>Its methods take in consideration driver-safety restrictions.
 *   <li>Callers doesn't need to be a {@code DPC}, but rather have the proper permissions.
 * </ol>
 *
 * @hide
 */
@SystemApi
@TestApi
public final class CarDevicePolicyManager extends CarManagerBase {

    /**
     * @hide
     */
    @VisibleForTesting
    @AddedInOrBefore(majorVersion = 33)
    public static final String TAG = CarDevicePolicyManager.class.getSimpleName();

    private final ICarDevicePolicyService mService;

    private static final String PREFIX_USER_TYPE = "USER_TYPE_";

    /**
     * Type used to indicate the user is a regular user.
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int USER_TYPE_REGULAR = 0;

    /**
     * Type used to indicate the user is an admin user.
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int USER_TYPE_ADMIN = 1;

    /**
     * Type used to indicate the user is a guest user.
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int USER_TYPE_GUEST = 2;

    /** @hide - Used on test cases only */
    @AddedInOrBefore(majorVersion = 33)
    public static final int FIRST_USER_TYPE = USER_TYPE_REGULAR;
    /** @hide - Used on test cases only */
    @AddedInOrBefore(majorVersion = 33)
    public static final int LAST_USER_TYPE = USER_TYPE_GUEST;

    private static final int DEVICE_POLICY_MANAGER_TIMEOUT_MS =
            CarSystemProperties.getDevicePolicyManagerTimeout().orElse(60_000);

    /** @hide */
    @IntDef(prefix = PREFIX_USER_TYPE, value = {
            USER_TYPE_REGULAR,
            USER_TYPE_ADMIN,
            USER_TYPE_GUEST
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface UserType {
    }

    /**
     * @hide
     */
    public CarDevicePolicyManager(@NonNull Car car, @NonNull IBinder service) {
        this(car, ICarDevicePolicyService.Stub.asInterface(service));
    }

    /**
     * @hide
     */
    @VisibleForTesting
    public CarDevicePolicyManager(@NonNull Car car, @NonNull ICarDevicePolicyService service) {
        super(car);
        mService = service;
    }

    /**
     * Removes the given user.
     *
     * <p><b>Note: </b>if the caller user is not an admin, it can only remove itself
     * (otherwise it will fail with {@link RemoveUserResult#STATUS_FAILURE_INVALID_ARGUMENTS}).
     *
     * @param user identification of the user to be removed.
     *
     * @return whether the user was successfully removed.
     *
     * @hide
     */
    @SystemApi
    @TestApi
    @RequiresPermission(anyOf = {android.Manifest.permission.MANAGE_USERS,
            android.Manifest.permission.CREATE_USERS})
    @NonNull
    @AddedInOrBefore(majorVersion = 33)
    public RemoveUserResult removeUser(@NonNull UserHandle user) {
        Objects.requireNonNull(user, "user cannot be null");

        int userId = user.getIdentifier();
        int uid = myUid();
        EventLogHelper.writeCarDevicePolicyManagerRemoveUserReq(uid, userId);
        int status = RemoveUserResult.STATUS_FAILURE_GENERIC;
        try {
            AndroidFuture<UserRemovalResult> future = new AndroidFuture<UserRemovalResult>();
            mService.removeUser(userId, future);
            UserRemovalResult result = future.get(DEVICE_POLICY_MANAGER_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS);
            status = result.getStatus();
            return new RemoveUserResult(status);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new RemoveUserResult(status);
        } catch (ExecutionException | TimeoutException e) {
            return new RemoveUserResult(status);
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, new RemoveUserResult(status));
        } finally {
            EventLogHelper.writeCarDevicePolicyManagerRemoveUserResp(uid, status);
        }
    }

    /**
     * Creates a user with the given characteristics.
     *
     * <p><b>Note: </b>if the caller user is not an admin, it can only create non-admin users
     * (otherwise it will fail with {@link CreateUserResult#STATUS_FAILURE_INVALID_ARGUMENTS}).
     *
     * @param name user name.
     * @param type either {@link #USER_TYPE_REGULAR}, {@link #USER_TYPE_ADMIN},
     * or {@link #USER_TYPE_GUEST}.
     *
     * @return whether the user was successfully removed.
     *
     * @hide
     */
    @SystemApi
    @TestApi
    @RequiresPermission(anyOf = {android.Manifest.permission.MANAGE_USERS,
            android.Manifest.permission.CREATE_USERS})
    @NonNull
    @AddedInOrBefore(majorVersion = 33)
    public CreateUserResult createUser(@Nullable String name, @UserType int type) {
        int uid = myUid();
        EventLogHelper.writeCarDevicePolicyManagerCreateUserReq(uid, UserHelperLite.safeName(name),
                type);
        int status = CreateUserResult.STATUS_FAILURE_GENERIC;
        try {
            AndroidFuture<UserCreationResult> future = new AndroidFuture<UserCreationResult>();
            mService.createUser(name, type, future);
            UserCreationResult result = future.get(DEVICE_POLICY_MANAGER_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS);
            status = result.getStatus();
            return new CreateUserResult(result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CreateUserResult.forGenericError();
        } catch (ExecutionException | TimeoutException e) {
            return CreateUserResult.forGenericError();
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, CreateUserResult.forGenericError());
        } finally {
            EventLogHelper.writeCarDevicePolicyManagerCreateUserResp(uid, status);
        }
    }

    /**
     * Starts a user in the background.
     *
     * @param user identification of the user to be started.
     *
     * @return whether the user was successfully started.
     *
     * @hide
     */
    @TestApi
    @RequiresPermission(anyOf = {android.Manifest.permission.MANAGE_USERS,
            android.Manifest.permission.CREATE_USERS})
    @NonNull
    @AddedInOrBefore(majorVersion = 33)
    public StartUserInBackgroundResult startUserInBackground(@NonNull UserHandle user) {
        Objects.requireNonNull(user, "user cannot be null");

        int userId = user.getIdentifier();
        int uid = myUid();
        EventLogHelper.writeCarDevicePolicyManagerStartUserInBackgroundReq(uid, userId);
        int status = StartUserInBackgroundResult.STATUS_FAILURE_GENERIC;
        try {
            AndroidFuture<UserStartResult> future = new AndroidFuture<>();
            mService.startUserInBackground(userId, future);
            UserStartResult result = future.get(DEVICE_POLICY_MANAGER_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS);
            status = result.getStatus();
            return new StartUserInBackgroundResult(status);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new StartUserInBackgroundResult(status);
        } catch (ExecutionException | TimeoutException e) {
            return new StartUserInBackgroundResult(status);
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, new StartUserInBackgroundResult(status));
        } finally {
            EventLogHelper.writeCarDevicePolicyManagerStartUserInBackgroundResp(uid, status);
        }
    }

    /**
     * Stops the given user.
     *
     * @param user identification of the user to stop.
     *
     * @return whether the user was successfully stopped.
     *
     * @hide
     */
    @TestApi
    @RequiresPermission(anyOf = {android.Manifest.permission.MANAGE_USERS,
            android.Manifest.permission.CREATE_USERS})
    @NonNull
    @AddedInOrBefore(majorVersion = 33)
    public StopUserResult stopUser(@NonNull UserHandle user) {
        Objects.requireNonNull(user, "user cannot be null");

        int userId = user.getIdentifier();
        int uid = myUid();
        EventLogHelper.writeCarDevicePolicyManagerStopUserReq(uid, userId);
        int status = StopUserResult.STATUS_FAILURE_GENERIC;
        try {
            AndroidFuture<UserStopResult> future = new AndroidFuture<>();
            mService.stopUser(userId, future);
            UserStopResult result =
                    future.get(DEVICE_POLICY_MANAGER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            status = result.getStatus();
            return new StopUserResult(status);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new StopUserResult(status);
        } catch (ExecutionException | TimeoutException e) {
            return new StopUserResult(status);
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, new StopUserResult(status));
        } finally {
            EventLogHelper.writeCarDevicePolicyManagerStopUserResp(uid, status);
        }
    }

    /** @hide */
    @AddedInOrBefore(majorVersion = 33)
    public void setUserDisclaimerShown(@NonNull UserHandle user) {
        Objects.requireNonNull(user, "user cannot be null");
        try {
            mService.setUserDisclaimerShown(user.getIdentifier());
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e, null);
        }
    }

    /** @hide */
    @AddedInOrBefore(majorVersion = 33)
    public void setUserDisclaimerAcknowledged(@NonNull UserHandle user) {
        Objects.requireNonNull(user, "user cannot be null");
        try {
            mService.setUserDisclaimerAcknowledged(user.getIdentifier());
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e, null);
        }
    }

    /** @hide */
    @Override
    @AddedInOrBefore(majorVersion = 33)
    public void onCarDisconnected() {
        // nothing to do
    }
}
