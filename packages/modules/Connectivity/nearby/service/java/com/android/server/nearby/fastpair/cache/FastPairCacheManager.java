/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.nearby.fastpair.cache;

import android.bluetooth.le.ScanResult;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.android.server.nearby.common.eventloop.Annotations;

import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;
import java.util.List;

import service.proto.Cache;
import service.proto.Rpcs;


/**
 * Save FastPair device info to database to avoid multiple requesting.
 */
public class FastPairCacheManager {
    private final Context mContext;
    private final FastPairDbHelper mFastPairDbHelper;

    public FastPairCacheManager(Context context) {
        mContext = context;
        mFastPairDbHelper = new FastPairDbHelper(context);
    }

    /**
     * Clean up function to release db
     */
    public void cleanUp() {
        mFastPairDbHelper.close();
    }

    /**
     * Saves the response to the db
     */
    private void saveDevice() {
    }

    Cache.ServerResponseDbItem getDeviceFromScanResult(ScanResult scanResult) {
        return Cache.ServerResponseDbItem.newBuilder().build();
    }

    /**
     * Checks if the entry can be auto deleted from the cache
     */
    public boolean isDeletable(Cache.ServerResponseDbItem entry) {
        if (!entry.getExpirable()) {
            return false;
        }
        return true;
    }

