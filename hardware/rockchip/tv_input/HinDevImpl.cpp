/*
 * Copyright (c) 2021 Rockchip Electronics Co., Ltd
 */

#include <utils/Log.h>
#include <utils/String8.h>

#include <sched.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/select.h>
#include <linux/videodev2.h>
#include <linux/media.h>
#include <sys/time.h>
#include <utils/Timers.h>

#include <cutils/properties.h>
#include <sys/types.h>
#include <sys/stat.h>

#include "HinDev.h"
#include <ui/GraphicBufferMapper.h>
#include <ui/GraphicBuffer.h>
#include <linux/videodev2.h>
#include <RockchipRga.h>
#include "im2d.hpp"

#ifdef LOG_TAG
#undef LOG_TAG
#define LOG_TAG "tv_input_HinDevImpl"
#endif

#define V4L2_ROTATE_ID 0x980922

#ifndef container_of
#define container_of(ptr, type, member) ({                      \
        const typeof(((type *) 0)->member) *__mptr = (ptr);     \
        (type *) ((char *) __mptr - (char *)(&((type *)0)->member)); })
#endif

#define BOUNDRY 32
#define ALIGN_32(x) ((x + (BOUNDRY) - 1)& ~((BOUNDRY) - 1))
#define ALIGN(b,w) (((b)+((w)-1))/(w)*(w))

const int kMaxDevicePathLen = 256;
const char* kDevicePath = "/dev/";
constexpr char kPrefix[] = "video";
constexpr int kPrefixLen = sizeof(kPrefix) - 1;
constexpr char kCsiPrefix[] = "v4l-subdev";
constexpr int kCsiPrefixLen = sizeof(kCsiPrefix) - 1;
//constexpr int kDevicePrefixLen = sizeof(kDevicePath) + kPrefixLen + 1;
constexpr char kHdmiNodeName[] = "rk_hdmirx";
constexpr char kCsiPreSubDevModule[] = "HDMI-MIPI";
constexpr int kCsiPreSubDevModuleLen = sizeof(kCsiPreSubDevModule) - 1;
constexpr char kCsiPreBusInfo[] = "platform:rkcif-mipi-lvds";

nsecs_t now = 0;
nsecs_t mLastTime = 0;
nsecs_t diff = 0;


static v4l2_buf_type TVHAL_V4L2_BUF_TYPE = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
static size_t getBufSize(int format, int width, int height)
{
    size_t buf_size = 0;
    switch (format) {
        case V4L2_PIX_FMT_YVU420:
        case V4L2_PIX_FMT_NV21:
        case V4L2_PIX_FMT_NV12:
        {
            buf_size = width * height * 3 / 2;
            break;
        }
        case V4L2_PIX_FMT_YUYV:
        case V4L2_PIX_FMT_RGB565:
        case V4L2_PIX_FMT_RGB565X:
            buf_size = width * height * 2;
            break;
        case V4L2_PIX_FMT_BGR24:
            buf_size = width * height * 3;
            break;
        case V4L2_PIX_FMT_RGB32:
            buf_size = width * height * 4;
            break;
        default:
            DEBUG_PRINT(3, "Invalid format");
            buf_size = width * height * 3 / 2;
    }
    return buf_size;
}

static int  getNativeWindowFormat(int format)
{
    int nativeFormat = -1;//HAL_PIXEL_FORMAT_YCbCr_422_I;

    switch(format){
        case V4L2_PIX_FMT_YVU420:
            nativeFormat = HAL_PIXEL_FORMAT_YV12;
            break;
        case V4L2_PIX_FMT_NV21:
            nativeFormat = HAL_PIXEL_FORMAT_YCrCb_420_SP;
            break;
        case V4L2_PIX_FMT_YUYV:
            nativeFormat = HAL_PIXEL_FORMAT_YCbCr_422_I;
            break;
        case V4L2_PIX_FMT_RGB565:
            nativeFormat = HAL_PIXEL_FORMAT_RGB_565;
            break;
        case V4L2_PIX_FMT_BGR24:
            nativeFormat = HAL_PIXEL_FORMAT_BGR_888;
            break;
        case V4L2_PIX_FMT_RGB32:
            nativeFormat = HAL_PIXEL_FORMAT_RGBA_8888;
            break;
        case V4L2_PIX_FMT_ABGR32:
            nativeFormat = HAL_PIXEL_FORMAT_BGRA_8888;
            break;
	case V4L2_PIX_FMT_NV12:
	    nativeFormat = HAL_PIXEL_FORMAT_YCrCb_NV12;
	    break;
	case V4L2_PIX_FMT_NV16:
	    nativeFormat = HAL_PIXEL_FORMAT_YCbCr_422_SP;
	    break;
        case V4L2_PIX_FMT_NV24:
            nativeFormat = HAL_PIXEL_FORMAT_YCbCr_444_888;
            break;
        default:
            DEBUG_PRINT(3, "Invalid format %d, Use default format", format);
    }
    return nativeFormat;
}

HinDevImpl::HinDevImpl()
    : mHinDevHandle(-1),
                    mHinDevEventHandle(-1),
                    mHinNodeInfo(NULL),
                    mSidebandHandle(NULL),
                    mDumpFrameCount(30),
                    mFirstRequestCapture(true),
                    mPqMode(0),
                    mUseZme(false)
{
    char prop_value[PROPERTY_VALUE_MAX] = {0};
    property_get(TV_INPUT_DEBUG_LEVEL, prop_value, "0");
    mDebugLevel = (int)atoi(prop_value);

    property_get(TV_INPUT_DEBUG_DUMP, prop_value, "0");
    mEnableDump = (int)atoi(prop_value);

    property_get(TV_INPUT_HDMIIN_TYPE, prop_value, "0");
    mHdmiInType = (int)atoi(prop_value);

    ALOGE("prop value : mHdmiInType=%d, mDebugLevel=%d, mSkipFrame=%d",
        mHdmiInType, mDebugLevel, mSkipFrame);

    mV4l2Event = new V4L2DeviceEvent();
    mSidebandWindow = new RTSidebandWindow();
}

int HinDevImpl::init(int id,int initType, int& initWidth, int& initHeight,int& initFormat) {
    char prop_value[PROPERTY_VALUE_MAX] = {0};
    property_get(TV_INPUT_HDMIIN_TYPE, prop_value, "0");
    int currentHdmiInType = (int)atoi(prop_value);
    ALOGE("lastHdmiInType=%d, nowHdmiInType=%d", mHdmiInType, currentHdmiInType);
    if (mHdmiInType != currentHdmiInType) {
        mHdmiInType = currentHdmiInType;
        if (mV4l2Event) {
            mV4l2Event->closePipe();
            mV4l2Event->closeEventThread();
            //mV4l2Event = nullptr;
        }
        if (mHinDevHandle >= 0) {
            close(mHinDevHandle);
            mHinDevHandle = -1;
        }
        if (mHinDevEventHandle >= 0) {
            close(mHinDevEventHandle);
            mHinDevEventHandle = -1;
        }
        findDevice(0, initWidth, initHeight, initFormat);
    }
    ALOGE("%s mHdmiInType=%d, id=%d, initType=%d", __FUNCTION__, mHdmiInType, id, initType);
    if(get_HdmiIn(true) <= 0 || getNativeWindowFormat(mPixelFormat) == -1){
	DEBUG_PRINT(3, "[%s %d] hdmi isnt in", __FUNCTION__, __LINE__);
        return -1;
    }
    mHinNodeInfo = (struct HinNodeInfo *) calloc (1, sizeof (struct HinNodeInfo));
    if (mHinNodeInfo == NULL)
    {
        DEBUG_PRINT(3, "[%s %d] no memory for mHinNodeInfo", __FUNCTION__, __LINE__);
        if (mHinDevHandle > -1) {
            close(mHinDevHandle);
            mHinDevHandle = -1;
        }
        if (mHinDevEventHandle > -1) {
            close(mHinDevEventHandle);
            mHinDevEventHandle = -1;
        }
        return NO_MEMORY;
    }
    memset(mHinNodeInfo, 0, sizeof(struct HinNodeInfo));
    mHinNodeInfo->currBufferHandleIndex = 0;
    mHinNodeInfo->currBufferHandleFd = 0;

    mNotifyQueueCb = NULL;
    mState = STOPED;
    mANativeWindow = NULL;
    mQbufCount = 0;
    mIsLastPqShowFrameMode = false;
    if (mWorkThread != NULL) {
        DEBUG_PRINT(3, "[%s %d] mWorkThread not null, need thread exit", __FUNCTION__, __LINE__);
        mWorkThread->requestExit();
        mWorkThread.clear();
        mWorkThread = NULL;
    }
    mWorkThread = NULL;
    mPqBufferThread = NULL;
    mIepBufferThread = NULL;
    mV4L2DataFormatConvert = false;
    // mPreviewThreadRunning = false;
    // mPreviewBuffThread = NULL;
    mTvInputCB = NULL;
    mOpen = false;
    property_get(TV_INPUT_SKIP_FRAME, prop_value, "0");
    mSkipFrame = (int)atoi(prop_value);
    DEBUG_PRINT(3, "[%s %d] mSkipFrame=%d", __FUNCTION__, __LINE__, mSkipFrame);

    /**
     *  init RTSidebandWindow
     */
    vt_win_attr_t info;
    memset(&info, 0, sizeof(vt_win_attr_t));
    info.struct_size = sizeof(vt_win_attr_t);
    info.struct_ver = 0;
    info.top = 0;
    info.left = 0;
    info.width = mSrcFrameWidth;
    info.height = mSrcFrameHeight;
    info.usage = STREAM_BUFFER_GRALLOC_USAGE;
    if (initType == TV_STREAM_TYPE_INDEPENDENT_VIDEO_SOURCE) {
        property_get(SIDEBAND_MODE_TYPE, prop_value, "0");
        if (0 == strcmp(prop_value, "0")) {
            mFrameType |= TYPE_SIDEBAND_WINDOW;
        } else {
            mFrameType |= TYPE_SIDEBAND_VTUNNEL;
            if (property_get_int32(TV_INPUT_PQ_ENABLE, 0)) {
                info.data_space = HAL_DATASPACE_STANDARD_BT601_625
                    | HAL_DATASPACE_TRANSFER_SMPTE_170M | HAL_DATASPACE_RANGE_FULL;
            } else {
                info.data_space = 0x2;
                if (V4L2_PIX_FMT_BGR24 != mPixelFormat) {
                    get_extfmt_info();
                    if (mFrameColorRange == HDMIRX_LIMIT_RANGE) {
                        info.data_space = HAL_DATASPACE_RANGE_LIMITED;
                    } else if (mFrameColorRange == HDMIRX_FULL_RANGE) {
                        info.data_space = HAL_DATASPACE_RANGE_FULL;
                    } else {
                        info.data_space = HAL_DATASPACE_RANGE_FULL;
                    }
                    if (mFrameColorSpace == HDMIRX_XVYCC601 || mFrameColorSpace == HDMIRX_SYCC601) {
                        info.data_space |= HAL_DATASPACE_STANDARD_BT601_625 | HAL_DATASPACE_TRANSFER_SMPTE_170M;
                    } else {
                        info.data_space |= HAL_DATASPACE_STANDARD_BT709;
                    }
                }
            }
            info.compress_mode = 0;
            info.transform = 0;
            info.buffer_cnt = SIDEBAND_WINDOW_BUFF_CNT;
            info.remain_cnt = 0;
            info.usage |= MALI_GRALLOC_USAGE_NO_AFBC;
        }
        mBufferCount = SIDEBAND_WINDOW_BUFF_CNT;
        mPqIniting = false;
        mFirstRequestCapture = false;
        mRequestCaptureCount = 1;
    } else {
        mFrameType |= TYPE_STREAM_BUFFER_PRODUCER;
        mBufferCount = APP_PREVIEW_BUFF_CNT;
    }
    if (mHdmiInType == HDMIIN_TYPE_MIPICSI) {
        info.usage |= RK_GRALLOC_USAGE_ALLOC_HEIGHT_ALIGN_16;
        if (mFrameType & TYPE_SIDEBAND_WINDOW) {
            info.usage |= GRALLOC_USAGE_HW_COMPOSER
                | RK_GRALLOC_USAGE_STRIDE_ALIGN_64;
        }
    } else {
        info.usage |= GRALLOC_USAGE_HW_COMPOSER
            | RK_GRALLOC_USAGE_STRIDE_ALIGN_64;
    }
    info.format = mPixelFormat; //0x15

    if(-1 == mSidebandWindow->init(&info, mFrameType)) {
        DEBUG_PRINT(3, "mSidebandWindow->init failed !!!");
        return -1;
    }
    return NO_ERROR;
}

