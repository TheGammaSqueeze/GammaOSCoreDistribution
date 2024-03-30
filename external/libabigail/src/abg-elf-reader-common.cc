// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- Mode: C++ -*-
//
// Copyright (C) 2013-2021 Oracle, Inc.
//
// Author: Jose E. Marchesi

/// @file
///
/// This file contains the implementation of facilities to deal with
/// status codes related to ELF based readers.
///
/// More generally, this file contains definitions related to
/// facilities shared by the various readers that handle the ELF
/// format, e.g, the DWARF and CTF realder.
#include "config.h"

#include "abg-internal.h"

// <headers defining libabigail's API go under here>
ABG_BEGIN_EXPORT_DECLARATIONS

#include "abg-elf-reader-common.h"

ABG_END_EXPORT_DECLARATIONS
// </headers defining libabigail's API>

namespace abigail
{

namespace elf_reader
{

status
operator|(status l, status r)
{
  return static_cast<status>(static_cast<unsigned>(l)
			     | static_cast<unsigned>(r));
}

status
operator&(status l, status r)
{
  return static_cast<status>(static_cast<unsigned>(l)
			     & static_cast<unsigned>(r));
}

status&
operator|=(status& l, status r)
{
  l = l | r;
  return l;
}

status&
operator&=(status& l, status r)
{
  l = l & r;
  return l;
}

/// Return a diagnostic status with english sentences to describe the
/// problems encoded in a given abigail::elf_reader::status, if
/// there is an error.
///
/// @param status the status to diagnose
///
/// @return a string containing sentences that describe the possible
/// errors encoded in @p s.  If there is no error to encode, then the
/// empty string is returned.
std::string
status_to_diagnostic_string(status s)
{
  std::string str;

  if (s & STATUS_DEBUG_INFO_NOT_FOUND)
    str += "could not find debug info\n";

  if (s & STATUS_ALT_DEBUG_INFO_NOT_FOUND)
    str += "could not find alternate debug info\n";

  if (s & STATUS_NO_SYMBOLS_FOUND)
    str += "could not load ELF symbols\n";

  return str;
}

}// end namespace elf_reader

}// end namespace abigail
