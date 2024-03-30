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
package com.android.server.uwb.secure.csml;

import static com.android.server.uwb.secure.csml.FiRaResponse.PROPRIETARY_RESPONSE_TAG;

import android.annotation.IntDef;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.server.uwb.secure.iso7816.ResponseApdu;
import com.android.server.uwb.secure.iso7816.TlvDatum;
import com.android.server.uwb.secure.iso7816.TlvDatum.Tag;
import com.android.server.uwb.secure.iso7816.TlvParser;
import com.android.server.uwb.util.DataTypeConversionUtil;
import com.android.server.uwb.util.ObjectIdentifier;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Response of Dispatch APDU, See CSML 1.0 - 8.2.2.14.2.9
 */
public class DispatchResponse extends FiRaResponse {
    @VisibleForTesting
    static final Tag STATUS_TAG = new Tag((byte) 0x80);
    @VisibleForTesting
    static final Tag DATA_TAG = new Tag((byte) 0x81);
    @VisibleForTesting
    static final Tag NOTIFICATION_TAG = new Tag((byte) 0xE1);
    @VisibleForTesting
    static final Tag NOTIFICATION_FORMAT_TAG = new Tag((byte) 0x80);
    @VisibleForTesting
    static final Tag NOTIFICATION_EVENT_ID_TAG = new Tag((byte) 0x81);
    @VisibleForTesting
    static final Tag NOTIFICATION_DATA_TAG = new Tag((byte) 0x82);

    @IntDef(prefix = { "TRANSACTION_STATUS_" }, value = {
            TRANSACTION_STATUS_NO_ERROR,
            TRANSACTION_STATUS_FORWARD_TO_REMOTE,
            TRANSACTION_STATUS_FORWARD_TO_HOST_APP,
            TRANSACTION_STATUS_WITH_ERROR,
            TRANSACTION_STATUS_NO_OP,
    })
    @Retention(RetentionPolicy.SOURCE)
    private @interface TransctionStatus {}

    private static final int TRANSACTION_STATUS_NO_ERROR = 0;
    private static final int TRANSACTION_STATUS_FORWARD_TO_REMOTE = 1;
    private static final int TRANSACTION_STATUS_FORWARD_TO_HOST_APP = 2;
    private static final int TRANSACTION_STATUS_WITH_ERROR = 3;
    private static final int TRANSACTION_STATUS_NO_OP = 4;

