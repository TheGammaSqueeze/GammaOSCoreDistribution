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

package com.android.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.telecom.PhoneAccountHandle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.telephony.GsmCdmaPhone;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;

@RunWith(AndroidJUnit4.class)
public class PhoneUtilsTest {
    @Mock
    private SubscriptionManager mMockSubscriptionManager;
    @Mock
    private SubscriptionInfo mMockSubscriptionInfo;
    @Mock
    private GsmCdmaPhone mMockPhone;

    private final int mPhoneAccountHandleIdInteger = 123;
    private final String mPhoneAccountHandleIdString = "123";
    private static final ComponentName PSTN_CONNECTION_SERVICE_COMPONENT = new ComponentName(
            "com.android.phone", "com.android.services.telephony.TelephonyConnectionService");
    private PhoneAccountHandle mPhoneAccountHandleTest = new PhoneAccountHandle(
            PSTN_CONNECTION_SERVICE_COMPONENT, mPhoneAccountHandleIdString);

    private HashMap<InstanceKey, Object> mOldInstances = new HashMap<InstanceKey, Object>();

    private ArrayList<InstanceKey> mInstanceKeys = new ArrayList<InstanceKey>();

    private static class InstanceKey {
        public final Class mClass;
        public final String mInstName;
        public final Object mObj;
        InstanceKey(final Class c, final String instName, final Object obj) {
            mClass = c;
            mInstName = instName;
            mObj = obj;
        }

        @Override
        public int hashCode() {
            return (mClass.getName().hashCode() * 31 + mInstName.hashCode()) * 31;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || !(obj instanceof InstanceKey)) {
                return false;
            }

            InstanceKey other = (InstanceKey) obj;
            return (other.mClass == mClass && other.mInstName.equals(mInstName)
                    && other.mObj == mObj);
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mMockSubscriptionManager.getActiveSubscriptionInfo(
                eq(mPhoneAccountHandleIdInteger))).thenReturn(mMockSubscriptionInfo);
        when(mMockPhone.getSubId()).thenReturn(mPhoneAccountHandleIdInteger);
        Phone[] mPhones = new Phone[] {mMockPhone};
        replaceInstance(PhoneFactory.class, "sPhones", null, mPhones);
    }

    @After
    public void tearDown() throws Exception {
        restoreInstance(PhoneFactory.class, "sPhones", null);
    }

    protected synchronized void replaceInstance(final Class c, final String instanceName,
            final Object obj, final Object newValue)
            throws Exception {
        Field field = c.getDeclaredField(instanceName);
        field.setAccessible(true);

        InstanceKey key = new InstanceKey(c, instanceName, obj);
        if (!mOldInstances.containsKey(key)) {
            mOldInstances.put(key, field.get(obj));
            mInstanceKeys.add(key);
        }
        field.set(obj, newValue);
    }

    protected synchronized void restoreInstance(final Class c, final String instanceName,
            final Object obj) throws Exception {
        InstanceKey key = new InstanceKey(c, instanceName, obj);
        if (mOldInstances.containsKey(key)) {
            Field field = c.getDeclaredField(instanceName);
            field.setAccessible(true);
            field.set(obj, mOldInstances.get(key));
            mOldInstances.remove(key);
            mInstanceKeys.remove(key);
        }
    }

    @Test
    public void testIsPhoneAccountActive() throws Exception {
        assertTrue(PhoneUtils.isPhoneAccountActive(
                mMockSubscriptionManager, mPhoneAccountHandleTest));
    }

    @Test
    public void testGetPhoneForPhoneAccountHandle() throws Exception {
        assertEquals(mMockPhone, PhoneUtils.getPhoneForPhoneAccountHandle(
                mPhoneAccountHandleTest));
    }

    @Test
    public void testMakePstnPhoneAccountHandleWithPrefix() throws Exception {
        PhoneAccountHandle phoneAccountHandleTest = new PhoneAccountHandle(
                PSTN_CONNECTION_SERVICE_COMPONENT, mPhoneAccountHandleIdString);
        assertEquals(phoneAccountHandleTest, PhoneUtils.makePstnPhoneAccountHandleWithPrefix(
                mPhoneAccountHandleIdString, "", false));
    }
}
