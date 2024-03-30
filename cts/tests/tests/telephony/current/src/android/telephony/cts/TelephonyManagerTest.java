/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.telephony.cts;

import static android.app.AppOpsManager.OPSTR_USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER;
import static android.telephony.PhoneCapability.DEVICE_NR_CAPABILITY_NSA;
import static android.telephony.PhoneCapability.DEVICE_NR_CAPABILITY_SA;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.Manifest.permission;
import android.annotation.NonNull;
import android.app.AppOpsManager;
import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Looper;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.SystemProperties;
import android.os.UserManager;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.AccessNetworkConstants;
import android.telephony.Annotation.RadioPowerState;
import android.telephony.AvailableNetworkInfo;
import android.telephony.CallAttributes;
import android.telephony.CallForwardingInfo;
import android.telephony.CallQuality;
import android.telephony.CarrierConfigManager;
import android.telephony.CellIdentity;
import android.telephony.CellIdentityCdma;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellIdentityTdscdma;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellLocation;
import android.telephony.DataThrottlingRequest;
import android.telephony.ImsiEncryptionInfo;
import android.telephony.ModemActivityInfo;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.PhoneCapability;
import android.telephony.PhoneStateListener;
import android.telephony.PinResult;
import android.telephony.PreciseCallState;
import android.telephony.RadioAccessFamily;
import android.telephony.RadioAccessSpecifier;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SignalStrengthUpdateRequest;
import android.telephony.SignalThresholdInfo;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.ThermalMitigationRequest;
import android.telephony.UiccCardInfo;
import android.telephony.UiccPortInfo;
import android.telephony.UiccSlotInfo;
import android.telephony.UiccSlotMapping;
import android.telephony.data.ApnSetting;
import android.telephony.data.NetworkSlicingConfig;
import android.telephony.emergency.EmergencyNumber;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CarrierPrivilegeUtils;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.TestThread;
import com.android.internal.telephony.uicc.IccUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Build, install and run the tests by running the commands below:
 *  make cts -j64
 *  cts-tradefed run cts -m CtsTelephonyTestCases --test android.telephony.cts.TelephonyManagerTest
 */
public class TelephonyManagerTest {
    private TelephonyManager mTelephonyManager;
    private SubscriptionManager mSubscriptionManager;
    private PackageManager mPackageManager;
    private boolean mOnCellLocationChangedCalled = false;
    private boolean mOnCellInfoChanged = false;
    private boolean mOnSignalStrengthsChanged = false;
    private boolean mServiceStateChangedCalled = false;
    private boolean mRadioRebootTriggered = false;
    private boolean mHasRadioPowerOff = false;
    private ServiceState mServiceState;
    private PhoneCapability mPhoneCapability;
    private boolean mOnPhoneCapabilityChanged = false;
    private final Object mLock = new Object();

    private CarrierConfigManager mCarrierConfigManager;
    private String mSelfPackageName;
    private String mSelfCertHash;

    private static final int TOLERANCE = 1000;
    private static final int TIMEOUT_FOR_NETWORK_OPS = TOLERANCE * 180;
    private PhoneStateListener mListener;
    private static ConnectivityManager mCm;
    private static final String TAG = "TelephonyManagerTest";
    private static final List<Integer> ROAMING_TYPES = Arrays.asList(
            ServiceState.ROAMING_TYPE_DOMESTIC,
            ServiceState.ROAMING_TYPE_INTERNATIONAL,
            ServiceState.ROAMING_TYPE_NOT_ROAMING,
            ServiceState.ROAMING_TYPE_UNKNOWN);
    private static final List<Integer> NETWORK_TYPES = Arrays.asList(
            TelephonyManager.NETWORK_TYPE_UNKNOWN,
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_IDEN,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_LTE,
            TelephonyManager.NETWORK_TYPE_EHRPD,
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_GSM,
            TelephonyManager.NETWORK_TYPE_TD_SCDMA,
            TelephonyManager.NETWORK_TYPE_IWLAN,
            TelephonyManager.NETWORK_TYPE_LTE_CA,
            TelephonyManager.NETWORK_TYPE_NR);

    private static final int EMERGENCY_NUMBER_SOURCE_RIL_ECCLIST = 0;
    private static final Set<Integer> EMERGENCY_NUMBER_SOURCE_SET;

    private static final String PLMN_A = "123456";
    private static final String PLMN_B = "78901";
    private static final List<String> FPLMN_TEST = Arrays.asList(PLMN_A, PLMN_B);
    private static final int MAX_FPLMN_NUM = 1000;
    private static final int MIN_FPLMN_NUM = 3;

    private static final String THERMAL_MITIGATION_COMMAND_BASE = "cmd phone thermal-mitigation ";
    private static final String ALLOW_PACKAGE_SUBCOMMAND = "allow-package ";
    private static final String DISALLOW_PACKAGE_SUBCOMMAND = "disallow-package ";
    private static final String TELEPHONY_CTS_PACKAGE = "android.telephony.cts";

    private static final String TEST_FORWARD_NUMBER = "54321";
    private static final String TESTING_PLMN = "12345";

    private static final String BAD_IMSI_CERT_URL = "https:badurl.badurl:8080";
    private static final String IMSI_CERT_STRING_EPDG = "-----BEGIN CERTIFICATE-----"
            + "\nMIIDkzCCAnugAwIBAgIEJ4MVZDANBgkqhkiG9w0BAQsFADB6MQswCQYDVQQGEwJV"
            + "\nUzEOMAwGA1UECBMFVGV4YXMxDzANBgNVBAcTBklydmluZzEiMCAGA1UEChMZVmVy"
            + "\naXpvbiBEYXRhIFNlcnZpY2VzIExMQzEMMAoGA1UECxMDTk5PMRgwFgYDVQQDEw9F"
            + "\nQVAtSURFLlZaVy5DT00wHhcNMTcxMTEzMTkxMTA1WhcNMjcxMTExMTkxMTA1WjB6"
            + "\nMQswCQYDVQQGEwJVUzEOMAwGA1UECBMFVGV4YXMxDzANBgNVBAcTBklydmluZzEi"
            + "\nMCAGA1UEChMZVmVyaXpvbiBEYXRhIFNlcnZpY2VzIExMQzEMMAoGA1UECxMDTk5P"
            + "\nMRgwFgYDVQQDEw9FQVAtSURFLlZaVy5DT00wggEiMA0GCSqGSIb3DQEBAQUAA4IB"
            + "\nDwAwggEKAoIBAQCrQ28TvN0uUV/vK4YUS7+zcYMKAe5IYtDa3Wa0r64iyBSz6Eau"
            + "\nT+YHNNzCV4xMqURM5mIY6796LnmWR5jViUgrHyw0d06mLE54uUET/drn2pwhaobK"
            + "\nNVvbYzpm5W3dvext+klEgIhpRW4fR/uNUmD0O9n/5ofpg++wbvMNWEIjeTVUGPRT"
            + "\nCeVblH3tK8bKdCKjp48HtuciY7gE8LMoHhMHA1cob9VktSYTy2ABa+rKAPAaqVz4"
            + "\nL0Arlbi9INHSDNFlLvy1xE5dyYIqhRMicM2i4LCMwJnwf0tz8m7DmDxfdmC4HY2Q"
            + "\nz4VpbQOu10oRhXXrhZFkZEmqp6RYQmDRDDDtAgMBAAGjITAfMB0GA1UdDgQWBBSg"
            + "\nFA6liox07smzfITrvjSlgWkMMTANBgkqhkiG9w0BAQsFAAOCAQEAIoFKLgLfS9f1"
            + "\n0UG85rb+noaeXY0YofSY0dxFIW3rA5zjRD0kus9iyw9CfADDD305hefJ4Kq/NLAF"
            + "\n0odR4MOTan5KhXTlD9/8mZjSSeEktgCX3BbmMqKoKcaV6Oo9C0RfwGccDms6D+Dw"
            + "\n3GkgsvKJEB8LjApzQSmDwCV9BVJsC60041cndqBxMr3RMxCkO6/sQRKyAuzx5f91"
            + "\nWn5cpYxvl4//TatSc9oeU+ootlxfXszdRPM5xqCodm6gWmxRkK6DePlhpaZ1sKdw"
            + "\nCQg/mA35Eh5ZgOpZT2YG+a8BbDRCF5gj/pu1tPt8VfApPHq6lAoitlrx1cEdJWx6"
            + "\n5JXaFrs0UA=="
            + "\n-----END CERTIFICATE-----";
    private static final String IMSI_CERT_STRING_WLAN = "-----BEGIN CERTIFICATE-----"
            + "\nMIIFbzCCBFegAwIBAgIUAz8I/cK3fILeJ9PSbi7MkN8yZBkwDQYJKoZIhvcNAQEL"
            + "\nBQAwgY0xCzAJBgNVBAYTAk5MMRIwEAYDVQQHEwlBbXN0ZXJkYW0xJTAjBgNVBAoT"
            + "\nHFZlcml6b24gRW50ZXJwcmlzZSBTb2x1dGlvbnMxEzARBgNVBAsTCkN5YmVydHJ1"
            + "\nc3QxLjAsBgNVBAMTJVZlcml6b24gUHVibGljIFN1cmVTZXJ2ZXIgQ0EgRzE0LVNI"
            + "\nQTIwHhcNMTcxMTE2MTU1NjMzWhcNMTkxMTE2MTU1NjMzWjB6MQswCQYDVQQGEwJV"
            + "\nUzEOMAwGA1UECBMFVGV4YXMxDzANBgNVBAcTBklydmluZzEiMCAGA1UEChMZVmVy"
            + "\naXpvbiBEYXRhIFNlcnZpY2VzIExMQzEMMAoGA1UECxMDTk5PMRgwFgYDVQQDEw9F"
            + "\nQVAtSURFLlZaVy5DT00wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCr"
            + "\nQ28TvN0uUV/vK4YUS7+zcYMKAe5IYtDa3Wa0r64iyBSz6EauT+YHNNzCV4xMqURM"
            + "\n5mIY6796LnmWR5jViUgrHyw0d06mLE54uUET/drn2pwhaobKNVvbYzpm5W3dvext"
            + "\n+klEgIhpRW4fR/uNUmD0O9n/5ofpg++wbvMNWEIjeTVUGPRTCeVblH3tK8bKdCKj"
            + "\np48HtuciY7gE8LMoHhMHA1cob9VktSYTy2ABa+rKAPAaqVz4L0Arlbi9INHSDNFl"
            + "\nLvy1xE5dyYIqhRMicM2i4LCMwJnwf0tz8m7DmDxfdmC4HY2Qz4VpbQOu10oRhXXr"
            + "\nhZFkZEmqp6RYQmDRDDDtAgMBAAGjggHXMIIB0zAMBgNVHRMBAf8EAjAAMEwGA1Ud"
            + "\nIARFMEMwQQYJKwYBBAGxPgEyMDQwMgYIKwYBBQUHAgEWJmh0dHBzOi8vc2VjdXJl"
            + "\nLm9tbmlyb290LmNvbS9yZXBvc2l0b3J5MIGpBggrBgEFBQcBAQSBnDCBmTAtBggr"
            + "\nBgEFBQcwAYYhaHR0cDovL3Zwc3NnMTQyLm9jc3Aub21uaXJvb3QuY29tMDMGCCsG"
            + "\nAQUFBzAChidodHRwOi8vY2FjZXJ0Lm9tbmlyb290LmNvbS92cHNzZzE0Mi5jcnQw"
            + "\nMwYIKwYBBQUHMAKGJ2h0dHA6Ly9jYWNlcnQub21uaXJvb3QuY29tL3Zwc3NnMTQy"
            + "\nLmRlcjAaBgNVHREEEzARgg9FQVAtSURFLlZaVy5DT00wDgYDVR0PAQH/BAQDAgWg"
            + "\nMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEFBQcDAjAfBgNVHSMEGDAWgBTkLbuR"
            + "\nAWUmH7R6P6MVJaTOjEQzOzA+BgNVHR8ENzA1MDOgMaAvhi1odHRwOi8vdnBzc2cx"
            + "\nNDIuY3JsLm9tbmlyb290LmNvbS92cHNzZzE0Mi5jcmwwHQYDVR0OBBYEFKAUDqWK"
            + "\njHTuybN8hOu+NKWBaQwxMA0GCSqGSIb3DQEBCwUAA4IBAQAbSrvVrdxRPLnVu6vc"
            + "\n4BiFT2gWDhZ63EyV4f877sC1iMJRFlfwWQQfHVyhGTFa8JnhbEhhTxCP+L00Q8rX"
            + "\nKbOw9ei5g2yp7OjStwhHz5T20UejjKkl7hKtMduZXxFToqhVwIpqG58Tzl/35FX4"
            + "\nu+YDPgwTX5gbpbJxpbncn9voxWGWu3AbHVvzaskfBgZfWAuJnbgq0WTEt7bGOfiI"
            + "\nelIIQe7XL6beFcdAM9C7DlgOLqpR/31LncrMC46cPA5HmfV4mnpeK/9uq0mMbUJK"
            + "\nx2vNRWONSm2UGwdb00tLsTloxeqCOMpbkBiqi/RhOlIKIOWMPojukA5+xryh2FVs"
            + "\n7bdw"
            + "\n-----END CERTIFICATE-----";

    private static final int RADIO_HAL_VERSION_1_3 = makeRadioVersion(1, 3);
    private static final int RADIO_HAL_VERSION_1_5 = makeRadioVersion(1, 5);
    private static final int RADIO_HAL_VERSION_1_6 = makeRadioVersion(1, 6);
    private static final int RADIO_HAL_VERSION_2_0 = makeRadioVersion(2, 0);

    static {
        EMERGENCY_NUMBER_SOURCE_SET = new HashSet<Integer>();
        EMERGENCY_NUMBER_SOURCE_SET.add(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING);
        EMERGENCY_NUMBER_SOURCE_SET.add(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_SIM);
        EMERGENCY_NUMBER_SOURCE_SET.add(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE);
        EMERGENCY_NUMBER_SOURCE_SET.add(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_MODEM_CONFIG);
        EMERGENCY_NUMBER_SOURCE_SET.add(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DEFAULT);
    }

    private static final Set<Integer> EMERGENCY_SERVICE_CATEGORY_SET;
    static {
        EMERGENCY_SERVICE_CATEGORY_SET = new HashSet<Integer>();
        EMERGENCY_SERVICE_CATEGORY_SET.add(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE);
        EMERGENCY_SERVICE_CATEGORY_SET.add(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AMBULANCE);
        EMERGENCY_SERVICE_CATEGORY_SET.add(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE);
        EMERGENCY_SERVICE_CATEGORY_SET.add(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MARINE_GUARD);
        EMERGENCY_SERVICE_CATEGORY_SET.add(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MOUNTAIN_RESCUE);
        EMERGENCY_SERVICE_CATEGORY_SET.add(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MIEC);
        EMERGENCY_SERVICE_CATEGORY_SET.add(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AIEC);
    }

    private static final Map<Class<? extends CellIdentity>, List<Integer>> sNetworkTypes;
    static {
        sNetworkTypes = new ArrayMap<>();
        sNetworkTypes.put(CellIdentityGsm.class,
                Arrays.asList(new Integer[]{
                    TelephonyManager.NETWORK_TYPE_GSM,
                    TelephonyManager.NETWORK_TYPE_GPRS,
                    TelephonyManager.NETWORK_TYPE_EDGE}));
        sNetworkTypes.put(CellIdentityWcdma.class,
                Arrays.asList(TelephonyManager.NETWORK_TYPE_UMTS,
                        TelephonyManager.NETWORK_TYPE_HSDPA,
                        TelephonyManager.NETWORK_TYPE_HSUPA,
                        TelephonyManager.NETWORK_TYPE_HSPA,
                        TelephonyManager.NETWORK_TYPE_HSPAP));
        sNetworkTypes.put(CellIdentityCdma.class,
                Arrays.asList(TelephonyManager.NETWORK_TYPE_CDMA,
                        TelephonyManager.NETWORK_TYPE_1xRTT,
                        TelephonyManager.NETWORK_TYPE_EVDO_0,
                        TelephonyManager.NETWORK_TYPE_EVDO_A,
                        TelephonyManager.NETWORK_TYPE_EVDO_B,
                        TelephonyManager.NETWORK_TYPE_EHRPD));
        sNetworkTypes.put(CellIdentityLte.class,
                Arrays.asList(TelephonyManager.NETWORK_TYPE_LTE));
        sNetworkTypes.put(CellIdentityNr.class,
                Arrays.asList(TelephonyManager.NETWORK_TYPE_NR));
        sNetworkTypes.put(CellIdentityTdscdma.class,
                Arrays.asList(TelephonyManager.NETWORK_TYPE_TD_SCDMA));
    }

    private int mTestSub;
    private int mRadioVersion;
    private boolean mIsAllowedNetworkTypeChanged;
    private Map<Integer, Long> mAllowedNetworkTypesList = new HashMap<>();

    private class CarrierPrivilegeChangeMonitor implements AutoCloseable {
        // CarrierPrivilegesCallback will be triggered upon registration. Filter the first callback
        // here since we really care of the *change* of carrier privileges instead of the content
        private boolean mHasSentPrivilegeChangeCallback = false;
        private CountDownLatch mLatch = new CountDownLatch(1);
        private final TelephonyManager.CarrierPrivilegesCallback mCarrierPrivilegesCallback;

        CarrierPrivilegeChangeMonitor() {
            mCarrierPrivilegesCallback = (privilegedPackageNames, privilegedUids) -> {
                // Ignore the first callback which is triggered upon registration
                if (!mHasSentPrivilegeChangeCallback) {
                    mHasSentPrivilegeChangeCallback = true;
                    return;
                }
                mLatch.countDown();
            };

            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.registerCarrierPrivilegesCallback(
                            SubscriptionManager.getSlotIndex(mTestSub),
                            getContext().getMainExecutor(),
                            mCarrierPrivilegesCallback));
        }

        public void waitForCarrierPrivilegeChanged() throws Exception {
            if (!mLatch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Failed to update carrier privileges");
            }
        }

