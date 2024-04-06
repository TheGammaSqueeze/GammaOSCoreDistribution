/*
 *
 * Copyright 2023 Rockchip Electronics S.LSI Co. LTD
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

#ifndef VENDOR_STORAGE_H_
#define VENDOR_STORAGE_H_

typedef         unsigned short    uint16;
typedef         unsigned int        uint32;
typedef         unsigned char       uint8;

/*
 * vendor item data layout
 */
struct vendor_item {
	uint16  id;
	uint16  offset;
	uint16  size;
	uint16  flag;
};

#define VENDOR_SN_ID		1
#define VENDOR_WIFI_MAC_ID	2
#define VENDOR_LAN_MAC_ID	3
#define VENDOR_BLUETOOTH_ID	4
#define VENDOR_HDCP_14_HDMI_ID	5
#define VENDOR_HDCP_14_DP_ID	6
#define VENDOR_HDCP_2X_ID	7
#define VENDOR_DRM_KEY_ID	8
#define VENDOR_PLAYREADY_CERT_ID	9
#define VENDOR_ATTENTION_KEY_ID	10
#define VENDOR_PLAYREADY_ROOT_KEY_0_ID	11
#define VENDOR_PLAYREADY_ROOT_KEY_1_ID	12
#define VENDOR_HDCP_14_HDMIRX_ID	13
#define VENDOR_SENSOR_CALIBRATION_ID	14
#define VENDOR_IMEI_ID	15
#define VENDOR_LAN_RGMII_DL_ID	16
#define VENDOR_EINK_VCOM_ID	17
#define VENDOR_FIRMWARE_VER_ID	18
#define VENDOR_IMEI_HDCP_2X_RX_ID	19
#define VENDOR_IMEI_HDCP_2X_HDMIRX_ID	20
//21 – 31 RK reserved for future use

/*
 * vendor_storage_init must be call first
 * rerurn： ret=0 succes   ret<0 failed
 */
int vendor_storage_init(void);

/*
 * emmc_vendor_write 
 * id: item id
 * pbuf: vendor_item
 * size: vendor_item.size
 * rerurn： ret>0 succes   ret<0 failed
 */
int emmc_vendor_write(uint32 id, void *pbuf, uint32 size);

/*
 * emmc_vendor_read 
 * id: item id
 * pbuf: vendor_item
 * size: vendor_item.size
 * rerurn： ret>0 succes   ret<0 failed
 */
int emmc_vendor_read(uint32 id, void *pbuf, uint32 size);

#endif
