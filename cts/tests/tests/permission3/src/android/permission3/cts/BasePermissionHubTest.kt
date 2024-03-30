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

package android.permission3.cts

import android.content.Intent
import com.android.compatibility.common.util.SystemUtil

/** Base class for the permission hub tests. */
abstract class BasePermissionHubTest : BasePermissionTest() {

    protected fun openMicrophoneTimeline() {
        SystemUtil.runWithShellPermissionIdentity {
            context.startActivity(Intent(Intent.ACTION_REVIEW_PERMISSION_HISTORY).apply {
                putExtra(Intent.EXTRA_PERMISSION_GROUP_NAME,
                    android.Manifest.permission_group.MICROPHONE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}