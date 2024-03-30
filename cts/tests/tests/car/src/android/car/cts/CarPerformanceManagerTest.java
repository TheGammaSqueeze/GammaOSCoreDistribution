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
package android.car.cts;

import static android.os.Process.getThreadPriority;
import static android.os.Process.setThreadPriority;

import static com.google.common.truth.Truth.assertThat;

import android.app.UiAutomation;
import android.car.Car;
import android.car.os.CarPerformanceManager;
import android.car.os.ThreadPolicyWithPriority;
import android.car.test.ApiCheckerRule;
import android.platform.test.annotations.AppModeFull;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@SmallTest
@AppModeFull(reason = "Instant Apps cannot get car related permissions")
public class CarPerformanceManagerTest extends CarApiTestBase {

    private UiAutomation mUiAutomation;
    private CarPerformanceManager mCarPerformanceManager;
    private ThreadPolicyWithPriority mOriginalPolicyWithPriority;

    // TODO(b/242350638): move to super class (although it would need to call
    // disableAnnotationsCheck()
    @Rule
    public final ApiCheckerRule mApiCheckerRule = new ApiCheckerRule.Builder().build();

    private void setThreadPriorityGotThreadPriorityVerify(ThreadPolicyWithPriority p)
            throws Exception {
        mCarPerformanceManager.setThreadPriority(p);

        ThreadPolicyWithPriority gotP = mCarPerformanceManager.getThreadPriority();

        assertThat(gotP.getPolicy()).isEqualTo(p.getPolicy());
        assertThat(gotP.getPriority()).isEqualTo(p.getPriority());
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(Car.PERMISSION_MANAGE_THREAD_PRIORITY);

        mCarPerformanceManager = (CarPerformanceManager) getCar().getCarManager(
                Car.CAR_PERFORMANCE_SERVICE);
        assertThat(mCarPerformanceManager).isNotNull();

        // TODO(b/237015981): it would be cleaner to split this logic into a separate @Before method
        // which would be annotated with:
        //   @TestApiRequirements(requiresApi="...", onApiViolation=IGNORE)
        // But that would require a new rule to wrap the whole test class with
        // adoptShellPermissionIdentity (otherwise there would be no guarantee that the new method
        // would be called before the call to adoptShellPermissionIdentity)
        if (mApiCheckerRule.isApiSupported("android.car.os.CarPerformanceManager#"
                + "getThreadPriority")) {
            mOriginalPolicyWithPriority = mCarPerformanceManager.getThreadPriority();
        }
    }

    @After
    public void tearDown() throws Exception {
        if (mOriginalPolicyWithPriority != null) {
            mCarPerformanceManager.setThreadPriority(mOriginalPolicyWithPriority);
        }

        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @ApiTest(apis = {
            "android.car.os.CarPerformanceManager#setThreadPriority(ThreadPolicyWithPriority)",
            "android.car.os.CarPerformanceManager#getThreadPriority"})
    public void testSetThreadPriorityDefault() throws Exception {
        setThreadPriorityGotThreadPriorityVerify(new ThreadPolicyWithPriority(
                ThreadPolicyWithPriority.SCHED_DEFAULT, /* priority= */ 0));
    }

    @Test
    @ApiTest(apis = {
            "android.car.os.CarPerformanceManager#setThreadPriority(ThreadPolicyWithPriority)",
            "android.car.os.CarPerformanceManager#getThreadPriority"})
    public void testSetThreadPriorityFIFOMinPriority() throws Exception {
        setThreadPriorityGotThreadPriorityVerify(new ThreadPolicyWithPriority(
                ThreadPolicyWithPriority.SCHED_FIFO,
                ThreadPolicyWithPriority.PRIORITY_MIN));
    }

    @Test
    @ApiTest(apis = {
            "android.car.os.CarPerformanceManager#setThreadPriority(ThreadPolicyWithPriority)",
            "android.car.os.CarPerformanceManager#getThreadPriority"})
    public void testSetThreadPriorityFIFOMaxPriority() throws Exception {
        setThreadPriorityGotThreadPriorityVerify(new ThreadPolicyWithPriority(
                ThreadPolicyWithPriority.SCHED_FIFO,
                ThreadPolicyWithPriority.PRIORITY_MAX));
    }

    @Test
    @ApiTest(apis = {
            "android.car.os.CarPerformanceManager#setThreadPriority(ThreadPolicyWithPriority)",
            "android.car.os.CarPerformanceManager#getThreadPriority"})
    public void testSetThreadPriorityRRMinPriority() throws Exception {
        setThreadPriorityGotThreadPriorityVerify(new ThreadPolicyWithPriority(
                ThreadPolicyWithPriority.SCHED_RR,
                ThreadPolicyWithPriority.PRIORITY_MIN));
    }

    @Test
    @ApiTest(apis = {
            "android.car.os.CarPerformanceManager#setThreadPriority(ThreadPolicyWithPriority)",
            "android.car.os.CarPerformanceManager#getThreadPriority"})
    public void testSetThreadPriorityRRMaxPriority() throws Exception {
        setThreadPriorityGotThreadPriorityVerify(new ThreadPolicyWithPriority(
                ThreadPolicyWithPriority.SCHED_RR,
                ThreadPolicyWithPriority.PRIORITY_MAX));
    }

    @Test
    @ApiTest(apis = {
            "android.car.os.CarPerformanceManager#setThreadPriority(ThreadPolicyWithPriority)",
            "android.car.os.CarPerformanceManager#getThreadPriority"})
    public void testSetThreadPriorityDefaultKeepNiceValue() throws Exception {
        int expectedNiceValue = 10;

        // Resume the test scheduling policy to default policy.
        mCarPerformanceManager.setThreadPriority(new ThreadPolicyWithPriority(
                ThreadPolicyWithPriority.SCHED_DEFAULT, /* priority= */ 0));
        // Set a nice value for regular scheduling policy.
        setThreadPriority(expectedNiceValue);

        // Change the scheduling policy.
        setThreadPriorityGotThreadPriorityVerify(new ThreadPolicyWithPriority(
                ThreadPolicyWithPriority.SCHED_FIFO,
                ThreadPolicyWithPriority.PRIORITY_MIN));

        // Change it back, the nice value should be resumed.
        setThreadPriorityGotThreadPriorityVerify(new ThreadPolicyWithPriority(
                ThreadPolicyWithPriority.SCHED_DEFAULT, /* priority= */ 0));

        assertThat(getThreadPriority(/* tid= */ 0)).isEqualTo(expectedNiceValue);
    }
}
