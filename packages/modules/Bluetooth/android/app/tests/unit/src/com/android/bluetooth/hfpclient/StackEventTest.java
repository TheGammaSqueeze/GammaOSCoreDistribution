/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.hfpclient;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class StackEventTest {

    @Test
    public void toString_returnsInfo() {
        int type = StackEvent.EVENT_TYPE_RING_INDICATION;

        StackEvent event = new StackEvent(type);
        String expectedString = "StackEvent {type:" + StackEvent.eventTypeToString(type)
                + ", value1:" + event.valueInt + ", value2:" + event.valueInt2 + ", value3:"
                + event.valueInt3 + ", value4:" + event.valueInt4 + ", string: \""
                + event.valueString + "\"" + ", device:" + event.device + "}";

        assertThat(event.toString()).isEqualTo(expectedString);
    }

    @Test
    public void eventTypeToString() {
        int invalidType = 23;

        assertThat(StackEvent.eventTypeToString(StackEvent.EVENT_TYPE_NONE)).isEqualTo(
                "EVENT_TYPE_NONE");
        assertThat(StackEvent.eventTypeToString(
                StackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED)).isEqualTo(
                "EVENT_TYPE_CONNECTION_STATE_CHANGED");
        assertThat(
                StackEvent.eventTypeToString(StackEvent.EVENT_TYPE_AUDIO_STATE_CHANGED)).isEqualTo(
                "EVENT_TYPE_AUDIO_STATE_CHANGED");
        assertThat(StackEvent.eventTypeToString(StackEvent.EVENT_TYPE_NETWORK_STATE)).isEqualTo(
                "EVENT_TYPE_NETWORK_STATE");
        assertThat(StackEvent.eventTypeToString(StackEvent.EVENT_TYPE_ROAMING_STATE)).isEqualTo(
                "EVENT_TYPE_ROAMING_STATE");
        assertThat(StackEvent.eventTypeToString(StackEvent.EVENT_TYPE_NETWORK_SIGNAL)).isEqualTo(
                "EVENT_TYPE_NETWORK_SIGNAL");
        assertThat(StackEvent.eventTypeToString(StackEvent.EVENT_TYPE_BATTERY_LEVEL)).isEqualTo(
                "EVENT_TYPE_BATTERY_LEVEL");
        assertThat(StackEvent.eventTypeToString(StackEvent.EVENT_TYPE_OPERATOR_NAME)).isEqualTo(
                "EVENT_TYPE_OPERATOR_NAME");
        assertThat(StackEvent.eventTypeToString(StackEvent.EVENT_TYPE_CALL)).isEqualTo(
                "EVENT_TYPE_CALL");
        assertThat(StackEvent.eventTypeToString(StackEvent.EVENT_TYPE_CALLSETUP)).isEqualTo(
                "EVENT_TYPE_CALLSETUP");
        assertThat(StackEvent.eventTypeToString(StackEvent.EVENT_TYPE_CALLHELD)).isEqualTo(
                "EVENT_TYPE_CALLHELD");
        assertThat(StackEvent.eventTypeToString(StackEvent.EVENT_TYPE_CLIP)).isEqualTo(
                "EVENT_TYPE_CLIP");
        assertThat(StackEvent.eventTypeToString(StackEvent.EVENT_TYPE_CALL_WAITING)).isEqualTo(
                "EVENT_TYPE_CALL_WAITING");
        assertThat(StackEvent.eventTypeToString(StackEvent.EVENT_TYPE_CURRENT_CALLS)).isEqualTo(
                "EVENT_TYPE_CURRENT_CALLS");
        assertThat(StackEvent.eventTypeToString(StackEvent.EVENT_TYPE_VOLUME_CHANGED)).isEqualTo(
                "EVENT_TYPE_VOLUME_CHANGED");
        assertThat(StackEvent.eventTypeToString(StackEvent.EVENT_TYPE_CMD_RESULT)).isEqualTo(
                "EVENT_TYPE_CMD_RESULT");
        assertThat(StackEvent.eventTypeToString(StackEvent.EVENT_TYPE_SUBSCRIBER_INFO)).isEqualTo(
                "EVENT_TYPE_SUBSCRIBER_INFO");
        assertThat(StackEvent.eventTypeToString(StackEvent.EVENT_TYPE_RESP_AND_HOLD)).isEqualTo(
                "EVENT_TYPE_RESP_AND_HOLD");
        assertThat(StackEvent.eventTypeToString(StackEvent.EVENT_TYPE_RING_INDICATION)).isEqualTo(
                "EVENT_TYPE_RING_INDICATION");
        assertThat(StackEvent.eventTypeToString(invalidType)).isEqualTo(
                "EVENT_TYPE_UNKNOWN:" + invalidType);
    }
}
