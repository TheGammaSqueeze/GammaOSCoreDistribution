// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- Mode: C++ -*-
//
// Copyright (C) 2013-2020 Red Hat, Inc.
//
// Author: Dodji Seketeli

/// @file read ELF binaries containing DWARF, save them in XML corpus
/// files and diff the corpus files against reference XML corpus
/// files.

#include <cstdlib>
#include <fstream>
#include <iostream>
#include <memory>
#include <string>
#include <vector>
#include "test-read-common.h"
#include "abg-dwarf-reader.h"

using std::vector;
using std::string;
using std::cerr;

using abigail::tests::read_common::InOutSpec;
using abigail::tests::read_common::test_task;
using abigail::tests::read_common::display_usage;
using abigail::tests::read_common::options;

using abigail::dwarf_reader::read_corpus_from_elf;
using abigail::dwarf_reader::read_context;
using abigail::dwarf_reader::read_context_sptr;
using abigail::dwarf_reader::create_read_context;
using abigail::xml_writer::SEQUENCE_TYPE_ID_STYLE;
using abigail::xml_writer::HASH_TYPE_ID_STYLE;
using abigail::tools_utils::emit_prefix;

static InOutSpec in_out_specs[] =
{
  {
    "data/test-read-dwarf/test0",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/test0.abi",
    "output/test-read-dwarf/test0.abi"
  },
  {
    "data/test-read-dwarf/test0",
    "",
    "",
    HASH_TYPE_ID_STYLE,
    "data/test-read-dwarf/test0.hash.abi",
    "output/test-read-dwarf/test0.hash.abi"
  },
  {
    "data/test-read-dwarf/test1",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/test1.abi",
    "output/test-read-dwarf/test1.abi"
  },
  {
    "data/test-read-dwarf/test1",
    "",
    "",
    HASH_TYPE_ID_STYLE,
    "data/test-read-dwarf/test1.hash.abi",
    "output/test-read-dwarf/test1.hash.abi"
  },
  {
    "data/test-read-dwarf/test2.so",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/test2.so.abi",
    "output/test-read-dwarf/test2.so.abi"
  },
  {
    "data/test-read-dwarf/test2.so",
    "",
    "",
    HASH_TYPE_ID_STYLE,
    "data/test-read-dwarf/test2.so.hash.abi",
    "output/test-read-dwarf/test2.so.hash.abi"
  },
  {
    "data/test-read-common/test3.so",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/test3.so.abi",
    "output/test-read-dwarf/test3.so.abi"
  },
  {
    "data/test-read-common/test3.so",
    "",
    "",
    HASH_TYPE_ID_STYLE,
    "data/test-read-dwarf/test3.so.hash.abi",
    "output/test-read-dwarf/test3.so.hash.abi"
  },
  // suppress all except the main symbol of a group of aliases
  {
    "data/test-read-common/test3.so",
    "data/test-read-common/test3-alias-1.suppr",
    "",
    HASH_TYPE_ID_STYLE,
    "data/test-read-dwarf/test3-alias-1.so.hash.abi",
    "output/test-read-dwarf/test3-alias-1.so.hash.abi"
  },
  // suppress the main symbol of a group of aliases
  {
    "data/test-read-common/test3.so",
    "data/test-read-common/test3-alias-2.suppr",
    "",
    HASH_TYPE_ID_STYLE,
    "data/test-read-dwarf/test3-alias-2.so.hash.abi",
    "output/test-read-dwarf/test3-alias-2.so.hash.abi"
  },
  // suppress all except one non main symbol of a group of aliases
  {
    "data/test-read-common/test3.so",
    "data/test-read-common/test3-alias-3.suppr",
    "",
    HASH_TYPE_ID_STYLE,
    "data/test-read-dwarf/test3-alias-3.so.hash.abi",
    "output/test-read-dwarf/test3-alias-3.so.hash.abi"
  },
  // suppress all symbols of a group of aliases
  {
    "data/test-read-common/test3.so",
    "data/test-read-common/test3-alias-4.suppr",
    "",
    HASH_TYPE_ID_STYLE,
    "data/test-read-dwarf/test3-alias-4.so.hash.abi",
    "output/test-read-dwarf/test3-alias-4.so.hash.abi"
  },
  // suppress the main symbols with alias (function+variable) in .o file
  {
    "data/test-read-dwarf/test-suppressed-alias.o",
    "data/test-read-dwarf/test-suppressed-alias.suppr",
    "",
    HASH_TYPE_ID_STYLE,
    "data/test-read-dwarf/test-suppressed-alias.o.abi",
    "output/test-read-dwarf/test-suppressed-alias.o.abi",
  },
  {
    "data/test-read-common/test4.so",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/test4.so.abi",
    "output/test-read-dwarf/test4.so.abi"
  },
  {
    "data/test-read-common/test4.so",
    "",
    "",
    HASH_TYPE_ID_STYLE,
    "data/test-read-dwarf/test4.so.hash.abi",
    "output/test-read-dwarf/test4.so.hash.abi"
  },
  {
    "data/test-read-dwarf/test5.o",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/test5.o.abi",
    "output/test-read-dwarf/test5.o.abi"
  },
  {
    "data/test-read-dwarf/test5.o",
    "",
    "",
    HASH_TYPE_ID_STYLE,
    "data/test-read-dwarf/test5.o.hash.abi",
    "output/test-read-dwarf/test5.o.hash.abi"
  },
  {
    "data/test-read-dwarf/test6.so",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/test6.so.abi",
    "output/test-read-dwarf/test6.so.abi"
  },
  {
    "data/test-read-dwarf/test6.so",
    "",
    "",
    HASH_TYPE_ID_STYLE,
    "data/test-read-dwarf/test6.so.hash.abi",
    "output/test-read-dwarf/test6.so.hash.abi"
  },
  {
    "data/test-read-dwarf/test7.so",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/test7.so.abi",
    "output/test-read-dwarf/test7.so.abi"
  },
  {
    "data/test-read-dwarf/test7.so",
    "",
    "",
    HASH_TYPE_ID_STYLE,
    "data/test-read-dwarf/test7.so.hash.abi",
    "output/test-read-dwarf/test7.so.hash.abi"
  },
  {
    "data/test-read-dwarf/test8-qualified-this-pointer.so",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/test8-qualified-this-pointer.so.abi",
    "output/test-read-dwarf/test8-qualified-this-pointer.so.abi"
  },
  {
    "data/test-read-dwarf/test8-qualified-this-pointer.so",
    "",
    "",
    HASH_TYPE_ID_STYLE,
    "data/test-read-dwarf/test8-qualified-this-pointer.so.hash.abi",
    "output/test-read-dwarf/test8-qualified-this-pointer.so.hash.abi"
  },
  {
    "data/test-read-dwarf/test9-pr18818-clang.so",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/test9-pr18818-clang.so.abi",
    "output/test-read-dwarf/test9-pr18818-clang.so.abi"
  },
  {
    "data/test-read-dwarf/test10-pr18818-gcc.so",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/test10-pr18818-gcc.so.abi",
    "output/test-read-dwarf/test10-pr18818-gcc.so.abi"
  },
  {
    "data/test-read-dwarf/test11-pr18828.so",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/test11-pr18828.so.abi",
    "output/test-read-dwarf/test11-pr18828.so.abi",
  },
  {
    "data/test-read-dwarf/test12-pr18844.so",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/test12-pr18844.so.abi",
    "output/test-read-dwarf/test12-pr18844.so.abi",
  },
  {
    "data/test-read-dwarf/test13-pr18894.so",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/test13-pr18894.so.abi",
    "output/test-read-dwarf/test13-pr18894.so.abi",
  },
  {
    "data/test-read-dwarf/test14-pr18893.so",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/test14-pr18893.so.abi",
    "output/test-read-dwarf/test14-pr18893.so.abi",
  },
  {
    "data/test-read-dwarf/test15-pr18892.so",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/test15-pr18892.so.abi",
    "output/test-read-dwarf/test15-pr18892.so.abi",
  },
  {
    "data/test-read-dwarf/test16-pr18904.so",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/test16-pr18904.so.abi",
    "output/test-read-dwarf/test16-pr18904.so.abi",
  },
  {
    "data/test-read-dwarf/test17-pr19027.so",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/test17-pr19027.so.abi",
    "output/test-read-dwarf/test17-pr19027.so.abi",
  },
  {
    "data/test-read-dwarf/test18-pr19037-libvtkRenderingLIC-6.1.so",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/test18-pr19037-libvtkRenderingLIC-6.1.so.abi",
    "output/test-read-dwarf/test18-pr19037-libvtkRenderingLIC-6.1.so.abi",
  },
  {
    "data/test-read-dwarf/test19-pr19023-libtcmalloc_and_profiler.so",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/test19-pr19023-libtcmalloc_and_profiler.so.abi",
    "output/test-read-dwarf/test19-pr19023-libtcmalloc_and_profiler.so.abi",
  },
  {
    "data/test-read-dwarf/test20-pr19025-libvtkParallelCore-6.1.so",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/test20-pr19025-libvtkParallelCore-6.1.so.abi",
    "output/test-read-dwarf/test20-pr19025-libvtkParallelCore-6.1.so.abi",
  },
  {
    "data/test-read-dwarf/test21-pr19092.so",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/test21-pr19092.so.abi",
    "output/test-read-dwarf/test21-pr19092.so.abi",
  },
  {
    "data/test-read-dwarf/test22-pr19097-libstdc++.so.6.0.17.so",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/test22-pr19097-libstdc++.so.6.0.17.so.abi",
    "output/test-read-dwarf/test22-pr19097-libstdc++.so.6.0.17.so.abi",
  },
  {
    "data/test-read-dwarf/libtest23.so",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/libtest23.so.abi",
    "output/test-read-dwarf/libtest23.so.abi",
  },
  {
    "data/test-read-dwarf/libtest24-drop-fns.so",
    "data/test-read-dwarf/test24-drop-fns-0.suppr",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/libtest24-drop-fns.so.abi",
    "output/test-read-dwarf/libtest24-drop-fns.so.abi",
  },
  {
    "data/test-read-dwarf/libtest24-drop-fns.so",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/libtest24-drop-fns-2.so.abi",
    "output/test-read-dwarf/libtest24-drop-fns-2.so.abi",
  },
  {
    "data/test-read-dwarf/PR22015-libboost_iostreams.so",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/PR22015-libboost_iostreams.so.abi",
    "output/test-read-dwarf/PR22015-libboost_iostreams.so.abi",
  },
  {
    "data/test-read-dwarf/PR22122-libftdc.so",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/PR22122-libftdc.so.abi",
    "output/test-read-dwarf/PR22122-libftdc.so.abi",
  },
  {
    "data/test-read-dwarf/PR24378-fn-is-not-scope.o",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/PR24378-fn-is-not-scope.abi",
    "output/test-read-dwarf/PR24378-fn-is-not-scope.abi",
  },
#if defined(HAVE_R_AARCH64_ABS64_MACRO) && defined(HAVE_R_AARCH64_PREL32_MACRO)
  {
    "data/test-read-dwarf/PR25007-sdhci.ko",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/PR25007-sdhci.ko.abi",
    "output/test-read-dwarf/PR25007-sdhci.ko.abi",
  },
#endif
#if defined HAVE_DW_FORM_strx
  {
    "data/test-read-dwarf/PR25042-libgdbm-clang-dwarf5.so.6.0.0",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/PR25042-libgdbm-clang-dwarf5.so.6.0.0.abi",
    "output/test-read-dwarf/PR25042-libgdbm-clang-dwarf5.so.6.0.0.abi",
  },
#endif
  {
    "data/test-read-dwarf/test25-bogus-binary.elf",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    NULL,
    NULL,
  },
  {
    "data/test-read-dwarf/test26-bogus-binary.elf",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    NULL,
    NULL,
  },
  {
    "data/test-read-dwarf/test27-bogus-binary.elf",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    NULL,
    NULL,
  },
  {
    "data/test-read-common/PR26261/PR26261-exe",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/PR26261/PR26261-exe.abi",
    "output/test-read-dwarf/PR26261/PR26261-exe.abi",
  },
  {
    "data/test-read-common/test-PR26568-1.o",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/test-PR26568-1.o.abi",
    "output/test-read-dwarf/test-PR26568-1.o.abi",
  },
  {
    "data/test-read-common/test-PR26568-2.o",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/test-PR26568-2.o.abi",
    "output/test-read-dwarf/test-PR26568-2.o.abi",
  },
  {
    "data/test-read-dwarf/test-libandroid.so",
    "",
    "",
    HASH_TYPE_ID_STYLE,
    "data/test-read-dwarf/test-libandroid.so.abi",
    "output/test-read-dwarf/test-libandroid.so.abi",
  },
  {
    "data/test-read-common/PR27700/test-PR27700.o",
    "",
    "data/test-read-common/PR27700/pub-incdir",
    HASH_TYPE_ID_STYLE,
    "data/test-read-dwarf/PR27700/test-PR27700.abi",
    "output/test-read-dwarf/PR27700/test-PR27700.abi",
  },
  {
    "data/test-read-dwarf/test-libaaudio.so",
    "",
    "",
    HASH_TYPE_ID_STYLE,
    "data/test-read-dwarf/test-libaaudio.so.abi",
    "output/test-read-dwarf/test-libaaudio.so.abi",
  },
  {
    "data/test-read-dwarf/PR28584/PR28584-smv.clang.o",
    "",
    "",
    SEQUENCE_TYPE_ID_STYLE,
    "data/test-read-dwarf/PR28584/PR28584-smv.clang.o.abi",
    "output/test-read-dwarf/PR28584/PR28584-smv.clang.o.abi",
  },
  // This should be the last entry.
  {NULL, NULL, NULL, SEQUENCE_TYPE_ID_STYLE, NULL, NULL}
};

