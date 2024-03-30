// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "src/logd/LogEvent.h"

#include <gtest/gtest.h>

#include "frameworks/proto_logging/stats/atoms.pb.h"
#include "frameworks/proto_logging/stats/enums/stats/launcher/launcher.pb.h"
#include "log/log_event_list.h"
#include "stats_event.h"

#ifdef __ANDROID__

namespace android {
namespace os {
namespace statsd {

using std::string;
using std::vector;
using util::ProtoOutputStream;
using util::ProtoReader;

namespace {

Field getField(int32_t tag, const vector<int32_t>& pos, int32_t depth, const vector<bool>& last) {
    Field f(tag, (int32_t*)pos.data(), depth);

    // only decorate last position for depths with repeated fields (depth 1)
    if (depth > 0 && last[1]) f.decorateLastPos(1);

    return f;
}

void createStatsEvent(AStatsEvent* statsEvent, uint8_t typeId) {
    AStatsEvent_setAtomId(statsEvent, /*atomId=*/100);

    int int32Array[2] = {3, 6};
    uint32_t uids[] = {1001, 1002};
    const char* tags[] = {"tag1", "tag2"};

    switch (typeId) {
        case INT32_TYPE:
            AStatsEvent_writeInt32(statsEvent, 10);
            break;
        case INT64_TYPE:
            AStatsEvent_writeInt64(statsEvent, 1000L);
            break;
        case STRING_TYPE:
            AStatsEvent_writeString(statsEvent, "test");
            break;
        case LIST_TYPE:
            AStatsEvent_writeInt32Array(statsEvent, int32Array, 2);
            break;
        case FLOAT_TYPE:
            AStatsEvent_writeFloat(statsEvent, 1.3f);
            break;
        case BOOL_TYPE:
            AStatsEvent_writeBool(statsEvent, 1);
            break;
        case BYTE_ARRAY_TYPE:
            AStatsEvent_writeByteArray(statsEvent, (uint8_t*)"test", strlen("test"));
            break;
        case ATTRIBUTION_CHAIN_TYPE:
            AStatsEvent_writeAttributionChain(statsEvent, uids, tags, 2);
            break;
        default:
            break;
    }
}

void createFieldWithBoolAnnotationLogEvent(LogEvent* logEvent, uint8_t typeId, uint8_t annotationId,
                                           bool annotationValue, bool parseBufferResult) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    createStatsEvent(statsEvent, typeId);
    AStatsEvent_addBoolAnnotation(statsEvent, annotationId, annotationValue);
    AStatsEvent_build(statsEvent);

    size_t size;
    uint8_t* buf = AStatsEvent_getBuffer(statsEvent, &size);
    EXPECT_EQ(parseBufferResult, logEvent->parseBuffer(buf, size));

