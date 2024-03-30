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

import static com.android.intentresolver.ChooserActivity.TARGET_TYPE_SHORTCUTS_FROM_PREDICTION_SERVICE;
import static com.android.intentresolver.ChooserActivity.TARGET_TYPE_SHORTCUTS_FROM_SHORTCUT_MANAGER;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.prediction.AppTarget;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.LabeledIntent;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.AsyncTask;
import android.os.Trace;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.DeviceConfig;
import android.service.chooser.ChooserTarget;
import android.text.Layout;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.WorkerThread;

import com.android.intentresolver.chooser.DisplayResolveInfo;
import com.android.intentresolver.chooser.MultiDisplayResolveInfo;
import com.android.intentresolver.chooser.NotSelectableTargetInfo;
import com.android.intentresolver.chooser.SelectableTargetInfo;
import com.android.intentresolver.chooser.TargetInfo;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.config.sysui.SystemUiDeviceConfigFlags;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ChooserListAdapter extends ResolverListAdapter {
    private static final String TAG = "ChooserListAdapter";
    private static final boolean DEBUG = false;

    public static final int NO_POSITION = -1;
    public static final int TARGET_BAD = -1;
    public static final int TARGET_CALLER = 0;
    public static final int TARGET_SERVICE = 1;
    public static final int TARGET_STANDARD = 2;
    public static final int TARGET_STANDARD_AZ = 3;

    private static final int MAX_SUGGESTED_APP_TARGETS = 4;

    /** {@link #getBaseScore} */
    public static final float CALLER_TARGET_SCORE_BOOST = 900.f;
    /** {@link #getBaseScore} */
    public static final float SHORTCUT_TARGET_SCORE_BOOST = 90.f;

    private final ChooserRequestParameters mChooserRequest;
    private final int mMaxRankedTargets;

    private final ChooserActivityLogger mChooserActivityLogger;

    private final Map<TargetInfo, AsyncTask> mIconLoaders = new HashMap<>();

    // Reserve spots for incoming direct share targets by adding placeholders
    private final TargetInfo mPlaceHolderTargetInfo;
    private final List<TargetInfo> mServiceTargets = new ArrayList<>();
    private final List<DisplayResolveInfo> mCallerTargets = new ArrayList<>();

    private final ShortcutSelectionLogic mShortcutSelectionLogic;

    // Sorted list of DisplayResolveInfos for the alphabetical app section.
    private List<DisplayResolveInfo> mSortedList = new ArrayList<>();

    // For pinned direct share labels, if the text spans multiple lines, the TextView will consume
    // the full width, even if the characters actually take up less than that. Measure the actual
    // line widths and constrain the View's width based upon that so that the pin doesn't end up
    // very far from the text.
    private final View.OnLayoutChangeListener mPinTextSpacingListener =
            new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                        int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    TextView textView = (TextView) v;
                    Layout layout = textView.getLayout();
                    if (layout != null) {
                        int textWidth = 0;
                        for (int line = 0; line < layout.getLineCount(); line++) {
                            textWidth = Math.max((int) Math.ceil(layout.getLineMax(line)),
                                    textWidth);
                        }
                        int desiredWidth = textWidth + textView.getPaddingLeft()
                                + textView.getPaddingRight();
                        if (textView.getWidth() > desiredWidth) {
                            ViewGroup.LayoutParams params = textView.getLayoutParams();
                            params.width = desiredWidth;
                            textView.setLayoutParams(params);
                            // Need to wait until layout pass is over before requesting layout.
                            textView.post(() -> textView.requestLayout());
                        }
                        textView.removeOnLayoutChangeListener(this);
                    }
                }
            };

    public ChooserListAdapter(
            Context context,
            List<Intent> payloadIntents,
            Intent[] initialIntents,
            List<ResolveInfo> rList,
            boolean filterLastUsed,
            ResolverListController resolverListController,
            UserHandle userHandle,
            Intent targetIntent,
            ResolverListCommunicator resolverListCommunicator,
            PackageManager packageManager,
            ChooserActivityLogger chooserActivityLogger,
            ChooserRequestParameters chooserRequest,
            int maxRankedTargets) {
        // Don't send the initial intents through the shared ResolverActivity path,
        // we want to separate them into a different section.
        super(
                context,
                payloadIntents,
                null,
                rList,
                filterLastUsed,
                resolverListController,
                userHandle,
                targetIntent,
                resolverListCommunicator,
                false);

        mChooserRequest = chooserRequest;
        mMaxRankedTargets = maxRankedTargets;

        mPlaceHolderTargetInfo = NotSelectableTargetInfo.newPlaceHolderTargetInfo(context);
        createPlaceHolders();
        mChooserActivityLogger = chooserActivityLogger;
        mShortcutSelectionLogic = new ShortcutSelectionLogic(
                context.getResources().getInteger(R.integer.config_maxShortcutTargetsPerApp),
                DeviceConfig.getBoolean(
                        DeviceConfig.NAMESPACE_SYSTEMUI,
                        SystemUiDeviceConfigFlags.APPLY_SHARING_APP_LIMITS_IN_SYSUI,
                        true)
        );

        if (initialIntents != null) {
            for (int i = 0; i < initialIntents.length; i++) {
                final Intent ii = initialIntents[i];
                if (ii == null) {
                    continue;
                }

                // We reimplement Intent#resolveActivityInfo here because if we have an
                // implicit intent, we want the ResolveInfo returned by PackageManager
                // instead of one we reconstruct ourselves. The ResolveInfo returned might
                // have extra metadata and resolvePackageName set and we want to respect that.
                ResolveInfo ri = null;
                ActivityInfo ai = null;
                final ComponentName cn = ii.getComponent();
                if (cn != null) {
                    try {
                        ai = packageManager.getActivityInfo(
                                ii.getComponent(),
                                PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA));
                        ri = new ResolveInfo();
                        ri.activityInfo = ai;
                    } catch (PackageManager.NameNotFoundException ignored) {
                        // ai will == null below
                    }
                }
                if (ai == null) {
                    // Because of AIDL bug, resolveActivity can't accept subclasses of Intent.
                    final Intent rii = (ii.getClass() == Intent.class) ? ii : new Intent(ii);
                    ri = packageManager.resolveActivity(
                            rii,
                            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY));
                    ai = ri != null ? ri.activityInfo : null;
                }
                if (ai == null) {
                    Log.w(TAG, "No activity found for " + ii);
                    continue;
                }
                UserManager userManager =
                        (UserManager) context.getSystemService(Context.USER_SERVICE);
                if (ii instanceof LabeledIntent) {
                    LabeledIntent li = (LabeledIntent) ii;
                    ri.resolvePackageName = li.getSourcePackage();
                    ri.labelRes = li.getLabelResource();
                    ri.nonLocalizedLabel = li.getNonLocalizedLabel();
                    ri.icon = li.getIconResource();
                    ri.iconResourceId = ri.icon;
                }
                if (userManager.isManagedProfile()) {
                    ri.noResourceId = true;
                    ri.icon = 0;
                }
                DisplayResolveInfo displayResolveInfo = DisplayResolveInfo.newDisplayResolveInfo(
                        ii, ri, ii, mPresentationFactory.makePresentationGetter(ri));
                mCallerTargets.add(displayResolveInfo);
                if (mCallerTargets.size() == MAX_SUGGESTED_APP_TARGETS) break;
            }
        }
    }

    @Override
    public void handlePackagesChanged() {
        if (DEBUG) {
            Log.d(TAG, "clearing queryTargets on package change");
        }
        createPlaceHolders();
        mResolverListCommunicator.onHandlePackagesChanged(this);

    }

    private void createPlaceHolders() {
        mServiceTargets.clear();
        for (int i = 0; i < mMaxRankedTargets; ++i) {
            mServiceTargets.add(mPlaceHolderTargetInfo);
        }
    }

    @Override
    View onCreateView(ViewGroup parent) {
        return mInflater.inflate(R.layout.resolve_grid_item, parent, false);
    }

    @VisibleForTesting
    @Override
    public void onBindView(View view, TargetInfo info, int position) {
        final ViewHolder holder = (ViewHolder) view.getTag();

        if (info == null) {
            holder.icon.setImageDrawable(loadIconPlaceholder());
            return;
        }

        holder.bindLabel(info.getDisplayLabel(), info.getExtendedInfo(), alwaysShowSubLabel());
        holder.bindIcon(info, /*animate =*/ true);
        if (info.isSelectableTargetInfo()) {
            // direct share targets should append the application name for a better readout
            DisplayResolveInfo rInfo = info.getDisplayResolveInfo();
            CharSequence appName = rInfo != null ? rInfo.getDisplayLabel() : "";
            CharSequence extendedInfo = info.getExtendedInfo();
            String contentDescription = String.join(" ", info.getDisplayLabel(),
                    extendedInfo != null ? extendedInfo : "", appName);
            holder.updateContentDescription(contentDescription);
            if (!info.hasDisplayIcon()) {
                loadDirectShareIcon((SelectableTargetInfo) info);
            }
        } else if (info.isDisplayResolveInfo()) {
            DisplayResolveInfo dri = (DisplayResolveInfo) info;
            if (!dri.hasDisplayIcon()) {
                loadIcon(dri);
            }
        }

        // If target is loading, show a special placeholder shape in the label, make unclickable
        if (info.isPlaceHolderTargetInfo()) {
            final int maxWidth = mContext.getResources().getDimensionPixelSize(
                    R.dimen.chooser_direct_share_label_placeholder_max_width);
            holder.text.setMaxWidth(maxWidth);
            holder.text.setBackground(mContext.getResources().getDrawable(
                    R.drawable.chooser_direct_share_label_placeholder, mContext.getTheme()));
            // Prevent rippling by removing background containing ripple
            holder.itemView.setBackground(null);
        } else {
            holder.text.setMaxWidth(Integer.MAX_VALUE);
            holder.text.setBackground(null);
            holder.itemView.setBackground(holder.defaultItemViewBackground);
        }

        // Always remove the spacing listener, attach as needed to direct share targets below.
        holder.text.removeOnLayoutChangeListener(mPinTextSpacingListener);

        if (info.isMultiDisplayResolveInfo()) {
            // If the target is grouped show an indicator
            Drawable bkg = mContext.getDrawable(R.drawable.chooser_group_background);
            holder.text.setPaddingRelative(0, 0, bkg.getIntrinsicWidth() /* end */, 0);
            holder.text.setBackground(bkg);
        } else if (info.isPinned() && (getPositionTargetType(position) == TARGET_STANDARD
                || getPositionTargetType(position) == TARGET_SERVICE)) {
            // If the appShare or directShare target is pinned and in the suggested row show a
            // pinned indicator
            Drawable bkg = mContext.getDrawable(R.drawable.chooser_pinned_background);
            holder.text.setPaddingRelative(bkg.getIntrinsicWidth() /* start */, 0, 0, 0);
            holder.text.setBackground(bkg);
            holder.text.addOnLayoutChangeListener(mPinTextSpacingListener);
        } else {
            holder.text.setBackground(null);
            holder.text.setPaddingRelative(0, 0, 0, 0);
        }
    }

    private void loadDirectShareIcon(SelectableTargetInfo info) {
        LoadDirectShareIconTask task = (LoadDirectShareIconTask) mIconLoaders.get(info);
        if (task == null) {
            task = createLoadDirectShareIconTask(info);
            mIconLoaders.put(info, task);
            task.loadIcon();
        }
    }

    @VisibleForTesting
    protected LoadDirectShareIconTask createLoadDirectShareIconTask(SelectableTargetInfo info) {
        return new LoadDirectShareIconTask(
                mContext.createContextAsUser(getUserHandle(), 0),
                info);
    }

    void updateAlphabeticalList() {
        // TODO: this procedure seems like it should be relatively lightweight. Why does it need to
        // run in an `AsyncTask`?
        new AsyncTask<Void, Void, List<DisplayResolveInfo>>() {
            @Override
            protected List<DisplayResolveInfo> doInBackground(Void... voids) {
                List<DisplayResolveInfo> allTargets = new ArrayList<>();
                allTargets.addAll(getTargetsInCurrentDisplayList());
                allTargets.addAll(mCallerTargets);

                // Consolidate multiple targets from same app.
                return allTargets
                        .stream()
                        .collect(Collectors.groupingBy(target ->
                                target.getResolvedComponentName().getPackageName()
                                + "#" + target.getDisplayLabel()
                        ))
                        .values()
                        .stream()
                        .map(appTargets ->
                                (appTargets.size() == 1)
                                ? appTargets.get(0)
                                : MultiDisplayResolveInfo.newMultiDisplayResolveInfo(appTargets))
                        .sorted(new ChooserActivity.AzInfoComparator(mContext))
                        .collect(Collectors.toList());
            }
            @Override
            protected void onPostExecute(List<DisplayResolveInfo> newList) {
                mSortedList = newList;
                notifyDataSetChanged();
            }
        }.execute();
    }

    @Override
    public int getCount() {
        return getRankedTargetCount() + getAlphaTargetCount()
                + getSelectableServiceTargetCount() + getCallerTargetCount();
    }

    @Override
    public int getUnfilteredCount() {
        int appTargets = super.getUnfilteredCount();
        if (appTargets > mMaxRankedTargets) {
            // TODO: what does this condition mean?
            appTargets = appTargets + mMaxRankedTargets;
        }
        return appTargets + getSelectableServiceTargetCount() + getCallerTargetCount();
    }


    public int getCallerTargetCount() {
        return mCallerTargets.size();
    }

    /**
     * Filter out placeholders and non-selectable service targets
     */
    public int getSelectableServiceTargetCount() {
        int count = 0;
        for (TargetInfo info : mServiceTargets) {
            if (info.isSelectableTargetInfo()) {
                count++;
            }
        }
        return count;
    }

    public int getServiceTargetCount() {
        if (mChooserRequest.isSendActionTarget() && !ActivityManager.isLowRamDeviceStatic()) {
            return Math.min(mServiceTargets.size(), mMaxRankedTargets);
        }

        return 0;
    }

    public int getAlphaTargetCount() {
        int groupedCount = mSortedList.size();
        int ungroupedCount = mCallerTargets.size() + getDisplayResolveInfoCount();
        return (ungroupedCount > mMaxRankedTargets) ? groupedCount : 0;
    }

    /**
     * Fetch ranked app target count
     */
    public int getRankedTargetCount() {
        int spacesAvailable = mMaxRankedTargets - getCallerTargetCount();
        return Math.min(spacesAvailable, super.getCount());
    }

    /** Get all the {@link DisplayResolveInfo} data for our targets. */
    public DisplayResolveInfo[] getDisplayResolveInfos() {
        int size = getDisplayResolveInfoCount();
        DisplayResolveInfo[] resolvedTargets = new DisplayResolveInfo[size];
        for (int i = 0; i < size; i++) {
            resolvedTargets[i] = getDisplayResolveInfo(i);
        }
        return resolvedTargets;
    }

    public int getPositionTargetType(int position) {
        int offset = 0;

        final int serviceTargetCount = getServiceTargetCount();
        if (position < serviceTargetCount) {
            return TARGET_SERVICE;
        }
        offset += serviceTargetCount;

        final int callerTargetCount = getCallerTargetCount();
        if (position - offset < callerTargetCount) {
            return TARGET_CALLER;
        }
        offset += callerTargetCount;

        final int rankedTargetCount = getRankedTargetCount();
        if (position - offset < rankedTargetCount) {
            return TARGET_STANDARD;
        }
        offset += rankedTargetCount;

        final int standardTargetCount = getAlphaTargetCount();
        if (position - offset < standardTargetCount) {
            return TARGET_STANDARD_AZ;
        }

        return TARGET_BAD;
    }

    @Override
    public TargetInfo getItem(int position) {
        return targetInfoForPosition(position, true);
    }

    /**
     * Find target info for a given position.
     * Since ChooserActivity displays several sections of content, determine which
     * section provides this item.
     */
    @Override
    public TargetInfo targetInfoForPosition(int position, boolean filtered) {
        if (position == NO_POSITION) {
            return null;
        }

        int offset = 0;

        // Direct share targets
        final int serviceTargetCount = filtered ? getServiceTargetCount() :
                getSelectableServiceTargetCount();
        if (position < serviceTargetCount) {
            return mServiceTargets.get(position);
        }
        offset += serviceTargetCount;

        // Targets provided by calling app
        final int callerTargetCount = getCallerTargetCount();
        if (position - offset < callerTargetCount) {
            return mCallerTargets.get(position - offset);
        }
        offset += callerTargetCount;

        // Ranked standard app targets
        final int rankedTargetCount = getRankedTargetCount();
        if (position - offset < rankedTargetCount) {
            return filtered ? super.getItem(position - offset)
                    : getDisplayResolveInfo(position - offset);
        }
        offset += rankedTargetCount;

        // Alphabetical complete app target list.
        if (position - offset < getAlphaTargetCount() && !mSortedList.isEmpty()) {
            return mSortedList.get(position - offset);
        }

        return null;
    }

    // Check whether {@code dri} should be added into mDisplayList.
    @Override
    protected boolean shouldAddResolveInfo(DisplayResolveInfo dri) {
        // Checks if this info is already listed in callerTargets.
        for (TargetInfo existingInfo : mCallerTargets) {
            if (mResolverListCommunicator.resolveInfoMatch(
                    dri.getResolveInfo(), existingInfo.getResolveInfo())) {
                return false;
            }
        }
        return super.shouldAddResolveInfo(dri);
    }

    /**
     * Fetch surfaced direct share target info
     */
    public List<TargetInfo> getSurfacedTargetInfo() {
        return mServiceTargets.subList(0,
                Math.min(mMaxRankedTargets, getSelectableServiceTargetCount()));
    }


    /**
     * Evaluate targets for inclusion in the direct share area. May not be included
     * if score is too low.
     */
    public void addServiceResults(
            @Nullable DisplayResolveInfo origTarget,
            List<ChooserTarget> targets,
            @ChooserActivity.ShareTargetType int targetType,
            Map<ChooserTarget, ShortcutInfo> directShareToShortcutInfos,
            Map<ChooserTarget, AppTarget> directShareToAppTargets) {
        // Avoid inserting any potentially late results.
        if ((mServiceTargets.size() == 1) && mServiceTargets.get(0).isEmptyTargetInfo()) {
            return;
        }
        boolean isShortcutResult = targetType == TARGET_TYPE_SHORTCUTS_FROM_SHORTCUT_MANAGER
                || targetType == TARGET_TYPE_SHORTCUTS_FROM_PREDICTION_SERVICE;
        boolean isUpdated = mShortcutSelectionLogic.addServiceResults(
                origTarget,
                getBaseScore(origTarget, targetType),
                targets,
                isShortcutResult,
                directShareToShortcutInfos,
                directShareToAppTargets,
                mContext.createContextAsUser(getUserHandle(), 0),
                mChooserRequest.getTargetIntent(),
                mChooserRequest.getReferrerFillInIntent(),
                mMaxRankedTargets,
                mServiceTargets);
        if (isUpdated) {
            notifyDataSetChanged();
        }
    }

    /**
     * Use the scoring system along with artificial boosts to create up to 4 distinct buckets:
     * <ol>
     *   <li>App-supplied targets
     *   <li>Shortcuts ranked via App Prediction Manager
     *   <li>Shortcuts ranked via legacy heuristics
     *   <li>Legacy direct share targets
     * </ol>
     */
    public float getBaseScore(
            DisplayResolveInfo target,
            @ChooserActivity.ShareTargetType int targetType) {
        if (target == null) {
            return CALLER_TARGET_SCORE_BOOST;
        }
        float score = super.getScore(target);
        if (targetType == TARGET_TYPE_SHORTCUTS_FROM_SHORTCUT_MANAGER
                || targetType == TARGET_TYPE_SHORTCUTS_FROM_PREDICTION_SERVICE) {
            return score * SHORTCUT_TARGET_SCORE_BOOST;
        }
        return score;
    }

    /**
     * Calling this marks service target loading complete, and will attempt to no longer
     * update the direct share area.
     */
    public void completeServiceTargetLoading() {
        mServiceTargets.removeIf(o -> o.isPlaceHolderTargetInfo());
        if (mServiceTargets.isEmpty()) {
            mServiceTargets.add(NotSelectableTargetInfo.newEmptyTargetInfo());
            mChooserActivityLogger.logSharesheetEmptyDirectShareRow();
        }
        notifyDataSetChanged();
    }

    protected boolean alwaysShowSubLabel() {
        // Always show a subLabel for visual consistency across list items. Show an empty
        // subLabel if the subLabel is the same as the label
        return true;
    }

    /**
     * Rather than fully sorting the input list, this sorting task will put the top k elements
     * in the head of input list and fill the tail with other elements in undetermined order.
     */
    @Override
    AsyncTask<List<ResolvedComponentInfo>,
                Void,
                List<ResolvedComponentInfo>> createSortingTask(boolean doPostProcessing) {
        return new AsyncTask<List<ResolvedComponentInfo>,
                Void,
                List<ResolvedComponentInfo>>() {
            @Override
            protected List<ResolvedComponentInfo> doInBackground(
                    List<ResolvedComponentInfo>... params) {
                Trace.beginSection("ChooserListAdapter#SortingTask");
                mResolverListController.topK(params[0], mMaxRankedTargets);
                Trace.endSection();
                return params[0];
            }
            @Override
            protected void onPostExecute(List<ResolvedComponentInfo> sortedComponents) {
                processSortedList(sortedComponents, doPostProcessing);
                if (doPostProcessing) {
                    mResolverListCommunicator.updateProfileViewButton();
                    notifyDataSetChanged();
                }
            }
        };
    }

    /**
     * Loads direct share targets icons.
     */
    @VisibleForTesting
    public class LoadDirectShareIconTask extends AsyncTask<Void, Void, Drawable> {
        private final Context mContext;
        private final SelectableTargetInfo mTargetInfo;

        private LoadDirectShareIconTask(Context context, SelectableTargetInfo targetInfo) {
            mContext = context;
            mTargetInfo = targetInfo;
        }

        @Override
        protected Drawable doInBackground(Void... voids) {
            Drawable drawable;
            try {
                drawable = getChooserTargetIconDrawable(
                        mContext,
                        mTargetInfo.getChooserTargetIcon(),
                        mTargetInfo.getChooserTargetComponentName(),
                        mTargetInfo.getDirectShareShortcutInfo());
            } catch (Exception e) {
                Log.e(TAG,
                        "Failed to load shortcut icon for "
                                + mTargetInfo.getChooserTargetComponentName(),
                        e);
                drawable = loadIconPlaceholder();
            }
            return drawable;
        }

        @Override
        protected void onPostExecute(@Nullable Drawable icon) {
            if (icon != null && !mTargetInfo.hasDisplayIcon()) {
                mTargetInfo.getDisplayIconHolder().setDisplayIcon(icon);
                notifyDataSetChanged();
            }
        }

        @WorkerThread
        private Drawable getChooserTargetIconDrawable(
                Context context,
                @Nullable Icon icon,
                ComponentName targetComponentName,
                @Nullable ShortcutInfo shortcutInfo) {
            Drawable directShareIcon = null;

            // First get the target drawable and associated activity info
            if (icon != null) {
                directShareIcon = icon.loadDrawable(context);
            } else if (shortcutInfo != null) {
                LauncherApps launcherApps = context.getSystemService(LauncherApps.class);
                if (launcherApps != null) {
                    directShareIcon = launcherApps.getShortcutIconDrawable(shortcutInfo, 0);
                }
            }

            if (directShareIcon == null) {
                return null;
            }

            ActivityInfo info = null;
            try {
                info = context.getPackageManager().getActivityInfo(targetComponentName, 0);
            } catch (PackageManager.NameNotFoundException error) {
                Log.e(TAG, "Could not find activity associated with ChooserTarget");
            }

            if (info == null) {
                return null;
            }

            // Now fetch app icon and raster with no badging even in work profile
            Bitmap appIcon = mPresentationFactory.makePresentationGetter(info).getIconBitmap(null);

            // Raster target drawable with appIcon as a badge
            SimpleIconFactory sif = SimpleIconFactory.obtain(context);
            Bitmap directShareBadgedIcon = sif.createAppBadgedIconBitmap(directShareIcon, appIcon);
            sif.recycle();

            return new BitmapDrawable(context.getResources(), directShareBadgedIcon);
        }

        /**
         * An alias for execute to use with unit tests.
         */
        public void loadIcon() {
            execute();
        }
    }
}
