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

package com.android.libraries.testing.deviceshadower.testcases;

import static org.mockito.ArgumentMatchers.argThat;

import android.bluetooth.BluetoothSocket;

import org.mockito.ArgumentMatcher;

/**
 * Convenient methods to create mockito matchers.
 */
public class Matchers {

    private Matchers() {
    }

    public static <T extends Exception> T exception(final Class<T> clazz, final String... msgs) {
        return argThat(
                new ArgumentMatcher<T>() {
                    @Override
                    public boolean matches(T obj) {
                        if (!clazz.isInstance(obj)) {
                            return false;
                        }
                        Throwable exception = clazz.cast(obj);
                        for (String msg : msgs) {
                            if (exception == null || !exception.getMessage().contains(msg)) {
                                return false;
                            }
                            exception = exception.getCause();
                        }
                        return true;
                    }
                });
    }

    public static BluetoothSocket socket(final String addr) {
        return argThat(
                new ArgumentMatcher<BluetoothSocket>() {
                    @Override
                    public boolean matches(BluetoothSocket obj) {
                        return ((BluetoothSocket) obj)
                                .getRemoteDevice()
                                .getAddress()
                                .toUpperCase()
                                .equals(addr.toUpperCase());
                    }
                });
    }
}