    AStatsEvent_release(statsEvent);
}

void createFieldWithIntAnnotationLogEvent(LogEvent* logEvent, uint8_t typeId, uint8_t annotationId,
                                          int annotationValue, bool parseBufferResult) {
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    createStatsEvent(statsEvent, typeId);
    AStatsEvent_addInt32Annotation(statsEvent, annotationId, annotationValue);
    AStatsEvent_build(statsEvent);

    size_t size;
    uint8_t* buf = AStatsEvent_getBuffer(statsEvent, &size);
    EXPECT_EQ(parseBufferResult, logEvent->parseBuffer(buf, size));

    AStatsEvent_release(statsEvent);
}

}  // anonymous namespace

// Setup for parameterized tests.
class LogEventTestBadAnnotationFieldTypes : public testing::TestWithParam<int> {
public:
    static std::string ToString(testing::TestParamInfo<int> info) {
        switch (info.param) {
            case INT32_TYPE:
                return "Int32";
            case INT64_TYPE:
                return "Int64";
            case STRING_TYPE:
                return "String";
            case LIST_TYPE:
                return "List";
            case FLOAT_TYPE:
                return "Float";
            case BYTE_ARRAY_TYPE:
                return "ByteArray";
            case ATTRIBUTION_CHAIN_TYPE:
                return "AttributionChain";
            default:
                return "Unknown";
        }
    }
};

// TODO(b/222539899): Add BOOL_TYPE value once parseAnnotations is updated to check specific
// typeIds. BOOL_TYPE should be a bad field type for is_uid, nested, and reset state annotations.
INSTANTIATE_TEST_SUITE_P(BadAnnotationFieldTypes, LogEventTestBadAnnotationFieldTypes,
                         testing::Values(INT32_TYPE, INT64_TYPE, STRING_TYPE, LIST_TYPE, FLOAT_TYPE,
                                         BYTE_ARRAY_TYPE, ATTRIBUTION_CHAIN_TYPE),
                         LogEventTestBadAnnotationFieldTypes::ToString);

TEST(LogEventTest, TestPrimitiveParsing) {
    AStatsEvent* event = AStatsEvent_obtain();
    AStatsEvent_setAtomId(event, 100);
    AStatsEvent_writeInt32(event, 10);
    AStatsEvent_writeInt64(event, 0x123456789);
    AStatsEvent_writeFloat(event, 2.0);
    AStatsEvent_writeBool(event, true);
    AStatsEvent_build(event);

    size_t size;
    uint8_t* buf = AStatsEvent_getBuffer(event, &size);

    LogEvent logEvent(/*uid=*/1000, /*pid=*/1001);
    EXPECT_TRUE(logEvent.parseBuffer(buf, size));

    EXPECT_EQ(100, logEvent.GetTagId());
    EXPECT_EQ(1000, logEvent.GetUid());
    EXPECT_EQ(1001, logEvent.GetPid());
    EXPECT_FALSE(logEvent.hasAttributionChain());

    const vector<FieldValue>& values = logEvent.getValues();
    ASSERT_EQ(4, values.size());

    const FieldValue& int32Item = values[0];
    Field expectedField = getField(100, {1, 1, 1}, 0, {false, false, false});
    EXPECT_EQ(expectedField, int32Item.mField);
    EXPECT_EQ(Type::INT, int32Item.mValue.getType());
    EXPECT_EQ(10, int32Item.mValue.int_value);

    const FieldValue& int64Item = values[1];
    expectedField = getField(100, {2, 1, 1}, 0, {false, false, false});
    EXPECT_EQ(expectedField, int64Item.mField);
    EXPECT_EQ(Type::LONG, int64Item.mValue.getType());
    EXPECT_EQ(0x123456789, int64Item.mValue.long_value);

    const FieldValue& floatItem = values[2];
    expectedField = getField(100, {3, 1, 1}, 0, {false, false, false});
    EXPECT_EQ(expectedField, floatItem.mField);
    EXPECT_EQ(Type::FLOAT, floatItem.mValue.getType());
    EXPECT_EQ(2.0, floatItem.mValue.float_value);

    const FieldValue& boolItem = values[3];
    expectedField = getField(100, {4, 1, 1}, 0, {true, false, false});
    EXPECT_EQ(expectedField, boolItem.mField);
    EXPECT_EQ(Type::INT, boolItem.mValue.getType());  // FieldValue does not support boolean type
    EXPECT_EQ(1, boolItem.mValue.int_value);

    AStatsEvent_release(event);
}

TEST(LogEventTest, TestStringAndByteArrayParsing) {
    AStatsEvent* event = AStatsEvent_obtain();
    AStatsEvent_setAtomId(event, 100);
    string str = "test";
    AStatsEvent_writeString(event, str.c_str());
    AStatsEvent_writeByteArray(event, (uint8_t*)str.c_str(), str.length());
    AStatsEvent_build(event);

    size_t size;
    uint8_t* buf = AStatsEvent_getBuffer(event, &size);

    LogEvent logEvent(/*uid=*/1000, /*pid=*/1001);
    EXPECT_TRUE(logEvent.parseBuffer(buf, size));

    EXPECT_EQ(100, logEvent.GetTagId());
    EXPECT_EQ(1000, logEvent.GetUid());
    EXPECT_EQ(1001, logEvent.GetPid());
    EXPECT_FALSE(logEvent.hasAttributionChain());

    const vector<FieldValue>& values = logEvent.getValues();
    ASSERT_EQ(2, values.size());

    const FieldValue& stringItem = values[0];
    Field expectedField = getField(100, {1, 1, 1}, 0, {false, false, false});
    EXPECT_EQ(expectedField, stringItem.mField);
    EXPECT_EQ(Type::STRING, stringItem.mValue.getType());
    EXPECT_EQ(str, stringItem.mValue.str_value);

    const FieldValue& storageItem = values[1];
    expectedField = getField(100, {2, 1, 1}, 0, {true, false, false});
    EXPECT_EQ(expectedField, storageItem.mField);
    EXPECT_EQ(Type::STORAGE, storageItem.mValue.getType());
    vector<uint8_t> expectedValue = {'t', 'e', 's', 't'};
    EXPECT_EQ(expectedValue, storageItem.mValue.storage_value);

    AStatsEvent_release(event);
}

TEST(LogEventTest, TestEmptyString) {
    AStatsEvent* event = AStatsEvent_obtain();
    AStatsEvent_setAtomId(event, 100);
    string empty = "";
    AStatsEvent_writeString(event, empty.c_str());
    AStatsEvent_build(event);

    size_t size;
    uint8_t* buf = AStatsEvent_getBuffer(event, &size);

    LogEvent logEvent(/*uid=*/1000, /*pid=*/1001);
    EXPECT_TRUE(logEvent.parseBuffer(buf, size));

    EXPECT_EQ(100, logEvent.GetTagId());
    EXPECT_EQ(1000, logEvent.GetUid());
    EXPECT_EQ(1001, logEvent.GetPid());
    EXPECT_FALSE(logEvent.hasAttributionChain());

    const vector<FieldValue>& values = logEvent.getValues();
    ASSERT_EQ(1, values.size());

    const FieldValue& item = values[0];
    Field expectedField = getField(100, {1, 1, 1}, 0, {true, false, false});
    EXPECT_EQ(expectedField, item.mField);
    EXPECT_EQ(Type::STRING, item.mValue.getType());
    EXPECT_EQ(empty, item.mValue.str_value);

    AStatsEvent_release(event);
}

TEST(LogEventTest, TestByteArrayWithNullCharacter) {
    AStatsEvent* event = AStatsEvent_obtain();
    AStatsEvent_setAtomId(event, 100);
    uint8_t message[] = {'\t', 'e', '\0', 's', 't'};
    AStatsEvent_writeByteArray(event, message, 5);
    AStatsEvent_build(event);

    size_t size;
    uint8_t* buf = AStatsEvent_getBuffer(event, &size);

    LogEvent logEvent(/*uid=*/1000, /*pid=*/1001);
    EXPECT_TRUE(logEvent.parseBuffer(buf, size));

    EXPECT_EQ(100, logEvent.GetTagId());
    EXPECT_EQ(1000, logEvent.GetUid());
    EXPECT_EQ(1001, logEvent.GetPid());

    const vector<FieldValue>& values = logEvent.getValues();
    ASSERT_EQ(1, values.size());

    const FieldValue& item = values[0];
    Field expectedField = getField(100, {1, 1, 1}, 0, {true, false, false});
    EXPECT_EQ(expectedField, item.mField);
    EXPECT_EQ(Type::STORAGE, item.mValue.getType());
    vector<uint8_t> expectedValue(message, message + 5);
    EXPECT_EQ(expectedValue, item.mValue.storage_value);

    AStatsEvent_release(event);
}

TEST(LogEventTest, TestTooManyTopLevelElements) {
    int32_t numElements = 128;
    AStatsEvent* event = AStatsEvent_obtain();
    AStatsEvent_setAtomId(event, 100);

    for (int i = 0; i < numElements; i++) {
        AStatsEvent_writeInt32(event, i);
    }

    AStatsEvent_build(event);

    size_t size;
    uint8_t* buf = AStatsEvent_getBuffer(event, &size);
    LogEvent logEvent(/*uid=*/1000, /*pid=*/1001);
    EXPECT_FALSE(logEvent.parseBuffer(buf, size));

    AStatsEvent_release(event);
}

TEST(LogEventTest, TestAttributionChain) {
    AStatsEvent* event = AStatsEvent_obtain();
    AStatsEvent_setAtomId(event, 100);

    string tag1 = "tag1";
    string tag2 = "tag2";

    uint32_t uids[] = {1001, 1002};
    const char* tags[] = {tag1.c_str(), tag2.c_str()};

    AStatsEvent_writeAttributionChain(event, uids, tags, 2);
    AStatsEvent_build(event);

    size_t size;
    uint8_t* buf = AStatsEvent_getBuffer(event, &size);

    LogEvent logEvent(/*uid=*/1000, /*pid=*/1001);
    EXPECT_TRUE(logEvent.parseBuffer(buf, size));

    EXPECT_EQ(100, logEvent.GetTagId());
    EXPECT_EQ(1000, logEvent.GetUid());
    EXPECT_EQ(1001, logEvent.GetPid());

    const vector<FieldValue>& values = logEvent.getValues();
    ASSERT_EQ(4, values.size());  // 2 per attribution node

    std::pair<size_t, size_t> attrIndexRange;
    EXPECT_TRUE(logEvent.hasAttributionChain(&attrIndexRange));
    EXPECT_EQ(0, attrIndexRange.first);
    EXPECT_EQ(3, attrIndexRange.second);

    // Check first attribution node
    const FieldValue& uid1Item = values[0];
    Field expectedField = getField(100, {1, 1, 1}, 2, {true, false, false});
    EXPECT_EQ(expectedField, uid1Item.mField);
    EXPECT_EQ(Type::INT, uid1Item.mValue.getType());
    EXPECT_EQ(1001, uid1Item.mValue.int_value);

    const FieldValue& tag1Item = values[1];
    expectedField = getField(100, {1, 1, 2}, 2, {true, false, true});
    EXPECT_EQ(expectedField, tag1Item.mField);
    EXPECT_EQ(Type::STRING, tag1Item.mValue.getType());
    EXPECT_EQ(tag1, tag1Item.mValue.str_value);

    // Check second attribution nodes
    const FieldValue& uid2Item = values[2];
    expectedField = getField(100, {1, 2, 1}, 2, {true, true, false});
    EXPECT_EQ(expectedField, uid2Item.mField);
    EXPECT_EQ(Type::INT, uid2Item.mValue.getType());
    EXPECT_EQ(1002, uid2Item.mValue.int_value);

    const FieldValue& tag2Item = values[3];
    expectedField = getField(100, {1, 2, 2}, 2, {true, true, true});
    EXPECT_EQ(expectedField, tag2Item.mField);
    EXPECT_EQ(Type::STRING, tag2Item.mValue.getType());
    EXPECT_EQ(tag2, tag2Item.mValue.str_value);

    AStatsEvent_release(event);
}

TEST(LogEventTest, TestEmptyAttributionChain) {
    AStatsEvent* event = AStatsEvent_obtain();
    AStatsEvent_setAtomId(event, 100);

    AStatsEvent_writeAttributionChain(event, {}, {}, 0);
    AStatsEvent_writeInt32(event, 10);
    AStatsEvent_build(event);

    size_t size;
    uint8_t* buf = AStatsEvent_getBuffer(event, &size);

    LogEvent logEvent(/*uid=*/1000, /*pid=*/1001);
    EXPECT_FALSE(logEvent.parseBuffer(buf, size));

    AStatsEvent_release(event);
}

TEST(LogEventTest, TestAttributionChainTooManyElements) {
    int32_t numNodes = 128;
    uint32_t uids[numNodes];
    vector<string> tags(numNodes);  // storage that cTag elements point to
    const char* cTags[numNodes];

    for (int i = 0; i < numNodes; i++) {
        uids[i] = i;
        tags.push_back("test");
        cTags[i] = tags[i].c_str();
    }

    AStatsEvent* event = AStatsEvent_obtain();
    AStatsEvent_setAtomId(event, 100);
    AStatsEvent_writeAttributionChain(event, uids, cTags, numNodes);
    AStatsEvent_build(event);

    size_t size;
    uint8_t* buf = AStatsEvent_getBuffer(event, &size);
    LogEvent logEvent(/*uid=*/1000, /*pid=*/1001);
    EXPECT_FALSE(logEvent.parseBuffer(buf, size));

    AStatsEvent_release(event);
}

TEST(LogEventTest, TestArrayParsing) {
    size_t numElements = 2;
    int32_t int32Array[2] = {3, 6};
    int64_t int64Array[2] = {1000L, 1002L};
    float floatArray[2] = {0.3f, 0.09f};
    bool boolArray[2] = {0, 1};

    vector<string> stringArray = {"str1", "str2"};
    const char* cStringArray[2];
    for (int i = 0; i < numElements; i++) {
        cStringArray[i] = stringArray[i].c_str();
    }

    AStatsEvent* event = AStatsEvent_obtain();
    AStatsEvent_setAtomId(event, 100);
    AStatsEvent_writeInt32Array(event, int32Array, numElements);
    AStatsEvent_writeInt64Array(event, int64Array, numElements);
    AStatsEvent_writeFloatArray(event, floatArray, numElements);
    AStatsEvent_writeBoolArray(event, boolArray, numElements);
    AStatsEvent_writeStringArray(event, cStringArray, numElements);
    AStatsEvent_build(event);

    size_t size;
    uint8_t* buf = AStatsEvent_getBuffer(event, &size);

    LogEvent logEvent(/*uid=*/1000, /*pid=*/1001);
    EXPECT_TRUE(logEvent.parseBuffer(buf, size));

    EXPECT_EQ(100, logEvent.GetTagId());
    EXPECT_EQ(1000, logEvent.GetUid());
    EXPECT_EQ(1001, logEvent.GetPid());
    EXPECT_FALSE(logEvent.hasAttributionChain());

    const vector<FieldValue>& values = logEvent.getValues();
    ASSERT_EQ(10, values.size());  // 2 for each array type

    const FieldValue& int32ArrayItem1 = values[0];
    Field expectedField = getField(100, {1, 1, 1}, 1, {false, false, false});
    EXPECT_EQ(expectedField, int32ArrayItem1.mField);
    EXPECT_EQ(Type::INT, int32ArrayItem1.mValue.getType());
    EXPECT_EQ(3, int32ArrayItem1.mValue.int_value);

    const FieldValue& int32ArrayItem2 = values[1];
    expectedField = getField(100, {1, 2, 1}, 1, {false, true, false});
    EXPECT_EQ(expectedField, int32ArrayItem2.mField);
    EXPECT_EQ(Type::INT, int32ArrayItem2.mValue.getType());
    EXPECT_EQ(6, int32ArrayItem2.mValue.int_value);

    const FieldValue& int64ArrayItem1 = values[2];
    expectedField = getField(100, {2, 1, 1}, 1, {false, false, false});
    EXPECT_EQ(expectedField, int64ArrayItem1.mField);
    EXPECT_EQ(Type::LONG, int64ArrayItem1.mValue.getType());
    EXPECT_EQ(1000L, int64ArrayItem1.mValue.long_value);

    const FieldValue& int64ArrayItem2 = values[3];
    expectedField = getField(100, {2, 2, 1}, 1, {false, true, false});
    EXPECT_EQ(expectedField, int64ArrayItem2.mField);
    EXPECT_EQ(Type::LONG, int64ArrayItem2.mValue.getType());
    EXPECT_EQ(1002L, int64ArrayItem2.mValue.long_value);

    const FieldValue& floatArrayItem1 = values[4];
    expectedField = getField(100, {3, 1, 1}, 1, {false, false, false});
    EXPECT_EQ(expectedField, floatArrayItem1.mField);
    EXPECT_EQ(Type::FLOAT, floatArrayItem1.mValue.getType());
    EXPECT_EQ(0.3f, floatArrayItem1.mValue.float_value);

    const FieldValue& floatArrayItem2 = values[5];
    expectedField = getField(100, {3, 2, 1}, 1, {false, true, false});
    EXPECT_EQ(expectedField, floatArrayItem2.mField);
    EXPECT_EQ(Type::FLOAT, floatArrayItem2.mValue.getType());
    EXPECT_EQ(0.09f, floatArrayItem2.mValue.float_value);

    const FieldValue& boolArrayItem1 = values[6];
    expectedField = getField(100, {4, 1, 1}, 1, {false, false, false});
    EXPECT_EQ(expectedField, boolArrayItem1.mField);
    EXPECT_EQ(Type::INT,
              boolArrayItem1.mValue.getType());  // FieldValue does not support boolean type
    EXPECT_EQ(false, boolArrayItem1.mValue.int_value);

    const FieldValue& boolArrayItem2 = values[7];
    expectedField = getField(100, {4, 2, 1}, 1, {false, true, false});
    EXPECT_EQ(expectedField, boolArrayItem2.mField);
    EXPECT_EQ(Type::INT,
              boolArrayItem2.mValue.getType());  // FieldValue does not support boolean type
    EXPECT_EQ(true, boolArrayItem2.mValue.int_value);

    const FieldValue& stringArrayItem1 = values[8];
    expectedField = getField(100, {5, 1, 1}, 1, {true, false, false});
    EXPECT_EQ(expectedField, stringArrayItem1.mField);
    EXPECT_EQ(Type::STRING, stringArrayItem1.mValue.getType());
    EXPECT_EQ("str1", stringArrayItem1.mValue.str_value);

    const FieldValue& stringArrayItem2 = values[9];
    expectedField = getField(100, {5, 2, 1}, 1, {true, true, false});
    EXPECT_EQ(expectedField, stringArrayItem2.mField);
    EXPECT_EQ(Type::STRING, stringArrayItem2.mValue.getType());
    EXPECT_EQ("str2", stringArrayItem2.mValue.str_value);
}

TEST(LogEventTest, TestEmptyStringArray) {
    const char* cStringArray[2];
    string empty = "";
    cStringArray[0] = empty.c_str();
    cStringArray[1] = empty.c_str();

    AStatsEvent* event = AStatsEvent_obtain();
    AStatsEvent_setAtomId(event, 100);
    AStatsEvent_writeStringArray(event, cStringArray, 2);
    AStatsEvent_build(event);

    size_t size;
    uint8_t* buf = AStatsEvent_getBuffer(event, &size);

    LogEvent logEvent(/*uid=*/1000, /*pid=*/1001);
    EXPECT_TRUE(logEvent.parseBuffer(buf, size));

    EXPECT_EQ(100, logEvent.GetTagId());
    EXPECT_EQ(1000, logEvent.GetUid());
    EXPECT_EQ(1001, logEvent.GetPid());

    const vector<FieldValue>& values = logEvent.getValues();
    ASSERT_EQ(2, values.size());

    const FieldValue& stringArrayItem1 = values[0];
    Field expectedField = getField(100, {1, 1, 1}, 1, {true, false, false});
    EXPECT_EQ(expectedField, stringArrayItem1.mField);
    EXPECT_EQ(Type::STRING, stringArrayItem1.mValue.getType());
    EXPECT_EQ(empty, stringArrayItem1.mValue.str_value);

    const FieldValue& stringArrayItem2 = values[1];
    expectedField = getField(100, {1, 2, 1}, 1, {true, true, false});
    EXPECT_EQ(expectedField, stringArrayItem2.mField);
    EXPECT_EQ(Type::STRING, stringArrayItem2.mValue.getType());
    EXPECT_EQ(empty, stringArrayItem2.mValue.str_value);

    AStatsEvent_release(event);
}

TEST(LogEventTest, TestArrayTooManyElements) {
    int32_t numElements = 128;
    int32_t int32Array[numElements];

    for (int i = 0; i < numElements; i++) {
        int32Array[i] = 1;
    }

    AStatsEvent* event = AStatsEvent_obtain();
    AStatsEvent_setAtomId(event, 100);
    AStatsEvent_writeInt32Array(event, int32Array, numElements);
    AStatsEvent_build(event);

    size_t size;
    uint8_t* buf = AStatsEvent_getBuffer(event, &size);

    LogEvent logEvent(/*uid=*/1000, /*pid=*/1001);
    EXPECT_FALSE(logEvent.parseBuffer(buf, size));

    AStatsEvent_release(event);
}

TEST(LogEventTest, TestEmptyArray) {
    int32_t int32Array[0] = {};

    AStatsEvent* event = AStatsEvent_obtain();
    AStatsEvent_setAtomId(event, 100);
    AStatsEvent_writeInt32Array(event, int32Array, 0);
    AStatsEvent_build(event);

    size_t size;
    uint8_t* buf = AStatsEvent_getBuffer(event, &size);

    LogEvent logEvent(/*uid=*/1000, /*pid=*/1001);
    EXPECT_TRUE(logEvent.parseBuffer(buf, size));

    EXPECT_EQ(100, logEvent.GetTagId());
    EXPECT_EQ(1000, logEvent.GetUid());
    EXPECT_EQ(1001, logEvent.GetPid());

    const vector<FieldValue>& values = logEvent.getValues();
    ASSERT_EQ(0, values.size());

    AStatsEvent_release(event);
}

TEST(LogEventTest, TestAnnotationIdIsUid) {
    LogEvent event(/*uid=*/0, /*pid=*/0);
    createFieldWithBoolAnnotationLogEvent(&event, INT32_TYPE, ANNOTATION_ID_IS_UID, true,
                                          /*parseBufferResult*/ true);

    ASSERT_EQ(event.getNumUidFields(), 1);

    const vector<FieldValue>& values = event.getValues();
    ASSERT_EQ(values.size(), 1);
    EXPECT_TRUE(isUidField(values.at(0)));
}

TEST(LogEventTest, TestAnnotationIdIsUid_RepeatedIntAndOtherFields) {
    size_t numElements = 2;
    int32_t int32Array[2] = {3, 6};

    vector<string> stringArray = {"str1", "str2"};
    const char* cStringArray[2];
    for (int i = 0; i < numElements; i++) {
        cStringArray[i] = stringArray[i].c_str();
    }

    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, 100);
    AStatsEvent_writeInt32(statsEvent, 5);
    AStatsEvent_writeInt32Array(statsEvent, int32Array, numElements);
    AStatsEvent_addBoolAnnotation(statsEvent, ANNOTATION_ID_IS_UID, true);
    AStatsEvent_writeStringArray(statsEvent, cStringArray, numElements);
    AStatsEvent_build(statsEvent);

