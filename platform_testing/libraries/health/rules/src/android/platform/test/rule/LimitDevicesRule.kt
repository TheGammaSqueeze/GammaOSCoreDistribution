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
package android.platform.test.rule

import android.os.Build
import android.platform.test.rule.DeviceProduct.CF_PHONE
import android.platform.test.rule.DeviceProduct.CF_TABLET
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
import org.junit.AssumptionViolatedException
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Limits the test to run on devices specified by [allowed], */
@Retention(RUNTIME)
@Target(FUNCTION, CLASS)
annotation class AllowedDevices(vararg val allowed: DeviceProduct)

/** Does not run the test on device specified by [denied], */
@Retention(RUNTIME)
@Target(FUNCTION, CLASS)
annotation class DeniedDevices(vararg val denied: DeviceProduct)

/** Limits the test on default screenshot devices, or [allowed] devices if specified. */
@Retention(RUNTIME)
@Target(FUNCTION, CLASS)
annotation class ScreenshotTestDevices(vararg val allowed: DeviceProduct = [CF_PHONE, CF_TABLET])

/**
 * Limits a test to run specified devices.
 *
 * Devices are specified by [AllowedDevices], [DeniedDevices] and [ScreenshotTestDevices]
 * annotations. Only one annotation per test is supported. Values are matched against [thisDevice].
 *
 * NOTE: It's not encouraged to use this to filter if it's possible to filter based on other device
 * characteristics. For example, to run a test only only on large screens or foldable,
 * [DeviceTypeRule] is encouraged. This rule should **never** be used to avoid running a test on a
 * tablet when the test is broken.
 */
class LimitDevicesRule(private val thisDevice: String = Build.PRODUCT) : TestRule {

    override fun apply(base: Statement, description: Description): Statement {
        val limitDevicesAnnotations = description.limitDevicesAnnotation()
        if (limitDevicesAnnotations.count() > 1) {
            return makeErrorStatement(
                "Only one LimitDeviceRule annotation is supported. Found $limitDevicesAnnotations"
            )
        }
        val deniedDevices = description.deniedDevices() ?: emptyList()
        if (thisDevice in deniedDevices) {
            return makeErrorStatement("Skipping test as $thisDevice in in $deniedDevices")
        }
        val allowedDevices = description.allowedDevices() ?: return base
        return when (thisDevice) {
            in allowedDevices -> base
            else -> makeErrorStatement("Skipping test as $thisDevice in not in $allowedDevices")
        }
    }

    private fun Description.allowedDevices(): List<String>? {
        return getAnnotation(AllowedDevices::class.java)?.allowed?.map { it.product }
            ?: getAnnotation(ScreenshotTestDevices::class.java)?.allowed?.map { it.product }
    }

    private fun Description.deniedDevices(): List<String>? =
        getAnnotation(DeniedDevices::class.java)?.denied?.map { it.product }

    private fun Description.limitDevicesAnnotation(): List<Annotation> =
        listOfNotNull(
            getAnnotation(AllowedDevices::class.java),
            getAnnotation(DeniedDevices::class.java),
            getAnnotation(ScreenshotTestDevices::class.java)
        )
}

enum class DeviceProduct(val product: String) {
    CF_PHONE("cf_x86_64_phone"),
    CF_TABLET("cf_x86_64_tablet")
}

private fun makeErrorStatement(errorMessage: String): Statement =
    object : Statement() {
        override fun evaluate() {
            throw AssumptionViolatedException(errorMessage)
        }
    }
