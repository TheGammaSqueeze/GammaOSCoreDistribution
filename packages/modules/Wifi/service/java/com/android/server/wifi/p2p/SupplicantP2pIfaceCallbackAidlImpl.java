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

package com.android.server.wifi.p2p;

import android.annotation.NonNull;
import android.hardware.wifi.supplicant.ISupplicantP2pIfaceCallback;
import android.hardware.wifi.supplicant.P2pProvDiscStatusCode;
import android.hardware.wifi.supplicant.P2pStatusCode;
import android.hardware.wifi.supplicant.WpsConfigMethods;
import android.hardware.wifi.supplicant.WpsDevPasswordId;
import android.net.wifi.ScanResult;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pProvDiscEvent;
import android.net.wifi.p2p.WifiP2pWfdInfo;
import android.net.wifi.p2p.nsd.WifiP2pServiceResponse;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.util.HexDump;
import com.android.server.wifi.util.NativeUtil;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Class used for processing all P2P callbacks for the AIDL implementation.
 */
public class SupplicantP2pIfaceCallbackAidlImpl extends ISupplicantP2pIfaceCallback.Stub {
    private static final String TAG = "SupplicantP2pIfaceCallbackAidlImpl";
    private static boolean sVerboseLoggingEnabled = true;

    private final String mInterface;
    private final WifiP2pMonitor mMonitor;

    public SupplicantP2pIfaceCallbackAidlImpl(
            @NonNull String iface, @NonNull WifiP2pMonitor monitor) {
        mInterface = iface;
        mMonitor = monitor;
    }

    /**
     * Enable verbose logging for all sub modules.
     */
    public static void enableVerboseLogging(boolean verboseEnabled, boolean halVerboseEnabled) {
        sVerboseLoggingEnabled = verboseEnabled;
    }

    protected static void logd(String msg) {
        if (sVerboseLoggingEnabled) {
            Log.d(TAG, msg);
        }
    }

    /**
     * Used to indicate that a P2P device has been found.
     *
     * @param srcAddress MAC address of the device found. This must either
     *        be the P2P device address or the P2P interface address.
     * @param p2pDeviceAddress P2P device address.
     * @param primaryDeviceType Type of device. Refer to section B.1 of Wifi P2P
     *        Technical specification v1.2.
     * @param deviceName Name of the device.
     * @param configMethods Mask of WPS configuration methods supported by the
     *        device.
     * @param deviceCapabilities Refer to section 4.1.4 of Wifi P2P Technical
     *        specification v1.2.
     * @param groupCapabilities Refer to section 4.1.4 of Wifi P2P Technical
     *        specification v1.2.
     * @param wfdDeviceInfo WFD device info as described in section 5.1.2 of WFD
     *        technical specification v1.0.0.
     */
    @Override
    public void onDeviceFound(byte[] srcAddress, byte[] p2pDeviceAddress, byte[] primaryDeviceType,
            String deviceName, int configMethods, byte deviceCapabilities, int groupCapabilities,
            byte[] wfdDeviceInfo) {
        if (deviceName == null) {
            Log.e(TAG, "Missing device name.");
            return;
        }
        WifiP2pDevice device = new WifiP2pDevice();
        device.deviceName = deviceName;
        try {
            device.deviceAddress = NativeUtil.macAddressFromByteArray(p2pDeviceAddress);
        } catch (Exception e) {
            Log.e(TAG, "Could not decode device address.", e);
            return;
        }

        try {
            device.primaryDeviceType = NativeUtil.wpsDevTypeStringFromByteArray(primaryDeviceType);
        } catch (Exception e) {
            Log.e(TAG, "Could not encode device primary type.", e);
            return;
        }

        device.deviceCapability = deviceCapabilities;
        device.groupCapability = groupCapabilities;
        device.wpsConfigMethodsSupported = configMethods;
        device.status = WifiP2pDevice.AVAILABLE;

        if (wfdDeviceInfo != null && wfdDeviceInfo.length >= 6) {
            device.wfdInfo = new WifiP2pWfdInfo(
                    // Use & 0xFF to cast signed byte to unsigned int, otherwise
                    // sign extension will affect the bitwise operations
                    ((wfdDeviceInfo[0] & 0xFF) << 8) + (wfdDeviceInfo[1] & 0xFF),
                    ((wfdDeviceInfo[2] & 0xFF) << 8) + (wfdDeviceInfo[3] & 0xFF),
                    ((wfdDeviceInfo[4] & 0xFF) << 8) + (wfdDeviceInfo[5] & 0xFF));
        }

        logd("Device discovered on " + mInterface + ": " + device);
        mMonitor.broadcastP2pDeviceFound(mInterface, device);
    }