    size_t size;
    uint8_t* buf = AStatsEvent_getBuffer(statsEvent, &size);
    LogEvent logEvent(/*uid=*/1000, /*pid=*/1001);
    EXPECT_TRUE(logEvent.parseBuffer(buf, size));
    EXPECT_EQ(2, logEvent.getNumUidFields());

    const vector<FieldValue>& values = logEvent.getValues();
    ASSERT_EQ(values.size(), 5);
    EXPECT_FALSE(isUidField(values.at(0)));
    EXPECT_TRUE(isUidField(values.at(1)));
    EXPECT_TRUE(isUidField(values.at(2)));
    EXPECT_FALSE(isUidField(values.at(3)));
    EXPECT_FALSE(isUidField(values.at(4)));
}

TEST(LogEventTest, TestAnnotationIdIsUid_RepeatedIntOneEntry) {
    size_t numElements = 1;
    int32_t int32Array[1] = {3};

    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, 100);
    AStatsEvent_writeInt32Array(statsEvent, int32Array, numElements);
    AStatsEvent_addBoolAnnotation(statsEvent, ANNOTATION_ID_IS_UID, true);
    AStatsEvent_build(statsEvent);

    size_t size;
    uint8_t* buf = AStatsEvent_getBuffer(statsEvent, &size);
    LogEvent logEvent(/*uid=*/1000, /*pid=*/1001);
    EXPECT_TRUE(logEvent.parseBuffer(buf, size));
    EXPECT_EQ(1, logEvent.getNumUidFields());

