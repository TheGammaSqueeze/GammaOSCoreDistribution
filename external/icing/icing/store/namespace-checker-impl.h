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

#ifndef ICING_STORE_NAMESPACE_CHECKER_IMPL_H_
#define ICING_STORE_NAMESPACE_CHECKER_IMPL_H_

#include "icing/store/document-id.h"
#include "icing/store/document-store.h"
#include "icing/store/namespace-checker.h"
#include "icing/store/namespace-id.h"

namespace icing {
namespace lib {

class NamespaceCheckerImpl : public NamespaceChecker {
 public:
  explicit NamespaceCheckerImpl(
      const DocumentStore* document_store,
      std::unordered_set<NamespaceId> target_namespace_ids)
      : document_store_(*document_store),
        target_namespace_ids_(std::move(target_namespace_ids)) {}

  bool BelongsToTargetNamespaces(DocumentId document_id) const override {
    if (target_namespace_ids_.empty()) {
      return true;
    }
    auto document_filter_data_or_ =
        document_store_.GetDocumentFilterData(document_id);
    return document_filter_data_or_.ok() &&
        target_namespace_ids_.count(
            document_filter_data_or_.ValueOrDie().namespace_id())> 0;
  }
  const DocumentStore& document_store_;
  std::unordered_set<NamespaceId> target_namespace_ids_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_STORE_NAMESPACE_CHECKER_IMPL_H_