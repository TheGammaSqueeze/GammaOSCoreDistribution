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

package android.graphics.cts;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertWithMessage;

import android.R;
import android.content.Context;
import android.graphics.Color;
import android.provider.Settings;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.ColorInt;
import androidx.core.graphics.ColorUtils;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.FeatureUtil;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SystemPaletteTest {

    private static final boolean DEBUG = true;
    private static final String TAG = "SystemPaletteTest";

    @Test
    public void testShades0and1000() {
        final Context context = getInstrumentation().getTargetContext();
        final int[] leftmostColors = new int[]{
                R.color.system_neutral1_0, R.color.system_neutral2_0, R.color.system_accent1_0,
                R.color.system_accent2_0, R.color.system_accent3_0
        };
        final int[] rightmostColors = new int[]{
                R.color.system_neutral1_1000, R.color.system_neutral2_1000,
                R.color.system_accent1_1000, R.color.system_accent2_1000,
                R.color.system_accent3_1000
        };
        for (int i = 0; i < leftmostColors.length; i++) {
            assertColor(context.getColor(leftmostColors[0]), Color.WHITE);
        }
        for (int i = 0; i < rightmostColors.length; i++) {
            assertColor(context.getColor(rightmostColors[0]), Color.BLACK);
        }
    }

    @Test
    @CddTest(requirements = {"3.8.6/C-1-4,C-1-5,C-1-6"})
    public void testThemeStyles() {
        // THEME_CUSTOMIZATION_OVERLAY_PACKAGES is not available in Wear OS
        // TODO: Remove this check when THEME_CUSTOMIZATION_OVERLAY_PACKAGES is supported in Wear OS
        if (FeatureUtil.isWatch()) {
            return;
        }
        final Context context = getInstrumentation().getTargetContext();
        forEachThemeDefinition((color, style, expectedPalette) -> {
            // Update setting, so system colors will change
            runWithShellPermissionIdentity(() -> {
                Settings.Secure.putString(context.getContentResolver(),
                        Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                        "{\"android.theme.customization.system_palette\":\"" + color
                                + "\",\"android.theme.customization.theme_style\":\"" + style
                                + "\"}");
            });

            final int[] allColors = new int[65];
            new PollingCheck(15_000L, "Invalid tonal palettes for " + color + " " + style) {
                @Override
                protected boolean check() {

                    System.arraycopy(getAllAccent1Colors(context), 0, allColors, 0, 13);
                    System.arraycopy(getAllAccent2Colors(context), 0, allColors, 13, 13);
                    System.arraycopy(getAllAccent3Colors(context), 0, allColors, 26, 13);
                    System.arraycopy(getAllNeutral1Colors(context), 0, allColors, 39, 13);
                    System.arraycopy(getAllNeutral2Colors(context), 0, allColors, 52, 13);

                    if (DEBUG) {
                        final String setting = Settings.Secure
                                .getString(context.getContentResolver(),
                                        Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES);
                        Log.d(TAG, "Expected:\n" + Arrays.toString(expectedPalette)
                                        + "\nActual:\n" + Arrays.toString(allColors)
                                        + "\nSetting:\n" + setting);
                    }

                    return Arrays.equals(allColors, expectedPalette);
                }
            }.run();
        });
    }

    private void forEachThemeDefinition(ThemeEvaluator evaluator) {
        final Context context = getInstrumentation().getTargetContext();
        final XmlPullParser parser = context.getResources()
                .getXml(android.graphics.cts.R.xml.valid_themes);
        try {
            parser.next();
            parser.next();
            parser.require(XmlPullParser.START_TAG, null, "themes");
            while (parser.next() != XmlPullParser.END_TAG) {
                parser.require(XmlPullParser.START_TAG, null, "theme");
                final String color = parser.getAttributeValue(null, "color");
                while (parser.next() != XmlPullParser.END_TAG) {
                    String styleName = parser.getName();
                    parser.next();
                    int[] colors = Arrays.stream(parser.getText().split(","))
                            .mapToInt(s -> Color.parseColor("#" + s))
                            .toArray();
                    parser.next();
                    parser.require(XmlPullParser.END_TAG, null, styleName);
                    evaluator.apply(color, styleName.toUpperCase(), colors);
                }
            }
        } catch (XmlPullParserException | IOException e) {
            throw new RuntimeException("Error parsing xml", e);
        }
    }

    @Test
    public void testColorsMatchExpectedLuminance() {
        final Context context = getInstrumentation().getTargetContext();
        List<int[]> allPalettes = Arrays.asList(getAllAccent1Colors(context),
                getAllAccent2Colors(context), getAllAccent3Colors(context),
                getAllNeutral1Colors(context), getAllNeutral2Colors(context));

        final double[] labColor = new double[3];
        final double[] expectedL = {100, 99, 95, 90, 80, 70, 60, 49, 40, 30, 20, 10, 0};

        for (int[] palette : allPalettes) {
            for (int i = 0; i < palette.length; i++) {
                ColorUtils.colorToLAB(palette[i], labColor);

                // Colors in the same palette should vary mostly in L, decreasing lightness as we
                // move across the palette.
                assertWithMessage("Color " + Integer.toHexString((palette[i]))
                        + " at index " + i + " should have L " + expectedL[i] + " in LAB space.")
                        .that(labColor[0]).isWithin(3).of(expectedL[i]);
            }
        }
    }

    @Test
    public void testContrastRatio() {
        final Context context = getInstrumentation().getTargetContext();

        final List<Pair<Integer, Integer>> atLeast4dot5 = Arrays.asList(new Pair<>(0, 500),
                new Pair<>(50, 600), new Pair<>(100, 600), new Pair<>(200, 700),
                new Pair<>(300, 800), new Pair<>(400, 900), new Pair<>(500, 1000));
        final List<Pair<Integer, Integer>> atLeast3dot0 = Arrays.asList(new Pair<>(0, 400),
                new Pair<>(50, 500), new Pair<>(100, 500), new Pair<>(200, 600),
                new Pair<>(300, 700), new Pair<>(400, 800), new Pair<>(500, 900),
                new Pair<>(600, 1000));

        List<int[]> allPalettes = Arrays.asList(getAllAccent1Colors(context),
                getAllAccent2Colors(context), getAllAccent3Colors(context),
                getAllNeutral1Colors(context), getAllNeutral2Colors(context));

        for (int[] palette : allPalettes) {
            for (Pair<Integer, Integer> shades : atLeast4dot5) {
                final int background = palette[shadeToArrayIndex(shades.first)];
                final int foreground = palette[shadeToArrayIndex(shades.second)];
                final double contrast = ColorUtils.calculateContrast(foreground, background);
                assertWithMessage("Shade " + shades.first + " (#" + Integer.toHexString(background)
                        + ") should have at least 4.5 contrast ratio against " + shades.second
                        + " (#" + Integer.toHexString(foreground) + ")").that(contrast)
                        .isGreaterThan(4.5);
            }

            for (Pair<Integer, Integer> shades : atLeast3dot0) {
                final int background = palette[shadeToArrayIndex(shades.first)];
                final int foreground = palette[shadeToArrayIndex(shades.second)];
                final double contrast = ColorUtils.calculateContrast(foreground, background);
                assertWithMessage("Shade " + shades.first + " (#" + Integer.toHexString(background)
                        + ") should have at least 3.0 contrast ratio against " + shades.second
                        + " (#" + Integer.toHexString(foreground) + ")").that(contrast)
                        .isGreaterThan(3);
            }
        }
    }

    /**
     * Convert the Material shade to an array position.
     *
     * @param shade Shade from 0 to 1000.
     * @return index in array
     * @see #getAllAccent1Colors(Context) (Context)
     * @see #getAllNeutral1Colors(Context)
     */
    private int shadeToArrayIndex(int shade) {
        if (shade == 0) {
            return 0;
        } else if (shade == 10) {
            return 1;
        } else if (shade == 50) {
            return 2;
        } else {
            return shade / 100 + 2;
        }
    }

    private void assertColor(@ColorInt int observed, @ColorInt int expected) {
        Assert.assertEquals("Color = " + Integer.toHexString(observed) + ", "
                        + Integer.toHexString(expected) + " expected", expected, observed);
    }

    private int[] getAllAccent1Colors(Context context) {
        final int[] colors = new int[13];
        colors[0] = context.getColor(R.color.system_accent1_0);
        colors[1] = context.getColor(R.color.system_accent1_10);
        colors[2] = context.getColor(R.color.system_accent1_50);
        colors[3] = context.getColor(R.color.system_accent1_100);
        colors[4] = context.getColor(R.color.system_accent1_200);
        colors[5] = context.getColor(R.color.system_accent1_300);
        colors[6] = context.getColor(R.color.system_accent1_400);
        colors[7] = context.getColor(R.color.system_accent1_500);
        colors[8] = context.getColor(R.color.system_accent1_600);
        colors[9] = context.getColor(R.color.system_accent1_700);
        colors[10] = context.getColor(R.color.system_accent1_800);
        colors[11] = context.getColor(R.color.system_accent1_900);
        colors[12] = context.getColor(R.color.system_accent1_1000);
        return colors;
    }

    private int[] getAllAccent2Colors(Context context) {
        final int[] colors = new int[13];
        colors[0] = context.getColor(R.color.system_accent2_0);
        colors[1] = context.getColor(R.color.system_accent2_10);
        colors[2] = context.getColor(R.color.system_accent2_50);
        colors[3] = context.getColor(R.color.system_accent2_100);
        colors[4] = context.getColor(R.color.system_accent2_200);
        colors[5] = context.getColor(R.color.system_accent2_300);
        colors[6] = context.getColor(R.color.system_accent2_400);
        colors[7] = context.getColor(R.color.system_accent2_500);
        colors[8] = context.getColor(R.color.system_accent2_600);
        colors[9] = context.getColor(R.color.system_accent2_700);
        colors[10] = context.getColor(R.color.system_accent2_800);
        colors[11] = context.getColor(R.color.system_accent2_900);
        colors[12] = context.getColor(R.color.system_accent2_1000);
        return colors;
    }

    private int[] getAllAccent3Colors(Context context) {
        final int[] colors = new int[13];
        colors[0] = context.getColor(R.color.system_accent3_0);
        colors[1] = context.getColor(R.color.system_accent3_10);
        colors[2] = context.getColor(R.color.system_accent3_50);
        colors[3] = context.getColor(R.color.system_accent3_100);
        colors[4] = context.getColor(R.color.system_accent3_200);
        colors[5] = context.getColor(R.color.system_accent3_300);
        colors[6] = context.getColor(R.color.system_accent3_400);
        colors[7] = context.getColor(R.color.system_accent3_500);
        colors[8] = context.getColor(R.color.system_accent3_600);
        colors[9] = context.getColor(R.color.system_accent3_700);
        colors[10] = context.getColor(R.color.system_accent3_800);
        colors[11] = context.getColor(R.color.system_accent3_900);
        colors[12] = context.getColor(R.color.system_accent3_1000);
        return colors;
    }

    private int[] getAllNeutral1Colors(Context context) {
        final int[] colors = new int[13];
        colors[0] = context.getColor(R.color.system_neutral1_0);
        colors[1] = context.getColor(R.color.system_neutral1_10);
        colors[2] = context.getColor(R.color.system_neutral1_50);
        colors[3] = context.getColor(R.color.system_neutral1_100);
        colors[4] = context.getColor(R.color.system_neutral1_200);
        colors[5] = context.getColor(R.color.system_neutral1_300);
        colors[6] = context.getColor(R.color.system_neutral1_400);
        colors[7] = context.getColor(R.color.system_neutral1_500);
        colors[8] = context.getColor(R.color.system_neutral1_600);
        colors[9] = context.getColor(R.color.system_neutral1_700);
        colors[10] = context.getColor(R.color.system_neutral1_800);
        colors[11] = context.getColor(R.color.system_neutral1_900);
        colors[12] = context.getColor(R.color.system_neutral1_1000);
        return colors;
    }

    private int[] getAllNeutral2Colors(Context context) {
        final int[] colors = new int[13];
        colors[0] = context.getColor(R.color.system_neutral2_0);
        colors[1] = context.getColor(R.color.system_neutral2_10);
        colors[2] = context.getColor(R.color.system_neutral2_50);
        colors[3] = context.getColor(R.color.system_neutral2_100);
        colors[4] = context.getColor(R.color.system_neutral2_200);
        colors[5] = context.getColor(R.color.system_neutral2_300);
        colors[6] = context.getColor(R.color.system_neutral2_400);
        colors[7] = context.getColor(R.color.system_neutral2_500);
        colors[8] = context.getColor(R.color.system_neutral2_600);
        colors[9] = context.getColor(R.color.system_neutral2_700);
        colors[10] = context.getColor(R.color.system_neutral2_800);
        colors[11] = context.getColor(R.color.system_neutral2_900);
        colors[12] = context.getColor(R.color.system_neutral2_1000);
        return colors;
    }

    private interface ThemeEvaluator {
        void apply(String color, String style, int[] expectedPalette);
    }
}