    /**
     * Used to indicate that a P2P device has been lost.
     *
     * @param p2pDeviceAddress P2P device address.
     */
    @Override
    public void onDeviceLost(byte[] p2pDeviceAddress) {
        WifiP2pDevice device = new WifiP2pDevice();

        try {
            device.deviceAddress = NativeUtil.macAddressFromByteArray(p2pDeviceAddress);
        } catch (Exception e) {
            Log.e(TAG, "Could not decode device address.", e);
            return;
        }

        device.status = WifiP2pDevice.UNAVAILABLE;

        logd("Device lost on " + mInterface + ": " + device);
        mMonitor.broadcastP2pDeviceLost(mInterface, device);
    }

    /**
     * Used to indicate the termination of P2P find operation.
     */
    @Override
    public void onFindStopped() {
        logd("Search stopped on " + mInterface);
        mMonitor.broadcastP2pFindStopped(mInterface);
    }

    /**
     * Used to indicate the reception of a P2P Group Owner negotiation request.
     *
     * @param srcAddress MAC address of the device that initiated the GO
     *        negotiation request.
     * @param passwordId Type of password.
     */
    @Override
    public void onGoNegotiationRequest(byte[] srcAddress, int passwordId) {
        WifiP2pConfig config = new WifiP2pConfig();

        try {
            config.deviceAddress = NativeUtil.macAddressFromByteArray(srcAddress);
        } catch (Exception e) {
            Log.e(TAG, "Could not decode device address.", e);
            return;
        }

        config.wps = new WpsInfo();

        switch (passwordId) {
            case WpsDevPasswordId.USER_SPECIFIED:
                config.wps.setup = WpsInfo.DISPLAY;
                break;
            case WpsDevPasswordId.PUSHBUTTON:
                config.wps.setup = WpsInfo.PBC;
                break;
            case WpsDevPasswordId.REGISTRAR_SPECIFIED:
                config.wps.setup = WpsInfo.KEYPAD;
                break;
            default:
                config.wps.setup = WpsInfo.PBC;
                break;
        }

        logd("Group Owner negotiation initiated on " + mInterface + ": " + config);
        mMonitor.broadcastP2pGoNegotiationRequest(mInterface, config);
    }

    /**
     * Used to indicate the completion of a P2P Group Owner negotiation request.
     *
     * @param status Status of the GO negotiation.
     */
    @Override
    public void onGoNegotiationCompleted(int status) {
        logd("Group Owner negotiation completed with status: " + status);
        WifiP2pServiceImpl.P2pStatus result = halStatusToP2pStatus(status);

        if (result == WifiP2pServiceImpl.P2pStatus.SUCCESS) {
            mMonitor.broadcastP2pGoNegotiationSuccess(mInterface);
        } else {
            mMonitor.broadcastP2pGoNegotiationFailure(mInterface, result);
        }
    }

    /**
     * Used to indicate a successful formation of a P2P group.
     */
    @Override
    public void onGroupFormationSuccess() {
        logd("Group formation successful on " + mInterface);
        mMonitor.broadcastP2pGroupFormationSuccess(mInterface);
    }

