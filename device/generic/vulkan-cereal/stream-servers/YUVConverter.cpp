/*
* Copyright (C) 2016 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

#include "YUVConverter.h"

#include <assert.h>
#include <stdio.h>
#include <string>

#include "DispatchTables.h"
#include "host-common/feature_control.h"
#include "host-common/misc.h"


#define FATAL(fmt,...) do { \
    fprintf(stderr, "%s: FATAL: " fmt "\n", __func__, ##__VA_ARGS__); \
    assert(false); \
} while(0)

#define YUV_CONVERTER_DEBUG 0

#if YUV_CONVERTER_DEBUG
#define YUV_DEBUG_LOG(fmt, ...)                                                     \
    fprintf(stderr, "yuv-converter: %s:%d " fmt "\n", __func__, __LINE__, \
            ##__VA_ARGS__);
#else
#define YUV_DEBUG_LOG(fmt, ...)
#endif

bool isInterleaved(FrameworkFormat format) {
    switch (format) {
    case FRAMEWORK_FORMAT_NV12:
    case FRAMEWORK_FORMAT_P010:
        return true;
    case FRAMEWORK_FORMAT_YUV_420_888:
        return feature_is_enabled(kFeature_YUV420888toNV21);
    case FRAMEWORK_FORMAT_YV12:
        return false;
    default:
        FATAL("Invalid for format:%d", format);
        return false;
    }
}

enum class YUVInterleaveDirection {
    VU = 0,
    UV = 1,
};

YUVInterleaveDirection getInterleaveDirection(FrameworkFormat format) {
    if (!isInterleaved(format)) {
        FATAL("Format:%d not interleaved", format);
    }

    switch (format) {
    case FRAMEWORK_FORMAT_NV12:
    case FRAMEWORK_FORMAT_P010:
        return YUVInterleaveDirection::UV;
    case FRAMEWORK_FORMAT_YUV_420_888:
        if (feature_is_enabled(kFeature_YUV420888toNV21)) {
            return YUVInterleaveDirection::VU;
        }
        FATAL("Format:%d not interleaved", format);
        return YUVInterleaveDirection::UV;
    case FRAMEWORK_FORMAT_YV12:
    default:
        FATAL("Format:%d not interleaved", format);
        return YUVInterleaveDirection::UV;
    }
}

GLint getGlTextureFormat(FrameworkFormat format, YUVPlane plane) {
    switch (format) {
    case FRAMEWORK_FORMAT_YV12:
        switch (plane) {
        case YUVPlane::Y:
        case YUVPlane::U:
        case YUVPlane::V:
            return GL_R8;
        case YUVPlane::UV:
            FATAL("Invalid plane:%d for format:%d", plane, format);
            return 0;
        }
    case FRAMEWORK_FORMAT_YUV_420_888:
        if (feature_is_enabled(kFeature_YUV420888toNV21)) {
            switch (plane) {
            case YUVPlane::Y:
                return GL_R8;
            case YUVPlane::UV:
                return GL_RG8;
            case YUVPlane::U:
            case YUVPlane::V:
                FATAL("Invalid plane:%d for format:%d", plane, format);
                return 0;
            }
        } else {
            switch (plane) {
            case YUVPlane::Y:
            case YUVPlane::U:
            case YUVPlane::V:
                return GL_R8;
            case YUVPlane::UV:
                FATAL("Invalid plane:%d for format:%d", plane, format);
                return 0;
            }
        }
    case FRAMEWORK_FORMAT_NV12:
        switch (plane) {
        case YUVPlane::Y:
            return GL_R8;
        case YUVPlane::UV:
            return GL_RG8;
        case YUVPlane::U:
        case YUVPlane::V:
            FATAL("Invalid plane:%d for format:%d", plane, format);
            return 0;
        }
    case FRAMEWORK_FORMAT_P010:
        switch (plane) {
        case YUVPlane::Y:
            return GL_R16UI;
        case YUVPlane::UV:
            return GL_RG16UI;
        case YUVPlane::U:
        case YUVPlane::V:
            FATAL("Invalid plane:%d for format:%d", plane, format);
            return 0;
        }
    default:
        FATAL("Invalid format:%d", format);
        return 0;
    }
}

GLenum getGlPixelFormat(FrameworkFormat format, YUVPlane plane) {
    switch (format) {
    case FRAMEWORK_FORMAT_YV12:
        switch (plane) {
        case YUVPlane::Y:
        case YUVPlane::U:
        case YUVPlane::V:
            return GL_RED;
        case YUVPlane::UV:
            FATAL("Invalid plane:%d for format:%d", plane, format);
            return 0;
        }
    case FRAMEWORK_FORMAT_YUV_420_888:
        if (feature_is_enabled(kFeature_YUV420888toNV21)) {
            switch (plane) {
            case YUVPlane::Y:
                return GL_RED;
            case YUVPlane::UV:
                return GL_RG;
            case YUVPlane::U:
            case YUVPlane::V:
                FATAL("Invalid plane:%d for format:%d", plane, format);
                return 0;
            }
        } else {
            switch (plane) {
            case YUVPlane::Y:
            case YUVPlane::U:
            case YUVPlane::V:
                return GL_RED;
            case YUVPlane::UV:
                FATAL("Invalid plane:%d for format:%d", plane, format);
                return 0;
            }
        }
    case FRAMEWORK_FORMAT_NV12:
        switch (plane) {
        case YUVPlane::Y:
            return GL_RED;
        case YUVPlane::UV:
            return GL_RG;
        case YUVPlane::U:
        case YUVPlane::V:
            FATAL("Invalid plane:%d for format:%d", plane, format);
            return 0;
        }
    case FRAMEWORK_FORMAT_P010:
        switch (plane) {
        case YUVPlane::Y:
            return GL_RED_INTEGER;
        case YUVPlane::UV:
            return GL_RG_INTEGER;
        case YUVPlane::U:
        case YUVPlane::V:
            FATAL("Invalid plane:%d for format:%d", plane, format);
            return 0;
        }
    default:
        FATAL("Invalid format:%d", format);
        return 0;
    }
}

GLsizei getGlPixelType(FrameworkFormat format, YUVPlane plane) {
    switch (format) {
    case FRAMEWORK_FORMAT_YV12:
        switch (plane) {
        case YUVPlane::Y:
        case YUVPlane::U:
        case YUVPlane::V:
            return GL_UNSIGNED_BYTE;
        case YUVPlane::UV:
            FATAL("Invalid plane:%d for format:%d", plane, format);
            return 0;
        }
    case FRAMEWORK_FORMAT_YUV_420_888:
        if (feature_is_enabled(kFeature_YUV420888toNV21)) {
            switch (plane) {
            case YUVPlane::Y:
            case YUVPlane::UV:
                return GL_UNSIGNED_BYTE;
            case YUVPlane::U:
            case YUVPlane::V:
                FATAL("Invalid plane:%d for format:%d", plane, format);
                return 0;
            }
        } else {
            switch (plane) {
            case YUVPlane::Y:
            case YUVPlane::U:
            case YUVPlane::V:
                return GL_UNSIGNED_BYTE;
            case YUVPlane::UV:
                FATAL("Invalid plane:%d for format:%d", plane, format);
                return 0;
            }
        }
    case FRAMEWORK_FORMAT_NV12:
        switch (plane) {
        case YUVPlane::Y:
        case YUVPlane::UV:
            return GL_UNSIGNED_BYTE;
        case YUVPlane::U:
        case YUVPlane::V:
            FATAL("Invalid plane:%d for format:%d", plane, format);
            return 0;
        }
    case FRAMEWORK_FORMAT_P010:
        switch (plane) {
        case YUVPlane::Y:
        case YUVPlane::UV:
            return GL_UNSIGNED_SHORT;
        case YUVPlane::U:
        case YUVPlane::V:
            FATAL("Invalid plane:%d for format:%d", plane, format);
            return 0;
        }
    default:
        FATAL("Invalid format:%d", format);
        return 0;
    }
}

// NV12 and YUV420 are all packed
static void NV12ToYUV420PlanarInPlaceConvert(int nWidth,
                                             int nHeight,
                                             uint8_t* pFrame,
                                             uint8_t* pQuad) {
    std::vector<uint8_t> tmp;
    if (pQuad == nullptr) {
        tmp.resize(nWidth * nHeight / 4);
        pQuad = tmp.data();
    }
    int nPitch = nWidth;
    uint8_t *puv = pFrame + nPitch * nHeight, *pu = puv,
            *pv = puv + nPitch * nHeight / 4;
    for (int y = 0; y < nHeight / 2; y++) {
        for (int x = 0; x < nWidth / 2; x++) {
            pu[y * nPitch / 2 + x] = puv[y * nPitch + x * 2];
            pQuad[y * nWidth / 2 + x] = puv[y * nPitch + x * 2 + 1];
        }
    }
    memcpy(pv, pQuad, nWidth * nHeight / 4);
}

inline uint32_t alignToPower2(uint32_t val, uint32_t align) {
    return (val + (align - 1)) & ~(align - 1);
}

// getYUVOffsets(), given a YUV-formatted buffer that is arranged
// according to the spec
// https://developer.android.com/reference/android/graphics/ImageFormat.html#YUV
// In particular, Android YUV widths are aligned to 16 pixels.
// Inputs:
// |yv12|: the YUV-formatted buffer
// Outputs:
// |yOffset|: offset into |yv12| of the start of the Y component
// |uOffset|: offset into |yv12| of the start of the U component
// |vOffset|: offset into |yv12| of the start of the V component
static void getYUVOffsets(int width,
                          int height,
                          FrameworkFormat format,
                          uint32_t* yOffset,
                          uint32_t* uOffset,
                          uint32_t* vOffset,
                          uint32_t* yWidth,
                          uint32_t* cWidth) {
    uint32_t yStride, cStride, cHeight, cSize;
    switch (format) {
    case FRAMEWORK_FORMAT_YV12:
        // Luma stride is 32 bytes aligned.
        yStride = alignToPower2(width, 32);
        // Chroma stride is 16 bytes aligned.
        cStride = alignToPower2(yStride, 16);
        cHeight = height / 2;
        cSize = cStride * cHeight;
        *yOffset = 0;
        *vOffset = yStride * height;
        *uOffset = (*vOffset) + cSize;
        *yWidth = yStride;
        *cWidth = cStride;
        break;
    case FRAMEWORK_FORMAT_YUV_420_888:
        if (feature_is_enabled(kFeature_YUV420888toNV21)) {
            yStride = width;
            cStride = yStride;
            cHeight = height / 2;
            *yOffset = 0;
            *vOffset = yStride * height;
            *uOffset = (*vOffset) + 1;
            *yWidth = yStride;
            *cWidth = cStride / 2;
        } else {
            yStride = width;
            cStride = yStride / 2;
            cHeight = height / 2;
            cSize = cStride * cHeight;
            *yOffset = 0;
            *uOffset = yStride * height;
            *vOffset = (*uOffset) + cSize;
            *yWidth = yStride;
            *cWidth = cStride;
        }
        break;
    case FRAMEWORK_FORMAT_NV12:
        yStride = width;
        cStride = yStride;
        cHeight = height / 2;
        cSize = cStride * cHeight;
        *yOffset = 0;
        *uOffset = yStride * height;
        *vOffset = (*uOffset) + 1;
        *yWidth = yStride;
        *cWidth = cStride / 2;
        break;
    case FRAMEWORK_FORMAT_P010:
        *yWidth = width;
        *cWidth = width / 2;
        yStride = width * /*bytes per pixel=*/2;
        cStride = *cWidth * /*bytes per pixel=*/2;
        cHeight = height / 2;
        cSize = cStride * cHeight;
        *yOffset = 0;
        *uOffset = yStride * height;
        *vOffset = (*uOffset) + 2;
        break;
    case FRAMEWORK_FORMAT_GL_COMPATIBLE:
        FATAL("Input not a YUV format! (FRAMEWORK_FORMAT_GL_COMPATIBLE)");
    default:
        FATAL("Unknown format: 0x%x", format);
    }
}

