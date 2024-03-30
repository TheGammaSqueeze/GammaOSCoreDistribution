/* SPDX-License-Identifier: GPL-2.0-only */
/*
 * Copyright (C) 2010 - 2021 Novatek, Inc.
 *
 * $Revision: 83893 $
 * $Date: 2021-06-21 10:52:25 +0800 (週一, 21 六月 2021) $
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 */
#ifndef _LINUX_NVT_TOUCH_H
#define	_LINUX_NVT_TOUCH_H

#include <linux/delay.h>
#include <linux/input.h>
#include <linux/of.h>
#include <linux/spi/spi.h>
#include <linux/uaccess.h>
#include <linux/version.h>

#ifdef CONFIG_HAS_EARLYSUSPEND
#include <linux/earlysuspend.h>
#endif

#include "../../../gs-google/drivers/soc/google/vh/kernel/systrace.h"
#include "nt36xxx_mem_map.h"

#ifdef CONFIG_MTK_SPI
/* Please copy mt_spi.h file under mtk spi driver folder */
#include "mt_spi.h"
#endif

#ifdef CONFIG_SPI_MT65XX
#include <linux/platform_data/spi-mt65xx.h>
#endif

#include <drm/drm_panel.h> /* struct drm_panel */
#include <drm/drm_bridge.h> /* struct drm_bridge */
#include <drm/drm_connector.h> /* struct drm_connector */

#include "nt36xxx_goog.h"

#define NVT_MP_DEBUG 0

#if defined(CONFIG_SOC_GOOGLE)
#undef CONFIG_FB
#undef CONFIG_HAS_EARLYSUSPEND
#undef CONFIG_ARCH_QCOM
#undef CONFIG_ARCH_MSM
#endif

//---GPIO number---
#define NVTTOUCH_RST_PIN 980
#define NVTTOUCH_INT_PIN 943


//---INT trigger mode---
//#define IRQ_TYPE_EDGE_RISING 1
//#define IRQ_TYPE_EDGE_FALLING 2
#define INT_TRIGGER_TYPE IRQ_TYPE_EDGE_RISING


//---SPI driver info.---
#define NVT_SPI_NAME "NVT-ts"

#define NVT_DBG(fmt, args...)    pr_debug("[%s] %s %d: " fmt, NVT_SPI_NAME,\
					__func__, __LINE__, ##args)
#define NVT_LOG(fmt, args...)    pr_info("[%s] %s %d: " fmt, NVT_SPI_NAME,\
					__func__, __LINE__, ##args)
#define NVT_ERR(fmt, args...)    pr_err("[%s] %s %d: " fmt, NVT_SPI_NAME,\
					__func__, __LINE__, ##args)

//---Input device info.---
#define NVT_TS_NAME "NVTCapacitiveTouchScreen"
#define NVT_PEN_NAME "NVTCapacitivePen"
#define NVT_PEN_BATTERY_NAME "nvt-pen-battery"

//---Touch info.---
#define TOUCH_DEFAULT_MAX_WIDTH 1600
#define TOUCH_DEFAULT_MAX_HEIGHT 2560
#define TOUCH_MAX_FINGER_NUM 10
#define TOUCH_KEY_NUM 0
#if TOUCH_KEY_NUM > 0
extern const uint16_t touch_key_array[TOUCH_KEY_NUM];
#endif
//#define TOUCH_FORCE_NUM 1000
#ifdef TOUCH_FORCE_NUM
#define MT_PRESSURE_MAX TOUCH_FORCE_NUM
#else
#define MT_PRESSURE_MAX 256
#endif
//---for Pen---
//#define PEN_DISTANCE_SUPPORT
#define PEN_PRESSURE_MAX (4095)
#define PEN_DISTANCE_MAX (1)
#define PEN_TILT_MIN (-60)
#define PEN_TILT_MAX (60)
#define PEN_BATTERY_MAX (100)
#define PEN_BATTERY_MIN (0)

/* Enable only when module have tp reset pin and connected to host */
#define NVT_TOUCH_SUPPORT_HW_RST 1

//---Customerized func.---
#define NVT_TOUCH_PROC 1
#define NVT_TOUCH_EXT_PROC 1
#define NVT_TOUCH_EXT_API 1
#define NVT_TOUCH_EXT_USI 1
#define REPORT_PROTOCOL_A 1
#define REPORT_PROTOCOL_B 0
#define NVT_TOUCH_MP 1
#define BOOT_UPDATE_FIRMWARE 1
#define BOOT_UPDATE_FIRMWARE_MS_DELAY 100
#define BOOT_UPDATE_FIRMWARE_NAME "novatek_ts_fw.bin"
#define MP_UPDATE_FIRMWARE_NAME   "novatek_ts_mp.bin"
#define POINT_DATA_CHECKSUM 0
#define POINT_DATA_CHECKSUM_LEN 65
#define NVT_HEATMAP_COMP_NOT_READY_SIZE (0xFFF << 1)

