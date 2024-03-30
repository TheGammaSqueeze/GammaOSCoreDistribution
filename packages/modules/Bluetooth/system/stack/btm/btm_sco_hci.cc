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

#include <errno.h>
#include <grp.h>
#include <sys/stat.h>

#include <memory>

#include "osi/include/log.h"
#include "stack/btm/btm_sco.h"
#include "udrv/include/uipc.h"

#if ESCO_DATA_PATH == ESCO_DATA_PATH_PCM
// For hardware encoding path, provide an empty implementation

namespace bluetooth::audio::sco {
void open() {}
void cleanup() {}
size_t read(uint8_t*, uint32_t) { return 0; }
size_t write(const uint8_t*, uint32_t) { return 0; }
}  // namespace bluetooth::audio::sco
#else

#define SCO_DATA_READ_POLL_MS 10
#define SCO_HOST_DATA_PATH "/var/run/bluetooth/audio/.sco_data"
// TODO(b/198260375): Make SCO data owner group configurable.
#define SCO_HOST_DATA_GROUP "bluetooth-audio"

namespace {

std::unique_ptr<tUIPC_STATE> sco_uipc = nullptr;

void sco_data_cb(tUIPC_CH_ID, tUIPC_EVENT event) {
  switch (event) {
    case UIPC_OPEN_EVT:
      /*
       * Read directly from media task from here on (keep callback for
       * connection events.
       */
      UIPC_Ioctl(*sco_uipc, UIPC_CH_ID_AV_AUDIO, UIPC_REG_REMOVE_ACTIVE_READSET,
                 NULL);
      UIPC_Ioctl(*sco_uipc, UIPC_CH_ID_AV_AUDIO, UIPC_SET_READ_POLL_TMO,
                 reinterpret_cast<void*>(SCO_DATA_READ_POLL_MS));
      break;
    default:
      break;
  }
}

}  // namespace

namespace bluetooth {
namespace audio {
namespace sco {

void open() {
  if (sco_uipc != nullptr) {
    LOG_WARN("Re-opening UIPC that is already running");
  }
  sco_uipc = UIPC_Init();
  UIPC_Open(*sco_uipc, UIPC_CH_ID_AV_AUDIO, sco_data_cb, SCO_HOST_DATA_PATH);
  struct group* grp = getgrnam(SCO_HOST_DATA_GROUP);
  chmod(SCO_HOST_DATA_PATH, 0770);
  if (grp) {
    int res = chown(SCO_HOST_DATA_PATH, -1, grp->gr_gid);
    if (res == -1) {
      LOG_ERROR("%s failed: %s", __func__, strerror(errno));
    }
  }
}

void cleanup() {
  if (sco_uipc == nullptr) {
    return;
  }
  UIPC_Close(*sco_uipc, UIPC_CH_ID_ALL);
  sco_uipc = nullptr;
}

size_t read(uint8_t* p_buf, uint32_t len) {
  if (sco_uipc == nullptr) {
    LOG_WARN("Read from uninitialized or closed UIPC");
    return 0;
  }
  return UIPC_Read(*sco_uipc, UIPC_CH_ID_AV_AUDIO, p_buf, len);
}

size_t write(const uint8_t* p_buf, uint32_t len) {
  if (sco_uipc == nullptr) {
    LOG_WARN("Write to uninitialized or closed UIPC");
    return 0;
  }
  return UIPC_Send(*sco_uipc, UIPC_CH_ID_AV_AUDIO, 0, p_buf, len);
}

}  // namespace sco
}  // namespace audio
}  // namespace bluetooth

#endif
