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

package android.webkit.cts;

import android.content.Context;
import android.os.Looper;
import android.test.InstrumentationTestCase;

import androidx.test.platform.app.InstrumentationRegistry;

import junit.framework.Assert;
import junit.framework.AssertionFailedError;

import java.io.IOException;

/**
 * Test various scenarios of using {@link TestProcessService} and {@link TestProcessClient}
 * framework to run tests cases in freshly created test processes.
 */
public class TestProcessClientTest extends InstrumentationTestCase {

    static class TestRunningOnUiThread extends TestProcessClient.UiThreadTestRunnable {
        @Override
        protected void runOnUiThread(Context ctx) throws Throwable {
            Assert.assertTrue(
                    "Test is not running on the main thread",
                    Looper.getMainLooper().isCurrentThread());
        }
    }

    static class TestRunningOnDefaultThread extends TestProcessClient.TestRunnable {
        private static Looper sLooper;

        @Override
        public void run(Context ctx) throws Throwable {
            Assert.assertFalse(
                    "Default thread should be different from the main thread",
                    Looper.getMainLooper().isCurrentThread());
            if (sLooper == null) {
                sLooper = Looper.myLooper();
                Assert.assertNotNull("The default thread should have a looper", sLooper);
            } else {
                Assert.assertTrue(
                        "Test cases should run on the same thread", sLooper.isCurrentThread());
            }
        }
    }

    public void testRunDifferentRunnables() throws Throwable {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        try (TestProcessClient process = TestProcessClient.createProcessA(context)) {
            process.run(TestRunningOnUiThread.class);
            process.run(TestRunningOnDefaultThread.class);
            process.run(TestRunningOnDefaultThread.class);
        }
    }

    static class TestNullPointerException extends TestProcessClient.TestRunnable {
        @Override
        public void run(Context ctx) throws Throwable {
            throw new NullPointerException("Test NullPointerException, should be caught");
        }
    }

    /**
     * Test throwing an exception that is handled by the Parcel class: {@link
     * Parcel#writeException(java.lang.Exception)}.
     */
    public void testThrowingNullPointerException() throws Throwable {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        try (TestProcessClient process = TestProcessClient.createProcessA(context)) {
            process.run(TestNullPointerException.class);
            fail("A NullPointerException is expected to be thrown");
        } catch (NullPointerException e) {

        }
    }

    static class TestIOException extends TestProcessClient.TestRunnable {
        @Override
        public void run(Context ctx) throws Throwable {
            throw new IOException("Test IOException, should be caught");
        }
    }

    /**
     * Test throwing an exception that is not handled by the Parcel class: {@link
     * Parcel#writeException(java.lang.Exception)}.
     */
    public void testThrowingIOException() throws Throwable {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        try (TestProcessClient process = TestProcessClient.createProcessA(context)) {
            process.run(TestIOException.class);
            fail("An IOException is expected to be thrown");
        } catch (IOException e) {
        }
    }

    static class TestFailedAssertion extends TestProcessClient.TestRunnable {
        @Override
        public void run(Context ctx) throws Throwable {
            fail("This assertion should be caught");
        }
    }

    /**
     * Test that junit assertions failures are propagated as expected.
     */
    public void testFailedAssertion() throws Throwable {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        try (TestProcessClient process = TestProcessClient.createProcessA(context)) {
            process.run(TestFailedAssertion.class);
            fail("An AssertionFailedError is expected to be thrown");
        } catch (AssertionFailedError e) {
            Assert.assertEquals("This assertion should be caught", e.getMessage());
        }
    }
}
