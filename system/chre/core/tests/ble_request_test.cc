/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "gtest/gtest.h"

#include "chre/core/ble_request.h"

using chre::BleRequest;

TEST(BleRequest, DefaultMinimalRequest) {
  BleRequest request;
  EXPECT_FALSE(request.isEnabled());
  EXPECT_EQ(CHRE_BLE_SCAN_MODE_BACKGROUND, request.getMode());
  EXPECT_EQ(0, request.getReportDelayMs());
  EXPECT_TRUE(request.getGenericFilters().empty());
  EXPECT_EQ(CHRE_BLE_RSSI_THRESHOLD_NONE, request.getRssiThreshold());
}

TEST(BleRequest, AggressiveModeIsHigherThanBackground) {
  BleRequest backgroundMode(0 /* instanceId */, true /* enable */,
                            CHRE_BLE_SCAN_MODE_BACKGROUND,
                            0 /* reportDelayMs */, nullptr /* filter */);
  BleRequest aggressiveMode(0 /* instanceId */, true /* enable */,
                            CHRE_BLE_SCAN_MODE_AGGRESSIVE,
                            0 /* reportDelayMs */, nullptr /* filter */);

  BleRequest mergedRequest;
  EXPECT_TRUE(mergedRequest.mergeWith(aggressiveMode));
  EXPECT_FALSE(mergedRequest.mergeWith(backgroundMode));

  EXPECT_TRUE(mergedRequest.isEnabled());
  EXPECT_EQ(CHRE_BLE_SCAN_MODE_AGGRESSIVE, mergedRequest.getMode());
  EXPECT_TRUE(mergedRequest.getGenericFilters().empty());
  EXPECT_EQ(CHRE_BLE_RSSI_THRESHOLD_NONE, mergedRequest.getRssiThreshold());
}

TEST(BleRequest, MergeWithReplacesParametersOfDisabledRequest) {
  chreBleScanFilter filter;
  filter.rssiThreshold = -5;
  filter.scanFilterCount = 1;
  auto scanFilters = std::make_unique<chreBleGenericFilter>();
  scanFilters->type = CHRE_BLE_AD_TYPE_SERVICE_DATA_WITH_UUID_16;
  scanFilters->len = 2;
  filter.scanFilters = scanFilters.get();
  BleRequest enabled(0, true, CHRE_BLE_SCAN_MODE_AGGRESSIVE, 20, &filter);

  BleRequest mergedRequest;
  EXPECT_FALSE(mergedRequest.isEnabled());
  EXPECT_TRUE(mergedRequest.mergeWith(enabled));
  EXPECT_TRUE(mergedRequest.isEnabled());
  EXPECT_EQ(CHRE_BLE_SCAN_MODE_AGGRESSIVE, mergedRequest.getMode());
  EXPECT_EQ(20, mergedRequest.getReportDelayMs());
  EXPECT_EQ(-5, mergedRequest.getRssiThreshold());
  EXPECT_EQ(1, mergedRequest.getGenericFilters().size());
  EXPECT_EQ(CHRE_BLE_AD_TYPE_SERVICE_DATA_WITH_UUID_16,
            mergedRequest.getGenericFilters()[0].type);
  EXPECT_EQ(2, mergedRequest.getGenericFilters()[0].len);
}

TEST(BleRequest, IsEquivalentToBasic) {
  BleRequest backgroundMode(0 /* instanceId */, true /* enable */,
                            CHRE_BLE_SCAN_MODE_BACKGROUND,
                            0 /* reportDelayMs */, nullptr /* filter */);
  EXPECT_TRUE(backgroundMode.isEquivalentTo(backgroundMode));
}

TEST(BleRequest, IsNotEquivalentToBasic) {
  BleRequest backgroundMode(0 /* instanceId */, true /* enable */,
                            CHRE_BLE_SCAN_MODE_BACKGROUND,
                            0 /* reportDelayMs */, nullptr /* filter */);
  BleRequest aggressiveMode(0 /* instanceId */, true /* enable */,
                            CHRE_BLE_SCAN_MODE_AGGRESSIVE,
                            0 /* reportDelayMs */, nullptr /* filter */);
  EXPECT_FALSE(backgroundMode.isEquivalentTo(aggressiveMode));
}

TEST(BleRequest, IsEquivalentToAdvanced) {
  chreBleScanFilter filter;
  filter.rssiThreshold = -5;
  filter.scanFilterCount = 1;
  auto scanFilters = std::make_unique<chreBleGenericFilter>();
  scanFilters->type = CHRE_BLE_AD_TYPE_SERVICE_DATA_WITH_UUID_16;
  scanFilters->len = 4;
  filter.scanFilters = scanFilters.get();

  BleRequest backgroundMode(100 /* instanceId */, true /* enable */,
                            CHRE_BLE_SCAN_MODE_BACKGROUND,
                            100 /* reportDelayMs */, &filter);
  EXPECT_TRUE(backgroundMode.isEquivalentTo(backgroundMode));
}

TEST(BleRequest, IsNotEquivalentToAdvanced) {
  chreBleScanFilter filter;
  filter.rssiThreshold = -5;
  filter.scanFilterCount = 1;
  auto scanFilters = std::make_unique<chreBleGenericFilter>();
  scanFilters->type = CHRE_BLE_AD_TYPE_SERVICE_DATA_WITH_UUID_16;
  scanFilters->len = 4;
  filter.scanFilters = scanFilters.get();

  BleRequest backgroundMode(100 /* instanceId */, true /* enable */,
                            CHRE_BLE_SCAN_MODE_BACKGROUND,
                            100 /* reportDelayMs */, &filter /* filter */);
  BleRequest aggressiveMode(0 /* instanceId */, true /* enable */,
                            CHRE_BLE_SCAN_MODE_AGGRESSIVE,
                            0 /* reportDelayMs */, nullptr /* filter */);

  EXPECT_FALSE(backgroundMode.isEquivalentTo(aggressiveMode));
}

TEST(BleRequest, GetScanFilter) {
  chreBleScanFilter filter;
  filter.rssiThreshold = -5;
  filter.scanFilterCount = 1;
  auto scanFilters = std::make_unique<chreBleGenericFilter>();
  scanFilters->type = CHRE_BLE_AD_TYPE_SERVICE_DATA_WITH_UUID_16;
  scanFilters->len = 4;
  filter.scanFilters = scanFilters.get();

  BleRequest backgroundMode(100 /* instanceId */, true /* enable */,
                            CHRE_BLE_SCAN_MODE_BACKGROUND,
                            100 /* reportDelayMs */, &filter /* filter */);

  chreBleScanFilter retFilter = backgroundMode.getScanFilter();
  EXPECT_EQ(filter.rssiThreshold, retFilter.rssiThreshold);
  EXPECT_EQ(filter.scanFilterCount, retFilter.scanFilterCount);
  EXPECT_EQ(0, memcmp(scanFilters.get(), retFilter.scanFilters,
                      sizeof(chreBleGenericFilter)));
}
