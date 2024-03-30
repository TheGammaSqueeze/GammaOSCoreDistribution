/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.intentresolver;

import static android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_CANT_ACCESS_PERSONAL;
import static android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_CANT_ACCESS_WORK;
import static android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_CANT_SHARE_WITH_PERSONAL;
import static android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_CANT_SHARE_WITH_WORK;
import static android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_CROSS_PROFILE_BLOCKED_TITLE;
import static android.stats.devicepolicy.nano.DevicePolicyEnums.RESOLVER_EMPTY_STATE_NO_SHARING_TO_PERSONAL;
import static android.stats.devicepolicy.nano.DevicePolicyEnums.RESOLVER_EMPTY_STATE_NO_SHARING_TO_WORK;

import static com.android.internal.util.LatencyTracker.ACTION_LOAD_SHARE_SHEET;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.prediction.AppPredictor;
import android.app.prediction.AppTarget;
import android.app.prediction.AppTargetEvent;
import android.app.prediction.AppTargetId;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.storage.StorageManager;
import android.provider.DeviceConfig;
import android.service.chooser.ChooserTarget;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.android.intentresolver.AbstractMultiProfilePagerAdapter.EmptyState;
import com.android.intentresolver.AbstractMultiProfilePagerAdapter.EmptyStateProvider;
import com.android.intentresolver.NoCrossProfileEmptyStateProvider.DevicePolicyBlockerEmptyState;
import com.android.intentresolver.chooser.DisplayResolveInfo;
import com.android.intentresolver.chooser.MultiDisplayResolveInfo;
import com.android.intentresolver.chooser.TargetInfo;
import com.android.intentresolver.contentpreview.ChooserContentPreviewUi;
import com.android.intentresolver.flags.FeatureFlagRepository;
import com.android.intentresolver.flags.FeatureFlagRepositoryFactory;
import com.android.intentresolver.flags.Flags;
import com.android.intentresolver.grid.ChooserGridAdapter;
import com.android.intentresolver.grid.DirectShareViewHolder;
import com.android.intentresolver.model.AbstractResolverComparator;
import com.android.intentresolver.model.AppPredictionServiceResolverComparator;
import com.android.intentresolver.model.ResolverRankerServiceResolverComparator;
import com.android.intentresolver.shortcuts.AppPredictorFactory;
import com.android.intentresolver.shortcuts.ShortcutLoader;
import com.android.intentresolver.widget.ResolverDrawerLayout;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.config.sysui.SystemUiDeviceConfigFlags;
import com.android.internal.content.PackageMonitor;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The Chooser Activity handles intent resolution specifically for sharing intents -
 * for example, as generated by {@see android.content.Intent#createChooser(Intent, CharSequence)}.
 *
 */
public class ChooserActivity extends ResolverActivity implements
        ResolverListAdapter.ResolverListCommunicator {
    private static final String TAG = "ChooserActivity";

    /**
     * Boolean extra to change the following behavior: Normally, ChooserActivity finishes itself
     * in onStop when launched in a new task. If this extra is set to true, we do not finish
     * ourselves when onStop gets called.
     */
    public static final String EXTRA_PRIVATE_RETAIN_IN_ON_STOP
            = "com.android.internal.app.ChooserActivity.EXTRA_PRIVATE_RETAIN_IN_ON_STOP";

    /**
     * Transition name for the first image preview.
     * To be used for shared element transition into this activity.
     * @hide
     */
    public static final String FIRST_IMAGE_PREVIEW_TRANSITION_NAME = "screenshot_preview_image";

    private static final String PREF_NUM_SHEET_EXPANSIONS = "pref_num_sheet_expansions";

    private static final String CHIP_LABEL_METADATA_KEY = "android.service.chooser.chip_label";
    private static final String CHIP_ICON_METADATA_KEY = "android.service.chooser.chip_icon";

    private static final boolean DEBUG = true;

    public static final String LAUNCH_LOCATION_DIRECT_SHARE = "direct_share";
    private static final String SHORTCUT_TARGET = "shortcut_target";

    private static final String PLURALS_COUNT = "count";
    private static final String PLURALS_FILE_NAME = "file_name";

    private static final String IMAGE_EDITOR_SHARED_ELEMENT = "screenshot_preview_image";

    // TODO: these data structures are for one-time use in shuttling data from where they're
    // populated in `ShortcutToChooserTargetConverter` to where they're consumed in
    // `ShortcutSelectionLogic` which packs the appropriate elements into the final `TargetInfo`.
    // That flow should be refactored so that `ChooserActivity` isn't responsible for holding their
    // intermediate data, and then these members can be removed.
    private final Map<ChooserTarget, AppTarget> mDirectShareAppTargetCache = new HashMap<>();
    private final Map<ChooserTarget, ShortcutInfo> mDirectShareShortcutInfoCache = new HashMap<>();

    public static final int TARGET_TYPE_DEFAULT = 0;
    public static final int TARGET_TYPE_CHOOSER_TARGET = 1;
    public static final int TARGET_TYPE_SHORTCUTS_FROM_SHORTCUT_MANAGER = 2;
    public static final int TARGET_TYPE_SHORTCUTS_FROM_PREDICTION_SERVICE = 3;

    private static final int SCROLL_STATUS_IDLE = 0;
    private static final int SCROLL_STATUS_SCROLLING_VERTICAL = 1;
    private static final int SCROLL_STATUS_SCROLLING_HORIZONTAL = 2;

    @IntDef(flag = false, prefix = { "TARGET_TYPE_" }, value = {
            TARGET_TYPE_DEFAULT,
            TARGET_TYPE_CHOOSER_TARGET,
            TARGET_TYPE_SHORTCUTS_FROM_SHORTCUT_MANAGER,
            TARGET_TYPE_SHORTCUTS_FROM_PREDICTION_SERVICE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ShareTargetType {}

    public static final float DIRECT_SHARE_EXPANSION_RATE = 0.78f;

    private static final int DEFAULT_SALT_EXPIRATION_DAYS = 7;
    private final int mMaxHashSaltDays = DeviceConfig.getInt(DeviceConfig.NAMESPACE_SYSTEMUI,
            SystemUiDeviceConfigFlags.HASH_SALT_MAX_DAYS,
            DEFAULT_SALT_EXPIRATION_DAYS);

    private static final int URI_PERMISSION_INTENT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;

    private ChooserIntegratedDeviceComponents mIntegratedDeviceComponents;

    /* TODO: this is `nullable` because we have to defer the assignment til onCreate(). We make the
     * only assignment there, and expect it to be ready by the time we ever use it --
     * someday if we move all the usage to a component with a narrower lifecycle (something that
     * matches our Activity's create/destroy lifecycle, not its Java object lifecycle) then we
     * should be able to make this assignment as "final."
     */
    @Nullable
    private ChooserRequestParameters mChooserRequest;

    private ChooserRefinementManager mRefinementManager;

    private FeatureFlagRepository mFeatureFlagRepository;
    private ChooserContentPreviewUi mChooserContentPreviewUi;

    private boolean mShouldDisplayLandscape;
    // statsd logger wrapper
    protected ChooserActivityLogger mChooserActivityLogger;

    private long mChooserShownTime;
    protected boolean mIsSuccessfullySelected;

    private int mCurrAvailableWidth = 0;
    private Insets mLastAppliedInsets = null;
    private int mLastNumberOfChildren = -1;
    private int mMaxTargetsPerRow = 1;

    private static final int MAX_LOG_RANK_POSITION = 12;

    // TODO: are these used anywhere? They should probably be migrated to ChooserRequestParameters.
    private static final int MAX_EXTRA_INITIAL_INTENTS = 2;
    private static final int MAX_EXTRA_CHOOSER_TARGETS = 2;

    private SharedPreferences mPinnedSharedPrefs;
    private static final String PINNED_SHARED_PREFS_NAME = "chooser_pin_settings";

    private final ExecutorService mBackgroundThreadPoolExecutor = Executors.newFixedThreadPool(5);

    private int mScrollStatus = SCROLL_STATUS_IDLE;

    @VisibleForTesting
    protected ChooserMultiProfilePagerAdapter mChooserMultiProfilePagerAdapter;
    private final EnterTransitionAnimationDelegate mEnterTransitionAnimationDelegate =
            new EnterTransitionAnimationDelegate(this, () -> mResolverDrawerLayout);

    private View mContentView = null;

    private final SparseArray<ProfileRecord> mProfileRecords = new SparseArray<>();

    private boolean mExcludeSharedText = false;

    public ChooserActivity() {}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        final long intentReceivedTime = System.currentTimeMillis();
        mLatencyTracker.onActionStart(ACTION_LOAD_SHARE_SHEET);

        getChooserActivityLogger().logSharesheetTriggered();

        mFeatureFlagRepository = createFeatureFlagRepository();
        mIntegratedDeviceComponents = getIntegratedDeviceComponents();

        try {
            mChooserRequest = new ChooserRequestParameters(
                    getIntent(),
                    getReferrerPackageName(),
                    getReferrer(),
                    mIntegratedDeviceComponents,
                    mFeatureFlagRepository);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Caller provided invalid Chooser request parameters", e);
            finish();
            super_onCreate(null);
            return;
        }

        mRefinementManager = new ChooserRefinementManager(
                this,
                mChooserRequest.getRefinementIntentSender(),
                (validatedRefinedTarget) -> {
                    maybeRemoveSharedText(validatedRefinedTarget);
                    if (super.onTargetSelected(validatedRefinedTarget, false)) {
                        finish();
                    }
                },
                () -> {
                    mRefinementManager.destroy();
                    finish();
                });

        mChooserContentPreviewUi = new ChooserContentPreviewUi(
                mChooserRequest.getTargetIntent(),
                getContentResolver(),
                this::isImageType,
                createPreviewImageLoader(),
                createChooserActionFactory(),
                mEnterTransitionAnimationDelegate,
                mFeatureFlagRepository);

        setAdditionalTargets(mChooserRequest.getAdditionalTargets());

        setSafeForwardingMode(true);

        mPinnedSharedPrefs = getPinnedSharedPrefs(this);

        mMaxTargetsPerRow = getResources().getInteger(R.integer.config_chooser_max_targets_per_row);
        mShouldDisplayLandscape =
                shouldDisplayLandscape(getResources().getConfiguration().orientation);
        setRetainInOnStop(mChooserRequest.shouldRetainInOnStop());

        createProfileRecords(
                new AppPredictorFactory(
                        getApplicationContext(),
                        mChooserRequest.getSharedText(),
                        mChooserRequest.getTargetIntentFilter()),
                mChooserRequest.getTargetIntentFilter());

        super.onCreate(
                savedInstanceState,
                mChooserRequest.getTargetIntent(),
                mChooserRequest.getTitle(),
                mChooserRequest.getDefaultTitleResource(),
                mChooserRequest.getInitialIntents(),
                /* rList: List<ResolveInfo> = */ null,
                /* supportsAlwaysUseOption = */ false);

        mChooserShownTime = System.currentTimeMillis();
        final long systemCost = mChooserShownTime - intentReceivedTime;
        getChooserActivityLogger().logChooserActivityShown(
                isWorkProfile(), mChooserRequest.getTargetType(), systemCost);

        if (mResolverDrawerLayout != null) {
            mResolverDrawerLayout.addOnLayoutChangeListener(this::handleLayoutChange);

            // expand/shrink direct share 4 -> 8 viewgroup
            if (mChooserRequest.isSendActionTarget()) {
                mResolverDrawerLayout.setOnScrollChangeListener(this::handleScroll);
            }

            mResolverDrawerLayout.setOnCollapsedChangedListener(
                    new ResolverDrawerLayout.OnCollapsedChangedListener() {

                        // Only consider one expansion per activity creation
                        private boolean mWrittenOnce = false;

                        @Override
                        public void onCollapsedChanged(boolean isCollapsed) {
                            if (!isCollapsed && !mWrittenOnce) {
                                incrementNumSheetExpansions();
                                mWrittenOnce = true;
                            }
                            getChooserActivityLogger()
                                    .logSharesheetExpansionChanged(isCollapsed);
                        }
                    });
        }

        if (DEBUG) {
            Log.d(TAG, "System Time Cost is " + systemCost);
        }

        getChooserActivityLogger().logShareStarted(
                getReferrerPackageName(),
                mChooserRequest.getTargetType(),
                mChooserRequest.getCallerChooserTargets().size(),
                (mChooserRequest.getInitialIntents() == null)
                        ? 0 : mChooserRequest.getInitialIntents().length,
                isWorkProfile(),
                mChooserContentPreviewUi.getPreferredContentPreview(),
                mChooserRequest.getTargetAction(),
                mChooserRequest.getChooserActions().size(),
                mChooserRequest.getModifyShareAction() != null
        );

        mEnterTransitionAnimationDelegate.postponeTransition();
    }

    @VisibleForTesting
    protected ChooserIntegratedDeviceComponents getIntegratedDeviceComponents() {
        return ChooserIntegratedDeviceComponents.get(this, new SecureSettings());
    }

    @Override
    protected int appliedThemeResId() {
        return R.style.Theme_DeviceDefault_Chooser;
    }

    protected FeatureFlagRepository createFeatureFlagRepository() {
        return new FeatureFlagRepositoryFactory().create(getApplicationContext());
    }

    private void createProfileRecords(
            AppPredictorFactory factory, IntentFilter targetIntentFilter) {
        UserHandle mainUserHandle = getPersonalProfileUserHandle();
        createProfileRecord(mainUserHandle, targetIntentFilter, factory);

        UserHandle workUserHandle = getWorkProfileUserHandle();
        if (workUserHandle != null) {
            createProfileRecord(workUserHandle, targetIntentFilter, factory);
        }
    }

    private void createProfileRecord(
            UserHandle userHandle, IntentFilter targetIntentFilter, AppPredictorFactory factory) {
        AppPredictor appPredictor = factory.create(userHandle);
        ShortcutLoader shortcutLoader = ActivityManager.isLowRamDeviceStatic()
                    ? null
                    : createShortcutLoader(
                            getApplicationContext(),
                            appPredictor,
                            userHandle,
                            targetIntentFilter,
                            shortcutsResult -> onShortcutsLoaded(userHandle, shortcutsResult));
        mProfileRecords.put(
                userHandle.getIdentifier(),
                new ProfileRecord(appPredictor, shortcutLoader));
    }

    @Nullable
    private ProfileRecord getProfileRecord(UserHandle userHandle) {
        return mProfileRecords.get(userHandle.getIdentifier(), null);
    }

    @VisibleForTesting
    protected ShortcutLoader createShortcutLoader(
            Context context,
            AppPredictor appPredictor,
            UserHandle userHandle,
            IntentFilter targetIntentFilter,
            Consumer<ShortcutLoader.Result> callback) {
        return new ShortcutLoader(
                context,
                appPredictor,
                userHandle,
                targetIntentFilter,
                callback);
    }

    static SharedPreferences getPinnedSharedPrefs(Context context) {
        // The code below is because in the android:ui process, no one can hear you scream.
        // The package info in the context isn't initialized in the way it is for normal apps,
        // so the standard, name-based context.getSharedPreferences doesn't work. Instead, we
        // build the path manually below using the same policy that appears in ContextImpl.
        // This fails silently under the hood if there's a problem, so if we find ourselves in
        // the case where we don't have access to credential encrypted storage we just won't
        // have our pinned target info.
        final File prefsFile = new File(new File(
                Environment.getDataUserCePackageDirectory(StorageManager.UUID_PRIVATE_INTERNAL,
                        context.getUserId(), context.getPackageName()),
                "shared_prefs"),
                PINNED_SHARED_PREFS_NAME + ".xml");
        return context.getSharedPreferences(prefsFile, MODE_PRIVATE);
    }

    @Override
    protected AbstractMultiProfilePagerAdapter createMultiProfilePagerAdapter(
            Intent[] initialIntents,
            List<ResolveInfo> rList,
            boolean filterLastUsed) {
        if (shouldShowTabs()) {
            mChooserMultiProfilePagerAdapter = createChooserMultiProfilePagerAdapterForTwoProfiles(
                    initialIntents, rList, filterLastUsed);
        } else {
            mChooserMultiProfilePagerAdapter = createChooserMultiProfilePagerAdapterForOneProfile(
                    initialIntents, rList, filterLastUsed);
        }
        return mChooserMultiProfilePagerAdapter;
    }

    @Override
    protected EmptyStateProvider createBlockerEmptyStateProvider() {
        final boolean isSendAction = mChooserRequest.isSendActionTarget();

        final EmptyState noWorkToPersonalEmptyState =
                new DevicePolicyBlockerEmptyState(
                        /* context= */ this,
                        /* devicePolicyStringTitleId= */ RESOLVER_CROSS_PROFILE_BLOCKED_TITLE,
                        /* defaultTitleResource= */ R.string.resolver_cross_profile_blocked,
                        /* devicePolicyStringSubtitleId= */
                        isSendAction ? RESOLVER_CANT_SHARE_WITH_PERSONAL : RESOLVER_CANT_ACCESS_PERSONAL,
                        /* defaultSubtitleResource= */
                        isSendAction ? R.string.resolver_cant_share_with_personal_apps_explanation
                                : R.string.resolver_cant_access_personal_apps_explanation,
                        /* devicePolicyEventId= */ RESOLVER_EMPTY_STATE_NO_SHARING_TO_PERSONAL,
                        /* devicePolicyEventCategory= */ ResolverActivity.METRICS_CATEGORY_CHOOSER);

        final EmptyState noPersonalToWorkEmptyState =
                new DevicePolicyBlockerEmptyState(
                        /* context= */ this,
                        /* devicePolicyStringTitleId= */ RESOLVER_CROSS_PROFILE_BLOCKED_TITLE,
                        /* defaultTitleResource= */ R.string.resolver_cross_profile_blocked,
                        /* devicePolicyStringSubtitleId= */
                        isSendAction ? RESOLVER_CANT_SHARE_WITH_WORK : RESOLVER_CANT_ACCESS_WORK,
                        /* defaultSubtitleResource= */
                        isSendAction ? R.string.resolver_cant_share_with_work_apps_explanation
                                : R.string.resolver_cant_access_work_apps_explanation,
                        /* devicePolicyEventId= */ RESOLVER_EMPTY_STATE_NO_SHARING_TO_WORK,
                        /* devicePolicyEventCategory= */ ResolverActivity.METRICS_CATEGORY_CHOOSER);

        return new NoCrossProfileEmptyStateProvider(getPersonalProfileUserHandle(),
                noWorkToPersonalEmptyState, noPersonalToWorkEmptyState,
                createCrossProfileIntentsChecker(), createMyUserIdProvider());
    }

    private ChooserMultiProfilePagerAdapter createChooserMultiProfilePagerAdapterForOneProfile(
            Intent[] initialIntents,
            List<ResolveInfo> rList,
            boolean filterLastUsed) {
        ChooserGridAdapter adapter = createChooserGridAdapter(
                /* context */ this,
                /* payloadIntents */ mIntents,
                initialIntents,
                rList,
                filterLastUsed,
                /* userHandle */ UserHandle.of(UserHandle.myUserId()));
        return new ChooserMultiProfilePagerAdapter(
                /* context */ this,
                adapter,
                createEmptyStateProvider(/* workProfileUserHandle= */ null),
                /* workProfileQuietModeChecker= */ () -> false,
                /* workProfileUserHandle= */ null,
                mMaxTargetsPerRow);
    }

    private ChooserMultiProfilePagerAdapter createChooserMultiProfilePagerAdapterForTwoProfiles(
            Intent[] initialIntents,
            List<ResolveInfo> rList,
            boolean filterLastUsed) {
        int selectedProfile = findSelectedProfile();
        ChooserGridAdapter personalAdapter = createChooserGridAdapter(
                /* context */ this,
                /* payloadIntents */ mIntents,
                selectedProfile == PROFILE_PERSONAL ? initialIntents : null,
                rList,
                filterLastUsed,
                /* userHandle */ getPersonalProfileUserHandle());
        ChooserGridAdapter workAdapter = createChooserGridAdapter(
                /* context */ this,
                /* payloadIntents */ mIntents,
                selectedProfile == PROFILE_WORK ? initialIntents : null,
                rList,
                filterLastUsed,
                /* userHandle */ getWorkProfileUserHandle());
        return new ChooserMultiProfilePagerAdapter(
                /* context */ this,
                personalAdapter,
                workAdapter,
                createEmptyStateProvider(/* workProfileUserHandle= */ getWorkProfileUserHandle()),
                () -> mWorkProfileAvailability.isQuietModeEnabled(),
                selectedProfile,
                getWorkProfileUserHandle(),
                mMaxTargetsPerRow);
    }

    private int findSelectedProfile() {
        int selectedProfile = getSelectedProfileExtra();
        if (selectedProfile == -1) {
            selectedProfile = getProfileForUser(getUser());
        }
        return selectedProfile;
    }

    @Override
    protected boolean postRebuildList(boolean rebuildCompleted) {
        updateStickyContentPreview();
        if (shouldShowStickyContentPreview()
                || mChooserMultiProfilePagerAdapter
                        .getCurrentRootAdapter().getSystemRowCount() != 0) {
            getChooserActivityLogger().logActionShareWithPreview(
                    mChooserContentPreviewUi.getPreferredContentPreview());
        }
        return postRebuildListInternal(rebuildCompleted);
    }

    /**
     * Check if the profile currently used is a work profile.
     * @return true if it is work profile, false if it is parent profile (or no work profile is
     * set up)
     */
    protected boolean isWorkProfile() {
        return getSystemService(UserManager.class)
                .getUserInfo(UserHandle.myUserId()).isManagedProfile();
    }

    @Override
    protected PackageMonitor createPackageMonitor(ResolverListAdapter listAdapter) {
        return new PackageMonitor() {
            @Override
            public void onSomePackagesChanged() {
                handlePackagesChanged(listAdapter);
            }
        };
    }

    /**
     * Update UI to reflect changes in data.
     */
    public void handlePackagesChanged() {
        handlePackagesChanged(/* listAdapter */ null);
    }

    /**
     * Update UI to reflect changes in data.
     * <p>If {@code listAdapter} is {@code null}, both profile list adapters are updated if
     * available.
     */
    private void handlePackagesChanged(@Nullable ResolverListAdapter listAdapter) {
        // Refresh pinned items
        mPinnedSharedPrefs = getPinnedSharedPrefs(this);
        if (listAdapter == null) {
            mChooserMultiProfilePagerAdapter.getActiveListAdapter().handlePackagesChanged();
            if (mChooserMultiProfilePagerAdapter.getCount() > 1) {
                mChooserMultiProfilePagerAdapter.getInactiveListAdapter().handlePackagesChanged();
            }
        } else {
            listAdapter.handlePackagesChanged();
        }
        updateProfileViewButton();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: " + getComponentName().flattenToShortString());
        maybeCancelFinishAnimation();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        ViewPager viewPager = findViewById(com.android.internal.R.id.profile_pager);
        if (viewPager.isLayoutRtl()) {
            mMultiProfilePagerAdapter.setupViewPager(viewPager);
        }

        mShouldDisplayLandscape = shouldDisplayLandscape(newConfig.orientation);
        mMaxTargetsPerRow = getResources().getInteger(R.integer.config_chooser_max_targets_per_row);
        mChooserMultiProfilePagerAdapter.setMaxTargetsPerRow(mMaxTargetsPerRow);
        adjustPreviewWidth(newConfig.orientation, null);
        updateStickyContentPreview();
        updateTabPadding();
    }

    private boolean shouldDisplayLandscape(int orientation) {
        // Sharesheet fixes the # of items per row and therefore can not correctly lay out
        // when in the restricted size of multi-window mode. In the future, would be nice
        // to use minimum dp size requirements instead
        return orientation == Configuration.ORIENTATION_LANDSCAPE && !isInMultiWindowMode();
    }

    private void adjustPreviewWidth(int orientation, View parent) {
        int width = -1;
        if (mShouldDisplayLandscape) {
            width = getResources().getDimensionPixelSize(R.dimen.chooser_preview_width);
        }

        parent = parent == null ? getWindow().getDecorView() : parent;

        updateLayoutWidth(com.android.internal.R.id.content_preview_text_layout, width, parent);
        updateLayoutWidth(com.android.internal.R.id.content_preview_title_layout, width, parent);
        updateLayoutWidth(com.android.internal.R.id.content_preview_file_layout, width, parent);
    }

    private void updateTabPadding() {
        if (shouldShowTabs()) {
            View tabs = findViewById(com.android.internal.R.id.tabs);
            float iconSize = getResources().getDimension(R.dimen.chooser_icon_size);
            // The entire width consists of icons or padding. Divide the item padding in half to get
            // paddingHorizontal.
            float padding = (tabs.getWidth() - mMaxTargetsPerRow * iconSize)
                    / mMaxTargetsPerRow / 2;
            // Subtract the margin the buttons already have.
            padding -= getResources().getDimension(R.dimen.resolver_profile_tab_margin);
            tabs.setPadding((int) padding, 0, (int) padding, 0);
        }
    }

    private void updateLayoutWidth(int layoutResourceId, int width, View parent) {
        View view = parent.findViewById(layoutResourceId);
        if (view != null && view.getLayoutParams() != null) {
            LayoutParams params = view.getLayoutParams();
            params.width = width;
            view.setLayoutParams(params);
        }
    }

    /**
     * Create a view that will be shown in the content preview area
     * @param parent reference to the parent container where the view should be attached to
     * @return content preview view
     */
    protected ViewGroup createContentPreviewView(ViewGroup parent) {
        ViewGroup layout = mChooserContentPreviewUi.displayContentPreview(
                getResources(),
                getLayoutInflater(),
                parent);

        if (layout != null) {
            adjustPreviewWidth(getResources().getConfiguration().orientation, layout);
        }

        return layout;
    }

    @Nullable
    private View getFirstVisibleImgPreviewView() {
        View firstImage = findViewById(com.android.internal.R.id.content_preview_image_1_large);
        return firstImage != null && firstImage.isVisibleToUser() ? firstImage : null;
    }

    /**
     * Wrapping the ContentResolver call to expose for easier mocking,
     * and to avoid mocking Android core classes.
     */
    @VisibleForTesting
    public Cursor queryResolver(ContentResolver resolver, Uri uri) {
        return resolver.query(uri, null, null, null, null);
    }

    @VisibleForTesting
    protected boolean isImageType(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/");
    }

    private int getNumSheetExpansions() {
        return getPreferences(Context.MODE_PRIVATE).getInt(PREF_NUM_SHEET_EXPANSIONS, 0);
    }

    private void incrementNumSheetExpansions() {
        getPreferences(Context.MODE_PRIVATE).edit().putInt(PREF_NUM_SHEET_EXPANSIONS,
                getNumSheetExpansions() + 1).apply();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (maybeCancelFinishAnimation()) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (isFinishing()) {
            mLatencyTracker.onActionCancel(ACTION_LOAD_SHARE_SHEET);
        }

        if (mRefinementManager != null) {  // TODO: null-checked in case of early-destroy, or skip?
            mRefinementManager.destroy();
            mRefinementManager = null;
        }

        mBackgroundThreadPoolExecutor.shutdownNow();

        destroyProfileRecords();
    }

    private void destroyProfileRecords() {
        for (int i = 0; i < mProfileRecords.size(); ++i) {
            mProfileRecords.valueAt(i).destroy();
        }
        mProfileRecords.clear();
    }

    @Override // ResolverListCommunicator
    public Intent getReplacementIntent(ActivityInfo aInfo, Intent defIntent) {
        if (mChooserRequest == null) {
            return defIntent;
        }

        Intent result = defIntent;
        if (mChooserRequest.getReplacementExtras() != null) {
            final Bundle replExtras =
                    mChooserRequest.getReplacementExtras().getBundle(aInfo.packageName);
            if (replExtras != null) {
                result = new Intent(defIntent);
                result.putExtras(replExtras);
            }
        }
        if (aInfo.name.equals(IntentForwarderActivity.FORWARD_INTENT_TO_PARENT)
                || aInfo.name.equals(IntentForwarderActivity.FORWARD_INTENT_TO_MANAGED_PROFILE)) {
            result = Intent.createChooser(result,
                    getIntent().getCharSequenceExtra(Intent.EXTRA_TITLE));

            // Don't auto-launch single intents if the intent is being forwarded. This is done
            // because automatically launching a resolving application as a response to the user
            // action of switching accounts is pretty unexpected.
            result.putExtra(Intent.EXTRA_AUTO_LAUNCH_SINGLE_CHOICE, false);
        }
        return result;
    }

    @Override
    public void onActivityStarted(TargetInfo cti) {
        if (mChooserRequest.getChosenComponentSender() != null) {
            final ComponentName target = cti.getResolvedComponentName();
            if (target != null) {
                final Intent fillIn = new Intent().putExtra(Intent.EXTRA_CHOSEN_COMPONENT, target);
                try {
                    mChooserRequest.getChosenComponentSender().sendIntent(
                            this, Activity.RESULT_OK, fillIn, null, null);
                } catch (IntentSender.SendIntentException e) {
                    Slog.e(TAG, "Unable to launch supplied IntentSender to report "
                            + "the chosen component: " + e);
                }
            }
        }
    }

    @Override
    public void addUseDifferentAppLabelIfNecessary(ResolverListAdapter adapter) {
        if (mChooserRequest.getCallerChooserTargets().size() > 0) {
            mChooserMultiProfilePagerAdapter.getActiveListAdapter().addServiceResults(
                    /* origTarget */ null,
                    new ArrayList<>(mChooserRequest.getCallerChooserTargets()),
                    TARGET_TYPE_DEFAULT,
                    /* directShareShortcutInfoCache */ Collections.emptyMap(),
                    /* directShareAppTargetCache */ Collections.emptyMap());
        }
    }

    @Override
    public int getLayoutResource() {
        return R.layout.chooser_grid;
    }

    @Override // ResolverListCommunicator
    public boolean shouldGetActivityMetadata() {
        return true;
    }

    @Override
    public boolean shouldAutoLaunchSingleChoice(TargetInfo target) {
        // Note that this is only safe because the Intent handled by the ChooserActivity is
        // guaranteed to contain no extras unknown to the local ClassLoader. That is why this
        // method can not be replaced in the ResolverActivity whole hog.
        if (!super.shouldAutoLaunchSingleChoice(target)) {
            return false;
        }

        return getIntent().getBooleanExtra(Intent.EXTRA_AUTO_LAUNCH_SINGLE_CHOICE, true);
    }

    private void showTargetDetails(TargetInfo targetInfo) {
        if (targetInfo == null) return;

        List<DisplayResolveInfo> targetList = targetInfo.getAllDisplayTargets();
        if (targetList.isEmpty()) {
            Log.e(TAG, "No displayable data to show target details");
            return;
        }

        // TODO: implement these type-conditioned behaviors polymorphically, and consider moving
        // the logic into `ChooserTargetActionsDialogFragment.show()`.
        boolean isShortcutPinned = targetInfo.isSelectableTargetInfo() && targetInfo.isPinned();
        IntentFilter intentFilter = targetInfo.isSelectableTargetInfo()
                ? mChooserRequest.getTargetIntentFilter() : null;
        String shortcutTitle = targetInfo.isSelectableTargetInfo()
                ? targetInfo.getDisplayLabel().toString() : null;
        String shortcutIdKey = targetInfo.getDirectShareShortcutId();

        ChooserTargetActionsDialogFragment.show(
                getSupportFragmentManager(),
                targetList,
                mChooserMultiProfilePagerAdapter.getCurrentUserHandle(),
                shortcutIdKey,
                shortcutTitle,
                isShortcutPinned,
                intentFilter);
    }

    @Override
    protected boolean onTargetSelected(TargetInfo target, boolean alwaysCheck) {
        if (mRefinementManager.maybeHandleSelection(target)) {
            return false;
        }
        updateModelAndChooserCounts(target);
        maybeRemoveSharedText(target);
        return super.onTargetSelected(target, alwaysCheck);
    }

    @Override
    public void startSelected(int which, boolean always, boolean filtered) {
        ChooserListAdapter currentListAdapter =
                mChooserMultiProfilePagerAdapter.getActiveListAdapter();
        TargetInfo targetInfo = currentListAdapter
                .targetInfoForPosition(which, filtered);
        if (targetInfo != null && targetInfo.isNotSelectableTargetInfo()) {
            return;
        }

        final long selectionCost = System.currentTimeMillis() - mChooserShownTime;

        if (targetInfo.isMultiDisplayResolveInfo()) {
            MultiDisplayResolveInfo mti = (MultiDisplayResolveInfo) targetInfo;
            if (!mti.hasSelected()) {
                ChooserStackedAppDialogFragment.show(
                        getSupportFragmentManager(),
                        mti,
                        which,
                        mChooserMultiProfilePagerAdapter.getCurrentUserHandle());
                return;
            }
        }

        super.startSelected(which, always, filtered);

        if (currentListAdapter.getCount() > 0) {
            switch (currentListAdapter.getPositionTargetType(which)) {
                case ChooserListAdapter.TARGET_SERVICE:
                    getChooserActivityLogger().logShareTargetSelected(
                            ChooserActivityLogger.SELECTION_TYPE_SERVICE,
                            targetInfo.getResolveInfo().activityInfo.processName,
                            which,
                            /* directTargetAlsoRanked= */ getRankedPosition(targetInfo),
                            mChooserRequest.getCallerChooserTargets().size(),
                            targetInfo.getHashedTargetIdForMetrics(this),
                            targetInfo.isPinned(),
                            mIsSuccessfullySelected,
                            selectionCost
                    );
                    return;
                case ChooserListAdapter.TARGET_CALLER:
                case ChooserListAdapter.TARGET_STANDARD:
                    getChooserActivityLogger().logShareTargetSelected(
                            ChooserActivityLogger.SELECTION_TYPE_APP,
                            targetInfo.getResolveInfo().activityInfo.processName,
                            (which - currentListAdapter.getSurfacedTargetInfo().size()),
                            /* directTargetAlsoRanked= */ -1,
                            currentListAdapter.getCallerTargetCount(),
                            /* directTargetHashed= */ null,
                            targetInfo.isPinned(),
                            mIsSuccessfullySelected,
                            selectionCost
                    );
                    return;
                case ChooserListAdapter.TARGET_STANDARD_AZ:
                    // A-Z targets are unranked standard targets; we use a value of -1 to mark that
                    // they are from the alphabetical pool.
                    // TODO: why do we log a different selection type if the -1 value already
                    // designates the same condition?
                    getChooserActivityLogger().logShareTargetSelected(
                            ChooserActivityLogger.SELECTION_TYPE_STANDARD,
                            targetInfo.getResolveInfo().activityInfo.processName,
                            /* value= */ -1,
                            /* directTargetAlsoRanked= */ -1,
                            /* numCallerProvided= */ 0,
                            /* directTargetHashed= */ null,
                            /* isPinned= */ false,
                            mIsSuccessfullySelected,
                            selectionCost
                    );
                    return;
            }
        }
    }

    private int getRankedPosition(TargetInfo targetInfo) {
        String targetPackageName =
                targetInfo.getChooserTargetComponentName().getPackageName();
        ChooserListAdapter currentListAdapter =
                mChooserMultiProfilePagerAdapter.getActiveListAdapter();
        int maxRankedResults = Math.min(
                currentListAdapter.getDisplayResolveInfoCount(), MAX_LOG_RANK_POSITION);

        for (int i = 0; i < maxRankedResults; i++) {
            if (currentListAdapter.getDisplayResolveInfo(i)
                    .getResolveInfo().activityInfo.packageName.equals(targetPackageName)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected boolean shouldAddFooterView() {
        // To accommodate for window insets
        return true;
    }

    @Override
    protected void applyFooterView(int height) {
        int count = mChooserMultiProfilePagerAdapter.getItemCount();

        for (int i = 0; i < count; i++) {
            mChooserMultiProfilePagerAdapter.getAdapterForIndex(i).setFooterHeight(height);
        }
    }

    private void logDirectShareTargetReceived(UserHandle forUser) {
        ProfileRecord profileRecord = getProfileRecord(forUser);
        if (profileRecord == null) {
            return;
        }
        getChooserActivityLogger().logDirectShareTargetReceived(
                MetricsEvent.ACTION_DIRECT_SHARE_TARGETS_LOADED_SHORTCUT_MANAGER,
                (int) (SystemClock.elapsedRealtime() - profileRecord.loadingStartTime));
    }

    void updateModelAndChooserCounts(TargetInfo info) {
        if (info != null && info.isMultiDisplayResolveInfo()) {
            info = ((MultiDisplayResolveInfo) info).getSelectedTarget();
        }
        if (info != null) {
            sendClickToAppPredictor(info);
            final ResolveInfo ri = info.getResolveInfo();
            Intent targetIntent = getTargetIntent();
            if (ri != null && ri.activityInfo != null && targetIntent != null) {
                ChooserListAdapter currentListAdapter =
                        mChooserMultiProfilePagerAdapter.getActiveListAdapter();
                if (currentListAdapter != null) {
                    sendImpressionToAppPredictor(info, currentListAdapter);
                    currentListAdapter.updateModel(info.getResolvedComponentName());
                    currentListAdapter.updateChooserCounts(ri.activityInfo.packageName,
                            targetIntent.getAction());
                }
                if (DEBUG) {
                    Log.d(TAG, "ResolveInfo Package is " + ri.activityInfo.packageName);
                    Log.d(TAG, "Action to be updated is " + targetIntent.getAction());
                }
            } else if (DEBUG) {
                Log.d(TAG, "Can not log Chooser Counts of null ResolveInfo");
            }
        }
        mIsSuccessfullySelected = true;
    }

    private void maybeRemoveSharedText(@androidx.annotation.NonNull TargetInfo targetInfo) {
        Intent targetIntent = targetInfo.getTargetIntent();
        if (targetIntent == null) {
            return;
        }
        Intent originalTargetIntent = new Intent(mChooserRequest.getTargetIntent());
        // Our TargetInfo implementations add associated component to the intent, let's do the same
        // for the sake of the comparison below.
        if (targetIntent.getComponent() != null) {
            originalTargetIntent.setComponent(targetIntent.getComponent());
        }
        // Use filterEquals as a way to check that the primary intent is in use (and not an
        // alternative one). For example, an app is sharing an image and a link with mime type
        // "image/png" and provides an alternative intent to share only the link with mime type
        // "text/uri". Should there be a target that accepts only the latter, the alternative intent
        // will be used and we don't want to exclude the link from it.
        if (mExcludeSharedText && originalTargetIntent.filterEquals(targetIntent)) {
            targetIntent.removeExtra(Intent.EXTRA_TEXT);
        }
    }

    private void sendImpressionToAppPredictor(TargetInfo targetInfo, ChooserListAdapter adapter) {
        // Send DS target impression info to AppPredictor, only when user chooses app share.
        if (targetInfo.isChooserTargetInfo()) {
            return;
        }

        AppPredictor directShareAppPredictor = getAppPredictor(
                mChooserMultiProfilePagerAdapter.getCurrentUserHandle());
        if (directShareAppPredictor == null) {
            return;
        }
        List<TargetInfo> surfacedTargetInfo = adapter.getSurfacedTargetInfo();
        List<AppTargetId> targetIds = new ArrayList<>();
        for (TargetInfo chooserTargetInfo : surfacedTargetInfo) {
            ShortcutInfo shortcutInfo = chooserTargetInfo.getDirectShareShortcutInfo();
            if (shortcutInfo != null) {
                ComponentName componentName =
                        chooserTargetInfo.getChooserTargetComponentName();
                targetIds.add(new AppTargetId(
                        String.format(
                                "%s/%s/%s",
                                shortcutInfo.getId(),
                                componentName.flattenToString(),
                                SHORTCUT_TARGET)));
            }
        }
        directShareAppPredictor.notifyLaunchLocationShown(LAUNCH_LOCATION_DIRECT_SHARE, targetIds);
    }

    private void sendClickToAppPredictor(TargetInfo targetInfo) {
        if (!targetInfo.isChooserTargetInfo()) {
            return;
        }

        AppPredictor directShareAppPredictor = getAppPredictor(
                mChooserMultiProfilePagerAdapter.getCurrentUserHandle());
        if (directShareAppPredictor == null) {
            return;
        }
        AppTarget appTarget = targetInfo.getDirectShareAppTarget();
        if (appTarget != null) {
            // This is a direct share click that was provided by the APS
            directShareAppPredictor.notifyAppTargetEvent(
                    new AppTargetEvent.Builder(appTarget, AppTargetEvent.ACTION_LAUNCH)
                        .setLaunchLocation(LAUNCH_LOCATION_DIRECT_SHARE)
                        .build());
        }
    }

    @Nullable
    private AppPredictor getAppPredictor(UserHandle userHandle) {
        ProfileRecord record = getProfileRecord(userHandle);
        return (record == null) ? null : record.appPredictor;
    }

    /**
     * Sort intents alphabetically based on display label.
     */
    static class AzInfoComparator implements Comparator<DisplayResolveInfo> {
        Collator mCollator;
        AzInfoComparator(Context context) {
            mCollator = Collator.getInstance(context.getResources().getConfiguration().locale);
        }

        @Override
        public int compare(
                DisplayResolveInfo lhsp, DisplayResolveInfo rhsp) {
            return mCollator.compare(lhsp.getDisplayLabel(), rhsp.getDisplayLabel());
        }
    }

    protected ChooserActivityLogger getChooserActivityLogger() {
        if (mChooserActivityLogger == null) {
            mChooserActivityLogger = new ChooserActivityLogger();
        }
        return mChooserActivityLogger;
    }

    public class ChooserListController extends ResolverListController {
        public ChooserListController(
                Context context,
                PackageManager pm,
                Intent targetIntent,
                String referrerPackageName,
                int launchedFromUid,
                AbstractResolverComparator resolverComparator) {
            super(
                    context,
                    pm,
                    targetIntent,
                    referrerPackageName,
                    launchedFromUid,
                    resolverComparator);
        }

        @Override
        boolean isComponentFiltered(ComponentName name) {
            return mChooserRequest.getFilteredComponentNames().contains(name);
        }

        @Override
        public boolean isComponentPinned(ComponentName name) {
            return mPinnedSharedPrefs.getBoolean(name.flattenToString(), false);
        }
    }

    @VisibleForTesting
    public ChooserGridAdapter createChooserGridAdapter(
            Context context,
            List<Intent> payloadIntents,
            Intent[] initialIntents,
            List<ResolveInfo> rList,
            boolean filterLastUsed,
            UserHandle userHandle) {
        ChooserListAdapter chooserListAdapter = createChooserListAdapter(
                context,
                payloadIntents,
                initialIntents,
                rList,
                filterLastUsed,
                createListController(userHandle),
                userHandle,
                getTargetIntent(),
                mChooserRequest,
                mMaxTargetsPerRow);

        return new ChooserGridAdapter(
                context,
                new ChooserGridAdapter.ChooserActivityDelegate() {
                    @Override
                    public boolean shouldShowTabs() {
                        return ChooserActivity.this.shouldShowTabs();
                    }

                    @Override
                    public View buildContentPreview(ViewGroup parent) {
                        return createContentPreviewView(parent);
                    }

                    @Override
                    public void onTargetSelected(int itemIndex) {
                        startSelected(itemIndex, false, true);
                    }

                    @Override
                    public void onTargetLongPressed(int selectedPosition) {
                        final TargetInfo longPressedTargetInfo =
                                mChooserMultiProfilePagerAdapter
                                .getActiveListAdapter()
                                .targetInfoForPosition(
                                        selectedPosition, /* filtered= */ true);
                        // Only a direct share target or an app target is expected
                        if (longPressedTargetInfo.isDisplayResolveInfo()
                                || longPressedTargetInfo.isSelectableTargetInfo()) {
                            showTargetDetails(longPressedTargetInfo);
                        }
                    }

                    @Override
                    public void updateProfileViewButton(View newButtonFromProfileRow) {
                        mProfileView = newButtonFromProfileRow;
                        mProfileView.setOnClickListener(ChooserActivity.this::onProfileClick);
                        ChooserActivity.this.updateProfileViewButton();
                    }

                    @Override
                    public int getValidTargetCount() {
                        return mChooserMultiProfilePagerAdapter
                                .getActiveListAdapter()
                                .getSelectableServiceTargetCount();
                    }

                    @Override
                    public void updateDirectShareExpansion(DirectShareViewHolder directShareGroup) {
                        RecyclerView activeAdapterView =
                                mChooserMultiProfilePagerAdapter.getActiveAdapterView();
                        if (mResolverDrawerLayout.isCollapsed()) {
                            directShareGroup.collapse(activeAdapterView);
                        } else {
                            directShareGroup.expand(activeAdapterView);
                        }
                    }

                    @Override
                    public void handleScrollToExpandDirectShare(
                            DirectShareViewHolder directShareGroup, int y, int oldy) {
                        directShareGroup.handleScroll(
                                mChooserMultiProfilePagerAdapter.getActiveAdapterView(),
                                y,
                                oldy,
                                mMaxTargetsPerRow);
                    }
                },
                chooserListAdapter,
                shouldShowContentPreview(),
                mMaxTargetsPerRow,
                getNumSheetExpansions());
    }

    @VisibleForTesting
    public ChooserListAdapter createChooserListAdapter(
            Context context,
            List<Intent> payloadIntents,
            Intent[] initialIntents,
            List<ResolveInfo> rList,
            boolean filterLastUsed,
            ResolverListController resolverListController,
            UserHandle userHandle,
            Intent targetIntent,
            ChooserRequestParameters chooserRequest,
            int maxTargetsPerRow) {
        return new ChooserListAdapter(
                context,
                payloadIntents,
                initialIntents,
                rList,
                filterLastUsed,
                resolverListController,
                userHandle,
                targetIntent,
                this,
                context.getPackageManager(),
                getChooserActivityLogger(),
                chooserRequest,
                maxTargetsPerRow);
    }

    @Override
    @VisibleForTesting
    protected ChooserListController createListController(UserHandle userHandle) {
        AppPredictor appPredictor = getAppPredictor(userHandle);
        AbstractResolverComparator resolverComparator;
        if (appPredictor != null) {
            resolverComparator = new AppPredictionServiceResolverComparator(this, getTargetIntent(),
                    getReferrerPackageName(), appPredictor, userHandle, getChooserActivityLogger());
        } else {
            resolverComparator =
                    new ResolverRankerServiceResolverComparator(this, getTargetIntent(),
                        getReferrerPackageName(), null, getChooserActivityLogger());
        }

        return new ChooserListController(
                this,
                mPm,
                getTargetIntent(),
                getReferrerPackageName(),
                getAnnotatedUserHandles().userIdOfCallingApp,
                resolverComparator);
    }

    @VisibleForTesting
    protected ImageLoader createPreviewImageLoader() {
        final int cacheSize;
        if (mFeatureFlagRepository.isEnabled(Flags.SHARESHEET_SCROLLABLE_IMAGE_PREVIEW)) {
            float chooserWidth = getResources().getDimension(R.dimen.chooser_width);
            float imageWidth = getResources().getDimension(R.dimen.chooser_preview_image_width);
            cacheSize = (int) (Math.ceil(chooserWidth / imageWidth) + 2);
        } else {
            cacheSize = 3;
        }
        return new ImagePreviewImageLoader(this, getLifecycle(), cacheSize);
    }

    private ChooserActionFactory createChooserActionFactory() {
        return new ChooserActionFactory(
                this,
                mChooserRequest,
                mFeatureFlagRepository,
                mIntegratedDeviceComponents,
                getChooserActivityLogger(),
                (isExcluded) -> mExcludeSharedText = isExcluded,
                this::getFirstVisibleImgPreviewView,
                new ChooserActionFactory.ActionActivityStarter() {
                    @Override
                    public void safelyStartActivityAsPersonalProfileUser(TargetInfo targetInfo) {
                        safelyStartActivityAsUser(targetInfo, getPersonalProfileUserHandle());
                        finish();
                    }

                    @Override
                    public void safelyStartActivityAsPersonalProfileUserWithSharedElementTransition(
                            TargetInfo targetInfo, View sharedElement, String sharedElementName) {
                        ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(
                                ChooserActivity.this, sharedElement, sharedElementName);
                        safelyStartActivityAsUser(
                                targetInfo, getPersonalProfileUserHandle(), options.toBundle());
                        startFinishAnimation();
                    }
                },
                (status) -> {
                    if (status != null) {
                        setResult(status);
                    }
                    finish();
                });
    }

    private void handleScroll(View view, int x, int y, int oldx, int oldy) {
        if (mChooserMultiProfilePagerAdapter.getCurrentRootAdapter() != null) {
            mChooserMultiProfilePagerAdapter.getCurrentRootAdapter().handleScroll(view, y, oldy);
        }
    }

    /*
     * Need to dynamically adjust how many icons can fit per row before we add them,
     * which also means setting the correct offset to initially show the content
     * preview area + 2 rows of targets
     */
    private void handleLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
            int oldTop, int oldRight, int oldBottom) {
        if (mChooserMultiProfilePagerAdapter == null) {
            return;
        }
        RecyclerView recyclerView = mChooserMultiProfilePagerAdapter.getActiveAdapterView();
        ChooserGridAdapter gridAdapter = mChooserMultiProfilePagerAdapter.getCurrentRootAdapter();
        // Skip height calculation if recycler view was scrolled to prevent it inaccurately
        // calculating the height, as the logic below does not account for the scrolled offset.
        if (gridAdapter == null || recyclerView == null
                || recyclerView.computeVerticalScrollOffset() != 0) {
            return;
        }

        final int availableWidth = right - left - v.getPaddingLeft() - v.getPaddingRight();
        boolean isLayoutUpdated =
                gridAdapter.calculateChooserTargetWidth(availableWidth)
                || recyclerView.getAdapter() == null
                || availableWidth != mCurrAvailableWidth;

        boolean insetsChanged = !Objects.equals(mLastAppliedInsets, mSystemWindowInsets);

        if (isLayoutUpdated
                || insetsChanged
                || mLastNumberOfChildren != recyclerView.getChildCount()) {
            mCurrAvailableWidth = availableWidth;
            if (isLayoutUpdated) {
                // It is very important we call setAdapter from here. Otherwise in some cases
                // the resolver list doesn't get populated, such as b/150922090, b/150918223
                // and b/150936654
                recyclerView.setAdapter(gridAdapter);
                ((GridLayoutManager) recyclerView.getLayoutManager()).setSpanCount(
                        mMaxTargetsPerRow);

                updateTabPadding();
            }

            UserHandle currentUserHandle = mChooserMultiProfilePagerAdapter.getCurrentUserHandle();
            int currentProfile = getProfileForUser(currentUserHandle);
            int initialProfile = findSelectedProfile();
            if (currentProfile != initialProfile) {
                return;
            }

            if (mLastNumberOfChildren == recyclerView.getChildCount() && !insetsChanged) {
                return;
            }

            getMainThreadHandler().post(() -> {
                if (mResolverDrawerLayout == null || gridAdapter == null) {
                    return;
                }
                int offset = calculateDrawerOffset(top, bottom, recyclerView, gridAdapter);
                mResolverDrawerLayout.setCollapsibleHeightReserved(offset);
                mEnterTransitionAnimationDelegate.markOffsetCalculated();
                mLastAppliedInsets = mSystemWindowInsets;
            });
        }
    }

    private int calculateDrawerOffset(
            int top, int bottom, RecyclerView recyclerView, ChooserGridAdapter gridAdapter) {

        final int bottomInset = mSystemWindowInsets != null
                ? mSystemWindowInsets.bottom : 0;
        int offset = bottomInset;
        int rowsToShow = gridAdapter.getSystemRowCount()
                + gridAdapter.getProfileRowCount()
                + gridAdapter.getServiceTargetRowCount()
                + gridAdapter.getCallerAndRankedTargetRowCount();

        // then this is most likely not a SEND_* action, so check
        // the app target count
        if (rowsToShow == 0) {
            rowsToShow = gridAdapter.getRowCount();
        }

        // still zero? then use a default height and leave, which
        // can happen when there are no targets to show
        if (rowsToShow == 0 && !shouldShowStickyContentPreview()) {
            offset += getResources().getDimensionPixelSize(
                    R.dimen.chooser_max_collapsed_height);
            return offset;
        }

        View stickyContentPreview = findViewById(com.android.internal.R.id.content_preview_container);
        if (shouldShowStickyContentPreview() && isStickyContentPreviewShowing()) {
            offset += stickyContentPreview.getHeight();
        }

        if (shouldShowTabs()) {
            offset += findViewById(com.android.internal.R.id.tabs).getHeight();
        }

        if (recyclerView.getVisibility() == View.VISIBLE) {
            int directShareHeight = 0;
            rowsToShow = Math.min(4, rowsToShow);
            boolean shouldShowExtraRow = shouldShowExtraRow(rowsToShow);
            mLastNumberOfChildren = recyclerView.getChildCount();
            for (int i = 0, childCount = recyclerView.getChildCount();
                    i < childCount && rowsToShow > 0; i++) {
                View child = recyclerView.getChildAt(i);
                if (((GridLayoutManager.LayoutParams)
                        child.getLayoutParams()).getSpanIndex() != 0) {
                    continue;
                }
                int height = child.getHeight();
                offset += height;
                if (shouldShowExtraRow) {
                    offset += height;
                }

                if (gridAdapter.getTargetType(
                        recyclerView.getChildAdapterPosition(child))
                        == ChooserListAdapter.TARGET_SERVICE) {
                    directShareHeight = height;
                }
                rowsToShow--;
            }

            boolean isExpandable = getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_PORTRAIT && !isInMultiWindowMode();
            if (directShareHeight != 0 && shouldShowContentPreview()
                    && isExpandable) {
                // make sure to leave room for direct share 4->8 expansion
                int requiredExpansionHeight =
                        (int) (directShareHeight / DIRECT_SHARE_EXPANSION_RATE);
                int topInset = mSystemWindowInsets != null ? mSystemWindowInsets.top : 0;
                int minHeight = bottom - top - mResolverDrawerLayout.getAlwaysShowHeight()
                        - requiredExpansionHeight - topInset - bottomInset;

                offset = Math.min(offset, minHeight);
            }
        } else {
            ViewGroup currentEmptyStateView = getActiveEmptyStateView();
            if (currentEmptyStateView.getVisibility() == View.VISIBLE) {
                offset += currentEmptyStateView.getHeight();
            }
        }

        return Math.min(offset, bottom - top);
    }

    /**
     * If we have a tabbed view and are showing 1 row in the current profile and an empty
     * state screen in the other profile, to prevent cropping of the empty state screen we show
     * a second row in the current profile.
     */
    private boolean shouldShowExtraRow(int rowsToShow) {
        return shouldShowTabs()
                && rowsToShow == 1
                && mChooserMultiProfilePagerAdapter.shouldShowEmptyStateScreen(
                        mChooserMultiProfilePagerAdapter.getInactiveListAdapter());
    }

    /**
     * Returns {@link #PROFILE_PERSONAL}, {@link #PROFILE_WORK}, or -1 if the given user handle
     * does not match either the personal or work user handle.
     **/
    private int getProfileForUser(UserHandle currentUserHandle) {
        if (currentUserHandle.equals(getPersonalProfileUserHandle())) {
            return PROFILE_PERSONAL;
        } else if (currentUserHandle.equals(getWorkProfileUserHandle())) {
            return PROFILE_WORK;
        }
        Log.e(TAG, "User " + currentUserHandle + " does not belong to a personal or work profile.");
        return -1;
    }

    private ViewGroup getActiveEmptyStateView() {
        int currentPage = mChooserMultiProfilePagerAdapter.getCurrentPage();
        return mChooserMultiProfilePagerAdapter.getEmptyStateView(currentPage);
    }

    @Override // ResolverListCommunicator
    public void onHandlePackagesChanged(ResolverListAdapter listAdapter) {
        mChooserMultiProfilePagerAdapter.getActiveListAdapter().notifyDataSetChanged();
        super.onHandlePackagesChanged(listAdapter);
    }

    @Override
    public void onListRebuilt(ResolverListAdapter listAdapter, boolean rebuildComplete) {
        setupScrollListener();
        maybeSetupGlobalLayoutListener();

        ChooserListAdapter chooserListAdapter = (ChooserListAdapter) listAdapter;
        if (chooserListAdapter.getUserHandle()
                .equals(mChooserMultiProfilePagerAdapter.getCurrentUserHandle())) {
            mChooserMultiProfilePagerAdapter.getActiveAdapterView()
                    .setAdapter(mChooserMultiProfilePagerAdapter.getCurrentRootAdapter());
            mChooserMultiProfilePagerAdapter
                    .setupListAdapter(mChooserMultiProfilePagerAdapter.getCurrentPage());
        }

        if (chooserListAdapter.getDisplayResolveInfoCount() == 0) {
            chooserListAdapter.notifyDataSetChanged();
        } else {
            chooserListAdapter.updateAlphabeticalList();
        }

        if (rebuildComplete) {
            getChooserActivityLogger().logSharesheetAppLoadComplete();
            maybeQueryAdditionalPostProcessingTargets(chooserListAdapter);
            mLatencyTracker.onActionEnd(ACTION_LOAD_SHARE_SHEET);
        }
    }

    private void maybeQueryAdditionalPostProcessingTargets(ChooserListAdapter chooserListAdapter) {
        UserHandle userHandle = chooserListAdapter.getUserHandle();
        ProfileRecord record = getProfileRecord(userHandle);
        if (record == null) {
            return;
        }
        if (record.shortcutLoader == null) {
            return;
        }
        record.loadingStartTime = SystemClock.elapsedRealtime();
        record.shortcutLoader.queryShortcuts(chooserListAdapter.getDisplayResolveInfos());
    }

    @MainThread
    private void onShortcutsLoaded(UserHandle userHandle, ShortcutLoader.Result result) {
        if (DEBUG) {
            Log.d(TAG, "onShortcutsLoaded for user: " + userHandle);
        }
        mDirectShareShortcutInfoCache.putAll(result.getDirectShareShortcutInfoCache());
        mDirectShareAppTargetCache.putAll(result.getDirectShareAppTargetCache());
        ChooserListAdapter adapter =
                mChooserMultiProfilePagerAdapter.getListAdapterForUserHandle(userHandle);
        if (adapter != null) {
            for (ShortcutLoader.ShortcutResultInfo resultInfo : result.getShortcutsByApp()) {
                adapter.addServiceResults(
                        resultInfo.getAppTarget(),
                        resultInfo.getShortcuts(),
                        result.isFromAppPredictor()
                                ? TARGET_TYPE_SHORTCUTS_FROM_PREDICTION_SERVICE
                                : TARGET_TYPE_SHORTCUTS_FROM_SHORTCUT_MANAGER,
                        mDirectShareShortcutInfoCache,
                        mDirectShareAppTargetCache);
            }
            adapter.completeServiceTargetLoading();
        }

        logDirectShareTargetReceived(userHandle);
        sendVoiceChoicesIfNeeded();
        getChooserActivityLogger().logSharesheetDirectLoadComplete();
    }

    private void setupScrollListener() {
        if (mResolverDrawerLayout == null) {
            return;
        }
        int elevatedViewResId = shouldShowTabs() ? com.android.internal.R.id.tabs : com.android.internal.R.id.chooser_header;
        final View elevatedView = mResolverDrawerLayout.findViewById(elevatedViewResId);
        final float defaultElevation = elevatedView.getElevation();
        final float chooserHeaderScrollElevation =
                getResources().getDimensionPixelSize(R.dimen.chooser_header_scroll_elevation);
        mChooserMultiProfilePagerAdapter.getActiveAdapterView().addOnScrollListener(
                new RecyclerView.OnScrollListener() {
                    public void onScrollStateChanged(RecyclerView view, int scrollState) {
                        if (scrollState == RecyclerView.SCROLL_STATE_IDLE) {
                            if (mScrollStatus == SCROLL_STATUS_SCROLLING_VERTICAL) {
                                mScrollStatus = SCROLL_STATUS_IDLE;
                                setHorizontalScrollingEnabled(true);
                            }
                        } else if (scrollState == RecyclerView.SCROLL_STATE_DRAGGING) {
                            if (mScrollStatus == SCROLL_STATUS_IDLE) {
                                mScrollStatus = SCROLL_STATUS_SCROLLING_VERTICAL;
                                setHorizontalScrollingEnabled(false);
                            }
                        }
                    }

                    public void onScrolled(RecyclerView view, int dx, int dy) {
                        if (view.getChildCount() > 0) {
                            View child = view.getLayoutManager().findViewByPosition(0);
                            if (child == null || child.getTop() < 0) {
                                elevatedView.setElevation(chooserHeaderScrollElevation);
                                return;
                            }
                        }

                        elevatedView.setElevation(defaultElevation);
                    }
                });
    }

    private void maybeSetupGlobalLayoutListener() {
        if (shouldShowTabs()) {
            return;
        }
        final View recyclerView = mChooserMultiProfilePagerAdapter.getActiveAdapterView();
        recyclerView.getViewTreeObserver()
                .addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        // Fixes an issue were the accessibility border disappears on list creation.
                        recyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        final TextView titleView = findViewById(com.android.internal.R.id.title);
                        if (titleView != null) {
                            titleView.setFocusable(true);
                            titleView.setFocusableInTouchMode(true);
                            titleView.requestFocus();
                            titleView.requestAccessibilityFocus();
                        }
                    }
                });
    }

    /**
     * The sticky content preview is shown only when we have a tabbed view. It's shown above
     * the tabs so it is not part of the scrollable list. If we are not in tabbed view,
     * we instead show the content preview as a regular list item.
     */
    private boolean shouldShowStickyContentPreview() {
        return shouldShowStickyContentPreviewNoOrientationCheck()
                && !getResources().getBoolean(R.bool.resolver_landscape_phone);
    }

    private boolean shouldShowStickyContentPreviewNoOrientationCheck() {
        return shouldShowTabs()
                && (mMultiProfilePagerAdapter.getListAdapterForUserHandle(
                UserHandle.of(UserHandle.myUserId())).getCount() > 0
                || shouldShowContentPreviewWhenEmpty())
                && shouldShowContentPreview();
    }

    /**
     * This method could be used to override the default behavior when we hide the preview area
     * when the current tab doesn't have any items.
     *
     * @return true if we want to show the content preview area even if the tab for the current
     *         user is empty
     */
    protected boolean shouldShowContentPreviewWhenEmpty() {
        return false;
    }

    /**
     * @return true if we want to show the content preview area
     */
    protected boolean shouldShowContentPreview() {
        return (mChooserRequest != null) && mChooserRequest.isSendActionTarget();
    }

    private void updateStickyContentPreview() {
        if (shouldShowStickyContentPreviewNoOrientationCheck()) {
            // The sticky content preview is only shown when we show the work and personal tabs.
            // We don't show it in landscape as otherwise there is no room for scrolling.
            // If the sticky content preview will be shown at some point with orientation change,
            // then always preload it to avoid subsequent resizing of the share sheet.
            ViewGroup contentPreviewContainer =
                    findViewById(com.android.internal.R.id.content_preview_container);
            if (contentPreviewContainer.getChildCount() == 0) {
                ViewGroup contentPreviewView = createContentPreviewView(contentPreviewContainer);
                contentPreviewContainer.addView(contentPreviewView);
            }
        }
        if (shouldShowStickyContentPreview()) {
            showStickyContentPreview();
        } else {
            hideStickyContentPreview();
        }
    }

    private void showStickyContentPreview() {
        if (isStickyContentPreviewShowing()) {
            return;
        }
        ViewGroup contentPreviewContainer = findViewById(com.android.internal.R.id.content_preview_container);
        contentPreviewContainer.setVisibility(View.VISIBLE);
    }

    private boolean isStickyContentPreviewShowing() {
        ViewGroup contentPreviewContainer = findViewById(com.android.internal.R.id.content_preview_container);
        return contentPreviewContainer.getVisibility() == View.VISIBLE;
    }

    private void hideStickyContentPreview() {
        if (!isStickyContentPreviewShowing()) {
            return;
        }
        ViewGroup contentPreviewContainer = findViewById(com.android.internal.R.id.content_preview_container);
        contentPreviewContainer.setVisibility(View.GONE);
    }

    private void startFinishAnimation() {
        View rootView = findRootView();
        if (rootView != null) {
            rootView.startAnimation(new FinishAnimation(this, rootView));
        }
    }

    private boolean maybeCancelFinishAnimation() {
        View rootView = findRootView();
        Animation animation = (rootView == null) ? null : rootView.getAnimation();
        if (animation instanceof FinishAnimation) {
            boolean hasEnded = animation.hasEnded();
            animation.cancel();
            rootView.clearAnimation();
            return !hasEnded;
        }
        return false;
    }

    private View findRootView() {
        if (mContentView == null) {
            mContentView = findViewById(android.R.id.content);
        }
        return mContentView;
    }

    /**
     * Intentionally override the {@link ResolverActivity} implementation as we only need that
     * implementation for the intent resolver case.
     */
    @Override
    public void onButtonClick(View v) {}

    /**
     * Intentionally override the {@link ResolverActivity} implementation as we only need that
     * implementation for the intent resolver case.
     */
    @Override
    protected void resetButtonBar() {}

    @Override
    protected String getMetricsCategory() {
        return METRICS_CATEGORY_CHOOSER;
    }

    @Override
    protected void onProfileTabSelected() {
        ChooserGridAdapter currentRootAdapter =
                mChooserMultiProfilePagerAdapter.getCurrentRootAdapter();
        currentRootAdapter.updateDirectShareExpansion();
        // This fixes an edge case where after performing a variety of gestures, vertical scrolling
        // ends up disabled. That's because at some point the old tab's vertical scrolling is
        // disabled and the new tab's is enabled. For context, see b/159997845
        setVerticalScrollEnabled(true);
        if (mResolverDrawerLayout != null) {
            mResolverDrawerLayout.scrollNestedScrollableChildBackToTop();
        }
    }

    @Override
    protected WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
        if (shouldShowTabs()) {
            mChooserMultiProfilePagerAdapter
                    .setEmptyStateBottomOffset(insets.getSystemWindowInsetBottom());
            mChooserMultiProfilePagerAdapter.setupContainerPadding(
                    getActiveEmptyStateView().findViewById(com.android.internal.R.id.resolver_empty_state_container));
        }

        WindowInsets result = super.onApplyWindowInsets(v, insets);
        if (mResolverDrawerLayout != null) {
            mResolverDrawerLayout.requestLayout();
        }
        return result;
    }

    private void setHorizontalScrollingEnabled(boolean enabled) {
        ResolverViewPager viewPager = findViewById(com.android.internal.R.id.profile_pager);
        viewPager.setSwipingEnabled(enabled);
    }

    private void setVerticalScrollEnabled(boolean enabled) {
        ChooserGridLayoutManager layoutManager =
                (ChooserGridLayoutManager) mChooserMultiProfilePagerAdapter.getActiveAdapterView()
                        .getLayoutManager();
        layoutManager.setVerticalScrollEnabled(enabled);
    }

    @Override
    void onHorizontalSwipeStateChanged(int state) {
        if (state == ViewPager.SCROLL_STATE_DRAGGING) {
            if (mScrollStatus == SCROLL_STATUS_IDLE) {
                mScrollStatus = SCROLL_STATUS_SCROLLING_HORIZONTAL;
                setVerticalScrollEnabled(false);
            }
        } else if (state == ViewPager.SCROLL_STATE_IDLE) {
            if (mScrollStatus == SCROLL_STATUS_SCROLLING_HORIZONTAL) {
                mScrollStatus = SCROLL_STATUS_IDLE;
                setVerticalScrollEnabled(true);
            }
        }
    }

    /**
     * Used in combination with the scene transition when launching the image editor
     */
    private static class FinishAnimation extends AlphaAnimation implements
            Animation.AnimationListener {
        @Nullable
        private Activity mActivity;
        @Nullable
        private View mRootView;
        private final float mFromAlpha;

        FinishAnimation(@NonNull Activity activity, @NonNull View rootView) {
            super(rootView.getAlpha(), 0.0f);
            mActivity = activity;
            mRootView = rootView;
            mFromAlpha = rootView.getAlpha();
            setInterpolator(new LinearInterpolator());
            long duration = activity.getWindow().getTransitionBackgroundFadeDuration();
            setDuration(duration);
            // The scene transition animation looks better when it's not overlapped with this
            // fade-out animation thus the delay.
            // It is most likely that the image editor will cause this activity to stop and this
            // animation will be cancelled in the background without running (i.e. we'll animate
            // only when this activity remains partially visible after the image editor launch).
            setStartOffset(duration);
            super.setAnimationListener(this);
        }

        @Override
        public void setAnimationListener(AnimationListener listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void cancel() {
            if (mRootView != null) {
                mRootView.setAlpha(mFromAlpha);
            }
            cleanup();
            super.cancel();
        }

        @Override
        public void onAnimationStart(Animation animation) {
        }

        @Override
        public void onAnimationEnd(Animation animation) {
            Activity activity = mActivity;
            cleanup();
            if (activity != null) {
                activity.finish();
            }
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }

        private void cleanup() {
            mActivity = null;
            mRootView = null;
        }
    }

    @Override
    protected void maybeLogProfileChange() {
        getChooserActivityLogger().logSharesheetProfileChanged();
    }

    private static class ProfileRecord {
        /** The {@link AppPredictor} for this profile, if any. */
        @Nullable
        public final AppPredictor appPredictor;
        /**
         * null if we should not load shortcuts.
         */
        @Nullable
        public final ShortcutLoader shortcutLoader;
        public long loadingStartTime;

        private ProfileRecord(
                @Nullable AppPredictor appPredictor,
                @Nullable ShortcutLoader shortcutLoader) {
            this.appPredictor = appPredictor;
            this.shortcutLoader = shortcutLoader;
        }

        public void destroy() {
            if (shortcutLoader != null) {
                shortcutLoader.destroy();
            }
            if (appPredictor != null) {
                appPredictor.destroy();
            }
        }
    }
}
