// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- Mode: C++ -*-
//
// Copyright (C) 2021 Google, Inc.
//
// Author: Giuliano Procida

/// @file
///
/// This file contains ABI XML manipulation routines and a main driver.
///
/// The libxml Tree API is used. The XPath API is not used as it proved
/// to be many times slower than direct traversal but only slightly more
/// convenient.

#include <fcntl.h>
#include <unistd.h>

#include <algorithm>
#include <array>
#include <cassert>
#include <cctype>
#include <cstring>
#include <fstream>
#include <functional>
#include <ios>
#include <iostream>
#include <map>
#include <optional>
#include <set>
#include <sstream>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>

#include <libxml/globals.h>
#include <libxml/parser.h>
#include <libxml/tree.h>

/// Convenience typedef referring to a namespace scope.
using namespace_scope = std::vector<std::string>;

/// Convenience typedef referring to a set of symbols.
using symbol_set = std::unordered_set<std::string>;

/// Level of location information to preserve.
enum struct LocationInfo { COLUMN, LINE, FILE, NONE };

static const std::map<std::string, LocationInfo> LOCATION_INFO_NAME = {
  {"column", LocationInfo::COLUMN},
  {"line", LocationInfo::LINE},
  {"file", LocationInfo::FILE},
  {"none", LocationInfo::NONE},
};

static const std::map<std::string, std::string> NAMED_TYPES = {
  {"enum-decl", "__anonymous_enum__"},
  {"class-decl", "__anonymous_struct__"},
  {"union-decl", "__anonymous_union__"},
};


/// Cast a C string to a libxml string.
///
/// @param str the C string (pointer)
///
/// @return the same thing, as a type compatible with the libxml API
static const xmlChar*
to_libxml(const char* str)
{
  return reinterpret_cast<const xmlChar*>(str);
}

/// Cast a libxml string to C string.
///
/// @param str the libxml string (pointer)
///
/// @return the same thing, as a type compatible with the C library API
static const char*
from_libxml(const xmlChar* str)
{
  return reinterpret_cast<const char*>(str);
}

/// Remove a node from its document and free its storage.
///
/// @param node the node to remove
static void
remove_node(xmlNodePtr node)
{
  xmlUnlinkNode(node);
  xmlFreeNode(node);
}

/// Remove an XML element and any immediately preceding comment.
///
/// @param node the element to remove
static void
remove_element(xmlNodePtr node)
{
  xmlNodePtr previous_node = node->prev;
  if (previous_node && previous_node->type == XML_COMMENT_NODE)
    remove_node(previous_node);
  remove_node(node);
}

/// Move a node to an element.
///
/// @param node the node to move
///
/// @param destination the destination element
static void
move_node(xmlNodePtr node, xmlNodePtr destination)
{
  xmlUnlinkNode(node);
  xmlAddChild(destination, node);
}

/// Move an XML element and any immediately preceding comment to another
/// element.
///
/// @param node the element to remove
///
/// @param destination the destination element
static void
move_element(xmlNodePtr node, xmlNodePtr destination)
{
  xmlNodePtr previous_node = node->prev;
  if (previous_node && previous_node->type == XML_COMMENT_NODE)
    move_node(previous_node, destination);
  move_node(node, destination);
}

/// Get child nodes of given node.
///
/// @param node the node whose children to fetch
///
/// @return a vector of child nodes
static std::vector<xmlNodePtr>
get_children(xmlNodePtr node)
{
  std::vector<xmlNodePtr> result;
  for (xmlNodePtr child = node->children; child; child = child->next)
    result.push_back(child);
  return result;
}

/// Fetch an attribute from a node.
///
/// @param node the node
///
/// @param name the attribute name
///
/// @return the attribute value, if present
static std::optional<std::string>
get_attribute(xmlNodePtr node, const char* name)
{
  std::optional<std::string> result;
  xmlChar* attribute = xmlGetProp(node, to_libxml(name));
  if (attribute)
    {
      result = from_libxml(attribute);
      xmlFree(attribute);
    }
  return result;
}

/// Set an attribute value.
///
/// @param node the node
///
/// @param name the attribute name
///
/// @param value the attribute value
static void
set_attribute(xmlNodePtr node, const char* name,
              const std::string& value)
{
  xmlSetProp(node, to_libxml(name), to_libxml(value.c_str()));
}

/// Unset an attribute value.
///
/// @param node the node
///
/// @param name the attribute name
static void
unset_attribute(xmlNodePtr node, const char* name)
{
  xmlUnsetProp(node, to_libxml(name));
}

/// Remove text nodes, recursively.
///
/// This simplifies subsequent analysis and manipulation. Removing and
/// moving elements will destroy formatting anyway. The only remaining
/// node types should be elements and comments.
///
/// @param node the node to process
static void
strip_text(xmlNodePtr node)
{
  if (node->type == XML_TEXT_NODE)
    remove_node(node);
  else if (node->type == XML_ELEMENT_NODE)
    for (xmlNodePtr child : get_children(node))
      strip_text(child);
}

/// Add text before / after a node.
///
/// @param node the node
///
/// @param after whether the next should go after
///
/// @param text the text
static void
add_text(xmlNodePtr node, bool after, const std::string& text)
{
  xmlNodePtr text_node = xmlNewTextLen(to_libxml(text.data()), text.size());
  if (after)
    xmlAddNextSibling(node, text_node);
  else
    xmlAddPrevSibling(node, text_node);
}

/// Format an XML element by adding internal indentation and newlines.
///
/// This makes the XML readable.
///
/// @param indentation what to add to the line indentation prefix
///
/// @param prefix the current line indentation prefix
///
/// @param node the node to format
static void
format_xml(const std::string& indentation, std::string prefix, xmlNodePtr node)
{
  std::vector<xmlNodePtr> children = get_children(node);
  if (children.empty())
    return;

  // The ordering of operations here is incidental. The outcomes we want
  // are: 1. an extra newline after the opening tag and indentation of
  // the closing tag to match, and 2. indentation and newline for each
  // child.
  add_text(children[0], false, "\n");
  add_text(children[children.size() - 1], true, prefix);
  prefix += indentation;
  for (xmlNodePtr child : children)
    {
      add_text(child, false, prefix);
      format_xml(indentation, prefix, child);
      add_text(child, true, "\n");
    }
}

