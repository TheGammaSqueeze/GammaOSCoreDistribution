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

package com.android.bluetooth.pbapclient;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import android.accounts.Account;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BluetoothPbapVcardListTest {

    private static final Account ACCOUNT = mock(Account.class);

    @Test
    public void constructor_withMockInputStream_throwsIOException() {
        InputStream is = mock(InputStream.class);

        assertThrows(IOException.class, () ->
                new BluetoothPbapVcardList(ACCOUNT, is, PbapClientConnectionHandler.VCARD_TYPE_30));
        assertThrows(IOException.class, () ->
                new BluetoothPbapVcardList(ACCOUNT, is, PbapClientConnectionHandler.VCARD_TYPE_21));
    }
}
