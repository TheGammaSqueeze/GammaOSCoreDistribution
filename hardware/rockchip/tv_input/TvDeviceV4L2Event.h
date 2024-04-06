/*
 * Copyright (c) 2021 Rockchip Electronics Co., Ltd
 */

#ifndef _TVINPUT_HAL_V4L2DEVICE_H_
#define _TVINPUT_HAL_V4L2DEVICE_H_

#include <memory>
#include <atomic>
#include <sys/ioctl.h>
#include <utils/Errors.h>
#include <utils/Thread.h>
#include <linux/videodev2.h>
#include <linux/v4l2-subdev.h>
#include <poll.h>
#include <string>
#include <sstream>
#include <vector>
#include "common/rk_hdmirx_config.h"

using namespace std;
using android::status_t;
using ::android::sp;
#define CLEAR(x) memset (&(x), 0, sizeof (x))
#define CLEAR_N(x, n) memset (&(x), 0, sizeof(x) * n)


using V4L2EventCallBack =
    add_pointer<void(int event_type)>::type;
//typedef void (*V4L2EventCallBack)(int width, int height,int isHdmiIn);
class V4L2DeviceEvent : public virtual android::RefBase{
public:
    explicit V4L2DeviceEvent();
    virtual ~V4L2DeviceEvent();
    
    virtual int initialize(int fd);
    virtual void closeEventThread();
    virtual status_t setControl(int aControlNum, const int value, const char *name);
    virtual status_t getControl(int aControlNum, int *value);
    virtual status_t queryMenu(v4l2_querymenu &menu);
    virtual status_t queryControl(v4l2_queryctrl &control);

    virtual int subscribeEvent(int event);
    virtual int unsubscribeEvent(int event);
    virtual int dequeueEvent(struct v4l2_event *event);
    virtual void closePipe();
    void RegisterEventvCallBack(V4L2EventCallBack cb) { callback_ = cb; }
    void UnRegisterEventCallBack() { callback_ = nullptr; }

    bool isOpen() { return mFd != -1; }
    int getFd() { return mFd; }
    class FormartSize : public virtual android::RefBase{
        public :
        FormartSize(int weight,int height,bool hdmiIn){
          mFormatWeight = weight;
          mFormatHeight = height;
          mIsHdmiIn = hdmiIn;
        }
        int getFormatWeight(){return mFormatWeight;}
        int getFormatHeight(){return mFormatHeight;}
        bool getIsHdmiIn(){return mIsHdmiIn;}
        void setIsHdmiIn(bool hdmiIn){mIsHdmiIn = hdmiIn;}
        void setFormatWeight(int weight){mFormatWeight = weight;}
        void setFormatHeight(int height){mFormatHeight = height;}
        private:
         int mFormatWeight;
         int mFormatHeight;
         bool mIsHdmiIn;
    };
    class V4L2EventThread : public android::Thread {
        public:
            V4L2EventThread(int fd,V4L2EventCallBack callback);
            ~V4L2EventThread();
            virtual bool v4l2pipe();
            virtual void openDevice();
            virtual void closeDevice();
            virtual bool threadLoop() override;
        private :
            int mVideoFd;
            bool mStopThread = false;
            int pipefd[2] = {-1, -1};
            V4L2EventCallBack mCallback_;
            sp<V4L2DeviceEvent::FormartSize> mCurformat;
    };
protected:
    int mFd;       /*!< file descriptor obtained when device is open */
    sp<V4L2EventThread> mV4L2EventThread;
    V4L2EventCallBack callback_;
};
#endif // _CAMERA3_HAL_V4L2DEVICE_H_