/// Rewrite attributes using single quotes.
///
/// libxml uses double quotes but libabigail uses single quotes.
///
/// Note that libabigail does not emit attributes *containing* single
/// quotes and if it did it would escape them as &quot; which libxml
/// would in turn preserve. However, the code here will handle all forms
/// of quotes, conservatively.
///
/// Annotation comments can contain single quote characters so just
/// checking for any single quotes at all is insufficiently precise.
///
/// @param start a pointer to the start of the XML text
///
/// @param limit a pointer to just past the end of the XML text
static void
adjust_quotes(xmlChar* start, xmlChar* limit)
{
  const std::string open{"<!--"};
  const std::string close{"-->"};
  while (start < limit)
    {
      // Look for a '<'
      start = std::find(start, limit, '<');
      if (start == limit)
        break;
      if (start + open.size() < limit
          && std::equal(open.begin(), open.end(), start))
        {
          // Have a comment, skip to the end.
          start += open.size();
          xmlChar* end = std::search(start, limit, close.begin(), close.end());
          if (end == limit)
            break;
          start = end + close.size();
        }
      else
        {
          // Have some tag, search for the end.
          start += 1;
          xmlChar* end = std::find(start, limit, '>');
          if (end == limit)
            break;
          // In general, inside a tag we could find either ' or " being
          // used to quote attributes and the other quote character
          // being used as part of the attribute data. However, libxml's
          // xmlDocDump* functions use " to quote attributes and it's
          // safe to substitute this quote character with ' so long as '
          // does not appear within the attribute data.
          if (std::find(start, end, '\'') == end)
            for (xmlChar* c = start; c < end; ++c)
              if (*c == '"')
                *c = '\'';
          start = end + 1;
        }
    }
}

static const std::set<std::string> DROP_IF_EMPTY = {
  "elf-variable-symbols",
  "elf-function-symbols",
  "namespace-decl",
  "abi-instr",
  "abi-corpus",
  "abi-corpus-group",
};

/// Drop empty elements, if safe to do so, recursively.
///
/// @param node the element to process
static void
drop_empty(xmlNodePtr node)
{
  if (node->type != XML_ELEMENT_NODE)
    return;
  for (xmlNodePtr child : get_children(node))
    drop_empty(child);
  // Do not drop the root element, even if empty.
  if (node->parent->type == XML_DOCUMENT_NODE)
    return;
  if (!node->children && DROP_IF_EMPTY.count(from_libxml(node->name)))
    remove_element(node);
}

/// Get ELF symbol id.
///
/// This is not an explicit attribute. It takes one of these forms:
///
/// * name (if symbol is not versioned)
/// * name@version (if symbol is versioned but not the default version)
/// * name@@version (if symbol is versioned and the default version)
///
/// @param node the elf-symbol element
///
/// @return the ELF symbol id
static std::string
get_elf_symbol_id(xmlNodePtr node)
{
  const auto name = get_attribute(node, "name");
  assert(name);
  std::string result = name.value();
  const auto version = get_attribute(node, "version");
  if (version)
    {
      result += '@';
      const auto is_default = get_attribute(node, "is-default-version");
      if (is_default && is_default.value() == "yes")
        result += '@';
      result += version.value();
    }
  return result;
}

static const std::set<std::string> HAS_LOCATION = {
  "class-decl",
  "enum-decl",
  "function-decl",
  "parameter",
  "typedef-decl",
  "union-decl",
  "var-decl"
};

/// Limit location information.
///
/// @param location_info the level of location information to retain
///
/// @param node the element to process
static void
limit_locations(LocationInfo location_info, xmlNodePtr node)
{
  if (node->type != XML_ELEMENT_NODE)
    return;
  if (HAS_LOCATION.count(from_libxml(node->name)))
    {
      if (location_info > LocationInfo::COLUMN)
        {
          unset_attribute(node, "column");
          if (location_info > LocationInfo::LINE)
            {
              unset_attribute(node, "line");
              if (location_info > LocationInfo::FILE)
                unset_attribute(node, "filepath");
            }
        }
    }
  for (xmlNodePtr child : get_children(node))
    limit_locations(location_info, child);
}

