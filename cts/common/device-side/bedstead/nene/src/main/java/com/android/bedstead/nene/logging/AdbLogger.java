/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.bedstead.nene.logging;

import android.util.Log;

import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * {@link Logger} which passes all logs to ADB.
 */
public final class AdbLogger implements Logger {

    public static final String KEY = "ADB";

    private final String mTag;

    AdbLogger(Object instance) {
        mTag = "NNL_" + instance.getClass().getCanonicalName() + "@"
                + Integer.toHexString(instance.hashCode());
    }

    @Override
    public void constructor(Runnable method) {
        constructor(method, new Object[]{});
    }

    @Override
    public void constructor(Object... args) {
        constructor(() -> {}, args);
    }

    @Override
    public void constructor(Object arg1, Runnable method) {
        constructor(method, arg1);
    }

    @Override
    public void constructor(Object arg1, Object arg2, Runnable method) {
        constructor(method, arg1, arg2);
    }

    @Override
    public void constructor(Object arg1, Object arg2, Object arg3, Runnable method) {
        constructor(method, arg1, arg2, arg3);
    }

    @Override
    public void constructor(Object arg1, Object arg2, Object arg3, Object arg4, Runnable method) {
        constructor(method, arg1, arg2, arg3, arg4);
    }

    @Override
    public void constructor(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5,
            Runnable method) {
        constructor(method, arg1, arg2, arg3, arg4, arg5);
    }

    @Override
    public void constructor(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5,
            Object arg6, Runnable method) {
        constructor(method, arg1, arg2, arg3, arg4, arg5, arg6);
    }

