// FIXME: your file license if you have one

#include "Hdmi.h"
#include "log/log.h"
#include <sys/inotify.h>
#include <errno.h>
#include <linux/videodev2.h>
#include <math.h>
#include "HdmiCallback.h"
#include "HdmiAudioCallback.h"
#include <condition_variable>

#define BASE_VIDIOC_PRIVATE 192     /* 192-255 are private */
#define RKMODULE_GET_HDMI_MODE       \
        _IOR('V', BASE_VIDIOC_PRIVATE + 34, __u32)


namespace rockchip::hardware::hdmi::implementation {

sp<::rockchip::hardware::hdmi::V1_0::IHdmiCallback> mCb = nullptr;
sp<::rockchip::hardware::hdmi::V1_0::IHdmiAudioCallback> mAudioCb = nullptr;
sp<::rockchip::hardware::hdmi::V1_0::IHdmiRxStatusCallback> mStatusCb = nullptr;
sp<::rockchip::hardware::hdmi::V1_0::IFrameWarpper> mFrameWarpper = nullptr;

std::mutex mLock;
std::mutex mLockAudio;
std::mutex mLockStatusCb;
std::mutex mLockFrameWarpper;

hidl_string mDeviceId;

const int kMaxDevicePathLen = 256;
const char* kDevicePath = "/dev/";
const char kPrefix[] = "v4l-subdev";
const int kPrefixLen = sizeof(kPrefix) - 1;
const int kDevicePrefixLen = sizeof(kDevicePath) + kPrefixLen + 1;

char kV4l2DevicePath[kMaxDevicePathLen];
int mMipiHdmi = 0;

sp<V4L2DeviceEvent> mV4l2Event;
int findMipiHdmi()
{
    DIR* devdir = opendir(kDevicePath);
    if(devdir == 0) {
        ALOGE("%s: cannot open %s! ", __FUNCTION__, kDevicePath);
        return -1;
    }
    struct dirent* de;
    int videofd,ret;
    while ((de = readdir(devdir)) != 0) {
        // Find external v4l devices that's existing before we start watching and add them
        if (!strncmp(kPrefix, de->d_name, kPrefixLen)) {
            std::string deviceId(de->d_name + kPrefixLen);
            ALOGD("found %s", de->d_name);
            char v4l2DeviceDriver[16];
            snprintf(kV4l2DevicePath, kMaxDevicePathLen,"%s%s", kDevicePath, de->d_name);
            videofd = open(kV4l2DevicePath, O_RDWR);
            if (videofd < 0){
                ALOGE("[%s %d] open device failed:%x [%s]", __FUNCTION__, __LINE__, videofd,strerror(errno));
                continue;
            } else {
                uint32_t ishdmi;
                ret = ::ioctl(videofd, RKMODULE_GET_HDMI_MODE, (void*)&ishdmi);
                if (ret < 0) {
                    ALOGE("RKMODULE_GET_HDMI_MODE Failed, error: %s", strerror(errno));
                    close(videofd);
                    continue;
                }
                ALOGD("%s RKMODULE_GET_HDMI_MODE:%d",kV4l2DevicePath,ishdmi);
                if (ishdmi)
                {
                    mMipiHdmi = videofd;
                    ALOGD("MipiHdmi fd:%d",mMipiHdmi);
                    if (mMipiHdmi < 0)
                    {
                        return ret;
                    }
                    mV4l2Event->initialize(mMipiHdmi);
                }
            }
        }
    }
    closedir(devdir);
    return ret;
}


Return<void> Hdmi::foundHdmiDevice(const hidl_string& deviceId, const ::android::sp<::rockchip::hardware::hdmi::V1_0::IHdmiRxStatusCallback>& cb) {

    ALOGD("@%s,deviceId:%s",__FUNCTION__,deviceId.c_str());
    std::unique_lock<std::mutex> lk(mLockStatusCb);
    mDeviceId = deviceId.c_str();
    mStatusCb = cb;
    lk.unlock();
    return Void();
}

Return<void> Hdmi::addAudioListener(const ::android::sp<::rockchip::hardware::hdmi::V1_0::IHdmiAudioCallback>& cb) {
    ALOGD("@%s",__FUNCTION__);
    std::unique_lock<std::mutex> lk(mLockAudio);
    mAudioCb = cb;
    lk.unlock();
    return Void();
}
Return<void> Hdmi::removeAudioListener(const ::android::sp<::rockchip::hardware::hdmi::V1_0::IHdmiAudioCallback>& cb) 
{
    ALOGD("@%s",__FUNCTION__);
    std::unique_lock<std::mutex> lk(mLockAudio);
    mAudioCb = nullptr;
    lk.unlock();
    return Void();
}
Return<void> Hdmi::onAudioChange(const ::rockchip::hardware::hdmi::V1_0::HdmiAudioStatus& status) {
    ALOGD("@%s",__FUNCTION__);
    std::unique_lock<std::mutex> lk(mLockAudio);
    if (mAudioCb.get()!=nullptr && strstr(status.deviceId.c_str(),mDeviceId.c_str()))
    {
        ALOGD("@%s,cameraId:%s status:%d",__FUNCTION__,status.deviceId.c_str(),status.status);
        if (status.status)
        {
            mAudioCb->onConnect(status.deviceId);
        }else{
            mAudioCb->onDisconnect(status.deviceId);
        }
    }
    lk.unlock();
    return Void();
}
Return<void> Hdmi::getHdmiDeviceId(getHdmiDeviceId_cb _hidl_cb) {
    ALOGD("@%s,mDeviceId：%s",__FUNCTION__,mDeviceId.c_str());
    _hidl_cb(mDeviceId);
    return Void();
}
Return<void> Hdmi::getMipiStatus(Hdmi::getMipiStatus_cb _hidl_cb){
    ALOGD("@%s",__FUNCTION__);
    V1_0::HdmiStatus status;
    struct v4l2_subdev_format aFormat;
    int err = ioctl(mMipiHdmi, VIDIOC_SUBDEV_G_FMT, &aFormat);
    if (err < 0) {
        ALOGE("VIDIOC_SUBDEV_G_FMT failed: %s", strerror(errno));
        _hidl_cb(status);
        return Void();
    }
    ALOGD("VIDIOC_SUBDEV_G_FMT: pad: %d, which: %d, width: %d, "
    "height: %d, format: 0x%x, field: %d, color space: %d",
    aFormat.pad,
    aFormat.which,
    aFormat.format.width,
    aFormat.format.height,
    aFormat.format.code,
    aFormat.format.field,
    aFormat.format.colorspace);
    status.width = aFormat.format.width;
    status.height = aFormat.format.height;
    struct v4l2_dv_timings timings;
    err = ioctl(mMipiHdmi, VIDIOC_SUBDEV_QUERY_DV_TIMINGS, &timings);
    if (err < 0) {
        ALOGD("get VIDIOC_SUBDEV_QUERY_DV_TIMINGS failed ,%d(%s)", errno, strerror(errno));
        _hidl_cb(status);
        return Void();
    }
    const struct v4l2_bt_timings *bt =&timings.bt;
    double tot_width, tot_height;
    tot_height = bt->height +
        bt->vfrontporch + bt->vsync + bt->vbackporch +
        bt->il_vfrontporch + bt->il_vsync + bt->il_vbackporch;
    tot_width = bt->width +
        bt->hfrontporch + bt->hsync + bt->hbackporch;
    ALOGD("%s:%dx%d, pixelclock:%lld Hz, %.2f fps", __func__,
    timings.bt.width, timings.bt.height,
    timings.bt.pixelclock,static_cast<double>(bt->pixelclock) /(tot_width * tot_height));
    status.fps = round(static_cast<double>(bt->pixelclock) /(tot_width * tot_height));
    struct v4l2_control control;
    memset(&control, 0, sizeof(struct v4l2_control));
    control.id = V4L2_CID_DV_RX_POWER_PRESENT;
    err = ioctl(mMipiHdmi, VIDIOC_G_CTRL, &control);
    if (err < 0) {
        ALOGE("V4L2_CID_DV_RX_POWER_PRESENT failed ,%d(%s)", errno, strerror(errno));
    }
    ALOGD("VIDIOC_G_CTRL:%d",control.value);
    status.status = control.value;
    _hidl_cb(status);
    return Void();
}

Return<void> Hdmi::getHdmiRxStatus(Hdmi::getHdmiRxStatus_cb _hidl_cb){
    ALOGD("@%s",__FUNCTION__);
    std::unique_lock<std::mutex> lk(mLockStatusCb);
    V1_0::HdmiStatus status;
    if (mStatusCb)
    {
        mStatusCb->getHdmiRxStatus(_hidl_cb);
        lk.unlock();
        return Void();
    }
    _hidl_cb(status);
    lk.unlock();
    return Void();
}
// Methods from ::rockchip::hardware::hdmi::V1_0::IHdmi follow.
Return<void> Hdmi::onStatusChange(uint32_t status) {
    ALOGD("@%s",__FUNCTION__);
    std::unique_lock<std::mutex> lk(mLock);
    if (mCb.get()!=nullptr)
    {
        ALOGD("@%s,status:%d",__FUNCTION__,status);
        if (status)
        {
            mCb->onConnect(mDeviceId);
        }else{
            mCb->onDisconnect(mDeviceId);
        }
    }
    lk.unlock();
    return Void();
}

Return<void> Hdmi::registerListener(const sp<::rockchip::hardware::hdmi::V1_0::IHdmiCallback>& cb) {
    ALOGD("@%s",__FUNCTION__);
    std::unique_lock<std::mutex> lk(mLock);
    mCb = cb;
    lk.unlock();
    return Void();
}

Return<void> Hdmi::unregisterListener(const sp<::rockchip::hardware::hdmi::V1_0::IHdmiCallback>& cb) {
    ALOGD("@%s",__FUNCTION__);
    std::unique_lock<std::mutex> lk(mLock);
    mCb = nullptr;
    lk.unlock();
    return Void();
}

V4L2EventCallBack Hdmi::eventCallback(void* sender,int event_type,struct v4l2_event *event){
    ALOGD("@%s,event_type:%d",__FUNCTION__,event_type);
    std::unique_lock<std::mutex> lk(mLock);
    if (event_type == V4L2_EVENT_CTRL)
    {
        struct v4l2_event_ctrl* ctrl =(struct v4l2_event_ctrl*) &(event->u);
        if (mCb != nullptr)
        {
            if (!ctrl->value)
            {
                mCb->onDisconnect("0");
            }
        }
        ALOGD("V4L2_EVENT_CTRL event %d\n", ctrl->value);
    }else if (event_type == V4L2_EVENT_SOURCE_CHANGE)
    {
        if (sender!=nullptr)
        {
            V4L2DeviceEvent::V4L2EventThread* eventThread = (V4L2DeviceEvent::V4L2EventThread*)sender;
            sp<V4L2DeviceEvent::FormartSize> format = eventThread->getFormat();
            if (format!=nullptr)
            {
                ALOGD("getFormatWeight:%d,getFormatHeight:%d",format->getFormatWeight(),format->getFormatHeight());
                if (mCb != nullptr)
                {
                    mCb->onFormatChange("0",format->getFormatWeight(),format->getFormatHeight());
                    mCb->onConnect("0");
                }
            }
        }
    }
    lk.unlock();
    return 0;
}

Hdmi::Hdmi(){
    ALOGD("@%s.",__FUNCTION__);
    mCb = new HdmiCallback();
    mV4l2Event = new V4L2DeviceEvent();
    mV4l2Event->RegisterEventvCallBack((V4L2EventCallBack)Hdmi::eventCallback);
    findMipiHdmi();
}
Hdmi::~Hdmi(){
    ALOGD("@%s",__FUNCTION__);
    if (mV4l2Event)
        mV4l2Event->closePipe();
    if (mV4l2Event)
        mV4l2Event->closeEventThread();
}
V1_0::IHdmi* HIDL_FETCH_IHdmi(const char* /* name */) {
    ALOGD("@%s",__FUNCTION__);
    return new Hdmi();
}

Return<void> Hdmi::setFrameDecorator(const sp<::rockchip::hardware::hdmi::V1_0::IFrameWarpper>& frameWarpper) {
    ALOGD("@%s",__FUNCTION__);
    std::unique_lock<std::mutex> lk(mLockFrameWarpper);
    mFrameWarpper = frameWarpper;
    lk.unlock();
    return Void();
}

Return<void> Hdmi::decoratorFrame(const ::rockchip::hardware::hdmi::V1_0::FrameInfo& frameInfo, decoratorFrame_cb _hidl_cb) {
    ALOGV("@%s",__FUNCTION__);
    std::unique_lock<std::mutex> lk(mLockFrameWarpper);

    rockchip::hardware::hdmi::V1_0::FrameInfo _frameInfo;
    if (mFrameWarpper.get()!=nullptr)
    {
        V1_0::IFrameWarpper::onFrame_cb _onFrame_cb;
        mFrameWarpper->onFrame(frameInfo,[&]( ::rockchip::hardware::hdmi::V1_0::FrameInfo frameInfo){
            ALOGV("[%s] Receive wrapped frame(%d,%d)",__FUNCTION__,frameInfo.width,frameInfo.height);
            _frameInfo = frameInfo;
        });
        ALOGV("[%s] Receive wrapped frame(%d,%d)",__FUNCTION__,_frameInfo.width,_frameInfo.height);
        _hidl_cb(_frameInfo);
        lk.unlock();
        return Void();
    }
    _hidl_cb(frameInfo);
    lk.unlock();
    return Void();
}

}  // namespace rockchip::hardware::hdmi::implementation
