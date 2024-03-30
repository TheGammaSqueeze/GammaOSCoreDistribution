/*
 * Copyright (C) 2019, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef ANDROID_STATS_LOG_API_GEN_UTILS_H
#define ANDROID_STATS_LOG_API_GEN_UTILS_H

#include <stdio.h>
#include <string.h>

#include <map>
#include <set>
#include <vector>

#include "Collation.h"

namespace android {
namespace stats_log_api_gen {

const char DEFAULT_CPP_NAMESPACE[] = "android,util";
const char DEFAULT_CPP_HEADER_IMPORT[] = "statslog.h";

const int API_LEVEL_CURRENT = 10000;
const int API_Q = 29;
const int API_R = 30;
const int API_T = 33;

const int JAVA_MODULE_REQUIRES_FLOAT = 0x01;
const int JAVA_MODULE_REQUIRES_ATTRIBUTION = 0x02;

void build_non_chained_decl_map(const Atoms& atoms,
                                std::map<int, AtomDeclSet::const_iterator>* decl_map);

const map<AnnotationId, string>& get_annotation_id_constants();

string make_constant_name(const string& str);

const char* cpp_type_name(java_type_t type);

const char* java_type_name(java_type_t type);

bool is_repeated_field(java_type_t type);

// Common Native helpers
void write_namespace(FILE* out, const string& cppNamespaces);

void write_closing_namespace(FILE* out, const string& cppNamespaces);

void write_native_atom_constants(FILE* out, const Atoms& atoms, const AtomDecl& attributionDecl);

// Common Java helpers.
void write_java_atom_codes(FILE* out, const Atoms& atoms);

void write_java_enum_values(FILE* out, const Atoms& atoms);

void write_java_usage(FILE* out, const string& method_name, const string& atom_code_name,
                      const AtomDecl& atom);

int write_java_non_chained_methods(FILE* out, const SignatureInfoMap& signatureInfoMap);

int write_java_work_source_methods(FILE* out, const SignatureInfoMap& signatureInfoMap);

}  // namespace stats_log_api_gen
}  // namespace android

#endif  // ANDROID_STATS_LOG_API_GEN_UTILS_H
