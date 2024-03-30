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

import android.content.res.Resources;
import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;

import com.android.intentresolver.ImageLoader;
import com.android.intentresolver.R;
import com.android.intentresolver.flags.FeatureFlagRepository;
import com.android.intentresolver.widget.ActionRow;

import java.util.ArrayList;
import java.util.List;

class TextContentPreviewUi extends ContentPreviewUi {
    @Nullable
    private final CharSequence mSharingText;
    @Nullable
    private final CharSequence mPreviewTitle;
    @Nullable
    private final Uri mPreviewThumbnail;
    private final ImageLoader mImageLoader;
    private final ChooserContentPreviewUi.ActionFactory mActionFactory;
    private final FeatureFlagRepository mFeatureFlagRepository;

    TextContentPreviewUi(
            @Nullable CharSequence sharingText,
            @Nullable CharSequence previewTitle,
            @Nullable Uri previewThumbnail,
            ChooserContentPreviewUi.ActionFactory actionFactory,
            ImageLoader imageLoader,
            FeatureFlagRepository featureFlagRepository) {
        mSharingText = sharingText;
        mPreviewTitle = previewTitle;
        mPreviewThumbnail = previewThumbnail;
        mImageLoader = imageLoader;
        mActionFactory = actionFactory;
        mFeatureFlagRepository = featureFlagRepository;
    }

    @Override
    public int getType() {
        return ContentPreviewType.CONTENT_PREVIEW_TEXT;
    }

    @Override
    public ViewGroup display(Resources resources, LayoutInflater layoutInflater, ViewGroup parent) {
        ViewGroup layout = displayInternal(layoutInflater, parent);
        displayPayloadReselectionAction(layout, mActionFactory, mFeatureFlagRepository);
        return layout;
    }

    private ViewGroup displayInternal(
            LayoutInflater layoutInflater,
            ViewGroup parent) {
        @LayoutRes int actionRowLayout = getActionRowLayout(mFeatureFlagRepository);
        ViewGroup contentPreviewLayout = (ViewGroup) layoutInflater.inflate(
                R.layout.chooser_grid_preview_text, parent, false);

        final ActionRow actionRow = inflateActionRow(contentPreviewLayout, actionRowLayout);
        if (actionRow != null) {
            actionRow.setActions(
                    createActions(
                            createTextPreviewActions(),
                            mActionFactory.createCustomActions(),
                            mFeatureFlagRepository));
        }

        if (mSharingText == null) {
            contentPreviewLayout
                    .findViewById(com.android.internal.R.id.content_preview_text_layout)
                    .setVisibility(View.GONE);
        } else {
            TextView textView = contentPreviewLayout.findViewById(
                    com.android.internal.R.id.content_preview_text);
            textView.setText(mSharingText);
        }

        if (TextUtils.isEmpty(mPreviewTitle)) {
            contentPreviewLayout
                    .findViewById(com.android.internal.R.id.content_preview_title_layout)
                    .setVisibility(View.GONE);
        } else {
            TextView previewTitleView = contentPreviewLayout.findViewById(
                    com.android.internal.R.id.content_preview_title);
            previewTitleView.setText(mPreviewTitle);

            ImageView previewThumbnailView = contentPreviewLayout.findViewById(
                    com.android.internal.R.id.content_preview_thumbnail);
            if (!validForContentPreview(mPreviewThumbnail)) {
                previewThumbnailView.setVisibility(View.GONE);
            } else {
                mImageLoader.loadImage(
                        mPreviewThumbnail,
                        (bitmap) -> updateViewWithImage(
                                contentPreviewLayout.findViewById(
                                        com.android.internal.R.id.content_preview_thumbnail),
                                bitmap));
            }
        }

        return contentPreviewLayout;
    }

    private List<ActionRow.Action> createTextPreviewActions() {
        ArrayList<ActionRow.Action> actions = new ArrayList<>(2);
        actions.add(mActionFactory.createCopyButton());
        ActionRow.Action nearbyAction = mActionFactory.createNearbyButton();
        if (nearbyAction != null) {
            actions.add(nearbyAction);
        }
        return actions;
    }
}
