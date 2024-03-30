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

import android.hardware.radio.network.CellConnectionStatus;
import android.hardware.radio.network.CellInfo;
import android.hardware.radio.network.CellInfoLte;
import android.hardware.radio.network.CellInfoRatSpecificInfo;
import android.hardware.radio.network.CellInfoWcdma;
import android.hardware.radio.network.RegState;
import android.telephony.RadioAccessFamily;
import android.telephony.ServiceState;
import android.util.Log;

import com.android.internal.telephony.RILConstants;

import java.util.ArrayList;

public class MockNetworkService {
    private static final String TAG = "MockNetworkService";

    // Grouping of RAFs
    // 2G
    public static final int GSM =
            RadioAccessFamily.RAF_GSM | RadioAccessFamily.RAF_GPRS | RadioAccessFamily.RAF_EDGE;
    public static final int CDMA =
            RadioAccessFamily.RAF_IS95A | RadioAccessFamily.RAF_IS95B | RadioAccessFamily.RAF_1xRTT;
    // 3G
    public static final int EVDO =
            RadioAccessFamily.RAF_EVDO_0
                    | RadioAccessFamily.RAF_EVDO_A
                    | RadioAccessFamily.RAF_EVDO_B
                    | RadioAccessFamily.RAF_EHRPD;
    public static final int HS =
            RadioAccessFamily.RAF_HSUPA
                    | RadioAccessFamily.RAF_HSDPA
                    | RadioAccessFamily.RAF_HSPA
                    | RadioAccessFamily.RAF_HSPAP;
    public static final int WCDMA = HS | RadioAccessFamily.RAF_UMTS;
    // 4G
    public static final int LTE = RadioAccessFamily.RAF_LTE | RadioAccessFamily.RAF_LTE_CA;
    // 5G
    public static final int NR = RadioAccessFamily.RAF_NR;

    static final int MOCK_CARRIER_NO_SERVICE = 0;
    // TODO: Integrate carrier network parameters with SIM profile
    static final int MOCK_CARRIER_CHT = 1;
    static final int MOCK_CARRIER_FET = 2;

    // Network status update reason
    static final int NETWORK_UPDATE_PREFERRED_MODE_CHANGE = 1;

    private int mCsRegState = RegState.NOT_REG_MT_NOT_SEARCHING_OP;
    private int mPsRegState = RegState.NOT_REG_MT_NOT_SEARCHING_OP;

    private String mSimPlmn;
    private boolean mIsHomeCamping;
    private boolean mIsRoamingCamping;
    private int mHomeCarrierId;
    private int mRoamingCarrierId;
    private int mInServiceCarrierId;
    private int mHighRat;

    private ArrayList<MockModemCell> mCellList = new ArrayList<MockModemCell>();

    private class MockModemCell {
        private int mCarrierId;

        // Non-AOSP
        public String[] mEHPlmnList;
        public String[] mAllowRoamingList;

        // AOSP
        private CellInfo[] mCells;

        MockModemCell(int carrierConfig) {
            mCarrierId = carrierConfig;
            updateHomeRoamingList();
            updateCellList();
        }

        public int getCarrierId() {
            return mCarrierId;
        }

        public CellInfo[] getCells() {
            return mCells;
        }

        private void updateHomeRoamingList() {
            // TODO: Read from carrier configuration file
            switch (mCarrierId) {
                case MOCK_CARRIER_CHT:
                    mEHPlmnList = new String[] {"46692"};
                    mAllowRoamingList = new String[] {"310026"};
                    break;
                case MOCK_CARRIER_FET:
                    mEHPlmnList = new String[] {"46601"};
                    mAllowRoamingList = new String[] {"310026"};
                    break;
                case MOCK_CARRIER_NO_SERVICE:
                default:
                    break;
            }
        }

