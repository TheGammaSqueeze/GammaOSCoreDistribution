/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.bluetooth.btservice;

import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.res.Resources;
import android.os.SystemProperties;
import android.util.Log;

import com.android.bluetooth.R;
import com.android.bluetooth.a2dp.A2dpService;
import com.android.bluetooth.a2dpsink.A2dpSinkService;
import com.android.bluetooth.avrcp.AvrcpTargetService;
import com.android.bluetooth.avrcpcontroller.AvrcpControllerService;
import com.android.bluetooth.bas.BatteryService;
import com.android.bluetooth.bass_client.BassClientService;
import com.android.bluetooth.csip.CsipSetCoordinatorService;
import com.android.bluetooth.gatt.GattService;
import com.android.bluetooth.hap.HapClientService;
import com.android.bluetooth.hearingaid.HearingAidService;
import com.android.bluetooth.hfp.HeadsetService;
import com.android.bluetooth.hfpclient.HeadsetClientService;
import com.android.bluetooth.hid.HidDeviceService;
import com.android.bluetooth.hid.HidHostService;
import com.android.bluetooth.le_audio.LeAudioService;
import com.android.bluetooth.map.BluetoothMapService;
import com.android.bluetooth.mapclient.MapClientService;
import com.android.bluetooth.mcp.McpService;
import com.android.bluetooth.opp.BluetoothOppService;
import com.android.bluetooth.pan.PanService;
import com.android.bluetooth.pbap.BluetoothPbapService;
import com.android.bluetooth.pbapclient.PbapClientService;
import com.android.bluetooth.sap.SapService;
import com.android.bluetooth.tbs.TbsService;
import com.android.bluetooth.vc.VolumeControlService;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;

public class Config {
    private static final String TAG = "AdapterServiceConfig";

    private static final String FEATURE_HEARING_AID = "settings_bluetooth_hearing_aid";
    private static final String FEATURE_BATTERY = "settings_bluetooth_battery";

    private static final String LE_AUDIO_DYNAMIC_SWITCH_PROPERTY =
            "ro.bluetooth.leaudio_switcher.supported";
    private static final String LE_AUDIO_BROADCAST_DYNAMIC_SWITCH_PROPERTY =
            "ro.bluetooth.leaudio_broadcast_switcher.supported";
    private static final String LE_AUDIO_DYNAMIC_ENABLED_PROPERTY =
            "persist.bluetooth.leaudio_switcher.enabled";

    private static class ProfileConfig {
        Class mClass;
        boolean mSupported;
        long mMask;

        ProfileConfig(Class theClass, boolean supported, long mask) {
            mClass = theClass;
            mSupported = supported;
            mMask = mask;
        }
    }

    /**
     * List of profile services related to LE audio
     */
    private static final HashSet<Class> mLeAudioUnicastProfiles = new HashSet<Class>(
            Arrays.asList(LeAudioService.class,
                        VolumeControlService.class,
                        McpService.class,
                        CsipSetCoordinatorService.class));

