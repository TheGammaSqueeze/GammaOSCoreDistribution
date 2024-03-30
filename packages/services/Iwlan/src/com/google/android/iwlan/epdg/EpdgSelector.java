/*
 * Copyright 2020 The Android Open Source Project
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

package com.google.android.iwlan.epdg;

import android.content.Context;
import android.net.DnsResolver;
import android.net.DnsResolver.DnsException;
import android.net.InetAddresses;
import android.net.Network;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telephony.CarrierConfigManager;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoTdscdma;
import android.telephony.CellInfoWcdma;
import android.telephony.DataFailCause;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import com.google.android.iwlan.ErrorPolicyManager;
import com.google.android.iwlan.IwlanError;
import com.google.android.iwlan.IwlanHelper;
import com.google.android.iwlan.epdg.NaptrDnsResolver.NaptrTarget;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;

public class EpdgSelector {
    private static final String TAG = "EpdgSelector";
    private Context mContext;
    private int mSlotId;
    private static ConcurrentHashMap<Integer, EpdgSelector> mSelectorInstances =
            new ConcurrentHashMap<>();
    private int mV4PcoId = -1;
    private int mV6PcoId = -1;
    private byte[] mV4PcoData = null;
    private byte[] mV6PcoData = null;
    @NonNull private ErrorPolicyManager mErrorPolicyManager;

    private static final long DNS_RESOLVER_TIMEOUT_DURATION_SEC = 5L;

    final Comparator<InetAddress> inetAddressComparator =
            new Comparator<InetAddress>() {
                @Override
                public int compare(InetAddress ip1, InetAddress ip2) {
                    if ((ip1 instanceof Inet4Address) && (ip2 instanceof Inet6Address)) {
                        return -1;
                    } else if ((ip1 instanceof Inet6Address) && (ip2 instanceof Inet4Address)) {
                        return 1;
                    } else {
                        return 0;
                    }
                }
            };

    public static final int PROTO_FILTER_IPV4 = 0;
    public static final int PROTO_FILTER_IPV6 = 1;
    public static final int PROTO_FILTER_IPV4V6 = 2;

    @IntDef({PROTO_FILTER_IPV4, PROTO_FILTER_IPV6, PROTO_FILTER_IPV4V6})
    @interface ProtoFilter {}

    public interface EpdgSelectorCallback {
        /*gives priority ordered list of addresses*/
        void onServerListChanged(int transactionId, ArrayList<InetAddress> validIPList);

        void onError(int transactionId, IwlanError error);
    }

    @VisibleForTesting
    EpdgSelector(Context context, int slotId) {
        mContext = context;
        mSlotId = slotId;

        mErrorPolicyManager = ErrorPolicyManager.getInstance(mContext, mSlotId);
    }

    public static EpdgSelector getSelectorInstance(Context context, int slotId) {
        mSelectorInstances.computeIfAbsent(slotId, k -> new EpdgSelector(context, slotId));
        return mSelectorInstances.get(slotId);
    }

    public boolean setPcoData(int pcoId, byte[] pcoData) {
        Log.d(TAG, "onReceive PcoId:" + String.format("0x%04x", pcoId) + " PcoData:" + pcoData);

        int PCO_ID_IPV6 =
                IwlanHelper.getConfig(
                        CarrierConfigManager.Iwlan.KEY_EPDG_PCO_ID_IPV6_INT, mContext, mSlotId);
        int PCO_ID_IPV4 =
                IwlanHelper.getConfig(
                        CarrierConfigManager.Iwlan.KEY_EPDG_PCO_ID_IPV4_INT, mContext, mSlotId);

        Log.d(
                TAG,
                "PCO_ID_IPV6:"
                        + String.format("0x%04x", PCO_ID_IPV6)
                        + " PCO_ID_IPV4:"
                        + String.format("0x%04x", PCO_ID_IPV4));

        if (pcoId == PCO_ID_IPV4) {
            mV4PcoId = pcoId;
            mV4PcoData = pcoData;
            return true;
        } else if (pcoId == PCO_ID_IPV6) {
            mV6PcoId = pcoId;
            mV6PcoData = pcoData;
            return true;
        }

        return false;
    }

    public void clearPcoData() {
        Log.d(TAG, "Clear PCO data");
        mV4PcoId = -1;
        mV6PcoId = -1;
        mV4PcoData = null;
        mV6PcoData = null;
    }

    private Map.Entry<String, List<InetAddress>> getIP(
            String domainName, int filter, List<InetAddress> validIpList, Network network) {
        List<InetAddress> ipList = new ArrayList<InetAddress>();

        // Get All IP for each domain name
        Log.d(TAG, "Input domainName : " + domainName);

        if (InetAddresses.isNumericAddress(domainName)) {
            try {
                Log.d(TAG, domainName + " is a numeric ip address");
                ipList.add(InetAddress.getByName(domainName));
            } catch (UnknownHostException e) {
                Log.e(TAG, "Exception when resolving domainName : " + domainName + ".", e);
            }
        } else {
            try {
                CompletableFuture<List<InetAddress>> result = new CompletableFuture();
                final DnsResolver.Callback<List<InetAddress>> cb =
                        new DnsResolver.Callback<List<InetAddress>>() {
                            @Override
                            public void onAnswer(
                                    @NonNull final List<InetAddress> answer, final int rcode) {
                                if (rcode != 0) {
                                    Log.e(TAG, "DnsResolver Response Code = " + rcode);
                                }
                                result.complete(answer);
                            }

                            @Override
                            public void onError(@Nullable final DnsResolver.DnsException error) {
                                Log.e(TAG, "Resolve DNS with error : " + error);
                                result.completeExceptionally(error);
                            }
                        };
                DnsResolver.getInstance()
                        .query(network, domainName, DnsResolver.FLAG_EMPTY, r -> r.run(), null, cb);
                ipList =
                        new ArrayList<>(
                                result.get(DNS_RESOLVER_TIMEOUT_DURATION_SEC, TimeUnit.SECONDS));
            } catch (ExecutionException e) {
                Log.e(TAG, "Cause of ExecutionException: ", e.getCause());
            } catch (InterruptedException e) {
                if (Thread.currentThread().interrupted()) {
                    Thread.currentThread().interrupt();
                }
                Log.e(TAG, "InterruptedException: ", e);
            } catch (TimeoutException e) {
                Log.e(TAG, "TimeoutException: ", e);
            }
        }

        List<InetAddress> filteredIpList = new ArrayList<>();
        // Filter the IP list by input ProtoFilter
        for (InetAddress ipAddress : ipList) {
            switch (filter) {
                case PROTO_FILTER_IPV4:
                    if (ipAddress instanceof Inet4Address) {
                        filteredIpList.add(ipAddress);
                    }
                    break;
                case PROTO_FILTER_IPV6:
                    if (!IwlanHelper.isIpv4EmbeddedIpv6Address(ipAddress)) {
                        filteredIpList.add(ipAddress);
                    }
                    break;
                case PROTO_FILTER_IPV4V6:
                    filteredIpList.add(ipAddress);
                    break;
                default:
                    Log.d(TAG, "Invalid ProtoFilter : " + filter);
            }
        }
        validIpList.addAll(filteredIpList);

        return Map.entry(domainName, filteredIpList);
    }

    private String[] getPlmnList() {
        List<String> plmnsFromSubInfo = new ArrayList<>();

        List<String> plmnsFromCarrierConfig =
                new ArrayList<>(
                        Arrays.asList(
                                IwlanHelper.getConfig(
                                        CarrierConfigManager.Iwlan.KEY_MCC_MNCS_STRING_ARRAY,
                                        mContext,
                                        mSlotId)));
        Log.d(TAG, "plmnsFromCarrierConfig:" + plmnsFromCarrierConfig);

        // Get Ehplmns & mccmnc from SubscriptionManager
        SubscriptionManager subscriptionManager =
                mContext.getSystemService(SubscriptionManager.class);
        if (subscriptionManager == null) {
            Log.e(TAG, "SubscriptionManager is NULL");
            return plmnsFromCarrierConfig.toArray(new String[plmnsFromCarrierConfig.size()]);
        }

        SubscriptionInfo subInfo =
                subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(mSlotId);
        if (subInfo == null) {
            Log.e(TAG, "SubscriptionInfo is NULL");
            return plmnsFromCarrierConfig.toArray(new String[plmnsFromCarrierConfig.size()]);
        }

        // There are three sources of plmns - sim plmn, plmn list from carrier config and
        // Ehplmn list from subscription info.
        // The plmns are prioritized as follows:
        // 1. Sim plmn
        // 2. Plmns common to both lists.
        // 3. Remaining plmns in the lists.
        List<String> combinedList = new ArrayList<>();
        // Get MCCMNC from IMSI
        String plmnFromImsi =
                new StringBuilder()
                        .append(subInfo.getMccString())
                        .append("-")
                        .append(subInfo.getMncString())
                        .toString();
        combinedList.add(plmnFromImsi);

        // Get Ehplmns from TelephonyManager
        for (String ehplmn : getEhplmns()) {
            if (ehplmn.length() == 5 || ehplmn.length() == 6) {
                StringBuilder str = new StringBuilder(ehplmn);
                str.insert(3, "-");
                plmnsFromSubInfo.add(str.toString());
            }
        }

        Log.d(TAG, "plmnsFromSubInfo:" + plmnsFromSubInfo);

        // To avoid double adding plmn from imsi
        plmnsFromCarrierConfig.removeIf(i -> i.equals(plmnFromImsi));
        plmnsFromSubInfo.removeIf(i -> i.equals(plmnFromImsi));

        for (Iterator<String> iterator = plmnsFromCarrierConfig.iterator(); iterator.hasNext(); ) {
            String plmn = iterator.next();
            if (plmnsFromSubInfo.contains(plmn)) {
                combinedList.add(plmn);
                plmnsFromSubInfo.remove(plmn);
                iterator.remove();
            }
        }

        combinedList.addAll(plmnsFromSubInfo);
        combinedList.addAll(plmnsFromCarrierConfig);

        Log.d(TAG, "Final plmn list:" + combinedList);
        return combinedList.toArray(new String[combinedList.size()]);
    }

    private ArrayList<InetAddress> removeDuplicateIp(List<InetAddress> validIpList) {
        ArrayList<InetAddress> resultIpList = new ArrayList<InetAddress>();

        for (Iterator<InetAddress> iterator = validIpList.iterator(); iterator.hasNext(); ) {
            InetAddress validIp = iterator.next();

            if (!resultIpList.contains(validIp)) {
                resultIpList.add(validIp);
            }
        }

        return resultIpList;
    }

    private String[] splitMccMnc(String plmn) {
        String[] mccmnc = plmn.split("-");
        mccmnc[1] = String.format("%03d", Integer.parseInt(mccmnc[1]));
        return mccmnc;
    }

    private List<String> getEhplmns() {
        TelephonyManager mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        mTelephonyManager =
                mTelephonyManager.createForSubscriptionId(IwlanHelper.getSubId(mContext, mSlotId));

        if (mTelephonyManager == null) {
            Log.e(TAG, "TelephonyManager is NULL");
            return new ArrayList<String>();
        } else {
            return mTelephonyManager.getEquivalentHomePlmns();
        }
    }

    private void resolutionMethodStatic(
            int filter, List<InetAddress> validIpList, boolean isRoaming, Network network) {
        String[] domainNames = null;

        Log.d(TAG, "STATIC Method");

        // Get the static domain names from carrier config
        // Config obtained in form of a list of domain names separated by
        // a delimeter is only used for testing purpose.
        if (!inSameCountry()) {
            domainNames =
                    getDomainNames(
                            CarrierConfigManager.Iwlan.KEY_EPDG_STATIC_ADDRESS_ROAMING_STRING);
        }
        if (domainNames == null
                && (domainNames =
                                getDomainNames(
                                        CarrierConfigManager.Iwlan.KEY_EPDG_STATIC_ADDRESS_STRING))
                        == null) {
            Log.d(TAG, "Static address string is null");
            return;
        }

        Log.d(TAG, "Static Domain Names: " + Arrays.toString(domainNames));
        for (String domainName : domainNames) {
            getIP(domainName, filter, validIpList, network);
        }
    }

    private String[] getDomainNames(String key) {
        String configValue = (String) IwlanHelper.getConfig(key, mContext, mSlotId);
        if (configValue == null || configValue.isEmpty()) {
            Log.d(TAG, key + " string is null");
            return null;
        }
        return configValue.split(",");
    }

    private boolean inSameCountry() {
        boolean inSameCountry = true;

        TelephonyManager tm = mContext.getSystemService(TelephonyManager.class);
        tm = tm.createForSubscriptionId(IwlanHelper.getSubId(mContext, mSlotId));

        if (tm != null) {
            String simCountry = tm.getSimCountryIso();
            String currentCountry = IwlanHelper.getLastKnownCountryCode(mContext);
            if (!TextUtils.isEmpty(simCountry) && !TextUtils.isEmpty(currentCountry)) {
                Log.d(TAG, "simCountry = " + simCountry + ", currentCountry = " + currentCountry);
                inSameCountry = simCountry.equalsIgnoreCase(currentCountry);
            }
        }

        return inSameCountry;
    }

    private Map<String, List<InetAddress>> resolutionMethodPlmn(
            int filter, List<InetAddress> validIpList, boolean isEmergency, Network network) {
        String[] plmnList;
        StringBuilder domainName = new StringBuilder();
        Map<String, List<InetAddress>> domainNameToIpAddr = new LinkedHashMap<>();

        Log.d(TAG, "PLMN Method");

        plmnList = getPlmnList();
        for (String plmn : plmnList) {
            String[] mccmnc = splitMccMnc(plmn);
            /*
             * Operator Identifier based ePDG FQDN format:
             * epdg.epc.mnc<MNC>.mcc<MCC>.pub.3gppnetwork.org
             *
             * Operator Identifier based Emergency ePDG FQDN format:
             * sos.epdg.epc.mnc<MNC>.mcc<MCC>.pub.3gppnetwork.org
             */
            if (isEmergency) {
                domainName = new StringBuilder();
                domainName
                        .append("sos.")
                        .append("epdg.epc.mnc")
                        .append(mccmnc[1])
                        .append(".mcc")
                        .append(mccmnc[0])
                        .append(".pub.3gppnetwork.org");
                getIP(domainName.toString(), filter, validIpList, network);
                domainName.setLength(0);
            }
            // For emergency PDN setup, still adding FQDN without "sos" header as second priority
            // because some operator doesn't support hostname with "sos" prefix.
            domainName
                    .append("epdg.epc.mnc")
                    .append(mccmnc[1])
                    .append(".mcc")
                    .append(mccmnc[0])
                    .append(".pub.3gppnetwork.org");
            Map.Entry<String, List<InetAddress>> entry =
                    getIP(domainName.toString(), filter, validIpList, network);
            domainNameToIpAddr.put(entry.getKey(), entry.getValue());
            domainName.setLength(0);
        }
        return domainNameToIpAddr;
    }

    private void resolutionMethodCellularLoc(
            int filter, List<InetAddress> validIpList, boolean isEmergency, Network network) {
        String[] plmnList;
        StringBuilder domainName = new StringBuilder();

        Log.d(TAG, "CELLULAR_LOC Method");

        TelephonyManager mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        mTelephonyManager =
                mTelephonyManager.createForSubscriptionId(IwlanHelper.getSubId(mContext, mSlotId));

        if (mTelephonyManager == null) {
            Log.e(TAG, "TelephonyManager is NULL");
            return;
        }

        List<CellInfo> cellInfoList = mTelephonyManager.getAllCellInfo();
        if (cellInfoList == null) {
            Log.e(TAG, "cellInfoList is NULL");
            return;
        }

        for (CellInfo cellInfo : cellInfoList) {
            if (!cellInfo.isRegistered()) {
                continue;
            }

            if (cellInfo instanceof CellInfoGsm) {
                CellIdentityGsm gsmCellId = ((CellInfoGsm) cellInfo).getCellIdentity();
                String lacString = String.format("%04x", gsmCellId.getLac());

                lacDomainNameResolution(filter, validIpList, lacString, isEmergency, network);
            } else if (cellInfo instanceof CellInfoWcdma) {
                CellIdentityWcdma wcdmaCellId = ((CellInfoWcdma) cellInfo).getCellIdentity();
                String lacString = String.format("%04x", wcdmaCellId.getLac());

                lacDomainNameResolution(filter, validIpList, lacString, isEmergency, network);
            } else if (cellInfo instanceof CellInfoLte) {
                CellIdentityLte lteCellId = ((CellInfoLte) cellInfo).getCellIdentity();
                String tacString = String.format("%04x", lteCellId.getTac());
                String[] tacSubString = new String[2];
                tacSubString[0] = tacString.substring(0, 2);
                tacSubString[1] = tacString.substring(2);

                plmnList = getPlmnList();
                for (String plmn : plmnList) {
                    String[] mccmnc = splitMccMnc(plmn);
                    /**
                     * Tracking Area Identity based ePDG FQDN format:
                     * tac-lb<TAC-low-byte>.tac-hb<TAC-high-byte>.tac.
                     * epdg.epc.mnc<MNC>.mcc<MCC>.pub.3gppnetwork.org
                     *
                     * <p>Tracking Area Identity based Emergency ePDG FQDN format:
                     * tac-lb<TAC-low-byte>.tac-hb<TAC-highbyte>.tac.
                     * sos.epdg.epc.mnc<MNC>.mcc<MCC>.pub.3gppnetwork.org"
                     */
                    domainName
                            .append("tac-lb")
                            .append(tacSubString[1])
                            .append(".tac-hb")
                            .append(tacSubString[0]);
                    if (isEmergency) {
                        domainName.append(".tac.sos.epdg.epc.mnc");
                    } else {
                        domainName.append(".tac.epdg.epc.mnc");
                    }
                    domainName
                            .append(mccmnc[1])
                            .append(".mcc")
                            .append(mccmnc[0])
                            .append(".pub.3gppnetwork.org");
                    getIP(domainName.toString(), filter, validIpList, network);
                    domainName.setLength(0);
                }
            } else if (cellInfo instanceof CellInfoNr) {
                CellIdentityNr nrCellId =
                        (CellIdentityNr) ((CellInfoNr) cellInfo).getCellIdentity();
                String tacString = String.format("%06x", nrCellId.getTac());
                String[] tacSubString = new String[3];
                tacSubString[0] = tacString.substring(0, 2);
                tacSubString[1] = tacString.substring(2, 4);
                tacSubString[2] = tacString.substring(4);

                plmnList = getPlmnList();
                for (String plmn : plmnList) {
                    String[] mccmnc = splitMccMnc(plmn);
                    /**
                     * 5GS Tracking Area Identity based ePDG FQDN format:
                     * tac-lb<TAC-low-byte>.tac-mb<TAC-middle-byte>.tac-hb<TAC-high-byte>.
                     * 5gstac.epdg.epc.mnc<MNC>.mcc<MCC>.pub.3gppnetwork.org
                     *
                     * <p>5GS Tracking Area Identity based Emergency ePDG FQDN format:
                     * tac-lb<TAC-low-byte>.tac-mb<TAC-middle-byte>.tac-hb<TAC-high-byte>.
                     * 5gstac.sos.epdg.epc.mnc<MNC>.mcc<MCC>.pub.3gppnetwork.org
                     */
                    domainName
                            .append("tac-lb")
                            .append(tacSubString[2])
                            .append(".tac-mb")
                            .append(tacSubString[1])
                            .append(".tac-hb")
                            .append(tacSubString[0]);
                    if (isEmergency) {
                        domainName.append(".5gstac.sos.epdg.epc.mnc");
                    } else {
                        domainName.append(".5gstac.epdg.epc.mnc");
                    }
                    domainName
                            .append(mccmnc[1])
                            .append(".mcc")
                            .append(mccmnc[0])
                            .append(".pub.3gppnetwork.org");
                    getIP(domainName.toString(), filter, validIpList, network);
                    domainName.setLength(0);
                }
            } else {
                Log.d(TAG, "This cell doesn't contain LAC/TAC info");
            }
        }
    }

    private void lacDomainNameResolution(
            int filter,
            List<InetAddress> validIpList,
            String lacString,
            boolean isEmergency,
            Network network) {
        String[] plmnList;
        StringBuilder domainName = new StringBuilder();

        plmnList = getPlmnList();
        for (String plmn : plmnList) {
            String[] mccmnc = splitMccMnc(plmn);
            /**
             * Location Area Identity based ePDG FQDN format:
             * lac<LAC>.epdg.epc.mnc<MNC>.mcc<MCC>.pub.3gppnetwork.org
             *
             * <p>Location Area Identity based Emergency ePDG FQDN format:
             * lac<LAC>.sos.epdg.epc.mnc<MNC>.mcc<MCC>.pub.3gppnetwork.org
             */
            domainName.append("lac").append(lacString);
            if (isEmergency) {
                domainName.append(".sos.epdg.epc.mnc");
            } else {
                domainName.append(".epdg.epc.mnc");
            }
            domainName
                    .append(mccmnc[1])
                    .append(".mcc")
                    .append(mccmnc[0])
                    .append(".pub.3gppnetwork.org");

            getIP(domainName.toString(), filter, validIpList, network);
            domainName.setLength(0);
        }
    }

    private void resolutionMethodPco(int filter, List<InetAddress> validIpList) {
        Log.d(TAG, "PCO Method");

        int PCO_ID_IPV6 =
                IwlanHelper.getConfig(
                        CarrierConfigManager.Iwlan.KEY_EPDG_PCO_ID_IPV6_INT, mContext, mSlotId);
        int PCO_ID_IPV4 =
                IwlanHelper.getConfig(
                        CarrierConfigManager.Iwlan.KEY_EPDG_PCO_ID_IPV4_INT, mContext, mSlotId);

        switch (filter) {
            case PROTO_FILTER_IPV4:
                if (mV4PcoId != PCO_ID_IPV4) {
                    clearPcoData();
                } else {
                    getInetAddressWithPcoData(mV4PcoData, validIpList);
                }
                break;
            case PROTO_FILTER_IPV6:
                if (mV6PcoId != PCO_ID_IPV6) {
                    clearPcoData();
                } else {
                    getInetAddressWithPcoData(mV6PcoData, validIpList);
                }
                break;
            case PROTO_FILTER_IPV4V6:
                if ((mV4PcoId != PCO_ID_IPV4) || (mV6PcoId != PCO_ID_IPV6)) {
                    clearPcoData();
                } else {
                    getInetAddressWithPcoData(mV4PcoData, validIpList);
                    getInetAddressWithPcoData(mV6PcoData, validIpList);
                }
                break;
            default:
                Log.d(TAG, "Invalid ProtoFilter : " + filter);
        }
    }

    private void getInetAddressWithPcoData(byte[] pcoData, List<InetAddress> validIpList) {
        InetAddress ipAddress;
        if (pcoData != null && pcoData.length > 0) {
            try {
                ipAddress = InetAddress.getByAddress(pcoData);
                validIpList.add(ipAddress);
            } catch (Exception e) {
                Log.e(TAG, "Exception when querying IP address : " + e);
            }
        } else {
            Log.d(TAG, "Empty PCO data");
        }
    }

    private String composeFqdnWithMccMnc(String mcc, String mnc, boolean isEmergency) {
        StringBuilder domainName = new StringBuilder();

        /*
         * Operator Identifier based ePDG FQDN format:
         * epdg.epc.mnc<MNC>.mcc<MCC>.pub.3gppnetwork.org
         *
         * Operator Identifier based Emergency ePDG FQDN format:
         * sos.epdg.epc.mnc<MNC>.mcc<MCC>.pub.3gppnetwork.org
         */
        domainName.setLength(0);
        if (isEmergency) {
            domainName.append("sos.");
        }
        domainName
                .append("epdg.epc.mnc")
                .append(mnc)
                .append(".mcc")
                .append(mcc)
                .append(".pub.3gppnetwork.org");

        return domainName.toString();
    }

    private boolean isRegisteredWith3GPP(TelephonyManager telephonyManager) {
        List<CellInfo> cellInfoList = telephonyManager.getAllCellInfo();
        if (cellInfoList == null) {
            Log.e(TAG, "cellInfoList is NULL");
        } else {
            for (CellInfo cellInfo : cellInfoList) {
                if (!cellInfo.isRegistered()) {
                    continue;
                }
                if (cellInfo instanceof CellInfoGsm
                        || cellInfo instanceof CellInfoTdscdma
                        || cellInfo instanceof CellInfoWcdma
                        || cellInfo instanceof CellInfoLte
                        || cellInfo instanceof CellInfoNr) {
                    return true;
                }
            }
        }
        return false;
    }

    private void processNaptrResponse(
            int filter,
            List<InetAddress> validIpList,
            boolean isEmergency,
            Network network,
            boolean isRegisteredWith3GPP,
            List<NaptrTarget> naptrResponse,
            Set<String> plmnsFromCarrierConfig,
            String registeredhostName) {
        Set<String> resultSet = new LinkedHashSet<>();

        for (NaptrTarget target : naptrResponse) {
            Log.d(TAG, "NaptrTarget - name: " + target.mName);
            Log.d(TAG, "NaptrTarget - type: " + target.mType);
            if (target.mType == NaptrDnsResolver.TYPE_A) {
                resultSet.add(target.mName);
            }
        }

        /*
         * As 3GPP TS 23.402 4.5.4.5 bullet 2a,
         * if the device registers via 3GPP and its PLMN info is in the NAPTR response,
         * try to connect ePDG with this PLMN info.
         */
        if (isRegisteredWith3GPP) {
            if (resultSet.contains(registeredhostName)) {
                getIP(registeredhostName, filter, validIpList, network);
                resultSet.remove(registeredhostName);
            }
        }

        /*
         * As 3GPP TS 23.402 4.5.4.5 bullet 2b
         * Check if there is any PLMN in both ePDG selection information and the DNS response
         */
        for (String plmn : plmnsFromCarrierConfig) {
            String[] mccmnc = splitMccMnc(plmn);
            String carrierConfighostName = composeFqdnWithMccMnc(mccmnc[0], mccmnc[1], isEmergency);

            if (resultSet.contains(carrierConfighostName)) {
                getIP(carrierConfighostName, filter, validIpList, network);
                resultSet.remove(carrierConfighostName);
            }
        }

        /*
         * Do FQDN with the remaining PLMNs in the ResultSet
         */
        for (String result : resultSet) {
            getIP(result, filter, validIpList, network);
        }
    }

    private void resolutionMethodVisitedCountry(
            int filter, List<InetAddress> validIpList, boolean isEmergency, Network network) {
        StringBuilder domainName = new StringBuilder();

        TelephonyManager telephonyManager = mContext.getSystemService(TelephonyManager.class);
        telephonyManager =
                telephonyManager.createForSubscriptionId(IwlanHelper.getSubId(mContext, mSlotId));

        if (telephonyManager == null) {
            Log.e(TAG, "TelephonyManager is NULL");
            return;
        }

        final boolean isRegisteredWith3GPP = isRegisteredWith3GPP(telephonyManager);

        // Get ePDG selection information from CarrierConfig
        final Set<String> plmnsFromCarrierConfig =
                new LinkedHashSet<>(
                        Arrays.asList(
                                IwlanHelper.getConfig(
                                        CarrierConfigManager.Iwlan.KEY_MCC_MNCS_STRING_ARRAY,
                                        mContext,
                                        mSlotId)));

        final String cellMcc = telephonyManager.getNetworkOperator().substring(0, 3);
        final String cellMnc = telephonyManager.getNetworkOperator().substring(3);
        final String plmnFromNetwork =
                new StringBuilder().append(cellMcc).append("-").append(cellMnc).toString();
        final String registeredhostName = composeFqdnWithMccMnc(cellMcc, cellMnc, isEmergency);

        /*
        * As TS 23 402 4.5.4.4 bullet 3a
        * If the UE determines to be located in a country other than its home country
        * If the UE is registered via 3GPP access to a PLMN and this PLMN matches an entry
          in the ePDG selection information, then the UE shall select an ePDG in this PLMN.
        */
        if (isRegisteredWith3GPP) {
            if (plmnsFromCarrierConfig.contains(plmnFromNetwork)) {
                getIP(registeredhostName, filter, validIpList, network);
            }
        }

        /*
         * Visited Country FQDN format:
         * epdg.epc.mcc<MCC>.visited-country.pub.3gppnetwork.org
         *
         * Visited Country Emergency ePDG FQDN format:
         * sos.epdg.epc.mcc<MCC>.visited-country.pub.3gppnetwork.org
         */
        if (isEmergency) {
            domainName.append("sos.");
        }
        domainName
                .append("epdg.epc.mcc")
                .append(cellMcc)
                .append(".visited-country.pub.3gppnetwork.org");

        Log.d(TAG, "Visited Country FQDN with " + domainName.toString());

        CompletableFuture<List<NaptrTarget>> naptrDnsResult = new CompletableFuture<>();
        DnsResolver.Callback<List<NaptrTarget>> naptrDnsCb =
                new DnsResolver.Callback<List<NaptrTarget>>() {
                    @Override
                    public void onAnswer(@NonNull final List<NaptrTarget> answer, final int rcode) {
                        if (rcode == 0 && answer.size() != 0) {
                            naptrDnsResult.complete(answer);
                        } else {
                            naptrDnsResult.completeExceptionally(new UnknownHostException());
                        }
                    }

                    @Override
                    public void onError(@Nullable final DnsException error) {
                        naptrDnsResult.completeExceptionally(error);
                    }
                };
        NaptrDnsResolver.query(network, domainName.toString(), r -> r.run(), null, naptrDnsCb);

        try {
            final List<NaptrTarget> naptrResponse =
                    naptrDnsResult.get(DNS_RESOLVER_TIMEOUT_DURATION_SEC, TimeUnit.SECONDS);
            // Check if there is any record in the NAPTR response
            if (naptrResponse != null && naptrResponse.size() > 0) {
                processNaptrResponse(
                        filter,
                        validIpList,
                        isEmergency,
                        network,
                        isRegisteredWith3GPP,
                        naptrResponse,
                        plmnsFromCarrierConfig,
                        registeredhostName);
            }
        } catch (ExecutionException e) {
            Log.e(TAG, "Cause of ExecutionException: ", e.getCause());
        } catch (InterruptedException e) {
            if (Thread.currentThread().interrupted()) {
                Thread.currentThread().interrupt();
            }
            Log.e(TAG, "InterruptedException: ", e);
        } catch (TimeoutException e) {
            Log.e(TAG, "TimeoutException: ", e);
        }
    }

    public IwlanError getValidatedServerList(
            int transactionId,
            @ProtoFilter int filter,
            boolean isRoaming,
            boolean isEmergency,
            @NonNull Network network,
            EpdgSelectorCallback selectorCallback) {

        Runnable doValidation =
                () -> {
                    List<InetAddress> validIpList = new ArrayList<>();
                    Log.d(TAG, "Processing request with transactionId: " + transactionId);

                    int[] addrResolutionMethods =
                            IwlanHelper.getConfig(
                                    CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                                    mContext,
                                    mSlotId);

                    final boolean isVisitedCountryMethodRequired =
                            Arrays.stream(addrResolutionMethods)
                                    .anyMatch(
                                            i ->
                                                    i
                                                            == CarrierConfigManager.Iwlan
                                                                    .EPDG_ADDRESS_VISITED_COUNTRY);

                    // In the visited country
                    if (isRoaming && !inSameCountry() && isVisitedCountryMethodRequired) {
                        resolutionMethodVisitedCountry(filter, validIpList, isEmergency, network);
                    }

                    Map<String, List<InetAddress>> plmnDomainNamesToIpAddress = null;
                    for (int addrResolutionMethod : addrResolutionMethods) {
                        switch (addrResolutionMethod) {
                            case CarrierConfigManager.Iwlan.EPDG_ADDRESS_STATIC:
                                resolutionMethodStatic(filter, validIpList, isRoaming, network);
                                break;

                            case CarrierConfigManager.Iwlan.EPDG_ADDRESS_PLMN:
                                plmnDomainNamesToIpAddress =
                                        resolutionMethodPlmn(
                                                filter, validIpList, isEmergency, network);
                                break;

                            case CarrierConfigManager.Iwlan.EPDG_ADDRESS_PCO:
                                resolutionMethodPco(filter, validIpList);
                                break;

                            case CarrierConfigManager.Iwlan.EPDG_ADDRESS_CELLULAR_LOC:
                                resolutionMethodCellularLoc(
                                        filter, validIpList, isEmergency, network);
                                break;

                            default:
                                Log.d(
                                        TAG,
                                        "Incorrect address resolution method "
                                                + addrResolutionMethod);
                        }
                    }

                    if (selectorCallback != null) {
                        if (mErrorPolicyManager.getMostRecentDataFailCause()
                                == DataFailCause.IWLAN_CONGESTION) {
                            int numFqdns = plmnDomainNamesToIpAddress.size();
                            int index = mErrorPolicyManager.getCurrentFqdnIndex(numFqdns);
                            if (index >= 0 && index < numFqdns) {
                                Object[] keys = plmnDomainNamesToIpAddress.keySet().toArray();
                                validIpList = plmnDomainNamesToIpAddress.get(keys[index]);
                            } else {
                                Log.w(
                                        TAG,
                                        "CONGESTION error handling- invalid index: "
                                                + index
                                                + " number of PLMN FQDNs: "
                                                + numFqdns);
                            }
                        }

                        if (!validIpList.isEmpty()) {
                            Collections.sort(validIpList, inetAddressComparator);
                            selectorCallback.onServerListChanged(
                                    transactionId, removeDuplicateIp(validIpList));
                        } else {
                            selectorCallback.onError(
                                    transactionId,
                                    new IwlanError(
                                            IwlanError.EPDG_SELECTOR_SERVER_SELECTION_FAILED));
                        }
                    }
                };
        Thread subThread = new Thread(doValidation);
        subThread.start();
        return new IwlanError(IwlanError.NO_ERROR);
    }
}
