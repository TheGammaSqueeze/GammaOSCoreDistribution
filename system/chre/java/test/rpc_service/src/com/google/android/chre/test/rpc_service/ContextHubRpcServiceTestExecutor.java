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
package com.google.android.chre.test.rpc_service;

import android.hardware.location.ContextHubClient;
import android.hardware.location.ContextHubClientCallback;
import android.hardware.location.ContextHubInfo;
import android.hardware.location.ContextHubManager;
import android.hardware.location.NanoAppBinary;
import android.hardware.location.NanoAppMessage;
import android.hardware.location.NanoAppState;

import com.google.android.chre.utils.pigweed.ChreCallbackHandler;
import com.google.android.chre.utils.pigweed.ChreChannelOutput;
import com.google.android.utils.chre.ChreTestUtil;

import org.junit.Assert;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.pigweed.pw_rpc.Call.UnaryFuture;
import dev.pigweed.pw_rpc.Channel;
import dev.pigweed.pw_rpc.Client;
import dev.pigweed.pw_rpc.MethodClient;
import dev.pigweed.pw_rpc.Service;
import dev.pigweed.pw_rpc.UnaryResult;
import dev.pigweed.pw_rpc.proto.Echo;

/**
 * A class that can execute the test for the RPC service test nanoapp.
 */
public class ContextHubRpcServiceTestExecutor extends ContextHubClientCallback {
    private static final String TAG = "ContextHubRpcServiceTestExecutor";

    private final NanoAppBinary mNanoAppBinary;

    private final long mNanoAppId;

    private final ContextHubClient mContextHubClient;

    private final AtomicBoolean mChreReset = new AtomicBoolean(false);

    private final ContextHubManager mContextHubManager;

    private final ContextHubInfo mContextHubInfo;

    private final Client mRpcClient;
    private final Channel mChannel;
    private final ChreCallbackHandler mCallbackHandler;

    // TODO(b/218677634): Remove flag once pigweed RPC can be used in nanoapps.
    private final boolean mPwRpcEnabled = false;

    // The ID and version of the "rpc_service_test" nanoapp. Must be synchronized with the
    // value defined in the nanoapp code.
    private static final int NUM_RPC_SERVICES = 1;
    private static final long RPC_SERVICE_ID = 0xca8f7150a3f05847L;
    private static final int RPC_SERVICE_VERSION = 0x01020034;
    private static final String RPC_ECHO_STRING = "HELLO_WORLD";

    public ContextHubRpcServiceTestExecutor(
                ContextHubManager manager, ContextHubInfo info, NanoAppBinary binary) {
        mContextHubManager = manager;
        mContextHubInfo = info;
        mNanoAppBinary = binary;
        mNanoAppId = mNanoAppBinary.getNanoAppId();

        mContextHubClient = mContextHubManager.createClient(mContextHubInfo, this);
        Assert.assertTrue(mContextHubClient != null);

        Service echoService = new Service("pw.rpc.EchoService",
                Service.unaryMethod("Echo", Echo.EchoMessage.class,
                        Echo.EchoMessage.class));
        ChreChannelOutput channelOutput = new ChreChannelOutput(mContextHubClient, mNanoAppId);
        mChannel = new Channel(channelOutput.getChannelId(), channelOutput);
        mRpcClient = Client.create(List.of(mChannel), List.of(echoService));
        mCallbackHandler =
                new ChreCallbackHandler(mContextHubClient, mNanoAppId, mRpcClient, channelOutput);
    }

    @Override
    public void onHubReset(ContextHubClient client) {
        mChreReset.set(true);
        mCallbackHandler.onHubReset();
    }

    @Override
    public void onMessageFromNanoApp(ContextHubClient client, NanoAppMessage message) {
        mCallbackHandler.onMessageFromNanoApp(message);
    }

    /**
     * Should be invoked before run() is invoked to set up the test, e.g. in a @Before method.
     */
    public void init() {
        ChreTestUtil.loadNanoAppAssertSuccess(mContextHubManager, mContextHubInfo, mNanoAppBinary);
    }

    /**
     * The test code, e.g. run in @Test method
     */
    public void run() throws Exception {
        List<NanoAppState> stateList =
                    ChreTestUtil.queryNanoAppsAssertSuccess(mContextHubManager, mContextHubInfo);
        boolean serviceFound = false;
        for (NanoAppState state : stateList) {
            if (state.getNanoAppId() == mNanoAppId) {
                Assert.assertEquals(state.getRpcServices().size(), NUM_RPC_SERVICES);

                Assert.assertEquals(state.getRpcServices().get(0).getId(), RPC_SERVICE_ID);
                Assert.assertEquals(
                            state.getRpcServices().get(0).getVersion(), RPC_SERVICE_VERSION);
                serviceFound = true;
                break;
            }
        }
        Assert.assertTrue(serviceFound);

        if (mPwRpcEnabled) {
            MethodClient methodClient = mRpcClient.method(mChannel.id(), "pw.rpc.EchoService.Echo");
            Assert.assertNotNull(methodClient);

            Echo.EchoMessage message =
                    Echo.EchoMessage.newBuilder().setMsg(RPC_ECHO_STRING).build();
            UnaryFuture<Echo.EchoMessage> responseFuture = methodClient.invokeUnaryFuture(message);

            UnaryResult<Echo.EchoMessage> responseResult = responseFuture.get(2, TimeUnit.SECONDS);
            Assert.assertNotNull(responseResult);
            Assert.assertTrue(responseResult.status().ok());

            Echo.EchoMessage response = responseResult.response();
            Assert.assertNotNull(response);
            Assert.assertEquals(RPC_ECHO_STRING, response.getMsg());
        }
    }

    /**
     * Cleans up the test, should be invoked in e.g. @After method.
     */
    public void deinit() {
        if (mChreReset.get()) {
            Assert.fail("CHRE reset during the test");
        }

        ChreTestUtil.unloadNanoAppAssertSuccess(mContextHubManager, mContextHubInfo, mNanoAppId);
        mContextHubClient.close();
    }
}