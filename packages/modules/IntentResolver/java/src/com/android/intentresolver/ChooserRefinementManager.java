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
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.ResultReceiver;
import android.util.Log;

import com.android.intentresolver.chooser.TargetInfo;

import java.util.List;
import java.util.function.Consumer;

/**
 * Helper class to manage Sharesheet's "refinement" flow, where callers supply a "refinement
 * activity" that will be invoked when a target is selected, allowing the calling app to add
 * additional extras and other refinements (subject to {@link Intent#filterEquals()}), e.g., to
 * convert the format of the payload, or lazy-download some data that was deferred in the original
 * call).
 */
public final class ChooserRefinementManager {
    private static final String TAG = "ChooserRefinement";

    @Nullable
    private final IntentSender mRefinementIntentSender;

    private final Context mContext;
    private final Consumer<TargetInfo> mOnSelectionRefined;
    private final Runnable mOnRefinementCancelled;

    @Nullable
    private RefinementResultReceiver mRefinementResultReceiver;

    public ChooserRefinementManager(
            Context context,
            @Nullable IntentSender refinementIntentSender,
            Consumer<TargetInfo> onSelectionRefined,
            Runnable onRefinementCancelled) {
        mContext = context;
        mRefinementIntentSender = refinementIntentSender;
        mOnSelectionRefined = onSelectionRefined;
        mOnRefinementCancelled = onRefinementCancelled;
    }

    /**
     * Delegate the user's {@code selectedTarget} to the refinement flow, if possible.
     * @return true if the selection should wait for a now-started refinement flow, or false if it
     * can proceed by the default (non-refinement) logic.
     */
    public boolean maybeHandleSelection(TargetInfo selectedTarget) {
        if (mRefinementIntentSender == null) {
            return false;
        }
        if (selectedTarget.getAllSourceIntents().isEmpty()) {
            return false;
        }

        destroy();  // Terminate any prior sessions.
        mRefinementResultReceiver = new RefinementResultReceiver(
                refinedIntent -> {
                    destroy();
                    TargetInfo refinedTarget =
                            selectedTarget.tryToCloneWithAppliedRefinement(refinedIntent);
                    if (refinedTarget != null) {
                        mOnSelectionRefined.accept(refinedTarget);
                    } else {
                        Log.e(TAG, "Failed to apply refinement to any matching source intent");
                        mOnRefinementCancelled.run();
                    }
                },
                mOnRefinementCancelled,
                mContext.getMainThreadHandler());

        Intent refinementRequest = makeRefinementRequest(mRefinementResultReceiver, selectedTarget);
        try {
            mRefinementIntentSender.sendIntent(mContext, 0, refinementRequest, null, null);
            return true;
        } catch (SendIntentException e) {
            Log.e(TAG, "Refinement IntentSender failed to send", e);
        }
        return false;
    }

    /** Clean up any ongoing refinement session. */
    public void destroy() {
        if (mRefinementResultReceiver != null) {
            mRefinementResultReceiver.destroy();
            mRefinementResultReceiver = null;
        }
    }

    private static Intent makeRefinementRequest(
            RefinementResultReceiver resultReceiver, TargetInfo originalTarget) {
        final Intent fillIn = new Intent();
        final List<Intent> sourceIntents = originalTarget.getAllSourceIntents();
        fillIn.putExtra(Intent.EXTRA_INTENT, sourceIntents.get(0));
        final int sourceIntentCount = sourceIntents.size();
        if (sourceIntentCount > 1) {
            fillIn.putExtra(
                    Intent.EXTRA_ALTERNATE_INTENTS,
                    sourceIntents
                            .subList(1, sourceIntentCount)
                            .toArray(new Intent[sourceIntentCount - 1]));
        }
        fillIn.putExtra(Intent.EXTRA_RESULT_RECEIVER, resultReceiver.copyForSending());
        return fillIn;
    }

    private static class RefinementResultReceiver extends ResultReceiver {
        private final Consumer<Intent> mOnSelectionRefined;
        private final Runnable mOnRefinementCancelled;

        private boolean mDestroyed;

        RefinementResultReceiver(
                Consumer<Intent> onSelectionRefined,
                Runnable onRefinementCancelled,
                Handler handler) {
            super(handler);
            mOnSelectionRefined = onSelectionRefined;
            mOnRefinementCancelled = onRefinementCancelled;
        }

        public void destroy() {
            mDestroyed = true;
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            if (mDestroyed) {
                Log.e(TAG, "Destroyed RefinementResultReceiver received a result");
                return;
            }
            if (resultData == null) {
                Log.e(TAG, "RefinementResultReceiver received null resultData");
                // TODO: treat as cancellation?
                return;
            }

            switch (resultCode) {
                case Activity.RESULT_CANCELED:
                    mOnRefinementCancelled.run();
                    break;
                case Activity.RESULT_OK:
                    Parcelable intentParcelable = resultData.getParcelable(Intent.EXTRA_INTENT);
                    if (intentParcelable instanceof Intent) {
                        mOnSelectionRefined.accept((Intent) intentParcelable);
                    } else {
                        Log.e(TAG, "No valid Intent.EXTRA_INTENT in 'OK' refinement result data");
                    }
                    break;
                default:
                    Log.w(TAG, "Received unknown refinement result " + resultCode);
                    break;
            }
        }

        /**
         * Apps can't load this class directly, so we need a regular ResultReceiver copy for
         * sending. Obtain this by parceling and unparceling (one weird trick).
         */
        ResultReceiver copyForSending() {
            Parcel parcel = Parcel.obtain();
            writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            ResultReceiver receiverForSending = ResultReceiver.CREATOR.createFromParcel(parcel);
            parcel.recycle();
            return receiverForSending;
        }
    }
}