        private void updateCellList() {
            // TODO: Read from carrier configuration file
            switch (mCarrierId) {
                case MOCK_CARRIER_NO_SERVICE:
                    break;
                case MOCK_CARRIER_CHT:
                    // LTE Cell configuration
                    CellInfoLte lte = new CellInfoLte();
                    lte.cellIdentityLte = new android.hardware.radio.network.CellIdentityLte();
                    lte.cellIdentityLte.mcc = "466";
                    lte.cellIdentityLte.mnc = "92";
                    lte.cellIdentityLte.ci = 101;
                    lte.cellIdentityLte.pci = 273;
                    lte.cellIdentityLte.tac = 13100;
                    lte.cellIdentityLte.earfcn = 9260;
                    lte.cellIdentityLte.operatorNames =
                            new android.hardware.radio.network.OperatorInfo();
                    lte.cellIdentityLte.operatorNames.alphaLong = "Chung Hwa Telecom";
                    lte.cellIdentityLte.operatorNames.alphaShort = "CHT";
                    lte.cellIdentityLte.operatorNames.operatorNumeric = "46692";
                    lte.cellIdentityLte.additionalPlmns = new String[0];
                    lte.cellIdentityLte.bands = new int[0];

                    lte.signalStrengthLte = new android.hardware.radio.network.LteSignalStrength();
                    lte.signalStrengthLte.signalStrength = 20;
                    lte.signalStrengthLte.rsrp = 71;
                    lte.signalStrengthLte.rsrq = 6;
                    lte.signalStrengthLte.rssnr = 100;
                    lte.signalStrengthLte.cqi = 13;
                    lte.signalStrengthLte.timingAdvance = 0;
                    lte.signalStrengthLte.cqiTableIndex = 1;

                    // WCDMA Cell configuration
                    CellInfoWcdma wcdma = new CellInfoWcdma();
                    wcdma.cellIdentityWcdma =
                            new android.hardware.radio.network.CellIdentityWcdma();
                    wcdma.cellIdentityWcdma.mcc = "466";
                    wcdma.cellIdentityWcdma.mnc = "92";
                    wcdma.cellIdentityWcdma.lac = 9222;
                    wcdma.cellIdentityWcdma.cid = 14549;
                    wcdma.cellIdentityWcdma.psc = 413;
                    wcdma.cellIdentityWcdma.uarfcn = 10613;
                    wcdma.cellIdentityWcdma.operatorNames =
                            new android.hardware.radio.network.OperatorInfo();
                    wcdma.cellIdentityWcdma.operatorNames.alphaLong = "Chung Hwa 3G";
                    wcdma.cellIdentityWcdma.operatorNames.alphaShort = "CHT";
                    wcdma.cellIdentityWcdma.operatorNames.operatorNumeric = "46692";
                    wcdma.cellIdentityWcdma.additionalPlmns = new String[0];

                    wcdma.signalStrengthWcdma =
                            new android.hardware.radio.network.WcdmaSignalStrength();
                    wcdma.signalStrengthWcdma.signalStrength = 20;
                    wcdma.signalStrengthWcdma.bitErrorRate = 3;
                    wcdma.signalStrengthWcdma.rscp = 45;
                    wcdma.signalStrengthWcdma.ecno = 25;

                    // Fill the cells
                    mCells = new CellInfo[2]; // TODO: 2 is read from config file
                    mCells[0] = new CellInfo();
                    mCells[0].registered = false;
                    mCells[0].connectionStatus = CellConnectionStatus.PRIMARY_SERVING;
                    mCells[0].ratSpecificInfo = new CellInfoRatSpecificInfo();
                    mCells[0].ratSpecificInfo.setLte(lte);

                    mCells[1] = new CellInfo();
                    mCells[1].registered = false;
                    mCells[1].connectionStatus = CellConnectionStatus.SECONDARY_SERVING;
                    mCells[1].ratSpecificInfo = new CellInfoRatSpecificInfo();
                    mCells[1].ratSpecificInfo.setWcdma(wcdma);
                    break;
                case MOCK_CARRIER_FET:
                    // WCDMA Cell configuration
                    CellInfoWcdma wcdma2 = new CellInfoWcdma();
                    wcdma2.cellIdentityWcdma =
                            new android.hardware.radio.network.CellIdentityWcdma();
                    wcdma2.cellIdentityWcdma.mcc = "466";
                    wcdma2.cellIdentityWcdma.mnc = "01";
                    wcdma2.cellIdentityWcdma.lac = 8122;
                    wcdma2.cellIdentityWcdma.cid = 16249;
                    wcdma2.cellIdentityWcdma.psc = 413;
                    wcdma2.cellIdentityWcdma.uarfcn = 10613;
                    wcdma2.cellIdentityWcdma.operatorNames =
                            new android.hardware.radio.network.OperatorInfo();
                    wcdma2.cellIdentityWcdma.operatorNames.alphaLong = "Far EasTone";
                    wcdma2.cellIdentityWcdma.operatorNames.alphaShort = "FET";
                    wcdma2.cellIdentityWcdma.operatorNames.operatorNumeric = "46601";
                    wcdma2.cellIdentityWcdma.additionalPlmns = new String[0];

                    wcdma2.signalStrengthWcdma =
                            new android.hardware.radio.network.WcdmaSignalStrength();
                    wcdma2.signalStrengthWcdma.signalStrength = 10;
                    wcdma2.signalStrengthWcdma.bitErrorRate = 6;
                    wcdma2.signalStrengthWcdma.rscp = 55;
                    wcdma2.signalStrengthWcdma.ecno = 15;

                    // Fill the cells
                    mCells = new CellInfo[1];
                    mCells[0] = new CellInfo();
                    mCells[0].registered = false;
                    mCells[0].connectionStatus = CellConnectionStatus.PRIMARY_SERVING;
                    mCells[0].ratSpecificInfo = new CellInfoRatSpecificInfo();
                    mCells[0].ratSpecificInfo.setWcdma(wcdma2);
                    break;
                default:
                    break;
            }
        }

