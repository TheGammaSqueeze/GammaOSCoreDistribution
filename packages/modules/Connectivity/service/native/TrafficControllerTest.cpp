/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * TrafficControllerTest.cpp - unit tests for TrafficController.cpp
 */

#include <cstdint>
#include <string>
#include <vector>

#include <fcntl.h>
#include <inttypes.h>
#include <linux/inet_diag.h>
#include <linux/sock_diag.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <unistd.h>

#include <gtest/gtest.h>

#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <binder/Status.h>

#include <netdutils/MockSyscalls.h>

#define TEST_BPF_MAP
#include "TrafficController.h"
#include "bpf/BpfUtils.h"
#include "NetdUpdatablePublic.h"

using namespace android::bpf;  // NOLINT(google-build-using-namespace): grandfathered

namespace android {
namespace net {

using android::netdutils::Status;
using base::Result;
using netdutils::isOk;

constexpr int TEST_MAP_SIZE = 10;
constexpr uid_t TEST_UID = 10086;
constexpr uid_t TEST_UID2 = 54321;
constexpr uid_t TEST_UID3 = 98765;
constexpr uint32_t TEST_TAG = 42;
constexpr uint32_t TEST_COUNTERSET = 1;

#define ASSERT_VALID(x) ASSERT_TRUE((x).isValid())

class TrafficControllerTest : public ::testing::Test {
  protected:
    TrafficControllerTest() {}
    TrafficController mTc;
    BpfMap<uint64_t, UidTagValue> mFakeCookieTagMap;
    BpfMap<uint32_t, StatsValue> mFakeAppUidStatsMap;
    BpfMap<StatsKey, StatsValue> mFakeStatsMapA;
    BpfMap<uint32_t, uint32_t> mFakeConfigurationMap;
    BpfMap<uint32_t, UidOwnerValue> mFakeUidOwnerMap;
    BpfMap<uint32_t, uint8_t> mFakeUidPermissionMap;

    void SetUp() {
        std::lock_guard guard(mTc.mMutex);
        ASSERT_EQ(0, setrlimitForTest());

        mFakeCookieTagMap.resetMap(BPF_MAP_TYPE_HASH, TEST_MAP_SIZE);
        ASSERT_VALID(mFakeCookieTagMap);

        mFakeAppUidStatsMap.resetMap(BPF_MAP_TYPE_HASH, TEST_MAP_SIZE);
        ASSERT_VALID(mFakeAppUidStatsMap);

        mFakeStatsMapA.resetMap(BPF_MAP_TYPE_HASH, TEST_MAP_SIZE);
        ASSERT_VALID(mFakeStatsMapA);

        mFakeConfigurationMap.resetMap(BPF_MAP_TYPE_ARRAY, CONFIGURATION_MAP_SIZE);
        ASSERT_VALID(mFakeConfigurationMap);

        mFakeUidOwnerMap.resetMap(BPF_MAP_TYPE_HASH, TEST_MAP_SIZE);
        ASSERT_VALID(mFakeUidOwnerMap);
        mFakeUidPermissionMap.resetMap(BPF_MAP_TYPE_HASH, TEST_MAP_SIZE);
        ASSERT_VALID(mFakeUidPermissionMap);

        mTc.mCookieTagMap = mFakeCookieTagMap;
        ASSERT_VALID(mTc.mCookieTagMap);
        mTc.mAppUidStatsMap = mFakeAppUidStatsMap;
        ASSERT_VALID(mTc.mAppUidStatsMap);
        mTc.mStatsMapA = mFakeStatsMapA;
        ASSERT_VALID(mTc.mStatsMapA);
        mTc.mConfigurationMap = mFakeConfigurationMap;
        ASSERT_VALID(mTc.mConfigurationMap);

        // Always write to stats map A by default.
        static_assert(SELECT_MAP_A == 0);

        mTc.mUidOwnerMap = mFakeUidOwnerMap;
        ASSERT_VALID(mTc.mUidOwnerMap);
        mTc.mUidPermissionMap = mFakeUidPermissionMap;
        ASSERT_VALID(mTc.mUidPermissionMap);
        mTc.mPrivilegedUser.clear();
    }

    void populateFakeStats(uint64_t cookie, uint32_t uid, uint32_t tag, StatsKey* key) {
        UidTagValue cookieMapkey = {.uid = (uint32_t)uid, .tag = tag};
        EXPECT_RESULT_OK(mFakeCookieTagMap.writeValue(cookie, cookieMapkey, BPF_ANY));
        *key = {.uid = uid, .tag = tag, .counterSet = TEST_COUNTERSET, .ifaceIndex = 1};
        StatsValue statsMapValue = {.rxPackets = 1, .rxBytes = 100};
        EXPECT_RESULT_OK(mFakeStatsMapA.writeValue(*key, statsMapValue, BPF_ANY));
        key->tag = 0;
        EXPECT_RESULT_OK(mFakeStatsMapA.writeValue(*key, statsMapValue, BPF_ANY));
        EXPECT_RESULT_OK(mFakeAppUidStatsMap.writeValue(uid, statsMapValue, BPF_ANY));
        // put tag information back to statsKey
        key->tag = tag;
    }

