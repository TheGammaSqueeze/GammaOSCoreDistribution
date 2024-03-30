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

package com.android.server.uwb;

import static android.uwb.UwbAddress.SHORT_ADDRESS_BYTE_LENGTH;

import static com.google.uwb.support.ccc.CccParams.CHAPS_PER_SLOT_3;
import static com.google.uwb.support.ccc.CccParams.HOPPING_CONFIG_MODE_ADAPTIVE;
import static com.google.uwb.support.ccc.CccParams.HOPPING_CONFIG_MODE_CONTINUOUS;
import static com.google.uwb.support.ccc.CccParams.HOPPING_SEQUENCE_AES;
import static com.google.uwb.support.ccc.CccParams.HOPPING_SEQUENCE_DEFAULT;
import static com.google.uwb.support.ccc.CccParams.PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE;
import static com.google.uwb.support.ccc.CccParams.SLOTS_PER_ROUND_6;
import static com.google.uwb.support.ccc.CccParams.UWB_CHANNEL_9;
import static com.google.uwb.support.fira.FiraParams.AOA_RESULT_REQUEST_MODE_NO_AOA_REPORT;
import static com.google.uwb.support.fira.FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS;
import static com.google.uwb.support.fira.FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS_AZIMUTH_ONLY;
import static com.google.uwb.support.fira.FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS_ELEVATION_ONLY;
import static com.google.uwb.support.fira.FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS_INTERLEAVED;
import static com.google.uwb.support.fira.FiraParams.HOPPING_MODE_DISABLE;
import static com.google.uwb.support.fira.FiraParams.MULTICAST_LIST_UPDATE_ACTION_ADD;
import static com.google.uwb.support.fira.FiraParams.MULTICAST_LIST_UPDATE_ACTION_DELETE;
import static com.google.uwb.support.fira.FiraParams.MULTI_NODE_MODE_ONE_TO_MANY;
import static com.google.uwb.support.fira.FiraParams.MULTI_NODE_MODE_UNICAST;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_ROLE_INITIATOR;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_ROLE_RESPONDER;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_TYPE_CONTROLEE;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_TYPE_CONTROLLER;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_DS_TWR_NON_DEFERRED_MODE;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_SS_TWR_DEFERRED_MODE;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_SS_TWR_NON_DEFERRED_MODE;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.annotation.NonNull;
import android.content.AttributionSource;
import android.content.Context;
import android.os.Binder;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Pair;
import android.uwb.IUwbRangingCallbacks;
import android.uwb.RangingReport;
import android.uwb.SessionHandle;
import android.uwb.UwbAddress;
import android.uwb.UwbManager;

import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.BasicShellCommandHandler;
import com.android.server.uwb.jni.NativeUwbManager;
import com.android.server.uwb.util.ArrayUtils;

import com.google.uwb.support.base.Params;
import com.google.uwb.support.ccc.CccOpenRangingParams;
import com.google.uwb.support.ccc.CccParams;
import com.google.uwb.support.ccc.CccPulseShapeCombo;
import com.google.uwb.support.ccc.CccStartRangingParams;
import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraRangingReconfigureParams;
import com.google.uwb.support.generic.GenericSpecificationParams;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Interprets and executes 'adb shell cmd uwb [args]'.
 *
 * To add new commands:
 * - onCommand: Add a case "<command>" execute. Return a 0
 *   if command executed successfully.
 * - onHelp: add a description string.
 *
 * Permissions: currently root permission is required for some commands. Others will
 * enforce the corresponding API permissions.
 */
public class UwbShellCommand extends BasicShellCommandHandler {
    @VisibleForTesting
    public static String SHELL_PACKAGE_NAME = "com.android.shell";
    private static final long RANGE_CTL_TIMEOUT_MILLIS = 10_000;

    // These don't require root access.
    // However, these do perform permission checks in the corresponding UwbService methods.
    private static final String[] NON_PRIVILEGED_COMMANDS = {
            "help",
            "status",
            "get-country-code",
            "enable-uwb",
            "disable-uwb",
            "start-fira-ranging-session",
            "start-ccc-ranging-session",
            "reconfigure-fira-ranging-session",
            "get-ranging-session-reports",
            "get-all-ranging-session-reports",
            "stop-ranging-session",
            "stop-all-ranging-sessions",
            "get-specification-info",
    };

    @VisibleForTesting
    public static final FiraOpenSessionParams.Builder DEFAULT_FIRA_OPEN_SESSION_PARAMS =
            new FiraOpenSessionParams.Builder()
                    .setProtocolVersion(FiraParams.PROTOCOL_VERSION_1_1)
                    .setSessionId(1)
                    .setChannelNumber(9)
                    .setDeviceType(RANGING_DEVICE_TYPE_CONTROLLER)
                    .setDeviceRole(RANGING_DEVICE_ROLE_INITIATOR)
                    .setDeviceAddress(UwbAddress.fromBytes(new byte[] { 0x4, 0x6}))
                    .setDestAddressList(Arrays.asList(UwbAddress.fromBytes(new byte[] { 0x4, 0x6})))
                    .setMultiNodeMode(MULTI_NODE_MODE_UNICAST)
                    .setRangingRoundUsage(RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE)
                    .setVendorId(new byte[]{0x5, 0x78})
                    .setStaticStsIV(new byte[]{0x1a, 0x55, 0x77, 0x47, 0x7e, 0x7d});

