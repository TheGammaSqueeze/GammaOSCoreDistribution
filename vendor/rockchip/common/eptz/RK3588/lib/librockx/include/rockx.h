/****************************************************************************
 *
 *    Copyright (c) 2017 - 2022 by Rockchip Corp.  All rights reserved.
 *
 *    The material in this file is confidential and contains trade secrets
 *    of Rockchip Corporation. This is proprietary information owned by
 *    Rockchip Corporation. No part of this work may be disclosed,
 *    reproduced, copied, transmitted, or used in any way for any purpose,
 *    without the express written permission of Rockchip Corporation.
 *
 *****************************************************************************/

#ifndef _ROCKX_H
#define _ROCKX_H

#include "rockx_type.h"
#include "utils/rockx_config_util.h"
#include "utils/rockx_image_util.h"
#include "utils/rockx_tensor_util.h"

/**
 * @mainpage Index Page
 *
 * @section Introduction
 *
 * Rock-X SDK is a set of AI components based on the RK3399Pro/RK180X/RV1109 platform. Developers can quickly build AI
 * applications through the API interface provided by SDK.
 *
 * @section How-to-use
 *
 * @subsection Import-Library
 *
 * Developers can refer to the following example to import the library of RockX sdk in CMakeLists.txt
 * ```
 * # Find RockX Package
 * set(RockX_DIR <path-to-rockx-sdk>/sdk/rockx-rk3399pro-Android)
 * find_package(RockX REQUIRED)
 *
 * # Include RockX Header
 * include_directories(${RockX_INCLUDE_DIRS})
 *
 * # Link RockX Libraries
 * target_link_libraries(target_name ${RockX_LIBS})
 * ```
 *
 * @subsection Create-and-Destroy-Module
 *
 * Rock-X modules are initialized by the rockx_create function, and different modules are initialized by passing in
 * different rockx_module_t enumeration values. The sample code is as follows:
 * ```
 *  rockx_ret_t ret;
 *  rockx_handle_t face_det_handle;
 *  ret = rockx_create(&face_det_handle,
 *                      ROCKX_MODULE_FACE_DETECTION,
 *                      nullptr, 0);
 *  if (ret != ROCKX_RET_SUCCESS) {
 *      printf("init rockx module error %d\n", ret);
 *  }
 * ```
 *
 * If you don't need to use this module, you can release the handle by calling the rockx_destroy function. The sample
 * code is as follows:
 * ```
 * rockx_destroy(face_det_handle);
 * ```
 *
 */

#ifdef __cplusplus
extern "C" {
#endif

/// Create A Rockx Module
/// \param handle [out] The handle for created module
/// \param m [in] Enum of RockX module(@ref rockx_module_t)
/// \param config [in] Config for Rockx Module(@ref rockx_config_t)
/// \param config_size [in] Size of config
/// \return @ref rockx_ret_t
rockx_ret_t rockx_create(rockx_handle_t* handle, rockx_module_t m, void* config, size_t config_size);

/// Destroy A Rockx Module
/// \param handle [in] The handle of a created module (created by @ref rockx_create)
/// \return @ref rockx_ret_t
rockx_ret_t rockx_destroy(rockx_handle_t handle);

#ifdef __cplusplus
}  // extern "C"
#endif

#endif  // _ROCKX_H
