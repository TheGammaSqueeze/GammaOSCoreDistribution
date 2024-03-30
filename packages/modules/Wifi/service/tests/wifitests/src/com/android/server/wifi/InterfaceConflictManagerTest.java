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

package com.android.server.wifi;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.res.Resources;
import android.net.wifi.WifiContext;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.WorkSource;
import android.os.test.TestLooper;
import android.util.Pair;

import androidx.test.filters.SmallTest;

import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.server.wifi.util.WaitingState;
import com.android.wifi.resources.R;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Unit test harness for InterfaceConflictManager.
 */
@SmallTest
public class InterfaceConflictManagerTest {
    private TestLooper mTestLooper;
    private InterfaceConflictManager mDut;
    private TestStateMachine mSm;

    @Mock WifiContext mWifiContext;
    @Mock Resources mResources;
    @Mock FrameworkFacade mFrameworkFacade;
    @Mock HalDeviceManager mHdm;
    @Mock WifiDialogManager mWifiDialogManager;
    @Mock WifiDialogManager.DialogHandle mDialogHandle;

    private static final int TEST_UID = 1234;
    private static final String TEST_PACKAGE_NAME = "some.package.name";
    private static final String TEST_APP_NAME = "Some App Name";
    private static final WorkSource TEST_WS = new WorkSource(TEST_UID, TEST_PACKAGE_NAME);

    ArgumentCaptor<WifiDialogManager.SimpleDialogCallback> mCallbackCaptor =
            ArgumentCaptor.forClass(WifiDialogManager.SimpleDialogCallback.class);

    private class TestState extends State {
        private final String mName;

        public List<Integer> commandList = new ArrayList<>();

        public void resetTestFlags() {
            commandList.clear();
        }

        TestState(String name) {
            super();
            mName = name;
        }

        @Override
        public String getName() {
            return mName;
        }

        @Override
        public boolean processMessage(Message msg) {
            commandList.add(msg.what);
            return HANDLED;
        }
    }

    private class TestStateMachine extends StateMachine {
        public TestState A = new TestState("A");
        public WaitingState B;

        TestStateMachine(String name, Looper looper) {
            super(name, looper);

            addState(A);
            B = new WaitingState(this);
            addState(B, A);

            setInitialState(A);
        }
    }

    /**
     * Initializes mocks.
     */
    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        mTestLooper = new TestLooper();

        mSm = new TestStateMachine("TestStateMachine", mTestLooper.getLooper());
        mSm.setDbg(true);
        mSm.start();

        // enable user approval (needed for most tests)
        when(mWifiContext.getResources()).thenReturn(mResources);
        when(mResources.getBoolean(
                R.bool.config_wifiUserApprovalRequiredForD2dInterfacePriority)).thenReturn(true);

        when(mFrameworkFacade.getAppName(any(), anyString(), anyInt())).thenReturn(TEST_APP_NAME);
        when(mWifiDialogManager.createSimpleDialog(any(), any(), any(), any(), any(), any(),
                any())).thenReturn(mDialogHandle);

