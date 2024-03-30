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

package android.security.cts.BUG_261036568_provider;

import static android.os.Binder.getCallingUid;
import static android.os.Binder.getCallingUserHandle;
import static android.os.Process.myUid;
import static android.os.Process.myUserHandle;
import static android.provider.DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class ImageProvider extends ContentProvider {

    private final Set<String> accessedUris = new HashSet<>();

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public AssetFileDescriptor openAssetFile(Uri uri, String mode) {
        maybeRecordUriAccess(uri);
        try {
            return getContext().getAssets().openFd("x.png");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) {
        AssetFileDescriptor fd = openAssetFile(uri, mode);
        return fd == null ? null : fd.getParcelFileDescriptor();
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle result = new Bundle();
        if (method.equals("verify")) {
            result.putBoolean("passed", accessedUris.isEmpty());
            result.putStringArrayList("accessed_uris", new ArrayList<>(accessedUris));
            accessedUris.clear();
        }
        return result;
    }


    @Override
    public String getType(Uri uri) {
        return uri.getPath().endsWith(".png") ? "image/png" : "*/*";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(new String[] {
                OpenableColumns.DISPLAY_NAME,
                DocumentsContract.Root.COLUMN_TITLE,
                DocumentsContract.Document.COLUMN_FLAGS
        });
        cursor.addRow(new Object[] {
                "DISPLAY_NAME",
                "TITLE",
                FLAG_SUPPORTS_THUMBNAIL
        });
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection,
            String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    private void maybeRecordUriAccess(Uri uri) {
        UserHandle caller = getCallingUserHandle();
        if (!myUserHandle().equals(caller)) {
            accessedUris.add("uri=" + uri.toString()
                    + ", owner_uid=" + myUid()
                    + ", caller_uid=" + getCallingUid()
                    + " ('" + getCallingPackage() + "')");
        }
    }
}