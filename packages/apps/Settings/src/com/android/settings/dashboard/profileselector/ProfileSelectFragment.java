/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.settings.dashboard.profileselector;

import static android.app.admin.DevicePolicyResources.Strings.Settings.PERSONAL_CATEGORY_HEADER;
import static android.app.admin.DevicePolicyResources.Strings.Settings.WORK_CATEGORY_HEADER;
import static android.content.Intent.EXTRA_USER_ID;

import android.annotation.IntDef;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.Utils;
import com.android.settings.dashboard.DashboardFragment;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Base fragment class for profile settings.
 */
public abstract class ProfileSelectFragment extends DashboardFragment {

    private static final String TAG = "ProfileSelectFragment";

    /**
     * Denotes the profile type.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            ProfileType.PERSONAL,
            ProfileType.WORK,
            ProfileType.ALL
    })
    public @interface ProfileType {
        /**
         * It is personal work profile.
         */
        int PERSONAL = 1;

        /**
         * It is work profile
         */
        int WORK = 1 << 1;

        /**
         * It is personal and work profile
         */
        int ALL = PERSONAL | WORK;
    }

    /**
     * Used in fragment argument and pass {@link ProfileType} to it
     */
    public static final String EXTRA_PROFILE = "profile";

    /**
     * Used in fragment argument with Extra key {@link SettingsActivity.EXTRA_SHOW_FRAGMENT_TAB}
     */
    public static final int PERSONAL_TAB = 0;

    /**
     * Used in fragment argument with Extra key {@link SettingsActivity.EXTRA_SHOW_FRAGMENT_TAB}
     */
    public static final int WORK_TAB = 1;

    private ViewGroup mContentView;

    private ViewPager2 mViewPager;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mContentView = (ViewGroup) super.onCreateView(inflater, container, savedInstanceState);
        final Activity activity = getActivity();
        final int titleResId = getTitleResId();
        if (titleResId > 0) {
            activity.setTitle(titleResId);
        }
        final int selectedTab = getTabId(activity, getArguments());

        final View tabContainer = mContentView.findViewById(R.id.tab_container);
        mViewPager = tabContainer.findViewById(R.id.view_pager);
        mViewPager.setAdapter(new ProfileSelectFragment.ViewPagerAdapter(this));
        final TabLayout tabs = tabContainer.findViewById(R.id.tabs);
        new TabLayoutMediator(tabs, mViewPager,
                (tab, position) -> tab.setText(getPageTitle(position))
        ).attach();
        mViewPager.registerOnPageChangeCallback(
                new ViewPager2.OnPageChangeCallback() {
                    @Override
                    public void onPageSelected(int position) {
                        super.onPageSelected(position);
                        updateHeight(position);
                    }
                }
        );
        tabContainer.setVisibility(View.VISIBLE);
        final TabLayout.Tab tab = tabs.getTabAt(selectedTab);
        tab.select();

        final FrameLayout listContainer = mContentView.findViewById(android.R.id.list_container);
        listContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        final RecyclerView recyclerView = getListView();
        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        Utils.setActionBarShadowAnimation(activity, getSettingsLifecycle(), recyclerView);

        return mContentView;
    }

    protected boolean forceUpdateHeight() {
        return false;
    }

    private void updateHeight(int position) {
        if (!forceUpdateHeight()) {
            return;
        }
        ViewPagerAdapter adapter = (ViewPagerAdapter) mViewPager.getAdapter();
        if (adapter == null || adapter.getItemCount() <= position) {
            return;
        }

        Fragment fragment = adapter.createFragment(position);
        View newPage = fragment.getView();
        if (newPage != null) {
            int viewWidth = View.MeasureSpec.makeMeasureSpec(newPage.getWidth(),
                    View.MeasureSpec.EXACTLY);
            int viewHeight = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            newPage.measure(viewWidth, viewHeight);
            int currentHeight = mViewPager.getLayoutParams().height;
            int newHeight = newPage.getMeasuredHeight();
            if (newHeight != 0 && currentHeight != newHeight) {
                ViewGroup.LayoutParams layoutParams = mViewPager.getLayoutParams();
                layoutParams.height = newHeight;
                mViewPager.setLayoutParams(layoutParams);
            }
        }
    }

    @Override
    public int getMetricsCategory() {
        return METRICS_CATEGORY_UNKNOWN;
    }

    /**
     * Returns an array of {@link Fragment} to display in the
     * {@link com.google.android.material.tabs.TabLayout}
     */
    public abstract Fragment[] getFragments();

    /**
     * Returns a resource ID of the title
     * Override this if the title needs to be updated dynamically.
     */
    public int getTitleResId() {
        return 0;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.placeholder_preference_screen;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @VisibleForTesting
    int getTabId(Activity activity, Bundle bundle) {
        if (bundle != null) {
            final int extraTab = bundle.getInt(SettingsActivity.EXTRA_SHOW_FRAGMENT_TAB, -1);
            if (extraTab != -1) {
                return extraTab;
            }
            final int userId = bundle.getInt(EXTRA_USER_ID, UserHandle.SYSTEM.getIdentifier());
            final boolean isWorkProfile = UserManager.get(activity).isManagedProfile(userId);
            if (isWorkProfile) {
                return WORK_TAB;
            }
        }
        // Start intent from a specific user eg: adb shell --user 10
        final int intentUser = activity.getIntent().getContentUserHint();
        if (UserManager.get(activity).isManagedProfile(intentUser)) {
            return WORK_TAB;
        }

        return PERSONAL_TAB;
    }

    private CharSequence getPageTitle(int position) {
        final DevicePolicyManager devicePolicyManager =
                getContext().getSystemService(DevicePolicyManager.class);

        if (position == WORK_TAB) {
            return devicePolicyManager.getResources().getString(WORK_CATEGORY_HEADER,
                    () -> getContext().getString(R.string.category_work));
        }

        return devicePolicyManager.getResources().getString(PERSONAL_CATEGORY_HEADER,
                () -> getContext().getString(R.string.category_personal));
    }

    static class ViewPagerAdapter extends FragmentStateAdapter {

        private final Fragment[] mChildFragments;

        ViewPagerAdapter(ProfileSelectFragment fragment) {
            super(fragment);
            mChildFragments = fragment.getFragments();
        }

        @Override
        public Fragment createFragment(int position) {
            return mChildFragments[position];
        }

        @Override
        public int getItemCount() {
            return mChildFragments.length;
        }
    }
}
