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
package com.android.car;

import static android.car.Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME;
import static android.car.Car.PERMISSION_CAR_POWER;
import static android.car.Car.PERMISSION_CONTROL_CAR_POWER_POLICY;
import static android.car.Car.PERMISSION_CONTROL_CAR_WATCHDOG_CONFIG;
import static android.car.Car.PERMISSION_USE_CAR_WATCHDOG;
import static android.car.telemetry.CarTelemetryManager.STATUS_ADD_METRICS_CONFIG_SUCCEEDED;
import static android.hardware.automotive.vehicle.UserIdentificationAssociationSetValue.ASSOCIATE_CURRENT_USER;
import static android.hardware.automotive.vehicle.UserIdentificationAssociationSetValue.DISASSOCIATE_ALL_USERS;
import static android.hardware.automotive.vehicle.UserIdentificationAssociationSetValue.DISASSOCIATE_CURRENT_USER;
import static android.hardware.automotive.vehicle.UserIdentificationAssociationType.CUSTOM_1;
import static android.hardware.automotive.vehicle.UserIdentificationAssociationType.CUSTOM_2;
import static android.hardware.automotive.vehicle.UserIdentificationAssociationType.CUSTOM_3;
import static android.hardware.automotive.vehicle.UserIdentificationAssociationType.CUSTOM_4;
import static android.hardware.automotive.vehicle.UserIdentificationAssociationType.KEY_FOB;
import static android.media.AudioManager.FLAG_SHOW_UI;

import static com.android.car.CarServiceUtils.toIntArray;
import static com.android.car.power.PolicyReader.POWER_STATE_ON;
import static com.android.car.power.PolicyReader.POWER_STATE_WAIT_FOR_VHAL;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.UiModeManager;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.CarVersion;
import android.car.VehiclePropertyIds;
import android.car.builtin.content.pm.PackageManagerHelper;
import android.car.builtin.os.BuildHelper;
import android.car.builtin.os.UserManagerHelper;
import android.car.builtin.util.Slogf;
import android.car.builtin.widget.LockPatternHelper;
import android.car.content.pm.CarPackageManager;
import android.car.input.CarInputManager;
import android.car.input.CustomInputEvent;
import android.car.input.RotaryEvent;
import android.car.telemetry.CarTelemetryManager;
import android.car.telemetry.TelemetryProto.TelemetryError;
import android.car.user.CarUserManager;
import android.car.user.UserCreationResult;
import android.car.user.UserIdentificationAssociationResponse;
import android.car.user.UserRemovalResult;
import android.car.user.UserSwitchResult;
import android.car.util.concurrent.AsyncFuture;
import android.car.watchdog.CarWatchdogManager;
import android.car.watchdog.IoOveruseConfiguration;
import android.car.watchdog.PerStateBytes;
import android.car.watchdog.ResourceOveruseConfiguration;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.hardware.automotive.vehicle.CreateUserRequest;
import android.hardware.automotive.vehicle.CreateUserStatus;
import android.hardware.automotive.vehicle.InitialUserInfoResponse;
import android.hardware.automotive.vehicle.InitialUserInfoResponseAction;
import android.hardware.automotive.vehicle.RemoveUserRequest;
import android.hardware.automotive.vehicle.SwitchUserMessageType;
import android.hardware.automotive.vehicle.SwitchUserRequest;
import android.hardware.automotive.vehicle.SwitchUserStatus;
import android.hardware.automotive.vehicle.UserIdentificationAssociation;
import android.hardware.automotive.vehicle.UserIdentificationAssociationValue;
import android.hardware.automotive.vehicle.UserIdentificationGetRequest;
import android.hardware.automotive.vehicle.UserIdentificationResponse;
import android.hardware.automotive.vehicle.UserIdentificationSetAssociation;
import android.hardware.automotive.vehicle.UserIdentificationSetRequest;
import android.hardware.automotive.vehicle.UserInfo;
import android.hardware.automotive.vehicle.UsersInfo;
import android.hardware.automotive.vehicle.VehicleArea;
import android.hardware.automotive.vehicle.VehicleDisplay;
import android.hardware.automotive.vehicle.VehicleGear;
import android.hardware.automotive.vehicle.VehiclePropError;
import android.os.Binder;
import android.os.FileUtils;
import android.os.IBinder;
import android.os.NewUserRequest;
import android.os.NewUserResponse;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.SparseArray;
import android.view.KeyEvent;

import com.android.car.am.FixedActivityService;
import com.android.car.audio.CarAudioService;
import com.android.car.evs.CarEvsService;
import com.android.car.garagemode.GarageModeService;
import com.android.car.hal.HalCallback;
import com.android.car.hal.HalPropConfig;
import com.android.car.hal.HalPropValue;
import com.android.car.hal.InputHalService;
import com.android.car.hal.PowerHalService;
import com.android.car.hal.UserHalHelper;
import com.android.car.hal.UserHalService;
import com.android.car.hal.VehicleHal;
import com.android.car.internal.util.DebugUtils;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.pm.CarPackageManagerService;
import com.android.car.power.CarPowerManagementService;
import com.android.car.systeminterface.SystemInterface;
import com.android.car.telemetry.CarTelemetryService;
import com.android.car.telemetry.scriptexecutorinterface.IScriptExecutor;
import com.android.car.telemetry.scriptexecutorinterface.IScriptExecutorListener;
import com.android.car.telemetry.util.IoUtils;
import com.android.car.user.CarUserService;
import com.android.car.user.UserHandleHelper;
import com.android.car.watchdog.CarWatchdogService;
import com.android.internal.util.Preconditions;
import com.android.modules.utils.BasicShellCommandHandler;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

final class CarShellCommand extends BasicShellCommandHandler {

    private static final String NO_INITIAL_USER = "N/A";

    private static final String TAG = CarLog.tagFor(CarShellCommand.class);
    private static final boolean VERBOSE = false;

    private static final String COMMAND_HELP = "-h";
    private static final String COMMAND_DAY_NIGHT_MODE = "day-night-mode";
    private static final String COMMAND_INJECT_VHAL_EVENT = "inject-vhal-event";
    private static final String COMMAND_INJECT_ERROR_EVENT = "inject-error-event";
    private static final String COMMAND_INJECT_CONTINUOUS_EVENT = "inject-continuous-events";
    private static final String COMMAND_ENABLE_UXR = "enable-uxr";
    private static final String COMMAND_GARAGE_MODE = "garage-mode";
    private static final String COMMAND_GET_DO_ACTIVITIES = "get-do-activities";
    private static final String COMMAND_GET_CARPROPERTYCONFIG = "get-carpropertyconfig";
    private static final String COMMAND_GET_PROPERTY_VALUE = "get-property-value";
    private static final String COMMAND_SET_PROPERTY_VALUE = "set-property-value";
    private static final String COMMAND_PROJECTION_AP_TETHERING = "projection-tethering";
    private static final String COMMAND_PROJECTION_AP_STABLE_CONFIG =
            "projection-stable-lohs-config";
    private static final String COMMAND_PROJECTION_UI_MODE = "projection-ui-mode";
    private static final String COMMAND_RESUME = "resume";
    private static final String COMMAND_SUSPEND = "suspend";
    private static final String COMMAND_HIBERNATE = "hibernate";
    private static final String PARAM_SIMULATE = "--simulate";
    private static final String PARAM_REAL = "--real";
    private static final String PARAM_AUTO = "--auto";
    private static final String PARAM_SKIP_GARAGEMODE = "--skip-garagemode";
    private static final String PARAM_REBOOT = "--reboot";
    private static final String PARAM_WAKEUP_AFTER = "--wakeup-after";
    private static final String COMMAND_SET_UID_TO_ZONE = "set-audio-zone-for-uid";
    private static final String COMMAND_RESET_VOLUME_CONTEXT = "reset-selected-volume-context";
    private static final String COMMAND_SET_MUTE_CAR_VOLUME_GROUP = "set-mute-car-volume-group";
    private static final String COMMAND_SET_GROUP_VOLUME = "set-group-volume";
    private static final String COMMAND_START_FIXED_ACTIVITY_MODE = "start-fixed-activity-mode";
    private static final String COMMAND_STOP_FIXED_ACTIVITY_MODE = "stop-fixed-activity-mode";
    private static final String COMMAND_ENABLE_FEATURE = "enable-feature";
    private static final String COMMAND_DISABLE_FEATURE = "disable-feature";
    private static final String COMMAND_INJECT_KEY = "inject-key";
    private static final String COMMAND_INJECT_ROTARY = "inject-rotary";
    private static final String COMMAND_INJECT_CUSTOM_INPUT = "inject-custom-input";
    private static final String COMMAND_CHECK_LOCK_IS_SECURE = "check-lock-is-secure";
    private static final String COMMAND_GET_INITIAL_USER_INFO = "get-initial-user-info";
    private static final String COMMAND_SWITCH_USER = "switch-user";
    private static final String COMMAND_LOGOUT_USER = "logout-user";
    private static final String COMMAND_REMOVE_USER = "remove-user";
    private static final String COMMAND_CREATE_USER = "create-user";
    private static final String COMMAND_GET_INITIAL_USER = "get-initial-user";
    private static final String COMMAND_SET_USER_ID_TO_OCCUPANT_ZONE =
            "set-occupant-zone-for-user";
    private static final String COMMAND_RESET_USER_ID_IN_OCCUPANT_ZONE =
            "reset-user-in-occupant-zone";
    private static final String COMMAND_GET_USER_AUTH_ASSOCIATION =
            "get-user-auth-association";
    private static final String COMMAND_SET_USER_AUTH_ASSOCIATION =
            "set-user-auth-association";
    private static final String COMMAND_SET_START_BG_USERS_ON_GARAGE_MODE =
            "set-start-bg-users-on-garage-mode";
    private static final String COMMAND_DEFINE_POWER_POLICY = "define-power-policy";
    private static final String COMMAND_APPLY_POWER_POLICY = "apply-power-policy";
    private static final String COMMAND_DEFINE_POWER_POLICY_GROUP = "define-power-policy-group";
    private static final String COMMAND_SET_POWER_POLICY_GROUP = "set-power-policy-group";
    private static final String COMMAND_APPLY_CTS_VERIFIER_POWER_OFF_POLICY =
            "apply-cts-verifier-power-off-policy";
    private static final String COMMAND_APPLY_CTS_VERIFIER_POWER_ON_POLICY =
            "apply-cts-verifier-power-on-policy";
    private static final String COMMAND_POWER_OFF = "power-off";
    private static final String COMMAND_SILENT_MODE = "silent-mode";
    // Used with COMMAND_SILENT_MODE for forced silent: "forced-silent"
    private static final String SILENT_MODE_FORCED_SILENT =
            CarPowerManagementService.SILENT_MODE_FORCED_SILENT;
    // Used with COMMAND_SILENT_MODE for forced non silent: "forced-non-silent"
    private static final String SILENT_MODE_FORCED_NON_SILENT =
            CarPowerManagementService.SILENT_MODE_FORCED_NON_SILENT;
    // Used with COMMAND_SILENT_MODE for non forced silent mode: "non-forced-silent-mode"
    private static final String SILENT_MODE_NON_FORCED =
            CarPowerManagementService.SILENT_MODE_NON_FORCED;

    private static final String COMMAND_EMULATE_DRIVING_STATE = "emulate-driving-state";
    private static final String DRIVING_STATE_DRIVE = "drive";
    private static final String DRIVING_STATE_PARK = "park";
    private static final String DRIVING_STATE_REVERSE = "reverse";

    private static final String COMMAND_SET_REARVIEW_CAMERA_ID = "set-rearview-camera-id";
    private static final String COMMAND_GET_REARVIEW_CAMERA_ID = "get-rearview-camera-id";

    private static final String COMMAND_WATCHDOG_CONTROL_PACKAGE_KILLABLE_STATE =
            "watchdog-control-package-killable-state";
    private static final String COMMAND_WATCHDOG_IO_SET_3P_FOREGROUND_BYTES =
            "watchdog-io-set-3p-foreground-bytes";
    private static final String COMMAND_WATCHDOG_IO_GET_3P_FOREGROUND_BYTES =
            "watchdog-io-get-3p-foreground-bytes";
    private static final String COMMAND_WATCHDOG_CONTROL_PROCESS_HEALTH_CHECK =
            "watchdog-control-health-check";
    private static final String COMMAND_WATCHDOG_RESOURCE_OVERUSE_KILL =
            "watchdog-resource-overuse-kill";

    private static final String COMMAND_DRIVING_SAFETY_SET_REGION =
            "set-drivingsafety-region";

    private static final String COMMAND_TELEMETRY = "telemetry";
    private static final String COMMAND_CONTROL_COMPONENT_ENABLED_STATE =
            "control-component-enabled-state";

    private static final String COMMAND_LIST_VHAL_PROPS = "list-vhal-props";
    private static final String COMMAND_GET_VHAL_BACKEND = "get-vhal-backend";

    private static final String COMMAND_TEST_ECHO_REVERSE_BYTES = "test-echo-reverse-bytes";

    private static final String COMMAND_GET_TARGET_CAR_VERSION = "get-target-car-version";

    private static final String[] CREATE_OR_MANAGE_USERS_PERMISSIONS = new String[] {
            android.Manifest.permission.CREATE_USERS,
            android.Manifest.permission.MANAGE_USERS
    };

    // List of commands allowed in user build. All these command should be protected with
    // a permission. K: command, V: required permissions (must have at least 1).
    // Only commands with permission already granted to shell user should be allowed.
    // Commands that can affect safety should be never allowed in user build.
    //
    // This map is looked up first, then USER_BUILD_COMMAND_TO_PERMISSION_MAP
    private static final ArrayMap<String, String[]> USER_BUILD_COMMAND_TO_PERMISSIONS_MAP;
    static {
        USER_BUILD_COMMAND_TO_PERMISSIONS_MAP = new ArrayMap<>(8);
        USER_BUILD_COMMAND_TO_PERMISSIONS_MAP.put(COMMAND_GET_INITIAL_USER_INFO,
                CREATE_OR_MANAGE_USERS_PERMISSIONS);
        USER_BUILD_COMMAND_TO_PERMISSIONS_MAP.put(COMMAND_SWITCH_USER,
                CREATE_OR_MANAGE_USERS_PERMISSIONS);
        USER_BUILD_COMMAND_TO_PERMISSIONS_MAP.put(COMMAND_LOGOUT_USER,
                CREATE_OR_MANAGE_USERS_PERMISSIONS);
        USER_BUILD_COMMAND_TO_PERMISSIONS_MAP.put(COMMAND_REMOVE_USER,
                CREATE_OR_MANAGE_USERS_PERMISSIONS);
        USER_BUILD_COMMAND_TO_PERMISSIONS_MAP.put(COMMAND_CREATE_USER,
                CREATE_OR_MANAGE_USERS_PERMISSIONS);
        USER_BUILD_COMMAND_TO_PERMISSIONS_MAP.put(COMMAND_GET_USER_AUTH_ASSOCIATION,
                CREATE_OR_MANAGE_USERS_PERMISSIONS);
        USER_BUILD_COMMAND_TO_PERMISSIONS_MAP.put(COMMAND_SET_USER_AUTH_ASSOCIATION,
                CREATE_OR_MANAGE_USERS_PERMISSIONS);
        USER_BUILD_COMMAND_TO_PERMISSIONS_MAP.put(COMMAND_SET_START_BG_USERS_ON_GARAGE_MODE,
                CREATE_OR_MANAGE_USERS_PERMISSIONS);
    }

