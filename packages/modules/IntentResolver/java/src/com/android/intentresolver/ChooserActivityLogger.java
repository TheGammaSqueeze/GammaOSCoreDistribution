/*
 * Copyright (C) 2020 The Android Open Source Project
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
import android.content.Intent;
import android.metrics.LogMaker;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.HashedStringCache;
import android.util.Log;

import com.android.intentresolver.contentpreview.ContentPreviewType;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.logging.InstanceId;
import com.android.internal.logging.InstanceIdSequence;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.UiEvent;
import com.android.internal.logging.UiEventLogger;
import com.android.internal.logging.UiEventLoggerImpl;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.util.FrameworkStatsLog;

/**
 * Helper for writing Sharesheet atoms to statsd log.
 * @hide
 */
public class ChooserActivityLogger {
    private static final String TAG = "ChooserActivity";
    private static final boolean DEBUG = true;

    public static final int SELECTION_TYPE_SERVICE = 1;
    public static final int SELECTION_TYPE_APP = 2;
    public static final int SELECTION_TYPE_STANDARD = 3;
    public static final int SELECTION_TYPE_COPY = 4;
    public static final int SELECTION_TYPE_NEARBY = 5;
    public static final int SELECTION_TYPE_EDIT = 6;
    public static final int SELECTION_TYPE_MODIFY_SHARE = 7;
    public static final int SELECTION_TYPE_CUSTOM_ACTION = 8;

    /**
     * This shim is provided only for testing. In production, clients will only ever use a
     * {@link DefaultFrameworkStatsLogger}.
     */
    @VisibleForTesting
    interface FrameworkStatsLogger {
        /** Overload to use for logging {@code FrameworkStatsLog.SHARESHEET_STARTED}. */
        void write(
                int frameworkEventId,
                int appEventId,
                String packageName,
                int instanceId,
                String mimeType,
                int numAppProvidedDirectTargets,
                int numAppProvidedAppTargets,
                boolean isWorkProfile,
                int previewType,
                int intentType,
                int numCustomActions,
                boolean modifyShareActionProvided);

        /** Overload to use for logging {@code FrameworkStatsLog.RANKING_SELECTED}. */
        void write(
                int frameworkEventId,
                int appEventId,
                String packageName,
                int instanceId,
                int positionPicked,
                boolean isPinned);
    }

    private static final int SHARESHEET_INSTANCE_ID_MAX = (1 << 13);

    // A small per-notification ID, used for statsd logging.
    // TODO: consider precomputing and storing as final.
    private static InstanceIdSequence sInstanceIdSequence;
    private InstanceId mInstanceId;

    private final UiEventLogger mUiEventLogger;
    private final FrameworkStatsLogger mFrameworkStatsLogger;
    private final MetricsLogger mMetricsLogger;

    public ChooserActivityLogger() {
        this(new UiEventLoggerImpl(), new DefaultFrameworkStatsLogger(), new MetricsLogger());
    }

    @VisibleForTesting
    ChooserActivityLogger(
            UiEventLogger uiEventLogger,
            FrameworkStatsLogger frameworkLogger,
            MetricsLogger metricsLogger) {
        mUiEventLogger = uiEventLogger;
        mFrameworkStatsLogger = frameworkLogger;
        mMetricsLogger = metricsLogger;
    }

    /** Records metrics for the start time of the {@link ChooserActivity}. */
    public void logChooserActivityShown(
            boolean isWorkProfile, String targetMimeType, long systemCost) {
        mMetricsLogger.write(new LogMaker(MetricsEvent.ACTION_ACTIVITY_CHOOSER_SHOWN)
                .setSubtype(
                        isWorkProfile ? MetricsEvent.MANAGED_PROFILE : MetricsEvent.PARENT_PROFILE)
                .addTaggedData(MetricsEvent.FIELD_SHARESHEET_MIMETYPE, targetMimeType)
                .addTaggedData(MetricsEvent.FIELD_TIME_TO_APP_TARGETS, systemCost));
    }

