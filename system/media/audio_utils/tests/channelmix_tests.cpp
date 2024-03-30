/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <audio_utils/ChannelMix.h>
#include <audio_utils/Statistics.h>
#include <gtest/gtest.h>
#include <log/log.h>

static constexpr audio_channel_mask_t kChannelPositionMasks[] = {
    AUDIO_CHANNEL_OUT_FRONT_LEFT, // Legacy: the ChannelMix effect treats MONO as FRONT_LEFT only.
                                  // The AudioMixer interprets MONO as a special case requiring
                                  // channel replication, bypassing the ChannelMix effect.
    AUDIO_CHANNEL_OUT_FRONT_CENTER,
    AUDIO_CHANNEL_OUT_STEREO,
    AUDIO_CHANNEL_OUT_2POINT1,
    AUDIO_CHANNEL_OUT_2POINT0POINT2,
    AUDIO_CHANNEL_OUT_QUAD, // AUDIO_CHANNEL_OUT_QUAD_BACK
    AUDIO_CHANNEL_OUT_QUAD_SIDE,
    AUDIO_CHANNEL_OUT_SURROUND,
    AUDIO_CHANNEL_OUT_2POINT1POINT2,
    AUDIO_CHANNEL_OUT_3POINT0POINT2,
    AUDIO_CHANNEL_OUT_PENTA,
    AUDIO_CHANNEL_OUT_3POINT1POINT2,
    AUDIO_CHANNEL_OUT_5POINT1, // AUDIO_CHANNEL_OUT_5POINT1_BACK
    AUDIO_CHANNEL_OUT_5POINT1_SIDE,
    AUDIO_CHANNEL_OUT_6POINT1,
    AUDIO_CHANNEL_OUT_5POINT1POINT2,
    AUDIO_CHANNEL_OUT_7POINT1,
    AUDIO_CHANNEL_OUT_5POINT1POINT4,
    AUDIO_CHANNEL_OUT_7POINT1POINT2,
    AUDIO_CHANNEL_OUT_7POINT1POINT4,
    AUDIO_CHANNEL_OUT_13POINT_360RA,
    AUDIO_CHANNEL_OUT_22POINT2,
    audio_channel_mask_t(AUDIO_CHANNEL_OUT_22POINT2
            | AUDIO_CHANNEL_OUT_FRONT_WIDE_LEFT | AUDIO_CHANNEL_OUT_FRONT_WIDE_RIGHT),
};

constexpr float COEF_25 = 0.2508909536f;
constexpr float COEF_35 = 0.3543928915f;
constexpr float COEF_36 = 0.3552343859f;
constexpr float COEF_61 = 0.6057043428f;

