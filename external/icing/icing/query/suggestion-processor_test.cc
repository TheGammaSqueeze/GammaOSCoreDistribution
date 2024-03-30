// Copyright (C) 2021 Google LLC
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

#include "icing/query/suggestion-processor.h"

#include "gmock/gmock.h"
#include "icing/store/document-store.h"
#include "icing/testing/always-true-namespace-checker-impl.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/fake-clock.h"
#include "icing/testing/icu-data-file-helper.h"
#include "icing/testing/jni-test-helpers.h"
#include "icing/testing/test-data.h"
#include "icing/testing/tmp-directory.h"
#include "icing/tokenization/language-segmenter-factory.h"
#include "icing/transform/normalizer-factory.h"
#include "unicode/uloc.h"

namespace icing {
namespace lib {

namespace {

using ::testing::IsEmpty;
using ::testing::Test;

class SuggestionProcessorTest : public Test {
 protected:
  SuggestionProcessorTest()
      : test_dir_(GetTestTempDir() + "/icing"),
        store_dir_(test_dir_ + "/store"),
        index_dir_(test_dir_ + "/index") {}

  void SetUp() override {
    filesystem_.DeleteDirectoryRecursively(test_dir_.c_str());
    filesystem_.CreateDirectoryRecursively(index_dir_.c_str());
    filesystem_.CreateDirectoryRecursively(store_dir_.c_str());

    if (!IsCfStringTokenization() && !IsReverseJniTokenization()) {
      // If we've specified using the reverse-JNI method for segmentation (i.e.
      // not ICU), then we won't have the ICU data file included to set up.
      // Technically, we could choose to use reverse-JNI for segmentation AND
      // include an ICU data file, but that seems unlikely and our current BUILD
      // setup doesn't do this.
      ICING_ASSERT_OK(
          // File generated via icu_data_file rule in //icing/BUILD.
          icu_data_file_helper::SetUpICUDataFile(
              GetTestFilePath("icing/icu.dat")));
    }

    Index::Options options(index_dir_,
                           /*index_merge_size=*/1024 * 1024);
    ICING_ASSERT_OK_AND_ASSIGN(
        index_, Index::Create(options, &filesystem_, &icing_filesystem_));

    language_segmenter_factory::SegmenterOptions segmenter_options(
        ULOC_US, jni_cache_.get());
    ICING_ASSERT_OK_AND_ASSIGN(
        language_segmenter_,
        language_segmenter_factory::Create(segmenter_options));

    ICING_ASSERT_OK_AND_ASSIGN(normalizer_, normalizer_factory::Create(
                                                /*max_term_byte_size=*/1000));

    ICING_ASSERT_OK_AND_ASSIGN(
        schema_store_,
        SchemaStore::Create(&filesystem_, test_dir_, &fake_clock_));

    ICING_ASSERT_OK_AND_ASSIGN(
        DocumentStore::CreateResult create_result,
        DocumentStore::Create(&filesystem_, store_dir_, &fake_clock_,
                              schema_store_.get()));
  }

  libtextclassifier3::Status AddTokenToIndex(
      DocumentId document_id, SectionId section_id,
      TermMatchType::Code term_match_type, const std::string& token) {
    Index::Editor editor = index_->Edit(document_id, section_id,
                                        term_match_type, /*namespace_id=*/0);
    auto status = editor.BufferTerm(token.c_str());
    return status.ok() ? editor.IndexAllBufferedTerms() : status;
  }

  void TearDown() override {
    filesystem_.DeleteDirectoryRecursively(test_dir_.c_str());
  }

  Filesystem filesystem_;
  const std::string test_dir_;
  const std::string store_dir_;
  std::unique_ptr<Index> index_;
  std::unique_ptr<LanguageSegmenter> language_segmenter_;
  std::unique_ptr<Normalizer> normalizer_;
  std::unique_ptr<SchemaStore> schema_store_;
  std::unique_ptr<const JniCache> jni_cache_ = GetTestJniCache();
  FakeClock fake_clock_;

