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

import static com.android.intentresolver.contentpreview.ContentPreviewType.CONTENT_PREVIEW_IMAGE;

import android.content.res.Resources;
import android.net.Uri;
import android.text.TextUtils;
import android.text.util.Linkify;
import android.transition.TransitionManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;

import com.android.intentresolver.ImageLoader;
import com.android.intentresolver.R;
import com.android.intentresolver.flags.FeatureFlagRepository;
import com.android.intentresolver.flags.Flags;
import com.android.intentresolver.widget.ActionRow;
import com.android.intentresolver.widget.ImagePreviewView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

class ImageContentPreviewUi extends ContentPreviewUi {
    private final List<Uri> mImageUris;
    @Nullable
    private final CharSequence mText;
    private final ChooserContentPreviewUi.ActionFactory mActionFactory;
    private final ImageLoader mImageLoader;
    private final ImagePreviewView.TransitionElementStatusCallback mTransitionElementStatusCallback;
    private final FeatureFlagRepository mFeatureFlagRepository;

    ImageContentPreviewUi(
            List<Uri> imageUris,
            @Nullable CharSequence text,
            ChooserContentPreviewUi.ActionFactory actionFactory,
            ImageLoader imageLoader,
            ImagePreviewView.TransitionElementStatusCallback transitionElementStatusCallback,
            FeatureFlagRepository featureFlagRepository) {
        mImageUris = imageUris;
        mText = text;
        mActionFactory = actionFactory;
        mImageLoader = imageLoader;
        mTransitionElementStatusCallback = transitionElementStatusCallback;
        mFeatureFlagRepository = featureFlagRepository;

        mImageLoader.prePopulate(mImageUris);
    }

    @Override
    public int getType() {
        return CONTENT_PREVIEW_IMAGE;
    }

    @Override
    public ViewGroup display(Resources resources, LayoutInflater layoutInflater, ViewGroup parent) {
        ViewGroup layout = displayInternal(layoutInflater, parent);
        displayPayloadReselectionAction(layout, mActionFactory, mFeatureFlagRepository);
        return layout;
    }

    private ViewGroup displayInternal(LayoutInflater layoutInflater, ViewGroup parent) {
        @LayoutRes int actionRowLayout = getActionRowLayout(mFeatureFlagRepository);
        ViewGroup contentPreviewLayout = (ViewGroup) layoutInflater.inflate(
                R.layout.chooser_grid_preview_image, parent, false);
        ImagePreviewView imagePreview = inflateImagePreviewView(contentPreviewLayout);

        final ActionRow actionRow = inflateActionRow(contentPreviewLayout, actionRowLayout);
        if (actionRow != null) {
            actionRow.setActions(
                    createActions(
                            createImagePreviewActions(),
                            mActionFactory.createCustomActions(),
                            mFeatureFlagRepository));
        }

        if (mImageUris.size() == 0) {
            Log.i(
                    TAG,
                    "Attempted to display image preview area with zero"
                        + " available images detected in EXTRA_STREAM list");
            ((View) imagePreview).setVisibility(View.GONE);
            mTransitionElementStatusCallback.onAllTransitionElementsReady();
            return contentPreviewLayout;
        }

        setTextInImagePreviewVisibility(contentPreviewLayout, mActionFactory);
        imagePreview.setTransitionElementStatusCallback(mTransitionElementStatusCallback);
        imagePreview.setImages(mImageUris, mImageLoader);

        return contentPreviewLayout;
    }

    private List<ActionRow.Action> createImagePreviewActions() {
        ArrayList<ActionRow.Action> actions = new ArrayList<>(2);
        //TODO: add copy action;
        ActionRow.Action action = mActionFactory.createNearbyButton();
        if (action != null) {
            actions.add(action);
        }
        action = mActionFactory.createEditButton();
        if (action != null) {
            actions.add(action);
        }
        return actions;
    }

    private ImagePreviewView inflateImagePreviewView(ViewGroup previewLayout) {
        ViewStub stub = previewLayout.findViewById(R.id.image_preview_stub);
        if (stub != null) {
            int layoutId =
                    mFeatureFlagRepository.isEnabled(Flags.SHARESHEET_SCROLLABLE_IMAGE_PREVIEW)
                            ? R.layout.scrollable_image_preview_view
                            : R.layout.chooser_image_preview_view;
            stub.setLayoutResource(layoutId);
            stub.inflate();
        }
        return previewLayout.findViewById(
                com.android.internal.R.id.content_preview_image_area);
    }

    private void setTextInImagePreviewVisibility(
            ViewGroup contentPreview, ChooserContentPreviewUi.ActionFactory actionFactory) {
        int visibility = mFeatureFlagRepository.isEnabled(Flags.SHARESHEET_IMAGE_AND_TEXT_PREVIEW)
                && !TextUtils.isEmpty(mText)
                ? View.VISIBLE
                : View.GONE;

        final TextView textView = contentPreview
                .requireViewById(com.android.internal.R.id.content_preview_text);
        CheckBox actionView = contentPreview
                .requireViewById(R.id.include_text_action);
        textView.setVisibility(visibility);
        boolean isLink = visibility == View.VISIBLE && HttpUriMatcher.isHttpUri(mText.toString());
        textView.setAutoLinkMask(isLink ? Linkify.WEB_URLS : 0);
        textView.setText(mText);

        if (visibility == View.VISIBLE) {
            final int[] actionLabels = isLink
                    ? new int[] { R.string.include_link, R.string.exclude_link }
                    : new int[] { R.string.include_text, R.string.exclude_text };
            final Consumer<Boolean> shareTextAction = actionFactory.getExcludeSharedTextAction();
            actionView.setChecked(true);
            actionView.setText(actionLabels[1]);
            shareTextAction.accept(false);
            actionView.setOnCheckedChangeListener((view, isChecked) -> {
                view.setText(actionLabels[isChecked ? 1 : 0]);
                TransitionManager.beginDelayedTransition((ViewGroup) textView.getParent());
                textView.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                shareTextAction.accept(!isChecked);
            });
        }
        actionView.setVisibility(visibility);
    }
}
