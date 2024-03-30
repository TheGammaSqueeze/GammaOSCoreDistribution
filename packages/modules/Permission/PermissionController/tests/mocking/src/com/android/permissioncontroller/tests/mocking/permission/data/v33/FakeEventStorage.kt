/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.permissioncontroller.tests.mocking.permission.data.v33

import com.android.permissioncontroller.permission.data.v33.PermissionEvent
import com.android.permissioncontroller.permission.service.v33.PermissionEventStorage

/**
 * Fake event storage class used for tests
 */
class FakeEventStorage<T : PermissionEvent> : PermissionEventStorage<T> {
    val events: MutableList<T> = mutableListOf()

    override suspend fun storeEvent(event: T): Boolean {
        events.add(event)
        return true
    }

    override suspend fun loadEvents(): List<T> {
        return events
    }

    override suspend fun clearEvents() {
        events.clear()
    }

    override suspend fun removeOldData(): Boolean {
        // not implemented
        return true
    }

    override suspend fun removeEventsForPackage(packageName: String): Boolean {
        val toRemove = mutableListOf<T>()
        for (event in events) {
            if (event.packageName == packageName) {
                toRemove.add(event)
            }
        }
        events.removeAll(toRemove)
        return true
    }

    override suspend fun updateEventsBySystemTimeDelta(diffSystemTimeMillis: Long): Boolean {
        // not implemented
        return true
    }
}
