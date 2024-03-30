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

#include "rpc_service_manager.h"

#include "chre/util/macros.h"
#include "chre/util/nanoapp/log.h"

#define LOG_TAG "[RpcServiceTest]"

namespace chre {
namespace rpc_service_test {

#ifdef PW_RPC_SERVICE_ENABLED
pw::Status EchoService::Echo(const pw_rpc_EchoMessage &request,
                             pw_rpc_EchoMessage &response) {
  memcpy(response.msg, request.msg,
         MIN(ARRAY_SIZE(response.msg), ARRAY_SIZE(request.msg)));
  return pw::OkStatus();
}
#endif  // PW_RPC_SERVICE_ENABLED

bool RpcServiceManager::start() {
  static chreNanoappRpcService sRpcService = {
      .id = 0xca8f7150a3f05847,
      .version = 0x01020034,
  };

#ifdef PW_RPC_SERVICE_ENABLED
  mServer.RegisterService(mEchoService);
#endif
  return chrePublishRpcServices(&sRpcService, 1 /* numServices */);
}

void RpcServiceManager::handleEvent(uint32_t senderInstanceId,
                                    uint16_t eventType, const void *eventData) {
#ifdef PW_RPC_SERVICE_ENABLED
  if (eventType == CHRE_EVENT_MESSAGE_FROM_HOST) {
    auto *hostMessage = static_cast<const chreMessageFromHostData *>(eventData);
    mOutput.setHostEndpoint(hostMessage->hostEndpoint);

    pw::Status success = mServer.ProcessPacket(
        std::span(static_cast<const std::byte *>(hostMessage->message),
                  hostMessage->messageSize),
        mOutput);
    LOGI("Parsing packet %d", success == pw::OkStatus());
  } else
#else
  UNUSED_VAR(eventData);
#endif  // PW_RPC_SERVICE_ENABLED
  {
    LOGW("Got unknown event type from senderInstanceId %" PRIu32
         " and with eventType %" PRIu16,
         senderInstanceId, eventType);
  }
}

}  // namespace rpc_service_test
}  // namespace chre
