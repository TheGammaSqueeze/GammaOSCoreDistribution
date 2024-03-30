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

package android.virtualdevice.cts.util;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager;
import android.content.pm.PackageManager;
import android.os.Process;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.rules.ExternalResource;

import java.util.List;

/**
 * A test rule that creates a {@link CompanionDeviceManager} association with the instrumented
 * package for the duration of the test.
 */
public class FakeAssociationRule extends ExternalResource {

    private static final String FAKE_ASSOCIATION_ADDRESS = "00:00:00:00:00:10";

    private AssociationInfo mAssociationInfo;
    private CompanionDeviceManager mCompanionDeviceManager;

    @Override
    protected void before() throws Throwable {
        super.before();
        assumeTrue(
                getApplicationContext().getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP));
        mCompanionDeviceManager =
                getApplicationContext().getSystemService(CompanionDeviceManager.class);
        clearExistingAssociations();
        SystemUtil.runShellCommand(String.format("cmd companiondevice associate %d %s %s",
                Process.myUserHandle().getIdentifier(),
                InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName(),
                FAKE_ASSOCIATION_ADDRESS));
        List<AssociationInfo> associations = mCompanionDeviceManager.getMyAssociations();
        assertThat(associations).hasSize(1);
        mAssociationInfo = associations.get(0);
    }

    @Override
    protected void after() {
        super.after();
        if (mAssociationInfo != null) {
            mCompanionDeviceManager.disassociate(mAssociationInfo.getId());
        }
    }

    private void clearExistingAssociations() {
        List<AssociationInfo> associations = mCompanionDeviceManager.getMyAssociations();
        for (AssociationInfo association : associations) {
            mCompanionDeviceManager.disassociate(association.getId());
        }
        assertThat(mCompanionDeviceManager.getMyAssociations()).isEmpty();
    }

    public AssociationInfo getAssociationInfo() {
        return mAssociationInfo;
    }
}