/// Handle unreachable elements.
///
/// Reachability is defined to be union of contains, containing and
/// refers-to relationships for types, declarations and symbols. The
/// roots for reachability are the ELF elements in the ABI.
///
/// The subrange element requires special treatment. It has a useless
/// type id, but it is not a type and its type id aliases with that of
/// all other subranges of the same length. So don't treat it as a type.
///
/// @param prune whether to prune unreachable elements
///
/// @param report whether to report untyped symbols
///
/// @param root the XML root element
///
/// @return the number of untyped symbols
static size_t
handle_unreachable(bool prune, bool report, xmlNodePtr root)
{
  // ELF symbol ids.
  std::set<std::string> elf_symbol_ids;

  // Simple way of allowing two kinds of nodes: false=>type,
  // true=>symbol.
  using vertex_t = std::pair<bool, std::string>;

  // Graph vertices.
  std::set<vertex_t> vertices;
  // Graph edges.
  std::map<vertex_t, std::set<vertex_t>> edges;

  // Keep track of type / symbol nesting so we can identify contains,
  // containing and refers-to relationships.
  std::vector<vertex_t> stack;

  // Process an XML node, adding a vertex and possibly some edges.
  std::function<void(xmlNodePtr)> process_node = [&](xmlNodePtr node) {
    // We only care about elements and not comments, at this stage.
    if (node->type != XML_ELEMENT_NODE)
      return;

    const char* node_name = from_libxml(node->name);

    // Is this an ELF symbol?
    if (strcmp(node_name, "elf-symbol") == 0)
      {
        elf_symbol_ids.insert(get_elf_symbol_id(node));
        // Early return is safe, but not necessary.
        return;
      }

    // Is this a type? Note that the same id may appear multiple times.
    const auto id = strcmp(node_name, "subrange") != 0
                    ? get_attribute(node, "id")
                    : std::optional<std::string>();
    if (id)
      {
        vertex_t type_vertex{false, id.value()};
        vertices.insert(type_vertex);
        const auto naming_typedef_id = get_attribute(node, "naming-typedef-id");
        if (naming_typedef_id)
          {
            // This is an odd one, there can be a backwards link from an
            // anonymous type to a typedef that refers to it. The -t
            // option will drop these, but if they are still present, we
            // should model the link to avoid the risk of dangling
            // references.
            vertex_t naming_typedef_vertex{false, naming_typedef_id.value()};
            edges[type_vertex].insert(naming_typedef_vertex);
          }
        if (!stack.empty())
          {
            // Parent<->child dependencies; record dependencies both
            // ways to avoid holes in XML types and declarations.
            const auto& parent = stack.back();
            edges[parent].insert(type_vertex);
            edges[type_vertex].insert(parent);
          }
        // Record the type.
        stack.push_back(type_vertex);
      }

    // Is this a (declaration expected to be linked to a) symbol?
    const auto symbol = get_attribute(node, "elf-symbol-id");
    if (symbol)
      {
        vertex_t symbol_vertex{true, symbol.value()};
        vertices.insert(symbol_vertex);
        if (!stack.empty())
          {
            // Parent<->child dependencies; record dependencies both
            // ways to avoid making holes in XML types and declarations.
            //
            // Symbols exist outside of the type hierarchy, so choosing
            // to make them depend on a containing type scope and vice
            // versa is conservative and probably not necessary.
            const auto& parent = stack.back();
            edges[parent].insert(symbol_vertex);
            edges[symbol_vertex].insert(parent);
          }
        // Record the symbol.
        stack.push_back(symbol_vertex);
        // In practice there will be at most one symbol on the stack; we could
        // verify this here, but it wouldn't achieve anything.
      }

    // Being both would make the stack ordering ambiguous.
    if (id && symbol)
      {
        std::cerr << "cannot handle element which is both type and symbol\n";
        exit(1);
      }

    // Is there a reference to another type?
    const auto type_id = get_attribute(node, "type-id");
    if (type_id && !stack.empty())
      {
        // The enclosing type or symbol refers to another type.
        const auto& parent = stack.back();
        vertex_t type_id_vertex{false, type_id.value()};
        edges[parent].insert(type_id_vertex);
      }

    // Process recursively.
    for (auto child : get_children(node))
      process_node(child);

    // Restore the stack.
    if (symbol)
      stack.pop_back();
    if (id)
      stack.pop_back();
  };

  // Traverse the whole root element and build a graph.
  process_node(root);

  // Simple DFS.
  std::set<vertex_t> seen;
  std::function<void(vertex_t)> dfs = [&](vertex_t vertex) {
    if (!seen.insert(vertex).second)
      return;
    auto it = edges.find(vertex);
    if (it != edges.end())
      for (auto to : it->second)
        dfs(to);
  };

  // Count of how many symbols are untyped.
  size_t untyped = 0;

  // Traverse the graph, starting from the ELF symbols.
  for (const auto& symbol_id : elf_symbol_ids)
    {
      vertex_t symbol_vertex{true, symbol_id};
      if (vertices.count(symbol_vertex))
        {
          dfs(symbol_vertex);
        }
      else
        {
          if (report)
            std::cerr << "no declaration found for ELF symbol with id "
                      << symbol_id << '\n';
          ++untyped;
        }
    }

  // This is a DFS with early stopping.
  std::function<void(xmlNodePtr)> remove_unseen = [&](xmlNodePtr node) {
    if (node->type != XML_ELEMENT_NODE)
      return;

    const char* node_name = from_libxml(node->name);

    // Return if we know that this is a type to keep or drop in its
    // entirety.
    const auto id = strcmp(node_name, "subrange") != 0
                    ? get_attribute(node, "id")
                    : std::optional<std::string>();
    if (id)
      {
        if (!seen.count(vertex_t{false, id.value()}))
          remove_element(node);
        return;
      }

    // Return if we know that this is a declaration to keep or drop in
    // its entirety. Note that var-decl and function-decl are the only
    // elements that can have an elf-symbol-id attribute.
    if (strcmp(node_name, "var-decl") == 0
        || strcmp(node_name, "function-decl") == 0)
      {
        const auto symbol = get_attribute(node, "elf-symbol-id");
        if (!(symbol && seen.count(vertex_t{true, symbol.value()})))
          remove_element(node);
        return;
      }

    // Otherwise, this is not a type, declaration or part thereof, so
    // process child elements.
    for (auto child : get_children(node))
      remove_unseen(child);
  };

  if (prune)
    // Traverse the XML, removing unseen elements.
    remove_unseen(root);

  return untyped;
}

