/******************************************************************************
 *
 *  Copyright 2008-2012 Broadcom Corporation
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
 *  this file contains the main ATT functions
 *
 ******************************************************************************/

#include <base/logging.h>

#include "bt_target.h"
#include "bt_utils.h"
#include "btif/include/btif_storage.h"
#include "connection_manager.h"
#include "device/include/interop.h"
#include "gd/common/init_flags.h"
#include "internal_include/stack_config.h"
#include "l2c_api.h"
#include "osi/include/allocator.h"
#include "osi/include/osi.h"
#include "osi/include/properties.h"
#include "stack/btm/btm_ble_int.h"
#include "stack/btm/btm_dev.h"
#include "stack/btm/btm_sec.h"
#include "stack/eatt/eatt.h"
#include "stack/gatt/gatt_int.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/l2cap_acl_interface.h"
#include "types/raw_address.h"

using base::StringPrintf;
using bluetooth::eatt::EattExtension;

/******************************************************************************/
/*            L O C A L    F U N C T I O N     P R O T O T Y P E S            */
/******************************************************************************/
static void gatt_le_connect_cback(uint16_t chan, const RawAddress& bd_addr,
                                  bool connected, uint16_t reason,
                                  tBT_TRANSPORT transport);
static void gatt_le_data_ind(uint16_t chan, const RawAddress& bd_addr,
                             BT_HDR* p_buf);
static void gatt_le_cong_cback(const RawAddress& remote_bda, bool congest);

static void gatt_l2cif_connect_ind_cback(const RawAddress& bd_addr,
                                         uint16_t l2cap_cid, uint16_t psm,
                                         uint8_t l2cap_id);
static void gatt_l2cif_connect_cfm_cback(uint16_t l2cap_cid, uint16_t result);
static void gatt_l2cif_config_ind_cback(uint16_t l2cap_cid,
                                        tL2CAP_CFG_INFO* p_cfg);
static void gatt_l2cif_config_cfm_cback(uint16_t lcid, uint16_t result,
                                        tL2CAP_CFG_INFO* p_cfg);
static void gatt_l2cif_disconnect_ind_cback(uint16_t l2cap_cid,
                                            bool ack_needed);
static void gatt_l2cif_disconnect(uint16_t l2cap_cid);
static void gatt_l2cif_data_ind_cback(uint16_t l2cap_cid, BT_HDR* p_msg);
static void gatt_send_conn_cback(tGATT_TCB* p_tcb);
static void gatt_l2cif_congest_cback(uint16_t cid, bool congested);
static void gatt_on_l2cap_error(uint16_t lcid, uint16_t result);

static const tL2CAP_APPL_INFO dyn_info = {gatt_l2cif_connect_ind_cback,
                                          gatt_l2cif_connect_cfm_cback,
                                          gatt_l2cif_config_ind_cback,
                                          gatt_l2cif_config_cfm_cback,
                                          gatt_l2cif_disconnect_ind_cback,
                                          NULL,
                                          gatt_l2cif_data_ind_cback,
                                          gatt_l2cif_congest_cback,
                                          NULL,
                                          gatt_on_l2cap_error,
                                          NULL,
                                          NULL,
                                          NULL,
                                          NULL};

tGATT_CB gatt_cb;

/*******************************************************************************
 *
 * Function         gatt_init
 *
 * Description      This function is enable the GATT profile on the device.
 *                  It clears out the control blocks, and registers with L2CAP.
 *
 * Returns          void
 *
 ******************************************************************************/
void gatt_init(void) {
  tL2CAP_FIXED_CHNL_REG fixed_reg;

  VLOG(1) << __func__;

  gatt_cb = tGATT_CB();
  connection_manager::reset(true);
  memset(&fixed_reg, 0, sizeof(tL2CAP_FIXED_CHNL_REG));

  gatt_cb.sign_op_queue = fixed_queue_new(SIZE_MAX);
  gatt_cb.srv_chg_clt_q = fixed_queue_new(SIZE_MAX);
  /* First, register fixed L2CAP channel for ATT over BLE */
  fixed_reg.pL2CA_FixedConn_Cb = gatt_le_connect_cback;
  fixed_reg.pL2CA_FixedData_Cb = gatt_le_data_ind;
  fixed_reg.pL2CA_FixedCong_Cb = gatt_le_cong_cback; /* congestion callback */

  // the GATT timeout is updated after a connection
  // is established, when we know whether any
  // clients exist
  fixed_reg.default_idle_tout = L2CAP_NO_IDLE_TIMEOUT;

  L2CA_RegisterFixedChannel(L2CAP_ATT_CID, &fixed_reg);

  gatt_cb.over_br_enabled =
      osi_property_get_bool("bluetooth.gatt.over_bredr.enabled", true);
  /* Now, register with L2CAP for ATT PSM over BR/EDR */
  if (gatt_cb.over_br_enabled &&
      !L2CA_Register2(BT_PSM_ATT, dyn_info, false /* enable_snoop */, nullptr,
                      GATT_MAX_MTU_SIZE, 0, BTM_SEC_NONE)) {
    LOG(ERROR) << "ATT Dynamic Registration failed";
  }

  gatt_cb.hdl_cfg.gatt_start_hdl = GATT_GATT_START_HANDLE;
  gatt_cb.hdl_cfg.gap_start_hdl = GATT_GAP_START_HANDLE;
  gatt_cb.hdl_cfg.gmcs_start_hdl = GATT_GMCS_START_HANDLE;
  gatt_cb.hdl_cfg.gtbs_start_hdl = GATT_GTBS_START_HANDLE;
  gatt_cb.hdl_cfg.tmas_start_hdl = GATT_TMAS_START_HANDLE;
  gatt_cb.hdl_cfg.app_start_hdl = GATT_APP_START_HANDLE;

  gatt_cb.hdl_list_info = new std::list<tGATT_HDL_LIST_ELEM>();
  gatt_cb.srv_list_info = new std::list<tGATT_SRV_LIST_ELEM>();
  gatt_profile_db_init();

  EattExtension::GetInstance()->Start();
}

