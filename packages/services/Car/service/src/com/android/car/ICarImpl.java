/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static android.car.builtin.content.pm.PackageManagerHelper.PROPERTY_CAR_SERVICE_PACKAGE_NAME;

import static com.android.car.CarServiceImpl.CAR_SERVICE_INIT_TIMING_MIN_DURATION_MS;
import static com.android.car.CarServiceImpl.CAR_SERVICE_INIT_TIMING_TAG;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DEPRECATED_CODE;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;
import static com.android.car.internal.SystemConstants.ICAR_SYSTEM_SERVER_CLIENT;

import android.annotation.MainThread;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.car.Car;
import android.car.CarFeatures;
import android.car.ICar;
import android.car.ICarResultReceiver;
import android.car.builtin.CarBuiltin;
import android.car.builtin.os.BinderHelper;
import android.car.builtin.os.BuildHelper;
import android.car.builtin.os.TraceHelper;
import android.car.builtin.os.UserManagerHelper;
import android.car.builtin.util.EventLogHelper;
import android.car.builtin.util.Slogf;
import android.car.builtin.util.TimingsTraceLog;
import android.car.user.CarUserManager;
import android.content.Context;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.frameworks.automotive.powerpolicy.internal.ICarPowerPolicySystemNotification;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.car.admin.CarDevicePolicyService;
import com.android.car.am.CarActivityService;
import com.android.car.am.FixedActivityService;
import com.android.car.audio.CarAudioService;
import com.android.car.bluetooth.CarBluetoothService;
import com.android.car.cluster.ClusterHomeService;
import com.android.car.cluster.ClusterNavigationService;
import com.android.car.cluster.InstrumentClusterService;
import com.android.car.evs.CarEvsService;
import com.android.car.garagemode.GarageModeService;
import com.android.car.hal.HalPropValue;
import com.android.car.hal.VehicleHal;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.ICarServiceHelper;
import com.android.car.internal.ICarSystemServerClient;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.oem.CarOemProxyService;
import com.android.car.os.CarPerformanceService;
import com.android.car.pm.CarPackageManagerService;
import com.android.car.power.CarPowerManagementService;
import com.android.car.stats.CarStatsService;
import com.android.car.systeminterface.SystemInterface;
import com.android.car.telemetry.CarTelemetryService;
import com.android.car.user.CarUserNoticeService;
import com.android.car.user.CarUserService;
import com.android.car.user.ExperimentalCarUserService;
import com.android.car.util.LimitedTimingsTraceLog;
import com.android.car.vms.VmsBrokerService;
import com.android.car.watchdog.CarWatchdogService;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.io.File;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

public class ICarImpl extends ICar.Stub {

    public static final String INTERNAL_INPUT_SERVICE = "internal_input";
    public static final String INTERNAL_SYSTEM_ACTIVITY_MONITORING_SERVICE =
            "system_activity_monitoring";

    @VisibleForTesting
    static final String TAG = CarLog.tagFor(ICarImpl.class);

    private static final int INITIAL_VHAL_GET_RETRY = 2;

    private static final boolean ENABLE_OEM_SERVICE = true;

    private final Context mContext;
    private final Context mCarServiceBuiltinPackageContext;
    private final VehicleHal mHal;

    private final CarFeatureController mFeatureController;

    private final SystemInterface mSystemInterface;

    @Nullable
    private final CarOemProxyService mCarOemService;
    private final SystemActivityMonitoringService mSystemActivityMonitoringService;
    private final CarPowerManagementService mCarPowerManagementService;
    private final CarPackageManagerService mCarPackageManagerService;
    private final CarInputService mCarInputService;
    private final CarDrivingStateService mCarDrivingStateService;
    private final CarUxRestrictionsManagerService mCarUXRestrictionsService;
    private final OccupantAwarenessService mOccupantAwarenessService;
    private final CarAudioService mCarAudioService;
    private final CarProjectionService mCarProjectionService;
    private final CarPropertyService mCarPropertyService;
    private final CarNightService mCarNightService;
    private final AppFocusService mAppFocusService;
    private final FixedActivityService mFixedActivityService;
    private final GarageModeService mGarageModeService;
    private final ClusterNavigationService mClusterNavigationService;
    private final InstrumentClusterService mInstrumentClusterService;
    private final CarLocationService mCarLocationService;
    private final CarBluetoothService mCarBluetoothService;
    private final PerUserCarServiceHelper mPerUserCarServiceHelper;
    private final CarDiagnosticService mCarDiagnosticService;
    private final CarStorageMonitoringService mCarStorageMonitoringService;
    private final CarMediaService mCarMediaService;
    private final CarUserService mCarUserService;
    @Nullable
    private final ExperimentalCarUserService mExperimentalCarUserService;
    private final CarOccupantZoneService mCarOccupantZoneService;
    private final CarUserNoticeService mCarUserNoticeService;
    private final VmsBrokerService mVmsBrokerService;
    private final CarBugreportManagerService mCarBugreportManagerService;
    private final CarStatsService mCarStatsService;
    private final CarExperimentalFeatureServiceController mCarExperimentalFeatureServiceController;
    private final CarWatchdogService mCarWatchdogService;
    private final CarPerformanceService mCarPerformanceService;
    private final CarDevicePolicyService mCarDevicePolicyService;
    private final ClusterHomeService mClusterHomeService;
    private final CarEvsService mCarEvsService;
    private final CarTelemetryService mCarTelemetryService;
    private final CarActivityService mCarActivityService;

