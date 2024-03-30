// -*- Mode: C++ -*-
//

/// @file
///
/// This file declares the common functionality for tests in
/// CTF and DWARF readers, it declares abstractions for `act` test
/// stage.

#ifndef __TEST_READ_COMMON_H__
#define __TEST_READ_COMMON_H__

#include <string>
#include "abg-ir.h"
#include "abg-corpus.h"
#include "abg-workers.h"
#include "abg-writer.h"
#include "test-utils.h"
#include "abg-tools-utils.h"

using std::string;

using abigail::xml_writer::type_id_style_kind;
using abigail::ir::corpus_sptr;

namespace abigail
{
namespace tests
{
namespace read_common
{

/// This is an aggregate that specifies where a test shall get its
/// input from, and where it shall write its output to.
struct InOutSpec
{
  const char* in_elf_path;
  const char* in_suppr_spec_path;
  const char* in_public_headers_path;
  type_id_style_kind type_id_style;
  const char* in_abi_path;
  const char* out_abi_path;
};// end struct InOutSpec

/// The task that performs the tests.
struct test_task : public abigail::workers::task
{
  bool is_ok;
  InOutSpec spec;
  string error_message;
  string out_abi_base;
  string in_elf_base;
  string in_abi_base;

  string in_elf_path;
  string in_abi_path;
  string in_suppr_spec_path;
  string in_public_headers_path;
  string out_abi_path;


  /// A setter for `in_elf_path` field.
  /// The `in_elf_path` is the full path for input object
  /// in the tests container @ref
  /// abigail::tests::read_common::InOutSpec.
  void
  set_in_elf_path()
  {
    in_elf_path = in_elf_base + spec.in_elf_path;
  }

  /// A setter for `in_suppr_spec_path` field.
  /// The `in_suppr_spec_path` is the full path for suppression
  /// entry in the tests container @ref
  /// abigail::tests::read_common::InOutSpec.
  void
  set_in_suppr_spec_path()
  {
    if (spec.in_suppr_spec_path)
      in_suppr_spec_path = in_elf_base + spec.in_suppr_spec_path;
    else
      in_suppr_spec_path.clear();
  }

  /// A setter for `in_public_headers_path` field.
  /// The `in_public_headers_path` is the full path for headers
  /// entry in the tests container @ref
  /// abigail::tests::read_common::InOutSpec.
  void
  set_in_public_headers_path()
  {
    if (spec.in_public_headers_path)
      in_public_headers_path = spec.in_public_headers_path;
    if (!in_public_headers_path.empty())
      in_public_headers_path = in_elf_base + spec.in_public_headers_path;
  }

  /// A setter for `out_abi_path` field.
  /// The `out_abi_path` is the full path for output of abixml file.
  /// @return true if `out_abi_path` is a valid directory.
  bool
  set_out_abi_path()
  {
    out_abi_path = out_abi_base + spec.out_abi_path;
    if (!abigail::tools_utils::ensure_parent_dir_created(out_abi_path))
      {
          error_message =
            string("Could not create parent directory for ") + out_abi_path;
          return false;
      }
    return true;
  }

  /// A setter for `in_abi_path` field.
  /// The `in_abi_path` is the full path for the expected abixml file.
  void
  set_in_abi_path()
  {
    in_abi_path = in_abi_base + spec.in_abi_path;
  }

  test_task(const InOutSpec &s,
            string& a_out_abi_base,
            string& a_in_elf_base,
            string& a_in_abi_base);
  bool
  serialize_corpus(const string& out_abi_path,
                   corpus_sptr corp);
  bool
  run_abidw(const string& extargs = "");

  bool
  run_diff();

  virtual
  ~test_task()
  {}
}; // end struct test_task

typedef shared_ptr<test_task> test_task_sptr;

/// An abstraction for valid test options.
struct options
{
  // saves a wrong option string passed to test-harness.
  string        wrong_option;
  // parallel test execution.
  bool          parallel;

  options()
    : parallel(true)
  {}

  ~options()
  {
  }
}; // end struct options

void
display_usage(const string& prog_name, ostream& out);

bool
parse_command_line(int argc, char* argv[], options& opts);

/// A convenience typedef for a callback to create_new_test
/// instances.
typedef test_task* (*create_new_test)(const InOutSpec* s,
                                      string& a_out_abi_base,
                                      string& a_in_elf_base,
                                      string& a_in_abi_base);
bool
run_tests(const size_t num_test, const InOutSpec* specs,
          const options& opts, create_new_test new_test);

}//end namespace read_common
}//end namespace tests
}//end namespace abigail

#endif //__TEST_READ_COMMON_H__
