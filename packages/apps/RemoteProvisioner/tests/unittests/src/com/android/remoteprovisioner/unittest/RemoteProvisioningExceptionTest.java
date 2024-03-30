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

package com.android.remoteprovisioner.unittest;

import android.platform.test.annotations.Presubmit;
import android.security.IGenerateRkpKeyService.Status;

import androidx.test.runner.AndroidJUnit4;

import com.android.remoteprovisioner.RemoteProvisioningException;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RemoteProvisioningExceptionTest {
    @Presubmit
    @Test
    public void handlesArbitraryErrors() {
        for (int i : new int[]{1, 11, 123, 0x8675309}) {
            RemoteProvisioningException ex = new RemoteProvisioningException(i, "error: " + i);
            Assert.assertEquals(i, ex.getErrorCode());
            Assert.assertEquals("error: " + i, ex.getMessage());
        }
    }

    @Presubmit
    @Test
    public void handlesUnknownHttpStatus() {
        RemoteProvisioningException ex = RemoteProvisioningException.createFromHttpError(123);
        Assert.assertNotNull(ex);
        Assert.assertEquals(ex.getErrorCode(), Status.HTTP_UNKNOWN_ERROR);
    }

    @Presubmit
    @Test
    public void handlesServerErrors() {
        for (int httpStatus = 500; httpStatus < 600; ++httpStatus) {
            RemoteProvisioningException ex = RemoteProvisioningException.createFromHttpError(
                    httpStatus);
            Assert.assertNotNull(ex);
            Assert.assertEquals(httpStatus + " should have been a server error", ex.getErrorCode(),
                    Status.HTTP_SERVER_ERROR);
            Assert.assertTrue(ex.getMessage().contains("HTTP"));
        }
    }

    @Presubmit
    @Test
    public void handlesClientErrors() {
        for (int httpStatus = 400; httpStatus < 500; ++httpStatus) {
            RemoteProvisioningException ex = RemoteProvisioningException.createFromHttpError(
                    httpStatus);
            Assert.assertNotNull(ex);
            if (httpStatus == 444) {
                Assert.assertEquals(ex.getErrorCode(), Status.DEVICE_NOT_REGISTERED);
            } else {
                Assert.assertEquals(httpStatus + " should have been a client error",
                        ex.getErrorCode(), Status.HTTP_CLIENT_ERROR);
            }
            Assert.assertTrue(ex.getMessage().contains("HTTP"));
        }
    }
}
