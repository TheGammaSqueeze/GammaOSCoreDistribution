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

package android.car.user;

import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.os.Process.myUid;

import static com.android.car.internal.util.FunctionalUtils.getLambdaName;

import android.annotation.CallbackExecutor;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.annotation.TestApi;
import android.annotation.UserIdInt;
import android.car.Car;
import android.car.CarManagerBase;
import android.car.ICarResultReceiver;
import android.car.ICarUserService;
import android.car.annotation.AddedInOrBefore;
import android.car.annotation.ApiRequirements;
import android.car.builtin.os.UserManagerHelper;
import android.car.builtin.util.EventLogHelper;
import android.car.util.concurrent.AndroidAsyncFuture;
import android.car.util.concurrent.AndroidFuture;
import android.car.util.concurrent.AsyncFuture;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;
import android.util.Dumpable;
import android.util.Log;
import android.util.Pair;

import com.android.car.internal.common.CommonConstants;
import com.android.car.internal.common.CommonConstants.UserLifecycleEventType;
import com.android.car.internal.common.UserHelperLite;
import com.android.car.internal.os.CarSystemProperties;
import com.android.car.internal.util.ArrayUtils;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * API to manage users related to car.
 *
 * @hide
 */
@SystemApi
@TestApi
public final class CarUserManager extends CarManagerBase {

    /** @hide */
    @AddedInOrBefore(majorVersion = 33)
    public static final String TAG = CarUserManager.class.getSimpleName();

    private static final int HAL_TIMEOUT_MS = CarSystemProperties.getUserHalTimeout().orElse(5_000);
    private static final int REMOVE_USER_CALL_TIMEOUT_MS = 60_000;

    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    /**
     * {@link UserLifecycleEvent} called when the user is starting, for components to initialize
     * any per-user state they maintain for running users.
     *
     * @hide
     */
    @SystemApi
    @TestApi
    @AddedInOrBefore(majorVersion = 33)
    public static final int USER_LIFECYCLE_EVENT_TYPE_STARTING =
            CommonConstants.USER_LIFECYCLE_EVENT_TYPE_STARTING;

    /**
     * {@link UserLifecycleEvent} called when switching to a different foreground user, for
     * components that have special behavior for whichever user is currently in the foreground.
     *
     * <p>This is called before any application processes are aware of the new user.
     *
     * <p>Notice that internal system services might not have handled user switching yet, so be
     * careful with interaction with them.
     *
     * @hide
     */
    @SystemApi
    @TestApi
    @AddedInOrBefore(majorVersion = 33)
    public static final int USER_LIFECYCLE_EVENT_TYPE_SWITCHING =
            CommonConstants.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;

    /**
     * {@link UserLifecycleEvent} called when an existing user is in the process of being unlocked.
     *
     * <p>This means the credential-encrypted storage for that user is now available, and
     * encryption-aware component filtering is no longer in effect.
     *
     * <p>Notice that internal system services might not have handled unlock yet, so most components
     * should ignore this callback and rely on {@link #USER_LIFECYCLE_EVENT_TYPE_UNLOCKED} instead.
     *
     * @hide
     */
    @SystemApi
    @TestApi
    @AddedInOrBefore(majorVersion = 33)
    public static final int USER_LIFECYCLE_EVENT_TYPE_UNLOCKING =
            CommonConstants.USER_LIFECYCLE_EVENT_TYPE_UNLOCKING;

    /**
     * {@link UserLifecycleEvent} called after an existing user is unlocked.
     *
     * @hide
     */
    @SystemApi
    @TestApi
    @AddedInOrBefore(majorVersion = 33)
    public static final int USER_LIFECYCLE_EVENT_TYPE_UNLOCKED =
            CommonConstants.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED;

    /**
     * {@link UserLifecycleEvent} called after an existing user is unlocked for components to
     * perform non-urgent tasks for user unlocked.
     *
     * <p>Note: This event type is intended only for internal system services. Application listeners
     * should not use this event type and will not receive any events of this type.
     *
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int USER_LIFECYCLE_EVENT_TYPE_POST_UNLOCKED =
            CommonConstants.USER_LIFECYCLE_EVENT_TYPE_POST_UNLOCKED;

    /**
     * {@link UserLifecycleEvent} called when an existing user is stopping, for components to
     * finalize any per-user state they maintain for running users.
     *
     * <p>This is called prior to sending the {@code SHUTDOWN} broadcast to the user; it is a good
     * place to stop making use of any resources of that user (such as binding to a service running
     * in the user).
     *
     * <p><b>Note:</b> this is the last callback where the callee may access the target user's CE
     * storage.
     *
     * @hide
     */
    @SystemApi
    @TestApi
    @AddedInOrBefore(majorVersion = 33)
    public static final int USER_LIFECYCLE_EVENT_TYPE_STOPPING =
            CommonConstants.USER_LIFECYCLE_EVENT_TYPE_STOPPING;

    /**
     * {@link UserLifecycleEvent} called after an existing user is stopped.
     *
     * <p>This is called after all application process teardown of the user is complete.
     *
     * @hide
     */
    @SystemApi
    @TestApi
    @AddedInOrBefore(majorVersion = 33)
    public static final int USER_LIFECYCLE_EVENT_TYPE_STOPPED =
            CommonConstants.USER_LIFECYCLE_EVENT_TYPE_STOPPED;

