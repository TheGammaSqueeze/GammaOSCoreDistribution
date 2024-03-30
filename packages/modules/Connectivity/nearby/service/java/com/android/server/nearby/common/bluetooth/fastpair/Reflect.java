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

package com.android.server.nearby.common.bluetooth.fastpair;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Utilities for calling methods using reflection. The main benefit of using this helper is to avoid
 * complications around exception handling when calling methods reflectively. It's not safe to use
 * Java 8's multicatch on such exceptions, because the java compiler converts multicatch into
 * ReflectiveOperationException in some instances, which doesn't work on older sdk versions.
 * Instead, use these utilities and catch ReflectionException.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * try {
 *   Reflect.on(btAdapter)
 *       .withMethod("setScanMode", int.class)
 *       .invoke(BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)
 * } catch (ReflectionException e) { }
 * }</pre>
 */
// TODO(b/202549655): remove existing Reflect usage. New usage is not allowed! No exception!
public final class Reflect {
    private final Object mTargetObject;

    private Reflect(Object targetObject) {
        this.mTargetObject = targetObject;
    }

    /** Creates an instance of this helper to invoke methods on the given target object. */
    public static Reflect on(Object targetObject) {
        return new Reflect(targetObject);
    }

    /** Finds a method with the given name and parameter types. */
    public ReflectionMethod withMethod(String methodName, Class<?>... paramTypes)
            throws ReflectionException {
        try {
            return new ReflectionMethod(mTargetObject.getClass().getMethod(methodName, paramTypes));
        } catch (NoSuchMethodException e) {
            throw new ReflectionException(e);
        }
    }

    /** Represents an invokable method found reflectively. */
    public final class ReflectionMethod {
        private final Method mMethod;

        private ReflectionMethod(Method method) {
            this.mMethod = method;
        }

        /**
         * Invokes this instance method with the given parameters. The called method does not return
         * a value.
         */
        public void invoke(Object... parameters) throws ReflectionException {
            try {
                mMethod.invoke(mTargetObject, parameters);
            } catch (IllegalAccessException e) {
                throw new ReflectionException(e);
            } catch (InvocationTargetException e) {
                throw new ReflectionException(e);
            }
        }

        /**
         * Invokes this instance method with the given parameters. The called method returns a non
         * null value.
         */
        public Object get(Object... parameters) throws ReflectionException {
            Object value;
            try {
                value = mMethod.invoke(mTargetObject, parameters);
            } catch (IllegalAccessException e) {
                throw new ReflectionException(e);
            } catch (InvocationTargetException e) {
                throw new ReflectionException(e);
            }
            if (value == null) {
                throw new ReflectionException(new NullPointerException());
            }
            return value;
        }
    }
}