    /**
     * List of profile services with the profile-supported resource flag and bit mask.
     */
    private static final ProfileConfig[] PROFILE_SERVICES_AND_FLAGS = {
            new ProfileConfig(A2dpService.class, A2dpService.isEnabled(),
                    (1 << BluetoothProfile.A2DP)),
            new ProfileConfig(A2dpSinkService.class, A2dpSinkService.isEnabled(),
                    (1 << BluetoothProfile.A2DP_SINK)),
            new ProfileConfig(AvrcpTargetService.class, AvrcpTargetService.isEnabled(),
                    (1 << BluetoothProfile.AVRCP)),
            new ProfileConfig(AvrcpControllerService.class, AvrcpControllerService.isEnabled(),
                    (1 << BluetoothProfile.AVRCP_CONTROLLER)),
            new ProfileConfig(BassClientService.class, BassClientService.isEnabled(),
                    (1 << BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT)),
            new ProfileConfig(BatteryService.class, BatteryService.isEnabled(),
                    (1 << BluetoothProfile.BATTERY)),
            new ProfileConfig(CsipSetCoordinatorService.class,
                    CsipSetCoordinatorService.isEnabled(),
                    (1 << BluetoothProfile.CSIP_SET_COORDINATOR)),
            new ProfileConfig(HapClientService.class, HapClientService.isEnabled(),
                    (1 << BluetoothProfile.HAP_CLIENT)),
            new ProfileConfig(HeadsetService.class, HeadsetService.isEnabled(),
                    (1 << BluetoothProfile.HEADSET)),
            new ProfileConfig(HeadsetClientService.class, HeadsetClientService.isEnabled(),
                    (1 << BluetoothProfile.HEADSET_CLIENT)),
            new ProfileConfig(HearingAidService.class, HearingAidService.isEnabled(),
                    (1 << BluetoothProfile.HEARING_AID)),
            new ProfileConfig(HidDeviceService.class, HidDeviceService.isEnabled(),
                    (1 << BluetoothProfile.HID_DEVICE)),
            new ProfileConfig(HidHostService.class, HidHostService.isEnabled(),
                    (1 << BluetoothProfile.HID_HOST)),
            new ProfileConfig(GattService.class, GattService.isEnabled(),
                    (1 << BluetoothProfile.GATT)),
            new ProfileConfig(LeAudioService.class, LeAudioService.isEnabled(),
                    (1 << BluetoothProfile.LE_AUDIO)),
            new ProfileConfig(TbsService.class, TbsService.isEnabled(),
                    (1 << BluetoothProfile.LE_CALL_CONTROL)),
            new ProfileConfig(BluetoothMapService.class, BluetoothMapService.isEnabled(),
                    (1 << BluetoothProfile.MAP)),
            new ProfileConfig(MapClientService.class, MapClientService.isEnabled(),
                    (1 << BluetoothProfile.MAP_CLIENT)),
            new ProfileConfig(McpService.class, McpService.isEnabled(),
                    (1 << BluetoothProfile.MCP_SERVER)),
            new ProfileConfig(BluetoothOppService.class, BluetoothOppService.isEnabled(),
                    (1 << BluetoothProfile.OPP)),
            new ProfileConfig(PanService.class, PanService.isEnabled(),
                    (1 << BluetoothProfile.PAN)),
            new ProfileConfig(BluetoothPbapService.class, BluetoothPbapService.isEnabled(),
                    (1 << BluetoothProfile.PBAP)),
            new ProfileConfig(PbapClientService.class, PbapClientService.isEnabled(),
                    (1 << BluetoothProfile.PBAP_CLIENT)),
            new ProfileConfig(SapService.class, SapService.isEnabled(),
                    (1 << BluetoothProfile.SAP)),
            new ProfileConfig(VolumeControlService.class, VolumeControlService.isEnabled(),
                    (1 << BluetoothProfile.VOLUME_CONTROL)),
    };

    /**
     * A test function to allow for dynamic enabled
     */
    @VisibleForTesting
    public static void setProfileEnabled(Class profileClass, boolean enabled) {
        if (profileClass == null) {
            return;
        }
        for (ProfileConfig profile : PROFILE_SERVICES_AND_FLAGS) {
            if (profileClass.equals(profile.mClass)) {
                profile.mSupported = enabled;
            }
        }
    }

    private static Class[] sSupportedProfiles = new Class[0];

    private static boolean sIsGdEnabledUptoScanningLayer = false;

