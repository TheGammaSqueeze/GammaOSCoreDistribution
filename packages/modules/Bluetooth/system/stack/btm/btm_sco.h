/*
 * Copyright 2020 The Android Open Source Project
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

#pragma once

#include <cstdint>
#include <string>

#include "device/include/esco_parameters.h"
#include "stack/include/btm_api_types.h"

constexpr uint16_t kMaxScoLinks = static_cast<uint16_t>(BTM_MAX_SCO_LINKS);

#ifndef ESCO_DATA_PATH
#ifdef OS_ANDROID
#define ESCO_DATA_PATH ESCO_DATA_PATH_PCM
#else
#define ESCO_DATA_PATH ESCO_DATA_PATH_HCI
#endif
#endif

// SCO-over-HCI audio related definitions
namespace bluetooth::audio::sco {

// Initialize SCO-over-HCI socket (UIPC); the client is audio server.
void init();

// Open the socket when there is SCO connection open
void open();

// Clean up the socket when the SCO connection is done
void cleanup();

// Read from the socket (audio server) for SCO Tx
size_t read(uint8_t* p_buf, uint32_t len);

// Write to the socket from SCO Rx
size_t write(const uint8_t* buf, uint32_t len);
}  // namespace bluetooth::audio::sco

/* Define the structures needed by sco
 */

#ifndef CASE_RETURN_TEXT
#define CASE_RETURN_TEXT(code) \
  case code:                   \
    return #code
#endif

/* Define the structures needed by sco */
typedef enum : uint16_t {
  SCO_ST_UNUSED = 0,
  SCO_ST_LISTENING = 1,
  SCO_ST_W4_CONN_RSP = 2,
  SCO_ST_CONNECTING = 3,
  SCO_ST_CONNECTED = 4,
  SCO_ST_DISCONNECTING = 5,
  SCO_ST_PEND_UNPARK = 6,
  SCO_ST_PEND_ROLECHANGE = 7,
  SCO_ST_PEND_MODECHANGE = 8,
} tSCO_STATE;

inline std::string sco_state_text(const tSCO_STATE& state) {
  switch (state) {
    CASE_RETURN_TEXT(SCO_ST_UNUSED);
    CASE_RETURN_TEXT(SCO_ST_LISTENING);
    CASE_RETURN_TEXT(SCO_ST_W4_CONN_RSP);
    CASE_RETURN_TEXT(SCO_ST_CONNECTING);
    CASE_RETURN_TEXT(SCO_ST_CONNECTED);
    CASE_RETURN_TEXT(SCO_ST_DISCONNECTING);
    CASE_RETURN_TEXT(SCO_ST_PEND_UNPARK);
    CASE_RETURN_TEXT(SCO_ST_PEND_ROLECHANGE);
    CASE_RETURN_TEXT(SCO_ST_PEND_MODECHANGE);
    default:
      return std::string("unknown_sco_state: ") +
             std::to_string(static_cast<uint16_t>(state));
  }
}

#undef CASE_RETURN_TEXT

/* Define the structure that contains (e)SCO data */
typedef struct {
  tBTM_ESCO_CBACK* p_esco_cback; /* Callback for eSCO events     */
  enh_esco_params_t setup;
  tBTM_ESCO_DATA data; /* Connection complete information */
  uint8_t hci_status;
} tBTM_ESCO_INFO;

/* Define the structure used for SCO Management
 */
typedef struct {
  tBTM_ESCO_INFO esco;    /* Current settings             */
  tBTM_SCO_CB* p_conn_cb; /* Callback for when connected  */
  tBTM_SCO_CB* p_disc_cb; /* Callback for when disconnect */
  tSCO_STATE state;       /* The state of the SCO link    */

  uint16_t hci_handle;    /* HCI Handle                   */
 public:
  bool is_active() const { return state != SCO_ST_UNUSED; }
  uint16_t Handle() const { return hci_handle; }

  bool is_orig;           /* true if the originator       */
  bool rem_bd_known;      /* true if remote BD addr known */

} tSCO_CONN;

/* SCO Management control block */
typedef struct {
  tSCO_CONN sco_db[BTM_MAX_SCO_LINKS];
  enh_esco_params_t def_esco_parms;
  bool esco_supported;        /* true if 1.2 cntlr AND supports eSCO links */

  tSCO_CONN* get_sco_connection_from_index(uint16_t index) {
    return (index < kMaxScoLinks) ? (&sco_db[index]) : nullptr;
  }

  tSCO_CONN* get_sco_connection_from_handle(uint16_t handle) {
    tSCO_CONN* p_sco = sco_db;
    for (uint16_t xx = 0; xx < kMaxScoLinks; xx++, p_sco++) {
      if (p_sco->hci_handle == handle) {
        return p_sco;
      }
    }
    return nullptr;
  }

  void Init() {
    def_esco_parms = esco_parameters_for_codec(ESCO_CODEC_CVSD_S3);
  }

  void Free() { bluetooth::audio::sco::cleanup(); }

  uint16_t get_index(const tSCO_CONN* p_sco) const {
    CHECK(p_sco != nullptr);
    const tSCO_CONN* p = sco_db;
    for (uint16_t xx = 0; xx < kMaxScoLinks; xx++, p++) {
      if (p_sco == p) {
        return xx;
      }
    }
    return 0xffff;
  }

} tSCO_CB;

extern void btm_sco_chk_pend_rolechange(uint16_t hci_handle);
extern void btm_sco_disc_chk_pend_for_modechange(uint16_t hci_handle);

// Visible for test only
BT_HDR* btm_sco_make_packet(std::vector<uint8_t> data, uint16_t sco_handle);

// Send a SCO packet
void btm_send_sco_packet(std::vector<uint8_t> data);
