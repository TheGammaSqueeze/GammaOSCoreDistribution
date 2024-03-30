/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.libraries.testing.deviceshadower.shadows.nfc;

import static org.robolectric.util.ReflectionHelpers.callConstructor;

import android.content.Context;
import android.nfc.NfcAdapter;

import com.android.libraries.testing.deviceshadower.Enums.NfcOperation;
import com.android.libraries.testing.deviceshadower.internal.DeviceShadowEnvironmentImpl;
import com.android.libraries.testing.deviceshadower.internal.nfc.INfcAdapterImpl;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/**
 * Shadow implementation of Nfc Adapter.
 */
@Implements(NfcAdapter.class)
public class ShadowNfcAdapter {

    @Implementation
    public static NfcAdapter getDefaultAdapter(Context context) {
        if (DeviceShadowEnvironmentImpl.getLocalNfcletImpl()
                .shouldInterrupt(NfcOperation.GET_ADAPTER)) {
            return null;
        }
        ReflectionHelpers.setStaticField(NfcAdapter.class, "sService", new INfcAdapterImpl());
        return callConstructor(NfcAdapter.class, ClassParameter.from(Context.class, context));
    }

    // TODO(b/200231384): support state change.
    public ShadowNfcAdapter() {
    }
}
