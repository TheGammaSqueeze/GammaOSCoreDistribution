// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- Mode: C++ -*-
//
// Copyright (C) 2021 Oracle, Inc.
//
// Author: Jose E. Marchesi

/// @file
///
/// This file contains the definitions of the entry points to
/// de-serialize an instance of @ref abigail::corpus from a file in
/// ELF format, containing CTF information.

#include "config.h"

#include <fcntl.h> /* For open(3) */
#include <iostream>
#include <memory>

#include "ctf-api.h"

#include "abg-internal.h"
#include "abg-ir-priv.h"
#include "abg-elf-helpers.h"

// <headers defining libabigail's API go under here>
ABG_BEGIN_EXPORT_DECLARATIONS

#include "abg-ctf-reader.h"
#include "abg-libxml-utils.h"
#include "abg-reader.h"
#include "abg-corpus.h"
#include "abg-symtab-reader.h"
#include "abg-tools-utils.h"

ABG_END_EXPORT_DECLARATIONS
// </headers defining libabigail's API>

namespace abigail
{
namespace ctf_reader
{
using std::dynamic_pointer_cast;

class read_context
{
public:
  /// The name of the ELF file from which the CTF archive got
  /// extracted.
  string filename;

  /// The IR environment.
  ir::environment *ir_env;

  /// The CTF archive read from FILENAME.  If an archive couldn't
  /// be read from the file then this is NULL.
  ctf_archive_t *ctfa;

  /// A map associating CTF type ids with libabigail IR types.  This
  /// is used to reuse already generated types.
  unordered_map<ctf_id_t,type_base_sptr> types_map;

  /// libelf handler for the ELF file from which we read the CTF data,
  /// and the corresponding file descriptor.
  Elf *elf_handler;
  int elf_fd;

  /// The symtab read from the ELF file.
  symtab_reader::symtab_sptr symtab;

  /// Raw contents of several sections from the ELF file.  These are
  /// used by libctf.
  ctf_sect_t ctf_sect;
  ctf_sect_t symtab_sect;
  ctf_sect_t strtab_sect;

  /// Associate a given CTF type ID with a given libabigail IR type.
  void add_type(ctf_id_t ctf_type, type_base_sptr type)
  {
    types_map.insert(std::make_pair(ctf_type, type));
  }

  /// Lookup a given CTF type ID in the types map.
  ///
  /// @param ctf_type the type ID of the type to lookup.
  type_base_sptr lookup_type(ctf_id_t ctf_type)
  {
    type_base_sptr result;

    auto search = types_map.find(ctf_type);
    if (search != types_map.end())
      result = search->second;

    return result;
  }

  /// Canonicalize all the types stored in the types map.
  void canonicalize_all_types(void)
  {
    for (auto t = types_map.begin(); t != types_map.end(); t++)
      canonicalize (t->second);
  }

  /// Constructor.
  ///
  /// @param elf_path the path to the ELF file.
  read_context(const string& elf_path, ir::environment *env)
  {
    types_map.clear();
    filename = elf_path;
    ir_env = env;
    elf_handler = NULL;
    elf_fd = -1;
    ctfa = NULL;
  }