constexpr inline float kScaleFromChannelIdxLeft[] = {
    1.f,       // AUDIO_CHANNEL_OUT_FRONT_LEFT            = 0x1u,
    0.f,       // AUDIO_CHANNEL_OUT_FRONT_RIGHT           = 0x2u,
    M_SQRT1_2, // AUDIO_CHANNEL_OUT_FRONT_CENTER          = 0x4u,
    0.5f,      // AUDIO_CHANNEL_OUT_LOW_FREQUENCY         = 0x8u,
    M_SQRT1_2, // AUDIO_CHANNEL_OUT_BACK_LEFT             = 0x10u,
    0.f,       // AUDIO_CHANNEL_OUT_BACK_RIGHT            = 0x20u,
    COEF_61,   // AUDIO_CHANNEL_OUT_FRONT_LEFT_OF_CENTER  = 0x40u,
    COEF_25,   // AUDIO_CHANNEL_OUT_FRONT_RIGHT_OF_CENTER = 0x80u,
    0.5f,      // AUDIO_CHANNEL_OUT_BACK_CENTER           = 0x100u,
    M_SQRT1_2, // AUDIO_CHANNEL_OUT_SIDE_LEFT             = 0x200u,
    0.f,       // AUDIO_CHANNEL_OUT_SIDE_RIGHT            = 0x400u,
    COEF_36,   // AUDIO_CHANNEL_OUT_TOP_CENTER            = 0x800u,
    1.f,       // AUDIO_CHANNEL_OUT_TOP_FRONT_LEFT        = 0x1000u,
    M_SQRT1_2, // AUDIO_CHANNEL_OUT_TOP_FRONT_CENTER      = 0x2000u,
    0.f,       // AUDIO_CHANNEL_OUT_TOP_FRONT_RIGHT       = 0x4000u,
    M_SQRT1_2, // AUDIO_CHANNEL_OUT_TOP_BACK_LEFT         = 0x8000u,
    COEF_35,   // AUDIO_CHANNEL_OUT_TOP_BACK_CENTER       = 0x10000u,
    0.f,       // AUDIO_CHANNEL_OUT_TOP_BACK_RIGHT        = 0x20000u,
    COEF_61,   // AUDIO_CHANNEL_OUT_TOP_SIDE_LEFT         = 0x40000u,
    0.f,       // AUDIO_CHANNEL_OUT_TOP_SIDE_RIGHT        = 0x80000u,
    1.f,       // AUDIO_CHANNEL_OUT_BOTTOM_FRONT_LEFT     = 0x100000u,
    M_SQRT1_2, // AUDIO_CHANNEL_OUT_BOTTOM_FRONT_CENTER   = 0x200000u,
    0.f,       // AUDIO_CHANNEL_OUT_BOTTOM_FRONT_RIGHT    = 0x400000u,
    0.f,       // AUDIO_CHANNEL_OUT_LOW_FREQUENCY_2       = 0x800000u,
    M_SQRT1_2, // AUDIO_CHANNEL_OUT_FRONT_WIDE_LEFT       = 0x1000000u,
    0.f,       // AUDIO_CHANNEL_OUT_FRONT_WIDE_RIGHT      = 0x2000000u,
};

constexpr inline float kScaleFromChannelIdxRight[] = {
    0.f,       // AUDIO_CHANNEL_OUT_FRONT_LEFT            = 0x1u,
    1.f,       // AUDIO_CHANNEL_OUT_FRONT_RIGHT           = 0x2u,
    M_SQRT1_2, // AUDIO_CHANNEL_OUT_FRONT_CENTER          = 0x4u,
    0.5f,      // AUDIO_CHANNEL_OUT_LOW_FREQUENCY         = 0x8u,
    0.f,       // AUDIO_CHANNEL_OUT_BACK_LEFT             = 0x10u,
    M_SQRT1_2, // AUDIO_CHANNEL_OUT_BACK_RIGHT            = 0x20u,
    COEF_25,   // AUDIO_CHANNEL_OUT_FRONT_LEFT_OF_CENTER  = 0x40u,
    COEF_61,   // AUDIO_CHANNEL_OUT_FRONT_RIGHT_OF_CENTER = 0x80u,
    0.5f,      // AUDIO_CHANNEL_OUT_BACK_CENTER           = 0x100u,
    0.f,       // AUDIO_CHANNEL_OUT_SIDE_LEFT             = 0x200u,
    M_SQRT1_2, // AUDIO_CHANNEL_OUT_SIDE_RIGHT            = 0x400u,
    COEF_36,   // AUDIO_CHANNEL_OUT_TOP_CENTER            = 0x800u,
    0.f,       // AUDIO_CHANNEL_OUT_TOP_FRONT_LEFT        = 0x1000u,
    M_SQRT1_2, // AUDIO_CHANNEL_OUT_TOP_FRONT_CENTER      = 0x2000u,
    1.f,       // AUDIO_CHANNEL_OUT_TOP_FRONT_RIGHT       = 0x4000u,
    0.f,       // AUDIO_CHANNEL_OUT_TOP_BACK_LEFT         = 0x8000u,
    COEF_35,   // AUDIO_CHANNEL_OUT_TOP_BACK_CENTER       = 0x10000u,
    M_SQRT1_2, // AUDIO_CHANNEL_OUT_TOP_BACK_RIGHT        = 0x20000u,
    0.f,       // AUDIO_CHANNEL_OUT_TOP_SIDE_LEFT         = 0x40000u,
    COEF_61,   // AUDIO_CHANNEL_OUT_TOP_SIDE_RIGHT        = 0x80000u,
    0.f,       // AUDIO_CHANNEL_OUT_BOTTOM_FRONT_LEFT     = 0x100000u,
    M_SQRT1_2, // AUDIO_CHANNEL_OUT_BOTTOM_FRONT_CENTER   = 0x200000u,
    1.f,       // AUDIO_CHANNEL_OUT_BOTTOM_FRONT_RIGHT    = 0x400000u,
    M_SQRT1_2, // AUDIO_CHANNEL_OUT_LOW_FREQUENCY_2       = 0x800000u,
    0.f,       // AUDIO_CHANNEL_OUT_FRONT_WIDE_LEFT       = 0x1000000u,
    M_SQRT1_2, // AUDIO_CHANNEL_OUT_FRONT_WIDE_RIGHT      = 0x2000000u,
};