int HinDevImpl::findDevice(int id, int& initWidth, int& initHeight,int& initFormat ) {
    ALOGD("%s called", __func__);
    // Find existing /dev/video* devices
    DIR* devdir = opendir(kDevicePath);
    int videofd,ret;
    string strCsiNum = "";
    if(devdir == 0) {
        ALOGE("%s: cannot open %s! Exiting threadloop", __FUNCTION__, kDevicePath);
        return -1;
    }
    struct dirent* de;
    while ((de = readdir(devdir)) != 0) {
        // Find external v4l devices that's existing before we start watching and add them
        if (mHdmiInType == HDMIIN_TYPE_HDMIRX && !strncmp(kPrefix, de->d_name, kPrefixLen)) {
		std::string deviceId(de->d_name + kPrefixLen);
		ALOGD(" v4l device %s found", de->d_name);
		char v4l2DevicePath[kMaxDevicePathLen];
		char v4l2DeviceDriver[16];
		char gadget_video[100] = {0};

		sprintf(gadget_video, "/sys/class/video4linux/%s/function_name", de->d_name);
		if (access(gadget_video, F_OK) == 0) {
			ALOGW("/dev/%s is uvc gadget device, don't open it!", de->d_name);
			continue;
		}

		snprintf(v4l2DevicePath, kMaxDevicePathLen,"%s%s", kDevicePath, de->d_name);
		videofd = open(v4l2DevicePath, O_RDWR);
		if (videofd < 0){
			DEBUG_PRINT(3, "[%s %d] mHinDevHandle:%x [%s]", __FUNCTION__, __LINE__, videofd,strerror(errno));
			continue;
		} else {
			DEBUG_PRINT(1, "%s open device %s successful.", __FUNCTION__, v4l2DevicePath);
			struct v4l2_capability cap;
			ret = ioctl(videofd, VIDIOC_QUERYCAP, &cap);
			if (ret < 0) {
				DEBUG_PRINT(3, "VIDIOC_QUERYCAP Failed, error: %s", strerror(errno));
				close(videofd);
				continue;
		}
		snprintf(v4l2DeviceDriver, 16,"%s",cap.driver);
		DEBUG_PRINT(3, "VIDIOC_QUERYCAP driver=%s,%s", cap.driver,v4l2DeviceDriver);
		DEBUG_PRINT(3, "VIDIOC_QUERYCAP card=%s", cap.card);
		DEBUG_PRINT(3, "VIDIOC_QUERYCAP version=%d", cap.version);
		DEBUG_PRINT(3, "VIDIOC_QUERYCAP capabilities=0x%08x,0x%08x", cap.capabilities,V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE);
		DEBUG_PRINT(3, "VIDIOC_QUERYCAP device_caps=0x%08x", cap.device_caps);
		if(!strncmp(kHdmiNodeName, v4l2DeviceDriver, sizeof(kHdmiNodeName)-1)){
			mHinDevHandle =  videofd;
                mHinDevEventHandle = mHinDevHandle;
			if ((cap.capabilities & V4L2_CAP_VIDEO_CAPTURE)) {
				ALOGE("V4L2_CAP_VIDEO_CAPTURE is  a video capture device, capabilities: %x\n", cap.capabilities);
					TVHAL_V4L2_BUF_TYPE = V4L2_BUF_TYPE_VIDEO_CAPTURE;
		}else if ((cap.capabilities & V4L2_CAP_VIDEO_CAPTURE_MPLANE)) {
				ALOGE("V4L2_CAP_VIDEO_CAPTURE_MPLANE is  a video capture device, capabilities: %x\n", cap.capabilities);
				TVHAL_V4L2_BUF_TYPE = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
			}
			break;
		}else{
			close(videofd);
			DEBUG_PRINT(3, "isnot hdmirx,VIDIOC_QUERYCAP driver=%s", cap.driver);
		}
            }
        } else if (mHdmiInType == HDMIIN_TYPE_MIPICSI && !strncmp(kCsiPrefix, de->d_name, kCsiPrefixLen)) {
            ALOGD(" v4l device %s found", de->d_name);
            char v4l2SubDevPath[kMaxDevicePathLen];
            snprintf(v4l2SubDevPath, kMaxDevicePathLen,"%s%s", kDevicePath, de->d_name);
            videofd = open(v4l2SubDevPath, O_RDWR);
            if (videofd < 0) {
                ALOGE("[%s %d] mHinDevEventHandle:%x [%s]", __FUNCTION__, __LINE__, videofd,strerror(errno));
                continue;
            } else {
                DEBUG_PRINT(1, "%s open device %s successful.", __FUNCTION__, v4l2SubDevPath);
                uint32_t ishdmi = 0;
                ret = ioctl(videofd, RKMODULE_GET_HDMI_MODE, (void*)&ishdmi);
                if (ret < 0 || !ishdmi) {
                    ALOGE("RKMODULE_GET_HDMI_MODE %s Failed, error: %s, ret=%d, ishdmi=%d", v4l2SubDevPath, strerror(errno), ret, ishdmi);
                    close(videofd);
                    continue;
                }
                struct rkmodule_inf minfo;
                memset(&minfo, 0, sizeof(struct rkmodule_inf));
                ret = ioctl(videofd, RKMODULE_GET_MODULE_INFO, &minfo);
                if (ret < 0) {
                    close(videofd);
                    continue;
                }
                ALOGE("sensor name: %s, module name: %s", minfo.base.sensor, minfo.base.module);
                if (strstr(minfo.base.module, kCsiPreSubDevModule)) {
                    string temp(1,minfo.base.module[kCsiPreSubDevModuleLen]);
                    if (strcmp(temp.c_str(), "0") != 0) {
                        strCsiNum = minfo.base.module[kCsiPreSubDevModuleLen];
                    }
                    ALOGE("csiNum=%s", strCsiNum.c_str());
                } else {
                    close(videofd);
                    continue;
                }
                mHinDevEventHandle = videofd;
                break;
            }
        }
    }
    if (mHinDevEventHandle > 0 && mHinDevHandle < 0) {
        rewinddir(devdir);
        int minVideoPathIndex = 999999;
        int tempVideoFd = -1;
        while ((de = readdir(devdir)) != 0) {
            if (!strncmp(kPrefix, de->d_name, kPrefixLen)) {
                char gadget_video[100] = {0};
                sprintf(gadget_video, "/sys/class/video4linux/%s/function_name", de->d_name);
                if (access(gadget_video, F_OK) == 0) {
                    ALOGW("/dev/%s is uvc gadget device, don't open it!", de->d_name);
                    continue;
                }
                char videoPath[kMaxDevicePathLen];
                snprintf(videoPath, kMaxDevicePathLen,"%s%s", kDevicePath, de->d_name);
                videofd = open(videoPath, O_RDWR);
                if (videofd < 0){
                    ALOGE("[%s %d] %s %x [%s]", __FUNCTION__, __LINE__, videoPath, videofd,strerror(errno));
                    continue;
                } else {
                    struct v4l2_capability cap;
                    memset(&cap, 0, sizeof(struct v4l2_capability));
                    ret = ioctl(videofd, VIDIOC_QUERYCAP, &cap);
                    if (ret < 0) {
                        ALOGE("VIDIOC_QUERYCAP %s Failed, error: %s", videoPath, strerror(errno));
                        close(videofd);
                        continue;
                    } else {
                        ALOGE("VIDIOC_QUERYCAP %s cap.bus_info=%s", videoPath, cap.bus_info);
                    }
                    char standard_bus_info[kMaxDevicePathLen];
                    snprintf(standard_bus_info, kMaxDevicePathLen,"%s%s", kCsiPreBusInfo, strCsiNum.c_str());
                    char cur_bus_info[kMaxDevicePathLen];
                    snprintf(cur_bus_info, 32,"%s",cap.bus_info);
                    if (strcmp(standard_bus_info, cur_bus_info) == 0) {
                        int tempVideoPathIndex = 0;
                        if (1 == sscanf(videoPath, "/dev/video%d", &tempVideoPathIndex)
                                && minVideoPathIndex > tempVideoPathIndex) {
                            minVideoPathIndex = tempVideoPathIndex;
                            if (tempVideoFd > -1) {
                                close(tempVideoFd);
                            }
                            tempVideoFd = videofd;
                        } else {
                            close(videofd);
                        }
                    } else {
                        close(videofd);
                    }
                }
            }
        }
        if (tempVideoFd > -1) {
            mHinDevHandle = tempVideoFd;
            ALOGE("min /dev/video%d", minVideoPathIndex);
        }
    }
    closedir(devdir);
    if (mHinDevHandle < 0) {
        DEBUG_PRINT(3, "[%s %d] mHinDevHandle:%x mHinDevEventHandle:%x", __FUNCTION__, __LINE__, mHinDevHandle, mHinDevEventHandle);
        return -1;
    }
    mV4l2Event->initialize(mHinDevEventHandle);
    if (mHinDevHandle == mHinDevEventHandle) {
        if (get_format(0, initWidth, initHeight, initFormat) == 0) {
            DEBUG_PRINT(3, "[%s %d] get_format fail ", __FUNCTION__, __LINE__);
            if (mHinDevHandle > -1) {
                close(mHinDevHandle);
                mHinDevHandle = -1;
            }
            if (mHinDevEventHandle > -1) {
                close(mHinDevEventHandle);
                mHinDevEventHandle = -1;
            }
            return -1;
        }
    } else {
        if (get_csi_format(mHinDevEventHandle, initWidth, initHeight, initFormat) == 0) {
            DEBUG_PRINT(3, "[%s %d] get_format fail ", __FUNCTION__, __LINE__);
            if (mHinDevHandle > -1) {
                close(mHinDevHandle);
                mHinDevHandle = -1;
            }
            if (mHinDevEventHandle > -1) {
                close(mHinDevEventHandle);
                mHinDevEventHandle = -1;
            }
            return -1;
        }
    }

   // mPixelFormat = DEFAULT_TVHAL_STREAM_FORMAT;

    mSrcFrameWidth = initWidth;
    mSrcFrameHeight = initHeight;
    /*int dst_width = 0, dst_height = 0;
    bool use_zme = check_zme(mSrcFrameWidth, mSrcFrameHeight, &dst_width, &dst_height);
    if(use_zme) {
        mDstFrameWidth = dst_width;
        mDstFrameHeight = dst_height;
    } else {
        mDstFrameWidth = mSrcFrameWidth;
        mDstFrameHeight = mSrcFrameHeight;
    }*/
    mDstFrameWidth = mSrcFrameWidth;
    mDstFrameHeight = mSrcFrameHeight;
    mBufferSize = mSrcFrameWidth * mSrcFrameHeight * 3/2;
    return 0;
}
int HinDevImpl::makeHwcSidebandHandle() {
    ALOGW("%s %d", __FUNCTION__, __LINE__);
    buffer_handle_t buffer = NULL;
    if (mFrameType & TYPE_SIDEBAND_WINDOW) {
        mSidebandWindow->allocateSidebandHandle(&buffer, mDstFrameWidth, mDstFrameHeight, -1, RK_GRALLOC_USAGE_STRIDE_ALIGN_64);
    } else {
        mSidebandWindow->allocateSidebandHandle(&buffer, -1);
        mSidebandWindow->allocateSidebandHandle(&mSidebandCancelHandle, 0);
    }
    if (!buffer) {
        DEBUG_PRINT(3, "allocate buffer from sideband window failed!");
        return -1;
    }
    mSidebandHandle = buffer;
    return 0;
}

buffer_handle_t HinDevImpl::getSindebandBufferHandle() {
    if (mSidebandHandle == NULL)
        makeHwcSidebandHandle();
    return mSidebandHandle;
}

buffer_handle_t HinDevImpl::getSindebandCancelBufferHandle() {
    return mSidebandCancelHandle;
}

HinDevImpl::~HinDevImpl()
{
    DEBUG_PRINT(3, "%s %d", __FUNCTION__, __LINE__);
    if (mSidebandWindow) {
        mSidebandWindow->stop();
    }
    if (mV4l2Event) {
        DEBUG_PRINT(3, "%s %d enter mV4l2Event release", __FUNCTION__, __LINE__);
        mV4l2Event->closePipe();
        mV4l2Event->closeEventThread();
        mV4l2Event = nullptr;
    }
    if (mHinNodeInfo) {
        free (mHinNodeInfo);
        //mHinNodeInfo = nullptr;
    }
    if (mHinDevHandle > -1) {
        close(mHinDevHandle);
        mHinDevHandle = -1;
    }
    if (mHinDevEventHandle > -1) {
        close(mHinDevEventHandle);
        mHinDevEventHandle = -1;
    }
}

int HinDevImpl::start_device()
{
    if (mFrameType & TYPE_SIDEBAND_WINDOW || mFrameType & TYPE_SIDEBAND_VTUNNEL) {
        //mRequestCaptureCount = 1;
    } else {
        mRequestCaptureCount = 0;
        mFirstRequestCapture = true;
    }
    DEBUG_PRINT(1, "[%s %d] mHinDevHandle:%x", __FUNCTION__, __LINE__, mHinDevHandle);

    get_extfmt_info();
    int ret = ioctl(mHinDevHandle, VIDIOC_QUERYCAP, &mHinNodeInfo->cap);
    if (ret < 0) {
        DEBUG_PRINT(3, "VIDIOC_QUERYCAP Failed, error: %s", strerror(errno));
        return ret;
    }
    DEBUG_PRINT(1, "VIDIOC_QUERYCAP driver=%s", mHinNodeInfo->cap.driver);
    DEBUG_PRINT(1, "VIDIOC_QUERYCAP card=%s", mHinNodeInfo->cap.card);
    DEBUG_PRINT(1, "VIDIOC_QUERYCAP version=%d", mHinNodeInfo->cap.version);
    DEBUG_PRINT(1, "VIDIOC_QUERYCAP capabilities=0x%08x,0x%08x", mHinNodeInfo->cap.capabilities,V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE);
    DEBUG_PRINT(1, "VIDIOC_QUERYCAP device_caps=0x%08x", mHinNodeInfo->cap.device_caps);

    mHinNodeInfo->reqBuf.type = TVHAL_V4L2_BUF_TYPE;
    mHinNodeInfo->reqBuf.memory = TVHAL_V4L2_BUF_MEMORY_TYPE;
    mHinNodeInfo->reqBuf.count = mBufferCount;

    ret = ioctl(mHinDevHandle, VIDIOC_REQBUFS, &mHinNodeInfo->reqBuf);
    if (ret < 0) {
        DEBUG_PRINT(3, "VIDIOC_REQBUFS Failed, error: %s", strerror(errno));
        return ret;
    } else {
        ALOGD("VIDIOC_REQBUFS successful.");
    }

    aquire_buffer();
    if (mFrameType & TYPE_SIDEBAND_VTUNNEL) {
        memset(&mCurrentPlanes, 0, sizeof(struct v4l2_plane));
        memset(&mCurrentBufferArray, 0, sizeof(struct v4l2_buffer));
        mCurrentBufferArray.index = 0;
        mCurrentBufferArray.type = TVHAL_V4L2_BUF_TYPE;
        mCurrentBufferArray.memory = TVHAL_V4L2_BUF_MEMORY_TYPE;
        mCurrentBufferArray.m.planes = &mCurrentPlanes;
        mCurrentBufferArray.length = PLANES_NUM;
        ret = ioctl(mHinDevHandle, VIDIOC_QUERYBUF, &mCurrentBufferArray);
        if (ret < 0) {
            DEBUG_PRINT(3, "VIDIOC_QUERYBUF Failed, error: %s", strerror(errno));
            return ret;
        }
        for (int i = 0; i < PLANES_NUM; i++) {
            mCurrentBufferArray.m.planes[i].m.fd = mSidebandWindow->getBufferHandleFd(mHinNodeInfo->vt_buffers[0]->handle);
            mCurrentBufferArray.m.planes[i].length = 0;
        }
    }
    for (int i = 0; i < mBufferCount; i++) {
        DEBUG_PRINT(mDebugLevel, "bufferArray index = %d", mHinNodeInfo->bufferArray[i].index);
        DEBUG_PRINT(mDebugLevel, "bufferArray type = %d", mHinNodeInfo->bufferArray[i].type);
        DEBUG_PRINT(mDebugLevel, "bufferArray memory = %d", mHinNodeInfo->bufferArray[i].memory);
        DEBUG_PRINT(mDebugLevel, "bufferArray m.fd = %d", mHinNodeInfo->bufferArray[i].m.planes[0].m.fd);
        DEBUG_PRINT(mDebugLevel, "bufferArray length = %d", mHinNodeInfo->bufferArray[i].length);
        DEBUG_PRINT(mDebugLevel, "buffer length = %d", mSidebandWindow->getBufferLength(mHinNodeInfo->buffer_handle_poll[i]));

 	//mHinNodeInfo->bufferArray[i].flags = V4L2_BUF_FLAG_NO_CACHE_INVALIDATE |
        //                 V4L2_BUF_FLAG_NO_CACHE_CLEAN;
        ret = ioctl(mHinDevHandle, VIDIOC_QBUF, &mHinNodeInfo->bufferArray[i]);
        if (ret < 0) {
            DEBUG_PRINT(3, "VIDIOC_QBUF Failed, error: %s", strerror(errno));
            return -1;
        }
    }
    ALOGD("[%s %d] VIDIOC_QBUF successful", __FUNCTION__, __LINE__);

    v4l2_buf_type bufType;
    bufType = TVHAL_V4L2_BUF_TYPE;
    ret = ioctl(mHinDevHandle, VIDIOC_STREAMON, &bufType);
    if (ret < 0) {
        DEBUG_PRINT(3, "VIDIOC_STREAMON Failed, error: %s", strerror(errno));
        return -1;
    }
    mUseIep = check_interlaced() > 0;
    ALOGD("[%s %d] VIDIOC_STREAMON return=:%d", __FUNCTION__, __LINE__, ret);
    return ret;
}

int HinDevImpl::stop_device()
{
    DEBUG_PRINT(3, "%s %d", __FUNCTION__, __LINE__);
    int ret;
    enum v4l2_buf_type bufType = TVHAL_V4L2_BUF_TYPE;

    ret = ioctl (mHinDevHandle, VIDIOC_STREAMOFF, &bufType);
    if (ret < 0) {
        DEBUG_PRINT(3, "StopStreaming: Unable to stop capture: %s", strerror(errno));
    }
    return ret;
}