    const vector<FieldValue>& values = logEvent.getValues();
    ASSERT_EQ(values.size(), 1);
    EXPECT_TRUE(isUidField(values.at(0)));
}

TEST(LogEventTest, TestAnnotationIdIsUid_EmptyIntArray) {
    int32_t int32Array[0] = {};

    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, 100);
    AStatsEvent_writeInt32Array(statsEvent, int32Array, /*numElements*/ 0);
    AStatsEvent_addBoolAnnotation(statsEvent, ANNOTATION_ID_IS_UID, true);
    AStatsEvent_writeInt32(statsEvent, 5);
    AStatsEvent_build(statsEvent);

    size_t size;
    uint8_t* buf = AStatsEvent_getBuffer(statsEvent, &size);
    LogEvent logEvent(/*uid=*/1000, /*pid=*/1001);
    EXPECT_TRUE(logEvent.parseBuffer(buf, size));
    EXPECT_EQ(0, logEvent.getNumUidFields());

    const vector<FieldValue>& values = logEvent.getValues();
    EXPECT_EQ(values.size(), 1);
}

TEST(LogEventTest, TestAnnotationIdIsUid_BadRepeatedInt64) {
    int64_t int64Array[2] = {1000L, 1002L};

    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, /*atomId=*/100);
    AStatsEvent_writeInt64Array(statsEvent, int64Array, /*numElements*/ 2);
    AStatsEvent_addBoolAnnotation(statsEvent, ANNOTATION_ID_IS_UID, true);
    AStatsEvent_build(statsEvent);

    size_t size;
    uint8_t* buf = AStatsEvent_getBuffer(statsEvent, &size);
    LogEvent logEvent(/*uid=*/1000, /*pid=*/1001);

    EXPECT_FALSE(logEvent.parseBuffer(buf, size));
    EXPECT_EQ(0, logEvent.getNumUidFields());

    AStatsEvent_release(statsEvent);
}