        public android.hardware.radio.network.OperatorInfo getPrimaryCellOperatorInfo() {
            android.hardware.radio.network.OperatorInfo operatorInfo =
                    new android.hardware.radio.network.OperatorInfo();
            for (CellInfo cellInfo : getCells()) {
                if (cellInfo.connectionStatus == CellConnectionStatus.PRIMARY_SERVING) {
                    switch (cellInfo.ratSpecificInfo.getTag()) {
                        case CellInfoRatSpecificInfo.wcdma:
                            operatorInfo =
                                    cellInfo.ratSpecificInfo.getWcdma()
                                            .cellIdentityWcdma
                                            .operatorNames;
                            break;
                        case CellInfoRatSpecificInfo.lte:
                            operatorInfo =
                                    cellInfo.ratSpecificInfo.getLte().cellIdentityLte.operatorNames;
                            break;
                        default:
                            break;
                    }
                }
            }

            return operatorInfo;
        }

        public android.hardware.radio.network.SignalStrength getPrimaryCellSignalStrength() {
            android.hardware.radio.network.SignalStrength signalStrength =
                    new android.hardware.radio.network.SignalStrength();

            signalStrength.gsm = new android.hardware.radio.network.GsmSignalStrength();
            signalStrength.cdma = new android.hardware.radio.network.CdmaSignalStrength();
            signalStrength.evdo = new android.hardware.radio.network.EvdoSignalStrength();
            signalStrength.lte = new android.hardware.radio.network.LteSignalStrength();
            signalStrength.tdscdma = new android.hardware.radio.network.TdscdmaSignalStrength();
            signalStrength.wcdma = new android.hardware.radio.network.WcdmaSignalStrength();
            signalStrength.nr = new android.hardware.radio.network.NrSignalStrength();
            signalStrength.nr.csiCqiReport = new byte[0];

            for (CellInfo cellInfo : getCells()) {
                if (cellInfo.connectionStatus == CellConnectionStatus.PRIMARY_SERVING) {
                    switch (cellInfo.ratSpecificInfo.getTag()) {
                        case CellInfoRatSpecificInfo.wcdma:
                            signalStrength.wcdma =
                                    cellInfo.ratSpecificInfo.getWcdma().signalStrengthWcdma;
                            break;
                        case CellInfoRatSpecificInfo.lte:
                            signalStrength.lte =
                                    cellInfo.ratSpecificInfo.getLte().signalStrengthLte;
                            break;
                        default:
                            break;
                    }
                }
            }

            return signalStrength;
        }

        public int getPrimaryCellRat() {
            int rat = android.hardware.radio.RadioTechnology.UNKNOWN;

            for (CellInfo cellInfo : getCells()) {
                if (cellInfo.connectionStatus == CellConnectionStatus.PRIMARY_SERVING) {
                    switch (cellInfo.ratSpecificInfo.getTag()) {
                        case CellInfoRatSpecificInfo.wcdma:
                            // TODO: Need find an element to assign the rat WCDMA, HSUPA, HSDPA, or
                            // HSPA
                            rat = android.hardware.radio.RadioTechnology.HSPA;
                            break;
                        case CellInfoRatSpecificInfo.lte:
                            rat = android.hardware.radio.RadioTechnology.LTE;
                            break;
                        default:
                            break;
                    }
                }
            }

            return rat;
        }