int HinDevImpl::start()
{
    ALOGD("%s %d", __FUNCTION__, __LINE__);
    int ret;
    if(mOpen == true){
        ALOGI("already open");
        return NO_ERROR;
    }

    ret = start_device();
    if(ret != NO_ERROR) {
        DEBUG_PRINT(3, "Start v4l2 device failed:%d",ret);
        return ret;
    }

    if (mFrameType & TYPE_SIDEBAND_WINDOW) {
        mSidebandWindow->allocateSidebandHandle(&mSignalHandle, -1, -1, HAL_PIXEL_FORMAT_BGR_888, RK_GRALLOC_USAGE_STRIDE_ALIGN_64);
    } else if (mFrameType & TYPE_SIDEBAND_VTUNNEL) {
        mSidebandWindow->allocateBuffer(&mSignalVTBuffer, mDstFrameWidth, mDstFrameHeight,
            HAL_PIXEL_FORMAT_BGR_888, RK_GRALLOC_USAGE_STRIDE_ALIGN_64 | MALI_GRALLOC_USAGE_NO_AFBC);
    } else if (mFrameType & TYPE_STREAM_BUFFER_PRODUCER) {
        mSidebandWindow->allocateSidebandHandle(&mSignalHandle, mSrcFrameWidth, mSrcFrameHeight,
            HAL_PIXEL_FORMAT_BGR_888, RK_GRALLOC_USAGE_STRIDE_ALIGN_64);
        if (mSignalHandle && mSignalPreviewHandle) {
            ALOGW("Start trans preview signal %d to %d", mSignalHandle->data[0], mSignalPreviewHandle->data[0]);
            buffDataTransfer(mSignalHandle, V4L2_PIX_FMT_BGR24,
                mSrcFrameWidth, mSrcFrameHeight,
                mSignalPreviewHandle, V4L2_PIX_FMT_NV12,
                mSrcFrameWidth, mSrcFrameHeight, _ALIGN(mSrcFrameWidth, 64), mSrcFrameHeight);
        }
        mDstFrameWidth = mSrcFrameWidth;
        mDstFrameHeight = mSrcFrameHeight;
        initPqInfo(PQ_NORMAL, 0);
    }

    ALOGD("Create Work Thread");

    //std::string g_stream_dev_name = "/dev/video17";
    //if (g_stream_dev_name.length() > 0 && mHasEncode) {
    /*if (g_stream_dev_name.length() > 0) {
        init_encodeserver(g_stream_dev_name.c_str(), mSrcFrameWidth, mSrcFrameHeight);
    }*/

    char prop_value[PROPERTY_VALUE_MAX] = {0};
    property_get(TV_INPUT_DISPLAY_RATIO, prop_value, "0");
    mDisplayRatio = (int)atoi(prop_value);
    property_set(TV_INPUT_PQ_MODE, "0");
    property_set(TV_INPUT_HDMIIN, "1");

    mWorkThread = new WorkThread(this);
    mState = START;
    mPqBufferThread = new PqBufferThread(this);
    mIepBufferThread = new IepBufferThread(this);
    /*property_get(TV_INPUT_PQ_STATUS, prop_value, "0");
    int pqStatus = (int)atoi(prop_value);
    mPqInitFinish = false;
    if ((pqStatus & PQ_NORMAL) == PQ_NORMAL) {
        map<string, string> pqData;
        pqData.clear();
        pqData.insert({"mode", prop_value});
        doPQCmd(pqData);
    }*/

    mOpen = true;
    ALOGD("%s %d ret:%d", __FUNCTION__, __LINE__, ret);
    return NO_ERROR;
}


int HinDevImpl::stop()
{
    ALOGD("%s %d", __FUNCTION__, __LINE__);
    int ret;
    mPqMode = PQ_OFF;
    mState = STOPED;
    char prop_value[PROPERTY_VALUE_MAX] = {0};
    property_get(TV_INPUT_PQ_ENABLE, prop_value, "0");
    if ((int)atoi(prop_value) == 1 && !mPqIniting) {
        property_set(TV_INPUT_PQ_MODE, "1");
    }
    property_set(TV_INPUT_HDMIIN, "0");
    Mutex::Autolock autoLock(mBufferLock);
    ALOGD("%s %d enter mBufferLock", __FUNCTION__, __LINE__);

    if(gMppEnCodeServer != nullptr) {
        ALOGD("zj add file: %s func %s line %d \n",__FILE__,__FUNCTION__,__LINE__);
        gMppEnCodeServer->stop();
    }
    if(mWorkThread != NULL){
        mWorkThread->requestExit();
        mWorkThread.clear();
        mWorkThread = NULL;
    }
    if(mPqBufferThread != NULL){
        mPqBufferThread->requestExit();
        mPqBufferThread.clear();
        mPqBufferThread = NULL;
    }

    if (mRkpq!=nullptr) {
        delete mRkpq;
        mRkpq = nullptr;
    }

    if (mIepBufferThread != NULL) {
        mIepBufferThread->requestExit();
         mIepBufferThread.clear();
         mIepBufferThread = NULL;
    }
    if (mRkiep!=nullptr) {
         delete mRkiep;
         mRkiep = nullptr;
    }

    if (!mPqPrepareList.empty()) {
        DEBUG_PRINT(3, "clear mPqPrepareList");
        mPqPrepareList.clear();
    }

    if (!mPqDoneList.empty()) {
        DEBUG_PRINT(3, "clear mPqDoneList");
        mPqDoneList.clear();
    }

    if (!mIepPrepareList.empty()) {
        DEBUG_PRINT(3, "clear mIepPrepareList");
        mIepPrepareList.clear();
    }

    if (!mIepDoneList.empty()) {
        DEBUG_PRINT(3, "clear mIepDoneList");
        mIepDoneList.clear();
    }

    if (mFrameType & TYPE_SIDEBAND_WINDOW) {
        mSidebandWindow->clearVopArea();
    }
    enum v4l2_buf_type bufType = TVHAL_V4L2_BUF_TYPE;
    ret = ioctl (mHinDevHandle, VIDIOC_STREAMOFF, &bufType);
    if (ret < 0) {
        DEBUG_PRINT(3, "StopStreaming: Unable to stop capture: %s", strerror(errno));
    } else {
        DEBUG_PRINT(3, "StopStreaming: successful.");
    }

    // cancel request buff
    v4l2_requestbuffers req_buffers{};
    req_buffers.type = TVHAL_V4L2_BUF_TYPE;
    req_buffers.memory = TVHAL_V4L2_BUF_MEMORY_TYPE;
    req_buffers.count = 0;
    ret = ioctl(mHinDevHandle, VIDIOC_REQBUFS, &req_buffers);
    if (ret < 0) {
        ALOGE("%s: cancel REQBUFS failed: %s", __FUNCTION__, strerror(errno));
    } else {
        ALOGE("%s: cancel REQBUFS successful.", __FUNCTION__);
    }

    if (mSidebandWindow) {
        mSidebandWindow->stop();
    }
    release_buffer();
    mDumpFrameCount = 3;

    mOpen = false;
    mFrameType = 0;

    if (mHinNodeInfo) {
        free(mHinNodeInfo);
        //mHinNodeInfo = nullptr;
    }

    if (mV4l2Event) {
        mV4l2Event->closePipe();
        mV4l2Event = nullptr;
    }

    if (mHinDevHandle > -1) {
        close(mHinDevHandle);
        mHinDevHandle = -1;
    }
    if (mHinDevEventHandle > -1) {
        close(mHinDevEventHandle);
        mHinDevEventHandle = -1;
    }

    mFirstRequestCapture = true;
    mRequestCaptureCount = 0;

    deinit_encodeserver();

    DEBUG_PRINT(3, "============================= %s end ================================", __FUNCTION__);
    return ret;
}

int HinDevImpl::set_preview_callback(NotifyQueueDataCallback callback)
{
    if (!callback) {
        DEBUG_PRINT(3, "NULL state callback pointer");
        return BAD_VALUE;
    }
    mNotifyQueueCb = callback;
    return NO_ERROR;
}

int HinDevImpl::set_data_callback(V4L2EventCallBack callback)
{
    ALOGD("%s %d", __FUNCTION__, __LINE__);
    if (callback == NULL){
        DEBUG_PRINT(3, "NULL data callback pointer");
        return BAD_VALUE;
    }
    mV4l2Event->RegisterEventvCallBack(callback);
    return NO_ERROR;
}

int HinDevImpl::set_command_callback(NotifyCommandCallback callback)
{
    if (!callback) {
        DEBUG_PRINT(3, "NULL state callback pointer");
        return BAD_VALUE;
    }
    mNotifyCommandCb = callback;
    return NO_ERROR;
}

int HinDevImpl::get_csi_format(int fd, int &hdmi_in_width, int &hdmi_in_height,int& initFormat)
{
    struct v4l2_subdev_format format;
    CLEAR(format);
    format.pad = 0;
    format.which = V4L2_SUBDEV_FORMAT_ACTIVE;
    int err = ioctl(fd, VIDIOC_SUBDEV_G_FMT, &format);
    if (err < 0) {
        ALOGE("[%s %d] failed, VIDIOC_SUBDEV_G_FMT %d, %s", __FUNCTION__, __LINE__, err, strerror(err));
    } else {
        hdmi_in_width = format.format.width;
        hdmi_in_height = format.format.height;
        if (format.format.code == MEDIA_BUS_FMT_UYVY8_2X8) {
            mPixelFormat = V4L2_PIX_FMT_NV16;
        } else if (format.format.code == MEDIA_BUS_FMT_BGR888_1X24) {
            mPixelFormat = V4L2_PIX_FMT_BGR24;
        } else {
            mPixelFormat = format.format.code;
        }
        ALOGE("VIDIOC_SUBDEV_G_FMT: pad: %d, which: %d, %dX%d, format: 0x%x, field: %d, color space: %d",
            format.pad, format.which, hdmi_in_width, hdmi_in_height, mPixelFormat,
            format.format.field, format.format.colorspace);
    }
    if(hdmi_in_width == 0 || hdmi_in_height == 0) return 0;
    return -1;
}

int HinDevImpl::get_format(int fd, int &hdmi_in_width, int &hdmi_in_height,int& initFormat)
{
    std::vector<int> formatList;
    struct v4l2_fmtdesc fmtdesc;
    fmtdesc.index = 0;
    fmtdesc.type = TVHAL_V4L2_BUF_TYPE;

    while (ioctl(mHinDevHandle, VIDIOC_ENUM_FMT, &fmtdesc) != -1)
    {
        formatList.push_back( fmtdesc.pixelformat);
        DEBUG_PRINT(3, "   V4L2 driver: idx=%d, \t desc:%s,format:0x%x", fmtdesc.index + 1, fmtdesc.description, fmtdesc.pixelformat);
        fmtdesc.index++;
    }
    v4l2_format format;
    format.type = TVHAL_V4L2_BUF_TYPE;
    vector<int>::iterator it;
    for(it = formatList.begin();it != formatList.end();it++){
    	format.fmt.pix.pixelformat = (int)*it;
    	if (ioctl(mHinDevHandle, VIDIOC_TRY_FMT, &format) != -1)
    	{
    		DEBUG_PRINT(3, "V4L2 driver try: width:%d,height:%d,format:0x%x", format.fmt.pix.width, format.fmt.pix.height,format.fmt.pix.pixelformat);
    		hdmi_in_width =  format.fmt.pix.width;
    		hdmi_in_height = format.fmt.pix.height;
    		mPixelFormat = format.fmt.pix.pixelformat;
    		initFormat = getNativeWindowFormat(format.fmt.pix.pixelformat);//V4L2_PIX_FMT_BGR24;
    		break;
    	}
    }
    int err = ioctl(mHinDevHandle, VIDIOC_G_FMT, &format);
    if (err < 0)
    {
        DEBUG_PRINT(3, "[%s %d] failed, VIDIOC_G_FMT %d, %s", __FUNCTION__, __LINE__, err, strerror(err));
    }
    else
    {
        DEBUG_PRINT(3, "after %s get from v4l2 format.type = %d ", __FUNCTION__, format.type);
        DEBUG_PRINT(3, "after %s get from v4l2 format.fmt.pix.width =%d", __FUNCTION__, format.fmt.pix.width);
        DEBUG_PRINT(3, "after %s get from v4l2 format.fmt.pix.height =%d", __FUNCTION__, format.fmt.pix.height);
        DEBUG_PRINT(3, "after %s get from v4l2 format.fmt.pix.pixelformat =%d", __FUNCTION__, format.fmt.pix.pixelformat);
    }

    if(hdmi_in_width == 0 || hdmi_in_height == 0) return 0;
    return -1;
}

int HinDevImpl::get_extfmt_info() {
    int err = ioctl(mHinDevHandle, RK_HDMIRX_CMD_GET_FPS, &mFrameFps);
    if (err < 0) {
        DEBUG_PRINT(3, "[%s %d] failed, RK_HDMIRX_CMD_GET_FPS %d, %s", __FUNCTION__, __LINE__, err, strerror(err));
        mFrameFps = 60;
    } else {
        DEBUG_PRINT(3, "[%s %d] RK_HDMIRX_CMD_GET_FPS %d", __FUNCTION__, __LINE__, mFrameFps);
    }

    err = ioctl(mHinDevHandle, RK_HDMIRX_CMD_GET_COLOR_RANGE, &mFrameColorRange);
    if (err < 0) {
        DEBUG_PRINT(3, "[%s %d] failed, RK_HDMIRX_CMD_GET_COLOR_RANGE %d, %s", __FUNCTION__, __LINE__, err, strerror(err));
        mFrameColorRange = HDMIRX_DEFAULT_RANGE;
    } else {
        DEBUG_PRINT(3, "[%s %d] RK_HDMIRX_CMD_GET_COLOR_RANGE %d", __FUNCTION__, __LINE__, mFrameColorRange);
    }

    err = ioctl(mHinDevHandle, RK_HDMIRX_CMD_GET_COLOR_SPACE, &mFrameColorSpace);
    if (err < 0) {
        DEBUG_PRINT(3, "[%s %d] failed, RK_HDMIRX_CMD_GET_COLOR_SPACE %d, %s", __FUNCTION__, __LINE__, err, strerror(err));
        mFrameColorSpace = HDMIRX_XVYCC709;
    } else {
        DEBUG_PRINT(3, "[%s %d] RK_HDMIRX_CMD_GET_COLOR_SPACE %d", __FUNCTION__, __LINE__, mFrameColorSpace);
    }
    return err;
}

int HinDevImpl::get_HdmiIn(bool enforce){
    if(enforce && mIsHdmiIn) return mIsHdmiIn;
    struct v4l2_control control;
    memset(&control, 0, sizeof(struct v4l2_control));
    control.id = V4L2_CID_DV_RX_POWER_PRESENT;
    int err = ioctl(mHinDevEventHandle, VIDIOC_G_CTRL, &control);
    if (err < 0) {
        ALOGE("Set POWER_PRESENT failed ,%d(%s)", errno, strerror(errno));
        return UNKNOWN_ERROR;
    }
    mIsHdmiIn = control.value;
    //enum v4l2_buf_type bufType = TVHAL_V4L2_BUF_TYPE;

    if(mIsHdmiIn && mState == START){
       /*err = ioctl(mHinDevHandle, VIDIOC_STREAMON, &bufType);
       if (err < 0) {
          DEBUG_PRINT(3, "VIDIOC_STREAMON Failed, error: %s", strerror(errno));
       }
       for (int i = 0; i < mBufferCount; i++) {
          err = ioctl(mHinDevHandle, VIDIOC_QBUF, &mHinNodeInfo->bufferArray[i]);
          if (err < 0) {
            DEBUG_PRINT(3, "VIDIOC_QBUF Failed, error: %s", strerror(errno));
          }
       }
       ALOGD("[%s %d] VIDIOC_STREAMON return=:%d", __FUNCTION__, __LINE__, err);*/
       //mState = START;
    }else{
       /*err = ioctl (mHinDevHandle, VIDIOC_STREAMOFF, &bufType);
       if (err < 0) {
          DEBUG_PRINT(3, "StopStreaming: Unable to stop capture: %s", strerror(errno));
       }*/
       mState = STOPED;
    }
    DEBUG_PRINT(3, "getHdmiIn : %d.", mIsHdmiIn);
    return mIsHdmiIn;
}
int HinDevImpl::set_mode(int displayMode)
{
    DEBUG_PRINT(3, "run into set_mode,displaymode = %d\n", displayMode);
    mHinNodeInfo->displaymode = displayMode;
    m_displaymode = displayMode;
    return 0;
}

int HinDevImpl::set_format(int width, int height, int color_format)
{
    ALOGD("[%s %d] width=%d, height=%d, color_format=%d, mPixelFormat=%d", __FUNCTION__, __LINE__, width, height, color_format, mPixelFormat);
    Mutex::Autolock autoLock(mLock);
    if (mOpen == true)
        return NO_ERROR;
    int ret;

    mSrcFrameWidth = width;
    mSrcFrameHeight = height;
    mUseZme = check_zme(mSrcFrameWidth, mSrcFrameHeight, &mDstFrameWidth, &mDstFrameHeight);
    //mPixelFormat = color_format;
    mHinNodeInfo->width = width;
    mHinNodeInfo->height = height;
    mHinNodeInfo->formatIn = mPixelFormat;
    mHinNodeInfo->format.type = TVHAL_V4L2_BUF_TYPE;
    mHinNodeInfo->format.fmt.pix.width = width;
    mHinNodeInfo->format.fmt.pix.height = height;
    mHinNodeInfo->format.fmt.pix.pixelformat = mPixelFormat;

    ret = ioctl(mHinDevHandle, VIDIOC_S_FMT, &mHinNodeInfo->format);
    if (ret < 0) {
        DEBUG_PRINT(3, "[%s %d] failed, set VIDIOC_S_FMT %d, %s", __FUNCTION__, __LINE__, ret, strerror(ret));
        return ret;
    } else {
        ALOGD("%s VIDIOC_S_FMT success. ", __FUNCTION__);
    }
    int format = getNativeWindowFormat(mPixelFormat);
    mSidebandWindow->setBufferGeometry(mSrcFrameWidth, mSrcFrameHeight, format);
    return ret;
}

