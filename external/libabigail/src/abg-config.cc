// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- Mode: C++ -*-
//
// Copyright (C) 2013-2020 Red Hat, Inc.

/// @file

#if defined(USE_ANDROID_BUILD_NUMBER)
#include <build/version.h>
#endif

#include "abg-internal.h"
// <headers defining libabigail's API go under here>
ABG_BEGIN_EXPORT_DECLARATIONS

#include "abg-config.h"
#include "abg-version.h"

ABG_END_EXPORT_DECLARATIONS
// </headers defining libabigail's API>

namespace abigail
{
config::config()
  : m_format_minor(ABIGAIL_ABIXML_VERSION_MINOR),
    m_format_major(ABIGAIL_ABIXML_VERSION_MAJOR),
    m_xml_element_indent(2),
    m_tu_instr_suffix(".bi"),
    m_tu_instr_archive_suffix(".abi")
{
}

const std::string&
config::get_format_minor_version_number() const
{return m_format_minor;}

void
config::set_format_minor_version_number(const std::string& v)
{m_format_minor = v;}

const std::string&
config::get_format_major_version_number() const
{return m_format_major;}

void
config::set_format_major_version_number(const std::string& v)
{m_format_major= v;}

unsigned
config::get_xml_element_indent() const
{ return m_xml_element_indent; }

void
config::set_xml_element_indent(unsigned indent)
{m_xml_element_indent = indent;}

const std::string&
config::get_tu_instr_suffix() const
{return m_tu_instr_suffix;}

void
config::set_tu_instr_suffix(const std::string& s)
{m_tu_instr_suffix = s;}

const std::string&
config::get_tu_instr_archive_suffix() const
{return m_tu_instr_archive_suffix;}

void
config::set_tu_instr_archive_suffix(const std::string& s)
{m_tu_instr_archive_suffix = s; }

extern "C"
{
/// Return the relevant version numbers of the library.
///
/// @param maj the major version number of the library.
///
/// @param min the minor version number of the library.
///
/// @param rev the revision version number of the library.
///
/// @param suf the version suffix of the library.
void
abigail_get_library_version(std::string& major,
			    std::string& minor,
			    std::string& revision,
			    std::string& suffix)
{
  major = ABIGAIL_VERSION_MAJOR;
  minor = ABIGAIL_VERSION_MINOR;
  revision = ABIGAIL_VERSION_REVISION;
#if defined(USE_ANDROID_BUILD_NUMBER)
  // Android edit: there is no compile time constant for build number.
  suffix = android::build::GetBuildNumber();
#else
  suffix = ABIGAIL_VERSION_SUFFIX;
#endif
}

/// Return the version numbers for the ABIXML format.
///
/// @param maj the major version number of the ABIXML format.
///
/// @param min the minor version number of the ABIXML format.
void
abigail_get_abixml_version(std::string& major,
			   std::string& minor)
{
  major = ABIGAIL_ABIXML_VERSION_MAJOR;
  minor = ABIGAIL_ABIXML_VERSION_MINOR;
}

}
}//end namespace abigail