// Allocates an OpenGL texture that is large enough for a single plane of
// a YUV buffer of the given format and returns the texture name in the
// `outTextureName` argument.
void YUVConverter::createYUVGLTex(GLenum textureUnit,
                                  GLsizei width,
                                  GLsizei height,
                                  FrameworkFormat format,
                                  YUVPlane plane,
                                  GLuint* outTextureName) {
    YUV_DEBUG_LOG("w:%d h:%d format:%d plane:%d", width, height, format, plane);

    s_gles2.glActiveTexture(textureUnit);
    s_gles2.glGenTextures(1, outTextureName);
    s_gles2.glBindTexture(GL_TEXTURE_2D, *outTextureName);
    s_gles2.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    s_gles2.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    GLint unprevAlignment = 0;
    s_gles2.glGetIntegerv(GL_UNPACK_ALIGNMENT, &unprevAlignment);
    s_gles2.glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    const GLint textureFormat = getGlTextureFormat(format, plane);
    const GLenum pixelFormat = getGlPixelFormat(format, plane);
    const GLenum pixelType = getGlPixelType(format, plane);
    s_gles2.glTexImage2D(GL_TEXTURE_2D, 0, textureFormat, width, height, 0, pixelFormat, pixelType, NULL);
    s_gles2.glPixelStorei(GL_UNPACK_ALIGNMENT, unprevAlignment);
    s_gles2.glActiveTexture(GL_TEXTURE0);
}

