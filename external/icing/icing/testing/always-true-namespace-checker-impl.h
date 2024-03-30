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

#ifndef ICING_TESTING_ALWAYS_TRUE_NAMESPACE_CHECKER_IMPL_H_
#define ICING_TESTING_ALWAYS_TRUE_NAMESPACE_CHECKER_IMPL_H_

#include "icing/store/document-id.h"
#include "icing/store/namespace-checker.h"

namespace icing {
namespace lib {

class AlwaysTrueNamespaceCheckerImpl : public NamespaceChecker {
 public:
  bool BelongsToTargetNamespaces(DocumentId document_id) const override {
    return true;
  }
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_TESTING_ALWAYS_TRUE_NAMESPACE_CHECKER_IMPL_H_