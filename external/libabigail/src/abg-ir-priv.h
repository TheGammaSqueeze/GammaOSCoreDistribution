// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- Mode: C++ -*-
//
// Copyright (C) 2016-2020 Red Hat, Inc.
//
// Author: Dodji Seketeli

/// @file
///
/// This contains the private implementation of the suppression engine
/// of libabigail.

#ifndef __ABG_IR_PRIV_H__
#define __ABG_IR_PRIV_H__

#include <string>

#include "abg-ir.h"
#include "abg-corpus.h"

namespace abigail
{

namespace ir
{

using std::string;

/// The internal representation of an integral type.
///
/// This is a "utility type" used internally to canonicalize the name
/// of fundamental integral types, so that "unsignd long" and "long
/// unsined int" end-up having the same name.
class integral_type
{
public:
  /// The possible base types of integral types.  We might have
  /// forgotten many of these, so do not hesitate to add new ones.
  ///
  /// If you do add new ones, please also consider updating functions
  /// parse_base_integral_type and integral_type::to_string.
  enum base_type
  {
    /// The "int" base type.
    INT_BASE_TYPE,
    /// The "char" base type.
    CHAR_BASE_TYPE,
    /// The "bool" base type in C++ or "_Bool" in C11.
    BOOL_BASE_TYPE,
    /// The "double" base type.
    DOUBLE_BASE_TYPE,
    /// The "float" base type.
    FLOAT_BASE_TYPE,
    /// The "char16_t base type.
    CHAR16_T_BASE_TYPE,
    /// The "char32_t" base type.
    CHAR32_T_BASE_TYPE,
    /// The "wchar_t" base type.
    WCHAR_T_BASE_TYPE
  };

  /// The modifiers of the base types above.  Several modifiers can be
  /// combined for a given base type.  The presence of modifiers is
  /// usually modelled by a bitmap of modifiers.
  ///
  /// If you add a new modifier, please consider updating functions
  /// parse_integral_type_modifier and integral_type::to_string.
  enum modifiers_type
  {
    NO_MODIFIER = 0,
    /// The "signed" modifier.
    SIGNED_MODIFIER = 1,
    /// The "unsigned" modier.
    UNSIGNED_MODIFIER = 1 << 1,
    /// The "short" modifier.
    SHORT_MODIFIER = 1 << 2,
    /// The "long" modifier.
    LONG_MODIFIER = 1 << 3,
    /// The "long long" modifier.
    LONG_LONG_MODIFIER = 1 << 4
  };

private:
  base_type	base_;
  modifiers_type modifiers_;

public:

  integral_type();
  integral_type(const string& name);
  integral_type(base_type, modifiers_type);

  base_type
  get_base_type() const;

  modifiers_type
  get_modifiers() const;

  bool
  operator==(const integral_type&) const;

  string
  to_string() const;

  operator string() const;
}; // end class integral_type

integral_type::modifiers_type
operator|(integral_type::modifiers_type l, integral_type::modifiers_type r);

integral_type::modifiers_type
operator&(integral_type::modifiers_type l, integral_type::modifiers_type r);

integral_type::modifiers_type&
operator|=(integral_type::modifiers_type& l, integral_type::modifiers_type r);

bool
parse_integral_type(const string& type_name,
		    integral_type& type);

/// Private type to hold private members of @ref translation_unit
struct translation_unit::priv
{
  const environment*				env_;
  corpus*					corp;
  bool						is_constructed_;
  char						address_size_;
  language					language_;
  std::string					path_;
  std::string					comp_dir_path_;
  std::string					abs_path_;
  location_manager				loc_mgr_;
  mutable global_scope_sptr			global_scope_;
  mutable vector<type_base_sptr>		synthesized_types_;
  vector<function_type_sptr>			live_fn_types_;
  type_maps					types_;


  priv(const environment* env)
    : env_(env),
      corp(),
      is_constructed_(),
      address_size_(),
      language_(LANG_UNKNOWN)
  {}

  ~priv()
  {}