static void readYUVTex(GLuint tex, FrameworkFormat format, YUVPlane plane, void* pixels) {
    YUV_DEBUG_LOG("format%d plane:%d pixels:%p", format, plane, pixels);

    GLuint prevTexture = 0;
    s_gles2.glGetIntegerv(GL_TEXTURE_BINDING_2D, (GLint*)&prevTexture);
    s_gles2.glBindTexture(GL_TEXTURE_2D, tex);
    GLint prevAlignment = 0;
    s_gles2.glGetIntegerv(GL_PACK_ALIGNMENT, &prevAlignment);
    s_gles2.glPixelStorei(GL_PACK_ALIGNMENT, 1);
    const GLenum pixelFormat = getGlPixelFormat(format, plane);
    const GLenum pixelType = getGlPixelType(format, plane);
    if (s_gles2.glGetTexImage) {
        s_gles2.glGetTexImage(GL_TEXTURE_2D, 0, pixelFormat, pixelType, pixels);
    } else {
        YUV_DEBUG_LOG("empty glGetTexImage");
    }

    s_gles2.glPixelStorei(GL_PACK_ALIGNMENT, prevAlignment);
    s_gles2.glBindTexture(GL_TEXTURE_2D, prevTexture);
}

// Updates a given YUV buffer's plane texture at the coordinates
// (x, y, width, height), with the raw YUV data in |pixels|.  We
// cannot view the result properly until after conversion; this is
// to be used only as input to the conversion shader.
static void subUpdateYUVGLTex(GLenum texture_unit,
                              GLuint tex,
                              int x,
                              int y,
                              int width,
                              int height,
                              FrameworkFormat format,
                              YUVPlane plane,
                              const void* pixels) {
    YUV_DEBUG_LOG("x:%d y:%d w:%d h:%d format:%d plane:%d", x, y, width, height, format, plane);

    const GLenum pixelFormat = getGlPixelFormat(format, plane);
    const GLenum pixelType = getGlPixelType(format, plane);

    s_gles2.glActiveTexture(texture_unit);
    s_gles2.glBindTexture(GL_TEXTURE_2D, tex);
    GLint unprevAlignment = 0;
    s_gles2.glGetIntegerv(GL_UNPACK_ALIGNMENT, &unprevAlignment);
    s_gles2.glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    s_gles2.glTexSubImage2D(GL_TEXTURE_2D, 0, x, y, width, height, pixelFormat, pixelType, pixels);
    s_gles2.glPixelStorei(GL_UNPACK_ALIGNMENT, unprevAlignment);
    s_gles2.glActiveTexture(GL_TEXTURE0);
}

