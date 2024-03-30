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

package android.telephony5.cts;

import static org.junit.Assert.fail;

import android.content.Context;
import android.content.pm.PackageManager;
import android.platform.test.annotations.AppModeFull;
import android.telephony.TelephonyManager;
import android.telephony.cts.TelephonyUtils;

import androidx.test.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Test APIs when the package does not have READ_PHONE_STATE.
 */
public class TelephonyManagerReadNonDangerousPermissionTest {

    private Context mContext;
    private PackageManager mPackageManager;
    private TelephonyManager mTelephonyManager;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        mPackageManager = mContext.getPackageManager();
    }

    @After
    public void tearDown() throws Exception {
        TelephonyUtils.resetCompatCommand(InstrumentationRegistry.getInstrumentation(),
                TelephonyUtils.CTS_APP_PACKAGE2,
                TelephonyUtils.ENABLE_GET_CALL_STATE_PERMISSION_PROTECTION_STRING);
    }

    @Test
    @AppModeFull
    public void testReadNonDangerousPermission() throws Exception {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }

        try {
            mTelephonyManager.isDataEnabled();
            mTelephonyManager.isDataRoamingEnabled();
            mTelephonyManager.isDataEnabledForReason(TelephonyManager.DATA_ENABLED_REASON_POLICY);
            mTelephonyManager.isDataConnectionAllowed();

        } catch (SecurityException e) {
            fail("should not fail with READ_BASIC_PHONE_STATE");
        }
    }
}
