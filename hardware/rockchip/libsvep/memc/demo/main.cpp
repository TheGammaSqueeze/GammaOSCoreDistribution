/****************************************************************************
 *
 *    Copyright (c) 2023 by Rockchip Corp.  All rights reserved.
 *
 *    The material in this file is confidential and contains trade secrets
 *    of Rockchip Corporation. This is proprietary information owned by
 *    Rockchip Corporation. No part of this work may be disclosed,
 *    reproduced, copied, transmitted, or used in any way for any purpose,
 *    without the express written permission of Rockchip Corporation.
 *
 *****************************************************************************/

#include "SvepMemc.h"
#include "hardware/hardware_rockchip.h"
#include "hardware/gralloc_rockchip.h"

#include <sync/sync.h>
#include <libsync/sw_sync.h>
#include <ui/GraphicBuffer.h>

#include "Buffer.h"

using namespace android;

#define ARRAY_SIZE(arr) (sizeof(arr) / sizeof((arr)[0]))

static char optstr[] = "i:m:f:c:H:";

static void usage(char *name){
    fprintf(stderr, "usage: %s [-imfH]\n", name);
    fprintf(stderr, "usage: %s -i 3840x2160+0+0:3840x2176@NV12 -m +proxy+split+osd=1 -f /data -c 100\n", name);
    fprintf(stderr, "\n Query options:\n\n");
    fprintf(stderr, "\t-i\t<crop_w>x<crop_h>[+<x>+<y>]:<stride_w>x<stride_h>@<format>[#afbc]\n");
    fprintf(stderr, "\t-m\t[+proxy][+split][+osd=1] or [+native]\n");
    fprintf(stderr, "\t-f\t<input_image_path>\n");
    fprintf(stderr, "\t-c\t<run_cnt> default cnt=1\n");
    fprintf(stderr, "\t-H\thelp\n");
    exit(0);
}

/*-------------------------------------------
                  Functions
-------------------------------------------*/
static inline int64_t getCurrentTimeUs(){
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return tv.tv_sec * 1000000 + tv.tv_usec;
}

struct image_arg {
    int x;
    int y;
    int crop_w;
    int crop_h;
    int stride_w;
    int stride_h;
    char format_str[5]; /* need to leave room for terminating \0 */
    uint32_t format;
    bool afbc;
    char image_path[80];
};

struct mode_arg {
    bool proxy_mode;
    bool split;
    bool osd;
    MEMC_OSD_MODE osd_mode;
    int run_cnt;
};

struct util_format_info {
    uint32_t format;
    const char *name;
};

static const struct util_format_info format_info[] = {
	/* YUV semi-planar */
	{ HAL_PIXEL_FORMAT_YCrCb_NV12, "NV12"},
	/* RGB16 */
	{ HAL_PIXEL_FORMAT_RGB_565, "RG16"},
	/* RGB24 */
	{ HAL_PIXEL_FORMAT_RGB_888, "RG24"},
	/* RGB32 */
	{ HAL_PIXEL_FORMAT_RGBA_8888, "RA24"},
};

uint32_t util_format(const char *name){
	unsigned int i;

	for (i = 0; i < ARRAY_SIZE(format_info); i++){
        if (!strcmp(format_info[i].name, name)){
            return format_info[i].format;
        }
    }

	return 0;
}

int parse_input_image_info(struct image_arg *pipe, const char *arg){
	/* Parse the input image info. */
	char *end;
	pipe->crop_w = strtoul(arg, &end, 10);
	if (*end != 'x'){
        return -EINVAL;
    }

	arg = end + 1;
	pipe->crop_h = strtoul(arg, &end, 10);
	if (*end != '+'){
        return -EINVAL;
    }

	arg = end + 1;
	pipe->x = strtoul(arg, &end, 10);
	if (*end != '+'){
        return -EINVAL;
    }

	arg = end + 1;
	pipe->y = strtoul(arg, &end, 10);
	if (*end != ':'){
        return -EINVAL;
    }

	arg = end + 1;
	pipe->stride_w = strtoul(arg, &end, 10);
	if (*end != 'x'){
        return -EINVAL;
    }

	arg = end + 1;
	pipe->stride_h = strtoul(arg, &end, 10);
	if (*end != '@'){
		return -EINVAL;
    }

	if (*end == '@') {
		strncpy(pipe->format_str, end + 1, 4);
		pipe->format_str[4] = '\0';
	} else {
		strcpy(pipe->format_str, "NV12");
	}

	pipe->format = util_format(pipe->format_str);
	if (pipe->format == 0) {
		fprintf(stderr, "unknown format %s\n", pipe->format_str);
		return -EINVAL;
	}

	arg = end + 5;
    if(*arg == '#'){
        if(!strcmp(arg, "#afbc")){
            pipe->afbc = true;
        }
    }

    return 0;
}