    void checkUidOwnerRuleForChain(ChildChain chain, UidOwnerMatchType match) {
        uint32_t uid = TEST_UID;
        EXPECT_EQ(0, mTc.changeUidOwnerRule(chain, uid, DENY, DENYLIST));
        Result<UidOwnerValue> value = mFakeUidOwnerMap.readValue(uid);
        EXPECT_RESULT_OK(value);
        EXPECT_TRUE(value.value().rule & match);

        uid = TEST_UID2;
        EXPECT_EQ(0, mTc.changeUidOwnerRule(chain, uid, ALLOW, ALLOWLIST));
        value = mFakeUidOwnerMap.readValue(uid);
        EXPECT_RESULT_OK(value);
        EXPECT_TRUE(value.value().rule & match);

        EXPECT_EQ(0, mTc.changeUidOwnerRule(chain, uid, DENY, ALLOWLIST));
        value = mFakeUidOwnerMap.readValue(uid);
        EXPECT_FALSE(value.ok());
        EXPECT_EQ(ENOENT, value.error().code());

        uid = TEST_UID;
        EXPECT_EQ(0, mTc.changeUidOwnerRule(chain, uid, ALLOW, DENYLIST));
        value = mFakeUidOwnerMap.readValue(uid);
        EXPECT_FALSE(value.ok());
        EXPECT_EQ(ENOENT, value.error().code());

        uid = TEST_UID3;
        EXPECT_EQ(-ENOENT, mTc.changeUidOwnerRule(chain, uid, ALLOW, DENYLIST));
        value = mFakeUidOwnerMap.readValue(uid);
        EXPECT_FALSE(value.ok());
        EXPECT_EQ(ENOENT, value.error().code());
    }

    void checkEachUidValue(const std::vector<int32_t>& uids, UidOwnerMatchType match) {
        for (uint32_t uid : uids) {
            Result<UidOwnerValue> value = mFakeUidOwnerMap.readValue(uid);
            EXPECT_RESULT_OK(value);
            EXPECT_TRUE(value.value().rule & match);
        }
        std::set<uint32_t> uidSet(uids.begin(), uids.end());
        const auto checkNoOtherUid = [&uidSet](const int32_t& key,
                                               const BpfMap<uint32_t, UidOwnerValue>&) {
            EXPECT_NE(uidSet.end(), uidSet.find(key));
            return Result<void>();
        };
        EXPECT_RESULT_OK(mFakeUidOwnerMap.iterate(checkNoOtherUid));
    }

    void checkUidMapReplace(const std::string& name, const std::vector<int32_t>& uids,
                            UidOwnerMatchType match) {
        bool isAllowlist = true;
        EXPECT_EQ(0, mTc.replaceUidOwnerMap(name, isAllowlist, uids));
        checkEachUidValue(uids, match);

        isAllowlist = false;
        EXPECT_EQ(0, mTc.replaceUidOwnerMap(name, isAllowlist, uids));
        checkEachUidValue(uids, match);
    }

    void expectUidOwnerMapValues(const std::vector<uint32_t>& appUids, uint8_t expectedRule,
                                 uint32_t expectedIif) {
        for (uint32_t uid : appUids) {
            Result<UidOwnerValue> value = mFakeUidOwnerMap.readValue(uid);
            EXPECT_RESULT_OK(value);
            EXPECT_EQ(expectedRule, value.value().rule)
                    << "Expected rule for UID " << uid << " to be " << expectedRule << ", but was "
                    << value.value().rule;
            EXPECT_EQ(expectedIif, value.value().iif)
                    << "Expected iif for UID " << uid << " to be " << expectedIif << ", but was "
                    << value.value().iif;
        }
    }

    template <class Key, class Value>
    void expectMapEmpty(BpfMap<Key, Value>& map) {
        auto isEmpty = map.isEmpty();
        EXPECT_RESULT_OK(isEmpty);
        EXPECT_TRUE(isEmpty.value());
    }

    void expectUidPermissionMapValues(const std::vector<uid_t>& appUids, uint8_t expectedValue) {
        for (uid_t uid : appUids) {
            Result<uint8_t> value = mFakeUidPermissionMap.readValue(uid);
            EXPECT_RESULT_OK(value);
            EXPECT_EQ(expectedValue, value.value())
                    << "Expected value for UID " << uid << " to be " << expectedValue
                    << ", but was " << value.value();
        }
    }

    void expectPrivilegedUserSet(const std::vector<uid_t>& appUids) {
        std::lock_guard guard(mTc.mMutex);
        EXPECT_EQ(appUids.size(), mTc.mPrivilegedUser.size());
        for (uid_t uid : appUids) {
            EXPECT_NE(mTc.mPrivilegedUser.end(), mTc.mPrivilegedUser.find(uid));
        }
    }

    void expectPrivilegedUserSetEmpty() {
        std::lock_guard guard(mTc.mMutex);
        EXPECT_TRUE(mTc.mPrivilegedUser.empty());
    }

    void addPrivilegedUid(uid_t uid) {
        std::vector privilegedUid = {uid};
        mTc.setPermissionForUids(INetd::PERMISSION_UPDATE_DEVICE_STATS, privilegedUid);
    }

    void removePrivilegedUid(uid_t uid) {
        std::vector privilegedUid = {uid};
        mTc.setPermissionForUids(INetd::PERMISSION_NONE, privilegedUid);
    }

