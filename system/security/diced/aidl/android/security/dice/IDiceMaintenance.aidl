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

package android.security.dice;

import android.hardware.security.dice.InputValues;

/**
 * The maintenance allows callers to prompt the DICE node to demote itself.
 *
 * @hide
 */
@SensitiveData
interface IDiceMaintenance {
    /**
     * The implementation must demote itself by deriving new effective artifacts
     * based on the list of input data passed to the function.
     * As opposed to the IDiceNode::demote, this function effects all clients of
     * the implementation.
     *
     * ## Error as service specific exception:
     *     ResponseCode::PERMISSION_DENIED if the caller does not have the demote_self permission.
     *     May produce any ResponseCode if anything went wrong.
     */
    void demoteSelf(in InputValues[] input_values);
}
