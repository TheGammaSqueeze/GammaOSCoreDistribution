/*
 * Copyright 2022 The Android Open Source Project
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

package platform.test.screenshot

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.screenshot.Screenshot
import com.android.internal.app.SimpleIconFactory
import org.junit.rules.TestRule
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runners.model.Statement
import platform.test.screenshot.matchers.BitmapMatcher
import platform.test.screenshot.matchers.MSSIMMatcher
import platform.test.screenshot.matchers.PixelPerfectMatcher
import platform.test.screenshot.proto.ScreenshotResultProto
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

/**
 * Rule to be added to a test to facilitate screenshot testing.
 *
 * This rule records current test name and when instructed it will perform the given bitmap
 * comparison against the given golden. All the results (including result proto file) are stored
 * into the device to be retrieved later.
 *
 * @param config To configure where this rule should look for goldens.
 * @param outputRootDir The root directory for output files.
 *
 * @see Bitmap.assertAgainstGolden
 */
@SuppressLint("SyntheticAccessor")
open class ScreenshotTestRule(
    val goldenImagePathManager: GoldenImagePathManager
) : TestRule {

    private val imageExtension = ".png"
    private val resultBinaryProtoFileSuffix = "goldResult.pb"
    // This is used in CI to identify the files.
    private val resultProtoFileSuffix = "goldResult.textproto"

    // Magic number for an in-progress status report
    private val bundleStatusInProgress = 2
    private val bundleKeyPrefix = "platform_screenshots_"

    private lateinit var testIdentifier: String
    private lateinit var deviceId: String

    private val testWatcher = object : TestWatcher() {
        override fun starting(description: Description?) {
            testIdentifier = "${description!!.className}_${description.methodName}"
        }
    }

    override fun apply(base: Statement, description: Description?): Statement {
        return ScreenshotTestStatement(base)
            .run { testWatcher.apply(this, description) }
    }

    class ScreenshotTestStatement(private val base: Statement) : Statement() {
        override fun evaluate() {
            try {
                SimpleIconFactory.setPoolEnabled(false)
                base.evaluate()
            } finally {
                SimpleIconFactory.setPoolEnabled(true)
            }
        }
    }

    private fun fetchExpectedImage(goldenIdentifier: String): Bitmap? {
        val instrument = InstrumentationRegistry.getInstrumentation()
        return listOf(
                instrument.targetContext.applicationContext,
                instrument.context
        ).map {
            try {
                it.assets.open(
                    goldenImagePathManager.goldenIdentifierResolver(goldenIdentifier)
                ).use {
                    return@use BitmapFactory.decodeStream(it)
                }
            } catch (e: FileNotFoundException) {
                return@map null
            }
        }.filterNotNull().firstOrNull()
    }

    /**
     * Asserts the given bitmap against the golden identified by the given name.
     *
     * Note: The golden identifier should be unique per your test module (unless you want multiple
     * tests to match the same golden). The name must not contain extension. You should also avoid
     * adding strings like "golden", "image" and instead describe what is the golder referring to.
     *
     * @param actual The bitmap captured during the test.
     * @param goldenIdentifier Name of the golden. Allowed characters: 'A-Za-z0-9_-'
     * @param matcher The algorithm to be used to perform the matching.
     *
     * @see MSSIMMatcher
     * @see PixelPerfectMatcher
     * @see Bitmap.assertAgainstGolden
     *
     * @throws IllegalArgumentException If the golden identifier contains forbidden characters or
     * is empty.
     */
    @Deprecated("use the ScreenshotTestRuleAsserter")
    public fun assertBitmapAgainstGolden(
        actual: Bitmap,
        goldenIdentifier: String,
        matcher: BitmapMatcher
    ) {
        assertBitmapAgainstGolden(
            actual = actual,
            goldenIdentifier = goldenIdentifier,
            matcher = matcher,
            regions = emptyList<Rect>()
        )
    }

    /**
     * Asserts the given bitmap against the golden identified by the given name.
     *
     * Note: The golden identifier should be unique per your test module (unless you want multiple
     * tests to match the same golden). The name must not contain extension. You should also avoid
     * adding strings like "golden", "image" and instead describe what is the golder referring to.
     *
     * @param actual The bitmap captured during the test.
     * @param goldenIdentifier Name of the golden. Allowed characters: 'A-Za-z0-9_-'
     * @param matcher The algorithm to be used to perform the matching.
     * @param regions An optional array of interesting regions for partial screenshot diff.
     *
     * @see MSSIMMatcher
     * @see PixelPerfectMatcher
     * @see Bitmap.assertAgainstGolden
     *
     * @throws IllegalArgumentException If the golden identifier contains forbidden characters or
     * is empty.
     */
    @Deprecated("use the ScreenshotTestRuleAsserter")
    public fun assertBitmapAgainstGolden(
        actual: Bitmap,
        goldenIdentifier: String,
        matcher: BitmapMatcher,
        regions: List<Rect>
    ) {
        if (!goldenIdentifier.matches("^[A-Za-z0-9_-]+$".toRegex())) {
            throw IllegalArgumentException(
                "The given golden identifier '$goldenIdentifier' does not satisfy the naming " +
                    "requirement. Allowed characters are: '[A-Za-z0-9_-]'"
            )
        }

        val expected = fetchExpectedImage(goldenIdentifier)
        if (expected == null) {
            reportResult(
                status = ScreenshotResultProto.DiffResult.Status.MISSING_REFERENCE,
                assetsPathRelativeToRepo = goldenImagePathManager.assetsPathRelativeToBuildRoot,
                goldenIdentifier = goldenIdentifier,
                actual = actual
            )
            throw AssertionError(
                "Missing golden image " +
                    "'${goldenImagePathManager.goldenIdentifierResolver(goldenIdentifier)}'. " +
                    "Did you mean to check in a new image?"
            )
        }

        if (actual.width != expected.width || actual.height != expected.height) {
            reportResult(
                status = ScreenshotResultProto.DiffResult.Status.FAILED,
                assetsPathRelativeToRepo = goldenImagePathManager.assetsPathRelativeToBuildRoot,
                goldenIdentifier = goldenIdentifier,
                actual = actual,
                expected = expected
            )
            throw AssertionError(
                "Sizes are different! Expected: [${expected.width}, ${expected
                    .height}], Actual: [${actual.width}, ${actual.height}]"
            )
        }

        val comparisonResult = matcher.compareBitmaps(
            expected = expected.toIntArray(),
            given = actual.toIntArray(),
            width = actual.width,
            height = actual.height,
            regions = regions
        )

        val status = if (comparisonResult.matches) {
            ScreenshotResultProto.DiffResult.Status.PASSED
        } else {
            ScreenshotResultProto.DiffResult.Status.FAILED
        }

        reportResult(
            status = status,
            assetsPathRelativeToRepo = goldenImagePathManager.assetsPathRelativeToBuildRoot,
            goldenIdentifier = goldenIdentifier,
            actual = actual,
            comparisonStatistics = comparisonResult.comparisonStatistics,
            expected = highlightedBitmap(expected, regions),
            diff = comparisonResult.diff
        )

        if (!comparisonResult.matches) {
            throw AssertionError(
                "Image mismatch! Comparison stats: '${comparisonResult
                    .comparisonStatistics}'"
            )
        }
    }

    private fun reportResult(
        status: ScreenshotResultProto.DiffResult.Status,
        assetsPathRelativeToRepo: String,
        goldenIdentifier: String,
        actual: Bitmap,
        comparisonStatistics: ScreenshotResultProto.DiffResult.ComparisonStatistics? = null,
        expected: Bitmap? = null,
        diff: Bitmap? = null
    ) {
        val resultProto = ScreenshotResultProto.DiffResult
            .newBuilder()
            .setResultType(status)
            .addMetadata(
                ScreenshotResultProto.Metadata.newBuilder()
                    .setKey("repoRootPath")
                    .setValue(goldenImagePathManager.deviceLocalPath))

        if (comparisonStatistics != null) {
            resultProto.comparisonStatistics = comparisonStatistics
        }

        val pathRelativeToAssets =
            goldenImagePathManager.goldenIdentifierResolver(goldenIdentifier)
        resultProto.imageLocationGolden = "$assetsPathRelativeToRepo/$pathRelativeToAssets"

        val report = Bundle()

        actual.writeToDevice(OutputFileType.IMAGE_ACTUAL, goldenIdentifier).also {
            resultProto.imageLocationTest = it.name
            report.putString(bundleKeyPrefix + OutputFileType.IMAGE_ACTUAL, it.absolutePath)
        }
        diff?.run {
            writeToDevice(OutputFileType.IMAGE_DIFF, goldenIdentifier).also {
                resultProto.imageLocationDiff = it.name
                report.putString(bundleKeyPrefix + OutputFileType.IMAGE_DIFF, it.absolutePath)
            }
        }
        expected?.run {
            writeToDevice(OutputFileType.IMAGE_EXPECTED, goldenIdentifier).also {
                resultProto.imageLocationReference = it.name
                report.putString(
                    bundleKeyPrefix + OutputFileType.IMAGE_EXPECTED,
                    it.absolutePath
                )
            }
        }

        writeToDevice(OutputFileType.RESULT_PROTO, goldenIdentifier) {
            it.write(resultProto.build().toString().toByteArray())
        }.also {
            report.putString(bundleKeyPrefix + OutputFileType.RESULT_PROTO, it.absolutePath)
        }

        writeToDevice(OutputFileType.RESULT_BIN_PROTO, goldenIdentifier) {
            it.write(resultProto.build().toByteArray())
        }.also {
            report.putString(bundleKeyPrefix + OutputFileType.RESULT_BIN_PROTO, it.absolutePath)
        }

        InstrumentationRegistry.getInstrumentation().sendStatus(bundleStatusInProgress, report)
    }

    internal fun getPathOnDeviceFor(fileType: OutputFileType, goldenIdentifier: String): File {
        val imageSuffix = "${goldenImagePathManager}_$goldenIdentifier$imageExtension"
        val fileName = when (fileType) {
            OutputFileType.IMAGE_ACTUAL ->
                "${testIdentifier}_actual_$imageSuffix"
            OutputFileType.IMAGE_EXPECTED ->
                "${testIdentifier}_expected_$imageSuffix"
            OutputFileType.IMAGE_DIFF ->
                "${testIdentifier}_diff_$imageSuffix"
            OutputFileType.RESULT_PROTO ->
                "${testIdentifier}_${goldenIdentifier}_$resultProtoFileSuffix"
            OutputFileType.RESULT_BIN_PROTO ->
                "${testIdentifier}_${goldenIdentifier}_$resultBinaryProtoFileSuffix"
        }
        return File(goldenImagePathManager.deviceLocalPath, fileName)
    }

    private fun Bitmap.writeToDevice(fileType: OutputFileType, goldenIdentifier: String): File {
        return writeToDevice(fileType, goldenIdentifier) {
            compress(Bitmap.CompressFormat.PNG, 0 /*ignored for png*/, it)
        }
    }

    private fun writeToDevice(
        fileType: OutputFileType,
        goldenIdentifier: String,
        writeAction: (FileOutputStream) -> Unit
    ): File {
        val fileGolden = File(goldenImagePathManager.deviceLocalPath)
        if (!fileGolden.exists() && !fileGolden.mkdir()) {
            throw IOException("Could not create folder $fileGolden.")
        }

        var file = getPathOnDeviceFor(fileType, goldenIdentifier)
        try {
            FileOutputStream(file).use {
                writeAction(it)
            }
        } catch (e: Exception) {
            throw IOException(
                "Could not write file to storage (path: ${file.absolutePath}). " +
                    " Stacktrace: " + e.stackTrace
            )
        }
        return file
    }

    private fun colorPixel(
        bitmapArray: IntArray,
        width: Int,
        height: Int,
        row: Int,
        column: Int,
        extra: Int,
        colorForHighlight: Int
    ) {
        val startRow = if (row - extra < 0) { 0 } else { row - extra }
        val endRow = if (row + extra >= height) { height - 1 } else { row + extra }
        val startColumn = if (column - extra < 0) { 0 } else { column - extra }
        val endColumn = if (column + extra >= width) { width - 1 } else { column + extra }
        for (i in startRow..endRow) {
            for (j in startColumn..endColumn) {
                bitmapArray[j + i * width] = colorForHighlight
            }
        }
    }

    private fun highlightedBitmap(original: Bitmap?, regions: List<Rect>): Bitmap? {
        if (original == null || regions.isEmpty()) {
            return original
        }
        val bitmapArray = original.toIntArray()
        val colorForHighlight = Color.argb(255, 255, 0, 0)
        for (region in regions) {
            for (i in region.top..region.bottom) {
                if (i >= original.height) { break }
                colorPixel(
                    bitmapArray,
                    original.width,
                    original.height,
                    i,
                    region.left,
                    /* extra= */2,
                    colorForHighlight
                )
                colorPixel(
                    bitmapArray,
                    original.width,
                    original.height,
                    i,
                    region.right,
                    /* extra= */2,
                    colorForHighlight
                )
            }
            for (j in region.left..region.right) {
                if (j >= original.width) { break }
                colorPixel(
                    bitmapArray,
                    original.width,
                    original.height,
                    region.top,
                    j,
                    /* extra= */2,
                    colorForHighlight
                )
                colorPixel(
                    bitmapArray,
                    original.width,
                    original.height,
                    region.bottom,
                    j,
                    /* extra= */2,
                    colorForHighlight
                )
            }
        }
        return Bitmap.createBitmap(bitmapArray, original.width, original.height, original.config)
    }
}

