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
package com.google.android.chre.utils.pigweed;

import static android.hardware.location.ContextHubManager.AUTHORIZATION_DENIED;
import static android.hardware.location.ContextHubManager.AUTHORIZATION_GRANTED;

import android.hardware.location.ContextHubClient;
import android.hardware.location.ContextHubClientCallback;
import android.hardware.location.ContextHubIntentEvent;
import android.hardware.location.NanoAppMessage;

import dev.pigweed.pw_rpc.Client;

/**
 * Implementation that can be used to ensure callbacks from the ContextHub APIs are properly handled
 * when using pw_rpc.
 */
public class ChreCallbackHandler {
    private final ContextHubClient mHubClient;
    private final long mNanoappId;
    private final Client mRpcClient;
    private final ChreChannelOutput mChannelOutput;

    public ChreCallbackHandler(ContextHubClient hubClient,
            long nanoappId, Client rpcClient, ChreChannelOutput channelOutput) {
        mHubClient = hubClient;
        mNanoappId = nanoappId;
        mRpcClient = rpcClient;
        mChannelOutput = channelOutput;
    }

    /**
     * This must be called directly from the equivalent {@link ContextHubClientCallback} method
     * or after decoding a {@link ContextHubIntentEvent} of type
     * {@link ContextHubManager.EVENT_NANOAPP_MESSAGE}.
     *
     * This method passes the message to pigweed RPC for decoding.
     */
    public void onMessageFromNanoApp(NanoAppMessage message) {
        if (message.getNanoAppId() == mNanoappId) {
            mRpcClient.processPacket(message.getMessageBody());
        }
    }

    /**
     * This must be called directly from the equivalent {@link ContextHubClientCallback} method
     * or after decoding a {@link ContextHubIntentEvent} of type
     * {@link ContextHubManager.EVENT_HUB_RESET}.
     *
     * This method ensures all outstanding RPCs are canceled.
     */
    public void onHubReset() {
        // TODO(b/210138227): Close all outsanding RPCs.
    }


    /**
     * This must be called directly from the equivalent {@link ContextHubClientCallback} method
     * or after decoding a {@link ContextHubIntentEvent} of type
     * {@link ContextHubManager.EVENT_NANOAPP_UNLOADED}.
     *
     * This method ensures all outstanding RPCs are canceled.
     */
    public void onNanoappUnloaded(long nanoappId) {
        if (nanoappId == mNanoappId) {
            // TODO(b/210138227): Close all outsanding RPCs.
        }
    }

    /**
     * This must be called directly from the equivalent {@link ContextHubClientCallback} method
     * or after decoding a {@link ContextHubIntentEvent} of type
     * {@link ContextHubManager.EVENT_NANOAPP_DISABLED}.
     *
     * This method ensures all outstanding RPCs are canceled.
     */
    public void onNanoappDisabled(long nanoappId) {
        if (nanoappId == mNanoappId) {
            // TODO(b/210138227): Close all outsanding RPCs.
        }
    }

    /**
     * This must be called directly from the equivalent {@link ContextHubClientCallback} method
     * or after decoding a {@link ContextHubIntentEvent} of type
     * {@link ContextHubManager.EVENT_NANOAPP_ABORTED}.
     *
     * This method ensures all outstanding RPCs are canceled.
     */
    public void onNanoppAborted(long nanoappId) {
        if (nanoappId == mNanoappId) {
            // TODO(b/210138227): Close all outsanding RPCs.
        }
    }

    /**
     * This must be called directly from the equivalent {@link ContextHubClientCallback} method
     * or after decoding a {@link ContextHubIntentEvent} of type
     * {@link ContextHubManager.EVENT_CLIENT_AUTHORIZATION}.
     *
     * If the client is now unauthorized to communicate with the nanoapp, any future RPCs attempted
     * will fail until the client becomes authorized again.
     */
    public void onClientAuthorizationChanged(long nanoappId, int authorization) {
        if (nanoappId == mNanoappId) {
            if (authorization == AUTHORIZATION_DENIED) {
                mChannelOutput.setAuthDenied(true /* denied */);
                // TODO(b/210138227): Close all outsanding RPCs.
            } else if (authorization == AUTHORIZATION_GRANTED) {
                mChannelOutput.setAuthDenied(false /* denied */);
            }
        }
    }
}
