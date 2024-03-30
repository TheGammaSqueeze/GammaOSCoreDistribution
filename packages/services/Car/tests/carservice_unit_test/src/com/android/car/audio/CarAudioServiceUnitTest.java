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

package com.android.car.audio;

import static android.car.Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS;
import static android.car.Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME;
import static android.car.media.CarAudioManager.INVALID_AUDIO_ZONE;
import static android.car.media.CarAudioManager.INVALID_VOLUME_GROUP_ID;
import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;
import static android.car.test.mocks.AndroidMockitoHelper.mockContextCheckCallingOrSelfPermission;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.media.AudioAttributes.USAGE_ALARM;
import static android.media.AudioAttributes.USAGE_ANNOUNCEMENT;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION;
import static android.media.AudioAttributes.USAGE_ASSISTANT;
import static android.media.AudioAttributes.USAGE_CALL_ASSISTANT;
import static android.media.AudioAttributes.USAGE_EMERGENCY;
import static android.media.AudioAttributes.USAGE_GAME;
import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioAttributes.USAGE_NOTIFICATION;
import static android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT;
import static android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE;
import static android.media.AudioAttributes.USAGE_SAFETY;
import static android.media.AudioAttributes.USAGE_UNKNOWN;
import static android.media.AudioAttributes.USAGE_VEHICLE_STATUS;
import static android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION;
import static android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING;
import static android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC;
import static android.media.AudioDeviceInfo.TYPE_FM_TUNER;
import static android.media.AudioManager.AUDIOFOCUS_GAIN;
import static android.media.AudioManager.AUDIOFOCUS_LOSS;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
import static android.media.AudioManager.SUCCESS;
import static android.os.Build.VERSION.SDK_INT;

