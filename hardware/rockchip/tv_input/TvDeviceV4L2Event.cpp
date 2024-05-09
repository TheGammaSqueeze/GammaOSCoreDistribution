/*
 * Copyright (c) 2021 Rockchip Electronics Co., Ltd
 */
#define LOG_TAG "V4L2DeviceEvent"

#include <cutils/log.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <poll.h>
#include <unistd.h>

#include "TvDeviceV4L2Event.h"
using android::UNKNOWN_ERROR;
using android::NO_ERROR;
////////////////////////////////////////////////////////////////////
//                          PUBLIC METHODS
////////////////////////////////////////////////////////////////////
V4L2DeviceEvent::V4L2DeviceEvent()
{
}

V4L2DeviceEvent::~V4L2DeviceEvent()
{
    ALOGI("@%s", __FUNCTION__);
    if (mFd != -1) {
        ALOGW("Destroying a device object not closed, closing first");
    }
}
int V4L2DeviceEvent::initialize(int fd){
    mFd = fd;
    subscribeEvent(V4L2_EVENT_SOURCE_CHANGE);
    subscribeEvent(V4L2_EVENT_CTRL);
    subscribeEvent(RK_HDMIRX_V4L2_EVENT_SIGNAL_LOST);
    mV4L2EventThread = new V4L2EventThread(mFd,callback_);
    mV4L2EventThread->v4l2pipe();
    mV4L2EventThread->run("Tif_Ev", android::PRIORITY_DISPLAY);
    return 0;
}
void V4L2DeviceEvent::closeEventThread() {
    ALOGW("@%s start", __FUNCTION__);
    if (mV4L2EventThread) {
        mV4L2EventThread->requestExit();
        mV4L2EventThread->join();
        mV4L2EventThread.clear();
    }
    ALOGW("@%s end", __FUNCTION__);
}

void V4L2DeviceEvent::closePipe() {
    ALOGW("@%s start", __FUNCTION__);
    if (mV4L2EventThread) {
        mV4L2EventThread->closeDevice();
    }
    ALOGW("@%s end", __FUNCTION__);
}

int V4L2DeviceEvent::subscribeEvent(int event)
{
    ALOGI("@%s", __FUNCTION__);
    int ret(0);
    struct v4l2_event_subscription sub;

    if (mFd == -1) {
        ALOGW("Device %d already closed. cannot subscribe.",mFd);
        return -1;
    }

    CLEAR(sub);
    sub.type = event;
    if(event == V4L2_EVENT_CTRL)sub.id = V4L2_CID_DV_RX_POWER_PRESENT;
    ret = ioctl(mFd, VIDIOC_SUBSCRIBE_EVENT, &sub);
    if (ret < 0) {
        ALOGE("error subscribing event %x: %s", event, strerror(errno));
        return ret;
    }

    return ret;
}

int V4L2DeviceEvent::unsubscribeEvent(int event)
{
    ALOGI("@%s", __FUNCTION__);
    int ret(0);
    struct v4l2_event_subscription sub;

    if (mFd == -1) {
        ALOGW("Device %d closed. cannot unsubscribe.", mFd);
        return -1;
    }

    CLEAR(sub);
    sub.type = event;

    ret = ioctl(mFd, VIDIOC_UNSUBSCRIBE_EVENT, &sub);
    if (ret < 0) {
        ALOGE("error unsubscribing event %x :%s",event,strerror(errno));
        return ret;
    }

    return ret;
}

int V4L2DeviceEvent::dequeueEvent(struct v4l2_event *event)
{
    ALOGD("@%s", __FUNCTION__);
    int ret(0);

    if (mFd == -1) {
        ALOGW("Device %d closed. cannot dequeue event.", mFd);
        return -1;
    }

    ret = ioctl(mFd, VIDIOC_DQEVENT, event);
    if (ret < 0) {
        ALOGE("error dequeuing event");
        return ret;
    }
    return ret;
}