  type_maps&
  get_types()
  {return types_;}
}; // end translation_unit::priv

// <type_base definitions>

/// Definition of the private data of @ref type_base.
struct type_base::priv
{
  size_t		size_in_bits;
  size_t		alignment_in_bits;
  type_base_wptr	canonical_type;
  // The data member below holds the canonical type that is managed by
  // the smart pointer referenced by the canonical_type data member
  // above.  We are storing this underlying (naked) pointer here, so
  // that users can access it *fast*.  Otherwise, accessing
  // canonical_type above implies creating a shared_ptr, and that has
  // been measured to be slow for some performance hot spots.
  type_base*		naked_canonical_type;
  // Computing the representation of a type again and again can be
  // costly.  So we cache the internal and non-internal type
  // representation strings here.
  interned_string	internal_cached_repr_;
  interned_string	cached_repr_;
  // The next two data members are used while comparing types during
  // canonicalization.  They are useful for the "canonical type
  // propagation" (aka on-the-fly-canonicalization) optimization
  // implementation.

  // The set of canonical recursive types this type depends on.
  unordered_set<uintptr_t> depends_on_recursive_type_;
  bool canonical_type_propagated_;

  priv()
    : size_in_bits(),
      alignment_in_bits(),
      naked_canonical_type(),
      canonical_type_propagated_(false)
  {}

  priv(size_t s,
       size_t a,
       type_base_sptr c = type_base_sptr())
    : size_in_bits(s),
      alignment_in_bits(a),
      canonical_type(c),
      naked_canonical_type(c.get()),
      canonical_type_propagated_(false)
  {}

  /// Test if the current type depends on recursive type comparison.
  ///
  /// A recursive type T is a type T which has a sub-type that is T
  /// (recursively) itself.
  ///
  /// So this function tests if the current type has a recursive
  /// sub-type or is a recursive type itself.
  ///
  /// @return true if the current type depends on a recursive type.
  bool
  depends_on_recursive_type() const
  {return !depends_on_recursive_type_.empty();}

  /// Test if the current type depends on a given recursive type.
  ///
  /// A recursive type T is a type T which has a sub-type that is T
  /// (recursively) itself.
  ///
  /// So this function tests if the current type depends on a given
  /// recursive type.
  ///
  /// @param dependant the type we want to test if the current type
  /// depends on.
  ///
  /// @return true if the current type depends on the recursive type
  /// @dependant.
  bool
  depends_on_recursive_type(const type_base* dependant) const
  {
    return
      (depends_on_recursive_type_.find(reinterpret_cast<uintptr_t>(dependant))
       != depends_on_recursive_type_.end());
  }

  /// Set the flag that tells if the current type depends on a given
  /// recursive type.
  ///
  /// A recursive type T is a type T which has asub-type that is T
  /// (recursively) itself.
  ///
  /// So this function tests if the current type depends on a
  /// recursive type.
  ///
  /// @param t the recursive type that current type depends on.
  void
  set_depends_on_recursive_type(const type_base * t)
  {depends_on_recursive_type_.insert(reinterpret_cast<uintptr_t>(t));}

  /// Unset the flag that tells if the current type depends on a given
  /// recursive type.
  ///
  /// A recursive type T is a type T which has asub-type that is T
  /// (recursively) itself.
  ///
  /// So this function flags the current type as not being dependant
  /// on a given recursive type.
  ///
  ///
  /// @param t the recursive type to consider.
  void
  set_does_not_depend_on_recursive_type(const type_base *t)
  {depends_on_recursive_type_.erase(reinterpret_cast<uintptr_t>(t));}

  /// Flag the current type as not being dependant on any recursive type.
  void
  set_does_not_depend_on_recursive_type()
  {depends_on_recursive_type_.clear();}

  /// Test if the type carries a canonical type that is the result of
  /// maybe_propagate_canonical_type(), aka, "canonical type
  /// propagation optimization".
  ///
  /// @return true iff the current type carries a canonical type that
  /// is the result of canonical type propagation.
  bool
  canonical_type_propagated()
  {return canonical_type_propagated_;}

  /// Set the flag that says if the type carries a canonical type that
  /// is the result of maybe_propagate_canonical_type(), aka,
  /// "canonical type propagation optimization".
  ///
  /// @param f true iff the current type carries a canonical type that
  /// is the result of canonical type propagation.
  void
  set_canonical_type_propagated(bool f)
  {canonical_type_propagated_ = f;}

