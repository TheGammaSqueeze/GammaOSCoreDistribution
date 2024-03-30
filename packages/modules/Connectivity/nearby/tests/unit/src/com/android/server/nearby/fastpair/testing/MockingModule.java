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
import com.android.server.nearby.common.locator.Module;


import org.mockito.Mockito;

/** Module for tests that just provides mocks for anything that's requested of it. */
public class MockingModule extends Module {

    @Override
    public void configure(Context context, Class<?> type, Locator locator) {
        configureMock(type, locator);
    }

    private <T> void configureMock(Class<T> type, Locator locator) {
        T mock = Mockito.mock(type);
        locator.bind(type, mock);
    }
}
