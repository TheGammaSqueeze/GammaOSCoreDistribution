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

package com.android.server.nearby.common.ble.util;


/**
 * Ranging utilities embody the physics of converting RF path loss to distance. The free space path
 * loss is proportional to the square of the distance from transmitter to receiver, and to the
 * square of the frequency of the propagation signal.
 */
public final class RangingUtils {
    private static final int MAX_RSSI_VALUE = 126;
    private static final int MIN_RSSI_VALUE = -127;

    private RangingUtils() {
    }

    /* This was original derived in {@link com.google.android.gms.beacon.util.RangingUtils} from
     * <a href="http://en.wikipedia.org/wiki/Free-space_path_loss">Free-space_path_loss</a>.
     * Duplicated here for easy reference.
     *
     * c   = speed of light (2.9979 x 10^8 m/s);
     * f   = frequency (Bluetooth center frequency is 2.44175GHz = 2.44175x10^9 Hz);
     * l   = wavelength (in meters);
     * d   = distance (from transmitter to receiver in meters);
     * dB  = decibels
     * dBm = decibel milliwatts
     *
     *
     * Free-space path loss (FSPL) is proportional to the square of the distance between the
     * transmitter and the receiver, and also proportional to the square of the frequency of the
     * radio signal.
     *
     * FSPL      = (4 * pi * d / l)^2 = (4 * pi * d * f / c)^2
     *
     * FSPL (dB) = 10 * log10((4 * pi * d  * f / c)^2)
     *           = 20 * log10(4 * pi * d * f / c)
     *           = (20 * log10(d)) + (20 * log10(f)) + (20 * log10(4 * pi/c))
     *
     * Calculating constants:
     *
     * FSPL_FREQ        = 20 * log10(f)
     *                  = 20 * log10(2.44175 * 10^9)
     *                  = 187.75
     *
     * FSPL_LIGHT       = 20 * log10(4 * pi/c)
     *                  = 20 * log10(4 * pi/(2.9979 * 10^8))
     *                  = 20 * log10(4 * pi/(2.9979 * 10^8))
     *                  = 20 * log10(41.9172441s * 10^-9)
     *                  = -147.55
     *
     * FSPL_DISTANCE_1M = 20 * log10(1)
     *                  = 0
     *
     * PATH_LOSS_AT_1M  = FSPL_DISTANCE_1M + FSPL_FREQ + FSPL_LIGHT
     *                  =       0          + 187.75    + (-147.55)
     *                  = 40.20db [round to 41db]
     *
     * Note: Rounding up makes us "closer" and makes us more aggressive at showing notifications.
     */
    private static final int RSSI_DROP_OFF_AT_1_M = 41;

    /**
     * Convert target distance and txPower to a RSSI value using the Log-distance path loss model
     * with Path Loss at 1m of 41db.
     *
     * @return RSSI expected at distanceInMeters with device broadcasting at txPower.
     */
    public static int rssiFromTargetDistance(double distanceInMeters, int txPower) {
        /*
         * See <a href="https://en.wikipedia.org/wiki/Log-distance_path_loss_model">
         * Log-distance path loss model</a>.
         *
         * PL      = total path loss in db
         * txPower = TxPower in dbm
         * rssi    = Received signal strength in dbm
         * PL_0    = Path loss at reference distance d_0 {@link RSSI_DROP_OFF_AT_1_M} dbm
         * d       = length of path
         * d_0     = reference distance  (1 m)
         * gamma   = path loss exponent (2 in free space)
         *
         * Log-distance path loss (LDPL) formula:
         *
         * PL = txPower - rssi =                   PL_0          + 10 * gamma  * log_10(d / d_0)
         *      txPower - rssi =            RSSI_DROP_OFF_AT_1_M + 10 * 2 * log_10
         * (distanceInMeters / 1)
         *              - rssi = -txPower + RSSI_DROP_OFF_AT_1_M + 20 * log_10(distanceInMeters)
         *                rssi =  txPower - RSSI_DROP_OFF_AT_1_M - 20 * log_10(distanceInMeters)
         */
        txPower = adjustPower(txPower);
        return distanceInMeters == 0
                ? txPower
                : (int) Math.floor((txPower - RSSI_DROP_OFF_AT_1_M)
                        - 20 * Math.log10(distanceInMeters));
    }

    /**
     * Convert RSSI and txPower to a distance value using the Log-distance path loss model with Path
     * Loss at 1m of 41db.
     *
     * @return distance in meters with device broadcasting at txPower and given RSSI.
     */
    public static double distanceFromRssiAndTxPower(int rssi, int txPower) {
        /*
         * See <a href="https://en.wikipedia.org/wiki/Log-distance_path_loss_model">Log-distance
         * path
         * loss model</a>.
         *
         * PL      = total path loss in db
         * txPower = TxPower in dbm
         * rssi    = Received signal strength in dbm
         * PL_0    = Path loss at reference distance d_0 {@link RSSI_DROP_OFF_AT_1_M} dbm
         * d       = length of path
         * d_0     = reference distance  (1 m)
         * gamma   = path loss exponent (2 in free space)
         *
         * Log-distance path loss (LDPL) formula:
         *
         * PL =    txPower - rssi                               = PL_0 + 10 * gamma  * log_10(d /
         *  d_0)
         *         txPower - rssi               = RSSI_DROP_OFF_AT_1_M + 10 * gamma  * log_10(d /
         *  d_0)
         *         txPower - rssi - RSSI_DROP_OFF_AT_1_M        = 10 * 2 * log_10
         * (distanceInMeters / 1)
         *         txPower - rssi - RSSI_DROP_OFF_AT_1_M        = 20 * log_10(distanceInMeters / 1)
         *        (txPower - rssi - RSSI_DROP_OFF_AT_1_M) / 20  = log_10(distanceInMeters)
         *  10 ^ ((txPower - rssi - RSSI_DROP_OFF_AT_1_M) / 20) = distanceInMeters
         */
        txPower = adjustPower(txPower);
        rssi = adjustPower(rssi);
        return Math.pow(10, (txPower - rssi - RSSI_DROP_OFF_AT_1_M) / 20.0);
    }

    /**
     * Prevents the power from becoming too large or too small.
     */
    private static int adjustPower(int power) {
        if (power > MAX_RSSI_VALUE) {
            return MAX_RSSI_VALUE;
        }
        if (power < MIN_RSSI_VALUE) {
            return MIN_RSSI_VALUE;
        }
        return power;
    }
}