  /// If the current canonical type was set as the result of the
  /// "canonical type propagation optimization", then clear it.
  void
  clear_propagated_canonical_type()
  {
    if (canonical_type_propagated_)
      {
	canonical_type.reset();
	naked_canonical_type = nullptr;
	set_canonical_type_propagated(false);
      }
  }
}; // end struct type_base::priv

// <environment definitions>
/// The private data of the @ref environment type.
struct environment::priv
{
  config				config_;
  canonical_types_map_type		canonical_types_;
  mutable vector<type_base_sptr>	sorted_canonical_types_;
  type_base_sptr			void_type_;
  type_base_sptr			variadic_marker_type_;
  unordered_set<const class_or_union*>	classes_being_compared_;
  unordered_set<const function_type*>	fn_types_being_compared_;
  vector<type_base_sptr>		extra_live_types_;
  interned_string_pool			string_pool_;
  // The two vectors below represent the stack of left and right
  // operands of the current type comparison operation that is
  // happening during type canonicalization.
  //
  // Basically, that stack of operand looks like below.
  //
  // First, suppose we have a type T_L that has two sub-types as this:
  //
  //  T_L
  //   |
  //   +-- L_OP0
  //   |
  //   +-- L_OP1
  //
  // Now suppose that we have another type T_R that has two sub-types
  // as this:
  //
  //  T_R
  //   |
  //   +-- R_OP0
  //   |
  //   +-- R_OP1
  //
  //   Now suppose that we compare T_L against T_R.  We are going to
  //   have a stack of pair of types. Each pair of types represents
  //   two (sub) types being compared against each other.
  //
  //   On the stack, we will thus first have the pair (T_L, T_R)
  //   being compared.  Then, we will have the pair (L_OP0, R_OP0)
  //   being compared, and then the pair (L_OP1, R_OP1) being
  //   compared.  Like this:
  //
  // | T_L | L_OP0 | L_OP1 | <-- this goes into left_type_comp_operands_;
  //  -------- -------------
  // | T_R | R_OP0 | R_OP1 | <-- this goes into right_type_comp_operands_;
  //
  // This "stack of operands of the current type comparison, during
  // type canonicalization" is used in the context of the @ref
  // OnTheFlyCanonicalization optimization.  It's used to detect if a
  // sub-type of the type being canonicalized depends on a recursive
  // type.
  vector<const type_base*>		left_type_comp_operands_;
  vector<const type_base*>		right_type_comp_operands_;
  // Vector of types that protentially received propagated canonical types.
  // If the canonical type propagation is confirmed, the potential
  // canonical types must be promoted as canonical types. Otherwise if
  // the canonical type propagation is cancelled, the canonical types
  // must be cleared.
  pointer_set		types_with_non_confirmed_propagated_ct_;
#ifdef WITH_DEBUG_SELF_COMPARISON
  // This is used for debugging purposes.
  // When abidw is used with the option --debug-abidiff, some
  // libabigail internals need to get a hold on the initial binary
  // input of abidw, as well as as the abixml file that represents the
  // ABI of that binary.
  //
  // So this one is the corpus for the input binary.
  corpus_wptr				first_self_comparison_corpus_;
  // This one is the corpus for the ABIXML file representing the
  // serialization of the input binary.
  corpus_wptr				second_self_comparison_corpus_;
  // This is also used for debugging purposes, when using
  //   'abidw --debug-abidiff <binary>'.  It holds the set of mapping of
  // an abixml (canonical) type and its type-id.
  unordered_map<string, uintptr_t>	type_id_canonical_type_map_;
  // Likewise.  It holds a map that associates the pointer to a type
  // read from abixml and the type-id string it corresponds to.
  unordered_map<uintptr_t, string>	pointer_type_id_map_;
#endif
  bool					canonicalization_is_done_;
  bool					do_on_the_fly_canonicalization_;
  bool					decl_only_class_equals_definition_;
  bool					use_enum_binary_only_equality_;
#ifdef WITH_DEBUG_SELF_COMPARISON
  bool					self_comparison_debug_on_;
#endif
#ifdef WITH_DEBUG_TYPE_CANONICALIZATION
  // This controls whether to use canonical type comparison during
  // type comparison or not.  This is only used for debugging, when we
  // want to ensure that comparing types using canonical or structural
  // comparison yields the same result.
  bool					use_canonical_type_comparison_;
  // Whether we are debugging type canonicalization or not.  When
  // debugging type canonicalization, a type is compared to its
  // potential canonical type twice: The first time with canonical
  // comparison activated, and the second time with structural
  // comparison activated.  The two comparison should yield the same
  // result, otherwise, canonicalization is "broken" for that
  // particular type.
  bool					debug_type_canonicalization_;
#endif

