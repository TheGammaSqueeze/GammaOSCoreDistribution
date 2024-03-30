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

package android.platform.helpers;

import android.app.Instrumentation;
import android.os.SystemClock;
import android.platform.helpers.exceptions.UnknownUiException;
import android.support.test.uiautomator.UiObject2;

import java.util.List;

public class TestMediaAppHelperImpl extends AbstractAutoStandardAppHelper
        implements IAutoTestMediaAppHelper {
    // Wait Time
    private static final int SHORT_RESPONSE_WAIT_MS = 1000;

    public TestMediaAppHelperImpl(Instrumentation instr) {
        super(instr);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void loadMediaInLocalMediaTestApp() {
        // Open Account type
        clickAndWait(
                AutoConfigConstants.TEST_MEDIA_ACCOUNT_TYPE, "Account Type");
        // Select Paid Account type
        clickAndWait(
                AutoConfigConstants.TEST_MEDIA_ACCOUNT_TYPE_PAID, "Account Type: Paid");
        // open Root node type
        clickAndWait(
                AutoConfigConstants.TEST_MEDIA_ROOT_NODE_TYPE, "Root node type");
        // select Browsable content
        clickAndWait(AutoConfigConstants.TEST_MEDIA_ROOT_NODE_TYPE_BROWSABLE,
                "Browsable Content");
        // close settings
        clickAndWait(AutoConfigConstants.TEST_MEDIA_APP_CLOSE_SETTING, "Close Settings");
        selectSongInTestMediaApp();
    }

    private void selectSongInTestMediaApp() {
        List<UiObject2> songList = findUiObjects(getResourceFromConfig(
                AutoConfigConstants.MEDIA_CENTER,
                AutoConfigConstants.MEDIA_APP,
                AutoConfigConstants.MEDIA_SONGS_LIST));
        if (songList.size() == 0) {
            throw new UnknownUiException("Unable to find Songs in the Test Media App.");
        }
        clickAndWaitForIdleScreen(songList.get(1));
        SystemClock.sleep(SHORT_RESPONSE_WAIT_MS);
        // minimize songs
        UiObject2 goBackToSongsList = findUiObject(getResourceFromConfig(
                AutoConfigConstants.MEDIA_CENTER,
                AutoConfigConstants.MEDIA_APP,
                AutoConfigConstants.MEDIA_APP_NAVIGATION_ICON));
        clickAndWaitForIdleScreen(goBackToSongsList);
        SystemClock.sleep(SHORT_RESPONSE_WAIT_MS);
    }

    private void clickAndWait(String autoConfigConstants, String fieldName) {
        UiObject2 mediaTestAppField = findUiObject(getResourceFromConfig(
                AutoConfigConstants.MEDIA_CENTER,
                AutoConfigConstants.TEST_MEDIA_APP,
                autoConfigConstants));
        if (mediaTestAppField == null) {
            throw new UnknownUiException("Unable to find Test Media App field: " + fieldName);
        }
        clickAndWaitForIdleScreen(mediaTestAppField);
        SystemClock.sleep(SHORT_RESPONSE_WAIT_MS);
    }
}
