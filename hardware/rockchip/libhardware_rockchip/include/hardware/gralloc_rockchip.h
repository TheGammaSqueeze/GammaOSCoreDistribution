#ifndef LIBHARDWARE_GRALLOC_ROCKCHIP_H
#define LIBHARDWARE_GRALLOC_ROCKCHIP_H

#include <hardware/gralloc.h>

__BEGIN_DECLS

/**
 * perform operation commands for rk gralloc.
 * Helpers for using the non-type-safe perform() extension functions. Use
 * these helpers instead of calling perform() directly in your application.
 */
enum {
  /****************Implement****************/
  GRALLOC_MODULE_PERFORM_GET_HADNLE_PHY_ADDR       = 0x08100001,
  GRALLOC_MODULE_PERFORM_GET_HADNLE_PRIME_FD       = 0x08100002,
  GRALLOC_MODULE_PERFORM_GET_HADNLE_ATTRIBUTES     = 0x08100004,
  GRALLOC_MODULE_PERFORM_GET_INTERNAL_FORMAT       = 0x08100006,
  GRALLOC_MODULE_PERFORM_GET_HADNLE_WIDTH          = 0x08100008,
  GRALLOC_MODULE_PERFORM_GET_HADNLE_HEIGHT         = 0x0810000A,
  GRALLOC_MODULE_PERFORM_GET_HADNLE_STRIDE         = 0x0810000C,
  GRALLOC_MODULE_PERFORM_GET_HADNLE_BYTE_STRIDE    = 0x0810000E,
  GRALLOC_MODULE_PERFORM_GET_HADNLE_FORMAT         = 0x08100010,
  GRALLOC_MODULE_PERFORM_GET_HADNLE_SIZE           = 0x08100012,

  GRALLOC_MODULE_PERFORM_GET_RK_ASHMEM             = 0x08100014,
  GRALLOC_MODULE_PERFORM_SET_RK_ASHMEM             = 0x08100016,

  /* perform(const struct gralloc_module_t *mod,
   *     int op,
   *     buffer_handle_t buffer,
   *     int64_t offset);
   */
  GRALLOC_MODULE_PERFORM_SET_OFFSET_OF_DYNAMIC_HDR_METADATA
                                                   = 0x08100017,
  /* perform(const struct gralloc_module_t *mod,
   *     int op,
   *     buffer_handle_t buffer,
   *     int64_t* offset);
   */
  GRALLOC_MODULE_PERFORM_GET_OFFSET_OF_DYNAMIC_HDR_METADATA
                                                  = 0x08100018,

  /* perform(const struct gralloc_module_t *mod,
   *     int op,
   *     buffer_handle_t buffer,
   *     metadata_for_rkvdec_scaling_t** metadata);
   * 将 'buffer' 中的 metadata_for_rkvdec_scaling_t 实例, lock 到当前进程的虚拟地址空间, 并返回对应的地址.
   * 返回的地址被存储在 *metadata 中.
   * 之后, client 可以使用该地址对该 metadata_for_rkvdec_scaling_t 实例进行读写.
   * 读写完成后, client "必须" 对 'buffer' 调用 perform(..., GRALLOC_MODULE_PERFORM_UNLOCK_RKVDEC_SCALING_METADATA).
   */
  GRALLOC_MODULE_PERFORM_LOCK_RKVDEC_SCALING_METADATA   = 0x08100019,
  GRALLOC_MODULE_PERFORM_UNLOCK_RKVDEC_SCALING_METADATA = 0x0810001A,

  /* perform(const struct gralloc_module_t *mod,
   *     int op,
   *     buffer_handle_t buffer,
   *     uint64_t* buffer_id);
   */
  GRALLOC_MODULE_PERFORM_GET_BUFFER_ID= 0x0810001B,

  /* perform(const struct gralloc_module_t *mod,
   *     int op,
   *     buffer_handle_t buffer,
   *     int *usage);
   */
  GRALLOC_MODULE_PERFORM_GET_USAGE = 0x0feeff03,


  /****************Not Implement****************/
  GRALLOC_MODULE_PERFORM_GET_DRM_FD                = 0x08000002,
  /* perform(const struct gralloc_module_t *mod,
   *       int op,
   *       int drm_fd,
   *       buffer_handle_t buffer,
   *       struct hwc_drm_bo *bo);
   */
  GRALLOC_MODULE_PERFORM_DRM_IMPORT = 0xffeeff00,