void YUVConverter::createYUVGLShader() {
    YUV_DEBUG_LOG("format:%d", mFormat);

    // P010 needs uint samplers.
    if (mFormat == FRAMEWORK_FORMAT_P010 && !mHasGlsl3Support) {
        return;
    }

    static const char kVertShader[] = R"(
precision highp float;
attribute mediump vec4 aPosition;
attribute highp vec2 aTexCoord;
varying highp vec2 vTexCoord;
void main(void) {
  gl_Position = aPosition;
  vTexCoord = aTexCoord;
}
    )";

    static const char kFragShaderVersion3[] = R"(#version 300 es)";

    static const char kFragShaderBegin[] = R"(
precision highp float;

varying highp vec2 vTexCoord;

uniform highp float uYWidthCutoff;
uniform highp float uCWidthCutoff;
    )";

    static const char kSamplerUniforms[] = R"(
uniform sampler2D uSamplerY;
uniform sampler2D uSamplerU;
uniform sampler2D uSamplerV;
    )";
    static const char kSamplerUniformsUint[] = R"(
uniform usampler2D uSamplerY;
uniform usampler2D uSamplerU;
uniform usampler2D uSamplerV;
    )";

    static const char kFragShaderMainBegin[] = R"(
void main(void) {
    highp vec2 yTexCoords = vTexCoord;
    highp vec2 uvTexCoords = vTexCoord;

    // For textures with extra padding for alignment (e.g. YV12 pads to 16),
    // scale the coordinates to only sample from the non-padded area.
    yTexCoords.x *= uYWidthCutoff;
    uvTexCoords.y *= uCWidthCutoff;

    highp vec3 yuv;
)";

    static const char kSampleY[] = R"(
    yuv[0] = texture2D(uSamplerY, yTexCoords).r;
    )";
    static const char kSampleUV[] = R"(
    yuv[1] = texture2D(uSamplerU, uvTexCoords).r;
    yuv[2] = texture2D(uSamplerV, uvTexCoords).r;
    )";
    static const char kSampleInterleavedUV[] = R"(
    // Note: uSamplerU and vSamplerV refer to the same texture.
    yuv[1] = texture2D(uSamplerU, uvTexCoords).r;
    yuv[2] = texture2D(uSamplerV, uvTexCoords).g;
    )";
    static const char kSampleInterleavedVU[] = R"(
    // Note: uSamplerU and vSamplerV refer to the same texture.
    yuv[1] = texture2D(uSamplerU, uvTexCoords).g;
    yuv[2] = texture2D(uSamplerV, uvTexCoords).r;
    )";

    static const char kSampleP010[] = R"(
        uint yRaw = texture(uSamplerY, yTexCoords).r;
        uint uRaw = texture(uSamplerU, uvTexCoords).r;
        uint vRaw = texture(uSamplerV, uvTexCoords).g;

        // P010 values are stored in the upper 10-bits of 16-bit unsigned shorts.
        yuv[0] = float(yRaw >> 6) / 1023.0;
        yuv[1] = float(uRaw >> 6) / 1023.0;
        yuv[2] = float(vRaw >> 6) / 1023.0;
    )";

    static const char kFragShaderMainEnd[] = R"(
    yuv[0] = yuv[0] - 0.0625;
    yuv[1] = 0.96 * (yuv[1] - 0.5);
    yuv[2] = (yuv[2] - 0.5);

    highp float yscale = 1.1643835616438356;
    highp vec3 rgb = mat3(            yscale,               yscale,            yscale,
                                           0, -0.39176229009491365, 2.017232142857143,
                          1.5960267857142856,  -0.8129676472377708,                 0) * yuv;

    gl_FragColor = vec4(rgb, 1.0);
}
    )";

    std::string vertShaderSource(kVertShader);
    std::string fragShaderSource;

    if (mFormat == FRAMEWORK_FORMAT_P010) {
        fragShaderSource += kFragShaderVersion3;
    }

    fragShaderSource += kFragShaderBegin;

    if (mFormat == FRAMEWORK_FORMAT_P010) {
        fragShaderSource += kSamplerUniformsUint;
    } else {
        fragShaderSource += kSamplerUniforms;
    }

    fragShaderSource += kFragShaderMainBegin;

    switch (mFormat) {
    case FRAMEWORK_FORMAT_NV12:
    case FRAMEWORK_FORMAT_YUV_420_888:
    case FRAMEWORK_FORMAT_YV12:
        fragShaderSource += kSampleY;
        if (isInterleaved(mFormat)) {
            if (getInterleaveDirection(mFormat) == YUVInterleaveDirection::UV) {
                fragShaderSource += kSampleInterleavedUV;
            } else {
                fragShaderSource += kSampleInterleavedVU;
            }
        } else {
            fragShaderSource += kSampleUV;
        }
        break;
    case FRAMEWORK_FORMAT_P010:
        fragShaderSource += kSampleP010;
        break;
    default:
        FATAL("%s: invalid format:%d", __FUNCTION__, mFormat);
        return;
    }

    fragShaderSource += kFragShaderMainEnd;

    YUV_DEBUG_LOG("format:%d vert-source:%s frag-source:%s", mFormat, vertShaderSource.c_str(), fragShaderSource.c_str());

    const GLchar* const vertShaderSourceChars = vertShaderSource.c_str();
    const GLchar* const fragShaderSourceChars = fragShaderSource.c_str();
    const GLint vertShaderSourceLen = vertShaderSource.length();
    const GLint fragShaderSourceLen = fragShaderSource.length();

    GLuint vertShader = s_gles2.glCreateShader(GL_VERTEX_SHADER);
    GLuint fragShader = s_gles2.glCreateShader(GL_FRAGMENT_SHADER);
    s_gles2.glShaderSource(vertShader, 1, &vertShaderSourceChars, &vertShaderSourceLen);
    s_gles2.glShaderSource(fragShader, 1, &fragShaderSourceChars, &fragShaderSourceLen);
    s_gles2.glCompileShader(vertShader);
    s_gles2.glCompileShader(fragShader);

    for (GLuint shader : {vertShader, fragShader}) {
        GLint status = GL_FALSE;
        s_gles2.glGetShaderiv(shader, GL_COMPILE_STATUS, &status);
        if (status == GL_FALSE) {
            GLchar error[1024];
            s_gles2.glGetShaderInfoLog(shader, sizeof(error), nullptr, &error[0]);
            FATAL("Failed to compile YUV conversion shader: %s", error);
            s_gles2.glDeleteShader(shader);
            return;
        }
    }

    mProgram = s_gles2.glCreateProgram();
    s_gles2.glAttachShader(mProgram, vertShader);
    s_gles2.glAttachShader(mProgram, fragShader);
    s_gles2.glLinkProgram(mProgram);

    GLint status = GL_FALSE;
    s_gles2.glGetProgramiv(mProgram, GL_LINK_STATUS, &status);
    if (status == GL_FALSE) {
        GLchar error[1024];
        s_gles2.glGetProgramInfoLog(mProgram, sizeof(error), 0, &error[0]);
        FATAL("Failed to link YUV conversion program: %s", error);
        s_gles2.glDeleteProgram(mProgram);
        mProgram = 0;
        return;
    }

    mUniformLocYWidthCutoff = s_gles2.glGetUniformLocation(mProgram, "uYWidthCutoff");
    mUniformLocCWidthCutoff = s_gles2.glGetUniformLocation(mProgram, "uCWidthCutoff");
    mUniformLocSamplerY = s_gles2.glGetUniformLocation(mProgram, "uSamplerY");
    mUniformLocSamplerU = s_gles2.glGetUniformLocation(mProgram, "uSamplerU");
    mUniformLocSamplerV = s_gles2.glGetUniformLocation(mProgram, "uSamplerV");
    mAttributeLocPos = s_gles2.glGetAttribLocation(mProgram, "aPosition");
    mAttributeLocTexCoord = s_gles2.glGetAttribLocation(mProgram, "aTexCoord");

    s_gles2.glDeleteShader(vertShader);
    s_gles2.glDeleteShader(fragShader);
}

