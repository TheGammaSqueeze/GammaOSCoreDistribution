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

package com.android.bedstead.nene.inputmethods;

import android.view.inputmethod.InputMethodManager;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.annotations.Experimental;

import java.util.Set;
import java.util.stream.Collectors;

/** Test APIs related to input methods. */
@Experimental
public final class InputMethods {
    public static final InputMethods sInstance = new InputMethods();

    private static final InputMethodManager sInputMethodManager =
            TestApis.context().instrumentedContext().getSystemService(InputMethodManager.class);

    private InputMethods() {

    }

    /** Get installed IMEs. */
    public Set<InputMethod> installedInputMethods() {
        return sInputMethodManager.getInputMethodList()
                .stream()
                .map(InputMethod::new)
                .collect(Collectors.toSet());
    }

}