import static com.android.car.R.bool.audioPersistMasterMuteState;
import static com.android.car.R.bool.audioUseCarVolumeGroupMuting;
import static com.android.car.R.bool.audioUseDynamicRouting;
import static com.android.car.R.bool.audioUseHalDuckingSignals;
import static com.android.car.R.integer.audioVolumeAdjustmentContextsVersion;
import static com.android.car.R.integer.audioVolumeKeyEventTimeoutMs;
import static com.android.car.audio.CarAudioService.CAR_DEFAULT_AUDIO_ATTRIBUTE;
import static com.android.car.audio.GainBuilder.DEFAULT_GAIN;
import static com.android.car.audio.GainBuilder.MAX_GAIN;
import static com.android.car.audio.GainBuilder.STEP_SIZE;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.builtin.media.AudioManagerHelper;
import android.car.builtin.media.AudioManagerHelper.AudioPatchInfo;
import android.car.media.CarAudioPatchHandle;
import android.car.media.CarVolumeGroupInfo;
import android.car.settings.CarSettings;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.automotive.audiocontrol.IAudioControl;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusInfo;
import android.media.AudioGain;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.media.IAudioService;
import android.media.audiopolicy.AudioPolicy;
import android.net.Uri;
import android.os.IBinder;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.android.car.CarLocalServices;
import com.android.car.CarOccupantZoneService;
import com.android.car.R;
import com.android.car.audio.hal.AudioControlFactory;
import com.android.car.audio.hal.AudioControlWrapper;
import com.android.car.audio.hal.AudioControlWrapperAidl;
import com.android.car.audio.hal.HalFocusListener;
import com.android.car.oem.CarOemProxyService;
import com.android.car.test.utils.TemporaryFile;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.InputStream;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public final class CarAudioServiceUnitTest extends AbstractExtendedMockitoTestCase {
    private static final String TAG = CarAudioServiceUnitTest.class.getSimpleName();
    private static final int VOLUME_KEY_EVENT_TIMEOUT_MS = 3000;
    private static final int AUDIO_CONTEXT_PRIORITY_LIST_VERSION_ONE = 1;
    private static final int AUDIO_CONTEXT_PRIORITY_LIST_VERSION_TWO = 2;
    private static final String MEDIA_TEST_DEVICE = "media_bus_device";
    private static final String NAVIGATION_TEST_DEVICE = "navigation_bus_device";
    private static final String CALL_TEST_DEVICE = "call_bus_device";
    private static final String NOTIFICATION_TEST_DEVICE = "notification_bus_device";
    private static final String VOICE_TEST_DEVICE = "voice_bus_device";
    private static final String RING_TEST_DEVICE = "ring_bus_device";
    private static final String ALARM_TEST_DEVICE = "alarm_bus_device";
    private static final String SYSTEM_BUS_DEVICE = "system_bus_device";
    private static final String SECONDARY_TEST_DEVICE = "secondary_zone_bus";
    private static final String PRIMARY_ZONE_MICROPHONE_ADDRESS = "Built-In Mic";
    private static final String PRIMARY_ZONE_FM_TUNER_ADDRESS = "FM Tuner";
    // From the car audio configuration file in /res/raw/car_audio_configuration.xml
    private static final int SECONDARY_ZONE_ID = 1;
    private static final int OUT_OF_RANGE_ZONE = SECONDARY_ZONE_ID + 1;
    private static final int PRIMARY_ZONE_VOLUME_GROUP_COUNT = 4;
    private static final int SECONDARY_ZONE_VOLUME_GROUP_COUNT = 1;
    private static final int SECONDARY_ZONE_VOLUME_GROUP_ID = SECONDARY_ZONE_VOLUME_GROUP_COUNT - 1;
    private static final int TEST_PRIMARY_GROUP = 0;
    private static final int TEST_SECONDARY_GROUP = 1;
    private static final int TEST_PRIMARY_GROUP_INDEX = 0;
    private static final int TEST_FLAGS = 0;

    private static final String PROPERTY_RO_ENABLE_AUDIO_PATCH =
            "ro.android.car.audio.enableaudiopatch";

    private static final int MEDIA_APP_UID = 1086753;
    private static final String MEDIA_CLIENT_ID = "media-client-id";
    private static final String MEDIA_PACKAGE_NAME = "com.android.car.audio";
    private static final int MEDIA_EMPTY_FLAG = 0;
    private static final String REGISTRATION_ID = "meh";
    private static final int MEDIA_VOLUME_GROUP_ID = 0;
    private static final int NAVIGATION_VOLUME_GROUP_ID = 1;
    private static final int INVALID_USAGE = -1;
    private static final int INVALID_AUDIO_FEATURE = -1;

    private static final CarVolumeGroupInfo TEST_PRIMARY_VOLUME_INFO =
            new CarVolumeGroupInfo.Builder("group id " + TEST_PRIMARY_GROUP, PRIMARY_AUDIO_ZONE,
                    TEST_PRIMARY_GROUP).setMuted(true).setMinVolumeGainIndex(0)
                    .setMaxVolumeGainIndex(MAX_GAIN / STEP_SIZE)
                    .setVolumeGainIndex(DEFAULT_GAIN / STEP_SIZE).build();

    private static final CarVolumeGroupInfo TEST_SECONDARY_VOLUME_INFO =
            new CarVolumeGroupInfo.Builder("group id " + TEST_SECONDARY_GROUP, PRIMARY_AUDIO_ZONE,
                    TEST_SECONDARY_GROUP).setMuted(true).setMinVolumeGainIndex(0)
                    .setMaxVolumeGainIndex(MAX_GAIN / STEP_SIZE)
                    .setVolumeGainIndex(DEFAULT_GAIN / STEP_SIZE).build();

    private CarAudioService mCarAudioService;
    @Mock
    private Context mMockContext;
    @Mock
    private TelephonyManager mMockTelephonyManager;
    @Mock
    private AudioManager mAudioManager;
    @Mock
    private Resources mMockResources;
    @Mock
    private ContentResolver mMockContentResolver;
    @Mock
    IBinder mBinder;
    @Mock
    IAudioControl mAudioControl;
    @Mock
    private PackageManager mMockPackageManager;
    @Mock
    private CarOccupantZoneService mMockOccupantZoneService;
    @Mock
    private CarOemProxyService mMockCarOemProxyService;
    @Mock
    private IAudioService mMockAudioService;
    @Mock
    private Uri mNavSettingUri;
    @Mock
    private CarVolumeCallbackHandler mCarVolumeCallbackHandler;
    @Mock
    private AudioControlWrapperAidl mAudioControlWrapperAidl;

    private boolean mPersistMasterMute = true;
    private boolean mUseDynamicRouting = true;
    private boolean mUseHalAudioDucking = true;
    private boolean mUserCarVolumeGroupMuting = true;

    private TemporaryFile mTemporaryAudioConfigurationFile;
    private TemporaryFile mTemporaryAudioConfigurationWithoutZoneMappingFile;
    private Context mContext;
    private AudioDeviceInfo mTunerDevice;
    private AudioDeviceInfo mMediaOutputDevice;

    public CarAudioServiceUnitTest() {
        super(CarAudioService.TAG);
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session
                .spyStatic(AudioManagerHelper.class)
                .spyStatic(AudioControlWrapperAidl.class)
                .spyStatic(AudioControlFactory.class)
                .spyStatic(SystemProperties.class)
                .spyStatic(ServiceManager.class);
    }

    @Before
    public void setUp() throws Exception {
        mContext = ApplicationProvider.getApplicationContext();

        try (InputStream configurationStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration)) {
            mTemporaryAudioConfigurationFile = new TemporaryFile("xml");
            mTemporaryAudioConfigurationFile.write(new String(configurationStream.readAllBytes()));
            Log.i(TAG, "Temporary Car Audio Configuration File Location: "
                    + mTemporaryAudioConfigurationFile.getPath());
        }

        try (InputStream configurationStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_without_zone_mapping)) {
            mTemporaryAudioConfigurationWithoutZoneMappingFile = new TemporaryFile("xml");
            mTemporaryAudioConfigurationWithoutZoneMappingFile
                    .write(new String(configurationStream.readAllBytes()));
            Log.i(TAG, "Temporary Car Audio Configuration without Zone mapping File Location: "
                    + mTemporaryAudioConfigurationWithoutZoneMappingFile.getPath());
        }

        mockGrantCarControlAudioSettingsPermission();

        setupAudioControlHAL();
        setupService();

        when(Settings.Secure.getUriFor(
                CarSettings.Secure.KEY_AUDIO_FOCUS_NAVIGATION_REJECTED_DURING_CALL))
                .thenReturn(mNavSettingUri);
    }

    @After
    public void tearDown() throws Exception {
        mTemporaryAudioConfigurationFile.close();
        mTemporaryAudioConfigurationWithoutZoneMappingFile.close();
        CarLocalServices.removeServiceForTest(CarOemProxyService.class);
        CarLocalServices.removeServiceForTest(CarOccupantZoneService.class);
    }

    private void setupAudioControlHAL() {
        when(mBinder.queryLocalInterface(anyString())).thenReturn(mAudioControl);
        doReturn(mBinder).when(AudioControlWrapperAidl::getService);
        when(mAudioControlWrapperAidl.supportsFeature(
                AudioControlWrapper.AUDIOCONTROL_FEATURE_AUDIO_DUCKING)).thenReturn(true);
        when(mAudioControlWrapperAidl.supportsFeature(
                AudioControlWrapper.AUDIOCONTROL_FEATURE_AUDIO_FOCUS)).thenReturn(true);
        when(mAudioControlWrapperAidl.supportsFeature(
                AudioControlWrapper.AUDIOCONTROL_FEATURE_AUDIO_GAIN_CALLBACK)).thenReturn(true);
        when(mAudioControlWrapperAidl.supportsFeature(
                AudioControlWrapper.AUDIOCONTROL_FEATURE_AUDIO_GROUP_MUTING)).thenReturn(true);
        doReturn(mAudioControlWrapperAidl)
                .when(() -> AudioControlFactory.newAudioControl());
    }

    private void setupService() throws Exception {
        when(mMockContext.getSystemService(Context.TELEPHONY_SERVICE))
                .thenReturn(mMockTelephonyManager);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        doReturn(true)
                .when(() -> AudioManagerHelper
                        .setAudioDeviceGain(any(), any(), anyInt(), anyBoolean()));
        doReturn(true)
                .when(() -> SystemProperties.getBoolean(PROPERTY_RO_ENABLE_AUDIO_PATCH, false));

        CarLocalServices.removeServiceForTest(CarOccupantZoneService.class);
        CarLocalServices.addService(CarOccupantZoneService.class, mMockOccupantZoneService);

        CarLocalServices.removeServiceForTest(CarOemProxyService.class);
        CarLocalServices.addService(CarOemProxyService.class, mMockCarOemProxyService);

        setupAudioManager();

        setupResources();

        mCarAudioService =
                new CarAudioService(mMockContext,
                        mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                        mCarVolumeCallbackHandler);
    }

    private void setupAudioManager() throws Exception {
        AudioDeviceInfo[] outputDevices = generateOutputDeviceInfos();
        AudioDeviceInfo[] inputDevices = generateInputDeviceInfos();
        when(mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS))
                .thenReturn(outputDevices);
        when(mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS))
               .thenReturn(inputDevices);
        when(mMockContext.getSystemService(Context.AUDIO_SERVICE)).thenReturn(mAudioManager);

        when(mAudioManager.registerAudioPolicy(any())).thenAnswer(invocation -> {
            AudioPolicy policy = (AudioPolicy) invocation.getArguments()[0];
            policy.setRegistration(REGISTRATION_ID);
            return SUCCESS;
        });

        IBinder mockBinder = mock(IBinder.class);
        when(mockBinder.queryLocalInterface(any())).thenReturn(mMockAudioService);
        doReturn(mockBinder).when(() -> ServiceManager.getService(Context.AUDIO_SERVICE));
    }

    private void setupResources() {
        when(mMockContext.getContentResolver()).thenReturn(mMockContentResolver);
        when(mMockContext.createContextAsUser(any(), anyInt())).thenReturn(mMockContext);
        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mMockResources.getBoolean(audioUseDynamicRouting)).thenReturn(mUseDynamicRouting);
        when(mMockResources.getInteger(audioVolumeKeyEventTimeoutMs))
                .thenReturn(VOLUME_KEY_EVENT_TIMEOUT_MS);
        when(mMockResources.getBoolean(audioUseHalDuckingSignals)).thenReturn(mUseHalAudioDucking);
        when(mMockResources.getBoolean(audioUseCarVolumeGroupMuting))
                .thenReturn(mUserCarVolumeGroupMuting);
        when(mMockResources.getInteger(audioVolumeAdjustmentContextsVersion))
                .thenReturn(AUDIO_CONTEXT_PRIORITY_LIST_VERSION_TWO);
        when(mMockResources.getBoolean(audioPersistMasterMuteState)).thenReturn(mPersistMasterMute);
    }

    @Test
    public void constructor_withNullContext_fails() {
        NullPointerException thrown =
                assertThrows(NullPointerException.class, () -> new CarAudioService(null));

        assertWithMessage("Car Audio Service Construction")
                .that(thrown).hasMessageThat().contains("Context");
    }

    @Test
    public void constructor_withNullContextAndNullPath_fails() {
        NullPointerException thrown =
                assertThrows(NullPointerException.class,
                        () -> new CarAudioService(null, null, mCarVolumeCallbackHandler));

        assertWithMessage("Car Audio Service Construction")
                .that(thrown).hasMessageThat().contains("Context");
    }

    @Test
    public void constructor_withInvalidVolumeConfiguration_fails() {
        when(mMockResources.getInteger(audioVolumeAdjustmentContextsVersion))
                .thenReturn(AUDIO_CONTEXT_PRIORITY_LIST_VERSION_ONE);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new CarAudioService(mMockContext));

        assertWithMessage("Car Audio Service Construction")
                .that(thrown).hasMessageThat()
                .contains("requires audioVolumeAdjustmentContextsVersion 2");
    }

    @Test
    public void getAudioZoneIds_withBaseConfiguration_returnAllTheZones() {
        mCarAudioService.init();

        assertWithMessage("Car Audio Service Zones")
                .that(mCarAudioService.getAudioZoneIds())
                .asList().containsExactly(PRIMARY_AUDIO_ZONE, SECONDARY_ZONE_ID);
    }

    @Test
    public void getVolumeGroupCount_onPrimaryZone_returnsAllGroups() {
        mCarAudioService.init();

        assertWithMessage("Primary zone car volume group count")
                .that(mCarAudioService.getVolumeGroupCount(PRIMARY_AUDIO_ZONE))
                .isEqualTo(PRIMARY_ZONE_VOLUME_GROUP_COUNT);
    }

    @Test
    public void getVolumeGroupCount_onPrimaryZone__withNonDynamicRouting_returnsAllGroups() {
        when(mMockResources.getBoolean(audioUseDynamicRouting))
                .thenReturn(/* useDynamicRouting= */ false);
        CarAudioService nonDynamicAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonDynamicAudioService.init();

        assertWithMessage("Non dynamic routing primary zone car volume group count")
                .that(nonDynamicAudioService.getVolumeGroupCount(PRIMARY_AUDIO_ZONE))
                .isEqualTo(CarAudioDynamicRouting.STREAM_TYPES.length);
    }

    @Test
    public void getVolumeGroupIdForUsage_forMusicUsage() {
        mCarAudioService.init();

        assertWithMessage("Primary zone's media car volume group id")
                .that(mCarAudioService.getVolumeGroupIdForUsage(PRIMARY_AUDIO_ZONE, USAGE_MEDIA))
                .isEqualTo(MEDIA_VOLUME_GROUP_ID);
    }

    @Test
    public void getVolumeGroupIdForUsage_withNonDynamicRouting_forMusicUsage() {
        when(mMockResources.getBoolean(audioUseDynamicRouting))
                .thenReturn(/* useDynamicRouting= */ false);
        CarAudioService nonDynamicAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonDynamicAudioService.init();

        assertWithMessage("Non dynamic routing primary zone's media car volume group id")
                .that(nonDynamicAudioService.getVolumeGroupIdForUsage(PRIMARY_AUDIO_ZONE,
                        USAGE_MEDIA)).isEqualTo(MEDIA_VOLUME_GROUP_ID);
    }

    @Test
    public void getVolumeGroupIdForUsage_forNavigationUsage() {
        mCarAudioService.init();

        assertWithMessage("Primary zone's navigation car volume group id")
                .that(mCarAudioService.getVolumeGroupIdForUsage(PRIMARY_AUDIO_ZONE,
                        USAGE_ASSISTANCE_NAVIGATION_GUIDANCE))
                .isEqualTo(NAVIGATION_VOLUME_GROUP_ID);
    }

    @Test
    public void getVolumeGroupIdForUsage_withNonDynamicRouting_forNavigationUsage() {
        when(mMockResources.getBoolean(audioUseDynamicRouting))
                .thenReturn(/* useDynamicRouting= */ false);
        CarAudioService nonDynamicAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonDynamicAudioService.init();

        assertWithMessage("Non dynamic routing primary zone's navigation car volume group id")
                .that(nonDynamicAudioService.getVolumeGroupIdForUsage(PRIMARY_AUDIO_ZONE,
                        USAGE_ASSISTANCE_NAVIGATION_GUIDANCE))
                .isEqualTo(INVALID_VOLUME_GROUP_ID);
    }

    @Test
    public void getVolumeGroupIdForUsage_forInvalidUsage_returnsInvalidGroupId() {
        mCarAudioService.init();

        assertWithMessage("Primary zone's invalid car volume group id")
                .that(mCarAudioService.getVolumeGroupIdForUsage(PRIMARY_AUDIO_ZONE, INVALID_USAGE))
                .isEqualTo(INVALID_VOLUME_GROUP_ID);
    }

    @Test
    public void
            getVolumeGroupIdForUsage_forInvalidUsage_withNonDynamicRouting_returnsInvalidGroupId() {
        when(mMockResources.getBoolean(audioUseDynamicRouting))
                .thenReturn(/* useDynamicRouting= */ false);
        CarAudioService nonDynamicAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonDynamicAudioService.init();

        assertWithMessage("Non dynamic routing primary zone's invalid car volume group id")
                .that(nonDynamicAudioService.getVolumeGroupIdForUsage(PRIMARY_AUDIO_ZONE,
                        INVALID_USAGE)).isEqualTo(INVALID_VOLUME_GROUP_ID);
    }

    @Test
    public void getVolumeGroupIdForUsage_forUnknownUsage_returnsMediaGroupId() {
        mCarAudioService.init();

        assertWithMessage("Primary zone's unknown car volume group id")
                .that(mCarAudioService.getVolumeGroupIdForUsage(PRIMARY_AUDIO_ZONE, USAGE_UNKNOWN))
                .isEqualTo(MEDIA_VOLUME_GROUP_ID);
    }

    @Test
    public void getVolumeGroupIdForUsage_forVirtualUsage_returnsInvalidGroupId() {
        mCarAudioService.init();

        assertWithMessage("Primary zone's virtual car volume group id")
                .that(mCarAudioService.getVolumeGroupIdForUsage(PRIMARY_AUDIO_ZONE,
                        AudioManagerHelper.getUsageVirtualSource()))
                .isEqualTo(INVALID_VOLUME_GROUP_ID);
    }

    @Test
    public void getVolumeGroupCount_onSecondaryZone_returnsAllGroups() {
        mCarAudioService.init();

        assertWithMessage("Secondary Zone car volume group count")
                .that(mCarAudioService.getVolumeGroupCount(SECONDARY_ZONE_ID))
                .isEqualTo(SECONDARY_ZONE_VOLUME_GROUP_COUNT);
    }

    @Test
    public void getUsagesForVolumeGroupId_forMusicContext() {
        mCarAudioService.init();


        assertWithMessage("Primary zone's music car volume group id usages")
                .that(mCarAudioService.getUsagesForVolumeGroupId(PRIMARY_AUDIO_ZONE,
                        MEDIA_VOLUME_GROUP_ID)).asList()
                .containsExactly(USAGE_UNKNOWN, USAGE_GAME, USAGE_MEDIA, USAGE_ANNOUNCEMENT,
                        USAGE_NOTIFICATION, USAGE_NOTIFICATION_EVENT);
    }

    @Test
    public void getUsagesForVolumeGroupId_forSystemContext() {
        mCarAudioService.init();
        int systemVolumeGroup =
                mCarAudioService.getVolumeGroupIdForUsage(PRIMARY_AUDIO_ZONE, USAGE_EMERGENCY);

        assertWithMessage("Primary zone's system car volume group id usages")
                .that(mCarAudioService.getUsagesForVolumeGroupId(PRIMARY_AUDIO_ZONE,
                        systemVolumeGroup)).asList().containsExactly(USAGE_ALARM, USAGE_EMERGENCY,
                        USAGE_SAFETY, USAGE_VEHICLE_STATUS, USAGE_ASSISTANCE_SONIFICATION);
    }

    @Test
    public void getUsagesForVolumeGroupId_onSecondaryZone_forSingleVolumeGroupId_returnAllUsages() {
        mCarAudioService.init();

        assertWithMessage("Secondary Zone's car volume group id usages")
                .that(mCarAudioService.getUsagesForVolumeGroupId(SECONDARY_ZONE_ID,
                        SECONDARY_ZONE_VOLUME_GROUP_ID))
                .asList().containsExactly(USAGE_UNKNOWN, USAGE_MEDIA,
                        USAGE_VOICE_COMMUNICATION, USAGE_VOICE_COMMUNICATION_SIGNALLING,
                        USAGE_ALARM, USAGE_NOTIFICATION, USAGE_NOTIFICATION_RINGTONE,
                        USAGE_NOTIFICATION_EVENT, USAGE_ASSISTANCE_ACCESSIBILITY,
                        USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, USAGE_ASSISTANCE_SONIFICATION,
                        USAGE_GAME, USAGE_ASSISTANT, USAGE_CALL_ASSISTANT, USAGE_EMERGENCY,
                        USAGE_ANNOUNCEMENT, USAGE_SAFETY, USAGE_VEHICLE_STATUS);
    }

    @Test
    public void createAudioPatch_onMediaOutputDevice_failsForConfigurationMissing() {
        mCarAudioService.init();

        doReturn(false)
                .when(() -> SystemProperties.getBoolean(PROPERTY_RO_ENABLE_AUDIO_PATCH, false));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> mCarAudioService
                        .createAudioPatch(PRIMARY_ZONE_FM_TUNER_ADDRESS,
                                USAGE_MEDIA, DEFAULT_GAIN));

        assertWithMessage("FM and Media Audio Patch Exception")
                .that(thrown).hasMessageThat().contains("Audio Patch APIs not enabled");
    }

    @Test
    public void createAudioPatch_onMediaOutputDevice_failsForMissingPermission() {
        mCarAudioService.init();

        mockDenyCarControlAudioSettingsPermission();

        SecurityException thrown = assertThrows(SecurityException.class,
                () -> mCarAudioService
                        .createAudioPatch(PRIMARY_ZONE_FM_TUNER_ADDRESS,
                                USAGE_MEDIA, DEFAULT_GAIN));

        assertWithMessage("FM and Media Audio Patch Permission Exception")
                .that(thrown).hasMessageThat().contains(PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
    }

    @Test
    public void createAudioPatch_onMediaOutputDevice_succeeds() {
        mCarAudioService.init();

        mockGrantCarControlAudioSettingsPermission();
        doReturn(false)
                .when(() -> SystemProperties.getBoolean(PROPERTY_RO_ENABLE_AUDIO_PATCH, true));
        doReturn(new AudioPatchInfo(PRIMARY_ZONE_FM_TUNER_ADDRESS, MEDIA_TEST_DEVICE, 0))
                .when(() -> AudioManagerHelper
                        .createAudioPatch(mTunerDevice, mMediaOutputDevice, DEFAULT_GAIN));

        CarAudioPatchHandle audioPatch = mCarAudioService
                .createAudioPatch(PRIMARY_ZONE_FM_TUNER_ADDRESS, USAGE_MEDIA, DEFAULT_GAIN);

        assertWithMessage("Audio Patch Sink Address")
                .that(audioPatch.getSinkAddress()).isEqualTo(MEDIA_TEST_DEVICE);
        assertWithMessage("Audio Patch Source Address")
                .that(audioPatch.getSourceAddress()).isEqualTo(PRIMARY_ZONE_FM_TUNER_ADDRESS);
        assertWithMessage("Audio Patch Handle")
                .that(audioPatch.getHandleId()).isEqualTo(0);
    }

    @Test
    public void releaseAudioPatch_failsForConfigurationMissing() {
        mCarAudioService.init();

        doReturn(false)
                .when(() -> SystemProperties.getBoolean(PROPERTY_RO_ENABLE_AUDIO_PATCH, false));
        CarAudioPatchHandle carAudioPatchHandle =
                new CarAudioPatchHandle(0, PRIMARY_ZONE_FM_TUNER_ADDRESS, MEDIA_TEST_DEVICE);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> mCarAudioService.releaseAudioPatch(carAudioPatchHandle));

        assertWithMessage("Release FM and Media Audio Patch Exception")
                .that(thrown).hasMessageThat().contains("Audio Patch APIs not enabled");
    }

    @Test
    public void releaseAudioPatch_failsForMissingPermission() {
        mCarAudioService.init();

        mockDenyCarControlAudioSettingsPermission();
        CarAudioPatchHandle carAudioPatchHandle =
                new CarAudioPatchHandle(0, PRIMARY_ZONE_FM_TUNER_ADDRESS, MEDIA_TEST_DEVICE);

        SecurityException thrown = assertThrows(SecurityException.class,
                () -> mCarAudioService.releaseAudioPatch(carAudioPatchHandle));

        assertWithMessage("FM and Media Audio Patch Permission Exception")
                .that(thrown).hasMessageThat().contains(PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
    }

    @Test
    public void releaseAudioPatch_failsForNullPatch() {
        mCarAudioService.init();

        assertThrows(NullPointerException.class,
                () -> mCarAudioService.releaseAudioPatch(null));
    }

    @Test
    public void setZoneIdForUid_withoutRoutingPermission_fails() {
        mCarAudioService.init();

        mockDenyCarControlAudioSettingsPermission();

        SecurityException thrown = assertThrows(SecurityException.class,
                () -> mCarAudioService.setZoneIdForUid(OUT_OF_RANGE_ZONE, MEDIA_APP_UID));

        assertWithMessage("Set Zone for UID Permission Exception")
                .that(thrown).hasMessageThat()
                .contains(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
    }

    @Test
    public void setZoneIdForUid_withoutDynamicRouting_fails() {
        when(mMockResources.getBoolean(audioUseDynamicRouting))
                .thenReturn(/* useDynamicRouting= */ false);
        CarAudioService nonDynamicAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonDynamicAudioService.init();

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> nonDynamicAudioService.setZoneIdForUid(PRIMARY_AUDIO_ZONE, MEDIA_APP_UID));

        assertWithMessage("Set Zone for UID Dynamic Configuration Exception")
                .that(thrown).hasMessageThat()
                .contains("Dynamic routing is required");
    }

    @Test
    public void setZoneIdForUid_withInvalidZone_fails() {
        mCarAudioService.init();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> mCarAudioService.setZoneIdForUid(INVALID_AUDIO_ZONE, MEDIA_APP_UID));

        assertWithMessage("Set Zone for UID Invalid Zone Exception")
                .that(thrown).hasMessageThat()
                .contains("Invalid audio zone Id " + INVALID_AUDIO_ZONE);
    }

    @Test
    public void setZoneIdForUid_withOutOfRangeZone_fails() {
        mCarAudioService.init();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> mCarAudioService.setZoneIdForUid(OUT_OF_RANGE_ZONE, MEDIA_APP_UID));

        assertWithMessage("Set Zone for UID Zone Out of Range Exception")
                .that(thrown).hasMessageThat()
                .contains("Invalid audio zone Id " + OUT_OF_RANGE_ZONE);
    }

    @Test
    public void setZoneIdForUid_withZoneAudioMapping_fails() {
        mCarAudioService.init();

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> mCarAudioService.setZoneIdForUid(PRIMARY_AUDIO_ZONE, MEDIA_APP_UID));

        assertWithMessage("Set Zone for UID With Audio Zone Mapping Exception")
                .that(thrown).hasMessageThat()
                .contains("UID based routing is not supported while using occupant zone mapping");
    }

    @Test
    public void setZoneIdForUid_withValidZone_succeeds() throws Exception {
        when(mMockAudioService.setUidDeviceAffinity(any(), anyInt(), any(), any()))
                .thenReturn(SUCCESS);
        CarAudioService noZoneMappingAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationWithoutZoneMappingFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        noZoneMappingAudioService.init();

        boolean results = noZoneMappingAudioService
                .setZoneIdForUid(SECONDARY_ZONE_ID, MEDIA_APP_UID);

        assertWithMessage("Set Zone for UID Status").that(results).isTrue();
    }

    @Test
    public void setZoneIdForUid_onDifferentZones_succeeds() throws Exception {
        when(mMockAudioService.setUidDeviceAffinity(any(), anyInt(), any(), any()))
                .thenReturn(SUCCESS);
        CarAudioService noZoneMappingAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationWithoutZoneMappingFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        noZoneMappingAudioService.init();

        noZoneMappingAudioService
                .setZoneIdForUid(SECONDARY_ZONE_ID, MEDIA_APP_UID);

        boolean results = noZoneMappingAudioService
                .setZoneIdForUid(PRIMARY_AUDIO_ZONE, MEDIA_APP_UID);

        assertWithMessage("Set Zone for UID For Different Zone")
                .that(results).isTrue();
    }

    @Test
    public void setZoneIdForUid_onDifferentZones_withAudioFocus_succeeds() throws Exception {
        when(mMockAudioService.setUidDeviceAffinity(any(), anyInt(), any(), any()))
                .thenReturn(SUCCESS);
        CarAudioService noZoneMappingAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationWithoutZoneMappingFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        noZoneMappingAudioService.init();
        AudioFocusInfo audioFocusInfo = createAudioFocusInfoForMedia();

        noZoneMappingAudioService
                .setZoneIdForUid(SECONDARY_ZONE_ID, MEDIA_APP_UID);

        noZoneMappingAudioService
                .requestAudioFocusForTest(audioFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        boolean results = noZoneMappingAudioService
                .setZoneIdForUid(PRIMARY_AUDIO_ZONE, MEDIA_APP_UID);

        assertWithMessage("Set Zone for UID For Different Zone with Audio Focus")
                .that(results).isTrue();
    }

    @Test
    public void getZoneIdForUid_withoutMappedUid_succeeds() throws Exception {
        when(mMockAudioService.setUidDeviceAffinity(any(), anyInt(), any(), any()))
                .thenReturn(SUCCESS);
        CarAudioService noZoneMappingAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationWithoutZoneMappingFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        noZoneMappingAudioService.init();

        int zoneId = noZoneMappingAudioService
                .getZoneIdForUid(MEDIA_APP_UID);

        assertWithMessage("Get Zone for Non Mapped UID")
                .that(zoneId).isEqualTo(PRIMARY_AUDIO_ZONE);
    }

    @Test
    public void getZoneIdForUid_succeeds() throws Exception {
        when(mMockAudioService.setUidDeviceAffinity(any(), anyInt(), any(), any()))
                .thenReturn(SUCCESS);
        CarAudioService noZoneMappingAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationWithoutZoneMappingFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        noZoneMappingAudioService.init();

        noZoneMappingAudioService
                .setZoneIdForUid(SECONDARY_ZONE_ID, MEDIA_APP_UID);

        int zoneId = noZoneMappingAudioService
                .getZoneIdForUid(MEDIA_APP_UID);

        assertWithMessage("Get Zone for UID Zone Id")
                .that(zoneId).isEqualTo(SECONDARY_ZONE_ID);
    }

    @Test
    public void getZoneIdForUid_afterSwitchingZones_succeeds() throws Exception {
        when(mMockAudioService.setUidDeviceAffinity(any(), anyInt(), any(), any()))
                .thenReturn(SUCCESS);
        CarAudioService noZoneMappingAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationWithoutZoneMappingFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        noZoneMappingAudioService.init();

        noZoneMappingAudioService
                .setZoneIdForUid(SECONDARY_ZONE_ID, MEDIA_APP_UID);

        noZoneMappingAudioService
                .setZoneIdForUid(PRIMARY_AUDIO_ZONE, MEDIA_APP_UID);

        int zoneId = noZoneMappingAudioService
                .getZoneIdForUid(MEDIA_APP_UID);

        assertWithMessage("Get Zone for UID Zone Id")
                .that(zoneId).isEqualTo(PRIMARY_AUDIO_ZONE);
    }

    @Test
    public void clearZoneIdForUid_withoutRoutingPermission_fails() {
        mCarAudioService.init();

        mockDenyCarControlAudioSettingsPermission();

        SecurityException thrown = assertThrows(SecurityException.class,
                () -> mCarAudioService.clearZoneIdForUid(MEDIA_APP_UID));

        assertWithMessage("Clear Zone for UID Permission Exception")
                .that(thrown).hasMessageThat()
                .contains(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
    }

    @Test
    public void clearZoneIdForUid_withoutDynamicRouting_fails() {
        when(mMockResources.getBoolean(audioUseDynamicRouting))
                .thenReturn(/* useDynamicRouting= */ false);
        CarAudioService nonDynamicAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonDynamicAudioService.init();

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> nonDynamicAudioService.clearZoneIdForUid(MEDIA_APP_UID));

        assertWithMessage("Clear Zone for UID Dynamic Configuration Exception")
                .that(thrown).hasMessageThat()
                .contains("Dynamic routing is required");
    }

    @Test
    public void clearZoneIdForUid_withZoneAudioMapping_fails() {
        mCarAudioService.init();

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> mCarAudioService.clearZoneIdForUid(MEDIA_APP_UID));

        assertWithMessage("Clear Zone for UID Audio Zone Mapping Exception")
                .that(thrown).hasMessageThat()
                .contains("UID based routing is not supported while using occupant zone mapping");
    }

    @Test
    public void clearZoneIdForUid_forNonMappedUid_succeeds() throws Exception {
        CarAudioService noZoneMappingAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationWithoutZoneMappingFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        noZoneMappingAudioService.init();

        boolean status = noZoneMappingAudioService
                .clearZoneIdForUid(MEDIA_APP_UID);

        assertWithMessage("Clear Zone for UID Audio Zone without Mapping")
                .that(status).isTrue();
    }

    @Test
    public void clearZoneIdForUid_forMappedUid_succeeds() throws Exception {
        when(mMockAudioService.setUidDeviceAffinity(any(), anyInt(), any(), any()))
                .thenReturn(SUCCESS);
        CarAudioService noZoneMappingAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationWithoutZoneMappingFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        noZoneMappingAudioService.init();

        noZoneMappingAudioService
                .setZoneIdForUid(SECONDARY_ZONE_ID, MEDIA_APP_UID);

        boolean status = noZoneMappingAudioService.clearZoneIdForUid(MEDIA_APP_UID);

        assertWithMessage("Clear Zone for UID Audio Zone with Mapping")
                .that(status).isTrue();
    }

    @Test
    public void getZoneIdForUid_afterClearedUidMapping_returnsDefaultZone() throws Exception {
        when(mMockAudioService.setUidDeviceAffinity(any(), anyInt(), any(), any()))
                .thenReturn(SUCCESS);
        CarAudioService noZoneMappingAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationWithoutZoneMappingFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        noZoneMappingAudioService.init();

        noZoneMappingAudioService
                .setZoneIdForUid(SECONDARY_ZONE_ID, MEDIA_APP_UID);

        noZoneMappingAudioService.clearZoneIdForUid(MEDIA_APP_UID);

        int zoneId = noZoneMappingAudioService.getZoneIdForUid(MEDIA_APP_UID);

        assertWithMessage("Get Zone for UID Audio Zone with Cleared Mapping")
                .that(zoneId).isEqualTo(PRIMARY_AUDIO_ZONE);
    }

    @Test
    public void setGroupVolume_withoutPermission_fails() {
        mCarAudioService.init();

        mockDenyCarControlAudioVolumePermission();

        SecurityException thrown = assertThrows(SecurityException.class,
                () -> mCarAudioService.setGroupVolume(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP,
                        TEST_PRIMARY_GROUP_INDEX, TEST_FLAGS));

        assertWithMessage("Set Volume Group Permission Exception")
                .that(thrown).hasMessageThat()
                .contains(PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
    }

    @Test
    public void getOutputDeviceAddressForUsage_forMusicUsage() {
        mCarAudioService.init();

        String mediaDeviceAddress =
                mCarAudioService.getOutputDeviceAddressForUsage(PRIMARY_AUDIO_ZONE, USAGE_MEDIA);

        assertWithMessage("Media usage audio device address")
                .that(mediaDeviceAddress).isEqualTo(MEDIA_TEST_DEVICE);
    }

    @Test
    public void getOutputDeviceAddressForUsage_withNonDynamicRouting_forMediaUsage_fails() {
        when(mMockResources.getBoolean(audioUseDynamicRouting))
                .thenReturn(/* useDynamicRouting= */ false);
        CarAudioService nonDynamicAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonDynamicAudioService.init();

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> nonDynamicAudioService
                        .getOutputDeviceAddressForUsage(PRIMARY_AUDIO_ZONE, USAGE_MEDIA));

        assertWithMessage("Non dynamic routing media usage audio device address exception")
                .that(thrown).hasMessageThat().contains("Dynamic routing is required");
    }

    @Test
    public void getOutputDeviceAddressForUsage_forNavigationUsage() {
        mCarAudioService.init();

        String mediaDeviceAddress =
                mCarAudioService.getOutputDeviceAddressForUsage(PRIMARY_AUDIO_ZONE,
                        USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);

        assertWithMessage("Navigation usage audio device address")
                .that(mediaDeviceAddress).isEqualTo(NAVIGATION_TEST_DEVICE);
    }

    @Test
    public void getOutputDeviceAddressForUsage_forInvalidUsage_fails() {
        mCarAudioService.init();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mCarAudioService.getOutputDeviceAddressForUsage(PRIMARY_AUDIO_ZONE,
                        INVALID_USAGE));

        assertWithMessage("Invalid usage audio device address exception")
                .that(thrown).hasMessageThat().contains("Invalid audio attribute " + INVALID_USAGE);
    }

    @Test
    public void getOutputDeviceAddressForUsage_forVirtualUsage_fails() {
        mCarAudioService.init();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mCarAudioService.getOutputDeviceAddressForUsage(PRIMARY_AUDIO_ZONE,
                        AudioManagerHelper.getUsageVirtualSource()));

        assertWithMessage("Invalid context audio device address exception")
                .that(thrown).hasMessageThat()
                .contains("invalid");
    }

    @Test
    public void getOutputDeviceAddressForUsage_onSecondaryZone_forMusicUsage() {
        mCarAudioService.init();

        String mediaDeviceAddress =
                mCarAudioService.getOutputDeviceAddressForUsage(SECONDARY_ZONE_ID, USAGE_MEDIA);

        assertWithMessage("Media usage audio device address for secondary zone")
                .that(mediaDeviceAddress).isEqualTo(SECONDARY_TEST_DEVICE);
    }

    @Test
    public void getSuggestedAudioContextForPrimaryZone() {
        mCarAudioService.init();
        int defaultAudioContext = mCarAudioService.getCarAudioContext()
                .getContextForAudioAttribute(CAR_DEFAULT_AUDIO_ATTRIBUTE);

        assertWithMessage("Suggested audio context for primary zone")
                .that(mCarAudioService.getSuggestedAudioContextForPrimaryZone())
                .isEqualTo(defaultAudioContext);
    }

    @Test
    public void isVolumeGroupMuted_noSetVolumeGroupMute() {
        mCarAudioService.init();

        assertWithMessage("Volume group mute for default state")
                .that(mCarAudioService.isVolumeGroupMuted(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP))
                .isFalse();
    }

    @Test
    public void isVolumeGroupMuted_setVolumeGroupMuted_isFalse() {
        mCarAudioService.init();
        mCarAudioService.setVolumeGroupMute(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP,
                /* mute= */ true, TEST_FLAGS);

        mCarAudioService.setVolumeGroupMute(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP,
                /* mute= */ false, TEST_FLAGS);

        assertWithMessage("Volume group muted after mute and unmute")
                .that(mCarAudioService.isVolumeGroupMuted(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP))
                .isFalse();
    }

    @Test
    public void isVolumeGroupMuted_setVolumeGroupMuted_isTrue() {
        mCarAudioService.init();

        mCarAudioService.setVolumeGroupMute(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP,
                /* mute= */ true, TEST_FLAGS);
        assertWithMessage("Volume group muted after mute")
                .that(mCarAudioService.isVolumeGroupMuted(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP))
                .isTrue();
    }

    @Test
    public void isVolumeGroupMuted_withVolumeGroupMutingDisabled() {
        when(mMockResources.getBoolean(audioUseCarVolumeGroupMuting))
                .thenReturn(false);
        CarAudioService nonVolumeGroupMutingAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonVolumeGroupMutingAudioService.init();

        assertWithMessage("Volume group for disabled volume group muting")
                .that(nonVolumeGroupMutingAudioService.isVolumeGroupMuted(
                        PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP))
                .isFalse();
    }

    @Test
    public void getGroupMinVolume_forPrimaryZone() {
        mCarAudioService.init();

        assertWithMessage("Group Min Volume for primary audio zone and group")
                .that(mCarAudioService.getGroupMinVolume(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP))
                .isEqualTo(0);
    }

    @Test
    public void getGroupMaxVolume_withNoDynamicRouting() {
        when(mMockResources.getBoolean(audioUseDynamicRouting))
                .thenReturn(/* useDynamicRouting= */ false);
        CarAudioService nonDynamicAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonDynamicAudioService.init();

        nonDynamicAudioService.getGroupMaxVolume(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP);

        verify(mAudioManager).getStreamMaxVolume(
                CarAudioDynamicRouting.STREAM_TYPES[TEST_PRIMARY_GROUP]);
    }

    @Test
    public void getGroupMinVolume_withNoDynamicRouting() {
        when(mMockResources.getBoolean(audioUseDynamicRouting))
                .thenReturn(/* useDynamicRouting= */ false);
        CarAudioService nonDynamicAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonDynamicAudioService.init();

        nonDynamicAudioService.getGroupMinVolume(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP);

        verify(mAudioManager).getStreamMinVolume(
                CarAudioDynamicRouting.STREAM_TYPES[TEST_PRIMARY_GROUP]);
    }

    @Test
    public void getGroupCurrentVolume_withNoDynamicRouting() {
        when(mMockResources.getBoolean(audioUseDynamicRouting))
                .thenReturn(/* useDynamicRouting= */ false);
        CarAudioService nonDynamicAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonDynamicAudioService.init();

        nonDynamicAudioService.getGroupVolume(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP);

        verify(mAudioManager).getStreamVolume(
                CarAudioDynamicRouting.STREAM_TYPES[TEST_PRIMARY_GROUP]);
    }

    @Test
    public void isAudioFeatureEnabled_forUnrecognizableAudioFeature_throws() {
        mCarAudioService.init();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> mCarAudioService.isAudioFeatureEnabled(INVALID_AUDIO_FEATURE));

        assertWithMessage("Unknown audio feature")
                .that(thrown).hasMessageThat()
                .contains("Unknown Audio Feature type: " + INVALID_AUDIO_FEATURE);
    }

    @Test
    public void getVolumeGroupIdForAudioContext_forPrimaryGroup() {
        mCarAudioService.init();

        assertWithMessage("Volume group ID for primary audio zone")
                .that(mCarAudioService.getVolumeGroupIdForAudioContext(PRIMARY_AUDIO_ZONE,
                        CarAudioContext.MUSIC))
                .isEqualTo(TEST_PRIMARY_GROUP_INDEX);
    }

    @Test
    public void getVolumeGroupIdForAudioAttribute() {
        mCarAudioService.init();

        assertWithMessage("Volume group ID for primary audio zone")
                .that(mCarAudioService.getVolumeGroupIdForAudioAttribute(PRIMARY_AUDIO_ZONE,
                        CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA)))
                .isEqualTo(TEST_PRIMARY_GROUP_INDEX);
    }

    @Test
    public void getVolumeGroupIdForAudioAttribute_withNullAttribute_fails() {
        mCarAudioService.init();

        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                mCarAudioService.getVolumeGroupIdForAudioAttribute(PRIMARY_AUDIO_ZONE,
                /* attribute= */ null));

        assertWithMessage("Null audio attribute exception").that(thrown).hasMessageThat()
                .contains("Audio attributes");
    }

    @Test
    public void getVolumeGroupIdForAudioAttribute_withInvalidZoneId_fails() {
        mCarAudioService.init();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mCarAudioService.getVolumeGroupIdForAudioAttribute(INVALID_AUDIO_ZONE,
                        CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA)));

        assertWithMessage("Invalid audio zone exception").that(thrown).hasMessageThat()
                .contains("Invalid audio zone Id");
    }

    @Test
    public void getExternalSources_forSingleDevice() {
        mCarAudioService.init();
        AudioDeviceInfo[] inputDevices = generateInputDeviceInfos();

        assertWithMessage("External input device addresses")
                .that(mCarAudioService.getExternalSources())
                .asList().containsExactly(inputDevices[1].getAddress());
    }

    @Test
    public void getMutedVolumeGroups_forInvalidZone() {
        mCarAudioService.init();

        assertWithMessage("Muted volume groups for invalid zone")
                .that(mCarAudioService.getMutedVolumeGroups(INVALID_AUDIO_ZONE))
                .isEmpty();
    }

    @Test
    public void getMutedVolumeGroups_whenVolumeGroupMuteNotSupported() {
        when(mMockResources.getBoolean(audioUseCarVolumeGroupMuting)).thenReturn(false);
        CarAudioService nonVolumeGroupMutingAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonVolumeGroupMutingAudioService.init();

        assertWithMessage("Muted volume groups with disable mute feature")
                .that(nonVolumeGroupMutingAudioService.getMutedVolumeGroups(PRIMARY_AUDIO_ZONE))
                .isEmpty();
    }

    @Test
    public void getMutedVolumeGroups_withMutedGroups() {
        mCarAudioService.init();
        mCarAudioService.setVolumeGroupMute(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP,
                /* muted= */ true, TEST_FLAGS);
        mCarAudioService.setVolumeGroupMute(PRIMARY_AUDIO_ZONE, TEST_SECONDARY_GROUP,
                /* muted= */ true, TEST_FLAGS);

        assertWithMessage("Muted volume groups")
                .that(mCarAudioService.getMutedVolumeGroups(PRIMARY_AUDIO_ZONE))
                .containsExactly(TEST_PRIMARY_VOLUME_INFO, TEST_SECONDARY_VOLUME_INFO);
    }

    @Test
    public void getMutedVolumeGroups_afterUnmuting() {
        mCarAudioService.init();
        mCarAudioService.setVolumeGroupMute(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP,
                /* muted= */ true, TEST_FLAGS);
        mCarAudioService.setVolumeGroupMute(PRIMARY_AUDIO_ZONE, TEST_SECONDARY_GROUP,
                /* muted= */ true, TEST_FLAGS);
        mCarAudioService.setVolumeGroupMute(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP,
                /* muted= */ false, TEST_FLAGS);

        assertWithMessage("Muted volume groups after unmuting one group")
                .that(mCarAudioService.getMutedVolumeGroups(PRIMARY_AUDIO_ZONE))
                .containsExactly(TEST_SECONDARY_VOLUME_INFO);
    }

    @Test
    public void getMutedVolumeGroups_withMutedGroupsForDifferentZone() {
        mCarAudioService.init();
        mCarAudioService.setVolumeGroupMute(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP,
                /* muted= */ true, TEST_FLAGS);
        mCarAudioService.setVolumeGroupMute(PRIMARY_AUDIO_ZONE, TEST_SECONDARY_GROUP,
                /* muted= */ true, TEST_FLAGS);

        assertWithMessage("Muted volume groups for secondary zone")
                .that(mCarAudioService.getMutedVolumeGroups(SECONDARY_ZONE_ID)).isEmpty();
    }

    @Test
    public void getVolumeGroupInfosForZone() {
        mCarAudioService.init();
        int groupCount = mCarAudioService.getVolumeGroupCount(PRIMARY_AUDIO_ZONE);

        List<CarVolumeGroupInfo> infos =
                mCarAudioService.getVolumeGroupInfosForZone(PRIMARY_AUDIO_ZONE);

        for (int index = 0; index < groupCount; index++) {
            CarVolumeGroupInfo info = mCarAudioService
                    .getVolumeGroupInfo(PRIMARY_AUDIO_ZONE, index);
            assertWithMessage("Car volume group infos for primary zone and info %s", info)
                    .that(infos).contains(info);
        }
    }

    @Test
    public void getVolumeGroupInfosForZone_forDynamicRoutingDisabled() {
        when(mMockResources.getBoolean(audioUseDynamicRouting))
                .thenReturn(/* useDynamicRouting= */ false);
        CarAudioService nonDynamicAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonDynamicAudioService.init();

        List<CarVolumeGroupInfo> infos =
                nonDynamicAudioService.getVolumeGroupInfosForZone(PRIMARY_AUDIO_ZONE);

        assertWithMessage("Car volume group infos with dynamic routing disabled")
                .that(infos).isEmpty();
    }

    @Test
    public void getVolumeGroupInfosForZone_size() {
        mCarAudioService.init();
        int groupCount = mCarAudioService.getVolumeGroupCount(PRIMARY_AUDIO_ZONE);

        List<CarVolumeGroupInfo> infos =
                mCarAudioService.getVolumeGroupInfosForZone(PRIMARY_AUDIO_ZONE);

        assertWithMessage("Car volume group infos size for primary zone")
                .that(infos).hasSize(groupCount);
    }

    @Test
    public void getVolumeGroupInfosForZone_forInvalidZone() {
        mCarAudioService.init();

        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () ->
                        mCarAudioService.getVolumeGroupInfosForZone(INVALID_AUDIO_ZONE));

        assertWithMessage("Exception for volume group infos size for invalid zone")
                .that(thrown).hasMessageThat().contains("audio zone Id");
    }

    @Test
    public void getVolumeGroupInfo() {
        CarVolumeGroupInfo testVolumeGroupInfo =
                new CarVolumeGroupInfo.Builder(TEST_PRIMARY_VOLUME_INFO).setMuted(false).build();
        mCarAudioService.init();

        assertWithMessage("Car volume group info for primary zone")
                .that(mCarAudioService.getVolumeGroupInfo(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP))
                .isEqualTo(testVolumeGroupInfo);
    }

    @Test
    public void getVolumeGroupInfo_forInvalidZone() {
        mCarAudioService.init();

        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () ->
                        mCarAudioService.getVolumeGroupInfo(INVALID_AUDIO_ZONE,
                                TEST_PRIMARY_GROUP));

        assertWithMessage("Exception for volume group info size for invalid zone")
                .that(thrown).hasMessageThat().contains("audio zone Id");
    }

    @Test
    public void getVolumeGroupInfo_forInvalidGroup() {
        mCarAudioService.init();

        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () ->
                        mCarAudioService.getVolumeGroupInfo(INVALID_AUDIO_ZONE,
                                TEST_PRIMARY_GROUP));

        assertWithMessage("Exception for volume groups info size for invalid group id")
                .that(thrown).hasMessageThat().contains("audio zone Id");
    }

    @Test
    public void getVolumeGroupInfo_forGroupOverRange() {
        mCarAudioService.init();
        int groupCount = mCarAudioService.getVolumeGroupCount(PRIMARY_AUDIO_ZONE);

        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () ->
                        mCarAudioService.getVolumeGroupInfo(INVALID_AUDIO_ZONE,
                                groupCount));

        assertWithMessage("Exception for volume groups info size for out of range group")
                .that(thrown).hasMessageThat().contains("audio zone Id");
    }

    @Test
    public void getAudioAttributesForVolumeGroup() {
        mCarAudioService.init();
        CarVolumeGroupInfo info = mCarAudioService.getVolumeGroupInfo(PRIMARY_AUDIO_ZONE,
                TEST_PRIMARY_GROUP);

        List<AudioAttributes> audioAttributes =
                mCarAudioService.getAudioAttributesForVolumeGroup(info);

        assertWithMessage("Volume group audio attributes").that(audioAttributes)
                .containsExactly(
                        CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA),
                        CarAudioContext.getAudioAttributeFromUsage(USAGE_GAME),
                        CarAudioContext.getAudioAttributeFromUsage(USAGE_UNKNOWN),
                        CarAudioContext.getAudioAttributeFromUsage(USAGE_NOTIFICATION),
                        CarAudioContext.getAudioAttributeFromUsage(USAGE_NOTIFICATION_EVENT),
                        CarAudioContext.getAudioAttributeFromUsage(USAGE_ANNOUNCEMENT));
    }

    @Test
    public void getAudioAttributesForVolumeGroup_withNullInfo_fails() {
        mCarAudioService.init();

        NullPointerException thrown =
                assertThrows(NullPointerException.class, () ->
                        mCarAudioService.getAudioAttributesForVolumeGroup(/* groupInfo= */ null));

        assertWithMessage("Volume group audio attributes with null info exception")
                .that(thrown).hasMessageThat().contains("Car volume group info");
    }

    @Test
    public void getAudioAttributesForVolumeGroup_withDynamicRoutingDisabled() {
        when(mMockResources.getBoolean(audioUseDynamicRouting))
                .thenReturn(/* useDynamicRouting= */ false);
        CarAudioService nonDynamicAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonDynamicAudioService.init();

        List<AudioAttributes> audioAttributes =
                nonDynamicAudioService.getAudioAttributesForVolumeGroup(TEST_PRIMARY_VOLUME_INFO);

        assertWithMessage("Volume group audio attributes with dynamic routing disabled")
                .that(audioAttributes).isEmpty();
    }

    @Test
    public void getActiveAudioAttributesForZone() {
        mCarAudioService.init();

        assertWithMessage("Default active audio attributes").that(
                mCarAudioService.getActiveAudioAttributesForZone(PRIMARY_AUDIO_ZONE)).isEmpty();
    }

    @Test
    public void getActiveAudioAttributesForZone_withActiveHalFocus() {
        when(mAudioManager.requestAudioFocus(any())).thenReturn(
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        mCarAudioService.init();
        requestHalAudioFocus(USAGE_ALARM);

        assertWithMessage("HAL active audio attributes")
                .that(mCarAudioService.getActiveAudioAttributesForZone(PRIMARY_AUDIO_ZONE))
                .containsExactly(new AudioAttributes.Builder().setUsage(USAGE_ALARM).build());
    }

    @Test
    public void getActiveAudioAttributesForZone_withActivePlayback() {
        mCarAudioService.init();
        mockActivePlayback();

        assertWithMessage("Playback active audio attributes")
                .that(mCarAudioService.getActiveAudioAttributesForZone(PRIMARY_AUDIO_ZONE))
                .containsExactly(new AudioAttributes.Builder().setUsage(USAGE_MEDIA).build());
    }

    @Test
    public void getActiveAudioAttributesForZone_withActiveHalAndPlayback() {
        mCarAudioService.init();
        mockActivePlayback();
        when(mAudioManager.requestAudioFocus(any())).thenReturn(
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        requestHalAudioFocus(USAGE_VOICE_COMMUNICATION);

        assertWithMessage("Playback active audio attributes")
                .that(mCarAudioService.getActiveAudioAttributesForZone(PRIMARY_AUDIO_ZONE))
                .containsExactly(new AudioAttributes.Builder().setUsage(USAGE_MEDIA).build(),
                        new AudioAttributes.Builder().setUsage(USAGE_VOICE_COMMUNICATION).build());
    }

    private void requestHalAudioFocus(int usage) {
        ArgumentCaptor<HalFocusListener> captor =
                ArgumentCaptor.forClass(HalFocusListener.class);
        verify(mAudioControlWrapperAidl).registerFocusListener(captor.capture());
        HalFocusListener halFocusListener = captor.getValue();
        halFocusListener.requestAudioFocus(usage, PRIMARY_AUDIO_ZONE,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
    }

    private void mockActivePlayback() {
        List<AudioPlaybackConfiguration> configurations = List.of(getPlaybackConfig());
        when(mAudioManager.getActivePlaybackConfigurations())
                .thenReturn(configurations);
    }

    private AudioPlaybackConfiguration getPlaybackConfig() {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(USAGE_MEDIA).build();
        AudioPlaybackConfiguration config = mock(AudioPlaybackConfiguration.class);
        when(config.getAudioAttributes()).thenReturn(audioAttributes);
        when(config.getAudioDeviceInfo()).thenReturn(mMediaOutputDevice);
        when(config.isActive()).thenReturn(true);

        return config;
    }

    private void mockGrantCarControlAudioSettingsPermission() {
        mockContextCheckCallingOrSelfPermission(mMockContext,
                PERMISSION_CAR_CONTROL_AUDIO_SETTINGS, PERMISSION_GRANTED);
    }

    private void mockDenyCarControlAudioSettingsPermission() {
        mockContextCheckCallingOrSelfPermission(mMockContext,
                PERMISSION_CAR_CONTROL_AUDIO_SETTINGS, PERMISSION_DENIED);
    }

    private void mockDenyCarControlAudioVolumePermission() {
        mockContextCheckCallingOrSelfPermission(mMockContext,
                PERMISSION_CAR_CONTROL_AUDIO_VOLUME, PERMISSION_DENIED);
    }

    private AudioDeviceInfo[] generateInputDeviceInfos() {
        mTunerDevice = new AudioDeviceInfoBuilder().setAddressName(PRIMARY_ZONE_FM_TUNER_ADDRESS)
                .setType(TYPE_FM_TUNER)
                .setIsSource(true)
                .build();
        return new AudioDeviceInfo[]{
                new AudioDeviceInfoBuilder().setAddressName(PRIMARY_ZONE_MICROPHONE_ADDRESS)
                        .setType(TYPE_BUILTIN_MIC)
                        .setIsSource(true)
                        .build(),
                mTunerDevice
        };
    }

    private AudioDeviceInfo[] generateOutputDeviceInfos() {
        mMediaOutputDevice = new AudioDeviceInfoBuilder()
                .setAudioGains(new AudioGain[] {new GainBuilder().build()})
                .setAddressName(MEDIA_TEST_DEVICE)
                .build();
        return new AudioDeviceInfo[] {
                mMediaOutputDevice,
                new AudioDeviceInfoBuilder()
                        .setAudioGains(new AudioGain[] {new GainBuilder().build()})
                        .setAddressName(NAVIGATION_TEST_DEVICE)
                        .build(),
                new AudioDeviceInfoBuilder()
                        .setAudioGains(new AudioGain[] {new GainBuilder().build()})
                        .setAddressName(CALL_TEST_DEVICE)
                        .build(),
                new AudioDeviceInfoBuilder()
                        .setAudioGains(new AudioGain[] {new GainBuilder().build()})
                        .setAddressName(SYSTEM_BUS_DEVICE)
                        .build(),
                new AudioDeviceInfoBuilder()
                        .setAudioGains(new AudioGain[] {new GainBuilder().build()})
                        .setAddressName(NOTIFICATION_TEST_DEVICE)
                        .build(),
                new AudioDeviceInfoBuilder()
                        .setAudioGains(new AudioGain[] {new GainBuilder().build()})
                        .setAddressName(VOICE_TEST_DEVICE)
                        .build(),
                new AudioDeviceInfoBuilder()
                        .setAudioGains(new AudioGain[] {new GainBuilder().build()})
                        .setAddressName(RING_TEST_DEVICE)
                        .build(),
                new AudioDeviceInfoBuilder()
                        .setAudioGains(new AudioGain[] {new GainBuilder().build()})
                        .setAddressName(ALARM_TEST_DEVICE)
                        .build(),
                new AudioDeviceInfoBuilder()
                        .setAudioGains(new AudioGain[] {new GainBuilder().build()})
                        .setAddressName(SECONDARY_TEST_DEVICE)
                        .build(),
        };
    }

    private static AudioFocusInfo createAudioFocusInfoForMedia() {
        AudioAttributes.Builder builder = new AudioAttributes.Builder();
        builder.setUsage(USAGE_MEDIA);

        return new AudioFocusInfo(builder.build(), MEDIA_APP_UID, MEDIA_CLIENT_ID,
                MEDIA_PACKAGE_NAME, AUDIOFOCUS_GAIN, AUDIOFOCUS_LOSS, MEDIA_EMPTY_FLAG, SDK_INT);
    }
}
