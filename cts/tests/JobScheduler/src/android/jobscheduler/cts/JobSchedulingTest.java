/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.jobscheduler.cts;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.jobscheduler.MockJobService.TestEnvironment;
import android.jobscheduler.MockJobService.TestEnvironment.Event;
import android.provider.DeviceConfig;

import com.android.compatibility.common.util.BatteryUtils;
import com.android.compatibility.common.util.SystemUtil;

import java.util.List;

/**
 * Tests related to scheduling jobs.
 */
@TargetApi(30)
public class JobSchedulingTest extends BaseJobSchedulerTest {
    private static final int MIN_SCHEDULE_QUOTA = 250;
    private static final int JOB_ID = JobSchedulingTest.class.hashCode();
    // The maximum number of jobs that can run concurrently.
    private static final int MAX_JOB_CONTEXTS_COUNT = 16;

    @Override
    public void tearDown() throws Exception {
        mJobScheduler.cancel(JOB_ID);
        SystemUtil.runShellCommand(getInstrumentation(), "cmd jobscheduler reset-schedule-quota");
        SystemUtil.runShellCommand(getInstrumentation(), "cmd jobscheduler monitor-battery off");
        BatteryUtils.runDumpsysBatteryReset();

        // The super method should be called at the end.
        super.tearDown();
    }

    /**
     * Test that apps can call schedule at least the minimum amount of times without being
     * blocked.
     */
    public void testMinSuccessfulSchedulingQuota() {
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setMinimumLatency(60 * 60 * 1000L)
                .setPersisted(true)
                .build();

        for (int i = 0; i < MIN_SCHEDULE_QUOTA; ++i) {
            assertEquals(JobScheduler.RESULT_SUCCESS, mJobScheduler.schedule(jobInfo));
        }
    }

    /**
     * Test that scheduling fails once an app hits the schedule quota limit.
     */
    public void testFailingScheduleOnQuotaExceeded() {
        mDeviceConfigStateHelper.set(
                new DeviceConfig.Properties.Builder(DeviceConfig.NAMESPACE_JOB_SCHEDULER)
                .setBoolean("enable_api_quotas", true)
                .setInt("aq_schedule_count", 300)
                .setLong("aq_schedule_window_ms", 300000)
                .setBoolean("aq_schedule_throw_exception", false)
                .setBoolean("aq_schedule_return_failure", true)
                .build());

        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setMinimumLatency(60 * 60 * 1000L)
                .setPersisted(true)
                .build();

        for (int i = 0; i < 500; ++i) {
            final int expected =
                    i < 300 ? JobScheduler.RESULT_SUCCESS : JobScheduler.RESULT_FAILURE;
            assertEquals("Got unexpected result for schedule #" + (i + 1),
                    expected, mJobScheduler.schedule(jobInfo));
        }
    }

    /**
     * Test that scheduling succeeds even after an app hits the schedule quota limit.
     */
    public void testContinuingScheduleOnQuotaExceeded() {
        mDeviceConfigStateHelper.set(
                new DeviceConfig.Properties.Builder(DeviceConfig.NAMESPACE_JOB_SCHEDULER)
                        .setBoolean("enable_api_quotas", true)
                        .setInt("aq_schedule_count", 300)
                        .setLong("aq_schedule_window_ms", 300000)
                        .setBoolean("aq_schedule_throw_exception", false)
                        .setBoolean("aq_schedule_return_failure", false)
                        .build());

        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setMinimumLatency(60 * 60 * 1000L)
                .setPersisted(true)
                .build();

        for (int i = 0; i < 500; ++i) {
            assertEquals("Got unexpected result for schedule #" + (i + 1),
                    JobScheduler.RESULT_SUCCESS, mJobScheduler.schedule(jobInfo));
        }
    }

    /**
     * Test that non-persisted jobs aren't limited by quota.
     */
    public void testNonPersistedJobsNotLimited() {
        mDeviceConfigStateHelper.set(
                new DeviceConfig.Properties.Builder(DeviceConfig.NAMESPACE_JOB_SCHEDULER)
                .setBoolean("enable_api_quotas", true)
                .setInt("aq_schedule_count", 300)
                .setLong("aq_schedule_window_ms", 60000)
                .setBoolean("aq_schedule_throw_exception", false)
                .setBoolean("aq_schedule_return_failure", true)
                .build());

        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent)
                .setMinimumLatency(60 * 60 * 1000L)
                .setPersisted(false)
                .build();

        for (int i = 0; i < 500; ++i) {
            assertEquals(JobScheduler.RESULT_SUCCESS, mJobScheduler.schedule(jobInfo));
        }
    }

    public void testHigherPriorityJobRunsFirst() throws Exception {
        SystemUtil.runShellCommand(getInstrumentation(), "cmd jobscheduler monitor-battery on");

        setStorageStateLow(true);
        final int higherPriorityJobId = JOB_ID;
        final int numMinPriorityJobs = 2 * MAX_JOB_CONTEXTS_COUNT;
        kTestEnvironment.setExpectedExecutions(1 + numMinPriorityJobs);
        for (int i = 0; i < numMinPriorityJobs; ++i) {
            JobInfo job = new JobInfo.Builder(higherPriorityJobId + 1 + i, kJobServiceComponent)
                    .setPriority(JobInfo.PRIORITY_MIN)
                    .setRequiresStorageNotLow(true)
                    .build();
            mJobScheduler.schedule(job);
        }
        // Schedule the higher priority job last since the default sorting is by enqueue time.
        JobInfo jobMax = new JobInfo.Builder(higherPriorityJobId, kJobServiceComponent)
                .setPriority(JobInfo.PRIORITY_DEFAULT)
                .setRequiresStorageNotLow(true)
                .build();
        mJobScheduler.schedule(jobMax);

        setStorageStateLow(false);
        kTestEnvironment.awaitExecution();

        Event jobHigherExecution = new Event(TestEnvironment.Event.EVENT_START_JOB,
                higherPriorityJobId);
        List<Event> executedEvents = kTestEnvironment.getExecutedEvents();
        boolean higherExecutedFirst = false;
        // Due to racing, we can't just check the very first item in the array. We can however
        // make sure it was in the first set of jobs to run.
        for (int i = 0; i < executedEvents.size() && i < MAX_JOB_CONTEXTS_COUNT; ++i) {
            if (executedEvents.get(i).equals(jobHigherExecution)) {
                higherExecutedFirst = true;
                break;
            }
        }
        assertTrue(
                "Higher priority job (" + higherPriorityJobId + ") didn't run in first batch: "
                        + executedEvents, higherExecutedFirst);
    }
}
