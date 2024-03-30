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
package com.android.car.bluetooth;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Context.RegisterReceiverFlags;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.test.mock.MockContentResolver;
import android.util.Log;

import com.android.car.CarLocalServices;
import com.android.dx.mockito.inline.extended.StaticMockitoSessionBuilder;
import com.android.internal.util.Preconditions;
import com.android.internal.util.test.BroadcastInterceptingContext;
import com.android.internal.util.test.FakeSettingsProvider;

import org.junit.After;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.mockito.session.MockitoSessionBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Base class for Bluetooth tests, provides mocking of BluetoothAdapter. Also provides CarService,
 * and Settings-related, helpers.
 * <p>
 * Uses {@link com.android.dx.mockito.inline.extended.ExtendedMockito} to mock static and/or final
 * classes and methods.
 * <p>
 * Would have liked to extend {@link AbstractExtendedMockitoTestCase}, but there was
 * incompatibility between our Fake {@code Settings} and their {@code Spy} of {@code Settings}.
 * <p>
 * <b>Note: </b>when using this class, you must include the following
 * dependencies in {@code Android.bp} (or {@code Android.mk}:
 * <pre><code>
    jni_libs: [
        "libdexmakerjvmtiagent",
        "libstaticjvmtiagent",
    ],

   LOCAL_JNI_SHARED_LIBRARIES := \
      libdexmakerjvmtiagent \
      libstaticjvmtiagent \
 *  </code></pre>
 */
public abstract class AbstractExtendedMockitoBluetoothTestCase {
    private static final String TAG = AbstractExtendedMockitoBluetoothTestCase.class
            .getSimpleName();
    private static final boolean VERBOSE = false;

    final int mUserId = 16;
    @Mock UserManager mMockUserManager;

    private final List<Class<?>> mStaticSpiedClasses = new ArrayList<>();
    private MockitoSession mSession;

    @Before
    public final void startSession() {
        mSession = newSessionBuilder().startMocking();
    }

    @After
    public final void finishSession() {
        if (mSession != null) {
            mSession.finishMocking();
        } else {
            Log.w(TAG, getClass().getSimpleName() + ".finishSession(): no session");
        }
    }

    /**
     * Subclasses can use this method to initialize the Mockito session that's started before every
     * test on {@link #startSession()}.
     *
     * <p>Typically, it should be overridden when mocking static methods.
     */
    protected void onSessionBuilder(@NonNull CustomMockitoSessionBuilder session) {
        if (VERBOSE) Log.v(TAG, "onSessionBuilder()");
    }

    /**
     * Changes the value of the session created by
     * {@link #onSessionBuilder(CustomMockitoSessionBuilder)}.
     *
     * <p>By default it's set to {@link Strictness.LENIENT}, but subclasses can overwrite this
     * method to change the behavior.
     */
    @NonNull
    protected Strictness getSessionStrictness() {
        return Strictness.LENIENT;
    }

    @NonNull
    private MockitoSessionBuilder newSessionBuilder() {
        StaticMockitoSessionBuilder builder = mockitoSession()
                .strictness(getSessionStrictness());

        CustomMockitoSessionBuilder customBuilder =
                new CustomMockitoSessionBuilder(builder, mStaticSpiedClasses);

        onSessionBuilder(customBuilder);

        if (VERBOSE) Log.v(TAG, "spied classes" + customBuilder.mStaticSpiedClasses);

        return builder.initMocks(this);
    }

    /**
     * Asserts the given class is being spied in the Mockito session.
     */
    protected void assertSpied(Class<?> clazz) {
        Preconditions.checkArgument(mStaticSpiedClasses.contains(clazz),
                "did not call spyStatic() on %s", clazz.getName());
    }

    /**
     * Custom {@code MockitoSessionBuilder} used to make sure some pre-defined mock stations fail
     * if the test case didn't explicitly set it to spy / mock the required classes.
     *
     * <p><b>NOTE: </b>for now it only provides simple {@link #spyStatic(Class)}, but more methods
     * (as provided by {@link StaticMockitoSessionBuilder}) could be provided as needed.
     */
    public static final class CustomMockitoSessionBuilder {
        private final StaticMockitoSessionBuilder mBuilder;
        private final List<Class<?>> mStaticSpiedClasses;

        private CustomMockitoSessionBuilder(StaticMockitoSessionBuilder builder,
                List<Class<?>> staticSpiedClasses) {
            mBuilder = builder;
            mStaticSpiedClasses = staticSpiedClasses;
        }

        /**
         * Same as {@link StaticMockitoSessionBuilder#spyStatic(Class)}.
         */
        public <T> CustomMockitoSessionBuilder spyStatic(Class<T> clazz) {
            Preconditions.checkState(!mStaticSpiedClasses.contains(clazz),
                    "already called spyStatic() on " + clazz);
            mStaticSpiedClasses.add(clazz);
            mBuilder.spyStatic(clazz);
            return this;
        }
    }

    /**
     * Mocks a call to {@link CarLocalServices#getService(Class)}.
     *
     * @throws IllegalStateException if class didn't override {@link #newSessionBuilder()} and
     * called {@code spyStatic(CarLocalServices.class)} on the session passed to it.
     *
     * (Same as {@link AbstractExtendedMockitoCarServiceTestCase#mockGetCarLocalService})
     */
    protected final <T> void mockGetCarLocalService(@NonNull Class<T> type, @NonNull T service) {
        if (VERBOSE) Log.v(TAG, "mockGetLocalService(" + type.getName() + ")");
        assertSpied(CarLocalServices.class);

        doReturn(service).when(() -> CarLocalServices.getService(type));
    }

    /**
     * Set the status of a user ID
     */
    public final void setUserUnlocked(int userId, boolean status) {
        when(mMockUserManager.isUserUnlocked(userId)).thenReturn(status);
        when(mMockUserManager.isUserUnlocked(UserHandle.of(userId))).thenReturn(status);
    }

    /**
     * For calls to {@code Settings}.
     */
    public class MockContext extends BroadcastInterceptingContext {
        private MockContentResolver mContentResolver;
        private FakeSettingsProvider mContentProvider;

        private final HashMap<String, Object> mMockedServices;

        MockContext(Context base) {
            super(base);
            FakeSettingsProvider.clearSettingsProvider();
            mContentResolver = new MockContentResolver(this);
            mContentProvider = new FakeSettingsProvider();
            mContentResolver.addProvider(Settings.AUTHORITY, mContentProvider);
            mMockedServices = new HashMap<String, Object>();
        }

        public void release() {
            FakeSettingsProvider.clearSettingsProvider();
        }

        @Override
        public ContentResolver getContentResolver() {
            return mContentResolver;
        }

        @Override
        public Context createContextAsUser(UserHandle user, @CreatePackageOptions int flags) {
            return this;
        }

        @Override
        public Intent registerReceiverAsUser(BroadcastReceiver receiver, UserHandle user,
                IntentFilter filter, String broadcastPermission, Handler scheduler,
                @RegisterReceiverFlags int flags) {
            throw new UnsupportedOperationException("Use createContextAsUser/registerReceiver");
        }

        public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter,
                @RegisterReceiverFlags int flags) {
            // BroadcastInterceptingReceiver doesn't have the variant with flags so just pass the
            // parameters to the function below for the test, as the flags don't really matter
            // for the purpose of getting our test broadcasts routed back to us
            return super.registerReceiver(receiver, filter, null, null);
        }

        public void addMockedSystemService(Class<?> serviceClass, Object service) {
            if (service == null) return;
            String name = getSystemServiceName(serviceClass);
            if (name == null) return;
            mMockedServices.put(name, service);
        }

        @Override
        public @Nullable Object getSystemService(String name) {
            if ((name != null) && name.equals(getSystemServiceName(UserManager.class))) {
                return mMockUserManager;
            } else if ((name != null) && mMockedServices.containsKey(name)) {
                return mMockedServices.get(name);
            }
            return super.getSystemService(name);
        }
    }
}
