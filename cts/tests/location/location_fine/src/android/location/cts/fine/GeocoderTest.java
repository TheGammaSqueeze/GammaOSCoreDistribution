/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.location.cts.fine;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.location.Geocoder;
import android.location.Geocoder.GeocodeListener;
import android.platform.test.annotations.AppModeFull;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.RetryRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

@RunWith(AndroidJUnit4.class)
public class GeocoderTest {

    // retry just in case of network failure
    @Rule
    public final RetryRule mRetryRule = new RetryRule(2);

    private Context mContext;
    private Geocoder mGeocoder;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mGeocoder = new Geocoder(mContext, Locale.US);
    }

    @ApiTest(apis = "android.location.Geocoder#getFromLocation")
    @AppModeFull(reason = "b/238831704 - Test cases don't apply for Instant apps")
    @Test
    public void testGetFromLocation() {
        assumeTrue(Geocoder.isPresent());

        GeocodeListener listener = mock(GeocodeListener.class);
        mGeocoder.getFromLocation(60, 30, 5, listener);
        verify(listener, timeout(10000)).onGeocode(anyList());
    }

    @ApiTest(apis = "android.location.Geocoder#getFromLocation")
    @AppModeFull(reason = "b/238831704 - Test cases don't apply for Instant apps")
    @Test
    public void testGetFromLocation_sync() throws Exception {
        assumeTrue(Geocoder.isPresent());

        mGeocoder.getFromLocation(60, 30, 5);
    }

    @ApiTest(apis = "android.location.Geocoder#getFromLocation")
    @AppModeFull(reason = "b/238831704 - Test cases don't apply for Instant apps")
    @Test
    public void testGetFromLocation_badInput() {
        GeocodeListener listener = mock(GeocodeListener.class);
        assertThrows(IllegalArgumentException.class,
                () -> mGeocoder.getFromLocation(-91, 30, 5, listener));
        assertThrows(IllegalArgumentException.class,
                () -> mGeocoder.getFromLocation(91, 30, 5, listener));
        assertThrows(IllegalArgumentException.class,
                () -> mGeocoder.getFromLocation(10, -181, 5, listener));
        assertThrows(IllegalArgumentException.class,
                () -> mGeocoder.getFromLocation(10, 181, 5, listener));
    }

    @ApiTest(apis = "android.location.Geocoder#getFromLocationName")
    @AppModeFull(reason = "b/238831704 - Test cases don't apply for Instant apps")
    @Test
    public void testGetFromLocationName() {
        assumeTrue(Geocoder.isPresent());

        GeocodeListener listener = mock(GeocodeListener.class);
        mGeocoder.getFromLocationName("Dalvik,Iceland", 5, listener);
        verify(listener, timeout(10000)).onGeocode(anyList());
    }

    @ApiTest(apis = "android.location.Geocoder#getFromLocationName")
    @AppModeFull(reason = "b/238831704 - Test cases don't apply for Instant apps")
    @Test
    public void testGetFromLocationName_sync() throws Exception {
        assumeTrue(Geocoder.isPresent());

        mGeocoder.getFromLocationName("Dalvik,Iceland", 5);
    }

    @ApiTest(apis = "android.location.Geocoder#getFromLocationName")
    @AppModeFull(reason = "b/238831704 - Test cases don't apply for Instant apps")
    @Test
    public void testGetFromLocationName_badInput() {
        GeocodeListener listener = mock(GeocodeListener.class);
        assertThrows(IllegalArgumentException.class,
                () -> mGeocoder.getFromLocationName(null, 5, listener));
        assertThrows(IllegalArgumentException.class,
                () -> mGeocoder.getFromLocationName("Beijing", 5, -91, 100, 45, 130, listener));
        assertThrows(IllegalArgumentException.class,
                () -> mGeocoder.getFromLocationName("Beijing", 5, 25, 190, 45, 130, listener));
        assertThrows(IllegalArgumentException.class,
                () -> mGeocoder.getFromLocationName("Beijing", 5, 25, 100, 91, 130, listener));
        assertThrows(IllegalArgumentException.class,
                () -> mGeocoder.getFromLocationName("Beijing", 5, 25, 100, 45, -181, listener));
    }
}
