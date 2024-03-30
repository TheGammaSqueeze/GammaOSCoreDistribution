/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.user;

import static android.Manifest.permission.CREATE_USERS;
import static android.Manifest.permission.MANAGE_USERS;
import static android.car.builtin.os.UserManagerHelper.USER_NULL;
import static android.car.drivingstate.CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP;

import static com.android.car.CarServiceUtils.toIntArray;
import static com.android.car.PermissionHelper.checkHasAtLeastOnePermissionGranted;
import static com.android.car.PermissionHelper.checkHasDumpPermissionGranted;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.car.Car;
import android.car.CarVersion;
import android.car.ICarResultReceiver;
import android.car.ICarUserService;
import android.car.PlatformVersion;
import android.car.builtin.app.ActivityManagerHelper;
import android.car.builtin.content.pm.PackageManagerHelper;
import android.car.builtin.os.TraceHelper;
import android.car.builtin.os.UserManagerHelper;
import android.car.builtin.util.EventLogHelper;
import android.car.builtin.util.Slogf;
import android.car.builtin.util.TimingsTraceLog;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.ICarUxRestrictionsChangeListener;
import android.car.settings.CarSettings;
import android.car.user.CarUserManager;
import android.car.user.CarUserManager.UserIdentificationAssociationSetValue;
import android.car.user.CarUserManager.UserIdentificationAssociationType;
import android.car.user.CarUserManager.UserLifecycleEvent;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.car.user.UserCreationResult;
import android.car.user.UserIdentificationAssociationResponse;
import android.car.user.UserLifecycleEventFilter;
import android.car.user.UserRemovalResult;
import android.car.user.UserStartResult;
import android.car.user.UserStopResult;
import android.car.user.UserSwitchResult;
import android.car.util.concurrent.AndroidFuture;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.hardware.automotive.vehicle.CreateUserRequest;
import android.hardware.automotive.vehicle.CreateUserStatus;
import android.hardware.automotive.vehicle.InitialUserInfoRequestType;
import android.hardware.automotive.vehicle.InitialUserInfoResponseAction;
import android.hardware.automotive.vehicle.RemoveUserRequest;
import android.hardware.automotive.vehicle.SwitchUserRequest;
import android.hardware.automotive.vehicle.SwitchUserStatus;
import android.hardware.automotive.vehicle.UserIdentificationGetRequest;
import android.hardware.automotive.vehicle.UserIdentificationResponse;
import android.hardware.automotive.vehicle.UserIdentificationSetAssociation;
import android.hardware.automotive.vehicle.UserIdentificationSetRequest;
import android.hardware.automotive.vehicle.UserInfo;
import android.hardware.automotive.vehicle.UsersInfo;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.NewUserRequest;
import android.os.NewUserResponse;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.Display;

import com.android.car.CarLog;
import com.android.car.CarServiceBase;
import com.android.car.CarServiceUtils;
import com.android.car.CarUxRestrictionsManagerService;
import com.android.car.R;
import com.android.car.hal.HalCallback;
import com.android.car.hal.UserHalHelper;
import com.android.car.hal.UserHalService;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.ICarServiceHelper;
import com.android.car.internal.common.CommonConstants.UserLifecycleEventType;
import com.android.car.internal.common.UserHelperLite;
import com.android.car.internal.os.CarSystemProperties;
import com.android.car.internal.util.ArrayUtils;
import com.android.car.internal.util.DebugUtils;
import com.android.car.internal.util.FunctionalUtils;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.pm.CarPackageManagerService;
import com.android.car.power.CarPowerManagementService;
import com.android.car.user.InitialUserSetter.InitialUserInfo;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * User service for cars.
 */
public final class CarUserService extends ICarUserService.Stub implements CarServiceBase {