        public android.hardware.radio.network.CellIdentity getPrimaryCellIdentity() {
            android.hardware.radio.network.CellIdentity cellIdentity =
                    android.hardware.radio.network.CellIdentity.noinit(true);

            for (CellInfo cellInfo : getCells()) {
                if (cellInfo.connectionStatus == CellConnectionStatus.PRIMARY_SERVING) {
                    switch (cellInfo.ratSpecificInfo.getTag()) {
                        case CellInfoRatSpecificInfo.wcdma:
                            cellIdentity.setWcdma(
                                    cellInfo.ratSpecificInfo.getWcdma().cellIdentityWcdma);
                            break;
                        case CellInfoRatSpecificInfo.lte:
                            cellIdentity.setLte(cellInfo.ratSpecificInfo.getLte().cellIdentityLte);
                            break;
                        default:
                            break;
                    }
                }
            }

            return cellIdentity;
        }
    }

    public MockNetworkService() {
        loadMockModemCell(MOCK_CARRIER_CHT);
        loadMockModemCell(MOCK_CARRIER_FET);
    }

    public void loadMockModemCell(int carrierId) {
        if (!mCellList.isEmpty()) {
            for (MockModemCell mmc : mCellList) {
                if (mmc.getCarrierId() == carrierId) {
                    Log.d(TAG, "Carrier ID " + carrierId + " is loaded.");
                    return;
                }
            }
        }

        mCellList.add(new MockModemCell(carrierId));
    }

    private int getHighestRatFromNetworkType(int raf) {
        int rat;
        int networkMode = RadioAccessFamily.getNetworkTypeFromRaf(raf);

        switch (networkMode) {
            case RILConstants.NETWORK_MODE_WCDMA_PREF:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_HSPA;
                break;
            case RILConstants.NETWORK_MODE_GSM_ONLY:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_GSM;
                break;
            case RILConstants.NETWORK_MODE_WCDMA_ONLY:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_HSPA;
                break;
            case RILConstants.NETWORK_MODE_GSM_UMTS:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_HSPA;
                break;
            case RILConstants.NETWORK_MODE_CDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_IS95A;
                break;
            case RILConstants.NETWORK_MODE_LTE_CDMA_EVDO:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_LTE;
                break;
            case RILConstants.NETWORK_MODE_LTE_GSM_WCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_LTE;
                break;
            case RILConstants.NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_LTE;
                break;
            case RILConstants.NETWORK_MODE_LTE_ONLY:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_LTE;
                break;
            case RILConstants.NETWORK_MODE_LTE_WCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_LTE;
                break;
            case RILConstants.NETWORK_MODE_CDMA_NO_EVDO:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_IS95A;
                break;
            case RILConstants.NETWORK_MODE_EVDO_NO_CDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_0;
                break;
            case RILConstants.NETWORK_MODE_GLOBAL:
                // GSM | WCDMA | CDMA | EVDO;
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_HSPA;
                break;
            case RILConstants.NETWORK_MODE_TDSCDMA_ONLY:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_TD_SCDMA;
                break;
            case RILConstants.NETWORK_MODE_TDSCDMA_WCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_HSPA;
                break;
            case RILConstants.NETWORK_MODE_LTE_TDSCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_LTE;
                break;
            case RILConstants.NETWORK_MODE_TDSCDMA_GSM:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_TD_SCDMA;
                break;
            case RILConstants.NETWORK_MODE_LTE_TDSCDMA_GSM:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_LTE;
                break;
            case RILConstants.NETWORK_MODE_TDSCDMA_GSM_WCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_HSPA;
                break;
            case RILConstants.NETWORK_MODE_LTE_TDSCDMA_WCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_LTE;
                break;
            case RILConstants.NETWORK_MODE_LTE_TDSCDMA_GSM_WCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_LTE;
                break;
            case RILConstants.NETWORK_MODE_TDSCDMA_CDMA_EVDO_GSM_WCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_HSPA;
                break;
            case RILConstants.NETWORK_MODE_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_LTE;
                break;
            case RILConstants.NETWORK_MODE_NR_ONLY:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_NR;
                break;
            case RILConstants.NETWORK_MODE_NR_LTE:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_NR;
                break;
            case RILConstants.NETWORK_MODE_NR_LTE_CDMA_EVDO:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_NR;
                break;
            case RILConstants.NETWORK_MODE_NR_LTE_GSM_WCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_NR;
                break;
            case RILConstants.NETWORK_MODE_NR_LTE_CDMA_EVDO_GSM_WCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_NR;
                break;
            case RILConstants.NETWORK_MODE_NR_LTE_WCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_NR;
                break;
            case RILConstants.NETWORK_MODE_NR_LTE_TDSCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_NR;
                break;
            case RILConstants.NETWORK_MODE_NR_LTE_TDSCDMA_GSM:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_NR;
                break;
            case RILConstants.NETWORK_MODE_NR_LTE_TDSCDMA_WCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_NR;
                break;
            case RILConstants.NETWORK_MODE_NR_LTE_TDSCDMA_GSM_WCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_NR;
                break;
            case RILConstants.NETWORK_MODE_NR_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_NR;
                break;
            default:
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN;
                break;
        }
        return rat;
    }

