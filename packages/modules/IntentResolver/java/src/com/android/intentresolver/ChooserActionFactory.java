/*
 * Copyright (C) 2023 The Android Open Source Project
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
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.service.chooser.ChooserAction;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.android.intentresolver.chooser.DisplayResolveInfo;
import com.android.intentresolver.chooser.TargetInfo;
import com.android.intentresolver.contentpreview.ChooserContentPreviewUi;
import com.android.intentresolver.flags.FeatureFlagRepository;
import com.android.intentresolver.flags.Flags;
import com.android.intentresolver.widget.ActionRow;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Implementation of {@link ChooserContentPreviewUi.ActionFactory} specialized to the application
 * requirements of Sharesheet / {@link ChooserActivity}.
 */
public final class ChooserActionFactory implements ChooserContentPreviewUi.ActionFactory {
    /** Delegate interface to launch activities when the actions are selected. */
    public interface ActionActivityStarter {
        /**
         * Request an activity launch for the provided target. Implementations may choose to exit
         * the current activity when the target is launched.
         */
        void safelyStartActivityAsPersonalProfileUser(TargetInfo info);

        /**
         * Request an activity launch for the provided target, optionally employing the specified
         * shared element transition. Implementations may choose to exit the current activity when
         * the target is launched.
         */
        default void safelyStartActivityAsPersonalProfileUserWithSharedElementTransition(
                TargetInfo info, View sharedElement, String sharedElementName) {
            safelyStartActivityAsPersonalProfileUser(info);
        }
    }

    private static final String TAG = "ChooserActions";

    private static final int URI_PERMISSION_INTENT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;

    private static final String CHIP_LABEL_METADATA_KEY = "android.service.chooser.chip_label";
    private static final String CHIP_ICON_METADATA_KEY = "android.service.chooser.chip_icon";

    private static final String IMAGE_EDITOR_SHARED_ELEMENT = "screenshot_preview_image";

    private final Context mContext;
    private final String mCopyButtonLabel;
    private final Drawable mCopyButtonDrawable;
    private final Runnable mOnCopyButtonClicked;
    private final TargetInfo mEditSharingTarget;
    private final Runnable mOnEditButtonClicked;
    private final TargetInfo mNearbySharingTarget;
    private final Runnable mOnNearbyButtonClicked;
    private final ImmutableList<ChooserAction> mCustomActions;
    private final Runnable mOnModifyShareClicked;
    private final Consumer<Boolean> mExcludeSharedTextAction;
    private final Consumer</* @Nullable */ Integer> mFinishCallback;
    private final ChooserActivityLogger mLogger;

    /**
     * @param context
     * @param chooserRequest data about the invocation of the current Sharesheet session.
     * @param featureFlagRepository feature flags that may control the eligibility of some actions.
     * @param integratedDeviceComponents info about other components that are available on this
     * device to implement the supported action types.
     * @param onUpdateSharedTextIsExcluded a delegate to be invoked when the "exclude shared text"
     * setting is updated. The argument is whether the shared text is to be excluded.
     * @param firstVisibleImageQuery a delegate that provides a reference to the first visible image
     * View in the Sharesheet UI, if any, or null.
     * @param activityStarter a delegate to launch activities when actions are selected.
     * @param finishCallback a delegate to close the Sharesheet UI (e.g. because some action was
     * completed).
     */
    public ChooserActionFactory(
            Context context,
            ChooserRequestParameters chooserRequest,
            FeatureFlagRepository featureFlagRepository,
            ChooserIntegratedDeviceComponents integratedDeviceComponents,
            ChooserActivityLogger logger,
            Consumer<Boolean> onUpdateSharedTextIsExcluded,
            Callable</* @Nullable */ View> firstVisibleImageQuery,
            ActionActivityStarter activityStarter,
            Consumer</* @Nullable */ Integer> finishCallback) {
        this(
                context,
                context.getString(com.android.internal.R.string.copy),
                context.getDrawable(com.android.internal.R.drawable.ic_menu_copy_material),
                makeOnCopyRunnable(
                        context,
                        chooserRequest.getTargetIntent(),
                        chooserRequest.getReferrerPackageName(),
                        finishCallback,
                        logger),
                getEditSharingTarget(
                        context,
                        chooserRequest.getTargetIntent(),
                        integratedDeviceComponents),
                makeOnEditRunnable(
                        getEditSharingTarget(
                                context,
                                chooserRequest.getTargetIntent(),
                                integratedDeviceComponents),
                        firstVisibleImageQuery,
                        activityStarter,
                        logger),
                getNearbySharingTarget(
                        context,
                        chooserRequest.getTargetIntent(),
                        integratedDeviceComponents),
                makeOnNearbyShareRunnable(
                        getNearbySharingTarget(
                                context,
                                chooserRequest.getTargetIntent(),
                                integratedDeviceComponents),
                        activityStarter,
                        finishCallback,
                        logger),
                chooserRequest.getChooserActions(),
                (featureFlagRepository.isEnabled(Flags.SHARESHEET_RESELECTION_ACTION)
                        ? createModifyShareRunnable(
                                chooserRequest.getModifyShareAction(),
                                finishCallback,
                                logger)
                        : null),
                onUpdateSharedTextIsExcluded,
                logger,
                finishCallback);
    }