    /** Logs a UiEventReported event for the system sharesheet completing initial start-up. */
    public void logShareStarted(
            String packageName,
            String mimeType,
            int appProvidedDirect,
            int appProvidedApp,
            boolean isWorkprofile,
            int previewType,
            String intent,
            int customActionCount,
            boolean modifyShareActionProvided) {
        mFrameworkStatsLogger.write(FrameworkStatsLog.SHARESHEET_STARTED,
                /* event_id = 1 */ SharesheetStartedEvent.SHARE_STARTED.getId(),
                /* package_name = 2 */ packageName,
                /* instance_id = 3 */ getInstanceId().getId(),
                /* mime_type = 4 */ mimeType,
                /* num_app_provided_direct_targets = 5 */ appProvidedDirect,
                /* num_app_provided_app_targets = 6 */ appProvidedApp,
                /* is_workprofile = 7 */ isWorkprofile,
                /* previewType = 8 */ typeFromPreviewInt(previewType),
                /* intentType = 9 */ typeFromIntentString(intent),
                /* num_provided_custom_actions = 10 */ customActionCount,
                /* modify_share_action_provided = 11 */ modifyShareActionProvided);
    }

    /**
     * Log that a custom action has been tapped by the user.
     *
     * @param positionPicked index of the custom action within the list of custom actions.
     */
    public void logCustomActionSelected(int positionPicked) {
        mFrameworkStatsLogger.write(FrameworkStatsLog.RANKING_SELECTED,
                /* event_id = 1 */
                SharesheetTargetSelectedEvent.SHARESHEET_CUSTOM_ACTION_SELECTED.getId(),
                /* package_name = 2 */ null,
                /* instance_id = 3 */ getInstanceId().getId(),
                /* position_picked = 4 */ positionPicked,
                /* is_pinned = 5 */ false);
    }

    /**
     * Logs a UiEventReported event for the system sharesheet when the user selects a target.
     * TODO: document parameters and/or consider breaking up by targetType so we don't have to
     * support an overly-generic signature.
     */
    public void logShareTargetSelected(
            int targetType,
            String packageName,
            int positionPicked,
            int directTargetAlsoRanked,
            int numCallerProvided,
            @Nullable HashedStringCache.HashResult directTargetHashed,
            boolean isPinned,
            boolean successfullySelected,
            long selectionCost) {
        mFrameworkStatsLogger.write(FrameworkStatsLog.RANKING_SELECTED,
                /* event_id = 1 */ SharesheetTargetSelectedEvent.fromTargetType(targetType).getId(),
                /* package_name = 2 */ packageName,
                /* instance_id = 3 */ getInstanceId().getId(),
                /* position_picked = 4 */ positionPicked,
                /* is_pinned = 5 */ isPinned);

        int category = getTargetSelectionCategory(targetType);
        if (category != 0) {
            LogMaker targetLogMaker = new LogMaker(category).setSubtype(positionPicked);
            if (directTargetHashed != null) {
                targetLogMaker.addTaggedData(
                        MetricsEvent.FIELD_HASHED_TARGET_NAME, directTargetHashed.hashedString);
                targetLogMaker.addTaggedData(
                                MetricsEvent.FIELD_HASHED_TARGET_SALT_GEN,
                                directTargetHashed.saltGeneration);
                targetLogMaker.addTaggedData(MetricsEvent.FIELD_RANKED_POSITION,
                                directTargetAlsoRanked);
            }
            targetLogMaker.addTaggedData(MetricsEvent.FIELD_IS_CATEGORY_USED, numCallerProvided);
            mMetricsLogger.write(targetLogMaker);
        }

        if (successfullySelected) {
            if (DEBUG) {
                Log.d(TAG, "User Selection Time Cost is " + selectionCost);
                Log.d(TAG, "position of selected app/service/caller is " + positionPicked);
            }
            MetricsLogger.histogram(
                    null, "user_selection_cost_for_smart_sharing", (int) selectionCost);
            MetricsLogger.histogram(null, "app_position_for_smart_sharing", positionPicked);
        }
    }

    /** Log when direct share targets were received. */
    public void logDirectShareTargetReceived(int category, int latency) {
        mMetricsLogger.write(new LogMaker(category).setSubtype(latency));
    }

    /**
     * Log when we display a preview UI of the specified {@code previewType} as part of our
     * Sharesheet session.
     */
    public void logActionShareWithPreview(int previewType) {
        mMetricsLogger.write(
                new LogMaker(MetricsEvent.ACTION_SHARE_WITH_PREVIEW).setSubtype(previewType));
    }