using abigail::suppr::suppression_sptr;
using abigail::suppr::suppressions_type;
using abigail::suppr::read_suppressions;

/// Set the suppression specification to use when reading the ELF binary.
///
/// @param read_ctxt the context used to read the ELF binary.
///
/// @param path the path to the suppression specification to read.
static void
set_suppressions(read_context& read_ctxt, const string& path)
{
  suppressions_type supprs;
  read_suppressions(path, supprs);
  add_read_context_suppressions(read_ctxt, supprs);
}

/// Define what headers contain public types definitions.
///
/// This automatically generates suppression specifications from the
/// set of header files present under a given directory.  Those
/// specifications actually suppress types that are *not* defined in
/// the headers found at a given directory.
///
/// @param read_ctxt the context used to read the ELF binary.
///
/// @param path the path to a directory where header files are to be
/// found.
static void
set_suppressions_from_headers(read_context& read_ctxt, const string& path)
{
  vector<string> files;
  suppression_sptr suppr =
    abigail::tools_utils::gen_suppr_spec_from_headers(path, files);

  if (suppr)
    {
      suppr->set_drops_artifact_from_ir(true);
      suppressions_type supprs;
      supprs.push_back(suppr);
      add_read_context_suppressions(read_ctxt, supprs);
    }
}

