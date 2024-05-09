#include "vdpp_proc.h"

#include "drmgralloc.h"
#include "vdpp_api.h"
#include "rk_type.h"
#include "video_tunnel.h"

#include <ui/GraphicBufferAllocator.h>
#include <cutils/properties.h>

#include <map>

#define VPDD_DEFAULT_WIDTH                   1920
#define VDPP_DEFAULT_HEIGHT                  1080
#define HAL_PIXEL_FORMAT_YCrCb_NV12          0x15

using namespace android;

unsigned int g_log_level;

bool LogLevel(LOG_LEVEL log_level) {
  return (g_log_level & log_level) > 0;
}

struct vdpp_dmsr_info dmsr_infos[] = {
    [VDPP_DMSR_STR_PRI_Y] = { .name = "str_pri_y", .defaultValue = 12, },
    [VDPP_DMSR_STR_SEC_Y] = { .name = "str_sec_y", .defaultValue = 6, },
    [VDPP_DMSR_DUMPING_Y] = { .name = "dumping_y", .defaultValue = 7, },
    [VDPP_DMSR_WGT_PRI_GAIN_EVEN_1] = { .name = "wgt_pri_gain_even_1", .defaultValue = 12, },
    [VDPP_DMSR_WGT_PRI_GAIN_EVEN_2] = { .name = "wgt_pri_gain_even_2", .defaultValue = 12, },
    [VDPP_DMSR_WGT_PRI_GAIN_ODD_1] = { .name = "wgt_pri_gain_odd_1", .defaultValue = 8, },
    [VDPP_DMSR_WGT_PRI_GAIN_ODD_2] = { .name = "wgt_pri_gain_odd_2", .defaultValue = 16, },
    [VDPP_DMSR_WGT_SEC_GAIN] = { .name = "wgt_sec_gain", .defaultValue = 5, },
    [VDPP_DMSR_BLK_FLAT_TH] = { .name = "blk_flat_th", .defaultValue = 40, },
    [VDPP_DMSR_CONTRAST_TO_CONF_MAP_X0] = { .name = "contrast_to_conf_map_x0", .defaultValue = 0, },
    [VDPP_DMSR_CONTRAST_TO_CONF_MAP_X1] = { .name = "contrast_to_conf_map_x1", .defaultValue = 1000, },
    [VDPP_DMSR_CONTRAST_TO_CONF_MAP_Y0] = { .name = "contrast_to_conf_map_y0", .defaultValue = 0, },
    [VDPP_DMSR_CONTRAST_TO_CONF_MAP_Y1] = {.name = "contrast_to_conf_map_y1", .defaultValue = 65535, },
    [VDPP_DMSR_DIFF_CORE_TH0] = {.name = "diff_core_th0", .defaultValue = 2, },
    [VDPP_DMSR_DIFF_CORE_TH1] = { .name = "diff_core_th1", .defaultValue = 5, },
    [VDPP_DMSR_DIFF_CORE_WGT0] = { .name = "diff_core_wgt0", .defaultValue = 16, },
    [VDPP_DMSR_DIFF_CORE_WGT1] = { .name = "diff_core_wgt1", .defaultValue = 16, },
    [VDPP_DMSR_DIFF_CORE_WGT2] = { .name = "diff_core_wgt2", .defaultValue = 12, },
    [VDPP_DMSR_EDGE_TH_LOW_ARR0] = { .name = "edge_th_low_arr_0", .defaultValue = 10, },
    [VDPP_DMSR_EDGE_TH_LOW_ARR1] = { .name = "edge_th_low_arr_1", .defaultValue = 5, },
    [VDPP_DMSR_EDGE_TH_LOW_ARR2] = { .name = "edge_th_low_arr_2", .defaultValue = 0, },
    [VDPP_DMSR_EDGE_TH_LOW_ARR3] = { .name = "edge_th_low_arr_3", .defaultValue = 0, },
    [VDPP_DMSR_EDGE_TH_LOW_ARR4] = { .name = "edge_th_low_arr_4", .defaultValue = 0, },
    [VDPP_DMSR_EDGE_TH_LOW_ARR5] = { .name = "edge_th_low_arr_5", .defaultValue = 0, },
    [VDPP_DMSR_EDGE_TH_LOW_ARR6] = { .name = "edge_th_low_arr_6", .defaultValue = 0, },
    [VDPP_DMSR_EDGE_TH_HIGH_ARR0] = { .name = "edge_th_high_arr_0", .defaultValue = 20, },
    [VDPP_DMSR_EDGE_TH_HIGH_ARR1] = { .name = "edge_th_high_arr_1", .defaultValue = 15, },
    [VDPP_DMSR_EDGE_TH_HIGH_ARR2] = { .name = "edge_th_high_arr_2", .defaultValue = 15, },
    [VDPP_DMSR_EDGE_TH_HIGH_ARR3] = { .name = "edge_th_high_arr_3", .defaultValue = 10, },
    [VDPP_DMSR_EDGE_TH_HIGH_ARR4] = { .name = "edge_th_high_arr_4", .defaultValue = 5, },
    [VDPP_DMSR_EDGE_TH_HIGH_ARR5] = { .name = "edge_th_high_arr_5", .defaultValue = 5, },
    [VDPP_DMSR_EDGE_TH_HIGH_ARR6] = { .name = "edge_th_high_arr_6", .defaultValue = 5, },
};

