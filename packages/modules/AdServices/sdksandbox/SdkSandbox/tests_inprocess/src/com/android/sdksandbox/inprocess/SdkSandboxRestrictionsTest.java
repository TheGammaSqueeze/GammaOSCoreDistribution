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

package com.android.sdksandbox.inprocess;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaDrm;
import android.media.UnsupportedSchemeException;
import android.net.Uri;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.UUID;

/**
 * Tests to check SDK sandbox process restrictions.
 */
@RunWith(JUnit4.class)
public class SdkSandboxRestrictionsTest {

    /**
     * Test that sdk sandbox doesn't crash on checking the uri permission.
     */
    @Test
    public void testCheckUriPermission() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        Uri uri = Uri.parse("content://com.example.sdk.provider/abc");
        int ret = context.checkCallingOrSelfUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        assertThat(ret).isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    /**
     * Tests that sandbox cannot access the Widevine ID.
     */
    @Test
    public void testNoWidevineAccess() throws Exception {
        UUID widevineUuid = new UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);

        UnsupportedSchemeException thrown = assertThrows(
                UnsupportedSchemeException.class,
                () -> new MediaDrm(widevineUuid));
        assertThat(thrown).hasMessageThat().contains("NO_INIT");
    }

    /**
     * Tests that the SDK sandbox cannot broadcast to PermissionController to request permissions.
     */
    @Test
    public void testCannotRequestPermissions() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        Intent intent = new Intent(PackageManager.ACTION_REQUEST_PERMISSIONS);
        intent.putExtra(PackageManager.EXTRA_REQUEST_PERMISSIONS_NAMES,
                new String[] {Manifest.permission.INSTALL_PACKAGES});
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        String packageName;
        try {
            packageName = context.getPackageManager().getPermissionControllerPackageName();
        } catch (Exception e) {
            packageName = "test.package";
        }
        intent.setPackage(packageName);

        SecurityException thrown = assertThrows(
                SecurityException.class,
                () -> context.startActivity(intent));
        assertThat(thrown).hasMessageThat().contains(
                "may not be broadcast from an SDK sandbox uid");
    }

    /**
     * Tests that sandbox cannot send implicit broadcast intents.
     */
    @Test
    public void testNoImplicitIntents() {
        final Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();

        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, "text");
        sendIntent.setType("text/plain");
        sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        SecurityException thrown = assertThrows(
                SecurityException.class,
                () -> ctx.startActivity(sendIntent));
        assertThat(thrown).hasMessageThat().contains("may not be broadcast from an SDK sandbox");
    }

    /**
     * Tests that sandbox can open URLs in a browser.
     */
    @Test
    public void testUrlViewIntents() {
        final Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();

        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("https://www.android.com"));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        ctx.startActivity(intent);
    }
}