/*******************************************************************************
 *
 * Function         gatt_free
 *
 * Description      This function frees resources used by the GATT profile.
 *
 * Returns          void
 *
 ******************************************************************************/
void gatt_free(void) {
  int i;
  VLOG(1) << __func__;

  fixed_queue_free(gatt_cb.sign_op_queue, NULL);
  gatt_cb.sign_op_queue = NULL;
  fixed_queue_free(gatt_cb.srv_chg_clt_q, NULL);
  gatt_cb.srv_chg_clt_q = NULL;
  for (i = 0; i < GATT_MAX_PHY_CHANNEL; i++) {
    gatt_cb.tcb[i].pending_enc_clcb = std::deque<tGATT_CLCB*>();

    fixed_queue_free(gatt_cb.tcb[i].pending_ind_q, NULL);
    gatt_cb.tcb[i].pending_ind_q = NULL;

    alarm_free(gatt_cb.tcb[i].conf_timer);
    gatt_cb.tcb[i].conf_timer = NULL;

    alarm_free(gatt_cb.tcb[i].ind_ack_timer);
    gatt_cb.tcb[i].ind_ack_timer = NULL;

    fixed_queue_free(gatt_cb.tcb[i].sr_cmd.multi_rsp_q, NULL);
    gatt_cb.tcb[i].sr_cmd.multi_rsp_q = NULL;

    if (gatt_cb.tcb[i].eatt)
      EattExtension::GetInstance()->FreeGattResources(gatt_cb.tcb[i].peer_bda);
  }

  gatt_cb.hdl_list_info->clear();
  delete gatt_cb.hdl_list_info;
  gatt_cb.hdl_list_info = nullptr;
  gatt_cb.srv_list_info->clear();
  delete gatt_cb.srv_list_info;
  gatt_cb.srv_list_info = nullptr;

  EattExtension::GetInstance()->Stop();
}

void gatt_find_in_device_record(const RawAddress& bd_addr,
                                tBLE_BD_ADDR* address_with_type) {
  const tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bd_addr);
  if (p_dev_rec == nullptr) {
    return;
  }

  if (p_dev_rec->device_type & BT_DEVICE_TYPE_BLE) {
    if (p_dev_rec->ble.identity_address_with_type.bda.IsEmpty()) {
      *address_with_type = {.type = p_dev_rec->ble.AddressType(),
                            .bda = bd_addr};
      return;
    }
    *address_with_type = p_dev_rec->ble.identity_address_with_type;
    return;
  }
  *address_with_type = {.type = BLE_ADDR_PUBLIC, .bda = bd_addr};
  return;
}

/*******************************************************************************
 *
 * Function         gatt_connect
 *
 * Description      This function is called to initiate a connection to a peer
 *                  device.
 *
 * Parameter        rem_bda: remote device address to connect to.
 *
 * Returns          true if connection is started, otherwise return false.
 *
 ******************************************************************************/
bool gatt_connect(const RawAddress& rem_bda, tGATT_TCB* p_tcb,
                  tBT_TRANSPORT transport, uint8_t initiating_phys,
                  tGATT_IF gatt_if) {
  if (gatt_get_ch_state(p_tcb) != GATT_CH_OPEN)
    gatt_set_ch_state(p_tcb, GATT_CH_CONN);

  if (transport != BT_TRANSPORT_LE) {
    p_tcb->att_lcid = L2CA_ConnectReq2(BT_PSM_ATT, rem_bda, BTM_SEC_NONE);
    return p_tcb->att_lcid != 0;
  }

  // Already connected, mark the link as used
  if (gatt_get_ch_state(p_tcb) == GATT_CH_OPEN) {
    gatt_update_app_use_link_flag(gatt_if, p_tcb, true, true);
    return true;
  }

  p_tcb->att_lcid = L2CAP_ATT_CID;
  return acl_create_le_connection_with_id(gatt_if, rem_bda);
}

/*******************************************************************************
 *
 * Function         gatt_disconnect
 *
 * Description      This function is called to disconnect to an ATT device.
 *
 * Parameter        p_tcb: pointer to the TCB to disconnect.
 *
 * Returns          true: if connection found and to be disconnected; otherwise
 *                  return false.
 *
 ******************************************************************************/
