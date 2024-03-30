#
# Copyright (C) 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

PRODUCT_ENFORCE_ARTIFACT_PATH_REQUIREMENTS := relaxed
PRODUCT_PACKAGES += android.frameworks.automotive.display@1.0-service

# TODO(b/193056430): Improve this temp fix for "artifact path requirement"
#                    errors.
# TODO(b/194941497): SHIPSTOP - remove CarServiceModule
PRODUCT_ARTIFACT_PATH_REQUIREMENT_ALLOWED_LIST += \
    root/init.bootstat.rc \
    root/init.car.rc \
    system/apex/com.android.car.framework.apex \
    system/app/Bluetooth/Bluetooth.apk \
    system/app/Bluetooth/lib/arm64/libbluetooth_jni.so \
    system/app/Bluetooth/lib/x86_64/libbluetooth_jni.so \
    system/app/CarFrameworkPackageStubs/CarFrameworkPackageStubs.apk \
    system/app/CarHTMLViewer/CarHTMLViewer.apk \
    system/app/CarLatinIME/CarLatinIME.apk \
    system/app/CarMapsPlaceholder/CarMapsPlaceholder.apk \
    system/app/CarPermissionControllerRRO/CarPermissionControllerRRO.apk \
    system/app/CarRotaryImeRRO/CarRotaryImeRRO.apk \
    system/app/HideCameraApps/HideCameraApps.apk \
    system/app/RotaryIME/RotaryIME.apk \
    system/app/RotaryPlayground/RotaryPlayground.apk \
    system/app/SampleCustomInputService/SampleCustomInputService.apk \
    system/app/SampleRearViewCamera/SampleRearViewCamera.apk \
    system/app/SystemUpdater/SystemUpdater.apk \
    system/bin/android.automotive.evs.manager@1.1 \
    system/bin/canhalconfigurator \
    system/bin/canhalctrl \
    system/bin/canhaldump \
    system/bin/canhalsend \
    system/bin/carbugreportd \
    system/bin/carpowerpolicyd \
    system/bin/carwatchdogd \
    system/bin/com.android.car.procfsinspector \
    system/bin/curl \
    system/bin/evsmanagerd \
    system/bin/vehicle_binding_util \
    system/etc/apns-conf.xml \
    system/etc/canbus_config.pb \
    system/etc/init/android.automotive.evs.manager@1.1.rc \
    system/etc/init/canhalconfigurator.rc \
    system/etc/init/carbugreportd.rc \
    system/etc/init/carpowerpolicyd.rc \
    system/etc/init/carwatchdogd.rc \
    system/etc/init/com.android.car.procfsinspector.rc \
    system/etc/init/evsmanagerd.rc \
    system/etc/init/init.bootstat.car.rc \
    system/etc/init/init.car.rc \
    system/etc/init/vehicle_binding_util.rc \
    system/etc/old-apns-conf.xml \
    system/etc/permissions/android.car.cluster.xml \
    system/etc/permissions/android.car.usb.handler.xml \
    system/etc/permissions/android.hardware.broadcastradio.xml \
    system/etc/permissions/android.hardware.type.automotive.xml \
    system/etc/permissions/car-frameworks-service.xml \
    system/etc/permissions/com.android.car.activityresolver.xml \
    system/etc/permissions/com.android.car.bugreport.xml \
    system/etc/permissions/com.android.car.cartelemetryapp.xml \
    system/etc/permissions/com.android.car.carlauncher.xml \
    system/etc/permissions/com.android.car.cluster.home.xml \
    system/etc/permissions/com.android.car.dialer.xml \
    system/etc/permissions/com.android.car.hvac.xml \
    system/etc/permissions/com.android.car.media.xml \
    system/etc/permissions/com.android.car.messenger.xml \
    system/etc/permissions/com.android.car.oemcarservice.testapp.xml \
    system/etc/permissions/com.android.car.radio.xml \
    system/etc/permissions/com.android.car.rotary.xml \
    system/etc/permissions/com.android.car.settings.xml \
    system/etc/permissions/com.android.car.shell.xml \
    system/etc/permissions/com.android.car.xml \
    system/etc/permissions/com.google.android.car.adaslocation.xml \
    system/etc/permissions/com.google.android.car.defaultstoragemonitoringcompanionapp.xml \
    system/etc/permissions/com.google.android.car.garagemode.testapp.xml \
    system/etc/permissions/com.google.android.car.kitchensink.xml \
    system/etc/permissions/com.google.android.car.networking.preferenceupdater.xml \
    system/etc/permissions/com.google.android.car.networking.railway.xml \
    system/etc/sysconfig/preinstalled-packages-product-car-base.xml \
    system/etc/sysconfig/preinstalled-packages-com.android.car.dialer.xml \
    system/etc/sysconfig/preinstalled-packages-com.android.car.messenger.xml \
    system/etc/vintf/manifest/carpowerpolicyd.xml \
    system/etc/vintf/manifest/carwatchdogd.xml \
    system/etc/vintf/manifest/manifest_android.automotive.evs.manager@1.1.xml \
    system/etc/vintf/manifest/manifest_evsmanagerd.xml \
    system/framework/android.car.jar \
    system/framework/android.car.builtin.jar \
    system/framework/car-frameworks-service.jar \
    system/framework/car-frameworks-service-module.jar \
    system/lib/libsuspend.so \
    system/lib64/android.automotive.watchdog-V3-cpp.so \
    system/lib64/android.automotive.watchdog.internal-cpp.so \
    system/lib64/android.frameworks.automotive.display@1.0.so \
    system/lib64/android.frameworks.automotive.powerpolicy-V1-cpp.so \
    system/lib64/android.frameworks.automotive.powerpolicy-V1-ndk.so \
    system/lib64/android.frameworks.automotive.powerpolicy.internal-V1-cpp.so \
    system/lib64/android.frameworks.automotive.powerpolicy.internal-V1-ndk.so \
    system/lib64/android.hardware.automotive.can@1.0.so \
    system/lib64/android.hardware.automotive.evs@1.0.so \
    system/lib64/android.hardware.automotive.evs@1.1.so \
    system/lib64/android.hardware.automotive.evs-V1-ndk.so \
    system/lib64/android.hardware.automotive.vehicle@2.0.so \
    system/lib64/android.os.statsbootstrap_aidl-cpp.so \
    system/lib64/libbluetooth_jni.so \
    system/lib64/libstatsbootstrap.so \
    system/lib64/libstatslog_evs.so \
    system/lib64/libstatslog_evsmanagerd.so \
    system/lib64/libsuspend.so \
    system/lib64/libwatchdog_binder_mediator.so \
    system/lib64/libwatchdog_package_info_resolver.so \
    system/lib64/libwatchdog_perf_service.so \
    system/lib64/libwatchdog_process_service.so \
    system/media/bootanimation.zip \
    system/priv-app/AdasLocationTestApp/AdasLocationTestApp.apk \
    system/priv-app/BugReportApp/BugReportApp.apk \
    system/priv-app/CarActivityResolver/CarActivityResolver.apk \
    system/priv-app/CarDialerApp/CarDialerApp.apk \
    system/priv-app/CarHvacApp/CarHvacApp.apk \
    system/priv-app/CarLauncher/CarLauncher.apk \
    system/priv-app/CarManagedProvisioning/CarManagedProvisioning.apk \
    system/priv-app/CarMediaApp/CarMediaApp.apk \
    system/priv-app/CarMessengerApp/CarMessengerApp.apk \
    system/priv-app/CarRadioApp/CarRadioApp.apk \
    system/priv-app/CarRotaryController/CarRotaryController.apk \
    system/priv-app/CarService/CarService.apk \
    system/priv-app/CarService/lib/arm64/libcarservicejni.so \
    system/priv-app/CarService/lib/x86_64/libcarservicejni.so \
    system/priv-app/CarSettings/CarSettings.apk \
    system/priv-app/CarShell/CarShell.apk \
    system/priv-app/CarTelemetryApp/CarTelemetryApp.apk \
    system/priv-app/CarUsbHandler/CarUsbHandler.apk \
    system/priv-app/ClusterHomeSample/ClusterHomeSample.apk \
    system/priv-app/DefaultStorageMonitoringCompanionApp/DefaultStorageMonitoringCompanionApp.apk \
    system/priv-app/DirectRenderingCluster/DirectRenderingCluster.apk \
    system/priv-app/EmbeddedKitchenSinkApp/EmbeddedKitchenSinkApp.apk \
    system/priv-app/ExperimentalCarService/ExperimentalCarService.apk \
    system/priv-app/GarageModeTestApp/GarageModeTestApp.apk \
    system/priv-app/LocalMediaPlayer/LocalMediaPlayer.apk \
    system/priv-app/NetworkPreferenceApp/NetworkPreferenceApp.apk \
    system/priv-app/OemCarServiceTestApp/OemCarServiceTestApp.apk \
    system/priv-app/RailwayReferenceApp/RailwayReferenceApp.apk \
    system/priv-app/RotaryIME/RotaryIME.apk \
    system/priv-app/RotaryPlayground/RotaryPlayground.apk \
    system/lib64/libcarservicejni.so \
    system/bin/android.automotive.telemetryd@1.0 \
    system/etc/init/android.automotive.telemetryd@1.0.rc \
    system/etc/vintf/manifest/android.automotive.telemetryd@1.0.xml \
    system/lib64/android.automotive.telemetryd@1.0-impl.so \
    system/lib64/android.frameworks.automotive.telemetry-V1-cpp.so \
    system/lib64/android.automotive.telemetry.internal-ndk.so \
    system/lib64/android.frameworks.automotive.telemetry-V1-ndk.so \
    system/etc/automotive/watchdog/system_resource_overuse_configuration.xml \
    system/etc/automotive/watchdog/third_party_resource_overuse_configuration.xml \
    system/bin/android.frameworks.automotive.display@1.0-service \
    system/etc/init/android.frameworks.automotive.display@1.0-service.rc \
    system/etc/vintf/manifest/manifest_android.frameworks.automotive.display@1.0.xml \

PRODUCT_ARTIFACT_PATH_REQUIREMENT_ALLOWED_LIST += %.odex %.vdex %.art

PRODUCT_PACKAGE_OVERLAYS := device/generic/car/common/overlay
EMULATOR_VENDOR_NO_SENSORS := true
PRODUCT_USE_DYNAMIC_PARTITION_SIZE := true
DO_NOT_INCLUDE_BT_SEPOLICY := true
$(call inherit-product, device/generic/car/emulator/aosp_car_emulator.mk)
EMULATOR_VENDOR_NO_SOUND := true