    @IntDef(prefix = { "NOTIFICATION_EVENT_ID_" }, value = {
            NOTIFICATION_EVENT_ID_ADF_SELECTED,
            NOTIFICATION_EVENT_ID_SECURE_CHANNEL_ESTABLISHED,
            NOTIFICATION_EVENT_ID_RDS_AVAILABLE,
            NOTIFICATION_EVENT_ID_SECURE_SESSION_ABORTED,
            NOTIFICATION_EVENT_ID_SEURE_SESSION_AUTO_TERMINATED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface NotificationEventId {}

    public static final int NOTIFICATION_EVENT_ID_ADF_SELECTED = 0;
    public static final int NOTIFICATION_EVENT_ID_SECURE_CHANNEL_ESTABLISHED = 1;
    public static final int NOTIFICATION_EVENT_ID_RDS_AVAILABLE = 2;
    public static final int NOTIFICATION_EVENT_ID_SECURE_SESSION_ABORTED = 3;
    public static final int NOTIFICATION_EVENT_ID_SEURE_SESSION_AUTO_TERMINATED = 4;

    /**
     * The base class of notification from the FiRa applet.
     */
    public static class Notification {
        @NotificationEventId
        public final int notificationEventId;

        protected Notification(@NotificationEventId int notificationEventId) {
            this.notificationEventId = notificationEventId;
        }
    }

    /**
     * The notification of ADF selected.
     */
    public static class AdfSelectedNotification extends Notification {
        @NonNull
        public final ObjectIdentifier adfOid;

        private AdfSelectedNotification(@NonNull ObjectIdentifier adfOid) {
            super(NOTIFICATION_EVENT_ID_ADF_SELECTED);

            this.adfOid = adfOid;
        }
    }

    /**
     * The notification of the secure channel established.
     */
    public static class SecureChannelEstablishedNotification extends Notification {
        private SecureChannelEstablishedNotification() {
            super(NOTIFICATION_EVENT_ID_SECURE_CHANNEL_ESTABLISHED);
        }
    }

    /**
     * The notification of the secure session aborted for internal error.
     */
    public static class SecureSessionAbortedNotification extends Notification {
        private SecureSessionAbortedNotification() {
            super(NOTIFICATION_EVENT_ID_SECURE_SESSION_ABORTED);
        }
    }

    /**
     * The notification of the secure session terminated automatically.
     */
    public static class SecureSessionAutoTerminatedNotification extends Notification {
        private SecureSessionAutoTerminatedNotification() {
            super(NOTIFICATION_EVENT_ID_SEURE_SESSION_AUTO_TERMINATED);
        }
    }

    /**
     * The notification of RDS available to be used.
     */
    public static class RdsAvailableNotification extends Notification {
        public final int sessionId;

        @NonNull
        public final Optional<byte[]> arbitraryData;

        private RdsAvailableNotification(
                int sessionId, @Nullable byte[] arbitraryData) {
            super(NOTIFICATION_EVENT_ID_RDS_AVAILABLE);
            this.sessionId = sessionId;
            if (arbitraryData == null) {
                this.arbitraryData = Optional.empty();
            } else {
                this.arbitraryData = Optional.of(arbitraryData);
            }
        }
    }

    @TransctionStatus
    private int mTransactionStatus = TRANSACTION_STATUS_NO_OP;

    /**
     * The data should be sent to the peer device or host app.
     */
    @NonNull
    private Optional<OutboundData> mOutboundData = Optional.empty();

    public Optional<OutboundData> getOutboundData() {
        return mOutboundData;
    }

    /**
     * The notifications got from the Dispatch response.
     */
    @NonNull
    public final List<Notification> notifications;

    private DispatchResponse(@NonNull ResponseApdu responseApdu) {
        super(responseApdu.getStatusWord());
        notifications = new ArrayList<Notification>();
        if (!isSuccess()) {
            return;
        }
        Map<Tag, List<TlvDatum>> proprietaryTlvsMap = TlvParser.parseTlvs(responseApdu);
        List<TlvDatum> proprietaryTlv = proprietaryTlvsMap.get(PROPRIETARY_RESPONSE_TAG);
        if (proprietaryTlv.size() == 0) {
            return;
        }

        Map<Tag, List<TlvDatum>> tlvsMap = TlvParser.parseTlvs(proprietaryTlv.get(0).value);

        notifications.addAll(parseNotification(tlvsMap.get(NOTIFICATION_TAG)));

        List<TlvDatum> statusTlvs = tlvsMap.get(STATUS_TAG);
        if (statusTlvs == null || statusTlvs.size() == 0) {
            // no status attached.
            return;
        }
        mTransactionStatus = parseTransctionStatus(statusTlvs.get(0).value);
        switch (mTransactionStatus) {
            case TRANSACTION_STATUS_NO_ERROR:
                notifications.add(new SecureSessionAutoTerminatedNotification());
                break;
            case TRANSACTION_STATUS_WITH_ERROR:
                notifications.add(new SecureSessionAbortedNotification());
                break;
            case TRANSACTION_STATUS_FORWARD_TO_HOST_APP:
                // fall through
            case TRANSACTION_STATUS_FORWARD_TO_REMOTE:
                List<TlvDatum> dataTlvs = tlvsMap.get(DATA_TAG);
                if (dataTlvs.size() == 0) {
                    break;
                }
                if (mTransactionStatus == TRANSACTION_STATUS_FORWARD_TO_HOST_APP) {
                    mOutboundData = Optional.of(
                            new OutboundData(OUTBOUND_TARGET_HOST_APP,
                                    dataTlvs.get(0).value));
                } else {
                    mOutboundData = Optional.of(
                            new OutboundData(OUTBOUND_TARGET_REMOTE,
                                    dataTlvs.get(0).value));
                }
                break;
            case TRANSACTION_STATUS_NO_OP:
                // fall through
            default:
                break;
        }
    }

    @TransctionStatus
    private int parseTransctionStatus(@Nullable byte[] status) {
        if (status == null || status.length < 1) {
            return TRANSACTION_STATUS_NO_OP;
        }
        switch (status[0]) {
            case (byte) 0x00:
                return TRANSACTION_STATUS_NO_ERROR;
            case (byte) 0x80:
                return TRANSACTION_STATUS_FORWARD_TO_REMOTE;
            case (byte) 0x81:
                return TRANSACTION_STATUS_FORWARD_TO_HOST_APP;
            case (byte) 0xFF:
                return TRANSACTION_STATUS_WITH_ERROR;
            default:
                return TRANSACTION_STATUS_NO_OP;
        }
    }

    // throw IllegalStateException
    @NonNull
    private List<Notification> parseNotification(
            @Nullable List<TlvDatum> notificationTlvs) {
        List<Notification> notificationList = new ArrayList<>();
        if (notificationTlvs == null || notificationTlvs.size() == 0) {
            return notificationList;
        }

        for (TlvDatum tlv : notificationTlvs) {
            Map<Tag, List<TlvDatum>> curTlvs = TlvParser.parseTlvs(tlv.value);
            List<TlvDatum> eventIdTlvs = curTlvs.get(NOTIFICATION_EVENT_ID_TAG);
            if (eventIdTlvs == null || eventIdTlvs.size() == 0) {
                throw new IllegalStateException("Notification event ID is not available.");
            }
            byte[] eventIdValue = eventIdTlvs.get(0).value;
            if (eventIdValue == null || eventIdValue.length == 0) {
                throw new IllegalStateException("Notification event ID value is not available.");
            }
            switch (eventIdValue[0]) {
                case (byte) 0x00:
                    // parse OID
                    List<TlvDatum> notificationDataTlvs = curTlvs.get(NOTIFICATION_DATA_TAG);
                    if (notificationDataTlvs == null || notificationDataTlvs.size() == 0) {
                        throw new IllegalStateException("Notification data - OID is not available");
                    }

                    byte[] adfOidBytes = notificationDataTlvs.get(0).value;
                    ObjectIdentifier adfOid =
                            ObjectIdentifier.fromBytes(adfOidBytes);

                    notificationList.add(new AdfSelectedNotification(adfOid));
                    break;
                case (byte) 0x01:
                    notificationList.add(new SecureChannelEstablishedNotification());
                    break;
                case (byte) 0x02:
                    // parse sessionId and arbitrary data
                    notificationDataTlvs = curTlvs.get(NOTIFICATION_DATA_TAG);
                    if (notificationDataTlvs == null || notificationDataTlvs.size() == 0) {
                        throw new IllegalStateException(
                                "RDS Notification data - sessionId is not available");
                    }
                    byte[] payload = notificationDataTlvs.get(0).value;
                    if (payload == null || payload.length < 2 || payload.length < 1 + payload[0]) {
                        throw new IllegalStateException(
                                "RDS Notificaition data - bad payload");
                    }
                    int sessionIdLen = payload[0];
                    byte[] sessionId = new byte[sessionIdLen];
                    System.arraycopy(payload, 1, sessionId, 0, sessionIdLen);

                    byte[] arbitratryData = null;
                    int arbitratryDataOffset = sessionIdLen + 1;
                    if (payload.length > arbitratryDataOffset) {
                        int arbitratryDataLen = payload[arbitratryDataOffset];
                        if (payload.length != 2 + sessionIdLen + arbitratryDataLen) {
                            // ignore the arbitrary data
                            arbitratryData = null;
                        } else {
                            arbitratryData = new byte[arbitratryDataLen];
                            System.arraycopy(payload, arbitratryDataOffset + 1,
                                    arbitratryData, 0, arbitratryDataLen);
                        }
                    }

                    notificationList.add(
                            new RdsAvailableNotification(
                                    DataTypeConversionUtil.arbitraryByteArrayToI32(sessionId),
                                    arbitratryData));
                    break;
                default:
            }
        }

        return notificationList;
    }

    /**
     * Parse the response of InitiateTractionCommand.
     */
    @NonNull
    public static DispatchResponse fromResponseApdu(@NonNull ResponseApdu responseApdu) {
        return new DispatchResponse(responseApdu);
    }

    @IntDef(prefix = { "OUTBOUND_TARGET_" }, value = {
            OUTBOUND_TARGET_HOST_APP,
            OUTBOUND_TARGET_REMOTE,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface OutboundTarget {}

    public static final int OUTBOUND_TARGET_HOST_APP = 0;
    public static final int OUTBOUND_TARGET_REMOTE = 1;

    /**
     * The outbound data from the DispatchResponse.
     */
    public static class OutboundData {
        @OutboundTarget
        public final int target;
        public final byte[] data;

        private OutboundData(@OutboundTarget int target, byte[] data) {
            this.target = target;
            this.data = data;
        }
    }
}
