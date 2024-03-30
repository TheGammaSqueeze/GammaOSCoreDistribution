/*
 * Copyright 2021 The Android Open Source Project
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

#include "a2dp_encoding_host.h"

#include <base/logging.h>
#include <errno.h>
#include <grp.h>
#include <sys/stat.h>

#include <memory>

#include "a2dp_encoding.h"
#include "a2dp_sbc_constants.h"
#include "btif_a2dp_source.h"
#include "btif_av.h"
#include "btif_av_co.h"
#include "btif_hf.h"
#include "osi/include/log.h"
#include "osi/include/properties.h"
#include "types/raw_address.h"
#include "udrv/include/uipc.h"

#define A2DP_DATA_READ_POLL_MS 10
#define A2DP_HOST_DATA_PATH "/var/run/bluetooth/audio/.a2dp_data"
// TODO(b/198260375): Make A2DP data owner group configurable.
#define A2DP_HOST_DATA_GROUP "bluetooth-audio"

namespace {

std::unique_ptr<tUIPC_STATE> a2dp_uipc = nullptr;

static void btif_a2dp_data_cb([[maybe_unused]] tUIPC_CH_ID ch_id,
                              tUIPC_EVENT event) {
  APPL_TRACE_WARNING("%s: BTIF MEDIA (A2DP-DATA) EVENT %s", __func__,
                     dump_uipc_event(event));

  switch (event) {
    case UIPC_OPEN_EVT:
      /*
       * Read directly from media task from here on (keep callback for
       * connection events.
       */
      UIPC_Ioctl(*a2dp_uipc, UIPC_CH_ID_AV_AUDIO,
                 UIPC_REG_REMOVE_ACTIVE_READSET, NULL);
      UIPC_Ioctl(*a2dp_uipc, UIPC_CH_ID_AV_AUDIO, UIPC_SET_READ_POLL_TMO,
                 reinterpret_cast<void*>(A2DP_DATA_READ_POLL_MS));

      // Will start audio on btif_a2dp_on_started

      /* ACK back when media task is fully started */
      break;

    case UIPC_CLOSE_EVT:
      /*
       * Send stop request only if we are actively streaming and haven't
       * received a stop request. Potentially, the audioflinger detached
       * abnormally.
       */
      if (btif_a2dp_source_is_streaming()) {
        /* Post stop event and wait for audio path to stop */
        btif_av_stream_stop(RawAddress::kEmpty);
      }
      break;

    default:
      APPL_TRACE_ERROR("%s: ### A2DP-DATA EVENT %d NOT HANDLED ###", __func__,
                       event);
      break;
  }
}

// If A2DP_HOST_DATA_GROUP exists we expect audio server and BT both are
// in this group therefore have access to A2DP socket. Otherwise audio
// server should be in the same group that BT stack runs with to access
// A2DP socket.
static void a2dp_data_path_open() {
  UIPC_Open(*a2dp_uipc, UIPC_CH_ID_AV_AUDIO, btif_a2dp_data_cb,
            A2DP_HOST_DATA_PATH);
  struct group* grp = getgrnam(A2DP_HOST_DATA_GROUP);
  chmod(A2DP_HOST_DATA_PATH, 0770);
  if (grp) {
    int res = chown(A2DP_HOST_DATA_PATH, -1, grp->gr_gid);
    if (res == -1) {
      LOG(ERROR) << __func__ << " failed: " << strerror(errno);
    }
  }
}

tA2DP_CTRL_CMD a2dp_pending_cmd_ = A2DP_CTRL_CMD_NONE;
uint64_t total_bytes_read_;
timespec data_position_;
uint16_t remote_delay_report_;

}  // namespace