    // List of commands allowed in user build. All these command should be protected with
    // a permission. K: command, V: required permission.
    // Only commands with permission already granted to shell user should be allowed.
    // Commands that can affect safety should be never allowed in user build.
    private static final ArrayMap<String, String> USER_BUILD_COMMAND_TO_PERMISSION_MAP;
    static {
        USER_BUILD_COMMAND_TO_PERMISSION_MAP = new ArrayMap<>(27);
        USER_BUILD_COMMAND_TO_PERMISSION_MAP.put(COMMAND_GARAGE_MODE, PERMISSION_CAR_POWER);
        USER_BUILD_COMMAND_TO_PERMISSION_MAP.put(COMMAND_RESUME, PERMISSION_CAR_POWER);
        USER_BUILD_COMMAND_TO_PERMISSION_MAP.put(COMMAND_SUSPEND, PERMISSION_CAR_POWER);
        USER_BUILD_COMMAND_TO_PERMISSION_MAP.put(COMMAND_HIBERNATE, PERMISSION_CAR_POWER);
        USER_BUILD_COMMAND_TO_PERMISSION_MAP.put(COMMAND_POWER_OFF, PERMISSION_CAR_POWER);
        USER_BUILD_COMMAND_TO_PERMISSION_MAP.put(COMMAND_DEFINE_POWER_POLICY, PERMISSION_CAR_POWER);
        USER_BUILD_COMMAND_TO_PERMISSION_MAP.put(COMMAND_APPLY_POWER_POLICY,
                PERMISSION_CONTROL_CAR_POWER_POLICY);
        USER_BUILD_COMMAND_TO_PERMISSION_MAP.put(COMMAND_DEFINE_POWER_POLICY_GROUP,
                PERMISSION_CAR_POWER);
        USER_BUILD_COMMAND_TO_PERMISSION_MAP.put(COMMAND_SET_POWER_POLICY_GROUP,
                PERMISSION_CONTROL_CAR_POWER_POLICY);
        USER_BUILD_COMMAND_TO_PERMISSION_MAP.put(COMMAND_APPLY_CTS_VERIFIER_POWER_OFF_POLICY,
                PERMISSION_CONTROL_CAR_POWER_POLICY);
        USER_BUILD_COMMAND_TO_PERMISSION_MAP.put(COMMAND_APPLY_CTS_VERIFIER_POWER_ON_POLICY,
                PERMISSION_CONTROL_CAR_POWER_POLICY);
        USER_BUILD_COMMAND_TO_PERMISSION_MAP.put(COMMAND_SILENT_MODE, PERMISSION_CAR_POWER);
        USER_BUILD_COMMAND_TO_PERMISSION_MAP.put(COMMAND_GET_INITIAL_USER,
                android.Manifest.permission.INTERACT_ACROSS_USERS_FULL);
        USER_BUILD_COMMAND_TO_PERMISSION_MAP.put(COMMAND_DAY_NIGHT_MODE,
                android.Manifest.permission.MODIFY_DAY_NIGHT_MODE);
        USER_BUILD_COMMAND_TO_PERMISSION_MAP.put(COMMAND_RESET_VOLUME_CONTEXT,
                PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
        USER_BUILD_COMMAND_TO_PERMISSION_MAP.put(COMMAND_SET_MUTE_CAR_VOLUME_GROUP,
                PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
        USER_BUILD_COMMAND_TO_PERMISSION_MAP.put(COMMAND_SET_GROUP_VOLUME,
                PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
        USER_BUILD_COMMAND_TO_PERMISSION_MAP.put(COMMAND_INJECT_KEY,
                android.Manifest.permission.INJECT_EVENTS);
        USER_BUILD_COMMAND_TO_PERMISSION_MAP.put(COMMAND_INJECT_ROTARY,
                android.Manifest.permission.INJECT_EVENTS);
        USER_BUILD_COMMAND_TO_PERMISSION_MAP.put(COMMAND_WATCHDOG_CONTROL_PACKAGE_KILLABLE_STATE,
                PERMISSION_CONTROL_CAR_WATCHDOG_CONFIG);
        USER_BUILD_COMMAND_TO_PERMISSION_MAP.put(COMMAND_WATCHDOG_IO_SET_3P_FOREGROUND_BYTES,
                PERMISSION_CONTROL_CAR_WATCHDOG_CONFIG);
        USER_BUILD_COMMAND_TO_PERMISSION_MAP.put(COMMAND_WATCHDOG_IO_GET_3P_FOREGROUND_BYTES,
                PERMISSION_CONTROL_CAR_WATCHDOG_CONFIG);
        USER_BUILD_COMMAND_TO_PERMISSION_MAP.put(COMMAND_WATCHDOG_CONTROL_PROCESS_HEALTH_CHECK,
                PERMISSION_USE_CAR_WATCHDOG);
        USER_BUILD_COMMAND_TO_PERMISSION_MAP.put(COMMAND_WATCHDOG_RESOURCE_OVERUSE_KILL,
                PERMISSION_USE_CAR_WATCHDOG);
        USER_BUILD_COMMAND_TO_PERMISSION_MAP.put(COMMAND_CONTROL_COMPONENT_ENABLED_STATE,
                android.Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE);
        // borrow the permission to pass assertHasAtLeastOnePermission() for a user build
        USER_BUILD_COMMAND_TO_PERMISSION_MAP.put(COMMAND_CHECK_LOCK_IS_SECURE,
                android.Manifest.permission.INJECT_EVENTS);
        USER_BUILD_COMMAND_TO_PERMISSION_MAP.put(COMMAND_TEST_ECHO_REVERSE_BYTES,
                android.car.Car.PERMISSION_CAR_DIAGNOSTIC_READ_ALL);
        USER_BUILD_COMMAND_TO_PERMISSION_MAP.put(COMMAND_GET_TARGET_CAR_VERSION,
                android.Manifest.permission.QUERY_ALL_PACKAGES);
    }

    private static final String PARAM_DAY_MODE = "day";
    private static final String PARAM_NIGHT_MODE = "night";
    private static final String PARAM_SENSOR_MODE = "sensor";
    private static final String PARAM_VEHICLE_PROPERTY_AREA_GLOBAL = "0";
    private static final String PARAM_INJECT_EVENT_DEFAULT_RATE = "10";
    private static final String PARAM_INJECT_EVENT_DEFAULT_DURATION = "60";
    private static final String PARAM_ALL_PROPERTIES_OR_AREA = "-1";
    private static final String PARAM_ON_MODE = "on";
    private static final String PARAM_OFF_MODE = "off";
    private static final String PARAM_QUERY_MODE = "query";
    private static final String PARAM_REBOOT_AFTER_GARAGEMODE = "reboot";
    private static final String PARAM_MUTE = "mute";
    private static final String PARAM_UNMUTE = "unmute";


    private static final int RESULT_OK = 0;
    private static final int RESULT_ERROR = -1; // Arbitrary value, any non-0 is fine

    private static final int DEFAULT_HAL_TIMEOUT_MS = 1_000;

    private static final int DEFAULT_CAR_USER_SERVICE_TIMEOUT_MS = 60_000;

    private static final int INVALID_USER_AUTH_TYPE_OR_VALUE = -1;

    private static final SparseArray<String> VALID_USER_AUTH_TYPES;
    private static final String VALID_USER_AUTH_TYPES_HELP;

    private static final SparseArray<String> VALID_USER_AUTH_SET_VALUES;
    private static final String VALID_USER_AUTH_SET_VALUES_HELP;

    private static final ArrayMap<String, Integer> CUSTOM_INPUT_FUNCTION_ARGS;

    static {
        VALID_USER_AUTH_TYPES = new SparseArray<>(5);
        VALID_USER_AUTH_TYPES.put(KEY_FOB, "KEY_FOB");
        VALID_USER_AUTH_TYPES.put(CUSTOM_1, "CUSTOM_1");
        VALID_USER_AUTH_TYPES.put(CUSTOM_2, "CUSTOM_2");
        VALID_USER_AUTH_TYPES.put(CUSTOM_3, "CUSTOM_3");
        VALID_USER_AUTH_TYPES.put(CUSTOM_4, "CUSTOM_4");
        VALID_USER_AUTH_TYPES_HELP = getHelpString("types", VALID_USER_AUTH_TYPES);

        VALID_USER_AUTH_SET_VALUES = new SparseArray<>(3);
        VALID_USER_AUTH_SET_VALUES.put(ASSOCIATE_CURRENT_USER, "ASSOCIATE_CURRENT_USER");
        VALID_USER_AUTH_SET_VALUES.put(DISASSOCIATE_CURRENT_USER, "DISASSOCIATE_CURRENT_USER");
        VALID_USER_AUTH_SET_VALUES.put(DISASSOCIATE_ALL_USERS, "DISASSOCIATE_ALL_USERS");
        VALID_USER_AUTH_SET_VALUES_HELP = getHelpString("values", VALID_USER_AUTH_SET_VALUES);

        CUSTOM_INPUT_FUNCTION_ARGS = new ArrayMap<>(10);
        CUSTOM_INPUT_FUNCTION_ARGS.put("f1", CustomInputEvent.INPUT_CODE_F1);
        CUSTOM_INPUT_FUNCTION_ARGS.put("f2", CustomInputEvent.INPUT_CODE_F2);
        CUSTOM_INPUT_FUNCTION_ARGS.put("f3", CustomInputEvent.INPUT_CODE_F3);
        CUSTOM_INPUT_FUNCTION_ARGS.put("f4", CustomInputEvent.INPUT_CODE_F4);
        CUSTOM_INPUT_FUNCTION_ARGS.put("f5", CustomInputEvent.INPUT_CODE_F5);
        CUSTOM_INPUT_FUNCTION_ARGS.put("f6", CustomInputEvent.INPUT_CODE_F6);
        CUSTOM_INPUT_FUNCTION_ARGS.put("f7", CustomInputEvent.INPUT_CODE_F7);
        CUSTOM_INPUT_FUNCTION_ARGS.put("f8", CustomInputEvent.INPUT_CODE_F8);
        CUSTOM_INPUT_FUNCTION_ARGS.put("f9", CustomInputEvent.INPUT_CODE_F9);
        CUSTOM_INPUT_FUNCTION_ARGS.put("f10", CustomInputEvent.INPUT_CODE_F10);
    }

    // CarTelemetryManager may not send result back if there are not results for the given
    // metrics config.
    private static final Duration TELEMETRY_RESULT_WAIT_TIMEOUT = Duration.ofSeconds(5);

    private static String getHelpString(String name, SparseArray<String> values) {
        StringBuilder help = new StringBuilder("Valid ").append(name).append(" are: ");
        int size = values.size();
        for (int i = 0; i < size; i++) {
            help.append(values.valueAt(i));
            if (i != size - 1) {
                help.append(", ");
            }
        }
        return help.append('.').toString();
    }

    private final Context mContext;
    private final VehicleHal mHal;
    private final CarAudioService mCarAudioService;
    private final CarPackageManagerService mCarPackageManagerService;
    private final CarProjectionService mCarProjectionService;
    private final CarPowerManagementService mCarPowerManagementService;
    private final FixedActivityService mFixedActivityService;
    private final CarFeatureController mFeatureController;
    private final CarInputService mCarInputService;
    private final CarNightService mCarNightService;
    private final SystemInterface mSystemInterface;
    private final GarageModeService mGarageModeService;
    private final CarUserService mCarUserService;
    private final CarOccupantZoneService mCarOccupantZoneService;
    private final CarEvsService mCarEvsService;
    private final CarWatchdogService mCarWatchdogService;
    private final CarTelemetryService mCarTelemetryService;
    private long mKeyDownTime;
    private ServiceConnection mScriptExecutorConn;
    private IScriptExecutor mScriptExecutor;

    CarShellCommand(Context context,
            VehicleHal hal,
            CarAudioService carAudioService,
            CarPackageManagerService carPackageManagerService,
            CarProjectionService carProjectionService,
            CarPowerManagementService carPowerManagementService,
            FixedActivityService fixedActivityService,
            CarFeatureController featureController,
            CarInputService carInputService,
            CarNightService carNightService,
            SystemInterface systemInterface,
            GarageModeService garageModeService,
            CarUserService carUserService,
            CarOccupantZoneService carOccupantZoneService,
            CarEvsService carEvsService,
            CarWatchdogService carWatchdogService,
            CarTelemetryService carTelemetryService) {
        mContext = context;
        mHal = hal;
        mCarAudioService = carAudioService;
        mCarPackageManagerService = carPackageManagerService;
        mCarProjectionService = carProjectionService;
        mCarPowerManagementService = carPowerManagementService;
        mFixedActivityService = fixedActivityService;
        mFeatureController = featureController;
        mCarInputService = carInputService;
        mCarNightService = carNightService;
        mSystemInterface = systemInterface;
        mGarageModeService = garageModeService;
        mCarUserService = carUserService;
        mCarOccupantZoneService = carOccupantZoneService;
        mCarEvsService = carEvsService;
        mCarWatchdogService = carWatchdogService;
        mCarTelemetryService = carTelemetryService;
    }

    @Override
    public int onCommand(String cmd) {
        if (cmd == null) {
            onHelp();
            return RESULT_ERROR;
        }
        ArrayList<String> argsList = new ArrayList<>();
        argsList.add(cmd);
        String arg = null;
        do {
            arg = getNextArg();
            if (arg != null) {
                argsList.add(arg);
            }
        } while (arg != null);
        String[] args = new String[argsList.size()];
        argsList.toArray(args);
        try (IndentingPrintWriter pw = new IndentingPrintWriter(getOutPrintWriter())) {
            return exec(args, pw);
        }
    }

    @Override
    public void onHelp() {
        try (IndentingPrintWriter pw = new IndentingPrintWriter(getOutPrintWriter())) {
            showHelp(pw);
        }
    }

    private static void showHelp(IndentingPrintWriter pw) {
        pw.println("Car service commands:");
        pw.println("\t-h");
        pw.println("\t  Print this help text.");
        pw.println("\tday-night-mode [day|night|sensor]");
        pw.println("\t  Force into day/night mode or restore to auto.");
        pw.println("\tinject-vhal-event <PROPERTY_ID in Hex or Decimal> [zone] "
                + "data(can be comma separated list) "
                + "[-t delay_time_seconds]");
        pw.println("\t  Inject a vehicle property for testing.");
        pw.println("\t  delay_time_seconds: the event timestamp is increased by certain second.");
        pw.println("\t  If not specified, it will be 0.");
        pw.println("\tinject-error-event <PROPERTY_ID in Hex or Decimal> zone <errorCode>");
        pw.println("\t  Inject an error event from VHAL for testing.");
        pw.println("\tinject-continuous-events <PROPERTY_ID in Hex or Decimal> "
                + "data(can be comma separated list) "
                + "[-z zone]  [-s SampleRate in Hz] [-d time duration in seconds]");
        pw.println("\t  Inject continuous vehicle events for testing.");
        pw.printf("\t  If not specified, CarService will inject fake events with areaId:%s "
                        + "at sample rate %s for %s seconds.", PARAM_VEHICLE_PROPERTY_AREA_GLOBAL,
                PARAM_INJECT_EVENT_DEFAULT_RATE, PARAM_INJECT_EVENT_DEFAULT_DURATION);
        pw.println("\tenable-uxr true|false");
        pw.println("\t  Enable/Disable UX restrictions and App blocking.");
        pw.println("\tgarage-mode [on|off|query|reboot]");
        pw.println("\t  Force into or out of garage mode, or check status.");
        pw.println("\t  With 'reboot', enter garage mode, then reboot when it completes.");
        pw.println("\tget-do-activities pkgname");
        pw.println("\t  Get Distraction Optimized activities in given package.");
        pw.println("\tget-carpropertyconfig [PROPERTY_ID in Hex or Decimal]");
        pw.println("\t  Get a CarPropertyConfig by Id or list all CarPropertyConfigs");
        pw.println("\tget-property-value [PROPERTY_ID in Hex or Decimal] [areaId]");
        pw.println("\t  Get a vehicle property value by property id and areaId");
        pw.println("\t  or list all property values for all areaId");
        pw.printf("\t%s\n", getSetPropertyValueUsage());
        pw.printf("\t%s\n", getSuspendCommandUsage(COMMAND_SUSPEND));
        pw.println("\t  Suspend the system to RAM.");
        pw.printf("\t  %s forces the device to perform suspend-to-RAM.\n", PARAM_REAL);
        pw.printf("\t  %s simulates suspend-to-RAM instead of putting the device into deep sleep."
                + "\n", PARAM_SIMULATE);
        pw.printf("\t  %s depending on the device capability, real or simulated suspend-to-RAM is "
                + "performed.\n", PARAM_AUTO);
        pw.printf("\t  %s skips Garage Mode before going into sleep.\n", PARAM_SKIP_GARAGEMODE);
        pw.printf("\t  %s [RESUME_DELAY] wakes up the device RESUME_DELAY seconds after suspend.\n",
                PARAM_WAKEUP_AFTER);
        pw.printf("\t%s\n", getSuspendCommandUsage(COMMAND_HIBERNATE));
        pw.println("\t  Suspend the system to disk.");
        pw.printf("\t  %s forces the device to perform suspend-to-disk.\n", PARAM_REAL);
        pw.printf("\t  %s simulates suspend-to-disk instead of putting the device into "
                + "hibernation.\n", PARAM_SIMULATE);
        pw.printf("\t  %s depending on the device capability, real or simulated suspend-to-disk is "
                + "performed.\n", PARAM_AUTO);
        pw.printf("\t  %s skips Garage Mode before going into hibernation.\n",
                PARAM_SKIP_GARAGEMODE);
        pw.println("\tresume");
        pw.println("\t  Wake the system up after a simulated suspension/hibernation.");
        pw.println("\tprojection-tethering [true|false]");
        pw.println("\t  Whether tethering should be used when creating access point for"
                + " wireless projection");
        pw.println("\t--metrics");
        pw.println("\t  When used with dumpsys, only metrics will be in the dumpsys output.");
        pw.printf("\t%s [zoneid] [uid]\n", COMMAND_SET_UID_TO_ZONE);
        pw.println("\t  Maps the audio zoneid to uid.");
        pw.printf("\t%s\n", COMMAND_RESET_VOLUME_CONTEXT);
        pw.println("\t  Resets the last selected volume context for volume changes.");
        pw.printf("\t%s [zoneId] [groupId] [%s\\%s]\n", COMMAND_SET_MUTE_CAR_VOLUME_GROUP,
                PARAM_MUTE, PARAM_UNMUTE);
        pw.printf("\t  %s\\%s groupId in zoneId\n", PARAM_MUTE, PARAM_UNMUTE);
        pw.printf("\t%s [zoneId] [groupId] [volume]\n", COMMAND_SET_GROUP_VOLUME);
        pw.println("\t  sets the group volume for [groupId] in [zoneId] to %volume,");
        pw.println("\t  [volume] must be an integer between 0 to 100");
        pw.println("\tstart-fixed-activity displayId packageName activityName");
        pw.println("\t  Start an Activity the specified display as fixed mode");
        pw.println("\tstop-fixed-mode displayId");
        pw.println("\t  Stop fixed Activity mode for the given display. "
                + "The Activity will not be restarted upon crash.");
        pw.println("\tenable-feature featureName");
        pw.println("\t  Enable the requested feature. Change will happen after reboot.");
        pw.println("\t  This requires root/su.");
        pw.println("\tdisable-feature featureName");
        pw.println("\t  Disable the requested feature. Change will happen after reboot");
        pw.println("\t  This requires root/su.");
        pw.println("\tinject-key [-d display] [-t down_delay_ms | -a down|up] key_code");
        pw.println("\t  inject key down and/or up event to car service");
        pw.println("\t  display: 0 for main, 1 for cluster. If not specified, it will be 0.");
        pw.println("\t  down_delay_ms: delay from down to up key event. If not specified,");
        pw.println("\t                 it will be 0");
        pw.println("\t  key_code: int key code defined in android KeyEvent");
        pw.println("\t  If -a isn't specified, both down and up will be injected.");
        pw.println("\tinject-rotary [-d display] [-i input_type] [-c clockwise]");
        pw.println("\t              [-dt delta_times_ms]");
        pw.println("\t  inject rotary input event to car service.");
        pw.println("\t  display: 0 for main, 1 for cluster. If not specified, it will be 0.");
        pw.println("\t  input_type: 10 for navigation controller input, 11 for volume");
        pw.println("\t              controller input. If not specified, it will be 10.");
        pw.println("\t  clockwise: true if the event is clockwise, false if the event is");
        pw.println("\t             counter-clockwise. If not specified, it will be false.");
        pw.println("\t  delta_times_ms: a list of delta time (current time minus event time)");
        pw.println("\t                  in descending order. If not specified, it will be 0.");
        pw.println("\tinject-custom-input [-d display] [-r repeatCounter] EVENT");
        pw.println("\t  display: 0 for main, 1 for cluster. If not specified, it will be 0.");
        pw.println("\t  repeatCounter: number of times the button was hit (default value is 1)");
        pw.println("\t  EVENT: mandatory last argument. Possible values for for this flag are ");
        pw.println("\t         F1, F2, up to F10 (functions to defined by OEM partners)");
        pw.printf("\t%s <REQ_TYPE> [--timeout TIMEOUT_MS]\n", COMMAND_GET_INITIAL_USER_INFO);
        pw.println("\t  Calls the Vehicle HAL to get the initial boot info, passing the given");
        pw.println("\t  REQ_TYPE (which could be either FIRST_BOOT, FIRST_BOOT_AFTER_OTA, ");
        pw.println("\t  COLD_BOOT, RESUME, or any numeric value that would be passed 'as-is')");
        pw.println("\t  and an optional TIMEOUT_MS to wait for the HAL response (if not set,");
        pw.println("\t  it will use a  default value).");
        pw.println("\t  The --hal-only option only calls HAL, without using CarUserService.");

        pw.printf("\t%s <USER_ID> [--hal-only] [--timeout TIMEOUT_MS]\n", COMMAND_SWITCH_USER);
        pw.println("\t  Switches to user USER_ID using the HAL integration.");
        pw.println("\t  The --hal-only option only calls HAL, without switching the user,");
        pw.println("\t  while the --timeout defines how long to wait for the response.");

        pw.printf("\t%s [--timeout TIMEOUT_MS]\n", COMMAND_LOGOUT_USER);
        pw.println("\t  Logout the current user (if the user was switched toby a device admin).");
        pw.println("\t  The --timeout option defines how long to wait for the UserHal response.");

        pw.printf("\t%s <USER_ID> [--hal-only]\n", COMMAND_REMOVE_USER);
        pw.println("\t  Removes user with USER_ID using the HAL integration.");
        pw.println("\t  The --hal-only option only calls HAL, without removing the user,");

        pw.printf("\t%s [--hal-only] [--timeout TIMEOUT_MS] [--guest] [--flags FLAGS] [NAME]\n",
                COMMAND_CREATE_USER);
        pw.println("\t  Creates a new user using the HAL integration.");
        pw.println("\t  The --hal-only uses UserManager to create the user,");
        pw.println("\t  while the --timeout defines how long to wait for the response.");

        pw.printf("\t%s\n", COMMAND_GET_INITIAL_USER);
        pw.printf("\t  Gets the id of the initial user (or %s when it's not available)\n",
                NO_INITIAL_USER);

        pw.printf("\t%s [occupantZoneId] [userId]\n", COMMAND_SET_USER_ID_TO_OCCUPANT_ZONE);
        pw.println("\t  Maps the occupant zone id to user id.");
        pw.printf("\t%s [occupantZoneId]\n", COMMAND_RESET_USER_ID_IN_OCCUPANT_ZONE);
        pw.println("\t  Unmaps the user assigned to occupant zone id.");

        pw.printf("\t%s [--hal-only] [--user USER_ID] TYPE1 [..TYPE_N]\n",
                COMMAND_GET_USER_AUTH_ASSOCIATION);
        pw.println("\t  Gets the N user authentication values for the N types for the given user");
        pw.println("\t  (or current user when not specified).");
        pw.println("\t  By defautt it calls CarUserManager, but using --hal-only will call just "
                + "UserHalService.");

        pw.printf("\t%s [--hal-only] [--user USER_ID] TYPE1 VALUE1 [..TYPE_N VALUE_N]\n",
                COMMAND_SET_USER_AUTH_ASSOCIATION);
        pw.println("\t  Sets the N user authentication types with the N values for the given user");
        pw.println("\t  (or current user when not specified).");
        pw.println("\t  By default it calls CarUserManager, but using --hal-only will call just "
                + "UserHalService.");
        pw.printf("\t  %s\n", VALID_USER_AUTH_TYPES_HELP);
        pw.printf("\t  %s\n", VALID_USER_AUTH_SET_VALUES_HELP);

        pw.printf("\t%s [true|false]\n", COMMAND_SET_START_BG_USERS_ON_GARAGE_MODE);
        pw.println("\t  Controls backgroud user start and stop during garage mode.");
        pw.println("\t  If false, garage mode operations (background users start at garage mode"
                + " entry and background users stop at garage mode exit) will be skipped.");

        pw.printf("\t  %s [%s|%s|%s|%s]\n", COMMAND_SILENT_MODE, SILENT_MODE_FORCED_SILENT,
                SILENT_MODE_FORCED_NON_SILENT, SILENT_MODE_NON_FORCED, PARAM_QUERY_MODE);
        pw.println("\t  Forces silent mode silent or non-silent. With query (or no command) "
                + "displays the silent state");
        pw.println("\t  and shows how many listeners are monitoring the state.");

        pw.printf("\t%s [%s|%s|%s]\n", COMMAND_EMULATE_DRIVING_STATE, DRIVING_STATE_DRIVE,
                DRIVING_STATE_PARK, DRIVING_STATE_REVERSE);
        pw.println("\t  Emulates the giving driving state.");

        pw.printf("\t%s <POLICY_ID> [--enable COMP1,COMP2,...] [--disable COMP1,COMP2,...]\n",
                COMMAND_DEFINE_POWER_POLICY);
        pw.println("\t  Defines a power policy. Components not specified in --enable or --disable");
        pw.println("\t  are unchanged when the policy is applied.");
        pw.println("\t  Components should be comma-separated without space.");

        pw.printf("\t%s <POLICY_ID>\n", COMMAND_APPLY_POWER_POLICY);
        pw.println("\t  Applies power policy which is defined in /vendor/etc/power_policy.xml or");
        pw.printf("\t  by %s command\n", COMMAND_DEFINE_POWER_POLICY);

        pw.printf("\t%s <POLICY_GROUP_ID> [%s:<POLICY_ID>] [%s:<POLICY_ID>]\n",
                COMMAND_DEFINE_POWER_POLICY_GROUP, POWER_STATE_WAIT_FOR_VHAL, POWER_STATE_ON);
        pw.println("\t  Defines a power policy group. The policy ID must be defined in advance.");

        pw.printf("\t%s <POLICY_GROUP_ID>\n", COMMAND_SET_POWER_POLICY_GROUP);
        pw.println("\t  Sets power policy group which is defined in /vendor/etc/power_policy.xml ");
        pw.printf("\t  or by %s command\n", COMMAND_DEFINE_POWER_POLICY_GROUP);

        pw.printf("\t%s\n", COMMAND_APPLY_CTS_VERIFIER_POWER_OFF_POLICY);
        pw.println("\t  Define and apply the cts_verifier_off power policy with "
                + "--disable WIFI,LOCATION,BLUETOOTH");

        pw.printf("\t%s\n", COMMAND_APPLY_CTS_VERIFIER_POWER_ON_POLICY);
        pw.println("\t  Define and apply the cts_verifier_on power policy with "
                + "--enable WIFI,LOCATION,BLUETOOTH");

        pw.printf("\t%s [%s] [%s]\n", COMMAND_POWER_OFF, PARAM_SKIP_GARAGEMODE, PARAM_REBOOT);
        pw.println("\t  Powers off the car.");

        pw.printf("\t%s <CAMERA_ID>\n", COMMAND_SET_REARVIEW_CAMERA_ID);
        pw.println("\t  Configures a target camera device CarEvsService to use.");
        pw.println("\t  If CAMEAR_ID is \"default\", this command will configure CarEvsService ");
        pw.println("\t  to use its default camera device.");

        pw.printf("\t%s\n", COMMAND_GET_REARVIEW_CAMERA_ID);
        pw.println("\t  Gets the name of the camera device CarEvsService is using for " +
                "the rearview.");

        pw.printf("\t%s true|false <PACKAGE_NAME>\n",
                COMMAND_WATCHDOG_CONTROL_PACKAGE_KILLABLE_STATE);
        pw.println("\t  Marks PACKAGE_NAME as killable or not killable on resource overuse ");

        pw.printf("\t%s <FOREGROUND_MODE_BYTES>\n", COMMAND_WATCHDOG_IO_SET_3P_FOREGROUND_BYTES);
        pw.println("\t  Sets third-party apps foreground I/O overuse threshold");

        pw.printf("\t%s\n", COMMAND_WATCHDOG_IO_GET_3P_FOREGROUND_BYTES);
        pw.println("\t  Gets third-party apps foreground I/O overuse threshold");

        pw.printf("\t%s enable|disable\n", COMMAND_WATCHDOG_CONTROL_PROCESS_HEALTH_CHECK);
        pw.println("\t  Enables/disables car watchdog process health check.");

        pw.printf("\t%s <PACKAGE_NAME>\n", COMMAND_WATCHDOG_RESOURCE_OVERUSE_KILL);
        pw.println("\t  Kills PACKAGE_NAME due to resource overuse.");

        pw.printf("\t%s [REGION_STRING]", COMMAND_DRIVING_SAFETY_SET_REGION);
        pw.println("\t  Set driving safety region.");
        pw.println("\t  Skipping REGION_STRING leads into resetting to all regions");

        pw.printf("\t%s <subcommand>", COMMAND_TELEMETRY);
        pw.println("\t  Telemetry commands.");
        pw.println("\t  Provide -h to see the list of sub-commands.");

        pw.printf("\t%s get|default|enable|disable_until_used <PACKAGE_NAME>\n",
                COMMAND_CONTROL_COMPONENT_ENABLED_STATE);
        pw.println("\t  Gets the current EnabledState, or changes the Application EnabledState"
                + " to DEFAULT, ENABLED or DISABLED_UNTIL_USED.");
        pw.printf("\t%s [user]\n", COMMAND_CHECK_LOCK_IS_SECURE);
        pw.println("\t  check if the current or given user has a lock to secure");
        pw.printf("\t%s", COMMAND_LIST_VHAL_PROPS);
        pw.println("\t  list all supported property IDS by vehicle HAL");
        pw.printf("\t%s", COMMAND_GET_VHAL_BACKEND);
        pw.println("\t  list whether we are connected to AIDL or HIDL vehicle HAL backend");
        pw.printf("\t%s <PROP_ID> <REQUEST_SIZE>", COMMAND_TEST_ECHO_REVERSE_BYTES);
        pw.println("\t  test the ECHO_REVERSE_BYTES property. PROP_ID is the ID (int) for "
                + "ECHO_REVERSE_BYTES, REQUEST_SIZE is how many byteValues in the request. "
                + "This command can be used for testing LargeParcelable by passing large request.");

        pw.printf("\t%s [--user USER] <APP1> [APPN]", COMMAND_GET_TARGET_CAR_VERSION);
        pw.println("\t  Gets the target API version (major and minor) defined by the given apps "
                + "for the given user (or current user when --user is not set).");
    }

    private static int showInvalidArguments(IndentingPrintWriter pw) {
        pw.println("Incorrect number of arguments.");
        showHelp(pw);
        return RESULT_ERROR;
    }

    private void runSetZoneIdForUid(String zoneString, String uidString) {
        int uid = Integer.parseInt(uidString);
        int zoneId = Integer.parseInt(zoneString);
        mCarAudioService.setZoneIdForUid(zoneId, uid);
    }

    private void runSetMuteCarVolumeGroup(String zoneString, String groupIdString,
            String muteString) {
        int groupId = Integer.parseInt(groupIdString);
        int zoneId = Integer.parseInt(zoneString);
        if (!PARAM_MUTE.equalsIgnoreCase(muteString)
                && !PARAM_UNMUTE.equalsIgnoreCase(muteString)) {
            throw new IllegalArgumentException("Failed to set volume group mute for "
                    + groupIdString + " in zone " + zoneString
                    + ", bad mute argument: " + muteString);
        }
        boolean muteState = PARAM_MUTE.equalsIgnoreCase(muteString);
        mCarAudioService.setVolumeGroupMute(zoneId, groupId, muteState, FLAG_SHOW_UI);
    }


    private void runSetGroupVolume(String zoneIdString, String groupIdString, String volumeString) {
        int groupId = Integer.parseInt(groupIdString);
        int zoneId = Integer.parseInt(zoneIdString);
        int percentVolume = Integer.parseInt(volumeString);
        Preconditions.checkArgumentInRange(percentVolume, 0, 100,
                "%volume for group " + groupIdString + " in zone " + zoneIdString);
        int minIndex = mCarAudioService.getGroupMinVolume(zoneId, groupId);
        int maxIndex = mCarAudioService.getGroupMaxVolume(zoneId, groupId);
        int index = minIndex
                + (int) ((float) (maxIndex - minIndex) * ((float) percentVolume / 100.0f));
        mCarAudioService.setGroupVolume(zoneId, groupId, index, FLAG_SHOW_UI);
    }

    private void runResetSelectedVolumeContext() {
        mCarAudioService.resetSelectedVolumeContext();
    }

    private void runSetOccupantZoneIdForUserId(String occupantZoneIdString,
            String userIdString) {
        int userId = Integer.parseInt(userIdString);
        int occupantZoneId = Integer.parseInt(occupantZoneIdString);
        if (!mCarOccupantZoneService.assignProfileUserToOccupantZone(occupantZoneId, userId)) {
            throw new IllegalStateException("Failed to set userId " + userId + " to occupantZoneId "
                    + occupantZoneIdString);
        }
    }

    private void runResetOccupantZoneId(String occupantZoneIdString) {
        int occupantZoneId = Integer.parseInt(occupantZoneIdString);
        if (!mCarOccupantZoneService
                .assignProfileUserToOccupantZone(occupantZoneId, UserManagerHelper.USER_NULL)) {
            throw new IllegalStateException("Failed to reset occupantZoneId "
                    + occupantZoneIdString);
        }
    }

    private void assertHasAtLeastOnePermission(String cmd, String[] requiredPermissions) {
        for (String requiredPermission : requiredPermissions) {
            if (CarServiceUtils.hasPermission(mContext, requiredPermission)) return;
        }
        if (requiredPermissions.length == 1) {
            throw new SecurityException("The command '" + cmd + "' requires permission:"
                    + requiredPermissions[0]);
        }
        throw new SecurityException(
                "The command " + cmd + " requires one of the following permissions:"
                        + Arrays.toString(requiredPermissions));
    }

    int exec(String[] args, IndentingPrintWriter writer) {
        String cmd = args[0];
        String[] requiredPermissions = USER_BUILD_COMMAND_TO_PERMISSIONS_MAP.get(cmd);
        if (requiredPermissions == null) {
            String requiredPermission = USER_BUILD_COMMAND_TO_PERMISSION_MAP.get(cmd);
            if (requiredPermission != null) {
                requiredPermissions = new String[] { requiredPermission };
            }

        }
        if (VERBOSE) {
            Slogf.v(TAG, "cmd: " + cmd + ", requiredPermissions: "
                    + Arrays.toString(requiredPermissions));
        }
        if (BuildHelper.isUserBuild() && requiredPermissions == null) {
            throw new SecurityException("The command '" + cmd + "' requires non-user build");
        }
        if (requiredPermissions != null) {
            assertHasAtLeastOnePermission(cmd, requiredPermissions);
        }

        switch (cmd) {
            case COMMAND_HELP:
                showHelp(writer);
                break;
            case COMMAND_DAY_NIGHT_MODE: {
                String value = args.length < 2 ? "" : args[1];
                forceDayNightMode(value, writer);
                break;
            }
            case COMMAND_GARAGE_MODE: {
                String value = args.length < 2 ? "" : args[1];
                forceGarageMode(value, writer);
                break;
            }
            case COMMAND_INJECT_VHAL_EVENT:
                String zone = PARAM_VEHICLE_PROPERTY_AREA_GLOBAL;
                String data;
                int argNum = args.length;
                if (argNum < 3 || argNum > 6) {
                    return showInvalidArguments(writer);
                }
                String delayTime = args[argNum - 2].equals("-t") ?  args[argNum - 1] : "0";
                if (argNum == 4 || argNum == 6) {
                    // Zoned
                    zone = args[2];
                    data = args[3];
                } else {
                    // Global
                    data = args[2];
                }
                injectVhalEvent(args[1], zone, data, false, delayTime, writer);
                break;
            case COMMAND_INJECT_CONTINUOUS_EVENT:
                injectContinuousEvents(args, writer);
                break;
            case COMMAND_INJECT_ERROR_EVENT:
                if (args.length != 4) {
                    return showInvalidArguments(writer);
                }
                String errorAreaId = args[2];
                String errorCode = args[3];
                injectVhalEvent(args[1], errorAreaId, errorCode, true, "0", writer);
                break;
            case COMMAND_ENABLE_UXR:
                if (args.length != 2) {
                    return showInvalidArguments(writer);
                }
                boolean enableBlocking = Boolean.valueOf(args[1]);
                if (mCarPackageManagerService != null) {
                    mCarPackageManagerService.setEnableActivityBlocking(enableBlocking);
                }
                break;
            case COMMAND_GET_DO_ACTIVITIES:
                if (args.length != 2) {
                    return showInvalidArguments(writer);
                }
                String pkgName = args[1].toLowerCase();
                if (mCarPackageManagerService != null) {
                    String[] doActivities =
                            mCarPackageManagerService.getDistractionOptimizedActivities(
                                    pkgName);
                    if (doActivities != null) {
                        writer.println("DO Activities for " + pkgName);
                        for (String a : doActivities) {
                            writer.println(a);
                        }
                    } else {
                        writer.println("No DO Activities for " + pkgName);
                    }
                }
                break;
            case COMMAND_GET_CARPROPERTYCONFIG:
                String propertyId = args.length < 2 ? PARAM_ALL_PROPERTIES_OR_AREA : args[1];
                mHal.dumpPropertyConfigs(writer, Integer.decode(propertyId));
                break;
            case COMMAND_GET_PROPERTY_VALUE:
                String propId = args.length < 2 ? PARAM_ALL_PROPERTIES_OR_AREA : args[1];
                String areaId = args.length < 3 ? PARAM_ALL_PROPERTIES_OR_AREA : args[2];
                mHal.dumpPropertyValueByCommand(writer, Integer.decode(propId),
                        Integer.decode(areaId));
                break;
            case COMMAND_SET_PROPERTY_VALUE:
                runSetVehiclePropertyValue(args, writer);
                break;
            case COMMAND_PROJECTION_UI_MODE:
                if (args.length != 2) {
                    return showInvalidArguments(writer);
                }
                mCarProjectionService.setUiMode(Integer.valueOf(args[1]));
                break;
            case COMMAND_PROJECTION_AP_TETHERING:
                if (args.length != 2) {
                    return showInvalidArguments(writer);
                }
                mCarProjectionService.setAccessPointTethering(Boolean.parseBoolean(args[1]));
                break;
            case COMMAND_PROJECTION_AP_STABLE_CONFIG:
                if (args.length != 2) {
                    return showInvalidArguments(writer);
                }
                mCarProjectionService.setStableLocalOnlyHotspotConfig(
                        Boolean.parseBoolean(args[1]));
                break;
            case COMMAND_RESUME:
                mCarPowerManagementService.forceSimulatedResume();
                writer.println("Resume: Simulating resuming from Deep Sleep");
                break;
            case COMMAND_SUSPEND:
                // fall-through
            case COMMAND_HIBERNATE:
                runSuspendCommand(args, writer);
                break;
            case COMMAND_SET_UID_TO_ZONE:
                if (args.length != 3) {
                    return showInvalidArguments(writer);
                }
                runSetZoneIdForUid(args[1], args[2]);
                break;
            case COMMAND_RESET_VOLUME_CONTEXT:
                if (args.length > 1) {
                    return showInvalidArguments(writer);
                }
                runResetSelectedVolumeContext();
                break;
            case COMMAND_SET_MUTE_CAR_VOLUME_GROUP:
                if (args.length != 4) {
                    return showInvalidArguments(writer);
                }
                runSetMuteCarVolumeGroup(args[1], args[2], args[3]);
                break;
            case COMMAND_SET_GROUP_VOLUME:
                if (args.length != 4) {
                    return showInvalidArguments(writer);
                }
                runSetGroupVolume(args[1], args[2], args[3]);
                break;
            case COMMAND_SET_USER_ID_TO_OCCUPANT_ZONE:
                if (args.length != 3) {
                    return showInvalidArguments(writer);
                }
                runSetOccupantZoneIdForUserId(args[1], args[2]);
                break;
            case COMMAND_SILENT_MODE: {
                String value = args.length < 2 ? ""
                        : args.length == 2 ? args[1] : "too many arguments";
                runSilentCommand(value, writer);
                break;
            }
            case COMMAND_RESET_USER_ID_IN_OCCUPANT_ZONE:
                if (args.length != 2) {
                    return showInvalidArguments(writer);
                }
                runResetOccupantZoneId(args[1]);
                break;
            case COMMAND_START_FIXED_ACTIVITY_MODE:
                startFixedActivity(args, writer);
                break;
            case COMMAND_STOP_FIXED_ACTIVITY_MODE:
                stopFixedMode(args, writer);
                break;
            case COMMAND_ENABLE_FEATURE:
                if (args.length != 2) {
                    return showInvalidArguments(writer);
                }
                enableDisableFeature(args, writer, /* enable= */ true);
                break;
            case COMMAND_DISABLE_FEATURE:
                if (args.length != 2) {
                    return showInvalidArguments(writer);
                }
                enableDisableFeature(args, writer, /* enable= */ false);
                break;
            case COMMAND_INJECT_KEY:
                if (args.length < 2) {
                    return showInvalidArguments(writer);
                }
                injectKey(args, writer);
                break;
            case COMMAND_INJECT_ROTARY:
                if (args.length < 1) {
                    return showInvalidArguments(writer);
                }
                injectRotary(args, writer);
                break;
            case COMMAND_INJECT_CUSTOM_INPUT:
                if (args.length < 2) {
                    return showInvalidArguments(writer);
                }
                injectCustomInputEvent(args, writer);
                break;
            case COMMAND_GET_INITIAL_USER_INFO:
                getInitialUserInfo(args, writer);
                break;
            case COMMAND_SWITCH_USER:
                switchUser(args, writer);
                break;
            case COMMAND_LOGOUT_USER:
                logoutUser(args, writer);
                break;
            case COMMAND_REMOVE_USER:
                removeUser(args, writer);
                break;
            case COMMAND_CREATE_USER:
                createUser(args, writer);
                break;
            case COMMAND_GET_INITIAL_USER:
                getInitialUser(writer);
                break;
            case COMMAND_GET_USER_AUTH_ASSOCIATION:
                getUserAuthAssociation(args, writer);
                break;
            case COMMAND_SET_USER_AUTH_ASSOCIATION:
                setUserAuthAssociation(args, writer);
                break;
            case COMMAND_SET_START_BG_USERS_ON_GARAGE_MODE:
                setStartBackgroundUsersOnGarageMode(args, writer);
                break;
            case COMMAND_EMULATE_DRIVING_STATE:
                emulateDrivingState(args, writer);
                break;
            case COMMAND_DEFINE_POWER_POLICY:
                return definePowerPolicy(args, writer);
            case COMMAND_APPLY_POWER_POLICY:
                return applyPowerPolicy(args, writer);
            case COMMAND_DEFINE_POWER_POLICY_GROUP:
                return definePowerPolicyGroup(args, writer);
            case COMMAND_SET_POWER_POLICY_GROUP:
                return setPowerPolicyGroup(args, writer);
            case COMMAND_APPLY_CTS_VERIFIER_POWER_OFF_POLICY:
                return applyCtsVerifierPowerOffPolicy(args, writer);
            case COMMAND_APPLY_CTS_VERIFIER_POWER_ON_POLICY:
                return applyCtsVerifierPowerOnPolicy(args, writer);
            case COMMAND_POWER_OFF:
                powerOff(args, writer);
                break;
            case COMMAND_SET_REARVIEW_CAMERA_ID:
                setRearviewCameraId(args, writer);
                break;
            case COMMAND_GET_REARVIEW_CAMERA_ID:
                getRearviewCameraId(writer);
                break;
            case COMMAND_WATCHDOG_CONTROL_PACKAGE_KILLABLE_STATE:
                controlWatchdogPackageKillableState(args, writer);
                break;
            case COMMAND_WATCHDOG_IO_SET_3P_FOREGROUND_BYTES:
                setWatchdogIoThirdPartyForegroundBytes(args, writer);
                break;
            case COMMAND_WATCHDOG_IO_GET_3P_FOREGROUND_BYTES:
                getWatchdogIoThirdPartyForegroundBytes(writer);
                break;
            case COMMAND_WATCHDOG_CONTROL_PROCESS_HEALTH_CHECK:
                controlWatchdogProcessHealthCheck(args, writer);
                break;
            case COMMAND_WATCHDOG_RESOURCE_OVERUSE_KILL:
                performResourceOveruseKill(args, writer);
                break;
            case COMMAND_DRIVING_SAFETY_SET_REGION:
                setDrivingSafetyRegion(args, writer);
                break;
            case COMMAND_TELEMETRY:
                handleTelemetryCommands(args, writer);
                break;
            case COMMAND_CONTROL_COMPONENT_ENABLED_STATE:
                controlComponentEnabledState(args, writer);
                break;
            case COMMAND_CHECK_LOCK_IS_SECURE:
                checkLockIsSecure(args, writer);
                break;
            case COMMAND_LIST_VHAL_PROPS:
                listVhalProps(writer);
                break;
            case COMMAND_GET_VHAL_BACKEND:
                getVhalBackend(writer);
                break;
            case COMMAND_TEST_ECHO_REVERSE_BYTES:
                testEchoReverseBytes(args, writer);
                break;
            case COMMAND_GET_TARGET_CAR_VERSION:
                getTargetCarVersion(args, writer);
                break;
            default:
                writer.println("Unknown command: \"" + cmd + "\"");
                showHelp(writer);
                return RESULT_ERROR;
        }
        return RESULT_OK;
    }

    private void setStartBackgroundUsersOnGarageMode(String[] args, IndentingPrintWriter writer) {
        if (args.length < 2) {
            writer.println("Insufficient number of args");
            return;
        }

        boolean enabled = Boolean.parseBoolean(args[1]);
        Slogf.d(TAG, "setStartBackgroundUsersOnGarageMode(): "
                + (enabled ? "enabled" : "disabled"));
        mCarUserService.setStartBackgroundUsersOnGarageMode(enabled);
        writer.printf("StartBackgroundUsersOnGarageMode set to %b\n", enabled);
    }

    private void startFixedActivity(String[] args, IndentingPrintWriter writer) {
        if (args.length != 4) {
            writer.println("Incorrect number of arguments");
            showHelp(writer);
            return;
        }
        int displayId;
        try {
            displayId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            writer.println("Wrong display id:" + args[1]);
            return;
        }
        String packageName = args[2];
        String activityName = args[3];
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(packageName, activityName));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(displayId);
        if (!mFixedActivityService.startFixedActivityModeForDisplayAndUser(intent, options,
                displayId, ActivityManager.getCurrentUser())) {
            writer.println("Failed to start");
            return;
        }
        writer.println("Succeeded");
    }

    private void stopFixedMode(String[] args, IndentingPrintWriter writer) {
        if (args.length != 2) {
            writer.println("Incorrect number of arguments");
            showHelp(writer);
            return;
        }
        int displayId;
        try {
            displayId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            writer.println("Wrong display id:" + args[1]);
            return;
        }
        mFixedActivityService.stopFixedActivityMode(displayId);
    }

    private void enableDisableFeature(String[] args, IndentingPrintWriter writer, boolean enable) {
        if (Binder.getCallingUid() != Process.ROOT_UID) {
            writer.println("Only allowed to root/su");
            return;
        }
        String featureName = args[1];
        long id = Binder.clearCallingIdentity();
        // no permission check here
        int r;
        if (enable) {
            r = mFeatureController.enableFeature(featureName);
        } else {
            r = mFeatureController.disableFeature(featureName);
        }
        switch (r) {
            case Car.FEATURE_REQUEST_SUCCESS:
                if (enable) {
                    writer.println("Enabled feature:" + featureName);
                } else {
                    writer.println("Disabled feature:" + featureName);
                }
                break;
            case Car.FEATURE_REQUEST_ALREADY_IN_THE_STATE:
                if (enable) {
                    writer.println("Already enabled:" + featureName);
                } else {
                    writer.println("Already disabled:" + featureName);
                }
                break;
            case Car.FEATURE_REQUEST_MANDATORY:
                writer.println("Cannot change mandatory feature:" + featureName);
                break;
            case Car.FEATURE_REQUEST_NOT_EXISTING:
                writer.println("Non-existing feature:" + featureName);
                break;
            default:
                writer.println("Unknown error:" + r);
                break;
        }
        Binder.restoreCallingIdentity(id);
    }

    private void injectKey(String[] args, IndentingPrintWriter writer) {
        int i = 1; // 0 is command itself
        int display = CarOccupantZoneManager.DISPLAY_TYPE_MAIN;
        int delayMs = 0;
        int keyCode = KeyEvent.KEYCODE_UNKNOWN;
        int action = -1;
        try {
            while (i < args.length) {
                switch (args[i]) {
                    case "-d":
                        i++;
                        int vehicleDisplay = Integer.parseInt(args[i]);
                        if (!checkVehicleDisplay(vehicleDisplay, writer)) {
                            return;
                        }
                        display = InputHalService.convertDisplayType(vehicleDisplay);
                        break;
                    case "-t":
                        i++;
                        delayMs = Integer.parseInt(args[i]);
                        break;
                    case "-a":
                        i++;
                        if (args[i].equalsIgnoreCase("down")) {
                            action = KeyEvent.ACTION_DOWN;
                        } else if (args[i].equalsIgnoreCase("up")) {
                            action = KeyEvent.ACTION_UP;
                        } else {
                            throw new IllegalArgumentException("Invalid action: " + args[i]);
                        }
                        break;
                    default:
                        if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
                            throw new IllegalArgumentException("key_code already set:"
                                    + keyCode);
                        }
                        keyCode = Integer.parseInt(args[i]);
                }
                i++;
            }
        } catch (NumberFormatException e) {
            writer.println("Invalid args:" + e);
            showHelp(writer);
            return;
        }
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            writer.println("Missing key code or invalid keycode");
            showHelp(writer);
            return;
        }
        if (delayMs < 0) {
            writer.println("Invalid delay:" + delayMs);
            showHelp(writer);

            return;
        }
        if (action == -1) {
            injectKeyEvent(KeyEvent.ACTION_DOWN, keyCode, display);
            SystemClock.sleep(delayMs);
            injectKeyEvent(KeyEvent.ACTION_UP, keyCode, display);
        } else {
            injectKeyEvent(action, keyCode, display);
        }
        writer.println("Succeeded");
    }