int HinDevImpl::set_crop(int x, int y, int width, int height)
{
    ALOGD("[%s %d] crop [%d - %d -%d - %d]", __FUNCTION__, __LINE__, x, y, width, height);
    mSidebandWindow->setCrop(x, y, width, height);
    return NO_ERROR;
}

int HinDevImpl::set_frame_rate(int frameRate)
{
    ALOGD("[%s %d]", __FUNCTION__, __LINE__);
    int ret = 0;

    if(mHinDevHandle<0)
        return -1;

    struct v4l2_streamparm sparm;
    memset(&sparm, 0, sizeof( sparm ));
    sparm.type = TVHAL_V4L2_BUF_TYPE;//stream_flag;
    sparm.parm.output.timeperframe.denominator = frameRate;
    sparm.parm.output.timeperframe.numerator = 1;

    ret = ioctl(mHinDevHandle, VIDIOC_S_PARM, &sparm);
    if(ret < 0){
        DEBUG_PRINT(3, "Set frame rate fail: %s. ret=%d", strerror(errno),ret);
    }
    return ret ;
}

int HinDevImpl::get_current_sourcesize(int& width,  int& height,int& pixelformat)
{
    ALOGW("[%s %d]", __FUNCTION__, __LINE__);
    int ret = NO_ERROR;
    if(mHdmiInType == HDMIIN_TYPE_MIPICSI) {
        get_csi_format(mHinDevEventHandle, width, height, pixelformat);
        pixelformat = getNativeWindowFormat(mPixelFormat);
    } else {
        v4l2_format format;
        memset(&format, 0,sizeof(struct v4l2_format));

        format.type = TVHAL_V4L2_BUF_TYPE;
        ret = ioctl(mHinDevHandle, VIDIOC_G_FMT, &format);
        if (ret < 0) {
            DEBUG_PRINT(3, "Open: VIDIOC_G_FMT Failed: %s", strerror(errno));
            return ret;
        }
        width = format.fmt.pix.width;
        height = format.fmt.pix.height;
        pixelformat = getNativeWindowFormat(format.fmt.pix.pixelformat);
        mPixelFormat = format.fmt.pix.pixelformat;
        ALOGD("VIDIOC_G_FMT, w * h: %5d x %5d, fomat 0x%x", width,  height,pixelformat);
    }

    mSrcFrameWidth = width;
    mSrcFrameHeight = height;
    mUseZme = check_zme(mSrcFrameWidth, mSrcFrameHeight, &mDstFrameWidth, &mDstFrameHeight);
    mBufferSize = mSrcFrameWidth * mSrcFrameHeight * 3/2;

    /*if(mIsHdmiIn){
       enum v4l2_buf_type bufType = TVHAL_V4L2_BUF_TYPE;
       ret = ioctl(mHinDevHandle, VIDIOC_STREAMON, &bufType);
    ALOGD("[%s %d] VIDIOC_STREAMON return=:%d", __FUNCTION__, __LINE__, ret);
       if (ret < 0) {
          DEBUG_PRINT(3, "VIDIOC_STREAMON Failed, error: %s", strerror(errno));
       }
       mState = START;
    }*/
    mState = START;
    return ret;
}

int HinDevImpl::aquire_buffer()
{
    int ret = UNKNOWN_ERROR;
    DEBUG_PRINT(3, "%s %d", __FUNCTION__, __LINE__);
    memset(&mHinNodeInfo->vt_buffers, 0, sizeof(mHinNodeInfo->vt_buffers));
    for (int i = 0; i < mBufferCount; i++) {
        memset(&mHinNodeInfo->planes[i], 0, sizeof(struct v4l2_plane));
        memset(&mHinNodeInfo->bufferArray[i], 0, sizeof(struct v4l2_buffer));

        mHinNodeInfo->bufferArray[i].index = i;
        mHinNodeInfo->bufferArray[i].type = TVHAL_V4L2_BUF_TYPE;
        mHinNodeInfo->bufferArray[i].memory = TVHAL_V4L2_BUF_MEMORY_TYPE;
        if (mHinNodeInfo->cap.device_caps & V4L2_CAP_VIDEO_CAPTURE_MPLANE) {
            mHinNodeInfo->bufferArray[i].m.planes = &mHinNodeInfo->planes[i];
            mHinNodeInfo->bufferArray[i].length = PLANES_NUM;
        }

        ret = ioctl(mHinDevHandle, VIDIOC_QUERYBUF, &mHinNodeInfo->bufferArray[i]);
        if (ret < 0) {
            DEBUG_PRINT(3, "VIDIOC_QUERYBUF Failed, error: %s", strerror(errno));
            return ret;
        }


       if (mFrameType & TYPE_SIDEBAND_WINDOW) {
            ret = mSidebandWindow->allocateBuffer(&mHinNodeInfo->buffer_handle_poll[i]);
            if (ret != 0) {
                DEBUG_PRINT(3, "mSidebandWindow->allocateBuffer failed !!!");
                return ret;
            }
        } else if (mFrameType & TYPE_SIDEBAND_VTUNNEL) {
            int fence = -1;
            ret = mSidebandWindow->dequeueBuffer(&mHinNodeInfo->vt_buffers[i], -1, &fence);
            mPreviewRawHandle[i].isFilled = true;
            if (ret != 0) {
                DEBUG_PRINT(3, "mSidebandWindow->allocateBuffer failed !!!");
                return ret;
            } else {
                DEBUG_PRINT(3, "dequeue success fd=%d", mHinNodeInfo->vt_buffers[i]->handle->data[0]);
            }
        } else {
            ret = mSidebandWindow->allocateBuffer(&mHinNodeInfo->buffer_handle_poll[i]);
            if (ret != 0) {
                DEBUG_PRINT(3, "mSidebandWindow->allocateBuffer failed !!!");
                return ret;
            }
        }

        if (mHinNodeInfo->cap.device_caps & V4L2_CAP_VIDEO_CAPTURE_MPLANE) {
            for (int j=0; j<PLANES_NUM; j++) {
                //mHinNodeInfo->bufferArray[i].m.planes[j].m.fd = mSidebandWindow->getBufferHandleFd(mHinNodeInfo->buffer_handle_poll[i]);
                if (mFrameType & TYPE_SIDEBAND_WINDOW) {
                    mHinNodeInfo->bufferArray[i].m.planes[j].m.fd = mSidebandWindow->getBufferHandleFd(mHinNodeInfo->buffer_handle_poll[i]);
                } else if (mFrameType & TYPE_SIDEBAND_VTUNNEL) {
                    mHinNodeInfo->bufferArray[i].m.planes[j].m.fd = mSidebandWindow->getBufferHandleFd(mHinNodeInfo->vt_buffers[i]->handle);
                } else if (mFrameType & TYPE_STREAM_BUFFER_PRODUCER) {
                    mHinNodeInfo->bufferArray[i].m.planes[j].m.fd = mSidebandWindow->getBufferHandleFd(mHinNodeInfo->buffer_handle_poll[i]);
                }
                mHinNodeInfo->bufferArray[i].m.planes[j].length = 0;
            }
        }
    }

    ALOGD("[%s %d] VIDIOC_QUERYBUF successful", __FUNCTION__, __LINE__);
    return -1;
}

int HinDevImpl::release_buffer()
{
    ALOGE("%s %d", __FUNCTION__, __LINE__);
    if (mSidebandHandle) {
        mSidebandWindow->freeBuffer(&mSidebandHandle, 0);
        mSidebandHandle = NULL;
    }

    if (mSignalHandle) {
        mSidebandWindow->freeBuffer(&mSignalHandle, 0);
        mSignalHandle = NULL;
    } else if (mSignalVTBuffer) {
        mSidebandWindow->freeBuffer(&mSignalVTBuffer);
    }

    if (!mRecordHandle.empty()){
        for (int i=0; i<mRecordHandle.size(); i++) {
            mSidebandWindow->freeBuffer(&mRecordHandle[i].outHandle, 1);
            mRecordHandle[i].outHandle = NULL;
        }
        mRecordHandle.clear();
    }

    if (!mPqBufferHandle.empty()) {
        for (int i=0; i<mPqBufferHandle.size(); i++) {
            //mSidebandWindow->freeBuffer(&mPqBufferHandle[i].srcHandle, 1);
            mPqBufferHandle[i].srcHandle = NULL;
            if (mPqBufferHandle[i].outHandle) {
                mSidebandWindow->freeBuffer(&mPqBufferHandle[i].outHandle, 1);
                mPqBufferHandle[i].outHandle = NULL;
            }
            if (mPqBufferHandle[i].out_vt_buffer != nullptr) {
                mSidebandWindow->freeBuffer(&mPqBufferHandle[i].out_vt_buffer);
            }
        }
        mPqBufferHandle.clear();
    }

    if (!mIepBufferHandle.empty()) {
        for (int i=0; i<mIepBufferHandle.size(); i++) {
            mSidebandWindow->freeBuffer(&mIepBufferHandle[i].srcHandle, 1);
            mIepBufferHandle[i].srcHandle = NULL;
            if (mIepBufferHandle[i].outHandle) {
                mSidebandWindow->freeBuffer(&mIepBufferHandle[i].outHandle, 1);
                mIepBufferHandle[i].outHandle = NULL;
            }
            if (mIepBufferHandle[i].out_vt_buffer != nullptr) {
                mSidebandWindow->freeBuffer(&mIepBufferHandle[i].out_vt_buffer);
            }
        }
        mIepBufferHandle.clear();

        if (mIepTempHandle.srcHandle != NULL) {
            mSidebandWindow->freeBuffer(&mIepTempHandle.srcHandle, 1);
            mIepTempHandle.srcHandle = NULL;
        }
        if (mIepTempHandle.outHandle != NULL) {
            mSidebandWindow->freeBuffer(&mIepTempHandle.outHandle, 1);
            mIepTempHandle.outHandle = NULL;
        }
        if (mIepTempHandle.out_vt_buffer != nullptr) {
            mSidebandWindow->freeBuffer(&mIepTempHandle.out_vt_buffer);
        }
    }

    if (mFrameType & TYPE_STREAM_BUFFER_PRODUCER) {
        if (!mPreviewRawHandle.empty()) {
            for (int i=0; i<mPreviewRawHandle.size(); i++) {
                mSidebandWindow->freeBuffer(&mPreviewRawHandle[i].outHandle, 1);
                mPreviewRawHandle[i].outHandle = NULL;
            }
            mPreviewRawHandle.clear();
        }
        if (mSignalPreviewHandle) {
            mSidebandWindow->freeBuffer(&mSignalPreviewHandle, 1);
            mSignalPreviewHandle = NULL;
        }
        for (int i=0; i < mBufferCount; i++) {
            mSidebandWindow->freeBuffer(&mHinNodeInfo->buffer_handle_poll[i], 0);
            mHinNodeInfo->buffer_handle_poll[i] = NULL;
        }
    } else {
        for (int i=0; i<mBufferCount; i++) {
            if (mSidebandWindow) {
                if (mFrameType & TYPE_SIDEBAND_VTUNNEL) {
                    if (mHinNodeInfo->vt_buffers[i]) {
                        mSidebandWindow->cancelBuffer(mHinNodeInfo->vt_buffers[i]);
                        mHinNodeInfo->vt_buffers[i]->handle = NULL;
                        mHinNodeInfo->vt_buffers[i] = nullptr;
                    } else {
                        ALOGE("%s %d vt_buffers %d is nullptr not need release", __FUNCTION__, __LINE__, i);
                    }
                } else {
                    mSidebandWindow->freeBuffer(&mHinNodeInfo->buffer_handle_poll[i], 0);
                    mHinNodeInfo->buffer_handle_poll[i] = NULL;
                }
            }
        }
        if (mFrameType & TYPE_SIDEBAND_VTUNNEL) {
            mSidebandWindow->release();
        }
    }
    return 0;
}

int HinDevImpl::set_preview_info(int top, int left, int width, int height) {
    mPreviewRawHandle.resize(APP_PREVIEW_BUFF_CNT);
    return 0;
}

int HinDevImpl::set_preview_buffer(buffer_handle_t rawHandle, uint64_t bufferId) {
    ALOGD("%s called, rawHandle=%p bufferId=%" PRIu64, __func__, rawHandle, bufferId);
    int buffHandleFd = mSidebandWindow->importHidlHandleBufferLocked(rawHandle);
    ALOGD("%s buffHandleFd=%d, after import rawHandle=%p", __FUNCTION__, buffHandleFd, rawHandle);
    if (mPreviewBuffIndex == -1) {
        mSignalPreviewHandle = rawHandle;
        mPreviewBuffIndex++;
        ALOGW("%s get the signal buffHandleFd=%d, rawHandle=%p", __FUNCTION__, buffHandleFd, rawHandle);
        return 0;
    }
    mPreviewRawHandle[mPreviewBuffIndex].bufferFd = buffHandleFd;
    mPreviewRawHandle[mPreviewBuffIndex].bufferId = bufferId;
    mPreviewRawHandle[mPreviewBuffIndex].outHandle = rawHandle;
    mPreviewRawHandle[mPreviewBuffIndex].isRendering = false;
    mPreviewRawHandle[mPreviewBuffIndex].isFilled = false;
    mPreviewBuffIndex++;
    if(mPreviewBuffIndex == APP_PREVIEW_BUFF_CNT) {
        if (mFrameType & TYPE_STREAM_BUFFER_PRODUCER) {
            mPreviewBuffIndex = -1;
        } else {
            mPreviewBuffIndex = 0;
        }
    }
    return 0;
}


int HinDevImpl::request_capture(buffer_handle_t rawHandle, uint64_t bufferId) {
    //int ret;
    //int bufferIndex = -1;
    //ALOGD("rawHandle = %p,bufferId=%lld,%lld" PRIu64, rawHandle,(long long)bufferId,(long long)mPreviewRawHandle[0].bufferId);
    int previewBufferIndex = -1;
    for (int i=0; i<mPreviewRawHandle.size(); i++) {
        if (mPreviewRawHandle[i].bufferId == bufferId) {
            previewBufferIndex = i;
            break;
        }
    }

    int requestFd = -1;
    /*for (int i=0; i<mBufferCount; i++) {
        int fd = mHinNodeInfo->bufferArray[i].m.planes[0].m.fd;
        if (fd == mPreviewRawHandle[previewBufferIndex].bufferFd) {
            bufferIndex = i;
            requestFd = fd;
            break;
        }
    }*/
    int bufferIndex = previewBufferIndex;

    DEBUG_PRINT(mDebugLevel, "request_capture previewBufferIndex=%d, bufferIndex=%d, requestFd=%d, bufferId %" PRIu64,
        previewBufferIndex, bufferIndex, requestFd, bufferId);
    if ( mFirstRequestCapture/* && mPreviewRawHandle[0].bufferId == bufferId*/) {
        ALOGW("first request_capture, deque first two buffer for skip");
        mFirstRequestCapture = false;
        mHinNodeInfo->currBufferHandleIndex = 0;
        mRequestCaptureCount = 2;
        // mPreviewBuffThread = new PreviewBuffThread(this);
        // mPreviewThreadRunning = true;
        //mPreviewBuffIndex = 0;
        return 0;
    }
    if (mState != START) {
        return 0;
    }

    mRequestCaptureCount++;

    //ALOGD("rawHandle = %p, bufferId=%" PRIu64, rawHandle, bufferId);
    for (int i=0; i<mPreviewRawHandle.size(); i++) {
        if (mPreviewRawHandle[i].bufferId == bufferId) {
            if (mPreviewRawHandle[i].isFilled) {
                mPreviewRawHandle[i].isRendering = false;
                mPreviewRawHandle[i].isFilled = false;
                break;
            }
        }
    }
    int ret = ioctl(mHinDevHandle, VIDIOC_QBUF, &mHinNodeInfo->bufferArray[bufferIndex]);
    if (ret != 0) {
        ALOGE("VIDIOC_QBUF Buffer failed err=%s bufferIndex %d requestFd=%d %" PRIu64,
            strerror(errno), bufferIndex, requestFd, bufferId);
    }

    ALOGV("%s end.", __FUNCTION__);
    return mHinNodeInfo->currBufferHandleIndex;
}