    private final CarServiceBase[] mAllServices;

    private static final boolean DBG = true; // TODO(b/154033860): STOPSHIP if true

    private TimingsTraceLog mBootTiming;

    private final Object mLock = new Object();

    /** Test only service. Populate it only when necessary. */
    @GuardedBy("mLock")
    private CarTestService mCarTestService;

    @GuardedBy("mLock")
    private ICarServiceHelper mICarServiceHelper;

    private final String mVehicleInterfaceName;

    private final ICarSystemServerClientImpl mICarSystemServerClientImpl;

    private final BinderHelper.ShellCommandListener mCmdListener =
            (FileDescriptor in, FileDescriptor out, FileDescriptor err, String[] args) ->
                    newCarShellCommand().exec(ICarImpl.this, in, out, err, args);

    public ICarImpl(Context serviceContext, Context builtinContext, VehicleStub vehicle,
            SystemInterface systemInterface, String vehicleInterfaceName) {
        this(serviceContext, builtinContext, vehicle, systemInterface, vehicleInterfaceName,
                /* carUserService= */ null, /* carWatchdogService= */ null,
                /* carPerformanceService= */ null, /* garageModeService= */ null,
                /* powerPolicyDaemon= */ null, /*carTelemetryService= */ null);
    }

    @VisibleForTesting
    ICarImpl(Context serviceContext, @Nullable Context builtinContext, VehicleStub vehicle,
            SystemInterface systemInterface, String vehicleInterfaceName,
            @Nullable CarUserService carUserService,
            @Nullable CarWatchdogService carWatchdogService,
            @Nullable CarPerformanceService carPerformanceService,
            @Nullable GarageModeService garageModeService,
            @Nullable ICarPowerPolicySystemNotification powerPolicyDaemon,
            @Nullable CarTelemetryService carTelemetryService) {
        LimitedTimingsTraceLog t = new LimitedTimingsTraceLog(
                CAR_SERVICE_INIT_TIMING_TAG, TraceHelper.TRACE_TAG_CAR_SERVICE,
                CAR_SERVICE_INIT_TIMING_MIN_DURATION_MS);
        t.traceBegin("ICarImpl.constructor");

        mContext = serviceContext;
        if (builtinContext == null) {
            mCarServiceBuiltinPackageContext = serviceContext;
        } else {
            mCarServiceBuiltinPackageContext = builtinContext;
        }

        if (ENABLE_OEM_SERVICE) {
            mCarOemService = constructWithTrace(t, CarOemProxyService.class,
                    () -> new CarOemProxyService(serviceContext));
        } else {
            mCarOemService = null;
        }

        mSystemInterface = systemInterface;
        CarLocalServices.addService(SystemInterface.class, mSystemInterface);
        mHal = constructWithTrace(t, VehicleHal.class,
                () -> new VehicleHal(serviceContext, vehicle));

        t.traceBegin("VHAL.earlyInit");
        // Do this before any other service components to allow feature check. It should work
        // even without init. For that, vhal get is retried as it can be too early.
        HalPropValue disabledOptionalFeatureValue = mHal.getIfAvailableOrFailForEarlyStage(
                VehicleProperty.DISABLED_OPTIONAL_FEATURES, INITIAL_VHAL_GET_RETRY);
        t.traceEnd();

        String[] disabledFeaturesFromVhal = null;
        if (disabledOptionalFeatureValue != null) {
            String disabledFeatures = disabledOptionalFeatureValue.getStringValue();
            if (disabledFeatures != null && !disabledFeatures.isEmpty()) {
                disabledFeaturesFromVhal = disabledFeatures.split(",");
            }
        }
        if (disabledFeaturesFromVhal == null) {
            disabledFeaturesFromVhal = new String[0];
        }
        Resources res = mContext.getResources();
        String[] defaultEnabledFeatures = res.getStringArray(
                R.array.config_allowed_optional_car_features);
        final String[] disabledFromVhal = disabledFeaturesFromVhal;
        mFeatureController = constructWithTrace(t, CarFeatureController.class,
                () -> new CarFeatureController(serviceContext, defaultEnabledFeatures,
                        disabledFromVhal, mSystemInterface.getSystemCarDir()));
        mVehicleInterfaceName = vehicleInterfaceName;
        mCarPropertyService = constructWithTrace(
                t, CarPropertyService.class,
                () -> new CarPropertyService(serviceContext, mHal.getPropertyHal()));
        mCarDrivingStateService = constructWithTrace(
                t, CarDrivingStateService.class,
                () -> new CarDrivingStateService(serviceContext, mCarPropertyService));
        mCarOccupantZoneService = constructWithTrace(t, CarOccupantZoneService.class,
                () -> new CarOccupantZoneService(serviceContext));
        mCarUXRestrictionsService = constructWithTrace(t, CarUxRestrictionsManagerService.class,
                () -> new CarUxRestrictionsManagerService(serviceContext, mCarDrivingStateService,
                        mCarPropertyService, mCarOccupantZoneService));
        mCarActivityService = constructWithTrace(t, CarActivityService.class,
                () -> new CarActivityService(serviceContext));
        mCarPackageManagerService = constructWithTrace(t, CarPackageManagerService.class,
                () -> new CarPackageManagerService(serviceContext, mCarUXRestrictionsService,
                        mCarActivityService, mCarOccupantZoneService));
        if (carUserService != null) {
            mCarUserService = carUserService;
            CarLocalServices.addService(CarUserService.class, carUserService);
        } else {
            UserManager userManager = serviceContext.getSystemService(UserManager.class);
            int maxRunningUsers = UserManagerHelper.getMaxRunningUsers(serviceContext);
            mCarUserService = constructWithTrace(t, CarUserService.class,
                    () -> new CarUserService(serviceContext, mHal.getUserHal(), userManager,
                            maxRunningUsers, mCarUXRestrictionsService, mCarPackageManagerService));
        }
        if (mFeatureController.isFeatureEnabled(Car.EXPERIMENTAL_CAR_USER_SERVICE)) {
            mExperimentalCarUserService = constructWithTrace(t, ExperimentalCarUserService.class,
                    () -> new ExperimentalCarUserService(serviceContext, mCarUserService,
                            serviceContext.getSystemService(UserManager.class)));
        } else {
            mExperimentalCarUserService = null;
        }
        mSystemActivityMonitoringService = constructWithTrace(
                t, SystemActivityMonitoringService.class,
                () -> new SystemActivityMonitoringService(serviceContext));
        mCarPowerManagementService = constructWithTrace(
                t, CarPowerManagementService.class,
                () -> new CarPowerManagementService(mContext, mHal.getPowerHal(),
                        systemInterface, mCarUserService, powerPolicyDaemon));
        if (mFeatureController.isFeatureEnabled(CarFeatures.FEATURE_CAR_USER_NOTICE_SERVICE)) {
            mCarUserNoticeService = constructWithTrace(
                    t, CarUserNoticeService.class, () -> new CarUserNoticeService(serviceContext));
        } else {
            mCarUserNoticeService = null;
        }
        if (mFeatureController.isFeatureEnabled(Car.OCCUPANT_AWARENESS_SERVICE)) {
            mOccupantAwarenessService = constructWithTrace(t, OccupantAwarenessService.class,
                    () -> new OccupantAwarenessService(serviceContext));
        } else {
            mOccupantAwarenessService = null;
        }
        mPerUserCarServiceHelper = constructWithTrace(
                t, PerUserCarServiceHelper.class,
                () -> new PerUserCarServiceHelper(serviceContext, mCarUserService));
        mCarBluetoothService = constructWithTrace(t, CarBluetoothService.class,
                () -> new CarBluetoothService(serviceContext, mPerUserCarServiceHelper));
        mCarInputService = constructWithTrace(t, CarInputService.class,
                () -> new CarInputService(serviceContext, mHal.getInputHal(), mCarUserService,
                        mCarOccupantZoneService, mCarBluetoothService));
        mCarProjectionService = constructWithTrace(t, CarProjectionService.class,
                () -> new CarProjectionService(serviceContext, null /* handler */, mCarInputService,
                        mCarBluetoothService));
        if (garageModeService == null) {
            mGarageModeService = constructWithTrace(t, GarageModeService.class,
                    () -> new GarageModeService(mContext));
        } else {
            mGarageModeService = garageModeService;
        }
        mAppFocusService = constructWithTrace(t, AppFocusService.class,
                () -> new AppFocusService(serviceContext, mSystemActivityMonitoringService));
        mCarAudioService = constructWithTrace(t, CarAudioService.class,
                () -> new CarAudioService(serviceContext));
        mCarNightService = constructWithTrace(t, CarNightService.class,
                () -> new CarNightService(serviceContext, mCarPropertyService));
        mFixedActivityService = constructWithTrace(t, FixedActivityService.class,
                () -> new FixedActivityService(serviceContext, mCarActivityService));
        mClusterNavigationService = constructWithTrace(
                t, ClusterNavigationService.class,
                () -> new ClusterNavigationService(serviceContext, mAppFocusService));
        if (mFeatureController.isFeatureEnabled(Car.CAR_INSTRUMENT_CLUSTER_SERVICE)) {
            mInstrumentClusterService = constructWithTrace(t, InstrumentClusterService.class,
                    () -> new InstrumentClusterService(serviceContext,
                            mClusterNavigationService, mCarInputService));
        } else {
            mInstrumentClusterService = null;
        }
        mCarStatsService = constructWithTrace(t, CarStatsService.class, () -> {
            // This service should be initialized here.
            CarStatsService service = new CarStatsService(serviceContext);
            service.init();
            return service;
        });
        if (mFeatureController.isFeatureEnabled(Car.VEHICLE_MAP_SERVICE)) {
            mVmsBrokerService = constructWithTrace(t, VmsBrokerService.class,
                    () -> new VmsBrokerService(mContext, mCarStatsService));
        } else {
            mVmsBrokerService = null;
        }
        if (mFeatureController.isFeatureEnabled(Car.DIAGNOSTIC_SERVICE)) {
            mCarDiagnosticService = constructWithTrace(t, CarDiagnosticService.class,
                    () -> new CarDiagnosticService(serviceContext,
                            mHal.getDiagnosticHal()));
        } else {
            mCarDiagnosticService = null;
        }
        if (mFeatureController.isFeatureEnabled(Car.STORAGE_MONITORING_SERVICE)) {
            mCarStorageMonitoringService = constructWithTrace(
                    t, CarStorageMonitoringService.class,
                    () -> new CarStorageMonitoringService(serviceContext,
                            systemInterface));
        } else {
            mCarStorageMonitoringService = null;
        }
        mCarLocationService = constructWithTrace(t, CarLocationService.class,
                () -> new CarLocationService(serviceContext));
        mCarMediaService = constructWithTrace(t, CarMediaService.class,
                () -> new CarMediaService(serviceContext, mCarUserService));
        mCarBugreportManagerService = constructWithTrace(t, CarBugreportManagerService.class,
                () -> new CarBugreportManagerService(serviceContext));
        if (!BuildHelper.isUserBuild()) {
            mCarExperimentalFeatureServiceController = constructWithTrace(
                    t, CarExperimentalFeatureServiceController.class,
                    () -> new CarExperimentalFeatureServiceController(serviceContext));
        } else {
            mCarExperimentalFeatureServiceController = null;
        }
        if (carWatchdogService == null) {
            mCarWatchdogService = constructWithTrace(t, CarWatchdogService.class,
                    () -> new CarWatchdogService(serviceContext, mCarServiceBuiltinPackageContext));
        } else {
            mCarWatchdogService = carWatchdogService;
            CarLocalServices.addService(CarWatchdogService.class, mCarWatchdogService);
        }
        if (carPerformanceService == null) {
            mCarPerformanceService = constructWithTrace(t, CarPerformanceService.class,
                    () -> new CarPerformanceService(serviceContext));
        } else {
            mCarPerformanceService = carPerformanceService;
            CarLocalServices.addService(CarPerformanceService.class, mCarPerformanceService);
        }
        mCarDevicePolicyService = constructWithTrace(
                t, CarDevicePolicyService.class, () -> new CarDevicePolicyService(mContext,
                        mCarServiceBuiltinPackageContext, mCarUserService));
        if (mFeatureController.isFeatureEnabled(Car.CLUSTER_HOME_SERVICE)) {
            if (!mFeatureController.isFeatureEnabled(Car.CAR_INSTRUMENT_CLUSTER_SERVICE)) {
                mClusterHomeService = constructWithTrace(
                        t, ClusterHomeService.class,
                        () -> new ClusterHomeService(serviceContext, mHal.getClusterHal(),
                                mClusterNavigationService, mCarOccupantZoneService,
                                mFixedActivityService));
            } else {
                Slogf.w(TAG, "Can't init ClusterHomeService, since Old cluster service is running");
                mClusterHomeService = null;
            }
        } else {
            mClusterHomeService = null;
        }

        if (mFeatureController.isFeatureEnabled(Car.CAR_EVS_SERVICE)) {
            mCarEvsService = constructWithTrace(t, CarEvsService.class,
                    () -> new CarEvsService(serviceContext, mCarServiceBuiltinPackageContext,
                            mHal.getEvsHal(), mCarPropertyService));
        } else {
            mCarEvsService = null;
        }

        if (mFeatureController.isFeatureEnabled(Car.CAR_TELEMETRY_SERVICE)) {
            if (carTelemetryService == null) {
                mCarTelemetryService = constructWithTrace(t, CarTelemetryService.class,
                        () -> new CarTelemetryService(
                                serviceContext, mCarPowerManagementService, mCarPropertyService));
            } else {
                mCarTelemetryService = carTelemetryService;
            }
        } else {
            mCarTelemetryService = null;
        }

        // Be careful with order. Service depending on other service should be inited later.
        List<CarServiceBase> allServices = new ArrayList<>();
        allServices.add(mFeatureController);
        allServices.add(mCarPropertyService); // mCarUXRestrictionsService depends on it
        allServices.add(mCarOccupantZoneService); // mCarUXRestrictionsService depends on it
        allServices.add(mCarUXRestrictionsService); // mCarUserService depends on it
        allServices.add(mCarUserService);
        addServiceIfNonNull(allServices, mExperimentalCarUserService);
        allServices.add(mSystemActivityMonitoringService);
        allServices.add(mCarPowerManagementService);
        allServices.add(mCarDrivingStateService);
        addServiceIfNonNull(allServices, mOccupantAwarenessService);
        allServices.add(mCarPackageManagerService);
        allServices.add(mCarInputService);
        allServices.add(mGarageModeService);
        addServiceIfNonNull(allServices, mCarUserNoticeService);
        allServices.add(mAppFocusService);
        allServices.add(mCarAudioService);
        allServices.add(mCarNightService);
        allServices.add(mFixedActivityService);
        allServices.add(mClusterNavigationService);
        addServiceIfNonNull(allServices, mInstrumentClusterService);
        allServices.add(mPerUserCarServiceHelper);
        allServices.add(mCarBluetoothService);
        allServices.add(mCarProjectionService);
        addServiceIfNonNull(allServices, mCarDiagnosticService);
        addServiceIfNonNull(allServices, mCarStorageMonitoringService);
        addServiceIfNonNull(allServices, mVmsBrokerService);
        allServices.add(mCarMediaService);
        allServices.add(mCarLocationService);
        allServices.add(mCarBugreportManagerService);
        allServices.add(mCarWatchdogService);
        allServices.add(mCarPerformanceService);
        allServices.add(mCarDevicePolicyService);
        addServiceIfNonNull(allServices, mClusterHomeService);
        addServiceIfNonNull(allServices, mCarEvsService);
        addServiceIfNonNull(allServices, mCarTelemetryService);
        allServices.add(mCarActivityService);

        // Always put mCarExperimentalFeatureServiceController in last.
        addServiceIfNonNull(allServices, mCarExperimentalFeatureServiceController);
        mAllServices = allServices.toArray(new CarServiceBase[allServices.size()]);

        mICarSystemServerClientImpl = new ICarSystemServerClientImpl();

        t.traceEnd(); // "ICarImpl.constructor"
    }