    static void init(Context ctx) {
        if (LeAudioService.isBroadcastEnabled()) {
            updateSupportedProfileMask(
                    true, LeAudioService.class, BluetoothProfile.LE_AUDIO_BROADCAST);
        }

        final boolean leAudioDynamicSwitchSupported =
                SystemProperties.getBoolean(LE_AUDIO_DYNAMIC_SWITCH_PROPERTY, false);

        if (leAudioDynamicSwitchSupported) {
            final String leAudioDynamicEnabled = SystemProperties
                    .get(LE_AUDIO_DYNAMIC_ENABLED_PROPERTY, "none");
            if (leAudioDynamicEnabled.equals("true")) {
                setLeAudioProfileStatus(true);
            } else if (leAudioDynamicEnabled.equals("false")) {
                setLeAudioProfileStatus(false);
            }
        }

        ArrayList<Class> profiles = new ArrayList<>(PROFILE_SERVICES_AND_FLAGS.length);
        for (ProfileConfig config : PROFILE_SERVICES_AND_FLAGS) {
            Log.i(TAG, "init: profile=" + config.mClass.getSimpleName() + ", enabled="
                    + config.mSupported);
            if (config.mSupported) {
                profiles.add(config.mClass);
            }
        }
        sSupportedProfiles = profiles.toArray(new Class[profiles.size()]);

        if (ctx == null) {
            return;
        }
        Resources resources = ctx.getResources();
        if (resources == null) {
            return;
        }
        sIsGdEnabledUptoScanningLayer = resources.getBoolean(R.bool.enable_gd_up_to_scanning_layer);
    }

    static void setLeAudioProfileStatus(Boolean enable) {
        setProfileEnabled(CsipSetCoordinatorService.class, enable);
        setProfileEnabled(HapClientService.class, enable);
        setProfileEnabled(LeAudioService.class, enable);
        setProfileEnabled(TbsService.class, enable);
        setProfileEnabled(McpService.class, enable);
        setProfileEnabled(VolumeControlService.class, enable);

        final boolean broadcastDynamicSwitchSupported =
                SystemProperties.getBoolean(LE_AUDIO_BROADCAST_DYNAMIC_SWITCH_PROPERTY, false);

        if (broadcastDynamicSwitchSupported) {
            setProfileEnabled(BassClientService.class, enable);
            updateSupportedProfileMask(
                    enable, LeAudioService.class, BluetoothProfile.LE_AUDIO_BROADCAST);
        }
    }

    /**
     * Remove the input profiles from the supported list.
     */
    static void removeProfileFromSupportedList(HashSet<Class> nonSupportedProfiles) {
        ArrayList<Class> profilesList = new ArrayList<Class>(Arrays.asList(sSupportedProfiles));
        Iterator<Class> iter = profilesList.iterator();

        while (iter.hasNext()) {
            Class profileClass = iter.next();

            if (nonSupportedProfiles.contains(profileClass)) {
                iter.remove();
                Log.v(TAG, "Remove " + profileClass.getSimpleName() + " from supported list.");
            }
        }

        sSupportedProfiles = profilesList.toArray(new Class[profilesList.size()]);
    }

    static void updateSupportedProfileMask(Boolean enable, Class profile, int supportedProfile) {
        for (ProfileConfig config : PROFILE_SERVICES_AND_FLAGS) {
            if (config.mClass == profile) {
                if (enable) {
                    config.mMask |= 1 << supportedProfile;
                } else {
                    config.mMask &= ~(1 << supportedProfile);
                }
                return;
            }
        }
    }

    static HashSet<Class> geLeAudioUnicastProfiles() {
        return mLeAudioUnicastProfiles;
    }

    static Class[] getSupportedProfiles() {
        return sSupportedProfiles;
    }

    static boolean isGdEnabledUpToScanningLayer() {
        return sIsGdEnabledUptoScanningLayer;
    }

    private static long getProfileMask(Class profile) {
        for (ProfileConfig config : PROFILE_SERVICES_AND_FLAGS) {
            if (config.mClass == profile) {
                return config.mMask;
            }
        }
        Log.w(TAG, "Could not find profile bit mask for " + profile.getSimpleName());
        return 0;
    }

    static long getSupportedProfilesBitMask() {
        long mask = 0;
        for (final Class profileClass : getSupportedProfiles()) {
            mask |= getProfileMask(profileClass);
        }
        return mask;
    }
}
