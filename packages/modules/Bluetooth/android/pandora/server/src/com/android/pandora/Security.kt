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
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.ACTION_PAIRING_REQUEST
import android.bluetooth.BluetoothDevice.BOND_BONDED
import android.bluetooth.BluetoothDevice.BOND_NONE
import android.bluetooth.BluetoothDevice.DEVICE_TYPE_CLASSIC
import android.bluetooth.BluetoothDevice.DEVICE_TYPE_LE
import android.bluetooth.BluetoothDevice.EXTRA_PAIRING_VARIANT
import android.bluetooth.BluetoothDevice.TRANSPORT_BREDR
import android.bluetooth.BluetoothDevice.TRANSPORT_LE
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.google.protobuf.ByteString
import com.google.protobuf.Empty
import io.grpc.Status
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import pandora.HostProto.*
import pandora.SecurityGrpc.SecurityImplBase
import pandora.SecurityProto.*
import pandora.SecurityProto.LESecurityLevel.LE_LEVEL1
import pandora.SecurityProto.LESecurityLevel.LE_LEVEL2
import pandora.SecurityProto.LESecurityLevel.LE_LEVEL3
import pandora.SecurityProto.LESecurityLevel.LE_LEVEL4
import pandora.SecurityProto.SecurityLevel.LEVEL0
import pandora.SecurityProto.SecurityLevel.LEVEL1
import pandora.SecurityProto.SecurityLevel.LEVEL2
import pandora.SecurityProto.SecurityLevel.LEVEL3

private const val TAG = "PandoraSecurity"

@kotlinx.coroutines.ExperimentalCoroutinesApi
class Security(private val context: Context) : SecurityImplBase() {

  private val globalScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
  private val flow: Flow<Intent>