    /**
     * Used to indicate a failure to form a P2P group.
     *
     * @param failureReason Failure reason string for debug purposes.
     */
    @Override
    public void onGroupFormationFailure(String failureReason) {
        logd("Group formation failed on " + mInterface + ": " + failureReason);
        mMonitor.broadcastP2pGroupFormationFailure(mInterface, failureReason);
    }

    /**
     * Used to indicate the start of a P2P group.
     *
     * @param groupIfName Interface name of the group. (For ex: p2p-p2p0-1)
     * @param isGroupOwner Whether this device is owner of the group.
     * @param ssid SSID of the group.
     * @param frequency Frequency on which this group is created.
     * @param psk PSK used to secure the group.
     * @param passphrase PSK passphrase used to secure the group.
     * @param goDeviceAddress MAC Address of the owner of this group.
     * @param isPersistent Whether this group is persisted or not.
     */
    @Override
    public void onGroupStarted(String groupIfName, boolean isGroupOwner, byte[] ssid,
            int frequency, byte[] psk, String passphrase, byte[] goDeviceAddress,
            boolean isPersistent) {
        if (groupIfName == null) {
            Log.e(TAG, "Missing group interface name.");
            return;
        }

        logd("Group " + groupIfName + " started on " + mInterface);

        WifiP2pGroup group = new WifiP2pGroup();
        group.setInterface(groupIfName);

        try {
            String quotedSsid = NativeUtil.encodeSsid(
                    NativeUtil.byteArrayToArrayList(ssid));
            group.setNetworkName(NativeUtil.removeEnclosingQuotes(quotedSsid));
        } catch (Exception e) {
            Log.e(TAG, "Could not encode SSID.", e);
            return;
        }

        group.setFrequency(frequency);
        group.setIsGroupOwner(isGroupOwner);
        group.setPassphrase(passphrase);

        if (isPersistent) {
            group.setNetworkId(WifiP2pGroup.NETWORK_ID_PERSISTENT);
        } else {
            group.setNetworkId(WifiP2pGroup.NETWORK_ID_TEMPORARY);
        }

        WifiP2pDevice owner = new WifiP2pDevice();

        try {
            owner.deviceAddress = NativeUtil.macAddressFromByteArray(goDeviceAddress);
        } catch (Exception e) {
            Log.e(TAG, "Could not decode Group Owner address.", e);
            return;
        }

        group.setOwner(owner);
        mMonitor.broadcastP2pGroupStarted(mInterface, group);
    }

    /**
     * Used to indicate the removal of a P2P group.
     *
     * @param groupIfName Interface name of the group. (For ex: p2p-p2p0-1)
     * @param isGroupOwner Whether this device is owner of the group.
     */
    @Override
    public void onGroupRemoved(String groupIfName, boolean isGroupOwner) {
        if (groupIfName == null) {
            Log.e(TAG, "Missing group name.");
            return;
        }

        logd("Group " + groupIfName + " removed from " + mInterface);
        WifiP2pGroup group = new WifiP2pGroup();
        group.setInterface(groupIfName);
        group.setIsGroupOwner(isGroupOwner);
        mMonitor.broadcastP2pGroupRemoved(mInterface, group);
    }

