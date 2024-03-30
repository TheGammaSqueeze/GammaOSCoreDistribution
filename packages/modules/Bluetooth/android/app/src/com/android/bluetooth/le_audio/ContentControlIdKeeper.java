/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

package com.android.bluetooth.le_audio;

import android.bluetooth.BluetoothLeAudio;
import android.os.ParcelUuid;
import android.util.Pair;

import com.android.bluetooth.btservice.ServiceFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * This class keeps Content Control Ids for LE Audio profiles.
 */
public class ContentControlIdKeeper {

    public static final int CCID_INVALID = 0;
    public static final int CCID_MIN = 0x01;
    public static final int CCID_MAX = 0xFF;

    private static SortedSet<Integer> sAssignedCcidList = new TreeSet();
    private static HashMap<ParcelUuid, Pair<Integer, Integer>> sUserMap = new HashMap();
    private static ServiceFactory sServiceFactory = null;

    /**
     * Functions is used to acquire Content Control ID (Ccid). Ccid is connected
     * with a context type  and the user uuid. In most of cases user uuid is the GATT service
     * UUID which makes use of Ccid
     *
     * @param userUuid user identifier (GATT service)
     * @param contextType the context types as defined in {@link BluetoothLeAudio}
     * @return ccid to be used in the Gatt service Ccid characteristic.
    */
    public static synchronized int acquireCcid(ParcelUuid userUuid, int contextType) {
        int ccid = CCID_INVALID;

        if (sAssignedCcidList.size() == 0) {
            ccid = CCID_MIN;
        } else if (sAssignedCcidList.last() < CCID_MAX) {
            ccid = sAssignedCcidList.last() + 1;
        } else if (sAssignedCcidList.first() > CCID_MIN) {
            ccid = sAssignedCcidList.first() - 1;
        } else {
            int first_ccid_avail = sAssignedCcidList.first() + 1;
            while (first_ccid_avail < CCID_MAX - 1) {
                if (!sAssignedCcidList.contains(first_ccid_avail)) {
                    ccid = first_ccid_avail;
                    break;
                }
                first_ccid_avail++;
            }
        }

        if (ccid != CCID_INVALID)  {
            sAssignedCcidList.add(ccid);
            sUserMap.put(userUuid, new Pair(ccid, contextType));

            if (sServiceFactory == null) {
                sServiceFactory = new ServiceFactory();
            }
            /* Notify LeAudioService about new ccid  */
            LeAudioService service = sServiceFactory.getLeAudioService();
            if (service != null) {
                service.setCcidInformation(userUuid, ccid, contextType);
            }
        }
        return ccid;
    }

    /**
     * Release the acquired Ccid
     *
     * @param value Ccid value to release
     */
    public static synchronized void releaseCcid(int value) {
        sAssignedCcidList.remove(value);
        sUserMap.entrySet().removeIf(entry -> entry.getValue().first.equals(value));
    }

    /**
     * Get Ccid information.
     *
     * @return Map of acquired ccids along with the user information.
     */
    public static synchronized Map<ParcelUuid, Pair<Integer, Integer>> getUserCcidMap() {
        return Collections.unmodifiableMap(sUserMap);
    }
}
