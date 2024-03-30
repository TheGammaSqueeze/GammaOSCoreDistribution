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

import android.content.ContentInterface;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.DocumentsContract;
import android.provider.Downloads;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.util.PluralsMessageFormatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.LayoutRes;

import com.android.intentresolver.ImageLoader;
import com.android.intentresolver.R;
import com.android.intentresolver.flags.FeatureFlagRepository;
import com.android.intentresolver.widget.ActionRow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class FileContentPreviewUi extends ContentPreviewUi {
    private static final String PLURALS_COUNT  = "count";
    private static final String PLURALS_FILE_NAME = "file_name";

    private final List<Uri> mUris;
    private final ChooserContentPreviewUi.ActionFactory mActionFactory;
    private final ImageLoader mImageLoader;
    private final ContentInterface mContentResolver;
    private final FeatureFlagRepository mFeatureFlagRepository;

    FileContentPreviewUi(List<Uri> uris,
            ChooserContentPreviewUi.ActionFactory actionFactory,
            ImageLoader imageLoader,
            ContentInterface contentResolver,
            FeatureFlagRepository featureFlagRepository) {
        mUris = uris;
        mActionFactory = actionFactory;
        mImageLoader = imageLoader;
        mContentResolver = contentResolver;
        mFeatureFlagRepository = featureFlagRepository;
    }

    @Override
    public int getType() {
        return ContentPreviewType.CONTENT_PREVIEW_FILE;
    }

    @Override
    public ViewGroup display(Resources resources, LayoutInflater layoutInflater, ViewGroup parent) {
        ViewGroup layout = displayInternal(resources, layoutInflater, parent);
        displayPayloadReselectionAction(layout, mActionFactory, mFeatureFlagRepository);
        return layout;
    }

    private ViewGroup displayInternal(
            Resources resources, LayoutInflater layoutInflater, ViewGroup parent) {
        @LayoutRes int actionRowLayout = getActionRowLayout(mFeatureFlagRepository);
        ViewGroup contentPreviewLayout = (ViewGroup) layoutInflater.inflate(
                R.layout.chooser_grid_preview_file, parent, false);

        final int uriCount = mUris.size();

        if (uriCount == 0) {
            contentPreviewLayout.setVisibility(View.GONE);
            Log.i(TAG, "Appears to be no uris available in EXTRA_STREAM,"
                    + " removing preview area");
            return contentPreviewLayout;
        }

        if (uriCount == 1) {
            loadFileUriIntoView(mUris.get(0), contentPreviewLayout, mImageLoader, mContentResolver);
        } else {
            FileInfo fileInfo = extractFileInfo(mUris.get(0), mContentResolver);
            int remUriCount = uriCount - 1;
            Map<String, Object> arguments = new HashMap<>();
            arguments.put(PLURALS_COUNT, remUriCount);
            arguments.put(PLURALS_FILE_NAME, fileInfo.name);
            String fileName =
                    PluralsMessageFormatter.format(resources, arguments, R.string.file_count);

            TextView fileNameView = contentPreviewLayout.findViewById(
                    com.android.internal.R.id.content_preview_filename);
            fileNameView.setText(fileName);

            View thumbnailView = contentPreviewLayout.findViewById(
                    com.android.internal.R.id.content_preview_file_thumbnail);
            thumbnailView.setVisibility(View.GONE);

            ImageView fileIconView = contentPreviewLayout.findViewById(
                    com.android.internal.R.id.content_preview_file_icon);
            fileIconView.setVisibility(View.VISIBLE);
            fileIconView.setImageResource(R.drawable.ic_file_copy);
        }

        final ActionRow actionRow = inflateActionRow(contentPreviewLayout, actionRowLayout);
        if (actionRow != null) {
            actionRow.setActions(
                    createActions(
                            createFilePreviewActions(),
                            mActionFactory.createCustomActions(),
                            mFeatureFlagRepository));
        }

        return contentPreviewLayout;
    }

    private List<ActionRow.Action> createFilePreviewActions() {
        List<ActionRow.Action> actions = new ArrayList<>(1);
        //TODO(b/120417119):
        // add action buttonFactory.createCopyButton()
        ActionRow.Action action = mActionFactory.createNearbyButton();
        if (action != null) {
            actions.add(action);
        }
        return actions;
    }

    private static void loadFileUriIntoView(
            final Uri uri,
            final View parent,
            final ImageLoader imageLoader,
            final ContentInterface contentResolver) {
        FileInfo fileInfo = extractFileInfo(uri, contentResolver);

        TextView fileNameView = parent.findViewById(
                com.android.internal.R.id.content_preview_filename);
        fileNameView.setText(fileInfo.name);

        if (fileInfo.hasThumbnail) {
            imageLoader.loadImage(
                    uri,
                    (bitmap) -> updateViewWithImage(
                            parent.findViewById(
                                    com.android.internal.R.id.content_preview_file_thumbnail),
                            bitmap));
        } else {
            View thumbnailView = parent.findViewById(
                    com.android.internal.R.id.content_preview_file_thumbnail);
            thumbnailView.setVisibility(View.GONE);

            ImageView fileIconView = parent.findViewById(
                    com.android.internal.R.id.content_preview_file_icon);
            fileIconView.setVisibility(View.VISIBLE);
            fileIconView.setImageResource(R.drawable.chooser_file_generic);
        }
    }

    private static FileInfo extractFileInfo(Uri uri, ContentInterface resolver) {
        String fileName = null;
        boolean hasThumbnail = false;

        try (Cursor cursor = queryResolver(resolver, uri)) {
            if (cursor != null && cursor.getCount() > 0) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int titleIndex = cursor.getColumnIndex(Downloads.Impl.COLUMN_TITLE);
                int flagsIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS);

                cursor.moveToFirst();
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex);
                } else if (titleIndex != -1) {
                    fileName = cursor.getString(titleIndex);
                }

                if (flagsIndex != -1) {
                    hasThumbnail = (cursor.getInt(flagsIndex)
                            & DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL) != 0;
                }
            }
        } catch (SecurityException | NullPointerException e) {
            // The ContentResolver already logs the exception. Log something more informative.
            Log.w(
                    TAG,
                    "Could not load (" + uri.toString() + ") thumbnail/name for preview. If "
                    + "desired, consider using Intent#createChooser to launch the ChooserActivity, "
                    + "and set your Intent's clipData and flags in accordance with that method's "
                    + "documentation");
        }

        if (TextUtils.isEmpty(fileName)) {
            fileName = uri.getPath();
            fileName = fileName == null ? "" : fileName;
            int index = fileName.lastIndexOf('/');
            if (index != -1) {
                fileName = fileName.substring(index + 1);
            }
        }

        return new FileInfo(fileName, hasThumbnail);
    }

    private static Cursor queryResolver(ContentInterface resolver, Uri uri) {
        try {
            return resolver.query(uri, null, null, null);
        } catch (RemoteException e) {
            return null;
        }
    }

    private static class FileInfo {
        public final String name;
        public final boolean hasThumbnail;

        FileInfo(String name, boolean hasThumbnail) {
            this.name = name;
            this.hasThumbnail = hasThumbnail;
        }
    }
}
