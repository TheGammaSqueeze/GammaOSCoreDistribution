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

package android.wallpapereffectsgeneration.cts;

import static android.app.wallpapereffectsgeneration.CinematicEffectResponse.CINEMATIC_EFFECT_STATUS_ERROR;
import static android.app.wallpapereffectsgeneration.CinematicEffectResponse.CINEMATIC_EFFECT_STATUS_NOT_READY;
import static android.app.wallpapereffectsgeneration.CinematicEffectResponse.CINEMATIC_EFFECT_STATUS_OK;

import android.app.wallpapereffectsgeneration.CinematicEffectRequest;
import android.app.wallpapereffectsgeneration.CinematicEffectResponse;
import android.service.wallpapereffectsgeneration.WallpaperEffectsGenerationService;
import android.util.Log;

import java.util.concurrent.CountDownLatch;

/**
 * WallpaperEffectsGenerationService implementation for cts tests.
 */
public class CtsWallpaperEffectsGenerationService extends WallpaperEffectsGenerationService {
    private static final String TAG =
            "WallpaperEffectsGenerationTest["
                    + CtsWallpaperEffectsGenerationService.class.getSimpleName() + "]";
    private static final boolean DEBUG = false;
    public static final String SERVICE_NAME = "android.wallpapereffectsgeneration.cts/."
            + CtsWallpaperEffectsGenerationService.class.getSimpleName();

    private static Watcher sWatcher;

    @Override
    public void onCreate() {
        if (DEBUG) Log.d(TAG, "CtsWallpaperEffectsGenerationService onCreate");
        super.onCreate();
        sWatcher.created.countDown();
    }

    @Override
    public void onGenerateCinematicEffect(CinematicEffectRequest cinematicEffectRequest) {
        if (DEBUG) {
            Log.d(TAG, "onGenerateCinematicEffect taskId = "
                    + cinematicEffectRequest.getTaskId() + ".");
        }

        sWatcher.verifier.onGenerateCinematicEffect(cinematicEffectRequest);

        String taskId = cinematicEffectRequest.getTaskId();
        if (taskId.contains("pending")) {
            // Do nothing. Simulate it takes a long time to process.
            return;
        }
        if (taskId.contains("error")) {
            super.returnCinematicEffectResponse(
                    createCinematicEffectResponse(taskId, CINEMATIC_EFFECT_STATUS_ERROR));
        } else if (taskId.contains("initial")) {
            // Use this status code to tell the difference between initial call and calls in the
            // real test case.
            super.returnCinematicEffectResponse(
                    createCinematicEffectResponse(taskId, CINEMATIC_EFFECT_STATUS_NOT_READY));
        } else {
            super.returnCinematicEffectResponse(
                    createCinematicEffectResponse(taskId, CINEMATIC_EFFECT_STATUS_OK));
        }
        sWatcher.requested.countDown();
    }

    private CinematicEffectResponse createCinematicEffectResponse(String taskId, int status) {
        return new CinematicEffectResponse.Builder(status, taskId).build();
    }


    @Override
    public void onDestroy() {
        if (DEBUG) {
            Log.d(TAG, "onDestroy");
        }
        super.onDestroy();
        sWatcher.destroyed.countDown();
    }

    public static Watcher setWatcher() {
        if (DEBUG) {
            Log.d(TAG, " setWatcher");
        }
        if (sWatcher != null) {
            throw new IllegalStateException("Set watcher with watcher already set");
        }
        sWatcher = new Watcher();
        return sWatcher;
    }

    public static void clearWatcher() {
        if (DEBUG) Log.d(TAG, "clearWatcher");
        sWatcher = null;
    }

    public static final class Watcher {
        public CountDownLatch created = new CountDownLatch(1);
        public CountDownLatch requested = new CountDownLatch(1);
        public CountDownLatch initialCallReturned = new CountDownLatch(1);
        public CountDownLatch destroyed = new CountDownLatch(1);
        public CountDownLatch okResponse = new CountDownLatch(1);
        public CountDownLatch errorResponse = new CountDownLatch(1);
        public CountDownLatch pendingResponse = new CountDownLatch(1);
        public CountDownLatch tooManyRequestsResponse = new CountDownLatch(1);

        /**
         * Can be used to verify that API specific service methods are called. Not a real mock as
         * the system isn't talking to this directly, it has calls proxied to it.
         */
        public CtsWallpaperEffectsGenerationService verifier;
    }
}