    void expectFakeStatsUnchanged(uint64_t cookie, uint32_t tag, uint32_t uid,
                                  StatsKey tagStatsMapKey) {
        Result<UidTagValue> cookieMapResult = mFakeCookieTagMap.readValue(cookie);
        EXPECT_RESULT_OK(cookieMapResult);
        EXPECT_EQ(uid, cookieMapResult.value().uid);
        EXPECT_EQ(tag, cookieMapResult.value().tag);
        Result<StatsValue> statsMapResult = mFakeStatsMapA.readValue(tagStatsMapKey);
        EXPECT_RESULT_OK(statsMapResult);
        EXPECT_EQ((uint64_t)1, statsMapResult.value().rxPackets);
        EXPECT_EQ((uint64_t)100, statsMapResult.value().rxBytes);
        tagStatsMapKey.tag = 0;
        statsMapResult = mFakeStatsMapA.readValue(tagStatsMapKey);
        EXPECT_RESULT_OK(statsMapResult);
        EXPECT_EQ((uint64_t)1, statsMapResult.value().rxPackets);
        EXPECT_EQ((uint64_t)100, statsMapResult.value().rxBytes);
        auto appStatsResult = mFakeAppUidStatsMap.readValue(uid);
        EXPECT_RESULT_OK(appStatsResult);
        EXPECT_EQ((uint64_t)1, appStatsResult.value().rxPackets);
        EXPECT_EQ((uint64_t)100, appStatsResult.value().rxBytes);
    }