    @VisibleForTesting
    ChooserActionFactory(
            Context context,
            String copyButtonLabel,
            Drawable copyButtonDrawable,
            Runnable onCopyButtonClicked,
            TargetInfo editSharingTarget,
            Runnable onEditButtonClicked,
            TargetInfo nearbySharingTarget,
            Runnable onNearbyButtonClicked,
            List<ChooserAction> customActions,
            @Nullable Runnable onModifyShareClicked,
            Consumer<Boolean> onUpdateSharedTextIsExcluded,
            ChooserActivityLogger logger,
            Consumer</* @Nullable */ Integer> finishCallback) {
        mContext = context;
        mCopyButtonLabel = copyButtonLabel;
        mCopyButtonDrawable = copyButtonDrawable;
        mOnCopyButtonClicked = onCopyButtonClicked;
        mEditSharingTarget = editSharingTarget;
        mOnEditButtonClicked = onEditButtonClicked;
        mNearbySharingTarget = nearbySharingTarget;
        mOnNearbyButtonClicked = onNearbyButtonClicked;
        mCustomActions = ImmutableList.copyOf(customActions);
        mOnModifyShareClicked = onModifyShareClicked;
        mExcludeSharedTextAction = onUpdateSharedTextIsExcluded;
        mLogger = logger;
        mFinishCallback = finishCallback;
    }

    /** Create an action that copies the share content to the clipboard. */
    @Override
    public ActionRow.Action createCopyButton() {
        return new ActionRow.Action(
                com.android.internal.R.id.chooser_copy_button,
                mCopyButtonLabel,
                mCopyButtonDrawable,
                mOnCopyButtonClicked);
    }

    /** Create an action that opens the share content in a system-default editor. */
    @Override
    @Nullable
    public ActionRow.Action createEditButton() {
        if (mEditSharingTarget == null) {
            return null;
        }

        return new ActionRow.Action(
                com.android.internal.R.id.chooser_edit_button,
                mEditSharingTarget.getDisplayLabel(),
                mEditSharingTarget.getDisplayIconHolder().getDisplayIcon(),
                mOnEditButtonClicked);
    }

    /** Create a "Share to Nearby" action. */
    @Override
    @Nullable
    public ActionRow.Action createNearbyButton() {
        if (mNearbySharingTarget == null) {
            return null;
        }

        return new ActionRow.Action(
                com.android.internal.R.id.chooser_nearby_button,
                mNearbySharingTarget.getDisplayLabel(),
                mNearbySharingTarget.getDisplayIconHolder().getDisplayIcon(),
                mOnNearbyButtonClicked);
    }

    /** Create custom actions */
    @Override
    public List<ActionRow.Action> createCustomActions() {
        List<ActionRow.Action> actions = new ArrayList<>();
        for (int i = 0; i < mCustomActions.size(); i++) {
            ActionRow.Action actionRow = createCustomAction(
                    mContext, mCustomActions.get(i), mFinishCallback, i, mLogger);
            if (actionRow != null) {
                actions.add(actionRow);
            }
        }
        return actions;
    }

    /**
     * Provides a share modification action, if any.
     */
    @Override
    @Nullable
    public Runnable getModifyShareAction() {
        return mOnModifyShareClicked;
    }

    private static Runnable createModifyShareRunnable(
            PendingIntent pendingIntent,
            Consumer<Integer> finishCallback,
            ChooserActivityLogger logger) {
        if (pendingIntent == null) {
            return null;
        }

        return () -> {
            try {
                pendingIntent.send();
            } catch (PendingIntent.CanceledException e) {
                Log.d(TAG, "Payload reselection action has been cancelled");
            }
            logger.logActionSelected(ChooserActivityLogger.SELECTION_TYPE_MODIFY_SHARE);
            finishCallback.accept(Activity.RESULT_OK);
        };
    }