#define GET_VDPP_DMSR_PROPERTY_VALUE(func_str, name, outvalue) \
  snprintf(property, PROPERTY_VALUE_MAX, func_str, name);\
  ret = property_get(property, value, ""); \
  if(!ret){ \
      outvalue = dmsr_infos[i].defaultValue; \
  }else{ \
     outvalue = atoi(value); \
  }

static void vdpp_get_dmsr_params(union vdpp_api_content* params) {
    char value[PROPERTY_VALUE_MAX]={0};
    char property[PROPERTY_VALUE_MAX]={0};
    int ret;

    for (int i = 0;i<VDPP_DMSR_PROPERTY_COUNT;i++) {
        const char* name = dmsr_infos[i].name;
        GET_VDPP_DMSR_PROPERTY_VALUE("sys.vdpp.dmsr.%s", name, dmsr_infos[i].value);
    }
    params->dmsr.str_pri_y = dmsr_infos[0].value;
    params->dmsr.str_sec_y = dmsr_infos[1].value;
    params->dmsr.dumping_y = dmsr_infos[2].value;
    params->dmsr.wgt_pri_gain_even_1 = dmsr_infos[3].value;
    params->dmsr.wgt_pri_gain_even_2 = dmsr_infos[4].value;
    params->dmsr.wgt_pri_gain_odd_1 = dmsr_infos[5].value;
    params->dmsr.wgt_pri_gain_odd_2 = dmsr_infos[6].value;
    params->dmsr.wgt_sec_gain = dmsr_infos[7].value;
    params->dmsr.blk_flat_th = dmsr_infos[8].value;
    params->dmsr.contrast_to_conf_map_x0 = dmsr_infos[9].value;
    params->dmsr.contrast_to_conf_map_x1 = dmsr_infos[10].value;
    params->dmsr.contrast_to_conf_map_y0 = dmsr_infos[11].value;
    params->dmsr.contrast_to_conf_map_y1 = dmsr_infos[12].value;
    params->dmsr.diff_core_th0 = dmsr_infos[13].value;
    params->dmsr.diff_core_th1 = dmsr_infos[14].value;
    params->dmsr.diff_core_wgt0 = dmsr_infos[15].value;
    params->dmsr.diff_core_wgt1 = dmsr_infos[16].value;
    params->dmsr.diff_core_wgt2 = dmsr_infos[17].value;

    params->dmsr.edge_th_low_arr[0] = dmsr_infos[18].value;
    params->dmsr.edge_th_low_arr[1] = dmsr_infos[19].value;
    params->dmsr.edge_th_low_arr[2] = dmsr_infos[20].value;
    params->dmsr.edge_th_low_arr[3] = dmsr_infos[21].value;
    params->dmsr.edge_th_low_arr[4] = dmsr_infos[22].value;
    params->dmsr.edge_th_low_arr[5] = dmsr_infos[23].value;
    params->dmsr.edge_th_low_arr[6] = dmsr_infos[24].value;


    params->dmsr.edge_th_high_arr[0] = dmsr_infos[25].value;
    params->dmsr.edge_th_high_arr[1] = dmsr_infos[26].value;
    params->dmsr.edge_th_high_arr[2] = dmsr_infos[27].value;
    params->dmsr.edge_th_high_arr[3] = dmsr_infos[28].value;
    params->dmsr.edge_th_high_arr[4] = dmsr_infos[29].value;
    params->dmsr.edge_th_high_arr[5] = dmsr_infos[30].value;
    params->dmsr.edge_th_high_arr[6] = dmsr_infos[31].value;
}

