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

package android.platform.helpers.features;

import android.support.test.uiautomator.BySelector;

/**
 * An interface which all the page should implement.
 */
public interface Page {

    /**
     * To get page selector used for determining the given page
     *
     * @return an instance of given page selector identifier.
     */
    BySelector getPageTitleSelector();

    /**
     * To get the name of the given page.
     *
     * @return the name of the given page
     */
    default String getPageName() {
        return getClass().getSimpleName();
    }

    /**
     * Action required to open the app or page otherwise it will remain empty.
     */
    default void open() {}
}
