/*
 * Copyright 2020 The Android Open Source Project
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

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "storage/adapter_config.h"
#include "storage/mutation.h"

using bluetooth::common::ByteArray;
using bluetooth::hci::Address;
using bluetooth::hci::DeviceType;
using bluetooth::storage::AdapterConfig;
using bluetooth::storage::ConfigCache;
using bluetooth::storage::Device;
using bluetooth::storage::Mutation;
using ::testing::Eq;
using ::testing::Optional;

TEST(AdapterConfigTest, create_new_adapter_config) {
  ConfigCache config(10, Device::kLinkKeyProperties);
  ConfigCache memory_only_config(10, {});
  AdapterConfig adapter_config(&config, &memory_only_config, "Adapter");
  ASSERT_FALSE(adapter_config.GetAddress());
}

TEST(AdapterConfigTest, set_property) {
  ConfigCache config(10, Device::kLinkKeyProperties);
  ConfigCache memory_only_config(10, {});
  Address address = {{0x01, 0x02, 0x03, 0x04, 0x05, 0x06}};
  AdapterConfig adapter_config(&config, &memory_only_config, "Adapter");
  ASSERT_FALSE(adapter_config.GetAddress());
  Mutation mutation(&config, &memory_only_config);
  mutation.Add(adapter_config.SetAddress(address));
  mutation.Commit();
  ASSERT_THAT(adapter_config.GetAddress(), Optional(Eq(address)));
}

TEST(AdapterConfigTest, equality_test) {
  ConfigCache config(10, Device::kLinkKeyProperties);
  ConfigCache memory_only_config(10, {});
  bluetooth::hci::Address address = {{0x01, 0x02, 0x03, 0x04, 0x05, 0x06}};
  AdapterConfig adapter_config_1(&config, &memory_only_config, "Adapter");
  AdapterConfig adapter_config_2(&config, &memory_only_config, "Adapter");
  ASSERT_EQ(adapter_config_1, adapter_config_2);
  ConfigCache memory_only_config_2(10, {});
  AdapterConfig adapter_config_3(&config, &memory_only_config_2, "Adapter");
  ASSERT_NE(adapter_config_1, adapter_config_3);
}

TEST(AdapterConfigTest, operator_less_than) {
  ConfigCache config1(10, Device::kLinkKeyProperties);
  ConfigCache config2(10, Device::kLinkKeyProperties);
  ASSERT_NE(&config1, &config2);
  ConfigCache* smaller_config_ptr = &config1;
  ConfigCache* larger_config_ptr = &config2;
  if (&config2 < &config1) {
    smaller_config_ptr = &config2;
    larger_config_ptr = &config1;
  }

  ConfigCache memory_only_config1(10, {});
  ConfigCache memory_only_config2(10, {});
  ASSERT_NE(&memory_only_config1, &memory_only_config2);
  ConfigCache* smaller_memory_only_config_ptr = &memory_only_config1;
  ConfigCache* larger_memory_only_config_ptr = &memory_only_config2;
  if (&memory_only_config2 < &memory_only_config1) {
    smaller_memory_only_config_ptr = &memory_only_config2;
    larger_memory_only_config_ptr = &memory_only_config1;
  }

  bluetooth::hci::Address smaller_address = {{0x01, 0x02, 0x03, 0x04, 0x05, 0x06}};
  bluetooth::hci::Address larger_address = {{0x01, 0x02, 0x03, 0x04, 0x05, 0x07}};

  {
    AdapterConfig adapter_config1(smaller_config_ptr, smaller_memory_only_config_ptr, smaller_address.ToString());
    AdapterConfig adapter_config2(larger_config_ptr, larger_memory_only_config_ptr, larger_address.ToString());
    ASSERT_TRUE(adapter_config1 < adapter_config2);
  }

  {
    AdapterConfig adapter_config1(larger_config_ptr, smaller_memory_only_config_ptr, smaller_address.ToString());
    AdapterConfig adapter_config2(smaller_config_ptr, larger_memory_only_config_ptr, larger_address.ToString());
    ASSERT_FALSE(adapter_config1 < adapter_config2);
  }

  {
    AdapterConfig adapter_config1(smaller_config_ptr, larger_memory_only_config_ptr, smaller_address.ToString());
    AdapterConfig adapter_config2(larger_config_ptr, smaller_memory_only_config_ptr, larger_address.ToString());
    ASSERT_TRUE(adapter_config1 < adapter_config2);
  }

  {
    AdapterConfig adapter_config1(smaller_config_ptr, smaller_memory_only_config_ptr, larger_address.ToString());
    AdapterConfig adapter_config2(larger_config_ptr, larger_memory_only_config_ptr, smaller_address.ToString());
    ASSERT_TRUE(adapter_config1 < adapter_config2);
  }

  {
    AdapterConfig adapter_config1(larger_config_ptr, larger_memory_only_config_ptr, smaller_address.ToString());
    AdapterConfig adapter_config2(smaller_config_ptr, smaller_memory_only_config_ptr, larger_address.ToString());
    ASSERT_FALSE(adapter_config1 < adapter_config2);
  }

  {
    AdapterConfig adapter_config1(larger_config_ptr, larger_memory_only_config_ptr, larger_address.ToString());
    AdapterConfig adapter_config2(smaller_config_ptr, smaller_memory_only_config_ptr, smaller_address.ToString());
    ASSERT_FALSE(adapter_config1 < adapter_config2);
  }

  {
    AdapterConfig adapter_config1(smaller_config_ptr, larger_memory_only_config_ptr, larger_address.ToString());
    AdapterConfig adapter_config2(larger_config_ptr, smaller_memory_only_config_ptr, smaller_address.ToString());
    ASSERT_TRUE(adapter_config1 < adapter_config2);
  }

  {
    AdapterConfig adapter_config1(larger_config_ptr, smaller_memory_only_config_ptr, larger_address.ToString());
    AdapterConfig adapter_config2(smaller_config_ptr, larger_memory_only_config_ptr, smaller_address.ToString());
    ASSERT_FALSE(adapter_config1 < adapter_config2);
  }

  {
    AdapterConfig adapter_config1(smaller_config_ptr, smaller_memory_only_config_ptr, smaller_address.ToString());
    AdapterConfig adapter_config2(smaller_config_ptr, larger_memory_only_config_ptr, smaller_address.ToString());
    ASSERT_TRUE(adapter_config1 < adapter_config2);
  }

  {
    AdapterConfig adapter_config1(smaller_config_ptr, smaller_memory_only_config_ptr, smaller_address.ToString());
    AdapterConfig adapter_config2(smaller_config_ptr, smaller_memory_only_config_ptr, larger_address.ToString());
    ASSERT_TRUE(adapter_config1 < adapter_config2);
  }

  {
    AdapterConfig adapter_config1(smaller_config_ptr, smaller_memory_only_config_ptr, smaller_address.ToString());
    AdapterConfig adapter_config2(larger_config_ptr, smaller_memory_only_config_ptr, smaller_address.ToString());
    ASSERT_TRUE(adapter_config1 < adapter_config2);
  }

  {
    AdapterConfig adapter_config1(smaller_config_ptr, smaller_memory_only_config_ptr, smaller_address.ToString());
    AdapterConfig adapter_config2(smaller_config_ptr, larger_memory_only_config_ptr, larger_address.ToString());
    ASSERT_TRUE(adapter_config1 < adapter_config2);
  }
}