    private void injectKeyEvent(int action, int keyCode, int display) {
        long currentTime = SystemClock.uptimeMillis();
        if (action == KeyEvent.ACTION_DOWN) mKeyDownTime = currentTime;
        long token = Binder.clearCallingIdentity();
        try {
            mCarInputService.injectKeyEvent(
                    new KeyEvent(/* downTime= */ mKeyDownTime, /* eventTime= */ currentTime,
                            action, keyCode, /* repeat= */ 0), display);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private void injectRotary(String[] args, IndentingPrintWriter writer) {
        int i = 1; // 0 is command itself
        int display = CarOccupantZoneManager.DISPLAY_TYPE_MAIN;
        int inputType = CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION;
        boolean clockwise = false;
        List<Long> deltaTimeMs = new ArrayList<>();
        try {
            while (i < args.length) {
                switch (args[i]) {
                    case "-d":
                        i++;
                        int vehicleDisplay = Integer.parseInt(args[i]);
                        if (!checkVehicleDisplay(vehicleDisplay, writer)) {
                            return;
                        }
                        display = InputHalService.convertDisplayType(vehicleDisplay);
                        break;
                    case "-i":
                        i++;
                        inputType = Integer.parseInt(args[i]);
                        break;
                    case "-c":
                        i++;
                        clockwise = Boolean.parseBoolean(args[i]);
                        break;
                    case "-dt":
                        i++;
                        while (i < args.length) {
                            deltaTimeMs.add(Long.parseLong(args[i]));
                            i++;
                        }
                        break;
                    default:
                        writer.println("Invalid option at index " + i + ": " + args[i]);
                        return;
                }
                i++;
            }
        } catch (NumberFormatException e) {
            writer.println("Invalid args:" + e);
            showHelp(writer);
            return;
        }
        if (deltaTimeMs.isEmpty()) {
            deltaTimeMs.add(0L);
        }
        for (int j = 0; j < deltaTimeMs.size(); j++) {
            if (deltaTimeMs.get(j) < 0) {
                writer.println("Delta time shouldn't be negative: " + deltaTimeMs.get(j));
                showHelp(writer);
                return;
            }
            if (j > 0 && deltaTimeMs.get(j) > deltaTimeMs.get(j - 1)) {
                writer.println("Delta times should be in descending order");
                showHelp(writer);
                return;
            }
        }
        long[] uptimeMs = new long[deltaTimeMs.size()];
        long currentUptime = SystemClock.uptimeMillis();
        for (int j = 0; j < deltaTimeMs.size(); j++) {
            uptimeMs[j] = currentUptime - deltaTimeMs.get(j);
        }
        RotaryEvent rotaryEvent = new RotaryEvent(inputType, clockwise, uptimeMs);
        mCarInputService.onRotaryEvent(rotaryEvent, display);
        writer.println("Succeeded in injecting: " + rotaryEvent);
    }

    private void injectCustomInputEvent(String[] args, IndentingPrintWriter writer) {
        int display = CarOccupantZoneManager.DISPLAY_TYPE_MAIN;
        int repeatCounter = 1;

        int argIdx = 1;
        for (; argIdx < args.length - 1; argIdx++) {
            switch (args[argIdx]) {
                case "-d":
                    int vehicleDisplay = Integer.parseInt(args[++argIdx]);
                    if (!checkVehicleDisplay(vehicleDisplay, writer)) {
                        return;
                    }
                    display = InputHalService.convertDisplayType(vehicleDisplay);
                    break;
                case "-r":
                    repeatCounter = Integer.parseInt(args[++argIdx]);
                    break;
                default:
                    writer.printf("Unrecognized argument: {%s}\n", args[argIdx]);
                    writer.println("Pass -help to see the full list of options");
                    return;
            }
        }

        if (argIdx == args.length) {
            writer.println("Last mandatory argument (fn) not passed.");
            writer.println("Pass -help to see the full list of options");
            return;
        }

        // Processing the last remaining argument. Argument is expected one of the tem functions
        // ('f1', 'f2', ..., 'f10') or just a plain integer representing the custom input event.
        String eventValue = args[argIdx].toLowerCase();
        Integer inputCode;
        if (eventValue.startsWith("f")) {
            inputCode = CUSTOM_INPUT_FUNCTION_ARGS.get(eventValue);
            if (inputCode == null) {
                writer.printf("Invalid input event value {%s}, valid values are f1, f2, ..., f10\n",
                        eventValue);
                writer.println("Pass -help to see the full list of options");
                return;
            }
        } else {
            inputCode = Integer.parseInt(eventValue);
        }
        CustomInputEvent event = new CustomInputEvent(inputCode, display, repeatCounter);
        mCarInputService.onCustomInputEvent(event);
        writer.printf("Succeeded in injecting {%s}\n", event);
    }

    private boolean checkVehicleDisplay(int vehicleDisplay, IndentingPrintWriter writer) {
        if (vehicleDisplay != VehicleDisplay.MAIN
                && vehicleDisplay != VehicleDisplay.INSTRUMENT_CLUSTER) {
            writer.println("Invalid display:" + vehicleDisplay);
            showHelp(writer);
            return false;
        }
        return true;
    }

    private void getInitialUserInfo(String[] args, IndentingPrintWriter writer) {
        if (args.length < 2) {
            writer.println("Insufficient number of args");
            return;
        }

        // Gets the request type
        String typeArg = args[1];
        int requestType = UserHalHelper.parseInitialUserInfoRequestType(typeArg);

        int timeout = DEFAULT_HAL_TIMEOUT_MS;
        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--timeout":
                    timeout = Integer.parseInt(args[++i]);
                    break;
                default:
                    writer.println("Invalid option at index " + i + ": " + arg);
                    return;

            }
        }

        Slogf.d(TAG, "handleGetInitialUserInfo(): type=" + requestType + " (" + typeArg
                + "), timeout=" + timeout);

        CountDownLatch latch = new CountDownLatch(1);
        HalCallback<InitialUserInfoResponse> callback = (status, resp) -> {
            try {
                Slogf.d(TAG, "GetUserInfoResponse: status=" + status + ", resp=" + resp);
                writer.printf("Call status: %s\n",
                        UserHalHelper.halCallbackStatusToString(status));
                if (status != HalCallback.STATUS_OK) {
                    return;
                }
                writer.printf("Request id: %d\n", resp.requestId);
                writer.printf("Action: %s\n", DebugUtils.constantToString(
                        InitialUserInfoResponseAction.class, resp.action));
                if (!TextUtils.isEmpty(resp.userNameToCreate)) {
                    writer.printf("User name: %s\n", resp.userNameToCreate);
                }
                if (resp.userToSwitchOrCreate.userId != UserManagerHelper.USER_NULL) {
                    writer.printf("User id: %d\n", resp.userToSwitchOrCreate.userId);
                }
                if (resp.userToSwitchOrCreate.flags != 0) {
                    writer.printf("User flags: %s\n",
                            UserHalHelper.userFlagsToString(resp.userToSwitchOrCreate.flags));
                }
                if (!TextUtils.isEmpty(resp.userLocales)) {
                    writer.printf("User locales: %s\n", resp.userLocales);
                }
            } finally {
                latch.countDown();
            }
        };

        UsersInfo usersInfo = generateUsersInfo();
        mHal.getUserHal().getInitialUserInfo(requestType, timeout, usersInfo, callback);
        waitForHal(writer, latch, timeout);
    }

