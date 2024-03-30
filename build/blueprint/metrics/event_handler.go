// Copyright 2022 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package metrics

import (
	"fmt"
	"strings"
	"time"
)

// EventHandler tracks nested events and their start/stop times in a single
// thread.
type EventHandler struct {
	completedEvents []Event

	// These fields handle event scoping. When starting a new event, a new entry
	// is pushed onto these fields. When ending an event, these fields are popped.
	scopeIds        []string
	scopeStartTimes []time.Time
}

// _now wraps the time.Now() function. _now is declared for unit testing purpose.
var _now = func() time.Time {
	return time.Now()
}

// Event holds the performance metrics data of a single build event.
type Event struct {
	// A unique human-readable identifier / "name" for the build event. Event
	// names use period-delimited scoping. For example, if an event alpha starts,
	// then an event bravo starts, then an event charlie starts and ends, the
	// unique identifier for charlie will be 'alpha.bravo.charlie'.
	Id string

	Start time.Time
	end   time.Time
}

// RuntimeNanoseconds returns the number of nanoseconds between the start
// and end times of the event.
func (e Event) RuntimeNanoseconds() uint64 {
	return uint64(e.end.Sub(e.Start).Nanoseconds())
}

// Begin logs the start of an event. This must be followed by a corresponding
// call to End (though other events may begin and end before this event ends).
// Events within the same scope must have unique names.
func (h *EventHandler) Begin(name string) {
	h.scopeIds = append(h.scopeIds, name)
	h.scopeStartTimes = append(h.scopeStartTimes, _now())
}

// End logs the end of an event. All events nested within this event must have
// themselves been marked completed.
func (h *EventHandler) End(name string) {
	if len(h.scopeIds) == 0 || name != h.scopeIds[len(h.scopeIds)-1] {
		panic(fmt.Errorf("Unexpected scope end '%s'. Current scope: (%s)",
			name, h.scopeIds))
	}
	event := Event{
		// The event Id is formed from the period-delimited scope names of all
		// active events (e.g. `alpha.beta.charlie`). See Event.Id documentation
		// for more detail.
		Id:    strings.Join(h.scopeIds, "."),
		Start: h.scopeStartTimes[len(h.scopeStartTimes)-1],
		end:   _now(),
	}
	h.completedEvents = append(h.completedEvents, event)
	h.scopeIds = h.scopeIds[:len(h.scopeIds)-1]
	h.scopeStartTimes = h.scopeStartTimes[:len(h.scopeStartTimes)-1]
}

// CompletedEvents returns all events which have been completed, after
// validation.
// It is an error to call this method if there are still ongoing events, or
// if two events were completed with the same scope and name.
func (h *EventHandler) CompletedEvents() []Event {
	if len(h.scopeIds) > 0 {
		panic(fmt.Errorf(
			"Retrieving events before all events have been closed. Current scope: (%s)",
			h.scopeIds))
	}
	// Validate no two events have the same full id.
	ids := map[string]bool{}
	for _, event := range h.completedEvents {
		if _, containsId := ids[event.Id]; containsId {
			panic(fmt.Errorf("Duplicate event registered: %s", event.Id))
		}
		ids[event.Id] = true
	}
	return h.completedEvents
}
