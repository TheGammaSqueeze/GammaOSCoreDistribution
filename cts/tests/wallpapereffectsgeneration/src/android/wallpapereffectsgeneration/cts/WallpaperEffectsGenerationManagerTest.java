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

/**
 * Tests for {@link android.app.wallpapereffectsgeneration.WallpaperEffectsGenerationManager}
 *
 * atest CtsWallpaperEffectsGenerationServiceTestCases
 */
package android.wallpapereffectsgeneration.cts;

import static androidx.test.InstrumentationRegistry.getContext;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.wallpapereffectsgeneration.CinematicEffectRequest;
import android.app.wallpapereffectsgeneration.CinematicEffectResponse;
import android.app.wallpapereffectsgeneration.WallpaperEffectsGenerationManager;
import android.app.wallpapereffectsgeneration.WallpaperEffectsGenerationManager.CinematicEffectListener;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.RequiredServiceRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link WallpaperEffectsGenerationManager}
 *
 * atest CtsWallpaperEffectsGenerationServiceTestCases
 */

@RunWith(AndroidJUnit4.class)
public class WallpaperEffectsGenerationManagerTest {
    private static final String TAG = "WallpaperEffectsGenerationTest";
    private static final boolean DEBUG = false;
    private static final long VERIFY_TIMEOUT_MS = 5_000;
    private static final long SERVICE_LIFECYCLE_TIMEOUT_MS = 20_000;

    @Rule
    public final RequiredServiceRule mRequiredServiceRule =
            new RequiredServiceRule(Context.WALLPAPER_EFFECTS_GENERATION_SERVICE);

    private WallpaperEffectsGenerationManager mManager;
    private CtsWallpaperEffectsGenerationService.Watcher mWatcher;
    private CinematicEffectRequest mInitialTaskRequest =
            createCinematicEffectRequest("initial-task");

    @Before
    public void setup() throws Exception {
        mWatcher = CtsWallpaperEffectsGenerationService.setWatcher();
        mManager = getContext().getSystemService(WallpaperEffectsGenerationManager.class);
        setService(CtsWallpaperEffectsGenerationService.SERVICE_NAME);
        // The wallpaper effects generation services are created lazily,
        // call one method to start the service for these tests.
        mWatcher.verifier = Mockito.mock(CtsWallpaperEffectsGenerationService.class);
        reset(mWatcher.verifier);
        mManager.generateCinematicEffect(mInitialTaskRequest,
                Executors.newSingleThreadExecutor(),
                createCinematicEffectListener());
        await(mWatcher.created, "Waiting for onCreated()");
    }

    @After
    public void tearDown() throws Exception {
        setService(null);
        await(mWatcher.destroyed, "Waiting for onDestroyed()");
        mWatcher = null;
        CtsWallpaperEffectsGenerationService.clearWatcher();
    }

    @Test
    public void testWallpaperEffectsGenerationServiceConnection() {
        // In test setup, 1st request is already made.
        assertNotNull(mManager);
        // Check the 1st call in setup was received by service.
        await(mWatcher.requested, "Waiting for requested.");
        await(mWatcher.initialCallReturned, "Result is produced");
        // Check the request the server received is the request sent.
        verifyService().onGenerateCinematicEffect(eq(mInitialTaskRequest));
    }

    @Test
    public void testGenerateCinematicEffect_okResponse() {
        mWatcher.verifier = Mockito.mock(CtsWallpaperEffectsGenerationService.class);
        reset(mWatcher.verifier);
        assertNotNull(mManager);
        // Let the initial request in setup finishes.
        await(mWatcher.requested, "Waiting for connect call finishes.");
        CinematicEffectRequest request = createSimpleCinematicEffectRequest("ok-task");

        mManager.generateCinematicEffect(request, Executors.newSingleThreadExecutor(),
                createCinematicEffectListener());
        await(mWatcher.okResponse, "Result is okay");
        verifyService().onGenerateCinematicEffect(eq(request));
    }

    @Test
    public void testGenerateCinematicEffect_errorResponse() {
        mWatcher.verifier = Mockito.mock(CtsWallpaperEffectsGenerationService.class);
        reset(mWatcher.verifier);
        assertNotNull(mManager);
        // Let the initial request in setup finishes.
        await(mWatcher.initialCallReturned, "Waiting for connect call finishes.");
        CinematicEffectRequest request = createSimpleCinematicEffectRequest("error-task");
        mManager.generateCinematicEffect(request, Executors.newSingleThreadExecutor(),
                createCinematicEffectListener());
        await(mWatcher.errorResponse, "Result is error");
        verifyService().onGenerateCinematicEffect(eq(request));
    }

