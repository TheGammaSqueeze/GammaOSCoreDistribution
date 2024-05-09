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
#ifndef SVEP_BUFFER_H
#define SVEP_BUFFER_H

#include "Gralloc.h"
#include "MemcAutoFd.h"

#include <ui/GraphicBuffer.h>

namespace android {

class Buffer {
public:
  Buffer(int w, int h, int format, std::string sName_);
  Buffer(int w, int h, int format, uint64_t usage, std::string name);
  ~Buffer();
  int Init();
  bool initCheck();
  buffer_handle_t GetHandle();
  uint64_t GetId();
  int GetFd();
  std::string GetName();
  int GetWidth();
  int GetHeight();
  int GetFormat();
  int GetStride();
  int GetByteStride();
  int GetSize();
  int GetUsage();
  uint32_t GetFourccFormat();
  uint64_t GetModifier();
  uint64_t GetBufferId();
  MemcUniqueFd GetFinishFence();
  int SetFinishFence(int fence);
  int WaitFinishFence();
  int SetReleaseFence(int fence);
  int MergeReleaseFence(int fd);
  MemcUniqueFd GetReleaseFence();
  MemcOutputFd ReleaseFenceOutput();
  int WaitReleaseFence();
  int DumpData();
  int FillFromFile(const char* file_name);

private:
  uint64_t uId;
  // BufferInfo
  int iFd_;
  int iWidth_;
  int iHeight_;
  int iFormat_;
  int iStride_;
  int iByteStride_;
  int iSize_;
  uint64_t iUsage_;
  uint32_t uFourccFormat_;
  uint64_t uModifier_;
  uint64_t uBufferId_;
  // Fence info
  MemcUniqueFd iFinishFence_;
  MemcUniqueFd iReleaseFence_;
  // Init flags
  bool bInit_;
  std::string sName_;
  buffer_handle_t buffer_;
  sp<GraphicBuffer> ptrBuffer_;
  Gralloc *ptrGralloc_;
};

}// namespace android
#endif