        mDut = new InterfaceConflictManager(mWifiContext, mFrameworkFacade, mHdm,
                new WifiThreadRunner(new Handler(mTestLooper.getLooper())), mWifiDialogManager);

    }

    /**
     * Verify that w/o user approval enabled will always continue operation
     */
    @Test
    public void testUserApprovalDisabled() {
        // disable user approval
        when(mResources.getBoolean(
                R.bool.config_wifiUserApprovalRequiredForD2dInterfacePriority)).thenReturn(false);

        assertEquals(InterfaceConflictManager.ICM_EXECUTE_COMMAND,
                mDut.manageInterfaceConflictForStateMachine("Some Tag", mSm.obtainMessage(10), mSm,
                        mSm.B, mSm.A, HalDeviceManager.HDM_CREATE_IFACE_NAN, TEST_WS));
        mTestLooper.dispatchAll();
        assertEquals("State changed incorrect", mSm.A, mSm.getCurrentState());
    }

    /**
     * Verify that w/o user approval enabled will always continue operation
     */
    @Test
    public void testUserApprovalDisabledForSpecificPackage() {
        // disable user approval for specific package
        when(mResources.getStringArray(
                R.array.config_wifiExcludedFromUserApprovalForD2dInterfacePriority)).thenReturn(
                new String[]{TEST_PACKAGE_NAME});

        assertEquals(InterfaceConflictManager.ICM_EXECUTE_COMMAND,
                mDut.manageInterfaceConflictForStateMachine("Some Tag", mSm.obtainMessage(10), mSm,
                        mSm.B, mSm.A, HalDeviceManager.HDM_CREATE_IFACE_NAN, TEST_WS));
        mTestLooper.dispatchAll();
        assertEquals("State changed incorrect", mSm.A, mSm.getCurrentState());
    }

    /**
     * Verify that if interface cannot be created or if interface can be created w/o side effects
     * then command simply proceeds.
     */
    @Test
    public void testUserApprovalNeededButCommandCanProceed() {
        int interfaceType = HalDeviceManager.HDM_CREATE_IFACE_NAN;

        // can't create interface
        when(mHdm.reportImpactToCreateIface(eq(interfaceType), eq(false), eq(TEST_WS))).thenReturn(
                null);
        assertEquals(InterfaceConflictManager.ICM_EXECUTE_COMMAND,
                mDut.manageInterfaceConflictForStateMachine("Some Tag", mSm.obtainMessage(10), mSm,
                        mSm.B, mSm.A, interfaceType, TEST_WS));
        mTestLooper.dispatchAll();
        assertEquals("State changed incorrect", mSm.A, mSm.getCurrentState());

        // can create interface w/o side effects
        when(mHdm.reportImpactToCreateIface(eq(interfaceType), eq(false), eq(TEST_WS))).thenReturn(
                Collections.emptyList());
        assertEquals(InterfaceConflictManager.ICM_EXECUTE_COMMAND,
                mDut.manageInterfaceConflictForStateMachine("Some Tag", mSm.obtainMessage(10), mSm,
                        mSm.B, mSm.A, interfaceType, TEST_WS));
        mTestLooper.dispatchAll();
        assertEquals("State changed incorrect", mSm.A, mSm.getCurrentState());
    }

    /**
     * Verify flow with user approval.
     */
    @Test
    public void testUserApproved() {
        int interfaceType = HalDeviceManager.HDM_CREATE_IFACE_P2P;
        Message msg = mSm.obtainMessage(10);

        // can create interface - but with side effects
        when(mHdm.reportImpactToCreateIface(eq(interfaceType), eq(false), eq(TEST_WS))).thenReturn(
                Arrays.asList(Pair.create(HalDeviceManager.HDM_CREATE_IFACE_NAN,
                        new WorkSource(10, "something else"))));

        // send request
        assertEquals(InterfaceConflictManager.ICM_SKIP_COMMAND_WAIT_FOR_USER,
                mDut.manageInterfaceConflictForStateMachine("Some Tag", msg, mSm, mSm.B, mSm.A,
                        interfaceType, TEST_WS));
        mTestLooper.dispatchAll();
        assertEquals("State not in waiting", mSm.B, mSm.getCurrentState());
        verify(mWifiDialogManager).createSimpleDialog(any(), any(), any(), any(), any(),
                mCallbackCaptor.capture(), any());

        // user approve
        mCallbackCaptor.getValue().onPositiveButtonClicked();
        mTestLooper.dispatchAll();
        assertEquals("State not back in primary", mSm.A, mSm.getCurrentState());
        assertArrayEquals("Executed the held back command", new Integer[]{10},
                mSm.A.commandList.toArray());

        // re-execute command and get indication to proceed
        assertEquals(InterfaceConflictManager.ICM_EXECUTE_COMMAND,
                mDut.manageInterfaceConflictForStateMachine("Some Tag", msg, mSm, mSm.B, mSm.A,
                        interfaceType, TEST_WS));
        mTestLooper.dispatchAll();
        assertEquals("State should stay in primary", mSm.A, mSm.getCurrentState());
    }

    /**
     * Verify flow with user rejection.
     */
    @Test
    public void testUserRejected() {
        int interfaceType = HalDeviceManager.HDM_CREATE_IFACE_P2P;
        Message msg = mSm.obtainMessage(10);

        // can create interface - but with side effects
        when(mHdm.reportImpactToCreateIface(eq(interfaceType), eq(false), eq(TEST_WS))).thenReturn(
                Arrays.asList(Pair.create(HalDeviceManager.HDM_CREATE_IFACE_NAN,
                        new WorkSource(10, "something else"))));

        // send request
        assertEquals(InterfaceConflictManager.ICM_SKIP_COMMAND_WAIT_FOR_USER,
                mDut.manageInterfaceConflictForStateMachine("Some Tag", msg, mSm, mSm.B, mSm.A,
                        interfaceType, TEST_WS));
        mTestLooper.dispatchAll();
        assertEquals("State not in waiting", mSm.B, mSm.getCurrentState());
        verify(mWifiDialogManager).createSimpleDialog(any(), any(), any(), any(), any(),
                mCallbackCaptor.capture(), any());

        // user rejects
        mCallbackCaptor.getValue().onNegativeButtonClicked();
        mTestLooper.dispatchAll();
        assertEquals("State not back in primary", mSm.A, mSm.getCurrentState());
        assertArrayEquals("Executed the held back command", new Integer[]{10},
                mSm.A.commandList.toArray());

        // re-execute command and get indication to abort
        assertEquals(InterfaceConflictManager.ICM_ABORT_COMMAND,
                mDut.manageInterfaceConflictForStateMachine("Some Tag", msg, mSm, mSm.B, mSm.A,
                        interfaceType, TEST_WS));
        mTestLooper.dispatchAll();
        assertEquals("State should stay in primary", mSm.A, mSm.getCurrentState());
    }
}