  /* perform(const struct gralloc_module_t *mod,
   *       int op,
   *       buffer_handle_t buffer,
   *       void (*free_callback)(void *),
   *       void *priv);
   */
  GRALLOC_MODULE_PERFORM_SET_IMPORTER_PRIVATE = 0xffeeff01,

  /* perform(const struct gralloc_module_t *mod,
   *       int op,
   *       buffer_handle_t buffer,
   *       void (*free_callback)(void *),
   *       void **priv);
   */
  GRALLOC_MODULE_PERFORM_GET_IMPORTER_PRIVATE = 0xffeeff02,
};

//eotf type
enum supported_eotf_type {
        TRADITIONAL_GAMMA_SDR = 0,
        TRADITIONAL_GAMMA_HDR,
        SMPTE_ST2084,  /* HDR10 */
        HLG,           /* HLG */
        FUTURE_EOTF
};

//hdmi_output_colorimetry type
enum supported_hdmi_colorimetry {
	COLOR_METRY_NONE=0,
	COLOR_METRY_ITU_2020=9
};

struct hdr_static_metadata {
       uint16_t eotf;
       uint16_t type;
       uint16_t display_primaries_x[3];
       uint16_t display_primaries_y[3];
       uint16_t white_point_x;
       uint16_t white_point_y;
       uint16_t max_mastering_display_luminance;
       uint16_t min_mastering_display_luminance;
       uint16_t max_fall;
       uint16_t max_cll;
       uint16_t min_cll;
};

#define maxLayerNameLength		100
typedef struct rk_ashmem_t
{
    int32_t alreadyStereo;
    int32_t displayStereo;
    char LayerName[maxLayerNameLength + 1];

    /* dynamic_hdr_metadata(_buf) 在图像数据 buffer 中的 offset, 以 byte 为单位. */
    int64_t offset_of_dynamic_hdr_metadata;
} rk_ashmem_t;


#ifdef USE_GRALLOC_0
typedef struct metadata_for_rkvdec_scaling_t
{
    uint64_t version;
    // mask
    uint64_t requestMask;
    uint64_t replyMask;

    // buffer info
    uint32_t width;   // pixel_w
    uint32_t height;  // pixel_h
    uint32_t format;  // drm_fourcc
    uint64_t modifier;// modifier
    uint32_t usage;   // usage
    uint32_t pixel_stride; // pixel_stride

    // image info
    uint32_t srcLeft;
    uint32_t srcTop;
    uint32_t srcRight;
    uint32_t srcBottom;

    // buffer layout
    uint32_t layer_cnt;
    uint32_t fd[4];
    uint32_t offset[4];
    uint32_t byteStride[4];
} metadata_for_rkvdec_scaling_t;

/* RK 对 Gralloc 0.3 扩展的 usage flag bits. */
enum {
    /* 表征 client 要求 buffer (的 plane_0) 的 byte_stride 是 16 对齐.
     * 仅 配合 HAL_PIXEL_FORMAT_YCrCb_NV12 等特定 rk_video_formats 使用.
     *
     * 对 HAL_PIXEL_FORMAT_YCrCb_NV12, plane_0 的 byte_stride 就是 pixel_stride.
     */
    RK_GRALLOC_USAGE_STRIDE_ALIGN_16	= 0x00080000U,
    RK_GRALLOC_USAGE_STRIDE_ALIGN_64    = 0x00400000U,
    RK_GRALLOC_USAGE_STRIDE_ALIGN_128   = 0x00800000U,
    /* 表征 client 要求 buffer (的 plane_0) 的 byte_stride 是 256 的奇数倍.
     * 仅 配合 HAL_PIXEL_FORMAT_YCrCb_NV12 等特定 rk_video_formats 使用.
     */
    RK_GRALLOC_USAGE_STRIDE_ALIGN_256_ODD_TIMES
                                        = 0x00080000U | 0x00400000U,
    RK_GRALLOC_USAGE_STRIDE_ALIGN_MASK  = 0x00080000U | 0x00400000U | 0x00800000U,

