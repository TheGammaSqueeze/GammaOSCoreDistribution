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

package android.appsearch.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.AfterClassWithInfo;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

/**
 * Test to cover multi-user CP2 contacts indexing into AppSearch.
 *
 * <p>Unlock your device when testing locally.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class ContactsIndexerMultiUserTest extends AppSearchHostTestBase {

    private static int sSecondaryUserId;

    @BeforeClassWithInfo
    public static void setUpClass(TestInformation testInfo) throws Exception {
        assumeTrue("Multi-user is not supported on this device",
                testInfo.getDevice().isMultiUserSupported());

        sSecondaryUserId = testInfo.getDevice().createUser("Test User #1");
        assertThat(testInfo.getDevice().startUser(sSecondaryUserId)).isTrue();
    }

    @Before
    public void setUp() throws Exception {
        if (!getDevice().isUserRunning(sSecondaryUserId)) {
            getDevice().startUser(sSecondaryUserId, /*waitFlag=*/ true);
        }
        installPackageAsUser(TARGET_APK_A, /*grantPermission=*/ true, sSecondaryUserId);
    }

    @AfterClassWithInfo
    public static void tearDownClass(TestInformation testInfo) throws Exception {
        if (sSecondaryUserId > 0) {
            testInfo.getDevice().removeUser(sSecondaryUserId);
        }
    }

    @Test
    public void testMultiUser_scheduleMultipleFullUpdateJobs() throws Exception {
        runContactsIndexerDeviceTestAsUserInPkgA("testFullUpdateJobIsScheduled",
                sSecondaryUserId,
                Collections.singletonMap(USER_ID_KEY, String.valueOf(sSecondaryUserId)));
    }
}