    public android.hardware.radio.network.OperatorInfo getPrimaryCellOperatorInfo() {
        android.hardware.radio.network.OperatorInfo operatorInfo =
                new android.hardware.radio.network.OperatorInfo();

        if (mCsRegState == RegState.REG_HOME || mPsRegState == RegState.REG_HOME) {
            operatorInfo = getCarrierStatus(mHomeCarrierId).getPrimaryCellOperatorInfo();
        } else if (mCsRegState == RegState.REG_ROAMING || mPsRegState == RegState.REG_ROAMING) {
            operatorInfo = getCarrierStatus(mRoamingCarrierId).getPrimaryCellOperatorInfo();
        }

        return operatorInfo;
    }

    public android.hardware.radio.network.CellIdentity getPrimaryCellIdentity() {
        android.hardware.radio.network.CellIdentity cellIdentity =
                android.hardware.radio.network.CellIdentity.noinit(true);

        if (mCsRegState == RegState.REG_HOME || mPsRegState == RegState.REG_HOME) {
            cellIdentity = getCarrierStatus(mHomeCarrierId).getPrimaryCellIdentity();
        } else if (mCsRegState == RegState.REG_ROAMING || mPsRegState == RegState.REG_ROAMING) {
            cellIdentity = getCarrierStatus(mRoamingCarrierId).getPrimaryCellIdentity();
        }

        return cellIdentity;
    }

    public android.hardware.radio.network.CellInfo[] getCells() {
        ArrayList<android.hardware.radio.network.CellInfo> cellInfos = new ArrayList<>();

        for (MockModemCell mmc : mCellList) {
            CellInfo[] cells = mmc.getCells();
            if (cells != null) {
                for (CellInfo cellInfo : cells) {
                    cellInfos.add(cellInfo);
                }
            }
        }

        return cellInfos.stream().toArray(android.hardware.radio.network.CellInfo[]::new);
    }

    public boolean updateHighestRegisteredRat(int raf) {

        int rat = mHighRat;
        mHighRat = getHighestRatFromNetworkType(raf);

        return (rat == mHighRat);
    }

    public void updateNetworkStatus(int reason) {
        if (reason == NETWORK_UPDATE_PREFERRED_MODE_CHANGE) {
            Log.d(TAG, "updateNetworkStatus: NETWORK_UPDATE_PREFERRED_MODE_CHANGE");
            // TODO
        }
    }

    public int getRegistrationRat() {
        int rat = android.hardware.radio.RadioTechnology.UNKNOWN;

        if (mCsRegState == RegState.REG_HOME || mPsRegState == RegState.REG_HOME) {
            rat = getCarrierStatus(mHomeCarrierId).getPrimaryCellRat();
        } else if (mCsRegState == RegState.REG_ROAMING || mPsRegState == RegState.REG_ROAMING) {
            rat = getCarrierStatus(mRoamingCarrierId).getPrimaryCellRat();
        }

        return rat;
    }

    public android.hardware.radio.network.SignalStrength getSignalStrength() {
        android.hardware.radio.network.SignalStrength signalStrength =
                new android.hardware.radio.network.SignalStrength();

        if (mCsRegState == RegState.REG_HOME || mPsRegState == RegState.REG_HOME) {
            signalStrength = getCarrierStatus(mHomeCarrierId).getPrimaryCellSignalStrength();
        } else if (mCsRegState == RegState.REG_ROAMING || mPsRegState == RegState.REG_ROAMING) {
            signalStrength = getCarrierStatus(mRoamingCarrierId).getPrimaryCellSignalStrength();
        } else {
            // TODO
        }

        return signalStrength;
    }

    public int getRegistration(int domain) {
        if (domain == android.hardware.radio.network.Domain.CS) {
            return mCsRegState;
        } else {
            return mPsRegState;
        }
    }

