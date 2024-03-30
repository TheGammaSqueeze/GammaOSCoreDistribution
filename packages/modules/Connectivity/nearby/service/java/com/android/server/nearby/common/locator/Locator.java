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

import android.annotation.Nullable;
import android.content.Context;
import android.content.ContextWrapper;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Collection of bindings that map service types to their respective implementation(s). */
public class Locator {
    private static final Object UNBOUND = new Object();
    private final Context mContext;
    @Nullable
    private Locator mParent;
    private final String mTag; // For debugging
    private final Map<Class<?>, Object> mBindings = new HashMap<>();
    private final ArrayList<Module> mModules = new ArrayList<>();

    /** Thrown upon attempt to bind an interface twice. */
    public static class DuplicateBindingException extends RuntimeException {
        DuplicateBindingException(String msg) {
            super(msg);
        }
    }

    /** Constructor with a null parent. */
    public Locator(Context context) {
        this(context, null);
    }

    /**
     * Constructor. Supply a valid context and the Locator's parent.
     *
     * <p>To find a suitable parent you may want to use findLocator.
     */
    public Locator(Context context, @Nullable Locator parent) {
        this.mContext = context;
        this.mParent = parent;
        this.mTag = context.getClass().getName();
    }

    /** Attaches the parent to the locator. */
    public void attachParent(Locator parent) {
        this.mParent = parent;
    }

    /** Associates the specified type with the supplied instance. */
    public <T extends Object> Locator bind(Class<T> type, T instance) {
        bindKeyValue(type, instance);
        return this;
    }

    /** For tests only. Disassociates the specified type from any instance. */
    @VisibleForTesting
    public <T extends Object> Locator overrideBindingForTest(Class<T> type, T instance) {
        mBindings.remove(type);
        return bind(type, instance);
    }

    /** For tests only. Force Locator to return null when try to get an instance. */
    @VisibleForTesting
    public <T> Locator removeBindingForTest(Class<T> type) {
        Locator locator = this;
        do {
            locator.mBindings.put(type, UNBOUND);
            locator = locator.mParent;
        } while (locator != null);
        return this;
    }

    /** Binds a module. */
    public synchronized Locator bind(Module module) {
        mModules.add(module);
        return this;
    }

    /**
     * Searches the chain of locators for a binding for the given type.
     *
     * @throws IllegalStateException if no binding is found.
     */
    public <T> T get(Class<T> type) {
        T instance = getOptional(type);
        if (instance != null) {
            return instance;
        }

        String errorMessage = getUnboundErrorMessage(type);
        throw new IllegalStateException(errorMessage);
    }

    private String getUnboundErrorMessage(Class<?> type) {
        StringBuilder sb = new StringBuilder();
        sb.append("Unbound type: ").append(type.getName()).append("\n").append(
                "Searched locators:\n");
        Locator locator = this;
        while (true) {
            sb.append(locator.mTag);
            locator = locator.mParent;
            if (locator == null) {
                break;
            }
            sb.append(" ->\n");
        }
        return sb.toString();
    }

    /**
     * Searches the chain of locators for a binding for the given type. Returns null if no locator
     * was
     * found.
     */
    @Nullable
    public <T> T getOptional(Class<T> type) {
        Locator locator = this;
        do {
            T instance = locator.getInstance(type);
            if (instance != null) {
                return instance;
            }
            locator = locator.mParent;
        } while (locator != null);
        return null;
    }

    private synchronized <T extends Object> void bindKeyValue(Class<T> key, T value) {
        Object boundInstance = mBindings.get(key);
        if (boundInstance != null) {
            if (boundInstance == UNBOUND) {
                Log.w(mTag, "Bind call too late - someone already tried to get: " + key);
            } else {
                throw new DuplicateBindingException("Duplicate binding: " + key);
            }
        }
        mBindings.put(key, value);
    }

    // Suppress warning of cast from Object -> T
    @SuppressWarnings("unchecked")
    @Nullable
    private synchronized <T> T getInstance(Class<T> type) {
        if (mContext == null) {
            throw new IllegalStateException("Locator not initialized yet.");
        }

        T instance = (T) mBindings.get(type);
        if (instance != null) {
            return instance != UNBOUND ? instance : null;
        }

        // Ask modules to supply a binding
        int moduleCount = mModules.size();
        for (int i = 0; i < moduleCount; i++) {
            mModules.get(i).configure(mContext, type, this);
        }

        instance = (T) mBindings.get(type);
        if (instance == null) {
            mBindings.put(type, UNBOUND);
        }
        return instance;
    }

    /**
     * Iterates over all bound objects and gives the modules a chance to clean up the objects they
     * have created.
     */
    public synchronized void destroy() {
        for (Class<?> type : mBindings.keySet()) {
            Object instance = mBindings.get(type);
            if (instance == UNBOUND) {
                continue;
            }

            for (Module module : mModules) {
                module.destroy(mContext, type, instance);
            }
        }
        mBindings.clear();
    }

    /** Returns true if there are no bindings. */
    public boolean isEmpty() {
        return mBindings.isEmpty();
    }

    /** Returns the parent locator or null if no parent. */
    @Nullable
    public Locator getParent() {
        return mParent;
    }

    /**
     * Finds the first locator, then searches the chain of locators for a binding for the given
     * type.
     *
     * @throws IllegalStateException if no binding is found.
     */
    public static <T> T get(Context context, Class<T> type) {
        Locator locator = findLocator(context);
        if (locator == null) {
            throw new IllegalStateException("No locator found in context " + context);
        }
        return locator.get(type);
    }

    /**
     * Find the first locator from the context wrapper.
     */
    public static <T> T getFromContextWrapper(LocatorContextWrapper wrapper, Class<T> type) {
        Locator locator = wrapper.getLocator();
        if (locator == null) {
            throw new IllegalStateException("No locator found in context wrapper");
        }
        return locator.get(type);
    }

    /**
     * Finds the first locator, then searches the chain of locators for a binding for the given
     * type.
     * Returns null if no binding was found.
     */
    @Nullable
    public static <T> T getOptional(Context context, Class<T> type) {
        Locator locator = findLocator(context);
        if (locator == null) {
            return null;
        }
        return locator.getOptional(type);
    }

    /** Finds the first locator in the context hierarchy. */
    @Nullable
    public static Locator findLocator(Context context) {
        Context applicationContext = context.getApplicationContext();
        boolean applicationContextVisited = false;

        Context searchContext = context;
        do {
            Locator locator = tryGetLocator(searchContext);
            if (locator != null) {
                return locator;
            }

            applicationContextVisited |= (searchContext == applicationContext);

            if (searchContext instanceof ContextWrapper) {
                searchContext = ((ContextWrapper) context).getBaseContext();

                if (searchContext == null) {
                    throw new IllegalStateException(
                            "Invalid ContextWrapper -- If this is a Robolectric test, "
                                    + "have you called ActivityController.create()?");
                }
            } else if (!applicationContextVisited) {
                searchContext = applicationContext;
            } else {
                searchContext = null;
            }
        } while (searchContext != null);

        return null;
    }

    @Nullable
    private static Locator tryGetLocator(Object object) {
        if (object instanceof LocatorContext) {
            Locator locator = ((LocatorContext) object).getLocator();
            if (locator == null) {
                throw new IllegalStateException(
                        "LocatorContext must not return null Locator: " + object);
            }
            return locator;
        }
        return null;
    }
}