TEST(LogEventTest, TestAnnotationIdIsUid_BadRepeatedString) {
    size_t numElements = 2;
    vector<string> stringArray = {"str1", "str2"};
    const char* cStringArray[2];
    for (int i = 0; i < numElements; i++) {
        cStringArray[i] = stringArray[i].c_str();
    }

    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, /*atomId=*/100);
    AStatsEvent_writeStringArray(statsEvent, cStringArray, /*numElements*/ 2);
    AStatsEvent_addBoolAnnotation(statsEvent, ANNOTATION_ID_IS_UID, true);
    AStatsEvent_build(statsEvent);

    size_t size;
    uint8_t* buf = AStatsEvent_getBuffer(statsEvent, &size);
    LogEvent logEvent(/*uid=*/1000, /*pid=*/1001);

    EXPECT_FALSE(logEvent.parseBuffer(buf, size));
    EXPECT_EQ(0, logEvent.getNumUidFields());

    AStatsEvent_release(statsEvent);
}

TEST_P(LogEventTestBadAnnotationFieldTypes, TestAnnotationIdIsUid) {
    LogEvent event(/*uid=*/0, /*pid=*/0);

    if (GetParam() != INT32_TYPE && GetParam() != LIST_TYPE) {
        createFieldWithBoolAnnotationLogEvent(&event, GetParam(), ANNOTATION_ID_IS_UID, true,
                                              /*parseBufferResult*/ false);
    }
}

