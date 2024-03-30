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
package com.android.server.appsearch;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;


import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.UiAutomation;
import android.app.appsearch.AppSearchResult;
import android.app.appsearch.aidl.AppSearchResultParcel;
import android.app.appsearch.aidl.IAppSearchManager;
import android.app.appsearch.aidl.IAppSearchResultCallback;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.util.ArrayMap;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.dx.mockito.inline.extended.StaticMockitoSessionBuilder;
import com.android.modules.utils.testing.StaticMockFixture;
import com.android.modules.utils.testing.StaticMockFixtureRule;
import com.android.server.LocalManagerRegistry;
import com.android.server.usage.StorageStatsManagerLocal;

import com.google.common.util.concurrent.SettableFuture;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import java.util.Map;

public class AppSearchManagerServiceTest {
    private final MockServiceManager mMockServiceManager = new MockServiceManager();
    private final Map<UserHandle, PackageManager> mMockPackageManagers = new ArrayMap<>();

    @Rule
    public StaticMockFixtureRule mStaticMockFixtureRule =
            new StaticMockFixtureRule(mMockServiceManager);

    private Context mContext;
    private AppSearchManagerService mAppSearchManagerService;
    private UserHandle mUserHandle;
    private UiAutomation mUiAutomation;
    private IAppSearchManager.Stub mAppSearchManagerServiceStub;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        mUserHandle = context.getUser();
        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mContext = new ContextWrapper(context) {
            @Override
            public Intent registerReceiverForAllUsers(
                    @Nullable BroadcastReceiver receiver,
                    @NonNull IntentFilter filter,
                    @Nullable String broadcastPermission,
                    @Nullable Handler scheduler) {
                // Do nothing
                return null;
            }

            @Override
            public Context createContextAsUser(UserHandle user, int flags) {
                return new ContextWrapper(super.createContextAsUser(user, flags)) {
                    @Override
                    public PackageManager getPackageManager() {
                        return getMockPackageManager(user);
                    }
                };
            }

            @Override
            public PackageManager getPackageManager() {
                return createContextAsUser(getUser(), /*flags=*/ 0).getPackageManager();
            }
        };
        // Start the service
        mAppSearchManagerService = new AppSearchManagerService(mContext);
        mAppSearchManagerService.onStart();
        mAppSearchManagerServiceStub = mMockServiceManager.mStubCaptor.getValue();
        assertThat(mAppSearchManagerServiceStub).isNotNull();
    }

    @Test
    public void testCallingPackageDoesntExistsInTargetUser() throws Exception {
        UserHandle testTargetUser = new UserHandle(1234);
        PackageManager mockPackageManager = mMockPackageManagers.get(mUserHandle);
        when(mockPackageManager.getPackageUid(mContext.getPackageName(), /*flags=*/0))
                .thenReturn(mContext.getAttributionSource().getUid());

        mUiAutomation.adoptShellPermissionIdentity(Manifest.permission.INTERACT_ACROSS_USERS_FULL);
        try {
            // Try to initial a AppSearchSession for secondary user, but the calling package doesn't
            // exists in there.
            SettableFuture<AppSearchResult<Void>> future = SettableFuture.create();
            mAppSearchManagerServiceStub.initialize(
                    mContext.getAttributionSource(),
                    testTargetUser,
                    System.currentTimeMillis(),
                    new IAppSearchResultCallback.Stub() {
                        @Override
                        public void onResult(AppSearchResultParcel resultParcel) {
                            future.set(resultParcel.getResult());
                        }
                    });
            assertThat(future.get().isSuccess()).isFalse();
            assertThat(future.get().getErrorMessage()).contains(
                    "SecurityException: Package: " + mContext.getPackageName()
                            + " haven't installed for user "
                            + testTargetUser.getIdentifier());
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }
    }

    private static class MockServiceManager implements StaticMockFixture {
        ArgumentCaptor<IAppSearchManager.Stub> mStubCaptor =
                ArgumentCaptor.forClass(IAppSearchManager.Stub.class);

        @Override
        public StaticMockitoSessionBuilder setUpMockedClasses(
                @NonNull StaticMockitoSessionBuilder sessionBuilder) {
            sessionBuilder.mockStatic(LocalManagerRegistry.class);
            sessionBuilder.spyStatic(ServiceManager.class);
            return sessionBuilder;
        }

        @Override
        public void setUpMockBehaviors() {
            ExtendedMockito.doReturn(Mockito.mock(StorageStatsManagerLocal.class))
                    .when(() -> LocalManagerRegistry.getManager(StorageStatsManagerLocal.class));
            ExtendedMockito.doNothing().when(
                    () -> ServiceManager.addService(
                            anyString(), mStubCaptor.capture(), anyBoolean(), anyInt()));
        }

        @Override
        public void tearDown() {}
    }

    @NonNull
    private PackageManager getMockPackageManager(@NonNull UserHandle user) {
        PackageManager pm = mMockPackageManagers.get(user);
        if (pm == null) {
            pm = Mockito.mock(PackageManager.class);
            mMockPackageManagers.put(user, pm);
        }
        return pm;
    }
}
