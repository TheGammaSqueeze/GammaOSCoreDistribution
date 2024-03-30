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

package com.android.intentresolver.contentpreview;

import static android.content.ContentProvider.getUserIdFromUri;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.UserHandle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.LayoutRes;

import com.android.intentresolver.R;
import com.android.intentresolver.flags.FeatureFlagRepository;
import com.android.intentresolver.flags.Flags;
import com.android.intentresolver.widget.ActionRow;
import com.android.intentresolver.widget.RoundedRectImageView;

import java.util.ArrayList;
import java.util.List;

abstract class ContentPreviewUi {
    private static final int IMAGE_FADE_IN_MILLIS = 150;
    static final String TAG = "ChooserPreview";

    @ContentPreviewType
    public abstract int getType();

    public abstract ViewGroup display(
            Resources resources, LayoutInflater layoutInflater, ViewGroup parent);

    protected static int getActionRowLayout(FeatureFlagRepository featureFlagRepository) {
        return featureFlagRepository.isEnabled(Flags.SHARESHEET_CUSTOM_ACTIONS)
                ? R.layout.scrollable_chooser_action_row
                : R.layout.chooser_action_row;
    }

    protected static ActionRow inflateActionRow(ViewGroup parent, @LayoutRes int actionRowLayout) {
        final ViewStub stub = parent.findViewById(com.android.intentresolver.R.id.action_row_stub);
        if (stub != null) {
            stub.setLayoutResource(actionRowLayout);
            stub.inflate();
        }
        return parent.findViewById(com.android.internal.R.id.chooser_action_row);
    }

    protected static List<ActionRow.Action> createActions(
            List<ActionRow.Action> systemActions,
            List<ActionRow.Action> customActions,
            FeatureFlagRepository featureFlagRepository) {
        ArrayList<ActionRow.Action> actions =
                new ArrayList<>(systemActions.size() + customActions.size());
        actions.addAll(systemActions);
        if (featureFlagRepository.isEnabled(Flags.SHARESHEET_CUSTOM_ACTIONS)) {
            actions.addAll(customActions);
        }
        return actions;
    }

    /**
     * Indicate if the incoming content URI should be allowed.
     *
     * @param uri the uri to test
     * @return true if the URI is allowed for content preview
     */
    protected static boolean validForContentPreview(Uri uri) throws SecurityException {
        if (uri == null) {
            return false;
        }
        int userId = getUserIdFromUri(uri, UserHandle.USER_CURRENT);
        if (userId != UserHandle.USER_CURRENT && userId != UserHandle.myUserId()) {
            Log.e(ContentPreviewUi.TAG, "dropped invalid content URI belonging to user " + userId);
            return false;
        }
        return true;
    }

    protected static void updateViewWithImage(RoundedRectImageView imageView, Bitmap image) {
        if (image == null) {
            imageView.setVisibility(View.GONE);
            return;
        }
        imageView.setVisibility(View.VISIBLE);
        imageView.setAlpha(0.0f);
        imageView.setImageBitmap(image);

        ValueAnimator fadeAnim = ObjectAnimator.ofFloat(imageView, "alpha", 0.0f, 1.0f);
        fadeAnim.setInterpolator(new DecelerateInterpolator(1.0f));
        fadeAnim.setDuration(IMAGE_FADE_IN_MILLIS);
        fadeAnim.start();
    }

    protected static void displayPayloadReselectionAction(
            ViewGroup layout,
            ChooserContentPreviewUi.ActionFactory actionFactory,
            FeatureFlagRepository featureFlagRepository) {
        Runnable modifyShareAction = actionFactory.getModifyShareAction();
        if (modifyShareAction != null && layout != null
                && featureFlagRepository.isEnabled(Flags.SHARESHEET_RESELECTION_ACTION)) {
            View modifyShareView = layout.findViewById(R.id.reselection_action);
            if (modifyShareView != null) {
                modifyShareView.setVisibility(View.VISIBLE);
                modifyShareView.setOnClickListener(view -> modifyShareAction.run());
            }
        }
    }
}