    private void addServiceIfNonNull(List<CarServiceBase> services, CarServiceBase service) {
        if (service != null) {
            services.add(service);
        }
    }

    @MainThread
    void init() {
        LimitedTimingsTraceLog t = new LimitedTimingsTraceLog(CAR_SERVICE_INIT_TIMING_TAG,
                TraceHelper.TRACE_TAG_CAR_SERVICE, CAR_SERVICE_INIT_TIMING_MIN_DURATION_MS);

        t.traceBegin("ICarImpl.init");

        t.traceBegin("VHAL.init");
        mHal.init();
        t.traceEnd();

        t.traceBegin("CarService.initAllServices");
        for (CarServiceBase service : mAllServices) {
            t.traceBegin(service.getClass().getSimpleName());
            service.init();
            t.traceEnd();
        }
        t.traceBegin("CarOemService.initComplete");
        if (ENABLE_OEM_SERVICE) {
            mCarOemService.onInitComplete();
        }
        t.traceEnd();
        t.traceEnd(); // "CarService.initAllServices"
        t.traceEnd(); // "ICarImpl.init"
    }

    void release() {
        // release done in opposite order from init
        for (int i = mAllServices.length - 1; i >= 0; i--) {
            mAllServices[i].release();
        }
        mHal.release();
    }