/// Tidy anonymous types in various ways.
///
/// 1. Normalise anonymous type names by removing the numerical suffix.
///
/// Anonymous type names take the form __anonymous_foo__N where foo is
/// one of enum, struct or union and N is an optional numerical suffix.
/// The suffices are senstive to processing order and do not convey
/// useful ABI information. They can cause spurious harmless diffs and
/// make XML diffing and rebasing harder.
///
/// It's best to remove the suffix.
///
/// 2. Reanonymise anonymous types that have been given names.
///
/// A recent change to abidw changed its behaviour for any anonymous
/// type that has a naming typedef. In addition to linking the typedef
/// and type in both directions, the code now gives (some) anonymous
/// types the same name as the typedef. This misrepresents the original
/// types.
///
/// Such types should be anonymous.
///
/// 3. Discard naming typedef backlinks.
///
/// The attribute naming-typedef-id is a backwards link from an
/// anonymous type to the typedef that refers to it. It is ignored by
/// abidiff.
///
/// Unfortunately, libabigail sometimes conflates multiple anonymous
/// types that have naming typedefs and only one of the typedefs can
/// "win". ABI XML is thus sensitive to processing order and can also
/// end up containing definitions of an anonymous type with differing
/// naming-typedef-id attributes.
///
/// It's best to just drop the attribute.
///
/// @param node the XML node to process
static void
handle_anonymous_types(bool normalise, bool reanonymise, bool discard_naming,
                       xmlNodePtr node)
{
  if (node->type != XML_ELEMENT_NODE)
    return;

  const auto it = NAMED_TYPES.find(from_libxml(node->name));
  if (it != NAMED_TYPES.end())
    {
      const auto& anon = it->second;
      const auto name_attribute = get_attribute(node, "name");
      const auto& name =
          name_attribute ? name_attribute.value() : std::string();
      const auto anon_attr = get_attribute(node, "is-anonymous");
      const bool is_anon = anon_attr && anon_attr.value() == "yes";
      const auto naming_attribute = get_attribute(node, "naming-typedef-id");
      if (normalise && is_anon && name != anon) {
        // __anonymous_foo__123 -> __anonymous_foo__
        set_attribute(node, "name", anon);
      }
      if (reanonymise && !is_anon && naming_attribute) {
        // bar with naming typedef -> __anonymous_foo__
        set_attribute(node, "is-anonymous", "yes");
        set_attribute(node, "name", anon);
      }
      if (discard_naming && naming_attribute)
        unset_attribute(node, "naming-typedef-id");
    }

  for (auto child : get_children(node))
    handle_anonymous_types(normalise, reanonymise, discard_naming, child);
}

/// Remove attributes emitted by abidw --load-all-types.
///
/// With this invocation and if any user-defined types are deemed
/// unreachable, libabigail will output a tracking-non-reachable-types
/// attribute on top-level elements and a is-non-reachable attribute on
/// each such type element.
///
/// abitidy has its own graph-theoretic notion of reachability and these
/// attributes have no ABI relevance.
///
/// It's best to just drop them.
///
/// @param node the XML node to process
void
clear_non_reachable(xmlNodePtr node)
{
  if (node->type != XML_ELEMENT_NODE)
    return;

  const char* node_name = from_libxml(node->name);

  if (strcmp(node_name, "abi-corpus-group") == 0
      || strcmp(node_name, "abi-corpus") == 0)
    unset_attribute(node, "tracking-non-reachable-types");
  else if (NAMED_TYPES.find(node_name) != NAMED_TYPES.end())
    unset_attribute(node, "is-non-reachable");

  for (auto child : get_children(node))
    clear_non_reachable(child);
}

/// The set of attributes that should be excluded from consideration
/// when comparing XML elements.
///
/// Source location attributes are omitted with --no-show-locs without
/// changing the meaning of the ABI. They can also sometimes vary
/// between duplicate type definitions.
///
/// The naming-typedef-id attribute, if not already removed by another
/// pass, is irrelevant to ABI semantics.
///
/// The is-non-reachable attribute, if not already removed by another
/// pass, is irrelevant to ABI semantics.
static const std::unordered_set<std::string> IRRELEVANT_ATTRIBUTES = {
  {"filepath"},
  {"line"},
  {"column"},
  {"naming-typedef-id"},
  {"is-non-reachable"},
};

/// Determine whether one XML element is a subtree of another.
///
/// XML elements representing types are sometimes emitted multiple
/// times, identically. Also, member typedefs are sometimes emitted
/// separately from their types, resulting in duplicate XML fragments.
///
/// Both these issues can be resolved by first detecting duplicate
/// occurrences of a given type id and then checking to see if there's
/// an instance that subsumes the others, which can then be eliminated.
///
/// @param left the first element to compare
///
/// @param right the second element to compare
///
/// @return whether the first element is a subtree of the second
bool
sub_tree(xmlNodePtr left, xmlNodePtr right)
{
  // Node names must match.
  const char* left_name = from_libxml(left->name);
  const char* right_name = from_libxml(right->name);
  if (strcmp(left_name, right_name) != 0)
    return false;
  // Attributes may be missing on the left, but must match otherwise.
  for (auto p = left->properties; p; p = p->next)
  {
    const char* attribute_name = from_libxml(p->name);
    if (IRRELEVANT_ATTRIBUTES.count(attribute_name))
      continue;
    // EXCEPTION: libabigail emits the access specifier for the type
    // it's trying to "emit in scope" rather than for what may be a
    // containing type; so allow member-type attribute access to differ.
    if (strcmp(left_name, "member-type") == 0
        && strcmp(attribute_name, "access") == 0)
      continue;
    const auto left_value = get_attribute(left, attribute_name);
    assert(left_value);
    const auto right_value = get_attribute(right, attribute_name);
    if (!right_value || left_value.value() != right_value.value())
      return false;
  }
  // The left subelements must be a subsequence of the right ones.
  xmlNodePtr left_child = xmlFirstElementChild(left);
  xmlNodePtr right_child = xmlFirstElementChild(right);
  while (left_child && right_child)
    {
      if (sub_tree(left_child, right_child))
        left_child = xmlNextElementSibling(left_child);
      right_child = xmlNextElementSibling(right_child);
    }
  return !left_child;
}

