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

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.*
import android.util.Log
import io.grpc.Status
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import pandora.A2DPGrpc.A2DPImplBase
import pandora.A2dpProto.*

@kotlinx.coroutines.ExperimentalCoroutinesApi
class A2dp(val context: Context) : A2DPImplBase() {
  private val TAG = "PandoraA2dp"

  private val scope: CoroutineScope
  private val flow: Flow<Intent>

  private val audioManager = context.getSystemService(AudioManager::class.java)!!

  private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)!!
  private val bluetoothAdapter = bluetoothManager.adapter
  private val bluetoothA2dp = getProfileProxy<BluetoothA2dp>(context, BluetoothProfile.A2DP)

  private var audioTrack: AudioTrack? = null

  init {
    scope = CoroutineScope(Dispatchers.Default)
    val intentFilter = IntentFilter()
    intentFilter.addAction(BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED)
    intentFilter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)

    flow = intentFlow(context, intentFilter).shareIn(scope, SharingStarted.Eagerly)
  }

  fun deinit() {
    bluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP, bluetoothA2dp)
    scope.cancel()
  }

  override fun openSource(
    request: OpenSourceRequest,
    responseObserver: StreamObserver<OpenSourceResponse>
  ) {
    grpcUnary<OpenSourceResponse>(scope, responseObserver) {
      val device = request.connection.toBluetoothDevice(bluetoothAdapter)
      Log.i(TAG, "openSource: device=$device")

      if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
        Log.e(TAG, "Device is not bonded, cannot openSource")
        throw Status.UNKNOWN.asException()
      }

      if (bluetoothA2dp.getConnectionState(device) != BluetoothA2dp.STATE_CONNECTED) {
        bluetoothA2dp.connect(device)
        val state =
          flow
            .filter { it.getAction() == BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED }
            .filter { it.getBluetoothDeviceExtra() == device }
            .map { it.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothAdapter.ERROR) }
            .filter {
              it == BluetoothProfile.STATE_CONNECTED || it == BluetoothProfile.STATE_DISCONNECTED
            }
            .first()

        if (state == BluetoothProfile.STATE_DISCONNECTED) {
          Log.e(TAG, "openSource failed, A2DP has been disconnected")
          throw Status.UNKNOWN.asException()
        }
      }

      // TODO: b/234891800, AVDTP start request sometimes never sent if playback starts too early.
      delay(2000L)

      val source = Source.newBuilder().setConnection(request.connection).build()
      OpenSourceResponse.newBuilder().setSource(source).build()
    }
  }

  override fun waitSource(
    request: WaitSourceRequest,
    responseObserver: StreamObserver<WaitSourceResponse>
  ) {
    grpcUnary<WaitSourceResponse>(scope, responseObserver) {
      val device = request.connection.toBluetoothDevice(bluetoothAdapter)
      Log.i(TAG, "waitSource: device=$device")

      if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
        Log.e(TAG, "Device is not bonded, cannot openSource")
        throw Status.UNKNOWN.asException()
      }

      if (bluetoothA2dp.getConnectionState(device) != BluetoothA2dp.STATE_CONNECTED) {
        val state =
          flow
            .filter { it.getAction() == BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED }
            .filter { it.getBluetoothDeviceExtra() == device }
            .map { it.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothAdapter.ERROR) }
            .filter {
              it == BluetoothProfile.STATE_CONNECTED || it == BluetoothProfile.STATE_DISCONNECTED
            }
            .first()

        if (state == BluetoothProfile.STATE_DISCONNECTED) {
          Log.e(TAG, "waitSource failed, A2DP has been disconnected")
          throw Status.UNKNOWN.asException()
        }
      }

      // TODO: b/234891800, AVDTP start request sometimes never sent if playback starts too early.
      delay(2000L)

      val source = Source.newBuilder().setConnection(request.connection).build()
      WaitSourceResponse.newBuilder().setSource(source).build()
    }
  }

  override fun start(request: StartRequest, responseObserver: StreamObserver<StartResponse>) {
    grpcUnary<StartResponse>(scope, responseObserver) {
      if (audioTrack == null) {
        audioTrack = buildAudioTrack()
      }
      val device = request.source.connection.toBluetoothDevice(bluetoothAdapter)
      Log.i(TAG, "start: device=$device")

      if (bluetoothA2dp.getConnectionState(device) != BluetoothA2dp.STATE_CONNECTED) {
        Log.e(TAG, "Device is not connected, cannot start")
        throw Status.UNKNOWN.asException()
      }

      audioTrack!!.play()

      // If A2dp is not already playing, wait for it
      if (!bluetoothA2dp.isA2dpPlaying(device)) {
        flow
          .filter { it.getAction() == BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED }
          .filter { it.getBluetoothDeviceExtra() == device }
          .map { it.getIntExtra(BluetoothA2dp.EXTRA_STATE, BluetoothAdapter.ERROR) }
          .filter { it == BluetoothA2dp.STATE_PLAYING }
          .first()
      }
      StartResponse.getDefaultInstance()
    }
  }

  override fun suspend(request: SuspendRequest, responseObserver: StreamObserver<SuspendResponse>) {
    grpcUnary<SuspendResponse>(scope, responseObserver) {
      val device = request.source.connection.toBluetoothDevice(bluetoothAdapter)
      Log.i(TAG, "suspend: device=$device")

      if (bluetoothA2dp.getConnectionState(device) != BluetoothA2dp.STATE_CONNECTED) {
        Log.e(TAG, "Device is not connected, cannot suspend")
        throw Status.UNKNOWN.asException()
      }

      if (!bluetoothA2dp.isA2dpPlaying(device)) {
        Log.e(TAG, "Device is already suspended, cannot suspend")
        throw Status.UNKNOWN.asException()
      }

      val a2dpPlayingStateFlow =
        flow
          .filter { it.getAction() == BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED }
          .filter { it.getBluetoothDeviceExtra() == device }
          .map { it.getIntExtra(BluetoothA2dp.EXTRA_STATE, BluetoothAdapter.ERROR) }

      audioTrack!!.pause()
      a2dpPlayingStateFlow.filter { it == BluetoothA2dp.STATE_NOT_PLAYING }.first()
      SuspendResponse.getDefaultInstance()
    }
  }

  override fun isSuspended(
    request: IsSuspendedRequest,
    responseObserver: StreamObserver<IsSuspendedResponse>
  ) {
    grpcUnary<IsSuspendedResponse>(scope, responseObserver) {
      val device = request.source.connection.toBluetoothDevice(bluetoothAdapter)
      Log.i(TAG, "isSuspended: device=$device")

      if (bluetoothA2dp.getConnectionState(device) != BluetoothA2dp.STATE_CONNECTED) {
        Log.e(TAG, "Device is not connected, cannot get suspend state")
        throw Status.UNKNOWN.asException()
      }

      val isSuspended = bluetoothA2dp.isA2dpPlaying(device)
      IsSuspendedResponse.newBuilder().setIsSuspended(isSuspended).build()
    }
  }

  override fun close(request: CloseRequest, responseObserver: StreamObserver<CloseResponse>) {
    grpcUnary<CloseResponse>(scope, responseObserver) {
      val device = request.source.connection.toBluetoothDevice(bluetoothAdapter)
      Log.i(TAG, "close: device=$device")

      if (bluetoothA2dp.getConnectionState(device) != BluetoothA2dp.STATE_CONNECTED) {
        Log.e(TAG, "Device is not connected, cannot close")
        throw Status.UNKNOWN.asException()
      }

      val a2dpConnectionStateChangedFlow =
        flow
          .filter { it.getAction() == BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED }
          .filter { it.getBluetoothDeviceExtra() == device }
          .map { it.getIntExtra(BluetoothA2dp.EXTRA_STATE, BluetoothAdapter.ERROR) }

      bluetoothA2dp.disconnect(device)
      a2dpConnectionStateChangedFlow.filter { it == BluetoothA2dp.STATE_DISCONNECTED }.first()

      CloseResponse.getDefaultInstance()
    }
  }

  override fun playbackAudio(
    responseObserver: StreamObserver<PlaybackAudioResponse>
  ): StreamObserver<PlaybackAudioRequest> {
    Log.i(TAG, "playbackAudio")

    if (audioTrack!!.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
      responseObserver.onError(
        Status.UNKNOWN.withDescription("AudioTrack is not started").asException()
      )
    }

    // Volume is maxed out to avoid any amplitude modification of the provided audio data,
    // enabling the test runner to do comparisons between input and output audio signal.
    // Any volume modification should be done before providing the audio data.
    if (audioManager.isVolumeFixed) {
      Log.w(TAG, "Volume is fixed, cannot max out the volume")
    } else {
      val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
      if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) < maxVolume) {
        audioManager.setStreamVolume(
          AudioManager.STREAM_MUSIC,
          maxVolume,
          AudioManager.FLAG_SHOW_UI
        )
      }
    }

    return object : StreamObserver<PlaybackAudioRequest> {
      override fun onNext(request: PlaybackAudioRequest) {
        val data = request.data.toByteArray()
        val written = synchronized(audioTrack!!) { audioTrack!!.write(data, 0, data.size) }
        if (written != data.size) {
          responseObserver.onError(
            Status.UNKNOWN.withDescription("AudioTrack write failed").asException()
          )
        }
      }
      override fun onError(t: Throwable?) {
        Log.e(TAG, t.toString())
        responseObserver.onError(t)
      }
      override fun onCompleted() {
        responseObserver.onNext(PlaybackAudioResponse.getDefaultInstance())
        responseObserver.onCompleted()
      }
    }
  }

  override fun getAudioEncoding(
    request: GetAudioEncodingRequest,
    responseObserver: StreamObserver<GetAudioEncodingResponse>
  ) {
    grpcUnary<GetAudioEncodingResponse>(scope, responseObserver) {
      val device = request.source.connection.toBluetoothDevice(bluetoothAdapter)
      Log.i(TAG, "getAudioEncoding: device=$device")

      if (bluetoothA2dp.getConnectionState(device) != BluetoothA2dp.STATE_CONNECTED) {
        Log.e(TAG, "Device is not connected, cannot getAudioEncoding")
        throw Status.UNKNOWN.asException()
      }

      // For now, we only support 44100 kHz sampling rate.
      GetAudioEncodingResponse.newBuilder()
        .setEncoding(AudioEncoding.PCM_S16_LE_44K1_STEREO)
        .build()
    }
  }
}