  priv()
    : canonicalization_is_done_(),
      do_on_the_fly_canonicalization_(true),
      decl_only_class_equals_definition_(false),
      use_enum_binary_only_equality_(false)
#ifdef WITH_DEBUG_SELF_COMPARISON
    ,
      self_comparison_debug_on_(false)
#endif
#ifdef WITH_DEBUG_TYPE_CANONICALIZATION
    ,
      use_canonical_type_comparison_(true),
      debug_type_canonicalization_(false)
#endif
  {}

  /// Push a pair of operands on the stack of operands of the current
  /// type comparison, during type canonicalization.
  ///
  /// For more information on this, please look at the description of
  /// the right_type_comp_operands_ data member.
  ///
  /// @param left the left-hand-side comparison operand to push.
  ///
  /// @param right the right-hand-side comparison operand to push.
  void
  push_composite_type_comparison_operands(const type_base* left,
					  const type_base* right)
  {
    ABG_ASSERT(left && right);

    left_type_comp_operands_.push_back(left);
    right_type_comp_operands_.push_back(right);
  }

  /// Pop a pair of operands from the stack of operands to the current
  /// type comparison.
  ///
  /// For more information on this, please look at the description of
  /// the right_type_comp_operands_ data member.
  ///
  /// @param left the left-hand-side comparison operand we expect to
  /// pop from the top of the stack.  If this doesn't match the
  /// operand found on the top of the stack, the function aborts.
  ///
  /// @param right the right-hand-side comparison operand we expect to
  /// pop from the bottom of the stack. If this doesn't match the
  /// operand found on the top of the stack, the function aborts.
  void
  pop_composite_type_comparison_operands(const type_base* left,
					 const type_base* right)
  {
    const type_base *t = left_type_comp_operands_.back();
    ABG_ASSERT(t == left);
    t = right_type_comp_operands_.back();
    ABG_ASSERT(t == right);

    left_type_comp_operands_.pop_back();
    right_type_comp_operands_.pop_back();
  }

  /// Mark all the types that comes after a certain one as NOT being
  /// eligible for the canonical type propagation optimization.
  ///
  /// @param type the type that represents the "marker type".  All
  /// types after this one will be marked as being NON-eligible to
  /// the canonical type propagation optimization.
  ///
  /// @param types the set of types to consider.  In that vector, all
  /// types that come after @p type are going to be marked as being
  /// non-eligible to the canonical type propagation optimization.
  ///
  /// @return true iff the operation was successful.
  bool
  mark_dependant_types(const type_base* type,
		       vector<const type_base*>& types)
  {
    bool found = false;
    for (auto t : types)
      {
	if (!found
	    && (reinterpret_cast<uintptr_t>(t)
		== reinterpret_cast<uintptr_t>(type)))
	  {
	    found = true;
	    continue;
	  }
	else if (found)
	  t->priv_->set_depends_on_recursive_type(type);
      }
    return found;
  }

