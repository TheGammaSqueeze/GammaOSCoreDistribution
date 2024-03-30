// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- Mode: C++ -*-
//
// Copyright (C) 2021 Oracle, Inc.
//
// Author: Jose E. Marchesi

/// @file
///
/// This file contains the declarations of the entry points to
/// de-serialize an instance of @ref abigail::corpus from a file in
/// elf format, containing CTF information.

#ifndef __ABG_CTF_READER_H__
#define __ABG_CTF_READER_H__

#include <ostream>
#include "abg-corpus.h"
#include "abg-suppression.h"
#include "abg-elf-reader-common.h"

namespace abigail
{
namespace ctf_reader
{

class read_context;
typedef shared_ptr<read_context> read_context_sptr;

read_context_sptr
create_read_context(const std::string& elf_path,
                    ir::environment *env);
corpus_sptr
read_corpus(read_context *ctxt, elf_reader::status& status);
corpus_sptr
read_corpus(const read_context_sptr &ctxt, elf_reader::status &status);
} // end namespace ctf_reader
} // end namespace abigail

#endif // ! __ABG_CTF_READER_H__