    @Test
    public void testGenerateCinematicEffect_pendingResponse() {
        mWatcher.verifier = Mockito.mock(CtsWallpaperEffectsGenerationService.class);
        reset(mWatcher.verifier);
        assertNotNull(mManager);
        // Let the initial request in setup finishes.
        await(mWatcher.initialCallReturned, "Waiting for requested call finishes.");
        CinematicEffectRequest request1 = createCinematicEffectRequest("pending-task-id");
        CinematicEffectRequest request2 = createCinematicEffectRequest("pending-task-id");
        mManager.generateCinematicEffect(request1, Executors.newSingleThreadExecutor(),
                createCinematicEffectListener());
        mManager.generateCinematicEffect(request2, Executors.newSingleThreadExecutor(),
                createCinematicEffectListener());
        await(mWatcher.pendingResponse, "Second request immediately fail with pending response");
    }

    @Test
    public void testGenerateCinematicEffect_tooManyRequestsResponse() {
        mWatcher.verifier = Mockito.mock(CtsWallpaperEffectsGenerationService.class);
        reset(mWatcher.verifier);
        assertNotNull(mManager);
        // Let the initial request in setup finishes.
        await(mWatcher.initialCallReturned, "Waiting for connect call finishes.");
        CinematicEffectRequest request1 = createCinematicEffectRequest("pending-task-id");
        CinematicEffectRequest request2 = createCinematicEffectRequest("other-task-id");
        mManager.generateCinematicEffect(request1, Executors.newSingleThreadExecutor(),
                createCinematicEffectListener());
        mManager.generateCinematicEffect(request2, Executors.newSingleThreadExecutor(),
                createCinematicEffectListener());
        await(mWatcher.tooManyRequestsResponse,
                "Second request immediately fail with too many requests response");
    }

    private CinematicEffectListener createCinematicEffectListener() {
        return cinematicEffectResponse -> {
            Log.d(TAG, "cinematic effect response taskId = " + cinematicEffectResponse.getTaskId()
                    + ", status code = " + cinematicEffectResponse.getStatusCode());
            if (cinematicEffectResponse.getStatusCode()
                    == CinematicEffectResponse.CINEMATIC_EFFECT_STATUS_OK) {
                mWatcher.okResponse.countDown();
            } else if (cinematicEffectResponse.getStatusCode()
                    == CinematicEffectResponse.CINEMATIC_EFFECT_STATUS_PENDING) {
                mWatcher.pendingResponse.countDown();
            } else if (cinematicEffectResponse.getStatusCode()
                    == CinematicEffectResponse.CINEMATIC_EFFECT_STATUS_TOO_MANY_REQUESTS) {
                mWatcher.tooManyRequestsResponse.countDown();
            } else if (cinematicEffectResponse.getStatusCode()
                    == CinematicEffectResponse.CINEMATIC_EFFECT_STATUS_NOT_READY) {
                // This case is used to check the 1st request in the "Setup" method finishes.
                mWatcher.initialCallReturned.countDown();
            } else if (cinematicEffectResponse.getStatusCode()
                    == CinematicEffectResponse.CINEMATIC_EFFECT_STATUS_ERROR) {
                mWatcher.errorResponse.countDown();
            }
        };
    }

    private CinematicEffectRequest createCinematicEffectRequest(String taskId) {
        Bitmap bmp = Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888);
        return new CinematicEffectRequest(taskId, bmp);
    }

    private CtsWallpaperEffectsGenerationService verifyService() {
        return verify(mWatcher.verifier, timeout(VERIFY_TIMEOUT_MS));
    }

    private void setService(String service) {
        if (DEBUG) {
            Log.d(TAG, "Setting WallpaperEffectsGeneration service to " + service);
        }
        int userId = Process.myUserHandle().getIdentifier();
        String shellCommand = "";
        if (service != null) {
            shellCommand = "cmd wallpaper_effects_generation set temporary-service "
                    + userId + " " + service + " 60000";
        } else {
            shellCommand = "cmd wallpaper_effects_generation set temporary-service " + userId;
        }
        if (DEBUG) {
            Log.d(TAG, "runShellCommand(): " + shellCommand);
        }
        runShellCommand(shellCommand);
    }

    private void await(@NonNull CountDownLatch latch, @NonNull String message) {
        try {
            assertWithMessage(message).that(
                    latch.await(SERVICE_LIFECYCLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while: " + message);
        }
    }

    private CinematicEffectRequest createSimpleCinematicEffectRequest(String taskId) {
        return new CinematicEffectRequest(taskId,
                Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888));
    }
}