    /* buffer may be used as a cursor */
    GRALLOC_USAGE_ROT_MASK              = 0x0F000000,
    /* the buffer would be used in rkvdec_scaling */
    GRALLOC_USAGE_RKVDEC_SCALING        = 0x01000000U,
    /* the buffer would be used for dynamic HDR (such as Vivid, ...) */
    GRALLOC_USAGE_DYNAMIC_HDR           = 0x02000000U,
    /* replacement of GRALLOC_USAGE_EXTERNAL_DISP,
     * which is treated as invalid by frameworks. */
    GRALLOC_USAGE__RK_EXT__EXTERNAL_DISP= 0x04000000U,
    /* mali p010 format */
    GRALLOC_USAGE_TO_USE_ARM_P010       = 0x0A000000,
    /* use Physically Continuous memory */
    GRALLOC_USAGE_TO_USE_PHY_CONT	= 0x08000000,
};
#else
/* Gralloc 4.0 中, RK 扩展的 usage flag bit,
 * 表征 "调用 alloc() 的 client 要求分配 底层 pages 是物理连续的 buffer".
 *
 * 注意:
 *	原始定义在 hardware/rockchip/libgralloc/bifrost 下的某个头文件中.
 *	但该文件的路径 可能随 DDK 升级而改变, 且外部要 include 该头文件困难.
 *	这里的定义 作为 public 定义, 提供给 其他 RK vendor 模块使用.
 */

// #define GRALLOC_USAGE_PRIVATE_0         (1ULL << 28)
// #define GRALLOC_USAGE_PRIVATE_1         (1ULL << 29)
// #define GRALLOC_USAGE_PRIVATE_2         (1ULL << 30)
// #define GRALLOC_USAGE_PRIVATE_3         (1ULL << 31)
        // 已经定义在 hardware/libhardware/include/hardware/gralloc.h 中.

#define GRALLOC_USAGE_PRIVATE_4         (1ULL << 63)
#define GRALLOC_USAGE_PRIVATE_5         (1ULL << 62)
#define GRALLOC_USAGE_PRIVATE_6         (1ULL << 61)
#define GRALLOC_USAGE_PRIVATE_7         (1ULL << 60)
#define GRALLOC_USAGE_PRIVATE_8         (1ULL << 59)
#define GRALLOC_USAGE_PRIVATE_9         (1ULL << 58)
#define GRALLOC_USAGE_PRIVATE_10        (1ULL << 57)
#define GRALLOC_USAGE_PRIVATE_11        (1ULL << 56)
#define GRALLOC_USAGE_PRIVATE_12        (1ULL << 55)
#define GRALLOC_USAGE_PRIVATE_13        (1ULL << 54)
#define GRALLOC_USAGE_PRIVATE_14        (1ULL << 53)
#define GRALLOC_USAGE_PRIVATE_15        (1ULL << 52)
#define GRALLOC_USAGE_PRIVATE_16        (1ULL << 51)
#define GRALLOC_USAGE_PRIVATE_17        (1ULL << 50)
#define GRALLOC_USAGE_PRIVATE_18        (1ULL << 49)
#define GRALLOC_USAGE_PRIVATE_19        (1ULL << 48)

#define RK_GRALLOC_USAGE_ALLOC_HEIGHT_ALIGN_MASK (GRALLOC_USAGE_PRIVATE_4 | GRALLOC_USAGE_PRIVATE_5)
/* 表征 "当前调用 alloc() 的 client 要求 buffer 的 alloc_height 是 8 对齐. */
#define RK_GRALLOC_USAGE_ALLOC_HEIGHT_ALIGN_8 (GRALLOC_USAGE_PRIVATE_4)
/* 16 对齐. */
#define RK_GRALLOC_USAGE_ALLOC_HEIGHT_ALIGN_16 (GRALLOC_USAGE_PRIVATE_5)
/* 64 对齐. */
#define RK_GRALLOC_USAGE_ALLOC_HEIGHT_ALIGN_64 (GRALLOC_USAGE_PRIVATE_4 | GRALLOC_USAGE_PRIVATE_5)

#define RK_GRALLOC_USAGE_PHY_CONTIG_BUFFER	GRALLOC_USAGE_PRIVATE_3