 private:
  IcingFilesystem icing_filesystem_;
  const std::string index_dir_;
};

constexpr DocumentId kDocumentId0 = 0;
constexpr SectionId kSectionId2 = 2;

TEST_F(SuggestionProcessorTest, PrependedPrefixTokenTest) {
  ASSERT_THAT(AddTokenToIndex(kDocumentId0, kSectionId2,
                              TermMatchType::EXACT_ONLY, "foo"),
              IsOk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SuggestionProcessor> suggestion_processor,
      SuggestionProcessor::Create(index_.get(), language_segmenter_.get(),
                                  normalizer_.get()));

  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix(
      "prefix token should be prepended to the suggestion f");
  suggestion_spec.set_num_to_return(10);

  AlwaysTrueNamespaceCheckerImpl impl;
  ICING_ASSERT_OK_AND_ASSIGN(
      std::vector<TermMetadata> terms,
      suggestion_processor->QuerySuggestions(suggestion_spec, &impl));
  EXPECT_THAT(terms.at(0).content,
              "prefix token should be prepended to the suggestion foo");
}

TEST_F(SuggestionProcessorTest, NonExistentPrefixTest) {
  ASSERT_THAT(AddTokenToIndex(kDocumentId0, kSectionId2,
                              TermMatchType::EXACT_ONLY, "foo"),
              IsOk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SuggestionProcessor> suggestion_processor,
      SuggestionProcessor::Create(index_.get(), language_segmenter_.get(),
                                  normalizer_.get()));

  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("nonExistTerm");
  suggestion_spec.set_num_to_return(10);

  AlwaysTrueNamespaceCheckerImpl impl;
  ICING_ASSERT_OK_AND_ASSIGN(
      std::vector<TermMetadata> terms,
      suggestion_processor->QuerySuggestions(suggestion_spec, &impl));

  EXPECT_THAT(terms, IsEmpty());
}

TEST_F(SuggestionProcessorTest, PrefixTrailingSpaceTest) {
  ASSERT_THAT(AddTokenToIndex(kDocumentId0, kSectionId2,
                              TermMatchType::EXACT_ONLY, "foo"),
              IsOk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SuggestionProcessor> suggestion_processor,
      SuggestionProcessor::Create(index_.get(), language_segmenter_.get(),
                                  normalizer_.get()));

  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("f    ");
  suggestion_spec.set_num_to_return(10);

  AlwaysTrueNamespaceCheckerImpl impl;
  ICING_ASSERT_OK_AND_ASSIGN(
      std::vector<TermMetadata> terms,
      suggestion_processor->QuerySuggestions(suggestion_spec, &impl));

  EXPECT_THAT(terms, IsEmpty());
}

TEST_F(SuggestionProcessorTest, NormalizePrefixTest) {
  ASSERT_THAT(AddTokenToIndex(kDocumentId0, kSectionId2,
                              TermMatchType::EXACT_ONLY, "foo"),
              IsOk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SuggestionProcessor> suggestion_processor,
      SuggestionProcessor::Create(index_.get(), language_segmenter_.get(),
                                  normalizer_.get()));

  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("F");
  suggestion_spec.set_num_to_return(10);

  AlwaysTrueNamespaceCheckerImpl impl;
  ICING_ASSERT_OK_AND_ASSIGN(
      std::vector<TermMetadata> terms,
      suggestion_processor->QuerySuggestions(suggestion_spec, &impl));
  EXPECT_THAT(terms.at(0).content, "foo");

  suggestion_spec.set_prefix("fO");
  ICING_ASSERT_OK_AND_ASSIGN(
      terms, suggestion_processor->QuerySuggestions(suggestion_spec, &impl));
  EXPECT_THAT(terms.at(0).content, "foo");

  suggestion_spec.set_prefix("Fo");
  ICING_ASSERT_OK_AND_ASSIGN(
      terms, suggestion_processor->QuerySuggestions(suggestion_spec, &impl));
  EXPECT_THAT(terms.at(0).content, "foo");

  suggestion_spec.set_prefix("FO");
  ICING_ASSERT_OK_AND_ASSIGN(
      terms, suggestion_processor->QuerySuggestions(suggestion_spec, &impl));
  EXPECT_THAT(terms.at(0).content, "foo");
}

TEST_F(SuggestionProcessorTest, OrOperatorPrefixTest) {
  ASSERT_THAT(AddTokenToIndex(kDocumentId0, kSectionId2,
                              TermMatchType::EXACT_ONLY, "foo"),
              IsOk());
  ASSERT_THAT(AddTokenToIndex(kDocumentId0, kSectionId2,
                              TermMatchType::EXACT_ONLY, "original"),
              IsOk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SuggestionProcessor> suggestion_processor,
      SuggestionProcessor::Create(index_.get(), language_segmenter_.get(),
                                  normalizer_.get()));

  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("f OR");
  suggestion_spec.set_num_to_return(10);

  AlwaysTrueNamespaceCheckerImpl impl;
  ICING_ASSERT_OK_AND_ASSIGN(
      std::vector<TermMetadata> terms,
      suggestion_processor->QuerySuggestions(suggestion_spec, &impl));

  // Last Operator token will be used to query suggestion
  EXPECT_THAT(terms.at(0).content, "f original");
}

TEST_F(SuggestionProcessorTest, ParenthesesOperatorPrefixTest) {
  ASSERT_THAT(AddTokenToIndex(kDocumentId0, kSectionId2,
                              TermMatchType::EXACT_ONLY, "foo"),
              IsOk());
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SuggestionProcessor> suggestion_processor,
      SuggestionProcessor::Create(index_.get(), language_segmenter_.get(),
                                  normalizer_.get()));

  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("{f}");
  suggestion_spec.set_num_to_return(10);

