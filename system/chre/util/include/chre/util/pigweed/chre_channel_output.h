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

#ifndef CHRE_CHANNEL_OUTPUT_H_
#define CHRE_CHANNEL_OUTPUT_H_

#include <span>

#include <chre.h>

#include "pw_rpc/channel.h"

namespace chre {

/**
 * Message format used for communicating between nanoapps since CHRE doesn't
 * have a standard format for this as part of the API definition.
 */
struct ChrePigweedNanoappMessage {
  size_t msgSize;
  uint8_t msg[];
};

/**
 * ChannelOutput that can be used for nanoapps wishing to utilize
 * pw::rpc::Server and pw::rpc::Client for RPC communication between other
 * nanoapps and Android app host clients.
 */
class ChreChannelOutputBase : public pw::rpc::ChannelOutput {
 public:
  // Random value chosen that matches Java client util, but is random enough
  // to not conflict with other CHRE messages the nanoapp and client may send.
  static constexpr uint32_t PW_RPC_CHRE_HOST_MESSAGE_TYPE = INT32_MAX - 10;

  // Random value chosen to be towards the end of the nanoapp event type region
  // so it doesn't conflict with existing nanoapp messages that can be sent.
  static constexpr uint16_t PW_RPC_CHRE_NAPP_EVENT_TYPE = UINT16_MAX - 10;

  size_t MaximumTransmissionUnit() override;

 protected:
  ChreChannelOutputBase();

  /**
   * Sets the endpoint ID that the message should be sent to.
   *
   * @param endpointId Either a host endpoint ID or nanoapp instance ID
   *     corresponding to the endpoint that should receive messages sent through
   *     this channel output.
   */
  void setEndpointId(uint16_t endpointId);

  uint16_t mEndpointId = CHRE_HOST_ENDPOINT_UNSPECIFIED;
};

/**
 * Channel output that must be used if the channel is between two nanoapps.
 */
class ChreNanoappChannelOutput : public ChreChannelOutputBase {
 public:
  /**
   * Sets the nanoapp instance ID that is being communicated with over this
   * channel output.
   */
  void setNanoappEndpoint(uint32_t nanoappInstanceId);

  pw::Status Send(std::span<const std::byte> buffer) override;
};

/**
 * Channel output that must be used if the channel is between a nanoapp and
 * host client.
 */
class ChreHostChannelOutput : public ChreChannelOutputBase {
 public:
  /**
   * Sets the host endpoint being communicated with.
   */
  void setHostEndpoint(uint16_t hostEndpoint);

  pw::Status Send(std::span<const std::byte> buffer) override;
};

}  // namespace chre

#endif  // CHRE_CHANNEL_OUTPUT_H_
