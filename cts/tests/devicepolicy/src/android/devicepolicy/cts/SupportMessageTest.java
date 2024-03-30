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

package android.devicepolicy.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.admin.RemoteDevicePolicyManager;
import android.content.ComponentName;
import android.stats.devicepolicy.EventId;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.SupportMessage;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.remotedpc.RemotePolicyManager;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class SupportMessageTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final int SHORT_SUPPORT_MESSAGE_MAX_LENGTH = 200;
    private static final int LONG_SUPPORT_MESSAGE_REASONABLE_LENGTH = 4000;
    private static final String VALID_SUPPORT_MESSAGE = "My valid support message.";
    private static final String SHORT_SUPPORT_MESSAGE_TOO_LONG =
            new String(new char[SHORT_SUPPORT_MESSAGE_MAX_LENGTH + 1])
                    .replace('\0', 'A');
    private static final String SHORT_SUPPORT_MESSAGE_TOO_LONG_TRUNCATED =
            SHORT_SUPPORT_MESSAGE_TOO_LONG
                    .subSequence(0, SHORT_SUPPORT_MESSAGE_MAX_LENGTH + 1)
                    .toString();
    private static final String LONG_SUPPORT_MESSAGE_REASONABLY_LONG =
            new String(new char[LONG_SUPPORT_MESSAGE_REASONABLE_LENGTH])
                    .replace('\0', 'A');
    private static final String EMPTY_SUPPORT_MESSAGE = "";

    private RemoteDevicePolicyManager mDevicePolicyManager;
    private ComponentName mAdmin;

    @Before
    public void setUp() {
        RemotePolicyManager dpc = sDeviceState.dpc();
        mAdmin = dpc.componentName();
        mDevicePolicyManager = dpc.devicePolicyManager();
    }

    @After
    public void tearDown() {
        mDevicePolicyManager.setShortSupportMessage(mAdmin, /* charSequence= */ null);
        mDevicePolicyManager.setLongSupportMessage(mAdmin, /* charSequence= */ null);
    }

    @PolicyAppliesTest(policy = SupportMessage.class)
    @Postsubmit(reason = "new test")
    public void setShortSupportMessage_validText_works() {
        mDevicePolicyManager.setShortSupportMessage(mAdmin, VALID_SUPPORT_MESSAGE);

        assertThat(mDevicePolicyManager.getShortSupportMessage(mAdmin).toString())
                .isEqualTo(VALID_SUPPORT_MESSAGE);
    }

    @PolicyAppliesTest(policy = SupportMessage.class)
    @Postsubmit(reason = "new test")
    public void setLongSupportMessage_validText_works() {
        mDevicePolicyManager.setLongSupportMessage(mAdmin, VALID_SUPPORT_MESSAGE);

        assertThat(mDevicePolicyManager.getLongSupportMessage(mAdmin).toString())
                .isEqualTo(VALID_SUPPORT_MESSAGE);
    }

    @PolicyAppliesTest(policy = SupportMessage.class)
    @Postsubmit(reason = "new test")
    public void setShortSupportMessage_emptyText_works() {
        mDevicePolicyManager.setShortSupportMessage(mAdmin, EMPTY_SUPPORT_MESSAGE);

        assertThat(mDevicePolicyManager.getShortSupportMessage(mAdmin).toString())
                .isEqualTo(EMPTY_SUPPORT_MESSAGE);
    }

    @PolicyAppliesTest(policy = SupportMessage.class)
    @Postsubmit(reason = "new test")
    public void setLongSupportMessage_nullText_clearsOldText() {
        mDevicePolicyManager.setLongSupportMessage(mAdmin, VALID_SUPPORT_MESSAGE);
        mDevicePolicyManager.setLongSupportMessage(mAdmin, /* charSequence= */ null);

        assertThat(mDevicePolicyManager.getLongSupportMessage(mAdmin).toString()).isEqualTo("null");
    }

    @PolicyAppliesTest(policy = SupportMessage.class)
    @Postsubmit(reason = "new test")
    public void setShortSupportMessage_nullText_clearsOldText() {
        mDevicePolicyManager.setShortSupportMessage(mAdmin, VALID_SUPPORT_MESSAGE);
        mDevicePolicyManager.setShortSupportMessage(mAdmin, /* charSequence= */ null);

        assertThat(mDevicePolicyManager.getShortSupportMessage(mAdmin).toString())
                .isEqualTo("null");
    }

    @PolicyAppliesTest(policy = SupportMessage.class)
    @Postsubmit(reason = "new test")
    public void setLongSupportMessage_emptyText_works() {
        mDevicePolicyManager.setLongSupportMessage(mAdmin, EMPTY_SUPPORT_MESSAGE);

        assertThat(mDevicePolicyManager.getLongSupportMessage(mAdmin).toString())
                .isEqualTo(EMPTY_SUPPORT_MESSAGE);
    }

    @PolicyAppliesTest(policy = SupportMessage.class)
    @Postsubmit(reason = "new test")
    public void setShortSupportMessage_tooLongText_isTruncated() {
        mDevicePolicyManager.setShortSupportMessage(mAdmin, SHORT_SUPPORT_MESSAGE_TOO_LONG);

        assertThat(mDevicePolicyManager.getShortSupportMessage(mAdmin).toString())
                .isEqualTo(SHORT_SUPPORT_MESSAGE_TOO_LONG_TRUNCATED);
    }

    @PolicyAppliesTest(policy = SupportMessage.class)
    @Postsubmit(reason = "new test")
    public void setLongSupportMessage_longText_notTruncated() {
        mDevicePolicyManager.setShortSupportMessage(mAdmin, LONG_SUPPORT_MESSAGE_REASONABLY_LONG);

        assertThat(mDevicePolicyManager.getShortSupportMessage(mAdmin).toString())
                .isEqualTo(LONG_SUPPORT_MESSAGE_REASONABLY_LONG);
    }

    @PolicyAppliesTest(policy = SupportMessage.class)
    @Postsubmit(reason = "new test")
    public void setShortSupportMessage_nullAdmin_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                mDevicePolicyManager.setShortSupportMessage(
                        /* componentName= */ null, VALID_SUPPORT_MESSAGE));
    }

    @PolicyAppliesTest(policy = SupportMessage.class)
    @Postsubmit(reason = "new test")
    public void setLongSupportMessage_nullAdmin_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                mDevicePolicyManager.setLongSupportMessage(
                        /* componentName= */ null, VALID_SUPPORT_MESSAGE));
    }

    @PolicyAppliesTest(policy = SupportMessage.class)
    @Postsubmit(reason = "new test")
    public void getShortSupportMessage_nullAdmin_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                mDevicePolicyManager.getShortSupportMessage(
                        /* componentName= */ null));
    }

    @PolicyAppliesTest(policy = SupportMessage.class)
    @Postsubmit(reason = "new test")
    public void getLongSupportMessage_nullAdmin_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                mDevicePolicyManager.getLongSupportMessage(
                        /* componentName= */ null));
    }

    // We don't include non device admin states as passing a null admin is a NullPointerException
    @CannotSetPolicyTest(policy = SupportMessage.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    public void getLongSupportMessage_invalidAdmin_fails() {
        assertThrows(SecurityException.class, () ->
                mDevicePolicyManager.getLongSupportMessage(mAdmin));
    }

    // We don't include non device admin states as passing a null admin is a NullPointerException
    @CannotSetPolicyTest(policy = SupportMessage.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    public void setLongSupportMessage_invalidAdmin_fails() {
        assertThrows(SecurityException.class, () ->
                mDevicePolicyManager.setLongSupportMessage(mAdmin, VALID_SUPPORT_MESSAGE));
    }

    // We don't include non device admin states as passing a null admin is a NullPointerException
    @CannotSetPolicyTest(policy = SupportMessage.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    public void getShortSupportMessage_invalidAdmin_fails() {
        assertThrows(SecurityException.class, () ->
                mDevicePolicyManager.getShortSupportMessage(mAdmin));
    }

    // We don't include non device admin states as passing a null admin is a NullPointerException
    @CannotSetPolicyTest(policy = SupportMessage.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    public void setShortSupportMessage_invalidAdmin_fails() {
        assertThrows(SecurityException.class, () ->
                mDevicePolicyManager.setShortSupportMessage(mAdmin, VALID_SUPPORT_MESSAGE));
    }

    @PolicyAppliesTest(policy = SupportMessage.class)
    @Postsubmit(reason = "new test")
    public void setShortSupportMessage_validText_logged() {
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            mDevicePolicyManager.setShortSupportMessage(mAdmin, VALID_SUPPORT_MESSAGE);

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_SHORT_SUPPORT_MESSAGE_VALUE)
                    .whereAdminPackageName().isEqualTo(mAdmin.getPackageName())
                    .poll())
                    .isNotNull();
        }
    }

    @PolicyAppliesTest(policy = SupportMessage.class)
    @Postsubmit(reason = "new test")
    public void setLongSupportMessage_validText_logged() {
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            mDevicePolicyManager.setLongSupportMessage(mAdmin, VALID_SUPPORT_MESSAGE);

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_LONG_SUPPORT_MESSAGE_VALUE)
                    .whereAdminPackageName().isEqualTo(mAdmin.getPackageName())
                    .poll())
                    .isNotNull();
        }
    }
}
