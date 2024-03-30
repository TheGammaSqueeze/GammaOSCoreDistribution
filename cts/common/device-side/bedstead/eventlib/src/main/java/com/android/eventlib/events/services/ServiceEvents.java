/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.eventlib.events.services;

/**
 * Quick access to event queries about services.
 */
public interface ServiceEvents {

    /**
     * Query for when an service is created.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    ServiceCreatedEvent.ServiceCreatedEventQuery serviceCreated();


    /**
     * Query for when an service is started.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    ServiceStartedEvent.ServiceStartedEventQuery serviceStarted();

    /**
     * Query for when an service is destroyed.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    ServiceDestroyedEvent.ServiceDestroyedEventQuery serviceDestroyed();

    /**
     * Query for when an service's configuration is changed.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    ServiceConfigurationChangedEvent.ServiceConfigurationChangedEventQuery
            serviceConfigurationChanged();

    /**
     * Query for when an service has low memory.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    ServiceLowMemoryEvent.ServiceLowMemoryEventQuery serviceLowMemory();

    /**
     * Query for when an service has it's memory trimmed.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    ServiceMemoryTrimmedEvent.ServiceMemoryTrimmedEventQuery serviceMemoryTrimmed();

    /**
     * Query for when an service is bound.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    ServiceBoundEvent.ServiceBoundEventQuery serviceBound();

    /**
     * Query for when an service is unbound.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    ServiceUnboundEvent.ServiceUnboundEventQuery serviceUnbound();

    /**
     * Query for when an service is re-bound.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    ServiceReboundEvent.ServiceReboundEventQuery serviceRebound();

    /**
     * Query for when an service has a task removed.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    ServiceTaskRemovedEvent.ServiceTaskRemovedEventQuery serviceTaskRemoved();
}
