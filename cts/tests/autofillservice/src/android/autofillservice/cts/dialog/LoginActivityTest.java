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

package android.autofillservice.cts.dialog;

import static android.autofillservice.cts.activities.FieldsNoPasswordActivity.STRING_ID_CREDIT_CARD_NUMBER;
import static android.autofillservice.cts.activities.FieldsNoPasswordActivity.STRING_ID_EMAILADDRESS;
import static android.autofillservice.cts.activities.FieldsNoPasswordActivity.STRING_ID_PHONE;
import static android.autofillservice.cts.testcore.Helper.ID_PASSWORD;
import static android.autofillservice.cts.testcore.Helper.ID_USERNAME;
import static android.autofillservice.cts.testcore.Helper.ID_USERNAME_LABEL;
import static android.autofillservice.cts.testcore.Helper.NULL_DATASET_ID;
import static android.autofillservice.cts.testcore.Helper.assertFillEventForDatasetSelected;
import static android.autofillservice.cts.testcore.Helper.assertFillEventForDatasetShown;
import static android.autofillservice.cts.testcore.Helper.assertHasFlags;
import static android.autofillservice.cts.testcore.Helper.assertMockImeStatus;
import static android.autofillservice.cts.testcore.Helper.assertNoDeprecatedClientState;
import static android.autofillservice.cts.testcore.Helper.assertNoFlags;
import static android.autofillservice.cts.testcore.Helper.enableFillDialogFeature;
import static android.autofillservice.cts.testcore.Helper.isImeShowing;
import static android.autofillservice.cts.testcore.Helper.setFillDialogHints;
import static android.service.autofill.FillEventHistory.Event.UI_TYPE_DIALOG;
import static android.service.autofill.FillRequest.FLAG_SUPPORTS_FILL_DIALOG;

import static com.google.common.truth.Truth.assertThat;

import android.autofillservice.cts.activities.FieldsNoPasswordActivity;
import android.autofillservice.cts.activities.LoginActivity;
import android.autofillservice.cts.commontests.AutoFillServiceTestCase;
import android.autofillservice.cts.testcore.CannedFillResponse;
import android.autofillservice.cts.testcore.CannedFillResponse.CannedDataset;
import android.autofillservice.cts.testcore.InstrumentedAutoFillService;
import android.autofillservice.cts.testcore.InstrumentedAutoFillService.FillRequest;
import android.content.Intent;
import android.service.autofill.FillEventHistory;
import android.support.test.uiautomator.UiObject2;
import android.view.View;

import org.junit.Test;

import java.util.List;


/**
 * This is the test cases for the fill dialog UI.
 */
public class LoginActivityTest extends AutoFillServiceTestCase.ManualActivityLaunch {

    @Test
    public void testTextView_withoutFillDialog_clickTwice_showIme() throws Exception {
        // Start activity and autofill
        LoginActivity activity = startLoginActivity();
        mUiBot.waitForIdleSync();

        // Click on password field
        mUiBot.selectByRelativeId(ID_PASSWORD);
        // Waits a while
        mUiBot.waitForIdleSync();

        // Click on password field again
        mUiBot.selectByRelativeId(ID_PASSWORD);
        // Waits a while
        mUiBot.waitForIdleSync();

        // Verify IME is shown
        assertThat(isImeShowing(activity.getRootWindowInsets())).isTrue();
    }