    /**
     * Used to indicate the reception of a P2P invitation.
     *
     * @param srcAddress MAC address of the device that sent the invitation.
     * @param goDeviceAddress MAC Address of the owner of this group.
     * @param bssid Bssid of the group.
     * @param persistentNetworkId Persistent network Id of the group.
     * @param operatingFrequency Frequency on which the invitation was received.
     */
    @Override
    public void onInvitationReceived(byte[] srcAddress, byte[] goDeviceAddress,
            byte[] bssid, int persistentNetworkId, int operatingFrequency) {
        WifiP2pGroup group = new WifiP2pGroup();
        group.setNetworkId(persistentNetworkId);

        WifiP2pDevice client = new WifiP2pDevice();

        try {
            client.deviceAddress = NativeUtil.macAddressFromByteArray(srcAddress);
        } catch (Exception e) {
            Log.e(TAG, "Could not decode MAC address.", e);
            return;
        }

        group.addClient(client);

        WifiP2pDevice owner = new WifiP2pDevice();

        try {
            owner.deviceAddress = NativeUtil.macAddressFromByteArray(goDeviceAddress);
        } catch (Exception e) {
            Log.e(TAG, "Could not decode Group Owner MAC address.", e);
            return;
        }

        group.setOwner(owner);

        logd("Invitation received on " + mInterface + ": " + group);
        mMonitor.broadcastP2pInvitationReceived(mInterface, group);
    }

    /**
     * Used to indicate the result of the P2P invitation request.
     *
     * @param bssid Bssid of the group.
     * @param status Status of the invitation.
     */
    @Override
    public void onInvitationResult(byte[] bssid, int status) {
        logd("Invitation completed with status: " + status);
        mMonitor.broadcastP2pInvitationResult(mInterface, halStatusToP2pStatus(status));
    }

    private @WifiP2pMonitor.P2pProvDiscStatus int convertHalProvDiscStatusToFrameworkStatus(
            int status) {
        switch (status) {
            case P2pProvDiscStatusCode.SUCCESS:
                return WifiP2pMonitor.PROV_DISC_STATUS_SUCCESS;
            case P2pProvDiscStatusCode.TIMEOUT:
                return WifiP2pMonitor.PROV_DISC_STATUS_TIMEOUT;
            case P2pProvDiscStatusCode.REJECTED:
                return WifiP2pMonitor.PROV_DISC_STATUS_REJECTED;
            case P2pProvDiscStatusCode.TIMEOUT_JOIN:
                return WifiP2pMonitor.PROV_DISC_STATUS_TIMEOUT_JOIN;
            case P2pProvDiscStatusCode.INFO_UNAVAILABLE:
                return WifiP2pMonitor.PROV_DISC_STATUS_INFO_UNAVAILABLE;
            default:
                return WifiP2pMonitor.PROV_DISC_STATUS_UNKNOWN;
        }
    }

    /**
     * Used to indicate the completion of a P2P provision discovery request.
     *
     * @param p2pDeviceAddress P2P device address.
     * @param isRequest Whether we received or sent the provision discovery.
     * @param status Status of the provision discovery (SupplicantStatusCode).
     * @param configMethods Mask of WPS configuration methods supported.
     *                      Only one configMethod bit should be set per call.
     * @param generatedPin 8 digit pin generated.
     */
    @Override
    public void onProvisionDiscoveryCompleted(byte[] p2pDeviceAddress, boolean isRequest,
            byte status, int configMethods, String generatedPin) {
        logd("Provision discovery " + (isRequest ? "request" : "response")
                + " for WPS Config method: " + configMethods
                + " status: " + status);

        WifiP2pProvDiscEvent event = new WifiP2pProvDiscEvent();
        event.device = new WifiP2pDevice();

        try {
            event.device.deviceAddress = NativeUtil.macAddressFromByteArray(p2pDeviceAddress);
        } catch (Exception e) {
            Log.e(TAG, "Could not decode MAC address.", e);
            event.device.deviceAddress = null;
        }

        if (status != P2pProvDiscStatusCode.SUCCESS) {
            Log.e(TAG, "Provision discovery failed, status code: " + status);
            mMonitor.broadcastP2pProvisionDiscoveryFailure(mInterface,
                    convertHalProvDiscStatusToFrameworkStatus(status), event);
            return;
        }

        if (TextUtils.isEmpty(event.device.deviceAddress)) return;

        if ((configMethods & WpsConfigMethods.PUSHBUTTON) != 0) {
            if (isRequest) {
                event.event = WifiP2pProvDiscEvent.PBC_REQ;
                mMonitor.broadcastP2pProvisionDiscoveryPbcRequest(mInterface, event);
            } else {
                event.event = WifiP2pProvDiscEvent.PBC_RSP;
                mMonitor.broadcastP2pProvisionDiscoveryPbcResponse(mInterface, event);
            }
        } else if (!isRequest && (configMethods & WpsConfigMethods.KEYPAD) != 0) {
            event.event = WifiP2pProvDiscEvent.SHOW_PIN;
            event.pin = generatedPin;
            mMonitor.broadcastP2pProvisionDiscoveryShowPin(mInterface, event);
        } else if (!isRequest && (configMethods & WpsConfigMethods.DISPLAY) != 0) {
            event.event = WifiP2pProvDiscEvent.ENTER_PIN;
            mMonitor.broadcastP2pProvisionDiscoveryEnterPin(mInterface, event);
        } else if (isRequest && (configMethods & WpsConfigMethods.DISPLAY) != 0) {
            event.event = WifiP2pProvDiscEvent.SHOW_PIN;
            event.pin = generatedPin;
            mMonitor.broadcastP2pProvisionDiscoveryShowPin(mInterface, event);
        } else if (isRequest && (configMethods & WpsConfigMethods.KEYPAD) != 0) {
            event.event = WifiP2pProvDiscEvent.ENTER_PIN;
            mMonitor.broadcastP2pProvisionDiscoveryEnterPin(mInterface, event);
        } else {
            Log.e(TAG, "Unsupported config methods: " + configMethods);
        }
    }

