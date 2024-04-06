/*
 * Copyright (C) 2016 The Android Open Source Project
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

#ifndef DRM_HDR_PARSER_H_
#define DRM_HDR_PARSER_H_
#include <stdint.h>
#include "rockchip/hdr/dovi.h"
#include "rockchip/hdr/metadata_hdr.h"

#include <mutex>
#include <map>
namespace android {

class DrmHdrParser{
public:
  static DrmHdrParser* Get() { return GetInstance(); }
  ~DrmHdrParser();
  DrmHdrParser(const DrmHdrParser&)=delete;
  DrmHdrParser& operator=(const DrmHdrParser&)=delete;

  // NextHdr
  void* NextHdrCreatHandle(int display, uint32_t layer_id);
  int NextHdrParser(dovi_handle_t dovi_handle, dovi_parser_param_s* param);
  void NextHdrDestoryHandle(int display, uint32_t layer_id);

  // RK3528 Metadata hdr parser
  int MetadataHdrParser(rk_hdr_parser_params_t* p_hdr_parser_params);
  int MetadataHdrparserFormat(rk_hdr_parser_params_t* p_hdr_parser_params,
                              rk_hdr_fmt_info_t* p_hdr_fmt_info);

private:
  DrmHdrParser();
  int Init();
  int InitNextHdr();
  int InitVividHdr();
  static DrmHdrParser* GetInstance(){
    static DrmHdrParser dhp;
    if(dhp.Init())
      return NULL;
    else
      return &dhp;
  }

  bool bInit_;
  bool bInitNextHdrSucess_;
  bool bInitVividHdrSucess_;
  std::map<int, std::map<uint32_t, dovi_handle_t>>
    mCacheHandle_;
  mutable std::mutex mtx_;
};
}; // namespace android

#endif