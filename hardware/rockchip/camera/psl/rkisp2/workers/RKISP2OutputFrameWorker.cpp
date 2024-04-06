/*
 * Copyright (C) 2016-2017 Intel Corporation.
 * Copyright (c) 2017, Fuzhou Rockchip Electronics Co., Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "RKISP2OutputFrameWorker"

#include "LogHelper.h"
#include "PerformanceTraces.h"
#include "RKISP2OutputFrameWorker.h"
#include "ColorConverter.h"
#include "NodeTypes.h"
#include <libyuv.h>
#include <sys/mman.h>
#include "FormatUtils.h"

namespace android {
namespace camera2 {
namespace rkisp2 {

RKISP2OutputFrameWorker::RKISP2OutputFrameWorker(int cameraId, std::string name,
                NodeTypes nodeName, size_t pipelineDepth) :
                RKISP2FrameWorker(nullptr, cameraId, pipelineDepth, name),
                mOutputBuffer(nullptr),
                mStream(NULL),
                mNeedPostProcess(false),
                mNodeName(nodeName),
                mLastPipelineDepth(pipelineDepth),
                mPostPipeline(new RKISP2PostProcessPipeline(this, this, cameraId)),
                mPostProcItemsPool("PostBufPool")
{
    LOGI("@%s, name:%s instance:%p, cameraId:%d", __FUNCTION__, name.data(), this, cameraId);
//    mPostProcItemsPool.init(mPipelineDepth, PostProcBuffer::reset);
//    for (size_t i = 0; i < mPipelineDepth; i++)
//    {
//        std::shared_ptr<PostProcBuffer> buffer= nullptr;
//        mPostProcItemsPool.acquireItem(buffer);
//        if (buffer.get() == nullptr) {
//            LOGE("No memory, fix me!");
//        }
//        buffer->index = i;
//    }
}

RKISP2OutputFrameWorker::~RKISP2OutputFrameWorker()
{
    LOGI("@%s, name:%s instance:%p, cameraId:%d", __FUNCTION__, mName.data(), this, mCameraId);
    mPostPipeline.reset();
}

status_t
RKISP2OutputFrameWorker::flushWorker()
{
    // this func will be called in every config stream time
    // 1.stream related variable should be destruct here.
    // 2.PostPipeline processing is base on streams, so it must
    // flush and stop here
    LOGI("@%s enter, %s, mIsStarted:%d", __FUNCTION__, mName.c_str(), mIsStarted);
    if (mIsStarted == false)
        return OK;
    RKISP2FrameWorker::flushWorker();
    mPostPipeline->flush();
    mPostPipeline->stop();
    mPostWorkingBufs.clear();
    clearListeners();

    return OK;
}

status_t
RKISP2OutputFrameWorker::stopWorker()
{
    LOGI("@%s enter, %s, mIsStarted:%d", __FUNCTION__, mName.c_str(), mIsStarted);
    if (mIsStarted == false)
        return OK;
    RKISP2FrameWorker::stopWorker();
    mOutputBuffers.clear();

    if (mOutputForListener.get() && mOutputForListener->isLocked()) {
        mOutputForListener->unlock();
    }
    mOutputForListener = nullptr;

    return OK;
}

status_t
RKISP2OutputFrameWorker::notifyNewFrame(const std::shared_ptr<PostProcBuffer>& buf,
                                  const std::shared_ptr<RKISP2ProcUnitSettings>& settings,
                                  int err)
{
    buf->cambuf->captureDone(buf->cambuf, true);
    return OK;
}

void RKISP2OutputFrameWorker::addListener(camera3_stream_t* stream)
{
    if (stream != nullptr) {
        LOGI("@%s, %s: stream %p has listener %p (%dx%d, fmt %s)", __FUNCTION__,
             mName.c_str(), mStream, stream, stream->width, stream->height,
             METAID2STR(android_scaler_availableFormats_values, stream->format));
        mListeners.push_back(stream);
    }
}

void RKISP2OutputFrameWorker::attachStream(camera3_stream_t* stream)
{
    if (stream != nullptr) {
        LOGI("@%s, %s attach to stream(%p): %dx%d, type %d, fmt %s", __FUNCTION__,
             mName.c_str(), stream, stream->width, stream->height, stream->stream_type,
             METAID2STR(android_scaler_availableFormats_values, stream->format));
        mStream = stream;
    }
}

void RKISP2OutputFrameWorker::clearListeners()
{
    mListeners.clear();
    cleanListener();
}

status_t RKISP2OutputFrameWorker::configPostPipeLine()
{
    FrameInfo sourceFmt;
    sourceFmt.width = mFormat.width();
    sourceFmt.height = mFormat.height();
    sourceFmt.size = mFormat.sizeimage();
    sourceFmt.format = mFormat.pixelformat();
    sourceFmt.stride = sourceFmt.width;
    std::vector<camera3_stream_t*> streams = mListeners;
    /* put the main stream to first */
    streams.insert(streams.begin(), mStream);
    mPostWorkingBufs.resize(mPipelineDepth);
    mPostPipeline->prepare(sourceFmt, streams, mNeedPostProcess, mPipelineDepth);
    mPostPipeline->start();
    return OK;
}