void HinDevImpl::wrapCaptureResultAndNotify(uint64_t buffId,buffer_handle_t handle, bool forceNotify) {
    if (mState == STOPED && !forceNotify) {
        return;
    };
    /*if (mFirstRequestCapture && mPreviewRawHandle[0].bufferId == buffId) {
        ALOGD("first wrapCaptureResultAndNotify, ignore it.");
        mFirstRequestCapture = false;
        return;
    }*/
    tv_input_capture_result_t result;
    //result.buff_id = buffId;
    //ALOGD("%s %lld,end.", __FUNCTION__,(long long)buffId);
    // result.buffer = handle;  //if need
    if(mNotifyQueueCb != NULL) {
        mNotifyQueueCb(result, buffId);
    }
}

void OnInputAvailableCB(int32_t index){
    //ALOGD("InputAvailable index = %d",index);
    if (!mRecordHandle.empty()){
        if (!mRecordHandle[index].isCoding) {
            DEBUG_PRINT(3, "%d not send to coding but return it???", index);
        }
        mRecordHandle[index].isCoding = false;
    }
}

int HinDevImpl::init_encodeserver(MppEncodeServer::MetaInfo* info) {
    if (gMppEnCodeServer == nullptr) {
        gMppEnCodeServer = new MppEncodeServer();
    }

    if (!gMppEnCodeServer->init(info)) {
        ALOGE("Failed to init gMppEnCodeServer");
        return -1;
    }
    NotifyCallback cB = {OnInputAvailableCB};
    gMppEnCodeServer->setNotifyCallback(cB,this);
    // gMppEnCodeServer->start();

    return 0;
}

void HinDevImpl::deinit_encodeserver() {
    ALOGD("deinit_encodeserver enter");
    if(gMppEnCodeServer!=nullptr){
        delete gMppEnCodeServer;
        gMppEnCodeServer = nullptr;
    }
}

void HinDevImpl::stopRecord() {
    if (gMppEnCodeServer != nullptr) {
        gMppEnCodeServer->stop();
    }
    deinit_encodeserver();
    if (!mRecordHandle.empty()){
        for (int i=0; i<mRecordHandle.size(); i++) {
            mSidebandWindow->freeBuffer(&mRecordHandle[i].outHandle, 1);
            mRecordHandle[i].outHandle = NULL;
        }
        mRecordHandle.clear();
    }
}

void HinDevImpl::doRecordCmd(const map<string, string> data) {
    Mutex::Autolock autoLock(mBufferLock);
    if (mState != START) {
        return;
    }
    int width = mSrcFrameWidth;
    int height = mSrcFrameHeight;
    if (mFrameFps < 1) {
        ioctl(mHinDevHandle, RK_HDMIRX_CMD_GET_FPS, &mFrameFps);
        ALOGD("%s RK_HDMIRX_CMD_GET_FPS %d", __FUNCTION__, mFrameFps);
    }
    int fps = mFrameFps;
    bool allowRecord = false;
    ALOGD("%s %d %d", __FUNCTION__, fps, mFrameFps);
    string storePath = "";
    for (auto it : data) {
        ALOGD("%s %s %s", __FUNCTION__, it.first.c_str(), it.second.c_str());
        if (it.first.compare("status") == 0) {
            if (it.second.compare("0") == 0) {
                allowRecord = false;
            } else if (it.second.compare("1") == 0) {
                if (mRecordHandle.empty()) {
                    mRecordHandle.resize(SIDEBAND_RECORD_BUFF_CNT);
                    for (int i=0; i<mRecordHandle.size(); i++) {
                        mSidebandWindow->allocateSidebandHandle(&mRecordHandle[i].outHandle,
                            width, height, HAL_PIXEL_FORMAT_YCrCb_NV12, RK_GRALLOC_USAGE_STRIDE_ALIGN_64);
                        mRecordHandle[i].width = width;
                        mRecordHandle[i].height = height;
                        mRecordHandle[i].verStride = width;//_ALIGN(width, 16);
                        mRecordHandle[i].horStride = _ALIGN(height, 16);
                    }
                    ALOGD("%s all recordhandle %d %d", __FUNCTION__, mRecordHandle[0].verStride,mRecordHandle[0].horStride);
                }
                for (int i=0; i<mRecordHandle.size(); i++) {
                    mRecordHandle[i].isCoding = false;
                }
                mRecordCodingBuffIndex = 0;
                allowRecord = true;
            } else {
                return;
            }
        } else if (it.first.compare("storePath") == 0) {
            storePath = it.second;
        /*} else if (it.first.compare("width")) {
            width = stoi(it.second);
        } else if (it.first.compare("height")) {
            height = stoi(it.second);
        } else if (it.first.compare("fps")) {
            fps = stoi(it.second);*/
        }
    }

    if (fps < 1) {
        fps = 60;
        ALOGD("fps == 0");
    }

    MppEncodeServer::MetaInfo info;
    info.width = width;
    info.height = height;
    info.fps = fps;
    info.port_num = 1234;
    strcat(info.dev_name, "v");
    strcat(info.stream_name, "v");
    ALOGD("%s %dx%d fps=%d %s", __FUNCTION__, width, height, fps, storePath.c_str());

    if (allowRecord && init_encodeserver(&info) != -1) {
        if (storePath.compare("") != 0) {
            gMppEnCodeServer->mOutputFile = fopen(storePath.c_str(), "w+b");
        }
        if (gMppEnCodeServer->mOutputFile == nullptr) {
            ALOGD("%s mOutputFile is null %s " , __FUNCTION__, strerror(errno));
        }
        gMppEnCodeServer->start();
    } else {
        stopRecord();
    }
}

void HinDevImpl::doPQCmd(const map<string, string> data) {
    if (mState != START || mFrameType & TYPE_STREAM_BUFFER_PRODUCER) {
        mPqMode = PQ_OFF;
        return;
    }
    bool stopPq = false;
    int tempPqMode = PQ_OFF;
    int hdmi_range_mode = 0;
    char range_type[PROPERTY_VALUE_MAX] = {0};
    property_get(TV_INPUT_HDMI_RANGE, range_type, "auto");
    if (strcmp(range_type, "auto") == 0) {
        hdmi_range_mode = 0;
    }else if (strcmp(range_type, "full") == 0){
        hdmi_range_mode = 1;
    }else if (strcmp(range_type, "limit") == 0){
        hdmi_range_mode = 2;
    }
    for (auto it : data) {
        ALOGD("%s %s %s", __FUNCTION__, it.first.c_str(), it.second.c_str());
        if (it.first.compare("status") == 0) {
            if (it.second.compare("0") == 0) {
                stopPq = true;
            } else if (it.second.compare("1") == 0 && mPqMode == PQ_OFF) {
                stopPq = false;
            }
        } else if (it.first.compare("mode") == 0) {
            tempPqMode = (int)atoi(it.second.c_str());
        }
    }

    if (mOutRange != mLastOutRange
            && !(mFrameType & TYPE_STREAM_BUFFER_PRODUCER)) {
        ALOGD("%s need redinit mLastOutRange=%d, newOutRange=%d", __FUNCTION__, mLastOutRange, mOutRange);
        if (mRkpq!=nullptr) {
           delete mRkpq;
           mRkpq = nullptr;
        }
        if (mRkiep!=nullptr) {
            delete mRkiep;
            mRkiep = nullptr;
        }
        mPqMode = PQ_OFF;
    }
    mLastOutRange = mOutRange;

    if (stopPq || tempPqMode == PQ_OFF) {
        if (mRkpq!=nullptr) {
           delete mRkpq;
           mRkpq = nullptr;
        }

        if (mRkiep!=nullptr) {
           delete mRkiep;
           mRkiep = nullptr;
        }

        int color_space = 0;
        if (getPqFmt(mPixelFormat) == RKPQ_IMG_FMT_BG24) {
            if(hdmi_range_mode == 2){//force limit
                color_space = RKPQ_CLR_SPC_RGB_LIMITED;
            }else if (hdmi_range_mode == 1){//force full
                    color_space = RKPQ_CLR_SPC_RGB_FULL;
            }else{
                if (mFrameColorRange == HDMIRX_FULL_RANGE) {
                    color_space = RKPQ_CLR_SPC_RGB_FULL;
                } else {
                    color_space = RKPQ_CLR_SPC_RGB_LIMITED;
                }
            }
        } else {
            bool force_yuv_limit = true;
            if(hdmi_range_mode == 2){//force limit
                if (mFrameColorSpace == HDMIRX_XVYCC601
                     || mFrameColorSpace ==HDMIRX_SYCC601) {
                    color_space = RKPQ_CLR_SPC_YUV_601_LIMITED;
                } else {
                    color_space = RKPQ_CLR_SPC_YUV_709_LIMITED;
                }
            }else if (hdmi_range_mode == 1){//force full
                if (mFrameColorSpace == HDMIRX_XVYCC601
                     || mFrameColorSpace ==HDMIRX_SYCC601) {
                    color_space = RKPQ_CLR_SPC_YUV_601_FULL;
                } else {
                    color_space = RKPQ_CLR_SPC_YUV_709_FULL;
                }
            }else{
               if (mFrameColorRange == HDMIRX_FULL_RANGE && !force_yuv_limit) {
                   if (mFrameColorSpace == HDMIRX_XVYCC601
                        || mFrameColorSpace ==HDMIRX_SYCC601) {
                      color_space = RKPQ_CLR_SPC_YUV_601_FULL;
                   } else {
                      color_space = RKPQ_CLR_SPC_YUV_709_FULL;
                   }
              } else {
                 if (mFrameColorSpace == HDMIRX_XVYCC601
                     || mFrameColorSpace ==HDMIRX_SYCC601) {
                    color_space = RKPQ_CLR_SPC_YUV_601_LIMITED;
                } else {
                    color_space = RKPQ_CLR_SPC_YUV_709_LIMITED;
                }
              }
           }
       }
       mDstColorSpace = color_space;
       mUpdateColorSpace = true;
       if (!(mFrameType & TYPE_SIDEBAND_VTUNNEL) && !mPqBufferHandle.empty()) {
          for (int i=0; i<mPqBufferHandle.size(); i++) {
              //mSidebandWindow->freeBuffer(&mPqBufferHandle[i].srcHandle, 1);
              mPqBufferHandle[i].srcHandle = NULL;
              if (mPqBufferHandle[i].outHandle) {
                  mSidebandWindow->freeBuffer(&mPqBufferHandle[i].outHandle, 1);
                  mPqBufferHandle[i].outHandle = NULL;
              }
              if (mPqBufferHandle[i].out_vt_buffer != nullptr) {
                  mSidebandWindow->freeBuffer(&mPqBufferHandle[i].out_vt_buffer);
              }
          }
          mPqBufferHandle.clear();
      }
    } else if(mPqMode == PQ_OFF) {
        if (mPqBufferHandle.empty()) {
            mPqBufferHandle.resize(SIDEBAND_PQ_BUFF_CNT);
            if (!mPqPrepareList.empty()) {
                DEBUG_PRINT(3, "clear mPqPrepareList");
                mPqPrepareList.clear();
            }
            if (!mPqDoneList.empty()) {
                DEBUG_PRINT(3, "clear mPqDoneList");
                mPqDoneList.clear();
            }
            for (int i=0; i<mPqBufferHandle.size(); i++) {
                if (mFrameType & TYPE_SIDEBAND_WINDOW) {
                    mSidebandWindow->allocateSidebandHandle(&mPqBufferHandle[i].outHandle, mDstFrameWidth, mDstFrameHeight,
                        HAL_PIXEL_FORMAT_YCBCR_444_888, RK_GRALLOC_USAGE_STRIDE_ALIGN_64);
                } else if (mFrameType & TYPE_SIDEBAND_VTUNNEL) {
                    mSidebandWindow->allocateBuffer(&mPqBufferHandle[i].out_vt_buffer, mDstFrameWidth, mDstFrameHeight,
                        HAL_PIXEL_FORMAT_YCBCR_444_888,
                        RK_GRALLOC_USAGE_STRIDE_ALIGN_64 | MALI_GRALLOC_USAGE_NO_AFBC);
                    mPqPrepareList.push_back(i);
                }
            }
            ALOGD("%s all pqbufferhandle", __FUNCTION__);
        }

        if (mUseIep) {
            if (mIepBufferHandle.empty()) {
                DEBUG_PRINT(3, "mIepBufferHandle empty, init it");
                mIepBufferHandle.resize(SIDEBAND_IEP_BUFF_CNT);
                for (int i=0; i<mIepBufferHandle.size(); i++) {
                    mSidebandWindow->allocateSidebandHandle(&mIepBufferHandle[i].srcHandle, mDstFrameWidth, mDstFrameHeight,
                    HAL_PIXEL_FORMAT_YCbCr_422_SP, RK_GRALLOC_USAGE_STRIDE_ALIGN_64);
                    if (mFrameType & TYPE_SIDEBAND_WINDOW) {
                        mSidebandWindow->allocateSidebandHandle(&mIepBufferHandle[i].outHandle, mDstFrameWidth, mDstFrameHeight,
                        HAL_PIXEL_FORMAT_YCbCr_422_SP, RK_GRALLOC_USAGE_STRIDE_ALIGN_64);
                    } else if (mFrameType & TYPE_SIDEBAND_VTUNNEL) {
                        mSidebandWindow->allocateBuffer(&mIepBufferHandle[i].out_vt_buffer, mDstFrameWidth, mDstFrameHeight,
                            HAL_PIXEL_FORMAT_YCbCr_422_SP,
                            RK_GRALLOC_USAGE_STRIDE_ALIGN_64 | MALI_GRALLOC_USAGE_NO_AFBC);
                    }
                }
                if (mFrameType & TYPE_SIDEBAND_VTUNNEL) {
                    mSidebandWindow->allocateBuffer(&mIepTempHandle.out_vt_buffer, mDstFrameWidth, mDstFrameHeight,
                            HAL_PIXEL_FORMAT_YCbCr_422_SP,
                            RK_GRALLOC_USAGE_STRIDE_ALIGN_64 | MALI_GRALLOC_USAGE_NO_AFBC);
                }
            }
            if (!mIepPrepareList.empty()) {
                DEBUG_PRINT(3, "clear mIepPrepareList");
                mIepPrepareList.clear();
            }
            if (!mIepDoneList.empty()) {
                DEBUG_PRINT(3, "clear mIepDoneList");
                mIepDoneList.clear();
            }
            for (int i=0; i<mIepBufferHandle.size(); i++) {
                mIepBufferHandle[i].isFilled = false;
                mIepPrepareList.push_back(i);
            }
            mIepBuffIndex = 0;
            mIepBuffOutIndex = 0;
        }

        mPqBuffIndex = 0;
        mPqBuffOutIndex = 0;
        initPqInfo(tempPqMode, hdmi_range_mode);
    }
    mPqMode = tempPqMode;
    ALOGD("%s mStartPQ pqMode=%d", __FUNCTION__, mPqMode);
}

