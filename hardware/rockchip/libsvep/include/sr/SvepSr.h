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

#include "SrType.h"
#include "SrImage.h"

class Sr;

class SvepSr
{
public:
    /**
     * @brief Sr构造函数
     */
    SvepSr();

    /**
     * @brief Sr析构函数
     */
    ~SvepSr();
    /**
     * @brief 禁用引用构造函数
     */
    SvepSr(const SvepSr &) = delete;
    /**
     * @brief 禁用拷贝构造函数
     */
    SvepSr &operator=(const SvepSr &) = delete;

    /**
     * @brief 初始化函数
     *
     * @param version_str [IN] 版本号，用于版本校验
     * @param async_init  [IN] 异步初始化标志
     *
     * @return SrError::
     *         - None, success
     *         - Other, fail
     */
    SrError Init(const char *version_str, bool async_init);

    /**
     * @brief 设置SR强度，RK3588支持，RK356x不支持
     *
     * @param rate [IN] 强度值
     * @return SrError::
     *         - None, success
     *         - Other, fail
     */
    SrError SetEnhancementRate(int rate);

    /**
     * @brief 设置 OSD 字幕模式
     *
     * @param mode [IN] 模式类型
     * @param osdStr [IN] OSD 字符串
     * @return SrError::
     *         - None, success
     *         - Other, fail
     */
    SrError SetOsdMode(SrOsdMode mode, const wchar_t *osdStr);

    /**
     * @brief 设置对比模式，提供SR增强与源数据的对比模式展示
     *
     * @param enable [IN] 模式使能开关，默认关闭
     * @param offsetPercent [IN] 分割线左右占比，可实现扫描线效果
     * @return SrError::
     *         - None, success
     *         - Other, fail
     */
    SrError SetContrastMode(bool enable, int offsetPercent);

    /**
     * @brief 设置旋转模式
     *
     * @param rotate [IN] 设置旋转方向
     * @return SrError::
     *         - None, success
     *         - Other, fail
     */
    SrError SetRotateMode(SrRotateMode rotate);

    /**
     * @brief 匹配SR处理模型
     *
     * @param int_src  [IN] 输入图像信息
     * @param usage    [IN] 模式额外的usage,例如输出8K分辨率模式
     * @param out_mode [OUT] 输出SR处理模式
     * @return SrError::
     *         - None, success
     *         - Other, fail
     */
    SrError MatchSrMode(const SrImageInfo *int_src, SrModeUsage usage,
                        SrMode *out_mode);
    /**
     * @brief 获取目标土星参数
     *
     * @param int_src  [IN] 输入图像信息
     * @param out_mode [OUT] 输出SR处理模式
     * @param out_dst  [OUT] 输出图像信息
     * @return SrError::
     *         - None, success
     *         - Other, fail
     */
    SrError GetDetImageInfo(SrImageInfo *out_dst);

    /**
     * @brief 同步处理模式，Sr执行完成后返回
     *
     * @param ctx [INOUT] sr上下文
     * @return SrError:
     *         - None, success
     *         - Other, fail
     */
    SrError Run(const SrImageInfo *int_src, const SrImageInfo *int_dst);

    /**
     * @brief 异步处理模式，可提高帧率
     *
     * @param ctx [INOUT] sr上下文
     * @param outFence [OUT] fence fd 标志异步执行完成
     * @return SrError:
     *         - None, success
     *         - Other, fail
     */
    SrError RunAsync(const SrImageInfo *int_src, const SrImageInfo *int_dst,
                     int *outFence);

private:
    std::shared_ptr<Sr> mSr_;
};