/// Elminate non-conflicting / report conflicting type definitions.
///
/// This function can eliminate exact type duplicates and duplicates
/// where there is at least one maximal definition. It can report the
/// remaining, conflicting duplicate definitions.
///
/// If a type has duplicate definitions in multiple namespace scopes,
/// these should not be reordered. This function reports how many such
/// types it finds.
///
/// @param eliminate whether to eliminate non-conflicting duplicates
///
/// @param report whether to report conflicting duplicate definitions
///
/// @param root the root XML element
///
/// @return the number of types defined in multiple namespace scopes
size_t handle_duplicate_types(bool eliminate, bool report, xmlNodePtr root)
{
  // map of type-id to pair of set of namespace scopes and vector of
  // xmlNodes
  std::unordered_map<
      std::string,
      std::pair<
          std::set<namespace_scope>,
          std::vector<xmlNodePtr>>> types;
  namespace_scope namespaces;

  // find all type occurrences
  std::function<void(xmlNodePtr)> dfs = [&](xmlNodePtr node) {
    if (node->type != XML_ELEMENT_NODE)
      return;
    const char* node_name = from_libxml(node->name);
    std::optional<std::string> namespace_name;
    if (strcmp(node_name, "namespace-decl") == 0)
      namespace_name = get_attribute(node, "name");
    if (namespace_name)
      namespaces.push_back(namespace_name.value());
    if (strcmp(node_name, "abi-corpus-group") == 0
        || strcmp(node_name, "abi-corpus") == 0
        || strcmp(node_name, "abi-instr") == 0
        || namespace_name)
      {
        for (auto child : get_children(node))
          dfs(child);
      }
    else
      {
        const auto id = get_attribute(node, "id");
        if (id)
          {
            auto& info = types[id.value()];
            info.first.insert(namespaces);
            info.second.push_back(node);
          }
      }
    if (namespace_name)
      namespaces.pop_back();
  };
  dfs(root);

  size_t scope_conflicts = 0;
  for (const auto& [id, scopes_and_definitions] : types)
    {
      const auto& [scopes, definitions] = scopes_and_definitions;

      if (scopes.size() > 1)
        {
          if (report)
            std::cerr << "conflicting scopes found for type '" << id << "'\n";
          ++scope_conflicts;
          continue;
        }

      const size_t count = definitions.size();
      if (count <= 1)
        continue;

      // Find a potentially maximal candidate by scanning through and
      // retaining the new definition if it's a supertree of the current
      // candidate.
      std::vector<bool> ok(count);
      size_t candidate = 0;
      ok[candidate] = true;
      for (size_t ix = 1; ix < count; ++ix)
        if (sub_tree(definitions[candidate], definitions[ix]))
          {
            candidate = ix;
            ok[candidate] = true;
          }

      // Verify the candidate is indeed maximal by scanning the
      // definitions not already known to be subtrees of it.
      bool bad = false;
      for (size_t ix = 0; ix < count; ++ix)
        if (!ok[ix] && !sub_tree(definitions[ix], definitions[candidate]))
          {
            bad = true;
            break;
          }
      if (bad)
        {
          if (report)
            std::cerr << "conflicting definitions found for type '" << id
                      << "'\n";
          continue;
        }

      if (eliminate)
        // Remove all but the maximal definition.
        for (size_t ix = 0; ix < count; ++ix)
          if (ix != candidate)
            remove_element(definitions[ix]);
    }

  return scope_conflicts;
}

static const std::set<std::string> INSTR_VARIABLE_ATTRIBUTES = {
  "path",
  "comp-dir-path",
  "language",
};

/// Collect elements of abi-instr elements by namespace.
///
/// Namespaces are not returned but are recursively traversed with the
/// namespace stack being maintained. Other elements are associated with
/// the current namespace.
///
/// @param nodes the nodes to traverse
///
/// @return child elements grouped by namespace scope
static std::map<namespace_scope, std::vector<xmlNodePtr>>
get_children_by_namespace(const std::vector<xmlNodePtr>& nodes)
{
  std::map<namespace_scope, std::vector<xmlNodePtr>> result;
  namespace_scope scope;

  std::function<void(xmlNodePtr)> process = [&](xmlNodePtr node) {
    if (node->type != XML_ELEMENT_NODE)
      return;
    std::optional<std::string> namespace_name;
    const char* node_name = from_libxml(node->name);
    if (strcmp(node_name, "namespace-decl") == 0)
      namespace_name = get_attribute(node, "name");
    if (namespace_name)
      {
        scope.push_back(namespace_name.value());
        for (auto child : get_children(node))
          process(child);
        scope.pop_back();
      }
    else
      result[scope].push_back(node);
  };

  for (auto node : nodes)
    for (auto child : get_children(node))
      process(child);
  return result;
}

