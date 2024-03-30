// Copyright 2021 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License"); you may not
// use this file except in compliance with the License. You may obtain a copy of
// the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
// WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
// License for the specific language governing permissions and limitations under
// the License.

#include "dice/android/bcc.h"

#include "dice/test_framework.h"

namespace {

extern "C" {

TEST(BccConfigTest, NoInputs) {
  BccConfigValues input_values = {};
  uint8_t buffer[10];
  size_t buffer_size;
  DiceResult result = BccFormatConfigDescriptor(&input_values, sizeof(buffer),
                                                buffer, &buffer_size);
  EXPECT_EQ(kDiceResultOk, result);
  EXPECT_EQ(1u, buffer_size);
  EXPECT_EQ(0xa0, buffer[0]);
}

TEST(BccConfigTest, AllInputs) {
  BccConfigValues input_values = {
      .inputs = BCC_INPUT_COMPONENT_NAME | BCC_INPUT_COMPONENT_VERSION |
                BCC_INPUT_RESETTABLE,
      .component_name = "Test Component Name",
      .component_version = 0x232a13dec90f42b5,
  };
  uint8_t buffer[256];
  size_t buffer_size;
  DiceResult result = BccFormatConfigDescriptor(&input_values, sizeof(buffer),
                                                buffer, &buffer_size);
  EXPECT_EQ(kDiceResultOk, result);
  const uint8_t expected[] = {
      0xa3, 0x3a, 0x00, 0x01, 0x11, 0x71, 0x73, 'T',  'e',  's',  't',  ' ',
      'C',  'o',  'm',  'p',  'o',  'n',  'e',  'n',  't',  ' ',  'N',  'a',
      'm',  'e',  0x3a, 0x00, 0x01, 0x11, 0x72, 0x1b, 0x23, 0x2a, 0x13, 0xde,
      0xc9, 0x0f, 0x42, 0xb5, 0x3a, 0x00, 0x01, 0x11, 0x73, 0xf6};
  EXPECT_EQ(sizeof(expected), buffer_size);
  EXPECT_EQ(0, memcmp(expected, buffer, buffer_size));
}

TEST(BccTest, PreservesPreviousEntries) {
  const uint8_t bcc[] = {
      // Fake BCC with the root public key and two entries.
      0x83,
      // Fake public key.
      0xa6, 0x01, 0x02, 0x03, 0x27, 0x04, 0x02, 0x20, 0x01, 0x21, 0x40, 0x22,
      0x40,
      // Fake BCC entry.
      0x84, 0x40, 0xa0, 0x40, 0x40,
      // Fake BCC entry.
      0x84, 0x41, 0x55, 0xa0, 0x42, 0x11, 0x22, 0x40,
      // 8-bytes of trailing data that aren't part of the BCC.
      0x84, 0x41, 0x55, 0xa0, 0x42, 0x11, 0x22, 0x40};
  const uint8_t fake_cdi_attest[DICE_CDI_SIZE] = {};
  const uint8_t fake_cdi_seal[DICE_CDI_SIZE] = {};
  DiceInputValues input_values = {};
  uint8_t next_bcc[2048] = {};
  size_t next_bcc_size;
  uint8_t next_cdi_attest[DICE_CDI_SIZE];
  uint8_t next_cdi_seal[DICE_CDI_SIZE];
  DiceResult result =
      BccMainFlow(/*context=*/NULL, fake_cdi_attest, fake_cdi_seal, bcc,
                  sizeof(bcc), &input_values, sizeof(next_bcc), next_bcc,
                  &next_bcc_size, next_cdi_attest, next_cdi_seal);
  EXPECT_EQ(kDiceResultOk, result);
  EXPECT_GT(next_bcc_size, sizeof(bcc));
  EXPECT_EQ(0x84, next_bcc[0]);
  EXPECT_NE(0, memcmp(next_bcc + 1, bcc + 1, sizeof(bcc) - 1));
  EXPECT_EQ(0, memcmp(next_bcc + 1, bcc + 1, sizeof(bcc) - 8 - 1));
}

TEST(BccHandoverTest, PreservesPreviousEntries) {
  const uint8_t bcc_handover[] = {
      0xa3,
      // CDI attest
      0x01, 0x58, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      // CDI seal
      0x02, 0x58, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      // BCC
      0x03, 0x82, 0xa6, 0x01, 0x02, 0x03, 0x27, 0x04, 0x02, 0x20, 0x01, 0x21,
      0x40, 0x22, 0x40, 0x84, 0x40, 0xa0, 0x40, 0x40,
      // 8-bytes of trailing data that aren't part of the BCC.
      0x84, 0x41, 0x55, 0xa0, 0x42, 0x11, 0x22, 0x40};
  DiceInputValues input_values = {};
  uint8_t next_bcc_handover[2048] = {};
  size_t next_bcc_handover_size;
  DiceResult result = BccHandoverMainFlow(
      /*context=*/NULL, bcc_handover, sizeof(bcc_handover), &input_values,
      sizeof(next_bcc_handover), next_bcc_handover, &next_bcc_handover_size);
  EXPECT_EQ(kDiceResultOk, result);
  EXPECT_GT(next_bcc_handover_size, sizeof(bcc_handover));
  EXPECT_EQ(0xa3, next_bcc_handover[0]);
  EXPECT_EQ(0x83, next_bcc_handover[72]);
  EXPECT_NE(0, memcmp(next_bcc_handover + 73, bcc_handover + 73,
                      sizeof(bcc_handover) - 73));
  EXPECT_EQ(0, memcmp(next_bcc_handover + 73, bcc_handover + 73,
                      sizeof(bcc_handover) - 8 - 73));
}

TEST(BccHandoverNoCertTest, InHandoverWithoutBccOutHandoverWithBcc) {
  const uint8_t bcc_handover[] = {
      0xa2,
      // CDI attest
      0x01, 0x58, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      // CDI seal
      0x02, 0x58, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
  };
  DiceInputValues input_values = {};
  uint8_t next_bcc_handover[1024] = {};
  size_t next_bcc_handover_size;
  DiceResult result = BccHandoverMainFlow(
      /*context=*/NULL, bcc_handover, sizeof(bcc_handover), &input_values,
      sizeof(next_bcc_handover), next_bcc_handover, &next_bcc_handover_size);
  EXPECT_EQ(kDiceResultOk, result);
  EXPECT_GT(next_bcc_handover_size, sizeof(bcc_handover));
  EXPECT_EQ(0xa3, next_bcc_handover[0]);
}
}

}  // namespace
