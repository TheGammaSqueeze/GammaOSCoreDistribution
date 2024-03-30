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

package com.android.permissioncontroller.permission.ui.handheld.v31;

import static com.android.permissioncontroller.Constants.EXTRA_SESSION_ID;
import static com.android.permissioncontroller.Constants.INVALID_SESSION_ID;
import static com.android.permissioncontroller.permission.ui.handheld.v31.DashboardUtilsKt.is7DayToggleEnabled;

import android.app.ActionBar;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

import com.android.permissioncontroller.PermissionControllerApplication;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.model.v31.AppPermissionUsage;
import com.android.permissioncontroller.permission.model.v31.PermissionUsages;
import com.android.permissioncontroller.permission.model.legacy.PermissionApps;
import com.android.permissioncontroller.permission.ui.ManagePermissionsActivity;
import com.android.permissioncontroller.permission.ui.handheld.SettingsWithLargeHeader;
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageDetailsViewModel;
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageDetailsViewModelFactory;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.permission.utils.Utils;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The permission details page showing the history/timeline of a permission
 */
@RequiresApi(Build.VERSION_CODES.S)
public class PermissionDetailsFragment extends SettingsWithLargeHeader implements
        PermissionUsages.PermissionsUsagesChangeCallback {

    public static final int FILTER_7_DAYS = 1;
    private static final String KEY_SHOW_SYSTEM_PREFS = "_show_system";
    private static final String SHOW_SYSTEM_KEY = PermissionDetailsFragment.class.getName()
            + KEY_SHOW_SYSTEM_PREFS;

    private static final String KEY_SESSION_ID = "_session_id";
    private static final String SESSION_ID_KEY = PermissionDetailsFragment.class.getName()
            + KEY_SESSION_ID;

    private static final int MENU_SHOW_7_DAYS_DATA = Menu.FIRST + 4;
    private static final int MENU_SHOW_24_HOURS_DATA = Menu.FIRST + 5;

    private @Nullable String mFilterGroup;
    private int mFilterTimeIndex;
    private @Nullable List<AppPermissionUsage> mAppPermissionUsages = new ArrayList<>();
    private @NonNull PermissionUsages mPermissionUsages;
    private boolean mFinishedInitialLoad;

    private boolean mShowSystem;
    private boolean mHasSystemApps;
    private boolean mShow7Days;

    private MenuItem mShowSystemMenu;
    private MenuItem mHideSystemMenu;
    private MenuItem mShow7DaysDataMenu;
    private MenuItem mShow24HoursDataMenu;
    private @NonNull RoleManager mRoleManager;

    private PermissionUsageDetailsViewModel mViewModel;

    private long mSessionId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFinishedInitialLoad = false;
        mFilterTimeIndex = FILTER_7_DAYS;

        if (savedInstanceState != null) {
            mShowSystem = savedInstanceState.getBoolean(SHOW_SYSTEM_KEY);
            mSessionId = savedInstanceState.getLong(SESSION_ID_KEY);
        } else {
            mShowSystem = getArguments().getBoolean(
                    ManagePermissionsActivity.EXTRA_SHOW_SYSTEM, false);
            mShow7Days = is7DayToggleEnabled() && getArguments().getBoolean(
                    ManagePermissionsActivity.EXTRA_SHOW_7_DAYS, false);
            mSessionId = getArguments().getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID);
        }

        if (mFilterGroup == null) {
            mFilterGroup = getArguments().getString(Intent.EXTRA_PERMISSION_GROUP_NAME);
        }

        setHasOptionsMenu(true);
        ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        Context context = getPreferenceManager().getContext();

        mPermissionUsages = new PermissionUsages(context);
        mRoleManager = Utils.getSystemServiceSafe(context, RoleManager.class);

        PermissionUsageDetailsViewModelFactory factory = new PermissionUsageDetailsViewModelFactory(
                PermissionControllerApplication.get(), mRoleManager, mFilterGroup, mSessionId);
        mViewModel = new ViewModelProvider(this, factory).get(
                PermissionUsageDetailsViewModel.class);

        reloadData();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) super.onCreateView(inflater, container,
                savedInstanceState);

        PermissionDetailsWrapperFragment parentFragment = (PermissionDetailsWrapperFragment)
                requireParentFragment();
        CoordinatorLayout coordinatorLayout = parentFragment.getCoordinatorLayout();
        inflater.inflate(R.layout.permission_details_extended_fab, coordinatorLayout);
        ExtendedFloatingActionButton extendedFab = coordinatorLayout.requireViewById(
                R.id.extended_fab);
        // Load the background tint color from the application theme
        // rather than the Material Design theme
        Activity activity = getActivity();
        ColorStateList backgroundColor = activity.getColorStateList(
                android.R.color.system_accent3_100);
        extendedFab.setBackgroundTintList(backgroundColor);
        extendedFab.setText(R.string.manage_permission);
        boolean isUiModeNight = (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int textColorAttr = isUiModeNight ? android.R.attr.textColorPrimaryInverse
                : android.R.attr.textColorPrimary;
        TypedArray typedArray = activity.obtainStyledAttributes(new int[] { textColorAttr });
        ColorStateList textColor = typedArray.getColorStateList(0);
        typedArray.recycle();
        extendedFab.setTextColor(textColor);
        extendedFab.setIcon(activity.getDrawable(R.drawable.ic_settings_outline));
        extendedFab.setVisibility(View.VISIBLE);
        extendedFab.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_MANAGE_PERMISSION_APPS)
                    .putExtra(Intent.EXTRA_PERMISSION_NAME, mFilterGroup);
            startActivity(intent);
        });
        RecyclerView recyclerView = getListView();
        int bottomPadding = getResources()
                .getDimensionPixelSize(R.dimen.privhub_details_recycler_view_bottom_padding);
        recyclerView.setPadding(0, 0, 0, bottomPadding);
        recyclerView.setClipToPadding(false);
        recyclerView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        CharSequence title = getString(R.string.permission_history_title);
        if (mFilterGroup != null) {
            title = getResources().getString(R.string.permission_group_usage_title,
                    KotlinUtils.INSTANCE.getPermGroupLabel(getActivity(), mFilterGroup));
        }
        getActivity().setTitle(title);
    }

    @Override
    public void onPermissionUsagesChanged() {
        if (mPermissionUsages.getUsages().isEmpty()) {
            return;
        }
        mAppPermissionUsages = new ArrayList<>(mPermissionUsages.getUsages());

        // Ensure the group name is valid.
        if (mViewModel.getGroup(mFilterGroup, mAppPermissionUsages) == null) {
            mFilterGroup = null;
        }

        updateUI();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SHOW_SYSTEM_KEY, mShowSystem);
        outState.putLong(SESSION_ID_KEY, mSessionId);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        mShowSystemMenu = menu.add(Menu.NONE, MENU_SHOW_SYSTEM, Menu.NONE,
                R.string.menu_show_system);
        mHideSystemMenu = menu.add(Menu.NONE, MENU_HIDE_SYSTEM, Menu.NONE,
                R.string.menu_hide_system);
        if (is7DayToggleEnabled()) {
            mShow7DaysDataMenu = menu.add(Menu.NONE, MENU_SHOW_7_DAYS_DATA, Menu.NONE,
                    R.string.menu_show_7_days_data);
            mShow24HoursDataMenu = menu.add(Menu.NONE, MENU_SHOW_24_HOURS_DATA, Menu.NONE,
                    R.string.menu_show_24_hours_data);
        }

        updateMenu();
    }

    private void updateMenu() {
        if (mHasSystemApps) {
            mShowSystemMenu.setVisible(!mShowSystem);
            mShowSystemMenu.setEnabled(true);

            mHideSystemMenu.setVisible(mShowSystem);
            mHideSystemMenu.setEnabled(true);
        } else {
            mShowSystemMenu.setVisible(true);
            mShowSystemMenu.setEnabled(false);

            mHideSystemMenu.setVisible(false);
            mHideSystemMenu.setEnabled(false);
        }

        if (mShow7DaysDataMenu != null) {
            mShow7DaysDataMenu.setVisible(!mShow7Days);
        }

        if (mShow24HoursDataMenu != null) {
            mShow24HoursDataMenu.setVisible(mShow7Days);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case android.R.id.home:
                getActivity().finishAfterTransition();
                return true;
            case MENU_SHOW_SYSTEM:
            case MENU_HIDE_SYSTEM:
                mShowSystem = itemId == MENU_SHOW_SYSTEM;
                // We already loaded all data, so don't reload
                updateUI();
                updateMenu();
                break;
            case MENU_SHOW_7_DAYS_DATA:
            case MENU_SHOW_24_HOURS_DATA:
                mShow7Days = is7DayToggleEnabled() && itemId == MENU_SHOW_7_DAYS_DATA;
                updateUI();
                updateMenu();
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    private void updateUI() {
        if (mAppPermissionUsages.isEmpty() || getActivity() == null) {
            return;
        }
        Context context = getActivity();
        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            screen = getPreferenceManager().createPreferenceScreen(context);
            setPreferenceScreen(screen);
        }
        screen.removeAll();

        Set<String> exemptedPackages = Utils.getExemptedPackages(mRoleManager);

        Preference subtitlePreference = new Preference(context);

        int usageSubtitle = mShow7Days
                ? R.string.permission_group_usage_subtitle_7d
                : R.string.permission_group_usage_subtitle_24h;
        subtitlePreference.setSummary(
                getResources().getString(usageSubtitle,
                        KotlinUtils.INSTANCE.getPermGroupLabel(getActivity(), mFilterGroup)));
        subtitlePreference.setSelectable(false);
        screen.addPreference(subtitlePreference);

        AtomicBoolean seenSystemApp = new AtomicBoolean(false);

        ArrayList<PermissionApps.PermissionApp> permApps = new ArrayList<>();
        List<PermissionUsageDetailsViewModel.AppPermissionUsageEntry> usages =
                mViewModel.parseUsages(mAppPermissionUsages, exemptedPackages, permApps,
                        seenSystemApp, mShowSystem, mShow7Days);

        if (mHasSystemApps != seenSystemApp.get()) {
            mHasSystemApps = seenSystemApp.get();
            getActivity().invalidateOptionsMenu();
        }

        // Make these variables effectively final so that
        // we can use these captured variables in the below lambda expression
        PreferenceFactory preferenceFactory = new PreferenceFactory(requireActivity());
        AtomicReference<PreferenceCategory> category = new AtomicReference<>(
                preferenceFactory.createDayCategoryPreference());
        screen.addPreference(category.get());
        PreferenceScreen finalScreen = screen;

        new PermissionApps.AppDataLoader(context, () -> {
            if (getActivity() == null) {
                // Fragment has no Activity, return.
                return;
            }
            mViewModel.renderTimelinePreferences(usages, category, finalScreen, preferenceFactory);

            setLoading(false, true);
            mFinishedInitialLoad = true;
            setProgressBarVisible(false);
            mPermissionUsages.stopLoader(getActivity().getLoaderManager());

        }).execute(permApps.toArray(new PermissionApps.PermissionApp[permApps.size()]));
    }

    private static class PreferenceFactory implements
            PermissionUsageDetailsViewModel.HistoryPreferenceFactory {

        private Context mContext;

        PreferenceFactory(Context context) {
            mContext = context;
        }

        @Override
        public PreferenceCategory createDayCategoryPreference() {
            PreferenceCategory category = new PreferenceCategory(mContext);
            // Do not reserve icon space, so that the text moves all the way left.
            category.setIconSpaceReserved(false);
            return category;
        }

        @Override
        public Preference createPermissionHistoryPreference(
                PermissionUsageDetailsViewModel.HistoryPreferenceData historyPreferenceData) {
            return new PermissionHistoryPreference(mContext,
                    historyPreferenceData.getUserHandle(),
                    historyPreferenceData.getPkgName(),
                    historyPreferenceData.getAppIcon(),
                    historyPreferenceData.getPreferenceTitle(),
                    historyPreferenceData.getPermissionGroup(),
                    historyPreferenceData.getAccessTime(),
                    historyPreferenceData.getSummaryText(),
                    historyPreferenceData.getShowingAttribution(),
                    historyPreferenceData.getAccessTimeList(),
                    historyPreferenceData.getAttributionTags(),
                    historyPreferenceData.isLastUsage(),
                    historyPreferenceData.getSessionId()
            );
        }
    }

    private void reloadData() {
        mViewModel.loadPermissionUsages(getActivity().getLoaderManager(),
                mPermissionUsages, this, mFilterTimeIndex);
        if (mFinishedInitialLoad) {
            setProgressBarVisible(true);
        }
    }
}
