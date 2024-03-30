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

package com.android.pandora

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAssignedNumbers
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.ADDRESS_TYPE_PUBLIC
import android.bluetooth.BluetoothDevice.BOND_BONDED
import android.bluetooth.BluetoothDevice.TRANSPORT_BREDR
import android.bluetooth.BluetoothDevice.TRANSPORT_LE
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.MacAddress
import android.os.ParcelUuid
import android.util.Log
import com.google.protobuf.Any
import com.google.protobuf.ByteString
import com.google.protobuf.Empty
import io.grpc.Status
import io.grpc.stub.StreamObserver
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import pandora.HostGrpc.HostImplBase
import pandora.HostProto.*

@kotlinx.coroutines.ExperimentalCoroutinesApi
class Host(
  private val context: Context,
  private val security: Security,
  private val server: Server
) : HostImplBase() {
  private val TAG = "PandoraHost"

  private val scope: CoroutineScope
  private val flow: Flow<Intent>

  private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)!!
  private val bluetoothAdapter = bluetoothManager.adapter

  private var connectability = ConnectabilityMode.NOT_CONNECTABLE
  private var discoverability = DiscoverabilityMode.NOT_DISCOVERABLE

  private val advertisers = mutableMapOf<UUID, AdvertiseCallback>()

  init {
    scope = CoroutineScope(Dispatchers.Default)

    // Add all intent actions to be listened.
    val intentFilter = IntentFilter()
    intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
    intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
    intentFilter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
    intentFilter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
    intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
    intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
    intentFilter.addAction(BluetoothDevice.ACTION_FOUND)

    // Creates a shared flow of intents that can be used in all methods in the coroutine scope.
    // This flow is started eagerly to make sure that the broadcast receiver is registered before
    // any function call. This flow is only cancelled when the corresponding scope is cancelled.
    flow = intentFlow(context, intentFilter).shareIn(scope, SharingStarted.Eagerly)
  }

  fun deinit() {
    scope.cancel()
  }

  private suspend fun rebootBluetooth() {
    Log.i(TAG, "rebootBluetooth")

    val stateFlow =
      flow
        .filter { it.getAction() == BluetoothAdapter.ACTION_STATE_CHANGED }
        .map { it.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) }

    if (bluetoothAdapter.isEnabled) {
      bluetoothAdapter.disable()
      stateFlow.filter { it == BluetoothAdapter.STATE_OFF }.first()
    }

    // TODO: b/234892968
    delay(3000L)

    bluetoothAdapter.enable()
    stateFlow.filter { it == BluetoothAdapter.STATE_ON }.first()
  }

  override fun factoryReset(request: Empty, responseObserver: StreamObserver<Empty>) {
    grpcUnary<Empty>(scope, responseObserver, 30) {
      Log.i(TAG, "factoryReset")

      val stateFlow =
      flow
        .filter { it.getAction() == BluetoothAdapter.ACTION_STATE_CHANGED }
        .map { it.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) }

      bluetoothAdapter.clearBluetooth()

      stateFlow.filter { it == BluetoothAdapter.STATE_ON }.first()
      Log.i(TAG, "Shutdown the gRPC Server")
      server.shutdown()

      // The last expression is the return value.
      Empty.getDefaultInstance()
    }
  }

  override fun reset(request: Empty, responseObserver: StreamObserver<Empty>) {
    grpcUnary<Empty>(scope, responseObserver) {
      Log.i(TAG, "reset")

      rebootBluetooth()

      Empty.getDefaultInstance()
    }
  }

  override fun readLocalAddress(
    request: Empty,
    responseObserver: StreamObserver<ReadLocalAddressResponse>
  ) {
    grpcUnary<ReadLocalAddressResponse>(scope, responseObserver) {
      Log.i(TAG, "readLocalAddress")
      val localMacAddress = MacAddress.fromString(bluetoothAdapter.getAddress())
      ReadLocalAddressResponse.newBuilder()
        .setAddress(ByteString.copyFrom(localMacAddress.toByteArray()))
        .build()
    }
  }

  private suspend fun waitPairingRequestIntent(bluetoothDevice: BluetoothDevice) {
    Log.i(TAG, "waitPairingRequestIntent: device=$bluetoothDevice")
    var pairingVariant =
      flow
        .filter { it.getAction() == BluetoothDevice.ACTION_PAIRING_REQUEST }
        .filter { it.getBluetoothDeviceExtra() == bluetoothDevice }
        .first()
        .getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR)

    val confirmationCases =
      intArrayOf(
        BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION,
        BluetoothDevice.PAIRING_VARIANT_CONSENT,
        BluetoothDevice.PAIRING_VARIANT_PIN,
      )

    if (pairingVariant in confirmationCases) {
      bluetoothDevice.setPairingConfirmation(true)
    }
  }

  private suspend fun waitConnectionIntent(bluetoothDevice: BluetoothDevice) {
    Log.i(TAG, "waitConnectionIntent: device=$bluetoothDevice")
    flow
      .filter { it.action == BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED }
      .filter { it.getBluetoothDeviceExtra() == bluetoothDevice }
      .map { it.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, BluetoothAdapter.ERROR) }
      .filter { it == BluetoothAdapter.STATE_CONNECTED }
      .first()
  }

  suspend fun waitBondIntent(bluetoothDevice: BluetoothDevice) {
    // We only wait for bonding to be completed since we only need the ACL connection to be
    // established with the peer device (on Android state connected is sent when all profiles
    // have been connected).
    Log.i(TAG, "waitBondIntent: device=$bluetoothDevice")
    flow
      .filter { it.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED }
      .filter { it.getBluetoothDeviceExtra() == bluetoothDevice }
      .map { it.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothAdapter.ERROR) }
      .filter { it == BOND_BONDED }
      .first()
  }

  private suspend fun acceptPairingAndAwaitBonded(bluetoothDevice: BluetoothDevice) {
    val acceptPairingJob = scope.launch { waitPairingRequestIntent(bluetoothDevice) }
    waitBondIntent(bluetoothDevice)
    if (acceptPairingJob.isActive) {
      acceptPairingJob.cancel()
    }
  }

  override fun waitConnection(
    request: WaitConnectionRequest,
    responseObserver: StreamObserver<WaitConnectionResponse>
  ) {
    grpcUnary(scope, responseObserver) {
      val bluetoothDevice = request.address.toBluetoothDevice(bluetoothAdapter)

      Log.i(TAG, "waitConnection: device=$bluetoothDevice")

      if (!bluetoothAdapter.isEnabled) {
        Log.e(TAG, "Bluetooth is not enabled, cannot waitConnection")
        throw Status.UNKNOWN.asException()
      }

      if (security.manuallyConfirm) {
        waitBondIntent(bluetoothDevice)
      } else {
        acceptPairingAndAwaitBonded(bluetoothDevice)
      }

      WaitConnectionResponse.newBuilder()
        .setConnection(bluetoothDevice.toConnection(TRANSPORT_BREDR))
        .build()
    }
  }

  override fun connect(request: ConnectRequest, responseObserver: StreamObserver<ConnectResponse>) {
    grpcUnary(scope, responseObserver) {
      val bluetoothDevice = request.address.toBluetoothDevice(bluetoothAdapter)

      Log.i(TAG, "connect: address=$bluetoothDevice")

      bluetoothAdapter.cancelDiscovery()

      if (!bluetoothDevice.isConnected()) {
        if (bluetoothDevice.bondState == BOND_BONDED) {
          // already bonded, just reconnect
          bluetoothDevice.connect()
          waitConnectionIntent(bluetoothDevice)
        } else {
          // need to bond
          bluetoothDevice.createBond()
          if (!security.manuallyConfirm) {
            acceptPairingAndAwaitBonded(bluetoothDevice)
          }
        }
      }

      ConnectResponse.newBuilder()
        .setConnection(bluetoothDevice.toConnection(TRANSPORT_BREDR))
        .build()
    }
  }

  override fun getConnection(
    request: GetConnectionRequest,
    responseObserver: StreamObserver<GetConnectionResponse>
  ) {
    grpcUnary(scope, responseObserver) {
      val bluetoothDevice = bluetoothAdapter.getRemoteDevice(request.address.toByteArray())
      if (bluetoothDevice.isConnected() && bluetoothDevice.type != BluetoothDevice.DEVICE_TYPE_LE) {
        GetConnectionResponse.newBuilder()
          .setConnection(bluetoothDevice.toConnection(TRANSPORT_BREDR))
          .build()
      } else {
        GetConnectionResponse.newBuilder().setPeerNotFound(Empty.getDefaultInstance()).build()
      }
    }
  }

  override fun disconnect(request: DisconnectRequest, responseObserver: StreamObserver<Empty>) {
    grpcUnary<Empty>(scope, responseObserver) {
      val bluetoothDevice = request.connection.toBluetoothDevice(bluetoothAdapter)
      Log.i(TAG, "disconnect: device=$bluetoothDevice")

      if (!bluetoothDevice.isConnected()) {
        Log.e(TAG, "Device is not connected, cannot disconnect")
        throw Status.UNKNOWN.asException()
      }

      when (request.connection.transport) {
        TRANSPORT_BREDR -> {
          Log.i(TAG, "disconnect BR_EDR")
          val connectionStateChangedFlow =
            flow
              .filter { it.getAction() == BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED }
              .filter { it.getBluetoothDeviceExtra() == bluetoothDevice }
              .map {
                it.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, BluetoothAdapter.ERROR)
              }

          bluetoothDevice.disconnect()
          connectionStateChangedFlow.filter { it == BluetoothAdapter.STATE_DISCONNECTED }.first()
        }
        TRANSPORT_LE -> {
          Log.i(TAG, "disconnect LE")
          val gattInstance = GattInstance.get(bluetoothDevice.address)

          if (gattInstance.isDisconnected()) {
            Log.e(TAG, "Device is not connected, cannot disconnect")
            throw Status.UNKNOWN.asException()
          }

          gattInstance.disconnectInstance()
          gattInstance.waitForState(BluetoothProfile.STATE_DISCONNECTED)
        }
        else -> {
          Log.e(TAG, "Device type UNKNOWN")
          throw Status.UNKNOWN.asException()
        }
      }

      Empty.getDefaultInstance()
    }
  }

  override fun connectLE(
    request: ConnectLERequest,
    responseObserver: StreamObserver<ConnectLEResponse>
  ) {
    grpcUnary<ConnectLEResponse>(scope, responseObserver) {
      if (request.getAddressCase() != ConnectLERequest.AddressCase.PUBLIC) {
        Log.e(TAG, "connectLE: public address not provided")
        throw Status.UNKNOWN.asException()
      }
      val address = request.public.decodeAsMacAddressToString()
      Log.i(TAG, "connectLE: $address")
      val bluetoothDevice = scanLeDevice(address)!!
      GattInstance(bluetoothDevice, TRANSPORT_LE, context)
        .waitForState(BluetoothProfile.STATE_CONNECTED)
      ConnectLEResponse.newBuilder()
        .setConnection(bluetoothDevice.toConnection(TRANSPORT_LE))
        .build()
    }
  }

  override fun getLEConnection(
    request: GetLEConnectionRequest,
    responseObserver: StreamObserver<GetLEConnectionResponse>,
  ) {
    grpcUnary<GetLEConnectionResponse>(scope, responseObserver) {
      if (request.getAddressCase() != GetLEConnectionRequest.AddressCase.PUBLIC) {
        Log.e(TAG, "connectLE: public address not provided")
        throw Status.UNKNOWN.asException()
      }
      val address = request.public.decodeAsMacAddressToString()
      Log.i(TAG, "getLEConnection: $address")
      val bluetoothDevice =
        bluetoothAdapter.getRemoteLeDevice(address, BluetoothDevice.ADDRESS_TYPE_PUBLIC)
      if (bluetoothDevice.isConnected()) {
        GetLEConnectionResponse.newBuilder()
          .setConnection(bluetoothDevice.toConnection(TRANSPORT_LE))
          .build()
      } else {
        Log.e(TAG, "Device: $bluetoothDevice is not connected")
        GetLEConnectionResponse.newBuilder().setPeerNotFound(Empty.getDefaultInstance()).build()
      }
    }
  }

  private fun scanLeDevice(address: String): BluetoothDevice? {
    Log.d(TAG, "scanLeDevice")
    var bluetoothDevice: BluetoothDevice? = null
    runBlocking {
      val flow = callbackFlow {
        val leScanCallback =
          object : ScanCallback() {
            override fun onScanFailed(errorCode: Int) {
              super.onScanFailed(errorCode)
              Log.d(TAG, "onScanFailed: errorCode: $errorCode")
              trySendBlocking(null)
            }
            override fun onScanResult(callbackType: Int, result: ScanResult) {
              super.onScanResult(callbackType, result)
              val deviceAddress = result.device.address
              if (deviceAddress == address) {
                Log.d(TAG, "found device address: $deviceAddress")
                trySendBlocking(result.device)
              }
            }
          }
        val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        bluetoothLeScanner?.startScan(leScanCallback) ?: run { trySendBlocking(null) }
        awaitClose { bluetoothLeScanner?.stopScan(leScanCallback) }
      }
      bluetoothDevice = flow.first()
    }
    return bluetoothDevice
  }

  override fun startAdvertising(
    request: StartAdvertisingRequest,
    responseObserver: StreamObserver<StartAdvertisingResponse>
  ) {
    Log.d(TAG, "startAdvertising")
    grpcUnary(scope, responseObserver) {
      val handle = UUID.randomUUID()

      callbackFlow {
          val callback =
            object : AdvertiseCallback() {
              override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                trySendBlocking(
                  StartAdvertisingResponse.newBuilder()
                    .setSet(
                      AdvertisingSet.newBuilder()
                        .setCookie(
                          Any.newBuilder()
                            .setValue(ByteString.copyFromUtf8(handle.toString()))
                            .build()
                        )
                        .build()
                    )
                    .build()
                )
              }
              override fun onStartFailure(errorCode: Int) {
                error("failed to start advertising")
              }
            }

          advertisers[handle] = callback

          val advertisingDataBuilder = AdvertiseData.Builder()
          val dataTypesRequest = request.data

          if (
            !dataTypesRequest.getIncompleteServiceClassUuids16List().isEmpty() or
              !dataTypesRequest.getIncompleteServiceClassUuids32List().isEmpty() or
              !dataTypesRequest.getIncompleteServiceClassUuids128List().isEmpty()
          ) {
            Log.e(TAG, "Incomplete Service Class Uuids not supported")
            throw Status.UNKNOWN.asException()
          }

          for (service_uuid in dataTypesRequest.getCompleteServiceClassUuids16List()) {
            advertisingDataBuilder.addServiceUuid(ParcelUuid.fromString(service_uuid))
          }
          for (service_uuid in dataTypesRequest.getCompleteServiceClassUuids32List()) {
            advertisingDataBuilder.addServiceUuid(ParcelUuid.fromString(service_uuid))
          }
          for (service_uuid in dataTypesRequest.getCompleteServiceClassUuids128List()) {
            advertisingDataBuilder.addServiceUuid(ParcelUuid.fromString(service_uuid))
          }

          advertisingDataBuilder
            .setIncludeDeviceName(
              dataTypesRequest.includeCompleteLocalName ||
                dataTypesRequest.includeShortenedLocalName
            )
            .setIncludeTxPowerLevel(dataTypesRequest.includeTxPowerLevel)
            .addManufacturerData(
              BluetoothAssignedNumbers.GOOGLE,
              dataTypesRequest.manufacturerSpecificData.toByteArray()
            )
          val advertisingData = advertisingDataBuilder.build()

          val ownAddressType =
            when (request.ownAddressType) {
              OwnAddressType.RESOLVABLE_OR_PUBLIC,
              OwnAddressType.PUBLIC -> AdvertisingSetParameters.ADDRESS_TYPE_PUBLIC
              OwnAddressType.RESOLVABLE_OR_RANDOM,
              OwnAddressType.RANDOM -> AdvertisingSetParameters.ADDRESS_TYPE_RANDOM
              else -> AdvertisingSetParameters.ADDRESS_TYPE_DEFAULT
            }
          val advertiseSettings =
            AdvertiseSettings.Builder()
              .setConnectable(request.connectable)
              .setOwnAddressType(ownAddressType)
              .build()

          bluetoothAdapter.bluetoothLeAdvertiser.startAdvertising(
            advertiseSettings,
            advertisingData,
            callback,
          )

          awaitClose { /* no-op */}
        }
        .first()
    }
  }

  override fun stopAdvertising(
    request: StopAdvertisingRequest,
    responseObserver: StreamObserver<Empty>
  ) {
    grpcUnary(scope, responseObserver) {
      Log.d(TAG, "stopAdvertising")
      val handle = UUID.fromString(request.set.cookie.value.toString())
      bluetoothAdapter.bluetoothLeAdvertiser.stopAdvertising(advertisers[handle])
      advertisers.remove(handle)
      Empty.getDefaultInstance()
    }
  }

  // TODO: Handle request parameters
  override fun scan(request: ScanRequest, responseObserver: StreamObserver<ScanningResponse>) {
    Log.d(TAG, "scan")
    grpcServerStream(scope, responseObserver) {
      callbackFlow {
        val callback =
          object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
              val bluetoothDevice = result.device
              val scanRecord = result.scanRecord
              val scanData = scanRecord.getAdvertisingDataMap()
              var dataTypesBuilder =
                DataTypes.newBuilder().setTxPowerLevel(scanRecord.getTxPowerLevel())
              scanData[ScanRecord.DATA_TYPE_LOCAL_NAME_SHORT]?.let {
                dataTypesBuilder.setShortenedLocalName(it.decodeToString())
              }
                ?: run { dataTypesBuilder.setIncludeShortenedLocalName(false) }
              scanData[ScanRecord.DATA_TYPE_LOCAL_NAME_COMPLETE]?.let {
                dataTypesBuilder.setCompleteLocalName(it.decodeToString())
              }
                ?: run { dataTypesBuilder.setIncludeCompleteLocalName(false) }
              // Flags DataTypes CSSv10 1.3 Flags
              val mode: DiscoverabilityMode =
                when (result.scanRecord.advertiseFlags and 0b11) {
                  0b01 -> DiscoverabilityMode.DISCOVERABLE_LIMITED
                  0b10 -> DiscoverabilityMode.DISCOVERABLE_GENERAL
                  else -> DiscoverabilityMode.NOT_DISCOVERABLE
                }
              dataTypesBuilder.setLeDiscoverabilityMode(mode)
              val primaryPhy =
                when (result.getPrimaryPhy()) {
                  BluetoothDevice.PHY_LE_1M -> PrimaryPhy.PRIMARY_1M
                  BluetoothDevice.PHY_LE_CODED -> PrimaryPhy.PRIMARY_CODED
                  else -> PrimaryPhy.UNRECOGNIZED
                }
              var scanningResponseBuilder =
                ScanningResponse.newBuilder()
                  .setLegacy(result.isLegacy())
                  .setConnectable(result.isConnectable())
                  .setSid(result.getPeriodicAdvertisingInterval())
                  .setPrimaryPhy(primaryPhy)
                  .setTxPower(result.getTxPower())
                  .setRssi(result.getRssi())
                  .setPeriodicAdvertisingInterval(result.getPeriodicAdvertisingInterval().toFloat())
                  .setData(dataTypesBuilder.build())
              when (bluetoothDevice.addressType) {
                BluetoothDevice.ADDRESS_TYPE_PUBLIC ->
                  scanningResponseBuilder.setPublic(bluetoothDevice.toByteString())
                BluetoothDevice.ADDRESS_TYPE_RANDOM ->
                  scanningResponseBuilder.setRandom(bluetoothDevice.toByteString())
                else ->
                  Log.w(TAG, "Address type UNKNOWN: ${bluetoothDevice.type} addr: $bluetoothDevice")
              }
              // TODO: Complete the missing field as needed, all the examples are here
              trySendBlocking(scanningResponseBuilder.build())
            }

            override fun onScanFailed(errorCode: Int) {
              error("scan failed")
            }
          }
        bluetoothAdapter.bluetoothLeScanner.startScan(callback)

        awaitClose { bluetoothAdapter.bluetoothLeScanner.stopScan(callback) }
      }
    }
  }

  override fun inquiry(request: Empty, responseObserver: StreamObserver<InquiryResponse>) {
    Log.d(TAG, "Inquiry")
    grpcServerStream(scope, responseObserver) {
      launch {
        try {
          bluetoothAdapter.startDiscovery()
          awaitCancellation()
        } finally {
          bluetoothAdapter.cancelDiscovery()
        }
      }
      flow
        .filter { it.action == BluetoothDevice.ACTION_FOUND }
        .map {
          val bluetoothDevice = it.getBluetoothDeviceExtra()
          Log.i(TAG, "Device found: $bluetoothDevice")
          InquiryResponse.newBuilder().setAddress(bluetoothDevice.toByteString()).build()
        }
    }
  }

  override fun setDiscoverabilityMode(
    request: SetDiscoverabilityModeRequest,
    responseObserver: StreamObserver<Empty>
  ) {
    Log.d(TAG, "setDiscoverabilityMode")
    grpcUnary(scope, responseObserver) {
      discoverability = request.mode!!

      val scanMode =
        when (discoverability) {
          DiscoverabilityMode.UNRECOGNIZED -> null
          DiscoverabilityMode.NOT_DISCOVERABLE ->
            if (connectability == ConnectabilityMode.CONNECTABLE) {
              BluetoothAdapter.SCAN_MODE_CONNECTABLE
            } else {
              BluetoothAdapter.SCAN_MODE_NONE
            }
          DiscoverabilityMode.DISCOVERABLE_LIMITED,
          DiscoverabilityMode.DISCOVERABLE_GENERAL ->
            BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE
        }

      if (scanMode != null) {
        bluetoothAdapter.setScanMode(scanMode)
      }

      if (discoverability == DiscoverabilityMode.DISCOVERABLE_LIMITED) {
        bluetoothAdapter.setDiscoverableTimeout(
          Duration.ofSeconds(120)
        ) // limited discoverability needs a timeout, 120s is Android default
      }
      Empty.getDefaultInstance()
    }
  }

  override fun setConnectabilityMode(
    request: SetConnectabilityModeRequest,
    responseObserver: StreamObserver<Empty>
  ) {
    grpcUnary(scope, responseObserver) {
      Log.d(TAG, "setConnectabilityMode")
      connectability = request.mode!!

      val scanMode =
        when (connectability) {
          ConnectabilityMode.UNRECOGNIZED -> null
          ConnectabilityMode.NOT_CONNECTABLE -> {
            BluetoothAdapter.SCAN_MODE_NONE
          }
          ConnectabilityMode.CONNECTABLE -> {
            if (
              discoverability == DiscoverabilityMode.DISCOVERABLE_LIMITED ||
                discoverability == DiscoverabilityMode.DISCOVERABLE_GENERAL
            ) {
              BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE
            } else {
              BluetoothAdapter.SCAN_MODE_CONNECTABLE
            }
          }
        }
      if (scanMode != null) {
        bluetoothAdapter.setScanMode(scanMode)
      }
      Empty.getDefaultInstance()
    }
  }

  override fun getRemoteName(
    request: GetRemoteNameRequest,
    responseObserver: StreamObserver<GetRemoteNameResponse>
  ) {
    grpcUnary(scope, responseObserver) {
      val device =
        if (request.hasConnection()) {
          request.connection.toBluetoothDevice(bluetoothAdapter)
        } else {
          request.address.toBluetoothDevice(bluetoothAdapter)
        }
      val deviceName = device.name
      if (deviceName == null) {
        GetRemoteNameResponse.newBuilder().setRemoteNotFound(Empty.getDefaultInstance()).build()
      } else {
        GetRemoteNameResponse.newBuilder().setName(deviceName).build()
      }
    }
  }
}