    /** Log when the user selects an action button with the specified {@code targetType}. */
    public void logActionSelected(int targetType) {
        if (targetType == SELECTION_TYPE_COPY) {
            LogMaker targetLogMaker = new LogMaker(
                    MetricsEvent.ACTION_ACTIVITY_CHOOSER_PICKED_SYSTEM_TARGET).setSubtype(1);
            mMetricsLogger.write(targetLogMaker);
        }
        mFrameworkStatsLogger.write(FrameworkStatsLog.RANKING_SELECTED,
                /* event_id = 1 */ SharesheetTargetSelectedEvent.fromTargetType(targetType).getId(),
                /* package_name = 2 */ "",
                /* instance_id = 3 */ getInstanceId().getId(),
                /* position_picked = 4 */ -1,
                /* is_pinned = 5 */ false);
    }

    /** Log a warning that we couldn't display the content preview from the supplied {@code uri}. */
    public void logContentPreviewWarning(Uri uri) {
        // The ContentResolver already logs the exception. Log something more informative.
        Log.w(TAG, "Could not load (" + uri.toString() + ") thumbnail/name for preview. If "
                + "desired, consider using Intent#createChooser to launch the ChooserActivity, "
                + "and set your Intent's clipData and flags in accordance with that method's "
                + "documentation");

    }

    /** Logs a UiEventReported event for the system sharesheet being triggered by the user. */
    public void logSharesheetTriggered() {
        log(SharesheetStandardEvent.SHARESHEET_TRIGGERED, getInstanceId());
    }

    /** Logs a UiEventReported event for the system sharesheet completing loading app targets. */
    public void logSharesheetAppLoadComplete() {
        log(SharesheetStandardEvent.SHARESHEET_APP_LOAD_COMPLETE, getInstanceId());
    }

    /**
     * Logs a UiEventReported event for the system sharesheet completing loading service targets.
     */
    public void logSharesheetDirectLoadComplete() {
        log(SharesheetStandardEvent.SHARESHEET_DIRECT_LOAD_COMPLETE, getInstanceId());
    }

    /**
     * Logs a UiEventReported event for the system sharesheet timing out loading service targets.
     */
    public void logSharesheetDirectLoadTimeout() {
        log(SharesheetStandardEvent.SHARESHEET_DIRECT_LOAD_TIMEOUT, getInstanceId());
    }

    /**
     * Logs a UiEventReported event for the system sharesheet switching
     * between work and main profile.
     */
    public void logSharesheetProfileChanged() {
        log(SharesheetStandardEvent.SHARESHEET_PROFILE_CHANGED, getInstanceId());
    }

    /** Logs a UiEventReported event for the system sharesheet getting expanded or collapsed. */
    public void logSharesheetExpansionChanged(boolean isCollapsed) {
        log(isCollapsed ? SharesheetStandardEvent.SHARESHEET_COLLAPSED :
                SharesheetStandardEvent.SHARESHEET_EXPANDED, getInstanceId());
    }

    /**
     * Logs a UiEventReported event for the system sharesheet app share ranking timing out.
     */
    public void logSharesheetAppShareRankingTimeout() {
        log(SharesheetStandardEvent.SHARESHEET_APP_SHARE_RANKING_TIMEOUT, getInstanceId());
    }

    /**
     * Logs a UiEventReported event for the system sharesheet when direct share row is empty.
     */
    public void logSharesheetEmptyDirectShareRow() {
        log(SharesheetStandardEvent.SHARESHEET_EMPTY_DIRECT_SHARE_ROW, getInstanceId());
    }

    /**
     * Logs a UiEventReported event for a given share activity
     * @param event
     * @param instanceId
     */
    private void log(UiEventLogger.UiEventEnum event, InstanceId instanceId) {
        mUiEventLogger.logWithInstanceId(
                event,
                0,
                null,
                instanceId);
    }

    /**
     * @return A unique {@link InstanceId} to join across events recorded by this logger instance.
     */
    private InstanceId getInstanceId() {
        if (mInstanceId == null) {
            if (sInstanceIdSequence == null) {
                sInstanceIdSequence = new InstanceIdSequence(SHARESHEET_INSTANCE_ID_MAX);
            }
            mInstanceId = sInstanceIdSequence.newInstanceId();
        }
        return mInstanceId;
    }

    /**
     * The UiEvent enums that this class can log.
     */
    enum SharesheetStartedEvent implements UiEventLogger.UiEventEnum {
        @UiEvent(doc = "Basic system Sharesheet has started and is visible.")
        SHARE_STARTED(228);

        private final int mId;
        SharesheetStartedEvent(int id) {
            mId = id;
        }
        @Override
        public int getId() {
            return mId;
        }
    }

