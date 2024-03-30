# JSON module graph queries

This directory contains `jq` scripts that query Soong's module graph.
`jq` may be installed through your distribution's repository.

Usage:

```
m json-module-graph
query.sh [-C] <command> <base-of-your-tree>/out/soong/module-graph.json [argument]
```

The following commands are available:
* `directDeps` prints the names of the direct dependencies of the given module
* `distanceFromLeaves` prints the longest distance each module has from a leaf
  in the module graph within the transitive closure of given module
* `filterSubtree` dumps only those modules that are in the given subtree of the
  source tree
* `fullTransitiveDeps` returns the full transitive dependencies of the given
  module
* `moduleTypeStats`: returns of a summary of the module types present on the
  input
* `modulesOfType`: returns the names of modules of the input type
* `printModule` prints all variations of a given module
* `printModule`: returns a slightly more consise view of the input module
* `properties`: returns the properties set in the input module, includes
  properties set via defaults
* `transitiveDeps` prints the names of the transitive dependencies of the given
  module
* `usedVariations` returns a map that shows which variations are used in the
  input and what values they take
* `variantTransitions`  summarizes the variant transitions in the transitive
  closure of the given module
* `fullTransitiveDepsProperties` returns the properties set (including via
  defaults) grouped by module type of the modules in the transitive closure of
  the given module

It's best to filter the full module graph to the part you are interested in
because `jq` isn't too fast on the full graph.