    /**
     * Used to indicate the reception of a P2P service discovery response.
     *
     * @param srcAddress MAC address of the device that sent the service discovery.
     * @param updateIndicator Service update indicator. Refer to section 3.1.3 of
     *        Wifi P2P Technical specification v1.2.
     * @param tlvs Refer to section 3.1.3.1 of Wifi P2P Technical specification v1.2.
     */
    @Override
    public void onServiceDiscoveryResponse(byte[] srcAddress, char updateIndicator,
            byte[] tlvs) {
        List<WifiP2pServiceResponse> response = null;

        logd("Service discovery response received on " + mInterface);
        try {
            String srcAddressStr = NativeUtil.macAddressFromByteArray(srcAddress);
            // updateIndicator is not used
            response = WifiP2pServiceResponse.newInstance(srcAddressStr, tlvs);
        } catch (Exception e) {
            Log.e(TAG, "Could not process service discovery response.", e);
            return;
        }
        mMonitor.broadcastP2pServiceDiscoveryResponse(mInterface, response);
    }

    private WifiP2pDevice createStaEventDevice(byte[] srcAddress, byte[] p2pDeviceAddress) {
        WifiP2pDevice device = new WifiP2pDevice();
        byte[] deviceAddressBytes;
        // Legacy STAs may not supply a p2pDeviceAddress (signaled by a zero'd p2pDeviceAddress)
        // In this case, use srcAddress instead
        if (!Arrays.equals(NativeUtil.ANY_MAC_BYTES, p2pDeviceAddress)) {
            deviceAddressBytes = p2pDeviceAddress;
        } else {
            deviceAddressBytes = srcAddress;
        }
        try {
            device.deviceAddress = NativeUtil.macAddressFromByteArray(deviceAddressBytes);
        } catch (Exception e) {
            Log.e(TAG, "Could not decode MAC address", e);
            return null;
        }
        return device;
    }

    /**
     * Used to indicate when a STA device is connected to this device.
     *
     * @param srcAddress MAC address of the device that was authorized.
     * @param p2pDeviceAddress P2P device address.
     */
    @Override
    public void onStaAuthorized(byte[] srcAddress, byte[] p2pDeviceAddress) {
        logd("STA authorized on " + mInterface);
        WifiP2pDevice device = createStaEventDevice(srcAddress, p2pDeviceAddress);
        if (device == null) {
            return;
        }
        mMonitor.broadcastP2pApStaConnected(mInterface, device);
    }