// Our near expectation is 16x the bit that doesn't fit the mantissa.
// this works so long as we add values close in exponent with each other
// realizing that errors accumulate as the sqrt of N (random walk, lln, etc).
#define EXPECT_NEAR_EPSILON(e, v) EXPECT_NEAR((e), (v), \
        abs((e) * std::numeric_limits<std::decay_t<decltype(e)>>::epsilon() * 8))

template<typename T>
static auto channelStatistics(const std::vector<T>& input, size_t channels) {
    std::vector<android::audio_utils::Statistics<T>> result(channels);
    const size_t frames = input.size() / channels;
    if (frames > 0) {
        const float *fptr = input.data();
        for (size_t i = 0; i < frames; ++i) {
            for (size_t j = 0; j < channels; ++j) {
                result[j].add(*fptr++);
            }
        }
    }
    return result;
}

using ChannelMixParam = std::tuple<int /* channel mask */, int /* 0 = replace, 1 = accumulate */>;
class ChannelMixTest : public ::testing::TestWithParam<ChannelMixParam> {
public:

    void testBalance(audio_channel_mask_t channelMask, bool accumulate) {
        using namespace ::android::audio_utils::channels;

        size_t frames = 100; // set to an even number (2, 4, 6 ... ) stream alternates +1, -1.
        constexpr unsigned outChannels = FCC_2;
        unsigned inChannels = audio_channel_count_from_out_mask(channelMask);
        std::vector<float> input(frames * inChannels);
        std::vector<float> output(frames * outChannels);

        double savedPower[32][FCC_2]{};
        for (unsigned i = 0, channel = channelMask; channel != 0; ++i) {
            const int index = __builtin_ctz(channel);
            ASSERT_LT((size_t)index, ChannelMix::MAX_INPUT_CHANNELS_SUPPORTED);
            const int pairIndex = pairIdxFromChannelIdx(index);
            const AUDIO_GEOMETRY_SIDE side = sideFromChannelIdx(index);
            const int channelBit = 1 << index;
            channel &= ~channelBit;

            // Generate a +0.5, -0.5 alternating stream in one channel, which has variance 0.25f
            auto indata = input.data();
            for (unsigned j = 0; j < frames; ++j) {
                for (unsigned k = 0; k < inChannels; ++k) {
                    *indata++ = (k == i) ? (j & 1 ? -0.5f : 0.5f) : 0;
                }
            }

            // Add an offset to the output data - this is ignored if replace instead of accumulate.
            // This must not cause the output to exceed [-1.f, 1.f] otherwise clamping will occur.
            auto outdata = output.data();
            for (unsigned j = 0; j < frames; ++j) {
                for (unsigned k = 0; k < outChannels; ++k) {
                    *outdata++ = 0.5f;
                }
            }

            // Do the channel mix
            ChannelMix(channelMask).process(input.data(), output.data(), frames, accumulate);

            // if we accumulate, we need to subtract the initial data offset.
            if (accumulate) {
                outdata = output.data();
                for (unsigned j = 0; j < frames; ++j) {
                    for (unsigned k = 0; k < outChannels; ++k) {
                        *outdata++ -= 0.5f;
                    }
                }
            }

            // renormalize the stream to unit amplitude (and unity variance).
            outdata = output.data();
            for (unsigned j = 0; j < frames; ++j) {
                for (unsigned k = 0; k < outChannels; ++k) {
                    *outdata++ *= 2.f;
                }
            }

            auto stats = channelStatistics(output, FCC_2);
            // printf("power: %s %s\n", stats[0].toString().c_str(), stats[1].toString().c_str());
            double power[FCC_2] = { stats[0].getPopVariance(), stats[1].getPopVariance() };

            // Check symmetric power for pair channels on exchange of left/right position.
            // to do this, we save previous power measurements.
            if (pairIndex >= 0 && pairIndex < index) {
                EXPECT_NEAR_EPSILON(power[0], savedPower[pairIndex][1]);
                EXPECT_NEAR_EPSILON(power[1], savedPower[pairIndex][0]);
            }
            savedPower[index][0] = power[0];
            savedPower[index][1] = power[1];

            // Confirm exactly the mix amount prescribed by the existing ChannelMix effect.
            // For future changes to the ChannelMix effect, the nearness needs to be relaxed
            // to compare behavior S or earlier.

            constexpr float POWER_TOLERANCE = 0.001;
            const float expectedPower =
                    kScaleFromChannelIdxLeft[index] * kScaleFromChannelIdxLeft[index]
                    + kScaleFromChannelIdxRight[index] * kScaleFromChannelIdxRight[index];
            EXPECT_NEAR(expectedPower, power[0] + power[1], POWER_TOLERANCE);
            switch (side) {
            case AUDIO_GEOMETRY_SIDE_LEFT:
                if (channelBit == AUDIO_CHANNEL_OUT_FRONT_LEFT_OF_CENTER) {
                    break;
                }
                EXPECT_EQ(0.f, power[1]);
                break;
            case AUDIO_GEOMETRY_SIDE_RIGHT:
                if (channelBit == AUDIO_CHANNEL_OUT_FRONT_RIGHT_OF_CENTER) {
                    break;
                }
                EXPECT_EQ(0.f, power[0]);
                break;
            case AUDIO_GEOMETRY_SIDE_CENTER:
                if (channelBit == AUDIO_CHANNEL_OUT_LOW_FREQUENCY) {
                    if (channelMask & AUDIO_CHANNEL_OUT_LOW_FREQUENCY_2) {
                        EXPECT_EQ(0.f, power[1]);
                        break;
                    } else {
                        EXPECT_NEAR_EPSILON(power[0], power[1]); // always true
                        EXPECT_NEAR(expectedPower, power[0] + power[1], POWER_TOLERANCE);
                        break;
                    }
                } else if (channelBit == AUDIO_CHANNEL_OUT_LOW_FREQUENCY_2) {
                    EXPECT_EQ(0.f, power[0]);
                    EXPECT_NEAR(expectedPower, power[1], POWER_TOLERANCE);
                    break;
                }
                EXPECT_NEAR_EPSILON(power[0], power[1]);
                break;
            }
        }
    }
};

