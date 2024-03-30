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

package android.virtualdevice.cts;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.platform.test.annotations.AppModeFull;
import android.virtualdevice.cts.util.EmptyActivity;
import android.virtualdevice.cts.util.TestAppHelper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "KeyguardManager cannot be accessed by instant apps")
public class DefaultDisplayIsDeviceSecureTest {

    @Test
    public void isDeviceSecure_checkReturnValuesOnDefaultDisplay() {
        Context context = getApplicationContext();
        KeyguardManager km = context.getSystemService(KeyguardManager.class);
        boolean isKeyguardSecure = km.isKeyguardSecure();

        EmptyActivity activity = (EmptyActivity) InstrumentationRegistry.getInstrumentation()
                .startActivitySync(
                        new Intent(context, EmptyActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                        | Intent.FLAG_ACTIVITY_CLEAR_TASK));

        EmptyActivity.Callback callback = mock(EmptyActivity.Callback.class);
        activity.setCallback(callback);

        int requestCode = 1;
        activity.startActivityForResult(
                TestAppHelper.createKeyguardManagerIsDeviceSecureTestIntent(),
                requestCode);

        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(callback, timeout(5000)).onActivityResult(
                eq(requestCode), eq(Activity.RESULT_OK), intentArgumentCaptor.capture());
        Intent resultData = intentArgumentCaptor.getValue();
        activity.finish();

        assertThat(resultData).isNotNull();
        boolean isDeviceSecure = resultData.getBooleanExtra(
                TestAppHelper.EXTRA_IS_DEVICE_SECURE, false);
        assertThat(isDeviceSecure).isEqualTo(isKeyguardSecure);
    }
}
