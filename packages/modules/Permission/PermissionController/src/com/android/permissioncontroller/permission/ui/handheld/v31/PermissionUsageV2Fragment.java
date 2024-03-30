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
import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_USAGE_FRAGMENT_INTERACTION;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_USAGE_FRAGMENT_INTERACTION__ACTION__SEE_OTHER_PERMISSIONS_CLICKED;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_USAGE_FRAGMENT_INTERACTION__ACTION__SHOW_SYSTEM_CLICKED;
import static com.android.permissioncontroller.PermissionControllerStatsLog.write;
import static com.android.permissioncontroller.permission.ui.handheld.v31.DashboardUtilsKt.is7DayToggleEnabled;

import android.app.ActionBar;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.model.v31.AppPermissionUsage;
import com.android.permissioncontroller.permission.model.v31.PermissionUsages;
import com.android.permissioncontroller.permission.model.legacy.PermissionApps;
import com.android.permissioncontroller.permission.ui.handheld.SettingsWithLargeHeader;
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageViewModel;
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageViewModelFactory;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.permission.utils.Utils;
import com.android.settingslib.HelpUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import kotlin.Triple;

/**
 * The main page for the privacy dashboard.
 */
@RequiresApi(Build.VERSION_CODES.S)
public class PermissionUsageV2Fragment extends SettingsWithLargeHeader implements
        PermissionUsages.PermissionsUsagesChangeCallback {

    // Pie chart in this screen will be the first child.
    // Hence we use PERMISSION_GROUP_ORDER + 1 here.
    private static final int PERMISSION_USAGE_INITIAL_EXPANDED_CHILDREN_COUNT =
            PermissionUsageViewModel.Companion.getPERMISSION_GROUP_ORDER().size() + 1;
    private static final int EXPAND_BUTTON_ORDER = 999;

    private static final String KEY_SESSION_ID = "_session_id";
    private static final String SESSION_ID_KEY = PermissionUsageV2Fragment.class.getName()
            + KEY_SESSION_ID;

    private static final int MENU_SHOW_7_DAYS_DATA = Menu.FIRST + 4;
    private static final int MENU_SHOW_24_HOURS_DATA = Menu.FIRST + 5;
    private static final int MENU_REFRESH = Menu.FIRST + 6;

    private @NonNull PermissionUsages mPermissionUsages;
    private @Nullable List<AppPermissionUsage> mAppPermissionUsages = new ArrayList<>();

    private PermissionUsageViewModel mViewModel;

    private boolean mShowSystem;
    private boolean mHasSystemApps;
    private MenuItem mShowSystemMenu;
    private MenuItem mHideSystemMenu;
    private boolean mShow7Days;
    private MenuItem mShow7DaysDataMenu;
    private MenuItem mShow24HoursDataMenu;
    private boolean mOtherExpanded;

    private boolean mFinishedInitialLoad;

    private @NonNull RoleManager mRoleManager;

    private PermissionUsageGraphicPreference mGraphic;

    /** Unique Id of a request */
    private long mSessionId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mSessionId = savedInstanceState.getLong(SESSION_ID_KEY);
        } else {
            mSessionId = getArguments().getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID);
        }

        mFinishedInitialLoad = false;

        // By default, do not show system app usages.
        mShowSystem = false;

        // By default, show permission usages for the past 24 hours.
        mShow7Days = false;

        // Start out with 'other' permissions not expanded.
        mOtherExpanded = false;

        setLoading(true, false);
        setHasOptionsMenu(true);
        ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        Context context = getPreferenceManager().getContext();
        mPermissionUsages = new PermissionUsages(context);
        mRoleManager = Utils.getSystemServiceSafe(context, RoleManager.class);

        PermissionUsageViewModelFactory factory = new PermissionUsageViewModelFactory(mRoleManager);
        mViewModel = new ViewModelProvider(this, factory).get(PermissionUsageViewModel.class);

        reloadData();
    }

    @Override
    public RecyclerView.Adapter onCreateAdapter(PreferenceScreen preferenceScreen) {
        PreferenceGroupAdapter adapter =
                (PreferenceGroupAdapter) super.onCreateAdapter(preferenceScreen);

        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                updatePreferenceScreenAdvancedTitleAndSummary(preferenceScreen, adapter);
            }

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                onChanged();
            }

            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                onChanged();
            }

            @Override
            public void onItemRangeChanged(int positionStart, int itemCount) {
                onChanged();
            }

            @Override
            public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                onChanged();
            }
        });

        updatePreferenceScreenAdvancedTitleAndSummary(preferenceScreen, adapter);
        return adapter;
    }

    private void updatePreferenceScreenAdvancedTitleAndSummary(PreferenceScreen preferenceScreen,
            PreferenceGroupAdapter adapter) {
        int count = adapter.getItemCount();
        if (count == 0) {
            return;
        }

        Preference preference = adapter.getItem(count - 1);

        // This is a hacky way of getting the expand button preference for advanced info
        if (preference.getOrder() == EXPAND_BUTTON_ORDER) {
            mOtherExpanded = false;
            preference.setTitle(R.string.perm_usage_adv_info_title);
            preference.setSummary(preferenceScreen.getSummary());
            preference.setLayoutResource(R.layout.expand_button_with_large_title);
            if (mGraphic != null) {
                mGraphic.setShowOtherCategory(false);
            }
        } else {
            mOtherExpanded = true;
            if (mGraphic != null) {
                mGraphic.setShowOtherCategory(true);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        getActivity().setTitle(R.string.permission_usage_title);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (mHasSystemApps) {
            mShowSystemMenu = menu.add(Menu.NONE, MENU_SHOW_SYSTEM, Menu.NONE,
                    R.string.menu_show_system);
            mHideSystemMenu = menu.add(Menu.NONE, MENU_HIDE_SYSTEM, Menu.NONE,
                    R.string.menu_hide_system);
        }

        if (is7DayToggleEnabled()) {
            mShow7DaysDataMenu = menu.add(Menu.NONE, MENU_SHOW_7_DAYS_DATA, Menu.NONE,
                    R.string.menu_show_7_days_data);
            mShow24HoursDataMenu = menu.add(Menu.NONE, MENU_SHOW_24_HOURS_DATA, Menu.NONE,
                    R.string.menu_show_24_hours_data);
        }

        HelpUtils.prepareHelpMenuItem(getActivity(), menu, R.string.help_permission_usage,
                getClass().getName());
        MenuItem refresh = menu.add(Menu.NONE, MENU_REFRESH, Menu.NONE,
                R.string.permission_usage_refresh);
        refresh.setIcon(R.drawable.ic_refresh);
        refresh.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        updateMenu();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case android.R.id.home:
                getActivity().finishAfterTransition();
                return true;
            case MENU_SHOW_SYSTEM:
                write(PERMISSION_USAGE_FRAGMENT_INTERACTION, mSessionId,
                        PERMISSION_USAGE_FRAGMENT_INTERACTION__ACTION__SHOW_SYSTEM_CLICKED);
                // fall through
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
            case MENU_REFRESH:
                reloadData();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateMenu() {
        if (mHasSystemApps) {
            mShowSystemMenu.setVisible(!mShowSystem);
            mHideSystemMenu.setVisible(mShowSystem);
        }

        if (mShow7DaysDataMenu != null) {
            mShow7DaysDataMenu.setVisible(!mShow7Days);
        }

        if (mShow24HoursDataMenu != null) {
            mShow24HoursDataMenu.setVisible(mShow7Days);
        }
    }

    @Override
    public void onPermissionUsagesChanged() {
        if (mPermissionUsages.getUsages().isEmpty()) {
            return;
        }
        mAppPermissionUsages = new ArrayList<>(mPermissionUsages.getUsages());
        updateUI();
    }

    @Override
    public int getEmptyViewString() {
        return R.string.no_permission_usages;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (outState != null) {
            outState.putLong(SESSION_ID_KEY, mSessionId);
        }
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

        if (mOtherExpanded) {
            screen.setInitialExpandedChildrenCount(Integer.MAX_VALUE);
        } else {
            screen.setInitialExpandedChildrenCount(
                    PERMISSION_USAGE_INITIAL_EXPANDED_CHILDREN_COUNT);
        }
        screen.setOnExpandButtonClickListener(() -> {
            write(PERMISSION_USAGE_FRAGMENT_INTERACTION, mSessionId,
                    PERMISSION_USAGE_FRAGMENT_INTERACTION__ACTION__SEE_OTHER_PERMISSIONS_CLICKED);
        });

        Triple<Map<String, Integer>, ArrayList<PermissionApps.PermissionApp>, Boolean>
                triple = mViewModel.extractUsages(mAppPermissionUsages, mShow7Days, mShowSystem);
        Map<String, Integer> usages = triple.getFirst();
        ArrayList<PermissionApps.PermissionApp> permApps = triple.getSecond();
        boolean seenSystemApp = triple.getThird();

        if (mHasSystemApps != seenSystemApp) {
            mHasSystemApps = seenSystemApp;
            getActivity().invalidateOptionsMenu();
        }

        mGraphic = new PermissionUsageGraphicPreference(context, mShow7Days);
        screen.addPreference(mGraphic);
        mGraphic.setUsages(usages);

        // Add the preference header.
        PreferenceCategory category = new PreferenceCategory(context);
        screen.addPreference(category);
        List<Map.Entry<String, Integer>> groupUsagesList = mViewModel.createGroupUsagesList(
                getContext(), usages);

        CharSequence advancedInfoSummary = getAdvancedInfoSummaryString(context, groupUsagesList);
        screen.setSummary(advancedInfoSummary);

        addUIContent(context, groupUsagesList, permApps, category);
    }

    private CharSequence getAdvancedInfoSummaryString(Context context,
            List<Map.Entry<String, Integer>> groupUsagesList) {
        int size = groupUsagesList.size();
        if (size <= PERMISSION_USAGE_INITIAL_EXPANDED_CHILDREN_COUNT - 1) {
            return "";
        }

        // case for 1 extra item in the advanced info
        if (size == PERMISSION_USAGE_INITIAL_EXPANDED_CHILDREN_COUNT) {
            String permGroupName = groupUsagesList
                    .get(PERMISSION_USAGE_INITIAL_EXPANDED_CHILDREN_COUNT - 1).getKey();
            return KotlinUtils.INSTANCE.getPermGroupLabel(context, permGroupName);
        }

        String permGroupName1 = groupUsagesList
                .get(PERMISSION_USAGE_INITIAL_EXPANDED_CHILDREN_COUNT - 1).getKey();
        String permGroupName2 = groupUsagesList
                .get(PERMISSION_USAGE_INITIAL_EXPANDED_CHILDREN_COUNT).getKey();
        CharSequence permGroupLabel1 = KotlinUtils
                .INSTANCE.getPermGroupLabel(context, permGroupName1);
        CharSequence permGroupLabel2 = KotlinUtils
                .INSTANCE.getPermGroupLabel(context, permGroupName2);

        // case for 2 extra items in the advanced info
        if (size == PERMISSION_USAGE_INITIAL_EXPANDED_CHILDREN_COUNT + 1) {
            return context.getResources().getString(R.string.perm_usage_adv_info_summary_2_items,
                    permGroupLabel1, permGroupLabel2);
        }

        // case for 3 or more extra items in the advanced info
        int numExtraItems = size - PERMISSION_USAGE_INITIAL_EXPANDED_CHILDREN_COUNT - 1;
        return context.getResources().getString(R.string.perm_usage_adv_info_summary_more_items,
                permGroupLabel1, permGroupLabel2, numExtraItems);
    }

    /**
     * Use the usages and permApps that are previously constructed to add UI content to the page
     */
    private void addUIContent(Context context,
            List<Map.Entry<String, Integer>> usages,
            ArrayList<PermissionApps.PermissionApp> permApps,
            PreferenceCategory category) {
        new PermissionApps.AppDataLoader(context, () -> {
            for (int i = 0; i < usages.size(); i++) {
                Map.Entry<String, Integer> currentEntry = usages.get(i);
                PermissionUsageV2ControlPreference permissionUsagePreference =
                        new PermissionUsageV2ControlPreference(context, currentEntry.getKey(),
                                currentEntry.getValue(), mShowSystem, mSessionId, mShow7Days);
                category.addPreference(permissionUsagePreference);
            }

            setLoading(false, true);
            mFinishedInitialLoad = true;
            setProgressBarVisible(false);

            Activity activity = getActivity();
            if (activity != null) {
                mPermissionUsages.stopLoader(activity.getLoaderManager());
            }
        }).execute(permApps.toArray(new PermissionApps.PermissionApp[0]));
    }

    /**
     * Reloads the data to show.
     */
    private void reloadData() {
        mViewModel.loadPermissionUsages(getActivity().getLoaderManager(), mPermissionUsages, this);
        if (mFinishedInitialLoad) {
            setProgressBarVisible(true);
        }
    }
}