    @VisibleForTesting
    public static final CccOpenRangingParams.Builder DEFAULT_CCC_OPEN_RANGING_PARAMS =
            new CccOpenRangingParams.Builder()
                    .setProtocolVersion(CccParams.PROTOCOL_VERSION_1_0)
                    .setUwbConfig(CccParams.UWB_CONFIG_0)
                    .setPulseShapeCombo(
                            new CccPulseShapeCombo(
                                    PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE,
                                    PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE))
                    .setSessionId(1)
                    .setRanMultiplier(4)
                    .setChannel(UWB_CHANNEL_9)
                    .setNumChapsPerSlot(CHAPS_PER_SLOT_3)
                    .setNumResponderNodes(1)
                    .setNumSlotsPerRound(SLOTS_PER_ROUND_6)
                    .setSyncCodeIndex(1)
                    .setHoppingConfigMode(HOPPING_MODE_DISABLE)
                    .setHoppingSequence(HOPPING_SEQUENCE_DEFAULT);

    private static final Map<Integer, SessionInfo> sSessionIdToInfo = new ArrayMap<>();
    private static int sSessionHandleIdNext = 0;

    private final UwbServiceImpl mUwbService;
    private final UwbCountryCode mUwbCountryCode;
    private final NativeUwbManager mNativeUwbManager;
    private final Context mContext;

    UwbShellCommand(UwbInjector uwbInjector, UwbServiceImpl uwbService, Context context) {
        mUwbService = uwbService;
        mContext = context;
        mUwbCountryCode = uwbInjector.getUwbCountryCode();
        mNativeUwbManager = uwbInjector.getNativeUwbManager();
    }

    private static String bundleToString(@Nullable PersistableBundle bundle) {
        if (bundle != null) {
            // Need to defuse any local bundles before printing. Use isEmpty() triggers unparcel.
            bundle.isEmpty();
            return bundle.toString();
        } else {
            return "null";
        }
    }

    private static final class UwbRangingCallbacks extends IUwbRangingCallbacks.Stub {
        private final SessionInfo mSessionInfo;
        private final PrintWriter mPw;
        private final CompletableFuture mRangingOpenedFuture;
        private final CompletableFuture mRangingStartedFuture;
        private final CompletableFuture mRangingStoppedFuture;
        private final CompletableFuture mRangingClosedFuture;
        private final CompletableFuture mRangingReconfiguredFuture;

        UwbRangingCallbacks(@NonNull SessionInfo sessionInfo, @NonNull PrintWriter pw,
                @NonNull CompletableFuture rangingOpenedFuture,
                @NonNull CompletableFuture rangingStartedFuture,
                @NonNull CompletableFuture rangingStoppedFuture,
                @NonNull CompletableFuture rangingClosedFuture,
                @NonNull CompletableFuture rangingReconfiguredFuture) {
            mSessionInfo = sessionInfo;
            mPw = pw;
            mRangingOpenedFuture = rangingOpenedFuture;
            mRangingStartedFuture = rangingStartedFuture;
            mRangingStoppedFuture = rangingStoppedFuture;
            mRangingClosedFuture = rangingClosedFuture;
            mRangingReconfiguredFuture = rangingReconfiguredFuture;
        }

        public void onRangingOpened(SessionHandle sessionHandle) {
            mPw.println("Ranging session opened");
            mRangingOpenedFuture.complete(true);
        }

        public void onRangingOpenFailed(SessionHandle sessionHandle, int reason,
                PersistableBundle params) {
            mPw.println("Ranging session open failed with reason: " + reason + " and params: "
                    + bundleToString(params));
            mRangingOpenedFuture.complete(false);
        }

        public void onRangingStarted(SessionHandle sessionHandle, PersistableBundle params) {
            mPw.println("Ranging session started with params: " + bundleToString(params));
            mRangingStartedFuture.complete(true);
        }

        public void onRangingStartFailed(SessionHandle sessionHandle, int reason,
                PersistableBundle params) {
            mPw.println("Ranging session start failed with reason: " + reason + " and params: "
                    + bundleToString(params));
            mRangingStartedFuture.complete(false);
        }

        public void onRangingReconfigured(SessionHandle sessionHandle, PersistableBundle params) {
            mPw.println("Ranging reconfigured with params: " + bundleToString(params));
            mRangingReconfiguredFuture.complete(true);
        }

        public void onRangingReconfigureFailed(SessionHandle sessionHandle, int reason,
                PersistableBundle params) {
            mPw.println("Ranging reconfigure failed with reason: " + reason + " and params: "
                    + bundleToString(params));
            mRangingReconfiguredFuture.complete(true);

        }