TEST(LogEventTest, TestAnnotationIdIsUid_NotIntAnnotation) {
    LogEvent event(/*uid=*/0, /*pid=*/0);
    createFieldWithIntAnnotationLogEvent(&event, INT32_TYPE, ANNOTATION_ID_IS_UID, 10,
                                         /*parseBufferResult*/ false);
}

TEST(LogEventTest, TestAnnotationIdStateNested) {
    LogEvent event(/*uid=*/0, /*pid=*/0);
    createFieldWithBoolAnnotationLogEvent(&event, INT32_TYPE, ANNOTATION_ID_STATE_NESTED, true,
                                          /*parseBufferResult*/ true);

    const vector<FieldValue>& values = event.getValues();
    ASSERT_EQ(values.size(), 1);
    EXPECT_TRUE(values[0].mAnnotations.isNested());
}

TEST_P(LogEventTestBadAnnotationFieldTypes, TestAnnotationIdStateNested) {
    LogEvent event(/*uid=*/0, /*pid=*/0);

    if (GetParam() != INT32_TYPE) {
        createFieldWithBoolAnnotationLogEvent(&event, GetParam(), ANNOTATION_ID_STATE_NESTED, true,
                                              /*parseBufferResult*/ false);
    }
}

TEST(LogEventTest, TestAnnotationIdStateNested_NotIntAnnotation) {
    LogEvent event(/*uid=*/0, /*pid=*/0);
    createFieldWithIntAnnotationLogEvent(&event, INT32_TYPE, ANNOTATION_ID_STATE_NESTED, 10,
                                         /*parseBufferResult*/ false);
}