    public boolean isInService() {
        return ((mCsRegState == RegState.REG_HOME)
                || (mPsRegState == RegState.REG_HOME)
                || (mCsRegState == RegState.REG_ROAMING)
                || (mPsRegState == RegState.REG_ROAMING));
    }

    public void updateSimPlmn(String simPlmn) {
        mSimPlmn = simPlmn;

        // Reset mHomeCarrierId and mRoamingCarrierId
        mHomeCarrierId = MOCK_CARRIER_NO_SERVICE;
        mRoamingCarrierId = MOCK_CARRIER_NO_SERVICE;

        if (mSimPlmn == null || mSimPlmn.isEmpty()) return;

        if (mCellList.isEmpty()) return;

        for (MockModemCell mmc : mCellList) {

            if (isHomeCellExisted() && isRoamingCellExisted()) break;

            // Find out which cell is Home cell
            for (String plmn : mmc.mEHPlmnList) {
                if (!isHomeCellExisted() && mSimPlmn.equals(plmn)) {
                    mHomeCarrierId = mmc.getCarrierId();
                    Log.d(TAG, "Cell ID: Home Cell " + mHomeCarrierId);
                }
            }

            // Find out which cell is Home cell
            for (String plmn : mmc.mAllowRoamingList) {
                if (!isRoamingCellExisted() && mSimPlmn.equals(plmn)) {
                    mRoamingCarrierId = mmc.getCarrierId();
                    Log.d(TAG, "Cell ID: Roaming Cell " + mRoamingCarrierId);
                }
            }
        }
    }

    /**
     * Set the device enters IN SERVICE
     *
     * @param isRoaming boolean true if the camping network is Roaming service, otherwise Home
     *     service
     * @param inService boolean true if the deviec enters carrier coverge, otherwise the device
     *     leaves the carrier coverage.
     */
    public void setServiceStatus(boolean isRoaming, boolean inService) {
        if (isRoaming) {
            mIsRoamingCamping = inService;
        } else {
            mIsHomeCamping = inService;
        }
    }

    public boolean getIsHomeCamping() {
        return mIsHomeCamping;
    }

    public boolean getIsRoamingCamping() {
        return mIsRoamingCamping;
    }

    public boolean isHomeCellExisted() {
        return (mHomeCarrierId != MOCK_CARRIER_NO_SERVICE);
    }

    public boolean isRoamingCellExisted() {
        return (mRoamingCarrierId != MOCK_CARRIER_NO_SERVICE);
    }

    public void updateServiceState(int reg) {
        Log.d(TAG, "Cell ID: updateServiceState " + reg);
        switch (reg) {
            case RegState.NOT_REG_MT_SEARCHING_OP:
                mCsRegState = RegState.NOT_REG_MT_SEARCHING_OP;
                mPsRegState = RegState.NOT_REG_MT_SEARCHING_OP;
                break;
            case RegState.REG_HOME:
                mCsRegState = RegState.REG_HOME;
                mPsRegState = RegState.REG_HOME;
                break;
            case RegState.REG_ROAMING:
                mCsRegState = RegState.REG_ROAMING;
                mPsRegState = RegState.REG_ROAMING;
                break;
            case RegState.NOT_REG_MT_NOT_SEARCHING_OP:
            default:
                mCsRegState = RegState.NOT_REG_MT_NOT_SEARCHING_OP;
                mPsRegState = RegState.NOT_REG_MT_NOT_SEARCHING_OP;
                break;
        }

        // TODO: mCsRegState and mPsReState may be changed by the registration denied reason set by
        // TestCase

        for (MockModemCell mmc : mCellList) {
            boolean registered;
            if ((mCsRegState == RegState.REG_HOME || mPsRegState == RegState.REG_HOME)
                    && mHomeCarrierId == mmc.getCarrierId()) {
                registered = true;
            } else if ((mCsRegState == RegState.REG_ROAMING || mPsRegState == RegState.REG_ROAMING)
                    && mRoamingCarrierId == mmc.getCarrierId()) {
                registered = true;
            } else {
                registered = false;
            }

            CellInfo[] cells = mmc.getCells();
            if (cells != null) {
                for (CellInfo cellInfo : cells) {
                    cellInfo.registered = registered;
                }
            }
        }
    }

    public MockModemCell getCarrierStatus(int carrierId) {
        for (MockModemCell mmc : mCellList) {
            if (mmc.getCarrierId() == carrierId) return mmc;
        }

        return null;
    }

    @Override
    public String toString() {
        return "isInService():" + isInService() + " Rat:" + getRegistrationRat() + "";
    }
}
