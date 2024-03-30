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
package android.car.test;

import com.google.common.truth.BooleanSubject;
import com.google.common.truth.Expect;
import com.google.common.truth.StandardSubjectBuilder;

import org.junit.Rule;

// NOTE: it could be a more generic AbstractTruthTestCase that provide similar methods
// for assertThat() / assertWithMessage(), but then we'd need to remove all static import imports
// from classes that indirectly extend it.
/**
 * Base class to make it easier to use {@code Truth} {@link Expect} assertions.
 */
public abstract class AbstractExpectableTestCase {

    @Rule
    public final Expect mExpect = Expect.create();

    protected final StandardSubjectBuilder expectWithMessage(String msg) {
        return mExpect.withMessage(msg);
    }

    protected final StandardSubjectBuilder expectWithMessage(String format, Object...args) {
        return mExpect.withMessage(format, args);
    }

    // NOTE: Expect has dozens of that() methods; the'll be added "on demand".

    protected final BooleanSubject expectThat(Boolean actual) {
        return mExpect.that(actual);
    }
}