//---ESD Protect.---
#define NVT_TOUCH_ESD_PROTECT 1
#define NVT_TOUCH_ESD_CHECK_PERIOD 1500	/* ms */
#define NVT_TOUCH_WDT_RECOVERY 1

#define CHECK_PEN_DATA_CHECKSUM 0

// MP
#define NORMAL_MODE 0x00
#define TEST_MODE_2 0x22
#define MP_MODE_CC 0x41
#define ENTER_ENG_MODE 0x61
#define LEAVE_ENG_MODE 0x62
#define FREQ_HOP_DISABLE 0x66
#define FREQ_HOP_ENABLE 0x65

// NVT_MT_CUSTOM for Cancel Mode Finger Status
#define NVT_MT_CUSTOM 1
#if NVT_MT_CUSTOM
#define ABS_MT_CUSTOM 0x3e
#define GRIP_TOUCH 0x04
#define PALM_TOUCH 0x05
#endif

// HEATMAP
#if NVT_TOUCH_EXT_API
#define HEATMAP_TOUCH_ADDR 0x23200
#define HEATMAP_PEN_ADDR 0x2A50A
enum {
	HEATMAP_DATA_TYPE_DISABLE = 0,
	HEATMAP_DATA_TYPE_TOUCH_RAWDATA = 1,
	HEATMAP_DATA_TYPE_TOUCH_RAWDATA_UNCOMP = HEATMAP_DATA_TYPE_TOUCH_RAWDATA,
	HEATMAP_DATA_TYPE_TOUCH_BASELINE = 2,
	HEATMAP_DATA_TYPE_TOUCH_BASELINE_UNCOMP = HEATMAP_DATA_TYPE_TOUCH_BASELINE,
	HEATMAP_DATA_TYPE_TOUCH_STRENGTH = 3,
	HEATMAP_DATA_TYPE_TOUCH_STRENGTH_UNCOMP = HEATMAP_DATA_TYPE_TOUCH_STRENGTH,
	HEATMAP_DATA_TYPE_TOUCH_STRENGTH_COMP = 4,
	HEATMAP_DATA_TYPE_PEN_STRENGTH_COMP = 5,
	HEATMAP_DATA_TYPE_UNSUPPORTED,
};
#define HEATMAP_HOST_CMD_DISABLE             0x90
#define HEATMAP_HOST_CMD_TOUCH_STRENGTH      0x91
#define HEATMAP_HOST_CMD_TOUCH_STRENGTH_COMP 0x92
#define HEATMAP_HOST_CMD_TOUCH_RAWDATA       0x93
#define HEATMAP_HOST_CMD_TOUCH_BASELINE      0x94
#endif

/* PEN */
#define PEN_HASH_SECTION_ID_ADDR 0x2B31D

/* FW History */
#define NVT_HISTORY_BUF_LEN		(65 * 4)

/* Gesture */
#define WAKEUP_GESTURE_OFF  0
#define WAKEUP_GESTURE_STTW 1
#define WAKEUP_GESTURE_DTTW 2
#define WAKEUP_GESTURE_DEFAULT WAKEUP_GESTURE_STTW

enum gesture_id : u8 {
	GESTURE_WORD_C = 12,
	GESTURE_WORD_W = 13,
	GESTURE_SINGLE_TAP = 14,
	GESTURE_DOUBLE_TAP = 15,
	GESTURE_WORD_Z = 16,
	GESTURE_WORD_M = 17,
	GESTURE_WORD_O = 18,
	GESTURE_WORD_e = 19,
	GESTURE_WORD_S = 20,
	GESTURE_SLIDE_UP = 21,
	GESTURE_SLIDE_DOWN = 22,
	GESTURE_SLIDE_LEFT = 23,
	GESTURE_SLIDE_RIGHT = 24,
	GESTURE_ID_MAX,
};

