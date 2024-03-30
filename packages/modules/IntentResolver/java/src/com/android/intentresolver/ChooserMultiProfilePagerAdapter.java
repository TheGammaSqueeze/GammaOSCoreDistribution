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

package com.android.intentresolver;

import android.content.Context;
import android.os.UserHandle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;

import com.android.intentresolver.grid.ChooserGridAdapter;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * A {@link PagerAdapter} which describes the work and personal profile share sheet screens.
 */
@VisibleForTesting
public class ChooserMultiProfilePagerAdapter extends GenericMultiProfilePagerAdapter<
        RecyclerView, ChooserGridAdapter, ChooserListAdapter> {
    private static final int SINGLE_CELL_SPAN_SIZE = 1;

    private final ChooserProfileAdapterBinder mAdapterBinder;
    private final BottomPaddingOverrideSupplier mBottomPaddingOverrideSupplier;

    ChooserMultiProfilePagerAdapter(
            Context context,
            ChooserGridAdapter adapter,
            EmptyStateProvider emptyStateProvider,
            Supplier<Boolean> workProfileQuietModeChecker,
            UserHandle workProfileUserHandle,
            int maxTargetsPerRow) {
        this(
                context,
                new ChooserProfileAdapterBinder(maxTargetsPerRow),
                ImmutableList.of(adapter),
                emptyStateProvider,
                workProfileQuietModeChecker,
                /* defaultProfile= */ 0,
                workProfileUserHandle,
                new BottomPaddingOverrideSupplier(context));
    }

    ChooserMultiProfilePagerAdapter(
            Context context,
            ChooserGridAdapter personalAdapter,
            ChooserGridAdapter workAdapter,
            EmptyStateProvider emptyStateProvider,
            Supplier<Boolean> workProfileQuietModeChecker,
            @Profile int defaultProfile,
            UserHandle workProfileUserHandle,
            int maxTargetsPerRow) {
        this(
                context,
                new ChooserProfileAdapterBinder(maxTargetsPerRow),
                ImmutableList.of(personalAdapter, workAdapter),
                emptyStateProvider,
                workProfileQuietModeChecker,
                defaultProfile,
                workProfileUserHandle,
                new BottomPaddingOverrideSupplier(context));
    }

    private ChooserMultiProfilePagerAdapter(
            Context context,
            ChooserProfileAdapterBinder adapterBinder,
            ImmutableList<ChooserGridAdapter> gridAdapters,
            EmptyStateProvider emptyStateProvider,
            Supplier<Boolean> workProfileQuietModeChecker,
            @Profile int defaultProfile,
            UserHandle workProfileUserHandle,
            BottomPaddingOverrideSupplier bottomPaddingOverrideSupplier) {
        super(
                context,
                        gridAdapter -> gridAdapter.getListAdapter(),
                adapterBinder,
                gridAdapters,
                emptyStateProvider,
                workProfileQuietModeChecker,
                defaultProfile,
                workProfileUserHandle,
                        () -> makeProfileView(context),
                bottomPaddingOverrideSupplier);
        mAdapterBinder = adapterBinder;
        mBottomPaddingOverrideSupplier = bottomPaddingOverrideSupplier;
    }

    public void setMaxTargetsPerRow(int maxTargetsPerRow) {
        mAdapterBinder.setMaxTargetsPerRow(maxTargetsPerRow);
    }

    public void setEmptyStateBottomOffset(int bottomOffset) {
        mBottomPaddingOverrideSupplier.setEmptyStateBottomOffset(bottomOffset);
    }

    private static ViewGroup makeProfileView(Context context) {
        LayoutInflater inflater = LayoutInflater.from(context);
        ViewGroup rootView = (ViewGroup) inflater.inflate(
                R.layout.chooser_list_per_profile, null, false);
        RecyclerView recyclerView = rootView.findViewById(com.android.internal.R.id.resolver_list);
        recyclerView.setAccessibilityDelegateCompat(
                new ChooserRecyclerViewAccessibilityDelegate(recyclerView));
        return rootView;
    }

    private static class BottomPaddingOverrideSupplier implements Supplier<Optional<Integer>> {
        private final Context mContext;
        private int mBottomOffset;

        BottomPaddingOverrideSupplier(Context context) {
            mContext = context;
        }

        public void setEmptyStateBottomOffset(int bottomOffset) {
            mBottomOffset = bottomOffset;
        }

        public Optional<Integer> get() {
            int initialBottomPadding = mContext.getResources().getDimensionPixelSize(
                    R.dimen.resolver_empty_state_container_padding_bottom);
            return Optional.of(initialBottomPadding + mBottomOffset);
        }
    }

    private static class ChooserProfileAdapterBinder implements
            AdapterBinder<RecyclerView, ChooserGridAdapter> {
        private int mMaxTargetsPerRow;

        ChooserProfileAdapterBinder(int maxTargetsPerRow) {
            mMaxTargetsPerRow = maxTargetsPerRow;
        }

        public void setMaxTargetsPerRow(int maxTargetsPerRow) {
            mMaxTargetsPerRow = maxTargetsPerRow;
        }

        @Override
        public void bind(
                RecyclerView recyclerView, ChooserGridAdapter chooserGridAdapter) {
            GridLayoutManager glm = (GridLayoutManager) recyclerView.getLayoutManager();
            glm.setSpanCount(mMaxTargetsPerRow);
            glm.setSpanSizeLookup(
                    new GridLayoutManager.SpanSizeLookup() {
                        @Override
                        public int getSpanSize(int position) {
                            return chooserGridAdapter.shouldCellSpan(position)
                                    ? SINGLE_CELL_SPAN_SIZE
                                    : glm.getSpanCount();
                        }
                    });
        }
    }
}