int parse_input_image_path(struct image_arg *pipe0, struct image_arg *pipe1, const char *arg){
    if(arg == NULL){
        return -EINVAL;
    }

    if(strlen(arg) > sizeof(pipe0->image_path)){
        fprintf(stderr, "%s is too long, max is %lu\n", arg, sizeof(pipe0->image_path));
        return -EINVAL;
    }
    sprintf(pipe0->image_path,"%s/memc_data/input_image0.bin",arg);
    sprintf(pipe1->image_path,"%s/memc_data/input_image1.bin",arg);

    return 0;
}

int parse_memc_mode(struct mode_arg *pipe, const char *arg){
    char *end;

    if(*arg != '+'){
        return -EINVAL;
    }
    arg++;

    if(*arg == 'n'){
        if(!strncmp(arg, "native", 6)){
            pipe->proxy_mode = false;
            arg = arg + 6;
            if(*arg == '+'){
                arg++;
            }
            return 0;
        }
    }

    if(*arg == 'p'){
        if(!strncmp(arg, "proxy", 5)){
            pipe->proxy_mode = true;
            arg = arg + 5;
            if(*arg == '+'){
                arg++;
            }
        }
    }

    if(*arg == 's'){
        if(!strncmp(arg, "split", 5)){
            pipe->split = true;
            arg = arg + 5;
            if(*arg == '+'){
                arg++;
            }
        }
    }

    if(*arg == 'o'){
        if(!strncmp(arg, "osd", 3)){
            pipe->osd = true;
            arg = arg + 3;
            if(*arg == '='){
                arg++;
                int osd_mode_tmp = strtoul(arg, &end, 10);
                switch (osd_mode_tmp)
                {
                case 0:
                    pipe->osd_mode = MEMC_OSD_DISABLE;
                    break;
                case 1:
                    pipe->osd_mode = MEMC_OSD_ENABLE_VIDEO;
                    break;
                case 2:
                    pipe->osd_mode = MEMC_OSD_ENABLE_VIDEO_ONELINE;
                    break;
                default:
                    printf("input invalid osd mode, set as default!\n");
                    pipe->osd_mode = MEMC_OSD_ENABLE_VIDEO;
                    break;
                }
            }else{
                pipe->osd_mode = MEMC_OSD_ENABLE_VIDEO;
            }
            if(*arg == '+'){
                arg++;
            }
        }
    }

    return 0;
}

// 解析输入参数
int parse_argv(int argc, char **argv, image_arg* input_image0, image_arg* input_image1, mode_arg* mode){
    int c;
    unsigned int args = 0;
    opterr = 0;
    bool exit = false;
	while ((c = getopt(argc, argv, optstr)) != -1) {
        args++;
        switch(c){
        case 'i':
            if(parse_input_image_info(input_image0, optarg) < 0){
                fprintf(stderr, "parse_input_image_info fail!\n");
                exit = true;
            }
            memcpy(input_image1, input_image0, sizeof(image_arg));
            break;
        case 'm':
            if(parse_memc_mode(mode, optarg) < 0){
                fprintf(stderr, "parse_memc_mode fail!\n");
                exit = true;
            }
            break;
        case 'f':
            if(parse_input_image_path(input_image0, input_image1, optarg) < 0){
                fprintf(stderr, "parse_input_image_path fail!\n");
                exit = true;
            }
            break;
        case 'c':
            mode->run_cnt = atoi(optarg);
            break;
        case 'H':
            exit = true;
            break;
        default:
            exit = true;
            break;
        }
    }
    if(args == 0 || exit){
        fprintf(stderr, "cmd_parse: crop[%d,%d,%d,%d] image[%d,%d,%s] afbc=%d path0=%s path1=%s proxy=%d split=%d osd_mode=%d\n",
                        input_image0->x,
                        input_image0->y,
                        input_image0->crop_w,
                        input_image0->crop_h,
                        input_image0->stride_w,
                        input_image0->stride_h,
                        input_image0->format_str,
                        input_image0->afbc,
                        input_image0->image_path,
                        input_image1->image_path,
                        mode->proxy_mode,
                        mode->split,
                        mode->osd_mode);
        usage(argv[0]);
        return -1;
    }

    fprintf(stderr, "cmd_parse: crop[%d,%d,%d,%d] image[%d,%d,%s] afbc=%d path0=%s path1=%s proxy=%d split=%d osd_mode=%d\n",
                    input_image0->x,
                    input_image0->y,
                    input_image0->crop_w,
                    input_image0->crop_h,
                    input_image0->stride_w,
                    input_image0->stride_h,
                    input_image0->format_str,
                    input_image0->afbc,
                    input_image0->image_path,
                    input_image1->image_path,
                    mode->proxy_mode,
                    mode->split,
                    mode->osd_mode);

  return 0;
}

