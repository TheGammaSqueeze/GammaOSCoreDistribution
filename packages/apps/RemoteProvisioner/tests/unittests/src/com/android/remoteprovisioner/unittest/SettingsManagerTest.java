/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.remoteprovisioner.unittest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.android.remoteprovisioner.SettingsManager;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.Instant;

@RunWith(AndroidJUnit4.class)
public class SettingsManagerTest {

    private static Context sContext;

    @BeforeClass
    public static void init() {
        sContext = ApplicationProvider.getApplicationContext();
    }

    @Before
    public void setUp() {
        SettingsManager.clearPreferences(sContext);
    }

    @After
    public void tearDown() {
        SettingsManager.clearPreferences(sContext);
    }

    @Test
    public void testCheckDefaults() throws Exception {
        assertEquals(SettingsManager.EXTRA_SIGNED_KEYS_AVAILABLE_DEFAULT,
                     SettingsManager.getExtraSignedKeysAvailable(sContext));
        assertEquals(SettingsManager.EXPIRING_BY_MS_DEFAULT,
                     SettingsManager.getExpiringBy(sContext).toMillis());
        assertEquals(SettingsManager.URL_DEFAULT,
                     SettingsManager.getUrl(sContext));
        assertEquals(0, SettingsManager.getFailureCounter(sContext));
    }

    @Test
    public void testCheckIdSettings() throws Exception {
        int defaultRandom = SettingsManager.getId(sContext);
        assertTrue("Default ID out of bounds.",
                defaultRandom < SettingsManager.ID_UPPER_BOUND && defaultRandom >= 0);
        SettingsManager.generateAndSetId(sContext);
        int setId = SettingsManager.getId(sContext);
        assertTrue("Stored ID out of bounds.",
                setId < SettingsManager.ID_UPPER_BOUND && setId >= 0);
        SettingsManager.generateAndSetId(sContext);
        assertEquals("ID should not be updated by a repeated call",
                     setId, SettingsManager.getId(sContext));
    }

    @Test
    public void testResetDefaults() throws Exception {
        int extraKeys = 12;
        Duration expiringBy = Duration.ofMillis(1000);
        String url = "https://www.remoteprovisionalot";
        assertTrue("Method did not return true on write.",
                   SettingsManager.setDeviceConfig(sContext, extraKeys, expiringBy, url));
        SettingsManager.incrementFailureCounter(sContext);
        SettingsManager.resetDefaultConfig(sContext);
        assertEquals(SettingsManager.EXTRA_SIGNED_KEYS_AVAILABLE_DEFAULT,
                     SettingsManager.getExtraSignedKeysAvailable(sContext));
        assertEquals(SettingsManager.EXPIRING_BY_MS_DEFAULT,
                     SettingsManager.getExpiringBy(sContext).toMillis());
        assertEquals(SettingsManager.URL_DEFAULT,
                     SettingsManager.getUrl(sContext));
        assertEquals(0, SettingsManager.getFailureCounter(sContext));
    }

    @Test
    public void testSetDeviceConfig() {
        int extraKeys = 12;
        Duration expiringBy = Duration.ofMillis(1000);
        String url = "https://www.remoteprovisionalot";
        assertTrue("Method did not return true on write.",
                   SettingsManager.setDeviceConfig(sContext, extraKeys, expiringBy, url));
        assertEquals(extraKeys, SettingsManager.getExtraSignedKeysAvailable(sContext));
        assertEquals(expiringBy.toMillis(), SettingsManager.getExpiringBy(sContext).toMillis());
        assertEquals(url, SettingsManager.getUrl(sContext));
    }

    @Test
    public void testGetExpirationTime() {
        long expiringBy = SettingsManager.getExpiringBy(sContext).toMillis();
        long timeDif = SettingsManager.getExpirationTime(sContext).toEpochMilli()
                       - (expiringBy + System.currentTimeMillis());
        assertTrue(Math.abs(timeDif) < 1000);
    }

    @Test
    public void testFailureCounter() {
        assertEquals(1, SettingsManager.incrementFailureCounter(sContext));
        assertEquals(1, SettingsManager.getFailureCounter(sContext));
        for (int i = 1; i < 10; i++) {
            assertEquals(i + 1, SettingsManager.incrementFailureCounter(sContext));
        }
        SettingsManager.clearFailureCounter(sContext);
        assertEquals(0, SettingsManager.getFailureCounter(sContext));
        SettingsManager.incrementFailureCounter(sContext);
        assertEquals(1, SettingsManager.getFailureCounter(sContext));
    }

    @Test
    public void testDataBudgetUnused() {
        assertEquals(0, SettingsManager.getErrDataBudgetConsumed(sContext));
    }

    @Test
    public void testDataBudgetIncrement() {
        int[] bytesUsed = new int[]{1, 40, 100};
        assertEquals(0, SettingsManager.getErrDataBudgetConsumed(sContext));

        SettingsManager.consumeErrDataBudget(sContext, bytesUsed[0]);
        assertEquals(bytesUsed[0], SettingsManager.getErrDataBudgetConsumed(sContext));

        SettingsManager.consumeErrDataBudget(sContext, bytesUsed[1]);
        assertEquals(bytesUsed[0] + bytesUsed[1],
                     SettingsManager.getErrDataBudgetConsumed(sContext));

        SettingsManager.consumeErrDataBudget(sContext, bytesUsed[2]);
        assertEquals(bytesUsed[0] + bytesUsed[1] + bytesUsed[2],
                     SettingsManager.getErrDataBudgetConsumed(sContext));
    }

    @Test
    public void testDataBudgetInvalidIncrement() {
        assertEquals(0, SettingsManager.getErrDataBudgetConsumed(sContext));
        SettingsManager.consumeErrDataBudget(sContext, -20);
        assertEquals(0, SettingsManager.getErrDataBudgetConsumed(sContext));
        SettingsManager.consumeErrDataBudget(sContext, 40);
        SettingsManager.consumeErrDataBudget(sContext, -400);
        SettingsManager.consumeErrDataBudget(sContext, 60);
        assertEquals(100, SettingsManager.getErrDataBudgetConsumed(sContext));
    }

    @Test
    public void testDataBudgetReset() {
        // The first call to hasErrDataBudget will set the start of the bucket.
        assertTrue(SettingsManager.hasErrDataBudget(sContext, null /* curTime */));

        SettingsManager.consumeErrDataBudget(sContext, 100);
        assertTrue(SettingsManager.hasErrDataBudget(sContext, null));
        assertEquals(100, SettingsManager.getErrDataBudgetConsumed(sContext));

        assertTrue(SettingsManager.hasErrDataBudget(sContext,
                Instant.now().plusMillis(SettingsManager.FAILURE_DATA_USAGE_WINDOW.toMillis()
                                         + 20)));
        assertEquals(0, SettingsManager.getErrDataBudgetConsumed(sContext));
    }

    @Test
    public void testDataBudgetExceeded() {
        // The first call to hasErrDataBudget will set the start of the bucket.
        assertTrue(SettingsManager.hasErrDataBudget(sContext, null /* curTime */));
        SettingsManager.consumeErrDataBudget(sContext, SettingsManager.FAILURE_DATA_USAGE_MAX - 1);
        assertTrue(SettingsManager.hasErrDataBudget(sContext, null));
        SettingsManager.consumeErrDataBudget(sContext, 1);
        assertFalse(SettingsManager.hasErrDataBudget(sContext, null));
    }
}