static void vdpp_dump_dmsr_params(union vdpp_api_content* params) {
    ALOGD_IF(LogLevel(DBG_DEBUG), "vdpp_dump_dmsr_params");
    ALOGD_IF(LogLevel(DBG_DEBUG), "edge_th_low_arr: %d %d %d %d %d %d %d",
        params->dmsr.edge_th_low_arr[0], params->dmsr.edge_th_low_arr[1], params->dmsr.edge_th_low_arr[2] , params->dmsr.edge_th_low_arr[3],
        params->dmsr.edge_th_low_arr[4], params->dmsr.edge_th_low_arr[5], params->dmsr.edge_th_low_arr[6]);
    ALOGD_IF(LogLevel(DBG_DEBUG), "edge_th_high_arr: %d %d %d %d %d %d %d",
        params->dmsr.edge_th_high_arr[0], params->dmsr.edge_th_high_arr[1], params->dmsr.edge_th_high_arr[2] , params->dmsr.edge_th_high_arr[3],
        params->dmsr.edge_th_high_arr[4], params->dmsr.edge_th_high_arr[5], params->dmsr.edge_th_high_arr[6]);

    ALOGD_IF(LogLevel(DBG_DEBUG), "params->dmsr.str_pri_y: %d", params->dmsr.str_pri_y);
    ALOGD_IF(LogLevel(DBG_DEBUG), "params->dmsr.str_sec_y: %d", params->dmsr.str_sec_y);
    ALOGD_IF(LogLevel(DBG_DEBUG), "params->dmsr.dumping_y: %d", params->dmsr.dumping_y);
    ALOGD_IF(LogLevel(DBG_DEBUG), "params->dmsr.wgt_pri_gain_even_1: %d", params->dmsr.wgt_pri_gain_even_1);
    ALOGD_IF(LogLevel(DBG_DEBUG), "params->dmsr.wgt_pri_gain_even_2: %d", params->dmsr.wgt_pri_gain_even_2);
    ALOGD_IF(LogLevel(DBG_DEBUG), "params->dmsr.wgt_pri_gain_odd_1: %d", params->dmsr.wgt_pri_gain_odd_1);
    ALOGD_IF(LogLevel(DBG_DEBUG), "params->dmsr.wgt_pri_gain_odd_2: %d", params->dmsr.wgt_pri_gain_odd_2);
    ALOGD_IF(LogLevel(DBG_DEBUG), "params->dmsr.wgt_sec_gain: %d", params->dmsr.wgt_sec_gain);
    ALOGD_IF(LogLevel(DBG_DEBUG), "params->dmsr.blk_flat_th: %d", params->dmsr.blk_flat_th);
    ALOGD_IF(LogLevel(DBG_DEBUG), "params->dmsr.contrast_to_conf_map_x0: %d", params->dmsr.contrast_to_conf_map_x0);
    ALOGD_IF(LogLevel(DBG_DEBUG), "params->dmsr.contrast_to_conf_map_x1: %d", params->dmsr.contrast_to_conf_map_x1);
    ALOGD_IF(LogLevel(DBG_DEBUG), "params->dmsr.contrast_to_conf_map_y0: %d", params->dmsr.contrast_to_conf_map_y0);
    ALOGD_IF(LogLevel(DBG_DEBUG), "params->dmsr.contrast_to_conf_map_y1: %d", params->dmsr.contrast_to_conf_map_y1);
    ALOGD_IF(LogLevel(DBG_DEBUG), "params->dmsr.diff_core_th0: %d", params->dmsr.diff_core_th0);
    ALOGD_IF(LogLevel(DBG_DEBUG), "params->dmsr.diff_core_th1: %d", params->dmsr.diff_core_th1);
    ALOGD_IF(LogLevel(DBG_DEBUG), "params->dmsr.diff_core_wgt0: %d", params->dmsr.diff_core_wgt0);
    ALOGD_IF(LogLevel(DBG_DEBUG), "params->dmsr.diff_core_wgt1: %d", params->dmsr.diff_core_wgt1);
    ALOGD_IF(LogLevel(DBG_DEBUG), "params->dmsr.diff_core_wgt2: %d", params->dmsr.diff_core_wgt2);
    ALOGD_IF(LogLevel(DBG_DEBUG), "\n");
}