void HinDevImpl::initPqInfo(int pqMode, int hdmi_range_mode) {
    if (mRkpq == nullptr) {
        mRkpq = new rkpq();
        int fmt = getPqFmt(mPixelFormat);
        uint32_t width_stride[2] = {0 , 0};
        uint32_t alignment = 64;
        if (mHdmiInType == HDMIIN_TYPE_MIPICSI) {
            width_stride[0] = 0;
            width_stride[1] = 0;
            alignment = 16;
        } else if (mSrcFrameWidth != _ALIGN(mSrcFrameWidth, 64)) {
            if (fmt == RKPQ_IMG_FMT_BG24) {
                width_stride[0] = _ALIGN(mSrcFrameWidth * 3, 64);
            } else if (fmt == RKPQ_IMG_FMT_NV16) {
                width_stride[0] = _ALIGN(mSrcFrameWidth, 64);
            } else if (fmt == RKPQ_IMG_FMT_NV24){
                width_stride[0] = _ALIGN(mSrcFrameWidth, 64);
                width_stride[1] = _ALIGN(mSrcFrameWidth*2, 64);
            }
        }
        int src_color_space = 0;
        if (fmt == RKPQ_IMG_FMT_BG24) {
            if(hdmi_range_mode == 2){//force limit
                src_color_space = RKPQ_CLR_SPC_RGB_LIMITED;
            }else if (hdmi_range_mode == 1){//force full
                src_color_space = RKPQ_CLR_SPC_RGB_FULL;
            }else{
              if (mFrameColorRange == HDMIRX_FULL_RANGE) {
                src_color_space = RKPQ_CLR_SPC_RGB_FULL;
            } else {
                src_color_space = RKPQ_CLR_SPC_RGB_LIMITED;
              }
            }
        } else {
            bool force_yuv_limit = true;
            if(hdmi_range_mode == 2){//force limit
                if (mFrameColorSpace == HDMIRX_XVYCC601
                        || mFrameColorSpace ==HDMIRX_SYCC601) {
                    src_color_space = RKPQ_CLR_SPC_YUV_601_LIMITED;
                } else {
                    src_color_space = RKPQ_CLR_SPC_YUV_709_LIMITED;
                }
            }else if (hdmi_range_mode == 1){//force full
                if (mFrameColorSpace == HDMIRX_XVYCC601
                        || mFrameColorSpace ==HDMIRX_SYCC601) {
                    src_color_space = RKPQ_CLR_SPC_YUV_601_FULL;
                } else {
                    src_color_space = RKPQ_CLR_SPC_YUV_709_FULL;
                }
            }else{
              if (mFrameColorRange == HDMIRX_FULL_RANGE && !force_yuv_limit) {
                if (mFrameColorSpace == HDMIRX_XVYCC601
                        || mFrameColorSpace ==HDMIRX_SYCC601) {
                    src_color_space = RKPQ_CLR_SPC_YUV_601_FULL;
                } else {
                    src_color_space = RKPQ_CLR_SPC_YUV_709_FULL;
                }
            } else {
                if (mFrameColorSpace == HDMIRX_XVYCC601
                        || mFrameColorSpace ==HDMIRX_SYCC601) {
                    src_color_space = RKPQ_CLR_SPC_YUV_601_LIMITED;
                } else {
                    src_color_space = RKPQ_CLR_SPC_YUV_709_LIMITED;
                }
              }
            }
        }
        int dst_color_space = RKPQ_CLR_SPC_YUV_601_FULL;
            char prop_value[PROPERTY_VALUE_MAX] = {0};
            property_get(TV_INPUT_HDMI_RANGE, prop_value, "auto");
            if (!strcmp(prop_value, "auto") && src_color_space == RKPQ_CLR_SPC_RGB_LIMITED) {
                dst_color_space = RKPQ_CLR_SPC_YUV_601_FULL;
            } else if (!strcmp(prop_value, "limit")) {
                dst_color_space = RKPQ_CLR_SPC_YUV_601_LIMITED;
            }
        int flag = RKPQ_FLAG_CALC_MEAN_LUMA | RKPQ_FLAG_HIGH_PERFORM;
        ALOGD("rkpq init %dx%d stride=%d-%d, fmt=%d, space=%d-%d, flag=%d, alignment=%d",
            mSrcFrameWidth, mSrcFrameHeight, width_stride[0], width_stride[1], fmt, src_color_space, dst_color_space, flag, alignment);
        if (mFrameType & TYPE_STREAM_BUFFER_PRODUCER) {
            mRkpq->init(mSrcFrameWidth, mSrcFrameHeight, width_stride, mDstFrameWidth, mDstFrameHeight, alignment, fmt, src_color_space, RKPQ_IMG_FMT_NV12, dst_color_space, flag);
        } else if(!mUseIep) {
            mRkpq->init(mSrcFrameWidth, mSrcFrameHeight, width_stride, mDstFrameWidth, mDstFrameHeight, alignment, fmt, src_color_space, RKPQ_IMG_FMT_NV24, dst_color_space, flag);
        } else {
            mRkpq->init(mSrcFrameWidth, mSrcFrameHeight, width_stride, mDstFrameWidth, mDstFrameHeight, alignment, fmt, src_color_space, RKPQ_IMG_FMT_NV16, dst_color_space, flag);
        }
        mDstColorSpace = dst_color_space;
        mUpdateColorSpace = true;
        ALOGD("rkpq init finish");
        ALOGD("rkpq iep status %d ", mUseIep);
        if (mFrameType & TYPE_STREAM_BUFFER_PRODUCER) {
        } else if (mUseIep) {
            if (mRkiep == nullptr) {
                mRkiep = new rkiep();
            }
            mRkiep->iep2_init(ALIGN(mDstFrameWidth, 64), mDstFrameHeight, fmt);
        }
    }
}

int HinDevImpl::getPqFmt(int V4L2Fmt) {
    if (V4L2_PIX_FMT_BGR24 == V4L2Fmt) {
        return RKPQ_IMG_FMT_BG24;
    } else if (V4L2_PIX_FMT_NV12 == V4L2Fmt) {
        return RKPQ_IMG_FMT_NV12;
    } else if (V4L2_PIX_FMT_NV16 == V4L2Fmt) {
        return RKPQ_IMG_FMT_NV16;
    } else if (V4L2_PIX_FMT_NV24 == V4L2Fmt) {
        return RKPQ_IMG_FMT_NV24;
    }
    return RKPQ_IMG_FMT_NV12;
}

int HinDevImpl::getOutRange(char* value) {
    int ret = HDMIRX_DEFAULT_RANGE;
    if (!strcmp(value, "limit")) {
        ret = HDMIRX_LIMIT_RANGE;
    } else if (!strcmp(value, "full")) {
        ret = HDMIRX_FULL_RANGE;
    } else if (!strcmp(value, "auto")) {
        //auto means default
        ret = HDMIRX_DEFAULT_RANGE;
    }
    return ret;
}

int HinDevImpl::deal_priv_message(const std::string action, const std::map<std::string, std::string> data) {
    ALOGD("%s %s ", __FUNCTION__, action.c_str());
    if (action.compare("record") == 0) {
        doRecordCmd(data);
        return 1;
    } else if (action.compare("pq") == 0){
        Mutex::Autolock autoLock(mBufferLock);
        doPQCmd(data);
        return 1;
    } else if (action.compare("hdmiinout") == 0) {
        Mutex::Autolock autoLock(mBufferLock);
        if (mFrameType & TYPE_SIDEBAND_WINDOW && NULL != mSidebandHandle) {
            //mSidebandWindow->clearVopArea();
            stopRecord();
            if (mSignalHandle != NULL && mWorkThread != NULL) {
                mSidebandWindow->show(mSignalHandle, FULL_SCREEN, mHdmiInType);
            }
        } else if (mFrameType & TYPE_SIDEBAND_VTUNNEL) {
            stopRecord();
            showVTunnel(mSignalVTBuffer);
        } else if (mFrameType & TYPE_STREAM_BUFFER_PRODUCER) {
            stopRecord();
            wrapCaptureResultAndNotify(0,
                mPreviewRawHandle[mHinNodeInfo->currBufferHandleIndex].outHandle, true);
        }
        return 1;
    } else if (action.compare("refresh_hotcfg") == 0) {
        char prop_value[PROPERTY_VALUE_MAX] = {0};
        property_get(TV_INPUT_DISPLAY_RATIO, prop_value, "0");
        mDisplayRatio = (int)atoi(prop_value);
        return 1;
    }
    return 0;
}

void HinDevImpl::buffDataTransfer(buffer_handle_t srcHandle, int srcFmt, int srcWidth, int srcHeight,
        buffer_handle_t dstHandle, int dstFmt, int dstWidth, int dstHeight, int dstWStride, int dstHStride) {
    if (V4L2_PIX_FMT_BGR24 == srcFmt
            || V4L2_PIX_FMT_NV12 == srcFmt
            || V4L2_PIX_FMT_NV16 == srcFmt) {
        RgaCropScale::Params src, dst;
        src.fd = srcHandle->data[0];
        src.offset_x = 0;
        src.offset_y = 0;
        src.width_stride = srcWidth;
        src.height_stride = srcHeight;
        src.width = srcWidth;
        src.height = srcHeight;
        int rgaSrcFormat = srcFmt;
        if (V4L2_PIX_FMT_BGR24 == srcFmt) {
            rgaSrcFormat = RK_FORMAT_BGR_888;
        } else if (V4L2_PIX_FMT_NV12 == srcFmt) {
            rgaSrcFormat = RK_FORMAT_YCbCr_420_SP;
        } else if (V4L2_PIX_FMT_NV16 == srcFmt) {
            rgaSrcFormat = RK_FORMAT_YCbCr_422_SP;
        }
        src.fmt = rgaSrcFormat;
        src.mirror = false;

        dst.fd = dstHandle->data[0];
        dst.offset_x = 0;
        dst.offset_y = 0;
        dst.width_stride = dstWStride;
        dst.height_stride = dstHStride;
        dst.width = dstWidth;
        dst.height = dstHeight;
        int rgaDstFormat = dstFmt;
        if (V4L2_PIX_FMT_BGR24 == dstFmt) {
            rgaDstFormat = RK_FORMAT_BGR_888;
        } else if (V4L2_PIX_FMT_NV12 == dstFmt) {
            rgaDstFormat = RK_FORMAT_YCbCr_420_SP;
        } else if (V4L2_PIX_FMT_NV16 == dstFmt) {
            rgaDstFormat = RK_FORMAT_YCbCr_422_SP;
        }
        dst.fmt = rgaDstFormat;
        dst.mirror = false;
        RgaCropScale::CropScaleNV12Or21(&src, &dst);
    } else if (V4L2_PIX_FMT_NV24 == srcFmt
            && V4L2_PIX_FMT_NV12 == dstFmt) {
        mSidebandWindow->NV24ToNV12(srcHandle, dstHandle, srcWidth, srcHeight);
    } else if (srcFmt == dstFmt) {
        mSidebandWindow->buffDataTransfer2(srcHandle, dstHandle);
    }
}

