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

package com.android.server.nearby.common.bluetooth.fastpair;

import com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.AccountKeyCharacteristic;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.NoSuchAlgorithmException;

/**
 * Unit tests for {@link AccountKeyGenerator}.
 */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class AccountKeyGeneratorTest {
    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void createAccountKey() throws NoSuchAlgorithmException {
        byte[] accountKey = AccountKeyGenerator.createAccountKey();

        assertThat(accountKey).hasLength(16);
        assertThat(accountKey[0]).isEqualTo(AccountKeyCharacteristic.TYPE);
    }
}