    /**
     * The UiEvent enums that this class can log.
     */
    enum SharesheetTargetSelectedEvent implements UiEventLogger.UiEventEnum {
        INVALID(0),
        @UiEvent(doc = "User selected a service target.")
        SHARESHEET_SERVICE_TARGET_SELECTED(232),
        @UiEvent(doc = "User selected an app target.")
        SHARESHEET_APP_TARGET_SELECTED(233),
        @UiEvent(doc = "User selected a standard target.")
        SHARESHEET_STANDARD_TARGET_SELECTED(234),
        @UiEvent(doc = "User selected the copy target.")
        SHARESHEET_COPY_TARGET_SELECTED(235),
        @UiEvent(doc = "User selected the nearby target.")
        SHARESHEET_NEARBY_TARGET_SELECTED(626),
        @UiEvent(doc = "User selected the edit target.")
        SHARESHEET_EDIT_TARGET_SELECTED(669),
        @UiEvent(doc = "User selected the modify share target.")
        SHARESHEET_MODIFY_SHARE_SELECTED(1316),
        @UiEvent(doc = "User selected a custom action.")
        SHARESHEET_CUSTOM_ACTION_SELECTED(1317);

        private final int mId;
        SharesheetTargetSelectedEvent(int id) {
            mId = id;
        }
        @Override public int getId() {
            return mId;
        }

        public static SharesheetTargetSelectedEvent fromTargetType(int targetType) {
            switch(targetType) {
                case SELECTION_TYPE_SERVICE:
                    return SHARESHEET_SERVICE_TARGET_SELECTED;
                case SELECTION_TYPE_APP:
                    return SHARESHEET_APP_TARGET_SELECTED;
                case SELECTION_TYPE_STANDARD:
                    return SHARESHEET_STANDARD_TARGET_SELECTED;
                case SELECTION_TYPE_COPY:
                    return SHARESHEET_COPY_TARGET_SELECTED;
                case SELECTION_TYPE_NEARBY:
                    return SHARESHEET_NEARBY_TARGET_SELECTED;
                case SELECTION_TYPE_EDIT:
                    return SHARESHEET_EDIT_TARGET_SELECTED;
                case SELECTION_TYPE_MODIFY_SHARE:
                    return SHARESHEET_MODIFY_SHARE_SELECTED;
                case SELECTION_TYPE_CUSTOM_ACTION:
                    return SHARESHEET_CUSTOM_ACTION_SELECTED;
                default:
                    return INVALID;
            }
        }
    }

    /**
     * The UiEvent enums that this class can log.
     */
    enum SharesheetStandardEvent implements UiEventLogger.UiEventEnum {
        INVALID(0),
        @UiEvent(doc = "User clicked share.")
        SHARESHEET_TRIGGERED(227),
        @UiEvent(doc = "User changed from work to personal profile or vice versa.")
        SHARESHEET_PROFILE_CHANGED(229),
        @UiEvent(doc = "User expanded target list.")
        SHARESHEET_EXPANDED(230),
        @UiEvent(doc = "User collapsed target list.")
        SHARESHEET_COLLAPSED(231),
        @UiEvent(doc = "Sharesheet app targets is fully populated.")
        SHARESHEET_APP_LOAD_COMPLETE(322),
        @UiEvent(doc = "Sharesheet direct targets is fully populated.")
        SHARESHEET_DIRECT_LOAD_COMPLETE(323),
        @UiEvent(doc = "Sharesheet direct targets timed out.")
        SHARESHEET_DIRECT_LOAD_TIMEOUT(324),
        @UiEvent(doc = "Sharesheet app share ranking timed out.")
        SHARESHEET_APP_SHARE_RANKING_TIMEOUT(831),
        @UiEvent(doc = "Sharesheet empty direct share row.")
        SHARESHEET_EMPTY_DIRECT_SHARE_ROW(828);

        private final int mId;
        SharesheetStandardEvent(int id) {
            mId = id;
        }
        @Override public int getId() {
            return mId;
        }
    }

    /**
     * Returns the enum used in sharesheet started atom to indicate what preview type was used.
     */
    private static int typeFromPreviewInt(int previewType) {
        switch(previewType) {
            case ContentPreviewType.CONTENT_PREVIEW_IMAGE:
                return FrameworkStatsLog.SHARESHEET_STARTED__PREVIEW_TYPE__CONTENT_PREVIEW_IMAGE;
            case ContentPreviewType.CONTENT_PREVIEW_FILE:
                return FrameworkStatsLog.SHARESHEET_STARTED__PREVIEW_TYPE__CONTENT_PREVIEW_FILE;
            case ContentPreviewType.CONTENT_PREVIEW_TEXT:
            default:
                return FrameworkStatsLog
                        .SHARESHEET_STARTED__PREVIEW_TYPE__CONTENT_PREVIEW_TYPE_UNKNOWN;
        }
    }

