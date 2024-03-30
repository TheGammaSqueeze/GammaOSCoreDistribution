/*
 * Copyright 2020 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

#include "le_audio_types.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

namespace le_audio {
namespace types {

using ::testing::AllOf;
using ::testing::Contains;
using ::testing::ElementsAre;
using ::testing::ElementsAreArray;
using ::testing::Eq;
using ::testing::Pair;
using ::testing::SizeIs;

TEST(LeAudioLtvMapTest, test_serialization) {
  // clang-format off
  const std::vector<uint8_t> ltv_test_vec{
      0x02, 0x01, 0x0a,
      0x03, 0x02, 0xaa, 0xbb,
      0x04, 0x03, 0xde, 0xc0, 0xd0,
  };

  const std::vector<uint8_t> ltv_test_vec2{
      0x04, 0x03, 0xde, 0xc0, 0xde,
      0x05, 0x04, 0xc0, 0xde, 0xc0, 0xde,
  };

  const std::vector<uint8_t> ltv_test_vec_expected{
      0x02, 0x01, 0x0a,
      0x03, 0x02, 0xaa, 0xbb,
      0x04, 0x03, 0xde, 0xc0, 0xde,
      0x05, 0x04, 0xc0, 0xde, 0xc0, 0xde,
  };
  // clang-format on

  // Parse
  bool success;
  LeAudioLtvMap ltv_map =
      LeAudioLtvMap::Parse(ltv_test_vec.data(), ltv_test_vec.size(), success);
  ASSERT_TRUE(success);
  ASSERT_FALSE(ltv_map.IsEmpty());
  ASSERT_EQ((size_t)3, ltv_map.Size());

  ASSERT_TRUE(ltv_map.Find(0x03));
  ASSERT_THAT(*(ltv_map.Find(0x03)), ElementsAre(0xde, 0xc0, 0xd0));

  LeAudioLtvMap ltv_map2 =
      LeAudioLtvMap::Parse(ltv_test_vec2.data(), ltv_test_vec2.size(), success);
  ASSERT_TRUE(success);
  ASSERT_FALSE(ltv_map2.IsEmpty());
  ASSERT_EQ((size_t)2, ltv_map2.Size());

  ltv_map.Append(ltv_map2);
  ASSERT_EQ((size_t)4, ltv_map.Size());

  ASSERT_TRUE(ltv_map.Find(0x01));
  ASSERT_THAT(*(ltv_map.Find(0x01)), ElementsAre(0x0a));
  ASSERT_TRUE(ltv_map.Find(0x02));
  ASSERT_THAT(*(ltv_map.Find(0x02)), ElementsAre(0xaa, 0xbb));
  ASSERT_TRUE(ltv_map.Find(0x03));
  ASSERT_THAT(*(ltv_map.Find(0x03)), ElementsAre(0xde, 0xc0, 0xde));
  ASSERT_TRUE(ltv_map.Find(0x04));
  ASSERT_THAT(*(ltv_map.Find(0x04)), ElementsAre(0xc0, 0xde, 0xc0, 0xde));

  // RawPacket
  std::vector<uint8_t> serialized(ltv_map.RawPacketSize());
  ASSERT_TRUE(ltv_map.RawPacket(serialized.data()));
  ASSERT_THAT(serialized, ElementsAreArray(ltv_test_vec_expected));
  ASSERT_THAT(ltv_map2.RawPacket(), ElementsAreArray(ltv_test_vec2));
}

TEST(LeAudioLtvMapTest, test_serialization_ltv_len_is_zero) {
  // clang-format off
  const std::vector<uint8_t> ltv_test_vec{
      0x02, 0x01, 0x0a,
      0x03, 0x02, 0xaa, 0xbb,
      0x00, 0x00, 0x00, 0x00, 0x00,       // ltv_len == 0
      0x05, 0x04, 0xc0, 0xde, 0xc0, 0xde,
  };
  // clang-format on

  // Parse
  bool success;
  LeAudioLtvMap ltv_map =
      LeAudioLtvMap::Parse(ltv_test_vec.data(), ltv_test_vec.size(), success);
  ASSERT_TRUE(success);
  ASSERT_FALSE(ltv_map.IsEmpty());
  ASSERT_EQ((size_t)3, ltv_map.Size());

  ASSERT_TRUE(ltv_map.Find(0x01));
  ASSERT_THAT(*(ltv_map.Find(0x01)), ElementsAre(0x0a));
  ASSERT_TRUE(ltv_map.Find(0x02));
  ASSERT_THAT(*(ltv_map.Find(0x02)), ElementsAre(0xaa, 0xbb));
  ASSERT_TRUE(ltv_map.Find(0x04));
  ASSERT_THAT(*(ltv_map.Find(0x04)), ElementsAre(0xc0, 0xde, 0xc0, 0xde));

  // RawPacket
  std::vector<uint8_t> serialized(ltv_map.RawPacketSize());
  ASSERT_TRUE(ltv_map.RawPacket(serialized.data()));
  ASSERT_THAT(serialized, ElementsAre(0x02, 0x01, 0x0a, 0x03, 0x02, 0xaa, 0xbb,
                                      0x05, 0x04, 0xc0, 0xde, 0xc0, 0xde));
}

TEST(LeAudioLtvMapTest, test_serialization_ltv_len_is_one) {
  // clang-format off
  const std::vector<uint8_t> ltv_test_vec{
    0x02, 0x01, 0x0a,
    0x01, 0x02,
  };
  // clang-format on

  // Parse
  bool success;
  LeAudioLtvMap ltv_map =
      LeAudioLtvMap::Parse(ltv_test_vec.data(), ltv_test_vec.size(), success);
  ASSERT_TRUE(success);
  ASSERT_FALSE(ltv_map.IsEmpty());
  ASSERT_EQ((size_t)2, ltv_map.Size());

  ASSERT_TRUE(ltv_map.Find(0x01));
  ASSERT_THAT(*(ltv_map.Find(0x01)), ElementsAre(0x0a));
  ASSERT_TRUE(ltv_map.Find(0x02));
  ASSERT_THAT(*(ltv_map.Find(0x02)), SizeIs(0));

  // RawPacket
  std::vector<uint8_t> serialized(ltv_map.RawPacketSize());
  ASSERT_TRUE(ltv_map.RawPacket(serialized.data()));
  ASSERT_THAT(serialized, ElementsAreArray(ltv_test_vec));
}

TEST(LeAudioLtvMapTest, test_serialization_ltv_len_is_invalid) {
  // clang-format off
  const std::vector<uint8_t> ltv_test_vec_1{
      0x02, 0x01, 0x0a,
      0x04, 0x02, 0xaa, 0xbb, // one byte missing
  };
  const std::vector<uint8_t> ltv_test_vec_2{
      0x02, 0x01, 0x0a,
      0x03, 0x02, 0xaa, 0xbb,
      0x01,
  };
  const std::vector<uint8_t> ltv_test_vec_3{
      0x02, 0x01, 0x0a,
      0x03, 0x02, 0xaa, 0xbb,
      0x02, 0x03,
  };
  // clang-format on

  // Parse
  bool success = true;
  LeAudioLtvMap ltv_map;

  ltv_map = LeAudioLtvMap::Parse(ltv_test_vec_1.data(), ltv_test_vec_1.size(),
                                 success);
  ASSERT_FALSE(success);

  ltv_map = LeAudioLtvMap::Parse(ltv_test_vec_2.data(), ltv_test_vec_2.size(),
                                 success);
  ASSERT_FALSE(success);

  ltv_map = LeAudioLtvMap::Parse(ltv_test_vec_3.data(), ltv_test_vec_3.size(),
                                 success);
  ASSERT_FALSE(success);
}

}  // namespace types
}  // namespace le_audio
