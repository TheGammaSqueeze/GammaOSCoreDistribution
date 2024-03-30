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

package com.android.server.net;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.INetd;
import android.net.MacAddress;
import android.os.Handler;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.net.module.util.BaseNetdUnsolicitedEventListener;
import com.android.net.module.util.IBpfMap;
import com.android.net.module.util.InterfaceParams;
import com.android.net.module.util.Struct.U32;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class BpfInterfaceMapUpdaterTest {
    private static final int TEST_INDEX = 1;
    private static final int TEST_INDEX2 = 2;
    private static final String TEST_INTERFACE_NAME = "test1";
    private static final String TEST_INTERFACE_NAME2 = "test2";

    private final TestLooper mLooper = new TestLooper();
    private BaseNetdUnsolicitedEventListener mListener;
    private BpfInterfaceMapUpdater mUpdater;
    @Mock private IBpfMap<U32, InterfaceMapValue> mBpfMap;
    @Mock private INetd mNetd;
    @Mock private Context mContext;

    private class TestDependencies extends BpfInterfaceMapUpdater.Dependencies {
        @Override
        public IBpfMap<U32, InterfaceMapValue> getInterfaceMap() {
            return mBpfMap;
        }

        @Override
        public InterfaceParams getInterfaceParams(String ifaceName) {
            if (ifaceName.equals(TEST_INTERFACE_NAME)) {
                return new InterfaceParams(TEST_INTERFACE_NAME, TEST_INDEX,
                        MacAddress.ALL_ZEROS_ADDRESS);
            } else if (ifaceName.equals(TEST_INTERFACE_NAME2)) {
                return new InterfaceParams(TEST_INTERFACE_NAME2, TEST_INDEX2,
                        MacAddress.ALL_ZEROS_ADDRESS);
            }

            return null;
        }

        @Override
        public INetd getINetd(Context ctx) {
            return mNetd;
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mNetd.interfaceGetList()).thenReturn(new String[] {TEST_INTERFACE_NAME});
        mUpdater = new BpfInterfaceMapUpdater(mContext, new Handler(mLooper.getLooper()),
                new TestDependencies());
    }

    private void verifyStartUpdater() throws Exception {
        mUpdater.start();
        mLooper.dispatchAll();
        final ArgumentCaptor<BaseNetdUnsolicitedEventListener> listenerCaptor =
                ArgumentCaptor.forClass(BaseNetdUnsolicitedEventListener.class);
        verify(mNetd).registerUnsolicitedEventListener(listenerCaptor.capture());
        mListener = listenerCaptor.getValue();
        verify(mBpfMap).updateEntry(eq(new U32(TEST_INDEX)),
                eq(new InterfaceMapValue(TEST_INTERFACE_NAME)));
    }

    @Test
    public void testUpdateInterfaceMap() throws Exception {
        verifyStartUpdater();

        mListener.onInterfaceAdded(TEST_INTERFACE_NAME2);
        mLooper.dispatchAll();
        verify(mBpfMap).updateEntry(eq(new U32(TEST_INDEX2)),
                eq(new InterfaceMapValue(TEST_INTERFACE_NAME2)));

        // Check that when onInterfaceRemoved is called, nothing happens.
        mListener.onInterfaceRemoved(TEST_INTERFACE_NAME);
        mLooper.dispatchAll();
        verifyNoMoreInteractions(mBpfMap);
    }
}