static bool vdpp_get_capaity(struct vdpp_dev* dev, vt_buffer_t* buffer) {
    uint32_t srcw, srch, usage;
    uint32_t dst_w = 0, dst_h = 0;
    float wscale_rate, hscale_rate;

    if (dev->drm_gralloc == NULL)
        dev->drm_gralloc = DrmGralloc::getInstance();
    srcw = dev->drm_gralloc->hwc_get_handle_width(buffer->handle);
    srch = dev->drm_gralloc->hwc_get_handle_height(buffer->handle);

    if (srcw > 1920 || srch > 1088)
        return false;

    if (dev->disp_rect.bottom && dev->disp_rect.right) {
        dst_w = dev->disp_rect.right - dev->disp_rect.left;
        dst_h = dev->disp_rect.bottom - dev->disp_rect.top;   
    }

    if (dst_w > 1920 || dst_h > 1088) {
        return false;
    }

    if (dst_w >= srcw)
        wscale_rate = (float)dst_w / srcw;
    else
        wscale_rate = (float)srcw / dst_w;

    if (dst_h >= srch)
        hscale_rate = (float)dst_h / srch;
    else
        hscale_rate = (float)srch / dst_h;

    if (wscale_rate > 6 || hscale_rate > 6) {
        ALOGE("vdpp: exceed scale factor src[%d,%d] dst[%d,%d]", srcw, srch, dst_w, dst_h);
        return false;
    }

    ALOGD_IF(LogLevel(DBG_DEBUG), "vdpp_get_capaity: src: %dx%d dst: %dx%d wscale_rate %.2f hscale_rate %.2f", srcw, srch, dst_w, dst_h, wscale_rate, hscale_rate);
    return true;
}

static MPP_RET vdpp_set_comon_params(vdpp_com_ctx *ctx, int srcw, int srch, int src_vir_w, int dstw, int dsth, int dst_vir_w) {
    struct vdpp_api_params params;
    MPP_RET ret = MPP_NOK;

    params.ptype = VDPP_PARAM_TYPE_COM;
    params.param.com.src_width = srcw;
    params.param.com.src_height = srch;
    params.param.com.src_vir_w = src_vir_w;
    params.param.com.sswap = VDPP_YUV_SWAP_SP_UV;
    params.param.com.dfmt = VDPP_FMT_YUV420;
    params.param.com.dst_width = dstw;
    params.param.com.dst_height = dsth;
    params.param.com.dst_vir_w = dst_vir_w;
    params.param.com.dswap = VDPP_YUV_SWAP_SP_UV;

    ret = ctx->ops->control(ctx->priv, VDPP_CMD_SET_COM_CFG, &params);
    if (ret)
        ALOGE("control %08x failed %d", VDPP_CMD_SET_COM_CFG, ret);

    return ret;
}

static MPP_RET vdpp_set_dmsr_params(vdpp_com_ctx *ctx) {
    struct vdpp_api_params params;
    MPP_RET ret = MPP_NOK;

    params.ptype = VDPP_PARAM_TYPE_DMSR;
    params.param.dmsr.enable = true;
    vdpp_get_dmsr_params(&params.param);
    vdpp_dump_dmsr_params(&params.param);

    ret = ctx->ops->control(ctx->priv, VDPP_CMD_SET_DMSR_CFG, &params);
    if (ret)
        ALOGE("control %08x failed %d", VDPP_CMD_SET_DMSR_CFG, ret);

    return ret;
}

static MPP_RET vdpp_set_img(vdpp_com_ctx *ctx, uint32_t w, uint32_t h,
                         VdppImg *img, int fd, VdppCmd cmd)
{
    RK_S32 y_size = w * h;
    img->mem_addr = fd;
    img->uv_addr = fd;
    img->uv_off = y_size;

    MPP_RET ret = ctx->ops->control(ctx->priv, cmd, img);
    if (ret)
        ALOGE("control %08x failed %d", cmd, ret);

    return ret;
}

