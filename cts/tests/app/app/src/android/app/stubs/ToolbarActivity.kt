/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.app.stubs

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.InputEvent
import android.widget.Toolbar
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class ToolbarActivity : Activity() {
    private lateinit var toolbar: Toolbar
    private val events = LinkedBlockingQueue<InputEvent>()

    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.toolbar_activity)
        toolbar = findViewById(R.id.toolbar)
        setActionBar(toolbar)
    }

    fun getToolbar(): Toolbar {
        return toolbar
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        events.add(KeyEvent(event))
        return super.dispatchKeyEvent(event)
    }

    fun getInputEvent(): InputEvent? {
        return events.poll(5, TimeUnit.SECONDS)
    }
}