void YUVConverter::createYUVGLFullscreenQuad() {
    s_gles2.glGenBuffers(1, &mQuadVertexBuffer);
    s_gles2.glGenBuffers(1, &mQuadIndexBuffer);

    static const float kVertices[] = {
        +1, -1, +0, +1, +0,
        +1, +1, +0, +1, +1,
        -1, +1, +0, +0, +1,
        -1, -1, +0, +0, +0,
    };

    static const GLubyte kIndices[] = { 0, 1, 2, 2, 3, 0 };

    s_gles2.glBindBuffer(GL_ARRAY_BUFFER, mQuadVertexBuffer);
    s_gles2.glBufferData(GL_ARRAY_BUFFER, sizeof(kVertices), kVertices, GL_STATIC_DRAW);
    s_gles2.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, mQuadIndexBuffer);
    s_gles2.glBufferData(GL_ELEMENT_ARRAY_BUFFER, sizeof(kIndices), kIndices, GL_STATIC_DRAW);
}

static void doYUVConversionDraw(GLuint program,
                                GLint uniformLocYWidthCutoff,
                                GLint uniformLocCWidthCutoff,
                                GLint uniformLocYSampler,
                                GLint uniformLocUSampler,
                                GLint uniformLocVSampler,
                                GLint attributeLocTexCoord,
                                GLint attributeLocPos,
                                GLuint quadVertexBuffer,
                                GLuint quadIndexBuffer,
                                float uYWidthCutoff,
                                float uCWidthCutoff) {
    const GLsizei kVertexAttribStride = 5 * sizeof(GL_FLOAT);
    const GLvoid* kVertexAttribPosOffset = (GLvoid*)0;
    const GLvoid* kVertexAttribCoordOffset = (GLvoid*)(3 * sizeof(GL_FLOAT));

    s_gles2.glUseProgram(program);

    s_gles2.glUniform1f(uniformLocYWidthCutoff, uYWidthCutoff);
    s_gles2.glUniform1f(uniformLocCWidthCutoff, uCWidthCutoff);

    s_gles2.glUniform1i(uniformLocYSampler, 0);
    s_gles2.glUniform1i(uniformLocUSampler, 1);
    s_gles2.glUniform1i(uniformLocVSampler, 2);

    s_gles2.glBindBuffer(GL_ARRAY_BUFFER, quadVertexBuffer);
    s_gles2.glEnableVertexAttribArray(attributeLocPos);
    s_gles2.glEnableVertexAttribArray(attributeLocTexCoord);

    s_gles2.glVertexAttribPointer(attributeLocPos, 3, GL_FLOAT, false,
                                  kVertexAttribStride,
                                  kVertexAttribPosOffset);
    s_gles2.glVertexAttribPointer(attributeLocTexCoord, 2, GL_FLOAT, false,
                                  kVertexAttribStride,
                                  kVertexAttribCoordOffset);

    s_gles2.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, quadIndexBuffer);
    s_gles2.glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_BYTE, 0);

    s_gles2.glDisableVertexAttribArray(attributeLocPos);
    s_gles2.glDisableVertexAttribArray(attributeLocTexCoord);
}