  private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)!!
  private val bluetoothAdapter = bluetoothManager.adapter

  var manuallyConfirm = false

  init {
    val intentFilter = IntentFilter()
    intentFilter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
    intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)

    flow = intentFlow(context, intentFilter).shareIn(globalScope, SharingStarted.Eagerly)
  }

  fun deinit() {
    globalScope.cancel()
  }

  override fun secure(request: SecureRequest, responseObserver: StreamObserver<SecureResponse>) {
    grpcUnary(globalScope, responseObserver) {
      val bluetoothDevice = request.connection.toBluetoothDevice(bluetoothAdapter)
      val transport = request.connection.transport
      Log.i(TAG, "secure: $bluetoothDevice transport: $transport")
      var reached =
        when (transport) {
          TRANSPORT_LE -> {
            check(request.getLevelCase() == SecureRequest.LevelCase.LE);
            val level = request.le
            if (level == LE_LEVEL1) true
            if (level == LE_LEVEL4) throw Status.UNKNOWN.asException()
            false
          }
          TRANSPORT_BREDR -> {
            check(request.getLevelCase() == SecureRequest.LevelCase.CLASSIC)
            val level = request.classic
            if (level == LEVEL0) true
            if (level >= LEVEL3) throw Status.UNKNOWN.asException()
            false
          }
          else -> throw Status.UNKNOWN.asException()
        }
      if (!reached) {
        bluetoothDevice.createBond(transport)
        val bondState =
          flow
            .filter { it.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED }
            .filter { it.getBluetoothDeviceExtra() == bluetoothDevice }
            .map { it.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothAdapter.ERROR) }
            .filter { it == BOND_BONDED || it == BOND_NONE }
            .first()
        val isEncrypted = bluetoothDevice.isEncrypted()
        reached =
          when (transport) {
            TRANSPORT_LE -> {
              val level = request.le
              when (level) {
                LE_LEVEL2 -> isEncrypted
                LE_LEVEL3 -> isEncrypted && bondState == BOND_BONDED
                else -> throw Status.UNKNOWN.asException()
              }
            }
            TRANSPORT_BREDR -> {
              val level = request.classic
              when (level) {
                LEVEL1 -> !isEncrypted || bondState == BOND_BONDED
                LEVEL2 -> isEncrypted && bondState == BOND_BONDED
                else -> throw Status.UNKNOWN.asException()
              }
            }
            else -> throw Status.UNKNOWN.asException()
          }
      }
      val secureResponseBuilder = SecureResponse.newBuilder()
      if (reached) secureResponseBuilder.setSuccess(Empty.getDefaultInstance())
      else secureResponseBuilder.setNotReached(Empty.getDefaultInstance())
      secureResponseBuilder.build()
    }
  }

  override fun onPairing(
    responseObserver: StreamObserver<PairingEvent>
  ): StreamObserver<PairingEventAnswer> =
    grpcBidirectionalStream(globalScope, responseObserver) {
      Log.i(TAG, "OnPairing: Starting stream")
      manuallyConfirm = true
      it
        .map { answer ->
          Log.i(
            TAG,
            "OnPairing: Handling PairingEventAnswer ${answer.answerCase} for device ${answer.event.address}"
          )
          val device = answer.event.address.toBluetoothDevice(bluetoothAdapter)
          when (answer.answerCase!!) {
            PairingEventAnswer.AnswerCase.CONFIRM -> device.setPairingConfirmation(true)
            PairingEventAnswer.AnswerCase.PASSKEY ->
              device.setPin(answer.passkey.toString().toByteArray())
            PairingEventAnswer.AnswerCase.PIN -> device.setPin(answer.pin.toByteArray())
            PairingEventAnswer.AnswerCase.ANSWER_NOT_SET -> error("unexpected pairing answer type")
          }
        }
        .launchIn(this)

      flow
        .filter { intent -> intent.action == ACTION_PAIRING_REQUEST }
        .map { intent ->
          val device = intent.getBluetoothDeviceExtra()
          val variant = intent.getIntExtra(EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR)
          Log.i(TAG, "OnPairing: Handling PairingEvent ${variant} for device ${device.address}")
          val eventBuilder = PairingEvent.newBuilder().setAddress(device.toByteString())
          when (variant) {
            // SSP / LE Just Works
            BluetoothDevice.PAIRING_VARIANT_CONSENT ->
              eventBuilder.justWorks = Empty.getDefaultInstance()

            // SSP / LE Numeric Comparison
            BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION ->
              eventBuilder.numericComparison =
                intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, BluetoothDevice.ERROR)
            BluetoothDevice.PAIRING_VARIANT_DISPLAY_PASSKEY -> {
              val passkey =
                intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, BluetoothDevice.ERROR)
              Log.i(TAG, "OnPairing: passkey=${passkey}")
              eventBuilder.passkeyEntryNotification = passkey
            }

            // Out-Of-Band not currently supported
            BluetoothDevice.PAIRING_VARIANT_OOB_CONSENT ->
              error("Received OOB pairing confirmation (UNSUPPORTED)")

            // Legacy PIN entry, or LE legacy passkey entry, depending on transport
            BluetoothDevice.PAIRING_VARIANT_PIN ->
              when (device.type) {
                DEVICE_TYPE_CLASSIC -> eventBuilder.pinCodeRequest = Empty.getDefaultInstance()
                DEVICE_TYPE_LE -> eventBuilder.passkeyEntryRequest = Empty.getDefaultInstance()
                else ->
                  error(
                    "cannot determine pairing variant, since transport is unknown: ${device.type}"
                  )
              }
            BluetoothDevice.PAIRING_VARIANT_PIN_16_DIGITS ->
              eventBuilder.pinCodeRequest = Empty.getDefaultInstance()

            // Legacy PIN entry or LE legacy passkey entry, except we just generate the PIN in
            // the
            // stack and display it to the user for convenience
            BluetoothDevice.PAIRING_VARIANT_DISPLAY_PIN -> {
              val passkey =
                intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, BluetoothDevice.ERROR)
              when (device.type) {
                DEVICE_TYPE_CLASSIC ->
                  eventBuilder.pinCodeNotification =
                    ByteString.copyFrom(passkey.toString().toByteArray())
                DEVICE_TYPE_LE -> eventBuilder.passkeyEntryNotification = passkey
                else -> error("cannot determine pairing variant, since transport is unknown")
              }
            }
            else -> {
              error("Received unknown pairing variant $variant")
            }
          }
          eventBuilder.build()
        }
    }
}
