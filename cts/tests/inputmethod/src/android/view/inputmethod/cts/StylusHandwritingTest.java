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

import static android.provider.Settings.Global.STYLUS_HANDWRITING_ENABLED;

import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectCommand;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.notExpectEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.cts.util.EndToEndImeTestBase;
import android.view.inputmethod.cts.util.NoOpInputConnection;
import android.view.inputmethod.cts.util.TestActivity;
import android.view.inputmethod.cts.util.TestUtils;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.test.filters.FlakyTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CommonTestUtils;
import com.android.compatibility.common.util.SystemUtil;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * IMF and end-to-end Stylus handwriting tests.
 */
public class StylusHandwritingTest extends EndToEndImeTestBase {
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);
    private static final long NOT_EXPECT_TIMEOUT = TimeUnit.SECONDS.toMillis(1);
    private static final int SETTING_VALUE_ON = 1;
    private static final int SETTING_VALUE_OFF = 0;
    private static final String TEST_MARKER_PREFIX =
            "android.view.inputmethod.cts.StylusHandwritingTest";

    private Context mContext;
    private int mHwInitialState;
    private boolean mShouldRestoreInitialHwState;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeFalse(mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_LEANBACK_ONLY));

        mHwInitialState = Settings.Global.getInt(mContext.getContentResolver(),
                STYLUS_HANDWRITING_ENABLED, SETTING_VALUE_OFF);
        if (mHwInitialState != SETTING_VALUE_ON) {
            SystemUtil.runWithShellPermissionIdentity(() -> {
                Settings.Global.putInt(mContext.getContentResolver(),
                        STYLUS_HANDWRITING_ENABLED, SETTING_VALUE_ON);
            }, Manifest.permission.WRITE_SECURE_SETTINGS);
            mShouldRestoreInitialHwState = true;
        }
    }

    @After
    public void tearDown() {
        if (mShouldRestoreInitialHwState) {
            mShouldRestoreInitialHwState = false;
            SystemUtil.runWithShellPermissionIdentity(() -> {
                Settings.Global.putInt(mContext.getContentResolver(),
                        STYLUS_HANDWRITING_ENABLED, mHwInitialState);
            }, Manifest.permission.WRITE_SECURE_SETTINGS);
        }
    }

    @Test
    public void testHandwritingDoesNotStartWhenNoStylusDown() throws Exception {
        final InputMethodManager imm = mContext.getSystemService(InputMethodManager.class);
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            final EditText editText = launchTestActivity(marker);

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            notExpectEvent(
                    stream,
                    editorMatcher("onStartInputView", marker),
                    NOT_EXPECT_TIMEOUT);

            imm.startStylusHandwriting(editText);

            // Handwriting should not start
            notExpectEvent(
                    stream,
                    editorMatcher("onStartStylusHandwriting", marker),
                    NOT_EXPECT_TIMEOUT);

            // Verify Stylus Handwriting window is not shown
            assertFalse(expectCommand(
                    stream, imeSession.callGetStylusHandwritingWindowVisibility(), TIMEOUT)
                    .getReturnBooleanValue());
        }
    }

    @Test
    @ApiTest(apis = {"android.view.inputmethod.InputMethodManager#startStylusHandwriting",
            "android.inputmethodservice.InputMethodService#onPrepareStylusHandwriting",
            "android.inputmethodservice.InputMethodService#onStartStylusHandwriting",
            "android.inputmethodservice.InputMethodService#onFinishStylusHandwriting"})
    public void testHandwritingStartAndFinish() throws Exception {
        final InputMethodManager imm = mContext.getSystemService(InputMethodManager.class);
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            final EditText editText = launchTestActivity(marker);
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            // Touch down with a stylus
            final int x = 10;
            final int y = 10;
            TestUtils.injectStylusDownEvent(editText, x, y);

            notExpectEvent(
                    stream,
                    editorMatcher("onStartInputView", marker),
                    NOT_EXPECT_TIMEOUT);
            imm.startStylusHandwriting(editText);
            // keyboard shouldn't show up.
            notExpectEvent(
                    stream,
                    editorMatcher("onStartInputView", marker),
                    NOT_EXPECT_TIMEOUT);

            // Handwriting should start
            expectEvent(
                    stream,
                    editorMatcher("onPrepareStylusHandwriting", marker),
                    TIMEOUT);
            expectEvent(
                    stream,
                    editorMatcher("onStartStylusHandwriting", marker),
                    TIMEOUT);

            verifyStylusHandwritingWindowIsShown(stream, imeSession);

            // Release the stylus pointer
            TestUtils.injectStylusUpEvent(editText, x, y);

            // Verify calling finishStylusHandwriting() calls onFinishStylusHandwriting().
            imeSession.callFinishStylusHandwriting();
            expectEvent(
                    stream,
                    editorMatcher("onFinishStylusHandwriting", marker),
                    TIMEOUT);
        }
    }

    /**
     * Call {@link InputMethodManager#startStylusHandwriting(View)} and inject Stylus touch events
     * on screen. Make sure {@link InputMethodService#onStylusHandwritingMotionEvent(MotionEvent)}
     * receives those events via Spy window surface.
     * @throws Exception
     */
    @Test
    @ApiTest(apis = {"android.view.inputmethod.InputMethodManager#startStylusHandwriting",
            "android.inputmethodservice.InputMethodService#onStylusMotionEvent",
            "android.inputmethodservice.InputMethodService#onStartStylusHandwriting"})
    public void testHandwritingStylusEvents_onStylusHandwritingMotionEvent() throws Exception {
        testHandwritingStylusEvents(false /* verifyOnInkView */);
    }

    /**
     * Call {@link InputMethodManager#startStylusHandwriting(View)} and inject Stylus touch events
     * on screen. Make sure Inking view receives those events via Spy window surface.
     * @throws Exception
     */
    @Test
    @ApiTest(apis = {"android.view.inputmethod.InputMethodManager#startStylusHandwriting",
            "android.inputmethodservice.InputMethodService#onStylusMotionEvent",
            "android.inputmethodservice.InputMethodService#onStartStylusHandwriting"})
    public void testHandwritingStylusEvents_dispatchToInkView() throws Exception {
        testHandwritingStylusEvents(false /* verifyOnInkView */);
    }

    private void verifyStylusHandwritingWindowIsShown(ImeEventStream stream,
            MockImeSession imeSession) throws InterruptedException, TimeoutException {
        CommonTestUtils.waitUntil("Stylus handwriting window should be shown", TIMEOUT,
                () -> expectCommand(
                        stream, imeSession.callGetStylusHandwritingWindowVisibility(), TIMEOUT)
                .getReturnBooleanValue());
    }

    private void testHandwritingStylusEvents(boolean verifyOnInkView) throws Exception {
        final InputMethodManager imm = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getSystemService(InputMethodManager.class);
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            final EditText editText = launchTestActivity(marker);
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            final List<MotionEvent> injectedEvents = new ArrayList<>();
            // Touch down with a stylus
            final int startX = 10;
            final int startY = 10;
            injectedEvents.add(TestUtils.injectStylusDownEvent(editText, startX, startY));

            notExpectEvent(
                    stream,
                    editorMatcher("onStartInputView", marker),
                    NOT_EXPECT_TIMEOUT);
            imm.startStylusHandwriting(editText);

            // Handwriting should start
            expectEvent(
                    stream,
                    editorMatcher("onStartStylusHandwriting", marker),
                    TIMEOUT);

            verifyStylusHandwritingWindowIsShown(stream, imeSession);

            if (verifyOnInkView) {
                // Verify IME stylus Ink view receives the motion Event.
                assertTrue(expectCommand(
                        stream,
                        imeSession.callSetStylusHandwritingInkView(),
                        TIMEOUT).getReturnBooleanValue());
            }

            final int endX = startX + 500;
            final int endY = startY + 500;
            injectedEvents.addAll(
                    TestUtils.injectStylusMoveEvents(editText, startX, startY, endX, endY, 10));
            injectedEvents.add(TestUtils.injectStylusUpEvent(editText, endX, endY));

            expectEvent(
                    stream, event -> "onStylusMotionEvent".equals(event.getEventName()), TIMEOUT);

            // get Stylus events from Ink view, splitting any batched events.
            final ArrayList<MotionEvent> capturedBatchedEvents = expectCommand(
                    stream, imeSession.callGetStylusHandwritingEvents(), TIMEOUT)
                    .getReturnParcelableArrayListValue();
            assertNotNull(capturedBatchedEvents);
            final ArrayList<MotionEvent> capturedEvents =  new ArrayList<>();
            capturedBatchedEvents.forEach(
                    e -> capturedEvents.addAll(TestUtils.splitBatchedMotionEvent(e)));

            // captured events should be same as injected.
            assertEquals(injectedEvents.size(), capturedEvents.size());

            // Verify MotionEvents as well.
            // Note: we cannot just use equals() since some MotionEvent fields can change after
            // dispatch.
            Iterator<MotionEvent> capturedIt = capturedEvents.iterator();
            Iterator<MotionEvent> injectedIt = injectedEvents.iterator();
            while (injectedIt.hasNext() && capturedIt.hasNext()) {
                MotionEvent injected = injectedIt.next();
                MotionEvent captured = capturedIt.next();
                assertEquals("X should be same for MotionEvent", injected.getX(), captured.getX(),
                        5.0f);
                assertEquals("Y should be same for MotionEvent", injected.getY(), captured.getY(),
                        5.0f);
                assertEquals("Action should be same for MotionEvent",
                        injected.getAction(), captured.getAction());
            }
        }
    }

    @FlakyTest(bugId = 210039666)
    @Test
    /**
     * Inject Stylus events on top of focused editor and verify Handwriting is started and InkWindow
     * is displayed.
     */
    public void testHandwritingEndToEnd() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            final EditText editText = launchTestActivity(marker);

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            notExpectEvent(
                    stream,
                    editorMatcher("onStartInputView", marker),
                    NOT_EXPECT_TIMEOUT);

            final int touchSlop = getTouchSlop();
            final int startX = editText.getWidth() / 2;
            final int startY = editText.getHeight() / 2;
            final int endX = startX + 2 * touchSlop;
            final int endY = startY;
            final int number = 5;
            TestUtils.injectStylusDownEvent(editText, startX, startY);
            TestUtils.injectStylusMoveEvents(editText, startX, startY,
                    endX, endY, number);
            // Handwriting should already be initiated before ACTION_UP.
            // keyboard shouldn't show up.
            notExpectEvent(
                    stream,
                    editorMatcher("onStartInputView", marker),
                    NOT_EXPECT_TIMEOUT);
            // Handwriting should start
            expectEvent(
                    stream,
                    editorMatcher("onStartStylusHandwriting", marker),
                    TIMEOUT);

            // Verify Stylus Handwriting window is shown
            assertTrue(expectCommand(
                    stream, imeSession.callGetStylusHandwritingWindowVisibility(), TIMEOUT)
                            .getReturnBooleanValue());

            TestUtils.injectStylusUpEvent(editText, endX, endY);
        }
    }

    @FlakyTest(bugId = 222840964)
    @Test
    /**
     * Inject Stylus events on top of focused editor and verify Handwriting can be initiated
     * multiple times.
     */
    public void testHandwritingInitMultipleTimes() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            final EditText editText = launchTestActivity(marker);

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            notExpectEvent(
                    stream,
                    editorMatcher("onStartInputView", marker),
                    NOT_EXPECT_TIMEOUT);

            final int touchSlop = getTouchSlop();
            final int startX = editText.getWidth() / 2;
            final int startY = editText.getHeight() / 2;
            final int endX = startX + 2 * touchSlop;
            final int endY = startY;
            final int number = 5;

            // Try to init handwriting for multiple times.
            for (int i = 0; i < 3; ++i) {
                TestUtils.injectStylusDownEvent(editText, startX, startY);
                TestUtils.injectStylusMoveEvents(editText, startX, startY,
                        endX, endY, number);
                // Handwriting should already be initiated before ACTION_UP.
                // keyboard shouldn't show up.
                notExpectEvent(
                        stream,
                        editorMatcher("onStartInputView", marker),
                        NOT_EXPECT_TIMEOUT);
                // Handwriting should start
                expectEvent(
                        stream,
                        editorMatcher("onStartStylusHandwriting", marker),
                        TIMEOUT);


                // Verify Stylus Handwriting window is shown
                assertTrue(expectCommand(
                        stream, imeSession.callGetStylusHandwritingWindowVisibility(), TIMEOUT)
                        .getReturnBooleanValue());

                TestUtils.injectStylusUpEvent(editText, endX, endY);

                imeSession.callFinishStylusHandwriting();
                expectEvent(
                        stream,
                        editorMatcher("onFinishStylusHandwriting", marker),
                        TIMEOUT);
            }
        }
    }

    @Test
    /**
     * Inject stylus events to a focused EditText that disables autoHandwriting.
     * {@link InputMethodManager#startStylusHandwriting(View)} should not be called.
     */
    public void testAutoHandwritingDisabled() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            final EditText editText = launchTestActivity(marker);
            editText.setAutoHandwritingEnabled(false);

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            notExpectEvent(
                    stream,
                    editorMatcher("onStartInputView", marker),
                    NOT_EXPECT_TIMEOUT);

            TestUtils.injectStylusEvents(editText);

            // TODO(215439842): check that keyboard is not shown.
            // Handwriting should not start
            notExpectEvent(
                    stream,
                    editorMatcher("onStartStylusHandwriting", marker),
                    NOT_EXPECT_TIMEOUT);

            // Verify Stylus Handwriting window is not shown
            assertFalse(expectCommand(
                    stream, imeSession.callGetStylusHandwritingWindowVisibility(), TIMEOUT)
                    .getReturnBooleanValue());
        }
    }

    @Test
    /**
     * Inject stylus events out of a focused editor's view bound.
     * {@link InputMethodManager#startStylusHandwriting(View)} should not be called for this editor.
     */
    public void testAutoHandwritingOutOfBound() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            final EditText editText = launchTestActivity(marker);
            editText.setAutoHandwritingEnabled(false);

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            notExpectEvent(
                    stream,
                    editorMatcher("onStartInputView", marker),
                    NOT_EXPECT_TIMEOUT);

            // Inject stylus events out of the editor boundary.
            TestUtils.injectStylusEvents(editText, editText.getWidth() / 2, -50);
            // keyboard shouldn't show up.
            notExpectEvent(
                    stream,
                    editorMatcher("onStartInputView", marker),
                    NOT_EXPECT_TIMEOUT);
            // Handwriting should not start
            notExpectEvent(
                    stream,
                    editorMatcher("onStartStylusHandwriting", marker),
                    NOT_EXPECT_TIMEOUT);

            // Verify Stylus Handwriting window is not shown
            assertFalse(expectCommand(
                    stream, imeSession.callGetStylusHandwritingWindowVisibility(), TIMEOUT)
                    .getReturnBooleanValue());
        }
    }

    @Test
    /**
     * Inject Stylus events on top of an unfocused editor and verify Handwriting is started and
     * InkWindow is displayed.
     */
    public void testHandwriting_unfocusedEditText() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String focusedMarker = getTestMarker();
            final String unfocusedMarker = getTestMarker();
            final Pair<EditText, EditText> editTextPair =
                    launchTestActivity(focusedMarker, unfocusedMarker);
            final EditText unfocusedEditText = editTextPair.second;

            expectEvent(stream, editorMatcher("onStartInput", focusedMarker), TIMEOUT);
            notExpectEvent(
                    stream,
                    editorMatcher("onStartInputView", focusedMarker),
                    NOT_EXPECT_TIMEOUT);

            final int touchSlop = getTouchSlop();
            final int startX = unfocusedEditText.getWidth() / 2;
            final int startY = 2 * touchSlop;
            // (endX, endY) is out of bound to avoid that unfocusedEditText is focused due to the
            // stylus touch.
            final int endX = startX;
            final int endY = unfocusedEditText.getHeight() + 2 * touchSlop;
            final int number = 5;

            TestUtils.injectStylusDownEvent(unfocusedEditText, startX, startY);
            TestUtils.injectStylusMoveEvents(unfocusedEditText, startX, startY,
                    endX, endY, number);
            // Handwriting should already be initiated before ACTION_UP.
            // unfocusedEditor is focused and triggers onStartInput.
            expectEvent(stream, editorMatcher("onStartInput", unfocusedMarker), TIMEOUT);
            // keyboard shouldn't show up.
            notExpectEvent(
                    stream,
                    editorMatcher("onStartInputView", unfocusedMarker),
                    NOT_EXPECT_TIMEOUT);
            // Handwriting should start on the unfocused EditText.
            expectEvent(
                    stream,
                    editorMatcher("onStartStylusHandwriting", unfocusedMarker),
                    TIMEOUT);

            // Verify Stylus Handwriting window is shown
            assertTrue(expectCommand(
                    stream, imeSession.callGetStylusHandwritingWindowVisibility(), TIMEOUT)
                    .getReturnBooleanValue());

            TestUtils.injectStylusUpEvent(unfocusedEditText, endX, endY);
        }
    }

    @Test
    /**
     * Inject Stylus events on top of a focused customized editor and verify Handwriting is started
     * and InkWindow is displayed.
     */
    public void testHandwritingInCustomizedEditor() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            final View editText = launchTestActivityCustomizedEditor(marker);

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            notExpectEvent(
                    stream,
                    editorMatcher("onStartInputView", marker),
                    NOT_EXPECT_TIMEOUT);

            final int touchSlop = getTouchSlop();
            final int startX = editText.getWidth() / 2;
            final int startY = editText.getHeight() / 2;
            final int endX = startX + 2 * touchSlop;
            final int endY = startY + 2 * touchSlop;
            final int number = 5;
            TestUtils.injectStylusDownEvent(editText, startX, startY);
            TestUtils.injectStylusMoveEvents(editText, startX, startY,
                    endX, endY, number);
            // Handwriting should already be initiated before ACTION_UP.
            // keyboard shouldn't show up.
            notExpectEvent(
                    stream,
                    editorMatcher("onStartInputView", marker),
                    NOT_EXPECT_TIMEOUT);
            // Handwriting should start
            expectEvent(
                    stream,
                    editorMatcher("onStartStylusHandwriting", marker),
                    TIMEOUT);

            // Verify Stylus Handwriting window is shown
            assertTrue(expectCommand(
                    stream, imeSession.callGetStylusHandwritingWindowVisibility(), TIMEOUT)
                    .getReturnBooleanValue());

            TestUtils.injectStylusUpEvent(editText, endX, endY);
        }
    }

    @Test
    /**
     * Inject Stylus events on top of an unfocused editor which disabled the autoHandwriting and
     * verify Handwriting is not started and InkWindow is not displayed.
     */
    public void testHandwriting_unfocusedEditText_autoHandwritingDisabled() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String focusedMarker = getTestMarker();
            final String unfocusedMarker = getTestMarker();
            final Pair<EditText, EditText> editTextPair =
                    launchTestActivity(focusedMarker, unfocusedMarker);
            final EditText unfocusedEditText = editTextPair.second;
            unfocusedEditText.setAutoHandwritingEnabled(false);

            expectEvent(stream, editorMatcher("onStartInput", focusedMarker), TIMEOUT);
            notExpectEvent(
                    stream,
                    editorMatcher("onStartInputView", focusedMarker),
                    NOT_EXPECT_TIMEOUT);

            final int touchSlop = getTouchSlop();
            final int startX = 50;
            final int startY = 2 * touchSlop;
            // (endX, endY) is out of bound to avoid that unfocusedEditText is focused due to the
            // stylus touch.
            final int endX = -2 * touchSlop;
            final int endY = 50;
            final int number = 5;
            TestUtils.injectStylusDownEvent(unfocusedEditText, startX, startY);
            TestUtils.injectStylusMoveEvents(unfocusedEditText, startX, startY,
                    endX, endY, number);
            TestUtils.injectStylusUpEvent(unfocusedEditText, endX, endY);

            // unfocusedEditor opts out autoHandwriting, so it won't trigger onStartInput.
            notExpectEvent(stream, editorMatcher("onStartInput", unfocusedMarker), TIMEOUT);
            // keyboard shouldn't show up.
            notExpectEvent(
                    stream,
                    editorMatcher("onStartInputView", unfocusedMarker),
                    NOT_EXPECT_TIMEOUT);
            // Handwriting should not start
            notExpectEvent(
                    stream,
                    editorMatcher("onStartStylusHandwriting", unfocusedMarker),
                    NOT_EXPECT_TIMEOUT);

            // Verify Stylus Handwriting window is not shown
            assertFalse(expectCommand(
                    stream, imeSession.callGetStylusHandwritingWindowVisibility(), TIMEOUT)
                    .getReturnBooleanValue());
        }
    }

    private EditText launchTestActivity(@NonNull String marker) {
        return launchTestActivity(marker, getTestMarker()).first;
    }

    private static String getTestMarker() {
        return TEST_MARKER_PREFIX + "/"  + SystemClock.elapsedRealtimeNanos();
    }

    private static int getTouchSlop() {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        return ViewConfiguration.get(context).getScaledTouchSlop();
    }

    private Pair<EditText, EditText> launchTestActivity(@NonNull String focusedMarker,
            @NonNull String nonFocusedMarker) {
        final AtomicReference<EditText> focusedEditTextRef = new AtomicReference<>();
        final AtomicReference<EditText> nonFocusedEditTextRef = new AtomicReference<>();
        TestActivity.startSync(activity -> {
            final LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);
            // Adding some top padding tests that inject stylus event out of the view boundary.
            layout.setPadding(0, 100, 0, 0);

            final EditText focusedEditText = new EditText(activity);
            focusedEditText.setHint("focused editText");
            focusedEditText.setPrivateImeOptions(focusedMarker);
            focusedEditText.requestFocus();
            focusedEditText.setAutoHandwritingEnabled(true);
            focusedEditTextRef.set(focusedEditText);
            layout.addView(focusedEditText);

            final EditText nonFocusedEditText = new EditText(activity);
            nonFocusedEditText.setPrivateImeOptions(nonFocusedMarker);
            nonFocusedEditText.setHint("target editText");
            nonFocusedEditText.setAutoHandwritingEnabled(true);
            nonFocusedEditTextRef.set(nonFocusedEditText);
            layout.addView(nonFocusedEditText);
            return layout;
        });
        return new Pair<>(focusedEditTextRef.get(), nonFocusedEditTextRef.get());
    }


    private View launchTestActivityCustomizedEditor(@NonNull String marker) {
        final AtomicReference<View> view = new AtomicReference<>();
        TestActivity.startSync(activity -> {
            final LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);
            // Adding some top padding tests that inject stylus event out of the view boundary.
            layout.setPadding(0, 100, 0, 0);

            final View customizedEditor = new View(activity) {
                @Override
                public boolean onCheckIsTextEditor() {
                    return true;
                }

                @Override
                public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
                    outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT;
                    outAttrs.privateImeOptions = marker;
                    return new NoOpInputConnection();
                }

                @Override
                public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    // This View needs a valid size to be focusable.
                    setMeasuredDimension(300, 100);
                }
            };
            customizedEditor.setFocusable(true);
            customizedEditor.setFocusableInTouchMode(true);
            customizedEditor.setAutoHandwritingEnabled(true);
            customizedEditor.requestFocus();
            layout.addView(customizedEditor);

            view.set(customizedEditor);
            return layout;
        });
        return view.get();
    }
}
