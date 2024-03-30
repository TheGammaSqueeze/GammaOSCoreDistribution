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

package com.android.cts.net.hostside.app3;

import static org.junit.Assert.assertEquals;

import android.Manifest;
import android.net.IpPrefix;
import android.net.LinkProperties;
import android.net.RouteInfo;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests to verify {@link LinkProperties#getRoutes} behavior, depending on
 * {@LinkProperties#EXCLUDED_ROUTES} change state.
 */
@RunWith(AndroidJUnit4.class)
public class ExcludedRoutesGatingTest {
    @Before
    public void setUp() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.LOG_COMPAT_CHANGE,
                        Manifest.permission.READ_COMPAT_CHANGE_CONFIG);
    }

    @After
    public void tearDown() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @Test
    public void testExcludedRoutesChangeEnabled() {
        final LinkProperties lp = makeLinkPropertiesWithExcludedRoutes();

        // Excluded routes change is enabled: non-RTN_UNICAST routes are visible.
        assertEquals(2, lp.getRoutes().size());
        assertEquals(2, lp.getAllRoutes().size());
    }

    @Test
    public void testExcludedRoutesChangeDisabled() {
        final LinkProperties lp = makeLinkPropertiesWithExcludedRoutes();

        // Excluded routes change is disabled: non-RTN_UNICAST routes are filtered out.
        assertEquals(0, lp.getRoutes().size());
        assertEquals(0, lp.getAllRoutes().size());
    }

    private LinkProperties makeLinkPropertiesWithExcludedRoutes() {
        final LinkProperties lp = new LinkProperties();

        lp.addRoute(new RouteInfo(new IpPrefix("10.0.0.0/8"), null, null, RouteInfo.RTN_THROW));
        lp.addRoute(new RouteInfo(new IpPrefix("2001:db8::/64"), null, null,
                RouteInfo.RTN_UNREACHABLE));

        return lp;
    }
}
