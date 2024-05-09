/****************************************************************************
 *
 *    Copyright (c) 2018 - 2022 by Rockchip Corp.  All rights reserved.
 *
 *    The material in this file is confidential and contains trade secrets
 *    of Rockchip Corporation. This is proprietary information owned by
 *    Rockchip Corporation. No part of this work may be disclosed,
 *    reproduced, copied, transmitted, or used in any way for any purpose,
 *    without the express written permission of Rockchip Corporation.
 *
 *****************************************************************************/

#ifndef _ROCKX_CONFIG_UTIL_H
#define _ROCKX_CONFIG_UTIL_H

#include "../rockx_type.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * @brief Config Key of rockx log level
 *
 * Value define:
 *  0: ROCKX_LOG_ERROR (Default)
 *  1: ROCKX_LOG_WARN
 *  2: ROCKX_LOG_DEBUG
 *  3: ROCKX_LOG_INFO
 *  4: ROCKX_LOG_TRACE
 *
 */
#define ROCKX_CONFIG_LOG_LEVEL "ROCKX_LOG_LEVEL"

/**
 * @brief Config Key of rockx log file path
 *
 */
#define ROCKX_CONFIG_LOG_FILE "ROCKX_LOG_FILE"

/**
 * @brief Config Key of rockx data path
 */
#define ROCKX_CONFIG_DATA_PATH "ROCKX_DATA_PATH"

/**
 * @brief Config Key of rockx bin path
 */
#define ROCKX_CONFIG_BIN_PATH "ROCKX_BIN_PATH"

/**
 * @brief Config Key of rockx target device id
 */
#define ROCKX_CONFIG_TARGET_DEVICE_ID "ROCKX_TARGET_DEVICE_ID"

/**
 * @brief Config Key of target npu core (Only for RK3588)
 *
 * Value Define:
 *  0x0: Auto Mode
 *  0x1: Run on core 0
 *  0x2: Run on core 1
 *  0x4: Run on core 2
 */
#define ROCKX_CONFIG_TARGET_CORE "ROCKX_TARGET_CORE"

/**
 * @brief Config Key of licence key path
 */
#define ROCKX_CONFIG_LICENCE_KEY_PATH "ROCKX_LICENCE_KEY"

/**
 * @brief Config Key of licence key string
 */
#define ROCKX_CONFIG_LICENCE_KEY_STR "ROCKX_LICENCE_KEY_STR"

/**
 * @brief Config activate code
 */
#define ROCKX_CONFIG_ACTIVATE_CODE "ROCKX_ACTIVATE_CODE"

/**
 * @brief Config Key of librknn_runtime.so path (Only for RK1808/RV1109)
 */
#define ROCKX_CONFIG_RKNN_RUNTIME_PATH "ROCKX_LIBRKNN_RUNTIME_PATH"

/**
 * @brief Max number of config item
 */
#define ROCKX_CONFIG_MAX_ITEM 16

/**
 * @brief Max size of config key
 */
#define ROCKX_CONFIG_KEY_MAX 32

/**
 * @brief Congfig item
 */
typedef struct rockx_config_item_t
{
    char key[ROCKX_CONFIG_KEY_MAX];  ///< Key
    char* value;                     ///< Value
} rockx_config_item_t;

/**
 * @brief Congfig
 */
typedef struct rockx_config_t
{
    rockx_config_item_t configs[ROCKX_CONFIG_MAX_ITEM];
    int count;
} rockx_config_t;

/// Create a rockx_config_t
/// \return pointer of @ref rockx_config_t
rockx_config_t* rockx_create_config();

/// Release rockx_config_t
/// \param config [in] pointer of @ref rockx_config_t
/// \return @ref rockx_ret_t
rockx_ret_t rockx_release_config(rockx_config_t* config);

/// Add a config item to rockx_config_t
/// \param config [in] pointer of @ref rockx_config_t
/// \param key [in] config key
/// \param value [in] config value
/// \return @ref rockx_ret_t
rockx_ret_t rockx_add_config(rockx_config_t* config, const char* key, const char* value, const int value_size);

/// Get a config item value of rockx_config_t
/// \param config [in] pointer of @ref rockx_config_t
/// \param key [in] config key
/// \return @ref value
char* rockx_get_config(rockx_config_t* config, const char* key);

#ifdef __cplusplus
}  // extern "C"
#endif

#endif  //_ROCKX_CONFIG_UTIL_H