typealias BitmapSupplier = () -> Bitmap

/**
 * Implements a screenshot asserter based on the ScreenshotRule
 */
class ScreenshotRuleAsserter private constructor(private val rule: ScreenshotTestRule) : ScreenshotAsserter {
    // use the most constraining matcher as default
    private var matcher: BitmapMatcher = PixelPerfectMatcher()
    private var beforeScreenshot: Runnable? = null
    private var afterScreenshot: Runnable? = null
    // use the instrumentation screenshot as default
    private var screenShotter: BitmapSupplier = { Screenshot.capture().bitmap }
    override fun assertGoldenImage(goldenId: String) {
        beforeScreenshot?.run();
        try {
            rule.assertBitmapAgainstGolden(screenShotter(), goldenId, matcher)
        }
        finally {
            afterScreenshot?.run();
        }
    }

    override fun assertGoldenImage(goldenId: String, areas: List<Rect>) {
        beforeScreenshot?.run();
        try {
            rule.assertBitmapAgainstGolden(screenShotter(), goldenId, matcher, areas)
        }
        finally {
            afterScreenshot?.run();
        }
    }

    class Builder(private val rule: ScreenshotTestRule) {
        private var asserter = ScreenshotRuleAsserter(rule)
        fun withMatcher(matcher: BitmapMatcher): Builder {
            asserter.matcher = matcher
            return this
        }

