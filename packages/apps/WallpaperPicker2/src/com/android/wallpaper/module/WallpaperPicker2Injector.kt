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
package com.android.wallpaper.module

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import com.android.wallpaper.compat.WallpaperManagerCompat
import com.android.wallpaper.config.BaseFlags
import com.android.wallpaper.effects.EffectsController
import com.android.wallpaper.model.CategoryProvider
import com.android.wallpaper.model.LiveWallpaperInfo
import com.android.wallpaper.model.WallpaperColorsViewModel
import com.android.wallpaper.model.WallpaperInfo
import com.android.wallpaper.monitor.PerformanceMonitor
import com.android.wallpaper.network.Requester
import com.android.wallpaper.network.WallpaperRequester
import com.android.wallpaper.picker.CustomizationPickerActivity
import com.android.wallpaper.picker.ImagePreviewFragment
import com.android.wallpaper.picker.LivePreviewFragment
import com.android.wallpaper.picker.PreviewFragment
import com.android.wallpaper.picker.customization.data.content.WallpaperClientImpl
import com.android.wallpaper.picker.customization.data.repository.WallpaperRepository
import com.android.wallpaper.picker.customization.domain.interactor.WallpaperInteractor
import com.android.wallpaper.picker.customization.domain.interactor.WallpaperSnapshotRestorer
import com.android.wallpaper.picker.individual.IndividualPickerFragment
import com.android.wallpaper.picker.undo.data.repository.UndoRepository
import com.android.wallpaper.picker.undo.domain.interactor.UndoInteractor
import com.android.wallpaper.settings.data.repository.SecureSettingsRepository
import com.android.wallpaper.settings.data.repository.SecureSettingsRepositoryImpl
import com.android.wallpaper.util.DisplayUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope

open class WallpaperPicker2Injector() : Injector {
    private var alarmManagerWrapper: AlarmManagerWrapper? = null
    private var bitmapCropper: BitmapCropper? = null
    private var categoryProvider: CategoryProvider? = null
    private var currentWallpaperFactory: CurrentWallpaperInfoFactory? = null
    private var customizationSections: CustomizationSections? = null
    private var displayUtils: DisplayUtils? = null
    private var drawableLayerResolver: DrawableLayerResolver? = null
    private var exploreIntentChecker: ExploreIntentChecker? = null
    private var liveWallpaperInfoFactory: LiveWallpaperInfoFactory? = null
    private var networkStatusNotifier: NetworkStatusNotifier? = null
    private var packageStatusNotifier: PackageStatusNotifier? = null
    private var partnerProvider: PartnerProvider? = null
    private var performanceMonitor: PerformanceMonitor? = null
    private var requester: Requester? = null
    private var systemFeatureChecker: SystemFeatureChecker? = null
    private var userEventLogger: UserEventLogger? = null
    private var wallpaperManagerCompat: WallpaperManagerCompat? = null
    private var wallpaperPersister: WallpaperPersister? = null
    private var prefs: WallpaperPreferences? = null
    private var wallpaperPreviewFragmentManager: WallpaperPreviewFragmentManager? = null
    private var wallpaperRefresher: WallpaperRefresher? = null
    private var wallpaperRotationRefresher: WallpaperRotationRefresher? = null
    private var wallpaperStatusChecker: WallpaperStatusChecker? = null
    private var flags: BaseFlags? = null
    private var undoInteractor: UndoInteractor? = null
    private var wallpaperInteractor: WallpaperInteractor? = null
    private var wallpaperSnapshotRestorer: WallpaperSnapshotRestorer? = null
    private var secureSettingsRepository: SecureSettingsRepository? = null
    private var wallpaperColorsViewModel: WallpaperColorsViewModel? = null

    @Synchronized
    override fun getAlarmManagerWrapper(context: Context): AlarmManagerWrapper {
        return alarmManagerWrapper
            ?: DefaultAlarmManagerWrapper(context.applicationContext).also {
                alarmManagerWrapper = it
            }
    }

    @Synchronized
    override fun getBitmapCropper(): BitmapCropper {
        return bitmapCropper ?: DefaultBitmapCropper().also { bitmapCropper = it }
    }