        public void onRangingStopped(SessionHandle sessionHandle, int reason,
                PersistableBundle params) {
            mPw.println("Ranging session stopped with reason: " + reason + " and params: "
                    + bundleToString(params));
            mRangingStoppedFuture.complete(true);
        }

        public void onRangingStopFailed(SessionHandle sessionHandle, int reason,
                PersistableBundle params) {
            mPw.println("Ranging session stop failed with reason: " + reason + " and params: "
                    + bundleToString(params));
            mRangingStoppedFuture.complete(false);
        }

        public void onRangingClosed(SessionHandle sessionHandle, int reason,
                PersistableBundle params) {
            mPw.println("Ranging session closed with reason: " + reason + " and params: "
                    + bundleToString(params));
            sSessionIdToInfo.remove(mSessionInfo.sessionId);
            mRangingClosedFuture.complete(true);
        }

        public void onRangingResult(SessionHandle sessionHandle, RangingReport rangingReport) {
            mPw.println("Ranging Result: " + rangingReport);
            mSessionInfo.addRangingReport(rangingReport);
        }

        public void onControleeAdded(SessionHandle sessionHandle, PersistableBundle params) {}

        public void onControleeAddFailed(SessionHandle sessionHandle, int reason,
                PersistableBundle params) {}

        public void onControleeRemoved(SessionHandle sessionHandle, PersistableBundle params) {}

        public void onControleeRemoveFailed(SessionHandle sessionHandle, int reason,
                PersistableBundle params) {}

        public void onRangingPaused(SessionHandle sessionHandle, PersistableBundle params) {}

        public void onRangingPauseFailed(SessionHandle sessionHandle, int reason,
                PersistableBundle params) {}

        public void onRangingResumed(SessionHandle sessionHandle, PersistableBundle params) {}

        public void onRangingResumeFailed(SessionHandle sessionHandle, int reason,
                PersistableBundle params) {}

        public void onDataSent(SessionHandle sessionHandle, UwbAddress uwbAddress,
                PersistableBundle params) {}

        public void onDataSendFailed(SessionHandle sessionHandle, UwbAddress uwbAddress, int reason,
                PersistableBundle params) {}

        public void onDataReceived(SessionHandle sessionHandle, UwbAddress uwbAddress,
                PersistableBundle params, byte[] data) {}

        public void onDataReceiveFailed(SessionHandle sessionHandle, UwbAddress uwbAddress,
                int reason, PersistableBundle params) {}

        public void onServiceDiscovered(SessionHandle sessionHandle, PersistableBundle params) {}

        public void onServiceConnected(SessionHandle sessionHandle, PersistableBundle params) {}
    }


    private class SessionInfo {
        private static final int LAST_NUM_RANGING_REPORTS = 20;

        public final SessionHandle sessionHandle;
        public final int sessionId;
        public final Params openRangingParams;
        public final UwbRangingCallbacks uwbRangingCbs;
        public final ArrayDeque<RangingReport> lastRangingReports =
                new ArrayDeque<>(LAST_NUM_RANGING_REPORTS);

        public final CompletableFuture<Boolean> rangingOpenedFuture = new CompletableFuture<>();
        public final CompletableFuture<Boolean> rangingStartedFuture = new CompletableFuture<>();
        public final CompletableFuture<Boolean> rangingStoppedFuture = new CompletableFuture<>();
        public final CompletableFuture<Boolean> rangingClosedFuture = new CompletableFuture<>();
        public final CompletableFuture<Boolean> rangingReconfiguredFuture =
                new CompletableFuture<>();

        SessionInfo(int sessionId, int sSessionHandleIdNext, @NonNull Params openRangingParams,
                @NonNull PrintWriter pw) {
            this.sessionId = sessionId;
            sessionHandle = new SessionHandle(sSessionHandleIdNext);
            this.openRangingParams = openRangingParams;
            uwbRangingCbs = new UwbRangingCallbacks(this, pw, rangingOpenedFuture,
                    rangingStartedFuture, rangingStoppedFuture, rangingClosedFuture,
                    rangingReconfiguredFuture);
        }

        public void addRangingReport(@NonNull RangingReport rangingReport) {
            if (lastRangingReports.size() == LAST_NUM_RANGING_REPORTS) {
                lastRangingReports.remove();
            }
            lastRangingReports.add(rangingReport);
        }
    }