TEST(LogEventTest, TestPrimaryFieldAnnotation) {
    LogEvent event(/*uid=*/0, /*pid=*/0);
    createFieldWithBoolAnnotationLogEvent(&event, INT32_TYPE, ANNOTATION_ID_PRIMARY_FIELD, true,
                                          /*parseBufferResult*/ true);

    const vector<FieldValue>& values = event.getValues();
    ASSERT_EQ(values.size(), 1);
    EXPECT_TRUE(values[0].mAnnotations.isPrimaryField());
}

TEST_P(LogEventTestBadAnnotationFieldTypes, TestPrimaryFieldAnnotation) {
    LogEvent event(/*uid=*/0, /*pid=*/0);

    if (GetParam() == LIST_TYPE || GetParam() == ATTRIBUTION_CHAIN_TYPE) {
        createFieldWithBoolAnnotationLogEvent(&event, GetParam(), ANNOTATION_ID_PRIMARY_FIELD, true,
                                              /*parseBufferResult*/ false);
    }
}

TEST(LogEventTest, TestPrimaryFieldAnnotation_NotIntAnnotation) {
    LogEvent event(/*uid=*/0, /*pid=*/0);
    createFieldWithIntAnnotationLogEvent(&event, INT32_TYPE, ANNOTATION_ID_PRIMARY_FIELD, 10,
                                         /*parseBufferResult*/ false);
}

TEST(LogEventTest, TestExclusiveStateAnnotation) {
    LogEvent event(/*uid=*/0, /*pid=*/0);
    createFieldWithBoolAnnotationLogEvent(&event, INT32_TYPE, ANNOTATION_ID_EXCLUSIVE_STATE, true,
                                          /*parseBufferResult*/ true);

    const vector<FieldValue>& values = event.getValues();
    ASSERT_EQ(values.size(), 1);
    EXPECT_TRUE(values[0].mAnnotations.isExclusiveState());
}

TEST_P(LogEventTestBadAnnotationFieldTypes, TestExclusiveStateAnnotation) {
    LogEvent event(/*uid=*/0, /*pid=*/0);

    if (GetParam() != INT32_TYPE) {
        createFieldWithBoolAnnotationLogEvent(&event, GetParam(), ANNOTATION_ID_EXCLUSIVE_STATE,
                                              true,
                                              /*parseBufferResult*/ false);
    }
}

TEST(LogEventTest, TestExclusiveStateAnnotation_NotIntAnnotation) {
    LogEvent event(/*uid=*/0, /*pid=*/0);
    createFieldWithIntAnnotationLogEvent(&event, INT32_TYPE, ANNOTATION_ID_EXCLUSIVE_STATE, 10,
                                         /*parseBufferResult*/ false);
}