bool gatt_disconnect(tGATT_TCB* p_tcb) {
  VLOG(1) << __func__;

  if (!p_tcb) {
    LOG_WARN("Unable to disconnect an unknown device");
    return false;
  }

  tGATT_CH_STATE ch_state = gatt_get_ch_state(p_tcb);
  if (ch_state == GATT_CH_CLOSING) {
    LOG_DEBUG("Device already in closing state peer:%s",
              PRIVATE_ADDRESS(p_tcb->peer_bda));
    VLOG(1) << __func__ << " already in closing state";
    return true;
  }

  if (p_tcb->att_lcid == L2CAP_ATT_CID) {
    if (ch_state == GATT_CH_OPEN) {
      L2CA_RemoveFixedChnl(L2CAP_ATT_CID, p_tcb->peer_bda);
      gatt_set_ch_state(p_tcb, GATT_CH_CLOSING);
    } else {
      if (!connection_manager::direct_connect_remove(CONN_MGR_ID_L2CAP,
                                                     p_tcb->peer_bda)) {
        BTM_AcceptlistRemove(p_tcb->peer_bda);
        LOG_INFO(
            "GATT connection manager has no record but removed filter "
            "acceptlist "
            "gatt_if:%hhu peer:%s",
            static_cast<uint8_t>(CONN_MGR_ID_L2CAP),
            PRIVATE_ADDRESS(p_tcb->peer_bda));
      }
      gatt_cleanup_upon_disc(p_tcb->peer_bda, GATT_CONN_TERMINATE_LOCAL_HOST,
                             p_tcb->transport);
    }
  } else {
    if ((ch_state == GATT_CH_OPEN) || (ch_state == GATT_CH_CFG)) {
      gatt_l2cif_disconnect(p_tcb->att_lcid);
    } else {
      VLOG(1) << __func__ << " gatt_disconnect channel not opened";
    }
  }

  return true;
}

/*******************************************************************************
 *
 * Function         gatt_update_app_hold_link_status
 *
 * Description      Update the application use link status
 *
 * Returns          true if any modifications are made or
 *                  when it already exists, false otherwise.
 *
 ******************************************************************************/
bool gatt_update_app_hold_link_status(tGATT_IF gatt_if, tGATT_TCB* p_tcb,
                                      bool is_add) {
  LOG_DEBUG("gatt_if=%d, is_add=%d, peer_bda=%s", +gatt_if, is_add,
            p_tcb->peer_bda.ToString().c_str());
  auto& holders = p_tcb->app_hold_link;

  if (is_add) {
    auto ret = holders.insert(gatt_if);
    if (ret.second) {
      LOG_DEBUG("added gatt_if=%d", +gatt_if);
    } else {
      LOG_DEBUG("attempt to add already existing gatt_if=%d", +gatt_if);
    }
    return true;
  }

  //! is_add
  if (!holders.erase(gatt_if)) {
    LOG_WARN("attempt to remove non-existing gatt_if=%d", +gatt_if);
    return false;
  }

  LOG_INFO("removed gatt_if=%d", +gatt_if);
  return true;
}

/*******************************************************************************
 *
 * Function         gatt_update_app_use_link_flag
 *
 * Description      Update the application use link flag and optional to check
 *                  the acl link if the link is up then set the idle time out
 *                  accordingly
 *
 * Returns          void.
 *
 ******************************************************************************/
void gatt_update_app_use_link_flag(tGATT_IF gatt_if, tGATT_TCB* p_tcb,
                                   bool is_add, bool check_acl_link) {
  LOG_DEBUG("gatt_if=%d, is_add=%d chk_link=%d", +gatt_if, is_add,
            check_acl_link);

  if (!p_tcb) {
    LOG_WARN("p_tcb is null");
    return;
  }

  // If we make no modification, i.e. kill app that was never connected to a
  // device, skip updating the device state.
  if (!gatt_update_app_hold_link_status(gatt_if, p_tcb, is_add)) {
    LOG_INFO("App status is not updated for gatt_if=%d", +gatt_if);
    return;
  }

  if (!check_acl_link) {
    LOG_INFO("check_acl_link is false, no need to check");
    return;
  }

  bool is_valid_handle =
      (BTM_GetHCIConnHandle(p_tcb->peer_bda, p_tcb->transport) !=
       GATT_INVALID_ACL_HANDLE);

  if (is_add) {
    if (p_tcb->att_lcid == L2CAP_ATT_CID && is_valid_handle) {
      LOG_INFO("disable link idle timer for %s",
               p_tcb->peer_bda.ToString().c_str());
      /* acl link is connected disable the idle timeout */
      GATT_SetIdleTimeout(p_tcb->peer_bda, GATT_LINK_NO_IDLE_TIMEOUT,
                          p_tcb->transport, true /* is_active */);
    } else {
      LOG_INFO("invalid handle %d or dynamic CID %d", is_valid_handle,
               p_tcb->att_lcid);
    }
  } else {
    if (p_tcb->app_hold_link.empty()) {
      // acl link is connected but no application needs to use the link
      if (p_tcb->att_lcid == L2CAP_ATT_CID && is_valid_handle) {
        /* Drop EATT before closing ATT */
        EattExtension::GetInstance()->Disconnect(p_tcb->peer_bda);

        /* for fixed channel, set the timeout value to
           GATT_LINK_IDLE_TIMEOUT_WHEN_NO_APP seconds */
        LOG_INFO(
            "GATT fixed channel is no longer useful, start link idle timer for "
            "%d seconds",
            GATT_LINK_IDLE_TIMEOUT_WHEN_NO_APP);
        GATT_SetIdleTimeout(p_tcb->peer_bda, GATT_LINK_IDLE_TIMEOUT_WHEN_NO_APP,
                            p_tcb->transport, false /* is_active */);
      } else {
        // disconnect the dynamic channel
        LOG_INFO("disconnect GATT dynamic channel");
        gatt_disconnect(p_tcb);
      }
    } else {
      LOG_INFO("is_add=false, but some app is still using the ACL link");
    }
  }
}