    @Test
    public void testTextView_clickTwiceWithShowFillDialog_showIme() throws Exception {
        // Enable feature and test service
        enableFillDialogFeature(sContext);
        enableService();

        // Set response with a dataset > fill dialog should have two buttons
        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("Dropdown Presentation"))
                        .setDialogPresentation(createPresentation("Dialog Presentation"))
                        .build())
                .setDialogHeader(createPresentation("Dialog Header"))
                .setDialogTriggerIds(ID_PASSWORD);
        sReplier.addResponse(builder.build());

        // Start activity and autofill
        LoginActivity activity = startLoginActivity();
        mUiBot.waitForIdleSync();
        sReplier.getNextFillRequest();
        mUiBot.waitForIdleSync();

        // Click on password field to trigger fill dialog
        mUiBot.selectByRelativeId(ID_PASSWORD);
        mUiBot.waitForIdleSync();

        mUiBot.assertFillDialogDatasets("Dialog Presentation");

        // Dismiss fill dialog
        mUiBot.touchOutsideDialog();
        mUiBot.waitForIdle();

        // Click on password field again
        mUiBot.selectByRelativeId(ID_PASSWORD);
        mUiBot.waitForIdleSync();

        // Verify IME is shown
        assertMockImeStatus(activity, true);
    }

    @Test
    public void testShowFillDialog() throws Exception {
        // Enable feature and test service
        enableFillDialogFeature(sContext);
        enableService();

        // Set response with a dataset > fill dialog should have two buttons
        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("Dropdown Presentation"))
                        .setDialogPresentation(createPresentation("Dialog Presentation"))
                        .build())
                .setDialogHeader(createPresentation("Dialog Header"))
                .setDialogTriggerIds(ID_PASSWORD);
        sReplier.addResponse(builder.build());

        // Start activity and autofill
        LoginActivity activity = startLoginActivity();
        mUiBot.waitForIdleSync();

        // Check onFillRequest has the flag: FLAG_SUPPORTS_FILL_DIALOG
        final FillRequest fillRequest = sReplier.getNextFillRequest();
        assertHasFlags(fillRequest.flags, FLAG_SUPPORTS_FILL_DIALOG);
        mUiBot.waitForIdleSync();

        // Click on password field to trigger fill dialog
        mUiBot.selectByRelativeId(ID_PASSWORD);
        mUiBot.waitForIdleSync();

        // Verify IME is not shown
        assertThat(isImeShowing(activity.getRootWindowInsets())).isFalse();

        // Verify the content of fill dialog, and then select dataset in fill dialog
        mUiBot.assertFillDialogHeader("Dialog Header");
        mUiBot.assertFillDialogRejectButton();
        mUiBot.assertFillDialogAcceptButton();
        final UiObject2 picker = mUiBot.assertFillDialogDatasets("Dialog Presentation");

        // Set expected value, then select dataset
        activity.expectAutoFill("dude", "sweet");
        mUiBot.selectDataset(picker, "Dialog Presentation");

        // Check the results.
        activity.assertAutoFilled();

        // Verify events history
        final FillEventHistory selection = InstrumentedAutoFillService.getFillEventHistory(2);
        assertNoDeprecatedClientState(selection);
        final List<FillEventHistory.Event> events = selection.getEvents();
        assertFillEventForDatasetShown(events.get(0), UI_TYPE_DIALOG);
        assertFillEventForDatasetSelected(events.get(1), NULL_DATASET_ID, UI_TYPE_DIALOG);
    }

    @Test
    public void testShowFillDialog_onlyShowOnce() throws Exception {
        // Enable feature and test service
        enableFillDialogFeature(sContext);
        enableService();

        // Set response with a dataset, there are two ids to trigger fill dialog.
        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("Dropdown Presentation"))
                        .setDialogPresentation(createPresentation("Dialog Presentation"))
                        .build())
                .setDialogHeader(createPresentation("Dialog Header"))
                .setDialogTriggerIds(ID_USERNAME, ID_PASSWORD);
        sReplier.addResponse(builder.build());

        // Start activity and autofill
        LoginActivity activity = startLoginActivity();
        mUiBot.waitForIdleSync();

        sReplier.getNextFillRequest();
        mUiBot.waitForIdleSync();

        // Click on password field to trigger fill dialog
        mUiBot.selectByRelativeId(ID_PASSWORD);
        mUiBot.waitForIdleSync();

        mUiBot.assertFillDialogDatasets("Dialog Presentation");

        // Hide fill dialog via touch outside, but ime will appear. to hide IME before next test.
        mUiBot.touchOutsideDialog();
        mUiBot.waitForIdleSync();

        assertMockImeStatus(activity, true);

        activity.hideSoftInput();

        assertMockImeStatus(activity, false);

        // Click on the username field to trigger autofill. Although the username field supports
        // a fill dialog, the fill dialog only shown once, so shows the dropdown UI.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();

        mUiBot.assertNoFillDialog();
        mUiBot.assertDatasets("Dropdown Presentation");

        // Focus on password field to trigger dropdown UI
        // Note: It will click on dropdown UI if click the password field via UiDevice, so just
        // switch focus via activity.
        activity.onPassword(View::requestFocus);
        mUiBot.waitForIdleSync();

        // Set expected value, then select dataset
        activity.expectAutoFill("dude", "sweet");
        mUiBot.selectDataset("Dropdown Presentation");

        // Check the results.
        activity.assertAutoFilled();
    }

    @Test
    public void testShowFillDialog_twoSuggestions_oneButton() throws Exception {
        // Enable feature and test service
        enableFillDialogFeature(sContext);
        enableService();

        // Set response with two datasets > fill dialog should only one button
        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("Dropdown Presentation"))
                        .setDialogPresentation(createPresentation("Dialog Presentation"))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "DUDE")
                        .setField(ID_PASSWORD, "SWEET")
                        .setPresentation(createPresentation("Dropdown Presentation2"))
                        .setDialogPresentation(createPresentation("Dialog Presentation2"))
                        .build())
                .setDialogHeader(createPresentation("Dialog Header"))
                .setDialogTriggerIds(ID_PASSWORD);
        sReplier.addResponse(builder.build());

        // Trigger autofill on the password field and verify fill dialog is shown
        LoginActivity activity = startLoginActivity();
        mUiBot.waitForIdleSync();
        sReplier.getNextFillRequest();
        mUiBot.waitForIdleSync();

        // Click on password field
        mUiBot.selectByRelativeId(ID_PASSWORD);
        mUiBot.waitForIdleSync();

        // Verify IME is not shown
        assertThat(isImeShowing(activity.getRootWindowInsets())).isFalse();
        // Verify the content of fill dialog
        mUiBot.assertFillDialogHeader("Dialog Header");
        mUiBot.assertFillDialogRejectButton();
        mUiBot.assertFillDialogNoAcceptButton();
        final UiObject2 picker =
                mUiBot.assertFillDialogDatasets("Dialog Presentation", "Dialog Presentation2");

        // Set expected value, then select dataset
        activity.expectAutoFill("dude", "sweet");
        mUiBot.selectDataset(picker, "Dialog Presentation");

        // Check the results.
        activity.assertAutoFilled();
    }

    @Test
    public void testShowFillDialog_switchToUnsupportedField_fallbackDropdown() throws Exception {
        // Enable feature and test service
        enableFillDialogFeature(sContext);
        enableService();

        // Set response
        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("Dropdown Presentation"))
                        .setDialogPresentation(createPresentation("Dialog presentation"))
                        .build())
                .setDialogHeader(createPresentation("Dialog Header"))
                .setDialogTriggerIds(ID_PASSWORD);
        sReplier.addResponse(builder.build());

        // Trigger autofill on the password field and verify fill dialog is shown.
        LoginActivity activity = startLoginActivity();
        mUiBot.waitForIdleSync();
        sReplier.getNextFillRequest();
        mUiBot.waitForIdleSync();

        mUiBot.selectByRelativeId(ID_PASSWORD);
        mUiBot.waitForIdleSync();

        mUiBot.assertFillDialogDatasets("Dialog presentation");
        // Verify IME is not shown
        assertThat(isImeShowing(activity.getRootWindowInsets())).isFalse();

        mUiBot.touchOutsideDialog();
        mUiBot.waitForIdle();

        // Click on username field, and verify dropdown UI is shown
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();

        mUiBot.assertDatasets("Dropdown Presentation");
        // Verify IME is shown
        assertThat(isImeShowing(activity.getRootWindowInsets())).isTrue();

        // Verify dropdown UI works
        activity.expectAutoFill("dude", "sweet");
        mUiBot.selectDataset("Dropdown Presentation");

        activity.assertAutoFilled();
    }

    @Test
    public void testFillDialog_fromUnsupportedFieldSwitchToSupported_noFillDialog()
            throws Exception {
        // Enable feature and test service
        enableFillDialogFeature(sContext);
        enableService();

        // Set response
        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("Dropdown Presentation"))
                        .setDialogPresentation(createPresentation("Dialog presentation"))
                        .build())
                .setDialogHeader(createPresentation("Dialog Header"))
                .setDialogTriggerIds(ID_PASSWORD);
        sReplier.addResponse(builder.build());

        // Start activity and autofill
        LoginActivity activity = startLoginActivity();
        mUiBot.waitForIdleSync();
        sReplier.getNextFillRequest();
        mUiBot.waitForIdleSync();

        // Click on username field, and verify dropdown UI is shown.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();

        mUiBot.assertDatasets("Dropdown Presentation");
        // Verify IME is shown
        assertThat(isImeShowing(activity.getRootWindowInsets())).isTrue();

        // Click on password field and verify dropdown is still shown
        // can't use mUiBot.selectByRelativeId(ID_PASSWORD), because will click on dropdown UI
        activity.onPassword(View::requestFocus);
        mUiBot.waitForIdleSync();

        // Verify IME is shown
        assertThat(isImeShowing(activity.getRootWindowInsets())).isTrue();

        // Verify dropdown UI actually works in this case.
        activity.expectAutoFill("dude", "sweet");
        mUiBot.selectDataset("Dropdown Presentation");

        activity.assertAutoFilled();
    }

    @Test
    public void testShowFillDialog_datasetNoDialogPresentation_notShownInDialog() throws Exception {
        // Enable feature and test service
        enableFillDialogFeature(sContext);
        enableService();

        // Set response with one dataset is no dialog presentation
        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("Dropdown Presentation"))
                        .setDialogPresentation(createPresentation("Dialog Presentation"))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "DUDE")
                        .setField(ID_PASSWORD, "SWEET")
                        .setPresentation(createPresentation("Dropdown Presentation2"))
                        .build())
                .setDialogHeader(createPresentation("Dialog Header"))
                .setDialogTriggerIds(ID_PASSWORD);
        sReplier.addResponse(builder.build());

        // Start activity and autofill
        LoginActivity activity = startLoginActivity();
        mUiBot.waitForIdleSync();
        sReplier.getNextFillRequest();
        mUiBot.waitForIdleSync();

        // Click on password field to trigger fill dialog, then select
        mUiBot.selectByRelativeId(ID_PASSWORD);
        mUiBot.waitForIdleSync();
        activity.expectAutoFill("dude", "sweet");
        mUiBot.selectFillDialogDataset("Dialog Presentation");

        activity.assertAutoFilled();
    }

    @Test
    public void testHints_empty_notShowFillDialog() throws Exception {
        testHintsNotMatch("");
    }

    @Test
    public void testHints_notExisting_notShowFillDialog() throws Exception {
        testHintsNotMatch("name:postalAddress:postalCode");
    }

    private void testHintsNotMatch(String hints) throws Exception {
        // Set hints config, enable fill dialog and test service
        setFillDialogHints(sContext, hints);
        enableService();

        // Start activity and autofill is not triggered
        final FieldsNoPasswordActivity activity = startNoPasswordActivity();
        mUiBot.waitForIdleSync();

        sReplier.assertNoUnhandledFillRequests();
        mUiBot.waitForIdleSync();

        // Set response with a dataset
        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(STRING_ID_PHONE, "0123456789")
                        .setField(STRING_ID_CREDIT_CARD_NUMBER, "1234567890")
                        .setField(STRING_ID_EMAILADDRESS, "dude@test")
                        .setPresentation(createPresentation("Dropdown Presentation"))
                        .setDialogPresentation(createPresentation("Dialog Presentation"))
                        .build())
                .setDialogHeader(createPresentation("Dialog Header"))
                .setDialogTriggerIds(ID_USERNAME);
        sReplier.addResponse(builder.build());

        // Click on username field to trigger fill dialog
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();

        // Check onFillRequest is called now, and the fill dialog is not shown
        final FillRequest fillRequest = sReplier.getNextFillRequest();
        assertNoFlags(fillRequest.flags, FLAG_SUPPORTS_FILL_DIALOG);
        mUiBot.assertNoFillDialog();

        // Verify IME is not shown
        assertThat(isImeShowing(activity.getRootWindowInsets())).isTrue();

        // Verify dropdown UI is shown and works
        mUiBot.selectDataset("Dropdown Presentation");
    }

    @Test
    public void testHints_emptyButEnabled_showFillDialog() throws Exception {
        testHintsNotMatchButFeatureEnabled("");
    }

    @Test
    public void testHints_notExistingButEnabled_showFillDialog() throws Exception {
        testHintsNotMatchButFeatureEnabled("name:postalAddress:postalCode");
    }

    // Tests the activity does not have allowed hints but fill dialog is enabled
    private void testHintsNotMatchButFeatureEnabled(String hints) throws Exception {
        // Enable fill dialog feature
        enableFillDialogFeature(sContext);
        // The test step is the same as if there is at least one match in the allowed
        // list when the feature is enabled.
        testHintsConfigMatchAtLeastOneField(hints);
    }

    @Test
    public void testHints_username_showFillDialog() throws Exception {
        testHintsConfigMatchAtLeastOneField("username");
    }

    @Test
    public void testHints_emailAddress_showFillDialog() throws Exception {
        testHintsConfigMatchAtLeastOneField("emailAddress");
    }

    @Test
    public void testHints_usernameAndPhone_showFillDialog() throws Exception {
        testHintsConfigMatchAtLeastOneField("username:phone");
    }

    private void testHintsConfigMatchAtLeastOneField(String hints) throws Exception {
        // Set hints config, enable fill dialog and test service
        setFillDialogHints(sContext, hints);
        enableService();

        // Set response with a dataset
        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(STRING_ID_PHONE, "0123456789")
                        .setField(STRING_ID_CREDIT_CARD_NUMBER, "1234567890")
                        .setField(STRING_ID_EMAILADDRESS, "dude@test")
                        .setPresentation(createPresentation("Dropdown Presentation"))
                        .setDialogPresentation(createPresentation("Dialog Presentation"))
                        .build())
                .setDialogHeader(createPresentation("Dialog Header"))
                .setDialogTriggerIds(ID_USERNAME);
        sReplier.addResponse(builder.build());

        // Start activity and autofill
        final FieldsNoPasswordActivity activity = startNoPasswordActivity();
        mUiBot.waitForIdleSync();

        final FillRequest fillRequest = sReplier.getNextFillRequest();
        assertHasFlags(fillRequest.flags, FLAG_SUPPORTS_FILL_DIALOG);

        // Click on password field to trigger fill dialog
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();

        // Verify IME is not shown
        assertThat(isImeShowing(activity.getRootWindowInsets())).isFalse();

        // Verify fill dialog is shown and works
        mUiBot.selectFillDialogDataset("Dialog Presentation");
    }

    @Test
    public void testHints_passwordAuto_showFillDialog() throws Exception {
        // Set hints and test service
        setFillDialogHints(sContext, "passwordAuto");
        enableService();

        // Set response with a dataset > fill dialog should have two buttons
        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("Dropdown Presentation"))
                        .setDialogPresentation(createPresentation("Dialog Presentation"))
                        .build())
                .setDialogHeader(createPresentation("Dialog Header"))
                .setDialogTriggerIds(ID_PASSWORD);
        sReplier.addResponse(builder.build());

        // Start activity and autofill
        LoginActivity activity = startLoginActivity();
        mUiBot.waitForIdleSync();

        // Check onFillRequest has the flag: FLAG_SUPPORTS_FILL_DIALOG
        final FillRequest fillRequest = sReplier.getNextFillRequest();
        assertHasFlags(fillRequest.flags, FLAG_SUPPORTS_FILL_DIALOG);
        mUiBot.waitForIdleSync();

        // Click on password field to trigger fill dialog
        mUiBot.selectByRelativeId(ID_PASSWORD);
        mUiBot.waitForIdleSync();

        // Verify IME is not shown
        assertThat(isImeShowing(activity.getRootWindowInsets())).isFalse();

        // Verify the content of fill dialog, and then select dataset in fill dialog
        mUiBot.assertFillDialogHeader("Dialog Header");
        mUiBot.assertFillDialogRejectButton();
        mUiBot.assertFillDialogAcceptButton();
        final UiObject2 picker = mUiBot.assertFillDialogDatasets("Dialog Presentation");

        // Set expected value, then select dataset
        activity.expectAutoFill("dude", "sweet");
        mUiBot.selectDataset(picker, "Dialog Presentation");

        // Check the results.
        activity.assertAutoFilled();
    }

    @Test
    public void testCancelFillDialog_showDropdown() throws Exception {
        // Enable feature and test service
        enableFillDialogFeature(sContext);
        enableService();

        // Set response with a dataset > fill dialog should have two buttons
        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("Dropdown Presentation"))
                        .setDialogPresentation(createPresentation("Dialog Presentation"))
                        .build())
                .setDialogHeader(createPresentation("Dialog Header"))
                .setDialogTriggerIds(ID_PASSWORD);
        sReplier.addResponse(builder.build());

        // Start activity and autofill
        LoginActivity activity = startLoginActivity();
        mUiBot.waitForIdleSync();

        sReplier.getNextFillRequest();
        mUiBot.waitForIdleSync();

        // Click on password field to trigger fill dialog
        mUiBot.selectByRelativeId(ID_PASSWORD);
        mUiBot.waitForIdleSync();

        // Verify the fill dialog shown
        mUiBot.assertFillDialogDatasets("Dialog Presentation");

        // Touch outside to cancel the fill dialog, should back to dropdown UI
        mUiBot.touchOutsideDialog();
        mUiBot.waitForIdle();

        mUiBot.assertDatasets("Dropdown Presentation");
        assertMockImeStatus(activity, true);

        // Set expected value, then select dataset
        activity.expectAutoFill("dude", "sweet");
        mUiBot.selectDataset("Dropdown Presentation");

        // Check the results.
        activity.assertAutoFilled();
    }

    @Test
    public void testDismissedFillDialog_showIme() throws Exception {
        // Enable feature and test service
        enableFillDialogFeature(sContext);
        enableService();

        // Set response with a dataset
        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("Dropdown Presentation"))
                        .setDialogPresentation(createPresentation("Dialog Presentation"))
                        .build())
                .setDialogHeader(createPresentation("Dialog Header"))
                .setDialogTriggerIds(ID_PASSWORD);
        sReplier.addResponse(builder.build());

        // Start activity and autofill
        LoginActivity activity = startLoginActivity();
        mUiBot.waitForIdleSync();

        sReplier.getNextFillRequest();
        mUiBot.waitForIdleSync();

        // Click on password field to trigger fill dialog
        mUiBot.selectByRelativeId(ID_PASSWORD);
        mUiBot.waitForIdleSync();

        // Verify the fill dialog shown
        mUiBot.assertFillDialogDatasets("Dialog Presentation");

        // Touch "No thanks" button to dismiss the fill dialog
        mUiBot.clickFillDialogDismiss();
        mUiBot.waitForIdleSync();

        // Verify IME is shown
        assertMockImeStatus(activity, true);
    }

    private FieldsNoPasswordActivity startNoPasswordActivity() throws Exception {
        final Intent intent = new Intent(mContext, FieldsNoPasswordActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        mUiBot.assertShownByRelativeId(ID_USERNAME_LABEL);
        return FieldsNoPasswordActivity.getCurrentActivity();
    }
}