    /**
     * {@link UserLifecycleEvent} called after an existing user is created.
     *
     * @hide
     */
    @SystemApi
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_1,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_1)
    public static final int USER_LIFECYCLE_EVENT_TYPE_CREATED =
            CommonConstants.USER_LIFECYCLE_EVENT_TYPE_CREATED;

    /**
     * {@link UserLifecycleEvent} called after an existing user is removed.
     *
     * @hide
     */
    @SystemApi
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_1,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_1)
    public static final int USER_LIFECYCLE_EVENT_TYPE_REMOVED =
            CommonConstants.USER_LIFECYCLE_EVENT_TYPE_REMOVED;

    /** @hide */
    @AddedInOrBefore(majorVersion = 33)
    public static final String BUNDLE_PARAM_ACTION = "action";
    /** @hide */
    @AddedInOrBefore(majorVersion = 33)
    public static final String BUNDLE_PARAM_PREVIOUS_USER_ID = "previous_user";

    /**
     * {@link UserIdentificationAssociationType} for key fob.
     *
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int USER_IDENTIFICATION_ASSOCIATION_TYPE_KEY_FOB = 1;

    /**
     * {@link UserIdentificationAssociationType} for custom type 1.
     *
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int USER_IDENTIFICATION_ASSOCIATION_TYPE_CUSTOM_1 = 101;

    /**
     * {@link UserIdentificationAssociationType} for custom type 2.
     *
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int USER_IDENTIFICATION_ASSOCIATION_TYPE_CUSTOM_2 = 102;

    /**
     * {@link UserIdentificationAssociationType} for custom type 3.
     *
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int USER_IDENTIFICATION_ASSOCIATION_TYPE_CUSTOM_3 = 103;

    /**
     * {@link UserIdentificationAssociationType} for custom type 4.
     *
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int USER_IDENTIFICATION_ASSOCIATION_TYPE_CUSTOM_4 = 104;

    /**
     *  User HAL's user identification association types
     *
     * @hide
     */
    @IntDef(prefix = { "USER_IDENTIFICATION_ASSOCIATION_TYPE_" }, value = {
            USER_IDENTIFICATION_ASSOCIATION_TYPE_KEY_FOB,
            USER_IDENTIFICATION_ASSOCIATION_TYPE_CUSTOM_1,
            USER_IDENTIFICATION_ASSOCIATION_TYPE_CUSTOM_2,
            USER_IDENTIFICATION_ASSOCIATION_TYPE_CUSTOM_3,
            USER_IDENTIFICATION_ASSOCIATION_TYPE_CUSTOM_4,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface UserIdentificationAssociationType{}

    /**
     * {@link UserIdentificationAssociationSetValue} to associate the identification type with the
     * current foreground Android user.
     *
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int USER_IDENTIFICATION_ASSOCIATION_SET_VALUE_ASSOCIATE_CURRENT_USER = 1;

    /**
     * {@link UserIdentificationAssociationSetValue} to disassociate the identification type from
     * the current foreground Android user.
     *
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int USER_IDENTIFICATION_ASSOCIATION_SET_VALUE_DISASSOCIATE_CURRENT_USER = 2;

    /**
     * {@link UserIdentificationAssociationSetValue} to disassociate the identification type from
     * all Android users.
     *
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int USER_IDENTIFICATION_ASSOCIATION_SET_VALUE_DISASSOCIATE_ALL_USERS = 3;

    /**
     * User HAL's user identification association types
     *
     * @hide
     */
    @IntDef(prefix = { "USER_IDENTIFICATION_ASSOCIATION_SET_VALUE_" }, value = {
            USER_IDENTIFICATION_ASSOCIATION_SET_VALUE_ASSOCIATE_CURRENT_USER,
            USER_IDENTIFICATION_ASSOCIATION_SET_VALUE_DISASSOCIATE_CURRENT_USER,
            USER_IDENTIFICATION_ASSOCIATION_SET_VALUE_DISASSOCIATE_ALL_USERS,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface UserIdentificationAssociationSetValue{}

    /**
     * {@link UserIdentificationAssociationValue} when the status of an association could not be
     * determined.
     *
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int USER_IDENTIFICATION_ASSOCIATION_VALUE_UNKNOWN = 1;

    /**
     * {@link UserIdentificationAssociationValue} when the identification type is associated with
     * the current foreground Android user.
     *
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int USER_IDENTIFICATION_ASSOCIATION_VALUE_ASSOCIATE_CURRENT_USER = 2;

    /**
     * {@link UserIdentificationAssociationValue} when the identification type is associated with
     * another Android user.
     *
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int USER_IDENTIFICATION_ASSOCIATION_VALUE_ASSOCIATED_ANOTHER_USER = 3;

    /**
     * {@link UserIdentificationAssociationValue} when the identification type is not associated
     * with any Android user.
     *
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int USER_IDENTIFICATION_ASSOCIATION_VALUE_NOT_ASSOCIATED_ANY_USER = 4;

    /**
     * User HAL's user identification association types
     *
     * @hide
     */
    @IntDef(prefix = { "USER_IDENTIFICATION_ASSOCIATION_VALUE_" }, value = {
            USER_IDENTIFICATION_ASSOCIATION_VALUE_UNKNOWN,
            USER_IDENTIFICATION_ASSOCIATION_VALUE_ASSOCIATE_CURRENT_USER,
            USER_IDENTIFICATION_ASSOCIATION_VALUE_ASSOCIATED_ANOTHER_USER,
            USER_IDENTIFICATION_ASSOCIATION_VALUE_NOT_ASSOCIATED_ANY_USER,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface UserIdentificationAssociationValue{}

    private final Object mLock = new Object();

    private final ICarUserService mService;
    private final UserManager mUserManager;

    /**
     * Map of listeners registers by the app.
     */
    @Nullable
    @GuardedBy("mLock")
    private ArrayMap<UserLifecycleListener, Pair<UserLifecycleEventFilter, Executor>> mListeners;

    /**
     * Receiver used to receive user-lifecycle callbacks from the service.
     */
    @Nullable
    @GuardedBy("mLock")
    private LifecycleResultReceiver mReceiver;

    private final Dumper mDumper;

    /**
     * Logs the number of received events so it's shown on {@code Dumper.dump()}.
     */
    private int mNumberReceivedEvents;

    /**
     * Logs the received events so they're shown on {@code Dumper.dump()}.
     *
     * <p><b>Note</b>: these events are only logged when {@link #VERBOSE} is {@code true}.
     */
    @Nullable
    private List<UserLifecycleEvent> mEvents;

    /**
     * @hide
     */
    public CarUserManager(@NonNull Car car, @NonNull IBinder service) {
        this(car, ICarUserService.Stub.asInterface(service),
                car.getContext().getSystemService(UserManager.class));
    }

    /**
     * @hide
     */
    @VisibleForTesting
    public CarUserManager(@NonNull Car car, @NonNull ICarUserService service,
            @NonNull UserManager userManager) {
        super(car);

        mDumper = addDumpable(car.getContext(), () -> new Dumper());
        Log.d(TAG, "CarUserManager(): DBG= " + DBG + ", mDumper=" + mDumper);

        mService = service;
        mUserManager = userManager;
    }

    /**
     * Switches the foreground user to the given target user.
     *
     * @hide
     */
    @TestApi
    @RequiresPermission(anyOf = {android.Manifest.permission.MANAGE_USERS,
            android.Manifest.permission.CREATE_USERS})
    @AddedInOrBefore(majorVersion = 33)
    public AsyncFuture<UserSwitchResult> switchUser(@UserIdInt int targetUserId) {
        int uid = myUid();
        try {
            AndroidFuture<UserSwitchResult> future = new AndroidFuture<UserSwitchResult>() {
                @Override
                protected void onCompleted(UserSwitchResult result, Throwable err) {
                    if (result != null) {
                        EventLogHelper.writeCarUserManagerSwitchUserResp(uid,
                                result.getStatus(), result.getErrorMessage());
                    } else {
                        Log.w(TAG, "switchUser(" + targetUserId + ") failed: " + err);
                    }
                    super.onCompleted(result, err);
                }
            };
            EventLogHelper.writeCarUserManagerSwitchUserReq(uid, targetUserId);
            mService.switchUser(targetUserId, HAL_TIMEOUT_MS, future);
            return new AndroidAsyncFuture<>(future);
        } catch (SecurityException e) {
            throw e;
        } catch (RemoteException | RuntimeException e) {
            AsyncFuture<UserSwitchResult> future =
                    newSwitchResuiltForFailure(UserSwitchResult.STATUS_HAL_INTERNAL_FAILURE);
            return handleExceptionFromCarService(e, future);
        }
    }

    /**
     * Logouts the current user (if it was switched to by a device admin).
     *
     * @hide
     */
    @RequiresPermission(anyOf = {android.Manifest.permission.MANAGE_USERS,
            android.Manifest.permission.CREATE_USERS})
    @AddedInOrBefore(majorVersion = 33)
    public AsyncFuture<UserSwitchResult> logoutUser() {
        int uid = myUid();
        try {
            AndroidFuture<UserSwitchResult> future = new AndroidFuture<UserSwitchResult>() {
                @Override
                protected void onCompleted(UserSwitchResult result, Throwable err) {
                    if (result != null) {
                        EventLogHelper.writeCarUserManagerLogoutUserResp(uid,
                                result.getStatus(), result.getErrorMessage());
                    } else {
                        Log.w(TAG, "logoutUser() failed: " + err);
                    }
                    super.onCompleted(result, err);
                }
            };
            EventLogHelper.writeCarUserManagerLogoutUserReq(uid);
            mService.logoutUser(HAL_TIMEOUT_MS, future);
            return new AndroidAsyncFuture<>(future);
        } catch (SecurityException e) {
            throw e;
        } catch (RemoteException | RuntimeException e) {
            AsyncFuture<UserSwitchResult> future =
                    newSwitchResuiltForFailure(UserSwitchResult.STATUS_HAL_INTERNAL_FAILURE);
            return handleExceptionFromCarService(e, future);
        }
    }

    private AndroidAsyncFuture<UserSwitchResult> newSwitchResuiltForFailure(
            @UserSwitchResult.Status int status) {
        AndroidFuture<UserSwitchResult> future = new AndroidFuture<>();
        future.complete(new UserSwitchResult(status, null));
        return new AndroidAsyncFuture<>(future);
    }

    private AsyncFuture<UserCreationResult> createUser(@Nullable String name,
            @NonNull String userType, int flags) {
        int uid = myUid();
        try {
            AndroidFuture<UserCreationResult> future = new AndroidFuture<UserCreationResult>() {
                @Override
                protected void onCompleted(UserCreationResult result, Throwable err) {
                    if (result != null) {
                        EventLogHelper.writeCarUserManagerCreateUserResp(uid,
                                result.getStatus(), result.getErrorMessage());
                    } else {
                        Log.w(TAG, "createUser(" + userType + "," + flags
                                + ") failed: " + err);
                    }
                    super.onCompleted(result, err);
                };
            };
            EventLogHelper.writeCarUserManagerCreateUserReq(uid,
                    UserHelperLite.safeName(name), userType, flags);
            mService.createUser(name, userType, flags, HAL_TIMEOUT_MS, future);
            return new AndroidAsyncFuture<>(future);
        } catch (SecurityException e) {
            throw e;
        } catch (RemoteException | RuntimeException e) {
            AndroidFuture<UserCreationResult> future = new AndroidFuture<>();
            future.complete(new UserCreationResult(UserCreationResult.STATUS_HAL_INTERNAL_FAILURE));
            return handleExceptionFromCarService(e, new AndroidAsyncFuture<>(future));
        }
    }

    /**
     * Creates a new guest Android user.
     *
     * @hide
     */
    @RequiresPermission(anyOf = {android.Manifest.permission.MANAGE_USERS,
            android.Manifest.permission.CREATE_USERS})
    @AddedInOrBefore(majorVersion = 33)
    public AsyncFuture<UserCreationResult> createGuest(@Nullable String name) {
        return createUser(name, UserManager.USER_TYPE_FULL_GUEST, /* flags= */ 0);
    }

    /**
     * Creates a new Android user.
     *
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    @RequiresPermission(anyOf = {android.Manifest.permission.MANAGE_USERS,
            android.Manifest.permission.CREATE_USERS})
    public AsyncFuture<UserCreationResult> createUser(@Nullable String name,
            int flags) {
        return createUser(name, UserManager.USER_TYPE_FULL_SECONDARY, flags);
    }

    /**
     * Updates pre-created users.
     * <p>
     * Updates pre-created users based on the car properties defined
     * {@code CarProperties.number_pre_created_guests} and (@code
     * CarProperties.number_pre_created_users}.
     *
     * @hide
     */
    @RequiresPermission(anyOf = {android.Manifest.permission.MANAGE_USERS,
            android.Manifest.permission.CREATE_USERS})
    @AddedInOrBefore(majorVersion = 33)
    public void updatePreCreatedUsers() {
        int uid = myUid();
        EventLogHelper.writeCarUserManagerPreCreateUserReq(uid);
        try {
            mService.updatePreCreatedUsers();
        } catch (SecurityException e) {
            throw e;
        } catch (RemoteException | RuntimeException e) {
            handleExceptionFromCarService(e, null);
        }
    }

    /**
     * Removes the given user.
     *
     * @param userId identification of the user to be removed.
     *
     * @return whether the user was successfully removed.
     *
     * @hide
     */
    @RequiresPermission(anyOf = {android.Manifest.permission.MANAGE_USERS,
            android.Manifest.permission.CREATE_USERS})
    @NonNull
    @AddedInOrBefore(majorVersion = 33)
    public UserRemovalResult removeUser(@UserIdInt int userId) {
        int uid = myUid();
        EventLogHelper.writeCarUserManagerRemoveUserReq(uid, userId);
        int status = UserRemovalResult.STATUS_ANDROID_FAILURE;
        try {
            AndroidFuture<UserRemovalResult> future = new AndroidFuture<UserRemovalResult>();
            mService.removeUser(userId, future);
            UserRemovalResult result = future.get(REMOVE_USER_CALL_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS);
            status = result.getStatus();
            return result;
        } catch (SecurityException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new UserRemovalResult(UserRemovalResult.STATUS_ANDROID_FAILURE);
        } catch (ExecutionException | TimeoutException e) {
            return new UserRemovalResult(UserRemovalResult.STATUS_ANDROID_FAILURE);
        } catch (RemoteException | RuntimeException e) {
            return handleExceptionFromCarService(e,
                    new UserRemovalResult(UserRemovalResult.STATUS_ANDROID_FAILURE));
        } finally {
            EventLogHelper.writeCarUserManagerRemoveUserResp(uid, status);
        }
    }

    /**
     * Adds a listener for {@link UserLifecycleEvent user lifecycle events}.
     *
     * @throws IllegalStateException if the listener was already added.
     *
     * @hide
     */
    @SystemApi
    @TestApi
    @RequiresPermission(anyOf = {INTERACT_ACROSS_USERS, INTERACT_ACROSS_USERS_FULL})
    @AddedInOrBefore(majorVersion = 33)
    public void addListener(@NonNull @CallbackExecutor Executor executor,
            @NonNull UserLifecycleListener listener) {
        addListenerInternal(executor, /* filter= */null, listener);
    }

    /**
     * Adds a listener for {@link UserLifecycleEvent user lifecycle events} with a filter that can
     * specify a specific event type or a user id.
     *
     * @throws IllegalStateException if the listener was already added.
     *
     * @hide
     */
    @SystemApi
    @TestApi
    @RequiresPermission(anyOf = {INTERACT_ACROSS_USERS, INTERACT_ACROSS_USERS_FULL})
    @AddedInOrBefore(majorVersion = 33)
    public void addListener(@NonNull @CallbackExecutor Executor executor,
            @NonNull UserLifecycleEventFilter filter, @NonNull UserLifecycleListener listener) {
        Objects.requireNonNull(filter, "filter cannot be null");

        addListenerInternal(executor, filter, listener);
    }

    private void addListenerInternal(@CallbackExecutor Executor executor,
            @Nullable UserLifecycleEventFilter filter, UserLifecycleListener listener) {
        Objects.requireNonNull(executor, "executor cannot be null");
        Objects.requireNonNull(listener, "listener cannot be null");

        int uid = myUid();
        String packageName = getContext().getPackageName();
        if (DBG) {
            Log.d(TAG, "addListener(): uid=" + uid + ", pkg=" + packageName
                    + ", listener=" + listener + ", filter= " + filter);
        }
        synchronized (mLock) {
            Preconditions.checkState(mListeners == null || !mListeners.containsKey(listener),
                    "already called for this listener");
            if (mReceiver == null) {
                mReceiver = new LifecycleResultReceiver();
                if (DBG) {
                    Log.d(TAG, "Setting lifecycle receiver with filter " + filter
                            + " for uid " + uid + " and package " + packageName);
                }
            } else {
                if (DBG) {
                    Log.d(TAG, "Already set receiver for uid " + uid + " and package "
                            + packageName + " adding new filter " + filter);
                }
            }
            try {
                boolean hasFilter = filter != null;
                EventLogHelper.writeCarUserManagerAddListener(uid, packageName, hasFilter);
                mService.setLifecycleListenerForApp(packageName, filter, mReceiver);
            } catch (RemoteException e) {
                handleRemoteExceptionFromCarService(e);
            }

            if (mListeners == null) {
                mListeners = new ArrayMap<>(1); // Most likely app will have just one listener
            } else if (DBG) {
                Log.d(TAG, "addListener(" + getLambdaName(listener) + "): context " + getContext()
                        + " already has " + mListeners.size() + " listeners: "
                        + mListeners.keySet().stream()
                                .map((l) -> getLambdaName(l))
                                .collect(Collectors.toList()), new Exception("caller's stack"));
            }
            if (DBG) Log.d(TAG, "Adding listener: " + listener + " with filter " + filter);
            mListeners.put(listener, Pair.create(filter, executor));
        }
    }

    /**
     * Removes a listener for {@link UserLifecycleEvent user lifecycle events}.
     *
     * @throws IllegalStateException if the listener was not added beforehand.
     *
     * @hide
     */
    @SystemApi
    @TestApi
    @RequiresPermission(anyOf = {INTERACT_ACROSS_USERS, INTERACT_ACROSS_USERS_FULL})
    @AddedInOrBefore(majorVersion = 33)
    public void removeListener(@NonNull UserLifecycleListener listener) {
        Objects.requireNonNull(listener, "listener cannot be null");

        int uid = myUid();
        String packageName = getContext().getPackageName();
        if (DBG) {
            Log.d(TAG, "removeListener(): uid=" + uid + ", pkg=" + packageName
                    + ", listener=" + listener);
        }
        synchronized (mLock) {
            Preconditions.checkState(mListeners != null && mListeners.containsKey(listener),
                    "not called for this listener yet");
            mListeners.remove(listener);

            // Note that there can be some rare corner cases that a listener is removed but its
            // corresponding filter remains in the service side. This may cause slight inefficiency
            // due to unnecessary receiver calls. It will still be functionally correct, because the
            // removed listener will no longer be invoked.
            if (!mListeners.isEmpty()) {
                if (DBG) Log.d(TAG, "removeListeners(): still " + mListeners.size() + " left");
                return;
            }
            mListeners = null;

            if (mReceiver == null) {
                Log.wtf(TAG, "removeListener(): receiver already null");
                return;
            }

            EventLogHelper.writeCarUserManagerRemoveListener(uid, packageName);
            if (DBG) {
                Log.d(TAG, "Removing lifecycle receiver for uid=" + uid + " and package "
                        + packageName);
            }
            try {
                mService.resetLifecycleListenerForApp(mReceiver);
                mReceiver = null;
            } catch (RemoteException e) {
                handleRemoteExceptionFromCarService(e);
            }
        }
    }

    /**
     * Check if user hal supports user association.
     *
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public boolean isUserHalUserAssociationSupported() {
        try {
            return mService.isUserHalUserAssociationSupported();
        } catch (RemoteException | RuntimeException e) {
            return handleExceptionFromCarService(e, false);
        }
    }

    /**
     * Gets the user authentication types associated with this manager's user.
     *
     * @hide
     */
    @NonNull
    @RequiresPermission(anyOf = {android.Manifest.permission.MANAGE_USERS,
            android.Manifest.permission.CREATE_USERS})
    @AddedInOrBefore(majorVersion = 33)
    public UserIdentificationAssociationResponse getUserIdentificationAssociation(
            @UserIdentificationAssociationType int... types) {
        Preconditions.checkArgument(!ArrayUtils.isEmpty(types), "must have at least one type");
        EventLogHelper.writeCarUserManagerGetUserAuthReq(convertToObjectArray(types));
        try {
            UserIdentificationAssociationResponse response =
                    mService.getUserIdentificationAssociation(types);
            if (response != null) {
                int[] values = response.getValues();
                EventLogHelper.writeCarUserManagerGetUserAuthResp(convertToObjectArray(values));
            }
            return response;
        } catch (SecurityException e) {
            throw e;
        } catch (RemoteException | RuntimeException e) {
            return handleExceptionFromCarService(e,
                    UserIdentificationAssociationResponse.forFailure(e.getMessage()));
        }
    }

    private Object[] convertToObjectArray(int[] input) {
        if (input == null) return null;
        Object[] output = new Object[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
        return output;
    }

    /**
     * Sets the user authentication types associated with this manager's user.
     *
     * @hide
     */
    @NonNull
    @RequiresPermission(anyOf = {android.Manifest.permission.MANAGE_USERS,
            android.Manifest.permission.CREATE_USERS})
    @AddedInOrBefore(majorVersion = 33)
    public AsyncFuture<UserIdentificationAssociationResponse> setUserIdentificationAssociation(
            @UserIdentificationAssociationType int[] types,
            @UserIdentificationAssociationSetValue int[] values) {
        Preconditions.checkArgument(!ArrayUtils.isEmpty(types), "must have at least one type");
        Preconditions.checkArgument(!ArrayUtils.isEmpty(values), "must have at least one value");
        if (types.length != values.length) {
            throw new IllegalArgumentException("types (" + Arrays.toString(types) + ") and values ("
                    + Arrays.toString(values) + ") should have the same length");
        }
        // TODO(b/153900032): move this logic to a common helper
        Object[] loggedValues = new Integer[types.length * 2];
        for (int i = 0; i < types.length; i++) {
            loggedValues[i * 2] = types[i];
            loggedValues[i * 2 + 1 ] = values[i];
        }
        EventLogHelper.writeCarUserManagerSetUserAuthReq(loggedValues);

        try {
            AndroidFuture<UserIdentificationAssociationResponse> future =
                    new AndroidFuture<UserIdentificationAssociationResponse>() {
                @Override
                protected void onCompleted(UserIdentificationAssociationResponse result,
                        Throwable err) {
                    if (result != null) {
                        int[] rawValues = result.getValues();
                        // TODO(b/153900032): move this logic to a common helper
                        if (rawValues != null) {
                            Object[] loggedValues = new Object[rawValues.length];
                            for (int i = 0; i < rawValues.length; i++) {
                                loggedValues[i] = rawValues[i];
                            }
                            EventLogHelper.writeCarUserManagerSetUserAuthResp(loggedValues);
                        }
                    } else {
                        Log.w(TAG, "setUserIdentificationAssociation(" + Arrays.toString(types)
                                + ", " + Arrays.toString(values) + ") failed: " + err);
                    }
                    super.onCompleted(result, err);
                };
            };
            mService.setUserIdentificationAssociation(HAL_TIMEOUT_MS, types, values, future);
            return new AndroidAsyncFuture<>(future);
        } catch (SecurityException e) {
            throw e;
        } catch (RemoteException | RuntimeException e) {
            AndroidFuture<UserIdentificationAssociationResponse> future = new AndroidFuture<>();
            future.complete(UserIdentificationAssociationResponse.forFailure());
            return handleExceptionFromCarService(e, new AndroidAsyncFuture<>(future));
        }
    }

    /**
     * Sets a callback to be notified before user switch. It should only be used by Car System UI.
     *
     * @hide
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_USERS)
    @AddedInOrBefore(majorVersion = 33)
    public void setUserSwitchUiCallback(@NonNull UserSwitchUiCallback callback) {
        Preconditions.checkArgument(callback != null, "Null callback");
        UserSwitchUiCallbackReceiver userSwitchUiCallbackReceiver =
                new UserSwitchUiCallbackReceiver(callback);
        try {
            mService.setUserSwitchUiCallback(userSwitchUiCallbackReceiver);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * {@code ICarResultReceiver} used to receive user switch UI Callback.
     */
    // TODO(b/154958003): use mReceiver instead as now there are two binder objects
    private final class UserSwitchUiCallbackReceiver extends ICarResultReceiver.Stub {

        private final UserSwitchUiCallback mUserSwitchUiCallback;

        UserSwitchUiCallbackReceiver(UserSwitchUiCallback callback) {
            mUserSwitchUiCallback = callback;
        }

        @Override
        public void send(int userId, Bundle unused) throws RemoteException {
            mUserSwitchUiCallback.showUserSwitchDialog(userId);
        }
    }

    /**
     * {@code ICarResultReceiver} used to receive lifecycle events and dispatch to the proper
     * listener.
     */
    private class LifecycleResultReceiver extends ICarResultReceiver.Stub {
        @Override
        public void send(int resultCode, Bundle resultData) {
            if (resultData == null) {
                Log.w(TAG, "Received result (" + resultCode + ") without data");
                return;
            }
            int from = resultData.getInt(BUNDLE_PARAM_PREVIOUS_USER_ID,
                    UserManagerHelper.USER_NULL);
            int to = resultCode;
            int eventType = resultData.getInt(BUNDLE_PARAM_ACTION);
            UserLifecycleEvent event = new UserLifecycleEvent(eventType, from, to);
            ArrayMap<UserLifecycleListener, Pair<UserLifecycleEventFilter, Executor>> listeners;
            synchronized (mLock) {
                if (mListeners == null) {
                    Log.w(TAG, "No listeners for event " + event);
                    return;
                }
                listeners = new ArrayMap<>(mListeners);
            }
            int size = listeners.size();
            EventLogHelper.writeCarUserManagerNotifyLifecycleListener(size, eventType, from, to);
            for (int i = 0; i < size; i++) {
                UserLifecycleListener listener = listeners.keyAt(i);
                UserLifecycleEventFilter filter = listeners.valueAt(i).first;
                if (filter != null && !filter.apply(event)) {
                    if (DBG) {
                        Log.d(TAG, "Listener " + getLambdaName(listener)
                                + " is skipped for the event " + event + " due to the filter "
                                + filter);
                    }
                    continue;
                }
                Executor executor = listeners.valueAt(i).second;
                if (DBG) {
                    Log.d(TAG, "Calling " + getLambdaName(listener) + " for event " + event);
                }
                executor.execute(() -> listener.onEvent(event));
            }
            mNumberReceivedEvents++;
            if (VERBOSE) {
                if (mEvents == null) {
                    mEvents = new ArrayList<>();
                }
                mEvents.add(event);
            }
        }
    }

    /** @hide */
    @Override
    @AddedInOrBefore(majorVersion = 33)
    public void onCarDisconnected() {
        // nothing to do
    }

    private final class Dumper implements Dumpable {
        @Override
        public void dump(PrintWriter pw, String[] args) {
            String prefix = "  ";

            pw.printf("DBG=%b, VERBOSE=%b\n", DBG, VERBOSE);
            int listenersSize = 0;
            synchronized (mLock) {
                pw.printf("mReceiver: %s\n", mReceiver);
                if (mListeners == null) {
                    pw.println("no listeners");
                } else {
                    listenersSize = mListeners.size();
                    pw.printf("%d listeners\n", listenersSize);
                }
                if (DBG) {
                    for (int i = 0; i < listenersSize; i++) {
                        pw.printf("%s%d: %s\n", prefix, i + 1, mListeners.keyAt(i));
                    }
                }
            }
            pw.printf("mNumberReceivedEvents: %d\n", mNumberReceivedEvents);
            if (VERBOSE && mEvents != null) {
                for (int i = 0; i < mEvents.size(); i++) {
                    pw.printf("%s%d: %s\n", prefix, i + 1, mEvents.get(i));
                }
            }
        }

        @Override
        public String getDumpableName() {
            return CarUserManager.class.getSimpleName();
        }
    }

    /**
     * @hide
     */
    @TestApi
    @AddedInOrBefore(majorVersion = 33)
    public static String lifecycleEventTypeToString(@UserLifecycleEventType int type) {
        switch (type) {
            case USER_LIFECYCLE_EVENT_TYPE_STARTING:
                return "STARTING";
            case USER_LIFECYCLE_EVENT_TYPE_SWITCHING:
                return "SWITCHING";
            case USER_LIFECYCLE_EVENT_TYPE_UNLOCKING:
                return "UNLOCKING";
            case USER_LIFECYCLE_EVENT_TYPE_UNLOCKED:
                return "UNLOCKED";
            case USER_LIFECYCLE_EVENT_TYPE_STOPPING:
                return "STOPPING";
            case USER_LIFECYCLE_EVENT_TYPE_STOPPED:
                return "STOPPED";
            case USER_LIFECYCLE_EVENT_TYPE_POST_UNLOCKED:
                return "POST_UNLOCKED";
            case USER_LIFECYCLE_EVENT_TYPE_CREATED:
                return "CREATED";
            case USER_LIFECYCLE_EVENT_TYPE_REMOVED:
                return "REMOVED";
            default:
                return "UNKNOWN-" + type;
        }
    }

    /**
     * Checks if the given {@code userId} represents a valid user.
     *
     * <p>A "valid" user:
     *
     * <ul>
     *   <li>Must exist in the device.
     *   <li>Is not in the process of being deleted.
     *   <li>Cannot be the {@link UserHandle#isSystem() system} user on devices that use
     *   {@link UserManager#isHeadlessSystemUserMode() headless system mode}.
     * </ul>
     *
     * @hide
     */
    @RequiresPermission(anyOf = {android.Manifest.permission.MANAGE_USERS,
            android.Manifest.permission.CREATE_USERS})
    @AddedInOrBefore(majorVersion = 33)
    public boolean isValidUser(@UserIdInt int userId) {
        List<UserHandle> allUsers = mUserManager.getUserHandles(/* excludeDying=*/ true);
        for (int i = 0; i < allUsers.size(); i++) {
            UserHandle user = allUsers.get(i);
            if (user.getIdentifier() == userId && (userId != UserHandle.SYSTEM.getIdentifier()
                    || !UserManager.isHeadlessSystemUserMode())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Defines a lifecycle event for an Android user.
     *
     * @hide
     */
    @SystemApi
    @TestApi
    public static final class UserLifecycleEvent {
        private final @UserLifecycleEventType int mEventType;
        private final @UserIdInt int mUserId;
        private final @UserIdInt int mPreviousUserId;

        /** @hide */
        public UserLifecycleEvent(@UserLifecycleEventType int eventType,
                @UserIdInt int from, @UserIdInt int to) {
            mEventType = eventType;
            mPreviousUserId = from;
            mUserId = to;
        }

        /** @hide */
        public UserLifecycleEvent(@UserLifecycleEventType int eventType, @UserIdInt int to) {
            this(eventType, UserManagerHelper.USER_NULL, to);
        }

        /**
         * Gets the event type.
         *
         * @return either {@link CarUserManager#USER_LIFECYCLE_EVENT_TYPE_STARTING},
         * {@link CarUserManager#USER_LIFECYCLE_EVENT_TYPE_SWITCHING},
         * {@link CarUserManager#USER_LIFECYCLE_EVENT_TYPE_UNLOCKING},
         * {@link CarUserManager#USER_LIFECYCLE_EVENT_TYPE_UNLOCKED},
         * {@link CarUserManager#USER_LIFECYCLE_EVENT_TYPE_STOPPING} or
         * {@link CarUserManager#USER_LIFECYCLE_EVENT_TYPE_STOPPED} for all apps;
         * for apps {@link CarPackageManager#getTargetCarVersion() targeting car version}
         * {@link CarVersion.VERSION_CODES#TIRAMISU_1} or higher, it could be new types
         * added on later releases, such as
         * {@link CarUserManager#USER_LIFECYCLE_EVENT_TYPE_CREATED},
         * {@link CarUserManager#USER_LIFECYCLE_EVENT_TYPE_REMOVED} and possibly others.
         *
         */
        @UserLifecycleEventType
        @AddedInOrBefore(majorVersion = 33)
        public int getEventType() {
            return mEventType;
        }

        /**
         * Gets the id of the user whose event is being reported.
         *
         * @hide
         */
        @UserIdInt
        @AddedInOrBefore(majorVersion = 33)
        public int getUserId() {
            return mUserId;
        }

        /**
         * Gets the handle of the user whose event is being reported.
         */
        @NonNull
        @AddedInOrBefore(majorVersion = 33)
        public UserHandle getUserHandle() {
            return UserHandle.of(mUserId);
        }

        /**
         * Gets the id of the user being switched from.
         *
         * <p>This method returns {@link UserHandle#USER_NULL} for all event types but
         * {@link CarUserManager#USER_LIFECYCLE_EVENT_TYPE_SWITCHING}.
         *
         * @hide
         */
        @UserIdInt
        @AddedInOrBefore(majorVersion = 33)
        public int getPreviousUserId() {
            return mPreviousUserId;
        }

        /**
         * Gets the handle of the user being switched from.
         *
         * <p>This method returns {@code null} for all event types but
         * {@link CarUserManager#USER_LIFECYCLE_EVENT_TYPE_SWITCHING}.
         */
        @Nullable
        @AddedInOrBefore(majorVersion = 33)
        public UserHandle getPreviousUserHandle() {
            return mPreviousUserId == UserManagerHelper.USER_NULL ? null
                    : UserHandle.of(mPreviousUserId);
        }

        @Override
        @AddedInOrBefore(majorVersion = 33)
        public String toString() {
            StringBuilder builder = new StringBuilder("Event[type=")
                    .append(lifecycleEventTypeToString(mEventType));
            if (mPreviousUserId != UserManagerHelper.USER_NULL) {
                builder
                    .append(",from=").append(mPreviousUserId)
                    .append(",to=").append(mUserId);
            } else {
                builder.append(",user=").append(mUserId);
            }

            return builder.append(']').toString();
        }

        @Override
        @AddedInOrBefore(majorVersion = 33)
        public boolean equals(@Nullable Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            UserLifecycleEvent that = (UserLifecycleEvent) o;
            return mEventType == that.mEventType && mUserId == that.mUserId
                    && mPreviousUserId == that.mPreviousUserId;
        }

        @Override
        @AddedInOrBefore(majorVersion = 33)
        public int hashCode() {
            int hash = 23;
            hash = 17 * hash + mEventType;
            hash = 17 * hash + mUserId;
            hash = 17 * hash + mPreviousUserId;

            return hash;
        }
    }

    /**
     * Listener for Android User lifecycle events.
     *
     * <p>Must be registered using {@link CarUserManager#addListener(UserLifecycleListener)} and
     * unregistered through {@link CarUserManager#removeListener(UserLifecycleListener)}.
     *
     * @hide
     */
    @SystemApi
    @TestApi
    public interface UserLifecycleListener {

        /**
         * Called to notify the given {@code event}.
         */
        @AddedInOrBefore(majorVersion = 33)
        void onEvent(@NonNull UserLifecycleEvent event);
    }

    /**
     * Callback for notifying user switch before switch started.
     *
     * <p> It should only be user by Car System UI. The purpose of this callback is notify the
     * Car System UI to display the user switch UI.
     *
     * @hide
     */
    public interface UserSwitchUiCallback {

        /**
         * Called to notify that user switch dialog should be shown now.
         */
        @AddedInOrBefore(majorVersion = 33)
        void showUserSwitchDialog(@UserIdInt int userId);
    }
}