/** GATT connection initiation */
bool gatt_act_connect(tGATT_REG* p_reg, const RawAddress& bd_addr,
                      tBT_TRANSPORT transport, int8_t initiating_phys) {
  tGATT_TCB* p_tcb = gatt_find_tcb_by_addr(bd_addr, transport);
  if (p_tcb != NULL) {
    /* before link down, another app try to open a GATT connection */
    uint8_t st = gatt_get_ch_state(p_tcb);
    if (st == GATT_CH_OPEN && p_tcb->app_hold_link.empty() &&
        transport == BT_TRANSPORT_LE) {
      if (!gatt_connect(bd_addr, p_tcb, transport, initiating_phys,
                        p_reg->gatt_if))
        return false;
    } else if (st == GATT_CH_CLOSING) {
      LOG(INFO) << "Must finish disconnection before new connection";
      /* need to complete the closing first */
      return false;
    }

    return true;
  }

  p_tcb = gatt_allocate_tcb_by_bdaddr(bd_addr, transport);
  if (!p_tcb) {
    LOG(ERROR) << "Max TCB for gatt_if [ " << +p_reg->gatt_if << "] reached.";
    return false;
  }

  if (!gatt_connect(bd_addr, p_tcb, transport, initiating_phys,
                    p_reg->gatt_if)) {
    LOG(ERROR) << "gatt_connect failed";
    fixed_queue_free(p_tcb->pending_ind_q, NULL);
    *p_tcb = tGATT_TCB();
    return false;
  }

  return true;
}

namespace connection_manager {
void on_connection_timed_out(uint8_t app_id, const RawAddress& address) {
  gatt_le_connect_cback(L2CAP_ATT_CID, address, false, 0xff, BT_TRANSPORT_LE);
}
}  // namespace connection_manager

/** This callback function is called by L2CAP to indicate that the ATT fixed
 * channel for LE is connected (conn = true)/disconnected (conn = false).
 */
static void gatt_le_connect_cback(uint16_t chan, const RawAddress& bd_addr,
                                  bool connected, uint16_t reason,
                                  tBT_TRANSPORT transport) {
  tGATT_TCB* p_tcb = gatt_find_tcb_by_addr(bd_addr, transport);
  bool check_srv_chg = false;
  tGATTS_SRV_CHG* p_srv_chg_clt = NULL;

  if (transport == BT_TRANSPORT_BR_EDR) {
    LOG_WARN("Ignoring fixed channel connect/disconnect on br_edr for GATT");
    return;
  }

  VLOG(1) << "GATT   ATT protocol channel with BDA: " << bd_addr << " is "
          << ((connected) ? "connected" : "disconnected");

  p_srv_chg_clt = gatt_is_bda_in_the_srv_chg_clt_list(bd_addr);
  if (p_srv_chg_clt != NULL) {
    check_srv_chg = true;
  } else {
    if (btm_sec_is_a_bonded_dev(bd_addr))
      gatt_add_a_bonded_dev_for_srv_chg(bd_addr);
  }

  if (!connected) {
    gatt_cleanup_upon_disc(bd_addr, static_cast<tGATT_DISCONN_REASON>(reason),
                           transport);
    return;
  }

  /* do we have a channel initiating a connection? */
  if (p_tcb) {
    /* we are initiating connection */
    if (gatt_get_ch_state(p_tcb) == GATT_CH_CONN) {
      /* send callback */
      gatt_set_ch_state(p_tcb, GATT_CH_OPEN);
      p_tcb->payload_size = GATT_DEF_BLE_MTU_SIZE;

      gatt_send_conn_cback(p_tcb);
    }
    if (check_srv_chg) gatt_chk_srv_chg(p_srv_chg_clt);
  }
  /* this is incoming connection or background connection callback */

  else {
    p_tcb = gatt_allocate_tcb_by_bdaddr(bd_addr, BT_TRANSPORT_LE);
    if (!p_tcb) {
      LOG(ERROR) << "CCB max out, no rsources";
      return;
    }

    p_tcb->att_lcid = L2CAP_ATT_CID;

    gatt_set_ch_state(p_tcb, GATT_CH_OPEN);

    p_tcb->payload_size = GATT_DEF_BLE_MTU_SIZE;

    gatt_send_conn_cback(p_tcb);
    if (check_srv_chg) {
      gatt_chk_srv_chg(p_srv_chg_clt);
    }
  }

  if (stack_config_get_interface()->get_pts_connect_eatt_before_encryption()) {
    LOG_INFO(" Start EATT before encryption ");
    EattExtension::GetInstance()->Connect(bd_addr);
  }
}