    @Override
    public void setSystemServerConnections(ICarServiceHelper carServiceHelper,
            ICarResultReceiver resultReceiver) {
        Bundle bundle;
        try {
            EventLogHelper.writeCarServiceSetCarServiceHelper(Binder.getCallingPid());
            assertCallingFromSystemProcess();
            synchronized (mLock) {
                mICarServiceHelper = carServiceHelper;
            }
            // TODO(b/173030628) create a proxy wrapping access to CarServiceHelper instead
            mCarOccupantZoneService.setCarServiceHelper(carServiceHelper);
            mCarUserService.setCarServiceHelper(carServiceHelper);
            mCarActivityService.setICarServiceHelper(carServiceHelper);

            bundle = new Bundle();
            bundle.putBinder(ICAR_SYSTEM_SERVER_CLIENT, mICarSystemServerClientImpl.asBinder());
        } catch (Exception e) {
            // send back a null response
            Slogf.w(TAG, "Exception in setSystemServerConnections", e);
            bundle = null;
        }

        try {
            resultReceiver.send(/* unused */ 0, bundle);
        } catch (RemoteException e) {
            Slogf.w(TAG, "RemoteException from CarServiceHelperService", e);
        }
    }

    @Override
    public boolean isFeatureEnabled(String featureName) {
        return mFeatureController.isFeatureEnabled(featureName);
    }

