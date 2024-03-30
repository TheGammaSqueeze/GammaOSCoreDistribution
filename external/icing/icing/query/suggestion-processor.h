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

#ifndef ICING_QUERY_SUGGESTION_PROCESSOR_H_
#define ICING_QUERY_SUGGESTION_PROCESSOR_H_

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/index/index.h"
#include "icing/proto/search.pb.h"
#include "icing/tokenization/language-segmenter.h"
#include "icing/transform/normalizer.h"

namespace icing {
namespace lib {

// Processes SuggestionSpecProtos and retrieves the specified TermMedaData that
// satisfies the prefix and its restrictions. This also performs ranking, and
// returns TermMetaData ordered by their hit count.
class SuggestionProcessor {
 public:
  // Factory function to create a SuggestionProcessor which does not take
  // ownership of any input components, and all pointers must refer to valid
  // objects that outlive the created SuggestionProcessor instance.
  //
  // Returns:
  //   An SuggestionProcessor on success
  //   FAILED_PRECONDITION if any of the pointers is null.
  static libtextclassifier3::StatusOr<std::unique_ptr<SuggestionProcessor>>
  Create(Index* index, const LanguageSegmenter* language_segmenter,
         const Normalizer* normalizer);

  // Query suggestions based on the given SuggestionSpecProto.
  //
  // Returns:
  //   On success,
  //     - One vector that represents the entire TermMetadata
  //   INTERNAL_ERROR on all other errors
  libtextclassifier3::StatusOr<std::vector<TermMetadata>> QuerySuggestions(
      const SuggestionSpecProto& suggestion_spec,
      const NamespaceChecker* namespace_checker);

 private:
  explicit SuggestionProcessor(Index* index,
                               const LanguageSegmenter* language_segmenter,
                               const Normalizer* normalizer);

  // Not const because we could modify/sort the TermMetaData buffer in the lite
  // index.
  Index& index_;
  const LanguageSegmenter& language_segmenter_;
  const Normalizer& normalizer_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_QUERY_SUGGESTION_PROCESSOR_H_