/** This function is called to process the congestion callback from lcb */
static void gatt_channel_congestion(tGATT_TCB* p_tcb, bool congested) {
  uint8_t i = 0;
  tGATT_REG* p_reg = NULL;
  uint16_t conn_id;

  /* if uncongested, check to see if there is any more pending data */
  if (p_tcb != NULL && !congested) {
    gatt_cl_send_next_cmd_inq(*p_tcb);
  }
  /* notifying all applications for the connection up event */
  for (i = 0, p_reg = gatt_cb.cl_rcb; i < GATT_MAX_APPS; i++, p_reg++) {
    if (p_reg->in_use) {
      if (p_reg->app_cb.p_congestion_cb) {
        conn_id = GATT_CREATE_CONN_ID(p_tcb->tcb_idx, p_reg->gatt_if);
        (*p_reg->app_cb.p_congestion_cb)(conn_id, congested);
      }
    }
  }
}

void gatt_notify_phy_updated(tGATT_STATUS status, uint16_t handle,
                             uint8_t tx_phy, uint8_t rx_phy) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev_by_handle(handle);
  if (!p_dev_rec) {
    LOG_WARN("No Device Found!");
    return;
  }

  tGATT_TCB* p_tcb =
      gatt_find_tcb_by_addr(p_dev_rec->ble.pseudo_addr, BT_TRANSPORT_LE);
  if (!p_tcb) return;

  for (int i = 0; i < GATT_MAX_APPS; i++) {
    tGATT_REG* p_reg = &gatt_cb.cl_rcb[i];
    if (p_reg->in_use && p_reg->app_cb.p_phy_update_cb) {
      uint16_t conn_id = GATT_CREATE_CONN_ID(p_tcb->tcb_idx, p_reg->gatt_if);
      (*p_reg->app_cb.p_phy_update_cb)(p_reg->gatt_if, conn_id, tx_phy, rx_phy,
                                       status);
    }
  }
}

void gatt_notify_conn_update(const RawAddress& remote, uint16_t interval,
                             uint16_t latency, uint16_t timeout,
                             tHCI_STATUS status) {
  tGATT_TCB* p_tcb = gatt_find_tcb_by_addr(remote, BT_TRANSPORT_LE);

  if (!p_tcb) return;

  for (int i = 0; i < GATT_MAX_APPS; i++) {
    tGATT_REG* p_reg = &gatt_cb.cl_rcb[i];
    if (p_reg->in_use && p_reg->app_cb.p_conn_update_cb) {
      uint16_t conn_id = GATT_CREATE_CONN_ID(p_tcb->tcb_idx, p_reg->gatt_if);
      (*p_reg->app_cb.p_conn_update_cb)(p_reg->gatt_if, conn_id, interval,
                                        latency, timeout,
                                        static_cast<tGATT_STATUS>(status));
    }
  }
}

/** This function is called when GATT fixed channel is congested or uncongested
 */
static void gatt_le_cong_cback(const RawAddress& remote_bda, bool congested) {
  tGATT_TCB* p_tcb = gatt_find_tcb_by_addr(remote_bda, BT_TRANSPORT_LE);
  if (!p_tcb) return;

  /* if uncongested, check to see if there is any more pending data */
    gatt_channel_congestion(p_tcb, congested);
}

/*******************************************************************************
 *
 * Function         gatt_le_data_ind
 *
 * Description      This function is called when data is received from L2CAP.
 *                  if we are the originator of the connection, we are the ATT
 *                  client, and the received message is queued up for the
 *                  client.
 *
 *                  If we are the destination of the connection, we are the ATT
 *                  server, so the message is passed to the server processing
 *                  function.
 *
 * Returns          void
 *
 ******************************************************************************/
static void gatt_le_data_ind(uint16_t chan, const RawAddress& bd_addr,
                             BT_HDR* p_buf) {

  /* Find CCB based on bd addr */
  tGATT_TCB* p_tcb = gatt_find_tcb_by_addr(bd_addr, BT_TRANSPORT_LE);
  if (p_tcb) {
    if (gatt_get_ch_state(p_tcb) < GATT_CH_OPEN) {
      LOG(WARNING) << "ATT - Ignored L2CAP data while in state: "
                   << +gatt_get_ch_state(p_tcb);
    } else
      gatt_data_process(*p_tcb, L2CAP_ATT_CID, p_buf);
  }

  osi_free(p_buf);
}

/*******************************************************************************
 *
 * Function         gatt_l2cif_connect_ind
 *
 * Description      This function handles an inbound connection indication
 *                  from L2CAP. This is the case where we are acting as a
 *                  server.
 *
 * Returns          void
 *
 ******************************************************************************/
static void gatt_l2cif_connect_ind_cback(const RawAddress& bd_addr,
                                         uint16_t lcid,
                                         UNUSED_ATTR uint16_t psm, uint8_t id) {
  uint8_t result = L2CAP_CONN_OK;
  LOG(INFO) << "Connection indication cid = " << +lcid;

  /* new connection ? */
  tGATT_TCB* p_tcb = gatt_find_tcb_by_addr(bd_addr, BT_TRANSPORT_BR_EDR);
  if (p_tcb == NULL) {
    /* allocate tcb */
    p_tcb = gatt_allocate_tcb_by_bdaddr(bd_addr, BT_TRANSPORT_BR_EDR);
    if (p_tcb == NULL) {
      /* no tcb available, reject L2CAP connection */
      result = L2CAP_CONN_NO_RESOURCES;
    } else
      p_tcb->att_lcid = lcid;

  } else /* existing connection , reject it */
  {
    result = L2CAP_CONN_NO_RESOURCES;
  }

  /* If we reject the connection, send DisconnectReq */
  if (result != L2CAP_CONN_OK) {
    L2CA_DisconnectReq(lcid);
    return;
  }

  /* transition to configuration state */
  gatt_set_ch_state(p_tcb, GATT_CH_CFG);
}

