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

package com.android.cts.verifier.audio;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.media.AudioDescriptor;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.TextView;

import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.CtsVerifierReportLog;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * AudioDescriptorActivity is used to test if the reported AudioDescriptor is valid and necessary.
 * AudioDescriptor is introduced for the HAL to report the device capabilities that have formats
 * unknown to Android. But it is also mandatory to report device capabilities using Android defined
 * enums as long as it is possible so that the developers won't need to parse AudioDescriptor.
 */
public class AudioDescriptorActivity extends PassFailButtons.Activity {
    private static final String TAG = "AudioDescriptorActivity";

    // ReportLog Schema
    private static final String KEY_CLAIMS_HDMI = "claims_hdmi";
    private static final String KEY_HAL_VERSION = "audio_hal_version";
    private static final String KEY_AUDIO_DESCRIPTOR = "audio_descriptor";

    // Audio HAL type
    private static final int HAL_TYPE_UNKNOWN = 0;
    private static final int HAL_TYPE_HIDL = 1;

    private static final int EXTENSION_FORMAT_CODE = 15;

    // Description of not used format extended codes can be found at
    // https://en.wikipedia.org/wiki/Extended_Display_Identification_Data.
    private static final Set<Integer> NOT_USED_FORMAT_EXTENDED_CODES = new HashSet<Integer>() {{
            add(1);
            add(2);
            add(3);
        }};

    // Description of short audio descriptor can be found at
    // https://en.wikipedia.org/wiki/Extended_Display_Identification_Data.
    // The collection is sorted decreasingly by HAL version.
    private static final SortedMap<HalVersion, HalFormats> ALL_HAL_FORMATS =
            new TreeMap<>(Collections.reverseOrder()) {{
            // Formats defined by audio HAL v7.0 can be found at
            // hardware/interfaces/audio/7.0/config/audio_policy_configuration.xsd
            put(new HalVersion(HAL_TYPE_HIDL, 7 /*majorVersion*/, 0 /*minorVersion*/),
                    new HalFormats(new HashMap<Integer, String>() {{
                            put(2, "AC-3");
                            put(4, "MP3");
                            put(6, "AAC-LC");
                            put(11, "DTS-HD");
                            put(12, "Dolby TrueHD");
                        }},
                    new HashMap<Integer, String>() {{
                            put(7, "DRA");
                            // put(11, "MPEG-H"); MPEG-H is defined by Android but its capability
                            // can only be reported by short audio descriptor.
                            put(12, "AC-4");
                        }}));
        }};

    private AudioManager mAudioManager;

    private boolean mClaimsHDMI;
    private AudioDeviceInfo mHDMIDeviceInfo;

    private CheckBox mClaimsHDMICheckBox;
    private TextView mHDMISupportLbl;

    private OnBtnClickListener mClickListener = new OnBtnClickListener();

    TextView mTestStatusLbl;

    boolean mIsValidHal;
    String mHalVersionStr;
    String mInvalidHalErrorMsg;
    HalFormats mHalFormats;

    AudioDescriptor mLastTestedAudioDescriptor;

    @Override
    protected void onCreate(Bundle savedInstceState) {
        super.onCreate(savedInstceState);
        setContentView(R.layout.audio_descriptor);

        mAudioManager = getSystemService(AudioManager.class);
        mAudioManager.registerAudioDeviceCallback(new TestAudioDeviceCallback(), null);

        mClaimsHDMICheckBox = (CheckBox) findViewById(R.id.audioDescriptorHasHDMICheckBox);
        mClaimsHDMICheckBox.setOnClickListener(mClickListener);
        mHDMISupportLbl = (TextView) findViewById(R.id.audioDescriptorHDMISupportLbl);
        mTestStatusLbl = (TextView) findViewById(R.id.audioDescriptorTestStatusLbl);

        setInfoResources(R.string.audio_descriptor_test, R.string.audio_descriptor_test_info, -1);
        setPassFailButtonClickListeners();
        detectHalVersion();
        displayTestResult();
    }