status_t RKISP2OutputFrameWorker::configure(bool configChanged)
{
    HAL_TRACE_CALL(CAM_GLBL_DBG_HIGH);
    status_t ret = OK;

    LOGI("@%s %s: configChanged:%d", __FUNCTION__, mName.c_str(), configChanged);
    if(configChanged) {
        ret = mNode->getFormat(mFormat);
        if (ret != OK)
            return ret;

        LOGI("@%s %s format %s, isRawFormat(%s), size %d, %dx%d", __FUNCTION__, mName.c_str(),
             v4l2Fmt2Str(mFormat.pixelformat()),
             graphconfig::utils::isRawFormat(mFormat.pixelformat()) ? "Yes" : "No",
             mFormat.sizeimage(), mFormat.width(), mFormat.height());

        ret = configPostPipeLine();
        if (ret != OK)
            return ret;

        mIndex = 0;
        mOutputBuffers.clear();
        mOutputBuffers.resize(mPipelineDepth);

        ret = setWorkerDeviceBuffers(
                                     mNeedPostProcess ? V4L2_MEMORY_MMAP : getDefaultMemoryType(mNodeName), mPostPipeline->mIsNeedcached);
        CheckError((ret != OK), ret, "@%s set worker device buffers failed.",
                   __FUNCTION__);

        // Allocate internal buffer.
        if (mNeedPostProcess) {
            ret = allocateWorkerBuffers();
            CheckError((ret != OK), ret, "@%s failed to allocate internal buffer.",
                       __FUNCTION__);
        }

    } else {
        ret = configPostPipeLine();
        if (!ret)
            return ret;
    }

    return OK;
}