    @VisibleForTesting
    static final String TAG = CarLog.tagFor(CarUserService.class);

    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);

    /** {@code int} extra used to represent a user id in a {@link ICarResultReceiver} response. */
    public static final String BUNDLE_USER_ID = "user.id";
    /** {@code int} extra used to represent user flags in a {@link ICarResultReceiver} response. */
    public static final String BUNDLE_USER_FLAGS = "user.flags";
    /**
     * {@code String} extra used to represent a user name in a {@link ICarResultReceiver} response.
     */
    public static final String BUNDLE_USER_NAME = "user.name";
    /**
     * {@code int} extra used to represent the user locales in a {@link ICarResultReceiver}
     * response.
     */
    public static final String BUNDLE_USER_LOCALES = "user.locales";
    /**
     * {@code int} extra used to represent the info action in a {@link ICarResultReceiver} response.
     */
    public static final String BUNDLE_INITIAL_INFO_ACTION = "initial_info.action";

    public static final String VEHICLE_HAL_NOT_SUPPORTED = "Vehicle Hal not supported.";

    public static final String HANDLER_THREAD_NAME = "UserService";

    // Constants below must match value of same constants defined by ActivityManager
    public static final int USER_OP_SUCCESS = 0;
    public static final int USER_OP_UNKNOWN_USER = -1;
    public static final int USER_OP_IS_CURRENT = -2;
    public static final int USER_OP_ERROR_IS_SYSTEM = -3;
    public static final int USER_OP_ERROR_RELATED_USERS_CANNOT_STOP = -4;

    @VisibleForTesting
    static final String ERROR_TEMPLATE_NON_ADMIN_CANNOT_CREATE_ADMIN_USERS =
            "Non-admin user %d can only create non-admin users";

    @VisibleForTesting
    static final String ERROR_TEMPLATE_INVALID_USER_TYPE_AND_FLAGS_COMBINATION =
            "Invalid combination of user type(%s) and flags (%d) for caller with restrictions";

    @VisibleForTesting
    static final String ERROR_TEMPLATE_INVALID_FLAGS_FOR_GUEST_CREATION =
            "Invalid flags %d specified when creating a guest user %s";

    @VisibleForTesting
    static final String ERROR_TEMPLATE_DISALLOW_ADD_USER =
            "Cannot create user because calling user %s has the '%s' restriction";

    private final Context mContext;
    private final ActivityManager mAm;
    private final UserManager mUserManager;
    private final DevicePolicyManager mDpm;
    private final int mMaxRunningUsers;
    private final InitialUserSetter mInitialUserSetter;
    private final UserPreCreator mUserPreCreator;

    private final Object mLockUser = new Object();
    @GuardedBy("mLockUser")
    private boolean mUser0Unlocked;
    @GuardedBy("mLockUser")
    private final ArrayList<Runnable> mUser0UnlockTasks = new ArrayList<>();
    /**
     * Background users that will be restarted in garage mode. This list can include the
     * current foreground user but the current foreground user should not be restarted.
     */
    @GuardedBy("mLockUser")
    private final ArrayList<Integer> mBackgroundUsersToRestart = new ArrayList<>();
    /**
     * Keep the list of background users started here. This is wholly for debugging purpose.
     */
    @GuardedBy("mLockUser")
    private final ArrayList<Integer> mBackgroundUsersRestartedHere = new ArrayList<>();

    private final UserHalService mHal;

    private final HandlerThread mHandlerThread = CarServiceUtils.getHandlerThread(
            HANDLER_THREAD_NAME);
    private final Handler mHandler;

    /**
     * Internal listeners to be notified on new user activities events.
     *
     * <p>This collection should be accessed and manipulated by {@code mHandlerThread} only.
     */
    private final List<InternalLifecycleListener> mUserLifecycleListeners = new ArrayList<>();

    /**
     * App listeners to be notified on new user activities events.
     *
     * <p>This collection should be accessed and manipulated by {@code mHandlerThread} only.
     */
    private final ArrayMap<IBinder, AppLifecycleListener> mAppLifecycleListeners =
            new ArrayMap<>();

    /**
     * User Id for the user switch in process, if any.
     */
    @GuardedBy("mLockUser")
    private int mUserIdForUserSwitchInProcess = USER_NULL;
    /**
     * Request Id for the user switch in process, if any.
     */
    @GuardedBy("mLockUser")
    private int mRequestIdForUserSwitchInProcess;
    private final int mHalTimeoutMs = CarSystemProperties.getUserHalTimeout().orElse(5_000);

    // TODO(b/163566866): Use mSwitchGuestUserBeforeSleep for new create guest request
    private final boolean mSwitchGuestUserBeforeSleep;

    @Nullable
    @GuardedBy("mLockUser")
    private UserHandle mInitialUser;

    private ICarResultReceiver mUserSwitchUiReceiver;

    private final CarUxRestrictionsManagerService mCarUxRestrictionService;

    private final CarPackageManagerService mCarPackageManagerService;

    private static final int PRE_CREATION_STAGE_BEFORE_SUSPEND = 1;

    private static final int PRE_CREATION_STAGE_ON_SYSTEM_START = 2;

    private static final int DEFAULT_PRE_CREATION_DELAY_MS = 0;

    @IntDef(flag = true, prefix = { "PRE_CREATION_STAGE_" }, value = {
            PRE_CREATION_STAGE_BEFORE_SUSPEND,
            PRE_CREATION_STAGE_ON_SYSTEM_START,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface PreCreationStage { }

    @PreCreationStage
    private final int mPreCreationStage;

    private final int mPreCreationDelayMs;

    /**
     * Whether some operations - like user switch - are restricted by driving safety constraints.
     */
    @GuardedBy("mLockUser")
    private boolean mUxRestricted;

    /**
     * If {@code false}, garage mode operations (background users start at garage mode entry and
     * background users stop at garage mode exit) will be skipped. Controlled using car shell
     * command {@code adb shell set-start-bg-users-on-garage-mode [true|false]}
     * Purpose: Garage mode testing and simulation
     */
    @GuardedBy("mLockUser")
    private boolean mStartBackgroundUsersOnGarageMode = true;

    /**
     * Callback to notify {@code CarServiceHelper} about driving safety changes (through
     * {@link ICarServiceHelper#setSafetyMode(boolean).
     *
     * <p>NOTE: in theory, that logic should belong to {@code CarDevicePolicyService}, but it's
     * simpler to do it here (and that service already depends on this one).
     */
    @GuardedBy("mLockUser")
    private ICarServiceHelper mICarServiceHelper;

    private final ICarUxRestrictionsChangeListener mCarUxRestrictionsChangeListener =
            new ICarUxRestrictionsChangeListener.Stub() {
        @Override
        public void onUxRestrictionsChanged(CarUxRestrictions restrictions) {
            setUxRestrictions(restrictions);
        }
    };

    /** Map used to avoid calling UserHAL when a user was removed because HAL creation failed. */
    @GuardedBy("mLockUser")
    private final SparseBooleanArray mFailedToCreateUserIds = new SparseBooleanArray(1);

    private final UserHandleHelper mUserHandleHelper;

    public CarUserService(@NonNull Context context, @NonNull UserHalService hal,
            @NonNull UserManager userManager,
            int maxRunningUsers,
            @NonNull CarUxRestrictionsManagerService uxRestrictionService,
            @NonNull CarPackageManagerService carPackageManagerService) {
        this(context, hal, userManager, new UserHandleHelper(context, userManager),
                context.getSystemService(DevicePolicyManager.class),
                context.getSystemService(ActivityManager.class), maxRunningUsers,
                /* initialUserSetter= */ null, /* userPreCreator= */ null, uxRestrictionService,
                null, carPackageManagerService);
    }

    @VisibleForTesting
    CarUserService(@NonNull Context context, @NonNull UserHalService hal,
            @NonNull UserManager userManager,
            @NonNull UserHandleHelper userHandleHelper,
            @NonNull DevicePolicyManager dpm,
            @NonNull ActivityManager am,
            int maxRunningUsers,
            @Nullable InitialUserSetter initialUserSetter,
            @Nullable UserPreCreator userPreCreator,
            @NonNull CarUxRestrictionsManagerService uxRestrictionService,
            @Nullable Handler handler,
            @NonNull CarPackageManagerService carPackageManagerService) {
        Slogf.d(TAG, "CarUserService(): DBG=%b, user=%s", DBG, context.getUser());
        mContext = context;
        mHal = hal;
        mAm = am;
        mMaxRunningUsers = maxRunningUsers;
        mUserManager = userManager;
        mDpm = dpm;
        mUserHandleHelper = userHandleHelper;
        mHandler = handler == null ? new Handler(mHandlerThread.getLooper()) : handler;
        mInitialUserSetter =
                initialUserSetter == null ? new InitialUserSetter(context, this,
                        (u) -> setInitialUser(u), mUserHandleHelper) : initialUserSetter;
        mUserPreCreator =
                userPreCreator == null ? new UserPreCreator(context, mUserManager) : userPreCreator;
        Resources resources = context.getResources();
        mSwitchGuestUserBeforeSleep = resources.getBoolean(
                R.bool.config_switchGuestUserBeforeGoingSleep);
        mCarUxRestrictionService = uxRestrictionService;
        mCarPackageManagerService = carPackageManagerService;
        mPreCreationStage = resources.getInteger(R.integer.config_userPreCreationStage);
        int preCreationDelayMs = resources
                .getInteger(R.integer.config_userPreCreationDelay);
        mPreCreationDelayMs = preCreationDelayMs < DEFAULT_PRE_CREATION_DELAY_MS
                ? DEFAULT_PRE_CREATION_DELAY_MS
                : preCreationDelayMs;
    }

    @Override
    public void init() {
        if (DBG) {
            Slogf.d(TAG, "init()");
        }

        mCarUxRestrictionService.registerUxRestrictionsChangeListener(
                mCarUxRestrictionsChangeListener, Display.DEFAULT_DISPLAY);

        setUxRestrictions(mCarUxRestrictionService.getCurrentUxRestrictions());
    }

    @Override
    public void release() {
        if (DBG) {
            Slogf.d(TAG, "release()");
        }

        mCarUxRestrictionService
                .unregisterUxRestrictionsChangeListener(mCarUxRestrictionsChangeListener);
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(@NonNull IndentingPrintWriter writer) {
        checkHasDumpPermissionGranted(mContext, "dump()");

        writer.println("*CarUserService*");
        writer.printf("DBG=%b\n", DBG);
        handleDumpListeners(writer);
        writer.printf("User switch UI receiver %s\n", mUserSwitchUiReceiver);
        synchronized (mLockUser) {
            writer.println("User0Unlocked: " + mUser0Unlocked);
            writer.println("BackgroundUsersToRestart: " + mBackgroundUsersToRestart);
            writer.println("BackgroundUsersRestarted: " + mBackgroundUsersRestartedHere);
            if (mFailedToCreateUserIds.size() > 0) {
                writer.println("FailedToCreateUserIds: " + mFailedToCreateUserIds);
            }
            writer.printf("Is UX restricted: %b\n", mUxRestricted);
            writer.printf("Start Background Users On Garage Mode=%s\n",
                    mStartBackgroundUsersOnGarageMode);
            writer.printf("Initial user: %s\n", mInitialUser);
        }

        writer.println("SwitchGuestUserBeforeSleep: " + mSwitchGuestUserBeforeSleep);
        writer.printf("PreCreateUserStages: %s\n", preCreationStageToString(mPreCreationStage));
        writer.printf("PreCreationDelayMs: %s\n", mPreCreationDelayMs);

        writer.println("MaxRunningUsers: " + mMaxRunningUsers);
        writer.printf("User HAL: supported=%b, timeout=%dms\n", isUserHalSupported(),
                mHalTimeoutMs);

        writer.println("Relevant overlayable properties");
        Resources res = mContext.getResources();
        writer.increaseIndent();
        writer.printf("owner_name=%s\n", UserManagerHelper.getDefaultUserName(mContext));
        writer.printf("default_guest_name=%s\n", res.getString(R.string.default_guest_name));
        writer.printf("config_multiuserMaxRunningUsers=%d\n",
                UserManagerHelper.getMaxRunningUsers(mContext));
        writer.decreaseIndent();
        writer.printf("User switch in process=%d\n", mUserIdForUserSwitchInProcess);
        writer.printf("Request Id for the user switch in process=%d\n ",
                    mRequestIdForUserSwitchInProcess);
        writer.printf("System UI package name=%s\n",
                PackageManagerHelper.getSystemUiPackageName(mContext));

        writer.println("Relevant Global settings");
        writer.increaseIndent();
        dumpGlobalProperty(writer, CarSettings.Global.LAST_ACTIVE_USER_ID);
        dumpGlobalProperty(writer, CarSettings.Global.LAST_ACTIVE_PERSISTENT_USER_ID);
        writer.decreaseIndent();

        mInitialUserSetter.dump(writer);
    }

    private static String preCreationStageToString(@PreCreationStage int stage) {
        return DebugUtils.flagsToString(CarUserService.class, "PRE_CREATION_STAGE_", stage);
    }

    private void dumpGlobalProperty(IndentingPrintWriter writer, String property) {
        String value = Settings.Global.getString(mContext.getContentResolver(), property);
        writer.printf("%s=%s\n", property, value);
    }

    private void handleDumpListeners(IndentingPrintWriter writer) {
        writer.increaseIndent();
        CountDownLatch latch = new CountDownLatch(1);
        mHandler.post(() -> {
            handleDumpServiceLifecycleListeners(writer);
            handleDumpAppLifecycleListeners(writer);
            latch.countDown();
        });
        int timeout = 5;
        try {
            if (!latch.await(timeout, TimeUnit.SECONDS)) {
                writer.printf("Handler thread didn't respond in %ds when dumping listeners\n",
                        timeout);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            writer.println("Interrupted waiting for handler thread to dump app and user listeners");
        }
        writer.decreaseIndent();
    }

    private void handleDumpServiceLifecycleListeners(PrintWriter writer) {
        if (mUserLifecycleListeners.isEmpty()) {
            writer.println("No lifecycle listeners for internal services");
            return;
        }
        int size = mUserLifecycleListeners.size();
        writer.printf("%d lifecycle listener%s for services\n", size, size == 1 ? "" : "s");
        String indent = "  ";
        for (int i = 0; i < size; i++) {
            InternalLifecycleListener listener = mUserLifecycleListeners.get(i);
            writer.printf("%slistener=%s, filter=%s\n", indent,
                    FunctionalUtils.getLambdaName(listener.listener), listener.filter);
        }
    }

    private void handleDumpAppLifecycleListeners(IndentingPrintWriter writer) {
        int size = mAppLifecycleListeners.size();
        if (size == 0) {
            writer.println("No lifecycle listeners for apps");
            return;
        }
        writer.printf("%d lifecycle listener%s for apps\n", size, size == 1 ? "" : "s");
        writer.increaseIndent();
        for (int i = 0; i < size; i++) {
            mAppLifecycleListeners.valueAt(i).dump(writer);
        }
        writer.decreaseIndent();
    }

    @Override
    public void setLifecycleListenerForApp(String packageName, UserLifecycleEventFilter filter,
            ICarResultReceiver receiver) {
        int uid = Binder.getCallingUid();
        EventLogHelper.writeCarUserServiceSetLifecycleListener(uid, packageName);
        checkInteractAcrossUsersPermission("setLifecycleListenerForApp-" + uid + "-" + packageName);

        IBinder receiverBinder = receiver.asBinder();
        mHandler.post(() -> {
            AppLifecycleListener listener = mAppLifecycleListeners.get(receiverBinder);
            if (listener == null) {
                listener = new AppLifecycleListener(uid, packageName, receiver, filter,
                        (l) -> onListenerDeath(l));
                Slogf.d(TAG, "Adding %s (using binder %s) with filter %s",
                        listener, receiverBinder, filter);
                mAppLifecycleListeners.put(receiverBinder, listener);
            } else {
                // Same listener already exists. Only add the additional filter.
                Slogf.d(TAG, "Adding filter %s to the listener %s (for binder %s)", filter,
                        listener, receiverBinder);
                listener.addFilter(filter);
            }
        });
    }

    private void onListenerDeath(AppLifecycleListener listener) {
        Slogf.i(TAG, "Removing listener %s on binder death", listener);
        mHandler.post(() -> mAppLifecycleListeners.remove(listener.receiver.asBinder()));
    }

    @Override
    public void resetLifecycleListenerForApp(ICarResultReceiver receiver) {
        int uid = Binder.getCallingUid();
        checkInteractAcrossUsersPermission("resetLifecycleListenerForApp-" + uid);
        IBinder receiverBinder = receiver.asBinder();
        mHandler.post(() -> {
            AppLifecycleListener listener = mAppLifecycleListeners.get(receiverBinder);
            if (listener == null) {
                Slogf.e(TAG, "resetLifecycleListenerForApp(uid=%d): no listener for receiver", uid);
                return;
            }
            if (listener.uid != uid) {
                Slogf.e(TAG, "resetLifecycleListenerForApp(): uid mismatch (called by %d) for "
                        + "listener %s", uid, listener);
            }
            EventLogHelper.writeCarUserServiceResetLifecycleListener(uid,
                    listener.packageName);
            if (DBG) {
                Slogf.d(TAG, "Removing %s (using binder %s)", listener, receiverBinder);
            }
            mAppLifecycleListeners.remove(receiverBinder);

            listener.onDestroy();
        });
    }

    /**
     * Gets the initial foreground user after the device boots or resumes from suspension.
     *
     * <p>When the OEM supports the User HAL, the initial user won't be available until the HAL
     * returns the initial value to {@code CarService} - if HAL takes too long or times out, this
     * method returns {@code null}.
     *
     * <p>If the HAL eventually times out, {@code CarService} will fallback to its default behavior
     * (like switching to the last active user), and this method will return the result of such
     * operation.
     *
     * <p>Notice that if {@code CarService} crashes, subsequent calls to this method will return
     * {@code null}.
     *
     * @hide
     */
    @Nullable
    public UserHandle getInitialUser() {
        checkInteractAcrossUsersPermission("getInitialUser");
        synchronized (mLockUser) {
            return mInitialUser;
        }
    }

    /**
     * Sets the initial foreground user after the device boots or resumes from suspension.
     */
    public void setInitialUser(@Nullable UserHandle user) {
        EventLogHelper
                .writeCarUserServiceSetInitialUser(user == null ? USER_NULL : user.getIdentifier());
        synchronized (mLockUser) {
            mInitialUser = user;
        }
        if (user == null) {
            // This mean InitialUserSetter failed and could not fallback, so the initial user was
            // not switched (and most likely is SYSTEM_USER).
            // TODO(b/153104378): should we set it to ActivityManager.getCurrentUser() instead?
            Slogf.wtf(TAG, "Initial user set to null");
            return;
        }
        sendInitialUserToSystemServer(user);
    }

    /**
     * Sets the initial foreground user after car service is crashed and reconnected.
     */
    public void setInitialUserFromSystemServer(@Nullable UserHandle user) {
        if (user == null || user.getIdentifier() == USER_NULL) {
            Slogf.e(TAG,
                    "setInitialUserFromSystemServer: Not setting initial user as user is NULL ");
            return;
        }

        if (DBG) {
            Slogf.d(TAG, "setInitialUserFromSystemServer: initial User: %s", user);
        }

        synchronized (mLockUser) {
            mInitialUser = user;
        }
    }

    private void sendInitialUserToSystemServer(UserHandle user) {
        ICarServiceHelper iCarServiceHelper;
        synchronized (mLockUser) {
            iCarServiceHelper = mICarServiceHelper;
        }

        if (iCarServiceHelper == null) {
            Slogf.e(TAG, "sendInitialUserToSystemServer(%s): CarServiceHelper is NULL.", user);
            return;
        }

        try {
            iCarServiceHelper.sendInitialUser(user);
        } catch (RemoteException e) {
            Slogf.e(TAG, e, "Error calling sendInitialUser(%s)", user);
        }
    }

    private void initResumeReplaceGuest() {
        int currentUserId = ActivityManager.getCurrentUser();
        UserHandle currentUser = mUserHandleHelper.getExistingUserHandle(currentUserId);

        if (currentUser == null) {
            Slogf.wtf(TAG, "Current user (%d) doesn't exist", currentUserId);
        }

        if (!mInitialUserSetter.canReplaceGuestUser(currentUser)) return; // Not a guest

        InitialUserInfo info =
                new InitialUserSetter.Builder(InitialUserSetter.TYPE_REPLACE_GUEST).build();

        mInitialUserSetter.set(info);
    }

    /**
     * Calls to switch user at the power suspend.
     *
     * <p><b>Note:</b> Should be used only by {@link CarPowerManagementService}
     *
     */
    public void onSuspend() {
        if (DBG) {
            Slogf.d(TAG, "onSuspend called.");
        }

        if (mSwitchGuestUserBeforeSleep) {
            initResumeReplaceGuest();
        }

        if ((mPreCreationStage & PRE_CREATION_STAGE_BEFORE_SUSPEND) != 0) {
            preCreateUsersInternal(/* waitTimeMs= */ DEFAULT_PRE_CREATION_DELAY_MS);
        }
    }

    /**
     * Calls to switch user at the power resume.
     *
     * <p>
     * <b>Note:</b> Should be used only by {@link CarPowerManagementService}
     *
     */
    public void onResume() {
        if (DBG) {
            Slogf.d(TAG, "onResume called.");
        }

        mHandler.post(() -> initBootUser(InitialUserInfoRequestType.RESUME));
    }

    /**
     * Calls to start user at the android startup.
     */
    public void initBootUser() {
        mHandler.post(() -> initBootUser(getInitialUserInfoRequestType()));

        if ((mPreCreationStage & PRE_CREATION_STAGE_ON_SYSTEM_START) != 0) {
            preCreateUsersInternal(mPreCreationDelayMs);
        }
    }

    private void initBootUser(int requestType) {
        boolean replaceGuest =
                requestType == InitialUserInfoRequestType.RESUME && !mSwitchGuestUserBeforeSleep;
        checkManageUsersPermission("startInitialUser");

        if (!isUserHalSupported()) {
            fallbackToDefaultInitialUserBehavior(/* userLocales= */ null, replaceGuest,
                    /* supportsOverrideUserIdProperty= */ true);
            EventLogHelper.writeCarUserServiceInitialUserInfoReqComplete(requestType);
            return;
        }

        UsersInfo usersInfo = UserHalHelper.newUsersInfo(mUserManager, mUserHandleHelper);
        EventLogHelper.writeCarUserServiceInitialUserInfoReq(requestType,
                mHalTimeoutMs, usersInfo.currentUser.userId, usersInfo.currentUser.flags,
                usersInfo.numberUsers);

        mHal.getInitialUserInfo(requestType, mHalTimeoutMs, usersInfo, (status, resp) -> {
            if (resp != null) {
                EventLogHelper.writeCarUserServiceInitialUserInfoResp(
                        status, resp.action, resp.userToSwitchOrCreate.userId,
                        resp.userToSwitchOrCreate.flags, resp.userNameToCreate, resp.userLocales);

                String userLocales = resp.userLocales;
                InitialUserInfo info;
                switch(resp.action) {
                    case InitialUserInfoResponseAction.SWITCH:
                        int userId = resp.userToSwitchOrCreate.userId;
                        if (userId <= 0) {
                            Slogf.w(TAG, "invalid (or missing) user id sent by HAL: %d", userId);
                            fallbackToDefaultInitialUserBehavior(userLocales, replaceGuest,
                                    /* supportsOverrideUserIdProperty= */ false);
                            break;
                        }
                        info = new InitialUserSetter.Builder(InitialUserSetter.TYPE_SWITCH)
                                .setUserLocales(userLocales)
                                .setSwitchUserId(userId)
                                .setReplaceGuest(replaceGuest)
                                .build();
                        mInitialUserSetter.set(info);
                        break;

                    case InitialUserInfoResponseAction.CREATE:
                        int halFlags = resp.userToSwitchOrCreate.flags;
                        String userName =  resp.userNameToCreate;
                        info = new InitialUserSetter.Builder(InitialUserSetter.TYPE_CREATE)
                                .setUserLocales(userLocales)
                                .setNewUserName(userName)
                                .setNewUserFlags(halFlags)
                                .build();
                        mInitialUserSetter.set(info);
                        break;

                    case InitialUserInfoResponseAction.DEFAULT:
                        fallbackToDefaultInitialUserBehavior(userLocales, replaceGuest,
                                /* supportsOverrideUserIdProperty= */ false);
                        break;
                    default:
                        Slogf.w(TAG, "invalid response action on %s", resp);
                        fallbackToDefaultInitialUserBehavior(/* userLocales= */ null, replaceGuest,
                                /* supportsOverrideUserIdProperty= */ false);
                        break;

                }
            } else {
                EventLogHelper.writeCarUserServiceInitialUserInfoResp(status, /* action= */ 0,
                        /* userId= */ 0, /* flags= */ 0,
                        /* safeName= */ "", /* userLocales= */ "");
                fallbackToDefaultInitialUserBehavior(/* user locale */ null, replaceGuest,
                        /* supportsOverrideUserIdProperty= */ false);
            }
            EventLogHelper.writeCarUserServiceInitialUserInfoReqComplete(requestType);
        });
    }

    private void fallbackToDefaultInitialUserBehavior(String userLocales, boolean replaceGuest,
            boolean supportsOverrideUserIdProperty) {
        InitialUserInfo info = new InitialUserSetter.Builder(
                InitialUserSetter.TYPE_DEFAULT_BEHAVIOR)
                .setUserLocales(userLocales)
                .setReplaceGuest(replaceGuest)
                .setSupportsOverrideUserIdProperty(supportsOverrideUserIdProperty)
                .build();
        mInitialUserSetter.set(info);
    }

    @VisibleForTesting
    int getInitialUserInfoRequestType() {
        if (!mInitialUserSetter.hasInitialUser()) {
            return InitialUserInfoRequestType.FIRST_BOOT;
        }
        if (mContext.getPackageManager().isDeviceUpgrading()) {
            return InitialUserInfoRequestType.FIRST_BOOT_AFTER_OTA;
        }
        return InitialUserInfoRequestType.COLD_BOOT;
    }

    /**
     * Sets the {@link ICarServiceHelper} so it can receive UX restriction updates.
     */
    public void setCarServiceHelper(ICarServiceHelper helper) {
        boolean restricted;
        synchronized (mLockUser) {
            mICarServiceHelper = helper;
            restricted = mUxRestricted;
        }
        updateSafetyMode(helper, restricted);
    }

    private void updateSafetyMode(@Nullable ICarServiceHelper helper, boolean restricted) {
        if (helper == null) return;

        boolean isSafe = !restricted;
        try {
            helper.setSafetyMode(isSafe);
        } catch (Exception e) {
            Slogf.e(TAG, e, "Exception calling helper.setDpmSafetyMode(%b)", isSafe);
        }
    }

    private void setUxRestrictions(@Nullable CarUxRestrictions restrictions) {
        boolean restricted = restrictions != null
                && (restrictions.getActiveRestrictions() & UX_RESTRICTIONS_NO_SETUP)
                        == UX_RESTRICTIONS_NO_SETUP;
        if (DBG) {
            Slogf.d(TAG, "setUxRestrictions(%s): restricted=%b", restrictions, restricted);
        } else {
            Slogf.i(TAG, "Setting UX restricted to %b", restricted);
        }

        ICarServiceHelper helper = null;

        synchronized (mLockUser) {
            mUxRestricted = restricted;
            if (mICarServiceHelper == null) {
                Slogf.e(TAG, "onUxRestrictionsChanged(): no mICarServiceHelper");
            }
            helper = mICarServiceHelper;
        }
        updateSafetyMode(helper, restricted);
    }

    private boolean isUxRestricted() {
        synchronized (mLockUser) {
            return mUxRestricted;
        }
    }

    /**
     * Calls the {@link UserHalService} and {@link ActivityManager} for user switch.
     *
     * <p>
     * When everything works well, the workflow is:
     * <ol>
     *   <li> {@link UserHalService} is called for HAL user switch with ANDROID_SWITCH request
     *   type, current user id, target user id, and a callback.
     *   <li> HAL called back with SUCCESS.
     *   <li> {@link ActivityManager} is called for Android user switch.
     *   <li> Receiver would receive {@code STATUS_SUCCESSFUL}.
     *   <li> Once user is unlocked, {@link UserHalService} is again called with ANDROID_POST_SWITCH
     *   request type, current user id, and target user id. In this case, the current and target
     *   user IDs would be same.
     * <ol/>
     *
     * <p>
     * Corner cases:
     * <ul>
     *   <li> If target user is already the current user, no user switch is performed and receiver
     *   would receive {@code STATUS_OK_USER_ALREADY_IN_FOREGROUND} right away.
     *   <li> If HAL user switch call fails, no Android user switch. Receiver would receive
     *   {@code STATUS_HAL_INTERNAL_FAILURE}.
     *   <li> If HAL user switch call is successful, but android user switch call fails,
     *   {@link UserHalService} is again called with request type POST_SWITCH, current user id, and
     *   target user id, but in this case the current and target user IDs would be different.
     *   <li> If another user switch request for the same target user is received while previous
     *   request is in process, receiver would receive
     *   {@code STATUS_TARGET_USER_ALREADY_BEING_SWITCHED_TO} for the new request right away.
     *   <li> If a user switch request is received while another user switch request for different
     *   target user is in process, the previous request would be abandoned and new request will be
     *   processed. No POST_SWITCH would be sent for the previous request.
     * <ul/>
     *
     * @param targetUserId - target user Id
     * @param timeoutMs - timeout for HAL to wait
     * @param receiver - receiver for the results
     */
    @Override
    public void switchUser(@UserIdInt int targetUserId, int timeoutMs,
            @NonNull AndroidFuture<UserSwitchResult> receiver) {
        EventLogHelper.writeCarUserServiceSwitchUserReq(targetUserId, timeoutMs);
        checkManageOrCreateUsersPermission("switchUser");
        Objects.requireNonNull(receiver);
        UserHandle targetUser = mUserHandleHelper.getExistingUserHandle(targetUserId);
        Preconditions.checkArgument(targetUser != null, "Target user doesn't exist");
        if (mUserManager.getUserSwitchability() != UserManager.SWITCHABILITY_STATUS_OK) {
            sendUserSwitchResult(receiver, /* isLogout= */ false,
                    UserSwitchResult.STATUS_NOT_SWITCHABLE);
            return;
        }
        mHandler.post(() -> handleSwitchUser(targetUser, timeoutMs, receiver,
                /* isLogout= */ false));
    }

    @Override
    public void logoutUser(int timeoutMs, @NonNull AndroidFuture<UserSwitchResult> receiver) {
        checkManageOrCreateUsersPermission("logoutUser");
        Objects.requireNonNull(receiver);

        UserHandle targetUser = mDpm.getLogoutUser();
        int logoutUserId = targetUser == null ? UserManagerHelper.USER_NULL
                : targetUser.getIdentifier();
        EventLogHelper.writeCarUserServiceLogoutUserReq(logoutUserId, timeoutMs);

        if (targetUser == null) {
            Slogf.w(TAG, "logoutUser() called when current user is not logged in");
            sendUserSwitchResult(receiver, /* isLogout= */ true,
                    UserSwitchResult.STATUS_NOT_LOGGED_IN);
            return;
        }

        mHandler.post(() -> handleSwitchUser(targetUser, timeoutMs, receiver,
                /* isLogout= */ true));
    }

    private void handleSwitchUser(@NonNull UserHandle targetUser, int timeoutMs,
            @NonNull AndroidFuture<UserSwitchResult> receiver, boolean isLogout) {
        int currentUser = ActivityManager.getCurrentUser();
        int targetUserId = targetUser.getIdentifier();
        if (currentUser == targetUserId) {
            if (DBG) {
                Slogf.d(TAG, "Current user is same as requested target user: %d", targetUserId);
            }
            int resultStatus = UserSwitchResult.STATUS_OK_USER_ALREADY_IN_FOREGROUND;
            sendUserSwitchResult(receiver, isLogout, resultStatus);
            return;
        }

        if (isUxRestricted()) {
            sendUserSwitchResult(receiver, isLogout,
                    UserSwitchResult.STATUS_UX_RESTRICTION_FAILURE);
            return;
        }

        // If User Hal is not supported, just android user switch.
        if (!isUserHalSupported()) {
            int result = switchOrLogoutUser(targetUser, isLogout);
            if (result == UserManager.USER_OPERATION_SUCCESS) {
                sendUserSwitchResult(receiver, isLogout, UserSwitchResult.STATUS_SUCCESSFUL);
                return;
            }
            sendUserSwitchResult(receiver, isLogout, HalCallback.STATUS_INVALID,
                    UserSwitchResult.STATUS_ANDROID_FAILURE, result, /* errorMessage= */ null);
            return;
        }

        synchronized (mLockUser) {
            if (DBG) {
                Slogf.d(TAG, "handleSwitchUser(%d): currentuser=%s, isLogout=%b, "
                        + "mUserIdForUserSwitchInProcess=%b", targetUserId, currentUser, isLogout,
                        mUserIdForUserSwitchInProcess);
            }

            // If there is another request for the same target user, return another request in
            // process, else {@link mUserIdForUserSwitchInProcess} is updated and {@link
            // mRequestIdForUserSwitchInProcess} is reset. It is possible that there may be another
            // user switch request in process for different target user, but that request is now
            // ignored.
            if (mUserIdForUserSwitchInProcess == targetUserId) {
                Slogf.w(TAG, "switchUser(%s): another user switch request (id=%d) in process for "
                        + "that user", targetUser, mRequestIdForUserSwitchInProcess);
                int resultStatus = UserSwitchResult.STATUS_TARGET_USER_ALREADY_BEING_SWITCHED_TO;
                sendUserSwitchResult(receiver, isLogout, resultStatus);
                return;
            } else {
                if (DBG) {
                    Slogf.d(TAG, "Changing mUserIdForUserSwitchInProcess from %d to %d",
                            mUserIdForUserSwitchInProcess, targetUserId);
                }
                mUserIdForUserSwitchInProcess = targetUserId;
                mRequestIdForUserSwitchInProcess = 0;
            }
        }

        UsersInfo usersInfo = UserHalHelper.newUsersInfo(mUserManager, mUserHandleHelper);
        SwitchUserRequest request = createUserSwitchRequest(targetUserId, usersInfo);

        if (DBG) {
            Slogf.d(TAG, "calling mHal.switchUser(%s)", request);
        }
        mHal.switchUser(request, timeoutMs, (halCallbackStatus, resp) -> {
            if (DBG) {
                Slogf.d(TAG, "switch response: status=%s, resp=%s",
                        Integer.toString(halCallbackStatus), resp);
            }

            int resultStatus = UserSwitchResult.STATUS_HAL_INTERNAL_FAILURE;
            Integer androidFailureStatus = null;

            synchronized (mLockUser) {
                if (halCallbackStatus != HalCallback.STATUS_OK || resp == null) {
                    Slogf.w(TAG, "invalid callback status (%s) or null response (%s)",
                            Integer.toString(halCallbackStatus), resp);
                    sendUserSwitchResult(receiver, isLogout, resultStatus);
                    mUserIdForUserSwitchInProcess = USER_NULL;
                    return;
                }

                if (mUserIdForUserSwitchInProcess != targetUserId) {
                    // Another user switch request received while HAL responded. No need to
                    // process this request further
                    Slogf.w(TAG, "Another user switch received while HAL responsed. Request"
                            + " abandoned for user %d. Current user in process: %d", targetUserId,
                            mUserIdForUserSwitchInProcess);
                    resultStatus =
                            UserSwitchResult.STATUS_TARGET_USER_ABANDONED_DUE_TO_A_NEW_REQUEST;
                    sendUserSwitchResult(receiver, isLogout, resultStatus);
                    mUserIdForUserSwitchInProcess = USER_NULL;
                    return;
                }

                switch (resp.status) {
                    case SwitchUserStatus.SUCCESS:
                        int result = switchOrLogoutUser(targetUser, isLogout);
                        if (result == UserManager.USER_OPERATION_SUCCESS) {
                            sendUserSwitchUiCallback(targetUserId);
                            resultStatus = UserSwitchResult.STATUS_SUCCESSFUL;
                            mRequestIdForUserSwitchInProcess = resp.requestId;
                        } else {
                            resultStatus = UserSwitchResult.STATUS_ANDROID_FAILURE;
                            if (isLogout) {
                                // Send internal result (there's no point on sending for regular
                                // switch as it will always be UNKNOWN_ERROR
                                androidFailureStatus = result;
                            }
                            postSwitchHalResponse(resp.requestId, targetUserId);
                        }
                        break;
                    case SwitchUserStatus.FAILURE:
                        // HAL failed to switch user
                        resultStatus = UserSwitchResult.STATUS_HAL_FAILURE;
                        break;
                    default:
                        // Shouldn't happen because UserHalService validates the status
                        Slogf.wtf(TAG, "Received invalid user switch status from HAL: %s", resp);
                }

                if (mRequestIdForUserSwitchInProcess == 0) {
                    mUserIdForUserSwitchInProcess = USER_NULL;
                }
            }
            sendUserSwitchResult(receiver, isLogout, halCallbackStatus, resultStatus,
                    androidFailureStatus, resp.errorMessage);
        });
    }

    private int switchOrLogoutUser(UserHandle targetUser, boolean isLogout) {
        if (isLogout) {
            int result = mDpm.logoutUser();
            if (result != UserManager.USER_OPERATION_SUCCESS) {
                Slogf.w(TAG, "failed to logout to user %s using DPM: result=%s", targetUser,
                        userOperationErrorToString(result));
            }
            return result;
        }

        if (!mAm.switchUser(targetUser)) {
            Slogf.w(TAG, "failed to switch to user %s using AM", targetUser);
            return UserManager.USER_OPERATION_ERROR_UNKNOWN;
        }

        return UserManager.USER_OPERATION_SUCCESS;
    }

    @Override
    public void removeUser(@UserIdInt int userId, AndroidFuture<UserRemovalResult> receiver) {
        removeUser(userId, /* hasCallerRestrictions= */ false, receiver);
    }

    /**
     * Internal implementation of {@code removeUser()}, which is used by both
     * {@code ICarUserService} and {@code ICarDevicePolicyService}.
     *
     * @param userId user to be removed
     * @param hasCallerRestrictions when {@code true}, if the caller user is not an admin, it can
     * only remove itself.
     * @param receiver to post results
     */
    public void removeUser(@UserIdInt int userId, boolean hasCallerRestrictions,
            AndroidFuture<UserRemovalResult> receiver) {
        checkManageOrCreateUsersPermission("removeUser");
        EventLogHelper.writeCarUserServiceRemoveUserReq(userId,
                hasCallerRestrictions ? 1 : 0);

        if (hasCallerRestrictions) {
            // Restrictions: non-admin user can only remove itself, admins have no restrictions
            int callingUserId = Binder.getCallingUserHandle().getIdentifier();
            if (!mUserHandleHelper.isAdminUser(UserHandle.of(callingUserId))
                    && userId != callingUserId) {
                throw new SecurityException("Non-admin user " + callingUserId
                        + " can only remove itself");
            }
        }
        mHandler.post(() -> handleRemoveUser(userId, hasCallerRestrictions, receiver));
    }

    private void handleRemoveUser(@UserIdInt int userId, boolean hasCallerRestrictions,
            AndroidFuture<UserRemovalResult> receiver) {
        UserHandle user = mUserHandleHelper.getExistingUserHandle(userId);
        if (user == null) {
            sendUserRemovalResult(userId, UserRemovalResult.STATUS_USER_DOES_NOT_EXIST, receiver);
            return;
        }
        UserInfo halUser = new UserInfo();
        halUser.userId = user.getIdentifier();
        halUser.flags = UserHalHelper.convertFlags(mUserHandleHelper, user);
        UsersInfo usersInfo = UserHalHelper.newUsersInfo(mUserManager, mUserHandleHelper);

        // check if the user is last admin user.
        boolean isLastAdmin = false;
        if (UserHalHelper.isAdmin(halUser.flags)) {
            int size = usersInfo.existingUsers.length;
            int totalAdminUsers = 0;
            for (int i = 0; i < size; i++) {
                if (UserHalHelper.isAdmin(usersInfo.existingUsers[i].flags)) {
                    totalAdminUsers++;
                }
            }
            if (totalAdminUsers == 1) {
                isLastAdmin = true;
            }
        }

        // First remove user from android and then remove from HAL because HAL remove user is one
        // way call.
        // TODO(b/170887769): rename hasCallerRestrictions to fromCarDevicePolicyManager (or use an
        // int / enum to indicate if it's called from CarUserManager or CarDevicePolicyManager), as
        // it's counter-intuitive that it's "allowed even when disallowed" when it
        // "has caller restrictions"
        boolean overrideDevicePolicy = hasCallerRestrictions;
        int result = mUserManager.removeUserWhenPossible(user, overrideDevicePolicy);
        if (!UserManager.isRemoveResultSuccessful(result)) {
            sendUserRemovalResult(userId, UserRemovalResult.STATUS_ANDROID_FAILURE, receiver);
            return;
        }

        if (isLastAdmin) {
            Slogf.w(TAG, "Last admin user successfully removed or set ephemeral. User Id: %d",
                    userId);
        }

        switch (result) {
            case UserManager.REMOVE_RESULT_REMOVED:
            case UserManager.REMOVE_RESULT_ALREADY_BEING_REMOVED:
                sendUserRemovalResult(userId,
                        isLastAdmin ? UserRemovalResult.STATUS_SUCCESSFUL_LAST_ADMIN_REMOVED
                                : UserRemovalResult.STATUS_SUCCESSFUL, receiver);
                break;
            case UserManager.REMOVE_RESULT_DEFERRED:
                sendUserRemovalResult(userId,
                        isLastAdmin ? UserRemovalResult.STATUS_SUCCESSFUL_LAST_ADMIN_SET_EPHEMERAL
                                : UserRemovalResult.STATUS_SUCCESSFUL_SET_EPHEMERAL, receiver);
                break;
            default:
                sendUserRemovalResult(userId, UserRemovalResult.STATUS_ANDROID_FAILURE, receiver);
        }
    }

    /**
     * Should be called by {@code ICarImpl} only.
     */
    public void onUserRemoved(@NonNull UserHandle user) {
        if (DBG) {
            Slogf.d(TAG, "onUserRemoved: %s", user);
        }
        notifyHalUserRemoved(user);
    }

    private void notifyHalUserRemoved(@NonNull UserHandle user) {
        if (!isUserHalSupported()) return;

        if (user == null) {
            Slogf.wtf(TAG, "notifyHalUserRemoved() called for null user");
            return;
        }

        int userId = user.getIdentifier();

        if (userId == USER_NULL) {
            Slogf.wtf(TAG, "notifyHalUserRemoved() called for USER_NULL");
            return;
        }

        synchronized (mLockUser) {
            if (mFailedToCreateUserIds.get(userId)) {
                if (DBG) {
                    Slogf.d(TAG, "notifyHalUserRemoved(): skipping user %d", userId);
                }
                mFailedToCreateUserIds.delete(userId);
                return;
            }
        }

        UserInfo halUser = new UserInfo();
        halUser.userId = userId;
        halUser.flags = UserHalHelper.convertFlags(mUserHandleHelper, user);

        RemoveUserRequest request = UserHalHelper.emptyRemoveUserRequest();
        request.removedUserInfo = halUser;
        request.usersInfo = UserHalHelper.newUsersInfo(mUserManager, mUserHandleHelper);
        mHal.removeUser(request);
    }

    private void sendUserRemovalResult(@UserIdInt int userId, @UserRemovalResult.Status int result,
            AndroidFuture<UserRemovalResult> receiver) {
        EventLogHelper.writeCarUserServiceRemoveUserResp(userId, result);
        receiver.complete(new UserRemovalResult(result));
    }

    private void sendUserSwitchUiCallback(@UserIdInt int targetUserId) {
        if (mUserSwitchUiReceiver == null) {
            Slogf.w(TAG, "No User switch UI receiver.");
            return;
        }

        EventLogHelper.writeCarUserServiceSwitchUserUiReq(targetUserId);
        try {
            mUserSwitchUiReceiver.send(targetUserId, null);
        } catch (RemoteException e) {
            Slogf.e(TAG, "Error calling user switch UI receiver.", e);
        }
    }

    /**
     * Used to create the initial user, even when it's disallowed by {@code DevicePolicyManager}.
     */
    @Nullable
    UserHandle createUserEvenWhenDisallowed(@Nullable String name, @NonNull String userType,
            int flags) {
        synchronized (mLockUser) {
            if (mICarServiceHelper == null) {
                Slogf.wtf(TAG, "createUserEvenWhenDisallowed(): mICarServiceHelper not set yet",
                        new Exception());
                return null;
            }
        }

        try {
            ICarServiceHelper iCarServiceHelper;
            synchronized (mLockUser) {
                iCarServiceHelper = mICarServiceHelper;
            }
            UserHandle user = iCarServiceHelper.createUserEvenWhenDisallowed(name,
                    userType, flags);
            return user;
        } catch (RemoteException e) {
            Slogf.e(TAG, e, "createUserEvenWhenDisallowed(%s, %s, %d) failed",
                    UserHelperLite.safeName(name), userType, flags);
            return null;
        }
    }

    @Override
    public void createUser(@Nullable String name, @NonNull String userType, int flags,
            int timeoutMs, @NonNull AndroidFuture<UserCreationResult> receiver) {
        createUser(name, userType, flags, timeoutMs, receiver, /* hasCallerRestrictions= */ false);
    }

    /**
     * Internal implementation of {@code createUser()}, which is used by both
     * {@code ICarUserService} and {@code ICarDevicePolicyService}.
     *
     * @param hasCallerRestrictions when {@code true}, if the caller user is not an admin, it can
     * only create admin users
     */
    public void createUser(@Nullable String name, @NonNull String userType, int flags,
            int timeoutMs, @NonNull AndroidFuture<UserCreationResult> receiver,
            boolean hasCallerRestrictions) {
        Objects.requireNonNull(userType, "user type cannot be null");
        Objects.requireNonNull(receiver, "receiver cannot be null");
        checkManageOrCreateUsersPermission(flags);
        EventLogHelper.writeCarUserServiceCreateUserReq(UserHelperLite.safeName(name), userType,
                flags, timeoutMs, hasCallerRestrictions ? 1 : 0);

        UserHandle callingUser = Binder.getCallingUserHandle();
        if (mUserManager.hasUserRestrictionForUser(UserManager.DISALLOW_ADD_USER, callingUser)) {
            String internalErrorMessage = String.format(ERROR_TEMPLATE_DISALLOW_ADD_USER,
                    callingUser, UserManager.DISALLOW_ADD_USER);
            Slogf.w(TAG, internalErrorMessage);
            sendUserCreationFailure(receiver, UserCreationResult.STATUS_ANDROID_FAILURE,
                    internalErrorMessage);
            return;
        }

        mHandler.post(() -> handleCreateUser(name, userType, flags, timeoutMs, receiver,
                callingUser, hasCallerRestrictions));
    }

    private void handleCreateUser(@Nullable String name, @NonNull String userType,
            int flags, int timeoutMs, @NonNull AndroidFuture<UserCreationResult> receiver,
            @NonNull UserHandle callingUser, boolean hasCallerRestrictions) {
        if (userType.equals(UserManager.USER_TYPE_FULL_GUEST) && flags != 0) {
            // Non-zero flags are not allowed when creating a guest user.
            String internalErroMessage = String
                    .format(ERROR_TEMPLATE_INVALID_FLAGS_FOR_GUEST_CREATION, flags, name);
            Slogf.e(TAG, internalErroMessage);
            sendUserCreationFailure(receiver, UserCreationResult.STATUS_INVALID_REQUEST,
                    internalErroMessage);
            return;
        }
        if (hasCallerRestrictions) {
            // Restrictions:
            // - type/flag can only be normal user, admin, or guest
            // - non-admin user can only create non-admin users

            boolean validCombination;
            switch (userType) {
                case UserManager.USER_TYPE_FULL_SECONDARY:
                    validCombination = flags == 0
                        || (flags & UserManagerHelper.FLAG_ADMIN) == UserManagerHelper.FLAG_ADMIN;
                    break;
                case UserManager.USER_TYPE_FULL_GUEST:
                    validCombination = true;
                    break;
                default:
                    validCombination = false;
            }
            if (!validCombination) {
                String internalErrorMessage = String.format(
                        ERROR_TEMPLATE_INVALID_USER_TYPE_AND_FLAGS_COMBINATION, userType, flags);

                Slogf.d(TAG, internalErrorMessage);
                sendUserCreationFailure(receiver, UserCreationResult.STATUS_INVALID_REQUEST,
                        internalErrorMessage);
                return;
            }

            if (!mUserHandleHelper.isAdminUser(callingUser)
                    && (flags & UserManagerHelper.FLAG_ADMIN) == UserManagerHelper.FLAG_ADMIN) {
                String internalErrorMessage = String
                        .format(ERROR_TEMPLATE_NON_ADMIN_CANNOT_CREATE_ADMIN_USERS,
                                callingUser.getIdentifier());
                Slogf.d(TAG, internalErrorMessage);
                sendUserCreationFailure(receiver, UserCreationResult.STATUS_INVALID_REQUEST,
                        internalErrorMessage);
                return;
            }
        }

        NewUserRequest newUserRequest;
        try {
            newUserRequest = getCreateUserRequest(name, userType, flags);
        } catch (Exception e) {
            Slogf.e(TAG, e, "Error creating new user request. name: %s UserType: %s and flags: %s",
                    name, userType, flags);
            sendUserCreationResult(receiver, UserCreationResult.STATUS_ANDROID_FAILURE,
                    UserManager.USER_OPERATION_ERROR_UNKNOWN, /* user= */ null,
                    /* errorMessage= */ null, e.toString());
            return;
        }

        UserHandle newUser;
        try {
            NewUserResponse newUserResponse = mUserManager.createUser(newUserRequest);

            if (!newUserResponse.isSuccessful()) {
                if (DBG) {
                    Slogf.d(TAG, "um.createUser() returned null for user of type %s and flags %d",
                            userType, flags);
                }
                sendUserCreationResult(receiver, UserCreationResult.STATUS_ANDROID_FAILURE,
                        newUserResponse.getOperationResult(), /* user= */ null,
                        /* errorMessage= */ null, /* internalErrorMessage= */ null);
                return;
            }

            newUser = newUserResponse.getUser();

            if (DBG) {
                Slogf.d(TAG, "Created user: %s", newUser);
            }
            EventLogHelper.writeCarUserServiceCreateUserUserCreated(newUser.getIdentifier(), name,
                    userType, flags);
        } catch (RuntimeException e) {
            Slogf.e(TAG, e, "Error creating user of type %s and flags %d", userType, flags);
            sendUserCreationResult(receiver, UserCreationResult.STATUS_ANDROID_FAILURE,
                    UserManager.USER_OPERATION_ERROR_UNKNOWN, /* user= */ null,
                    /* errorMessage= */ null, e.toString());
            return;
        }

        if (!isUserHalSupported()) {
            sendUserCreationResult(receiver, UserCreationResult.STATUS_SUCCESSFUL,
                    /* androidFailureStatus= */ null , newUser, /* errorMessage= */ null,
                    /* internalErrorMessage= */ null);
            return;
        }

        CreateUserRequest request = UserHalHelper.emptyCreateUserRequest();
        request.usersInfo = UserHalHelper.newUsersInfo(mUserManager, mUserHandleHelper);
        if (!TextUtils.isEmpty(name)) {
            request.newUserName = name;
        }
        request.newUserInfo.userId = newUser.getIdentifier();
        request.newUserInfo.flags = UserHalHelper.convertFlags(mUserHandleHelper, newUser);
        if (DBG) {
            Slogf.d(TAG, "Create user request: %s", request);
        }

        try {
            mHal.createUser(request, timeoutMs, (status, resp) -> {
                String errorMessage = resp != null ? resp.errorMessage : null;
                int resultStatus = UserCreationResult.STATUS_HAL_INTERNAL_FAILURE;
                if (DBG) {
                    Slogf.d(TAG, "createUserResponse: status=%s, resp=%s",
                            UserHalHelper.halCallbackStatusToString(status), resp);
                }
                UserHandle user = null; // user returned in the result
                if (status != HalCallback.STATUS_OK || resp == null) {
                    Slogf.w(TAG, "invalid callback status (%s) or null response (%s)",
                            UserHalHelper.halCallbackStatusToString(status), resp);
                    EventLogHelper.writeCarUserServiceCreateUserResp(status, resultStatus,
                            errorMessage);
                    removeCreatedUser(newUser, "HAL call failed with "
                            + UserHalHelper.halCallbackStatusToString(status));
                    sendUserCreationResult(receiver, resultStatus, /* androidFailureStatus= */ null,
                            user, errorMessage,  /* internalErrorMessage= */ null);
                    return;
                }

                switch (resp.status) {
                    case CreateUserStatus.SUCCESS:
                        resultStatus = UserCreationResult.STATUS_SUCCESSFUL;
                        user = newUser;
                        break;
                    case CreateUserStatus.FAILURE:
                        // HAL failed to switch user
                        resultStatus = UserCreationResult.STATUS_HAL_FAILURE;
                        break;
                    default:
                        // Shouldn't happen because UserHalService validates the status
                        Slogf.wtf(TAG, "Received invalid user switch status from HAL: %s", resp);
                }
                EventLogHelper.writeCarUserServiceCreateUserResp(status, resultStatus,
                        errorMessage);
                if (user == null) {
                    removeCreatedUser(newUser, "HAL returned "
                            + UserCreationResult.statusToString(resultStatus));
                }
                sendUserCreationResult(receiver, resultStatus, /* androidFailureStatus= */ null,
                        user, errorMessage, /* internalErrorMessage= */ null);
            });
        } catch (Exception e) {
            Slogf.w(TAG, e, "mHal.createUser(%s) failed", request);
            removeCreatedUser(newUser, "mHal.createUser() failed");
            sendUserCreationFailure(receiver, UserCreationResult.STATUS_HAL_INTERNAL_FAILURE,
                    e.toString());
        }
    }

    private NewUserRequest getCreateUserRequest(String name, String userType, int flags) {
        NewUserRequest.Builder builder = new NewUserRequest.Builder().setName(name)
                .setUserType(userType);
        if ((flags & UserManagerHelper.FLAG_ADMIN) == UserManagerHelper.FLAG_ADMIN) {
            builder.setAdmin();
        }

        if ((flags & UserManagerHelper.FLAG_EPHEMERAL) == UserManagerHelper.FLAG_EPHEMERAL) {
            builder.setEphemeral();
        }

        return builder.build();
    }

    private void removeCreatedUser(@NonNull UserHandle user, @NonNull String reason) {
        Slogf.i(TAG, "removing user %s reason: %s", user, reason);

        int userId = user.getIdentifier();
        EventLogHelper.writeCarUserServiceCreateUserUserRemoved(userId, reason);

        synchronized (mLockUser) {
            mFailedToCreateUserIds.put(userId, true);
        }

        try {
            if (!mUserManager.removeUser(user)) {
                Slogf.w(TAG, "Failed to remove user %s", user);
            }
        } catch (Exception e) {
            Slogf.e(TAG, e, "Failed to remove user %s", user);
        }
    }

    @Override
    public UserIdentificationAssociationResponse getUserIdentificationAssociation(
            @UserIdentificationAssociationType int[] types) {
        if (!isUserHalUserAssociationSupported()) {
            return UserIdentificationAssociationResponse.forFailure(VEHICLE_HAL_NOT_SUPPORTED);
        }

        Preconditions.checkArgument(!ArrayUtils.isEmpty(types), "must have at least one type");
        checkManageOrCreateUsersPermission("getUserIdentificationAssociation");

        int uid = getCallingUid();
        int userId = getCallingUserHandle().getIdentifier();
        EventLogHelper.writeCarUserServiceGetUserAuthReq(uid, userId, types.length);

        UserIdentificationGetRequest request = UserHalHelper.emptyUserIdentificationGetRequest();
        request.userInfo.userId = userId;
        request.userInfo.flags = getHalUserInfoFlags(userId);

        request.numberAssociationTypes = types.length;
        ArrayList<Integer> associationTypes = new ArrayList<>(types.length);
        for (int i = 0; i < types.length; i++) {
            associationTypes.add(types[i]);
        }
        request.associationTypes = toIntArray(associationTypes);

        UserIdentificationResponse halResponse = mHal.getUserAssociation(request);
        if (halResponse == null) {
            Slogf.w(TAG, "getUserIdentificationAssociation(): HAL returned null for %s",
                    Arrays.toString(types));
            return UserIdentificationAssociationResponse.forFailure();
        }

        int[] values = new int[halResponse.associations.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = halResponse.associations[i].value;
        }
        EventLogHelper.writeCarUserServiceGetUserAuthResp(values.length);

        return UserIdentificationAssociationResponse.forSuccess(values, halResponse.errorMessage);
    }

    @Override
    public void setUserIdentificationAssociation(int timeoutMs,
            @UserIdentificationAssociationType int[] types,
            @UserIdentificationAssociationSetValue int[] values,
            AndroidFuture<UserIdentificationAssociationResponse> result) {
        if (!isUserHalUserAssociationSupported()) {
            result.complete(
                    UserIdentificationAssociationResponse.forFailure(VEHICLE_HAL_NOT_SUPPORTED));
            return;
        }

        Preconditions.checkArgument(!ArrayUtils.isEmpty(types), "must have at least one type");
        Preconditions.checkArgument(!ArrayUtils.isEmpty(values), "must have at least one value");
        if (types.length != values.length) {
            throw new IllegalArgumentException("types (" + Arrays.toString(types) + ") and values ("
                    + Arrays.toString(values) + ") should have the same length");
        }
        checkManageOrCreateUsersPermission("setUserIdentificationAssociation");

        int uid = getCallingUid();
        int userId = getCallingUserHandle().getIdentifier();
        EventLogHelper.writeCarUserServiceSetUserAuthReq(uid, userId, types.length);

        UserIdentificationSetRequest request = UserHalHelper.emptyUserIdentificationSetRequest();
        request.userInfo.userId = userId;
        request.userInfo.flags = getHalUserInfoFlags(userId);

        request.numberAssociations = types.length;
        ArrayList<UserIdentificationSetAssociation> associations = new ArrayList<>();
        for (int i = 0; i < types.length; i++) {
            UserIdentificationSetAssociation association = new UserIdentificationSetAssociation();
            association.type = types[i];
            association.value = values[i];
            associations.add(association);
        }
        request.associations =
                associations.toArray(new UserIdentificationSetAssociation[associations.size()]);

        mHal.setUserAssociation(timeoutMs, request, (status, resp) -> {
            if (status != HalCallback.STATUS_OK || resp == null) {
                Slogf.w(TAG, "setUserIdentificationAssociation(): invalid callback status (%s) for "
                        + "response %s", UserHalHelper.halCallbackStatusToString(status), resp);
                if (resp == null || TextUtils.isEmpty(resp.errorMessage)) {
                    EventLogHelper.writeCarUserServiceSetUserAuthResp(0, /* errorMessage= */ "");
                    result.complete(UserIdentificationAssociationResponse.forFailure());
                    return;
                }
                EventLogHelper.writeCarUserServiceSetUserAuthResp(0, resp.errorMessage);
                result.complete(
                        UserIdentificationAssociationResponse.forFailure(resp.errorMessage));
                return;
            }
            int respSize = resp.associations.length;
            EventLogHelper.writeCarUserServiceSetUserAuthResp(respSize, resp.errorMessage);

            int[] responseTypes = new int[respSize];
            for (int i = 0; i < respSize; i++) {
                responseTypes[i] = resp.associations[i].value;
            }
            UserIdentificationAssociationResponse response = UserIdentificationAssociationResponse
                    .forSuccess(responseTypes, resp.errorMessage);
            if (DBG) {
                Slogf.d(TAG, "setUserIdentificationAssociation(): resp=%s, converted=%s", resp,
                        response);
            }
            result.complete(response);
        });
    }

    /**
     * Gets the User HAL flags for the given user.
     *
     * @throws IllegalArgumentException if the user does not exist.
     */
    private int getHalUserInfoFlags(@UserIdInt int userId) {
        UserHandle user = mUserHandleHelper.getExistingUserHandle(userId);
        Preconditions.checkArgument(user != null, "no user for id %d", userId);
        return UserHalHelper.convertFlags(mUserHandleHelper, user);
    }

    static void sendUserSwitchResult(@NonNull AndroidFuture<UserSwitchResult> receiver,
            boolean isLogout, @UserSwitchResult.Status int userSwitchStatus) {
        sendUserSwitchResult(receiver, isLogout, HalCallback.STATUS_INVALID, userSwitchStatus,
                /* androidFailureStatus= */ null, /* errorMessage= */ null);
    }

    static void sendUserSwitchResult(@NonNull AndroidFuture<UserSwitchResult> receiver,
            boolean isLogout, @HalCallback.HalCallbackStatus int halCallbackStatus,
            @UserSwitchResult.Status int userSwitchStatus, @Nullable Integer androidFailureStatus,
            @Nullable String errorMessage) {
        if (isLogout) {
            EventLogHelper.writeCarUserServiceLogoutUserResp(halCallbackStatus, userSwitchStatus,
                    errorMessage);
        } else {
            EventLogHelper.writeCarUserServiceSwitchUserResp(halCallbackStatus, userSwitchStatus,
                    errorMessage);
        }
        receiver.complete(
                new UserSwitchResult(userSwitchStatus, androidFailureStatus, errorMessage));
    }

    static void sendUserCreationFailure(AndroidFuture<UserCreationResult> receiver,
            @UserCreationResult.Status int status, String internalErrorMessage) {
        sendUserCreationResult(receiver, status, /* androidStatus= */ null, /* user= */ null,
                /* errorMessage= */ null, internalErrorMessage);
    }

    private static void sendUserCreationResult(AndroidFuture<UserCreationResult> receiver,
            @UserCreationResult.Status int status, @Nullable Integer androidFailureStatus,
            @NonNull UserHandle user, @Nullable String errorMessage,
            @Nullable String internalErrorMessage) {
        if (TextUtils.isEmpty(errorMessage)) {
            errorMessage = null;
        }
        if (TextUtils.isEmpty(internalErrorMessage)) {
            internalErrorMessage = null;
        }

        receiver.complete(new UserCreationResult(status, androidFailureStatus, user, errorMessage,
                internalErrorMessage));
    }

    /**
     * Calls activity manager for user switch.
     *
     * <p><b>NOTE</b> This method is meant to be called just by UserHalService.
     *
     * @param requestId for the user switch request
     * @param targetUserId of the target user
     *
     * @hide
     */
    public void switchAndroidUserFromHal(int requestId, @UserIdInt int targetUserId) {
        EventLogHelper.writeCarUserServiceSwitchUserFromHalReq(requestId, targetUserId);
        Slogf.i(TAG, "User hal requested a user switch. Target user id is %d", targetUserId);

        boolean result = mAm.switchUser(UserHandle.of(targetUserId));
        if (result) {
            updateUserSwitchInProcess(requestId, targetUserId);
        } else {
            postSwitchHalResponse(requestId, targetUserId);
        }
    }

    private void updateUserSwitchInProcess(int requestId, @UserIdInt int targetUserId) {
        synchronized (mLockUser) {
            if (mUserIdForUserSwitchInProcess != USER_NULL) {
                // Some other user switch is in process.
                Slogf.w(TAG, "User switch for user id %d is in process. Abandoning it as a new user"
                        + " switch is requested for the target user %d",
                        mUserIdForUserSwitchInProcess, targetUserId);
            }
            mUserIdForUserSwitchInProcess = targetUserId;
            mRequestIdForUserSwitchInProcess = requestId;
        }
    }

    private void postSwitchHalResponse(int requestId, @UserIdInt int targetUserId) {
        if (!isUserHalSupported()) return;

        UsersInfo usersInfo = UserHalHelper.newUsersInfo(mUserManager, mUserHandleHelper);
        EventLogHelper.writeCarUserServicePostSwitchUserReq(targetUserId,
                usersInfo.currentUser.userId);
        SwitchUserRequest request = createUserSwitchRequest(targetUserId, usersInfo);
        request.requestId = requestId;
        mHal.postSwitchResponse(request);
    }

    private SwitchUserRequest createUserSwitchRequest(@UserIdInt int targetUserId,
            @NonNull UsersInfo usersInfo) {
        UserHandle targetUser = mUserHandleHelper.getExistingUserHandle(targetUserId);
        UserInfo halTargetUser = new UserInfo();
        halTargetUser.userId = targetUser.getIdentifier();
        halTargetUser.flags = UserHalHelper.convertFlags(mUserHandleHelper, targetUser);
        SwitchUserRequest request = UserHalHelper.emptySwitchUserRequest();
        request.targetUser = halTargetUser;
        request.usersInfo = usersInfo;
        return request;
    }

    /**
     * Checks if the User HAL is supported.
     */
    public boolean isUserHalSupported() {
        return mHal.isSupported();
    }

    /**
     * Checks if the User HAL user association is supported.
     */
    @Override
    public boolean isUserHalUserAssociationSupported() {
        return mHal.isUserAssociationSupported();
    }

    /**
     * Sets a callback which is invoked before user switch.
     *
     * <p>
     * This method should only be called by the Car System UI. The purpose of this call is to notify
     * Car System UI to show the user switch UI before the user switch.
     */
    @Override
    public void setUserSwitchUiCallback(@NonNull ICarResultReceiver receiver) {
        checkManageUsersPermission("setUserSwitchUiCallback");

        // Confirm that caller is system UI.
        String systemUiPackageName = PackageManagerHelper.getSystemUiPackageName(mContext);

        try {
            int systemUiUid = mContext
                    .createContextAsUser(UserHandle.SYSTEM, /* flags= */ 0).getPackageManager()
                    .getPackageUid(systemUiPackageName, PackageManager.MATCH_SYSTEM_ONLY);
            int callerUid = Binder.getCallingUid();
            if (systemUiUid != callerUid) {
                throw new SecurityException("Invalid caller. Only" + systemUiPackageName
                        + " is allowed to make this call");
            }
        } catch (NameNotFoundException e) {
            throw new IllegalStateException("Package " + systemUiPackageName + " not found.");
        }

        mUserSwitchUiReceiver = receiver;
    }

    private void updateDefaultUserRestriction() {
        // We want to set restrictions on system and guest users only once. These are persisted
        // onto disk, so it's sufficient to do it once + we minimize the number of disk writes.
        if (Settings.Global.getInt(mContext.getContentResolver(),
                CarSettings.Global.DEFAULT_USER_RESTRICTIONS_SET, /* default= */ 0) != 0) {
            return;
        }
        // Only apply the system user restrictions if the system user is headless.
        if (UserManager.isHeadlessSystemUserMode()) {
            setSystemUserRestrictions();
        }
        Settings.Global.putInt(mContext.getContentResolver(),
                CarSettings.Global.DEFAULT_USER_RESTRICTIONS_SET, 1);
    }

    private boolean isPersistentUser(@UserIdInt int userId) {
        return !mUserHandleHelper.isEphemeralUser(UserHandle.of(userId));
    }

    /**
     * Adds a new {@link UserLifecycleListener} with {@code filter} to selectively listen to user
     * activity events.
     */
    public void addUserLifecycleListener(@Nullable UserLifecycleEventFilter filter,
            @NonNull UserLifecycleListener listener) {
        Objects.requireNonNull(listener, "listener cannot be null");
        mHandler.post(() -> mUserLifecycleListeners.add(
                new InternalLifecycleListener(listener, filter)));
    }

    /**
     * Removes previously added {@link UserLifecycleListener}.
     */
    public void removeUserLifecycleListener(@NonNull UserLifecycleListener listener) {
        Objects.requireNonNull(listener, "listener cannot be null");
        mHandler.post(() -> {
            for (int i = 0; i < mUserLifecycleListeners.size(); i++) {
                if (listener.equals(mUserLifecycleListeners.get(i).listener)) {
                    mUserLifecycleListeners.remove(i);
                }
            }
        });
    }

    private void onUserUnlocked(@UserIdInt int userId) {
        ArrayList<Runnable> tasks = null;
        synchronized (mLockUser) {
            sendPostSwitchToHalLocked(userId);
            if (userId == UserHandle.SYSTEM.getIdentifier()) {
                if (!mUser0Unlocked) { // user 0, unlocked, do this only once
                    updateDefaultUserRestriction();
                    tasks = new ArrayList<>(mUser0UnlockTasks);
                    mUser0UnlockTasks.clear();
                    mUser0Unlocked = true;
                }
            } else { // none user0
                Integer user = userId;
                if (isPersistentUser(userId)) {
                    // current foreground user should stay in top priority.
                    if (userId == ActivityManager.getCurrentUser()) {
                        mBackgroundUsersToRestart.remove(user);
                        mBackgroundUsersToRestart.add(0, user);
                    }
                    // -1 for user 0
                    if (mBackgroundUsersToRestart.size() > (mMaxRunningUsers - 1)) {
                        int userToDrop = mBackgroundUsersToRestart.get(
                                mBackgroundUsersToRestart.size() - 1);
                        Slogf.i(TAG, "New user (%d) unlocked, dropping least recently user from "
                                + "restart list (%s)", userId, userToDrop);
                        // Drop the least recently used user.
                        mBackgroundUsersToRestart.remove(mBackgroundUsersToRestart.size() - 1);
                    }
                }
            }
        }
        if (tasks != null) {
            int tasksSize = tasks.size();
            if (tasksSize > 0) {
                Slogf.d(TAG, "User0 unlocked, run queued tasks size: %d", tasksSize);
                for (int i = 0; i < tasksSize; i++) {
                    tasks.get(i).run();
                }
            }
        }
    }

    /**
     * Starts the specified user in the background.
     *
     * @param userId user to start in background
     * @param receiver to post results
     */
    public void startUserInBackground(@UserIdInt int userId,
            @NonNull AndroidFuture<UserStartResult> receiver) {
        checkManageOrCreateUsersPermission("startUserInBackground");
        EventLogHelper.writeCarUserServiceStartUserInBackgroundReq(userId);

        mHandler.post(() -> handleStartUserInBackground(userId, receiver));
    }

    private void handleStartUserInBackground(@UserIdInt int userId,
            @NonNull AndroidFuture<UserStartResult> receiver) {
        // If the requested user is the current user, do nothing and return success.
        if (ActivityManager.getCurrentUser() == userId) {
            sendUserStartResult(
                    userId, UserStartResult.STATUS_SUCCESSFUL_USER_IS_CURRENT_USER, receiver);
            return;
        }
        // If requested user does not exist, return error.
        if (mUserHandleHelper.getExistingUserHandle(userId) == null) {
            Slogf.w(TAG, "User %d does not exist", userId);
            sendUserStartResult(userId, UserStartResult.STATUS_USER_DOES_NOT_EXIST, receiver);
            return;
        }

        if (!ActivityManagerHelper.startUserInBackground(userId)) {
            Slogf.w(TAG, "Failed to start user %d in background", userId);
            sendUserStartResult(userId, UserStartResult.STATUS_ANDROID_FAILURE, receiver);
            return;
        }

        // TODO(b/181331178): We are not updating mBackgroundUsersToRestart or
        // mBackgroundUsersRestartedHere, which were only used for the garage mode. Consider
        // renaming them to make it more clear.
        sendUserStartResult(userId, UserStartResult.STATUS_SUCCESSFUL, receiver);
    }

    private void sendUserStartResult(@UserIdInt int userId, @UserStartResult.Status int result,
            @NonNull AndroidFuture<UserStartResult> receiver) {
        EventLogHelper.writeCarUserServiceStartUserInBackgroundResp(userId, result);
        receiver.complete(new UserStartResult(result));
    }

    /**
     * Starts all background users that were active in system.
     *
     * @return list of background users started successfully.
     */
    @NonNull
    public ArrayList<Integer> startAllBackgroundUsersInGarageMode() {
        synchronized (mLockUser) {
            if (!mStartBackgroundUsersOnGarageMode) {
                Slogf.i(TAG, "Background users are not started as mStartBackgroundUsersOnGarageMode"
                        + " is false.");
                return new ArrayList<>();
            }
        }

        ArrayList<Integer> users;
        synchronized (mLockUser) {
            users = new ArrayList<>(mBackgroundUsersToRestart);
            mBackgroundUsersRestartedHere.clear();
            mBackgroundUsersRestartedHere.addAll(mBackgroundUsersToRestart);
        }
        ArrayList<Integer> startedUsers = new ArrayList<>();
        for (Integer user : users) {
            if (user == ActivityManager.getCurrentUser()) {
                continue;
            }
            if (ActivityManagerHelper.startUserInBackground(user)) {
                if (mUserManager.isUserUnlockingOrUnlocked(UserHandle.of(user))) {
                    // already unlocked / unlocking. No need to unlock.
                    startedUsers.add(user);
                } else if (ActivityManagerHelper.unlockUser(user)) {
                    startedUsers.add(user);
                } else { // started but cannot unlock
                    Slogf.w(TAG, "Background user started but cannot be unlocked: %s", user);
                    if (mUserManager.isUserRunning(UserHandle.of(user))) {
                        // add to started list so that it can be stopped later.
                        startedUsers.add(user);
                    }
                }
            }
        }
        // Keep only users that were re-started in mBackgroundUsersRestartedHere
        synchronized (mLockUser) {
            ArrayList<Integer> usersToRemove = new ArrayList<>();
            for (Integer user : mBackgroundUsersToRestart) {
                if (!startedUsers.contains(user)) {
                    usersToRemove.add(user);
                }
            }
            mBackgroundUsersRestartedHere.removeAll(usersToRemove);
        }
        return startedUsers;
    }

    /**
     * Stops the specified background user.
     *
     * @param userId user to stop
     * @param receiver to post results
     */
    public void stopUser(@UserIdInt int userId, @NonNull AndroidFuture<UserStopResult> receiver) {
        checkManageOrCreateUsersPermission("stopUser");
        EventLogHelper.writeCarUserServiceStopUserReq(userId);

        mHandler.post(() -> handleStopUser(userId, receiver));
    }

    private void handleStopUser(
            @UserIdInt int userId, @NonNull AndroidFuture<UserStopResult> receiver) {
        @UserStopResult.Status int userStopStatus = stopBackgroundUserInternal(userId);
        sendUserStopResult(userId, userStopStatus, receiver);
    }

    private void sendUserStopResult(@UserIdInt int userId, @UserStopResult.Status int result,
            @NonNull AndroidFuture<UserStopResult> receiver) {
        EventLogHelper.writeCarUserServiceStopUserResp(userId, result);
        receiver.complete(new UserStopResult(result));
    }

    private @UserStopResult.Status int stopBackgroundUserInternal(@UserIdInt int userId) {
        int r = ActivityManagerHelper.stopUserWithDelayedLocking(userId, true);
        switch(r) {
            case USER_OP_SUCCESS:
                return UserStopResult.STATUS_SUCCESSFUL;
            case USER_OP_ERROR_IS_SYSTEM:
                Slogf.w(TAG, "Cannot stop the system user: %d", userId);
                return UserStopResult.STATUS_FAILURE_SYSTEM_USER;
            case USER_OP_IS_CURRENT:
                Slogf.w(TAG, "Cannot stop the current user: %d", userId);
                return UserStopResult.STATUS_FAILURE_CURRENT_USER;
            case USER_OP_UNKNOWN_USER:
                Slogf.w(TAG, "Cannot stop the user that does not exist: %d", userId);
                return UserStopResult.STATUS_USER_DOES_NOT_EXIST;
            default:
                Slogf.w(TAG, "stopUser failed, user: %d, err: %d", userId, r);
        }
        return UserStopResult.STATUS_ANDROID_FAILURE;
    }

    /**
     * Sets boolean to control background user operations during garage mode.
     */
    public void setStartBackgroundUsersOnGarageMode(boolean enable) {
        synchronized (mLockUser) {
            mStartBackgroundUsersOnGarageMode = enable;
        }
    }

    /**
     * Stops a background user.
     *
     * @return whether stopping succeeds.
     */
    public boolean stopBackgroundUserInGagageMode(@UserIdInt int userId) {
        synchronized (mLockUser) {
            if (!mStartBackgroundUsersOnGarageMode) {
                Slogf.i(TAG, "Background users are not stopped as mStartBackgroundUsersOnGarageMode"
                        + " is false.");
                return false;
            }
        }

        @UserStopResult.Status int userStopStatus = stopBackgroundUserInternal(userId);
        if (UserStopResult.isSuccess(userStopStatus)) {
            // Remove the stopped user from the mBackgroundUserRestartedHere list.
            synchronized (mLockUser) {
                mBackgroundUsersRestartedHere.remove(Integer.valueOf(userId));
            }
            return true;
        }
        return false;
    }

    /**
     * Notifies all registered {@link UserLifecycleListener} with the event passed as argument.
     */
    public void onUserLifecycleEvent(@UserLifecycleEventType int eventType,
            @UserIdInt int fromUserId, @UserIdInt int toUserId) {
        if (DBG) {
            Slogf.d(TAG, "onUserLifecycleEvent(): event=%d, from=%d, to=%d", eventType, fromUserId,
                    toUserId);
        }
        int userId = toUserId;

        // Handle special cases first...
        switch (eventType) {
            case CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING:
                onUserSwitching(fromUserId, toUserId);
                break;
            case CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED:
                onUserUnlocked(userId);
                break;
            case CarUserManager.USER_LIFECYCLE_EVENT_TYPE_REMOVED:
                onUserRemoved(UserHandle.of(userId));
                break;
            default:
        }

        // ...then notify listeners.
        UserLifecycleEvent event = new UserLifecycleEvent(eventType, fromUserId, userId);

        mHandler.post(() -> {
            handleNotifyServiceUserLifecycleListeners(event);
            // POST_UNLOCKED event is meant only for internal service listeners. Skip sending it to
            // app listeners.
            if (eventType != CarUserManager.USER_LIFECYCLE_EVENT_TYPE_POST_UNLOCKED) {
                handleNotifyAppUserLifecycleListeners(event);
            }
        });
    }

    private void sendPostSwitchToHalLocked(@UserIdInt int userId) {
        int userIdForUserSwitchInProcess;
        int requestIdForUserSwitchInProcess;
        synchronized (mLockUser) {
            if (mUserIdForUserSwitchInProcess == USER_NULL
                    || mUserIdForUserSwitchInProcess != userId
                    || mRequestIdForUserSwitchInProcess == 0) {
                Slogf.d(TAG, "No user switch request Id. No android post switch sent.");
                return;
            }
            userIdForUserSwitchInProcess = mUserIdForUserSwitchInProcess;
            requestIdForUserSwitchInProcess = mRequestIdForUserSwitchInProcess;

            mUserIdForUserSwitchInProcess = USER_NULL;
            mRequestIdForUserSwitchInProcess = 0;
        }
        postSwitchHalResponse(requestIdForUserSwitchInProcess, userIdForUserSwitchInProcess);
    }

    private void handleNotifyAppUserLifecycleListeners(UserLifecycleEvent event) {
        int listenersSize = mAppLifecycleListeners.size();
        if (listenersSize == 0) {
            Slogf.d(TAG, "No app listener to be notified of %s", event);
            return;
        }
        // Must use a different TimingsTraceLog because it's another thread
        if (DBG) {
            Slogf.d(TAG, "Notifying %d app listeners of %s", listenersSize, event);
        }
        int userId = event.getUserId();
        TimingsTraceLog t = new TimingsTraceLog(TAG, TraceHelper.TRACE_TAG_CAR_SERVICE);
        int eventType = event.getEventType();
        t.traceBegin("notify-app-listeners-user-" + userId + "-event-" + eventType);
        for (int i = 0; i < listenersSize; i++) {
            AppLifecycleListener listener = mAppLifecycleListeners.valueAt(i);
            if (eventType == CarUserManager.USER_LIFECYCLE_EVENT_TYPE_CREATED
                    || eventType == CarUserManager.USER_LIFECYCLE_EVENT_TYPE_REMOVED) {
                PlatformVersion platformVersion = Car.getPlatformVersion();
                // Perform platform version check to ensure the support for these new events
                // is consistent with the platform version declared in their ApiRequirements.
                if (!platformVersion.isAtLeast(PlatformVersion.VERSION_CODES.TIRAMISU_1)) {
                    if (DBG) {
                        Slogf.d(TAG, "Skipping app listener %s for event %s due to unsupported"
                                + " car platform version %s.", listener, event, platformVersion);
                    }
                    continue;
                }
                // Perform target car version check to ensure only apps expecting the new
                // lifecycle event types will have the events sent to them.
                // TODO(b/235524989): Cache the target car version for packages in
                // CarPackageManagerService.
                CarVersion targetCarVersion = mCarPackageManagerService.getTargetCarVersion(
                        listener.packageName);
                if (!targetCarVersion.isAtLeast(CarVersion.VERSION_CODES.TIRAMISU_1)) {
                    if (DBG) {
                        Slogf.d(TAG, "Skipping app listener %s for event %s due to incompatible"
                                + " target car version %s.", listener, event, targetCarVersion);
                    }
                    continue;
                }
            }
            if (!listener.applyFilters(event)) {
                if (DBG) {
                    Slogf.d(TAG, "Skipping app listener %s for event %s due to the filters"
                            + " evaluated to false.", listener, event);
                }
                continue;
            }
            Bundle data = new Bundle();
            data.putInt(CarUserManager.BUNDLE_PARAM_ACTION, eventType);

            int fromUserId = event.getPreviousUserId();
            if (fromUserId != USER_NULL) {
                data.putInt(CarUserManager.BUNDLE_PARAM_PREVIOUS_USER_ID, fromUserId);
            }
            Slogf.d(TAG, "Notifying app listener %s", listener);
            EventLogHelper.writeCarUserServiceNotifyAppLifecycleListener(listener.uid,
                    listener.packageName, eventType, fromUserId, userId);
            try {
                t.traceBegin("notify-app-listener-" + listener.toShortString());
                listener.receiver.send(userId, data);
            } catch (RemoteException e) {
                Slogf.e(TAG, e, "Error calling lifecycle listener %s", listener);
            } finally {
                t.traceEnd();
            }
        }
        t.traceEnd(); // notify-app-listeners-user-USERID-event-EVENT_TYPE
    }

    private void handleNotifyServiceUserLifecycleListeners(UserLifecycleEvent event) {
        TimingsTraceLog t = new TimingsTraceLog(TAG, TraceHelper.TRACE_TAG_CAR_SERVICE);
        if (mUserLifecycleListeners.isEmpty()) {
            Slogf.w(TAG, "No internal UserLifecycleListeners registered to notify event %s",
                    event);
            return;
        }
        int userId = event.getUserId();
        int eventType = event.getEventType();
        t.traceBegin("notify-listeners-user-" + userId + "-event-" + eventType);
        for (InternalLifecycleListener listener : mUserLifecycleListeners) {
            String listenerName = FunctionalUtils.getLambdaName(listener);
            UserLifecycleEventFilter filter = listener.filter;
            if (filter != null && !filter.apply(event)) {
                if (DBG) {
                    Slogf.d(TAG, "Skipping service listener %s for event %s due to the filter %s"
                            + " evaluated to false", listenerName, event, filter);
                }
                continue;
            }
            if (DBG) {
                Slogf.d(TAG, "Notifying %d service listeners of %s", mUserLifecycleListeners.size(),
                        event);
            }
            EventLogHelper.writeCarUserServiceNotifyInternalLifecycleListener(listenerName,
                    eventType, event.getPreviousUserId(), userId);
            try {
                t.traceBegin("notify-listener-" + listenerName);
                listener.listener.onEvent(event);
            } catch (RuntimeException e) {
                Slogf.e(TAG, e , "Exception raised when invoking onEvent for %s", listenerName);
            } finally {
                t.traceEnd();
            }
        }
        t.traceEnd(); // notify-listeners-user-USERID-event-EVENT_TYPE
    }

    private void onUserSwitching(@UserIdInt int fromUserId, @UserIdInt int toUserId) {
        if (DBG) {
            Slogf.i(TAG, "onUserSwitching(from=%d, to=%d)", fromUserId, toUserId);
        }
        TimingsTraceLog t = new TimingsTraceLog(TAG, TraceHelper.TRACE_TAG_CAR_SERVICE);
        t.traceBegin("onUserSwitching-" + toUserId);

        notifyLegacyUserSwitch(fromUserId, toUserId);

        mInitialUserSetter.setLastActiveUser(toUserId);

        t.traceEnd();
    }

    private void notifyLegacyUserSwitch(@UserIdInt int fromUserId, @UserIdInt int toUserId) {
        if (DBG) {
            Slogf.d(TAG, "notifyLegacyUserSwitch(%d, %d): mUserIdForUserSwitchInProcess=%d",
                    fromUserId, toUserId, mUserIdForUserSwitchInProcess);
        }
        synchronized (mLockUser) {
            if (mUserIdForUserSwitchInProcess != USER_NULL) {
                if (mUserIdForUserSwitchInProcess == toUserId) {
                    if (DBG) {
                        Slogf.d(TAG, "Ignoring, not legacy");
                    }
                    return;
                }
                if (DBG) {
                    Slogf.d(TAG, "Resetting mUserIdForUserSwitchInProcess");
                }
                mUserIdForUserSwitchInProcess = USER_NULL;
                mRequestIdForUserSwitchInProcess = 0;
            }
        }

        sendUserSwitchUiCallback(toUserId);

        // Switch HAL users if user switch is not requested by CarUserService
        notifyHalLegacySwitch(fromUserId, toUserId);
    }

    private void notifyHalLegacySwitch(@UserIdInt int fromUserId, @UserIdInt int toUserId) {
        if (!isUserHalSupported()) {
            if (DBG) {
                Slogf.d(TAG, "notifyHalLegacySwitch(): not calling UserHal (not supported)");
            }
            return;
        }

        // switch HAL user
        UsersInfo usersInfo = UserHalHelper.newUsersInfo(mUserManager, mUserHandleHelper,
                fromUserId);
        SwitchUserRequest request = createUserSwitchRequest(toUserId, usersInfo);
        mHal.legacyUserSwitch(request);
    }

    /**
     * Runs the given runnable when user 0 is unlocked. If user 0 is already unlocked, it is
     * run inside this call.
     *
     * @param r Runnable to run.
     */
    public void runOnUser0Unlock(@NonNull Runnable r) {
        Objects.requireNonNull(r, "runnable cannot be null");
        boolean runNow = false;
        synchronized (mLockUser) {
            if (mUser0Unlocked) {
                runNow = true;
            } else {
                mUser0UnlockTasks.add(r);
            }
        }
        if (runNow) {
            r.run();
        }
    }

    @VisibleForTesting
    @NonNull
    ArrayList<Integer> getBackgroundUsersToRestart() {
        ArrayList<Integer> backgroundUsersToRestart = null;
        synchronized (mLockUser) {
            backgroundUsersToRestart = new ArrayList<>(mBackgroundUsersToRestart);
        }
        return backgroundUsersToRestart;
    }

    private void setSystemUserRestrictions() {
        // Disable Location service for system user.
        LocationManager locationManager =
                (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        locationManager.setLocationEnabledForUser(
                /* enabled= */ false, UserHandle.SYSTEM);
        locationManager.setAdasGnssLocationEnabled(false);
    }

    private void checkInteractAcrossUsersPermission(String message) {
        checkHasAtLeastOnePermissionGranted(mContext, message,
                android.Manifest.permission.INTERACT_ACROSS_USERS,
                android.Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    /**
     * Manages the required number of pre-created users.
     */
    @Override
    public void updatePreCreatedUsers() {
        checkManageOrCreateUsersPermission("preCreateUsers");
        preCreateUsersInternal(/* waitTimeMs= */ DEFAULT_PRE_CREATION_DELAY_MS);
    }

    private void preCreateUsersInternal(int waitTimeMs) {
        mHandler.postDelayed(() -> mUserPreCreator.managePreCreatedUsers(), waitTimeMs);
    }

    // TODO(b/167698977): members below were copied from UserManagerService; it would be better to
    // move them to some internal android.os class instead.
    private static final int ALLOWED_FLAGS_FOR_CREATE_USERS_PERMISSION =
            UserManagerHelper.FLAG_MANAGED_PROFILE
            | UserManagerHelper.FLAG_PROFILE
            | UserManagerHelper.FLAG_EPHEMERAL
            | UserManagerHelper.FLAG_RESTRICTED
            | UserManagerHelper.FLAG_GUEST
            | UserManagerHelper.FLAG_DEMO
            | UserManagerHelper.FLAG_FULL;

    static void checkManageUsersPermission(String message) {
        if (!hasManageUsersPermission()) {
            throw new SecurityException("You need " + MANAGE_USERS + " permission to: " + message);
        }
    }

    private static void checkManageOrCreateUsersPermission(String message) {
        if (!hasManageOrCreateUsersPermission()) {
            throw new SecurityException(
                    "You either need " + MANAGE_USERS + " or " + CREATE_USERS + " permission to: "
            + message);
        }
    }

    private static void checkManageOrCreateUsersPermission(int creationFlags) {
        if ((creationFlags & ~ALLOWED_FLAGS_FOR_CREATE_USERS_PERMISSION) == 0) {
            if (!hasManageOrCreateUsersPermission()) {
                throw new SecurityException("You either need " + MANAGE_USERS + " or "
                        + CREATE_USERS + "permission to create a user with flags "
                        + creationFlags);
            }
        } else if (!hasManageUsersPermission()) {
            throw new SecurityException("You need " + MANAGE_USERS + " permission to create a user"
                    + " with flags " + creationFlags);
        }
    }

    private static boolean hasManageUsersPermission() {
        final int callingUid = Binder.getCallingUid();
        return isSameApp(callingUid, Process.SYSTEM_UID)
                || callingUid == Process.ROOT_UID
                || hasPermissionGranted(MANAGE_USERS, callingUid);
    }

    private static boolean hasManageUsersOrPermission(String alternativePermission) {
        final int callingUid = Binder.getCallingUid();
        return isSameApp(callingUid, Process.SYSTEM_UID)
                || callingUid == Process.ROOT_UID
                || hasPermissionGranted(MANAGE_USERS, callingUid)
                || hasPermissionGranted(alternativePermission, callingUid);
    }

    private static boolean isSameApp(int uid1, int uid2) {
        return UserHandle.getAppId(uid1) == UserHandle.getAppId(uid2);
    }

    private static boolean hasManageOrCreateUsersPermission() {
        return hasManageUsersOrPermission(CREATE_USERS);
    }

    private static boolean hasPermissionGranted(String permission, int uid) {
        return ActivityManagerHelper.checkComponentPermission(permission, uid, /* owningUid= */ -1,
                /* exported= */ true) == PackageManager.PERMISSION_GRANTED;
    }

    private static String userOperationErrorToString(int error) {
        return DebugUtils.constantToString(UserManager.class, "USER_OPERATION_", error);
    }
}
