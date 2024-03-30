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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.annotation.Nullable;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.VirtualDisplay;
import android.platform.test.annotations.AppModeFull;
import android.view.Display;
import android.virtualdevice.cts.util.FakeAssociationRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = " cannot be accessed by instant apps")
public class CreateVirtualDisplayTest {

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
    @Nullable
    private VirtualDevice mVirtualDevice;
    @Mock
    private VirtualDisplay.Callback mVirtualDisplayCallback;

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
    public void createVirtualDisplay_shouldNotBeNull() {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);
        VirtualDisplay virtualDisplay = mVirtualDevice.createVirtualDisplay(
                /* width= */ 100,
                /* height= */ 100,
                /* densityDpi= */ 240,
                /* surface= */ null,
                /* flags= */ 0,
                Runnable::run,
                mVirtualDisplayCallback);
        assertThat(virtualDisplay).isNotNull();
        int displayFlags = virtualDisplay.getDisplay().getFlags();
        assertWithMessage(
                String.format(
                        "Virtual display flags (0x%x) should contain FLAG_OWN_DISPLAY_GROUP",
                        displayFlags))
                .that(displayFlags & Display.FLAG_OWN_DISPLAY_GROUP)
                .isNotEqualTo(0);
    }

    @Test
    public void createVirtualDisplay_alwaysUnlocked_shouldSpecifyFlagInVirtualDisplays() {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        new VirtualDeviceParams.Builder()
                                .setLockState(VirtualDeviceParams.LOCK_STATE_ALWAYS_UNLOCKED)
                                .build());
        VirtualDisplay virtualDisplay = mVirtualDevice.createVirtualDisplay(
                /* width= */ 100,
                /* height= */ 100,
                /* densityDpi= */ 240,
                /* surface= */ null,
                /* flags= */ 0,
                Runnable::run,
                mVirtualDisplayCallback);
        assertThat(virtualDisplay).isNotNull();
        int displayFlags = virtualDisplay.getDisplay().getFlags();
        assertWithMessage(
                String.format(
                        "Virtual display flags (0x%x) should contain FLAG_ALWAYS_UNLOCKED",
                        displayFlags))
                .that(displayFlags & Display.FLAG_ALWAYS_UNLOCKED)
                .isNotEqualTo(0);
    }

    @Test
    public void createVirtualDisplay_nullExecutorAndCallback_shouldSucceed() {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);
        VirtualDisplay virtualDisplay = mVirtualDevice.createVirtualDisplay(
                /* width= */ 100,
                /* height= */ 100,
                /* densityDpi= */ 240,
                /* surface= */ null,
                /* flags= */ 0,
                /* executor= */ null,
                /* callback= */ null);
        assertThat(virtualDisplay).isNotNull();
    }

    @Test
    public void createVirtualDisplay_nullExecutorButNonNullCallback_shouldThrow() {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);

        assertThrows(NullPointerException.class, () ->
                mVirtualDevice.createVirtualDisplay(
                        /* width= */ 100,
                        /* height= */ 100,
                        /* densityDpi= */ 240,
                        /* surface= */ null,
                        /* flags= */ 0,
                        /* executor= */ null,
                        mVirtualDisplayCallback));
    }

    @Test
    public void createVirtualDisplay_createAndRemoveSeveralDisplays() throws InterruptedException {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);
        ArrayList<VirtualDisplay> displays = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            displays.add(mVirtualDevice.createVirtualDisplay(
                    /* width= */ 100,
                    /* height= */ 100,
                    /* densityDpi= */ 240,
                    /* surface= */ null,
                    /* flags= */ 0,
                    Runnable::run,
                    mVirtualDisplayCallback));
        }

        // Releasing several displays in quick succession should not cause deadlock
        while (!displays.isEmpty()) {
            int index = displays.size() - 1;
            displays.remove(index).release();
        }
    }
}