  AlwaysTrueNamespaceCheckerImpl impl;
  ICING_ASSERT_OK_AND_ASSIGN(
      std::vector<TermMetadata> terms,
      suggestion_processor->QuerySuggestions(suggestion_spec, &impl));
  EXPECT_THAT(terms, IsEmpty());

  suggestion_spec.set_prefix("[f]");
  ICING_ASSERT_OK_AND_ASSIGN(
      terms, suggestion_processor->QuerySuggestions(suggestion_spec, &impl));
  EXPECT_THAT(terms, IsEmpty());

  suggestion_spec.set_prefix("(f)");
  ICING_ASSERT_OK_AND_ASSIGN(
      terms, suggestion_processor->QuerySuggestions(suggestion_spec, &impl));
  EXPECT_THAT(terms, IsEmpty());
}

TEST_F(SuggestionProcessorTest, OtherSpecialPrefixTest) {
  ASSERT_THAT(AddTokenToIndex(kDocumentId0, kSectionId2,
                              TermMatchType::EXACT_ONLY, "foo"),
              IsOk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SuggestionProcessor> suggestion_processor,
      SuggestionProcessor::Create(index_.get(), language_segmenter_.get(),
                                  normalizer_.get()));

  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("f:");
  suggestion_spec.set_num_to_return(10);

  AlwaysTrueNamespaceCheckerImpl impl;
  ICING_ASSERT_OK_AND_ASSIGN(
      std::vector<TermMetadata> terms,
      suggestion_processor->QuerySuggestions(suggestion_spec, &impl));
  EXPECT_THAT(terms, IsEmpty());

  suggestion_spec.set_prefix("f-");
  ICING_ASSERT_OK_AND_ASSIGN(
      terms, suggestion_processor->QuerySuggestions(suggestion_spec, &impl));
  EXPECT_THAT(terms, IsEmpty());
}

TEST_F(SuggestionProcessorTest, InvalidPrefixTest) {
  ASSERT_THAT(AddTokenToIndex(kDocumentId0, kSectionId2,
                              TermMatchType::EXACT_ONLY, "original"),
              IsOk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SuggestionProcessor> suggestion_processor,
      SuggestionProcessor::Create(index_.get(), language_segmenter_.get(),
                                  normalizer_.get()));

  SuggestionSpecProto suggestion_spec;
  suggestion_spec.set_prefix("OR OR - :");
  suggestion_spec.set_num_to_return(10);

  AlwaysTrueNamespaceCheckerImpl impl;
  ICING_ASSERT_OK_AND_ASSIGN(
      std::vector<TermMetadata> terms,
      suggestion_processor->QuerySuggestions(suggestion_spec, &impl));
  EXPECT_THAT(terms, IsEmpty());
}

}  // namespace

}  // namespace lib
}  // namespace icing
