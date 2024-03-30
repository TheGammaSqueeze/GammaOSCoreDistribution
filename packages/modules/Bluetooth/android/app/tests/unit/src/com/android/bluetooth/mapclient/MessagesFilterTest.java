/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.bluetooth.mapclient;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class MessagesFilterTest {

    @Test
    public void setOriginator() {
        MessagesFilter filter = new MessagesFilter();

        String originator = "test_originator";
        filter.setOriginator(originator);
        assertThat(filter.originator).isEqualTo(originator);

        filter.setOriginator("");
        assertThat(filter.originator).isEqualTo(null); // Empty string is stored as null

        filter.setOriginator(null);
        assertThat(filter.originator).isEqualTo(null);
    }

    @Test
    public void setPriority() {
        MessagesFilter filter = new MessagesFilter();

        byte priority = 5;
        filter.setPriority(priority);

        assertThat(filter.priority).isEqualTo(priority);
    }

    @Test
    public void setReadStatus() {
        MessagesFilter filter = new MessagesFilter();

        byte readStatus = 5;
        filter.setReadStatus(readStatus);

        assertThat(filter.readStatus).isEqualTo(readStatus);
    }

    @Test
    public void setRecipient() {
        MessagesFilter filter = new MessagesFilter();

        String recipient = "test_originator";
        filter.setRecipient(recipient);
        assertThat(filter.recipient).isEqualTo(recipient);

        filter.setRecipient("");
        assertThat(filter.recipient).isEqualTo(null); // Empty string is stored as null

        filter.setRecipient(null);
        assertThat(filter.recipient).isEqualTo(null);
    }

}
