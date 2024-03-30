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

package android.security.cts.usepermission

import android.app.Activity
import android.os.Bundle
import android.content.Intent

class RequestPermissionActivity : Activity() {
    private val LOG_TAG = RequestPermissionActivity::class.java.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val permissions = intent.getStringArrayExtra("$packageName.PERMISSIONS")!!
            requestPermissions(permissions, 1)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        setResult(RESULT_OK, Intent().apply {
            putExtra("$packageName.PERMISSIONS", permissions)
            putExtra("$packageName.GRANT_RESULTS", grantResults)
        })
        finish()
    }
}