static int vdpp_create_mem_pool(struct vdpp_dev* dev, buffer_handle_t handle) {
    GraphicBufferAllocator &allocator = GraphicBufferAllocator::get();
    int vir_w, vir_h, format, usage;

    format = HAL_PIXEL_FORMAT_YCrCb_NV12;//dev->drm_gralloc->hwc_get_handle_format(handle);
    usage = dev->drm_gralloc->hwc_get_handle_usage(handle);
    vir_w = 1920;
    vir_h = ALIGN(1080, 16);

    usage |= GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_EXTERNAL_DISP;

    for (int i = 0; i < VDPP_MAX_BUF_NUM; i++) {
        buffer_handle_t temp_buffer = NULL;
        status_t err;

        dev->hdl[i].vir_w = vir_w;
        dev->hdl[i].vir_h = vir_h;
        dev->hdl[i].usage = usage;
        dev->hdl[i].format = format;
        dev->hdl[i].slot = i;
        err = allocator.allocate(dev->hdl[i].vir_w,
                                 dev->hdl[i].vir_h,
                                 dev->hdl[i].format,
                                 1,
                                 dev->hdl[i].usage,
                                 &temp_buffer,
                                 &dev->hdl[i].stride,
                                 dev->tunnel_id,
                                 std::move("vdpp"));

        dev->hdl[i].vtBuffer = rk_vt_buffer_malloc();
        dev->hdl[i].vtBuffer->handle = (native_handle_t *)temp_buffer;
        dev->hdl[i].prime_fd = dev->drm_gralloc->hwc_get_handle_primefd(temp_buffer);
        dev->hdl[i].used = false;

        ALOGD_IF(LogLevel(DBG_DEBUG), "vdpp_proc_init : buffer %p stride %d prime_fd: %d", temp_buffer, dev->hdl[i].stride, dev->hdl[i].prime_fd);
    }

    return 0;
}

static void vdpp_destroy_mem_pool(struct vdpp_dev* dev) {
    GraphicBufferAllocator &allocator = GraphicBufferAllocator::get();

    for (int i = 0; i < VDPP_MAX_BUF_NUM; i++) {
        if (dev->hdl[i].vtBuffer && dev->hdl[i].vtBuffer->handle) {
            allocator.free(dev->hdl[i].vtBuffer->handle);
            dev->hdl[i].vtBuffer->handle = NULL;
        }

        if (dev->hdl[i].vtBuffer)
            rk_vt_buffer_free(&dev->hdl[i].vtBuffer);
    }
}

static void vdpp_dump_data(struct vdpp_dev* dev, buffer_handle_t srcbuf, buffer_handle_t dstbuf) {
    char name[64];
    char value[PROPERTY_VALUE_MAX]={0};
    FILE* fp;
    int src_vir_w, src_vir_h, dst_vir_w, dst_vir_h;
    void* pstsrc, *ptrdst;
    static int dump_cnt = 0;

    property_get("sys.vdpp.dump_data", value, "false");
    if (strcmp(value, "true") != 0)
        return;

    src_vir_w = dev->drm_gralloc->hwc_get_handle_byte_stride_workround(srcbuf);
    src_vir_h = dev->drm_gralloc->hwc_get_handle_height_stride(srcbuf);

    dst_vir_w = dev->drm_gralloc->hwc_get_handle_byte_stride_workround(dstbuf);
    dst_vir_h = dev->drm_gralloc->hwc_get_handle_height_stride(dstbuf);

    pstsrc = dev->drm_gralloc->hwc_get_handle_lock(srcbuf, src_vir_w, src_vir_h);
    ptrdst = dev->drm_gralloc->hwc_get_handle_lock(dstbuf, dst_vir_w, dst_vir_h);

    dump_cnt++;
    sprintf(name, "/data/dump_src_%d", dump_cnt);
    //////////////////////////dump src buffer /////////////////////////////////
    //fp = fopen(name, "wb");
    fp = fopen("/data/dump_src.yuv", "ab+");
    if (fp) {
        fwrite(ptrdst, 1, src_vir_w*src_vir_h*3/2, fp);
        fclose(fp);
    } else {
        ALOGE("failed to open name (%s): %s", name, strerror(errno));
    }

    //////////////////////////dump dst buffer /////////////////////////////////
    sprintf(name, "/data/dump_dst_%d", dump_cnt);
    fp = fopen(name, "wb");
    fp = fopen("/data/dump_dst.yuv", "ab+");
    if (fp) {
        fwrite(ptrdst, 1, dst_vir_w*dst_vir_h*3/2, fp);
        fclose(fp);
    } else {
        ALOGE("failed to open name (%s): %s", name, strerror(errno));
    }
    dev->drm_gralloc->hwc_get_handle_unlock(srcbuf);
    dev->drm_gralloc->hwc_get_handle_unlock(dstbuf);
    property_set("sys.vdpp.dump_data", "false");
}