struct nvt_ts_data {
	struct spi_device *client;
	struct input_dev *input_dev;
	struct delayed_work nvt_fwu_work;
	uint16_t addr;
	int8_t phys[32];
#if defined(CONFIG_FB) && !defined(CONFIG_SOC_GOOGLE)
#if defined(CONFIG_DRM_PANEL) && (defined(CONFIG_ARCH_QCOM) || defined(CONFIG_ARCH_MSM))
	struct notifier_block drm_panel_notif;
#elif defined(_MSM_DRM_NOTIFY_H_)
	struct notifier_block drm_notif;
#else
	struct notifier_block fb_notif;
#endif
#elif defined(CONFIG_HAS_EARLYSUSPEND)
	struct early_suspend early_suspend;
#endif
	uint8_t fw_ver;
	uint8_t x_num;
	uint8_t y_num;
	uint16_t touch_width;
	uint16_t touch_height;
	uint16_t abs_x_max;		/* abs report start from 0 to 'width-1' */
	uint16_t abs_y_max;		/* abs report start from 0 to 'height-1' */
	uint8_t max_touch_num;
	uint8_t max_button_num;
	uint8_t touch_freq_index;
	uint8_t pen_freq_index;
	uint32_t int_trigger_type;
	int32_t irq_gpio;
	uint32_t irq_flags;
	int32_t reset_gpio;
	uint32_t reset_flags;
	struct mutex lock;
#if defined(CONFIG_SOC_GOOGLE)
	const struct nvt_ts_trim_id_table *trim_table;
#endif
	const struct nvt_ts_mem_map *mmap;
	uint8_t hw_crc;
	uint16_t nvt_pid;
	uint8_t *rbuf;
	uint8_t *xbuf;
	char history_buf[NVT_HISTORY_BUF_LEN];
	struct mutex xbuf_lock;
	bool probe_done;
	bool irq_enabled;
	bool pen_support;
	bool wgp_stylus;
	uint8_t x_gang_num;
	uint8_t y_gang_num;
	int8_t pen_input_idx;
	int8_t pen_phys[32];
	int8_t pen_name[32];
	struct input_dev *pen_input_dev;
#ifdef CONFIG_MTK_SPI
	struct mt_chip_conf spi_ctrl;
#endif
#ifdef CONFIG_SPI_MT65XX
	struct mtk_chip_config spi_ctrl;
#endif
	uint8_t report_protocol;
	u8 wkg_option;
	u8 wkg_default;
	uint8_t bTouchIsAwake;
	uint8_t pen_format_id;
	uint32_t pen_bat_capa;
	struct power_supply *pen_bat_psy;
#if NVT_TOUCH_EXT_USI
	char battery_serial_number_str[17]; /* 16 hex digits */
	uint32_t pen_serial_high; /* transducer serial number high 32 bits */
	uint32_t pen_serial_low;  /* transducer serial number low 32 bits */
	uint16_t pen_vid;
	uint16_t pen_pid;
#endif
#if NVT_TOUCH_EXT_API
	uint16_t dttw_touch_area_max;
	uint16_t dttw_touch_area_min;
	uint16_t dttw_contact_duration_max;
	uint16_t dttw_contact_duration_min;
	uint16_t dttw_tap_offset;
	uint16_t dttw_tap_gap_duration_max;
	uint16_t dttw_tap_gap_duration_min;
	uint16_t dttw_motion_tolerance;
	uint16_t dttw_detection_window_edge;
	uint8_t heatmap_data_type;
#endif

	const char *fw_name;
	const char *mp_fw_name;

	/*
	 * Time that the event was first received from
	 * the touch IC, acquired during hard interrupt,
	 * in CLOCK_MONOTONIC
	 */
	ktime_t timestamp;

	/*
	 * Used for event handler, suspend and resume work.
	 */
	struct pinctrl *pinctrl;
	struct drm_panel *active_panel;
	u32 initial_panel_index;

	struct completion bus_resumed;
	struct drm_bridge panel_bridge;
	struct drm_connector *connector;
	bool is_panel_lp_mode;
	struct delayed_work suspend_work;
	struct delayed_work resume_work;
	struct workqueue_struct *event_wq;

	struct mutex bus_mutex;
	ktime_t bugreport_ktime_start;
	u8 force_release_fw;

	/*
	 * Used for google touch interface.
	 */
	struct goog_touch_interface *gti;
	uint8_t heatmap_host_cmd;
	uint32_t heatmap_host_cmd_addr;
	uint32_t heatmap_out_buf_size;
	uint8_t *heatmap_out_buf;
	uint32_t heatmap_spi_buf_size;
	uint8_t *heatmap_spi_buf;
	uint32_t extra_spi_buf_size;
	uint8_t *extra_spi_buf;
	uint32_t touch_heatmap_comp_len;

