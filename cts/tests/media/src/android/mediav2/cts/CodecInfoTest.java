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

package android.mediav2.cts;

import android.hardware.display.DisplayManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;
import android.view.Display;

import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.MediaUtils;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;

import static android.media.MediaCodecInfo.CodecCapabilities.*;
import static android.media.MediaCodecInfo.CodecProfileLevel.*;
import static android.mediav2.cts.CodecTestBase.*;
import static android.view.Display.HdrCapabilities.*;
import static org.junit.Assert.*;

@SmallTest
@RunWith(Parameterized.class)
public class CodecInfoTest {
    private static final String LOG_TAG = CodecInfoTest.class.getSimpleName();
    private static final int[] DISPLAY_HDR_TYPES;

    public String mMediaType;
    public String mCodecName;
    public MediaCodecInfo mCodecInfo;

    static {
        DisplayManager displayManager = mContext.getSystemService(DisplayManager.class);
        DISPLAY_HDR_TYPES =
                displayManager.getDisplay(Display.DEFAULT_DISPLAY).getHdrCapabilities()
                        .getSupportedHdrTypes();
    }

    public CodecInfoTest(String mediaType, String codecName, MediaCodecInfo codecInfo) {
        mMediaType = mediaType;
        mCodecName = codecName;
        mCodecInfo = codecInfo;
    }

