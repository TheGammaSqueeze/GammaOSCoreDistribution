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

#ifndef _ROCKX_OBJECT_DETECTION_H
#define _ROCKX_OBJECT_DETECTION_H

#include "rockx_type.h"

#ifdef __cplusplus
extern "C" {
#endif

extern rockx_module_t ROCKX_MODULE_OBJECT_DETECTION;     ///< Object detection
extern rockx_module_t ROCKX_MODULE_PERSON_DETECTION;     ///< Person detection
extern rockx_module_t ROCKX_MODULE_PERSON_DETECTION_V2;  ///< Person detection for small target
extern rockx_module_t ROCKX_MODULE_HEAD_DETECTION;       ///< Head detection

/**
 * @brief Object Detection Labels Table (91 Classes)
 *
 *    "???", "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", \n
 *    "trafficlight", "firehydrant", "???", "stopsign", "parkingmeter", "bench", "bird", "cat", "dog", "horse", \n
 *    "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "???", "backpack", "umbrella", "???", \n
 *    "???", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sportsball", "kite", "baseballbat", \n
 *    "baseballglove", "skateboard", "surfboard", "tennisracket", "bottle", "???", "wineglass", "cup", "fork", "knife",
 * \n "spoon", "bowl", "banana", "apple", "sandwich", "orange", "broccoli", "carrot", "hotdog", "pizza", \n "donut",
 * "cake", "chair", "couch", "pottedplant", "bed", "???", "diningtable", "???", "???", \n "toilet", "???", "tv",
 * "laptop", "mouse", "remote", "keyboard", "cellphone", "microwave", "oven", \n "toaster", "sink", "refrigerator",
 * "???", "book", "clock", "vase", "scissors", "teddybear", "hairdrier", \n "toothbrush" \n
 *
 */
extern const char* const ROCKX_OBJECT_DETECTION_LABELS_91[91];

/**
 * Object Detection (91 Class)
 * @param handle [in] Handle of a created ROCKX_MODULE_OBJECT_DETECTION module(created by @ref rockx_create)
 * @param in_img [in] Input image
 * @param object_array [out] Detection Result
 * @return @ref rockx_ret_t
 */
rockx_ret_t rockx_object_detect(rockx_handle_t handle, rockx_image_t* in_img, rockx_object_array_t* object_array);

/**
 * Head Detection
 * @param handle [in] Handle of a created ROCKX_MODULE_HEAD_DETECTION module(created by @ref rockx_create)
 * @param in_img [in] Input image
 * @param object_array [out] Detection Result
 * @return @ref rockx_ret_t
 */
rockx_ret_t rockx_head_detect(rockx_handle_t handle, rockx_image_t* in_img, rockx_object_array_t* object_array);

/**
 * Person Detection
 * @param handle [in] Handle of a created ROCKX_MODULE_BODY_DETECTION module(created by @ref rockx_create)
 * @param in_img [in] Input image
 * @param object_array [out] Detection Result
 * @return @ref rockx_ret_t
 */
rockx_ret_t rockx_person_detect(rockx_handle_t handle, rockx_image_t* in_img, rockx_object_array_t* object_array);

/**
 * Person Detection (input RGB and IR image)
 * @param handle [in] Handle of a created ROCKX_MODULE_BODY_DETECTION module(created by @ref rockx_create)
 * @param in_img [in] Input image
 * @param filter_level [in] Detect result filter level (0: disable; 1:normal; 2: strict; 3:night)
 * @param object_array [out] Detection Result
 * @return @ref rockx_ret_t
 */
rockx_ret_t rockx_person_detect2(rockx_handle_t handle, rockx_image_t* in_img, int filter_level,
                                 rockx_object_array_t* object_array);

#ifdef __cplusplus
}  // extern "C"
#endif

#endif  // _ROCKX_OBJECT_DETECTION_H