TEST_P(ChannelMixTest, basic) {
    testBalance(kChannelPositionMasks[std::get<0>(GetParam())], (bool)std::get<1>(GetParam()));
}

static const char *kName1[] = {"_replace_", "_accumulate_"};

INSTANTIATE_TEST_SUITE_P(
        ChannelMixTestAll, ChannelMixTest,
        ::testing::Combine(
                ::testing::Range(0, (int)std::size(kChannelPositionMasks)),
                ::testing::Range(0, 2)
                ),
        [](const testing::TestParamInfo<ChannelMixTest::ParamType>& info) {
            const int index = std::get<0>(info.param);
            const audio_channel_mask_t channelMask = kChannelPositionMasks[index];
            const std::string name = std::string(audio_channel_out_mask_to_string(channelMask)) +
                    kName1[std::get<1>(info.param)] + std::to_string(index);
            return name;
        });

TEST(channelmix, input_channel_mask) {
    using namespace ::android::audio_utils::channels;
    ChannelMix channelMix(AUDIO_CHANNEL_NONE);

    ASSERT_EQ(AUDIO_CHANNEL_NONE, channelMix.getInputChannelMask());
    ASSERT_TRUE(channelMix.setInputChannelMask(AUDIO_CHANNEL_OUT_STEREO));
    ASSERT_EQ(AUDIO_CHANNEL_OUT_STEREO, channelMix.getInputChannelMask());
}