    /**
     * Returns the enum used in sharesheet started atom to indicate what intent triggers the
     * ChooserActivity.
     */
    private static int typeFromIntentString(String intent) {
        if (intent == null) {
            return FrameworkStatsLog.SHARESHEET_STARTED__INTENT_TYPE__INTENT_DEFAULT;
        }
        switch (intent) {
            case Intent.ACTION_VIEW:
                return FrameworkStatsLog.SHARESHEET_STARTED__INTENT_TYPE__INTENT_ACTION_VIEW;
            case Intent.ACTION_EDIT:
                return FrameworkStatsLog.SHARESHEET_STARTED__INTENT_TYPE__INTENT_ACTION_EDIT;
            case Intent.ACTION_SEND:
                return FrameworkStatsLog.SHARESHEET_STARTED__INTENT_TYPE__INTENT_ACTION_SEND;
            case Intent.ACTION_SENDTO:
                return FrameworkStatsLog.SHARESHEET_STARTED__INTENT_TYPE__INTENT_ACTION_SENDTO;
            case Intent.ACTION_SEND_MULTIPLE:
                return FrameworkStatsLog
                        .SHARESHEET_STARTED__INTENT_TYPE__INTENT_ACTION_SEND_MULTIPLE;
            case MediaStore.ACTION_IMAGE_CAPTURE:
                return FrameworkStatsLog
                        .SHARESHEET_STARTED__INTENT_TYPE__INTENT_ACTION_IMAGE_CAPTURE;
            case Intent.ACTION_MAIN:
                return FrameworkStatsLog.SHARESHEET_STARTED__INTENT_TYPE__INTENT_ACTION_MAIN;
            default:
                return FrameworkStatsLog.SHARESHEET_STARTED__INTENT_TYPE__INTENT_DEFAULT;
        }
    }

    @VisibleForTesting
    static int getTargetSelectionCategory(int targetType) {
        switch (targetType) {
            case SELECTION_TYPE_SERVICE:
                return MetricsEvent.ACTION_ACTIVITY_CHOOSER_PICKED_SERVICE_TARGET;
            case SELECTION_TYPE_APP:
                return MetricsEvent.ACTION_ACTIVITY_CHOOSER_PICKED_APP_TARGET;
            case SELECTION_TYPE_STANDARD:
                return MetricsEvent.ACTION_ACTIVITY_CHOOSER_PICKED_STANDARD_TARGET;
            default:
                return 0;
        }
    }

    private static class DefaultFrameworkStatsLogger implements FrameworkStatsLogger {
        @Override
        public void write(
                int frameworkEventId,
                int appEventId,
                String packageName,
                int instanceId,
                String mimeType,
                int numAppProvidedDirectTargets,
                int numAppProvidedAppTargets,
                boolean isWorkProfile,
                int previewType,
                int intentType,
                int numCustomActions,
                boolean modifyShareActionProvided) {
            FrameworkStatsLog.write(
                    frameworkEventId,
                    /* event_id = 1 */ appEventId,
                    /* package_name = 2 */ packageName,
                    /* instance_id = 3 */ instanceId,
                    /* mime_type = 4 */ mimeType,
                    /* num_app_provided_direct_targets */ numAppProvidedDirectTargets,
                    /* num_app_provided_app_targets */ numAppProvidedAppTargets,
                    /* is_workprofile */ isWorkProfile,
                    /* previewType = 8 */ previewType,
                    /* intentType = 9 */ intentType,
                    /* num_provided_custom_actions = 10 */ numCustomActions,
                    /* modify_share_action_provided = 11 */ modifyShareActionProvided);
        }

        @Override
        public void write(
                int frameworkEventId,
                int appEventId,
                String packageName,
                int instanceId,
                int positionPicked,
                boolean isPinned) {
            FrameworkStatsLog.write(
                    frameworkEventId,
                    /* event_id = 1 */ appEventId,
                    /* package_name = 2 */ packageName,
                    /* instance_id = 3 */ instanceId,
                    /* position_picked = 4 */ positionPicked,
                    /* is_pinned = 5 */ isPinned);
        }
    }
}