    @Override
    public void recordTestResults() {
        CtsVerifierReportLog reportLog = getReportLog();

        reportLog.addValue(
                KEY_CLAIMS_HDMI,
                mClaimsHDMI,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
        reportLog.addValue(
                KEY_HAL_VERSION,
                mHalVersionStr,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
        Log.i("flamme", "halVersion:" + mHalVersionStr);
        reportLog.addValue(
                KEY_AUDIO_DESCRIPTOR,
                mLastTestedAudioDescriptor == null ? "" : mLastTestedAudioDescriptor.toString(),
                ResultType.NEUTRAL,
                ResultUnit.NONE);
        Log.i("flamme", "desc:" + mLastTestedAudioDescriptor);

        reportLog.submit();
    }

    private void detectHalVersion() {
        try {
            mHalVersionStr = mAudioManager.getHalVersion();
            HalVersion halVersion = new HalVersion(mHalVersionStr);
            if (!halVersion.isValid()) {
                mIsValidHal = false;
                mInvalidHalErrorMsg = getResources().getString(
                        R.string.audio_descriptor_invalid_hal_version, mHalVersionStr);
                return;
            }
            mHalFormats = getHalFormats(halVersion);
            mIsValidHal = true;
            mInvalidHalErrorMsg = "";
        } catch (Exception e) {
            mIsValidHal = false;
            mInvalidHalErrorMsg = getResources().getString(
                    R.string.audio_descriptor_cannot_get_hal_version);
        }
    }

    private void detectHDMIDevice() {
        mHDMIDeviceInfo = null;
        AudioDeviceInfo[] deviceInfos = mAudioManager.getDevices(AudioManager.GET_DEVICES_ALL);
        for (AudioDeviceInfo deviceInfo : deviceInfos) {
            Log.i(TAG, "  " + deviceInfo.getProductName() + " type:" + deviceInfo.getType());
            if (deviceInfo.isSink() && deviceInfo.getType() == AudioDeviceInfo.TYPE_HDMI) {
                mHDMIDeviceInfo = deviceInfo;
                break;
            }
        }

        if (mHDMIDeviceInfo != null) {
            mClaimsHDMICheckBox.setChecked(true);
        }
    }

    protected void onDeviceConnectionChanged() {
        detectHDMIDevice();
        displayTestResult();
    }

    private class OnBtnClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            int id = v.getId();
            if (id == R.id.audioDescriptorHasHDMICheckBox) {
                Log.i(TAG, "HDMI check box is clicked");
                if (mClaimsHDMICheckBox.isChecked()) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(
                            v.getContext(), android.R.style.Theme_Material_Dialog_Alert);
                    builder.setTitle(R.string.audio_descriptor_hdmi_info_title);
                    builder.setMessage(R.string.audio_descriptor_hdmi_message);
                    builder.setPositiveButton(android.R.string.yes,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            });
                    builder.setIcon(android.R.drawable.ic_dialog_alert);
                    builder.show();

                    mClaimsHDMI = true;
                } else {
                    mClaimsHDMI = false;
                    mHDMISupportLbl.setText(R.string.audio_proaudio_NA);
                }
                detectHDMIDevice();
                displayTestResult();
            }
        }
    }

    private void displayTestResult() {
        if (mClaimsHDMI && mHDMIDeviceInfo == null) {
            mHDMISupportLbl.setText(R.string.audio_descriptor_hdmi_pending);
            getPassButton().setEnabled(false);
            mTestStatusLbl.setText("");
            return;
        }
        if (!mIsValidHal) {
            getPassButton().setEnabled(false);
            mTestStatusLbl.setText(mInvalidHalErrorMsg);
            return;
        }
        Pair<Boolean, String> testResult = testAudioDescriptors();
        getPassButton().setEnabled(testResult.first);
        mTestStatusLbl.setText(testResult.second);
    }

    private Pair<Boolean, String> testAudioDescriptors() {
        AudioDeviceInfo[] devices = mAudioManager.getDevices(AudioManager.GET_DEVICES_ALL);
        for (AudioDeviceInfo device : devices) {
            for (AudioDescriptor descriptor : device.getAudioDescriptors()) {
                mLastTestedAudioDescriptor = descriptor;
                Pair<Boolean, String> ret = isAudioDescriptorValid(descriptor);
                if (!ret.first) {
                    return ret;
                }
            }
        }
        return new Pair<>(true, getResources().getString(R.string.audio_descriptor_pass));
    }

    private Pair<Boolean, String> isAudioDescriptorValid(AudioDescriptor descriptor) {
        if (descriptor.getStandard() == AudioDescriptor.STANDARD_NONE) {
            return new Pair<>(
                    false, getResources().getString(R.string.audio_descriptor_standard_none));
        }
        if (descriptor.getDescriptor() == null) {
            return new Pair<>(
                    false, getResources().getString(R.string.audio_descriptor_is_null));
        }
        switch (descriptor.getStandard()) {
            case AudioDescriptor.STANDARD_EDID:
                return verifyShortAudioDescriptor(descriptor.getDescriptor());
            default:
                return new Pair<>(false, getResources().getString(
                        R.string.audio_descriptor_unrecognized_standard, descriptor.getStandard()));
        }
    }

    /**
     * Verify if short audio descriptor is valid and necessary. The length of short audio descriptor
     * must be 3. Short audio descriptor is only needed when it can not be reported by Android
     * defined enums.
     *
     * @param sad a byte array of short audio descriptor
     * @return a pair where first object indicates if the short audio descriptor is valid and
     *         necessary and the second object is the error message.
     */
    private Pair<Boolean, String> verifyShortAudioDescriptor(byte[] sad) {
        if (sad.length != 3) {
            return new Pair<>(false, getResources().getString(
                    R.string.audio_descriptor_length_error, sad.length));
        }

        if (!mIsValidHal) {
            return new Pair<>(false, mInvalidHalErrorMsg);
        }

        if (mHalFormats == null) {
            Log.i(TAG, "No HAL formats found for v" + mHalVersionStr);
            return new Pair<>(true, getResources().getString(R.string.audio_descriptor_pass));
        }

        // Parse according CTA-861-G, section 7.5.2.
        final int formatCode = (sad[0] >> 3) & 0xf;
        if (mHalFormats.getFormatCodes().containsKey(formatCode)) {
            return new Pair<>(false, getResources().getString(
                    R.string.audio_descriptor_format_code_should_not_be_reported,
                    formatCode,
                    mHalFormats.getFormatCodes().get(formatCode)));
        } else if (formatCode == EXTENSION_FORMAT_CODE) {
            final int formatExtendedCode = sad[2] >> 3;
            if (mHalFormats.getExtendedFormatCodes().containsKey(formatExtendedCode)) {
                return new Pair<>(false, getResources().getString(
                        R.string.audio_descriptor_format_extended_code_should_not_be_reported,
                        formatExtendedCode,
                        mHalFormats.getExtendedFormatCodes().get(formatExtendedCode)));
            } else if (NOT_USED_FORMAT_EXTENDED_CODES.contains(formatExtendedCode)) {
                return new Pair<>(false, getResources().getString(
                        R.string.audio_descriptor_format_extended_code_is_not_used,
                        formatExtendedCode));
            }
        }
        return new Pair<>(true, getResources().getString(R.string.audio_descriptor_pass));
    }

    static class HalVersion implements Comparable<HalVersion> {
        private final int mHalType;
        private final int mMajorVersion;
        private final int mMinorVersion;

        HalVersion(int halType, int majorVersion, int minorVersion) {
            mHalType = halType;
            mMajorVersion = majorVersion;
            mMinorVersion = minorVersion;
        }

        HalVersion(String halVersion) {
            int halType = HAL_TYPE_UNKNOWN;
            int majorVersion = -1;
            int minorVersion = -1;
            if (halVersion != null) {
                String[] versions = halVersion.split("\\.");
                if (versions.length == 2) {
                    try {
                        majorVersion = Integer.parseInt(versions[0]);
                        minorVersion = Integer.parseInt(versions[1]);
                        halType = HAL_TYPE_HIDL;
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Unable to parse hal version: " + halVersion);
                    }
                }
            }
            mHalType = halType;
            mMajorVersion = majorVersion;
            mMinorVersion = minorVersion;
        }

        boolean isValid() {
            return mHalType != HAL_TYPE_UNKNOWN;
        }

        /**
         * Compare two HAL versions.
         * @return 0 if the HAL version is the same as the other HAL version. Positive if the HAL
         *         version is newer than the other HAL version. Negative if the HAL version is
         *         older than the other version.
         */
        @Override
        public int compareTo(HalVersion that) {
            // Note that currently only HIDL hal here. New HAL type may be added in the future.
            if (mMajorVersion > that.mMajorVersion) {
                return 1;
            } else if (mMajorVersion < that.mMajorVersion) {
                return -1;
            }
            // Major version is the same one. Compare minor version.
            return mMinorVersion > that.mMinorVersion ? 1
                    : mMinorVersion < that.mMinorVersion ? -1 : 0;
        }

        @Override
        public boolean equals(Object that) {
            if (that == null) return false;
            if (that == this) return true;
            if (!(that instanceof HalVersion)) return false;
            return this.compareTo((HalVersion) that) == 0;
        }
    }

    private HalFormats getHalFormats(HalVersion halVersion) {
        for (Map.Entry<HalVersion, HalFormats> entry : ALL_HAL_FORMATS.entrySet()) {
            if (halVersion.compareTo(entry.getKey()) >= 0) {
                return entry.getValue();
            }
        }
        return null;
    }

    static class HalFormats {
        private final Map<Integer, String> mFormatCodes;
        private final Map<Integer, String> mExtendedFormatCodes;

        HalFormats(Map<Integer, String> formatCodes,
                   Map<Integer, String> extendedFormatCodes) {
            mFormatCodes = formatCodes;
            mExtendedFormatCodes = extendedFormatCodes;
        }

        Map<Integer, String> getFormatCodes() {
            return mFormatCodes;
        }

        Map<Integer, String> getExtendedFormatCodes() {
            return mExtendedFormatCodes;
        }
    }

    private class TestAudioDeviceCallback extends AudioDeviceCallback {
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            onDeviceConnectionChanged();
        }

        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            onDeviceConnectionChanged();
        }
    }
}
