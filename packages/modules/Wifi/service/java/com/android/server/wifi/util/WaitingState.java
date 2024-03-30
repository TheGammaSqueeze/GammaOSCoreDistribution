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

package com.android.server.wifi.util;

import android.os.Message;

import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

/**
 * A generic WaitingState which will defer all messages.
 *
 * Note: if the state machine implements local transitions as opposed to external transitions
 * (see https://en.wikipedia.org/wiki/UML_state_machine#Local_versus_external_transitions for a
 * description) then this state can be added under the state which it is supposed to be blocking -
 * it will prevent triggering local exit() and enter() methods on that state. However, the Android
 * state machine does a mix of local and external transitions. Therefore, if the class which is
 * being blocked has an enter() and/or exit() methods then modify the state machine structure:
 * - Create a new container state which will have the enter() and exit() methods
 * - Add both the original state (now without the enter() and exit() methods) and the waiting state
 *   to the container.
 */
public class WaitingState extends State {
    private final StateMachine mParentStateMachine;

    // message.what and message.getData() entries to try to guarantee no overlap with any containing
    // state
    private static final int MESSAGE_TYPE_TRANSITION = 0xFFFFFF;
    private static final String MESSAGE_BUNDLE_KEY_UNIQUE_ID = "__waiting_state_unique_key";

    public WaitingState(StateMachine parentStateMachine) {
        mParentStateMachine = parentStateMachine;
    }

    @Override
    public boolean processMessage(Message message) {
        if (message.what == MESSAGE_TYPE_TRANSITION && message.getData().getBoolean(
                MESSAGE_BUNDLE_KEY_UNIQUE_ID, false)) {
            mParentStateMachine.transitionTo((State) message.obj);
        } else {
            mParentStateMachine.deferMessage(message);
        }
        return HANDLED;
    }

    /**
     * Trigger a transition to another state.
     *
     * Note: has to be done as a message processing operation for the StateMachine to accept it
     * and trigger state transition, deferred message processing.
     */
    public void sendTransitionStateCommand(State destState) {
        Message message = mParentStateMachine.obtainMessage(MESSAGE_TYPE_TRANSITION, destState);
        message.getData().putBoolean(MESSAGE_BUNDLE_KEY_UNIQUE_ID, true);
        mParentStateMachine.sendMessage(message);
    }
}
