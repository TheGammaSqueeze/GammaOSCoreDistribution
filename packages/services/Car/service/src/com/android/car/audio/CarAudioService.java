/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.car.audio;

import static android.car.builtin.media.AudioManagerHelper.UNDEFINED_STREAM_TYPE;
import static android.car.builtin.media.AudioManagerHelper.isMasterMute;
import static android.car.media.CarAudioManager.AUDIO_FEATURE_DYNAMIC_ROUTING;
import static android.car.media.CarAudioManager.AUDIO_FEATURE_VOLUME_GROUP_MUTING;
import static android.car.media.CarAudioManager.CarAudioFeature;
import static android.car.media.CarAudioManager.INVALID_VOLUME_GROUP_ID;
import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;
import static android.media.AudioManager.FLAG_FROM_KEY;
import static android.media.AudioManager.FLAG_PLAY_SOUND;
import static android.media.AudioManager.FLAG_SHOW_UI;

import static com.android.car.audio.CarVolume.VERSION_TWO;
import static com.android.car.audio.hal.AudioControlWrapper.AUDIOCONTROL_FEATURE_AUDIO_DUCKING;
import static com.android.car.audio.hal.AudioControlWrapper.AUDIOCONTROL_FEATURE_AUDIO_FOCUS;
import static com.android.car.audio.hal.AudioControlWrapper.AUDIOCONTROL_FEATURE_AUDIO_GAIN_CALLBACK;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneConfigChangeListener;
import android.car.builtin.media.AudioManagerHelper;
import android.car.builtin.media.AudioManagerHelper.AudioPatchInfo;
import android.car.builtin.media.AudioManagerHelper.VolumeAndMuteReceiver;
import android.car.builtin.os.UserManagerHelper;
import android.car.builtin.util.Slogf;
import android.car.media.CarAudioManager;
import android.car.media.CarAudioPatchHandle;
import android.car.media.CarVolumeGroupInfo;
import android.car.media.ICarAudio;
import android.car.media.ICarVolumeCallback;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusInfo;
import android.media.AudioManager;
import android.media.audiopolicy.AudioPolicy;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.android.car.CarLocalServices;
import com.android.car.CarLog;
import com.android.car.CarOccupantZoneService;
import com.android.car.CarServiceBase;
import com.android.car.CarServiceUtils;
import com.android.car.R;
import com.android.car.audio.CarAudioContext.AudioContext;
import com.android.car.audio.hal.AudioControlFactory;
import com.android.car.audio.hal.AudioControlWrapper;
import com.android.car.audio.hal.AudioControlWrapperV1;
import com.android.car.audio.hal.HalAudioFocus;
import com.android.car.audio.hal.HalAudioGainCallback;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.annotation.AttributeUsage;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Service responsible for interaction with car's audio system.
 */
public class CarAudioService extends ICarAudio.Stub implements CarServiceBase {

    static final String TAG = CarLog.TAG_AUDIO;

    static final AudioAttributes CAR_DEFAULT_AUDIO_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(AudioAttributes.USAGE_MEDIA);

    private static final String PROPERTY_RO_ENABLE_AUDIO_PATCH =
            "ro.android.car.audio.enableaudiopatch";

    // CarAudioService reads configuration from the following paths respectively.
    // If the first one is found, all others are ignored.
    // If no one is found, it fallbacks to car_volume_groups.xml resource file.
    private static final String[] AUDIO_CONFIGURATION_PATHS = new String[] {
            "/vendor/etc/car_audio_configuration.xml",
            "/system/etc/car_audio_configuration.xml"
    };

    private final Object mImplLock = new Object();

    private final Context mContext;
    private final TelephonyManager mTelephonyManager;
    private final AudioManager mAudioManager;
    private final boolean mUseDynamicRouting;
    private final boolean mUseCarVolumeGroupMuting;
    private final boolean mUseHalDuckingSignals;
    private final @CarVolume.CarVolumeListVersion int mAudioVolumeAdjustmentContextsVersion;
    private final boolean mPersistMasterMuteState;
    private final CarAudioSettings mCarAudioSettings;
    private final int mKeyEventTimeoutMs;
    private AudioControlWrapper mAudioControlWrapper;
    private CarDucking mCarDucking;
    private CarVolumeGroupMuting mCarVolumeGroupMuting;
    private HalAudioFocus mHalAudioFocus;
    private @Nullable CarAudioGainMonitor mCarAudioGainMonitor;

    private CarOccupantZoneService mOccupantZoneService;

    private CarOccupantZoneManager mOccupantZoneManager;

    /**
     * Simulates {@link ICarVolumeCallback} when it's running in legacy mode.
     * This receiver assumes the intent is sent to {@link CarAudioManager#PRIMARY_AUDIO_ZONE}.
     */
    private final VolumeAndMuteReceiver mLegacyVolumeChangedHelper =
            new AudioManagerHelper.VolumeAndMuteReceiver() {
                @Override
                public void onVolumeChanged(int streamType) {
                    if (streamType == UNDEFINED_STREAM_TYPE) {
                        Slogf.w(TAG, "Invalid stream type: %d", streamType);
                    }
                    int groupId = getVolumeGroupIdForStreamType(streamType);
                    if (groupId == INVALID_VOLUME_GROUP_ID) {
                        Slogf.w(TAG, "Unknown stream type: %d", streamType);
                    } else {
                        callbackGroupVolumeChange(PRIMARY_AUDIO_ZONE, groupId,
                                FLAG_FROM_KEY | FLAG_SHOW_UI);
                    }
                }

                @Override
                public void onMuteChanged() {
                    callbackMasterMuteChange(PRIMARY_AUDIO_ZONE, FLAG_FROM_KEY | FLAG_SHOW_UI);
                }
    };

    private AudioPolicy mAudioPolicy;
    private CarZonesAudioFocus mFocusHandler;
    private String mCarAudioConfigurationPath;
    private SparseIntArray mAudioZoneIdToOccupantZoneIdMapping;
    @GuardedBy("mImplLock")
    private SparseArray<CarAudioZone> mCarAudioZones;
    @GuardedBy("mImplLock")
    private CarVolume mCarVolume;
    @GuardedBy("mImplLock")
    private CarAudioContext mCarAudioContext;
    private final CarVolumeCallbackHandler mCarVolumeCallbackHandler;
    private final SparseIntArray mAudioZoneIdToUserIdMapping;
    private final SystemClockWrapper mClock = new SystemClockWrapper();

    // TODO do not store uid mapping here instead use the uid
    //  device affinity in audio policy when available
    private Map<Integer, Integer> mUidToZoneMap;
    private OccupantZoneConfigChangeListener
            mOccupantZoneConfigChangeListener = new CarAudioOccupantConfigChangeListener();
    private CarAudioPlaybackCallback mCarAudioPlaybackCallback;
    private CarAudioPowerListener mCarAudioPowerListener;

    private final HalAudioGainCallback mHalAudioGainCallback =
            new HalAudioGainCallback() {
                @Override
                public void onAudioDeviceGainsChanged(
                        List<Integer> halReasons, List<CarAudioGainConfigInfo> gains) {
                    synchronized (mImplLock) {
                        handleAudioDeviceGainsChangedLocked(halReasons, gains);
                    }
                }
            };

    public CarAudioService(Context context) {
        this(context, getAudioConfigurationPath(), new CarVolumeCallbackHandler());
    }

