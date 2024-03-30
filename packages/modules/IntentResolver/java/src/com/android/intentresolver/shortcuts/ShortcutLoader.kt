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
package com.android.intentresolver.shortcuts

import android.app.ActivityManager
import android.app.prediction.AppPredictor
import android.app.prediction.AppTarget
import android.content.ComponentName
import android.content.Context
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.pm.ShortcutManager.ShareShortcutInfo
import android.os.AsyncTask
import android.os.UserHandle
import android.os.UserManager
import android.service.chooser.ChooserTarget
import android.text.TextUtils
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.OpenForTesting
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.android.intentresolver.chooser.DisplayResolveInfo
import java.lang.RuntimeException
import java.util.ArrayList
import java.util.HashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

/**
 * Encapsulates shortcuts loading logic from either AppPredictor or ShortcutManager.
 *
 *
 * A ShortcutLoader instance can be viewed as a per-profile singleton hot stream of shortcut
 * updates. The shortcut loading is triggered by the [queryShortcuts],
 * the processing will happen on the [backgroundExecutor] and the result is delivered
 * through the [callback] on the [callbackExecutor], the main thread.
 *
 *
 * The current version does not improve on the legacy in a way that it does not guarantee that
 * each invocation of the [queryShortcuts] will be matched by an
 * invocation of the callback (there are early terminations of the flow). Also, the fetched
 * shortcuts would be matched against the last known input, i.e. two invocations of
 * [queryShortcuts] may result in two callbacks where shortcuts are
 * processed against the latest input.
 *
 */
