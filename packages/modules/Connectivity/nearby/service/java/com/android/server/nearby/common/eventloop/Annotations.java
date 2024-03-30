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

package com.android.server.nearby.common.eventloop;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

import androidx.annotation.AnyThread;
import androidx.annotation.BinderThread;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * A collection of threading annotations relating to EventLoop. These should be used in conjunction
 * with {@link UiThread}, {@link BinderThread}, {@link WorkerThread}, and {@link AnyThread}.
 */
public class Annotations {

    /**
     * Denotes that the annotated method or constructor should only be called on the EventLoop
     * thread.
     */
    @Retention(CLASS)
    @Target({METHOD, CONSTRUCTOR, TYPE})
    public @interface EventThread {
    }

    /** Denotes that the annotated method or constructor should only be called on a Network
     * thread. */
    @Retention(CLASS)
    @Target({METHOD, CONSTRUCTOR, TYPE})
    public @interface NetworkThread {
    }
}