    private UsersInfo generateUsersInfo() {
        UserManager um = mContext.getSystemService(UserManager.class);
        UserHandleHelper userHandleHelper = new UserHandleHelper(mContext, um);
        return UserHalHelper.newUsersInfo(um, userHandleHelper);
    }

    private int getUserHalFlags(@UserIdInt int userId) {
        UserManager um = mContext.getSystemService(UserManager.class);
        UserHandleHelper userHandleHelper = new UserHandleHelper(mContext, um);
        return UserHalHelper.getFlags(userHandleHelper, userId);
    }

    private static void waitForHal(IndentingPrintWriter writer, CountDownLatch latch,
            int timeoutMs) {
        try {
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                writer.printf("HAL didn't respond in %dms\n", timeoutMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            writer.println("Interrupted waiting for HAL");
        }
        return;
    }

    private void switchUser(String[] args, IndentingPrintWriter writer) {
        if (args.length < 2) {
            writer.println("Insufficient number of args");
            return;
        }

        int targetUserId = Integer.parseInt(args[1]);
        int timeout = DEFAULT_HAL_TIMEOUT_MS + DEFAULT_CAR_USER_SERVICE_TIMEOUT_MS;
        boolean halOnly = false;

        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--timeout":
                    timeout = Integer.parseInt(args[++i]);
                    break;
                case "--hal-only":
                    halOnly = true;
                    break;
                default:
                    writer.println("Invalid option at index " + i + ": " + arg);
                    return;
            }
        }