    private Pair<FiraOpenSessionParams, Boolean> buildFiraOpenSessionParams() {
        FiraOpenSessionParams.Builder builder =
                new FiraOpenSessionParams.Builder(DEFAULT_FIRA_OPEN_SESSION_PARAMS);
        boolean shouldBlockCall = false;
        boolean interleavingEnabled = false;
        boolean aoaResultReqEnabled = false;
        String option = getNextOption();
        while (option != null) {
            if (option.equals("-b")) {
                shouldBlockCall = true;
            }
            if (option.equals("-i")) {
                builder.setSessionId(Integer.parseInt(getNextArgRequired()));
            }
            if (option.equals("-c")) {
                builder.setChannelNumber(Integer.parseInt(getNextArgRequired()));
            }
            if (option.equals("-t")) {
                String type = getNextArgRequired();
                if (type.equals("controller")) {
                    builder.setDeviceType(RANGING_DEVICE_TYPE_CONTROLLER);
                } else if (type.equals("controlee")) {
                    builder.setDeviceType(RANGING_DEVICE_TYPE_CONTROLEE);
                } else {
                    throw new IllegalArgumentException("Unknown device type: " + type);
                }
            }
            if (option.equals("-r")) {
                String role = getNextArgRequired();
                if (role.equals("initiator")) {
                    builder.setDeviceType(RANGING_DEVICE_ROLE_INITIATOR);
                } else if (role.equals("responder")) {
                    builder.setDeviceType(RANGING_DEVICE_ROLE_RESPONDER);
                } else {
                    throw new IllegalArgumentException("Unknown device role: " + role);
                }
            }
            if (option.equals("-a")) {
                builder.setDeviceAddress(
                        UwbAddress.fromBytes(
                                ByteBuffer.allocate(SHORT_ADDRESS_BYTE_LENGTH)
                                        .putShort(Short.parseShort(getNextArgRequired()))
                                        .array()));
            }
            if (option.equals("-d")) {
                String[] destAddressesString = getNextArgRequired().split(",");
                List<UwbAddress> destAddresses = new ArrayList<>();
                for (String destAddressString : destAddressesString) {
                    destAddresses.add(UwbAddress.fromBytes(
                            ByteBuffer.allocate(SHORT_ADDRESS_BYTE_LENGTH)
                                    .putShort(Short.parseShort(destAddressString))
                                    .array()));
                }
                builder.setDestAddressList(destAddresses);
                builder.setMultiNodeMode(destAddresses.size() > 1
                        ? MULTI_NODE_MODE_ONE_TO_MANY
                        : MULTI_NODE_MODE_UNICAST);
            }
            if (option.equals("-u")) {
                String usage = getNextArgRequired();
                if (usage.equals("ds-twr")) {
                    builder.setRangingRoundUsage(RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE);
                } else if (usage.equals("ss-twr")) {
                    builder.setRangingRoundUsage(RANGING_ROUND_USAGE_SS_TWR_DEFERRED_MODE);
                } else if (usage.equals("ds-twr-non-deferred")) {
                    builder.setRangingRoundUsage(RANGING_ROUND_USAGE_DS_TWR_NON_DEFERRED_MODE);
                } else if (usage.equals("ss-twr-non-deferred")) {
                    builder.setRangingRoundUsage(RANGING_ROUND_USAGE_SS_TWR_NON_DEFERRED_MODE);
                } else {
                    throw new IllegalArgumentException("Unknown round usage: " + usage);
                }
            }
            if (option.equals("-z")) {
                String[] interleaveRatioString = getNextArgRequired().split(",");
                if (interleaveRatioString.length != 3) {
                    throw new IllegalArgumentException("Unexpected interleaving ratio: "
                            +  Arrays.toString(interleaveRatioString)
                            + " expected to be <numRange, numAoaAzimuth, numAoaElevation>");
                }
                int numOfRangeMsrmts = Integer.parseInt(interleaveRatioString[0]);
                int numOfAoaAzimuthMrmts = Integer.parseInt(interleaveRatioString[1]);
                int numOfAoaElevationMrmts = Integer.parseInt(interleaveRatioString[2]);
                // Set to interleaving mode
                builder.setAoaResultRequest(AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS_INTERLEAVED);
                builder.setMeasurementFocusRatio(
                        numOfRangeMsrmts,
                        numOfAoaAzimuthMrmts,
                        numOfAoaElevationMrmts);
                interleavingEnabled = true;
            }
            if (option.equals("-e")) {
                String aoaType = getNextArgRequired();
                if (aoaType.equals("none")) {
                    builder.setAoaResultRequest(AOA_RESULT_REQUEST_MODE_NO_AOA_REPORT);
                } else if (aoaType.equals("enabled")) {
                    builder.setAoaResultRequest(AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS);
                } else if (aoaType.equals("azimuth-only")) {
                    builder.setAoaResultRequest(
                        AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS_AZIMUTH_ONLY);
                } else if (aoaType.equals("elevation-only")) {
                    builder.setAoaResultRequest(
                        AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS_ELEVATION_ONLY);
                } else {
                    throw new IllegalArgumentException("Unknown aoa type: " + aoaType);
                }
                aoaResultReqEnabled = true;
            }
            option = getNextOption();
        }
        if (aoaResultReqEnabled && interleavingEnabled) {
            throw new IllegalArgumentException(
                    "Both interleaving (-z) and aoa result req (-e) cannot be specified");
        }
        // TODO: Add remaining params if needed.
        return Pair.create(builder.build(), shouldBlockCall);
    }