    override fun getCategoryProvider(context: Context): CategoryProvider {
        return categoryProvider
            ?: DefaultCategoryProvider(context.applicationContext).also { categoryProvider = it }
    }

    @Synchronized
    override fun getCurrentWallpaperInfoFactory(context: Context): CurrentWallpaperInfoFactory {
        return currentWallpaperFactory
            ?: DefaultCurrentWallpaperInfoFactory(context.applicationContext).also {
                currentWallpaperFactory = it
            }
    }

    override fun getCustomizationSections(activity: ComponentActivity): CustomizationSections {
        return customizationSections
            ?: WallpaperPickerSections().also { customizationSections = it }
    }

    override fun getDeepLinkRedirectIntent(context: Context, uri: Uri): Intent {
        val intent = Intent()
        intent.setClass(context, CustomizationPickerActivity::class.java)
        intent.data = uri
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        return intent
    }

    override fun getDisplayUtils(context: Context): DisplayUtils {
        return displayUtils ?: DisplayUtils(context.applicationContext).also { displayUtils = it }
    }

    override fun getDownloadableIntentAction(): String? {
        return null
    }

    override fun getDrawableLayerResolver(): DrawableLayerResolver {
        return drawableLayerResolver
            ?: DefaultDrawableLayerResolver().also { drawableLayerResolver = it }
    }

    override fun getEffectsController(
        context: Context,
    ): EffectsController? {
        return null
    }

    @Synchronized
    override fun getExploreIntentChecker(context: Context): ExploreIntentChecker {
        return exploreIntentChecker
            ?: DefaultExploreIntentChecker(context.applicationContext).also {
                exploreIntentChecker = it
            }
    }

    override fun getIndividualPickerFragment(context: Context, collectionId: String): Fragment {
        return IndividualPickerFragment.newInstance(collectionId)
    }

    override fun getLiveWallpaperInfoFactory(context: Context): LiveWallpaperInfoFactory {
        return liveWallpaperInfoFactory
            ?: DefaultLiveWallpaperInfoFactory().also { liveWallpaperInfoFactory = it }
    }

    @Synchronized
    override fun getNetworkStatusNotifier(context: Context): NetworkStatusNotifier {
        return networkStatusNotifier
            ?: DefaultNetworkStatusNotifier(context.applicationContext).also {
                networkStatusNotifier = it
            }
    }

    @Synchronized
    override fun getPackageStatusNotifier(context: Context): PackageStatusNotifier {
        return packageStatusNotifier
            ?: DefaultPackageStatusNotifier(context.applicationContext).also {
                packageStatusNotifier = it
            }
    }

    @Synchronized
    override fun getPartnerProvider(context: Context): PartnerProvider {
        return partnerProvider
            ?: DefaultPartnerProvider(context.applicationContext).also { partnerProvider = it }
    }

    @Synchronized
    override fun getPerformanceMonitor(): PerformanceMonitor? {

        return performanceMonitor
            ?: PerformanceMonitor {
                    /** No Op */
                }
                .also { performanceMonitor = it }
    }

    override fun getPreviewFragment(
        context: Context,
        wallpaperInfo: WallpaperInfo,
        mode: Int,
        viewAsHome: Boolean,
        viewFullScreen: Boolean,
        testingModeEnabled: Boolean
    ): Fragment {
        val args = Bundle()
        args.putParcelable(PreviewFragment.ARG_WALLPAPER, wallpaperInfo)
        args.putInt(PreviewFragment.ARG_PREVIEW_MODE, mode)
        args.putBoolean(PreviewFragment.ARG_VIEW_AS_HOME, viewAsHome)
        args.putBoolean(PreviewFragment.ARG_FULL_SCREEN, viewFullScreen)
        args.putBoolean(PreviewFragment.ARG_TESTING_MODE_ENABLED, testingModeEnabled)
        val fragment =
            if (wallpaperInfo is LiveWallpaperInfo) LivePreviewFragment()
            else ImagePreviewFragment()
        fragment.arguments = args
        return fragment
    }