/// Sort namespaces, types and declarations.
///
/// This loses annotations (XML comments) on namespace-decl elements.
/// It would have been a fair amount of extra work to preserve them.
///
/// @param root the XML root element
static void
sort_namespaces_types_and_declarations(xmlNodePtr root)
{
  // There are (currently) 2 ABI formats we handle here.
  //
  // 1. An abi-corpus containing one or more abi-instr. In this case, we
  // move all namespaces, types and declarations to a replacement
  // abi-instr at the end of the abi-corpus. The existing abi-instr will
  // then be confirmed as empty and removed.
  //
  // 2. An abi-corpus-group containing one or more abi-corpus each
  // containing zero or more abi-instr (with at least one abi-instr
  // altogether). In this case the replacement abi-instr is created
  // within the first abi-corpus of the group.
  //
  // Anything else is left alone. For example, single abi-instr elements
  // are present in some libabigail test suite files.

  // We first need to identify where to place the new abi-instr and
  // collect all the abi-instr to process.
  xmlNodePtr where = nullptr;
  std::vector<xmlNodePtr> instrs;

  auto process_corpus = [&](xmlNodePtr corpus) {
    if (!where)
      where = corpus;
    for (auto instr : get_children(corpus))
      if (strcmp(from_libxml(instr->name), "abi-instr") == 0)
        instrs.push_back(instr);
  };

  const char* root_name = from_libxml(root->name);
  if (strcmp(root_name, "abi-corpus-group") == 0)
    {
      // Process all corpora in a corpus group together.
      for (auto corpus : get_children(root))
        if (strcmp(from_libxml(corpus->name), "abi-corpus") == 0)
          process_corpus(corpus);
    }
  else if (strcmp(root_name, "abi-corpus") == 0)
    {
      // We have a corpus to sort, just get its instrs.
      process_corpus(root);
    }

  if (instrs.empty())
    return;

  // Collect the attributes of all the instrs.
  std::map<std::string, std::set<std::string>> attributes;
  for (auto instr : instrs)
    for (auto p = instr->properties; p; p = p->next)
      {
        // This is horrible. There should be a better way of iterating.
        const char* attribute_name = from_libxml(p->name);
        const auto attribute_value = get_attribute(instr, attribute_name);
        assert(attribute_value);
        attributes[attribute_name].insert(attribute_value.value());
      }

  // Create and attach a replacement instr and populate its attributes.
  xmlNodePtr replacement =
      xmlAddChild(where, xmlNewNode(nullptr, to_libxml("abi-instr")));
  for (const auto& attribute : attributes)
    {
      const char* attribute_name = attribute.first.c_str();
      const auto& attribute_values = attribute.second;
      if (attribute_values.size() == 1)
        set_attribute(replacement, attribute_name, *attribute_values.begin());
      else if (INSTR_VARIABLE_ATTRIBUTES.count(attribute_name))
        set_attribute(replacement, attribute_name, "various");
      else
        {
          std::cerr << "unexpectedly variable abi-instr attribute '"
                    << attribute_name << "'\n";
          remove_node(replacement);
          return;
        }
    }

  // Order types before declarations, types by id, declarations by name
  // (and by mangled-name, if present).
  struct Compare {
    int
    cmp(xmlNodePtr a, xmlNodePtr b) const
    {
      // NOTE: This must not reorder type definitions with the same id.
      // In particular, we cannot do anything nice and easy like order
      // by element tag first.
      //
      // TODO: Replace compare and subtraction with <=>.
      int result;

      auto a_id = get_attribute(a, "id");
      auto b_id = get_attribute(b, "id");
      // types before non-types
      result = b_id.has_value() - a_id.has_value();
      if (result)
        return result;
      if (a_id)
        // sort types by id
        return a_id.value().compare(b_id.value());

      auto a_name = get_attribute(a, "name");
      auto b_name = get_attribute(b, "name");
      // declarations before non-declarations
      result = b_name.has_value() - a_name.has_value();
      if (result)
        return result;
      if (a_name)
        {
          // sort declarations by name
          result = a_name.value().compare(b_name.value());
          if (result)
            return result;
          auto a_mangled = get_attribute(a, "mangled-name");
          auto b_mangled = get_attribute(b, "mangled-name");
          // without mangled-name first
          result = a_mangled.has_value() - b_mangled.has_value();
          if (result)
            return result;
          // and by mangled-name if present
          return !a_mangled ? 0 : a_mangled.value().compare(b_mangled.value());
        }

      // a and b are not types or declarations; should not be reached
      return 0;
    }

    bool
    operator()(xmlNodePtr a, xmlNodePtr b) const
    {
      return cmp(a, b) < 0;
    }
  };

  // Collect the child elements of all the instrs, by namespace scope.
  auto scoped_children = get_children_by_namespace(instrs);
  for (auto& [scope, children] : scoped_children)
    // Sort the children, preserving order of duplicates.
    std::stable_sort(children.begin(), children.end(), Compare());

  // Create namespace elements on demand. The global namespace, with
  // empty scope, is just the replacement instr itself.
  std::map<namespace_scope, xmlNodePtr> namespace_elements{{{}, replacement}};
  std::function<xmlNodePtr(const namespace_scope&)> get_namespace_element =
      [&](const namespace_scope& scope) {
        auto insertion = namespace_elements.insert({scope, nullptr});
    if (insertion.second)
      {
        // Insertion succeeded, so the scope cannot be empty.
        namespace_scope truncated = scope;
        truncated.pop_back();
        xmlNodePtr parent = get_namespace_element(truncated);
        // We can now create an XML element in the right place.
        xmlNodePtr child = xmlNewNode(nullptr, to_libxml("namespace-decl"));
        set_attribute(child, "name", scope.back());
        xmlAddChild(parent, child);
        insertion.first->second = child;
      }
    return insertion.first->second;
  };

  // Move the children to the replacement instr or its subelements.
  for (const auto& [scope, elements] : scoped_children)
    {
      xmlNodePtr namespace_element = get_namespace_element(scope);
      for (auto element : elements)
        move_element(element, namespace_element);
    }

  // Check the original instrs are now all empty and remove them.
  for (auto instr : instrs)
    if (get_children_by_namespace({instr}).empty())
      remove_node(instr);
    else
      std::cerr << "original abi-instr has residual child elements\n";
}

static constexpr std::array<std::string_view, 2> SYMBOL_SECTION_SUFFICES = {
  "symbol_list",
  "whitelist",
};

