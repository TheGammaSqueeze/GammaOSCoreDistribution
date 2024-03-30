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

package com.android.intentresolver.grid;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;

import androidx.recyclerview.widget.RecyclerView;

import com.android.intentresolver.ChooserActivity;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/** Holder for direct share targets in the {@link ChooserGridAdapter}. */
public class DirectShareViewHolder extends ItemGroupViewHolder {
    private final ViewGroup mParent;
    private final List<ViewGroup> mRows;
    private int mCellCountPerRow;

    private boolean mHideDirectShareExpansion = false;
    private int mDirectShareMinHeight = 0;
    private int mDirectShareCurrHeight = 0;
    private int mDirectShareMaxHeight = 0;

    private final boolean[] mCellVisibility;

    private final Supplier<Integer> mDeferredTargetCountSupplier;

    public DirectShareViewHolder(
            ViewGroup parent,
            List<ViewGroup> rows,
            int cellCountPerRow,
            int viewType,
            Supplier<Integer> deferredTargetCountSupplier) {
        super(rows.size() * cellCountPerRow, parent, viewType);

        this.mParent = parent;
        this.mRows = rows;
        this.mCellCountPerRow = cellCountPerRow;
        this.mCellVisibility = new boolean[rows.size() * cellCountPerRow];
        Arrays.fill(mCellVisibility, true);
        this.mDeferredTargetCountSupplier = deferredTargetCountSupplier;
    }

    public ViewGroup addView(int index, View v) {
        ViewGroup row = getRowByIndex(index);
        row.addView(v);
        mCells[index] = v;

        return row;
    }

    public ViewGroup getViewGroup() {
        return mParent;
    }

    public ViewGroup getRowByIndex(int index) {
        return mRows.get(index / mCellCountPerRow);
    }

    public ViewGroup getRow(int rowNumber) {
        return mRows.get(rowNumber);
    }

    public void measure() {
        final int spec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        getRow(0).measure(spec, spec);
        getRow(1).measure(spec, spec);

        mDirectShareMinHeight = getRow(0).getMeasuredHeight();
        mDirectShareCurrHeight = (mDirectShareCurrHeight > 0)
                ? mDirectShareCurrHeight : mDirectShareMinHeight;
        mDirectShareMaxHeight = 2 * mDirectShareMinHeight;
    }

    public int getMeasuredRowHeight() {
        return mDirectShareCurrHeight;
    }

    public int getMinRowHeight() {
        return mDirectShareMinHeight;
    }

    public void setViewVisibility(int i, int visibility) {
        final View v = getView(i);
        if (visibility == View.VISIBLE) {
            mCellVisibility[i] = true;
            v.setVisibility(visibility);
            v.setAlpha(1.0f);
        } else if (visibility == View.INVISIBLE && mCellVisibility[i]) {
            mCellVisibility[i] = false;

            ValueAnimator fadeAnim = ObjectAnimator.ofFloat(v, "alpha", 1.0f, 0f);
            fadeAnim.setDuration(ChooserGridAdapter.NO_DIRECT_SHARE_ANIM_IN_MILLIS);
            fadeAnim.setInterpolator(new AccelerateInterpolator(1.0f));
            fadeAnim.addListener(new AnimatorListenerAdapter() {
                public void onAnimationEnd(Animator animation) {
                    v.setVisibility(View.INVISIBLE);
                }
            });
            fadeAnim.start();
        }
    }

    public void handleScroll(RecyclerView view, int y, int oldy, int maxTargetsPerRow) {
        // only exit early if fully collapsed, otherwise onListRebuilt() with shifting
        // targets can lock us into an expanded mode
        boolean notExpanded = mDirectShareCurrHeight == mDirectShareMinHeight;
        if (notExpanded) {
            if (mHideDirectShareExpansion) {
                return;
            }

            // only expand if we have more than maxTargetsPerRow, and delay that decision
            // until they start to scroll
            final int validTargets = this.mDeferredTargetCountSupplier.get();
            if (validTargets <= maxTargetsPerRow) {
                mHideDirectShareExpansion = true;
                return;
            }
        }

        int yDiff = (int) ((oldy - y) * ChooserActivity.DIRECT_SHARE_EXPANSION_RATE);

        int prevHeight = mDirectShareCurrHeight;
        int newHeight = Math.min(prevHeight + yDiff, mDirectShareMaxHeight);
        newHeight = Math.max(newHeight, mDirectShareMinHeight);
        yDiff = newHeight - prevHeight;

        updateDirectShareRowHeight(view, yDiff, newHeight);
    }

    public void expand(RecyclerView view) {
        updateDirectShareRowHeight(
                view, mDirectShareMaxHeight - mDirectShareCurrHeight, mDirectShareMaxHeight);
    }

    public void collapse(RecyclerView view) {
        updateDirectShareRowHeight(
                view, mDirectShareMinHeight - mDirectShareCurrHeight, mDirectShareMinHeight);
    }

    private void updateDirectShareRowHeight(RecyclerView view, int yDiff, int newHeight) {
        if (view == null || view.getChildCount() == 0 || yDiff == 0) {
            return;
        }

        // locate the item to expand, and offset the rows below that one
        boolean foundExpansion = false;
        for (int i = 0; i < view.getChildCount(); i++) {
            View child = view.getChildAt(i);

            if (foundExpansion) {
                child.offsetTopAndBottom(yDiff);
            } else {
                if (child.getTag() != null && child.getTag() instanceof DirectShareViewHolder) {
                    int widthSpec = MeasureSpec.makeMeasureSpec(child.getWidth(),
                            MeasureSpec.EXACTLY);
                    int heightSpec = MeasureSpec.makeMeasureSpec(newHeight,
                            MeasureSpec.EXACTLY);
                    child.measure(widthSpec, heightSpec);
                    child.getLayoutParams().height = child.getMeasuredHeight();
                    child.layout(child.getLeft(), child.getTop(), child.getRight(),
                            child.getTop() + child.getMeasuredHeight());

                    foundExpansion = true;
                }
            }
        }

        if (foundExpansion) {
            mDirectShareCurrHeight = newHeight;
        }
    }
}
