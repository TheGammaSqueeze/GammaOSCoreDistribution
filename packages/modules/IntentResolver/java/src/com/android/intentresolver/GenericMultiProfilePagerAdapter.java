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

package com.android.intentresolver;

import android.annotation.Nullable;
import android.content.Context;
import android.os.UserHandle;
import android.view.View;
import android.view.ViewGroup;

import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Implementation of {@link AbstractMultiProfilePagerAdapter} that consolidates the variation in
 * existing implementations; most overrides were only to vary type signatures (which are better
 * represented via generic types), and a few minor behavioral customizations are now implemented
 * through small injectable delegate classes.
 * TODO: now that the existing implementations are shown to be expressible in terms of this new
 * generic type, merge up into the base class and simplify the public APIs.
 * TODO: attempt to further restrict visibility in the methods we expose.
 * TODO: deprecate and audit/fix usages of any methods that refer to the "active" or "inactive"
 * adapters; these were marked {@link VisibleForTesting} and their usage seems like an accident
 * waiting to happen since clients seem to make assumptions about which adapter will be "active" in
 * a particular context, and more explicit APIs would make sure those were valid.
 * TODO: consider renaming legacy methods (e.g. why do we know it's a "list", not just a "page"?)
 *
 * @param <PageViewT> the type of the widget that represents the contents of a page in this adapter
 * @param <SinglePageAdapterT> the type of a "root" adapter class to be instantiated and included in
 * the per-profile records.
 * @param <ListAdapterT> the concrete type of a {@link ResolverListAdapter} implementation to
 * control the contents of a given per-profile list. This is provided for convenience, since it must
 * be possible to get the list adapter from the page adapter via our {@link mListAdapterExtractor}.
 *
 * TODO: this class doesn't make any explicit usage of the {@link ResolverListAdapter} API, so the
 * type constraint can probably be dropped once the API is merged upwards and cleaned.
 */
class GenericMultiProfilePagerAdapter<
        PageViewT extends ViewGroup,
        SinglePageAdapterT,
        ListAdapterT extends ResolverListAdapter> extends AbstractMultiProfilePagerAdapter {

    /** Delegate to set up a given adapter and page view to be used together. */
    public interface AdapterBinder<PageViewT, SinglePageAdapterT> {
        /**
         * The given {@code view} will be associated with the given {@code adapter}. Do any work
         * necessary to configure them compatibly, introduce them to each other, etc.
         */
        void bind(PageViewT view, SinglePageAdapterT adapter);
    }

    private final Function<SinglePageAdapterT, ListAdapterT> mListAdapterExtractor;
    private final AdapterBinder<PageViewT, SinglePageAdapterT> mAdapterBinder;
    private final Supplier<ViewGroup> mPageViewInflater;
    private final Supplier<Optional<Integer>> mContainerBottomPaddingOverrideSupplier;

    private final ImmutableList<GenericProfileDescriptor<PageViewT, SinglePageAdapterT>> mItems;

    GenericMultiProfilePagerAdapter(
            Context context,
            Function<SinglePageAdapterT, ListAdapterT> listAdapterExtractor,
            AdapterBinder<PageViewT, SinglePageAdapterT> adapterBinder,
            ImmutableList<SinglePageAdapterT> adapters,
            EmptyStateProvider emptyStateProvider,
            Supplier<Boolean> workProfileQuietModeChecker,
            @Profile int defaultProfile,
            UserHandle workProfileUserHandle,
            Supplier<ViewGroup> pageViewInflater,
            Supplier<Optional<Integer>> containerBottomPaddingOverrideSupplier) {
        super(
                context,
                /* currentPage= */ defaultProfile,
                emptyStateProvider,
                workProfileQuietModeChecker,
                workProfileUserHandle);

        mListAdapterExtractor = listAdapterExtractor;
        mAdapterBinder = adapterBinder;
        mPageViewInflater = pageViewInflater;
        mContainerBottomPaddingOverrideSupplier = containerBottomPaddingOverrideSupplier;

        ImmutableList.Builder<GenericProfileDescriptor<PageViewT, SinglePageAdapterT>> items =
                new ImmutableList.Builder<>();
        for (SinglePageAdapterT adapter : adapters) {
            items.add(createProfileDescriptor(adapter));
        }
        mItems = items.build();
    }

    private GenericProfileDescriptor<PageViewT, SinglePageAdapterT>
            createProfileDescriptor(SinglePageAdapterT adapter) {
        return new GenericProfileDescriptor<>(mPageViewInflater.get(), adapter);
    }

    @Override
    protected GenericProfileDescriptor<PageViewT, SinglePageAdapterT> getItem(int pageIndex) {
        return mItems.get(pageIndex);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    public PageViewT getListViewForIndex(int index) {
        return getItem(index).mView;
    }

    @Override
    @VisibleForTesting
    public SinglePageAdapterT getAdapterForIndex(int index) {
        return getItem(index).mAdapter;
    }

    @Override
    protected void setupListAdapter(int pageIndex) {
        mAdapterBinder.bind(getListViewForIndex(pageIndex), getAdapterForIndex(pageIndex));
    }

    @Override
    public ViewGroup instantiateItem(ViewGroup container, int position) {
        setupListAdapter(position);
        return super.instantiateItem(container, position);
    }

    @Override
    @Nullable
    protected ListAdapterT getListAdapterForUserHandle(UserHandle userHandle) {
        if (getActiveListAdapter().getUserHandle().equals(userHandle)) {
            return getActiveListAdapter();
        }
        if ((getInactiveListAdapter() != null) && getInactiveListAdapter().getUserHandle().equals(
                userHandle)) {
            return getInactiveListAdapter();
        }
        return null;
    }

    @Override
    @VisibleForTesting
    public ListAdapterT getActiveListAdapter() {
        return mListAdapterExtractor.apply(getAdapterForIndex(getCurrentPage()));
    }

    @Override
    @VisibleForTesting
    public ListAdapterT getInactiveListAdapter() {
        if (getCount() < 2) {
            return null;
        }
        return mListAdapterExtractor.apply(getAdapterForIndex(1 - getCurrentPage()));
    }

    @Override
    public ListAdapterT getPersonalListAdapter() {
        return mListAdapterExtractor.apply(getAdapterForIndex(PROFILE_PERSONAL));
    }

    @Override
    public ListAdapterT getWorkListAdapter() {
        return mListAdapterExtractor.apply(getAdapterForIndex(PROFILE_WORK));
    }

    @Override
    protected SinglePageAdapterT getCurrentRootAdapter() {
        return getAdapterForIndex(getCurrentPage());
    }

    @Override
    protected PageViewT getActiveAdapterView() {
        return getListViewForIndex(getCurrentPage());
    }

    @Override
    protected PageViewT getInactiveAdapterView() {
        if (getCount() < 2) {
            return null;
        }
        return getListViewForIndex(1 - getCurrentPage());
    }

    @Override
    protected void setupContainerPadding(View container) {
        Optional<Integer> bottomPaddingOverride = mContainerBottomPaddingOverrideSupplier.get();
        bottomPaddingOverride.ifPresent(paddingBottom ->
                container.setPadding(
                    container.getPaddingLeft(),
                    container.getPaddingTop(),
                    container.getPaddingRight(),
                    paddingBottom));
    }

    // TODO: `ChooserActivity` also has a per-profile record type. Maybe the "multi-profile pager"
    // should be the owner of all per-profile data (especially now that the API is generic)?
    private static class GenericProfileDescriptor<PageViewT, SinglePageAdapterT> extends
            ProfileDescriptor {
        private final SinglePageAdapterT mAdapter;
        private final PageViewT mView;

        GenericProfileDescriptor(ViewGroup rootView, SinglePageAdapterT adapter) {
            super(rootView);
            mAdapter = adapter;
            mView = (PageViewT) rootView.findViewById(com.android.internal.R.id.resolver_list);
        }
    }
}
