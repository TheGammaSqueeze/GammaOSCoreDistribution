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

#ifndef _ROCKX_TYPE_H
#define _ROCKX_TYPE_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * @brief Handle of a created RockX module
 */
typedef void* rockx_handle_t;

/**
 * @brief RockX modules define
 *
 * @details using for create a RockX module(See @ref rockx_create)
 */
typedef const char* rockx_module_t;

/**
 * @brief Pointer of async callback function
 */
typedef void (*rockx_async_callback_function)(void* result, size_t result_size, void* extra_data);

#define ROCKX_MAX_DIMS 16

/**
 * @brief Async callback
 */
typedef struct
{
    rockx_async_callback_function callback_func;
    void* extra_data;
} rockx_async_callback;

/**
 * @brief Return Value of RockX functions
 */
typedef enum {
    ROCKX_RET_SUCCESS = 0,          ///< Success
    ROCKX_RET_FAIL = -1,            ///< Fail
    ROCKX_RET_PARAM_ERR = -2,       ///< Input Param error
    ROCKX_UNINIT_ERR = -3,          ///< Module uninitialized
    ROCKX_RET_NO_SUPPORT_ERR = -4,  ///< Module no support
    ROCKX_RET_AUTH_FAIL = -99,      ///< Auth Error
    ROCKX_RET_NOT_SUPPORT = -98     ///< Device no support
} rockx_ret_t;

/**
 * @brief Image Pixel Format
 */
typedef enum {
    ROCKX_PIXEL_FORMAT_GRAY8 = 0,      ///< Gray8
    ROCKX_PIXEL_FORMAT_RGB888,         ///< RGB888
    ROCKX_PIXEL_FORMAT_BGR888,         ///< BGR888
    ROCKX_PIXEL_FORMAT_RGBA8888,       ///< RGBA8888
    ROCKX_PIXEL_FORMAT_BGRA8888,       ///< BGRA8888
    ROCKX_PIXEL_FORMAT_YUV420P_YU12,   ///< YUV420P YU12: YYYYYYYYUUVV
    ROCKX_PIXEL_FORMAT_YUV420P_YV12,   ///< YUV420P YV12: YYYYYYYYVVUU
    ROCKX_PIXEL_FORMAT_YUV420SP_NV12,  ///< YUV420SP NV12: YYYYYYYYUVUV
    ROCKX_PIXEL_FORMAT_YUV420SP_NV21,  ///< YUV420SP NV21: YYYYYYYYVUVU
    ROCKX_PIXEL_FORMAT_YUV422P_YU16,   ///< YUV422P YU16: YYYYYYYYUUUUVVVV
    ROCKX_PIXEL_FORMAT_YUV422P_YV16,   ///< YUV422P YV16: YYYYYYYYVVVVUUUU
    ROCKX_PIXEL_FORMAT_YUV422SP_NV16,  ///< YUV422SP NV16: YYYYYYYYUVUVUVUV
    ROCKX_PIXEL_FORMAT_YUV422SP_NV61,  ///< YUV422SP NV61: YYYYYYYYVUVUVUVU
    ROCKX_PIXEL_FORMAT_YUV422_YUYV,    ///< YUV422 YUYV: YUYVYUYV
    ROCKX_PIXEL_FORMAT_YUV422_YVYU,    ///< YUV422 YVYU: YVYUYVYU
    ROCKX_PIXEL_FORMAT_GRAY16,         ///< Gray16
    ROCKX_PIXEL_FORMAT_MAX,
} rockx_pixel_format;

/**
 * @brief Image Rotate Mode
 */
typedef enum {
    ROCKX_IMAGE_TRANSFORM_NONE = 0x00,        ///< Do not transform
    ROCKX_IMAGE_TRANSFORM_FLIP_H = 0x01,      ///< Flip image horizontally
    ROCKX_IMAGE_TRANSFORM_FLIP_V = 0x02,      ///< Flip image vertically
    ROCKX_IMAGE_TRANSFORM_ROTATE_90 = 0x04,   ///< Rotate image 90 degree
    ROCKX_IMAGE_TRANSFORM_ROTATE_180 = 0x03,  ///< Rotate image 180 degree
    ROCKX_IMAGE_TRANSFORM_ROTATE_270 = 0x07,  ///< Rotate image 270 defree
} rockx_image_transform_mode;

/**
 * @brief Data Type
 */
typedef enum {
    ROCKX_DTYPE_FLOAT32 = 0,  ///< Data type is float32
    ROCKX_DTYPE_FLOAT16,      ///< Data type is float16
    ROCKX_DTYPE_INT8,         ///< Data type is int8
    ROCKX_DTYPE_UINT8,        ///< Data type is uint8
    ROCKX_DTYPE_INT16,        ///< Data type is int16
    ROCKX_DTYPE_UINT16,       ///< data type is uint16
    ROCKX_DTYPE_INT32,        ///< data type is int32
    ROCKX_DTYPE_UINT32,       ///< data type is uint32
    ROCKX_DTYPE_INT64,        ///< data type is int64
    ROCKX_DTYPE_BOOL,
    ROCKX_DTYPE_TYPE_MAX
} rockx_data_type;

/**
 * @brief Tensor Format
 */
typedef enum {
    ROCKX_TENSOR_FORMAT_NCHW = 0,
    ROCKX_TENSOR_FORMAT_NHWC,
    ROCKX_TENSOR_FORMAT_NC1HWC2,
    ROCKX_TENSOR_FORMAT_MAX
} rockx_tensor_format;

/**
 * @brief Tensor quantization type
 * 
 */