int test_memc_proxy_mode(const image_arg &input_image0, const image_arg &input_image1,
                            MEMC_OSD_MODE osd_mode, bool contrast_mode, int loop_cnt);
int test_memc_native_mode(const image_arg &input_image0, const image_arg &input_image1, int loop_cnt);

int main(int argc, char** argv) {
    int ret = 0;
    bool memc_proxy_mode = false;
    bool memc_comparation_mode = true;
    int loop_cnt = 0;
    image_arg input_image0, input_image1;
    mode_arg mode;
    memset(&input_image0, 0x00, sizeof(image_arg));
    memset(&input_image0, 0x00, sizeof(image_arg));
    memset(&mode, 0x00, sizeof(mode));

    //0. 获取配置的参数
    if(parse_argv(argc, argv, &input_image0, &input_image1, &mode)){
        return -1;
    }
    memc_proxy_mode = mode.proxy_mode;
    if(memc_proxy_mode){
        memc_comparation_mode = mode.split;
    }else{
        printf("if not MEMC_PROXY_MODE, don't support spilt_mode and osd!\n");
    }
    if(mode.run_cnt > 0){
        loop_cnt = mode.run_cnt;
    }else{
        loop_cnt = 5;
    }

    if(memc_proxy_mode){
        ret = test_memc_proxy_mode(input_image0, input_image1, mode.osd_mode,
                                            memc_comparation_mode, loop_cnt);
        if(ret){
            printf("test_memc_proxy_mode fail!\n");
            return ret;
        }
    }else{
        ret = test_memc_native_mode(input_image0, input_image1, loop_cnt);
        if(ret){
            printf("test_memc_native_mode fail!\n");
            return ret;
        }
    }

    printf("memc-test end!\n");

    return 0;
}

