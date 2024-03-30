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

package android.view.inputmethod.cts;

import static android.view.inputmethod.cts.util.TestUtils.getOnMainSync;
import static android.view.inputmethod.cts.util.TestUtils.runOnMainSync;

import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectBindInput;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectCommand;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.platform.test.annotations.LargeTest;
import android.system.Os;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.SurroundingText;
import android.view.inputmethod.cts.util.EndToEndImeTestBase;
import android.view.inputmethod.cts.util.HandlerInputConnection;
import android.view.inputmethod.cts.util.TestActivity;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.cts.mockime.ImeCommand;
import com.android.cts.mockime.ImeEvent;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests the thread-affinity in {@link InputConnection} callbacks provided by
 * {@link InputConnection#getHandler()}.
 *
 * <p>TODO: Add more tests.</p>
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class InputConnectionHandlerTest extends EndToEndImeTestBase {
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);

    /**
     * The value used in android.inputmethodservice.RemoteInputConnection#MAX_WAIT_TIME_MILLIS.
     *
     * <p>Although this is not a strictly-enforced timeout for all the Android devices, hopefully
     * it'd be acceptable to assume that IMEs can receive result within 2 second even on slower
     * devices.</p>
     *
     * <p>TODO: Consider making this as a test API.</p>
     */
    private static final long TIMEOUT_IN_REMOTE_INPUT_CONNECTION =
            TimeUnit.MILLISECONDS.toMillis(2000);

    private static final int TEST_VIEW_HEIGHT = 10;

    private static final String TEST_MARKER_PREFIX =
            "android.view.inputmethod.cts.InputConnectionHandlerTest";

    private static String getTestMarker() {
        return TEST_MARKER_PREFIX + "/"  + SystemClock.elapsedRealtimeNanos();
    }

    private static final class InputConnectionHandlingThread extends HandlerThread
            implements AutoCloseable {

        private final Handler mHandler;

        InputConnectionHandlingThread() {
            super("IC-callback");
            start();
            mHandler = Handler.createAsync(getLooper());
        }

        @NonNull
        Handler getHandler() {
            return mHandler;
        }

        @Override
        public void close() {
            quitSafely();
            try {
                join(TIMEOUT);
            } catch (InterruptedException e) {
                fail("Failed to stop the thread: " + e);
            }
        }
    }

    /**
     * A mostly-minimum implementation of {@link View} that can be used to test custom
     * implementations of {@link View#onCreateInputConnection(EditorInfo)}.
     */
    static class TestEditor extends View {
        TestEditor(@NonNull Context context) {
            super(context);
            setBackgroundColor(Color.YELLOW);
            setFocusableInTouchMode(true);
            setFocusable(true);
            setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, TEST_VIEW_HEIGHT));
        }
    }

    /**
     * Test {@link InputConnection#commitText(CharSequence, int)} respects
     * {@link InputConnection#getHandler()}.
     */
    @Test
    public void testCommitText() throws Exception {
        try (InputConnectionHandlingThread thread = new InputConnectionHandlingThread();
             MockImeSession imeSession = MockImeSession.create(
                     InstrumentationRegistry.getInstrumentation().getContext(),
                     InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                     new ImeSettings.Builder())) {

            final AtomicInteger callingThreadId = new AtomicInteger(0);
            final CountDownLatch latch = new CountDownLatch(1);

            final class MyInputConnection extends HandlerInputConnection {
                MyInputConnection() {
                    super(thread.getHandler());
                }

                @Override
                public boolean commitText(CharSequence text, int newCursorPosition) {
                    callingThreadId.set(Os.gettid());
                    latch.countDown();
                    return super.commitText(text, newCursorPosition);
                }
            }

            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();

            TestActivity.startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);

                // Just to be conservative, we explicitly check MockImeSession#isActive() here when
                // injecting our custom InputConnection implementation.
                final TestEditor testEditor = new TestEditor(activity) {
                    @Override
                    public boolean onCheckIsTextEditor() {
                        return imeSession.isActive();
                    }

                    @Override
                    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
                        if (imeSession.isActive()) {
                            outAttrs.inputType = InputType.TYPE_CLASS_TEXT;
                            outAttrs.privateImeOptions = marker;
                            return new MyInputConnection();
                        }
                        return null;
                    }
                };

                testEditor.requestFocus();
                layout.addView(testEditor);
                return layout;
            });

            // Wait until the MockIme gets bound to the TestActivity.
            expectBindInput(stream, Process.myPid(), TIMEOUT);

            // Wait until "onStartInput" gets called for the EditText.
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            final ImeCommand command = imeSession.callCommitText("", 1);
            expectCommand(stream, command, TIMEOUT);

            assertTrue("commitText() must be called", latch.await(TIMEOUT, TimeUnit.MILLISECONDS));

            assertEquals("commitText() must happen on the handler thread",
                    thread.getThreadId(), callingThreadId.get());
        }
    }

    /**
     * Test {@link InputConnection#reportFullscreenMode(boolean)} respects
     * {@link InputConnection#getHandler()}.
     */
    @Test
    public void testReportFullscreenMode() throws Exception {
        try (InputConnectionHandlingThread thread = new InputConnectionHandlingThread();
             MockImeSession imeSession = MockImeSession.create(
                     InstrumentationRegistry.getInstrumentation().getContext(),
                     InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                     new ImeSettings.Builder().setFullscreenModePolicy(
                             ImeSettings.FullscreenModePolicy.FORCE_FULLSCREEN))) {

            final AtomicInteger callingThreadId = new AtomicInteger(0);
            final CountDownLatch latch = new CountDownLatch(1);

            final class MyInputConnection extends HandlerInputConnection {
                MyInputConnection() {
                    super(thread.getHandler());
                }

                @Override
                public boolean reportFullscreenMode(boolean enabled) {
                    callingThreadId.set(Os.gettid());
                    latch.countDown();
                    return true;
                }
            }

            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();

            final AtomicReference<View> testEditorViewRef = new AtomicReference<>();
            TestActivity.startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);

                // Just to be conservative, we explicitly check MockImeSession#isActive() here when
                // injecting our custom InputConnection implementation.
                final TestEditor testEditor = new TestEditor(activity) {
                    @Override
                    public boolean onCheckIsTextEditor() {
                        return imeSession.isActive();
                    }

                    @Override
                    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
                        if (imeSession.isActive()) {
                            outAttrs.inputType = InputType.TYPE_CLASS_TEXT;
                            outAttrs.privateImeOptions = marker;
                            return new MyInputConnection();
                        }
                        return null;
                    }
                };

                testEditor.requestFocus();
                testEditorViewRef.set(testEditor);
                layout.addView(testEditor);
                return layout;
            });

            // Wait until the MockIme gets bound to the TestActivity.
            expectBindInput(stream, Process.myPid(), TIMEOUT);

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            assertFalse("InputMethodManager#isFullscreenMode() must return false",
                    getOnMainSync(() -> InstrumentationRegistry.getInstrumentation().getContext()
                            .getSystemService(InputMethodManager.class).isFullscreenMode()));

            // In order to have an IME be shown in the fullscreen mode,
            // SOFT_INPUT_STATE_ALWAYS_VISIBLE is insufficient.  An explicit API call is necessary.
            runOnMainSync(() -> {
                final View editor = testEditorViewRef.get();
                editor.getContext().getSystemService(InputMethodManager.class)
                        .showSoftInput(editor, 0);
            });

            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);

            assertTrue("reportFullscreenMode() must be called",
                    latch.await(TIMEOUT, TimeUnit.MILLISECONDS));

            assertEquals("reportFullscreenMode() must happen on the handler thread",
                    thread.getThreadId(), callingThreadId.get());

            assertTrue("InputMethodManager#isFullscreenMode() must return true",
                    getOnMainSync(() -> InstrumentationRegistry.getInstrumentation().getContext()
                            .getSystemService(InputMethodManager.class).isFullscreenMode()));
            assertTrue(expectCommand(stream, imeSession.callVerifyExtractViewNotNull(), TIMEOUT)
                    .getReturnBooleanValue());
        }
    }

    /**
     * A holder of {@link Handler} that is bound to a background {@link Looper} where
     * {@link Throwable} thrown from tasks running there will be just ignored instead of triggering
     * process crashes.
     */
    private static final class ErrorSwallowingHandlerThread implements AutoCloseable {
        @NonNull
        private final Handler mHandler;

        @NonNull
        Handler getHandler() {
            return mHandler;
        }

        @NonNull
        static ErrorSwallowingHandlerThread create() {
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Looper> mLooperRef = new AtomicReference<>();
            new Thread(() -> {
                Looper.prepare();
                mLooperRef.set(Looper.myLooper());
                latch.countDown();

                while (true) {
                    try {
                        Looper.loop();
                        return;
                    } catch (Throwable ignore) {
                    }
                }
            }).start();

            try {
                assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Failed to create a Handler thread");
            }

            final Handler handler = Handler.createAsync(mLooperRef.get());
            return new ErrorSwallowingHandlerThread(handler);
        }

        private ErrorSwallowingHandlerThread(@NonNull Handler handler) {
            mHandler = handler;
        }

        @Override
        public void close() {
            mHandler.getLooper().quitSafely();
            try {
                mHandler.getLooper().getThread().join(TIMEOUT);
            } catch (InterruptedException e) {
                fail("Failed to terminate the thread");
            }
        }
    }

    /**
     * Ensures that {@code event}'s elapse time is less than the given threshold.
     *
     * @param event {@link ImeEvent} to be tested.
     * @param elapseThresholdInMilliSecond threshold in milli sec.
     */
    private static void expectElapseTimeLessThan(@NonNull ImeEvent event,
            long elapseThresholdInMilliSecond) {
        final long elapseTimeInMilli = TimeUnit.NANOSECONDS.toMillis(
                event.getExitTimestamp() - event.getEnterTimestamp());
        if (elapseTimeInMilli > elapseThresholdInMilliSecond) {
            fail(event.getEventName() + " took " + elapseTimeInMilli + " msec,"
                    + " which must be less than " + elapseThresholdInMilliSecond + " msec.");
        }
    }

    /**
     * Test {@link InputConnection#getSurroundingText(int, int, int)} that throws an exception.
     */
    @Test
    public void testExceptionFromGetSurroundingText() throws Exception {
        try (ErrorSwallowingHandlerThread handlerThread = ErrorSwallowingHandlerThread.create();
             MockImeSession imeSession = MockImeSession.create(
                     InstrumentationRegistry.getInstrumentation().getContext(),
                     InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                     new ImeSettings.Builder())) {

            final CountDownLatch latch = new CountDownLatch(1);

            final class MyInputConnection extends HandlerInputConnection {
                MyInputConnection() {
                    super(handlerThread.getHandler());
                }

                @Override
                public SurroundingText getSurroundingText(int beforeLength, int afterLength,
                        int flags) {
                    latch.countDown();
                    throw new RuntimeException("Exception!");
                }

            }

            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();

            TestActivity.startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);

                // Just to be conservative, we explicitly check MockImeSession#isActive() here when
                // injecting our custom InputConnection implementation.
                final TestEditor testEditor = new TestEditor(activity) {
                    @Override
                    public boolean onCheckIsTextEditor() {
                        return imeSession.isActive();
                    }

                    @Override
                    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
                        if (imeSession.isActive()) {
                            outAttrs.inputType = InputType.TYPE_CLASS_TEXT;
                            outAttrs.privateImeOptions = marker;
                            return new MyInputConnection();
                        }
                        return null;
                    }
                };

                testEditor.requestFocus();
                layout.addView(testEditor);
                return layout;
            });

            // Wait until the MockIme gets bound to the TestActivity.
            expectBindInput(stream, Process.myPid(), TIMEOUT);

            // Wait until "onStartInput" gets called for the EditText.
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            final ImeCommand command = imeSession.callGetSurroundingText(1, 1, 0);
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);

            assertTrue("IC#getSurroundingText() must be called",
                    latch.await(TIMEOUT, TimeUnit.MILLISECONDS));

            assertTrue("Exceptions from IC#getSurroundingText() must be interpreted as null.",
                    result.isNullReturnValue());
            expectElapseTimeLessThan(result, TIMEOUT_IN_REMOTE_INPUT_CONNECTION);
        }
    }
}
