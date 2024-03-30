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

import static android.net.DnsResolver.TYPE_A;
import static android.net.DnsResolver.TYPE_AAAA;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import static java.util.stream.Collectors.toList;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.DnsResolver;
import android.net.InetAddresses;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoWcdma;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.google.android.iwlan.ErrorPolicyManager;
import com.google.android.iwlan.IwlanError;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class EpdgSelectorTest {
    private static final String TAG = "EpdgSelectorTest";
    private EpdgSelector mEpdgSelector;
    public static final int DEFAULT_SLOT_INDEX = 0;

    private static final String TEST_IP_ADDRESS = "127.0.0.1";
    private static final String TEST_IP_ADDRESS_1 = "127.0.0.2";
    private static final String TEST_IP_ADDRESS_2 = "127.0.0.3";
    private static final String TEST_IP_ADDRESS_3 = "127.0.0.4";
    private static final String TEST_IP_ADDRESS_4 = "127.0.0.5";
    private static final String TEST_IP_ADDRESS_5 = "127.0.0.6";
    private static final String TEST_IPV6_ADDRESS = "0000:0000:0000:0000:0000:0000:0000:0001";

    private static int testPcoIdIPv6 = 0xFF01;
    private static int testPcoIdIPv4 = 0xFF02;

    private String testPcoString = "testPcoData";
    private byte[] pcoData = testPcoString.getBytes();
    private List<String> ehplmnList = new ArrayList<String>();

    @Mock private Context mMockContext;
    @Mock private Network mMockNetwork;
    @Mock private ErrorPolicyManager mMockErrorPolicyManager;
    @Mock private SubscriptionManager mMockSubscriptionManager;
    @Mock private SubscriptionInfo mMockSubscriptionInfo;
    @Mock private CarrierConfigManager mMockCarrierConfigManager;
    @Mock private TelephonyManager mMockTelephonyManager;
    @Mock private SharedPreferences mMockSharedPreferences;
    @Mock private CellInfoGsm mMockCellInfoGsm;
    @Mock private CellIdentityGsm mMockCellIdentityGsm;
    @Mock private CellInfoWcdma mMockCellInfoWcdma;
    @Mock private CellIdentityWcdma mMockCellIdentityWcdma;
    @Mock private CellInfoLte mMockCellInfoLte;
    @Mock private CellIdentityLte mMockCellIdentityLte;
    @Mock private CellInfoNr mMockCellInfoNr;
    @Mock private CellIdentityNr mMockCellIdentityNr;
    @Mock private DnsResolver mMockDnsResolver;

    private PersistableBundle mTestBundle;
    private FakeDns mFakeDns;
    MockitoSession mStaticMockSession;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mStaticMockSession =
                mockitoSession()
                        .mockStatic(DnsResolver.class)
                        .mockStatic(ErrorPolicyManager.class)
                        .startMocking();

        when(ErrorPolicyManager.getInstance(mMockContext, DEFAULT_SLOT_INDEX))
                .thenReturn(mMockErrorPolicyManager);
        mEpdgSelector = new EpdgSelector(mMockContext, DEFAULT_SLOT_INDEX);

        when(mMockContext.getSystemService(eq(SubscriptionManager.class)))
                .thenReturn(mMockSubscriptionManager);

        when(mMockSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(anyInt()))
                .thenReturn(mMockSubscriptionInfo);

        when(mMockSubscriptionInfo.getMccString()).thenReturn("311");

        when(mMockSubscriptionInfo.getMncString()).thenReturn("120");

        when(mMockContext.getSystemService(eq(TelephonyManager.class)))
                .thenReturn(mMockTelephonyManager);

        when(mMockTelephonyManager.createForSubscriptionId(anyInt()))
                .thenReturn(mMockTelephonyManager);

        ehplmnList.add("300120");
        when(mMockTelephonyManager.getEquivalentHomePlmns()).thenReturn(ehplmnList);

        when(mMockTelephonyManager.getSimCountryIso()).thenReturn("ca");

        when(mMockContext.getSharedPreferences(anyString(), anyInt()))
                .thenReturn(mMockSharedPreferences);

        when(mMockSharedPreferences.getString(any(), any())).thenReturn("US");

        // Mock carrier configs with test bundle
        mTestBundle = new PersistableBundle();
        when(mMockContext.getSystemService(eq(CarrierConfigManager.class)))
                .thenReturn(mMockCarrierConfigManager);
        when(mMockCarrierConfigManager.getConfigForSubId(anyInt())).thenReturn(mTestBundle);

        lenient().when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);

        mFakeDns = new FakeDns();
        mFakeDns.startMocking();
    }

    @After
    public void cleanUp() throws Exception {
        mStaticMockSession.finishMocking();
        mFakeDns.clearAll();
    }

    @Test
    public void testStaticMethodPass() throws Exception {
        // Set DnsResolver query mock
        final String testStaticAddress = "epdg.epc.mnc088.mcc888.pub.3gppnetwork.org";
        mFakeDns.setAnswer(testStaticAddress, new String[] {TEST_IP_ADDRESS}, TYPE_A);

        // Set carrier config mock
        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_STATIC});
        mTestBundle.putString(
                CarrierConfigManager.Iwlan.KEY_EPDG_STATIC_ADDRESS_STRING, testStaticAddress);

        ArrayList<InetAddress> testInetAddresses =
                getValidatedServerListWithDefaultParams(false /*isEmergency*/);

        InetAddress expectedAddress = InetAddress.getByName(TEST_IP_ADDRESS);

        assertEquals(testInetAddresses.size(), 1);
        assertEquals(testInetAddresses.get(0), expectedAddress);
    }

    @Test
    public void testRoamStaticMethodPass() throws Exception {
        // Set DnsResolver query mock
        final String testRoamStaticAddress = "epdg.epc.mnc088.mcc888.pub.3gppnetwork.org";
        mFakeDns.setAnswer(testRoamStaticAddress, new String[] {TEST_IP_ADDRESS}, TYPE_A);

        // Set carrier config mock
        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_STATIC});
        mTestBundle.putString(
                CarrierConfigManager.Iwlan.KEY_EPDG_STATIC_ADDRESS_ROAMING_STRING,
                testRoamStaticAddress);

        ArrayList<InetAddress> testInetAddresses =
                getValidatedServerListWithDefaultParams(false /*isEmergency*/);

        InetAddress expectedAddress = InetAddress.getByName(TEST_IP_ADDRESS);

        assertEquals(testInetAddresses.size(), 1);
        assertEquals(testInetAddresses.get(0), expectedAddress);
    }

    @Test
    public void testPlmnResolutionMethod() throws Exception {
        testPlmnResolutionMethod(false);
    }

    @Test
    public void testPlmnResolutionMethodForEmergency() throws Exception {
        testPlmnResolutionMethod(true);
    }

    @Test
    public void testPlmnResolutionMethodWithNoPlmnInCarrierConfig() throws Exception {
        // setUp() fills default values for mcc-mnc
        String expectedFqdn1 = "epdg.epc.mnc120.mcc311.pub.3gppnetwork.org";
        String expectedFqdn2 = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";

        mFakeDns.setAnswer(expectedFqdn1, new String[] {TEST_IP_ADDRESS_1}, TYPE_A);
        mFakeDns.setAnswer(expectedFqdn2, new String[] {TEST_IP_ADDRESS_2}, TYPE_A);

        ArrayList<InetAddress> testInetAddresses =
                getValidatedServerListWithDefaultParams(false /*isEmergency*/);

        assertEquals(testInetAddresses.size(), 2);
        assertTrue(testInetAddresses.contains(InetAddress.getByName(TEST_IP_ADDRESS_1)));
        assertTrue(testInetAddresses.contains(InetAddress.getByName(TEST_IP_ADDRESS_2)));
    }

    private void testPlmnResolutionMethod(boolean isEmergency) throws Exception {
        String expectedFqdnFromHplmn = "epdg.epc.mnc120.mcc311.pub.3gppnetwork.org";
        String expectedFqdnFromEHplmn = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";
        String expectedFqdnFromConfig = "epdg.epc.mnc480.mcc310.pub.3gppnetwork.org";

        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_PLMN});
        mTestBundle.putStringArray(
                CarrierConfigManager.Iwlan.KEY_MCC_MNCS_STRING_ARRAY,
                new String[] {"310-480", "300-120", "311-120"});

        mFakeDns.setAnswer(expectedFqdnFromHplmn, new String[] {TEST_IP_ADDRESS}, TYPE_A);
        mFakeDns.setAnswer(expectedFqdnFromEHplmn, new String[] {TEST_IP_ADDRESS_1}, TYPE_A);
        mFakeDns.setAnswer(expectedFqdnFromConfig, new String[] {TEST_IP_ADDRESS_2}, TYPE_A);
        mFakeDns.setAnswer(
                "sos." + expectedFqdnFromHplmn, new String[] {TEST_IP_ADDRESS_3}, TYPE_A);
        mFakeDns.setAnswer(
                "sos." + expectedFqdnFromEHplmn, new String[] {TEST_IP_ADDRESS_4}, TYPE_A);
        mFakeDns.setAnswer(
                "sos." + expectedFqdnFromConfig, new String[] {TEST_IP_ADDRESS_5}, TYPE_A);

        ArrayList<InetAddress> testInetAddresses =
                getValidatedServerListWithDefaultParams(isEmergency);

        if (isEmergency) {
            assertEquals(6, testInetAddresses.size());
            assertEquals(InetAddress.getByName(TEST_IP_ADDRESS_3), testInetAddresses.get(0));
            assertEquals(InetAddress.getByName(TEST_IP_ADDRESS), testInetAddresses.get(1));
            assertEquals(InetAddress.getByName(TEST_IP_ADDRESS_4), testInetAddresses.get(2));
            assertEquals(InetAddress.getByName(TEST_IP_ADDRESS_1), testInetAddresses.get(3));
            assertEquals(InetAddress.getByName(TEST_IP_ADDRESS_5), testInetAddresses.get(4));
            assertEquals(InetAddress.getByName(TEST_IP_ADDRESS_2), testInetAddresses.get(5));
        } else {
            assertEquals(3, testInetAddresses.size());
            assertEquals(InetAddress.getByName(TEST_IP_ADDRESS), testInetAddresses.get(0));
            assertEquals(InetAddress.getByName(TEST_IP_ADDRESS_1), testInetAddresses.get(1));
            assertEquals(InetAddress.getByName(TEST_IP_ADDRESS_2), testInetAddresses.get(2));
        }
    }

    @Test
    public void testCarrierConfigStaticAddressList() throws Exception {
        // Set Network.getAllByName mock
        final String addr1 = "epdg.epc.mnc480.mcc310.pub.3gppnetwork.org";
        final String addr2 = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";
        final String addr3 = "epdg.epc.mnc120.mcc311.pub.3gppnetwork.org";
        final String testStaticAddress = addr1 + "," + addr2 + "," + addr3;

        mFakeDns.setAnswer(addr1, new String[] {TEST_IP_ADDRESS_1}, TYPE_A);
        mFakeDns.setAnswer(addr2, new String[] {TEST_IP_ADDRESS_2}, TYPE_A);
        mFakeDns.setAnswer(addr3, new String[] {TEST_IP_ADDRESS}, TYPE_A);

        // Set carrier config mock
        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_STATIC});
        mTestBundle.putString(
                CarrierConfigManager.Iwlan.KEY_EPDG_STATIC_ADDRESS_STRING, testStaticAddress);

        ArrayList<InetAddress> testInetAddresses =
                getValidatedServerListWithDefaultParams(false /*isEmergency*/);

        assertEquals(testInetAddresses.size(), 3);
        assertEquals(testInetAddresses.get(0), InetAddress.getByName(TEST_IP_ADDRESS_1));
        assertEquals(testInetAddresses.get(1), InetAddress.getByName(TEST_IP_ADDRESS_2));
        assertEquals(testInetAddresses.get(2), InetAddress.getByName(TEST_IP_ADDRESS));
    }

    private ArrayList<InetAddress> getValidatedServerListWithDefaultParams(boolean isEmergency)
            throws Exception {
        ArrayList<InetAddress> testInetAddresses = new ArrayList<InetAddress>();
        final CountDownLatch latch = new CountDownLatch(1);
        IwlanError ret =
                mEpdgSelector.getValidatedServerList(
                        1234,
                        EpdgSelector.PROTO_FILTER_IPV4V6,
                        false /* isRoaming */,
                        isEmergency,
                        mMockNetwork,
                        new EpdgSelector.EpdgSelectorCallback() {
                            @Override
                            public void onServerListChanged(
                                    int transactionId, ArrayList<InetAddress> validIPList) {
                                assertEquals(transactionId, 1234);

                                for (InetAddress mInetAddress : validIPList) {
                                    testInetAddresses.add(mInetAddress);
                                }
                                Log.d(TAG, "onServerListChanged received");
                                latch.countDown();
                            }

                            @Override
                            public void onError(int transactionId, IwlanError epdgSelectorError) {
                                Log.d(TAG, "onError received");
                                latch.countDown();
                            }
                        });

        assertEquals(ret.getErrorType(), IwlanError.NO_ERROR);
        latch.await(1, TimeUnit.SECONDS);
        return testInetAddresses;
    }

    @Test
    public void testSetPcoData() throws Exception {
        addTestPcoIdsToTestConfigBundle();

        boolean retIPv6 = mEpdgSelector.setPcoData(testPcoIdIPv6, pcoData);
        boolean retIPv4 = mEpdgSelector.setPcoData(testPcoIdIPv4, pcoData);
        boolean retIncorrect = mEpdgSelector.setPcoData(0xFF00, pcoData);

        assertTrue(retIPv6);
        assertTrue(retIPv4);
        assertFalse(retIncorrect);
    }

    @Test
    public void testPcoResolutionMethod() throws Exception {
        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_PCO});
        addTestPcoIdsToTestConfigBundle();

        mEpdgSelector.clearPcoData();
        boolean retIPv6 =
                mEpdgSelector.setPcoData(
                        testPcoIdIPv6, InetAddress.getByName(TEST_IPV6_ADDRESS).getAddress());
        boolean retIPv4 =
                mEpdgSelector.setPcoData(
                        testPcoIdIPv4, InetAddress.getByName(TEST_IP_ADDRESS).getAddress());

        ArrayList<InetAddress> testInetAddresses =
                getValidatedServerListWithDefaultParams(false /* isEmergency */);

        assertEquals(testInetAddresses.size(), 2);
        assertTrue(testInetAddresses.contains(InetAddress.getByName(TEST_IP_ADDRESS)));
        assertTrue(testInetAddresses.contains(InetAddress.getByName(TEST_IPV6_ADDRESS)));
    }

    private void addTestPcoIdsToTestConfigBundle() {
        mTestBundle.putInt(CarrierConfigManager.Iwlan.KEY_EPDG_PCO_ID_IPV6_INT, testPcoIdIPv6);
        mTestBundle.putInt(CarrierConfigManager.Iwlan.KEY_EPDG_PCO_ID_IPV4_INT, testPcoIdIPv4);
    }

    @Test
    public void testCellularResolutionMethod() throws Exception {
        testCellularResolutionMethod(false);
    }

    @Test
    public void testCellularResolutionMethodForEmergency() throws Exception {
        testCellularResolutionMethod(true);
    }

    private void testCellularResolutionMethod(boolean isEmergency) throws Exception {
        int testMcc = 311;
        int testMnc = 120;
        String testMccString = "311";
        String testMncString = "120";
        int testLac = 65484;
        int testTac = 65484;
        int testNrTac = 16764074;

        List<CellInfo> fakeCellInfoArray = new ArrayList<CellInfo>();

        mTestBundle.putIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_CELLULAR_LOC});

        // Set cell info mock
        fakeCellInfoArray.add(mMockCellInfoGsm);
        when(mMockCellInfoGsm.isRegistered()).thenReturn(true);
        when(mMockCellInfoGsm.getCellIdentity()).thenReturn(mMockCellIdentityGsm);
        when(mMockCellIdentityGsm.getMcc()).thenReturn(testMcc);
        when(mMockCellIdentityGsm.getMnc()).thenReturn(testMnc);
        when(mMockCellIdentityGsm.getLac()).thenReturn(testLac);

        fakeCellInfoArray.add(mMockCellInfoWcdma);
        when(mMockCellInfoWcdma.isRegistered()).thenReturn(true);
        when(mMockCellInfoWcdma.getCellIdentity()).thenReturn(mMockCellIdentityWcdma);
        when(mMockCellIdentityWcdma.getMcc()).thenReturn(testMcc);
        when(mMockCellIdentityWcdma.getMnc()).thenReturn(testMnc);
        when(mMockCellIdentityWcdma.getLac()).thenReturn(testLac);

        fakeCellInfoArray.add(mMockCellInfoLte);
        when(mMockCellInfoLte.isRegistered()).thenReturn(true);
        when(mMockCellInfoLte.getCellIdentity()).thenReturn(mMockCellIdentityLte);
        when(mMockCellIdentityLte.getMcc()).thenReturn(testMcc);
        when(mMockCellIdentityLte.getMnc()).thenReturn(testMnc);
        when(mMockCellIdentityLte.getTac()).thenReturn(testTac);

        fakeCellInfoArray.add(mMockCellInfoNr);
        when(mMockCellInfoNr.isRegistered()).thenReturn(true);
        when(mMockCellInfoNr.getCellIdentity()).thenReturn(mMockCellIdentityNr);
        when(mMockCellIdentityNr.getMccString()).thenReturn(testMccString);
        when(mMockCellIdentityNr.getMncString()).thenReturn(testMncString);
        when(mMockCellIdentityNr.getTac()).thenReturn(testNrTac);

        when(mMockTelephonyManager.getAllCellInfo()).thenReturn(fakeCellInfoArray);

        setAnswerForCellularMethod(isEmergency, 311, 120);
        setAnswerForCellularMethod(isEmergency, 300, 120);

        ArrayList<InetAddress> testInetAddresses =
                getValidatedServerListWithDefaultParams(isEmergency);

        assertEquals(testInetAddresses.size(), 3);
        assertEquals(testInetAddresses.get(0), InetAddress.getByName(TEST_IP_ADDRESS));
        assertEquals(testInetAddresses.get(1), InetAddress.getByName(TEST_IP_ADDRESS_1));
        assertEquals(testInetAddresses.get(2), InetAddress.getByName(TEST_IP_ADDRESS_2));
    }

    private void setAnswerForCellularMethod(boolean isEmergency, int mcc, int mnc)
            throws Exception {
        String expectedFqdn1 =
                (isEmergency)
                        ? "lacffcc.sos.epdg.epc.mnc" + mnc + ".mcc" + mcc + ".pub.3gppnetwork.org"
                        : "lacffcc.epdg.epc.mnc" + mnc + ".mcc" + mcc + ".pub.3gppnetwork.org";
        String expectedFqdn2 =
                (isEmergency)
                        ? "tac-lbcc.tac-hbff.tac.sos.epdg.epc.mnc"
                                + mnc
                                + ".mcc"
                                + mcc
                                + ".pub.3gppnetwork.org"
                        : "tac-lbcc.tac-hbff.tac.epdg.epc.mnc"
                                + mnc
                                + ".mcc"
                                + mcc
                                + ".pub.3gppnetwork.org";
        String expectedFqdn3 =
                (isEmergency)
                        ? "tac-lbaa.tac-mbcc.tac-hbff.5gstac.sos.epdg.epc.mnc"
                                + mnc
                                + ".mcc"
                                + mcc
                                + ".pub.3gppnetwork.org"
                        : "tac-lbaa.tac-mbcc.tac-hbff.5gstac.epdg.epc.mnc"
                                + mnc
                                + ".mcc"
                                + mcc
                                + ".pub.3gppnetwork.org";

        mFakeDns.setAnswer(expectedFqdn1, new String[] {TEST_IP_ADDRESS}, TYPE_A);
        mFakeDns.setAnswer(expectedFqdn2, new String[] {TEST_IP_ADDRESS_1}, TYPE_A);
        mFakeDns.setAnswer(expectedFqdn3, new String[] {TEST_IP_ADDRESS_2}, TYPE_A);
    }

    /**
     * Fakes DNS responses.
     *
     * <p>Allows test methods to configure the IP addresses that will be resolved by
     * Network#getAllByName and by DnsResolver#query.
     */
    class FakeDns {
        /** Data class to record the Dns entry. */
        class DnsEntry {
            final String mHostname;
            final int mType;
            final List<InetAddress> mAddresses;

            DnsEntry(String host, int type, List<InetAddress> addr) {
                mHostname = host;
                mType = type;
                mAddresses = addr;
            }
            // Full match or partial match that target host contains the entry hostname to support
            // random private dns probe hostname.
            private boolean matches(String hostname, int type) {
                return hostname.equals(mHostname) && type == mType;
            }
        }

        private final ArrayList<DnsEntry> mAnswers = new ArrayList<DnsEntry>();

        /** Clears all DNS entries. */
        private synchronized void clearAll() {
            mAnswers.clear();
        }

        /** Returns the answer for a given name and type on the given mock network. */
        private synchronized List<InetAddress> getAnswer(Object mock, String hostname, int type) {
            return mAnswers.stream()
                    .filter(e -> e.matches(hostname, type))
                    .map(answer -> answer.mAddresses)
                    .findFirst()
                    .orElse(null);
        }

        /** Sets the answer for a given name and type. */
        private synchronized void setAnswer(String hostname, String[] answer, int type)
                throws UnknownHostException {
            DnsEntry record = new DnsEntry(hostname, type, generateAnswer(answer));
            // Remove the existing one.
            mAnswers.removeIf(entry -> entry.matches(hostname, type));
            // Add or replace a new record.
            mAnswers.add(record);
        }

        private List<InetAddress> generateAnswer(String[] answer) {
            if (answer == null) return new ArrayList<>();
            return Arrays.stream(answer)
                    .map(addr -> InetAddresses.parseNumericAddress(addr))
                    .collect(toList());
        }

        // Regardless of the type, depends on what the responses contained in the network.
        private List<InetAddress> queryAllTypes(Object mock, String hostname) {
            List<InetAddress> answer = new ArrayList<>();
            addAllIfNotNull(answer, getAnswer(mock, hostname, TYPE_A));
            addAllIfNotNull(answer, getAnswer(mock, hostname, TYPE_AAAA));
            return answer;
        }

        private void addAllIfNotNull(List<InetAddress> list, List<InetAddress> c) {
            if (c != null) {
                list.addAll(c);
            }
        }

        /** Starts mocking DNS queries. */
        private void startMocking() throws UnknownHostException {
            doAnswer(
                            invocation -> {
                                return mockQuery(
                                        invocation,
                                        1 /* posHostname */,
                                        3 /* posExecutor */,
                                        5 /* posCallback */,
                                        -1 /* posType */);
                            })
                    .when(mMockDnsResolver)
                    .query(any(), any(), anyInt(), any(), any(), any());
        }

        // Mocking queries on DnsResolver#query.
        private Answer mockQuery(
                InvocationOnMock invocation,
                int posHostname,
                int posExecutor,
                int posCallback,
                int posType) {
            String hostname = (String) invocation.getArgument(posHostname);
            Executor executor = (Executor) invocation.getArgument(posExecutor);
            DnsResolver.Callback<List<InetAddress>> callback = invocation.getArgument(posCallback);
            List<InetAddress> answer;

            answer = queryAllTypes(invocation.getMock(), hostname);

            if (answer != null && answer.size() > 0) {
                new Handler(Looper.getMainLooper())
                        .post(
                                () -> {
                                    executor.execute(() -> callback.onAnswer(answer, 0));
                                });
            }
            // If no answers, do nothing. sendDnsProbeWithTimeout will time out and throw UHE.
            return null;
        }
    }
}