    Status updateUidOwnerMaps(const std::vector<uint32_t>& appUids,
                              UidOwnerMatchType matchType, TrafficController::IptOp op) {
        Status ret(0);
        for (auto uid : appUids) {
        ret = mTc.updateUidOwnerMap(uid, matchType, op);
           if(!isOk(ret)) break;
        }
        return ret;
    }

};

TEST_F(TrafficControllerTest, TestUpdateOwnerMapEntry) {
    uint32_t uid = TEST_UID;
    ASSERT_TRUE(isOk(mTc.updateOwnerMapEntry(STANDBY_MATCH, uid, DENY, DENYLIST)));
    Result<UidOwnerValue> value = mFakeUidOwnerMap.readValue(uid);
    ASSERT_RESULT_OK(value);
    ASSERT_TRUE(value.value().rule & STANDBY_MATCH);

    ASSERT_TRUE(isOk(mTc.updateOwnerMapEntry(DOZABLE_MATCH, uid, ALLOW, ALLOWLIST)));
    value = mFakeUidOwnerMap.readValue(uid);
    ASSERT_RESULT_OK(value);
    ASSERT_TRUE(value.value().rule & DOZABLE_MATCH);

    ASSERT_TRUE(isOk(mTc.updateOwnerMapEntry(DOZABLE_MATCH, uid, DENY, ALLOWLIST)));
    value = mFakeUidOwnerMap.readValue(uid);
    ASSERT_RESULT_OK(value);
    ASSERT_FALSE(value.value().rule & DOZABLE_MATCH);

    ASSERT_TRUE(isOk(mTc.updateOwnerMapEntry(STANDBY_MATCH, uid, ALLOW, DENYLIST)));
    ASSERT_FALSE(mFakeUidOwnerMap.readValue(uid).ok());

    uid = TEST_UID2;
    ASSERT_FALSE(isOk(mTc.updateOwnerMapEntry(STANDBY_MATCH, uid, ALLOW, DENYLIST)));
    ASSERT_FALSE(mFakeUidOwnerMap.readValue(uid).ok());
}

TEST_F(TrafficControllerTest, TestChangeUidOwnerRule) {
    checkUidOwnerRuleForChain(DOZABLE, DOZABLE_MATCH);
    checkUidOwnerRuleForChain(STANDBY, STANDBY_MATCH);
    checkUidOwnerRuleForChain(POWERSAVE, POWERSAVE_MATCH);
    checkUidOwnerRuleForChain(RESTRICTED, RESTRICTED_MATCH);
    checkUidOwnerRuleForChain(LOW_POWER_STANDBY, LOW_POWER_STANDBY_MATCH);
    checkUidOwnerRuleForChain(LOCKDOWN, LOCKDOWN_VPN_MATCH);
    checkUidOwnerRuleForChain(OEM_DENY_1, OEM_DENY_1_MATCH);
    checkUidOwnerRuleForChain(OEM_DENY_2, OEM_DENY_2_MATCH);
    checkUidOwnerRuleForChain(OEM_DENY_3, OEM_DENY_3_MATCH);
    ASSERT_EQ(-EINVAL, mTc.changeUidOwnerRule(NONE, TEST_UID, ALLOW, ALLOWLIST));
    ASSERT_EQ(-EINVAL, mTc.changeUidOwnerRule(INVALID_CHAIN, TEST_UID, ALLOW, ALLOWLIST));
}

TEST_F(TrafficControllerTest, TestReplaceUidOwnerMap) {
    std::vector<int32_t> uids = {TEST_UID, TEST_UID2, TEST_UID3};
    checkUidMapReplace("fw_dozable", uids, DOZABLE_MATCH);
    checkUidMapReplace("fw_standby", uids, STANDBY_MATCH);
    checkUidMapReplace("fw_powersave", uids, POWERSAVE_MATCH);
    checkUidMapReplace("fw_restricted", uids, RESTRICTED_MATCH);
    checkUidMapReplace("fw_low_power_standby", uids, LOW_POWER_STANDBY_MATCH);
    checkUidMapReplace("fw_oem_deny_1", uids, OEM_DENY_1_MATCH);
    checkUidMapReplace("fw_oem_deny_2", uids, OEM_DENY_2_MATCH);
    checkUidMapReplace("fw_oem_deny_3", uids, OEM_DENY_3_MATCH);
    ASSERT_EQ(-EINVAL, mTc.replaceUidOwnerMap("unknow", true, uids));
}

TEST_F(TrafficControllerTest, TestReplaceSameChain) {
    std::vector<int32_t> uids = {TEST_UID, TEST_UID2, TEST_UID3};
    checkUidMapReplace("fw_dozable", uids, DOZABLE_MATCH);
    std::vector<int32_t> newUids = {TEST_UID2, TEST_UID3};
    checkUidMapReplace("fw_dozable", newUids, DOZABLE_MATCH);
}

TEST_F(TrafficControllerTest, TestDenylistUidMatch) {
    std::vector<uint32_t> appUids = {1000, 1001, 10012};
    ASSERT_TRUE(isOk(updateUidOwnerMaps(appUids, PENALTY_BOX_MATCH,
                                        TrafficController::IptOpInsert)));
    expectUidOwnerMapValues(appUids, PENALTY_BOX_MATCH, 0);
    ASSERT_TRUE(isOk(updateUidOwnerMaps(appUids, PENALTY_BOX_MATCH,
                                        TrafficController::IptOpDelete)));
    expectMapEmpty(mFakeUidOwnerMap);
}

TEST_F(TrafficControllerTest, TestAllowlistUidMatch) {
    std::vector<uint32_t> appUids = {1000, 1001, 10012};
    ASSERT_TRUE(isOk(updateUidOwnerMaps(appUids, HAPPY_BOX_MATCH, TrafficController::IptOpInsert)));
    expectUidOwnerMapValues(appUids, HAPPY_BOX_MATCH, 0);
    ASSERT_TRUE(isOk(updateUidOwnerMaps(appUids, HAPPY_BOX_MATCH, TrafficController::IptOpDelete)));
    expectMapEmpty(mFakeUidOwnerMap);
}

TEST_F(TrafficControllerTest, TestReplaceMatchUid) {
    std::vector<uint32_t> appUids = {1000, 1001, 10012};
    // Add appUids to the denylist and expect that their values are all PENALTY_BOX_MATCH.
    ASSERT_TRUE(isOk(updateUidOwnerMaps(appUids, PENALTY_BOX_MATCH,
                                        TrafficController::IptOpInsert)));
    expectUidOwnerMapValues(appUids, PENALTY_BOX_MATCH, 0);

    // Add the same UIDs to the allowlist and expect that we get PENALTY_BOX_MATCH |
    // HAPPY_BOX_MATCH.
    ASSERT_TRUE(isOk(updateUidOwnerMaps(appUids, HAPPY_BOX_MATCH, TrafficController::IptOpInsert)));
    expectUidOwnerMapValues(appUids, HAPPY_BOX_MATCH | PENALTY_BOX_MATCH, 0);

    // Remove the same UIDs from the allowlist and check the PENALTY_BOX_MATCH is still there.
    ASSERT_TRUE(isOk(updateUidOwnerMaps(appUids, HAPPY_BOX_MATCH, TrafficController::IptOpDelete)));
    expectUidOwnerMapValues(appUids, PENALTY_BOX_MATCH, 0);

    // Remove the same UIDs from the denylist and check the map is empty.
    ASSERT_TRUE(isOk(updateUidOwnerMaps(appUids, PENALTY_BOX_MATCH,
                                        TrafficController::IptOpDelete)));
    ASSERT_FALSE(mFakeUidOwnerMap.getFirstKey().ok());
}

TEST_F(TrafficControllerTest, TestDeleteWrongMatchSilentlyFails) {
    std::vector<uint32_t> appUids = {1000, 1001, 10012};
    // If the uid does not exist in the map, trying to delete a rule about it will fail.
    ASSERT_FALSE(isOk(updateUidOwnerMaps(appUids, HAPPY_BOX_MATCH,
                                         TrafficController::IptOpDelete)));
    expectMapEmpty(mFakeUidOwnerMap);

    // Add denylist rules for appUids.
    ASSERT_TRUE(isOk(updateUidOwnerMaps(appUids, HAPPY_BOX_MATCH,
                                        TrafficController::IptOpInsert)));
    expectUidOwnerMapValues(appUids, HAPPY_BOX_MATCH, 0);

    // Delete (non-existent) denylist rules for appUids, and check that this silently does
    // nothing if the uid is in the map but does not have denylist match. This is required because
    // NetworkManagementService will try to remove a uid from denylist after adding it to the
    // allowlist and if the remove fails it will not update the uid status.
    ASSERT_TRUE(isOk(updateUidOwnerMaps(appUids, PENALTY_BOX_MATCH,
                                        TrafficController::IptOpDelete)));
    expectUidOwnerMapValues(appUids, HAPPY_BOX_MATCH, 0);
}

TEST_F(TrafficControllerTest, TestAddUidInterfaceFilteringRules) {
    int iif0 = 15;
    ASSERT_TRUE(isOk(mTc.addUidInterfaceRules(iif0, {1000, 1001})));
    expectUidOwnerMapValues({1000, 1001}, IIF_MATCH, iif0);

    // Add some non-overlapping new uids. They should coexist with existing rules
    int iif1 = 16;
    ASSERT_TRUE(isOk(mTc.addUidInterfaceRules(iif1, {2000, 2001})));
    expectUidOwnerMapValues({1000, 1001}, IIF_MATCH, iif0);
    expectUidOwnerMapValues({2000, 2001}, IIF_MATCH, iif1);

    // Overwrite some existing uids
    int iif2 = 17;
    ASSERT_TRUE(isOk(mTc.addUidInterfaceRules(iif2, {1000, 2000})));
    expectUidOwnerMapValues({1001}, IIF_MATCH, iif0);
    expectUidOwnerMapValues({2001}, IIF_MATCH, iif1);
    expectUidOwnerMapValues({1000, 2000}, IIF_MATCH, iif2);
}

TEST_F(TrafficControllerTest, TestRemoveUidInterfaceFilteringRules) {
    int iif0 = 15;
    int iif1 = 16;
    ASSERT_TRUE(isOk(mTc.addUidInterfaceRules(iif0, {1000, 1001})));
    ASSERT_TRUE(isOk(mTc.addUidInterfaceRules(iif1, {2000, 2001})));
    expectUidOwnerMapValues({1000, 1001}, IIF_MATCH, iif0);
    expectUidOwnerMapValues({2000, 2001}, IIF_MATCH, iif1);

    // Rmove some uids
    ASSERT_TRUE(isOk(mTc.removeUidInterfaceRules({1001, 2001})));
    expectUidOwnerMapValues({1000}, IIF_MATCH, iif0);
    expectUidOwnerMapValues({2000}, IIF_MATCH, iif1);
    checkEachUidValue({1000, 2000}, IIF_MATCH);  // Make sure there are only two uids remaining

    // Remove non-existent uids shouldn't fail
    ASSERT_TRUE(isOk(mTc.removeUidInterfaceRules({2000, 3000})));
    expectUidOwnerMapValues({1000}, IIF_MATCH, iif0);
    checkEachUidValue({1000}, IIF_MATCH);  // Make sure there are only one uid remaining

    // Remove everything
    ASSERT_TRUE(isOk(mTc.removeUidInterfaceRules({1000})));
    expectMapEmpty(mFakeUidOwnerMap);
}

TEST_F(TrafficControllerTest, TestUidInterfaceFilteringRulesCoexistWithExistingMatches) {
    // Set up existing PENALTY_BOX_MATCH rules
    ASSERT_TRUE(isOk(updateUidOwnerMaps({1000, 1001, 10012}, PENALTY_BOX_MATCH,
                                        TrafficController::IptOpInsert)));
    expectUidOwnerMapValues({1000, 1001, 10012}, PENALTY_BOX_MATCH, 0);

    // Add some partially-overlapping uid owner rules and check result
    int iif1 = 32;
    ASSERT_TRUE(isOk(mTc.addUidInterfaceRules(iif1, {10012, 10013, 10014})));
    expectUidOwnerMapValues({1000, 1001}, PENALTY_BOX_MATCH, 0);
    expectUidOwnerMapValues({10012}, PENALTY_BOX_MATCH | IIF_MATCH, iif1);
    expectUidOwnerMapValues({10013, 10014}, IIF_MATCH, iif1);

    // Removing some PENALTY_BOX_MATCH rules should not change uid interface rule
    ASSERT_TRUE(isOk(updateUidOwnerMaps({1001, 10012}, PENALTY_BOX_MATCH,
                                        TrafficController::IptOpDelete)));
    expectUidOwnerMapValues({1000}, PENALTY_BOX_MATCH, 0);
    expectUidOwnerMapValues({10012, 10013, 10014}, IIF_MATCH, iif1);

    // Remove all uid interface rules
    ASSERT_TRUE(isOk(mTc.removeUidInterfaceRules({10012, 10013, 10014})));
    expectUidOwnerMapValues({1000}, PENALTY_BOX_MATCH, 0);
    // Make sure these are the only uids left
    checkEachUidValue({1000}, PENALTY_BOX_MATCH);
}

TEST_F(TrafficControllerTest, TestUidInterfaceFilteringRulesCoexistWithNewMatches) {
    int iif1 = 56;
    // Set up existing uid interface rules
    ASSERT_TRUE(isOk(mTc.addUidInterfaceRules(iif1, {10001, 10002})));
    expectUidOwnerMapValues({10001, 10002}, IIF_MATCH, iif1);

    // Add some partially-overlapping doze rules
    EXPECT_EQ(0, mTc.replaceUidOwnerMap("fw_dozable", true, {10002, 10003}));
    expectUidOwnerMapValues({10001}, IIF_MATCH, iif1);
    expectUidOwnerMapValues({10002}, DOZABLE_MATCH | IIF_MATCH, iif1);
    expectUidOwnerMapValues({10003}, DOZABLE_MATCH, 0);

    // Introduce a third rule type (powersave) on various existing UIDs
    EXPECT_EQ(0, mTc.replaceUidOwnerMap("fw_powersave", true, {10000, 10001, 10002, 10003}));
    expectUidOwnerMapValues({10000}, POWERSAVE_MATCH, 0);
    expectUidOwnerMapValues({10001}, POWERSAVE_MATCH | IIF_MATCH, iif1);
    expectUidOwnerMapValues({10002}, POWERSAVE_MATCH | DOZABLE_MATCH | IIF_MATCH, iif1);
    expectUidOwnerMapValues({10003}, POWERSAVE_MATCH | DOZABLE_MATCH, 0);

    // Remove all doze rules
    EXPECT_EQ(0, mTc.replaceUidOwnerMap("fw_dozable", true, {}));
    expectUidOwnerMapValues({10000}, POWERSAVE_MATCH, 0);
    expectUidOwnerMapValues({10001}, POWERSAVE_MATCH | IIF_MATCH, iif1);
    expectUidOwnerMapValues({10002}, POWERSAVE_MATCH | IIF_MATCH, iif1);
    expectUidOwnerMapValues({10003}, POWERSAVE_MATCH, 0);

    // Remove all powersave rules, expect ownerMap to only have uid interface rules left
    EXPECT_EQ(0, mTc.replaceUidOwnerMap("fw_powersave", true, {}));
    expectUidOwnerMapValues({10001, 10002}, IIF_MATCH, iif1);
    // Make sure these are the only uids left
    checkEachUidValue({10001, 10002}, IIF_MATCH);
}

TEST_F(TrafficControllerTest, TestAddUidInterfaceFilteringRulesWithWildcard) {
    // iif=0 is a wildcard
    int iif = 0;
    // Add interface rule with wildcard to uids
    ASSERT_TRUE(isOk(mTc.addUidInterfaceRules(iif, {1000, 1001})));
    expectUidOwnerMapValues({1000, 1001}, IIF_MATCH, iif);
}

TEST_F(TrafficControllerTest, TestRemoveUidInterfaceFilteringRulesWithWildcard) {
    // iif=0 is a wildcard
    int iif = 0;
    // Add interface rule with wildcard to two uids
    ASSERT_TRUE(isOk(mTc.addUidInterfaceRules(iif, {1000, 1001})));
    expectUidOwnerMapValues({1000, 1001}, IIF_MATCH, iif);

    // Remove interface rule from one of the uids
    ASSERT_TRUE(isOk(mTc.removeUidInterfaceRules({1000})));
    expectUidOwnerMapValues({1001}, IIF_MATCH, iif);
    checkEachUidValue({1001}, IIF_MATCH);

    // Remove interface rule from the remaining uid
    ASSERT_TRUE(isOk(mTc.removeUidInterfaceRules({1001})));
    expectMapEmpty(mFakeUidOwnerMap);
}

TEST_F(TrafficControllerTest, TestUidInterfaceFilteringRulesWithWildcardAndExistingMatches) {
    // Set up existing DOZABLE_MATCH and POWERSAVE_MATCH rule
    ASSERT_TRUE(isOk(updateUidOwnerMaps({1000}, DOZABLE_MATCH,
                                        TrafficController::IptOpInsert)));
    ASSERT_TRUE(isOk(updateUidOwnerMaps({1000}, POWERSAVE_MATCH,
                                        TrafficController::IptOpInsert)));

    // iif=0 is a wildcard
    int iif = 0;
    // Add interface rule with wildcard to the existing uid
    ASSERT_TRUE(isOk(mTc.addUidInterfaceRules(iif, {1000})));
    expectUidOwnerMapValues({1000}, POWERSAVE_MATCH | DOZABLE_MATCH | IIF_MATCH, iif);

    // Remove interface rule with wildcard from the existing uid
    ASSERT_TRUE(isOk(mTc.removeUidInterfaceRules({1000})));
    expectUidOwnerMapValues({1000}, POWERSAVE_MATCH | DOZABLE_MATCH, 0);
}

TEST_F(TrafficControllerTest, TestUidInterfaceFilteringRulesWithWildcardAndNewMatches) {
    // iif=0 is a wildcard
    int iif = 0;
    // Set up existing interface rule with wildcard
    ASSERT_TRUE(isOk(mTc.addUidInterfaceRules(iif, {1000})));

    // Add DOZABLE_MATCH and POWERSAVE_MATCH rule to the existing uid
    ASSERT_TRUE(isOk(updateUidOwnerMaps({1000}, DOZABLE_MATCH,
                                        TrafficController::IptOpInsert)));
    ASSERT_TRUE(isOk(updateUidOwnerMaps({1000}, POWERSAVE_MATCH,
                                        TrafficController::IptOpInsert)));
    expectUidOwnerMapValues({1000}, POWERSAVE_MATCH | DOZABLE_MATCH | IIF_MATCH, iif);

    // Remove DOZABLE_MATCH and POWERSAVE_MATCH rule from the existing uid
    ASSERT_TRUE(isOk(updateUidOwnerMaps({1000}, DOZABLE_MATCH,
                                        TrafficController::IptOpDelete)));
    ASSERT_TRUE(isOk(updateUidOwnerMaps({1000}, POWERSAVE_MATCH,
                                        TrafficController::IptOpDelete)));
    expectUidOwnerMapValues({1000}, IIF_MATCH, iif);
}

TEST_F(TrafficControllerTest, TestGrantInternetPermission) {
    std::vector<uid_t> appUids = {TEST_UID, TEST_UID2, TEST_UID3};

    mTc.setPermissionForUids(INetd::PERMISSION_INTERNET, appUids);
    expectMapEmpty(mFakeUidPermissionMap);
    expectPrivilegedUserSetEmpty();
}

TEST_F(TrafficControllerTest, TestRevokeInternetPermission) {
    std::vector<uid_t> appUids = {TEST_UID, TEST_UID2, TEST_UID3};

    mTc.setPermissionForUids(INetd::PERMISSION_NONE, appUids);
    expectUidPermissionMapValues(appUids, INetd::PERMISSION_NONE);
}

TEST_F(TrafficControllerTest, TestPermissionUninstalled) {
    std::vector<uid_t> appUids = {TEST_UID, TEST_UID2, TEST_UID3};

    mTc.setPermissionForUids(INetd::PERMISSION_UPDATE_DEVICE_STATS, appUids);
    expectUidPermissionMapValues(appUids, INetd::PERMISSION_UPDATE_DEVICE_STATS);
    expectPrivilegedUserSet(appUids);

    std::vector<uid_t> uidToRemove = {TEST_UID};
    mTc.setPermissionForUids(INetd::PERMISSION_UNINSTALLED, uidToRemove);

    std::vector<uid_t> uidRemain = {TEST_UID3, TEST_UID2};
    expectUidPermissionMapValues(uidRemain, INetd::PERMISSION_UPDATE_DEVICE_STATS);
    expectPrivilegedUserSet(uidRemain);

    mTc.setPermissionForUids(INetd::PERMISSION_UNINSTALLED, uidRemain);
    expectMapEmpty(mFakeUidPermissionMap);
    expectPrivilegedUserSetEmpty();
}

TEST_F(TrafficControllerTest, TestGrantUpdateStatsPermission) {
    std::vector<uid_t> appUids = {TEST_UID, TEST_UID2, TEST_UID3};

    mTc.setPermissionForUids(INetd::PERMISSION_UPDATE_DEVICE_STATS, appUids);
    expectUidPermissionMapValues(appUids, INetd::PERMISSION_UPDATE_DEVICE_STATS);
    expectPrivilegedUserSet(appUids);

    mTc.setPermissionForUids(INetd::PERMISSION_NONE, appUids);
    expectPrivilegedUserSetEmpty();
    expectUidPermissionMapValues(appUids, INetd::PERMISSION_NONE);
}

TEST_F(TrafficControllerTest, TestRevokeUpdateStatsPermission) {
    std::vector<uid_t> appUids = {TEST_UID, TEST_UID2, TEST_UID3};

    mTc.setPermissionForUids(INetd::PERMISSION_UPDATE_DEVICE_STATS, appUids);
    expectPrivilegedUserSet(appUids);

    std::vector<uid_t> uidToRemove = {TEST_UID};
    mTc.setPermissionForUids(INetd::PERMISSION_NONE, uidToRemove);

    std::vector<uid_t> uidRemain = {TEST_UID3, TEST_UID2};
    expectPrivilegedUserSet(uidRemain);

    mTc.setPermissionForUids(INetd::PERMISSION_NONE, uidRemain);
    expectPrivilegedUserSetEmpty();
}

TEST_F(TrafficControllerTest, TestGrantWrongPermission) {
    std::vector<uid_t> appUids = {TEST_UID, TEST_UID2, TEST_UID3};

    mTc.setPermissionForUids(INetd::PERMISSION_NONE, appUids);
    expectPrivilegedUserSetEmpty();
    expectUidPermissionMapValues(appUids, INetd::PERMISSION_NONE);
}

TEST_F(TrafficControllerTest, TestGrantDuplicatePermissionSlientlyFail) {
    std::vector<uid_t> appUids = {TEST_UID, TEST_UID2, TEST_UID3};

    mTc.setPermissionForUids(INetd::PERMISSION_INTERNET, appUids);
    expectMapEmpty(mFakeUidPermissionMap);

    std::vector<uid_t> uidToAdd = {TEST_UID};
    mTc.setPermissionForUids(INetd::PERMISSION_INTERNET, uidToAdd);

    expectPrivilegedUserSetEmpty();

    mTc.setPermissionForUids(INetd::PERMISSION_NONE, appUids);
    expectUidPermissionMapValues(appUids, INetd::PERMISSION_NONE);

    mTc.setPermissionForUids(INetd::PERMISSION_UPDATE_DEVICE_STATS, appUids);
    expectPrivilegedUserSet(appUids);

    mTc.setPermissionForUids(INetd::PERMISSION_UPDATE_DEVICE_STATS, uidToAdd);
    expectPrivilegedUserSet(appUids);

    mTc.setPermissionForUids(INetd::PERMISSION_NONE, appUids);
    expectPrivilegedUserSetEmpty();
}

constexpr uint32_t SOCK_CLOSE_WAIT_US = 30 * 1000;
constexpr uint32_t ENOBUFS_POLL_WAIT_US = 10 * 1000;

using android::base::Error;
using android::base::Result;
using android::bpf::BpfMap;

// This test set up a SkDestroyListener that is running parallel with the production
// SkDestroyListener. The test will create thousands of sockets and tag them on the
// production cookieUidTagMap and close them in a short time. When the number of
// sockets get closed exceeds the buffer size, it will start to return ENOBUFF
// error. The error will be ignored by the production SkDestroyListener and the
// test will clean up the tags in tearDown if there is any remains.

// TODO: Instead of test the ENOBUFF error, we can test the production
// SkDestroyListener to see if it failed to delete a tagged socket when ENOBUFF
// triggered.
class NetlinkListenerTest : public testing::Test {
  protected:
    NetlinkListenerTest() {}
    BpfMap<uint64_t, UidTagValue> mCookieTagMap;