    /**
     * Used to indicate when a STA device is disconnected from this device.
     *
     * @param srcAddress MAC address of the device that was deauthorized.
     * @param p2pDeviceAddress P2P device address.
     */
    @Override
    public void onStaDeauthorized(byte[] srcAddress, byte[] p2pDeviceAddress) {
        logd("STA deauthorized on " + mInterface);
        WifiP2pDevice device = createStaEventDevice(srcAddress, p2pDeviceAddress);
        if (device == null) {
            return;
        }
        mMonitor.broadcastP2pApStaDisconnected(mInterface, device);
    }

    /**
     * Used to indicate that a P2P WFD R2 device has been found.
     *
     * @param srcAddress MAC address of the device found. This must either
     *        be the P2P device address or the P2P interface address.
     * @param p2pDeviceAddress P2P device address.
     * @param primaryDeviceType Type of device. Refer to section B.1 of Wifi P2P
     *        Technical specification v1.2.
     * @param deviceName Name of the device.
     * @param configMethods Mask of WPS configuration methods supported by the
     *        device.
     * @param deviceCapabilities Refer to section 4.1.4 of Wifi P2P Technical
     *        specification v1.2.
     * @param groupCapabilities Refer to section 4.1.4 of Wifi P2P Technical
     *        specification v1.2.
     * @param wfdDeviceInfo WFD device info as described in section 5.1.2 of WFD
     *        technical specification v1.0.0.
     * @param wfdR2DeviceInfo WFD R2 device info as described in section 5.1.12 of WFD
     *        technical specification v2.1.
     */
    @Override
    public void onR2DeviceFound(byte[] srcAddress, byte[] p2pDeviceAddress,
            byte[] primaryDeviceType, String deviceName, int configMethods,
            byte deviceCapabilities, int groupCapabilities, byte[] wfdDeviceInfo,
            byte[] wfdR2DeviceInfo) {
        WifiP2pDevice device = new WifiP2pDevice();
        device.deviceName = deviceName;
        if (deviceName == null) {
            Log.e(TAG, "Missing device name.");
            return;
        }

        try {
            device.deviceAddress = NativeUtil.macAddressFromByteArray(p2pDeviceAddress);
        } catch (Exception e) {
            Log.e(TAG, "Could not decode device address.", e);
            return;
        }

        try {
            device.primaryDeviceType = NativeUtil.wpsDevTypeStringFromByteArray(primaryDeviceType);
        } catch (Exception e) {
            Log.e(TAG, "Could not encode device primary type.", e);
            return;
        }

        device.deviceCapability = deviceCapabilities;
        device.groupCapability = groupCapabilities;
        device.wpsConfigMethodsSupported = configMethods;
        device.status = WifiP2pDevice.AVAILABLE;

        if (wfdDeviceInfo != null && wfdDeviceInfo.length >= 6) {
            device.wfdInfo = new WifiP2pWfdInfo(
                    ((wfdDeviceInfo[0] & 0xFF) << 8) + (wfdDeviceInfo[1] & 0xFF),
                    ((wfdDeviceInfo[2] & 0xFF) << 8) + (wfdDeviceInfo[3] & 0xFF),
                    ((wfdDeviceInfo[4] & 0xFF) << 8) + (wfdDeviceInfo[5] & 0xFF));
        }
        if (wfdR2DeviceInfo != null && wfdR2DeviceInfo.length >= 2) {
            device.wfdInfo.setR2DeviceInfo(
                    ((wfdR2DeviceInfo[0] & 0xFF) << 8) + (wfdR2DeviceInfo[1] & 0xFF));
        }

        logd("R2 Device discovered on " + mInterface + ": "
                + device + " R2 Info:" + Arrays.toString(wfdR2DeviceInfo));
        mMonitor.broadcastP2pDeviceFound(mInterface, device);
    }

