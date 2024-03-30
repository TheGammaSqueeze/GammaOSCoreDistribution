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

package android.virtualdevice.cts;

import static android.Manifest.permission.ACTIVITY_EMBEDDING;
import static android.Manifest.permission.ADD_ALWAYS_UNLOCKED_DISPLAY;
import static android.Manifest.permission.ADD_TRUSTED_DISPLAY;
import static android.Manifest.permission.CREATE_VIRTUAL_DEVICE;
import static android.Manifest.permission.WAKE_LOCK;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.annotation.Nullable;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.platform.test.annotations.AppModeFull;
import android.virtualdevice.cts.util.FakeAssociationRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualInputTest {

    private static final VirtualDeviceParams DEFAULT_VIRTUAL_DEVICE_PARAMS =
            new VirtualDeviceParams.Builder().build();

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            ACTIVITY_EMBEDDING,
            ADD_ALWAYS_UNLOCKED_DISPLAY,
            ADD_TRUSTED_DISPLAY,
            CREATE_VIRTUAL_DEVICE,
            WAKE_LOCK);

    @Rule
    public FakeAssociationRule mFakeAssociationRule = new FakeAssociationRule();

    private VirtualDeviceManager mVirtualDeviceManager;
    @Nullable private VirtualDevice mVirtualDevice;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        Context context = getApplicationContext();
        assumeTrue(
                context.getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS));
        mVirtualDeviceManager = context.getSystemService(VirtualDeviceManager.class);
    }

    @After
    public void tearDown() {
        if (mVirtualDevice != null) {
            mVirtualDevice.close();
        }
    }

    @Test
    public void createVirtualKeyboard_noDisplay_shouldThrowSecurityException() {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);
        DisplayManager displayManager =
                getApplicationContext().getSystemService(DisplayManager.class);
        // Virtual displays created directly using DisplayManager should not be allowed to create
        // associated virtual keyboards
        VirtualDisplay testVirtualDisplay = displayManager.createVirtualDisplay(
                /* displayName= */ "testVirtualDisplay",
                /* width= */ 100,
                /* height= */ 100,
                /* densityDpi= */ 100,
                /* surface= */ null,
                /* flags= */ 0);

        assertThrows(
                SecurityException.class,
                () -> mVirtualDevice.createVirtualKeyboard(
                        testVirtualDisplay,
                        "testVirtualKeyboard",
                        /* vendorId= */ 0,
                        /* productId= */ 0));
    }
}

