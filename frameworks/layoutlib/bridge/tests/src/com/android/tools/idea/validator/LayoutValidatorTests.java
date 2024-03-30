/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.idea.validator;

import com.android.ide.common.rendering.api.SessionParams;
import com.android.layoutlib.bridge.intensive.RenderTestBase;
import com.android.layoutlib.bridge.intensive.setup.ConfigGenerator;
import com.android.layoutlib.bridge.intensive.setup.LayoutLibTestCallback;
import com.android.layoutlib.bridge.intensive.setup.LayoutPullParser;
import com.android.tools.idea.validator.ValidatorData.CompoundFix;
import com.android.tools.idea.validator.ValidatorData.Issue;
import com.android.tools.idea.validator.ValidatorData.Level;
import com.android.tools.idea.validator.ValidatorData.SetViewAttributeFix;

import com.android.tools.idea.validator.ValidatorData.Type;

import org.junit.Test;

import android.view.View;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckPreset;
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityHierarchyCheck;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LayoutValidatorTests extends RenderTestBase {

    private static final float SCALE_X_FOR_NEXUS_5 = 1.0f;
    private static final float SCALE_Y_FOR_NEXUS_5 = 1.0f;

    @Test
    public void testEnsureDebuggingOff() {
        assertFalse(LayoutValidator.shouldSaveCroppedImages());
    }

    @Test
    public void testEnsureObtainCharacterLocation() {
        assertFalse(LayoutValidator.obtainCharacterLocations());
    }

    @Test
    public void testRenderAndVerify() throws Exception {
        LayoutPullParser parser = createParserFromPath("a11y_test1.xml");
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();
        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setConfigGenerator(ConfigGenerator.NEXUS_5)
                .setCallback(layoutLibCallback)
                .disableDecoration()
                .enableLayoutValidation()
                .build();

        renderAndVerify(params, "a11y_test1.png");
    }

    @Test
    public void testValidation() throws Exception {
        render(sBridge, generateParams(), -1, session -> {
            ValidatorResult result = LayoutValidator.validate(
                    ((View) session.getRootViews().get(0).getViewObject()),
                    null,
                    SCALE_X_FOR_NEXUS_5,
                    SCALE_Y_FOR_NEXUS_5);
            assertEquals(30, result.getIssues().size());
            ArrayList<Issue> errorIssues = new ArrayList<>();
            for (Issue issue : result.getIssues()) {
                assertEquals(Type.ACCESSIBILITY, issue.mType);
                if (issue.mLevel == Level.ERROR) {
                    errorIssues.add(issue);
                }
            }

            Issue first = errorIssues.get(0);
            assertEquals("This item may not have a label readable by screen readers.",
                         first.mMsg);
            assertEquals("https://support.google.com/accessibility/android/answer/7158690",
                         first.mHelpfulUrl);
            assertEquals("SpeakableTextPresentCheck", first.mSourceClass);
            assertTrue(first.mFix instanceof SetViewAttributeFix);
            assertEquals("Set this item's android:contentDescription to a meaningful" +
                            " non-empty string or resource reference.",
                    first.mFix.getDescription());

            Issue second = errorIssues.get(1);
            CompoundFix compoundFix = (CompoundFix) second.mFix;
            assertEquals("This item's size is 10dp x 10dp. Consider making this touch target " +
                            "48dp wide and 48dp high or larger.",
                         second.mMsg);
            assertEquals("https://support.google.com/accessibility/android/answer/7101858",
                         second.mHelpfulUrl);
            assertEquals("TouchTargetSizeCheck", second.mSourceClass);
            assertTrue(compoundFix.mFixes.size() == 2);
            assertEquals(
                    "Set this item's android:layout_width to 48dp.",
                    compoundFix.mFixes.get(0).getDescription());
            assertEquals(
                    "Set this item's android:layout_height to 48dp.",
                    compoundFix.mFixes.get(1).getDescription());

            Issue third = errorIssues.get(2);
            assertEquals("The item's text contrast ratio is 1.00. This ratio is based on a text color " +
                            "of #000000 and background color of #000000. Consider increasing this item's" +
                            " text contrast ratio to 4.50 or greater.",
                         third.mMsg);
            assertEquals("https://support.google.com/accessibility/android/answer/7158390",
                         third.mHelpfulUrl);
            assertEquals("TextContrastCheck", third.mSourceClass);
            assertTrue(third.mFix instanceof SetViewAttributeFix);
            assertEquals("Set this item's android:textColor to #757575.",
                    third.mFix.getDescription());
        });
    }

    @Test
    public void testValidationPolicyType() throws Exception {
        try {
            ValidatorData.Policy newPolicy = new ValidatorData.Policy(
                    EnumSet.of(Type.RENDER),
                    EnumSet.of(Level.ERROR, Level.WARNING));
            LayoutValidator.updatePolicy(newPolicy);

            render(sBridge, generateParams(), -1, session -> {
                ValidatorResult result = LayoutValidator.validate(
                        ((View) session.getRootViews().get(0).getViewObject()),
                        null,
                        SCALE_X_FOR_NEXUS_5,
                        SCALE_Y_FOR_NEXUS_5);

                assertEquals(1, result.getIssues().size());
                assertEquals("Hierarchy is not built yet.",
                        result.getIssues().get(0).mMsg);
            });
        } finally {
            LayoutValidator.updatePolicy(LayoutValidator.DEFAULT_POLICY);
        }
    }

    @Test
    public void testValidationPolicyLevel() throws Exception {
        try {
            ValidatorData.Policy newPolicy = new ValidatorData.Policy(
                    EnumSet.of(Type.ACCESSIBILITY, Type.RENDER),
                    EnumSet.of(Level.VERBOSE));
            LayoutValidator.updatePolicy(newPolicy);

            render(sBridge, generateParams(), -1, session -> {
                ValidatorResult result = LayoutValidator.validate(
                        ((View) session.getRootViews().get(0).getViewObject()),
                        null,
                        SCALE_X_FOR_NEXUS_5,
                        SCALE_Y_FOR_NEXUS_5);
                assertEquals(27, result.getIssues().size());
                result.getIssues().forEach(issue ->assertEquals(Level.VERBOSE, issue.mLevel));
            });
        } finally {
            LayoutValidator.updatePolicy(LayoutValidator.DEFAULT_POLICY);
        }
    }

    @Test
    public void testValidationPolicyChecks() throws Exception {
        Set<AccessibilityHierarchyCheck> allChecks =
                AccessibilityCheckPreset.getAccessibilityHierarchyChecksForPreset(
                        AccessibilityCheckPreset.LATEST);
        Set<AccessibilityHierarchyCheck> filtered =allChecks
                .stream()
                .filter(it -> it.getClass().getSimpleName().equals("TextContrastCheck"))
                .collect(Collectors.toSet());
        try {
            ValidatorData.Policy newPolicy = new ValidatorData.Policy(
                    EnumSet.of(Type.ACCESSIBILITY, Type.RENDER),
                    EnumSet.of(Level.ERROR));
            newPolicy.mChecks.addAll(filtered);
            LayoutValidator.updatePolicy(newPolicy);

            render(sBridge, generateParams(), -1, session -> {
                ValidatorResult result = LayoutValidator.validate(
                        ((View) session.getRootViews().get(0).getViewObject()),
                        null,
                        SCALE_X_FOR_NEXUS_5,
                        SCALE_Y_FOR_NEXUS_5);
                assertEquals(1, result.getIssues().size());
                Issue textCheck = result.getIssues().get(0);
                assertEquals("The item's text contrast ratio is 1.00. This ratio is based on a text color " +
                                "of #000000 and background color of #000000. Consider increasing this item's" +
                                " text contrast ratio to 4.50 or greater.",
                        textCheck.mMsg);
                assertEquals("https://support.google.com/accessibility/android/answer/7158390",
                        textCheck.mHelpfulUrl);
                assertEquals("TextContrastCheck", textCheck.mSourceClass);
            });
        } finally {
            LayoutValidator.updatePolicy(LayoutValidator.DEFAULT_POLICY);
        }
    }

    private SessionParams generateParams() throws Exception {
        LayoutPullParser parser = createParserFromPath("a11y_test1.xml");
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();
        return getSessionParamsBuilder()
                .setParser(parser)
                .setConfigGenerator(ConfigGenerator.NEXUS_5)
                .setCallback(layoutLibCallback)
                .disableDecoration()
                .enableLayoutValidation()
                .build();
    }
}
