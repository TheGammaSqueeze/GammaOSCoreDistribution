/*
 * Copyright 2021 The Android Open Source Project
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

#include <random>
#include <vector>

#include <benchmark/benchmark.h>
#include <log/log.h>

static constexpr audio_channel_mask_t kChannelPositionMasks[] = {
    AUDIO_CHANNEL_OUT_FRONT_LEFT,
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
};

/*
$ adb shell /data/benchmarktest64/channelmix_benchmark/channelmix_benchmark
Pixel 4XL Coral arm64 benchmark

-----------------------------------------------------------
Benchmark                 Time             CPU   Iterations
-----------------------------------------------------------
BM_ChannelMix/0        2180 ns         2175 ns       321797 AUDIO_CHANNEL_OUT_MONO
BM_ChannelMix/1        2180 ns         2175 ns       321901
BM_ChannelMix/2        3265 ns         3256 ns       214957 AUDIO_CHANNEL_OUT_STEREO
BM_ChannelMix/3        3987 ns         3978 ns       175964 AUDIO_CHANNEL_OUT_2POINT1
BM_ChannelMix/4        4713 ns         4700 ns       148922 AUDIO_CHANNEL_OUT_2POINT0POINT2
BM_ChannelMix/5        1050 ns         1047 ns       668462 AUDIO_CHANNEL_OUT_QUAD
BM_ChannelMix/6        1052 ns         1049 ns       667155 AUDIO_CHANNEL_OUT_QUAD_SIDE
BM_ChannelMix/7        4714 ns         4701 ns       148926 AUDIO_CHANNEL_OUT_SURROUND
BM_ChannelMix/8        5437 ns         5422 ns       129099 AUDIO_CHANNEL_OUT_2POINT1POINT2
BM_ChannelMix/9        5437 ns         5422 ns       129108 AUDIO_CHANNEL_OUT_3POINT0POINT2
BM_ChannelMix/10       5435 ns         5422 ns       129083 AUDIO_CHANNEL_OUT_PENTA
BM_ChannelMix/11       6161 ns         6143 ns       113945 AUDIO_CHANNEL_OUT_3POINT1POINT2
BM_ChannelMix/12       2511 ns         2504 ns       279645 AUDIO_CHANNEL_OUT_5POINT1
BM_ChannelMix/13       2511 ns         2503 ns       279621 AUDIO_CHANNEL_OUT_5POINT1_SIDE
BM_ChannelMix/14       6882 ns         6865 ns       101946 AUDIO_CHANNEL_OUT_6POINT1
BM_ChannelMix/15       7607 ns         7586 ns        92271 AUDIO_CHANNEL_OUT_5POINT1POINT2
BM_ChannelMix/16       2812 ns         2804 ns       249729 AUDIO_CHANNEL_OUT_7POINT1
BM_ChannelMix/17       9055 ns         9032 ns        77517 AUDIO_CHANNEL_OUT_5POINT1POINT4
BM_ChannelMix/18       9055 ns         9031 ns        77477 AUDIO_CHANNEL_OUT_7POINT1POINT2
BM_ChannelMix/19      10510 ns        10479 ns        66762 AUDIO_CHANNEL_OUT_7POINT1POINT4
BM_ChannelMix/20      11293 ns        11262 ns        62135 AUDIO_CHANNEL_OUT_13POINT_360RA
BM_ChannelMix/21      19886 ns        19829 ns        35265 AUDIO_CHANNEL_OUT_22POINT2
*/

static void BM_ChannelMix(benchmark::State& state) {
    const audio_channel_mask_t channelMask = kChannelPositionMasks[state.range(0)];
    using namespace ::android::audio_utils::channels;
    ChannelMix channelMix(channelMask);
    constexpr size_t frameCount = 1024;
    size_t inChannels = audio_channel_count_from_out_mask(channelMask);
    std::vector<float> input(inChannels * frameCount);
    std::vector<float> output(FCC_2 * frameCount);
    constexpr float amplitude = 0.01f;

    std::minstd_rand gen(channelMask);
    std::uniform_real_distribution<> dis(-amplitude, amplitude);
    for (auto& in : input) {
        in = dis(gen);
    }

    assert(channelMix.getInputChannelMask() != AUDIO_CHANNEL_NONE);
    // Run the test
    for (auto _ : state) {
        benchmark::DoNotOptimize(input.data());
        benchmark::DoNotOptimize(output.data());
        channelMix.process(input.data(), output.data(), frameCount, false /* accumulate */);
        benchmark::ClobberMemory();
    }

    state.SetComplexityN(inChannels);
    state.SetLabel(audio_channel_out_mask_to_string(channelMask));
}

static void ChannelMixArgs(benchmark::internal::Benchmark* b) {
    for (int i = 0; i < (int)std::size(kChannelPositionMasks); i++) {
        b->Args({i});
    }
}

BENCHMARK(BM_ChannelMix)->Apply(ChannelMixArgs);

BENCHMARK_MAIN();
