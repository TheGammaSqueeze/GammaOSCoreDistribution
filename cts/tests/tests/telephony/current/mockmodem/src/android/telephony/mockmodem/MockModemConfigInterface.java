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

import android.os.Handler;

public interface MockModemConfigInterface {

    // ***** Constants
    int MAX_NUM_OF_SIM_SLOT = 3; // Change this needs to add more SIM SLOT NVs.
    int MAX_NUM_OF_LOGICAL_MODEM = 2; // Change this needs to add more MODEM NVs.
    int RADIO_STATE_UNAVAILABLE = 0;
    int RADIO_STATE_OFF = 1;
    int RADIO_STATE_ON = 2;

    // Default config value
    String DEFAULT_BASEBAND_VERSION = "mock-modem-service-1.0";
    String DEFAULT_IMEI = "123456789012345";
    String DEFAULT_IMEISV = "01";
    String DEFAULT_ESN = "123456789";
    String DEFAULT_MEID = "123456789012345";
    int DEFAULT_RADIO_STATE = RADIO_STATE_UNAVAILABLE;
    int DEFAULT_NUM_OF_LIVE_MODEM = 1; // Should <= MAX_NUM_OF_MODEM
    int DEFAULT_MAX_ACTIVE_DATA = 2;
    int DEFAULT_MAX_ACTIVE_INTERNAL_DATA = 1;
    boolean DEFAULT_IS_INTERNAL_LINGERING_SUPPORTED = false;
    int DEFAULT_LOGICAL_MODEM1_ID = 0;
    int DEFAULT_LOGICAL_MODEM2_ID = 1;

    // ***** Methods
    Handler getMockModemConfigHandler();

    /** Broadcast all notifications */
    void notifyAllRegistrantNotifications();

    // ***** IRadioConfig
    /** Register/unregister notification handler for number of modem changed */
    void registerForNumOfLiveModemChanged(Handler h, int what, Object obj);

    void unregisterForNumOfLiveModemChanged(Handler h);

    /** Register/unregister notification handler for sim slot status changed */
    void registerForPhoneCapabilityChanged(Handler h, int what, Object obj);

    void unregisterForPhoneCapabilityChanged(Handler h);

    /** Register/unregister notification handler for sim slot status changed */
    void registerForSimSlotStatusChanged(Handler h, int what, Object obj);

    void unregisterForSimSlotStatusChanged(Handler h);

    // ***** IRadioModem
    /** Register/unregister notification handler for baseband version changed */
    void registerForBasebandVersionChanged(Handler h, int what, Object obj);

    void unregisterForBasebandVersionChanged(Handler h);

    /** Register/unregister notification handler for device identity changed */
    void registerForDeviceIdentityChanged(Handler h, int what, Object obj);

    void unregisterForDeviceIdentityChanged(Handler h);

    /** Register/unregister notification handler for radio state changed */
    void registerForRadioStateChanged(Handler h, int what, Object obj);

    void unregisterForRadioStateChanged(Handler h);

    // ***** IRadioSim
    /** Register/unregister notification handler for card status changed */
    void registerForCardStatusChanged(Handler h, int what, Object obj);

    void unregisterForCardStatusChanged(Handler h);

    /** Register/unregister notification handler for sim app data changed */
    void registerForSimAppDataChanged(Handler h, int what, Object obj);

    void unregisterForSimAppDataChanged(Handler h);

    /** Register/unregister notification handler for sim info changed */
    void registerForSimInfoChanged(Handler h, int what, Object obj);

    void unregisterForSimInfoChanged(Handler h);

    // ***** IRadioNetwork
    /** Register/unregister notification handler for service status changed */
    void registerForServiceStateChanged(Handler h, int what, Object obj);

    void unregisterForServiceStateChanged(Handler h);

    /**
     * Sets the latest radio power state of modem
     *
     * @param state 0 means "unavailable", 1 means "off", 2 means "on".
     * @param client for tracking calling client
     */
    void setRadioState(int state, String client);

    /**
     * Query whether any SIM cards are present or not.
     *
     * @param client for tracking calling client
     * @return boolean true if any sim card inserted, otherwise false.
     */
    boolean isSimCardPresent(String client);

    /**
     * Change SIM profile
     *
     * @param simProfileId The target profile to be switched.
     * @param client for tracking calling client
     */
    void changeSimProfile(int simProfileId, String client);

    /**
     * Modify SIM info of the SIM such as MCC/MNC, IMSI, etc.
     *
     * @param type the type of SIM info to modify.
     * @param data to modify for the type of SIM info.
     * @param client for tracking calling client
     */
    void setSimInfo(int type, String[] data, String client);

    /**
     * Get SIM info of the SIM slot, e.g. MCC/MNC, IMSI.
     *
     * @param type the type of SIM info.
     * @param client for tracking calling client
     * @return String the SIM info of the queried type.
     */
    String getSimInfo(int type, String client);
}
