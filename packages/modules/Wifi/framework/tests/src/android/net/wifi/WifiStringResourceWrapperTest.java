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

package android.net.wifi;

import static android.net.wifi.WifiStringResourceWrapper.CARRIER_ID_RESOURCE_NAME_SUFFIX;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.when;

import android.content.res.Resources;
import android.telephony.SubscriptionManager;

import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

/**
 * Unit tests for {@link WifiStringResourceWrapper}
 */
@SmallTest
public class WifiStringResourceWrapperTest {
    private MockitoSession mStaticMockSession = null;

    @Mock WifiContext mContext;
    @Mock Resources mResources;

    WifiStringResourceWrapper mDut;

    private static final int SUB_ID = 123;
    private static final int CARRIER_ID = 4567;

    private static final int RES_ID_NOT_FOUND = 0;

    private static final String RES_NAME_1 = "some_eap_error";
    private static final int RES_ID_1 = 32764;
    private static final String RES_STRING_VAL_1 = "Some message";

    private static final int RES_ID_2 = 32765;
    private static final String[] RES_STRING_ARRAY_VAL_2 = { // carrier ID not included
            ":::1234:::Some message AA",
            ":::45678:::Some message BB"
    };
    private static final String[] RES_STRING_ARRAY_VAL_3 = { // carrier ID is here!
            ":::1234:::Some message CC",
            ":::4567:::Some message DD"
    };
    private static final String[] RES_STRING_ARRAY_VAL_4 = { // carrier ID is here - empty message
            ":::1234:::Some message EE",
            ":::4567:::"
    };
    private static final String[] RES_STRING_ARRAY_VAL_5 = { // carrier ID is here - bad format
            ":::1234:::Some message FF",
            ":::4567::"
    };

    /**
     * Sets up for unit test
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        // static mocking
        mStaticMockSession = mockitoSession()
                .mockStatic(SubscriptionManager.class)
                .startMocking();
        lenient().when(SubscriptionManager.getResourcesForSubId(any(), eq(SUB_ID)))
                .thenReturn(mResources);

        when(mResources.getIdentifier(eq(RES_NAME_1), eq("string"), any())).thenReturn(RES_ID_1);
        when(mResources.getString(eq(RES_ID_1), any())).thenReturn(RES_STRING_VAL_1);
        when(mResources.getIdentifier(eq(RES_NAME_1 + CARRIER_ID_RESOURCE_NAME_SUFFIX),
                eq("array"), any())).thenReturn(RES_ID_NOT_FOUND);

        mDut = new WifiStringResourceWrapper(mContext, SUB_ID, CARRIER_ID);
    }

    @After
    public void cleanUp() throws Exception {
        validateMockitoUsage();
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testBasicOperations() {
        assertEquals("Some message", mDut.getString(RES_NAME_1));
        assertNull(mDut.getString("something else"));
    }

    @Test
    public void testCarrierIdWithBaseNoOverride() {
        when(mResources.getIdentifier(eq(RES_NAME_1 + CARRIER_ID_RESOURCE_NAME_SUFFIX),
                eq("array"), any())).thenReturn(RES_ID_2);
        when(mResources.getStringArray(eq(RES_ID_2))).thenReturn(RES_STRING_ARRAY_VAL_2);
        assertEquals("Some message", mDut.getString(RES_NAME_1));
    }

    @Test
    public void testCarrierIdAvailable() {
        when(mResources.getIdentifier(eq(RES_NAME_1 + CARRIER_ID_RESOURCE_NAME_SUFFIX),
                eq("array"), any())).thenReturn(RES_ID_2);
        when(mResources.getStringArray(eq(RES_ID_2))).thenReturn(RES_STRING_ARRAY_VAL_3);
        assertEquals("Some message DD", mDut.getString(RES_NAME_1));
    }

    @Test
    public void testCarrierIdAvailableEmptyMessage() {
        when(mResources.getIdentifier(eq(RES_NAME_1 + CARRIER_ID_RESOURCE_NAME_SUFFIX),
                eq("array"), any())).thenReturn(RES_ID_2);
        when(mResources.getStringArray(eq(RES_ID_2))).thenReturn(RES_STRING_ARRAY_VAL_4);
        assertEquals("", mDut.getString(RES_NAME_1));
    }

    @Test
    public void testCarrierIdBadlyFormatted() {
        when(mResources.getIdentifier(eq(RES_NAME_1 + CARRIER_ID_RESOURCE_NAME_SUFFIX),
                eq("array"), any())).thenReturn(RES_ID_2);
        when(mResources.getStringArray(eq(RES_ID_2))).thenReturn(RES_STRING_ARRAY_VAL_5);
        assertEquals("Some message", mDut.getString(RES_NAME_1));
    }
}
