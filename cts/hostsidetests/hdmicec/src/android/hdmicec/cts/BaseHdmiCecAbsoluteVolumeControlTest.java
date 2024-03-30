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

package android.hdmicec.cts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * Abstract base class for tests for Absolute Volume Control (AVC). Subclasses must call
 * this class's constructor to specify the device type of the DUT and the System Audio device.
 * The three valid pairs of (DUT type, System Audio device type) for AVC are as follows:
 * (Playback, TV); (Playback, Audio System); (TV, Audio System).
 *
 * Currently, it is only feasible to test the case where the DUT is a playback device and the
 * System Audio device is a TV. This is because the CEC adapter responds <Feature Abort> to
 * <Set Audio Volume Level> when it is started as an Audio System.
 */
public abstract class BaseHdmiCecAbsoluteVolumeControlTest extends BaseHdmiCecCtsTest {

    /**
     * Constructor. The test device type and client params (determining the client's device type)
     * passed in here determine the behavior of the tests.
     */
    public BaseHdmiCecAbsoluteVolumeControlTest(@HdmiCecConstants.CecDeviceType int testDeviceType,
            String... clientParams) {
        super(testDeviceType, clientParams);
    }

    /**
     * Returns the audio output device being used.
     */
    public int getAudioOutputDevice() {
        if (mTestDeviceType == HdmiCecConstants.CEC_DEVICE_TYPE_TV) {
            return HdmiCecConstants.DEVICE_OUT_HDMI_ARC;
        } else {
            return HdmiCecConstants.DEVICE_OUT_HDMI;
        }
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        // This setting must be enabled to use AVC. Start with it disabled to ensure that we can
        // control when the AVC initiation process starts.
        setSettingsValue(HdmiCecConstants.SETTING_VOLUME_CONTROL_ENABLED,
                HdmiCecConstants.VOLUME_CONTROL_DISABLED);

        // Disable and enable CEC on the DUT to clear its knowledge of device feature support.
        // If the DUT isn't a TV, simulate a connected sink as well.
        if (mTestDeviceType == HdmiCecConstants.CEC_DEVICE_TYPE_TV) {
            getDevice().executeShellCommand("cmd hdmi_control cec_setting set hdmi_cec_enabled 0");
            waitForCondition(() -> !isCecAvailable(getDevice()), "Could not disable CEC");
            getDevice().executeShellCommand("cmd hdmi_control cec_setting set hdmi_cec_enabled 1");
            waitForCondition(() -> isCecAvailable(getDevice()), "Could not enable CEC");
        } else {
            simulateCecSinkConnected(getDevice(), getTargetLogicalAddress());
        }

        // Full volume behavior is a prerequisite for AVC. However, we cannot control this
        // condition from CTS tests or shell due to missing permissions. Therefore, we run these
        // tests only if it is already true.
        assumeTrue(isFullVolumeDevice(getAudioOutputDevice()));
    }