int test_memc_proxy_mode(const image_arg &input_image0, const image_arg &input_image1,
                            MEMC_OSD_MODE osd_mode, bool contrast_mode, int loop_cnt){
    int ret = 0;
    //1. 获取 Memc 实例
    MemcProxyMode* memc = new MemcProxyMode();
    if(memc->Init(MEMC_VERSION, false)){
        printf("Memc init check fail\n");
        return -1;
    }

    //2. 设置 OSD 字幕模式
    static const wchar_t test_osd[] = L"oneLine osd: 测试";
    if(osd_mode == MEMC_OSD_ENABLE_VIDEO_ONELINE){
        if(memc->SetOsdMode(osd_mode, test_osd)){
            printf("SetOsdMode failed\n");
            return 1;
        }
    }else{
        if(memc->SetOsdMode(osd_mode, NULL)){
            printf("SetOsdMode failed\n");
            return 1;
        }
    }

    //3. 设置对比模式，提供MEMC输出数据与源数据的对比展示
    if(memc->SetContrastMode(contrast_mode)){
        printf("SetContrastMode failed\n");
        return 1;
    }

    //4. 申请输入 buffer
    Buffer *src_buffer0 = new Buffer(input_image0.stride_w,
                                    input_image0.stride_h,
                                    input_image0.format,
                                    "MemcTestSrcBuffer");
    if (src_buffer0->Init()) {
        printf("GraphicBuffer check error : %s\n",strerror(errno));
        return -1;
    }else{
        printf("GraphicBuffer check %s \n","ok");
    }
    if(src_buffer0->FillFromFile(input_image0.image_path)){
        printf("buffer: FillFromFile fail! path=%s\n",input_image0.image_path);
        return 1;
    }
    src_buffer0->DumpData();

    Buffer *src_buffer1 = new Buffer(input_image1.stride_w,
                                    input_image1.stride_h,
                                    input_image1.format,
                                    "MemcTestSrcBuffer");
    if (src_buffer1->Init()) {
        printf("GraphicBuffer check error : %s\n",strerror(errno));
        return -1;
    }else{
        printf("GraphicBuffer check %s \n","ok");
    }
    if(src_buffer1->FillFromFile(input_image1.image_path)){
        printf("buffer: FillFromFile fail! path=%s\n",input_image1.image_path);
        return 1;
    }
    src_buffer1->DumpData();

    //5. 配置输入图像的信息
    MemcImageInfo src0, src1;
    src0.mBufferInfo_.iFd_     = src_buffer0->GetFd();
    src0.mBufferInfo_.iWidth_  = src_buffer0->GetWidth();
    src0.mBufferInfo_.iHeight_ = src_buffer0->GetHeight();
    src0.mBufferInfo_.iFormat_ = src_buffer0->GetFormat();
    src0.mBufferInfo_.uMask_ = MEMC_BUFFER_MASK::NONE;  //Not AFBC
    src0.mBufferInfo_.iStride_ = src_buffer0->GetStride();
    src0.mBufferInfo_.uBufferId_ = src_buffer0->GetBufferId();
    src0.mBufferInfo_.iSize_ = src_buffer0->GetSize();
    src0.mCrop_.iLeft_  = input_image0.x;
    src0.mCrop_.iTop_   = input_image0.y;
    src0.mCrop_.iRight_ = input_image0.x + input_image0.crop_w;
    src0.mCrop_.iBottom_= input_image0.y + input_image0.crop_h;

    src1.mBufferInfo_.iFd_     = src_buffer1->GetFd();
    src1.mBufferInfo_.iWidth_  = src_buffer1->GetWidth();
    src1.mBufferInfo_.iHeight_ = src_buffer1->GetHeight();
    src1.mBufferInfo_.iFormat_ = src_buffer1->GetFormat();
    src1.mBufferInfo_.uMask_ = MEMC_BUFFER_MASK::NONE;  //Not AFBC
    src1.mBufferInfo_.iStride_ = src_buffer1->GetStride();
    src1.mBufferInfo_.uBufferId_ = src_buffer1->GetBufferId();
    src1.mBufferInfo_.iSize_ = src_buffer1->GetSize();
    src1.mCrop_.iLeft_  = input_image1.x;
    src1.mCrop_.iTop_   = input_image1.y;
    src1.mCrop_.iRight_ = input_image1.x + input_image1.crop_w;
    src1.mCrop_.iBottom_= input_image1.y + input_image1.crop_h;

    //6. 设置并获取MEMC_MODE处理模式信息
    MEMC_MODE memc_mode = MEMC_MODE::MEMC_UN_SUPPORT;
    if(memc->MatchMemcMode(&src0, &memc_mode)){
        printf("MatchMemcMode failed\n");
        return 1;
    }

    //7. 获取建议的输出图像参数
    MemcImageInfo require_dst;
    ret = memc->GetDstImageInfo(&require_dst);
    if(ret != MEMC_ERROR::MEMC_NO_ERROR){
        printf("Memc GetDstImageInfo fail!\n");
        return ret;
    }

    //8. 申请输出buffer
    Buffer *dst_buffer = new Buffer(require_dst.mBufferInfo_.iWidth_,
                                    require_dst.mBufferInfo_.iHeight_,
                                    require_dst.mBufferInfo_.iFormat_,
                                    "MemcTestDstBuffer");
    if (dst_buffer->Init()) {
        printf("GraphicBuffer check error : %s\n",strerror(errno));
        return 1;
    }else{
        printf("GraphicBuffer check %s \n","ok");
    }

    //9. 配置输出图像的信息
    MemcImageInfo dst;
    dst.mBufferInfo_.iFd_     = dst_buffer->GetFd();
    dst.mBufferInfo_.iWidth_  = dst_buffer->GetWidth();
    dst.mBufferInfo_.iHeight_ = dst_buffer->GetHeight();
    dst.mBufferInfo_.iFormat_ = dst_buffer->GetFormat();
    dst.mBufferInfo_.iStride_ = dst_buffer->GetStride();
    dst.mBufferInfo_.uBufferId_ = dst_buffer->GetBufferId();
    dst.mBufferInfo_.iSize_ = dst_buffer->GetSize();

    dst.mCrop_.iLeft_  = require_dst.mCrop_.iLeft_;
    dst.mCrop_.iTop_   = require_dst.mCrop_.iTop_;
    dst.mCrop_.iRight_ = require_dst.mCrop_.iRight_;
    dst.mCrop_.iBottom_= require_dst.mCrop_.iBottom_;
    printf("dst[w=%d,h=%d,f=%d][l,t,r,b]=[%d,%d,%d,%d]\n",dst.mBufferInfo_.iWidth_,dst.mBufferInfo_.iHeight_,dst.mBufferInfo_.iFormat_,
                                                            dst.mCrop_.iLeft_,dst.mCrop_.iTop_,dst.mCrop_.iRight_,dst.mCrop_.iBottom_);

    int memc_fence = -1;
    for(int i = 0; i < loop_cnt; i++){
        //10. 异步执行 Memc 处理
        MemcImageInfo src;
        if(i %2 == 0){
            src = src0;
        }else{
            src = src1;
        }
        ret = memc->RunAsync(&src, &dst, &memc_fence);
        if(ret){
            printf("RunAsync fail!\n");
            return ret;
        }

        //11. 等待 Memc 算法完成，可在另一个线程等待
        if(memc_fence > 0){
            int sync_ret = sync_wait(memc_fence, 1500);
            if (sync_ret) {
                printf("Failed to wait fence %d/%d 1500ms!\n", memc_fence, sync_ret);
            }else{
                printf("wait fence %d success!\n", memc_fence);
            }
            close(memc_fence);
        }

        //12. 检查输出图像是否正确
        dst_buffer->DumpData();
        printf("Memc dump data to /data/dump success!\n");
    }
    //13. 当视频结束、切换显示模式、切换Memc模式时要清理 Memc 内部资源
    memc->ClearResource();

    delete src_buffer0;
    delete src_buffer1;
    delete dst_buffer;
    printf("test_memc_proxy_mode end!\n");

    return 0;
}