/* Gralloc 4.0 中, 表征 "调用 alloc() 的 client 要求分配的 buffer 的所有物理 page 的地址都在 4G 以内".
*/
#define RK_GRALLOC_USAGE_WITHIN_4G		GRALLOC_USAGE_PRIVATE_11
/* To indicate the buffer to allocate would be accessed by RGA.
 *
 * For the limitation of IP implementation, RGA2 could only access buffers with physical address within 4G.
 * There is no such limitation in RGA3.
 */
#define RK_GRALLOC_USAGE_RGA_ACCESS     RK_GRALLOC_USAGE_WITHIN_4G

/* Gralloc 4.0 中, 表征 "调用 alloc() 的 client 要求分配的 buffer 不是 AFBC 格式".
*/
#define MALI_GRALLOC_USAGE_NO_AFBC		GRALLOC_USAGE_PRIVATE_1

/* 表征 "当前 调用 alloc() 的 client 通过 width 指定了其预期的 buffer stride",
 * 即要求 gralloc 遵循 rk_implicit_alloc_semantic (即 满足 implicit_requirement_for_rk_gralloc_allocate).
 */
#define RK_GRALLOC_USAGE_SPECIFY_STRIDE         GRALLOC_USAGE_PRIVATE_2

/* 表征 client 要求 buffer (的 plane_0) 的 byte_stride 是 16 对齐.
 * 仅 配合 HAL_PIXEL_FORMAT_YCrCb_NV12 等特定 rk_video_formats 使用.
 *
 * 对 HAL_PIXEL_FORMAT_YCrCb_NV12, plane_0 的 byte_stride 就是 pixel_stride.
 */
#define RK_GRALLOC_USAGE_STRIDE_ALIGN_16        GRALLOC_USAGE_PRIVATE_10

/* 表征 client 要求 buffer (的 plane_0) 的 byte_stride 是 128 对齐.
 * 仅 配合 HAL_PIXEL_FORMAT_YCrCb_NV12 等特定 rk_video_formats 使用.
 */
#define RK_GRALLOC_USAGE_STRIDE_ALIGN_128       GRALLOC_USAGE_PRIVATE_9

/* 表征 client 要求 buffer (的 plane_0) 的 byte_stride 是 256 的奇数倍.
 * 仅 配合 HAL_PIXEL_FORMAT_YCrCb_NV12 等特定 rk_video_formats 使用.
 */
#define RK_GRALLOC_USAGE_STRIDE_ALIGN_256_ODD_TIMES     GRALLOC_USAGE_PRIVATE_8

/* 表征 client 要求 buffer (的 plane_0) 的 byte_stride 是 64 对齐.
 * 仅 配合 HAL_PIXEL_FORMAT_YCrCb_NV12 等特定 rk_video_formats 使用.
 */
#define RK_GRALLOC_USAGE_STRIDE_ALIGN_64        GRALLOC_USAGE_PRIVATE_7

/* YUV-only. */
#define MALI_GRALLOC_USAGE_YUV_COLOR_SPACE_DEFAULT      (0)
#define MALI_GRALLOC_USAGE_YUV_COLOR_SPACE_BT601        GRALLOC_USAGE_PRIVATE_18
#define MALI_GRALLOC_USAGE_YUV_COLOR_SPACE_BT709        GRALLOC_USAGE_PRIVATE_19
#define MALI_GRALLOC_USAGE_YUV_COLOR_SPACE_BT2020       (GRALLOC_USAGE_PRIVATE_18 | GRALLOC_USAGE_PRIVATE_19)
#define MALI_GRALLOC_USAGE_YUV_COLOR_SPACE_MASK         MALI_GRALLOC_USAGE_YUV_COLOR_SPACE_BT2020

#define MALI_GRALLOC_USAGE_RANGE_DEFAULT        (0)
#define MALI_GRALLOC_USAGE_RANGE_NARROW         GRALLOC_USAGE_PRIVATE_16
#define MALI_GRALLOC_USAGE_RANGE_WIDE           GRALLOC_USAGE_PRIVATE_17
#define MALI_GRALLOC_USAGE_RANGE_MASK           (GRALLOC_USAGE_PRIVATE_16 | GRALLOC_USAGE_PRIVATE_17)
#endif

/*---------------------------------------------------------------------------*/
__END_DECLS

#endif  // LIBHARDWARE_GRALLOC_ROCKCHIP_H
