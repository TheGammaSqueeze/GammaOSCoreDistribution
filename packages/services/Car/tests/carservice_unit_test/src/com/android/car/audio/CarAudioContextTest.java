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

import static android.media.AudioAttributes.USAGE_ALARM;
import static android.media.AudioAttributes.USAGE_ANNOUNCEMENT;
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

import static com.android.car.audio.CarAudioContext.isCriticalAudioAudioAttribute;
import static com.android.car.audio.CarAudioContext.isNotificationAudioAttribute;
import static com.android.car.audio.CarAudioContext.isRingerOrCallAudioAttribute;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.car.builtin.media.AudioManagerHelper;
import android.media.AudioAttributes;
import android.util.ArraySet;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.audio.CarAudioContext.AudioContext;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class CarAudioContextTest {

    private static final int INVALID_CONTEXT_ID = 0;
    private static final int INVALID_CONTEXT = -5;

    private static final AudioAttributes UNKNOWN_USAGE_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_UNKNOWN);
    private static final AudioAttributes GAME_USAGE_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_GAME);
    public static final AudioAttributes TEST_CALL_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_VOICE_COMMUNICATION);
    public static final AudioAttributes TEST_RINGER_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_NOTIFICATION_RINGTONE);
    public static final AudioAttributes TEST_MEDIA_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA);
    public static final AudioAttributes TEST_EMERGENCY_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_EMERGENCY);
    public static final AudioAttributes TEST_INVALID_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(AudioManagerHelper
                    .getUsageVirtualSource());
    public static final AudioAttributes TEST_NAVIGATION_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(
                            USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);
    public static final AudioAttributes TEST_ASSISTANT_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_ASSISTANT);
    public static final AudioAttributes TEST_ALARM_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_ALARM);
    public static final AudioAttributes TEST_NOTIFICATION_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_NOTIFICATION);
    public static final AudioAttributes TEST_SYSTEM_ATTRIBUTE = CarAudioContext
            .getAudioAttributeFromUsage(USAGE_ASSISTANCE_SONIFICATION);
    public static final AudioAttributes TEST_VEHICLE_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_VEHICLE_STATUS);
    public static final AudioAttributes TEST_ANNOUNCEMENT_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_ANNOUNCEMENT);
    public static final AudioAttributes TEST_SAFETY_ATTRIBUTE = CarAudioContext
            .getAudioAttributeFromUsage(USAGE_SAFETY);
    public static final AudioAttributes TEST_NOTIFICATION_EVENT_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_NOTIFICATION_EVENT);

    public static final CarAudioContext TEST_CAR_AUDIO_CONTEXT =
            new CarAudioContext(CarAudioContext.getAllContextsInfo());

    public static final @CarAudioContext.AudioContext int TEST_MEDIA_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_MEDIA_ATTRIBUTE);
    public static final @CarAudioContext.AudioContext int TEST_ALARM_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_ALARM_ATTRIBUTE);
    public static final @CarAudioContext.AudioContext int TEST_CALL_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_CALL_ATTRIBUTE);
    public static final @CarAudioContext.AudioContext int TEST_CALL_RING_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_RINGER_ATTRIBUTE);
    public static final @CarAudioContext.AudioContext int TEST_EMERGENCY_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_EMERGENCY_ATTRIBUTE);
    public static final @CarAudioContext.AudioContext int TEST_NAVIGATION_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_NAVIGATION_ATTRIBUTE);
    public static final @CarAudioContext.AudioContext int TEST_NOTIFICATION_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_NOTIFICATION_ATTRIBUTE);
    public static final @CarAudioContext.AudioContext int TEST_ANNOUNCEMENT_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_ANNOUNCEMENT_ATTRIBUTE);
    public static final @CarAudioContext.AudioContext int TEST_SAFETY_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_SAFETY_ATTRIBUTE);
    public static final @CarAudioContext.AudioContext int TEST_SYSTEM_SOUND_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_SYSTEM_ATTRIBUTE);
    public static final @CarAudioContext.AudioContext int TEST_VEHICLE_STATUS_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_VEHICLE_ATTRIBUTE);
    public static final @CarAudioContext.AudioContext int TEST_ASSISTANT_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_ASSISTANT_ATTRIBUTE);

    @Test
    public void getContextForAudioAttributes_forAttributeWithValidUsage_returnsContext() {
        AudioAttributes attributes = new AudioAttributes.Builder().setUsage(USAGE_MEDIA).build();

        assertWithMessage("Context for valid audio attributes usage")
                .that(TEST_CAR_AUDIO_CONTEXT.getContextForAttributes(attributes))
                .isEqualTo(TEST_MEDIA_CONTEXT);
    }

    @Test
    public void getContextForAudioAttributes_forAttributesWithInvalidUsage_returnsInvalidContext() {
        assertWithMessage("Context for invalid audio attribute")
                .that(TEST_CAR_AUDIO_CONTEXT.getContextForAttributes(TEST_INVALID_ATTRIBUTE))
                .isEqualTo(CarAudioContext.getInvalidContext());
    }

    @Test
    public void getAudioAttributesForContext_withValidContext_returnsAttributes() {
        AudioAttributes[] attributes =
                TEST_CAR_AUDIO_CONTEXT.getAudioAttributesForContext(TEST_MEDIA_CONTEXT);
        assertWithMessage("Music context's audio attributes")
                .that(attributes).asList().containsExactly(UNKNOWN_USAGE_ATTRIBUTE,
                        TEST_MEDIA_ATTRIBUTE, GAME_USAGE_ATTRIBUTE);
    }

    @Test
    public void getAudioAttributesForContext_withInvalidContext_throws() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            TEST_CAR_AUDIO_CONTEXT.getAudioAttributesForContext(INVALID_CONTEXT);
        });

        assertWithMessage("Invalid context exception").that(thrown)
                .hasMessageThat().contains("Car audio context " + INVALID_CONTEXT + " is invalid");
    }

    @Test
    public void getAudioAttributesForContext_returnsUniqueValuesForAllContexts() {
        Set<CarAudioContext.AudioAttributesWrapper> allUsages = new ArraySet<>();
        for (@AudioContext int audioContext : TEST_CAR_AUDIO_CONTEXT.getAllContextsIds()) {
            AudioAttributes[] audioAttributes =
                    TEST_CAR_AUDIO_CONTEXT.getAudioAttributesForContext(audioContext);
            List<CarAudioContext.AudioAttributesWrapper> attributesWrappers =
                    Arrays.stream(audioAttributes).map(CarAudioContext.AudioAttributesWrapper::new)
                            .collect(Collectors.toList());

            assertWithMessage("Unique audio attributes wrapper for context %s",
                    TEST_CAR_AUDIO_CONTEXT.toString(audioContext))
                    .that(allUsages.addAll(attributesWrappers)).isTrue();
        }
    }

    @Test
    public void getUniqueContextsForAudioAttribute_withEmptyArray_returnsEmptySet() {
        Set<Integer> result =
                TEST_CAR_AUDIO_CONTEXT.getUniqueContextsForAudioAttributes(new ArrayList<>());

        assertWithMessage("Empty unique context list").that(result).isEmpty();
    }

    @Test
    public void getUniqueContextsForAudioAttribute_withInvalidElement_returnsEmptySet() {
        Set<Integer> result =
                TEST_CAR_AUDIO_CONTEXT.getUniqueContextsForAudioAttributes(
                        new ArrayList<>(TEST_CAR_AUDIO_CONTEXT
                                .getContextForAudioAttribute(CarAudioContext
                                        .getAudioAttributeFromUsage(AudioManagerHelper
                                                .getUsageVirtualSource()))));

        assertWithMessage("Empty unique context list for invalid context")
                .that(result).isEmpty();
    }

    @Test
    public void getUniqueContextsForAudioAttribute_withNullArray_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            TEST_CAR_AUDIO_CONTEXT.getUniqueContextsForAudioAttributes(null);
        });

        assertWithMessage("Unique contexts conversion exception")
                .that(thrown).hasMessageThat().contains("can not be null");
    }

    @Test
    public void getUniqueContextsForAudioAttributes_withMultipleAttributes_filtersDupContexts() {
        List<AudioAttributes> audioAttributes = new ArrayList<>(2);
        audioAttributes.add(CarAudioContext.getAudioAttributeFromUsage(USAGE_GAME));
        audioAttributes.add(CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA));

        Set<Integer> result = TEST_CAR_AUDIO_CONTEXT
                .getUniqueContextsForAudioAttributes(audioAttributes);

        assertWithMessage("Media and Game audio attribute's context")
                .that(result).containsExactly(TEST_MEDIA_CONTEXT);
    }

    @Test
    public void getUniqueContextsForAudioAttributes_withDiffAttributes_returnsAllUniqueContexts() {
        List<AudioAttributes> audioAttributes = new ArrayList<>(3);
        audioAttributes.add(CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA));
        audioAttributes.add(CarAudioContext.getAudioAttributeFromUsage(USAGE_EMERGENCY));
        audioAttributes.add(TEST_NAVIGATION_ATTRIBUTE);

        Set<Integer> result =
                TEST_CAR_AUDIO_CONTEXT.getUniqueContextsForAudioAttributes(audioAttributes);

        assertWithMessage("Separate audio attribute's contexts")
                .that(result).containsExactly(TEST_MEDIA_CONTEXT,
                        TEST_NAVIGATION_CONTEXT,
                        TEST_EMERGENCY_CONTEXT);
    }

    @Test
    public void getUniqueAttributesHoldingFocus_withNoAttributes_returnsEmpty() {
        Set<Integer> contexts =
                TEST_CAR_AUDIO_CONTEXT.getUniqueContextsForAudioAttributes(new ArrayList<>());

        assertWithMessage("Empty unique contexts set")
                .that(contexts).isEmpty();
    }

    @Test
    public void getUniqueAttributesHoldingFocus_withDuplicates_returnsSetWithNoDuplicates() {
        List<AudioAttributes> audioAttributes = new ArrayList<>(/* initialCapacity= */ 3);
        audioAttributes.add(TEST_NOTIFICATION_ATTRIBUTE);
        audioAttributes.add(TEST_MEDIA_ATTRIBUTE);
        audioAttributes.add(TEST_NOTIFICATION_ATTRIBUTE);

        Set<Integer> contexts =
                TEST_CAR_AUDIO_CONTEXT.getUniqueContextsForAudioAttributes(audioAttributes);

        assertWithMessage("Non duplicates unique contexts set")
                .that(contexts).containsExactly(TEST_MEDIA_CONTEXT,
                        TEST_NOTIFICATION_CONTEXT);
    }

    @Test
    public void getUniqueAttributesHoldingFocus_withSystemAudioAttributes_retSystemContext() {
        List<AudioAttributes> audioAttributes = new ArrayList<>(/* initialCapacity= */ 3);
        audioAttributes.add(CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA));
        audioAttributes.add(CarAudioContext.getAudioAttributeFromUsage(USAGE_SAFETY));
        audioAttributes.add(CarAudioContext.getAudioAttributeFromUsage(USAGE_EMERGENCY));

        Set<Integer> contexts =
                TEST_CAR_AUDIO_CONTEXT.getUniqueContextsForAudioAttributes(audioAttributes);

        assertWithMessage("Non duplicates unique contexts set")
                .that(contexts).containsExactly(TEST_MEDIA_CONTEXT,
                        TEST_SAFETY_CONTEXT,
                        TEST_EMERGENCY_CONTEXT);
    }

    @Test
    public void getUniqueAttributesHoldingFocus_withInvalidAttribute_returnsEmpty() {
        List<AudioAttributes> audioAttributes = new ArrayList<>(/* initialCapacity= */ 1);
        audioAttributes.add(CarAudioContext
                .getAudioAttributeFromUsage(AudioManagerHelper.getUsageVirtualSource()));

        Set<Integer> contexts =
                TEST_CAR_AUDIO_CONTEXT.getUniqueContextsForAudioAttributes(audioAttributes);

        assertWithMessage("Unique contexts without invalid")
                .that(contexts).isEmpty();
    }

    @Test
    public void isCriticalAudioContext_forNonCriticalContexts_returnsFalse() {
        assertWithMessage("Non-critical context INVALID")
                .that(isCriticalAudioAudioAttribute(TEST_INVALID_ATTRIBUTE)).isFalse();
        assertWithMessage("Non-critical context MUSIC")
                .that(isCriticalAudioAudioAttribute(TEST_MEDIA_ATTRIBUTE)).isFalse();
        assertWithMessage("Non-critical context NAVIGATION")
                .that(isCriticalAudioAudioAttribute(TEST_NAVIGATION_ATTRIBUTE)).isFalse();
        assertWithMessage("Non-critical context VOICE_COMMAND")
                .that(isCriticalAudioAudioAttribute(TEST_ASSISTANT_ATTRIBUTE)).isFalse();
        assertWithMessage("Non-critical context CALL_RING")
                .that(isCriticalAudioAudioAttribute(TEST_RINGER_ATTRIBUTE)).isFalse();
        assertWithMessage("Non-critical context CALL")
                .that(isCriticalAudioAudioAttribute(TEST_CALL_ATTRIBUTE)).isFalse();
        assertWithMessage("Non-critical context ALARM")
                .that(isCriticalAudioAudioAttribute(TEST_ALARM_ATTRIBUTE)).isFalse();
        assertWithMessage("Non-critical context NOTIFICATION")
                .that(isCriticalAudioAudioAttribute(TEST_NOTIFICATION_ATTRIBUTE)).isFalse();
        assertWithMessage("Non-critical context SYSTEM_SOUND")
                .that(isCriticalAudioAudioAttribute(TEST_SYSTEM_ATTRIBUTE)).isFalse();
        assertWithMessage("Non-critical context VEHICLE_STATUS")
                .that(isCriticalAudioAudioAttribute(TEST_VEHICLE_ATTRIBUTE)).isFalse();
        assertWithMessage("Non-critical context ANNOUNCEMENT")
                .that(isCriticalAudioAudioAttribute(TEST_ANNOUNCEMENT_ATTRIBUTE)).isFalse();
    }

    @Test
    public void isCriticalAudioContext_forCriticalContexts_returnsTrue() {
        assertWithMessage("Critical context EMERGENCY")
                .that(isCriticalAudioAudioAttribute(TEST_EMERGENCY_ATTRIBUTE)).isTrue();
        assertWithMessage("Critical context SAFETY")
                .that(isCriticalAudioAudioAttribute(TEST_SAFETY_ATTRIBUTE)).isTrue();
    }

    @Test
    public void isNotificationAudioAttribute_forNonNotification_returnsFalse() {
        assertWithMessage("Non Notification attribute INVALID")
                .that(isNotificationAudioAttribute(TEST_INVALID_ATTRIBUTE)).isFalse();
        assertWithMessage("Non notification attribute MUSIC")
                .that(isNotificationAudioAttribute(TEST_MEDIA_ATTRIBUTE)).isFalse();
        assertWithMessage("Non notification attribute NAVIGATION")
                .that(isNotificationAudioAttribute(TEST_NAVIGATION_ATTRIBUTE)).isFalse();
        assertWithMessage("Non notification attribute VOICE_COMMAND")
                .that(isNotificationAudioAttribute(TEST_ASSISTANT_ATTRIBUTE)).isFalse();
        assertWithMessage("Non notification attribute ALARM")
                .that(isNotificationAudioAttribute(TEST_ALARM_ATTRIBUTE)).isFalse();
        assertWithMessage("Non notification attribute SYSTEM_SOUND")
                .that(isNotificationAudioAttribute(TEST_SYSTEM_ATTRIBUTE)).isFalse();
        assertWithMessage("Non notification attribute VEHICLE_STATUS")
                .that(isNotificationAudioAttribute(TEST_VEHICLE_ATTRIBUTE)).isFalse();
        assertWithMessage("Non notification attribute ANNOUNCEMENT")
                .that(isNotificationAudioAttribute(TEST_ANNOUNCEMENT_ATTRIBUTE)).isFalse();
        assertWithMessage("Non Notification attribute EMERGENCY")
                .that(isNotificationAudioAttribute(TEST_EMERGENCY_ATTRIBUTE)).isFalse();
        assertWithMessage("Non Notification attribute SAFETY")
                .that(isNotificationAudioAttribute(TEST_SAFETY_ATTRIBUTE)).isFalse();
    }

    @Test
    public void isNotificationAudioAttribute_forNotification_returnsTrue() {
        assertWithMessage("Notification attribute NOTIFICATION")
                .that(isNotificationAudioAttribute(TEST_NOTIFICATION_ATTRIBUTE)).isTrue();
        assertWithMessage("Notification attribute MUSIC")
                .that(isNotificationAudioAttribute(TEST_NOTIFICATION_EVENT_ATTRIBUTE)).isTrue();
    }

    @Test
    public void isRingerOrCallAudioAttribute_forNonCall_returnsFalse() {
        assertWithMessage("Non call attribute INVALID")
                .that(isRingerOrCallAudioAttribute(TEST_INVALID_ATTRIBUTE)).isFalse();
        assertWithMessage("Non call attribute MUSIC")
                .that(isRingerOrCallAudioAttribute(TEST_MEDIA_ATTRIBUTE)).isFalse();
        assertWithMessage("Non call attribute NAVIGATION")
                .that(isRingerOrCallAudioAttribute(TEST_NAVIGATION_ATTRIBUTE)).isFalse();
        assertWithMessage("Non call attribute VOICE_COMMAND")
                .that(isRingerOrCallAudioAttribute(TEST_ASSISTANT_ATTRIBUTE)).isFalse();
        assertWithMessage("Non call attribute ALARM")
                .that(isRingerOrCallAudioAttribute(TEST_ALARM_ATTRIBUTE)).isFalse();
        assertWithMessage("Non call attribute SYSTEM_SOUND")
                .that(isRingerOrCallAudioAttribute(TEST_SYSTEM_ATTRIBUTE)).isFalse();
        assertWithMessage("Non call attribute VEHICLE_STATUS")
                .that(isRingerOrCallAudioAttribute(TEST_VEHICLE_ATTRIBUTE)).isFalse();
        assertWithMessage("Non call attribute ANNOUNCEMENT")
                .that(isRingerOrCallAudioAttribute(TEST_ANNOUNCEMENT_ATTRIBUTE)).isFalse();
        assertWithMessage("Non call attribute EMERGENCY")
                .that(isRingerOrCallAudioAttribute(TEST_EMERGENCY_ATTRIBUTE)).isFalse();
        assertWithMessage("Non call attribute SAFETY")
                .that(isRingerOrCallAudioAttribute(TEST_SAFETY_ATTRIBUTE)).isFalse();
        assertWithMessage("Non call attribute NOTIFICATION")
                .that(isRingerOrCallAudioAttribute(TEST_NOTIFICATION_ATTRIBUTE)).isFalse();
        assertWithMessage("Non call attribute NOTIFICATION_EVENT")
                .that(isRingerOrCallAudioAttribute(TEST_NOTIFICATION_EVENT_ATTRIBUTE)).isFalse();
    }

    @Test
    public void isRingerOrCallAudioAttribute_forCallOrRinger_returnsTrue() {
        assertWithMessage("Non call attribute CALL")
                .that(isRingerOrCallAudioAttribute(TEST_CALL_ATTRIBUTE)).isTrue();
        assertWithMessage("Non call attribute CALL_RING")
                .that(isRingerOrCallAudioAttribute(TEST_RINGER_ATTRIBUTE)).isTrue();
    }

    @Test
    public void getAudioAttributeWrapperFromUsage_withNonCriticalUsage_succeeds() {
        CarAudioContext.AudioAttributesWrapper wrapper =
                CarAudioContext.getAudioAttributeWrapperFromUsage(USAGE_MEDIA);

        assertWithMessage("Non critical audio attributes for wrapper")
                .that(wrapper.getAudioAttributes()).isEqualTo(TEST_MEDIA_ATTRIBUTE);
    }

    @Test
    public void getAudioAttributeWrapperFromUsage_withNonCriticalUsage_toString_succeeds() {
        CarAudioContext.AudioAttributesWrapper wrapper =
                CarAudioContext.getAudioAttributeWrapperFromUsage(USAGE_MEDIA);

        assertWithMessage("Non critical audio attributes for wrapper string")
                .that(wrapper.toString()).isEqualTo(TEST_MEDIA_ATTRIBUTE.toString());
    }

    @Test
    public void getAudioAttributeWrapperFromUsage_withNonCriticalUsage_equals_succeeds() {
        CarAudioContext.AudioAttributesWrapper wrapper =
                new CarAudioContext.AudioAttributesWrapper(TEST_MEDIA_ATTRIBUTE);

        CarAudioContext.AudioAttributesWrapper createdWrapper =
                CarAudioContext.getAudioAttributeWrapperFromUsage(USAGE_MEDIA);

        assertWithMessage("Non critical audio attributes wrapper is equal check")
                .that(createdWrapper.equals(wrapper)).isTrue();
    }

    @Test
    public void getAudioAttributeWrapperFromUsage_withNonCriticalUsage_hashCode_succeeds() {
        CarAudioContext.AudioAttributesWrapper createdWrapper =
                CarAudioContext.getAudioAttributeWrapperFromUsage(USAGE_MEDIA);

        assertWithMessage("Non critical audio attributes wrapper hash code")
                .that(createdWrapper.hashCode()).isEqualTo(Integer.hashCode(USAGE_MEDIA));
    }

    @Test
    public void getAudioAttributeWrapperFromUsage_withCriticalUsage_succeeds() {
        CarAudioContext.AudioAttributesWrapper wrapper =
                CarAudioContext.getAudioAttributeWrapperFromUsage(USAGE_EMERGENCY);

        assertWithMessage("Critical audio attributes for wrapper")
                .that(wrapper.getAudioAttributes()).isEqualTo(TEST_EMERGENCY_ATTRIBUTE);
    }

    @Test
    public void getAudioAttributeWrapperFromUsage_withCriticalUsage_toString_succeeds() {
        CarAudioContext.AudioAttributesWrapper wrapper =
                CarAudioContext.getAudioAttributeWrapperFromUsage(USAGE_EMERGENCY);

        assertWithMessage("Critical audio attributes for wrapper string")
                .that(wrapper.toString()).isEqualTo(TEST_EMERGENCY_ATTRIBUTE.toString());
    }

    @Test
    public void getAudioAttributeWrapperFromUsage_withCriticalUsage_equals_succeeds() {
        CarAudioContext.AudioAttributesWrapper wrapper =
                new CarAudioContext.AudioAttributesWrapper(TEST_EMERGENCY_ATTRIBUTE);

        CarAudioContext.AudioAttributesWrapper createdWrapper =
                CarAudioContext.getAudioAttributeWrapperFromUsage(USAGE_EMERGENCY);

        assertWithMessage("Critical audio attributes wrapper is equal check")
                .that(createdWrapper.equals(wrapper)).isTrue();
    }

    @Test
    public void getAudioAttributeWrapperFromUsage_withCriticalUsage_hashCode_succeeds() {
        CarAudioContext.AudioAttributesWrapper createdWrapper =
                CarAudioContext.getAudioAttributeWrapperFromUsage(USAGE_EMERGENCY);

        assertWithMessage("Critical audio attributes wrapper hash code")
                .that(createdWrapper.hashCode()).isEqualTo(Integer.hashCode(USAGE_EMERGENCY));
    }

    @Test
    public void getAudioAttributeFromUsage_withNonCriticalUsage_succeeds() {
        AudioAttributes attributes = new AudioAttributes.Builder().setUsage(USAGE_MEDIA).build();

        AudioAttributes createdAttributes = CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA);

        assertWithMessage("Non critical audio attributes")
                .that(createdAttributes).isEqualTo(attributes);
    }

    @Test
    public void getAudioAttributeFromUsage_withCriticalUsage_succeeds() {
        AudioAttributes attributes =
                new AudioAttributes.Builder().setSystemUsage(USAGE_EMERGENCY).build();

        AudioAttributes createdAttributes =
                CarAudioContext.getAudioAttributeFromUsage(USAGE_EMERGENCY);

        assertWithMessage("Critical audio attributes")
                .that(createdAttributes).isEqualTo(attributes);
    }

    @Test
    public void isRingerOrCallContext_withCallContext_returnsTrue() {
        boolean isRingerOrCall = isRingerOrCallAudioAttribute(TEST_CALL_ATTRIBUTE);

        assertWithMessage("Is call check")
                .that(isRingerOrCall).isTrue();
    }

    @Test
    public void isRingerOrCallContext_withRingerContext_returnsTrue() {
        boolean isRingerOrCall = isRingerOrCallAudioAttribute(TEST_RINGER_ATTRIBUTE);

        assertWithMessage("Is ringer check")
                .that(isRingerOrCall).isTrue();
    }

    @Test
    public void isRingerOrCallContext_withNonCriticalContext_returnsFalse() {
        boolean isRingerOrCall = isRingerOrCallAudioAttribute(TEST_MEDIA_ATTRIBUTE);

        assertWithMessage("Non critical context is ringer or call check")
                .that(isRingerOrCall).isFalse();
    }

    @Test
    public void isRingerOrCallContext_withCriticalContext_returnsFalse() {
        boolean isRingerOrCall = isRingerOrCallAudioAttribute(TEST_EMERGENCY_ATTRIBUTE);

        assertWithMessage("Critical context is ringer or call check")
                .that(isRingerOrCall).isFalse();
    }

    @Test
    public void preconditionCheckAudioContext_withNonExistentContext_throws() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            TEST_CAR_AUDIO_CONTEXT.preconditionCheckAudioContext(-TEST_EMERGENCY_CONTEXT);
        });

        assertWithMessage("Precondition exception with non existent context check")
                .that(thrown).hasMessageThat()
                .contains("Car audio context " + -TEST_EMERGENCY_CONTEXT + " is invalid");
    }

    @Test
    public void preconditionCheckAudioContext_withInvalidContext_throws() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            TEST_CAR_AUDIO_CONTEXT.preconditionCheckAudioContext(INVALID_CONTEXT);
        });

        assertWithMessage("Precondition exception with invalid context check")
                .that(thrown).hasMessageThat()
                .contains("Car audio context " + INVALID_CONTEXT + " is invalid");
    }

    @Test
    public void getSystemUsages_returnsAllSystemUsages() {
        int[] systemUsages = CarAudioContext.getSystemUsages();

        assertWithMessage("System Usages")
                .that(systemUsages).asList().containsExactly(
                        USAGE_CALL_ASSISTANT,
                        USAGE_EMERGENCY,
                        USAGE_SAFETY,
                        USAGE_VEHICLE_STATUS,
                        USAGE_ANNOUNCEMENT);
    }

    @Test
    public void toString_forNonSystemSoundsContexts_returnsStrings() {
        assertWithMessage("Context String for INVALID")
                .that(TEST_CAR_AUDIO_CONTEXT.toString(CarAudioContext.getInvalidContext()))
                .isEqualTo("INVALID");
        assertWithMessage("Context String for MUSIC")
                .that(TEST_CAR_AUDIO_CONTEXT.toString(TEST_MEDIA_CONTEXT)).isEqualTo("MUSIC");
        assertWithMessage("Context String for NAVIGATION")
                .that(TEST_CAR_AUDIO_CONTEXT.toString(TEST_NAVIGATION_CONTEXT))
                .isEqualTo("NAVIGATION");
        assertWithMessage("Context String for VOICE_COMMAND")
                .that(TEST_CAR_AUDIO_CONTEXT.toString(TEST_ASSISTANT_CONTEXT))
                .isEqualTo("VOICE_COMMAND");
        assertWithMessage("Context String for CALL_RING")
                .that(TEST_CAR_AUDIO_CONTEXT.toString(TEST_CALL_RING_CONTEXT))
                .isEqualTo("CALL_RING");
        assertWithMessage("Context String for CALL")
                .that(TEST_CAR_AUDIO_CONTEXT.toString(TEST_CALL_CONTEXT)).isEqualTo("CALL");
        assertWithMessage("Context String for ALARM")
                .that(TEST_CAR_AUDIO_CONTEXT.toString(TEST_ALARM_CONTEXT)).isEqualTo("ALARM");
        assertWithMessage("Context String for NOTIFICATION")
                .that(TEST_CAR_AUDIO_CONTEXT.toString(TEST_NOTIFICATION_CONTEXT))
                .isEqualTo("NOTIFICATION");
    }

    @Test
    public void toString_forSystemSoundsContexts_returnsStrings() {
        assertWithMessage("Context String for SYSTEM_SOUND")
                .that(TEST_CAR_AUDIO_CONTEXT.toString(TEST_SYSTEM_SOUND_CONTEXT))
                .isEqualTo("SYSTEM_SOUND");
        assertWithMessage("Context String for EMERGENCY")
                .that(TEST_CAR_AUDIO_CONTEXT.toString(TEST_EMERGENCY_CONTEXT))
                .isEqualTo("EMERGENCY");
        assertWithMessage("Context String for SAFETY")
                .that(TEST_CAR_AUDIO_CONTEXT.toString(TEST_SAFETY_CONTEXT)).isEqualTo("SAFETY");
        assertWithMessage("Context String for VEHICLE_STATUS")
                .that(TEST_CAR_AUDIO_CONTEXT.toString(TEST_VEHICLE_STATUS_CONTEXT))
                .isEqualTo("VEHICLE_STATUS");
        assertWithMessage("Context String for ANNOUNCEMENT")
                .that(TEST_CAR_AUDIO_CONTEXT.toString(TEST_ANNOUNCEMENT_CONTEXT))
                .isEqualTo("ANNOUNCEMENT");
    }

    @Test
    public void toString_forInvalidContext_returnsUnsupportedContext() {
        assertWithMessage("Context String for invalid context")
                .that(TEST_CAR_AUDIO_CONTEXT.toString(/* context= */ -1))
                .contains("Unsupported Context");
    }

    @Test
    public void getAllContextIds_returnsAllContext() {
        assertWithMessage("All context IDs")
                .that(TEST_CAR_AUDIO_CONTEXT.getAllContextsIds())
                .containsExactly(TEST_MEDIA_CONTEXT,
                        TEST_NAVIGATION_CONTEXT,
                        TEST_ASSISTANT_CONTEXT,
                        TEST_CALL_RING_CONTEXT,
                        TEST_CALL_CONTEXT,
                        TEST_ALARM_CONTEXT,
                        TEST_NOTIFICATION_CONTEXT,
                        TEST_SYSTEM_SOUND_CONTEXT,
                        TEST_EMERGENCY_CONTEXT,
                        TEST_SAFETY_CONTEXT,
                        TEST_VEHICLE_STATUS_CONTEXT,
                        TEST_ANNOUNCEMENT_CONTEXT);
    }

    @Test
    public void getAllContextIds_failsForInvalid() {
        assertWithMessage("All context IDs")
                .that(TEST_CAR_AUDIO_CONTEXT.getAllContextsIds())
                .doesNotContain(CarAudioContext.getInvalidContext());
    }

    @Test
    public void getCarSystemContextIds() {
        List<Integer> systemContextIds = CarAudioContext.getCarSystemContextIds();

        assertWithMessage("Car audio system contexts")
                .that(systemContextIds)
                .containsExactly(TEST_EMERGENCY_CONTEXT, TEST_SAFETY_CONTEXT,
                        TEST_VEHICLE_STATUS_CONTEXT, TEST_ANNOUNCEMENT_CONTEXT);
    }

    @Test
    public void getNonCarSystemContextIds() {
        List<Integer> nonCarSystemContextIds = CarAudioContext.getNonCarSystemContextIds();

        assertWithMessage("Car audio non system contexts")
                .that(nonCarSystemContextIds)
                .containsExactly(TEST_MEDIA_CONTEXT, TEST_NAVIGATION_CONTEXT,
                        TEST_ASSISTANT_CONTEXT, TEST_CALL_RING_CONTEXT,
                        TEST_CALL_CONTEXT,
                        TEST_ALARM_CONTEXT, TEST_NOTIFICATION_CONTEXT,
                        TEST_SYSTEM_SOUND_CONTEXT);
    }

    @Test
    public void validateAllAudioAttributesSupported() {
        boolean valid = TEST_CAR_AUDIO_CONTEXT.validateAllAudioAttributesSupported(
                TEST_CAR_AUDIO_CONTEXT.getAllContextsIds());

        assertWithMessage("All audio attributes are supported flag")
                .that(valid).isTrue();
    }

    @Test
    public void validateAllAudioAttributesSupported_forNonCarSystemContextsOnly_fails() {
        boolean valid = TEST_CAR_AUDIO_CONTEXT.validateAllAudioAttributesSupported(
                CarAudioContext.getNonCarSystemContextIds());

        assertWithMessage("Missing car audio system audio attributes are supported flag")
                .that(valid).isFalse();
    }

    @Test
    public void validateAllAudioAttributesSupported_forCarSystemContextsOnly_fails() {
        boolean valid = TEST_CAR_AUDIO_CONTEXT.validateAllAudioAttributesSupported(
                CarAudioContext.getCarSystemContextIds());

        assertWithMessage("Missing non car audio system audio attributes are supported flag")
                .that(valid).isFalse();
    }

    @Test
    public void getAllContextsInfo() {
        Set<Integer> allContextIds =
                new ArraySet<Integer>(TEST_CAR_AUDIO_CONTEXT.getAllContextsIds());
        allContextIds.add(CarAudioContext.getInvalidContext());

        List<CarAudioContextInfo> contextInfos = CarAudioContext.getAllContextsInfo();

        for (CarAudioContextInfo info : contextInfos) {
            assertWithMessage("Context info id for %s", info)
                    .that(info.getId()).isIn(allContextIds);
        }
    }

    @Test
    public void getAllContextsInfo_sameSizeAsGetAllContextsIds() {
        Set<Integer> allContextIds =
                new ArraySet<Integer>(TEST_CAR_AUDIO_CONTEXT.getAllContextsIds());
        allContextIds.add(CarAudioContext.getInvalidContext());

        List<CarAudioContextInfo> contextInfos = CarAudioContext.getAllContextsInfo();

        assertWithMessage("All contexts info size")
                .that(contextInfos.size()).isEqualTo(allContextIds.size());
    }

    @Test
    public void getInvalidContext() {
        assertWithMessage("Invalid context id")
                .that(CarAudioContext.getInvalidContext()).isEqualTo(INVALID_CONTEXT_ID);
    }

    @Test
    public void isInvalidContext() {
        assertWithMessage("Is invalid context id")
                .that(CarAudioContext.isInvalidContextId(INVALID_CONTEXT_ID)).isTrue();
    }

    @Test
    public void constructor_withNullContextInfos_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> new CarAudioContext(/* carAudioContexts= */ null));

        assertWithMessage("Constructor exception")
                .that(thrown).hasMessageThat()
                .contains("Car audio contexts");
    }

    @Test
    public void constructor_withEmptyContextInfos_fails() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new CarAudioContext(/* carAudioContexts= */ Collections.EMPTY_LIST));

        assertWithMessage("Empty list constructor exception")
                .that(thrown).hasMessageThat()
                .contains("Car audio contexts must not be empty");
    }

    @Test
    public void getAllCarSystemContextInfo_verifyContents() {
        Set<Integer> carContextIds =
                new ArraySet<Integer>(TEST_CAR_AUDIO_CONTEXT.getCarSystemContextIds());

        List<CarAudioContextInfo> carContextInfo = CarAudioContext.getAllCarSystemContextsInfo();

        assertWithMessage("Car system context info size").that(carContextInfo)
                .hasSize(carContextIds.size());
        for (CarAudioContextInfo info : carContextInfo) {
            assertWithMessage("Context info id for %s", info)
                    .that(info.getId()).isIn(carContextIds);
        }
    }

    @Test
    public void getAllNonCarSystemContextInfo_verifyContents() {
        Set<Integer> nonCarContextIds =
                new ArraySet<Integer>(TEST_CAR_AUDIO_CONTEXT.getNonCarSystemContextIds());

        List<CarAudioContextInfo> nonCarContextInfo =
                CarAudioContext.getAllNonCarSystemContextsInfo();

        assertWithMessage("Non car system context info size").that(nonCarContextInfo)
                .hasSize(nonCarContextIds.size());
        for (CarAudioContextInfo info : nonCarContextInfo) {
            assertWithMessage("Context info id for %s", info)
                    .that(info.getId()).isIn(nonCarContextIds);
        }
    }

    @Test
    public void evaluateAttributesToDuck() {
        List<AudioAttributes> focusHolders = Collections.EMPTY_LIST;

        List<AudioAttributes> attributesToDuck =
                CarAudioContext.evaluateAudioAttributesToDuck(focusHolders);

        assertWithMessage("Audio attributes to duck").that(attributesToDuck).isEmpty();
    }

    @Test
    public void evaluateAudioAttributesToDuck_withMedia() {
        List<AudioAttributes> focusHolders = List.of(TEST_MEDIA_ATTRIBUTE);

        List<AudioAttributes> attributesToDuck =
                CarAudioContext.evaluateAudioAttributesToDuck(focusHolders);

        assertWithMessage("Audio attributes to duck with media")
                .that(attributesToDuck).isEmpty();
    }

    @Test
    public void evaluateAudioAttributesToDuck_withCallAndEmergency() {
        List<AudioAttributes> focusHolders =
                List.of(TEST_EMERGENCY_ATTRIBUTE, TEST_CALL_ATTRIBUTE);

        List<AudioAttributes> attributesToDuck =
                CarAudioContext.evaluateAudioAttributesToDuck(focusHolders);

        assertWithMessage("Audio attributes to duck with media and emergency")
                .that(attributesToDuck).containsExactly(TEST_CALL_ATTRIBUTE);
    }
}