        fun setScreenshotProvider(screenshotProvider: BitmapSupplier): Builder {
            asserter.screenShotter = screenshotProvider
            return this
        }

        fun setOnBeforeScreenshot(run: Runnable): Builder {
            asserter.beforeScreenshot = run
            return this
        }

        fun setOnAfterScreenshot(run: Runnable): Builder {
            asserter.afterScreenshot = run
            return this
        }

        fun build(): ScreenshotAsserter {
            val built = asserter
            asserter = ScreenshotRuleAsserter(rule)
            return built
        }
    }
}

internal fun Bitmap.toIntArray(): IntArray {
    val bitmapArray = IntArray(width * height)
    getPixels(bitmapArray, 0, width, 0, 0, width, height)
    return bitmapArray
}

/**
 * Asserts this bitmap against the golden identified by the given name.
 *
 * Note: The golden identifier should be unique per your test module (unless you want multiple tests
 * to match the same golden). The name must not contain extension. You should also avoid adding
 * strings like "golden", "image" and instead describe what is the golder referring to.
 *
 * @param rule The screenshot test rule that provides the comparison and reporting.
 * @param goldenIdentifier Name of the golden. Allowed characters: 'A-Za-z0-9_-'
 * @param matcher The algorithm to be used to perform the matching. By default [MSSIMMatcher]
 * is used.
 *
 * @see MSSIMMatcher
 * @see PixelPerfectMatcher
 */
fun Bitmap.assertAgainstGolden(
    rule: ScreenshotTestRule,
    goldenIdentifier: String,
    matcher: BitmapMatcher = MSSIMMatcher(),
    regions: List<Rect> = emptyList<Rect>()
) {
    rule.assertBitmapAgainstGolden(this, goldenIdentifier, matcher = matcher, regions = regions)
}

/**
 * Type of file that can be produced by the [ScreenshotTestRule].
 */
internal enum class OutputFileType {
    IMAGE_ACTUAL,
    IMAGE_EXPECTED,
    IMAGE_DIFF,
    RESULT_PROTO,
    RESULT_BIN_PROTO
}