int test_memc_native_mode(const image_arg &input_image0, const image_arg &input_image1, int loop_cnt){
    int ret = 0;
    //1. 获取 Memc 实例
    MemcNativeMode* memc = new MemcNativeMode();
    if(memc->Init(MEMC_VERSION, false)){
        printf("Memc init check fail\n");
        return -1;
    }

    //2. 申请输入 buffer
    Buffer *src_buffer0 = new Buffer(input_image0.stride_w,
                                    input_image0.stride_h,
                                    input_image0.format,
                                    "MemcTestSrcBuffer");
    if (src_buffer0->Init()) {
        printf("GraphicBuffer check error : %s\n",strerror(errno));
        return -1;
    }else{
        printf("GraphicBuffer check %s \n","ok");
    }
    if(src_buffer0->FillFromFile(input_image0.image_path)){
        printf("buffer: FillFromFile fail! path=%s\n",input_image0.image_path);
        return 1;
    }
    src_buffer0->DumpData();

    Buffer *src_buffer1 = new Buffer(input_image1.stride_w,
                                    input_image1.stride_h,
                                    input_image1.format,
                                    "MemcTestSrcBuffer");
    if (src_buffer1->Init()) {
        printf("GraphicBuffer check error : %s\n",strerror(errno));
        return -1;
    }else{
        printf("GraphicBuffer check %s \n","ok");
    }
    if(src_buffer1->FillFromFile(input_image1.image_path)){
        printf("buffer: FillFromFile fail! path=%s\n",input_image1.image_path);
        return 1;
    }
    src_buffer1->DumpData();

    //3. 配置2个输入图像的信息
    MemcImageInfo src0, src1;
    src0.mBufferInfo_.iFd_     = src_buffer0->GetFd();
    src0.mBufferInfo_.iWidth_  = src_buffer0->GetWidth();
    src0.mBufferInfo_.iHeight_ = src_buffer0->GetHeight();
    src0.mBufferInfo_.iFormat_ = src_buffer0->GetFormat();
    src0.mBufferInfo_.uMask_ = MEMC_BUFFER_MASK::NONE;  //Not AFBC
    src0.mBufferInfo_.iStride_ = src_buffer0->GetStride();
    src0.mBufferInfo_.uBufferId_ = src_buffer0->GetBufferId();
    src0.mBufferInfo_.iSize_ = src_buffer0->GetSize();
    src0.mCrop_.iLeft_  = 0;
    src0.mCrop_.iTop_   = 0;
    src0.mCrop_.iRight_ = src_buffer0->GetWidth();
    src0.mCrop_.iBottom_= src_buffer0->GetHeight();

    src1.mBufferInfo_.iFd_     = src_buffer1->GetFd();
    src1.mBufferInfo_.iWidth_  = src_buffer1->GetWidth();
    src1.mBufferInfo_.iHeight_ = src_buffer1->GetHeight();
    src1.mBufferInfo_.iFormat_ = src_buffer1->GetFormat();
    src1.mBufferInfo_.uMask_ = MEMC_BUFFER_MASK::NONE;  //Not AFBC
    src1.mBufferInfo_.iStride_ = src_buffer1->GetStride();
    src1.mBufferInfo_.uBufferId_ = src_buffer1->GetBufferId();
    src1.mBufferInfo_.iSize_ = src_buffer1->GetSize();
    src1.mCrop_.iLeft_  = 0;
    src1.mCrop_.iTop_   = 0;
    src1.mCrop_.iRight_ = src_buffer1->GetWidth();
    src1.mCrop_.iBottom_= src_buffer1->GetHeight();

    //4. 设置并获取MEMC_MODE处理模式信息
    MEMC_MODE memc_mode = MEMC_MODE::MEMC_UN_SUPPORT;
    if(memc->MatchMemcMode(&src0, &memc_mode)){
        printf("MatchMemcMode failed\n");
        return 1;
    }

    //5. 获取建议的输出图像参数
    MemcImageInfo require_dst;
    ret = memc->GetDstImageInfo(&require_dst);
    if(ret != MEMC_ERROR::MEMC_NO_ERROR){
        printf("Memc GetDstImageInfo fail!\n");
        return ret;
    }

    //6. 申请输出buffer
    Buffer *dst_buffer = new Buffer(require_dst.mBufferInfo_.iWidth_,
                                    require_dst.mBufferInfo_.iHeight_,
                                    require_dst.mBufferInfo_.iFormat_,
                                    "MemcTestDstBuffer");
    if (dst_buffer->Init()) {
        printf("GraphicBuffer check error : %s\n",strerror(errno));
        return 1;
    }else{
        printf("GraphicBuffer check %s \n","ok");
    }

    //7. 配置输出图像的信息
    MemcImageInfo dst;
    dst.mBufferInfo_.iFd_     = dst_buffer->GetFd();
    dst.mBufferInfo_.iWidth_  = dst_buffer->GetWidth();
    dst.mBufferInfo_.iHeight_ = dst_buffer->GetHeight();
    dst.mBufferInfo_.iFormat_ = dst_buffer->GetFormat();
    dst.mBufferInfo_.iStride_ = dst_buffer->GetStride();
    dst.mBufferInfo_.uBufferId_ = dst_buffer->GetBufferId();
    dst.mBufferInfo_.iSize_ = dst_buffer->GetSize();
    dst.mCrop_.iLeft_  = require_dst.mCrop_.iLeft_;
    dst.mCrop_.iTop_   = require_dst.mCrop_.iTop_;
    dst.mCrop_.iRight_ = require_dst.mCrop_.iRight_;
    dst.mCrop_.iBottom_= require_dst.mCrop_.iBottom_;
    printf("dst[w=%d,h=%d,f=%d][l,t,r,b]=[%d,%d,%d,%d]\n",dst.mBufferInfo_.iWidth_,dst.mBufferInfo_.iHeight_,dst.mBufferInfo_.iFormat_,
                                                            dst.mCrop_.iLeft_,dst.mCrop_.iTop_,dst.mCrop_.iRight_,dst.mCrop_.iBottom_);

    int memc_fence = -1;
    for(int i = 0; i < loop_cnt; i++){
        //8. 异步执行 Memc 处理
        ret = memc->RunAsync(&src0, &src1, &dst, &memc_fence);
        if(ret){
            printf("RunAsync fail!\n");
            return ret;
        }

        //9. 等待 Memc 算法完成, 可在另一个线程等待
        if(memc_fence > 0){
            int sync_ret = sync_wait(memc_fence, 1500);
            if (sync_ret) {
                printf("Failed to wait fence %d/%d 1500ms!\n", memc_fence, sync_ret);
            }else{
                printf("wait fence %d success!\n", memc_fence);
            }
            close(memc_fence);
        }

        //10. 检查输出图像是否正确
        dst_buffer->DumpData();
        printf("Memc dump data to /data/dump success!\n");
    }

    delete src_buffer0;
    delete src_buffer1;
    delete dst_buffer;
    printf("test_memc_native_mode end!\n");

    return 0;
}