status_t V4L2DeviceEvent::setControl(int aControlNum, const int value, const char *name)
{
    ALOGD("@%s", __FUNCTION__);

    struct v4l2_control control;
    struct v4l2_ext_controls controls;
    struct v4l2_ext_control ext_control;

    CLEAR(control);
    CLEAR(controls);
    CLEAR(ext_control);

    ALOGD("setting attribute [%s] to %d", name, value);

    if (mFd == -1) {
        ALOGE("%s: Invalid device state (CLOSED)", __FUNCTION__);
        return UNKNOWN_ERROR;
    }

    control.id = aControlNum;
    control.value = value;
    controls.ctrl_class = V4L2_CTRL_ID2CLASS(control.id);
    controls.count = 1;
    controls.controls = &ext_control;
    ext_control.id = aControlNum;
    ext_control.value = value;

    if (ioctl(mFd, VIDIOC_S_EXT_CTRLS, &controls) == 0)
        return NO_ERROR;
    if (ioctl(mFd, VIDIOC_S_CTRL, &control) == 0)
        return NO_ERROR;

    ALOGE("Failed to set value %d for control %s (%d) on device , %s",
        value, name, aControlNum, strerror(errno));

    return UNKNOWN_ERROR;
}

status_t V4L2DeviceEvent::getControl(int aControlNum, int *value)
{
    ALOGD("@%s", __FUNCTION__);

    struct v4l2_control control;
    struct v4l2_ext_controls controls;
    struct v4l2_ext_control ext_control;

    CLEAR(control);
    CLEAR(controls);
    CLEAR(ext_control);

    if (mFd == -1) {
        ALOGE("%s: Invalid state device (CLOSED)", __FUNCTION__);
        return UNKNOWN_ERROR;
    }

    control.id = aControlNum;
    controls.ctrl_class = V4L2_CTRL_ID2CLASS(control.id);
    controls.count = 1;
    controls.controls = &ext_control;
    ext_control.id = aControlNum;

    if (ioctl(mFd, VIDIOC_G_EXT_CTRLS, &controls) == 0) {
       *value = ext_control.value;
       return NO_ERROR;
    }

    if (ioctl(mFd, VIDIOC_G_CTRL, &control) == 0) {
       *value = control.value;
       return NO_ERROR;
    }

    ALOGE("Failed to get value for control (%d) on device, %s",
            aControlNum,  strerror(errno));
    return UNKNOWN_ERROR;
}

status_t V4L2DeviceEvent::queryMenu(v4l2_querymenu &menu)
{
    ALOGD("@%s", __FUNCTION__);

    if (mFd == -1) {
        ALOGE("%s: Invalid state device (CLOSED)", __FUNCTION__);
        return UNKNOWN_ERROR;
    }

    if (ioctl(mFd, VIDIOC_QUERYMENU, &menu) == 0) {
       return NO_ERROR;
    }

    ALOGE("Failed to get values for query menu (%d) on device , %s",
            menu.id, strerror(errno));
    return UNKNOWN_ERROR;
}

status_t V4L2DeviceEvent::queryControl(v4l2_queryctrl &control)
{
    ALOGD("@%s", __FUNCTION__);

    if (mFd == -1) {
        ALOGE("%s: Invalid state device (CLOSED)", __FUNCTION__);
        return UNKNOWN_ERROR;
    }

    if (ioctl(mFd, VIDIOC_QUERYCTRL, &control) == 0) {
       return NO_ERROR;
    }
    ALOGE("Failed to get values for query control (%d) on device, %s",
            control.id,  strerror(errno));
    return UNKNOWN_ERROR;
}
V4L2DeviceEvent::V4L2EventThread::V4L2EventThread(int fd,V4L2EventCallBack callback){
     mVideoFd = fd;
     mCallback_ = callback;
     mCurformat = new V4L2DeviceEvent::FormartSize(0,0,0);
     mStopThread = false;
}

