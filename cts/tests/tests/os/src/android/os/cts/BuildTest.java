/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.os.cts;

import static android.os.Build.VERSION.ACTIVE_CODENAMES;
import static android.os.Build.VERSION_CODES.CUR_DEVELOPMENT;

import android.os.Build;
import android.os.SystemProperties;
import android.platform.test.annotations.AppModeFull;

import junit.framework.TestCase;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class BuildTest extends TestCase {

    private static final String RO_PRODUCT_CPU_ABILIST = "ro.product.cpu.abilist";
    private static final String RO_PRODUCT_CPU_ABILIST32 = "ro.product.cpu.abilist32";
    private static final String RO_PRODUCT_CPU_ABILIST64 = "ro.product.cpu.abilist64";

    /**
     * Verify that the values of the various CPU ABI fields are consistent.
     */
    @AppModeFull(reason = "Instant apps cannot access APIs")
    public void testCpuAbi() throws Exception {
        runTestCpuAbiCommon();
        if (android.os.Process.is64Bit()) {
            runTestCpuAbi64();
        } else {
            runTestCpuAbi32();
        }
    }

    /**
     * Verify that the CPU ABI fields on device match the permitted ABIs defined by CDD.
     */
    public void testCpuAbi_valuesMatchPermitted() throws Exception {
        // The permitted ABIs are listed in https://developer.android.com/ndk/guides/abis.
        Set<String> just32 = new HashSet<>(Arrays.asList("armeabi", "armeabi-v7a", "x86"));
        Set<String> just64 = new HashSet<>(Arrays.asList("x86_64", "arm64-v8a"));
        Set<String> all = new HashSet<>();
        all.addAll(just32);
        all.addAll(just64);
        Set<String> allAndEmpty = new HashSet<>(all);
        allAndEmpty.add("");

        // The cpu abi fields on the device must match the permitted values.
        assertValueIsAllowed(all, Build.CPU_ABI);
        // CPU_ABI2 will be empty when the device does not support a secondary CPU architecture.
        assertValueIsAllowed(allAndEmpty, Build.CPU_ABI2);

        // The supported abi fields on the device must match the permitted values.
        assertValuesAreAllowed(all, Build.SUPPORTED_ABIS);
        assertValuesAreAllowed(just32, Build.SUPPORTED_32_BIT_ABIS);
        assertValuesAreAllowed(just64, Build.SUPPORTED_64_BIT_ABIS);
    }

    private void runTestCpuAbiCommon() throws Exception {
        // The build property must match Build.SUPPORTED_ABIS exactly.
        final String[] abiListProperty = getStringList(RO_PRODUCT_CPU_ABILIST);
        assertEquals(Arrays.toString(abiListProperty), Arrays.toString(Build.SUPPORTED_ABIS));

        List<String> abiList = Arrays.asList(abiListProperty);

        // Every supported 32 bit ABI must be present in Build.SUPPORTED_ABIS.
        for (String abi : Build.SUPPORTED_32_BIT_ABIS) {
            assertTrue(abiList.contains(abi));
            assertFalse(Build.is64BitAbi(abi));
        }

        // Every supported 64 bit ABI must be present in Build.SUPPORTED_ABIS.
        for (String abi : Build.SUPPORTED_64_BIT_ABIS) {
            assertTrue(abiList.contains(abi));
            assertTrue(Build.is64BitAbi(abi));
        }

        // Build.CPU_ABI and Build.CPU_ABI2 must be present in Build.SUPPORTED_ABIS.
        assertTrue(abiList.contains(Build.CPU_ABI));
        if (!Build.CPU_ABI2.isEmpty()) {
            assertTrue(abiList.contains(Build.CPU_ABI2));
        }
    }

    private void runTestCpuAbi32() throws Exception {
        List<String> abi32 = Arrays.asList(Build.SUPPORTED_32_BIT_ABIS);
        assertTrue(abi32.contains(Build.CPU_ABI));

        if (!Build.CPU_ABI2.isEmpty()) {
            assertTrue(abi32.contains(Build.CPU_ABI2));
        }
    }

    private void runTestCpuAbi64() {
        List<String> abi64 = Arrays.asList(Build.SUPPORTED_64_BIT_ABIS);
        assertTrue(abi64.contains(Build.CPU_ABI));

        if (!Build.CPU_ABI2.isEmpty()) {
            assertTrue(abi64.contains(Build.CPU_ABI2));
        }
    }

    private String[] getStringList(String property) throws IOException {
        String value = getProperty(property);
        if (value.isEmpty()) {
            return new String[0];
        } else {
            return value.split(",");
        }
    }

    /**
     * @param property name passed to getprop
     */
    static String getProperty(String property)
            throws IOException {
        Process process = new ProcessBuilder("getprop", property).start();
        Scanner scanner = null;
        String line = "";
        try {
            scanner = new Scanner(process.getInputStream());
            line = scanner.nextLine();
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
        return line;
    }

    private static void assertValueIsAllowed(Set<String> allowedValues, String actualValue) {
        assertTrue("Expected one of " + allowedValues + ", but was: '" + actualValue + "'",
                allowedValues.contains(actualValue));
    }

    private static void assertValuesAreAllowed(Set<String> allowedValues, String[] actualValues) {
        for (String actualValue : actualValues) {
            assertValueIsAllowed(allowedValues, actualValue);
        }
    }

    private static final Pattern BOARD_PATTERN =
        Pattern.compile("^([0-9A-Za-z._-]+)$");
    private static final Pattern BRAND_PATTERN =
        Pattern.compile("^([0-9A-Za-z._-]+)$");
    private static final Pattern DEVICE_PATTERN =
        Pattern.compile("^([0-9A-Za-z._-]+)$");
    private static final Pattern ID_PATTERN =
        Pattern.compile("^([0-9A-Za-z._-]+)$");
    private static final Pattern HARDWARE_PATTERN =
        Pattern.compile("^([0-9A-Za-z.,_-]+)$");
    private static final Pattern PRODUCT_PATTERN =
        Pattern.compile("^([0-9A-Za-z._-]+)$");
    private static final Pattern SOC_MANUFACTURER_PATTERN =
        Pattern.compile("^([0-9A-Za-z ]+)$");
    private static final Pattern SOC_MODEL_PATTERN =
        Pattern.compile("^([0-9A-Za-z ._/+-]+)$");
    private static final Pattern SERIAL_NUMBER_PATTERN =
        Pattern.compile("^([0-9A-Za-z]{6,20})$");
    private static final Pattern SKU_PATTERN =
        Pattern.compile("^([0-9A-Za-z.,_-]+)$");
    private static final Pattern TAGS_PATTERN =
        Pattern.compile("^([0-9A-Za-z.,_-]+)$");
    private static final Pattern TYPE_PATTERN =
        Pattern.compile("^([0-9A-Za-z._-]+)$");

    /** Tests that check for valid values of constants in Build. */
    public void testBuildConstants() {
        // Build.VERSION.* constants tested by BuildVersionTest

        assertTrue(BOARD_PATTERN.matcher(Build.BOARD).matches());

        assertTrue(BRAND_PATTERN.matcher(Build.BRAND).matches());

        assertTrue(DEVICE_PATTERN.matcher(Build.DEVICE).matches());

        // Build.FINGERPRINT tested by BuildVersionTest

        assertTrue(HARDWARE_PATTERN.matcher(Build.HARDWARE).matches());

        assertNotEmpty(Build.HOST);

        assertTrue(ID_PATTERN.matcher(Build.ID).matches());

        assertNotEmpty(Build.MANUFACTURER);

        assertNotEmpty(Build.MODEL);

        assertEquals(Build.SOC_MANUFACTURER, Build.SOC_MANUFACTURER.trim());
        assertTrue(SOC_MANUFACTURER_PATTERN.matcher(Build.SOC_MANUFACTURER).matches());
        if (getVendorPartitionVersion() > Build.VERSION_CODES.R) {
            assertFalse(Build.SOC_MANUFACTURER.equals(Build.UNKNOWN));
        }

        assertEquals(Build.SOC_MODEL, Build.SOC_MODEL.trim());
        assertTrue(SOC_MODEL_PATTERN.matcher(Build.SOC_MODEL).matches());
        if (getVendorPartitionVersion() > Build.VERSION_CODES.R) {
            assertFalse(Build.SOC_MODEL.equals(Build.UNKNOWN));
        }

        assertTrue(PRODUCT_PATTERN.matcher(Build.PRODUCT).matches());

        assertTrue(SERIAL_NUMBER_PATTERN.matcher(Build.SERIAL).matches());

        assertTrue(SKU_PATTERN.matcher(Build.SKU).matches());

        assertTrue(SKU_PATTERN.matcher(Build.ODM_SKU).matches());

        assertTrue(TAGS_PATTERN.matcher(Build.TAGS).matches());

        // No format requirements stated in CDD for Build.TIME

        assertTrue(TYPE_PATTERN.matcher(Build.TYPE).matches());

        assertNotEmpty(Build.USER);
    }

    /**
     * Tests that check for valid values of codenames related constants.
     */
    public void testBuildCodenameConstants() {
        // CUR_DEVELOPMENT must be larger than any released version.
        Field[] fields = Build.VERSION_CODES.class.getDeclaredFields();
        List<String> activeCodenames = Arrays.asList(ACTIVE_CODENAMES);
        // Make the codenames uppercase to match the field names.
        activeCodenames.replaceAll(String::toUpperCase);
        Set<String> knownCodenames = Build.VERSION.KNOWN_CODENAMES.stream()
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
        HashSet<String> declaredCodenames = new HashSet<>();
        for (Field field : fields) {
            if (field.getType().equals(int.class) && Modifier.isStatic(field.getModifiers())) {
                String fieldName = field.getName();
                final int fieldValue;
                try {
                    fieldValue = field.getInt(null);
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e.getMessage());
                }
                declaredCodenames.add(fieldName);
                if (fieldName.equals("CUR_DEVELOPMENT")) {
                    // It should be okay to change the value of this constant in future, but it
                    // should at least be a conscious decision.
                    assertEquals(10000, fieldValue);
                } else {
                    if (activeCodenames.contains(fieldName)) {
                        // This is the current development version. Note that fieldName can
                        // become < CUR_DEVELOPMENT before CODENAME becomes "REL", so we
                        // can't assertEquals(CUR_DEVELOPMENT, fieldValue) here.
                        assertTrue("Expected " + fieldName + " value to be <= " + CUR_DEVELOPMENT
                                + ", got " + fieldValue, fieldValue <= CUR_DEVELOPMENT);
                    } else {
                        assertTrue("Expected " + fieldName + " value to be < " + CUR_DEVELOPMENT
                                + ", got " + fieldValue, fieldValue < CUR_DEVELOPMENT);
                    }
                    // Remove all underscores to match build level codenames, e.g. S_V2 is Sv2.
                    String name = fieldName.replaceAll("_", "");
                    declaredCodenames.add(name);
                    assertTrue("Expected " + name
                                        + " to be declared in Build.VERSION.KNOWN_CODENAMES",
                            knownCodenames.contains(name));
                }
            }
        }

        HashSet<String> diff = new HashSet<>(knownCodenames);
        diff.removeAll(declaredCodenames);
        assertTrue(
                "Expected all elements in Build.VERSION.KNOWN_CODENAMES to be declared in"
                        + " Build.VERSION_CODES, found " + diff, diff.isEmpty());

        if (!Build.VERSION.CODENAME.equals("REL")) {
            assertTrue("In-development CODENAME must be declared in Build.VERSION.KNOWN_CODENAMES",
                Build.VERSION.KNOWN_CODENAMES.contains(Build.VERSION.CODENAME));
        }
    }

    /**
     * Verify that SDK versions are bounded by both high and low expected
     * values.
     */
    public void testSdkInt() {
        assertTrue(
                "Current SDK version " + Build.VERSION.SDK_INT
                        + " is invalid; must be at least VERSION_CODES.BASE",
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.BASE);
        assertTrue(
                "First SDK version " + Build.VERSION.DEVICE_INITIAL_SDK_INT
                        + " is invalid; must be at least VERSION_CODES.BASE",
                Build.VERSION.DEVICE_INITIAL_SDK_INT >= Build.VERSION_CODES.BASE);
        assertTrue(
                "Current SDK version " + Build.VERSION.SDK_INT
                        + " must be at least first SDK version "
                        + Build.VERSION.DEVICE_INITIAL_SDK_INT,
                Build.VERSION.SDK_INT >= Build.VERSION.DEVICE_INITIAL_SDK_INT);
    }

    /**
     * Verify that MEDIA_PERFORMANCE_CLASS are bounded by both high and low expected values.
     */
    public void testMediaPerformanceClass() {
        // media performance class value of 0 is valid
        if (Build.VERSION.MEDIA_PERFORMANCE_CLASS == 0) {
            return;
        }

        assertTrue(
                "Media Performance Class " + Build.VERSION.MEDIA_PERFORMANCE_CLASS
                        + " is invalid; must be at least VERSION_CODES.R",
                Build.VERSION.MEDIA_PERFORMANCE_CLASS >= Build.VERSION_CODES.R);
        assertTrue(
                "Media Performance Class " + Build.VERSION.MEDIA_PERFORMANCE_CLASS
                        + " is invalid; must be at most VERSION.SDK_INT",
                // we use RESOURCES_SDK_INT to account for active development versions
                Build.VERSION.MEDIA_PERFORMANCE_CLASS <= Build.VERSION.RESOURCES_SDK_INT);
    }

    private void assertNotEmpty(String value) {
        assertNotNull(value);
        assertFalse(value.isEmpty());
    }

    private int getVendorPartitionVersion() {
        String version = SystemProperties.get("ro.vndk.version");
        try {
            return Integer.parseInt(version);
        } catch (NumberFormatException ignore) {
            return Build.VERSION_CODES.CUR_DEVELOPMENT;
        }
    }
}