  /// In the stack of the current types being compared (as part of
  /// type canonicalization), mark all the types that comes after a
  /// certain one as NOT being eligible to the canonical type
  /// propagation optimization.
  ///
  /// For a starter, please read about the @ref
  /// OnTheFlyCanonicalization, aka, "canonical type propagation
  /// optimization".
  ///
  /// To implement that optimization, we need, among other things to
  /// maintain stack of the types (and their sub-types) being
  /// currently compared as part of type canonicalization.
  ///
  /// Note that we only consider the type that is the right-hand-side
  /// operand of the comparison because it's that one that is being
  /// canonicalized and thus, that is not yet canonicalized.
  ///
  /// The reason why a type is deemed NON-eligible to the canonical
  /// type propagation optimization is that it "depends" on
  /// recursively present type.  Let me explain.
  ///
  /// Suppose we have a type T that has sub-types named ST0 and ST1.
  /// Suppose ST1 itself has a sub-type that is T itself.  In this
  /// case, we say that T is a recursive type, because it has T
  /// (itself) as one of its sub-types:
  ///
  ///   T
  ///   +-- ST0
  ///   |
  ///   +-- ST1
  ///        +
  ///        |
  ///        +-- T
  ///
  /// ST1 is said to "depend" on T because it has T as a sub-type.
  /// But because T is recursive, then ST1 is said to depend on a
  /// recursive type.  Notice however that ST0 does not depend on any
  /// recursive type.
  ///
  /// When we are at the point of comparing the sub-type T of ST1
  /// against its counterpart, the stack of the right-hand-side
  /// operands of the type canonicalization is going to look like
  /// this:
  ///
  ///    | T | ST1 |
  ///
  /// We don't add the type T to the stack as we detect that T was
  /// already in there (recursive cycle).
  ///
  /// So, this function will basically mark ST1 as being NON-eligible
  /// to being the target of canonical type propagation.
  ///
  /// @param right the right-hand-side operand of the type comparison.
  ///
  /// @return true iff the operation was successful.
  bool
  mark_dependant_types_compared_until(const type_base* right)
  {
    bool result = false;

    result |=
      mark_dependant_types(right,
			   right_type_comp_operands_);
    return result;
  }

  /// Propagate the canonical type of a type to another one.
  ///
  /// @param src the type to propagate the canonical type from.
  ///
  /// @param dest the type to propagate the canonical type of @p src
  /// to.
  ///
  /// @return bool iff the canonical was propagated.
  bool
  propagate_ct(const type_base& src, const type_base& dest)
  {
    type_base_sptr canonical = src.get_canonical_type();
    ABG_ASSERT(canonical);
    dest.priv_->canonical_type = canonical;
    dest.priv_->naked_canonical_type = canonical.get();
    dest.priv_->set_canonical_type_propagated(true);
    return true;
  }

  /// Mark a set of types that have been the target of canonical type
  /// propagation and that depend on a recursive type as being
  /// permanently canonicalized.
  ///
  /// To understand the sentence above, please read the description of
  /// type canonicalization and especially about the "canonical type
  /// propagation optimization" at @ref OnTheFlyCanonicalization, in
  /// the src/abg-ir.cc file.
  void
  confirm_ct_propagation(const type_base* dependant_type)
  {
    pointer_set to_remove;
    for (auto i : types_with_non_confirmed_propagated_ct_)
      {
	type_base *t = reinterpret_cast<type_base*>(i);
	ABG_ASSERT(t->priv_->depends_on_recursive_type());
	t->priv_->set_does_not_depend_on_recursive_type(dependant_type);
	if (!t->priv_->depends_on_recursive_type())
	  to_remove.insert(i);
      }

    for (auto i : to_remove)
      types_with_non_confirmed_propagated_ct_.erase(i);
  }

  /// Collect the types that depends on a given "target" type.
  ///
  /// Walk a set of types and if they depend directly or indirectly on
  /// a "target" type, then collect them into a set.
  ///
  /// @param target the target type to consider.
  ///
  /// @param types the types to walk to detect those who depend on @p
  /// target.
  ///
  /// @return true iff one or more type from @p types is found to
  /// depend on @p target.
  bool
  collect_types_that_depends_on(const type_base *target,
				const pointer_set& types,
				pointer_set& collected)
  {
    bool result = false;
    for (const auto i : types)
      {
	// First avoid infinite loop if we've already collected the
	// current type.
	if (collected.find(i) != collected.end())
	  continue;

	type_base *t = reinterpret_cast<type_base*>(i);
	if (t->priv_->depends_on_recursive_type(target))
	  {
	    collected.insert(i);
	    collect_types_that_depends_on(t, types, collected);
	    result = true;
	  }
      }
    return result;
  }