    void SetUp() {
        mCookieTagMap.init(COOKIE_TAG_MAP_PATH);
        ASSERT_TRUE(mCookieTagMap.isValid());
    }

    void TearDown() {
        const auto deleteTestCookieEntries = [](const uint64_t& key, const UidTagValue& value,
                                                BpfMap<uint64_t, UidTagValue>& map) {
            if ((value.uid == TEST_UID) && (value.tag == TEST_TAG)) {
                Result<void> res = map.deleteValue(key);
                if (res.ok() || (res.error().code() == ENOENT)) {
                    return Result<void>();
                }
                ALOGE("Failed to delete data(cookie = %" PRIu64 "): %s\n", key,
                      strerror(res.error().code()));
            }
            // Move forward to next cookie in the map.
            return Result<void>();
        };
        EXPECT_RESULT_OK(mCookieTagMap.iterateWithValue(deleteTestCookieEntries));
    }

    Result<void> checkNoGarbageTagsExist() {
        const auto checkGarbageTags = [](const uint64_t&, const UidTagValue& value,
                                         const BpfMap<uint64_t, UidTagValue>&) -> Result<void> {
            if ((TEST_UID == value.uid) && (TEST_TAG == value.tag)) {
                return Error(EUCLEAN) << "Closed socket is not untagged";
            }
            return {};
        };
        return mCookieTagMap.iterateWithValue(checkGarbageTags);
    }

