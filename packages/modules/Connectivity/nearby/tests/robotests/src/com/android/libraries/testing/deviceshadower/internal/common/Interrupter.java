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

package com.android.libraries.testing.deviceshadower.internal.common;

import com.android.libraries.testing.deviceshadower.Enums;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Interrupter sets and checks interruptible point, and interrupt operation by throwing
 * IOException.
 */
public class Interrupter {

    private final InheritableThreadLocal<Integer> mCurrentIdentifier;
    private int mInterruptIdentifier;

    private final Set<Enums.Operation> mInterruptOperations = new HashSet<>();

    public Interrupter() {
        mCurrentIdentifier = new InheritableThreadLocal<Integer>() {
            @Override
            protected Integer initialValue() {
                return -1;
            }
        };
    }

    public void checkInterrupt() throws IOException {
        if (mCurrentIdentifier.get() == mInterruptIdentifier) {
            throw new IOException(
                    "Bluetooth interrupted at identifier: " + mCurrentIdentifier.get());
        }
    }

    public void setInterruptible(int identifier) {
        mCurrentIdentifier.set(identifier);
    }

    public void interrupt(int identifier) {
        mInterruptIdentifier = identifier;
    }

    public void addInterruptOperation(Enums.Operation operation) {
        mInterruptOperations.add(operation);
    }

    public boolean shouldInterrupt(Enums.Operation operation) {
        return mInterruptOperations.contains(operation);
    }

}
