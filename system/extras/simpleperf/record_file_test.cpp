/*
 * Copyright (C) 2015 The Android Open Source Project
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

#include <gtest/gtest.h>

#include <string.h>

#include <memory>
#include <vector>

#include <android-base/file.h>

#include "environment.h"
#include "event_attr.h"
#include "event_type.h"
#include "record.h"
#include "record_file.h"
#include "utils.h"

#include "record_equal_test.h"

using namespace simpleperf;
using namespace simpleperf::PerfFileFormat;

class RecordFileTest : public ::testing::Test {
 protected:
  void SetUp() override { close(tmpfile_.release()); }

  void AddEventType(const std::string& event_type_str) {
    std::unique_ptr<EventTypeAndModifier> event_type_modifier = ParseEventType(event_type_str);
    ASSERT_TRUE(event_type_modifier != nullptr);
    perf_event_attr attr = CreateDefaultPerfEventAttr(event_type_modifier->event_type);
    attr.sample_id_all = 1;
    attrs_.push_back(std::unique_ptr<perf_event_attr>(new perf_event_attr(attr)));
    EventAttrWithId attr_id;
    attr_id.attr = attrs_.back().get();
    attr_id.ids.push_back(attrs_.size());  // Fake id.
    attr_ids_.push_back(attr_id);
  }

  TemporaryFile tmpfile_;
  std::vector<std::unique_ptr<perf_event_attr>> attrs_;
  std::vector<EventAttrWithId> attr_ids_;
};

TEST_F(RecordFileTest, smoke) {
  // Write to a record file.
  std::unique_ptr<RecordFileWriter> writer = RecordFileWriter::CreateInstance(tmpfile_.path);
  ASSERT_TRUE(writer != nullptr);

  // Write attr section.
  AddEventType("cpu-cycles");
  ASSERT_TRUE(writer->WriteAttrSection(attr_ids_));

  // Write data section.
  MmapRecord mmap_record(*(attr_ids_[0].attr), true, 1, 1, 0x1000, 0x2000, 0x3000,
                         "mmap_record_example", attr_ids_[0].ids[0]);
  ASSERT_TRUE(writer->WriteRecord(mmap_record));

  // Write feature section.
  ASSERT_TRUE(writer->BeginWriteFeatures(1));
  char p[BuildId::Size()];
  for (size_t i = 0; i < BuildId::Size(); ++i) {
    p[i] = i;
  }
  BuildId build_id(p);
  std::vector<BuildIdRecord> build_id_records;
  build_id_records.push_back(BuildIdRecord(false, getpid(), build_id, "init"));
  ASSERT_TRUE(writer->WriteBuildIdFeature(build_id_records));
  ASSERT_TRUE(writer->EndWriteFeatures());
  ASSERT_TRUE(writer->Close());

  // Read from a record file.
  std::unique_ptr<RecordFileReader> reader = RecordFileReader::CreateInstance(tmpfile_.path);
  ASSERT_TRUE(reader != nullptr);
  std::vector<EventAttrWithId> attrs = reader->AttrSection();
  ASSERT_EQ(1u, attrs.size());
  ASSERT_EQ(0, memcmp(attrs[0].attr, attr_ids_[0].attr, sizeof(perf_event_attr)));
  ASSERT_EQ(attrs[0].ids, attr_ids_[0].ids);

  // Read and check data section.
  std::vector<std::unique_ptr<Record>> records = reader->DataSection();
  ASSERT_EQ(1u, records.size());
  CheckRecordEqual(mmap_record, *records[0]);

  // Read and check feature section.
  std::vector<BuildIdRecord> read_build_id_records = reader->ReadBuildIdFeature();
  ASSERT_EQ(1u, read_build_id_records.size());
  CheckRecordEqual(read_build_id_records[0], build_id_records[0]);

  ASSERT_TRUE(reader->Close());
}

TEST_F(RecordFileTest, record_more_than_one_attr) {
  // Write to a record file.
  std::unique_ptr<RecordFileWriter> writer = RecordFileWriter::CreateInstance(tmpfile_.path);
  ASSERT_TRUE(writer != nullptr);

  // Write attr section.
  AddEventType("cpu-cycles");
  AddEventType("cpu-clock");
  AddEventType("task-clock");
  ASSERT_TRUE(writer->WriteAttrSection(attr_ids_));

  ASSERT_TRUE(writer->Close());

  // Read from a record file.
  std::unique_ptr<RecordFileReader> reader = RecordFileReader::CreateInstance(tmpfile_.path);
  ASSERT_TRUE(reader != nullptr);
  std::vector<EventAttrWithId> attrs = reader->AttrSection();
  ASSERT_EQ(3u, attrs.size());
  for (size_t i = 0; i < attrs.size(); ++i) {
    ASSERT_EQ(0, memcmp(attrs[i].attr, attr_ids_[i].attr, sizeof(perf_event_attr)));
    ASSERT_EQ(attrs[i].ids, attr_ids_[i].ids);
  }
}

TEST_F(RecordFileTest, write_meta_info_feature_section) {
  // Write to a record file.
  std::unique_ptr<RecordFileWriter> writer = RecordFileWriter::CreateInstance(tmpfile_.path);
  ASSERT_TRUE(writer != nullptr);
  AddEventType("cpu-cycles");
  ASSERT_TRUE(writer->WriteAttrSection(attr_ids_));

  // Write meta_info feature section.
  ASSERT_TRUE(writer->BeginWriteFeatures(1));
  std::unordered_map<std::string, std::string> info_map;
  for (int i = 0; i < 100; ++i) {
    std::string s = std::to_string(i);
    info_map[s] = s + s;
  }
  ASSERT_TRUE(writer->WriteMetaInfoFeature(info_map));
  ASSERT_TRUE(writer->EndWriteFeatures());
  ASSERT_TRUE(writer->Close());

  // Read from a record file.
  std::unique_ptr<RecordFileReader> reader = RecordFileReader::CreateInstance(tmpfile_.path);
  ASSERT_TRUE(reader != nullptr);
  ASSERT_EQ(reader->GetMetaInfoFeature(), info_map);
}

TEST_F(RecordFileTest, write_debug_unwind_feature_section) {
  // Write to a record file.
  std::unique_ptr<RecordFileWriter> writer = RecordFileWriter::CreateInstance(tmpfile_.path);
  ASSERT_TRUE(writer != nullptr);
  AddEventType("cpu-cycles");
  ASSERT_TRUE(writer->WriteAttrSection(attr_ids_));

  // Write debug_unwind feature section.
  ASSERT_TRUE(writer->BeginWriteFeatures(1));
  DebugUnwindFeature debug_unwind(2);
  debug_unwind[0].path = "file1";
  debug_unwind[0].size = 1000;
  debug_unwind[1].path = "file2";
  debug_unwind[1].size = 2000;
  ASSERT_TRUE(writer->WriteDebugUnwindFeature(debug_unwind));
  ASSERT_TRUE(writer->EndWriteFeatures());
  ASSERT_TRUE(writer->Close());

  // Read from a record file.
  std::unique_ptr<RecordFileReader> reader = RecordFileReader::CreateInstance(tmpfile_.path);
  ASSERT_TRUE(reader != nullptr);
  std::optional<DebugUnwindFeature> opt_debug_unwind = reader->ReadDebugUnwindFeature();
  ASSERT_TRUE(opt_debug_unwind.has_value());
  ASSERT_EQ(opt_debug_unwind.value().size(), debug_unwind.size());
  for (size_t i = 0; i < debug_unwind.size(); i++) {
    ASSERT_EQ(opt_debug_unwind.value()[i].path, debug_unwind[i].path);
    ASSERT_EQ(opt_debug_unwind.value()[i].size, debug_unwind[i].size);
  }
}

TEST_F(RecordFileTest, write_file2_feature_section) {
  // Write to a record file.
  std::unique_ptr<RecordFileWriter> writer = RecordFileWriter::CreateInstance(tmpfile_.path);
  ASSERT_TRUE(writer != nullptr);
  AddEventType("cpu-cycles");
  ASSERT_TRUE(writer->WriteAttrSection(attr_ids_));

  // Write file2 feature section.
  ASSERT_TRUE(writer->BeginWriteFeatures(1));
  std::vector<FileFeature> files(3);
  files[0].path = "fake_dex_file";
  files[0].type = DSO_DEX_FILE;
  files[0].min_vaddr = 0x1000;
  files[0].symbols.emplace_back("dex_symbol", 0x1001, 0x1002);
  files[0].dex_file_offsets.assign(0x1003, 0x1004);
  files[1].path = "fake_elf_file";
  files[1].type = DSO_ELF_FILE;
  files[1].min_vaddr = 0x2000;
  Symbol symbol("elf_symbol", 0x2001, 0x2002);
  files[1].symbol_ptrs.emplace_back(&symbol);
  files[1].file_offset_of_min_vaddr = 0x2003;
  files[2].path = "fake_kernel_module";
  files[2].type = DSO_KERNEL_MODULE;
  files[2].min_vaddr = 0x3000;
  files[2].symbols.emplace_back("kernel_module_symbol", 0x3001, 0x3002);
  files[2].file_offset_of_min_vaddr = 0x3003;

  for (const auto& file : files) {
    ASSERT_TRUE(writer->WriteFileFeature(file));
  }
  ASSERT_TRUE(writer->EndWriteFeatures());
  ASSERT_TRUE(writer->Close());

  // Read from a record file.
  std::unique_ptr<RecordFileReader> reader = RecordFileReader::CreateInstance(tmpfile_.path);
  ASSERT_TRUE(reader != nullptr);
  size_t read_pos = 0;
  FileFeature file;

  auto check_symbol = [](const Symbol& sym1, const Symbol& sym2) {
    return sym1.addr == sym2.addr && sym1.len == sym2.len && strcmp(sym1.Name(), sym2.Name()) == 0;
  };

  size_t file_id = 0;
  while (reader->ReadFileFeature(read_pos, &file)) {
    ASSERT_LT(file_id, files.size());
    const FileFeature& expected_file = files[file_id++];
    ASSERT_EQ(file.path, expected_file.path);
    ASSERT_EQ(file.type, expected_file.type);
    ASSERT_EQ(file.min_vaddr, expected_file.min_vaddr);
    if (!expected_file.symbols.empty()) {
      ASSERT_EQ(file.symbols.size(), expected_file.symbols.size());
      for (size_t i = 0; i < file.symbols.size(); i++) {
        ASSERT_TRUE(check_symbol(file.symbols[i], expected_file.symbols[i]));
      }
    } else {
      ASSERT_EQ(file.symbols.size(), expected_file.symbol_ptrs.size());
      for (size_t i = 0; i < file.symbols.size(); i++) {
        ASSERT_TRUE(check_symbol(file.symbols[i], *expected_file.symbol_ptrs[i]));
      }
    }
    if (file.type == DSO_DEX_FILE) {
      ASSERT_EQ(file.dex_file_offsets, expected_file.dex_file_offsets);
    } else if (file.type == DSO_ELF_FILE) {
      ASSERT_TRUE(file.dex_file_offsets.empty());
      ASSERT_EQ(file.file_offset_of_min_vaddr, expected_file.file_offset_of_min_vaddr);
    } else if (file.type == DSO_KERNEL_MODULE) {
      ASSERT_TRUE(file.dex_file_offsets.empty());
      ASSERT_EQ(file.file_offset_of_min_vaddr, expected_file.file_offset_of_min_vaddr);
    }
  }
  ASSERT_EQ(file_id, files.size());
}