    @Synchronized
    override fun getRequester(context: Context): Requester {
        return requester ?: WallpaperRequester(context.applicationContext).also { requester = it }
    }

    @Synchronized
    override fun getSystemFeatureChecker(): SystemFeatureChecker {
        return systemFeatureChecker
            ?: DefaultSystemFeatureChecker().also { systemFeatureChecker = it }
    }

    override fun getUserEventLogger(context: Context): UserEventLogger {
        return userEventLogger ?: NoOpUserEventLogger().also { userEventLogger = it }
    }

    @Synchronized
    override fun getWallpaperManagerCompat(context: Context): WallpaperManagerCompat {
        return wallpaperManagerCompat
            ?: WallpaperManagerCompat.getInstance(context).also { wallpaperManagerCompat = it }
    }

    @Synchronized
    override fun getWallpaperPersister(context: Context): WallpaperPersister {
        return wallpaperPersister
            ?: DefaultWallpaperPersister(context.applicationContext).also {
                wallpaperPersister = it
            }
    }

    @Synchronized
    override fun getPreferences(context: Context): WallpaperPreferences {
        return prefs ?: DefaultWallpaperPreferences(context.applicationContext).also { prefs = it }
    }

    @Synchronized
    override fun getWallpaperPreviewFragmentManager(): WallpaperPreviewFragmentManager {
        return wallpaperPreviewFragmentManager
            ?: DefaultWallpaperPreviewFragmentManager().also {
                wallpaperPreviewFragmentManager = it
            }
    }

    @Synchronized
    override fun getWallpaperRefresher(context: Context): WallpaperRefresher {
        return wallpaperRefresher
            ?: DefaultWallpaperRefresher(context.applicationContext).also {
                wallpaperRefresher = it
            }
    }

    @Synchronized
    override fun getWallpaperRotationRefresher(): WallpaperRotationRefresher {
        return wallpaperRotationRefresher
            ?: WallpaperRotationRefresher { _, listener ->
                    // Not implemented
                    listener.onError()
                }
                .also { wallpaperRotationRefresher = it }
    }

    override fun getWallpaperStatusChecker(): WallpaperStatusChecker {
        return wallpaperStatusChecker
            ?: DefaultWallpaperStatusChecker().also { wallpaperStatusChecker = it }
    }

    override fun getFlags(): BaseFlags {
        return flags ?: object : BaseFlags() {}.also { flags = it }
    }

    override fun getUndoInteractor(context: Context): UndoInteractor {
        return undoInteractor
            ?: UndoInteractor(GlobalScope, UndoRepository(), getSnapshotRestorers(context)).also {
                undoInteractor = it
            }
    }

    override fun getWallpaperInteractor(context: Context): WallpaperInteractor {
        return wallpaperInteractor
            ?: WallpaperInteractor(
                    repository =
                        WallpaperRepository(
                            scope = GlobalScope,
                            client = WallpaperClientImpl(context = context),
                            backgroundDispatcher = Dispatchers.IO,
                        ),
                )
                .also { wallpaperInteractor = it }
    }

    override fun getWallpaperSnapshotRestorer(context: Context): WallpaperSnapshotRestorer {
        return wallpaperSnapshotRestorer
            ?: WallpaperSnapshotRestorer(
                    scope = GlobalScope,
                    interactor = getWallpaperInteractor(context),
                )
                .also { wallpaperSnapshotRestorer = it }
    }

    protected fun getSecureSettingsRepository(context: Context): SecureSettingsRepository {
        return secureSettingsRepository
            ?: SecureSettingsRepositoryImpl(
                    contentResolver = context.contentResolver,
                    backgroundDispatcher = Dispatchers.IO,
                )
                .also { secureSettingsRepository = it }
    }

    override fun getWallpaperColorsViewModel(): WallpaperColorsViewModel {
        return wallpaperColorsViewModel
            ?: WallpaperColorsViewModel().also { wallpaperColorsViewModel = it }
    }

    companion object {
        /**
         * When this injector is overridden, this is the minimal value that should be used by
         * restorers returns in [getSnapshotRestorers].
         */
        @JvmStatic protected val MIN_SNAPSHOT_RESTORER_KEY = 0
    }
}
