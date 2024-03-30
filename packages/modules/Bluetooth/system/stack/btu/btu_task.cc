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

#define LOG_TAG "bt_btu_task"

#include <base/bind.h>
#include <base/logging.h>
#include <base/run_loop.h>
#include <base/threading/thread.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "bta/sys/bta_sys.h"
#include "btcore/include/module.h"
#include "btif/include/btif_common.h"
#include "btm_iso_api.h"
#include "common/message_loop_thread.h"
#include "osi/include/allocator.h"
#include "osi/include/log.h"
#include "osi/include/osi.h"
#include "stack/include/acl_hci_link_interface.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/btu.h"

using bluetooth::common::MessageLoopThread;
using bluetooth::hci::IsoManager;

void btm_route_sco_data(BT_HDR* p_msg);

/* Define BTU storage area */
uint8_t btu_trace_level = HCI_INITIAL_TRACE_LEVEL;

static MessageLoopThread main_thread("bt_main_thread", true);

void btu_hci_msg_process(BT_HDR* p_msg) {
  /* Determine the input message type. */
  switch (p_msg->event & BT_EVT_MASK) {
    case BT_EVT_TO_BTU_HCI_ACL:
      /* All Acl Data goes to ACL */
      acl_rcv_acl_data(p_msg);
      break;

    case BT_EVT_TO_BTU_L2C_SEG_XMIT:
      /* L2CAP segment transmit complete */
      acl_link_segments_xmitted(p_msg);
      break;

    case BT_EVT_TO_BTU_HCI_SCO:
      btm_route_sco_data(p_msg);
      break;

    case BT_EVT_TO_BTU_HCI_EVT:
      btu_hcif_process_event((uint8_t)(p_msg->event & BT_SUB_EVT_MASK), p_msg);
      osi_free(p_msg);
      break;

    case BT_EVT_TO_BTU_HCI_CMD:
      btu_hcif_send_cmd((uint8_t)(p_msg->event & BT_SUB_EVT_MASK), p_msg);
      break;

    case BT_EVT_TO_BTU_HCI_ISO:
      IsoManager::GetInstance()->HandleIsoData(p_msg);
      osi_free(p_msg);
      break;

    default:
      osi_free(p_msg);
      break;
  }
}

bluetooth::common::MessageLoopThread* get_main_thread() { return &main_thread; }

bt_status_t do_in_main_thread(const base::Location& from_here,
                              base::OnceClosure task) {
  if (!main_thread.DoInThread(from_here, std::move(task))) {
    LOG(ERROR) << __func__ << ": failed from " << from_here.ToString();
    return BT_STATUS_FAIL;
  }
  return BT_STATUS_SUCCESS;
}

bt_status_t do_in_main_thread_delayed(const base::Location& from_here,
                                      base::OnceClosure task,
                                      const base::TimeDelta& delay) {
  if (!main_thread.DoInThreadDelayed(from_here, std::move(task), delay)) {
    LOG(ERROR) << __func__ << ": failed from " << from_here.ToString();
    return BT_STATUS_FAIL;
  }
  return BT_STATUS_SUCCESS;
}

static void do_post_on_bt_main(BtMainClosure closure) { closure(); }

void post_on_bt_main(BtMainClosure closure) {
  ASSERT(do_in_main_thread(
             FROM_HERE, base::Bind(do_post_on_bt_main, std::move(closure))) ==
         BT_STATUS_SUCCESS);
}

void main_thread_start_up() {
  main_thread.StartUp();
  if (!main_thread.IsRunning()) {
    LOG(FATAL) << __func__ << ": unable to start btu message loop thread.";
  }
  if (!main_thread.EnableRealTimeScheduling()) {
#if defined(OS_ANDROID)
    LOG(FATAL) << __func__ << ": unable to enable real time scheduling";
#else
    LOG(ERROR) << __func__ << ": unable to enable real time scheduling";
#endif
  }
}

void main_thread_shut_down() { main_thread.ShutDown(); }

bool is_on_main_thread() {
  // Pthreads doesn't have the concept of a thread ID, so we have to reach down
  // into the kernel.
#if defined(OS_MACOSX)
  return main_thread.GetThreadId() == pthread_mach_thread_np(pthread_self());
#elif defined(OS_LINUX)
#include <sys/syscall.h> /* For SYS_xxx definitions */
#include <unistd.h>
  return main_thread.GetThreadId() == syscall(__NR_gettid);
#elif defined(OS_ANDROID)
#include <sys/types.h>
#include <unistd.h>
  return main_thread.GetThreadId() == gettid();
#else
  LOG(ERROR) << __func__ << "Unable to determine if on main thread";
  return true;
#endif
}
