/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.libraries.testing.deviceshadower.internal.common;

import android.database.Cursor;

import org.robolectric.fakes.RoboCursor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Simulate Sqlite database for Android content provider.
 */
public class ContentDatabase {

    private final List<String> mColumnNames;
    private final List<List<Object>> mData;

    public ContentDatabase(String... names) {
        mColumnNames = Arrays.asList(names);
        mData = new ArrayList<>();
    }

    public void addData(Object... items) {
        mData.add(Arrays.asList(items));
    }

    public Cursor getCursor() {
        RoboCursor cursor = new RoboCursor();
        cursor.setColumnNames(mColumnNames);
        Object[][] dataArr = new Object[mData.size()][mColumnNames.size()];
        for (int i = 0; i < mData.size(); i++) {
            dataArr[i] = new Object[mColumnNames.size()];
            mData.get(i).toArray(dataArr[i]);
        }
        cursor.setResults(dataArr);
        return cursor;
    }
}