TEST(LogEventTest, TestPrimaryFieldFirstUidAnnotation) {
    // Event has 10 ints and then an attribution chain
    int numInts = 10;
    int firstUidInChainIndex = numInts;
    string tag1 = "tag1";
    string tag2 = "tag2";
    uint32_t uids[] = {1001, 1002};
    const char* tags[] = {tag1.c_str(), tag2.c_str()};

    // Construct AStatsEvent
    AStatsEvent* statsEvent = AStatsEvent_obtain();
    AStatsEvent_setAtomId(statsEvent, 100);
    for (int i = 0; i < numInts; i++) {
        AStatsEvent_writeInt32(statsEvent, 10);
    }
    AStatsEvent_writeAttributionChain(statsEvent, uids, tags, 2);
    AStatsEvent_addBoolAnnotation(statsEvent, ANNOTATION_ID_PRIMARY_FIELD_FIRST_UID, true);
    AStatsEvent_build(statsEvent);

    // Construct LogEvent
    size_t size;
    uint8_t* buf = AStatsEvent_getBuffer(statsEvent, &size);
    LogEvent logEvent(/*uid=*/0, /*pid=*/0);
    EXPECT_TRUE(logEvent.parseBuffer(buf, size));
    AStatsEvent_release(statsEvent);

    // Check annotation
    const vector<FieldValue>& values = logEvent.getValues();
    ASSERT_EQ(values.size(), numInts + 4);
    EXPECT_TRUE(values[firstUidInChainIndex].mAnnotations.isPrimaryField());
}

TEST_P(LogEventTestBadAnnotationFieldTypes, TestPrimaryFieldFirstUidAnnotation) {
    LogEvent event(/*uid=*/0, /*pid=*/0);

    if (GetParam() != ATTRIBUTION_CHAIN_TYPE) {
        createFieldWithBoolAnnotationLogEvent(&event, GetParam(),
                                              ANNOTATION_ID_PRIMARY_FIELD_FIRST_UID, true,
                                              /*parseBufferResult*/ false);
    }
}

TEST(LogEventTest, TestPrimaryFieldFirstUidAnnotation_NotIntAnnotation) {
    LogEvent event(/*uid=*/0, /*pid=*/0);
    createFieldWithIntAnnotationLogEvent(&event, ATTRIBUTION_CHAIN_TYPE,
                                         ANNOTATION_ID_PRIMARY_FIELD_FIRST_UID, 10,
                                         /*parseBufferResult*/ false);
}

TEST(LogEventTest, TestResetStateAnnotation) {
    int32_t resetState = 10;
    LogEvent event(/*uid=*/0, /*pid=*/0);
    createFieldWithIntAnnotationLogEvent(&event, INT32_TYPE, ANNOTATION_ID_TRIGGER_STATE_RESET,
                                         resetState, /*parseBufferResult*/ true);

    const vector<FieldValue>& values = event.getValues();
    ASSERT_EQ(values.size(), 1);
    EXPECT_EQ(event.getResetState(), resetState);
}

TEST_P(LogEventTestBadAnnotationFieldTypes, TestResetStateAnnotation) {
    LogEvent event(/*uid=*/0, /*pid=*/0);
    int32_t resetState = 10;

    if (GetParam() != INT32_TYPE) {
        createFieldWithIntAnnotationLogEvent(&event, GetParam(), ANNOTATION_ID_TRIGGER_STATE_RESET,
                                             resetState,
                                             /*parseBufferResult*/ false);
    }
}

TEST(LogEventTest, TestResetStateAnnotation_NotBoolAnnotation) {
    LogEvent event(/*uid=*/0, /*pid=*/0);
    createFieldWithBoolAnnotationLogEvent(&event, INT32_TYPE, ANNOTATION_ID_TRIGGER_STATE_RESET,
                                          true,
                                          /*parseBufferResult*/ false);
}

TEST(LogEventTest, TestUidAnnotationWithInt8MaxValues) {
    int32_t numElements = INT8_MAX;
    int32_t int32Array[numElements];

    for (int i = 0; i < numElements; i++) {
        int32Array[i] = i;
    }

    AStatsEvent* event = AStatsEvent_obtain();
    AStatsEvent_setAtomId(event, 100);
    AStatsEvent_writeInt32Array(event, int32Array, numElements);
    AStatsEvent_writeInt32(event, 10);
    AStatsEvent_writeInt32(event, 11);
    AStatsEvent_addBoolAnnotation(event, ANNOTATION_ID_IS_UID, true);
    AStatsEvent_build(event);

    size_t size;
    uint8_t* buf = AStatsEvent_getBuffer(event, &size);
    LogEvent logEvent(/*uid=*/1000, /*pid=*/1001);
    EXPECT_TRUE(logEvent.parseBuffer(buf, size));

    AStatsEvent_release(event);
}

TEST(LogEventTest, TestEmptyAttributionChainWithPrimaryFieldFirstUidAnnotation) {
    AStatsEvent* event = AStatsEvent_obtain();
    AStatsEvent_setAtomId(event, 100);

    uint32_t uids[] = {};
    const char* tags[] = {};

    AStatsEvent_writeInt32(event, 10);
    AStatsEvent_writeAttributionChain(event, uids, tags, 0);
    AStatsEvent_addBoolAnnotation(event, ANNOTATION_ID_PRIMARY_FIELD_FIRST_UID, true);

    AStatsEvent_build(event);

    size_t size;
    uint8_t* buf = AStatsEvent_getBuffer(event, &size);

    LogEvent logEvent(/*uid=*/1000, /*pid=*/1001);
    EXPECT_FALSE(logEvent.parseBuffer(buf, size));

    AStatsEvent_release(event);
}

}  // namespace statsd
}  // namespace os
}  // namespace android
#else
GTEST_LOG_(INFO) << "This test does nothing.\n";
#endif