    @Override
    public void constructor(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5,
            Object arg6, Object arg7, Runnable method) {
        constructor(method, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
    }

    private void constructor(Runnable method, Object... args) {
        begin("constructor", args);
        try {
            method.run();
            end();
        } catch (Throwable t) {
            exception(t);
            throw t;
        }
    }

    @Override
    public void method(String name, Runnable method) {
        method(method, name);
    }

    @Override
    public void method(String name, Object arg1, Runnable method) {
        method(method, name, arg1);
    }

    @Override
    public void method(String name, Object arg1, Object arg2, Runnable method) {
        method(method, name, arg1, arg2);
    }

    @Override
    public void method(String name, Object arg1, Object arg2, Object arg3, Runnable method) {
        method(method, name, arg1, arg2, arg3);
    }

    @Override
    public void method(String name, Object arg1, Object arg2, Object arg3, Object arg4,
            Runnable method) {
        method(method, name, arg1, arg2, arg3, arg4);
    }

    @Override
    public void method(String name, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5,
            Runnable method) {
        method(method, name, arg1, arg2, arg3, arg4, arg5);
    }

    @Override
    public void method(String name, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5,
            Object arg6, Runnable method) {
        method(method, name, arg1, arg2, arg3, arg4, arg5, arg6);
    }

    @Override
    public void method(String name, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5,
            Object arg6, Object arg7, Runnable method) {
        method(method, name, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
    }

    @Override
    public <T extends Throwable> void method(Class<T> throwableClass, String name,
            RunnableThrows<T> method) throws T {
        method(method, name);
    }

    @Override
    public <T extends Throwable> void method(Class<T> throwableClass, String name, Object arg1,
            RunnableThrows<T> method) throws T {
        method(method, name, arg1);
    }

    @Override
    public <T extends Throwable> void method(Class<T> throwableClass, String name, Object arg1,
            Object arg2, RunnableThrows<T> method) throws T {
        method(method, name, arg1, arg2);
    }

    @Override
    public <T extends Throwable> void method(Class<T> throwableClass, String name, Object arg1,
            Object arg2, Object arg3, RunnableThrows<T> method) throws T {
        method(method, name, arg1, arg2, arg3);
    }

    @Override
    public <T extends Throwable> void method(Class<T> throwableClass, String name, Object arg1,
            Object arg2, Object arg3, Object arg4, RunnableThrows<T> method) throws T {
        method(method, name, arg1, arg2, arg3, arg4);
    }

    @Override
    public <T extends Throwable> void method(Class<T> throwableClass, String name, Object arg1,
            Object arg2, Object arg3, Object arg4, Object arg5, RunnableThrows<T> method) throws T {
        method(method, name, arg1, arg2, arg3, arg4, arg5);
    }

    @Override
    public <T extends Throwable> void method(Class<T> throwableClass, String name, Object arg1,
            Object arg2, Object arg3, Object arg4, Object arg5, Object arg6,
            RunnableThrows<T> method) throws T {
        method(method, name, arg1, arg2, arg3, arg4, arg5, arg6);
    }

    @Override
    public <T extends Throwable> void method(Class<T> throwableClass, String name, Object arg1,
            Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            RunnableThrows<T> method) throws T {
        method(method, name, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
    }

    private void method(Runnable method, String name, Object... args) {
        begin(name, args);
        try {
            method.run();
            end();
        } catch (Throwable t) {
            exception(t);
            throw t;
        }
    }

    private <T extends Throwable> void method(
            RunnableThrows<T> method, String name, Object... args) throws T {
        begin(name, args);
        try {
            method.run();
            end();
        } catch (Throwable t) {
            exception(t);
            throw t;
        }
    }

    @Override
    public <R> R method(String name, Supplier<R> method) {
        return method(method, name);
    }

    @Override
    public <R> R method(String name, Object arg1, Supplier<R> method) {
        return method(method, name, arg1);
    }

    @Override
    public <R> R method(String name, Object arg1, Object arg2, Supplier<R> method) {
        return method(method, name, arg1, arg2);
    }

    @Override
    public <R> R method(String name, Object arg1, Object arg2, Object arg3, Supplier<R> method) {
        return method(method, name, arg1, arg2, arg3);
    }

    @Override
    public <R> R method(String name, Object arg1, Object arg2, Object arg3, Object arg4,
            Supplier<R> method) {
        return method(method, name, arg1, arg2, arg3, arg4);
    }

    @Override
    public <R> R method(String name, Object arg1, Object arg2, Object arg3, Object arg4,
            Object arg5, Supplier<R> method) {
        return method(method, name, arg1, arg2, arg3, arg4, arg5);
    }

    @Override
    public <R> R method(String name, Object arg1, Object arg2, Object arg3, Object arg4,
            Object arg5, Object arg6, Supplier<R> method) {
        return method(method, name, arg1, arg2, arg3, arg4, arg5, arg6);
    }

    @Override
    public <R> R method(String name, Object arg1, Object arg2, Object arg3, Object arg4,
            Object arg5, Object arg6, Object arg7, Supplier<R> method) {
        return method(method, name, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
    }

    @Override
    public <T extends Throwable, R> R method(Class<T> throwableClass, String name,
            SupplierThrows<R, T> method) throws T {
        return method(method, name);
    }

    @Override
    public <T extends Throwable, R> R method(Class<T> throwableClass, String name, Object arg1,
            SupplierThrows<R, T> method) throws T {
        return method(method, name, arg1);
    }

    @Override
    public <T extends Throwable, R> R method(Class<T> throwableClass, String name, Object arg1,
            Object arg2, SupplierThrows<R, T> method) throws T {
        return method(method, name, arg1, arg2);
    }

    @Override
    public <T extends Throwable, R> R method(Class<T> throwableClass, String name, Object arg1,
            Object arg2, Object arg3, SupplierThrows<R, T> method) throws T {
        return method(method, name, arg1, arg2, arg3);
    }

    @Override
    public <T extends Throwable, R> R method(Class<T> throwableClass, String name, Object arg1,
            Object arg2, Object arg3, Object arg4, SupplierThrows<R, T> method) throws T {
        return method(method, name, arg1, arg2, arg3, arg4);
    }

    @Override
    public <T extends Throwable, R> R method(Class<T> throwableClass, String name, Object arg1,
            Object arg2, Object arg3, Object arg4, Object arg5, SupplierThrows<R, T> method)
            throws T {
        return method(method, name, arg1, arg2, arg3, arg4, arg5);
    }

    @Override
    public <T extends Throwable, R> R method(Class<T> throwableClass, String name, Object arg1,
            Object arg2, Object arg3, Object arg4, Object arg5, Object arg6,
            SupplierThrows<R, T> method) throws T {
        return method(method, name, arg1, arg2, arg3, arg4, arg5, arg6);
    }

    @Override
    public <T extends Throwable, R> R method(Class<T> throwableClass, String name, Object arg1,
            Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            SupplierThrows<R, T> method) throws T {
        return method(method, name, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
    }

    private <R> R method(Supplier<R> method, String name, Object... args) {
        begin(name, args);
        try {
            R value = method.get();
            end(value);
            return value;
        } catch (Throwable t) {
            exception(t);
            throw t;
        }
    }

    private <R, T extends Throwable> R method(
            SupplierThrows<R, T> method, String name, Object... args) throws T {
        begin(name, args);
        try {
            R value = method.get();
            end(value);
            return value;
        } catch (Throwable t) {
            exception(t);
            throw t;
        }
    }

    private void begin(String title, Object... args) {
        Log.i(mTag, "BEGIN " + title + formatArgs(args));
    }

    private void exception(Throwable t) {
        Log.i(mTag, "END EXCEPTION ("  + t + ")");
    }

    private void end(Object ret) {
        Log.i(mTag, "END ("  + ret + ")");
    }

    private void end() {
        Log.i(mTag, "END");
    }

    private String formatArgs(Object[] args) {
        if (args.length == 0) {
            return "";
        }

        return " (" + Arrays.stream(args).map(Object::toString)
                .collect(Collectors.joining(", ")) + ")";
    }
}