  /// Reset the canonical type (set it nullptr) of a set of types that
  /// have been the target of canonical type propagation and that
  /// depend on a given recursive type.
  ///
  /// Once the canonical type of a type in that set is reset, the type
  /// is marked as non being dependant on a recursive type anymore.
  ///
  /// To understand the sentences above, please read the description
  /// of type canonicalization and especially about the "canonical
  /// type propagation optimization" at @ref OnTheFlyCanonicalization,
  /// in the src/abg-ir.cc file.
  ///
  /// @param target if a type which has been subject to the canonical
  /// type propagation optimizationdepends on a this target type, then
  /// cancel its canonical type.
  void
  cancel_ct_propagation(const type_base* target)
  {
    pointer_set to_remove;
    collect_types_that_depends_on(target,
				  types_with_non_confirmed_propagated_ct_,
				  to_remove);

    for (auto i : to_remove)
      {
	type_base *t = reinterpret_cast<type_base*>(i);
	ABG_ASSERT(t->priv_->depends_on_recursive_type());
	type_base_sptr canonical = t->priv_->canonical_type.lock();
	if (canonical)
	  {
	    t->priv_->clear_propagated_canonical_type();
	    t->priv_->set_does_not_depend_on_recursive_type();
	  }
      }

    for (auto i : to_remove)
      types_with_non_confirmed_propagated_ct_.erase(i);
  }

  /// Remove a given type from the set of types that have been
  /// non-confirmed subjects of the canonical type propagation
  /// optimization.
  ///
  /// @param dependant the dependant type to consider.
  void
  remove_from_types_with_non_confirmed_propagated_ct(const type_base* dependant)
  {
    uintptr_t i = reinterpret_cast<uintptr_t>(dependant);
    types_with_non_confirmed_propagated_ct_.erase(i);
  }

#ifdef WITH_DEBUG_SELF_COMPARISON
  /// When debugging self comparison, verify that a type T
  /// de-serialized from abixml has the same canonical type as the
  /// initial type built from DWARF that was serialized into T in the
  /// first place.
  ///
  /// @param t deserialized type (from abixml) to consider.
  ///
  /// @param c the canonical type @p t should have.
  ///
  /// @return true iff @p c is the canonical type that @p t should
  /// have.
  bool
  check_canonical_type_from_abixml_during_self_comp(const type_base* t,
						    const type_base* c)
  {
    if (!t || !t->get_corpus() || !c)
      return false;

    if (!(t->get_corpus()->get_origin() == ir::corpus::NATIVE_XML_ORIGIN))
      return false;

    // Get the abixml type-id that this type was constructed from.
    string type_id;
    {
      unordered_map<uintptr_t, string>::const_iterator it =
	pointer_type_id_map_.find(reinterpret_cast<uintptr_t>(t));
      if (it == pointer_type_id_map_.end())
	return false;
      type_id = it->second;
    }

    // Get the canonical type the original in-memory type (constructed
    // from DWARF) had when it was serialized into abixml in the first place.
    type_base *original_canonical_type = nullptr;
    if (!type_id.empty())
      {
	unordered_map<string, uintptr_t>::const_iterator it =
	  type_id_canonical_type_map_.find(type_id);
	if (it == type_id_canonical_type_map_.end())
	  return false;
	original_canonical_type = reinterpret_cast<type_base*>(it->second);
      }

    // Now perform the real check.
    //
    // We want to ensure that the canonical type 'c' of 't' is the
    // same as the canonical type of initial in-memory type (built
    // from DWARF) that was serialized into 't' (in abixml) in the
    // first place.
    if (original_canonical_type == c)
      return true;

    return false;
  }

  /// When debugging self comparison, verify that a type T
  /// de-serialized from abixml has the same canonical type as the
  /// initial type built from DWARF that was serialized into T in the
  /// first place.
  ///
  /// @param t deserialized type (from abixml) to consider.
  ///
  /// @param c the canonical type @p t should have.
  ///
  /// @return true iff @p c is the canonical type that @p t should
  /// have.
  bool
  check_canonical_type_from_abixml_during_self_comp(const type_base_sptr& t,
						    const type_base_sptr& c)
  {
    return check_canonical_type_from_abixml_during_self_comp(t.get(), c.get());
  }
#endif
};// end struct environment::priv

// <class_or_union::priv definitions>
struct class_or_union::priv
{
  typedef_decl_wptr		naming_typedef_;
  member_types			member_types_;
  data_members			data_members_;
  data_members			non_static_data_members_;
  member_functions		member_functions_;
  // A map that associates a linkage name to a member function.
  string_mem_fn_sptr_map_type	mem_fns_map_;
  // A map that associates function signature strings to member
  // function.
  string_mem_fn_ptr_map_type	signature_2_mem_fn_map_;
  member_function_templates	member_function_templates_;
  member_class_templates	member_class_templates_;