        Slogf.d(TAG, "switchUser(): target=" + targetUserId + ", halOnly=" + halOnly
                + ", timeout=" + timeout);

        if (halOnly) {
            CountDownLatch latch = new CountDownLatch(1);
            UserHalService userHal = mHal.getUserHal();
            UserInfo targetUserInfo = new UserInfo();
            targetUserInfo.userId = targetUserId;
            targetUserInfo.flags = getUserHalFlags(targetUserId);

            SwitchUserRequest request = UserHalHelper.emptySwitchUserRequest();
            request.targetUser = targetUserInfo;
            request.usersInfo = generateUsersInfo();

            userHal.switchUser(request, timeout, (status, resp) -> {
                try {
                    Slogf.d(TAG, "SwitchUserResponse: status=" + status + ", resp=" + resp);
                    writer.printf("Call Status: %s\n",
                            UserHalHelper.halCallbackStatusToString(status));
                    if (status != HalCallback.STATUS_OK) {
                        return;
                    }
                    writer.printf("Request id: %d\n", resp.requestId);
                    writer.printf("Message type: %s\n", DebugUtils.constantToString(
                            SwitchUserMessageType.class, resp.messageType));
                    writer.printf("Switch Status: %s\n", DebugUtils.constantToString(
                            SwitchUserStatus.class, resp.status));
                    String errorMessage = resp.errorMessage;
                    if (!TextUtils.isEmpty(errorMessage)) {
                        writer.printf("Error message: %s", errorMessage);
                    }
                    // If HAL returned OK, make a "post-switch" call to the HAL indicating an
                    // Android error. This is to "rollback" the HAL switch.
                    if (status == HalCallback.STATUS_OK
                            && resp.status == SwitchUserStatus.SUCCESS) {
                        userHal.postSwitchResponse(request);
                    }
                } finally {
                    latch.countDown();
                }
            });
            waitForHal(writer, latch, timeout);
            return;
        }
        CarUserManager carUserManager = getCarUserManager(mContext);
        AsyncFuture<UserSwitchResult> future = carUserManager.switchUser(targetUserId);