	/*
	 * Stylus context used by touch_offload
	 */
#ifdef GOOG_TOUCH_INTERFACE
	struct TouchOffloadCoord pen_offload_coord;
#endif
	ktime_t pen_offload_coord_timestamp;
	u8 pen_active;
};

#if NVT_TOUCH_PROC
struct nvt_flash_data {
	rwlock_t lock;
};
#endif

typedef enum {
	RESET_STATE_INIT = 0xA0,// IC reset
	RESET_STATE_REK,		// ReK baseline
	RESET_STATE_REK_FINISH,	// baseline is ready
	RESET_STATE_NORMAL_RUN,	// normal run
	RESET_STATE_MAX  = 0xAF
} RST_COMPLETE_STATE;

typedef enum {
	EVENT_MAP_HOST_CMD                      = 0x50,
	EVENT_MAP_HANDSHAKING_or_SUB_CMD_BYTE   = 0x51,
	EVENT_MAP_RESET_COMPLETE                = 0x60,
	EVENT_MAP_FWINFO                        = 0x78,
	EVENT_MAP_PROJECTID                     = 0x9A,
} SPI_EVENT_MAP;

//---SPI READ/WRITE---
#define SPI_WRITE_MASK(a)	(a | 0x80)
#define SPI_READ_MASK(a)	(a & 0x7F)

#define DUMMY_BYTES (1)
#define NVT_TRANSFER_LEN	(63*1024)
#define NVT_READ_LEN		(4*1024)
#define NVT_XBUF_LEN		(NVT_TRANSFER_LEN+1+DUMMY_BYTES)

typedef enum {
	NVTWRITE = 0,
	NVTREAD  = 1
} NVT_SPI_RW;

//---extern structures---
extern struct nvt_ts_data *ts;

//---extern functions---
int32_t CTP_SPI_READ(struct spi_device *client, uint8_t *buf, uint16_t len);
int32_t CTP_SPI_WRITE(struct spi_device *client, uint8_t *buf, uint16_t len);
void nvt_bootloader_reset(void);
void nvt_eng_reset(void);
void nvt_sw_reset(void);
void nvt_sw_reset_idle(void);
void nvt_boot_ready(void);
void nvt_bld_crc_enable(void);
void nvt_fw_crc_enable(void);
void nvt_tx_auto_copy_mode(void);
int32_t nvt_check_fw_reset_state(RST_COMPLETE_STATE check_reset_state);
int32_t nvt_get_fw_info(void);
int32_t nvt_clear_fw_status(void);
int32_t nvt_check_fw_status(void);
int32_t nvt_check_spi_dma_tx_info(void);
int32_t nvt_set_page(uint32_t addr);
int32_t nvt_write_addr(uint32_t addr, uint8_t data);
extern void update_firmware_release(void);
extern int32_t nvt_update_firmware(const char *firmware_name, uint8_t full);
extern void nvt_change_mode(uint8_t mode);
extern void nvt_get_xdata_info(int32_t **ptr, int *size);
extern void nvt_read_mdata(uint32_t xdata_addr, uint32_t xdata_btn_addr);
extern int8_t nvt_switch_FreqHopEnDis(uint8_t FreqHopEnDis);
extern uint8_t nvt_get_fw_pipe(void);
extern void nvt_read_fw_history(uint32_t addr);
#if NVT_TOUCH_EXT_API
extern int32_t nvt_extra_api_init(void);
extern void nvt_extra_api_deinit(void);
extern void nvt_get_dttw_conf(void);
extern void nvt_set_dttw(bool check_result);
#endif
#if NVT_TOUCH_EXT_USI
extern int32_t nvt_extra_usi_init(void);
extern void nvt_extra_usi_deinit(void);
extern int32_t nvt_usi_clear_stylus_read_map(void);
extern int32_t nvt_usi_store_battery(const uint8_t *buf_bat);
extern int32_t nvt_usi_store_capability(const uint8_t *buf_cap);
extern int32_t nvt_usi_store_fw_version(const uint8_t *buf_fw_ver);
extern int32_t nvt_usi_store_gid(const uint8_t *buf_gid);
extern int32_t nvt_usi_store_hash_id(const uint8_t *buf_hash_id);
extern int32_t nvt_usi_store_session_id(const uint8_t *buf_session_id);
extern int32_t nvt_usi_store_freq_seed(const uint8_t *buf_freq_seed);