namespace bluetooth {
namespace audio {
namespace a2dp {

// Invoked by audio server to set audio config (PCM for now)
bool SetAudioConfig(AudioConfig config) {
  btav_a2dp_codec_config_t codec_config;
  codec_config.sample_rate = config.sample_rate;
  codec_config.bits_per_sample = config.bits_per_sample;
  codec_config.channel_mode = config.channel_mode;
  btif_a2dp_source_feeding_update_req(codec_config);
  return true;
}

// Invoked by audio server when it has audio data to stream.
bool StartRequest() {
  // Reset total read bytes and timestamp to avoid confusing audio
  // server at delay calculation.
  total_bytes_read_ = 0;
  data_position_ = {0, 0};

  // Check if a previous request is not finished
  if (a2dp_pending_cmd_ == A2DP_CTRL_CMD_START) {
    LOG(INFO) << __func__ << ": A2DP_CTRL_CMD_START in progress";
    return false;
  } else if (a2dp_pending_cmd_ != A2DP_CTRL_CMD_NONE) {
    LOG(WARNING) << __func__ << ": busy in pending_cmd=" << a2dp_pending_cmd_;
    return false;
  }

  // Don't send START request to stack while we are in a call
  if (!bluetooth::headset::IsCallIdle()) {
    LOG(ERROR) << __func__ << ": call state is busy";
    return false;
  }

  if (btif_av_stream_started_ready()) {
    // Already started, ACK back immediately.
    a2dp_data_path_open();
    return true;
  }
  if (btif_av_stream_ready()) {
    a2dp_data_path_open();
    /*
     * Post start event and wait for audio path to open.
     * If we are the source, the ACK will be sent after the start
     * procedure is completed, othewise send it now.
     */
    a2dp_pending_cmd_ = A2DP_CTRL_CMD_START;
    btif_av_stream_start();
    if (btif_av_get_peer_sep() != AVDT_TSEP_SRC) {
      LOG(INFO) << __func__ << ": accepted";
      return false;  // TODO: should be pending
    }
    a2dp_pending_cmd_ = A2DP_CTRL_CMD_NONE;
    return true;
  }
  LOG(ERROR) << __func__ << ": AV stream is not ready to start";
  return false;
}

// Invoked by audio server when audio streaming is done.
bool StopRequest() {
  if (btif_av_get_peer_sep() == AVDT_TSEP_SNK &&
      !btif_av_stream_started_ready()) {
    btif_av_clear_remote_suspend_flag();
    return true;
  }
  LOG(INFO) << __func__ << ": handling";
  a2dp_pending_cmd_ = A2DP_CTRL_CMD_STOP;
  btif_av_stream_stop(RawAddress::kEmpty);
  return true;
}

// Invoked by audio server to check audio presentation position periodically.
PresentationPosition GetPresentationPosition() {
  PresentationPosition presentation_position{
      .remote_delay_report_ns = remote_delay_report_ * 100000u,
      .total_bytes_read = total_bytes_read_,
      .data_position = data_position_,
  };
  return presentation_position;
}

// delay reports from AVDTP is based on 1/10 ms (100us)
void set_remote_delay(uint16_t delay_report) {
  remote_delay_report_ = delay_report;
}

// Inform audio server about offloading codec; not used for now
bool update_codec_offloading_capabilities(
    const std::vector<btav_a2dp_codec_config_t>& framework_preference) {
  return false;
}

// Checking if new bluetooth_audio is enabled
bool is_hal_enabled() { return true; }

// Check if new bluetooth_audio is running with offloading encoders
bool is_hal_offloading() { return false; }

// Initialize BluetoothAudio HAL: openProvider
bool init(bluetooth::common::MessageLoopThread* message_loop) {
  a2dp_uipc = UIPC_Init();
  total_bytes_read_ = 0;
  data_position_ = {};
  remote_delay_report_ = 0;

  return true;
}

// Clean up BluetoothAudio HAL
void cleanup() {
  end_session();

  if (a2dp_uipc != nullptr) {
    UIPC_Close(*a2dp_uipc, UIPC_CH_ID_ALL);
  }
}

// Set up the codec into BluetoothAudio HAL
bool setup_codec() {
  // TODO: setup codec
  return true;
}

void start_session() {
  // TODO: Notify server; or do we handle it during connected?
}

void end_session() {
  // TODO: Notify server; or do we handle it during disconnected?

  // Reset remote delay. New value will be set when new session starts.
  remote_delay_report_ = 0;
}

void set_audio_low_latency_mode_allowed(bool allowed){
}


void ack_stream_started(const tA2DP_CTRL_ACK& ack) {
  a2dp_pending_cmd_ = A2DP_CTRL_CMD_NONE;
  // TODO: Notify server
}

void ack_stream_suspended(const tA2DP_CTRL_ACK& ack) {
  a2dp_pending_cmd_ = A2DP_CTRL_CMD_NONE;
  // TODO: Notify server
}

// Read from the FMQ of BluetoothAudio HAL
size_t read(uint8_t* p_buf, uint32_t len) {
  uint32_t bytes_read = 0;
  if (a2dp_uipc == nullptr) {
    return 0;
  }
  bytes_read = UIPC_Read(*a2dp_uipc, UIPC_CH_ID_AV_AUDIO, p_buf, len);
  total_bytes_read_ += bytes_read;
  // MONOTONIC_RAW isn't affected by NTP, audio stack rely on this
  // to get precise delay calculation.
  clock_gettime(CLOCK_MONOTONIC_RAW, &data_position_);
  return bytes_read;
}

// Check if OPUS codec is supported
bool is_opus_supported() { return true; }

}  // namespace a2dp
}  // namespace audio
}  // namespace bluetooth
