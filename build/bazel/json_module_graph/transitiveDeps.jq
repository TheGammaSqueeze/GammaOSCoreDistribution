# CMD: Returns the names of the transitive dependencies of the module named $arg

include "library";

(moduleGraphNoVariants | removeSelfEdges) as $m |
  [$arg] |
  transitiveDeps($m)
