/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
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
#pragma once

#include <base/callback.h>
#include <gmock/gmock.h>

#include "bta_api.h"
#include "bta_dm_api.h"

namespace dm {

class BtaDmInterface {
 public:
  virtual void BTA_DmBleScan(bool start, uint8_t duration) = 0;
  virtual void BTA_DmBleCsisObserve(bool observe,
                                    tBTA_DM_SEARCH_CBACK* p_results_cb) = 0;
  virtual ~BtaDmInterface() = default;
};

class MockBtaDmInterface : public BtaDmInterface {
 public:
  MOCK_METHOD((void), BTA_DmBleScan, (bool start, uint8_t duration));
  MOCK_METHOD((void), BTA_DmBleCsisObserve,
              (bool observe, tBTA_DM_SEARCH_CBACK* p_results_cb));
};

/**
 * Set the {@link MockBtaDmInterface} for testing
 *
 * @param mock_bta_dm_interface pointer to mock bta dm interface,
 * could be null
 */
void SetMockBtaDmInterface(MockBtaDmInterface* mock_bta_dm_interface);

}  // namespace dm
