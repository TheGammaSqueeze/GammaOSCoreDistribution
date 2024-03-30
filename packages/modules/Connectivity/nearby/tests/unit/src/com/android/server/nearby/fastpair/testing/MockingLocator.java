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

package src.com.android.server.nearby.fastpair.testing;

import android.content.Context;

import com.android.server.nearby.common.locator.Locator;
import com.android.server.nearby.common.locator.LocatorContextWrapper;

/** A locator for tests that, by default, installs mocks for everything that's requested of it. */
public class MockingLocator extends Locator {
    private final LocatorContextWrapper mLocatorContextWrapper;

    /**
     * Creates a MockingLocator with the explicit bindings already configured on the given locator.
     */
    public static MockingLocator withBindings(Context context, Locator locator) {
        Locator mockingLocator = new Locator(context);
        mockingLocator.bind(new MockingModule());
        locator.attachParent(mockingLocator);
        return new MockingLocator(context, locator);
    }

    /** Creates a MockingLocator with no explicit bindings. */
    public static MockingLocator withMocksOnly(Context context) {
        return withBindings(context, new Locator(context));
    }

    @SuppressWarnings("nullness") // due to passing in this before initialized.
    private MockingLocator(Context context, Locator locator) {
        super(context, locator);
        this.mLocatorContextWrapper = new LocatorContextWrapper(context, this);
    }

    /** Returns a LocatorContextWrapper with this Locator attached. */
    public LocatorContextWrapper getContextForTest() {
        return mLocatorContextWrapper;
    }
}