    private void startFiraRangingSession(PrintWriter pw) throws Exception {
        Pair<FiraOpenSessionParams, Boolean> firaOpenSessionParams = buildFiraOpenSessionParams();
        startRangingSession(
                firaOpenSessionParams.first, null, firaOpenSessionParams.first.getSessionId(),
                firaOpenSessionParams.second, pw);
    }

    private Pair<CccOpenRangingParams, Boolean> buildCccOpenRangingParams() {
        CccOpenRangingParams.Builder builder =
                new CccOpenRangingParams.Builder(DEFAULT_CCC_OPEN_RANGING_PARAMS);
        boolean shouldBlockCall = false;
        String option = getNextOption();
        while (option != null) {
            if (option.equals("-b")) {
                shouldBlockCall = true;
            }
            if (option.equals("-u")) {
                builder.setUwbConfig(Integer.parseInt(getNextArgRequired()));
            }
            if (option.equals("-p")) {
                String[] pulseComboString = getNextArgRequired().split(",");
                if (pulseComboString.length != 2) {
                    throw new IllegalArgumentException("Erroneous pulse combo: "
                            + Arrays.toString(pulseComboString));
                }
                builder.setPulseShapeCombo(new CccPulseShapeCombo(
                        Integer.parseInt(pulseComboString[0]),
                        Integer.parseInt(pulseComboString[1])));
            }
            if (option.equals("-i")) {
                builder.setSessionId(Integer.parseInt(getNextArgRequired()));
            }
            if (option.equals("-r")) {
                builder.setRanMultiplier(Integer.parseInt(getNextArgRequired()));
            }
            if (option.equals("-c")) {
                builder.setChannel(Integer.parseInt(getNextArgRequired()));
            }
            if (option.equals("-p")) {
                builder.setNumChapsPerSlot(Integer.parseInt(getNextArgRequired()));
            }
            if (option.equals("-n")) {
                builder.setNumResponderNodes(Integer.parseInt(getNextArgRequired()));
            }
            if (option.equals("-o")) {
                builder.setNumSlotsPerRound(Integer.parseInt(getNextArgRequired()));
            }
            if (option.equals("-s")) {
                builder.setSyncCodeIndex(Integer.parseInt(getNextArgRequired()));
            }
            if (option.equals("-h")) {
                String hoppingConfigMode = getNextArgRequired();
                if (hoppingConfigMode.equals("none")) {
                    builder.setHoppingConfigMode(HOPPING_MODE_DISABLE);
                } else if (hoppingConfigMode.equals("continuous")) {
                    builder.setHoppingConfigMode(HOPPING_CONFIG_MODE_CONTINUOUS);
                } else if (hoppingConfigMode.equals("adaptive")) {
                    builder.setHoppingConfigMode(HOPPING_CONFIG_MODE_ADAPTIVE);
                } else {
                    throw new IllegalArgumentException("Unknown hopping config mode: "
                            + hoppingConfigMode);
                }
            }
            if (option.equals("-a")) {
                String hoppingSequence = getNextArgRequired();
                if (hoppingSequence.equals("default")) {
                    builder.setHoppingSequence(HOPPING_SEQUENCE_DEFAULT);
                } else if (hoppingSequence.equals("aes")) {
                    builder.setHoppingConfigMode(HOPPING_SEQUENCE_AES);
                } else {
                    throw new IllegalArgumentException("Unknown hopping sequence: "
                            + hoppingSequence);
                }
            }
            option = getNextOption();
        }
        // TODO: Add remaining params if needed.
        return Pair.create(builder.build(), shouldBlockCall);
    }

    private void startCccRangingSession(PrintWriter pw) throws Exception {
        Pair<CccOpenRangingParams, Boolean> cccOpenRangingParamsAndBlocking =
                buildCccOpenRangingParams();
        CccOpenRangingParams cccOpenRangingParams = cccOpenRangingParamsAndBlocking.first;
        CccStartRangingParams cccStartRangingParams = new CccStartRangingParams.Builder()
                .setSessionId(cccOpenRangingParams.getSessionId())
                .setRanMultiplier(cccOpenRangingParams.getRanMultiplier())
                .build();
        startRangingSession(
                cccOpenRangingParams, cccStartRangingParams, cccOpenRangingParams.getSessionId(),
                cccOpenRangingParamsAndBlocking.second, pw);
    }

