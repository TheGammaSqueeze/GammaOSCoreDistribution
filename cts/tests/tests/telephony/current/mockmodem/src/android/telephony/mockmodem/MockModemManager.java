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

package android.telephony.mockmodem;

import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_DEFAULT;

import static com.android.internal.telephony.RILConstants.RIL_REQUEST_RADIO_POWER;

import android.content.Context;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import java.util.Set;
import java.util.concurrent.TimeUnit;

public class MockModemManager {
    private static final String TAG = "MockModemManager";

    private static Context sContext;
    private static MockModemServiceConnector sServiceConnector;
    private MockModemService mMockModemService;

    public MockModemManager() {
        sContext = InstrumentationRegistry.getInstrumentation().getContext();
    }

    private void waitForTelephonyFrameworkDone(int delayInSec) throws Exception {
        TimeUnit.SECONDS.sleep(delayInSec);
    }

    /* Public APIs */

    /**
     * Bring up Mock Modem Service and connect to it.
     *
     * @return boolean true if the operation is successful, otherwise false.
     */
    public boolean connectMockModemService() throws Exception {
        return connectMockModemService(MOCK_SIM_PROFILE_ID_DEFAULT);
    }
    /**
     * Bring up Mock Modem Service and connect to it.
     *
     * @pararm simprofile for initial Sim profile
     * @return boolean true if the operation is successful, otherwise false.
     */
    public boolean connectMockModemService(int simprofile) throws Exception {
        boolean result = false;

        if (sServiceConnector == null) {
            sServiceConnector =
                    new MockModemServiceConnector(InstrumentationRegistry.getInstrumentation());
        }

        if (sServiceConnector != null) {
            // TODO: support DSDS
            result = sServiceConnector.connectMockModemService(simprofile);

            if (result) {
                mMockModemService = sServiceConnector.getMockModemService();

                if (mMockModemService != null) {
                    /*
                     It needs to have a delay to wait for Telephony Framework to bind with
                     MockModemService and set radio power as a desired state for initial condition
                     even get SIM card state. Currently, 1 sec is enough for now.
                    */
                    waitForTelephonyFrameworkDone(1);
                } else {
                    Log.e(TAG, "MockModemService get failed!");
                    result = false;
                }
            }
        } else {
            Log.e(TAG, "Create MockModemServiceConnector failed!");
        }

        return result;
    }

    /**
     * Disconnect from Mock Modem Service.
     *
     * @return boolean true if the operation is successful, otherwise false.
     */
    public boolean disconnectMockModemService() throws Exception {
        boolean result = false;

        if (sServiceConnector != null) {
            result = sServiceConnector.disconnectMockModemService();

            if (result) {
                mMockModemService = null;
            } else {
                Log.e(TAG, "MockModemService disconnected failed!");
            }
        } else {
            Log.e(TAG, "No MockModemServiceConnector exist!");
        }

        return result;
    }

    /**
     * Query whether an active SIM card is present on this slot or not.
     *
     * @param slotId which slot would be checked.
     * @return boolean true if any sim card inserted, otherwise false.
     */
    public boolean isSimCardPresent(int slotId) throws Exception {
        Log.d(TAG, "isSimCardPresent[" + slotId + "]");

        MockModemConfigInterface[] configInterfaces =
                mMockModemService.getMockModemConfigInterfaces();
        return (configInterfaces != null) ? configInterfaces[slotId].isSimCardPresent(TAG) : false;
    }

    /**
     * Insert a SIM card.
     *
     * @param slotId which slot would insert.
     * @param simProfileId which carrier sim card is inserted.
     * @return boolean true if the operation is successful, otherwise false.
     */
    public boolean insertSimCard(int slotId, int simProfileId) throws Exception {
        Log.d(TAG, "insertSimCard[" + slotId + "] with profile Id(" + simProfileId + ")");
        boolean result = true;

        if (!isSimCardPresent(slotId)) {
            MockModemConfigInterface[] configInterfaces =
                    mMockModemService.getMockModemConfigInterfaces();
            if (configInterfaces != null) {
                configInterfaces[slotId].changeSimProfile(simProfileId, TAG);
                waitForTelephonyFrameworkDone(1);
            }
        } else {
            Log.d(TAG, "There is a SIM inserted. Need to remove first.");
            result = false;
        }
        return result;
    }

