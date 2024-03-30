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

package com.android.server.nearby.fastpair.halfsheet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;

import com.android.server.nearby.common.locator.Locator;
import com.android.server.nearby.common.locator.LocatorContextWrapper;
import com.android.server.nearby.fastpair.FastPairController;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

import service.proto.Cache;

public class FastPairHalfSheetManagerTest {
    private static final String BLEADDRESS = "11:22:44:66";
    private static final String NAME = "device_name";
    private FastPairHalfSheetManager mFastPairHalfSheetManager;
    private Cache.ScanFastPairStoreItem mScanFastPairStoreItem;
    @Mock
    LocatorContextWrapper mContextWrapper;
    @Mock
    ResolveInfo mResolveInfo;
    @Mock
    PackageManager mPackageManager;
    @Mock
    Locator mLocator;
    @Mock
    FastPairController mFastPairController;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        mScanFastPairStoreItem = Cache.ScanFastPairStoreItem.newBuilder()
                .setAddress(BLEADDRESS)
                .setDeviceName(NAME)
                .build();
    }

    @Test
    public void verifyFastPairHalfSheetManagerBehavior() {
        mLocator.overrideBindingForTest(FastPairController.class, mFastPairController);
        ResolveInfo resolveInfo = new ResolveInfo();
        List<ResolveInfo> resolveInfoList = new ArrayList<>();

        mPackageManager = mock(PackageManager.class);
        when(mContextWrapper.getPackageManager()).thenReturn(mPackageManager);
        resolveInfo.activityInfo = new ActivityInfo();
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.sourceDir = "/apex/com.android.tethering";
        applicationInfo.packageName = "test.package";
        resolveInfo.activityInfo.applicationInfo = applicationInfo;
        resolveInfoList.add(resolveInfo);
        when(mPackageManager.queryIntentActivities(any(), anyInt())).thenReturn(resolveInfoList);
        when(mPackageManager.canRequestPackageInstalls()).thenReturn(false);

        mFastPairHalfSheetManager =
                new FastPairHalfSheetManager(mContextWrapper);

        when(mContextWrapper.getLocator()).thenReturn(mLocator);

        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);

        mFastPairHalfSheetManager.showHalfSheet(mScanFastPairStoreItem);

        verify(mContextWrapper, atLeastOnce())
                .startActivityAsUser(intentArgumentCaptor.capture(), eq(UserHandle.CURRENT));
    }

    @Test
    public void verifyFastPairHalfSheetManagerHalfSheetApkNotValidBehavior() {
        mLocator.overrideBindingForTest(FastPairController.class, mFastPairController);
        ResolveInfo resolveInfo = new ResolveInfo();
        List<ResolveInfo> resolveInfoList = new ArrayList<>();

        mPackageManager = mock(PackageManager.class);
        when(mContextWrapper.getPackageManager()).thenReturn(mPackageManager);
        resolveInfo.activityInfo = new ActivityInfo();
        ApplicationInfo applicationInfo = new ApplicationInfo();
        // application directory is wrong
        applicationInfo.sourceDir = "/apex/com.android.nearby";
        applicationInfo.packageName = "test.package";
        resolveInfo.activityInfo.applicationInfo = applicationInfo;
        resolveInfoList.add(resolveInfo);
        when(mPackageManager.queryIntentActivities(any(), anyInt())).thenReturn(resolveInfoList);
        when(mPackageManager.canRequestPackageInstalls()).thenReturn(false);

        mFastPairHalfSheetManager =
                new FastPairHalfSheetManager(mContextWrapper);

        when(mContextWrapper.getLocator()).thenReturn(mLocator);

        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);

        mFastPairHalfSheetManager.showHalfSheet(mScanFastPairStoreItem);

        verify(mContextWrapper, never())
                .startActivityAsUser(intentArgumentCaptor.capture(), eq(UserHandle.CURRENT));
    }
}