    private void startRangingSession(@NonNull Params openRangingSessionParams,
            @Nullable Params startRangingSessionParams, int sessionId,
            boolean shouldBlockCall, @NonNull PrintWriter pw) throws Exception {
        if (sSessionIdToInfo.containsKey(sessionId)) {
            pw.println("Session with session ID: " + sessionId
                    + " already ongoing. Stop that session before you start a new session");
            return;
        }
        SessionInfo sessionInfo =
                new SessionInfo(sessionId, sSessionHandleIdNext++, openRangingSessionParams, pw);
        mUwbService.openRanging(
                new AttributionSource.Builder(Process.SHELL_UID)
                        .setPackageName(SHELL_PACKAGE_NAME)
                        .build(),
                sessionInfo.sessionHandle,
                sessionInfo.uwbRangingCbs,
                openRangingSessionParams.toBundle(),
                null);
        boolean openCompleted = false;
        try {
            openCompleted = sessionInfo.rangingOpenedFuture.get(
                    RANGE_CTL_TIMEOUT_MILLIS, MILLISECONDS);
        } catch (InterruptedException | CancellationException | TimeoutException
                | ExecutionException e) {
        }
        if (!openCompleted) {
            pw.println("Failed to open ranging session. Aborting!");
            return;
        }
        pw.println("Ranging session opened with params: "
                + bundleToString(openRangingSessionParams.toBundle()));

        mUwbService.startRanging(
                sessionInfo.sessionHandle,
                startRangingSessionParams != null
                        ? startRangingSessionParams.toBundle()
                        : new PersistableBundle());
        boolean startCompleted = false;
        try {
            startCompleted = sessionInfo.rangingStartedFuture.get(
                    RANGE_CTL_TIMEOUT_MILLIS, MILLISECONDS);
        } catch (InterruptedException | CancellationException | TimeoutException
                | ExecutionException e) {
        }
        if (!startCompleted) {
            pw.println("Failed to start ranging session. Aborting!");
            return;
        }
        pw.println("Ranging session started for sessionId: " + sessionId);
        sSessionIdToInfo.put(sessionId, sessionInfo);
        while (shouldBlockCall) {
            Thread.sleep(RANGE_CTL_TIMEOUT_MILLIS);
        }
    }

    private void stopRangingSession(PrintWriter pw) throws RemoteException {
        int sessionId = Integer.parseInt(getNextArgRequired());
        stopRangingSession(pw, sessionId);
    }

    private void stopRangingSession(PrintWriter pw, int sessionId) throws RemoteException {
        SessionInfo sessionInfo = sSessionIdToInfo.get(sessionId);
        if (sessionInfo == null) {
            pw.println("No active session with session ID: " + sessionId + " found");
            return;
        }
        mUwbService.stopRanging(sessionInfo.sessionHandle);
        boolean stopCompleted = false;
        try {
            stopCompleted = sessionInfo.rangingStoppedFuture.get(
                    RANGE_CTL_TIMEOUT_MILLIS, MILLISECONDS);
        } catch (InterruptedException | CancellationException | TimeoutException
                | ExecutionException e) {
        }
        if (!stopCompleted) {
            pw.println("Failed to stop ranging session. Aborting!");
            return;
        }
        pw.println("Ranging session stopped");

        mUwbService.closeRanging(sessionInfo.sessionHandle);
        boolean closeCompleted = false;
        try {
            closeCompleted = sessionInfo.rangingClosedFuture.get(
                    RANGE_CTL_TIMEOUT_MILLIS, MILLISECONDS);
        } catch (InterruptedException | CancellationException | TimeoutException
                | ExecutionException e) {
        }
        if (!closeCompleted) {
            pw.println("Failed to close ranging session. Aborting!");
            return;
        }
        pw.println("Ranging session closed");
    }

    private FiraRangingReconfigureParams buildFiraReconfigureParams() {
        FiraRangingReconfigureParams.Builder builder =
                new FiraRangingReconfigureParams.Builder();
        // defaults
        builder.setAction(MULTICAST_LIST_UPDATE_ACTION_ADD);

        String option = getNextOption();
        while (option != null) {
            if (option.equals("-a")) {
                String action = getNextArgRequired();
                if (action.equals("add")) {
                    builder.setAction(MULTICAST_LIST_UPDATE_ACTION_ADD);
                } else if (action.equals("delete")) {
                    builder.setAction(MULTICAST_LIST_UPDATE_ACTION_DELETE);
                } else {
                    throw new IllegalArgumentException("Unexpected action " + action);
                }
            }
            if (option.equals("-d")) {
                String[] destAddressesString = getNextArgRequired().split(",");
                List<UwbAddress> destAddresses = new ArrayList<>();
                for (String destAddressString : destAddressesString) {
                    destAddresses.add(UwbAddress.fromBytes(
                            ByteBuffer.allocate(SHORT_ADDRESS_BYTE_LENGTH)
                                    .putShort(Short.parseShort(destAddressString))
                                    .array()));
                }
                builder.setAddressList(destAddresses.toArray(new UwbAddress[0]));
            }
            if (option.equals("-s")) {
                String[] subSessionIdsString = getNextArgRequired().split(",");
                List<Integer> subSessionIds = new ArrayList<>();
                for (String subSessionIdString : subSessionIdsString) {
                    subSessionIds.add(Integer.parseInt(subSessionIdString));
                }
                builder.setSubSessionIdList(subSessionIds.stream().mapToInt(s -> s).toArray());
            }
            option = getNextOption();
        }
        // TODO: Add remaining params if needed.
        return builder.build();
    }