// initialize(): allocate GPU memory for YUV components,
// and create shaders and vertex data.
YUVConverter::YUVConverter(int width, int height, FrameworkFormat format)
    : mWidth(width),
      mHeight(height),
      mFormat(format),
      mColorBufferFormat(format) {}

void YUVConverter::init(int width, int height, FrameworkFormat format) {
    YUV_DEBUG_LOG("w:%d h:%d format:%d", width, height, format);

    uint32_t yOffset, uOffset, vOffset, ywidth, cwidth, cheight;
    getYUVOffsets(width, height, mFormat, &yOffset, &uOffset, &vOffset, &ywidth, &cwidth);
    cheight = height / 2;

    mWidth = width;
    mHeight = height;
    if (!mTextureY) {
        createYUVGLTex(GL_TEXTURE0, ywidth, height, mFormat, YUVPlane::Y, &mTextureY);
    }
    if (isInterleaved(mFormat)) {
        if (!mTextureU) {
            createYUVGLTex(GL_TEXTURE1, cwidth, cheight, mFormat, YUVPlane::UV, &mTextureU);
            mTextureV = mTextureU;
        }
    } else {
        if (!mTextureU) {
            createYUVGLTex(GL_TEXTURE1, cwidth, cheight, mFormat, YUVPlane::U, &mTextureU);
        }
        if (!mTextureV) {
            createYUVGLTex(GL_TEXTURE2, cwidth, cheight, mFormat, YUVPlane::V, &mTextureV);
        }
    }

    int glesMajor;
    int glesMinor;
    emugl::getGlesVersion(&glesMajor, &glesMinor);
    mHasGlsl3Support = glesMajor >= 3;
    YUV_DEBUG_LOG("YUVConverter has GLSL ES 3 support:%s (major:%d minor:%d", (mHasGlsl3Support ? "yes" : "no"), glesMajor, glesMinor);

    createYUVGLShader();
    createYUVGLFullscreenQuad();
}

