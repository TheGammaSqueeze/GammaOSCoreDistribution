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

package com.android.bluetooth.tbs;

import static org.mockito.Mockito.*;
import static org.mockito.AdditionalMatchers.*;

import android.bluetooth.*;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.IBluetoothManager;
import android.bluetooth.IBluetoothLeCallControl;
import android.bluetooth.IBluetoothLeCallControlCallback;
import android.content.Context;
import android.os.Looper;
import android.util.Pair;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ServiceTestRule;
import androidx.test.runner.AndroidJUnit4;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.AdapterService;
import com.google.common.primitives.Bytes;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class TbsGattTest {
    private static Context sContext;

    private BluetoothAdapter mAdapter;
    private BluetoothDevice mCurrentDevice;

    private Integer mCurrentCcid;
    private String mCurrentUci;
    private List<String> mCurrentUriSchemes;
    private int mCurrentFeatureFlags;
    private String mCurrentProviderName;
    private int mCurrentTechnology;

    private TbsGatt mTbsGatt;

    @Mock
    private AdapterService mAdapterService;
    @Mock
    private BluetoothGattServerProxy mMockGattServer;
    @Mock
    private TbsGatt.Callback mMockTbsGattCallback;

    @Rule
    public final ServiceTestRule mServiceRule = new ServiceTestRule();

    @Captor
    private ArgumentCaptor<BluetoothGattService> mGattServiceCaptor;

    @BeforeClass
    public static void setUpOnce() {
        sContext = getInstrumentation().getTargetContext();
    }

    @Before
    public void setUp() throws Exception {
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity();

        MockitoAnnotations.initMocks(this);

        TestUtils.setAdapterService(mAdapterService);
        mAdapter = BluetoothAdapter.getDefaultAdapter();

        doReturn(true).when(mMockGattServer).addService(any(BluetoothGattService.class));
        doReturn(true).when(mMockGattServer).open(any(BluetoothGattServerCallback.class));

        mTbsGatt = new TbsGatt(sContext);
        mTbsGatt.setBluetoothGattServerForTesting(mMockGattServer);

        mCurrentDevice = TestUtils.getTestDevice(mAdapter, 0);
    }

    @After
    public void tearDown() throws Exception {
        mCurrentDevice = null;
        mTbsGatt = null;
        TestUtils.clearAdapterService(mAdapterService);
    }

    private void prepareDefaultService() {
        mCurrentCcid = 122;
        mCurrentUci = "un" + mCurrentCcid.toString();
        mCurrentUriSchemes = new ArrayList<String>(Arrays.asList("tel"));
        mCurrentProviderName = "unknown";
        mCurrentTechnology = 0x00;

        Assert.assertTrue(mTbsGatt.init(mCurrentCcid, mCurrentUci, mCurrentUriSchemes, true, true,
                mCurrentProviderName, mCurrentTechnology, mMockTbsGattCallback));
        Assert.assertNotNull(mMockGattServer);

        verify(mMockGattServer).addService(mGattServiceCaptor.capture());
        Assert.assertNotNull(mMockGattServer);
    }

    private BluetoothGattCharacteristic getCharacteristic(UUID uuid) {
        BluetoothGattService service = mGattServiceCaptor.getValue();
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(uuid);
        Assert.assertNotNull(characteristic);

        return characteristic;
    }

    private void configureNotifications(BluetoothDevice device,
            BluetoothGattCharacteristic characteristic, boolean enable) {
        BluetoothGattDescriptor descriptor =
                characteristic.getDescriptor(TbsGatt.UUID_CLIENT_CHARACTERISTIC_CONFIGURATION);
        Assert.assertNotNull(descriptor);

        mTbsGatt.mGattServerCallback.onDescriptorWriteRequest(device, 1, descriptor, false, true, 0,
                enable ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        verify(mMockGattServer).sendResponse(eq(mCurrentDevice), eq(1),
                eq(BluetoothGatt.GATT_SUCCESS), eq(0), any());
        reset(mMockGattServer);
    }

    private void verifySetValue(BluetoothGattCharacteristic characteristic, Object value,
            boolean shouldNotify) {
        if (characteristic.getUuid().equals(TbsGatt.UUID_BEARER_PROVIDER_NAME)) {
            boolean valueChanged = !characteristic.getStringValue(0).equals((String) value);
            if (valueChanged) {
                Assert.assertTrue(mTbsGatt.setBearerProviderName((String) value));
            } else {
                Assert.assertFalse(mTbsGatt.setBearerProviderName((String) value));
            }
            Assert.assertEquals((String) value, characteristic.getStringValue(0));

        } else if (characteristic.getUuid().equals(TbsGatt.UUID_BEARER_TECHNOLOGY)) {
            boolean valueChanged = characteristic
                    .getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0) != (Integer) value;
            if (valueChanged) {
                Assert.assertTrue(mTbsGatt.setBearerTechnology((Integer) value));
            } else {
                Assert.assertFalse(mTbsGatt.setBearerTechnology((Integer) value));
            }
            Assert.assertEquals((Integer) value,
                    characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0));

        } else if (characteristic.getUuid()
                .equals(TbsGatt.UUID_BEARER_URI_SCHEMES_SUPPORTED_LIST)) {
            String valueString = String.join(",", (List<String>) value);
            boolean valueChanged = !characteristic.getStringValue(0).equals(valueString);
            if (valueChanged) {
                Assert.assertTrue(mTbsGatt.setBearerUriSchemesSupportedList((List<String>) value));
            } else {
                Assert.assertFalse(mTbsGatt.setBearerUriSchemesSupportedList((List<String>) value));
            }
            Assert.assertEquals(valueString, characteristic.getStringValue(0));

        } else if (characteristic.getUuid().equals(TbsGatt.UUID_STATUS_FLAGS)) {

            Pair<Integer, Boolean> flagStatePair = (Pair<Integer, Boolean>) value;
            switch (flagStatePair.first) {
                case TbsGatt.STATUS_FLAG_INBAND_RINGTONE_ENABLED:
                    if (flagStatePair.second) {
                        Assert.assertTrue(mTbsGatt.setInbandRingtoneFlag());
                    } else {
                        Assert.assertTrue(mTbsGatt.clearInbandRingtoneFlag());
                    }
                    break;

                case TbsGatt.STATUS_FLAG_SILENT_MODE_ENABLED:
                    if (flagStatePair.second) {
                        Assert.assertTrue(mTbsGatt.setSilentModeFlag());
                    } else {
                        Assert.assertTrue(mTbsGatt.clearSilentModeFlag());
                    }
                    break;

                default:
                    Assert.assertTrue(false);
            }

            if (flagStatePair.second) {
                Assert.assertTrue(
                        (characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0)
                                & flagStatePair.first) != 0);
            } else {
                Assert.assertTrue(
                        (characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0)
                                & flagStatePair.first) == 0);
            }

        } else if (characteristic.getUuid().equals(TbsGatt.UUID_CALL_STATE)) {
            Pair<Map<Integer, TbsCall>, byte[]> callsExpectedPacketPair =
                    (Pair<Map<Integer, TbsCall>, byte[]>) value;
            Assert.assertTrue(mTbsGatt.setCallState(callsExpectedPacketPair.first));
            Assert.assertTrue(
                    Arrays.equals(callsExpectedPacketPair.second, characteristic.getValue()));

        } else if (characteristic.getUuid().equals(TbsGatt.UUID_BEARER_LIST_CURRENT_CALLS)) {
            Pair<Map<Integer, TbsCall>, byte[]> callsExpectedPacketPair =
                    (Pair<Map<Integer, TbsCall>, byte[]>) value;
            Assert.assertTrue(mTbsGatt.setBearerListCurrentCalls(callsExpectedPacketPair.first));
            Assert.assertTrue(
                    Arrays.equals(callsExpectedPacketPair.second, characteristic.getValue()));

        } else if (characteristic.getUuid().equals(TbsGatt.UUID_TERMINATION_REASON)) {
            Pair<Integer, Integer> indexReasonPair = (Pair<Integer, Integer>) value;
            Assert.assertTrue(
                    mTbsGatt.setTerminationReason(indexReasonPair.first, indexReasonPair.second));
            Assert.assertTrue(
                    Arrays.equals(
                            new byte[] {(byte) indexReasonPair.first.byteValue(),
                                    indexReasonPair.second.byteValue()},
                            characteristic.getValue()));

        } else if (characteristic.getUuid().equals(TbsGatt.UUID_INCOMING_CALL)) {
            if (value == null) {
                Assert.assertTrue(mTbsGatt.clearIncomingCall());
                Assert.assertEquals(0, characteristic.getValue().length);
            } else {
                Pair<Integer, String> indexStrPair = (Pair<Integer, String>) value;
                Assert.assertTrue(
                        mTbsGatt.setIncomingCall(indexStrPair.first, indexStrPair.second));
                Assert.assertTrue(Arrays.equals(
                        Bytes.concat(new byte[] {(byte) indexStrPair.first.byteValue()},
                                indexStrPair.second.getBytes(StandardCharsets.UTF_8)),
                        characteristic.getValue()));
            }

        } else if (characteristic.getUuid().equals(TbsGatt.UUID_CALL_FRIENDLY_NAME)) {
            if (value == null) {
                Assert.assertTrue(mTbsGatt.clearFriendlyName());
                Assert.assertEquals(0, characteristic.getValue().length);
            } else {
                Pair<Integer, String> indexNamePair = (Pair<Integer, String>) value;
                Assert.assertTrue(
                        mTbsGatt.setCallFriendlyName(indexNamePair.first, indexNamePair.second));
                Assert.assertTrue(Arrays.equals(
                        Bytes.concat(new byte[] {(byte) indexNamePair.first.byteValue()},
                                indexNamePair.second.getBytes(StandardCharsets.UTF_8)),
                        characteristic.getValue()));
            }
        }

        if (shouldNotify) {
            verify(mMockGattServer).notifyCharacteristicChanged(eq(mCurrentDevice),
                    eq(characteristic), eq(false));
        } else {
            verify(mMockGattServer, times(0)).notifyCharacteristicChanged(any(), any(),
                    anyBoolean());
        }

        reset(mMockGattServer);
    }

    @Test
    public void testSetBearerProviderName() {
        prepareDefaultService();
        BluetoothGattCharacteristic characteristic =
                getCharacteristic(TbsGatt.UUID_BEARER_PROVIDER_NAME);

        // Check with notifications enabled
        configureNotifications(mCurrentDevice, characteristic, true);
        verifySetValue(characteristic, "providerName2", true);

        // Check with notifications disabled
        configureNotifications(mCurrentDevice, characteristic, false);
        verifySetValue(characteristic, "providerName3", false);
    }

    @Test
    public void testSetBearerTechnology() {
        prepareDefaultService();
        BluetoothGattCharacteristic characteristic =
                getCharacteristic(TbsGatt.UUID_BEARER_TECHNOLOGY);

        // Check with notifications enabled
        configureNotifications(mCurrentDevice, characteristic, true);
        verifySetValue(characteristic, 0x04, true);

        // Check with notifications disabled
        configureNotifications(mCurrentDevice, characteristic, false);
        verifySetValue(characteristic, 0x05, false);
    }

    @Test
    public void testSetUriSchemes() {
        prepareDefaultService();
        BluetoothGattCharacteristic characteristic =
                getCharacteristic(TbsGatt.UUID_BEARER_URI_SCHEMES_SUPPORTED_LIST);

        // Check with notifications enabled
        configureNotifications(mCurrentDevice, characteristic, true);
        verifySetValue(characteristic, new ArrayList<>(Arrays.asList("uri2", "uri3")), true);

        // Check with notifications disabled
        configureNotifications(mCurrentDevice, characteristic, false);
        verifySetValue(characteristic, new ArrayList<>(Arrays.asList("uri4", "uri5")), false);
    }

    @Test
    public void testSetCurrentCallList() {
        prepareDefaultService();
        BluetoothGattCharacteristic characteristic =
                getCharacteristic(TbsGatt.UUID_BEARER_LIST_CURRENT_CALLS);

        // Check with notifications enabled
        configureNotifications(mCurrentDevice, characteristic, true);
        Map<Integer, TbsCall> callsMap = new TreeMap<>();
        callsMap.put(0x0A, TbsCall.create(
                new BluetoothLeCall(UUID.randomUUID(), "tel:123456789", "John Doe", 0x03, 0x00)));
        byte[] packetExpected = new byte[] {
                // First call
                (byte) 0x10, // Length of this entry (incl. URI length, excl. this length field
                             // byte)
                0x0A, // Call index
                0x03, // Active call state
                0x00, // Bit0:0-incoming,1-outgoing | Bit1:0-not-withheld,1-withheld |
                      // Bit2:0-provided-by-netw.,1-withheld-by-netw.
                0x74, 0x65, 0x6c, 0x3a, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39,
                // URI: tel:123456789
        };
        verifySetValue(characteristic,
                new Pair<Map<Integer, TbsCall>, byte[]>(callsMap, packetExpected), true);

        // Check with notifications disabled
        configureNotifications(mCurrentDevice, characteristic, false);
        callsMap.put(0x0B, TbsCall.create(new BluetoothLeCall(UUID.randomUUID(), "tel:987654321",
                "Kate", 0x01, BluetoothLeCall.FLAG_OUTGOING_CALL)));
        packetExpected = new byte[] {
                // First call
                (byte) 0x10, // Length of this entry (incl. URI length, excl. this length field
                             // byte)
                0x0A, // Call index
                0x03, // Active call state
                0x00, // Bit0:0-incoming,1-outgoing | Bit1:0-not-withheld,1-withheld |
                      // Bit2:0-provided-by-netw.,1-withheld-by-netw.
                0x74, 0x65, 0x6c, 0x3a, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39,
                // URI: tel:123456789
                // Second call
                (byte) 0x10, // Length of this entry (incl. URI length, excl. this length field
                             // byte)
                0x0B, // Call index
                0x01, // Dialing call state
                0x01, // Bit0:0-incoming,1-outgoing | Bit1:0-not-withheld,1-withheld |
                      // Bit2:0-provided-by-netw.,1-withheld-by-netw.
                0x74, 0x65, 0x6c, 0x3a, 0x39, 0x38, 0x37, 0x36, 0x35, 0x34, 0x33, 0x32, 0x31,
                // URI: tel:987654321
        };
        verifySetValue(characteristic,
                new Pair<Map<Integer, TbsCall>, byte[]>(callsMap, packetExpected), false);
    }

    @Test
    public void testSetStatusFlags() {
        prepareDefaultService();
        BluetoothGattCharacteristic characteristic = getCharacteristic(TbsGatt.UUID_STATUS_FLAGS);

        // Check with notifications enabled
        configureNotifications(mCurrentDevice, characteristic, true);
        verifySetValue(characteristic,
                new Pair<Integer, Boolean>(TbsGatt.STATUS_FLAG_INBAND_RINGTONE_ENABLED, true),
                true);
        verifySetValue(characteristic,
                new Pair<Integer, Boolean>(TbsGatt.STATUS_FLAG_SILENT_MODE_ENABLED, true), true);

        // Check with notifications disabled
        configureNotifications(mCurrentDevice, characteristic, false);
        verifySetValue(characteristic,
                new Pair<Integer, Boolean>(TbsGatt.STATUS_FLAG_SILENT_MODE_ENABLED, false), false);
    }

    @Test
    public void testSetCallState() {
        prepareDefaultService();
        BluetoothGattCharacteristic characteristic = getCharacteristic(TbsGatt.UUID_CALL_STATE);

        // Check with notifications enabled
        configureNotifications(mCurrentDevice, characteristic, true);
        byte[] packetExpected = new byte[] {(byte) 0x0A, // Call index
                0x03, // Active call state
                0x00, // Bit0:0-incoming,1-outgoing | Bit1:0-not-withheld,1-withheld |
                      // Bit2:0-provided-by-netw.,1-withheld-by-netw.
        };
        Map<Integer, TbsCall> callsMap = new TreeMap<>();
        callsMap.put(0x0A, TbsCall.create(
                new BluetoothLeCall(UUID.randomUUID(), "tel:123456789", "John Doe", 0x03, 0x00)));
        verifySetValue(characteristic,
                new Pair<Map<Integer, TbsCall>, byte[]>(callsMap, packetExpected), true);

        // Check with notifications disabled
        configureNotifications(mCurrentDevice, characteristic, false);
        packetExpected = new byte[] {(byte) 0x0A, // Call index
                0x03, // Active call state
                0x00, // Bit0:0-incoming,1-outgoing | Bit1:0-not-withheld,1-withheld |
                      // Bit2:0-provided-by-netw.,1-withheld-by-netw.
                (byte) 0x0B, // Call index
                0x04, // Locally Held call state
                0x00, // Bit0:0-incoming,1-outgoing | Bit1:0-not-withheld,1-withheld |
                      // Bit2:0-provided-by-netw.,1-withheld-by-netw.
        };
        callsMap.put(0x0B, TbsCall.create(
                new BluetoothLeCall(UUID.randomUUID(), "tel:987654321", "Kate", 0x04, 0x00)));
        verifySetValue(characteristic,
                new Pair<Map<Integer, TbsCall>, byte[]>(callsMap, packetExpected), false);
    }

    @Test
    public void testSetCallControlPointResult() {
        prepareDefaultService();
        BluetoothGattCharacteristic characteristic =
                getCharacteristic(TbsGatt.UUID_CALL_CONTROL_POINT);

        int requestedOpcode = TbsGatt.CALL_CONTROL_POINT_OPCODE_ACCEPT;
        int callIndex = 0x01;
        int result = TbsGatt.CALL_CONTROL_POINT_RESULT_SUCCESS;

        // Check with notifications enabled
        configureNotifications(mCurrentDevice, characteristic, true);
        mTbsGatt.setCallControlPointResult(mCurrentDevice, requestedOpcode, callIndex, result);
        Assert.assertTrue(Arrays.equals(characteristic.getValue(),
                new byte[] {(byte) (requestedOpcode & 0xff), (byte) (callIndex & 0xff),
                        (byte) (result & 0xff)}));
        verify(mMockGattServer, after(2000)).notifyCharacteristicChanged(eq(mCurrentDevice),
                eq(characteristic), eq(false));
        reset(mMockGattServer);

        callIndex = 0x02;

        // Check with notifications disabled
        configureNotifications(mCurrentDevice, characteristic, false);
        mTbsGatt.setCallControlPointResult(mCurrentDevice, requestedOpcode, callIndex, result);
        Assert.assertTrue(Arrays.equals(characteristic.getValue(),
                new byte[] {(byte) (requestedOpcode & 0xff), (byte) (callIndex & 0xff),
                        (byte) (result & 0xff)}));
        verify(mMockGattServer, after(2000).times(0)).notifyCharacteristicChanged(any(), any(),
                anyBoolean());
    }

    @Test
    public void testSetTerminationReason() {
        prepareDefaultService();
        BluetoothGattCharacteristic characteristic =
                getCharacteristic(TbsGatt.UUID_TERMINATION_REASON);

        // Check with no CCC configured
        verifySetValue(characteristic, new Pair<Integer, Integer>(0x0A, 0x01), false);

        // Check with notifications enabled
        configureNotifications(mCurrentDevice, characteristic, true);
        verifySetValue(characteristic, new Pair<Integer, Integer>(0x0B, 0x02), true);

        // Check with notifications disabled
        configureNotifications(mCurrentDevice, characteristic, false);
        verifySetValue(characteristic, new Pair<Integer, Integer>(0x0C, 0x02), false);
    }

    @Test
    public void testSetIncomingCall() {
        prepareDefaultService();
        BluetoothGattCharacteristic characteristic = getCharacteristic(TbsGatt.UUID_INCOMING_CALL);

        // Check with no CCC configured
        verifySetValue(characteristic, new Pair<Integer, String>(0x0A, "tel:123456789"), false);

        // Check with notifications enabled
        configureNotifications(mCurrentDevice, characteristic, true);
        verifySetValue(characteristic, new Pair<Integer, String>(0x0A, "tel:987654321"), true);

        // No incoming call (should not send any notification)
        verifySetValue(characteristic, null, false);

        // Check with notifications disabled
        configureNotifications(mCurrentDevice, characteristic, false);
        verifySetValue(characteristic, new Pair<Integer, String>(0x0A, "tel:123456789"), false);
    }

    @Test
    public void testSetFriendlyName() {
        prepareDefaultService();
        BluetoothGattCharacteristic characteristic =
                getCharacteristic(TbsGatt.UUID_CALL_FRIENDLY_NAME);

        // Check with no CCC configured
        verifySetValue(characteristic, new Pair<Integer, String>(0x0A, "PersonA"), false);

        // Check with notifications enabled
        configureNotifications(mCurrentDevice, characteristic, true);
        verifySetValue(characteristic, new Pair<Integer, String>(0x0B, "PersonB"), true);

        // Clear freindly name (should not send any notification)
        verifySetValue(characteristic, null, false);

        // Check with notifications disabled
        configureNotifications(mCurrentDevice, characteristic, false);
        verifySetValue(characteristic, new Pair<Integer, String>(0x0C, "PersonC"), false);
    }

    @Test
    public void testHandleControlPointRequest() {
        prepareDefaultService();
        BluetoothGattCharacteristic characteristic =
                getCharacteristic(TbsGatt.UUID_CALL_CONTROL_POINT);

        // Call the internal GATT callback as if peer device accepts the call
        byte[] value = new byte[] {0x00, /* opcode */ 0x0A, /* argument */ };
        mTbsGatt.mGattServerCallback.onCharacteristicWriteRequest(mCurrentDevice, 1, characteristic,
                false, false, 0, value);

        // Verify the higher layer callback call
        verify(mMockTbsGattCallback).onCallControlPointRequest(eq(mCurrentDevice), eq(0x00),
                aryEq(new byte[] {0x0A}));
    }

    @Test
    public void testClientCharacteristicConfiguration() {
        prepareDefaultService();

        BluetoothGattCharacteristic characteristic =
                getCharacteristic(TbsGatt.UUID_BEARER_TECHNOLOGY);
        BluetoothGattDescriptor descriptor =
                characteristic.getDescriptor(TbsGatt.UUID_CLIENT_CHARACTERISTIC_CONFIGURATION);

        // Check with no configuration
        mTbsGatt.mGattServerCallback.onDescriptorReadRequest(mCurrentDevice, 1, 0, descriptor);
        verify(mMockGattServer).sendResponse(eq(mCurrentDevice), eq(1),
                eq(BluetoothGatt.GATT_SUCCESS), eq(0),
                eq(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE));
        reset(mMockGattServer);

        // Check with notifications enabled
        configureNotifications(mCurrentDevice, characteristic, true);
        mTbsGatt.mGattServerCallback.onDescriptorReadRequest(mCurrentDevice, 1, 0, descriptor);
        verify(mMockGattServer).sendResponse(eq(mCurrentDevice), eq(1),
                eq(BluetoothGatt.GATT_SUCCESS), eq(0),
                eq(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE));
        reset(mMockGattServer);

        // Check with notifications disabled
        configureNotifications(mCurrentDevice, characteristic, false);
        mTbsGatt.mGattServerCallback.onDescriptorReadRequest(mCurrentDevice, 1, 0, descriptor);
        verify(mMockGattServer).sendResponse(eq(mCurrentDevice), eq(1),
                eq(BluetoothGatt.GATT_SUCCESS), eq(0),
                eq(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE));
    }
}
