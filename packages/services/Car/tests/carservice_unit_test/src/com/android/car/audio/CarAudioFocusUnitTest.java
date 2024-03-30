/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;
import static android.media.AudioAttributes.USAGE_ANNOUNCEMENT;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_ASSISTANT;
import static android.media.AudioAttributes.USAGE_EMERGENCY;
import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioAttributes.USAGE_NOTIFICATION;
import static android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE;
import static android.media.AudioAttributes.USAGE_VEHICLE_STATUS;
import static android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION;
import static android.media.AudioManager.AUDIOFOCUS_GAIN;
import static android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE;
import static android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK;
import static android.media.AudioManager.AUDIOFOCUS_LOSS;
import static android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_DELAYED;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_FAILED;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.oem.AudioFocusEntry;
import android.car.oem.OemCarAudioFocusEvaluationRequest;
import android.car.oem.OemCarAudioFocusResult;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioAttributes.AttributeUsage;
import android.media.AudioFocusInfo;
import android.media.AudioManager;
import android.media.audiopolicy.AudioPolicy;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.CarLocalServices;
import com.android.car.oem.CarOemAudioFocusProxyService;
import com.android.car.oem.CarOemProxyService;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CarAudioFocusUnitTest {
    private static final int CLIENT_UID = 1;
    private static final String FIRST_CLIENT_ID = "first-client-id";
    private static final String SECOND_CLIENT_ID = "second-client-id";
    private static final String THIRD_CLIENT_ID = "third-client-id";
    private static final String CALL_CLIENT_ID = "AudioFocus_For_Phone_Ring_And_Calls";

    private static final String PACKAGE_NAME = "com.android.car.audio";
    private static final int AUDIOFOCUS_FLAG = 0;

    private static final CarAudioContext TEST_CAR_AUDIO_CONTEXT =
            new CarAudioContext(CarAudioContext.getAllContextsInfo());

    private static final int TEST_VOLUME_GROUP = 5;

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();
    @Mock
    private AudioManager mMockAudioManager;
    @Mock
    private PackageManager mMockPackageManager;
    @Mock
    private AudioPolicy mAudioPolicy;
    @Mock
    private CarAudioSettings mCarAudioSettings;
    @Mock
    private CarVolumeInfoWrapper mMockCarVolumeInfoWrapper;
    @Mock
    private CarOemProxyService mMockCarOemProxyService;
    @Mock
    private CarOemAudioFocusProxyService mMockAudioFocusProxyService;

    private FocusInteraction mFocusInteraction;

    @Before
    public void setUp() {
        mFocusInteraction = new FocusInteraction(mCarAudioSettings);
        CarLocalServices.removeServiceForTest(CarOemProxyService.class);
        CarLocalServices.addService(CarOemProxyService.class, mMockCarOemProxyService);
    }

    @After
    public void tearDown() {
        CarLocalServices.removeServiceForTest(CarOemProxyService.class);
    }

    @Test
    public void constructor_withNullCarAudioContext_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            new CarAudioFocus(mMockAudioManager, mMockPackageManager,
                    mFocusInteraction, /* carAudioContext= */ null, mMockCarVolumeInfoWrapper,
                    PRIMARY_AUDIO_ZONE);
        });

        assertWithMessage("Constructor with null car audio context exception")
                .that(thrown).hasMessageThat().contains("Car audio context");
    }

    @Test
    public void constructor_withNullAudioManager_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            new CarAudioFocus(/* audioManager= */ null, mMockPackageManager,
                    mFocusInteraction, TEST_CAR_AUDIO_CONTEXT, mMockCarVolumeInfoWrapper,
                    PRIMARY_AUDIO_ZONE);
        });

        assertWithMessage("Constructor with null audio manager exception")
                .that(thrown).hasMessageThat().contains("Audio manager");
    }

    @Test
    public void constructor_withNullPackageManager_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            new CarAudioFocus(mMockAudioManager, /* packageManager= */ null,
                    mFocusInteraction, TEST_CAR_AUDIO_CONTEXT, mMockCarVolumeInfoWrapper,
                    PRIMARY_AUDIO_ZONE);
        });

        assertWithMessage("Constructor with null package manager exception")
                .that(thrown).hasMessageThat().contains("Package manager");
    }

    @Test
    public void constructor_withNullFocusInteractions_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            new CarAudioFocus(mMockAudioManager, mMockPackageManager,
                    /* focusInteractions= */ null, TEST_CAR_AUDIO_CONTEXT,
                    mMockCarVolumeInfoWrapper,
                    PRIMARY_AUDIO_ZONE);
        });

        assertWithMessage("Constructor with null focus interaction exception")
                .that(thrown).hasMessageThat().contains("Focus interactions");
    }

    @Test
    public void constructor_withNullVolumeInfoWrapper_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            new CarAudioFocus(mMockAudioManager, mMockPackageManager,
                    mFocusInteraction, TEST_CAR_AUDIO_CONTEXT, /* carVolumeInfo= */ null,
                    PRIMARY_AUDIO_ZONE);
        });

        assertWithMessage("Constructor with null focus volume info wrapper exception")
                .that(thrown).hasMessageThat().contains("Car volume info");
    }

    @Test
    public void onAudioFocusRequest_withNoCurrentFocusHolder_requestGranted() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        AudioFocusInfo audioFocusInfo = getInfoForFirstClientWithMedia();
        carAudioFocus.onAudioFocusRequest(audioFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager).setFocusRequestResult(audioFocusInfo,
                AUDIOFOCUS_REQUEST_GRANTED, mAudioPolicy);
    }

    @Test
    public void onAudioFocusRequest_withSameClientIdSameUsage_requestGranted() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        AudioFocusInfo audioFocusInfo = getInfoForFirstClientWithMedia();
        carAudioFocus.onAudioFocusRequest(audioFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        AudioFocusInfo sameClientAndUsageFocusInfo = getInfoForFirstClientWithMedia();
        carAudioFocus.onAudioFocusRequest(sameClientAndUsageFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager, times(2)).setFocusRequestResult(sameClientAndUsageFocusInfo,
                AUDIOFOCUS_REQUEST_GRANTED, mAudioPolicy);
    }

    @Test
    public void onAudioFocusRequest_withSameCallClientIdDifferentUsage_requestGranted() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        AudioFocusInfo audioFocusInfo = getInfo(USAGE_VOICE_COMMUNICATION, CALL_CLIENT_ID,
                AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, /* acceptsDelayedFocus= */ false);
        carAudioFocus.onAudioFocusRequest(audioFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        AudioFocusInfo sameClientAndUsageFocusInfo = getInfo(USAGE_NOTIFICATION_RINGTONE,
                CALL_CLIENT_ID, AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                /* acceptsDelayedFocus= */ false);
        carAudioFocus.onAudioFocusRequest(sameClientAndUsageFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager, times(1)).setFocusRequestResult(audioFocusInfo,
                AUDIOFOCUS_REQUEST_GRANTED, mAudioPolicy);
        verify(mMockAudioManager, times(1)).setFocusRequestResult(sameClientAndUsageFocusInfo,
                AUDIOFOCUS_REQUEST_GRANTED, mAudioPolicy);
    }

    @Test
    public void onAudioFocusAbandon_whileMediaWaitsForRingerAndCall_focusRegained() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        AudioFocusInfo mediaFocusInfo = getInfoForFirstClientWithMedia();
        carAudioFocus.onAudioFocusRequest(mediaFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);
        AudioFocusInfo ringerFocusInfo = getInfo(USAGE_NOTIFICATION_RINGTONE, CALL_CLIENT_ID,
                AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, /* acceptsDelayedFocus= */ false);
        carAudioFocus.onAudioFocusRequest(ringerFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);
        AudioFocusInfo callFocusInfo = getInfo(USAGE_VOICE_COMMUNICATION, CALL_CLIENT_ID,
                AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, /* acceptsDelayedFocus= */ false);
        carAudioFocus.onAudioFocusRequest(callFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        carAudioFocus.onAudioFocusAbandon(callFocusInfo);

        List<Integer> focusChanges = getFocusChanges(mediaFocusInfo);
        assertWithMessage("Media focus changes with call and ringer")
                .that(focusChanges).containsExactly(AUDIOFOCUS_LOSS_TRANSIENT, AUDIOFOCUS_GAIN);
    }

    @Test
    public void onAudioFocusAbandon_whileMediaWaitsOnCallOnly_focusRegained() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        AudioFocusInfo mediaFocusInfo = getInfoForFirstClientWithMedia();
        carAudioFocus.onAudioFocusRequest(mediaFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);
        AudioFocusInfo callFocusInfo = getInfo(USAGE_VOICE_COMMUNICATION, CALL_CLIENT_ID,
                AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, /* acceptsDelayedFocus= */ false);
        carAudioFocus.onAudioFocusRequest(callFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        carAudioFocus.onAudioFocusAbandon(callFocusInfo);

        List<Integer> focusChanges = getFocusChanges(mediaFocusInfo);
        assertWithMessage("Media focus changes with call only")
                .that(focusChanges).containsExactly(AUDIOFOCUS_LOSS_TRANSIENT, AUDIOFOCUS_GAIN);
    }

    @Test
    public void onAudioFocusRequest_withSameClientIdDifferentUsage_requestFailed() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        requestFocusForMediaWithFirstClient(carAudioFocus);

        AudioFocusInfo sameClientFocusInfo = getInfo(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
                FIRST_CLIENT_ID, AUDIOFOCUS_GAIN, false);
        carAudioFocus.onAudioFocusRequest(sameClientFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager).setFocusRequestResult(sameClientFocusInfo,
                AUDIOFOCUS_REQUEST_FAILED, mAudioPolicy);
    }

    @Test
    public void onAudioFocusRequest_concurrentRequest_requestGranted() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        requestFocusForMediaWithFirstClient(carAudioFocus);

        AudioFocusInfo concurrentFocusInfo = getConcurrentInfo(AUDIOFOCUS_GAIN);
        carAudioFocus.onAudioFocusRequest(concurrentFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager).setFocusRequestResult(concurrentFocusInfo,
                AUDIOFOCUS_REQUEST_GRANTED, mAudioPolicy);
    }

    @Test
    public void onAudioFocusRequest_concurrentRequestWithoutDucking_holderLosesFocus() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        AudioFocusInfo initialFocusInfo = requestFocusForMediaWithFirstClient(carAudioFocus);

        AudioFocusInfo concurrentFocusInfo = getConcurrentInfo(AUDIOFOCUS_GAIN);
        carAudioFocus.onAudioFocusRequest(concurrentFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager).dispatchAudioFocusChange(initialFocusInfo,
                AudioManager.AUDIOFOCUS_LOSS, mAudioPolicy);
    }

    @Test
    public void onAudioFocusRequest_concurrentRequestMayDuck_holderRetainsFocus() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        AudioFocusInfo initialFocusInfo = requestFocusForMediaWithFirstClient(carAudioFocus);

        AudioFocusInfo concurrentFocusInfo = getConcurrentInfo(AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        carAudioFocus.onAudioFocusRequest(concurrentFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager, times(0)).dispatchAudioFocusChange(eq(initialFocusInfo),
                anyInt(), eq(mAudioPolicy));
    }

    @Test
    public void onAudioFocusRequest_exclusiveRequest_requestGranted() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        requestFocusForMediaWithFirstClient(carAudioFocus);

        AudioFocusInfo exclusiveRequestInfo = getExclusiveInfo(AUDIOFOCUS_GAIN);
        carAudioFocus.onAudioFocusRequest(exclusiveRequestInfo, AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager).setFocusRequestResult(exclusiveRequestInfo,
                AUDIOFOCUS_REQUEST_GRANTED, mAudioPolicy);
    }

    @Test
    public void onAudioFocusRequest_exclusiveRequest_holderLosesFocus() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        AudioFocusInfo initialFocusInfo = requestFocusForMediaWithFirstClient(carAudioFocus);

        AudioFocusInfo exclusiveRequestInfo = getExclusiveInfo(AUDIOFOCUS_GAIN);
        carAudioFocus.onAudioFocusRequest(exclusiveRequestInfo, AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager).dispatchAudioFocusChange(initialFocusInfo,
                AudioManager.AUDIOFOCUS_LOSS, mAudioPolicy);
    }

    @Test
    public void onAudioFocusRequest_exclusiveRequestMayDuck_holderLosesFocusTransiently() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        AudioFocusInfo initialFocusInfo = requestFocusForMediaWithFirstClient(carAudioFocus);

        AudioFocusInfo exclusiveRequestInfo = getExclusiveInfo(AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        carAudioFocus.onAudioFocusRequest(exclusiveRequestInfo, AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager).dispatchAudioFocusChange(initialFocusInfo,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, mAudioPolicy);
    }

    @Test
    public void onAudioFocus_rejectRequest_requestFailed() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        requestFocusForUsageWithFirstClient(USAGE_ASSISTANT, carAudioFocus);

        AudioFocusInfo rejectRequestInfo = getRejectInfo();
        carAudioFocus.onAudioFocusRequest(rejectRequestInfo, AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager).setFocusRequestResult(rejectRequestInfo,
                AUDIOFOCUS_REQUEST_FAILED, mAudioPolicy);
    }

    @Test
    public void onAudioFocus_rejectRequest_holderRetainsFocus() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        AudioFocusInfo initialFocusInfo = requestFocusForUsageWithFirstClient(USAGE_ASSISTANT,
                carAudioFocus);

        AudioFocusInfo rejectRequestInfo = getRejectInfo();
        carAudioFocus.onAudioFocusRequest(rejectRequestInfo, AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager, times(0)).dispatchAudioFocusChange(eq(initialFocusInfo),
                anyInt(), eq(mAudioPolicy));
    }

    // System Usage tests

    @Test
    public void onAudioFocus_exclusiveWithSystemUsage_requestGranted() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        requestFocusForMediaWithFirstClient(carAudioFocus);

        AudioFocusInfo exclusiveSystemUsageInfo = getExclusiveWithSystemUsageInfo();
        carAudioFocus.onAudioFocusRequest(exclusiveSystemUsageInfo, AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager).setFocusRequestResult(exclusiveSystemUsageInfo,
                AUDIOFOCUS_REQUEST_GRANTED, mAudioPolicy);
    }

    @Test
    public void onAudioFocus_exclusiveWithSystemUsage_holderLosesFocus() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        AudioFocusInfo initialFocusInfo = requestFocusForMediaWithFirstClient(carAudioFocus);

        AudioFocusInfo exclusiveSystemUsageInfo = getExclusiveWithSystemUsageInfo();
        carAudioFocus.onAudioFocusRequest(exclusiveSystemUsageInfo, AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager).dispatchAudioFocusChange(initialFocusInfo,
                AUDIOFOCUS_LOSS, mAudioPolicy);
    }

    @Test
    public void onAudioFocus_concurrentWithSystemUsage_requestGranted() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        requestFocusForMediaWithFirstClient(carAudioFocus);

        AudioFocusInfo concurrentSystemUsageInfo = getConcurrentWithSystemUsageInfo();
        carAudioFocus.onAudioFocusRequest(concurrentSystemUsageInfo, AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager).setFocusRequestResult(concurrentSystemUsageInfo,
                AUDIOFOCUS_REQUEST_GRANTED, mAudioPolicy);
    }

    @Test
    public void onAudioFocus_concurrentWithSystemUsageAndConcurrent_holderRetainsFocus() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        AudioFocusInfo initialFocusInfo = requestFocusForMediaWithFirstClient(carAudioFocus);

        AudioFocusInfo concurrentSystemUsageInfo = getConcurrentWithSystemUsageInfo();
        carAudioFocus.onAudioFocusRequest(concurrentSystemUsageInfo, AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager, times(0)).dispatchAudioFocusChange(eq(initialFocusInfo),
                anyInt(), eq(mAudioPolicy));
    }

    @Test
    public void onAudioFocus_rejectWithSystemUsage_requestFailed() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        requestFocusForUsageWithFirstClient(USAGE_VOICE_COMMUNICATION, carAudioFocus);

        AudioFocusInfo rejectWithSystemUsageInfo = getRejectWithSystemUsageInfo();
        carAudioFocus.onAudioFocusRequest(rejectWithSystemUsageInfo, AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager).setFocusRequestResult(rejectWithSystemUsageInfo,
                AUDIOFOCUS_REQUEST_FAILED, mAudioPolicy);
    }

    // Delayed Focus tests
    @Test
    public void onAudioFocus_requestWithDelayedFocus_requestGranted() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();

        AudioFocusInfo delayedFocusInfo = getDelayedExclusiveInfo(AUDIOFOCUS_GAIN);
        carAudioFocus.onAudioFocusRequest(delayedFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager)
                .setFocusRequestResult(delayedFocusInfo,
                        AUDIOFOCUS_REQUEST_GRANTED, mAudioPolicy);
    }

    @Test
    public void
            requestAudioFocusWithDelayed_whileInCall_thenConcurrentNav_delayedFocusNotChanged() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        AudioFocusInfo callFocusInfo = setupFocusInfoAndRequestFocusForCall(carAudioFocus);
        AudioFocusInfo delayedFocusInfo = getDelayedExclusiveInfo(AUDIOFOCUS_GAIN);
        carAudioFocus.onAudioFocusRequest(delayedFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        // Nav focus request: concurrent with call (and also with delayed music)
        AudioFocusInfo secondConcurrentRequest =
                getInfo(
                        USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
                        THIRD_CLIENT_ID,
                        AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                        /* acceptsDelayedFocus= */ true);
        carAudioFocus.onAudioFocusRequest(secondConcurrentRequest, AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager)
                .setFocusRequestResult(
                        secondConcurrentRequest, AUDIOFOCUS_REQUEST_GRANTED, mAudioPolicy);
        verify(mMockAudioManager, never())
                .dispatchAudioFocusChange(callFocusInfo, AUDIOFOCUS_LOSS, mAudioPolicy);
        verify(mMockAudioManager, never())
                .dispatchAudioFocusChange(delayedFocusInfo, AUDIOFOCUS_LOSS, mAudioPolicy);
    }

    @Test
    public void requestAudioFocusWithDelayed_whileInCallAndNav_thenCallStop_delayedFocusGained() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        AudioFocusInfo callFocusInfo = setupFocusInfoAndRequestFocusForCall(carAudioFocus);
        AudioFocusInfo delayedFocusInfo = getDelayedExclusiveInfo(AUDIOFOCUS_GAIN);
        carAudioFocus.onAudioFocusRequest(delayedFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);
        // Nav focus request: concurrent with call (and also with delayed music)
        AudioFocusInfo secondConcurrentRequest =
                getInfo(
                        USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
                        THIRD_CLIENT_ID,
                        AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                        /* acceptsDelayedFocus= */ true);
        carAudioFocus.onAudioFocusRequest(secondConcurrentRequest, AUDIOFOCUS_REQUEST_GRANTED);

        carAudioFocus.onAudioFocusAbandon(callFocusInfo);

        verify(mMockAudioManager)
                .dispatchAudioFocusChange(secondConcurrentRequest, AUDIOFOCUS_LOSS, mAudioPolicy);
        verify(mMockAudioManager)
                .dispatchAudioFocusChange(delayedFocusInfo, AUDIOFOCUS_GAIN, mAudioPolicy);
    }

    @Test
    public void requestAudioFocusWithDelayed_whileInCall_thenRing_delayedFocusNotChanged() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        AudioFocusInfo callFocusInfo = setupFocusInfoAndRequestFocusForCall(carAudioFocus);
        AudioFocusInfo delayedFocusInfo = getDelayedExclusiveInfo(AUDIOFOCUS_GAIN);
        carAudioFocus.onAudioFocusRequest(delayedFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        // Ring focus request: concurrent with call (BUT REJECT delayed music)
        AudioFocusInfo secondConcurrentRequest =
                getInfo(
                        USAGE_NOTIFICATION_RINGTONE,
                        THIRD_CLIENT_ID,
                        AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                        true);
        carAudioFocus.onAudioFocusRequest(secondConcurrentRequest, AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager)
                .setFocusRequestResult(
                        secondConcurrentRequest, AUDIOFOCUS_REQUEST_GRANTED, mAudioPolicy);
        verify(mMockAudioManager, never())
                .dispatchAudioFocusChange(callFocusInfo, AUDIOFOCUS_LOSS, mAudioPolicy);
        verify(mMockAudioManager, never())
                .dispatchAudioFocusChange(delayedFocusInfo, AUDIOFOCUS_LOSS, mAudioPolicy);
    }

    @Test
    public void
            requestAudioFocusWithDelayed_whileInCallAndRing_thenCallStop_delayedFocusNotChanged() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        AudioFocusInfo callFocusInfo = setupFocusInfoAndRequestFocusForCall(carAudioFocus);
        AudioFocusInfo delayedFocusInfo = getDelayedExclusiveInfo(AUDIOFOCUS_GAIN);
        carAudioFocus.onAudioFocusRequest(delayedFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);
        // Ring focus request: concurrent with call (BUT REJECT delayed music)
        AudioFocusInfo secondConcurrentRequest =
                getInfo(
                        USAGE_NOTIFICATION_RINGTONE,
                        THIRD_CLIENT_ID,
                        AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                        true);
        carAudioFocus.onAudioFocusRequest(secondConcurrentRequest, AUDIOFOCUS_REQUEST_GRANTED);

        carAudioFocus.onAudioFocusAbandon(callFocusInfo);

        verify(mMockAudioManager, never())
                .dispatchAudioFocusChange(secondConcurrentRequest, AUDIOFOCUS_LOSS, mAudioPolicy);
        verify(mMockAudioManager, never())
                .dispatchAudioFocusChange(delayedFocusInfo, AUDIOFOCUS_LOSS, mAudioPolicy);
    }

    @Test
    public void requestAudioFocusWithDelayed_whileInCallAndRing_thenBothStop_delayedFocusGained() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        AudioFocusInfo callFocusInfo = setupFocusInfoAndRequestFocusForCall(carAudioFocus);
        AudioFocusInfo delayedFocusInfo = getDelayedExclusiveInfo(AUDIOFOCUS_GAIN);
        carAudioFocus.onAudioFocusRequest(delayedFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);
        // Yet another focus request concurrent with call (BUT REJECT delayed)
        AudioFocusInfo secondConcurrentRequest =
                getInfo(
                        USAGE_NOTIFICATION_RINGTONE,
                        THIRD_CLIENT_ID,
                        AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                        true);
        carAudioFocus.onAudioFocusRequest(secondConcurrentRequest, AUDIOFOCUS_REQUEST_GRANTED);

        carAudioFocus.onAudioFocusAbandon(callFocusInfo);
        carAudioFocus.onAudioFocusAbandon(secondConcurrentRequest);

        verify(mMockAudioManager)
                .dispatchAudioFocusChange(delayedFocusInfo, AUDIOFOCUS_GAIN, mAudioPolicy);
    }

    @Test
    public void requestAudioFocusWithDelayed_whileInCall_thenNav_delayedFocusNotChanged() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        AudioFocusInfo callFocusInfo = setupFocusInfoAndRequestFocusForCallRing(carAudioFocus);
        AudioFocusInfo delayedFocusInfo =
                getInfo(USAGE_NOTIFICATION, SECOND_CLIENT_ID, AUDIOFOCUS_GAIN,
                        /* acceptsDelayedFocus= */ true);
        carAudioFocus.onAudioFocusRequest(delayedFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        // Nav focus request concurrent with call (BUT REJECT delayed ring)
        AudioFocusInfo secondConcurrentRequest =
                getInfo(
                        USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
                        THIRD_CLIENT_ID,
                        AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
                        true);
        carAudioFocus.onAudioFocusRequest(secondConcurrentRequest, AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager)
                .setFocusRequestResult(
                        secondConcurrentRequest, AUDIOFOCUS_REQUEST_GRANTED, mAudioPolicy);
        verify(mMockAudioManager, never())
                .dispatchAudioFocusChange(callFocusInfo, AUDIOFOCUS_LOSS, mAudioPolicy);
        verify(mMockAudioManager, never())
                .dispatchAudioFocusChange(delayedFocusInfo, AUDIOFOCUS_LOSS, mAudioPolicy);
    }

    @Test
    public void requestAudioFocusWithDelayed_whileInCallAndNav_thenCallStop_delayedFocusLost() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        AudioFocusInfo callFocusInfo = setupFocusInfoAndRequestFocusForCallRing(carAudioFocus);
        AudioFocusInfo delayedFocusInfo =
                getInfo(USAGE_NOTIFICATION, SECOND_CLIENT_ID, AUDIOFOCUS_GAIN,
                        /* acceptsDelayedFocus= */ true);
        carAudioFocus.onAudioFocusRequest(delayedFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);
        // Nav focus request concurrent with call (BUT REJECT delayed ring)
        AudioFocusInfo secondConcurrentRequest =
                getInfo(
                        USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
                        THIRD_CLIENT_ID,
                        AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
                        /* acceptsDelayedFocus= */ true);
        carAudioFocus.onAudioFocusRequest(secondConcurrentRequest, AUDIOFOCUS_REQUEST_GRANTED);

        carAudioFocus.onAudioFocusAbandon(callFocusInfo);

        verify(mMockAudioManager, never())
                .dispatchAudioFocusChange(secondConcurrentRequest, AUDIOFOCUS_LOSS, mAudioPolicy);
        verify(mMockAudioManager)
                .dispatchAudioFocusChange(delayedFocusInfo, AUDIOFOCUS_LOSS, mAudioPolicy);
    }

    @Test
    public void requestAudioFocusWithDelayed_whileInCall_thenCallFocusRequestReplaced_noChanges() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        AudioFocusInfo callFocusInfo = setupFocusInfoAndRequestFocusForCall(carAudioFocus);
        AudioFocusInfo delayedFocusInfo = getDelayedExclusiveInfo(AUDIOFOCUS_GAIN);
        carAudioFocus.onAudioFocusRequest(delayedFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        // Same client makes the same focus request, AFI will be replaced
        carAudioFocus.onAudioFocusRequest(callFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager, times(2))
                .setFocusRequestResult(callFocusInfo, AUDIOFOCUS_REQUEST_GRANTED, mAudioPolicy);
        // No focus change expected for Call, even if the replaced request was removed.
        verify(mMockAudioManager, never())
                .dispatchAudioFocusChange(callFocusInfo, AUDIOFOCUS_LOSS, mAudioPolicy);
        // No focus change expected for delayed as well.
        verify(mMockAudioManager, never())
                .dispatchAudioFocusChange(delayedFocusInfo, AUDIOFOCUS_GAIN, mAudioPolicy);
    }

    @Test
    public void requestAudioFocus_afterReplacedFocusHolderRequestAbandon_delayedFocusGained() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        AudioFocusInfo callFocusInfo = setupFocusInfoAndRequestFocusForCall(carAudioFocus);
        AudioFocusInfo delayedFocusInfo = getDelayedExclusiveInfo(AUDIOFOCUS_GAIN);
        carAudioFocus.onAudioFocusRequest(delayedFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);
        // Same client makes the same focus request, AFI will be replaced
        carAudioFocus.onAudioFocusRequest(callFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        carAudioFocus.onAudioFocusAbandon(callFocusInfo);

        verify(mMockAudioManager)
                .dispatchAudioFocusChange(delayedFocusInfo, AUDIOFOCUS_GAIN, mAudioPolicy);
    }

    @Test
    public void onAudioFocus_delayedRequestAbandonedBeforeGettingFocus_abandonSucceeds() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();

        AudioFocusInfo callFocusInfo = setupFocusInfoAndRequestFocusForCall(carAudioFocus);

        AudioFocusInfo delayedFocusInfo = getDelayedExclusiveInfo(AUDIOFOCUS_GAIN);
        carAudioFocus.onAudioFocusRequest(delayedFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        carAudioFocus.onAudioFocusAbandon(delayedFocusInfo);

        verify(mMockAudioManager, never()).dispatchAudioFocusChange(
                delayedFocusInfo, AUDIOFOCUS_LOSS, mAudioPolicy);

        carAudioFocus.onAudioFocusAbandon(callFocusInfo);

        verify(mMockAudioManager, never()).dispatchAudioFocusChange(
                delayedFocusInfo, AUDIOFOCUS_GAIN, mAudioPolicy);
    }

    @Test
    public void onAudioFocus_forRequestDelayed_requestDelayed() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();

        setupFocusInfoAndRequestFocusForCall(carAudioFocus);

        AudioFocusInfo delayedFocusInfo = getDelayedExclusiveInfo(AUDIOFOCUS_GAIN);
        carAudioFocus.onAudioFocusRequest(delayedFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager)
                .setFocusRequestResult(delayedFocusInfo,
                        AUDIOFOCUS_REQUEST_DELAYED, mAudioPolicy);
    }

    @Test
    public void onAudioFocus_forRequestDelayed_delayedFocusGained() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();

        AudioFocusInfo callFocusInfo = setupFocusInfoAndRequestFocusForCall(carAudioFocus);

        AudioFocusInfo delayedFocusInfo = getDelayedExclusiveInfo(AUDIOFOCUS_GAIN);
        carAudioFocus.onAudioFocusRequest(delayedFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager)
                .setFocusRequestResult(delayedFocusInfo,
                        AUDIOFOCUS_REQUEST_DELAYED, mAudioPolicy);

        carAudioFocus.onAudioFocusAbandon(callFocusInfo);

        verify(mMockAudioManager)
                .dispatchAudioFocusChange(delayedFocusInfo, AUDIOFOCUS_GAIN, mAudioPolicy);
    }

    @Test
    public void onAudioFocus_multipleRequestWithDelayedFocus_requestsDelayed() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();

        setupFocusInfoAndRequestFocusForCall(carAudioFocus);

        AudioFocusInfo firstRequestWithDelayedFocus = getInfo(USAGE_MEDIA, SECOND_CLIENT_ID,
                AUDIOFOCUS_GAIN, true);
        carAudioFocus.onAudioFocusRequest(firstRequestWithDelayedFocus, AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager)
                .setFocusRequestResult(firstRequestWithDelayedFocus,
                        AUDIOFOCUS_REQUEST_DELAYED, mAudioPolicy);

        AudioFocusInfo secondRequestWithDelayedFocus = getInfo(USAGE_MEDIA, THIRD_CLIENT_ID,
                AUDIOFOCUS_GAIN, true);
        carAudioFocus.onAudioFocusRequest(secondRequestWithDelayedFocus,
                AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager)
                .setFocusRequestResult(secondRequestWithDelayedFocus,
                        AUDIOFOCUS_REQUEST_DELAYED, mAudioPolicy);
    }

    @Test
    public void onAudioFocus_multipleRequestWithDelayedFocus_firstRequestReceivesFocusLoss() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();

        setupFocusInfoAndRequestFocusForCall(carAudioFocus);

        AudioFocusInfo firstRequestWithDelayedFocus = getInfo(USAGE_MEDIA, SECOND_CLIENT_ID,
                AUDIOFOCUS_GAIN, true);
        carAudioFocus.onAudioFocusRequest(firstRequestWithDelayedFocus, AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager)
                .setFocusRequestResult(firstRequestWithDelayedFocus,
                        AUDIOFOCUS_REQUEST_DELAYED, mAudioPolicy);

        AudioFocusInfo secondRequestWithDelayedFocus = getInfo(USAGE_MEDIA, THIRD_CLIENT_ID,
                AUDIOFOCUS_GAIN, true);

        carAudioFocus.onAudioFocusRequest(secondRequestWithDelayedFocus,
                AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager).dispatchAudioFocusChange(
                firstRequestWithDelayedFocus, AUDIOFOCUS_LOSS, mAudioPolicy);
    }

    @Test
    public void onAudioFocus_multipleRequestOnlyOneWithDelayedFocus_delayedFocusNotChanged() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();

        setupFocusInfoAndRequestFocusForCall(carAudioFocus);

        AudioFocusInfo firstRequestWithDelayedFocus = getInfo(USAGE_MEDIA, SECOND_CLIENT_ID,
                AUDIOFOCUS_GAIN, true);
        carAudioFocus.onAudioFocusRequest(firstRequestWithDelayedFocus, AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager)
                .setFocusRequestResult(firstRequestWithDelayedFocus,
                        AUDIOFOCUS_REQUEST_DELAYED, mAudioPolicy);

        AudioFocusInfo secondRequestWithNoDelayedFocus = getInfo(USAGE_MEDIA, THIRD_CLIENT_ID,
                AUDIOFOCUS_GAIN, false);

        carAudioFocus.onAudioFocusRequest(secondRequestWithNoDelayedFocus,
                AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager)
                .setFocusRequestResult(secondRequestWithNoDelayedFocus,
                        AUDIOFOCUS_REQUEST_FAILED, mAudioPolicy);

        verify(mMockAudioManager, never()).dispatchAudioFocusChange(
                firstRequestWithDelayedFocus, AUDIOFOCUS_LOSS, mAudioPolicy);
    }

    @Test
    public void
            onAudioFocus_multipleRequestOnlyOneWithDelayedFocus_nonTransientRequestReceivesLoss() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();

        AudioFocusInfo mediaRequestWithOutDelayedFocus = getInfo(USAGE_MEDIA, SECOND_CLIENT_ID,
                AUDIOFOCUS_GAIN, false);
        carAudioFocus.onAudioFocusRequest(mediaRequestWithOutDelayedFocus,
                AUDIOFOCUS_REQUEST_GRANTED);

        setupFocusInfoAndRequestFocusForCall(carAudioFocus);

        AudioFocusInfo mediaRequestWithDelayedFocus = getInfo(USAGE_MEDIA, THIRD_CLIENT_ID,
                AUDIOFOCUS_GAIN, true);

        carAudioFocus.onAudioFocusRequest(mediaRequestWithDelayedFocus,
                AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager)
                .setFocusRequestResult(mediaRequestWithDelayedFocus,
                        AUDIOFOCUS_REQUEST_DELAYED, mAudioPolicy);

        verify(mMockAudioManager).dispatchAudioFocusChange(
                mediaRequestWithOutDelayedFocus, AUDIOFOCUS_LOSS, mAudioPolicy);
    }

    @Test
    public void
            onAudioFocus_multipleRequestOnlyOneWithDelayedFocus_duckedRequestReceivesLoss() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();

        AudioFocusInfo navRequestWithOutDelayedFocus =
                getInfo(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, SECOND_CLIENT_ID,
                        AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, false);
        carAudioFocus.onAudioFocusRequest(navRequestWithOutDelayedFocus,
                AUDIOFOCUS_REQUEST_GRANTED);

        setupFocusInfoAndRequestFocusForCall(carAudioFocus);

        verify(mMockAudioManager).dispatchAudioFocusChange(
                navRequestWithOutDelayedFocus, AUDIOFOCUS_LOSS_TRANSIENT, mAudioPolicy);

        AudioFocusInfo mediaRequestWithDelayedFocus = getInfo(USAGE_MEDIA, THIRD_CLIENT_ID,
                AUDIOFOCUS_GAIN, true);

        carAudioFocus.onAudioFocusRequest(mediaRequestWithDelayedFocus,
                AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager).dispatchAudioFocusChange(
                navRequestWithOutDelayedFocus, AUDIOFOCUS_LOSS, mAudioPolicy);
    }

    @Test
    public void
            onAudioFocus_concurrentRequestAfterDelayedFocus_concurrentFocusGranted() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();

        setupFocusInfoAndRequestFocusForCall(carAudioFocus);

        AudioFocusInfo delayedFocusRequest = getInfo(USAGE_MEDIA, SECOND_CLIENT_ID,
                AUDIOFOCUS_GAIN, true);

        carAudioFocus.onAudioFocusRequest(delayedFocusRequest,
                AUDIOFOCUS_REQUEST_GRANTED);

        AudioFocusInfo mapFocusInfo = getInfo(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, THIRD_CLIENT_ID,
                AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, false);
        carAudioFocus.onAudioFocusRequest(mapFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager)
                .setFocusRequestResult(mapFocusInfo,
                        AUDIOFOCUS_REQUEST_GRANTED, mAudioPolicy);
    }

    @Test
    public void
            onAudioFocus_concurrentRequestsAndAbandonsAfterDelayedFocus_noDelayedFocusChange() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();

        setupFocusInfoAndRequestFocusForCall(carAudioFocus);

        AudioFocusInfo delayedFocusRequest = getInfo(USAGE_MEDIA, SECOND_CLIENT_ID,
                AUDIOFOCUS_GAIN, true);

        carAudioFocus.onAudioFocusRequest(delayedFocusRequest,
                AUDIOFOCUS_REQUEST_GRANTED);

        AudioFocusInfo mapFocusInfo = getInfo(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, THIRD_CLIENT_ID,
                AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, false);
        carAudioFocus.onAudioFocusRequest(mapFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        carAudioFocus.onAudioFocusAbandon(mapFocusInfo);

        verify(mMockAudioManager, never()).dispatchAudioFocusChange(
                delayedFocusRequest, AUDIOFOCUS_LOSS, mAudioPolicy);

        verify(mMockAudioManager, never()).dispatchAudioFocusChange(
                delayedFocusRequest, AUDIOFOCUS_GAIN, mAudioPolicy);
    }

    @Test
    public void
            onAudioFocus_concurrentRequestAfterDelayedFocus_delayedGainesFocus() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();

        AudioFocusInfo callFocusInfo = setupFocusInfoAndRequestFocusForCall(carAudioFocus);

        AudioFocusInfo delayedFocusRequest = getInfo(USAGE_MEDIA, SECOND_CLIENT_ID,
                AUDIOFOCUS_GAIN, true);

        carAudioFocus.onAudioFocusRequest(delayedFocusRequest,
                AUDIOFOCUS_REQUEST_GRANTED);

        AudioFocusInfo mapFocusInfo = getInfo(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, THIRD_CLIENT_ID,
                AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, false);
        carAudioFocus.onAudioFocusRequest(mapFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        carAudioFocus.onAudioFocusAbandon(mapFocusInfo);

        carAudioFocus.onAudioFocusAbandon(callFocusInfo);

        verify(mMockAudioManager).dispatchAudioFocusChange(
                delayedFocusRequest, AUDIOFOCUS_GAIN, mAudioPolicy);
    }

    @Test
    public void
            onAudioFocus_delayedFocusRequestAfterDoubleReject_delayedGainesFocus() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();

        AudioFocusInfo callRingFocusInfo = getInfo(USAGE_NOTIFICATION_RINGTONE, FIRST_CLIENT_ID,
                AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, false);
        carAudioFocus.onAudioFocusRequest(callRingFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);
        verify(mMockAudioManager)
                .setFocusRequestResult(callRingFocusInfo,
                        AUDIOFOCUS_REQUEST_GRANTED, mAudioPolicy);

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setSystemUsage(USAGE_EMERGENCY)
                .build();
        AudioFocusInfo emergencyFocusInfo = getInfo(audioAttributes, SECOND_CLIENT_ID,
                AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, false);
        carAudioFocus.onAudioFocusRequest(emergencyFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);
        verify(mMockAudioManager)
                .setFocusRequestResult(emergencyFocusInfo,
                        AUDIOFOCUS_REQUEST_GRANTED, mAudioPolicy);

        verify(mMockAudioManager).dispatchAudioFocusChange(
                callRingFocusInfo, AUDIOFOCUS_LOSS_TRANSIENT, mAudioPolicy);

        AudioFocusInfo delayedFocusRequest = getInfo(USAGE_MEDIA, THIRD_CLIENT_ID,
                AUDIOFOCUS_GAIN, true);

        carAudioFocus.onAudioFocusRequest(delayedFocusRequest,
                AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager)
                .setFocusRequestResult(delayedFocusRequest,
                        AUDIOFOCUS_REQUEST_DELAYED, mAudioPolicy);

        carAudioFocus.onAudioFocusAbandon(emergencyFocusInfo);

        verify(mMockAudioManager, never()).dispatchAudioFocusChange(
                delayedFocusRequest, AUDIOFOCUS_GAIN, mAudioPolicy);

        verify(mMockAudioManager).dispatchAudioFocusChange(
                callRingFocusInfo, AUDIOFOCUS_GAIN, mAudioPolicy);

        carAudioFocus.onAudioFocusAbandon(callRingFocusInfo);

        verify(mMockAudioManager).dispatchAudioFocusChange(
                delayedFocusRequest, AUDIOFOCUS_GAIN, mAudioPolicy);

    }

    @Test
    public void getAudioFocusHolders_withNoFocusHolders_returnsEmptyList() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();

        assertThat(carAudioFocus.getAudioFocusHolders()).isEmpty();
    }

    @Test
    public void getAudioFocusHolders_withFocusHolders_returnsPopulatedList() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        AudioFocusInfo info = requestFocusForMediaWithFirstClient(carAudioFocus);
        AudioFocusInfo secondInfo = requestConcurrentFocus(carAudioFocus);

        List<AudioFocusInfo> focusHolders = carAudioFocus.getAudioFocusHolders();

        assertThat(focusHolders).containsExactly(info, secondInfo);
    }

    @Test
    public void getAudioFocusHolders_doesNotMutateList() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        AudioFocusInfo info = requestFocusForMediaWithFirstClient(carAudioFocus);

        List<AudioFocusInfo> focusHolders = carAudioFocus.getAudioFocusHolders();

        assertThat(focusHolders).containsExactly(info);

        requestConcurrentFocus(carAudioFocus);

        assertThat(focusHolders).containsExactly(info);
    }

    @Test
    public void getAudioFocusHolders_withTransientFocusLoser_doesNotIncludeTransientLoser() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        requestFocusForMediaWithFirstClient(carAudioFocus);
        AudioFocusInfo callInfo = getInfo(USAGE_VOICE_COMMUNICATION, SECOND_CLIENT_ID,
                AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, false);
        carAudioFocus.onAudioFocusRequest(callInfo, AUDIOFOCUS_REQUEST_GRANTED);

        List<AudioFocusInfo> focusHolders = carAudioFocus.getAudioFocusHolders();

        assertThat(focusHolders).containsExactly(callInfo);
    }

    @Test
    public void getAudioFocusHolders_withDelayedRequest_doesNotIncludeDelayedRequest() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        AudioFocusInfo callFocusInfo = setupFocusInfoAndRequestFocusForCall(carAudioFocus);
        AudioFocusInfo delayedFocusInfo = getDelayedExclusiveInfo(AUDIOFOCUS_GAIN);
        carAudioFocus.onAudioFocusRequest(delayedFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        List<AudioFocusInfo> focusHolders = carAudioFocus.getAudioFocusHolders();

        assertThat(focusHolders).containsExactly(callFocusInfo);
    }

    @Test
    public void getAudioFocusLosers_withNoFocusHolders_returnsEmptyList() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();

        assertThat(carAudioFocus.getAudioFocusLosers()).isEmpty();
    }

    @Test
    public void getAudioFocusLosers_withFocusHolders_returnsEmptyList() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        requestFocusForMediaWithFirstClient(carAudioFocus);
        requestConcurrentFocus(carAudioFocus);

        assertThat(carAudioFocus.getAudioFocusLosers()).isEmpty();
    }

    @Test
    public void getAudioFocusLosers_doesNotMutateList() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        requestFocusForMediaWithFirstClient(carAudioFocus);

        List<AudioFocusInfo> focusLosers = carAudioFocus.getAudioFocusLosers();

        assertThat(focusLosers).isEmpty();

        requestConcurrentFocus(carAudioFocus);

        assertThat(focusLosers).isEmpty();
    }

    @Test
    public void getAudioFocusLosers_withTransientFocusLoser_doesNotIncludeTransientLoser() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        AudioFocusInfo mediaInfo = requestFocusForMediaWithFirstClient(carAudioFocus);
        AudioFocusInfo callInfo = getInfo(USAGE_VOICE_COMMUNICATION, SECOND_CLIENT_ID,
                AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, /* acceptsDelayedFocus= */ false);
        carAudioFocus.onAudioFocusRequest(callInfo, AUDIOFOCUS_REQUEST_GRANTED);

        List<AudioFocusInfo> focusLosers = carAudioFocus.getAudioFocusLosers();

        assertThat(focusLosers).containsExactly(mediaInfo);
    }

    @Test
    public void getAudioFocusLosers_withDelayedRequest_doesNotIncludeDelayedRequest() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        setupFocusInfoAndRequestFocusForCall(carAudioFocus);
        AudioFocusInfo delayedFocusInfo = getDelayedExclusiveInfo(AUDIOFOCUS_GAIN);
        carAudioFocus.onAudioFocusRequest(delayedFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        List<AudioFocusInfo> focusLosers = carAudioFocus.getAudioFocusLosers();

        assertThat(focusLosers).isEmpty();
    }

    @Test
    public void setRestrictFocusTrue_withNonCriticalDelayedRequest_abandonsIt() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        setupFocusInfoAndRequestFocusForCall(carAudioFocus);
        AudioFocusInfo delayedFocusInfo = getDelayedExclusiveInfo(AUDIOFOCUS_GAIN);
        carAudioFocus.onAudioFocusRequest(delayedFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        carAudioFocus.setRestrictFocus(true);

        verify(mMockAudioManager).dispatchAudioFocusChange(delayedFocusInfo, AUDIOFOCUS_LOSS,
                mAudioPolicy);
    }

    @Test
    public void setRestrictFocusTrue_withNonTransientNonCriticalFocusHolder_abandonsIt() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        AudioFocusInfo nonTransientFocus = requestFocusForMediaWithFirstClient(carAudioFocus);

        carAudioFocus.setRestrictFocus(true);

        verify(mMockAudioManager, description("non-transient focus holder should have lost focus"))
                .dispatchAudioFocusChange(nonTransientFocus, AUDIOFOCUS_LOSS, mAudioPolicy);
    }

    @Test
    public void setRestrictFocusTrue_withTransientNonCriticalFocusHolders_abandonsThem() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        AudioFocusInfo transientFocus = requestConcurrentFocus(carAudioFocus
        );

        carAudioFocus.setRestrictFocus(true);

        verify(mMockAudioManager, description("transient focus holder should have lost focus"))
                .dispatchAudioFocusChange(transientFocus, AUDIOFOCUS_LOSS, mAudioPolicy);
    }


    @Test
    public void setRestrictFocusTrue_withMultipleNonCriticalFocusHolders_abandonsThem() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        requestFocusForMediaWithFirstClient(carAudioFocus);
        requestConcurrentFocus(carAudioFocus);

        carAudioFocus.setRestrictFocus(true);

        verify(mMockAudioManager, times(2))
                .dispatchAudioFocusChange(any(), eq(AUDIOFOCUS_LOSS), eq(mAudioPolicy));
    }

    @Test
    public void setRestrictFocusTrue_withCriticalFocusHolder_leavesIt() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        AudioFocusInfo emergencyInfo = getSystemUsageInfo(USAGE_EMERGENCY, AUDIOFOCUS_GAIN);
        carAudioFocus.onAudioFocusRequest(emergencyInfo, AUDIOFOCUS_REQUEST_GRANTED);

        carAudioFocus.setRestrictFocus(true);

        verify(mMockAudioManager, never()).dispatchAudioFocusChange(emergencyInfo, AUDIOFOCUS_LOSS,
                mAudioPolicy);
    }

    @Test
    public void setRestrictFocusTrue_withNonTransientNonCriticalFocusLosers_abandonsThem() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        AudioFocusInfo mediaInfo = requestFocusForMediaWithFirstClient(carAudioFocus);
        setupFocusInfoAndRequestFocusForCall(carAudioFocus, THIRD_CLIENT_ID);

        carAudioFocus.setRestrictFocus(true);

        verify(mMockAudioManager, description("non-transient focus losers should have lost focus"))
                .dispatchAudioFocusChange(mediaInfo, AUDIOFOCUS_LOSS, mAudioPolicy);
    }

    @Test
    public void setRestrictFocusTrue_withTransientNonCriticalFocusLosers_abandonsThem() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        AudioFocusInfo secondInfo = requestConcurrentFocus(carAudioFocus
        );
        setupFocusInfoAndRequestFocusForCall(carAudioFocus, THIRD_CLIENT_ID);

        carAudioFocus.setRestrictFocus(true);

        verify(mMockAudioManager, description("transient focus losers should have lost focus"))
                .dispatchAudioFocusChange(secondInfo, AUDIOFOCUS_LOSS, mAudioPolicy);
    }

    @Test
    public void setRestrictFocusTrue_withMultipleNonCriticalFocusLosers_abandonsThem() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        requestFocusForMediaWithFirstClient(carAudioFocus);
        requestConcurrentFocus(carAudioFocus);
        AudioFocusInfo emergencyInfo = getSystemUsageInfo(USAGE_EMERGENCY, AUDIOFOCUS_GAIN);
        carAudioFocus.onAudioFocusRequest(emergencyInfo, AUDIOFOCUS_REQUEST_GRANTED);

        carAudioFocus.setRestrictFocus(true);

        verify(mMockAudioManager, times(2))
                .dispatchAudioFocusChange(any(), eq(AUDIOFOCUS_LOSS), eq(mAudioPolicy));
    }

    @Test
    public void onAudioFocusRequest_withRestrictedFocus_rejectsNonCriticalUsages() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        carAudioFocus.setRestrictFocus(true);
        AudioFocusInfo mediaInfo = getInfo(USAGE_MEDIA, SECOND_CLIENT_ID, AUDIOFOCUS_GAIN, false);

        carAudioFocus.onAudioFocusRequest(mediaInfo, AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager).setFocusRequestResult(mediaInfo, AUDIOFOCUS_REQUEST_FAILED,
                mAudioPolicy);
    }

    @Test
    public void onAudioFocusRequest_withRestrictedFocus_grantsCriticalUsages() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        carAudioFocus.setRestrictFocus(true);
        AudioFocusInfo mediaInfo = getSystemUsageInfo(USAGE_EMERGENCY, AUDIOFOCUS_GAIN);

        carAudioFocus.onAudioFocusRequest(mediaInfo, AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager).setFocusRequestResult(mediaInfo, AUDIOFOCUS_REQUEST_GRANTED,
                mAudioPolicy);
    }

    @Test
    public void onAudioFocusRequest_afterUnrestrictFocus_grantsNonCriticalUsages() {
        CarAudioFocus carAudioFocus = getCarAudioFocus();
        carAudioFocus.setRestrictFocus(true);
        carAudioFocus.setRestrictFocus(false);
        AudioFocusInfo mediaInfo = getInfo(USAGE_MEDIA, SECOND_CLIENT_ID, AUDIOFOCUS_GAIN, false);

        carAudioFocus.onAudioFocusRequest(mediaInfo, AUDIOFOCUS_REQUEST_GRANTED);

        verify(mMockAudioManager).setFocusRequestResult(mediaInfo, AUDIOFOCUS_REQUEST_GRANTED,
                mAudioPolicy);
    }

    @Test
    public void onAudioFocusRequest_withOemServiceEnabled_capturesFocusRequest() {
        when(mMockCarOemProxyService.isOemServiceEnabled()).thenReturn(true);
        when(mMockCarOemProxyService.isOemServiceReady()).thenReturn(true);
        when(mMockCarOemProxyService.getCarOemAudioFocusService())
                .thenReturn(mMockAudioFocusProxyService);
        AudioFocusInfo audioFocusInfo = getInfo(USAGE_MEDIA, SECOND_CLIENT_ID, AUDIOFOCUS_GAIN,
                /* acceptsDelayedFocus= */ false);
        AudioAttributes mediaAudioAttribute =
                new AudioAttributes.Builder().setUsage(USAGE_MEDIA).build();
        AudioFocusEntry mediaEntry = new AudioFocusEntry.Builder(audioFocusInfo,
                TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(mediaAudioAttribute),
                TEST_VOLUME_GROUP, AUDIOFOCUS_GAIN).build();
        OemCarAudioFocusResult mediaResults = getAudioFocusResults(mediaEntry,
                AUDIOFOCUS_REQUEST_GRANTED);
        when(mMockAudioFocusProxyService.evaluateAudioFocusRequest(any())).thenReturn(mediaResults);
        when(mMockCarVolumeInfoWrapper.getVolumeGroupIdForAudioAttribute(PRIMARY_AUDIO_ZONE,
                mediaAudioAttribute)).thenReturn(TEST_VOLUME_GROUP);
        CarAudioFocus carAudioFocus = getCarAudioFocus();

        carAudioFocus.onAudioFocusRequest(audioFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        AudioFocusEntry entry = captureOemServiceAudioFocusEntry();
        assertWithMessage("Media focus entry info").that(entry
                .getAudioFocusInfo()).isEqualTo(audioFocusInfo);
        assertWithMessage("Media focus entry volume group").that(entry
                .getAudioVolumeGroupId()).isEqualTo(TEST_VOLUME_GROUP);
    }

    private AudioFocusEntry captureOemServiceAudioFocusEntry() {
        ArgumentCaptor<OemCarAudioFocusEvaluationRequest> captor = ArgumentCaptor
                .forClass(OemCarAudioFocusEvaluationRequest.class);
        verify(mMockAudioFocusProxyService).evaluateAudioFocusRequest(captor.capture());
        OemCarAudioFocusEvaluationRequest request = captor.getValue();
        AudioFocusEntry entry = request.getAudioFocusRequest();
        return entry;
    }

    protected OemCarAudioFocusResult getAudioFocusResults(AudioFocusEntry entry, int results) {
        return new OemCarAudioFocusResult.Builder(/* lostEntries= */ List.of(),
                /* blockedEntries= */ List.of(), results).setAudioFocusEntry(entry).build();
    }

    private List<Integer> getFocusChanges(AudioFocusInfo info) {
        ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
        verify(mMockAudioManager, atLeastOnce()).dispatchAudioFocusChange(eq(info),
                captor.capture(), any());
        return captor.getAllValues();
    }

    private AudioFocusInfo setupFocusInfoAndRequestFocusForCall(CarAudioFocus carAudioFocus) {
        return setupFocusInfoAndRequestFocusForCall(carAudioFocus, FIRST_CLIENT_ID);
    }

    private AudioFocusInfo setupFocusInfoAndRequestFocusForCall(CarAudioFocus carAudioFocus,
            String clientId) {
        AudioFocusInfo callFocusInfo = getInfo(USAGE_VOICE_COMMUNICATION, clientId,
                AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, false);
        carAudioFocus.onAudioFocusRequest(callFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);
        verify(mMockAudioManager, description("Failed get focus for call"))
                .setFocusRequestResult(callFocusInfo, AUDIOFOCUS_REQUEST_GRANTED, mAudioPolicy);
        return callFocusInfo;
    }

    private AudioFocusInfo setupFocusInfoAndRequestFocusForCallRing(CarAudioFocus carAudioFocus) {
        return setupFocusInfoAndRequestFocusForCallRing(carAudioFocus, FIRST_CLIENT_ID);
    }

    private AudioFocusInfo setupFocusInfoAndRequestFocusForCallRing(
            CarAudioFocus carAudioFocus, String clientId) {
        AudioFocusInfo callFocusInfo =
                getInfo(
                        USAGE_NOTIFICATION_RINGTONE,
                        clientId,
                        AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                        /* acceptsDelayedFocus= */ false);
        carAudioFocus.onAudioFocusRequest(callFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);
        verify(mMockAudioManager, description("Failed get focus for call"))
                .setFocusRequestResult(callFocusInfo, AUDIOFOCUS_REQUEST_GRANTED, mAudioPolicy);
        return callFocusInfo;
    }

    private AudioFocusInfo requestConcurrentFocus(CarAudioFocus carAudioFocus) {
        AudioFocusInfo concurrentInfo = getConcurrentInfo(AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        carAudioFocus.onAudioFocusRequest(concurrentInfo, AUDIOFOCUS_REQUEST_GRANTED);
        return concurrentInfo;
    }

    // USAGE_ASSISTANCE_NAVIGATION_GUIDANCE is concurrent with USAGE_MEDIA
    private AudioFocusInfo getConcurrentInfo(int gainType) {
        return getInfo(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, SECOND_CLIENT_ID, gainType,
                false);
    }

    // USAGE_VEHICLE_STATUS is concurrent with USAGE_MEDIA
    private AudioFocusInfo getConcurrentWithSystemUsageInfo() {
        return getSystemUsageInfo(USAGE_VEHICLE_STATUS, AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
    }

    // USAGE_MEDIA is exclusive with USAGE_MEDIA
    private AudioFocusInfo getExclusiveInfo(int gainType) {
        return getInfo(USAGE_MEDIA, SECOND_CLIENT_ID, gainType, false);
    }

    // USAGE_MEDIA is exclusive with USAGE_MEDIA
    private AudioFocusInfo getDelayedExclusiveInfo(int gainType) {
        return getInfo(USAGE_MEDIA, SECOND_CLIENT_ID, gainType, true);
    }

    // USAGE_EMERGENCY is exclusive with USAGE_MEDIA
    private AudioFocusInfo getExclusiveWithSystemUsageInfo() {
        return getSystemUsageInfo(USAGE_EMERGENCY, AUDIOFOCUS_GAIN);
    }

    // USAGE_ASSISTANCE_NAVIGATION_GUIDANCE is rejected with USAGE_ASSISTANT
    private AudioFocusInfo getRejectInfo() {
        return getInfo(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, SECOND_CLIENT_ID,
                AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, false);
    }

    // USAGE_ANNOUNCEMENT is rejected with USAGE_VOICE_COMMUNICATION
    private AudioFocusInfo getRejectWithSystemUsageInfo() {
        return getSystemUsageInfo(USAGE_ANNOUNCEMENT, AUDIOFOCUS_GAIN);
    }

    private AudioFocusInfo requestFocusForUsageWithFirstClient(@AttributeUsage int usage,
            CarAudioFocus carAudioFocus) {
        AudioFocusInfo initialFocusInfo = getInfo(usage, FIRST_CLIENT_ID, AUDIOFOCUS_GAIN,
                false);
        carAudioFocus.onAudioFocusRequest(initialFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);
        return initialFocusInfo;
    }

    private AudioFocusInfo requestFocusForMediaWithFirstClient(CarAudioFocus carAudioFocus) {
        return requestFocusForUsageWithFirstClient(USAGE_MEDIA, carAudioFocus);
    }

    private AudioFocusInfo getInfoForFirstClientWithMedia() {
        return getInfo(USAGE_MEDIA, FIRST_CLIENT_ID, AUDIOFOCUS_GAIN, false);
    }

    private AudioFocusInfo getInfo(@AttributeUsage int usage, String clientId, int gainType,
            boolean acceptsDelayedFocus) {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(usage)
                .build();
        return getInfo(audioAttributes, clientId, gainType, acceptsDelayedFocus);
    }

    private AudioFocusInfo getSystemUsageInfo(@AttributeUsage int systemUsage, int gainType) {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setSystemUsage(systemUsage)
                .build();
        return getInfo(audioAttributes, SECOND_CLIENT_ID, gainType, false);
    }

    private AudioFocusInfo getInfo(AudioAttributes audioAttributes, String clientId, int gainType,
            boolean acceptsDelayedFocus) {
        int flags = acceptsDelayedFocus ? AudioManager.AUDIOFOCUS_FLAG_DELAY_OK : AUDIOFOCUS_FLAG;
        return new AudioFocusInfo(audioAttributes, CLIENT_UID, clientId, PACKAGE_NAME,
                gainType, AudioManager.AUDIOFOCUS_NONE,
                flags, Build.VERSION.SDK_INT);
    }

    private CarAudioFocus getCarAudioFocus() {
        CarAudioFocus carAudioFocus = new CarAudioFocus(mMockAudioManager, mMockPackageManager,
                mFocusInteraction, TEST_CAR_AUDIO_CONTEXT, mMockCarVolumeInfoWrapper,
                PRIMARY_AUDIO_ZONE);
        carAudioFocus.setOwningPolicy(mAudioPolicy);
        return carAudioFocus;
    }
}
