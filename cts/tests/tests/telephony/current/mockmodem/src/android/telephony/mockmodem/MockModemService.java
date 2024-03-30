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

package android.telephony.mockmodem;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.radio.RadioError;
import android.hardware.radio.RadioResponseInfo;
import android.hardware.radio.RadioResponseType;
import android.os.Binder;
import android.os.IBinder;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MockModemService extends Service {
    private static final String TAG = "MockModemService";
    private static final String RESOURCE_PACKAGE_NAME = "android";

    public static final int TEST_TIMEOUT_MS = 30000;
    public static final String IRADIOCONFIG_INTERFACE = "android.telephony.mockmodem.iradioconfig";
    public static final String IRADIOMODEM_INTERFACE = "android.telephony.mockmodem.iradiomodem";
    public static final String IRADIOSIM_INTERFACE = "android.telephony.mockmodem.iradiosim";
    public static final String IRADIONETWORK_INTERFACE =
            "android.telephony.mockmodem.iradionetwork";
    public static final String IRADIODATA_INTERFACE = "android.telephony.mockmodem.iradiodata";
    public static final String IRADIOMESSAGING_INTERFACE =
            "android.telephony.mockmodem.iradiomessaging";
    public static final String IRADIOVOICE_INTERFACE = "android.telephony.mockmodem.iradiovoice";
    public static final String PHONE_ID = "phone_id";

    private static Context sContext;
    private static MockModemConfigInterface[] sMockModemConfigInterfaces;
    private static IRadioConfigImpl sIRadioConfigImpl;
    private static IRadioModemImpl sIRadioModemImpl;
    private static IRadioSimImpl sIRadioSimImpl;
    private static IRadioNetworkImpl sIRadioNetworkImpl;
    private static IRadioDataImpl sIRadioDataImpl;
    private static IRadioMessagingImpl sIRadioMessagingImpl;
    private static IRadioVoiceImpl sIRadioVoiceImpl;

    public static final byte PHONE_ID_0 = 0x00;
    public static final byte PHONE_ID_1 = 0x01;

    public static final int LATCH_MOCK_MODEM_SERVICE_READY = 0;
    public static final int LATCH_RADIO_INTERFACES_READY = 1;
    public static final int LATCH_MAX = 2;

    private static final int IRADIO_CONFIG_INTERFACE_NUMBER = 1;
    private static final int IRADIO_INTERFACE_NUMBER = 6;

    private TelephonyManager mTelephonyManager;
    private int mNumOfSim;
    private int mNumOfPhone;
    private static final int DEFAULT_SUB_ID = 0;

    private Object mLock;
    protected static CountDownLatch[] sLatches;
    private LocalBinder mBinder;

    // For local access of this Service.
    class LocalBinder extends Binder {
        MockModemService getService() {
            return MockModemService.this;
        }
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "Mock Modem Service Created");

        sContext = InstrumentationRegistry.getInstrumentation().getContext();
        mTelephonyManager = sContext.getSystemService(TelephonyManager.class);
        mNumOfSim = getNumPhysicalSlots();
        mNumOfPhone = mTelephonyManager.getActiveModemCount();
        Log.d(TAG, "Support number of phone = " + mNumOfPhone + ", number of SIM = " + mNumOfSim);

        mLock = new Object();

        sLatches = new CountDownLatch[LATCH_MAX];
        for (int i = 0; i < LATCH_MAX; i++) {
            sLatches[i] = new CountDownLatch(1);
            if (i == LATCH_RADIO_INTERFACES_READY) {
                int radioInterfaceNumber =
                        IRADIO_CONFIG_INTERFACE_NUMBER + mNumOfPhone * IRADIO_INTERFACE_NUMBER;
                sLatches[i] = new CountDownLatch(radioInterfaceNumber);
            } else {
                sLatches[i] = new CountDownLatch(1);
            }
        }

        sMockModemConfigInterfaces = new MockModemConfigBase[mNumOfPhone];
        for (int i = 0; i < mNumOfPhone; i++) {
            sMockModemConfigInterfaces[i] =
                    new MockModemConfigBase(sContext, i, mNumOfSim, mNumOfPhone);
        }

        sIRadioConfigImpl = new IRadioConfigImpl(this, sMockModemConfigInterfaces, DEFAULT_SUB_ID);
        // TODO: Support DSDS
        sIRadioModemImpl = new IRadioModemImpl(this, sMockModemConfigInterfaces, DEFAULT_SUB_ID);
        sIRadioSimImpl = new IRadioSimImpl(this, sMockModemConfigInterfaces, DEFAULT_SUB_ID);
        sIRadioNetworkImpl =
                new IRadioNetworkImpl(this, sMockModemConfigInterfaces, DEFAULT_SUB_ID);
        sIRadioDataImpl = new IRadioDataImpl(this);
        sIRadioMessagingImpl = new IRadioMessagingImpl(this);
        sIRadioVoiceImpl = new IRadioVoiceImpl(this);

        mBinder = new LocalBinder();
    }

    @Override
    public IBinder onBind(Intent intent) {
        byte phoneId = intent.getByteExtra(PHONE_ID, PHONE_ID_0);

        if (IRADIOCONFIG_INTERFACE.equals(intent.getAction())) {
            Log.i(TAG, "onBind-IRadioConfig " + phoneId);
            return sIRadioConfigImpl;
        } else if (IRADIOMODEM_INTERFACE.equals(intent.getAction())) {
            Log.i(TAG, "onBind-IRadioModem " + phoneId);
            if (phoneId == PHONE_ID_0) {
                return sIRadioModemImpl;
            } else if (phoneId == PHONE_ID_1) {
                // TODO
            } else {
                return null;
            }
        } else if (IRADIOSIM_INTERFACE.equals(intent.getAction())) {
            Log.i(TAG, "onBind-IRadioSim " + phoneId);
            if (phoneId == PHONE_ID_0) {
                return sIRadioSimImpl;
            } else if (phoneId == PHONE_ID_1) {
                // TODO
            } else {
                return null;
            }
        } else if (IRADIONETWORK_INTERFACE.equals(intent.getAction())) {
            Log.i(TAG, "onBind-IRadioNetwork " + phoneId);
            if (phoneId == PHONE_ID_0) {
                return sIRadioNetworkImpl;
            } else if (phoneId == PHONE_ID_1) {
                // TODO
            } else {
                return null;
            }
        } else if (IRADIODATA_INTERFACE.equals(intent.getAction())) {
            Log.i(TAG, "onBind-IRadioData " + phoneId);
            if (phoneId == PHONE_ID_0) {
                return sIRadioDataImpl;
            } else if (phoneId == PHONE_ID_1) {
                // TODO
            } else {
                return null;
            }
        } else if (IRADIOMESSAGING_INTERFACE.equals(intent.getAction())) {
            Log.i(TAG, "onBind-IRadioMessaging " + phoneId);
            if (phoneId == PHONE_ID_0) {
                return sIRadioMessagingImpl;
            } else if (phoneId == PHONE_ID_1) {
                // TODO
            } else {
                return null;
            }
        } else if (IRADIOVOICE_INTERFACE.equals(intent.getAction())) {
            Log.i(TAG, "onBind-IRadioVoice " + phoneId);
            if (phoneId == PHONE_ID_0) {
                return sIRadioVoiceImpl;
            } else if (phoneId == PHONE_ID_1) {
                // TODO
            } else {
                return null;
            }
        }

        countDownLatch(LATCH_MOCK_MODEM_SERVICE_READY);
        Log.i(TAG, "onBind-Local");
        return mBinder;
    }

    public boolean waitForLatchCountdown(int latchIndex) {
        boolean complete = false;
        try {
            CountDownLatch latch;
            synchronized (mLock) {
                latch = sLatches[latchIndex];
            }
            complete = latch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
        }
        synchronized (mLock) {
            sLatches[latchIndex] = new CountDownLatch(1);
        }
        return complete;
    }

    public boolean waitForLatchCountdown(int latchIndex, long waitMs) {
        boolean complete = false;
        try {
            CountDownLatch latch;
            synchronized (mLock) {
                latch = sLatches[latchIndex];
            }
            complete = latch.await(waitMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
        }
        synchronized (mLock) {
            sLatches[latchIndex] = new CountDownLatch(1);
        }
        return complete;
    }

    public void countDownLatch(int latchIndex) {
        synchronized (mLock) {
            sLatches[latchIndex].countDown();
        }
    }

    public int getNumPhysicalSlots() {
        int numPhysicalSlots = MockSimService.MOCK_SIM_SLOT_MIN;
        int resourceId =
                sContext.getResources()
                        .getIdentifier(
                                "config_num_physical_slots", "integer", RESOURCE_PACKAGE_NAME);

        if (resourceId > 0) {
            numPhysicalSlots = sContext.getResources().getInteger(resourceId);
        } else {
            Log.d(TAG, "Fail to get the resource Id, using default: " + numPhysicalSlots);
        }

        if (numPhysicalSlots > MockSimService.MOCK_SIM_SLOT_MAX) {
            Log.d(
                    TAG,
                    "Number of physical Slot ("
                            + numPhysicalSlots
                            + ") > mock sim slot support. Reset to max number supported ("
                            + MockSimService.MOCK_SIM_SLOT_MAX
                            + ").");
            numPhysicalSlots = MockSimService.MOCK_SIM_SLOT_MAX;
        } else if (numPhysicalSlots <= MockSimService.MOCK_SIM_SLOT_MIN) {
            Log.d(
                    TAG,
                    "Number of physical Slot ("
                            + numPhysicalSlots
                            + ") < mock sim slot support. Reset to min number supported ("
                            + MockSimService.MOCK_SIM_SLOT_MIN
                            + ").");
            numPhysicalSlots = MockSimService.MOCK_SIM_SLOT_MIN;
        }

        return numPhysicalSlots;
    }

    public RadioResponseInfo makeSolRsp(int serial) {
        RadioResponseInfo rspInfo = new RadioResponseInfo();
        rspInfo.type = RadioResponseType.SOLICITED;
        rspInfo.serial = serial;
        rspInfo.error = RadioError.NONE;

        return rspInfo;
    }

    public RadioResponseInfo makeSolRsp(int serial, int error) {
        RadioResponseInfo rspInfo = new RadioResponseInfo();
        rspInfo.type = RadioResponseType.SOLICITED;
        rspInfo.serial = serial;
        rspInfo.error = error;

        return rspInfo;
    }

    public boolean initialize(int simprofile) {
        Log.d(TAG, "initialize simprofile = " + simprofile);
        boolean result = true;

        // Sync mock modem status between modules
        for (int i = 0; i < mNumOfPhone; i++) {
            // Set initial SIM profile
            sMockModemConfigInterfaces[i].changeSimProfile(simprofile, TAG);

            // Sync modem configurations to radio modules
            sMockModemConfigInterfaces[i].notifyAllRegistrantNotifications();
        }

        // Connect to telephony framework
        sIRadioModemImpl.rilConnected();

        return result;
    }

    public MockModemConfigInterface[] getMockModemConfigInterfaces() {
        return sMockModemConfigInterfaces;
    }

    // TODO: Support DSDS
    public IRadioConfigImpl getIRadioConfig() {
        return sIRadioConfigImpl;
    }

    public IRadioModemImpl getIRadioModem() {
        return sIRadioModemImpl;
    }

    public IRadioSimImpl getIRadioSim() {
        return sIRadioSimImpl;
    }

    public IRadioNetworkImpl getIRadioNetwork() {
        return sIRadioNetworkImpl;
    }

    public IRadioVoiceImpl getIRadioVoice() {
        return sIRadioVoiceImpl;
    }

    public IRadioMessagingImpl getIRadioMessaging() {
        return sIRadioMessagingImpl;
    }

    public IRadioDataImpl getIRadioData() {
        return sIRadioDataImpl;
    }
}
