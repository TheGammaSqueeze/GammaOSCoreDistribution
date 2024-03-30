/*
 * Copyright (C) 2020 The Android Open Source Project
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

#ifndef DISPLAYCOLOR_GS101_H_
#define DISPLAYCOLOR_GS101_H_

#include <array>
#include <functional>
#include <memory>

#include <displaycolor/displaycolor.h>

namespace displaycolor {

/// An interface for accessing GS101 color management data.
class IDisplayColorGS101 : public IDisplayColorGeneric {
   private:
    /// Register data for matrices in DPP and DQE.
    template <typename T, size_t kDimensions>
    struct MatrixData {
        /**
         * DQE0_GAMMA_MATRIX_COEFF0..4[GAMMA_MATRIX_COEFF_xx]
         * DQE0_LINEAR_MATRIX_COEFF0..4[LINEAR_MATRIX_COEFF_xx]
         * DPP_HDR_LSI_L#_GM_COEF0..8[COEF], #(0..5)
         */
        std::array<T, kDimensions * kDimensions> coeffs{};

        /**
         * DQE0_GAMMA_MATRIX_OFFSET0..1[GAMMA_MATRIX_COEFF_n]
         * DQE0_LINEAR_MATRIX_OFFSET0..1[LINEAR_MATRIX_COEFF_n]
         * DPP_HDR_LSI_L#_GM_OFFS0..2[OFFS], #(0..5)
         */
        std::array<T, kDimensions> offsets{};
    };

   public:
    /**
     * @brief Interface for accessing data for DPP stages.
     *
     * Note that the data returned by this interface is applicable to both DPP
     * in DPU and the HDR blocks in G2D. These two IPs' register specs are
     * identical, with one caveat: While all G2D layers support display tone
     * mapping (DTM) for HDR10+, only DPP layers L1/L3/L5 support this stage.
     */
    struct IDpp {
     private:
        /// Register data for transfer function LUTs in DPP)
        template <typename XT, typename YT, size_t N>
        struct TransferFunctionData {
            /**
             * DPP_HDR_LSI_L#_EOTF_POSX0~64[POSXn], #(0..5), n(0..1)
             * DPP_HDR_LSI_L#_OETF_POSX0~16[POSXn], #(0..5), n(0..1)
             * DPP_HDR_LSI_L#_TM_POSX0~16[POSXn], #(1, 3, 5), n(0..1)
             */
            std::array<XT, N> posx;
            /**
             * DPP_HDR_LSI_L#_EOTF_POSY0~128[POSY0], #(0..5)
             * DPP_HDR_LSI_L#_OETF_POSY0~16[POSYn] #(0..5), n(0..1)
             * DPP_HDR_LSI_L#_TM_POSY0~32[POSY0], #(1, 3, 5)
             */
            std::array<YT, N> posy;
        };

        struct EotfConfigType {
            using XContainer = uint16_t;
            using YContainer = uint32_t;
            static constexpr size_t kLutLen = 129;

            TransferFunctionData<XContainer, YContainer, kLutLen> tf_data;
        };

        struct GmConfigType {
            using Container = uint32_t;
            static constexpr size_t kDimensions = 3;

            MatrixData<Container, kDimensions> matrix_data;
        };

        struct DtmConfigType {
            using XContainer = uint16_t;
            using YContainer = uint32_t;
            static constexpr size_t kLutLen = 33;

            TransferFunctionData<XContainer, YContainer, kLutLen> tf_data;
            uint16_t coeff_r;    // DPP_HDR_LSI_L#_TM_COEF[COEFR] #(1, 3, 5)
            uint16_t coeff_g;    // DPP_HDR_LSI_L#_TM_COEF[COEFG] #(1, 3, 5)
            uint16_t coeff_b;    // DPP_HDR_LSI_L#_TM_COEF[COEFB] #(1, 3, 5)
            uint16_t rng_x_min;  // DPP_HDR_LSI_L#_TM_RNGX[MINX] #(1, 3, 5)
            uint16_t rng_x_max;  // DPP_HDR_LSI_L#_TM_RNGX[MAXX] #(1, 3, 5)
            uint16_t rng_y_min;  // DPP_HDR_LSI_L#_TM_RNGY[MINY] #(1, 3, 5)
            uint16_t rng_y_max;  // DPP_HDR_LSI_L#_TM_RNGY[MAXY] #(1, 3, 5)
        };

        struct OetfConfigType {
            using XContainer = uint32_t;
            using YContainer = uint16_t;
            static constexpr size_t kLutLen = 33;

            TransferFunctionData<XContainer, YContainer, kLutLen> tf_data;
        };

     public:
        /// Register data for the EOTF LUT in DPP.
        using EotfData = DisplayStage<EotfConfigType>;

        /// Register data for the gamut mapping (GM) matrix in DPP.
        using GmData = DisplayStage<GmConfigType>;

        /**
         * @brief Register data for the DTM stage in DPP.
         *
         * Note that this data is only applicable to DPP in layers L1/L3/L5 and
         * G2D layers. Other DPPs do not support DTM. DTM data will be provided
         * for any layer whose DisplayScene::LayerColorData contains HDR dynamic
         * metadata. It is the caller's (typically HWComposer) responsibility to
         * validate layers and HW capabilities correctly, before calling this
         * API.
         */
        using DtmData = DisplayStage<DtmConfigType>;

        /// Register data for the OETF LUT in DPP.
        using OetfData = DisplayStage<OetfConfigType>;

        /// Get data for the EOTF LUT.
        virtual const EotfData& EotfLut() const = 0;

        /// Get data for the gamut mapping (GM) matrix.
        virtual const GmData& Gm() const = 0;

        /**
         * @brief Get data for the DTM LUT. Only used for HDR10+, and only
         * applicable to DPPs that support this functionality.
         */
        virtual const DtmData& Dtm() const = 0;

        /// Get data for the OETF LUT.
        virtual const OetfData& OetfLut() const = 0;

        /// Get the solid color
        virtual const Color SolidColor() const = 0;

        virtual ~IDpp() {}
    };

    /// Interface for accessing data for DQE stages.
    struct IDqe {
     private:
        /// 32-bit DQE dither register, same definition as in uapi
        struct DitherConfigType {
            uint8_t en : 1;
            uint8_t mode : 1;
            uint8_t frame_con : 1;
            uint8_t frame_offset : 2;
            uint8_t table_sel_r : 1;
            uint8_t table_sel_g : 1;
            uint8_t table_sel_b : 1;
            uint32_t reserved : 24;
        };

        struct DqeControlConfigType {
            /// DQE force 10bpc mode
            bool force_10bpc = false;

            /// flag to use cgc_dither
            bool cgc_dither_override = false;
            /// CGC dither register value
            union {
                DitherConfigType cgc_dither_reg = {};
                uint8_t cgc_dither; // only lowest 8 bit is used
            };

            /// flag to use disp_dither
            bool disp_dither_override = false;
            /// Display dither register value
            union {
                DitherConfigType disp_dither_reg = {};
                uint8_t disp_dither; // only lowest 8 bit is used
            };
        };

        struct DqeMatrixConfigType {
            using Container = uint16_t;
            static constexpr size_t kDimensions = 3;

            struct MatrixData<Container, kDimensions> matrix_data;
        };

        struct DegammaConfigType {
            using Container = uint16_t;
            static constexpr size_t kLutLen = 65;

            std::array<Container, kLutLen> values;
        };

        struct CgcConfigType {
            using Container = uint32_t;
            static constexpr size_t kChannelLutLen = 2457;

            /// DQE0_CGC_LUT_R_N{0-2456} (8 bit: 0~2047, 10 bit: 0~8191)
            std::array<Container, kChannelLutLen> r_values{};
            /// DQE0_CGC_LUT_G_N{0-2456} (8 bit: 0~2047, 10 bit: 0~8191)
            std::array<Container, kChannelLutLen> g_values{};
            /// DQE0_CGC_LUT_B_N{0-2456} (8 bit: 0~2047, 10 bit: 0~8191)
            std::array<Container, kChannelLutLen> b_values{};
        };

        struct RegammaConfigType {
            using Container = uint16_t;
            static constexpr size_t kChannelLutLen = 65;

            /// REGAMMA LUT_R_{00-64} (8 bit: 0~1024, 10 bit: 0~4096)
            std::array<Container, kChannelLutLen> r_values{};
            /// REGAMMA LUT_G_{00-64} (8 bit: 0~1024, 10 bit: 0~4096)
            std::array<Container, kChannelLutLen> g_values{};
            /// REGAMMA LUT_B_{00-64} (8 bit: 0~1024, 10 bit: 0~4096)
            std::array<Container, kChannelLutLen> b_values{};
        };

     public:
        /// DQE control data
        using DqeControlData = DisplayStage<DqeControlConfigType>;

        /// Register data for the gamma and linear matrices in DQE.
        using DqeMatrixData = DisplayStage<DqeMatrixConfigType>;

        /// Register data for the degamma LUT in DQE.
        using DegammaLutData = DisplayStage<DegammaConfigType>;

        /// Register data for CGC.
        using CgcData = DisplayStage<CgcConfigType>;

        /// Register data for the regamma LUT.
        using RegammaLutData = DisplayStage<RegammaConfigType>;

        /// Get DQE control data
        virtual const DqeControlData& DqeControl() const = 0;

        /// Get data for the gamma-space matrix.
        virtual const DqeMatrixData& GammaMatrix() const = 0;

        /// Get data for the 1D de-gamma LUT (EOTF).
        virtual const DegammaLutData& DegammaLut() const = 0;

        /// Get data for the linear-space matrix.
        virtual const DqeMatrixData& LinearMatrix() const = 0;

        /// Get data for the Color Gamut Conversion stage (3D LUT).
        virtual const CgcData& Cgc() const = 0;

        /// Get data for the 3x1D re-gamma LUTa (OETF).
        virtual const RegammaLutData& RegammaLut() const = 0;

        virtual ~IDqe() {}
    };

    /// Interface for accessing particular display color data
    struct IDisplayPipelineData {
        /**
         * @brief Get handles to Display Pre-Processor (DPP) data accessors.
         *
         * The order of the returned DPP handles match the order of the
         * LayerColorData provided as part of struct DisplayScene and
         * IDisplayColorGeneric::Update().
         */
        virtual std::vector<std::reference_wrapper<const IDpp>> Dpp() const = 0;

        /// Get a handle to Display Quality Enhancer (DQE) data accessors.
        virtual const IDqe& Dqe() const = 0;

        /// Get a handle to panel data accessors
        virtual const IPanel& Panel() const = 0;

        virtual ~IDisplayPipelineData() {}
    };

    /// Get pipeline color data for specified display type
    virtual const IDisplayPipelineData* GetPipelineData(
        DisplayType display) const = 0;

    virtual ~IDisplayColorGS101() {}
};

extern "C" {

/// Get the GS101 instance.
IDisplayColorGS101* GetDisplayColorGS101(const std::vector<DisplayInfo> &display_info);
}

}  // namespace displaycolor

#endif  // DISPLAYCOLOR_GS101_H_
