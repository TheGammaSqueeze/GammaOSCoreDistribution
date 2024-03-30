/******************************************************************************
 *
 *  Copyright 1999-2012 Broadcom Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

/******************************************************************************
 *
 *  this file contains the main Bluetooth Upper Layer definitions. The Broadcom
 *  implementations of L2CAP RFCOMM, SDP and the BTIf run as one GKI task. The
 *  btu_task switches between them.
 *
 ******************************************************************************/

#ifndef BTU_H
#define BTU_H

#include <base/callback.h>
#include <base/location.h>
#include <base/threading/thread.h>

#include <cstdint>

#include "bt_target.h"
#include "common/message_loop_thread.h"
#include "include/hardware/bluetooth.h"
#include "osi/include/alarm.h"
#include "osi/include/osi.h"  // UNUSED_ATTR
#include "stack/include/bt_hdr.h"

/* Global BTU data */
extern uint8_t btu_trace_level;

/* Functions provided by btu_hcif.cc
 ***********************************
*/
void btu_hcif_process_event(UNUSED_ATTR uint8_t controller_id,
                            const BT_HDR* p_buf);
void btu_hcif_send_cmd(UNUSED_ATTR uint8_t controller_id, const BT_HDR* p_msg);
void btu_hcif_send_cmd_with_cb(const base::Location& posted_from,
                               uint16_t opcode, uint8_t* params,
                               uint8_t params_len,
                               base::OnceCallback<void(uint8_t*, uint16_t)> cb);
namespace bluetooth::legacy::testing {
void btu_hcif_hdl_command_status(uint16_t opcode, uint8_t status,
                                 const uint8_t* p_cmd,
                                 void* p_vsc_status_cback);
}  // namespace bluetooth::legacy::testing

/* Functions provided by btu_task.cc
 ***********************************
*/
bluetooth::common::MessageLoopThread* get_main_thread();
bt_status_t do_in_main_thread(const base::Location& from_here,
                              base::OnceClosure task);
bt_status_t do_in_main_thread_delayed(const base::Location& from_here,
                                      base::OnceClosure task,
                                      const base::TimeDelta& delay);

bool is_on_main_thread();
using BtMainClosure = std::function<void()>;
void post_on_bt_main(BtMainClosure closure);

#endif
