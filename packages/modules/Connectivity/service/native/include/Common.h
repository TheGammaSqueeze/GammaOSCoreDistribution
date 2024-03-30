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

#pragma once
// TODO: deduplicate with the constants in NetdConstants.h.
#include <aidl/android/net/INetd.h>
#include "clat_mark.h"

using aidl::android::net::INetd;

static_assert(INetd::CLAT_MARK == CLAT_MARK, "must be 0xDEADC1A7");

enum FirewallRule { ALLOW = INetd::FIREWALL_RULE_ALLOW, DENY = INetd::FIREWALL_RULE_DENY };

// ALLOWLIST means the firewall denies all by default, uids must be explicitly ALLOWed
// DENYLIST means the firewall allows all by default, uids must be explicitly DENYed

enum FirewallType { ALLOWLIST = INetd::FIREWALL_ALLOWLIST, DENYLIST = INetd::FIREWALL_DENYLIST };

// LINT.IfChange(firewall_chain)
enum ChildChain {
    NONE = 0,
    DOZABLE = 1,
    STANDBY = 2,
    POWERSAVE = 3,
    RESTRICTED = 4,
    LOW_POWER_STANDBY = 5,
    LOCKDOWN = 6,
    OEM_DENY_1 = 7,
    OEM_DENY_2 = 8,
    OEM_DENY_3 = 9,
    INVALID_CHAIN
};
// LINT.ThenChange(packages/modules/Connectivity/framework/src/android/net/ConnectivityManager.java)