    /**
     * Requires the device to be able to adopt CEC 2.0 so that it sends <Give Features>.
     *
     * Tests that the DUT enables and disables AVC in response to changes in the System Audio
     * device's support for <Set Audio Volume Level>. In this test, this support status is
     * communicated through <Report Features> messages.
     */
    @Test
    public void testEnableDisableAvc_cec20_triggeredByReportFeatures() throws Exception {
        // Enable CEC 2.0
        setCec20();

        // Enable CEC volume
        setSettingsValue(HdmiCecConstants.SETTING_VOLUME_CONTROL_ENABLED,
                HdmiCecConstants.VOLUME_CONTROL_ENABLED);

        // Enable System Audio Mode if the System Audio device is an Audio System
        enableSystemAudioModeIfApplicable();

        // Since CEC 2.0 is enabled, DUT should also use <Give Features> to query AVC support
        hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.GIVE_FEATURES);
        hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.SET_AUDIO_VOLUME_LEVEL);

        // System Audio device reports support for <Set Audio Volume Level> via <Report Features>
        sendReportFeatures(true);

        // DUT queries audio status
        hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.GIVE_AUDIO_STATUS);
        checkAbsoluteVolumeControlStatus(false);

        // DUT receives audio status
        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.REPORT_AUDIO_STATUS,
                CecMessage.formatParams(50));
        checkAbsoluteVolumeControlStatus(true);

        // System Audio device reports no support for <Set Audio Volume Level>
        sendReportFeatures(false);
        checkAbsoluteVolumeControlStatus(false);
    }

    /**
     * Tests that the DUT enables and disables AVC in response to changes in the System Audio
     * device's support for <Set Audio Volume Level>. In this test, this support status is
     * communicated through (the lack of) <Feature Abort> responses to <Set Audio Volume Level>.
     */
    @Test
    public void testEnableDisableAvc_triggeredByAvcSupportChanged() throws Exception {
        setSettingsValue(HdmiCecConstants.SETTING_VOLUME_CONTROL_ENABLED,
                HdmiCecConstants.VOLUME_CONTROL_ENABLED);

        enableSystemAudioModeIfApplicable();

        // DUT queries AVC support by sending <Set Audio Volume Level>
        hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.SET_AUDIO_VOLUME_LEVEL);

        // System Audio device does not respond with <Feature Abort>. DUT queries audio status
        hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.GIVE_AUDIO_STATUS);
        checkAbsoluteVolumeControlStatus(false);

        // DUT receives audio status
        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.REPORT_AUDIO_STATUS,
                CecMessage.formatParams(50));
        checkAbsoluteVolumeControlStatus(true);

        // System Audio device responds to <Set Audio Volume Level> with
        // <Feature Abort>[Unrecognized opcode]
        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.FEATURE_ABORT,
                CecMessage.formatParams(CecOperand.SET_AUDIO_VOLUME_LEVEL + "00"));
        checkAbsoluteVolumeControlStatus(false);
    }

    /**
     * Tests that the DUT enables and disables AVC in response to CEC volume control being
     * enabled or disabled.
     */
    @Test
    public void testEnableAndDisableAVC_triggeredByVolumeControlSettingChange() throws Exception {
        enableSystemAudioModeIfApplicable();

        // System audio device reports support for <Set Audio Volume Level>
        sendReportFeatures(true);

        // Enable CEC volume
        setSettingsValue(HdmiCecConstants.SETTING_VOLUME_CONTROL_ENABLED,
                HdmiCecConstants.VOLUME_CONTROL_ENABLED);

        // DUT queries audio status
        hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.GIVE_AUDIO_STATUS);
        checkAbsoluteVolumeControlStatus(false);

        // DUT receives audio status
        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.REPORT_AUDIO_STATUS,
                CecMessage.formatParams(50));
        checkAbsoluteVolumeControlStatus(true);

        // CEC volume control is disabled on the DUT
        setSettingsValue(HdmiCecConstants.SETTING_VOLUME_CONTROL_ENABLED,
                HdmiCecConstants.VOLUME_CONTROL_DISABLED);
        checkAbsoluteVolumeControlStatus(false);
    }

    /**
     * Tests that the DUT enables and disables AVC in response to System Audio mode being
     * enabled or disabled.
     *
     * Only valid when the System Audio device is an Audio System.
     */
    @Test
    public void testEnableDisableAvc_triggeredBySystemAudioModeChange() throws Exception {
        assumeTrue("Skipping this test for this setup because the System Audio device "
                        + "is not an Audio System.",
                hdmiCecClient.getSelfDevice().getDeviceType()
                        == HdmiCecConstants.CEC_DEVICE_TYPE_AUDIO_SYSTEM);

        // System audio device reports support for <Set Audio Volume Level>
        sendReportFeatures(true);

        // CEC volume control is enabled on the DUT
        setSettingsValue(HdmiCecConstants.SETTING_VOLUME_CONTROL_ENABLED,
                HdmiCecConstants.VOLUME_CONTROL_ENABLED);

        // Enable System Audio Mode
        broadcastSystemAudioModeMessage(true);

        // DUT queries audio status
        hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.GIVE_AUDIO_STATUS);
        checkAbsoluteVolumeControlStatus(false);

        // DUT receives audio status
        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.REPORT_AUDIO_STATUS,
                CecMessage.formatParams(50));
        checkAbsoluteVolumeControlStatus(true);

        // System Audio Mode is disabled
        broadcastSystemAudioModeMessage(false);
        checkAbsoluteVolumeControlStatus(false);
    }

    /**
     * Tests that the DUT sends the correct CEC messages when AVC is enabled and Android
     * initiates volume changes.
     */
    @Test
    public void testOutgoingVolumeUpdates() throws Exception {
        // Enable AVC
        setSettingsValue(HdmiCecConstants.SETTING_VOLUME_CONTROL_ENABLED,
                HdmiCecConstants.VOLUME_CONTROL_ENABLED);
        enableSystemAudioModeIfApplicable();
        sendReportFeatures(true);
        hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.GIVE_AUDIO_STATUS);
        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.REPORT_AUDIO_STATUS,
                CecMessage.formatParams(50));
        checkAbsoluteVolumeControlStatus(true);

        // Calling AudioManager#setStreamVolume should cause the DUT to send
        // <Set Audio Volume Level> with the new volume level as a parameter
        AudioManagerHelper.setDeviceVolume(getDevice(), 80);
        String setAudioVolumeLevelMessage = hdmiCecClient.checkExpectedOutput(
                hdmiCecClient.getSelfDevice(), CecOperand.SET_AUDIO_VOLUME_LEVEL);
        assertThat(CecMessage.getParams(setAudioVolumeLevelMessage)).isEqualTo(80);

        // Calling AudioManager#adjustStreamVolume should cause the DUT to send
        // <User Control Pressed>, <User Control Released>, and <Give Audio Status>
        AudioManagerHelper.raiseVolume(getDevice());
        String userControlPressedMessage = hdmiCecClient.checkExpectedOutput(
                hdmiCecClient.getSelfDevice(), CecOperand.USER_CONTROL_PRESSED);
        assertThat(CecMessage.getParams(userControlPressedMessage))
                .isEqualTo(HdmiCecConstants.CEC_KEYCODE_VOLUME_UP);
        hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.USER_CONTROL_RELEASED);
        hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.GIVE_AUDIO_STATUS);
    }

    /**
     * Tests that the DUT notifies AudioManager when it receives <Report Audio Status> from the
     * System Audio device.
     */
    @Test
    public void testIncomingVolumeUpdates() throws Exception {
        // Enable AVC
        setSettingsValue(HdmiCecConstants.SETTING_VOLUME_CONTROL_ENABLED,
                HdmiCecConstants.VOLUME_CONTROL_ENABLED);
        enableSystemAudioModeIfApplicable();
        sendReportFeatures(true);
        hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.GIVE_AUDIO_STATUS);
        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.REPORT_AUDIO_STATUS,
                CecMessage.formatParams(50)); // Volume 50, mute off
        checkAbsoluteVolumeControlStatus(true);

        // Volume and mute status should match the initial <Report Audio Status>
        assertApproximateDeviceVolumeAndMute(50, false);

        // Test an incoming <Report Audio Status> that does not mute the device
        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.REPORT_AUDIO_STATUS,
                CecMessage.formatParams(90)); // Volume 90, mute off
        assertApproximateDeviceVolumeAndMute(90, false);

        // Test an incoming <Report Audio Status> that mutes the device
        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.REPORT_AUDIO_STATUS,
                CecMessage.formatParams(70 + 0b1000_0000)); // Volume 70, mute on
        assertApproximateDeviceVolumeAndMute(0, true);
    }

    /**
     * Enables System Audio Mode if the System Audio device is an Audio System.
     */
    private void enableSystemAudioModeIfApplicable() throws Exception {
        if (hdmiCecClient.getSelfDevice() == LogicalAddress.AUDIO_SYSTEM) {
            broadcastSystemAudioModeMessage(true);
        }
    }

    /**
     * Has the CEC client broadcast a message enabling or disabling System Audio Mode.
     */
    private void broadcastSystemAudioModeMessage(boolean val) throws Exception {
        hdmiCecClient.sendCecMessage(
                hdmiCecClient.getSelfDevice(),
                LogicalAddress.BROADCAST,
                CecOperand.SET_SYSTEM_AUDIO_MODE,
                CecMessage.formatParams(val ? 1 : 0));
    }

    /**
     * Has the CEC client send a <Report Features> message expressing support or lack of support for
     * <Set Audio Volume Level>.
     */
    private void sendReportFeatures(boolean setAudioVolumeLevelSupport) throws Exception {
        String deviceTypeNibble = hdmiCecClient.getSelfDevice() == LogicalAddress.TV
                ? "80" : "08";
        String featureSupportNibble = setAudioVolumeLevelSupport ? "01" : "00";
        hdmiCecClient.sendCecMessage(
                hdmiCecClient.getSelfDevice(),
                LogicalAddress.BROADCAST,
                CecOperand.REPORT_FEATURES,
                CecMessage.formatParams("06" + deviceTypeNibble + "00" + featureSupportNibble));
    }

    /**
     * Checks that the status of Absolute Volume Control on the DUT to match the expected status.
     * Waits and rechecks a limited number of times if the status does not currently match.
     */
    private void checkAbsoluteVolumeControlStatus(boolean enabledStatus) throws Exception {
        String expectedStatus = enabledStatus ? "enabled" : "disabled";
        waitForCondition(() -> getAbsoluteVolumeControlStatus() == enabledStatus,
                "Absolute Volume Control was not " + expectedStatus + " when expected");
    }

    /**
     * Returns the state of Absolute Volume Control on the DUT. This is determined by the
     * volume behavior of the audio output device being used for HDMI audio.
     */
    private boolean getAbsoluteVolumeControlStatus() throws Exception {
        return getDevice()
                .executeShellCommand("dumpsys hdmi_control | grep mIsAbsoluteVolumeControlEnabled:")
                .replace("mIsAbsoluteVolumeControlEnabled:", "").trim()
                .equals("true");
    }

    /**
     * Asserts that the DUT's volume (scale: [0, 100]) is within 5 points of an expected volume.
     * This accounts for small differences due to rounding when converting between volume scales.
     * Also asserts that the DUT's mute status is equal to {@code expectedMute}.
     *
     * Asserting both volume and mute at the same time saves a shell command because both are
     * conveyed in a single log message.
     */
    private void assertApproximateDeviceVolumeAndMute(int expectedVolume, boolean expectedMute)
            throws Exception {
        // Raw output is equal to volume out of 100, plus 128 if muted
        // In practice, if the stream is muted, volume equals 0, so this will be at most 128
        int rawOutput = AudioManagerHelper.getDutAudioVolume(getDevice());

        int actualVolume = rawOutput % 128;
        assertWithMessage("Expected DUT to have volume " + expectedVolume
                + " but was actually " + actualVolume)
                .that(Math.abs(expectedVolume - actualVolume) <= 5)
                .isTrue();

        boolean actualMute = rawOutput >= 128;
        String expectedMuteString = expectedMute ? "muted" : "unmuted";
        String actualMuteString = actualMute ? "muted" : "unmuted";
        assertWithMessage("Expected DUT to be " + expectedMuteString
                + "but was actually " + actualMuteString)
                .that(expectedMute)
                .isEqualTo(actualMute);
    }
}
