// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- Mode: C++ -*-
//
// Copyright (C) 2013-2021 Oracle, Inc.
//
// Author: Jose E. Marchesi

/// @file
///
/// This file contains declarations implementing the different status
/// in which a corpus read from an ELF file can result.  It is used by
/// the readers based on ELF files, such as DWARF and CTF.
///
/// More generally, this file contains declarations related to
/// facilities shared by the various readers that handle the ELF
/// format, e.g, the DWARF and CTF realder.

#ifndef __ABG_ELF_READER_COMMON_H__
#define __ABG_ELF_READER_COMMON_H__

#include <string>

namespace abigail
{

/// The namespace for an ELF based reader.
namespace elf_reader
{

/// The status of the @ref read_corpus_from_elf() call.
enum status
{
  /// The status is in an unknown state
  STATUS_UNKNOWN = 0,

  /// This status is for when the call went OK.
  STATUS_OK = 1,

  /// This status is for when the debug info could not be read.
  STATUS_DEBUG_INFO_NOT_FOUND = 1 << 1,

  /// This status is for when the alternate debug info could not be
  /// found.
  STATUS_ALT_DEBUG_INFO_NOT_FOUND = 1 << 2,

  /// This status is for when the symbols of the ELF binaries could
  /// not be read.
  STATUS_NO_SYMBOLS_FOUND = 1 << 3,
};

std::string
status_to_diagnostic_string(status s);

status
operator|(status, status);

status
operator&(status, status);

status&
operator|=(status&, status);

status&
operator&=(status&, status);

}// end namespace elf_reader

}// end namespace abigail

#endif //__ABG_ELF_READER_COMMON_H__