V4L2DeviceEvent::V4L2EventThread::~V4L2EventThread() {
    ALOGW("@%s", __FUNCTION__);
    //closeDevice();
}
bool V4L2DeviceEvent::V4L2EventThread::v4l2pipe() {
    ALOGI("@%s", __FUNCTION__);
    if (pipe(pipefd) < 0) {
	ALOGE("pipe failed: %s\n", strerror(errno));
	return false;
    }
    return true;
}
void V4L2DeviceEvent::V4L2EventThread::openDevice()
{
    char video_name[64];
    memset(video_name, 0, sizeof(video_name));
    strcat(video_name, "/dev/v4l-subdev2");
}
/*int V4L2DeviceEvent::V4L2EventThread::getHdmiIn(bool enforce){
    if(enforce && mCurformat->getIsHdmiIn()) return mCurformat->getIsHdmiIn();
    struct v4l2_control control;
    memset(&control, 0, sizeof(struct v4l2_control));
    control.id = V4L2_CID_DV_RX_POWER_PRESENT;
    int err = ioctl(mVideoFd, VIDIOC_G_CTRL, &control);
    if (err < 0) {
        ALOGV("Set POWER_PRESENT failed ,%d(%s)", errno, strerror(errno));
        return UNKNOWN_ERROR;
    }
    unsigned int noSignalAndSync = 0;
    ioctl(mVideoFd, VIDIOC_G_INPUT, &noSignalAndSync);
    ALOGI("noSignalAndSync ? %s",noSignalAndSync?"YES":"NO");
    mCurformat->setIsHdmiIn(control.value && !noSignalAndSync);
    ALOGD("======================= ,%d",mCurformat->getIsHdmiIn());
    return mCurformat->getIsHdmiIn();
}
int V4L2DeviceEvent::V4L2EventThread::getFormat(){
    struct v4l2_dv_timings dv_timings;
    memset(&dv_timings, 0 ,sizeof(struct v4l2_dv_timings));
    int err = ioctl(mVideoFd, VIDIOC_SUBDEV_QUERY_DV_TIMINGS, &dv_timings);
    if (err < 0) {
        ALOGD("Set VIDIOC_SUBDEV_QUERY_DV_TIMINGS failed ,%d(%s)", errno, strerror(errno));
	return UNKNOWN_ERROR;
    }
    sp<V4L2DeviceEvent::FormartSize> formatSize = new V4L2DeviceEvent::FormartSize(dv_timings.bt.width,dv_timings.bt.height,true);
    //ALOGD("FormatWeight=%d,FormatHeigh=%d,%d",formatSize->getFormatWeight(),formatSize->getFormatHeight(),formatSize->getIsHdmiIn());
    if(mCurformat->getFormatWeight() != formatSize->getFormatWeight() 
            && mCurformat->getFormatHeight() != formatSize->getFormatHeight()){
        getHdmiIn(false);
        if(mCallback_ != NULL)
          mCallback_(formatSize->getFormatWeight(),formatSize->getFormatHeight(),mCurformat->getFormat(),mCurformat->getIsHdmiIn());
        mCurformat->setFormatWeight(formatSize->getFormatWeight());
        mCurformat->setFormatHeight(formatSize->getFormatHeight());
    }
    return NO_ERROR;
}*/
void V4L2DeviceEvent::V4L2EventThread::closeDevice()
{
    ALOGI("close device");
    if (write(pipefd[1], "q", 1) != 1) {}
    close(pipefd[0]);
    close(pipefd[1]);
    mStopThread = true;
}
bool V4L2DeviceEvent::V4L2EventThread::threadLoop() {
    ALOGV("@%s", __FUNCTION__);
    struct pollfd fds[2];
    //int retry = 3;
    //fds.events = POLLIN | POLLRDNORM | POLLOUT | POLLWRNORM | POLLRDBAND | POLLPRI;
    fds[0].fd = pipefd[0];
    fds[0].events = POLLIN;

    fds[1].fd = mVideoFd;
    fds[1].events = POLLPRI;
    struct v4l2_event ev;
    CLEAR(ev);
    if (poll(fds, 2, 5000) < 0) {
	ALOGD("%d: poll failed: %s\n", mVideoFd, strerror(errno));
	return false;
    }
    if (fds[0].revents & POLLIN) {
	ALOGD("%d: quit message received\n", mVideoFd);
	return false;
    }
    if (fds[1].revents & POLLPRI) {
	if (ioctl(fds[1].fd, VIDIOC_DQEVENT, &ev) == 0) {
		switch (ev.type) {
		case V4L2_EVENT_SOURCE_CHANGE:
			ALOGD("%d: V4L2_EVENT_SOURCE_CHANGE event\n", mVideoFd);
			/*retry = 3;
			while(retry-- >= 0){
			  if(!getFormat()) break;
			  else usleep(100*1000);
			}*/
			break;
		case V4L2_EVENT_CTRL:
			ALOGD("%d:  V4L2_EVENT_CTRL event \n", mVideoFd );
		        //getHdmiIn(false);
			break;
		default:
			ALOGD("%d: unknown event\n", mVideoFd);
			break;
		}
		if(mCallback_ != NULL)
                  mCallback_(ev.type);
	} else {
		ALOGD("%d: VIDIOC_DQEVENT failed: %s\n",mVideoFd, strerror(errno));
	}
    }
    if (mStopThread) {
        return false;
    }
    return true;
}
////////////////////////////////////////////////////////////////////
//                          PRIVATE METHODS
////////////////////////////////////////////////////////////////////