static void gatt_on_l2cap_error(uint16_t lcid, uint16_t result) {
  tGATT_TCB* p_tcb = gatt_find_tcb_by_cid(lcid);
  if (p_tcb == nullptr) return;
  if (gatt_get_ch_state(p_tcb) == GATT_CH_CONN) {
    gatt_cleanup_upon_disc(p_tcb->peer_bda, GATT_CONN_L2C_FAILURE,
                           BT_TRANSPORT_BR_EDR);
  } else {
    gatt_l2cif_disconnect(lcid);
  }
}

/** This is the L2CAP connect confirm callback function */
static void gatt_l2cif_connect_cfm_cback(uint16_t lcid, uint16_t result) {
  tGATT_TCB* p_tcb;

  /* look up clcb for this channel */
  p_tcb = gatt_find_tcb_by_cid(lcid);
  if (!p_tcb) return;

  VLOG(1) << __func__
          << StringPrintf(" result: %d ch_state: %d, lcid:0x%x", result,
                          gatt_get_ch_state(p_tcb), p_tcb->att_lcid);

  if (gatt_get_ch_state(p_tcb) == GATT_CH_CONN && result == L2CAP_CONN_OK) {
    gatt_set_ch_state(p_tcb, GATT_CH_CFG);
  } else {
    gatt_on_l2cap_error(lcid, result);
  }
}

/** This is the L2CAP config confirm callback function */
void gatt_l2cif_config_cfm_cback(uint16_t lcid, uint16_t initiator,
                                 tL2CAP_CFG_INFO* p_cfg) {
  gatt_l2cif_config_ind_cback(lcid, p_cfg);

  /* look up clcb for this channel */
  tGATT_TCB* p_tcb = gatt_find_tcb_by_cid(lcid);
  if (!p_tcb) return;

  /* if in incorrect state */
  if (gatt_get_ch_state(p_tcb) != GATT_CH_CFG) return;

  gatt_set_ch_state(p_tcb, GATT_CH_OPEN);

  tGATTS_SRV_CHG* p_srv_chg_clt =
      gatt_is_bda_in_the_srv_chg_clt_list(p_tcb->peer_bda);
  if (p_srv_chg_clt != NULL) {
    gatt_chk_srv_chg(p_srv_chg_clt);
  } else {
    if (btm_sec_is_a_bonded_dev(p_tcb->peer_bda))
      gatt_add_a_bonded_dev_for_srv_chg(p_tcb->peer_bda);
  }

  /* send callback */
  gatt_send_conn_cback(p_tcb);
}

/** This is the L2CAP config indication callback function */
void gatt_l2cif_config_ind_cback(uint16_t lcid, tL2CAP_CFG_INFO* p_cfg) {
  /* look up clcb for this channel */
  tGATT_TCB* p_tcb = gatt_find_tcb_by_cid(lcid);
  if (!p_tcb) return;

  /* GATT uses the smaller of our MTU and peer's MTU  */
  if (p_cfg->mtu_present && p_cfg->mtu < L2CAP_DEFAULT_MTU)
    p_tcb->payload_size = p_cfg->mtu;
  else
    p_tcb->payload_size = L2CAP_DEFAULT_MTU;
}

/** This is the L2CAP disconnect indication callback function */
void gatt_l2cif_disconnect_ind_cback(uint16_t lcid, bool ack_needed) {

  /* look up clcb for this channel */
  tGATT_TCB* p_tcb = gatt_find_tcb_by_cid(lcid);
  if (!p_tcb) return;

  if (gatt_is_bda_in_the_srv_chg_clt_list(p_tcb->peer_bda) == NULL) {
    if (btm_sec_is_a_bonded_dev(p_tcb->peer_bda))
      gatt_add_a_bonded_dev_for_srv_chg(p_tcb->peer_bda);
  }
  /* send disconnect callback */
  gatt_cleanup_upon_disc(p_tcb->peer_bda, GATT_CONN_TERMINATE_PEER_USER,
                         BT_TRANSPORT_BR_EDR);
}

static void gatt_l2cif_disconnect(uint16_t lcid) {
  L2CA_DisconnectReq(lcid);

  /* look up clcb for this channel */
  tGATT_TCB* p_tcb = gatt_find_tcb_by_cid(lcid);
  if (!p_tcb) return;

  /* If the device is not in the service changed client list, add it... */
  if (gatt_is_bda_in_the_srv_chg_clt_list(p_tcb->peer_bda) == NULL) {
    if (btm_sec_is_a_bonded_dev(p_tcb->peer_bda))
      gatt_add_a_bonded_dev_for_srv_chg(p_tcb->peer_bda);
  }

  gatt_cleanup_upon_disc(p_tcb->peer_bda, GATT_CONN_TERMINATE_LOCAL_HOST,
                         BT_TRANSPORT_BR_EDR);
}