    /**
     * Remove a SIM card.
     *
     * @param slotId which slot would remove the SIM.
     * @return boolean true if the operation is successful, otherwise false.
     */
    public boolean removeSimCard(int slotId) throws Exception {
        Log.d(TAG, "removeSimCard[" + slotId + "]");
        boolean result = true;

        if (isSimCardPresent(slotId)) {
            MockModemConfigInterface[] configInterfaces =
                    mMockModemService.getMockModemConfigInterfaces();
            if (configInterfaces != null) {
                configInterfaces[slotId].changeSimProfile(MOCK_SIM_PROFILE_ID_DEFAULT, TAG);
                waitForTelephonyFrameworkDone(1);
            }
        } else {
            Log.d(TAG, "There is no SIM inserted.");
            result = false;
        }
        return result;
    }

    /**
     * Modify SIM info of the SIM such as MCC/MNC, IMSI, etc.
     *
     * @param slotId for modifying.
     * @param type the type of SIM info to modify.
     * @param data to modify for the type of SIM info.
     * @return boolean true if the operation is successful, otherwise false.
     */
    public boolean setSimInfo(int slotId, int type, String[] data) throws Exception {
        Log.d(TAG, "setSimInfo[" + slotId + "]");
        boolean result = true;

        if (isSimCardPresent(slotId)) {
            MockModemConfigInterface[] configInterfaces =
                    mMockModemService.getMockModemConfigInterfaces();
            if (configInterfaces != null) {
                configInterfaces[slotId].setSimInfo(type, data, TAG);

                // Wait for telephony framework refresh data and carrier config
                waitForTelephonyFrameworkDone(2);
            } else {
                Log.e(TAG, "MockModemConfigInterface == null!");
                result = false;
            }
        } else {
            Log.d(TAG, "There is no SIM inserted.");
            result = false;
        }
        return result;
    }

    /**
     * Get SIM info of the SIM slot, e.g. MCC/MNC, IMSI.
     *
     * @param slotId for the query.
     * @param type the type of SIM info.
     * @return String the SIM info of the queried type.
     */
    public String getSimInfo(int slotId, int type) throws Exception {
        Log.d(TAG, "getSimInfo[" + slotId + "]");
        String result = "";

        if (isSimCardPresent(slotId)) {
            MockModemConfigInterface[] configInterfaces =
                    mMockModemService.getMockModemConfigInterfaces();
            if (configInterfaces != null) {
                result = configInterfaces[slotId].getSimInfo(type, TAG);
            }
        } else {
            Log.d(TAG, "There is no SIM inserted.");
        }
        return result;
    }

    /**
     * Force the response error return for a specific RIL request
     *
     * @param slotId which slot needs to be set.
     * @param requestId the request/response message ID
     * @param error RIL_Errno and -1 means to disable the modified mechanism, back to original mock
     *     modem behavior
     * @return boolean true if the operation is successful, otherwise false.
     */
    public boolean forceErrorResponse(int slotId, int requestId, int error) throws Exception {
        Log.d(
                TAG,
                "forceErrorResponse[" + slotId + "] for request:" + requestId + " ,error:" + error);
        boolean result = true;

        // TODO: support DSDS
        switch (requestId) {
            case RIL_REQUEST_RADIO_POWER:
                mMockModemService.getIRadioModem().forceErrorResponse(requestId, error);
                break;
            default:
                Log.e(TAG, "request:" + requestId + " not support to change the response error");
                result = false;
                break;
        }
        return result;
    }

    /**
     * Make the modem is in service or not.
     *
     * @param slotId which SIM slot is under the carrierId network.
     * @param carrierId which carrier network is used.
     * @param registration boolean true if the modem is in service, otherwise false.
     * @return boolean true if the operation is successful, otherwise false.
     */
    public boolean changeNetworkService(int slotId, int carrierId, boolean registration)
            throws Exception {
        Log.d(
                TAG,
                "changeNetworkService["
                        + slotId
                        + "] in carrier ("
                        + carrierId
                        + ") "
                        + registration);

        boolean result;
        // TODO: support DSDS for slotId
        result = mMockModemService.getIRadioNetwork().changeNetworkService(carrierId, registration);

        waitForTelephonyFrameworkDone(1);
        return result;
    }

    /**
     * get GSM CellBroadcastConfig outputs from IRadioMessagingImpl
     *
     * @return Set of broadcast configs
     */
    public Set<Integer> getGsmBroadcastConfig() {
        return mMockModemService.getIRadioMessaging().getGsmBroadcastConfigSet();
    }

    /**
     * get CDMA CellBroadcastConfig outputs from IRadioMessagingImpl
     *
     * @return Set of broadcast configs
     */
    public Set<Integer> getCdmaBroadcastConfig() {
        return mMockModemService.getIRadioMessaging().getCdmaBroadcastConfigSet();
    }
}