typedef enum {
    ROCKX_TENSOR_QNT_NONE = 0,                           /* none. */
    ROCKX_TENSOR_QNT_DFP,                                /* dynamic fixed point. */
    ROCKX_TENSOR_QNT_AFFINE_ASYMMETRIC,                  /* asymmetric affine. */
    ROCKX_TENSOR_QNT_MAX
} rockx_tensor_qnt_type;

typedef enum {
    ROCKX_MEM_TYPE_CPU,
    ROCKX_MEM_TYPE_DMA
} rockx_mem_type;

/**
 * @brief Tensor quantization info
 * 
 */
typedef struct {
    rockx_tensor_qnt_type qnt_type; ///< the quantitative type of tensor. */
    int8_t fl;                      ///< fractional length for RKNN_TENSOR_QNT_DFP. */
    int32_t zp;                     ///< zero point for RKNN_TENSOR_QNT_AFFINE_ASYMMETRIC. */
    float scale;                    ///< scale for RKNN_TENSOR_QNT_AFFINE_ASYMMETRIC. */
} rockx_tensor_qnt_info_t;

/**
 * @brief Tensor
 */
typedef struct rockx_tensor_t
{
    rockx_data_type dtype;          ///< Data type (@ref rockx_data_type)
    rockx_tensor_format fmt;        ///< Tensor Format(@ref rockx_tensor_format)
    uint8_t n_dims;                 ///< Number of tensor dimension (0 < n_dims <= 4)
    uint32_t dims[ROCKX_MAX_DIMS];  ///< Tensor dimension
    uint32_t n_elems;               ///< the number of elements
    uint32_t size;                  ///< the bytes size of tensor
    rockx_tensor_qnt_info_t qnt_info;   ///< Quantization infomation
    void* data;                     ///< Tensor data virtual address
    void* data_phy_addr;            ///< Tensor data physical address
    int data_fd;                    ///< Tensor data dma buffer fd
    void* priv_data;                ///< Private date
} rockx_tensor_t;

/**
 * @brief Point
 */
typedef struct rockx_point_t
{
    int x;  ///< X Coordinate
    int y;  ///< Y Coordinate
    int z;  ///< Z Coordinate
} rockx_point_t;

/**
 * @brief Point (Float)
 */
typedef struct rockx_pointf_t
{
    float x;  ///< X Coordinate
    float y;  ///< Y Coordinate
    float z;  ///< Z Coordinate
} rockx_pointf_t;

/**
 * @brief Rectangle of Object Region
 */
typedef struct rockx_rect_t
{
    int left;    ///< Most left coordinate
    int top;     ///< Most top coordinate
    int right;   ///< Most right coordinate
    int bottom;  ///< Most bottom coordinate
} rockx_rect_t;

/**
 * @brief Rectangle of Object Region
 */
typedef struct rockx_rectf_t
{
    float left;    ///< Most left coordinate
    float top;     ///< Most top coordinate
    float right;   ///< Most right coordinate
    float bottom;  ///< Most bottom coordinate
} rockx_rectf_t;

/**
 * @brief Rectangle of Object Region
 */
typedef struct rockx_rectf_center_t
{
    float x_center;
    float y_center;
    float width;
    float height;
    float rotation;
} rockx_rectf_center_t;

/**
 * @brief Quadrangle
 */
typedef struct rockx_quad_t
{
    rockx_point_t left_top;      // Left top point
    rockx_point_t right_top;     // Right top point
    rockx_point_t left_bottom;   // Left bottom point
    rockx_point_t right_bottom;  // Right bottom point
} rockx_quad_t;

/**
 * @brief Quadrangle
 */
typedef struct rockx_quadf_t
{
    rockx_pointf_t left_top;      // Left top point
    rockx_pointf_t right_top;     // Right top point
    rockx_pointf_t left_bottom;   // Left bottom point
    rockx_pointf_t right_bottom;  // Right bottom point
} rockx_quadf_t;

/**
 * @brief Buffer memory
 */
typedef struct
{
    uint32_t size;                  ///< Buffer memory size
    rockx_mem_type type;            ///< Memory type
    void* virt_addr;                ///< Buffer memory virtual address
    void* phy_addr;                 ///< Memory physic address
    int fd;                         ///< DMA buffer memory fd
    uint32_t handle;                ///< DMA buffer memory handle
} rockx_mem_t;

/**
 * @brief Image
 */
typedef struct rockx_image_t
{
    uint8_t* data;                    ///< Image data
    uint32_t size;                    ///< Image data size
    uint8_t is_prealloc_buf;          ///< Image data buffer prealloc
    rockx_pixel_format pixel_format;  ///< Image pixel format (@ref rockx_pixel_format)
    uint32_t width;                   ///< Image Width
    uint32_t height;                  ///< Image Height
    int fd;                           ///< Image buffer fd
    uint8_t* data_phy;
} rockx_image_t;

/**
 * @brief Color
 */
typedef struct rockx_color_t
{
    uint8_t r;
    uint8_t g;
    uint8_t b;
} rockx_color_t;

/**
 * @brief Object Detection Result(include Face, CarPlate, Head, Object, etc...)
 */
typedef struct rockx_object_t
{
    int id;            ///< Track id
    int cls_idx;       ///< Class index
    rockx_rect_t box;  ///< Object Region
    float score;       ///< Object confidence score
} rockx_object_t;

/**
 * @brief Object Array Result
 */
typedef struct rockx_object_array_t
{
    int count;                   ///< Array Count(0 <= count < 128)
    rockx_object_t object[128];  ///< Objects
} rockx_object_array_t;

#ifdef __cplusplus
}  // extern "C"
#endif

#endif  // _ROCKX_TYPE_H