status_t RKISP2OutputFrameWorker::prepareRun(std::shared_ptr<DeviceMessage> msg)
{
    HAL_TRACE_CALL(CAM_GLBL_DBG_HIGH);
    status_t status = NO_ERROR;
    std::shared_ptr<CameraBuffer> buffer;

    mPollMe = false;

    if (!mStream)
        return NO_ERROR;

    if (mIsStarted == false)
        return OK;

    mMsg = msg;

    Camera3Request* request = mMsg->cbMetadataMsg.request;
    request->setSequenceId(-1);

    LOGD("%s %s  request->getId():%d.",__FUNCTION__,mName.c_str(),request->getId());

    buffer = findBuffer(request, mStream);
    if (buffer.get()) {
        // Work for mStream
        status = prepareBuffer(buffer);
        if (status != NO_ERROR) {
            LOGE("prepare buffer error!");
            goto exit;
        }

        // If output format is something else than
        // NV21 or Android flexible YCbCr 4:2:0, return
        if (buffer->format() != HAL_PIXEL_FORMAT_YCrCb_420_SP &&
                buffer->format() != HAL_PIXEL_FORMAT_YCbCr_420_888 &&
                buffer->format() != HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED &&
            buffer->format() != HAL_PIXEL_FORMAT_BLOB)  {
            LOGE("Bad format %d", buffer->format());
            status = BAD_TYPE;
            goto exit;
        }

        mPollMe = true;
    } else if (checkListenerBuffer(request)) {
        // Work for listeners
        LOGD("%s: stream %p works for listener only in req %d",
             __FUNCTION__, mStream, request->getId());
        mPollMe = true;
    // if dump raw, need to poll raw video node
    } else if ((mName == "RawWork") && mStream) {
        LOGI("@%s : Dump raw enabled", __FUNCTION__);
        mPollMe = true;
    } else if ((mName == "MainWork") && mStream) {
        LOGI("@%s : stream %p  MainWork for BLOB!",
            __FUNCTION__, mStream);
        mPollMe = true;
    } else if ((mName == "SelfWork") && mStream) {
        LOGI("@%s : stream %p  SelfWork for BLOB!",
            __FUNCTION__, mStream);
        mPollMe = true;
    } else {
        LOGD("No work for this worker mStream: %p", mStream);
        mPollMe = false;
        return NO_ERROR;
    }

    /*
     * store the buffer in a map where the key is the terminal UID
     */
    if (!mNeedPostProcess) {
        // Use stream buffer for zero-copy
        unsigned long userptr;
        /*
         * If there exist linsteners, we force to use main stream buffer
         * as driver buffer directly, so when we handle the request that
         * contain only the listener's buffer, we should allocate extra
         * buffers.
         */
        if (buffer.get() == nullptr) {
            buffer = getOutputBufferForListener();
            CheckError((buffer.get() == nullptr), UNKNOWN_ERROR,
                       "failed to allocate listener buffer");
        }
        switch (mNode->getMemoryType()) {
        case V4L2_MEMORY_USERPTR:
            userptr = reinterpret_cast<unsigned long>(buffer->data());
            mBuffers[mIndex].userptr(userptr);
            break;
        case V4L2_MEMORY_DMABUF:
            mBuffers[mIndex].setFd(buffer->dmaBufFd(), 0);
            break;
        case V4L2_MEMORY_MMAP:
            break;
        default:
            LOGE("%s unsupported memory type.", __FUNCTION__);
            status = BAD_VALUE;
            goto exit;
        }
    } else {
    }
    LOGD("%s: %s, requestId(%d), index(%d)", __FUNCTION__, mName.c_str(), request->getId(), mIndex);

exit:
    if (status < 0)
        returnBuffers(true);

    return status < 0 ? status : OK;
}

status_t RKISP2OutputFrameWorker::skipBadFrames(int skipFrames)
{
    HAL_TRACE_CALL(CAM_GLBL_DBG_HIGH);
    status_t ret = NO_ERROR;
    V4L2BufferInfo outBuf;
    int index;
    fd_set fds;
    struct timeval tv;
    int res;

    ALOGI("@%s enter, %s, skipFrames: %d. mIsStarted:%d", __FUNCTION__, mName.c_str(), skipFrames, mIsStarted);
    if (mNode->getBufsInDeviceCount() == 0) {
        LOGE("@%s: devices: %s, mBuffersInDevice is 0, can't skip!", __FUNCTION__, mName.c_str());
        return NO_ERROR;
    }

    FD_ZERO(&fds);
    FD_SET(mNode->getFd(), &fds);

    /* Timeout. */
    tv.tv_sec = 1;
    tv.tv_usec = 0;

    for (int i = 0; i < skipFrames; i++) {
        res = select(mNode->getFd() + 1, &fds, NULL, NULL, &tv);
        if (res <= 0) {
            LOGE("@%s(%d) error select or select time out!!",__FUNCTION__,__LINE__);
            return 0;
        }
        index = mNode->grabFrame(&outBuf);
        ALOGI("device: %s, grabFrame buf index(%d)!", mNode->name(), index);
        ret = mNode->putFrame(outBuf.vbuffer);
        if (ret != OK) {
            LOGE("Unable to putFrame from device: %s ret: %d", mNode->name(), ret);
            return ret;
        }
    }

    return ret;
}

status_t RKISP2OutputFrameWorker::bufferDone(int64_t reqId){
    status_t status = NO_ERROR;
    LOGD("qiujian %s(%d) %s  reqId:%d",__FUNCTION__,__LINE__, mName.data(),reqId);
    std::lock_guard<std::mutex> l(mIndexMutex);
    if(mRequestIndexMap.count(reqId)){
        int index = mRequestIndexMap[reqId];
        if(index!= -1){
            mNode->putFrame(mBuffers[index]);
            LOGD("qiujian %s(%d) putFrame  reqId:%d",__FUNCTION__,__LINE__, reqId);
        }
        mRequestIndexMap.erase(reqId);
    }
    return status;
}