void YUVConverter::saveGLState() {
    s_gles2.glGetFloatv(GL_VIEWPORT, mCurrViewport);
    s_gles2.glGetIntegerv(GL_ACTIVE_TEXTURE, &mCurrTexUnit);
    s_gles2.glGetIntegerv(GL_TEXTURE_BINDING_2D, &mCurrTexBind);
    s_gles2.glGetIntegerv(GL_CURRENT_PROGRAM, &mCurrProgram);
    s_gles2.glGetIntegerv(GL_ARRAY_BUFFER_BINDING, &mCurrVbo);
    s_gles2.glGetIntegerv(GL_ELEMENT_ARRAY_BUFFER_BINDING, &mCurrIbo);
}

void YUVConverter::restoreGLState() {
    s_gles2.glViewport(mCurrViewport[0], mCurrViewport[1],
                       mCurrViewport[2], mCurrViewport[3]);
    s_gles2.glActiveTexture(mCurrTexUnit);
    s_gles2.glUseProgram(mCurrProgram);
    s_gles2.glBindBuffer(GL_ARRAY_BUFFER, mCurrVbo);
    s_gles2.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, mCurrIbo);
}

uint32_t YUVConverter::getDataSize() {
    uint32_t align = (mFormat == FRAMEWORK_FORMAT_YV12) ? 16 : 1;
    uint32_t yStride = (mWidth + (align - 1)) & ~(align - 1);
    uint32_t uvStride = (yStride / 2 + (align - 1)) & ~(align - 1);
    uint32_t uvHeight = mHeight / 2;
    uint32_t dataSize = yStride * mHeight + 2 * (uvHeight * uvStride);
    return dataSize;
}

void YUVConverter::readPixels(uint8_t* pixels, uint32_t pixels_size) {
    YUV_DEBUG_LOG("w:%d h:%d format:%d pixels:%p pixels-size:%d", mWidth, mHeight, mFormat, pixels, pixels_size);

    uint32_t yOffset, uOffset, vOffset, ywidth, cwidth;
    getYUVOffsets(mWidth, mHeight, mFormat, &yOffset, &uOffset, &vOffset, &ywidth, &cwidth);

    if (isInterleaved(mFormat)) {
        readYUVTex(mTextureV, mFormat, YUVPlane::UV, pixels + std::min(uOffset, vOffset));
    } else {
        readYUVTex(mTextureU, mFormat, YUVPlane::U, pixels + uOffset);
        readYUVTex(mTextureV, mFormat, YUVPlane::V, pixels + vOffset);
    }

    if (mFormat == FRAMEWORK_FORMAT_NV12 && mColorBufferFormat == FRAMEWORK_FORMAT_YUV_420_888) {
        NV12ToYUV420PlanarInPlaceConvert(mWidth, mHeight, pixels, pixels);
    }

    // Read the Y plane last because so that we can use it as a scratch space.
    readYUVTex(mTextureY, mFormat, YUVPlane::Y, pixels + yOffset);
}

void YUVConverter::swapTextures(uint32_t type, uint32_t* textures) {
    FrameworkFormat format = static_cast<FrameworkFormat>(type);

    if (isInterleaved(format)) {
        std::swap(textures[0], mTextureY);
        std::swap(textures[1], mTextureU);
        mTextureV = mTextureU;
    } else {
        std::swap(textures[0], mTextureY);
        std::swap(textures[1], mTextureU);
        std::swap(textures[2], mTextureV);
    }

    mFormat = format;
}