    private void reconfigureFiraRangingSession(PrintWriter pw) throws RemoteException {
        int sessionId = Integer.parseInt(getNextArgRequired());
        SessionInfo sessionInfo = sSessionIdToInfo.get(sessionId);
        if (sessionInfo == null) {
            pw.println("No active session with session ID: " + sessionId + " found");
            return;
        }
        FiraRangingReconfigureParams params = buildFiraReconfigureParams();

        mUwbService.reconfigureRanging(sessionInfo.sessionHandle, params.toBundle());
        boolean reconfigureCompleted = false;
        try {
            reconfigureCompleted = sessionInfo.rangingClosedFuture.get(
                    RANGE_CTL_TIMEOUT_MILLIS, MILLISECONDS);
        } catch (InterruptedException | CancellationException | TimeoutException
                | ExecutionException e) {
        }
        if (!reconfigureCompleted) {
            pw.println("Failed to reconfigure ranging session. Aborting!");
            return;
        }
        pw.println("Ranging session reconfigured");
    }

    @Override
    public int onCommand(String cmd) {
        // Treat no command as help command.
        if (cmd == null || cmd.equals("")) {
            cmd = "help";
        }
        // Explicit exclusion from root permission
        if (ArrayUtils.indexOf(NON_PRIVILEGED_COMMANDS, cmd) == -1) {
            final int uid = Binder.getCallingUid();
            if (uid != Process.ROOT_UID) {
                throw new SecurityException(
                        "Uid " + uid + " does not have access to " + cmd + " uwb command "
                                + "(or such command doesn't exist)");
            }
        }

        final PrintWriter pw = getOutPrintWriter();
        try {
            switch (cmd) {
                case "force-country-code": {
                    boolean enabled = getNextArgRequiredTrueOrFalse("enabled", "disabled");
                    if (enabled) {
                        String countryCode = getNextArgRequired();
                        if (!UwbCountryCode.isValid(countryCode)) {
                            pw.println("Invalid argument: Country code must be a 2-Character"
                                    + " alphanumeric code. But got countryCode " + countryCode
                                    + " instead");
                            return -1;
                        }
                        mUwbCountryCode.setOverrideCountryCode(countryCode);
                        return 0;
                    } else {
                        mUwbCountryCode.clearOverrideCountryCode();
                        return 0;
                    }
                }
                case "get-country-code":
                    pw.println("Uwb Country Code = " + mUwbCountryCode.getCountryCode());
                    return 0;
                case "status":
                    printStatus(pw);
                    return 0;
                case "enable-uwb":
                    mUwbService.setEnabled(true);
                    return 0;
                case "disable-uwb":
                    mUwbService.setEnabled(false);
                    return 0;
                case "start-fira-ranging-session":
                    startFiraRangingSession(pw);
                    return 0;
                case "start-ccc-ranging-session":
                    startCccRangingSession(pw);
                    return 0;
                case "reconfigure-fira-ranging-session":
                    reconfigureFiraRangingSession(pw);
                    return 0;
                case "get-ranging-session-reports": {
                    int sessionId = Integer.parseInt(getNextArgRequired());
                    SessionInfo sessionInfo = sSessionIdToInfo.get(sessionId);
                    if (sessionInfo == null) {
                        pw.println("No active session with session ID: " + sessionId + " found");
                        return -1;
                    }
                    pw.println("Last Ranging results:");
                    for (RangingReport rangingReport : sessionInfo.lastRangingReports) {
                        pw.println(rangingReport);
                    }
                    return 0;
                }
                case "get-all-ranging-session-reports": {
                    for (SessionInfo sessionInfo: sSessionIdToInfo.values()) {
                        pw.println("Last Ranging results for sessionId " + sessionInfo.sessionId
                                + ":");
                        for (RangingReport rangingReport : sessionInfo.lastRangingReports) {
                            pw.println(rangingReport);
                        }
                    }
                    return 0;
                }
                case "stop-ranging-session":
                    stopRangingSession(pw);
                    return 0;
                case "stop-all-ranging-sessions": {
                    for (int sessionId : sSessionIdToInfo.keySet()) {
                        stopRangingSession(pw, sessionId);
                    }
                    return 0;
                }
                case "get-specification-info": {
                    PersistableBundle bundle = mUwbService.getSpecificationInfo(null);
                    pw.println("Specification info: " + bundleToString(bundle));
                    return 0;
                }
                case "get-power-stats": {
                    PersistableBundle bundle = mUwbService.getSpecificationInfo(null);
                    GenericSpecificationParams params =
                            GenericSpecificationParams.fromBundle(bundle);
                    if (params == null) {
                        pw.println("Spec info is empty");
                        return -1;
                    }
                    if (params.hasPowerStatsSupport()) {
                        pw.println(mNativeUwbManager.getPowerStats());
                    } else {
                        pw.println("power stats query is not supported");
                    }
                    return 0;
                }
                default:
                    return handleDefaultCommands(cmd);
            }
        } catch (IllegalArgumentException e) {
            pw.println("Invalid args for " + cmd + ": ");
            e.printStackTrace(pw);
            return -1;
        } catch (Exception e) {
            pw.println("Exception while executing UwbShellCommand" + cmd + ": ");
            e.printStackTrace(pw);
            return -1;
        }
    }

