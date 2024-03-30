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

package android.photopicker.cts.util;

import static com.google.common.truth.Truth.assertWithMessage;

import android.text.format.DateUtils;

import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;

import java.util.ArrayList;
import java.util.List;

/**
 * Photo Picker Utility methods for finding UI elements.
 */
public class PhotoPickerUiUtils {
    public static final long SHORT_TIMEOUT = 5 * DateUtils.SECOND_IN_MILLIS;

    private static final long TIMEOUT = 30 * DateUtils.SECOND_IN_MILLIS;
    public static final String REGEX_PACKAGE_NAME =
            "com(.google)?.android.providers.media(.module)?";

    /**
     * Get the list of items from the photo grid list.
     * @param itemCount if the itemCount is -1, return all matching items. Otherwise, return the
     *                  item list that its size is not greater than the itemCount.
     * @throws Exception
     */
    public static List<UiObject> findItemList(int itemCount) throws Exception {
        final List<UiObject> itemList = new ArrayList<>();
        final UiSelector gridList = new UiSelector().className(
                "androidx.recyclerview.widget.RecyclerView").resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/picker_tab_recyclerview");

        // Wait for the first item to appear
        assertWithMessage("Timed out while waiting for first item to appear")
                .that(new UiObject(gridList.childSelector(new UiSelector())).waitForExists(TIMEOUT))
                .isTrue();

        final UiSelector itemSelector = new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/icon_thumbnail");
        final UiScrollable grid = new UiScrollable(gridList);
        final int childCount = grid.getChildCount();
        final int count = itemCount == -1 ? childCount : itemCount;

        for (int i = 0; i < childCount; i++) {
            final UiObject item = grid.getChildByInstance(itemSelector, i);
            if (item.exists()) {
                itemList.add(item);
            }
            if (itemList.size() == count) {
                break;
            }
        }
        return itemList;
    }

    public static UiObject findPreviewAddButton() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/preview_add_button"));
    }

    public static UiObject findPreviewAddOrSelectButton() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/preview_add_or_select_button"));
    }

    public static UiObject findAddButton() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/button_add"));
    }

    public static UiObject findProfileButton() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/profile_button"));
    }
}