status_t RKISP2OutputFrameWorker::run()
{
    status_t status = NO_ERROR;
    int index = 0;
    Camera3Request* request = mMsg->cbMetadataMsg.request;
    uint32_t cacheflags = V4L2_BUF_FLAG_NO_CACHE_INVALIDATE |
                         V4L2_BUF_FLAG_NO_CACHE_CLEAN;
    HAL_TRACE_CALL(CAM_GLBL_DBG_HIGH);

    V4L2BufferInfo outBuf;
    if (mPostPipeline->mIsNeedcached)
        outBuf.cache_flags = 0;
    else
        outBuf.cache_flags = cacheflags;

    if (!mDevError) {
        status = mNode->grabFrame(&outBuf);

        // Update request sequence if needed
        int sequence = outBuf.vbuffer.sequence();
        if (request->sequenceId() < sequence)
            request->setSequenceId(sequence);

        index =  outBuf.vbuffer.index();


        mPostWorkingBuf  =  std::make_shared<PostProcBuffer> ();
        std::shared_ptr<CameraBuffer> buffer = findBuffer(request, mStream);
        mOutputBuffer = buffer;
        {
            std::lock_guard<std::mutex> l(mIndexMutex);
            mRequestIndexMap[request->getId()] = index;
        }
        LOGD("%s:@%s(%d) reqId:%d dmaBufFd:%d",mName.data(),__FUNCTION__,__LINE__,request->getId(),mCameraBuffers[index]->dmaBufFd());

        mPostWorkingBuf->request = request;
        mPostWorkingBuf->cambuf =  mCameraBuffers[index];
        std::string s(mNode->name());
        // node name is "/dev/videox", substr is videox
        std::string substr = s.substr(5,10);
        // CAMERA_DUMP_RAW : raw format buffers
        // CAMERA_DUMP_ISP_PURE : the buffers not processed
        // after dequing from driver
        if (graphconfig::utils::isRawFormat(mFormat.pixelformat()))
            mPostWorkingBuf->cambuf->dumpImage(CAMERA_DUMP_RAW, "RAW");
        else
            mPostWorkingBuf->cambuf->dumpImage(CAMERA_DUMP_ISP_PURE, substr.c_str());
    } else {
        LOGE("%s:%d device error!", __FUNCTION__, __LINE__);
        /* get the prepared but undequed buffers */
        for (int i = 0; i < mPipelineDepth; i++)
            if (mOutputBuffers[(i + mIndex) % mPipelineDepth]) {
                index = (i + mIndex) % mPipelineDepth;
                break;
            }
        status = UNKNOWN_ERROR;
    }

    ICaptureEventListener::CaptureMessage outMsg;
    outMsg.data.event.reqId = request->getId();
    outMsg.id = ICaptureEventListener::CAPTURE_MESSAGE_ID_EVENT;
    outMsg.data.event.type = ICaptureEventListener::CAPTURE_EVENT_SHUTTER;
    outMsg.data.event.timestamp = outBuf.vbuffer.timestamp();
    outMsg.data.event.sequence = outBuf.vbuffer.sequence();
    notifyListeners(&outMsg);

    LOGD("%s: %s, frame_id(%d), requestId(%d), index(%d)", __FUNCTION__, mName.c_str(), outBuf.vbuffer.sequence(), request->getId(), index);

    if (request->getNumberOutputBufs() >=2) {
        int64_t ts = (int64_t)outMsg.data.event.timestamp.tv_sec * 1000000000; // seconds to nanoseconds
        ts += (int64_t)outMsg.data.event.timestamp.tv_usec * 1000; // microseconds to nanoseconds

        LOGD_CAP("%s:%d, reqId: %d, tv_ns(%lld), done!",
             __FUNCTION__, __LINE__, request->getId(), ts);
    }

    if (status < 0)
        returnBuffers(true);

    return (status < 0) ? status : OK;
}

