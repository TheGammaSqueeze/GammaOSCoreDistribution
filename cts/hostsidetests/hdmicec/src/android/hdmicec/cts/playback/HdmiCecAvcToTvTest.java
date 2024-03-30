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

package android.hdmicec.cts.playback;

import android.hdmicec.cts.BaseHdmiCecAbsoluteVolumeControlTest;
import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.HdmiCecConstants;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

/**
 * Tests for Absolute Volume Control where the DUT is a Playback device and the
 * System Audio device is a TV.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class HdmiCecAvcToTvTest extends BaseHdmiCecAbsoluteVolumeControlTest {

    /**
     * No need to pass in client parameters because the client is started as TV as long as the
     * DUT is not a TV.
     */
    public HdmiCecAvcToTvTest() {
        super(HdmiCecConstants.CEC_DEVICE_TYPE_PLAYBACK_DEVICE);
    }

    @Rule
    public RuleChain ruleChain =
            RuleChain.outerRule(BaseHdmiCecCtsTest.CecRules.requiresCec(this))
                    .around(BaseHdmiCecCtsTest.CecRules.requiresLeanback(this))
                    .around(
                            BaseHdmiCecCtsTest.CecRules.requiresDeviceType(
                                    this, HdmiCecConstants.CEC_DEVICE_TYPE_PLAYBACK_DEVICE))
                    .around(hdmiCecClient);
}
