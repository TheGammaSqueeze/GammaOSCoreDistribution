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
#pragma once

#include <memory>
#include "MemcType.h"

namespace android {

class MemcBase;

/**
 * 本地模式（MEMC_NATIVE_MODE）
 * @brief: 每次MEMC任务输入2帧图像，输出此2帧图像的插帧结果
 */
class MemcNativeMode{
public:
  MemcNativeMode();
  ~MemcNativeMode();
  MemcNativeMode(const MemcNativeMode&) = delete;
  MemcNativeMode& operator=(const MemcNativeMode&) = delete;

  /**
   * @brief 初始化函数
   *
   * @param version_str [IN] 外部传入的版本信息，用于版本适配
   * @param initAsync   [IN] 异步初始化标志, 设置为 true 表示使能异步初始化
   * @return MEMC_ERROR::
   *         - MEMC_NO_ERROR, success
   *         - Other, fail
   */
  MEMC_ERROR Init(const char *version_str, bool initAsync = false);

  /**
   * @brief 获取MEMC_MODE处理模式信息
   *
   * @param src      [IN] 输入图像信息
   * @param out_mode [OUT] 输出MEMC模式
   * @return MEMC_ERROR::
   *         - MEMC_NO_ERROR, success
   *         - Other, fail
   */
  MEMC_ERROR MatchMemcMode(const MemcImageInfo *src, MEMC_MODE *out_mode);

  /**
   * @brief 获取MEMC所需目标图像数据
   *
   * @param out_dst   [OUT] 输出目标图像数据
   * @return MEMC_ERROR::
   *         - MEMC_NO_ERROR, success
   *         - Other, fail
   */
  MEMC_ERROR GetDstImageInfo(MemcImageInfo *out_dst);

  /**
   * @brief 同步处理模式，MEMC执行完成后返回
   *
   * @param src0 [IN] 输入图像0
   * @param src1 [IN] 输入图像1
   * @param dst  [OUT] 输出图像
   * @return MEMC_ERROR:
   *         - MEMC_NO_ERROR, success
   *         - Other, fail
   */
  MEMC_ERROR Run(const MemcImageInfo *src0,
                  const MemcImageInfo *src1,
                  const MemcImageInfo *dst);

  /**
   * @brief 异步执行MEMC处理
   *
   * @param src0 [IN] 输入图像0
   * @param src1 [IN] 输入图像1
   * @param dst  [OUT] 输出图像
   * @param outFence [OUT] fence fd 标志异步执行完成
   * @return MEMC_ERROR:
   *         - MEMC_NO_ERROR, success
   *         - Other, fail
   */
  MEMC_ERROR RunAsync(const MemcImageInfo *src0,
                        const MemcImageInfo *src1,
                        const MemcImageInfo *dst,
                        int *outFence);

private:
  std::shared_ptr<MemcBase> mMemcBase_;
};


/**
 * 代理模式（MEMC_PROXY_MODE）
 * @brief: 需要输入连续的图像流，每次MEMC任务输入1张图像，从Memc实例内部队列输出1张图像
 */
class MemcProxyMode{
public:
  MemcProxyMode();
  ~MemcProxyMode();
  MemcProxyMode(const MemcProxyMode&) = delete;
  MemcProxyMode& operator=(const MemcProxyMode&) = delete;

  /**
   * @brief 初始化函数
   *
   * @param version_str [IN] 外部传入的版本信息，用于版本适配
   * @param initAsync   [IN] 异步初始化标志, 设置为 true 表示使能异步初始化
   * @return MEMC_ERROR::
   *         - MEMC_NO_ERROR, success
   *         - Other, fail
   */
  MEMC_ERROR Init(const char *version_str, bool initAsync = false);

  /**
   * @brief 设置 OSD 字幕模式
   *
   * @param mode   [IN] 模式类型
   * @param osdStr [IN] OSD 字符串
   * @return MEMC_ERROR::
   *         - MEMC_NO_ERROR, success
   *         - Other, fail
   */
  MEMC_ERROR SetOsdMode(MEMC_OSD_MODE mode, const wchar_t* osdStr);

  /**
   * @brief 设置对比模式，将MEMC输出数据与源数据进行对比展示
   *
   * @param enable [IN] 对比模式使能开关，默认关闭
   * @return MEMC_ERROR::
   *         - MEMC_NO_ERROR, success
   *         - Other, fail
   */
  MEMC_ERROR SetContrastMode(bool enable);

  /**
   * @brief 设置旋转模式
   *
   * @param rotate [IN] 设置旋转方向
   * @return MEMC_ERROR::
   *         - MEMC_NO_ERROR, success
   *         - Other, fail
   */
  MEMC_ERROR SetRotateMode(MEMC_ROTATE_MODE rotate);

  /**
   * @brief 获取MEMC_MODE处理模式信息
   *
   * @param src      [IN] 输入图像信息
   * @param out_mode [OUT] 输出MEMC模式
   * @return MEMC_ERROR::
   *         - MEMC_NO_ERROR, success
   *         - Other, fail
   */
  MEMC_ERROR MatchMemcMode(const MemcImageInfo *src, MEMC_MODE *out_mode);

  /**
   * @brief 获取MEMC建议的目标图像信息
   *
   * @param out_dst   [OUT] 输出目标图像数据
   * @return MEMC_ERROR::
   *         - MEMC_NO_ERROR, success
   *         - Other, fail
   */
  MEMC_ERROR GetDstImageInfo(MemcImageInfo *out_dst);

  /**
   * @brief 同步处理模式，MEMC执行完成后返回
   *
   * @param src0 [IN] 输入图像
   * @param dst  [OUT] 输出图像
   * @return MEMC_ERROR:
   *         - MEMC_NO_ERROR, success
   *         - Other, fail
   */
  MEMC_ERROR Run(const MemcImageInfo *src, const MemcImageInfo *dst);

  /**
   * @brief 异步执行MEMC处理
   *
   * @param src [IN] 输入图像
   * @param dst [OUT] 输出图像
   * @param outFence [OUT] fence fd 标志异步执行完成
   * @return MEMC_ERROR:
   *         - MEMC_NO_ERROR, success
   *         - Other, fail
   */
  MEMC_ERROR RunAsync(const MemcImageInfo *src, const MemcImageInfo *dst, int *outFence);

  /**
   * @brief 清理MEMC实例内部的资源，在切换模式及切换输入流时调用
   *
   * @return MEMC_ERROR::
   *         - MEMC_NO_ERROR, success
   *         - Other, fail
   */
  MEMC_ERROR ClearResource(void);

private:
  std::shared_ptr<MemcBase> mMemcBase_;
};

} //namespace android