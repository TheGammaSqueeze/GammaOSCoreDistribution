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
package com.android.wallpaper.picker.individual

import android.app.Activity
import android.app.ProgressDialog
import android.app.WallpaperManager
import android.content.DialogInterface
import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.Resources.ID_NULL
import android.graphics.Point
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.service.wallpaper.WallpaperService
import android.text.TextUtils
import android.util.ArraySet
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.cardview.widget.CardView
import androidx.core.widget.ContentLoadingProgressBar
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.wallpaper.R
import com.android.wallpaper.model.Category
import com.android.wallpaper.model.CategoryProvider
import com.android.wallpaper.model.CategoryReceiver
import com.android.wallpaper.model.LiveWallpaperInfo
import com.android.wallpaper.model.WallpaperCategory
import com.android.wallpaper.model.WallpaperInfo
import com.android.wallpaper.model.WallpaperRotationInitializer
import com.android.wallpaper.model.WallpaperRotationInitializer.NetworkPreference
import com.android.wallpaper.module.InjectorProvider
import com.android.wallpaper.module.PackageStatusNotifier
import com.android.wallpaper.picker.AppbarFragment
import com.android.wallpaper.picker.FragmentTransactionChecker
import com.android.wallpaper.picker.MyPhotosStarter.MyPhotosStarterProvider
import com.android.wallpaper.picker.RotationStarter
import com.android.wallpaper.picker.StartRotationDialogFragment
import com.android.wallpaper.picker.StartRotationErrorDialogFragment
import com.android.wallpaper.util.ActivityUtils
import com.android.wallpaper.util.DiskBasedLogger
import com.android.wallpaper.util.LaunchUtils
import com.android.wallpaper.util.SizeCalculator
import com.android.wallpaper.widget.GridPaddingDecoration
import com.android.wallpaper.widget.WallpaperPickerRecyclerViewAccessibilityDelegate
import com.android.wallpaper.widget.WallpaperPickerRecyclerViewAccessibilityDelegate.BottomSheetHost
import com.bumptech.glide.Glide
import com.bumptech.glide.MemoryCategory
import java.util.Date
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Displays the Main UI for picking an individual wallpaper image. */
class IndividualPickerFragment2 :
    AppbarFragment(),
    RotationStarter,
    StartRotationErrorDialogFragment.Listener,
    StartRotationDialogFragment.Listener {

    companion object {
        private const val TAG = "IndividualPickerFrag2"

        /**
         * Position of a special tile that doesn't belong to an individual wallpaper of the
         * category, such as "my photos" or "daily rotation".
         */
        private const val SPECIAL_FIXED_TILE_ADAPTER_POSITION = 0

        private const val ARG_CATEGORY_COLLECTION_ID = "category_collection_id"

        private const val UNUSED_REQUEST_CODE = 1
        private const val TAG_START_ROTATION_DIALOG = "start_rotation_dialog"
        private const val TAG_START_ROTATION_ERROR_DIALOG = "start_rotation_error_dialog"
        private const val PROGRESS_DIALOG_INDETERMINATE = true
        private const val KEY_NIGHT_MODE = "IndividualPickerFragment.NIGHT_MODE"
        private const val MAX_CAPACITY_IN_FEWER_COLUMN_LAYOUT = 8
        private val PROGRESS_DIALOG_NO_TITLE = null

        fun newInstance(collectionId: String?): IndividualPickerFragment2 {
            val args = Bundle()
            args.putString(ARG_CATEGORY_COLLECTION_ID, collectionId)
            val fragment = IndividualPickerFragment2()
            fragment.arguments = args
            return fragment
        }
    }

    private lateinit var imageGrid: RecyclerView
    private var adapter: IndividualAdapter? = null
    private var category: WallpaperCategory? = null
    private var wallpaperRotationInitializer: WallpaperRotationInitializer? = null
    private lateinit var items: MutableList<PickerItem>
    private var packageStatusNotifier: PackageStatusNotifier? = null
    private var isWallpapersReceived = false

    private var appStatusListener: PackageStatusNotifier.Listener? = null
    private var progressDialog: ProgressDialog? = null

    private var testingMode = false
    private var loading: ContentLoadingProgressBar? = null
    private var shouldReloadWallpapers = false
    private lateinit var categoryProvider: CategoryProvider
    private var appliedWallpaperIds: Set<String> = setOf()

    /**
     * Staged error dialog fragments that were unable to be shown when the activity didn't allow
     * committing fragment transactions.
     */
    private var stagedStartRotationErrorDialogFragment: StartRotationErrorDialogFragment? = null

    private var wallpaperManager: WallpaperManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val injector = InjectorProvider.getInjector()
        val appContext = requireContext().applicationContext
        wallpaperManager = WallpaperManager.getInstance(appContext)
        packageStatusNotifier = injector.getPackageStatusNotifier(appContext)
        items = ArrayList()

        // Clear Glide's cache if night-mode changed to ensure thumbnails are reloaded
        if (
            savedInstanceState != null &&
                (savedInstanceState.getInt(KEY_NIGHT_MODE) !=
                    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
        ) {
            Glide.get(requireContext()).clearMemory()
        }
        categoryProvider = injector.getCategoryProvider(appContext)
        categoryProvider.fetchCategories(
            object : CategoryReceiver {
                override fun onCategoryReceived(category: Category) {
                    // Do nothing.
                }

                override fun doneFetchingCategories() {
                    val fetchedCategory =
                        categoryProvider.getCategory(
                            arguments?.getString(ARG_CATEGORY_COLLECTION_ID)
                        )
                    if (fetchedCategory != null && fetchedCategory !is WallpaperCategory) {
                        return
                    }

                    if (fetchedCategory == null) {
                        DiskBasedLogger.e(TAG, "Failed to find the category.", context)

                        // The absence of this category in the CategoryProvider indicates a broken
                        // state, see b/38030129. Hence, finish the activity and return.
                        getIndividualPickerFragmentHost().moveToPreviousFragment()
                        Toast.makeText(
                                context,
                                R.string.collection_not_exist_msg,
                                Toast.LENGTH_SHORT
                            )
                            .show()
                        return
                    }
                    category = fetchedCategory as WallpaperCategory
                    category?.let { onCategoryLoaded(it) }
                }
            },
            false
        )
    }

    fun onCategoryLoaded(category: Category) {
        val fragmentHost = getIndividualPickerFragmentHost()
        if (fragmentHost.isHostToolbarShown) {
            fragmentHost.setToolbarTitle(category.title)
        } else {
            setTitle(category.title)
        }
        wallpaperRotationInitializer = category.wallpaperRotationInitializer
        if (mToolbar != null && isRotationEnabled()) {
            setUpToolbarMenu(R.menu.individual_picker_menu)
        }
        var shouldForceReload = false
        if (category.supportsThirdParty()) {
            shouldForceReload = true
        }
        fetchWallpapers(shouldForceReload)
        registerPackageListener(category)
    }

    private fun fetchWallpapers(forceReload: Boolean) {
        items.clear()
        isWallpapersReceived = false
        updateLoading()
        val context = requireContext()
        category?.fetchWallpapers(
            context.applicationContext,
            { fetchedWallpapers ->
                isWallpapersReceived = true
                updateLoading()
                val byGroup = fetchedWallpapers.groupBy { it.getGroupName(context) }
                appliedWallpaperIds = getAppliedWallpaperIds()
                byGroup.forEach { (groupName, wallpapers) ->
                    if (!TextUtils.isEmpty(groupName)) {
                        items.add(
                            if (items.isEmpty()) {
                                PickerItem.FirstHeaderItem(groupName)
                            } else {
                                PickerItem.HeaderItem(groupName)
                            }
                        )
                    }
                    val currentWallpaper = WallpaperManager.getInstance(context).wallpaperInfo
                    items.addAll(
                        wallpapers.map {
                            val isApplied =
                                if (it is LiveWallpaperInfo) {
                                    it.isApplied(currentWallpaper)
                                } else {
                                    appliedWallpaperIds.contains(it.wallpaperId)
                                }
                            PickerItem.WallpaperItem(it, isApplied)
                        }
                    )
                }
                maybeSetUpImageGrid()

                // Wallpapers may load after the adapter is initialized, in which case we have
                // to explicitly notify that the data set has changed.
                adapter?.notifyDataSetChanged()
                if (fetchedWallpapers.isEmpty()) {
                    // If there are no more wallpapers and we're on phone, just finish the
                    // Activity.
                    val activity: Activity? = activity
                    activity?.finish()
                }
            },
            forceReload
        )
    }

    private fun registerPackageListener(category: Category) {
        if (category.supportsThirdParty()) {
            appStatusListener =
                PackageStatusNotifier.Listener { pkgName: String?, status: Int ->
                    if (
                        status != PackageStatusNotifier.PackageStatus.REMOVED ||
                            category.containsThirdParty(pkgName)
                    ) {
                        fetchWallpapers(true)
                    }
                }
            packageStatusNotifier?.addListener(
                appStatusListener,
                WallpaperService.SERVICE_INTERFACE
            )
        }
    }

    private fun updateLoading() {
        if (isWallpapersReceived) {
            loading?.hide()
        } else {
            loading?.show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(
            KEY_NIGHT_MODE,
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view: View = inflater.inflate(R.layout.fragment_individual_picker, container, false)
        if (getIndividualPickerFragmentHost().isHostToolbarShown) {
            view.findViewById<View>(R.id.header_bar).visibility = View.GONE
            setUpArrowEnabled(/* upArrow= */ true)
            if (isRotationEnabled()) {
                getIndividualPickerFragmentHost().setToolbarMenu(R.menu.individual_picker_menu)
            }
        } else {
            setUpToolbar(view)
            if (isRotationEnabled()) {
                setUpToolbarMenu(R.menu.individual_picker_menu)
            }
            setTitle(category?.title)
        }
        imageGrid = view.findViewById<View>(R.id.wallpaper_grid) as RecyclerView
        loading = view.findViewById(R.id.loading_indicator)
        updateLoading()
        maybeSetUpImageGrid()
        // For nav bar edge-to-edge effect.
        imageGrid.setOnApplyWindowInsetsListener { v: View, windowInsets: WindowInsets ->
            v.setPadding(
                v.paddingLeft,
                v.paddingTop,
                v.paddingRight,
                windowInsets.systemWindowInsetBottom
            )
            windowInsets.consumeSystemWindowInsets()
        }
        return view
    }

    private fun getIndividualPickerFragmentHost():
        IndividualPickerFragment.IndividualPickerFragmentHost {
        val parentFragment = parentFragment
        return if (parentFragment != null) {
            parentFragment as IndividualPickerFragment.IndividualPickerFragmentHost
        } else {
            activity as IndividualPickerFragment.IndividualPickerFragmentHost
        }
    }

    private fun maybeSetUpImageGrid() {
        // Skip if mImageGrid been initialized yet
        if (!this::imageGrid.isInitialized) {
            return
        }
        // Skip if category hasn't loaded yet
        if (category == null) {
            return
        }
        if (context == null) {
            return
        }

        // Wallpaper count could change, so we may need to change the layout(2 or 3 columns layout)
        val gridLayoutManager = imageGrid.layoutManager as GridLayoutManager?
        val needUpdateLayout = gridLayoutManager?.spanCount != getNumColumns()

        // Skip if the adapter was already created and don't need to change the layout
        if (adapter != null && !needUpdateLayout) {
            return
        }

        // Clear the old decoration
        val decorationCount = imageGrid.itemDecorationCount
        for (i in 0 until decorationCount) {
            imageGrid.removeItemDecorationAt(i)
        }
        imageGrid.addItemDecoration(
            GridPaddingDecoration(getGridItemPaddingHorizontal(), getGridItemPaddingBottom())
        )
        val edgePadding = getEdgePadding()
        imageGrid.setPadding(
            edgePadding,
            imageGrid.paddingTop,
            edgePadding,
            imageGrid.paddingBottom
        )
        val tileSizePx =
            if (isFewerColumnLayout()) {
                SizeCalculator.getFeaturedIndividualTileSize(activity!!)
            } else {
                SizeCalculator.getIndividualTileSize(activity!!)
            }
        setUpImageGrid(tileSizePx, checkNotNull(category))
        imageGrid.setAccessibilityDelegateCompat(
            WallpaperPickerRecyclerViewAccessibilityDelegate(
                imageGrid,
                parentFragment as BottomSheetHost?,
                getNumColumns()
            )
        )
    }

    private fun isFewerColumnLayout(): Boolean =
        items.count { it is PickerItem.WallpaperItem } <= MAX_CAPACITY_IN_FEWER_COLUMN_LAYOUT

    private fun getGridItemPaddingHorizontal(): Int {
        return if (isFewerColumnLayout()) {
            resources.getDimensionPixelSize(
                R.dimen.grid_item_featured_individual_padding_horizontal
            )
        } else {
            resources.getDimensionPixelSize(R.dimen.grid_item_individual_padding_horizontal)
        }
    }

    private fun getGridItemPaddingBottom(): Int {
        return if (isFewerColumnLayout()) {
            resources.getDimensionPixelSize(R.dimen.grid_item_featured_individual_padding_bottom)
        } else {
            resources.getDimensionPixelSize(R.dimen.grid_item_individual_padding_bottom)
        }
    }

    private fun getEdgePadding(): Int {
        return if (isFewerColumnLayout()) {
            resources.getDimensionPixelSize(R.dimen.featured_wallpaper_grid_edge_space)
        } else {
            resources.getDimensionPixelSize(R.dimen.wallpaper_grid_edge_space)
        }
    }

    /**
     * Create the adapter and assign it to mImageGrid. Both mImageGrid and mCategory are guaranteed
     * to not be null when this method is called.
     */
    private fun setUpImageGrid(tileSizePx: Point, category: Category) {
        adapter =
            IndividualAdapter(
                items,
                category,
                requireActivity(),
                tileSizePx,
                isRotationEnabled(),
                isFewerColumnLayout()
            )
        imageGrid.adapter = adapter
        val gridLayoutManager = GridLayoutManager(activity, getNumColumns())
        gridLayoutManager.spanSizeLookup =
            object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return when (items[position]) {
                        is PickerItem.FirstHeaderItem,
                        is PickerItem.HeaderItem -> {
                            gridLayoutManager.spanCount
                        }
                        else -> 1
                    }
                }
            }
        imageGrid.layoutManager = gridLayoutManager
    }

    private suspend fun fetchWallpapersIfNeeded() {
        coroutineScope {
            if (isWallpapersReceived && (shouldReloadWallpapers || isAppliedWallpaperChanged())) {
                fetchWallpapers(true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val preferences = InjectorProvider.getInjector().getPreferences(requireActivity())
        preferences.lastAppActiveTimestamp = Date().time

        // Reset Glide memory settings to a "normal" level of usage since it may have been lowered
        // in PreviewFragment.
        Glide.get(requireContext()).setMemoryCategory(MemoryCategory.NORMAL)

        // Show the staged 'start rotation' error dialog fragment if there is one that was unable to
        // be shown earlier when this fragment's hosting activity didn't allow committing fragment
        // transactions.
        if (isAdded) {
            stagedStartRotationErrorDialogFragment?.show(
                parentFragmentManager,
                TAG_START_ROTATION_ERROR_DIALOG
            )
            lifecycleScope.launch { fetchWallpapersIfNeeded() }
        }
        stagedStartRotationErrorDialogFragment = null
    }

    override fun onPause() {
        shouldReloadWallpapers = category?.supportsWallpaperSetUpdates() ?: false
        super.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        getIndividualPickerFragmentHost().removeToolbarMenu()
    }

    override fun onDestroy() {
        super.onDestroy()
        progressDialog?.dismiss()
        if (appStatusListener != null) {
            packageStatusNotifier?.removeListener(appStatusListener)
        }
    }

    override fun onStartRotationDialogDismiss(dialog: DialogInterface) {
        // TODO(b/159310028): Refactor fragment layer to make it able to restore from config change.
        // This is to handle config change with StartRotationDialog popup,  the StartRotationDialog
        // still holds a reference to the destroyed Fragment and is calling
        // onStartRotationDialogDismissed on that destroyed Fragment.
    }

    override fun retryStartRotation(@NetworkPreference networkPreference: Int) {
        startRotation(networkPreference)
    }

    /**
     * Enable a test mode of operation -- in which certain UI features are disabled to allow for UI
     * tests to run correctly. Works around issue in ProgressDialog currently where the dialog
     * constantly keeps the UI thread alive and blocks a test forever.
     *
     * @param testingMode
     */
    fun setTestingMode(testingMode: Boolean) {
        this.testingMode = testingMode
    }

    override fun startRotation(@NetworkPreference networkPreference: Int) {
        if (!isRotationEnabled()) {
            Log.e(TAG, "Rotation is not enabled for this category " + category?.title)
            return
        }

        // ProgressDialog endlessly updates the UI thread, keeping it from going idle which
        // therefore causes Espresso to hang once the dialog is shown.
        if (!testingMode) {
            val themeResId =
                if (Build.VERSION.SDK_INT < VERSION_CODES.LOLLIPOP) {
                    R.style.ProgressDialogThemePreL
                } else {
                    R.style.LightDialogTheme
                }
            val progressDialog = ProgressDialog(activity, themeResId)
            progressDialog.setTitle(PROGRESS_DIALOG_NO_TITLE)
            progressDialog.setMessage(resources.getString(R.string.start_rotation_progress_message))
            progressDialog.isIndeterminate = PROGRESS_DIALOG_INDETERMINATE
            progressDialog.show()
            this.progressDialog = progressDialog
        }
        val appContext = activity!!.applicationContext
        wallpaperRotationInitializer?.setFirstWallpaperInRotation(
            appContext,
            networkPreference,
            object : WallpaperRotationInitializer.Listener {
                override fun onFirstWallpaperInRotationSet() {
                    progressDialog?.dismiss()

                    // The fragment may be detached from its containing activity if the user exits
                    // the app before the first wallpaper image in rotation finishes downloading.
                    val activity: Activity? = activity
                    if (wallpaperRotationInitializer!!.startRotation(appContext)) {
                        if (activity != null) {
                            try {
                                Toast.makeText(
                                        activity,
                                        R.string.wallpaper_set_successfully_message,
                                        Toast.LENGTH_SHORT
                                    )
                                    .show()
                            } catch (e: Resources.NotFoundException) {
                                Log.e(TAG, "Could not show toast $e")
                            }
                            activity.setResult(Activity.RESULT_OK)
                            activity.finish()
                            if (!ActivityUtils.isSUWMode(appContext)) {
                                // Go back to launcher home.
                                LaunchUtils.launchHome(appContext)
                            }
                        }
                    } else { // Failed to start rotation.
                        showStartRotationErrorDialog(networkPreference)
                    }
                }

                override fun onError() {
                    progressDialog?.dismiss()
                    showStartRotationErrorDialog(networkPreference)
                }
            }
        )
    }

    private fun showStartRotationErrorDialog(@NetworkPreference networkPreference: Int) {
        val activity = activity as FragmentTransactionChecker?
        if (activity != null) {
            val startRotationErrorDialogFragment =
                StartRotationErrorDialogFragment.newInstance(networkPreference)
            startRotationErrorDialogFragment.setTargetFragment(
                this@IndividualPickerFragment2,
                UNUSED_REQUEST_CODE
            )
            if (activity.isSafeToCommitFragmentTransaction) {
                startRotationErrorDialogFragment.show(
                    parentFragmentManager,
                    TAG_START_ROTATION_ERROR_DIALOG
                )
            } else {
                stagedStartRotationErrorDialogFragment = startRotationErrorDialogFragment
            }
        }
    }

    private fun getNumColumns(): Int {
        val activity = this.activity ?: return 1
        return if (isFewerColumnLayout()) {
            SizeCalculator.getNumFeaturedIndividualColumns(activity)
        } else {
            SizeCalculator.getNumIndividualColumns(activity)
        }
    }

    /** Returns whether rotation is enabled for this category. */
    private fun isRotationEnabled() = wallpaperRotationInitializer != null

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (item.itemId == R.id.daily_rotation) {
            showRotationDialog()
            return true
        }
        return super.onMenuItemClick(item)
    }

    /** Popups a daily rotation dialog for the uses to confirm. */
    private fun showRotationDialog() {
        val startRotationDialogFragment: DialogFragment = StartRotationDialogFragment()
        startRotationDialogFragment.setTargetFragment(
            this@IndividualPickerFragment2,
            UNUSED_REQUEST_CODE
        )
        startRotationDialogFragment.show(parentFragmentManager, TAG_START_ROTATION_DIALOG)
    }

    private fun getAppliedWallpaperIds(): Set<String> {
        val prefs = InjectorProvider.getInjector().getPreferences(requireContext())
        val wallpaperInfo = wallpaperManager?.wallpaperInfo
        val appliedWallpaperIds: MutableSet<String> = ArraySet()
        val homeWallpaperId =
            if (wallpaperInfo != null) {
                wallpaperInfo.serviceName
            } else {
                prefs.homeWallpaperRemoteId
            }
        if (!TextUtils.isEmpty(homeWallpaperId)) {
            appliedWallpaperIds.add(homeWallpaperId)
        }
        val isLockWallpaperApplied =
            wallpaperManager!!.getWallpaperId(WallpaperManager.FLAG_LOCK) >= 0
        val lockWallpaperId = prefs.lockWallpaperRemoteId
        if (isLockWallpaperApplied && !TextUtils.isEmpty(lockWallpaperId)) {
            appliedWallpaperIds.add(lockWallpaperId)
        }
        return appliedWallpaperIds
    }

    // TODO(b/277180178): Extract the check to another class for unit testing
    private fun isAppliedWallpaperChanged(): Boolean {
        // Reload wallpapers if the current wallpapers have changed
        getAppliedWallpaperIds().let {
            if (appliedWallpaperIds.isEmpty() || appliedWallpaperIds != it) {
                return true
            }
        }
        return false
    }

    sealed class PickerItem(val title: CharSequence = "") {
        class WallpaperItem(val wallpaperInfo: WallpaperInfo, val isApplied: Boolean) :
            PickerItem()

        class HeaderItem(title: CharSequence) : PickerItem(title)

        class FirstHeaderItem(title: CharSequence) : PickerItem(title)
    }

    /** RecyclerView Adapter subclass for the wallpaper tiles in the RecyclerView. */
    class IndividualAdapter(
        private val items: List<PickerItem>,
        private val category: Category,
        private val activity: Activity,
        private val tileSizePx: Point,
        private val isRotationEnabled: Boolean,
        private val isFewerColumnLayout: Boolean
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        companion object {
            const val ITEM_VIEW_TYPE_INDIVIDUAL_WALLPAPER = 2
            const val ITEM_VIEW_TYPE_MY_PHOTOS = 3
            const val ITEM_VIEW_TYPE_HEADER = 4
            const val ITEM_VIEW_TYPE_HEADER_TOP = 5
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                ITEM_VIEW_TYPE_INDIVIDUAL_WALLPAPER -> createIndividualHolder(parent)
                ITEM_VIEW_TYPE_MY_PHOTOS -> createMyPhotosHolder(parent)
                ITEM_VIEW_TYPE_HEADER -> createTitleHolder(parent, /* removePaddingTop= */ false)
                ITEM_VIEW_TYPE_HEADER_TOP -> createTitleHolder(parent, /* removePaddingTop= */ true)
                else -> {
                    throw RuntimeException("Unsupported viewType $viewType in IndividualAdapter")
                }
            }
        }

        override fun getItemViewType(position: Int): Int {
            // A category cannot have both a "start rotation" tile and a "my photos" tile.
            return if (
                category.supportsCustomPhotos() &&
                    !isRotationEnabled &&
                    position == SPECIAL_FIXED_TILE_ADAPTER_POSITION
            ) {
                ITEM_VIEW_TYPE_MY_PHOTOS
            } else {
                when (items[position]) {
                    is PickerItem.WallpaperItem -> ITEM_VIEW_TYPE_INDIVIDUAL_WALLPAPER
                    is PickerItem.HeaderItem -> ITEM_VIEW_TYPE_HEADER
                    is PickerItem.FirstHeaderItem -> ITEM_VIEW_TYPE_HEADER_TOP
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val viewType = getItemViewType(position)) {
                ITEM_VIEW_TYPE_INDIVIDUAL_WALLPAPER -> bindIndividualHolder(holder, position)
                ITEM_VIEW_TYPE_MY_PHOTOS -> (holder as MyPhotosViewHolder?)!!.bind()
                ITEM_VIEW_TYPE_HEADER,
                ITEM_VIEW_TYPE_HEADER_TOP -> {
                    val textView = holder.itemView as TextView
                    val item = items[position]
                    textView.text = item.title
                    textView.contentDescription = item.title
                }
                else -> Log.e(TAG, "Unexpected viewType $viewType in IndividualAdapter")
            }
        }

        override fun getItemCount(): Int {
            return if (category.supportsCustomPhotos()) {
                items.size + 1
            } else {
                items.size
            }
        }

        private fun createIndividualHolder(parent: ViewGroup): RecyclerView.ViewHolder {
            val layoutInflater = LayoutInflater.from(activity)
            val view: View = layoutInflater.inflate(R.layout.grid_item_image, parent, false)
            return PreviewIndividualHolder(activity, tileSizePx.y, view)
        }

        private fun createMyPhotosHolder(parent: ViewGroup): RecyclerView.ViewHolder {
            val layoutInflater = LayoutInflater.from(activity)
            val view: View = layoutInflater.inflate(R.layout.grid_item_my_photos, parent, false)
            return MyPhotosViewHolder(
                activity,
                (activity as MyPhotosStarterProvider).myPhotosStarter,
                tileSizePx.y,
                view
            )
        }

        private fun createTitleHolder(
            parent: ViewGroup,
            removePaddingTop: Boolean
        ): RecyclerView.ViewHolder {
            val layoutInflater = LayoutInflater.from(activity)
            val view =
                layoutInflater.inflate(R.layout.grid_item_header, parent, /* attachToRoot= */ false)
            if (removePaddingTop) {
                view.setPadding(
                    view.paddingStart,
                    /* top= */ 0,
                    view.paddingEnd,
                    view.paddingBottom
                )
            }
            return object : RecyclerView.ViewHolder(view) {}
        }

        private fun bindIndividualHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val wallpaperIndex = if (category.supportsCustomPhotos()) position - 1 else position
            val item = items[wallpaperIndex] as PickerItem.WallpaperItem
            val wallpaper = item.wallpaperInfo
            wallpaper.computeColorInfo(holder.itemView.context)
            (holder as IndividualHolder).bindWallpaper(wallpaper)
            val container = holder.itemView.findViewById<CardView>(R.id.wallpaper_container)
            val radiusId: Int =
                if (isFewerColumnLayout) {
                    R.dimen.grid_item_all_radius
                } else {
                    R.dimen.grid_item_all_radius_small
                }
            container.radius = activity.resources.getDimension(radiusId)
            showBadge(holder, R.drawable.wallpaper_check_circle_24dp, item.isApplied)
            if (!item.isApplied) {
                showBadge(holder, wallpaper.badgeDrawableRes, wallpaper.badgeDrawableRes != ID_NULL)
            }
        }

        private fun showBadge(
            holder: RecyclerView.ViewHolder,
            @DrawableRes icon: Int,
            show: Boolean
        ) {
            val badge = holder.itemView.findViewById<ImageView>(R.id.indicator_icon)
            if (show) {
                val margin =
                    if (isFewerColumnLayout) {
                            activity.resources.getDimension(R.dimen.grid_item_badge_margin)
                        } else {
                            activity.resources.getDimension(R.dimen.grid_item_badge_margin_small)
                        }
                        .toInt()
                val layoutParams = badge.layoutParams as RelativeLayout.LayoutParams
                layoutParams.setMargins(margin, margin, margin, margin)
                badge.layoutParams = layoutParams
                badge.setBackgroundResource(icon)
                badge.visibility = View.VISIBLE
            } else {
                badge.visibility = View.GONE
            }
        }
    }
}
