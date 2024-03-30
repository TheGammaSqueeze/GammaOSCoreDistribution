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

#include "icing/tokenization/tokenizer-factory.h"
#include "icing/tokenization/tokenizer.h"
#include "icing/transform/normalizer.h"

namespace icing {
namespace lib {

libtextclassifier3::StatusOr<std::unique_ptr<SuggestionProcessor>>
SuggestionProcessor::Create(Index* index,
                            const LanguageSegmenter* language_segmenter,
                            const Normalizer* normalizer) {
  ICING_RETURN_ERROR_IF_NULL(index);
  ICING_RETURN_ERROR_IF_NULL(language_segmenter);

  return std::unique_ptr<SuggestionProcessor>(
      new SuggestionProcessor(index, language_segmenter, normalizer));
}

libtextclassifier3::StatusOr<std::vector<TermMetadata>>
SuggestionProcessor::QuerySuggestions(
    const icing::lib::SuggestionSpecProto& suggestion_spec,
    const NamespaceChecker* namespace_checker) {
  // We use query tokenizer to tokenize the give prefix, and we only use the
  // last token to be the suggestion prefix.
  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<Tokenizer> tokenizer,
      tokenizer_factory::CreateIndexingTokenizer(
          StringIndexingConfig::TokenizerType::PLAIN, &language_segmenter_));
  ICING_ASSIGN_OR_RETURN(std::unique_ptr<Tokenizer::Iterator> iterator,
                         tokenizer->Tokenize(suggestion_spec.prefix()));

  // If there are previous tokens, they are prepended to the suggestion,
  // separated by spaces.
  std::string last_token;
  int token_start_pos;
  while (iterator->Advance()) {
    Token token = iterator->GetToken();
    last_token = token.text;
    token_start_pos = token.text.data() - suggestion_spec.prefix().c_str();
  }

  // If the position of the last token is not the end of the prefix, it means
  // there should be some operator tokens after it and are ignored by the
  // tokenizer.
  bool is_last_token = token_start_pos + last_token.length() >=
                       suggestion_spec.prefix().length();

  if (!is_last_token || last_token.empty()) {
    // We don't have a valid last token, return early.
    return std::vector<TermMetadata>();
  }

  std::string query_prefix =
      suggestion_spec.prefix().substr(0, token_start_pos);
  // Run suggestion based on given SuggestionSpec.
  // Normalize token text to lowercase since all tokens in the lexicon are
  // lowercase.
  ICING_ASSIGN_OR_RETURN(
      std::vector<TermMetadata> terms,
      index_.FindTermsByPrefix(
          normalizer_.NormalizeTerm(last_token),
          suggestion_spec.num_to_return(),
          suggestion_spec.scoring_spec().scoring_match_type(),
          namespace_checker));

  for (TermMetadata& term : terms) {
    term.content = query_prefix + term.content;
  }
  return terms;
}

SuggestionProcessor::SuggestionProcessor(
    Index* index, const LanguageSegmenter* language_segmenter,
    const Normalizer* normalizer)
    : index_(*index),
      language_segmenter_(*language_segmenter),
      normalizer_(*normalizer) {}

}  // namespace lib
}  // namespace icing