status_t RKISP2OutputFrameWorker::postRun()
{
    HAL_TRACE_CALL(CAM_GLBL_DBG_HIGH);

    status_t status = OK;
    CameraStream *stream;
    Camera3Request* request = nullptr;
    std::vector<std::shared_ptr<PostProcBuffer>> outBufs;
    std::shared_ptr<PostProcBuffer> postOutBuf;
    std::shared_ptr<PostProcBuffer> tempBuf = std::make_shared<PostProcBuffer> ();
    int stream_type;

    if (mDevError) {
        LOGE("%s:%d device error!", __FUNCTION__, __LINE__);
        status = UNKNOWN_ERROR;
        goto exit;
    }

    if (mMsg == nullptr) {
        LOGE("Message null - Fix the bug");
        status = UNKNOWN_ERROR;
        goto exit;
    }

    request = mMsg->cbMetadataMsg.request;
    if (request == nullptr) {
        LOGE("No request provided for captureDone");
        status = UNKNOWN_ERROR;
        goto exit;
    }

    // Handle for listeners at first
    for (size_t i = 0; i < mListeners.size(); i++) {
        camera3_stream_t* listener = mListeners[i];
        std::shared_ptr<CameraBuffer> listenerBuf = findBuffer(request, listener);
        if (listenerBuf.get() == nullptr) {
            continue;
        }

        if (NO_ERROR != prepareBuffer(listenerBuf)) {
            LOGE("prepare listener buffer error!");
            listenerBuf->captureDone(listenerBuf);
            status = UNKNOWN_ERROR;
            continue;
        }
        postOutBuf = std::make_shared<PostProcBuffer> ();
        postOutBuf->cambuf = listenerBuf;
        postOutBuf->request = request;
        outBufs.push_back(postOutBuf);
        postOutBuf = nullptr;
        if(listenerBuf->getOwner()->getStreamType() ==  STREAM_CAPTURE) {
            listenerBuf->captureDone(listenerBuf, false);
            LOGD("@%s : captureDone in advance for req %d", __FUNCTION__, request->getId());
        }
    }
    if (status != OK)
        goto exit;

    // All done
    if (mOutputBuffer == nullptr) {
        std::shared_ptr<PostProcBuffer> inPostBuf = std::make_shared<PostProcBuffer> ();
        inPostBuf->cambuf = mPostWorkingBuf->cambuf;
        inPostBuf->request = mPostWorkingBuf->request;
        mPostPipeline->processFrame(inPostBuf, outBufs, mMsg->pMsg.processingSettings);
        LOGI("@%s %d: Only listener include a buffer", __FUNCTION__, __LINE__);
        goto exit;
    }
    postOutBuf = std::make_shared<PostProcBuffer> ();
    postOutBuf->cambuf = mOutputBuffer;
    postOutBuf->request = request;
    outBufs.push_back(postOutBuf);
    postOutBuf = nullptr;

    // can't pass mPostWorkingBuf to processFrame becasuse the life of
    // mPostWorkingBuf should not be managered by PostProcPine. if pass
    // mPostWorkingBuf directly to processFrame, acquire postproc buffer in
    // @prepareRun maybe failed dute to the shared_ptr of mPostWorkingBuf can be
    // held by PostProcPipeline
    tempBuf->cambuf = mPostWorkingBuf->cambuf;
    tempBuf->request = mPostWorkingBuf->request;

    mPostPipeline->processFrame(tempBuf, outBufs, mMsg->pMsg.processingSettings);
    stream = mOutputBuffer->getOwner();

    // call captureDone for the stream of the buffer
    if(stream->getStreamType() ==  STREAM_CAPTURE) {
        mOutputBuffer->captureDone(mOutputBuffer, false);
        LOGD("@%s : captureDone in advance for req %d", __FUNCTION__, request->getId());
    }

exit:
    /* Prevent from using old data */
    mMsg = nullptr;
    mOutputBuffer = nullptr;
    mPostWorkingBuf = nullptr;

    if (status != OK)
        returnBuffers(false);

    return status;
}

void RKISP2OutputFrameWorker::returnBuffers(bool returnListenerBuffers)
{
    if (!mMsg || !mMsg->cbMetadataMsg.request)
        return;

    Camera3Request* request = mMsg->cbMetadataMsg.request;
    std::shared_ptr<CameraBuffer> buffer;

    buffer = findBuffer(request, mStream);
    if (buffer.get() && buffer->isRegistered())
        buffer->captureDone(buffer);

    if (!returnListenerBuffers)
        return;

    for (size_t i = 0; i < mListeners.size(); i++) {
        camera3_stream_t* listener = mListeners[i];
        buffer = findBuffer(request, listener);
        if (buffer.get() == nullptr || !buffer->isRegistered())
            continue;
        buffer->captureDone(buffer);
    }
}

