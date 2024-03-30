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

package com.android.server.nearby.common.locator;

import android.content.Context;

/** Configures late bindings of service types to their concrete implementations. */
public abstract class Module {
    /**
     * Configures the binding between the {@code type} and its implementation by calling methods on
     * the {@code locator}, for example:
     *
     * <pre>{@code
     * void configure(Context context, Class<?> type, Locator locator) {
     *   if (type == MyService.class) {
     *     locator.bind(MyService.class, new MyImplementation(context));
     *   }
     * }
     * }</pre>
     *
     * <p>If the module does not recognize the specified type, the method does not have to do
     * anything.
     */
    public abstract void configure(Context context, Class<?> type, Locator locator);

    /**
     * Notifies you that a binding of class {@code type} is no longer needed and can now release
     * everything it was holding on to, such as a database connection.
     *
     * <pre>{@code
     * void destroy(Context context, Class<?> type, Object instance) {
     *   if (type == MyService.class) {
     *     ((MyService) instance).destroy();
     *   }
     * }
     * }</pre>
     *
     * <p>If the module does not recognize the specified type, the method does not have to do
     * anything.
     */
    public void destroy(Context context, Class<?> type, Object instance) {}
}