    @VisibleForTesting
    CarAudioService(Context context, @Nullable String audioConfigurationPath,
            CarVolumeCallbackHandler carVolumeCallbackHandler) {
        mContext = Objects.requireNonNull(context,
                "Context to create car audio service can not be null");
        mCarAudioConfigurationPath = audioConfigurationPath;
        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

        mUseDynamicRouting = mContext.getResources().getBoolean(R.bool.audioUseDynamicRouting);
        mKeyEventTimeoutMs =
                mContext.getResources().getInteger(R.integer.audioVolumeKeyEventTimeoutMs);
        mUseHalDuckingSignals = mContext.getResources().getBoolean(
                R.bool.audioUseHalDuckingSignals);

        mUidToZoneMap = new HashMap<>();
        mCarVolumeCallbackHandler = carVolumeCallbackHandler;
        mCarAudioSettings = new CarAudioSettings(mContext);
        mAudioZoneIdToUserIdMapping = new SparseIntArray();
        mAudioVolumeAdjustmentContextsVersion =
                mContext.getResources().getInteger(R.integer.audioVolumeAdjustmentContextsVersion);
        boolean useCarVolumeGroupMuting = mUseDynamicRouting && mContext.getResources().getBoolean(
                R.bool.audioUseCarVolumeGroupMuting);
        if (mAudioVolumeAdjustmentContextsVersion != VERSION_TWO && useCarVolumeGroupMuting) {
            throw new IllegalArgumentException("audioUseCarVolumeGroupMuting is enabled but "
                    + "this requires audioVolumeAdjustmentContextsVersion 2,"
                    + " instead version " + mAudioVolumeAdjustmentContextsVersion + " was found");
        }
        mUseCarVolumeGroupMuting = useCarVolumeGroupMuting;
        mPersistMasterMuteState = !mUseCarVolumeGroupMuting && mContext.getResources().getBoolean(
                R.bool.audioPersistMasterMuteState);
    }

    /**
     * Dynamic routing and volume groups are set only if
     * {@link #mUseDynamicRouting} is {@code true}. Otherwise, this service runs in legacy mode.
     */
    @Override
    public void init() {
        synchronized (mImplLock) {
            mOccupantZoneService = CarLocalServices.getService(CarOccupantZoneService.class);
            Car car = new Car(mContext, /* service= */null, /* handler= */ null);
            mOccupantZoneManager = new CarOccupantZoneManager(car, mOccupantZoneService);
            if (mUseDynamicRouting) {
                setupDynamicRoutingLocked();
                setupHalAudioFocusListenerLocked();
                setupHalAudioGainCallbackLocked();
                setupAudioConfigurationCallbackLocked();
                setupPowerPolicyListener();
            } else {
                Slogf.i(TAG, "Audio dynamic routing not enabled, run in legacy mode");
                setupLegacyVolumeChangedListener();
            }

            mAudioManager.setSupportedSystemUsages(CarAudioContext.getSystemUsages());
        }

        restoreMasterMuteState();
    }

    private void setupPowerPolicyListener() {
        mCarAudioPowerListener = CarAudioPowerListener.newCarAudioPowerListener(this);
        mCarAudioPowerListener.startListeningForPolicyChanges();
    }

    private void restoreMasterMuteState() {
        if (mUseCarVolumeGroupMuting) {
            return;
        }
        // Restore master mute state if applicable
        if (mPersistMasterMuteState) {
            boolean storedMasterMute = mCarAudioSettings.getMasterMute();
            setMasterMute(storedMasterMute, 0);
        }
    }

    @Override
    public void release() {
        synchronized (mImplLock) {
            if (mUseDynamicRouting) {
                if (mAudioPolicy != null) {
                    mAudioManager.unregisterAudioPolicyAsync(mAudioPolicy);
                    mAudioPolicy = null;
                    mFocusHandler.setOwningPolicy(null, null);
                    mFocusHandler = null;
                }
            } else {
                AudioManagerHelper.unregisterVolumeAndMuteReceiver(mContext,
                        mLegacyVolumeChangedHelper);

            }

            mCarVolumeCallbackHandler.release();

            if (mHalAudioFocus != null) {
                mHalAudioFocus.unregisterFocusListener();
            }

            if (mAudioControlWrapper != null) {
                mAudioControlWrapper.unlinkToDeath();
                mAudioControlWrapper = null;
            }

            if (mCarAudioPowerListener != null) {
                mCarAudioPowerListener.stopListeningForPolicyChanges();
            }
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        synchronized (mImplLock) {
            writer.println("*CarAudioService*");
            writer.increaseIndent();

            writer.println("Configurations:");
            writer.increaseIndent();
            writer.printf("Run in legacy mode? %b\n", !mUseDynamicRouting);
            writer.printf("Audio Patch APIs enabled? %b\n", areAudioPatchAPIsEnabled());
            writer.printf("Persist master mute state? %b\n", mPersistMasterMuteState);
            writer.printf("Use hal ducking signals %b\n", mUseHalDuckingSignals);
            writer.printf("Volume key event timeout ms: %d\n", mKeyEventTimeoutMs);
            if (mCarAudioConfigurationPath != null) {
                writer.printf("Car audio configuration path: %s\n", mCarAudioConfigurationPath);
            }
            writer.decreaseIndent();
            writer.println();

            writer.println("Current State:");
            writer.increaseIndent();
            writer.printf("Master muted? %b\n", isMasterMute(mAudioManager));
            if (mCarAudioPowerListener != null) {
                writer.printf("Audio enabled? %b\n", mCarAudioPowerListener.isAudioEnabled());
            }
            writer.decreaseIndent();
            writer.println();

            if (mUseDynamicRouting) {
                writer.printf("Volume Group Mute Enabled? %b\n", mUseCarVolumeGroupMuting);
                writer.println();
                mCarVolume.dump(writer);
                writer.println();
                mCarAudioContext.dump(writer);
                writer.println();
                for (int i = 0; i < mCarAudioZones.size(); i++) {
                    CarAudioZone zone = mCarAudioZones.valueAt(i);
                    zone.dump(writer);
                }
                writer.println();
                writer.println("UserId to Zone Mapping:");
                writer.increaseIndent();
                for (int index = 0; index < mAudioZoneIdToUserIdMapping.size(); index++) {
                    int audioZoneId = mAudioZoneIdToUserIdMapping.keyAt(index);
                    writer.printf("UserId %d mapped to zone %d\n",
                            mAudioZoneIdToUserIdMapping.get(audioZoneId),
                            audioZoneId);
                }
                writer.decreaseIndent();
                writer.println();
                writer.println("Audio Zone to Occupant Zone Mapping:");
                writer.increaseIndent();
                for (int index = 0; index < mAudioZoneIdToOccupantZoneIdMapping.size(); index++) {
                    int audioZoneId = mAudioZoneIdToOccupantZoneIdMapping.keyAt(index);
                    writer.printf("AudioZoneId %d mapped to OccupantZoneId %d\n", audioZoneId,
                            mAudioZoneIdToOccupantZoneIdMapping.get(audioZoneId));
                }
                writer.decreaseIndent();
                writer.println();
                writer.println("UID to Zone Mapping:");
                writer.increaseIndent();
                for (int callingId : mUidToZoneMap.keySet()) {
                    writer.printf("UID %d mapped to zone %d\n",
                            callingId,
                            mUidToZoneMap.get(callingId));
                }
                writer.decreaseIndent();

                writer.println();
                mFocusHandler.dump(writer);

                writer.println();
                getAudioControlWrapperLocked().dump(writer);

                if (mHalAudioFocus != null) {
                    writer.println();
                    mHalAudioFocus.dump(writer);
                } else {
                    writer.println("No HalAudioFocus instance\n");
                }
                if (mCarDucking != null) {
                    writer.println();
                    mCarDucking.dump(writer);
                }
                if (mCarVolumeGroupMuting != null) {
                    mCarVolumeGroupMuting.dump(writer);
                }

            }
            writer.decreaseIndent();
        }
    }

    @Override
    public boolean isAudioFeatureEnabled(@CarAudioFeature int audioFeatureType) {
        switch (audioFeatureType) {
            case AUDIO_FEATURE_DYNAMIC_ROUTING:
                return mUseDynamicRouting;
            case AUDIO_FEATURE_VOLUME_GROUP_MUTING:
                return mUseCarVolumeGroupMuting;
            default:
                throw new IllegalArgumentException("Unknown Audio Feature type: "
                        + audioFeatureType);
        }
    }

    /**
     * @see {@link android.car.media.CarAudioManager#setGroupVolume(int, int, int, int)}
     */
    @Override
    public void setGroupVolume(int zoneId, int groupId, int index, int flags) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
        callbackGroupVolumeChange(zoneId, groupId, flags);
        // For legacy stream type based volume control
        boolean wasMute;
        if (!mUseDynamicRouting) {
            mAudioManager.setStreamVolume(
                    CarAudioDynamicRouting.STREAM_TYPES[groupId], index, flags);
            return;
        }
        synchronized (mImplLock) {
            CarVolumeGroup group = getCarVolumeGroupLocked(zoneId, groupId);
            wasMute = group.isMuted();
            group.setCurrentGainIndex(index);
        }
        if (wasMute) {
            handleMuteChanged(zoneId, groupId, flags);
        }
    }

