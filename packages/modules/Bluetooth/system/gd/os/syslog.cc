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

#include "gd/os/syslog.h"

#include <syslog.h>

#include <cstdarg>
#include <memory>

namespace {
#define SYSLOG_IDENT "btadapterd"

const char kSyslogIdent[] = SYSLOG_IDENT;

// Map LOG_TAG_* to syslog mappings
const int kTagMap[] = {
    /*LOG_TAG_VERBOSE=*/LOG_DEBUG,
    /*LOG_TAG_DEBUG=*/LOG_DEBUG,
    /*LOG_TAG_INFO=*/LOG_INFO,
    /*LOG_TAG_WARN=*/LOG_WARNING,
    /*LOG_TAG_ERROR=*/LOG_ERR,
    /*LOG_TAG_FATAL=*/LOG_CRIT,
};

static_assert(sizeof(kTagMap) / sizeof(kTagMap[0]) == LOG_TAG_FATAL + 1);

class SyslogWrapper {
 public:
  SyslogWrapper() {
    openlog(kSyslogIdent, LOG_CONS | LOG_NDELAY | LOG_PID | LOG_PERROR, LOG_DAEMON);
  }

  ~SyslogWrapper() {
    closelog();
  }
};

std::unique_ptr<SyslogWrapper> gSyslog;
}  // namespace

void write_syslog(int tag, const char* format, ...) {
  if (!gSyslog) {
    gSyslog = std::make_unique<SyslogWrapper>();
  }

  // I don't expect to see incorrect tags but making the check anyway so we
  // don't go out of bounds in the array above.
  tag = tag <= LOG_TAG_FATAL ? tag : LOG_TAG_ERROR;
  int level = kTagMap[tag];

  va_list args;
  va_start(args, format);
  vsyslog(level, format, args);
  va_end(args);
}