    /**
     * Used to indicate the frequency changed notification.
     *
     * @param groupIfName Interface name of the group.
     * @param frequency New operating frequency.
     */
    public void onGroupFrequencyChanged(String groupIfName, int frequency) {
        if (groupIfName == null) {
            Log.e(TAG, "Missing group interface name.");
            return;
        }

        logd("Frequency changed event on " + groupIfName + ". New frequency: " + frequency);

        mMonitor.broadcastP2pFrequencyChanged(mInterface, frequency);
    }

    /*
     * Used to indicate that a P2P device has been found.
     *
     * @param srcAddress MAC address of the device found. This must either
     *        be the P2P device address or the P2P interface address.
     * @param p2pDeviceAddress P2P device address.
     * @param primaryDeviceType Type of device. Refer to section B.1 of Wifi P2P
     *        Technical specification v1.2.
     * @param deviceName Name of the device.
     * @param configMethods Mask of WPS configuration methods supported by the
     *        device.
     * @param deviceCapabilities Refer to section 4.1.4 of Wifi P2P Technical
     *        specification v1.2.
     * @param groupCapabilities Refer to section 4.1.4 of Wifi P2P Technical
     *        specification v1.2.
     * @param wfdDeviceInfo WFD device info as described in section 5.1.2 of WFD
     *        technical specification v1.0.0.
     * @param wfdR2DeviceInfo WFD R2 device info as described in section 5.1.12 of WFD
     *        technical specification v2.1.
     * @param vendorElemBytes bytes of vendor-specific information elements.
     */
    @Override
    public void onDeviceFoundWithVendorElements(byte[] srcAddress, byte[] p2pDeviceAddress,
            byte[] primaryDeviceType, String deviceName, int configMethods,
            byte deviceCapabilities, int groupCapabilities, byte[] wfdDeviceInfo,
            byte[] wfdR2DeviceInfo, byte[] vendorElemBytes) {
        WifiP2pDevice device = new WifiP2pDevice();
        device.deviceName = deviceName;
        if (deviceName == null) {
            Log.e(TAG, "Missing device name.");
            return;
        }

        try {
            device.deviceAddress = NativeUtil.macAddressFromByteArray(p2pDeviceAddress);
        } catch (Exception e) {
            Log.e(TAG, "Could not decode device address.", e);
            return;
        }

        try {
            device.primaryDeviceType = NativeUtil.wpsDevTypeStringFromByteArray(primaryDeviceType);
        } catch (Exception e) {
            Log.e(TAG, "Could not encode device primary type.", e);
            return;
        }

        device.deviceCapability = deviceCapabilities;
        device.groupCapability = groupCapabilities;
        device.wpsConfigMethodsSupported = configMethods;
        device.status = WifiP2pDevice.AVAILABLE;

        if (wfdDeviceInfo != null && wfdDeviceInfo.length >= 6) {
            device.wfdInfo = new WifiP2pWfdInfo(
                    ((wfdDeviceInfo[0] & 0xFF) << 8) + (wfdDeviceInfo[1] & 0xFF),
                    ((wfdDeviceInfo[2] & 0xFF) << 8) + (wfdDeviceInfo[3] & 0xFF),
                    ((wfdDeviceInfo[4] & 0xFF) << 8) + (wfdDeviceInfo[5] & 0xFF));
        }
        if (wfdR2DeviceInfo != null && wfdR2DeviceInfo.length >= 2) {
            device.wfdInfo.setR2DeviceInfo(
                    ((wfdR2DeviceInfo[0] & 0xFF) << 8) + (wfdR2DeviceInfo[1] & 0xFF));
        }

        if (null != vendorElemBytes && vendorElemBytes.length > 0) {
            logd("Vendor Element Bytes: " + HexDump.dumpHexString(vendorElemBytes));
            List<ScanResult.InformationElement> vendorElements = new ArrayList<>();
            try {
                ByteArrayInputStream is = new ByteArrayInputStream(vendorElemBytes);
                int b;
                while ((b = is.read()) != -1) {
                    int id = b;
                    int len = is.read();
                    if (len == -1) break;
                    byte[] bytes = new byte[len];
                    int read = is.read(bytes, 0, len);
                    if (-1 == read || len != read) break;
                    if (id != ScanResult.InformationElement.EID_VSA) continue;
                    vendorElements.add(new ScanResult.InformationElement(id, 0, bytes));
                }
            } catch (Exception ex) {
                logd("Cannot parse vendor element bytes: " + ex);
                vendorElements = null;
            }
            device.setVendorElements(vendorElements);
        }

        logd("Device discovered on " + mInterface + ": " + device);
        mMonitor.broadcastP2pDeviceFound(mInterface, device);
    }