/** This is the L2CAP data indication callback function */
static void gatt_l2cif_data_ind_cback(uint16_t lcid, BT_HDR* p_buf) {
  /* look up clcb for this channel */
  tGATT_TCB* p_tcb = gatt_find_tcb_by_cid(lcid);
  if (p_tcb && gatt_get_ch_state(p_tcb) == GATT_CH_OPEN) {
    /* process the data */
    gatt_data_process(*p_tcb, lcid, p_buf);
  }

  osi_free(p_buf);
}

/** L2CAP congestion callback */
static void gatt_l2cif_congest_cback(uint16_t lcid, bool congested) {
  tGATT_TCB* p_tcb = gatt_find_tcb_by_cid(lcid);

  if (p_tcb != NULL) {
    gatt_channel_congestion(p_tcb, congested);
  }
}

/** Callback used to notify layer above about a connection */
static void gatt_send_conn_cback(tGATT_TCB* p_tcb) {
  uint8_t i;
  tGATT_REG* p_reg;
  uint16_t conn_id;

  std::set<tGATT_IF> apps =
      connection_manager::get_apps_connecting_to(p_tcb->peer_bda);

  /* notifying all applications for the connection up event */
  for (i = 0, p_reg = gatt_cb.cl_rcb; i < GATT_MAX_APPS; i++, p_reg++) {
    if (!p_reg->in_use) continue;

    if (apps.find(p_reg->gatt_if) != apps.end())
      gatt_update_app_use_link_flag(p_reg->gatt_if, p_tcb, true, true);

    if (p_reg->app_cb.p_conn_cb) {
      conn_id = GATT_CREATE_CONN_ID(p_tcb->tcb_idx, p_reg->gatt_if);
      (*p_reg->app_cb.p_conn_cb)(p_reg->gatt_if, p_tcb->peer_bda, conn_id,
                                 kGattConnected, GATT_CONN_OK,
                                 p_tcb->transport);
    }
  }

  /* Remove the direct connection */
  connection_manager::on_connection_complete(p_tcb->peer_bda);

  if (p_tcb->att_lcid == L2CAP_ATT_CID) {
    if (!p_tcb->app_hold_link.empty()) {
      /* disable idle timeout if one or more clients are holding the link
       * disable the idle timer */
      GATT_SetIdleTimeout(p_tcb->peer_bda, GATT_LINK_NO_IDLE_TIMEOUT,
                          p_tcb->transport, true /* is_active */);
    } else {
      if (bluetooth::common::init_flags::finite_att_timeout_is_enabled()) {
        GATT_SetIdleTimeout(p_tcb->peer_bda, GATT_LINK_IDLE_TIMEOUT_WHEN_NO_APP,
                            p_tcb->transport, false /* is_active */);
      }
    }
  }
}

/*******************************************************************************
 *
 * Function         gatt_le_data_ind
 *
 * Description      This function is called when data is received from L2CAP.
 *                  if we are the originator of the connection, we are the ATT
 *                  client, and the received message is queued up for the
 *                  client.
 *
 *                  If we are the destination of the connection, we are the ATT
 *                  server, so the message is passed to the server processing
 *                  function.
 *
 * Returns          void
 *
 ******************************************************************************/
void gatt_data_process(tGATT_TCB& tcb, uint16_t cid, BT_HDR* p_buf) {
  uint8_t* p = (uint8_t*)(p_buf + 1) + p_buf->offset;
  uint8_t op_code, pseudo_op_code;

  if (p_buf->len <= 0) {
    LOG(ERROR) << "invalid data length, ignore";
    return;
  }

  uint16_t msg_len = p_buf->len - 1;
  STREAM_TO_UINT8(op_code, p);

  /* remove the two MSBs associated with sign write and write cmd */
  pseudo_op_code = op_code & (~GATT_WRITE_CMD_MASK);

  if (pseudo_op_code >= GATT_OP_CODE_MAX) {
    /* Note: PTS: GATT/SR/UNS/BI-01-C mandates error on unsupported ATT request.
     */
    LOG(ERROR) << __func__
               << ": ATT - Rcvd L2CAP data, unknown cmd: " << loghex(op_code);
    gatt_send_error_rsp(tcb, cid, GATT_REQ_NOT_SUPPORTED, op_code, 0, false);
    return;
  }

  if (op_code == GATT_SIGN_CMD_WRITE) {
    gatt_verify_signature(tcb, cid, p_buf);
  } else {
    /* message from client */
    if ((op_code % 2) == 0)
      gatt_server_handle_client_req(tcb, cid, op_code, msg_len, p);
    else
      gatt_client_handle_server_rsp(tcb, cid, op_code, msg_len, p);
  }
}

/** Add a bonded dev to the service changed client list */
void gatt_add_a_bonded_dev_for_srv_chg(const RawAddress& bda) {
  tGATTS_SRV_CHG_REQ req;
  tGATTS_SRV_CHG srv_chg_clt;

  srv_chg_clt.bda = bda;
  srv_chg_clt.srv_changed = false;
  if (!gatt_add_srv_chg_clt(&srv_chg_clt)) return;

  req.srv_chg.bda = bda;
  req.srv_chg.srv_changed = false;
  if (gatt_cb.cb_info.p_srv_chg_callback)
    (*gatt_cb.cb_info.p_srv_chg_callback)(GATTS_SRV_CHG_CMD_ADD_CLIENT, &req,
                                          NULL);
}

/** This function is called to send a service chnaged indication to the
 * specified bd address */
