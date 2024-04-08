/*
 * Copyright (C) 2020 Rockchip Electronics Co. LTD
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

#ifndef ANDROID_C2_RK_MPI_ENC_H__
#define ANDROID_C2_RK_MPI_ENC_H__

#include "C2RKComponent.h"
#include "mpp/rk_mpi.h"
#include "C2RKMlvecLegacy.h"
#include "C2RKDump.h"

namespace android {

struct C2RKMpiEnc : public C2RKComponent {
public:
    class IntfImpl;

    C2RKMpiEnc(const char *name, c2_node_id_t id, const std::shared_ptr<IntfImpl> &intfImpl);
    virtual ~C2RKMpiEnc();

    c2_status_t onInit() override;
    c2_status_t onStop() override;
    void onReset() override;
    void onRelease() override;
    c2_status_t onFlush_sm() override;
    void process(
            const std::unique_ptr<C2Work> &work,
            const std::shared_ptr<C2BlockPool> &pool) override;
    c2_status_t drain(
            uint32_t drainMode,
            const std::shared_ptr<C2BlockPool> &pool) override;

private:
    /* DMA buffer memery */
    typedef struct {
        int32_t  fd;
        int32_t  size;
        void    *handler; /* buffer_handle_t */
    } MyDmaBuffer_t;

    /* Supported lists for InputFormat */
    typedef enum {
        C2_INPUT_FMT_UNKNOWN = 0,
        C2_INPUT_FMT_YUV420SP,
        C2_IPNUT_FMT_RGBA,
    } MyInputFormat;

    typedef struct {
        MppPacket outPacket;
        uint64_t  frameIndex;
    } OutWorkEntry;

    std::shared_ptr<IntfImpl> mIntf;
    MyDmaBuffer_t *mDmaMem;
    C2RKMlvecLegacy *mMlvec;
    C2RKDump *mDump;

    /* MPI interface parameters */
    MppCtx         mMppCtx;
    MppApi        *mMppMpi;
    MppEncCfg      mEncCfg;
    MppCodingType  mCodingType;
    MppFrameFormat mInputMppFmt;
    int32_t        mChipType;

    bool           mStarted;
    bool           mSpsPpsHeaderReceived;
    bool           mSawInputEOS;
    bool           mOutputEOS;
    bool           mSignalledError;
    int32_t        mHorStride;
    int32_t        mVerStride;
    int32_t        mCurLayerCount;
    int32_t        mInputCount;
    int32_t        mOutputCount;

    // configurations used by component in process
    // (TODO: keep this in intf but make them internal only)
    uint32_t mProfile;
    std::shared_ptr<C2StreamPictureSizeInfo::input> mSize;
    std::shared_ptr<C2StreamBitrateInfo::output> mBitrate;
    std::shared_ptr<C2StreamFrameRateInfo::output> mFrameRate;

    void fillEmptyWork(const std::unique_ptr<C2Work> &work);
    void finishWork(
            const std::unique_ptr<C2Work> &work,
            const std::shared_ptr<C2BlockPool>& pool,
            OutWorkEntry entry);
    c2_status_t drainInternal(uint32_t drainMode,
            const std::shared_ptr<C2BlockPool> &pool,
            const std::unique_ptr<C2Work> &work);

    c2_status_t setupBaseCodec();
    c2_status_t setupSceneMode();
    c2_status_t setupSliceSize();
    c2_status_t setupFrameRate();
    c2_status_t setupBitRate();
    c2_status_t setupProfileParams();
    c2_status_t setupQp();
    c2_status_t setupVuiParams();
    c2_status_t setupTemporalLayers();
    c2_status_t setupPrependHeaderSetting();
    c2_status_t setupMlvecIfNeccessary();
    c2_status_t setupEncCfg();

    c2_status_t initEncoder();
    c2_status_t releaseEncoder();

    c2_status_t handleCommonDynamicCfg();
    c2_status_t handleRequestSyncFrame();
    c2_status_t handleMlvecDynamicCfg(MppMeta meta);

    c2_status_t getInBufferFromWork(
            const std::unique_ptr<C2Work> &work, MyDmaBuffer_t *outBuffer);
    c2_status_t sendframe(
            MyDmaBuffer_t dBuffer, uint64_t pts, uint32_t flags);
    c2_status_t getoutpacket(OutWorkEntry *entry);

    C2_DO_NOT_COPY(C2RKMpiEnc);
};

C2ComponentFactory* CreateRKMpiEncFactory(std::string componentName);

}  // namespace android

#endif  // ANDROID_C2_RK_MPI_ENC_H__

