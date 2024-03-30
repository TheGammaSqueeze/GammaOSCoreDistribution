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

package com.android.server.telecom.tests;

import com.android.server.telecom.Call;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.HeadsetMediaButton;
import com.android.server.telecom.TelecomSystem;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class HeadsetMediaButtonTest extends TelecomTestCase {
    private static final int TEST_TIMEOUT_MILLIS = 1000;

    private HeadsetMediaButton mHeadsetMediaButton;

    @Mock private CallsManager mMockCallsManager;
    @Mock private HeadsetMediaButton.MediaSessionAdapter mMediaSessionAdapter;
    private TelecomSystem.SyncRoot mLock = new TelecomSystem.SyncRoot() {};

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        mHeadsetMediaButton = new HeadsetMediaButton(mContext, mMockCallsManager, mLock,
                mMediaSessionAdapter);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        mHeadsetMediaButton = null;
        super.tearDown();
    }

    /**
     * Nominal case; just add a call and remove it.
     */
    @Test
    public void testAddCall() {
        Call regularCall = getRegularCall();

        when(mMockCallsManager.hasAnyCalls()).thenReturn(true);
        mHeadsetMediaButton.onCallAdded(regularCall);
        waitForHandlerAction(mHeadsetMediaButton.getHandler(), TEST_TIMEOUT_MILLIS);
        verify(mMediaSessionAdapter).setActive(eq(true));
        // ... and thus we see how the original code isn't amenable to tests.
        when(mMediaSessionAdapter.isActive()).thenReturn(true);

        when(mMockCallsManager.hasAnyCalls()).thenReturn(false);
        mHeadsetMediaButton.onCallRemoved(regularCall);
        waitForHandlerAction(mHeadsetMediaButton.getHandler(), TEST_TIMEOUT_MILLIS);
        verify(mMediaSessionAdapter).setActive(eq(false));
    }

    /**
     * Test a case where a regular call becomes an external call, and back again.
     */
    @Test
    public void testRegularCallThatBecomesExternal() {
        Call regularCall = getRegularCall();

        // Start with a regular old call.
        when(mMockCallsManager.hasAnyCalls()).thenReturn(true);
        mHeadsetMediaButton.onCallAdded(regularCall);
        waitForHandlerAction(mHeadsetMediaButton.getHandler(), TEST_TIMEOUT_MILLIS);
        verify(mMediaSessionAdapter).setActive(eq(true));
        when(mMediaSessionAdapter.isActive()).thenReturn(true);

        // Change so it is external.
        when(regularCall.isExternalCall()).thenReturn(true);
        when(mMockCallsManager.hasAnyCalls()).thenReturn(false);
        mHeadsetMediaButton.onExternalCallChanged(regularCall, true);
        // Expect to set session inactive.
        waitForHandlerAction(mHeadsetMediaButton.getHandler(), TEST_TIMEOUT_MILLIS);
        verify(mMediaSessionAdapter).setActive(eq(false));

        // For good measure lets make it non-external again.
        when(regularCall.isExternalCall()).thenReturn(false);
        when(mMockCallsManager.hasAnyCalls()).thenReturn(true);
        mHeadsetMediaButton.onExternalCallChanged(regularCall, false);
        // Expect to set session active.
        waitForHandlerAction(mHeadsetMediaButton.getHandler(), TEST_TIMEOUT_MILLIS);
        verify(mMediaSessionAdapter).setActive(eq(true));
    }

    /**
     * @return a mock call instance of a regular non-external call.
     */
    private Call getRegularCall() {
        Call regularCall = Mockito.mock(Call.class);
        when(regularCall.isExternalCall()).thenReturn(false);
        return regularCall;
    }
}
