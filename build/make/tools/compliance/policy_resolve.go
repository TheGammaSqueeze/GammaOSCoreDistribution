// Copyright 2021 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package compliance

import (
	"sync"
)

var (
	// AllResolutions is a TraceConditions function that resolves all
	// unfiltered license conditions.
	AllResolutions = TraceConditions(func(tn *TargetNode) LicenseConditionSet { return tn.licenseConditions })
)

// TraceConditions is a function that returns the conditions to trace for each
// target node `tn`.
type TraceConditions func(tn *TargetNode) LicenseConditionSet

// ResolveBottomUpConditions performs a bottom-up walk of the LicenseGraph
// propagating conditions up the graph as necessary according to the properties
// of each edge and according to each license condition in question.
//
// e.g. if a "restricted" condition applies to a binary, it also applies to all
// of the statically-linked libraries and the transitive closure of their static
// dependencies; even if neither they nor the transitive closure of their
// dependencies originate any "restricted" conditions. The bottom-up walk will
// not resolve the library and its transitive closure, but the later top-down
// walk will.
func ResolveBottomUpConditions(lg *LicenseGraph) {
	TraceBottomUpConditions(lg, AllResolutions)
}

// TraceBottomUpConditions performs a bottom-up walk of the LicenseGraph
// propagating trace conditions from `conditionsFn` up the graph as necessary
// according to the properties of each edge and according to each license
// condition in question.
func TraceBottomUpConditions(lg *LicenseGraph, conditionsFn TraceConditions) {

	// short-cut if already walked and cached
	lg.mu.Lock()
	wg := lg.wgBU

	if wg != nil {
		lg.mu.Unlock()
		wg.Wait()
		return
	}
	wg = &sync.WaitGroup{}
	wg.Add(1)
	lg.wgBU = wg
	lg.mu.Unlock()

	// amap identifes targets previously walked. (guarded by mu)
	amap := make(map[*TargetNode]struct{})

	// cmap identifies targets previously walked as pure aggregates. i.e. as containers
	// (guarded by mu)
	cmap := make(map[*TargetNode]struct{})
	var mu sync.Mutex

	var walk func(target *TargetNode, treatAsAggregate bool) LicenseConditionSet

	walk = func(target *TargetNode, treatAsAggregate bool) LicenseConditionSet {
		priorWalkResults := func() (LicenseConditionSet, bool) {
			mu.Lock()
			defer mu.Unlock()

			if _, alreadyWalked := amap[target]; alreadyWalked {
				if treatAsAggregate {
					return target.resolution, true
				}
				if _, asAggregate := cmap[target]; !asAggregate {
					return target.resolution, true
				}
				// previously walked in a pure aggregate context,
				// needs to walk again in non-aggregate context
				delete(cmap, target)
			} else {
				target.resolution |= conditionsFn(target)
				amap[target] = struct{}{}
			}
			if treatAsAggregate {
				cmap[target] = struct{}{}
			}
			return target.resolution, false
		}
		cs, alreadyWalked := priorWalkResults()
		if alreadyWalked {
			return cs
		}

		c := make(chan LicenseConditionSet, len(target.edges))
		// add all the conditions from all the dependencies
		for _, edge := range target.edges {
			go func(edge *TargetEdge) {
				// walk dependency to get its conditions
				cs := walk(edge.dependency, treatAsAggregate && edge.dependency.IsContainer())

				// turn those into the conditions that apply to the target
				cs = depConditionsPropagatingToTarget(lg, edge, cs, treatAsAggregate)

				c <- cs
			}(edge)
		}
		for i := 0; i < len(target.edges); i++ {
			cs |= <-c
		}
		mu.Lock()
		target.resolution |= cs
		mu.Unlock()

		// return conditions up the tree
		return cs
	}

	// walk each of the roots
	for _, rname := range lg.rootFiles {
		rnode := lg.targets[rname]
		_ = walk(rnode, rnode.IsContainer())
	}

	wg.Done()
}

// ResolveTopDownCondtions performs a top-down walk of the LicenseGraph
// propagating conditions from target to dependency.
//
// e.g. For current policy, none of the conditions propagate from target to
// dependency except restricted. For restricted, the policy is to share the
// source of any libraries linked to restricted code and to provide notice.
func ResolveTopDownConditions(lg *LicenseGraph) {
	TraceTopDownConditions(lg, AllResolutions)
}

// TraceTopDownCondtions performs a top-down walk of the LicenseGraph
// propagating trace conditions returned by `conditionsFn` from target to
// dependency.
func TraceTopDownConditions(lg *LicenseGraph, conditionsFn TraceConditions) {

	// short-cut if already walked and cached
	lg.mu.Lock()
	wg := lg.wgTD

	if wg != nil {
		lg.mu.Unlock()
		wg.Wait()
		return
	}
	wg = &sync.WaitGroup{}
	wg.Add(1)
	lg.wgTD = wg
	lg.mu.Unlock()

	// start with the conditions propagated up the graph
	TraceBottomUpConditions(lg, conditionsFn)

	// amap contains the set of targets already walked. (guarded by mu)
	amap := make(map[*TargetNode]struct{})

	// cmap contains the set of targets walked as pure aggregates. i.e. containers
	// (guarded by mu)
	cmap := make(map[*TargetNode]struct{})

	// mu guards concurrent access to cmap
	var mu sync.Mutex

	var walk func(fnode *TargetNode, cs LicenseConditionSet, treatAsAggregate bool)

	walk = func(fnode *TargetNode, cs LicenseConditionSet, treatAsAggregate bool) {
		defer wg.Done()
		mu.Lock()
		fnode.resolution |= conditionsFn(fnode)
		fnode.resolution |= cs
		amap[fnode] = struct{}{}
		if treatAsAggregate {
			cmap[fnode] = struct{}{}
		}
		cs = fnode.resolution
		mu.Unlock()
		// for each dependency
		for _, edge := range fnode.edges {
			func(edge *TargetEdge) {
				// dcs holds the dpendency conditions inherited from the target
				dcs := targetConditionsPropagatingToDep(lg, edge, cs, treatAsAggregate, conditionsFn)
				dnode := edge.dependency
				mu.Lock()
				defer mu.Unlock()
				depcs := dnode.resolution
				_, alreadyWalked := amap[dnode]
				if !dcs.IsEmpty() && alreadyWalked {
					if dcs.Difference(depcs).IsEmpty() {
						// no new conditions

						// pure aggregates never need walking a 2nd time with same conditions
						if treatAsAggregate {
							return
						}
						// non-aggregates don't need walking as non-aggregate a 2nd time
						if _, asAggregate := cmap[dnode]; !asAggregate {
							return
						}
						// previously walked as pure aggregate; need to re-walk as non-aggregate
						delete(cmap, dnode)
					}
				}
				// add the conditions to the dependency
				wg.Add(1)
				go walk(dnode, dcs, treatAsAggregate && dnode.IsContainer())
			}(edge)
		}
	}

	// walk each of the roots
	for _, rname := range lg.rootFiles {
		rnode := lg.targets[rname]
		wg.Add(1)
		// add the conditions to the root and its transitive closure
		go walk(rnode, NewLicenseConditionSet(), rnode.IsContainer())
	}
	wg.Done()
	wg.Wait()
}
