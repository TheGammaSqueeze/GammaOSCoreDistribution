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

package com.android.intentresolver.contentpreview;

import static com.android.intentresolver.contentpreview.ContentPreviewType.CONTENT_PREVIEW_FILE;
import static com.android.intentresolver.contentpreview.ContentPreviewType.CONTENT_PREVIEW_IMAGE;
import static com.android.intentresolver.contentpreview.ContentPreviewType.CONTENT_PREVIEW_TEXT;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ContentInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.android.intentresolver.ImageLoader;
import com.android.intentresolver.flags.FeatureFlagRepository;
import com.android.intentresolver.widget.ActionRow;
import com.android.intentresolver.widget.ImagePreviewView;
import com.android.intentresolver.widget.ImagePreviewView.TransitionElementStatusCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Collection of helpers for building the content preview UI displayed in
 * {@link com.android.intentresolver.ChooserActivity}.
 *
 * A content preview façade.
 */
public final class ChooserContentPreviewUi {
    /**
     * Delegate to build the default system action buttons to display in the preview layout, if/when
     * they're determined to be appropriate for the particular preview we display.
     * TODO: clarify why action buttons are part of preview logic.
     */
    public interface ActionFactory {
        /** Create an action that copies the share content to the clipboard. */
        ActionRow.Action createCopyButton();

        /** Create an action that opens the share content in a system-default editor. */
        @Nullable
        ActionRow.Action createEditButton();

        /** Create an "Share to Nearby" action. */
        @Nullable
        ActionRow.Action createNearbyButton();

        /** Create custom actions */
        List<ActionRow.Action> createCustomActions();

        /**
         * Provides a share modification action, if any.
         */
        @Nullable
        Runnable getModifyShareAction();

        /**
         * <p>
         * Creates an exclude-text action that can be called when the user changes shared text
         * status in the Media + Text preview.
         * </p>
         * <p>
         * <code>true</code> argument value indicates that the text should be excluded.
         * </p>
         */
        Consumer<Boolean> getExcludeSharedTextAction();
    }

    /**
     * Testing shim to specify whether a given mime type is considered to be an "image."
     *
     * TODO: move away from {@link ChooserActivityOverrideData} as a model to configure our tests,
     * then migrate {@link com.android.intentresolver.ChooserActivity#isImageType(String)} into this
     * class.
     */
    public interface ImageMimeTypeClassifier {
        /** @return whether the specified {@code mimeType} is classified as an "image" type. */
        boolean isImageType(String mimeType);
    }

    private final ContentPreviewUi mContentPreviewUi;

    public ChooserContentPreviewUi(
            Intent targetIntent,
            ContentInterface contentResolver,
            ImageMimeTypeClassifier imageClassifier,
            ImageLoader imageLoader,
            ActionFactory actionFactory,
            TransitionElementStatusCallback transitionElementStatusCallback,
            FeatureFlagRepository featureFlagRepository) {

        mContentPreviewUi = createContentPreview(
                targetIntent,
                contentResolver,
                imageClassifier,
                imageLoader,
                actionFactory,
                transitionElementStatusCallback,
                featureFlagRepository);
        if (mContentPreviewUi.getType() != CONTENT_PREVIEW_IMAGE) {
            transitionElementStatusCallback.onAllTransitionElementsReady();
        }
    }

    private ContentPreviewUi createContentPreview(
            Intent targetIntent,
            ContentInterface contentResolver,
            ImageMimeTypeClassifier imageClassifier,
            ImageLoader imageLoader,
            ActionFactory actionFactory,
            TransitionElementStatusCallback transitionElementStatusCallback,
            FeatureFlagRepository featureFlagRepository) {
        int type = findPreferredContentPreview(targetIntent, contentResolver, imageClassifier);
        switch (type) {
            case CONTENT_PREVIEW_TEXT:
                return createTextPreview(
                        targetIntent, actionFactory, imageLoader, featureFlagRepository);

            case CONTENT_PREVIEW_FILE:
                return new FileContentPreviewUi(
                        extractContentUris(targetIntent),
                        actionFactory,
                        imageLoader,
                        contentResolver,
                        featureFlagRepository);

            case CONTENT_PREVIEW_IMAGE:
                return createImagePreview(
                        targetIntent,
                        actionFactory,
                        contentResolver,
                        imageClassifier,
                        imageLoader,
                        transitionElementStatusCallback,
                        featureFlagRepository);
        }

        return new NoContextPreviewUi(type);
    }

    public int getPreferredContentPreview() {
        return mContentPreviewUi.getType();
    }

    /**
     * Display a content preview of the specified {@code previewType} to preview the content of the
     * specified {@code intent}.
     */
    public ViewGroup displayContentPreview(
            Resources resources, LayoutInflater layoutInflater, ViewGroup parent) {

        return mContentPreviewUi.display(resources, layoutInflater, parent);
    }