    /**
     * <p>
     * Creates an exclude-text action that can be called when the user changes shared text
     * status in the Media + Text preview.
     * </p>
     * <p>
     * <code>true</code> argument value indicates that the text should be excluded.
     * </p>
     */
    @Override
    public Consumer<Boolean> getExcludeSharedTextAction() {
        return mExcludeSharedTextAction;
    }

    private static Runnable makeOnCopyRunnable(
            Context context,
            Intent targetIntent,
            String referrerPackageName,
            Consumer<Integer> finishCallback,
            ChooserActivityLogger logger) {
        return () -> {
            if (targetIntent == null) {
                finishCallback.accept(null);
                return;
            }

            final String action = targetIntent.getAction();

            ClipData clipData = null;
            if (Intent.ACTION_SEND.equals(action)) {
                String extraText = targetIntent.getStringExtra(Intent.EXTRA_TEXT);
                Uri extraStream = targetIntent.getParcelableExtra(Intent.EXTRA_STREAM);

                if (extraText != null) {
                    clipData = ClipData.newPlainText(null, extraText);
                } else if (extraStream != null) {
                    clipData = ClipData.newUri(context.getContentResolver(), null, extraStream);
                } else {
                    Log.w(TAG, "No data available to copy to clipboard");
                    return;
                }
            } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
                final ArrayList<Uri> streams = targetIntent.getParcelableArrayListExtra(
                        Intent.EXTRA_STREAM);
                clipData = ClipData.newUri(context.getContentResolver(), null, streams.get(0));
                for (int i = 1; i < streams.size(); i++) {
                    clipData.addItem(
                            context.getContentResolver(),
                            new ClipData.Item(streams.get(i)));
                }
            } else {
                // expected to only be visible with ACTION_SEND or ACTION_SEND_MULTIPLE
                // so warn about unexpected action
                Log.w(TAG, "Action (" + action + ") not supported for copying to clipboard");
                return;
            }

            ClipboardManager clipboardManager = (ClipboardManager) context.getSystemService(
                    Context.CLIPBOARD_SERVICE);
            clipboardManager.setPrimaryClipAsPackage(clipData, referrerPackageName);

            logger.logActionSelected(ChooserActivityLogger.SELECTION_TYPE_COPY);
            finishCallback.accept(Activity.RESULT_OK);
        };
    }

    private static TargetInfo getEditSharingTarget(
            Context context,
            Intent originalIntent,
            ChooserIntegratedDeviceComponents integratedComponents) {
        final ComponentName editorComponent = integratedComponents.getEditSharingComponent();

        final Intent resolveIntent = new Intent(originalIntent);
        // Retain only URI permission grant flags if present. Other flags may prevent the scene
        // transition animation from running (i.e FLAG_ACTIVITY_NO_ANIMATION,
        // FLAG_ACTIVITY_NEW_TASK, FLAG_ACTIVITY_NEW_DOCUMENT) but also not needed.
        resolveIntent.setFlags(originalIntent.getFlags() & URI_PERMISSION_INTENT_FLAGS);
        resolveIntent.setComponent(editorComponent);
        resolveIntent.setAction(Intent.ACTION_EDIT);
        String originalAction = originalIntent.getAction();
        if (Intent.ACTION_SEND.equals(originalAction)) {
            if (resolveIntent.getData() == null) {
                Uri uri = resolveIntent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (uri != null) {
                    String mimeType = context.getContentResolver().getType(uri);
                    resolveIntent.setDataAndType(uri, mimeType);
                }
            }
        } else {
            Log.e(TAG, originalAction + " is not supported.");
            return null;
        }
        final ResolveInfo ri = context.getPackageManager().resolveActivity(
                resolveIntent, PackageManager.GET_META_DATA);
        if (ri == null || ri.activityInfo == null) {
            Log.e(TAG, "Device-specified editor (" + editorComponent + ") not available");
            return null;
        }

        final DisplayResolveInfo dri = DisplayResolveInfo.newDisplayResolveInfo(
                originalIntent,
                ri,
                context.getString(com.android.internal.R.string.screenshot_edit),
                "",
                resolveIntent,
                null);
        dri.getDisplayIconHolder().setDisplayIcon(
                context.getDrawable(com.android.internal.R.drawable.ic_screenshot_edit));
        return dri;
    }

    private static Runnable makeOnEditRunnable(
            TargetInfo editSharingTarget,
            Callable</* @Nullable */ View> firstVisibleImageQuery,
            ActionActivityStarter activityStarter,
            ChooserActivityLogger logger) {
        return () -> {
            // Log share completion via edit.
            logger.logActionSelected(ChooserActivityLogger.SELECTION_TYPE_EDIT);

            View firstImageView = null;
            try {
                firstImageView = firstVisibleImageQuery.call();
            } catch (Exception e) { /* ignore */ }
            // Action bar is user-independent; always start as primary.
            if (firstImageView == null) {
                activityStarter.safelyStartActivityAsPersonalProfileUser(editSharingTarget);
            } else {
                activityStarter.safelyStartActivityAsPersonalProfileUserWithSharedElementTransition(
                        editSharingTarget, firstImageView, IMAGE_EDITOR_SHARED_ELEMENT);
            }
        };
    }

    private static TargetInfo getNearbySharingTarget(
            Context context,
            Intent originalIntent,
            ChooserIntegratedDeviceComponents integratedComponents) {
        final ComponentName cn = integratedComponents.getNearbySharingComponent();
        if (cn == null) {
            return null;
        }

        final Intent resolveIntent = new Intent(originalIntent);
        resolveIntent.setComponent(cn);
        final ResolveInfo ri = context.getPackageManager().resolveActivity(
                resolveIntent, PackageManager.GET_META_DATA);
        if (ri == null || ri.activityInfo == null) {
            Log.e(TAG, "Device-specified nearby sharing component (" + cn
                    + ") not available");
            return null;
        }

        // Allow the nearby sharing component to provide a more appropriate icon and label
        // for the chip.
        CharSequence name = null;
        Drawable icon = null;
        final Bundle metaData = ri.activityInfo.metaData;
        if (metaData != null) {
            try {
                final Resources pkgRes = context.getPackageManager().getResourcesForActivity(cn);
                final int nameResId = metaData.getInt(CHIP_LABEL_METADATA_KEY);
                name = pkgRes.getString(nameResId);
                final int resId = metaData.getInt(CHIP_ICON_METADATA_KEY);
                icon = pkgRes.getDrawable(resId);
            } catch (NameNotFoundException | Resources.NotFoundException ex) { /* ignore */ }
        }
        if (TextUtils.isEmpty(name)) {
            name = ri.loadLabel(context.getPackageManager());
        }
        if (icon == null) {
            icon = ri.loadIcon(context.getPackageManager());
        }

        final DisplayResolveInfo dri = DisplayResolveInfo.newDisplayResolveInfo(
                originalIntent, ri, name, "", resolveIntent, null);
        dri.getDisplayIconHolder().setDisplayIcon(icon);
        return dri;
    }

    private static Runnable makeOnNearbyShareRunnable(
            TargetInfo nearbyShareTarget,
            ActionActivityStarter activityStarter,
            Consumer<Integer> finishCallback,
            ChooserActivityLogger logger) {
        return () -> {
            logger.logActionSelected(ChooserActivityLogger.SELECTION_TYPE_NEARBY);
            // Action bar is user-independent; always start as primary.
            activityStarter.safelyStartActivityAsPersonalProfileUser(nearbyShareTarget);
        };
    }

    @Nullable
    private static ActionRow.Action createCustomAction(
            Context context,
            ChooserAction action,
            Consumer<Integer> finishCallback,
            int position,
            ChooserActivityLogger logger) {
        Drawable icon = action.getIcon().loadDrawable(context);
        if (icon == null && TextUtils.isEmpty(action.getLabel())) {
            return null;
        }
        return new ActionRow.Action(
                action.getLabel(),
                icon,
                () -> {
                    try {
                        action.getAction().send(
                                null,
                                0,
                                null,
                                null,
                                null,
                                null,
                                ActivityOptions.makeCustomAnimation(
                                        context,
                                        R.anim.slide_in_right,
                                        R.anim.slide_out_left)
                                                .toBundle());
                    } catch (PendingIntent.CanceledException e) {
                        Log.d(TAG, "Custom action, " + action.getLabel() + ", has been cancelled");
                    }
                    logger.logCustomActionSelected(position);
                    finishCallback.accept(Activity.RESULT_OK);
                }
        );
    }
}
