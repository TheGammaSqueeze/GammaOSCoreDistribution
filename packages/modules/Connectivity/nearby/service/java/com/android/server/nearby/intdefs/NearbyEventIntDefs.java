/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.server.nearby.intdefs;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Holds integer definitions for NearbyEvent. */
public class NearbyEventIntDefs {

    /** NearbyEvent Code. */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                    EventCode.UNKNOWN_EVENT_TYPE,
                    EventCode.MAGIC_PAIR_START,
                    EventCode.WAIT_FOR_SCREEN_UNLOCK,
                    EventCode.GATT_CONNECT,
                    EventCode.BR_EDR_HANDOVER_WRITE_CONTROL_POINT_REQUEST,
                    EventCode.BR_EDR_HANDOVER_READ_BLUETOOTH_MAC,
                    EventCode.BR_EDR_HANDOVER_READ_TRANSPORT_BLOCK,
                    EventCode.GET_PROFILES_VIA_SDP,
                    EventCode.DISCOVER_DEVICE,
                    EventCode.CANCEL_DISCOVERY,
                    EventCode.REMOVE_BOND,
                    EventCode.CANCEL_BOND,
                    EventCode.CREATE_BOND,
                    EventCode.CONNECT_PROFILE,
                    EventCode.DISABLE_BLUETOOTH,
                    EventCode.ENABLE_BLUETOOTH,
                    EventCode.MAGIC_PAIR_END,
                    EventCode.SECRET_HANDSHAKE,
                    EventCode.WRITE_ACCOUNT_KEY,
                    EventCode.WRITE_TO_FOOTPRINTS,
                    EventCode.PASSKEY_EXCHANGE,
                    EventCode.DEVICE_RECOGNIZED,
                    EventCode.GET_LOCAL_PUBLIC_ADDRESS,
                    EventCode.DIRECTLY_CONNECTED_TO_PROFILE,
                    EventCode.DEVICE_ALIAS_CHANGED,
                    EventCode.WRITE_DEVICE_NAME,
                    EventCode.UPDATE_PROVIDER_NAME_START,
                    EventCode.UPDATE_PROVIDER_NAME_END,
                    EventCode.READ_FIRMWARE_VERSION,
                    EventCode.RETROACTIVE_PAIR_START,
                    EventCode.RETROACTIVE_PAIR_END,
                    EventCode.SUBSEQUENT_PAIR_START,
                    EventCode.SUBSEQUENT_PAIR_END,
                    EventCode.BISTO_PAIR_START,
                    EventCode.BISTO_PAIR_END,
                    EventCode.REMOTE_PAIR_START,
                    EventCode.REMOTE_PAIR_END,
                    EventCode.BEFORE_CREATE_BOND,
                    EventCode.BEFORE_CREATE_BOND_BONDING,
                    EventCode.BEFORE_CREATE_BOND_BONDED,
                    EventCode.BEFORE_CONNECT_PROFILE,
                    EventCode.HANDLE_PAIRING_REQUEST,
                    EventCode.SECRET_HANDSHAKE_GATT_COMMUNICATION,
                    EventCode.GATT_CONNECTION_AND_SECRET_HANDSHAKE,
                    EventCode.CHECK_SIGNAL_AFTER_HANDSHAKE,
                    EventCode.RECOVER_BY_RETRY_GATT,
                    EventCode.RECOVER_BY_RETRY_HANDSHAKE,
                    EventCode.RECOVER_BY_RETRY_HANDSHAKE_RECONNECT,
                    EventCode.GATT_HANDSHAKE_MANUAL_RETRY_ATTEMPTS,
                    EventCode.PAIR_WITH_CACHED_MODEL_ID,
                    EventCode.DIRECTLY_CONNECT_PROFILE_WITH_CACHED_ADDRESS,
                    EventCode.PAIR_WITH_NEW_MODEL,
            })
    public @interface EventCode {
        int UNKNOWN_EVENT_TYPE = 0;

        // Codes for Magic Pair.
        // Starting at 1000 to not conflict with other existing codes (e.g.
        // DiscoveryEvent) that may be migrated to become official Event Codes.
        int MAGIC_PAIR_START = 1010;
        int WAIT_FOR_SCREEN_UNLOCK = 1020;
        int GATT_CONNECT = 1030;
        int BR_EDR_HANDOVER_WRITE_CONTROL_POINT_REQUEST = 1040;
        int BR_EDR_HANDOVER_READ_BLUETOOTH_MAC = 1050;
        int BR_EDR_HANDOVER_READ_TRANSPORT_BLOCK = 1060;
        int GET_PROFILES_VIA_SDP = 1070;
        int DISCOVER_DEVICE = 1080;
        int CANCEL_DISCOVERY = 1090;
        int REMOVE_BOND = 1100;
        int CANCEL_BOND = 1110;
        int CREATE_BOND = 1120;
        int CONNECT_PROFILE = 1130;
        int DISABLE_BLUETOOTH = 1140;
        int ENABLE_BLUETOOTH = 1150;
        int MAGIC_PAIR_END = 1160;
        int SECRET_HANDSHAKE = 1170;
        int WRITE_ACCOUNT_KEY = 1180;
        int WRITE_TO_FOOTPRINTS = 1190;
        int PASSKEY_EXCHANGE = 1200;
        int DEVICE_RECOGNIZED = 1210;
        int GET_LOCAL_PUBLIC_ADDRESS = 1220;
        int DIRECTLY_CONNECTED_TO_PROFILE = 1230;
        int DEVICE_ALIAS_CHANGED = 1240;
        int WRITE_DEVICE_NAME = 1250;
        int UPDATE_PROVIDER_NAME_START = 1260;
        int UPDATE_PROVIDER_NAME_END = 1270;
        int READ_FIRMWARE_VERSION = 1280;
        int RETROACTIVE_PAIR_START = 1290;
        int RETROACTIVE_PAIR_END = 1300;
        int SUBSEQUENT_PAIR_START = 1310;
        int SUBSEQUENT_PAIR_END = 1320;
        int BISTO_PAIR_START = 1330;
        int BISTO_PAIR_END = 1340;
        int REMOTE_PAIR_START = 1350;
        int REMOTE_PAIR_END = 1360;
        int BEFORE_CREATE_BOND = 1370;
        int BEFORE_CREATE_BOND_BONDING = 1380;
        int BEFORE_CREATE_BOND_BONDED = 1390;
        int BEFORE_CONNECT_PROFILE = 1400;
        int HANDLE_PAIRING_REQUEST = 1410;
        int SECRET_HANDSHAKE_GATT_COMMUNICATION = 1420;
        int GATT_CONNECTION_AND_SECRET_HANDSHAKE = 1430;
        int CHECK_SIGNAL_AFTER_HANDSHAKE = 1440;
        int RECOVER_BY_RETRY_GATT = 1450;
        int RECOVER_BY_RETRY_HANDSHAKE = 1460;
        int RECOVER_BY_RETRY_HANDSHAKE_RECONNECT = 1470;
        int GATT_HANDSHAKE_MANUAL_RETRY_ATTEMPTS = 1480;
        int PAIR_WITH_CACHED_MODEL_ID = 1490;
        int DIRECTLY_CONNECT_PROFILE_WITH_CACHED_ADDRESS = 1500;
        int PAIR_WITH_NEW_MODEL = 1510;
    }

    private NearbyEventIntDefs() {}
}
