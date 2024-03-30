# CMD: Returns the properties of module types in the transitive closure of module $arg

include "library";

[((moduleGraphNoVariants | removeSelfEdges) as $m |
  [$arg] |
  transitiveDeps($m)) as $names |
  .[] |
  select (IN(.Name; $names | .[]))] |
  group_by(.Type) |
  map({Type: .[0].Type,
    Props: map(.Module.Android.SetProperties) | flatten | map(.Name) | unique | sort }) |
  sort_by(.Type)