    @Parameterized.Parameters(name = "{index}({0}_{1})")
    public static Collection<Object[]> input() {
        final List<Object[]> argsList = new ArrayList<>();
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        for (MediaCodecInfo codecInfo : codecList.getCodecInfos()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && codecInfo.isAlias()) {
                continue;
            }
            if (CodecTestBase.codecPrefix != null &&
                    !codecInfo.getName().startsWith(CodecTestBase.codecPrefix)) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                argsList.add(new Object[]{type, codecInfo.getName(), codecInfo});
            }
        }
        return argsList;
    }

    /**
     * Tests if the devices on T or later, if decoder for a mediaType supports HDR profiles then
     * it should be capable of displaying the same. Since HLG profiles can't be distinguished from
     * default 10-bit profiles, those are excluded from this test.
     */
    @Test
    // TODO (b/228237404) Remove the following once there is a reliable way to query HDR
    // display capabilities at native level, till then limit the test to vendor codecs
    @NonMediaMainlineTest
    @ApiTest(apis = "MediaCodecInfo.CodecCapabilities#profileLevels")
    public void testHDRDisplayCapabilities() {
        Assume.assumeTrue("Test needs Android 13", IS_AT_LEAST_T);
        Assume.assumeTrue("Test needs VNDK Android 13", VNDK_IS_AT_LEAST_T);
        Assume.assumeTrue("Test needs First SDK Android 13", FIRST_SDK_IS_AT_LEAST_T);
        Assume.assumeTrue("Test is applicable for video codecs", mMediaType.startsWith("video/"));
        // TODO (b/228237404) Remove the following once there is a reliable way to query HDR
        // display capabilities at native level, till then limit the test to vendor codecs
        Assume.assumeTrue("Test is restricted to vendor codecs", isVendorCodec(mCodecName));

        int[] Hdr10Profiles = mProfileHdr10Map.get(mMediaType);
        int[] Hdr10PlusProfiles = mProfileHdr10PlusMap.get(mMediaType);
        Assume.assumeTrue("Test is applicable for codecs with HDR10/HDR10+ profiles",
                Hdr10Profiles != null || Hdr10PlusProfiles != null);

        MediaCodecInfo.CodecCapabilities caps = mCodecInfo.getCapabilitiesForType(mMediaType);

        for (CodecProfileLevel pl : caps.profileLevels) {
            boolean isHdr10Profile = Hdr10Profiles != null &&
                    IntStream.of(Hdr10Profiles).anyMatch(x -> x == pl.profile);
            boolean isHdr10PlusProfile = Hdr10PlusProfiles != null &&
                    IntStream.of(Hdr10PlusProfiles).anyMatch(x -> x == pl.profile);
            // TODO (b/228237404) Once there is a way to query support for HDR10/HDR10+ display at
            // native level, separate the following to independent checks for HDR10 and HDR10+
            if (isHdr10Profile || isHdr10PlusProfile) {
                assertTrue(mCodecInfo.getName() + " Advertises support for HDR10/HDR10+ profile " +
                        pl.profile + " without any HDR display", DISPLAY_HDR_TYPES.length > 0);
            }
        }
    }

    /**
     * Tests if the device under test has support for necessary color formats.
     * The test only checks if the decoder/encoder is advertising the required color format. It
     * doesn't validate its support.
     */
    @Test
    public void testColorFormatSupport() throws IOException {
        Assume.assumeTrue("Test is applicable for video codecs", mMediaType.startsWith("video/"));
        MediaCodecInfo.CodecCapabilities caps = mCodecInfo.getCapabilitiesForType(mMediaType);
        assertFalse(mCodecInfo.getName() + " does not support COLOR_FormatYUV420Flexible",
                IntStream.of(caps.colorFormats)
                        .noneMatch(x -> x == COLOR_FormatYUV420Flexible));

        // Encoders that support FEATURE_HdrEditing, must support P010 and ABGR2101010
        // color format and at least one HDR profile
        boolean hdrEditingSupported = caps.isFeatureSupported(FEATURE_HdrEditing);
        if (mCodecInfo.isEncoder() && hdrEditingSupported) {
            boolean abgr2101010Supported =
                    IntStream.of(caps.colorFormats)
                            .anyMatch(x -> x == COLOR_Format32bitABGR2101010);
            boolean p010Supported =
                    IntStream.of(caps.colorFormats).anyMatch(x -> x == COLOR_FormatYUVP010);
            assertTrue(mCodecName + " supports FEATURE_HdrEditing, but does not support " +
                    "COLOR_FormatABGR2101010 and COLOR_FormatYUVP010 color formats.",
                    abgr2101010Supported && p010Supported);
            assertTrue(mCodecName + " supports FEATURE_HdrEditing, but does not support any HDR " +
                    "profiles.", CodecTestBase.doesCodecSupportHDRProfile(mCodecName, mMediaType));
        }

        // COLOR_FormatSurface support is an existing requirement, but we did not
        // test for it before T.  We can not retroactively apply the higher standard to
        // devices that are already certified, so only test on devices luanching with T or later.
        if (FIRST_SDK_IS_AT_LEAST_T && VNDK_IS_AT_LEAST_T) {
            assertFalse(mCodecInfo.getName() + " does not support COLOR_FormatSurface",
                    IntStream.of(caps.colorFormats)
                            .noneMatch(x -> x == COLOR_FormatSurface));
        }
    }

    /** For devices launching with Android T or higher, if a codec supports an HDR profile and
     * device supports HDR display, it must support COLOR_FormatYUVP010 as a video decoder output
     * format. For TVs, this requirement is optional.
     */
    @Test
    public void testP010SupportForHDRDisplay() {
        Assume.assumeTrue("Test is applicable for video codecs", mMediaType.startsWith("video/"));
        MediaCodecInfo.CodecCapabilities caps = mCodecInfo.getCapabilitiesForType(mMediaType);
        int[] HdrProfileArray = mProfileHdrMap.get(mMediaType);
        if (FIRST_SDK_IS_AT_LEAST_T && VNDK_IS_AT_LEAST_T && BOARD_SDK_IS_AT_LEAST_T
                        && HdrProfileArray != null && DISPLAY_HDR_TYPES.length > 0) {
            for (CodecProfileLevel pl : caps.profileLevels) {
                if (IntStream.of(HdrProfileArray).anyMatch(x -> x == pl.profile)) {
                    if (MediaUtils.isTv()) {
                        // Some TV devices support HDR10 display with VO instead of GPU. In this
                        // case, skip checking P010 on TV devices.
                        Assume.assumeFalse(mCodecInfo.getName() + " supports HDR profile "
                                        + pl.profile + ","
                                        + " but does not support COLOR_FormatYUVP010."
                                        + " Skip checking on TV device",
                                IntStream.of(caps.colorFormats)
                                        .noneMatch(x -> x == COLOR_FormatYUVP010));
                    } else {
                        assertFalse(mCodecInfo.getName() + " supports HDR profile "
                                        + pl.profile + "," +
                                        " but does not support COLOR_FormatYUVP010",
                                IntStream.of(caps.colorFormats)
                                        .noneMatch(x -> x == COLOR_FormatYUVP010));
                    }
                }
            }
        }
    }

    /**
     * Tests if a device supports encoding for a given mediaType, then it must support decoding it
     */
    @Test
    public void testDecoderAvailability() {
        Assume.assumeTrue("Test is applicable only for encoders", mCodecInfo.isEncoder());
        Assume.assumeTrue("Test is applicable for video/audio codecs",
                mMediaType.startsWith("video/") || mMediaType.startsWith("audio/"));
        if (selectCodecs(mMediaType, null, null, true).size() > 0) {
            assertTrue("Device advertises support for encoding " + mMediaType +
                            ", but not decoding it",
                    selectCodecs(mMediaType, null, null, false).size() > 0);
        }
    }
}