  /// Destructor of the @ref read_context type.
  ~read_context()
  {
    ctf_close(ctfa);
  }
}; // end class read_context.

/// Forward reference, needed because several of the process_ctf_*
/// functions below are indirectly recursive through this call.
static type_base_sptr lookup_type(read_context *ctxt,
                                  corpus_sptr corp,
                                  translation_unit_sptr tunit,
                                  ctf_dict_t *ctf_dictionary,
                                  ctf_id_t ctf_type);

/// Build and return a typedef libabigail IR.
///
/// @param ctxt the read context.
/// @param corp the libabigail IR corpus being constructed.
/// @param tunit the current IR translation unit.
/// @param ctf_dictionary the CTF dictionary being read.
/// @param ctf_type the CTF type ID of the source type.
///
/// @return a shared pointer to the IR node for the typedef.

static typedef_decl_sptr
process_ctf_typedef(read_context *ctxt,
                    corpus_sptr corp,
                    translation_unit_sptr tunit,
                    ctf_dict_t *ctf_dictionary,
                    ctf_id_t ctf_type)
{
  typedef_decl_sptr result;

  ctf_id_t ctf_utype = ctf_type_reference(ctf_dictionary, ctf_type);
  if (ctf_utype == CTF_ERR)
    return result;

  const char *typedef_name = ctf_type_name_raw(ctf_dictionary, ctf_type);
  type_base_sptr utype = lookup_type(ctxt, corp, tunit,
                                     ctf_dictionary, ctf_utype);

  if (!utype)
    return result;

  result = dynamic_pointer_cast<typedef_decl>(ctxt->lookup_type(ctf_type));
  if (result)
    return result;

  result.reset(new typedef_decl(typedef_name, utype, location(),
                                typedef_name /* mangled_name */));

  /* If this typedef "names" an anonymous type, reflect this fact in
     the underlying type.  In C enum, struct and union types can be
     anonymous.  */
  if (is_anonymous_type(utype)
      && (is_enum_type(utype) || is_class_or_union_type(utype)))
    {
      decl_base_sptr decl = is_decl(utype);
      ABG_ASSERT(decl);
      decl->set_naming_typedef(result);
    }

  if (result)
    {
      add_decl_to_scope(result, tunit->get_global_scope());
      ctxt->add_type(ctf_type, result);
    }

  return result;
}

/// Build and return an integer or float type declaration libabigail
/// IR.
///
/// @param ctxt the read context.
/// @param corp the libabigail IR corpus being constructed.
/// @param ctf_dictionary the CTF dictionary being read.
/// @param ctf_type the CTF type ID of the source type.
///
/// @return a shared pointer to the IR node for the type.

static type_decl_sptr
process_ctf_base_type(read_context *ctxt,
                      corpus_sptr corp,
                      translation_unit_sptr tunit,
                      ctf_dict_t *ctf_dictionary,
                      ctf_id_t ctf_type)
{
  type_decl_sptr result;

  ssize_t type_alignment = ctf_type_align(ctf_dictionary, ctf_type);
  const char *type_name = ctf_type_name_raw(ctf_dictionary, ctf_type);

  /* Get the type encoding and extract some useful properties of
     the type from it.  In case of any error, just ignore the
     type.  */
  ctf_encoding_t type_encoding;
  if (ctf_type_encoding(ctf_dictionary,
                         ctf_type,
                         &type_encoding))
    return result;

  /* Create the IR type corresponding to the CTF type.  */
  if (type_encoding.cte_bits == 0
      && type_encoding.cte_format == CTF_INT_SIGNED)
    {
      /* This is the `void' type.  */
      type_base_sptr void_type = ctxt->ir_env->get_void_type();
      decl_base_sptr type_declaration = get_type_declaration(void_type);
      result = is_type_decl(type_declaration);
    }
  else
    {
      result = lookup_basic_type(type_name, *corp);
      if (!result)
        result.reset(new type_decl(ctxt->ir_env,
                                   type_name,
                                   type_encoding.cte_bits,
                                   type_alignment * 8 /* in bits */,
                                   location(),
                                   type_name /* mangled_name */));

    }

  if (result)
    {
      add_decl_to_scope(result, tunit->get_global_scope());
      ctxt->add_type(ctf_type, result);
    }

  return result;
}

/// Build and return a function type libabigail IR.
///
/// @param ctxt the read context.
/// @param corp the libabigail IR corpus being constructed.
/// @param tunit the current IR translation unit.
/// @param ctf_dictionary the CTF dictionary being read.
/// @param ctf_type the CTF type ID of the source type.
///
/// @return a shared pointer to the IR node for the function type.

static function_type_sptr
process_ctf_function_type(read_context *ctxt,
                          corpus_sptr corp,
                          translation_unit_sptr tunit,
                          ctf_dict_t *ctf_dictionary,
                          ctf_id_t ctf_type)
{
  function_type_sptr result;

  /* Fetch the function type info from the CTF type.  */
  ctf_funcinfo_t funcinfo;
  ctf_func_type_info(ctf_dictionary, ctf_type, &funcinfo);
  int vararg_p = funcinfo.ctc_flags & CTF_FUNC_VARARG;

  /* Take care first of the result type.  */
  ctf_id_t ctf_ret_type = funcinfo.ctc_return;
  type_base_sptr ret_type = lookup_type(ctxt, corp, tunit,
                                        ctf_dictionary, ctf_ret_type);
  if (!ret_type)
    return result;

  /* Now process the argument types.  */
  int argc = funcinfo.ctc_argc;
  std::vector<ctf_id_t> argv(argc);
  if (static_cast<ctf_id_t>(ctf_func_type_args(ctf_dictionary, ctf_type,
					       argc, argv.data())) == CTF_ERR)
    return result;

  function_decl::parameters function_parms;
  for (int i = 0; i < argc; i++)
    {
      ctf_id_t ctf_arg_type = argv[i];
      type_base_sptr arg_type = lookup_type(ctxt, corp, tunit,
                                            ctf_dictionary, ctf_arg_type);
      if (!arg_type)
        return result;

      function_decl::parameter_sptr parm
        (new function_decl::parameter(arg_type, "",
                                      location(),
                                      vararg_p && (i == argc - 1),
                                      false /* is_artificial */));
      function_parms.push_back(parm);
    }

  result = dynamic_pointer_cast<function_type>(ctxt->lookup_type(ctf_type));
  if (result)
    return result;

  /* Ok now the function type itself.  */
  result.reset(new function_type(ret_type,
                                 function_parms,
                                 tunit->get_address_size(),
                                 ctf_type_align(ctf_dictionary, ctf_type)));

  if (result)
    {
      tunit->bind_function_type_life_time(result);
      result->set_is_artificial(true);
      decl_base_sptr function_type_decl = get_type_declaration(result);
      add_decl_to_scope(function_type_decl, tunit->get_global_scope());
      ctxt->add_type(ctf_type, result);
    }

  return result;
}

/// Add member information to a IR struct or union type.
///
/// @param ctxt the read context.
/// @param corp the libabigail IR corpus being constructed.
/// @param tunit the current IR translation unit.
/// @param ctf_dictionary the CTF dictionary being read.
/// @param ctf_type the CTF type ID of the source type.
/// @param sou the IR struct or union type to which add the members.

static void
process_ctf_sou_members(read_context *ctxt,
                        corpus_sptr corp,
                        translation_unit_sptr tunit,
                        ctf_dict_t *ctf_dictionary,
                        ctf_id_t ctf_type,
                        class_or_union_sptr sou)
{
  ssize_t member_size;
  ctf_next_t *member_next = NULL;
  const char *member_name = NULL;
  ctf_id_t member_ctf_type;

  while ((member_size = ctf_member_next(ctf_dictionary, ctf_type,
                                        &member_next, &member_name,
                                        &member_ctf_type,
                                        CTF_MN_RECURSE)) >= 0)
    {
      ctf_membinfo_t membinfo;

      if (static_cast<ctf_id_t>(ctf_member_info(ctf_dictionary,
						ctf_type,
						member_name,
						&membinfo)) == CTF_ERR)
        return;

      /* Build the IR for the member's type.  */
      type_base_sptr member_type = lookup_type(ctxt, corp, tunit,
                                               ctf_dictionary,
                                               member_ctf_type);
      if (!member_type)
        /* Ignore this member.  */
        continue;

      /* Create a declaration IR node for the member and add it to the
         struct type.  */
      var_decl_sptr data_member_decl(new var_decl(member_name,
                                                  member_type,
                                                  location(),
                                                  member_name));
      sou->add_data_member(data_member_decl,
                           public_access,
                           true /* is_laid_out */,
                           false /* is_static */,
                           membinfo.ctm_offset);
    }
  if (ctf_errno(ctf_dictionary) != ECTF_NEXT_END)
    fprintf(stderr, "ERROR from ctf_member_next\n");
}

/// Build and return a struct type libabigail IR.
///
/// @param ctxt the read context.
/// @param corp the libabigail IR corpus being constructed.
/// @param tunit the current IR translation unit.
/// @param ctf_dictionary the CTF dictionary being read.
/// @param ctf_type the CTF type ID of the source type.
///
/// @return a shared pointer to the IR node for the struct type.

static class_decl_sptr
process_ctf_struct_type(read_context *ctxt,
                        corpus_sptr corp,
                        translation_unit_sptr tunit,
                        ctf_dict_t *ctf_dictionary,
                        ctf_id_t ctf_type)
{
  class_decl_sptr result;
  std::string struct_type_name = ctf_type_name_raw(ctf_dictionary,
                                                 ctf_type);
  bool struct_type_is_anonymous = (struct_type_name == "");

  /* The libabigail IR encodes C struct types in `class' IR nodes.  */
  result.reset(new class_decl(ctxt->ir_env,
                              struct_type_name,
                              ctf_type_size(ctf_dictionary, ctf_type) * 8,
                              ctf_type_align(ctf_dictionary, ctf_type) * 8,
                              true /* is_struct */,
                              location(),
                              decl_base::VISIBILITY_DEFAULT,
                              struct_type_is_anonymous));
  if (!result)
    return result;

  /* The C type system indirectly supports loops by the mean of
     pointers to structs or unions.  Since some contained type can
     refer to this struct, we have to make it available in the cache
     at this point even if the members haven't been added to the IR
     node yet.  */
  add_decl_to_scope(result, tunit->get_global_scope());
  ctxt->add_type(ctf_type, result);

  /* Now add the struct members as specified in the CTF type description.
     This is C, so named types can only be defined in the global
     scope.  */
  process_ctf_sou_members(ctxt, corp, tunit, ctf_dictionary, ctf_type,
                          result);

  return result;
}

/// Build and return an union type libabigail IR.
///
/// @param ctxt the read context.
/// @param corp the libabigail IR corpus being constructed.
/// @param tunit the current IR translation unit.
/// @param ctf_dictionary the CTF dictionary being read.
/// @param ctf_type the CTF type ID of the source type.
///
/// @return a shared pointer to the IR node for the union type.

static union_decl_sptr
process_ctf_union_type(read_context *ctxt,
                       corpus_sptr corp,
                       translation_unit_sptr tunit,
                       ctf_dict_t *ctf_dictionary,
                       ctf_id_t ctf_type)
{
  union_decl_sptr result;
  std::string union_type_name = ctf_type_name_raw(ctf_dictionary,
                                                   ctf_type);
  bool union_type_is_anonymous = (union_type_name == "");

  /* Create the corresponding libabigail union IR node.  */
  result.reset(new union_decl(ctxt->ir_env,
                                union_type_name,
                                ctf_type_size(ctf_dictionary, ctf_type) * 8,
                                location(),
                                decl_base::VISIBILITY_DEFAULT,
                                union_type_is_anonymous));
  if (!result)
    return result;

  /* The C type system indirectly supports loops by the mean of
     pointers to structs or unions.  Since some contained type can
     refer to this union, we have to make it available in the cache
     at this point even if the members haven't been added to the IR
     node yet.  */
  add_decl_to_scope(result, tunit->get_global_scope());
  ctxt->add_type(ctf_type, result);

  /* Now add the union members as specified in the CTF type description.
     This is C, so named types can only be defined in the global
     scope.  */
  process_ctf_sou_members(ctxt, corp, tunit, ctf_dictionary, ctf_type,
                          result);

  return result;
}

/// Build and return an array type libabigail IR.
///
/// @param ctxt the read context.
/// @param corp the libabigail IR corpus being constructed.
/// @param tunit the current IR translation unit.
/// @param ctf_dictionary the CTF dictionary being read.
/// @param ctf_type the CTF type ID of the source type.
///
/// @return a shared pointer to the IR node for the array type.

static array_type_def_sptr
process_ctf_array_type(read_context *ctxt,
                       corpus_sptr corp,
                       translation_unit_sptr tunit,
                       ctf_dict_t *ctf_dictionary,
                       ctf_id_t ctf_type)
{
  array_type_def_sptr result;
  ctf_arinfo_t ctf_ainfo;
  bool is_infinite = false;

  /* First, get the information about the CTF array.  */
  if (static_cast<ctf_id_t>(ctf_array_info(ctf_dictionary,
					   ctf_type,
					   &ctf_ainfo)) == CTF_ERR)
    return result;

  ctf_id_t ctf_element_type = ctf_ainfo.ctr_contents;
  ctf_id_t ctf_index_type = ctf_ainfo.ctr_index;
  uint64_t nelems = ctf_ainfo.ctr_nelems;

  /* Make sure the element type is generated.  */
  type_base_sptr element_type = lookup_type(ctxt, corp, tunit,
                                            ctf_dictionary,
                                            ctf_element_type);
  if (!element_type)
    return result;

  /* Ditto for the index type.  */
  type_base_sptr index_type = lookup_type(ctxt, corp, tunit,
                                          ctf_dictionary,
                                          ctf_index_type);
  if (!index_type)
    return result;

  result = dynamic_pointer_cast<array_type_def>(ctxt->lookup_type(ctf_type));
  if (result)
    return result;

  /* The number of elements of the array determines the IR subranges
     type to build.  */
  array_type_def::subranges_type subranges;
  array_type_def::subrange_sptr subrange;
  array_type_def::subrange_type::bound_value lower_bound;
  array_type_def::subrange_type::bound_value upper_bound;

  lower_bound.set_unsigned(0); /* CTF supports C only.  */
  upper_bound.set_unsigned(nelems > 0 ? nelems - 1 : 0U);

  /* for VLAs number of array elements is 0 */
  if (upper_bound.get_unsigned_value() == 0)
    is_infinite = true;

  subrange.reset(new array_type_def::subrange_type(ctxt->ir_env,
                                                   "",
                                                   lower_bound,
                                                   upper_bound,
                                                   index_type,
                                                   location(),
                                                   translation_unit::LANG_C));
  if (!subrange)
    return result;

  subrange->is_infinite(is_infinite);
  add_decl_to_scope(subrange, tunit->get_global_scope());
  canonicalize(subrange);
  subranges.push_back(subrange);

  /* Finally build the IR for the array type and return it.  */
  result.reset(new array_type_def(element_type, subranges, location()));
  if (result)
    {
      decl_base_sptr array_type_decl = get_type_declaration(result);
      add_decl_to_scope(array_type_decl, tunit->get_global_scope());
      ctxt->add_type(ctf_type, result);
    }

  return result;
}

/// Build and return a qualified type libabigail IR.
///
/// @param ctxt the read context.
/// @param corp the libabigail IR corpus being constructed.
/// @param tunit the current IR translation unit.
/// @param ctf_dictionary the CTF dictionary being read.
/// @param ctf_type the CTF type ID of the source type.

static type_base_sptr
process_ctf_qualified_type(read_context *ctxt,
                           corpus_sptr corp,
                           translation_unit_sptr tunit,
                           ctf_dict_t *ctf_dictionary,
                           ctf_id_t ctf_type)
{
  type_base_sptr result;
  int type_kind = ctf_type_kind(ctf_dictionary, ctf_type);
  ctf_id_t ctf_utype = ctf_type_reference(ctf_dictionary, ctf_type);
  type_base_sptr utype = lookup_type(ctxt, corp, tunit,
                                     ctf_dictionary, ctf_utype);
  if (!utype)
    return result;

  qualified_type_def::CV qualifiers = qualified_type_def::CV_NONE;
  if (type_kind == CTF_K_CONST)
    qualifiers |= qualified_type_def::CV_CONST;
  else if (type_kind == CTF_K_VOLATILE)
    qualifiers |= qualified_type_def::CV_VOLATILE;
  else if (type_kind == CTF_K_RESTRICT)
    qualifiers |= qualified_type_def::CV_RESTRICT;
  else
    ABG_ASSERT_NOT_REACHED;

  result.reset(new qualified_type_def(utype, qualifiers, location()));

  if (result)
    {
      decl_base_sptr qualified_type_decl = get_type_declaration(result);
      add_decl_to_scope(qualified_type_decl, tunit->get_global_scope());
      ctxt->add_type(ctf_type, result);
    }

  return result;
}

/// Build and return a pointer type libabigail IR.
///
/// @param ctxt the read context.
/// @param corp the libabigail IR corpus being constructed.
/// @param tunit the current IR translation unit.
/// @param ctf_dictionary the CTF dictionary being read.
/// @param ctf_type the CTF type ID of the source type.
///
/// @return a shared pointer to the IR node for the pointer type.

static pointer_type_def_sptr
process_ctf_pointer_type(read_context *ctxt,
                         corpus_sptr corp,
                         translation_unit_sptr tunit,
                         ctf_dict_t *ctf_dictionary,
                         ctf_id_t ctf_type)
{
  pointer_type_def_sptr result;
  ctf_id_t ctf_target_type = ctf_type_reference(ctf_dictionary, ctf_type);
  if (ctf_target_type == CTF_ERR)
    return result;

  type_base_sptr target_type = lookup_type(ctxt, corp, tunit,
                                           ctf_dictionary,
                                           ctf_target_type);
  if (!target_type)
    return result;

  result = dynamic_pointer_cast<pointer_type_def>(ctxt->lookup_type(ctf_type));
  if (result)
    return result;

  result.reset(new pointer_type_def(target_type,
                                      ctf_type_size(ctf_dictionary, ctf_type) * 8,
                                      ctf_type_align(ctf_dictionary, ctf_type) * 8,
                                      location()));
  if (result)
    {
      add_decl_to_scope(result, tunit->get_global_scope());
      ctxt->add_type(ctf_type, result);
    }

  return result;
}

/// Build and return an enum type libabigail IR.
///
/// @param ctxt the read context.
/// @param corp the libabigail IR corpus being constructed.
/// @param tunit the current IR translation unit.
/// @param ctf_dictionary the CTF dictionary being read.
/// @param ctf_type the CTF type ID of the source type.
///
/// @return a shared pointer to the IR node for the enum type.

static enum_type_decl_sptr
process_ctf_enum_type(read_context *ctxt,
                      translation_unit_sptr tunit,
                      ctf_dict_t *ctf_dictionary,
                      ctf_id_t ctf_type)
{
  enum_type_decl_sptr result;

  /* Build a signed integral type for the type of the enumerators, aka
     the underlying type.  The size of the enumerators in bytes is
     specified in the CTF enumeration type.  */
  size_t utype_size_in_bits = ctf_type_size(ctf_dictionary, ctf_type) * 8;
  type_decl_sptr utype;

  utype.reset(new type_decl(ctxt->ir_env,
                              "",
                              utype_size_in_bits,
                              utype_size_in_bits,
                              location()));
  utype->set_is_anonymous(true);
  utype->set_is_artificial(true);
  if (!utype)
    return result;
  add_decl_to_scope(utype, tunit->get_global_scope());
  canonicalize(utype);

  /* Iterate over the enum entries.  */
  enum_type_decl::enumerators enms;
  ctf_next_t *enum_next = NULL;
  const char *ename;
  int evalue;

  while ((ename = ctf_enum_next(ctf_dictionary, ctf_type, &enum_next, &evalue)))
    enms.push_back(enum_type_decl::enumerator(ctxt->ir_env, ename, evalue));
  if (ctf_errno(ctf_dictionary) != ECTF_NEXT_END)
    {
      fprintf(stderr, "ERROR from ctf_enum_next\n");
      return result;
    }

  const char *enum_name = ctf_type_name_raw(ctf_dictionary, ctf_type);
  result.reset(new enum_type_decl(enum_name, location(),
                                  utype, enms, enum_name));
  if (result)
    {
      add_decl_to_scope(result, tunit->get_global_scope());
      ctxt->add_type(ctf_type, result);
    }

  return result;
}

/// Add a new type declaration to the given libabigail IR corpus CORP.
///
/// @param ctxt the read context.
/// @param corp the libabigail IR corpus being constructed.
/// @param tunit the current IR translation unit.
/// @param ctf_dictionary the CTF dictionary being read.
/// @param ctf_type the CTF type ID of the source type.
///
/// Note that if @ref ctf_type can't reliably be translated to the IR
/// then it is simply ignored.
///
/// @return a shared pointer to the IR node for the type.

static type_base_sptr
process_ctf_type(read_context *ctxt,
                 corpus_sptr corp,
                 translation_unit_sptr tunit,
                 ctf_dict_t *ctf_dictionary,
                 ctf_id_t ctf_type)
{
  int type_kind = ctf_type_kind(ctf_dictionary, ctf_type);
  type_base_sptr result;

  if ((result = ctxt->lookup_type(ctf_type)))
    return result;

  switch (type_kind)
    {
    case CTF_K_INTEGER:
    case CTF_K_FLOAT:
      {
        type_decl_sptr type_decl
          = process_ctf_base_type(ctxt, corp, tunit, ctf_dictionary, ctf_type);
        result = is_type(type_decl);
        break;
      }
    case CTF_K_TYPEDEF:
      {
        typedef_decl_sptr typedef_decl
          = process_ctf_typedef(ctxt, corp, tunit, ctf_dictionary, ctf_type);
        result = is_type(typedef_decl);
        break;
      }
    case CTF_K_POINTER:
      {
        pointer_type_def_sptr pointer_type
          = process_ctf_pointer_type(ctxt, corp, tunit, ctf_dictionary, ctf_type);
        result = pointer_type;
        break;
      }
    case CTF_K_CONST:
    case CTF_K_VOLATILE:
    case CTF_K_RESTRICT:
      {
        type_base_sptr qualified_type
          = process_ctf_qualified_type(ctxt, corp, tunit, ctf_dictionary, ctf_type);
        result = qualified_type;
        break;
      }
    case CTF_K_ARRAY:
      {
        array_type_def_sptr array_type
          = process_ctf_array_type(ctxt, corp, tunit, ctf_dictionary, ctf_type);
        result = array_type;
        break;
      }
    case CTF_K_ENUM:
      {
        enum_type_decl_sptr enum_type
          = process_ctf_enum_type(ctxt, tunit, ctf_dictionary, ctf_type);
        result = enum_type;
        break;
      }
    case CTF_K_FUNCTION:
      {
        function_type_sptr function_type
          = process_ctf_function_type(ctxt, corp, tunit, ctf_dictionary, ctf_type);
        result = function_type;
        break;
      }
    case CTF_K_STRUCT:
      {
        class_decl_sptr struct_decl
          = process_ctf_struct_type(ctxt, corp, tunit, ctf_dictionary, ctf_type);
        result = is_type(struct_decl);
        break;
      }
    case CTF_K_UNION:
      {
        union_decl_sptr union_decl
          = process_ctf_union_type(ctxt, corp, tunit, ctf_dictionary, ctf_type);
        result = is_type(union_decl);
        break;
      }
    case CTF_K_UNKNOWN:
      /* Unknown types are simply ignored.  */
    default:
      break;
    }

  if (!result)
    fprintf(stderr, "NOT PROCESSED TYPE %lu\n", ctf_type);

  return result;
}

/// Given a CTF type id, lookup the corresponding libabigail IR type.
/// If the IR type hasn't been generated yet, generate it.
///
/// @param ctxt the read context.
/// @param corp the libabigail IR corpus being constructed.
/// @param tunit the current IR translation unit.
/// @param ctf_dictionary the CTF dictionary being read.
/// @param ctf_type the CTF type ID of the looked type.
///
/// Note that if @ref ctf_type can't reliably be translated to the IR
/// then a NULL shared pointer is returned.
///
/// @return a shared pointer to the IR node for the type.

static type_base_sptr
lookup_type(read_context *ctxt, corpus_sptr corp,
            translation_unit_sptr tunit, ctf_dict_t *ctf_dictionary,
            ctf_id_t ctf_type)
{
  type_base_sptr result = ctxt->lookup_type(ctf_type);

  if (!result)
    result = process_ctf_type(ctxt, corp, tunit, ctf_dictionary, ctf_type);
  return result;
}

/// Process a CTF archive and create libabigail IR for the types,
/// variables and function declarations found in the archive.  The IR
/// is added to the given corpus.
///
/// @param ctxt the read context containing the CTF archive to
/// process.
/// @param corp the IR corpus to which add the new contents.

static void
process_ctf_archive(read_context *ctxt, corpus_sptr corp)
{
  /* We only have a translation unit.  */
  translation_unit_sptr ir_translation_unit =
    std::make_shared<translation_unit>(ctxt->ir_env, "", 64);
  ir_translation_unit->set_language(translation_unit::LANG_C);
  corp->add(ir_translation_unit);

  /* Iterate over the CTF dictionaries in the archive.  */
  int ctf_err;
  ctf_dict_t *ctf_dict;
  ctf_next_t *dict_next = NULL;
  const char *archive_name;

  while ((ctf_dict = ctf_archive_next(ctxt->ctfa, &dict_next, &archive_name,
                                      0 /* skip_parent */, &ctf_err)) != NULL)
    {
      /* Iterate over the CTF types stored in this archive.  */
      ctf_id_t ctf_type;
      int type_flag;
      ctf_next_t *type_next = NULL;

      while ((ctf_type = ctf_type_next(ctf_dict, &type_next, &type_flag,
                                       1 /* want_hidden */)) != CTF_ERR)
        {
          process_ctf_type(ctxt, corp, ir_translation_unit,
                            ctf_dict, ctf_type);
        }
      if (ctf_errno(ctf_dict) != ECTF_NEXT_END)
        fprintf(stderr, "ERROR from ctf_type_next\n");

      /* Canonicalize all the types generated above.  This must be
         done "a posteriori" because the processing of types may
         require other related types to not be already
         canonicalized.  */
      ctxt->canonicalize_all_types();

      /* Iterate over the CTF variables stored in this archive.  */
      ctf_id_t ctf_var_type;
      ctf_next_t *var_next = NULL;
      const char *var_name;

      while ((ctf_var_type = ctf_variable_next(ctf_dict, &var_next, &var_name))
             != CTF_ERR)
        {
          type_base_sptr var_type = lookup_type(ctxt, corp, ir_translation_unit,
                                                ctf_dict, ctf_var_type);
          if (!var_type)
            /* Ignore variable if its type can't be sorted out.  */
            continue;

          var_decl_sptr var_declaration;
          var_declaration.reset(new var_decl(var_name,
                                             var_type,
                                             location(),
                                             var_name));

          add_decl_to_scope(var_declaration,
                             ir_translation_unit->get_global_scope());
        }
      if (ctf_errno(ctf_dict) != ECTF_NEXT_END)
        fprintf(stderr, "ERROR from ctf_variable_next\n");

      /* Iterate over the CTF functions stored in this archive.  */
      ctf_next_t *func_next = NULL;
      const char *func_name = NULL;
      ctf_id_t ctf_sym;

      while ((ctf_sym = ctf_symbol_next(ctf_dict, &func_next, &func_name,
                                        1 /* functions symbols only */) != CTF_ERR))
      {
        ctf_id_t ctf_func_type = ctf_lookup_by_name(ctf_dict, func_name);
        type_base_sptr func_type = lookup_type(ctxt, corp, ir_translation_unit,
                                               ctf_dict, ctf_func_type);
        if (!func_type)
          /* Ignore function if its type can't be sorted out.  */
          continue;

        elf_symbols func_elf_symbols = ctxt->symtab->lookup_symbol(func_name);
        if (func_elf_symbols.size() == 0
            || func_elf_symbols[0]->get_binding() == elf_symbol::LOCAL_BINDING)
          /* Ignore local functions.  */
          continue;

        function_decl_sptr func_declaration;
        func_declaration.reset(new function_decl(func_name,
                                                 func_type,
                                                 0 /* is_inline */,
                                                 location()));

        add_decl_to_scope(func_declaration,
                           ir_translation_unit->get_global_scope());
      }
      if (ctf_errno(ctf_dict) != ECTF_NEXT_END)
        fprintf(stderr, "ERROR from ctf_symbol_next\n");

      ctf_dict_close(ctf_dict);
    }
  if (ctf_err != ECTF_NEXT_END)
    fprintf(stderr, "ERROR from ctf_archive_next\n");

}

/// Open the ELF file described by the given read context.
///
/// @param ctxt the read context.
/// @return 0 if the ELF file can't be opened.
/// @return 1 otherwise.

static int
open_elf_handler(read_context *ctxt)
{
  /* libelf requires to negotiate/set the version of ELF.  */
  if (elf_version(EV_CURRENT) == EV_NONE)
    return 0;

  /* Open an ELF handler.  */
  ctxt->elf_fd = open(ctxt->filename.c_str(), O_RDONLY);
  if (ctxt->elf_fd == -1)
    return 0;

  ctxt->elf_handler = elf_begin(ctxt->elf_fd, ELF_C_READ, NULL);
  if (ctxt->elf_handler == NULL)
    {
      fprintf(stderr, "cannot open %s: %s\n",
               ctxt->filename.c_str(), elf_errmsg(elf_errno()));
      close(ctxt->elf_fd);
      return 0;
    }

  return 1;
}

/// Close the ELF file described by the given read context.
///
/// @param ctxt the read context.

static void
close_elf_handler (read_context *ctxt)
{
  /* Finish the ELF handler and close the associated file.  */
  elf_end(ctxt->elf_handler);
  close(ctxt->elf_fd);
}

/// Fill a CTF section description with the information in a given ELF
/// section.
///
/// @param elf_section the ELF section from which to get.
/// @param ctf_section the CTF section to fill with the raw data.

static void
fill_ctf_section(Elf_Scn *elf_section, ctf_sect_t *ctf_section)
{
  GElf_Shdr section_header_mem, *section_header;
  Elf_Data *section_data;

  section_header = gelf_getshdr(elf_section, &section_header_mem);
  section_data = elf_getdata(elf_section, 0);

  ABG_ASSERT (section_header != NULL);
  ABG_ASSERT (section_data != NULL);

  ctf_section->cts_name = ""; /* This is not actually used by libctf.  */
  ctf_section->cts_data = (char *) section_data->d_buf;
  ctf_section->cts_size = section_data->d_size;
  ctf_section->cts_entsize = section_header->sh_entsize;
}

/// Slurp certain information from the ELF file described by a given
/// read context and install it in a libabigail corpus.
///
/// @param ctxt the read context
/// @param corp the libabigail corpus in which to install the info.
///
/// @return 0 if there is an error.
/// @return 1 otherwise.

static int
slurp_elf_info(read_context *ctxt, corpus_sptr corp)
{
  /* Set the ELF architecture.  */
  GElf_Ehdr eh_mem;
  GElf_Ehdr *ehdr = gelf_getehdr(ctxt->elf_handler, &eh_mem);
  corp->set_architecture_name(elf_helpers::e_machine_to_string(ehdr->e_machine));

  /* Read the symtab from the ELF file and set it in the corpus.  */
  ctxt->symtab =
    symtab_reader::symtab::load(ctxt->elf_handler, ctxt->ir_env,
                                0 /* No suppressions.  */);
  corp->set_symtab(ctxt->symtab);

  /* Get the raw ELF section contents for libctf.  */
  Elf_Scn *ctf_scn = elf_helpers::find_section(ctxt->elf_handler, ".ctf", SHT_PROGBITS);
  Elf_Scn *symtab_scn = elf_helpers::find_symbol_table_section(ctxt->elf_handler);
  Elf_Scn *strtab_scn = elf_helpers::find_strtab_for_symtab_section(ctxt->elf_handler,
                                                                    symtab_scn);

  if (ctf_scn == NULL || symtab_scn == NULL || strtab_scn == NULL)
    return 0;

  fill_ctf_section(ctf_scn, &ctxt->ctf_sect);
  fill_ctf_section(symtab_scn, &ctxt->symtab_sect);
  fill_ctf_section(strtab_scn, &ctxt->strtab_sect);

  return 1;
}

/// Create and return a new read context to process CTF information
/// from a given ELF file.
///
/// @param elf_path the patch of some ELF file.
/// @param env a libabigail IR environment.

read_context_sptr
create_read_context(const std::string& elf_path,
                    ir::environment *env)
{
  read_context_sptr result(new read_context(elf_path, env));
  return result;
}

/// Read the CTF information from some source described by a given
/// read context and process it to create a libabigail IR corpus.
/// Store the corpus in the same read context.
///
/// @param ctxt the read context to use.
///
/// @param status the resulting status of the corpus read.
///
/// @return a shared pointer to the read corpus.

corpus_sptr
read_corpus(read_context *ctxt, elf_reader::status &status)
{
  corpus_sptr corp
    = std::make_shared<corpus>(ctxt->ir_env, ctxt->filename);

  /* Be optimist.  */
  status = elf_reader::STATUS_OK;

  /* Open the ELF file.  */
  if (!open_elf_handler(ctxt))
    {
      status = elf_reader::STATUS_DEBUG_INFO_NOT_FOUND;
      return corp;
    }

  /* Set some properties of the corpus first.  */
  corp->set_origin(corpus::CTF_ORIGIN);
  if (!slurp_elf_info(ctxt, corp))
    {
      status = elf_reader::STATUS_NO_SYMBOLS_FOUND;
      return corp;
    }

  /* Build the ctfa from the contents of the relevant ELF sections,
     and process the CTF archive in the read context, if any.
     Information about the types, variables, functions, etc contained
     in the archive are added to the given corpus.  */
  int errp;
  ctxt->ctfa = ctf_arc_bufopen(&ctxt->ctf_sect, &ctxt->symtab_sect,
                               &ctxt->strtab_sect, &errp);
  if (ctxt->ctfa == NULL)
    status = elf_reader::STATUS_DEBUG_INFO_NOT_FOUND;
  else
    process_ctf_archive(ctxt, corp);

  /* Cleanup and return.  */
  close_elf_handler(ctxt);
  return corp;
}

/// Read the CTF information from some source described by a given
/// read context and process it to create a libabigail IR corpus.
/// Store the corpus in the same read context.
///
/// @param ctxt the read context to use.
///
/// @param status the resulting status of the corpus read.
///
/// @return a shared pointer to the read corpus.

corpus_sptr
read_corpus(const read_context_sptr &ctxt, elf_reader::status &status)
{return read_corpus(ctxt.get(), status);}

} // End of namespace ctf_reader
} // End of namespace abigail