void gatt_send_srv_chg_ind(const RawAddress& peer_bda) {
  static const uint16_t sGATT_DEFAULT_START_HANDLE =
      (uint16_t)osi_property_get_int32(
          "bluetooth.gatt.default_start_handle_for_srvc_change.value",
          GATT_GATT_START_HANDLE);
  static const uint16_t sGATT_LAST_HANDLE = (uint16_t)osi_property_get_int32(
      "bluetooth.gatt.last_handle_for_srvc_change.value", 0xFFFF);

  VLOG(1) << __func__;

  if (!gatt_cb.handle_of_h_r) return;

  uint16_t conn_id = gatt_profile_find_conn_id_by_bd_addr(peer_bda);
  if (conn_id == GATT_INVALID_CONN_ID) {
    LOG(ERROR) << "Unable to find conn_id for " << peer_bda;
    return;
  }

  uint8_t handle_range[GATT_SIZE_OF_SRV_CHG_HNDL_RANGE];
  uint8_t* p = handle_range;
  UINT16_TO_STREAM(p, sGATT_DEFAULT_START_HANDLE);
  UINT16_TO_STREAM(p, sGATT_LAST_HANDLE);
  GATTS_HandleValueIndication(conn_id, gatt_cb.handle_of_h_r,
                              GATT_SIZE_OF_SRV_CHG_HNDL_RANGE, handle_range);
}

/** Check sending service chnaged Indication is required or not if required then
 * send the Indication */
void gatt_chk_srv_chg(tGATTS_SRV_CHG* p_srv_chg_clt) {
  VLOG(1) << __func__ << " srv_changed=" << +p_srv_chg_clt->srv_changed;

  if (p_srv_chg_clt->srv_changed) {
    gatt_send_srv_chg_ind(p_srv_chg_clt->bda);
  }
}

/** This function is used to initialize the service changed attribute value */
void gatt_init_srv_chg(void) {
  tGATTS_SRV_CHG_REQ req;
  tGATTS_SRV_CHG_RSP rsp;
  tGATTS_SRV_CHG srv_chg_clt;

  VLOG(1) << __func__;
  if (!gatt_cb.cb_info.p_srv_chg_callback) {
    VLOG(1) << __func__ << " callback not registered yet";
    return;
  }

  bool status = (*gatt_cb.cb_info.p_srv_chg_callback)(
      GATTS_SRV_CHG_CMD_READ_NUM_CLENTS, NULL, &rsp);

  if (!(status && rsp.num_clients)) return;

  VLOG(1) << "num_srv_chg_clt_clients=" << +rsp.num_clients;
  uint8_t num_clients = rsp.num_clients;
  uint8_t i = 1; /* use one based index */
  while ((i <= num_clients) && status) {
    req.client_read_index = i;
    status = (*gatt_cb.cb_info.p_srv_chg_callback)(GATTS_SRV_CHG_CMD_READ_CLENT,
                                                   &req, &rsp);
    if (status) {
      memcpy(&srv_chg_clt, &rsp.srv_chg, sizeof(tGATTS_SRV_CHG));
      if (gatt_add_srv_chg_clt(&srv_chg_clt) == NULL) {
        LOG(ERROR) << "Unable to add a service change client";
        status = false;
      }
    }
    i++;
  }
}

/**This function is process the service changed request */
void gatt_proc_srv_chg(void) {
  RawAddress bda;
  tBT_TRANSPORT transport;
  uint8_t found_idx;

  VLOG(1) << __func__;

  if (!gatt_cb.cb_info.p_srv_chg_callback || !gatt_cb.handle_of_h_r) return;

  gatt_set_srv_chg();
  uint8_t start_idx = 0;
  while (gatt_find_the_connected_bda(start_idx, bda, &found_idx, &transport)) {
    tGATT_TCB* p_tcb = &gatt_cb.tcb[found_idx];

    bool send_indication = true;

    if (gatt_is_srv_chg_ind_pending(p_tcb)) {
      send_indication = false;
      VLOG(1) << "discard srv chg - already has one in the queue";
    }

    // Some LE GATT clients don't respond to service changed indications.
    char remote_name[BTM_MAX_REM_BD_NAME_LEN] = "";
    if (send_indication &&
        btif_storage_get_stored_remote_name(bda, remote_name)) {
      if (interop_match_name(INTEROP_GATTC_NO_SERVICE_CHANGED_IND,
                             remote_name)) {
        VLOG(1) << "discard srv chg - interop matched " << remote_name;
        send_indication = false;
      }
    }

    if (send_indication) gatt_send_srv_chg_ind(bda);

    start_idx = ++found_idx;
  }
}

/** This function set the ch_state in tcb */
void gatt_set_ch_state(tGATT_TCB* p_tcb, tGATT_CH_STATE ch_state) {
  if (!p_tcb) return;

  VLOG(1) << __func__ << ": old=" << +p_tcb->ch_state
          << " new=" << loghex(static_cast<uint8_t>(ch_state));
  p_tcb->ch_state = ch_state;
}

/** This function get the ch_state in tcb */
tGATT_CH_STATE gatt_get_ch_state(tGATT_TCB* p_tcb) {
  if (!p_tcb) return GATT_CH_CLOSE;

  VLOG(1) << "gatt_get_ch_state: ch_state=" << +p_tcb->ch_state;
  return p_tcb->ch_state;
}
