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

package com.android.server.nearby.provider;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.hardware.location.ContextHubClient;
import android.hardware.location.ContextHubInfo;
import android.hardware.location.ContextHubTransaction;
import android.hardware.location.NanoAppMessage;
import android.hardware.location.NanoAppState;

import com.android.server.nearby.injector.ContextHubManagerAdapter;
import com.android.server.nearby.injector.Injector;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

public class ChreCommunicationTest {
    @Mock Injector mInjector;
    @Mock ContextHubManagerAdapter mManager;
    @Mock ContextHubTransaction<List<NanoAppState>> mTransaction;
    @Mock ContextHubTransaction.Response<List<NanoAppState>> mTransactionResponse;
    @Mock ContextHubClient mClient;
    @Mock ChreCommunication.ContextHubCommsCallback mChreCallback;

    @Captor
    ArgumentCaptor<ChreCommunication.OnQueryCompleteListener> mOnQueryCompleteListenerCaptor;

    private ChreCommunication mChreCommunication;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mInjector.getContextHubManagerAdapter()).thenReturn(mManager);
        when(mManager.getContextHubs()).thenReturn(Collections.singletonList(new ContextHubInfo()));
        when(mManager.queryNanoApps(any())).thenReturn(mTransaction);
        when(mManager.createClient(any(), any(), any())).thenReturn(mClient);
        when(mTransactionResponse.getResult()).thenReturn(ContextHubTransaction.RESULT_SUCCESS);
        when(mTransactionResponse.getContents())
                .thenReturn(
                        Collections.singletonList(
                                new NanoAppState(ChreDiscoveryProvider.NANOAPP_ID, 1, true)));

        mChreCommunication = new ChreCommunication(mInjector, new InlineExecutor());
        mChreCommunication.start(
                mChreCallback, Collections.singleton(ChreDiscoveryProvider.NANOAPP_ID));

        verify(mTransaction).setOnCompleteListener(mOnQueryCompleteListenerCaptor.capture(), any());
        mOnQueryCompleteListenerCaptor.getValue().onComplete(mTransaction, mTransactionResponse);
    }

    @Test
    public void testStart() {
        verify(mChreCallback).started(true);
    }

    @Test
    public void testStop() {
        mChreCommunication.stop();
        verify(mClient).close();
    }

    @Test
    public void testSendMessageToNanApp() {
        NanoAppMessage message =
                NanoAppMessage.createMessageToNanoApp(
                        ChreDiscoveryProvider.NANOAPP_ID,
                        ChreDiscoveryProvider.NANOAPP_MESSAGE_TYPE_FILTER,
                        new byte[] {1, 2, 3});
        mChreCommunication.sendMessageToNanoApp(message);
        verify(mClient).sendMessageToNanoApp(eq(message));
    }

    @Test
    public void testOnMessageFromNanoApp() {
        NanoAppMessage message =
                NanoAppMessage.createMessageToNanoApp(
                        ChreDiscoveryProvider.NANOAPP_ID,
                        ChreDiscoveryProvider.NANOAPP_MESSAGE_TYPE_FILTER_RESULT,
                        new byte[] {1, 2, 3});
        mChreCommunication.onMessageFromNanoApp(mClient, message);
        verify(mChreCallback).onMessageFromNanoApp(eq(message));
    }

    @Test
    public void testOnHubReset() {
        mChreCommunication.onHubReset(mClient);
        verify(mChreCallback).onHubReset();
    }

    @Test
    public void testOnNanoAppLoaded() {
        mChreCommunication.onNanoAppLoaded(mClient, ChreDiscoveryProvider.NANOAPP_ID);
        verify(mChreCallback).onNanoAppRestart(eq(ChreDiscoveryProvider.NANOAPP_ID));
    }

    private static class InlineExecutor implements Executor {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
