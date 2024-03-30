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

package android.car.oem;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.car.annotation.ApiRequirements;
import android.media.AudioAttributes;

import java.util.List;

/*
 * OemCarAudioDuckingService would expose all the method from IOemCarAudioDuckingService. It
 * should always be in sync with IOemCarAudioDuckingService. OEM will implement
 * OemCarAudioDuckingServiceInterface which would be used by OemCarAudioDuckingService.
 */

/**
 * Interface for audio ducking for OEM Service.
 *
 * @hide
 */
@SystemApi
public interface OemCarAudioDuckingService extends OemCarServiceComponent {

    /**
     * Call to evaluate a ducking change.
     *
     * <p>The results will be evaluated against the currently ducked audio attributes. Any audio
     * attribute that is currently ducked and not on the returned list will be un-ducked.
     *
     * @param requestInfo the current state of the audio service.
     *
     * @return the selected audio attribute which should be ducked.
     *
     */
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    @NonNull
    List<AudioAttributes> evaluateAttributesToDuck(@NonNull OemCarAudioVolumeRequest requestInfo);
}