/// Read symbols from a file.
///
/// This aims to be compatible with the .ini format used by libabigail
/// for suppression specifications and symbol lists. All symbol list
/// sections in the given file are combined into a single set of
/// symbols.
///
/// @param filename the name of the file from which to read
///
/// @return a set of symbols
symbol_set
read_symbols(const char* filename)
{
  symbol_set symbols;
  std::ifstream file(filename);
  if (!file)
    {
      std::cerr << "error opening symbol file '" << filename << "'\n";
      exit(1);
    }

  bool in_symbol_section = false;
  std::string line;
  while (std::getline(file, line))
    {
      size_t start = 0;
      size_t limit = line.size();
      // Strip comments and leading / trailing whitespace.
      while (start < limit)
        {
          if (std::isspace(line[start]))
            ++start;
          else if (line[start] == '#')
            start = limit;
          else
            break;
        }
      while (start < limit)
        {
          if (std::isspace(line[limit - 1]))
            --limit;
          else
            break;
        }
      // Skip empty lines.
      if (start == limit)
        continue;
      // See if we are entering a symbol list section.
      if (line[start] == '[' && line[limit - 1] == ']')
        {
          std::string_view section(&line[start + 1], limit - start - 2);
          bool found = false;
          for (const auto& suffix : SYMBOL_SECTION_SUFFICES)
            if (section.size() >= suffix.size()
                && section.substr(section.size() - suffix.size()) == suffix)
              {
                found = true;
                break;
              }
          in_symbol_section = found;
          continue;
        }
      // Add symbol.
      if (in_symbol_section)
        symbols.insert(std::string(&line[start], limit - start));
    }
  if (!file.eof())
    {
      std::cerr << "error reading symbol file '" << filename << "'\n";
      exit(1);
    }
  return symbols;
}

/// Remove unlisted ELF symbols.
///
/// @param symbols the set of symbols
///
/// @param node the XML node to process
void
filter_symbols(const symbol_set& symbols, xmlNodePtr node)
{
  if (node->type != XML_ELEMENT_NODE)
    return;
  const char* node_name = from_libxml(node->name);
  if (strcmp(node_name, "abi-corpus-group") == 0
      || strcmp(node_name, "abi-corpus") == 0
      || strcmp(node_name, "elf-variable-symbols") == 0
      || strcmp(node_name, "elf-function-symbols") == 0)
    {
      // Process children.
      for (auto child : get_children(node))
        filter_symbols(symbols, child);
    }
  else if (strcmp(node_name, "elf-symbol") == 0)
    {
      const auto name = get_attribute(node, "name");
      if (name && !symbols.count(name.value()))
        remove_element(node);
    }
}

