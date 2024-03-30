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
package android.angle.cts;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.PackageInfo;

import java.util.HashMap;
import java.util.Map;

class CtsAngleCommon {
    // General
    static final int NUM_ATTEMPTS = 5;
    static final int REATTEMPT_SLEEP_MSEC = 5000;

    // Settings.Global
    static final String SETTINGS_GLOBAL_ALL_USE_ANGLE = "angle_gl_driver_all_angle";
    static final String SETTINGS_GLOBAL_DRIVER_PKGS = "angle_gl_driver_selection_pkgs";
    static final String SETTINGS_GLOBAL_DRIVER_VALUES = "angle_gl_driver_selection_values";
    static final String SETTINGS_GLOBAL_ALLOWLIST = "angle_allowlist";
    static final String SETTINGS_GLOBAL_ANGLE_IN_USE_DIALOG_BOX = "show_angle_in_use_dialog_box";

    // System Properties
    static final String PROPERTY_TEMP_RULES_FILE = "debug.angle.rules";

    // ANGLE
    static final String ANGLE_PACKAGE_NAME = "com.android.angle";
    static final String ANGLE_DRIVER_TEST_PKG = "com.android.angleintegrationtest.drivertest";
    static final String ANGLE_DRIVER_TEST_SEC_PKG =
            "com.android.angleintegrationtest.drivertestsecondary";
    static final String ANGLE_GAME_DRIVER_TEST_PKG =
            "com.android.angleintegrationtest.gamedrivertest";
    static final String ANGLE_DRIVER_TEST_CLASS = "AngleDriverTestActivity";
    static final String ANGLE_DRIVER_TEST_DEFAULT_METHOD = "testUseDefaultDriver";
    static final String ANGLE_DRIVER_TEST_ANGLE_METHOD = "testUseAngleDriver";
    static final String ANGLE_DRIVER_TEST_NATIVE_METHOD = "testUseNativeDriver";
    static final String ANGLE_DRIVER_TEST_APP = "CtsAngleDriverTestCases.apk";
    static final String ANGLE_DRIVER_TEST_SEC_APP = "CtsAngleDriverTestCasesSecondary.apk";
    static final String ANGLE_GAME_DRIVER_TEST_APP = "CtsAngleGameDriverTestCases.apk";
    static final String ANGLE_DUMPSYS_GPU_TEST_PKG =
            "com.android.angleintegrationtest.dumpsysgputest";
    static final String ANGLE_DUMPSYS_GPU_TEST_CLASS = "AngleDumpsysGpuTestActivity";
    static final String ANGLE_DUMPSYS_GPU_TEST_APP = "CtsAngleDumpsysGpuTestApp.apk";
    static final String ANGLE_DRIVER_TEST_ACTIVITY =
            ANGLE_DRIVER_TEST_PKG
                    + "/com.android.angleIntegrationTest.common.AngleIntegrationTestActivity";
    static final String ANGLE_DRIVER_TEST_SEC_ACTIVITY =
            ANGLE_DRIVER_TEST_SEC_PKG
                    + "/com.android.angleIntegrationTest.common.AngleIntegrationTestActivity";

    enum OpenGlDriverChoice {
        DEFAULT,
        NATIVE,
        ANGLE
    }

    static final Map<OpenGlDriverChoice, String> sDriverGlobalSettingMap =
            buildDriverGlobalSettingMap();

    static Map<OpenGlDriverChoice, String> buildDriverGlobalSettingMap() {
        Map<OpenGlDriverChoice, String> map = new HashMap<>();
        map.put(OpenGlDriverChoice.DEFAULT, "default");
        map.put(OpenGlDriverChoice.ANGLE, "angle");
        map.put(OpenGlDriverChoice.NATIVE, "native");

        return map;
    }

    static final Map<OpenGlDriverChoice, String> sDriverTestMethodMap = buildDriverTestMethodMap();

    static Map<OpenGlDriverChoice, String> buildDriverTestMethodMap() {
        Map<OpenGlDriverChoice, String> map = new HashMap<>();
        map.put(OpenGlDriverChoice.DEFAULT, ANGLE_DRIVER_TEST_DEFAULT_METHOD);
        map.put(OpenGlDriverChoice.ANGLE, ANGLE_DRIVER_TEST_ANGLE_METHOD);
        map.put(OpenGlDriverChoice.NATIVE, ANGLE_DRIVER_TEST_NATIVE_METHOD);

        return map;
    }

    static String getGlobalSetting(ITestDevice device, String globalSetting) throws Exception {
        return device.getSetting("global", globalSetting);
    }