    bool checkMassiveSocketDestroy(int totalNumber, bool expectError) {
        std::unique_ptr<android::netdutils::NetlinkListenerInterface> skDestroyListener;
        auto result = android::net::TrafficController::makeSkDestroyListener();
        if (!isOk(result)) {
            ALOGE("Unable to create SkDestroyListener: %s", toString(result).c_str());
        } else {
            skDestroyListener = std::move(result.value());
        }
        int rxErrorCount = 0;
        // Rx handler extracts nfgenmsg looks up and invokes registered dispatch function.
        const auto rxErrorHandler = [&rxErrorCount](const int, const int) { rxErrorCount++; };
        skDestroyListener->registerSkErrorHandler(rxErrorHandler);
        int fds[totalNumber];
        for (int i = 0; i < totalNumber; i++) {
            fds[i] = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC, 0);
            // The likely reason for a failure is running out of available file descriptors.
            EXPECT_LE(0, fds[i]) << i << " of " << totalNumber;
            if (fds[i] < 0) {
                // EXPECT_LE already failed above, so test case is a failure, but we don't
                // want potentially tens of thousands of extra failures creating and then
                // closing all these fds cluttering up the logs.
                totalNumber = i;
                break;
            };
            libnetd_updatable_tagSocket(fds[i], TEST_TAG, TEST_UID, 1000);
        }

