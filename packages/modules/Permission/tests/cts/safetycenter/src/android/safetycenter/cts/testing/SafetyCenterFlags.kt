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

package android.safetycenter.cts.testing

import android.Manifest.permission.WRITE_DEVICE_CONFIG
import android.content.Context
import android.content.res.Resources
import android.provider.DeviceConfig
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity

/** A class that facilitates working with Safety Center flags. */
object SafetyCenterFlags {

    /** Name of the flag that determines whether SafetyCenter is enabled. */
    private const val PROPERTY_SAFETY_CENTER_ENABLED = "safety_center_is_enabled"

    /** Returns whether the device supports Safety Center. */
    fun Context.deviceSupportsSafetyCenter() =
        resources.getBoolean(
            Resources.getSystem().getIdentifier("config_enableSafetyCenter", "bool", "android"))

    /** Sets the Safety Center device config flag to the given boolean [value]. */
    fun setSafetyCenterEnabled(value: Boolean) {
        callWithShellPermissionIdentity(
            {
                val valueWasSet =
                    DeviceConfig.setProperty(
                        DeviceConfig.NAMESPACE_PRIVACY,
                        PROPERTY_SAFETY_CENTER_ENABLED,
                        /* value = */ value.toString(),
                        /* makeDefault = */ false)
                if (!valueWasSet) {
                    throw IllegalStateException("Could not set Safety Center flag value to: $value")
                }
            },
            WRITE_DEVICE_CONFIG)
    }
}
