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

import android.content.Context
import android.content.Intent
import android.media.*
import com.google.protobuf.Empty
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import pandora.MediaPlayerGrpc.MediaPlayerImplBase
import pandora.MediaPlayerProto.*

@kotlinx.coroutines.ExperimentalCoroutinesApi
class MediaPlayer(val context: Context) : MediaPlayerImplBase() {
  private val TAG = "PandoraMediaPlayer"

  private val scope: CoroutineScope

  init {
    // Init the CoroutineScope
    scope = CoroutineScope(Dispatchers.Default)
    context.startService(Intent(context, MediaPlayerBrowserService::class.java))
  }

  override fun play(request: Empty, responseObserver: StreamObserver<Empty>) {
    grpcUnary<Empty>(scope, responseObserver) {
      MediaPlayerBrowserService.instance.play()
      Empty.getDefaultInstance()
    }
  }

  override fun stop(request: Empty, responseObserver: StreamObserver<Empty>) {
    grpcUnary<Empty>(scope, responseObserver) {
      MediaPlayerBrowserService.instance.stop()
      Empty.getDefaultInstance()
    }
  }

  override fun pause(request: Empty, responseObserver: StreamObserver<Empty>) {
    grpcUnary<Empty>(scope, responseObserver) {
      MediaPlayerBrowserService.instance.pause()
      Empty.getDefaultInstance()
    }
  }

  override fun rewind(request: Empty, responseObserver: StreamObserver<Empty>) {
    grpcUnary<Empty>(scope, responseObserver) {
      MediaPlayerBrowserService.instance.rewind()
      Empty.getDefaultInstance()
    }
  }

  override fun fastForward(request: Empty, responseObserver: StreamObserver<Empty>) {
    grpcUnary<Empty>(scope, responseObserver) {
      MediaPlayerBrowserService.instance.fastForward()
      Empty.getDefaultInstance()
    }
  }

  override fun forward(request: Empty, responseObserver: StreamObserver<Empty>) {
    grpcUnary<Empty>(scope, responseObserver) {
      MediaPlayerBrowserService.instance.forward()
      Empty.getDefaultInstance()
    }
  }

  override fun backward(request: Empty, responseObserver: StreamObserver<Empty>) {
    grpcUnary<Empty>(scope, responseObserver) {
      MediaPlayerBrowserService.instance.backward()
      Empty.getDefaultInstance()
    }
  }

  override fun setLargeMetadata(request: Empty, responseObserver: StreamObserver<Empty>) {
    grpcUnary<Empty>(scope, responseObserver) {
      MediaPlayerBrowserService.instance.setLargeMetadata()
      Empty.getDefaultInstance()
    }
  }

  fun deinit() {
    // Deinit the CoroutineScope
    scope.cancel()
    // Stop service
    context.stopService(Intent(context, MediaPlayerBrowserService::class.java))
  }
}