void vdpp_update_disp_rect(struct vdpp_dev* dev, vt_buffer_t* buffer) {
    uint32_t srcw, srch, format, usage;
    float wscale_rate, hscale_rate;

    if (!dev || !buffer)
        return;

    srcw = dev->drm_gralloc->hwc_get_handle_width(buffer->handle);
    srch = dev->drm_gralloc->hwc_get_handle_height(buffer->handle);
    format = dev->drm_gralloc->hwc_get_handle_format(buffer->handle);

    if (srcw > 1920 || srch > 1088 || format != HAL_PIXEL_FORMAT_YCrCb_NV12) {
        dev->disp_rect.left = 0;
        dev->disp_rect.top = 0;
        dev->disp_rect.right = 0;
        dev->disp_rect.bottom = 0;
        ALOGD_IF(LogLevel(DBG_DEBUG), "VDPP: do no support current src! srcw %d srch %d format is not nv12(%d)", srcw, srch, format);
        return;
    }

    if (buffer->dis_rect.right && buffer->dis_rect.bottom) {
        dev->disp_rect.left = 0;
        dev->disp_rect.top = 0;
        dev->disp_rect.right = buffer->dis_rect.right - buffer->dis_rect.left;
        dev->disp_rect.bottom = buffer->dis_rect.bottom - buffer->dis_rect.top;

        if (dev->disp_rect.right > 1920)
            dev->disp_rect.right = 1920;
        if (dev->disp_rect.bottom > 1080)
            dev->disp_rect.bottom = 1080;
    }

    ALOGD_IF(LogLevel(DBG_DEBUG), "vdpp_update_disp_rect to [%dx%d]", dev->disp_rect.right, dev->disp_rect.bottom);

    vdpp_dev_init(dev, buffer->handle);
}

int vdpp_process_frame(struct vdpp_dev* dev, vt_buffer_t* srcbuf, vt_buffer_t* dstbuf) {
    VdppImg imgsrc, imgdst;
    vdpp_com_ctx* vdpp;
    buffer_handle_t src_hdl, dst_hdl;
    int srcw, srch, dstw, dsth;
    int src_vir_w, src_vir_h, dst_vir_w, dst_vir_h;
    int fdsrc, fddst;
    int ret = MPP_NOK;
    int srcsize, dstsize;
    void* pstsrc, *ptrdst;

    vdpp = (vdpp_com_ctx*)dev->ctx;
    if (!vdpp) {
        ALOGE("vdpp dev has not create!");
        return ret;
    }

    if (!srcbuf || !dstbuf) {
        ALOGE("buf is NULL src %p dst %p", srcbuf, dstbuf);
        return ret;
    }

    src_hdl = srcbuf->handle;
    dst_hdl = dstbuf->handle;
    if (!src_hdl || !dst_hdl) {
        ALOGD_IF(LogLevel(DBG_DEBUG), "%s-%d: invaild buffer src: %p dst: %p\n", __FUNCTION__, __LINE__, src_hdl, dst_hdl);
        return ret;
    }

    fdsrc = dev->drm_gralloc->hwc_get_handle_primefd(src_hdl);
    if (srcbuf->crop.right && srcbuf->crop.bottom) {
        srcw = srcbuf->crop.right - srcbuf->crop.left;
        srch = srcbuf->crop.bottom - srcbuf->crop.top;
    } else {
        srcw = dev->drm_gralloc->hwc_get_handle_width(src_hdl);
        srch = dev->drm_gralloc->hwc_get_handle_height(src_hdl);
    }
    src_vir_w = dev->drm_gralloc->hwc_get_handle_byte_stride_workround(src_hdl);
    src_vir_h = dev->drm_gralloc->hwc_get_handle_height_stride(src_hdl);
    srcsize = dev->drm_gralloc->hwc_get_handle_size(src_hdl);

    fddst = dev->drm_gralloc->hwc_get_handle_primefd(dst_hdl);

    dstw = dev->disp_rect.right - dev->disp_rect.left;
    dsth = dev->disp_rect.bottom - dev->disp_rect.top;

    dst_vir_w = dev->drm_gralloc->hwc_get_handle_byte_stride_workround(dst_hdl);
    dst_vir_h = dev->drm_gralloc->hwc_get_handle_height_stride(dst_hdl);
    dstsize = dev->drm_gralloc->hwc_get_handle_size(dst_hdl);

    vdpp_set_comon_params(vdpp, srcw, srch, src_vir_w, dstw, dsth, dst_vir_w);
    vdpp_set_dmsr_params(vdpp);

    vdpp_set_img(vdpp, src_vir_w, src_vir_h,
                 &imgsrc, fdsrc, VDPP_CMD_SET_SRC);
    vdpp_set_img(vdpp, dst_vir_w, dst_vir_h,
                 &imgdst, fddst, VDPP_CMD_SET_DST);

    ret = vdpp->ops->control(vdpp->priv, VDPP_CMD_RUN_SYNC, NULL);

    ALOGD_IF(LogLevel(DBG_DEBUG), "vdpp_process: dstw %d dsth %d", dstw, dsth);
    vdpp_dump_data(dev, src_hdl, dst_hdl);

    return ret;
}