void YUVConverter::drawConvert(int x, int y, int width, int height, const char* pixels) {
    YUV_DEBUG_LOG("x:%d y:%d w:%d h:%d", x, y, width, height);

    saveGLState();
    if (pixels && (width != mWidth || height != mHeight)) {
        reset();
    }

    if (mProgram == 0) {
        init(width, height, mFormat);
    }

    if (mFormat == FRAMEWORK_FORMAT_P010 && !mHasGlsl3Support) {
        // TODO: perhaps fallback to just software conversion.
        return;
    }

    s_gles2.glViewport(x, y, width, height);
    uint32_t yOffset, uOffset, vOffset, ywidth, cwidth, cheight;
    getYUVOffsets(width, height, mFormat, &yOffset, &uOffset, &vOffset, &ywidth, &cwidth);
    cheight = height / 2;
    updateCutoffs(width, ywidth, width / 2, cwidth);

    if (pixels) {
        subUpdateYUVGLTex(GL_TEXTURE0, mTextureY, x, y, ywidth, height, mFormat, YUVPlane::Y, pixels + yOffset);
        if (isInterleaved(mFormat)) {
            subUpdateYUVGLTex(GL_TEXTURE1, mTextureU, x, y, cwidth, cheight, mFormat, YUVPlane::UV, pixels + std::min(uOffset, vOffset));
        } else {
            subUpdateYUVGLTex(GL_TEXTURE1, mTextureU, x, y, cwidth, cheight, mFormat, YUVPlane::U, pixels + uOffset);
            subUpdateYUVGLTex(GL_TEXTURE2, mTextureV, x, y, cwidth, cheight, mFormat, YUVPlane::V, pixels + vOffset);
        }
    } else {
        // special case: draw from texture, only support NV12 for now
        // as cuvid's native format is NV12.
        // TODO: add more formats if there are such needs in the future.
        assert(mFormat == FRAMEWORK_FORMAT_NV12);
    }

    s_gles2.glActiveTexture(GL_TEXTURE0);
    s_gles2.glBindTexture(GL_TEXTURE_2D, mTextureY);
    s_gles2.glActiveTexture(GL_TEXTURE1);
    s_gles2.glBindTexture(GL_TEXTURE_2D, mTextureU);
    s_gles2.glActiveTexture(GL_TEXTURE2);
    s_gles2.glBindTexture(GL_TEXTURE_2D, mTextureV);

    doYUVConversionDraw(mProgram,
                        mUniformLocYWidthCutoff,
                        mUniformLocCWidthCutoff,
                        mUniformLocSamplerY,
                        mUniformLocSamplerU,
                        mUniformLocSamplerV,
                        mAttributeLocTexCoord,
                        mAttributeLocPos,
                        mQuadVertexBuffer,
                        mQuadIndexBuffer,
                        mYWidthCutoff,
                        mCWidthCutoff);

    restoreGLState();
}

void YUVConverter::updateCutoffs(float width, float ywidth,
                                 float halfwidth, float cwidth) {
    switch (mFormat) {
    case FRAMEWORK_FORMAT_YV12:
        mYWidthCutoff = ((float)width) / ((float)ywidth);
        mCWidthCutoff = ((float)halfwidth) / ((float)cwidth);
        break;
    case FRAMEWORK_FORMAT_NV12:
    case FRAMEWORK_FORMAT_P010:
    case FRAMEWORK_FORMAT_YUV_420_888:
        mYWidthCutoff = 1.0f;
        mCWidthCutoff = 1.0f;
        break;
    case FRAMEWORK_FORMAT_GL_COMPATIBLE:
        FATAL("Input not a YUV format!");
    }
}

void YUVConverter::reset() {
    if (mQuadIndexBuffer) s_gles2.glDeleteBuffers(1, &mQuadIndexBuffer);
    if (mQuadVertexBuffer) s_gles2.glDeleteBuffers(1, &mQuadVertexBuffer);
    if (mProgram) s_gles2.glDeleteProgram(mProgram);
    if (mTextureY) s_gles2.glDeleteTextures(1, &mTextureY);
    if (isInterleaved(mFormat)) {
        if (mTextureU) s_gles2.glDeleteTextures(1, &mTextureU);
    } else {
        if (mTextureU) s_gles2.glDeleteTextures(1, &mTextureU);
        if (mTextureV) s_gles2.glDeleteTextures(1, &mTextureV);
    }
    mQuadIndexBuffer = 0;
    mQuadVertexBuffer = 0;
    mProgram = 0;
    mTextureY = 0;
    mTextureU = 0;
    mTextureV = 0;
}

YUVConverter::~YUVConverter() {
    reset();
}
