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

#include <cstdio>

#include "chre/core/event_loop_manager.h"
#include "chre/platform/system_time.h"

#ifdef CHRE_USE_TOKENIZED_LOGGING
#include "pw_tokenizer/tokenize_to_global_handler_with_payload.h"

// The callback function that must be defined to handle an encoded
// tokenizer message.
void pw_tokenizer_HandleEncodedMessageWithPayload(pw_tokenizer_Payload logLevel,
                                                  const uint8_t encodedMsg[],
                                                  size_t encodedMsgSize) {
#if defined(CHRE_USE_BUFFERED_LOGGING)
  chrePlatformEncodedLogToBuffer(static_cast<chreLogLevel>(logLevel),
                                 encodedMsg, encodedMsgSize);
#else
#error "Tokenized logging is currently only supported with buffered logging."
#endif  // CHRE_USE_BUFFERED_LOGGING
}
#endif