    private void handleMuteChanged(int zoneId, int groupId, int flags) {
        callbackGroupMuteChanged(zoneId, groupId, flags);
        mCarVolumeGroupMuting.carMuteChanged();
    }

    private void callbackGroupVolumeChange(int zoneId, int groupId, int flags) {
        if (mUseDynamicRouting && !isPlaybackOnVolumeGroupActive(zoneId, groupId)) {
            flags |= FLAG_PLAY_SOUND;
        }
        mCarVolumeCallbackHandler.onVolumeGroupChange(zoneId, groupId, flags);
    }

    private void callbackGroupMuteChanged(int zoneId, int groupId, int flags) {
        mCarVolumeCallbackHandler.onGroupMuteChange(zoneId, groupId, flags);
    }

    void setMasterMute(boolean mute, int flags) {
        AudioManagerHelper.setMasterMute(mAudioManager, mute, flags);

        // Master Mute only applies to primary zone
        callbackMasterMuteChange(PRIMARY_AUDIO_ZONE, flags);
    }

    void callbackMasterMuteChange(int zoneId, int flags) {
        mCarVolumeCallbackHandler.onMasterMuteChanged(zoneId, flags);

        // Persists master mute state if applicable
        if (mPersistMasterMuteState) {
            mCarAudioSettings.storeMasterMute(isMasterMute(mAudioManager));
        }
    }