    /** Determine the most appropriate type of preview to show for the provided {@link Intent}. */
    @ContentPreviewType
    private static int findPreferredContentPreview(
            Intent targetIntent,
            ContentInterface resolver,
            ImageMimeTypeClassifier imageClassifier) {
        /* In {@link android.content.Intent#getType}, the app may specify a very general mime type
         * that broadly covers all data being shared, such as {@literal *}/* when sending an image
         * and text. We therefore should inspect each item for the preferred type, in order: IMAGE,
         * FILE, TEXT.  */
        final String action = targetIntent.getAction();
        final String type = targetIntent.getType();
        final boolean isSend = Intent.ACTION_SEND.equals(action);
        final boolean isSendMultiple = Intent.ACTION_SEND_MULTIPLE.equals(action);

        if (!(isSend || isSendMultiple)
                || (type != null && ClipDescription.compareMimeTypes(type, "text/*"))) {
            return CONTENT_PREVIEW_TEXT;
        }

        if (isSend) {
            Uri uri = targetIntent.getParcelableExtra(Intent.EXTRA_STREAM);
            return findPreferredContentPreview(uri, resolver, imageClassifier);
        }

        List<Uri> uris = targetIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        if (uris == null || uris.isEmpty()) {
            return CONTENT_PREVIEW_TEXT;
        }

        for (Uri uri : uris) {
            // Defaulting to file preview when there are mixed image/file types is
            // preferable, as it shows the user the correct number of items being shared
            int uriPreviewType = findPreferredContentPreview(uri, resolver, imageClassifier);
            if (uriPreviewType == CONTENT_PREVIEW_FILE) {
                return CONTENT_PREVIEW_FILE;
            }
        }

        return CONTENT_PREVIEW_IMAGE;
    }

    @ContentPreviewType
    private static int findPreferredContentPreview(
            Uri uri, ContentInterface resolver, ImageMimeTypeClassifier imageClassifier) {
        if (uri == null) {
            return CONTENT_PREVIEW_TEXT;
        }

        String mimeType = null;
        try {
            mimeType = resolver.getType(uri);
        } catch (RemoteException ignored) {
        }
        return imageClassifier.isImageType(mimeType) ? CONTENT_PREVIEW_IMAGE : CONTENT_PREVIEW_FILE;
    }

    private static TextContentPreviewUi createTextPreview(
            Intent targetIntent,
            ChooserContentPreviewUi.ActionFactory actionFactory,
            ImageLoader imageLoader,
            FeatureFlagRepository featureFlagRepository) {
        CharSequence sharingText = targetIntent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        String previewTitle = targetIntent.getStringExtra(Intent.EXTRA_TITLE);
        ClipData previewData = targetIntent.getClipData();
        Uri previewThumbnail = null;
        if (previewData != null) {
            if (previewData.getItemCount() > 0) {
                ClipData.Item previewDataItem = previewData.getItemAt(0);
                previewThumbnail = previewDataItem.getUri();
            }
        }
        return new TextContentPreviewUi(
                sharingText,
                previewTitle,
                previewThumbnail,
                actionFactory,
                imageLoader,
                featureFlagRepository);
    }

    static ImageContentPreviewUi createImagePreview(
            Intent targetIntent,
            ChooserContentPreviewUi.ActionFactory actionFactory,
            ContentInterface contentResolver,
            ChooserContentPreviewUi.ImageMimeTypeClassifier imageClassifier,
            ImageLoader imageLoader,
            ImagePreviewView.TransitionElementStatusCallback transitionElementStatusCallback,
            FeatureFlagRepository featureFlagRepository) {
        CharSequence text = targetIntent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        String action = targetIntent.getAction();
        // TODO: why don't we use image classifier for single-element ACTION_SEND?
        final List<Uri> imageUris = Intent.ACTION_SEND.equals(action)
                ? extractContentUris(targetIntent)
                : extractContentUris(targetIntent)
                        .stream()
                        .filter(uri -> {
                            String type = null;
                            try {
                                type = contentResolver.getType(uri);
                            } catch (RemoteException ignored) {
                            }
                            return imageClassifier.isImageType(type);
                        })
                        .collect(Collectors.toList());
        return new ImageContentPreviewUi(
                imageUris,
                text,
                actionFactory,
                imageLoader,
                transitionElementStatusCallback,
                featureFlagRepository);
    }

    private static List<Uri> extractContentUris(Intent targetIntent) {
        List<Uri> uris = new ArrayList<>();
        if (Intent.ACTION_SEND.equals(targetIntent.getAction())) {
            Uri uri = targetIntent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (ContentPreviewUi.validForContentPreview(uri)) {
                uris.add(uri);
            }
        } else {
            List<Uri> receivedUris = targetIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (receivedUris != null) {
                for (Uri uri : receivedUris) {
                    if (ContentPreviewUi.validForContentPreview(uri)) {
                        uris.add(uri);
                    }
                }
            }
        }
        return uris;
    }
}
