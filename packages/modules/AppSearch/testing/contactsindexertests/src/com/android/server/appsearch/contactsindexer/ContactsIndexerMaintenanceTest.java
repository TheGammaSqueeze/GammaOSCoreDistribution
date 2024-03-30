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

package com.android.server.appsearch.contactsindexer;

import static android.Manifest.permission.RECEIVE_BOOT_COMPLETED;

import static com.android.server.appsearch.contactsindexer.ContactsIndexerMaintenanceService.MIN_INDEXER_JOB_ID;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.UserIdInt;
import android.app.UiAutomation;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.ContextWrapper;

import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class ContactsIndexerMaintenanceTest {

    private static final int DEFAULT_USER_ID = 0;

    private Context mContext = ApplicationProvider.getApplicationContext();
    private Context mContextWrapper;
    @Mock private JobScheduler mockJobScheduler;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContextWrapper = new ContextWrapper(mContext) {
            @Override
            @Nullable
            public Object getSystemService(String name) {
                if (Context.JOB_SCHEDULER_SERVICE.equals(name)) {
                    return mockJobScheduler;
                }
                return getSystemService(name);
            }
        };
    }

    @Test
    public void testScheduleFullUpdateJob_oneOff_isNotPeriodic() {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity(RECEIVE_BOOT_COMPLETED);
            ContactsIndexerMaintenanceService.scheduleFullUpdateJob(mContext, DEFAULT_USER_ID,
                    /*periodic=*/ false, /*intervalMillis=*/ -1);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
        JobInfo jobInfo = getPendingFullUpdateJob(DEFAULT_USER_ID);
        assertThat(jobInfo).isNotNull();
        assertThat(jobInfo.isRequireBatteryNotLow()).isTrue();
        assertThat(jobInfo.isRequireDeviceIdle()).isTrue();
        assertThat(jobInfo.isPersisted()).isTrue();
        assertThat(jobInfo.isPeriodic()).isFalse();
    }

    @Test
    public void testScheduleFullUpdateJob_periodic_isPeriodic() {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity(RECEIVE_BOOT_COMPLETED);
            ContactsIndexerMaintenanceService.scheduleFullUpdateJob(mContext, /*userId=*/ 0,
                    /*periodic=*/ true, /*intervalMillis=*/ TimeUnit.DAYS.toMillis(7));
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
        JobInfo jobInfo = getPendingFullUpdateJob(DEFAULT_USER_ID);
        assertThat(jobInfo).isNotNull();
        assertThat(jobInfo.isRequireBatteryNotLow()).isTrue();
        assertThat(jobInfo.isRequireDeviceIdle()).isTrue();
        assertThat(jobInfo.isPersisted()).isTrue();
        assertThat(jobInfo.isPeriodic()).isTrue();
        assertThat(jobInfo.getIntervalMillis()).isEqualTo(TimeUnit.DAYS.toMillis(7));
        assertThat(jobInfo.getFlexMillis()).isEqualTo(TimeUnit.DAYS.toMillis(7)/2);
    }

    @Test
    public void testScheduleFullUpdateJob_oneOffThenPeriodic_isRescheduled() {
        ContactsIndexerMaintenanceService.scheduleFullUpdateJob(mContextWrapper, DEFAULT_USER_ID,
                /*periodic=*/ false, /*intervalMillis=*/ -1);
        ArgumentCaptor<JobInfo> firstJobInfoCaptor = ArgumentCaptor.forClass(JobInfo.class);
        verify(mockJobScheduler).schedule(firstJobInfoCaptor.capture());
        JobInfo firstJobInfo = firstJobInfoCaptor.getValue();

        when(mockJobScheduler.getPendingJob(eq(MIN_INDEXER_JOB_ID))).thenReturn(firstJobInfo);
        ContactsIndexerMaintenanceService.scheduleFullUpdateJob(mContextWrapper, DEFAULT_USER_ID,
                /*periodic=*/ true, /*intervalMillis=*/ TimeUnit.DAYS.toMillis(7));
        ArgumentCaptor<JobInfo> argumentCaptor = ArgumentCaptor.forClass(JobInfo.class);
        verify(mockJobScheduler, times(2)).schedule(argumentCaptor.capture());
        List<JobInfo> jobInfos = argumentCaptor.getAllValues();
        JobInfo jobInfo = jobInfos.get(1);
        assertThat(jobInfo.isRequireBatteryNotLow()).isTrue();
        assertThat(jobInfo.isRequireDeviceIdle()).isTrue();
        assertThat(jobInfo.isPersisted()).isTrue();
        assertThat(jobInfo.isPeriodic()).isTrue();
        assertThat(jobInfo.getIntervalMillis()).isEqualTo(TimeUnit.DAYS.toMillis(7));
    }

    @Test
    public void testScheduleFullUpdateJob_differentParams_isRescheduled() {
        ContactsIndexerMaintenanceService.scheduleFullUpdateJob(mContextWrapper, DEFAULT_USER_ID,
                /*periodic=*/ true, /*intervalMillis=*/ TimeUnit.DAYS.toMillis(7));
        ArgumentCaptor<JobInfo> firstJobInfoCaptor = ArgumentCaptor.forClass(JobInfo.class);
        verify(mockJobScheduler).schedule(firstJobInfoCaptor.capture());
        JobInfo firstJobInfo = firstJobInfoCaptor.getValue();

        when(mockJobScheduler.getPendingJob(eq(MIN_INDEXER_JOB_ID))).thenReturn(firstJobInfo);
        ContactsIndexerMaintenanceService.scheduleFullUpdateJob(mContextWrapper, DEFAULT_USER_ID,
                /*periodic=*/ true, /*intervalMillis=*/ TimeUnit.DAYS.toMillis(30));
        ArgumentCaptor<JobInfo> argumentCaptor = ArgumentCaptor.forClass(JobInfo.class);
        // Mockito.verify() counts the number of occurrences from the beginning of the test.
        // This verify() uses times(2) to also account for the call to JobScheduler.schedule() above
        // where the first JobInfo is captured.
        verify(mockJobScheduler, times(2)).schedule(argumentCaptor.capture());
        List<JobInfo> jobInfos = argumentCaptor.getAllValues();
        JobInfo jobInfo = jobInfos.get(1);
        assertThat(jobInfo.isRequireBatteryNotLow()).isTrue();
        assertThat(jobInfo.isRequireDeviceIdle()).isTrue();
        assertThat(jobInfo.isPersisted()).isTrue();
        assertThat(jobInfo.isPeriodic()).isTrue();
        assertThat(jobInfo.getIntervalMillis()).isEqualTo(TimeUnit.DAYS.toMillis(30));
    }

    @Test
    public void testScheduleFullUpdateJob_sameParams_isNotRescheduled() {
        ContactsIndexerMaintenanceService.scheduleFullUpdateJob(mContextWrapper, DEFAULT_USER_ID,
                /*periodic=*/ true, /*intervalMillis=*/ TimeUnit.DAYS.toMillis(7));
        ArgumentCaptor<JobInfo> argumentCaptor = ArgumentCaptor.forClass(JobInfo.class);
        verify(mockJobScheduler).schedule(argumentCaptor.capture());
        JobInfo firstJobInfo = argumentCaptor.getValue();

        when(mockJobScheduler.getPendingJob(eq(MIN_INDEXER_JOB_ID))).thenReturn(firstJobInfo);
        ContactsIndexerMaintenanceService.scheduleFullUpdateJob(mContextWrapper, DEFAULT_USER_ID,
                /*periodic=*/ true, /*intervalMillis=*/ TimeUnit.DAYS.toMillis(7));
        // Mockito.verify() counts the number of occurrences from the beginning of the test.
        // This verify() uses the default count of 1 (equivalent to times(1)) to account for the
        // call to JobScheduler.schedule() above where the first JobInfo is captured.
        verify(mockJobScheduler).schedule(any(JobInfo.class));
    }

    @Nullable
    private JobInfo getPendingFullUpdateJob(@UserIdInt int userId) {
        int jobId = MIN_INDEXER_JOB_ID + userId;
        return mContext.getSystemService(JobScheduler.class).getPendingJob(jobId);
    }
}