/// Task specialization to perform DWARF tests.
struct test_task_dwarf : public test_task
{
  test_task_dwarf(const InOutSpec &s,
                string& a_out_abi_base,
                string& a_in_elf_base,
                string& a_in_abi_base);
  virtual void
  perform();

  virtual
  ~test_task_dwarf()
  {}
}; // end struct test_task_dwarf

/// Constructor.
///
/// Task to be executed for each DWARF test entry in @ref
/// abigail::tests::read_common::InOutSpec.
///
/// @param InOutSpec the array containing set of tests.
///
/// @param a_out_abi_base the output base directory for abixml files.
///
/// @param a_in_elf_base the input base directory for object files.
///
/// @param a_in_elf_base the input base directory for expected
/// abixml files.
test_task_dwarf::test_task_dwarf(const InOutSpec &s,
                             string& a_out_abi_base,
                             string& a_in_elf_base,
                             string& a_in_abi_base)
        : test_task(s, a_out_abi_base, a_in_elf_base, a_in_abi_base)
  {}

/// The thread function to execute each DWARF test entry in @ref
/// abigail::tests::read_common::InOutSpec.
///
/// This reads the corpus into memory, saves it to disk, loads it
/// again and compares the new in-memory representation against the
void
test_task_dwarf::perform()
{
  abigail::ir::environment_sptr env;

  set_in_elf_path();
  set_in_suppr_spec_path();
  set_in_public_headers_path();

  env.reset(new abigail::ir::environment);
  abigail::elf_reader::status status =
    abigail::elf_reader::STATUS_UNKNOWN;
  vector<char**> di_roots;
  ABG_ASSERT(abigail::tools_utils::file_exists(in_elf_path));
  read_context_sptr ctxt = create_read_context(in_elf_path,
                                               di_roots,
                                               env.get());
  ABG_ASSERT(ctxt);
  if (!in_suppr_spec_path.empty())
    set_suppressions(*ctxt, in_suppr_spec_path);

  if (!in_public_headers_path.empty())
    set_suppressions_from_headers(*ctxt, in_public_headers_path);

  abigail::corpus_sptr corp = read_corpus_from_elf(*ctxt, status);
  // if there is no output and no input, assume that we do not care about the
  // actual read result, just that it succeeded.
  if (!spec.in_abi_path && !spec.out_abi_path)
    {
      // Phew! we made it here and we did not crash! yay!
      return;
    }
  if (!corp)
    {
      error_message = string("failed to read ") + in_elf_path  + "\n";
      is_ok = false;
      return;
    }
  corp->set_path(spec.in_elf_path);
  // Do not take architecture names in comparison so that these
  // test input binaries can come from whatever arch the
  // programmer likes.
  corp->set_architecture_name("");

  if (!(is_ok = set_out_abi_path()))
      return;

  if (!(is_ok = serialize_corpus(out_abi_path, corp)))
       return;

  if (!(is_ok = run_abidw()))
    return;

  if (!(is_ok = run_diff()))
      return;
}

/// Create a new DWARF instance for task to be execute by the testsuite.
///
/// @param s the @ref abigail::tests::read_common::InOutSpec
/// tests container.
///
/// @param a_out_abi_base the output base directory for abixml files.
///
/// @param a_in_elf_base the input base directory for object files.
///
/// @param a_in_abi_base the input base directory for abixml files.
///
/// @return abigail::tests::read_common::test_task instance.
static test_task*
new_task(const InOutSpec* s, string& a_out_abi_base,
         string& a_in_elf_base, string& a_in_abi_base)
{
  return new test_task_dwarf(*s, a_out_abi_base,
                             a_in_elf_base, a_in_abi_base);
}

int
main(int argc, char *argv[])
{
  options opts;
  if (!parse_command_line(argc, argv, opts))
    {
      if (!opts.wrong_option.empty())
        emit_prefix(argv[0], cerr)
          << "unrecognized option: " << opts.wrong_option << "\n";
      display_usage(argv[0], cerr);
      return 1;
    }

  // compute number of tests to be executed.
  const size_t num_tests = sizeof(in_out_specs) / sizeof(InOutSpec) - 1;

  return run_tests(num_tests, in_out_specs, opts, new_task);
}
