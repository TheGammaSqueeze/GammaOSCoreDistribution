package com.android.server.tvproviderstub;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.content.ContentUris;

public class StubTvProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        // Initialization code if necessary
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        // Assuming the app needs a cursor with an '_id' and two more columns named 'column2' and 'column3'
        MatrixCursor cursor = new MatrixCursor(new String[] {"_id", "column2", "column3"});
        // Add a dummy row. Adjust the data as necessary to match app expectations
        cursor.addRow(new Object[] {1, "Dummy data 1", "Dummy data 2"});
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        // Return a MIME type according to the data at the given URI
        return "vnd.android.cursor.dir/vnd.com.example.provider.element";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // Assume _id is auto-increment and is needed by the app for some operations
        long dummyId = 1; // Dummy ID for demonstration purposes
        return ContentUris.withAppendedId(uri, dummyId);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // Since it's a stub, no actual data is deleted
        return 0; // Return the number of rows affected
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // Since it's a stub, no actual data is updated
        return 0; // Return the number of rows affected
    }
}