        showUserSwitchResult(writer, future, timeout);
    }

    private void showUserSwitchResult(IndentingPrintWriter writer,
            AsyncFuture<UserSwitchResult> future, int timeout) {
        UserSwitchResult result = waitForFuture(writer, future, timeout);
        if (result == null) return;
        writer.printf("UserSwitchResult: status=%s",
                UserSwitchResult.statusToString(result.getStatus()));
        String msg = result.getErrorMessage();
        if (!TextUtils.isEmpty(msg)) {
            writer.printf(", errorMessage=%s", msg);
        }
        writer.println();
    }

    private void logoutUser(String[] args, IndentingPrintWriter writer) {
        int timeout = DEFAULT_HAL_TIMEOUT_MS + DEFAULT_CAR_USER_SERVICE_TIMEOUT_MS;

        if (args.length > 1) {
            for (int i = 1; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--timeout":
                        timeout = Integer.parseInt(args[++i]);
                        break;
                    default:
                        writer.println("Invalid option at index " + i + ": " + arg);
                        return;
                }
            }
        }

        Slogf.d(TAG, "logoutUser(): timeout=%d", timeout);

        CarUserManager carUserManager = getCarUserManager(mContext);
        AsyncFuture<UserSwitchResult> future = carUserManager.logoutUser();
        showUserSwitchResult(writer, future, timeout);
    }

    private void createUser(String[] args, IndentingPrintWriter writer) {
        int timeout = DEFAULT_HAL_TIMEOUT_MS + DEFAULT_CAR_USER_SERVICE_TIMEOUT_MS;
        int flags = 0;
        boolean isGuest = false;
        boolean halOnly = false;
        String name = null;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--timeout":
                    timeout = Integer.parseInt(args[++i]);
                    break;
                case "--guest":
                    isGuest = true;
                    break;
                case "--hal-only":
                    halOnly = true;
                    break;
                case "--flags":
                    flags = Integer.parseInt(args[++i]);
                    break;
                case "--type":
                    // print an error and quit.
                    writer.printf("Error: --type is not supported. Use --guest to create a guest.");
                    writer.println();
                    return;
                default:
                    if (name != null) {
                        writer.println("Invalid option at index " + i + ": " + arg);
                        return;
                    }
                    name = arg;
            }
        }

        Slogf.d(TAG, "createUser(): name=" + name
                + ", flags=" + flags
                + ", guest=" + isGuest + ", halOnly=" + halOnly + ", timeout=" + timeout);

        if (!halOnly) {
            CarUserManager carUserManager = getCarUserManager(mContext);
            AsyncFuture<UserCreationResult> future = isGuest
                    ? carUserManager.createGuest(name)
                    : carUserManager.createUser(name, flags);

            UserCreationResult result = waitForFuture(writer, future, timeout);
            if (result == null) return;

            UserHandle user = result.getUser();
            // NOTE: must always show the id=%d, as it's used by CTS tests
            writer.printf("UserCreationResult: status=%s, user=%s, id=%d",
                    UserCreationResult.statusToString(result.getStatus()),
                    user == null ? "N/A" : user.toString(),
                    user == null ? UserManagerHelper.USER_NULL : user.getIdentifier());
            Integer androidFailureStatus = result.getAndroidFailureStatus();
            if (androidFailureStatus != null) {
                writer.printf(", androidStatus=%s", DebugUtils.constantToString(UserManager.class,
                        "USER_OPERATION_", androidFailureStatus));
            }
            String msg = result.getErrorMessage();
            if (!TextUtils.isEmpty(msg)) {
                writer.printf(", errorMessage=%s", msg);
            }
            String internalMsg = result.getInternalErrorMessage();
            if (!TextUtils.isEmpty(internalMsg)) {
                writer.printf(", internalErrorMessage=%s", internalMsg);
            }
            writer.println();
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        UserHalService userHal = mHal.getUserHal();

        CreateUserRequest request = UserHalHelper.emptyCreateUserRequest();

        UserManager um = mContext.getSystemService(UserManager.class);

        NewUserRequest newUserRequest;
        try {
            newUserRequest = getCreateUserRequest(name, isGuest, flags);
        } catch (Exception e) {
            Slogf.e(TAG, e, "Error creating new user request. name: %s isGuest: %b and flags: %s",
                    name, isGuest, flags);
            writer.println("Failed to create user");
            return;
        }

        NewUserResponse newUserResponse = um.createUser(newUserRequest);

        if (!newUserResponse.isSuccessful()) {
            writer.printf("Failed to create user");
            return;
        }

        UserHandle newUser = newUserResponse.getUser();

        writer.printf("New user: %s\n", newUser);
        Slogf.i(TAG, "Created new user: " + newUser);

        request.newUserInfo.userId = newUser.getIdentifier();
        request.newUserInfo.flags = UserHalHelper.convertFlags(new UserHandleHelper(mContext, um),
                newUser);

        request.usersInfo = generateUsersInfo();

        AtomicBoolean halOk = new AtomicBoolean(false);
        try {
            userHal.createUser(request, timeout, (status, resp) -> {
                Slogf.d(TAG, "CreateUserResponse: status=" + status + ", resp=" + resp);
                writer.printf("Call Status: %s\n",
                        UserHalHelper.halCallbackStatusToString(status));
                if (status == HalCallback.STATUS_OK) {
                    halOk.set(resp.status == CreateUserStatus.SUCCESS);
                    writer.printf("Request id: %d\n", resp.requestId);
                    writer.printf("Create Status: %s\n", DebugUtils.constantToString(
                            CreateUserStatus.class, resp.status));
                    String errorMessage = resp.errorMessage;
                    if (!TextUtils.isEmpty(errorMessage)) {
                        writer.printf("Error message: %s", errorMessage);
                    }
                }
                latch.countDown();
            });
            waitForHal(writer, latch, timeout);
        } catch (RuntimeException e) {
            writer.printf("HAL failed: %s\n", e);
        } finally {
            if (!halOk.get()) {
                writer.printf("Removing user %d due to HAL failure\n", newUser.getIdentifier());
                boolean removed = um.removeUser(newUser);
                writer.printf("User removed: %b\n", removed);
            }
        }
    }

    private NewUserRequest getCreateUserRequest(String name, boolean isGuest, int flags) {
        NewUserRequest.Builder builder = new NewUserRequest.Builder().setName(name);
        if ((flags & UserManagerHelper.FLAG_ADMIN) == UserManagerHelper.FLAG_ADMIN) {
            builder.setAdmin();
        }

        if ((flags & UserManagerHelper.FLAG_EPHEMERAL) == UserManagerHelper.FLAG_EPHEMERAL) {
            builder.setEphemeral();
        }

        if (isGuest) {
            builder.setUserType(UserManager.USER_TYPE_FULL_GUEST);
        }

        return builder.build();
    }

    private void removeUser(String[] args, IndentingPrintWriter writer) {
        if (args.length < 2) {
            writer.println("Insufficient number of args");
            return;
        }

        int userId = Integer.parseInt(args[1]);
        boolean halOnly = false;

        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--hal-only":
                    halOnly = true;
                    break;
                default:
                    writer.println("Invalid option at index " + i + ": " + arg);
                    return;
            }
        }

        Slogf.d(TAG, "handleRemoveUser(): User to remove=" + userId + ", halOnly=" + halOnly);

        if (halOnly) {
            UserHalService userHal = mHal.getUserHal();
            UsersInfo usersInfo = generateUsersInfo();
            UserInfo userInfo = new UserInfo();
            userInfo.userId = userId;
            userInfo.flags = getUserHalFlags(userId);

            RemoveUserRequest request = UserHalHelper.emptyRemoveUserRequest();
            request.removedUserInfo = userInfo;
            request.usersInfo = usersInfo;

            userHal.removeUser(request);
            writer.printf("User removal sent for HAL only.\n");
            return;
        }

        CarUserManager carUserManager = getCarUserManager(mContext);
        UserRemovalResult result = carUserManager.removeUser(userId);
        writer.printf("UserRemovalResult: status = %s\n",
                UserRemovalResult.statusToString(result.getStatus()));
    }

    private static <T> T waitForFuture(IndentingPrintWriter writer,
            AsyncFuture<T> future, int timeoutMs) {
        T result = null;
        try {
            result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            if (result == null) {
                writer.printf("Service didn't respond in %d ms", timeoutMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            writer.printf("Exception getting future: %s",  e);
        }
        return result;
    }

    private void getInitialUser(IndentingPrintWriter writer) {
        UserHandle user = mCarUserService.getInitialUser();
        writer.println(user == null ? NO_INITIAL_USER : user.getIdentifier());
    }

    private void getUserAuthAssociation(String[] args, IndentingPrintWriter writer) {
        if (args.length < 2) {
            writer.println("invalid usage, must pass at least 1 argument");
            return;
        }

        boolean halOnly = false;
        int userId = UserHandle.CURRENT.getIdentifier();

        UserIdentificationGetRequest request = UserHalHelper.emptyUserIdentificationGetRequest();
        ArrayList<Integer> associationTypes = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--user":
                    try {
                        userId = Integer.parseInt(args[++i]);
                    } catch (NumberFormatException e) {
                        writer.printf("Invalid user id at index %d (from %s): %s\n", i + 1,
                                Arrays.toString(args), arg);
                    }
                    break;
                case "--hal-only":
                    halOnly = true;
                    break;
                default:
                    int type = parseAuthArg(VALID_USER_AUTH_TYPES, arg);
                    if (type == INVALID_USER_AUTH_TYPE_OR_VALUE) {
                        writer.printf("Invalid type at index %d (from %s): %s. %s\n", i + 1,
                                Arrays.toString(args), arg, VALID_USER_AUTH_TYPES_HELP);
                        return;
                    }
                    associationTypes.add(type);
            }
        }
        request.associationTypes = toIntArray(associationTypes);
        if (userId == UserHandle.CURRENT.getIdentifier()) {
            userId = ActivityManager.getCurrentUser();
        }
        int requestSize = request.associationTypes.length;
        if (halOnly) {
            request.numberAssociationTypes = requestSize;
            request.userInfo.userId = userId;
            request.userInfo.flags = getUserHalFlags(userId);

            Slogf.d(TAG, "getUserAuthAssociation(): user=" + userId + ", halOnly=" + halOnly
                    + ", request=" + request);
            UserIdentificationResponse response = mHal.getUserHal().getUserAssociation(request);
            Slogf.d(TAG, "getUserAuthAssociation(): response=" + response);
            showResponse(writer, response);
            return;
        }

        CarUserManager carUserManager = getCarUserManager(writer, userId);
        int[] types = new int[requestSize];
        for (int i = 0; i < requestSize; i++) {
            types[i] = request.associationTypes[i];
        }
        UserIdentificationAssociationResponse response = carUserManager
                .getUserIdentificationAssociation(types);
        showResponse(writer, response);
    }

    private CarUserManager getCarUserManager(IndentingPrintWriter writer,
            @UserIdInt int userId) {
        Context context = getContextForUser(userId);
        int actualUserId = Binder.getCallingUid();
        if (actualUserId != userId) {
            writer.printf("Emulating call for user id %d, but caller's user id is %d, so that's "
                    + "what CarUserService will use when calling HAL.\n", userId, actualUserId);
        }

        return getCarUserManager(context);
    }

    private Context getContextForUser(int userId) {
        if (userId == mContext.getUser().getIdentifier()) {
            return mContext;
        }
        return mContext.createContextAsUser(UserHandle.of(userId), /* flags= */ 0);
    }

    private CarUserManager getCarUserManager(Context context) {
        Car car = Car.createCar(context);
        CarUserManager carUserManager = (CarUserManager) car.getCarManager(Car.CAR_USER_SERVICE);
        return carUserManager;
    }

    private void showResponse(
            IndentingPrintWriter writer, UserIdentificationResponse response) {
        if (response == null) {
            writer.println("null response");
            return;
        }

        if (!TextUtils.isEmpty(response.errorMessage)) {
            writer.printf("Error message: %s\n", response.errorMessage);
        }
        int numberAssociations = response.associations.length;
        writer.printf("%d associations:\n", numberAssociations);
        for (int i = 0; i < numberAssociations; i++) {
            UserIdentificationAssociation association = response.associations[i];
            writer.printf("  %s\n", association);
        }
    }

    private void showResponse(IndentingPrintWriter writer,
            UserIdentificationAssociationResponse response) {
        if (response == null) {
            writer.println("null response");
            return;
        }
        if (!response.isSuccess()) {
            writer.printf("failed response: %s\n", response);
            return;
        }
        String errorMessage = response.getErrorMessage();
        if (!TextUtils.isEmpty(errorMessage)) {
            writer.printf("Error message: %s\n", errorMessage);
        }
        int[] values = response.getValues();
        if (values == null) {
            writer.printf("no associations on %s\n", response);
            return;
        }
        writer.printf("%d associations:\n", values.length);
        for (int i = 0; i < values.length; i++) {
            writer.printf("  %s\n", DebugUtils.constantToString(
                    UserIdentificationAssociationValue.class, values[i]));
        }
    }

    private void setUserAuthAssociation(String[] args, IndentingPrintWriter writer) {
        if (args.length < 3) {
            writer.println("invalid usage, must pass at least 4 arguments");
            return;
        }

        boolean halOnly = false;
        int timeout = DEFAULT_HAL_TIMEOUT_MS;
        int userId = UserHandle.CURRENT.getIdentifier();

        UserIdentificationSetRequest request = UserHalHelper.emptyUserIdentificationSetRequest();
        ArrayList<UserIdentificationSetAssociation> associations = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--user":
                    try {
                        userId = Integer.parseInt(args[++i]);
                    } catch (NumberFormatException e) {
                        writer.printf("Invalid user id at index %d (from %s): %s\n", i + 1,
                                Arrays.toString(args), arg);
                    }
                    break;
                case "--hal-only":
                    halOnly = true;
                    break;
                case "--timeout":
                    timeout = Integer.parseInt(args[++i]);
                    break;
                default:
                    UserIdentificationSetAssociation association =
                            new UserIdentificationSetAssociation();
                    association.type = parseAuthArg(VALID_USER_AUTH_TYPES, arg);
                    if (association.type == INVALID_USER_AUTH_TYPE_OR_VALUE) {
                        writer.printf("Invalid type at index %d (from %s): %s. %s\n", i + 1,
                                Arrays.toString(args), arg, VALID_USER_AUTH_TYPES_HELP);
                        return;
                    }
                    association.value = parseAuthArg(VALID_USER_AUTH_SET_VALUES, args[++i]);
                    if (association.value == INVALID_USER_AUTH_TYPE_OR_VALUE) {
                        writer.printf("Invalid value at index %d (from %s): %s. %s\n", i + 1,
                                Arrays.toString(args), arg, VALID_USER_AUTH_SET_VALUES_HELP);
                        return;
                    }
                    associations.add(association);
            }
        }
        int requestSize = associations.size();
        request.associations = associations.toArray(
                new UserIdentificationSetAssociation[requestSize]);

        if (userId == UserHandle.CURRENT.getIdentifier()) {
            userId = ActivityManager.getCurrentUser();
        }
        if (halOnly) {
            request.numberAssociations = requestSize;
            request.userInfo.userId = userId;
            request.userInfo.flags = getUserHalFlags(userId);

            Slogf.d(TAG, "setUserAuthAssociation(): user=" + userId + ", halOnly=" + halOnly
                    + ", request=" + request);
            CountDownLatch latch = new CountDownLatch(1);
            mHal.getUserHal().setUserAssociation(timeout, request, (status, response) -> {
                Slogf.d(TAG, "setUserAuthAssociation(): response=" + response);
                try {
                    showResponse(writer, response);
                } finally {
                    latch.countDown();
                }
            });
            waitForHal(writer, latch, timeout);
            return;
        }
        CarUserManager carUserManager = getCarUserManager(writer, userId);
        int[] types = new int[requestSize];
        int[] values = new int[requestSize];
        for (int i = 0; i < requestSize; i++) {
            UserIdentificationSetAssociation association = request.associations[i];
            types[i] = association.type;
            values[i] = association.value;
        }
        AsyncFuture<UserIdentificationAssociationResponse> future = carUserManager
                .setUserIdentificationAssociation(types, values);
        UserIdentificationAssociationResponse response = waitForFuture(writer, future, timeout);
        if (response != null) {
            showResponse(writer, response);
        }
    }

    private static int parseAuthArg(SparseArray<String> types, String type) {
        for (int i = 0; i < types.size(); i++) {
            if (types.valueAt(i).equals(type)) {
                return types.keyAt(i);
            }
        }
        return INVALID_USER_AUTH_TYPE_OR_VALUE;
    }

    private void forceDayNightMode(String arg, IndentingPrintWriter writer) {
        int mode;
        switch (arg) {
            case PARAM_DAY_MODE:
                mode = CarNightService.FORCED_DAY_MODE;
                break;
            case PARAM_NIGHT_MODE:
                mode = CarNightService.FORCED_NIGHT_MODE;
                break;
            case PARAM_SENSOR_MODE:
                mode = CarNightService.FORCED_SENSOR_MODE;
                break;
            default:
                writer.printf("Unknown value: %s. Valid argument: %s|%s|%s\n",
                        arg, PARAM_DAY_MODE, PARAM_NIGHT_MODE, PARAM_SENSOR_MODE);
                return;
        }
        int current = mCarNightService.forceDayNightMode(mode);
        String currentMode = null;
        switch (current) {
            case UiModeManager.MODE_NIGHT_AUTO:
                currentMode = PARAM_SENSOR_MODE;
                break;
            case UiModeManager.MODE_NIGHT_YES:
                currentMode = PARAM_NIGHT_MODE;
                break;
            case UiModeManager.MODE_NIGHT_NO:
                currentMode = PARAM_DAY_MODE;
                break;
        }
        writer.println("DayNightMode changed to: " + currentMode);
    }

    private void runSuspendCommand(String[] args, IndentingPrintWriter writer) {
        // args[0] is always either COMMAND_SUSPEND or COMMAND_HIBERNE.
        String command = args[0];
        boolean isHibernation = command.equals(COMMAND_HIBERNATE);
        // Default is --auto, so simulate is decided based on device capability.
        boolean simulate = !mCarPowerManagementService.isSuspendAvailable(isHibernation);
        boolean modeSet = false;
        boolean skipGarageMode = false;
        int resumeDelay = CarPowerManagementService.NO_WAKEUP_BY_TIMER;
        int index = 1;
        while (index < args.length) {
            switch (args[index]) {
                case PARAM_SIMULATE:
                    if (modeSet) {
                        writer.printf("Invalid command syntax.\nUsage: %s\n",
                                getSuspendCommandUsage(command));
                        return;
                    }
                    simulate = true;
                    modeSet = true;
                    break;
                case PARAM_AUTO:
                    if (modeSet) {
                        writer.printf("Invalid command syntax.\nUsage: %s\n",
                                getSuspendCommandUsage(command));
                        return;
                    }
                    simulate = !mCarPowerManagementService.isSuspendAvailable(isHibernation);
                    modeSet = true;
                    break;
                case PARAM_REAL:
                    if (modeSet) {
                        writer.printf("Invalid command syntax.\nUsage: %s\n",
                                getSuspendCommandUsage(command));
                        return;
                    }
                    simulate = false;
                    modeSet = true;
                    break;
                case PARAM_SKIP_GARAGEMODE:
                    skipGarageMode = true;
                    break;
                case PARAM_WAKEUP_AFTER:
                    index++;
                    if (index >= args.length) {
                        writer.printf("Invalid command syntax.\nUsage: %s\n",
                                getSuspendCommandUsage(command));
                        return;
                    }
                    resumeDelay = Integer.parseInt(args[index]);
                    break;
                default:
                    writer.printf("Invalid command syntax.\nUsage: %s\n",
                            getSuspendCommandUsage(command));
                    return;
            }
            index++;
        }
        if (resumeDelay >= 0 && !simulate) {
            writer.printf("Wake up by timer is available only with simulated suspend.\n");
            return;
        }

        String suspendType = isHibernation ? "disk" : "RAM";
        if (simulate) {
            try {
                writer.printf("Suspend: simulating suspend-to-%s.\n", suspendType);
                mCarPowerManagementService.simulateSuspendAndMaybeReboot(
                        isHibernation ? PowerHalService.PowerState.SHUTDOWN_TYPE_HIBERNATION
                        : PowerHalService.PowerState.SHUTDOWN_TYPE_DEEP_SLEEP,
                        /* shouldReboot= */ false, skipGarageMode, resumeDelay);
            } catch (Exception e) {
                writer.printf("Simulating suspend-to-%s failed: %s\n", suspendType, e.getMessage());
            }
        } else {
            try {
                mCarPowerManagementService.suspendFromCommand(isHibernation, skipGarageMode);
            } catch (Exception e) {
                writer.printf("Suspend to %s failed: %s.\n", suspendType, e.getMessage());
            }
        }
    }

    private void forceGarageMode(String arg, IndentingPrintWriter writer) {
        switch (arg) {
            case PARAM_ON_MODE:
                mSystemInterface.setDisplayState(false);
                mGarageModeService.forceStartGarageMode();
                writer.println("Garage mode: " + mGarageModeService.isGarageModeActive());
                break;
            case PARAM_OFF_MODE:
                mSystemInterface.setDisplayState(true);
                mGarageModeService.stopAndResetGarageMode();
                writer.println("Garage mode: " + mGarageModeService.isGarageModeActive());
                break;
            case PARAM_QUERY_MODE:
                mGarageModeService.dump(writer);
                break;
            case PARAM_REBOOT_AFTER_GARAGEMODE:
                writer.printf("\"cmd car_service garagemode reboot\" is deprecated. Use "
                        + "\"cmd car_service power-off --reboot\" next time");
                try {
                    mCarPowerManagementService.powerOffFromCommand(/*skipGarageMode= */ false,
                            /* reboot= */ true);
                    writer.println("Entering Garage Mode. Will reboot when it completes.");
                } catch (IllegalStateException e) {
                    writer.printf("Entering Garage Mode failed: %s\n", e.getMessage());
                }
                break;
            default:
                writer.printf("Unknown value: %s. Valid argument: %s|%s|%s|%s\n",
                        arg, PARAM_ON_MODE, PARAM_OFF_MODE, PARAM_QUERY_MODE,
                        PARAM_REBOOT_AFTER_GARAGEMODE);
        }
    }

    private void runSilentCommand(String arg, IndentingPrintWriter writer) {
        switch (arg) {
            case SILENT_MODE_FORCED_SILENT:
                writer.println("Forcing silent mode to silent");
                mCarPowerManagementService.setSilentMode(SILENT_MODE_FORCED_SILENT);
                break;
            case SILENT_MODE_FORCED_NON_SILENT:
                writer.println("Forcing silent mode to non-silent");
                mCarPowerManagementService.setSilentMode(SILENT_MODE_FORCED_NON_SILENT);
                break;
            case SILENT_MODE_NON_FORCED:
                writer.println("Not forcing silent mode");
                mCarPowerManagementService.setSilentMode(SILENT_MODE_NON_FORCED);
                break;
            case PARAM_QUERY_MODE:
                mCarPowerManagementService.dumpSilentMode(writer);
                break;
            default:
                writer.printf("Unknown value: %s. Valid argument: %s|%s|%s|%s\n", arg,
                        SILENT_MODE_FORCED_SILENT, SILENT_MODE_FORCED_NON_SILENT,
                        SILENT_MODE_NON_FORCED, PARAM_QUERY_MODE);
        }
    }

    private void emulateDrivingState(String[] args, IndentingPrintWriter writer) {
        if (args.length != 2) {
            writer.println("invalid usage, must pass driving state");
            return;
        }
        String mode = args[1];
        switch (mode) {
            case DRIVING_STATE_DRIVE:
                emulateDrive();
                break;
            case DRIVING_STATE_PARK:
                emulatePark();
                break;
            case DRIVING_STATE_REVERSE:
                emulateReverse();
                break;
            default:
                writer.printf("invalid driving mode %s; must be %s or %s\n", mode,
                        DRIVING_STATE_DRIVE, DRIVING_STATE_PARK);
        }
    }

    /**
     * Emulates driving mode. Called by
     * {@code adb shell cmd car_service emulate-driving-state drive}.
     */
    private void emulateDrive() {
        Slogf.i(TAG, "Emulating driving mode (speed=80mph, gear=8)");
        mHal.injectVhalEvent(VehiclePropertyIds.PERF_VEHICLE_SPEED,
                /* zone= */ 0, /* value= */ "80", /* delayTime= */ 2000);
        mHal.injectVhalEvent(VehiclePropertyIds.GEAR_SELECTION,
                /* zone= */ 0, Integer.toString(VehicleGear.GEAR_8), /* delayTime= */ 0);
        mHal.injectVhalEvent(VehiclePropertyIds.PARKING_BRAKE_ON,
                /* zone= */ 0, /* value= */ "false", /* delayTime= */ 0);
    }

    /**
     * Emulates reverse driving mode. Called by
     * {@code adb shell cmd car_service emulate-driving-state reverse}.
     */
    private void emulateReverse() {
        Slogf.i(TAG, "Emulating reverse driving mode (speed=5mph)");
        mHal.injectVhalEvent(VehiclePropertyIds.PERF_VEHICLE_SPEED,
                /* zone= */ 0, /* value= */ "5", /* delayTime= */ 2000);
        mHal.injectVhalEvent(VehiclePropertyIds.GEAR_SELECTION,
                /* zone= */ 0, Integer.toString(VehicleGear.GEAR_REVERSE), /* delayTime= */ 0);
        mHal.injectVhalEvent(VehiclePropertyIds.PARKING_BRAKE_ON,
                /* zone= */ 0, /* value= */ "false", /* delayTime= */ 0);
    }

    /**
     * Emulates parking mode. Called by
     * {@code adb shell cmd car_service emulate-driving-state park}.
     */
    private void emulatePark() {
        Slogf.i(TAG, "Emulating parking mode");
        mHal.injectVhalEvent(VehiclePropertyIds.PERF_VEHICLE_SPEED,
                /* zone= */ 0, /* value= */ "0", /* delayTime= */ 0);
        mHal.injectVhalEvent(VehiclePropertyIds.GEAR_SELECTION,
                /* zone= */ 0, Integer.toString(VehicleGear.GEAR_PARK), /* delayTime= */ 0);
    }

    private int definePowerPolicy(String[] args, IndentingPrintWriter writer) {
        boolean result = mCarPowerManagementService.definePowerPolicyFromCommand(args, writer);
        if (result) return RESULT_OK;
        writer.printf("\nUsage: cmd car_service %s <POLICY_ID> [--enable COMP1,COMP2,...] "
                + "[--disable COMP1,COMP2,...]\n", COMMAND_DEFINE_POWER_POLICY);
        return RESULT_ERROR;
    }

    private int applyPowerPolicy(String[] args, IndentingPrintWriter writer) {
        boolean result = mCarPowerManagementService.applyPowerPolicyFromCommand(args, writer);
        if (result) return RESULT_OK;
        writer.printf("\nUsage: cmd car_service %s <POLICY_ID>\n", COMMAND_APPLY_POWER_POLICY);
        return RESULT_ERROR;
    }

    private int definePowerPolicyGroup(String[] args, IndentingPrintWriter writer) {
        boolean result = mCarPowerManagementService.definePowerPolicyGroupFromCommand(args, writer);
        if (result) return RESULT_OK;
        writer.printf("\nUsage: cmd car_service %s <POLICY_GROUP_ID> [%s:<POLICY_ID>] "
                + "[%s:<POLICY_ID>]\n", COMMAND_DEFINE_POWER_POLICY_GROUP,
                POWER_STATE_WAIT_FOR_VHAL, POWER_STATE_ON);
        return RESULT_ERROR;
    }

    private int setPowerPolicyGroup(String[] args, IndentingPrintWriter writer) {
        boolean result = mCarPowerManagementService.setPowerPolicyGroupFromCommand(args, writer);
        if (result) return RESULT_OK;
        writer.printf("\nUsage: cmd car_service %s <POLICY_GROUP_ID>\n",
                COMMAND_SET_POWER_POLICY_GROUP);
        return RESULT_ERROR;
    }

    private int applyCtsVerifierPowerPolicy(String policyId, String ops, String cmdName,
            IndentingPrintWriter writer) {
        String[] defArgs = {"define-power-policy", policyId, ops, "WIFI,BLUETOOTH,LOCATION"};
        mCarPowerManagementService.definePowerPolicyFromCommand(defArgs, writer);

        String[] appArgs = {"apply-power-policy", policyId};
        boolean result = mCarPowerManagementService.applyPowerPolicyFromCommand(appArgs, writer);
        if (result) return RESULT_OK;

        writer.printf("\nUsage: cmd car_service %s\n", cmdName);
        return RESULT_ERROR;
    }

    private int applyCtsVerifierPowerOffPolicy(String[] unusedArgs, IndentingPrintWriter writer) {
        return applyCtsVerifierPowerPolicy("cts_verifier_off", "--disable",
                COMMAND_APPLY_CTS_VERIFIER_POWER_OFF_POLICY, writer);
    }

    private int applyCtsVerifierPowerOnPolicy(String[] unusedArgs, IndentingPrintWriter writer) {
        return applyCtsVerifierPowerPolicy("cts_verifier_on", "--enable",
                COMMAND_APPLY_CTS_VERIFIER_POWER_ON_POLICY, writer);
    }

    private void powerOff(String[] args, IndentingPrintWriter writer) {
        boolean skipGarageMode = false;
        boolean reboot = false;
        int index = 1;
        while (index < args.length) {
            switch (args[index]) {
                case PARAM_SKIP_GARAGEMODE:
                    skipGarageMode = true;
                    break;
                case PARAM_REBOOT:
                    reboot = true;
                    break;
                default:
                    writer.printf("Invalid usage: %s [%s] [%s]\n", COMMAND_POWER_OFF,
                            PARAM_SKIP_GARAGEMODE, PARAM_REBOOT);
                    return;
            }
            index++;
        }
        mCarPowerManagementService.powerOffFromCommand(skipGarageMode, reboot);
    }

    /**
     * Inject a fake  VHAL event
     *
     * @param property the Vehicle property Id as defined in the HAL
     * @param zone     Zone that this event services
     * @param isErrorEvent indicates the type of event
     * @param value    Data value of the event
     * @param delayTime the event timestamp is increased by delayTime
     * @param writer   IndentingPrintWriter
     */
    private void injectVhalEvent(String property, String zone, String value,
            boolean isErrorEvent, String delayTime, IndentingPrintWriter writer) {
        Slogf.i(TAG, "Injecting VHAL event: prop="  + property + ", zone=" + zone + ", value="
                + value + ", isError=" + isErrorEvent
                + (TextUtils.isEmpty(delayTime) ?  "" : ", delayTime=" + delayTime));
        if (zone.equalsIgnoreCase(PARAM_VEHICLE_PROPERTY_AREA_GLOBAL)) {
            if (!isPropertyAreaTypeGlobal(property)) {
                writer.printf("Property area type inconsistent with given zone: %s \n", zone);
                return;
            }
        }
        try {
            if (isErrorEvent) {
                VehiclePropError error = new VehiclePropError();
                error.areaId = Integer.decode(zone);
                error.propId = Integer.decode(property);
                error.errorCode = Integer.decode(value);
                mHal.onPropertySetError(new ArrayList<VehiclePropError>(Arrays.asList(error)));
            } else {
                mHal.injectVhalEvent(Integer.decode(property), Integer.decode(zone), value,
                        Integer.decode(delayTime));
            }
        } catch (NumberFormatException e) {
            writer.printf("Invalid property Id zone Id or value: %s \n", e);
            showHelp(writer);
        }
    }

    // Inject continuous vhal events.
    private void injectContinuousEvents(String[] args, IndentingPrintWriter writer) {
        if (args.length < 3 || args.length > 8) {
            showInvalidArguments(writer);
            return;
        }
        String areaId = PARAM_VEHICLE_PROPERTY_AREA_GLOBAL;
        String sampleRate = PARAM_INJECT_EVENT_DEFAULT_RATE;
        String durationTime = PARAM_INJECT_EVENT_DEFAULT_DURATION;
        String propId = args[1];
        String data = args[2];
        // scan input
        for (int i = 3; i < args.length - 1; i++) {
            switch (args[i]) {
                case "-d":
                    durationTime = args[++i];
                    break;
                case "-z" :
                    areaId = args[++i];
                    break;
                case "-s" :
                    sampleRate = args[++i];
                    break;
                default:
                    writer.printf("%s is an invalid flag.\n", args[i]);
                    showHelp(writer);
                    return;
            }
        }
        try {
            float sampleRateFloat = Float.parseFloat(sampleRate);
            if (sampleRateFloat <= 0) {
                writer.printf("SampleRate: %s is an invalid value. "
                        + "SampleRate must be greater than 0.\n", sampleRate);
                showHelp(writer);
                return;
            }
            mHal.injectContinuousVhalEvent(Integer.decode(propId),
                    Integer.decode(areaId), data,
                    sampleRateFloat, Long.parseLong(durationTime));
        } catch (NumberFormatException e) {
            writer.printf("Invalid arguments: %s\n", e);
            showHelp(writer);
        }

    }

    // Handles set-property-value command.
    private void runSetVehiclePropertyValue(String[] args, IndentingPrintWriter writer) {
        if (args.length != 4) {
            writer.println("Invalid command syntax:");
            writer.printf("Usage: %s\n", getSetPropertyValueUsage());
            return;
        }
        String strId = args[1];
        String strAreaId = args[2];
        String value = args[3];
        int id;
        int areaId;
        try {
            id = Integer.decode(strId);
            areaId = Integer.decode(strAreaId);
        } catch (NumberFormatException e) {
            writer.printf("Cannot set a property: Invalid property ID(%s) or area ID(%s) format\n",
                    strId, strAreaId);
            return;
        }
        Slogf.i(TAG, "Setting vehicle property: id=%s, areaId=%s, value=%s", strId, strAreaId,
                value);
        if (strAreaId.equalsIgnoreCase(PARAM_VEHICLE_PROPERTY_AREA_GLOBAL)
                && !isPropertyAreaTypeGlobal(strId)) {
            writer.printf("Property area type is inconsistent with given area ID: %s\n",
                    strAreaId);
            return;
        }
        try {
            mHal.setPropertyFromCommand(id, areaId, value, writer);
            writer.printf("Property(%s) is set to %s successfully\n", strId, value);
        } catch (Exception e) {
            writer.printf("Cannot set a property: %s\n", e);
        }
    }

    private static String getSetPropertyValueUsage() {
        return COMMAND_SET_PROPERTY_VALUE + " <PROPERTY_ID in Hex or Decimal> <areaId> "
                + "<data (can be comma-separated)>";
    }

    // Set a target camera device for the rearview
    private void setRearviewCameraId(String[] args, IndentingPrintWriter writer) {
        if (args.length != 2) {
            showInvalidArguments(writer);
            return;
        }

        if (!mCarEvsService.setRearviewCameraIdFromCommand(args[1])) {
            writer.println("Failed to set CarEvsService rearview camera device id.");
        } else {
            writer.printf("CarEvsService is set to use %s.\n", args[1]);
        }
    }

    private void setDrivingSafetyRegion(String[] args, IndentingPrintWriter writer) {
        if (args.length != 1 && args.length != 2) {
            showInvalidArguments(writer);
            return;
        }
        String region = args.length == 2 ? args[1] : CarPackageManager.DRIVING_SAFETY_REGION_ALL;
        writer.println("Set driving safety region to:" + region);
        CarLocalServices.getService(CarPackageManagerService.class).resetDrivingSafetyRegion(
                region);
    }

    private void getRearviewCameraId(IndentingPrintWriter writer) {
        writer.printf("CarEvsService is using %s for the rearview.\n",
                mCarEvsService.getRearviewCameraIdFromCommand());
    }

    private void controlWatchdogPackageKillableState(String[] args, IndentingPrintWriter writer) {
        if (args.length != 3) {
            showInvalidArguments(writer);
            return;
        }
        if (!args[1].equals("true") && !args[1].equals("false")) {
            writer.println("Failed to parse killable state argument. "
                    + "Valid arguments: killable | not-killable");
            return;
        }
        int currentUserId = ActivityManager.getCurrentUser();
        mCarWatchdogService.setKillablePackageAsUser(
                args[2], UserHandle.of(currentUserId), args[1].equals("true"));
        writer.printf("Set package killable state as '%s' for user '%d' and package '%s'\n",
                args[1].equals("true") ? "killable" : "not killable", currentUserId, args[2]);
    }

    // Set third-party foreground I/O threshold for car watchdog
    private void setWatchdogIoThirdPartyForegroundBytes(String[] args,
            IndentingPrintWriter writer) {
        if (args.length != 2) {
            showInvalidArguments(writer);
            return;
        }
        try {
            long newForegroundModeBytes = Long.parseLong(args[1]);
            ResourceOveruseConfiguration configuration =
                    getThirdPartyResourceOveruseConfiguration(
                            CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO);
            if (configuration == null) {
                writer.println("Failed to get third-party resource overuse configurations.");
                return;
            }
            ResourceOveruseConfiguration newConfiguration = setComponentLevelForegroundIoBytes(
                    configuration, newForegroundModeBytes);
            int result = mCarWatchdogService.setResourceOveruseConfigurations(
                    Collections.singletonList(newConfiguration),
                    CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO);
            if (result == CarWatchdogManager.RETURN_CODE_SUCCESS) {
                writer.printf(
                        "Successfully set third-party I/O overuse foreground threshold. { "
                                + "foregroundModeBytes = %d } \n",
                        newForegroundModeBytes);
            } else {
                writer.println("Failed to set third-party I/O overuse foreground threshold.");
            }
        } catch (NumberFormatException e) {
            writer.println("The argument provided does not contain a parsable long.");
            writer.println("Failed to set third-party I/O overuse foreground threshold.");
        } catch (RemoteException e) {
            writer.printf("Failed to set third-party I/O overuse foreground threshold: %s",
                    e.getMessage());
        }
    }

    private void getWatchdogIoThirdPartyForegroundBytes(IndentingPrintWriter writer) {
        ResourceOveruseConfiguration configuration =
                getThirdPartyResourceOveruseConfiguration(
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO);
        try {
            IoOveruseConfiguration ioOveruseConfiguration = Objects.requireNonNull(
                    configuration).getIoOveruseConfiguration();
            PerStateBytes componentLevelThresholds = Objects.requireNonNull(ioOveruseConfiguration)
                    .getComponentLevelThresholds();
            long foregroundBytes = Objects.requireNonNull(
                    componentLevelThresholds).getForegroundModeBytes();
            writer.printf("foregroundModeBytes = %d \n", foregroundBytes);
        } catch (NullPointerException e) {
            writer.println("Failed to get third-party I/O overuse foreground threshold.");
        }
    }

    private ResourceOveruseConfiguration getThirdPartyResourceOveruseConfiguration(
            int resourceOveruseFlag) {
        for (ResourceOveruseConfiguration configuration :
                mCarWatchdogService.getResourceOveruseConfigurations(resourceOveruseFlag)) {
            if (configuration.getComponentType()
                    == ResourceOveruseConfiguration.COMPONENT_TYPE_THIRD_PARTY) {
                return configuration;
            }
        }
        return null;
    }

    private ResourceOveruseConfiguration setComponentLevelForegroundIoBytes(
            ResourceOveruseConfiguration configuration, long foregroundModeBytes) {
        IoOveruseConfiguration ioOveruseConfiguration = configuration.getIoOveruseConfiguration();
        PerStateBytes componentLevelThresholds =
                ioOveruseConfiguration.getComponentLevelThresholds();
        return constructResourceOveruseConfigurationBuilder(
                configuration).setIoOveruseConfiguration(
                new IoOveruseConfiguration.Builder(
                        new PerStateBytes(foregroundModeBytes,
                                componentLevelThresholds.getBackgroundModeBytes(),
                                componentLevelThresholds.getGarageModeBytes()),
                        ioOveruseConfiguration.getPackageSpecificThresholds(),
                        ioOveruseConfiguration.getAppCategorySpecificThresholds(),
                        ioOveruseConfiguration.getSystemWideThresholds())
                        .build())
                .build();
    }

    private ResourceOveruseConfiguration.Builder constructResourceOveruseConfigurationBuilder(
            ResourceOveruseConfiguration configuration) {
        return new ResourceOveruseConfiguration.Builder(configuration.getComponentType(),
                configuration.getSafeToKillPackages(),
                configuration.getVendorPackagePrefixes(),
                configuration.getPackagesToAppCategoryTypes())
                .setIoOveruseConfiguration(configuration.getIoOveruseConfiguration());
    }

    private void controlWatchdogProcessHealthCheck(String[] args, IndentingPrintWriter writer) {
        if (args.length != 2) {
            showInvalidArguments(writer);
            return;
        }
        if (!args[1].equals("enable") && !args[1].equals("disable")) {
            writer.println("Failed to parse argument. Valid arguments: enable | disable");
            return;
        }
        mCarWatchdogService.controlProcessHealthCheck(args[1].equals("enable"));
        writer.printf("Watchdog health checking is now %sd \n", args[1]);
    }

    private void performResourceOveruseKill(String[] args, IndentingPrintWriter writer) {
        if (args.length != 2) {
            showInvalidArguments(writer);
            return;
        }
        String packageName = args[1];
        int userId = ActivityManager.getCurrentUser();
        boolean isKilled = mCarWatchdogService.performResourceOveruseKill(packageName, userId);
        if (isKilled) {
            writer.printf("Successfully killed package '%s' for user %d\n", packageName, userId);
        } else {
            writer.printf("Failed to kill package '%s' for user %d\n", packageName, userId);
        }
    }

    private void printTelemetryHelp(IndentingPrintWriter writer) {
        writer.println("A CLI to interact with CarTelemetryService.");
        writer.println("\nUSAGE: adb shell cmd car_service telemetry <subcommand> [options]");
        writer.println("\n\t-h");
        writer.println("\t  Print this help text.");
        writer.println("\tadd <name>");
        writer.println("\t  Adds MetricsConfig from STDIN. Only a binary proto is supported.");
        writer.println("\tremove <name>");
        writer.println("\t  Removes metrics config.");
        writer.println("\tremove-all");
        writer.println("\t  Removes all metrics configs.");
        writer.println("\tping-script-executor [published data filepath] [state filepath]");
        writer.println("\t  Runs a Lua script from stdin.");
        writer.println("\tlist");
        writer.println("\t  Lists the active config metrics.");
        writer.println("\tget-result <name>");
        writer.println("\t  Blocks until a metrics report is available and returns it.");
        writer.println("\t  If there are multiple reports, the CLI is guaranteed to receive "
                + "at least one report. There is no guarantee that it will be able to get "
                + "all of them.");
        writer.println("\nEXAMPLES:");
        writer.println("\t$ adb shell cmd car_service telemetry add name < config1.protobin");
        writer.println("\t\tWhere config1.protobin is a serialized MetricsConfig proto.");
        writer.println("\n\t$ adb shell cmd car_service telemetry get-result name");
        writer.println("\t$ adb shell cmd car_service telemetry ping-script-executor "
                + "< example_script.lua");
        writer.println("\t$ adb shell cmd car_service telemetry ping-script-executor "
                + "/data/local/tmp/published_data < example_script.lua");
        writer.println("\t$ adb shell cmd car_service telemetry ping-script-executor "
                + "/data/local/tmp/bundle /data/local/tmp/bundle2 < example_script.lua");
    }

    private void handleTelemetryCommands(String[] args, IndentingPrintWriter writer) {
        if (args.length < 2) {
            printTelemetryHelp(writer);
            return;
        }
        Car car = Car.createCar(mContext);
        CarTelemetryManager carTelemetryManager =
                (CarTelemetryManager) car.getCarManager(Car.CAR_TELEMETRY_SERVICE);
        if (carTelemetryManager == null) {
            writer.println("telemetry service is not enabled, cannot use CLI");
            return;
        }
        String cmd = args[1];
        switch (cmd) {
            case "add":
                if (args.length != 3) {
                    writer.println("Invalid number of arguments.");
                    printTelemetryHelp(writer);
                    return;
                }
                try (BufferedInputStream in = new BufferedInputStream(
                                new FileInputStream(getInFileDescriptor()));
                        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    FileUtils.copy(in, out);
                    CountDownLatch latch = new CountDownLatch(1);
                    carTelemetryManager.addMetricsConfig(args[2], out.toByteArray(), Runnable::run,
                            (metricsConfigName, statusCode) -> {
                                if (statusCode == STATUS_ADD_METRICS_CONFIG_SUCCEEDED) {
                                    writer.printf("MetricsConfig %s is added.\n", args[2]);
                                } else {
                                    writer.printf(
                                            "Failed to add %s. Status is %d. "
                                                    + "Please see logcat for details.\n",
                                            args[2],
                                            statusCode);
                                }
                                latch.countDown();
                            });
                    latch.await(TELEMETRY_RESULT_WAIT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                } catch (IOException | InterruptedException | NumberFormatException e) {
                    writer.println("Failed to read from stdin: " + e);
                }
                break;
            case "remove":
                if (args.length != 3) {
                    writer.println("Invalid number of arguments.");
                    printTelemetryHelp(writer);
                    return;
                }
                carTelemetryManager.removeMetricsConfig(args[2]);
                writer.printf("Removing %s... Please see logcat for details.\n", args[2]);
                break;
            case "remove-all":
                if (args.length != 2) {
                    writer.println("Invalid number of arguments.");
                    printTelemetryHelp(writer);
                    return;
                }
                carTelemetryManager.removeAllMetricsConfigs();
                writer.printf("Removing all MetricsConfigs... Please see logcat for details.\n");
                break;
            case "ping-script-executor":
                if (args.length < 2 || args.length > 4) {
                    writer.println("Invalid number of arguments.");
                    printTelemetryHelp(writer);
                    return;
                }
                PersistableBundle publishedData = new PersistableBundle();
                publishedData.putInt("age", 99);
                publishedData.putStringArray(
                        "string_array",
                        new String[]{"a", "b", "c", "a", "b", "c", "a", "b", "c"});
                PersistableBundle nestedBundle = new PersistableBundle();
                nestedBundle.putInt("age", 100);
                nestedBundle.putStringArray(
                        "string_array",
                        new String[]{"q", "w", "e", "r", "t", "y"});
                publishedData.putPersistableBundle("pers_bundle", nestedBundle);
                PersistableBundle savedState = null;
                // Read published data
                if (args.length >= 3) {
                    try {
                        publishedData = IoUtils.readBundle(new File(args[2]));
                    } catch (IOException e) {
                        writer.println("Published data path is invalid: " + e);
                        return;
                    }
                }
                // Read saved state
                if (args.length == 4) {
                    try {
                        savedState = IoUtils.readBundle(new File(args[3]));
                    } catch (IOException e) {
                        writer.println("Saved data path is invalid: " + e);
                        return;
                    }
                }
                try {
                    pingScriptExecutor(writer, publishedData, savedState);
                } catch (InterruptedException | RemoteException e) {
                    throw new RuntimeException(e);
                }
                break;
            case "list":
                writer.println("Active metric configs:");
                mCarTelemetryService.getActiveMetricsConfigDetails().forEach((configDetails) -> {
                    writer.printf("- %s\n", configDetails);
                });
                break;
            case "get-result":
                if (args.length != 3) {
                    writer.println("Invalid number of arguments.");
                    printTelemetryHelp(writer);
                    return;
                }
                String configName = args[2];
                CountDownLatch latch = new CountDownLatch(1);
                CarTelemetryManager.MetricsReportCallback callback =
                        (metricsConfigName, report, telemetryError, status) -> {
                            if (report != null) {
                                report.size(); // unparcel()'s
                                writer.println("Report for " + metricsConfigName + ": " + report);
                            } else if (telemetryError != null) {
                                parseTelemetryError(telemetryError, writer);
                            }
                            // the latch counts after receiving 1 report even if there are
                            // multiple reports
                            latch.countDown();
                        };
                carTelemetryManager.clearReportReadyListener();
                Executor executor = Executors.newSingleThreadExecutor();
                carTelemetryManager.setReportReadyListener(executor, metricsConfigName -> {
                    if (metricsConfigName.equals(configName)) {
                        carTelemetryManager.getFinishedReport(
                                metricsConfigName, executor, callback);
                    }
                });
                try {
                    writer.println("Waiting for the result...");
                    writer.flush();
                    latch.await();
                } catch (InterruptedException e) {
                    writer.println("Result await error: " + e);
                } finally {
                    carTelemetryManager.clearReportReadyListener();
                }
                break;
            default:
                printTelemetryHelp(writer);
        }
    }

    private void pingScriptExecutor(
            IndentingPrintWriter writer,
            PersistableBundle publishedData,
            PersistableBundle savedState)
            throws InterruptedException, RemoteException {
        writer.println("Sending data to script executor...");
        if (mScriptExecutor == null) {
            writer.println("[I] No mScriptExecutor, creating a new one");
            connectToScriptExecutor(writer);
        }
        String script;
        try (
                BufferedInputStream in = new BufferedInputStream(
                        new FileInputStream(getInFileDescriptor()));
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            FileUtils.copy(in, out);
            script = out.toString();
        } catch (IOException | NumberFormatException e) {
            writer.println("[E] Failed to read from stdin: " + e);
            return;
        }
        writer.println("[I] Running the script: ");
        writer.println(script);
        writer.flush();

        CountDownLatch resultLatch = new CountDownLatch(1);
        IScriptExecutorListener listener =
                new IScriptExecutorListener.Stub() {
                    @Override
                    public void onScriptFinished(PersistableBundle result) {
                        writer.println("Script finished");
                        result.size(); // unparcel()'s
                        writer.println("result: " + result);
                        writer.flush();
                        resultLatch.countDown();
                    }

                    @Override
                    public void onSuccess(PersistableBundle state) {
                        writer.println("Script succeeded, saving inter result");
                        state.size(); // unparcel()'s
                        writer.println("state: " + state);
                        writer.flush();
                        resultLatch.countDown();
                    }

                    @Override
                    public void onError(int errorType, String msg, String stack) {
                        writer.println("Script error: " + errorType + ": " + msg);
                        writer.println("Stack: " + stack);
                        writer.flush();
                        resultLatch.countDown();
                    }

                    @Override
                    public void onMetricsReport(
                            @NonNull PersistableBundle report,
                            @Nullable PersistableBundle stateToPersist) {
                        writer.println("Script produced a report without finishing");
                        report.size(); // unparcel()'s
                        writer.println("report: " + report);
                        if (stateToPersist != null) {
                            stateToPersist.size(); // unparcel()'s
                            writer.println("state to persist: " + stateToPersist);
                        }
                        writer.flush();
                        resultLatch.countDown();
                    }
                };
        mScriptExecutor.invokeScript(
                script,
                "foo",
                publishedData,
                savedState,
                listener);
        writer.println("[I] Waiting for the result");
        writer.flush();
        resultLatch.await(10, TimeUnit.SECONDS); // seconds
        mContext.unbindService(mScriptExecutorConn);
    }

    private void connectToScriptExecutor(IndentingPrintWriter writer) throws InterruptedException {
        CountDownLatch connectionLatch = new CountDownLatch(1);
        mScriptExecutorConn =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        writer.println("[I] Connected to ScriptExecutor Service");
                        writer.flush();
                        mScriptExecutor = IScriptExecutor.Stub.asInterface(service);
                        connectionLatch.countDown();
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        writer.println("[E] Failed to connect to ScriptExecutor Service");
                        writer.flush();
                        mScriptExecutor = null;
                        connectionLatch.countDown();
                    }
                };
        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(
                        "com.android.car.scriptexecutor",
                        "com.android.car.scriptexecutor.ScriptExecutor"));
        writer.println("[I] Binding to the script executor");
        boolean success =
                mContext.bindServiceAsUser(
                        intent,
                        mScriptExecutorConn,
                        Context.BIND_AUTO_CREATE,
                        UserHandle.SYSTEM);
        if (success) {
            writer.println("[I] Found ScriptExecutor package");
            writer.flush();
        } else {
            writer.println("[E] Failed to bind to ScriptExecutor");
            writer.flush();
            mScriptExecutor = null;
            if (mScriptExecutorConn != null) {
                mContext.unbindService(mScriptExecutorConn);
            }
            return;
        }
        writer.println("[I] Waiting for the connection");
        connectionLatch.await(5, TimeUnit.SECONDS); // seconds
    }

    private void parseTelemetryError(byte[] telemetryError, IndentingPrintWriter writer) {
        try {
            TelemetryError error = TelemetryError.parseFrom(telemetryError);
            writer.println("Error: " + error.getErrorType().name() + ": "
                    + error.getMessage());
        } catch (IOException e) {
            writer.println("Error is received, but parsing error failed: " + e);
        }
    }

    private void controlComponentEnabledState(String[] args, IndentingPrintWriter writer) {
        if (args.length != 3) {
            showInvalidArguments(writer);
            return;
        }

        String packageName = args[2];
        int currentUserId = ActivityManager.getCurrentUser();

        if ("get".equals(args[1])) {
            try {
                int curState = PackageManagerHelper
                        .getApplicationEnabledSettingForUser(packageName, currentUserId);
                writer.println("Current State: " + getAppEnabledStateName(curState));
            } catch (Exception e) {
                writer.printf("%s: getting package enabled state failed with error: %s\n",
                        TAG, e.toString());
            }
            return;
        }

        int newState = 0;
        switch (args[1]) {
            case "default":
                newState = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
                break;
            case "enable":
                newState = PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
                break;
            case "disable_until_used":
                newState = PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;
                break;
            default:
                writer.println("unsupported state action: " + args[1]);
                return;
        }

        String callingPackageName = mContext.getPackageManager().getNameForUid(Process.myUid());
        try {
            PackageManagerHelper.setApplicationEnabledSettingForUser(packageName, newState,
                    /* EnabledFlag */ 0, currentUserId, callingPackageName);
        } catch (Exception e) {
            writer.printf("%s: setting package enabled state failed with error: %s\n",
                    TAG, e.toString());
            return;
        }
        writer.println("New State: " + getAppEnabledStateName(newState));
    }

    private String getAppEnabledStateName(int enabledState) {
        String stateName = "COMPONENT_ENABLED_STATE_";
        switch (enabledState) {
            case PackageManager.COMPONENT_ENABLED_STATE_DEFAULT:
                stateName += "DEFAULT";
                break;
            case PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
                stateName += "ENABLED";
                break;
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED:
                stateName += "DISABLED";
                break;
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER:
                stateName += "DISABLED_USER";
                break;
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED:
                stateName += "DISABLED_UNTIL_USED";
                break;
            default:
                stateName += "UNSUPPORTED";
                break;
        }
        return stateName;
    }

    private void checkLockIsSecure(String[] args, IndentingPrintWriter writer) {
        if ((args.length != 1) && (args.length != 2)) {
            showInvalidArguments(writer);
        }

        int userId = UserHandle.myUserId();
        if (args.length == 2) {
            userId = Integer.parseInt(args[1]);
        }
        writer.println(LockPatternHelper.isSecure(mContext, userId));
    }

    private void listVhalProps(IndentingPrintWriter writer) {
        // Note: The output here is used in AtsVehicleDeviceTest. DO NOT CHANGE the format without
        // updating AtsVehicleDeviceTest.
        writer.println("All supported property IDs from Vehicle HAL:");
        List<Integer> propIds = new ArrayList<>();
        try {
            HalPropConfig[] configs = mHal.getAllPropConfigs();
            for (int i = 0; i < configs.length; i++) {
                propIds.add(configs[i].getPropId());
            }
            writer.println(propIds.toString());
        } catch (RemoteException | ServiceSpecificException e) {
            writer.println("Failed to call getAllPropConfigs, exception: " + e);
        }
    }

    private void getVhalBackend(IndentingPrintWriter writer) {
        // Note: The output here is used in AtsVehicleDeviceTest. DO NOT CHANGE the format without
        // updating AtsVehicleDeviceTest.
        if (mHal.isAidlVhal()) {
            writer.println("Vehicle HAL backend: AIDL");
        } else {
            writer.println("Vehicle HAL backend: HIDL");
        }
    }

    private void testEchoReverseBytes(String[] args, IndentingPrintWriter writer) {
        // Note: The output here is used in
        // AndroidCarApiTest:android.car.apitest.VehicleHalLargeParcelableTest.
        // Do not change the output format without updating the test.
        if (args.length != 3) {
            showInvalidArguments(writer);
            return;
        }

        int propId = Integer.parseInt(args[1]);
        int requestSize = Integer.parseInt(args[2]);

        byte[] byteValues = new byte[requestSize];
        for (int i = 0; i < requestSize; i++) {
            byteValues[i] = (byte) (i);
        }

        try {
            mHal.set(mHal.getHalPropValueBuilder().build(propId, /* areaId= */ 0, byteValues));
        } catch (IllegalArgumentException e) {
            writer.println(
                    "Test Skipped: The property: " + propId + " is not supported, error: " + e);
            return;
        } catch (ServiceSpecificException e) {
            writer.println(
                    "Test Failed: Failed to set property: " + propId + ", error: " + e);
            return;
        }

        HalPropValue result;
        try {
            result = mHal.get(mHal.getHalPropValueBuilder().build(propId, /* areaId= */ 0));
        } catch (IllegalArgumentException | ServiceSpecificException e) {
            writer.println(
                    "Test Failed: Failed to get property: " + propId + ", error: " + e);
            return;
        }

        int resultSize = result.getByteValuesSize();
        if (resultSize != requestSize) {
            writer.println("Test Failed: expect: " + requestSize + " bytes to be returned, got: "
                    + resultSize);
            return;
        }

        byte[] reverse = new byte[requestSize];
        for (int i = 0; i < requestSize; i++) {
            reverse[i] = byteValues[requestSize - 1 - i];
        }

        byte[] resultValues = result.getByteArray();
        if (!Arrays.equals(resultValues, reverse)) {
            writer.println("Test Failed: result mismatch, expect: " + Arrays.toString(reverse)
                    + ", got: " + Arrays.toString(resultValues));
            return;
        }

        try {
            // Set the property to a single byte to free-up memory. Cannot use empty byte array
            // here which would cause IllegalArgumentException.
            mHal.set(mHal.getHalPropValueBuilder().build(propId, /* areaId= */ 0,
                    new byte[]{ 0x00 }));
        } catch (IllegalArgumentException | ServiceSpecificException e) {
            writer.println(
                    "Test Failed: Failed to clean up property value: failed to set property: "
                    + propId + ", error: " + e);
            return;
        }

        writer.println("Test Succeeded!");
    }

    private void getTargetCarVersion(String[] args, IndentingPrintWriter writer) {
        if (args.length < 2) {
            showInvalidArguments(writer);
            return;
        }

        int firstAppArg = 1;

        // TODO(b/234499460): move --user logic to private helper / support 'all'
        int userId = UserHandle.CURRENT.getIdentifier();
        if (args[1].equals("--user")) {
            if (args.length < 4) {
                showInvalidArguments(writer);
                return;
            }
            String userArg = args[2];
            firstAppArg += 2;
            if (!"current".equals(userArg) && !"cur".equals(userArg)) {
                try {
                    userId = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    showInvalidArguments(writer);
                    return;
                }
            }
        }
        if (userId == UserHandle.CURRENT.getIdentifier()) {
            userId = ActivityManager.getCurrentUser();
        }
        writer.printf("User %d:\n", userId);

        Context userContext = getContextForUser(userId);
        for (int i = firstAppArg; i < args.length; i++) {
            String app = args[i];
            try {
                CarVersion Version = CarPackageManagerService.getTargetCarVersion(
                        userContext, app);
                writer.printf("  %s: major=%d, minor=%d\n", app,
                        Version.getMajorVersion(), Version.getMinorVersion());
            } catch (ServiceSpecificException e) {
                if (e.errorCode == CarPackageManager.ERROR_CODE_NO_PACKAGE) {
                    writer.printf("  %s: not found\n", app);
                } else {
                    writer.printf("  %s: unexpected exception: %s \n", app, e);
                }
                continue;
            }
        }
    }

    // Check if the given property is global
    private static boolean isPropertyAreaTypeGlobal(@Nullable String property) {
        if (property == null) {
            return false;
        }
        return (Integer.decode(property) & VehicleArea.MASK) == VehicleArea.GLOBAL;
    }

    private static String getSuspendCommandUsage(String command) {
        return command + " [" + PARAM_AUTO + "|" + PARAM_SIMULATE + "|" + PARAM_REAL + "] ["
                + PARAM_SKIP_GARAGEMODE + "] [" + PARAM_WAKEUP_AFTER + " RESUME_DELAY]";
    }
}