    private boolean getNextArgRequiredTrueOrFalse(String trueString, String falseString)
            throws IllegalArgumentException {
        String nextArg = getNextArgRequired();
        if (trueString.equals(nextArg)) {
            return true;
        } else if (falseString.equals(nextArg)) {
            return false;
        } else {
            throw new IllegalArgumentException("Expected '" + trueString + "' or '" + falseString
                    + "' as next arg but got '" + nextArg + "'");
        }
    }

    private void printStatus(PrintWriter pw) throws RemoteException {
        boolean uwbEnabled =
                mUwbService.getAdapterState() != UwbManager.AdapterStateCallback.STATE_DISABLED;
        pw.println("Uwb is " + (uwbEnabled ? "enabled" : "disabled"));
    }

    private void onHelpNonPrivileged(PrintWriter pw) {
        pw.println("  status");
        pw.println("    Gets status of UWB stack");
        pw.println("  get-country-code");
        pw.println("    Gets country code as a two-letter string");
        pw.println("  enable-uwb");
        pw.println("    Toggle UWB on");
        pw.println("  disable-uwb");
        pw.println("    Toggle UWB off");
        pw.println("  start-fira-ranging-session"
                + " [-b](blocking call)"
                + " [-i <sessionId>](session-id)"
                + " [-c <channel>](channel)"
                + " [-t controller|controlee](device-type)"
                + " [-r initiator|responder](device-role)"
                + " [-a <deviceAddress>](device-address)"
                + " [-d <destAddress-1, destAddress-2,...>](dest-addresses)"
                + " [-u ds-twr|ss-twr|ds-twr-non-deferred|ss-twr-non-deferred](round-usage)"
                + " [-z <numRangeMrmts, numAoaAzimuthMrmts, numAoaElevationMrmts>"
                + "(interleaving-ratio)"
                + " [-e none|enabled|azimuth-only|elevation-only](aoa type)");
        pw.println("    Starts a FIRA ranging session with the provided params."
                + " Note: default behavior is to cache the latest ranging reports which can be"
                + " retrieved using |get-ranging-session-reports|");
        pw.println("  start-ccc-ranging-session"
                + " [-b](blocking call)"
                + " Ranging reports will be displayed on screen)"
                + " [-u 0|1](uwb-config)"
                + " [-p <tx>,<rx>](pulse-shape-combo)"
                + " [-i <sessionId>](session-id)"
                + " [-r <ran_multiplier>](ran-multiplier)"
                + " [-c <channel>](channel)"
                + " [-p <num-chaps-per-slot>](num-chaps-per-slot)"
                + " [-n <num-responder-nodes>](num-responder-nodes)"
                + " [-o <num-slots-per-round>](num-slots-per-round)"
                + " [-s <sync-code-index>](sync-code-index)"
                + " [-h none|continuous|adaptive](hopping-config-mode)"
                + " [-a default|aes](hopping-sequence)");
        pw.println("    Starts a CCC ranging session with the provided params."
                + " Note: default behavior is to cache the latest ranging reports which can be"
                + " retrieved using |get-ranging-session-reports|");
        pw.println("  reconfigure-fira-ranging-session"
                + " <sessionId>"
                + " [-a add|delete](action)"
                + " [-d <destAddress-1, destAddress-2,...>](dest-addresses)"
                + " [-s <subSessionId-1, subSessionId-2,...>](sub-sessionIds)");
        pw.println("  get-ranging-session-reports <sessionId>");
        pw.println("    Displays latest cached ranging reports for an ongoing ranging session");
        pw.println("  get-all-ranging-session-reports");
        pw.println("    Displays latest cached ranging reports for all ongoing ranging session");
        pw.println("  stop-ranging-session <sessionId>");
        pw.println("    Stops an ongoing ranging session");
        pw.println("  stop-all-ranging-sessions");
        pw.println("    Stops all ongoing ranging sessions");
        pw.println("  get-specification-info");
        pw.println("    Gets specification info from uwb chip");
    }

    private void onHelpPrivileged(PrintWriter pw) {
        pw.println("  force-country-code enabled <two-letter code> | disabled ");
        pw.println("    Sets country code to <two-letter code> or left for normal value");
        pw.println("  get-power-stats");
        pw.println("    Get power stats");
    }

    @Override
    public void onHelp() {
        final PrintWriter pw = getOutPrintWriter();
        pw.println("UWB (ultra wide-band) commands:");
        pw.println("  help or -h");
        pw.println("    Print this help text.");
        onHelpNonPrivileged(pw);
        if (Binder.getCallingUid() == Process.ROOT_UID) {
            onHelpPrivileged(pw);
        }
        pw.println();
    }

    @VisibleForTesting
    public void reset() {
        sSessionHandleIdNext = 0;
        sSessionIdToInfo.clear();
    }
}
