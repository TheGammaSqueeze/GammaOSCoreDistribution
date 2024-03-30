/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.inputmethodservice.InputMethodService.FINISH_INPUT_NO_FALLBACK_CONNECTION;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.WindowInsets.Type.ime;
import static android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE;
import static android.view.inputmethod.InputMethodManager.CLEAR_SHOW_FORCED_FLAG_WHEN_LEAVING;
import static android.view.inputmethod.InputMethodManager.SHOW_FORCED;
import static android.view.inputmethod.cts.util.InputMethodVisibilityVerifier.expectImeInvisible;
import static android.view.inputmethod.cts.util.InputMethodVisibilityVerifier.expectImeVisible;
import static android.view.inputmethod.cts.util.TestUtils.getOnMainSync;
import static android.view.inputmethod.cts.util.TestUtils.runOnMainSync;
import static android.widget.PopupWindow.INPUT_METHOD_NOT_NEEDED;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEventWithKeyValue;
import static com.android.cts.mockime.ImeEventStreamTestUtils.notExpectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.waitForInputViewLayoutStable;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.AlertDialog;
import android.app.Instrumentation;
import android.app.compat.CompatChanges;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.SystemClock;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeInstant;
import android.server.wm.WindowManagerState;
import android.support.test.uiautomator.UiObject2;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethod;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.cts.util.AutoCloseableWrapper;
import android.view.inputmethod.cts.util.EndToEndImeTestBase;
import android.view.inputmethod.cts.util.MockTestActivityUtil;
import android.view.inputmethod.cts.util.RequireImeCompatFlagRule;
import android.view.inputmethod.cts.util.TestActivity;
import android.view.inputmethod.cts.util.TestUtils;
import android.view.inputmethod.cts.util.TestWebView;
import android.view.inputmethod.cts.util.UnlockScreenRule;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.android.cts.mockime.ImeEvent;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeLayoutInfo;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class KeyboardVisibilityControlTest extends EndToEndImeTestBase {
    private static final String TAG = KeyboardVisibilityControlTest.class.getSimpleName();
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);
    private static final long START_INPUT_TIMEOUT = TimeUnit.SECONDS.toMillis(10);
    private static final long NOT_EXPECT_TIMEOUT = TimeUnit.SECONDS.toMillis(1);
    private static final long LAYOUT_STABLE_THRESHOLD = TimeUnit.SECONDS.toMillis(3);

    private static final int NEW_KEYBOARD_HEIGHT = 400;

    @Rule
    public final UnlockScreenRule mUnlockScreenRule = new UnlockScreenRule();
    @Rule
    public final RequireImeCompatFlagRule mRequireImeCompatFlagRule = new RequireImeCompatFlagRule(
            FINISH_INPUT_NO_FALLBACK_CONNECTION, true);

    private static final String TEST_MARKER_PREFIX =
            "android.view.inputmethod.cts.KeyboardVisibilityControlTest";

    private static String getTestMarker() {
        return TEST_MARKER_PREFIX + "/"  + SystemClock.elapsedRealtimeNanos();
    }

    private static Predicate<ImeEvent> editorMatcher(
            @NonNull String eventName, @NonNull String marker) {
        return event -> {
            if (!TextUtils.equals(eventName, event.getEventName())) {
                return false;
            }
            final EditorInfo editorInfo = event.getArguments().getParcelable("editorInfo");
            return TextUtils.equals(marker, editorInfo.privateImeOptions);
        };
    }

    private static Predicate<ImeEvent> showSoftInputMatcher(int requiredFlags) {
        return event -> {
            if (!TextUtils.equals("showSoftInput", event.getEventName())) {
                return false;
            }
            final int flags = event.getArguments().getInt("flags");
            return (flags & requiredFlags) == requiredFlags;
        };
    }

    private static Predicate<ImeEvent> hideSoftInputMatcher() {
        return event -> TextUtils.equals("hideSoftInput", event.getEventName());
    }

    private static Predicate<ImeEvent> onFinishInputViewMatcher(boolean expectedFinishingInput) {
        return event -> {
            if (!TextUtils.equals("onFinishInputView", event.getEventName())) {
                return false;
            }
            final boolean finishingInput = event.getArguments().getBoolean("finishingInput");
            return finishingInput == expectedFinishingInput;
        };
    }

    private Pair<EditText, EditText> launchTestActivity(@NonNull String focusedMarker,
            @NonNull String nonFocusedMarker) {
        final AtomicReference<EditText> focusedEditTextRef = new AtomicReference<>();
        final AtomicReference<EditText> nonFocusedEditTextRef = new AtomicReference<>();
        TestActivity.startSync(activity -> {
            final LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);

            final EditText focusedEditText = new EditText(activity);
            focusedEditText.setHint("focused editText");
            focusedEditText.setPrivateImeOptions(focusedMarker);
            focusedEditText.requestFocus();
            focusedEditTextRef.set(focusedEditText);
            layout.addView(focusedEditText);

            final EditText nonFocusedEditText = new EditText(activity);
            nonFocusedEditText.setPrivateImeOptions(nonFocusedMarker);
            nonFocusedEditText.setHint("target editText");
            nonFocusedEditTextRef.set(nonFocusedEditText);
            layout.addView(nonFocusedEditText);
            return layout;
        });
        return new Pair<>(focusedEditTextRef.get(), nonFocusedEditTextRef.get());
    }

    private EditText launchTestActivity(@NonNull String marker) {
        return launchTestActivity(marker, getTestMarker()).first;
    }

    @Test
    public void testBasicShowHideSoftInput() throws Exception {
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
            notExpectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeInvisible(TIMEOUT);

            assertTrue("isActive() must return true if the View has IME focus",
                    getOnMainSync(() -> imm.isActive(editText)));

            // Test showSoftInput() flow
            assertTrue("showSoftInput must success if the View has IME focus",
                    getOnMainSync(() -> imm.showSoftInput(editText, 0)));

            expectEvent(stream, showSoftInputMatcher(InputMethod.SHOW_EXPLICIT), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.VISIBLE, TIMEOUT);
            expectImeVisible(TIMEOUT);

            // Test hideSoftInputFromWindow() flow
            assertTrue("hideSoftInputFromWindow must success if the View has IME focus",
                    getOnMainSync(() -> imm.hideSoftInputFromWindow(editText.getWindowToken(), 0)));

            expectEvent(stream, hideSoftInputMatcher(), TIMEOUT);
            expectEvent(stream, onFinishInputViewMatcher(false), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.GONE, TIMEOUT);
            expectImeInvisible(TIMEOUT);
        }
    }

    private void verifyHideImeBackPressed(
            boolean appRequestsBackCallback, boolean imeRequestsBackCallback) throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final Context context = instrumentation.getTargetContext();
        final InputMethodManager imm = context.getSystemService(InputMethodManager.class);

        // Whether 'OnBackInvokedCallback' or 'onBackPressed' (legacy back) is used is defined by
        // the 'enableOnBackInvokedCallback' flag in the Application manifest.
        // Registering a callback is only authorized if the flag is set to true. Since the
        // WindowOnBackDispatcher is created at the same time as the ViewRootImpl, for test purpose,
        // we need to manually set the flag on ApplicationInfo before the window is created which
        // happens during the MockIme creation and TestActivity creation.

        try (MockImeSession imeSession = MockImeSession.create(
                instrumentation.getContext(),
                instrumentation.getUiAutomation(),
                new ImeSettings.Builder()
                        .setOnBackCallbackEnabled(imeRequestsBackCallback)
        )) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();

            if (appRequestsBackCallback) {
                context.getApplicationInfo().setEnableOnBackInvokedCallback(true);
            }

            final EditText editText = launchTestActivity(marker);
            final TestActivity testActivity = (TestActivity) editText.getContext();

            if (!appRequestsBackCallback) {
                testActivity.setIgnoreBackKey(true);
            }

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            notExpectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeInvisible(TIMEOUT);

            assertTrue("isActive() must return true if the View has IME focus",
                    getOnMainSync(() -> imm.isActive(editText)));

            // Test showSoftInput() flow
            assertTrue("showSoftInput must success if the View has IME focus",
                    getOnMainSync(() -> imm.showSoftInput(editText, 0)));

            expectEvent(stream, showSoftInputMatcher(InputMethod.SHOW_EXPLICIT), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.VISIBLE, TIMEOUT);
            expectImeVisible(TIMEOUT);

            // Pressing back key, expect soft-keyboard will become invisible.
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            expectEvent(stream, hideSoftInputMatcher(), TIMEOUT);
            expectEvent(stream, onFinishInputViewMatcher(false), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.GONE, TIMEOUT);
            expectImeInvisible(TIMEOUT);
        }
    }

    @Test
    public void testHideImeAfterBackPressed_legacyAppLegacyIme() throws Exception {
        verifyHideImeBackPressed(false/* appRequestsBackCallback */,
                false/* imeRequestsBackCallback */);
    }

    @Test
    public void testHideImeAfterBackPressed_migratedAppLegacyIme() throws Exception {
        verifyHideImeBackPressed(true/* appRequestsBackCallback */,
                false/* imeRequestsBackCallback */);
    }

    @Test
    public void testHideImeAfterBackPressed_migratedAppMigratedIme() throws Exception {
        verifyHideImeBackPressed(true/* appRequestsBackCallback */,
                true/* imeRequestsBackCallback */);
    }

    @Test
    public void testHideImeAfterBackPressed_legacyAppMigratedIme() throws Exception {
        verifyHideImeBackPressed(false/* appRequestsBackCallback */,
                true/* imeRequestsBackCallback */);
    }

    @Test
    public void testShowHideSoftInputShouldBeIgnoredOnNonFocusedView() throws Exception {
        final InputMethodManager imm = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getSystemService(InputMethodManager.class);

        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String focusedMarker = getTestMarker();
            final String nonFocusedMarker = getTestMarker();
            final Pair<EditText, EditText> editTextPair =
                    launchTestActivity(focusedMarker, nonFocusedMarker);
            final EditText nonFocusedEditText = editTextPair.second;

            expectEvent(stream, editorMatcher("onStartInput", focusedMarker), TIMEOUT);

            expectImeInvisible(TIMEOUT);
            assertFalse("isActive() must return false if the View does not have IME focus",
                    getOnMainSync(() -> imm.isActive(nonFocusedEditText)));
            assertFalse("showSoftInput must fail if the View does not have IME focus",
                    getOnMainSync(() -> imm.showSoftInput(nonFocusedEditText, 0)));
            notExpectEvent(stream, showSoftInputMatcher(InputMethod.SHOW_EXPLICIT), TIMEOUT);

            assertFalse("hideSoftInputFromWindow must fail if the View does not have IME focus",
                    getOnMainSync(() -> imm.hideSoftInputFromWindow(
                            nonFocusedEditText.getWindowToken(), 0)));
            notExpectEvent(stream, hideSoftInputMatcher(), TIMEOUT);
            expectImeInvisible(TIMEOUT);
        }
    }

    @Test
    public void testToggleSoftInput() throws Exception {
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
            notExpectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeInvisible(TIMEOUT);

            // Test toggleSoftInputFromWindow() flow
            runOnMainSync(() -> imm.toggleSoftInputFromWindow(editText.getWindowToken(), 0, 0));

            expectEvent(stream.copy(), showSoftInputMatcher(InputMethod.SHOW_EXPLICIT), TIMEOUT);
            expectEvent(stream.copy(), editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeVisible(TIMEOUT);

            // Calling toggleSoftInputFromWindow() must hide the IME.
            runOnMainSync(() -> imm.toggleSoftInputFromWindow(editText.getWindowToken(), 0, 0));

            expectEvent(stream, hideSoftInputMatcher(), TIMEOUT);
            expectEvent(stream, onFinishInputViewMatcher(false), TIMEOUT);
            expectImeInvisible(TIMEOUT);
        }
    }

    @Test
    public void testShowHideKeyboardOnWebView() throws Exception {
        final PackageManager pm =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
        assumeTrue(pm.hasSystemFeature("android.software.webview"));

        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();
            final String marker = getTestMarker();
            final UiObject2 inputTextField = TestWebView.launchTestWebViewActivity(
                    TIMEOUT, marker);
            assertNotNull("Editor must exists on WebView", inputTextField);
            expectImeInvisible(TIMEOUT);

            inputTextField.click();
            expectEvent(stream.copy(), showSoftInputMatcher(InputMethod.SHOW_EXPLICIT), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeVisible(TIMEOUT);
        }
    }

    @Test
    public void testShowHideKeyboardWithInterval() throws Exception {
        final InputMethodManager imm = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getSystemService(InputMethodManager.class);

        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();
            final String marker = getTestMarker();
            final EditText editText = launchTestActivity(marker);
            expectImeInvisible(TIMEOUT);

            runOnMainSync(() -> imm.showSoftInput(editText, 0));
            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeVisible(TIMEOUT);

            // Intervals = 10, 20, 30, ..., 100, 150, 200, ...
            final List<Integer> intervals = new ArrayList<>();
            for (int i = 10; i < 100; i += 10) intervals.add(i);
            for (int i = 100; i < 500; i += 50) intervals.add(i);
            // Regression test for b/221483132.
            // WindowInsetsController tries to clean up IME window after IME hide animation is done.
            // Makes sure that IMM#showSoftInput during IME hide animation cancels the cleanup.
            for (int intervalMillis : intervals) {
                runOnMainSync(() -> imm.hideSoftInputFromWindow(editText.getWindowToken(), 0));
                SystemClock.sleep(intervalMillis);
                runOnMainSync(() -> imm.showSoftInput(editText, 0));
                expectImeVisible(TIMEOUT, "IME should be visible. Interval = " + intervalMillis);
            }
        }
    }

    @Test
    public void testShowSoftInputWithShowForcedFlagWhenAppIsLeaving() throws Exception {
        final InputMethodManager imm = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getSystemService(InputMethodManager.class);

        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            // Launch a simple test activity
            final TestActivity testActivity = TestActivity.startSync(activity -> {
                activity.getWindow().setSoftInputMode(SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                return new LinearLayout(activity);
            });
            assertTrue("test activity should be in resume state",
                    getOnMainSync(testActivity::hasWindowFocus));

            // Launch a test editor activity
            final String marker = getTestMarker();
            final AtomicReference<EditText> ediTextRef = new AtomicReference<>();
            final TestActivity testEditorActivity =
                    TestActivity.startNewTaskSync(activity -> {
                        final LinearLayout layout = new LinearLayout(activity);
                        layout.setOrientation(LinearLayout.VERTICAL);

                        final EditText focusedEditText = new EditText(activity);
                        focusedEditText.setHint("focused editText");
                        focusedEditText.setPrivateImeOptions(marker);
                        focusedEditText.requestFocus();
                        layout.addView(focusedEditText);
                        ediTextRef.set(focusedEditText);
                        return layout;
                    });

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            notExpectEvent(stream, editorMatcher("onStartInputView", marker), NOT_EXPECT_TIMEOUT);
            expectImeInvisible(TIMEOUT);

            assertTrue("isActive() must return true if the View has IME focus",
                    getOnMainSync(() -> imm.isActive(ediTextRef.get())));

            // Test showSoftInput() flow with adding SHOW_FORCED flag
            assertTrue("showSoftInput must success if the View has IME focus",
                    getOnMainSync(() -> imm.showSoftInput(ediTextRef.get(), SHOW_FORCED)));

            expectEvent(stream, showSoftInputMatcher(InputMethod.SHOW_EXPLICIT), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.VISIBLE, TIMEOUT);
            expectImeVisible(TIMEOUT);

            // Finish testEditorActivity
            runOnMainSync(testEditorActivity::finish);

            // Verify soft-keyboard will not visible when enabling the platform compat flag to
            // clear SHOW_FOCED flag. Otherwise, keeping the legacy behavior of SHOW_FOCED that
            // soft-keyboard remains visible if there is no explicit hiding request.
            if (isClearShowForcedFlagEnabled(testActivity.getPackageName())) {
                notExpectEvent(stream, event -> "showSoftInput".equals(event.getEventName()),
                        NOT_EXPECT_TIMEOUT);
                expectImeInvisible(TIMEOUT);
            } else {
                expectEvent(stream, event -> "showSoftInput".equals(event.getEventName()), TIMEOUT);
                expectImeVisible(TIMEOUT);
            }
        }
    }

    @Test
    public void testFloatingImeHideKeyboardAfterBackPressed() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final InputMethodManager imm = instrumentation.getTargetContext().getSystemService(
                InputMethodManager.class);

        // Initial MockIme with floating IME settings.
        try (MockImeSession imeSession = MockImeSession.create(
                instrumentation.getContext(), instrumentation.getUiAutomation(),
                getFloatingImeSettings(Color.BLACK))) {
            final ImeEventStream stream = imeSession.openEventStream();
            final String marker = getTestMarker();
            final EditText editText = launchTestActivity(marker);

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            notExpectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeInvisible(TIMEOUT);

            assertTrue("isActive() must return true if the View has IME focus",
                    getOnMainSync(() -> imm.isActive(editText)));

            // Test showSoftInput() flow
            assertTrue("showSoftInput must success if the View has IME focus",
                    getOnMainSync(() -> imm.showSoftInput(editText, 0)));

            expectEvent(stream, showSoftInputMatcher(InputMethod.SHOW_EXPLICIT), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.VISIBLE, TIMEOUT);
            expectImeVisible(TIMEOUT);

            // Pressing back key, expect soft-keyboard will become invisible.
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            expectEvent(stream, hideSoftInputMatcher(), TIMEOUT);
            expectEvent(stream, onFinishInputViewMatcher(false), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.GONE, TIMEOUT);
            expectImeInvisible(TIMEOUT);
        }
    }

    @Test
    public void testImeVisibilityWhenDismissingDialogWithImeFocused() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (MockImeSession imeSession = MockImeSession.create(
                instrumentation.getContext(),
                instrumentation.getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            // Launch a simple test activity
            final TestActivity testActivity =
                    new TestActivity.Starter()
                            .withWindowingMode(WINDOWING_MODE_FULLSCREEN)
                            .startSync(LinearLayout::new);

            // Launch a dialog
            final String marker = getTestMarker();
            final AtomicReference<EditText> editTextRef = new AtomicReference<>();
            final AtomicReference<AlertDialog> dialogRef = new AtomicReference<>();
            TestUtils.runOnMainSync(() -> {
                final EditText editText = new EditText(testActivity);
                editText.setHint("focused editText");
                editText.setPrivateImeOptions(marker);
                editText.requestFocus();
                final AlertDialog dialog = new AlertDialog.Builder(testActivity)
                        .setView(editText)
                        .create();
                final WindowInsetsController.OnControllableInsetsChangedListener listener =
                        new WindowInsetsController.OnControllableInsetsChangedListener() {
                            @Override
                            public void onControllableInsetsChanged(
                                    @NonNull WindowInsetsController controller, int typeMask) {
                                if ((typeMask & ime()) != 0) {
                                    editText.getWindowInsetsController()
                                            .removeOnControllableInsetsChangedListener(this);
                                    editText.getWindowInsetsController().show(ime());
                                }
                            }
                        };
                dialog.show();
                editText.getWindowInsetsController().addOnControllableInsetsChangedListener(
                        listener);
                editTextRef.set(editText);
                dialogRef.set(dialog);
            });
            TestUtils.waitOnMainUntil(() -> dialogRef.get().isShowing()
                    && editTextRef.get().hasFocus(), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            expectEvent(stream, event -> "showSoftInput".equals(event.getEventName()), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.VISIBLE, TIMEOUT);
            expectImeVisible(TIMEOUT);

            // Hide keyboard and dismiss dialog.
            TestUtils.runOnMainSync(() -> {
                editTextRef.get().getWindowInsetsController().hide(ime());
                dialogRef.get().dismiss();
            });

            // Expect onFinishInput called and keyboard should hide successfully.
            expectEvent(stream, hideSoftInputMatcher(), TIMEOUT);
            expectEvent(stream, onFinishInputViewMatcher(false), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.GONE, TIMEOUT);
            expectImeInvisible(TIMEOUT);

            // onWindowVisibilityChanged event can be out of sequence. Creating
            // a copy of the ImeEventStream to handle this event.
            final ImeEventStream streamCopy = stream.copy();

            // Expect fallback input connection started and keyboard invisible after activity
            // focused unless avoidable keyboard startup is desired,
            // in which case, no fallback will be started.
            if (!isPreventImeStartup()) {
                final ImeEvent onStart = expectEvent(stream,
                        event -> "onStartInput".equals(event.getEventName()), TIMEOUT);
                assertTrue(onStart.getEnterState().hasFallbackInputConnection());
            }
            TestUtils.waitOnMainUntil(testActivity::hasWindowFocus, TIMEOUT);
            expectEventWithKeyValue(streamCopy, "onWindowVisibilityChanged", "visible",
                    View.GONE, TIMEOUT);
            expectImeInvisible(TIMEOUT);
        }
    }

    @AppModeFull(reason = "KeyguardManager is not accessible from instant apps")
    @Test
    public void testImeState_Unspecified_EditorDialogLostFocusAfterUnlocked() throws Exception {
        runImeDoesntReshowAfterKeyguardTest(SOFT_INPUT_STATE_UNSPECIFIED);
    }

    @AppModeFull(reason = "KeyguardManager is not accessible from instant apps")
    @Test
    public void testImeState_Visible_EditorDialogLostFocusAfterUnlocked() throws Exception {
        runImeDoesntReshowAfterKeyguardTest(SOFT_INPUT_STATE_VISIBLE);
    }

    @AppModeFull(reason = "KeyguardManager is not accessible from instant apps")
    @Test
    public void testImeState_AlwaysVisible_EditorDialogLostFocusAfterUnlocked() throws Exception {
        runImeDoesntReshowAfterKeyguardTest(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    @AppModeFull(reason = "KeyguardManager is not accessible from instant apps")
    @Test
    public void testImeState_Hidden_EditorDialogLostFocusAfterUnlocked() throws Exception {
        runImeDoesntReshowAfterKeyguardTest(SOFT_INPUT_STATE_HIDDEN);
    }

    @AppModeFull(reason = "KeyguardManager is not accessible from instant apps")
    @Test
    public void testImeState_AlwaysHidden_EditorDialogLostFocusAfterUnlocked() throws Exception {
        runImeDoesntReshowAfterKeyguardTest(SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    private void runImeDoesntReshowAfterKeyguardTest(int softInputState) throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();
            // Launch a simple test activity
            final TestActivity testActivity =
                    new TestActivity.Starter()
                            .withWindowingMode(WINDOWING_MODE_FULLSCREEN)
                            .startSync(LinearLayout::new);

            // Launch a dialog and show keyboard
            final String marker = getTestMarker();
            final AtomicReference<EditText> editTextRef = new AtomicReference<>();
            final AtomicReference<AlertDialog> dialogRef = new AtomicReference<>();
            TestUtils.runOnMainSync(() -> {
                final EditText editText = new EditText(testActivity);
                editText.setHint("focused editText");
                editText.setPrivateImeOptions(marker);
                editText.requestFocus();
                final AlertDialog dialog = new AlertDialog.Builder(testActivity)
                        .setView(editText)
                        .create();
                dialog.getWindow().setSoftInputMode(softInputState);
                // Tracking onFocusChange callback for debugging purpose.
                editText.setOnFocusChangeListener((v, hasFocus) -> {
                    if (Log.isLoggable(TAG, Log.VERBOSE)) {
                        Log.v(TAG, "Editor " + editText + " hasFocus=" + hasFocus, new Throwable());
                    }
                });
                dialog.show();
                editText.getWindowInsetsController().show(ime());
                editTextRef.set(editText);
                dialogRef.set(dialog);
            });

            try (AutoCloseableWrapper<AlertDialog> dialogCloseWrapper = AutoCloseableWrapper.create(
                    dialogRef.get(), dialog -> TestUtils.runOnMainSync(dialog::dismiss))) {
                TestUtils.waitOnMainUntil(() -> dialogRef.get().isShowing()
                        && editTextRef.get().hasFocus(), TIMEOUT);
                expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
                expectEvent(stream, event -> "showSoftInput".equals(event.getEventName()), TIMEOUT);
                // Copy the event stream to verify both events in case expectEvent missed the
                // event verification if the actual event sequence has flipped.
                expectEvent(stream.copy(), editorMatcher("onStartInputView", marker), TIMEOUT);
                expectEventWithKeyValue(stream.copy(), "onWindowVisibilityChanged", "visible",
                        View.VISIBLE, TIMEOUT);
                expectImeVisible(TIMEOUT);

                TestUtils.turnScreenOff();
                // Clear editor focus after screen-off
                TestUtils.runOnMainSync(editTextRef.get()::clearFocus);

                TestUtils.waitOnMainUntil(() -> editTextRef.get().getWindowVisibility() != VISIBLE,
                        TIMEOUT);
                expectEvent(stream, onFinishInputViewMatcher(true), TIMEOUT);
                if (MockImeSession.isFinishInputNoFallbackConnectionEnabled()) {
                    // When IME enabled the new app compat behavior to finish input without fallback
                    // input connection when device interactive state changed,
                    // we expect onFinishInput happens without any additional fallback input
                    // connection started and no showShowSoftInput requested.
                    expectEvent(stream, event -> "onFinishInput".equals(event.getEventName()),
                            TIMEOUT);
                    notExpectEvent(stream, event -> "showSoftInput".equals(event.getEventName()),
                            NOT_EXPECT_TIMEOUT);
                } else {
                    // For legacy IME, the fallback input connection will started after screen-off.
                    expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
                    expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
                    // Expect showSoftInput comes when system notify InsetsController to apply
                    // show IME insets after IME input target updated.
                    expectEvent(stream, event -> "showSoftInput".equals(event.getEventName()),
                            TIMEOUT);
                    notExpectEvent(stream, hideSoftInputMatcher(), NOT_EXPECT_TIMEOUT);
                }

                // Verify IME will invisible after device unlocked
                TestUtils.turnScreenOn();
                TestUtils.unlockScreen();
                // Expect hideSoftInput will called by IMMS when the same window
                // focused since the editText view focus has been cleared.
                TestUtils.waitOnMainUntil(() -> editTextRef.get().hasWindowFocus()
                        && !editTextRef.get().hasFocus(), TIMEOUT);
                expectEvent(stream, hideSoftInputMatcher(), TIMEOUT);
                if (!MockImeSession.isFinishInputNoFallbackConnectionEnabled()) {
                    expectEvent(stream, onFinishInputViewMatcher(false), TIMEOUT);
                }
                expectImeInvisible(TIMEOUT);
            }
        }
    }

    @AppModeFull
    @Test
    public void testImeVisibilityWhenImeTransitionBetweenActivities_Full() throws Exception {
        runImeVisibilityWhenImeTransitionBetweenActivities(false /* instant */);
    }

    @AppModeInstant
    @Test
    public void testImeVisibilityWhenImeTransitionBetweenActivities_Instant() throws Exception {
        runImeVisibilityWhenImeTransitionBetweenActivities(true /* instant */);
    }

    @AppModeFull
    @Test
    public void testImeInvisibleWhenForceStopPkgProcess_Full() throws Exception {
        runImeVisibilityTestWhenForceStopPackage(false /* instant */);
    }

    @AppModeInstant
    @Test
    public void testImeInvisibleWhenForceStopPkgProcess_Instant() throws Exception {
        runImeVisibilityTestWhenForceStopPackage(true /* instant */);
    }

    @Test
    public void testRestoreImeVisibility() throws Exception {
        // TODO(b/226110728): Remove after we can send ime restore signal to DisplayAreaOrganizer.
        assumeFalse(isImeOrganized(DEFAULT_DISPLAY));
        runRestoreImeVisibility(TestSoftInputMode.UNCHANGED_WITH_BACKWARD_NAV, true);
    }

    @Test
    public void testRestoreImeVisibility_noRestoreForAlwaysHidden() throws Exception {
        runRestoreImeVisibility(TestSoftInputMode.ALWAYS_HIDDEN_WITH_BACKWARD_NAV, false);
    }

    @Test
    public void testRestoreImeVisibility_noRestoreForHiddenWithForwardNav() throws Exception {
        runRestoreImeVisibility(TestSoftInputMode.HIDDEN_WITH_FORWARD_NAV, false);
    }

    /**
     * Test case for Bug 225028378.
     *
     * <p>This test ensures that showing a non-ime-focusable {@link PopupWindow} with
     * {@link PopupWindow#INPUT_METHOD_NOT_NEEDED} will be on top of the IME.</p>
     */
    @Test
    public void testNonImeFocusablePopupWindow_onTopOfIme() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();
            final String marker = getTestMarker();
            final AtomicReference<EditText> editorRef = new AtomicReference<>();
            new TestActivity.Starter().withWindowingMode(
                    WINDOWING_MODE_FULLSCREEN).startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setGravity(Gravity.BOTTOM);
                final EditText editText = new EditText(activity);
                editorRef.set(editText);
                editText.setHint("focused editText");
                editText.setPrivateImeOptions(marker);
                editText.requestFocus();
                layout.addView(editText);
                return layout;
            });
            // Show IME.
            runOnMainSync(() -> editorRef.get().getContext().getSystemService(
                    InputMethodManager.class).showSoftInput(editorRef.get(), 0));

            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeVisible(TIMEOUT);

            // Create then show a non-ime-focusable PopupWindow with INPUT_METHOD_NOT_NEEDED.
            try (AutoCloseableWrapper<PopupWindow> popupWindowWrapper = AutoCloseableWrapper.create(
                    TestUtils.getOnMainSync(() -> {
                        final PopupWindow popup = new PopupWindow(editorRef.get().getContext());
                        popup.setInputMethodMode(INPUT_METHOD_NOT_NEEDED);
                        final TextView textView = new TextView(editorRef.get().getContext());
                        textView.setText("Popup");
                        popup.setContentView(textView);
                        popup.setWidth(MATCH_PARENT);
                        popup.setHeight(MATCH_PARENT);
                        // Show the popup window.
                        popup.showAsDropDown(textView);
                        return popup;
                    }), popup -> TestUtils.runOnMainSync(popup::dismiss))
            ) {
                instrumentation.waitForIdleSync();
                // Verify IME became invisible when the non-ime-focusable PopupWindow is shown.
                expectImeInvisible(NOT_EXPECT_TIMEOUT);

                runOnMainSync(() ->popupWindowWrapper.get().dismiss());
                // Verify IME became visible when the non-ime-focusable PopupWindow has dismissed.
                expectImeVisible(TIMEOUT);
            }
        }
    }

    private enum TestSoftInputMode {
        UNCHANGED_WITH_BACKWARD_NAV,
        ALWAYS_HIDDEN_WITH_BACKWARD_NAV,
        HIDDEN_WITH_FORWARD_NAV
    }

    private void runRestoreImeVisibility(TestSoftInputMode mode, boolean expectImeVisible)
            throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final WindowManager wm = instrumentation.getContext().getSystemService(WindowManager.class);
        // As restoring IME visibility behavior is only available when TaskSnapshot mechanism
        // enabled, skip the test when TaskSnapshot is not supported.
        assumeTrue("Restoring IME visibility not available when TaskSnapshot unsupported",
                wm.isTaskSnapshotSupported());

        try (MockImeSession imeSession = MockImeSession.create(
                instrumentation.getContext(), instrumentation.getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();
            final String markerForActivity1 = getTestMarker();
            final AtomicReference<EditText> editTextRef = new AtomicReference<>();
            // Launch a test activity with focusing editText to show keyboard
            new TestActivity.Starter().withWindowingMode(
                    WINDOWING_MODE_FULLSCREEN).startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                final EditText editText = new EditText(activity);
                editTextRef.set(editText);
                editText.setHint("focused editText");
                editText.setPrivateImeOptions(markerForActivity1);
                editText.requestFocus();
                layout.addView(editText);
                activity.getWindow().getDecorView().getWindowInsetsController().show(ime());
                if (mode == TestSoftInputMode.ALWAYS_HIDDEN_WITH_BACKWARD_NAV) {
                    activity.getWindow().setSoftInputMode(SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                }
                return layout;
            });

            expectEvent(stream, editorMatcher("onStartInput", markerForActivity1), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", markerForActivity1), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.VISIBLE, TIMEOUT);
            expectImeVisible(TIMEOUT);

            // Launch another app task activity to hide keyboard
            new TestActivity.Starter().asNewTask().withWindowingMode(
                    WINDOWING_MODE_FULLSCREEN).startSync(activity -> {
                activity.getWindow().setSoftInputMode(SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                return new LinearLayout(activity);
            });
            expectEvent(stream, hideSoftInputMatcher(), TIMEOUT);
            expectEvent(stream, onFinishInputViewMatcher(false), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.GONE, TIMEOUT);
            expectImeInvisible(TIMEOUT);

            if (mode == TestSoftInputMode.HIDDEN_WITH_FORWARD_NAV) {
                // Start new TestActivity on the same task with STATE_HIDDEN softInputMode.
                final String markerForActivity2 = getTestMarker();
                new TestActivity.Starter().asSameTaskAndClearTop().withWindowingMode(
                        WINDOWING_MODE_FULLSCREEN).startSync(activity -> {
                    final LinearLayout layout = new LinearLayout(activity);
                    final EditText editText = new EditText(activity);
                    editText.setHint("focused editText");
                    editText.setPrivateImeOptions(markerForActivity2);
                    editText.requestFocus();
                    layout.addView(editText);
                    activity.getWindow().setSoftInputMode(SOFT_INPUT_STATE_HIDDEN);
                    return layout;
                });
                expectEvent(stream, editorMatcher("onStartInput", markerForActivity2), TIMEOUT);
            } else {
                // Press back key to back to the first test activity
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
                expectEvent(stream, editorMatcher("onStartInput", markerForActivity1), TIMEOUT);
            }

            // Expect the IME visibility according to expectImeVisible
            // The expected result could be:
            //  1) The system can restore the IME visibility to show IME up when navigated back to
            //     the original app task, even the IME is hidden when switching to the next task.
            //  2) The system won't restore the IME visibility in some softInputMode cases.
            if (expectImeVisible) {
                expectImeVisible(TIMEOUT);
            } else {
                expectImeInvisible(TIMEOUT);
            }
        }
    }

    private void runImeVisibilityWhenImeTransitionBetweenActivities(boolean instant)
            throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder()
                        .setInputViewHeight(NEW_KEYBOARD_HEIGHT)
                        .setDrawsBehindNavBar(true))) {
            final ImeEventStream stream = imeSession.openEventStream();
            final String marker = getTestMarker();

            AtomicReference<EditText> editTextRef = new AtomicReference<>();
            // Launch test activity with focusing editor
            final TestActivity testActivity =
                    new TestActivity.Starter().withWindowingMode(
                            WINDOWING_MODE_FULLSCREEN).startSync(activity -> {
                        final LinearLayout layout = new LinearLayout(activity);
                        layout.setOrientation(LinearLayout.VERTICAL);
                        layout.setGravity(Gravity.BOTTOM);
                        final EditText editText = new EditText(activity);
                        editTextRef.set(editText);
                        editText.setHint("focused editText");
                        editText.setPrivateImeOptions(marker);
                        editText.requestFocus();
                        layout.addView(editText);
                        activity.getWindow().getDecorView().setFitsSystemWindows(true);
                        activity.getWindow().getDecorView().getWindowInsetsController().show(ime());
                        return layout;
                    });
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            expectEvent(stream, event -> "showSoftInput".equals(event.getEventName()), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.VISIBLE, TIMEOUT);
            expectImeVisible(TIMEOUT);

            // Launch another test activity from another process with popup dialog.
            MockTestActivityUtil.launchSync(instant, TIMEOUT,
                    Map.of(MockTestActivityUtil.EXTRA_KEY_SHOW_DIALOG, "true"));
            BySelector dialogSelector = By.clazz(AlertDialog.class).depth(0);
            UiDevice uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            assertNotNull(uiDevice.wait(Until.hasObject(dialogSelector), TIMEOUT));

            // Dismiss dialog and back to original test activity
            MockTestActivityUtil.sendBroadcastAction(MockTestActivityUtil.EXTRA_DISMISS_DIALOG);

            final CountDownLatch imeVisibilityUpdateLatch = new CountDownLatch(1);
            AtomicReference<Boolean> imeInsetsVisible = new AtomicReference<>();
            TestUtils.runOnMainSync(
                    () -> testActivity.getWindow().getDecorView().setOnApplyWindowInsetsListener(
                            (v, insets) -> {
                                if (insets.hasInsets()) {
                                    imeInsetsVisible.set(insets.isVisible(WindowInsets.Type.ime()));
                                    imeVisibilityUpdateLatch.countDown();
                                }
                                return v.onApplyWindowInsets(insets);
                            }));
            // Verify keyboard visibility should aligned with IME insets visibility.
            TestUtils.waitOnMainUntil(
                    () -> testActivity.getWindow().getDecorView().getVisibility() == VISIBLE
                            && testActivity.getWindow().getDecorView().hasWindowFocus(), TIMEOUT);
            assertTrue("Waiting for onApplyWindowInsets timed out",
                    imeVisibilityUpdateLatch.await(5, TimeUnit.SECONDS));
            // Wait for layout being stable in case insets visibility might not align with the
            // input view visibility.
            waitForInputViewLayoutStable(stream, LAYOUT_STABLE_THRESHOLD);

            if (imeInsetsVisible.get()) {
                expectImeVisible(TIMEOUT);
            } else {
                expectImeInvisible(TIMEOUT);
            }
        }
    }

    private void runImeVisibilityTestWhenForceStopPackage(boolean instant) throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();
            final String marker = getTestMarker();

            // Make sure that MockIme isn't shown in the initial state.
            final ImeLayoutInfo lastLayout =
                    waitForInputViewLayoutStable(stream, LAYOUT_STABLE_THRESHOLD);
            assertNull(lastLayout);
            expectImeInvisible(TIMEOUT);
            // Flush all the events happened before launching the test Activity.
            stream.skipAll();

            // Launch test activity with focusing an editor from remote process and expect the
            // IME is visible.
            try (AutoCloseable closable = MockTestActivityUtil.launchSync(
                    instant, TIMEOUT,
                    Map.of(MockTestActivityUtil.EXTRA_KEY_PRIVATE_IME_OPTIONS, marker))) {
                expectEvent(stream, editorMatcher("onStartInput", marker), START_INPUT_TIMEOUT);
                expectImeInvisible(TIMEOUT);

                // Request showSoftInput, expect the request is valid and soft-keyboard visible.
                MockTestActivityUtil.sendBroadcastAction(
                        MockTestActivityUtil.EXTRA_SHOW_SOFT_INPUT);
                expectEvent(stream, event -> "showSoftInput".equals(event.getEventName()), TIMEOUT);
                expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
                expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                        View.VISIBLE, TIMEOUT);
                expectImeVisible(TIMEOUT);

                // Force stop test app package, and then expect IME should be invisible after the
                // remote process stopped by forceStopPackage.
                MockTestActivityUtil.forceStopPackage();
                expectEvent(stream, onFinishInputViewMatcher(false), TIMEOUT);
                expectImeInvisible(TIMEOUT);
            }
        }
    }

    private static ImeSettings.Builder getFloatingImeSettings(@ColorInt int navigationBarColor) {
        final ImeSettings.Builder builder = new ImeSettings.Builder();
        builder.setWindowFlags(0, FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        // As documented, Window#setNavigationBarColor() is actually ignored when the IME window
        // does not have FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS.  We are calling setNavigationBarColor()
        // to ensure it.
        builder.setNavigationBarColor(navigationBarColor);
        return builder;
    }

    /**
     * Whether enabling a compatibility flag to clear {@link InputMethodManager#SHOW_FORCED} flag
     * for the given {@code packageName} of the app when it's leaving.
     *
     * @return {@code true} if the compatibility flag is enabled.
     */
    private static boolean isClearShowForcedFlagEnabled(String packageName) {
        AtomicBoolean result = new AtomicBoolean();
        runWithShellPermissionIdentity(() -> result.set(
                CompatChanges.isChangeEnabled(CLEAR_SHOW_FORCED_FLAG_WHEN_LEAVING, packageName,
                        UserHandle.CURRENT)));
        return result.get();
    }

    /** Whether the IME DisplayArea is organized by WM Shell. */
    private static boolean isImeOrganized(int displayId) {
        final WindowManagerState wmState = new WindowManagerState();
        wmState.computeState();
        WindowManagerState.DisplayArea imeContainer =  wmState.getImeContainer(displayId);
        assertNotNull("ImeContainer not found for display id: " + displayId, imeContainer);
        return imeContainer.isOrganized();
    }
}
