# Rockit注册解码器

文件标识：

发布版本：

日期：

文件密级：□绝密   □秘密   □内部资料   ■公开

**免责声明**

本文档按“现状”提供，瑞芯微电子股份有限公司（“本公司”，下同）不对本文档的任何陈述、信息和内容的准确性、可靠性、完整性、适销性、特定目的性和非侵权性提供任何明示或暗示的声明或保证。本文档仅作为使用指导的参考。

由于产品版本升级或其他原因，本文档将可能在未经任何通知的情况下，不定期进行更新或修改。

**商标声明**

“Rockchip”、“瑞芯微”、“瑞芯”均为本公司的注册商标，归本公司所有。

本文档可能提及的其他所有注册商标或商标，由其各自拥有者所有。

**版权所有 © 2022 瑞芯微电子股份有限公司**

超越合理使用范畴，非经本公司书面许可，任何单位和个人不得擅自摘抄、复制本文档内容的部分或全部，并不得以任何形式传播。

瑞芯微电子股份有限公司

Rockchip Electronics Co., Ltd.

地址：     福建省福州市铜盘路软件园A区18号

网址：     [www.rock-chips.com](http://www.rock-chips.com)

客户服务电话： +86-4007-700-590

客户服务传真： +86-591-83951833

客户服务邮箱： [fae@rock-chips.com](mailto:fae@rock-chips.com)

---

**前言**

**概述**

Rockchip 多媒体处理平台接口操作指南。

**产品版本**
| 芯片名称  | Android版本 |
| ------------- | ------------ |
| 所有芯片 | Android11及以上版本 |

**读者对象**

本文档（本指南）主要适用于以下工程师：

技术支持工程师

软件开发工程师

**修订记录**

| **版本号** | **作者** | **修改日期** | **修改说明** |
| ---------- | -------- | :----------- | ------------ |
| V1.0.0     | HXM，HH  | 2023-03-22   | 初始版本     |

---

**目录**

[TOC]

---

## 概述

本文档用于描述在Android系统中使用rockit解码库时，如何外部注册音视频解码器。

**客户使用该接口注册并使用解码器时，必须先从版权权利人处获取注册解码器授权，并缴纳Licensing Fee**。

## API

Android SDK包，librockit媒体播放库实现了外部解码器函数的注册。

该功能模块为用户提供以下API:

- **[RockitRegisterDecoder](###RockitRegisterDecoder)**：外部注册解码器
- **[RockitUnRegisterDecoder](###RockitUnRegisterDecoder)**：外部注销解码器

### RockitRegisterDecoder

【描述】
注册解码器

【语法】

RT_RET RockitRegisterDecoder(int32_t *ps32Handle, const [RTAdecDecoder](###RTAdecDecoder) *pstDecoder);

【参数】

| 参数名     | 描述             | 输入/输出 |
| ---------- | ---------------- | --------- |
| ps32Handle | 注册句柄         | 输出      |
| pstDecoder | 解码器属性结构体 | 输入      |

【返回值】

| 返回值 | 描述     |
| ------ | -------- |
| 0      | 注册成功 |
| 非0    | 注册失败 |

【注意】

- **客户使用该接口注册并使用解码器时，必须先从版权权利人处获取注册解码器授权，并缴纳Licensing Fee**。
- 用户通过传入解码器属性结构体，向 librockit的解码模块注册一个解码器，并返回注册句柄，用户通过注册句柄来注销该解码器。
- 外部最大可注册 20 个解码器，且不允许注册librockit中已包含的解码器。
- 不允许外部重复注册同一种解码器，例如假如外部已注册 G711a 解码器，不允许另外再注册一个  G711a 解码器。
- 对于android的媒体播放服务mediaservice，外部只需注册一次解码器。注册成功后，多播放器实例均可使用。

### RockitUnRegisterDecoder

【描述】
注销解码器

【语法】

RT_RET RockitUnRegisterDecoder(int32_t s32Handle);

【参数】

| 参数名    | 描述                                 | 输入/输出 |
| --------- | ------------------------------------ | --------- |
| s32Handle | 注销句柄（注册解码器时获得的句柄）。 | 输入      |

【返回值】

| 返回值 | 描述 |
| ------ | ---- |
| 0      | 成功 |
| 非0    | 失败 |

【注意】

- 对于android的媒体播放服务mediaservice，无需注销解码，避免后续再使用时需要重新注册。
- 对于非mediaservice的播放，当多个播放器实例使用同一注册的解码器，只有使用该解码器的播放器实例全部销毁时，才能注销解码器。

## 示例

代码目录：vendor/rockchip/hardware/interfaces/rockit/direct/RockitExtCodecRegister.cpp。

【举列】

下面代码为注册Dummy解码器的示例：

```c++
int registerDummyDec(registerDecoderFunc* func) {
    (void)func;
    // open here to register codec
#if 0
    RTAdecDecoder adecCtx;
    RT_RET ret = RT_OK;
    INT32 ps32Handle = -1;

    // define context of DummyDec codec
    memset(&adecCtx, 0, sizeof(RTAdecDecoder));
    adecCtx.enType = RT_AUDIO_ID_XXX;   // for example RT_AUDIO_ID_PCM_ALAW
    adecCtx.profiles = RTMediaProfiles::getSupportProfile(adecCtx.enType);
    snprintf(reinterpret_cast<char*>(adecCtx.aszName), sizeof(adecCtx.aszName), "ext_dummy");
    adecCtx.pfnOpenDecoder  = DummyDec::open;
    adecCtx.pfnDecodeFrm    = DummyDec::decode;
    adecCtx.pfnGetFrmInfo   = DummyDec::getFrameInfo;
    adecCtx.pfnCloseDecoder = DummyDec::close;
    adecCtx.pfnResetDecoder = DummyDec::reset;
    // register to rockitx
    ret = func(&ps32Handle, &adecCtx);
    if (ret != RT_OK) {
        ALOGE("adec register decoder fail, ret = 0x%x", ret);
        return -1;
    }
#endif
    return 0;
}
```

【说明】

- dummyDec为外部注册解码器的示例，用户可仿照dummyDec代码实现具体的解码器。**需要强调的是，客户注册和使用解码器时，必现首先从版权权利人处获取注册解码器授权，并缴纳Licensing Fee。**
- RT_AUDIO_ID_XXX修改为具体的音频codec类型，具体请见RTLibDefine.h的定义。
- **Dummy.cpp为伪代码实现，为了防止编译不过，因此registerDummyDec中实现对DummyDec的注册默认注释不编译。当客户在DummyDec实现/对接完某具体解码器时，可在registerDummyDec函数打开注释的代码。**
- 当某解码类型没有定义profile和level时，可设置adecCtx.profiles = NULL，否则需要定义当前注册解码支持的对应格式的profile和level。


```c++
int rockitRegisterCodec(registerDecoderFunc* func) {
    // only resigter once
    Mutex::Autolock autoLock(gCodecLock);
    if (func == NULL || gReisgter) {
        return 0;
    }

    gReisgter = true;
    // 注册Dummy解码器
    registerDummyDec(func);

    // 添加代码 注册更多解码器

    return 0;
}
```

【说明】

- mediaserver是android的媒体播放服务，会一直存在，因此只需在第一次播放前完成解码器的注册。
- 因为mediaserver一直存在，因此无需注销对应的解码器，避免后续使用时需要重新注册。

### DummyDec

目录：vendor\rockchip\hardware\interfaces\rockit\direct\codec\DummyDec.cpp

DummyDec解码示例。

- **[open](####open)**：初始化解码。
- **[close](####close)**：关闭解码器。
- **[decode](####decode)**：解码。
- **[getFrameInfo](####getFrameInfo)**：获取解码音频帧信息。
- **[reset](####reset)**：复位解码器。

#### open

【描述】
初始化DummyDec解码。

【语法】

INT32 DummyDec::open(RT_VOID *pDecoderAttr, RT_VOID **ppDecoder) ；

【参数】

| 参数名       | 描述                                                      | 输入/输出 |
| ------------ | --------------------------------------------------------- | --------- |
| pDecoderAttr | 解码器属性结构体[ADEC_ATTR_CODEC_S](###ADEC_ATTR_CODEC_S) | 输入      |
| ppDecoder    | 解码器句柄，该句柄将用于后续的函数中。                    | 输出      |

【返回值】

| 返回值 | 描述   |
| ------ | ------ |
| 0      | 成功。 |
| 非0    | 失败。 |

【注意】

- pDecoderAttr传入的采样率，声道数等参数，可能是通过解析文件的封装头得到，与实际音频编码的参数可能并不一致，因此该传入的值并不一定正确。
- ppDecoder为open函数创建的解码器句柄。

#### close

【描述】
关闭DummyDec解码。

【语法】

INT32 DummyDec::close(RT_VOID *pDecoder) ；

【参数】

| 参数名   | 描述       | 输入/输出 |
| -------- | ---------- | --------- |
| pDecoder | 解码器句柄 | 输入      |

【返回值】

| 返回值 | 描述   |
| ------ | ------ |
| 0      | 成功。 |
| 非0    | 失败。 |

#### decode

【描述】
DummyDec解码。

【语法】

INT32 DummyDec::decode(RT_VOID*pDecoder, RT_VOID *pDecParam) ；

【参数】

| 参数名    | 描述                                                         | 输入/输出 |
| --------- | ------------------------------------------------------------ | --------- |
| pDecoder  | 解码器句柄                                                   | 输入      |
| pDecParam | 音频输入码流和解码输出结构体[AUDIO_ADENC_PARAM_S。](###AUDIO_ADENC_PARAM_S) | 输入/输出 |

【返回值】

| 返回值 | 描述                                                        |
| ------ | ----------------------------------------------------------- |
| 0      | 成功。                                                      |
| 非0    | 失败，请参见[ADEC_DECODER_RESULT](###ADEC_DECODER_RESULT)。 |

【说明】

- AUDIO_ADENC_PARAM_S的u32InLen 有输入/输出2个功能 。输入：表示decode函数中，可用于解码的码流有效数据长度。输出：表示当前解码完成后，剩余码流的有效数据的长度，其值 = 当前输入有效数据长度 - 解码消耗掉的数据长度。

- AUDIO_ADENC_PARAM_S的u32OutLen 有输入/输出2个功能 。输入：表示decode函数中，存放解码数据缓冲区大小。输出：表示当前解码完成后，解码音频数据的实际长度。

- AUDIO_ADENC_PARAM_S的u64OutTimeStamp表示解码音频数据的时间戳。对于不带缓冲的解码器(比如示例的g711a/u)，输出时间戳u64OutTimeStamp总是等于未解码输入数据的时间戳u64InTimeStamp；当解码器带有缓冲时，输出时间戳u64OutTimeStamp必现等于实际数据的时间戳，否则会造成音视频不同步。

- 解码的返回值：

  ADEC_DECODER_OK： 表示解码成功，解码后的pcm数据长度需保存到AUDIO_ADENC_PARAM_S的 u32OutLen中。

  ADEC_DECODER_TRY_AGAIN： 表示当前送入待解码数据需要重新送入。当解码器缓冲区待解码数据满了，不能在送入新的待解码数据时，可返回该值以使得解码线程后续再送入该数据。

  ADEC_DECODER_ERROR：解码出错。当送入的待解码异常时，可返回该值表示解码出错，解码流程将会丢弃当前输入数据。

  ADEC_DECODER_EOS：表示当前解码结束。当输入的待解码数据长度为0时，表示当前播放流/文件结束，此时解码器中如果没有缓存数据可输出，则可返回该值表示解码器中的数据输出完毕，返回该值后，解码流程将停止调用。

#### getFrameInfo

【描述】
获取解码音频帧信息。

【语法】

INT32 DummyDec::getFrameInfo(RT_VOID *pDecoder, RT_VOID *pInfo)；

【参数】

| 参数名   | 描述                                                         | 输入/输出 |
| -------- | ------------------------------------------------------------ | --------- |
| pDecoder | 解码器句柄。                                                 | 输入      |
| pInfo    | 音频帧信息结构体指针[ADEC_FRAME_INFO_S](###ADEC_FRAME_INFO_S)。 | 输出      |

【返回值】

| 返回值 | 描述   |
| ------ | ------ |
| 0      | 成功。 |
| 非0    | 失败。 |

【说明】

- decode函数返回解码成功后，解码流程将调用该函数获取输出音频pcm数据的参数。

#### reset

【描述】
复位解码器。

【语法】

INT32 DummyDec::reset(RT_VOID *pDecoder)；

【参数】

| 参数名   | 描述       | 输入/输出 |
| -------- | ---------- | --------- |
| pDecoder | 解码器句柄 | 输入      |

【返回值】

| 返回值 | 描述   |
| ------ | ------ |
| 0      | 成功。 |
| 非0    | 失败。 |

## 注册解码器编译

由于DummyDec.cpp只是一个示例代码，伪代码中使用XXX和xxx代表某音频格式的宏定义和代码。当客户需要适配某具体的音频格式时，需如下操作：

- 修改伪代码，对接实际解码器的api调用和宏定义。
- 在codec目录的Android.bp_bak重命名为Android.bp(因为是伪代码，默认定义为Android.bp_bak，默认不编译，防止伪代码编译时的错误)。当对接的解码器为第三方库时，需要在shared_libs或者static_libs中添加对解码库的引用。当需要解码库的头文件时，需要在include_dirs中添加解码头文件路径。

```shell
cc_library {
    name: "librockit_ext_codec",
    srcs: [
       "DummyDec.cpp",
    ],

    shared_libs: [
        ......
        "libaaa", // 添加引用的动态库
    ],

    static_libs: [
        ......
        "libbbb", // 添加引用的静态库
    ],

    include_dirs: [
        ......
        "external/ccc/include"   // 添加头文件目录
    ]
}
```

在direct目录下的Android.bp 中打开librockit_ext_codec动态库的引用：

```shell
cc_library_shared {
    ......
    shared_libs: [
        ......
        "librockit_ext_codec",  // 链接librockit_ext_codec
    ],
}
```

## 结构体

### RTAdecDecoder

【说明】

定义解码器属性结构体

【定义】

```c++
typedef struct _RTAdecDecoder {
    int32_t enType;       // see RTCodecID
    char aszName[17];
    RTCodecProfiles *profiles;    // see RTCodecProfiles.h
    // open decoder
    int32_t (*pfnOpenDecoder)(void *pDecoderAttr, void **ppDecoder);
    int32_t (*pfnDecodeFrm)(void *pDecoder, void *pParam);
    // get audio frames infor
    int32_t (*pfnGetFrmInfo)(void *pDecoder, void *pInfo);
    // close audio decoder
    int32_t (*pfnCloseDecoder)(void *pDecoder);
    // reset audio decoder
    int32_t (*pfnResetDecoder)(void *pDecoder);
} RTAdecDecoder;
```

【成员】

| 成员名称        | 描述                                                         |
| --------------- | ------------------------------------------------------------ |
| enType          | 解码协议。 见RTLibDefine.h中RTCodecID的定义。                |
| aszName         | 解码器名称。注意外部注册解码名称，必须以"ext_"开头，比如“ext_g711a”。否则将导致注册失败。 |
| profiles        | 解码器支持的profile和level。当解码器支持多个profile和level时，需将所以支持的profile和level都列举出来。 |
| pfnOpenDecoder  | 打开解码器的函数指针。                                       |
| pfnDecodeFrm    | 解码的函数指针。                                             |
| pfnGetFrmInfo   | 获取音频帧信息的函数指针。                                   |
| pfnCloseDecoder | 关闭解码器的函数指针。                                       |
| pfnResetDecoder | 清空缓存 buffer，复位解码器。                                |

【注意事项】

- pfnOpenDecoder函数第一个参数pDecoderAttr为[ADEC_ATTR_CODEC_S](###ADEC_ATTR_CODEC_S)类型，用户可在对应的注册函数中强行类型转换，可访问到[ADEC_ATTR_CODEC_S](###ADEC_ATTR_CODEC_S)中的相关变量。
- pfnDecodeFrm函数的第二个参数pDecParam为[AUDIO_ADENC_PARAM_S](###AUDIO_ADENC_PARAM_S)类型，用户可在对应的注册函数中强行类型转换，可访问到[AUDIO_ADENC_PARAM_S](###AUDIO_ADENC_PARAM_S)中的相关变量。

- profiles：当解码器注册多个profile和level时，需将所以支持的profile和level都列举处理。

  例如：定义了某解码类型的2个profile，RT_PROFILE_XXX_MAIN和 RT_PROFILE_XXX_HE。

  需要注意的是，当用户定义了某解码器的profile时，必须以 { RT_PROFILE_UNKNOWN， "UNKNOWN"} 结尾。

  ```c
  static RTCodecProfiles sXXXProfiles[] = {
      { RT_PROFILE_XXX_MAIN,               "XXX_MAIN" },
      { RT_PROFILE_XXX_HE,                 "XXX_HE"   },
      { RT_PROFILE_UNKNOWN,                "UNKNOWN"  },    // 必须以该项结尾
  };
  ```

### ADEC_ATTR_CODEC_S

【说明】
定义音频解码器属性结构体

【定义】

```c
typedef struct rkADEC_ATTR_CODEC_S {
    int32_t    enType;                // see RTCodecID
    uint32_t   u32Channels;
    uint32_t   u32SampleRate;
    uint32_t   u32Bitrate;

    void       *pExtraData;
    uint32_t   u32ExtraDataSize;

    uint32_t   u32Resv[4];           // resv for user
    void       *pstResv;             // resv for user
} ADEC_ATTR_CODEC_S;
```

【成员】

| 成员名称         | 描述                                        |
| ---------------- | ------------------------------------------- |
| enType           | 解码协议。 见RTLibDefine.h中RTCodecID的定义 |
| u32Channels      | 码流声道数。                                |
| u32SampleRate    | 码流采样率。                                |
| u32Bitrate       | 数据比特率                                  |
| pExtraData       | 解码外部数据                                |
| u32ExtraDataSize | 解码外部数据长度                            |
| u32Resv[4]       | 保留字节，用于解码器参数扩展                |
| pstResv          | 保留结构体指针，用于解码器参数扩展          |

【注意事项】

- 当结构体变量不足以传递解码器参数时，可通过保留字节u32Resv和保留结构体指针pstResv来扩展参数。
- 特定解码器需要设置当前音频协议支持的音频数据比特率，比如G726解码器。G726解码器在初始化时，需要根据比特率设置正确的码字(codeword)。
- 部分解码器的初始化，需要用到pExtraData数据。

### ADEC_DECODER_RESULT

【说明】
定义注册解码器解码函数返回值

【定义】

```c
typedef enum rkADEC_DECODER_RESULT {
    ADEC_DECODER_OK = RK_SUCCESS,
    ADEC_DECODER_TRY_AGAIN,
    ADEC_DECODER_ERROR,
    ADEC_DECODER_EOS,
} ADEC_DECODER_RESULT;
```

【成员】

| 成员名称               | 描述                                       |
| ---------------------- | ------------------------------------------ |
| ADEC_DECODER_OK        | 解码成功(送解码数据成功且拿到了解码数据)。 |
| ADEC_DECODER_TRY_AGAIN | 解码重试。                                 |
| ADEC_DECODER_ERROR     | 解码错误。                                 |
| ADEC_DECODER_EOS       | 解码最后一帧数据。                         |

【注意事项】

- ADEC_DECODER_TRY_AGAIN：该值用于在表示当前解码数据送入失败(比如当前解码内部缓冲满，无法送入新的数据)或者送入码流数据后，没有拿到解码数据的情形。librockit的解码模块在获取该返回值后，会再次送入当前码流(如果[AUDIO_ADENC_PARAM_S](###AUDIO_ADENC_PARAM_S)的u32InLen输出值不为0)。

- ADEC_DECODER_ERROR：解码错误。librockit的解码模块获取到该值后，会丢弃当前帧。

- ADEC_DECODER_EOS：该值用于通知librockit的ADEC模块，解码器已送出最后一帧数据(标记当前解码结束)。ADEC模块获取到该值后，不再调用解码器相关函数进行解码，并标记当前音频帧为EOS帧。

### ADEC_FRAME_INFO_S

  【说明】
  定义解码器音频帧信息，用于返回解码后的音频数据的参数

  【定义】

  ```c
typedef struct rkADEC_FRAME_INFO_S {
    uint32_t         u32SampleRate;
    uint32_t         u32Channels;
    uint32_t         u32FrameSize;
    uint64_t         u64ChnLayout;
    AUDIO_BIT_WIDTH_E enBitWidth;
    uint32_t         resv[2];
} ADEC_FRAME_INFO_S;
  ```

  【成员】

| 员名称        | 描述                 |
| ------------- | -------------------- |
| u32SampleRate | 解码数据的采样率。   |
| u32Channels   | 解码数据的声道数。   |
| u64ChnLayout  | 解码数据的声道布局。 |
| enBitWidth    | 解码数据的采样精度。 |
| resv[2]       | 保留位，用于扩展。   |

### AUDIO_ADENC_PARAM_S

【说明】
定义注册解码器/编码器 解码/编码 输入输出数据的结构体。

【定义】

```c
typedef struct rkAUDIO_ADENC_PARAM_S {
    uint8_t    *pu8InBuf;
    uint32_t    u32InLen;
    uint64_t    u64InTimeStamp;

    uint8_t    *pu8OutBuf;
    uint32_t    u32OutLen;
    uint64_t    u64OutTimeStamp;
} AUDIO_ADENC_PARAM_S;
```

【成员】

| 成员名称        | 描述                                                         | 输入/输出 |
| --------------- | ------------------------------------------------------------ | --------- |
| pu8InBuf        | 输入数据buffer指针。                                         | 输入      |
| u32InLen        | 输入： 输入数据长度(即pu8InBuf中输入有效数据的长度)。<br/>输出：输入buffer中(即pu8InBuf)剩余数据的长度 | 输入/出   |
| u64InTimeStamp  | 输入数据的时间戳。                                           | 输入      |
| pu8OutBuf       | 输出数据buffer指针。                                         | 输入      |
| u32OutLen       | 输入：输出buffer的最大容量。<br/>输出：输出buffer中有效数据的长度。 | 输入/出   |
| u64OutTimeStamp | 输出数据的时间戳。                                           | 输出      |
