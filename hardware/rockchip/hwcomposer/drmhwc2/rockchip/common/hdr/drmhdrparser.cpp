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

#include "rockchip/hdr/drmhdrparser.h"
#include "rockchip/utils/drmdebug.h"

#include <dlfcn.h>
#include <fcntl.h>
#define DOVI_PARSER_LIB "/vendor/lib64/libdovi_parser.so"

// #define VIVID_PARSER_USE_DLOPEN 1
#ifdef VIVID_PARSER_USE_DLOPEN
#define VIVID_PARSER_LIB "/vendor/lib64/libhdr_params_parser.so"
#endif


namespace android{

// Next Hdr
typedef dovi_handle_t (*dovi_init_func)(void);
typedef int (*dovi_parser_func)(dovi_handle_t handle, dovi_parser_param_s *param);
typedef void (*dovi_destroy_func)(dovi_handle_t handle);
struct dovi_ops {
    dovi_handle_t (*init)(void);
    int (*parser)(dovi_handle_t handle, dovi_parser_param_s *param);
    void (*destroy)(dovi_handle_t handle);
};
static struct dovi_ops dovi_ops;
static void * dovi_lib_handle = NULL;

#if VIVID_PARSER_USE_DLOPEN==1
// Vivid Hdr
typedef int (*hdr_format_parser_func)(rk_hdr_parser_params_t* p_hdr_parser_params,
                                 rk_hdr_fmt_info_t* p_hdr_fmt_info);
typedef void (*vivid_parser_func)(rk_hdr_parser_params_t* p_hdr_parser_params);
static void * vivid_lib_handle = NULL;
struct vivid_ops {
    void (*parser)(rk_hdr_parser_params_t* p_hdr_parser_params);
    void (*parser_hdr)(rk_hdr_parser_params_t* p_hdr_parser_params,
                       rk_hdr_fmt_info_t* p_hdr_fmt_info);
};
static struct vivid_ops vivid_ops;
#elif USE_HDR_PARSER==false
int hdr_parser(rk_hdr_parser_params_t* p_hdr_parser_params){
  return -1;
}

int hdr_format_parser(rk_hdr_parser_params_t* p_hdr_parser_params,
                      rk_hdr_fmt_info_t* p_hdr_fmt_info){
  return -1;
}

#endif

DrmHdrParser::DrmHdrParser()
  : bInit_(false),
    bInitNextHdrSucess_(false),
    bInitVividHdrSucess_(false){};
DrmHdrParser::~DrmHdrParser(){};

int DrmHdrParser::Init(){
    if(bInit_){
      return 0;
    }

    InitNextHdr();
    InitVividHdr();

    bInit_ = true;
    return 0;
}

int DrmHdrParser::InitNextHdr(){
    dovi_lib_handle = dlopen(DOVI_PARSER_LIB, RTLD_NOW);
    if (dovi_lib_handle == NULL) {
        HWC2_ALOGD_IF_ERR("cat not open %s\n", DOVI_PARSER_LIB);
        return -1;
    }else{
        dovi_ops.init = (dovi_init_func)dlsym(dovi_lib_handle, "dovi_init");
        dovi_ops.parser = (dovi_parser_func)dlsym(dovi_lib_handle, "dovi_parser");
        dovi_ops.destroy = (dovi_destroy_func)dlsym(dovi_lib_handle, "dovi_deinit");

        if(dovi_ops.init == NULL||
           dovi_ops.parser == NULL ||
           dovi_ops.destroy == NULL){
          HWC2_ALOGD_IF_ERR("cat not dlsym init=%p parser=%p destroy=%p\n", dovi_ops.init,
                                                                            dovi_ops.parser,
                                                                            dovi_ops.destroy);
          return -1;
        }
    }

    bInitNextHdrSucess_ = true;
    return 0;
}

int DrmHdrParser::InitVividHdr(){
#ifdef VIVID_PARSER_USE_DLOPEN
    vivid_lib_handle = dlopen(VIVID_PARSER_LIB, RTLD_NOW);
    if (vivid_lib_handle == NULL) {
        HWC2_ALOGD_IF_ERR("cat not open %s\n", VIVID_PARSER_LIB);
        return -1;
    }else{
        vivid_ops.parser = (vivid_parser_func)dlsym(vivid_lib_handle, "_Z10hdr_parserP22rk_hdr_parser_params_t");
        vivid_ops.parser_hdr = (hdr_format_parser_func)dlsym(vivid_lib_handle, "_Z17hdr_format_parserP22rk_hdr_parser_params_tP17rk_hdr_fmt_info_t");
        if(vivid_ops.parser  == NULL ||
           vivid_ops.parser_hdr == NULL){
          HWC2_ALOGD_IF_ERR("cat not dlsym parser=%p parser_hdr=%p\n",
                            vivid_ops.parser, vivid_ops.parser_hdr);
          return -1;
        }
    }
#elif USE_HDR_PARSER
    bInitVividHdrSucess_ = true;
#else
    bInitVividHdrSucess_ = false;
#endif

    return 0;
}

void* DrmHdrParser::NextHdrCreatHandle(int display, uint32_t layer_id){
  if(dovi_lib_handle == NULL){
    return NULL;
  }

  if(!bInitNextHdrSucess_){
    return NULL;
  }

  auto cache_display = mCacheHandle_.find(display);
  if( cache_display != mCacheHandle_.end()){
    auto cache_layer = cache_display->second.find(layer_id);
    if( cache_layer != cache_display->second.end()){
      return cache_layer->second;
    }
  }

  std::map<uint32_t, dovi_handle_t> map_layer;
  map_layer[layer_id] = dovi_ops.init();
  mCacheHandle_[display] = map_layer;
  return map_layer.at(layer_id);
}

int DrmHdrParser::NextHdrParser(dovi_handle_t dovi_handle, dovi_parser_param_s* param){
  if(dovi_lib_handle == NULL){
    return -1;
  }

  if(!bInitNextHdrSucess_){
    return -1;
  }

  if(!dovi_handle)
    return -1;

  int ret = dovi_ops.parser(dovi_handle, param);
  if(ret)
    return ret;

  return 0;
}
void DrmHdrParser::NextHdrDestoryHandle(int display, uint32_t layer_id){
  if(dovi_lib_handle == NULL){
    return;
  }

  if(!bInitNextHdrSucess_){
    return;
  }

  auto cache_display = mCacheHandle_.find(display);
  if( cache_display != mCacheHandle_.end()){
    auto cache_layer = cache_display->second.find(layer_id);
    if( cache_layer != cache_display->second.end()){
      dovi_ops.destroy(cache_layer->second);
      mCacheHandle_.erase(cache_display);
      return;
    }
  }

  HWC2_ALOGD_IF_ERR("can't find suitable hdrParserHandle display=%d layer-id=%d",
      display, layer_id);

  return;
}

int DrmHdrParser::MetadataHdrParser(rk_hdr_parser_params_t* p_hdr_parser_params){
  if(!bInitVividHdrSucess_){
    return -1;
  }

  if(!p_hdr_parser_params){
    return -1;
  }

#if VIVID_PARSER_USE_DLOPEN==1
  if(vivid_lib_handle == NULL){
    return -1;
  }

  return vivid_ops.parser(p_hdr_parser_params);
#elif USE_HDR_PARSER==true
  return hdr_parser(p_hdr_parser_params);
#else
  return -1;
#endif
}

int DrmHdrParser::MetadataHdrparserFormat(rk_hdr_parser_params_t* p_hdr_parser_params,
                                          rk_hdr_fmt_info_t* p_hdr_fmt_info){
  if(!bInitVividHdrSucess_){
    return -1;
  }

  if(!p_hdr_parser_params){
    return -1;
  }

#if VIVID_PARSER_USE_DLOPEN==1
  if(vivid_lib_handle == NULL){
    return -1;
  }

  return vivid_ops.hdr_format_parser(p_hdr_parser_params, p_hdr_fmt_info);
#elif USE_HDR_PARSER==true
  return hdr_format_parser(p_hdr_parser_params, p_hdr_fmt_info);
#else
  return -1;
#endif
}

};// namespace android