status_t
RKISP2OutputFrameWorker::prepareBuffer(std::shared_ptr<CameraBuffer>& buffer)
{
    CheckError((buffer.get() == nullptr), UNKNOWN_ERROR, "null buffer!");

    status_t status = NO_ERROR;
    if (!buffer->isLocked()) {
        status = buffer->lock();
        if (CC_UNLIKELY(status != NO_ERROR)) {
            LOGE("Could not lock the buffer error %d", status);
            return UNKNOWN_ERROR;
        }
    }
    // do waitOnAcquireFence in postpipeline last process unit
    /* status = buffer->waitOnAcquireFence(); */
    /* if (CC_UNLIKELY(status != NO_ERROR)) { */
    /*     LOGW("Wait on fence for buffer %p timed out", buffer.get()); */
    /* } */
    return status;
}

std::shared_ptr<CameraBuffer>
RKISP2OutputFrameWorker::findBuffer(Camera3Request* request,
                              camera3_stream_t* stream)
{
    CheckError((request == nullptr || stream == nullptr), nullptr,
                "null request/stream!");

    CameraStream *s = nullptr;
    std::shared_ptr<CameraBuffer> buffer = nullptr;
    const std::vector<camera3_stream_buffer>* outBufs =
                                        request->getOutputBuffers();
    // don't deal with reprocess request in RKISP2OutputFrameWorker,
    // and we will process the prequest in inputFrameWorker insteadly.
    if (request->getInputBuffers()->size() > 0)
        return buffer;

    for (camera3_stream_buffer outputBuffer : *outBufs) {
        s = reinterpret_cast<CameraStream *>(outputBuffer.stream->priv);
        if (s->getStream() == stream) {
            buffer = request->findBuffer(s, false);
            if (CC_UNLIKELY(buffer == nullptr)) {
                LOGW("buffer not found for stream");
            }
            break;
        }
    }

    if (buffer.get() == nullptr) {
        LOGI("No buffer for stream %p in req %d", stream, request->getId());
    }
    return buffer;
}

bool RKISP2OutputFrameWorker::checkListenerBuffer(Camera3Request* request)
{
    bool required = false;
    for (auto* s : mListeners) {
        std::shared_ptr<CameraBuffer> listenerBuf = findBuffer(request, s);
        if (listenerBuf.get()) {
            required = true;
            break;
        }
    }

    return required;
}

std::shared_ptr<CameraBuffer>
RKISP2OutputFrameWorker::getOutputBufferForListener()
{
    // mOutputForListener buffer infor is same with mOutputBuffer,
    // and only allocated once
    if (mOutputForListener.get() == nullptr) {
        // Allocate buffer for listeners
        if (mNode->getMemoryType() == V4L2_MEMORY_DMABUF) {
            mOutputForListener = MemoryUtils::allocateHandleBuffer(
                    mFormat.width(),
                    mFormat.height(),
                    HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED,
                    GRALLOC_USAGE_SW_READ_OFTEN | GRALLOC_USAGE_HW_CAMERA_WRITE);
        } else if (mNode->getMemoryType() == V4L2_MEMORY_MMAP) {
            mOutputForListener = std::make_shared<CameraBuffer>(
                    mFormat.width(),
                    mFormat.height(),
                    mFormat.bytesperline(),
                    mNode->getFd(), -1, // dmabuf fd is not required.
                    mBuffers[0].length(),
                    mFormat.pixelformat(),
                    mBuffers[0].offset(), PROT_READ | PROT_WRITE, MAP_SHARED);
        } else if (mNode->getMemoryType() == V4L2_MEMORY_USERPTR) {
            mOutputForListener = MemoryUtils::allocateHeapBuffer(
                    mFormat.width(),
                    mFormat.height(),
                    mFormat.bytesperline(),
                    mFormat.pixelformat(),
                    mCameraId,
                    mBuffers[0].length());
        } else {
            LOGE("bad type for stream buffer %d", mNode->getMemoryType());
            return nullptr;
        }
        CheckError((mOutputForListener.get() == nullptr), nullptr,
                   "Can't allocate buffer for listeners!");
    }

    if (!mOutputForListener->isLocked()) {
        mOutputForListener->lock();
    }

    LOGD("%s, get output buffer for Listeners", __FUNCTION__);
    return mOutputForListener;
}

} /* namespace rkisp2 */
} /* namespace camera2 */
} /* namespace android */