int HinDevImpl::workThread()
{
    pthread_t tid=0;
    if (mState == START /*&& !mFirstRequestCapture*/ && mRequestCaptureCount > 0) {
        //DEBUG_PRINT(3, "%s %d currBufferHandleIndex = %d", __FUNCTION__, __LINE__, mHinNodeInfo->currBufferHandleIndex);
 	//mHinNodeInfo->bufferArray[mHinNodeInfo->currBufferHandleIndex].flags = V4L2_BUF_FLAG_NO_CACHE_INVALIDATE |
        //                 V4L2_BUF_FLAG_NO_CACHE_CLEAN;
        if (mFrameType & TYPE_SIDEBAND_WINDOW || mFrameType & TYPE_SIDEBAND_VTUNNEL) {
            if (mHinNodeInfo->currBufferHandleIndex == SIDEBAND_WINDOW_BUFF_CNT)
                mHinNodeInfo->currBufferHandleIndex = mHinNodeInfo->currBufferHandleIndex % SIDEBAND_WINDOW_BUFF_CNT;
        } else {
            if (mHinNodeInfo->currBufferHandleIndex == APP_PREVIEW_BUFF_CNT)
                mHinNodeInfo->currBufferHandleIndex = mHinNodeInfo->currBufferHandleIndex % APP_PREVIEW_BUFF_CNT;
            mRequestCaptureCount--;
        }

        int ret;
        int ts;
        fd_set fds;
        struct timeval tv;
        FD_ZERO(&fds);
        FD_SET(mHinDevHandle, &fds);
        tv.tv_sec = 1;
        tv.tv_usec = 0;
        ts = select(mHinDevHandle + 1, &fds, NULL, NULL, &tv);
        if (mDebugLevel) {
            tid = pthread_self();
            for (int i = 0; i < SIDEBAND_WINDOW_BUFF_CNT; i++) {
               DEBUG_PRINT(mDebugLevel, "==now tid=%lu, i=%d, index=%d, fd=%d", tid, i, mHinNodeInfo->bufferArray[i].index, mHinNodeInfo->bufferArray[i].m.planes[0].m.fd);
            }
        }
        if(ts == 0 || mState != START) {
            return 0;
        }

        int currDqbufHandleIndex = mHinNodeInfo->currBufferHandleIndex;
        int currentDqBufFd = 0;
        if (mFrameType & TYPE_SIDEBAND_VTUNNEL) {
            DEBUG_PRINT(mDebugLevel, "start VIDIOC_DQBUF");
            ret = ioctl(mHinDevHandle, VIDIOC_DQBUF, &mCurrentBufferArray);
        } else {
            ret = ioctl(mHinDevHandle, VIDIOC_DQBUF, &mHinNodeInfo->bufferArray[currDqbufHandleIndex]);
        }
        if (ret < 0) {
            DEBUG_PRINT(3, "VIDIOC_DQBUF Failed, error: %s", strerror(errno));
            return 0;
        } else {
            if (mFrameType & TYPE_SIDEBAND_VTUNNEL) {
                bool findCorrectFd = false;
                currentDqBufFd = mCurrentBufferArray.m.planes[0].m.fd;
                for (int i = 0; i < SIDEBAND_WINDOW_BUFF_CNT; i++) {
                    if (currentDqBufFd == mHinNodeInfo->vt_buffers[i]->handle->data[0]) {
                        currDqbufHandleIndex = i;
                        findCorrectFd = true;
                        break;
                    }
                }
                if (!findCorrectFd) {
                    ALOGE("VIDIOC_DQBUF happen uncorrect err fd=%d", currentDqBufFd);
                    for (int i = 0; i < SIDEBAND_WINDOW_BUFF_CNT; i++) {
                        ALOGE("err vtunnel bufferArray fd=%d", mHinNodeInfo->vt_buffers[i]->handle->data[0]);
                    }
                }
                if (mDebugLevel == 3) {
                    ALOGE("VIDIOC_DQBUF mEnableDump=%d,mDumpFrameCount=%d, tid=%lu, currIndex=%d, fd=%d, %ld.%03ld-%ld",
                        mEnableDump,mDumpFrameCount, tid, currDqbufHandleIndex, currentDqBufFd,
                        (long)mCurrentBufferArray.timestamp.tv_sec, (long)(mCurrentBufferArray.timestamp.tv_usec/1000), (long)(systemTime()/1000000));
                }
            } else if (mDebugLevel == 3) {
                ALOGE("VIDIOC_DQBUF successful.mEnableDump=%d,mDumpFrameCount=%d, tid=%lu, currBufferHandleIndex=%d, fd=%d",
                    mEnableDump,mDumpFrameCount, tid, currDqbufHandleIndex, mHinNodeInfo->bufferArray[currDqbufHandleIndex].m.planes[0].m.fd);
            }
        }
        if (mState != START) {
            //DEBUG_PRINT(3, "mState != START skip");
            return NO_ERROR;
        }

        if (mEnableDump == 1) {
            if (mDumpFrameCount > 0) {
                char fileName[128] = {0};
                sprintf(fileName, "/data/system/dumpimage/tv_input_dump_%dx%d_%d.yuv", mSrcFrameWidth, mSrcFrameHeight, mDumpFrameCount);
                if (mFrameType & TYPE_SIDEBAND_WINDOW) {
                    mSidebandWindow->dumpImage(mHinNodeInfo->buffer_handle_poll[currDqbufHandleIndex], fileName, 0);
                } else if (mFrameType & TYPE_SIDEBAND_VTUNNEL) {
                    mSidebandWindow->dumpImage(mHinNodeInfo->vt_buffers[currDqbufHandleIndex]->handle, fileName, 0);
                }
                mDumpFrameCount--;
            }
        }
        mSidebandWindow->setDebugLevel(mDebugLevel);
        if (mFrameType & TYPE_SIDEBAND_WINDOW) {
            // add flushCache to prevent image tearing and ghosting caused by
            // cache consistency issues
            ret = mSidebandWindow->flushCache(
                mHinNodeInfo->buffer_handle_poll[currDqbufHandleIndex]);
            if (ret != 0) {
                DEBUG_PRINT(3, "mSidebandWindow->flushCache failed !!!");
                return ret;
            }

            if (mPqMode != PQ_OFF && !mPqBufferHandle.empty()) {
                if (mPqBufferHandle[mPqBuffIndex].isFilled) {
                    DEBUG_PRINT(mDebugLevel, "skip pq buffer");
                } else {
                    mPqBufferHandle[mPqBuffIndex].srcHandle = mHinNodeInfo->buffer_handle_poll[currDqbufHandleIndex];
                    mPqBufferHandle[mPqBuffIndex].isFilled = true;
                    mPqBuffIndex++;
                    if (mPqBuffIndex == SIDEBAND_PQ_BUFF_CNT) {
                        mPqBuffIndex = 0;
                    }
                }
            }

            if (((mPqMode & PQ_LF_RANGE) == PQ_LF_RANGE && mPixelFormat == V4L2_PIX_FMT_BGR24)
                    || (mPqMode & PQ_NORMAL) == PQ_NORMAL || mPqIniting) {
                    if(mDebugLevel == 3)
                        ALOGE("workThread mSidebandWindow no show, mPqMode %d mPixelFormat %d mPqIniting %d", mPqMode, V4L2_PIX_FMT_BGR24, mPqIniting);
            } else {
                if (mSkipFrame > 0) {
                    mSkipFrame--;
                    DEBUG_PRINT(3, "mSkipFrame not to show %d", mSkipFrame);
                } else {
                    if (mDebugLevel == 3) {
                        ALOGE("sidebandwindow show index=%d", currDqbufHandleIndex);
                    }
                    mSidebandWindow->show(mHinNodeInfo->buffer_handle_poll[currDqbufHandleIndex], mDisplayRatio, mHdmiInType);
                }
            }

//encode:sendFrame
            if (gMppEnCodeServer != nullptr && gMppEnCodeServer->mThreadEnabled.load()) {
                RKMppEncApi::MyDmaBuffer_t inDmaBuf;
                memset(&inDmaBuf, 0, sizeof(RKMppEncApi::MyDmaBuffer_t));
                inDmaBuf.fd = -1;
                if(!mRecordHandle.empty()) {
                    tv_record_buffer_info_t recordBuffer = mRecordHandle[mRecordCodingBuffIndex];
                    if (!recordBuffer.isCoding) {
                        buffDataTransfer(mHinNodeInfo->buffer_handle_poll[currDqbufHandleIndex], mPixelFormat,
                            mSrcFrameWidth, mSrcFrameHeight,
                            recordBuffer.outHandle, V4L2_PIX_FMT_NV12,
                            recordBuffer.width, recordBuffer.height, recordBuffer.verStride, recordBuffer.horStride);
                        inDmaBuf.fd = recordBuffer.outHandle->data[0];
                    }
                }
                if (inDmaBuf.fd == -1) {
                    DEBUG_PRINT(3, "skip record");
                } else if (gMppEnCodeServer != nullptr) {
                inDmaBuf.size = gMppEnCodeServer->mEncoder->mHorStride *
                                gMppEnCodeServer->mEncoder->mVerStride * 3 / 2;
                inDmaBuf.handler =
                    (void *)mHinNodeInfo
                    ->buffer_handle_poll[currDqbufHandleIndex];
                inDmaBuf.index = mRecordCodingBuffIndex;
                mRecordHandle[mRecordCodingBuffIndex].isCoding = true;
                mRecordCodingBuffIndex++;
                if (mRecordCodingBuffIndex == SIDEBAND_RECORD_BUFF_CNT) {
                    mRecordCodingBuffIndex = 0;
                }
                mLastTime = systemTime();
                bool enc_ret = gMppEnCodeServer->mEncoder->sendFrame(
                                   (RKMppEncApi::MyDmaBuffer_t)inDmaBuf,
                                   getBufSize(V4L2_PIX_FMT_NV12, mSrcFrameWidth, mSrcFrameHeight),
                                   systemTime(), 0);

                now = systemTime();
                diff = now - mLastTime;

                if (!enc_ret) {
                    DEBUG_PRINT(3, "sendFrame failed");
                }
                }
            }
//start encode threads
             if (gMppEnCodeServer != nullptr && !mEncodeThreadRunning) {
                gMppEnCodeServer->start();
                mEncodeThreadRunning = true;
             }
            ret = ioctl(mHinDevHandle, VIDIOC_QBUF, &mHinNodeInfo->bufferArray[currDqbufHandleIndex]);
            if (ret != 0) {
                DEBUG_PRINT(3, "VIDIOC_QBUF Buffer failed %s", strerror(errno));
            } else {
                DEBUG_PRINT(mDebugLevel, "VIDIOC_QBUF %d successful.", currDqbufHandleIndex);
            }
        } else if (mFrameType & TYPE_SIDEBAND_VTUNNEL) {
            if (mSkipFrame > 0) {
                ret = ioctl(mHinDevHandle, VIDIOC_QBUF, &mHinNodeInfo->bufferArray[currDqbufHandleIndex]);
                mSkipFrame--;
                DEBUG_PRINT(3, "mSkipFrame not to show %d", mSkipFrame);
                return NO_ERROR;
            }

            if (mUseZme && mPqMode == PQ_OFF) {
                ret = ioctl(mHinDevHandle, VIDIOC_QBUF, &mHinNodeInfo->bufferArray[currDqbufHandleIndex]);
                DEBUG_PRINT(3, "wait zme prepared");
                return NO_ERROR;
            }

            bool showPqFrame = false;
            if (mPqMode != PQ_OFF && needShowPqFrame(mPqMode)) {
                nsecs_t startTime = systemTime();
                bool fillFinish = false;
                while (mState == START && !fillFinish && mPqMode != PQ_OFF) {
                    {
                        Mutex::Autolock autoLock(mBufferLock);
                        DEBUG_PRINT(mDebugLevel, "enter mBufferLock mPqMode=%d", mPqMode);
                        if (mPqMode != PQ_OFF && !mPqPrepareList.empty()) {
                            for (int i = 0; i < mPqPrepareList.size(); i++) {
                                int pqBufIndex = mPqPrepareList[i];
                                if (!mPqBufferHandle[pqBufIndex].isFilled) {
                                    mPqBufferHandle[pqBufIndex].src_vt_fd = currentDqBufFd;
                                    mPqBufferHandle[pqBufIndex].isFilled = true;
                                    DEBUG_PRINT(mDebugLevel, "===find mPqPrepareList listIndex=%d, pqBufIndex=%d, src_vt_fd=%d===",
                                        i, pqBufIndex, mPqBufferHandle[pqBufIndex].src_vt_fd);
                                    fillFinish = true;
                                    showPqFrame = needShowPqFrame(mPqMode);
                                    break;
                                }
                            }
                        }
                    }
                    if (fillFinish) {
                        break;
                    }
                    usleep(1000);
                    long waitTime = (long)((systemTime() - startTime)/1000000);
                    if (waitTime > 10) {
                        DEBUG_PRINT(3, "wait availe mPqPrepareList waitTime=%ld", waitTime);
                    }
                }
            } else if (mPqMode != PQ_OFF) {
                if (mPqBufferHandle[mPqBuffIndex].isFilled) {
                    DEBUG_PRINT(mDebugLevel, "skip pq luma buffer");
                } else {
                    mPqBufferHandle[mPqBuffIndex].src_vt_fd = currentDqBufFd;
                    mPqBufferHandle[mPqBuffIndex].isFilled = true;
                    mPqBuffIndex++;
                    if (mPqBuffIndex == SIDEBAND_PQ_BUFF_CNT) {
                        mPqBuffIndex = 0;
                    }
                }
            }
            if (!showPqFrame && mState == START) {
                DEBUG_PRINT(mDebugLevel, "sidebandwindow show index=%d", currDqbufHandleIndex);
                showVTunnel(mHinNodeInfo->vt_buffers[currDqbufHandleIndex]);
            }
        } else {
            if (mV4L2DataFormatConvert) {
                mSidebandWindow->buffDataTransfer(mHinNodeInfo->buffer_handle_poll[currDqbufHandleIndex], mPreviewRawHandle[mPreviewBuffIndex].outHandle);
            }
            if (mRkpq == nullptr) {
            } else {
                Mutex::Autolock autoLock(mBufferLock);
                mRkpq->dopq(mHinNodeInfo->bufferArray[currDqbufHandleIndex].m.planes[0].m.fd,
                    mPreviewRawHandle[currDqbufHandleIndex].bufferFd, PQ_LF_RANGE);
                wrapCaptureResultAndNotify(mPreviewRawHandle[currDqbufHandleIndex].bufferId,
                    mPreviewRawHandle[currDqbufHandleIndex].outHandle, false);
            }
        }
        mHinNodeInfo->currBufferHandleIndex++;
    } else {
        usleep(500);
    }
    return NO_ERROR;
}

bool HinDevImpl::needShowPqFrame(int pqMode) {
    if ((pqMode & PQ_LF_RANGE) == PQ_LF_RANGE
            || (pqMode & PQ_NORMAL) == PQ_NORMAL) {
        DEBUG_PRINT(mDebugLevel, "pqMode %d", pqMode);
        return true;
    }
    return false;
}

bool HinDevImpl::qBuf(int fd, bool noFoundLog) {
    if (mState != START || fd < 0) {
        return true;
    }
    for (int i=0; i < SIDEBAND_WINDOW_BUFF_CNT; i++) {
        if (fd == mHinNodeInfo->bufferArray[i].m.planes[0].m.fd) {
            int ret = ioctl(mHinDevHandle, VIDIOC_QBUF, &mHinNodeInfo->bufferArray[i]);
            if (ret != 0) {
                ALOGE("%s %d VIDIOC_QBUF index=%d, fd=%d failed %s", __FUNCTION__, __LINE__, i, fd, strerror(errno));
                return false;
            } else {
                DEBUG_PRINT(mDebugLevel, "VIDIOC_QBUF index=%d, fd=%d successful.", i, fd);
                return true;
            }
        }
    }
    if (noFoundLog) {
        ALOGE("%s not do VIDIOC_QBUF with fd=%d", __FUNCTION__, fd);
    }
    return false;
}

void HinDevImpl::showVTunnel(vt_buffer_t* vt_buffer) {
    if (vt_buffer == nullptr) {
        ALOGE("%s buffer is nullptr", __FUNCTION__);
        return;
    }
    int ret = 0;
    if (mDebugLevel == 3) {
        ALOGW("%s %d vtQueueFd=%d", __FUNCTION__, __LINE__, vt_buffer->handle->data[0]);
    }
    ret = mSidebandWindow->queueBuffer(vt_buffer, -1, 0);
    if (mState != START) {
        ALOGE("%s after vtunnel queueBuffer mState != START", __FUNCTION__);
        return;
    }
    mQbufCount++;
    DEBUG_PRINT(mDebugLevel, "queueBuffer ret=%d, mQbufCount=%d", ret, mQbufCount);
    if (mQbufCount > 2) {
        vt_buffer_t *vtBuf;
        //memset(vtBuf, 0, sizeof(vt_buffer_t));
        int fence_id = -1;
        int vtDqbufFd = -1;
        int timeout_ms = 1000;
        nsecs_t startDqbufTime = systemTime();
        ret = mSidebandWindow->dequeueBuffer(&vtBuf, timeout_ms, &fence_id);
        /*if (ret < 0) {
            long usedDqBufTime = (long)((systemTime() - startDqbufTime)/1000000);
            DEBUG_PRINT(3, "dqBuf failed ret=%d, usedDqBufTime=%ld", ret, usedDqBufTime);
            if (usedDqBufTime >= timeout_ms) {
                while (ret < 0 && mState == START) {
                    ret = mSidebandWindow->dequeueBuffer(&vtBuf, timeout_ms, &fence_id);
                }
            }
        }*/
        if (ret >= 0 && vtBuf && vtBuf->handle) {
            vtDqbufFd = vtBuf->handle->data[0];
            if (mDebugLevel == 3) {
                ALOGE("%s vtDqbufFd=%d, ret=%d, usedDqBufTime=%ld",
                    __FUNCTION__, vtDqbufFd, ret, (long)((systemTime() - startDqbufTime)/1000000));
            }
        } else {
            DEBUG_PRINT(3, "dqBuf but not find displayDqbufFd ret=%d, usedDqBufTime=%ld",
                ret, (long)((systemTime() - startDqbufTime)/1000000));
        }
        mQbufCount--;

        if (qBuf(vtDqbufFd, false)) {
            return;
        }
        if (mState == START && vtDqbufFd > -1 && !mPqDoneList.empty()) {
            for (int i = 0; i < mPqDoneList.size(); i++) {
                int pqBufIndex = mPqDoneList[i];
                if (vtDqbufFd == mPqBufferHandle[pqBufIndex].out_vt_buffer->handle->data[0]) {
                    DEBUG_PRINT(mDebugLevel, "pqDoneList listIndex=%d, pqBufIndex=%d, fd=%d to pqPrepareList",
                        i, pqBufIndex, vtDqbufFd);
                    //mPqDoneList[i].src_vt_fd;
                    mPqBufferHandle[pqBufIndex].src_vt_fd = -1;
                    mPqBufferHandle[pqBufIndex].isFilled = false;
                    mPqDoneList.erase(mPqDoneList.begin() + i);
                    mPqPrepareList.push_back(pqBufIndex);
                    return;
                }
            }
        }
        if (mState == START && vtDqbufFd > -1 && !mIepDoneList.empty()) {
            for (int i = 0; i < mIepDoneList.size(); i++) {
                int iepBufIndex = mIepDoneList[i];
                if (vtDqbufFd == mIepBufferHandle[iepBufIndex].out_vt_buffer->handle->data[0]) {
                    DEBUG_PRINT(mDebugLevel, "iepDoneList listIndex=%d, iepBufIndex=%d, fd=%d to iepPreparelist",
                        i, iepBufIndex, vtDqbufFd);
                    //mIepDoneList[i].src_vt_fd = -1;
                    mIepDoneList.erase(mIepDoneList.begin() + i);
                    mIepPrepareList.push_back(iepBufIndex);
                    return;
                }
            }
        }
        if (mState != START || vtDqbufFd < 0 || ret != 0) {
            DEBUG_PRINT(3, "warn or err mState=%d, vtDqbufFd=%d, ret=%d", mState, vtDqbufFd, ret);
            return;
        }
    }
}