    private static WifiP2pServiceImpl.P2pStatus halStatusToP2pStatus(int status) {
        WifiP2pServiceImpl.P2pStatus result = WifiP2pServiceImpl.P2pStatus.UNKNOWN;

        switch (status) {
            case P2pStatusCode.SUCCESS:
            case P2pStatusCode.SUCCESS_DEFERRED:
                result = WifiP2pServiceImpl.P2pStatus.SUCCESS;
                break;

            case P2pStatusCode.FAIL_INFO_CURRENTLY_UNAVAILABLE:
                result = WifiP2pServiceImpl.P2pStatus.INFORMATION_IS_CURRENTLY_UNAVAILABLE;
                break;

            case P2pStatusCode.FAIL_INCOMPATIBLE_PARAMS:
                result = WifiP2pServiceImpl.P2pStatus.INCOMPATIBLE_PARAMETERS;
                break;

            case P2pStatusCode.FAIL_LIMIT_REACHED:
                result = WifiP2pServiceImpl.P2pStatus.LIMIT_REACHED;
                break;

            case P2pStatusCode.FAIL_INVALID_PARAMS:
                result = WifiP2pServiceImpl.P2pStatus.INVALID_PARAMETER;
                break;

            case P2pStatusCode.FAIL_UNABLE_TO_ACCOMMODATE:
                result = WifiP2pServiceImpl.P2pStatus.UNABLE_TO_ACCOMMODATE_REQUEST;
                break;

            case P2pStatusCode.FAIL_PREV_PROTOCOL_ERROR:
                result = WifiP2pServiceImpl.P2pStatus.PREVIOUS_PROTOCOL_ERROR;
                break;

            case P2pStatusCode.FAIL_NO_COMMON_CHANNELS:
                result = WifiP2pServiceImpl.P2pStatus.NO_COMMON_CHANNEL;
                break;

            case P2pStatusCode.FAIL_UNKNOWN_GROUP:
                result = WifiP2pServiceImpl.P2pStatus.UNKNOWN_P2P_GROUP;
                break;

            case P2pStatusCode.FAIL_BOTH_GO_INTENT_15:
                result = WifiP2pServiceImpl.P2pStatus.BOTH_GO_INTENT_15;
                break;

            case P2pStatusCode.FAIL_INCOMPATIBLE_PROV_METHOD:
                result = WifiP2pServiceImpl.P2pStatus.INCOMPATIBLE_PROVISIONING_METHOD;
                break;

            case P2pStatusCode.FAIL_REJECTED_BY_USER:
                result = WifiP2pServiceImpl.P2pStatus.REJECTED_BY_USER;
                break;
        }
        return result;
    }

    @Override
    public String getInterfaceHash() {
        return ISupplicantP2pIfaceCallback.HASH;
    }

    @Override
    public int getInterfaceVersion() {
        return ISupplicantP2pIfaceCallback.VERSION;
    }
}