    /**
     * Save discovery item into database. Discovery item is item that discovered through Ble before
     * pairing success.
     */
    public boolean saveDiscoveryItem(DiscoveryItem item) {

        SQLiteDatabase db = mFastPairDbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DiscoveryItemContract.DiscoveryItemEntry.COLUMN_MODEL_ID, item.getTriggerId());
        values.put(DiscoveryItemContract.DiscoveryItemEntry.COLUMN_SCAN_BYTE,
                item.getCopyOfStoredItem().toByteArray());
        db.insert(DiscoveryItemContract.DiscoveryItemEntry.TABLE_NAME, null, values);
        return true;
    }


    @Annotations.EventThread
    private Rpcs.GetObservedDeviceResponse getObservedDeviceInfo(ScanResult scanResult) {
        return Rpcs.GetObservedDeviceResponse.getDefaultInstance();
    }

    /**
     * Get discovery item from item id.
     */
    public DiscoveryItem getDiscoveryItem(String itemId) {
        return new DiscoveryItem(mContext, getStoredDiscoveryItem(itemId));
    }

    /**
     * Get discovery item from item id.
     */
    public Cache.StoredDiscoveryItem getStoredDiscoveryItem(String itemId) {
        SQLiteDatabase db = mFastPairDbHelper.getReadableDatabase();
        String[] projection = {
                DiscoveryItemContract.DiscoveryItemEntry.COLUMN_MODEL_ID,
                DiscoveryItemContract.DiscoveryItemEntry.COLUMN_SCAN_BYTE
        };
        String selection = DiscoveryItemContract.DiscoveryItemEntry.COLUMN_MODEL_ID + " =? ";
        String[] selectionArgs = {itemId};
        Cursor cursor = db.query(
                DiscoveryItemContract.DiscoveryItemEntry.TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                null
        );

        if (cursor.moveToNext()) {
            byte[] res = cursor.getBlob(cursor.getColumnIndexOrThrow(
                    DiscoveryItemContract.DiscoveryItemEntry.COLUMN_SCAN_BYTE));
            try {
                Cache.StoredDiscoveryItem item = Cache.StoredDiscoveryItem.parseFrom(res);
                return item;
            } catch (InvalidProtocolBufferException e) {
                Log.e("FastPairCacheManager", "storediscovery has error");
            }
        }
        cursor.close();
        return Cache.StoredDiscoveryItem.getDefaultInstance();
    }

    /**
     * Get all of the discovery item related info in the cache.
     */
    public List<Cache.StoredDiscoveryItem> getAllSavedStoreDiscoveryItem() {
        List<Cache.StoredDiscoveryItem> storedDiscoveryItemList = new ArrayList<>();
        SQLiteDatabase db = mFastPairDbHelper.getReadableDatabase();
        String[] projection = {
                DiscoveryItemContract.DiscoveryItemEntry.COLUMN_MODEL_ID,
                DiscoveryItemContract.DiscoveryItemEntry.COLUMN_SCAN_BYTE
        };
        Cursor cursor = db.query(
                DiscoveryItemContract.DiscoveryItemEntry.TABLE_NAME,
                projection,
                null,
                null,
                null,
                null,
                null
        );

        while (cursor.moveToNext()) {
            byte[] res = cursor.getBlob(cursor.getColumnIndexOrThrow(
                    DiscoveryItemContract.DiscoveryItemEntry.COLUMN_SCAN_BYTE));
            try {
                Cache.StoredDiscoveryItem item = Cache.StoredDiscoveryItem.parseFrom(res);
                storedDiscoveryItemList.add(item);
            } catch (InvalidProtocolBufferException e) {
                Log.e("FastPairCacheManager", "storediscovery has error");
            }

        }
        cursor.close();
        return storedDiscoveryItemList;
    }

    /**
     * Get scan result from local database use model id
     */
    public Cache.StoredScanResult getStoredScanResult(String modelId) {
        return Cache.StoredScanResult.getDefaultInstance();
    }

    /**
     * Gets the paired Fast Pair item that paired to the phone through mac address.
     */
    public Cache.StoredFastPairItem getStoredFastPairItemFromMacAddress(String macAddress) {
        SQLiteDatabase db = mFastPairDbHelper.getReadableDatabase();
        String[] projection = {
                StoredFastPairItemContract.StoredFastPairItemEntry.COLUMN_ACCOUNT_KEY,
                StoredFastPairItemContract.StoredFastPairItemEntry.COLUMN_MAC_ADDRESS,
                StoredFastPairItemContract.StoredFastPairItemEntry.COLUMN_STORED_FAST_PAIR_BYTE
        };
        String selection =
                StoredFastPairItemContract.StoredFastPairItemEntry.COLUMN_MAC_ADDRESS + " =? ";
        String[] selectionArgs = {macAddress};
        Cursor cursor = db.query(
                StoredFastPairItemContract.StoredFastPairItemEntry.TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                null
        );

        if (cursor.moveToNext()) {
            byte[] res = cursor.getBlob(cursor.getColumnIndexOrThrow(
                    StoredFastPairItemContract.StoredFastPairItemEntry
                            .COLUMN_STORED_FAST_PAIR_BYTE));
            try {
                Cache.StoredFastPairItem item = Cache.StoredFastPairItem.parseFrom(res);
                return item;
            } catch (InvalidProtocolBufferException e) {
                Log.e("FastPairCacheManager", "storediscovery has error");
            }
        }
        cursor.close();
        return Cache.StoredFastPairItem.getDefaultInstance();
    }

    /**
     * Save paired fast pair item into the database.
     */
    public boolean putStoredFastPairItem(Cache.StoredFastPairItem storedFastPairItem) {
        SQLiteDatabase db = mFastPairDbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(StoredFastPairItemContract.StoredFastPairItemEntry.COLUMN_MAC_ADDRESS,
                storedFastPairItem.getMacAddress());
        values.put(StoredFastPairItemContract.StoredFastPairItemEntry.COLUMN_ACCOUNT_KEY,
                storedFastPairItem.getAccountKey().toString());
        values.put(StoredFastPairItemContract.StoredFastPairItemEntry.COLUMN_STORED_FAST_PAIR_BYTE,
                storedFastPairItem.toByteArray());
        db.insert(StoredFastPairItemContract.StoredFastPairItemEntry.TABLE_NAME, null, values);
        return true;

    }

    /**
     * Removes certain storedFastPairItem so that it can update timely.
     */
    public void removeStoredFastPairItem(String macAddress) {
        SQLiteDatabase db = mFastPairDbHelper.getWritableDatabase();
        int res = db.delete(StoredFastPairItemContract.StoredFastPairItemEntry.TABLE_NAME,
                StoredFastPairItemContract.StoredFastPairItemEntry.COLUMN_MAC_ADDRESS + "=?",
                new String[]{macAddress});

    }

    /**
     * Get all of the store fast pair item related info in the cache.
     */
    public List<Cache.StoredFastPairItem> getAllSavedStoredFastPairItem() {
        List<Cache.StoredFastPairItem> storedFastPairItemList = new ArrayList<>();
        SQLiteDatabase db = mFastPairDbHelper.getReadableDatabase();
        String[] projection = {
                StoredFastPairItemContract.StoredFastPairItemEntry.COLUMN_MAC_ADDRESS,
                StoredFastPairItemContract.StoredFastPairItemEntry.COLUMN_ACCOUNT_KEY,
                StoredFastPairItemContract.StoredFastPairItemEntry.COLUMN_STORED_FAST_PAIR_BYTE
        };
        Cursor cursor = db.query(
                StoredFastPairItemContract.StoredFastPairItemEntry.TABLE_NAME,
                projection,
                null,
                null,
                null,
                null,
                null
        );

        while (cursor.moveToNext()) {
            byte[] res = cursor.getBlob(cursor.getColumnIndexOrThrow(StoredFastPairItemContract
                    .StoredFastPairItemEntry.COLUMN_STORED_FAST_PAIR_BYTE));
            try {
                Cache.StoredFastPairItem item = Cache.StoredFastPairItem.parseFrom(res);
                storedFastPairItemList.add(item);
            } catch (InvalidProtocolBufferException e) {
                Log.e("FastPairCacheManager", "storediscovery has error");
            }

        }
        cursor.close();
        return storedFastPairItemList;
    }
}