        @Override
        public void close() throws Exception {
            if(mTelephonyManager != null) {
                ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                        (tm) -> tm.unregisterCarrierPrivilegesCallback(
                                mCarrierPrivilegesCallback));
            }
        }
    }

    @Before
    public void setUp() throws Exception {
        mCm = getContext().getSystemService(ConnectivityManager.class);
        mPackageManager = getContext().getPackageManager();
        assumeTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY));

        mSubscriptionManager = getContext().getSystemService(SubscriptionManager.class);
        mCarrierConfigManager = getContext().getSystemService(CarrierConfigManager.class);
        mSelfPackageName = getContext().getPackageName();
        mSelfCertHash = getCertHash(mSelfPackageName);
        mTestSub = SubscriptionManager.getDefaultSubscriptionId();
        mTelephonyManager = getContext().getSystemService(TelephonyManager.class)
                .createForSubscriptionId(mTestSub);
        Pair<Integer, Integer> radioVersion = mTelephonyManager.getRadioHalVersion();
        mRadioVersion = makeRadioVersion(radioVersion.first, radioVersion.second);
        IntentFilter filter = new IntentFilter(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.READ_PHONE_STATE");
        saveAllowedNetworkTypesForAllReasons();
    }

    @After
    public void tearDown() throws Exception {
        if (mListener != null) {
            // unregister the listener
            mTelephonyManager.listen(mListener, PhoneStateListener.LISTEN_NONE);
        }
        if (mIsAllowedNetworkTypeChanged) {
            recoverAllowedNetworkType();
        }

        StringBuilder cmdBuilder = new StringBuilder();
        cmdBuilder.append(THERMAL_MITIGATION_COMMAND_BASE).append(DISALLOW_PACKAGE_SUBCOMMAND)
                .append(TELEPHONY_CTS_PACKAGE);
        TelephonyUtils.executeShellCommand(InstrumentationRegistry.getInstrumentation(),
                cmdBuilder.toString());
    }

    private void saveAllowedNetworkTypesForAllReasons() {
        mIsAllowedNetworkTypeChanged = false;
        if (mAllowedNetworkTypesList == null) {
            mAllowedNetworkTypesList = new HashMap<>();
        }
        long allowedNetworkTypesUser = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.getAllowedNetworkTypesForReason(
                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER)
        );
        long allowedNetworkTypesPower = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.getAllowedNetworkTypesForReason(
                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_POWER)
        );
        long allowedNetworkTypesCarrier = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.getAllowedNetworkTypesForReason(
                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_CARRIER)
        );
        long allowedNetworkTypesEnable2g = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.getAllowedNetworkTypesForReason(
                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_ENABLE_2G)
        );
        mAllowedNetworkTypesList.put(TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER,
                allowedNetworkTypesUser);
        mAllowedNetworkTypesList.put(TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_POWER,
                allowedNetworkTypesPower);
        mAllowedNetworkTypesList.put(TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_CARRIER,
                allowedNetworkTypesCarrier);
        mAllowedNetworkTypesList.put(TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_ENABLE_2G,
                allowedNetworkTypesEnable2g);
    }

    private void recoverAllowedNetworkType() {
        if (mAllowedNetworkTypesList == null) {
            return;
        }
        for (Integer key : mAllowedNetworkTypesList.keySet()) {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager,
                    (tm) -> tm.setAllowedNetworkTypesForReason(
                            key,
                            mAllowedNetworkTypesList.get(key)));
        }
    }

    private String getCertHash(String pkgName) throws Exception {
        try {
            PackageInfo pInfo = mPackageManager.getPackageInfo(pkgName,
                    PackageManager.GET_SIGNATURES
                            | PackageManager.GET_DISABLED_UNTIL_USED_COMPONENTS);
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            return IccUtils.bytesToHexString(md.digest(pInfo.signatures[0].toByteArray()));
        } catch (PackageManager.NameNotFoundException ex) {
            Log.e(TAG, pkgName + " not found", ex);
            throw ex;
        } catch (NoSuchAlgorithmException ex) {
            Log.e(TAG, "Algorithm SHA1 is not found.");
            throw ex;
        }
    }

    /** Checks whether the telephony feature is supported. */
    private boolean hasFeature(String feature) {
        return mPackageManager.hasSystemFeature(feature);
    }

    @Test
    public void testHasCarrierPrivilegesViaCarrierConfigs() throws Exception {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));
        PersistableBundle carrierConfig = mCarrierConfigManager.getConfigForSubId(mTestSub);

        try {
            assertNotNull("CarrierConfigManager#getConfigForSubId() returned null",
                    carrierConfig);
            assertFalse("CarrierConfigManager#getConfigForSubId() returned empty bundle",
                    carrierConfig.isEmpty());

            // purge the certs in carrierConfigs first
            carrierConfig.putStringArray(
                    CarrierConfigManager.KEY_CARRIER_CERTIFICATE_STRING_ARRAY, new String[]{});
            changeCarrierPrivileges(false, carrierConfig);
            // verify we don't have privilege through carrierConfigs or Uicc
            assertFalse(mTelephonyManager.hasCarrierPrivileges());

            carrierConfig.putStringArray(
                    CarrierConfigManager.KEY_CARRIER_CERTIFICATE_STRING_ARRAY,
                    new String[]{mSelfCertHash});

            // verify we now have privilege after adding certificate to carrierConfigs
            changeCarrierPrivileges(true, carrierConfig);
            assertTrue(mTelephonyManager.hasCarrierPrivileges());
        } finally {
            // purge the newly added certificate
            carrierConfig.putStringArray(
                    CarrierConfigManager.KEY_CARRIER_CERTIFICATE_STRING_ARRAY, new String[]{});
            changeCarrierPrivileges(false, carrierConfig);
            // verify we no longer have privilege after removing certificate
            assertFalse(mTelephonyManager.hasCarrierPrivileges());
        }
    }

    private void changeCarrierPrivileges(boolean gain, PersistableBundle carrierConfig)
            throws Exception {
        if (mTelephonyManager.hasCarrierPrivileges() == gain) {
            Log.w(TAG, "Carrier privileges already " + (gain ? "granted" : "revoked"));
            return;
        }

        try(CarrierPrivilegeChangeMonitor monitor = new CarrierPrivilegeChangeMonitor()) {
            overrideCarrierConfig(carrierConfig);
            monitor.waitForCarrierPrivilegeChanged();
        }
    }

    private void overrideCarrierConfig(PersistableBundle bundle) throws Exception {
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mCarrierConfigManager,
                (cm) -> cm.overrideConfig(mTestSub, bundle));
    }

    public static void grantLocationPermissions() {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        String packageName = getContext().getPackageName();
        uiAutomation.grantRuntimePermission(packageName, permission.ACCESS_COARSE_LOCATION);
        uiAutomation.grantRuntimePermission(packageName, permission.ACCESS_FINE_LOCATION);
        uiAutomation.grantRuntimePermission(packageName, permission.ACCESS_BACKGROUND_LOCATION);
    }

    @Test
    public void testDevicePolicyApn() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_DATA));

        // These methods aren't accessible to anything except system and phone by design, so we just
        // look for security exceptions here.
        try {
            List<ApnSetting> apns = mTelephonyManager.getDevicePolicyOverrideApns(getContext());
            fail("SecurityException expected");
        } catch (SecurityException e) {
            // expected
        }

        try {
            ApnSetting.Builder builder = new ApnSetting.Builder();

            ApnSetting setting = builder
                    .setEntryName("asdf")
                    .setApnName("asdf")
                    .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT)
                    .build();
            int id = mTelephonyManager.addDevicePolicyOverrideApn(getContext(), setting);
            fail("SecurityException expected");
        } catch (SecurityException e) {
            // expected
        }

        try {
            ApnSetting.Builder builder = new ApnSetting.Builder();

            ApnSetting setting = builder
                    .setEntryName("asdf")
                    .setApnName("asdf")
                    .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT)
                    .build();
            boolean success = mTelephonyManager.modifyDevicePolicyOverrideApn(
                    getContext(), 0, setting);
            fail("SecurityException expected");
        } catch (SecurityException e) {
            // expected
        }
    }

    @Test
    public void testListen() throws Throwable {
        if (mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
            // TODO: temp workaround, need to adjust test to for CDMA
            return;
        }

        grantLocationPermissions();

        TestThread t = new TestThread(() -> {
            Looper.prepare();
            mListener = new PhoneStateListener() {
                @Override
                public void onCellLocationChanged(CellLocation location) {
                    if (!mOnCellLocationChangedCalled) {
                        synchronized (mLock) {
                            mOnCellLocationChangedCalled = true;
                            mLock.notify();
                        }
                    }
                }
            };

            synchronized (mLock) {
                mLock.notify(); // mListener is ready
            }

            Looper.loop();
        });

        synchronized (mLock) {
            t.start();
            mLock.wait(TOLERANCE); // wait for mListener
        }

        // Test register
        synchronized (mLock) {
            // .listen generates an onCellLocationChanged event
            mTelephonyManager.listen(mListener, PhoneStateListener.LISTEN_CELL_LOCATION);
            mLock.wait(TOLERANCE);

            assertTrue("Test register, mOnCellLocationChangedCalled should be true.",
                    mOnCellLocationChangedCalled);
        }

        synchronized (mLock) {
            mOnCellLocationChangedCalled = false;
            CellLocation.requestLocationUpdate();
            mLock.wait(TOLERANCE);

            // Starting with Android S, this API will silently drop all requests from apps
            // targeting Android S due to unfixable limitations with the API.
            assertFalse("Test register, mOnCellLocationChangedCalled should be false.",
                    mOnCellLocationChangedCalled);
        }

        // unregister the listener
        mTelephonyManager.listen(mListener, PhoneStateListener.LISTEN_NONE);
        Thread.sleep(TOLERANCE);

        // Test unregister
        synchronized (mLock) {
            mOnCellLocationChangedCalled = false;
            // unregister again, to make sure doing so does not call the listener
            mTelephonyManager.listen(mListener, PhoneStateListener.LISTEN_NONE);
            CellLocation.requestLocationUpdate();
            mLock.wait(TOLERANCE);

            assertFalse("Test unregister, mOnCellLocationChangedCalled should be false.",
                    mOnCellLocationChangedCalled);
        }
    }

    /**
     * The getter methods here are all related to the information about the telephony.
     * These getters are related to concrete location, phone, service provider company, so
     * it's no need to get details of these information, just make sure they are in right
     * condition(>0 or not null).
     */
    @Test
    public void testTelephonyManager() {
        assertTrue(mTelephonyManager.getNetworkType() >= TelephonyManager.NETWORK_TYPE_UNKNOWN);
        assertTrue(mTelephonyManager.getPhoneType() >= TelephonyManager.PHONE_TYPE_NONE);
        assertTrue(mTelephonyManager.getSimState() >= TelephonyManager.SIM_STATE_UNKNOWN);
        assertTrue(mTelephonyManager.getDataActivity() >= TelephonyManager.DATA_ACTIVITY_NONE);
        assertTrue(mTelephonyManager.getDataState() >= TelephonyManager.DATA_DISCONNECTED);
        assertTrue(mTelephonyManager.getCallState() >= TelephonyManager.CALL_STATE_IDLE);

        for (int i = 0; i < mTelephonyManager.getPhoneCount(); ++i) {
            assertTrue(mTelephonyManager.getSimState(i) >= TelephonyManager.SIM_STATE_UNKNOWN);
        }

        // Make sure devices without MMS service won't fail on this
        if (InstrumentationRegistry.getContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
                && (mTelephonyManager.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE)) {
            assertFalse(mTelephonyManager.getMmsUserAgent().isEmpty());
            assertFalse(mTelephonyManager.getMmsUAProfUrl().isEmpty());
        }

        // The following methods may return any value depending on the state of the device. Simply
        // call them to make sure they do not throw any exceptions.
        mTelephonyManager.getVoiceMailNumber();
        mTelephonyManager.getSimOperatorName();
        mTelephonyManager.getNetworkCountryIso();
        mTelephonyManager.getCellLocation();
        mTelephonyManager.getSimCarrierId();
        mTelephonyManager.getSimCarrierIdName();
        mTelephonyManager.getSimSpecificCarrierId();
        mTelephonyManager.getSimSpecificCarrierIdName();
        mTelephonyManager.getCarrierIdFromSimMccMnc();
        mTelephonyManager.isDataRoamingEnabled();
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getSimSerialNumber());
        mTelephonyManager.getSimOperator();
        mTelephonyManager.getSignalStrength();
        mTelephonyManager.getNetworkOperatorName();
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getSubscriberId());
        mTelephonyManager.getLine1Number();
        mTelephonyManager.getNetworkOperator();

        try {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(
                            "android.permission.READ_PRIVILEGED_PHONE_STATE");
            mTelephonyManager.getPhoneAccountHandle();
        } catch (SecurityException e) {
            fail("TelephonyManager#getPhoneAccountHandle requires READ_PRIVILEGED_PHONE_STATE");
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
        mTelephonyManager.getSimCountryIso();
        mTelephonyManager.getVoiceMailAlphaTag();
        mTelephonyManager.isNetworkRoaming();
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getDeviceId());
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getDeviceId(mTelephonyManager.getSlotIndex()));
        mTelephonyManager.getDeviceSoftwareVersion();
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getDeviceSoftwareVersion(mTelephonyManager.getSlotIndex()));
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getImei());
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getImei(mTelephonyManager.getSlotIndex()));
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.isManualNetworkSelectionAllowed());
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getManualNetworkSelectionPlmn());

        mTelephonyManager.getPhoneCount();
        mTelephonyManager.getDataEnabled();
        mTelephonyManager.getNetworkSpecifier();
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager, (tm) -> tm.getNai());
        TelecomManager telecomManager = getContext().getSystemService(TelecomManager.class);
        PhoneAccountHandle defaultAccount = telecomManager
                .getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL);
        mTelephonyManager.getVoicemailRingtoneUri(defaultAccount);
        mTelephonyManager.isVoicemailVibrationEnabled(defaultAccount);
        mTelephonyManager.getSubscriptionId(defaultAccount);
        mTelephonyManager.getCarrierConfig();
        mTelephonyManager.isVoiceCapable();
        mTelephonyManager.isSmsCapable();
        mTelephonyManager.isLteCdmaEvdoGsmWcdmaEnabled();
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.isDataConnectionAllowed());
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.isAnyRadioPoweredOn());
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                (tm) -> tm.resetIms(tm.getSlotIndex()));

        // Verify TelephonyManager.getCarrierPrivilegeStatus
        List<Integer> validCarrierPrivilegeStatus = new ArrayList<>();
        validCarrierPrivilegeStatus.add(TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS);
        validCarrierPrivilegeStatus.add(TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS);
        validCarrierPrivilegeStatus.add(
                TelephonyManager.CARRIER_PRIVILEGE_STATUS_RULES_NOT_LOADED);
        validCarrierPrivilegeStatus.add(
                TelephonyManager.CARRIER_PRIVILEGE_STATUS_ERROR_LOADING_RULES);
        int carrierPrivilegeStatusResult = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.getCarrierPrivilegeStatus(Process.myUid()));
        assertTrue(validCarrierPrivilegeStatus.contains(carrierPrivilegeStatusResult));

        // Verify TelephonyManager.getCarrierPrivilegedPackagesForAllActiveSubscriptions
        List<String> resultForGetCarrierPrivilegedApis =
                ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                        (tm) -> tm.getCarrierPrivilegedPackagesForAllActiveSubscriptions());
        assertNotNull(resultForGetCarrierPrivilegedApis);
        for (String result : resultForGetCarrierPrivilegedApis) {
            assertFalse(TextUtils.isEmpty(result));
        }

        mTelephonyManager.getDefaultRespondViaMessageApplication();
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                TelephonyManager::getAndUpdateDefaultRespondViaMessageApplication);

        // Verify getImei/getSubscriberId/getIccAuthentication:
        // With app ops permision USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER, should not throw
        // SecurityException.
        try {
            setAppOpsPermissionAllowed(true, OPSTR_USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER);

            mTelephonyManager.getImei();
            mTelephonyManager.getSubscriberId();
            mTelephonyManager.getIccAuthentication(
                    TelephonyManager.APPTYPE_USIM, TelephonyManager.AUTHTYPE_EAP_AKA, "");
        } finally {
            setAppOpsPermissionAllowed(false, OPSTR_USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER);
        }
    }

    @Test
    public void testGetCallForwarding() throws Exception {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CALLING));

        List<Integer> callForwardingReasons = new ArrayList<>();
        callForwardingReasons.add(CallForwardingInfo.REASON_UNCONDITIONAL);
        callForwardingReasons.add(CallForwardingInfo.REASON_BUSY);
        callForwardingReasons.add(CallForwardingInfo.REASON_NO_REPLY);
        callForwardingReasons.add(CallForwardingInfo.REASON_NOT_REACHABLE);
        callForwardingReasons.add(CallForwardingInfo.REASON_ALL);
        callForwardingReasons.add(CallForwardingInfo.REASON_ALL_CONDITIONAL);

        Set<Integer> callForwardingErrors = new HashSet<Integer>();
        callForwardingErrors.add(TelephonyManager.CallForwardingInfoCallback
                .RESULT_ERROR_FDN_CHECK_FAILURE);
        callForwardingErrors.add(TelephonyManager.CallForwardingInfoCallback.RESULT_ERROR_UNKNOWN);
        callForwardingErrors.add(TelephonyManager.CallForwardingInfoCallback
                .RESULT_ERROR_NOT_SUPPORTED);

        for (int callForwardingReasonToGet : callForwardingReasons) {
            Log.d(TAG, "[testGetCallForwarding] callForwardingReasonToGet: "
                    + callForwardingReasonToGet);
            AtomicReference<CallForwardingInfo> receivedForwardingInfo = new AtomicReference<>();
            AtomicReference<Integer> receivedErrorCode = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            TelephonyManager.CallForwardingInfoCallback callback =
                    new TelephonyManager.CallForwardingInfoCallback() {
                        @Override
                        public void onCallForwardingInfoAvailable(CallForwardingInfo info) {
                            receivedForwardingInfo.set(info);
                            latch.countDown();
                        }

                        @Override
                        public void onError(int error) {
                            receivedErrorCode.set(error);
                            latch.countDown();
                        }
            };
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.getCallForwarding(callForwardingReasonToGet,
                            getContext().getMainExecutor(), callback));

            assertTrue(latch.await(TIMEOUT_FOR_NETWORK_OPS, TimeUnit.MILLISECONDS));
            // Make sure only one of the callbacks gets invoked
            assertTrue((receivedForwardingInfo.get() != null) ^ (receivedErrorCode.get() != null));
            if (receivedForwardingInfo.get() != null) {
                CallForwardingInfo info = receivedForwardingInfo.get();
                assertTrue("Got reason not in expected set:" + info.getReason(),
                        callForwardingReasons.contains(info.getReason()));
                if (info.isEnabled()) {
                    assertNotNull(info.getNumber());
                    assertTrue("Got negative timeoutSeconds=" + info.getTimeoutSeconds(),
                            info.getTimeoutSeconds() >= 0);
                }
            }

            if (receivedErrorCode.get() != null) {
                assertTrue("Got code not in expected set:" + receivedErrorCode.get(),
                        callForwardingErrors.contains(receivedErrorCode.get()));
            }
        }
    }

    @Test
    public void testSetCallForwarding() throws Exception {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CALLING));

        List<Integer> callForwardingReasons = new ArrayList<>();
        callForwardingReasons.add(CallForwardingInfo.REASON_UNCONDITIONAL);
        callForwardingReasons.add(CallForwardingInfo.REASON_BUSY);
        callForwardingReasons.add(CallForwardingInfo.REASON_NO_REPLY);
        callForwardingReasons.add(CallForwardingInfo.REASON_NOT_REACHABLE);
        callForwardingReasons.add(CallForwardingInfo.REASON_ALL);
        callForwardingReasons.add(CallForwardingInfo.REASON_ALL_CONDITIONAL);

        // Enable Call Forwarding
        for (int callForwardingReasonToEnable : callForwardingReasons) {
            CountDownLatch latch = new CountDownLatch(1);
            // Disregard success or failure; just make sure it reports back.
            Consumer<Integer> ignoringResultListener = (x) -> latch.countDown();

            final CallForwardingInfo callForwardingInfoToEnable = new CallForwardingInfo(
                    true,
                    callForwardingReasonToEnable,
                    TEST_FORWARD_NUMBER,
                    // time seconds
                    1);
            Log.d(TAG, "[testSetCallForwarding] Enable Call Forwarding. Reason: "
                    + callForwardingReasonToEnable + " Number: " + TEST_FORWARD_NUMBER
                    + " Time Seconds: 1");
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.setCallForwarding(callForwardingInfoToEnable,
                            getContext().getMainExecutor(), ignoringResultListener));
            // TODO: this takes way too long on a real network (upwards of 40s).
            // assertTrue("No response for forwarding for reason " + callForwardingReasonToEnable,
            //        latch.await(TIMEOUT_FOR_NETWORK_OPS * 3, TimeUnit.MILLISECONDS));
        }

        // Disable Call Forwarding
        for (int callForwardingReasonToDisable : callForwardingReasons) {
            CountDownLatch latch = new CountDownLatch(1);
            // Disregard success or failure; just make sure it reports back.
            Consumer<Integer> ignoringResultListener = (x) -> latch.countDown();

            final CallForwardingInfo callForwardingInfoToDisable = new CallForwardingInfo(
                    false,
                    callForwardingReasonToDisable,
                    TEST_FORWARD_NUMBER,
                    // time seconds
                    1);
            Log.d(TAG, "[testSetCallForwarding] Disable Call Forwarding. Reason: "
                    + callForwardingReasonToDisable + " Number: " + TEST_FORWARD_NUMBER
                    + " Time Seconds: 1");
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.setCallForwarding(callForwardingInfoToDisable,
                            getContext().getMainExecutor(), ignoringResultListener));
            // TODO: this takes way too long on a real network (upwards of 40s).
            //assertTrue("No response for forwarding for reason " + callForwardingReasonToDisable,
            //        latch.await(TIMEOUT_FOR_NETWORK_OPS * 3, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    public void testGetCallWaitingStatus() throws Exception {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            Log.d(TAG, "skipping test on device without FEATURE_TELEPHONY present");
            return;
        }
        Set<Integer> validCallWaitingStatuses = new HashSet<Integer>();
        validCallWaitingStatuses.add(TelephonyManager.CALL_WAITING_STATUS_ENABLED);
        validCallWaitingStatuses.add(TelephonyManager.CALL_WAITING_STATUS_DISABLED);
        validCallWaitingStatuses.add(TelephonyManager.CALL_WAITING_STATUS_UNKNOWN_ERROR);
        validCallWaitingStatuses.add(TelephonyManager.CALL_WAITING_STATUS_NOT_SUPPORTED);
        validCallWaitingStatuses.add(TelephonyManager.CALL_WAITING_STATUS_FDN_CHECK_FAILURE);

        LinkedBlockingQueue<Integer> callWaitingStatusResult = new LinkedBlockingQueue<>(1);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager, (tm) -> tm.getCallWaitingStatus(getContext().getMainExecutor(),
                        callWaitingStatusResult::offer));
        assertTrue(validCallWaitingStatuses.contains(
                callWaitingStatusResult.poll(TIMEOUT_FOR_NETWORK_OPS, TimeUnit.MILLISECONDS)));
    }

    @Test
    public void testSetCallWaitingStatus() throws Exception {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CALLING));

        Set<Integer> validCallWaitingErrors = new HashSet<Integer>();
        validCallWaitingErrors.add(TelephonyManager.CALL_WAITING_STATUS_UNKNOWN_ERROR);
        validCallWaitingErrors.add(TelephonyManager.CALL_WAITING_STATUS_NOT_SUPPORTED);
        validCallWaitingErrors.add(TelephonyManager.CALL_WAITING_STATUS_FDN_CHECK_FAILURE);
        Executor executor = getContext().getMainExecutor();
        {
            LinkedBlockingQueue<Integer> callWaitingResult = new LinkedBlockingQueue<>(1);

            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.setCallWaitingEnabled(true, executor, callWaitingResult::offer));
            Integer result = callWaitingResult.poll(TIMEOUT_FOR_NETWORK_OPS, TimeUnit.MILLISECONDS);
            assertNotNull("Never got callback from set call waiting", result);
            if (result != TelephonyManager.CALL_WAITING_STATUS_ENABLED) {
                assertTrue("Call waiting callback got an invalid value: " + result,
                        validCallWaitingErrors.contains(result));
            }
        }

        {
            LinkedBlockingQueue<Integer> callWaitingResult = new LinkedBlockingQueue<>(1);

            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.setCallWaitingEnabled(false, executor, callWaitingResult::offer));
            Integer result = callWaitingResult.poll(TIMEOUT_FOR_NETWORK_OPS, TimeUnit.MILLISECONDS);
            assertNotNull("Never got callback from set call waiting", result);
            if (result != TelephonyManager.CALL_WAITING_STATUS_DISABLED) {
                assertTrue("Call waiting callback got an invalid value: " + result,
                        validCallWaitingErrors.contains(result));
            }
        }
    }

    @Test
    public void testGetRadioHalVersion() {
        Pair<Integer, Integer> version = mTelephonyManager.getRadioHalVersion();

        // The version must be valid, and the versions start with 1.0
        assertFalse("Invalid Radio HAL Version: " + version,
                version.first < 1 || version.second < 0);
    }

    @Test
    public void testCreateForPhoneAccountHandle() {
        if (!mTelephonyManager.isVoiceCapable()) {
            Log.d(TAG, "Skipping test that requires config_voice_capable is true");
            return;
        }
        int subId = SubscriptionManager.getDefaultDataSubscriptionId();
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            Log.d(TAG, "Skipping test that requires DefaultDataSubscriptionId setting");
            return;
        }

        TelecomManager telecomManager = getContext().getSystemService(TelecomManager.class);
        PhoneAccountHandle handle =
                telecomManager.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL);
        TelephonyManager telephonyManager = mTelephonyManager.createForPhoneAccountHandle(handle);
        String globalSubscriberId = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.getSubscriberId());
        String localSubscriberId = ShellIdentityUtils.invokeMethodWithShellPermissions(
                telephonyManager, (tm) -> tm.getSubscriberId());
        assertEquals(globalSubscriberId, localSubscriberId);
    }

    @Test
    public void testCreateForPhoneAccountHandle_InvalidHandle(){
        PhoneAccountHandle handle =
                new PhoneAccountHandle(new ComponentName("com.example.foo", "bar"), "baz");
        assertNull(mTelephonyManager.createForPhoneAccountHandle(handle));
    }

    @Test
    public void testGetPhoneAccountHandle() {
        TelecomManager telecomManager = getContext().getSystemService(TelecomManager.class);
        PhoneAccountHandle defaultAccount = telecomManager
                .getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL);
        try {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(
                            "android.permission.READ_PRIVILEGED_PHONE_STATE");
            PhoneAccountHandle phoneAccountHandle = mTelephonyManager.getPhoneAccountHandle();
            assertEquals(phoneAccountHandle, defaultAccount);
        } catch (SecurityException e) {
            fail("TelephonyManager#getPhoneAccountHandle requires READ_PRIVILEGED_PHONE_STATE");
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    /**
     * Tests that the phone count returned is valid.
     */
    @Test
    public void testGetPhoneCount() {
        int phoneCount = mTelephonyManager.getPhoneCount();
        int phoneType = mTelephonyManager.getPhoneType();
        switch (phoneType) {
            case TelephonyManager.PHONE_TYPE_GSM:
            case TelephonyManager.PHONE_TYPE_CDMA:
                assertTrue("Phone count should be > 0", phoneCount > 0);
                break;
            case TelephonyManager.PHONE_TYPE_NONE:
                assertTrue("Phone count should be >= 0", phoneCount >= 0);
                break;
            default:
                throw new IllegalArgumentException("Did you add a new phone type? " + phoneType);
        }
    }

    /**
     * Tests that the device properly reports either a valid IMEI, MEID/ESN, or a valid MAC address
     * if only a WiFi device. At least one of them must be valid.
     */
    @Test
    public void testGetDeviceId() {
        String deviceId = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getDeviceId());
        verifyDeviceId(deviceId);
    }

    /**
     * Tests the max number of active SIMs method
     */
    @Test
    public void testGetMaxNumberOfSimultaneouslyActiveSims() {
        int maxNum = mTelephonyManager.getMaxNumberOfSimultaneouslyActiveSims();
        assertTrue(maxNum >= 1);
    }

    /**
     * Tests that the device properly reports either a valid IMEI, MEID/ESN, or a valid MAC address
     * if only a WiFi device. At least one of them must be valid.
     */
    @Test
    public void testGetDeviceIdForSlot() {
        String deviceId = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getDeviceId(mTelephonyManager.getSlotIndex()));
        verifyDeviceId(deviceId);
        // Also verify that no exception is thrown for any slot index (including invalid ones)
        for (int i = -1; i <= mTelephonyManager.getPhoneCount(); i++) {
            // The compiler error 'local variables referenced from a lambda expression must be final
            // or effectively final' is reported when using i, so assign it to a final variable.
            final int currI = i;
            ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    (tm) -> tm.getDeviceId(currI));
        }
    }

    private void verifyDeviceId(String deviceId) {
        if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            // Either IMEI or MEID need to be valid.
            try {
                assertImei(deviceId);
            } catch (AssertionError e) {
                assertMeidEsn(deviceId);
            }
        } else if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            assertSerialNumber();
            assertMacAddress(getWifiMacAddress());
        } else if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            assertSerialNumber();
            assertMacAddress(getBluetoothMacAddress());
        } else if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_ETHERNET)) {
            assertTrue(mCm.getNetworkInfo(ConnectivityManager.TYPE_ETHERNET) != null);
        }
    }

    private static void assertImei(String id) {
        assertFalse("Imei should not be empty or null", TextUtils.isEmpty(id));
        // IMEI may include the check digit
        String imeiPattern = "[0-9]{14,15}";
        String invalidPattern = "[0]{14,15}";
        assertTrue("IMEI " + id + " does not match pattern " + imeiPattern,
                Pattern.matches(imeiPattern, id));
        assertFalse("IMEI " + id + " must not be a zero sequence" + invalidPattern,
                Pattern.matches(invalidPattern, id));
        if (id.length() == 15) {
            // if the ID is 15 digits, the 15th must be a check digit.
            assertImeiCheckDigit(id);
        }
    }

    private static void assertImeiCheckDigit(String deviceId) {
        int expectedCheckDigit = getLuhnCheckDigit(deviceId.substring(0, 14));
        int actualCheckDigit = Character.digit(deviceId.charAt(14), 10);
        assertEquals("Incorrect check digit for " + deviceId, expectedCheckDigit, actualCheckDigit);
    }

    /**
     * Use decimal value (0-9) to index into array to get sum of its digits
     * needed by Lunh check.
     *
     * Example: DOUBLE_DIGIT_SUM[6] = 3 because 6 * 2 = 12 => 1 + 2 = 3
     */
    private static final int[] DOUBLE_DIGIT_SUM = {0, 2, 4, 6, 8, 1, 3, 5, 7, 9};

    /**
     * Calculate the check digit by starting from the right, doubling every
     * each digit, summing all the digits including the doubled ones, and
     * finding a number to make the sum divisible by 10.
     *
     * @param deviceId not including the check digit
     * @return the check digit
     */
    private static int getLuhnCheckDigit(String deviceId) {
        int sum = 0;
        int dontDoubleModulus = deviceId.length() % 2;
        for (int i = deviceId.length() - 1; i >= 0; --i) {
            int digit = Character.digit(deviceId.charAt(i), 10);
            if (i % 2 == dontDoubleModulus) {
                sum += digit;
            } else {
                sum += DOUBLE_DIGIT_SUM[digit];
            }
        }
        sum %= 10;
        return sum == 0 ? 0 : 10 - sum;
    }

    private static void assertMeidEsn(String id) {
        // CDMA device IDs may either be a 14-hex-digit MEID or an
        // 8-hex-digit ESN.  If it's an ESN, it may not be a
        // pseudo-ESN.
        assertFalse("Meid ESN should not be empty or null", TextUtils.isEmpty(id));
        if (id.length() == 14) {
            assertMeidFormat(id);
        } else if (id.length() == 8) {
            assertHexadecimalEsnFormat(id);
        } else {
            fail("device id on CDMA must be 14-digit hex MEID or 8-digit hex ESN.");
        }
    }

    private static void assertHexadecimalEsnFormat(String deviceId) {
        String esnPattern = "[0-9a-fA-F]{8}";
        String invalidPattern = "[0]{8}";
        assertTrue("ESN hex device id " + deviceId + " does not match pattern " + esnPattern,
                Pattern.matches(esnPattern, deviceId));
        assertFalse("ESN hex device id " + deviceId + " must not be a pseudo-ESN",
                "80".equals(deviceId.substring(0, 2)));
        assertFalse("ESN hex device id " + deviceId + "must not be a zero sequence",
                Pattern.matches(invalidPattern, deviceId));
    }

    private static void assertMeidFormat(String deviceId) {
        // MEID must NOT include the check digit.
        String meidPattern = "[0-9a-fA-F]{14}";
        String invalidPattern = "[0]{14}";
        assertTrue("MEID device id " + deviceId + " does not match pattern "
                + meidPattern, Pattern.matches(meidPattern, deviceId));
        assertFalse("MEID device id " + deviceId + "must not be a zero sequence",
                Pattern.matches(invalidPattern, deviceId));
    }

    private void assertSerialNumber() {
        String serial = ShellIdentityUtils.invokeStaticMethodWithShellPermissions(
                Build::getSerial);
        assertNotNull("Non-telephony devices must have a Build.getSerial() number.",
                serial);
        assertTrue("Hardware id must be alphanumeric.",
                Pattern.matches("[0-9A-Za-z.,_-]+", serial));
    }

    private void assertMacAddress(String macAddress) {
        String macPattern = "([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}";
        assertTrue("MAC Address " + macAddress + " does not match pattern " + macPattern,
                Pattern.matches(macPattern, macAddress));
    }

    /** @return mac address which requires the WiFi system to be enabled */
    private String getWifiMacAddress() {
        WifiManager wifiManager = getContext().getSystemService(WifiManager.class);

        if (wifiManager.isWifiEnabled()) {
            return wifiManager.getConnectionInfo().getMacAddress();
        } else {
            try {
                runWithShellPermissionIdentity(() -> wifiManager.setWifiEnabled(true));

                return wifiManager.getConnectionInfo().getMacAddress();

            } finally {
                runWithShellPermissionIdentity(() -> wifiManager.setWifiEnabled(false));
            }
        }
    }

    private String getBluetoothMacAddress() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            return "";
        }

        return adapter.getAddress();
    }

    private static final String ISO_COUNTRY_CODE_PATTERN = "[a-z]{2}";

    @Test
    @ApiTest(apis = "android.telephony.TelephonyManager#getNetworkCountryIso")
    public void testGetNetworkCountryIso() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        String countryCode = mTelephonyManager.getNetworkCountryIso();
        ServiceState serviceState = mTelephonyManager.getServiceState();
        if (serviceState != null && (serviceState.getState()
                == ServiceState.STATE_IN_SERVICE || serviceState.getState()
                == ServiceState.STATE_EMERGENCY_ONLY)) {
            assertTrue("Country code '" + countryCode + "' did not match "
                    + ISO_COUNTRY_CODE_PATTERN,
                    Pattern.matches(ISO_COUNTRY_CODE_PATTERN, countryCode));
        } else {
            assertTrue("Country code could be empty when out of service",
                    Pattern.matches(ISO_COUNTRY_CODE_PATTERN, countryCode)
                    || TextUtils.isEmpty(countryCode));
        }

        int[] allSubs = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mSubscriptionManager, (sm) -> sm.getActiveSubscriptionIdList());
        for (int i : allSubs) {
            countryCode = mTelephonyManager.getNetworkCountryIso(
                    SubscriptionManager.getSlotIndex(i));
            serviceState = mTelephonyManager.createForSubscriptionId(i).getServiceState();

            if (serviceState != null && (serviceState.getState()
                    == ServiceState.STATE_IN_SERVICE || serviceState.getState()
                    == ServiceState.STATE_EMERGENCY_ONLY)) {
                assertTrue("Country code '" + countryCode + "' did not match "
                        + ISO_COUNTRY_CODE_PATTERN + " for slot " + i,
                        Pattern.matches(ISO_COUNTRY_CODE_PATTERN, countryCode));
            } else {
                assertTrue("Country code could be empty when out of service",
                        Pattern.matches(ISO_COUNTRY_CODE_PATTERN, countryCode)
                        || TextUtils.isEmpty(countryCode));
            }
        }

        for (int i = 0; i < mTelephonyManager.getPhoneCount(); i++) {
            countryCode = mTelephonyManager.getNetworkCountryIso(i);
            assertTrue("Country code must match " + ISO_COUNTRY_CODE_PATTERN + "or empty",
                    Pattern.matches(ISO_COUNTRY_CODE_PATTERN, countryCode)
                    || TextUtils.isEmpty(countryCode));
        }
    }

    @Test
    public void testSetSystemSelectionChannels() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // Get initial list of system selection channels if the API is available
        List<RadioAccessSpecifier> initialSpecifiers = tryGetSystemSelectionChannels();
        // TODO (b/189255895): Don't allow empty or null channels once API is enforced in U.
        boolean getAvailable = initialSpecifiers != null && !initialSpecifiers.isEmpty();
        Log.d(TAG, "getSystemSelectionChannels is " + (getAvailable ? "" : "not ") + "available.");

        List<RadioAccessSpecifier> validSpecifiers = new ArrayList<>();
        List<RadioAccessSpecifier> specifiers;
        for (int accessNetworkType : TelephonyUtils.ALL_BANDS.keySet()) {
            List<Integer> validBands = new ArrayList<>();
            for (int band : TelephonyUtils.ALL_BANDS.get(accessNetworkType)) {
                // Set each band to see which ones are supported by the modem
                RadioAccessSpecifier specifier = new RadioAccessSpecifier(
                        accessNetworkType, new int[]{band}, new int[]{});
                boolean success = trySetSystemSelectionChannels(
                        Collections.singletonList(specifier), true);
                if (success) {
                    validBands.add(band);

                    // Try calling the API that doesn't provide feedback.
                    // We have no way of knowing if it succeeds, so just make sure nothing crashes.
                    trySetSystemSelectionChannels(Collections.singletonList(specifier), false);

                    if (getAvailable) {
                        // Assert that we get back the value we set.
                        specifiers = tryGetSystemSelectionChannels();
                        assertNotNull(specifiers);
                        assertEquals(1, specifiers.size());
                        assertEquals(specifier, specifiers.get(0));
                    }
                }
            }
            if (!validBands.isEmpty()) {
                validSpecifiers.add(new RadioAccessSpecifier(accessNetworkType,
                        validBands.stream().mapToInt(i -> i).toArray(), new int[]{}));
            }
        }

        // Call setSystemSelectionChannels with an empty list and verify no error
        if (!trySetSystemSelectionChannels(Collections.emptyList(), true)) {
            // TODO (b/189255895): Reset initial system selection channels on failure
            fail("Failed to call setSystemSelectionChannels with an empty list.");
        }

        // Verify that getSystemSelectionChannels returns all valid specifiers
        specifiers = tryGetSystemSelectionChannels();
        // TODO (b/189255895): Uncomment in U after getSystemSelectionChannels is enforced
        //assertNotNull(specifiers);
        //assertEquals(specifiers.size(), validSpecifiers.size());
        //assertTrue(specifiers.containsAll(validSpecifiers));

        // Call setSystemSelectionChannels with all valid specifiers to test batch operations
        if (!trySetSystemSelectionChannels(validSpecifiers, true)) {
            // TODO (b/189255895): Reset initial system selection channels on failure
            // TODO (b/189255895): Fail once setSystemSelectionChannels is enforced properly
            Log.e(TAG, "Failed to call setSystemSelectionChannels with all valid specifiers.");
        }

        // Reset the values back to the original.
        if (getAvailable) {
            trySetSystemSelectionChannels(initialSpecifiers, true);
        }
    }

    private List<RadioAccessSpecifier> tryGetSystemSelectionChannels() {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();
        List<RadioAccessSpecifier> channels = null;
        try {
            channels = mTelephonyManager.getSystemSelectionChannels();
        } catch (IllegalStateException ignored) {
            // TODO (b/189255895): Reset and fail in U after getSystemSelectionChannels is enforced
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
        return channels;
    }

    private boolean trySetSystemSelectionChannels(List<RadioAccessSpecifier> specifiers,
            boolean useCallback) {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();
        boolean success = false;
        try {
            if (useCallback) {
                LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue<>(1);
                // This is a oneway binder call, meaning we may return before the permission check
                // happens. Hold shell permissions until we get a response.
                mTelephonyManager.setSystemSelectionChannels(
                        specifiers, getContext().getMainExecutor(), queue::offer);
                Boolean result = queue.poll(2000, TimeUnit.MILLISECONDS);

                // Ensure we get a result
                assertNotNull(result);
                success = result;
            } else {
                mTelephonyManager.setSystemSelectionChannels(specifiers);
                success = true;
            }
        } catch (InterruptedException e) {
            // TODO (b/189255895): Reset initial system selection channels on failure
            fail("setSystemSelectionChannels interrupted.");
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
        return success;
    }

    @Test
    public void testGetSimCountryIso() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        String countryCode = mTelephonyManager.getSimCountryIso();
        if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            assertTrue("Country code '" + countryCode + "' did not match "
                            + ISO_COUNTRY_CODE_PATTERN,
                    Pattern.matches(ISO_COUNTRY_CODE_PATTERN, countryCode));
        } else {
            // Non-telephony may still have the property defined if it has a SIM.
        }
    }

    @Test
    public void testResetSettings() throws Exception {
        UserManager userManager = getContext().getSystemService(UserManager.class);

        boolean canChangeMobileNetworkSettings = userManager != null
                && !userManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS);
        assertTrue("Primary user must be able to configure mobile networks to pass this test",
                canChangeMobileNetworkSettings);
        boolean initialDataSetting = isDataEnabled();

        //First check permissions are correct
        try {
            mTelephonyManager.resetSettings();
            fail("TelephonyManager#resetSettings requires the"
                    + " android.Manifest.permission.NETWORK_SETTINGS permission");
        } catch (SecurityException e) {
            //expected
        }
        // and then do a reset to move data to default.
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    TelephonyManager::resetSettings,
                    "android.permission.NETWORK_SETTINGS",
                    "android.permission.MODIFY_PHONE_STATE");
        } catch (SecurityException e) {
            e.printStackTrace();
            fail(e.toString());
        }
        // This may timeout because the default is equal to the initial data setting, but there is
        // no way to definitively check what the default should be, so assume the default will be
        // set within TOLERANCE time.
        TelephonyUtils.pollUntilTrue(() -> initialDataSetting != isDataEnabled(), 5 /*times*/,
                TOLERANCE/5 /*timeout per poll*/);

        boolean defaultDataSetting = isDataEnabled();

        // set data to not the default!
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                tm -> tm.setDataEnabled(!defaultDataSetting));
        assertTrue("Data enable change didn't work",
                TelephonyUtils.pollUntilTrue(() -> defaultDataSetting != isDataEnabled(),
                        5 /*times*/, TOLERANCE/5 /*timeout per poll*/));

        // and then do a reset to move data to default again.
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    TelephonyManager::resetSettings,
                    "android.permission.NETWORK_SETTINGS",
                    "android.permission.MODIFY_PHONE_STATE");
        } catch (SecurityException e) {
            e.printStackTrace();
            fail(e.toString());
        }

        assertTrue("resetSettings did not reset default data",
                TelephonyUtils.pollUntilTrue(() -> defaultDataSetting == isDataEnabled(),
                        5 /*times*/, TOLERANCE/5 /*timeout per poll*/));
    }

    @Test
    public void testNetworkTypeMatchesDataNetworkType() throws Exception {
        assertEquals(mTelephonyManager.getDataNetworkType(),
                mTelephonyManager.getNetworkType());
    }

    @Test
    public void testNetworkTypeMatchesCellIdentity() throws Exception {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));
        ServiceState ss = mTelephonyManager.getServiceState();
        assertNotNull(ss);
        for (NetworkRegistrationInfo nri : ss.getNetworkRegistrationInfoList()) {
            final int networkType = nri.getAccessNetworkTechnology();
            final CellIdentity cid = nri.getCellIdentity();
            if (nri.getTransportType() == AccessNetworkConstants.TRANSPORT_TYPE_WLAN) {
                assertTrue("NetworkType for WLAN transport must be IWLAN if registered or"
                        + " UNKNOWN if unregistered",
                    networkType == TelephonyManager.NETWORK_TYPE_UNKNOWN
                            || networkType == TelephonyManager.NETWORK_TYPE_IWLAN);
                assertNull("There is no valid cell type for WLAN", cid);
                continue;
            }
            if (!nri.isRegistered() && !nri.isEmergencyEnabled()) {
                assertEquals(
                        "Network type cannot be known unless it is providing some service",
                        TelephonyManager.NETWORK_TYPE_UNKNOWN, networkType);
                assertNull(cid);
                continue;
            }

            assertEquals(nri.getTransportType(), AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
            if (nri.isRegistered() || (nri.isEmergencyEnabled() && !nri.isSearching())) {
                assertNotEquals("Network type must be known if it is providing some service",
                        TelephonyManager.NETWORK_TYPE_UNKNOWN, networkType);
                assertNotNull("The cid must be known for a cell providing service", cid);
                // The network type must roughly match the CellIdentity type
                assertTrue("The network type must be valid for the current cell",
                        sNetworkTypes.get(cid.getClass()).contains(networkType));
            }
        }
    }

    @Test
    public void testGetServiceState() throws InterruptedException {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        if (mCm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) == null) {
            Log.d(TAG, "Skipping test that requires ConnectivityManager.TYPE_MOBILE");
            return;
        }

        TestThread t = new TestThread(() -> {
            Looper.prepare();

            mListener = new PhoneStateListener() {
                @Override
                public void onServiceStateChanged(ServiceState serviceState) {
                    synchronized (mLock) {
                        mServiceState = serviceState;
                        mLock.notify();
                    }
                }
            };
            mTelephonyManager.listen(mListener, PhoneStateListener.LISTEN_SERVICE_STATE);
            Looper.loop();
        });

        synchronized (mLock) {
            t.start();
            mLock.wait(TOLERANCE);
        }

        assertEquals(mServiceState, mTelephonyManager.getServiceState());
        assertServiceStateSanitization(mServiceState, mTelephonyManager.getServiceState(
                TelephonyManager.INCLUDE_LOCATION_DATA_NONE));
        assertServiceStateFineLocationSanitization(mServiceState,
                mTelephonyManager.getServiceState(TelephonyManager.INCLUDE_LOCATION_DATA_COARSE));
        assertEquals(mServiceState, mTelephonyManager.getServiceState(
                TelephonyManager.INCLUDE_LOCATION_DATA_FINE));
    }

    private void assertServiceStateSanitization(ServiceState expectedServiceState,
            ServiceState receivedServiceState) {
        assertNotEquals(null, receivedServiceState);
        assertServiceStateFineLocationSanitization(expectedServiceState, receivedServiceState);

        assertTrue(TextUtils.isEmpty(receivedServiceState.getOperatorAlphaLong()));
        assertTrue(TextUtils.isEmpty(receivedServiceState.getOperatorAlphaShort()));
        assertTrue(TextUtils.isEmpty(receivedServiceState.getOperatorNumeric()));
    }

    private void assertServiceStateFineLocationSanitization(ServiceState expectedServiceState,
            ServiceState receivedServiceState) {
        assertNotEquals(null, receivedServiceState);

        assertEquals(expectedServiceState.getVoiceRegState(),
                receivedServiceState.getVoiceRegState());
        assertEquals(expectedServiceState.getDataRegState(),
                receivedServiceState.getDataRegState());
        assertEquals(expectedServiceState.getDataNetworkType(),
                receivedServiceState.getDataNetworkType());
        assertEquals(expectedServiceState.getDataRoaming(),
                receivedServiceState.getDataRoaming());
        assertEquals(expectedServiceState.getRilVoiceRadioTechnology(),
                receivedServiceState.getRilVoiceRadioTechnology());

        if (receivedServiceState.getNetworkRegistrationInfoList() != null) {
            for (NetworkRegistrationInfo nrs : receivedServiceState
                    .getNetworkRegistrationInfoList()) {
                assertNull(nrs.getCellIdentity());
            }
        }
    }

    @Test
    public void testGetServiceStateForInactiveSub() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        if (mCm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) == null) {
            Log.d(TAG, "Skipping test that requires ConnectivityManager.TYPE_MOBILE");
            return;
        }

        int[] allSubs  = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mSubscriptionManager, (sm) ->sm.getActiveSubscriptionIdList());
        // generate a subscription that is valid (>0) but inactive (not part of active subId list)
        // A simple way to do this is sum the active subIds and add 1
        int inactiveValidSub = 1;
        for (int sub : allSubs) {
            inactiveValidSub += sub;
        }

        assertNull(mTelephonyManager.createForSubscriptionId(inactiveValidSub).getServiceState());
    }

    // This test is to ensure the RAT IWLAN is not reported on WWAN transport if the device is
    // operated in AP-assisted mode.
    @Test
    @CddTest(requirement = "7.4.1/C-4-1")
    public void testIWlanServiceState() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        if (mCm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) == null) {
            Log.d(TAG, "Skipping test that requires ConnectivityManager.TYPE_MOBILE");
            return;
        }
        String mode = SystemProperties.get("ro.telephony.iwlan_operation_mode");
        if (!mode.equals("legacy")) {
            ServiceState ss = mTelephonyManager.getServiceState();
            if (ss != null) {
                for (NetworkRegistrationInfo nri : ss.getNetworkRegistrationInfoList()) {
                    if (nri.getTransportType() == AccessNetworkConstants.TRANSPORT_TYPE_WWAN) {
                        assertNotEquals(TelephonyManager.NETWORK_TYPE_IWLAN,
                                nri.getAccessNetworkTechnology());
                    }
                }
            }
        }
    }

    private MockPhoneCapabilityListener mMockPhoneCapabilityListener;

    private class MockPhoneCapabilityListener extends TelephonyCallback
            implements TelephonyCallback.PhoneCapabilityListener {
        @Override
        public void onPhoneCapabilityChanged(PhoneCapability capability) {
            synchronized (mLock) {
                mPhoneCapability = capability;
                mOnPhoneCapabilityChanged = true;
                mLock.notify();
            }
        }
    }

    @Test
    public void testGetPhoneCapabilityAndVerify() {
        boolean is5gStandalone = getContext().getResources().getBoolean(
                Resources.getSystem().getIdentifier("config_telephony5gStandalone", "bool",
                        "android"));
        boolean is5gNonStandalone = getContext().getResources().getBoolean(
                Resources.getSystem().getIdentifier("config_telephony5gNonStandalone", "bool",
                        "android"));
        int[] deviceNrCapabilities = new int[0];
        if (is5gStandalone || is5gNonStandalone) {
            List<Integer> list = new ArrayList<>();
            if (is5gNonStandalone) {
                list.add(DEVICE_NR_CAPABILITY_NSA);
            }
            if (is5gStandalone) {
                list.add(DEVICE_NR_CAPABILITY_SA);
            }
            deviceNrCapabilities = list.stream().mapToInt(Integer::valueOf).toArray();
        }

        PhoneCapability phoneCapability = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.getPhoneCapability());

        assertArrayEquals(deviceNrCapabilities, phoneCapability.getDeviceNrCapabilities());
    }

    @Test
    public void testGetSimLocale() throws InterruptedException {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            Log.d(TAG,"skipping test that requires Telephony");
            return;
        }
        if (SubscriptionManager.getDefaultSubscriptionId()
                == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            fail("Expected SIM inserted");
        }
        Locale locale = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getSimLocale());
        Log.d(TAG, "testGetSimLocale: " + locale);
        assertNotNull(locale);
    }

    /**
     * Tests that a GSM device properly reports either the correct TAC (type allocation code) or
     * null.
     * The TAC should match the first 8 digits of the IMEI.
     */
    @Test
    public void testGetTac() {
        String tac = mTelephonyManager.getTypeAllocationCode();
        String imei = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getImei());

        if (tac == null || imei == null) {
            return;
        }

        if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            if (mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
                assertEquals(imei.substring(0, 8), tac);
            }
        }
    }

    /**
     * Tests that a CDMA device properly reports either the correct MC (manufacturer code) or null.
     * The MC should match the first 8 digits of the MEID.
     */
    @Test
    public void testGetMc() {
        String mc = mTelephonyManager.getManufacturerCode();
        String meid = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getMeid());

        if (mc == null || meid == null) {
            return;
        }

        // mc and meid should either be null or supported. empty string is not expected even if
        // the device does not support mc/meid.
        assertNotEquals("", mc);
        assertNotEquals("", meid);

        if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            if (mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
                assertEquals(meid.substring(0, 8), mc);
            }
        }
    }

    /**
     * Tests that the device properly reports either a valid IMEI or null.
     */
    @Test
    public void testGetImei() {
        String imei = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getImei());

        if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            if (mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
                assertImei(imei);
            }
        }
    }

    /**
     * Tests that the device properly reports either a valid IMEI or null.
     */
    @Test
    public void testGetImeiForSlot() {
        for (int i = 0; i < mTelephonyManager.getPhoneCount(); i++) {
            // The compiler error 'local variables referenced from a lambda expression must be final
            // or effectively final' is reported when using i, so assign it to a final variable.
            final int currI = i;
            String imei = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    (tm) -> tm.getImei(currI));
            if (!TextUtils.isEmpty(imei)) {
                assertImei(imei);
            }
        }

        // Also verify that no exception is thrown for any slot index (including invalid ones)
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getImei(-1));
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getImei(mTelephonyManager.getPhoneCount()));
    }

    /**
     * Verifies that {@link TelephonyManager#getRadioPowerState()} does not throw any exception
     * and returns radio on.
     */
    @Test
    public void testGetRadioPowerState() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // Also verify that no exception is thrown.
        assertThat(mTelephonyManager.getRadioPowerState()).isEqualTo(
                TelephonyManager.RADIO_POWER_ON);
    }

    /**
     * Verifies that {@link TelephonyManager#setCarrierDataEnabled(boolean)} does not throw any
     * exception. TODO enhance later if we have an API to get data enabled state.
     */
    @Test
    public void testSetCarrierDataEnabled() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_DATA));

        // Also verify that no exception is thrown.
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                (tm) -> tm.setCarrierDataEnabled(false));
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                (tm) -> tm.setCarrierDataEnabled(true));
    }

    /**
     * Verifies that {@link TelephonyManager#rebootModem()} does not throw any exception
     * and final radio state is radio power on.
     */
    @Test
    public void testRebootRadio() throws Throwable {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));
        if (mRadioVersion <= RADIO_HAL_VERSION_2_0) {
            Log.d(TAG, "Skipping test since rebootModem is not supported.");
            return;
        }

        TestThread t = new TestThread(() -> {
            Looper.prepare();

            mListener = new PhoneStateListener() {
                @Override
                public void onRadioPowerStateChanged(@RadioPowerState int state) {
                    synchronized (mLock) {
                        if (state == TelephonyManager.RADIO_POWER_ON && mHasRadioPowerOff) {
                            mRadioRebootTriggered = true;
                            mLock.notify();
                        } else if (state == TelephonyManager.RADIO_POWER_OFF) {
                            // reboot must go to power off
                            mHasRadioPowerOff = true;
                        }
                    }
                }
            };
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.listen(mListener,
                            PhoneStateListener.LISTEN_RADIO_POWER_STATE_CHANGED));
            Looper.loop();
        });

        assertThat(mTelephonyManager.getRadioPowerState()).isEqualTo(
                TelephonyManager.RADIO_POWER_ON);
        assertThat(mRadioRebootTriggered).isFalse();
        assertThat(mHasRadioPowerOff).isFalse();
        t.start();
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    TelephonyManager::rebootModem);
        } catch (Exception ex) {
            //skip this test if not supported or unsuccessful (success=false)
            return;
        }

        synchronized (mLock) {
            // reboot takes longer time
            if (!mRadioRebootTriggered) {
                mLock.wait(20000);
            }
        }
        assertThat(mTelephonyManager.getRadioPowerState()).isEqualTo(
                TelephonyManager.RADIO_POWER_ON);
        assertThat(mRadioRebootTriggered).isTrue();

        if (mListener != null) {
            // unregister the listener
            mTelephonyManager.listen(mListener, PhoneStateListener.LISTEN_NONE);
        }

        // note, other telephony states might not resumes properly at this point. e.g, service state
        // might still in the transition from OOS to In service. Thus we need to wait for in
        // service state before running next tests.
        t = new TestThread(() -> {
            Looper.prepare();

            mListener = new PhoneStateListener() {
                @Override
                public void onServiceStateChanged(ServiceState serviceState) {
                    synchronized (mLock) {
                        if (serviceState.getState() == ServiceState.STATE_IN_SERVICE) {
                            mServiceStateChangedCalled = true;
                            mLock.notify();
                        }
                    }
                }
            };
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.listen(mListener, PhoneStateListener.LISTEN_SERVICE_STATE));
            Looper.loop();
        });

        synchronized (mLock) {
            t.start();
            if (!mServiceStateChangedCalled) {
                mLock.wait(60000);
            }
        }
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.READ_PHONE_STATE");
        assertThat(mTelephonyManager.getServiceState().getState()).isEqualTo(
                ServiceState.STATE_IN_SERVICE);
    }

    /**
     * Verifies that {@link TelephonyManager#getAidForAppType(int)} does not throw any exception
     * for all supported subscription app type.
     */
    @Test
    public void testGetAidForAppType() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getAidForAppType(TelephonyManager.APPTYPE_SIM));
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getAidForAppType(TelephonyManager.APPTYPE_CSIM));
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getAidForAppType(TelephonyManager.APPTYPE_RUIM));
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getAidForAppType(TelephonyManager.APPTYPE_ISIM));
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getAidForAppType(TelephonyManager.APPTYPE_USIM));
    }

    /**
     * Verifies that {@link TelephonyManager#getIsimDomain()} does not throw any exception
     */
    @Test
    public void testGetIsimDomain() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getIsimDomain());
    }

    /**
     * Verifies that {@link TelephonyManager#getIsimImpu()} does not throw any exception when called
     * and has the correct permissions.
     */
    @Ignore("API moved back to @hide for Android R.")
    @Test
    public void testGetIsimImpu() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                TelephonyManager::getIsimImpu);
        // Try without the correct permissions and ensure it fails.
        try {
            mTelephonyManager.getIsimImpu();
            fail();
        } catch (SecurityException e) {
            // expected
        }
    }

    /**
     * Basic test to ensure {@link NetworkRegistrationInfo#getRegisteredPlmn()} provides valid
     * information.
     */
    @Test
    public void testNetworkRegistrationInfoRegisteredPlmn() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // get NetworkRegistration object
        ServiceState ss = mTelephonyManager.getServiceState();
        assertNotNull(ss);

        boolean hasRegistered = false;
        for (NetworkRegistrationInfo nwReg : ss.getNetworkRegistrationInfoList()) {
            if (nwReg.isRegistered()
                        && nwReg.getTransportType() == AccessNetworkConstants.TRANSPORT_TYPE_WWAN) {
                hasRegistered = true;
                String plmnId = nwReg.getRegisteredPlmn();
                // CDMA doesn't have PLMN IDs. Rather than put CID|NID here, instead it will be
                // empty. It's a case that's becoming less important over time, but for now a
                // device that's only registered on CDMA needs to pass this test.
                if (nwReg.getCellIdentity() instanceof android.telephony.CellIdentityCdma) {
                    assertTrue(TextUtils.isEmpty(plmnId));
                } else {
                    assertFalse(TextUtils.isEmpty(plmnId));
                    assertTrue("PlmnId() out of range [00000 - 999999], PLMN ID=" + plmnId,
                            plmnId.matches("^[0-9]{5,6}$"));
                }
            }
        }
        assertTrue(hasRegistered);
    }

    /**
     * Basic test to ensure {@link NetworkRegistrationInfo#isRoaming()} does not throw any
     * exception.
     */
    @Test
    public void testNetworkRegistrationInfoIsRoaming() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // get NetworkRegistration object
        NetworkRegistrationInfo nwReg = mTelephonyManager.getServiceState()
                .getNetworkRegistrationInfo(NetworkRegistrationInfo.DOMAIN_CS,
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertThat(nwReg).isNotNull();
        nwReg.isRoaming();
    }

    /**
     * Basic test to ensure {@link NetworkRegistrationInfo#getRoamingType()} ()} does not throw any
     * exception and returns valid result
     * @see ServiceState.RoamingType
     */
    @Test
    public void testNetworkRegistrationInfoGetRoamingType() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // get NetworkRegistration object for voice
        NetworkRegistrationInfo nwReg = mTelephonyManager.getServiceState()
                .getNetworkRegistrationInfo(NetworkRegistrationInfo.DOMAIN_CS,
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertNotNull(nwReg);
        assertThat(nwReg.getRoamingType()).isIn(ROAMING_TYPES);

        // getNetworkRegistration object for data
        // get NetworkRegistration object for voice
        nwReg = mTelephonyManager.getServiceState()
                .getNetworkRegistrationInfo(NetworkRegistrationInfo.DOMAIN_PS,
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertThat(nwReg).isNotNull();
        assertThat(nwReg.getRoamingType()).isIn(ROAMING_TYPES);
    }

    /**
     * Basic test to ensure {@link NetworkRegistrationInfo#getAccessNetworkTechnology()} not
     * throw any exception and returns valid result
     * @see android.telephony.Annotation.NetworkType
     */
    @Test
    public void testNetworkRegistationStateGetAccessNetworkTechnology() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // get NetworkRegistration object for voice
        NetworkRegistrationInfo nwReg = mTelephonyManager.getServiceState()
                .getNetworkRegistrationInfo(NetworkRegistrationInfo.DOMAIN_CS,
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertThat(nwReg).isNotNull();
        assertThat(nwReg.getAccessNetworkTechnology()).isIn(NETWORK_TYPES);

        // get NetworkRegistation object for data
        nwReg = mTelephonyManager.getServiceState()
                .getNetworkRegistrationInfo(NetworkRegistrationInfo.DOMAIN_PS,
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertThat(nwReg).isNotNull();
        assertThat(nwReg.getAccessNetworkTechnology()).isIn(NETWORK_TYPES);
    }


    /**
     * Tests that the device properly reports either a valid MEID or null.
     */
    @Test
    public void testGetMeid() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CDMA));

        String meid = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getMeid());

        if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            if (mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
                assertMeidEsn(meid);
            }
        }
    }

    /**
     * Tests that the device properly reports either a valid MEID or null.
     */
    @Test
    public void testGetMeidForSlot() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CDMA));

        SubscriptionManager sm = SubscriptionManager.from(getContext());
        List<SubscriptionInfo> subInfos = sm.getActiveSubscriptionInfoList();

        if (subInfos != null) {
            for (SubscriptionInfo subInfo : subInfos) {
                int slotIndex = subInfo.getSimSlotIndex();
                int subId = subInfo.getSubscriptionId();
                TelephonyManager tm = mTelephonyManager.createForSubscriptionId(subId);
                if (tm.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
                    String meid = ShellIdentityUtils.invokeMethodWithShellPermissions(
                            mTelephonyManager,
                            (telephonyManager) -> telephonyManager.getMeid(slotIndex));

                    if (!TextUtils.isEmpty(meid)) {
                        assertMeidEsn(meid);
                    }
                }
            }
        }

        // Also verify that no exception is thrown for any slot index (including invalid ones)
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getMeid(-1));
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getMeid(mTelephonyManager.getPhoneCount()));
    }

    /**
     * Tests sendDialerSpecialCode API.
     * Expects a security exception since the caller does not have carrier privileges or is not the
     * current default dialer app.
     */
    @Test
    public void testSendDialerSpecialCode() {
        try {
            mTelephonyManager.sendDialerSpecialCode("4636");
            fail("Expected SecurityException. App does not have carrier privileges or is not the "
                    + "default dialer app");
        } catch (SecurityException expected) {
        }
    }

    /**
     * Tests that the device properly reports the contents of EF_FPLMN or null
     */
    @Test
    public void testGetForbiddenPlmns() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        String[] plmns = mTelephonyManager.getForbiddenPlmns();

        int phoneType = mTelephonyManager.getPhoneType();
        switch (phoneType) {
            case TelephonyManager.PHONE_TYPE_GSM:
                assertNotNull("Forbidden PLMNs must be valid or an empty list!", plmns);
            case TelephonyManager.PHONE_TYPE_CDMA:
            case TelephonyManager.PHONE_TYPE_NONE:
                if (plmns == null) {
                    return;
                }
        }

        for(String plmn : plmns) {
            assertTrue(
                    "Invalid Length for PLMN-ID, must be 5 or 6! plmn=" + plmn,
                    plmn.length() >= 5 && plmn.length() <= 6);
            assertTrue(
                    "PLMNs must be strings of digits 0-9! plmn=" + plmn,
                    android.text.TextUtils.isDigitsOnly(plmn));
        }
    }

    /**
     * Tests that the device properly sets and pads the contents of EF_FPLMN
     */
    @Test
    public void testSetForbiddenPlmns() {
        assumeTrue(supportSetFplmn());

        String[] originalFplmns = mTelephonyManager.getForbiddenPlmns();
        try {
            int numFplmnsSet = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.setForbiddenPlmns(FPLMN_TEST));
            String[] writtenFplmns = mTelephonyManager.getForbiddenPlmns();
            assertEquals("Wrong return value for setFplmns with less than required fplmns: "
                    + numFplmnsSet, FPLMN_TEST.size(), numFplmnsSet);
            assertEquals("Wrong Fplmns content written", FPLMN_TEST, Arrays.asList(writtenFplmns));
        } finally {
            // Restore
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.setForbiddenPlmns(Arrays.asList(originalFplmns)));
        }
    }

    /**
     * Tests that the device properly truncates the contents of EF_FPLMN when provided size
     * is too big.
     */
    @Test
    public void testSetForbiddenPlmnsTruncate() {
        assumeTrue(supportSetFplmn());

        String[] originalFplmns = mTelephonyManager.getForbiddenPlmns();
        try {
            List<String> targetFplmns = new ArrayList<>();
            for (int i = 0; i < MIN_FPLMN_NUM; i++) {
                targetFplmns.add(PLMN_A);
            }
            for (int i = MIN_FPLMN_NUM; i < MAX_FPLMN_NUM; i++) {
                targetFplmns.add(PLMN_B);
            }
            int numFplmnsSet = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.setForbiddenPlmns(targetFplmns));
            String[] writtenFplmns = mTelephonyManager.getForbiddenPlmns();
            assertTrue("Wrong return value for setFplmns with overflowing fplmns: " + numFplmnsSet,
                    numFplmnsSet < MAX_FPLMN_NUM);
            assertEquals("Number of Fplmns set does not equal number of Fplmns available",
                    numFplmnsSet, writtenFplmns.length);
            assertEquals("Wrong Fplmns content written", targetFplmns.subList(0, numFplmnsSet),
                    Arrays.asList(writtenFplmns));
        } finally {
            // Restore
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.setForbiddenPlmns(Arrays.asList(originalFplmns)));
        }
    }

    /**
     * Tests that the device properly deletes the contents of EF_FPLMN
     */
    @Test
    public void testSetForbiddenPlmnsDelete() {
        assumeTrue(supportSetFplmn());

        String[] originalFplmns = mTelephonyManager.getForbiddenPlmns();
        try {
            // Support test for empty SIM
            List<String> targetDummyFplmns = new ArrayList<>();
            for (int i = 0; i < MIN_FPLMN_NUM; i++) {
                targetDummyFplmns.add(PLMN_A);
            }
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.setForbiddenPlmns(targetDummyFplmns));
            String[] writtenDummyFplmns = mTelephonyManager.getForbiddenPlmns();
            assertEquals(targetDummyFplmns, Arrays.asList(writtenDummyFplmns));

            List<String> targetFplmns = new ArrayList<>();
            int numFplmnsSet = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.setForbiddenPlmns(targetFplmns));
            String[] writtenFplmns = mTelephonyManager.getForbiddenPlmns();
            assertEquals("Wrong return value for setFplmns with empty list", 0, numFplmnsSet);
            assertEquals("Wrong number of Fplmns written", 0, writtenFplmns.length);
            // TODO wait for 10 minutes or so for the FPLMNS list to grow back
        } finally {
            // Restore
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.setForbiddenPlmns(Arrays.asList(originalFplmns)));
        }
    }


    /**
     * Tests that setForbiddenPlmns properly handles null input
     */
    @Test
    public void testSetForbiddenPlmnsVoid() {
        assumeTrue(supportSetFplmn());

        String[] originalFplmns = mTelephonyManager.getForbiddenPlmns();
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.setForbiddenPlmns(null));
            fail("Expected IllegalArgumentException. Null input is not allowed");
        } catch (IllegalArgumentException expected) {
        } finally {
            // Restore
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.setForbiddenPlmns(Arrays.asList(originalFplmns)));
        }
    }

    @Test
    public void testGetEquivalentHomePlmns() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        List<String> plmns = mTelephonyManager.getEquivalentHomePlmns();

        if (mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
            assertEquals(0, plmns.size());
        } else {
            for (String plmn : plmns) {
                assertTrue(
                        "Invalid Length for PLMN-ID, must be 5 or 6! plmn=" + plmn,
                        plmn.length() >= 5 && plmn.length() <= 6);
                assertTrue(
                        "PLMNs must be strings of digits 0-9! plmn=" + plmn,
                        android.text.TextUtils.isDigitsOnly(plmn));
            }
        }
    }

    /**
     * Tests that the device properly reports the contents of ManualNetworkSelectionPlmn
     * The setting is not persisted selection
     */
    @Test
    public void testGetManualNetworkSelectionPlmnNonPersisted() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        if (mTelephonyManager.getPhoneType() != TelephonyManager.PHONE_TYPE_GSM) return;

        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    (tm) -> tm.setNetworkSelectionModeManual(
                     TESTING_PLMN/* operatorNumeric */, false /* persistSelection */));
            String plmn = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                     (tm) -> tm.getManualNetworkSelectionPlmn());
            assertEquals(TESTING_PLMN, plmn);
        } finally {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.setNetworkSelectionModeAutomatic());
        }
    }

    /**
     * Tests that the device properly reports the contents of ManualNetworkSelectionPlmn
     * The setting is persisted selection
     */
    @Test
    public void testGetManualNetworkSelectionPlmnPersisted() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        if (mTelephonyManager.getPhoneType() != TelephonyManager.PHONE_TYPE_GSM) return;

        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    (tm) -> tm.setNetworkSelectionModeManual(
                     TESTING_PLMN/* operatorNumeric */, true /* persistSelection */));
            String plmn = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                     (tm) -> tm.getManualNetworkSelectionPlmn());
            assertEquals(TESTING_PLMN, plmn);
        } finally {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.setNetworkSelectionModeAutomatic());
        }
    }

    /**
     * Verify that TelephonyManager.getCardIdForDefaultEuicc returns a positive value or either
     * UNINITIALIZED_CARD_ID or UNSUPPORTED_CARD_ID.
     */
    @Test
    public void testGetCardIdForDefaultEuicc() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_EUICC));

        int cardId = mTelephonyManager.getCardIdForDefaultEuicc();
        assertTrue("Card ID for default EUICC is not a valid value",
                cardId == TelephonyManager.UNSUPPORTED_CARD_ID
                        || cardId == TelephonyManager.UNINITIALIZED_CARD_ID
                        || cardId >= 0);
    }

    /**
     * Tests that a SecurityException is thrown when trying to access UiccCardsInfo.
     */
    @Test
    public void testGetUiccCardsInfoException() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        try {
            // Requires READ_PRIVILEGED_PHONE_STATE or carrier privileges
            List<UiccCardInfo> infos = mTelephonyManager.getUiccCardsInfo();
            fail("Expected SecurityException. App does not have carrier privileges");
        } catch (SecurityException e) {
        }
    }

    /**
     * Tests that UiccCardsInfo methods don't crash.
     */
    @Test
    public void testGetUiccCardsInfo() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        // The API requires either READ_PRIVILEGED_PHONE_STATE or carrier privileges
        try {
            mTelephonyManager.getUiccCardsInfo();
            fail("Telephony#getUiccCardsInfo should throw SecurityException without "
                    + "READ_PRIVILEGED_PHONE_STATE nor carrier privileges");
        } catch (SecurityException expected) {
        }

        // With READ_PRIVILEGED_PHONE_STATE only, it should work
        List<UiccCardInfo> infos =
                ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getUiccCardsInfo());
        // test that these methods don't crash
        if (infos.size() > 0) {
            UiccCardInfo info = infos.get(0);
            info.getEid();
            info.isRemovable();
            info.isEuicc();
            info.getCardId();
            info.getPorts();
            info.getPhysicalSlotIndex();
            info.isRemovable();
        }

        // With carrier privileges only, it should also work
        try {
            CarrierPrivilegeUtils.withCarrierPrivileges(
                    getContext(),
                    SubscriptionManager.getDefaultSubscriptionId(),
                    () -> mTelephonyManager.getUiccCardsInfo());
        } catch (SecurityException se) {
            fail("TelephonyManager.getUiccCardsInfo should not throw SecurityException with "
                    + "carrier privileges");
        } catch (Exception e) {
            fail("Exception thrown when try to get carrier privileges.");
        }
    }

    private static Context getContext() {
        return InstrumentationRegistry.getContext();
    }

    /**
     * Tests that the device properly reports the contents of NetworkSelectionMode
     */
    @Test
    public void testGetNetworkSelectionMode() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.setNetworkSelectionModeAutomatic());
        } catch (Exception e) {
        }

        int networkMode = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getNetworkSelectionMode());

        assertEquals(TelephonyManager.NETWORK_SELECTION_MODE_AUTO, networkMode);
    }

    /**
     * Tests that the device properly sets the network selection mode to automatic.
     * Expects a security exception since the caller does not have carrier privileges.
     */
    @Test
    public void testSetNetworkSelectionModeAutomatic() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        try {
            mTelephonyManager.setNetworkSelectionModeAutomatic();
            fail("Expected SecurityException. App does not have carrier privileges.");
        } catch (SecurityException expected) {
        }
    }

    /**
     * Tests that the device properly asks the radio to connect to the input network and change
     * selection mode to manual.
     * Expects a security exception since the caller does not have carrier privileges.
     */
    @Test
    public void testSetNetworkSelectionModeManual() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        try {
            mTelephonyManager.setNetworkSelectionModeManual(
                    "" /* operatorNumeric */, false /* persistSelection */);
            fail("Expected SecurityException. App does not have carrier privileges.");
        } catch (SecurityException expected) {
        }
    }

    /**
     * Tests that the device properly check whether selection mode was manual.
     */
    @Test
    public void testIsManualNetworkSelectionAllowed() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        if (mTelephonyManager.getPhoneType() != TelephonyManager.PHONE_TYPE_GSM) return;

        assertTrue(ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.isManualNetworkSelectionAllowed()));
    }

    /**
     * Tests that the device properly sets the VoNr
     */
    @Test
    public void testIsVoNrEnabled() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }

        try {
            int result = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    (tm) -> tm.setVoNrEnabled(true));
            if (result ==  TelephonyManager.ENABLE_VONR_REQUEST_NOT_SUPPORTED) {
                return;
            }
        } catch (Exception e) {
        }

        assertTrue(ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.isVoNrEnabled()));
    }

    /**
     * Tests that a SecurityException is thrown when trying to set VoNR
     */
    @Test
    public void testSetVoNrEnabledException() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            Log.d(TAG, "Skipping test that requires FEATURE_TELEPHONY");
            return;
        }
        try {
            mTelephonyManager.setVoNrEnabled(true);
            fail("Expected SecurityException. App does not have carrier privileges.");
        } catch (SecurityException expected) {
        }
    }

    /**
     * Construct a CallAttributes object and test getters.
     */
    @Test
    public void testCallAttributes() {
        CallQuality cq = new CallQuality();
        PreciseCallState pcs = new PreciseCallState();
        CallAttributes ca = new CallAttributes(pcs, TelephonyManager.NETWORK_TYPE_UNKNOWN, cq);
        assertEquals(pcs, ca.getPreciseCallState());
        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN, ca.getNetworkType());
        assertEquals(cq, ca.getCallQuality());
    }

    /**
     * Checks that a zeroed-out default CallQuality object can be created
     */
    @Test
    public void testCallQuality() {
        CallQuality cq = new CallQuality();
        assertEquals(0, cq.getDownlinkCallQualityLevel());
        assertEquals(0, cq.getUplinkCallQualityLevel());
        assertEquals(0, cq.getCallDuration());
        assertEquals(0, cq.getNumRtpPacketsTransmitted());
        assertEquals(0, cq.getNumRtpPacketsReceived());
        assertEquals(0, cq.getNumRtpPacketsTransmittedLost());
        assertEquals(0, cq.getNumRtpPacketsNotReceived());
        assertEquals(0, cq.getAverageRelativeJitter());
        assertEquals(0, cq.getMaxRelativeJitter());
        assertEquals(0, cq.getAverageRoundTripTime());
        assertEquals(0, cq.getCodecType());
        assertEquals(false, cq.isRtpInactivityDetected());
        assertEquals(false, cq.isIncomingSilenceDetectedAtCallSetup());
        assertEquals(false, cq.isOutgoingSilenceDetectedAtCallSetup());
        assertEquals(0, cq.getNumVoiceFrames());
        assertEquals(0, cq.getNumNoDataFrames());
        assertEquals(0, cq.getNumDroppedRtpPackets());
        assertEquals(0, cq.getMinPlayoutDelayMillis());
        assertEquals(0, cq.getMaxPlayoutDelayMillis());
        assertEquals(0, cq.getNumRtpSidPacketsReceived());
        assertEquals(0, cq.getNumRtpDuplicatePackets());
    }

    /**
     * Validate CallQuality Parcel
     */
    @Test
    public void testCallQualityParcel() {
        CallQuality cq = new CallQuality.Builder()
                .setDownlinkCallQualityLevel(CallQuality.CALL_QUALITY_NOT_AVAILABLE)
                .setUplinkCallQualityLevel(CallQuality.CALL_QUALITY_NOT_AVAILABLE)
                .setCallDurationMillis(20000)
                .setNumRtpPacketsTransmitted(550)
                .setNumRtpPacketsReceived(450)
                .setNumRtpPacketsTransmittedLost(4)
                .setNumRtpPacketsNotReceived(6)
                .setAverageRelativeJitter(20)
                .setMaxRelativeJitter(30)
                .setAverageRoundTripTimeMillis(150)
                .setCodecType(0)
                .setRtpInactivityDetected(false)
                .setIncomingSilenceDetectedAtCallSetup(false)
                .setOutgoingSilenceDetectedAtCallSetup(false)
                .setNumVoiceFrames(300)
                .setNumNoDataFrames(300)
                .setNumDroppedRtpPackets(5)
                .setMinPlayoutDelayMillis(500)
                .setMaxPlayoutDelayMillis(1000)
                .setNumRtpSidPacketsReceived(300)
                .setNumRtpDuplicatePackets(0)
                .build();

        Parcel stateParcel = Parcel.obtain();
        cq.writeToParcel(stateParcel, 0);
        stateParcel.setDataPosition(0);

        CallQuality parcelCq = CallQuality.CREATOR.createFromParcel(stateParcel);
        assertThat(cq).isEqualTo(parcelCq);

    }

    // Reference: packages/services/Telephony/ecc/input/eccdata.txt
    private static final Map<String, String> EMERGENCY_NUMBERS_FOR_COUNTRIES =
            new HashMap<String, String>() {{
                put("au", "000");
                put("ca", "911");
                put("de", "112");
                put("gb", "999");
                put("in", "112");
                put("jp", "110");
                put("sg", "999");
                put("tw", "110");
                put("us", "911");
            }};

    /**
     * Tests TelephonyManager.getEmergencyNumberList.
     *
     * Also enforce country-specific emergency number in CTS.
     */
    @Test
    public void testGetEmergencyNumberList() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CALLING));

        Map<Integer, List<EmergencyNumber>> emergencyNumberList =
                mTelephonyManager.getEmergencyNumberList();

        assertFalse(emergencyNumberList == null);

        checkEmergencyNumberFormat(emergencyNumberList);

        int defaultSubId = mSubscriptionManager.getDefaultSubscriptionId();
        for (Map.Entry<String, String> entry : EMERGENCY_NUMBERS_FOR_COUNTRIES.entrySet()) {
            if (mTelephonyManager.getNetworkCountryIso().equals(entry.getKey())) {
                assertTrue(checkIfEmergencyNumberListHasSpecificAddress(
                        emergencyNumberList.get(defaultSubId), entry.getValue()));
            }
        }
    }

    /**
     * Tests TelephonyManager.getEmergencyNumberList(@EmergencyServiceCategories int categories).
     *
     */
    @Test
    public void testGetEmergencyNumberListForCategories() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CALLING));

        Map<Integer, List<EmergencyNumber>> emergencyNumberList =
                mTelephonyManager.getEmergencyNumberList(
                        EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE);

        assertFalse(emergencyNumberList == null);

        checkEmergencyNumberFormat(emergencyNumberList);

        int defaultSubId = mSubscriptionManager.getDefaultSubscriptionId();
        final String country_us = "us";
        final String country_us_police_number = "911";
        if (mTelephonyManager.getNetworkCountryIso().equals(country_us)) {
            assertTrue(checkIfEmergencyNumberListHasSpecificAddress(
                    emergencyNumberList.get(defaultSubId), country_us_police_number));
        }
        for (EmergencyNumber num : emergencyNumberList.get(defaultSubId)) {
            assertTrue(num.isInEmergencyServiceCategories(
                    EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE));
        }
    }

    /**
     * Tests TelephonyManager.isEmergencyNumber.
     *
     * Also enforce country-specific emergency number in CTS.
     */
    @Test
    public void testIsEmergencyNumber() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CALLING));

        for (Map.Entry<String, String> entry : EMERGENCY_NUMBERS_FOR_COUNTRIES.entrySet()) {
            if (mTelephonyManager.getNetworkCountryIso().equals(entry.getKey())) {
                assertTrue(mTelephonyManager.isEmergencyNumber(entry.getValue()));
            }
        }
    }

    /**
     * Tests TelephonyManager.isPotentialEmergencyNumber.
     */
    @Test
    public void testIsPotentialEmergencyNumber() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CALLING));

        String countryIso = mTelephonyManager.getNetworkCountryIso();
        String potentialEmergencyAddress = "91112345";
        // According to com.android.i18n.phonenumbers.ShortNumberInfo, in
        // these countries, if extra digits are added to an emergency number,
        // it no longer connects to the emergency service.
        if (countryIso.equals("br") || countryIso.equals("cl") || countryIso.equals("ni")) {
            assertFalse(ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    (tm) -> tm.isPotentialEmergencyNumber(potentialEmergencyAddress)));
        } else {
            assertTrue(ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    (tm) -> tm.isPotentialEmergencyNumber(potentialEmergencyAddress)));
        }
    }

    /**
     * Tests TelephonyManager.setCallComposerStatus and TelephonyManager.getCallComposerStatus.
     */
    @Test
    public void testSetGetCallComposerStatus() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CALLING));

        if (hasFeature(PackageManager.FEATURE_TELEPHONY_IMS)) {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    tm -> tm.setCallComposerStatus(TelephonyManager.CALL_COMPOSER_STATUS_OFF));
            int status = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    tm -> tm.getCallComposerStatus());
            assertThat(status).isEqualTo(TelephonyManager.CALL_COMPOSER_STATUS_OFF);

            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    tm -> tm.setCallComposerStatus(TelephonyManager.CALL_COMPOSER_STATUS_ON));
            status = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    tm -> tm.getCallComposerStatus());
            assertThat(status).isEqualTo(TelephonyManager.CALL_COMPOSER_STATUS_ON);
        } else {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    tm -> tm.setCallComposerStatus(TelephonyManager.CALL_COMPOSER_STATUS_OFF));
            int status = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    tm -> tm.getCallComposerStatus());
            assertThat(status).isEqualTo(TelephonyManager.CALL_COMPOSER_STATUS_OFF);

            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    tm -> tm.setCallComposerStatus(TelephonyManager.CALL_COMPOSER_STATUS_ON));
            status = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    tm -> tm.getCallComposerStatus());
            assertThat(status).isEqualTo(TelephonyManager.CALL_COMPOSER_STATUS_OFF);
        }
    }

    /**
     * Tests {@link TelephonyManager#getSupportedRadioAccessFamily()}
     */
    @Test
    public void testGetRadioAccessFamily() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        long raf = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getSupportedRadioAccessFamily());
        assertThat(raf).isNotEqualTo(TelephonyManager.NETWORK_TYPE_BITMASK_UNKNOWN);
    }

    private static void assertSetOpportunisticSubSuccess(int value) {
        assertThat(value).isEqualTo(TelephonyManager.SET_OPPORTUNISTIC_SUB_SUCCESS);
    }

    private static void assertSetOpportunisticNoOpportunisticSub(int value) {
        assertThat(value).isEqualTo(
                TelephonyManager.SET_OPPORTUNISTIC_SUB_NO_OPPORTUNISTIC_SUB_AVAILABLE);
    }

    /**
     * Tests {@link TelephonyManager#setPreferredOpportunisticDataSubscription} and
     * {@link TelephonyManager#getPreferredOpportunisticDataSubscription}
     */
    @Test
    public void testPreferredOpportunisticDataSubscription() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_DATA));

        int randomSubId = 1;
        int activeSubscriptionInfoCount = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mSubscriptionManager, (tm) -> tm.getActiveSubscriptionInfoCount());
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }
        if (mTelephonyManager.getPhoneCount() == 1) {
            return;
        }
        if (mTelephonyManager.getPhoneCount() == 2 && activeSubscriptionInfoCount != 2) {
            return;
        }
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                (tm) -> tm.setPreferredOpportunisticDataSubscription(
                        SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, false,
                        null, null));
        // wait for the data change to take effect
        waitForMs(500);
        int subId =
                ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                        (tm) -> tm.getPreferredOpportunisticDataSubscription());
        assertThat(subId).isEqualTo(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        List<SubscriptionInfo> subscriptionInfoList =
                ShellIdentityUtils.invokeMethodWithShellPermissions(mSubscriptionManager,
                        (tm) -> tm.getOpportunisticSubscriptions());
        Consumer<Integer> callbackSuccess = TelephonyManagerTest::assertSetOpportunisticSubSuccess;
        Consumer<Integer> callbackNoOpSub =
                TelephonyManagerTest::assertSetOpportunisticNoOpportunisticSub;
        if (subscriptionInfoList == null || subscriptionInfoList.size() == 0) {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.setPreferredOpportunisticDataSubscription(randomSubId, false,
                            AsyncTask.SERIAL_EXECUTOR, callbackNoOpSub));
            // wait for the data change to take effect
            waitForMs(500);
            subId = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    (tm) -> tm.getPreferredOpportunisticDataSubscription());
            assertThat(subId).isEqualTo(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        } else {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.setPreferredOpportunisticDataSubscription(
                            subscriptionInfoList.get(0).getSubscriptionId(), false,
                            AsyncTask.SERIAL_EXECUTOR, callbackSuccess));
            // wait for the data change to take effect
            waitForMs(500);
            subId = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    (tm) -> tm.getPreferredOpportunisticDataSubscription());
            assertThat(subId).isEqualTo(subscriptionInfoList.get(0).getSubscriptionId());
        }

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                (tm) -> tm.setPreferredOpportunisticDataSubscription(
                        SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, false,
                        AsyncTask.SERIAL_EXECUTOR, callbackSuccess));
        // wait for the data change to take effect
        waitForMs(500);
        subId = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getPreferredOpportunisticDataSubscription());
        assertThat(subId).isEqualTo(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
    }

    private static void assertUpdateAvailableNetworkSuccess(int value) {
        assertThat(value).isEqualTo(TelephonyManager.UPDATE_AVAILABLE_NETWORKS_SUCCESS);
    }

    private static void assertUpdateAvailableNetworkNoOpportunisticSub(int value) {
        assertThat(value).isEqualTo(
                TelephonyManager.UPDATE_AVAILABLE_NETWORKS_NO_OPPORTUNISTIC_SUB_AVAILABLE);
    }

    private static boolean checkIfEmergencyNumberListHasSpecificAddress(
            List<EmergencyNumber> emergencyNumberList, String address) {
        for (EmergencyNumber emergencyNumber : emergencyNumberList) {
            if (address.equals(emergencyNumber.getNumber())) {
                return true;
            }
        }
        return false;
    }

    private static void checkEmergencyNumberFormat(
            Map<Integer, List<EmergencyNumber>> emergencyNumberLists) {
        for (List<EmergencyNumber> emergencyNumberList : emergencyNumberLists.values()) {
            for (EmergencyNumber emergencyNumber : emergencyNumberList) {

                // Validate Emergency number address
                assertTrue(validateEmergencyNumberAddress(emergencyNumber.getNumber()));

                // Validate Emergency number country Iso
                assertTrue(validateEmergencyNumberCountryIso(emergencyNumber.getCountryIso()));

                // Validate Emergency number mnc
                assertTrue(validateEmergencyNumberMnc(emergencyNumber.getMnc()));

                // Validate Emergency service category list
                assertTrue(validateEmergencyServiceCategoryList(
                        emergencyNumber.getEmergencyServiceCategories()));

                // Validate Emergency number source list
                assertTrue(validateEmergencyNumberSourceList(
                        emergencyNumber.getEmergencyNumberSources()));

                // Validate Emergency URN list
                // (just verify it is not null, because the support of this field is optional)
                assertTrue(emergencyNumber.getEmergencyUrns() != null);

                // Validat Emergency call routing
                assertTrue(validateEmergencyCallRouting(
                        emergencyNumber.getEmergencyCallRouting()));

                // Valid the emergency number should be at least in a valid source.
                assertTrue(validateEmergencyNumberFromAnySource(emergencyNumber));

                // Valid the emergency number should be at least in a valid category.
                assertTrue(validateEmergencyNumberInAnyCategory(emergencyNumber));
            }

            // Validate compareTo
            assertTrue(validateEmergencyNumberCompareTo(emergencyNumberList));
        }
    }

    /**
     * Tests {@link TelephonyManager#updateAvailableNetworks}
     */
    @Test
    public void testUpdateAvailableNetworks() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        int randomSubId = 1;
        int activeSubscriptionInfoCount = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mSubscriptionManager, (tm) -> tm.getActiveSubscriptionInfoCount());
        boolean isOpportunisticNetworkEnabled = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.isOpportunisticNetworkEnabled());

        if (!isOpportunisticNetworkEnabled) {
            return;
        }
        if (mTelephonyManager.getPhoneCount() == 1) {
            return;
        }
        if (mTelephonyManager.getPhoneCount() == 2 && activeSubscriptionInfoCount != 2) {
            return;
        }

        List<SubscriptionInfo> subscriptionInfoList =
                ShellIdentityUtils.invokeMethodWithShellPermissions(mSubscriptionManager,
                        (tm) -> tm.getOpportunisticSubscriptions());
        List<String> mccMncs = new ArrayList<String>();
        List<Integer> bands = new ArrayList<Integer>();
        List<AvailableNetworkInfo> availableNetworkInfos = new ArrayList<AvailableNetworkInfo>();
        Consumer<Integer> callbackSuccess =
                TelephonyManagerTest::assertUpdateAvailableNetworkSuccess;
        Consumer<Integer> callbackNoOpSub =
                TelephonyManagerTest::assertUpdateAvailableNetworkNoOpportunisticSub;
        if (subscriptionInfoList == null || subscriptionInfoList.size() == 0
                || !mSubscriptionManager.isActiveSubscriptionId(
                subscriptionInfoList.get(0).getSubscriptionId())) {
            AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(randomSubId,
                    AvailableNetworkInfo.PRIORITY_HIGH, mccMncs, bands);
            availableNetworkInfos.add(availableNetworkInfo);
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.updateAvailableNetworks(availableNetworkInfos,
                            AsyncTask.SERIAL_EXECUTOR, callbackNoOpSub));
            // wait for the data change to take effect
            waitForMs(500);
            // clear all the operations at the end of test.
            availableNetworkInfos.clear();
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.updateAvailableNetworks(availableNetworkInfos,
                            AsyncTask.SERIAL_EXECUTOR, callbackNoOpSub));
        } else {
            AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(
                    subscriptionInfoList.get(0).getSubscriptionId(),
                    AvailableNetworkInfo.PRIORITY_HIGH, mccMncs, bands);
            availableNetworkInfos.add(availableNetworkInfo);
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.updateAvailableNetworks(availableNetworkInfos,
                            AsyncTask.SERIAL_EXECUTOR, callbackSuccess));
            // wait for the data change to take effect
            waitForMs(500);
            // clear all the operations at the end of test.
            availableNetworkInfos.clear();
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.updateAvailableNetworks(availableNetworkInfos,
                            AsyncTask.SERIAL_EXECUTOR, callbackSuccess));
        }
    }

    @Test
    public void testSwitchMultiSimConfig() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        try {
            mTelephonyManager.switchMultiSimConfig(mTelephonyManager.getActiveModemCount());
            fail("TelephonyManager#switchMultiSimConfig should require the MODIFY_PHONE_STATE"
                    + " permission to access.");
        } catch (SecurityException e) {
            // expected
        }
        try {
            // This should result in no-op.
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.switchMultiSimConfig(mTelephonyManager.getActiveModemCount()),
                    SecurityException.class, "android.permission.MODIFY_PHONE_STATE");
        } catch (SecurityException e) {
            fail("TelephonyManager#switchMultiSimConfig should require MODIFY_PHONE_STATE"
                    + "permission to access.");
        }
    }

    @Test
    public void testIccOpenLogicalChannelBySlot() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        // just verify no crash
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.iccOpenLogicalChannelBySlot(0, null, 0));
        } catch (IllegalArgumentException e) {
            // IllegalArgumentException is okay, just not SecurityException
        }
    }

    @Test
    public void testIccOpenLogicalChannelBySlotAndPort() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }
        // just verify no crash
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.iccOpenLogicalChannelByPort(0, 0, null, 0));
        } catch (SecurityException e) {
            // IllegalArgumentException is okay, just not SecurityException
            fail("iccCloseLogicalChannelByPort: SecurityException not expected");
        }
    }

    @Test
    public void testIccCloseLogicalChannelBySlot() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        // just verify no crash
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.iccCloseLogicalChannelBySlot(0, 0));
        } catch (IllegalArgumentException e) {
            // IllegalArgumentException is okay, just not SecurityException
        }
    }
    @Test
    public void testIccCloseLogicalChannelBySlotAndPort() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }
        int slotIndex = getValidSlotIndexAndPort().getKey();
        int portIndex = getValidSlotIndexAndPort().getValue();
        // just verify no crash
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager, (tm) -> tm.iccCloseLogicalChannelByPort(
                            slotIndex, portIndex, 0));
        } catch (IllegalArgumentException | IllegalStateException e) {
            // IllegalArgumentException and IllegalStateException is okay, just not
            // SecurityException
        } catch (SecurityException e) {
            // IllegalArgumentException is okay, just not SecurityException
            fail("iccCloseLogicalChannelByPort: SecurityException not expected");
        }
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager, (tm) -> tm.iccCloseLogicalChannelByPort(slotIndex, -1, 0));
            fail("Expected IllegalArgumentException, invalid PortIndex");
        } catch (IllegalArgumentException e) {
            // IllegalArgumentException is expected
        }
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager, (tm) -> tm.iccCloseLogicalChannelByPort(
                            slotIndex, portIndex, -1));
            fail("Expected IllegalArgumentException, invalid channel");
        } catch (IllegalArgumentException e) {
            // IllegalArgumentException is expected
        }
    }

    @Test
    public void testIccTransmitApduLogicalChannelBySlot() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        int slotIndex = getValidSlotIndexAndPort().getKey();
        String result = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.iccTransmitApduLogicalChannelBySlot(
                        slotIndex,
                        0 /* channel */,
                        0 /* cla */,
                        0 /* instruction */,
                        0 /* p1 */,
                        0 /* p2 */,
                        0 /* p3 */,
                        null /* data */));
        assertTrue(TextUtils.isEmpty(result));
    }

    @Test
    public void testIccTransmitApduLogicalChannelBySlotAndPort() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }
        int slotIndex = getValidSlotIndexAndPort().getKey();
        int portIndex = getValidSlotIndexAndPort().getValue();
        try {
            String result = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.iccTransmitApduLogicalChannelByPort(
                            slotIndex,
                            portIndex /* portIndex */,
                            0 /* channel */,
                            0 /* cla */,
                            0 /* instruction */,
                            0 /* p1 */,
                            0 /* p2 */,
                            0 /* p3 */,
                            null /* data */));
            assertTrue(TextUtils.isEmpty(result));
        } catch (SecurityException e) {
            // IllegalArgumentException is okay, just not SecurityException
            fail("iccTransmitApduLogicalChannelByPort: SecurityException not expected");
        }
    }
    @Test
    public void testIccTransmitApduBasicChannelBySlot() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        // just verify no crash
        int slotIndex = getValidSlotIndexAndPort().getKey();
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.iccTransmitApduBasicChannelBySlot(
                            slotIndex,
                            0 /* cla */,
                            0 /* instruction */,
                            0 /* p1 */,
                            0 /* p2 */,
                            0 /* p3 */,
                            null /* data */));
        } catch (IllegalArgumentException e ) {
            // IllegalArgumentException is okay, just not SecurityException
        }
    }

    @Test
    public void testIccTransmitApduBasicChannelBySlotAndPort() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }
        // just verify no crash
        int slotIndex = getValidSlotIndexAndPort().getKey();
        int portIndex = getValidSlotIndexAndPort().getValue();
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.iccTransmitApduBasicChannelByPort(
                            slotIndex,
                            portIndex /*portIndex */,
                            0 /* cla */,
                            0 /* instruction */,
                            0 /* p1 */,
                            0 /* p2 */,
                            0 /* p3 */,
                            null /* data */));
        } catch (SecurityException e) {
            // IllegalArgumentException is okay, just not SecurityException
            fail("iccTransmitApduBasicChannelByPort: SecurityException not expected");
        }
    }

    @Test
    public void testIsIccLockEnabled() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        // verify SecurityException
        try {
            mTelephonyManager.isIccLockEnabled();
            fail("testIsIccLockEnabled: Expected SecurityException on isIccLockEnabled");
        } catch (SecurityException se) {
            // expected
        }

        // test with permission
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.isIccLockEnabled());
        } catch (SecurityException se) {
            fail("testIsIccLockEnabled: SecurityException not expected");
        }
    }

    @Test
    public void testIsDataEnabledForApn() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_DATA));

        // verify SecurityException
        try {
            mTelephonyManager.isDataEnabledForApn(ApnSetting.TYPE_MMS);
            fail("testIsDataEnabledForApn: Expected SecurityException on isDataEnabledForApn");
        } catch (SecurityException se) {
            // expected
        }

        // test with permission
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.isDataEnabledForApn(ApnSetting.TYPE_MMS));
        } catch (SecurityException se) {
            fail("testIsDataEnabledForApn: SecurityException not expected");
        }
    }

    @Test
    public void testIsTetheringApnRequired() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_DATA));

        // verify SecurityException
        try {
            mTelephonyManager.isTetheringApnRequired();
            fail("testIsTetheringApnRequired: Expected SecurityException on "
                    + "isTetheringApnRequired");
        } catch (SecurityException se) {
            // expected
        }

        // test with permission
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.isTetheringApnRequired());
        } catch (SecurityException se) {
            fail("testIsIccLockEnabled: SecurityException not expected");
        }
    }

    @Test
    public void testGetCarrierInfoForImsiEncryption() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        // test without permission: verify SecurityException
        try {
            mTelephonyManager.getCarrierInfoForImsiEncryption(TelephonyManager.KEY_TYPE_EPDG);
            fail("testGetCarrierInfoForImsiEncryption: "
                    + "SecurityException expected on getCarrierInfoForImsiEncryption");
        } catch (SecurityException se) {
            // expected
        }
        try {
            mTelephonyManager.getCarrierInfoForImsiEncryption(TelephonyManager.KEY_TYPE_WLAN);
            fail("testGetCarrierInfoForImsiEncryption: "
                    + "SecurityException expected on getCarrierInfoForImsiEncryption");
        } catch (SecurityException se) {
            // expected
        }
        // test with permission
        PublicKey epdgKey = null;
        PublicKey wlanKey = null;
        try {
            PersistableBundle carrierConfig = mCarrierConfigManager.getConfigForSubId(mTestSub);

            assertNotNull("CarrierConfigManager#getConfigForSubId() returned null",
                    carrierConfig);
            assertFalse("CarrierConfigManager#getConfigForSubId() returned empty bundle",
                    carrierConfig.isEmpty());

            // purge the certs in carrierConfigs first
            carrierConfig.putInt(
                    CarrierConfigManager.IMSI_KEY_AVAILABILITY_INT, 3);
            carrierConfig.putString(
                    CarrierConfigManager.IMSI_KEY_DOWNLOAD_URL_STRING, BAD_IMSI_CERT_URL);
            carrierConfig.putString(
                    CarrierConfigManager.IMSI_CARRIER_PUBLIC_KEY_EPDG_STRING,
                    IMSI_CERT_STRING_EPDG);
            carrierConfig.putString(
                    CarrierConfigManager.IMSI_CARRIER_PUBLIC_KEY_WLAN_STRING,
                    IMSI_CERT_STRING_WLAN);
            overrideCarrierConfig(carrierConfig);
        } catch (Exception e) {
            fail("Could not override carrier config. e=" + e.toString());
        }

        try {
            // It appears that the two certs actually have the same public key. Ideally we would
            // want these to be different for testing, but it's challenging to create a valid
            // certificate string for testing and these are the only two examples available
            InputStream inStream = new ByteArrayInputStream(IMSI_CERT_STRING_WLAN.getBytes());
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(inStream);
            wlanKey = cert.getPublicKey();

            inStream = new ByteArrayInputStream(IMSI_CERT_STRING_EPDG.getBytes());
            cert = (X509Certificate) cf.generateCertificate(inStream);
            epdgKey = cert.getPublicKey();
        } catch (CertificateException e) {
            fail("Could not create certs. e=" + e.toString());
        }

        try {
            ImsiEncryptionInfo info = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager,
                    (tm) -> {
                        return tm.getCarrierInfoForImsiEncryption(TelephonyManager.KEY_TYPE_EPDG);
                    });
            assertNotNull("Encryption info returned null", info);
            assertEquals(epdgKey, info.getPublicKey());
            assertEquals(TelephonyManager.KEY_TYPE_EPDG, info.getKeyType());
        } catch (SecurityException se) {
            fail("testGetCarrierInfoForImsiEncryption: SecurityException not expected");
        } catch (IllegalArgumentException iae) {
            // IllegalArgumentException is okay, just not SecurityException
        }
        try {
            ImsiEncryptionInfo info = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager,
                    (tm) -> {
                        return tm.getCarrierInfoForImsiEncryption(TelephonyManager.KEY_TYPE_WLAN);
                    });
            assertNotNull("Encryption info returned null", info);
            assertEquals(wlanKey, info.getPublicKey());
            assertEquals(TelephonyManager.KEY_TYPE_WLAN, info.getKeyType());
        } catch (SecurityException se) {
            fail("testGetCarrierInfoForImsiEncryption: SecurityException not expected");
        } catch (IllegalArgumentException iae) {
            // IllegalArgumentException is okay, just not SecurityException
        }
    }

    @Test
    public void testResetCarrierKeysForImsiEncryption() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        // test without permission: verify SecurityException
        try {
            mTelephonyManager.resetCarrierKeysForImsiEncryption();
            fail("testResetCarrierKeysForImsiEncryption: SecurityException expected");
        } catch (SecurityException se) {
            // expected
        }
        // test with permission
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager,
                    (tm) -> tm.resetCarrierKeysForImsiEncryption());
        } catch (SecurityException se) {
            fail("testResetCarrierKeysForImsiEncryption: SecurityException not expected");
        }
    }

    @Test
    public void testIsInEmergencySmsMode() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING));

        // test without permission: verify SecurityException
        try {
            mTelephonyManager.isInEmergencySmsMode();
            fail("testIsInEmergencySmsMode: SecurityException expected");
        } catch (SecurityException se) {
            // expected
        }
        // test with permission
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager,
                    (tm) -> tm.isInEmergencySmsMode());
        } catch (SecurityException se) {
            fail("testIsInEmergencySmsMode: SecurityException not expected");
        }
    }

    @Test
    public void testGetSubscriptionId() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        TelephonyManager tm = mTelephonyManager.createForSubscriptionId(1);
        int subId = tm.getSubscriptionId();
        assertEquals(1, subId);
    }

    @Test
    public void testSetAllowedNetworkTypes() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // test without permission: verify SecurityException
        long allowedNetworkTypes = TelephonyManager.NETWORK_TYPE_BITMASK_NR;
        try {
            mTelephonyManager.setAllowedNetworkTypes(allowedNetworkTypes);
            fail("testSetAllowedNetworkTypes: SecurityException expected");
        } catch (SecurityException se) {
            // expected
        }

        // test with permission
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager,
                    (tm) -> tm.setAllowedNetworkTypes(allowedNetworkTypes));

            long deviceAllowedNetworkTypes = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> {
                        return tm.getAllowedNetworkTypes();
                    }
            );
            assertEquals(allowedNetworkTypes, deviceAllowedNetworkTypes);
        } catch (SecurityException se) {
            fail("testSetAllowedNetworkTypes: SecurityException not expected");
        }
    }

    @Test
    public void testDisAllowedNetworkTypes() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        long allowedNetworkTypes = ~TelephonyManager.NETWORK_TYPE_BITMASK_NR;
        long networkTypeBitmask = TelephonyManager.NETWORK_TYPE_BITMASK_NR
                | TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                | TelephonyManager.NETWORK_TYPE_BITMASK_LTE_CA;

        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager,
                    (tm) -> tm.setAllowedNetworkTypesForReason(
                            TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_CARRIER,
                            allowedNetworkTypes));

            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager,
                    (tm) -> tm.setAllowedNetworkTypesForReason(
                            TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER,
                            networkTypeBitmask));

            long modemNetworkTypeBitmask = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> {
                        return tm.getAllowedNetworkTypesBitmask();
                    }
            );
            long radioAccessFamily = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> {
                        return tm.getSupportedRadioAccessFamily();
                    }
            );

            // RadioAccessFamily won't include all bits of RAFs group, so transfer to preferred
            // network type instead of using bitmask directly
            int modemPreferredNetworkType = RadioAccessFamily.getNetworkTypeFromRaf(
                    (int) modemNetworkTypeBitmask);
            int preferredNetworkType = RadioAccessFamily.getNetworkTypeFromRaf(
                    (int) (networkTypeBitmask & allowedNetworkTypes & radioAccessFamily));
            assertEquals(preferredNetworkType, modemPreferredNetworkType);
        } catch (SecurityException se) {
            fail("testDisAllowedNetworkTypes: SecurityException not expected");
        }
    }

    @Test
    public void testSetAllowedNetworkTypesForReason() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // test without permission: verify SecurityException
        long allowedNetworkTypes = TelephonyManager.NETWORK_TYPE_BITMASK_NR;
        try {
            mIsAllowedNetworkTypeChanged = true;
            mTelephonyManager.setAllowedNetworkTypesForReason(
                    TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_POWER, allowedNetworkTypes);
            fail("testSetAllowedNetworkTypesForReason: SecurityException expected");
        } catch (SecurityException se) {
            // expected
        }

        // test with permission
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager,
                    (tm) -> tm.setAllowedNetworkTypesForReason(
                            TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_POWER,
                            allowedNetworkTypes));

            long deviceAllowedNetworkTypes = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> {
                        return tm.getAllowedNetworkTypesForReason(
                                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_POWER);
                    }
            );
            assertEquals(allowedNetworkTypes, deviceAllowedNetworkTypes);
        } catch (SecurityException se) {
            fail("testSetAllowedNetworkTypes: SecurityException not expected");
        }
    }

    @Test
    public void testSetAllowedNetworkTypesForReason_moreReason() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // test without permission: verify SecurityException
        long allowedNetworkTypes1 = TelephonyManager.NETWORK_TYPE_BITMASK_NR
                | TelephonyManager.NETWORK_TYPE_BITMASK_UMTS;
        long allowedNetworkTypes2 = TelephonyManager.NETWORK_TYPE_BITMASK_LTE;
        long allowedNetworkTypes3 = TelephonyManager.NETWORK_TYPE_BITMASK_NR
                | TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                | TelephonyManager.NETWORK_TYPE_BITMASK_UMTS;
        long allowedNetworkTypes4 = TelephonyManager.NETWORK_TYPE_LTE
                | TelephonyManager.NETWORK_TYPE_EVDO_B;

        try {
            mIsAllowedNetworkTypeChanged = true;
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager,
                    (tm) -> tm.setAllowedNetworkTypesForReason(
                            TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_POWER,
                            allowedNetworkTypes1));

            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager,
                    (tm) -> tm.setAllowedNetworkTypesForReason(
                            TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER,
                            allowedNetworkTypes2));
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager,
                    (tm) -> tm.setAllowedNetworkTypesForReason(
                            TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_CARRIER,
                            allowedNetworkTypes3));
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager,
                    (tm) -> tm.setAllowedNetworkTypesForReason(
                            TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_ENABLE_2G,
                            allowedNetworkTypes4));
            long deviceAllowedNetworkTypes1 = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> {
                        return tm.getAllowedNetworkTypesForReason(
                                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_POWER);
                    }
            );
            long deviceAllowedNetworkTypes2 = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> {
                        return tm.getAllowedNetworkTypesForReason(
                                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);
                    }
            );
            long deviceAllowedNetworkTypes3 = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> {
                        return tm.getAllowedNetworkTypesForReason(
                                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_CARRIER);
                    }
            );
            long deviceAllowedNetworkTypes4 = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> {
                        return tm.getAllowedNetworkTypesForReason(
                                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_ENABLE_2G);
                    }
            );
            assertEquals(allowedNetworkTypes1, deviceAllowedNetworkTypes1);
            assertEquals(allowedNetworkTypes2, deviceAllowedNetworkTypes2);
            assertEquals(allowedNetworkTypes3, deviceAllowedNetworkTypes3);
            assertEquals(allowedNetworkTypes4, deviceAllowedNetworkTypes4);
        } catch (SecurityException se) {
            fail("testSetAllowedNetworkTypes: SecurityException not expected");
        }
    }

    @Test
    public void testIsApplicationOnUicc() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        // Expect a security exception without permission.
        try {
            mTelephonyManager.isApplicationOnUicc(TelephonyManager.APPTYPE_SIM);
            fail("Expected security exception");
        } catch (SecurityException se1) {
            // Expected
        }

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.READ_PRIVILEGED_PHONE_STATE");
        try {
            mTelephonyManager.isApplicationOnUicc(TelephonyManager.APPTYPE_SIM);
        } catch (SecurityException se) {
            fail("Caller with READ_PRIVILEGED_PHONE_STATE should be able to call API");
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    public void testRequestModemActivityInfo() throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.MODIFY_PHONE_STATE");
        try {
            // Get one instance of activity info and make sure it's valid
            CompletableFuture<ModemActivityInfo> future1 = new CompletableFuture<>();
            mTelephonyManager.requestModemActivityInfo(getContext().getMainExecutor(),
                    future1::complete);
            ModemActivityInfo activityInfo1 = future1.get(TOLERANCE, TimeUnit.MILLISECONDS);
            assertNotNull(activityInfo1);
            assertTrue("first activity info is" + activityInfo1, activityInfo1.isValid());

            // Wait a bit, then get another instance to make sure that some info has accumulated
            CompletableFuture<ModemActivityInfo> future2 = new CompletableFuture<>();
            mTelephonyManager.requestModemActivityInfo(getContext().getMainExecutor(),
                    future2::complete);
            ModemActivityInfo activityInfo2 = future2.get(TOLERANCE, TimeUnit.MILLISECONDS);
            assertNotNull(activityInfo2);
            assertTrue("second activity info is" + activityInfo2, activityInfo2.isValid());

            ModemActivityInfo diff = activityInfo1.getDelta(activityInfo2);
            assertNotNull(diff);
            assertTrue("two activityInfo are identical", !activityInfo1.equals(activityInfo2));
            assertTrue("diff is" + diff, diff.isValid() || diff.isEmpty());
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    public void testModemActivityInfoException() {
        TelephonyManager.ModemActivityInfoException exception =
                new TelephonyManager.ModemActivityInfoException(
                        TelephonyManager.ModemActivityInfoException.ERROR_PHONE_NOT_AVAILABLE);
        assertEquals(TelephonyManager.ModemActivityInfoException.ERROR_PHONE_NOT_AVAILABLE,
                exception.getErrorCode());
    }

    @Test
    public void testGetSupportedModemCount() {
        int supportedModemCount = mTelephonyManager.getSupportedModemCount();
        int activeModemCount = mTelephonyManager.getActiveModemCount();
        assertTrue(activeModemCount >= 0);
        assertTrue(supportedModemCount >= activeModemCount);
    }

    @Test
    public void testGetAllNetworkTypes() {
        Set<Integer> expectedNetworkTypes = new HashSet<>(Arrays.asList(
                TelephonyManager.NETWORK_TYPE_GPRS,
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_UMTS,
                TelephonyManager.NETWORK_TYPE_CDMA,
                TelephonyManager.NETWORK_TYPE_EVDO_0,
                TelephonyManager.NETWORK_TYPE_EVDO_A,
                TelephonyManager.NETWORK_TYPE_1xRTT,
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_IDEN,
                TelephonyManager.NETWORK_TYPE_EVDO_B,
                TelephonyManager.NETWORK_TYPE_LTE,
                TelephonyManager.NETWORK_TYPE_EHRPD,
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_GSM,
                TelephonyManager.NETWORK_TYPE_TD_SCDMA,
                TelephonyManager.NETWORK_TYPE_IWLAN,
                TelephonyManager.NETWORK_TYPE_LTE_CA,
                TelephonyManager.NETWORK_TYPE_NR
        ));

        Set<Integer> actualNetworkTypes = IntStream.of(TelephonyManager.getAllNetworkTypes())
                .boxed().collect(Collectors.toSet());
        assertTrue(expectedNetworkTypes.containsAll(actualNetworkTypes));
        assertTrue(actualNetworkTypes.containsAll(expectedNetworkTypes));
    }

    @Test
    public void testIsModemEnabledForSlot() {
        int activeModemCount = mTelephonyManager.getActiveModemCount();
        for (int i = 0; i < activeModemCount; i++) {
            // Call isModemEnabledForSlot for each slot and verify no crash.
            mTelephonyManager.isModemEnabledForSlot(i);
        }
    }

    @Test
    public void testOpportunisticNetworkState() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS)
                && !mPackageManager.hasSystemFeature(PackageManager.FEATURE_WATCH));

        boolean isEnabled = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                tm -> tm.isOpportunisticNetworkEnabled());
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                tm -> tm.setOpportunisticNetworkState(true));
        assertTrue(ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                tm -> tm.isOpportunisticNetworkEnabled()));
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                tm -> tm.setOpportunisticNetworkState(false));
        assertFalse(ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                tm -> tm.isOpportunisticNetworkEnabled()));
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                tm -> tm.setOpportunisticNetworkState(isEnabled));
    }

    @Test
    public void testGetSimApplicationState() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        int simApplicationState = mTelephonyManager.getSimApplicationState();
        assertTrue(Arrays.asList(TelephonyManager.SIM_STATE_UNKNOWN,
                TelephonyManager.SIM_STATE_PIN_REQUIRED,
                TelephonyManager.SIM_STATE_PUK_REQUIRED,
                TelephonyManager.SIM_STATE_NETWORK_LOCKED,
                TelephonyManager.SIM_STATE_NOT_READY,
                TelephonyManager.SIM_STATE_PERM_DISABLED,
                TelephonyManager.SIM_STATE_LOADED).contains(simApplicationState));

        for (int i = 0; i <= mTelephonyManager.getPhoneCount(); i++) {
            final int slotId = i;
            simApplicationState = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.getSimApplicationState(slotId));
            assertTrue(Arrays.asList(TelephonyManager.SIM_STATE_UNKNOWN,
                    TelephonyManager.SIM_STATE_PIN_REQUIRED,
                    TelephonyManager.SIM_STATE_PUK_REQUIRED,
                    TelephonyManager.SIM_STATE_NETWORK_LOCKED,
                    TelephonyManager.SIM_STATE_NOT_READY,
                    TelephonyManager.SIM_STATE_PERM_DISABLED,
                    TelephonyManager.SIM_STATE_LOADED).contains(simApplicationState));
        }
    }

    @Test
    public void testGetSimApplicationStateWithPhysicalSlotIndexAndPortIndex() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        try {
            List<UiccCardInfo> cardInfoList =
                    ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                            (tm) -> tm.getUiccCardsInfo());
            for (UiccCardInfo cardInfo : cardInfoList) {
                int physicalSlotIndex = cardInfo.getPhysicalSlotIndex();
                List<UiccPortInfo> portInfoList = (List<UiccPortInfo>) cardInfo.getPorts();
                for (UiccPortInfo uiccPortInfo : portInfoList) {
                    int portIndex = uiccPortInfo.getPortIndex();
                    int simApplicationState =
                            ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                                    (tm) -> tm.getSimApplicationState(physicalSlotIndex,
                                            portIndex));
                    assertTrue(Arrays.asList(TelephonyManager.SIM_STATE_UNKNOWN,
                            TelephonyManager.SIM_STATE_PIN_REQUIRED,
                            TelephonyManager.SIM_STATE_PUK_REQUIRED,
                            TelephonyManager.SIM_STATE_NETWORK_LOCKED,
                            TelephonyManager.SIM_STATE_NOT_READY,
                            TelephonyManager.SIM_STATE_PERM_DISABLED,
                            TelephonyManager.SIM_STATE_LOADED).contains(simApplicationState));
                }
            }
        } catch (SecurityException e) {
            fail("Caller with READ_PRIVILEGED_PHONE_STATE should be able to call API");
        }
    }

    @Test
    public void testGetSimCardState() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        int simCardState = mTelephonyManager.getSimCardState();
        assertTrue(Arrays.asList(TelephonyManager.SIM_STATE_UNKNOWN,
                TelephonyManager.SIM_STATE_ABSENT,
                TelephonyManager.SIM_STATE_CARD_IO_ERROR,
                TelephonyManager.SIM_STATE_CARD_RESTRICTED,
                TelephonyManager.SIM_STATE_PRESENT).contains(simCardState));
    }
    @Test
    public void getSimCardStateTest() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.READ_PRIVILEGED_PHONE_STATE");
        List<UiccCardInfo> cardsInfo = mTelephonyManager.getUiccCardsInfo();
        for (UiccCardInfo cardInfo : cardsInfo) {
            for (UiccPortInfo portInfo : cardInfo.getPorts()) {
                int simCardState = mTelephonyManager.getSimCardState(cardInfo
                        .getPhysicalSlotIndex(), portInfo.getPortIndex());
                assertTrue(Arrays.asList(TelephonyManager.SIM_STATE_UNKNOWN,
                        TelephonyManager.SIM_STATE_ABSENT,
                        TelephonyManager.SIM_STATE_CARD_IO_ERROR,
                        TelephonyManager.SIM_STATE_CARD_RESTRICTED,
                        TelephonyManager.SIM_STATE_PRESENT).contains(simCardState));
            }
        }
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    private boolean isDataEnabled() {
        return ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                TelephonyManager::isDataEnabled);
    }

    @Test
    public void testThermalDataEnable() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_DATA));

        // Perform this test on default data subscription.
        mTelephonyManager = getContext().getSystemService(TelephonyManager.class)
                .createForSubscriptionId(SubscriptionManager.getDefaultDataSubscriptionId());
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager,
                (tm) -> tm.setDataEnabledForReason(TelephonyManager.DATA_ENABLED_REASON_THERMAL,
                        false));

        waitForMs(1000);
        boolean isDataEnabledForReason = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.isDataEnabledForReason(
                        TelephonyManager.DATA_ENABLED_REASON_THERMAL));
        assertFalse(isDataEnabledForReason);

        boolean isDataConnectionAvailable = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::isDataConnectionAllowed);
        assertFalse(isDataConnectionAvailable);

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager,
                (tm) -> tm.setDataEnabledForReason(TelephonyManager.DATA_ENABLED_REASON_THERMAL,
                        true));

        waitForMs(1000);
        isDataEnabledForReason = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.isDataEnabledForReason(
                        TelephonyManager.DATA_ENABLED_REASON_THERMAL));
        assertTrue(isDataEnabledForReason);

        isDataConnectionAvailable = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::isDataConnectionAllowed);
        assertTrue(isDataConnectionAvailable);
    }

    @Test
    public void testPolicyDataEnable() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_DATA));

        // Perform this test on default data subscription.
        mTelephonyManager = getContext().getSystemService(TelephonyManager.class)
                .createForSubscriptionId(SubscriptionManager.getDefaultDataSubscriptionId());

        int retry = 0;
        boolean isDataEnabledForReason = true;
        boolean isDataConnectionAvailable = true;
        // NPMS will set policy data to true after tests set it to false,
        // so retry disabling policy data to prevent flaky test failures.
        // TODO: Set empty policies once we can suppress default policies.
        while ((isDataEnabledForReason || isDataConnectionAvailable) && retry < 30) {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager,
                    (tm) -> tm.setDataEnabledForReason(TelephonyManager.DATA_ENABLED_REASON_POLICY,
                            false));
            isDataEnabledForReason = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.isDataEnabledForReason(
                            TelephonyManager.DATA_ENABLED_REASON_POLICY));
            isDataConnectionAvailable = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, TelephonyManager::isDataConnectionAllowed);
            retry++;
            waitForMs(500);
        }
        assertFalse(isDataEnabledForReason);
        assertFalse(isDataConnectionAvailable);

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager,
                (tm) -> tm.setDataEnabledForReason(TelephonyManager.DATA_ENABLED_REASON_POLICY,
                        true));

        waitForMs(1000);
        isDataEnabledForReason = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.isDataEnabledForReason(
                        TelephonyManager.DATA_ENABLED_REASON_POLICY));
        assertTrue(isDataEnabledForReason);

        isDataConnectionAvailable = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::isDataConnectionAllowed);
        assertTrue(isDataConnectionAvailable);
    }

    @Test
    public void testCarrierDataEnable() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_DATA));

        // Perform this test on default data subscription.
        mTelephonyManager = getContext().getSystemService(TelephonyManager.class)
                .createForSubscriptionId(SubscriptionManager.getDefaultDataSubscriptionId());
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager,
                (tm) -> tm.setDataEnabledForReason(TelephonyManager.DATA_ENABLED_REASON_CARRIER,
                        false));

        waitForMs(1000);
        boolean isDataEnabledForReason = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.isDataEnabledForReason(
                        TelephonyManager.DATA_ENABLED_REASON_CARRIER));
        assertFalse(isDataEnabledForReason);

        boolean isDataConnectionAvailable = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::isDataConnectionAllowed);
        assertFalse(isDataConnectionAvailable);

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager,
                (tm) -> tm.setDataEnabledForReason(TelephonyManager.DATA_ENABLED_REASON_CARRIER,
                        true));

        waitForMs(1000);
        isDataEnabledForReason = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.isDataEnabledForReason(
                        TelephonyManager.DATA_ENABLED_REASON_CARRIER));
        assertTrue(isDataEnabledForReason);
        isDataConnectionAvailable = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::isDataConnectionAllowed);
        assertTrue(isDataConnectionAvailable);
    }

    @Test
    public void testUserDataEnable() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_DATA));

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager,
                (tm) -> tm.setDataEnabledForReason(TelephonyManager.DATA_ENABLED_REASON_USER,
                        false));

        waitForMs(1000);
        boolean isDataEnabledForReason = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.isDataEnabledForReason(
                        TelephonyManager.DATA_ENABLED_REASON_USER));
        assertFalse(isDataEnabledForReason);

        boolean isDataConnectionAvailable = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::isDataConnectionAllowed);
        assertFalse(isDataConnectionAvailable);

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager,
                (tm) -> tm.setDataEnabledForReason(TelephonyManager.DATA_ENABLED_REASON_USER,
                        true));

        waitForMs(1000);
        isDataEnabledForReason = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.isDataEnabledForReason(
                        TelephonyManager.DATA_ENABLED_REASON_USER));
        assertTrue(isDataEnabledForReason);
        isDataConnectionAvailable = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::isDataConnectionAllowed);
        assertTrue(isDataConnectionAvailable);
    }

    @Test
    public void testDataDuringVoiceCallPolicy() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_DATA));

        ShellIdentityUtils.ShellPermissionMethodHelper<Boolean, TelephonyManager> getPolicyHelper =
                (tm) -> tm.isMobileDataPolicyEnabled(
                        TelephonyManager.MOBILE_DATA_POLICY_DATA_ON_NON_DEFAULT_DURING_VOICE_CALL);

        boolean allowDataDuringVoiceCall = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, getPolicyHelper);

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager, (tm) -> tm.setMobileDataPolicyEnabled(
                        TelephonyManager.MOBILE_DATA_POLICY_DATA_ON_NON_DEFAULT_DURING_VOICE_CALL,
                        !allowDataDuringVoiceCall));

        waitForMs(500);
        assertNotEquals(allowDataDuringVoiceCall,
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        mTelephonyManager, getPolicyHelper));

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager, (tm) -> tm.setMobileDataPolicyEnabled(
                        TelephonyManager.MOBILE_DATA_POLICY_DATA_ON_NON_DEFAULT_DURING_VOICE_CALL,
                        allowDataDuringVoiceCall));

        waitForMs(500);
        assertEquals(allowDataDuringVoiceCall,
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        mTelephonyManager, getPolicyHelper));
    }

    @Test
    public void testAlwaysAllowMmsDataPolicy() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_DATA));

        ShellIdentityUtils.ShellPermissionMethodHelper<Boolean, TelephonyManager> getPolicyHelper =
                (tm) -> tm.isMobileDataPolicyEnabled(
                        TelephonyManager.MOBILE_DATA_POLICY_MMS_ALWAYS_ALLOWED);

        boolean mmsAlwaysAllowed = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, getPolicyHelper);

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager, (tm) -> tm.setMobileDataPolicyEnabled(
                        TelephonyManager.MOBILE_DATA_POLICY_MMS_ALWAYS_ALLOWED,
                        !mmsAlwaysAllowed));

        waitForMs(500);
        assertNotEquals(mmsAlwaysAllowed,
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        mTelephonyManager, getPolicyHelper));

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager, (tm) -> tm.setMobileDataPolicyEnabled(
                        TelephonyManager.MOBILE_DATA_POLICY_MMS_ALWAYS_ALLOWED,
                        mmsAlwaysAllowed));

        waitForMs(500);
        assertEquals(mmsAlwaysAllowed,
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        mTelephonyManager, getPolicyHelper));
    }

    @Test
    public void testGetCdmaEnhancedRoamingIndicatorDisplayNumber() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CDMA));

        int index = mTelephonyManager.getCdmaEnhancedRoamingIndicatorDisplayNumber();
        int phoneType = mTelephonyManager.getPhoneType();
        if (phoneType == TelephonyManager.PHONE_TYPE_CDMA) {
            assertTrue(index >= 0 && index <= 255);
        } else {
            assertEquals(-1, index);
        }
    }

    private int disableNrDualConnectivity() {
        if (!ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.isRadioInterfaceCapabilitySupported(
                        TelephonyManager
                                .CAPABILITY_NR_DUAL_CONNECTIVITY_CONFIGURATION_AVAILABLE))) {
            return TelephonyManager.ENABLE_NR_DUAL_CONNECTIVITY_NOT_SUPPORTED;
        }

        int result = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager,
                (tm) -> tm.setNrDualConnectivityState(
                        TelephonyManager.NR_DUAL_CONNECTIVITY_DISABLE));

        boolean isNrDualConnectivityEnabled =
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        mTelephonyManager, (tm) -> tm.isNrDualConnectivityEnabled());
        // Only verify the result for supported devices on IRadio 1.6+
        if (mRadioVersion >= RADIO_HAL_VERSION_1_6
                && result != TelephonyManager.ENABLE_NR_DUAL_CONNECTIVITY_NOT_SUPPORTED) {
            assertFalse(isNrDualConnectivityEnabled);
        }

        return result;
    }

    @Test
    public void testNrDualConnectivityEnable() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        if (!ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.isRadioInterfaceCapabilitySupported(
                        TelephonyManager
                                .CAPABILITY_NR_DUAL_CONNECTIVITY_CONFIGURATION_AVAILABLE))) {
            return;
        }

        boolean isInitiallyEnabled = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.isNrDualConnectivityEnabled());
        boolean isNrDualConnectivityEnabled;
        int result;
        if (isInitiallyEnabled) {
            result = disableNrDualConnectivity();
            if (result == TelephonyManager.ENABLE_NR_DUAL_CONNECTIVITY_NOT_SUPPORTED) {
                return;
            }
        }


        result = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager,
                (tm) -> tm.setNrDualConnectivityState(
                        TelephonyManager.NR_DUAL_CONNECTIVITY_ENABLE));

        if (result == TelephonyManager.ENABLE_NR_DUAL_CONNECTIVITY_NOT_SUPPORTED) {
            return;
        }

        isNrDualConnectivityEnabled = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.isNrDualConnectivityEnabled());
        // Only verify the result for supported devices on IRadio 1.6+
        if (mRadioVersion >= RADIO_HAL_VERSION_1_6) {
            assertTrue(isNrDualConnectivityEnabled);
        }

        if (!isInitiallyEnabled) {
            disableNrDualConnectivity();
        }
    }

    @Test
    public void testCdmaRoamingMode() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CDMA)
                && mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA);

        // Save state
        int cdmaRoamingMode = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                TelephonyManager::getCdmaRoamingMode);

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                tm -> tm.setCdmaRoamingMode(TelephonyManager.CDMA_ROAMING_MODE_HOME));
        assertEquals(TelephonyManager.CDMA_ROAMING_MODE_HOME,
                (int) ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                        TelephonyManager::getCdmaRoamingMode));
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                tm -> tm.setCdmaRoamingMode(TelephonyManager.CDMA_ROAMING_MODE_AFFILIATED));
        assertEquals(TelephonyManager.CDMA_ROAMING_MODE_AFFILIATED,
                (int) ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                        TelephonyManager::getCdmaRoamingMode));

        // Reset state
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                tm -> tm.setCdmaRoamingMode(cdmaRoamingMode));
    }

    @Test
    public void testCdmaSubscriptionMode() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CDMA)
                && mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA);

        // Save state
        int cdmaSubscriptionMode = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::getCdmaSubscriptionMode);

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                tm -> tm.setCdmaSubscriptionMode(TelephonyManager.CDMA_SUBSCRIPTION_NV));
        assertEquals(TelephonyManager.CDMA_SUBSCRIPTION_NV,
                (int) ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                        TelephonyManager::getCdmaSubscriptionMode));
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                tm -> tm.setCdmaSubscriptionMode(TelephonyManager.CDMA_SUBSCRIPTION_RUIM_SIM));
        assertEquals(TelephonyManager.CDMA_SUBSCRIPTION_RUIM_SIM,
                (int) ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                        TelephonyManager::getCdmaSubscriptionMode));

        // Reset state
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                tm -> tm.setCdmaSubscriptionMode(cdmaSubscriptionMode));
    }

    @Test
    public void testPinResult() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        final String empty_pin = ""; // For getting current remaining pin attempt.
        final String pin = "fake_pin";
        final String puk = "fake_puk";
        final String newPin = "fake_new_pin";

        //Refer GSM 02.17 5.6 PIN Management
        //To avoid that sim may enter PUK state,
        //TC should be allowed when current Pin attempt count is reset with 3.
        boolean isEnabled = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::isIccLockEnabled);
        PinResult result = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.supplyIccLockPin(empty_pin));
        if (result.getAttemptsRemaining() < 3) {
            Log.d(TAG, "Skipping test and requires that reboot device and unlock pin successfully");
            return;
        }

        result = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.setIccLockEnabled(!isEnabled, pin));
        assertTrue(result.getResult() == PinResult.PIN_RESULT_TYPE_INCORRECT
                || result.getResult() == PinResult.PIN_RESULT_TYPE_FAILURE);
        assertTrue(result.getAttemptsRemaining() >= -1);
        assertEquals(isEnabled, ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::isIccLockEnabled));

        result = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.changeIccLockPin(pin, newPin));
        assertTrue(result.getResult() == PinResult.PIN_RESULT_TYPE_INCORRECT
                || result.getResult() == PinResult.PIN_RESULT_TYPE_FAILURE);
        assertTrue(result.getAttemptsRemaining() >= -1);

        result = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.supplyIccLockPin(pin));
        assertTrue(result.getResult() == PinResult.PIN_RESULT_TYPE_INCORRECT
                || result.getResult() == PinResult.PIN_RESULT_TYPE_FAILURE);
        assertTrue(result.getAttemptsRemaining() >= -1);

        result = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.supplyIccLockPuk(puk, pin));
        assertTrue(result.getResult() == PinResult.PIN_RESULT_TYPE_INCORRECT
                || result.getResult() == PinResult.PIN_RESULT_TYPE_FAILURE);
        assertTrue(result.getAttemptsRemaining() >= -1);
    }

    @Test
    public void testSetSignalStrengthUpdateRequest_nullRequest() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // Verify NPE throws if set request with null object
        try {
            mTelephonyManager.setSignalStrengthUpdateRequest(null);
            fail("NullPointerException expected when setSignalStrengthUpdateRequest with null");
        } catch (NullPointerException expected) {
        }
    }

    @Test
    public void testSetSignalStrengthUpdateRequest_noPermission() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        final SignalStrengthUpdateRequest normalRequest =
                new SignalStrengthUpdateRequest.Builder()
                        .setSignalThresholdInfos(List.of(
                                new SignalThresholdInfo.Builder()
                                        .setRadioAccessNetworkType(
                                                AccessNetworkConstants.AccessNetworkType.GERAN)
                                        .setSignalMeasurementType(
                                                SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI)
                                        .setThresholds(new int[]{-113, -103, -97, -51})
                                        .build()))
                        .setReportingRequestedWhileIdle(true)
                        .build();

        // Verify SE throws for apps without carrier privilege or MODIFY_PHONE_STATE permission
        try {
            mTelephonyManager.setSignalStrengthUpdateRequest(normalRequest);
            fail("SecurityException expected when setSignalStrengthUpdateRequest without "
                    + "carrier privilege or MODIFY_PHONE_STATE permission");
        } catch (SecurityException expected) {
        }
    }

    @Test
    public void testSetSignalStrengthUpdateRequest_systemThresholdReportingRequestedWhileIdle() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // Verify system privileged app with permission LISTEN_ALWAYS_REPORTED_SIGNAL_STRENGTH can
        // set systemThresholdReportingRequestedWhileIdle to true with empty thresholdInfos
        SignalStrengthUpdateRequest request = new SignalStrengthUpdateRequest.Builder()
                .setSignalThresholdInfos(Collections.EMPTY_LIST)
                .setSystemThresholdReportingRequestedWhileIdle(true)
                .build();

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                (tm) -> tm.setSignalStrengthUpdateRequest(request));
    }

    @Test
    public void testSetSignalStrengthUpdateRequest_hysteresisDbSet() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // Verify SE throws for app when set hysteresisDb in the SignalThresholdInfo
        SignalStrengthUpdateRequest requestWithHysteresisDbSet =
                new SignalStrengthUpdateRequest.Builder()
                        .setSignalThresholdInfos(List.of(
                                new SignalThresholdInfo.Builder()
                                        .setRadioAccessNetworkType(
                                                AccessNetworkConstants.AccessNetworkType.GERAN)
                                        .setSignalMeasurementType(
                                                SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI)
                                        .setThresholds(new int[]{-113, -103, -97, -51})
                                        .setHysteresisDb(10) //allowed for system caller only
                                        .build()))
                        .setReportingRequestedWhileIdle(true)
                        .build();
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.setSignalStrengthUpdateRequest(requestWithHysteresisDbSet));
            fail("IllegalArgumentException expected when set hysteresisDb in SignalThresholdInfo "
                    + "to true");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testSetSignalStrengthUpdateRequest_hysteresisMsSet() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // Verify SE throws for app when set hysteresisMs in the SignalThresholdInfo
        SignalStrengthUpdateRequest requestWithHysteresisMsSet =
                new SignalStrengthUpdateRequest.Builder()
                        .setSignalThresholdInfos(List.of(
                                new SignalThresholdInfo.Builder()
                                        .setRadioAccessNetworkType(
                                                AccessNetworkConstants.AccessNetworkType.GERAN)
                                        .setSignalMeasurementType(
                                                SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI)
                                        .setThresholds(new int[]{-113, -103, -97, -51})
                                        .setHysteresisMs(1000) //allowed for system caller only
                                        .build()))
                        .setReportingRequestedWhileIdle(true)
                        .build();
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.setSignalStrengthUpdateRequest(requestWithHysteresisMsSet));
            fail("IllegalArgumentException expected when set hysteresisMs in SignalThresholdInfo "
                    + "to true");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testSetSignalStrengthUpdateRequest_isEnabledSet() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // Verify SE throws for app when set isEnabled in the SignalThresholdInfo
        SignalStrengthUpdateRequest requestWithThresholdIsEnabledSet =
                new SignalStrengthUpdateRequest.Builder()
                        .setSignalThresholdInfos(List.of(
                                new SignalThresholdInfo.Builder()
                                        .setRadioAccessNetworkType(
                                                AccessNetworkConstants.AccessNetworkType.GERAN)
                                        .setSignalMeasurementType(
                                                SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI)
                                        .setThresholds(new int[]{-113, -103, -97})
                                        .setIsEnabled(true) //allowed for system caller only
                                        .build()))
                        .setReportingRequestedWhileIdle(true)
                        .build();
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.setSignalStrengthUpdateRequest(requestWithThresholdIsEnabledSet));
            fail("IllegalArgumentException expected when set isEnabled in SignalThresholdInfo "
                    + "with true");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testSetSignalStrengthUpdateRequest_tooShortThresholds() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // verify SE throws if app set too short thresholds
        SignalStrengthUpdateRequest requestWithTooShortThresholds =
                new SignalStrengthUpdateRequest.Builder()
                        .setSignalThresholdInfos(List.of(
                                new SignalThresholdInfo.Builder()
                                        .setRadioAccessNetworkType(
                                                AccessNetworkConstants.AccessNetworkType.GERAN)
                                        .setSignalMeasurementType(
                                                SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI)
                                        .setThresholds(new int[]{}, true /*isSystem*/)
                                        .build()))
                        .setReportingRequestedWhileIdle(true)
                        .build();
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.setSignalStrengthUpdateRequest(requestWithTooShortThresholds));
            fail("IllegalArgumentException expected when set thresholds that is too short");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testSetSignalStrengthUpdateRequest_tooLongThresholds() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // verify SE throws if app set too long thresholds
        SignalStrengthUpdateRequest requestWithTooLongThresholds =
                new SignalStrengthUpdateRequest.Builder()
                        .setSignalThresholdInfos(List.of(
                                new SignalThresholdInfo.Builder()
                                        .setRadioAccessNetworkType(
                                                AccessNetworkConstants.AccessNetworkType.GERAN)
                                        .setSignalMeasurementType(
                                                SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI)
                                        .setThresholds(new int[]{-113, -103, -97, -61, -51},
                                            true /*isSystem*/)
                                        .build()))
                        .setReportingRequestedWhileIdle(true)
                        .build();
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.setSignalStrengthUpdateRequest(requestWithTooLongThresholds));
            fail("IllegalArgumentException expected when set thresholds that is too long");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testSetSignalStrengthUpdateRequest_duplicatedRequest() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        final SignalStrengthUpdateRequest normalRequest =
                new SignalStrengthUpdateRequest.Builder()
                        .setSignalThresholdInfos(List.of(
                                new SignalThresholdInfo.Builder()
                                        .setRadioAccessNetworkType(
                                                AccessNetworkConstants.AccessNetworkType.GERAN)
                                        .setSignalMeasurementType(
                                                SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI)
                                        .setThresholds(new int[]{-113, -103, -97, -51})
                                        .build()))
                        .setReportingRequestedWhileIdle(true)
                        .build();

        // Verify IllegalStateException should throw when set the same request twice
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                (tm) -> tm.setSignalStrengthUpdateRequest(normalRequest));
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.setSignalStrengthUpdateRequest(normalRequest));
            fail("IllegalStateException expected when setSignalStrengthUpdateRequest twice with "
                    + "same request object");
        } catch (IllegalStateException expected) {
        } finally {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.clearSignalStrengthUpdateRequest(normalRequest));
        }
    }

    @Test
    public void testClearSignalStrengthUpdateRequest_nullRequest() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // Verify NPE should throw if clear request with null object
        try {
            mTelephonyManager.clearSignalStrengthUpdateRequest(null);
            fail("NullPointerException expected when clearSignalStrengthUpdateRequest with null");
        } catch (NullPointerException expected) {
        }
    }

    @Test
    public void testClearSignalStrengthUpdateRequest_noPermission() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        final SignalStrengthUpdateRequest normalRequest =
                new SignalStrengthUpdateRequest.Builder()
                        .setSignalThresholdInfos(List.of(
                                new SignalThresholdInfo.Builder()
                                        .setRadioAccessNetworkType(
                                                AccessNetworkConstants.AccessNetworkType.GERAN)
                                        .setSignalMeasurementType(
                                                SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI)
                                        .setThresholds(new int[]{-113, -103, -97, -51})
                                        .build()))
                        .setReportingRequestedWhileIdle(true)
                        .build();

        // Verify SE throws for apps without carrier privilege or MODIFY_PHONE_STATE permission
        try {
            mTelephonyManager.clearSignalStrengthUpdateRequest(normalRequest);
            fail("SecurityException expected when clearSignalStrengthUpdateRequest without "
                    + "carrier privilege or MODIFY_PHONE_STATE permission");
        } catch (SecurityException expected) {
        }
    }

    @Test
    public void testClearSignalStrengthUpdateRequest_clearWithNoSet() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        SignalStrengthUpdateRequest requestNeverSetBefore = new SignalStrengthUpdateRequest
                .Builder()
                .setSignalThresholdInfos(List.of(new SignalThresholdInfo.Builder()
                        .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.GERAN)
                        .setSignalMeasurementType(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI)
                        .setThresholds(new int[]{-113, -103, -97, -51})
                        .build()))
                .setReportingRequestedWhileIdle(true)
                .build();

        // Verify clearSignalStrengthUpdateRequest is no-op when clear request that was not set
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                (tm) -> tm.clearSignalStrengthUpdateRequest(requestNeverSetBefore));
    }

    @Test
    public void testSendThermalMitigationRequest() throws Exception {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        StringBuilder cmdBuilder = new StringBuilder();
        cmdBuilder.append(THERMAL_MITIGATION_COMMAND_BASE).append(ALLOW_PACKAGE_SUBCOMMAND)
                .append(TELEPHONY_CTS_PACKAGE);
        TelephonyUtils.executeShellCommand(InstrumentationRegistry.getInstrumentation(),
                cmdBuilder.toString());

        long arbitraryCompletionWindowMillis = 60000L;

        boolean isDataThrottlingSupported = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.isRadioInterfaceCapabilitySupported(
                        TelephonyManager.CAPABILITY_THERMAL_MITIGATION_DATA_THROTTLING));

        int thermalMitigationResult = -1;
        if (isDataThrottlingSupported) {
            // Test a proper data throttling thermal mitigation request.
            thermalMitigationResult = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.sendThermalMitigationRequest(
                        new ThermalMitigationRequest.Builder()
                                .setThermalMitigationAction(ThermalMitigationRequest
                                        .THERMAL_MITIGATION_ACTION_DATA_THROTTLING)
                                .setDataThrottlingRequest(new DataThrottlingRequest.Builder()
                                        .setDataThrottlingAction(DataThrottlingRequest
                                                .DATA_THROTTLING_ACTION_THROTTLE_SECONDARY_CARRIER)
                                        .setCompletionDurationMillis(arbitraryCompletionWindowMillis)
                                        .build())
                                .build()));

            assertEquals(thermalMitigationResult,
                    TelephonyManager.THERMAL_MITIGATION_RESULT_SUCCESS);
        }
        // Test negative completionDurationSecs is an invalid parameter.
        try {
            thermalMitigationResult = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.sendThermalMitigationRequest(
                            new ThermalMitigationRequest.Builder()
                                    .setThermalMitigationAction(ThermalMitigationRequest
                                            .THERMAL_MITIGATION_ACTION_DATA_THROTTLING)
                                    .setDataThrottlingRequest(new DataThrottlingRequest.Builder()
                                            .setDataThrottlingAction(DataThrottlingRequest
                                                    .DATA_THROTTLING_ACTION_THROTTLE_PRIMARY_CARRIER
                                            )
                                            .setCompletionDurationMillis(-1)
                                            .build())
                                    .build()));
        } catch (IllegalArgumentException e) {
        }

        // Test non-zero completionDurationSecs is an invalid parameter for data throttling hold.
        try {
            thermalMitigationResult = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.sendThermalMitigationRequest(
                            new ThermalMitigationRequest.Builder()
                                    .setThermalMitigationAction(ThermalMitigationRequest
                                            .THERMAL_MITIGATION_ACTION_DATA_THROTTLING)
                                    .setDataThrottlingRequest(new DataThrottlingRequest.Builder()
                                            .setDataThrottlingAction(
                                                    DataThrottlingRequest
                                                            .DATA_THROTTLING_ACTION_HOLD)
                                            .setCompletionDurationMillis(
                                                    arbitraryCompletionWindowMillis)
                                            .build())
                                    .build()));
        } catch (IllegalArgumentException e) {
        }

        // Test null DataThrottlingParams is an invalid parameter for data throttling request.
        try {
            thermalMitigationResult = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.sendThermalMitigationRequest(
                            new ThermalMitigationRequest.Builder()
                                    .setThermalMitigationAction(ThermalMitigationRequest
                                            .THERMAL_MITIGATION_ACTION_DATA_THROTTLING)
                                    .build()));
        } catch (IllegalArgumentException e) {
        }

        // Test non-null DataThrottlingParams is an invalid parameter for voice only request.
        try {
            thermalMitigationResult = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.sendThermalMitigationRequest(
                            new ThermalMitigationRequest.Builder()
                                    .setThermalMitigationAction(
                                            ThermalMitigationRequest
                                                    .THERMAL_MITIGATION_ACTION_VOICE_ONLY)
                                    .setDataThrottlingRequest(new DataThrottlingRequest.Builder()
                                            .setDataThrottlingAction(
                                                    DataThrottlingRequest
                                                    .DATA_THROTTLING_ACTION_THROTTLE_PRIMARY_CARRIER
                                            )
                                            .setCompletionDurationMillis(-1)
                                            .build())
                            .build()));
        } catch (IllegalArgumentException e) {
        }

        // Test non-null DataThrottlingParams is an invalid parameter for radio off request.
        try {
            thermalMitigationResult = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.sendThermalMitigationRequest(
                            new ThermalMitigationRequest.Builder()
                                    .setThermalMitigationAction(
                                            ThermalMitigationRequest
                                                    .THERMAL_MITIGATION_ACTION_RADIO_OFF)
                                    .setDataThrottlingRequest(new DataThrottlingRequest.Builder()
                                            .setDataThrottlingAction(DataThrottlingRequest
                                                    .DATA_THROTTLING_ACTION_THROTTLE_PRIMARY_CARRIER
                                            )
                                            .setCompletionDurationMillis(-1)
                                            .build())
                            .build()));
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testIsRadioInterfaceCapabilitySupported() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        assertFalse(mTelephonyManager.isRadioInterfaceCapabilitySupported("empty"));
        assertFalse(mTelephonyManager.isRadioInterfaceCapabilitySupported(null));
        assertFalse(mTelephonyManager.isRadioInterfaceCapabilitySupported(""));
    }

    private Set<CellIdentity> getRegisteredCellIdentities() {
        ServiceState ss = mTelephonyManager.getServiceState();
        Set<CellIdentity> cidSet = new ArraySet<>(2);
        for (NetworkRegistrationInfo nri : ss.getNetworkRegistrationInfoListForTransportType(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN)) {
            if (nri.isRegistered()) cidSet.add(nri.getCellIdentity());
        }
        return cidSet;
    }

    private boolean hasMultipleRegisteredSubscriptions() {
        final int[] activeSubIds = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mSubscriptionManager, (sm) ->sm.getActiveSubscriptionIdList());
        int registeredSubscriptions = 0;
        for (int subId : activeSubIds) {
            ServiceState ss = mTelephonyManager.createForSubscriptionId(subId).getServiceState();
            for (NetworkRegistrationInfo nri : ss.getNetworkRegistrationInfoListForTransportType(
                    AccessNetworkConstants.TRANSPORT_TYPE_WWAN)) {
                if (nri.isRegistered()) {
                    registeredSubscriptions++;
                    break;
                }
            }
        }
        return registeredSubscriptions > 1;
    }

    @Test
    public void testGetAllCellInfo() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));
        // For IRadio <1.5, just verify that calling the method doesn't throw an error.
        if (mRadioVersion < RADIO_HAL_VERSION_1_5) {
            mTelephonyManager.getAllCellInfo();
            return;
        }

        List<CellInfo> allCellInfo = mTelephonyManager.getAllCellInfo();
        assertTrue(!allCellInfo.isEmpty());
        for (CellInfo cellInfo : allCellInfo) {
            CellIdentity cellIdentity = cellInfo.getCellIdentity();
            int[] bands;
            if (cellIdentity instanceof CellIdentityLte) {
                bands = ((CellIdentityLte) cellIdentity).getBands();
                if (cellInfo.isRegistered()) assertTrue(bands.length > 0);
                for (int band : bands) {
                    assertTrue(band >= AccessNetworkConstants.EutranBand.BAND_1
                            && band <= AccessNetworkConstants.EutranBand.BAND_88);
                }
            } else if (cellIdentity instanceof CellIdentityNr) {
                bands = ((CellIdentityNr) cellIdentity).getBands();
                if (cellInfo.isRegistered()) assertTrue(bands.length > 0);
                for (int band : bands) {
                    assertTrue((band >= AccessNetworkConstants.NgranBands.BAND_1
                            && band <= AccessNetworkConstants.NgranBands.BAND_95)
                            || (band >= AccessNetworkConstants.NgranBands.BAND_257
                            && band <= AccessNetworkConstants.NgranBands.BAND_261));
                }
            }

            // TODO(229311863): This can theoretically break on a DSDS device where both SIMs are
            // registered because CellInfo returns data for both modems and this code only cross
            // checks against the default subscription.
            if (hasMultipleRegisteredSubscriptions()) continue;

            boolean isSameCell = false;
            if (cellInfo.isRegistered()) {
                for (CellIdentity cid : getRegisteredCellIdentities()) {
                    if (cellIdentity.isSameCell(cid)) isSameCell = true;
                }
                assertTrue(sNetworkTypes.get(cellIdentity.getClass()).contains(
                            mTelephonyManager.getDataNetworkType())
                                    || sNetworkTypes.get(cellIdentity.getClass()).contains(
                                            mTelephonyManager.getVoiceNetworkType()));
                assertTrue(
                        "Registered CellInfo#CellIdentity not found in ServiceState",
                        isSameCell);
            }
        }

    }

    /**
     * Validate Emergency Number address that only contains the dialable character.
     *
     * @param address Emergency number address to validate
     * @return {@code true} if the address is valid; {@code false} otherwise.
     */
    private static boolean validateEmergencyNumberAddress(String address) {
        if (address == null) {
            return false;
        }
        for (char c : address.toCharArray()) {
            if (!isDialable(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validate Emergency Number country Iso
     *
     * @param countryIso Emergency number country iso to validate
     * @return {@code true} if the country iso is valid; {@code false} otherwise.
     */
    private static boolean validateEmergencyNumberCountryIso(String countryIso) {
        if (countryIso == null) {
            return false;
        }
        int length = countryIso.length();
        return length >= 0 && length <= 2;
    }

    /**
     * Validate Emergency Number MNC
     *
     * @param mnc Emergency number MNC to validate
     * @return {@code true} if the MNC is valid; {@code false} otherwise.
     */
    private static boolean validateEmergencyNumberMnc(String mnc) {
        if (mnc == null) {
            return false;
        }
        int length = mnc.length();
        return length >= 0 && length <= 3;
    }

    /**
     * Validate Emergency service category list
     *
     * @param categories Emergency service category list to validate
     * @return {@code true} if the category list is valid; {@code false} otherwise.
     */
    private static boolean validateEmergencyServiceCategoryList(List<Integer> categories) {
        if (categories == null) {
            return false;
        }
        if (categories.contains(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED)) {
            return categories.size() == 1;
        }
        for (int category : categories) {
            if (!EMERGENCY_SERVICE_CATEGORY_SET.contains(category)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validate Emergency number source list
     *
     * @param categories Emergency number source list to validate
     * @return {@code true} if the source list is valid; {@code false} otherwise.
     */
    private static boolean validateEmergencyNumberSourceList(List<Integer> sources) {
        if (sources == null) {
            return false;
        }
        for (int source : sources) {
            if (!EMERGENCY_NUMBER_SOURCE_SET.contains(source)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validate Emergency call routing.
     *
     * @param routing Emergency call routing to validate
     * @return {@code true} if the emergency call routing is valid; {@code false} otherwise.
     */
    private static boolean validateEmergencyCallRouting(int routing) {
        return routing >= EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN
                && routing <= (EmergencyNumber.EMERGENCY_CALL_ROUTING_EMERGENCY
                | EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);
    }

    /**
     * Valid the emergency number should be at least from a valid source.
     *
     * @param emergencyNumber Emergency number to verify
     * @return {@code true} if the emergency number is from any source; {@code false} otherwise.
     */
    private static boolean validateEmergencyNumberFromAnySource(EmergencyNumber emergencyNumber) {
        boolean isFromAnySource = false;
        for (int possibleSourceValue = EMERGENCY_NUMBER_SOURCE_RIL_ECCLIST;
                possibleSourceValue <= (EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING
                        | EmergencyNumber.EMERGENCY_NUMBER_SOURCE_SIM
                        | EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE
                        | EmergencyNumber.EMERGENCY_NUMBER_SOURCE_MODEM_CONFIG
                        | EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DEFAULT);
                possibleSourceValue++) {
            if (emergencyNumber.isFromSources(possibleSourceValue)) {
                isFromAnySource = true;
                break;
            }
        }
        return isFromAnySource;
    }

    /**
     * Valid the emergency number should be at least in a valid category.
     *
     * @param emergencyNumber Emergency number to verify
     * @return {@code true} if it is in any category; {@code false} otherwise.
     */
    private static boolean validateEmergencyNumberInAnyCategory(EmergencyNumber emergencyNumber) {
        boolean isInAnyCategory = false;
        for (int possibleCategoryValue = EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED;
                possibleCategoryValue <= (EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE
                        | EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AMBULANCE
                        | EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE
                        | EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MARINE_GUARD
                        | EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MOUNTAIN_RESCUE
                        | EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MIEC
                        | EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AIEC);
                possibleCategoryValue++) {
            if (emergencyNumber.isInEmergencyServiceCategories(possibleCategoryValue)) {
                isInAnyCategory = true;
                break;
            }
        }
        return isInAnyCategory;
    }

    private static boolean validateEmergencyNumberCompareTo(
            List<EmergencyNumber> emergencyNumberList) {
        if (emergencyNumberList == null) {
            return false;
        }
        if (emergencyNumberList.size() > 0) {
            EmergencyNumber emergencyNumber = emergencyNumberList.get(0);
            if (emergencyNumber.compareTo(emergencyNumber) != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isDialable(char c) {
        return (c >= '0' && c <= '9') || c == '*' || c == '#' || c == '+' || c == 'N';
    }

    private Map.Entry<Integer, Integer> getValidSlotIndexAndPort() {
        return ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> {

                    List<UiccCardInfo> cardInfos = mTelephonyManager.getUiccCardsInfo();
                    Set<String> presentCards = Arrays.stream(mTelephonyManager.getUiccSlotsInfo())
                            .filter(Objects::nonNull)
                            .filter(port -> port.getPorts().stream().anyMatch(portInfo ->
                                    portInfo.isActive()))
                            .map(UiccSlotInfo::getCardId)
                            .filter(Objects::nonNull)
                            // hack around getUiccSlotsInfo not stripping trailing F
                            .map(s -> s.endsWith("F") ? s.substring(0, s.length() - 1) : s)
                            .collect(Collectors.toSet());
                    int slotIndex = -1;
                    int portIndex = -1;
                    for (UiccCardInfo cardInfo : cardInfos) {
                        for (UiccPortInfo portInfo : cardInfo.getPorts()) {
                            if (presentCards.contains(portInfo.getIccId())
                                    || presentCards.contains(cardInfo.getEid())) {
                                slotIndex = cardInfo.getPhysicalSlotIndex();
                                portIndex = portInfo.getPortIndex();
                                Log.d(TAG, "SlotIndex : " + slotIndex + " and portIndex :"
                                        + portIndex);
                                break;
                            }
                        }
                    }
                    if (slotIndex < 0) {
                        fail("Test must be run with SIM card inserted, presentCards = "
                                + presentCards + "cardinfos = " + cardInfos);
                    }
                    return Map.entry(slotIndex, portIndex);
                });
    }

    public static void waitForMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Log.d(TAG, "InterruptedException while waiting: " + e);
        }
    }

    /**
     * Verify that the phone is supporting the action of setForbiddenPlmn.
     *
     * @return whether to proceed the test
     */
    private boolean supportSetFplmn() {
        if (!hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION)) {
            return false;
        }
        return mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM;
    }

    /**
     * Verify that the phone is supporting the action of setForbiddenPlmn.
     *
     * @return whether to proceed the test
     */
    private boolean test() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return false;
        }
        return mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM;
    }

    private static int makeRadioVersion(int major, int minor) {
        if (major < 0 || minor < 0) return 0;
        return major * 100 + minor;
    }

    private Executor mSimpleExecutor = Runnable::run;

    private static MockSignalStrengthsTelephonyCallback mMockSignalStrengthsTelephonyCallback;

    private class MockSignalStrengthsTelephonyCallback extends TelephonyCallback
            implements TelephonyCallback.SignalStrengthsListener {
        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            if (!mOnSignalStrengthsChanged) {
                synchronized (mLock) {
                    mOnSignalStrengthsChanged = true;
                    mLock.notify();
                }
            }
        }
    }

    @Test
    public void testRegisterTelephonyCallbackWithNonLooper() throws Throwable {
        mMockSignalStrengthsTelephonyCallback = new MockSignalStrengthsTelephonyCallback();

        // Test register, generates an mOnSignalStrengthsChanged event
        mTelephonyManager.registerTelephonyCallback(mSimpleExecutor,
                mMockSignalStrengthsTelephonyCallback);

        synchronized (mLock) {
            if (!mOnSignalStrengthsChanged) {
                mLock.wait(TOLERANCE);
            }
        }
        assertTrue("Test register, mOnSignalStrengthsChanged should be true.",
                mOnSignalStrengthsChanged);

        // Test unregister
        mOnSignalStrengthsChanged = false;
        // unregister again, to make sure doing so does not call the listener
        mTelephonyManager.unregisterTelephonyCallback(mMockSignalStrengthsTelephonyCallback);

        assertFalse("Test unregister, mOnSignalStrengthsChanged should be false.",
                mOnSignalStrengthsChanged);
    }

    private static MockCellInfoListener mMockCellInfoListener;

    private class MockCellInfoListener extends TelephonyCallback
            implements TelephonyCallback.CellInfoListener {
        @Override
        public void onCellInfoChanged(@NonNull List<CellInfo> cellInfo) {
            if (!mOnCellInfoChanged) {
                synchronized (mLock) {
                    mOnCellInfoChanged = true;
                    mLock.notify();
                }
            }
        }
    }

    @Test
    public void testRegisterTelephonyCallback() throws Throwable {
        if (mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
            // TODO: temp workaround, need to adjust test to for CDMA
            return;
        }
        grantLocationPermissions();

        TestThread t = new TestThread(() -> {
            Looper.prepare();
            mMockCellInfoListener = new MockCellInfoListener();
            synchronized (mLock) {
                mLock.notify(); // listener is ready
            }

            Looper.loop();
        });

        synchronized (mLock) {
            t.start();
            mLock.wait(TOLERANCE); // wait for listener
        }

        // Test register
        synchronized (mLock) {
            // .registerTelephonyCallback generates an onCellLocationChanged event
            mTelephonyManager.registerTelephonyCallback(mSimpleExecutor, mMockCellInfoListener);
            mLock.wait(TOLERANCE);

            assertTrue("Test register, mOnCellLocationChangedCalled should be true.",
                    mOnCellInfoChanged);
        }

        synchronized (mLock) {
            mOnCellInfoChanged = false;

            CellInfoResultsCallback resultsCallback = new CellInfoResultsCallback();
            mTelephonyManager.requestCellInfoUpdate(mSimpleExecutor, resultsCallback);
            mLock.wait(TOLERANCE);

            assertTrue("Test register, mOnCellLocationChangedCalled should be true.",
                    mOnCellInfoChanged);
        }

        // unregister the listener
        mTelephonyManager.unregisterTelephonyCallback(mMockCellInfoListener);
        Thread.sleep(TOLERANCE);

        // Test unregister
        synchronized (mLock) {
            mOnCellInfoChanged = false;
            // unregister again, to make sure doing so does not call the listener
            mTelephonyManager.unregisterTelephonyCallback(mMockCellInfoListener);
            CellLocation.requestLocationUpdate();
            mLock.wait(TOLERANCE);

            assertFalse("Test unregister, mOnCellLocationChangedCalled should be false.",
                    mOnCellInfoChanged);
        }
    }

    private class CellInfoResultsCallback extends TelephonyManager.CellInfoCallback {
        public List<CellInfo> cellInfo;

        @Override
        public synchronized void onCellInfo(List<CellInfo> cellInfo) {
            this.cellInfo = cellInfo;
            notifyAll();
        }

        public synchronized void wait(int millis) throws InterruptedException {
            if (cellInfo == null) {
                super.wait(millis);
            }
        }
    }

    private void setAppOpsPermissionAllowed(boolean allowed, String op) {
        AppOpsManager appOpsManager = getContext().getSystemService(AppOpsManager.class);
        int mode = allowed ? AppOpsManager.MODE_ALLOWED : AppOpsManager.opToDefaultMode(op);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                appOpsManager, (appOps) -> appOps.setUidMode(op, Process.myUid(), mode));
    }

    /**
     * Verifies that {@link TelephonyManager#getNetworkSlicingConfiguration()} does not throw any
     * exception
     */
    @Test
    public void testGetNetworkSlicingConfiguration() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        CompletableFuture<NetworkSlicingConfig> resultFuture = new CompletableFuture<>();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                (tm) -> tm.getNetworkSlicingConfiguration(mSimpleExecutor, resultFuture::complete));
    }

    @Test
    public void testCheckCarrierPrivilegesForPackageEnforcesReadPrivilege() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        try {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.READ_PRIVILEGED_PHONE_STATE");
            mTelephonyManager.checkCarrierPrivilegesForPackage(mSelfPackageName);
        } catch (SecurityException e) {
            fail("TelephonyManager#checkCarrierPrivilegesForPackage requires "
                    + "READ_PRIVILEGED_PHONE_STATE");
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
        }
    }

    @Test
    public void testCheckCarrierPrivilegesForPackageThrowsExceptionWithoutReadPrivilege() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        try {
            mTelephonyManager.checkCarrierPrivilegesForPackage(mSelfPackageName);
            fail("TelephonyManager#checkCarrierPrivilegesForPackage must be protected "
                    + "with READ_PRIVILEGED_PHONE_STATE");
        } catch (SecurityException e) {
            // expected
        }
    }

    @Test
    public void testCheckCarrierPrivilegesForPackageAnyPhone() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        try {
            mTelephonyManager.checkCarrierPrivilegesForPackageAnyPhone(mSelfPackageName);
            fail("TelephonyManager#checkCarrierPrivilegesForPackageAnyPhone must be protected "
                    + "with READ_PRIVILEGED_PHONE_STATE");
        } catch (SecurityException expected) {
        }

        try {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(
                            "android.permission.READ_PRIVILEGED_PHONE_STATE");
            mTelephonyManager.checkCarrierPrivilegesForPackageAnyPhone(mSelfPackageName);
        } catch (SecurityException e) {
            fail("TelephonyManager#checkCarrierPrivilegesForPackageAnyPhone should not throw "
                    + "SecurityException with READ_PRIVILEGED_PHONE_STATE permission");
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    public void testGetCarrierPackageNamesForIntentAndPhoneEnforcesReadPrivilege() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        try {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.READ_PRIVILEGED_PHONE_STATE");
            Intent intent = new Intent();
            int phoneId = 1;
            mTelephonyManager.getCarrierPackageNamesForIntentAndPhone(intent, phoneId);
        } catch (SecurityException e) {
            fail("TelephonyManager#getCarrierPackageNamesForIntentAndPhone requires "
                    + "READ_PRIVILEGED_PHONE_STATE");
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
        }
    }

    @Test
    public void testGetCarrierPackageNamesForIntentAndPhoneThrowsExceptionWithoutReadPrivilege() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        try {
            Intent intent = new Intent();
            int phoneId = 1;
            mTelephonyManager.getCarrierPackageNamesForIntentAndPhone(intent, phoneId);
            fail("TelephonyManager#getCarrierPackageNamesForIntentAndPhone must be protected "
                    + "with READ_PRIVILEGED_PHONE_STATE");
        } catch (SecurityException e) {
            // expected
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
        }
    }

    @Test
    public void testGetPackagesWithCarrierPrivilegesEnforcesReadPrivilege() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        try {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.READ_PRIVILEGED_PHONE_STATE");
            mTelephonyManager.getPackagesWithCarrierPrivileges();
        } catch (SecurityException e) {
            fail("TelephonyManager#getPackagesWithCarrierPrivileges requires "
                    + "READ_PRIVILEGED_PHONE_STATE");
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
        }
    }

    @Test
    public void testGetPackagesWithCarrierPrivilegesThrowsExceptionWithoutReadPrivilege() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        try {
            mTelephonyManager.getPackagesWithCarrierPrivileges();
            fail("TelephonyManager#getPackagesWithCarrierPrivileges must be protected "
                    + "with READ_PRIVILEGED_PHONE_STATE");
        } catch (SecurityException e) {
            // expected
        }
    }

    @Test
    public void testSimSlotMapping() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.READ_PRIVILEGED_PHONE_STATE");
        Collection<UiccSlotMapping> simSlotMapping = mTelephonyManager.getSimSlotMapping();
        // passing slotMapping combination
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.MODIFY_PHONE_STATE");
        try {
            mTelephonyManager.setSimSlotMapping(simSlotMapping);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // if HAL version is less than 2.0, vendors may not have implemented API,
            // skipping the failure.
            if (mRadioVersion >= RADIO_HAL_VERSION_2_0) {
                fail("Not Expected Fail, Error in setSimSlotMapping :" + e);
            }
        }

        List<UiccSlotMapping> slotMappingList = new ArrayList<>();
        // invalid logicalSlotIndex - Fail
        UiccSlotMapping slotMapping1 = new UiccSlotMapping(
                TelephonyManager.DEFAULT_PORT_INDEX, /*portIndex*/
                1, /*physicalSlotIndex*/
                SubscriptionManager.INVALID_PHONE_INDEX /*logicalSlotIndex*/);
        UiccSlotMapping slotMapping2 = new UiccSlotMapping(
                TelephonyManager.DEFAULT_PORT_INDEX, /*portIndex*/
                0, /*physicalSlotIndex*/
                0 /*logicalSlotIndex*/);
        slotMappingList.add(slotMapping1);
        slotMappingList.add(slotMapping2);
        try {
            mTelephonyManager.setSimSlotMapping(slotMappingList);
            fail("Expected IllegalStateException, invalid UiccSlotMapping data found");
        } catch (IllegalStateException e) {
            //expected
        }
        slotMappingList.clear();

        // Duplicate logicalSlotIndex - Fail
        UiccSlotMapping slotMapping3 = new UiccSlotMapping(
                TelephonyManager.DEFAULT_PORT_INDEX, /*portIndex*/
                1, /*physicalSlotIndex*/
                0 /*logicalSlotIndex*/);
        UiccSlotMapping slotMapping4 = new UiccSlotMapping(
                TelephonyManager.DEFAULT_PORT_INDEX, /*portIndex*/
                0, /*physicalSlotIndex*/
                0 /*logicalSlotIndex*/);
        slotMappingList.add(slotMapping3);
        slotMappingList.add(slotMapping4);
        try {
            mTelephonyManager.setSimSlotMapping(slotMappingList);
            fail("Expected IllegalArgumentException, Duplicate UiccSlotMapping data found");
        } catch (IllegalArgumentException e) {
            //expected
        }
        slotMappingList.clear();

        // Duplicate {portIndex+physicalSlotIndex} - Fail
        UiccSlotMapping slotMapping5 = new UiccSlotMapping(
                TelephonyManager.DEFAULT_PORT_INDEX, /*portIndex*/
                1, /*physicalSlotIndex*/
                0 /*logicalSlotIndex*/);
        UiccSlotMapping slotMapping6 = new UiccSlotMapping(
                TelephonyManager.DEFAULT_PORT_INDEX, /*portIndex*/
                1, /*physicalSlotIndex*/
                1 /*logicalSlotIndex*/);
        slotMappingList.add(slotMapping5);
        slotMappingList.add(slotMapping6);
        try {
            mTelephonyManager.setSimSlotMapping(slotMappingList);
            fail("Expected IllegalArgumentException, Duplicate UiccSlotMapping data found");
        } catch (IllegalArgumentException e) {
            //expected
        }
        slotMappingList.clear();

        // Duplicate {portIndex+physicalSlotIndex+logicalSlotIndex} - Fail
        UiccSlotMapping slotMapping7 = new UiccSlotMapping(
                TelephonyManager.DEFAULT_PORT_INDEX, /*portIndex*/
                1, /*physicalSlotIndex*/
                0 /*logicalSlotIndex*/);
        UiccSlotMapping slotMapping8 = new UiccSlotMapping(
                TelephonyManager.DEFAULT_PORT_INDEX, /*portIndex*/
                1, /*physicalSlotIndex*/
                0 /*logicalSlotIndex*/);
        slotMappingList.add(slotMapping7);
        slotMappingList.add(slotMapping8);
        try {
            mTelephonyManager.setSimSlotMapping(slotMappingList);
            fail("Expected IllegalArgumentException, Duplicate UiccSlotMapping data found");
        } catch (IllegalArgumentException e) {
            //expected
        }
        slotMappingList.clear();

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();

    }

    @Test
    public void getUiccSlotInfoTest() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.READ_PRIVILEGED_PHONE_STATE");
        UiccSlotInfo[] slotInfos = mTelephonyManager.getUiccSlotsInfo();

        if (slotInfos == null) {
            return;
        }

        // Call below methods to make sure it doesn't crash.
        for (UiccSlotInfo slotInfo : slotInfos) {
            slotInfo.getIsEuicc();
            slotInfo.getCardId();
            slotInfo.getCardStateInfo();
            slotInfo.getIsExtendedApduSupported();
            slotInfo.isRemovable();
            for (UiccPortInfo portInfo :slotInfo.getPorts()) {
                portInfo.isActive();
                portInfo.getIccId();
                portInfo.getLogicalSlotIndex();
                portInfo.getPortIndex();
            }
        }
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @Test
    public void testGetUiccSlotInfosFailsWithoutReadPhoneStatePrivilege() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));
        try {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
            mTelephonyManager.getUiccSlotsInfo();
            fail("TelephonyManager#getUiccSlotsInfo must be protected "
                    + "with READ_PRIVILEGED_PHONE_STATE");
        } catch (SecurityException e) {
            // expected
        }
    }

    @Test
    public void getSimSlotMappingTestReadPermission() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        try {
            Collection<UiccSlotMapping> simSlotMapping = mTelephonyManager.getSimSlotMapping();
            fail("Expected SecurityException, no READ_PRIVILEGED_PHONE_STATE permission");
        } catch (SecurityException e) {
            // expected
        }
    }

    @Test
    public void testIgnoreInvalidNetworkType() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }

        // NETWORK_TYPE_BITMASK_LTE_CA is invalid, should be converted into NETWORK_TYPE_BITMASK_LTE
        long invalidAllowedNetworkTypes = TelephonyManager.NETWORK_TYPE_BITMASK_NR
                | TelephonyManager.NETWORK_TYPE_BITMASK_LTE_CA;
        long expectedAllowedNetworkTypes = TelephonyManager.NETWORK_TYPE_BITMASK_NR
                | TelephonyManager.NETWORK_TYPE_BITMASK_LTE;
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager,
                    (tm) -> tm.setAllowedNetworkTypesForReason(
                            TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_POWER,
                            invalidAllowedNetworkTypes));

            long deviceAllowedNetworkTypes = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> {
                        return tm.getAllowedNetworkTypesForReason(
                                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_POWER);
                    }
            );
            assertEquals(expectedAllowedNetworkTypes, deviceAllowedNetworkTypes);
        } catch (SecurityException se) {
            fail("testIgnoreInvalidNetworkType: SecurityException not expected");
        }
    }

    @Test
    public void getSimSlotMappingTest() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.READ_PRIVILEGED_PHONE_STATE");
        try {
            Collection<UiccSlotMapping> simSlotMapping = mTelephonyManager.getSimSlotMapping();
            assertTrue(isSlotMappingValid(simSlotMapping));
        } catch (IllegalArgumentException e) {
            fail("IllegalArgumentException, Duplicate UiccSlotMapping data found");
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }
    private static boolean isSlotMappingValid(@NonNull Collection<UiccSlotMapping> slotMapping) {
        // Grouping the collection by logicalSlotIndex, finding different entries mapping to the
        // same logical slot
        Map<Integer, List<UiccSlotMapping>> slotMappingInfo = slotMapping.stream().collect(
                Collectors.groupingBy(UiccSlotMapping::getLogicalSlotIndex));
        for (Map.Entry<Integer, List<UiccSlotMapping>> entry : slotMappingInfo.entrySet()) {
            List<UiccSlotMapping> logicalSlotMap = entry.getValue();
            if (logicalSlotMap.size() > 1) {
                // duplicate logicalSlotIndex found
                return false;
            }
        }
        return true;
    }

    private static class ServiceStateRadioStateListener extends TelephonyCallback
            implements TelephonyCallback.ServiceStateListener,
            TelephonyCallback.RadioPowerStateListener {
        ServiceState mServiceState;
        int mRadioPowerState;

        ServiceStateRadioStateListener(ServiceState serviceState, int radioPowerState) {
            mServiceState = serviceState;
            mRadioPowerState = radioPowerState;
        }

        @Override
        public void onServiceStateChanged(ServiceState ss) {
            mServiceState = ss;
        }

        @Override
        public void onRadioPowerStateChanged(int radioState) {
            mRadioPowerState = radioState;
        }
    }

    @Test
    public void testSetVoiceServiceStateOverride() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CALLING));
        ServiceStateRadioStateListener callback = new ServiceStateRadioStateListener(
                mTelephonyManager.getServiceState(), mTelephonyManager.getRadioPowerState());
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                tm -> tm.registerTelephonyCallback(Runnable::run, callback));

        boolean turnedRadioOff = false;
        boolean setServiceStateOverride = false;
        try {
            if (mTelephonyManager.getServiceState().getState() == ServiceState.STATE_IN_SERVICE) {
                Log.i(TAG, "testSetVoiceServiceStateOverride: turning radio off to force OOS");
                ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                        tm -> tm.setRadioPower(false), permission.MODIFY_PHONE_STATE);
                turnedRadioOff = true;
                // Wait until ServiceState reflects the power change
                int retry = 0;
                while ((callback.mRadioPowerState != TelephonyManager.RADIO_POWER_OFF
                        || callback.mServiceState.getState() == ServiceState.STATE_IN_SERVICE)
                        && retry < 10) {
                    retry++;
                    waitForMs(1000);
                }
                assertEquals(TelephonyManager.RADIO_POWER_OFF, callback.mRadioPowerState);
                assertNotEquals(ServiceState.STATE_IN_SERVICE, callback.mServiceState.getState());
            }
            // This could be OUT_OF_SERVICE or POWER_OFF, it doesn't really matter for this test as
            // long as it's not IN_SERVICE
            ServiceState serviceState = mTelephonyManager.getServiceState();
            int retry = 0;
            while (serviceState == null && retry < 3) {
                serviceState = mTelephonyManager.getServiceState();
                retry++;
                waitForMs(200);
            }
            int originalServiceState = serviceState != null ? serviceState.getState()
                    : callback.mServiceState.getState();
            Log.i(TAG, "testSetVoiceServiceStateOverride: originalSS = " + originalServiceState);
            assertNotEquals(ServiceState.STATE_IN_SERVICE, originalServiceState);

            // Telecom will sometimes remove the override after radio reboots.
            // Retry setting the override to prevent flaky test failures.
            int listenerState = callback.mServiceState.getState();
            int telephonyManagerState = originalServiceState;
            retry = 0;
            while ((listenerState != ServiceState.STATE_IN_SERVICE
                    || telephonyManagerState != ServiceState.STATE_IN_SERVICE) && retry < 3) {
                // We should see the override in both ServiceStateListener and getServiceState
                ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                        tm -> tm.setVoiceServiceStateOverride(true),
                        permission.BIND_TELECOM_CONNECTION_SERVICE);
                setServiceStateOverride = true;

                serviceState = mTelephonyManager.getServiceState();
                if (serviceState != null) {
                    telephonyManagerState = serviceState.getState();
                }
                listenerState = callback.mServiceState.getState();
                retry++;
                waitForMs(5000);
            }
            assertEquals(ServiceState.STATE_IN_SERVICE, listenerState);
            assertEquals(ServiceState.STATE_IN_SERVICE, telephonyManagerState);

            // When we take away the override, things flip back to the original state since there
            // were no other material changes made to the device that would impact ServiceState
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    tm -> tm.setVoiceServiceStateOverride(false),
                    permission.BIND_TELECOM_CONNECTION_SERVICE);
            assertEquals(originalServiceState, callback.mServiceState.getState());
            assertEquals(originalServiceState, mTelephonyManager.getServiceState().getState());
        } finally {
            if (setServiceStateOverride) {
                // No harm in calling this again if we already did, but call just in case we failed
                // an assertion related to setOverride(true)
                ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                        tm -> tm.setVoiceServiceStateOverride(false),
                        permission.BIND_TELECOM_CONNECTION_SERVICE);
            }
            if (turnedRadioOff) {
                // Turn the radio back on and wait for ServiceState to become stable again so we
                // don't cause flakes in other tests
                Log.i(TAG, "testSetVoiceServiceStateOverride: turning radio back on");
                ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                        tm -> tm.setRadioPower(true), permission.MODIFY_PHONE_STATE);
                int retry = 0;
                while ((callback.mRadioPowerState != TelephonyManager.RADIO_POWER_ON
                        || callback.mServiceState.getState() != ServiceState.STATE_IN_SERVICE)
                        && retry < 10) {
                    retry++;
                    waitForMs(1000);
                }
                assertEquals(TelephonyManager.RADIO_POWER_ON, callback.mRadioPowerState);
                assertEquals(ServiceState.STATE_IN_SERVICE, callback.mServiceState.getState());
            }
        }
    }
}
