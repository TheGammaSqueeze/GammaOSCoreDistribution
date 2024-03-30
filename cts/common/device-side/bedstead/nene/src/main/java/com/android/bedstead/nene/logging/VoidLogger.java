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

import java.util.function.Supplier;

/**
 * {@link Logger} which drops all logs.
 */
public final class VoidLogger implements Logger {

    public static final VoidLogger sInstance = new VoidLogger();

    private VoidLogger() {

    }

    @Override
    public void constructor(Runnable method) {
        method.run();
    }

    @Override
    public void constructor(Object... args) {

    }

    @Override
    public void constructor(Object arg1, Runnable method) {
        method.run();
    }

    @Override
    public void constructor(Object arg1, Object arg2, Runnable method) {
        method.run();
    }

    @Override
    public void constructor(Object arg1, Object arg2, Object arg3, Runnable method) {
        method.run();
    }

    @Override
    public void constructor(Object arg1, Object arg2, Object arg3, Object arg4, Runnable method) {
        method.run();
    }

    @Override
    public void constructor(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5,
            Runnable method) {
        method.run();
    }

    @Override
    public void constructor(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5,
            Object arg6, Runnable method) {
        method.run();
    }

    @Override
    public void constructor(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5,
            Object arg6, Object arg7, Runnable method) {
        method.run();
    }

    @Override
    public void method(String name, Runnable method) {
        method.run();
    }

    @Override
    public void method(String name, Object arg1, Runnable method) {
        method.run();
    }

    @Override
    public void method(String name, Object arg1, Object arg2, Runnable method) {
        method.run();
    }

    @Override
    public void method(String name, Object arg1, Object arg2, Object arg3, Runnable method) {
        method.run();
    }

    @Override
    public void method(String name, Object arg1, Object arg2, Object arg3, Object arg4,
            Runnable method) {
        method.run();
    }

    @Override
    public void method(String name, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5,
            Runnable method) {
        method.run();
    }

    @Override
    public void method(String name, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5,
            Object arg6, Runnable method) {
        method.run();
    }

    @Override
    public void method(String name, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5,
            Object arg6, Object arg7, Runnable method) {
        method.run();
    }

    @Override
    public <T extends Throwable> void method(Class<T> throwableClass, String name,
            RunnableThrows<T> method) throws T {
        method.run();
    }

    @Override
    public <T extends Throwable> void method(Class<T> throwableClass, String name, Object arg1,
            RunnableThrows<T> method) throws T {
        method.run();
    }

    @Override
    public <T extends Throwable> void method(Class<T> throwableClass, String name, Object arg1,
            Object arg2, RunnableThrows<T> method) throws T {
        method.run();
    }

    @Override
    public <T extends Throwable> void method(Class<T> throwableClass, String name, Object arg1,
            Object arg2, Object arg3, RunnableThrows<T> method) throws T {
        method.run();
    }

    @Override
    public <T extends Throwable> void method(Class<T> throwableClass, String name, Object arg1,
            Object arg2, Object arg3, Object arg4, RunnableThrows<T> method) throws T {
        method.run();
    }

    @Override
    public <T extends Throwable> void method(Class<T> throwableClass, String name, Object arg1,
            Object arg2, Object arg3, Object arg4, Object arg5, RunnableThrows<T> method) throws T {
        method.run();
    }

    @Override
    public <T extends Throwable> void method(Class<T> throwableClass, String name, Object arg1,
            Object arg2, Object arg3, Object arg4, Object arg5, Object arg6,
            RunnableThrows<T> method) throws T {
        method.run();
    }

    @Override
    public <T extends Throwable> void method(Class<T> throwableClass, String name, Object arg1,
            Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            RunnableThrows<T> method) throws T {
        method.run();
    }

    @Override
    public <R> R method(String name, Supplier<R> method) {
        return method.get();
    }

    @Override
    public <R> R method(String name, Object arg1, Supplier<R> method) {
        return method.get();
    }

    @Override
    public <R> R method(String name, Object arg1, Object arg2, Supplier<R> method) {
        return method.get();
    }

    @Override
    public <R> R method(String name, Object arg1, Object arg2, Object arg3, Supplier<R> method) {
        return method.get();
    }

    @Override
    public <R> R method(String name, Object arg1, Object arg2, Object arg3, Object arg4,
            Supplier<R> method) {
        return method.get();
    }

    @Override
    public <R> R method(String name, Object arg1, Object arg2, Object arg3, Object arg4,
            Object arg5, Supplier<R> method) {
        return method.get();
    }

    @Override
    public <R> R method(String name, Object arg1, Object arg2, Object arg3, Object arg4,
            Object arg5, Object arg6, Supplier<R> method) {
        return method.get();
    }

    @Override
    public <R> R method(String name, Object arg1, Object arg2, Object arg3, Object arg4,
            Object arg5, Object arg6, Object arg7, Supplier<R> method) {
        return method.get();
    }

    @Override
    public <T extends Throwable, R> R method(Class<T> throwableClass, String name,
            SupplierThrows<R, T> method) throws T {
        return method.get();
    }

    @Override
    public <T extends Throwable, R> R method(Class<T> throwableClass, String name, Object arg1,
            SupplierThrows<R, T> method) throws T {
        return method.get();
    }

    @Override
    public <T extends Throwable, R> R method(Class<T> throwableClass, String name, Object arg1,
            Object arg2, SupplierThrows<R, T> method) throws T {
        return method.get();
    }

    @Override
    public <T extends Throwable, R> R method(Class<T> throwableClass, String name, Object arg1,
            Object arg2, Object arg3, SupplierThrows<R, T> method) throws T {
        return method.get();
    }

    @Override
    public <T extends Throwable, R> R method(Class<T> throwableClass, String name, Object arg1,
            Object arg2, Object arg3, Object arg4, SupplierThrows<R, T> method) throws T {
        return method.get();
    }

    @Override
    public <T extends Throwable, R> R method(Class<T> throwableClass, String name, Object arg1,
            Object arg2, Object arg3, Object arg4, Object arg5, SupplierThrows<R, T> method)
            throws T {
        return method.get();
    }

    @Override
    public <T extends Throwable, R> R method(Class<T> throwableClass, String name, Object arg1,
            Object arg2, Object arg3, Object arg4, Object arg5, Object arg6,
            SupplierThrows<R, T> method) throws T {
        return method.get();
    }

    @Override
    public <T extends Throwable, R> R method(Class<T> throwableClass, String name, Object arg1,
            Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
            SupplierThrows<R, T> method) throws T {
        return method.get();
    }
}