@OpenForTesting
open class ShortcutLoader @VisibleForTesting constructor(
    private val context: Context,
    private val appPredictor: AppPredictorProxy?,
    private val userHandle: UserHandle,
    private val isPersonalProfile: Boolean,
    private val targetIntentFilter: IntentFilter?,
    private val backgroundExecutor: Executor,
    private val callbackExecutor: Executor,
    private val callback: Consumer<Result>
) {
    private val shortcutToChooserTargetConverter = ShortcutToChooserTargetConverter()
    private val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    private val activeRequest = AtomicReference(NO_REQUEST)
    private val appPredictorCallback = AppPredictor.Callback { onAppPredictorCallback(it) }
    private var isDestroyed = false

    @MainThread
    constructor(
        context: Context,
        appPredictor: AppPredictor?,
        userHandle: UserHandle,
        targetIntentFilter: IntentFilter?,
        callback: Consumer<Result>
    ) : this(
        context,
        appPredictor?.let { AppPredictorProxy(it) },
        userHandle, userHandle == UserHandle.of(ActivityManager.getCurrentUser()),
        targetIntentFilter,
        AsyncTask.SERIAL_EXECUTOR,
        context.mainExecutor,
        callback
    )

    init {
        appPredictor?.registerPredictionUpdates(callbackExecutor, appPredictorCallback)
    }

    /**
     * Unsubscribe from app predictor if one was provided.
     */
    @OpenForTesting
    @MainThread
    open fun destroy() {
        isDestroyed = true
        appPredictor?.unregisterPredictionUpdates(appPredictorCallback)
    }

    /**
     * Set new resolved targets. This will trigger shortcut loading.
     * @param appTargets a collection of application targets a loaded set of shortcuts will be
     * grouped against
     */
    @OpenForTesting
    @MainThread
    open fun queryShortcuts(appTargets: Array<DisplayResolveInfo>) {
        if (isDestroyed) return
        activeRequest.set(Request(appTargets))
        backgroundExecutor.execute { loadShortcuts() }
    }

    @WorkerThread
    private fun loadShortcuts() {
        // no need to query direct share for work profile when its locked or disabled
        if (!shouldQueryDirectShareTargets()) return
        Log.d(TAG, "querying direct share targets")
        queryDirectShareTargets(false)
    }

    @WorkerThread
    private fun queryDirectShareTargets(skipAppPredictionService: Boolean) {
        if (!skipAppPredictionService && appPredictor != null) {
            appPredictor.requestPredictionUpdate()
            return
        }
        // Default to just querying ShortcutManager if AppPredictor not present.
        if (targetIntentFilter == null) return
        val shortcuts = queryShortcutManager(targetIntentFilter)
        sendShareShortcutInfoList(shortcuts, false, null)
    }

    @WorkerThread
    private fun queryShortcutManager(targetIntentFilter: IntentFilter): List<ShareShortcutInfo> {
        val selectedProfileContext = context.createContextAsUser(userHandle, 0 /* flags */)
        val sm = selectedProfileContext
            .getSystemService(Context.SHORTCUT_SERVICE) as ShortcutManager?
        val pm = context.createContextAsUser(userHandle, 0 /* flags */).packageManager
        return sm?.getShareTargets(targetIntentFilter)
            ?.filter { pm.isPackageEnabled(it.targetComponent.packageName) }
            ?: emptyList()
    }

    @WorkerThread
    private fun onAppPredictorCallback(appPredictorTargets: List<AppTarget>) {
        if (appPredictorTargets.isEmpty() && shouldQueryDirectShareTargets()) {
            // APS may be disabled, so try querying targets ourselves.
            queryDirectShareTargets(true)
            return
        }
        val pm = context.createContextAsUser(userHandle, 0).packageManager
        val pair = appPredictorTargets.toShortcuts(pm)
        sendShareShortcutInfoList(pair.shortcuts, true, pair.appTargets)
    }

    @WorkerThread
    private fun List<AppTarget>.toShortcuts(pm: PackageManager): ShortcutsAppTargetsPair =
        fold(
            ShortcutsAppTargetsPair(ArrayList(size), ArrayList(size))
        ) { acc, appTarget ->
            val shortcutInfo = appTarget.shortcutInfo
            val packageName = appTarget.packageName
            val className = appTarget.className
            if (shortcutInfo != null && className != null && pm.isPackageEnabled(packageName)) {
                (acc.shortcuts as ArrayList<ShareShortcutInfo>).add(
                    ShareShortcutInfo(shortcutInfo, ComponentName(packageName, className))
                )
                (acc.appTargets as ArrayList<AppTarget>).add(appTarget)
            }
            acc
        }

    @WorkerThread
    private fun sendShareShortcutInfoList(
        shortcuts: List<ShareShortcutInfo>,
        isFromAppPredictor: Boolean,
        appPredictorTargets: List<AppTarget>?
    ) {
        if (appPredictorTargets != null && appPredictorTargets.size != shortcuts.size) {
            throw RuntimeException(
                "resultList and appTargets must have the same size."
                        + " resultList.size()=" + shortcuts.size
                        + " appTargets.size()=" + appPredictorTargets.size
            )
        }
        val directShareAppTargetCache = HashMap<ChooserTarget, AppTarget>()
        val directShareShortcutInfoCache = HashMap<ChooserTarget, ShortcutInfo>()
        // Match ShareShortcutInfos with DisplayResolveInfos to be able to use the old code path
        // for direct share targets. After ShareSheet is refactored we should use the
        // ShareShortcutInfos directly.
        val appTargets = activeRequest.get().appTargets
        val resultRecords: MutableList<ShortcutResultInfo> = ArrayList()
        for (displayResolveInfo in appTargets) {
            val matchingShortcuts = shortcuts.filter {
                it.targetComponent == displayResolveInfo.resolvedComponentName
            }
            if (matchingShortcuts.isEmpty()) continue
            val chooserTargets = shortcutToChooserTargetConverter.convertToChooserTarget(
                matchingShortcuts,
                shortcuts,
                appPredictorTargets,
                directShareAppTargetCache,
                directShareShortcutInfoCache
            )
            val resultRecord = ShortcutResultInfo(displayResolveInfo, chooserTargets)
            resultRecords.add(resultRecord)
        }
        postReport(
            Result(
                isFromAppPredictor,
                appTargets,
                resultRecords.toTypedArray(),
                directShareAppTargetCache,
                directShareShortcutInfoCache
            )
        )
    }

    private fun postReport(result: Result) = callbackExecutor.execute { report(result) }

    @MainThread
    private fun report(result: Result) {
        if (isDestroyed) return
        callback.accept(result)
    }

    /**
     * Returns `false` if `userHandle` is the work profile and it's either
     * in quiet mode or not running.
     */
    private fun shouldQueryDirectShareTargets(): Boolean = isPersonalProfile || isProfileActive

    @get:VisibleForTesting
    protected val isProfileActive: Boolean
        get() = userManager.isUserRunning(userHandle)
            && userManager.isUserUnlocked(userHandle)
            && !userManager.isQuietModeEnabled(userHandle)

    private class Request(val appTargets: Array<DisplayResolveInfo>)

    /**
     * Resolved shortcuts with corresponding app targets.
     */
    class Result(
        val isFromAppPredictor: Boolean,
        /**
         * Input app targets (see [ShortcutLoader.queryShortcuts] the
         * shortcuts were process against.
         */
        val appTargets: Array<DisplayResolveInfo>,
        /**
         * Shortcuts grouped by app target.
         */
        val shortcutsByApp: Array<ShortcutResultInfo>,
        val directShareAppTargetCache: Map<ChooserTarget, AppTarget>,
        val directShareShortcutInfoCache: Map<ChooserTarget, ShortcutInfo>
    )

    /**
     * Shortcuts grouped by app.
     */
    class ShortcutResultInfo(
        val appTarget: DisplayResolveInfo,
        val shortcuts: List<ChooserTarget?>
    )

    private class ShortcutsAppTargetsPair(
        val shortcuts: List<ShareShortcutInfo>,
        val appTargets: List<AppTarget>?
    )

    /**
     * A wrapper around AppPredictor to facilitate unit-testing.
     */
    @VisibleForTesting
    open class AppPredictorProxy internal constructor(private val mAppPredictor: AppPredictor) {
        /**
         * [AppPredictor.registerPredictionUpdates]
         */
        open fun registerPredictionUpdates(
            callbackExecutor: Executor, callback: AppPredictor.Callback
        ) = mAppPredictor.registerPredictionUpdates(callbackExecutor, callback)

        /**
         * [AppPredictor.unregisterPredictionUpdates]
         */
        open fun unregisterPredictionUpdates(callback: AppPredictor.Callback) =
            mAppPredictor.unregisterPredictionUpdates(callback)

        /**
         * [AppPredictor.requestPredictionUpdate]
         */
        open fun requestPredictionUpdate() = mAppPredictor.requestPredictionUpdate()
    }

    companion object {
        private const val TAG = "ShortcutLoader"
        private val NO_REQUEST = Request(arrayOf())

        private fun PackageManager.isPackageEnabled(packageName: String): Boolean {
            if (TextUtils.isEmpty(packageName)) {
                return false
            }
            return runCatching {
                val appInfo = getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                )
                appInfo.enabled && (appInfo.flags and ApplicationInfo.FLAG_SUSPENDED) == 0
            }.getOrDefault(false)
        }
    }
}
