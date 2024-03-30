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

package com.android.car.systeminterface;

import static com.google.common.truth.Truth.assertThat;

import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.Context;

import com.android.car.test.utils.TemporaryFile;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;

/**
 * Unit tests for {@link SystemStateInterface}
 *
 * Run:
 * atest SystemStateInterfaceTest
 */
public final class SystemStateInterfaceTest extends AbstractExtendedMockitoTestCase {
    private static final String TAG = SystemStateInterfaceTest.class.getSimpleName();

    @Mock
    private Context mMockContext;
    private SystemStateInterface.DefaultImpl mSystemStateInterface;

    public SystemStateInterfaceTest() {
        super(SystemStateInterface.TAG);
    }

    @Before
    public void setUp() throws IOException {
        mSystemStateInterface = new SystemStateInterface.DefaultImpl(mMockContext);
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(SystemPowerControlHelper.class);
    }

    @Test
    public void testSleepWhenHelperSucceeds() throws Exception {
        mockGetSysFsPowerControlFile();

        assertThat(mSystemStateInterface.enterDeepSleep()).isTrue();
    }

    @Test
    public void testSleepWhenHelperFails() {
        ExtendedMockito.when(SystemPowerControlHelper.getSysFsPowerControlFile()).thenReturn("");

        assertThat(mSystemStateInterface.enterDeepSleep()).isFalse();
    }

    @Test
    public void testHibernateWhenHelperSucceeds() throws Exception {
        mockGetSysFsPowerControlFile();

        assertThat(mSystemStateInterface.enterHibernation()).isTrue();
    }

    private void mockGetSysFsPowerControlFile() throws Exception {
        assertSpied(SystemPowerControlHelper.class);

        try (TemporaryFile powerStateControlFile = new TemporaryFile(TAG)) {
            ExtendedMockito.when(SystemPowerControlHelper.getSysFsPowerControlFile()).thenReturn(
                    powerStateControlFile.getFile().getAbsolutePath());
        }
    }

    @Test
    public void testHibernateWhenHelperFails() {
        ExtendedMockito.when(SystemPowerControlHelper.getSysFsPowerControlFile()).thenReturn("");

        assertThat(mSystemStateInterface.enterHibernation()).isFalse();
    }
}