    /**
     * @see {@link android.car.media.CarAudioManager#getGroupMaxVolume(int, int)}
     */
    @Override
    public int getGroupMaxVolume(int zoneId, int groupId) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);

        if (!mUseDynamicRouting) {
            return mAudioManager.getStreamMaxVolume(
                    CarAudioDynamicRouting.STREAM_TYPES[groupId]);
        }

        synchronized (mImplLock) {
            CarVolumeGroup group = getCarVolumeGroupLocked(zoneId, groupId);
            return group.getMaxGainIndex();
        }
    }

    /**
     * @see {@link android.car.media.CarAudioManager#getGroupMinVolume(int, int)}
     */
    @Override
    public int getGroupMinVolume(int zoneId, int groupId) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);

        if (!mUseDynamicRouting) {
            return mAudioManager.getStreamMinVolume(
                    CarAudioDynamicRouting.STREAM_TYPES[groupId]);
        }

        synchronized (mImplLock) {
            CarVolumeGroup group = getCarVolumeGroupLocked(zoneId, groupId);
            return group.getMinGainIndex();
        }
    }

    /**
     * @see {@link android.car.media.CarAudioManager#getGroupVolume(int, int)}
     */
    @Override
    public int getGroupVolume(int zoneId, int groupId) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);

        // For legacy stream type based volume control
        if (!mUseDynamicRouting) {
            return mAudioManager.getStreamVolume(
                    CarAudioDynamicRouting.STREAM_TYPES[groupId]);
        }

        synchronized (mImplLock) {
            CarVolumeGroup group = getCarVolumeGroupLocked(zoneId, groupId);
            return group.getCurrentGainIndex();
        }
    }

    @GuardedBy("mImplLock")
    private CarVolumeGroup getCarVolumeGroupLocked(int zoneId, int groupId) {
        return getCarAudioZoneLocked(zoneId).getVolumeGroup(groupId);
    }

    private void setupLegacyVolumeChangedListener() {
        AudioManagerHelper.registerVolumeAndMuteReceiver(mContext, mLegacyVolumeChangedHelper);
    }

    private List<CarAudioDeviceInfo> generateCarAudioDeviceInfos() {
        AudioDeviceInfo[] deviceInfos = mAudioManager.getDevices(
                AudioManager.GET_DEVICES_OUTPUTS);

        List<CarAudioDeviceInfo> infos = new ArrayList<>();

        for (int index = 0; index < deviceInfos.length; index++) {
            if (deviceInfos[index].getType() == AudioDeviceInfo.TYPE_BUS) {
                infos.add(new CarAudioDeviceInfo(mAudioManager, deviceInfos[index]));
            }
        }
        return infos;
    }

    private AudioDeviceInfo[] getAllInputDevices() {
        return mAudioManager.getDevices(
                AudioManager.GET_DEVICES_INPUTS);
    }

    @GuardedBy("mImplLock")
    private SparseArray<CarAudioZone> loadCarAudioConfigurationLocked(
            List<CarAudioDeviceInfo> carAudioDeviceInfos, AudioDeviceInfo[] inputDevices) {
        try (InputStream inputStream = new FileInputStream(mCarAudioConfigurationPath)) {
            CarAudioZonesHelper zonesHelper = new CarAudioZonesHelper(mCarAudioSettings,
                    inputStream, carAudioDeviceInfos, inputDevices, mUseCarVolumeGroupMuting);
            mAudioZoneIdToOccupantZoneIdMapping =
                    zonesHelper.getCarAudioZoneIdToOccupantZoneIdMapping();
            SparseArray<CarAudioZone> zones = zonesHelper.loadAudioZones();
            mCarAudioContext = zonesHelper.getCarAudioContext();
            return zones;
        } catch (IOException | XmlPullParserException e) {
            throw new RuntimeException("Failed to parse audio zone configuration", e);
        }
    }

    @GuardedBy("mImplLock")
    private SparseArray<CarAudioZone> loadVolumeGroupConfigurationWithAudioControlLocked(
            List<CarAudioDeviceInfo> carAudioDeviceInfos, AudioDeviceInfo[] inputDevices) {
        AudioControlWrapper audioControlWrapper = getAudioControlWrapperLocked();
        if (!(audioControlWrapper instanceof AudioControlWrapperV1)) {
            throw new IllegalStateException(
                    "Updated version of IAudioControl no longer supports CarAudioZonesHelperLegacy."
                    + " Please provide car_audio_configuration.xml.");
        }
        mCarAudioContext = new CarAudioContext(CarAudioContext.getAllContextsInfo());
        CarAudioZonesHelperLegacy legacyHelper = new CarAudioZonesHelperLegacy(mContext,
                mCarAudioContext, R.xml.car_volume_groups, carAudioDeviceInfos,
                (AudioControlWrapperV1) audioControlWrapper,
                mCarAudioSettings, inputDevices);
        return legacyHelper.loadAudioZones();
    }

    @GuardedBy("mImplLock")
    private void loadCarAudioZonesLocked() {
        List<CarAudioDeviceInfo> carAudioDeviceInfos = generateCarAudioDeviceInfos();
        AudioDeviceInfo[] inputDevices = getAllInputDevices();

        if (mCarAudioConfigurationPath != null) {
            mCarAudioZones = loadCarAudioConfigurationLocked(carAudioDeviceInfos, inputDevices);
        } else {
            mCarAudioZones =
                    loadVolumeGroupConfigurationWithAudioControlLocked(carAudioDeviceInfos,
                            inputDevices);
        }

        CarAudioZonesValidator.validate(mCarAudioZones);
    }

    @GuardedBy("mImplLock")
    private void setupDynamicRoutingLocked() {
        final AudioPolicy.Builder builder = new AudioPolicy.Builder(mContext);
        builder.setLooper(Looper.getMainLooper());

        loadCarAudioZonesLocked();

        mCarVolume = new CarVolume(mCarAudioContext, mClock,
                mAudioVolumeAdjustmentContextsVersion, mKeyEventTimeoutMs);

        for (int i = 0; i < mCarAudioZones.size(); i++) {
            CarAudioZone zone = mCarAudioZones.valueAt(i);
            // Ensure HAL gets our initial value
            zone.synchronizeCurrentGainIndex();
            Slogf.v(TAG, "Processed audio zone: %s", zone);
        }

        CarAudioDynamicRouting.setupAudioDynamicRouting(builder, mCarAudioZones,
                mCarAudioContext);

        // Attach the {@link AudioPolicyVolumeCallback}
        CarAudioPolicyVolumeCallback
                .addVolumeCallbackToPolicy(builder, mAudioManager, new CarVolumeInfoWrapper(this),
                        mUseCarVolumeGroupMuting);


        AudioControlWrapper audioControlWrapper = getAudioControlWrapperLocked();
        if (mUseHalDuckingSignals) {
            if (audioControlWrapper.supportsFeature(AUDIOCONTROL_FEATURE_AUDIO_DUCKING)) {
                mCarDucking = new CarDucking(mCarAudioZones, audioControlWrapper);
            }
        }

        if (mUseCarVolumeGroupMuting) {
            mCarVolumeGroupMuting = new CarVolumeGroupMuting(mCarAudioZones, audioControlWrapper);
        }

        // Configure our AudioPolicy to handle focus events.
        // This gives us the ability to decide which audio focus requests to accept and bypasses
        // the framework ducking logic.
        mFocusHandler = CarZonesAudioFocus.createCarZonesAudioFocus(mAudioManager,
                mContext.getPackageManager(),
                mCarAudioZones,
                mCarAudioSettings,
                mCarDucking,
                new CarVolumeInfoWrapper(this));
        builder.setAudioPolicyFocusListener(mFocusHandler);
        builder.setIsAudioFocusPolicy(true);

        mAudioPolicy = builder.build();

        // Connect the AudioPolicy and the focus listener
        mFocusHandler.setOwningPolicy(this, mAudioPolicy);

        int r = mAudioManager.registerAudioPolicy(mAudioPolicy);
        if (r != AudioManager.SUCCESS) {
            throw new RuntimeException("registerAudioPolicy failed " + r);
        }

        setupOccupantZoneInfo();
    }

    @GuardedBy("mImplLock")
    private void setupAudioConfigurationCallbackLocked() {
        mCarAudioPlaybackCallback =
                new CarAudioPlaybackCallback(getCarAudioZone(PRIMARY_AUDIO_ZONE),
                        mClock, mKeyEventTimeoutMs);
        mAudioManager.registerAudioPlaybackCallback(mCarAudioPlaybackCallback, null);
    }

    private void setupOccupantZoneInfo() {
        CarOccupantZoneService occupantZoneService;
        CarOccupantZoneManager occupantZoneManager;
        SparseIntArray audioZoneIdToOccupantZoneMapping;
        OccupantZoneConfigChangeListener listener;
        synchronized (mImplLock) {
            audioZoneIdToOccupantZoneMapping = mAudioZoneIdToOccupantZoneIdMapping;
            occupantZoneService = mOccupantZoneService;
            occupantZoneManager = mOccupantZoneManager;
            listener = mOccupantZoneConfigChangeListener;
        }
        occupantZoneService.setAudioZoneIdsForOccupantZoneIds(audioZoneIdToOccupantZoneMapping);
        occupantZoneManager.registerOccupantZoneConfigChangeListener(listener);
    }

    @GuardedBy("mImplLock")
    private void setupHalAudioFocusListenerLocked() {
        AudioControlWrapper audioControlWrapper = getAudioControlWrapperLocked();
        if (!audioControlWrapper.supportsFeature(AUDIOCONTROL_FEATURE_AUDIO_FOCUS)) {
            Slogf.d(TAG, "HalAudioFocus is not supported on this device");
            return;
        }

        mHalAudioFocus = new HalAudioFocus(mAudioManager, mAudioControlWrapper, getAudioZoneIds());
        mHalAudioFocus.registerFocusListener();
    }

    @GuardedBy("mImplLock")
    private void setupHalAudioGainCallbackLocked() {
        AudioControlWrapper audioControlWrapper = getAudioControlWrapperLocked();
        if (!audioControlWrapper.supportsFeature(AUDIOCONTROL_FEATURE_AUDIO_GAIN_CALLBACK)) {
            Slogf.d(CarLog.TAG_AUDIO, "HalAudioGainCallback is not supported on this device");
            return;
        }
        mCarAudioGainMonitor = new CarAudioGainMonitor(mAudioControlWrapper, mCarAudioZones);
        mCarAudioGainMonitor.registerAudioGainListener(mHalAudioGainCallback);
    }

    /**
     * Read from {@link #AUDIO_CONFIGURATION_PATHS} respectively.
     * @return File path of the first hit in {@link #AUDIO_CONFIGURATION_PATHS}
     */
    @Nullable
    private static String getAudioConfigurationPath() {
        for (String path : AUDIO_CONFIGURATION_PATHS) {
            File configuration = new File(path);
            if (configuration.exists()) {
                return path;
            }
        }
        return null;
    }

    @Override
    public void setFadeTowardFront(float value) {
        synchronized (mImplLock) {
            enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
            getAudioControlWrapperLocked().setFadeTowardFront(value);
        }
    }

    @Override
    public void setBalanceTowardRight(float value) {
        synchronized (mImplLock) {
            enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
            getAudioControlWrapperLocked().setBalanceTowardRight(value);
        }
    }

    /**
     * @return Array of accumulated device addresses, empty array if we found nothing
     */
    @Override
    public @NonNull String[] getExternalSources() {
        synchronized (mImplLock) {
            enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
            List<String> sourceAddresses = new ArrayList<>();

            AudioDeviceInfo[] devices = mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
            if (devices.length == 0) {
                Slogf.w(TAG, "getExternalSources, no input devices found");
            }

            // Collect the list of non-microphone input ports
            for (AudioDeviceInfo info : devices) {
                switch (info.getType()) {
                    // TODO:  Can we trim this set down? Especially duplicates like FM vs FM_TUNER?
                    case AudioDeviceInfo.TYPE_FM:
                    case AudioDeviceInfo.TYPE_FM_TUNER:
                    case AudioDeviceInfo.TYPE_TV_TUNER:
                    case AudioDeviceInfo.TYPE_HDMI:
                    case AudioDeviceInfo.TYPE_AUX_LINE:
                    case AudioDeviceInfo.TYPE_LINE_ANALOG:
                    case AudioDeviceInfo.TYPE_LINE_DIGITAL:
                    case AudioDeviceInfo.TYPE_USB_ACCESSORY:
                    case AudioDeviceInfo.TYPE_USB_DEVICE:
                    case AudioDeviceInfo.TYPE_USB_HEADSET:
                    case AudioDeviceInfo.TYPE_IP:
                    case AudioDeviceInfo.TYPE_BUS:
                        String address = info.getAddress();
                        if (TextUtils.isEmpty(address)) {
                            Slogf.w(TAG, "Discarded device with empty address, type=%d",
                                    info.getType());
                        } else {
                            sourceAddresses.add(address);
                        }
                }
            }

            return sourceAddresses.toArray(new String[0]);
        }
    }

    @Override
    public CarAudioPatchHandle createAudioPatch(String sourceAddress,
            @AttributeUsage int usage, int gainInMillibels) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        enforceCanUseAudioPatchAPI();
        synchronized (mImplLock) {
            return createAudioPatchLocked(sourceAddress, usage, gainInMillibels);
        }
    }

    @Override
    public void releaseAudioPatch(CarAudioPatchHandle carPatch) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        enforceCanUseAudioPatchAPI();
        synchronized (mImplLock) {
            releaseAudioPatchLocked(carPatch);
        }
    }

    private void enforceCanUseAudioPatchAPI() {
        if (!areAudioPatchAPIsEnabled()) {
            throw new IllegalStateException("Audio Patch APIs not enabled, see "
                    + PROPERTY_RO_ENABLE_AUDIO_PATCH);
        }
    }

    private boolean areAudioPatchAPIsEnabled() {
        return SystemProperties.getBoolean(PROPERTY_RO_ENABLE_AUDIO_PATCH, /* default= */ false);
    }

    @GuardedBy("mImplLock")
    private CarAudioPatchHandle createAudioPatchLocked(String sourceAddress,
            @AttributeUsage int usage, int gainInMillibels) {
        // Find the named source port
        AudioDeviceInfo sourcePortInfo = null;
        AudioDeviceInfo[] deviceInfos = mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
        for (AudioDeviceInfo info : deviceInfos) {
            if (sourceAddress.equals(info.getAddress())) {
                // This is the one for which we're looking
                sourcePortInfo = info;
                break;
            }
        }
        Objects.requireNonNull(sourcePortInfo,
                "Specified source is not available: " + sourceAddress);

        AudioAttributes audioAttributes = CarAudioContext.getAudioAttributeFromUsage(usage);

        AudioPatchInfo audioPatchInfo = AudioManagerHelper.createAudioPatch(sourcePortInfo,
                getOutputDeviceForAudioAttributeLocked(PRIMARY_AUDIO_ZONE, audioAttributes),
                gainInMillibels);

        Slogf.d(TAG, "Audio patch created: %s", audioPatchInfo);

        // Ensure the initial volume on output device port
        int groupId = getVolumeGroupIdForAudioAttributeLocked(PRIMARY_AUDIO_ZONE, audioAttributes);
        setGroupVolume(PRIMARY_AUDIO_ZONE, groupId,
                getGroupVolume(PRIMARY_AUDIO_ZONE, groupId), 0);

        return new CarAudioPatchHandle(audioPatchInfo.getHandleId(),
                audioPatchInfo.getSourceAddress(), audioPatchInfo.getSinkAddress());
    }

    @GuardedBy("mImplLock")
    private void releaseAudioPatchLocked(CarAudioPatchHandle carPatch) {
        Objects.requireNonNull(carPatch);

        if (AudioManagerHelper.releaseAudioPatch(mAudioManager, getAudioPatchInfo(carPatch))) {
            Slogf.d(TAG, "releaseAudioPatch %s successfully", carPatch);
        }
        // If we didn't find a match, then something went awry, but it's probably not fatal...
        Slogf.e(TAG, "releaseAudioPatch found no match for %s", carPatch);
    }

    private static AudioPatchInfo getAudioPatchInfo(CarAudioPatchHandle carPatch) {
        return new AudioPatchInfo(carPatch.getSourceAddress(),
                carPatch.getSinkAddress(),
                carPatch.getHandleId());
    }

    @Override
    public int getVolumeGroupCount(int zoneId) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);

        if (!mUseDynamicRouting) {
            return CarAudioDynamicRouting.STREAM_TYPES.length;
        }

        synchronized (mImplLock) {
            return getCarAudioZoneLocked(zoneId).getVolumeGroupCount();
        }
    }

    @Override
    public int getVolumeGroupIdForUsage(int zoneId, @AttributeUsage int usage) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
        if (!CarAudioContext.isValidAudioAttributeUsage(usage)) {
            return INVALID_VOLUME_GROUP_ID;
        }

        synchronized (mImplLock) {
            return getVolumeGroupIdForAudioAttributeLocked(zoneId,
                    CarAudioContext.getAudioAttributeFromUsage(usage));
        }
    }

    @Override
    public CarVolumeGroupInfo getVolumeGroupInfo(int zoneId, int groupId) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
        if (!mUseDynamicRouting) {
            return null;
        }
        synchronized (mImplLock) {
            return getCarVolumeGroupLocked(zoneId, groupId).getCarVolumeGroupInfo();
        }
    }

    @Override
    public List<CarVolumeGroupInfo> getVolumeGroupInfosForZone(int zoneId) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
        if (!mUseDynamicRouting) {
            return Collections.EMPTY_LIST;
        }
        synchronized (mImplLock) {
            return getCarAudioZoneLocked(zoneId).getVolumeGroupInfos();
        }
    }

    @Override
    public List<AudioAttributes> getAudioAttributesForVolumeGroup(CarVolumeGroupInfo groupInfo) {
        Objects.requireNonNull(groupInfo, "Car volume group info can not be null");
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
        if (!mUseDynamicRouting) {
            return Collections.EMPTY_LIST;
        }

        synchronized (mImplLock) {
            return getCarAudioZoneLocked(groupInfo.getZoneId())
                    .getVolumeGroup(groupInfo.getId()).getAudioAttributes();
        }
    }

    @GuardedBy("mImplLock")
    private int getVolumeGroupIdForAudioAttributeLocked(int zoneId,
            AudioAttributes audioAttributes) {
        if (!mUseDynamicRouting) {
            return getStreamTypeFromAudioAttribute(audioAttributes);
        }

        @AudioContext int audioContext =
                mCarAudioContext.getContextForAudioAttribute(audioAttributes);
        return getVolumeGroupIdForAudioContextLocked(zoneId, audioContext);
    }

    private static int getStreamTypeFromAudioAttribute(AudioAttributes audioAttributes) {
        int usage = audioAttributes.getSystemUsage();
        for (int i = 0; i < CarAudioDynamicRouting.STREAM_TYPE_USAGES.length; i++) {
            if (usage == CarAudioDynamicRouting.STREAM_TYPE_USAGES[i]) {
                return i;
            }
        }

        return INVALID_VOLUME_GROUP_ID;
    }

    @GuardedBy("mImplLock")
    private int getVolumeGroupIdForAudioContextLocked(int zoneId, @AudioContext int audioContext) {
        CarVolumeGroup[] groups = getCarAudioZoneLocked(zoneId).getVolumeGroups();
        for (int i = 0; i < groups.length; i++) {
            int[] groupAudioContexts = groups[i].getContexts();
            for (int groupAudioContext : groupAudioContexts) {
                if (audioContext == groupAudioContext) {
                    return i;
                }
            }
        }
        return INVALID_VOLUME_GROUP_ID;
    }

    @Override
    public @NonNull int[] getUsagesForVolumeGroupId(int zoneId, int groupId) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);

        if (!mUseDynamicRouting) {
            return new int[] { CarAudioDynamicRouting.STREAM_TYPE_USAGES[groupId] };
        }
        synchronized (mImplLock) {
            CarVolumeGroup group = getCarVolumeGroupLocked(zoneId, groupId);
            int[] contexts = group.getContexts();
            List<Integer> usages = new ArrayList<>();
            for (int index = 0; index < contexts.length; index++) {
                AudioAttributes[] attributesForContext =
                        mCarAudioContext.getAudioAttributesForContext(contexts[index]);
                for (int counter = 0; counter < attributesForContext.length; counter++) {
                    usages.add(attributesForContext[counter].getSystemUsage());
                }
            }

            int[] usagesArray = CarServiceUtils.toIntArray(usages);

            return usagesArray;
        }
    }

    @Override
    public boolean isPlaybackOnVolumeGroupActive(int zoneId, int groupId) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
        requireDynamicRouting();
        Preconditions.checkArgument(isAudioZoneIdValid(zoneId),
                "Invalid audio zone id %d", zoneId);

        CarVolume carVolume;
        synchronized (mImplLock) {
            carVolume = mCarVolume;
        }
        return carVolume.isAnyContextActive(getContextsForVolumeGroupId(zoneId, groupId),
                getActiveAttributesFromPlaybackConfigurations(zoneId),
                getCallStateForZone(zoneId), getActiveHalAudioAttributesForZone(zoneId));
    }

    /**
     *
     * returns the current call state ({@code CALL_STATE_OFFHOOK}, {@code CALL_STATE_RINGING},
     * {@code CALL_STATE_IDLE}) from the telephony manager.
     */
    int getCallStateForZone(int zoneId) {
        synchronized (mImplLock) {
            // Only driver can use telephony stack
            if (getUserIdForZoneLocked(zoneId) == mOccupantZoneService.getDriverUserId()) {
                return mTelephonyManager.getCallState();
            }
        }
        return TelephonyManager.CALL_STATE_IDLE;
    }

    private List<AudioAttributes> getActiveAttributesFromPlaybackConfigurations(int zoneId) {
        return getCarAudioZone(zoneId)
                .findActiveAudioAttributesFromPlaybackConfigurations(mAudioManager
                        .getActivePlaybackConfigurations());
    }


    private @NonNull @AudioContext int[] getContextsForVolumeGroupId(int zoneId, int groupId) {
        synchronized (mImplLock) {
            CarVolumeGroup group = getCarVolumeGroupLocked(zoneId, groupId);
            return group.getContexts();
        }
    }

    /**
     * Gets the ids of all available audio zones
     *
     * @return Array of available audio zones ids
     */
    @Override
    public @NonNull int[] getAudioZoneIds() {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireDynamicRouting();
        synchronized (mImplLock) {
            int[] zoneIds = new int[mCarAudioZones.size()];
            for (int i = 0; i < mCarAudioZones.size(); i++) {
                zoneIds[i] = mCarAudioZones.keyAt(i);
            }
            return zoneIds;
        }
    }

    /**
     * Gets the audio zone id currently mapped to uid,
     *
     * <p><b>Note:</b> Will use uid mapping first, followed by uid's {@userId} mapping.
     * defaults to PRIMARY_AUDIO_ZONE if no mapping exist
     *
     * @param uid The uid
     * @return zone id mapped to uid
     */
    @Override
    public int getZoneIdForUid(int uid) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireDynamicRouting();
        synchronized (mImplLock) {
            if (mUidToZoneMap.containsKey(uid)) {
                return mUidToZoneMap.get(uid);
            }
            int userId = UserHandle.getUserHandleForUid(uid).getIdentifier();
            return getZoneIdForUserIdLocked(userId);
        }
    }

    @GuardedBy("mImplLock")
    private int getZoneIdForUserIdLocked(@UserIdInt int userId) {
        int audioZoneId = mOccupantZoneService.getAudioZoneIdForOccupant(
                mOccupantZoneService.getOccupantZoneIdForUserId(userId));
        if (audioZoneId != CarAudioManager.INVALID_AUDIO_ZONE) {
            return audioZoneId;
        }
        Slogf.w(TAG,
                "getZoneIdForUid userId %d does not have a zone. Defaulting to %s: %d",
                userId, "PRIMARY_AUDIO_ZONE", PRIMARY_AUDIO_ZONE);
        return PRIMARY_AUDIO_ZONE;
    }

    /**
     * Maps the audio zone id to uid
     *
     * @param zoneId The audio zone id
     * @param uid The uid to map
     *
     * <p><b>Note:</b> Will throw if occupant zone mapping exist, as uid and occupant zone mapping
     * do not work in conjunction.
     *
     * @return true if the device affinities, for devices in zone, are successfully set
     */
    @Override
    public boolean setZoneIdForUid(int zoneId, int uid) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireDynamicRouting();
        synchronized (mImplLock) {
            checkAudioZoneIdLocked(zoneId);
            Slogf.i(TAG, "setZoneIdForUid Calling uid %d mapped to : %d", uid, zoneId);

            // If occupant mapping exist uid routing can not be used
            requiredOccupantZoneMappingDisabledLocked();

            // Figure out if anything is currently holding focus,
            // This will change the focus to transient loss while we are switching zones
            Integer currentZoneId = mUidToZoneMap.get(uid);
            ArrayList<AudioFocusInfo> currentFocusHoldersForUid = new ArrayList<>();
            ArrayList<AudioFocusInfo> currentFocusLosersForUid = new ArrayList<>();
            if (currentZoneId != null) {
                currentFocusHoldersForUid = mFocusHandler.getAudioFocusHoldersForUid(uid,
                        currentZoneId.intValue());
                currentFocusLosersForUid = mFocusHandler.getAudioFocusLosersForUid(uid,
                        currentZoneId.intValue());
                if (!currentFocusHoldersForUid.isEmpty() || !currentFocusLosersForUid.isEmpty()) {
                    // Order matters here: Remove the focus losers first
                    // then do the current holder to prevent loser from popping up while
                    // the focus is being remove for current holders
                    // Remove focus for current focus losers
                    mFocusHandler.transientlyLoseInFocusInZone(currentFocusLosersForUid,
                            currentZoneId.intValue());
                    // Remove focus for current holders
                    mFocusHandler.transientlyLoseInFocusInZone(currentFocusHoldersForUid,
                            currentZoneId.intValue());
                }
            }

            // if the current uid is in the list
            // remove it from the list

            if (checkAndRemoveUidLocked(uid)) {
                if (setZoneIdForUidNoCheckLocked(zoneId, uid)) {
                    // Order matters here: Regain focus for
                    // Previously lost focus holders then regain
                    // focus for holders that had it last
                    // Regain focus for the focus losers from previous zone
                    if (!currentFocusLosersForUid.isEmpty()) {
                        regainAudioFocusLocked(currentFocusLosersForUid, zoneId);
                    }
                    // Regain focus for the focus holders from previous zone
                    if (!currentFocusHoldersForUid.isEmpty()) {
                        regainAudioFocusLocked(currentFocusHoldersForUid, zoneId);
                    }
                    return true;
                }
            }
            return false;
        }
    }

    @GuardedBy("mImplLock")
    private AudioDeviceInfo getOutputDeviceForAudioAttributeLocked(int zoneId,
            AudioAttributes audioAttributes) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireDynamicRouting();
        int contextForUsage = mCarAudioContext.getContextForAudioAttribute(audioAttributes);
        Preconditions.checkArgument(!CarAudioContext.isInvalidContextId(contextForUsage),
                "Invalid audio attribute usage %d", audioAttributes);
        return getCarAudioZoneLocked(zoneId).getAudioDeviceForContext(contextForUsage);
    }

    @Override
    public String getOutputDeviceAddressForUsage(int zoneId, @AttributeUsage int usage) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireDynamicRouting();
        CarAudioContext.checkAudioAttributeUsage(usage);
        int contextForUsage = getCarAudioContext()
                .getContextForAudioAttribute(CarAudioContext.getAudioAttributeFromUsage(usage));
        return getCarAudioZone(zoneId).getAddressForContext(contextForUsage);
    }

    /**
     * Regain focus for the focus list passed in
     * @param afiList focus info list to regain
     * @param zoneId zone id where the focus holder belong
     */
    @GuardedBy("mImplLock")
    void regainAudioFocusLocked(ArrayList<AudioFocusInfo> afiList, int zoneId) {
        for (AudioFocusInfo info : afiList) {
            if (mFocusHandler.reevaluateAndRegainAudioFocus(info)
                    != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Slogf.i(TAG,
                        " Focus could not be granted for entry %s uid %d in zone %d",
                        info.getClientId(), info.getClientUid(), zoneId);
            }
        }
    }

    /**
     * Removes the current mapping of the uid, focus will be lost in zone
     * @param uid The uid to remove
     *
     * <p><b>Note:</b> Will throw if occupant zone mapping exist, as uid and occupant zone mapping
     * do not work in conjunction.
     *
     * return true if all the devices affinities currently
     *            mapped to uid are successfully removed
     */
    @Override
    public boolean clearZoneIdForUid(int uid) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireDynamicRouting();
        synchronized (mImplLock) {
            // Throw so as to not set the wrong expectation,
            // that routing will be changed if clearZoneIdForUid is called.
            requiredOccupantZoneMappingDisabledLocked();

            return checkAndRemoveUidLocked(uid);
        }
    }

    /**
     * Sets the zone id for uid
     * @param zoneId zone id to map to uid
     * @param uid uid to map
     * @return true if setting uid device affinity is successful
     */
    @GuardedBy("mImplLock")
    private boolean setZoneIdForUidNoCheckLocked(int zoneId, int uid) {
        Slogf.d(TAG, "setZoneIdForUidNoCheck Calling uid %d mapped to %d", uid, zoneId);
        //Request to add uid device affinity
        List<AudioDeviceInfo> deviceInfos = getCarAudioZoneLocked(zoneId).getAudioDeviceInfos();
        if (mAudioPolicy.setUidDeviceAffinity(uid, deviceInfos)) {
            // TODO do not store uid mapping here instead use the uid
            //  device affinity in audio policy when available
            mUidToZoneMap.put(uid, zoneId);
            return true;
        }
        Slogf.w(TAG, "setZoneIdForUidNoCheck Failed set device affinity for uid %d in zone %d",
                uid, zoneId);
        return false;
    }

    /**
     * Check if uid is attached to a zone and remove it
     * @param uid unique id to remove
     * @return true if the uid was successfully removed or mapping was not assigned
     */
    @GuardedBy("mImplLock")
    private boolean checkAndRemoveUidLocked(int uid) {
        Integer zoneId = mUidToZoneMap.get(uid);
        if (zoneId != null) {
            Slogf.i(TAG, "checkAndRemoveUid removing Calling uid %d from zone %d", uid, zoneId);
            if (mAudioPolicy.removeUidDeviceAffinity(uid)) {
                // TODO use the uid device affinity in audio policy when available
                mUidToZoneMap.remove(uid);
                return true;
            }
            //failed to remove device affinity from zone devices
            Slogf.w(TAG, "checkAndRemoveUid Failed remove device affinity for uid %d in zone %d",
                    uid, zoneId);
            return false;
        }
        return true;
    }

    @Override
    public void registerVolumeCallback(@NonNull IBinder binder) {
        synchronized (mImplLock) {
            enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
            mCarVolumeCallbackHandler.registerCallback(binder);
        }
    }

    @Override
    public void unregisterVolumeCallback(@NonNull IBinder binder) {
        synchronized (mImplLock) {
            enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
            mCarVolumeCallbackHandler.unregisterCallback(binder);
        }
    }

    /**
     * @see {@link android.car.media.CarAudioManager#isVolumeGroupMuted(int, int)}
     */
    @Override
    public boolean isVolumeGroupMuted(int zoneId, int groupId) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
        requireDynamicRouting();
        if (!mUseCarVolumeGroupMuting) {
            return false;
        }
        synchronized (mImplLock) {
            CarVolumeGroup group = getCarVolumeGroupLocked(zoneId, groupId);
            return group.isMuted();
        }
    }

    /**
     * @see {@link android.car.media.CarAudioManager#setVolumeGroupMute(int, int, boolean, int)}
     */
    @Override
    public void setVolumeGroupMute(int zoneId, int groupId, boolean mute, int flags) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
        requireDynamicRouting();
        requireVolumeGroupMuting();
        synchronized (mImplLock) {
            CarVolumeGroup group = getCarVolumeGroupLocked(zoneId, groupId);
            group.setMute(mute);
        }
        handleMuteChanged(zoneId, groupId, flags);
    }

    @Override
    public @NonNull List<AudioDeviceAttributes> getInputDevicesForZoneId(int zoneId) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireDynamicRouting();

        return getCarAudioZone(zoneId).getInputAudioDevices();
    }

    void setAudioEnabled(boolean isAudioEnabled) {
        Slogf.d(TAG, "Setting isAudioEnabled to %b", isAudioEnabled);

        mFocusHandler.setRestrictFocus(/* isFocusRestricted= */ !isAudioEnabled);
        if (mUseCarVolumeGroupMuting) {
            mCarVolumeGroupMuting.setRestrictMuting(/* isMutingRestricted= */ !isAudioEnabled);
        }
        // TODO(b/176258537) if not using group volume, then set master mute accordingly
    }

    private void enforcePermission(String permissionName) {
        if (mContext.checkCallingOrSelfPermission(permissionName)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("requires permission " + permissionName);
        }
    }

    private void requireDynamicRouting() {
        Preconditions.checkState(mUseDynamicRouting, "Dynamic routing is required");
    }

    private void requireVolumeGroupMuting() {
        Preconditions.checkState(mUseCarVolumeGroupMuting,
                "Car Volume Group Muting is required");
    }

    @GuardedBy("mImplLock")
    private void requiredOccupantZoneMappingDisabledLocked() {
        if (isOccupantZoneMappingAvailableLocked()) {
            throw new IllegalStateException(
                    "UID based routing is not supported while using occupant zone mapping");
        }
    }

    @AudioContext int getSuggestedAudioContextForPrimaryZone() {
        int zoneId = PRIMARY_AUDIO_ZONE;
        CarVolume carVolume;
        synchronized (mImplLock) {
            carVolume = mCarVolume;
        }
        return carVolume.getSuggestedAudioContextAndSaveIfFound(
                getAllActiveAttributesForPrimaryZone(), getCallStateForZone(zoneId),
                getActiveHalAudioAttributesForZone(zoneId));
    }

    private List<AudioAttributes> getActiveHalAudioAttributesForZone(int zoneId) {
        if (mHalAudioFocus == null) {
            return new ArrayList<>(0);
        }
        return mHalAudioFocus.getActiveAudioAttributesForZone(zoneId);
    }

    /**
     * Gets volume group by a given legacy stream type
     * @param streamType Legacy stream type such as {@link AudioManager#STREAM_MUSIC}
     * @return volume group id mapped from stream type
     */
    private int getVolumeGroupIdForStreamType(int streamType) {
        int groupId = INVALID_VOLUME_GROUP_ID;
        for (int i = 0; i < CarAudioDynamicRouting.STREAM_TYPES.length; i++) {
            if (streamType == CarAudioDynamicRouting.STREAM_TYPES[i]) {
                groupId = i;
                break;
            }
        }
        return groupId;
    }

    private void handleOccupantZoneUserChanged() {
        int driverUserId = mOccupantZoneService.getDriverUserId();
        synchronized (mImplLock) {
            if (!isOccupantZoneMappingAvailableLocked()) {
                adjustZonesToUserIdLocked(driverUserId);
                return;
            }
            int occupantZoneForDriver =  getOccupantZoneIdForDriver();
            Set<Integer> assignedZones = new HashSet<Integer>();
            for (int index = 0; index < mAudioZoneIdToOccupantZoneIdMapping.size(); index++) {
                int audioZoneId = mAudioZoneIdToOccupantZoneIdMapping.keyAt(index);
                int occupantZoneId = mAudioZoneIdToOccupantZoneIdMapping.get(audioZoneId);
                assignedZones.add(audioZoneId);
                updateUserForOccupantZoneLocked(occupantZoneId, audioZoneId, driverUserId,
                        occupantZoneForDriver);
            }

            assignMissingZonesToDriverLocked(driverUserId, assignedZones);
        }
        restoreVolumeGroupMuteState();
    }

    private void restoreVolumeGroupMuteState() {
        if (!mUseCarVolumeGroupMuting) {
            return;
        }
        mCarVolumeGroupMuting.carMuteChanged();
    }

    @GuardedBy("mImplLock")
    private void assignMissingZonesToDriverLocked(@UserIdInt int driverUserId,
            Set<Integer> assignedZones) {
        for (int i = 0; i < mCarAudioZones.size(); i++) {
            CarAudioZone zone = mCarAudioZones.valueAt(i);
            if (assignedZones.contains(zone.getId())) {
                continue;
            }
            assignUserIdToAudioZoneLocked(zone, driverUserId);
        }
    }

    @GuardedBy("mImplLock")
    private void adjustZonesToUserIdLocked(@UserIdInt int userId) {
        for (int i = 0; i < mCarAudioZones.size(); i++) {
            CarAudioZone zone = mCarAudioZones.valueAt(i);
            assignUserIdToAudioZoneLocked(zone, userId);
        }
    }

    @GuardedBy("mImplLock")
    private void assignUserIdToAudioZoneLocked(CarAudioZone zone, @UserIdInt int userId) {
        if (userId == getUserIdForZoneLocked(zone.getId())) {
            Slogf.d(TAG, "assignUserIdToAudioZone userId(%d) already assigned to audioZoneId(%d)",
                    userId, zone.getId());
            return;
        }
        Slogf.d(TAG, "assignUserIdToAudioZone assigning userId(%d) to audioZoneId(%d)",
                userId, zone.getId());
        zone.updateVolumeGroupsSettingsForUser(userId);
        mFocusHandler.updateUserForZoneId(zone.getId(), userId);
        setUserIdForAudioZoneLocked(userId, zone.getId());
    }

    @GuardedBy("mImplLock")
    private boolean isOccupantZoneMappingAvailableLocked() {
        return mAudioZoneIdToOccupantZoneIdMapping.size() > 0;
    }

    @GuardedBy("mImplLock")
    private void updateUserForOccupantZoneLocked(int occupantZoneId, int audioZoneId,
            @UserIdInt int driverUserId, int occupantZoneForDriver) {
        CarAudioZone audioZone = getCarAudioZoneLocked(audioZoneId);
        int userId = mOccupantZoneService.getUserForOccupant(occupantZoneId);
        int prevUserId = getUserIdForZoneLocked(audioZoneId);

        if (userId == prevUserId) {
            Slogf.d(TAG, "updateUserForOccupantZone userId(%d) already assigned to audioZoneId(%d)",
                    userId, audioZoneId);
            return;
        }
        Slogf.d(TAG, "updateUserForOccupantZone assigning userId(%d) to audioZoneId(%d)",
                userId, audioZoneId);
        // If the user has changed, be sure to remove from current routing
        // This would be true even if the new user is UserManagerHelper.USER_NULL,
        // as that indicates the user has logged out.
        removeUserIdDeviceAffinitiesLocked(prevUserId);

        if (userId == UserManagerHelper.USER_NULL) {
            // Reset zone back to driver user id
            resetZoneToDefaultUser(audioZone, driverUserId);
            setUserIdForAudioZoneLocked(userId, audioZoneId);
            return;
        }

        // Only set user id device affinities for driver when it is the driver's occupant zone
        if (userId != driverUserId || occupantZoneId == occupantZoneForDriver) {
            setUserIdDeviceAffinitiesLocked(audioZone, userId, audioZoneId);
        }
        audioZone.updateVolumeGroupsSettingsForUser(userId);
        mFocusHandler.updateUserForZoneId(audioZoneId, userId);
        setUserIdForAudioZoneLocked(userId, audioZoneId);
    }

    private int getOccupantZoneIdForDriver() {
        List<CarOccupantZoneManager.OccupantZoneInfo> occupantZoneInfos =
                mOccupantZoneManager.getAllOccupantZones();
        for (CarOccupantZoneManager.OccupantZoneInfo info: occupantZoneInfos) {
            if (info.occupantType == CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER) {
                return info.zoneId;
            }
        }
        return CarOccupantZoneManager.OccupantZoneInfo.INVALID_ZONE_ID;
    }

    @GuardedBy("mImplLock")
    private void setUserIdDeviceAffinitiesLocked(CarAudioZone zone, @UserIdInt int userId,
            int audioZoneId) {
        if (!mAudioPolicy.setUserIdDeviceAffinity(userId, zone.getAudioDeviceInfos())) {
            throw new IllegalStateException(String.format(
                    "setUserIdDeviceAffinity for userId %d in zone %d Failed,"
                            + " could not set audio routing.",
                    userId, audioZoneId));
        }
    }

    private void resetZoneToDefaultUser(CarAudioZone zone, @UserIdInt int driverUserId) {
        resetCarZonesAudioFocus(zone.getId(), driverUserId);
        zone.updateVolumeGroupsSettingsForUser(driverUserId);
    }

    private void resetCarZonesAudioFocus(int audioZoneId, @UserIdInt int driverUserId) {
        mFocusHandler.updateUserForZoneId(audioZoneId, driverUserId);
    }

    @GuardedBy("mImplLock")
    private void removeUserIdDeviceAffinitiesLocked(@UserIdInt int userId) {
        Slogf.d(TAG, "removeUserIdDeviceAffinities(%d) Succeeded", userId);
        if (userId == UserManagerHelper.USER_NULL) {
            return;
        }
        if (!mAudioPolicy.removeUserIdDeviceAffinity(userId)) {
            Slogf.e(TAG, "removeUserIdDeviceAffinities(%d) Failed", userId);
            return;
        }
    }

    @GuardedBy("mImplLock")
    private @UserIdInt int getUserIdForZoneLocked(int audioZoneId) {
        return mAudioZoneIdToUserIdMapping.get(audioZoneId, UserManagerHelper.USER_NULL);
    }

    @GuardedBy("mImplLock")
    private void setUserIdForAudioZoneLocked(@UserIdInt int userId, int audioZoneId) {
        mAudioZoneIdToUserIdMapping.put(audioZoneId, userId);
    }

    @GuardedBy("mImplLock")
    private AudioControlWrapper getAudioControlWrapperLocked() {
        if (mAudioControlWrapper == null) {
            mAudioControlWrapper = AudioControlFactory.newAudioControl();
            mAudioControlWrapper.linkToDeath(this::audioControlDied);
        }
        return mAudioControlWrapper;
    }

    private void resetHalAudioFocus() {
        if (mHalAudioFocus != null) {
            mHalAudioFocus.reset();
            mHalAudioFocus.registerFocusListener();
        }
    }

    private void resetHalAudioGain() {
        if (mCarAudioGainMonitor != null) {
            mCarAudioGainMonitor.reset();
            mCarAudioGainMonitor.registerAudioGainListener(mHalAudioGainCallback);
        }
    }

    private void handleAudioDeviceGainsChangedLocked(
            List<Integer> halReasons, List<CarAudioGainConfigInfo> gains) {
        mCarAudioGainMonitor.handleAudioDeviceGainsChanged(halReasons, gains);
    }

    private void audioControlDied() {
        resetHalAudioFocus();
        resetHalAudioGain();
    }

    boolean isAudioZoneIdValid(int zoneId) {
        synchronized (mImplLock) {
            return mCarAudioZones.contains(zoneId);
        }
    }

    private CarAudioZone getCarAudioZone(int zoneId) {
        synchronized (mImplLock) {
            return getCarAudioZoneLocked(zoneId);
        }
    }

    @GuardedBy("mImplLock")
    private CarAudioZone getCarAudioZoneLocked(int zoneId) {
        checkAudioZoneIdLocked(zoneId);
        return mCarAudioZones.get(zoneId);
    }

    @GuardedBy("mImplLock")
    private void checkAudioZoneIdLocked(int zoneId) {
        Preconditions.checkArgument(mCarAudioZones.contains(zoneId),
                "Invalid audio zone Id " + zoneId);
    }

    int getVolumeGroupIdForAudioContext(int zoneId, int suggestedContext) {
        synchronized (mImplLock) {
            return getVolumeGroupIdForAudioContextLocked(zoneId, suggestedContext);
        }
    }

    /**
     * Resets the last selected volume context.
     */
    public void resetSelectedVolumeContext() {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
        synchronized (mImplLock) {
            mCarVolume.resetSelectedVolumeContext();
            mCarAudioPlaybackCallback.resetStillActiveContexts();
        }
    }

    @VisibleForTesting
    CarAudioContext getCarAudioContext() {
        synchronized (mImplLock) {
            return mCarAudioContext;
        }
    }

    @VisibleForTesting
    void requestAudioFocusForTest(AudioFocusInfo audioFocusInfo, int audioFocusResult) {
        mFocusHandler.onAudioFocusRequest(audioFocusInfo, audioFocusResult);
    }

    private class CarAudioOccupantConfigChangeListener implements OccupantZoneConfigChangeListener {
        @Override
        public void onOccupantZoneConfigChanged(int flags) {
            Slogf.d(TAG, "onOccupantZoneConfigChanged(%d)", flags);
            if (((flags & CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_USER)
                    == CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_USER)
                    || ((flags & CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_DISPLAY)
                    == CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_DISPLAY)) {
                handleOccupantZoneUserChanged();
            }
        }
    }

    private List<AudioAttributes> getAllActiveAttributesForPrimaryZone() {
        synchronized (mImplLock) {
            return mCarAudioPlaybackCallback.getAllActiveAudioAttributesForPrimaryZone();
        }
    }

    List<CarVolumeGroupInfo> getMutedVolumeGroups(int zoneId) {
        List<CarVolumeGroupInfo> mutedGroups = new ArrayList<>();

        if (!mUseCarVolumeGroupMuting || !isAudioZoneIdValid(zoneId)) {
            return mutedGroups;
        }

        synchronized (mImplLock) {
            int groupCount = getCarAudioZoneLocked(zoneId).getVolumeGroupCount();
            for (int groupId = 0; groupId < groupCount; groupId++) {
                CarVolumeGroup group = getCarVolumeGroupLocked(zoneId, groupId);
                if (!group.isMuted()) {
                    continue;
                }

                mutedGroups.add(group.getCarVolumeGroupInfo());
            }
        }

        return mutedGroups;
    }

    List<AudioAttributes> getActiveAudioAttributesForZone(int zoneId) {
        List<AudioAttributes> activeAudioAttributes = new ArrayList<>();
        activeAudioAttributes.addAll(getActiveAttributesFromPlaybackConfigurations(zoneId));
        activeAudioAttributes.addAll(getActiveHalAudioAttributesForZone(zoneId));

        return activeAudioAttributes;
    }

    int getVolumeGroupIdForAudioAttribute(int audioZoneId, AudioAttributes attributes) {
        Objects.requireNonNull(attributes, "Audio attributes can not be null");
        synchronized (mImplLock) {
            checkAudioZoneIdLocked(audioZoneId);
            return getVolumeGroupIdForAudioAttributeLocked(audioZoneId, attributes);
        }
    }

    static final class SystemClockWrapper {
        public long uptimeMillis() {
            return SystemClock.uptimeMillis();
        }
    }
}