    static void setGlobalSetting(ITestDevice device, String globalSetting, String value)
            throws Exception {
        device.setSetting("global", globalSetting, value);
        device.executeShellCommand("am refresh-settings-cache");
    }

    static void clearSettings(ITestDevice device) throws Exception {
        // Cached Activity Manager settings
        setGlobalSetting(device, SETTINGS_GLOBAL_ALL_USE_ANGLE, "0");
        setGlobalSetting(device, SETTINGS_GLOBAL_ANGLE_IN_USE_DIALOG_BOX, "0");
        setGlobalSetting(device, SETTINGS_GLOBAL_DRIVER_PKGS, "\"\"");
        setGlobalSetting(device, SETTINGS_GLOBAL_DRIVER_VALUES, "\"\"");
        setGlobalSetting(device, SETTINGS_GLOBAL_ALLOWLIST, "\"\"");

        // Properties
        setProperty(device, PROPERTY_TEMP_RULES_FILE, "\"\"");
    }

    static boolean isAngleInstalled(ITestDevice device) throws Exception {
        PackageInfo info = device.getAppPackageInfo(ANGLE_PACKAGE_NAME);

        return (info != null);
    }

    static boolean isNativeDriverAngle(ITestDevice device) throws Exception {
        String driverProp = device.getProperty("ro.hardware.egl");

        return (driverProp != null) && (driverProp.equals("angle"));
    }

    static void startActivity(ITestDevice device, String pkgName, String className)
            throws Exception {
        String startCommand = String.format(
                "am start -W -a android.intent.action.MAIN -n %s/.%s", pkgName, className);
        device.executeShellCommand(startCommand);
    }

    static void stopPackage(ITestDevice device, String pkgName) throws Exception {
        device.executeShellCommand("am force-stop " + pkgName);
    }

    /**
     * Work around the fact that INativeDevice.enableAdbRoot() is not supported.
     */
    static void setProperty(ITestDevice device, String property, String value) throws Exception {
        device.executeShellCommand("setprop " + property + " " + value);
    }

    static void setGameModeBatteryConfig(ITestDevice device, String packageName, boolean useAngle)
            throws Exception {
        device.executeShellCommand("device_config put game_overlay " + packageName
                + " mode=3,useAngle=" + Boolean.toString(useAngle));
    }

    static void setGameModeStandardConfig(ITestDevice device, String packageName, boolean useAngle)
            throws Exception {
        device.executeShellCommand("device_config put game_overlay " + packageName
                + " mode=1,useAngle=" + Boolean.toString(useAngle));
    }

    static void setGameModeBattery(ITestDevice device, String packageName) throws Exception {
        device.executeShellCommand("cmd game mode battery " + packageName);
    }

    static void setGameModeStandard(ITestDevice device, String packageName) throws Exception {
        device.executeShellCommand("cmd game mode standard " + packageName);
    }

    /**
     * Find and parse the `dumpsys gpu` output for the specified package.
     *
     * Sample output:
     *      appPackageName = com.android.angleIntegrationTest.dumpsysGpuTest
     *      driverVersionCode = 0
     *      cpuVulkanInUse = 0
     *      falsePrerotation = 0
     *      gles1InUse = 0
     *      angleInUse = 1
     *      glDriverLoadingTime:
     *      angleDriverLoadingTime:
     *      vkDriverLoadingTime: 37390096
     *
     * @return angleInUse, -1 on error
     */
    static int getDumpsysGpuAngleInUse(ITestDevice device, String packageName) throws Exception {
        String dumpSysGpu = device.executeShellCommand("dumpsys gpu");
        String[] lines = dumpSysGpu.split("\n");

        boolean foundPackage = false;
        for (String s : lines) {
            String line = s.trim();
            if (!foundPackage && line.contains(packageName)) {
                foundPackage = true;
                continue;
            }

            if (foundPackage) {
                if (line.contains("angleInUse")) {
                    String[] tokens = line.split(" ");
                    if (tokens.length != 3) {
                        throw new IllegalArgumentException(
                                "Malformed result: tokens.length = " + tokens.length);
                    }

                    return Integer.parseInt(tokens[2]);
                } else if (line.contains("appPackageName")) {
                    // We've moved to another block for a different package without finding the
                    // 'angleInUse' field, so return an error.
                    throw new IllegalArgumentException("Failed to find field: angleInUse");
                }
            }
        }

        // Didn't find the specified package, return an error.
        return -1;
    }
}