extern int32_t nvt_usi_get_battery(uint8_t *bat);
extern int32_t nvt_usi_get_fw_version(uint8_t *buf_fw_ver);
extern int32_t nvt_usi_get_vid_pid(uint16_t *vid, uint16_t *pid);
extern int32_t nvt_usi_get_serial_number(uint32_t *serial_high, uint32_t *serial_low);
extern int32_t nvt_usi_get_hash_id(uint8_t *buf_hash_id);
extern int32_t nvt_usi_get_session_id(uint8_t *buf_session_id);
extern int32_t nvt_usi_get_freq_seed(uint8_t *buf_freq_seed);
extern int32_t nvt_usi_get_validity_flags(uint16_t *validity_flags);

/* Flags for the responses of the USI read commands */
enum {
	USI_GID_FLAG		= 1U << 0,
	USI_BATTERY_FLAG	= 1U << 1,
	USI_CAPABILITY_FLAG	= 1U << 2,
	USI_FW_VERSION_FLAG	= 1U << 3,
	USI_CRC_FAIL_FLAG	= 1U << 4,
	USI_FAST_PAIR_FLAG	= 1U << 5,
	USI_NORMAL_PAIR_FLAG	= 1U << 6,
	USI_RESERVED1_FLAG	= 1U << 7,
	USI_RESERVED2_FLAG	= 1U << 8,
	USI_RESERVED3_FLAG	= 1U << 9,
	USI_RESERVED4_FLAG	= 1U << 10,
	USI_RESERVED5_FLAG	= 1U << 11,
	USI_HASH_ID_FLAG	= 1U << 12,
	USI_SESSION_ID_FLAG	= 1U << 13,
	USI_FREQ_SEED_FLAG	= 1U << 14,
	USI_INFO_FLAG		= 1U << 15,
};

enum {
	USI_GID_SIZE		= 12,
	USI_BATTERY_SIZE	= 2,
	USI_FW_VERSION_SIZE	= 2,
	USI_CAPABILITY_SIZE	= 12,
	USI_CRC_FAIL_SIZE	= 2,
	USI_FAST_PAIR_SIZE	= 2,
	USI_NORMAL_PAIR_SIZE	= 2,
	USI_RESERVED1_SIZE	= 22,
	USI_HASH_ID_SIZE	= 2,
	USI_SESSION_ID_SIZE	= 2,
	USI_FREQ_SEED_SIZE	= 1,
	USI_RESERVED2_SIZE	= 1,
	USI_INFO_FLAG_SIZE	= 2,
};

/* location of the data in the response buffer */
enum {
	USI_GID_OFFSET		= 1,
	USI_BATTERY_OFFSET	= USI_GID_OFFSET + USI_GID_SIZE,
	USI_FW_VERSION_OFFSET	= USI_BATTERY_OFFSET + USI_BATTERY_SIZE,
	USI_CAPABILITY_OFFSET	= USI_FW_VERSION_OFFSET + USI_FW_VERSION_SIZE,
	USI_CRC_FAIL_OFFSET	= USI_CAPABILITY_OFFSET + USI_CAPABILITY_SIZE,
	USI_FAST_PAIR_OFFSET	= USI_CRC_FAIL_OFFSET + USI_CRC_FAIL_SIZE,
	USI_NORMAL_PAIR_OFFSET	= USI_FAST_PAIR_OFFSET + USI_FAST_PAIR_SIZE,
	USI_RESERVED1_OFFSET	= USI_NORMAL_PAIR_OFFSET + USI_NORMAL_PAIR_SIZE,
	USI_HASH_ID_OFFSET	= USI_RESERVED1_OFFSET + USI_RESERVED1_SIZE,
	USI_SESSION_ID_OFFSET	= USI_HASH_ID_OFFSET + USI_HASH_ID_SIZE,
	USI_FREQ_SEED_OFFSET	= USI_SESSION_ID_OFFSET + USI_SESSION_ID_SIZE,
	USI_RESERVED2_OFFSET	= USI_FREQ_SEED_OFFSET + USI_FREQ_SEED_SIZE,
	USI_INFO_FLAG_OFFSET	= USI_RESERVED2_OFFSET + USI_RESERVED2_SIZE,
};
#endif

#if NVT_TOUCH_ESD_PROTECT
extern void nvt_esd_check_enable(uint8_t enable);
#endif /* #if NVT_TOUCH_ESD_PROTECT */

void nvt_irq_enable(bool enable);
inline const char *get_fw_name(void);
inline const char *get_mp_fw_name(void);
int nvt_ts_resume(struct device *dev);
int nvt_ts_suspend(struct device *dev);

void nvt_set_heatmap_host_cmd(struct nvt_ts_data *ts);
#endif /* _LINUX_NVT_TOUCH_H */