/// Main program.
///
/// Read and write ABI XML, with optional processing passes.
///
/// @param argc argument count
///
/// @param argv argument vector
///
/// @return exit status
int
main(int argc, char* argv[])
{
  // Defaults.
  const char* opt_input = nullptr;
  const char* opt_output = nullptr;
  std::optional<symbol_set> opt_symbols;
  LocationInfo opt_locations = LocationInfo::COLUMN;
  int opt_indentation = 2;
  bool opt_normalise_anonymous = false;
  bool opt_reanonymise_anonymous = false;
  bool opt_discard_naming_typedefs = false;
  bool opt_prune_unreachable = false;
  bool opt_report_untyped = false;
  bool opt_abort_on_untyped = false;
  bool opt_clear_non_reachable = false;
  bool opt_eliminate_duplicates = false;
  bool opt_report_conflicts = false;
  bool opt_sort = false;
  bool opt_drop_empty = false;

  // Process command line.
  auto usage = [&]() -> int {
    std::cerr << "usage: " << argv[0] << '\n'
              << "  [-i|--input file]\n"
              << "  [-o|--output file]\n"
              << "  [-S|--symbols file]\n"
              << "  [-L|--locations {column|line|file|none}]\n"
              << "  [-I|--indentation n]\n"
              << "  [-a|--all] (implies -n -r -t -p -u -b -e -c -s -d)\n"
              << "  [-n|--[no-]normalise-anonymous]\n"
              << "  [-r|--[no-]reanonymise-anonymous]\n"
              << "  [-t|--[no-]discard-naming-typedefs]\n"
              << "  [-p|--[no-]prune-unreachable]\n"
              << "  [-u|--[no-]report-untyped]\n"
              << "  [-U|--abort-on-untyped-symbols]\n"
              << "  [-b|--[no-]clear-non-reachable]\n"
              << "  [-e|--[no-]eliminate-duplicates]\n"
              << "  [-c|--[no-]report-conflicts]\n"
              << "  [-s|--[no-]sort]\n"
              << "  [-d|--[no-]drop-empty]\n";
    return 1;
  };
  int opt_index = 1;
  auto get_arg = [&]() {
    if (opt_index < argc)
      return argv[opt_index++];
    exit(usage());
  };
  while (opt_index < argc)
    {
      const std::string arg = get_arg();
      if (arg == "-i" || arg == "--input")
        opt_input = get_arg();
      else if (arg == "-o" || arg == "--output")
        opt_output = get_arg();
      else if (arg == "-S" || arg == "--symbols")
        opt_symbols = read_symbols(get_arg());
      else if (arg == "-L" || arg == "--locations")
        {
          auto it = LOCATION_INFO_NAME.find(get_arg());
          if (it == LOCATION_INFO_NAME.end())
            exit(usage());
          opt_locations = it->second;
        }
      else if (arg == "-I" || arg == "--indentation")
        {
          std::istringstream is(get_arg());
          is >> std::noskipws >> opt_indentation;
          if (!is || !is.eof() || opt_indentation < 0)
            exit(usage());
        }
      else if (arg == "-a" || arg == "--all")
        opt_normalise_anonymous = opt_reanonymise_anonymous
                                = opt_discard_naming_typedefs
                                = opt_prune_unreachable
                                = opt_report_untyped
                                = opt_clear_non_reachable
                                = opt_eliminate_duplicates
                                = opt_report_conflicts
                                = opt_sort
                                = opt_drop_empty
                                = true;
      else if (arg == "-n" || arg == "--normalise-anonymous")
        opt_normalise_anonymous = true;
      else if (arg == "--no-normalise-anonymous")
        opt_normalise_anonymous = false;
      else if (arg == "-r" || arg == "--reanonymise-anonymous")
        opt_reanonymise_anonymous = true;
      else if (arg == "--no-reanonymise-anonymous")
        opt_reanonymise_anonymous = false;
      else if (arg == "-t" || arg == "--discard-naming-typedefs")
        opt_discard_naming_typedefs = true;
      else if (arg == "--no-discard-naming-typedefs")
        opt_discard_naming_typedefs = false;
      else if (arg == "-p" || arg == "--prune-unreachable")
        opt_prune_unreachable = true;
      else if (arg == "--no-prune-unreachable")
        opt_prune_unreachable = false;
      else if (arg == "-u" || arg == "--report-untyped")
        opt_report_untyped = true;
      else if (arg == "--no-report-untyped")
        opt_report_untyped = false;
      else if (arg == "-U" || arg == "--abort-on-untyped-symbols")
        opt_abort_on_untyped = true;
      else if (arg == "-b" || arg == "--clear-non-reachable")
        opt_clear_non_reachable = true;
      else if (arg == "--no-clear-non-reachable")
        opt_clear_non_reachable = false;
      else if (arg == "-e" || arg == "--eliminate-duplicates")
        opt_eliminate_duplicates = true;
      else if (arg == "--no-eliminate-duplicates")
        opt_eliminate_duplicates = false;
      else if (arg == "-c" || arg == "--report-conflicts")
        opt_report_conflicts = true;
      else if (arg == "--no-report-conflicts")
        opt_report_conflicts = false;
      else if (arg == "-s" || arg == "--sort")
        opt_sort = true;
      else if (arg == "--no-sort")
        opt_sort = false;
      else if (arg == "-d" || arg == "--drop-empty")
        opt_drop_empty = true;
      else if (arg == "--no-drop-empty")
        opt_drop_empty = false;
      else
        exit(usage());
    }

  // Open input for reading.
  int in_fd = STDIN_FILENO;
  if (opt_input)
    {
      in_fd = open(opt_input, O_RDONLY);
      if (in_fd < 0)
        {
          std::cerr << "could not open '" << opt_input << "' for reading: "
                    << strerror(errno) << '\n';
          exit(1);
        }
    }

  // Read the XML.
  xmlParserCtxtPtr parser_context = xmlNewParserCtxt();
  xmlDocPtr document
      = xmlCtxtReadFd(parser_context, in_fd, nullptr, nullptr, 0);
  if (!document)
    {
      std::cerr << "failed to parse input as XML\n";
      exit(1);
    }
  xmlFreeParserCtxt(parser_context);
  close(in_fd);

  // Get the root element.
  xmlNodePtr root = xmlDocGetRootElement(document);
  if (!root)
    {
      std::cerr << "XML document has no root element\n";
      exit(1);
    }

  // Strip text nodes to simplify other operations.
  strip_text(root);

  // Remove unlisted symbols.
  if (opt_symbols)
    filter_symbols(opt_symbols.value(), root);

  // Normalise anonymous type names.
  // Reanonymise anonymous types.
  // Discard naming typedef backlinks.
  if (opt_normalise_anonymous || opt_reanonymise_anonymous
      || opt_discard_naming_typedefs)
    handle_anonymous_types(opt_normalise_anonymous, opt_reanonymise_anonymous,
                           opt_discard_naming_typedefs, root);

  // Prune unreachable elements and/or report untyped symbols.
  size_t untyped_symbols = 0;
  if (opt_prune_unreachable || opt_report_untyped || opt_abort_on_untyped)
    untyped_symbols += handle_unreachable(
        opt_prune_unreachable, opt_report_untyped, root);
  if (opt_abort_on_untyped && untyped_symbols)
    {
      std::cerr << "found " << untyped_symbols << " untyped symbols\n";
      exit(1);
    }

  // Limit location information.
  if (opt_locations > LocationInfo::COLUMN)
    limit_locations(opt_locations, root);

  // Clear unwanted non-reachable attributes.
  if (opt_clear_non_reachable)
    clear_non_reachable(root);

  // Eliminate complete duplicates and extra fragments of types.
  // Report conflicting type defintions.
  // Record whether there are namespace scope conflicts.
  size_t scope_conflicts = 0;
  if (opt_eliminate_duplicates || opt_report_conflicts || opt_sort)
    scope_conflicts += handle_duplicate_types(
        opt_eliminate_duplicates, opt_report_conflicts, root);

  // Sort namespaces, types and declarations.
  if (opt_sort)
    {
      if (scope_conflicts)
        std::cerr << "found type definition scope conflicts, skipping sort\n";
      else
        sort_namespaces_types_and_declarations(root);
    }

  // Drop empty subelements.
  if (opt_drop_empty)
    drop_empty(root);

  // Reformat root element for human consumption.
  format_xml(std::string(opt_indentation, ' '), std::string(), root);

  // Open output for writing.
  int out_fd = STDOUT_FILENO;
  if (opt_output)
    {
      out_fd = open(opt_output, O_CREAT | O_TRUNC | O_WRONLY,
                    S_IRUSR | S_IWUSR | S_IRGRP | S_IWGRP | S_IROTH | S_IWOTH);
      if (out_fd < 0)
        {
          std::cerr << "could not open '" << opt_output << "' for writing: "
                    << strerror(errno) << '\n';
          exit(1);
        }
    }

  // Write the XML.
  //
  // First to memory, as we need to do a little post-processing.
  xmlChar* out_data;
  int out_size;
  xmlDocDumpMemory(document, &out_data, &out_size);
  // Remove the XML declaration as it currently upsets abidiff.
  xmlChar* out_limit = out_data + out_size;
  while (out_data < out_limit && *out_data != '\n')
    ++out_data;
  if (out_data < out_limit)
    ++out_data;
  // Adjust quotes to match abidw.
  adjust_quotes(out_data, out_limit);
  // And now to a file.
  size_t count = out_limit - out_data;
  if (write(out_fd, out_data, count) != count)
    {
      std::cerr << "could not write output: " << strerror(errno) << '\n';
      exit(1);
    }
  if (close(out_fd) < 0)
    {
      std::cerr << "could not close output: " << strerror(errno) << '\n';
      exit(1);
    }

  // Free libxml document.
  xmlFreeDoc(document);
  return 0;
}