    @Override
    public int enableFeature(String featureName) {
        // permission check inside the controller
        return mFeatureController.enableFeature(featureName);
    }

    @Override
    public int disableFeature(String featureName) {
        // permission check inside the controller
        return mFeatureController.disableFeature(featureName);
    }

    @Override
    public List<String> getAllEnabledFeatures() {
        // permission check inside the controller
        return mFeatureController.getAllEnabledFeatures();
    }

    @Override
    public List<String> getAllPendingDisabledFeatures() {
        // permission check inside the controller
        return mFeatureController.getAllPendingDisabledFeatures();
    }

    @Override
    public List<String> getAllPendingEnabledFeatures() {
        // permission check inside the controller
        return mFeatureController.getAllPendingEnabledFeatures();
    }

    @Override
    @Nullable
    public String getCarManagerClassForFeature(String featureName) {
        if (mCarExperimentalFeatureServiceController == null) {
            return null;
        }
        return mCarExperimentalFeatureServiceController.getCarManagerClassForFeature(featureName);
    }

    static void assertCallingFromSystemProcess() {
        int uid = Binder.getCallingUid();
        if (uid != Process.SYSTEM_UID) {
            throw new SecurityException("Only allowed from system");
        }
    }

    @Override
    @Nullable
    public IBinder getCarService(String serviceName) {
        if (!mFeatureController.isFeatureEnabled(serviceName)) {
            Slogf.w(CarLog.TAG_SERVICE, "getCarService for disabled service:" + serviceName);
            return null;
        }
        switch (serviceName) {
            case Car.AUDIO_SERVICE:
                return mCarAudioService;
            case Car.APP_FOCUS_SERVICE:
                return mAppFocusService;
            case Car.PACKAGE_SERVICE:
                return mCarPackageManagerService;
            case Car.DIAGNOSTIC_SERVICE:
                CarServiceUtils.assertAnyDiagnosticPermission(mContext);
                return mCarDiagnosticService;
            case Car.POWER_SERVICE:
                return mCarPowerManagementService;
            case Car.CABIN_SERVICE:
            case Car.HVAC_SERVICE:
            case Car.INFO_SERVICE:
            case Car.PROPERTY_SERVICE:
            case Car.SENSOR_SERVICE:
            case Car.VENDOR_EXTENSION_SERVICE:
                return mCarPropertyService;
            case Car.CAR_NAVIGATION_SERVICE:
                CarServiceUtils.assertNavigationManagerPermission(mContext);
                return mClusterNavigationService;
            case Car.CAR_INSTRUMENT_CLUSTER_SERVICE:
                CarServiceUtils.assertClusterManagerPermission(mContext);
                return mInstrumentClusterService.getManagerService();
            case Car.PROJECTION_SERVICE:
                return mCarProjectionService;
            case Car.VEHICLE_MAP_SERVICE:
                CarServiceUtils.assertAnyVmsPermission(mContext);
                return mVmsBrokerService;
            case Car.VMS_SUBSCRIBER_SERVICE:
                CarServiceUtils.assertVmsSubscriberPermission(mContext);
                return mVmsBrokerService;
            case Car.TEST_SERVICE: {
                CarServiceUtils.assertPermission(mContext, Car.PERMISSION_CAR_TEST_SERVICE);
                synchronized (mLock) {
                    if (mCarTestService == null) {
                        mCarTestService = new CarTestService(mContext, this);
                    }
                    return mCarTestService;
                }
            }
            case Car.STORAGE_MONITORING_SERVICE:
                CarServiceUtils.assertPermission(mContext, Car.PERMISSION_STORAGE_MONITORING);
                return mCarStorageMonitoringService;
            case Car.CAR_DRIVING_STATE_SERVICE:
                CarServiceUtils.assertDrivingStatePermission(mContext);
                return mCarDrivingStateService;
            case Car.CAR_UX_RESTRICTION_SERVICE:
                return mCarUXRestrictionsService;
            case Car.OCCUPANT_AWARENESS_SERVICE:
                return mOccupantAwarenessService;
            case Car.CAR_MEDIA_SERVICE:
                return mCarMediaService;
            case Car.CAR_OCCUPANT_ZONE_SERVICE:
                return mCarOccupantZoneService;
            case Car.CAR_BUGREPORT_SERVICE:
                return mCarBugreportManagerService;
            case Car.CAR_USER_SERVICE:
                return mCarUserService;
            case Car.EXPERIMENTAL_CAR_USER_SERVICE:
                return mExperimentalCarUserService;
            case Car.CAR_WATCHDOG_SERVICE:
                return mCarWatchdogService;
            case Car.CAR_PERFORMANCE_SERVICE:
                return mCarPerformanceService;
            case Car.CAR_INPUT_SERVICE:
                return mCarInputService;
            case Car.CAR_DEVICE_POLICY_SERVICE:
                return mCarDevicePolicyService;
            case Car.CLUSTER_HOME_SERVICE:
                return mClusterHomeService;
            case Car.CAR_EVS_SERVICE:
                return mCarEvsService;
            case Car.CAR_TELEMETRY_SERVICE:
                return mCarTelemetryService;
            case Car.CAR_ACTIVITY_SERVICE:
                return mCarActivityService;
            default:
                IBinder service = null;
                if (mCarExperimentalFeatureServiceController != null) {
                    service = mCarExperimentalFeatureServiceController.getCarService(serviceName);
                }
                if (service == null) {
                    Slogf.w(CarLog.TAG_SERVICE, "getCarService for unknown service:"
                            + serviceName);
                }
                return service;
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DEPRECATED_CODE)
    public int getCarConnectionType() {
        return Car.CONNECTION_TYPE_EMBEDDED;
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            writer.println("Permission Denial: can't dump CarService from from pid="
                    + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid()
                    + " without permission " + android.Manifest.permission.DUMP);
            return;
        }

        try (IndentingPrintWriter pw = new IndentingPrintWriter(writer)) {
            dumpIndenting(fd, pw, args);
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpIndenting(FileDescriptor fd, IndentingPrintWriter writer, String[] args) {
        if (args == null || args.length == 0 || (args.length > 0 && "-a".equals(args[0]))) {
            writer.println("*Dump car service*");
            dumpVersions(writer);
            dumpAllServices(writer);
            dumpAllHals(writer);
            dumpRROs(writer);
        } else if ("--list".equals(args[0])) {
            dumpListOfServices(writer);
            return;
        } else if ("--version".equals(args[0])) {
            dumpVersions(writer);
            return;
        } else if ("--services".equals(args[0])) {
            if (args.length < 2) {
                writer.println("Must pass services to dump when using --services");
                return;
            }
            int length = args.length - 1;
            String[] services = new String[length];
            System.arraycopy(args, 1, services, 0, length);
            dumpIndividualServices(writer, services);
            return;
        } else if ("--metrics".equals(args[0])) {
            // Strip the --metrics flag when passing dumpsys arguments to CarStatsService
            // allowing for nested flag selection
            mCarStatsService.dump(writer, Arrays.copyOfRange(args, 1, args.length));
        } else if ("--vms-hal".equals(args[0])) {
            mHal.getVmsHal().dumpMetrics(fd);
        } else if ("--hal".equals(args[0])) {
            if (args.length == 1) {
                dumpAllHals(writer);
                return;
            }

            int length = args.length - 1;
            String[] halNames = new String[length];
            System.arraycopy(args, 1, halNames, 0, length);
            mHal.dumpSpecificHals(writer, halNames);

        } else if ("--list-hals".equals(args[0])) {
            mHal.dumpListHals(writer);
            return;
        } else if ("--data-dir".equals(args[0])) {
            dumpDataDir(writer);
            return;
        } else if ("--oem-service".equals(args[0])) {
            if (args.length > 1 && args[1].equalsIgnoreCase("--name-only")) {
                writer.println(getOemServiceName());
            } else {
                dumpOemService(writer);
            }
            return;
        } else if ("--help".equals(args[0])) {
            showDumpHelp(writer);
        } else {
            execShellCmd(args, writer);
        }
    }

    private void dumpOemService(IndentingPrintWriter writer) {
        if (ENABLE_OEM_SERVICE) {
            mCarOemService.dump(writer);
            return;
        }

        writer.println("OEM service is disabled.");
    }

    public String getOemServiceName() {
        if (ENABLE_OEM_SERVICE) {
            return mCarOemService.getOemServiceName();
        }

        return "";
    }

    private void dumpAll(IndentingPrintWriter writer) {
        writer.println("*Dump car service*");
        dumpVersions(writer);
        dumpAllServices(writer);
        dumpAllHals(writer);
        dumpRROs(writer);
    }

    private void dumpRROs(IndentingPrintWriter writer) {
        writer.println("*Dump Car Service RROs*");

        String packageName = SystemProperties.get(
                PROPERTY_CAR_SERVICE_PACKAGE_NAME, /*def= */null);
        if (packageName == null) {
            writer.println("Car Service updatable package name is null.");
            return;
        }

        OverlayManager manager = mContext.getSystemService(OverlayManager.class);

        List<OverlayInfo> installedOverlaysForSystem = manager.getOverlayInfosForTarget(packageName,
                UserHandle.SYSTEM);
        writer.println("RROs for System User");
        for (int i = 0; i < installedOverlaysForSystem.size(); i++) {
            OverlayInfo overlayInfo = installedOverlaysForSystem.get(i);
            writer.printf("Overlay: %s, Enabled: %b \n", overlayInfo.getPackageName(),
                    overlayInfo.isEnabled());
        }

        int currentUser = ActivityManager.getCurrentUser();
        writer.printf("RROs for Current User: %d\n", currentUser);
        List<OverlayInfo> installedOverlaysForCurrentUser = manager.getOverlayInfosForTarget(
                packageName, UserHandle.of(currentUser));
        for (int i = 0; i < installedOverlaysForCurrentUser.size(); i++) {
            OverlayInfo overlayInfo = installedOverlaysForCurrentUser.get(i);
            writer.printf("Overlay: %s, Enabled: %b \n", overlayInfo.getPackageName(),
                    overlayInfo.isEnabled());
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpVersions(IndentingPrintWriter writer) {
        writer.println("*Dump versions*");
        writer.println("Android SDK_INT: " + Build.VERSION.SDK_INT);
        writer.println("Car Version: " + Car.getCarVersion());
        writer.println("Platform Version: " + Car.getPlatformVersion());
        writer.println("CarBuiltin Platform minor: " + CarBuiltin.PLATFORM_VERSION_MINOR_INT);
        writer.println("Legacy versions (might differ from above as they can't be emulated)");
        writer.increaseIndent();
        writer.println("Car API major: " + Car.API_VERSION_MAJOR_INT);
        writer.println("Car API minor: " + Car.API_VERSION_MINOR_INT);
        writer.println("Car Platform minor: " + Car.PLATFORM_VERSION_MINOR_INT);
        writer.decreaseIndent();
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpAllHals(IndentingPrintWriter writer) {
        writer.println("*Dump Vehicle HAL*");
        writer.println("Vehicle HAL Interface: " + mVehicleInterfaceName);
        try {
            // TODO dump all feature flags by creating a dumpable interface
            mHal.dump(writer);
        } catch (Exception e) {
            writer.println("Failed dumping: " + mHal.getClass().getName());
            e.printStackTrace(writer);
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void showDumpHelp(IndentingPrintWriter writer) {
        writer.println("Car service dump usage:");
        writer.println("[NO ARG]");
        writer.println("\t  dumps everything (all services and HALs)");
        writer.println("--help");
        writer.println("\t  shows this help");
        writer.println("--version");
        writer.println("\t  shows the version of all car components");
        writer.println("--list");
        writer.println("\t  lists the name of all services");
        writer.println("--list-hals");
        writer.println("\t  lists the name of all HALs");
        writer.println("--services <SVC1> [SVC2] [SVCN]");
        writer.println("\t  dumps just the specific services, where SVC is just the service class");
        writer.println("\t  name (like CarUserService)");
        writer.println("--vms-hal");
        writer.println("\t  dumps the VMS HAL metrics");
        writer.println("--hal [HAL1] [HAL2] [HALN]");
        writer.println("\t  dumps just the specified HALs (or all of them if none specified),");
        writer.println("\t  where HAL is just the class name (like UserHalService)");
        writer.println("--user-metrics");
        writer.println("\t  dumps user switching and stopping metrics");
        writer.println("--first-user-metrics");
        writer.println("\t  dumps how long it took to unlock first user since Android started\n");
        writer.println("\t  (or -1 if not unlocked)");
        writer.println("--data-dir");
        writer.println("\t  dumps CarService data dir (and whether it exists)");
        writer.println("-h");
        writer.println("\t  shows commands usage (NOTE: commands are not available on USER builds");
        writer.println("[ANYTHING ELSE]");
        writer.println("\t  runs the given command (use --h to see the available commands)");
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpDataDir(IndentingPrintWriter writer) {
        File dataDir = mContext.getDataDir();
        writer.printf("Data dir: %s Exists: %b\n", dataDir.getAbsolutePath(), dataDir.exists());
    }

    @Override
    public boolean onTransact(int code, @NonNull Parcel data, @Nullable Parcel reply,
            int flags) throws RemoteException {
        // Shell cmd is handled specially.
        if (BinderHelper.onTransactForCmd(code, data, reply, flags, mCmdListener)) {
            return true;
        }
        return super.onTransact(code, data, reply, flags);
    }

    private CarShellCommand newCarShellCommand() {
        return new CarShellCommand(mContext, mHal, mCarAudioService, mCarPackageManagerService,
                mCarProjectionService, mCarPowerManagementService, mFixedActivityService,
                mFeatureController, mCarInputService, mCarNightService, mSystemInterface,
                mGarageModeService, mCarUserService, mCarOccupantZoneService, mCarEvsService,
                mCarWatchdogService, mCarTelemetryService);
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpListOfServices(IndentingPrintWriter writer) {
        for (CarServiceBase service : mAllServices) {
            writer.println(service.getClass().getName());
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpAllServices(IndentingPrintWriter writer) {
        writer.println("*Dump all services*");
        for (CarServiceBase service : mAllServices) {
            dumpService(service, writer);
        }
        if (mCarTestService != null) {
            dumpService(mCarTestService, writer);
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpIndividualServices(IndentingPrintWriter writer, String... serviceNames) {
        for (String serviceName : serviceNames) {
            writer.printf("** Dumping %s\n\n", serviceName);
            CarServiceBase service = getCarServiceBySubstring(serviceName);
            if (service == null) {
                writer.println("No such service!");
            } else {
                dumpService(service, writer);
            }
            writer.println();
        }
    }

    @Nullable
    private CarServiceBase getCarServiceBySubstring(String className) {
        return Arrays.asList(mAllServices).stream()
                .filter(s -> s.getClass().getSimpleName().equals(className))
                .findFirst().orElse(null);
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpService(CarServiceBase service, IndentingPrintWriter writer) {
        try {
            service.dump(writer);
        } catch (Exception e) {
            writer.println("Failed dumping: " + service.getClass().getName());
            e.printStackTrace(writer);
        }
    }

    void execShellCmd(String[] args, IndentingPrintWriter writer) {
        newCarShellCommand().exec(args, writer);
    }

    private <T> T constructWithTrace(LimitedTimingsTraceLog t, Class<T> cls, Callable<T> callable) {
        t.traceBegin(cls.getSimpleName());
        T constructed;
        try {
            constructed = callable.call();
            CarLocalServices.addService(cls, constructed);
        } catch (Exception e) {
            throw new RuntimeException("Crash while constructing:" + cls.getSimpleName(), e);
        } finally {
            t.traceEnd();
        }
        return constructed;
    }

    private final class ICarSystemServerClientImpl extends ICarSystemServerClient.Stub {
        @Override
        public void onUserLifecycleEvent(int eventType, int fromUserId, int toUserId)
                throws RemoteException {
            assertCallingFromSystemProcess();
            EventLogHelper.writeCarServiceOnUserLifecycle(eventType, fromUserId, toUserId);
            if (DBG) {
                Slogf.d(TAG,
                        "onUserLifecycleEvent("
                                + CarUserManager.lifecycleEventTypeToString(eventType) + ", "
                                + toUserId + ")");
            }
            mCarUserService.onUserLifecycleEvent(eventType, fromUserId, toUserId);
        }

        @Override
        public void initBootUser() throws RemoteException {
            assertCallingFromSystemProcess();
            EventLogHelper.writeCarServiceInitBootUser();
            if (DBG) Slogf.d(TAG, "initBootUser(): ");
            mCarUserService.initBootUser();
        }

        // TODO(235524989): Remove this method as on user removed will now go through
        // onUserLifecycleEvent due to changes in CarServiceProxy and CarUserService.
        @Override
        public void onUserRemoved(UserHandle user) throws RemoteException {
            assertCallingFromSystemProcess();
            EventLogHelper.writeCarServiceOnUserRemoved(user.getIdentifier());
            if (DBG) Slogf.d(TAG, "onUserRemoved(): " + user.toString());
            mCarUserService.onUserRemoved(user);
        }

        @Override
        public void onFactoryReset(ICarResultReceiver callback) {
            assertCallingFromSystemProcess();

            mCarPowerManagementService.setFactoryResetCallback(callback);
            BuiltinPackageDependency.createNotificationHelper(mCarServiceBuiltinPackageContext)
                    .showFactoryResetNotification(callback);
        }

        @Override
        public void setInitialUser(UserHandle user) {
            mCarUserService.setInitialUserFromSystemServer(user);
        }
    }
}