  priv()
  {}

  priv(class_or_union::member_types& mbr_types,
       class_or_union::data_members& data_mbrs,
       class_or_union::member_functions& mbr_fns)
    : member_types_(mbr_types),
      data_members_(data_mbrs),
      member_functions_(mbr_fns)
  {
    for (data_members::const_iterator i = data_members_.begin();
	 i != data_members_.end();
	 ++i)
      if (!get_member_is_static(*i))
	non_static_data_members_.push_back(*i);
  }

  /// Mark a class or union or union as being currently compared using
  /// the class_or_union== operator.
  ///
  /// Note that is marking business is to avoid infinite loop when
  /// comparing a class or union or union. If via the comparison of a
  /// data member or a member function a recursive re-comparison of
  /// the class or union is attempted, the marking business help to
  /// detect that infinite loop possibility and avoid it.
  ///
  /// @param klass the class or union or union to mark as being
  /// currently compared.
  void
  mark_as_being_compared(const class_or_union& klass) const
  {
    const environment* env = klass.get_environment();
    ABG_ASSERT(env);
    env->priv_->classes_being_compared_.insert(&klass);
  }

  /// Mark a class or union as being currently compared using the
  /// class_or_union== operator.
  ///
  /// Note that is marking business is to avoid infinite loop when
  /// comparing a class or union. If via the comparison of a data
  /// member or a member function a recursive re-comparison of the
  /// class or union is attempted, the marking business help to detect
  /// that infinite loop possibility and avoid it.
  ///
  /// @param klass the class or union to mark as being currently
  /// compared.
  void
  mark_as_being_compared(const class_or_union* klass) const
  {mark_as_being_compared(*klass);}

  /// Mark a class or union as being currently compared using the
  /// class_or_union== operator.
  ///
  /// Note that is marking business is to avoid infinite loop when
  /// comparing a class or union. If via the comparison of a data
  /// member or a member function a recursive re-comparison of the
  /// class or union is attempted, the marking business help to detect
  /// that infinite loop possibility and avoid it.
  ///
  /// @param klass the class or union to mark as being currently
  /// compared.
  void
  mark_as_being_compared(const class_or_union_sptr& klass) const
  {mark_as_being_compared(*klass);}

  /// If the instance of class_or_union has been previously marked as
  /// being compared -- via an invocation of mark_as_being_compared()
  /// this method unmarks it.  Otherwise is has no effect.
  ///
  /// This method is not thread safe because it uses the static data
  /// member classes_being_compared_.  If you wish to use it in a
  /// multi-threaded environment you should probably protect the
  /// access to that static data member with a mutex or somesuch.
  ///
  /// @param klass the instance of class_or_union to unmark.
  void
  unmark_as_being_compared(const class_or_union& klass) const
  {
    const environment* env = klass.get_environment();
    ABG_ASSERT(env);
    env->priv_->classes_being_compared_.erase(&klass);
  }

  /// If the instance of class_or_union has been previously marked as
  /// being compared -- via an invocation of mark_as_being_compared()
  /// this method unmarks it.  Otherwise is has no effect.
  ///
  /// @param klass the instance of class_or_union to unmark.
  void
  unmark_as_being_compared(const class_or_union* klass) const
  {
    if (klass)
      return unmark_as_being_compared(*klass);
  }

  /// Test if a given instance of class_or_union is being currently
  /// compared.
  ///
  ///@param klass the class or union to test.
  ///
  /// @return true if @p klass is being compared, false otherwise.
  bool
  comparison_started(const class_or_union& klass) const
  {
    const environment* env = klass.get_environment();
    ABG_ASSERT(env);
    return env->priv_->classes_being_compared_.count(&klass);
  }

  /// Test if a given instance of class_or_union is being currently
  /// compared.
  ///
  ///@param klass the class or union to test.
  ///
  /// @return true if @p klass is being compared, false otherwise.
  bool
  comparison_started(const class_or_union* klass) const
  {
    if (klass)
      return comparison_started(*klass);
    return false;
  }
}; // end struct class_or_union::priv

} // end namespace ir

} // end namespace abigail

#endif // __ABG_IR_PRIV_H__