        // TODO: Use a separate thread that has its own fd table so we can
        // close sockets even faster simply by terminating that thread.
        for (int i = 0; i < totalNumber; i++) {
            EXPECT_EQ(0, close(fds[i]));
        }
        // wait a bit for netlink listener to handle all the messages.
        usleep(SOCK_CLOSE_WAIT_US);
        if (expectError) {
            // If ENOBUFS triggered, check it only called into the handler once, ie.
            // that the netlink handler is not spinning.
            int currentErrorCount = rxErrorCount;
            // 0 error count is acceptable because the system has chances to close all sockets
            // normally.
            EXPECT_LE(0, rxErrorCount);
            if (!rxErrorCount) return true;

            usleep(ENOBUFS_POLL_WAIT_US);
            EXPECT_EQ(currentErrorCount, rxErrorCount);
        } else {
            EXPECT_RESULT_OK(checkNoGarbageTagsExist());
            EXPECT_EQ(0, rxErrorCount);
        }
        return false;
    }
};

TEST_F(NetlinkListenerTest, TestAllSocketUntagged) {
    checkMassiveSocketDestroy(10, false);
    checkMassiveSocketDestroy(100, false);
}

// Disabled because flaky on blueline-userdebug; this test relies on the main thread
// winning a race against the NetlinkListener::run() thread. There's no way to ensure
// things will be scheduled the same way across all architectures and test environments.
TEST_F(NetlinkListenerTest, DISABLED_TestSkDestroyError) {
    bool needRetry = false;
    int retryCount = 0;
    do {
        needRetry = checkMassiveSocketDestroy(32500, true);
        if (needRetry) retryCount++;
    } while (needRetry && retryCount < 3);
    // Should review test if it can always close all sockets correctly.
    EXPECT_GT(3, retryCount);
}


}  // namespace net
}  // namespace android