int HinDevImpl::pqBufferThread() {
    char debugInfoValue[PROPERTY_VALUE_MAX] = {0};
    property_get(TV_INPUT_DEBUG_LEVEL, debugInfoValue, "0");
    mDebugLevel = (int)atoi(debugInfoValue);
    property_get(TV_INPUT_DEBUG_DUMP, debugInfoValue, "0");
    mEnableDump = (int)atoi(debugInfoValue);
    if (mEnableDump == 1) {
        property_get(TV_INPUT_DEBUG_DUMPNUM, debugInfoValue, "0");
        int dumpFrameCount = (int)atoi(debugInfoValue);
        if (dumpFrameCount > 0) {
            mDumpFrameCount = dumpFrameCount;
        }
    }
    if (mFrameType & TYPE_STREAM_BUFFER_PRODUCER || mState != START) {
        usleep(500);
        return NO_ERROR;
    }
    {
    Mutex::Autolock autoLock(mBufferLock);
    if (mState != START) {
        return NO_ERROR;
    }
    char prop_value[PROPERTY_VALUE_MAX] = {0};
    int pqMode = PQ_OFF;
    int value = 0;
    int auto_detection = property_get_int32(TV_INPUT_PQ_AUTO_DETECTION, 0);
    property_get(TV_INPUT_PQ_RANGE, prop_value, "auto");
    mOutRange = getOutRange(prop_value);
    property_get(TV_INPUT_PQ_ENABLE, prop_value, "0");
    value = (int)atoi(prop_value);
    if (value != 0)  {
        pqMode |= PQ_NORMAL;
    } 
    if (mLastPqStatus == -1) {
        mLastZmeStatus = mUseZme;
        mLastPqStatus = value;
    } else if (mLastPqStatus != value) {
        /*The stream needs to be reopened when the pq state changes
            to get the correct colorspace and zme states.
        */
        //mUseZme = check_zme(mSrcFrameWidth, mSrcFrameHeight, &mDstFrameWidth, &mDstFrameHeight);
        //if (mLastZmeStatus != mUseZme){
            mPqIniting = true;
            tv_input_command command;
            command.command_id = CMD_HDMIIN_RESET;
            if(mNotifyCommandCb != NULL) {
                mNotifyCommandCb(command);
            }
        //}
        mLastZmeStatus = mUseZme;
        mLastPqStatus = value;
    }
    property_get(TV_INPUT_PQ_LUMA, prop_value, "0");
    value = (int)atoi(prop_value);
    if (value != 0) {
        pqMode |= PQ_CACL_LUMA;
    } else if (auto_detection != 0) {
        property_get(TV_INPUT_HDMI_RANGE, prop_value, "auto");
        int fmt = getPqFmt(mPixelFormat);
      if (!strcmp(prop_value, "auto") && fmt == RKPQ_IMG_FMT_BG24 &&
           mFrameColorRange != HDMIRX_FULL_RANGE) {
            pqMode |= PQ_LF_RANGE;
      }
        if (mUseIep) {
            pqMode |= PQ_NORMAL;
        }
    }

   if ((mOutRange == HDMIRX_LIMIT_RANGE && mFrameColorRange != HDMIRX_LIMIT_RANGE) ||
        (mOutRange == HDMIRX_FULL_RANGE && mFrameColorRange != HDMIRX_FULL_RANGE)) {
       pqMode |= PQ_LF_RANGE;
   }

    if (mUpdateColorSpace && mSidebandWindow->getSidebandPlaneId() > 0) {
        mRkpq->setDstColorSpace(mSidebandWindow->getSidebandPlaneId(), mDstColorSpace);
        mUpdateColorSpace = false;
    }
    if (mPqMode != pqMode || mOutRange != mLastOutRange) {
        bool isPqShowFrameMode = needShowPqFrame(pqMode);
        if (mIsLastPqShowFrameMode && !isPqShowFrameMode) {
            DEBUG_PRINT(3, "start queue all buf from mPqPrepareList");
            if (!mPqPrepareList.empty()) {
                for (int i = 0; i < mPqPrepareList.size(); i++) {
                    int pqBufIndex = mPqPrepareList[i];
                    if (mPqBufferHandle[pqBufIndex].isFilled) {
                        qBuf(mPqBufferHandle[pqBufIndex].src_vt_fd, true);
                    }
                }
                /*done list will push_back, so reset or clear mPqPrepareList may cause num uncorrect
                  Do not execute the following code:
                  mPqPrepareList.clear();
                */
            }
            DEBUG_PRINT(3, "end queue all buf from mPqPrepareList");
        } else if (!mIsLastPqShowFrameMode && isPqShowFrameMode) {
            DEBUG_PRINT(3, "start reset mPqBufferHandle fill status");
            if (!mPqBufferHandle.empty()) {
                for (int i=0; i < mPqBufferHandle.size(); i++) {
                    mPqBufferHandle[i].isFilled = false;
                }
            }
            DEBUG_PRINT(3, "end reset mPqBufferHandle fill status");
            if (mUseIep) {
                if (!mPqDoneList.empty()) {
                    DEBUG_PRINT(3, "clear mPqDoneList with iep");
                    mPqDoneList.clear();
                }
            }
        }
        mIsLastPqShowFrameMode = isPqShowFrameMode;

        map<string, string> pqData;
        pqData.clear();
        pqData.insert({"mode", to_string(pqMode)});
        doPQCmd(pqData);
    }

    if (mState == START && mPqMode != PQ_OFF && !mPqBufferHandle.empty()) {
        if (mFrameType & TYPE_SIDEBAND_VTUNNEL) {
            if (mPqMode == PQ_CACL_LUMA) {
                if (mPqBufferHandle[mPqBuffOutIndex].isFilled) {
                    DEBUG_PRINT(mDebugLevel, "dopq luma %d", mPqBufferHandle[mPqBuffOutIndex].src_vt_fd);
                    mRkpq->dopq(mPqBufferHandle[mPqBuffOutIndex].src_vt_fd,
                        mPqBufferHandle[mPqBuffOutIndex].out_vt_buffer->handle->data[0], PQ_CACL_LUMA);
                    mPqBufferHandle[mPqBuffOutIndex].isFilled = false;
                    mPqBuffOutIndex++;
                    if (mPqBuffOutIndex == SIDEBAND_PQ_BUFF_CNT) {
                        mPqBuffOutIndex = 0;
                    }
                }
            } else if (!mPqPrepareList.empty()) {
                int pqBufIndex = mPqPrepareList[0];
                if (mPqBufferHandle[pqBufIndex].isFilled) {
                    bool enableLuma = (mPqMode & PQ_CACL_LUMA) == PQ_CACL_LUMA;
                    bool enablePqNormal = (mPqMode & PQ_NORMAL) == PQ_NORMAL;
                    if (enablePqNormal && mUseIep) {
                        mRkpq->dopq(mPqBufferHandle[pqBufIndex].src_vt_fd,
                            mPqBufferHandle[pqBufIndex].out_vt_buffer->handle->data[0],
                            enableLuma?(PQ_CACL_LUMA|PQ_IEP):PQ_IEP);
                    } else if (enablePqNormal) {
                        mRkpq->dopq(mPqBufferHandle[pqBufIndex].src_vt_fd,
                            mPqBufferHandle[pqBufIndex].out_vt_buffer->handle->data[0],
                            enableLuma?(PQ_CACL_LUMA|PQ_NORMAL):PQ_NORMAL);
                    } else if ((mPqMode & PQ_LF_RANGE) == PQ_LF_RANGE) {
                        mRkpq->dopq(mPqBufferHandle[pqBufIndex].src_vt_fd,
                            mPqBufferHandle[pqBufIndex].out_vt_buffer->handle->data[0],
                            enableLuma?(PQ_CACL_LUMA|PQ_LF_RANGE):PQ_LF_RANGE);
                    }
                    qBuf(mPqBufferHandle[pqBufIndex].src_vt_fd, true);
                    mPqPrepareList.erase(mPqPrepareList.begin());
                    mPqDoneList.push_back(pqBufIndex);
                    if (!mUseIep) {
                        showVTunnel(mPqBufferHandle[pqBufIndex].out_vt_buffer);
                    }
                }
            }
        } else if (mFrameType & TYPE_SIDEBAND_WINDOW
                && mPqBufferHandle[mPqBuffOutIndex].isFilled) {
            bool showPqFrame = false;
            bool enableLuma = (mPqMode & PQ_CACL_LUMA) == PQ_CACL_LUMA;
            if ((mPqMode & PQ_NORMAL) == PQ_NORMAL) {
                if (mUseIep) {
                    if (!mIepBufferHandle.empty()) {
                        if (mIepBufferHandle[mIepBuffIndex].isFilled) {
                             mIepBuffIndex++;
                             if (mIepBuffIndex == SIDEBAND_IEP_BUFF_CNT) {
                                mIepBuffIndex = 0;
                             }
                         } else {
                            mRkpq->dopq(mPqBufferHandle[mPqBuffOutIndex].srcHandle->data[0],
                                mIepBufferHandle[mIepBuffIndex].srcHandle->data[0], enableLuma?(PQ_CACL_LUMA|PQ_IEP):PQ_IEP);
                             mIepBufferHandle[mIepBuffIndex].isFilled = true;
                             mIepBuffIndex++;
                             if (mIepBuffIndex == SIDEBAND_IEP_BUFF_CNT) {
                                 mIepBuffIndex = 0;
                             }
                         }
                    }
                } else {
                    mRkpq->dopq(mPqBufferHandle[mPqBuffOutIndex].srcHandle->data[0],
                        mPqBufferHandle[mPqBuffOutIndex].outHandle->data[0], enableLuma?(PQ_CACL_LUMA|PQ_NORMAL):PQ_NORMAL);
                    showPqFrame = true;
                }
            } else if (((mPqMode & PQ_LF_RANGE) == PQ_LF_RANGE && mPixelFormat == V4L2_PIX_FMT_BGR24)) {
                mRkpq->dopq(mPqBufferHandle[mPqBuffOutIndex].srcHandle->data[0],
                    mPqBufferHandle[mPqBuffOutIndex].outHandle->data[0], enableLuma?(PQ_CACL_LUMA|PQ_LF_RANGE):PQ_LF_RANGE);
                showPqFrame = true;
            } else if (enableLuma) {
                mRkpq->dopq(mPqBufferHandle[mPqBuffOutIndex].srcHandle->data[0],
                    mPqBufferHandle[mPqBuffOutIndex].outHandle->data[0], PQ_CACL_LUMA);
            }
            if (mState != START) {
                return NO_ERROR;
            }
            if (showPqFrame && !mPqIniting) {
                mSidebandWindow->show(mPqBufferHandle[mPqBuffOutIndex].outHandle, mDisplayRatio, mHdmiInType);
            } else if(mDebugLevel == 3) {
                ALOGE("pq mSidebandWindow no show, because showPqFrame false");
            }
            mPqBufferHandle[mPqBuffOutIndex].isFilled = false;
            mPqBuffOutIndex++;
            if (mPqBuffOutIndex == SIDEBAND_PQ_BUFF_CNT) {
                mPqBuffOutIndex = 0;
            }
        }
    }
    }
    usleep(500);

    return NO_ERROR;
}

bool HinDevImpl::check_zme(int src_width, int src_height, int* dst_width, int* dst_height) {
    int pq_enable = property_get_int32(TV_INPUT_PQ_ENABLE, 0);
    if(!pq_enable) {
        return false;
    }
    *dst_width = src_width;
    *dst_height = src_height;
    uint32_t width = 0, height = 0;
    char res_prop[PROPERTY_VALUE_MAX];
    int prop_len = property_get(TV_INPUT_RESOLUTION_MAIN, res_prop, NULL);
    if(prop_len > 0) {
        sscanf(res_prop, "%dx%d", &width, &height);
    } else {
        mRkpq->getResolutionInfo(&width, &height);
    }
    if(src_width == 1920 && src_height == 1080 &&
        width == 3840 && height == 2160 && pq_enable && check_interlaced() == 0) {
        *dst_width = width;
        *dst_height = height;
        return true;
    }
    return false;
}

int HinDevImpl::iepBufferThread() {
    //Mutex::Autolock autoLock(mBufferLock); will happend rob wait if mBufferLock
    if (mState == START && mPqMode != PQ_OFF && mUseIep) {
        int iepDilOrder = 0;
        if (mFrameType & TYPE_SIDEBAND_VTUNNEL) {
            Mutex::Autolock autoLock(mBufferLock);
            if (mState == START && !mIepBufferHandle.empty() && !mIepPrepareList.empty()
                    && !mPqDoneList.empty() && mPqDoneList.size() > 2) {
                int pqBufIndex0 = mPqDoneList[0];
                int pqBufIndex1 = mPqDoneList[1];
                int pqBufIndex2 = mPqDoneList[2];
                //DEBUG_PRINT(mDebugLevel, "pqBuf fill-%d, %d, %d", mPqBufferHandle[pqBufIndex0].isFilled,
                //    mPqBufferHandle[pqBufIndex1].isFilled, mPqBufferHandle[pqBufIndex2].isFilled);
                if (mPqBufferHandle[pqBufIndex0].isFilled
                        && mPqBufferHandle[pqBufIndex1].isFilled
                        && mPqBufferHandle[pqBufIndex2].isFilled) {
                    DEBUG_PRINT(mDebugLevel, "do iep iep iep");
                    int iepBufIndex = mIepPrepareList[0];
                    mRkiep->iep2_deinterlace(mPqBufferHandle[pqBufIndex0].out_vt_buffer->handle->data[0],
                        mPqBufferHandle[pqBufIndex1].out_vt_buffer->handle->data[0],
                        mPqBufferHandle[pqBufIndex2].out_vt_buffer->handle->data[0],
                        mIepBufferHandle[iepBufIndex].out_vt_buffer->handle->data[0],
                        mIepTempHandle.out_vt_buffer->handle->data[0], &iepDilOrder);
                    if (mState != START) {
                        DEBUG_PRINT(mDebugLevel, "iep mState != START return NO_ERROR");
                        return NO_ERROR;
                    }
                    //deal mPqDoneList and mPqPrepareList
                    mPqBufferHandle[pqBufIndex0].src_vt_fd = -1;
                    mPqBufferHandle[pqBufIndex0].isFilled = false;
                    mPqDoneList.erase(mPqDoneList.begin());
                    mPqPrepareList.push_back(pqBufIndex0);
                    //iep show
                    mIepPrepareList.erase(mIepPrepareList.begin());
                    mIepDoneList.push_back(iepBufIndex);
                    showVTunnel(mIepBufferHandle[iepBufIndex].out_vt_buffer);
                }
            }
        } else if (mFrameType & TYPE_SIDEBAND_WINDOW && !mIepBufferHandle.empty()) {
            int cur = mIepBuffOutIndex;
            int last1 = (cur + SIDEBAND_IEP_BUFF_CNT -1)%SIDEBAND_IEP_BUFF_CNT;
            int last2 = (cur + SIDEBAND_IEP_BUFF_CNT -2)%SIDEBAND_IEP_BUFF_CNT;
            //ALOGD("check iep %s  %d %d %d----%d %d %d", __FUNCTION__, last2, last1, cur, mIepBufferHandle[last2].isFilled, mIepBufferHandle[last1].isFilled, mIepBufferHandle[cur].isFilled);
            if (mIepBufferHandle[cur].isFilled && mIepBufferHandle[last1].isFilled && mIepBufferHandle[last2].isFilled) {
                int curIepOutIndex = mIepBuffOutIndex;
                int nextIepOutIndex = (mIepBuffOutIndex + SIDEBAND_IEP_BUFF_CNT + 1)%SIDEBAND_IEP_BUFF_CNT;
                mRkiep->iep2_deinterlace(mIepBufferHandle[cur].srcHandle->data[0], mIepBufferHandle[last1].srcHandle->data[0], mIepBufferHandle[last2].srcHandle->data[0],
                    mIepBufferHandle[curIepOutIndex].outHandle->data[0], mIepBufferHandle[nextIepOutIndex].outHandle->data[0], &iepDilOrder);
                if (mState != START) {
                    if(mDebugLevel == 3) {
                        ALOGE("iep mState != START return NO_ERROR");
                    }
                    return NO_ERROR;
                }
                mSidebandWindow->show(mIepBufferHandle[curIepOutIndex].outHandle, mDisplayRatio, mHdmiInType);
                mIepBufferHandle[curIepOutIndex].isFilled = false;
                mIepBuffOutIndex ++;
                if (mIepBuffOutIndex == SIDEBAND_IEP_BUFF_CNT) {
                    mIepBuffOutIndex = 0;
                }
            }
        }
    }
    usleep(1000);

    return NO_ERROR;
}

int HinDevImpl::check_interlaced() {
    struct v4l2_dv_timings dv_timings;
    memset(&dv_timings, 0 ,sizeof(struct v4l2_dv_timings));
    ALOGE("check_interlaced mHinDevHandle %d", mHinDevHandle);
    int err = ioctl(mHinDevHandle, VIDIOC_QUERY_DV_TIMINGS, &dv_timings);
    ALOGE("check_interlaced ioctl error %d", err);
    if (err < 0) {
        return 0;
    } else {
        ALOGE("check_interlaced interlaced %d", dv_timings.bt.interlaced);
        return dv_timings.bt.interlaced;
    }
}

void HinDevImpl::set_interlaced(int interlaced) {
    int pqEnable = property_get_int32(TV_INPUT_PQ_ENABLE, 0);
    if (pqEnable == 1)  {
        if (interlaced == 1)
            mUseIep = true;
        else
            mUseIep = false;
    } else {
        mUseIep = false;
    }
}