bool vdpp_access(struct vdpp_dev* dev, vt_buffer_t* buffer) {
    bool enable_vdpp = false;
    char value[PROPERTY_VALUE_MAX]={0};

    property_get("sys.vdpp.debug", value, "0");
    g_log_level = atoi(value);

    if (!dev || !buffer)
        return enable_vdpp;

    property_get("sys.vdpp.enable", value, "1");
    if (atoi(value) == 0)
        return enable_vdpp;

    enable_vdpp = vdpp_get_capaity(dev, buffer);
    dev->vdpp_enable = enable_vdpp;

    ALOGD_IF(LogLevel(DBG_DEBUG), "vdpp_access: enable_vdpp %d", enable_vdpp);
    return enable_vdpp;
}

struct vdpp_buffer_handle* vdpp_get_unused_buf(struct vdpp_dev* dev) {
    int i;
    struct vdpp_buffer_handle* handle = NULL;

    pthread_mutex_lock(&dev->vdppLock);
    for (i = 0; i < VDPP_MAX_BUF_NUM; i++) {
        if (dev->hdl[i].used == false) {
            dev->hdl[i].used = true;
            handle = &dev->hdl[i];
            ALOGD_IF(LogLevel(DBG_DEBUG), "vdpp_get_unused_buf hdl[%d] buf %p", i, handle);
            break;
        }
    }

    if (!handle) {
        pthread_mutex_unlock(&dev->vdppLock);
        ALOGE("failed to find unsed buffer");
        return NULL;
    }

    pthread_mutex_unlock(&dev->vdppLock);
    return handle;
}

void vdpp_dev_init(struct vdpp_dev* dev, buffer_handle_t handle) {

    if (!dev || !handle)
        return;

    pthread_mutex_lock(&dev->vdppLock);
    if (!dev->initial) {
        dev->initial = true;
        vdpp_create_mem_pool(dev, handle);
    }
    pthread_mutex_unlock(&dev->vdppLock);
}

void vdpp_create_ctx(struct vdpp_dev* dev) {
    vdpp_com_ctx* vdpp = NULL;
    char value[PROPERTY_VALUE_MAX]={0};

    if (!dev)
        return;

    if (dev->ctx)
        return;

    vdpp = rockchip_vdpp_api_alloc_ctx();
    vdpp->ops->init(&vdpp->priv);
    dev->ctx = vdpp;
    dev->initial = false;
    pthread_mutex_init(&dev->vdppLock, NULL);

    property_get("sys.vdpp.debug", value, "0");
    g_log_level = atoi(value);

    if (dev->drm_gralloc == NULL)
        dev->drm_gralloc = DrmGralloc::getInstance();
}

void vdpp_destroy_ctx(struct vdpp_dev* dev) {
    if (dev && dev->ctx) {
        vdpp_com_ctx* vdpp = (vdpp_com_ctx*)dev->ctx;

        vdpp->ops->deinit(vdpp->priv);
        rockchip_vdpp_api_release_ctx((vdpp_com_ctx*)dev->ctx);
        vdpp_destroy_mem_pool(dev);
        dev->ctx = NULL;
    }
}

